-- =============================================================================
-- V161: View Setting seed — Payment > Overseas Payment > Payment Result Detail.
--
-- ScreenId: payment-overseas-payment-result-detail
-- DymAreaId: default
-- Legacy reference: pymn_0007_001 detail view (OVRS_PYMN_SUMR_DETL rows)
--
-- Columns reflect the per-line beneficiary data returned by
-- OverseasPaymentService.getDetail() (OverseasPaymentDetailRowResponse):
--   BNFC_BANK_NM  — beneficiary bank name
--   BNFC_ACCT_NO  — beneficiary account number
--   BNFC_NM       — beneficiary name
--   CCY_CD        — currency code
--   TRNF_AMT      — transfer amount
--   MEMO          — memo / remark
--   TRNF_YN       — transfer result (Completed / In Progress)
--
-- All columns: SORT_YN='N' (no server-side sort whitelist on detail pages).
-- ETC special row included as action-column anchor (FIX_YN='Y', DISP_YN='N').
-- Idempotent via ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING.
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID,      FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_BANK_NM', 'Deposit Bank',      1,  'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_ACCT_NO', 'Account No.',        2,  'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_NM',      'Beneficiary Name',   3,  'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'CCY_CD',       'Currency',           4,  'Y', 'N', 'Y', 'N', '100px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'TRNF_AMT',     'Transfer Amount',    5,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'MEMO',         'Remark',             6,  'Y', 'N', 'Y', 'N', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'TRNF_YN',      'Results',            7,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'ETC',          '',                   99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
