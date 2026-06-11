-- ============================================================================
-- manual_seed_napas_bank_remit_info.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the napas_bank_remit_info table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds NAPAS_BANK_REMIT_INFO — the per-source-bank NAPAS remittance
--   eligibility map (bank_cd + napas_bank_cd -> remit_yn) — empty on a fresh
--   refactor database.  Source banks covered: 11704008, 11704003, 11704080,
--   11704004, 11704088.
--
-- Load order (no hard FK)
--   napas_bank_cd pairs with NAPAS_BANK_INFO and bank_cd with the bank registry,
--   but there is NO foreign-key constraint on this table, so it loads
--   independently.  Recommended to load manual_seed_napas_bank_info.sql first
--   for logical consistency.
--
-- Table / conflict key
--   Table   : napas_bank_remit_info
--   PK      : napas_bank_remit_info_pk (bank_cd, napas_bank_cd)   ← composite.
--   NOT NULL columns : bank_cd, napas_bank_cd, remit_yn (all present in every row).
--   Data    : 325 rows, supplied by the requester (real JEX DEV registry).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_napas_bank_remit_info.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.napas_bank_remit_info).
-- ============================================================================

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704008','970425','Y'),
	 ('11704008','970416','Y'),
	 ('11704008','970405','Y'),
	 ('11704008','970409','Y'),
	 ('11704008','970438','Y'),
	 ('11704008','422589','Y'),
	 ('11704008','970446','Y'),
	 ('11704008','970406','Y'),
	 ('11704008','970431','Y'),
	 ('11704008','970408','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704008','970437','Y'),
	 ('11704008','970442','Y'),
	 ('11704008','458761','Y'),
	 ('11704008','970455','Y'),
	 ('11704008','970456','Y'),
	 ('11704008','970434','Y'),
	 ('11704008','668888','Y'),
	 ('11704008','970452','Y'),
	 ('11704008','970449','Y'),
	 ('11704008','970422','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704008','970426','Y'),
	 ('11704008','970428','Y'),
	 ('11704008','970419','Y'),
	 ('11704008','801011','Y'),
	 ('11704008','970448','Y'),
	 ('11704008','970414','Y'),
	 ('11704008','970430','Y'),
	 ('11704008','970439','Y'),
	 ('11704008','970412','Y'),
	 ('11704008','970403','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704008','970400','Y'),
	 ('11704008','970429','Y'),
	 ('11704008','970440','Y'),
	 ('11704008','970443','Y'),
	 ('11704008','970424','Y'),
	 ('11704008','970410','Y'),
	 ('11704008','970407','Y'),
	 ('11704008','970423','Y'),
	 ('11704008','970458','Y'),
	 ('11704008','970441','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704008','970427','Y'),
	 ('11704008','970454','Y'),
	 ('11704008','970433','Y'),
	 ('11704008','970436','Y'),
	 ('11704008','970415','Y'),
	 ('11704008','970432','Y'),
	 ('11704008','970421','Y'),
	 ('11704008','970457','Y'),
	 ('11704008','970418','N'),
	 ('11704008','546034','N')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704008','796500','N'),
	 ('11704008','970464','N'),
	 ('11704008','970466','N'),
	 ('11704008','970467','N'),
	 ('11704008','963399','N'),
	 ('11704008','970462','N'),
	 ('11704008','970463','N'),
	 ('11704008','963369','N'),
	 ('11704008','970468','N'),
	 ('11704008','963388','N')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704008','546035','N'),
	 ('11704008','1207001','N'),
	 ('11704008','970444','N'),
	 ('11704008','971005','N'),
	 ('11704008','971011','N'),
	 ('11704003','970425','Y'),
	 ('11704003','970416','Y'),
	 ('11704003','970405','Y'),
	 ('11704003','970409','Y'),
	 ('11704003','970438','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704003','970418','Y'),
	 ('11704003','970446','Y'),
	 ('11704003','970406','Y'),
	 ('11704003','970431','Y'),
	 ('11704003','970464','Y'),
	 ('11704003','970408','Y'),
	 ('11704003','970437','Y'),
	 ('11704003','970442','Y'),
	 ('11704003','458761','Y'),
	 ('11704003','970434','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704003','668888','Y'),
	 ('11704003','970466','Y'),
	 ('11704003','970467','Y'),
	 ('11704003','963399','Y'),
	 ('11704003','970452','Y'),
	 ('11704003','970462','Y'),
	 ('11704003','970463','Y'),
	 ('11704003','970449','Y'),
	 ('11704003','970422','Y'),
	 ('11704003','970468','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704003','970426','Y'),
	 ('11704003','970428','Y'),
	 ('11704003','970419','Y'),
	 ('11704003','801011','Y'),
	 ('11704003','970448','Y'),
	 ('11704003','970414','Y'),
	 ('11704003','970430','Y'),
	 ('11704003','970439','Y'),
	 ('11704003','970412','Y'),
	 ('11704003','970403','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704003','970400','Y'),
	 ('11704003','970429','Y'),
	 ('11704003','970440','Y'),
	 ('11704003','970443','Y'),
	 ('11704003','970424','Y'),
	 ('11704003','970410','Y'),
	 ('11704003','970407','Y'),
	 ('11704003','970423','Y'),
	 ('11704003','963388','Y'),
	 ('11704003','970458','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704003','970441','Y'),
	 ('11704003','970427','Y'),
	 ('11704003','970454','Y'),
	 ('11704003','970433','Y'),
	 ('11704003','970436','Y'),
	 ('11704003','970415','Y'),
	 ('11704003','970444','Y'),
	 ('11704003','971005','Y'),
	 ('11704003','970432','Y'),
	 ('11704003','970457','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704003','546034','N'),
	 ('11704003','422589','N'),
	 ('11704003','796500','N'),
	 ('11704003','970455','N'),
	 ('11704003','970456','N'),
	 ('11704003','963369','N'),
	 ('11704003','546035','N'),
	 ('11704003','1207001','N'),
	 ('11704003','971011','N'),
	 ('11704003','970421','N')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704080','970425','Y'),
	 ('11704080','970416','Y'),
	 ('11704080','970405','Y'),
	 ('11704080','970409','Y'),
	 ('11704080','970438','Y'),
	 ('11704080','970418','Y'),
	 ('11704080','546034','Y'),
	 ('11704080','422589','Y'),
	 ('11704080','970446','Y'),
	 ('11704080','796500','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704080','970406','Y'),
	 ('11704080','970431','Y'),
	 ('11704080','970408','Y'),
	 ('11704080','970437','Y'),
	 ('11704080','970442','Y'),
	 ('11704080','458761','Y'),
	 ('11704080','970455','Y'),
	 ('11704080','970456','Y'),
	 ('11704080','970434','Y'),
	 ('11704080','668888','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704080','963399','Y'),
	 ('11704080','970452','Y'),
	 ('11704080','970462','Y'),
	 ('11704080','970463','Y'),
	 ('11704080','970449','Y'),
	 ('11704080','963369','Y'),
	 ('11704080','970422','Y'),
	 ('11704080','970426','Y'),
	 ('11704080','970428','Y'),
	 ('11704080','970419','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704080','970448','Y'),
	 ('11704080','970414','Y'),
	 ('11704080','970430','Y'),
	 ('11704080','970439','Y'),
	 ('11704080','970412','Y'),
	 ('11704080','970403','Y'),
	 ('11704080','970400','Y'),
	 ('11704080','970429','Y'),
	 ('11704080','970440','Y'),
	 ('11704080','970443','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704080','970424','Y'),
	 ('11704080','970410','Y'),
	 ('11704080','970407','Y'),
	 ('11704080','970423','Y'),
	 ('11704080','963388','Y'),
	 ('11704080','546035','Y'),
	 ('11704080','970458','Y'),
	 ('11704080','970441','Y'),
	 ('11704080','970427','Y'),
	 ('11704080','970454','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704080','970433','Y'),
	 ('11704080','970436','Y'),
	 ('11704080','970415','Y'),
	 ('11704080','970444','Y'),
	 ('11704080','970432','Y'),
	 ('11704080','970421','Y'),
	 ('11704080','970457','Y'),
	 ('11704080','970464','N'),
	 ('11704080','970466','N'),
	 ('11704080','970467','N')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704080','970468','N'),
	 ('11704080','801011','N'),
	 ('11704080','1207001','N'),
	 ('11704080','971005','N'),
	 ('11704080','971011','N'),
	 ('11704004','970425','Y'),
	 ('11704004','970416','Y'),
	 ('11704004','970405','Y'),
	 ('11704004','970409','Y'),
	 ('11704004','970438','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704004','970418','Y'),
	 ('11704004','546034','Y'),
	 ('11704004','422589','Y'),
	 ('11704004','796500','Y'),
	 ('11704004','970431','Y'),
	 ('11704004','970408','Y'),
	 ('11704004','970437','Y'),
	 ('11704004','970442','Y'),
	 ('11704004','458761','Y'),
	 ('11704004','970455','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704004','970456','Y'),
	 ('11704004','970434','Y'),
	 ('11704004','668888','Y'),
	 ('11704004','970466','Y'),
	 ('11704004','970467','Y'),
	 ('11704004','963399','Y'),
	 ('11704004','970452','Y'),
	 ('11704004','970462','Y'),
	 ('11704004','963369','Y'),
	 ('11704004','970422','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704004','970468','Y'),
	 ('11704004','970426','Y'),
	 ('11704004','970428','Y'),
	 ('11704004','970419','Y'),
	 ('11704004','801011','Y'),
	 ('11704004','970448','Y'),
	 ('11704004','970414','Y'),
	 ('11704004','970430','Y'),
	 ('11704004','970439','Y'),
	 ('11704004','970412','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704004','970403','Y'),
	 ('11704004','970400','Y'),
	 ('11704004','970429','Y'),
	 ('11704004','970440','Y'),
	 ('11704004','970443','Y'),
	 ('11704004','970424','Y'),
	 ('11704004','970410','Y'),
	 ('11704004','970407','Y'),
	 ('11704004','970423','Y'),
	 ('11704004','963388','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704004','546035','Y'),
	 ('11704004','970458','Y'),
	 ('11704004','970441','Y'),
	 ('11704004','970427','Y'),
	 ('11704004','970454','Y'),
	 ('11704004','970433','Y'),
	 ('11704004','970415','Y'),
	 ('11704004','1207001','Y'),
	 ('11704004','970444','Y'),
	 ('11704004','971005','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704004','971011','Y'),
	 ('11704004','970432','Y'),
	 ('11704004','970421','Y'),
	 ('11704004','970446','N'),
	 ('11704004','970406','N'),
	 ('11704004','970464','N'),
	 ('11704004','970463','N'),
	 ('11704004','970449','N'),
	 ('11704004','970436','N'),
	 ('11704004','970457','N')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704088','970425','Y'),
	 ('11704088','970416','Y'),
	 ('11704088','970405','Y'),
	 ('11704088','970409','Y'),
	 ('11704088','970438','Y'),
	 ('11704088','970418','Y'),
	 ('11704088','546034','Y'),
	 ('11704088','422589','Y'),
	 ('11704088','970446','Y'),
	 ('11704088','796500','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704088','970406','Y'),
	 ('11704088','970431','Y'),
	 ('11704088','970408','Y'),
	 ('11704088','970437','Y'),
	 ('11704088','970442','Y'),
	 ('11704088','458761','Y'),
	 ('11704088','970455','Y'),
	 ('11704088','970456','Y'),
	 ('11704088','970434','Y'),
	 ('11704088','668888','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704088','970466','Y'),
	 ('11704088','970467','Y'),
	 ('11704088','963399','Y'),
	 ('11704088','970452','Y'),
	 ('11704088','970462','Y'),
	 ('11704088','970463','Y'),
	 ('11704088','970449','Y'),
	 ('11704088','963369','Y'),
	 ('11704088','970422','Y'),
	 ('11704088','970468','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704088','970426','Y'),
	 ('11704088','970428','Y'),
	 ('11704088','970419','Y'),
	 ('11704088','801011','Y'),
	 ('11704088','970448','Y'),
	 ('11704088','970414','Y'),
	 ('11704088','970430','Y'),
	 ('11704088','970439','Y'),
	 ('11704088','970412','Y'),
	 ('11704088','970403','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704088','970400','Y'),
	 ('11704088','970429','Y'),
	 ('11704088','970440','Y'),
	 ('11704088','970443','Y'),
	 ('11704088','970410','Y'),
	 ('11704088','970407','Y'),
	 ('11704088','970423','Y'),
	 ('11704088','963388','Y'),
	 ('11704088','546035','Y'),
	 ('11704088','970458','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704088','970441','Y'),
	 ('11704088','970427','Y'),
	 ('11704088','970454','Y'),
	 ('11704088','970433','Y'),
	 ('11704088','970436','Y'),
	 ('11704088','970415','Y'),
	 ('11704088','1207001','Y'),
	 ('11704088','970444','Y'),
	 ('11704088','971005','Y'),
	 ('11704088','971011','Y')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_remit_info (bank_cd,napas_bank_cd,remit_yn) VALUES
	 ('11704088','970432','Y'),
	 ('11704088','970457','Y'),
	 ('11704088','970464','N'),
	 ('11704088','970424','N'),
	 ('11704088','970421','N')
ON CONFLICT (bank_cd,napas_bank_cd) DO NOTHING;
