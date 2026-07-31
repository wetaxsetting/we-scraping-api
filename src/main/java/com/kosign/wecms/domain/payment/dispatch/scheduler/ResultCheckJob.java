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
     * <h2>{@code lockAtMostFor} derivation (PT2M → PT30M → <b>PT60M</b>)</h2>
     *
     * <p>{@code lockAtMostFor} is the crash-recovery ceiling: if the holder dies
     * mid-pass the lock frees after it. It MUST exceed the worst-case duration of a
     * <em>valid</em> pass, otherwise a still-running pass loses its lock and a
     * second node starts a concurrent one on the same {@code PROC_STAT='6'} rows.
     * The inputs, all verified in code:
     * <ul>
     *   <li><b>Rows per pass: capped at {@code wecms.dispatch.result-check.batch-size}</b>
     *       (default <b>100</b>, {@code ResultCheckService.DEFAULT_BATCH_SIZE}), applied as
     *       {@code PageRequest.of(0, limit)} on {@code findResultCheckCandidates}. This is the H-2
     *       change; the paragraph that used to stand here described the pre-H-2 unbounded
     *       {@code findByTranTpAndProcStat} finder and was stale. A non-positive configured value
     *       falls back to the same 100, so the cap can never be disabled by misconfiguration.</li>
     *   <li><b>Processing is strictly sequential</b> — one {@code for} loop over
     *       the aged rows, one 203 inquiry each.</li>
     *   <li><b>Per-row gateway worst case ≈ 23 s</b> — {@code EfsProperties}
     *       {@code connectTimeoutMs=3000} + {@code readTimeoutMs=20000}.</li>
     *   <li><b>Per-row pacing</b> — {@code DispatchPacer}: Woori {@code 970457}
     *       3000 ms, Shinhan / default 100 ms. Applied on the skip / comms-fail /
     *       original-fail branches, not after a clean success — and a timed-out row takes the
     *       comms-fail branch, so the worst case pays the pacing too.</li>
     *   <li><b>Errors do not shorten the pass</b> — a failed row is logged, left at
     *       {@code '6'} for the next pass, paced, and the loop continues.</li>
     *   <li><b>Per-row DB work</b> — a {@code wb_get_next_val} call plus three
     *       {@code TB_FIRM_TRAN} saves and one {@code REQUIRES_NEW} finalize transaction. Small
     *       next to the gateway timeout, but it is additive, not free.</li>
     * </ul>
     *
     * <h2>C-11 — the arithmetic, and why PT30M did not cover it</h2>
     *
     * <p>Worst case per row = 3 s connect + 20 s read + 3000 ms Woori pace = <b>26 s</b>.
     * At the default cap: {@code 100 × 26 s = 2600 s = 43.3 min}. PT30M was <b>13 minutes short</b>
     * of a single valid worst-case pass, so a slow gateway could expire the lock mid-run and let a
     * second instance start on rows the first was still processing — the exact concurrency this lock
     * exists to prevent, on live payment rows.
     *
     * <p>Typical load is nowhere near that (a healthy pass is ~1 s of gateway time plus pacing per
     * row, so 100 Woori rows ≈ 7 min), but {@code lockAtMostFor} must bound the <b>worst</b> valid
     * pass, not the typical one.
     *
     * <p><b>Chosen: raise the ceiling to PT60M, and derive it from the cap</b> — which is what the
     * previous revision of this Javadoc itself instructed ("if the aged-row set is ever bounded,
     * revisit this value and derive it from the cap"); H-2 added the cap and nobody came back. 60 min
     * gives 43.3 min of worst-case work ~38% headroom for the per-row DB work, JVM pauses and clock
     * skew.
     *
     * <p><b>Why not lower the batch size instead</b> (≈65 rows would fit PT30M): the cap is also the
     * drain rate, and this sweep is the only thing that finalizes a dispatched-but-unconfirmed
     * transfer — a payment already sent to the bank. Slowing the drain to fit a lock ceiling
     * optimises the wrong quantity. The cost of a larger ceiling is bounded and one-sided: a longer
     * stale-lock window after an <i>actual</i> node crash (ShedLock releases the lock normally on
     * completion, so the ceiling only matters on abnormal termination), versus concurrent processing
     * of live payment rows if it is too small. {@code lockAtLeastFor} stays PT5S — with a 1-minute
     * cron the floor only needs to outlast an empty/near-empty pass.
     *
     * <p><b>These two values are coupled. If you change
     * {@code wecms.dispatch.result-check.batch-size}, recompute this ceiling</b> as
     * {@code batchSize × (connectTimeoutMs + readTimeoutMs + worstPaceMs)} plus headroom, and update
     * {@code ResultCheckLockCeilingTest}, which asserts the relationship rather than the literal.
     */
    @Scheduled(cron = "${wecms.dispatch.result-check.cron:-}", zone = "Asia/Ho_Chi_Minh")
    @SchedulerLock(name = JobLockNames.DISPATCH_RESULT_CHECK,
                   lockAtMostFor = "PT60M", lockAtLeastFor = "PT5S")
    public void run() {
        log.debug("ResultCheckJob.run fired");
        resultCheckService.runOnce();
    }
}
