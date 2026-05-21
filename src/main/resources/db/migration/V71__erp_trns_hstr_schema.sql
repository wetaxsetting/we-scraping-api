-- =============================================================================
-- V71 — ERP_TRNS_HSTR: schema parity (Settings > ERP Transaction History)
-- =============================================================================
-- First migration for the ERP_TRNS_HSTR table — mirrors JEX DEV column-for-
-- column per the legacy-schema-parity rule in wecms-api/CLAUDE.md.
--
-- DEV source-of-truth audit (verified 2026-05-11 via postgres MCP):
--   - 9 columns
--   - PK: (BIZ_KEY, SEQ_NO)
--   - No indexes beyond PK; no FKs; no CHECK; no triggers
--   - 759 rows for tenant VN_VNM0004_000001 (W=533, E=205, D=21)
--
-- DEV column shape:
--   biz_key       VARCHAR(20)   NOT NULL
--   seq_no        NUMERIC(10,0) NOT NULL
--   org_file_nm   VARCHAR(500)  NOT NULL
--   save_file_nm  VARCHAR(500)  NOT NULL
--   file_size     NUMERIC(10,0) NOT NULL
--   erp_type      CHAR(1)       NOT NULL    -- W / E / D
--   trns_type_cd  CHAR(2)       NOT NULL    -- '01'..'17'
--   reg_id        VARCHAR(100)  NOT NULL
--   reg_dtm       VARCHAR(14)   NOT NULL    -- yyyyMMddHHmmss
--
-- JEX citations (read-only reference):
--   WECMS DEV/WECMS/src/wabooks/entity/ErpTrnsHstr.java
--   WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ERP_TRNS_HSTR_R001.xml
--
-- See:
--   docs/refactor-wecms/features/erp-transaction-history.md §3.1
--   docs/refactor-wecms/backend/erp-transaction-history.md §2
-- =============================================================================

CREATE TABLE IF NOT EXISTS ERP_TRNS_HSTR (
    BIZ_KEY       VARCHAR(20)    NOT NULL,
    SEQ_NO        NUMERIC(10, 0) NOT NULL,
    ORG_FILE_NM   VARCHAR(500)   NOT NULL,
    SAVE_FILE_NM  VARCHAR(500)   NOT NULL,
    FILE_SIZE     NUMERIC(10, 0) NOT NULL,
    ERP_TYPE      CHAR(1)        NOT NULL,
    TRNS_TYPE_CD  CHAR(2)        NOT NULL,
    REG_ID        VARCHAR(100)   NOT NULL,
    REG_DTM       VARCHAR(14)    NOT NULL,
    CONSTRAINT PK_ERP_TRNS_HSTR PRIMARY KEY (BIZ_KEY, SEQ_NO)
);

-- Performance index for the canonical date-range filter on the legacy IDO
-- (R001 + R001_CNT both filter on SUBSTRING(REG_DTM, 1, 8)). DEV has no
-- such index — extra performance indexes are allowed beyond DEV parity
-- per wecms-api/CLAUDE.md §"Legacy Schema Parity Rule".
CREATE INDEX IF NOT EXISTS IX_ERP_TRNS_HSTR_BIZ_REG_DTM
    ON ERP_TRNS_HSTR (BIZ_KEY, REG_DTM DESC);

-- Optional helper index for the ERP_TYPE tab counts (W / E / D fan-out).
-- Cheap to maintain because the table grows at the rate of one row per
-- file exchange and the column has only three distinct values.
CREATE INDEX IF NOT EXISTS IX_ERP_TRNS_HSTR_BIZ_ERP_TYPE
    ON ERP_TRNS_HSTR (BIZ_KEY, ERP_TYPE);
