-- =====================================================================
-- V159 — Banking > Transaction History: add the two missing on-screen
-- columns that V157 omitted (Contact Name + Confirmed by).
--
-- V157 reseeded the screen with 10 fields aligned to the legacy
-- on-screen table (TRSC_ID · TRSC_DTM · DEPOSIT_AMT · WITHDRAW_AMT ·
-- BAL · RMRK · FILE_NM · MTCH_DT · MTCH_BY · PURPOSE). The legacy
-- table actually carries 12 data columns plus a trailing action
-- column — the missing pair is:
--
--    11 CONTACT_NM   · 12 CONFIRMED_BY
--
-- These two map to the ACCT_TRNS_RCV_PAY_INFM matching-panel fields
-- (`RCV_PAY_TRCO_NM` → Contact Name, `CNFM_BY_USER_NM` → Confirmed
-- by). Add them as `USE_YN='Y'` / `FIX_YN='N'` so they appear by
-- default and are toggleable like the other columns.
--
-- Effect:
--   1. INSERT the two missing rows into SYS_SCR_OPTION_WECMS
--      (idempotent via ON CONFLICT).
--   2. Wipe USER_SCR_OPTION_WECMS for the screen so lazy-init
--      re-copies the extended defaults on next GET (matches V32 /
--      V42 / V151 / V155 / V157 pattern).
--
-- Backend export allow-list — `TransactionHistoryExportProvider`
-- ALLOWED_FIELD_IDS is updated in the same MR to include the new
-- FIELD_IDs.
--
-- Idempotent — re-running is a no-op (INSERT … ON CONFLICT DO
-- NOTHING; DELETE finds zero rows once the user-side has been
-- re-init'd).
-- =====================================================================

-- (1) Append the two missing columns to the existing 10-field set.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID,                    DYM_AREA_ID, FIELD_ID,      FIELD_NM,        DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('banking-transaction-history', 'default',   'CONTACT_NM',   'Contact Name',  11, 'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('banking-transaction-history', 'default',   'CONFIRMED_BY', 'Confirmed by',  12, 'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- (2) Wipe per-user overrides so lazy-init re-copies the extended set.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-transaction-history';
