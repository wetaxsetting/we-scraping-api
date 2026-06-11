-- ============================================================================
-- V41: View Setting — turn off SORT_YN for VAS_ACCOUNT (2026-05-02)
-- ============================================================================
--
-- Background
-- ----------
-- The common table-sort feature (CLAUDE.md §13a / wecms-api "Common
-- table sort" section) gates the frontend's clickable-header glyph on
-- SYS_SCR_OPTION_WECMS.SORT_YN. The earlier V34 seed declared every column
-- on `banking-account-inquiry-vnd` with SORT_YN='Y' as a default, including
-- VAS_ACCOUNT.
--
-- VAS_ACCOUNT, however, is currently a UI placeholder ("—") with no
-- backing field on the JPA entity (BUSI_VAS_ACCT_INFM is not yet ported in
-- Sprint 1) and is intentionally NOT registered in the backend sort
-- whitelist enum `AccountSortField`. The mismatch produces the following
-- UX gap when a user un-hides VAS_ACCOUNT via View Setting (it is
-- USE_YN='N' by default, so most users never trigger this):
--
--   * frontend renders a clickable sort glyph (because SORT_YN='Y');
--   * clicking the glyph cycles the cycle UI as expected;
--   * the API request includes ?sortField=VAS_ACCOUNT&sortDirection=ASC;
--   * AccountSortField.parse("VAS_ACCOUNT") returns null;
--   * service falls back to DEFAULT_SORT — the rows do not actually move.
--
-- The discoverable rule going forward (codified in CLAUDE.md):
--
--   * if a frontend column has SORT_YN='Y' it MUST have a backend
--     whitelist entry;
--   * if no backend mapping exists, SORT_YN MUST be 'N'.
--
-- This migration enforces the rule for VAS_ACCOUNT. When the column gets
-- a real backing field in a later sprint, flip it back to 'Y' in the
-- same migration that adds the AccountSortField enum constant + entity
-- property + mapper change.
--
-- Idempotent
-- ----------
-- Plain UPDATE — re-running is a no-op once the row is at SORT_YN='N'.
-- Trailing DELETE on USER_SCR_OPTION_WECMS clears any user override
-- rows for the screen so View Setting's lazy-INIT step re-copies the
-- corrected sys defaults on the next page load (mirrors the V34 pattern).

UPDATE SYS_SCR_OPTION_WECMS
   SET SORT_YN = 'N'
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd'
   AND FIELD_ID   = 'VAS_ACCOUNT';

-- Wipe USER_SCR_OPTION_WECMS for the scope so per-user overrides re-init
-- from sys on the next load. The user table currently holds no SORT_YN
-- column (refactor-only mirror of DEV — see V34 / V35) so this DELETE
-- is what propagates the corrected sys flag to existing logged-in users.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd';
