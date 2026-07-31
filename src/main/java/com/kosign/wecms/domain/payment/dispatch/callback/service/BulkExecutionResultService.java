package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkExecutionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
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
 * <p><b>Audit-row durability (G1 / audit M-7):</b> legacy commits the 01302 audit row
 * outside the parse transaction so it survives a parse failure. The audit insert here is
 * therefore routed through {@link DispatchApiTranAuditWriter} in a {@code REQUIRES_NEW}
 * transaction that commits immediately; a later download/parse failure rolls back the
 * result-file work (MST stays at {@code PROC_STEP='2'}, replayable) but the audit trail
 * persists — legacy parity. The success RS update runs only on the happy path, in this
 * handler's own transaction (merge of the now-detached row).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkExecutionResultService {

    private static final DateTimeFormatter DTTM14_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TRAN_TM_FMT = DateTimeFormatter.ofPattern("HHmmss");
    /** E — 01302 audit row TRAN_DT uses the callback processing date (legacy API2_act.jsp:85 `dt`). */
    private static final DateTimeFormatter DATE8_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
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
    /** G1 (audit M-7): writes the 01302 audit row in its own REQUIRES_NEW tx so it survives a parse rollback. */
    private final DispatchApiTranAuditWriter apiTranAuditWriter;

    /**
     * Handle a 01302 execution-result callback.
     *
     * @throws BusinessException     ({@code WBE_EFS_VERIFY_FAILED}) on a bad MAC,
     *                               ({@code WBE_EFS_RESULT_FILE}) on a missing/empty/
     *                               malformed result file
     * @throws IllegalStateException when the API key is not provisioned (B2)
     */
    @Transactional
    public EfsEnvelope handle(EfsCallbackEnvelope envelope) {
        if (envelope == null || !StringUtils.hasText(envelope.id())) {
            throw new IllegalArgumentException("callback envelope ID is required");
        }
        String vanOrgCd = envelope.id();

        // 1. Resolve key + verify VV + decrypt EV.
        String apiKey = vanApiKeyService.getApiKey(vanOrgCd);
        if (!efsCryptoService.verifyVv(envelope.ev(), envelope.vv(), apiKey)) {
            log.warn("01302 callback VV verification failed vanOrgCd={} tno={}", vanOrgCd, envelope.tno());
            // A2 (audit C1): return the EFS G003 acknowledgement (RC/RM only — 01302 echoes the
            // request EV/VV verbatim, legacy API2_act.jsp copyDomain), not an internal error body.
            return ack(envelope, FirmBankingResultCd.ENC_VRFY_ERR, FirmBankingResultCd.MSG_ENC_VRFY_ERR);
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
            // A2: acknowledge with the general error RC (validation failure, before any DB work).
            return ack(envelope, FirmBankingResultCd.ETC_ERR, FirmBankingResultCd.MSG_ETC_ERR);
        }
        String tranDt = parts[4];

        // 4. Look up the execution-requested file (legacy R006): PROC_STEP='2'/PROC_STAT='6'.
        //    C3 (audit M-2): match by substring(FIRM_FILE_NM,3) + VAN_ORG_CD + FILE_SEQ — the bank's
        //    result file carries a different leading 2-char prefix than the sent S_ name, so an
        //    exact-name match would drop the result and strand the file at PROC_STEP='2'.
        //    parts = {prefix, vanOrgCd, type, fileSeq, tranDt}.
        TbFirmFileMst mst = firmFileMstRepository
                .findForExecutionResult(tranDt, req.fileNm(), parts[1], parts[3],
                        ProcStep.DEPO_REQ.code(), ProcStat.REQ.code())
                .orElse(null);
        if (mst == null) {
            log.info("01302 callback ignored — no file at PROC_STEP=2/PROC_STAT=6 (already processed or unknown) "
                    + "vanOrgCd={} fileNm={}", vanOrgCd, req.fileNm());
            // Idempotent replay / already-processed → acknowledge success so the gateway stops resending.
            return ack(envelope, FirmBankingResultCd.SUCC, FirmBankingResultCd.MSG_SUCC);
        }
        String bizKey = mst.getBizKey();
        String fileSeqNo = mst.getFileSeqNo();

        LocalDateTime now = LocalDateTime.now(ICT);
        String dttm14 = now.format(DTTM14_FMT);
        String tranTm = now.format(TRAN_TM_FMT);
        // E — legacy parity (API2_act.jsp:85,182): the 01302 audit row's TRAN_DT is the callback
        // PROCESSING date (today), NOT the result-filename date (parts[4]). The filename date stays
        // the key for the file lookup + the firm/pay-data indexes above (legacy uses it there).
        String procDt = now.format(DATE8_FMT);

        // 5. Audit the 01302 leg.
        BigDecimal batAmt = combineAmount(req.dataAmt(), req.dataDcmAmt());
        TbFirmBatApiTran audit = writeApiTran(mst, procDt, tranTm, ApiTranTp.BULK_EXECUTION_RESULT.code(),
                bigDecimalOrZero(req.dataCnt()), batAmt, envelope.tno());

        // 6. SFTP download the bank result file (then best-effort move to the processed dir).
        String localPath = downloadResultFile(req.fileNm());

        // 7. Read + parse (malformed/empty → throw → rollback, MST stays at step 2).
        FixedLengthCodec.ParsedFile parsed = parseResultFile(localPath, req.fileNm());

        // 8. Insert TB_FIRM_FILE_RSLT per D line + reflect onto TB_FIRM_FILE_DATA.
        // H-1: keyed on FILE_SEQ + prefix-stripped name (legacy U001), NOT the exact callback name —
        // req.fileNm() is the bank's R_ result name; the stored rows carry the sent S_ name.
        Map<Integer, TbFirmFileData> firmDataBySeq =
                indexFirmDataBySeq(tranDt, mst.getFileSeq(), req.fileNm());
        Map<Integer, TbPayFileData> payDataBySeq = indexPayDataBySeq(bizKey, tranDt, fileSeqNo);

        for (DepositData line : parsed.lines()) {
            int seq = parseInt(line.seq());
            String procRsltDiv = blankTo(line.processingResult(), "N");
            String rsCd = trim(line.responseCode());
            TbFirmFileData fd = firmDataBySeq.get(seq);

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
            // Town name (RCPT_USE_ADDR_SI): not present in the result file — carry from the matching
            // TB_FIRM_FILE_DATA row (audit M-11/E1). Null when the line has no firm-data match.
            rslt.setRcptUseAddrSi(fd != null ? fd.getRcptUseAddrSi() : null);
            firmFileRsltRepository.save(rslt);

            // Reflect onto TB_FIRM_FILE_DATA + then TB_PAY_FILE_DATA (legacy U001 chain via ORG_SEQ).
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
        // A2: EFS success acknowledgement. 01302 is RC/RM-only — the request EV/VV/ID/TNO/EM/VM are
        // echoed verbatim (legacy FILE_DEPOSIT_TRANSFER_API2_act.jsp copyDomain + setResult), NOT
        // re-encrypted (the load-bearing 01301↔01302 asymmetry).
        return ack(envelope, FirmBankingResultCd.SUCC, FirmBankingResultCd.MSG_SUCC);
    }

    /**
     * Build the 01302 EFS acknowledgement envelope (A2). Per legacy {@code API2_act.jsp}, 01302
     * echoes the inbound {@code ID/RQ_DTIME/TNO/EV/VV/EM/VM} verbatim and sets only {@code RC}/{@code RM}
     * — it does NOT rebuild {@code EV}/{@code VV} (unlike 01301).
     */
    private EfsEnvelope ack(EfsCallbackEnvelope in, String rc, String rm) {
        return new EfsEnvelope(in.id(), in.rqDtime(), null, in.tno(),
                in.ev(), in.vv(), in.em(), in.vm(), rc, rm);
    }

    /**
     * Finalize {@code TB_PAY_FILE_MST} only when every sibling file of the same
     * {@code FILE_SEQ_NO} has reported (none still at {@code PROC_STEP='0'/PROC_STAT='6'}):
     * {@code 0} all-success / {@code 3} all-fail / {@code 1} partial.
     */
    /** {@code PROC_STEP/PROC_STAT} of each sibling, for the deferral logs. */
    private static String siblingStates(List<TbFirmFileMst> siblings) {
        return siblings.stream()
                .map(f -> f.getFirmFileNm() + "=" + f.getProcStep() + "/" + f.getProcStat())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void finalizePayFileStatus(String tranDt, String bizKey, String fileSeqNo, String dttm14) {
        List<TbFirmFileMst> siblings = firmFileMstRepository
                .findByTranDtAndBizKeyAndFileSeqNo(tranDt, bizKey, fileSeqNo);

        // ── Guard 1 (Option A) — defer while ANY sibling is still non-terminal ────────────────────
        // Legacy's barrier (IDO.TB_FIRM_FILE_MST_R003.xml:26-30, called at API2_act.jsp:389-405) tests
        // only PROC_STEP='0' AND PROC_STAT='6' — reception-pending. A sibling mid-EXECUTION ('1'/'6' or
        // '2'/'6') does not block it, and R005 then counts only terminal rows into TOT, so an
        // in-flight sibling is invisible to the tot==succ ratio and the parent finalises without it.
        //
        // DELIBERATE DIVERGENCE FROM LEGACY (approved 2026-07-31, same class as the ANT_DIV_CD
        // canonicalisation): defer on PROC_STAT='6' at ANY step. A file that has not reported cannot
        // be counted as success or failure, so the only correct action is to wait.
        boolean awaitingSiblings = siblings.stream()
                .anyMatch(f -> ProcStat.REQ.code().equals(f.getProcStat()));
        if (awaitingSiblings) {
            log.info("01302 — deferring pay-file finalize, sibling files still pending fileSeqNo={} states={}",
                    fileSeqNo, siblingStates(siblings));
            return;
        }

        long tot = siblings.stream().filter(f ->
                ProcStat.SUCC.code().equals(f.getProcStat()) || ProcStat.FAIL.code().equals(f.getProcStat())).count();
        long succ = siblings.stream().filter(f -> ProcStat.SUCC.code().equals(f.getProcStat())).count();
        long fail = siblings.stream().filter(f -> ProcStat.FAIL.code().equals(f.getProcStat())).count();

        // ── Guard 2 — never finalise on an empty terminal set ─────────────────────────────────────
        // THIS is the arithmetic that produced the two observed PROD false-"all success" rows, and it
        // needs no split batch at all: for a SINGLE file stranded at '2'/'6', tot=0 and succ=0, so
        // `tot == succ` is 0 == 0 — TRUE — and the parent is finalised to '0' (ALL SUCCESS) while its
        // only file never reported. An empty count satisfies the equality.
        //
        // Guard 1 already prevents reaching this line (a '6' sibling defers first), so this is
        // belt-and-braces — but the equality is the actual defect, it is one comparison, and any future
        // change to Guard 1's predicate would silently re-expose it. Keep both.
        if (tot == 0) {
            log.warn("01302 — refusing to finalize pay-file with NO terminal sibling fileSeqNo={} "
                            + "bizKey={} siblings={} states={} — parent left at PROC_STAT='6' "
                            + "(finalising here would report '0'/all-success for a file that never reported)",
                    fileSeqNo, bizKey, siblings.size(), siblingStates(siblings));
            return;
        }

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

    /**
     * Index the sent file's detail lines by line {@code SEQ}, matching the way legacy
     * {@code TB_FIRM_FILE_DATA_U001} joins — prefix-stripped {@code FIRM_FILE_NM} + {@code FILE_SEQ}.
     *
     * <p><b>H-1:</b> {@code fileNm} here is the bank's {@code R_…} result name, while the stored rows
     * carry the sent {@code S_…} name, so an exact-name lookup returns nothing. See
     * {@link TbFirmFileDataRepository#findForExecutionResult}.
     */
    private Map<Integer, TbFirmFileData> indexFirmDataBySeq(String tranDt, String fileSeq, String fileNm) {
        Map<Integer, TbFirmFileData> map = new HashMap<>();
        for (TbFirmFileData fd : firmFileDataRepository
                .findForExecutionResult(tranDt, fileSeq, fileNm)) {
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
                                          String apiTranTp, BigDecimal batCnt, BigDecimal batAmt,
                                          String callbackTno) {
        TbFirmBatApiTran row = new TbFirmBatApiTran();
        row.setTranDt(tranDt);
        row.setBizKey(file.getBizKey());
        // A1 (audit X-2 / D6): the 01302 audit row is keyed by the VAN callback's TNO (legacy
        // FILE_DEPOSIT_TRANSFER_API2_act.jsp:185 — TRAN_SEQ = input.getString("TNO")), so it
        // reconciles to the gateway transaction id. Fall back to a generated seq only if the
        // callback omitted the TNO (defensive — legacy relied on it always being present).
        row.setTranSeq(StringUtils.hasText(callbackTno)
                ? callbackTno : dispatchKeyGenerator.nextTranSeq(file.getBizKey(), tranDt));
        row.setApiTranTp(apiTranTp);
        row.setOutBankCd(file.getOutBankCd());
        row.setBankPrtnId(file.getBankPrtnId());
        row.setTranTm(tranTm);
        row.setFirmFileNm(file.getFirmFileNm());
        row.setBatCnt(batCnt == null ? BigDecimal.ZERO : batCnt);
        row.setBatAmt(batAmt == null ? BigDecimal.ZERO : batAmt);
        // G1 (audit M-7): commit the audit row in its own transaction so a later parse/download
        // failure (which rolls back handle()) does not erase it — legacy API2 commits it before
        // the parse tx. The success RS_CD/RS_MSG update at step 11 stays in the caller's tx.
        return apiTranAuditWriter.saveInNewTransaction(row);
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
