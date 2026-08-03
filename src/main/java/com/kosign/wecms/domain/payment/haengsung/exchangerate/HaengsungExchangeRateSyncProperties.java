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
 * <h2>Cadence — the legacy schedule IS in source (corrected 2026-07-28)</h2>
 *
 * <p>A previous revision of this Javadoc stated that legacy {@code ERP_0100_001}
 * "has no cron in source … the exact legacy cadence is unknown", and defaulted to
 * a guessed {@code 0 *}{@code /5 * * * *} (every 5 minutes, 24 h). <b>That premise
 * was false.</b> {@code ERP_0100_001} is a JexSimpleBatch job whose schedule is
 * committed at:
 *
 * <pre>
 *   WECMS DEV/WECMS/web/WEB-INF/jex/simpleBatch/WECMS/ERP_0100_001.batch
 *     name=WecmsToErp
 *     batchClass=batch.jex.wabooks.erp.ERP_0100_001
 *     schedule=*&#47;10 7-19 * * *        # 5-field cron: min hour dom mon dow
 * </pre>
 *
 * <p>Legacy runs <b>every 10 minutes, only during hours 07–19</b> — not every 5
 * minutes and not around the clock. {@link #DEFAULT_CRON} is corrected to match.
 * The old default fired at 2× the legacy rate across a 24 h window instead of 13 h.
 *
 * <p><b>Unconfirmed — the timezone of the legacy {@code 7-19} window.</b> The
 * {@code .batch} cron carries no zone; it follows the legacy WAS host TZ, which
 * this audit could not establish. If that host runs {@code Asia/Seoul}, legacy
 * 07–19 is 05:00–17:00 ICT — two hours earlier than the window configured here.
 * The <em>cadence</em> (every 10 min) is exact; the <em>window boundary</em>
 * requires Infra confirmation before UAT/PROD enablement (open item OQ-10, same
 * family as the operation-status timezone question OQ-3).
 */
@ConfigurationProperties(prefix = "haengsung.exchange-rate-sync")
public record HaengsungExchangeRateSyncProperties(

        /** Master switch for the scheduled exchange-rate sync job (default false). */
        boolean enabled,

        /** Spring cron expression (ICT). Default = legacy {@code ERP_0100_001}
         *  cadence: every 10 minutes, hours 07–19. */
        String cron
) {
    /**
     * Legacy {@code ERP_0100_001} cadence, translated to Spring 6-field from
     * {@code ERP_0100_001.batch} ({@code schedule=*}{@code /10 7-19 * * *}).
     */
    public static final String DEFAULT_CRON = "0 */10 7-19 * * ?";

    public HaengsungExchangeRateSyncProperties {
        if (cron == null || cron.isBlank()) {
            cron = DEFAULT_CRON;
        }
    }
}
