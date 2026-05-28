-- =====================================================================
-- V209 — Settings > Bank Account: widen DESCRIPTION to match
--        ACCOUNT_NICKNAME (180px).
--
-- V208 narrowed DESCRIPTION 200px → 140px, which read as too cramped.
-- Product direction (2026-05-28): the Description column should be the
-- same width as Account Nickname (180px) — a balanced middle ground
-- that still reclaims width from the original 200px while giving short
-- descriptions and the "Description" header comfortable room.
--
-- Shipped as a new migration (not an edit of V208) because V208 may
-- already be applied to a developer's local DB — editing an applied
-- migration trips Flyway's checksum validation. The guard makes this
-- idempotent and order-independent: it lands 180px regardless of
-- whether the current value is 140px (V208 applied) or 200px (V208 not
-- yet applied — it runs first, then this no-ops the 140px guard... so
-- guard on "anything but the target").
--
-- Effect:
--   SYS_SCR_OPTION_WECMS.SIZE → '180px' for
--   (WEB_SVC_ID='bank-account', DYM_AREA_ID='default', FIELD_ID='DESCRIPTION').
--
-- No USER_SCR_OPTION_WECMS touch needed: ScreenOptionService.buildMergedResponse
-- always sources SIZE from the system row (never the per-user override).
--
-- Frontend counterpart (same MR): the FALLBACK_COLUMNS first-paint seed
-- in BankAccountPanel.tsx and the --w-wb-bank-acct-table-min /
-- --w-wb-bank-acct-col-description tokens in app/globals.css are bumped
-- to match.
--
-- Idempotent.
-- =====================================================================

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE    = '180px',
       MOD_ID  = 'SYSTEM',
       MOD_DTM = NOW()
 WHERE WEB_SVC_ID  = 'bank-account'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID    = 'DESCRIPTION'
   AND SIZE       <> '180px';
