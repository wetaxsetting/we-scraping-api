-- WeCMS Manual Database Change
-- Date: 2026-04-08
-- Source: migrated from Claude Code (AI)/sql/haengsung_close_accts_20260408.sql
-- Related record: docs/jex features file haengsung-hide-closed-bank-accounts.md (path as cited by the original script header)
-- Type: DML
-- Execution: MANUAL
-- Target environments: DEV then UAT then PROD (per the original script header)
-- Executor: DBA TEAM
-- Rollback: Reversible by setting ACTV_YN back to 'Y' for the same accounts; no data is deleted
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================
-- [IMP] Hide Closed Bank Accounts — HAENGSUNG ELECTRONICS
-- ============================================================
-- Client  : HAENGSUNG ELECTRONICS
-- biz_key : VN_VNM0004_000014
-- Feature : haengsung-hide-closed-bank-accounts.md
-- Author  : [developer name]
-- Date    : 2026-04-08
--
-- Purpose : Mark physically closed bank accounts as inactive
--           (actv_yn = 'N') so they are hidden from all screens.
--           Data is NOT deleted.
--
-- Run on  : DEV → UAT → PROD (same script, same biz_key)
-- Schema  : wecmsvndev (DEV/UAT) | wecmsvn (PROD — confirm schema name)
-- ============================================================

-- STEP 1: Verify count before update
-- Run this SELECT first. Confirm the count matches expected closed accounts.
-- ============================================================
SELECT bank_cd, acct_no, acct_nick_nm, ccy_cd, acct_type_cd, actv_yn
FROM wecmsvndev.acct_mstr
WHERE biz_key = 'VN_VNM0004_000014'
  AND (bank_cd, acct_no) IN (
    -- TODO: Insert confirmed closed account list from client (Ms. Kim Thư)
    -- Format: ('<BANK_CD>', '<ACCT_NO>'),
    -- Example:
    -- ('11704004', '001063875442'),
    -- ('11704004', '001065042693'),
    -- ('11704001', '114000187729'),
    ('<BANK_CD_1>', '<ACCT_NO_1>'),
    ('<BANK_CD_2>', '<ACCT_NO_2>')
  );


-- STEP 2: Mark closed accounts as inactive
-- Run ONLY after verifying the SELECT count above is correct.
-- ============================================================
UPDATE wecmsvndev.acct_mstr
SET    actv_yn = 'N',
       mod_dtm = TO_CHAR(CURRENT_TIMESTAMP, 'YYYYMMDDHH24MISS')
WHERE  biz_key = 'VN_VNM0004_000014'
  AND  (bank_cd, acct_no) IN (
    -- TODO: Same list as STEP 1
    ('<BANK_CD_1>', '<ACCT_NO_1>'),
    ('<BANK_CD_2>', '<ACCT_NO_2>')
  );


-- STEP 3: Verify after update
-- ============================================================
SELECT bank_cd, acct_no, acct_nick_nm, ccy_cd, actv_yn, mod_dtm
FROM wecmsvndev.acct_mstr
WHERE biz_key = 'VN_VNM0004_000014'
  AND actv_yn = 'N'
ORDER BY ccy_cd, bank_cd, acct_no;
