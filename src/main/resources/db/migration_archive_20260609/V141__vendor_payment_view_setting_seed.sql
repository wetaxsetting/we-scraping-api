-- =============================================================================
-- V141: View Setting seed — Payment > Vendor Payment > Scheduled Payment.
--
-- Mirrors V131 §"Screen 1" (`payment-internal-transfer-scheduled`) — Vendor
-- and Internal Transfer share the same physical SCDUL_PYMN_SUMR table via
-- the SCDUL_PYMN_TYPE_CD discriminator ('01' Vendor vs. '03' Internal), and
-- the Vendor list page (Figma 24:1398) renders the same canonical column
-- set the shared `<PaymentScheduledBatchListPage>` paints.
--
-- WEB_SVC_ID `payment-vendor-payment-scheduled` is the refactor's
-- semantic kebab slug per the central registry at
-- `wecms-web/lib/viewSettingScreens.ts`. Legacy WSVC ids
-- (`pymn_0001_001`) are NOT used per workspace CLAUDE.md
-- §"Refactor identifier naming".
--
-- Special FIELD_IDs: CHECKBOX (DISP_ORD=0, FIX_YN='Y', non-sortable) +
-- ETC (DISP_ORD=99) follow the ScreenOptionService.SPECIAL_FIELD_IDS
-- contract — admin-controlled, never editable from the popover.
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING`.
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-vendor-payment-scheduled', 'default', 'CHECKBOX',         'Checkbox',              0,  'Y', 'Y', 'Y', 'N', '38px',  'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'REG_DTM',          'Date / Time',           1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'TITL',             'File Name',             2,  'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'TOT_TRNS_CNT',     'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'TOTAL_TRAN_AMT',   'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'APPR_STTS',        'Approval Status',       5,  'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'REG_ID',           'Requestor',             6,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'APPR_SEQ_NO',      'Approval Ref.',         7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'TRNF_YN',          'Transfer Status',       8,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'DOC_NO',           'Doc No.',               9,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled', 'default', 'ETC',              '',                      99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
