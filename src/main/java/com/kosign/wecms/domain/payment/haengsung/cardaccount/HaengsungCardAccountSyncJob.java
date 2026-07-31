package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the HAENGSUNG Oracle account-history + card-approval sync
 * ({@code haengsung-card-account-sync}, Step 7 — refactor of legacy
 * {@code ERP_0100_003}). It is ONLY a trigger: all work lives on
 * {@link HaengsungCardAccountSyncService}.
 *
 * <p><b>Gating (disabled by default):</b>
 * <ul>
 *   <li>The bean exists only when {@code haengsung.card-account-sync.enabled=true}
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
 *
 * <p><b>COEXISTENCE PRECONDITION — read before enabling.</b>
 * {@code haengsung.card-account-sync.enabled=true} is safe ONLY after legacy
 * {@code ERP_0100_003} is disabled in that environment. ShedLock does NOT help —
 * the legacy JexSimpleBatch runtime does not participate in the {@code shedlock}
 * table. Both sides push to the ERP Oracle {@code TB_ACCT_HISTORY} /
 * {@code TB_CARD_APPROVAL} and flip the source {@code ERP_SND_YN} guards, so
 * concurrent runs produce duplicate ERP history/card rows with no XA to unwind
 * them. See {@code docs/audits/scheduler-legacy-parity-audit.md} §7.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(HaengsungCardAccountSyncProperties.class)
@ConditionalOnProperty(
        prefix = "haengsung.card-account-sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class HaengsungCardAccountSyncJob {

    private static final String VIETNAM_TZ_ID = "Asia/Ho_Chi_Minh";

    /** Optional — present only when {@code haengsung.oracle.enabled=true}. */
    private final ObjectProvider<HaengsungCardAccountSyncService> serviceProvider;

    @Scheduled(cron = "${haengsung.card-account-sync.cron:"
            + HaengsungCardAccountSyncProperties.DEFAULT_CRON + "}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = com.kosign.wecms.common.lock.JobLockNames.HAENGSUNG_CARD_ACCOUNT_SYNC,
                   lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() {
        HaengsungCardAccountSyncService service = serviceProvider.getIfAvailable();
        if (service == null) {
            log.info("feature=haengsung-card-account-sync skipped — Oracle feature disabled "
                    + "(haengsung.oracle.enabled=false)");
            return;
        }
        service.sync();
    }
}
