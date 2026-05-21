-- =============================================================================
-- V79 — Settings > VAS Account: View Setting seed
-- =============================================================================
-- Seeds SYS_SCR_OPTION_WECMS with the 5 columns for the VAS Account list page
-- under web_svc_id = 'vas-account', dym_area_id = 'default'.
--
-- Mirrors the legacy comm_0217_001_view.jsp 4-column grid (VAS Code · VAS
-- Account Name · Status · action icon) plus an opt-in Type column hidden by
-- default. CHECKBOX special FIELD_ID is intentionally NOT seeded — DN-8
-- (no bulk delete; legacy parity).
--
-- Column conventions per wecms-api/CLAUDE.md §"Screen Option (View
-- Setting)":
--   - FIELD_ID is uppercase and stable across releases
--   - FIX_YN='Y' rows always persist as USE_YN='Y' regardless of payload
--     (server-side enforcement in ScreenOptionService)
--   - ETC special FIELD_ID (action menu column) hides from popover but
--     rides through Save / Reset so column order survives
--   - SORT_YN='Y' columns MUST have a matching constant in
--     VasAccountSortField (verified at MR time)
--
-- See docs/refactor-wecms/features/vas-account.md §8 "View Setting".
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('vas-account', 'default', 'VAS_ACCT_CD',   'VAS Code',          1, 'Y', 'Y', 'Y', 'Y', '185px',
     'system', NOW(), 'system', NOW()),
    ('vas-account', 'default', 'VAS_ACCT_NM',   'VAS Account Name',  2, 'Y', 'N', 'Y', 'Y', '600px',
     'system', NOW(), 'system', NOW()),
    ('vas-account', 'default', 'VAS_ACCT_TYPE', 'Type',              3, 'N', 'N', 'Y', 'Y', '140px',
     'system', NOW(), 'system', NOW()),
    ('vas-account', 'default', 'STTS',          'Status',            4, 'Y', 'N', 'Y', 'Y', '120px',
     'system', NOW(), 'system', NOW()),
    ('vas-account', 'default', 'ETC',           '',                 99, 'Y', 'Y', 'N', 'N',  '45px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
