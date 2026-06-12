-- =============================================================================
-- V76 — remove the lamine@gmail.com → VN_VNM0004_000014 test link.
-- =============================================================================
-- V73 inserted a guarded multi-company link for 'lamine@gmail.com' so the
-- company-chooser UI could be exercised locally on DEV-imported DBs (shape
-- B in V73's header). Subsequent confirmation:
--
--   Legacy and current WeCMS operation do NOT officially support one login
--   user belonging to multiple companies as a normal production case. The
--   lamine cross-company link was demo/test data only, not a real legacy
--   requirement (see wecms-api/CLAUDE.md §"Project Overview" bullets
--   on multi-company infrastructure vs. production default).
--
-- This migration removes only that one test link so 'lamine@gmail.com'
-- returns to a single-company login (VN_VNM0004_000001 only — his real
-- DEV-imported admin link, untouched). The chooser branch of POST
-- /api/v1/auth/login will no longer fire for this user; login auto-picks
-- the single remaining link.
--
-- Scope (strictly bounded):
--   * Deletes exactly one row from USER_BUSI_CONN_INFM matching
--     (USER_ID = 'lamine@gmail.com', BIZ_KEY = 'VN_VNM0004_000014').
--   * Does NOT delete the USER_INFM('lamine@gmail.com') row.
--   * Does NOT delete or modify VN_VNM0004_000001 data (his real
--     admin link there is untouched).
--   * Does NOT delete the BUSI_INFM('VN_VNM0004_000014') row or any
--     other USER_BUSI_CONN_INFM row for that tenant.
--   * Does NOT touch BUSI_ADMN_INFM at all (lamine was never a 000014
--     admin, so there's nothing to remove there).
--
-- Idempotent: DELETE matching zero rows is not an error in PostgreSQL.
-- Safe to re-run after V73 has been re-applied from scratch (V73 inserts
-- the link, V76 removes it — net effect: no link).
-- =============================================================================

DELETE FROM USER_BUSI_CONN_INFM
 WHERE USER_ID = 'lamine@gmail.com'
   AND BIZ_KEY = 'VN_VNM0004_000014';
