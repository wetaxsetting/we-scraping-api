package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkExecutionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.event.PaymentDispatchResultFinalizedEvent;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileRslt;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileRsltRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 01302 bulk execution result callback handler — refactor of
 * {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp}.
 *
 * <p>Flow ({@link #handle}, one transaction): verify {@code VV} + decrypt
 * {@code EV} → parse {@code file_nm} → look up the file at
 * {@code (TRAN_DT, FIRM_FILE_NM, PROC_STEP='2', PROC_STAT='6')} (legacy
 * {@code R006}) → audit {@code 01302} → SFTP-download the result file → parse it
 * ({@link FixedLengthCodec}) → insert {@code TB_FIRM_FILE_RSLT} per {@code D}
 * line → reflect each result onto {@code TB_FIRM_FILE_DATA} → advance
 * {@code TB_FIRM_FILE_MST} {@code PROC_STEP 2→3 / PROC_STAT='0'} with the trailer
 * aggregates → reflect onto {@code TB_PAY_FILE_DATA} → when every sibling of the
 * same {@code FILE_SEQ_NO} has reported, finalize {@code TB_PAY_FILE_MST} status
 * ({@code 0} all-success / {@code 1} partial / {@code 3} all-fail).
 *
 * <h2>Fail-safe / no-corruption</h2>
 * The whole method is {@code @Transactional}. A file not at {@code step2/stat6}
 * is a safe no-op (duplicate / late / unknown callback — already processed).
 * A bad envelope, a missing/empty download, or a malformed result file
 * <b>throws</b> → the transaction rolls back → {@code TB_FIRM_FILE_MST} stays at
 * {@code PROC_STEP='2'} (replayable). Malformed records are never silently
 * ignored, and {@code PROC_STEP 2→3} is reached only after every row is parsed
 * and written.
 *
 * <p><b>Deviation from legacy (documented):</b> legacy committed the 01302 audit
 * row outside the parse transaction so it survived a parse failure. Here the audit
 * shares the single transaction, so a parse failure rolls the audit back too — the
 * trade-off keeps the handler a single clean atomic unit. The MST state (the thing
 * that must not corrupt) is protected either way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkExecutionResultService {

    private static final DateTimeFormatter DTTM14_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TRAN_TM_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final ObjectMapper objectMapper;
    private final SftpClient sftpClient;
    private final SftpProperties sftpProperties;
    private final FixedLengthCodec fixedLengthCodec;
    private final DispatchKeyGenerator dispatchKeyGenerator;

    private final TbFirmFileMstRepository firmFileMstRepository;
    private final TbFirmFileDataRepository firmFileDataRepository;
    private final TbFirmFileRsltRepository firmFileRsltRepository;
    private final TbFirmBatApiTranRepository firmBatApiTranRepository;
    private final TbPayFileMstRepository payFileMstRepository;
    private final TbPayFileDataRepository payFileDataRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OpsAlertService opsAlertService;

    /**
     * Handle a 01302 execution-result callback.
     *
     * @throws BusinessException     ({@code WBE_EFS_VERIFY_FAILED}) on a bad MAC,
     *                               ({@code WBE_EFS_RESULT_FILE}) on a missing/empty/
     *                               malformed result file
     * @throws IllegalStateException when the API key is not provisioned (B2)
     */
    @Transactional
    public void handle(EfsCallbackEnvelope envelope) {
        if (envelope == null || !StringUtils.hasText(envelope.id())) {
            throw new IllegalArgumentException("callback envelope ID is required");
        }
        String vanOrgCd = envelope.id();

        // 1. Resolve key + verify VV + decrypt EV.
        String apiKey = vanApiKeyService.getApiKey(vanOrgCd);
        if (!efsCryptoService.verifyVv(envelope.ev(), envelope.vv(), apiKey)) {
            log.warn("01302 callback VV verification failed vanOrgCd={} tno={}", vanOrgCd, envelope.tno());
            throw new BusinessException("WBE_EFS_VERIFY_FAILED");
        }
        BulkExecutionResultRequest req = decode(efsCryptoService.decryptEv(envelope.ev(), apiKey));

        // 2. Required-field check (legacy detail guard).
        requireField(req.bankCd(), "bank_cd");
        requireField(req.prtnId(), "prtn_id");
        requireField(req.fileNm(), "file_nm");
        requireField(req.dataAmt(), "data_amt");
        requireField(req.dataDcmAmt(), "data_dcm_amt");

        // 3. Parse file_nm = {prefix}_{vanOrgCd}_{type}_{fileSeq}_{tranDt}.
        String[] parts = req.fileNm().split("_");
        if (parts.length < 5) {
            // Ops alert (legacy FILE_DEPOSIT_TRANSFER_BATCH2:280 "이체실행지시 Batch2 API수신 오류 발생").
            opsAlertService.notify(vanOrgCd,
                    "이체실행지시 Batch2 API수신 오류 발생"
                            + "\nFIRM_FILE_NM : " + req.fileNm()
                            + "\nAPI RM : malformed file_nm");
            throw new BusinessException("WBE_EFS_RESULT_FILE", "malformed file_nm: " + req.fileNm());
        }
        String tranDt = parts[4];

        // 4. Look up the execution-requested file (legacy R006): PROC_STEP='2'/PROC_STAT='6'.
        TbFirmFileMst mst = firmFileMstRepository
                .findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                        tranDt, req.fileNm(), ProcStep.DEPO_REQ.code(), ProcStat.REQ.code())
                .orElse(null);
        if (mst == null) {
            log.info("01302 callback ignored — no file at PROC_STEP=2/PROC_STAT=6 (already processed or unknown) "
                    + "vanOrgCd={} fileNm={}", vanOrgCd, req.fileNm());
            return;
        }
        String bizKey = mst.getBizKey();
        String fileSeqNo = mst.getFileSeqNo();

        LocalDateTime now = LocalDateTime.now(ICT);
        String dttm14 = now.format(DTTM14_FMT);
        String tranTm = now.format(TRAN_TM_FMT);

        // 5. Audit the 01302 leg.
        BigDecimal batAmt = combineAmount(req.dataAmt(), req.dataDcmAmt());
        TbFirmBatApiTran audit = writeApiTran(mst, tranDt, tranTm, ApiTranTp.BULK_EXECUTION_RESULT.code(),
                bigDecimalOrZero(req.dataCnt()), batAmt);

        // 6. SFTP download the bank result file (then best-effort move to the processed dir).
        String localPath = downloadResultFile(req.fileNm());

        // 7. Read + parse (malformed/empty → throw → rollback, MST stays at step 2).
        FixedLengthCodec.ParsedFile parsed = parseResultFile(localPath, req.fileNm());

        // 8. Insert TB_FIRM_FILE_RSLT per D line + reflect onto TB_FIRM_FILE_DATA.
        Map<Integer, TbFirmFileData> firmDataBySeq = indexFirmDataBySeq(tranDt, fileSeqNo, req.fileNm());
        Map<Integer, TbPayFileData> payDataBySeq = indexPayDataBySeq(bizKey, tranDt, fileSeqNo);

        for (DepositData line : parsed.lines()) {
            int seq = parseInt(line.seq());
            String procRsltDiv = blankTo(line.processingResult(), "N");
            String rsCd = trim(line.responseCode());

            TbFirmFileRslt rslt = new TbFirmFileRslt();
            rslt.setTranDt(tranDt);
            rslt.setBizKey(bizKey);
            rslt.setFirmFileNm(req.fileNm());
            rslt.setSeq(BigDecimal.valueOf(seq));
            rslt.setFileSeq(mst.getFileSeq());
            rslt.setTarBankCd(trim(line.depositBankCode()));
            rslt.setFincMdaDiv(blankTo(line.cardAccountType(), "Y"));
            rslt.setFincMdaNo(trim(line.depositAccountNumber()));
            rslt.setTrscCcyCd(blankTo(line.currencyCode(), "VND"));
            rslt.setTranAmt(amount(line.transferAmount()));
            rslt.setBankChnlDiv(trimOrNull(line.sameBankFlag()));
            rslt.setAntDivCd(trimOrNull(line.otherBankNetworkCode()));
            rslt.setSenderNm(trim(line.senderName()));
            rslt.setFincMdaOwnerNm(trim(line.receiverName()));
            rslt.setTarAcctRmrk(trim(line.depositAccountMemo()));
            rslt.setWdrlAcctRmrk(trim(line.withdrawalAccountMemo()));
            rslt.setProcRsltDiv(procRsltDiv);
            rslt.setRsCd(rsCd);
            rslt.setRcptBankCntryCd(trimOrNull(line.receiptBankCountryCode()));
            rslt.setRemitCcyCd(trimOrNull(line.remittanceCurrencyCode()));
            rslt.setRcptUseAddr(trim(line.rcptUseAddr()));
            rslt.setFeeBrdnClsCd(trimOrNull(line.feeBrdnClsCd()));
            firmFileRsltRepository.save(rslt);

            // Reflect onto TB_FIRM_FILE_DATA + then TB_PAY_FILE_DATA (legacy U001 chain via ORG_SEQ).
            TbFirmFileData fd = firmDataBySeq.get(seq);
            if (fd != null) {
                fd.setProcRsltDiv(procRsltDiv);
                fd.setRsCd(rsCd);
                firmFileDataRepository.save(fd);

                TbPayFileData pd = payDataBySeq.get(intValue(fd.getOrgSeq()));
                if (pd != null) {
                    pd.setProcRsltDiv(procRsltDiv);
                    pd.setRsCd(rsCd);
                    payFileDataRepository.save(pd);
                }
            } else {
                log.warn("01302 result line seq={} has no TB_FIRM_FILE_DATA row fileNm={}", seq, req.fileNm());
            }
        }

        // 9. Advance MST PROC_STEP 2→3 + trailer aggregates (legacy U003).
        DepositTrailer t = parsed.trailer();
        mst.setProcStep(ProcStep.DEPO_RES_RECV.code());
        mst.setProcStat(ProcStat.SUCC.code());
        mst.setSuccCnt(count(t.succProcessingCnt()));
        mst.setSuccAmt(amount(t.succProcessingAmt()));
        mst.setUnprocCnt(count(t.nonProcessingCnt()));
        mst.setUnprocAmt(amount(t.nonProcessingAmt()));
        mst.setProcDttm(dttm14);
        firmFileMstRepository.save(mst);

        // 10. Sibling gate + final pay-file status (legacy R003/R005 + PAY U002).
        finalizePayFileStatus(tranDt, bizKey, fileSeqNo, dttm14);

        // 11. Audit success.
        audit.setRsCd(FirmBankingResultCd.SUCC);
        audit.setRsMsg(FirmBankingResultCd.MSG_SUCC);
        audit.setRsDttm(dttm14);
        firmBatApiTranRepository.save(audit);

        log.info("01302 execution result processed fileNm={} bizKey={} lines={}",
                req.fileNm(), bizKey, parsed.lines().size());
    }

    /**
     * Finalize {@code TB_PAY_FILE_MST} only when every sibling file of the same
     * {@code FILE_SEQ_NO} has reported (none still at {@code PROC_STEP='0'/PROC_STAT='6'}):
     * {@code 0} all-success / {@code 3} all-fail / {@code 1} partial.
     */
    private void finalizePayFileStatus(String tranDt, String bizKey, String fileSeqNo, String dttm14) {
        List<TbFirmFileMst> siblings = firmFileMstRepository
                .findByTranDtAndBizKeyAndFileSeqNo(tranDt, bizKey, fileSeqNo);

        boolean awaitingSiblings = siblings.stream().anyMatch(f ->
                ProcStep.REQ.code().equals(f.getProcStep()) && ProcStat.REQ.code().equals(f.getProcStat()));
        if (awaitingSiblings) {
            log.info("01302 — deferring pay-file finalize, sibling files still pending fileSeqNo={}", fileSeqNo);
            return;
        }

        long tot = siblings.stream().filter(f ->
                ProcStat.SUCC.code().equals(f.getProcStat()) || ProcStat.FAIL.code().equals(f.getProcStat())).count();
        long succ = siblings.stream().filter(f -> ProcStat.SUCC.code().equals(f.getProcStat())).count();
        long fail = siblings.stream().filter(f -> ProcStat.FAIL.code().equals(f.getProcStat())).count();

        // Legacy partial status is the literal '1' (NOT a WCConst PROC_ST_* — see API2_act.jsp:421-429).
        String stat;
        if (tot == succ) {
            stat = ProcStat.SUCC.code();   // "0" — all success
        } else if (tot == fail) {
            stat = ProcStat.FAIL.code();   // "3" — all fail
        } else {
            stat = "1";                    // partial (mixed) — verbatim legacy value
        }

        payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(tranDt, bizKey, fileSeqNo)
                .ifPresent(pay -> {
                    pay.setProcStat(stat);
                    pay.setProcDttm(dttm14);
                    payFileMstRepository.save(pay);

                    // Single trigger for the HAENGSUNG Oracle dispatch-result
                    // write-back (Step 5). Fires once, only on actual pay-file
                    // finalization (all siblings reported). Deferred to
                    // AFTER_COMMIT by the subscriber, which filters to
                    // HAENGSUNG / ERP batches and is a no-op otherwise — so it
                    // is harmless for every other tenant/module.
                    eventPublisher.publishEvent(
                            new PaymentDispatchResultFinalizedEvent(bizKey, tranDt, fileSeqNo));
                });
    }

    // ------------------------------------------------------------------ helpers

    private String downloadResultFile(String fileNm) {
        String remote = joinPath(sftpProperties.getRemoteDownloadPath(), fileNm);
        String dir = StringUtils.hasText(sftpProperties.getLocalDownloadPath())
                ? sftpProperties.getLocalDownloadPath() : System.getProperty("java.io.tmpdir");
        String local = Path.of(dir, fileNm).toString();
        try {
            sftpClient.download(remote, local);
        } catch (RuntimeException e) {
            throw new BusinessException("WBE_EFS_RESULT_FILE", fileNm + " download failed");
        }
        // Best-effort move to the processed dir (legacy fileMove — failure ignored).
        if (StringUtils.hasText(sftpProperties.getRemoteMovePath())) {
            sftpClient.move(remote, joinPath(sftpProperties.getRemoteMovePath(), fileNm));
        }
        return local;
    }

    private FixedLengthCodec.ParsedFile parseResultFile(String localPath, String fileNm) {
        try {
            byte[] content = Files.readAllBytes(Path.of(localPath));
            if (content.length < 1) {
                throw new BusinessException("WBE_EFS_RESULT_FILE", fileNm + " is empty");
            }
            return fixedLengthCodec.decode(content);
        } catch (IOException e) {
            throw new BusinessException("WBE_EFS_RESULT_FILE", fileNm + " read failed");
        } catch (IllegalArgumentException e) {
            // FixedLengthCodec rejects a malformed file — do NOT silently ignore.
            throw new BusinessException("WBE_EFS_RESULT_FILE", fileNm + " parse failed: " + e.getMessage());
        }
    }

    private Map<Integer, TbFirmFileData> indexFirmDataBySeq(String tranDt, String fileSeqNo, String fileNm) {
        Map<Integer, TbFirmFileData> map = new HashMap<>();
        for (TbFirmFileData fd : firmFileDataRepository
                .findByTranDtAndFileSeqNoAndFirmFileNmOrderBySeqAsc(tranDt, fileSeqNo, fileNm)) {
            map.put(intValue(fd.getSeq()), fd);
        }
        return map;
    }

    private Map<Integer, TbPayFileData> indexPayDataBySeq(String bizKey, String tranDt, String fileSeqNo) {
        Map<Integer, TbPayFileData> map = new HashMap<>();
        for (TbPayFileData pd : payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(bizKey, tranDt, fileSeqNo)) {
            map.put(intValue(pd.getSeq()), pd);
        }
        return map;
    }

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

    private void requireField(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("01302 callback missing required field: " + name);
        }
    }

    private BulkExecutionResultRequest decode(String json) {
        try {
            return objectMapper.readValue(json, BulkExecutionResultRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse 01302 callback payload", e);
        }
    }

    private static String joinPath(String dir, String name) {
        if (!StringUtils.hasText(dir)) {
            return name;
        }
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    private static BigDecimal combineAmount(String itg, String dcm) {
        String i = StringUtils.hasText(itg) ? itg.trim() : "0";
        String d = StringUtils.hasText(dcm) ? dcm.trim() : "0";
        return new BigDecimal(i + "." + d);
    }

    /** Parse a fixed-width numeric/amount field (leading zeros, optional decimal point). */
    private static BigDecimal amount(String raw) {
        String s = trim(raw);
        return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
    }

    private static BigDecimal count(String raw) {
        String s = trim(raw);
        return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
    }

    private static BigDecimal bigDecimalOrZero(String value) {
        return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : BigDecimal.ZERO;
    }

    private static int parseInt(String raw) {
        String s = trim(raw);
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }

    private static int intValue(BigDecimal v) {
        return v == null ? 0 : v.intValue();
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private static String trimOrNull(String v) {
        String t = trim(v);
        return t.isEmpty() ? null : t;
    }

    private static String blankTo(String v, String dflt) {
        String t = trim(v);
        return t.isEmpty() ? dflt : t;
    }
}
