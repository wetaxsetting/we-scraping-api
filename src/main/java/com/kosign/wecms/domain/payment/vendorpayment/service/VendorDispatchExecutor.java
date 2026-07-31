package com.kosign.wecms.domain.payment.vendorpayment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.constant.AntDivChannel;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.service.RealtimeDispatchRegistrar;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
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
 * <b>Vendor-Payment-specific</b> dispatch executor.
 *
 * <p>This class is a deliberate, isolated copy of the shared
 * {@code TransferDispatchExecutor} (deleted 2026-07-28, F-3)
 * with the legacy-parity corrections that apply to the Vendor/Expense shared
 * transfer engine. It is consumed <b>only</b> by {@link VendorPaymentService}.
 *
 * <p><b>Why a separate class instead of the shared executor:</b> the shared
 * {@code TransferDispatchExecutor} file path diverged from legacy in six places
 * (BANK_PRTN_ID placeholder, unconditional TRSC_CCY_CD, missing USD ANT_DIV_CD
 * override, {@code BANK_CHNL_DIV="C"} applied to all banks, missing VCB reject,
 * remarks sourced from {@code WDRL_ACT_RMRK} instead of {@code TRAN_USAG}).
 * Vendor and Expense share the SAME legacy engine
 * ({@code PymnHelper.pymn_0001_001_c001}), and the
 * {@link com.kosign.wecms.domain.payment.expensepayment.service.ExpenseDispatchExecutor}
 * already encodes that engine correctly. This class mirrors the Expense executor
 * verbatim so Vendor emits legacy-identical {@code TB_PAY_FILE_MST} /
 * {@code TB_PAY_FILE_DATA} rows, while leaving the shared executor (and every
 * other module that historically referenced it) byte-for-byte untouched —
 * matching the per-module executor pattern Internal Transfer / Expense /
 * Payroll / Overseas already follow.
 *
 * <p>Mirrors legacy {@code PymnHelper.pymn_0001_001_c001} (the shared
 * Vendor/Expense transfer engine, {@code ~2123-2406}). Branch on
 * {@code TRNF_CCY_CD}:
 *
 * <ul>
 *   <li><b>VND -&gt; API path</b>: one {@link TbPayTarget} per detail line,
 *       {@code ANT_DIV_CD} = INTER for same-bank else the line's own code
 *       (legacy {@code :2176-2180}).</li>
 *   <li><b>Non-VND -&gt; File path</b>: per-bank {@code TRSC_CCY_CD} /
 *       {@code ANT_DIV_CD} / {@code BANK_CHNL_DIV}, real {@code BANK_PRTN_ID}
 *       from {@code TB_FIRM_CONF}, the VCB USD-&gt;other-bank reject, and the
 *       {@code TRAN_USAG}-sourced remark columns — all per legacy
 *       {@code :2233-2405}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorDispatchExecutor {

    private static final DateTimeFormatter DT8   = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM6   = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Vietnam-local zone — same window the cut-off-time validator uses. */
    private static final ZoneId VN_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String PROC_STAT_PENDING         = "9";  // tb_pay_*.proc_stat DEFAULT '9'
    private static final String PROC_RSLT_DIV_UNPROCESSED  = "N";  // ProcRsltDiv.UNPROCESSED
    private static final String FUND_TP_OTHER             = "9";  // FundTp.OTHER
    private static final String TRAN_TP_DEPOSIT           = "1";  // TranTpF.DEPOSIT
    private static final String FINC_MDA_DIV_ACCOUNT      = "A";  // FincMdaDiv.ACCOUNT

    /** Legacy {@code BankChnlDiv.CURRENT_BANK_TRANSFER} = "T" (NOT "C" — the
     *  shared executor's "C" is the bug this copy fixes). */
    private static final String BANK_CHNL_DIV_CURRENT_BANK = "T";
    private static final String BANK_CHNL_DIV_OTHER_BANK    = "O";

    /** API-path same-bank tag — {@code AntDivCd.INTER}. */
    private static final String ANT_DIV_INTER = "INTER";
    /** File-path USD ANT_DIV_CD overrides — {@code AntDivCdT}: EBANK="E", VCB="V". */
    private static final String ANT_DIV_T_EBANK = "E";
    private static final String ANT_DIV_T_VCB   = "V";

    /** Active-bank flag in {@code TB_FIRM_BANK_CODE} (legacy filter
     *  {@code bank_stas = '1'} at {@code :2334}). */
    private static final String BANK_STAS_ACTIVE = "1";

    private static final String CCY_VND = "VND";
    private static final String CCY_USD = "USD";

    /** Firm-banking out-bank codes (config-overridable, same defaults as the
     *  Expense / Internal Transfer executors). */
    @Value("${wecms.internal-transfer.routing.shinhan-bank-cd:970424}")
    private String shinhanBankCd;
    @Value("${wecms.internal-transfer.routing.woori-bank-cd:970457}")
    private String wooriBankCd;
    @Value("${wecms.internal-transfer.routing.vcb-bank-cd:970436}")
    private String vcbBankCd;

    private final TbPayTargetRepository    tbPayTargetRepository;
    private final TbPayFileMstRepository   tbPayFileMstRepository;
    private final TbPayFileDataRepository  tbPayFileDataRepository;
    private final AcctMstrRepository       acctMstrRepository;
    private final DispatchKeyGenerator     keyGenerator;
    private final Clock                    clock;
    private final LegacyFunctionRepository legacyFunctions;
    private final DispatchAutoReceiveRegistrar autoReceiveRegistrar;
    private final RealtimeDispatchRegistrar    realtimeDispatchRegistrar;
    /** Real {@code BANK_PRTN_ID} source (legacy {@code TB_FIRM_CONF}). */
    private final TbFirmConfRepository     tbFirmConfRepository;
    /** {@code ENG_ABRVAT_NM} same-group resolution (legacy {@code TB_FIRM_BANK_CODE}). */
    private final TbFirmBankCodeRepository tbFirmBankCodeRepository;

    /**
     * Insert dispatch rows for one Vendor batch. Must run inside the caller's
     * transaction — the master {@code TRNF_YN='Y'} stamp and the dispatch
     * inserts must commit or roll back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines, String trnfCcyCd) {
        String bizKey = master.getBizKey();
        String userId = TenantContext.currentUserId();

        // F1 (audit VCB, legacy PymnHelper.pymn_0001_001_c001:2188, SVN r36822 2026-07-16): ALL VCB
        // (970436) — VND and USD — dispatches via the File path, not the real-time API.
        boolean isVcbFileTransfer = eq(master.getOutBankCd(), vcbBankCd);
        if (CCY_VND.equalsIgnoreCase(trnfCcyCd) && !isVcbFileTransfer) {
            // K — legacy parity (PymnHelper:2196,2214): the API-path TB_PAY_TARGET stamps TRAN_DT +
            // REG_TM from ONE captured UTC 'now' (utcNow), NOT ICT. TRAN_DT is a PK component, so the
            // same UTC value flows through nextTranSeq / save / registerAfterCommit → the realtime
            // dispatch lookup. PROC_DTTM stays ICT (legacy gmtNowVanCenter; out of scope for K).
            LocalDateTime nowUtc = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
            String tranDt = nowUtc.format(DT8);
            String regTm  = nowUtc.format(TM6);
            String dttm14 = LocalDateTime.now(clock.withZone(VN_TZ)).format(DTM14);
            writeApiPath(master, lines, bizKey, userId, tranDt, regTm, dttm14, trnfCcyCd);
        } else {
            // File path: legacy TB_PAY_FILE_MST uses server-local LocalDate.now(); refactor keeps ICT.
            LocalDateTime nowVn = LocalDateTime.now(clock.withZone(VN_TZ));
            writeFilePath(master, lines, bizKey, nowVn.format(DT8), nowVn.format(DTM14), trnfCcyCd);
        }
    }

    /* ───────────────── VND / API path (legacy :2156-2231) ───────────────── */

    private void writeApiPath(
            ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines,
            String bizKey, String userId, String tranDt, String regTm, String dttm14,
            String trnfCcyCd) {

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
            row.setAntDivCd(resolveAntDivCd(master.getOutBankCd(), line));
            row.setTarBankCd(line.getTarBankCd());
            row.setFincMdaNo(line.getFincMdaNo());
            row.setFincMdaOwnerNm(line.getRcptNm());   // B: legacy raw RCPT_NM, no fallback (PymnHelper:2218)
            row.setTranAmt(line.getTranAmt());
            row.setTranUsag(line.getTranUsag());
            row.setProcStat(PROC_STAT_PENDING);
            row.setProcDttm(dttm14);
            row.setUserId(userId);
            row.setTrscCcyCd(trnfCcyCd);
            // H — legacy parity (PymnHelper.pymn_0001_001_c001:2193-2224): the API-path
            // TB_PAY_TARGET insert leaves WITHD_CCY_CD / DEP_CCY_CD null. Verified against
            // live legacy data (dep_ccy_cd null 101/101, withd_ccy_cd null 99/101 — the 2
            // non-null are the IT Woori-USD->VND case). Do not re-add these setters here.

            tbPayTargetRepository.save(row);

            line.setPayTranDt(tranDt);
            line.setPayTranSeq(tranSeq);
            line.setPayFileSeqNo(null);
            line.setPaySeq(null);
        }

        log.info("feature=vendor-payment dispatch path=API bizKey={} scdulPymnNo={} lines={} tranDt={}",
                bizKey, master.getScdulPymnNo(), lines.size(), tranDt);

        realtimeDispatchRegistrar.registerAfterCommit(bizKey, tranDt, tranSeqs);
    }

    /* ───────────────── Non-VND / File path (legacy :2233-2405) ───────────────── */

    private void writeFilePath(
            ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines,
            String bizKey, String tranDt, String dttm14, String trnfCcyCd) {

        String outBank   = trimToNull(master.getOutBankCd());
        String fileSeqNo = keyGenerator.nextFileSeqNo(bizKey, tranDt);

        TbPayFileMst mst = new TbPayFileMst();
        mst.setTranDt(tranDt);
        mst.setBizKey(bizKey);
        mst.setFileSeqNo(fileSeqNo);
        mst.setOutBankCd(master.getOutBankCd());
        mst.setWdrlActNo(master.getWdrlActNo());
        // BANK_PRTN_ID — real value from TB_FIRM_CONF (legacy :2237-2256).
        // Legacy reads the row with no null guard and NPE-rolls-back when it
        // is absent; the refactor blocks with WBE2089 (same effect, clean code).
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

        long seqCursor = Long.parseLong(legacyFunctions.nextTbPayFileDataSeq(bizKey, fileSeqNo)) - 1;
        for (ScdulPymnSumrDetl line : lines) {
            BigDecimal seq = BigDecimal.valueOf(++seqCursor);
            String lineCcy  = trimToNull(line.getTrnfCcyCd());
            String tarBank  = trimToNull(line.getTarBankCd());

            TbPayFileData data = new TbPayFileData();
            data.setTranDt(tranDt);
            data.setBizKey(bizKey);
            data.setFileSeqNo(fileSeqNo);
            data.setSeq(seq);
            data.setTarBankCd(line.getTarBankCd());
            data.setFincMdaDiv(FINC_MDA_DIV_ACCOUNT);
            data.setFincMdaNo(line.getFincMdaNo());

            // TRSC_CCY_CD per out-bank (legacy :2275-2286):
            //   Shinhan -> withdrawal-account currency; Woori / VCB -> transfer currency.
            if (eq(outBank, shinhanBankCd)) {
                data.setTrscCcyCd(master.getWdrlCcyCd());
            } else if (eq(outBank, wooriBankCd) || eq(outBank, vcbBankCd)) {
                data.setTrscCcyCd(line.getTrnfCcyCd());
            }

            data.setTranAmt(line.getTranAmt());

            // OQ-2 (Mr. Jung 2026-07-16, CONFIRMED file-based VCB; proven vs legacy PymnHelper:2573
            // + copyFrom :2840-2843): the ANT_DIV_CD base is the uploaded network code. VCB VND keeps
            // its uploaded NAPAS/CITAD code (legacy "VCB VND -> Other : NAPAS/CITAD from upload"), so
            // it is seeded here; the USD block below only replaces it for Shinhan/Woori/VCB *USD*.
            // (Among VND rows, only VCB VND reaches the File path — non-VCB VND takes the API path.)
            //
            // F-4 (2026-07-28, Option A — legacy-faithful): the detail column is bpchar(5) and normally
            // holds the 5-char "NAPAS"/"CITAD"; this target is bpchar(1). A raw copy makes PostgreSQL
            // reject the INSERT with `value too long for type character(1)` (SQLSTATE 22001) — proven via
            // bpchar('CITAD'::bpchar, 5, false), the assignment-coercion function INSERT itself uses.
            // Legacy narrows through TbPayFileData.copyFrom -> AntDivCdT.fromValue, which accepts ONLY
            // the 1-char codes and yields NULL for a long name — so NULL is the legacy-correct value here.
            // toStorageCode (= AntDivCdT.parse, 'CITAD'->'C') is Payroll's contract and must NOT be used
            // here: it would assign a channel legacy never assigned and change money routing.
            data.setAntDivCd(AntDivChannel.toStorageCodeStrict(line.getAntDivCd()));

            // ANT_DIV_CD USD overrides (legacy :2309-2316 / :2778-2785):
            //   Shinhan USD -> EBANK ("E"); Woori USD -> VCB ("V"); VCB USD -> null.
            if (CCY_USD.equalsIgnoreCase(lineCcy)) {
                if (eq(outBank, shinhanBankCd)) {
                    data.setAntDivCd(ANT_DIV_T_EBANK);
                } else if (eq(outBank, wooriBankCd)) {
                    data.setAntDivCd(ANT_DIV_T_VCB);
                } else if (eq(outBank, vcbBankCd)) {
                    data.setAntDivCd(null);
                }
            }

            // BANK_CHNL_DIV + VCB USD->other-bank guard (legacy :2327-2360),
            // applied only for the Shinhan / Woori / VCB out-banks.
            if (eq(outBank, shinhanBankCd) || eq(outBank, wooriBankCd) || eq(outBank, vcbBankCd)) {
                boolean sameBank = eq(outBank, tarBank) || sameBankGroup(outBank, tarBank);
                // F1: the VCB->other-bank reject applies to USD only (legacy pymn_0001:2387-2388
                // `&& "USD".equals(TRNF_CCY_CD)`). VCB VND->other-bank is allowed on the File path; its
                // fields are CONFIRMED from legacy (BANK_CHNL_DIV=T/O below; ANT_DIV_CD=uploaded above).
                if (eq(outBank, vcbBankCd) && !sameBank && !CCY_VND.equalsIgnoreCase(trnfCcyCd)) {
                    // Legacy :2349-2353 — WbMessageCode.WBE0000 + literal.
                    throw new BusinessException("WBE2088");
                }
                data.setBankChnlDiv(sameBank ? BANK_CHNL_DIV_CURRENT_BANK : BANK_CHNL_DIV_OTHER_BANK);
            }

            data.setFincMdaOwnerNm(line.getRcptNm());  // B: legacy raw RCPT_NM (PymnHelper:2327)
            // Legacy :2293-2294 — BOTH remark columns carry TRAN_USAG.
            data.setWdrlActRmrk(line.getTranUsag());
            data.setTarAcctRmrk(line.getTranUsag());
            data.setRemitCcyCd(line.getTrnfCcyCd());
            data.setProcRsltDiv(PROC_RSLT_DIV_UNPROCESSED);

            tbPayFileDataRepository.save(data);

            line.setPayTranDt(tranDt);
            line.setPayTranSeq(seq.toPlainString());
            line.setPayFileSeqNo(null);
            line.setPaySeq(null);
        }

        log.info("feature=vendor-payment dispatch path=FILE bizKey={} scdulPymnNo={} lines={} tranDt={} fileSeqNo={}",
                bizKey, master.getScdulPymnNo(), lines.size(), tranDt, fileSeqNo);

        autoReceiveRegistrar.registerAfterCommit(tranDt, bizKey, fileSeqNo);
    }

    /* ───────────────── Helpers ───────────────── */

    /** Real {@code BANK_PRTN_ID} from {@code TB_FIRM_CONF} (PK
     *  {@code (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)}). Blocks with {@code WBE2089}
     *  when the row is missing — legacy parity ({@code :2242-2256} NPE-blocks). */
    private String resolveBankPrtnId(String bizKey, ScdulPymnSumr master) {
        return tbFirmConfRepository
                .findById(new TbFirmConfId(bizKey, master.getOutBankCd(), master.getWdrlActNo()))
                .map(TbFirmConf::getBankPrtnId)
                .map(VendorDispatchExecutor::trimToNull)
                .orElseThrow(() -> new BusinessException("WBE2089"));
    }

    /** Same banking group by {@code ENG_ABRVAT_NM} across firm out-bank vs.
     *  NAPAS/CITAD code spaces (legacy :2331-2347). Active rows only
     *  ({@code BANK_STAS='1'}). Returns false when either side is unresolved
     *  — identical degradation to legacy. */
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

    /** {@code FINC_MDA_OWNER_NM} is NOT NULL — resolve from the line's
     *  recipient name, then {@code ACCT_MSTR}, then the account number. The
     *  legacy engine uses {@code RCPT_NM} directly; this fallback keeps the
     *  NOT-NULL insert alive for rows where the parser left it blank. */
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
                    .map(VendorDispatchExecutor::trimToNull)
                    .orElse(null);
            if (nm != null) return nm;
        }
        return acctNo != null ? acctNo : "UNKNOWN";
    }

    /** API-path {@code ANT_DIV_CD}: same-bank -> INTER, else the line's own
     *  value (blank -> INTER). Legacy :2176-2180. */
    private static String resolveAntDivCd(String outBankCd, ScdulPymnSumrDetl line) {
        if (outBankCd != null && outBankCd.equals(line.getTarBankCd())) {
            return ANT_DIV_INTER;
        }
        String fromLine = trimToNull(line.getAntDivCd());
        return fromLine == null ? ANT_DIV_INTER : fromLine;
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
