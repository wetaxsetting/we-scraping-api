-- ============================================================================
-- V15__sprint1_einvoice_schema.sql
--
-- Sprint 1 E-Invoice schema. The legacy BUSA_DOC_MSTR carries 100 columns;
-- Sprint 1 maps only the ~16 the static UI reads. Additional columns may
-- be added in later sprints via ADD COLUMN IF NOT EXISTS.
--
-- Idempotent: safe both on a fresh DB and on a DB that already received a
-- partial legacy import.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BUSA_DOC_MSTR — invoice / business-document master record.
--    DOC_TYPE distinguishes Sales ('S') from Expense ('E').
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS BUSA_DOC_MSTR (
    BIZ_KEY                 VARCHAR(20)  NOT NULL,
    DOC_ID                  VARCHAR(50)  NOT NULL,
    DOC_TYPE                CHAR(1)      NOT NULL,
    INV_TYPE                VARCHAR(1),
    ISSU_DT                 VARCHAR(8)   NOT NULL,
    ISSU_TAX_ID             VARCHAR(40)  NOT NULL,
    TRCO_KEY                VARCHAR(20)  NOT NULL,
    TRCO_TAX_ID             VARCHAR(40),
    TRCO_CMNM               VARCHAR(200),
    TRCO_CMENM              VARCHAR(200),
    TRCO_TRSC_CCY_CD        CHAR(3),
    TOTL_AMT                NUMERIC,
    SPLY_AMT_SUM            NUMERIC,
    TAX_AMT_SUM             NUMERIC,
    EINV_INVOICESTATUS      VARCHAR(100),
    EINV_INVOICESYMBOL      VARCHAR(10),
    REG_ID                  VARCHAR(100) NOT NULL,
    REG_DTM                 VARCHAR(14)  NOT NULL,
    MOD_ID                  VARCHAR(100) NOT NULL,
    MOD_DTM                 VARCHAR(14)  NOT NULL,
    CONSTRAINT pk_busa_doc_mstr PRIMARY KEY (BIZ_KEY, DOC_ID)
);

-- If a future legacy import lands these columns first, the ADD COLUMN
-- statements above will not run — that is the intent. Sprint 1 reads
-- only the columns declared in the CREATE TABLE above.
ALTER TABLE BUSA_DOC_MSTR
    ADD COLUMN IF NOT EXISTS INV_TYPE             VARCHAR(1),
    ADD COLUMN IF NOT EXISTS TOTL_AMT             NUMERIC,
    ADD COLUMN IF NOT EXISTS SPLY_AMT_SUM         NUMERIC,
    ADD COLUMN IF NOT EXISTS TAX_AMT_SUM          NUMERIC,
    ADD COLUMN IF NOT EXISTS EINV_INVOICESTATUS   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS EINV_INVOICESYMBOL   VARCHAR(10),
    ADD COLUMN IF NOT EXISTS TRCO_TRSC_CCY_CD     CHAR(3);

CREATE INDEX IF NOT EXISTS ix_busa_doc_mstr_biz_type_dt
    ON BUSA_DOC_MSTR (BIZ_KEY, DOC_TYPE, ISSU_DT);

-- ----------------------------------------------------------------------------
-- 2. EINV_FEE_DETL — per-invoice fee detail (declared so future detail
--    pages can read it; not consumed by Sprint 1 list endpoints).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS EINV_FEE_DETL (
    BIZ_KEY         VARCHAR(20)  NOT NULL,
    DOC_ID          VARCHAR(50)  NOT NULL,
    DISP_ORD        NUMERIC      NOT NULL,
    EINV_FEENAME    VARCHAR(100),
    EINV_FEE        NUMERIC,
    REG_ID          VARCHAR(100),
    REG_DTM         VARCHAR(14)  NOT NULL,
    MOD_ID          VARCHAR(100),
    MOD_DTM         VARCHAR(14)  NOT NULL,
    CONSTRAINT pk_einv_fee_detl PRIMARY KEY (BIZ_KEY, DOC_ID, DISP_ORD)
);

-- ----------------------------------------------------------------------------
-- 3. EINV_TAX_DETL — per-invoice tax-rate breakdown.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS EINV_TAX_DETL (
    BIZ_KEY         VARCHAR(20)  NOT NULL,
    DOC_ID          VARCHAR(50)  NOT NULL,
    DISP_ORD        NUMERIC      NOT NULL,
    EINV_TAXRATE    VARCHAR(50),
    AMT             NUMERIC,
    TAX_AMT         NUMERIC,
    REG_ID          VARCHAR(100),
    REG_DTM         VARCHAR(14)  NOT NULL,
    MOD_ID          VARCHAR(100),
    MOD_DTM         VARCHAR(14)  NOT NULL,
    CONSTRAINT pk_einv_tax_detl PRIMARY KEY (BIZ_KEY, DOC_ID, DISP_ORD)
);
