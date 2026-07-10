package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.nxisas.BankTrnsIdGenerator;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrId;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * P1 auto-matching engine — links freshly-scraped bank <b>withdrawal</b>
 * transactions to the outgoing payment that produced them, writing the
 * {@code BUSI_MATCH_LOG} audit row + the {@code LINK_MATCH_YN} flag on both
 * sides. This is the automated counterpart of the manual
 * {@link MatchService} / {@code DomesticPaymentLineMatchService} /
 * {@code PayrollBankingMatchService}; those stay untouched.
 *
 * <p><b>Legacy baseline:</b> ported from the auto-match block in
 * {@code fnsh_0001_001_c001_act.jsp:1853-2079} (candidate query
 * {@code OVRS_PYMN_SUMR_DETL_R007}). Behaviour is kept to legacy parity —
 * do not "improve" the algorithm without a PO decision.
 *
 * <p><b>Conditions</b> for a candidate (all required):
 * <ol>
 *   <li>same withdrawal account ({@code WDRL_ACT_NO = ACCT_NO})</li>
 *   <li>payment already transferred ({@code TRNF_YN='Y'})</li>
 *   <li>payment result successful ({@code proc_stat='0'} + {@code rs_cd='0000'})</li>
 *   <li>payment not already matched ({@code LINK_MATCH_YN != 'Y'})</li>
 *   <li>same transfer date (exact, {@code TRNF_DTM[0..8] = TRSC_DT})</li>
 *   <li>payment transfer time {@literal <} banking transaction time
 *       (skipped when the bank row has no time — legacy)</li>
 *   <li>same amount (exact; line {@code TRAN_AMT} for domestic, batch
 *       {@code TOTAL_TRAN_AMT} for payroll)</li>
 *   <li>payment remark contained in the bank remark after
 *       {@link BankTrnsIdGenerator#cleanRemark} normalisation (skipped when
 *       either side is blank — legacy)</li>
 *   <li><b>exactly one</b> candidate across the domestic + payroll union —
 *       0 or {@literal >}1 ⇒ skip (logged)</li>
 * </ol>
 *
 * <p><b>Scope — strict legacy parity with {@code OVRS_PYMN_SUMR_DETL_R007}:</b>
 * <ul>
 *   <li><b>Domestic scheduled — detail-level:</b> Vendor ({@code '01'}),
 *       Expense ({@code '02'}), Internal Transfer ({@code '03'}) —
 *       legacy R007 Branch 1 ({@code SCDUL_PYMN_TYPE_CD <> '00'}).</li>
 *   <li><b>Overseas — detail-level:</b> {@code OVRS_PYMN_SUMR_DETL} —
 *       legacy R007 Branch 3.</li>
 *   <li><b>Payroll ({@code SCDUL_PYMN_TYPE_CD = '00'}) is NOT auto-matched.</b>
 *       Legacy R007 Branch 1 excludes it ({@code <> '00'}) and the legacy
 *       auto-match block has no payroll code path — payroll is reconciled only
 *       via the manual Payroll match popup. The refactor therefore never
 *       queries payroll here and never writes {@code SCDUL_PYMN_SUMR}
 *       (master/header) through the auto-match flow.</li>
 * </ul>
 * Both matched types write {@code LINK_MATCH_YN='Y'} on the <em>detail</em>
 * table only ({@code SCDUL_PYMN_SUMR_DETL} / {@code OVRS_PYMN_SUMR_DETL}) plus
 * the bank-side {@code ACCT_TRNS_HSTR} FK — exactly as legacy
 * {@code fnsh_0001_001_c001_act.jsp:2041-2065} does for {@code PAY_TYPE_CD}
 * {@code 'S'} / {@code 'O'}.
 *
 * <p><b>Feature gate:</b> runs only when the tenant's business-environment
 * setting {@code 00079} = {@code 'Y'} (legacy biz-env key). Absent / any
 * other value ⇒ disabled (fail-safe default — never global).
 *
 * <p><b>Transaction / error handling:</b> {@link #autoMatchNewDebits} is
 * invoked inside the bank-sync transaction (the just-inserted rows must be
 * visible), so it shares that transaction. Each debit is processed under its
 * own try/catch — a logic failure on one row is logged and skipped, never
 * aborting the sync (legacy parity: "auto-match failure must NOT abort the
 * scraping"). Caveat: a DB-level failure during a match write can still mark
 * the shared transaction rollback-only; the caller also wraps the whole call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMatchService {

    /** Legacy biz-env flag that enables auto-match per tenant. */
    static final String ENV_AUTO_MATCH = "00079";
    private static final String YES = "Y";
    private static final String NO  = "N";
    private static final String DEBIT = "2";               // INOUT_DV: withdrawal
    private static final String MATCH_TYPE_AUTO = "A";
    private static final String PROC_STAT_SUCCESS = "0";
    private static final String RS_CD_SUCCESS = "0000";
    private static final String PAY_TRNS_ID_SEP = "_";
    private static final DateTimeFormatter DTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AcctTrnsHstrRepository txnRepo;
    private final BusiMatchLogRepository matchLogRepo;
    private final ScdulPymnSumrRepository sumrRepo;
    private final ScdulPymnSumrDetlRepository detlRepo;
    private final OvrsPymnSumrRepository ovrsSumrRepo;
    private final OvrsPymnDtlsRepository ovrsDetlRepo;
    private final DispatchResultResolver dispatchResultResolver;
    private final LegacyFunctionRepository legacyFunctions;
    private final BusiEnvSetInfmRepository envRepo;

    /** Payment family a candidate belongs to — drives the match-write path. */
    private enum PayType { DOMESTIC, OVERSEAS }

    /**
     * One resolved candidate payment for a bank debit. {@code type} selects
     * which detail table the match write targets: {@code DOMESTIC} →
     * {@code SCDUL_PYMN_SUMR_DETL} (keys {@code scdulPymnNo}/{@code scdulPymnDetlNo});
     * {@code OVERSEAS} → {@code OVRS_PYMN_SUMR_DETL} (keys
     * {@code ovrsPymnNo}/{@code ovrsPymnDetlNo}). Payroll is intentionally
     * absent — legacy does not auto-match it.
     */
    private record Candidate(PayType type, String payTrnsId,
                             String scdulPymnNo, String scdulPymnDetlNo,
                             String ovrsPymnNo, String ovrsPymnDetlNo) {
        static Candidate domestic(String payTrnsId, String pymnNo, String detlNo) {
            return new Candidate(PayType.DOMESTIC, payTrnsId, pymnNo, detlNo, null, null);
        }
        static Candidate overseas(String payTrnsId, String pymnNo, String detlNo) {
            return new Candidate(PayType.OVERSEAS, payTrnsId, null, null, pymnNo, detlNo);
        }
    }

    /**
     * Attempt to auto-match every newly-inserted debit row for one account.
     *
     * @param bizKey     tenant
     * @param userId     the sync operator (stamped as MATCHED_BY / MOD_ID)
     * @param acctNo     the withdrawal account the rows belong to
     * @param newDebits  the {@code ACCT_TRNS_HSTR} rows just persisted by the
     *                   sync (only {@code INOUT_DV='2'} are considered)
     * @return number of rows auto-matched
     */
    public int autoMatchNewDebits(String bizKey, String userId, String acctNo,
                                  List<AcctTrnsHstr> newDebits) {
        if (newDebits == null || newDebits.isEmpty()) return 0;
        if (!isEnabled(bizKey)) {
            log.debug("feature=auto-match disabled (biz-env {} != 'Y') bizKey={}", ENV_AUTO_MATCH, bizKey);
            return 0;
        }

        int matched = 0;
        for (AcctTrnsHstr debit : newDebits) {
            if (!DEBIT.equals(debit.getInoutDv())) continue;
            try {
                if (matchOne(bizKey, userId, acctNo, debit)) matched++;
            } catch (Exception ex) {
                // Never abort the sync for one row (legacy parity).
                log.error("feature=auto-match error bizKey={} acctSeqNo={}",
                        bizKey, debit.getAcctSeqNo(), ex);
            }
        }
        if (matched > 0) {
            log.info("feature=auto-match matched={} bizKey={} acctNo={}", matched, bizKey, acctNo);
        }
        return matched;
    }

    /* ---------------------------------------------------------- one debit -- */

    private boolean matchOne(String bizKey, String userId, String acctNo, AcctTrnsHstr debit) {
        List<Candidate> candidates = new ArrayList<>();
        String bankTm   = debit.getTrscTm();
        String bankRmrk = debit.getRmrk();

        // Domestic (Vendor '01' / Expense '02' / Internal Transfer '03') —
        // detail-level. Legacy R007 Branch 1 (SCDUL_PYMN_TYPE_CD <> '00').
        for (ScdulPymnSumrDetl d : detlRepo.findDomesticAutoMatchCandidates(
                bizKey, acctNo, debit.getTrscDt(), debit.getTrscAmt())) {
            ScdulPymnSumr master = sumrRepo.findById(
                    new ScdulPymnSumrId(bizKey, d.getScdulPymnNo())).orElse(null);
            if (master == null) continue;
            if (!transferTimeBeforeBankTime(master.getTrnfDtm(), bankTm)) continue;
            if (!remarkContained(bankRmrk, d.getTranUsag())) continue;
            Map<String, DispatchResult> res = dispatchResultResolver.resolve(master, List.of(d));
            if (!isSuccess(res.get(d.getScdulPymnDetlNo()))) continue;
            candidates.add(Candidate.domestic(
                    d.getScdulPymnNo() + PAY_TRNS_ID_SEP + d.getScdulPymnDetlNo(),
                    d.getScdulPymnNo(), d.getScdulPymnDetlNo()));
        }

        // Overseas — detail-level. Legacy R007 Branch 3
        // (OVRS_PYMN_SUMR_DETL). Overseas has no per-line remark other than
        // MEMO and the batch TRNF_DT is date-only, so the transfer-time floor
        // passes trivially (SUBSTRING(TRNF_DT,9,6) = '' — same as legacy).
        for (OvrsPymnSumrDetl d : ovrsDetlRepo.findOverseasAutoMatchCandidates(
                bizKey, acctNo, debit.getTrscDt(), debit.getTrscAmt())) {
            OvrsPymnSumr master = ovrsSumrRepo.findByBizKeyAndOvrsPymnNo(
                    bizKey, d.getOvrsPymnNo()).orElse(null);
            if (master == null) continue;
            if (!transferTimeBeforeBankTime(master.getTrnfDt(), bankTm)) continue;
            if (!remarkContained(bankRmrk, d.getMemo())) continue;
            Map<String, DispatchResult> res = dispatchResultResolver.resolveOverseas(master, List.of(d));
            if (!isSuccess(res.get(d.getOvrsPymnDetlNo()))) continue;
            candidates.add(Candidate.overseas(
                    d.getOvrsPymnNo() + PAY_TRNS_ID_SEP + d.getOvrsPymnDetlNo(),
                    d.getOvrsPymnNo(), d.getOvrsPymnDetlNo()));
        }

        // Exactly one, else skip (0 = no match, 2+ = ambiguous).
        if (candidates.size() != 1) {
            log.info("feature=auto-match skip bizKey={} acctSeqNo={} amt={} dt={} candidateCount={}",
                    bizKey, debit.getAcctSeqNo(), debit.getTrscAmt(), debit.getTrscDt(), candidates.size());
            return false;
        }
        return writeMatch(bizKey, userId, debit, candidates.get(0));
    }

    /* ------------------------------------------------------------- write -- */

    private boolean writeMatch(String bizKey, String userId, AcctTrnsHstr debit, Candidate c) {
        String shortId = nz(debit.getBankTrnsShortId());
        String fullId  = nz(debit.getBankTrnsFullId());
        String now     = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        // Duplicate guard: a live (locked) log for this exact pair already
        // exists (e.g. a re-scrape of an already-matched row) → skip.
        if (matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                bizKey, shortId, c.payTrnsId(), YES).isPresent()) {
            log.info("feature=auto-match skip-duplicate bizKey={} acctSeqNo={} payTrnsId={}",
                    bizKey, debit.getAcctSeqNo(), c.payTrnsId());
            return false;
        }

        // 1. BUSI_MATCH_LOG — re-lock a retained (LOCKED_YN='N') row for the
        //    same pair if present, else insert a fresh one (same idiom as the
        //    manual matchers).
        BusiMatchLog logRow = matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                bizKey, shortId, c.payTrnsId(), NO).orElseGet(BusiMatchLog::new);
        boolean fresh = logRow.getMatchLogSeq() == null;
        if (fresh) {
            logRow.setBizKey(bizKey);
            logRow.setMatchLogSeq(legacyFunctions.nextBusiMatchLogSeq(bizKey));
            logRow.setBankTrnsShortId(shortId);
            logRow.setBankTrnsFullId(fullId);
            logRow.setPayTrnsId(c.payTrnsId());
            logRow.setRegId(userId);
            logRow.setRegDtm(now);
        }
        logRow.setMatchTypeCd(MATCH_TYPE_AUTO);
        logRow.setMemo(null);
        logRow.setMatchedAt(now);
        logRow.setMatchedBy(userId);
        logRow.setLastMatchedAt(now);
        logRow.setLastMatchedBy(userId);
        logRow.setLockedYn(YES);
        logRow.setModId(userId);
        logRow.setModDtm(now);
        matchLogRepo.save(logRow);

        // 2. ACCT_TRNS_HSTR — LINK_MATCH_YN='Y' + payment FK. Domestic stamps
        //    the SCDUL pair; overseas stamps the OVRS pair (nulls the SCDUL
        //    pair) — mirrors legacy PAY_TYPE_CD 'S' / 'O' branches
        //    (fnsh_0001_001_c001_act.jsp:2029-2035).
        int bankUpdated = c.type() == PayType.DOMESTIC
                ? txnRepo.markBankRowMatched(
                        bizKey, debit.getAcctSeqNo(), c.scdulPymnNo(), c.scdulPymnDetlNo(), userId, now)
                : txnRepo.markBankRowMatchedOverseas(
                        bizKey, debit.getAcctSeqNo(), c.ovrsPymnNo(), c.ovrsPymnDetlNo(), userId, now);
        if (bankUpdated == 0) {
            log.warn("feature=auto-match bank-row-vanished bizKey={} acctSeqNo={}",
                    bizKey, debit.getAcctSeqNo());
            return false;
        }

        // 3. Payment side — detail table only (legacy never writes the
        //    SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR master through auto-match).
        if (c.type() == PayType.DOMESTIC) {
            detlRepo.markDetailMatched(
                    bizKey, c.scdulPymnNo(), c.scdulPymnDetlNo(),
                    debit.getAcctSeqNo(), shortId, fullId, userId, now);
        } else {
            ovrsDetlRepo.markOverseasDetailMatched(
                    bizKey, c.ovrsPymnNo(), c.ovrsPymnDetlNo(),
                    debit.getAcctSeqNo(), shortId, fullId, userId, now);
        }

        log.info("feature=auto-match matched bizKey={} acctSeqNo={} payTrnsId={} type={}",
                bizKey, debit.getAcctSeqNo(), c.payTrnsId(), c.type());
        return true;
    }

    /* ----------------------------------------------------------- helpers -- */

    /**
     * Public tenant-level gate check — the Bank Transaction Sync popup needs
     * to know whether auto-match is enabled so it can decide whether to render
     * the per-currency "Debit Matched" row at all (legacy parity:
     * {@code _autoMatchEnv !== "N"} in {@code nxisas_tranList_osmc.js:528}).
     * Single authoritative source ({@code BUSI_ENV_SET_INFM} setting
     * {@code 00079}) — the frontend never re-checks its own env copy.
     */
    public boolean isAutoMatchEnabled(String bizKey) {
        return isEnabled(bizKey);
    }

    private boolean isEnabled(String bizKey) {
        return envRepo.findById(new BusiEnvSetInfmId(bizKey, ENV_AUTO_MATCH))
                .map(e -> YES.equals(e.getEnvSetCont() == null ? null : e.getEnvSetCont().trim()))
                .orElse(false);
    }

    private boolean isSuccess(DispatchResult dr) {
        return dr != null
                && PROC_STAT_SUCCESS.equals(dr.procStat())
                && RS_CD_SUCCESS.equals(dr.rsCd());
    }

    /**
     * Legacy {@code (:BANK_TRSC_TM = '' OR SUBSTRING(TRNF_DTM,9,6) < :BANK_TRSC_TM)}.
     * Blank bank time disables the filter (returns true). Payment time is the
     * {@code HHmmss} slice of {@code TRNF_DTM}; a payment with no time slice
     * ('') sorts before any non-empty bank time, so it passes — matching the
     * legacy string comparison exactly.
     */
    private static boolean transferTimeBeforeBankTime(String trnfDtm, String bankTrscTm) {
        if (bankTrscTm == null || bankTrscTm.isBlank()) return true;
        String payTm = (trnfDtm != null && trnfDtm.length() >= 14) ? trnfDtm.substring(8, 14) : "";
        return payTm.compareTo(bankTrscTm) < 0;
    }

    /**
     * Legacy post-filter: {@code bankRemark.contains(paymentRemark)} after
     * {@link BankTrnsIdGenerator#cleanRemark}. Either side blank ⇒ keep the
     * candidate (cannot verify — legacy parity).
     */
    private static boolean remarkContained(String bankRmrk, String payRmrk) {
        String b = BankTrnsIdGenerator.cleanRemark(bankRmrk);
        String p = BankTrnsIdGenerator.cleanRemark(payRmrk);
        if (b.isEmpty() || p.isEmpty()) return true;
        return b.contains(p);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
