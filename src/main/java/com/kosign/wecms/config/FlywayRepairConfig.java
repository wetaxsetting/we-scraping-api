package com.kosign.wecms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Local-profile-only Flyway repair strategy.
 *
 * <p>Calls {@code flyway.repair()} before {@code flyway.migrate()} to reconcile
 * stored checksums and clear any failed-migration rows from prior boots.
 *
 * <p>Also detects tables that are recorded as applied in {@code flyway_schema_history}
 * but do not physically exist in the DB (e.g. when the shared dev DB schema was
 * partially reset). Stale history rows for those missing tables are deleted so
 * {@code migrate()} can re-create them cleanly.
 *
 * <p>Safe because it is scoped to {@code @Profile("local")} only.
 */
@Slf4j
@Configuration
@Profile("local")
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayRepairStrategy() {
        return flyway -> {
            flyway.repair();
            // DataSource comes from Flyway's own configuration — no Spring injection needed.
            try (Connection conn = flyway.getConfiguration().getDataSource().getConnection()) {
                purgeMissingTableEntries(conn);
            } catch (SQLException e) {
                throw new RuntimeException(
                        "FlywayRepairConfig: failed to check/purge stale history entries", e);
            }
            flyway.migrate();
        };
    }

    /**
     * For each locally-managed table that Flyway thinks it has applied, verify the
     * table physically exists. If it is absent, delete the corresponding
     * {@code flyway_schema_history} rows so {@code migrate()} will re-create it.
     *
     * <p>Currently guarded tables (all use {@code CREATE TABLE IF NOT EXISTS}):
     * <ul>
     *   <li>{@code overseas_payment_key_seq} — V135 / V136</li>
     *   <li>{@code ovrs_pymn_dtls} — V138</li>
     * </ul>
     */
    private void purgeMissingTableEntries(Connection conn) throws SQLException {
        if (!tableExists(conn, "overseas_payment_key_seq")) {
            log.warn("FlywayRepairConfig: overseas_payment_key_seq is missing — " +
                     "purging stale V135/V136 history entries so migrate() recreates the table");
            deleteHistoryVersions(conn, "135", "136");
        }
        if (!tableExists(conn, "ovrs_pymn_dtls")) {
            log.warn("FlywayRepairConfig: ovrs_pymn_dtls is missing — " +
                     "purging stale V138 history entry so migrate() recreates the table");
            deleteHistoryVersions(conn, "138");
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(
                null, "public", tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private void deleteHistoryVersions(Connection conn, String... versions) throws SQLException {
        String inClause = "'" + String.join("','", versions) + "'";
        try (Statement st = conn.createStatement()) {
            int deleted = st.executeUpdate(
                    "DELETE FROM flyway_schema_history WHERE version IN (" + inClause + ")");
            log.info("FlywayRepairConfig: deleted {} stale flyway_schema_history row(s)", deleted);
        }
    }
}