package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Payment &gt; Payroll Payment — <b>Payroll-only</b> dispatch executor (PR2).
 *
 * <p>Deliberate, isolated copy of the shared
 * {@link com.kosign.wecms.domain.payment.dispatch.service.TransferDispatchExecutor},
 * mirroring the {@code InternalTransferDispatchExecutor} /
 * {@code OverseasDispatchExecutor} pattern. Consumed ONLY by
 * {@link PayrollPaymentService#dispatch}.
 *
 * <p><b>Why a separate class:</b> the shared executor branches on
 * {@code TRNF_CCY_CD} — VND → API ({@code TB_PAY_TARGET}), else file. Legacy
 * Payroll ({@code pymn_0005_001_c001_act.jsp}) is <b>file-route only</b>: it
 * NEVER writes {@code TB_PAY_TARGET} and writes {@code TB_PAY_FILE_MST} +
 * {@code TB_PAY_FILE_DATA} regardless of currency (unlike Vendor / Internal
 * Transfer which route VND to the API). Keeping this in a Payroll-only class
 * leaves the shared executor byte-unchanged, so Vendor / Expense behaviour is
 * provably untouched.
 *
 * <p>Legacy-parity deltas vs the shared file path:
 * <ul>
 *   <li><b>No currency branch</b> — always file path, never {@code TB_PAY_TARGET}.</li>
 *   <li><b>{@code FUND_TP}</b> from {@code EXPT_RESOL_MSTR.APPR_FUND_TP}
 *       ({@code FundTp.fromValue} legacy mapping: {@code "0"}=Salary,
 *       {@code "1"}=Bonus, {@code "9"}/null=Other) — NOT hard-coded {@code "9"}.</li>
 *   <li><b>{@code BANK_CHNL_DIV}</b> same-bank = {@code "T"}
 *       ({@code BankChnlDiv.CURRENT_BANK_TRANSFER}), other-bank = {@code "O"} —
 *       NOT the shared executor's {@code "C"}.</li>
 *   <li><b>{@code TRSC_CCY_CD}</b> = the request {@code trnfCcyCd} (file-path
 *       parity); no Woori/Shinhan USD→VND API routing.</li>
 * </ul>
 *
 * <p>Validation + cut-off + the master {@code TRNF_YN='Y'} stamp stay in
 * {@link PayrollPaymentService#dispatch}; this executor only writes the
 * dispatch rows, inside the caller's transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollDispatchExecutor {

    private static final DateTimeFormatter DT8   = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final ZoneId VN_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Pending status on every freshly-written dispatch row (DEV default '9'). */
    private static final String PROC_STAT_PENDING = "9";
    /** {@code TB_PAY_FILE_DATA.PROC_RSLT_DIV} initial state — 'N' (unprocessed). */
    private static final String PROC_RSLT_DIV_UNPROCESSED = "N";

    /** {@code TB_PAY_FILE_MST.TRAN_TP} = '1' (deposit). Legacy {@code TranTpF.DEPOSIT}. */
    private static final String TRAN_TP_DEPOSIT = "1";
    /** {@code TB_PAY_FILE_DATA.FINC_MDA_DIV} = 'A' (account). Legacy {@code FincMdaDiv.ACCOUNT}. */
    private static final String FINC_MDA_DIV_ACCOUNT = "A";

    /** {@code FUND_TP} codes — legacy {@code FundTp}: SALARY/BONUS/OTHER.
     *  {@code FundTp.fromValue(APPR_FUND_TP)} returns the same code value, so
     *  FUND_TP = APPR_FUND_TP for 0/1/9; null/blank/unknown default to OTHER. */
    private static final String FUND_TP_SALARY = "0";
    private static final String FUND_TP_BONUS  = "1";
    private static final String FUND_TP_OTHER  = "9";

    /** Same-bank routing flag — legacy {@code BankChnlDiv.CURRENT_BANK_TRANSFER} = "T". */
    private static final String BANK_CHNL_DIV_CURRENT_BANK = "T";
    /** Cross-bank routing flag — legacy {@code BankChnlDiv.OTHER_BANK_TRANSFER} = "O". */
    private static final String BANK_CHNL_DIV_OTHER_BANK = "O";

    /** Placeholder bank-partner id (TB_FIRM_CONF.BANK_PRTN_ID not yet exposed). */
    private static final String BANK_PRTN_ID_PLACEHOLDER = "DEFAULT";

    private final TbPayFileMstRepository  tbPayFileMstRepository;
    private final TbPayFileDataRepository tbPayFileDataRepository;
    private final AcctMstrRepository      acctMstrRepository;
    private final DispatchKeyGenerator    keyGenerator;
    private final Clock                   clock;
    private final LegacyFunctionRepository legacyFunctions;

    /**
     * Insert the file-route dispatch rows for one Payroll batch — always
     * {@code TB_PAY_FILE_MST} + {@code TB_PAY_FILE_DATA}, never
     * {@code TB_PAY_TARGET}. Runs inside the caller's transaction.
     *
     * @param apprFundTp the batch's {@code EXPT_RESOL_MSTR.APPR_FUND_TP}
     *                   (resolved by {@code PayrollPaymentService.dispatch});
     *                   drives {@code TB_PAY_FILE_MST.FUND_TP}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(
            ScdulPymnSumr           master,
            List<ScdulPymnSumrDetl> lines,
            String                  trnfCcyCd,
            String                  apprFundTp) {

        String bizKey = master.getBizKey();
        LocalDateTime nowVn = LocalDateTime.now(clock.withZone(VN_TZ));
        String tranDt = nowVn.format(DT8);
        String dttm14 = nowVn.format(DTM14);

        // 1. Header row (TB_PAY_FILE_MST). FUND_TP from APPR_FUND_TP.
        String fileSeqNo = keyGenerator.nextFileSeqNo(bizKey, tranDt);

        TbPayFileMst mst = new TbPayFileMst();
        mst.setTranDt(tranDt);
        mst.setBizKey(bizKey);
        mst.setFileSeqNo(fileSeqNo);
        mst.setOutBankCd(master.getOutBankCd());
        mst.setWdrlActNo(master.getWdrlActNo());
        mst.setBankPrtnId(BANK_PRTN_ID_PLACEHOLDER);
        mst.setFundTp(resolveFundTp(apprFundTp));
        mst.setTranTp(TRAN_TP_DEPOSIT);
        mst.setProcStat(PROC_STAT_PENDING);
        mst.setReqCnt(master.getTotTrnsCnt());
        mst.setReqAmt(master.getTotalTranAmt());
        mst.setRegDttm(dttm14);

        tbPayFileMstRepository.save(mst);

        // Stamp the master's shadow keys so the Result-detail join
        // (SCDUL_PYMN_SUMR → TB_PAY_FILE_MST via PAY_TRAN_DT + PAY_FILE_SEQ_NO)
        // resolves. Same as the shared file path.
        master.setPayTranDt(tranDt);
        master.setPayFileSeqNo(fileSeqNo);

        // 2. Detail rows (TB_PAY_FILE_DATA). SEQ seeded ONCE via the
        //    wb_get_next_val funnel (V140 #20, per (biz_key, file_seq_no) MAX+1)
        //    then incremented per line — no per-row DB call. fileSeqNo is freshly
        //    allocated above, so the seed is 1 → 1, 2, 3, ...
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
            // TRSC_CCY_CD — file-path parity: the request currency. Payroll is
            // file-only regardless of currency; no Woori/Shinhan USD→VND API
            // routing (that is Internal Transfer's per-bank rule, not Payroll's).
            data.setTrscCcyCd(trnfCcyCd);
            data.setTranAmt(line.getTranAmt());
            data.setBankChnlDiv(
                    sameBank(master.getOutBankCd(), line.getTarBankCd())
                            ? BANK_CHNL_DIV_CURRENT_BANK   // "T"
                            : BANK_CHNL_DIV_OTHER_BANK);    // "O"
            data.setFincMdaOwnerNm(resolveOwnerNm(bizKey, line));
            // Legacy quirk parity (PymnHelper:2295): WDRL_ACT_RMRK copied into
            // both TAR_ACCT_RMRK and WDRL_ACT_RMRK.
            data.setTarAcctRmrk(line.getWdrlActRmrk());
            data.setWdrlActRmrk(line.getWdrlActRmrk());
            data.setRemitCcyCd(line.getTrnfCcyCd());
            data.setProcRsltDiv(PROC_RSLT_DIV_UNPROCESSED);

            tbPayFileDataRepository.save(data);

            // Stamp shadow keys on the detail — file-path join uses
            // PAY_TRAN_SEQ against TB_PAY_FILE_DATA.SEQ (same as shared path).
            line.setPayTranDt(tranDt);
            line.setPayTranSeq(seq.toPlainString());
            line.setPayFileSeqNo(null);
            line.setPaySeq(null);
        }

        log.info("feature=payroll-payment dispatch path=FILE bizKey={} scdulPymnNo={} lines={} tranDt={} fileSeqNo={} fundTp={}",
                bizKey, master.getScdulPymnNo(), lines.size(), tranDt, fileSeqNo, resolveFundTp(apprFundTp));
    }

    /* ───────────────── Helpers ───────────────── */

    /** Legacy {@code FundTp.fromValue(APPR_FUND_TP)}: "0"→Salary, "1"→Bonus,
     *  "9"/null/blank/unknown → Other (default). */
    private static String resolveFundTp(String apprFundTp) {
        if (FUND_TP_SALARY.equals(apprFundTp)) return FUND_TP_SALARY;
        if (FUND_TP_BONUS.equals(apprFundTp))  return FUND_TP_BONUS;
        return FUND_TP_OTHER;
    }

    /** Resolve {@code FINC_MDA_OWNER_NM} (NOT NULL): RCPT_NM, else ACCT_MSTR
     *  lookup, else the account number. Mirrors the shared executor. */
    private String resolveOwnerNm(String bizKey, ScdulPymnSumrDetl line) {
        String fromLine = line.getRcptNm();
        if (fromLine != null && !fromLine.isBlank()) {
            return fromLine;
        }
        String tarBankCd = line.getTarBankCd();
        String acctNo    = line.getFincMdaNo();
        if (acctNo != null && !acctNo.isBlank() && tarBankCd != null && !tarBankCd.isBlank()) {
            String nm = acctMstrRepository
                    .findByBizKeyAndBankCdAndAcctNo(bizKey, tarBankCd, acctNo)
                    .map(AcctMstr::getAcctNm)
                    .filter(s -> s != null && !s.isBlank())
                    .orElse(null);
            if (nm != null) return nm;
        }
        return acctNo != null && !acctNo.isBlank() ? acctNo : "UNKNOWN";
    }

    private static boolean sameBank(String outBankCd, String tarBankCd) {
        return outBankCd != null && outBankCd.equals(tarBankCd);
    }
}
