-- =============================================================================
-- V150: View Setting seeds — Payment > Vendor Payment Result (2 screens).
--
-- The Vendor Result + Result Detail screenIds were added to the frontend
-- registry (`wecms-web-main/lib/viewSettingScreens.ts`) when the Vendor
-- Result page was wired, but the matching backend seeds in
-- SYS_SCR_OPTION_WECMS were not landed in V141 (which seeded only
-- payment-vendor-payment-scheduled + scheduled-detail). Result: hitting
-- the Vendor Result page surfaces WBE2001 "Screen option not configured
-- for this page (payment-vendor-payment-result/default)" from
-- ScreenOptionService.load.
--
-- This migration adds the two missing seeds. Schema mirrors V143
-- (Expense Result + Result Detail) byte-for-byte — Vendor and Expense
-- share the same canonical column set on both screens.
--
-- Slugs per the central registry at
-- `wecms-web-main/lib/viewSettingScreens.ts`:
--   - payment-vendor-payment-result          (legacy pymn_0002_001)
--   - payment-vendor-payment-result-detail   (legacy pymn_0002_002)
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING` — same
-- conflict target the V141–V144 seeds use.
-- =============================================================================

-- Screen 1: Vendor Payment Result list (mirror V143 Screen 3)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-vendor-payment-result', 'default', 'REG_DTM',        'Date / Time',           1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result', 'default', 'TITL',           'File Name',             2,  'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result', 'default', 'TOT_TRNS_CNT',   'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result', 'default', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result', 'default', 'TRNF_YN',        'Transfer Status',       5,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result', 'default', 'APPR_SEQ_NO',    'Approval Ref.',         6,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result', 'default', 'DOC_NO',         'Doc No.',               7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result', 'default', 'ETC',            '',                      99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 2: Vendor Payment Result Detail (mirror V143 Screen 4)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-vendor-payment-result-detail', 'default', 'SCDUL_PYMN_DETL_NO', 'Line No.',           1,  'Y', 'N', 'Y', 'Y', '100px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'PROC_DTTM',          'Result Date / Time', 2,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'TAR_BANK_CD',        'Deposit Bank',       3,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'RCPT_NM',            'Recipient Name',     4,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'TRAN_AMT',           'Transfer Amount',    5,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'NOTE',               'Remarks',            6,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'PROC_STAT',          'Results',            7,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'CHART_ACCT_CD',      'Reporting Acct',     8,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'TRAN_USAG',          'Description',        9,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'DOC_NO',             'Document No.',       10, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'BANK_TRNS_SHORT_ID', 'Banking Txn ID',     11, 'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-result-detail', 'default', 'ETC',                '',                   99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
