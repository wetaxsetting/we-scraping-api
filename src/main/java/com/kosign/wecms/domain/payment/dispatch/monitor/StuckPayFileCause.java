package com.kosign.wecms.domain.payment.dispatch.monitor;

/**
 * Best-effort classification of <i>why</i> a {@code TB_PAY_FILE_MST} row is still at
 * {@code PROC_STAT='9'} (WAIT) past the monitor threshold.
 *
 * <p>Diagnostic only — the monitor never changes a payment status. Ordering below is
 * the evaluation order used by {@code StuckPayFileMonitorService.classify}: the most
 * specific / most actionable cause wins.
 */
public enum StuckPayFileCause {

    /**
     * {@code wecms.dispatch.auto-receive=false}. Rows written while the flag is off are
     * never handed to {@code BulkReceptionService}, so they sit at WAIT indefinitely.
     * This is the current default in every environment, and it explains <b>every</b>
     * stuck row on its own — hence it is evaluated first.
     */
    DISPATCH_FLAG_DISABLED,

    /**
     * The pay-file has no {@code TB_PAY_FILE_DATA} rows at all. {@code BulkReceptionService}
     * throws on an empty detail set, so such a file can never progress.
     */
    NO_DETAIL_ROWS,

    /**
     * Every detail row has a null / blank {@code ANT_DIV_CD}. The channel set is empty,
     * so {@code OperatingHoursService.evaluateBulk} returns {@code SKIP} and the file is
     * left WAIT with no FAIL and no alert.
     *
     * <p>Reachable via the two <b>intentional legacy NULL branches</b> (VCB foreign
     * currency; Woori VND same-bank) — see F-1 §9 of
     * {@code docs/audits/payment-dispatch-operating-hours-flow.md}.
     */
    ALL_CHANNELS_NULL,

    /**
     * At least one detail row carries an {@code ANT_DIV_CD} that is not one of the five
     * recognised storage codes {@code E/V/C/N/S}. Such a code is dropped from the channel
     * set (legacy {@code ETC_CNT}, deliberately un-gated), which can empty the set.
     *
     * <p>Reachable via the HAENGSUNG ERP raw-passthrough path
     * ({@code ErpPaymentRefreshMasterImporter.mapAntDivCd}, documented unresolved {@code 'T'}).
     */
    UNKNOWN_CHANNEL_CODE,

    /**
     * Channels resolve, but re-evaluating the gate read-only right now still yields
     * {@code SKIP} — i.e. the file is genuinely outside its operating window (before a
     * channel's start time). Legitimate and self-healing once the window opens; only a
     * concern if it persists across the window.
     */
    OPERATING_HOURS_SKIP,

    // ── TB_FIRM_FILE_MST at PROC_STEP='0' / PROC_STAT='6' — reception never reconciled ───────────
    // Added 2026-07-31. This state was previously invisible to every sweep: the parent pay-file is
    // often already terminal (so both pay-file sweeps skip it) and the firm-file sweep filtered
    // PROC_STEP='1' and '2' only. It arises two ways, and two-phase dispatch made the second routine:
    //   * a LOST 01301 — the 00301 was accepted, the bank has the file, the reception result never
    //     came back (JEX DEV: 27 of 30 rows at this state carry a 00301 audit row);
    //   * a PARTIAL phase-B failure — when dispatch breaks at file k of N, files k+1..N keep committed
    //     rows at '0'/'6' that were never built to SFTP and never sent.
    // The two need opposite handling, which is why they are two constants.

    /**
     * Stranded at {@code PROC_STEP='0' / PROC_STAT='6'} <b>with</b> a {@code 00301}
     * ({@code API_TRAN_TP}) audit row — the reception request was issued.
     *
     * <p><b>The bank has the file.</b> The SFTP upload precedes the {@code 00301} call and the audit row
     * is written before it too, so an audit row means the instruction went out; only our copy of the
     * result is missing. The {@code 01301} was lost (gateway-side), or arrived and could not be matched.
     *
     * <p><b>Never re-drive.</b> Re-issuing {@code 00301} for a file the bank already accepted
     * double-instructs a reception. Reconcile forward: confirm the batch's per-line state with the VAN
     * Center, then correct {@code TB_FIRM_FILE_MST} / {@code TB_PAY_FILE_MST} to match what actually
     * happened. Procedure: {@code docs/deployment/bulk-file-reconciliation-procedure.md} §5.3 (answer
     * <b>C</b> is the expected one for this cause).
     *
     * <p>The audit row's {@code RS_CD} is the starting point — it records what the gateway answered the
     * {@code 00301} with, which narrows "accepted then lost" against "never acknowledged".
     */
    BULK_RECEPTION_RESULT_NOT_RECEIVED,

    /**
     * Stranded at {@code PROC_STEP='0' / PROC_STAT='6'} with <b>no</b> {@code 00301} audit row — the
     * reception request was never issued.
     *
     * <p><b>Nothing left the building.</b> The audit row is written immediately before the {@code 00301}
     * call ({@code BulkReceptionService:799-807}), so its absence means the call was never reached: the
     * staging write or the SFTP upload failed, or the process died between the phase-A commit and the
     * upload. The canonical case since 2026-07-31 is the <b>phase-B partial failure</b> — dispatch broke
     * at an earlier sibling and {@code break}s out of the loop, leaving the remaining prepared files at
     * this state, unbuilt and unsent.
     *
     * <p><b>Safe to re-dispatch — after checking the parent.</b> The file itself can be re-driven, but
     * only if the batch as a whole should still go: when the parent {@code TB_PAY_FILE_MST} is
     * {@code PROC_STAT='3'} an earlier sibling failed and the batch was abandoned, so re-sending this one
     * would part-execute it. That case is classified {@link #BULK_BATCH_PARENT_FAILED} instead and never
     * reaches this constant. Procedure: {@code bulk-file-reconciliation-procedure.md} §5.3 (answers
     * <b>A</b> / <b>B</b>).
     */
    BULK_RECEPTION_NOT_ISSUED,

    /**
     * A firm file stranded at {@code PROC_STEP='0'} or {@code '1'} (with {@code PROC_STAT='6'}) whose
     * <b>parent {@code TB_PAY_FILE_MST} is already {@code PROC_STAT='3'} (FAIL)</b> — an earlier sibling
     * in the same branch-split batch failed and the batch was abandoned.
     *
     * <h2>Why this needs its own constant</h2>
     * Without it, such a file classified as {@link #BULK_EXECUTION_NOT_ISSUED} or
     * {@link #BULK_RECEPTION_NOT_ISSUED} — and both of those tell the operator that re-driving is the
     * routine action. It is not: {@code BulkReceptionService.buildAndDispatchAllFiles} marks the pay-file
     * FAIL and {@code break}s on the first phase-B failure, so the siblings after the failure point were
     * never sent at all. Re-driving one file of an abandoned batch <b>part-executes a payment the system
     * has already reported as failed</b> — money moves for some lines and not others, with the UI saying
     * the whole thing failed. That is a worse outcome than the strand.
     *
     * <p><b>Never re-drive a single file on this cause.</b> The unit of decision is the batch: establish
     * with the VAN Center which siblings the bank actually received and executed, then either re-create
     * the payment cleanly or reconcile the partial forward. Procedure:
     * {@code bulk-file-reconciliation-procedure.md} §5.6.
     *
     * <p>Deliberately <b>not</b> applied to {@code PROC_STEP='2'} — a file there had its {@code 00302}
     * accepted, so {@link #BULK_EXECUTION_RESULT_NOT_RECEIVED} carries the stronger and more specific
     * warning ("the bank has most likely executed this batch"), and overriding it with the parent's
     * state would lose that information.
     */
    BULK_BATCH_PARENT_FAILED,

    /**
     * A {@code TB_FIRM_FILE_MST} row is stranded at {@code PROC_STEP='1' / PROC_STAT='6'} past the
     * threshold and <b>no {@code 00302} was ever issued</b> — no {@code TB_FIRM_BAT_API_TRAN} row
     * with {@code API_TRAN_TP='00302'} exists for the file.
     *
     * <p>Diagnosis: the inline issue never ran. Three ways to get here:
     * <ul>
     *   <li>a <b>lost {@code 01301} callback</b> — the reception result never arrived, so nothing
     *       advanced the file;</li>
     *   <li>an <b>application restart between the reception commit and the 00302 fan-out</b>. The
     *       fan-out runs in an {@code afterCommit} hook ({@code BulkReceptionResultService}), which is
     *       an in-JVM callback and not a persisted outbox, so a process death in that window leaves the
     *       reception durable and the instruction unsent. This is the documented residual of that design
     *       and <b>this cause is its only detection</b>;</li>
     *   <li>the file was re-read at fan-out time and found no longer reception-success (a concurrent
     *       callback or an operator moved it), so {@code issueOne} skipped it.</li>
     * </ul>
     *
     * <p>Note that a gateway <b>retry</b> of the lost 01301 does not rescue the second case: the file is
     * past {@code PROC_STEP='0'}, so the retry is classified as an already-processed replay and acked
     * {@code RC=0000}. That is the accepted trade — a duplicate 00302 would risk double-executing a bulk
     * payment, which is worse than a stranded one — and it is why this cause must be watched.
     *
     * <p>Legacy self-healed this within 60 s, because {@code FILE_DEPOSIT_TRANSFER_BATCH2} re-picked
     * the row on its next poll. The approved TO-BE removes that batch and replaces it with inline
     * execution from the callback, leaving this residual edge to <b>monitoring + manual recovery</b> —
     * which is what this cause exists to surface. There is deliberately <b>no automatic retry</b>
     * (K9: Option A "Recovery Scheduler" was offered and not selected).
     *
     * <p><b>Operationally significant:</b> unlike every cause above, the file is already at the bank
     * via {@code 00301}. This is not a "nothing happened yet" state.
     */
    BULK_EXECUTION_NOT_ISSUED,

    /**
     * Same stranded state, but a {@code 00302} audit row <b>does</b> exist — the execution
     * instruction was sent and the file still did not advance to {@code PROC_STEP='2'}.
     *
     * <p>Diagnosis: the gateway did not confirm (empty body, or {@code BT01}/{@code G999}/{@code C999}),
     * or the confirmation was lost. The operator must establish with the VAN Center <b>whether the bank
     * executed the batch</b> before doing anything — the audit row's {@code RS_CD} is the starting
     * point, and re-issuing blindly could double-send.
     *
     * <p>Distinguishing this from {@link #BULK_EXECUTION_NOT_ISSUED} is the whole diagnostic value of
     * this sweep: the two states need opposite manual actions.
     */
    BULK_EXECUTION_UNCONFIRMED,

    /**
     * A {@code TB_FIRM_FILE_MST} row is stranded at {@code PROC_STEP='2' / PROC_STAT='6'} past the
     * threshold — the {@code 00302} execution instruction was <b>accepted</b> and the {@code 01302}
     * execution <b>result</b> never arrived.
     *
     * <h2>Why this is not {@link #BULK_EXECUTION_UNCONFIRMED}</h2>
     * For the two causes above, what the bank did must be <i>inferred</i> from an audit row. Here it
     * does not: the only code path that writes {@code PROC_STEP='2'} is
     * {@code BulkExecutionInlineTxService}, reached <b>only</b> after the {@code 00302} returned
     * {@code 0000}/{@code BT00}. The advanced step is therefore <b>positive evidence the gateway
     * accepted the instruction</b> — the bank has almost certainly executed the batch. What is missing
     * is only our copy of the per-line outcome.
     *
     * <p>The dispositions are consequently <b>opposite</b> to {@link #BULK_EXECUTION_NOT_ISSUED},
     * whose default is "re-drive is usually correct". Re-driving a {@code PROC_STEP='2'} file would
     * re-instruct a batch the bank already ran. <b>There is no correct re-drive from this state</b> —
     * only reconcile-forward or escalate. That is why it needs its own constant rather than being
     * folded into an existing one.
     *
     * <h2>Two variants — read the {@code 01302} audit row</h2>
     * <ul>
     *   <li><b>No {@code 01302} audit row</b> — the callback never arrived (gateway-side loss).</li>
     *   <li><b>An {@code 01302} audit row exists</b> — it arrived and our handler rolled back.
     *       {@code BulkExecutionResultService} deliberately leaves the file at {@code '2'} when the
     *       SFTP result-file download or the fixed-length parse fails, so the gateway can retry. If the
     *       cause is persistent (file absent, wrong remote path, malformed), every retry rolls back
     *       identically and the file stays at {@code '2'} forever. <b>This variant is our defect</b> and
     *       is diagnosable from our own logs without involving the bank.</li>
     * </ul>
     * Probe that audit row with a <b>prefix-stripped</b> name match: legacy stores the {@code 01302}
     * audit row under the callback's {@code R_} name ({@code API2_act.jsp:189}) while the refactor
     * stores the sent {@code S_} name, so an exact-name probe returns 0 for every legacy-written row
     * regardless of whether a callback arrived.
     *
     * <p><b>No automatic retry (K9 stands).</b> Detection and the manual procedure are the whole
     * mitigation, exactly as for the two causes above.
     */
    BULK_EXECUTION_RESULT_NOT_RECEIVED,

    // ── H-3 / N-3 · pay-file stranded at PROC_STAT='6' (claimed, never finished) ──────────────
    // These three describe the population the H-4 claim created: the claim commits '9'→'6' in its own
    // transaction before any file work, so a failure in between leaves a committed '6'. The WAIT-only
    // sweeps above cannot see it. They are ordered least-to-most ambiguous, because that is also
    // least-to-most dangerous to act on.

    /**
     * Pay-file at {@code PROC_STAT='6'} with <b>no {@code TB_FIRM_FILE_MST} row at all</b>.
     *
     * <p>Diagnosis: the claim committed and then the split-file transaction rolled back before (or
     * while) inserting the firm-file row — a pre-egress build failure, or a crash between the two.
     *
     * <p><b>Nothing left the building.</b> No file was written, no SFTP upload happened, no {@code 00301}
     * was sent. This is the one state in this group that is safe to re-drive after resetting the row to
     * WAIT, and the safest to reason about.
     *
     * <p>Since 2026-07-29 the fail-closed guards in {@code BulkReceptionService} mark such a file
     * {@code '3'} directly, so a row appearing here means the terminal write itself did not run — a hard
     * process kill, or a DB failure during the failure write.
     */
    CLAIMED_NO_FIRM_FILE,

    /**
     * Pay-file at {@code PROC_STAT='6'}, a firm-file row exists at {@code PROC_STEP='0'}, and <b>no
     * {@code 00301} audit row records success</b>.
     *
     * <p>Diagnosis: the file was built and the firm-file row committed, but the reception request did
     * not complete. <b>Ambiguous by construction:</b> the SFTP upload happens before the {@code 00301}
     * call, so the file may already be sitting on the bank's server even though no instruction was
     * accepted.
     *
     * <p><b>Do not blindly re-drive.</b> Re-uploading is usually harmless (same deterministic
     * {@code FIRM_FILE_NM}), but re-issuing {@code 00301} for a file the bank did accept could
     * double-instruct. Reconcile the {@code TB_FIRM_BAT_API_TRAN} row with the VAN Center first.
     */
    RECEPTION_UNCONFIRMED,

    /**
     * Pay-file at {@code PROC_STAT='6'} whose firm-file row has advanced past {@code PROC_STEP='0'} —
     * the file is inside the {@code 01301} → {@code 00302} → {@code 01302} callback pipeline and the
     * pay-file simply has not been finalised yet.
     *
     * <p>Often <b>not</b> a defect: {@code BulkExecutionResultService.finalizePayFileStatus} only
     * finalises the pay-file once every split sibling has reported, so a partially-reported branch-split
     * batch sits here legitimately. It becomes a concern when it persists well past the threshold, which
     * is what surfacing it here is for.
     *
     * <p><b>Never re-drive.</b> The batch is at the bank. The firm-file sweep's
     * {@link #BULK_EXECUTION_NOT_ISSUED} / {@link #BULK_EXECUTION_UNCONFIRMED} causes carry the
     * actionable per-file detail; this cause exists so the pay-file side is not silently unaccounted for.
     */
    AWAITING_EXECUTION_RESULT,

    // ── TB_PAY_TARGET · real-time (202) rail stranded at PROC_STAT='9' ───────────────────────────
    // A different table, rail and timescale from everything above: RealtimeDispatchRegistrar hands a
    // committed row to the 202 within milliseconds of commit, so a WAIT row past its (short) grace
    // window has NOT simply "not started yet" — something returned early or threw. These four say which.

    /**
     * {@code wecms.dispatch.realtime-dispatch=false}. Rows written while the flag is off are never
     * handed to {@code RealtimeDispatchService}, so they sit at WAIT indefinitely.
     *
     * <p>The real-time twin of {@link #DISPATCH_FLAG_DISABLED}, and evaluated first on the pay-target
     * sweep for the same reason: it explains every stranded row on its own. Separate constant because
     * the two flags are separate ({@code auto-receive} gates bulk reception, {@code realtime-dispatch}
     * gates the 202), and reporting a real-time strand under the bulk flag's name would send an operator
     * to the wrong switch.
     */
    REALTIME_DISPATCH_FLAG_DISABLED,

    /**
     * A {@code TB_PAY_TARGET} row at {@code PROC_STAT='9'} for which a {@code TB_FIRM_TRAN} row
     * <b>already exists</b> on the same {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ)} key — so
     * {@code RealtimeDispatchService.dispatch} step 2 ({@code :133}) short-circuits as "already
     * dispatched" on every re-drive and the row can never progress without manual intervention.
     *
     * <h2>This cannot arise from a refactor-written row, and that is the point</h2>
     * The step-2 guard probes the <b>pay-target's</b> {@code TRAN_SEQ}, which only the {@code 202} row
     * reuses ({@code RealtimeDispatchService:263}); the {@code 204} FX pre-step allocates its own
     * independent sequence ({@code :355}, legacy {@code ccy_exc_seq} at
     * {@code NAPAS_REAL_TIME_BATCH:299}) and therefore never trips it. And the {@code '9'→'6'} claim
     * ({@code claimForDispatch}) shares {@code dispatch()}'s single {@code REQUIRES_NEW} transaction with
     * the {@code 202} insert, so the two commit or roll back together: <b>on our own writes a 202 row
     * implies the target left WAIT.</b>
     *
     * <p>Reaching this cause therefore means the row came from somewhere else:
     * <ul>
     *   <li><b>A legacy-JEX-written row on a shared DB.</b> Legacy has no claim step — it inserts the
     *       {@code 202} firm-tran row ({@code :496}) and updates {@code TB_PAY_TARGET} later — so a crash
     *       between the two leaves exactly this shape. Legacy's own next tick then <b>re-sends the
     *       202</b>, because {@code NAPAS_REAL_TIME_BATCH} reads no {@code TB_FIRM_TRAN} and has no
     *       exists-guard at all. Our permanent block is the <i>safer</i> behaviour, not a defect.</li>
     *   <li><b>A manual {@code PROC_STAT} edit</b> back to {@code '9'} after a transfer was sent.</li>
     * </ul>
     *
     * <p><b>Never re-drive on this cause.</b> The firm-tran row's {@code TRAN_TP} / {@code RS_CD} /
     * {@code PROC_STAT} say what the bank was told; a {@code '202'} row means a transfer instruction
     * exists and may have moved money. Reconcile with the VAN Center, then correct the pay-target
     * forward — do not reset it to WAIT.
     *
     * <p><b>Correction of record (2026-07-31).</b> This constant shipped earlier the same day documented
     * as the Woori 204 pre-step blocking its own retry. That was wrong on the mechanism — the 204's
     * sequence is independent, so no block occurs, and both legacy and the refactor retry the 204 freely
     * (accumulating one audit row per attempt, which is legacy-faithful). The observable this cause
     * reports is unchanged; only the explanation was incorrect.
     */
    REALTIME_DISPATCHED_NOT_CLAIMED,

    /**
     * No {@code TB_FIRM_CONF} row resolves for the pay-target's
     * {@code (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)}.
     *
     * <p>{@code RealtimeDispatchService} throws {@code IllegalStateException} on this, which the
     * after-commit registrar catches and logs, leaving the row at WAIT. It will fail identically on
     * every re-drive, so this is a <b>configuration</b> fix (register the firm-banking settings for that
     * withdrawal account), not a retry.
     *
     * <p>Safe to re-drive once the row exists: nothing was sent.
     */
    REALTIME_NO_FIRM_CONF,

    /**
     * The real-time operating-hours gate currently returns {@code SKIP} for the row's
     * {@code (OUT_BANK_CD, ANT_DIV_CD)} — the window has not opened yet.
     *
     * <p>Legitimate at the moment of evaluation, and the real-time twin of
     * {@link #OPERATING_HOURS_SKIP}. But note it is <b>not self-healing in the approved TO-BE</b>: the
     * after-commit hand-off already ran and returned, and no scheduled job re-drives the 202 unless
     * {@code wecms.dispatch.recovery.enabled=true}. So a row that lands outside its window stays at WAIT
     * <i>after</i> the window opens too. Persisting past the window start is therefore the signal, and
     * the reason this cause is reported rather than filtered out.
     */
    REALTIME_OPERATING_HOURS_SKIP,

    /** None of the above matched — needs manual inspection. */
    UNKNOWN
}
