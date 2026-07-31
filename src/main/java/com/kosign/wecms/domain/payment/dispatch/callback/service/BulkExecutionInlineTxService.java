package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Issues {@code 00302} (bulk execution instruction) for <b>one</b> reception-success file, in its own
 * {@code REQUIRES_NEW} transaction — the refactor of {@code FILE_DEPOSIT_TRANSFER_BATCH2}'s per-row
 * loop body.
 *
 * <h2>Why this is a separate bean</h2>
 * {@link BulkReceptionResultService} calls this after its own transaction commits, once per sibling.
 * Keeping the body in that class and annotating it {@code REQUIRES_NEW} would not work: a
 * self-invocation bypasses the Spring proxy, so the method would silently join the caller's transaction
 * — which is the exact boundary the fan-out exists to escape.
 *
 * <p>The EFS request leg itself is passed in as a {@link Consumer} rather than injected, so this class
 * does not depend on {@code BulkReceptionResultService} and the two do not form a cycle.
 *
 * <h2>What legacy did, per row (and what this restores)</h2>
 * BATCH2 opened a transaction per row ({@code :238}) and committed it before moving on — on success at
 * {@code :260-263}, on error at {@code :317-320}. Everything below follows from that:
 * <ul>
 *   <li>each file's outcome is durable the moment it is known;</li>
 *   <li>a failure on this file cannot undo a sibling whose 00302 already reached the bank;</li>
 *   <li>the file state is re-read from committed data, not inherited from the caller's persistence
 *       context — so the guard below reflects what is actually in the database.</li>
 * </ul>
 *
 * <h2>Terminal states this writes</h2>
 * <table border="1">
 *   <caption>00302 outcome → status</caption>
 *   <tr><th>Outcome</th><th>Firm file</th><th>Pay file</th><th>Legacy</th></tr>
 *   <tr><td>{@code 0000} / {@code BT00} success</td>
 *       <td>{@code PROC_STEP='2'}, {@code PROC_STAT} untouched ({@code '6'})</td><td>untouched</td>
 *       <td>{@code :242-263}</td></tr>
 *   <tr><td>transient ({@code BT01} / {@code G999} / {@code C999} / empty), <b>and</b> any
 *       non-{@code BusinessException} runtime failure — see {@link #issueOne}</td>
 *       <td>left at {@code PROC_STEP='1'/PROC_STAT='6'}</td><td>untouched</td>
 *       <td>{@code :202-206}, {@code :232-234} — {@code continue}</td></tr>
 *   <tr><td><b>permanent</b> (any other code)</td>
 *       <td>{@code PROC_STEP='2'} + {@code PROC_STAT='3'}</td><td>{@code PROC_STAT='3'}</td>
 *       <td>{@code :264-304}</td></tr>
 * </table>
 *
 * <p>The permanent row is finding <b>H-2</b> of {@code wecms-batch-business-parity-audit-2026-07-29.md}:
 * the refactor previously left a permanently-rejected bulk payment at {@code '1'/'6'} with the pay file
 * at {@code '6'}, so the UI showed a payment the bank had refused outright as <i>still processing</i>,
 * indefinitely. Note that legacy advances {@code PROC_STEP} to {@code '2'} on this branch too — it
 * records "execution was instructed and the instruction failed", not "we never got that far" — and that
 * is reproduced verbatim.
 *
 * <p>There is still <b>no automatic re-issue</b>, transient or otherwise. Legacy's retry was its next
 * 60-second poll; K9 offered Mr. Kang exactly that ("Option A — Recovery Scheduler") and he chose
 * inline execution, leaving the transient edge to monitoring plus manual recovery.
 * {@code StuckPayFileMonitorService} classifies the state the transient branch leaves as
 * {@code BULK_EXECUTION_UNCONFIRMED}. Do not add a retry here or a sweep elsewhere without reopening K9.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkExecutionInlineTxService {

    private final TbFirmFileMstRepository firmFileMstRepository;
    private final TbFirmBatApiTranRepository firmBatApiTranRepository;
    private final TbPayFileMstRepository payFileMstRepository;
    /** Commits the 00302 marker before the gateway call — see {@link #issueOne}. */
    private final DispatchApiTranAuditWriter apiTranAuditWriter;
    /** Allocates the fresh {@code TRAN_SEQ} that is also sent as the 00302 gateway {@code TNO} (A1). */
    private final DispatchKeyGenerator dispatchKeyGenerator;
    /** Legacy {@code MsgSendUtil.SendMsg} on the permanent-failure branch (BATCH2 {@code :266-280}). */
    private final OpsAlertService opsAlertService;

    /**
     * Issue 00302 for the file identified by {@code id}, idempotently, in a fresh transaction.
     *
     * <p><b>The idempotency marker commits before the gateway call.</b> The guard is a probe of
     * {@code TB_FIRM_BAT_API_TRAN} for an existing {@code 00302} row, so it can only work if that row
     * outlives a failure of this transaction. It previously did not: the write joined the caller's
     * transaction and rolled back with it, after which the guard found nothing and a gateway retry
     * <b>re-sent 00302 to the bank</b>. Routing it through
     * {@link DispatchApiTranAuditWriter#saveInNewTransaction} — a transaction that commits immediately,
     * the same treatment the 01302 audit row already had — makes "we have attempted this" durable before
     * the attempt happens. The returned row is detached, so the later {@code save(...)} is a merge.
     *
     * @param sender the EFS 00302 request leg, supplied by the caller (see the class Javadoc)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issueOne(TbFirmFileMstId id, String vanOrgCd, String tranDt, String tranTm,
                         Consumer<BulkExecutionApiCommand> sender) {
        TbFirmFileMst file = firmFileMstRepository.findById(id).orElse(null);
        if (file == null) {
            log.warn("00302 skipped — TB_FIRM_FILE_MST row gone for fileNm={} bizKey={} fileSeqNo={}",
                    id.getFirmFileNm(), id.getBizKey(), id.getFileSeqNo());
            return;
        }
        // Re-check the state against committed data. The caller selected this file before its own
        // commit; between then and now another node's callback or an operator could have moved it.
        if (!ProcStep.REQ_RECV.code().equals(file.getProcStep())
                || !ProcStat.REQ.code().equals(file.getProcStat())) {
            log.info("00302 skipped — fileNm={} is at PROC_STEP={}/PROC_STAT={}, no longer reception-success",
                    file.getFirmFileNm(), file.getProcStep(), file.getProcStat());
            return;
        }
        // FIX D — the guard is keyed on (BIZ_KEY, FIRM_FILE_NM, API_TRAN_TP), with NO date predicate.
        //
        // It used to include `tranDt`, the CALLBACK-clock date passed in by the fan-out. The row it
        // probes for is written a few lines below under that same clock — but the clock is read once per
        // invocation, so two invocations straddling ICT midnight probe two different keyspaces: the
        // second finds nothing and re-sends 00302 to the bank. Substituting file.getTranDt() would not
        // fix it either — that is the file-BUILD date, and the audit row is not stamped with it. There is
        // no correct date to probe with, so the date is gone. See the repository method's Javadoc for the
        // uniqueness proof (FIRM_FILE_NM embeds its own TRAN_DT) and the index situation.
        if (firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                file.getBizKey(), file.getFirmFileNm(), ApiTranTp.BULK_EXECUTION.code())) {
            log.info("00302 already issued for fileNm={} (idempotent skip) bizKey={}",
                    file.getFirmFileNm(), file.getBizKey());
            return;
        }

        String reqCnt = file.getReqCnt() == null ? "0" : file.getReqCnt().toBigInteger().toString();
        String[] amt = splitAmount(file.getReqAmt());
        String dttm14 = tranDt + tranTm;

        // Marker BEFORE the call, in its own committed transaction (see the method Javadoc).
        // TRAN_SEQ is allocated fresh (tranSeq=null) and then sent as the 00302 gateway TNO (A1) —
        // this is a request the refactor originates, not a callback echo.
        TbFirmBatApiTran audit = apiTranAuditWriter.saveInNewTransaction(
                buildApiTran(file, tranDt, tranTm, ApiTranTp.BULK_EXECUTION.code(),
                        file.getReqCnt(), file.getReqAmt(), null, null, null, null, null, null));

        try {
            sender.accept(new BulkExecutionApiCommand(
                    file.getVanOrgCd() != null ? file.getVanOrgCd() : vanOrgCd,
                    file.getOutBankCd(), file.getBankPrtnId(), file.getFirmFileNm(),
                    reqCnt, amt[0], amt[1], audit.getTranSeq()));
        } catch (BusinessException e) {
            // Gateway returned a definite non-success, or did not respond.
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            recordFailure(file, audit, rc, e.getCode(), dttm14, vanOrgCd);
            return;
        } catch (RuntimeException e) {
            // Everything that is NOT a BusinessException: an unprovisioned VAN key from
            // VanApiKeyService (IllegalStateException — the B2 gap), an EfsCryptoService encrypt/MAC
            // failure, a serialize/deserialize failure, a RestClientEfsApiClient transport error.
            //
            // Before this catch existed, every one of those escaped issueOne's predecessor, escaped the
            // sibling loop, and rolled back the WHOLE @Transactional handle() — including siblings whose
            // 00302 had already reached the bank, whose committed reception state was then discarded
            // while the money moved. The 00301 leg has caught both shapes since the H-3 fix
            // (BulkReceptionService:667-708); this is the same boundary on the 00302 leg.
            //
            // Recorded as G999/ETC_ERR, which isBulkExecutionRetryable() classifies as TRANSIENT — so
            // the file stays at PROC_STEP='1'/PROC_STAT='6' and no ops alert fires. That is deliberate,
            // and it is the opposite of what the 00301 leg does with the same exception shape:
            //
            //   * A RuntimeException means there was no gateway RESPONSE CODE at all. Legacy's nearest
            //     analogue is its empty-body branch (:202-206), which `continue`s without writing any
            //     status — which is also why isBulkExecutionRetryable(null/blank) returns true.
            //   * Egress is ambiguous: a transport error or an unparseable response may or may not have
            //     reached the bank. Writing a terminal FAIL for a bulk payment the bank may have
            //     executed is the worse of the two errors — stranded is recoverable, a wrongly-terminal
            //     batch is not.
            //   * The state is not unmonitored. The marker row written before the call exists, so
            //     StuckPayFileMonitorService reports BULK_EXECUTION_UNCONFIRMED — "sent, unconfirmed,
            //     reconcile with the VAN Center before acting", which is the correct operator
            //     instruction for exactly this ambiguity.
            //
            // (BulkReceptionService's H-3 branch marks FAIL for the same exception shape because the
            // 00301 leg has no transient/permanent split and its pay-file claim would otherwise strand
            // at '6' with nothing watching it. Different leg, different monitored state.)
            //
            // An Error propagates untouched.
            log.error("00302 failed unexpectedly ({}) fileNm={} bizKey={}",
                    e.getClass().getSimpleName(), file.getFirmFileNm(), file.getBizKey(), e);
            recordFailure(file, audit, FirmBankingResultCd.ETC_ERR, e.getClass().getSimpleName(),
                    dttm14, vanOrgCd);
            return;
        }

        // Confirmed → record success + advance PROC_STEP 1→2 (execution requested). PROC_STAT stays
        // '6': the execution RESULT arrives later via the 01302 callback (legacy :242-263).
        audit.setRsCd(FirmBankingResultCd.SUCC);
        audit.setRsMsg(FirmBankingResultCd.MSG_SUCC);
        audit.setRsDttm(dttm14);
        firmBatApiTranRepository.save(audit);

        file.setProcStep(ProcStep.DEPO_REQ.code());
        file.setProcDttm(dttm14);
        firmFileMstRepository.save(file);
        log.info("00302 execution issued fileNm={} bizKey={} -> PROC_STEP=2",
                file.getFirmFileNm(), file.getBizKey());
    }

    /**
     * Record a failed 00302 on the audit row and, when the code is <b>permanent</b>, on the file and
     * pay-file status as well (audit finding H-2, legacy {@code FILE_DEPOSIT_TRANSFER_BATCH2:264-304}).
     *
     * <p>The transient / permanent split is legacy's own, taken verbatim from {@code :232}: {@code BT01},
     * {@code G999}, {@code C999} and an empty response body {@code continue} without any status write —
     * because legacy's next 60-second poll would retry them — while every other code falls into the
     * {@code else} at {@code :264}, which marks the firm file {@code PROC_STEP='2'/PROC_STAT='3'} and
     * the pay file {@code PROC_STAT='3'}, and alerts.
     *
     * <p>The refactor has no retry, so a transient code leaves the file at {@code '1'/'6'} for the
     * monitor rather than for a poll — the same state, a different recovery mechanism. A permanent code
     * gets legacy's terminal FAIL, which is what makes the UI stop claiming a rejected payment is still
     * processing.
     */
    private void recordFailure(TbFirmFileMst file, TbFirmBatApiTran audit, String rc, String rsMsg,
                               String dttm14, String vanOrgCd) {
        audit.setRsCd(rc);
        audit.setRsMsg(rsMsg);
        audit.setRsDttm(dttm14);
        firmBatApiTranRepository.save(audit);

        if (FirmBankingResultCd.isBulkExecutionRetryable(rc)) {
            // Legacy `continue` — no status write, no alert. The file stays reception-success and
            // StuckPayFileMonitorService reports it as BULK_EXECUTION_UNCONFIRMED.
            log.warn("00302 NOT confirmed (rc={}, transient) — fileNm={} bizKey={} left at "
                            + "PROC_STEP=1/PROC_STAT=6 for monitoring + manual recovery",
                    rc, file.getFirmFileNm(), file.getBizKey());
            return;
        }

        // Permanent (H-2): legacy sets PROC_STEP='2' (execution WAS instructed) + PROC_STAT='3'.
        file.setProcStep(ProcStep.DEPO_REQ.code());
        file.setProcStat(ProcStat.FAIL.code());
        file.setProcDttm(dttm14);
        firmFileMstRepository.save(file);

        // ...and the shared pay-file summary to '3' (legacy TB_PAY_FILE_MST_U001 at :283-304), which is
        // what the UI reads and what trips the C1 sibling fail-stop for the rest of the split group.
        // TB_PAY_FILE_MST has RS_MSG but no RS_CD column — same shape as the reception-failure path.
        payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(
                        file.getTranDt(), file.getBizKey(), file.getFileSeqNo())
                .ifPresent(pay -> {
                    pay.setProcStat(ProcStat.FAIL.code());
                    pay.setRsMsg("EFS 00302 failed: " + rc);
                    pay.setProcDttm(dttm14);
                    payFileMstRepository.save(pay);
                });

        log.error("00302 PERMANENTLY failed (rc={}) — fileNm={} bizKey={} marked "
                        + "PROC_STEP=2/PROC_STAT=3 and pay-file PROC_STAT=3",
                rc, file.getFirmFileNm(), file.getBizKey());

        // Legacy :266-280 — ops alert on the permanent branch only. OpsAlertService is itself a no-op
        // unless wecms.dispatch.alert.enabled=true.
        String alertVanOrgCd = file.getVanOrgCd() != null && !file.getVanOrgCd().isBlank()
                ? file.getVanOrgCd().trim() : vanOrgCd;
        if (alertVanOrgCd != null && !alertVanOrgCd.isBlank()) {
            opsAlertService.notify(alertVanOrgCd,
                    "이체실행지시 Batch2 API수신 오류 발생 (00302 permanent failure)."
                            + "\nTRAN_DT : " + file.getTranDt()
                            + "\nBIZ_KEY : " + file.getBizKey()
                            + "\nFILE_SEQ_NO : " + file.getFileSeqNo()
                            + "\nFIRM_FILE_NM : " + file.getFirmFileNm()
                            + "\nAPI RC : " + rc);
        }
    }

    /** Build (not persist) the {@code TB_FIRM_BAT_API_TRAN} row for this leg. */
    private TbFirmBatApiTran buildApiTran(TbFirmFileMst file, String tranDt, String tranTm, String apiTranTp,
                                         BigDecimal batCnt, BigDecimal batAmt,
                                         String regRsltCd, String regRsltMsg,
                                         String rsCd, String rsMsg, String rsDttm, String tranSeq) {
        TbFirmBatApiTran row = new TbFirmBatApiTran();
        row.setTranDt(tranDt);
        row.setBizKey(file.getBizKey());
        row.setTranSeq(tranSeq != null && !tranSeq.isBlank()
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
     * Split the stored amount into [integer, decimal-portion] for the 00302 request, reproducing legacy
     * {@code StrUtil.BigDecPaser} exactly.
     */
    private String[] splitAmount(BigDecimal amt) {
        return com.kosign.wecms.domain.payment.dispatch.van.AmountSplitter.split(amt);
    }
}
