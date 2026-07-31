package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.lock.JobLockNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedule gate for the exchange-rate collection batch (G0).
 *
 * <p>This bean carries the only {@code @Scheduled} triggers for FX scraping
 * and is created <b>only</b> when {@code wecms.exchange-rate.batch.enabled=true}
 * ({@code matchIfMissing = false} → off by default). When the property is
 * absent or {@code false} the bean does not exist, so no FX cron fires.
 *
 * <p><b>Why off by default:</b> the refactor runs against the live JEX DB in
 * every environment, where the <em>legacy</em> JEX batch already collects FX
 * into {@code BANK_EXCH_RATE_INFM}. If the refactor also collected, it would
 * double-write / race the legacy batch against the shared DB. Collection stays
 * legacy-owned; this gate is flipped on only in an environment that genuinely
 * owns FX collection (e.g. a future cut-over).
 *
 * <p><b>COEXISTENCE PRECONDITION — read before enabling.</b>
 * {@code wecms.exchange-rate.batch.enabled=true} is safe ONLY after the legacy
 * {@code EXCH_0001_001} / {@code EXCH_0010_001} / {@code EXCH_0020_001} BCS jobs
 * are disabled in that same environment. ShedLock does NOT help here: the legacy
 * JEX runtime does not participate in the {@code shedlock} table, so both sides
 * would write {@code BANK_EXCH_RATE_INFM} concurrently. The Woori leg is the
 * most dangerous — legacy and refactor both compute {@code SEQ_NO = MAX+1}
 * cross-tenant and both INSERT only, so a concurrent round silently duplicates
 * or collides. Legacy was verified still running in DEV on 2026-07-28.
 * Additionally, legacy {@code EXCH_0030_001} (multi-bank × multi-SEQ_NO
 * backfill) has NO refactor equivalent — a missed window is not self-healing
 * (blocking confirmation item OQ-2). See
 * {@code docs/audits/scheduler-legacy-parity-audit.md} §7 + §11.
 *
 * <p>The actual work + per-job {@code @Transactional} units live on
 * {@link ExchangeRateBatchService} (always present so the on-demand path and DI
 * keep working). {@code @SchedulerLock} stays co-located with {@code @Scheduled}
 * here because ShedLock runs in scheduler-proxy mode
 * ({@code @EnableSchedulerLock} in {@code SchedulerLockConfig}) — the lock is
 * applied by the scheduler proxy, so it must annotate the scheduled method, not
 * the delegate. Crons + lock names are anchored to {@code Asia/Ho_Chi_Minh}; see
 * {@link ExchangeRateBatchService} for the per-job persistence semantics.
 *
 * <p><b>Confirmed JEX Studio schedule (start/end windows):</b>
 * <ul>
 *   <li>EXCH_0001_001 — Shinhan first: 08:30:00–08:59:59 → once/day @ 08:30
 *       ({@code 0 30 8 * * ?}).</li>
 *   <li>EXCH_0010_001 — Shinhan last: 08:30:05–18:30:00, every 1 hour →
 *       11 runs/day, 08:30–18:30 ({@code 0 30 8-18 * * ?}). The refactor fires
 *       at 08:30:00, not 08:30:05 — an accepted, harmless 5-second offset.</li>
 *   <li>EXCH_0020_001 — Woori: 08:30:00–20:30:00, every 30 min → 25 runs/day,
 *       08:30, 09:00, 09:30, …, 20:00, 20:30
 *       ({@code 0 30 8 * * ?} + {@code 0 0,30 9-20 * * ?}).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "wecms.exchange-rate.batch",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class ExchangeRateBatchScheduler {

    private static final String VIETNAM_TZ_ID = "Asia/Ho_Chi_Minh";

    private final ExchangeRateBatchService batchService;

    /** EXCH_0001_001 — Shinhan first-time, once/day at 08:30 ICT. */
    @Scheduled(cron = "0 30 8 * * ?", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = JobLockNames.EXCH_SHINHAN_FIRST_TIME,
                   lockAtMostFor = "PT10M",
                   lockAtLeastFor = "PT30S")
    public void shinhanFirstTime() {
        batchService.runShinhanFirstTime();
    }

    /** EXCH_0010_001 — Shinhan last-time, hourly 08:30–18:30 ICT. */
    @Scheduled(cron = "0 30 8-18 * * ?", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = JobLockNames.EXCH_SHINHAN_LAST_TIME,
                   lockAtMostFor = "PT10M",
                   lockAtLeastFor = "PT30S")
    public void shinhanLastTime() {
        batchService.runShinhanLastTime();
    }

    /** EXCH_0020_001 — Woori, every 30 min 08:30–20:30 ICT = 25 runs/day
     *  (confirmed JEX Studio: start 08:30:00, end 20:30:00, every 30 min).
     *  Split into two crons to avoid the 08:00 fire a single {@code 8-20}
     *  pattern would trigger: the first fires 08:30, the second fires :00/:30
     *  of hours 09–20 (→ 09:00 … 20:30). */
    @Scheduled(cron = "0 30 8 * * ?",      zone = VIETNAM_TZ_ID)
    @Scheduled(cron = "0 0,30 9-20 * * ?", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = JobLockNames.EXCH_WOORI,
                   lockAtMostFor = "PT10M",
                   lockAtLeastFor = "PT20S")
    public void woori() {
        batchService.runWoori();
    }
}
