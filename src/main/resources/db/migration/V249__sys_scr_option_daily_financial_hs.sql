------------------------------------------------------------------
-- Manual DBA Script
-- Flyway disabled/removed.
-- Execute manually in DEV -> UAT -> PROD.
------------------------------------------------------------------
-- File:    V249__sys_scr_option_daily_financial_hs.sql
-- Table:   public.sys_scr_option_wecms  (live JEX, 10-column schema)
-- Purpose: Seed the refactor View Setting rows for Banking > Reports >
--          Daily Financial (HS) so its <ViewSettingPopover> loads column
--          visibility / order / width from the DB (source of truth) instead
--          of the frontend static registry (which stays as first-paint /
--          unseeded fallback only).
--
-- SCOPE: View Setting popover page. Daily Financial (HS) is the one report
--        page that renders the popover (Figma 291:7180 shows the list-icon);
--        Daily Cash / Daily Financial CMC / Cash Flow do NOT and are excluded
--        from the seed (see V156 header).
--
-- VERSION/LOCATION: the V249 prefix is for file organization / change tracking
--        ONLY. Flyway is removed/disabled (ddl-auto=validate in every profile);
--        nothing here auto-runs. Executed BY HAND by the DBA, DEV -> UAT -> PROD.
--
-- SOURCE -> TARGET MAPPING:
--   Legacy source:   sys_scr_option_wecms  web_svc_id='rept_0017_001',
--                    dym_area_id='table1'  (12 columns).
--   Refactor target: web_svc_id='banking-reports-daily-financial-hs',
--                    dym_area_id='default'.
--   The legacy underscore id (`rept_0017_001`) is intentionally NOT reused —
--   the refactor screenId convention is `<module>-<page>[-<type>]` kebab-case
--   (lib/viewSettingScreens.ts) and the backend screen-option `@Pattern`
--   rejects underscores. The legacy area `table1` maps to `default`: the
--   refactor renders one consolidated grid and the frontend calls
--   useViewSetting(SCREEN_IDS.BANKING_REPORTS_DAILY_FINANCIAL_HS, DEFAULT_AREA_ID)
--   — components/banking/reports/daily-financial-hs/DailyFinancialHsPage.tsx.
--
--   FIELD_ID remap (legacy -> refactor registry FIELD_ID; the frontend
--   COLUMN_REGISTRY keys on these, so the seed MUST use the refactor ids):
--     BANK_ACCT    -> BANK             (Bank Name,             125px)
--     CCY_CD       -> CURRENCY         (Currency,               78px)
--     RECEIPT_CODE -> RECEIPT_CODE     (Receipt Code,          170px)
--     PAYMENT_CODE -> PAYMENT_CODE     (Payment Code,          170px)
--     DESC_EN      -> DESCRIPTION_EN   (Description (English), 200px)
--     DESC_KO      -> DESCRIPTION_KO   (Description (Korean),  200px)
--     RCPT_NM      -> COUNTERPARTY     (Customer/Supplier,     200px)
--     OPEN_BAL     -> BEFORE_BALANCE   (Before Balance,        135px)
--     RCPT         -> RECEIPT          (Receipt,               135px)
--     PYMN         -> PAYMENT          (Payment,               135px)
--     CLOSE_BAL    -> BALANCE          (Balance,               135px)
--     MEMO         -> MEMO_NOTE        (Memo,                  285px)
--   NOTE: the memo column uses FIELD_ID `MEMO_NOTE`, NOT `MEMO` — `MEMO` is a
--   reserved special FIELD_ID (VIEW_SETTING_SPECIAL_FIELD_IDS) that the shared
--   popover hides; the requirement is that Memo stays user-controllable.
--
-- RULES APPLIED:
--   * 10 live-JEX columns only: web_svc_id, field_id, field_nm, disp_ord,
--     disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont.
--   * sort_yn='N' for every column — the report has no column sorting (the
--     HS page does not wire the sort triplet; matches legacy).
--   * Legacy JEX rows (web_svc_id='rept_0017_001') are never touched.
--   * Idempotent: ON CONFLICT (web_svc_id, field_id) DO NOTHING.
--   * USER_SCR_OPTION_WECMS is NOT seeded (lazy-init populates it per user
--     on first GET).
------------------------------------------------------------------

INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('banking-reports-daily-financial-hs', 'BANK',           'Bank Name',             1,  'Y', 'Y', '125px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'CURRENCY',       'Currency',              2,  'Y', 'Y', '78px',  'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'RECEIPT_CODE',   'Receipt Code',          3,  'Y', 'Y', '170px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'PAYMENT_CODE',   'Payment Code',          4,  'Y', 'Y', '170px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'DESCRIPTION_EN', 'Description (English)', 5,  'Y', 'Y', '200px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'DESCRIPTION_KO', 'Description (Korean)',  6,  'Y', 'Y', '200px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'COUNTERPARTY',   'Customer/Supplier',     7,  'Y', 'Y', '200px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'BEFORE_BALANCE', 'Before Balance',        8,  'Y', 'Y', '135px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'RECEIPT',        'Receipt',               9,  'Y', 'Y', '135px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'PAYMENT',        'Payment',               10, 'Y', 'Y', '135px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'BALANCE',        'Balance',               11, 'Y', 'Y', '135px', 'default', 'N', NULL),
    ('banking-reports-daily-financial-hs', 'MEMO_NOTE',      'Memo',                  12, 'Y', 'Y', '285px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;
