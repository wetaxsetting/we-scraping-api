-- ============================================================================
-- V124__cash_flow_report_view_setting_seed.sql
--
-- View Setting (Banking > Reports > Cash Flow) screen seed. Registers
-- the `cash-flow-report` screenId in SYS_SCR_OPTION_WECMS so the shared
-- Excel export pipeline (ExcelExportService → ScreenOptionService.load)
-- intersects request columns against a real persisted visibility set.
--
-- The report page itself does NOT expose the List icon / column toggle
-- popover today (it's a fixed-shape pivot matrix per Figma); the seed
-- exists purely so the export's defence-in-depth visibility filter
-- passes. All non-special columns are USE_YN='Y'.
--
-- Columns mirror the legacy cash_0001_001_r001 WSVC OUT plus monthly
-- amount columns:
--   DISP_CD       — display code (e.g. "1.1.1")
--   DISP_NM       — display name (e.g. "Sales receipts")
--   ITEM_CD       — leaf item code
--   ITEM_NM       — leaf item name
--   ROW_TYPE      — legacy classifier (Level 1 / Subtotal / etc.)
--   JAN_AMT..DEC_AMT — twelve monthly amount columns
--   TOTAL_AMT     — annual total
--   DISP_CCY_CD   — display currency for the row
--
-- SORT_YN='N' on every row — the legacy report uses a fixed ORDER BY
-- (DISP_ORD inside the function); a user-level sort would require a
-- whitelist enum and is deferred.
--
-- Idempotent: ON CONFLICT DO NOTHING.
-- ============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('cash-flow-report', 'default', 'DISP_CD',     'Code',            1, 'Y', 'Y', 'Y', 'N', '120px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'DISP_NM',     'Display Name',    2, 'Y', 'Y', 'Y', 'N', '240px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'ITEM_CD',     'Item Code',       3, 'Y', 'N', 'Y', 'N', '120px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'ITEM_NM',     'Item Name',       4, 'Y', 'N', 'Y', 'N', '200px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'ROW_TYPE',    'Row Type',        5, 'Y', 'N', 'Y', 'N', '140px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'JAN_AMT',     'Jan',             6, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'FEB_AMT',     'Feb',             7, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'MAR_AMT',     'Mar',             8, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'APR_AMT',     'Apr',             9, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'MAY_AMT',     'May',            10, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'JUN_AMT',     'Jun',            11, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'JUL_AMT',     'Jul',            12, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'AUG_AMT',     'Aug',            13, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'SEP_AMT',     'Sep',            14, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'OCT_AMT',     'Oct',            15, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'NOV_AMT',     'Nov',            16, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'DEC_AMT',     'Dec',            17, 'Y', 'N', 'Y', 'N', '110px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'TOTAL_AMT',   'Total',          18, 'Y', 'N', 'Y', 'N', '140px',  'system', NOW(), 'system', NOW()),
    ('cash-flow-report', 'default', 'DISP_CCY_CD', 'Currency',       19, 'Y', 'N', 'Y', 'N', '100px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
