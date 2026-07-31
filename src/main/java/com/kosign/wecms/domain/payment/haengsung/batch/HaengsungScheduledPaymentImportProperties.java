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
 *
 * <h2>Cadence — legacy schedule confirmed in source (2026-07-28)</h2>
 *
 * <p>{@code ERP_0100_002} is a JexSimpleBatch job whose schedule is committed at:
 *
 * <pre>
 *   WECMS DEV/WECMS/web/WEB-INF/jex/simpleBatch/WECMS/ERP_0100_002.batch
 *     name=ERP to WeCMS
 *     batchClass=batch.jex.wabooks.erp.ERP_0100_002
 *     schedule=*&#47;5 7-19 * * *         # 5-field cron: min hour dom mon dow
 * </pre>
 *
 * <p>The 5-minute cadence previously assumed here was correct, but the
 * <b>hour window was not</b> — the old default ran 24 h whereas legacy runs only
 * during hours 07–19. {@link #DEFAULT_CRON} now carries the window too.
 *
 * <p><b>Unconfirmed — the timezone of the legacy {@code 7-19} window.</b> The
 * {@code .batch} cron carries no zone; it follows the legacy WAS host TZ, which
 * this audit could not establish. If that host runs {@code Asia/Seoul}, legacy
 * 07–19 is 05:00–17:00 ICT. The <em>cadence</em> (every 5 min) is exact; the
 * <em>window boundary</em> requires Infra confirmation before UAT/PROD enablement
 * (open item OQ-10).
 */
@ConfigurationProperties(prefix = "haengsung.scheduled-payment-import")
public record HaengsungScheduledPaymentImportProperties(

        /** Master switch for the scheduled import job (default false). */
        boolean enabled,

        /** Spring cron expression (ICT). Default = legacy {@code ERP_0100_002}
         *  cadence: every 5 minutes, hours 07–19. */
        String cron
) {
    /**
     * Legacy {@code ERP_0100_002} cadence, translated to Spring 6-field from
     * {@code ERP_0100_002.batch} ({@code schedule=*}{@code /5 7-19 * * *}).
     */
    public static final String DEFAULT_CRON = "0 */5 7-19 * * ?";

    public HaengsungScheduledPaymentImportProperties {
        if (cron == null || cron.isBlank()) {
            cron = DEFAULT_CRON;
        }
    }
}
