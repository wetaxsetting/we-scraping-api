-- ============================================================================
-- V181__transfer_history_helper_index.sql
--
-- Helper index for Banking > Internal Transfer > Transfer History.
--
-- The Transfer History list endpoint
--   GET /api/v1/banking/internal-transfer/transfer-history
-- filters by BIZ_KEY + substring(REG_DTM, 1, 8) BETWEEN ? AND ?
-- and orders by REG_DTM DESC, SEQ_NO DESC by default. The PK index
-- on (BIZ_KEY, SEQ_NO) does not cover the date-range scan, so on
-- multi-tenant PROD volumes the planner falls back to a sort over a
-- BIZ_KEY-filtered scan.
--
-- DEV table has only the PK index (verified 2026-05-20 via
-- pg_indexes against wecmsvndev.napas_remit_trns_hstr). The
-- "Legacy Schema Parity Rule" in wecms-api/CLAUDE.md explicitly
-- allows helper indexes beyond DEV — extra indexes don't break
-- column-for-column parity.
--
-- Index columns mirror the access pattern exactly:
--   (biz_key ASC, substr(reg_dtm,1,8) DESC, seq_no DESC).
-- Postgres uses the leading prefix for the BIZ_KEY equality, the
-- second key as the range filter, and the third for the
-- deterministic tie-break that the sort enum appends.
--
-- Idempotent: CREATE INDEX IF NOT EXISTS.
-- ============================================================================

CREATE INDEX IF NOT EXISTS ix_napas_remit_trns_hstr_biz_regdtm
    ON NAPAS_REMIT_TRNS_HSTR (
        BIZ_KEY,
        SUBSTR(REG_DTM, 1, 8) DESC,
        SEQ_NO DESC
    );

COMMENT ON INDEX ix_napas_remit_trns_hstr_biz_regdtm IS
    'Transfer History list endpoint: (BIZ_KEY, date-portion of REG_DTM DESC, SEQ_NO DESC). Refactor addition — DEV table has only the PK.';
