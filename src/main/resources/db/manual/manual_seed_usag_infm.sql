-- ============================================================================
-- manual_seed_usag_infm.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the usag_infm table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds USAG_INFM — the global usage/service-type catalog — which is empty on
--   a fresh refactor database.  It is a Level-0 lookup table and the FK parent
--   of SYS_USAG_SET_INFM (sys_usag_set_infm.use_usag_cd -> usag_infm.use_usag_cd),
--   so it must be populated before that child table or any usage-coded data.
--
-- Table / conflict key
--   Table : usag_infm
--   PK    : usag_infm_pk (use_usag_cd)   ← single-column primary key.
--   NOT NULL columns : use_usag_cd, usag_grp_cd (both present in every row).
--   Data  : 8 rows, supplied by the requester (real JEX DEV registry).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_usag_infm.sql
--   If your search_path does not include the schema that owns usag_infm,
--   prefix the table name accordingly (e.g. wecmsdevdb.usag_infm).
-- ============================================================================

INSERT INTO usag_infm (use_usag_cd,usag_grp_cd,rept_dv,rept_grp,acct_cd,acct_nm,del_yn,mod_id,mod_dtm,usag_type,hand_use_usag_cd) VALUES
	 ('4000000034','4',NULL,NULL,NULL,NULL,'N','SYSTEM','20250530070434',NULL,'30'),
	 ('4000000300','4',NULL,NULL,NULL,NULL,'N','SYSTEM','20250530070434',NULL,'31'),
	 ('4000000301','4',NULL,NULL,NULL,NULL,'N','SYSTEM','20250530070434',NULL,'32'),
	 ('4000000302','4',NULL,NULL,NULL,NULL,'N','SYSTEM','20250530070434',NULL,'33'),
	 ('4000000303','4',NULL,NULL,NULL,NULL,'N','SYSTEM','20250530070434',NULL,'34'),
	 ('6000000057','6',NULL,NULL,NULL,NULL,'N','SYSTEM','20250530070434',NULL,'40'),
	 ('6000000300','6',NULL,NULL,NULL,NULL,'N','SYSTEM','20250530070434',NULL,'41'),
	 ('6000000301','6',NULL,NULL,NULL,NULL,'N','SYSTEM','20250530070434',NULL,'43')
ON CONFLICT (use_usag_cd) DO NOTHING;
