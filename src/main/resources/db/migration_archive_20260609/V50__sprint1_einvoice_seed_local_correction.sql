-- ============================================================================
-- V50__sprint1_einvoice_seed_local_correction.sql
--
-- Local-only corrective migration for the Sales / Expenses e-Invoices seed
-- in BUSA_DOC_MSTR. Targets only rows imported by V22 for the configured
-- single tenant (BIZ_KEY = 'VN_VNM0004_000001').
--
-- Why this migration exists
-- -------------------------
-- 1. V22 (`V22__sprint1_real_dev_seed.sql`) inserted 25 sales (DOC_TYPE='I')
--    and 25 expense (DOC_TYPE='E') rows into BUSA_DOC_MSTR. The HAND_REG_YN
--    column did NOT exist on the table at V22 time — V23 adds it later — so
--    the inserted rows landed with HAND_REG_YN = NULL.
--
-- 2. V23 (`V23__sprint1_legacy_schema_parity.sql`) then ran:
--        ALTER TABLE BUSA_DOC_MSTR ADD COLUMN IF NOT EXISTS HAND_REG_YN VARCHAR(1);
--        UPDATE BUSA_DOC_MSTR SET HAND_REG_YN = 'Y' WHERE HAND_REG_YN IS NULL;
--        ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN HAND_REG_YN SET DEFAULT 'Y';
--        ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN HAND_REG_YN SET NOT NULL;
--    so every V22-imported row was backfilled to HAND_REG_YN = 'Y'.
--
-- 3. The Sales / Expenses e-Invoices listing applies the IR-2 baseline
--    filter `m.handRegYn <> 'Y'` in
--    `BusaDocMstrRepository.findFiltered(...)` (and the matching
--    `aggregateByCurrency`). Result: 100% of the V22 seed is excluded from
--    the listing and totals — the page renders empty regardless of the
--    user's date range, status, or keyword.
--
-- Additionally, V22 used DOC_TYPE = 'E' for the expense rows, but
-- IR-1 maps the wire `EXPENSE` request value to `DOC_TYPE = 'B'`
-- (`BusaDocType.BILL`). Without correction, Expenses e-Invoices would
-- still return zero rows even after fixing HAND_REG_YN.
--
-- What this migration does
-- ------------------------
--   1. Flips HAND_REG_YN from 'Y' to 'N' on V22-seeded rows so they pass
--      IR-2's `HAND_REG_YN <> 'Y'` filter. Scoped narrowly to:
--         - BIZ_KEY = 'VN_VNM0004_000001'  (tenant)
--         - DOC_ID matches a V22-seed prefix ('1C25TWV-%' for sales,
--                                              'R-%' for expense),
--      so it cannot accidentally touch hand-registered rows that the
--      team adds later in dev. IR-2 is intentionally kept; only the seed
--      data is corrected.
--   2. Migrates DOC_TYPE 'E' → 'B' on V22-seeded expense rows so IR-1's
--      EXPENSE → 'B' mapping resolves cleanly. Scoped to BIZ_KEY +
--      DOC_TYPE='E'; no other code path inserts DOC_TYPE='E' for this
--      tenant locally.
--
-- What this migration deliberately does NOT do
-- --------------------------------------------
--   - Does NOT edit V22 or V23 (Flyway rule: never modify applied
--     migrations).
--   - Does NOT relax the IR-2 `HAND_REG_YN <> 'Y'` filter — that filter
--     is correct legacy parity; the local seed was wrong.
--   - Does NOT widen the tenant filter — BIZ_KEY scope mirrors V22.
--   - Does NOT touch ISSU_DT — local QA selects a date range that
--     covers seed dates (Sales: 2025-10-15…2025-10-16; Expenses:
--     2025-05-22…2025-08-06). Real DEV-imported issue dates stay as-is.
--
-- Idempotent: re-running affects 0 rows after the first apply.
-- ============================================================================

-- 1. Flip HAND_REG_YN so V22 seed passes IR-2's `HAND_REG_YN <> 'Y'` filter.
UPDATE BUSA_DOC_MSTR
   SET HAND_REG_YN = 'N'
 WHERE BIZ_KEY = 'VN_VNM0004_000001'
   AND HAND_REG_YN = 'Y'
   AND (DOC_ID LIKE '1C25TWV-%' OR DOC_ID LIKE 'R-%');

-- 2. Migrate seed expense rows from DOC_TYPE='E' to DOC_TYPE='B' so IR-1's
--    EXPENSE → 'B' mapping returns them.
UPDATE BUSA_DOC_MSTR
   SET DOC_TYPE = 'B'
 WHERE BIZ_KEY = 'VN_VNM0004_000001'
   AND DOC_TYPE = 'E';
