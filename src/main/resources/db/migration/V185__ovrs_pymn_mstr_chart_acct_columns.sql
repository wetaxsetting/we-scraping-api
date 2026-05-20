-- ============================================================================
-- V167__ovrs_pymn_mstr_chart_acct_columns.sql
--
-- Phase 1 follow-up for the Cash Flow report refactor.
--
-- V130 created OVRS_PYMN_MSTR with the column subset Sprint 2's overseas
-- payment feature needed. The Cash Flow aggregation needs two more
-- columns — CHART_ACCT_CD + CHART_ACCT_SEQ_NO — which the legacy
-- get_cashflow_report() function reads directly off the row to classify
-- overseas transactions into the chart-of-accounts hierarchy.
--
-- Both columns exist on DEV (wecmsvndev.ovrs_pymn_mstr) per the
-- Phase 0 verification. Additive only; nullable; no DEFAULT (matches DEV).
-- Idempotent via ADD COLUMN IF NOT EXISTS.
--
-- Reference: docs/refactor-wecms/features/cash-flow-report.md §3.1.
-- ============================================================================

ALTER TABLE OVRS_PYMN_MSTR
    ADD COLUMN IF NOT EXISTS CHART_ACCT_CD     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS CHART_ACCT_SEQ_NO VARCHAR(10);

-- Helper index for the Cash Flow aggregator's query path
-- (WHERE chart_acct_cd <> '' AND substring(reg_dtm,1,4) = ?).
CREATE INDEX IF NOT EXISTS ix_ovrs_pymn_mstr_biz_chart_regdtm
    ON OVRS_PYMN_MSTR (BIZ_KEY, CHART_ACCT_CD, REG_DTM)
    WHERE CHART_ACCT_CD IS NOT NULL AND CHART_ACCT_CD <> '';
