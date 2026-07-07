package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositHeader;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.file.VietnameseText;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.BulkValidationCounts;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk reception — refactor of legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1}.
 *
 * <p>Confirmed TOBE (Mr. Kang 2026-06-06): BATCH1 becomes a <b>request-time</b>
 * flow. {@link #receive} reproduces the BATCH1 core: validate the pay-file
 * target → resolve {@code TB_FIRM_CONF} → build the fixed-length file
 * ({@link FixedLengthCodec}) → insert {@code TB_FIRM_FILE_MST}
 * ({@code PROC_STEP=0/PROC_STAT=6}) + {@code TB_FIRM_FILE_DATA} → SFTP-upload →
 * audit {@code 00301} (before the call) → {@link #issueReception} (EFS 00301).
 *
 * <h2>Legacy-fidelity guarantees</h2>
 * <ul>
 *   <li><b>Audit before the external call</b> — the {@code TB_FIRM_BAT_API_TRAN}
 *       row is written before {@code issueReception}, then updated with the result.</li>
 *   <li><b>No unsafe state advance</b> — {@code TB_FIRM_FILE_MST} is created at
 *       {@code PROC_STEP=0} and is NEVER advanced here (the {@code 0→1} advance
 *       belongs to the 01301 callback). On any SFTP/API failure the file and the
 *       pay-file row are set to {@code PROC_STAT='3'} (fail); nothing moves
 *       forward.</li>
 *   <li><b>Idempotency / duplicate protection</b> — the pay-file target is
 *       processed only from {@code PROC_STAT='9'} (WAIT); a row already in
 *       {@code '6'}/{@code '0'}/{@code '3'} is a safe no-op (mirrors legacy
 *       {@code R001} selecting only WAIT + the {@code R004} fail skip).</li>
 * </ul>
 *
 * <h2>Transaction model</h2>
 * One {@code @Transactional} unit. The happy path commits the file-header/detail
 * inserts and leaves the pay row at {@code '6'}. An EFS/SFTP {@link BusinessException}
 * is caught, recorded as a committed FAIL state, and <b>not</b> rethrown — so the
 * failure persists (legacy parity) instead of rolling back to WAIT. (Minor
 * deviation from legacy, documented: legacy ran SFTP/API outside the DB tx; here
 * they run inside it — acceptable for low-volume R&D.)
 *
 * <h2>Branch-split into multiple files (C5, ported 2026-06-13)</h2>
 * When {@code TB_FIRM_CONF.BAT_TRAN_BRANCH_DIV='Y'} and the detail count exceeds
 * {@code BAT_TRAN_BRANCH_LIMIT}, the request is split into
 * {@code ceil(reqCnt / limit)} files of at most {@code limit} rows each — the
 * refactor of legacy {@code calculateBatchDivision} + the per-file dispatch loop in
 * {@code FILE_DEPOSIT_TRANSFER_BATCH1}. Each split file is fully independent: its own
 * consecutive file round, {@code FIRM_FILE_NM}, {@code TB_FIRM_FILE_MST} /
 * {@code TB_FIRM_FILE_DATA} rows (line sequence restarts at 1 per file), fixed-length
 * file, SFTP upload, 00301 audit, and EFS 301 call. The non-split path is the special
 * case {@code loopCnt=1} and is byte-for-byte unchanged. A per-file failure marks the
 * shared pay-file FAIL and stops the batch (legacy {@code R004} skip parity — once the
 * pay-file is FAIL, every remaining sibling is skipped). The whole {@code receive()}
 * is one transaction, so a build that throws before the EFS leg rolls back entirely —
 * no partial/oversized files.
 *
 * <h2>Deferred (NOT in this phase — documented blockers)</h2>
 * The full BATCH1 pre-flight is intentionally out of scope: channel-mix validation
 * (EBANK/VCB/CITAD/NAPAS rules), the operating-hours gate
 * ({@code TB_BATCH_SCHEDULE_*}), and Slack alerting ({@code MsgSendUtil}). These port
 * in a later phase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkReceptionService {

    private static final DateTimeFormatter DTTM14_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TRAN_TM_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TNO_MAX = 50;

    /** Legacy {@code Deposit_Data} address field is 180 bytes (post-2025.10.29 layout). */
    private static final int RCPT_USE_ADDR_MAX_BYTES = 180;

    /** Legacy {@code WCConst.NON_PROCESSING} — detail rows start un-processed. */
    private static final String NON_PROCESSING = "N";

    /** Legacy no-INFO-row message (FILE_DEPOSIT_TRANSFER_BATCH1.java:289, verbatim). */
    private static final String NO_CRITERIA_MSG =
            "No data to perform bulk transfer batch. Please check TB_BATCH_SCHEDULE_INFO.";

    /**
     * 1-char {@code ANT_DIV_CD} → operating-hours channel name, per the verified legacy
     * {@code R005} classification (IDO.TB_PAY_FILE_DATA_R005.xml + entity ANT_DIV_CD length 1).
     * The DTL table keys on the channel name (legacy {@code operTimeChk(bank, "EBANK")}).
     */
    private static final Map<String, String> CHANNEL_BY_ANT_DIV = Map.of(
            "E", "EBANK", "V", "VCB", "C", "CITAD", "N", "NAPAS", "S", "SWIFT");

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;

    private final TbPayFileMstRepository payFileMstRepository;
    private final TbPayFileDataRepository payFileDataRepository;
    private final TbFirmConfRepository firmConfRepository;
    private final TbFirmFileMstRepository firmFileMstRepository;
    private final TbFirmFileDataRepository firmFileDataRepository;
    private final TbFirmBatApiTranRepository firmBatApiTranRepository;
    private final DispatchKeyGenerator dispatchKeyGenerator;
    private final FixedLengthCodec fixedLengthCodec;
    private final SftpClient sftpClient;
    private final SftpProperties sftpProperties;
    /** BATCH1 pre-flight network/currency validator (legacy
     *  FILE_DEPOSIT_TRANSFER_BATCH1:113-266, Gate A). */
    private final BulkTransferValidator bulkTransferValidator;
    private final OpsAlertService opsAlertService;
    private final OperatingHoursService operatingHoursService;

    /**
     * Request-time bulk reception of one pay-file target: build + upload the
     * fixed-length file and issue 00301.
     *
     * @param tranDt    transaction date (YYYYMMDD) — pay-file key
     * @param bizKey    tenant business key
     * @param fileSeqNo bulk file sequence within the tenant/date
     */
    @Transactional
    public void receive(String tranDt, String bizKey, String fileSeqNo) {
        // 1. Load + validate the pay-file target (legacy R001 selects PROC_STAT='9').
        TbPayFileMst pay = payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(tranDt, bizKey, fileSeqNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "pay-file target not found: tranDt=" + tranDt + " bizKey=" + bizKey + " fileSeqNo=" + fileSeqNo));

        // Duplicate / re-run guard: only a WAIT target is dispatchable.
        if (!ProcStat.WAIT.code().equals(pay.getProcStat())) {
            log.info("receive() no-op — pay-file not WAIT (procStat={}) tranDt={} bizKey={} fileSeqNo={}",
                    pay.getProcStat(), tranDt, bizKey, fileSeqNo);
            return;
        }

        // 2. Resolve dispatch config (VAN_ORG_CD / FILE_TYPE / branch split) — legacy TB_FIRM_CONF_R001.
        TbFirmConf conf = firmConfRepository
                .findById(new TbFirmConfId(bizKey, pay.getOutBankCd(), pay.getWdrlActNo()))
                .orElseThrow(() -> new IllegalStateException(
                        "TB_FIRM_CONF missing for bizKey=" + bizKey + " outBankCd=" + pay.getOutBankCd()
                                + " wdrlActNo=" + pay.getWdrlActNo()));

        // 3. Load detail rows (legacy R001 orders by SEQ; sort so split windows are deterministic).
        List<TbPayFileData> details = new ArrayList<>(
                payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(bizKey, tranDt, fileSeqNo));
        if (details.isEmpty()) {
            throw new IllegalStateException("pay-file has no detail rows: fileSeqNo=" + fileSeqNo);
        }
        details.sort(Comparator.comparing(TbPayFileData::getSeq));

        int reqCnt = details.size();

        // 3a. BATCH1 pre-flight network/currency validation (legacy
        //     FILE_DEPOSIT_TRANSFER_BATCH1:113-266, Gate A) — runs BEFORE branch-split,
        //     the PROC_STAT='6' mark, and any TB_FIRM_* / SFTP / EFS work. On failure,
        //     the whole file is marked FAIL ('3') with the legacy RS_MSG and skipped
        //     (legacy `continue`). The operating-hours / cut-off gate (legacy :269-447,
        //     TB_BATCH_SCHEDULE_INFO/DTL) runs next in step 3b.
        BulkValidationCounts counts =
                payFileDataRepository.aggregateValidationCounts(bizKey, tranDt, fileSeqNo);
        Optional<String> vrfiMsg = bulkTransferValidator.validate(pay, counts);
        if (vrfiMsg.isPresent()) {
            pay.setProcStat(ProcStat.FAIL.code());
            pay.setRsMsg(vrfiMsg.get());
            payFileMstRepository.save(pay);
            log.warn("00301 pre-flight validation failed — file marked FAIL bizKey={} fileSeqNo={} reason={}",
                    bizKey, fileSeqNo, vrfiMsg.get());
            // Ops alert (legacy FILE_DEPOSIT_TRANSFER_BATCH1:249 "이체파일 Batch1 검증 오류 발생").
            opsAlertService.notify(conf.getVanOrgCd(),
                    "이체파일 Batch1 검증 오류 발생"
                            + "\nTRAN_DT : " + tranDt
                            + "\nBIZ_KEY : " + bizKey
                            + "\nFILE_SEQ_NO : " + fileSeqNo
                            + "\nOUT_BANK_CD : " + pay.getOutBankCd()
                            + "\nBANK_PRTN_ID : " + pay.getBankPrtnId()
                            + "\nREQ_CNT : " + reqCnt
                            + "\nVRFIMSG : " + vrfiMsg.get());
            return;
        }

        // 3b. Operating-hours gate (legacy FILE_DEPOSIT_TRANSFER_BATCH1:269-448,
        //     TB_BATCH_SCHEDULE_INFO/DTL, EXE_DIV). Runs BEFORE the PROC_STAT='6' mark.
        //     CUTOFF / NO_CRITERIA → FAIL the pay-file + ops alert + return (legacy exeErrYN
        //     block :411-443). SKIP ('N' before start, or bank-uses-network with no matching
        //     channel) → leave the file WAIT for a later run, no write, no alert (legacy
        //     silent `continue`). Present channels are the file's distinct 1-char ANT_DIV_CD
        //     mapped to names via the verified R005 classification (E/V/C/N/S).
        OperatingHoursDecision oh = operatingHoursService.evaluateBulk(pay.getOutBankCd(), presentChannels(details));
        if (oh == OperatingHoursDecision.SKIP) {
            log.info("00301 skipped — outside operating window (before start / channel not open) "
                    + "bizKey={} fileSeqNo={}", bizKey, fileSeqNo);
            return;
        }
        if (oh == OperatingHoursDecision.CUTOFF || oh == OperatingHoursDecision.NO_CRITERIA) {
            boolean cutoff = oh == OperatingHoursDecision.CUTOFF;
            // Legacy leaves vrfi_msg empty for cutoff; sets the "No data…" text for no-criteria.
            String rsMsg = cutoff ? "" : NO_CRITERIA_MSG;
            pay.setProcStat(ProcStat.FAIL.code());
            pay.setRsMsg(rsMsg);
            payFileMstRepository.save(pay);
            log.warn("00301 blocked ({}) bizKey={} fileSeqNo={} — pay-file FAIL",
                    cutoff ? "cutoff" : "no-criteria", bizKey, fileSeqNo);
            opsAlertService.notify(conf.getVanOrgCd(),
                    (cutoff ? "대량이체 Batch 거래 마감." : "대량이체 Batch 수행 기준 없음.")
                            + "\nTRAN_DT : " + tranDt
                            + "\nBIZ_KEY : " + bizKey
                            + "\nFILE_SEQ_NO : " + fileSeqNo
                            + "\nMSG : " + rsMsg);
            return;
        }

        // 4. Branch-split division (legacy calculateBatchDivision): split into
        //    ceil(reqCnt/limit) files of `chunkSize` rows when BAT_TRAN_BRANCH_DIV='Y'
        //    and reqCnt > limit; otherwise one file of all rows.
        int[] division = calculateBatchDivision(conf.getBatTranBranchDiv(), reqCnt, conf.getBatTranBranchLimit());
        int loopCnt = division[0];
        int chunkSize = division[1];

        LocalDateTime now = LocalDateTime.now(ICT);
        String dttm14 = now.format(DTTM14_FMT);
        String tranTm = now.format(TRAN_TM_FMT);

        // 5. Mark the pay-file in-process once (legacy U001 → PROC_STAT='6', before the loop).
        pay.setProcStat(ProcStat.REQ.code());
        pay.setProcDttm(dttm14);
        payFileMstRepository.save(pay);

        // 6. Base file round (legacy R002 MAX+1). Computed once; split file i uses base+i so the
        //    rounds stay consecutive without depending on intra-transaction flush visibility.
        int baseRound = nextFileRound(tranDt, pay.getOutBankCd(), pay.getBankPrtnId());

        if (loopCnt > 1) {
            log.info("00301 branch-split bizKey={} fileSeqNo={} reqCnt={} -> {} files of <= {} rows",
                    bizKey, fileSeqNo, reqCnt, loopCnt, chunkSize);
        }

        // 7. Build + dispatch each split file independently (legacy per-file loop).
        for (int i = 0; i < loopCnt; i++) {
            int from = i * chunkSize;
            int to = Math.min(from + chunkSize, reqCnt);
            List<TbPayFileData> window = details.subList(from, to);
            String fileSeq = String.format("%03d", baseRound + i);

            boolean ok = buildAndDispatchFile(pay, conf, tranDt, bizKey, fileSeqNo, window, fileSeq, dttm14, tranTm);
            if (!ok) {
                // Legacy parity (R004 skip, added 2025.09.10): once a split file fails the shared
                // pay-file is FAIL, so every remaining sibling would be skipped — stop the batch.
                log.warn("00301 branch-split aborted after a failed file — pay-file FAIL bizKey={} fileSeqNo={}",
                        bizKey, fileSeqNo);
                // Ops alert (legacy FILE_DEPOSIT_TRANSFER_BATCH1:751 "이체파일 Batch1 처리중 오류 발생").
                opsAlertService.notify(conf.getVanOrgCd(),
                        "이체파일 Batch1 처리중 오류 발생"
                                + "\nTRAN_DT : " + tranDt
                                + "\nBIZ_KEY : " + bizKey
                                + "\nFILE_SEQ_NO : " + fileSeqNo
                                + "\nOUT_BANK_CD : " + pay.getOutBankCd()
                                + "\nBANK_PRTN_ID : " + pay.getBankPrtnId()
                                + "\nREQ_CNT : " + reqCnt);
                break;
            }
        }
    }

    /**
     * Build, persist, upload, audit and 00301-dispatch ONE firm-banking file for the
     * given detail {@code window}. Shared by the single-file and branch-split paths
     * (the single-file path is just {@code window == all details}).
     *
     * <p>Inserts {@code TB_FIRM_FILE_MST} (PROC_STEP=0/PROC_STAT=6) + one
     * {@code TB_FIRM_FILE_DATA} per window row (line sequence restarts at 1), encodes
     * the fixed-length file, SFTP-uploads it, audits 00301 before the call, then issues
     * EFS 301. On SFTP or EFS failure both the firm file and the pay-file are marked
     * FAIL (no advance) and {@code false} is returned; the EFS error is NOT rethrown
     * (legacy parity — the FAIL state commits).
     *
     * @return {@code true} when the file was uploaded and 00301 accepted; {@code false}
     *         on an SFTP/EFS failure (caller stops the batch).
     */
    private boolean buildAndDispatchFile(TbPayFileMst pay, TbFirmConf conf, String tranDt, String bizKey,
                                         String fileSeqNo, List<TbPayFileData> window, String fileSeq,
                                         String dttm14, String tranTm) {
        int reqCnt = window.size();
        BigDecimal reqAmt = window.stream()
                .map(TbPayFileData::getTranAmt)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Filename S_{VAN}_{TYPE}_{round}_{date} — the per-file round makes each split file's
        // FIRM_FILE_NM (and therefore its MST/DATA primary keys) unique.
        String fileName = "S_" + conf.getVanOrgCd() + "_" + trim(conf.getFileType()) + "_" + fileSeq + "_" + tranDt;

        // Insert TB_FIRM_FILE_MST at PROC_STEP=0 / PROC_STAT=6.
        TbFirmFileMst mst = new TbFirmFileMst();
        mst.setTranDt(tranDt);
        mst.setBizKey(bizKey);
        mst.setFileSeqNo(fileSeqNo);
        mst.setFirmFileNm(fileName);
        mst.setOutBankCd(pay.getOutBankCd());
        mst.setBankPrtnId(conf.getBankPrtnId());
        mst.setVanOrgCd(conf.getVanOrgCd());
        mst.setFileType(trim(conf.getFileType()));
        mst.setWdrlActNo(pay.getWdrlActNo());
        mst.setFundTp(pay.getFundTp());
        mst.setTranTp(pay.getTranTp());
        mst.setFileSeq(fileSeq);
        mst.setProcStep(ProcStep.REQ.code());
        mst.setProcStat(ProcStat.REQ.code());
        mst.setReqCnt(BigDecimal.valueOf(reqCnt));
        mst.setReqAmt(reqAmt);
        mst.setRegDttm(dttm14);
        firmFileMstRepository.save(mst);

        // Insert TB_FIRM_FILE_DATA rows + build the fixed-length D records (line seq restarts at 1).
        List<DepositData> dataLines = new ArrayList<>(reqCnt);
        int seq = 0;
        for (TbPayFileData d : window) {
            seq++;
            BigDecimal lineSeq = BigDecimal.valueOf(seq);

            TbFirmFileData fd = new TbFirmFileData();
            fd.setTranDt(tranDt);
            fd.setFileSeqNo(fileSeqNo);
            fd.setFirmFileNm(fileName);
            fd.setSeq(lineSeq);
            fd.setBizKey(bizKey);
            fd.setFileSeq(fileSeq);
            fd.setOrgSeq(d.getSeq());
            fd.setTarBankCd(d.getTarBankCd());
            fd.setFincMdaDiv(d.getFincMdaDiv());
            fd.setFincMdaNo(d.getFincMdaNo());
            fd.setTrscCcyCd(d.getTrscCcyCd());
            fd.setTranAmt(d.getTranAmt());
            fd.setBankChnlDiv(d.getBankChnlDiv());
            fd.setAntDivCd(d.getAntDivCd());
            fd.setSenderNm(VietnameseText.replaceTone(d.getSenderNm()));
            fd.setFincMdaOwnerNm(VietnameseText.replaceTone(d.getFincMdaOwnerNm()));
            fd.setTarAcctRmrk(VietnameseText.replaceTone(d.getTarAcctRmrk()));
            fd.setWdrlAcctRmrk(VietnameseText.replaceTone(d.getWdrlActRmrk()));
            fd.setProcRsltDiv(NON_PROCESSING);
            fd.setRcptBankCntryCd(d.getRcptBankCntryCd());
            fd.setRemitCcyCd(d.getRemitCcyCd());
            fd.setRcptUseAddr(byteTruncate(VietnameseText.replaceTone(d.getRcptUseAddr()), RCPT_USE_ADDR_MAX_BYTES));
            fd.setFeeBrdnClsCd(d.getFeeBrdnClsCd());
            firmFileDataRepository.save(fd);

            dataLines.add(new DepositData(
                    String.valueOf(seq),                                  // seq
                    d.getFincMdaNo(),                                     // depositAccountNumber
                    d.getTrscCcyCd(),                                     // currencyCode
                    plain(d.getTranAmt()),                                // transferAmount (verbatim)
                    d.getBankChnlDiv(),                                   // sameBankFlag
                    d.getAntDivCd(),                                      // otherBankNetworkCode
                    "C".equals(trim(d.getFincMdaDiv())) ? "N" : "Y",      // cardAccountType (legacy mapping)
                    d.getTarBankCd(),                                     // depositBankCode
                    VietnameseText.replaceTone(d.getSenderNm()),          // senderName
                    VietnameseText.replaceTone(d.getFincMdaOwnerNm()),    // receiverName
                    VietnameseText.replaceTone(d.getTarAcctRmrk()),       // depositAccountMemo
                    VietnameseText.replaceTone(d.getWdrlActRmrk()),       // withdrawalAccountMemo
                    "",                                                   // processingResult (send file: blank)
                    "",                                                   // responseCode (send file: blank)
                    d.getRcptBankCntryCd(),                               // receiptBankCountryCode
                    d.getRemitCcyCd(),                                    // remittanceCurrencyCode
                    d.getFeeBrdnClsCd(),                                  // feeBrdnClsCd
                    byteTruncate(VietnameseText.replaceTone(d.getRcptUseAddr()), RCPT_USE_ADDR_MAX_BYTES) // rcptUseAddr
            ));
        }

        DepositHeader header = new DepositHeader(
                pay.getOutBankCd(), conf.getBankPrtnId(), tranDt, pay.getWdrlActNo(),
                pay.getFundTp(), pay.getTranTp(), fileSeq);
        DepositTrailer trailer = new DepositTrailer(
                String.valueOf(reqCnt), plain(reqAmt), "0", "0", "0", "0");

        byte[] fileBytes = fixedLengthCodec.encode(header, dataLines, trailer);

        // Write the local staging file.
        String localPath = writeLocalFile(fileName, fileBytes);

        // SFTP upload — on failure mark FAIL (no advance) and stop the batch.
        String remotePath = joinPath(sftpProperties.getRemoteUploadPath(), fileName);
        try {
            sftpClient.upload(localPath, remotePath);
        } catch (RuntimeException e) {
            log.error("00301 SFTP upload failed fileNm={} — marking FAIL", fileName, e);
            failBoth(pay, mst, FirmBankingResultCd.ETC_ERR, "SFTP upload failed", dttm14);
            return false;
        }

        // Audit the 00301 leg BEFORE the call.
        String[] amt = splitAmount(reqAmt);
        TbFirmBatApiTran audit = writeApiTran(mst, tranDt, tranTm, ApiTranTp.BULK_RECEPTION.code(),
                mst.getReqCnt(), mst.getReqAmt());

        // Issue EFS 00301.
        try {
            issueReception(new BulkReceptionApiCommand(
                    conf.getVanOrgCd(), pay.getOutBankCd(), conf.getBankPrtnId(),
                    fileName, String.valueOf(reqCnt), amt[0], amt[1]));

            audit.setRsCd(FirmBankingResultCd.SUCC);
            audit.setRsMsg(FirmBankingResultCd.MSG_SUCC);
            audit.setRsDttm(dttm14);
            firmBatApiTranRepository.save(audit);
            // Success: TB_FIRM_FILE_MST stays at PROC_STEP=0/PROC_STAT=6 — the 0→1 advance
            // is the 01301 callback's job. No unsafe advance here.
            log.info("00301 reception requested fileNm={} bizKey={} cnt={}", fileName, bizKey, reqCnt);
            return true;
        } catch (BusinessException e) {
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            audit.setRsCd(rc);
            audit.setRsMsg(e.getCode());
            audit.setRsDttm(dttm14);
            firmBatApiTranRepository.save(audit);
            failBoth(pay, mst, rc, "EFS 00301 failed: " + e.getCode(), dttm14);
            log.warn("00301 reception failed (rc={}) fileNm={} — FAIL recorded, no advance", rc, fileName);
            // Do NOT rethrow — commit the FAIL state (legacy parity).
            return false;
        }
    }

    /**
     * Branch-split division — refactor of legacy
     * {@code FILE_DEPOSIT_TRANSFER_BATCH1.calculateBatchDivision}.
     *
     * <p>Returns {@code [loopCnt, chunkSize]}. When {@code BAT_TRAN_BRANCH_DIV='Y'} and
     * {@code reqCnt > limit}, the request splits into {@code ceil(reqCnt / limit)} files
     * of at most {@code limit} rows each. Otherwise a single file of all {@code reqCnt}
     * rows.
     *
     * <p><b>Defensive deviation from legacy:</b> a null or non-positive limit is treated
     * as "no split" rather than reaching {@code ceil(reqCnt / 0)} (legacy
     * {@code Math.ceil(reqCnt / 0.0)} would overflow to a huge {@code int}). A
     * misconfigured limit therefore degrades to the safe single-file path instead of
     * producing an absurd file count.
     */
    /**
     * The set of operating-hours channel names present in the file — the distinct
     * 1-char {@code ANT_DIV_CD} of the detail rows mapped via {@link #CHANNEL_BY_ANT_DIV}
     * (E/V/C/N/S → EBANK/VCB/CITAD/NAPAS/SWIFT). Unmapped / blank codes are ignored
     * (legacy {@code ETC_CNT} — not operating-hours-gated). Mirrors the legacy per-channel
     * {@code count > 0} guards (FILE_DEPOSIT_TRANSFER_BATCH1.java:299/319/339/359/379).
     */
    private Set<String> presentChannels(List<TbPayFileData> details) {
        Set<String> channels = new LinkedHashSet<>();
        for (TbPayFileData d : details) {
            String code = d.getAntDivCd();
            if (code == null) {
                continue; // Map.of() rejects null-key lookups; blank ANT_DIV_CD = legacy ETC (ungated)
            }
            String name = CHANNEL_BY_ANT_DIV.get(code);
            if (name != null) {
                channels.add(name);
            }
        }
        return channels;
    }

    private int[] calculateBatchDivision(String branchDiv, int reqCnt, BigDecimal branchLimit) {
        if ("Y".equals(trim(branchDiv)) && branchLimit != null) {
            int limit = branchLimit.intValue();
            if (limit > 0 && reqCnt > limit) {
                int loopCnt = (int) Math.ceil((double) reqCnt / limit);
                return new int[] {loopCnt, limit};
            }
        }
        return new int[] {1, reqCnt};
    }

    /** Mark both the firm file ({@code PROC_STEP=0/PROC_STAT=3}) and the pay file ({@code '3'}) failed. */
    private void failBoth(TbPayFileMst pay, TbFirmFileMst mst, String rsltCd, String msg, String dttm14) {
        mst.setProcStep(ProcStep.REQ.code());
        mst.setProcStat(ProcStat.FAIL.code());
        mst.setRegRsltCd(rsltCd);
        mst.setRegRsltMsg(msg);
        mst.setProcDttm(dttm14);
        firmFileMstRepository.save(mst);

        pay.setProcStat(ProcStat.FAIL.code());
        pay.setRsMsg(msg);
        pay.setProcDttm(dttm14);
        payFileMstRepository.save(pay);
    }

    /** Insert a {@code TB_FIRM_BAT_API_TRAN} audit row (00301), result columns left null. */
    private TbFirmBatApiTran writeApiTran(TbFirmFileMst file, String tranDt, String tranTm,
                                          String apiTranTp, BigDecimal batCnt, BigDecimal batAmt) {
        TbFirmBatApiTran row = new TbFirmBatApiTran();
        row.setTranDt(tranDt);
        row.setBizKey(file.getBizKey());
        row.setTranSeq(dispatchKeyGenerator.nextTranSeq(file.getBizKey(), tranDt));
        row.setApiTranTp(apiTranTp);
        row.setOutBankCd(file.getOutBankCd());
        row.setBankPrtnId(file.getBankPrtnId());
        row.setTranTm(tranTm);
        row.setFirmFileNm(file.getFirmFileNm());
        row.setBatCnt(batCnt == null ? BigDecimal.ZERO : batCnt);
        row.setBatAmt(batAmt == null ? BigDecimal.ZERO : batAmt);
        return firmBatApiTranRepository.save(row);
    }

    /** Next file round (MAX existing + 1) for the bank/partner group (legacy R002 MAX+1). */
    private int nextFileRound(String tranDt, String outBankCd, String bankPrtnId) {
        int max = firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(tranDt, outBankCd, bankPrtnId).stream()
                .map(TbFirmFileMst::getFileSeq)
                .filter(StringUtils::hasText)
                .mapToInt(s -> {
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException nfe) {
                        return 0;
                    }
                })
                .max().orElse(0);
        return max + 1;
    }

    /** Write the generated file to the local staging dir (or the JVM temp dir if unset). */
    private String writeLocalFile(String fileName, byte[] bytes) {
        try {
            String dir = StringUtils.hasText(sftpProperties.getLocalUploadPath())
                    ? sftpProperties.getLocalUploadPath()
                    : System.getProperty("java.io.tmpdir");
            Path target = Path.of(dir, fileName);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, bytes);
            return target.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write local dispatch file " + fileName, e);
        }
    }

    private static String joinPath(String dir, String name) {
        if (!StringUtils.hasText(dir)) {
            return name;
        }
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    /** Truncate to {@code maxBytes} UTF-8 bytes without splitting a character (legacy {@code getStrByteMaxLen}). */
    private static String byteTruncate(String value, int maxBytes) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int total = 0;
        int cut = 0;
        for (int i = 0; i < value.length(); i++) {
            int len = String.valueOf(value.charAt(i)).getBytes(FixedLengthCodec.CHARSET).length;
            if (total + len > maxBytes) {
                break;
            }
            total += len;
            cut = i + 1;
        }
        return value.substring(0, cut);
    }

    /** Split a scale-2 amount into [integer, decimal-portion] strings for the 00301 request. */
    private static String[] splitAmount(BigDecimal amt) {
        BigDecimal a = amt == null ? BigDecimal.ZERO : amt;
        String itg = a.toBigInteger().toString();
        String dcm = a.subtract(new BigDecimal(a.toBigInteger())).movePointRight(2).abs().toBigInteger().toString();
        return new String[] {itg, dcm};
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.toPlainString();
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    // ------------------------------------------------------------------ 00301 API leg (unchanged)

    /**
     * The {@code WGVN_EFS_301} bulk-reception <b>API request leg</b> — build →
     * encrypt → POST {@code EFS_301} → parse. Called from {@link #receive} after
     * the file is built + uploaded; also directly callable for the leg in isolation.
     *
     * @return the echoed header totals on {@code RC=0000}
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000} gateway result
     */
    public BulkReceptionApiResult issueReception(BulkReceptionApiCommand cmd) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs301Request request = new Efs301Request(
                cmd.bankCd(), cmd.prtnId(), cmd.fileNm(), cmd.dataCnt(), cmd.dataAmt(), cmd.dataDcmAmt());
        String businessJson = serialize(request, "301");

        String rqDtime = LocalDateTime.now(ICT).format(DTTM14_FMT);
        String tno = newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        String id = StringUtils.hasText(efsProperties.getPartnerId())
                ? efsProperties.getPartnerId() : cmd.vanOrgCd();

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_301, requestEnvelope);

        String rc = response == null ? null : response.rc();
        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            log.warn("EFS 301 reception failed vanOrgCd={} fileNm={} dataCnt={} rc={}",
                    cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        Efs301Response parsed = deserialize(responseJson, Efs301Response.class, "301");

        log.debug("EFS 301 reception ok vanOrgCd={} fileNm={} dataCnt={} rc={}",
                cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
        return BulkReceptionApiResult.from(parsed);
    }

    private void validate(BulkReceptionApiCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("BulkReceptionApiCommand is required");
        }
        if (!StringUtils.hasText(cmd.vanOrgCd())) {
            throw new IllegalArgumentException("vanOrgCd is required");
        }
        if (!StringUtils.hasText(cmd.fileNm())) {
            throw new IllegalArgumentException("fileNm is required");
        }
        if (!StringUtils.hasText(cmd.dataCnt())) {
            throw new IllegalArgumentException("dataCnt is required");
        }
    }

    private String newTno(String rqDtime) {
        String tno = rqDtime + UUID.randomUUID().toString().replace("-", "");
        return tno.length() > TNO_MAX ? tno.substring(0, TNO_MAX) : tno;
    }

    private String serialize(Object value, String svc) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise EFS " + svc + " request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type, String svc) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS " + svc + " response", e);
        }
    }
}
