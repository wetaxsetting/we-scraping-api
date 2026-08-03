package com.kosign.wecms.domain.operation.scheduler;

import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.domain.operation.service.OperationStatusBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedule gate for the Operation Status Inquiry batch — refactor of the legacy
 * JEX BCS jobs {@code OPRT_0001_001} (seed) / {@code OPRT_0010_001} (refresh).
 *
 * <p>This bean carries the only {@code @Scheduled} trigger for the GDT
 * operating-status sweep and is created <b>only</b> when
 * {@code wecms.operation-status.batch.enabled=true} ({@code matchIfMissing=false}
 * → OFF by default). When the property is absent or {@code false} the bean does
 * not exist, so no cron fires.
 *
 * <p><b>Why off by default:</b> the refactor runs against the live JEX DB in every
 * environment, where the LEGACY JEX batch already maintains {@code OPRT_STTS_INFO}.
 * If the refactor also wrote it would double-write / race the legacy batch against
 * the shared DB. Collection stays legacy-owned; this gate is flipped on only in an
 * environment that genuinely owns the sweep (e.g. a future cut-over) — and only
 * after the legacy JEX batch is disabled there. Mirrors
 * {@code ExchangeRateBatchScheduler}.
 *
 * <p><b>COEXISTENCE PRECONDITION — read before enabling.</b>
 * {@code wecms.operation-status.batch.enabled=true} is safe ONLY after legacy
 * {@code OPRT_0001_001} and {@code OPRT_0010_001} are disabled in that same
 * environment. ShedLock does NOT help: the legacy JEX runtime does not participate
 * in the {@code shedlock} table. Both sides would write {@code OPRT_STTS_INFO} +
 * {@code EXT_TRAN_CALL_HIS} and issue duplicate (billable) OSMC/GDT calls. Legacy
 * was verified still running in DEV on 2026-07-27. Note also that
 * {@code OPRT_STTS_INFO} is written by the interactive JEX screen as well as the
 * batch, so disabling the legacy batch alone does not make the table
 * refactor-exclusive. See {@code docs/audits/scheduler-legacy-parity-audit.md} §7.
 *
 * <h2>Seed → refresh runs as ONE locked unit (ordering fix)</h2>
 *
 * <p>The two legacy programs were previously two {@code @Scheduled} methods with
 * two independent {@code @SchedulerLock} names and two cron properties that both
 * defaulted to the same instant. Independent locks impose no ordering and the
 * single-threaded scheduler pool picks an arbitrary order, so
 * <em>refresh could run before seed</em> — which silently loses a day of new tax
 * codes, because seed is create-only and refresh is update-only (a tax code not
 * yet seeded is skipped by refresh rather than inserted).
 *
 * <p>They are now one method, one lock ({@link JobLockNames#OPRT_STATUS_BATCH}),
 * one cron — so the order is deterministic (seed first, refresh second) and the
 * two can never overlap. The per-job semantics are untouched:
 * {@code seedAllTenants()} stays create-only and {@code refreshAllTenants()}
 * stays update-only, each with its own per-tenant transactions inside
 * {@link OperationStatusBatchService}.
 *
 * <p><b>Cron — deliberately never-fire by default.</b>
 * {@code wecms.operation-status.batch.cron} defaults to {@code "-"} (Spring's
 * never-fire marker) rather than a guessed clock time.
 * <b>The real legacy execution instant is UNRESOLVED</b> (blocking confirmation
 * item OQ-3): live JEX DEV evidence puts the last legacy run at
 * {@code 2026-07-27 15:00:01} UTC — that is 22:00 ICT, equivalently 00:00
 * {@code Asia/Seoul} — while the previous refactor placeholder assumed 00:00 ICT
 * (17:00 UTC), a two-hour miss consistent with the documented JEX Seoul/ICT skew.
 * Do NOT pick a value here. Before any UAT/PROD enablement the legacy batch owner
 * or Infra must confirm the JEX Batch Admin entry and its timezone, and that
 * confirmed value is then set via {@code WECMS_OPERATION_STATUS_BATCH_CRON}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "wecms.operation-status.batch",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class OperationStatusBatchScheduler {

    private static final String VIETNAM_TZ_ID = "Asia/Ho_Chi_Minh";

    private final OperationStatusBatchService batchService;

    /**
     * One pass of the operating-status sweep: legacy {@code OPRT_0001_001} (seed /
     * discovery, create-only) followed by legacy {@code OPRT_0010_001} (refresh /
     * update, update-only), in that order, under one lock.
     *
     * <p>Seed runs first so a tax code discovered today is inserted before refresh
     * looks for rows to update. A seed failure does NOT abort refresh — each is a
     * cross-tenant fan-out that already isolates failures per tenant, and skipping
     * refresh because seed had one bad tenant would lose a full day of status
     * updates for every healthy tenant. Both summaries are logged.
     *
     * <p>{@code lockAtMostFor} covers both legs (the previous per-leg bound was
     * PT30M each): two sequential multi-tenant OSMC/GDT fan-outs.
     */
    @Scheduled(cron = "${wecms.operation-status.batch.cron:-}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = JobLockNames.OPRT_STATUS_BATCH,
                   lockAtMostFor = "PT60M",
                   lockAtLeastFor = "PT1M")
    public void runSeedThenRefresh() {
        OperationStatusBatchService.BatchSummary seed = null;
        try {
            seed = batchService.seedAllTenants();
        } catch (RuntimeException ex) {
            // Never let a seed failure suppress refresh — see method Javadoc.
            log.error("feature=operation-status-batch seed leg failed — continuing to refresh", ex);
        }

        OperationStatusBatchService.BatchSummary refresh = null;
        try {
            refresh = batchService.refreshAllTenants();
        } catch (RuntimeException ex) {
            log.error("feature=operation-status-batch refresh leg failed", ex);
        }

        log.info("feature=operation-status-batch pass done seed={} refresh={}", seed, refresh);
    }
}
