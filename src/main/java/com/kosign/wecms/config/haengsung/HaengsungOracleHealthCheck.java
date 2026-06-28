package com.kosign.wecms.config.haengsung;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Safe connectivity probe for the HAENGSUNG Oracle ERP datasource.
 *
 * <p>Runs once at startup (when {@code haengsung.oracle.enabled=true}) and
 * exposes {@link #isReachable()} for later steps (payment refresh, write-backs,
 * sync) to reuse. The probe is {@code SELECT 1 FROM dual} — the cheapest Oracle
 * round-trip.
 *
 * <p><b>Resilient by design.</b> A failed probe NEVER throws and NEVER fails
 * startup — the Oracle datasource is secondary and optional, so an Oracle outage
 * must not take the API down. Failures are logged with the redacted SQLState /
 * vendor error code only; the JDBC URL, username, and password are never logged.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungOracleHealthCheck implements ApplicationRunner {

    private static final String VALIDATION_QUERY = "SELECT 1 FROM dual";

    private final JdbcTemplate oracleJdbcTemplate;

    public HaengsungOracleHealthCheck(
            @Qualifier(HaengsungOracleDataSourceConfig.ORACLE_JDBC_TEMPLATE)
            JdbcTemplate oracleJdbcTemplate) {
        this.oracleJdbcTemplate = oracleJdbcTemplate;
    }

    /**
     * Probe the Oracle ERP DB with {@code SELECT 1 FROM dual}.
     *
     * @return {@code true} when the query returns 1; {@code false} on any
     *         failure (never throws).
     */
    public boolean isReachable() {
        try {
            Integer result = oracleJdbcTemplate.queryForObject(VALIDATION_QUERY, Integer.class);
            return result != null && result == 1;
        } catch (Exception ex) {
            // Redacted: SQLState / error code only — no URL / credentials.
            log.debug("HAENGSUNG Oracle connectivity probe failed: {}", describe(ex));
            return false;
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isReachable()) {
            log.info("HAENGSUNG Oracle connectivity OK (SELECT 1 FROM dual).");
        } else {
            log.warn("HAENGSUNG Oracle is enabled but not reachable at startup. "
                    + "The application will continue; ERP flows retry on demand.");
        }
    }

    /**
     * Build a redacted diagnostic string from a failure. Emits the exception
     * type plus, for SQL failures, the SQLState and vendor error code (the
     * {@code ORA-xxxxx} number) — but never the message, URL, or credentials.
     */
    private static String describe(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof SQLException sql) {
            return root.getClass().getSimpleName()
                    + " [SQLState=" + sql.getSQLState()
                    + ", errorCode=" + sql.getErrorCode() + "]";
        }
        return root.getClass().getSimpleName();
    }
}
