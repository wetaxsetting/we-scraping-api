package com.kosign.wecms.domain.payment.match.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.dto.OverseasMatchCandidateListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasMatchCandidateRow;
import com.kosign.wecms.domain.payment.dto.OverseasMatchResultResponse;
import com.kosign.wecms.domain.payment.dto.OverseasUnmatchAllResponse;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchRequest;
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
 * <p><b>P2 counterpart of {@link DomesticPaymentLineMatchService}</b> — same
 * direction (payment-line → bank-transaction), same cardinality (one line ↔
 * one bank transaction), same {@code LOCKED_YN} history + re-lock-after-unmatch
 * semantics — but over {@code OVRS_PYMN_SUMR_DETL} instead of
 * {@code SCDUL_PYMN_SUMR_DETL}, and using the overseas native writers
 * ({@code markBankRowMatchedOverseas} / {@code markOverseasDetailMatched}).
 *
 * <p>Writes the same three legacy tables:
 * <ul>
 *   <li>{@code BUSI_MATCH_LOG} — insert or re-lock; never deleted (history);
 *       {@code MATCH_TYPE_CD='M'} (manual).</li>
 *   <li>{@code ACCT_TRNS_HSTR} — {@code markBankRowMatchedOverseas} stamps
 *       {@code OVRS_PYMN_NO} + {@code OVRS_PYMN_DETL_NO} (and nulls the SCDUL pair).</li>
 *   <li>{@code OVRS_PYMN_SUMR_DETL} — {@code markOverseasDetailMatched} stamps
 *       {@code LINK_MATCH_YN='Y'} + {@code ACCT_SEQ_NO} + bank ids.</li>
 * </ul>
 *
 * <p>{@code PAY_TRNS_ID} is the composite {@code OVRS_PYMN_NO + "_" +
 * OVRS_PYMN_DETL_NO} — same delimiter idiom as the domestic services and the
 * bank-side {@code MatchService}, so a match made from either direction is
 * found by the other.
 *
 * <p>Overseas <b>auto</b>-match (post bank-sync) is NOT implemented here — the
 * P1 {@code AutoMatchService} covers domestic only; overseas auto-match stays
 * deferred. This service is the manual (Smart Organizer / Result-detail) path.
 */
@Service
@RequiredArgsConstructor
public class OverseasPaymentLineMatchService {

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

    private final AcctTrnsHstrRepository   txnRepo;
    private final BusiMatchLogRepository   matchLogRepo;
    private final OvrsPymnSumrRepository   sumrRepo;
    private final OvrsPymnDtlsRepository   detlRepo;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /* ----------------------------------------------------------- list ---- */

    /**
     * Candidate search for one overseas payment line (legacy
     * {@code comm_0197_001_r001} isOversea). Loads the line + its parent batch,
     * then scans {@code ACCT_TRNS_HSTR} for unmatched debits on the withdrawal
     * account matching the line's date + amount (+ the line's MEMO remark and
     * an optional keyword).
     */
    @Transactional(readOnly = true)
    public OverseasMatchCandidateListResponse listCandidates(
            String ovrsPymnNo, String ovrsPymnDetlNo, String srchWd) {
        String bizKey = TenantContext.currentBizKey();

        OvrsPymnSumr batch = loadBatch(bizKey, ovrsPymnNo);
        OvrsPymnSumrDetl line = loadLine(bizKey, ovrsPymnNo, ovrsPymnDetlNo);
        if (YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_LINE_ALREADY_MATCHED);
        }

        // Remark filter — overseas uses the line MEMO (O-type; the domestic
        // path uses TRAN_USAG). ilike against bank RMRK/RMRK2.
        String memo = isNotBlank(line.getMemo())
                ? "%" + line.getMemo().trim().toLowerCase() + "%" : null;
        String keyword = isNotBlank(srchWd) ? "%" + srchWd.trim().toLowerCase() + "%" : null;

        // P3 parity — TRSC_TM >= the overseas batch TRNF_DT (UTC) shifted to
        // VN-local HHmmss (legacy comm_0197_001_r001 putBankingCandidateIdoInOvrs).
        // Null when the batch is not yet transferred → the query skips the floor.
        String transferTime = MatchCandidateTime.localTransferTime(batch.getTrnfDt());

        List<OverseasMatchCandidateRow> rows = new ArrayList<>();
        for (AcctTrnsHstr h : txnRepo.findOverseasMatchCandidates(
                bizKey, batch.getWdrlActNo(), line.getPayTranDt(), line.getTrnfAmt(),
                transferTime, memo, keyword)) {
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

    /** Match an overseas payment line to the selected bank transaction
     *  (legacy {@code comm_0197_001_c001} isOversea). */
    @Transactional
    public OverseasMatchResultResponse match(
            String ovrsPymnNo, String ovrsPymnDetlNo, PaymentLineMatchRequest req) {
        String bizKey = TenantContext.currentBizKey();

        loadBatch(bizKey, ovrsPymnNo);  // existence.
        OvrsPymnSumrDetl line = loadLine(bizKey, ovrsPymnNo, ovrsPymnDetlNo);
        if (YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_LINE_ALREADY_MATCHED);
        }

        AcctTrnsHstr bank = txnRepo.findById(new AcctTrnsHstrId(bizKey, req.acctSeqNo()))
                .orElseThrow(() -> new BusinessException(CODE_BANK_TXN_NOT_FOUND));
        if (YES.equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_BANK_TXN_ALREADY_MATCHED);
        }

        String payTrnsId = payTrnsId(ovrsPymnNo, ovrsPymnDetlNo);
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

        // 2. ACCT_TRNS_HSTR — stamp OVRS_PYMN_NO + OVRS_PYMN_DETL_NO (nulls SCDUL pair).
        if (txnRepo.markBankRowMatchedOverseas(bizKey, req.acctSeqNo(), ovrsPymnNo, ovrsPymnDetlNo,
                userId, nowDtm) == 0) {
            throw new BusinessException(CODE_BANK_TXN_NOT_FOUND);
        }

        // 3. OVRS_PYMN_SUMR_DETL — LINK_MATCH_YN='Y' + ACCT_SEQ_NO + bank ids.
        if (detlRepo.markOverseasDetailMatched(bizKey, ovrsPymnNo, ovrsPymnDetlNo,
                req.acctSeqNo(), shortId, fullId, userId, nowDtm) == 0) {
            throw new BusinessException(CODE_LINE_NOT_FOUND);
        }

        return new OverseasMatchResultResponse(
                ovrsPymnNo, ovrsPymnDetlNo, YES,
                req.acctSeqNo(), shortId, fullId, log.getMatchLogSeq());
    }

    /* -------------------------------------------------------- unmatch ---- */

    /** Unmatch a single overseas payment line (legacy {@code comm_0197_002_u001}
     *  isOversea). Unlocks the log (kept for history); clears both sides. */
    @Transactional
    public OverseasMatchResultResponse unmatch(String ovrsPymnNo, String ovrsPymnDetlNo) {
        String bizKey = TenantContext.currentBizKey();

        loadBatch(bizKey, ovrsPymnNo);  // existence.
        OvrsPymnSumrDetl line = loadLine(bizKey, ovrsPymnNo, ovrsPymnDetlNo);
        if (!YES.equals(line.getLinkMatchYn())) {
            throw new BusinessException(CODE_NOT_MATCHED);
        }

        String payTrnsId = payTrnsId(ovrsPymnNo, ovrsPymnDetlNo);
        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, payTrnsId, YES)
                .orElseThrow(() -> new BusinessException(CODE_LOG_NOT_FOUND));

        String userId = TenantContext.currentUserId();
        String nowDtm = nowDtm();

        unlockLog(log, userId, nowDtm);
        if (isNotBlank(line.getAcctSeqNo())) {
            txnRepo.clearBankRowMatch(bizKey, line.getAcctSeqNo(), userId, nowDtm);
        }
        detlRepo.clearOverseasDetailMatch(bizKey, ovrsPymnNo, ovrsPymnDetlNo, userId, nowDtm);

        return new OverseasMatchResultResponse(
                ovrsPymnNo, ovrsPymnDetlNo, NO, null, null, null, log.getMatchLogSeq());
    }

    /* ---------------------------------------------------- unmatch-all ---- */

    /** Unmatch every matched line under the batch (legacy
     *  {@code comm_0197_001_u001} isOversea). Logs are unlocked, never deleted. */
    @Transactional
    public OverseasUnmatchAllResponse unmatchAll(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        loadBatch(bizKey, ovrsPymnNo);  // existence.

        String userId = TenantContext.currentUserId();
        String nowDtm = nowDtm();
        int cleared = 0;

        List<OvrsPymnSumrDetl> lines =
                detlRepo.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(bizKey, ovrsPymnNo);
        for (OvrsPymnSumrDetl line : lines) {
            if (!YES.equals(line.getLinkMatchYn())) continue;

            String payTrnsId = payTrnsId(ovrsPymnNo, line.getOvrsPymnDetlNo());
            matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, payTrnsId, YES)
                    .ifPresent(log -> unlockLog(log, userId, nowDtm));
            if (isNotBlank(line.getAcctSeqNo())) {
                txnRepo.clearBankRowMatch(bizKey, line.getAcctSeqNo(), userId, nowDtm);
            }
            detlRepo.clearOverseasDetailMatch(bizKey, ovrsPymnNo, line.getOvrsPymnDetlNo(), userId, nowDtm);
            cleared++;
        }
        return new OverseasUnmatchAllResponse(ovrsPymnNo, cleared);
    }

    /* --------------------------------------------------------- helpers --- */

    private OvrsPymnSumr loadBatch(String bizKey, String ovrsPymnNo) {
        return sumrRepo.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException(CODE_BATCH_NOT_FOUND));
    }

    private OvrsPymnSumrDetl loadLine(String bizKey, String ovrsPymnNo, String ovrsPymnDetlNo) {
        return detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(bizKey, ovrsPymnNo, ovrsPymnDetlNo)
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
        return legacyFunctions.nextBusiMatchLogSeq(bizKey);
    }

    private static String payTrnsId(String ovrsPymnNo, String ovrsPymnDetlNo) {
        return ovrsPymnNo + PAY_TRNS_ID_SEP + ovrsPymnDetlNo;
    }

    private static String nowDtm() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
    }

    private static boolean isNotBlank(String s) { return s != null && !s.isBlank(); }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
