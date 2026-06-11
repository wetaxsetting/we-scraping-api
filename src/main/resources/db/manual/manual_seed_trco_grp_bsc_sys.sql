-- ============================================================================
-- manual_seed_trco_grp_bsc_sys.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the trco_grp_bsc_sys table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds TRCO_GRP_BSC_SYS — the system-level counterparty (trading-company)
--   group catalog — empty on a fresh refactor database.  Level-0 lookup table
--   and FK parent of TRCO_GRP_BSC_CSTM (trco_grp_bsc_cstm.grp_cd ->
--   trco_grp_bsc_sys.grp_cd), so it must be populated before that child table.
--
-- Table / conflict key
--   Table : trco_grp_bsc_sys
--   PK    : trco_grp_bsc_sys_pk (grp_cd)   ← single-column primary key.
--   NOT NULL columns : grp_cd, grp_nm, grp_stts, reg_dtm (all present in every row).
--   Data  : 3 rows, supplied by the requester (real JEX DEV registry).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_trco_grp_bsc_sys.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.trco_grp_bsc_sys).
-- ============================================================================

INSERT INTO trco_grp_bsc_sys (grp_cd,grp_nm,grp_stts,reg_dtm) VALUES
	 ('S','매출','Y','20250505042144'),
	 ('B','매입','Y','20250505042144'),
	 ('E','기타','Y','20250505042144')
ON CONFLICT (grp_cd) DO NOTHING;
