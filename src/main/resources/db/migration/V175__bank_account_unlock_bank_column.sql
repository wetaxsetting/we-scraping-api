-- =====================================================================
-- V151 — Settings > Bank Account: unlock the BANK column.
--
-- V127 originally seeded the BANK row (DISP_ORD=1, "Accounts") with
-- FIX_YN='Y', mirroring the Account Inquiry convention where the bank
-- name + account number cell is the anchor identifier and cannot be
-- hidden / reordered.
--
-- Product decision (2026-05-14) — surface the column as toggleable on
-- this screen so admins can prune the Account column when they only
-- need the per-row Nickname / Description for a tenant's working flow.
-- This is a Settings-only deviation from the Account Inquiry locked
-- baseline; the live JEX list at /settings → Bank Account never
-- enforced a locked column.
--
-- Effect:
--   1. SYS_SCR_OPTION_WECMS.FIX_YN flips Y → N for
--      (WEB_SVC_ID='bank-account', FIELD_ID='BANK').
--   2. USER_SCR_OPTION_WECMS rows for the same scope are deleted so
--      lazy-init (ScreenOptionService.load) re-copies the updated
--      sys default on next GET. Mirrors the V32 / V42 cutover pattern.
--
-- Idempotent — re-running the migration is a no-op once applied (the
-- UPDATE matches zero rows, the DELETE finds zero rows). Safe because:
--   - DISP_ORD / USE_YN / SORT_YN / SIZE values are untouched.
--   - Special FIELD_IDs (CHECKBOX, ETC) are unaffected.
--   - The backend ScreenOptionService.SPECIAL_FIELD_IDS lock continues
--     to pin CHECKBOX at USE_YN='Y' regardless of payload, so the
--     popover still hides + locks the row-selection special row.
-- =====================================================================

-- (1) Sys default: unlock the BANK row.
UPDATE SYS_SCR_OPTION_WECMS
   SET FIX_YN = 'N',
       MOD_ID = 'SYSTEM',
       MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'bank-account'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'BANK'
   AND FIX_YN = 'Y';

-- (2) Wipe per-user overrides for the screen so lazy-init re-copies
--     the new FIX_YN='N' default. Same pattern as V32 (semantic
--     screen-id cutover) and V42 (VAS_ACCOUNT visibility flip).
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'bank-account'
   AND DYM_AREA_ID = 'default';
