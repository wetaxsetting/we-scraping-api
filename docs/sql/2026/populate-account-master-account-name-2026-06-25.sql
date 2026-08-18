-- WeCMS Manual Database Change
-- Date: 2026-06-25
-- Source: migrated from Claude Code (AI)/sql/acct_mstr_acct_nm_seed_20260625.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: Shared dev DB 172.168.5.172:5432/wecmsdevdb
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — the pre-change value was NULL for the affected rows; no rollback documented in the original script
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- Script 1 of 3 — Populate ACCT_MSTR.ACCT_NM for tenant test accounts
-- ----------------------------------------------------------------------------
-- Feature       : Internal Transfer > Request Approval — "Recipient Name"
-- Target DB     : SHARED DEV  172.168.5.172:5432/wecmsdevdb  (user wecmsdev_owner)
-- Tenant        : VN_VNM0004_000001  (CÔNG TY TNHH WEBCASH VIỆT NAM, PRDT_TYPE 'F')
-- Executed by   : DBA, BY HAND. NOT a Flyway migration (Flyway is disabled on the
--                 shared dev DB; schema is external/pre-migrated).
-- Run order     : 1st.  Script 2 (RCPT_NM backfill) DEPENDS on this script —
--                 it reads ACCT_MSTR.ACCT_NM. Script 3 is independent.
-- Idempotent    : YES — updates only rows where ACCT_NM IS NULL.
--
-- WHY ----------------------------------------------------------------------
-- The Request Approval line resolver (InternalTransferExcelParser) sets
-- SCDUL_PYMN_SUMR_DETL.RCPT_NM from the company's OWN account directory:
-- ACCT_MSTR.ACCT_NM, looked up by (BIZ_KEY, FINC_MDA_NO). On the shared dev DB
-- all 11 of this tenant's ACCT_MSTR rows have ACCT_NM = NULL, so the resolver
-- leaves RCPT_NM null and the Recipient Name column renders blank. This is a
-- DATA gap, not a code defect (read mapper + write parser are both correct).
--
-- This script writes the holder name onto the tenant's accounts. The value
-- 'CONG TY TNHH WEBCASH VIET NAM' is the ASCII holder name the live system
-- displays (also already present in these rows' ACCT_NICK_NM). Account-holder
-- name for own accounts = the company itself.
--
-- AFFECTED ROWS : up to 11 ACCT_MSTR rows for tenant VN_VNM0004_000001
--                 (only those currently NULL).
-- NOTE          : ACCT_MSTR.ACCT_NM is normally populated by the bank-balance
--                 sync (NXiSAS scrape). This is a manual stand-in for R&D so the
--                 resolver has a name to copy on the NEXT upload. It does NOT
--                 retroactively fill already-uploaded detail rows — that is what
--                 Script 2 does (RCPT_NM is denormalized at upload time and the
--                 detail() read does NOT re-resolve it).
-- ============================================================================

BEGIN;

UPDATE acct_mstr
   SET acct_nm = 'CONG TY TNHH WEBCASH VIET NAM'
 WHERE biz_key = 'VN_VNM0004_000001'
   AND acct_nm IS NULL
   AND acct_no IN (
        '700005285280',  -- VND (deposit acct used by the Internal Transfer test batches)
        '700005285299',  -- USD (withdrawal acct in the screenshot)
        '700022499445',  -- VND
        '700022964690',  -- VND
        '700034110936',  -- KRW
        '700035842686',  -- USD
        '703012202658',  -- VND (E-Time Deposit)
        '703012365769',  -- VND (E-Time Deposit)
        '703012794576',  -- VND (E-Time Deposit)
        '703012794583',  -- VND (E-Time Deposit)
        '703012794591'   -- VND (E-Time Deposit)
   );

-- Verify (expect 11 rows, all ACCT_NM populated):
-- SELECT acct_no, ccy_cd, acct_nm FROM acct_mstr
--  WHERE biz_key = 'VN_VNM0004_000001' ORDER BY acct_no;

COMMIT;

-- ============================================================================
-- ROLLBACK — reverts only the accounts this script targets, back to NULL.
-- Run as its own statement if you need to undo Script 1.
-- ----------------------------------------------------------------------------
-- BEGIN;
-- UPDATE acct_mstr
--    SET acct_nm = NULL
--  WHERE biz_key = 'VN_VNM0004_000001'
--    AND acct_nm = 'CONG TY TNHH WEBCASH VIET NAM'
--    AND acct_no IN (
--         '700005285280','700005285299','700022499445','700022964690',
--         '700034110936','700035842686','703012202658','703012365769',
--         '703012794576','703012794583','703012794591'
--    );
-- COMMIT;
-- ============================================================================
