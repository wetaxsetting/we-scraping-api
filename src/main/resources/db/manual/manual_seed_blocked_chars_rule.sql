-- ============================================================================
-- manual_seed_blocked_chars_rule.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the blocked_chars_rule table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds BLOCKED_CHARS_RULE — per-bank / payment-type / field blocked-character
--   validation rules — empty on a fresh refactor database.
--
-- String-literal notes (do NOT "fix" these)
--   * The blocked_chars values contain '' which is the SQL escape for a single
--     apostrophe — it is correct, not a typo. Each '' yields one ' in the stored value.
--   * The values also contain backslashes (e.g. \| ). The target uses
--     standard_conforming_strings = on, so backslashes are literal — no E'' prefix needed.
--   * "version" is quoted because it collides with a SQL keyword.
--
-- Table / conflict key
--   Table : blocked_chars_rule
--   PK    : pk_blocked_chars_rule (rule_id)   ← single-column primary key.
--   NOT NULL : all columns except eff_fr_dt and eff_to_dt (NULL eff_to_dt is used for open-ended rules).
--   No FK   : bank_cd references the bank registry conceptually only; loads independently.
--   Data  : 39 rows, supplied by the requester (real JEX DEV registry).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_blocked_chars_rule.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.blocked_chars_rule).
-- ============================================================================

INSERT INTO blocked_chars_rule (rule_id,bank_cd,bank_nm,pymn_tpye,field_cd,blocked_chars,eff_fr_dt,eff_to_dt,"version",status,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('0002','11704088','Shinhan VN','VEND','RMKS','~!@#$%^*={}[]<>','20251118',NULL,'1','Y','SYSTEM','20260112171455','SYSTEM','20260112171455'),
	 ('0003','11704088','Shinhan VN','EXPN','RMKS','~!@#$%^*={}[]<>','20251118',NULL,'1','Y','SYSTEM','20260112171458','SYSTEM','20260112171458'),
	 ('0004','11704088','Shinhan VN','PAYR','RMKS','~!@#$%^*={}[]<>','20251118',NULL,'1','Y','SYSTEM','20260112171500','SYSTEM','20260112171500'),
	 ('0005','11704088','Shinhan VN','VEND','RCPT','~!@^*={}[]''"<>','20251118',NULL,'1','Y','SYSTEM','20260112172049','SYSTEM','20260112172049'),
	 ('0006','11704088','Shinhan VN','EXPN','RCPT','~!@^*={}[]''"<>','20251118',NULL,'1','Y','SYSTEM','20260112172056','SYSTEM','20260112172056'),
	 ('0007','11704088','Shinhan VN','PAYR','RCPT','~!@^*={}[]''"<>','20251118',NULL,'1','Y','SYSTEM','20260112172104','SYSTEM','20260112172104'),
	 ('0001','11704088','Shinhan VN','INTR','RMKS','~!@#$%^*={}[]<>','20251118',NULL,'1','Y','SYSTEM','20260112164930','SYSTEM','20260112164930'),
	 ('0011','11704020','Woori VN','INTR','RMKS','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0012','11704020','Woori VN','VEND','RMKS','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0013','11704020','Woori VN','EXPN','RMKS','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750')
ON CONFLICT (rule_id) DO NOTHING;

INSERT INTO blocked_chars_rule (rule_id,bank_cd,bank_nm,pymn_tpye,field_cd,blocked_chars,eff_fr_dt,eff_to_dt,"version",status,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('0014','11704020','Woori VN','PAYR','RMKS','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0015','11704020','Woori VN','OVER','MEMO','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0016','11704020','Woori VN','VEND','RCPT','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0017','11704020','Woori VN','EXPN','RCPT','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0018','11704020','Woori VN','PAYR','RCPT','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0019','11704020','Woori VN','OVER','BENA','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0020','11704020','Woori VN','OVER','BEAD','<>''"@$&','20251216',NULL,'1','Y','SYSTEM','20260112172750','SYSTEM','20260112172750'),
	 ('0008','11704088','Shinhan VN','OVER','MEMO','`$''"','20251218','20260224','1','N','SYSTEM','20260112172613','SYSTEM','20260227181130'),
	 ('0009','11704088','Shinhan VN','OVER','BENA','`~!@#$%^&*()_=+{}[]\|?''":><','20251218','20260224','1','N','SYSTEM','20260112172655','SYSTEM','20260227181130'),
	 ('0010','11704088','Shinhan VN','OVER','BEAD','`~!@#$%^&*()_=+{}[]\|?''":><','20251218','20260224','1','N','SYSTEM','20260112172656','SYSTEM','20260227181130')
ON CONFLICT (rule_id) DO NOTHING;

INSERT INTO blocked_chars_rule (rule_id,bank_cd,bank_nm,pymn_tpye,field_cd,blocked_chars,eff_fr_dt,eff_to_dt,"version",status,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('0021','11704088','Shinhan VN','OVER','MEMO','`@$''"<>','20260225',NULL,'2','Y','SYSTEM','20260227181137','SYSTEM','20260227181137'),
	 ('0022','11704088','Shinhan VN','OVER','BENA','`@$''"<>','20260225',NULL,'2','Y','SYSTEM','20260227181137','SYSTEM','20260227181137'),
	 ('0023','11704088','Shinhan VN','OVER','BEAD','`@$''"<>','20260225',NULL,'2','Y','SYSTEM','20260227181137','SYSTEM','20260227181137'),
	 ('0024','11704004','VCB','INTR','RMKS','`~!@#$%^_={}[]\|''";><','20260414',NULL,'1','Y','SYSTEM','20260519175159','SYSTEM','20260519175159'),
	 ('0025','11704004','VCB','VEND','RMKS','`~!@#$%^_={}[]\|''";><','20260414',NULL,'1','Y','SYSTEM','20260519175159','SYSTEM','20260519175159'),
	 ('0026','11704004','VCB','EXPN','RMKS','`~!@#$%^_={}[]\|''";><','20260414',NULL,'1','Y','SYSTEM','20260519175159','SYSTEM','20260519175159'),
	 ('0027','11704004','VCB','PAYR','RMKS','`~!@#$%^_={}[]\|''";><','20260414',NULL,'1','Y','SYSTEM','20260519175159','SYSTEM','20260519175159'),
	 ('0028','11704004','VCB','INTR','RCPT','`~!@#$%^_={}[]\|''";><','20260414',NULL,'1','Y','SYSTEM','20260519175159','SYSTEM','20260519175159'),
	 ('0029','11704004','VCB','VEND','RCPT','`~!@#$%^_={}[]\|''";><','20260414',NULL,'1','Y','SYSTEM','20260519175159','SYSTEM','20260519175159'),
	 ('0030','11704004','VCB','EXPN','RCPT','`~!@#$%^_={}[]\|''";><','20260414',NULL,'1','Y','SYSTEM','20260519175159','SYSTEM','20260519175159')
ON CONFLICT (rule_id) DO NOTHING;

INSERT INTO blocked_chars_rule (rule_id,bank_cd,bank_nm,pymn_tpye,field_cd,blocked_chars,eff_fr_dt,eff_to_dt,"version",status,reg_id,reg_dtm,mod_id,mod_dtm) VALUES
	 ('0031','11704004','VCB','PAYR','RCPT','`~!@#$%^_={}[]\|''";><','20260414',NULL,'1','Y','SYSTEM','20260519175159','SYSTEM','20260519175159'),
	 ('0032','11704008','BIDV','INTR','RMKS','`~!@#$%^&*()_=+{}[]\|/?''";:><','20260414',NULL,'1','Y','SYSTEM','20260519175207','SYSTEM','20260519175207'),
	 ('0033','11704008','BIDV','VEND','RMKS','`~!@#$%^&*()_=+{}[]\|/?''";:><','20260414',NULL,'1','Y','SYSTEM','20260519175207','SYSTEM','20260519175207'),
	 ('0034','11704008','BIDV','EXPN','RMKS','`~!@#$%^&*()_=+{}[]\|/?''";:><','20260414',NULL,'1','Y','SYSTEM','20260519175207','SYSTEM','20260519175207'),
	 ('0035','11704008','BIDV','PAYR','RMKS','`~!@#$%^&*()_=+{}[]\|/?''";:><','20260414',NULL,'1','Y','SYSTEM','20260519175207','SYSTEM','20260519175207'),
	 ('0036','11704008','BIDV','INTR','RCPT','`~!@#$%^*()_=+{}[]\|/?''";:,><','20260414',NULL,'1','Y','SYSTEM','20260519175207','SYSTEM','20260519175207'),
	 ('0037','11704008','BIDV','VEND','RCPT','`~!@#$%^*()_=+{}[]\|/?''";:,><','20260414',NULL,'1','Y','SYSTEM','20260519175207','SYSTEM','20260519175207'),
	 ('0038','11704008','BIDV','EXPN','RCPT','`~!@#$%^*()_=+{}[]\|/?''";:,><','20260414',NULL,'1','Y','SYSTEM','20260519175207','SYSTEM','20260519175207'),
	 ('0039','11704008','BIDV','PAYR','RCPT','`~!@#$%^*()_=+{}[]\|/?''";:,><','20260414',NULL,'1','Y','SYSTEM','20260519175207','SYSTEM','20260519175207')
ON CONFLICT (rule_id) DO NOTHING;
