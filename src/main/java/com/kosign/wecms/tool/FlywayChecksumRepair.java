package com.kosign.wecms.tool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * One-time CLI tool: restores V86/V87 in flyway_schema_history to the other
 * developer's original values on the shared dev DB (172.168.5.172:5432/wecmsdevdb).
 *
 * Background:
 *   - Our V86/V87 SQL files (BANK_CERT_INFO and NXSIAS DDL) were renumbered
 *     from V84/V85, but the shared DB already had another dev's V86/V87
 *     ("overseas payment demo seed", "bank exch rate infm seed").
 *   - We previously patched V86/V87 to match our file checksums/descriptions,
 *     but that only updated metadata — the other dev's tables are still what's
 *     actually in the DB at those version slots.
 *   - Our DDL files have been renumbered to V100/V101 instead.
 *
 * This tool restores V86/V87 to the other dev's original checksums so Flyway
 * validates cleanly against those existing records, while V100/V101 are
 * new migrations Flyway will apply to create our tables.
 *
 * Run via Gradle:  ./gradlew repairFlywayChecksums
 */
public class FlywayChecksumRepair {

    private static final String URL  = "jdbc:postgresql://172.168.5.172:5432/wecmsdevdb";
    private static final String USER = "wecmsdev_owner";
    private static final String PASS = "P2CKJpDAnO#vUM146W$";

    // The other developer's original V86/V87 values — restore these so their
    // migration files validate correctly. Source: original "Applied to database"
    // values from the first Flyway error log.
    private static final int    V86_ORIG_CHECKSUM    = 1205819954;
    private static final int    V87_ORIG_CHECKSUM    = 1540872974;
    private static final String V86_ORIG_DESCRIPTION = "overseas payment demo seed";
    private static final String V87_ORIG_DESCRIPTION = "bank exch rate infm seed";

    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to " + URL + " ...");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            conn.setAutoCommit(false);

            // Show max version applied on the DB
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT MAX(CAST(version AS INTEGER)) AS max_ver FROM flyway_schema_history WHERE success=true")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) System.out.println("Max applied version on DB: " + rs.getInt("max_ver"));
                }
            }

            // Show current state before patching
            System.out.println("\nCurrent flyway_schema_history for V86 / V87:");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT version, description, checksum FROM flyway_schema_history " +
                    "WHERE version IN ('86','87') ORDER BY version")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.printf("  V%-4s  %-50s  checksum=%d%n",
                                rs.getString("version"),
                                rs.getString("description"),
                                rs.getInt("checksum"));
                    }
                }
            }

            // Restore V86 to the other dev's original values
            int rows86;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE flyway_schema_history SET checksum = ?, description = ? WHERE version = '86'")) {
                ps.setInt(1, V86_ORIG_CHECKSUM);
                ps.setString(2, V86_ORIG_DESCRIPTION);
                rows86 = ps.executeUpdate();
            }

            // Restore V87 to the other dev's original values
            int rows87;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE flyway_schema_history SET checksum = ?, description = ? WHERE version = '87'")) {
                ps.setInt(1, V87_ORIG_CHECKSUM);
                ps.setString(2, V87_ORIG_DESCRIPTION);
                rows87 = ps.executeUpdate();
            }

            conn.commit();

            System.out.println("\nRestored rows: V86=" + rows86 + ", V87=" + rows87);
            System.out.println("\nUpdated flyway_schema_history for V86 / V87:");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT version, description, checksum FROM flyway_schema_history " +
                    "WHERE version IN ('86','87') ORDER BY version")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.printf("  V%-4s  %-50s  checksum=%d%n",
                                rs.getString("version"),
                                rs.getString("description"),
                                rs.getInt("checksum"));
                    }
                }
            }

            System.out.println("\nSUCCESS — V86/V87 restored. V100/V101 will be applied by bootRun.");
        }
    }
}
