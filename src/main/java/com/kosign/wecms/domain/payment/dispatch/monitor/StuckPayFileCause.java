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

    /** None of the above matched — needs manual inspection. */
    UNKNOWN
}
