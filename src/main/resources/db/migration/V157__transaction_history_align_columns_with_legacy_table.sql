-- =====================================================================
-- V157 — Banking > Transaction History: realign View Setting seed with
-- the legacy on-screen table column set.
--
-- V155 reseeded the screen with legacy `fnsh_0002_001`'s 9-field
-- popover set (TRSC_DT/TRSC_TM/BANK_CD/ACCT_NO/IN_AMT/OUT_AMT/BAL/RMRK
-- /MEMO). That matched the legacy popover, but the legacy on-screen
-- table renders a DIFFERENT 10-column set (matching-panel oriented):
--
--    1 TRSC_ID      · 2 TRSC_DTM     · 3 DEPOSIT_AMT  ·
--    4 WITHDRAW_AMT · 5 BAL          · 6 RMRK         ·
--    7 FILE_NM      · 8 MTCH_DT      · 9 MTCH_BY      · 10 PURPOSE
--
-- Product decision (2026-05-16) — align View Setting + on-screen table
-- to a single source of truth keyed to the rendered table so the
-- popover's toggle/reorder maps 1:1 to what the user sees. Every
-- column FIX_YN='N' (toggleable), matching the legacy convention.
--
-- Effect:
--   1. Wipe every SYS_SCR_OPTION_WECMS row for
--      `web_svc_id='banking-transaction-history'` (the V155 seed).
--   2. Re-insert the 10-field on-screen-table set with FIX_YN='N'.
--   3. Wipe USER_SCR_OPTION_WECMS for the screen so lazy-init re-copies
--      the new defaults on next GET (matches V32 / V42 / V151 / V155).
--
-- Backend export allow-list — `TransactionHistoryExportProvider`
-- ALLOWED_FIELD_IDS is updated in the same MR to mirror this set.
--
-- Idempotent — re-running is a no-op (DELETE finds zero rows;
-- INSERT … ON CONFLICT DO NOTHING).
-- =====================================================================

-- (1) Drop the prior seed for this screen.
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-transaction-history';

-- (2) Reseed with the on-screen-table 10-field set, all FIX_YN='N'.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID,                    DYM_AREA_ID, FIELD_ID,        FIELD_NM,         DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('banking-transaction-history', 'default',   'TRSC_ID',        'Transaction ID',  1, 'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'TRSC_DTM',       'Date/Time',       2, 'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'DEPOSIT_AMT',    'Deposit',         3, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'WITHDRAW_AMT',   'Withdrawal',      4, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'BAL',            'Balance',         5, 'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'RMRK',           'Remarks',         6, 'Y', 'N', 'Y', 'N', '240px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'FILE_NM',        'File Name',       7, 'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'MTCH_DT',        'Matched Date',    8, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'MTCH_BY',        'Matched By',      9, 'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'PURPOSE',        'Purpose',        10, 'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- (3) Wipe per-user overrides so lazy-init re-copies the new defaults.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-transaction-history';
