-- ============================================================================
-- V74__backfill_busi_infm_prdt_type.sql
--
-- Corrects an oversight in V2__auth_seed_dev.sql: the original seed for
-- BIZ_KEY = 'VN_VNM0004_000001' omitted PRDT_TYPE from the INSERT, so
-- the row was created with PRDT_TYPE = NULL locally. DEV stores 'F'
-- (WeCMS default) for the same BIZ_KEY (confirmed via DEV
-- information_schema 2026-05-11).
--
-- After this migration the two local R&D tenants align with the
-- expected refactor product-type set:
--
--   VN_VNM0004_000001  →  PRDT_TYPE = 'F'   (WeCMS default)
--   VN_VNM0004_000014  →  PRDT_TYPE = 'H'   (HAENGSUNG Electronics, seeded by V73)
--
-- Scope: F / M / H are the three product types the refactor's PrdtType
-- enum recognises. Other legacy letters (W, R, B, E, U, L, S, P, C, D,
-- T) are carried verbatim on the wire and JWT but not in the enum yet
-- — see wecms-api-main/CLAUDE.md §Product-type rule. M has no local
-- tenant today; tests cover M via mocked BusiInfm fixtures.
--
-- Idempotent: the WHERE clause excludes rows that already carry a
-- non-NULL PRDT_TYPE, so re-running the migration after a manual edit
-- never overwrites a deliberate choice.
-- ============================================================================

UPDATE BUSI_INFM
   SET PRDT_TYPE = 'F',
       MOD_ID    = 'SYSTEM',
       MOD_DTM   = CURRENT_TIMESTAMP
 WHERE BIZ_KEY   = 'VN_VNM0004_000001'
   AND PRDT_TYPE IS NULL;
