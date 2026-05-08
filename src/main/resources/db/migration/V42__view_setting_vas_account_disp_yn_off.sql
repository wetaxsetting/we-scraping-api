-- ============================================================================
-- V42: View Setting — set DISP_YN='N' for VAS_ACCOUNT on banking-account-inquiry-vnd (2026-05-02)
-- ============================================================================
--
-- Background
-- ----------
-- DISP_YN controls whether a field appears in the View Setting popup:
--   DISP_YN='Y'  → field is shown in the popup (user can toggle/reorder it)
--   DISP_YN='N'  → field is hidden from the popup (user cannot configure it)
--
-- This is confirmed by IDO.USER_SCR_OPTION_WECMS_R002.xml in the legacy JEX
-- system, which queries  `WHERE ... AND USR.DISP_YN = 'Y'`  so rows with
-- DISP_YN='N' are excluded from the popup results. The refactor frontend
-- mirrors this in ViewSettingPopover.tsx (added in the same MR as V42).
--
-- VAS_ACCOUNT is already hidden from the table by default (USE_YN='N', seeded
-- by V34). Because it has no backing field yet (BUSI_VAS_ACCT_INFM is not
-- ported in Sprint 1), exposing it in the View Setting popup would let users
-- "un-hide" a column that always renders "—". Setting DISP_YN='N' prevents
-- VAS_ACCOUNT from appearing in the popup until the column has real data.
--
-- When BUSI_VAS_ACCT_INFM is ported in a later sprint, the migration that
-- wires real data for VAS_ACCOUNT should also flip DISP_YN back to 'Y' here
-- so the column becomes user-configurable.
--
-- Idempotent
-- ----------
-- Plain UPDATE — re-running is a no-op once the row is at DISP_YN='N'.
-- Trailing DELETE on USER_SCR_OPTION_WECMS clears any user override rows so
-- the View Setting lazy-init step re-copies the corrected sys defaults on the
-- next page load (mirrors the V41 / V34 pattern).

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_YN = 'N',
       MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd'
   AND FIELD_ID   = 'VAS_ACCOUNT';

-- Wipe USER_SCR_OPTION_WECMS for the scope so per-user overrides re-init
-- from sys on the next load. This propagates the corrected DISP_YN flag to
-- any user who already had a saved View Setting for this screen.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd';
