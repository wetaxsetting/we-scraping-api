package com.kosign.wecms.config.haengsung;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HAENGSUNG external Oracle ERP datasource settings — bound from
 * {@code haengsung.oracle.*}.
 *
 * <p>Foundation for the HAENGSUNG ({@code PRDT_TYPE='H'}) Oracle integration
 * (Step 1 of {@code docs/features/haengsung-oracle-integration-roadmap.md}).
 * HAENGSUNG does not exchange payment data by Excel like other tenants — it
 * integrates with an external Oracle ERP DB. This is the shared connection
 * config every later flow (payment refresh, status / dispatch write-back,
 * FX / account / card sync) reuses.
 *
 * <p><b>Optional, off by default.</b> {@link #enabled} defaults to
 * {@code false} (env {@code HAENGSUNG_ORACLE_ENABLED}); when off, no Oracle
 * bean is created and the application boots normally against PostgreSQL only.
 *
 * <p><b>Fail-fast.</b> When {@link #enabled} is {@code true} the compact
 * constructor requires {@link #url} / {@link #username} / {@link #password}
 * to be present and rejects startup otherwise. The exception names the missing
 * setting but <b>never</b> echoes its value — no credential reaches a log.
 *
 * <p><b>Configuration source.</b> Local dev → the gitignored {@code .env.sit}
 * (copied from {@code .env.sit.example}). Real SIT host → OS env via Tomcat
 * {@code bin/setenv.sh} (authoritative — {@code .env.sit} is NOT read on SIT,
 * per memory {@code reference_sit_server_config_setenv}). The PostgreSQL
 * datasource stays {@code @Primary}; this Oracle datasource is secondary.
 */
@ConfigurationProperties(prefix = "haengsung.oracle")
public record HaengsungOracleProperties(

        /** Master switch. When false, no Oracle datasource / JdbcTemplate is created. */
        boolean enabled,

        /**
         * Oracle JDBC URL — e.g. {@code jdbc:oracle:thin:@//host:1521/DEV}.
         * Required when {@link #enabled}. Never logged.
         */
        String url,

        /** Oracle DB username. Required when {@link #enabled}. Never logged. */
        String username,

        /** Oracle DB password. Required when {@link #enabled}. Never logged. */
        String password,

        /** Hikari max pool size (default 5). */
        Integer maxPoolSize,

        /** Hikari minimum idle connections (default 1). */
        Integer minIdle,

        /** Connection-acquire timeout in ms (default 30000). */
        Long connectionTimeoutMs,

        /**
         * Idle keep-alive interval in ms (default 120000) — pings idle
         * connections so they are not silently dropped, guarding against the
         * ORA-17008 stale-connection failure (memory
         * {@code project_hs_prod_datasource_ora17008}).
         */
        Long keepaliveMs,

        /** Connection-validation timeout in ms (default 5000). */
        Long validationTimeoutMs

) {

    private static final int  DEFAULT_MAX_POOL_SIZE        = 5;
    private static final int  DEFAULT_MIN_IDLE             = 1;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L;
    private static final long DEFAULT_KEEPALIVE_MS          = 120_000L;
    private static final long DEFAULT_VALIDATION_TIMEOUT_MS = 5_000L;

    public HaengsungOracleProperties {
        if (enabled) {
            requireConfigured(url, "haengsung.oracle.url (HAENGSUNG_ORACLE_URL)");
            requireConfigured(username, "haengsung.oracle.username (HAENGSUNG_ORACLE_USERNAME)");
            requireConfigured(password, "haengsung.oracle.password (HAENGSUNG_ORACLE_PASSWORD)");
        }
        maxPoolSize         = maxPoolSize != null ? maxPoolSize : DEFAULT_MAX_POOL_SIZE;
        minIdle             = minIdle != null ? minIdle : DEFAULT_MIN_IDLE;
        connectionTimeoutMs = connectionTimeoutMs != null ? connectionTimeoutMs : DEFAULT_CONNECTION_TIMEOUT_MS;
        keepaliveMs         = keepaliveMs != null ? keepaliveMs : DEFAULT_KEEPALIVE_MS;
        validationTimeoutMs = validationTimeoutMs != null ? validationTimeoutMs : DEFAULT_VALIDATION_TIMEOUT_MS;
    }

    /**
     * Reject startup when an enabled integration is missing a required value.
     * The message names the setting (so ops can fix it) but NEVER its value.
     */
    private static void requireConfigured(String value, String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "HAENGSUNG Oracle integration is enabled (haengsung.oracle.enabled=true) but a "
                    + "required setting is missing: " + settingName + ". Provide it via the SIT host "
                    + "setenv.sh / OS env (or .env.sit locally), or disable the integration with "
                    + "HAENGSUNG_ORACLE_ENABLED=false.");
        }
    }
}
