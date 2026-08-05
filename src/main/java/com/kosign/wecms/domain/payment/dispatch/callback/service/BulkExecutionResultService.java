package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.MissingCallbackFieldException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkExecutionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTranId;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 01302 bulk execution result callback handler — refactor of
 * {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp}.
 *
 * <p>Flow ({@link #handle} — no transaction; the writes from {@code TB_FIRM_FILE_RSLT}
 * onward run in one transaction on {@link BulkExecutionResultTxService}, see C-4 below):
 * verify {@code VV} + decrypt
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
 * <h2>C-4 — this class is NOT transactional; the writes live on a separate bean</h2>
 * {@code handle} is a plain orchestrator: envelope guards, key resolve + {@code VV} verify +
 * {@code EV} decrypt, required-field checks, the {@code file_nm} split, the {@code R006} lookup, the
 * {@code REQUIRES_NEW} audit insert, the SFTP download and the parse. Every database <b>write</b> then
 * happens in one transaction owned by {@link BulkExecutionResultTxService#applyResult}.
 *
 * <p><b>Why:</b> legacy {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp} opens its transaction at
 * {@code :291} — after the download ({@code :234}), the remote archive move ({@code :235}), the
 * disconnect ({@code :236}), the empty-file check ({@code :249-254}) and the whole parse loop
 * ({@code :262-290}). This method used to be {@code @Transactional} end-to-end, holding a database
 * connection across an SFTP round trip on a callback thread. It no longer does. The writer is a
 * separate bean because a self-invocation would bypass the proxy and run the apply with no transaction
 * at all — see its class Javadoc.
 *
 * <h2>Fail-safe / no-corruption</h2>
 * A file not at {@code step2/stat6} is a safe no-op (duplicate / late / unknown callback — already
 * processed). A bad envelope, a missing/empty download, or a malformed result file <b>throws before the
 * writer is ever called</b>, so no write is attempted and {@code TB_FIRM_FILE_MST} stays at
 * {@code PROC_STEP='2'} (replayable). A failure <i>inside</i> the writer rolls that transaction back to
 * the same state. Malformed records are never silently ignored, and {@code PROC_STEP 2→3} is reached
 * only after every row is parsed.
 *
 * <p><b>C-4 removed no writes from the rollback set.</b> Nothing was ever written inside the
 * transaction ahead of the parse — the only earlier write is the audit row, and that already committed
 * in its own {@code REQUIRES_NEW}. That is what makes the split cheap and the fail-safe guarantee
 * identical to before.
 *
 * <h2>Unmatched callbacks: this leg answers {@code 0000}, the 01301 leg answers {@code G999}</h2>
 * Both legs face the identical question — the state-scoped lookup found nothing, so is this a replay we
 * already handled or a callback we cannot match? {@code BulkReceptionResultService} splits them
 * (replay → {@code 0000}; unknown / ambiguous / unexpected state → {@code G999} so the gateway resends,
 * with the reasoning written out at its {@code handleUnmatchedCallback}); this leg answers {@code 0000}
 * on every branch. <b>The asymmetry is ours, not a legacy divergence</b> — legacy answers {@code G999} +
 * {@code "<file_nm> not found."} here ({@code FILE_DEPOSIT_TRANSFER_API2_act.jsp:163-169}).
 *
 * <p>It is deliberate, not an oversight. This leg's miss is dominated by the routine replay case: once a
 * file reaches {@code PROC_STEP='3'}, <i>every</i> later callback misses the {@code '2'}/{@code '6'}
 * lookup, so a blanket {@code G999} would answer a correctly-processed replay and produce a
 * deterministic resend loop with nothing to converge on — legacy tolerated that only because its 60 s
 * poller re-selected work each pass. <b>Treat the two legs as one decision with two answers.</b> The flip
 * is deferred pending §10 q1 (does a non-{@code 0000} callback RC make the gateway resend?), and if the
 * answer is yes the correct change is to port the 01301 split, not to flip the acknowledgement. Full
 * record: {@code docs/audits/van-center-open-questions-verification-2026-08-01.md} §4 and §6.
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
    private final OpsAlertService opsAlertService;
    /** G1 (audit M-7): writes the 01302 audit row in its own REQUIRES_NEW tx so it survives a parse rollback. */
    private final DispatchApiTranAuditWriter apiTranAuditWriter;
    /**
     * C-4: owns the single transaction every write of this leg happens in. A separate bean by
     * necessity — calling an {@code @Transactional} method on {@code this} would bypass the proxy.
     */
    private final BulkExecutionResultTxService resultTxService;

    /**
     * Handle a 01302 execution-result callback.
     *
     * @throws BusinessException     ({@code WBE_EFS_VERIFY_FAILED}) on a bad MAC,
     *                               ({@code WBE_EFS_RESULT_FILE}) on a missing/empty/
     *                               malformed result file
     * @throws IllegalStateException when the API key is not provisioned (B2)
     */
    public EfsEnvelope handle(EfsCallbackEnvelope envelope) {
        // 0. Common header check — legacy FILE_DEPOSIT_TRANSFER_API2_act.jsp:94-99 guards ID/EV/VV
        //    (only three; the 01301 twin guards seven) and answers G001. We answered G002 for a
        //    blank ID and did not check EV/VV at all. Corrected 2026-08-03.
        if (envelope == null) {
            throw baseFieldMissing("<envelope>");
        }
        requireBaseField(envelope.id(), "ID");
        requireBaseField(envelope.ev(), "EV");
        requireBaseField(envelope.vv(), "VV");
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

        // 2. Required-field check — legacy API2_act.jsp:130-136 tests these five in ONE combined
        //    guard and answers G002 (CD_DETAIL_NONE_INPUT_DATA) for all of them. Unlike the 01301
        //    leg there is no G001/G002 split here.
        //
        //    S-9: legacy OMITS data_cnt from this guard (01301 requires it, 01302 does not), then
        //    writes the unchecked value straight into TB_FIRM_BAT_API_TRAN.BAT_CNT. We require it
        //    below — a deliberate, harmless tightening: a blank BAT_CNT is never useful, and no
        //    conforming gateway omits the field. Do not "restore parity" by dropping this check.
        requireDetailField(req.bankCd(), "bank_cd");        // legacy :130 → G002
        requireDetailField(req.prtnId(), "prtn_id");        // legacy :130 → G002
        requireDetailField(req.fileNm(), "file_nm");        // legacy :130 → G002
        requireDetailField(req.dataAmt(), "data_amt");      // legacy :130 → G002
        requireDetailField(req.dataDcmAmt(), "data_dcm_amt"); // legacy :130 → G002

        // 3. Parse file_nm = {prefix}_{vanOrgCd}_{type}_{fileSeq}_{tranDt}.
        String[] parts = req.fileNm().split("_");
        if (parts.length < 5) {
            // Ops alert (legacy FILE_DEPOSIT_TRANSFER_BATCH2:280 "이체실행지시 Batch2 API수신 오류 발생").
            opsAlertService.notify(vanOrgCd,
                    "이체실행지시 Batch2 API수신 오류 발생"
                            + "\nFIRM_FILE_NM : " + req.fileNm()
                            + "\nAPI RM : malformed file_nm");
            // A2: acknowledge with the general error RC (validation failure, before any DB work).
            //
            // DO NOT PORT LEGACY HERE. API2_act.jsp:142-148 does file_nm.split("_") then indexes [4]
            // with no length guard, OUTSIDE any try (the verify try closes at :119, the next opens at
            // :173). A short name therefore raises an uncaught ArrayIndexOutOfBoundsException and the
            // gateway receives a container HTTP 500 with NO envelope at all — not a parseable RC.
            // G999 is strictly better and is kept deliberately. Recorded in the do-not-port list:
            // docs/audits/legacy-batch-boundary-rule-sweep-2026-07-31.md §1.
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
            // The lookup collapses four conditions into one miss: (a) the file already advanced to
            // PROC_STEP='3' — a genuine replay; (b) it is still at '0'/'1' — the 00302 was never
            // confirmed; (c) NO ROW EXISTS AT ALL for this (tranDt, fileNm) — a name or date mismatch;
            // (d) it is terminal at PROC_STAT='3'. Cases (a)/(b)/(d) all leave a row behind, and
            // StuckPayFileMonitorService sweeps '1'/'6' (BULK_EXECUTION_UNCONFIRMED) and '2'/'6'
            // (BULK_EXECUTION_RESULT_NOT_RECEIVED). Case (c) leaves NO state anywhere — nothing to
            // sweep, no audit row possible (TB_FIRM_BAT_API_TRAN.BIZ_KEY is a NOT NULL PK column and
            // an unmatched callback resolves no tenant) — so without the escalation below it was the
            // only unmatched-callback condition in the dispatch tree with no operator-visible signal.
            // Matches the 01301 twin, BulkReceptionResultService.handleUnmatchedCallback:560-582.
            //
            // The probe MUST reuse findForExecutionResult's prefix-insensitive match. FIRM_FILE_NM
            // stores the sent S_… name while the callback carries the bank's R_… name, so an exact
            // findByTranDtAndFirmFileNm (what the 01301 leg can safely use — 01301 quotes the sent
            // name back) returns 0 rows for EVERY 01302 callback, replays included, and would fire
            // the alert on the routine path.
            boolean anyRowForFile = !firmFileMstRepository
                    .findAnyStateForExecutionResult(tranDt, req.fileNm(), parts[1], parts[3]).isEmpty();
            if (!anyRowForFile) {
                log.error("01302 callback UNMATCHED — no TB_FIRM_FILE_MST row for tranDt={} fileNm={} in "
                                + "any state; the execution result cannot be applied and leaves no state to "
                                + "monitor. Answering RC={} (see below). vanOrgCd={} tno={} (no audit row is "
                                + "possible: TB_FIRM_BAT_API_TRAN.BIZ_KEY is a NOT NULL PK column and an "
                                + "unmatched callback resolves no tenant)",
                        tranDt, req.fileNm(), FirmBankingResultCd.SUCC, vanOrgCd, envelope.tno());
                // OpsAlertService is @Async and a no-op unless wecms.dispatch.alert.enabled=true.
                // Field values only — never the decrypted payload (it carries payee names and amounts).
                if (StringUtils.hasText(vanOrgCd)) {
                    opsAlertService.notify(vanOrgCd,
                            "실행결과 수신요청 API — unmatched callback (no TB_FIRM_FILE_MST row)."
                                    + "\nTRAN_DT (from file name) : " + tranDt
                                    + "\nFIRM_FILE_NM : " + req.fileNm()
                                    + "\nID : " + vanOrgCd
                                    + "\nTNO : " + envelope.tno()
                                    + "\nAPI RC : " + FirmBankingResultCd.SUCC
                                    + "\nThe result was NOT applied and no row exists to reconcile against. "
                                    + "Establish which file the bank means before any manual action.");
                }
            } else {
                log.info("01302 callback ignored — no file at PROC_STEP=2/PROC_STAT=6 but a row exists for "
                                + "this name (already processed, unconfirmed or terminal) vanOrgCd={} fileNm={}",
                        vanOrgCd, req.fileNm());
            }
            // THE ACKNOWLEDGEMENT IS DELIBERATELY 0000 ON BOTH BRANCHES — A LEGACY BEHAVIOUR WE DECLINE.
            //
            // What legacy does (verified 2026-08-03, not inferred): IDO.TB_FIRM_FILE_MST_R006.xml
            // constrains "AND PROC_STEP = :PROC_STEP AND PROC_STAT = :PROC_STAT", and API2_act.jsp:144-146
            // binds those to the literals '2' and '6'. So a file that has advanced to PROC_STEP='3' is
            // NOT excluded from legacy's view by some quirk — legacy runs the identical filtered lookup,
            // gets getResultCount < 1, and answers G999 + "<file_nm> not found." (:163-169). Legacy
            // answers G999 to a completed-file REPLAY exactly as it does to a genuinely unknown file.
            //
            // Why we still decline it — the poller, not the lookup. Legacy could afford that resend loop
            // because FILE_DEPOSIT_TRANSFER_BATCH2 re-selected work every 60 s: a callback wedged in
            // G999 was never the only way for a file to move forward. We removed that poller (K9), so
            // copying G999 here would give us the loop with nothing to converge on — the gateway resends
            // forever against a file that is already correctly processed, and no other actor advances it.
            // Same class as the transient-00302 retry we already declined.
            //
            // An earlier version of this comment argued from the lookup ("legacy never sees a step-3
            // row"). That was wrong and is corrected above: legacy does see it, via getResultCount < 1.
            // The poller is the argument.
            //
            // Recorded in the do-not-port list: docs/audits/legacy-batch-boundary-rule-sweep-2026-07-31.md §1.
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

        // 8. EVERY WRITE, IN ONE TRANSACTION ON ANOTHER BEAN (C-4).
        //
        // Legacy's transaction covers exactly this span — API2_act.jsp:291 to the end: the
        // TB_FIRM_FILE_RSLT per-line loop (:293-331, where one failed insert rolls back every prior
        // line — semantics we match), the TB_FIRM_FILE_DATA / TB_PAY_FILE_DATA reflection (:334-348,
        // :375-387), the MST 2→3 advance with the trailer aggregates (:351-372), the sibling gate and
        // pay-file finalize (:389-446), and the audit RS update (:451-462).
        //
        // Identifiers cross this boundary, never entities. `mst` was loaded WITHOUT a transaction, so
        // it is detached; save()-ing it inside the writer would be a merge that writes every column and
        // blind-overwrites a concurrent change. The writer re-reads by PK instead.
        //
        // The writer's FIRST statement is an atomic '2'→'3' claim. Moving the R006 lookup above out of
        // the transaction matches legacy's boundary but would also import legacy's TOCTOU race (its
        // R006 is likewise outside, at :144-153, with no claim of any kind); the claim declines that,
        // consistently with H-4 on the reception leg and G2 on the real-time leg.
        boolean applied = resultTxService.applyResult(new BulkExecutionResultCommand(
                new TbFirmFileMstId(mst.getTranDt(), bizKey, fileSeqNo, mst.getFirmFileNm()),
                new TbFirmBatApiTranId(audit.getTranDt(), audit.getBizKey(), audit.getTranSeq(),
                        audit.getApiTranTp(), audit.getOutBankCd(), audit.getBankPrtnId()),
                req.fileNm(), tranDt, parsed, dttm14));

        if (!applied) {
            // Lost the claim to a concurrent callback. Acknowledge 0000 for the same reason the
            // lookup-miss branch above does: the result IS being applied, by the winner, and a
            // non-0000 here would only produce a resend against a file already being finalized.
            log.warn("01302 callback did not claim fileNm={} bizKey={} — another callback is applying "
                    + "this result; answering RC={}", req.fileNm(), bizKey, FirmBankingResultCd.SUCC);
        }
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
        // Archive move to the processed dir — legacy fileMove (API2_act.jsp:235).
        //
        // NOT best-effort, and the comment that used to say so was wrong. There is no catch here, so a
        // move failure propagates and aborts the callback — which is exactly what legacy does: its
        // fileMove sits inside the same try as the download (:226-247) and any exception becomes
        // `JexBIZException(CD_ETC_ERR, "<file_nm> DOWNLOAD FAIL !!!")` at :246. The behaviour was
        // always right; only the comment claimed otherwise.
        //
        // Consequence worth knowing: the file is downloaded locally before this runs, so a failed move
        // leaves a local copy and an un-archived remote file, and the retry re-downloads. Legacy has
        // the identical window.
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

    /** Transport header → {@code G001} (legacy {@code API2_act.jsp:94-99}). */
    private void requireBaseField(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw baseFieldMissing(name);
        }
    }

    /** Body field → {@code G002} (legacy {@code API2_act.jsp:130-136}, one combined guard). */
    private void requireDetailField(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new MissingCallbackFieldException(
                    FirmBankingResultCd.DETAIL_NONE_INPUT_DATA,
                    FirmBankingResultCd.MSG_DETAIL_NONE_INPUT_DATA,
                    "01302 callback missing required field: " + name);
        }
    }

    private MissingCallbackFieldException baseFieldMissing(String name) {
        return new MissingCallbackFieldException(
                FirmBankingResultCd.BASE_NONE_INPUT_DATA,
                FirmBankingResultCd.MSG_BASE_NONE_INPUT_DATA,
                "01302 callback missing required field: " + name);
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

    private static BigDecimal bigDecimalOrZero(String value) {
        return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : BigDecimal.ZERO;
    }
}
