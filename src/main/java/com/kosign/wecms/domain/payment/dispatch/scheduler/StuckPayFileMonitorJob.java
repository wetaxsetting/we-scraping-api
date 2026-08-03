package com.kosign.wecms.domain.payment.dispatch.scheduler;

import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.domain.payment.dispatch.monitor.StuckPayFileMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled driver for the read-only stuck-pay-file monitor. Same shape as
 * {@link StrandedRecoveryJob} / {@link ResultCheckJob} — {@code @Scheduled} + {@code @SchedulerLock},
 * ICT zone, shared ShedLock table.
 *
 * <p><b>ON by default in {@code sit} and {@code uat}; OFF in {@code local} and {@code prod}.</b>
 * The cron resolves from {@code wecms.dispatch.stuck-monitor.cron} and the body from
 * {@code wecms.dispatch.stuck-monitor.enabled}; both properties appear in all four
 * {@code application-*.yml} profiles bound to an environment variable with an explicit default, so an
 * unset variable can never abort startup (readiness finding H-1). SIT/UAT default to
 * {@code enabled=true} + {@code cron=0 *}{@code /5 * * * *}; local/prod keep {@code false} + {@code "-"}
 * (Spring's never-fire marker).
 *
 * <p>SIT/UAT were flipped on deliberately (C-14 / batch-1 follow-up): this read-only sweep is the only
 * detection for the two post-dispatch states that resolve solely by operator action —
 * {@code BULK_EXECUTION_UNCONFIRMED} (transient 00302 failure; marker committed, nothing re-drives it)
 * and {@code BULK_EXECUTION_NOT_ISSUED} (the afterCommit residual). See
 * {@code docs/deployment/bulk-file-reconciliation-procedure.md} for what an operator does with each.
 * If you add a new gate here, give it an in-yaml default too — absence is not the mechanism.
 *
 * <p><b>Why this is a separate job from {@link StrandedRecoveryJob}.</b> The recovery sweep
 * <i>re-drives</i> rows and is therefore gated behind {@code wecms.dispatch.recovery.enabled}, which
 * is coexistence-gated and must stay OFF until the shared-DB question is resolved (PO / Mr. Kang).
 * Monitoring is read-only and must be enableable <i>without</i> opening that mutating gate —
 * sharing the job would tie observability to the one flag that has to remain closed. Everything
 * shareable <i>is</i> shared: the {@code findStrandedPayFiles} finder, the age-window
 * eligibility rule, the ShedLock table, and {@code OpsAlertService}.
 *
 * <p>{@code lockAtMostFor} is shorter than the recovery job's (this sweep is two reads per stuck
 * file, no network I/O), and {@code lockAtLeastFor} matches so a fast sweep cannot be re-run by a
 * second node inside the same window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StuckPayFileMonitorJob {

    private final StuckPayFileMonitorService stuckPayFileMonitorService;

    @Scheduled(cron = "${wecms.dispatch.stuck-monitor.cron:-}", zone = "Asia/Ho_Chi_Minh")
    @SchedulerLock(name = JobLockNames.DISPATCH_STUCK_PAY_FILE_MONITOR,
                   lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void run() {
        log.debug("StuckPayFileMonitorJob.run fired");
        stuckPayFileMonitorService.monitorOnce();
    }
}
