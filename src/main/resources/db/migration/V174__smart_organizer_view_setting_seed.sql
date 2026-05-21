-- ============================================================================
-- V162__smart_organizer_view_setting_seed.sql
--
-- Banking > Smart Organizer: View Setting seed for the eligibility
-- list table. Registers `web_svc_id = 'smart-organizer'` /
-- `dym_area_id = 'default'` per the route-based slug convention in
-- CLAUDE.md §"Refactor identifier naming" + the central frontend
-- registry at `wecms-web/lib/viewSettingScreens.ts`.
--
-- Mirrors the legacy `fnsh_0200_001_view.jsp` 10-column table layout:
--
--   Bank Transaction Data section:
--     1. TRSC_DT          Date            120px (sortable)
--     2. ACCT_NO          Account No.     120px (sortable, right-aligned)
--     3. IN_AMT           Deposit         120px (sortable, right-aligned)
--     4. OUT_AMT          Withdrawal      160px (sortable, right-aligned)
--     5. RMRK             Remarks         250px (sortable)
--
--   Confirmation section (auto-populated from matched RMRK_ORG_INFM rule):
--     6. CHART_ACCT_CD    Reporting Acct  210px (sortable)
--     7. USE_USAG_CD      Purpose         200px (sortable)
--     8. VAS_ACCT_SEQ_NO  VAS Account     250px (not sortable)
--     9. TRCO_KEY         Contact         250px (not sortable)
--
-- A leading CHECKBOX special row (FIX_YN='Y', DISP_ORD=0) anchors the
-- row-selection column. No ETC special row — the action column lives
-- inline (Save / Cancel toolbar bar, not per-row).
--
-- SORT_YN='Y' columns: TRSC_DT, ACCT_NO, IN_AMT, OUT_AMT, RMRK,
-- CHART_ACCT_CD, USE_USAG_CD. The two ID-projected columns
-- (VAS_ACCT_SEQ_NO, TRCO_KEY) carry SORT_YN='N' because they project
-- through sub-tables and the SmartOrganizerSortField whitelist
-- enumerates only the direct ACCT_TRNS_HSTR / RMRK_ORG_INFM columns
-- the eligibility query can ORDER BY in one pass.
--
-- Idempotent via `INSERT … ON CONFLICT DO NOTHING`.
-- ============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE, HTML_CONT, REG_ID, REG_DTM)
VALUES
    ('smart-organizer', 'default', 'CHECKBOX',         'Select',            0, 'Y', 'Y', 'N', 'N', '38px',  NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'TRSC_DT',          'Date',              1, 'Y', 'N', 'Y', 'Y', '120px', NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'ACCT_NO',          'Account No.',       2, 'Y', 'N', 'Y', 'Y', '120px', NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'IN_AMT',           'Deposit',           3, 'Y', 'N', 'Y', 'Y', '120px', NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'OUT_AMT',          'Withdrawal',        4, 'Y', 'N', 'Y', 'Y', '160px', NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'RMRK',             'Remarks',           5, 'Y', 'N', 'Y', 'Y', '250px', NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'CHART_ACCT_CD',    'Reporting Account', 6, 'Y', 'N', 'Y', 'Y', '210px', NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'USE_USAG_CD',      'Purpose',           7, 'Y', 'N', 'Y', 'Y', '200px', NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'VAS_ACCT_SEQ_NO',  'VAS Account',       8, 'Y', 'N', 'Y', 'N', '250px', NULL, 'system', NOW()),
    ('smart-organizer', 'default', 'TRCO_KEY',         'Contact',           9, 'Y', 'N', 'Y', 'N', '250px', NULL, 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
