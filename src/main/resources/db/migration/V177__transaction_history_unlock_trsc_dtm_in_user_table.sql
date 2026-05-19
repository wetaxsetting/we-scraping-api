-- =====================================================================
-- V177 — Banking > Transaction History: defensively unlock TRSC_DTM
-- (Date/Time) for environments that still carry the legacy V128 seed.
--
-- Originally landed as V163 on 2026-05-18 but collided with
-- V163__overseas_payment_result_detail_view_setting_corrective.sql,
-- which prevented Flyway from booting once it was re-enabled. Renumbered
-- to V177 on 2026-05-19 to deduplicate. The migration body is still
-- defensive + idempotent, so running it after V164–V176 is safe.
--
-- V157 + V159 reseeded `web_svc_id='banking-transaction-history'` with
-- FIX_YN='N' on every row including TRSC_DTM, and wiped
-- USER_SCR_OPTION_WECMS so lazy-init would re-copy the unlocked sys
-- defaults on next GET. However, some local DBs (and any deployed
-- environment that took the V128 seed before V155 landed) still show
-- the Date/Time checkbox disabled in the View Setting popover because
-- their effective state — whether from a stale USER row or a sys row
-- that V157 somehow missed — still carries FIX_YN='Y' on TRSC_DTM.
--
-- This migration is purely defensive and idempotent:
--
--   1. UPDATE SYS_SCR_OPTION_WECMS  ➜ TRSC_DTM.FIX_YN='N' (no-op when
--      already 'N', as V157 set it).
--   2. UPDATE USER_SCR_OPTION_WECMS ➜ TRSC_DTM.FIX_YN='N' for every
--      user override row in every tenant — catches stale rows that
--      were copied from the V128 seed and survived because the user
--      never re-init'd post-V157 (e.g. saved overrides predate V157).
--   3. Also flip USE_YN='Y' on TRSC_DTM so the column is visible by
--      default — a stale override could have hidden it too.
--
-- No row deletes — preserves any per-user reorder / visibility on the
-- OTHER columns. The fix is scoped to TRSC_DTM specifically.
--
-- Idempotent — re-running matches zero rows after the first apply
-- (UPDATE … WHERE FIX_YN='Y' AND FIELD_ID='TRSC_DTM' is empty once
-- corrected).
-- =====================================================================

-- (1) Force-unlock TRSC_DTM in the system defaults.
UPDATE SYS_SCR_OPTION_WECMS
   SET FIX_YN = 'N',
       USE_YN = 'Y',
       MOD_ID = 'system',
       MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'banking-transaction-history'
   AND FIELD_ID   = 'TRSC_DTM'
   AND (FIX_YN <> 'N' OR USE_YN <> 'Y');

-- (2) Force-unlock TRSC_DTM in every existing per-user override row.
UPDATE USER_SCR_OPTION_WECMS
   SET FIX_YN = 'N',
       USE_YN = 'Y',
       MOD_ID = 'system',
       MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'banking-transaction-history'
   AND FIELD_ID   = 'TRSC_DTM'
   AND (FIX_YN <> 'N' OR USE_YN <> 'Y');
