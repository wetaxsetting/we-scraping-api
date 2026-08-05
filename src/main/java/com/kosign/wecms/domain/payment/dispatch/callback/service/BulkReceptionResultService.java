package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.MissingCallbackFieldException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkReceptionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
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
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
 * <p>Flow ({@link #handle}, one transaction up to the barrier — see the transaction note below):
 * <ol>
 *   <li>resolve the per-VAN key by {@code envelope.id()}, verify {@code VV},
 *       decrypt {@code EV} → {@link BulkReceptionResultRequest};</li>
 *   <li>validate the individual required fields (legacy step 3);</li>
 *   <li>look up the file at {@code (TRAN_DT=today, FIRM_FILE_NM, PROC_STEP='0',
 *       PROC_STAT='6')} (legacy {@code R004}). Nothing in that state is <b>not</b> a
 *       single outcome — {@link #handleUnmatchedCallback} distinguishes a genuine
 *       replay (ack success) from an unknown file or an unexpected state (retryable
 *       error, so the gateway resends);</li>
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
 *       00302 is issued for each reception-success sibling — group issue, no sweep.</li>
 * </ol>
 *
 * <h2>Transaction boundaries — where legacy's per-row commit went</h2>
 * The 00302 fan-out does <b>not</b> run inside {@link #handle}'s transaction. It is registered on
 * {@code afterCommit} and each sibling is then issued in its own transaction by
 * {@link BulkExecutionInlineTxService#issueOne}. Three properties depend on that, all of which legacy
 * got for free from BATCH2 being a separate process reading committed rows and committing per row
 * ({@code FILE_DEPOSIT_TRANSFER_BATCH2}: {@code beginTransaction:238}, {@code commit:260-263} success
 * / {@code :317-320} error):
 * <ul>
 *   <li><b>Durability per sibling.</b> Each file's transition commits the moment its result is known,
 *       instead of all N transitions riding on one commit at the end of the callback.</li>
 *   <li><b>Failure isolation.</b> A throw while issuing sibling 3 cannot undo siblings 1–2 — including
 *       siblings whose 00302 already reached the bank. In one transaction it could, and did.</li>
 *   <li><b>No N HTTP calls with a DB connection held.</b> The reception work is committed and the
 *       connection released before the first gateway call.</li>
 * </ul>
 * {@code afterCommit} rather than a per-sibling {@code REQUIRES_NEW} called from inside {@code handle}:
 * a nested transaction cannot see this callback's own uncommitted {@code PROC_STEP 0→1} advance, so it
 * would re-read the file as {@code '0'/'6'} and skip it. Committing first is also legacy's actual
 * ordering.
 *
 * <h3>Known residual: {@code afterCommit} is not durable</h3>
 * The hook is an in-JVM callback, not a persisted outbox. If the process dies between the commit and the
 * hook running, the reception advance is durable but the 00302 was never sent, so every ready sibling
 * sits at {@code PROC_STEP='1' / PROC_STAT='6'} with <b>no {@code TB_FIRM_BAT_API_TRAN} 00302 row</b> —
 * and nothing re-drives it:
 * <ul>
 *   <li>there is no recovery sweep (K9 Option A, "Recovery Scheduler", was offered to Mr. Kang on
 *       2026-07-29 and <b>declined</b> — do not add one here or elsewhere without reopening K9);</li>
 *   <li>a gateway retry of the 01301 does not help either. The file is now past {@code PROC_STEP='0'},
 *       so {@code R004} misses and {@link #handleUnmatchedCallback} classifies the retry as a genuine
 *       replay and answers {@code RC=0000} — correctly, by its own rule: the reception <i>was</i>
 *       processed. It has no way to know the fan-out that should have followed did not happen.</li>
 * </ul>
 *
 * <p>This is the accepted trade, not an oversight: the alternative — re-driving on the 01301 retry —
 * risks sending 00302 twice for a file whose first instruction may already have reached the bank, and
 * duplicate execution of a bulk payment is a far worse failure than a stranded one. Stranding is
 * visible and manually recoverable; a double transfer is neither.
 *
 * <p><b>Detection</b> is {@code StuckPayFileMonitorService}'s firm-file sweep, whose selection
 * ({@link com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository#findStrandedFirmFiles})
 * is exactly this state — {@code PROC_STEP='1'/PROC_STAT='6'} older than the age threshold — and which
 * classifies it {@code BULK_EXECUTION_NOT_ISSUED} (no 00302 row) as distinct from
 * {@code BULK_EXECUTION_UNCONFIRMED} (a 00302 row exists but the bank never confirmed). The two need
 * opposite manual actions, and this residual produces the first.
 *
 * <p><b>Idempotency.</b> The {@code 01301 → 00302} step never double-executes:
 * (a) the {@code PROC_STEP='0'} lookup means a duplicate callback finds no file to advance, and
 * (b) before each 00302 the {@code TB_FIRM_BAT_API_TRAN} ledger is probed
 * ({@link TbFirmBatApiTranRepository#existsByBizKeyAndFirmFileNmAndApiTranTp}) — a file that
 * already has a 00302 row is skipped. That probe only works if the marker is durable, so the 00302
 * audit row is written through {@link DispatchApiTranAuditWriter#saveInNewTransaction} — a committed
 * transaction of its own — <b>before</b> the gateway call. Written in the caller's transaction instead
 * (as it was), the marker rolled back with any later failure and a gateway retry re-sent 00302 to the
 * bank.
 *
 * <p>The probe carries <b>no date predicate</b> (FIX D). It used to key on the callback-clock
 * {@code TRAN_DT}, which made the guard blind across an ICT midnight — the very rollover this class's
 * FIX A already had to fix on the lookup leg. The date this class passes to
 * {@link #scheduleExecutionFanOut} is still the callback clock, and still correct: it dates the audit
 * row, it no longer keys the guard.
 *
 * <h2>Envelope RC vs persisted result code — two different questions (D-4)</h2>
 *
 * <p>These are <b>not</b> the same field and must not carry the same value. Conflating them was a
 * real defect, corrected 2026-07-31:
 * <ul>
 *   <li>the <b>envelope {@code RC}</b> answers <i>"did you accept my message?"</i> — a transport-level
 *       acknowledgement to the gateway;</li>
 *   <li>{@code TB_FIRM_FILE_MST.REG_RSLT_CD} and {@code TB_FIRM_BAT_API_TRAN.RS_CD} answer
 *       <i>"what happened to the file?"</i> — the business outcome, legacy-parity, unchanged.</li>
 * </ul>
 *
 * <p>Two branches used to answer the second question in the first field. The sibling fail-stop returned
 * {@code G998} and the count/amount mismatch returned {@code 9999}, on paths where this handler had
 * <b>durably committed</b> the audit row and the file transition. The envelope was therefore telling the
 * gateway "I did not accept this notification" about a notification that had been fully received,
 * reconciled and acted upon. Both now return {@code RC=0000}; both still persist {@code G998} /
 * {@code 9999} exactly as before.
 *
 * <p><b>Why this is the safe default rather than a guess in the other direction.</b> REST API guide
 * v1.2.6 does not enumerate the {@code RC} values a partner may return on a Return-URL callback, does not
 * state what the gateway does with a non-{@code 0000}, and contains no retry semantics at all (zero
 * occurrences of 재시도 / 재전송 in 44 pages; §3.8's flow diagram draws the callback response as a plain
 * return with no retry arrow). So the consequence of a non-{@code 0000} is <b>unknown</b>, and both
 * possibilities are bad: if it triggers a resend, the duplicate finds the file past {@code PROC_STEP='0'}
 * and falls into {@link #handleUnmatchedCallback}, potentially looping; if it is terminal, we have
 * recorded a falsehood in the counterparty's system. {@code 0000} makes our behaviour independent of the
 * unknown, and it is what legacy answered on every non-throwing path. Note also that {@code 9999} is not
 * a vendor code at all — the guide's [참조 2] appendix publishes only
 * {@code 0000}/{@code G001}/{@code G002}/{@code G997}/{@code G998}/{@code G999} — and the appendix gives
 * {@code G998} a different meaning from ours ("An error occurred during banking communication.").
 *
 * <p><b>What deliberately still answers non-{@code 0000}</b> — the cases where the callback genuinely
 * was <i>not</i> processed, so a retryable error is the honest answer and the C-2 reasoning stands:
 * <ul>
 *   <li>{@link #handleUnmatchedCallback}'s unknown-file, ambiguous-name and unexpected-state branches
 *       ({@code G999}). Nothing is recorded there — for the first two, no audit row is even
 *       <i>possible</i>, since {@code TB_FIRM_BAT_API_TRAN.BIZ_KEY} is a NOT NULL PK column and no
 *       tenant resolves. A resend is exactly what we want.</li>
 *   <li>The escaped-exception path in {@code CallbackAck} ({@code G002}/{@code G004}/{@code G999}). The
 *       transaction has rolled back; the file is in its prior state and the callback is replayable.</li>
 * </ul>
 *
 * <p><b>Do not "simplify" this by unifying the two.</b> If a future change makes a branch return
 * non-{@code 0000}, the test is not "did something go wrong" but "is this callback still unprocessed and
 * safe to replay". Settling it properly needs B5 (a real captured gateway sample) — see
 * {@code docs/audits/efs-guide-1.2.6-open-questions-closure.md} §6.
 *
 * <h2>The 01302 leg answers this same question differently — read both together</h2>
 * {@code BulkExecutionResultService} faces the identical question (its state-scoped lookup found
 * nothing: replay, or unmatchable?) and answers <b>{@code 0000} on every branch</b>, where this leg
 * answers {@code G999} for unknown / ambiguous / unexpected-state. <b>That asymmetry is ours, not a
 * legacy divergence</b>, and it is deliberate rather than an oversight: 01302's miss is dominated by the
 * routine replay case (once a file reaches {@code PROC_STEP='3'} every later callback misses its
 * {@code '2'}/{@code '6'} lookup), so the C-2 reasoning above — <i>"a retrying callback is recoverable
 * the moment the ambiguity is resolved, whereas a wrongly-acked one is gone"</i> — would there buy an
 * unbounded resend loop with nothing to converge on. Legacy tolerated exactly that only because its 60 s
 * poller re-selected work each pass. <b>Treat the two legs as one decision with two answers</b>; the
 * split that would make them one rule is recorded, with the deferral and the §10 q1 trigger, in
 * {@code docs/audits/van-center-open-questions-verification-2026-08-01.md} §4 and §6.
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

    /** FIX A — guards the {@code FIRM_FILE_NM} date token; see {@link #tranDtFromFileNm}. */
    private static final java.util.regex.Pattern DT8_DIGITS = java.util.regex.Pattern.compile("\\d{8}");

    /**
     * Legacy reception-success token compared in {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:247}.
     *
     * <p><b>TODO(B5) — this value has NO documentary basis.</b> Verified 2026-07-31 against REST API
     * guide v1.2.6 in full: {@code BT00}, {@code BT01} and {@code C999} each appear <b>zero times</b> —
     * not in §5.11, not in the [참조 2] 응답코드 appendix (which publishes only {@code 0000},
     * {@code G001}, {@code G002}, {@code G997}, {@code G998}, {@code G999}), not anywhere in the
     * document. §5.11 marks {@code result_cd String(4)} <i>Mondatory</i> and names it 접수 결과 코드
     * ("reception result code") but never enumerates its values.
     *
     * <p>So the constant that gates the entire bulk flow — reception success vs. fail, and therefore
     * whether 00302 is ever issued — is a literal observed in legacy
     * {@code FILE_DEPOSIT_TRANSFER_BATCH2}/{@code API1_act.jsp} and nothing more. It is almost certainly
     * right (it has driven production for years), but it is unverifiable from the vendor specification
     * and the <i>rest</i> of the value space is entirely unknown: we cannot distinguish a
     * "reception failed" {@code result_cd} from an unrecognised success token.
     *
     * <p>Closing this needs the full {@code result_cd} set from Mr. Kang / VAN Center (question 2 in
     * {@code docs/audits/efs-guide-1.2.6-open-questions-closure.md} §10). Until then, treat any
     * {@code result_cd != BT00} as failure — which is what the reconciliation below does, and which is
     * the safe direction: it withholds 00302 rather than issuing one on an unrecognised code.
     */
    private static final String RECEPTION_SUCCESS_CD = "BT00";

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;
    private final TbFirmFileMstRepository firmFileMstRepository;
    private final TbFirmBatApiTranRepository firmBatApiTranRepository;
    private final DispatchKeyGenerator dispatchKeyGenerator;
    /** Legacy {@code MsgSendUtil.SendMsg} on the 00302 failure branch (BATCH2 {@code :266-280}). */
    private final com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService opsAlertService;
    /** C1 (audit H-3): sibling fail-stop reads/propagates the shared pay-file status. */
    private final com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository payFileMstRepository;
    /**
     * Per-sibling 00302 issuance, each in its own {@code REQUIRES_NEW} transaction — a separate bean
     * because a self-invocation would bypass the proxy and inherit this handler's transaction, which is
     * the exact boundary the fan-out has to escape.
     */
    private final BulkExecutionInlineTxService bulkExecutionInlineTxService;
    /**
     * Writes a {@code TB_FIRM_BAT_API_TRAN} row in its own committed transaction. Used on the
     * unmatched-callback path so a discarded 01301 is still auditable.
     */
    private final DispatchApiTranAuditWriter apiTranAuditWriter;

    /**
     * Handle a 01301 reception-result callback: decrypt → validate → reconcile →
     * advance the file → (on success) issue 00302 inline, idempotently.
     *
     * @throws BusinessException     ({@code WBE_EFS_VERIFY_FAILED}) when the inbound
     *                               VV/EV cannot be verified/decrypted
     * @throws IllegalStateException when the API key is not provisioned (B2)
     */
    @Transactional
    public EfsEnvelope handle(EfsCallbackEnvelope envelope) {
        // 0. Common header check (legacy step 0, FILE_DEPOSIT_TRANSFER_API1_act.jsp:68-77).
        //    Legacy tests all SEVEN transport fields in one guard and answers G001
        //    (CD_BASE_NONE_INPUT_DATA) for any of them. We previously checked only ID, and answered
        //    G002. Both corrected 2026-08-03.
        validateEnvelope(envelope);
        String vanOrgCd = envelope.id();

        // 1. Resolve key + verify VV + decrypt EV (legacy steps 1–2). A bad MAC must not process.
        String apiKey = vanApiKeyService.getApiKey(vanOrgCd);
        if (!efsCryptoService.verifyVv(envelope.ev(), envelope.vv(), apiKey)) {
            log.warn("01301 callback VV verification failed vanOrgCd={} tno={}", vanOrgCd, envelope.tno());
            // A2 (audit C1): return the EFS G003 acknowledgement (echo EV/VV, no rebuild).
            return receptionAck(envelope, null, FirmBankingResultCd.ENC_VRFY_ERR,
                    FirmBankingResultCd.MSG_ENC_VRFY_ERR, false, apiKey);
        }
        BulkReceptionResultRequest req = decode(efsCryptoService.decryptEv(envelope.ev(), apiKey));

        // 2. Individual required-field check (legacy step 3).
        validateRequest(req);

        LocalDateTime now = LocalDateTime.now(ICT);
        String tranDt = now.format(TRAN_DT_FMT);
        String tranTm = now.format(TRAN_TM_FMT);

        // FIX A — the LOOKUP date comes from the FILE NAME, not from this clock.
        //
        // `tranDt` above is "today in ICT at CALLBACK time". The row's TRAN_DT was stamped at FILE
        // BUILD time. Those are the same value only when the callback lands on the same ICT calendar
        // day as the build. A file built at 23:58 whose 01301 arrives at 00:02 misses the lookup, and
        // because handleUnmatchedCallback filtered on the same clock-derived date, every gateway retry
        // missed identically — the file stayed at PROC_STEP='0'/PROC_STAT='6' forever.
        //
        // FIRM_FILE_NM is S_{VAN}_{TYPE}_{round}_{TRAN_DT} (BulkReceptionService:513), so the name
        // carries the authoritative date. This is the same idiom BulkExecutionResultService:147-157
        // already uses on the 01302 leg; the 01301 leg was the outlier.
        //
        // DELIBERATE IMPROVEMENT ON LEGACY, NOT PARITY. Legacy has the identical defect:
        // FILE_DEPOSIT_TRANSFER_API1_act.jsp:55 sets `procDt = DateTime.getInstance().getDate("YYYYMMDD")`
        // and uses it as the TRAN_DT key at :152. Do not "restore parity" here.
        //
        // Scope is deliberately narrow — see the two dates below.
        String lookupTranDt = tranDtFromFileNm(req.fileNm(), tranDt, vanOrgCd, envelope.tno());

        // 3. Look up the reception-requested file (legacy R004). PROC_STEP guard:
        //    a duplicate callback (file already advanced) finds nothing → safe no-op.
        TbFirmFileMst file = firmFileMstRepository
                .findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                        lookupTranDt, req.fileNm(), ProcStep.REQ.code(), ProcStat.REQ.code())
                .orElse(null);
        if (file == null) {
            return handleUnmatchedCallback(envelope, req, vanOrgCd, lookupTranDt, tranDt, tranTm, apiKey);
        }

        // From here the row is in hand, so its OWN TRAN_DT is authoritative for every sibling / pay-file
        // lookup — mirroring legacy :177, which switches from procDt to
        // idoOutFFMR001.getString("TRAN_DT") the moment the file row is resolved.
        String rowTranDt = file.getTranDt();

        BigDecimal receivedAmt = combineAmount(req.dataAmt(), req.dataDcmAmt());

        // C1 (audit H-3, legacy 2025.09.10 FILE_DEPOSIT_TRANSFER_API1_act.jsp:173-242): sibling
        // fail-stop. Before reconciling this split sibling, abort if the shared pay-file
        // (TB_PAY_FILE_MST) is already FAIL — a prior sibling failed, so executing this one would
        // move money partially. Write a G998 audit row + mark THIS firm file PROC_STEP='1'/PROC_STAT='3'
        // (REG_RSLT_CD=G998) and stop: no reception advance, no 00302. Matches legacy R004 CNT>0.
        if (siblingAlreadyFailed(rowTranDt, file.getBizKey(), file.getFileSeqNo())) {
            log.warn("01301 sibling fail-stop (G998) — pay-file already FAIL, aborting fileNm={} bizKey={} fileSeqNo={}",
                    req.fileNm(), file.getBizKey(), file.getFileSeqNo());
            writeApiTran(file, tranDt, tranTm, ApiTranTp.BULK_RECEPTION_RESULT.code(),
                    bigDecimalOrZero(req.dataCnt()), receivedAmt,
                    req.resultCd(), req.resultMsg(),
                    FirmBankingResultCd.TARGET_STAT_ERR, FirmBankingResultCd.MSG_TARGET_STAT_ERR,
                    tranDt + tranTm, envelope.tno());
            failFile(file, tranDt + tranTm, req.dataCnt(), receivedAmt,
                    FirmBankingResultCd.TARGET_STAT_ERR, FirmBankingResultCd.MSG_TARGET_STAT_ERR);
            // Envelope RC=0000, NOT G998 — see "Envelope RC vs persisted result code" in the class
            // Javadoc. G998 is durably recorded above (audit row + REG_RSLT_CD) because that is the
            // truth about the FILE; the envelope answers a different question, and the honest answer
            // to "did you accept my notification" here is yes — this branch committed both writes.
            return receptionAck(envelope, req, FirmBankingResultCd.SUCC,
                    FirmBankingResultCd.MSG_SUCC, false, apiKey);
        }

        // 4. 01301 reception-result audit row. TRAN_SEQ = the VAN callback's TNO (A1 / audit X-2 —
        //    legacy FILE_DEPOSIT_TRANSFER_API1_act.jsp:200 sets TRAN_SEQ = inputTno).
        boolean receptionSuccess = RECEPTION_SUCCESS_CD.equals(req.resultCd());
        writeApiTran(file, tranDt, tranTm, ApiTranTp.BULK_RECEPTION_RESULT.code(),
                bigDecimalOrZero(req.dataCnt()), receivedAmt,
                req.resultCd(), req.resultMsg(),
                receptionSuccess ? FirmBankingResultCd.SUCC : FirmBankingResultCd.ETC_ERR,
                receptionSuccess ? FirmBankingResultCd.MSG_SUCC : FirmBankingResultCd.MSG_ETC_ERR,
                tranDt + tranTm, envelope.tno());

        // 5. Reconcile and advance the file (legacy step 5).
        if (!receptionSuccess) {
            failFile(file, tranDt + tranTm, req.dataCnt(), receivedAmt, req.resultCd(), req.resultMsg());
            log.info("01301 reception failed (result_cd={}) — no 00302 vanOrgCd={} fileNm={}",
                    req.resultCd(), vanOrgCd, req.fileNm());
            // WeCMS processed the callback (recorded the bank's reception failure) → ack 0000, echo EV.
            return receptionAck(envelope, req, FirmBankingResultCd.SUCC, FirmBankingResultCd.MSG_SUCC, false, apiKey);
        }
        if (isMismatch(req.dataCnt(), receivedAmt, file)) {
            failFile(file, tranDt + tranTm, req.dataCnt(), receivedAmt,
                    FirmBankingResultCd.AMT_ERR, FirmBankingResultCd.MSG_AMT_ERR);
            log.warn("01301 reception count/amount mismatch — no 00302 vanOrgCd={} fileNm={} "
                            + "recvCnt={} reqCnt={} recvAmt={} reqAmt={}",
                    vanOrgCd, req.fileNm(), req.dataCnt(), file.getReqCnt(), receivedAmt, file.getReqAmt());
            // Envelope RC=0000, NOT 9999 — see "Envelope RC vs persisted result code" in the class
            // Javadoc. 9999 (AMT_ERR) is durably recorded above via failFile (REG_RSLT_CD) because the
            // mismatch is the truth about the FILE; the notification itself was received, reconciled
            // and acted on, so the envelope says so. 9999 is not a vendor code in any case ([참조 2]
            // publishes only 0000/G001/G002/G997/G998/G999), so it could never have been read.
            return receptionAck(envelope, req, FirmBankingResultCd.SUCC, FirmBankingResultCd.MSG_SUCC, false, apiKey);
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
                .findByTranDtAndBizKeyAndFileSeqNo(rowTranDt, file.getBizKey(), file.getFileSeqNo());
        boolean awaitingSiblings = siblings.stream()
                .anyMatch(f -> ProcStep.REQ.code().equals(f.getProcStep()));
        if (awaitingSiblings) {
            log.info("01301 reception success — deferring 00302, sibling split files still pending "
                    + "vanOrgCd={} fileSeqNo={}", vanOrgCd, file.getFileSeqNo());
            // Reception itself succeeded (00302 merely deferred) → success ack with rebuilt EV.
            return receptionAck(envelope, req, FirmBankingResultCd.SUCC, FirmBankingResultCd.MSG_SUCC, true, apiKey);
        }
        // Barrier released: every sibling has reported. Issue 00302 for each reception-success sibling
        // AFTER this transaction commits, one transaction per sibling (see the class Javadoc's
        // "Transaction boundaries" section for why the fan-out cannot run in here).
        List<TbFirmFileMstId> ready = siblings.stream()
                .filter(f -> ProcStep.REQ_RECV.code().equals(f.getProcStep())
                        && ProcStat.REQ.code().equals(f.getProcStat()))
                .map(f -> new TbFirmFileMstId(
                        f.getTranDt(), f.getBizKey(), f.getFileSeqNo(), f.getFirmFileNm()))
                .toList();
        scheduleExecutionFanOut(ready, vanOrgCd, tranDt, tranTm);
        // A2 (audit C1): 01301 success ack — rebuild EV/VV over the SIX echo fields
        // {bank_cd, prtn_id, file_nm, data_cnt, data_amt, data_dcm_amt} (legacy
        // FILE_DEPOSIT_TRANSFER_API1_act.jsp:409-423), RC=0000. NOT the request EV (unlike 01302).
        return receptionAck(envelope, req, FirmBankingResultCd.SUCC, FirmBankingResultCd.MSG_SUCC, true, apiKey);
    }

    /**
     * Build the 01301 EFS acknowledgement (A2). On the reception-success path {@code rebuildEv=true}:
     * {@code EV}/{@code VV} are freshly built over the six echo fields (legacy API1_act.jsp:409-423);
     * otherwise the inbound {@code EV}/{@code VV} are echoed. {@code RS_DTIME} is the response time.
     */
    private EfsEnvelope receptionAck(EfsCallbackEnvelope in, BulkReceptionResultRequest req,
                                     String rc, String rm, boolean rebuildEv, String apiKey) {
        String responseDttm = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String ev = in.ev();
        String vv = in.vv();
        if (rebuildEv && req != null) {
            String echoJson = serialize(new Efs301Request(
                    req.bankCd(), req.prtnId(), req.fileNm(), req.dataCnt(), req.dataAmt(), req.dataDcmAmt()));
            ev = efsCryptoService.encryptEv(echoJson, apiKey, responseDttm);
            vv = efsCryptoService.buildVv(echoJson, apiKey);
        }
        return new EfsEnvelope(in.id(), null, responseDttm, in.tno(), ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, rc, rm);
    }

    /**
     * Register the 00302 fan-out for {@code afterCommit}, one transaction per sibling.
     *
     * <p>See the class Javadoc's "Transaction boundaries" section for why the fan-out cannot run inside
     * {@link #handle}'s transaction. In short: legacy BATCH2 committed per row, so each file's
     * transition was durable the moment its result was known and one file's failure could not undo
     * another's. Issuing N gateway calls inside one transaction lost both properties, and held a DB
     * connection across all N.
     *
     * <p><b>The no-transaction fallback is deliberate and loud.</b> {@code handle} is
     * {@code @Transactional}, so in the running application there is always a synchronization to
     * register on ({@code BulkReceptionResultTransactionBoundaryTest} asserts that the proxied path
     * does, so a future refactor cannot drop the annotation unnoticed). A caller without one (a unit
     * test constructing the service directly, or some future non-transactional entry point) would
     * otherwise get no 00302 at all, and a stranded payment is a worse outcome than a missing commit
     * barrier — so the fan-out runs immediately and says so at WARN.
     *
     * <p>Running immediately is also not a correctness compromise: with no synchronization active there
     * is no transaction, so every repository write {@code handle} just performed has already
     * auto-committed. There is no barrier left to lose — only the guarantee that the writes and the
     * gateway calls are ordered by an explicit commit rather than by having each write commit on its own.
     */
    private void scheduleExecutionFanOut(List<TbFirmFileMstId> ready, String vanOrgCd,
                                         String tranDt, String tranTm) {
        if (ready.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("01301 handled with no active transaction — issuing {} 00302 request(s) immediately, "
                    + "with no commit barrier (expected only for non-transactional callers)", ready.size());
            fanOutExecution(ready, vanOrgCd, tranDt, tranTm);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fanOutExecution(ready, vanOrgCd, tranDt, tranTm);
            }
        });
    }

    /**
     * Issue 00302 for each ready sibling — each in its own transaction, each isolated from the others.
     *
     * <p>{@link BulkExecutionInlineTxService#issueOne} records every <i>expected</i> failure on the
     * audit row and the file status itself, so reaching the catch here means something escaped even
     * that (and took its own transaction down with it). Legacy's equivalent is BATCH2's per-row
     * {@code continue}: abandon that sibling to monitoring, keep going. Rethrowing would be worse than
     * useless — this runs after commit, so there is nothing left to roll back and the exception would
     * surface on a callback WeCMS has already acknowledged.
     */
    private void fanOutExecution(List<TbFirmFileMstId> ready, String vanOrgCd,
                                 String tranDt, String tranTm) {
        for (TbFirmFileMstId id : ready) {
            try {
                bulkExecutionInlineTxService.issueOne(id, vanOrgCd, tranDt, tranTm, this::issueExecution);
            } catch (RuntimeException e) {
                log.error("00302 fan-out failed unexpectedly for fileNm={} bizKey={} fileSeqNo={} — "
                                + "sibling abandoned for monitoring, remaining siblings continue",
                        id.getFirmFileNm(), id.getBizKey(), id.getFileSeqNo(), e);
            }
        }
    }

    /**
     * A 01301 callback whose {@code R004} lookup ({@code PROC_STEP='0'/PROC_STAT='6'}) matched nothing.
     *
     * <p>That lookup returning empty covers <b>three different situations</b>, and the handler used to
     * answer all three with {@code RC=0000} and a rebuilt {@code EV} — the strongest "stop resending"
     * signal the protocol has — while persisting nothing and logging at {@code info}. It is the correct
     * answer for exactly one of them:
     * <ol>
     *   <li><b>a row with this {@code FIRM_FILE_NM} exists at a later {@code PROC_STEP}</b>
     *       ({@code '1'}/{@code '2'}/{@code '3'}) → a genuine replay of a callback WeCMS already
     *       processed. Keep the success ack: resending cannot help and the work is done;</li>
     *   <li><b>no row with this {@code FIRM_FILE_NM} in any state</b> → WeCMS has no record of the file
     *       the bank is reporting on. That is not "already processed", it is a lookup that should have
     *       matched (a callback that crossed midnight so {@code TRAN_DT} differs, a reception row not
     *       yet committed, a name mismatch, the wrong environment). Acking {@code 0000} tells the
     *       gateway to stop, and the reception result is then <b>lost with nothing recorded anywhere</b>.
     *       Answer a retryable error so the gateway resends;</li>
     *   <li><b>a row exists but in an unexpected state</b> (e.g. still {@code PROC_STEP='0'} with
     *       {@code PROC_STAT} other than {@code '6'}) → also not a completed replay; retryable.</li>
     * </ol>
     *
     * <p><b>Audit on every callback, not only the processed ones.</b> In cases 1 and 3 the
     * {@code TB_FIRM_BAT_API_TRAN} row is written through
     * {@link DispatchApiTranAuditWriter#saveInNewTransaction}, so a discarded callback stays
     * reconstructable and — being {@code REQUIRES_NEW} — survives even if this transaction later rolls
     * back.
     *
     * <p>Case 2 <b>cannot</b> be audited that way: {@code BIZ_KEY} is part of
     * {@code TB_FIRM_BAT_API_TRAN}'s primary key and is {@code NOT NULL}, and an unmatched callback
     * resolves no tenant (it carries {@code bank_cd} and {@code prtn_id}, never {@code BIZ_KEY}).
     * Inventing a placeholder tenant to satisfy the PK would put a fabricated {@code BIZ_KEY} into the
     * payment ledger, which is worse than not recording the row. So case 2 escalates through
     * {@link OpsAlertService#notify} instead — it needs no DB row, and the condition warrants a push
     * rather than a log line, because an unmatched callback means the gateway is retrying against a file
     * WeCMS cannot find and only an operator can work out why (wrong environment, a reception row that
     * never committed, a {@code TRAN_DT} that rolled over). The {@code error} log carrying
     * {@code ID} / {@code TNO} / {@code file_nm} / {@code RC} remains the detail record.
     */
    /**
     * @param lookupTranDt the FILE-NAME-derived date (FIX A) — the key for every row lookup here. Using
     *                     the callback clock instead was what made the rollover unrecoverable: the
     *                     primary lookup missed, this fallback filtered on the same wrong date, and so
     *                     every gateway retry produced the identical UNMATCHED error.
     * @param tranDt       the CALLBACK-CLOCK date — used only for the audit row's {@code TRAN_DT} and,
     *                     with {@code tranTm}, for {@code RS_DTTM}. Kept clock-derived deliberately:
     *                     legacy stamps both from {@code procDt} ({@code API1_act.jsp:197,211}), and
     *                     splicing the filename date onto a callback-time clock would manufacture a
     *                     timestamp for a day that never happened.
     */
    private EfsEnvelope handleUnmatchedCallback(EfsCallbackEnvelope envelope, BulkReceptionResultRequest req,
                                                String vanOrgCd, String lookupTranDt,
                                                String tranDt, String tranTm, String apiKey) {
        List<TbFirmFileMst> byName =
                firmFileMstRepository.findByTranDtAndFirmFileNm(lookupTranDt, req.fileNm());

        if (byName.isEmpty()) {
            // Case 2 — nothing processed and no tenant to attribute an audit row to. Error level: this
            // is a reception result WeCMS could not match, not a routine duplicate.
            // Report the date the query ACTUALLY used (the filename-derived one) plus the callback
            // clock date, so an operator can see immediately whether the two disagree — that
            // disagreement is the rollover signature and it used to be invisible in this log line.
            // RC = G004, matching legacy: FILE_DEPOSIT_TRANSFER_API1_act.jsp:168-170 answers
            // WCConst.CD_DB_ERR when the file lookup returns resultCount == 0. (Legacy reuses its
            // DB-error code for "no such file" on this leg; the 01302 twin uses G999 instead. We
            // answered G999 here until 2026-08-03 — same retryable intent, wrong code.)
            log.error("01301 callback UNMATCHED — no TB_FIRM_FILE_MST row for lookupTranDt={} fileNm={} in "
                            + "any state (callbackClockDt={}); answering RC={} so the gateway resends. "
                            + "vanOrgCd={} tno={} (no audit row is possible: TB_FIRM_BAT_API_TRAN.BIZ_KEY "
                            + "is a NOT NULL PK column and an unmatched callback resolves no tenant)",
                    lookupTranDt, req.fileNm(), tranDt, FirmBankingResultCd.DB_ERR,
                    vanOrgCd, envelope.tno());
            // Escalate instead of auditing: no DB row is possible here (see the method Javadoc), and the
            // gateway will keep retrying until someone works out why the file cannot be found.
            // OpsAlertService is @Async and a no-op unless wecms.dispatch.alert.enabled=true.
            if (vanOrgCd != null && !vanOrgCd.isBlank()) {
                opsAlertService.notify(vanOrgCd,
                        "접수결과 수신요청 API — unmatched callback (no TB_FIRM_FILE_MST row)."
                                + "\nTRAN_DT (lookup, from file name) : " + lookupTranDt
                                + "\nTRAN_DT (callback clock) : " + tranDt
                                + "\nFIRM_FILE_NM : " + req.fileNm()
                                + "\nID : " + vanOrgCd
                                + "\nTNO : " + envelope.tno()
                                + "\nAPI RC : " + FirmBankingResultCd.DB_ERR
                                + "\nThe gateway will retry. No audit row could be written "
                                + "(TB_FIRM_BAT_API_TRAN.BIZ_KEY is NOT NULL and no tenant is resolvable).");
            }
            return receptionAck(envelope, req, FirmBankingResultCd.DB_ERR,
                    FirmBankingResultCd.MSG_DB_ERR, false, apiKey);
        }

        // FIX J — AMBIGUOUS NAME. More than one file carries this FIRM_FILE_NM, so "which file is this
        // callback about?" has no answer, and the checks below would answer it by accident:
        //   * `byName.get(0)` picks a row arbitrarily (the query has no ORDER BY), and
        //   * `anyMatch` lets ANY row's progress decide the acknowledgement.
        // Combined, a callback genuinely meant for row Y — sitting in an unexpected state — is acked
        // RC=0000 on the strength of row X having advanced. RC=0000 is the strongest "stop resending"
        // signal there is, so Y's reception result is consumed and lost, with no retry and no record.
        // That is the exact failure C-2 exists to prevent (it separated "replay" from "unknown" from
        // "unexpected state" precisely because the three need opposite acknowledgements); the multi-row
        // case slipped through because C-2 reasoned about one row.
        //
        // So: do not classify at all. Answer retryable, escalate, and let a human disambiguate.
        //
        // The gateway will keep resending and the condition is deterministic — two rows will keep
        // matching — so this is an unbounded retry until someone acts. That is deliberate and is the
        // lesser evil: a retrying callback is recoverable the moment the ambiguity is resolved, whereas
        // a wrongly-acked one is gone. It is the same trade the R004 lookup already makes when two rows
        // match at '0'/'6' (Spring Data raises IncorrectResultSizeDataAccessException, which CallbackAck
        // maps to G004). The alert below is what makes it actionable rather than a silent loop.
        //
        // NOT audited, on purpose. TB_FIRM_BAT_API_TRAN.BIZ_KEY is a NOT NULL PK column and the two rows
        // may belong to different tenants, so writing the row would mean guessing a tenant for a payment
        // ledger entry. That is the same objection this method already records for the no-row case
        // ("inventing a placeholder tenant … is worse than not recording the row"), and an arbitrary
        // guess is not better than an invented value. The error log + ops alert carry the record.
        if (byName.size() > 1) {
            String rows = byName.stream()
                    .map(f -> "{fileSeqNo=" + f.getFileSeqNo()
                            + ", bizKey=" + f.getBizKey()
                            + ", bankPrtnId=" + f.getBankPrtnId()
                            + ", outBankCd=" + f.getOutBankCd()
                            + ", procStep=" + f.getProcStep()
                            + ", procStat=" + f.getProcStat() + "}")
                    .collect(java.util.stream.Collectors.joining(", "));
            // RC stays G999 here, deliberately — unlike the UNMATCHED branch above there is NO legacy
            // line to mirror. Legacy has no ambiguity guard at all: it takes the row the unordered
            // query happened to return. FIX J is a refactor-only safety stop, so it keeps the generic
            // error code rather than borrowing legacy's G004, which means "lookup found nothing".
            log.error("01301 callback AMBIGUOUS — {} TB_FIRM_FILE_MST rows share fileNm={} on tranDt={}; "
                            + "refusing to classify from another row's state. Answering RC={} so the "
                            + "gateway resends. vanOrgCd={} tno={} rows=[{}] (no audit row written: "
                            + "TB_FIRM_BAT_API_TRAN.BIZ_KEY is a NOT NULL PK column and the tenant is "
                            + "ambiguous). The callback's prtn_id={} is the field that would "
                            + "disambiguate — see the FIRM_FILE_NM naming decision.",
                    byName.size(), req.fileNm(), lookupTranDt, FirmBankingResultCd.ETC_ERR,
                    vanOrgCd, envelope.tno(), rows, req.prtnId());
            if (vanOrgCd != null && !vanOrgCd.isBlank()) {
                opsAlertService.notify(vanOrgCd,
                        "접수결과 수신요청 API — ambiguous FIRM_FILE_NM (multiple files share one name)."
                                + "\nTRAN_DT (lookup, from file name) : " + lookupTranDt
                                + "\nFIRM_FILE_NM : " + req.fileNm()
                                + "\nMATCHING_ROWS : " + byName.size()
                                + "\nROWS : " + rows
                                + "\nCALLBACK prtn_id : " + req.prtnId()
                                + "\nID : " + vanOrgCd
                                + "\nTNO : " + envelope.tno()
                                + "\nAPI RC : " + FirmBankingResultCd.ETC_ERR
                                + "\nThe gateway will retry until the ambiguity is resolved. Do NOT "
                                + "advance either row without establishing which file the bank means.");
            }
            return receptionAck(envelope, req, FirmBankingResultCd.ETC_ERR,
                    FirmBankingResultCd.MSG_ETC_ERR, false, apiKey);
        }

        // Exactly one row from here — `get(0)` is unambiguous and `anyMatch` degenerates to that row.
        TbFirmFileMst known = byName.get(0);
        boolean alreadyProgressed = byName.stream()
                .anyMatch(f -> isPastReceptionRequest(f.getProcStep()));
        BigDecimal receivedAmt = combineAmount(req.dataAmt(), req.dataDcmAmt());
        String rc = alreadyProgressed ? FirmBankingResultCd.SUCC : FirmBankingResultCd.ETC_ERR;
        String rm = alreadyProgressed ? FirmBankingResultCd.MSG_SUCC : FirmBankingResultCd.MSG_ETC_ERR;

        // Audit the discarded callback (REQUIRES_NEW — durable regardless of this transaction's fate).
        apiTranAuditWriter.saveInNewTransaction(buildApiTran(known, tranDt, tranTm,
                ApiTranTp.BULK_RECEPTION_RESULT.code(),
                bigDecimalOrZero(req.dataCnt()), receivedAmt,
                req.resultCd(), req.resultMsg(), rc, rm, tranDt + tranTm, envelope.tno()));

        if (alreadyProgressed) {
            log.info("01301 callback replay — fileNm={} is already at PROC_STEP={} (past '0'); acking "
                            + "RC=0000 so the gateway stops resending. vanOrgCd={} tno={}",
                    req.fileNm(), known.getProcStep(), vanOrgCd, envelope.tno());
        } else {
            log.error("01301 callback in an UNEXPECTED state — fileNm={} is at PROC_STEP={}/PROC_STAT={}, "
                            + "not the expected '0'/'6'; answering RC={} so the gateway resends. "
                            + "vanOrgCd={} tno={}",
                    req.fileNm(), known.getProcStep(), known.getProcStat(),
                    FirmBankingResultCd.ETC_ERR, vanOrgCd, envelope.tno());
        }
        // The replay case keeps legacy's rebuilt-EV success ack; the retryable cases echo the inbound EV.
        return receptionAck(envelope, req, rc, rm, alreadyProgressed, apiKey);
    }

    /**
     * {@code true} when {@code procStep} is past reception-request ({@code '0'}) — the file has already
     * been advanced by a 01301 callback, so a further callback for it is a replay.
     */
    private static boolean isPastReceptionRequest(String procStep) {
        return ProcStep.REQ_RECV.code().equals(procStep)
                || ProcStep.DEPO_REQ.code().equals(procStep)
                || ProcStep.DEPO_RES_RECV.code().equals(procStep);
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

        // C2 (audit M-1, legacy FILE_DEPOSIT_TRANSFER_API1_act.jsp:297-310/369-382 U001): a reception
        // failure must also fail the shared pay-file summary (TB_PAY_FILE_MST PROC_STAT='3'), so the
        // UI/summary reflect it AND the C1 sibling fail-stop trips for the remaining split files.
        payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(
                        file.getTranDt(), file.getBizKey(), file.getFileSeqNo())
                .ifPresent(pay -> {
                    pay.setProcStat(ProcStat.FAIL.code());
                    pay.setRsMsg(rsltMsg); // TB_PAY_FILE_MST has RS_MSG (no RS_CD column) — legacy U001 parity
                    pay.setProcDttm(procDttm);
                    payFileMstRepository.save(pay);
                });
    }

    /**
     * C1 sibling fail-stop probe — legacy {@code TB_PAY_FILE_MST_R004} ({@code CNT} of
     * {@code PROC_STAT='3'} for the {@code (TRAN_DT, BIZ_KEY, FILE_SEQ_NO)} group). The refactor's
     * pay-file is one row per group, so this is "is the shared pay-file already FAIL?".
     */
    /**
     * FIX A — extract {@code TRAN_DT} from {@code FIRM_FILE_NM}.
     *
     * <p>The name is {@code S_{VAN_ORG_CD}_{FILE_TYPE}_{round}_{TRAN_DT}}
     * ({@code BulkReceptionService:513}), so {@code split("_")[4]} is the build date. Neither
     * {@code VAN_ORG_CD} ({@code GV00000001}) nor {@code FILE_TYPE} ({@code SHB}/{@code VCB}/{@code WRB})
     * contains an underscore, so the split is unambiguous. Unlike the 01302 result file, the 01301
     * callback echoes the <b>sent</b> name verbatim — which is why the caller can also still match
     * {@code FIRM_FILE_NM} exactly.
     *
     * <p><b>Fails soft, never throws.</b> On a malformed name this returns {@code fallbackTranDt} (the
     * callback clock date, i.e. the previous behaviour) and logs a WARN. Rejecting the callback instead
     * would be worse than the bug being fixed: a name shape this method does not recognise is a
     * gateway-contract change, and answering it with an error would strand every file rather than the
     * ones crossing midnight. The 8-digit check is what stops a plausible-but-wrong token (a round
     * number, a renamed file) silently becoming the lookup key.
     *
     * @param fallbackTranDt the callback-clock date to fall back to
     */
    private String tranDtFromFileNm(String fileNm, String fallbackTranDt, String vanOrgCd, String tno) {
        if (fileNm != null) {
            String[] parts = fileNm.split("_");
            if (parts.length >= 5 && DT8_DIGITS.matcher(parts[4]).matches()) {
                return parts[4];
            }
        }
        log.warn("01301 callback — could not derive TRAN_DT from FIRM_FILE_NM={} (expected "
                        + "S_{{VAN}}_{{TYPE}}_{{round}}_{{yyyyMMdd}}); falling back to the callback clock "
                        + "date {}. A file built on a different ICT day will NOT be found. "
                        + "vanOrgCd={} tno={}",
                fileNm, fallbackTranDt, vanOrgCd, tno);
        return fallbackTranDt;
    }

    private boolean siblingAlreadyFailed(String tranDt, String bizKey, String fileSeqNo) {
        return payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(tranDt, bizKey, fileSeqNo)
                .map(pay -> ProcStat.FAIL.code().equals(pay.getProcStat()))
                .orElse(false);
    }

    /**
     * Build + persist a {@code TB_FIRM_BAT_API_TRAN} audit row; returns the managed entity.
     *
     * @param tranSeq explicit {@code TRAN_SEQ} (A1): for the 01301 reception-result audit this is
     *                the VAN callback's TNO (legacy {@code inputTno}); for the 00302 request audit
     *                it is {@code null} → a fresh {@code TRAN_SEQ} is allocated (which the caller
     *                then sends as the 00302 gateway TNO).
     */
    private TbFirmBatApiTran writeApiTran(TbFirmFileMst file, String tranDt, String tranTm, String apiTranTp,
                                          BigDecimal batCnt, BigDecimal batAmt,
                                          String regRsltCd, String regRsltMsg,
                                          String rsCd, String rsMsg, String rsDttm, String tranSeq) {
        return firmBatApiTranRepository.save(buildApiTran(file, tranDt, tranTm, apiTranTp,
                batCnt, batAmt, regRsltCd, regRsltMsg, rsCd, rsMsg, rsDttm, tranSeq));
    }

    /**
     * Build (but do not persist) a {@code TB_FIRM_BAT_API_TRAN} row — split out of
     * {@link #writeApiTran} so a caller can choose its own persistence boundary. The unmatched-callback
     * path routes the built row through {@link DispatchApiTranAuditWriter#saveInNewTransaction} so the
     * audit survives a rollback of the callback transaction.
     */
    private TbFirmBatApiTran buildApiTran(TbFirmFileMst file, String tranDt, String tranTm, String apiTranTp,
                                          BigDecimal batCnt, BigDecimal batAmt,
                                          String regRsltCd, String regRsltMsg,
                                          String rsCd, String rsMsg, String rsDttm, String tranSeq) {
        TbFirmBatApiTran row = new TbFirmBatApiTran();
        row.setTranDt(tranDt);
        row.setBizKey(file.getBizKey());
        row.setTranSeq(StringUtils.hasText(tranSeq)
                ? tranSeq : dispatchKeyGenerator.nextTranSeq(file.getBizKey(), tranDt));
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
        return row;
    }

    /**
     * Common transport-header check — legacy {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:68-77}.
     *
     * <p>Legacy guards all seven fields together and throws
     * {@code WCConst.CD_BASE_NONE_INPUT_DATA} ({@code G001}) for any of them. The order below
     * matches the legacy {@code ||} chain so the reported field is the same one legacy would have
     * hit first.
     */
    private void validateEnvelope(EfsCallbackEnvelope envelope) {
        if (envelope == null) {
            throw baseFieldMissing("<envelope>");
        }
        requireHeader(envelope.id(), "ID");
        requireHeader(envelope.rqDtime(), "RQ_DTIME");
        requireHeader(envelope.tno(), "TNO");
        requireHeader(envelope.ev(), "EV");
        requireHeader(envelope.vv(), "VV");
        requireHeader(envelope.em(), "EM");
        requireHeader(envelope.vm(), "VM");
    }

    /**
     * Per-field business check — legacy {@code API1_act.jsp:125-147}.
     *
     * <p><b>The split is not what the constant names suggest.</b> Legacy answers
     * {@code G002} ({@code CD_DETAIL_NONE_INPUT_DATA}) for exactly two fields — the bank-identity
     * pair at {@code :125-129} — and {@code G001} ({@code CD_BASE_NONE_INPUT_DATA}) for the other
     * six at {@code :131-147}. We previously answered {@code G002} for all eight.
     */
    private void validateRequest(BulkReceptionResultRequest req) {
        requireDetailField(req.bankCd(), "bank_cd");        // legacy :125-127 → G002
        requireDetailField(req.prtnId(), "prtn_id");        // legacy :128-129 → G002
        requireBaseField(req.fileNm(), "file_nm");          // legacy :131-132 → G001
        requireBaseField(req.dataCnt(), "data_cnt");        // legacy :134-135 → G001
        requireBaseField(req.dataAmt(), "data_amt");        // legacy :137-138 → G001
        requireBaseField(req.dataDcmAmt(), "data_dcm_amt"); // legacy :140-141 → G001
        requireBaseField(req.resultCd(), "result_cd");      // legacy :143-144 → G001
        requireBaseField(req.resultMsg(), "result_msg");    // legacy :146-147 → G001
    }

    /** Transport header → {@code G001} (legacy {@code API1_act.jsp:68-77}). */
    private void requireHeader(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw baseFieldMissing(name);
        }
    }

    /** Body field legacy answers {@code G001} for (legacy {@code API1_act.jsp:131-147}). */
    private void requireBaseField(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw baseFieldMissing(name);
        }
    }

    /** Bank-identity field legacy answers {@code G002} for (legacy {@code API1_act.jsp:125-129}). */
    private void requireDetailField(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new MissingCallbackFieldException(
                    FirmBankingResultCd.DETAIL_NONE_INPUT_DATA,
                    FirmBankingResultCd.MSG_DETAIL_NONE_INPUT_DATA,
                    "01301 callback missing required field: " + name);
        }
    }

    private MissingCallbackFieldException baseFieldMissing(String name) {
        return new MissingCallbackFieldException(
                FirmBankingResultCd.BASE_NONE_INPUT_DATA,
                FirmBankingResultCd.MSG_BASE_NONE_INPUT_DATA,
                "01301 callback missing required field: " + name);
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

        // A1 (audit X-2): the envelope TNO is the allocated TB_FIRM_BAT_API_TRAN.TRAN_SEQ (legacy
        // BATCH2 `seq`), NOT a UUID.
        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = cmd.tno();
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        // D-2 (legacy parity): the envelope ID is ALWAYS TB_FIRM_CONF.VAN_ORG_CD — never a
        // configured partner id. Per-leg legacy evidence in EfsProperties' class Javadoc.
        String id = cmd.vanOrgCd();

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_302, requestEnvelope);

        String rc = response == null ? null : response.rc();
        // Legacy parity (FILE_DEPOSIT_TRANSFER_BATCH2.java:242): the 00302 leg accepts BOTH "0000"
        // and "BT00" as success. The generic isSuccess() accepts only "0000" and is left untouched
        // for the 201/202/203/00301 legs, which have no BT00 in their code space.
        if (response == null || !FirmBankingResultCd.isBulkExecutionSuccess(rc)) {
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
        if (!StringUtils.hasText(cmd.tno())) {
            throw new IllegalArgumentException("tno (TRAN_SEQ) is required");
        }
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
