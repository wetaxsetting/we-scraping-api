-- ============================================================================
-- manual_seed_bank_infm.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the bank_infm table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds BANK_INFM — the global Vietnamese bank registry — which is empty on
--   a fresh refactor database.  Bank-name / Napas-Citad resolution on every
--   banking screen reads from this table, so it must be populated before those
--   features return meaningful data.
--
-- Table / conflict key
--   Table : bank_infm
--   PK    : pk_bank_infm (bank_cd, start_date, end_date)   ← composite, NOT
--           bank_cd alone.  The ON CONFLICT target below matches the real PK so
--           the script stays idempotent.
--   Data  : 26 rows, supplied by the requester (real JEX DEV registry).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_bank_infm.sql
--   If your search_path does not include the schema that owns bank_infm,
--   prefix the table name accordingly (e.g. wecmsdevdb.bank_infm).
-- ============================================================================

INSERT INTO bank_infm (bank_cd,start_date,end_date,rprs_bank_nm,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('11704012','20210801','99991231','VIB Bank','soknyran','20220322214000','soknyran','20220322214000'),
	 ('11704020','20210801','99991231','Woori(Vietnam)','soknyran','20220322214000','soknyran','20220322214000'),
	 ('11704080','20210801','99991231','KEBHana','soknyran','20220322214000','soknyran','20220322214000'),
	 ('11704003','20210801','99991231','IBK(Vietnam)','soknyran','20220322214000','soknyran','20220721083358'),
	 ('11704088','20210801','99991231','Shinhan(Vietnam)','soknyran','20220322214000','soknyran','20240808105333'),
	 ('11704001','20210801','99991231','Vietin Bank','soknyran','20220322214000','soknyran','20240808105044'),
	 ('11704008','20210801','99991231','BIDV Bank ','soknyran','20220322214000','soknyran','20241024102334'),
	 ('11704004','20210801','99991231','Vietcom Bank','soknyran','20220322214000','soknyran','20240816040855'),
	 ('11704002','20210801','99991231','Techcom Bank','soknyran','20220322214000','soknyran','20241026050401'),
	 ('11704014','20210801','99991231','MSB Bank','soknyran','20220322214000','soknyran','20220322214000')
ON CONFLICT (bank_cd,start_date,end_date) DO NOTHING;

INSERT INTO bank_infm (bank_cd,start_date,end_date,rprs_bank_nm,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('11740099','20210801','99991231','SMBC','soknyran','20220322214000','soknyran','20220322214000'),
	 ('11704013','20210801','99991231','Kookmin(Vietnam)','soknyran','20220322214000','soknyran','20220721083459'),
	 ('11704011','20210801','99991231','ACB Bank','soknyran','20220322214000','soknyran','20230202081007'),
	 ('11704107','20231113','99991231','NongHyup Bank(Vietnam)','soknyran','20231113102603','soknyran','20231113102603'),
	 ('11704106','20231113','99991231','An Binh Commercial Joint Stock Bank','soknyran','20231113102547','soknyran','20240216100302'),
	 ('11704105','20231113','99991231','Saigon-Hanoi Commercial Joint Stock Bank','soknyran','20231113102514','soknyran','20240216102614'),
	 ('11704104','20230516','99991231','Standard Chartered Vietnam','soknyran','20230516060827','soknyran','20240305105007'),
	 ('11704108','20240409','99991231','Prosperity and Growth Commercial Joint Stock Bank','soknyran','20240409040534','soknyran','20240503100751'),
	 ('11704109','20240722','99991231','Sacombank','soknyran','20240722042424','soknyran','20240722042424'),
	 ('11704110','20240722','99991231','OCB Bank','soknyran','20240722042513','soknyran','20240722042513')
ON CONFLICT (bank_cd,start_date,end_date) DO NOTHING;

INSERT INTO bank_infm (bank_cd,start_date,end_date,rprs_bank_nm,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('11704111','20240722','99991231','KienLongBank','soknyran','20240722042546','soknyran','20240722042546'),
	 ('11704102','20220728','99991231','VP Bank','soknyran','20220728080249','soknyran','20240808104517'),
	 ('11704103','20220728','99991231','Tienphong Bank','soknyran','20220728080333','soknyran','20240808110143'),
	 ('11704100','20220713','99991231','Agribank','soknyran','20220713012321','soknyran','20241024102434'),
	 ('11704101','20220713','99991231','HD Bank','soknyran','20220713012403','soknyran','20241024102522'),
	 ('11704007','20210801','99991231','MBBank','soknyran','20220322214000','soknyran','20241024102622')
ON CONFLICT (bank_cd,start_date,end_date) DO NOTHING;
