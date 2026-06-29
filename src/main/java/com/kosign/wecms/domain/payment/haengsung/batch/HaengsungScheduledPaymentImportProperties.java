package com.kosign.wecms.domain.payment.haengsung.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the HAENGSUNG scheduled Oracle payment-import batch
 * ({@code haengsung-scheduled-payment-import}, Step 3 / legacy
 * {@code ERP_0100_002}). Bound from {@code haengsung.scheduled-payment-import.*}.
 *
 * <p><b>Disabled by default.</b> {@link #enabled} is {@code false} unless set —
 * the {@link HaengsungScheduledPaymentImportJob} bean is created only when it is
 * {@code true}, so no cron is registered out of the box. The batch additionally
 * no-ops unless the Step-1 Oracle feature ({@code haengsung.oracle.enabled}) is
 * on (the shared import service bean is absent otherwise).
 */
@ConfigurationProperties(prefix = "haengsung.scheduled-payment-import")
public record HaengsungScheduledPaymentImportProperties(

        /** Master switch for the scheduled import job (default false). */
        boolean enabled,

        /** Spring cron expression (ICT). Default: every 5 minutes. */
        String cron
) {
    /** Legacy {@code ERP_0100_002} cadence (~5 min). */
    public static final String DEFAULT_CRON = "0 */5 * * * *";

    public HaengsungScheduledPaymentImportProperties {
        if (cron == null || cron.isBlank()) {
            cron = DEFAULT_CRON;
        }
    }
}
