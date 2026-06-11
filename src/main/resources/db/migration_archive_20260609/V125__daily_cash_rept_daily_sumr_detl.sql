-- ============================================================================
-- V125__daily_cash_rept_daily_sumr_detl.sql
--
-- Re-introduces DAILY_CASH_REPT_DAILY_SUMR_DETL — the per-account daily
-- detail table backing the Banking > Daily Cash report (refactor slug
-- `daily-cash-report`). The table was dropped in V31 alongside several
-- other report tables flagged as out-of-Sprint-1 scope; it is now back
-- in scope as the canonical PROD Active source for daily-cash reads.
--
-- Sister tables `DAILY_CASH_REPT_DAILY_SUMR` and
-- `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` are NOT in the PROD Active
-- Table List — currency-level summary + flow rollup are computed by
-- aggregating over this detail table in `DailyCashReportService`.
--
-- Column-for-column parity with DEV (`wecmsvndev.daily_cash_rept_daily_sumr_detl`,
-- 15 columns, PK column order `(biz_key, ref_date, acct_type_cd, ccy_cd,
-- bank_cd, acct_no)`, CHECK constraint `ccy_cd IN ('VND','USD','KRW')`).
--
-- Helper indexes added beyond DEV:
--   * ix_*_biz_ref       — (biz_key, ref_date DESC) for the canonical
--                          "as-of date for tenant" query path.
--
-- Idempotent: CREATE TABLE / CREATE INDEX use IF NOT EXISTS.
-- ============================================================================

CREATE TABLE IF NOT EXISTS DAILY_CASH_REPT_DAILY_SUMR_DETL (
    BIZ_KEY        VARCHAR(20)    NOT NULL,
    REF_DATE       VARCHAR(8)     NOT NULL,
    CCY_CD         CHAR(3)        NOT NULL,
    ACCT_TYPE_CD   CHAR(2)        NOT NULL,
    BANK_CD        VARCHAR(20)    NOT NULL,
    ACCT_NO        VARCHAR(100)   NOT NULL,
    BAL            NUMERIC(20, 6) NOT NULL,
    LOAN_NM        VARCHAR(100),
    EXPI_DT        VARCHAR(8),
    SND_CNT        NUMERIC(20, 6) NOT NULL,
    SND_AMT        NUMERIC(20, 6) NOT NULL,
    RCV_CNT        NUMERIC(20, 6) NOT NULL,
    RCV_AMT        NUMERIC(20, 6) NOT NULL,
    AGMT_AMT       NUMERIC(20, 6),
    LST_COLL_DATE  VARCHAR(14),
    CONSTRAINT daily_cash_rept_daily_sumr_detl_pk
        PRIMARY KEY (BIZ_KEY, REF_DATE, ACCT_TYPE_CD, CCY_CD, BANK_CD, ACCT_NO),
    CONSTRAINT daily_cash_rept_daily_sumr_detl_chk01
        CHECK (CCY_CD IN ('VND', 'USD', 'KRW'))
);

CREATE INDEX IF NOT EXISTS ix_daily_cash_rept_daily_sumr_detl_biz_ref
    ON DAILY_CASH_REPT_DAILY_SUMR_DETL (BIZ_KEY, REF_DATE DESC);

-- ---------------------------------------------------------------------------
-- Demo seed — top 30 most-recent rows for tenant VN_VNM0004_000001 from
-- real DEV, ordered by (REF_DATE DESC, ACCT_TYPE_CD, CCY_CD, BANK_CD,
-- ACCT_NO). Idempotent via ON CONFLICT.
-- ---------------------------------------------------------------------------

INSERT INTO DAILY_CASH_REPT_DAILY_SUMR_DETL
    (BIZ_KEY, REF_DATE, CCY_CD, ACCT_TYPE_CD, BANK_CD, ACCT_NO,
     BAL, LOAN_NM, EXPI_DT, SND_CNT, SND_AMT, RCV_CNT, RCV_AMT,
     AGMT_AMT, LST_COLL_DATE)
VALUES
    ('VN_VNM0004_000001', '20260413', 'VND', '03', '11704088', '700005285280',     71745969,        NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260410', 'VND', '03', '11704088', '700005285280',     71745969,        NULL, NULL, 1, 606304166,   4, 26500000,   0,          NULL),
    ('VN_VNM0004_000001', '20260409', 'VND', '03', '11704020', '100200543518',     275058,          NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260409', 'VND', '03', '11704088', '700005285280',     651550135,       NULL, NULL, 3, 70119900,    3, 20514600,   0,          NULL),
    ('VN_VNM0004_000001', '20260408', 'VND', '03', '11704020', '100200543518',     179017,          NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260408', 'VND', '03', '11704020', '100200612649',     41409918,        NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260408', 'VND', '03', '11704020', '100912150373',     15461295,        NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260408', 'VND', '03', '11704088', '700005285280',     701155435,       NULL, NULL, 1, 3287498,     1, 3000000,    0,          NULL),
    ('VN_VNM0004_000001', '20260408', 'USD', '04', '11704020', '100912150384',     26.2,            NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260408', 'KRW', '07', '11704020', '100200626194',     0,               NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260406', 'VND', '03', '11704088', '700005285280',     701442933,       NULL, NULL, 8, 26535874,    1, 4000000,    0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '02', '11704088', '703011613756',     6000000000,      NULL, '20260324', 0, 0,     0, 0,          6000000000, NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '02', '11704088', '703011757830',     2000000000,      NULL, '20260415', 0, 0,     0, 0,          2000000000, NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '02', '11704088', '703012202658',     3000000000,      NULL, '20260625', 0, 0,     0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '03', '11704088', '700005285280',     723978807,       NULL, NULL, 3, 1744850,     1, 3000000,    0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '03', '11704088', '700022499445',     0,               NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '03', '11704088', '700022964690',     4982694,         NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '03', '11704088', '703009080996',     1500000000,      NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '03', '11704088', '703010473002',     500000000,       NULL, '20251223', 0, 0,     0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '03', '11704088', '703011053160',     3000000000,      NULL, '20251223', 0, 0,     0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '03', '11704088', '703011053407',     2007695057,      NULL, '20251223', 0, 0,     0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'VND', '03', '11704088', '703011053414',     3000000000,      NULL, '20251223', 0, 0,     0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'USD', '04', '11704088', '700005285299',     12572.9,         NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'USD', '04', '11704088', '700035842686',     0,               NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260403', 'KRW', '07', '11704088', '700034110936',     0,               NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260402', 'VND', '03', '11704088', '700005285280',     722723657,       NULL, NULL, 2, 9980198,     0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260401', 'VND', '03', '11704088', '700005285280',     732703855,       NULL, NULL, 1, 408450,      1, 3000000,    0,          NULL),
    ('VN_VNM0004_000001', '20260331', 'VND', '03', '11704088', '700005285280',     730112305,       NULL, NULL, 0, 0,           1, 70000000,   0,          NULL),
    ('VN_VNM0004_000001', '20260330', 'VND', '03', '11704001', '119003026640',     1496575,         NULL, NULL, 0, 0,           0, 0,          0,          NULL),
    ('VN_VNM0004_000001', '20260330', 'VND', '03', '11704088', '700005285280',     660112305,       NULL, NULL, 1, 1166400,     0, 0,          0,          NULL)
ON CONFLICT (BIZ_KEY, REF_DATE, ACCT_TYPE_CD, CCY_CD, BANK_CD, ACCT_NO) DO NOTHING;
