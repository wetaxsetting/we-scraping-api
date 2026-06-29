package com.kosign.wecms.domain.payment.haengsung.exchangerate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the HAENGSUNG Oracle exchange-rate sync
 * ({@code haengsung-exchange-rate-sync}, Step 6 — refactor of legacy
 * {@code ERP_0100_001}). It is ONLY a trigger: all work + the per-row/per-company
 * units live on {@link HaengsungExchangeRateSyncService}.
 *
 * <p><b>Gating (disabled by default):</b>
 * <ul>
 *   <li>The bean exists only when {@code haengsung.exchange-rate-sync.enabled=true}
 *       ({@code matchIfMissing=false}) — with the default the cron is never even
 *       registered.</li>
 *   <li>It additionally no-ops when the Step-1 Oracle feature is off
 *       ({@code haengsung.oracle.enabled=false}): the sync-service bean is absent,
 *       so {@code serviceProvider.getIfAvailable()} returns {@code null} and
 *       {@link #run()} logs and returns.</li>
 * </ul>
 *
 * <p>{@code @SchedulerLock} co-located with {@code @Scheduled} (ShedLock proxy
 * mode) serialises the job across instances; the cron is anchored to ICT. No
 * Oracle credentials are logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(HaengsungExchangeRateSyncProperties.class)
@ConditionalOnProperty(
        prefix = "haengsung.exchange-rate-sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class HaengsungExchangeRateSyncJob {

    private static final String VIETNAM_TZ_ID = "Asia/Ho_Chi_Minh";

    /** Optional — present only when {@code haengsung.oracle.enabled=true}. */
    private final ObjectProvider<HaengsungExchangeRateSyncService> serviceProvider;

    @Scheduled(cron = "${haengsung.exchange-rate-sync.cron:"
            + HaengsungExchangeRateSyncProperties.DEFAULT_CRON + "}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = "HaengsungExchangeRateSyncJob.run",
                   lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() {
        HaengsungExchangeRateSyncService service = serviceProvider.getIfAvailable();
        if (service == null) {
            log.info("feature=haengsung-exchange-rate-sync skipped — Oracle feature disabled "
                    + "(haengsung.oracle.enabled=false)");
            return;
        }
        service.sync();
    }
}
