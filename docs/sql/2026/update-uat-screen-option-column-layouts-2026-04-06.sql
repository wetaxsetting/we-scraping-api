-- WeCMS Manual Database Change
-- Date: 2026-04-06
-- Source: migrated from Claude Code (AI)/sql/haengsung_uat_sys_scr_option_migration_20260406.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: UAT (the original script header names "CMC UAT")
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================
-- CMC UAT — sys_scr_option_wecms Migration
-- Date: 2026-04-06
-- Services: fnsh_0002_001, fnsh_0006_001, fnsh_0001_001,
--           fnsh_0005_001, pymn_0010_002, pymn_0002_002,
--           pymn_0004_002, pymn_0006_002, pymn_0008_002,
--           pymn_0009_003, pymn_0001_003, pymn_0003_003,
--           pymn_0005_003, pymn_0007_004
-- ============================================================


-- ============================================================
-- 1. fnsh_0002_001 — Transaction History (VND)
-- ============================================================

-- === TABLE 1 ===

-- Step 1: Shift existing rows 1–10 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table1'
  AND disp_ord <= 10;

-- Step 2: Insert TRNS_ID at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table1', 'N', NULL);

-- Step 3: Shift rows at 7–11 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table1'
  AND disp_ord >= 7
  AND disp_ord <= 11;

-- Step 4: Insert FILE_NM, MATCH_DT, MATCH_BY at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM',  'File Name',    7, 'Y', 'Y', '200px', 'table1', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT', 'Matched Date', 8, 'Y', 'Y', '150px', 'table1', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY', 'Matched By',   9, 'Y', 'Y', '150px', 'table1', 'N', NULL);

-- === TABLE 5 ===

-- Step 1: Shift existing rows 1–7 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table5'
  AND disp_ord <= 7;

-- Step 2: Insert TRNS_ID_5 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_5', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table5', 'N', NULL);

-- Step 3: Shift rows at 7–8 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table5'
  AND disp_ord >= 7
  AND disp_ord <= 8;

-- Step 4: Insert FILE_NM_5, MATCH_DT_5, MATCH_BY_5 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_5',  'File Name',    7, 'Y', 'Y', '200px', 'table5', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_5', 'Matched Date', 8, 'Y', 'Y', '150px', 'table5', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_5', 'Matched By',   9, 'Y', 'Y', '150px', 'table5', 'N', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table5' ORDER BY disp_ord;


-- ============================================================
-- 2. fnsh_0006_001 — Transaction History (FCY)
-- ============================================================

-- === TABLE 1 ===
-- Current disp_ord values are sequential (1-10, 99) — shift logic applies.

-- Step 1: Shift existing rows 1–10 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table1'
  AND disp_ord <= 10;

-- Step 2: Insert TRNS_ID at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table1', 'N', NULL);

-- Step 3: Shift rows at 7–11 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table1'
  AND disp_ord >= 7
  AND disp_ord <= 11;

-- Step 4: Insert FILE_NM, MATCH_DT, MATCH_BY at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'FILE_NM',  'File Name',    7, 'Y', 'Y', '200px', 'table1', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT', 'Matched Date', 8, 'Y', 'Y', '150px', 'table1', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY', 'Matched By',   9, 'Y', 'Y', '150px', 'table1', 'N', NULL);

-- === TABLE 5 ===

-- Step 1: Shift existing rows 1–7 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table5'
  AND disp_ord <= 7;

-- Step 2: Insert TRNS_ID_5 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_5', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table5', 'N', NULL);

-- Step 3: Shift rows at 7–8 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table5'
  AND disp_ord >= 7
  AND disp_ord <= 8;

-- Step 4: Insert FILE_NM_5, MATCH_DT_5, MATCH_BY_5 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'FILE_NM_5',  'File Name',    7, 'Y', 'Y', '200px', 'table5', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_5', 'Matched Date', 8, 'Y', 'Y', '150px', 'table5', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_5', 'Matched By',   9, 'Y', 'Y', '150px', 'table5', 'N', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table5' ORDER BY disp_ord;


-- ============================================================
-- 3. fnsh_0001_001 — Hide VAS_CD (CMC-only field)
-- ============================================================

UPDATE sys_scr_option_wecms
SET disp_yn = 'N', use_yn = 'N'
WHERE web_svc_id = 'fnsh_0001_001'
  AND field_id = 'VAS_CD';

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0001_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0001_001' AND field_id = 'VAS_CD';


-- ============================================================
-- 4. fnsh_0005_001 — Hide VAS_CD_3 (CMC-only field)
-- ============================================================

UPDATE sys_scr_option_wecms
SET disp_yn = 'N', use_yn = 'N'
WHERE web_svc_id = 'fnsh_0005_001'
  AND field_id = 'VAS_CD_3';

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0005_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0005_001' AND field_id = 'VAS_CD_3';


-- ============================================================
-- 5. pymn_0010_002 — Transfer Result Internal
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0010_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0010_002', 'ETC',          'ETC',                    99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0010_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9,  'Y', 'Y', '250px', 'table2', 'Y', NULL),
('pymn_0010_002', 'ETC_2',          'ETC',                    99, 'Y', 'Y', '100px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 6. pymn_0002_002 — Payment Result Vendor
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0002_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0002_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 7. pymn_0004_002 — Payment Result Expense
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0004_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0004_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 8. pymn_0006_002 — Payment Result Payroll
-- ============================================================
-- Pre-migration table1: 8 rows (1-7 + CHART_ACCT_CD at 10)
-- Pre-migration table2: 7 rows (1-7)

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0006_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 8,  'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0006_002', 'ETC',          'ETC',                    99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0006_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 8,  'Y', 'Y', '250px', 'table2', 'Y', NULL),
('pymn_0006_002', 'ETC_2',          'ETC',                    99, 'Y', 'Y', '100px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 9. pymn_0008_002 — Payment Result Overseas
-- ============================================================

-- === TABLE 1 (append 2 fields — RSLT_DTTM already at 11) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0008_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 12, 'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0008_002', 'ETC',          'ETC',                    99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

-- === TABLE 2 (append 2 fields — RSLT_DTTM_2 already at 10) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0008_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 11, 'Y', 'Y', '250px', 'table2', 'Y', NULL),
('pymn_0008_002', 'ETC_2',          'ETC',                    99, 'Y', 'Y', '100px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 10. pymn_0009_003 — Scheduled Transfer Internal
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0009_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0009_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 11. pymn_0001_003 — Scheduled Payment Vendor
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0001_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0001_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 12. pymn_0003_003 — Scheduled Payment Expense
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0003_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0003_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 13. pymn_0005_003 — Scheduled Payment Payroll
-- ============================================================
-- Pre-migration table1: 5 rows (1-4 + CHART_ACCT_CD at 5)
-- Pre-migration table2: 4 rows (1-4)

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0005_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 6, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0005_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 5, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0005_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0005_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0005_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 14. pymn_0007_004 — Scheduled Payment Overseas
-- ============================================================

-- === TABLE 1 (append BANK_TRAN_ID after MEMO at 9) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0007_004', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

-- === TABLE 2 (append BANK_TRAN_ID_2 after MEMO_2 at 8) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0007_004', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 15. sys_env_set_infm — New environment setting (00079)
-- ============================================================

INSERT INTO sys_env_set_infm (env_set_inf, env_set_cont)
VALUES ('00079', 'N');

-- VERIFY
SELECT * FROM sys_env_set_infm WHERE env_set_inf = '00079';


-- ============================================================
-- 16. busi_env_set_infm — Propagate env setting 00079 to all tenants
-- ============================================================

INSERT INTO busi_env_set_infm (
     biz_key
,    env_set_inf
,    env_set_cont
,    mod_id
,    mod_dtm
)
SELECT
     T.biz_key
,    T.env_set_inf
,    T.env_set_cont
,    T.mod_id
,    T.mod_dtm
FROM (
    SELECT
         bi.biz_key
    ,    sesi.env_set_inf
    ,    sesi.env_set_cont
    ,    'SYSTEM'                                    AS mod_id
    ,    to_char(now(), 'YYYYMMDDHH24MISS')          AS mod_dtm
    FROM sys_env_set_infm sesi
    CROSS JOIN busi_infm bi
) T
WHERE env_set_inf = '00079';

-- VERIFY
SELECT * FROM busi_env_set_infm WHERE env_set_inf = '00079' ORDER BY biz_key;


-- ================================================================
-- ================================================================
-- Haengsung UAT — sys_scr_option_wecms Migration
-- Date: 2026-04-06
-- Services: fnsh_0002_001, fnsh_0006_001, pymn_0010_002,
--           pymn_0002_002, pymn_0004_002, pymn_0006_002,
--           pymn_0008_002, pymn_0009_003, pymn_0001_003,
--           pymn_0003_003, pymn_0005_003, pymn_0007_004
-- ================================================================
-- ================================================================


-- ============================================================
-- 1. fnsh_0002_001 — Transaction History (VND)
-- ============================================================

-- === TABLE 1 ===

-- Step 1: Shift existing rows 1–10 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table1'
  AND disp_ord <= 10;

-- Step 2: Insert TRNS_ID at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table1', 'N', NULL);

-- Step 3: Shift rows at 7–11 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table1'
  AND disp_ord >= 7
  AND disp_ord <= 11;

-- Step 4: Insert FILE_NM, MATCH_DT, MATCH_BY at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM',  'File Name',    7, 'Y', 'Y', '200px', 'table1', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT', 'Matched Date', 8, 'Y', 'Y', '150px', 'table1', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY', 'Matched By',   9, 'Y', 'Y', '150px', 'table1', 'N', NULL);

-- === TABLE 5 ===

-- Step 1: Shift existing rows 1–7 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table5'
  AND disp_ord <= 7;

-- Step 2: Insert TRNS_ID_5 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_5', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table5', 'N', NULL);

-- Step 3: Shift rows at 7–8 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table5'
  AND disp_ord >= 7
  AND disp_ord <= 8;

-- Step 4: Insert FILE_NM_5, MATCH_DT_5, MATCH_BY_5 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_5',  'File Name',    7, 'Y', 'Y', '200px', 'table5', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_5', 'Matched Date', 8, 'Y', 'Y', '150px', 'table5', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_5', 'Matched By',   9, 'Y', 'Y', '150px', 'table5', 'N', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table5' ORDER BY disp_ord;


-- ============================================================
-- 2. fnsh_0006_001 — Transaction History (FCY)
-- ============================================================

-- === TABLE 1 ===
-- Current disp_ord values (1, 25-33, 125) are non-sequential.
-- Direct per-field UPDATE to target positions.

UPDATE sys_scr_option_wecms SET disp_ord = 2  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'TRSC_DT';
UPDATE sys_scr_option_wecms SET disp_ord = 3  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'IN_AMT';
UPDATE sys_scr_option_wecms SET disp_ord = 4  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'OUT_AMT';
UPDATE sys_scr_option_wecms SET disp_ord = 5  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'BAL';
UPDATE sys_scr_option_wecms SET disp_ord = 6  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RMRK';
UPDATE sys_scr_option_wecms SET disp_ord = 10 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'CHART_ACCT_CD';
UPDATE sys_scr_option_wecms SET disp_ord = 11 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_USAG_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 12 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_VAS_ACCT_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 13 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_TRCO_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 14 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'CNFM_BY_USER_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 99 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'MEMO';

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID',  'Transaction ID', 1,  'Y', 'Y', '190px', 'table1', 'N', NULL),
('fnsh_0006_001', 'FILE_NM',  'File Name',       7,  'Y', 'Y', '200px', 'table1', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT', 'Matched Date',    8,  'Y', 'Y', '150px', 'table1', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY', 'Matched By',      9,  'Y', 'Y', '150px', 'table1', 'N', NULL);

-- === TABLE 5 ===
-- Current disp_ord is sequential (1-7, 99) — shift logic applies.

-- Step 1: Shift existing rows 1–7 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table5'
  AND disp_ord <= 7;

-- Step 2: Insert TRNS_ID_5 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_5', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table5', 'N', NULL);

-- Step 3: Shift rows at 7–8 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table5'
  AND disp_ord >= 7
  AND disp_ord <= 8;

-- Step 4: Insert FILE_NM_5, MATCH_DT_5, MATCH_BY_5 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'FILE_NM_5',  'File Name',    7, 'Y', 'Y', '200px', 'table5', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_5', 'Matched Date', 8, 'Y', 'Y', '150px', 'table5', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_5', 'Matched By',   9, 'Y', 'Y', '150px', 'table5', 'N', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table5' ORDER BY disp_ord;


-- ============================================================
-- 3. pymn_0010_002 — Transfer Result Internal
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0010_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0010_002', 'ETC',          'ETC',                    99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0010_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9,  'Y', 'Y', '250px', 'table2', 'Y', NULL),
('pymn_0010_002', 'ETC_2',          'ETC',                    99, 'Y', 'Y', '100px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 4. pymn_0002_002 — Payment Result Vendor
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0002_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0002_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 5. pymn_0004_002 — Payment Result Expense
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0004_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0004_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 6. pymn_0006_002 — Payment Result Payroll
-- ============================================================
-- Pre-migration table1: 7 rows (1-7, no CHART_ACCT_CD in Haengsung)
-- Pre-migration table2: 7 rows (1-7)

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0006_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 8,  'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0006_002', 'ETC',          'ETC',                    99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0006_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 8,  'Y', 'Y', '250px', 'table2', 'Y', NULL),
('pymn_0006_002', 'ETC_2',          'ETC',                    99, 'Y', 'Y', '100px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 7. pymn_0008_002 — Payment Result Overseas
-- ============================================================

-- === TABLE 1 (currently empty — full insert matching Haengsung table2 structure) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0008_002', 'PAY_TRAN_SEQ', 'Payment Transaction Sequence', 1,  'Y', 'Y', '150px', 'table1', 'Y', ''),
('pymn_0008_002', 'BNFC_NM',      'Beneficiary Name',             2,  'Y', 'Y', '220px', 'table1', 'Y', ''),
('pymn_0008_002', 'BNFC_BANK_NM', 'Beneficiary Bank Name',        3,  'Y', 'Y', '225px', 'table1', 'Y', ''),
('pymn_0008_002', 'BNFC_ACCT_NO', 'Beneficiary Account Number',   4,  'Y', 'Y', '200px', 'table1', 'Y', ''),
('pymn_0008_002', 'BNFC_ADDR',    'Beneficiary Address',          5,  'Y', 'Y', '300px', 'table1', 'Y', ''),
('pymn_0008_002', 'TRNF_CCY_CD',  'Transfer Currency Code',       6,  'Y', 'Y', '150px', 'table1', 'Y', ''),
('pymn_0008_002', 'TRNF_AMT',     'Transfer Amount',              7,  'Y', 'Y', '160px', 'table1', 'Y', ''),
('pymn_0008_002', 'FEE_PAYER',    'Fee Payer',                    8,  'Y', 'Y', '120px', 'table1', 'N', NULL),
('pymn_0008_002', 'MEMO',         'Memo/Description',             9,  'Y', 'Y', '300px', 'table1', 'Y', ''),
('pymn_0008_002', 'PROC_STAT',    'Status',                       10, 'Y', 'Y', '100px', 'table1', 'Y', ''),
('pymn_0008_002', 'RSLT_DTTM',    'Result Date/Time',             11, 'Y', 'Y', '130px', 'table1', 'Y', NULL),
('pymn_0008_002', 'BANK_TRAN_ID', 'Banking Transaction ID',       12, 'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0008_002', 'ETC',          'ETC',                          99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

-- === TABLE 2 (append 3 missing fields after PROC_STAT_2 at 10) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0008_002', 'RSLT_DTTM_2',    'Result Date/Time',       11, 'Y', 'Y', '130px', 'table2', 'Y', NULL),
('pymn_0008_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 12, 'Y', 'Y', '250px', 'table2', 'Y', NULL),
('pymn_0008_002', 'ETC_2',          'ETC',                    99, 'Y', 'Y', '100px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 8. pymn_0009_003 — Scheduled Transfer Internal
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0009_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0009_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 9. pymn_0001_003 — Scheduled Payment Vendor
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0001_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0001_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 10. pymn_0003_003 — Scheduled Payment Expense
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0003_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0003_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 11. pymn_0005_003 — Scheduled Payment Payroll
-- ============================================================
-- Pre-migration table1: 4 rows (1-4, no CHART_ACCT_CD in Haengsung)
-- Pre-migration table2: 4 rows (1-4)

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0005_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 5, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0005_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 5, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0005_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0005_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0005_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 12. pymn_0007_004 — Scheduled Payment Overseas
-- ============================================================

-- === TABLE 1 (currently empty — full insert mirroring Haengsung table2 structure) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0007_004', 'BNFC_NM',      'Beneficiary Name',        1, 'Y', 'Y', '180px', 'table1', 'Y', NULL),
('pymn_0007_004', 'BNFC_BANK_NM', 'Beneficiary Bank Name',   2, 'Y', 'Y', '220px', 'table1', 'Y', NULL),
('pymn_0007_004', 'BNFC_ACCT_NO', 'Beneficiary Account No.', 3, 'Y', 'Y', '150px', 'table1', 'Y', NULL),
('pymn_0007_004', 'BNFC_ADDR',    'Beneficiary Address',     4, 'Y', 'Y', '200px', 'table1', 'Y', NULL),
('pymn_0007_004', 'TRNF_CCY_CD',  'Transfer Currency Code',  5, 'Y', 'Y', '120px', 'table1', 'Y', NULL),
('pymn_0007_004', 'TRNF_AMT',     'Transfer Amount',         6, 'Y', 'Y', '150px', 'table1', 'Y', NULL),
('pymn_0007_004', 'FEE_PAYER_CD', 'Fee Payer',               7, 'Y', 'Y', '80px',  'table1', 'Y', NULL),
('pymn_0007_004', 'MEMO',         'Memo',                    8, 'Y', 'Y', '150px', 'table1', 'Y', NULL),
('pymn_0007_004', 'BANK_TRAN_ID', 'Banking Transaction ID',  9, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

-- === TABLE 2 (append BANK_TRAN_ID_2 after MEMO_2 at 8) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0007_004', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 13. sys_env_set_infm — New environment setting (00079)
-- ============================================================

INSERT INTO sys_env_set_infm (env_set_inf, env_set_cont)
VALUES ('00079', 'N');

-- VERIFY
SELECT * FROM sys_env_set_infm WHERE env_set_inf = '00079';


-- ============================================================
-- 14. busi_env_set_infm — Propagate env setting 00079 to all tenants
-- ============================================================

INSERT INTO busi_env_set_infm (
     biz_key
,    env_set_inf
,    env_set_cont
,    mod_id
,    mod_dtm
)
SELECT
     T.biz_key
,    T.env_set_inf
,    T.env_set_cont
,    T.mod_id
,    T.mod_dtm
FROM (
    SELECT
         bi.biz_key
    ,    sesi.env_set_inf
    ,    sesi.env_set_cont
    ,    'SYSTEM'                                    AS mod_id
    ,    to_char(now(), 'YYYYMMDDHH24MISS')          AS mod_dtm
    FROM sys_env_set_infm sesi
    CROSS JOIN busi_infm bi
) T
WHERE env_set_inf = '00079';

-- VERIFY
SELECT * FROM busi_env_set_infm WHERE env_set_inf = '00079' ORDER BY biz_key;


-- ================================================================
-- ================================================================
-- CMC PROD — sys_scr_option_wecms Migration
-- Date: 2026-04-06
-- Tables: table1, table2, table3, table4 (no table5 in PROD)
-- ================================================================
-- ================================================================


-- ============================================================
-- 1. fnsh_0002_001 — Transaction History (VND)
-- ============================================================

-- === TABLE 1 (10 rows: 1-10, MEMO at 99) ===

-- Step 1: Shift existing rows 1–10 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table1'
  AND disp_ord <= 10;

-- Step 2: Insert TRNS_ID at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table1', 'N', NULL);

-- Step 3: Shift rows at 7–11 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table1'
  AND disp_ord >= 7
  AND disp_ord <= 11;

-- Step 4: Insert FILE_NM, MATCH_DT, MATCH_BY at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM',  'File Name',    7, 'Y', 'Y', '200px', 'table1', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT', 'Matched Date', 8, 'Y', 'Y', '150px', 'table1', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY', 'Matched By',   9, 'Y', 'Y', '150px', 'table1', 'N', NULL);

-- === TABLE 2 (9 rows: 1-9, MEMO_2 at 99) ===

-- Step 1: Shift existing rows 1–9 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table2'
  AND disp_ord <= 9;

-- Step 2: Insert TRNS_ID_2 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_2', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table2', 'N', NULL);

-- Step 3: Shift rows at 7–10 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table2'
  AND disp_ord >= 7
  AND disp_ord <= 10;

-- Step 4: Insert FILE_NM_2, MATCH_DT_2, MATCH_BY_2 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_2',  'File Name',    7, 'Y', 'Y', '200px', 'table2', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_2', 'Matched Date', 8, 'Y', 'Y', '150px', 'table2', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_2', 'Matched By',   9, 'Y', 'Y', '150px', 'table2', 'N', NULL);

-- === TABLE 3 (9 rows: 1-9, MEMO_3 at 99) ===

-- Step 1: Shift existing rows 1–9 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table3'
  AND disp_ord <= 9;

-- Step 2: Insert TRNS_ID_3 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_3', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table3', 'N', NULL);

-- Step 3: Shift rows at 7–10 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table3'
  AND disp_ord >= 7
  AND disp_ord <= 10;

-- Step 4: Insert FILE_NM_3, MATCH_DT_3, MATCH_BY_3 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_3',  'File Name',    7, 'Y', 'Y', '200px', 'table3', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_3', 'Matched Date', 8, 'Y', 'Y', '150px', 'table3', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_3', 'Matched By',   9, 'Y', 'Y', '150px', 'table3', 'N', NULL);

-- === TABLE 4 (8 rows: 1-8, MEMO_4 at 99) ===

-- Step 1: Shift existing rows 1–8 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table4'
  AND disp_ord <= 8;

-- Step 2: Insert TRNS_ID_4 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_4', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table4', 'N', NULL);

-- Step 3: Shift rows at 7–9 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table4'
  AND disp_ord >= 7
  AND disp_ord <= 9;

-- Step 4: Insert FILE_NM_4, MATCH_DT_4, MATCH_BY_4 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_4',  'File Name',    7, 'Y', 'Y', '200px', 'table4', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_4', 'Matched Date', 8, 'Y', 'Y', '150px', 'table4', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_4', 'Matched By',   9, 'Y', 'Y', '150px', 'table4', 'N', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table2' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table3' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table4' ORDER BY disp_ord;


-- ============================================================
-- 2. fnsh_0006_001 — Transaction History (FCY)
-- ============================================================

-- === TABLE 1 ===
-- Current disp_ord (1, 25-33, 125) are non-sequential → direct per-field UPDATE.

UPDATE sys_scr_option_wecms SET disp_ord = 2  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'TRSC_DT';
UPDATE sys_scr_option_wecms SET disp_ord = 3  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'IN_AMT';
UPDATE sys_scr_option_wecms SET disp_ord = 4  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'OUT_AMT';
UPDATE sys_scr_option_wecms SET disp_ord = 5  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'BAL';
UPDATE sys_scr_option_wecms SET disp_ord = 6  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RMRK';
UPDATE sys_scr_option_wecms SET disp_ord = 10 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'CHART_ACCT_CD';
UPDATE sys_scr_option_wecms SET disp_ord = 11 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_USAG_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 12 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_VAS_ACCT_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 13 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_TRCO_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 14 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'CNFM_BY_USER_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 99 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'MEMO';

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID',  'Transaction ID', 1, 'Y', 'Y', '190px', 'table1', 'N', NULL),
('fnsh_0006_001', 'FILE_NM',  'File Name',      7, 'Y', 'Y', '200px', 'table1', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT', 'Matched Date',   8, 'Y', 'Y', '150px', 'table1', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY', 'Matched By',     9, 'Y', 'Y', '150px', 'table1', 'N', NULL);

-- === TABLE 2 ===
-- Current disp_ord (1, 17-24, 116) are non-sequential → direct per-field UPDATE.

UPDATE sys_scr_option_wecms SET disp_ord = 2  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'TRSC_DT_2';
UPDATE sys_scr_option_wecms SET disp_ord = 3  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'IN_AMT_2';
UPDATE sys_scr_option_wecms SET disp_ord = 4  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'OUT_AMT_2';
UPDATE sys_scr_option_wecms SET disp_ord = 5  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'BAL_2';
UPDATE sys_scr_option_wecms SET disp_ord = 6  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'RMRK_2';
UPDATE sys_scr_option_wecms SET disp_ord = 10 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'CHART_ACCT_CD_2';
UPDATE sys_scr_option_wecms SET disp_ord = 11 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'RCV_PAY_DISP_VAS_ACCT_NM_2';
UPDATE sys_scr_option_wecms SET disp_ord = 12 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'RCV_PAY_DISP_TRCO_NM_2';
UPDATE sys_scr_option_wecms SET disp_ord = 13 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'CNFM_BY_USER_NM_2';
UPDATE sys_scr_option_wecms SET disp_ord = 99 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'MEMO_2';

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_2',  'Transaction ID', 1, 'Y', 'Y', '190px', 'table2', 'N', NULL),
('fnsh_0006_001', 'FILE_NM_2',  'File Name',      7, 'Y', 'Y', '200px', 'table2', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_2', 'Matched Date',   8, 'Y', 'Y', '150px', 'table2', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_2', 'Matched By',     9, 'Y', 'Y', '150px', 'table2', 'N', NULL);

-- === TABLE 3 ===
-- Current disp_ord (1, 9-16, 107) are non-sequential → direct per-field UPDATE.
-- Run UPDATEs in target-position order to avoid transient conflicts.

UPDATE sys_scr_option_wecms SET disp_ord = 2  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'TRSC_DT_3';
UPDATE sys_scr_option_wecms SET disp_ord = 3  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'IN_AMT_3';
UPDATE sys_scr_option_wecms SET disp_ord = 4  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'OUT_AMT_3';
UPDATE sys_scr_option_wecms SET disp_ord = 5  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'BAL_3';
UPDATE sys_scr_option_wecms SET disp_ord = 6  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'RMRK_3';
UPDATE sys_scr_option_wecms SET disp_ord = 10 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'CHART_ACCT_CD_3';
UPDATE sys_scr_option_wecms SET disp_ord = 11 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'RCV_PAY_DISP_USAG_NM_3';
UPDATE sys_scr_option_wecms SET disp_ord = 12 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'RCV_PAY_DISP_TRCO_NM_3';
UPDATE sys_scr_option_wecms SET disp_ord = 13 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'CNFM_BY_USER_NM_3';
UPDATE sys_scr_option_wecms SET disp_ord = 99 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'MEMO_3';

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_3',  'Transaction ID', 1, 'Y', 'Y', '190px', 'table3', 'N', NULL),
('fnsh_0006_001', 'FILE_NM_3',  'File Name',      7, 'Y', 'Y', '200px', 'table3', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_3', 'Matched Date',   8, 'Y', 'Y', '150px', 'table3', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_3', 'Matched By',     9, 'Y', 'Y', '150px', 'table3', 'N', NULL);

-- === TABLE 4 (8 rows: 1-8, MEMO_4 at 99 — sequential, shift logic) ===

-- Step 1: Shift existing rows 1–8 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table4'
  AND disp_ord <= 8;

-- Step 2: Insert TRNS_ID_4 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_4', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table4', 'N', NULL);

-- Step 3: Shift rows at 7–9 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table4'
  AND disp_ord >= 7
  AND disp_ord <= 9;

-- Step 4: Insert FILE_NM_4, MATCH_DT_4, MATCH_BY_4 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'FILE_NM_4',  'File Name',    7, 'Y', 'Y', '200px', 'table4', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_4', 'Matched Date', 8, 'Y', 'Y', '150px', 'table4', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_4', 'Matched By',   9, 'Y', 'Y', '150px', 'table4', 'N', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table4' ORDER BY disp_ord;


-- ============================================================
-- 3. pymn_0009_003 — Scheduled Transfer Internal
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0009_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0009_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- 4. pymn_0010_002 — SKIP (BANK_TRAN_ID + ETC already exist in PROD)
-- RSLT_DTTM migration handled in IMP_RSLT_DTTM_pymn_0010_002.sql


-- ============================================================
-- 5. pymn_0001_003 — Scheduled Payment Vendor
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0001_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0001_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003' AND dym_area_id = 'table2' ORDER BY disp_ord;
-- 6. pymn_0002_002 — SKIP (BANK_TRAN_ID + ETC already exist in PROD)
-- RSLT_DTTM migration handled in IMP_RSLT_DTTM_pymn_0010_002.sql


-- ============================================================
-- 7. pymn_0003_003 — Scheduled Payment Expense
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0003_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0003_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003' AND dym_area_id = 'table2' ORDER BY disp_ord;

-- 8. pymn_0004_002 — SKIP (BANK_TRAN_ID + ETC already exist in PROD)
-- RSLT_DTTM migration handled in IMP_RSLT_DTTM_pymn_0010_002.sql


-- ============================================================
-- 8b. pymn_0006_002 — Payment Result Payroll
-- ============================================================
-- Run AFTER IMP_RSLT_DTTM (RSLT_DTTM at 7, CHART_ACCT_CD shifted to 10)
-- table2 already has ETC_2 at 99

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0006_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 8,  'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0006_002', 'ETC',          'ETC',                    99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0006_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002' AND dym_area_id = 'table2' ORDER BY disp_ord;

-- ============================================================
-- 9. pymn_0007_004 — Scheduled Payment Overseas
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0007_004', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0007_004', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004' AND dym_area_id = 'table2' ORDER BY disp_ord;

-- 10. pymn_0008_002 — SKIP (BANK_TRAN_ID + ETC already exist in PROD)
-- RSLT_DTTM migration handled in IMP_RSLT_DTTM_pymn_0010_002.sql


-- ================================================================
-- ================================================================
-- Haengsung PROD — sys_scr_option_wecms Migration
-- Date: 2026-04-06
-- Tables: table1, table2, table3, table4, table5
-- ================================================================
-- ================================================================


-- ============================================================
-- 1. fnsh_0002_001 — Transaction History (VND)
-- ============================================================

-- === TABLE 1 (10 rows: 1-10, MEMO at 99) ===

-- Step 1: Shift existing rows 1–10 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table1'
  AND disp_ord <= 10;

-- Step 2: Insert TRNS_ID at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table1', 'N', NULL);

-- Step 3: Shift rows at 7–11 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table1'
  AND disp_ord >= 7
  AND disp_ord <= 11;

-- Step 4: Insert FILE_NM, MATCH_DT, MATCH_BY at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM',  'File Name',    7, 'Y', 'Y', '200px', 'table1', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT', 'Matched Date', 8, 'Y', 'Y', '150px', 'table1', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY', 'Matched By',   9, 'Y', 'Y', '150px', 'table1', 'N', NULL);

-- === TABLE 2 (9 rows: 1-9, MEMO_2 at 99) ===

-- Step 1: Shift existing rows 1–9 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table2'
  AND disp_ord <= 9;

-- Step 2: Insert TRNS_ID_2 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_2', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table2', 'N', NULL);

-- Step 3: Shift rows at 7–10 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table2'
  AND disp_ord >= 7
  AND disp_ord <= 10;

-- Step 4: Insert FILE_NM_2, MATCH_DT_2, MATCH_BY_2 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_2',  'File Name',    7, 'Y', 'Y', '200px', 'table2', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_2', 'Matched Date', 8, 'Y', 'Y', '150px', 'table2', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_2', 'Matched By',   9, 'Y', 'Y', '150px', 'table2', 'N', NULL);

-- === TABLE 3 (9 rows: 1-9, MEMO_3 at 99) ===

-- Step 1: Shift existing rows 1–9 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table3'
  AND disp_ord <= 9;

-- Step 2: Insert TRNS_ID_3 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_3', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table3', 'N', NULL);

-- Step 3: Shift rows at 7–10 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table3'
  AND disp_ord >= 7
  AND disp_ord <= 10;

-- Step 4: Insert FILE_NM_3, MATCH_DT_3, MATCH_BY_3 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_3',  'File Name',    7, 'Y', 'Y', '200px', 'table3', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_3', 'Matched Date', 8, 'Y', 'Y', '150px', 'table3', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_3', 'Matched By',   9, 'Y', 'Y', '150px', 'table3', 'N', NULL);

-- === TABLE 4 (8 rows: 1-8, MEMO_4 at 99) ===

-- Step 1: Shift existing rows 1–8 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table4'
  AND disp_ord <= 8;

-- Step 2: Insert TRNS_ID_4 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_4', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table4', 'N', NULL);

-- Step 3: Shift rows at 7–9 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table4'
  AND disp_ord >= 7
  AND disp_ord <= 9;

-- Step 4: Insert FILE_NM_4, MATCH_DT_4, MATCH_BY_4 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_4',  'File Name',    7, 'Y', 'Y', '200px', 'table4', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_4', 'Matched Date', 8, 'Y', 'Y', '150px', 'table4', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_4', 'Matched By',   9, 'Y', 'Y', '150px', 'table4', 'N', NULL);

-- === TABLE 5 (7 rows: 1-7, MEMO_5 at 99) ===

-- Step 1: Shift existing rows 1–7 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table5'
  AND disp_ord <= 7;

-- Step 2: Insert TRNS_ID_5 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'TRNS_ID_5', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table5', 'N', NULL);

-- Step 3: Shift rows at 7–8 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0002_001'
  AND dym_area_id = 'table5'
  AND disp_ord >= 7
  AND disp_ord <= 8;

-- Step 4: Insert FILE_NM_5, MATCH_DT_5, MATCH_BY_5 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0002_001', 'FILE_NM_5',  'File Name',    7, 'Y', 'Y', '200px', 'table5', 'N', NULL),
('fnsh_0002_001', 'MATCH_DT_5', 'Matched Date', 8, 'Y', 'Y', '150px', 'table5', 'N', NULL),
('fnsh_0002_001', 'MATCH_BY_5', 'Matched By',   9, 'Y', 'Y', '150px', 'table5', 'N', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table2' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table3' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table4' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0002_001' AND dym_area_id = 'table5' ORDER BY disp_ord;


-- ============================================================
-- 2. fnsh_0006_001 — Transaction History (FCY)
-- ============================================================

-- === TABLE 1 ===
-- Current disp_ord (1, 25-33, 125) are non-sequential → direct per-field UPDATE.

UPDATE sys_scr_option_wecms SET disp_ord = 2  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'TRSC_DT';
UPDATE sys_scr_option_wecms SET disp_ord = 3  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'IN_AMT';
UPDATE sys_scr_option_wecms SET disp_ord = 4  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'OUT_AMT';
UPDATE sys_scr_option_wecms SET disp_ord = 5  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'BAL';
UPDATE sys_scr_option_wecms SET disp_ord = 6  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RMRK';
UPDATE sys_scr_option_wecms SET disp_ord = 10 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'CHART_ACCT_CD';
UPDATE sys_scr_option_wecms SET disp_ord = 11 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_USAG_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 12 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_VAS_ACCT_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 13 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'RCV_PAY_DISP_TRCO_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 14 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'CNFM_BY_USER_NM';
UPDATE sys_scr_option_wecms SET disp_ord = 99 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' AND field_id = 'MEMO';

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID',  'Transaction ID', 1, 'Y', 'Y', '190px', 'table1', 'N', NULL),
('fnsh_0006_001', 'FILE_NM',  'File Name',      7, 'Y', 'Y', '200px', 'table1', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT', 'Matched Date',   8, 'Y', 'Y', '150px', 'table1', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY', 'Matched By',     9, 'Y', 'Y', '150px', 'table1', 'N', NULL);

-- === TABLE 2 ===
-- Current disp_ord (1, 17-24, 116) are non-sequential → direct per-field UPDATE.

UPDATE sys_scr_option_wecms SET disp_ord = 2  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'TRSC_DT_2';
UPDATE sys_scr_option_wecms SET disp_ord = 3  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'IN_AMT_2';
UPDATE sys_scr_option_wecms SET disp_ord = 4  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'OUT_AMT_2';
UPDATE sys_scr_option_wecms SET disp_ord = 5  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'BAL_2';
UPDATE sys_scr_option_wecms SET disp_ord = 6  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'RMRK_2';
UPDATE sys_scr_option_wecms SET disp_ord = 10 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'CHART_ACCT_CD_2';
UPDATE sys_scr_option_wecms SET disp_ord = 11 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'RCV_PAY_DISP_VAS_ACCT_NM_2';
UPDATE sys_scr_option_wecms SET disp_ord = 12 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'RCV_PAY_DISP_TRCO_NM_2';
UPDATE sys_scr_option_wecms SET disp_ord = 13 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'CNFM_BY_USER_NM_2';
UPDATE sys_scr_option_wecms SET disp_ord = 99 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' AND field_id = 'MEMO_2';

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_2',  'Transaction ID', 1, 'Y', 'Y', '190px', 'table2', 'N', NULL),
('fnsh_0006_001', 'FILE_NM_2',  'File Name',      7, 'Y', 'Y', '200px', 'table2', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_2', 'Matched Date',   8, 'Y', 'Y', '150px', 'table2', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_2', 'Matched By',     9, 'Y', 'Y', '150px', 'table2', 'N', NULL);

-- === TABLE 3 ===
-- Current disp_ord (1, 9-16, 107) are non-sequential → direct per-field UPDATE.

UPDATE sys_scr_option_wecms SET disp_ord = 2  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'TRSC_DT_3';
UPDATE sys_scr_option_wecms SET disp_ord = 3  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'IN_AMT_3';
UPDATE sys_scr_option_wecms SET disp_ord = 4  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'OUT_AMT_3';
UPDATE sys_scr_option_wecms SET disp_ord = 5  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'BAL_3';
UPDATE sys_scr_option_wecms SET disp_ord = 6  WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'RMRK_3';
UPDATE sys_scr_option_wecms SET disp_ord = 10 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'CHART_ACCT_CD_3';
UPDATE sys_scr_option_wecms SET disp_ord = 11 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'RCV_PAY_DISP_USAG_NM_3';
UPDATE sys_scr_option_wecms SET disp_ord = 12 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'RCV_PAY_DISP_TRCO_NM_3';
UPDATE sys_scr_option_wecms SET disp_ord = 13 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'CNFM_BY_USER_NM_3';
UPDATE sys_scr_option_wecms SET disp_ord = 99 WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' AND field_id = 'MEMO_3';

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_3',  'Transaction ID', 1, 'Y', 'Y', '190px', 'table3', 'N', NULL),
('fnsh_0006_001', 'FILE_NM_3',  'File Name',      7, 'Y', 'Y', '200px', 'table3', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_3', 'Matched Date',   8, 'Y', 'Y', '150px', 'table3', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_3', 'Matched By',     9, 'Y', 'Y', '150px', 'table3', 'N', NULL);

-- === TABLE 4 (8 rows: 1-8, MEMO_4 at 99 — sequential, shift logic) ===

-- Step 1: Shift existing rows 1–8 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table4'
  AND disp_ord <= 8;

-- Step 2: Insert TRNS_ID_4 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_4', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table4', 'N', NULL);

-- Step 3: Shift rows at 7–9 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table4'
  AND disp_ord >= 7
  AND disp_ord <= 9;

-- Step 4: Insert FILE_NM_4, MATCH_DT_4, MATCH_BY_4 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'FILE_NM_4',  'File Name',    7, 'Y', 'Y', '200px', 'table4', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_4', 'Matched Date', 8, 'Y', 'Y', '150px', 'table4', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_4', 'Matched By',   9, 'Y', 'Y', '150px', 'table4', 'N', NULL);

-- === TABLE 5 (7 rows: 1-7, MEMO_5 at 99 — sequential, shift logic) ===

-- Step 1: Shift existing rows 1–7 up by 1 to free slot 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table5'
  AND disp_ord <= 7;

-- Step 2: Insert TRNS_ID_5 at position 1
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'TRNS_ID_5', 'Transaction ID', 1, 'Y', 'Y', '190px', 'table5', 'N', NULL);

-- Step 3: Shift rows at 7–8 up by 3 to free slots 7–9
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 3
WHERE web_svc_id = 'fnsh_0006_001'
  AND dym_area_id = 'table5'
  AND disp_ord >= 7
  AND disp_ord <= 8;

-- Step 4: Insert FILE_NM_5, MATCH_DT_5, MATCH_BY_5 at positions 7–9
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('fnsh_0006_001', 'FILE_NM_5',  'File Name',    7, 'Y', 'Y', '200px', 'table5', 'N', NULL),
('fnsh_0006_001', 'MATCH_DT_5', 'Matched Date', 8, 'Y', 'Y', '150px', 'table5', 'N', NULL),
('fnsh_0006_001', 'MATCH_BY_5', 'Matched By',   9, 'Y', 'Y', '150px', 'table5', 'N', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table2' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table3' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table4' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'fnsh_0006_001' AND dym_area_id = 'table5' ORDER BY disp_ord;


-- ============================================================
-- 3. pymn_0010_002 — Transfer Result Internal
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0010_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0010_002', 'ETC',          'ETC',                    99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0010_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9,  'Y', 'Y', '250px', 'table2', 'Y', NULL),
('pymn_0010_002', 'ETC_2',          'ETC',                    99, 'Y', 'Y', '100px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 4. pymn_0009_003 — Scheduled Transfer Internal
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0009_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0009_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0009_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 5. pymn_0001_003 — Scheduled Payment Vendor
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0001_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0001_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0001_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 6. pymn_0002_002 — Payment Result Vendor
-- ============================================================
-- PROD already has ETC/ETC_2 at 99 — only insert BANK_TRAN_ID

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0002_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0002_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 7. pymn_0003_003 — Scheduled Payment Expense
-- ============================================================

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0003_003', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0003_003', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 7, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0003_003' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 8. pymn_0004_002 — Payment Result Expense
-- ============================================================
-- PROD already has ETC/ETC_2 at 99 — only insert BANK_TRAN_ID

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0004_002', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0004_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 9. pymn_0007_004 — Scheduled Payment Overseas
-- ============================================================

-- === TABLE 1 (currently empty — full insert mirroring table2 structure) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0007_004', 'BNFC_NM',      'Beneficiary Name',        1, 'Y', 'Y', '180px', 'table1', 'Y', NULL),
('pymn_0007_004', 'BNFC_BANK_NM', 'Beneficiary Bank Name',   2, 'Y', 'Y', '220px', 'table1', 'Y', NULL),
('pymn_0007_004', 'BNFC_ACCT_NO', 'Beneficiary Account No.', 3, 'Y', 'Y', '150px', 'table1', 'Y', NULL),
('pymn_0007_004', 'BNFC_ADDR',    'Beneficiary Address',     4, 'Y', 'Y', '200px', 'table1', 'Y', NULL),
('pymn_0007_004', 'TRNF_CCY_CD',  'Transfer Currency Code',  5, 'Y', 'Y', '120px', 'table1', 'Y', NULL),
('pymn_0007_004', 'TRNF_AMT',     'Transfer Amount',         6, 'Y', 'Y', '150px', 'table1', 'Y', NULL),
('pymn_0007_004', 'FEE_PAYER_CD', 'Fee Payer',               7, 'Y', 'Y', '80px',  'table1', 'Y', NULL),
('pymn_0007_004', 'MEMO',         'Memo',                    8, 'Y', 'Y', '150px', 'table1', 'Y', NULL),
('pymn_0007_004', 'BANK_TRAN_ID', 'Banking Transaction ID',  9, 'Y', 'Y', '250px', 'table1', 'Y', NULL);

-- === TABLE 2 (8 rows: 1-8, append BANK_TRAN_ID_2) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0007_004', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 9, 'Y', 'Y', '250px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0007_004' AND dym_area_id = 'table2' ORDER BY disp_ord;


-- ============================================================
-- 10. pymn_0008_002 — Payment Result Overseas
-- ============================================================

-- === TABLE 1 (currently empty — full insert mirroring table2 structure) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0008_002', 'PAY_TRAN_SEQ', 'Payment Transaction Sequence', 1,  'Y', 'Y', '150px', 'table1', 'Y', ''),
('pymn_0008_002', 'BNFC_NM',      'Beneficiary Name',             2,  'Y', 'Y', '220px', 'table1', 'Y', ''),
('pymn_0008_002', 'BNFC_BANK_NM', 'Beneficiary Bank Name',        3,  'Y', 'Y', '225px', 'table1', 'Y', ''),
('pymn_0008_002', 'BNFC_ACCT_NO', 'Beneficiary Account Number',   4,  'Y', 'Y', '200px', 'table1', 'Y', ''),
('pymn_0008_002', 'BNFC_ADDR',    'Beneficiary Address',          5,  'Y', 'Y', '300px', 'table1', 'Y', ''),
('pymn_0008_002', 'TRNF_CCY_CD',  'Transfer Currency Code',       6,  'Y', 'Y', '150px', 'table1', 'Y', ''),
('pymn_0008_002', 'TRNF_AMT',     'Transfer Amount',              7,  'Y', 'Y', '160px', 'table1', 'Y', ''),
('pymn_0008_002', 'FEE_PAYER',    'Fee Payer',                    8,  'Y', 'Y', '120px', 'table1', 'N', NULL),
('pymn_0008_002', 'MEMO',         'Memo/Description',             9,  'Y', 'Y', '300px', 'table1', 'Y', ''),
('pymn_0008_002', 'PROC_STAT',    'Status',                       10, 'Y', 'Y', '100px', 'table1', 'Y', ''),
('pymn_0008_002', 'RSLT_DTTM',    'Result Date/Time',             11, 'Y', 'Y', '130px', 'table1', 'Y', NULL),
('pymn_0008_002', 'BANK_TRAN_ID', 'Banking Transaction ID',       12, 'Y', 'Y', '250px', 'table1', 'Y', NULL),
('pymn_0008_002', 'ETC',          'ETC',                          99, 'Y', 'Y', '100px', 'table1', 'Y', NULL);

-- === TABLE 2 (10 rows: 1-10, append 3 fields) ===
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont
) VALUES
('pymn_0008_002', 'RSLT_DTTM_2',    'Result Date/Time',       11, 'Y', 'Y', '130px', 'table2', 'Y', NULL),
('pymn_0008_002', 'BANK_TRAN_ID_2', 'Banking Transaction ID', 12, 'Y', 'Y', '250px', 'table2', 'Y', NULL),
('pymn_0008_002', 'ETC_2',          'ETC',                    99, 'Y', 'Y', '100px', 'table2', 'Y', NULL);

DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002';

-- VERIFY
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002' AND dym_area_id = 'table1' ORDER BY disp_ord;
SELECT * FROM sys_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002' AND dym_area_id = 'table2' ORDER BY disp_ord;
