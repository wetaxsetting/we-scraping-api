package com.kosign.wecms.domain.payment.haengsung.exchangerate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the HAENGSUNG Oracle exchange-rate sync
 * ({@code haengsung-exchange-rate-sync}, Step 6 / legacy {@code ERP_0100_001}).
 * Bound from {@code haengsung.exchange-rate-sync.*}.
 *
 * <p><b>Disabled by default.</b> {@link #enabled} is {@code false} unless set —
 * the {@link HaengsungExchangeRateSyncJob} bean is created only when it is
 * {@code true}, so no cron is registered out of the box. The sync additionally
 * no-ops unless the Step-1 Oracle feature ({@code haengsung.oracle.enabled}) is on
 * (the {@link HaengsungExchangeRateSyncService} bean is absent otherwise).
 *
 * <p><b>Cadence.</b> Legacy {@code ERP_0100_001} has no cron in source — it is
 * scheduled in Jex Studio's batch scheduler ({@code TB_BATCH_SCHEDULE_*}), so the
 * exact legacy cadence is unknown. The refactor exposes a configurable cron with a
 * conservative 5-minute default.
 */
@ConfigurationProperties(prefix = "haengsung.exchange-rate-sync")
public record HaengsungExchangeRateSyncProperties(

        /** Master switch for the scheduled exchange-rate sync job (default false). */
        boolean enabled,

        /** Spring cron expression (ICT). Default: every 5 minutes (configurable;
         *  legacy cadence is Jex-Studio-defined and not in source). */
        String cron
) {
    public static final String DEFAULT_CRON = "0 */5 * * * *";

    public HaengsungExchangeRateSyncProperties {
        if (cron == null || cron.isBlank()) {
            cron = DEFAULT_CRON;
        }
    }
}
