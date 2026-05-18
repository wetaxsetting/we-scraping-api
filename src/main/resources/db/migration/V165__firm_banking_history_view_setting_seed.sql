-- =============================================================================
-- V153 — Settings > Firm Banking History: View Setting seed (export gate)
-- =============================================================================
-- Seeds SYS_SCR_OPTION_WECMS with the 7 columns for the Firm Banking History
-- list page under web_svc_id = 'firm-banking-history', dym_area_id = 'default'.
--
-- The popover is intentionally NOT rendered on this screen (legacy panel has
-- no toolbar list-icon button — same precedent as ERP Transaction History /
-- Approval Line / Chart of Accounts). This seed exists to unlock the shared
-- Excel-export pipeline: `ExcelExportService` intersects the requested column
-- list against `ScreenOptionService.load(screenId, dymAreaId)` and would
-- reject every column with `WBE2013` ("no exportable columns") if the
-- screen had no row in `SYS_SCR_OPTION_WECMS`.
--
-- Every row is `FIX_YN='Y'` so the (non-existent) popover could not relax
-- the visibility, and `SORT_YN='Y'` only where there's a matching constant
-- in `FirmBankingHistorySortField` — REG_DTM (TARN_DTM), SVC_TYPE,
-- SVC_NM, SEQ_NO. RS_CD / RS_MSG / DEP_BANK_CD are not in the sort
-- whitelist today, so `SORT_YN='N'` per the §"Common table sort" rule
-- ("Seed `SORT_YN` and the whitelist enum must agree").
--
-- No CHECKBOX / ETC special rows — the panel has neither a row-selection
-- column nor an action-menu column.
--
-- See wecms-api-main/CLAUDE.md §"Screen Option (View Setting)" and the
-- §"Excel export" defence-in-depth rule.
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('firm-banking-history', 'default', 'REG_DTM',     'Date / Time',     1, 'Y', 'Y', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('firm-banking-history', 'default', 'SVC_TYPE',    'Service Type',    2, 'Y', 'Y', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('firm-banking-history', 'default', 'SVC_NM',      'Service Name',    3, 'Y', 'Y', 'Y', 'Y', '220px',
     'system', NOW(), 'system', NOW()),
    ('firm-banking-history', 'default', 'SEQ_NO',      'Seq. No.',        4, 'Y', 'Y', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('firm-banking-history', 'default', 'DEP_BANK_CD', 'Deposit Bank',    5, 'Y', 'Y', 'Y', 'N', '200px',
     'system', NOW(), 'system', NOW()),
    ('firm-banking-history', 'default', 'RS_CD',       'Result Code',     6, 'Y', 'Y', 'Y', 'N', '140px',
     'system', NOW(), 'system', NOW()),
    ('firm-banking-history', 'default', 'RS_MSG',      'Result Message',  7, 'Y', 'Y', 'Y', 'N', '320px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
