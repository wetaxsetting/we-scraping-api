package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkReceptionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 01301 bulk reception result callback handler — refactor of
 * {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp} + the inline absorption of the
 * removed {@code FILE_DEPOSIT_TRANSFER_BATCH2} 00302 issuance.
 *
 * <p><b>Confirmed TOBE (Mr. Kang, 2026-06-12, K9):</b> BATCH2 is removed; a
 * <b>normal {@code 01301} reception-result response is itself the execution
 * instruction</b>, so on a successful reception the handler issues {@code 00302}
 * <b>inline</b>. There is <b>no periodic recovery scheduler</b> — a lost callback
 * or a 00302 that never confirms is a monitoring / manual-operations concern.
 *
 * <p>Flow ({@link #handle}, one transaction):
 * <ol>
 *   <li>resolve the per-VAN key by {@code envelope.id()}, verify {@code VV},
 *       decrypt {@code EV} → {@link BulkReceptionResultRequest};</li>
 *   <li>validate the individual required fields (legacy step 3);</li>
 *   <li>look up the file at {@code (TRAN_DT=today, FIRM_FILE_NM, PROC_STEP='0',
 *       PROC_STAT='6')} (legacy {@code R004}). <b>A file not in that state is a
 *       no-op</b> — a duplicate callback (file already at step '1'/'2') or an
 *       unknown file is safely ignored, never re-executed;</li>
 *   <li>write the {@code 01301} audit row ({@code TB_FIRM_BAT_API_TRAN});</li>
 *   <li>reconcile {@code result_cd} + received {@code data_cnt}/{@code data_amt}
 *       vs requested {@code REQ_CNT}/{@code REQ_AMT}:
 *       <ul>
 *         <li>{@code result_cd != BT00} → file {@code PROC_STEP='1'/PROC_STAT='3'}
 *             (fail), <b>no 00302</b>;</li>
 *         <li>{@code BT00} but count/amount mismatch → fail ({@code REG_RSLT_CD='9999'}),
 *             <b>no 00302</b>;</li>
 *         <li>{@code BT00} and match → file {@code PROC_STEP='1'/PROC_STAT='6'}
 *             (reception success), then issue 00302 inline.</li>
 *       </ul></li>
 *   <li><b>split-file gate</b> (legacy {@code R003}): a single request may be
 *       split into several bank files; 00302 is held until every sibling of the
 *       same {@code FILE_SEQ_NO} has been received. When the last sibling arrives,
 *       00302 is issued for each reception-success sibling — group issue, inline,
 *       no sweep.</li>
 * </ol>
 *
 * <p><b>Idempotency.</b> The {@code 01301 → 00302} step never double-executes:
 * (a) the {@code PROC_STEP='0'} lookup means a duplicate callback finds nothing to
 * process, and (b) before each 00302 the {@code TB_FIRM_BAT_API_TRAN} ledger is
 * probed ({@link TbFirmBatApiTranRepository#existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp})
 * — a file that already has a 00302 row is skipped. The 00302 audit row is written
 * <b>before</b> the gateway call, so even a no-response leaves a marker that blocks
 * any re-issue.
 *
 * <p><b>Crypto note (B2):</b> the inbound envelope is verified/decrypted here with
 * the real {@link EfsCryptoService} + the per-VAN key from {@link VanApiKeyService}.
 *
 * <p>Out of scope (unchanged): the K3 fixed-length result-file parse (01302 path),
 * the {@code TB_PAY_FILE_MST} cross-file failure propagation, and the encrypted
 * response envelope echoed to EFS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkReceptionResultService {

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TRAN_DT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TRAN_TM_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TNO_MAX = 50;

    /** Legacy reception-success token compared in {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:247}. */
    private static final String RECEPTION_SUCCESS_CD = "BT00"; // TODO(B5): confirm full reception result-code set.

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;
    private final TbFirmFileMstRepository firmFileMstRepository;
    private final TbFirmBatApiTranRepository firmBatApiTranRepository;
    private final DispatchKeyGenerator dispatchKeyGenerator;

    /**
     * Handle a 01301 reception-result callback: decrypt → validate → reconcile →
     * advance the file → (on success) issue 00302 inline, idempotently.
     *
     * @throws BusinessException     ({@code WBE_EFS_VERIFY_FAILED}) when the inbound
     *                               VV/EV cannot be verified/decrypted
     * @throws IllegalStateException when the API key is not provisioned (B2)
     */
    @Transactional
    public void handle(EfsCallbackEnvelope envelope) {
        if (envelope == null || !StringUtils.hasText(envelope.id())) {
            throw new IllegalArgumentException("callback envelope ID is required");
        }
        String vanOrgCd = envelope.id();

        // 1. Resolve key + verify VV + decrypt EV (legacy steps 1–2). A bad MAC must not process.
        String apiKey = vanApiKeyService.getApiKey(vanOrgCd);
        if (!efsCryptoService.verifyVv(envelope.ev(), envelope.vv(), apiKey)) {
            log.warn("01301 callback VV verification failed vanOrgCd={} tno={}", vanOrgCd, envelope.tno());
            throw new BusinessException("WBE_EFS_VERIFY_FAILED");
        }
        BulkReceptionResultRequest req = decode(efsCryptoService.decryptEv(envelope.ev(), apiKey));

        // 2. Individual required-field check (legacy step 3).
        validateRequest(req);

        LocalDateTime now = LocalDateTime.now(ICT);
        String tranDt = now.format(TRAN_DT_FMT);
        String tranTm = now.format(TRAN_TM_FMT);

        // 3. Look up the reception-requested file (legacy R004). PROC_STEP guard:
        //    a duplicate callback (file already advanced) finds nothing → safe no-op.
        TbFirmFileMst file = firmFileMstRepository
                .findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                        tranDt, req.fileNm(), ProcStep.REQ.code(), ProcStat.REQ.code())
                .orElse(null);
        if (file == null) {
            log.info("01301 callback ignored — no file at PROC_STEP=0/PROC_STAT=6 (already progressed or unknown) "
                    + "vanOrgCd={} fileNm={}", vanOrgCd, req.fileNm());
            return;
        }

        BigDecimal receivedAmt = combineAmount(req.dataAmt(), req.dataDcmAmt());

        // 4. 01301 reception-result audit row.
        boolean receptionSuccess = RECEPTION_SUCCESS_CD.equals(req.resultCd());
        writeApiTran(file, tranDt, tranTm, ApiTranTp.BULK_RECEPTION_RESULT.code(),
                bigDecimalOrZero(req.dataCnt()), receivedAmt,
                req.resultCd(), req.resultMsg(),
                receptionSuccess ? FirmBankingResultCd.SUCC : FirmBankingResultCd.ETC_ERR,
                receptionSuccess ? FirmBankingResultCd.MSG_SUCC : FirmBankingResultCd.MSG_ETC_ERR,
                tranDt + tranTm);

        // 5. Reconcile and advance the file (legacy step 5).
        if (!receptionSuccess) {
            failFile(file, tranDt + tranTm, req.dataCnt(), receivedAmt, req.resultCd(), req.resultMsg());
            log.info("01301 reception failed (result_cd={}) — no 00302 vanOrgCd={} fileNm={}",
                    req.resultCd(), vanOrgCd, req.fileNm());
            return;
        }
        if (isMismatch(req.dataCnt(), receivedAmt, file)) {
            failFile(file, tranDt + tranTm, req.dataCnt(), receivedAmt,
                    FirmBankingResultCd.AMT_ERR, FirmBankingResultCd.MSG_AMT_ERR);
            log.warn("01301 reception count/amount mismatch — no 00302 vanOrgCd={} fileNm={} "
                            + "recvCnt={} reqCnt={} recvAmt={} reqAmt={}",
                    vanOrgCd, req.fileNm(), req.dataCnt(), file.getReqCnt(), receivedAmt, file.getReqAmt());
            return;
        }

        // Reception success + totals match → PROC_STEP 0→1, PROC_STAT='6' (ready to execute).
        file.setProcStep(ProcStep.REQ_RECV.code());
        file.setProcStat(ProcStat.REQ.code());
        file.setRegCnt(bigDecimalOrZero(req.dataCnt()));
        file.setRegAmt(receivedAmt);
        file.setRegRsltCd(req.resultCd());
        file.setRegRsltMsg(req.resultMsg());
        file.setProcDttm(tranDt + tranTm);
        firmFileMstRepository.save(file);

        // 6. Split-file gate + inline 00302 (absorbs BATCH2). Hold until every sibling of the
        //    same FILE_SEQ_NO has been received; then issue 00302 for each reception-success sibling.
        List<TbFirmFileMst> siblings = firmFileMstRepository
                .findByTranDtAndBizKeyAndFileSeqNo(tranDt, file.getBizKey(), file.getFileSeqNo());
        boolean awaitingSiblings = siblings.stream()
                .anyMatch(f -> ProcStep.REQ.code().equals(f.getProcStep()));
        if (awaitingSiblings) {
            log.info("01301 reception success — deferring 00302, sibling split files still pending "
                    + "vanOrgCd={} fileSeqNo={}", vanOrgCd, file.getFileSeqNo());
            return;
        }
        for (TbFirmFileMst ready : siblings) {
            if (ProcStep.REQ_RECV.code().equals(ready.getProcStep())
                    && ProcStat.REQ.code().equals(ready.getProcStat())) {
                issueExecutionInline(ready, vanOrgCd, tranDt, tranTm);
            }
        }
    }

    /**
     * Issue {@code 00302} for one reception-success file, idempotently, and advance
     * it to {@code PROC_STEP='2'} (execution requested) on a confirmed response.
     * Mirrors {@code FILE_DEPOSIT_TRANSFER_BATCH2}: audit-before-call, then update.
     *
     * <p>A {@code 00302} that already has a {@code TB_FIRM_BAT_API_TRAN} row is
     * skipped (idempotency). A no-response / gateway failure is caught, recorded on
     * the audit row, and leaves the file at {@code PROC_STEP='1'} for manual
     * recovery — it never rolls back the reception-success state and never throws
     * out of the 01301 callback.
     */
    private void issueExecutionInline(TbFirmFileMst file, String vanOrgCd, String tranDt, String tranTm) {
        if (firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                tranDt, file.getBizKey(), file.getFirmFileNm(), ApiTranTp.BULK_EXECUTION.code())) {
            log.info("00302 already issued for fileNm={} (idempotent skip) bizKey={}",
                    file.getFirmFileNm(), file.getBizKey());
            return;
        }

        String reqCnt = file.getReqCnt() == null ? "0" : file.getReqCnt().toBigInteger().toString();
        String[] amt = splitAmount(file.getReqAmt());

        // Audit BEFORE the call so even a no-response leaves a 00302 marker that blocks re-issue.
        TbFirmBatApiTran audit = writeApiTran(file, tranDt, tranTm, ApiTranTp.BULK_EXECUTION.code(),
                file.getReqCnt(), file.getReqAmt(), null, null, null, null, null);

        try {
            issueExecution(new BulkExecutionApiCommand(
                    file.getVanOrgCd() != null ? file.getVanOrgCd() : vanOrgCd,
                    file.getOutBankCd(), file.getBankPrtnId(), file.getFirmFileNm(),
                    reqCnt, amt[0], amt[1]));

            // Confirmed → record success + advance PROC_STEP 1→2 (execution requested).
            audit.setRsCd(FirmBankingResultCd.SUCC);
            audit.setRsMsg(FirmBankingResultCd.MSG_SUCC);
            audit.setRsDttm(tranDt + tranTm);
            firmBatApiTranRepository.save(audit);

            file.setProcStep(ProcStep.DEPO_REQ.code());
            file.setProcDttm(tranDt + tranTm);
            firmFileMstRepository.save(file);
            log.info("00302 execution issued inline fileNm={} bizKey={} -> PROC_STEP=2",
                    file.getFirmFileNm(), file.getBizKey());
        } catch (BusinessException e) {
            // No-response / gateway error: keep PROC_STEP=1, record the result, do NOT rethrow.
            // Recovery is monitoring / manual operations (Mr. Kang, K9) — no automatic re-issue.
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            audit.setRsCd(rc);
            audit.setRsMsg(e.getCode());
            audit.setRsDttm(tranDt + tranTm);
            firmBatApiTranRepository.save(audit);
            log.warn("00302 execution NOT confirmed (rc={}) — file left at PROC_STEP=1 for manual recovery "
                    + "fileNm={} bizKey={}", rc, file.getFirmFileNm(), file.getBizKey());
        }
    }

    /** Mark the file reception-failed: {@code PROC_STEP='1' / PROC_STAT='3'}. */
    private void failFile(TbFirmFileMst file, String procDttm, String regCnt, BigDecimal regAmt,
                          String rsltCd, String rsltMsg) {
        file.setProcStep(ProcStep.REQ_RECV.code());
        file.setProcStat(ProcStat.FAIL.code());
        file.setRegCnt(bigDecimalOrZero(regCnt));
        file.setRegAmt(regAmt);
        file.setRegRsltCd(rsltCd);
        file.setRegRsltMsg(rsltMsg);
        file.setProcDttm(procDttm);
        firmFileMstRepository.save(file);
    }

    /** Build + persist a {@code TB_FIRM_BAT_API_TRAN} audit row; returns the managed entity. */
    private TbFirmBatApiTran writeApiTran(TbFirmFileMst file, String tranDt, String tranTm, String apiTranTp,
                                          BigDecimal batCnt, BigDecimal batAmt,
                                          String regRsltCd, String regRsltMsg,
                                          String rsCd, String rsMsg, String rsDttm) {
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
        row.setRegRsltCd(regRsltCd);
        row.setRegRsltMsg(regRsltMsg);
        row.setRsCd(rsCd);
        row.setRsMsg(rsMsg);
        row.setRsDttm(rsDttm);
        return firmBatApiTranRepository.save(row);
    }

    private void validateRequest(BulkReceptionResultRequest req) {
        requireField(req.bankCd(), "bank_cd");
        requireField(req.prtnId(), "prtn_id");
        requireField(req.fileNm(), "file_nm");
        requireField(req.dataCnt(), "data_cnt");
        requireField(req.dataAmt(), "data_amt");
        requireField(req.dataDcmAmt(), "data_dcm_amt");
        requireField(req.resultCd(), "result_cd");
        requireField(req.resultMsg(), "result_msg");
    }

    private void requireField(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("01301 callback missing required field: " + name);
        }
    }

    /** {@code true} when received count or amount differs from the requested totals (legacy step 5-2-1). */
    private boolean isMismatch(String dataCnt, BigDecimal receivedAmt, TbFirmFileMst file) {
        BigDecimal reqCnt = file.getReqCnt() == null ? BigDecimal.ZERO : file.getReqCnt();
        BigDecimal reqAmt = file.getReqAmt() == null ? BigDecimal.ZERO : file.getReqAmt();
        return bigDecimalOrZero(dataCnt).compareTo(reqCnt) != 0 || receivedAmt.compareTo(reqAmt) != 0;
    }

    /** Combine integer + decimal-portion strings into one {@link BigDecimal} (legacy {@code StringToBigDec}). */
    private BigDecimal combineAmount(String itg, String dcm) {
        String i = StringUtils.hasText(itg) ? itg.trim() : "0";
        String d = StringUtils.hasText(dcm) ? dcm.trim() : "0";
        return new BigDecimal(i + "." + d);
    }

    /** Split a stored amount (scale 2) back into [integer, decimal-portion] for the 00302 request. */
    private String[] splitAmount(BigDecimal amt) {
        BigDecimal a = amt == null ? BigDecimal.ZERO : amt;
        String itg = a.toBigInteger().toString();
        String dcm = a.subtract(new BigDecimal(a.toBigInteger())).movePointRight(2).abs().toBigInteger().toString();
        return new String[] {itg, dcm};
    }

    private BigDecimal bigDecimalOrZero(String value) {
        return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : BigDecimal.ZERO;
    }

    private BulkReceptionResultRequest decode(String json) {
        return deserialize(json, BulkReceptionResultRequest.class);
    }

    /**
     * The {@code WGVN_EFS_302} bulk-execution <b>API request leg</b> — build →
     * encrypt → POST {@code EFS_302} → parse. Called inline from {@link #handle}
     * via {@link #issueExecutionInline} on a confirmed reception; also directly
     * callable for the API leg in isolation.
     *
     * <p>The per-line transfer outcomes return later via the 01302 callback + the
     * SFTP result file (K3 + B4); this leg only transmits the header totals.
     *
     * @return the echoed header totals on {@code RC=0000}
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000} gateway result
     */
    public BulkExecutionApiResult issueExecution(BulkExecutionApiCommand cmd) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs302Request request = new Efs302Request(
                cmd.bankCd(), cmd.prtnId(), cmd.fileNm(), cmd.dataCnt(), cmd.dataAmt(), cmd.dataDcmAmt());
        String businessJson = serialize(request);

        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        String id = StringUtils.hasText(efsProperties.getPartnerId())
                ? efsProperties.getPartnerId() : cmd.vanOrgCd();

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_302, requestEnvelope);

        String rc = response == null ? null : response.rc();
        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            log.warn("EFS 302 execution failed vanOrgCd={} fileNm={} dataCnt={} rc={}",
                    cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        Efs302Response parsed = deserialize(responseJson, Efs302Response.class);

        log.debug("EFS 302 execution ok vanOrgCd={} fileNm={} dataCnt={} rc={}",
                cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
        return BulkExecutionApiResult.from(parsed);
    }

    private void validate(BulkExecutionApiCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("BulkExecutionApiCommand is required");
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

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise EFS request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS payload", e);
        }
    }
}
