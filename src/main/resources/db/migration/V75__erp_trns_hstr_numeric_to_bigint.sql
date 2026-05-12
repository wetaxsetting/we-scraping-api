-- =============================================================================
-- V75 — ERP_TRNS_HSTR: NUMERIC(10,0) → BIGINT for SEQ_NO and FILE_SIZE.
-- =============================================================================
-- Hotfix for V71. The V71 schema mirrored DEV column-for-column where
-- SEQ_NO and FILE_SIZE are NUMERIC(10,0). The `ErpTrnsHstr` JPA entity
-- maps both as Java `Long` and the V71 entity Javadoc claims that
-- `precision = 10, scale = 0` on a `Long` would make Hibernate bind the
-- column as NUMERIC — but on Hibernate 6.6.45 / PostgreSQL dialect the
-- schema validator still expects BIGINT for a `Long` field. Startup
-- fails with:
--
--   Schema-validation: wrong column type encountered in column [seq_no]
--   in table [erp_trns_hstr]; found [numeric (Types#NUMERIC)],
--   but expecting [bigint (Types#BIGINT)]
--
-- Same trade-off precedent: V35 → V36 reverted `disp_ord` from
-- NUMERIC(10,0) to INTEGER for the same Hibernate-validator reason
-- (refactor-only deviation from DEV documented inline; DEV stays
-- NUMERIC).
--
-- This migration applies the equivalent fix for ERP_TRNS_HSTR:
--   SEQ_NO    NUMERIC(10,0)  →  BIGINT  (refactor-local deviation from DEV)
--   FILE_SIZE NUMERIC(10,0)  →  BIGINT  (refactor-local deviation from DEV)
--
-- Data safety: every NUMERIC(10,0) value fits losslessly in BIGINT
-- (max NUMERIC(10,0) value ≈ 10^10, well below BIGINT's 9.2×10^18 max).
-- PostgreSQL's implicit NUMERIC → BIGINT cast handles every existing
-- row (V72's 30 demo rows are small monotonic seq_no values + non-negative
-- file_size bytes — all cast losslessly). PK rebuild not required;
-- PostgreSQL rewrites the underlying index entries in place when
-- the column type changes for an integer PK component.
--
-- Idempotent: ALTER COLUMN ... TYPE on a column that's already BIGINT
-- is a no-op in PostgreSQL.
-- =============================================================================

ALTER TABLE ERP_TRNS_HSTR
    ALTER COLUMN SEQ_NO TYPE BIGINT USING SEQ_NO::bigint;

ALTER TABLE ERP_TRNS_HSTR
    ALTER COLUMN FILE_SIZE TYPE BIGINT USING FILE_SIZE::bigint;
