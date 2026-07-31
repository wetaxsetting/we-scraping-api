package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import com.kosign.wecms.domain.payment.dispatch.constant.AntDivChannel;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.service.RealtimeDispatchRegistrar;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>Internal-Transfer-specific</b> dispatch executor.
 *
 * <p>This class is a deliberate, isolated copy of the shared
 * {@link com.kosign.wecms.domain.payment.dispatch.service.TransferDispatchExecutor}
 * with the legacy-parity corrections that apply to Internal Transfer
 * (gaps <b>G18 / G19 / G20</b> — see
 * {@code docs/features/internal-transfer/internal-transfer-dispatch-fix-plan.md}).
 * It is consumed <b>only</b> by {@link InternalTransferDispatchService}.
 *
 * <p><b>Why a separate class instead of editing the shared executor:</b>
 * the shared executor is also called by Vendor / Expense / Payroll. The
 * cross-module impact analysis
 * ({@code docs/features/payment/dispatch-routing-impact-analysis.md})
 * found that whether those siblings expect the same Woori/Shinhan routing,
 * per-bank {@code TRSC_CCY_CD}, and {@code BANK_CHNL_DIV} values is
 * <i>unverified</i>. Keeping this logic in an Internal-Transfer-only class
 * leaves the shared executor byte-for-byte unchanged, so Vendor / Expense /
 * Payroll behavior is provably untouched. When sibling parity is later
 * confirmed, this class should be re-merged back into the shared executor
 * (the shared dispatch MR) to remove the duplication.
 *
 * <p>Mirrors legacy {@code PymnHelper.pymn_0009_001_c001} (Internal
 * Transfer execute, {@code ~2419-2790}). Differences from the shared
 * executor:
 * <ol>
 *   <li><b>G18 routing:</b> API path when
 *       {@code (TRNF_CCY_CD=="VND" && !isShinhanUsdToVnd) || isWooriUsdToVnd},
 *       else file path — restoring the Woori-USD→VND (API) and
 *       Shinhan-USD→VND (file) special cases.</li>
 *   <li><b>G19 TRSC_CCY_CD:</b> API path → {@code "USD"} when
 *       {@code isWooriUsdToVnd} else {@code "VND"}; file path → Shinhan
 *       ({@code WDRL_CCY_CD}) / otherwise the line's own {@code TRNF_CCY_CD}.</li>
 *   <li><b>G20 BANK_CHNL_DIV:</b> same-bank → {@code "T"}
 *       ({@code BankChnlDiv.CURRENT_BANK_TRANSFER}); other-bank → {@code "O"}.
 *       Same-bank = raw bank-code equality OR same banking group via
 *       {@code ENG_ABRVAT_NM} (NC-2, below).</li>
 *   <li><b>Shinhan-USD→VND ANT_DIV_CD override:</b> file-path lines get
 *       {@code "C"} (CITAD, same-bank) / {@code "E"} (EBANK, other-bank).</li>
 * </ol>
 *
 * <p><b>NC-2 (implemented):</b> the file-path same-bank detection matches by
 * raw bank-code equality <b>and</b> by {@code ENG_ABRVAT_NM} group across the
 * firm-out-bank vs. NAPAS/CITAD code spaces ({@code TB_FIRM_BANK_CODE}, active
 * rows only) — see {@link #sameBankGroup}. This mirrors legacy
 * {@code pymn_0009_001_c001:2767-2806} and the same helper in
 * {@code ExpenseDispatchExecutor} / {@code VendorDispatchExecutor}. (The
 * VND/API-path {@code ANT_DIV_CD} same-bank check at {@link #resolveApiAntDivCd}
 * remains raw-code only — out of scope for the file-path NC-2 fix.)
 *
 * <p>Read side is unaffected: {@code BANK_CHNL_DIV} / {@code TRSC_CCY_CD}
 * have no refactor-side reader (the result resolver keys off
 * {@code PROC_STAT}/{@code RS_CD}), so these corrections have zero R&D
 * runtime impact today — they are legacy-parity / bank-side-file
 * correctness fixes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferDispatchExecutor {

    private static final DateTimeFormatter DT8   = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM6   = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Vietnam-local zone — same window the cut-off-time validator uses. */
    private static final ZoneId VN_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String PROC_STAT_PENDING        = "9";
    private static final String PROC_RSLT_DIV_UNPROCESSED = "N";
    private static final String FUND_TP_OTHER            = "9";  // FundTp.OTHER
    private static final String TRAN_TP_DEPOSIT          = "1";  // TranTpF.DEPOSIT
    private static final String FINC_MDA_DIV_ACCOUNT     = "A";  // FincMdaDiv.ACCOUNT

    /** Active-bank flag in {@code TB_FIRM_BANK_CODE} (legacy filter
     *  {@code bank_stas = '1'}). */
    private static final String BANK_STAS_ACTIVE = "1";

    /** G20 fix — same-bank current-bank transfer = "T"
     *  ({@code BankChnlDiv.CURRENT_BANK_TRANSFER}), NOT "C". */
    private static final String BANK_CHNL_DIV_CURRENT_BANK = "T";
    private static final String BANK_CHNL_DIV_OTHER_BANK   = "O";

    /** API-path same-bank tag ({@code AntDivCd.INTER}). */
    private static final String ANT_DIV_INTER = "INTER";
    // File-path 1-char channel codes now come from the shared AntDivChannel.CODE_* vocabulary
    // (F-2/F-5, 2026-07-28) — the former local ANT_DIV_T_CITAD / ANT_DIV_T_EBANK constants were
    // duplicate mappings and were removed rather than extended.

    private static final String CCY_VND = "VND";
    private static final String CCY_USD = "USD";

    /** Firm-banking out-bank codes (config-overridable so QA can flex). */
    @Value("${wecms.internal-transfer.routing.woori-bank-cd:970457}")
    private String wooriBankCd;
    @Value("${wecms.internal-transfer.routing.shinhan-bank-cd:970424}")
    private String shinhanBankCd;
    /** F1 (audit VCB): all VCB (970436) dispatches via the File path (legacy pymn_0009:2600). */
    @Value("${wecms.internal-transfer.routing.vcb-bank-cd:970436}")
    private String vcbBankCd;

    private final TbPayTargetRepository   tbPayTargetRepository;
    private final TbPayFileMstRepository  tbPayFileMstRepository;
    private final TbPayFileDataRepository tbPayFileDataRepository;
    private final AcctMstrRepository      acctMstrRepository;
    /** {@code ENG_ABRVAT_NM} same-group resolution (legacy {@code TB_FIRM_BANK_CODE}). */
    private final TbFirmBankCodeRepository tbFirmBankCodeRepository;
    /** Real {@code BANK_PRTN_ID} source (legacy {@code TB_FIRM_CONF}). */
    private final TbFirmConfRepository    tbFirmConfRepository;
    private final DispatchKeyGenerator    keyGenerator;
    private final Clock                   clock;
    private final LegacyFunctionRepository legacyFunctions;
    private final DispatchAutoReceiveRegistrar autoReceiveRegistrar;
    /** Post-commit real-time 202 hand-off (port of NAPAS_REAL_TIME_BATCH), gated by
     *  {@code wecms.dispatch.realtime-dispatch}. Used by the VND/API path. */
    private final RealtimeDispatchRegistrar realtimeDispatchRegistrar;

    /**
     * Insert dispatch rows for one Internal-Transfer batch, applying the
     * legacy routing/currency/channel rules. Must run inside the caller's
     * transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines, String trnfCcyCd) {
        String bizKey = master.getBizKey();
        String userId = TenantContext.currentUserId();

        // --- Routing flags (legacy PymnHelper.pymn_0009_001_c001 ~2459-2494) ---
        RoutingFlags rf = computeRoutingFlags(master, lines, trnfCcyCd);
        String wdrlCcyCd = rf.wdrlCcyCd();

        // --- G18 path selection ---
        if (rf.useApiPath()) {
            // K — legacy parity (PymnHelper:2196,2214): API-path TB_PAY_TARGET TRAN_DT + REG_TM from
            // ONE captured UTC 'now'. The same UTC TRAN_DT (a PK component) flows to nextTranSeq /
            // save / registerAfterCommit → the realtime dispatch lookup. PROC_DTTM stays ICT.
            LocalDateTime nowUtc = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
            String tranDt = nowUtc.format(DT8);
            String regTm  = nowUtc.format(TM6);
            String dttm14 = LocalDateTime.now(clock.withZone(VN_TZ)).format(DTM14);
            writeApiPath(master, lines, bizKey, userId, tranDt, regTm, dttm14, trnfCcyCd,
                    wdrlCcyCd, rf.isWooriUsdToVnd());
        } else {
            // File path: legacy TB_PAY_FILE_MST uses server-local LocalDate.now(); refactor keeps ICT.
            LocalDateTime nowVn = LocalDateTime.now(clock.withZone(VN_TZ));
            writeFilePath(master, lines, bizKey, nowVn.format(DT8), nowVn.format(DTM14), trnfCcyCd,
                    wdrlCcyCd, rf.isShinhanUsdToVnd());
        }
    }

    /**
     * Whether this batch dispatches via the real-time API path
     * ({@code TB_PAY_TARGET}) rather than the File-SFTP path. Same rule
     * {@link #execute} applies for routing — exposed so the cut-off
     * validator can pick the Shinhan CITAD-via-API window (16:30) vs.
     * the File-SFTP CITAD window (15:30) from a single source of truth.
     * Read-only — no DB writes.
     */
    public boolean usesApiPath(ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines, String trnfCcyCd) {
        return computeRoutingFlags(master, lines, trnfCcyCd).useApiPath();
    }

    /** Routing decision + the currency flags {@link #execute} threads
     *  into the API / File writers. Single computation reused by both
     *  {@link #execute} and {@link #usesApiPath}. */
    private record RoutingFlags(
            boolean useApiPath, boolean isShinhanUsdToVnd, boolean isWooriUsdToVnd, String wdrlCcyCd) { }

    private RoutingFlags computeRoutingFlags(
            ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines, String trnfCcyCd) {
        String outBankCd = trimToNull(master.getOutBankCd());
        String wdrlCcyCd = resolveWdrlCcyCd(master.getBizKey(), master);
        String depoCcyCd = lines.isEmpty() ? null : trimToNull(lines.get(0).getDepoCcyCd());

        boolean isWooriUsdToVnd =
                eq(outBankCd, wooriBankCd)
                && CCY_USD.equalsIgnoreCase(trimToNull(trnfCcyCd))
                && CCY_USD.equalsIgnoreCase(wdrlCcyCd)
                && CCY_VND.equalsIgnoreCase(depoCcyCd);
        boolean isShinhanUsdToVnd =
                eq(outBankCd, shinhanBankCd)
                && CCY_USD.equalsIgnoreCase(wdrlCcyCd)
                && CCY_VND.equalsIgnoreCase(depoCcyCd);
        // F1 (audit VCB, legacy PymnHelper.pymn_0009_001_c001:2600, SVN r36822): ALL VCB (970436),
        // VND and USD, dispatches via the File path — the isVcbFileTransfer gate pulls VCB VND off
        // the API path. NOTE: the file path's VCB-specific field values (BANK_CHNL_DIV via
        // ENG_ABRVAT_NM, VND ANT_DIV_CD) are NOT built out here — VCB VND file-field parity is
        // REQUIRES EXTERNAL CONFIRMATION (Van Center), per the F1 "routing switch only" decision.
        boolean isVcbFileTransfer = eq(outBankCd, vcbBankCd);
        boolean useApiPath =
                ((CCY_VND.equalsIgnoreCase(trimToNull(trnfCcyCd)) && !isShinhanUsdToVnd)
                        || isWooriUsdToVnd)
                        && !isVcbFileTransfer;

        return new RoutingFlags(useApiPath, isShinhanUsdToVnd, isWooriUsdToVnd, wdrlCcyCd);
    }

    /* ───────────────── API path (TB_PAY_TARGET) ───────────────── */

    private void writeApiPath(
            ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines,
            String bizKey, String userId, String tranDt, String regTm, String dttm14,
            String trnfCcyCd, String wdrlCcyCd, boolean isWooriUsdToVnd) {

        // G19 (API side): Woori-USD→VND carries USD, everything else VND.
        String trscCcyCd = isWooriUsdToVnd ? CCY_USD : CCY_VND;

        List<String> tranSeqs = new ArrayList<>(lines.size());
        for (ScdulPymnSumrDetl line : lines) {
            String tranSeq = keyGenerator.nextTranSeq(bizKey, tranDt);
            tranSeqs.add(tranSeq);

            TbPayTarget row = new TbPayTarget();
            row.setTranDt(tranDt);
            row.setBizKey(bizKey);
            row.setTranSeq(tranSeq);
            row.setOutBankCd(master.getOutBankCd());
            row.setWdrlActNo(master.getWdrlActNo());
            row.setRegTm(regTm);
            row.setAntDivCd(resolveApiAntDivCd(master.getOutBankCd(), line));
            row.setTarBankCd(line.getTarBankCd());
            row.setFincMdaNo(line.getFincMdaNo());
            row.setFincMdaOwnerNm(line.getRcptNm());   // B: legacy raw RCPT_NM, no fallback (PymnHelper:2218 pattern)
            row.setTranAmt(line.getTranAmt());
            row.setTranUsag(line.getTranUsag());
            row.setProcStat(PROC_STAT_PENDING);
            row.setProcDttm(dttm14);
            row.setUserId(userId);
            row.setTrscCcyCd(trscCcyCd);
            row.setWithdCcyCd(wdrlCcyCd);
            row.setDepCcyCd(line.getDepoCcyCd());

            tbPayTargetRepository.save(row);

            line.setPayTranDt(tranDt);
            line.setPayTranSeq(tranSeq);
            line.setPayFileSeqNo(null);
            line.setPaySeq(null);
        }

        log.info("feature=internal-transfer dispatch path=API bizKey={} scdulPymnNo={} lines={} trscCcyCd={} wooriUsdVnd={}",
                bizKey, master.getScdulPymnNo(), lines.size(), trscCcyCd, isWooriUsdToVnd);

        // Hand off to the real-time 202 flow AFTER this transaction commits
        // (port of NAPAS_REAL_TIME_BATCH; gated, default-off; per-target isolated).
        realtimeDispatchRegistrar.registerAfterCommit(bizKey, tranDt, tranSeqs);
    }

    /* ───────────────── File path (TB_PAY_FILE_MST + DATA) ───────────────── */

    private void writeFilePath(
            ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines,
            String bizKey, String tranDt, String dttm14,
            String trnfCcyCd, String wdrlCcyCd, boolean isShinhanUsdToVnd) {

        String fileSeqNo = keyGenerator.nextFileSeqNo(bizKey, tranDt);

        TbPayFileMst mst = new TbPayFileMst();
        mst.setTranDt(tranDt);
        mst.setBizKey(bizKey);
        mst.setFileSeqNo(fileSeqNo);
        mst.setOutBankCd(master.getOutBankCd());
        mst.setWdrlActNo(master.getWdrlActNo());
        // BANK_PRTN_ID — real value from TB_FIRM_CONF (legacy pymn_0009_001_c001:2667).
        mst.setBankPrtnId(resolveBankPrtnId(bizKey, master));
        mst.setFundTp(FUND_TP_OTHER);
        mst.setTranTp(TRAN_TP_DEPOSIT);
        mst.setProcStat(PROC_STAT_PENDING);
        mst.setReqCnt(master.getTotTrnsCnt());
        mst.setReqAmt(master.getTotalTranAmt());
        mst.setRegDttm(dttm14);
        // PROC_DTTM is NOT NULL in TB_PAY_FILE_MST_HIS (trigger-written mirror copies
        // new.proc_dttm). Legacy stamps proc_dttm = reg_dttm on insert; the callback
        // UPDATE later writes the real processing time.
        mst.setProcDttm(dttm14);

        tbPayFileMstRepository.save(mst);

        master.setPayTranDt(tranDt);
        master.setPayFileSeqNo(fileSeqNo);

        boolean shinhan = eq(trimToNull(master.getOutBankCd()), shinhanBankCd);

        // SEQ seeded ONCE via the wb_get_next_val funnel (V140 #20, per
        // (biz_key, file_seq_no) MAX+1) then incremented per line — no per-row
        // DB call. fileSeqNo is freshly allocated here, so the seed is 1 → 1, 2, 3, ...
        long seqCursor = Long.parseLong(legacyFunctions.nextTbPayFileDataSeq(bizKey, fileSeqNo)) - 1;
        for (ScdulPymnSumrDetl line : lines) {
            BigDecimal seq = BigDecimal.valueOf(++seqCursor);
            TbPayFileData data = new TbPayFileData();
            data.setTranDt(tranDt);
            data.setBizKey(bizKey);
            data.setFileSeqNo(fileSeqNo);
            data.setSeq(seq);
            data.setTarBankCd(line.getTarBankCd());
            data.setFincMdaDiv(FINC_MDA_DIV_ACCOUNT);
            data.setFincMdaNo(line.getFincMdaNo());

            // G19 (file side): Shinhan → withdrawal currency; otherwise the
            // line's own transfer currency (covers Woori + generic banks).
            data.setTrscCcyCd(shinhan ? wdrlCcyCd : line.getTrnfCcyCd());

            data.setTranAmt(line.getTranAmt());

            // F-2 (2026-07-28) — legacy ANT_DIV_CD FIRST PASS, ported from
            // PymnHelper.pymn_0009_001_c001:2779-2786. This block was missing entirely; Vendor and
            // Expense have always had it (VendorDispatchExecutor:283-289 / ExpenseDispatchExecutor:276-284),
            // so Internal Transfer was the only file path writing NULL where legacy writes a channel.
            //
            // Legacy conditions on the PER-DETAIL TRNF_CCY_CD (not the batch parameter) — verbatim:
            //   Shinhan 970424 + detail USD -> EBANK 'E'
            //   Woori   970457 + detail USD -> VCB   'V'
            //   VCB     970436 + detail USD -> null  (legacy sets it explicitly at :2783-2785; this
            //                                        path never assigns ANT_DIV_CD, so it is ALREADY
            //                                        null — no branch is added, per the parity rule
            //                                        that only differing non-null legacy values are
            //                                        implemented)
            // isShinhanUsdToVnd is NOT caught here: its detail TRNF_CCY_CD is 'VND' (legacy note at
            // :2775-2777). It is handled by the second pass below.
            if (CCY_USD.equalsIgnoreCase(trimToNull(line.getTrnfCcyCd()))) {
                if (eq(master.getOutBankCd(), shinhanBankCd)) {
                    data.setAntDivCd(AntDivChannel.CODE_EBANK);          // legacy :2779-2780
                } else if (eq(master.getOutBankCd(), wooriBankCd)) {
                    data.setAntDivCd(AntDivChannel.CODE_VCB);            // legacy :2781-2782
                }
            }

            // G20 + NC-2: same-bank current-bank transfer = "T", other-bank = "O".
            // "Same bank": raw bank-code equality OR same banking group via
            // ENG_ABRVAT_NM (legacy pymn_0009_001_c001:2767-2806).
            boolean sameBank = sameBank(master.getOutBankCd(), line.getTarBankCd())
                    || sameBankGroup(master.getOutBankCd(), line.getTarBankCd());
            data.setBankChnlDiv(sameBank ? BANK_CHNL_DIV_CURRENT_BANK : BANK_CHNL_DIV_OTHER_BANK);

            // Shinhan-USD→VND ANT_DIV_CD second pass (legacy :2831-2855).
            //
            // F-5 (2026-07-28) — same-bank is NULL, not CITAD. Legacy code at :2846-2848 is an
            // explicit `setAntDivCd(null)`, justified in its own comment: per
            // sFTP_Firm_Banking_Form_Layout_File v2.1 (Sheet1 "File ebank USD -> VND same bank")
            // scfm_intf_t is empty, and Shinhan (Mr. Kang 2026-05-12) confirmed a DB null renders as a
            // 1-byte space in the BLS file. The refactor previously wrote 'C' because the legacy
            // *comment block* at :2801-2804 claims "Same bank -> CITAD" — that comment contradicts the
            // code directly beneath it. The CODE is authoritative; the comment is stale.
            // Other-bank -> EBANK 'E' matches legacy (:2852-2854) and is unchanged.
            if (isShinhanUsdToVnd) {
                data.setAntDivCd(sameBank ? null : AntDivChannel.CODE_EBANK);
            } else if (eq(master.getOutBankCd(), vcbBankCd) && CCY_VND.equalsIgnoreCase(trnfCcyCd)) {
                // OQ-2 (Mr. Jung 2026-07-16, CONFIRMED file-based VCB; proven vs legacy PymnHelper:2573
                // + copyFrom :2840-2843): VCB VND carries the uploaded NAPAS/CITAD network code
                // ("VCB VND -> Other : NAPAS/CITAD from upload"). VCB USD stays null (same-bank only);
                // non-VCB internal transfers are unchanged.
                //
                // F-4 (2026-07-28, Option A — legacy-faithful): bpchar(5) source -> bpchar(1) target.
                // A raw copy of "NAPAS"/"CITAD" makes PostgreSQL reject the INSERT with `value too long
                // for type character(1)` (SQLSTATE 22001) — proven via bpchar('CITAD'::bpchar, 5, false),
                // the assignment-coercion function INSERT uses. Legacy narrows via copyFrom ->
                // AntDivCdT.fromValue (1-char only), so a long name becomes NULL. Do NOT swap in
                // toStorageCode (= AntDivCdT.parse): that is Payroll's contract and would change routing.
                data.setAntDivCd(AntDivChannel.toStorageCodeStrict(line.getAntDivCd()));
            }

            data.setFincMdaOwnerNm(line.getRcptNm());  // B: legacy raw RCPT_NM (PymnHelper file path)
            // I — legacy parity (PymnHelper.pymn_0009_001_c001:2757-2758): Internal Transfer
            // sources BOTH remark columns from TRAN_USAG (verified by direct source read).
            data.setTarAcctRmrk(line.getTranUsag());
            data.setWdrlActRmrk(line.getTranUsag());
            data.setRemitCcyCd(line.getTrnfCcyCd());
            data.setProcRsltDiv(PROC_RSLT_DIV_UNPROCESSED);

            tbPayFileDataRepository.save(data);

            line.setPayTranDt(tranDt);
            line.setPayTranSeq(seq.toPlainString());
            line.setPayFileSeqNo(null);
            line.setPaySeq(null);
        }

        log.info("feature=internal-transfer dispatch path=FILE bizKey={} scdulPymnNo={} lines={} fileSeqNo={} shinhanUsdVnd={}",
                bizKey, master.getScdulPymnNo(), lines.size(), fileSeqNo, isShinhanUsdToVnd);

        // Hand off to the 00301 reception flow AFTER this transaction commits (gated).
        autoReceiveRegistrar.registerAfterCommit(tranDt, bizKey, fileSeqNo);
    }

    /* ───────────────── Helpers ───────────────── */

    /**
     * Resolve the withdrawal currency: master {@code WDRL_CCY_CD}, falling
     * back to {@code ACCT_MSTR.CCY_CD} by {@code (BIZ_KEY, OUT_BANK_CD,
     * WDRL_ACT_NO)} for legacy rows that left it null (legacy issue #42761).
     */
    private String resolveWdrlCcyCd(String bizKey, ScdulPymnSumr master) {
        String fromMaster = trimToNull(master.getWdrlCcyCd());
        if (fromMaster != null) {
            return fromMaster;
        }
        return acctMstrRepository
                .findByBizKeyAndBankCdAndAcctNo(bizKey, master.getOutBankCd(), master.getWdrlActNo())
                .map(AcctMstr::getCcyCd)
                .map(InternalTransferDispatchExecutor::trimToNull)
                .orElse(null);
    }

    /** Real {@code BANK_PRTN_ID} from {@code TB_FIRM_CONF} (PK
     *  {@code (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)}). Blocks with {@code WBE2089}
     *  when the row is missing — legacy parity (pymn_0009_001_c001:2667 reads it
     *  with no null guard and NPE-rolls-back; refactor blocks cleanly, mirroring
     *  ExpenseDispatchExecutor / VendorDispatchExecutor). */
    private String resolveBankPrtnId(String bizKey, ScdulPymnSumr master) {
        return tbFirmConfRepository
                .findById(new TbFirmConfId(bizKey, master.getOutBankCd(), master.getWdrlActNo()))
                .map(TbFirmConf::getBankPrtnId)
                .map(InternalTransferDispatchExecutor::trimToNull)
                .orElseThrow(() -> new BusinessException("WBE2089"));
    }

    /** {@code FINC_MDA_OWNER_NM} is NOT NULL — resolve from the line's
     *  recipient name, then {@code ACCT_MSTR}, then the account number. */
    private String resolveOwnerNm(String bizKey, ScdulPymnSumrDetl line) {
        String fromLine = trimToNull(line.getRcptNm());
        if (fromLine != null) {
            return fromLine;
        }
        String tarBankCd = trimToNull(line.getTarBankCd());
        String acctNo    = trimToNull(line.getFincMdaNo());
        if (acctNo != null && tarBankCd != null) {
            String nm = acctMstrRepository
                    .findByBizKeyAndBankCdAndAcctNo(bizKey, tarBankCd, acctNo)
                    .map(AcctMstr::getAcctNm)
                    .map(InternalTransferDispatchExecutor::trimToNull)
                    .orElse(null);
            if (nm != null) return nm;
        }
        return acctNo != null ? acctNo : "UNKNOWN";
    }

    /** API-path ANT_DIV_CD: same-bank → INTER, else the line's own value
     *  (blank → INTER). Raw-code same-bank detection (NC-2 deferred). */
    private static String resolveApiAntDivCd(String outBankCd, ScdulPymnSumrDetl line) {
        if (sameBank(outBankCd, line.getTarBankCd())) {
            return ANT_DIV_INTER;
        }
        String fromLine = trimToNull(line.getAntDivCd());
        return fromLine == null ? ANT_DIV_INTER : fromLine;
    }

    /** Raw bank-code equality (first same-bank check). */
    private static boolean sameBank(String outBankCd, String tarBankCd) {
        String a = trimToNull(outBankCd);
        String b = trimToNull(tarBankCd);
        return a != null && a.equals(b);
    }

    /** NC-2 fix — same banking group by {@code ENG_ABRVAT_NM} across the firm
     *  out-bank vs. NAPAS/CITAD code spaces (legacy pymn_0009_001_c001:2767-2806;
     *  mirrors {@code ExpenseDispatchExecutor} / {@code VendorDispatchExecutor}).
     *  Active rows only ({@code BANK_STAS='1'}). Returns false when either side
     *  is unresolved — identical degradation to legacy. */
    private boolean sameBankGroup(String outBankCd, String tarBankCd) {
        String src = engAbrvatNm(outBankCd);
        String tar = engAbrvatNm(tarBankCd);
        return src != null && src.equals(tar);
    }

    private String engAbrvatNm(String bankCd) {
        String code = trimToNull(bankCd);
        if (code == null) return null;
        return tbFirmBankCodeRepository
                .findFirstByBankCdAndBankStas(code, BANK_STAS_ACTIVE)
                .map(b -> trimToNull(b.getEngAbrvatNm()))
                .orElse(null);
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
