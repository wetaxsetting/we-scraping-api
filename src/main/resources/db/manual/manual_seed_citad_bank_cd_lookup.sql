-- ============================================================================
-- manual_seed_citad_bank_cd_lookup.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the citad_bank_cd_lookup table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds CITAD_BANK_CD_LOOKUP — maps the local bank_cd to its CITAD bank code —
--   empty on a fresh refactor database.
--
-- Table / conflict key
--   Table : citad_bank_cd_lookup
--   PK    : pk_citad_bank_cd_lookup (bank_cd)   ← single-column primary key.
--   NOT NULL columns : bank_cd, use_yn (default 'Y'); citad_bank_cd nullable.
--   No FK   : bank_cd references the bank registry conceptually only; loads independently.
--   Data  : 22 rows, supplied by the requester (real JEX DEV registry).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_citad_bank_cd_lookup.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.citad_bank_cd_lookup).
-- ============================================================================

INSERT INTO citad_bank_cd_lookup (bank_cd,citad_bank_cd,use_yn) VALUES
	 ('11704012','79314013','Y'),
	 ('11704020','01663001','Y'),
	 ('11704080','01626001','Y'),
	 ('11704014','01302001','Y'),
	 ('11740099','79636001','Y'),
	 ('11704003','01652001','Y'),
	 ('11704013','01666001','Y'),
	 ('11704011','79307001','Y'),
	 ('11704107','01662001','Y'),
	 ('11704104','01604001','Y')
ON CONFLICT (bank_cd) DO NOTHING;

INSERT INTO citad_bank_cd_lookup (bank_cd,citad_bank_cd,use_yn) VALUES
	 ('11704109','79303001','Y'),
	 ('11704110','79333001','Y'),
	 ('11704111','79353001','Y'),
	 ('11704001','01201001','Y'),
	 ('11704102','01309001','Y'),
	 ('11704088','79616001','Y'),
	 ('11704004','01203001','Y'),
	 ('11704002','01310001','Y'),
	 ('11704008','01202001','Y'),
	 ('11704101','79321001','Y')
ON CONFLICT (bank_cd) DO NOTHING;

INSERT INTO citad_bank_cd_lookup (bank_cd,citad_bank_cd,use_yn) VALUES
	 ('11704007','01311001','Y'),
	 ('11704100','01204009','Y')
ON CONFLICT (bank_cd) DO NOTHING;
