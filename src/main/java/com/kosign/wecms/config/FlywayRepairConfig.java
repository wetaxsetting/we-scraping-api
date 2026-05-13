package com.kosign.wecms.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Local-profile-only Flyway repair strategy.
 *
 * <p>On the shared dev DB some migrations were applied by other team members with
 * SQL that was later revised locally, leaving stale checksum entries and/or
 * schema objects in the wrong state. Two detection mechanisms are used:
 *
 * <ul>
 *   <li><b>Missing-table anchors (V84–V86):</b> if the anchor table is absent,
 *       the history entries for that group are deleted so Flyway re-applies them.
 *   <li><b>Wrong-column-type anchors:</b> if an anchor column still has the stale
 *       SQL type, <em>all</em> numeric-versioned history entries above a given
 *       baseline are deleted. This covers however many experimental V87/V88/…
 *       entries the shared DB accumulated — no need to enumerate them explicitly.
 *       The corrective migration (V88) uses {@code USING col::INTEGER} so it is
 *       a no-op when the column is already correct.
 * </ul>
 *
 * <p>Safe because:
 * <ul>
 *   <li>Scoped to {@code @Profile("local")} — never runs in any other environment.
 *   <li>Only deletes history entries when the physical schema confirms the stale state.
 *   <li>Flyway then re-applies the correct migrations and records accurate checksums.
 * </ul>
 */
@Slf4j
@Configuration
@Profile("local")
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayRepairStrategy(DataSource dataSource) {
        return flyway -> {
            repairStaleEntries(dataSource);
            // Sync stored checksums with the current local files before migrating.
            // Covers cases where a migration was edited after being applied to the
            // shared dev DB (e.g. V87, V89 checksum mismatches) without requiring
            // explicit per-version entries in repairStaleEntries().
            flyway.repair();
            flyway.migrate();
        };
    }

    private void repairStaleEntries(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            repairMissingTables(conn);
            repairWrongColumnTypes(conn);
        } catch (Exception ex) {
            log.error("[FlywayRepair] Failed to inspect/repair schema history — proceeding anyway", ex);
        }
    }

    // ── Missing-table anchors ─────────────────────────────────────────────────

    /**
     * If the anchor table is absent, delete the corresponding flyway_schema_history
     * rows so Flyway re-applies those migrations from scratch.
     */
    private void repairMissingTables(Connection conn) throws Exception {
        record Anchor(String table, List<String> versions) {}

        List<Anchor> anchors = List.of(
                new Anchor("bank_exch_rate_infm", List.of("84", "85", "86"))
        );

        for (Anchor anchor : anchors) {
            if (!tableExists(conn, anchor.table())) {
                log.warn("[FlywayRepair] Table '{}' not found — purging stale history entries {} so Flyway re-runs them",
                        anchor.table(), anchor.versions());
                purgeVersions(conn, anchor.versions());
            }
        }
    }

    // ── Wrong-column-type anchors ─────────────────────────────────────────────

    /**
     * If an anchor column still has the stale SQL type, delete ALL numeric-versioned
     * history entries above {@code purgeAboveVersion} so the corrective migration runs.
     *
     * <p>The shared dev DB may have accumulated any number of experimental V87, V88, …
     * entries with different checksums. Purging everything above the last known-good
     * baseline (V86 for the overseas-payment tables) handles them all in one pass,
     * regardless of how many there are.
     */
    private void repairWrongColumnTypes(Connection conn) throws Exception {
        record ColumnAnchor(String table, String column, String staleType, int purgeAboveVersion) {}

        List<ColumnAnchor> anchors = List.of(
                // V85 created TOT_TRNS_CNT as NUMERIC(10); V88 corrects it to INTEGER.
                // Purge every history entry above V86 when the column is still numeric.
                new ColumnAnchor("ovrs_pymn_mstr", "tot_trns_cnt", "numeric", 86)
        );

        for (ColumnAnchor anchor : anchors) {
            if (columnHasType(conn, anchor.table(), anchor.column(), anchor.staleType())) {
                log.warn("[FlywayRepair] Column '{}.{}' still has type '{}' — purging all history entries above V{} so corrective migration runs",
                        anchor.table(), anchor.column(), anchor.staleType(), anchor.purgeAboveVersion());
                purgeVersionsAbove(conn, anchor.purgeAboveVersion());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean columnHasType(Connection conn, String tableName, String columnName,
                                   String expectedDataType) throws Exception {
        String sql = """
                SELECT data_type FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                LIMIT 1
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                return expectedDataType.equalsIgnoreCase(rs.getString("data_type"));
            }
        }
    }

    private void purgeVersions(Connection conn, List<String> versions) throws Exception {
        String placeholders = String.join(",", versions.stream().map(v -> "?").toList());
        String sql = "DELETE FROM flyway_schema_history WHERE version IN (" + placeholders + ")";
        try (var ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < versions.size(); i++) {
                ps.setString(i + 1, versions.get(i));
            }
            int deleted = ps.executeUpdate();
            log.info("[FlywayRepair] Deleted {} stale flyway_schema_history row(s) for versions {}", deleted, versions);
        }
    }

    /**
     * Deletes all flyway_schema_history rows whose version is a plain integer
     * greater than {@code baselineVersion}. Covers any number of stale entries
     * left by previous experimental runs on the shared dev DB.
     */
    private void purgeVersionsAbove(Connection conn, int baselineVersion) throws Exception {
        // version column is VARCHAR; cast to numeric only for rows that look like integers
        // (excludes repeatable migrations whose version is NULL).
        String sql = """
                DELETE FROM flyway_schema_history
                WHERE version ~ '^[0-9]+$'
                  AND version::numeric > ?
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setInt(1, baselineVersion);
            int deleted = ps.executeUpdate();
            log.info("[FlywayRepair] Deleted {} stale flyway_schema_history row(s) above V{}", deleted, baselineVersion);
        }
    }
}