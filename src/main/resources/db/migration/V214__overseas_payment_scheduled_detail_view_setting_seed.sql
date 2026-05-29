-- =============================================================================
-- V214: View Setting seed — Payment > Overseas Payment > Scheduled Payment Detail.
--
-- Mirrors V142 §Vendor Scheduled Detail. The per-line transaction table
-- inside the Overseas Scheduled detail page renders the canonical
-- 10-column layout from legacy `pymn_0007_004` view JSP
-- `<table-list-wrap[name='SCHEDULED_PAYMENT_DETAIL']>` (see
-- WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/pymn/pymn_0007_004.ts:417-489
-- for the switch-case that drives the dynamic table).
--
-- WEB_SVC_ID `payment-overseas-payment-scheduled-detail` is the
-- refactor's semantic kebab slug per the central registry at
-- `wecms-web/lib/viewSettingScreens.ts`. Legacy WSVC ids
-- (`pymn_0007_004`) are NOT used per workspace CLAUDE.md
-- §"Refactor identifier naming".
--
-- Column set mirrors legacy field codes verbatim so the popover labels
-- match what a JEX-trained user already knows:
--   BNFC_NM         — Beneficiary Name
--   BNFC_BANK_NM    — Beneficiary Bank Name (composite cell: name + BIC)
--   BNFC_ACCT_NO    — Beneficiary Account No.
--   BNFC_ADDR       — Beneficiary Address
--   TRNF_CCY_CD     — Transfer Currency
--   TRNF_AMT        — Transfer Amount
--   FEE_PAYER_CD    — Fee Payer (B/O/S → Beneficiary charged / Ordering Charge / Shared)
--   MEMO            — Memo
--   CHART_ACCT_CD   — Reporting Account (editable picker, yellow cell)
--   BANK_TRAN_ID    — Banking Transaction ID
--
-- All columns are USER editable (FIX_YN='N') — same precedent as the
-- Vendor / Expense / Payroll detail seeds. No CHECKBOX special row
-- (the Overseas detail page has no row-selection column) and no ETC
-- action column.
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING`.
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-overseas-payment-scheduled-detail', 'default', 'BNFC_NM',       'Beneficiary Name',        1,  'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'BNFC_BANK_NM',  'Beneficiary Bank Name',   2,  'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'BNFC_ACCT_NO',  'Beneficiary Account No.', 3,  'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'BNFC_ADDR',     'Beneficiary Address',     4,  'Y', 'N', 'Y', 'N', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'TRNF_CCY_CD',   'Transfer Currency',       5,  'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'TRNF_AMT',      'Transfer Amount',         6,  'Y', 'N', 'Y', 'N', '138px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'FEE_PAYER_CD',  'Fee Payer',               7,  'Y', 'N', 'Y', 'N', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'MEMO',          'Memo',                    8,  'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'CHART_ACCT_CD', 'Reporting Account',       9,  'Y', 'N', 'Y', 'N', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'BANK_TRAN_ID',  'Banking Transaction ID',  10, 'Y', 'N', 'Y', 'N', '175px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
