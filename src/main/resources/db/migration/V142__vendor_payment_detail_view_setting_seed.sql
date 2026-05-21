-- =============================================================================
-- V142: View Setting seed — Payment > Vendor Payment > Scheduled Payment Detail.
--
-- Mirrors V131 §"Screen 3" (`payment-internal-transfer-scheduled-detail`) —
-- the per-line transaction table inside the Vendor detail page renders
-- the same canonical 10-column layout. Legacy mirror: `pymn_0001_003`
-- view JSP `<table-list-wrap[name='SCHEDULED_PAYMENT_DETAIL']>`.
--
-- WEB_SVC_ID `payment-vendor-payment-scheduled-detail` is the refactor's
-- semantic kebab slug per the central registry at
-- `wecms-web/lib/viewSettingScreens.ts`. Legacy WSVC ids
-- (`pymn_0001_003_r001`) are NOT used per workspace CLAUDE.md
-- §"Refactor identifier naming".
--
-- The `ETC` special FIELD_ID (DISP_ORD=99) is included as the row's
-- trailing action slot — kept admin-controlled via the
-- `ScreenOptionService.SPECIAL_FIELD_IDS` contract.
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING`.
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-vendor-payment-scheduled-detail', 'default', 'TAR_BANK_CD',  'Deposit Bank',     1,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'RCPT_NM',      'Recipient Name',   2,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'FINC_MDA_NO',  'Account No.',      3,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'TRAN_AMT',     'Transfer Amount',  4,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'NOTE',         'Remark',           5,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'CHART_ACCT_CD','Reporting Acct',   6,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'TRAN_USAG',    'Description',      7,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'DOC_NO',       'Doc No.',          8,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'ETC',          '',                 99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
