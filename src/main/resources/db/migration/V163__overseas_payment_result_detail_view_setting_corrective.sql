-- =============================================================================
-- V163: Corrective View Setting seed — Payment > Overseas Payment > Payment
--       Result Detail.
--
-- V161 seeded generic column names (Deposit Bank, Account No., Currency,
-- Remark, Results) that do not match the overseas-specific Figma layout.
-- This migration replaces them with the correct overseas column set.
--
-- ScreenId: payment-overseas-payment-result-detail
-- DymAreaId: default
--
-- New column set (Figma 29:17560):
--   TRN_SEQ      — Transaction Seq
--   BNFC_NM      — Beneficiary Name
--   BNFC_BANK_NM — Beneficiary Bank Name
--   BNFC_ACCT_NO — Beneficiary Account No.
--   BNFC_ADDR    — Beneficiary Address
--   CCY_CD       — Transfer Currency
--   TRNF_AMT     — Transfer Amount
--   FEE_PAYER_CD — Fee Payer
--   MEMO         — Memo
--   RPRT_ACCT_CD — Reporting Account
--   TRNF_YN      — Status
--   ETC          — action column anchor (special, FIX_YN='Y', DISP_YN='N')
--
-- Strategy: delete old rows for this scope then insert the corrected set.
-- User overrides from the old set are also cleared (scope reset) so there
-- is no orphaned user data pointing to retired FIELD_IDs.
-- =============================================================================

-- 1. Clear old system rows for this screen
DELETE FROM SYS_SCR_OPTION_WECMS
WHERE WEB_SVC_ID = 'payment-overseas-payment-result-detail'
  AND DYM_AREA_ID = 'default';

-- 2. Clear any existing user overrides so they re-initialise from the new sys set
DELETE FROM USER_SCR_OPTION_WECMS
WHERE WEB_SVC_ID = 'payment-overseas-payment-result-detail'
  AND DYM_AREA_ID = 'default';

-- 3. Insert the corrected column set
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID,       FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-overseas-payment-result-detail', 'default', 'TRN_SEQ',      'Transaction Seq',         1,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_NM',      'Beneficiary Name',        2,  'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_BANK_NM', 'Beneficiary Bank Name',   3,  'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_ACCT_NO', 'Beneficiary Account No.', 4,  'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_ADDR',    'Beneficiary Address',     5,  'Y', 'N', 'Y', 'N', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'CCY_CD',       'Transfer Currency',       6,  'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'TRNF_AMT',     'Transfer Amount',         7,  'Y', 'N', 'Y', 'N', '138px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'FEE_PAYER_CD', 'Fee Payer',               8,  'Y', 'N', 'Y', 'N', '110px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'MEMO',         'Memo',                    9,  'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'RPRT_ACCT_CD', 'Reporting Account',       10, 'Y', 'N', 'Y', 'N', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'TRNF_YN',      'Status',                  11, 'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'ETC',          '',                        99, 'Y', 'Y', 'N', 'N',  '60px', 'system', NOW(), 'system', NOW());
