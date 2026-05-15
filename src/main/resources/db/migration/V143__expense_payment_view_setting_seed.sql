-- =============================================================================
-- V143: View Setting seeds — Payment > Expense Payment (4 screens).
--
-- Mirrors V141 (Vendor scheduled) + V142 (Vendor scheduled detail) +
-- the Internal Transfer result + result-detail seeds (V131). Expense
-- and Vendor share the physical SCDUL_PYMN_SUMR table via the
-- SCDUL_PYMN_TYPE_CD discriminator ('02' Expense vs. '01' Vendor); the
-- list page renders the same canonical column set.
--
-- Slugs per the central registry at
-- `wecms-web-main/lib/viewSettingScreens.ts`:
--   - payment-expense-payment-scheduled          (legacy pymn_0003_001)
--   - payment-expense-payment-scheduled-detail   (legacy pymn_0003_003)
--   - payment-expense-payment-result             (legacy pymn_0004_001)
--   - payment-expense-payment-result-detail      (legacy pymn_0004_002)
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING`.
-- =============================================================================

-- Screen 1: Expense Scheduled Payment list (mirror Vendor V141 schema)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-expense-payment-scheduled', 'default', 'CHECKBOX',         'Checkbox',              0,  'Y', 'Y', 'Y', 'N', '38px',  'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'REG_DTM',          'Date / Time',           1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'TITL',             'File Name',             2,  'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'TOT_TRNS_CNT',     'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'TOTAL_TRAN_AMT',   'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'APPR_STTS',        'Approval Status',       5,  'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'REG_ID',           'Requestor',             6,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'APPR_SEQ_NO',      'Approval Ref.',         7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'TRNF_YN',          'Transfer Status',       8,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'DOC_NO',           'Doc No.',               9,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'ETC',              '',                      99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 2: Expense Scheduled Payment Detail (per-line table) — mirror V142
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-expense-payment-scheduled-detail', 'default', 'TAR_BANK_CD',  'Deposit Bank',     1,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled-detail', 'default', 'RCPT_NM',      'Recipient Name',   2,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled-detail', 'default', 'FINC_MDA_NO',  'Account No.',      3,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled-detail', 'default', 'TRAN_AMT',     'Transfer Amount',  4,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled-detail', 'default', 'NOTE',         'Remark',           5,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled-detail', 'default', 'CHART_ACCT_CD','Reporting Acct',   6,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled-detail', 'default', 'TRAN_USAG',    'Description',      7,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled-detail', 'default', 'DOC_NO',       'Doc No.',          8,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled-detail', 'default', 'ETC',          '',                 99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 3: Expense Payment Result list (mirror Internal Transfer Result)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-expense-payment-result', 'default', 'REG_DTM',          'Date / Time',           1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'TITL',             'File Name',             2,  'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'TOT_TRNS_CNT',     'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'TOTAL_TRAN_AMT',   'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'TRNF_YN',          'Transfer Status',       5,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'APPR_SEQ_NO',      'Approval Ref.',         6,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'DOC_NO',           'Doc No.',               7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'ETC',              '',                      99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 4: Expense Payment Result Detail (mirror Internal Transfer Result Detail)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-expense-payment-result-detail', 'default', 'SCDUL_PYMN_DETL_NO', 'Line No.',           1,  'Y', 'N', 'Y', 'Y', '100px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'PROC_DTTM',          'Result Date / Time', 2,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'TAR_BANK_CD',        'Deposit Bank',       3,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'RCPT_NM',            'Recipient Name',     4,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'TRAN_AMT',           'Transfer Amount',    5,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'NOTE',               'Remarks',            6,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'PROC_STAT',          'Results',            7,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'CHART_ACCT_CD',      'Reporting Acct',     8,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'TRAN_USAG',          'Description',        9,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'DOC_NO',             'Document No.',       10, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'BANK_TRNS_SHORT_ID', 'Banking Txn ID',     11, 'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'ETC',                '',                   99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
