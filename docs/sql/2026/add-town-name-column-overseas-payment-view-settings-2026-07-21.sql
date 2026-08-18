-- WeCMS Manual Database Change
-- Date: 2026-07-21
-- Source: migrated from Claude Code (AI)/sql/overseas_payment_detail_town_name_view_setting_20260721.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: Refactor DB 172.168.5.172:5432/wecmsdevdb
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — per-user override rows are deleted so the lazy-init copy re-seeds them
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =====================================================================
-- overseas_payment_detail_town_name_view_setting_20260721.sql
-- Feature : Overseas Payment > Scheduled Payment Detail + Payment Result
--           Detail — add the "Town Name" column (BNFC_TOWN_NM) to the
--           View Setting column set of both detail screens.
-- Target  : refactor DB 172.168.5.172:5432/wecmsdevdb (schema wecmsdevdb)
--
-- ⚠ EXECUTED BY A DBA BY HAND — NOT by the application, NOT by Flyway.
--   Flyway is disabled (ddl-auto=validate); View Setting seeds are the
--   source of truth for the live column set. The frontend FALLBACK_*
--   arrays only paint before the API resolves — without these rows the
--   Town Name column would NOT appear once the live VS loads.
--
-- What it does, per detail screen:
--   1. Shifts every column AFTER "Beneficiary Address" down by one
--      disp_ord (leaving the special ETC=99 row untouched).
--   2. Inserts BNFC_TOWN_NM immediately after Beneficiary Address
--      (matches legacy comm_0194_001 / pymn_0007_004 / the screenshots).
--   3. Deletes the per-user overrides for the screen so the lazy-init
--      copy re-seeds each user with the new column (V41/V42 precedent —
--      users who already saved a layout otherwise never get the new col).
--
-- Idempotent: each screen block is guarded by "town row not present yet",
-- so re-running is a no-op (the disp_ord shift never double-applies).
-- =====================================================================

-- Column set of SYS_SCR_OPTION_WECMS (10 cols, no audit columns):
--   web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size,
--   dym_area_id, sort_yn, html_cont

-- ---------------------------------------------------------------------
-- Screen 1 — Payment Result Detail  (BNFC_ADDR is disp_ord 5 → town = 6)
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM wecmsdevdb.sys_scr_option_wecms
        WHERE web_svc_id = 'payment-overseas-payment-result-detail'
          AND dym_area_id = 'default'
          AND field_id = 'BNFC_TOWN_NM'
    ) THEN
        -- 1. push TRNF_CCY_CD(6) … BANK_TRAN_ID(13) down by one; ETC(99) stays.
        UPDATE wecmsdevdb.sys_scr_option_wecms
           SET disp_ord = disp_ord + 1
         WHERE web_svc_id = 'payment-overseas-payment-result-detail'
           AND dym_area_id = 'default'
           AND disp_ord >= 6
           AND disp_ord <> 99;

        -- 2. insert Town Name right after Beneficiary Address.
        INSERT INTO wecmsdevdb.sys_scr_option_wecms
            (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont)
        VALUES
            ('payment-overseas-payment-result-detail', 'BNFC_TOWN_NM', 'Town Name', 6, 'Y', 'Y', '150px', 'default', 'Y', NULL);
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Screen 2 — Scheduled Payment Detail (BNFC_ADDR is disp_ord 4 → town = 5)
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM wecmsdevdb.sys_scr_option_wecms
        WHERE web_svc_id = 'payment-overseas-payment-scheduled-detail'
          AND dym_area_id = 'default'
          AND field_id = 'BNFC_TOWN_NM'
    ) THEN
        -- 1. push CHART_ACCT_CD(5) … BANK_TRAN_ID(10) down by one
        --    (this screen has no ETC row).
        UPDATE wecmsdevdb.sys_scr_option_wecms
           SET disp_ord = disp_ord + 1
         WHERE web_svc_id = 'payment-overseas-payment-scheduled-detail'
           AND dym_area_id = 'default'
           AND disp_ord >= 5
           AND disp_ord <> 99;

        -- 2. insert Town Name right after Beneficiary Address.
        INSERT INTO wecmsdevdb.sys_scr_option_wecms
            (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, size, dym_area_id, sort_yn, html_cont)
        VALUES
            ('payment-overseas-payment-scheduled-detail', 'BNFC_TOWN_NM', 'Town Name', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL);
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 3. Reset per-user overrides for both screens so the lazy-init copy
--    re-seeds each user with the new Town Name column. Safe in R&D
--    (mirrors V41/V42). Users re-customise from the refreshed default.
-- ---------------------------------------------------------------------
DELETE FROM wecmsdevdb.user_scr_option_wecms
 WHERE web_svc_id IN (
     'payment-overseas-payment-result-detail',
     'payment-overseas-payment-scheduled-detail'
 );

-- ---------------------------------------------------------------------
-- VERIFY (run after)
-- ---------------------------------------------------------------------
--   SELECT field_id, disp_ord FROM wecmsdevdb.sys_scr_option_wecms
--    WHERE web_svc_id = 'payment-overseas-payment-result-detail'
--    ORDER BY disp_ord;
--   -- expect: PAY_TRAN_SEQ 1, BNFC_NM 2, BNFC_BANK_NM 3, BNFC_ACCT_NO 4,
--   --         BNFC_ADDR 5, BNFC_TOWN_NM 6, TRNF_CCY_CD 7, TRNF_AMT 8,
--   --         FEE_PAYER 9, MEMO 10, CHART_ACCT_CD 11, PROC_STAT 12,
--   --         RSLT_DTTM 13, BANK_TRAN_ID 14, ETC 99
--
--   SELECT field_id, disp_ord FROM wecmsdevdb.sys_scr_option_wecms
--    WHERE web_svc_id = 'payment-overseas-payment-scheduled-detail'
--    ORDER BY disp_ord;
--   -- expect: BNFC_NM 1, BNFC_BANK_NM 2, BNFC_ACCT_NO 3, BNFC_ADDR 4,
--   --         BNFC_TOWN_NM 5, CHART_ACCT_CD 6, TRNF_CCY_CD 7, TRNF_AMT 8,
--   --         FEE_PAYER_CD 9, MEMO 10, BANK_TRAN_ID 11
-- =====================================================================
