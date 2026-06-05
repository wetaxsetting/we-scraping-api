package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.ScdulPymnTypeCd;
import com.kosign.wecms.domain.payment.payrollpayment.dto.PayrollMatchCandidateListResponse;
import com.kosign.wecms.domain.payment.payrollpayment.dto.PayrollMatchCandidateRow;
import com.kosign.wecms.domain.payment.payrollpayment.dto.PayrollMatchRequest;
import com.kosign.wecms.domain.payment.payrollpayment.dto.PayrollMatchResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Payment &gt; Payroll Payment — banking-match (Result detail) service.
 *
 * <p>Ports the legacy <b>batch path</b> of {@code comm_0197_001_r001}
 * (candidate search), {@code comm_0197_001_c001} (match) and
 * {@code comm_0197_002_u001} (unmatch) — i.e. {@code PYMN_TYPE='PAYR'}.
 * Payroll match is <b>header / batch level</b>: one {@code SCDUL_PYMN_SUMR}
 * batch links to exactly one {@code ACCT_TRNS_HSTR} bank transaction (1:1).
 *
 * <p>Writes three tables, never {@code SCDUL_PYMN_SUMR_DETL}:
 * <ul>
 *   <li>{@code BUSI_MATCH_LOG} — insert or re-lock (audit + lock; {@code MEMO})</li>
 *   <li>{@code ACCT_TRNS_HSTR} — {@code LINK_MATCH_YN}, {@code SCDUL_PYMN_NO}
 *       back-ref (via the existing {@code markBankRowMatched} /
 *       {@code clearBankRowMatch} native updates — the entity does not map the
 *       payment-FK columns)</li>
 *   <li>{@code SCDUL_PYMN_SUMR} — header {@code LINK_MATCH_YN}, {@code ACCT_SEQ_NO},
 *       {@code BANK_TRNS_SHORT_ID}, {@code BANK_TRNS_FULL_ID}</li>
 * </ul>
 *
 * <p><b>No Unmatch-All</b> (Payroll is 1:1). Separate from the bank-side
 * {@code MatchService} (legacy {@code comm_0196_001}, detail-level, opposite
 * direction) — deliberately not reused.
 *
 * <p>Tenant scoping: every read/write constrains on {@link TenantContext#currentBizKey()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollBankingMatchService {

    /* Error codes — see messages.properties. */
    private static final String CODE_BATCH_NOT_FOUND        = "WBE_PAYROLL_MATCH_BATCH_NOT_FOUND";
    private static final String CODE_BATCH_ALREADY_MATCHED  = "WBE_PAYROLL_MATCH_BATCH_ALREADY_MATCHED";
    private static final String CODE_BANK_TXN_NOT_FOUND     = "WBE_PAYROLL_MATCH_BANK_TXN_NOT_FOUND";
    private static final String CODE_BANK_TXN_ALREADY_MATCHED = "WBE_PAYROLL_MATCH_BANK_TXN_ALREADY_MATCHED";
    private static final String CODE_MATCH_ALREADY_EXISTS   = "WBE_PAYROLL_MATCH_ALREADY_EXISTS";
    private static final String CODE_MATCH_LOG_NOT_FOUND    = "WBE_PAYROLL_MATCH_LOG_NOT_FOUND";
    private static final String CODE_NOT_MATCHED            = "WBE_PAYROLL_MATCH_NOT_MATCHED";

    private static final DateTimeFormatter DTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String YES = "Y";
    private static final String NO  = "N";
    private static final String MATCH_TYPE_MANUAL = "M";

    private final ScdulPymnSumrRepository  sumrRepository;
    private final AcctTrnsHstrRepository   txnRepository;
    private final BusiMatchLogRepository   matchLogRepository;

    /* ----------------------------------------------------------------
     *  Candidate search — legacy comm_0197_001_r001 (batch path)
     * ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public PayrollMatchCandidateListResponse listCandidates(String id, String srchWd) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadPayrollBatch(bizKey, id);
        // Legacy r001 refuses to open the picker for an already-matched batch.
        if (YES.equals(master.getLinkMatchYn())) {
            throw new BusinessException(CODE_BATCH_ALREADY_MATCHED);
        }

        String keyword = (srchWd == null || srchWd.isBlank())
                ? null
                : "%" + srchWd.trim().toLowerCase() + "%";

        List<PayrollMatchCandidateRow> candidates = txnRepository
                .findPayrollMatchCandidates(
                        bizKey,
                        master.getWdrlActNo(),
                        master.getPayTranDt(),
                        master.getTotalTranAmt(),
                        keyword)
                .stream()
                .map(PayrollBankingMatchService::toCandidate)
                .toList();

        log.info("feature=payroll-payment match-candidates bizKey={} scdulPymnNo={} candidates={}",
                bizKey, master.getScdulPymnNo(), candidates.size());
        return new PayrollMatchCandidateListResponse(
                master.getScdulPymnNo(),
                master.getWdrlActNo(),
                master.getTotalTranAmt(),
                master.getPayTranDt(),
                candidates.size(),
                candidates);
    }

    /* ----------------------------------------------------------------
     *  Match — legacy comm_0197_001_c001 (batch path)
     * ---------------------------------------------------------------- */

    @Transactional
    public PayrollMatchResultResponse match(String id, PayrollMatchRequest req) {
        String bizKey = requireTenant();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        ScdulPymnSumr master = loadPayrollBatch(bizKey, id);
        if (YES.equals(master.getLinkMatchYn())) {
            throw new BusinessException(CODE_BATCH_ALREADY_MATCHED);
        }

        AcctTrnsHstr bank = txnRepository
                .findById(new AcctTrnsHstrId(bizKey, req.acctSeqNo()))
                .orElseThrow(() -> new BusinessException(CODE_BANK_TXN_NOT_FOUND));
        if (YES.equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_BANK_TXN_ALREADY_MATCHED);
        }

        String payTrnsId = master.getScdulPymnNo();   // header-level key

        // Guard: no live (locked) log already exists for this (bank txn, batch) pair.
        matchLogRepository
                .findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                        bizKey, req.bankTrnsShortId(), payTrnsId, YES)
                .ifPresent(l -> { throw new BusinessException(CODE_MATCH_ALREADY_EXISTS); });

        // 1. BUSI_MATCH_LOG — re-lock a retained (LOCKED_YN='N') row for the
        //    same pair if present, else insert a fresh one.
        BusiMatchLog logRow = matchLogRepository
                .findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                        bizKey, req.bankTrnsShortId(), payTrnsId, NO)
                .orElseGet(BusiMatchLog::new);
        boolean fresh = logRow.getMatchLogSeq() == null;
        if (fresh) {
            logRow.setBizKey(bizKey);
            logRow.setMatchLogSeq(nextMatchLogSeq(bizKey));
            logRow.setBankTrnsShortId(req.bankTrnsShortId());
            logRow.setBankTrnsFullId(req.bankTrnsFullId());
            logRow.setPayTrnsId(payTrnsId);
            logRow.setRegId(userId);
            logRow.setRegDtm(now);
        }
        logRow.setMatchTypeCd(MATCH_TYPE_MANUAL);
        logRow.setMemo(req.memo());
        logRow.setMatchedAt(now);
        logRow.setMatchedBy(userId);
        logRow.setLastMatchedAt(now);
        logRow.setLastMatchedBy(userId);
        logRow.setLockedYn(YES);
        logRow.setModId(userId);
        logRow.setModDtm(now);
        matchLogRepository.save(logRow);

        // 2. ACCT_TRNS_HSTR — LINK_MATCH_YN='Y' + SCDUL_PYMN_NO back-ref
        //    (SCDUL_PYMN_DETL_NO null — header-level). Native update via the
        //    existing repo method (entity does not map the payment-FK columns).
        int bankUpdated = txnRepository.markBankRowMatched(
                bizKey, req.acctSeqNo(), payTrnsId, null, userId, now);
        if (bankUpdated == 0) {
            throw new BusinessException(CODE_BANK_TXN_NOT_FOUND);
        }

        // 3. SCDUL_PYMN_SUMR (header) — LINK_MATCH_YN='Y' + matched-ref.
        master.setLinkMatchYn(YES);
        master.setAcctSeqNo(req.acctSeqNo());
        master.setBankTrnsShortId(req.bankTrnsShortId());
        master.setBankTrnsFullId(req.bankTrnsFullId());
        master.setModId(userId);
        master.setModDtm(now);
        sumrRepository.save(master);

        log.info("feature=payroll-payment match bizKey={} scdulPymnNo={} acctSeqNo={} bankTrnsShortId={}",
                bizKey, payTrnsId, req.acctSeqNo(), req.bankTrnsShortId());
        return new PayrollMatchResultResponse(
                payTrnsId, YES, req.acctSeqNo(),
                req.bankTrnsShortId(), req.bankTrnsFullId(), logRow.getMatchLogSeq());
    }

    /* ----------------------------------------------------------------
     *  Unmatch — legacy comm_0197_002_u001 (batch path). No Unmatch-All.
     * ---------------------------------------------------------------- */

    @Transactional
    public PayrollMatchResultResponse unmatch(String id) {
        String bizKey = requireTenant();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        ScdulPymnSumr master = loadPayrollBatch(bizKey, id);
        String payTrnsId = master.getScdulPymnNo();

        BusiMatchLog logRow = matchLogRepository
                .findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, payTrnsId, YES)
                .orElseThrow(() -> new BusinessException(CODE_MATCH_LOG_NOT_FOUND));
        if (!YES.equals(master.getLinkMatchYn())) {
            throw new BusinessException(CODE_NOT_MATCHED);
        }

        // 1. BUSI_MATCH_LOG — unlock (retain row for history).
        logRow.setLockedYn(NO);
        logRow.setLastMatchedAt(now);
        logRow.setLastMatchedBy(userId);
        logRow.setModId(userId);
        logRow.setModDtm(now);
        matchLogRepository.save(logRow);

        // 2. ACCT_TRNS_HSTR — clear the bank-side link (only if a matched txn
        //    is recorded on the header; mirrors legacy's null-guard).
        if (master.getAcctSeqNo() != null && !master.getAcctSeqNo().isBlank()) {
            txnRepository.clearBankRowMatch(bizKey, master.getAcctSeqNo(), userId, now);
        }

        // 3. SCDUL_PYMN_SUMR (header) — clear flag + matched-ref.
        master.setLinkMatchYn(NO);
        master.setAcctSeqNo(null);
        master.setBankTrnsShortId(null);
        master.setBankTrnsFullId(null);
        master.setModId(userId);
        master.setModDtm(now);
        sumrRepository.save(master);

        log.info("feature=payroll-payment unmatch bizKey={} scdulPymnNo={}", bizKey, payTrnsId);
        return new PayrollMatchResultResponse(
                payTrnsId, NO, null, null, null, logRow.getMatchLogSeq());
    }

    /* ----------------------------------------------------------------
     *  Helpers
     * ---------------------------------------------------------------- */

    private ScdulPymnSumr loadPayrollBatch(String bizKey, String id) {
        return sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, id, ScdulPymnTypeCd.PAYROLL.code())
                .orElseThrow(() -> new BusinessException(CODE_BATCH_NOT_FOUND));
    }

    private static PayrollMatchCandidateRow toCandidate(AcctTrnsHstr h) {
        return new PayrollMatchCandidateRow(
                h.getAcctSeqNo(),
                h.getBankTrnsShortId(),
                h.getBankTrnsFullId(),
                h.getTrscDt(),
                h.getTrscTm(),
                h.getTrscAmt(),
                h.getRmrk(),
                h.getRmrk2());
    }

    /** MAX(numeric match_log_seq)+1 for the tenant — same generator pattern as
     *  {@code MatchService.nextMatchLogSeq}. */
    private String nextMatchLogSeq(String bizKey) {
        long max = 0;
        for (BusiMatchLog row : matchLogRepository.findByBizKey(bizKey)) {
            try {
                long v = Long.parseLong(row.getMatchLogSeq());
                if (v > max) max = v;
            } catch (NumberFormatException ignored) {
                // non-numeric legacy seq — skip
            }
        }
        return String.valueOf(max + 1);
    }

    private static String requireTenant() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            throw new IllegalStateException("tenant context not populated");
        }
        return bizKey;
    }
}
