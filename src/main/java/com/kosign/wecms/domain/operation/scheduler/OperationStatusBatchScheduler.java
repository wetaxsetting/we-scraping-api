package com.kosign.wecms.domain.operation.scheduler;

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
 * <p>This bean carries the only {@code @Scheduled} triggers for the GDT
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
 * <p>The actual work + per-tenant {@code @Transactional} units live on
 * {@link OperationStatusBatchService} (always present so DI / any on-demand path
 * keep working). {@code @SchedulerLock} stays co-located with {@code @Scheduled}
 * because ShedLock runs in scheduler-proxy mode — the lock is applied by the
 * scheduler proxy, so it must annotate the scheduled method, not the delegate.
 *
 * <p><b>Cron.</b> Both crons are externalised
 * ({@code wecms.operation-status.batch.seed-cron} / {@code .refresh-cron}, anchored
 * to {@code Asia/Ho_Chi_Minh}). They match the confirmed legacy JEX Batch Admin
 * schedule — run once a day, every day, at 00:00:00 ICT ({@code 0 0 0 * * ?}).
 * Nothing fires while the gate is OFF.
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

    /** OPRT_0001_001 — seed / discovery. Legacy Batch Admin: daily @ 00:00 ICT. */
    @Scheduled(cron = "${wecms.operation-status.batch.seed-cron:0 0 0 * * ?}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = "oprt-status-seed",
                   lockAtMostFor = "PT30M",
                   lockAtLeastFor = "PT1M")
    public void seed() {
        batchService.seedAllTenants();
    }

    /** OPRT_0010_001 — refresh / update. Legacy Batch Admin: daily @ 00:00 ICT. */
    @Scheduled(cron = "${wecms.operation-status.batch.refresh-cron:0 0 0 * * ?}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = "oprt-status-refresh",
                   lockAtMostFor = "PT30M",
                   lockAtLeastFor = "PT1M")
    public void refresh() {
        batchService.refreshAllTenants();
    }
}
