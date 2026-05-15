-- ============================================================================
-- V128__transaction_history_view_setting_seed.sql
--
-- View Setting (Banking > Transaction History) screen seed. Registers
-- the `banking-transaction-history` screenId in SYS_SCR_OPTION_WECMS so
-- the shared Excel export pipeline intersects request columns against
-- a real persisted visibility set.
--
-- Columns mirror legacy trns_0001_001 visible-by-default fields plus
-- the View-Setting-toggleable extras (VAS account, counterparty, usage,
-- confirmation metadata). The page itself is wired up incrementally —
-- the seed exists now so the export endpoint passes the registry +
-- visibility intersection without WBE2010.
--
-- One screen serves both VND and FCY transaction history (the legacy
-- WSVC is identical; CCY filtering is parameter-driven). Adding a
-- separate `banking-transaction-history-fcy` screen is unnecessary.
--
-- Idempotent: ON CONFLICT DO NOTHING.
-- ============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('banking-transaction-history', 'default', 'CHECKBOX',       'Select',           0,  'Y', 'Y', 'N', 'N', '38px',  'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'TRSC_DTM',       'Date / Time',      1,  'Y', 'Y', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'RMRK',           'Description',      2,  'Y', 'N', 'Y', 'N', '320px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'DEPOSIT_AMT',    'Deposit',          3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'WITHDRAW_AMT',   'Withdrawal',       4,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'BAL',            'Balance',          5,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'VAS_ACCT_CD',    'VAS Account',      6,  'N', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'RCV_PAY_USAG_CD','Purpose',          7,  'N', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'RCV_PAY_TRCO_NM','Counterparty',     8,  'N', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'CONF_YN',        'Confirmed',        9,  'Y', 'N', 'Y', 'N', '110px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'CONFIRMED_BY_NM','Confirmed By',    10,  'N', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default', 'BANK_REF_NO',    'Bank Ref. No.',   11,  'N', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
