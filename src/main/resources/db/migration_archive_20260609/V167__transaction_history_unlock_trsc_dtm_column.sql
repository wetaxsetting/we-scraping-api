-- =====================================================================
-- V155 — Banking > Transaction History: realign View Setting seed with
-- legacy `fnsh_0002_001`.
--
-- V128 originally seeded the Transaction History screen with a 12-field
-- column set derived from the refactor's on-screen Figma table (CHECKBOX,
-- TRSC_DTM, RMRK, DEPOSIT_AMT, WITHDRAW_AMT, BAL, VAS_ACCT_CD,
-- RCV_PAY_USAG_CD, RCV_PAY_TRCO_NM, CONF_YN, CONFIRMED_BY_NM,
-- BANK_REF_NO) and locked TRSC_DTM with FIX_YN='Y'.
--
-- The legacy WeCMS production seed for the equivalent page (WSVC
-- `fnsh_0002_001` — `WECMS_DOC/database/ddl/sys_scr_option.sql:111-125`)
-- registers **9 fields, every one FIX_YN='N'**:
--
--    1 TRSC_DT  · 2 TRSC_TM  · 3 BANK_CD · 4 ACCT_NO ·
--    5 IN_AMT   · 6 OUT_AMT  · 7 BAL     · 8 RMRK    · 9 MEMO
--
-- Product decision (2026-05-16) — bring the refactor seed into parity
-- with legacy so admins see the same column set and the same lock
-- behaviour. The on-screen table keeps its current Figma layout for
-- now; column-to-table binding is a separate follow-up.
--
-- Effect:
--   1. Wipe every SYS_SCR_OPTION_WECMS row for
--      `web_svc_id='banking-transaction-history'` (the V128 column set).
--   2. Re-insert the legacy 9-field set with FIX_YN='N' on every row.
--   3. Wipe USER_SCR_OPTION_WECMS for the screen so lazy-init re-copies
--      the new defaults on next GET (matches V32 / V42 / V151 pattern).
--
-- Idempotent — re-running is a no-op:
--   - DELETE matches zero rows once the reseed is in place.
--   - INSERT … ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING is a no-op
--     because the V128 columns we removed do not collide with the
--     legacy column set, and the legacy rows already exist on re-run.
--
-- Note on backend export allow-list — the matching
-- `TransactionHistoryExportProvider.ALLOWED_FIELD_IDS` constant is
-- updated in the same MR to mirror the new FIELD_ID set.
-- =====================================================================

-- (1) Drop the V128 seed for this screen.
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-transaction-history';

-- (2) Reseed with the legacy 9-field set, all FIX_YN='N'.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID,                    DYM_AREA_ID, FIELD_ID,  FIELD_NM,        DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('banking-transaction-history', 'default',   'TRSC_DT',  'Date',          1, 'Y', 'N', 'Y', 'Y', '110px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'TRSC_TM',  'Time',          2, 'Y', 'N', 'Y', 'Y',  '90px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'BANK_CD',  'Bank',          3, 'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'ACCT_NO',  'Account No.',   4, 'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'IN_AMT',   'Deposit',       5, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'OUT_AMT',  'Withdraw',      6, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'BAL',      'Balance',       7, 'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'RMRK',     'Remarks',       8, 'Y', 'N', 'Y', 'N', '240px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'MEMO',     'Memo',          9, 'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- (3) Wipe per-user overrides so lazy-init re-copies the new defaults.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-transaction-history';
