-- WeCMS Manual Database Change
-- Date: 2026-04-20
-- Source: migrated from Claude Code (AI)/sql/BLOCKED_CHARS_RULE_VCB_BIDV_20260420.sql
-- Related record: docs/features/update-blocked-character-data-bidv-vcb.md (path as cited by the original script header; not verified to exist in this repository)
-- Type: DML
-- Execution: MANUAL
-- Target environments: TBD — original script does not name the environments
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================
-- [IMP] Update blocked-character data for BIDV and VCB
-- Target table : BLOCKED_CHARS_RULE
-- Feature doc  : docs/features/update-blocked-character-data-bidv-vcb.md
-- Author       : Ran Sokny (Sonny)
-- Date         : 2026-04-20
--
-- Purpose:
--   Insert 16 new blocked-character rules so that Internal Transfer,
--   Vendor / Expense / Payroll Payment screens reject characters that
--   VCB (11704004) and BIDV (11704008) APIs refuse.
--
--   RULE_IDs 0024..0031 = VCB (8 rows, {INTR,VEND,EXPN,PAYR} x {RMKS,RCPT})
--   RULE_IDs 0032..0039 = BIDV (8 rows, same matrix)
--
-- Enums used (see src/wabooks/enums/):
--   PymnType : INTR, VEND, EXPN, PAYR, OVER
--   FieldCd  : RMKS, RCPT, MEMO, BENA, BEAD
--   YesOrNo  : Y, N
--
-- BIDV char set note:
--   RMKS includes `&` and `+`.
--   RCPT excludes `&` and `+` (narrower subset, matching Shinhan convention).
--
-- Rollout (staged — one gate per step):
--   1. DEV DB (single shared DB covers CMC + Haengsung in DEV)
--   2. Youvita Chan sign-off -> Haengsung UAT DB + CMC UAT DB
--   3. Vietnam PO sign-off   -> Haengsung PROD DB + CMC PROD DB
--
-- Idempotency:
--   Wrapped in a single transaction. If any INSERT fails (e.g. PK dup
--   because someone already added RULE_IDs in this range), the entire
--   batch rolls back -- safe to re-run after resolving the conflict.
--
-- Pre-check (run before BEGIN; abort if any row returns):
--   SELECT rule_id
--     FROM BLOCKED_CHARS_RULE
--    WHERE rule_id IN ('0024','0025','0026','0027','0028','0029','0030','0031',
--                      '0032','0033','0034','0035','0036','0037','0038','0039');
--
-- Post-check (after COMMIT): expect 16
--   SELECT COUNT(*) FROM BLOCKED_CHARS_RULE
--    WHERE bank_cd IN ('11704004','11704008') AND status = 'Y';
-- ============================================================

BEGIN;

-- ---------- VCB (bank_cd = 11704004) ----------
-- RMKS char set: `~!@#$%^_={}[]\|'";:,><
-- RCPT char set: `~!@#$%^_={}[]\|'";:,>< (same as RMKS for VCB)

INSERT INTO BLOCKED_CHARS_RULE
  (RULE_ID, BANK_CD, BANK_NM, PYMN_TPYE, FIELD_CD, BLOCKED_CHARS,
   EFF_FR_DT, EFF_TO_DT, VERSION, STATUS,
   REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
  ('0024', '11704004', 'VCB', 'INTR', 'RMKS',
   '`~!@#$%^_={}[]\|''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0025', '11704004', 'VCB', 'VEND', 'RMKS',
   '`~!@#$%^_={}[]\|''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0026', '11704004', 'VCB', 'EXPN', 'RMKS',
   '`~!@#$%^_={}[]\|''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0027', '11704004', 'VCB', 'PAYR', 'RMKS',
   '`~!@#$%^_={}[]\|''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0028', '11704004', 'VCB', 'INTR', 'RCPT',
   '`~!@#$%^_={}[]\|''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0029', '11704004', 'VCB', 'VEND', 'RCPT',
   '`~!@#$%^_={}[]\|''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0030', '11704004', 'VCB', 'EXPN', 'RCPT',
   '`~!@#$%^_={}[]\|''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0031', '11704004', 'VCB', 'PAYR', 'RCPT',
   '`~!@#$%^_={}[]\|''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL);

-- ---------- BIDV (bank_cd = 11704008) ----------
-- RMKS char set: `~!@#$%^&*()_=+{}[]\|/?'";:,><
-- RCPT char set: `~!@#$%^*()_={}[]\|/?'";:,><   (excludes & and +)

INSERT INTO BLOCKED_CHARS_RULE
  (RULE_ID, BANK_CD, BANK_NM, PYMN_TPYE, FIELD_CD, BLOCKED_CHARS,
   EFF_FR_DT, EFF_TO_DT, VERSION, STATUS,
   REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
  ('0032', '11704008', 'BIDV', 'INTR', 'RMKS',
   '`~!@#$%^&*()_=+{}[]\|/?''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0033', '11704008', 'BIDV', 'VEND', 'RMKS',
   '`~!@#$%^&*()_=+{}[]\|/?''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0034', '11704008', 'BIDV', 'EXPN', 'RMKS',
   '`~!@#$%^&*()_=+{}[]\|/?''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0035', '11704008', 'BIDV', 'PAYR', 'RMKS',
   '`~!@#$%^&*()_=+{}[]\|/?''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0036', '11704008', 'BIDV', 'INTR', 'RCPT',
   '`~!@#$%^*()_={}[]\|/?''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0037', '11704008', 'BIDV', 'VEND', 'RCPT',
   '`~!@#$%^*()_={}[]\|/?''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0038', '11704008', 'BIDV', 'EXPN', 'RCPT',
   '`~!@#$%^*()_={}[]\|/?''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL),

  ('0039', '11704008', 'BIDV', 'PAYR', 'RCPT',
   '`~!@#$%^*()_={}[]\|/?''";:,><',
   '20260414', NULL, '1', 'Y',
   'SYSTEM', to_char(now(), 'YYYYMMDDHH24MISS'), NULL, NULL);

-- Sanity check before commit (uncomment to inspect before COMMIT):
-- SELECT rule_id, bank_cd, bank_nm, pymn_tpye, field_cd, blocked_chars,
--        eff_fr_dt, version, status
--   FROM BLOCKED_CHARS_RULE
--  WHERE rule_id BETWEEN '0024' AND '0039'
--  ORDER BY rule_id;

COMMIT;

-- ============================================================
-- Post-execution verification
-- ============================================================
-- 1) Row count (expect 16):
-- SELECT COUNT(*) FROM BLOCKED_CHARS_RULE
--  WHERE bank_cd IN ('11704004','11704008') AND status = 'Y';
--
-- 2) Visual inspection (expect 16 rows):
-- SELECT rule_id, bank_nm, pymn_tpye, field_cd, blocked_chars
--   FROM BLOCKED_CHARS_RULE
--  WHERE bank_cd IN ('11704004','11704008')
--  ORDER BY bank_nm, pymn_tpye, field_cd;
-- ============================================================
