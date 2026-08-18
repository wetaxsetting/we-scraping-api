-- WeCMS Manual Database Change
-- Date: 2026-05-31
-- Source: migrated from Claude Code (AI)/sql/flyway_history_repair_20260531.sql
-- Related record: TBD
-- Type: DDL+DML
-- Execution: MANUAL
-- Target environments: Refactor dev DB (172.168.5.172:5432/wecmsdevdb)
-- Executor: DBA OR DEV TEAM — environment dependent
-- Rollback: Original script backs the history table up before collapsing it; restore from that backup table
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- Flyway schema-history repair — refactor dev DB (172.168.5.172:5432/wecmsdevdb)
-- Generated: 2026-05-31
--
-- WHY: wecms-api fails to start with
--   FlywayException: Corrupted schema history: multiple delete entries for version 2
-- The diagnostic below showed the corruption is NOT limited to version 2 —
-- nearly every version in flyway_schema_history carries spurious `DELETE`
-- marker rows (e.g. v164 ×23, v98/v99 ×14, v118 ×12) plus duplicate `SQL`
-- applied rows. Root cause: FlywayRepairConfig runs flyway.repair() on EVERY
-- local boot; against a SHARED dev DB used by multiple working copies that
-- hold different migration-file sets, each boot marks "missing" migrations as
-- deleted, accumulating DELETE markers and duplicate applications over time.
-- This is shared-DB bookkeeping drift — the actual schema + seed data are
-- intact; only flyway_schema_history is junk.
--
-- WHAT THIS DOES: collapses the history table to exactly one successful
-- applied row per version, with no DELETE markers and no duplicates, so
-- repair() can proceed and migrate() can apply pending migrations (V232).
-- It does NOT touch any business table or any schema object — only the
-- flyway_schema_history bookkeeping table.
--
-- ⚠️  SHARED DATABASE — COORDINATE BEFORE RUNNING.
--     Other developers use this DB. Run during a quiet window, tell the team
--     (Arthur / Sonny), and take the backup in step 0 first.
-- ⚠️  Run as the DB owner. Review the SELECT outputs before the DELETEs.
-- ⚠️  Do NOT run against UAT/PROD — there is no Flyway/refactor schema there;
--     this is the local/shared R&D refactor DB only.
--
-- After running: restart wecms-api. repair() realigns checksums of the kept
-- rows to the files on disk, then migrate() applies V232.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. BACKUP (mandatory) — snapshot the history table so this is reversible.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS flyway_schema_history_bak_20260531 AS
SELECT * FROM flyway_schema_history;

-- ---------------------------------------------------------------------------
-- 1. INSPECT — confirm the corruption scope (read-only; review before step 2).
-- ---------------------------------------------------------------------------
-- Versions with DELETE markers and/or duplicate applied rows:
SELECT version,
       count(*) FILTER (WHERE type = 'SQL')        AS applied_rows,
       count(*) FILTER (WHERE type = 'DELETE')     AS delete_rows,
       count(*) FILTER (WHERE success = false)     AS failed_rows
FROM flyway_schema_history
WHERE version IS NOT NULL
GROUP BY version
HAVING count(*) FILTER (WHERE type = 'DELETE') > 0
    OR count(*) FILTER (WHERE type = 'SQL')    > 1
    OR count(*) FILTER (WHERE success = false) > 0
ORDER BY version;

-- ---------------------------------------------------------------------------
-- 2. CLEANUP — run the three statements together (ideally in one transaction).
--    BEGIN;  ... ; COMMIT;   (or run individually and verify with step 3)
-- ---------------------------------------------------------------------------

-- 2a. Drop every DELETE marker. No migration files are missing from disk in a
--     correct checkout, so no version should be "logically deleted".
DELETE FROM flyway_schema_history
 WHERE type = 'DELETE';

-- 2b. Drop failed-migration rows (repair() would normally clear these; do it
--     up front so a stuck failed row doesn't keep a version "unapplied").
DELETE FROM flyway_schema_history
 WHERE success = false;

-- 2c. Collapse duplicate applied rows — keep the LOWEST installed_rank per
--     version (the original application), delete the rest. Versioned rows
--     only; repeatable (R__) and baseline rows have NULL version and are left
--     untouched.
DELETE FROM flyway_schema_history a
 USING flyway_schema_history b
 WHERE a.version IS NOT NULL
   AND a.version = b.version
   AND a.installed_rank > b.installed_rank;

-- ---------------------------------------------------------------------------
-- 3. VERIFY — both queries should return ZERO rows after cleanup.
-- ---------------------------------------------------------------------------
-- 3a. No version should have >1 row, any DELETE row, or any failed row:
SELECT version,
       count(*)                                    AS total_rows,
       count(*) FILTER (WHERE type = 'DELETE')     AS delete_rows,
       count(*) FILTER (WHERE success = false)     AS failed_rows
FROM flyway_schema_history
WHERE version IS NOT NULL
GROUP BY version
HAVING count(*) > 1
    OR count(*) FILTER (WHERE type = 'DELETE') > 0
    OR count(*) FILTER (WHERE success = false) > 0
ORDER BY version;

-- 3b. Spot-check version 2 (the one in the original error) — expect a single
--     SQL / success=true row:
SELECT installed_rank, version, description, type, success, installed_on
FROM flyway_schema_history
WHERE version = '2'
ORDER BY installed_rank;

-- ---------------------------------------------------------------------------
-- 4. RESTART wecms-api. FlywayRepairConfig.repair() realigns checksums to the
--    on-disk files, then migrate() applies V232 (Payroll Scheduled View
--    Setting) and any other pending versions.
--
--    Once confirmed healthy, drop the backup:
--    -- DROP TABLE flyway_schema_history_bak_20260531;
-- ---------------------------------------------------------------------------
