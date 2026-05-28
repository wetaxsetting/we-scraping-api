package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.entity.OvrsPymnDtls;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
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
 * Shared dispatch executor for Overseas Payment batches. Mirrors
 * {@link com.kosign.wecms.domain.payment.dispatch.service.TransferDispatchExecutor}
 * but speaks the {@code OVRS_PYMN_*} schema (different table family
 * from {@code SCDUL_PYMN_*}) and always uses the File path — Overseas
 * remittances go through the SWIFT / international wire rail, never the
 * domestic NAPAS API path.
 *
 * <p>Per row in the batch:
 * <ol>
 *   <li>Insert one {@link TbPayFileMst} header (PROC_STAT='9' pending,
 *       FUND_TP='9' overseas remittance, TRAN_TP='1', BANK_PRTN_ID placeholder).</li>
 *   <li>Insert one {@link TbPayFileData} per detail line. Beneficiary
 *       fields are mapped from {@link OvrsPymnDtls}: {@code BIC_CD} →
 *       {@code TAR_BANK_CD}, {@code BNFC_ACCT_NO} → {@code FINC_MDA_NO},
 *       {@code BNFC_NM} → {@code FINC_MDA_OWNER_NM}, {@code TRNF_AMT} →
 *       {@code TRAN_AMT}, {@code CCY_CD} → {@code TRSC_CCY_CD}.</li>
 *   <li>Stamp shadow keys on the master ({@code PAY_TRAN_DT} +
 *       {@code PAY_FILE_SEQ_NO}) so the Result-detail IDO query can join
 *       {@code OVRS_PYMN_SUMR ⋈ TB_PAY_FILE_MST}.</li>
 *   <li>Stamp shadow keys on each detail ({@code PAY_TRAN_DT} +
 *       {@code PAY_TRAN_SEQ} = {@code TB_PAY_FILE_DATA.SEQ} as varchar).</li>
 * </ol>
 *
 * <p>Status values are conservative: every row goes in with
 * {@code PROC_STAT='9'} (pending). The live JEX bank-integration daemon
 * flips them to '0' / '3' on bank callback; refactor R&D doesn't touch
 * that flip path.
 *
 * <p>Mirrors legacy {@code pymn_0007_001_c001_act.jsp:100-456}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasDispatchExecutor {

    private static final DateTimeFormatter DT8   = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Vietnam-local zone — same window the cut-off-time validator uses. */
    private static final ZoneId VN_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Status pinned on every freshly-written dispatch row. */
    private static final String PROC_STAT_PENDING = "9";

    /** {@code TB_PAY_FILE_DATA.PROC_RSLT_DIV} initial state. */
    private static final String PROC_RSLT_DIV_UNPROCESSED = "N";

    /** {@code FUND_TP='9'} — overseas remittance. Mirrors the DEV
     *  {@code tb_pay_file_mst} distribution: every cross-currency batch
     *  (USD↔VND) carries this value, verified against 28 dispatched
     *  overseas-flavored rows on JEX DEV. */
    private static final String FUND_TP_OVERSEAS = "9";

    /** {@code TRAN_TP='1'} — DEV's actual stored value. The
     *  SCDUL_PYMN executor writes {@code 'D'} (enum {@code TranTpF.DEPOSIT})
     *  which diverges from DEV; the Overseas path aligns with DEV from
     *  the start. */
    private static final String TRAN_TP_DISPATCH = "1";

    /** {@code TB_PAY_FILE_DATA.FINC_MDA_DIV='A'} — account-number target.
     *  Same value the SCDUL_PYMN executor uses. */
    private static final String FINC_MDA_DIV_ACCOUNT = "A";

    /** Cross-bank routing flag — Overseas is always cross-bank by
     *  definition (the recipient bank is overseas / SWIFT). */
    private static final String BANK_CHNL_DIV_OTHER_BANK = "O";

    /** {@code BANK_PRTN_ID} placeholder — the real value lives in
     *  {@code TB_FIRM_CONF.BANK_PRTN_ID}. Mirrors the SCDUL_PYMN executor's
     *  pragmatic choice (bank-side daemon re-resolves before sending). */
    private static final String BANK_PRTN_ID_PLACEHOLDER = "DEFAULT";

    private final TbPayFileMstRepository  tbPayFileMstRepository;
    private final TbPayFileDataRepository tbPayFileDataRepository;
    private final DispatchKeyGenerator    keyGenerator;
    private final Clock                   clock;

    /**
     * Insert TB_PAY_FILE_MST + TB_PAY_FILE_DATA rows for one Overseas
     * batch + stamp the shadow keys back on the master + detail rows.
     * Must run inside the caller's transaction (caller's
     * {@code TRNF_YN='Y'} stamp and the dispatch inserts commit or
     * roll back together).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(OvrsPymnSumr master, List<OvrsPymnDtls> lines) {
        String bizKey = master.getBizKey();
        LocalDateTime nowVn = LocalDateTime.now(clock.withZone(VN_TZ));
        String tranDt = nowVn.format(DT8);
        String dttm14 = nowVn.format(DTM14);

        // 1. Header row.
        String fileSeqNo = keyGenerator.nextFileSeqNo(bizKey, tranDt);

        TbPayFileMst mst = new TbPayFileMst();
        mst.setTranDt(tranDt);
        mst.setBizKey(bizKey);
        mst.setFileSeqNo(fileSeqNo);
        mst.setOutBankCd(master.getOutBankCd());
        mst.setWdrlActNo(master.getWdrlActNo());
        mst.setBankPrtnId(BANK_PRTN_ID_PLACEHOLDER);
        mst.setFundTp(FUND_TP_OVERSEAS);
        mst.setTranTp(TRAN_TP_DISPATCH);
        mst.setProcStat(PROC_STAT_PENDING);
        mst.setReqCnt(master.getTotTrnsCnt() == null
                ? null : new BigDecimal(master.getTotTrnsCnt()));
        mst.setReqAmt(master.getTotalTranAmt());
        mst.setRegDttm(dttm14);

        tbPayFileMstRepository.save(mst);

        // Stamp the master's shadow keys so the Result-detail IDO query
        // can join OVRS_PYMN_SUMR → TB_PAY_FILE_MST via
        // (master.PAY_TRAN_DT, master.PAY_FILE_SEQ_NO). Legacy parity
        // per pymn_0007_001_c001_act.jsp:179-401.
        master.setPayTranDt(tranDt);
        master.setPayFileSeqNo(fileSeqNo);

        // 2. Detail rows. SEQ starts at 1 and increments per line.
        BigDecimal seq = BigDecimal.ONE;
        for (OvrsPymnDtls line : lines) {
            TbPayFileData data = new TbPayFileData();
            data.setTranDt(tranDt);
            data.setBizKey(bizKey);
            data.setFileSeqNo(fileSeqNo);
            data.setSeq(seq);
            // BIC code identifies the beneficiary bank on the SWIFT rail.
            data.setTarBankCd(line.getBicCd());
            data.setFincMdaDiv(FINC_MDA_DIV_ACCOUNT);
            data.setFincMdaNo(line.getBnfcActNo());
            // Use the line's transfer currency (carried by each
            // beneficiary row — Overseas batches can be heterogeneous).
            data.setTrscCcyCd(line.getCcyCd());
            data.setTranAmt(line.getTrnfAmt());
            data.setBankChnlDiv(BANK_CHNL_DIV_OTHER_BANK);
            data.setFincMdaOwnerNm(resolveOwnerNm(line));
            data.setRemitCcyCd(line.getCcyCd());
            data.setProcRsltDiv(PROC_RSLT_DIV_UNPROCESSED);

            tbPayFileDataRepository.save(data);

            // Stamp shadow keys on the detail. Mirrors the SCDUL_PYMN
            // executor's File-path stamping pattern — pay_tran_seq
            // stores the file-data SEQ as varchar so the Result-detail
            // join can compare it to a string column.
            line.setPayTranDt(tranDt);
            line.setPayTranSeq(seq.toPlainString());

            seq = seq.add(BigDecimal.ONE);
        }

        log.info("feature=overseas-payment dispatch path=FILE bizKey={} ovrsPymnNo={} lines={} tranDt={} fileSeqNo={}",
                bizKey, master.getOvrsPymnNo(), lines.size(), tranDt, fileSeqNo);
    }

    /**
     * Resolve the recipient's owner name for the NOT-NULL
     * {@code FINC_MDA_OWNER_NM} column. Prefers {@code BNFC_NM} from the
     * detail line; falls back to the beneficiary account number when the
     * Excel parser produced no name (defensive — keeps the insert alive).
     */
    private static String resolveOwnerNm(OvrsPymnDtls line) {
        String bnfcNm = line.getBnfcNm();
        if (bnfcNm != null && !bnfcNm.isBlank()) {
            return bnfcNm;
        }
        String acctNo = line.getBnfcActNo();
        return acctNo != null && !acctNo.isBlank() ? acctNo : "UNKNOWN";
    }
}
