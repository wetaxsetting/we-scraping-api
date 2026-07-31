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
 * <h2>Cadence — the legacy schedule IS in source (corrected 2026-07-28)</h2>
 *
 * <p>A previous revision of this Javadoc stated that legacy {@code ERP_0100_003}
 * "has no cron in source … the exact legacy cadence is unknown", and defaulted to
 * a guessed {@code 0 *}{@code /5 * * * *} (every 5 minutes, 24 h). <b>That premise
 * was false, and this was the largest schedule divergence in the refactor</b> —
 * 288 fires/day against legacy's 13, roughly 22×. {@code ERP_0100_003} is a
 * JexSimpleBatch job whose schedule is committed at:
 *
 * <pre>
 *   WECMS DEV/WECMS/web/WEB-INF/jex/simpleBatch/WECMS/ERP_0100_003.batch
 *     name=WeCMS to ERP
 *     batchClass=batch.jex.wabooks.erp.ERP_0100_003
 *     schedule=0 7-19 * * *            # 5-field cron: min hour dom mon dow
 * </pre>
 *
 * <p>Legacy runs <b>hourly, on the hour, only during hours 07–19</b> — 13 runs a
 * day. {@link #DEFAULT_CRON} is corrected to match.
 *
 * <p><b>Unconfirmed — the timezone of the legacy {@code 7-19} window.</b> The
 * {@code .batch} cron carries no zone; it follows the legacy WAS host TZ, which
 * this audit could not establish. If that host runs {@code Asia/Seoul}, legacy
 * 07–19 is 05:00–17:00 ICT. The <em>cadence</em> (hourly) is exact; the
 * <em>window boundary</em> requires Infra confirmation before UAT/PROD enablement
 * (open item OQ-10).
 */
@ConfigurationProperties(prefix = "haengsung.card-account-sync")
public record HaengsungCardAccountSyncProperties(

        /** Master switch for the scheduled card/account sync job (default false). */
        boolean enabled,

        /** Spring cron expression (ICT). Default = legacy {@code ERP_0100_003}
         *  cadence: hourly on the hour, hours 07–19. */
        String cron
) {
    /**
     * Legacy {@code ERP_0100_003} cadence, translated to Spring 6-field from
     * {@code ERP_0100_003.batch} ({@code schedule=0 7-19 * * *}).
     */
    public static final String DEFAULT_CRON = "0 0 7-19 * * ?";

    public HaengsungCardAccountSyncProperties {
        if (cron == null || cron.isBlank()) {
            cron = DEFAULT_CRON;
        }
    }
}
