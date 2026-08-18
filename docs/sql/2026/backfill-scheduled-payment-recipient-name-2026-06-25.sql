-- WeCMS Manual Database Change
-- Date: 2026-06-25
-- Source: migrated from Claude Code (AI)/sql/scdul_pymn_sumr_detl_rcpt_nm_backfill_20260625.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: Shared dev DB 172.168.5.172:5432/wecmsdevdb
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — the pre-change value was NULL for the affected rows
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- Script 2 of 3 — Backfill SCDUL_PYMN_SUMR_DETL.RCPT_NM for EXISTING
--                 Internal Transfer test batches
-- ----------------------------------------------------------------------------
-- Feature       : Internal Transfer > Request Approval — "Recipient Name"
-- Target DB     : SHARED DEV  172.168.5.172:5432/wecmsdevdb  (user wecmsdev_owner)
-- Tenant        : VN_VNM0004_000001
-- Executed by   : DBA, BY HAND. NOT a Flyway migration.
-- Run order     : 2nd — MUST run AFTER Script 1
--                 (acct_mstr_acct_nm_seed_20260625.sql). This script copies
--                 ACCT_MSTR.ACCT_NM, so Script 1 must have populated it first.
-- Idempotent    : YES — updates only detail rows where RCPT_NM IS NULL.
-- Optional      : This backfill is only needed if you want the ALREADY-uploaded
--                 test batches (e.g. VO_260625_002 / scdul_pymn_no 1,5,6,7,10)
--                 to display a Recipient Name immediately. Future uploads resolve
--                 RCPT_NM on their own once Script 1 has run.
--
-- WHY ----------------------------------------------------------------------
-- RCPT_NM is denormalized onto the detail row at UPLOAD time; the detail()
-- read path returns the stored value verbatim and does NOT re-resolve it. So
-- fixing ACCT_MSTR (Script 1) only helps NEW uploads. To make existing rows
-- show a name, copy ACCT_MSTR.ACCT_NM onto the stored RCPT_NM using the SAME
-- lookup key the parser uses: detail.FINC_MDA_NO = acct_mstr.ACCT_NO.
--
-- SCOPE GUARD --------------------------------------------------------------
-- Restricted to SCDUL_PYMN_TYPE_CD = '03' (Internal Transfer) ONLY. Internal
-- Transfer targets one of the company's OWN accounts, so RCPT_NM legitimately
-- equals ACCT_MSTR.ACCT_NM. Vendor ('01') / Expense ('02') / Payroll ('00')
-- batches resolve their recipient from CONTACTS / payroll data, NOT ACCT_MSTR —
-- they are deliberately EXCLUDED so this script never mislabels them.
--
-- AFFECTED ROWS : Internal-Transfer detail rows for the tenant whose RCPT_NM is
--                 NULL and whose FINC_MDA_NO matches a tenant ACCT_MSTR row that
--                 now has a non-null ACCT_NM (all current test rows deposit to
--                 700005285280).
-- ============================================================================

BEGIN;

UPDATE scdul_pymn_sumr_detl AS d
   SET rcpt_nm = a.acct_nm
  FROM scdul_pymn_sumr AS s,
       acct_mstr        AS a
 WHERE d.biz_key            = 'VN_VNM0004_000001'
   AND s.biz_key            = d.biz_key
   AND s.scdul_pymn_no      = d.scdul_pymn_no
   AND s.scdul_pymn_type_cd = '03'           -- Internal Transfer ONLY
   AND a.biz_key            = d.biz_key
   AND a.acct_no            = d.finc_mda_no   -- same key the parser uses
   AND a.acct_nm IS NOT NULL
   AND d.rcpt_nm IS NULL;

-- Verify (expect the Internal Transfer detail rows to now show RCPT_NM):
-- SELECT d.scdul_pymn_no, d.scdul_pymn_detl_no, d.finc_mda_no, d.rcpt_nm
--   FROM scdul_pymn_sumr_detl d
--   JOIN scdul_pymn_sumr s
--     ON s.biz_key = d.biz_key AND s.scdul_pymn_no = d.scdul_pymn_no
--  WHERE d.biz_key = 'VN_VNM0004_000001' AND s.scdul_pymn_type_cd = '03'
--  ORDER BY d.scdul_pymn_no DESC;

COMMIT;

-- ============================================================================
-- ROLLBACK — clears RCPT_NM back to NULL on the Internal Transfer rows this
-- script set (matched by the seeded value + the '03' type scope).
-- ----------------------------------------------------------------------------
-- BEGIN;
-- UPDATE scdul_pymn_sumr_detl AS d
--    SET rcpt_nm = NULL
--   FROM scdul_pymn_sumr AS s
--  WHERE d.biz_key            = 'VN_VNM0004_000001'
--    AND s.biz_key            = d.biz_key
--    AND s.scdul_pymn_no      = d.scdul_pymn_no
--    AND s.scdul_pymn_type_cd = '03'
--    AND d.rcpt_nm            = 'CONG TY TNHH WEBCASH VIET NAM';
-- COMMIT;
-- ============================================================================
