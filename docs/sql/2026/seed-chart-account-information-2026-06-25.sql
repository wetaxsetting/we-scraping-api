-- WeCMS Manual Database Change
-- Date: 2026-06-25
-- Source: migrated from Claude Code (AI)/sql/chart_acct_infm_seed_20260625.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: Shared dev DB 172.168.5.172:5432/wecmsdevdb
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- Script 3 of 3 — Seed minimum CHART_ACCT_INFM for Reporting Account display
-- ----------------------------------------------------------------------------
-- Feature       : Internal Transfer > Request Approval — "Reporting Account"
-- Target DB     : SHARED DEV  172.168.5.172:5432/wecmsdevdb  (user wecmsdev_owner)
-- Tenant        : VN_VNM0004_000001
-- Executed by   : DBA, BY HAND. NOT a Flyway migration.
-- Run order     : Independent — may run before/after Scripts 1 & 2.
-- Idempotent    : YES — INSERT ... ON CONFLICT DO NOTHING.
--
-- WHY ----------------------------------------------------------------------
-- CHART_ACCT_INFM is EMPTY on the shared dev DB (0 rows, all tenants), so:
--   (a) the Reporting Account breadcrumb resolver (TransferLineDetailMapper
--       .buildChartBreadcrumb) has no parent chain to walk, and
--   (b) any upload carrying a Reporting Account code would be REJECTED at parse
--       time ("not registered or inactive") because the code can't be resolved.
-- This seeds the minimal chart-account chain for code 2001001, copied verbatim
-- from JEX DEV for this tenant, so future uploads can carry a Reporting Account
-- and the detail/approval popup can render its code + breadcrumb.
--
-- SOURCE (JEX DEV, biz_key VN_VNM0004_000001) ------------------------------
--   ACC-02  seq 74  EXPENSES         parent NULL      (restricted root)
--   Code004 seq 75  PAYMENT          parent ACC-02
--   2001001 seq 104 Payment K1 (BLU) parent 2001001   (SELF-REF — grandfathered
--                                                       DEV anomaly, see V97)
-- Seq numbers + names are the authentic DEV values (table is empty locally → no
-- PK/UK collision). reg_id/mod_id = biz_key per the V96 restricted-root seed
-- convention.
--
-- BREADCRUMB NOTE ----------------------------------------------------------
-- Because 2001001's real parent is itself, the cycle-safe resolver renders the
-- breadcrumb as just "Payment K1 (BLU)" (the leaf), NOT "EXPENSES > PAYMENT".
-- The Reporting Account code still displays. If you want the full
-- "EXPENSES > PAYMENT" breadcrumb for the demo, run the OPTIONAL block at the
-- bottom (re-parents 2001001 → Code004). That DIVERGES from legacy DEV data, so
-- it is a deliberate demo-only choice — leave it commented for legacy fidelity.
--
-- DISPLAY ON EXISTING ROWS -------------------------------------------------
-- Existing test detail rows have CHART_ACCT_CD = NULL, so seeding alone will NOT
-- make them show a Reporting Account. Only future uploads (Excel col 6 carrying
-- '2001001') will. An OPTIONAL demo backfill for one batch is at the bottom.
--
-- AFFECTED ROWS : up to 3 new CHART_ACCT_INFM rows for tenant VN_VNM0004_000001.
-- ============================================================================

BEGIN;

INSERT INTO chart_acct_infm (
    biz_key, chart_acct_seq_no, chart_acct_cd,
    chart_acct_nm_en, chart_acct_nm_vn, chart_acct_nm_ko,
    chart_acct_type, parent_chart_acct_cd, stts, del_yn,
    reg_id, reg_dtm, mod_id, mod_dtm, cal_rule
) VALUES
    ('VN_VNM0004_000001', '74',  'ACC-02',  'EXPENSES',         'EXPENSES',       '지출',
        NULL, NULL,      'A', 'N', 'VN_VNM0004_000001', '20260625000000', 'VN_VNM0004_000001', '20260625000000', NULL),
    ('VN_VNM0004_000001', '75',  'Code004', 'PAYMENT',          'PAYMENT',        '경비',
        NULL, 'ACC-02',  'A', 'N', 'VN_VNM0004_000001', '20260625000000', 'VN_VNM0004_000001', '20260625000000', NULL),
    ('VN_VNM0004_000001', '104', '2001001', 'Payment K1 (BLU)', 'Chi trả 1 (BLU)', '지급(K1 BLU)',
        NULL, '2001001', 'A', 'N', 'VN_VNM0004_000001', '20260625000000', 'VN_VNM0004_000001', '20260625000000', NULL)
ON CONFLICT DO NOTHING;

-- Verify (expect 3 rows):
-- SELECT chart_acct_seq_no, chart_acct_cd, chart_acct_nm_en, parent_chart_acct_cd
--   FROM chart_acct_infm WHERE biz_key = 'VN_VNM0004_000001' ORDER BY chart_acct_cd;

COMMIT;

-- ============================================================================
-- OPTIONAL A (DEMO ONLY) — full "EXPENSES > PAYMENT" breadcrumb.
-- Re-parents 2001001 to Code004. DIVERGES from legacy DEV (which is self-ref).
-- ----------------------------------------------------------------------------
-- BEGIN;
-- UPDATE chart_acct_infm SET parent_chart_acct_cd = 'Code004'
--  WHERE biz_key = 'VN_VNM0004_000001' AND chart_acct_seq_no = '104';
-- COMMIT;
-- -- revert: UPDATE chart_acct_infm SET parent_chart_acct_cd = '2001001'
-- --          WHERE biz_key='VN_VNM0004_000001' AND chart_acct_seq_no='104';

-- ============================================================================
-- OPTIONAL B (DEMO ONLY) — make ONE existing Internal Transfer batch display a
-- Reporting Account by stamping the now-seeded code onto its detail row(s).
-- Pick the batch you want to demo (example: scdul_pymn_no = '10').
-- ----------------------------------------------------------------------------
-- BEGIN;
-- UPDATE scdul_pymn_sumr_detl
--    SET chart_acct_cd = '2001001', chart_acct_seq_no = '104'
--  WHERE biz_key = 'VN_VNM0004_000001'
--    AND scdul_pymn_no = '10'
--    AND chart_acct_cd IS NULL;
-- COMMIT;
-- -- revert: UPDATE scdul_pymn_sumr_detl
-- --            SET chart_acct_cd = NULL, chart_acct_seq_no = NULL
-- --          WHERE biz_key='VN_VNM0004_000001' AND scdul_pymn_no='10'
-- --            AND chart_acct_cd='2001001';

-- ============================================================================
-- ROLLBACK (Script 3 main seed) — removes the 3 seeded chart rows.
-- Run OPTIONAL A/B reverts first if you applied them.
-- ----------------------------------------------------------------------------
-- BEGIN;
-- DELETE FROM chart_acct_infm
--  WHERE biz_key = 'VN_VNM0004_000001'
--    AND chart_acct_seq_no IN ('74', '75', '104');
-- COMMIT;
-- ============================================================================
