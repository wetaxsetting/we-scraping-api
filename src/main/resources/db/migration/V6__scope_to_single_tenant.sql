-- =======================================================================
-- V6__scope_to_single_tenant.sql
--
-- R&D scope reduction (directive 2026-04-22):
--   * Local DB is treated as a single-tenant environment.
--   * Active tenant = 'VN_VNM0004_000001' (exact, no wildcards).
--   * Rows belonging to any other BIZ_KEY are dropped from the local DB.
--
-- This migration is explicit, versioned, and runs exactly once. It is NOT
-- the Spring Boot application auto-resetting data — the rule "no automatic
-- delete/reset" still holds. This is a deliberate, named scope change.
--
-- Order respects the FKs declared in V1:
--   USER_BUSI_CONN_INFM → USER_INFM (FK_UBCI_USER)
--   USER_BUSI_CONN_INFM → BUSI_INFM (FK_UBCI_BUSI)
-- So links must go before the referenced users/tenants.
-- =======================================================================

-- 1. Drop user↔tenant links to any BIZ_KEY other than the scope.
DELETE FROM USER_BUSI_CONN_INFM
WHERE BIZ_KEY <> 'VN_VNM0004_000001';

-- 2. Drop users that are now orphaned (no remaining link).
--    Safe because USER_LOGIN_HIS has no FK to USER_INFM in this schema.
DELETE FROM USER_INFM
WHERE USER_ID NOT IN (SELECT USER_ID FROM USER_BUSI_CONN_INFM);

-- 3. Drop all tenants except the scope.
DELETE FROM BUSI_INFM
WHERE BIZ_KEY <> 'VN_VNM0004_000001';

-- 4. Tidy orphan audit rows left behind by step 2.
DELETE FROM USER_LOGIN_HIS
WHERE USER_ID NOT IN (SELECT USER_ID FROM USER_INFM);
