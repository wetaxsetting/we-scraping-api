package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the HAENGSUNG Oracle account-history + card-approval sync
 * ({@code haengsung-card-account-sync}, Step 7 / legacy {@code ERP_0100_003}).
 * Bound from {@code haengsung.card-account-sync.*}.
 *
 * <p><b>Disabled by default.</b> {@link #enabled} is {@code false} unless set —
 * the {@link HaengsungCardAccountSyncJob} bean is created only when it is
 * {@code true}, so no cron is registered out of the box. The sync additionally
 * no-ops unless the Step-1 Oracle feature ({@code haengsung.oracle.enabled}) is on
 * (the {@link HaengsungCardAccountSyncService} bean is absent otherwise).
 *
 * <p><b>Cadence.</b> Legacy {@code ERP_0100_003} has no cron in source — it is
 * scheduled in Jex Studio's batch scheduler ({@code TB_BATCH_SCHEDULE_*}), so the
 * exact legacy cadence is unknown. The refactor exposes a configurable cron with a
 * conservative 5-minute default.
 */
@ConfigurationProperties(prefix = "haengsung.card-account-sync")
public record HaengsungCardAccountSyncProperties(

        /** Master switch for the scheduled card/account sync job (default false). */
        boolean enabled,

        /** Spring cron expression (ICT). Default: every 5 minutes (configurable;
         *  legacy cadence is Jex-Studio-defined and not in source). */
        String cron
) {
    public static final String DEFAULT_CRON = "0 */5 * * * *";

    public HaengsungCardAccountSyncProperties {
        if (cron == null || cron.isBlank()) {
            cron = DEFAULT_CRON;
        }
    }
}
