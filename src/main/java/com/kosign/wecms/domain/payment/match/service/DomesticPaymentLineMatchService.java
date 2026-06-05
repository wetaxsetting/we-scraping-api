package com.kosign.wecms.domain.payment.match.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchCandidateListResponse;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchCandidateRow;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchRequest;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchResultResponse;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineUnmatchAllResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Domestic payment-line ↔ bank-transaction reconciliation (legacy
 * {@code comm_0197_001_r001} / {@code _c001} / {@code comm_0197_002_u001} /
 * {@code comm_0197_001_u001}, the {@code else} / domestic-detail branch).
 *
 * <p><b>Direction:</b> payment-line → bank-transaction (the inverse of the
 * bank-side {@link com.kosign.wecms.domain.banking.service.MatchService}
 * Smart Organizer). <b>Granularity:</b> per detail line, keyed on
 * {@code (SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)}. <b>Cardinality:</b> strictly
 * one line ↔ one bank transaction. Deliberately NOT reusing the header-level
 * Payroll match logic.
 *
 * <p>Shared by Vendor ({@code SCDUL_PYMN_TYPE_CD='01'}) and Expense
 * ({@code '02'}) — the controller passes the expected type code so a Vendor
 * endpoint cannot reconcile an Expense batch (defence in depth; the composite
 * key is already globally unique).
 *
 * <p>Writes exactly the three legacy tables and reuses the existing native
 * writers so the two match directions stay interoperable:
 * <ul>
 *   <li>{@code BUSI_MATCH_LOG} — insert or re-lock; never deleted (history).</li>
 *   <li>{@code ACCT_TRNS_HSTR} — {@code markBankRowMatched} stamps
 *       {@code SCDUL_PYMN_NO} + {@code SCDUL_PYMN_DETL_NO}.</li>
 *   <li>{@code SCDUL_PYMN_SUMR_DETL} — {@code markDetailMatched} stamps
 *       {@code LINK_MATCH_YN='Y'} + {@code ACCT_SEQ_NO} + bank ids.</li>
 * </ul>
 * The {@code SCDUL_PYMN_SUMR} header is NOT written (legacy parity — only the
 * Payroll batch path touches the header).
 *
 * <p>{@code PAY_TRNS_ID} is the composite {@code SCDUL_PYMN_NO + "_" +
 * SCDUL_PYMN_DETL_NO} — identical to {@code MatchService} so a match made from
 * either direction is found by the other.
 */
@Service
@RequiredArgsConstructor
public class DomesticPaymentLineMatchService {

    private static final String CODE_BATCH_NOT_FOUND          = "WBE_PAYMENT_MATCH_BATCH_NOT_FOUND";
    private static final String CODE_LINE_NOT_FOUND           = "WBE_PAYMENT_MATCH_LINE_NOT_FOUND";
    private static final String CODE_LINE_ALREADY_MATCHED     = "WBE_PAYMENT_MATCH_LINE_ALREADY_MATCHED";
    private static final String CODE_BANK_TXN_NOT_FOUND       = "WBE_PAYMENT_MATCH_BANK_TXN_NOT_FOUND";
    private static final String CODE_BANK_TXN_ALREADY_MATCHED = "WBE_PAYMENT_MATCH_BANK_TXN_ALREADY_MATCHED";
    private static final String CODE_ALREADY_EXISTS           = "WBE_PAYMENT_MATCH_ALREADY_EXISTS";
    private static final String CODE_LOG_NOT_FOUND            = "WBE_PAYMENT_MATCH_LOG_NOT_FOUND";
    private static final String CODE_NOT_MATCHED              = "WBE_PAYMENT_MATCH_NOT_MATCHED";

    private static final DateTimeFormatter DTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String MATCH_TYPE_MANUAL = "M";
    private static final String YES = "Y";
    private static final String NO  = "N";
    /** Composite PAY_TRNS_ID delimiter — must match {@code MatchService}. */
    private static final String PAY_TRNS_ID_SEP = "_";

    private final AcctTrnsHstrRepository       txnRepo;
    private final BusiMatchLogRepository        matchLogRepo;
    private final ScdulPymnSumrRepository       sumrRepo;
    private final ScdulPymnSumrDetlRepository   detlRepo;

    /* ----------------------------------------------------------- list ---- */

    /**
     * Candidate search for one payment line (legacy {@code comm_0197_001_r001}
     * domestic path). Loads the line + its parent batch (type-guarded), then
     * scans {@code ACCT_TRNS_HSTR} for unmatched debits on the withdrawal
     * account matching the line's date + amount (+ optional remark / keyword).
     *
     * @param matchRmrkYn {@code "N"} disables the {@code TRAN_USAG} remark
     *                    filter; any other value (incl. null) keeps it on.
     */
    @Transactional(readOnly = true)
    public PaymentLineMatchCandidateListResponse listCandidates(
            String typeCd, String scdulPymnNo, String scdulPymnDetlNo,
            String matchRmrkYn, String srchWd) {
        String bizKey = TenantContext.currentBizKey();

        ScdulPymnSumr batch = loadBatch(bizKey, scdulPymnNo, typeCd);
        ScdulPymnSumrDetl line = loadLine(bizKey, scdulPymnNo, scdulPymnDetlNo);
        if (YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_LINE_ALREADY_MATCHED);
        }

        // Remark filter — line TRAN_USAG ilike bank RMRK/RMRK2, unless turned off.
        String memo = null;
        if (!NO.equals(matchRmrkYn) && isNotBlank(line.getTranUsag())) {
            memo = "%" + line.getTranUsag().trim().toLowerCase() + "%";
        }
        String keyword = isNotBlank(srchWd) ? "%" + srchWd.trim().toLowerCase() + "%" : null;

        List<PaymentLineMatchCandidateRow> rows = new ArrayList<>();
        for (AcctTrnsHstr h : txnRepo.findDomesticDetailMatchCandidates(
                bizKey, batch.getWdrlActNo(), line.getPayTranDt(), line.getTranAmt(), memo, keyword)) {
            rows.add(new PaymentLineMatchCandidateRow(
                    h.getAcctSeqNo(), h.getBankTrnsShortId(), h.getBankTrnsFullId(),
                    h.getTrscDt(), h.getTrscTm(), h.getTrscAmt(),
                    h.getCcyCd(), h.getRmrk(), h.getRmrk2()));
        }
        return new PaymentLineMatchCandidateListResponse(
                scdulPymnNo, scdulPymnDetlNo, batch.getWdrlActNo(),
                line.getTranAmt(), line.getPayTranDt(), line.getTrnfCcyCd(),
                rows.size(), rows);
    }

    /* ---------------------------------------------------------- match ---- */

    /** Match a payment line to the selected bank transaction (legacy
     *  {@code comm_0197_001_c001} domestic path). */
    @Transactional
    public PaymentLineMatchResultResponse match(
            String typeCd, String scdulPymnNo, String scdulPymnDetlNo,
            PaymentLineMatchRequest req) {
        String bizKey = TenantContext.currentBizKey();

        loadBatch(bizKey, scdulPymnNo, typeCd);  // type-guard / existence.
        ScdulPymnSumrDetl line = loadLine(bizKey, scdulPymnNo, scdulPymnDetlNo);
        if (YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_LINE_ALREADY_MATCHED);
        }

        AcctTrnsHstr bank = txnRepo.findById(new AcctTrnsHstrId(bizKey, req.acctSeqNo()))
                .orElseThrow(() -> new BusinessException(CODE_BANK_TXN_NOT_FOUND));
        if (YES.equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_BANK_TXN_ALREADY_MATCHED);
        }

        String payTrnsId = payTrnsId(scdulPymnNo, scdulPymnDetlNo);
        String shortId = nullToEmpty(bank.getBankTrnsShortId());
        String fullId  = nullToEmpty(bank.getBankTrnsFullId());

        // Guard — no active (locked) log for this exact pair.
        matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                        bizKey, shortId, payTrnsId, YES)
                .ifPresent(rp -> { throw new BusinessException(CODE_ALREADY_EXISTS); });

        String userId = TenantContext.currentUserId();
        String nowDtm = nowDtm();

        // 1. BUSI_MATCH_LOG — re-lock a retained row for the pair, else insert.
        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                        bizKey, shortId, payTrnsId, NO)
                .orElseGet(BusiMatchLog::new);
        if (log.getMatchLogSeq() == null) {
            log.setBizKey(bizKey);
            log.setMatchLogSeq(nextMatchLogSeq(bizKey));
            log.setBankTrnsShortId(shortId);
            log.setBankTrnsFullId(fullId);
            log.setPayTrnsId(payTrnsId);
            log.setRegId(userId);
            log.setRegDtm(nowDtm);
        }
        log.setMatchTypeCd(MATCH_TYPE_MANUAL);
        log.setMemo(req.memo());
        log.setMatchedAt(nowDtm);
        log.setMatchedBy(userId);
        log.setLastMatchedAt(nowDtm);
        log.setLastMatchedBy(userId);
        log.setLockedYn(YES);
        log.setModId(userId);
        log.setModDtm(nowDtm);
        matchLogRepo.save(log);

        // 2. ACCT_TRNS_HSTR — stamp SCDUL_PYMN_NO + SCDUL_PYMN_DETL_NO.
        if (txnRepo.markBankRowMatched(bizKey, req.acctSeqNo(), scdulPymnNo, scdulPymnDetlNo,
                userId, nowDtm) == 0) {
            throw new BusinessException(CODE_BANK_TXN_NOT_FOUND);
        }

        // 3. SCDUL_PYMN_SUMR_DETL — LINK_MATCH_YN='Y' + ACCT_SEQ_NO + bank ids.
        if (detlRepo.markDetailMatched(bizKey, scdulPymnNo, scdulPymnDetlNo,
                req.acctSeqNo(), shortId, fullId, userId, nowDtm) == 0) {
            throw new BusinessException(CODE_LINE_NOT_FOUND);
        }

        return new PaymentLineMatchResultResponse(
                scdulPymnNo, scdulPymnDetlNo, YES,
                req.acctSeqNo(), shortId, fullId, log.getMatchLogSeq());
    }

    /* -------------------------------------------------------- unmatch ---- */

    /** Unmatch a single payment line (legacy {@code comm_0197_002_u001}
     *  domestic path). Unlocks the log (kept for history); clears both sides. */
    @Transactional
    public PaymentLineMatchResultResponse unmatch(
            String typeCd, String scdulPymnNo, String scdulPymnDetlNo) {
        String bizKey = TenantContext.currentBizKey();

        loadBatch(bizKey, scdulPymnNo, typeCd);  // type-guard / existence.
        ScdulPymnSumrDetl line = loadLine(bizKey, scdulPymnNo, scdulPymnDetlNo);
        if (!YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_NOT_MATCHED);
        }

        String payTrnsId = payTrnsId(scdulPymnNo, scdulPymnDetlNo);
        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, payTrnsId, YES)
                .orElseThrow(() -> new BusinessException(CODE_LOG_NOT_FOUND));

        String userId = TenantContext.currentUserId();
        String nowDtm = nowDtm();

        unlockLog(log, userId, nowDtm);
        if (isNotBlank(line.getAcctSeqNo())) {
            txnRepo.clearBankRowMatch(bizKey, line.getAcctSeqNo(), userId, nowDtm);
        }
        detlRepo.clearDetailMatch(bizKey, scdulPymnNo, scdulPymnDetlNo, userId, nowDtm);

        return new PaymentLineMatchResultResponse(
                scdulPymnNo, scdulPymnDetlNo, NO, null, null, null, log.getMatchLogSeq());
    }

    /* ---------------------------------------------------- unmatch-all ---- */

    /** Unmatch every matched line under the batch (legacy
     *  {@code comm_0197_001_u001}). Logs are unlocked, never deleted. */
    @Transactional
    public PaymentLineUnmatchAllResponse unmatchAll(String typeCd, String scdulPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        loadBatch(bizKey, scdulPymnNo, typeCd);  // type-guard / existence.

        String userId = TenantContext.currentUserId();
        String nowDtm = nowDtm();
        int cleared = 0;

        List<ScdulPymnSumrDetl> lines = detlRepo.findByBizKeyAndScdulPymnNo(
                bizKey, scdulPymnNo, Sort.by(Sort.Order.asc("scdulPymnDetlNo")));
        for (ScdulPymnSumrDetl line : lines) {
            if (!YES.equals(line.getLinkMatchYn())) continue;

            String payTrnsId = payTrnsId(scdulPymnNo, line.getScdulPymnDetlNo());
            matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, payTrnsId, YES)
                    .ifPresent(log -> unlockLog(log, userId, nowDtm));
            if (isNotBlank(line.getAcctSeqNo())) {
                txnRepo.clearBankRowMatch(bizKey, line.getAcctSeqNo(), userId, nowDtm);
            }
            detlRepo.clearDetailMatch(bizKey, scdulPymnNo, line.getScdulPymnDetlNo(), userId, nowDtm);
            cleared++;
        }
        return new PaymentLineUnmatchAllResponse(scdulPymnNo, cleared);
    }

    /* --------------------------------------------------------- helpers --- */

    private ScdulPymnSumr loadBatch(String bizKey, String scdulPymnNo, String typeCd) {
        return sumrRepo.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(bizKey, scdulPymnNo, typeCd)
                .orElseThrow(() -> new BusinessException(CODE_BATCH_NOT_FOUND));
    }

    private ScdulPymnSumrDetl loadLine(String bizKey, String scdulPymnNo, String scdulPymnDetlNo) {
        return detlRepo.findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(bizKey, scdulPymnNo, scdulPymnDetlNo)
                .orElseThrow(() -> new BusinessException(CODE_LINE_NOT_FOUND));
    }

    private void unlockLog(BusiMatchLog log, String userId, String nowDtm) {
        log.setLockedYn(NO);
        log.setLastMatchedAt(nowDtm);
        log.setLastMatchedBy(userId);
        log.setModId(userId);
        log.setModDtm(nowDtm);
        matchLogRepo.save(log);
    }

    private String nextMatchLogSeq(String bizKey) {
        long max = 0;
        for (BusiMatchLog row : matchLogRepo.findByBizKey(bizKey)) {
            try {
                long v = Long.parseLong(row.getMatchLogSeq());
                if (v > max) max = v;
            } catch (NumberFormatException ignored) {
                // Non-numeric legacy seq — skip.
            }
        }
        return String.valueOf(max + 1);
    }

    private static String payTrnsId(String scdulPymnNo, String scdulPymnDetlNo) {
        return scdulPymnNo + PAY_TRNS_ID_SEP + scdulPymnDetlNo;
    }

    private static String nowDtm() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
    }

    private static boolean isNotBlank(String s) { return s != null && !s.isBlank(); }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
