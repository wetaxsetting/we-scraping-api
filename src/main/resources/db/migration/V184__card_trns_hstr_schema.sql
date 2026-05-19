-- ============================================================================
-- V166__card_trns_hstr_schema.sql
--
-- Phase 1 of the Cash Flow report refactor. CARD_TRNS_HSTR is one of the
-- four source tables consumed by the legacy get_cashflow_report() function;
-- the refactor's Java aggregation needs it locally to compute card-side
-- spend per (chart_acct_cd, year_month) for tenant rows whose
-- chart_acct_cd is populated (DEV: 21 of 156 rows for VN_VNM0004_000001).
--
-- Column-for-column parity with wecmsvndev.card_trns_hstr verified
-- 2026-05-18 via information_schema.columns. 26 columns, PK on
-- (BIZ_KEY, CARD_SEQ_NO). The audit columns are VARCHAR(14) holding
-- yyyyMMddHHmmss strings — matches legacy parity, mirrors V13
-- ACCT_TRNS_HSTR. Two CHAR columns (CCY_CD CHAR(3), INOUT_DV CHAR(1))
-- and one CHAR(1) flag (ERP_SND_YN) with DB-side defaults from DEV.
--
-- Idempotent via CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS.
--
-- Reference: docs/refactor-wecms/features/cash-flow-report.md §3.1 + §7.1.
-- ============================================================================

CREATE TABLE IF NOT EXISTS CARD_TRNS_HSTR (
    BIZ_KEY                 VARCHAR(20)   NOT NULL,
    BANK_CD                 VARCHAR(20)   NOT NULL,
    CARD_NO                 VARCHAR(100)  NOT NULL,
    TRSC_DT                 VARCHAR(8)    NOT NULL,
    TRSC_TM                 VARCHAR(6),
    CARD_SEQ_NO             VARCHAR(20)   NOT NULL,
    CARD_NM                 VARCHAR(50),
    MERCH_NM                VARCHAR(50),
    CCY_CD                  CHAR(3)       NOT NULL,
    INOUT_DV                CHAR(1)       NOT NULL DEFAULT '2',
    TRSC_AMT                NUMERIC(20,6),
    USING_TYPE              VARCHAR(50),
    PRCH_STTS               VARCHAR(50),
    NOTE                    VARCHAR(255),
    CONT                    VARCHAR(200),
    APPR_SEQ_NO             VARCHAR(15),
    CARD_TRNS_USAGE_CODE    VARCHAR(20),
    AUTH_CODE               VARCHAR(15),
    CITY                    VARCHAR(100),
    REG_ID                  VARCHAR(100)  NOT NULL,
    REG_DTM                 VARCHAR(14)   NOT NULL,
    MOD_ID                  VARCHAR(100)  NOT NULL,
    MOD_DTM                 VARCHAR(14)   NOT NULL,
    CHART_ACCT_SEQ_NO       VARCHAR(10),
    CHART_ACCT_CD           VARCHAR(20),
    ERP_SND_YN              CHAR(1)       NOT NULL DEFAULT 'N',
    CONSTRAINT pk_card_trns_hstr PRIMARY KEY (BIZ_KEY, CARD_SEQ_NO)
);

-- ----------------------------------------------------------------------------
-- Helper indexes beyond DEV (DEV has only the PK).
-- Cash Flow aggregator walks by (biz_key, chart_acct_cd, trsc_dt) — the
-- index supports the WHERE chart_acct_cd <> '' AND year(trsc_dt) = ?
-- predicate on the inner SELECT of all_payment_data.
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_card_trns_hstr_biz_chart_dt
    ON CARD_TRNS_HSTR (BIZ_KEY, CHART_ACCT_CD, TRSC_DT)
    WHERE CHART_ACCT_CD IS NOT NULL AND CHART_ACCT_CD <> '';

CREATE INDEX IF NOT EXISTS ix_card_trns_hstr_biz_reg_dtm
    ON CARD_TRNS_HSTR (BIZ_KEY, REG_DTM DESC);
