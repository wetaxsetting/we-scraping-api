-- ============================================================================
-- manual_seed_sys_usag_set_infm.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the sys_usag_set_infm table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds SYS_USAG_SET_INFM — the system-level usage on/off + display-order
--   settings for the usage catalog — empty on a fresh refactor database.
--
-- Load order (FK dependency)
--   This is a CHILD of USAG_INFM (use_usag_cd -> usag_infm.use_usag_cd).  Run
--   manual_seed_usag_infm.sql FIRST so the parent rows exist; otherwise these
--   inserts violate the foreign key.  All 8 use_usag_cd values seeded here exist
--   in manual_seed_usag_infm.sql.
--
-- Table / conflict key
--   Table   : sys_usag_set_infm
--   PK      : sys_usag_set_infm_pk (use_usag_cd)   ← single-column primary key.
--   Columns : use_usag_cd, use_yn, disp_yn (NOT NULL; default 'Y'), disp_ord (numeric, nullable).
--   Data    : 8 rows, supplied by the requester as tab-separated values
--             (use_usag_cd / use_yn / disp_yn / disp_ord).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_sys_usag_set_infm.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.sys_usag_set_infm).
-- ============================================================================

INSERT INTO sys_usag_set_infm (use_usag_cd,use_yn,disp_yn,disp_ord) VALUES
	 ('4000000034','Y','Y',16),
	 ('4000000300','Y','Y',55),
	 ('4000000301','Y','Y',56),
	 ('4000000302','Y','Y',57),
	 ('4000000303','Y','Y',58),
	 ('6000000057','Y','Y',7),
	 ('6000000300','Y','Y',180),
	 ('6000000301','Y','Y',181)
ON CONFLICT (use_usag_cd) DO NOTHING;
