-- ============================================================================
-- manual_seed_usag_infm_lang_detl.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the usag_infm_lang_detl table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds USAG_INFM_LANG_DETL — the per-language (KO/EN/KM/VI) display names for
--   the usage/service-type catalog — empty on a fresh refactor database.
--
-- Load order (FK dependency)
--   This is a CHILD of USAG_INFM (use_usag_cd -> usag_infm.use_usag_cd).  Run
--   manual_seed_usag_infm.sql FIRST so the parent rows exist; otherwise these
--   inserts violate the foreign key.  The 8 parent use_usag_cd values seeded
--   here (4000000034, 4000000300-303, 6000000057, 6000000300-301) all exist in
--   manual_seed_usag_infm.sql.
--
-- Table / conflict key
--   Table : usag_infm_lang_detl
--   PK    : pk_usag_infm_lang_detl (use_usag_cd, lang_code)   ← composite.
--   NOT NULL columns : use_usag_cd, lang_code (both present in every row).
--   Data  : 32 rows (8 codes x 4 languages), supplied by the requester.
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_usag_infm_lang_detl.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.usag_infm_lang_detl).
-- ============================================================================

INSERT INTO usag_infm_lang_detl (use_usag_cd,lang_code,usag_nm,usag_cont) VALUES
	 ('4000000034','KO','기타 수입',''),
	 ('4000000034','EN','Other Income',''),
	 ('4000000034','KM','Other Income',''),
	 ('4000000034','VI','Other Income',''),
	 ('4000000300','KO','대출금',''),
	 ('4000000300','EN','Loan',''),
	 ('4000000300','KM','Loan',''),
	 ('4000000300','VI','Loan',''),
	 ('4000000301','KO','금융 투자 수익',''),
	 ('4000000301','EN','Financial Investment Income','')
ON CONFLICT (use_usag_cd,lang_code) DO NOTHING;

INSERT INTO usag_infm_lang_detl (use_usag_cd,lang_code,usag_nm,usag_cont) VALUES
	 ('4000000301','KM','Financial Investment Income',''),
	 ('4000000301','VI','Financial Investment Income',''),
	 ('4000000302','KO','선급금 환급',''),
	 ('4000000302','EN','Advance Payment Refund',''),
	 ('4000000302','KM','Advance Payment Refund',''),
	 ('4000000302','VI','Advance Payment Refund',''),
	 ('4000000303','KO','부가가치세 환급',''),
	 ('4000000303','EN','VAT Refund',''),
	 ('4000000303','KM','VAT Refund',''),
	 ('4000000303','VI','VAT Refund','')
ON CONFLICT (use_usag_cd,lang_code) DO NOTHING;

INSERT INTO usag_infm_lang_detl (use_usag_cd,lang_code,usag_nm,usag_cont) VALUES
	 ('6000000057','KO','대출 상환',''),
	 ('6000000057','EN','Loan Repaid',''),
	 ('6000000057','KM','Loan Repaid',''),
	 ('6000000057','VI','Loan Repaid',''),
	 ('6000000300','KO','직원에 대한 임시 지불',''),
	 ('6000000300','EN','Temporary payment for employees',''),
	 ('6000000300','KM','Temporary payment for employees',''),
	 ('6000000300','VI','Temporary payment for employees',''),
	 ('6000000301','KO','기타 지출',''),
	 ('6000000301','EN','Other expenses','')
ON CONFLICT (use_usag_cd,lang_code) DO NOTHING;

INSERT INTO usag_infm_lang_detl (use_usag_cd,lang_code,usag_nm,usag_cont) VALUES
	 ('6000000301','KM','Other expenses',''),
	 ('6000000301','VI','Other expenses','')
ON CONFLICT (use_usag_cd,lang_code) DO NOTHING;
