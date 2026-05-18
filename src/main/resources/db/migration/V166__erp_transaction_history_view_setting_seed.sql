-- =============================================================================
-- V154 — Settings > ERP Transaction History: View Setting seed (export gate)
-- =============================================================================
-- Seeds SYS_SCR_OPTION_WECMS with the 7 columns for the ERP Transaction
-- History list page under web_svc_id = 'erp-transaction-history',
-- dym_area_id = 'default'.
--
-- The popover is intentionally NOT rendered on this screen (legacy panel
-- has no toolbar list-icon button — OQ-FE-2 resolved 2026-05-11). This
-- seed exists to unlock the shared Excel-export pipeline:
-- `ExcelExportService` intersects the requested column list against
-- `ScreenOptionService.load(screenId, dymAreaId)` and would reject every
-- column with `WBE2013` if the screen had no row in `SYS_SCR_OPTION_WECMS`.
--
-- Every row is `FIX_YN='Y'` so the (non-existent) popover could not
-- relax the visibility, and `SORT_YN='Y'` only where there's a matching
-- constant in `ErpTrnsHistorySortField` — REG_DTM, ERP_TYPE,
-- TRNS_TYPE_CD, REG_USER. ORG_FILE_NM / FILE_SIZE / REG_ID are not in
-- the sort whitelist today, so `SORT_YN='N'` per the §"Common table
-- sort" rule.
--
-- No CHECKBOX / ETC special rows — the panel has neither a row-selection
-- column nor an action-menu column (the trailing download glyph is part
-- of the FILE/ORG_FILE_NM cell, not a separate ETC column).
--
-- See wecms-api-main/CLAUDE.md §"Screen Option (View Setting)" and the
-- §"Excel export" defence-in-depth rule.
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('erp-transaction-history', 'default', 'REG_DTM',      'Date / Time',         1, 'Y', 'Y', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('erp-transaction-history', 'default', 'ERP_TYPE',     'Direction',           2, 'Y', 'Y', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('erp-transaction-history', 'default', 'TRNS_TYPE_CD', 'Transaction Type',    3, 'Y', 'Y', 'Y', 'Y', '220px',
     'system', NOW(), 'system', NOW()),
    ('erp-transaction-history', 'default', 'ORG_FILE_NM',  'File Name',           4, 'Y', 'Y', 'Y', 'N', '320px',
     'system', NOW(), 'system', NOW()),
    ('erp-transaction-history', 'default', 'FILE_SIZE',    'File Size (bytes)',   5, 'Y', 'Y', 'Y', 'N', '120px',
     'system', NOW(), 'system', NOW()),
    ('erp-transaction-history', 'default', 'REG_USER',     'Registered By',       6, 'Y', 'Y', 'Y', 'Y', '180px',
     'system', NOW(), 'system', NOW()),
    ('erp-transaction-history', 'default', 'REG_ID',       'User ID',             7, 'Y', 'Y', 'Y', 'N', '140px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
