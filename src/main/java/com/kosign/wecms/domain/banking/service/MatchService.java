package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.CreateMatchRequest;
import com.kosign.wecms.domain.banking.dto.MatchCandidateListResponse;
import com.kosign.wecms.domain.banking.dto.MatchCandidateRow;
import com.kosign.wecms.domain.banking.dto.MatchResultResponse;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrId;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.match.service.MatchCandidateTime;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart Organizer (legacy {@code comm_0196_001} match popup).
 *
 * <p>Owns three operations on the bank-transaction ↔ payment-line link
 * pair stored across {@link AcctTrnsHstr#getLinkMatchYn()} and
 * {@link ScdulPymnSumrDetl#getLinkMatchYn()}:
 *
 * <ol>
 *   <li>{@link #listCandidates} — the legacy {@code comm_0196_001_r001} /
 *       {@code OVRS_PYMN_SUMR_DETL_R006} candidate list: domestic non-payroll
 *       detail lines, payroll batch rows, and overseas detail lines that pass
 *       the exact amount / date / transfer-time / transferred / dispatch-success
 *       / unmatched / unlocked filter set (P4).</li>
 *   <li>{@link #create} — atomically write the three-table state:
 *       {@code BUSI_MATCH_LOG} (insert or unlock-then-update),
 *       {@code ACCT_TRNS_HSTR.LINK_MATCH_YN='Y'} + payment-FK columns,
 *       {@code SCDUL_PYMN_SUMR_DETL.LINK_MATCH_YN='Y'} + bank-FK
 *       back-references.</li>
 *   <li>{@link #undo} — inverse flow: flip {@code LINK_MATCH_YN='N'}
 *       on both sides and {@code LOCKED_YN='N'} on the audit row.</li>
 * </ol>
 *
 * <p>The candidate list and {@link #create} both cover domestic
 * (detail), payroll (master), and overseas ({@code OVRS_PYMN_SUMR_DETL})
 * payments — routed by {@link CreateMatchRequest#payTypeCd()} +
 * the {@code payTrnsId} shape (bare {@code SCDUL_PYMN_NO} = payroll).
 *
 * <p>Tenant scoping: every read / write constrains on
 * {@link TenantContext#currentBizKey()}.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final String CODE_BAD_REQUEST = "WBE2002";
    private static final String CODE_TRNS_NOT_FOUND = "WBE_TRANSACTION_NOT_FOUND";
    private static final String CODE_PAY_NOT_FOUND = "WBE_MATCH_PAY_NOT_FOUND";
    private static final String CODE_ALREADY_MATCHED = "WBE_MATCH_ALREADY_MATCHED";
    private static final String CODE_PAY_ALREADY_MATCHED = "WBE_MATCH_PAY_ALREADY_MATCHED";
    private static final String CODE_NOT_MATCHED = "WBE_MATCH_NOT_MATCHED";

    private static final DateTimeFormatter DTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String PAY_TYPE_DOMESTIC = "S";
    /** P2 — overseas payment type; PAY_TRNS_ID = {@code OVRS_PYMN_NO + "_" + OVRS_PYMN_DETL_NO}. */
    private static final String PAY_TYPE_OVERSEAS = "O";
    /** Pre-built composite ID delimiter (`SCDUL_PYMN_NO + "_" + SCDUL_PYMN_DETL_NO`). */
    private static final String PAY_TRNS_ID_SEP = "_";

    private final AcctTrnsHstrRepository txnRepo;
    private final BusiMatchLogRepository matchLogRepo;
    private final ScdulPymnSumrRepository sumrRepo;
    private final ScdulPymnSumrDetlRepository detlRepo;
    /** P2 — overseas payment-detail side of the banking→payment match. */
    private final com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository ovrsDetlRepo;
    /** P4 — overseas batch master, for the candidate transfer-time floor + dispatch success. */
    private final com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository ovrsSumrRepo;
    /** P4 — shared per-line dispatch-result resolver (reused from AutoMatchService). */
    private final com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver dispatchResultResolver;
    private final AccountAccessGuard accountAccessGuard;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /** Legacy dispatch success codes ({@code proc_stat='0'} + {@code rs_cd='0000'}). */
    private static final String PROC_STAT_SUCCESS = "0";
    private static final String RS_CD_SUCCESS = "0000";
    /** Payroll batch discriminator ({@code SCDUL_PYMN_TYPE_CD}). */
    private static final String SCDUL_TYPE_PAYROLL = "00";

    /* ---------------------------------------------------------- list ---- */

    /**
     * Smart Organizer candidate list (legacy {@code comm_0196_001_r001} /
     * {@code OVRS_PYMN_SUMR_DETL_R006}). Given the bank withdrawal identified by
     * {@code trnsSeqNo}, returns every payment line it could be reconciled to,
     * across the same three branches as the legacy R006 UNION — domestic
     * non-payroll detail lines, payroll master rows, and overseas detail lines.
     *
     * <p><b>P4 legacy-parity filter set</b> (mirrors R006 exactly — this
     * replaced the earlier "all unmatched details in a date range" behaviour;
     * see {@code banking-payment-parity-p4-implementation-result.md}):
     * <ol>
     *   <li>tenant {@code BIZ_KEY};</li>
     *   <li>bank row not already matched (legacy {@code r001:183});</li>
     *   <li>exact <b>amount</b> = bank {@code TRSC_AMT} (in the query);</li>
     *   <li>exact transfer <b>date</b> = bank {@code TRSC_DT} + inside
     *       {@code [fromDt,toDt]} (in the query + {@link #matchesDateRange});</li>
     *   <li>header <b>transferred</b> {@code TRNF_YN='Y'} + detail (and SCDUL
     *       header) <b>unmatched</b> (in the query);</li>
     *   <li>payment transfer <b>time</b> ≤ bank time
     *       ({@link MatchCandidateTime} → {@link #withinBankTime});</li>
     *   <li>per-line dispatch <b>success</b> (via {@link DispatchResultResolver},
     *       route-aware — same rule {@code AutoMatchService} uses);</li>
     *   <li>no <b>locked</b> {@code BUSI_MATCH_LOG} row for the payment;</li>
     *   <li>optional keyword ({@code SRCH_WD}) + effective-remark containment
     *       (legacy {@code getDynamic0}, skipped when {@code matchRmrkYn='N'}).</li>
     * </ol>
     * There is deliberately <b>no account filter</b> — legacy R006 matches on
     * date/time/amount/remark/success regardless of account.
     *
     * @param matchRmrkYn legacy {@code MATCH_RMRK_YN} — {@code "N"} disables the
     *                    effective-remark containment filter; any other value
     *                    (incl. null) keeps it on.
     */
    @Transactional(readOnly = true)
    public MatchCandidateListResponse listCandidates(
            String acctSeqNo, String trnsSeqNo, String fromDt, String toDt,
            String keyword, String matchRmrkYn) {
        String bizKey = TenantContext.currentBizKey();
        AcctTrnsHstr bank = requireBankRow(bizKey, trnsSeqNo);
        // Legacy comm_0196_001_r001:183 — an already-matched bank row has no candidates.
        if ("Y".equals(bank.getLinkMatchYn())) {
            return new MatchCandidateListResponse(List.of(), 0);
        }
        String trscDt = bank.getTrscDt();
        BigDecimal trscAmt = bank.getTrscAmt();
        // No date or amount → nothing to anchor an exact match on (R006 binds both).
        if (trscDt == null || trscDt.isBlank() || trscAmt == null) {
            return new MatchCandidateListResponse(List.of(), 0);
        }
        // Legacy R006 requires the transfer date to be BOTH == bank date AND
        // within [fromDt,toDt]; when the caller's window excludes the bank's own
        // date the candidate set is empty.
        if (!matchesDateRange(trscDt, fromDt, toDt)) {
            return new MatchCandidateListResponse(List.of(), 0);
        }

        String bankTm = bank.getTrscTm();            // raw scraped VN-local HHmmss
        String needle = normalizeNeedle(keyword);
        // Effective bank remark for the containment filter (legacy getDynamic0).
        String effRmrk = effectiveRmrk(bank);
        boolean applyRmrk = !"N".equals(matchRmrkYn) && effRmrk != null && !effRmrk.isBlank();
        String rmrkLc = applyRmrk ? effRmrk.toLowerCase() : null;

        List<MatchCandidateRow> rows = new ArrayList<>();
        collectDomestic(bizKey, trscDt, trscAmt, bankTm, needle, rmrkLc, rows);
        collectPayroll(bizKey, trscDt, trscAmt, bankTm, needle, rows);
        collectOverseas(bizKey, trscDt, trscAmt, bankTm, needle, rmrkLc, rows);

        // Legacy: ORDER BY TRNF_SORT DESC NULLS LAST.
        rows.sort(Comparator.comparing(
                (MatchCandidateRow r) -> r.trnfDtm() == null ? "" : r.trnfDtm(),
                Comparator.reverseOrder()));
        return new MatchCandidateListResponse(rows, rows.size());
    }

    /* ---- domestic non-payroll branch (SCDUL detail) ---- */
    private void collectDomestic(String bizKey, String trscDt, BigDecimal trscAmt, String bankTm,
                                 String needle, String rmrkLc, List<MatchCandidateRow> out) {
        List<ScdulPymnSumrDetl> details = detlRepo.findDomesticBankMatchCandidates(bizKey, trscDt, trscAmt);
        if (details.isEmpty()) return;

        // Load parents once, group details per parent so dispatch results are
        // resolved in one call per batch (avoids per-line N+1).
        Map<String, ScdulPymnSumr> parents = new HashMap<>();
        for (ScdulPymnSumr s : sumrRepo.findAllById(details.stream()
                .map(d -> new ScdulPymnSumrId(d.getBizKey(), d.getScdulPymnNo()))
                .distinct().toList())) {
            parents.put(s.getScdulPymnNo(), s);
        }
        Map<String, List<ScdulPymnSumrDetl>> byBatch = new HashMap<>();
        for (ScdulPymnSumrDetl d : details) {
            byBatch.computeIfAbsent(d.getScdulPymnNo(), k -> new ArrayList<>()).add(d);
        }

        for (Map.Entry<String, List<ScdulPymnSumrDetl>> e : byBatch.entrySet()) {
            ScdulPymnSumr parent = parents.get(e.getKey());
            if (parent == null) continue;
            if (!withinBankTime(parent.getTrnfDtm(), bankTm)) continue;  // #6 time floor (batch-level)
            Map<String, DispatchResult> res = dispatchResultResolver.resolve(parent, e.getValue());
            boolean fileRoute = isFileRoute(parent.getPayFileSeqNo());
            for (ScdulPymnSumrDetl d : e.getValue()) {
                if (!domesticDispatchOk(fileRoute, res.get(d.getScdulPymnDetlNo()))) continue;   // #12
                String payTrnsId = d.getScdulPymnNo() + PAY_TRNS_ID_SEP + d.getScdulPymnDetlNo();
                if (isLocked(bizKey, payTrnsId)) continue;                                        // #11
                if (rmrkLc != null && !rmrkContains(rmrkLc, d.getTranUsag())) continue;           // #13 domestic
                if (needle != null && !matchesKeyword(d, parent, needle)) continue;              // SRCH_WD
                out.add(toCandidate(d, parent, trnfDt(parent)));
            }
        }
    }

    /* ---- payroll branch (SCDUL master, type '00') ---- */
    private void collectPayroll(String bizKey, String trscDt, BigDecimal trscAmt, String bankTm,
                                String needle, List<MatchCandidateRow> out) {
        for (ScdulPymnSumr master : sumrRepo.findPayrollBankMatchCandidates(bizKey, trscDt, trscAmt)) {
            if (!withinBankTime(master.getTrnfDtm(), bankTm)) continue;   // #6
            if (!payrollDispatchOk(bizKey, master)) continue;             // #12 (payroll rule)
            if (isLocked(bizKey, master.getScdulPymnNo())) continue;      // #11 (payTrnsId = SCDUL_PYMN_NO)
            // Legacy #13: payroll always passes the remark filter. Keyword still narrows.
            if (needle != null && !containsLc(master.getTitl(), needle)) continue;
            out.add(toPayrollCandidate(master));
        }
    }

    /* ---- overseas branch (OVRS detail) ---- */
    private void collectOverseas(String bizKey, String trscDt, BigDecimal trscAmt, String bankTm,
                                 String needle, String rmrkLc, List<MatchCandidateRow> out) {
        List<com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl> details =
                ovrsDetlRepo.findOverseasBankMatchCandidates(bizKey, trscDt, trscAmt);
        if (details.isEmpty()) return;

        Map<String, com.kosign.wecms.domain.payment.entity.OvrsPymnSumr> parents = new HashMap<>();
        Map<String, List<com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl>> byBatch = new HashMap<>();
        for (com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl d : details) {
            byBatch.computeIfAbsent(d.getOvrsPymnNo(), k -> new ArrayList<>()).add(d);
            parents.computeIfAbsent(d.getOvrsPymnNo(), k ->
                    ovrsSumrRepo.findById(new com.kosign.wecms.domain.payment.entity.OvrsPymnSumrId(bizKey, k))
                            .orElse(null));
        }

        for (Map.Entry<String, List<com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl>> e : byBatch.entrySet()) {
            com.kosign.wecms.domain.payment.entity.OvrsPymnSumr parent = parents.get(e.getKey());
            if (parent == null) continue;
            if (!withinBankTime(parent.getTrnfDt(), bankTm)) continue;    // #6 (overseas date/time = TRNF_DT)
            Map<String, DispatchResult> res = dispatchResultResolver.resolveOverseas(parent, e.getValue());
            for (com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl d : e.getValue()) {
                // Overseas is always SWIFT/file-route (DispatchResultResolver.resolveOverseas has
                // no API branch — P2 precedent); success = proc='0' + rs_cd='0000'.
                if (!isSuccess(res.get(d.getOvrsPymnDetlNo()))) continue;                          // #12
                String payTrnsId = d.getOvrsPymnNo() + PAY_TRNS_ID_SEP + d.getOvrsPymnDetlNo();
                if (isLocked(bizKey, payTrnsId)) continue;                                         // #11
                if (rmrkLc != null && !rmrkContains(rmrkLc, d.getMemo())) continue;                // #13 overseas (MEMO)
                if (needle != null && !containsLc(d.getBnfcNm(), needle) && !containsLc(d.getMemo(), needle)) continue;
                out.add(toOverseasCandidate(d, parent));
            }
        }
    }

    /* -------------------------------------------------------- create ---- */

    /**
     * Match the bank transaction to the selected payment line.
     * Atomically writes {@code BUSI_MATCH_LOG} (insert or unlock-then-
     * update) + flips {@code LINK_MATCH_YN='Y'} on both sides.
     */
    @Transactional
    public MatchResultResponse create(String acctSeqNo, String trnsSeqNo, CreateMatchRequest req) {
        if (req == null) throw new BusinessException(CODE_BAD_REQUEST);
        // P2 — overseas is now wired (was fail-closed). Domestic falls through.
        if (PAY_TYPE_OVERSEAS.equals(req.payTypeCd())) {
            return createOverseas(trnsSeqNo, req);
        }
        if (!PAY_TYPE_DOMESTIC.equals(req.payTypeCd())) {
            throw new BusinessException(CODE_BAD_REQUEST);
        }

        String bizKey = TenantContext.currentBizKey();
        AcctTrnsHstr bank = requireBankRow(bizKey, trnsSeqNo);
        if ("Y".equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_ALREADY_MATCHED);
        }

        // P4 — payroll master-level match: legacy PAY_TRNS_ID is the bare
        // SCDUL_PYMN_NO (no "_"). Domestic detail always has "_", so an empty
        // parse unambiguously means the payroll batch branch.
        if (parsePayTrnsId(req.payTrnsId()).isEmpty()) {
            return createPayrollMaster(bizKey, bank, trnsSeqNo, req);
        }

        ScdulPymnSumrDetl detail = parsePayTrnsId(req.payTrnsId())
                .map(parts -> detlRepo.findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(
                        bizKey, parts[0], parts[1]).orElse(null))
                .orElse(null);
        if (detail == null) throw new BusinessException(CODE_PAY_NOT_FOUND);
        if ("Y".equals(detail.getLinkMatchYn())) {
            throw new BusinessException(CODE_PAY_ALREADY_MATCHED);
        }

        // Cross-tenant probe defence — `findByBizKeyAnd*` already
        // constrains, but the lock-row check below is keyed by
        // (bizKey, payTrnsId), so a malicious replay across tenants
        // can never collide.
        matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, req.payTrnsId(), "Y")
                .ifPresent(rp -> { throw new BusinessException(CODE_PAY_ALREADY_MATCHED); });

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        // 1. BUSI_MATCH_LOG — insert new, or unlock-then-update if a
        //    historical row for the same pair exists.
        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(
                        bizKey, nullToEmpty(bank.getBankTrnsShortId()), "N")
                .orElseGet(BusiMatchLog::new);
        boolean isFresh = log.getMatchLogSeq() == null;
        if (isFresh) {
            log.setBizKey(bizKey);
            log.setMatchLogSeq(nextMatchLogSeq(bizKey));
            log.setBankTrnsShortId(nullToEmpty(bank.getBankTrnsShortId()));
            log.setBankTrnsFullId(nullToEmpty(bank.getBankTrnsFullId()));
            log.setPayTrnsId(req.payTrnsId());
            log.setRegId(userId);
            log.setRegDtm(nowDtm);
        }
        log.setMatchTypeCd("M");
        log.setMemo(req.memo());
        log.setMatchedAt(nowDtm);
        log.setMatchedBy(userId);
        log.setLastMatchedAt(nowDtm);
        log.setLastMatchedBy(userId);
        log.setLockedYn("Y");
        log.setModId(userId);
        log.setModDtm(nowDtm);
        matchLogRepo.save(log);

        // 2. ACCT_TRNS_HSTR — set LINK_MATCH_YN='Y' + payment FKs.
        int bankUpdated = txnRepo.markBankRowMatched(
                bizKey, trnsSeqNo,
                detail.getScdulPymnNo(), detail.getScdulPymnDetlNo(),
                userId, nowDtm);
        if (bankUpdated == 0) throw new BusinessException(CODE_TRNS_NOT_FOUND);

        // 3. SCDUL_PYMN_SUMR_DETL — set LINK_MATCH_YN='Y' + bank refs.
        int detlUpdated = detlRepo.markDetailMatched(
                bizKey, detail.getScdulPymnNo(), detail.getScdulPymnDetlNo(),
                trnsSeqNo,
                nullToEmpty(bank.getBankTrnsShortId()),
                nullToEmpty(bank.getBankTrnsFullId()),
                userId, nowDtm);
        if (detlUpdated == 0) throw new BusinessException(CODE_PAY_NOT_FOUND);

        return new MatchResultResponse(trnsSeqNo, "Y", req.payTrnsId(), log.getMatchLogSeq());
    }

    /* ------------------------------------------------ overseas create ---- */

    /** P2 — banking→payment match against an overseas payment line
     *  ({@code payTypeCd='O'}). Mirrors the domestic {@link #create} body but
     *  writes the {@code OVRS_PYMN_SUMR_DETL} row + the OVRS bank-FK pair, and
     *  is symmetric with {@code OverseasPaymentLineMatchService} (same
     *  composite {@code OVRS_PYMN_NO + "_" + OVRS_PYMN_DETL_NO} PAY_TRNS_ID). */
    private MatchResultResponse createOverseas(String trnsSeqNo, CreateMatchRequest req) {
        String bizKey = TenantContext.currentBizKey();
        AcctTrnsHstr bank = requireBankRow(bizKey, trnsSeqNo);
        if ("Y".equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_ALREADY_MATCHED);
        }

        String[] parts = parsePayTrnsId(req.payTrnsId()).orElse(null);
        com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl detail = parts == null ? null
                : ovrsDetlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(bizKey, parts[0], parts[1])
                        .orElse(null);
        if (detail == null) throw new BusinessException(CODE_PAY_NOT_FOUND);
        if ("Y".equals(detail.getLinkMatchYn())) {
            throw new BusinessException(CODE_PAY_ALREADY_MATCHED);
        }

        matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, req.payTrnsId(), "Y")
                .ifPresent(rp -> { throw new BusinessException(CODE_PAY_ALREADY_MATCHED); });

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        // BUSI_MATCH_LOG — insert or re-lock a retained row (same idiom as domestic).
        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(
                        bizKey, nullToEmpty(bank.getBankTrnsShortId()), "N")
                .orElseGet(BusiMatchLog::new);
        if (log.getMatchLogSeq() == null) {
            log.setBizKey(bizKey);
            log.setMatchLogSeq(nextMatchLogSeq(bizKey));
            log.setBankTrnsShortId(nullToEmpty(bank.getBankTrnsShortId()));
            log.setBankTrnsFullId(nullToEmpty(bank.getBankTrnsFullId()));
            log.setPayTrnsId(req.payTrnsId());
            log.setRegId(userId);
            log.setRegDtm(nowDtm);
        }
        log.setMatchTypeCd("M");
        log.setMemo(req.memo());
        log.setMatchedAt(nowDtm);
        log.setMatchedBy(userId);
        log.setLastMatchedAt(nowDtm);
        log.setLastMatchedBy(userId);
        log.setLockedYn("Y");
        log.setModId(userId);
        log.setModDtm(nowDtm);
        matchLogRepo.save(log);

        // ACCT_TRNS_HSTR — stamp the OVRS FK pair (nulls the SCDUL pair).
        if (txnRepo.markBankRowMatchedOverseas(bizKey, trnsSeqNo,
                detail.getOvrsPymnNo(), detail.getOvrsPymnDetlNo(), userId, nowDtm) == 0) {
            throw new BusinessException(CODE_TRNS_NOT_FOUND);
        }
        // OVRS_PYMN_SUMR_DETL — LINK_MATCH_YN='Y' + ACCT_SEQ_NO + bank ids.
        if (ovrsDetlRepo.markOverseasDetailMatched(bizKey, detail.getOvrsPymnNo(),
                detail.getOvrsPymnDetlNo(), trnsSeqNo,
                nullToEmpty(bank.getBankTrnsShortId()), nullToEmpty(bank.getBankTrnsFullId()),
                userId, nowDtm) == 0) {
            throw new BusinessException(CODE_PAY_NOT_FOUND);
        }

        return new MatchResultResponse(trnsSeqNo, "Y", req.payTrnsId(), log.getMatchLogSeq());
    }

    /* ------------------------------------------------ payroll create ---- */

    /** P4 — banking→payment match against a payroll <b>batch</b>
     *  ({@code payTypeCd='S'}, {@code payTrnsId} = bare {@code SCDUL_PYMN_NO}).
     *  Payroll matches 1:1 at the header level (same as {@code AutoMatchService}
     *  and {@code PayrollBankingMatchService}) — it stamps
     *  {@code SCDUL_PYMN_SUMR.LINK_MATCH_YN='Y'} + the bank-FK pair on the master
     *  row, NOT a detail row, and writes {@code BUSI_MATCH_LOG.PAY_TRNS_ID} as the
     *  bare {@code SCDUL_PYMN_NO}. */
    private MatchResultResponse createPayrollMaster(
            String bizKey, AcctTrnsHstr bank, String trnsSeqNo, CreateMatchRequest req) {
        ScdulPymnSumr master = sumrRepo
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(bizKey, req.payTrnsId(), SCDUL_TYPE_PAYROLL)
                .orElseThrow(() -> new BusinessException(CODE_PAY_NOT_FOUND));
        if ("Y".equals(master.getLinkMatchYn())) {
            throw new BusinessException(CODE_PAY_ALREADY_MATCHED);
        }
        matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, req.payTrnsId(), "Y")
                .ifPresent(rp -> { throw new BusinessException(CODE_PAY_ALREADY_MATCHED); });

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(
                        bizKey, nullToEmpty(bank.getBankTrnsShortId()), "N")
                .orElseGet(BusiMatchLog::new);
        if (log.getMatchLogSeq() == null) {
            log.setBizKey(bizKey);
            log.setMatchLogSeq(nextMatchLogSeq(bizKey));
            log.setBankTrnsShortId(nullToEmpty(bank.getBankTrnsShortId()));
            log.setBankTrnsFullId(nullToEmpty(bank.getBankTrnsFullId()));
            log.setPayTrnsId(req.payTrnsId());
            log.setRegId(userId);
            log.setRegDtm(nowDtm);
        }
        log.setMatchTypeCd("M");
        log.setMemo(req.memo());
        log.setMatchedAt(nowDtm);
        log.setMatchedBy(userId);
        log.setLastMatchedAt(nowDtm);
        log.setLastMatchedBy(userId);
        log.setLockedYn("Y");
        log.setModId(userId);
        log.setModDtm(nowDtm);
        matchLogRepo.save(log);

        // ACCT_TRNS_HSTR — stamp the SCDUL master key (detail slot null = batch-level).
        if (txnRepo.markBankRowMatched(bizKey, trnsSeqNo,
                master.getScdulPymnNo(), null, userId, nowDtm) == 0) {
            throw new BusinessException(CODE_TRNS_NOT_FOUND);
        }
        // SCDUL_PYMN_SUMR — flip the master link + bank refs (same idiom as AutoMatchService).
        master.setLinkMatchYn("Y");
        master.setAcctSeqNo(trnsSeqNo);
        master.setBankTrnsShortId(nullToEmpty(bank.getBankTrnsShortId()));
        master.setBankTrnsFullId(nullToEmpty(bank.getBankTrnsFullId()));
        master.setModId(userId);
        master.setModDtm(nowDtm);
        sumrRepo.save(master);

        return new MatchResultResponse(trnsSeqNo, "Y", req.payTrnsId(), log.getMatchLogSeq());
    }

    /* ---------------------------------------------------------- undo ---- */

    /** Undo a match — flips {@code LINK_MATCH_YN='N'} on both sides and
     *  {@code LOCKED_YN='N'} on the audit row (preserves history). */
    @Transactional
    public MatchResultResponse undo(String acctSeqNo, String trnsSeqNo) {
        String bizKey = TenantContext.currentBizKey();
        AcctTrnsHstr bank = requireBankRow(bizKey, trnsSeqNo);
        if (!"Y".equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_NOT_MATCHED);
        }

        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(
                        bizKey, nullToEmpty(bank.getBankTrnsShortId()), "Y")
                .orElseThrow(() -> new BusinessException(CODE_NOT_MATCHED));

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        // 1. Unlock the audit row (don't delete — preserves history).
        log.setLockedYn("N");
        log.setLastMatchedAt(nowDtm);
        log.setLastMatchedBy(userId);
        log.setModId(userId);
        log.setModDtm(nowDtm);
        matchLogRepo.save(log);

        // 2. Clear payment-side link. Try domestic first; when no domestic
        //    detail row was matched (P2 overseas match), clear the overseas
        //    detail instead. Type-agnostic so a match made from either payment
        //    side (SCDUL or OVRS) is undone correctly from the banking side.
        String[] parts = parsePayTrnsId(log.getPayTrnsId()).orElse(null);
        if (parts != null) {
            int cleared = detlRepo.clearDetailMatch(bizKey, parts[0], parts[1], userId, nowDtm);
            if (cleared == 0) {
                ovrsDetlRepo.clearOverseasDetailMatch(bizKey, parts[0], parts[1], userId, nowDtm);
            }
        } else {
            // P4 — payroll batch match: PAY_TRNS_ID is the bare SCDUL_PYMN_NO,
            // so the payment side lives on the master row (no detail / overseas
            // row was stamped). Flip the master link + clear the bank refs.
            sumrRepo.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                    bizKey, log.getPayTrnsId(), SCDUL_TYPE_PAYROLL)
                    .ifPresent(master -> {
                        master.setLinkMatchYn("N");
                        master.setAcctSeqNo(null);
                        master.setBankTrnsShortId(null);
                        master.setBankTrnsFullId(null);
                        master.setModId(userId);
                        master.setModDtm(nowDtm);
                        sumrRepo.save(master);
                    });
        }

        // 3. Clear bank-side link.
        int updated = txnRepo.clearBankRowMatch(bizKey, trnsSeqNo, userId, nowDtm);
        if (updated == 0) throw new BusinessException(CODE_TRNS_NOT_FOUND);

        return new MatchResultResponse(trnsSeqNo, "N", log.getPayTrnsId(), log.getMatchLogSeq());
    }

    /* -------------------------------------------------------- helpers --- */

    private AcctTrnsHstr requireBankRow(String bizKey, String trnsSeqNo) {
        if (trnsSeqNo == null || trnsSeqNo.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST);
        }
        AcctTrnsHstr bank = txnRepo.findById(new AcctTrnsHstrId(bizKey, trnsSeqNo))
                .orElseThrow(() -> new BusinessException(CODE_TRNS_NOT_FOUND));
        // Audit #29 — per-account data scope on the match/unmatch/candidates
        // paths (Smart Organizer). Non-admins may only act on accounts they
        // hold an active View grant for; throws 403 otherwise.
        accountAccessGuard.assertCanView(bizKey, bank.getBankCd(), bank.getAcctNo());
        return bank;
    }

    private static java.util.Optional<String[]> parsePayTrnsId(String payTrnsId) {
        if (payTrnsId == null) return java.util.Optional.empty();
        int sep = payTrnsId.indexOf(PAY_TRNS_ID_SEP);
        if (sep <= 0 || sep == payTrnsId.length() - 1) return java.util.Optional.empty();
        return java.util.Optional.of(new String[]{
                payTrnsId.substring(0, sep),
                payTrnsId.substring(sep + 1)
        });
    }

    /** MATCH_LOG_SEQ via the single wb_get_next_val funnel (V140 #24 —
     *  per-tenant MAX+1, verbatim from the live JEX function). */
    private String nextMatchLogSeq(String bizKey) {
        return legacyFunctions.nextBusiMatchLogSeq(bizKey);
    }

    /** {@code yyyyMMddHHmmss} → {@code yyyyMMdd} for filter matching;
     *  falls back to {@code REG_DTM} when {@code TRNF_DTM} is null
     *  (pre-dispatch parity). */
    private static String trnfDt(ScdulPymnSumr parent) {
        String src = parent.getTrnfDtm() != null && parent.getTrnfDtm().length() >= 8
                ? parent.getTrnfDtm()
                : parent.getRegDtm() != null ? parent.getRegDtm() : "";
        return src.length() >= 8 ? src.substring(0, 8) : src;
    }

    private static boolean matchesDateRange(String trnfDt, String fromDt, String toDt) {
        if (fromDt != null && !fromDt.isBlank() && trnfDt.compareTo(fromDt) < 0) return false;
        if (toDt != null && !toDt.isBlank() && trnfDt.compareTo(toDt) > 0) return false;
        return true;
    }

    private static boolean matchesKeyword(ScdulPymnSumrDetl d, ScdulPymnSumr parent, String lcNeedle) {
        return containsLc(d.getRcptNm(), lcNeedle)
                || containsLc(d.getTranUsag(), lcNeedle)
                || containsLc(d.getTrcoCmnm(), lcNeedle)
                || containsLc(parent.getTitl(), lcNeedle);
    }

    private static boolean containsLc(String haystack, String lcNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lcNeedle);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private MatchCandidateRow toCandidate(
            ScdulPymnSumrDetl d, ScdulPymnSumr parent, String trnfDt) {
        BigDecimal amt = d.getTranAmt() != null ? d.getTranAmt() : BigDecimal.ZERO;
        return new MatchCandidateRow(
                d.getScdulPymnNo() + PAY_TRNS_ID_SEP + d.getScdulPymnDetlNo(),
                PAY_TYPE_DOMESTIC,
                d.getScdulPymnNo(),
                d.getScdulPymnDetlNo(),
                parent.getScdulPymnTypeCd(),
                trnfDt,
                parent.getTrnfDtm(),
                AmountFormatter.wire(amt),
                d.getTrnfCcyCd() != null ? d.getTrnfCcyCd() : parent.getCcyCd(),
                d.getRcptNm() != null ? d.getRcptNm() : (d.getTrcoCmnm() != null ? d.getTrcoCmnm() : ""),
                d.getTarBankCd(),
                null,  // BNFC_BANK_NM — resolved on the frontend via the existing bank-name map.
                d.getFincMdaNo(),
                d.getTranUsag(),
                parent.getTitl(),
                null, null, null,          // overseas identity — n/a on the domestic branch
                d.getNote()                // MEMO ← domestic NOTE (legacy R006 domestic branch)
        );
    }

    /** Payroll master row (legacy R006 payroll branch — 1 row per batch). */
    private MatchCandidateRow toPayrollCandidate(ScdulPymnSumr master) {
        BigDecimal amt = master.getTotalTranAmt() != null ? master.getTotalTranAmt() : BigDecimal.ZERO;
        return new MatchCandidateRow(
                master.getScdulPymnNo(),   // PAY_TRNS_ID = bare SCDUL_PYMN_NO (legacy)
                PAY_TYPE_DOMESTIC,
                master.getScdulPymnNo(),
                null,                      // no detail row
                master.getScdulPymnTypeCd(),   // "00"
                trnfDt(master),
                master.getTrnfDtm(),
                AmountFormatter.wire(amt),
                master.getCcyCd(),
                null,                      // RCPT_NM null (legacy)
                null, null,                // BNFC_BANK_CD / BNFC_BANK_NM null (legacy)
                master.getWdrlActNo(),     // FINC_MDA_NO ← S.WDRL_ACT_NO (legacy)
                master.getTitl(),          // TRAN_USAG ← S.TITL (legacy)
                master.getTitl(),
                null, null, null, null
        );
    }

    /** Overseas detail row (legacy R006 overseas branch). */
    private MatchCandidateRow toOverseasCandidate(
            com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl d,
            com.kosign.wecms.domain.payment.entity.OvrsPymnSumr parent) {
        BigDecimal amt = d.getTrnfAmt() != null ? d.getTrnfAmt() : BigDecimal.ZERO;
        String trnfDt = parent.getTrnfDt() != null && parent.getTrnfDt().length() >= 8
                ? parent.getTrnfDt().substring(0, 8) : "";
        return new MatchCandidateRow(
                d.getOvrsPymnNo() + PAY_TRNS_ID_SEP + d.getOvrsPymnDetlNo(),
                PAY_TYPE_OVERSEAS,
                null, null, null,          // SCDUL identity — n/a on the overseas branch
                trnfDt,
                parent.getTrnfDt(),
                AmountFormatter.wire(amt),
                d.getCcyCd(),              // OvrsPymnSumrDetl.ccyCd maps TRNF_CCY_CD
                d.getBnfcNm(),             // RCPT_NM ← BNFC_NM (legacy)
                d.getBnfcBankCd(),
                d.getBnfcBankNm(),
                d.getBnfcAcctNo(),
                null,                      // TRAN_USAG null on overseas (legacy)
                null,                      // TITL null on overseas
                d.getOvrsPymnNo(),
                d.getOvrsPymnDetlNo(),
                parent.getOvrsPymnTypeCd(),
                d.getMemo()                // MEMO ← overseas MEMO
        );
    }

    /* -------------------------------------------- candidate filters ----- */

    private static String normalizeNeedle(String keyword) {
        if (keyword == null) return null;
        String n = keyword.trim().toLowerCase();
        return n.isEmpty() ? null : n;
    }

    /**
     * Effective bank remark used by the containment filter (legacy
     * {@code comm_0196_001_r001_act.jsp getDynamic0}). Bank {@code 11704020}
     * uses {@code RMRK2} for debits ({@code INOUT_DV='2'}) / {@code RMRK}
     * otherwise; every other bank concatenates {@code RMRK2 " - " RMRK}.
     */
    private static String effectiveRmrk(AcctTrnsHstr bank) {
        String rmrk = nullToEmpty(bank.getRmrk());
        String rmrk2 = nullToEmpty(bank.getRmrk2());
        if ("11704020".equals(bank.getBankCd())) {
            return "2".equals(bank.getInoutDv()) ? rmrk2 : rmrk;
        }
        boolean hasR = !rmrk.isBlank();
        boolean hasR2 = !rmrk2.isBlank();
        if (hasR2 && hasR) return rmrk2 + " - " + rmrk;
        if (hasR2) return rmrk2;
        return rmrk;
    }

    /**
     * Legacy transfer-time floor (#6):
     * {@code (:BANK_TRSC_TM IS NULL/'' OR paymentTime <= :BANK_TRSC_TM)}.
     * The payment's UTC {@code TRNF_DTM/TRNF_DT} is shifted to VN-local
     * {@code HHmmss} via {@link MatchCandidateTime} (same helper P3 uses) so it
     * compares against the raw scraped VN-local bank {@code TRSC_TM}. Blank bank
     * time or an unparseable payment time passes (mirrors the legacy string
     * comparison where an empty slice sorts before any non-empty bank time).
     */
    private static boolean withinBankTime(String parentDtm14, String bankTrscTm) {
        if (bankTrscTm == null || bankTrscTm.isBlank()) return true;
        String payLocal = MatchCandidateTime.localTransferTime(parentDtm14);
        if (payLocal == null || payLocal.isBlank()) return true;
        return payLocal.compareTo(bankTrscTm) <= 0;
    }

    private static boolean isFileRoute(String payFileSeqNo) {
        return payFileSeqNo != null && !payFileSeqNo.isBlank();
    }

    /**
     * Domestic per-line dispatch success (#12). File route requires
     * {@code PROC_STAT='0'} AND line {@code RS_CD='0000'} (legacy file EXISTS
     * block); API route requires only {@code TB_PAY_TARGET PROC_STAT='0'} (legacy
     * api EXISTS block does not check RS_CD).
     */
    private static boolean domesticDispatchOk(boolean fileRoute, DispatchResult dr) {
        if (dr == null) return false;
        if (fileRoute) {
            return PROC_STAT_SUCCESS.equals(dr.procStat()) && RS_CD_SUCCESS.equals(dr.rsCd());
        }
        return PROC_STAT_SUCCESS.equals(dr.procStat());
    }

    /**
     * Payroll dispatch success (#12, legacy R006 payroll branch):
     * {@code EXISTS(TB_PAY_FILE_MST PROC_STAT='0') OR NOT EXISTS(TB_PAY_FILE_MST)}
     * — i.e. a file master with {@code PROC_STAT='0'}, or no file master at all
     * (API/no-file batch). RS_CD is NOT checked for payroll (legacy parity).
     */
    private boolean payrollDispatchOk(String bizKey, ScdulPymnSumr master) {
        if (!isFileRoute(master.getPayFileSeqNo())) return true;   // NOT EXISTS(file master) → pass
        List<ScdulPymnSumrDetl> lines = detlRepo.findByBizKeyAndScdulPymnNo(
                bizKey, master.getScdulPymnNo(), ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC);
        if (lines.isEmpty()) return false;
        Map<String, DispatchResult> res = dispatchResultResolver.resolve(master, lines);
        // File path: PROC_STAT is master-level (same for every line) — check any.
        return lines.stream()
                .map(l -> res.get(l.getScdulPymnDetlNo()))
                .filter(java.util.Objects::nonNull)
                .anyMatch(dr -> PROC_STAT_SUCCESS.equals(dr.procStat()));
    }

    /** Strict per-line success (proc='0' + rs_cd='0000') — used by the overseas branch. */
    private static boolean isSuccess(DispatchResult dr) {
        return dr != null
                && PROC_STAT_SUCCESS.equals(dr.procStat())
                && RS_CD_SUCCESS.equals(dr.rsCd());
    }

    /** Locked-{@code BUSI_MATCH_LOG} exclusion (#11) — a payment already locked
     *  to another bank line is not a candidate. */
    private boolean isLocked(String bizKey, String payTrnsId) {
        return matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, payTrnsId, "Y").isPresent();
    }

    /** Legacy remark containment (#13): the bank's effective remark (lowercased)
     *  must contain the payment's usage/memo text. Blank payment text ⇒ excluded
     *  (legacy requires {@code COALESCE(..,'') <> ''}). */
    private static boolean rmrkContains(String rmrkLc, String payText) {
        if (payText == null || payText.isBlank()) return false;
        return rmrkLc.contains(payText.trim().toLowerCase());
    }
}
