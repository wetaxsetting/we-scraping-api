-- =============================================================================
-- V144: View Setting seeds — Payment > Payroll Payment (4 screens).
--
-- Mirrors V143 (Expense Payment seeds). Payroll, Vendor, Expense and
-- Internal Transfer share the physical SCDUL_PYMN_SUMR table via the
-- SCDUL_PYMN_TYPE_CD discriminator ('00' Payroll vs. '02' Expense vs.
-- '01' Vendor vs. '03' Internal); the list page renders the same
-- canonical column set.
--
-- Slugs per the central registry at
-- `wecms-web/lib/viewSettingScreens.ts`:
--   - payment-payroll-payment-scheduled          (legacy pymn_0005_001)
--   - payment-payroll-payment-scheduled-detail   (legacy pymn_0005_003)
--   - payment-payroll-payment-result             (legacy pymn_0006_001)
--   - payment-payroll-payment-result-detail      (legacy pymn_0006_002)
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING`.
-- =============================================================================

-- Screen 1: Payroll Scheduled Payment list
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-payroll-payment-scheduled', 'default', 'CHECKBOX',         'Checkbox',              0,  'Y', 'Y', 'Y', 'N', '38px',  'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'REG_DTM',          'Date / Time',           1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'TITL',             'File Name',             2,  'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'TOT_TRNS_CNT',     'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'TOTAL_TRAN_AMT',   'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'APPR_STTS',        'Approval Status',       5,  'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'REG_ID',           'Requestor',             6,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'APPR_SEQ_NO',      'Approval Ref.',         7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'TRNF_YN',          'Transfer Status',       8,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'DOC_NO',           'Doc No.',               9,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'ETC',              '',                      99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 2: Payroll Scheduled Payment Detail (per-line table)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-payroll-payment-scheduled-detail', 'default', 'TAR_BANK_CD',  'Deposit Bank',     1,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'RCPT_NM',      'Recipient Name',   2,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'FINC_MDA_NO',  'Account No.',      3,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'TRAN_AMT',     'Transfer Amount',  4,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'TRAN_USAG',    'Remark',           5,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'CHART_ACCT_CD','Reporting Acct',   6,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'ETC',          '',                 99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 3: Payroll Payment Result list
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-payroll-payment-result', 'default', 'REG_DTM',          'Date / Time',           1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result', 'default', 'TITL',             'File Name',             2,  'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result', 'default', 'TOT_TRNS_CNT',     'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result', 'default', 'TOTAL_TRAN_AMT',   'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result', 'default', 'TRNF_YN',          'Transfer Status',       5,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result', 'default', 'APPR_SEQ_NO',      'Approval Ref.',         6,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result', 'default', 'DOC_NO',           'Doc No.',               7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result', 'default', 'ETC',              '',                      99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 4: Payroll Payment Result Detail
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-payroll-payment-result-detail', 'default', 'SCDUL_PYMN_DETL_NO', 'Line No.',           1,  'Y', 'N', 'Y', 'Y', '100px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'PROC_DTTM',          'Result Date / Time', 2,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'TAR_BANK_CD',        'Deposit Bank',       3,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'RCPT_NM',            'Recipient Name',     4,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'TRAN_AMT',           'Transfer Amount',    5,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'TRAN_USAG',          'Remarks',            6,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'PROC_STAT',          'Results',            7,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'CHART_ACCT_CD',      'Reporting Acct',     8,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'BANK_TRNS_SHORT_ID', 'Banking Txn ID',     9,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'ETC',                '',                   99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
