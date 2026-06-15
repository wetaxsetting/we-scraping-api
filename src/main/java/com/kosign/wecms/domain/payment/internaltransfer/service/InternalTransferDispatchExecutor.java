package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
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
 *       ({@code BankChnlDiv.CURRENT_BANK_TRANSFER}); other-bank → {@code "O"}.</li>
 *   <li><b>Shinhan-USD→VND ANT_DIV_CD override:</b> file-path lines get
 *       {@code "C"} (CITAD, same-bank) / {@code "E"} (EBANK, other-bank).</li>
 * </ol>
 *
 * <p><b>Deferred (Need confirmation — NC-2):</b> legacy same-bank detection
 * also matches by {@code ENG_ABRVAT_NM} across the firm-out-bank vs.
 * NAPAS/CITAD code spaces ({@code TB_FIRM_BANK_CODE}). This executor uses
 * <b>raw bank-code equality only</b> (identical to the shared executor's
 * current behavior — no regression). Cross-code-space abbreviation matching
 * is intentionally NOT implemented here to avoid guessing the firm↔NAPAS
 * code mapping; it is documented as a follow-up.
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

    /** G20 fix — same-bank current-bank transfer = "T"
     *  ({@code BankChnlDiv.CURRENT_BANK_TRANSFER}), NOT "C". */
    private static final String BANK_CHNL_DIV_CURRENT_BANK = "T";
    private static final String BANK_CHNL_DIV_OTHER_BANK   = "O";

    /** API-path same-bank tag ({@code AntDivCd.INTER}). */
    private static final String ANT_DIV_INTER = "INTER";
    /** File-path ANT_DIV_CD for the Shinhan-USD→VND special case
     *  ({@code AntDivCdT}: CITAD="C", EBANK="E"). */
    private static final String ANT_DIV_T_CITAD = "C";
    private static final String ANT_DIV_T_EBANK = "E";

    private static final String BANK_PRTN_ID_PLACEHOLDER = "DEFAULT";

    private static final String CCY_VND = "VND";
    private static final String CCY_USD = "USD";

    /** Firm-banking out-bank codes (config-overridable so QA can flex). */
    @Value("${wecms.internal-transfer.routing.woori-bank-cd:970457}")
    private String wooriBankCd;
    @Value("${wecms.internal-transfer.routing.shinhan-bank-cd:970424}")
    private String shinhanBankCd;

    private final TbPayTargetRepository   tbPayTargetRepository;
    private final TbPayFileMstRepository  tbPayFileMstRepository;
    private final TbPayFileDataRepository tbPayFileDataRepository;
    private final AcctMstrRepository      acctMstrRepository;
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
        LocalDateTime nowVn = LocalDateTime.now(clock.withZone(VN_TZ));
        String tranDt = nowVn.format(DT8);
        String regTm  = nowVn.format(TM6);
        String dttm14 = nowVn.format(DTM14);

        // --- Routing flags (legacy PymnHelper.pymn_0009_001_c001 ~2459-2494) ---
        String outBankCd = trimToNull(master.getOutBankCd());
        String wdrlCcyCd = resolveWdrlCcyCd(bizKey, master);
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

        // --- G18 path selection ---
        boolean useApiPath =
                (CCY_VND.equalsIgnoreCase(trimToNull(trnfCcyCd)) && !isShinhanUsdToVnd)
                || isWooriUsdToVnd;

        if (useApiPath) {
            writeApiPath(master, lines, bizKey, userId, tranDt, regTm, dttm14, trnfCcyCd,
                    wdrlCcyCd, isWooriUsdToVnd);
        } else {
            writeFilePath(master, lines, bizKey, tranDt, dttm14, trnfCcyCd, wdrlCcyCd,
                    isShinhanUsdToVnd);
        }
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
            row.setFincMdaOwnerNm(resolveOwnerNm(bizKey, line));
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
        mst.setBankPrtnId(BANK_PRTN_ID_PLACEHOLDER);
        mst.setFundTp(FUND_TP_OTHER);
        mst.setTranTp(TRAN_TP_DEPOSIT);
        mst.setProcStat(PROC_STAT_PENDING);
        mst.setReqCnt(master.getTotTrnsCnt());
        mst.setReqAmt(master.getTotalTranAmt());
        mst.setRegDttm(dttm14);

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

            // G20: same-bank current-bank transfer = "T", other-bank = "O".
            boolean sameBank = sameBank(master.getOutBankCd(), line.getTarBankCd());
            data.setBankChnlDiv(sameBank ? BANK_CHNL_DIV_CURRENT_BANK : BANK_CHNL_DIV_OTHER_BANK);

            // Shinhan-USD→VND ANT_DIV_CD override (CITAD same / EBANK other).
            if (isShinhanUsdToVnd) {
                data.setAntDivCd(sameBank ? ANT_DIV_T_CITAD : ANT_DIV_T_EBANK);
            }

            data.setFincMdaOwnerNm(resolveOwnerNm(bizKey, line));
            // Legacy quirk parity (PymnHelper:2295): WDRL_ACT_RMRK copied into
            // both TAR_ACCT_RMRK and WDRL_ACCT_RMRK.
            data.setTarAcctRmrk(line.getWdrlActRmrk());
            data.setWdrlActRmrk(line.getWdrlActRmrk());
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

    /** Raw bank-code equality. NC-2 (ENG_ABRVAT_NM cross-code-space match)
     *  is intentionally NOT implemented — see class Javadoc. */
    private static boolean sameBank(String outBankCd, String tarBankCd) {
        String a = trimToNull(outBankCd);
        String b = trimToNull(tarBankCd);
        return a != null && a.equals(b);
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
