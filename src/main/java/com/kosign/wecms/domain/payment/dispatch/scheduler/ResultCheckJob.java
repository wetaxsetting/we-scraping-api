package com.kosign.wecms.domain.payment.dispatch.scheduler;

import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.domain.payment.dispatch.service.ResultCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The single firm-banking scheduled job in the confirmed TOBE — refactor of
 * legacy {@code PROCESSING_RESULT_CHECK_BATCH}. Mirrors the proven
 * {@code banking/service/ExchangeRateBatchService} shape (@Scheduled +
 * @SchedulerLock, ICT zone) and reuses the existing {@code SchedulerLockConfig}
 * + ShedLock {@code shedlock} table.
 *
 * <p><b>DISABLED BY DEFAULT (R&D safety, D4/B7) — double-gated.</b> Two
 * independent switches, both defaulting OFF, exactly like {@link StrandedRecoveryJob}
 * and {@link StuckPayFileMonitorJob}:
 * <ul>
 *   <li>the cron, {@code wecms.dispatch.result-check.cron}, defaults to {@code "-"} —
 *       Spring's "never fire" marker;</li>
 *   <li>{@code ResultCheckService.runOnce()} is itself inert unless
 *       {@code wecms.dispatch.result-check.enabled=true}.</li>
 * </ul>
 * The wiring (bean, lock, cadence) is therefore registered and verifiable, but the job
 * does nothing until an environment opens <b>both</b> gates. This prevents the refactor
 * from co-processing live {@code PROC_STAT} rows on the shared dev DB alongside the live
 * JEX batch (workspace rule: refactor is local-only R&D, no coexistence).
 *
 * <p>The {@code enabled} flag exists because a cron alone was too thin a gate for the one
 * scheduled body that both issues outbound EFS 203 traffic and finalizes payment rows: a
 * single mistyped/inherited cron variable was enough to start it. It also governs the
 * {@code StrandedRecoveryService} delegation into the same body.
 *
 * <p>Single transfer (real-time 202) and bulk reception (request-time) /
 * execution (01301-triggered) are intentionally NOT scheduled here.
 *
 * <p><b>COEXISTENCE PRECONDITION — read before setting a cron.</b> This job must
 * NOT be enabled while the legacy {@code PROCESSING_RESULT_CHECK_BATCH}
 * (in the separate {@code WECMS_BATCH DEV/} application, scheduled
 * {@code *}{@code /1 * * * *}) is still active against the same database. Both drive
 * {@code TB_FIRM_TRAN.PROC_STAT} and {@code TB_PAY_TARGET.PROC_STAT} through
 * {@code 6 → 0/3} and both issue EFS 203 inquiries; concurrently they produce
 * double status writes and inconsistent transitions on real payments. ShedLock
 * cannot prevent this — the legacy runtime does not participate in the
 * {@code shedlock} table. See
 * {@code docs/audits/scheduler-legacy-parity-audit.md} §7.
 *
 * <p><b>Lock identity is shared, not private.</b> The lock name is
 * {@link JobLockNames#DISPATCH_RESULT_CHECK}, the identity for the result-check
 * <em>body</em> rather than for this job. {@code StrandedRecoveryService}
 * delegates into {@code ResultCheckService.runOnce()} and acquires the same
 * identity programmatically, so the body cannot run twice concurrently under two
 * different names (scheduler-audit finding F-8).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultCheckJob {

    private final ResultCheckService resultCheckService;

    /**
     * Result-check pass. Cron disabled by default ({@code "-"}); when enabled it
     * runs every minute at ICT. ShedLock serialises it across refactor instances.
     *
     * <h2>{@code lockAtMostFor} derivation (PT2M → PT30M → PT60M → PT2H30M → <b>PT30M</b>)</h2>
     *
     * <p>{@code lockAtMostFor} is the crash-recovery ceiling: if the holder dies
     * mid-pass the lock frees after it. It MUST exceed the worst-case duration of a
     * <em>valid</em> pass, otherwise a still-running pass loses its lock and a
     * second node starts a concurrent one on the same {@code PROC_STAT='6'} rows.
     *
     * <p><b>What bounds a pass is now the pass deadline, not the batch size.</b> Every earlier value
     * on this line was computed as {@code batchSize × worstCaseRow}, because nothing stopped a pass
     * early. {@code ResultCheckService.passDeadline}
     * ({@code wecms.dispatch.result-check.pass-deadline}, default <b>PT10M</b>) changed that: the
     * budget is checked at the top of each row iteration and the loop breaks when it is spent, leaving
     * the remaining rows at {@code '6'} for the next pass. So:
     *
     * <pre>  worst valid pass  =  pass-deadline  +  ONE in-flight worst-case row</pre>
     *
     * <p>and the batch size drops out of the arithmetic entirely. The {@code + one row} term is
     * load-bearing and deliberate: the check is at the top of the iteration, never inside a row, so a
     * row that has already issued its 203 always runs to completion (interrupting it would leave a 203
     * audit row at {@code '6'} with an inquiry already at the gateway — see {@code passDeadline}'s
     * Javadoc).
     *
     * <h2>The arithmetic (C-11, recomputed 2026-07-31)</h2>
     * <ul>
     *   <li><b>Worst-case row = 93 s</b> — {@code EfsProperties.connectTimeoutMs} <b>30 s</b>
     *       (raised from 3 s to match legacy {@code ConnectionUtil:16}) + {@code readTimeoutMs}
     *       <b>60 s</b> (guide §3.2 + legacy {@code ConnectionUtil:17}) + {@code DispatchPacer}'s
     *       slowest bank delay <b>3 s</b> (Woori {@code 970457}). The pace belongs in the sum: a
     *       timed-out row takes the comms-fail branch, and <i>that</i> branch paces.</li>
     *   <li><b>Worst valid pass = 600 s + 93 s = 693 s ≈ 11.6 min.</b></li>
     *   <li><b>PT30M = 1800 s gives 2.6× headroom</b> for per-row DB work (a
     *       {@code wb_get_next_val} call, three {@code TB_FIRM_TRAN} saves, one
     *       {@code REQUIRES_NEW} finalize), JVM pauses and clock skew — none of which are in the
     *       93 s — and absorbs a pass-deadline raise to PT20M without needing a recompute.</li>
     * </ul>
     *
     * <p><b>Processing is still strictly sequential</b> (one {@code for} loop, one 203 per row) and
     * <b>errors still do not shorten the pass</b> (a failed row is logged, left at {@code '6'}, paced,
     * and the loop continues) — both inputs to the per-row figure above, unchanged.
     *
     * <h2>Why this is a reduction and not a regression</h2>
     * <p>PT2H30M was the honest ceiling for a pass that could legitimately grind through 100 gateway
     * timeouts. Raising the connect timeout 3 s → 30 s would have pushed that model to
     * {@code 100 × 93 s = 155 min}, i.e. <i>above</i> PT2H30M — the old literal was already short the
     * moment {@link #run()}'s inputs changed. Rather than raise it again down a path that has no upper
     * bound, the deadline caps the pass and the ceiling collapses to a fixed crash-recovery constant.
     * The cost side improves in the same step: the post-crash blind spot, during which dispatched
     * transfers awaiting confirmation sit at {@code PROC_STAT='6'} unfinalized, drops from
     * <b>150 minutes to 30</b>.
     *
     * <p>The concurrency property is unchanged — the ceiling still exceeds the worst valid pass, which
     * is the only thing it has to do. ShedLock releases the lock normally on completion, so the
     * ceiling <b>only binds on abnormal exit</b> (a JVM kill, an OOM, a pod eviction). Operationally:
     * after any abnormal termination of a node running this job, clear the {@code shedlock} row for
     * {@link JobLockNames#DISPATCH_RESULT_CHECK} manually rather than waiting the ceiling out.
     *
     * <p><b>PT30M also makes the {@code StrandedRecoveryService} delegation honest.</b> That sweep
     * borrows this lock identity with hardcoded bounds of {@code Duration.ofMinutes(30)} /
     * {@code ofSeconds(5)} and a comment claiming they "match {@code ResultCheckJob} exactly". Against
     * PT2H30M they did not — a delegated pass could lose the lock at 30 min while a scheduled pass ran
     * on to 150. They match now, and {@code StrandedRecoveryResultCheckLockTest} pins it.
     *
     * <p>The batch size is no longer part of this derivation, so <b>lowering it is no longer a lever
     * on the ceiling</b> — which is the right outcome. The cap is also the drain rate, and this sweep
     * is the only thing that finalizes a dispatched-but-unconfirmed transfer (a payment already sent
     * to the bank); trading drain rate for lock headroom optimised the wrong quantity.
     *
     * <p><b>Not yet live.</b> {@code wecms.dispatch.result-check.cron} is {@code "-"} in every
     * profile and {@code result-check.enabled} defaults false, so no environment currently holds this
     * lock at all. The window above is what would apply on the day both gates open, not a live
     * exposure today.
     *
     * <p>{@code lockAtLeastFor} stays PT5S — with a 1-minute cron the floor only needs to outlast an
     * empty/near-empty pass.
     *
     * <p><b>Recompute this ceiling when you change
     * {@code wecms.dispatch.result-check.pass-deadline}</b> (not {@code batch-size}) as
     * {@code passDeadline + connectTimeoutMs + readTimeoutMs + worstPaceMs} plus headroom, and update
     * {@code ResultCheckLockCeilingTest}, which asserts the relationship rather than the literal.
     */
    @Scheduled(cron = "${wecms.dispatch.result-check.cron:-}", zone = "Asia/Ho_Chi_Minh")
    @SchedulerLock(name = JobLockNames.DISPATCH_RESULT_CHECK,
                   lockAtMostFor = "PT30M", lockAtLeastFor = "PT5S")
    public void run() {
        log.debug("ResultCheckJob.run fired");
        resultCheckService.runOnce();
    }
}
