-- =============================================================================
-- V203: View Setting seed — Payment > Corporate Card > Card Transactions.
--
-- WEB_SVC_ID `card-transactions` is the refactor's semantic kebab slug
-- per the central registry at `wecms-web-main/lib/viewSettingScreens.ts`
-- (legacy WSVC id `card_0001_001` is NOT used per workspace CLAUDE.md
-- §"Refactor identifier naming").
--
-- Required for:
--   1. Export endpoint — `ExcelExportService` intersects requested
--      columns with persisted `USE_YN='Y'` rows from this table;
--      without the seed every column is dropped and the XLSX comes
--      back empty.
--   2. View Setting popover — `<ViewSettingPopover>` lazy-INITs from
--      this seed on first user open.
--
-- Special FIELD_IDs (CLAUDE.md §Special FIELD_IDs):
--   - CHECKBOX (`DISP_ORD=0`, `FIX_YN='Y'`, `SORT_YN='N'`) — selection col
--   - MEMO_ICON (`DISP_ORD=98`, `FIX_YN='Y'`, `SORT_YN='N'`) — row action
--
-- Sortable columns mirror the backend `CardTrnsHstrSortField` whitelist:
-- TRSC_DT / CARD_NO / MERCH_NM / TRSC_AMT. Other columns ship
-- `SORT_YN='N'` so the table header doesn't paint a glyph the backend
-- can't honor.
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING`.
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('card-transactions', 'default', 'CHECKBOX',           'Checkbox',          0,  'Y', 'Y', 'Y', 'N', '38px',  'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'TRSC_DT',            'Date / Time',       1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_NO',            'Card Number',       2,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'MERCH_NM',           'Merchant',          3,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_NICK_NM',       'Card Nickname',     4,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_HLDR_NM',       'Cardholder',        5,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CCY_CD',             'Currency',          6,  'Y', 'N', 'Y', 'N', '90px',  'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'TRSC_AMT',           'Amount',            7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CHART_ACCT_CD',      'Reporting Account', 8,  'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'STTS',               'Status',            9,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_TRNS_USAGE_NM', 'Purpose',          10, 'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'LINK_REF',           'Link Reference',   11, 'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CONT',               'Description',      12, 'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'APPR_SEQ_NO',        'Approval Ref.',    13, 'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'MEMO_ICON',          'Memo',             98, 'Y', 'Y', 'Y', 'N', '48px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
