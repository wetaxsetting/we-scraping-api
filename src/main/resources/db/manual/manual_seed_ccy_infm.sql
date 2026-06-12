-- ============================================================================
-- manual_seed_ccy_infm.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the ccy_infm table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds CCY_INFM — the currency-code lookup (code + symbol) — empty on a
--   fresh refactor database.
--
-- Table / conflict key
--   Table : ccy_infm
--   PK    : pk_ccy_infm (ccy_cd)   ← single-column primary key (char(3)).
--   NOT NULL columns : ccy_cd only (ccy_sym/use_yn/audit columns nullable).
--   Data  : 2 rows, supplied by the requester (real JEX DEV registry).
--   Note  : ccy_sym is char(1); the VND symbol ₫ is one character. File is UTF-8.
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_ccy_infm.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.ccy_infm).
-- ============================================================================

INSERT INTO ccy_infm (ccy_cd,ccy_sym,use_yn,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('VND','₫','Y','SYSTEM','20250505040807','SYSTEM','20250505040807'),
	 ('USD','$','Y','SYSTEM','20250505040807','SYSTEM','20250505040807')
ON CONFLICT (ccy_cd) DO NOTHING;
