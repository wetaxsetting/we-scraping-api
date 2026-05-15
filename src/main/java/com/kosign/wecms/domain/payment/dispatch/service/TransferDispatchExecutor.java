package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
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
 * Shared executor invoked by every payment module's dispatch service
 * (Internal Transfer / Vendor / Expense / Payroll) when the user clicks
 * Transfer.
 *
 * <p>Mirrors the legacy single shared dispatcher at
 * {@code PymnHelper.java:2142-2369}. Branch on {@code TRNF_CCY_CD}:
 *
 * <ul>
 *   <li><b>VND → API path</b>: insert one {@link TbPayTarget} per detail
 *       line. Stamps each {@link ScdulPymnSumrDetl} with
 *       {@code PAY_TRAN_DT} + {@code PAY_TRAN_SEQ} so the read-side count
 *       query (see {@code DispatchCountRepository}) can match details to
 *       their dispatch rows.</li>
 *   <li><b>Non-VND → File path</b>: insert one {@link TbPayFileMst}
 *       header, then one {@link TbPayFileData} per detail line. Stamps
 *       {@code PAY_TRAN_DT} + {@code PAY_FILE_SEQ_NO} + {@code PAY_SEQ}
 *       on each detail.</li>
 * </ul>
 *
 * <p>Status values are deliberately conservative: every row goes in with
 * {@code PROC_STAT = '9'} (pending). Until a downstream bank-side updater
 * flips them to {@code '0'} (success) or {@code '3'} (error), the Payment
 * Result list reads each batch as "In Progress" via the legacy two-status
 * rule (see {@code pymn_0002_001.ts:443-450}). That mirrors reality — we
 * wrote the dispatch rows, but no real bank exchange happened yet.
 *
 * <p>Sprint 2+ owners of the bank-side flip path: when wiring NAPAS /
 * CITAD / SWIFT callbacks, look up rows here by the matching key set
 * and {@code UPDATE … PROC_STAT, PROC_DTTM, RS_CD, RS_MSG}.
 *
 * <p>The frontend pre-flight check ({@code validateBankingTime} in
 * {@code add_osmc.js}) and the backend cut-off-time validator
 * ({@code CutoffValidator}) run BEFORE this executor — by the time
 * {@link #execute} fires, the timing window has been cleared.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferDispatchExecutor {

    private static final DateTimeFormatter DT8   = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM6   = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Vietnam-local zone — same window the cut-off-time validator uses. */
    private static final ZoneId VN_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Status pinned on every freshly-written dispatch row. Mirrors the
     *  DEV {@code tb_pay_file_mst.proc_stat DEFAULT '9'} default; tells
     *  the count query "this batch hasn't settled yet". */
    private static final String PROC_STAT_PENDING = "9";

    /** {@code TB_PAY_FILE_DATA.PROC_RSLT_DIV} initial state. DEV default
     *  is {@code 'N'} (unprocessed). */
    private static final String PROC_RSLT_DIV_UNPROCESSED = "N";

    /** Hard-coded {@code TB_PAY_FILE_MST.FUND_TP} = 'O' (other). Legacy
     *  PymnHelper:2222 uses {@code FundTp.OTHER}. */
    private static final String FUND_TP_OTHER = "O";

    /** Hard-coded {@code TB_PAY_FILE_MST.TRAN_TP} = 'D' (deposit). Legacy
     *  PymnHelper:2223 uses {@code TranTpF.DEPOSIT}. */
    private static final String TRAN_TP_DEPOSIT = "D";

    /** {@code TB_PAY_FILE_DATA.FINC_MDA_DIV} = 'A' (account). Legacy
     *  PymnHelper:2259 uses {@code FincMdaDiv.ACCOUNT}. */
    private static final String FINC_MDA_DIV_ACCOUNT = "A";

    /** Same-bank routing flag. Legacy PymnHelper:2280 uses
     *  {@code BankChnlDiv.CURRENT_BANK_TRANSFER}. */
    private static final String BANK_CHNL_DIV_CURRENT_BANK = "C";

    /** Cross-bank routing flag. Legacy PymnHelper:2281 uses
     *  {@code BankChnlDiv.OTHER_BANK_TRANSFER}. */
    private static final String BANK_CHNL_DIV_OTHER_BANK = "O";

    /** API path same-bank tag. Mirrors legacy
     *  {@code AntDivCd.INTER.value()}. */
    private static final String ANT_DIV_INTER = "INTER";

    /** Placeholder bank-partner id used on the File path. The real
     *  value lives in {@code TB_FIRM_CONF.BANK_PRTN_ID} which the
     *  refactor doesn't yet expose; PymnHelper:2226 reads it from the
     *  same table. Using a non-empty placeholder lets the NOT-NULL
     *  column persist; bank-side updaters re-resolve before sending. */
    private static final String BANK_PRTN_ID_PLACEHOLDER = "DEFAULT";

    private final TbPayTargetRepository  tbPayTargetRepository;
    private final TbPayFileMstRepository tbPayFileMstRepository;
    private final TbPayFileDataRepository tbPayFileDataRepository;
    private final DispatchKeyGenerator   keyGenerator;
    private final Clock                  clock;

    /**
     * Insert dispatch rows for one batch. Branch on the request
     * currency: VND → {@link TbPayTarget} (API), else → {@link TbPayFileMst}
     * + {@link TbPayFileData} (File).
     *
     * <p>Must run inside the caller's transaction — the master
     * {@code TRNF_YN='Y'} stamp and the dispatch inserts must commit or
     * roll back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(
            ScdulPymnSumr            master,
            List<ScdulPymnSumrDetl>  lines,
            String                   trnfCcyCd) {

        String bizKey = master.getBizKey();
        String userId = TenantContext.currentUserId();
        LocalDateTime nowVn = LocalDateTime.now(clock.withZone(VN_TZ));
        String tranDt = nowVn.format(DT8);
        String regTm  = nowVn.format(TM6);
        String dttm14 = nowVn.format(DTM14);

        if ("VND".equalsIgnoreCase(trnfCcyCd)) {
            writeApiPath(master, lines, bizKey, userId, tranDt, regTm, dttm14, trnfCcyCd);
        } else {
            writeFilePath(master, lines, bizKey, tranDt, dttm14, trnfCcyCd);
        }
    }

    /* ───────────────── VND / API path ───────────────── */

    private void writeApiPath(
            ScdulPymnSumr master,
            List<ScdulPymnSumrDetl> lines,
            String bizKey,
            String userId,
            String tranDt,
            String regTm,
            String dttm14,
            String trnfCcyCd) {

        for (ScdulPymnSumrDetl line : lines) {
            String tranSeq = keyGenerator.nextTranSeq(bizKey, tranDt);

            TbPayTarget row = new TbPayTarget();
            row.setTranDt(tranDt);
            row.setBizKey(bizKey);
            row.setTranSeq(tranSeq);
            row.setOutBankCd(master.getOutBankCd());
            row.setWdrlActNo(master.getWdrlActNo());
            row.setRegTm(regTm);
            // ANT_DIV_CD legacy rule (PymnHelper:2160): same-bank → INTER,
            // else the detail-line's own ANT_DIV_CD (NAPAS / CITAD / etc.)
            row.setAntDivCd(resolveAntDivCd(master.getOutBankCd(), line));
            row.setTarBankCd(line.getTarBankCd());
            row.setFincMdaNo(line.getFincMdaNo());
            row.setFincMdaOwnerNm(line.getRcptNm());
            row.setTranAmt(line.getTranAmt());
            row.setTranUsag(line.getTranUsag());
            row.setProcStat(PROC_STAT_PENDING);
            row.setProcDttm(dttm14);
            row.setUserId(userId);
            row.setTrscCcyCd(trnfCcyCd);
            row.setWithdCcyCd(master.getWdrlCcyCd());
            row.setDepCcyCd(line.getDepoCcyCd());

            tbPayTargetRepository.save(row);

            // Stamp shadow keys on the detail so DispatchCountRepository
            // can join SCDUL_PYMN_SUMR_DETL ⋈ TB_PAY_TARGET cleanly.
            line.setPayTranDt(tranDt);
            line.setPayTranSeq(tranSeq);
            line.setPayFileSeqNo(null);
            line.setPaySeq(null);
        }

        log.info("feature=dispatch path=API bizKey={} scdulPymnNo={} lines={} tranDt={}",
                bizKey, master.getScdulPymnNo(), lines.size(), tranDt);
    }

    /* ───────────────── Non-VND / File path ───────────────── */

    private void writeFilePath(
            ScdulPymnSumr master,
            List<ScdulPymnSumrDetl> lines,
            String bizKey,
            String tranDt,
            String dttm14,
            String trnfCcyCd) {

        // 1. Header row.
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

        // 2. Detail rows. SEQ starts at 1 and increments per line.
        BigDecimal seq = BigDecimal.ONE;
        for (ScdulPymnSumrDetl line : lines) {
            TbPayFileData data = new TbPayFileData();
            data.setTranDt(tranDt);
            data.setBizKey(bizKey);
            data.setFileSeqNo(fileSeqNo);
            data.setSeq(seq);
            data.setTarBankCd(line.getTarBankCd());
            data.setFincMdaDiv(FINC_MDA_DIV_ACCOUNT);
            data.setFincMdaNo(line.getFincMdaNo());
            // TRSC_CCY_CD: legacy (PymnHelper:2266-2276) picks WDRL_CCY_CD for
            // Shinhan, TRNF_CCY_CD for Woori. Refactor uses the request's
            // trnfCcyCd as the unified source — the bank-specific override
            // can be added when the bank dictionary lands.
            data.setTrscCcyCd(trnfCcyCd);
            data.setTranAmt(line.getTranAmt());
            data.setBankChnlDiv(
                    sameBank(master.getOutBankCd(), line.getTarBankCd())
                            ? BANK_CHNL_DIV_CURRENT_BANK
                            : BANK_CHNL_DIV_OTHER_BANK);
            data.setFincMdaOwnerNm(line.getRcptNm());
            // TAR_ACCT_RMRK preserves the legacy quirk (PymnHelper:2295):
            // the legacy code copies WDRL_ACT_RMRK into TAR_ACCT_RMRK
            // instead of TAR_ACCT_RMRK — keep the bug for parity.
            data.setTarAcctRmrk(line.getWdrlActRmrk());
            data.setWdrlActRmrk(line.getWdrlActRmrk());
            data.setRemitCcyCd(line.getTrnfCcyCd());
            data.setProcRsltDiv(PROC_RSLT_DIV_UNPROCESSED);

            tbPayFileDataRepository.save(data);

            // Stamp shadow keys back on the detail.
            line.setPayTranDt(tranDt);
            line.setPayTranSeq(null);
            line.setPayFileSeqNo(fileSeqNo);
            line.setPaySeq(seq);

            seq = seq.add(BigDecimal.ONE);
        }

        log.info("feature=dispatch path=FILE bizKey={} scdulPymnNo={} lines={} tranDt={} fileSeqNo={}",
                bizKey, master.getScdulPymnNo(), lines.size(), tranDt, fileSeqNo);
    }

    /* ───────────────── Helpers ───────────────── */

    private static String resolveAntDivCd(String outBankCd, ScdulPymnSumrDetl line) {
        if (outBankCd != null && outBankCd.equals(line.getTarBankCd())) {
            return ANT_DIV_INTER;
        }
        String fromLine = line.getAntDivCd();
        return (fromLine == null || fromLine.isBlank()) ? ANT_DIV_INTER : fromLine;
    }

    private static boolean sameBank(String outBankCd, String tarBankCd) {
        return outBankCd != null && outBankCd.equals(tarBankCd);
    }
}
