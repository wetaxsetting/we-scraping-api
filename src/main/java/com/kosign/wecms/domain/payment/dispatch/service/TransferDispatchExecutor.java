package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
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
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
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
    // Legacy parity — FundTp.OTHER = "9" (wabooks/enums/FundTp.java:9).
    // Verified against JEX DEV: tb_pay_file_mst.fund_tp ∈ {'0','1','9','S'}
    // — the letter 'O' was never a valid value.
    private static final String FUND_TP_OTHER = "9";

    /** Hard-coded {@code TB_PAY_FILE_MST.TRAN_TP} = 'D' (deposit). Legacy
     *  PymnHelper:2223 uses {@code TranTpF.DEPOSIT}. */
    // Legacy parity — TranTpF.DEPOSIT = "1" (wabooks/enums/TranTpF.java:8).
    // Verified against JEX DEV: tb_pay_file_mst.tran_tp = '1' on every row
    // — the letter 'D' was never a valid value.
    private static final String TRAN_TP_DEPOSIT = "1";

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
    /** Banking-account master — used to resolve the recipient's account
     *  holder name ({@code FINC_MDA_OWNER_NM}) when the batch detail
     *  carries a null {@code RCPT_NM}. Mirrors legacy
     *  {@code PymnHelper.validateBankAccountInAcctMstr} at parse time;
     *  this dispatch-time fallback covers batches where the parser
     *  didn't populate the name (pre-fix uploads, future modules that
     *  share this executor). */
    private final AcctMstrRepository     acctMstrRepository;
    private final DispatchKeyGenerator   keyGenerator;
    private final Clock                  clock;
    private final LegacyFunctionRepository legacyFunctions;

    /** Post-commit 00301 hand-off (port of BATCH1), gated by
     *  {@code wecms.dispatch.auto-receive}. Shared by every File-path executor. */
    private final DispatchAutoReceiveRegistrar autoReceiveRegistrar;

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
            row.setFincMdaOwnerNm(resolveOwnerNm(bizKey, line));
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

        // Stamp the master's shadow keys so the Result-detail IDO query
        // (SCDUL_PYMN_SUMR_R006) can join SCDUL_PYMN_SUMR → TB_PAY_FILE_MST
        // via (SPS.PAY_TRAN_DT, SPS.PAY_FILE_SEQ_NO). Legacy PymnHelper:2366-2367
        // does the same — without this stamp, Phase 2 reads back null for
        // every File-path batch's master PROC_STAT.
        master.setPayTranDt(tranDt);
        master.setPayFileSeqNo(fileSeqNo);

        // 2. Detail rows. SEQ seeded ONCE via the wb_get_next_val funnel (V140 #20,
        //    per (biz_key, file_seq_no) MAX+1) then incremented per line — no per-row DB call.
        //    fileSeqNo is freshly allocated per dispatch, so the seed is 1 → 1, 2, 3, ...
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
            data.setFincMdaOwnerNm(resolveOwnerNm(bizKey, line));
            // TAR_ACCT_RMRK preserves the legacy quirk (PymnHelper:2295):
            // the legacy code copies WDRL_ACT_RMRK into TAR_ACCT_RMRK
            // instead of TAR_ACCT_RMRK — keep the bug for parity.
            data.setTarAcctRmrk(line.getWdrlActRmrk());
            data.setWdrlActRmrk(line.getWdrlActRmrk());
            data.setRemitCcyCd(line.getTrnfCcyCd());
            data.setProcRsltDiv(PROC_RSLT_DIV_UNPROCESSED);

            tbPayFileDataRepository.save(data);

            // Stamp shadow keys back on the detail. Legacy parity (per
            // PymnHelper:2347 + IDO SCDUL_PYMN_SUMR_R006.xml:178-182): the
            // File-path join uses `SPMD.PAY_TRAN_SEQ` against
            // `TB_PAY_FILE_DATA.SEQ` (cast to varchar) — the same
            // `pay_tran_seq` column the API path uses. The `pay_file_seq_no`
            // + `pay_seq` columns on the detail row are refactor-only and
            // intentionally left null (JEX DEV: 0/486 detail rows
            // populate either column).
            line.setPayTranDt(tranDt);
            line.setPayTranSeq(seq.toPlainString());
            line.setPayFileSeqNo(null);
            line.setPaySeq(null);

        }

        log.info("feature=dispatch path=FILE bizKey={} scdulPymnNo={} lines={} tranDt={} fileSeqNo={}",
                bizKey, master.getScdulPymnNo(), lines.size(), tranDt, fileSeqNo);

        // 3. Hand off to the 00301 reception flow AFTER this transaction commits.
        autoReceiveRegistrar.registerAfterCommit(tranDt, bizKey, fileSeqNo);
    }

    /* ───────────────── Helpers ───────────────── */

    /**
     * Resolve the recipient's account-holder name for the legacy
     * {@code FINC_MDA_OWNER_NM} column. The column is NOT NULL on both
     * {@code TB_PAY_TARGET} (API leg) and {@code TB_PAY_FILE_DATA}
     * (File leg), so a missing value crashes the dispatch with a raw
     * PostgreSQL NOT NULL violation.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code SCDUL_PYMN_SUMR_DETL.RCPT_NM} when set by the
     *       upload parser (legacy parity — see
     *       {@code PymnHelper.validateBankAccountInAcctMstr}).</li>
     *   <li>{@code ACCT_MSTR.ACCT_NM} looked up by
     *       {@code (BIZ_KEY, BANK_CD, ACCT_NO)} — Internal Transfer
     *       always targets one of the company's own accounts, so this
     *       resolves even when the Excel parser skipped the name
     *       column.</li>
     *   <li>{@code ACCT_MSTR.ACCT_NM} looked up by
     *       {@code (BIZ_KEY, ACCT_NO)} — same shape the legacy uses
     *       (no bank-code filter). Handles the case where the upload
     *       captured an off-by-one bank code.</li>
     *   <li>Last-resort: the recipient account number itself, so the
     *       insert still satisfies the NOT NULL constraint instead of
     *       surfacing a {@code WBE_DB} envelope to the user.</li>
     * </ol>
     *
     * <p>For dispatch flows that pass through this executor with a
     * recipient outside {@code ACCT_MSTR} (Vendor / Expense / Payroll
     * to a third-party contact), the parser is expected to fill
     * {@code RCPT_NM} from the contact lookup; this fallback only
     * fires for Internal Transfer's same-tenant accounts.
     */
    private String resolveOwnerNm(String bizKey, ScdulPymnSumrDetl line) {
        String fromLine = line.getRcptNm();
        if (fromLine != null && !fromLine.isBlank()) {
            return fromLine;
        }
        String tarBankCd = line.getTarBankCd();
        String acctNo    = line.getFincMdaNo();
        if (acctNo != null && !acctNo.isBlank()) {
            if (tarBankCd != null && !tarBankCd.isBlank()) {
                String nm = acctMstrRepository
                        .findByBizKeyAndBankCdAndAcctNo(bizKey, tarBankCd, acctNo)
                        .map(AcctMstr::getAcctNm)
                        .filter(s -> s != null && !s.isBlank())
                        .orElse(null);
                if (nm != null) return nm;
            }
        }
        // Last-resort — keep the dispatch alive. The bank-side will
        // still process the transfer using FINC_MDA_NO + TAR_BANK_CD
        // as the lookup key.
        return acctNo != null && !acctNo.isBlank() ? acctNo : "UNKNOWN";
    }

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
