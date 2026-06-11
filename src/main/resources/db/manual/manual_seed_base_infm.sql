-- ============================================================================
-- manual_seed_base_infm.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the base_infm table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds BASE_INFM — per-country base formatting config (number / date /
--   tax-id format, national dialling code, telephone length, English-name
--   flag) — empty on a fresh refactor database.
--
-- Table / conflict key
--   Table : base_infm
--   PK    : pk_base_infm (ntnl_cd)   ← single-column primary key.
--   NOT NULL columns : ntnl_cd only (all other columns nullable; tel_len is
--                      numeric, use_eng_trco_nm_yn defaults 'Y').
--   Data  : 1 row, supplied by the requester (real JEX DEV registry).
--   Note  : ntnl_cd value is 'VI' as supplied (kept verbatim — note this differs
--           from the 'VN' nation code used elsewhere; no FK constraint here).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_base_infm.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.base_infm).
-- ============================================================================

INSERT INTO base_infm (ntnl_cd,num_format,dat_format,taxid_format,natn_num,tel_len,use_eng_trco_nm_yn,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('VI','999.999.999.999,999','DD-MM-YYYY','999999999999','+84',8,'Y','SYSTEM','20250505040936','SYSTEM','20250505040936')
ON CONFLICT (ntnl_cd) DO NOTHING;
