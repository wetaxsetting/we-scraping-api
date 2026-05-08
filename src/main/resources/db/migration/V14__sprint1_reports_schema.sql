-- ============================================================================
-- V14__sprint1_reports_schema.sql
--
-- Sprint 1 Reports schema. Backs the Daily Cash and Daily Financial CMC
-- pages with seeded summary tables. The Daily Financial HS variant and
-- the Cash Flow page are stub-mode in Sprint 1 (see §12 of
-- task-sprint-1-backend-golden-chipmunk.md) and use the same tables
-- under fixed seed rows.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BUSI_COND_DAILY_SUMR — daily business condition summary per trading
--    counterpart and currency. Used for Daily Cash + Daily Financial.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS BUSI_COND_DAILY_SUMR (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    TRCO_KEY            VARCHAR(20)  NOT NULL,
    TRCO_CMNM           VARCHAR(200) NOT NULL,
    TRCO_CMENM          VARCHAR(200) NOT NULL,
    TRCO_TAX_ID         VARCHAR(40),
    TRSC_DT             VARCHAR(8)   NOT NULL,
    CCY_CD              CHAR(3)      NOT NULL,
    SELL_AMT            NUMERIC      NOT NULL DEFAULT 0,
    BUY_AMT             NUMERIC      NOT NULL DEFAULT 0,
    RCV_AMT             NUMERIC      NOT NULL DEFAULT 0,
    SND_AMT             NUMERIC      NOT NULL DEFAULT 0,
    PRJT_NO             VARCHAR(20)  NOT NULL DEFAULT '',
    CONSTRAINT pk_busi_cond_daily_sumr PRIMARY KEY (BIZ_KEY, TRCO_KEY, TRSC_DT, CCY_CD, PRJT_NO)
);

CREATE INDEX IF NOT EXISTS ix_bcds_biz_date
    ON BUSI_COND_DAILY_SUMR (BIZ_KEY, TRSC_DT);

-- ----------------------------------------------------------------------------
-- 2. BUSI_COND_DAILY_SUMR_DETL — line-item detail rows for the parent
--    summary above.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS BUSI_COND_DAILY_SUMR_DETL (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    TRCO_KEY            VARCHAR(20)  NOT NULL,
    TRSC_DT             VARCHAR(8)   NOT NULL,
    CCY_CD              CHAR(3)      NOT NULL,
    PRJT_NO             VARCHAR(20)  NOT NULL DEFAULT '',
    DTL_SEQ_NO          NUMERIC      NOT NULL,
    TRSC_TYPE           VARCHAR(1)   NOT NULL,
    TRCO_CMNM           VARCHAR(200) NOT NULL,
    TRCO_CMENM          VARCHAR(200) NOT NULL,
    TRCO_TAX_ID         VARCHAR(40),
    TRSC_AMT            NUMERIC      NOT NULL DEFAULT 0,
    DOC_TYPE            CHAR(1)      NOT NULL,
    REF_NO              VARCHAR(50),
    REF_NO2             VARCHAR(50),
    USER_ID             VARCHAR(100),
    UOM                 VARCHAR(90),
    CONSTRAINT pk_busi_cond_daily_sumr_detl PRIMARY KEY (BIZ_KEY, TRCO_KEY, TRSC_DT, CCY_CD, PRJT_NO, DTL_SEQ_NO)
);

-- ----------------------------------------------------------------------------
-- 3. SELL_HIS_DAILY_SUMR — daily sales-history summary per trading
--    counterpart. Currently unused by the Sprint 1 endpoints but seeded
--    so future report endpoints can read it without another migration.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SELL_HIS_DAILY_SUMR (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    TRSC_DT             VARCHAR(8)   NOT NULL,
    TRSC_TYPE           VARCHAR(1)   NOT NULL,
    TRCO_KEY            VARCHAR(20)  NOT NULL,
    TRCO_CMNM           VARCHAR(200) NOT NULL,
    TRCO_CMENM          VARCHAR(200) NOT NULL,
    TRCO_TAX_ID         VARCHAR(40),
    CCY_CD              CHAR(3)      NOT NULL,
    SPLY_AMT_SUM        NUMERIC      NOT NULL DEFAULT 0,
    TAX_AMT_SUM         NUMERIC      NOT NULL DEFAULT 0,
    SELL_AMT_SUM        NUMERIC      NOT NULL DEFAULT 0,
    RCV_AMT_SUM         NUMERIC      NOT NULL DEFAULT 0,
    PRJT_NO             VARCHAR(20)  NOT NULL DEFAULT '',
    CONT                VARCHAR(200),
    LST_COLL_DATE       VARCHAR(14),
    CONSTRAINT pk_sell_his_daily_sumr PRIMARY KEY (BIZ_KEY, TRSC_DT, TRSC_TYPE, TRCO_KEY, CCY_CD, PRJT_NO)
);

-- ----------------------------------------------------------------------------
-- 4. SELL_HIS_DAILY_SUMR_DETL — line-item detail for SELL_HIS_DAILY_SUMR.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SELL_HIS_DAILY_SUMR_DETL (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    TRSC_DT             VARCHAR(8)   NOT NULL,
    TRSC_TYPE           VARCHAR(1)   NOT NULL,
    TRCO_KEY            VARCHAR(20)  NOT NULL,
    CCY_CD              CHAR(3)      NOT NULL,
    PRJT_NO             VARCHAR(20)  NOT NULL DEFAULT '',
    ITEM_CD             VARCHAR(10)  NOT NULL,
    ITEM_NM             VARCHAR(255) NOT NULL,
    TRCO_CMNM           VARCHAR(200) NOT NULL,
    TRCO_CMENM          VARCHAR(200) NOT NULL,
    TRCO_TAX_ID         VARCHAR(40),
    ITEM_QUNT           NUMERIC      NOT NULL DEFAULT 0,
    ITEM_UNIT_AMT       NUMERIC      NOT NULL DEFAULT 0,
    SPLY_AMT            NUMERIC      NOT NULL DEFAULT 0,
    TAX_AMT             NUMERIC      NOT NULL DEFAULT 0,
    SELL_AMT            NUMERIC      NOT NULL DEFAULT 0,
    RCV_AMT             NUMERIC      NOT NULL DEFAULT 0,
    UOM                 VARCHAR(90),
    CONSTRAINT pk_sell_his_daily_sumr_detl PRIMARY KEY (BIZ_KEY, TRSC_DT, TRSC_TYPE, TRCO_KEY, CCY_CD, PRJT_NO, ITEM_CD)
);

-- ----------------------------------------------------------------------------
-- 5. BAT_CALL_HIS — batch-job execution history. Sprint 1 declares the
--    table so the BCS port (Sprint 2/3) lands without an extra migration;
--    no Sprint 1 endpoint reads it.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS BAT_CALL_HIS (
    JOB_ID              VARCHAR(100) NOT NULL,
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    JOB_DATE            VARCHAR(8)   NOT NULL,
    JOB_STR_TM          VARCHAR(6)   NOT NULL,
    JOB_END_TM          VARCHAR(6),
    TOT_CNT             NUMERIC,
    SUC_CNT             NUMERIC,
    ERR_CNT             NUMERIC,
    RSLT_CD             VARCHAR(10),
    ERR_CONT            VARCHAR(1000),
    REQ_DATE            VARCHAR(10)  NOT NULL,
    MOD_UID             VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_bat_call_his PRIMARY KEY (JOB_ID, BIZ_KEY, JOB_DATE, JOB_STR_TM)
);
