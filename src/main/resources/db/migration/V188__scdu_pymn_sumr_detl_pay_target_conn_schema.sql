-- =============================================================================
-- V188 — Payment Dispatch bridge: SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN
-- =============================================================================
-- Bridge row linking a payment-module detail line
-- (SCDUL_PYMN_SUMR_DETL.(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)) to its
-- API-path dispatch row (TB_PAY_TARGET via (TRAN_DT, TRAN_SEQ)).
--
-- Schema copied column-for-column from wecmsvndev DEV DB (verified via the
-- read-only postgres_dev MCP, 2026-05-19). PK matches DEV. No UK / CHECK / FK
-- constraints on DEV — refactor mirrors.
--
-- Spelling note: the DB identifier is SCDU_PYMN_..., NOT SCDUL_PYMN_... —
-- preserved verbatim from DEV. The PK columns themselves keep the SCDUL_ prefix
-- (matching the source SCDUL_PYMN_SUMR_DETL columns).
--
-- Idempotent — CREATE TABLE IF NOT EXISTS so re-running on a DB that already
-- has the table (the wecmsvndev source) is a no-op.
-- =============================================================================

CREATE TABLE IF NOT EXISTS SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN (
    BIZ_KEY              VARCHAR(20)  NOT NULL,
    SCDUL_PYMN_NO        VARCHAR(10)  NOT NULL,
    SCDUL_PYMN_DETL_NO   VARCHAR(20)  NOT NULL,
    TRAN_DT              VARCHAR(8),
    TRAN_SEQ             VARCHAR(10),
    CONSTRAINT pk_scdu_pymn_sumr_detl_pay_target_conn
        PRIMARY KEY (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)
);
