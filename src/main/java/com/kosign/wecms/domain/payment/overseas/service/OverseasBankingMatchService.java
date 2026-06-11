package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.dto.OverseasMatchCandidateListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasMatchCandidateRow;
import com.kosign.wecms.domain.payment.dto.OverseasMatchRequest;
import com.kosign.wecms.domain.payment.dto.OverseasMatchResultResponse;
import com.kosign.wecms.domain.payment.dto.OverseasUnmatchAllResponse;
import com.kosign.wecms.domain.payment.entity.OvrsPymnDtls;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Overseas payment-line ↔ bank-transaction reconciliation (legacy
 * {@code comm_0197_001_r001} / {@code _c001} / {@code comm_0197_002_u001} /
 * {@code comm_0197_001_u001}, the {@code isOversea} branch).
 *
 * <p><b>Direction:</b> payment-line → bank-transaction. <b>Granularity:</b>
 * per detail line, keyed on {@code OVRS_PYMN_DETL_NO} (the V248 match identity
 * — NOT the batch and NOT {@code TRN_SEQ}). <b>Cardinality:</b> strictly one
 * line ↔ one bank transaction. Deliberately a per-line model — NOT the
 * header-level Payroll match.
 *
 * <p>Writes exactly the three legacy tables (NEVER the SCDUL tables, NEVER the
 * OVRS header):
 * <ul>
 *   <li>{@code BUSI_MATCH_LOG} — insert or re-lock; never deleted (history).
 *       {@code PAY_TRNS_ID = OVRS_PYMN_DETL_NO} (bare — overseas parity).</li>
 *   <li>{@code ACCT_TRNS_HSTR} — {@code markBankRowMatchedOverseas} stamps
 *       {@code OVRS_PYMN_NO} + {@code OVRS_PYMN_DETL_NO} (nulls the SCDUL pair).</li>
 *   <li>{@code OVRS_PYMN_DTLS} — {@code markDetailMatchedOverseas} stamps
 *       {@code LINK_MATCH_YN='Y'} + {@code ACCT_SEQ_NO} + bank ids.</li>
 * </ul>
 *
 * <p>Reuses the shared {@code BUSI_MATCH_LOG} + {@code ACCT_TRNS_HSTR} writers
 * so the bank-side Smart Organizer (opposite direction) interoperates; the
 * generic {@code WBE_PAYMENT_MATCH_*} codes carry module-agnostic wording.
 *
 * <p>OVR-G1 Phase C. Backend match APIs only — no frontend, no dispatch, no
 * schema change.
 */
@Service
@RequiredArgsConstructor
public class OverseasBankingMatchService {

    // Reuse the generic, module-agnostic payment-match codes (already in
    // messages.properties; identical semantics for overseas).
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

    private final AcctTrnsHstrRepository  txnRepo;
    private final BusiMatchLogRepository   matchLogRepo;
    private final OvrsPymnSumrRepository   sumrRepo;
    private final OvrsPymnDtlsRepository   dtlsRepo;

    /* ----------------------------------------------------------- list ---- */

    /**
     * Candidate search for one overseas line (legacy {@code comm_0197_001_r001}
     * {@code isOversea}). Loads the line + parent batch, then scans
     * {@code ACCT_TRNS_HSTR} for unmatched debits on the withdrawal account
     * matching the line's {@code PAY_TRAN_DT} + {@code TRNF_AMT} (+ optional
     * MEMO remark filter + keyword).
     *
     * @param matchRmrkYn {@code "N"} disables the line-{@code MEMO} remark
     *                    filter; any other value (incl. null) keeps it on
     *                    (legacy default {@code 'Y'}).
     */
    @Transactional(readOnly = true)
    public OverseasMatchCandidateListResponse listCandidates(
            String ovrsPymnNo, String ovrsPymnDetlNo, String matchRmrkYn, String srchWd) {
        String bizKey = TenantContext.currentBizKey();

        OvrsPymnSumr batch = loadBatch(bizKey, ovrsPymnNo);
        OvrsPymnDtls line = loadLine(bizKey, ovrsPymnNo, ovrsPymnDetlNo);
        if (YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_LINE_ALREADY_MATCHED);
        }

        // Overseas remark filter — line MEMO ilike bank RMRK/RMRK2, unless off.
        String memo = null;
        if (!NO.equals(matchRmrkYn) && isNotBlank(line.getMemo())) {
            memo = "%" + line.getMemo().trim().toLowerCase() + "%";
        }
        String keyword = isNotBlank(srchWd) ? "%" + srchWd.trim().toLowerCase() + "%" : null;

        List<OverseasMatchCandidateRow> rows = new ArrayList<>();
        for (AcctTrnsHstr h : txnRepo.findOverseasMatchCandidates(
                bizKey, batch.getWdrlActNo(), line.getPayTranDt(), line.getTrnfAmt(), memo, keyword)) {
            rows.add(new OverseasMatchCandidateRow(
                    h.getAcctSeqNo(), h.getBankTrnsShortId(), h.getBankTrnsFullId(),
                    h.getTrscDt(), h.getTrscTm(), h.getTrscAmt(),
                    h.getCcyCd(), h.getRmrk(), h.getRmrk2()));
        }
        return new OverseasMatchCandidateListResponse(
                ovrsPymnNo, ovrsPymnDetlNo, batch.getWdrlActNo(),
                line.getTrnfAmt(), line.getPayTranDt(), line.getCcyCd(),
                rows.size(), rows);
    }

    /* ---------------------------------------------------------- match ---- */

    /** Match an overseas line to the selected bank transaction (legacy
     *  {@code comm_0197_001_c001} {@code isOversea}). */
    @Transactional
    public OverseasMatchResultResponse match(
            String ovrsPymnNo, String ovrsPymnDetlNo, OverseasMatchRequest req) {
        String bizKey = TenantContext.currentBizKey();

        loadBatch(bizKey, ovrsPymnNo);  // existence / tenant guard.
        OvrsPymnDtls line = loadLine(bizKey, ovrsPymnNo, ovrsPymnDetlNo);
        if (YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_LINE_ALREADY_MATCHED);
        }

        AcctTrnsHstr bank = txnRepo.findById(new AcctTrnsHstrId(bizKey, req.acctSeqNo()))
                .orElseThrow(() -> new BusinessException(CODE_BANK_TXN_NOT_FOUND));
        if (YES.equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_BANK_TXN_ALREADY_MATCHED);
        }

        // PAY_TRNS_ID = OVRS_PYMN_DETL_NO (bare — overseas legacy parity).
        String payTrnsId = ovrsPymnDetlNo;
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

        // 2. ACCT_TRNS_HSTR — stamp OVRS_PYMN_NO + OVRS_PYMN_DETL_NO.
        if (txnRepo.markBankRowMatchedOverseas(bizKey, req.acctSeqNo(), ovrsPymnNo, ovrsPymnDetlNo,
                userId, nowDtm) == 0) {
            throw new BusinessException(CODE_BANK_TXN_NOT_FOUND);
        }

        // 3. OVRS_PYMN_DTLS — LINK_MATCH_YN='Y' + ACCT_SEQ_NO + bank ids.
        if (dtlsRepo.markDetailMatchedOverseas(bizKey, ovrsPymnNo, ovrsPymnDetlNo,
                req.acctSeqNo(), shortId, fullId, userId, nowDtm) == 0) {
            throw new BusinessException(CODE_LINE_NOT_FOUND);
        }

        return new OverseasMatchResultResponse(
                ovrsPymnNo, ovrsPymnDetlNo, YES,
                req.acctSeqNo(), shortId, fullId, log.getMatchLogSeq());
    }

    /* -------------------------------------------------------- unmatch ---- */

    /** Unmatch a single overseas line (legacy {@code comm_0197_002_u001}
     *  {@code isOversea}). Unlocks the log (kept for history); clears both sides. */
    @Transactional
    public OverseasMatchResultResponse unmatch(String ovrsPymnNo, String ovrsPymnDetlNo) {
        String bizKey = TenantContext.currentBizKey();

        loadBatch(bizKey, ovrsPymnNo);  // existence / tenant guard.
        OvrsPymnDtls line = loadLine(bizKey, ovrsPymnNo, ovrsPymnDetlNo);
        if (!YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_NOT_MATCHED);
        }

        String payTrnsId = ovrsPymnDetlNo;
        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, payTrnsId, YES)
                .orElseThrow(() -> new BusinessException(CODE_LOG_NOT_FOUND));

        String userId = TenantContext.currentUserId();
        String nowDtm = nowDtm();

        unlockLog(log, userId, nowDtm);
        if (isNotBlank(line.getAcctSeqNo())) {
            txnRepo.clearBankRowMatch(bizKey, line.getAcctSeqNo(), userId, nowDtm);
        }
        dtlsRepo.clearDetailMatchOverseas(bizKey, ovrsPymnNo, ovrsPymnDetlNo, userId, nowDtm);

        return new OverseasMatchResultResponse(
                ovrsPymnNo, ovrsPymnDetlNo, NO, null, null, null, log.getMatchLogSeq());
    }

    /* ---------------------------------------------------- unmatch-all ---- */

    /** Unmatch every matched line under the batch (legacy
     *  {@code comm_0197_001_u001} {@code isOversea}). Logs unlocked, never deleted. */
    @Transactional
    public OverseasUnmatchAllResponse unmatchAll(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        loadBatch(bizKey, ovrsPymnNo);  // existence / tenant guard.

        String userId = TenantContext.currentUserId();
        String nowDtm = nowDtm();
        int cleared = 0;

        List<OvrsPymnDtls> lines =
                dtlsRepo.findByBizKeyAndOvrsPymnNoOrderByTrnSeqAsc(bizKey, ovrsPymnNo);
        for (OvrsPymnDtls line : lines) {
            if (!YES.equals(line.getLinkMatchYn())) continue;
            String detlNo = line.getOvrsPymnDetlNo();
            if (detlNo == null) continue;  // pre-backfill safety.

            matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, detlNo, YES)
                    .ifPresent(log -> unlockLog(log, userId, nowDtm));
            if (isNotBlank(line.getAcctSeqNo())) {
                txnRepo.clearBankRowMatch(bizKey, line.getAcctSeqNo(), userId, nowDtm);
            }
            dtlsRepo.clearDetailMatchOverseas(bizKey, ovrsPymnNo, detlNo, userId, nowDtm);
            cleared++;
        }
        return new OverseasUnmatchAllResponse(ovrsPymnNo, cleared);
    }

    /* --------------------------------------------------------- helpers --- */

    private OvrsPymnSumr loadBatch(String bizKey, String ovrsPymnNo) {
        return sumrRepo.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException(CODE_BATCH_NOT_FOUND));
    }

    private OvrsPymnDtls loadLine(String bizKey, String ovrsPymnNo, String ovrsPymnDetlNo) {
        return dtlsRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(bizKey, ovrsPymnNo, ovrsPymnDetlNo)
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

    private static String nowDtm() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
    }

    private static boolean isNotBlank(String s) { return s != null && !s.isBlank(); }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
