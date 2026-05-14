-- ============================================================================
-- V123__daily_cash_report_view_setting_seed.sql
--
-- View Setting (Banking > Reports > Daily Cash) screen seed. Registers
-- the `daily-cash-report` screenId with the shared SYS_SCR_OPTION_WECMS
-- infra so the existing global View Setting endpoints
-- (GET / PUT / DELETE /api/v1/screen-options/{webSvcId}/{dymAreaId})
-- can serve the per-account grid.
--
-- Columns (per docs/refactor-wecms/features/daily-cash-report.md):
--   BANK            — DISP_ORD=1,  SORT_YN='N' (composite cell)
--   ACCT_NO         — DISP_ORD=2,  SORT_YN='N'
--   BALANCE         — DISP_ORD=3,  SORT_YN='N'
--   LOAN_NAME       — DISP_ORD=4,  SORT_YN='N'
--   MATURITY_DATE   — DISP_ORD=5,  SORT_YN='N'
--   SEND_COUNT      — DISP_ORD=6,  SORT_YN='N'
--   SEND_AMOUNT     — DISP_ORD=7,  SORT_YN='N'
--   RECEIVE_COUNT   — DISP_ORD=8,  SORT_YN='N'
--   RECEIVE_AMOUNT  — DISP_ORD=9,  SORT_YN='N'
--
-- SORT_YN='N' on every row — the report is single-day snapshot with a
-- fixed legacy ORDER BY (ccy_cd, acct_type_cd, bank_cd, acct_no). The
-- table-header sort UX is deliberately not exposed; user-level sort
-- would require a new whitelist enum and is deferred.
--
-- No CHECKBOX / ETC special rows — the grid has no row-select or action
-- column.
--
-- Idempotent: ON CONFLICT DO NOTHING.
-- ============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('daily-cash-report', 'default', 'BANK',           'Bank',            1, 'Y', 'Y', 'Y', 'N', '220px',
     'system', NOW(), 'system', NOW()),
    ('daily-cash-report', 'default', 'ACCT_NO',        'Account No',      2, 'Y', 'N', 'Y', 'N', '180px',
     'system', NOW(), 'system', NOW()),
    ('daily-cash-report', 'default', 'BALANCE',        'Balance',         3, 'Y', 'N', 'Y', 'N', '160px',
     'system', NOW(), 'system', NOW()),
    ('daily-cash-report', 'default', 'LOAN_NAME',      'Loan / Deposit',  4, 'Y', 'N', 'Y', 'N', '160px',
     'system', NOW(), 'system', NOW()),
    ('daily-cash-report', 'default', 'MATURITY_DATE',  'Maturity',        5, 'Y', 'N', 'Y', 'N', '120px',
     'system', NOW(), 'system', NOW()),
    ('daily-cash-report', 'default', 'SEND_COUNT',     'Send Count',      6, 'Y', 'N', 'Y', 'N', '110px',
     'system', NOW(), 'system', NOW()),
    ('daily-cash-report', 'default', 'SEND_AMOUNT',    'Send Amount',     7, 'Y', 'N', 'Y', 'N', '160px',
     'system', NOW(), 'system', NOW()),
    ('daily-cash-report', 'default', 'RECEIVE_COUNT',  'Receive Count',   8, 'Y', 'N', 'Y', 'N', '110px',
     'system', NOW(), 'system', NOW()),
    ('daily-cash-report', 'default', 'RECEIVE_AMOUNT', 'Receive Amount',  9, 'Y', 'N', 'Y', 'N', '160px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
