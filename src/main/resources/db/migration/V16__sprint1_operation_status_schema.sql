-- ============================================================================
-- V16__sprint1_operation_status_schema.sql
--
-- Sprint 1 Operation Status schema. Backs the Add-on > Operation Status
-- Inquiry page. Sprint 1 reads only the seeded data; the legacy live
-- ingestion path (OSMC HTTP gateway → Vietnamese General Department of
-- Taxation) is intentionally NOT wired — see §11.8 of
-- task-sprint-1-banking-addon-backend-plan and §12.
--
-- EXT_TRAN_CALL_HIS is an audit log; per legacy it is not BIZ_KEY-scoped
-- (the column is nullable). Service layer joins via SVC_ID ownership
-- when the Sprint 2 OSMC port lands.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. OPRT_STTS_INFO — tax-authority operation status snapshot per
--    company tax code (Operation Status Inquiry list).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS OPRT_STTS_INFO (
    BIZ_KEY                 VARCHAR(20)  NOT NULL,
    TAXCODE                 VARCHAR(100) NOT NULL,
    COMPANYNAME             VARCHAR(100),
    COMPANYSTATUS           VARCHAR(255),
    OFFICEADD               VARCHAR(255),
    SEARCHDATETIME          VARCHAR(14)  NOT NULL,
    REPRESENTATIVEFIRSTNAME VARCHAR(255) NOT NULL DEFAULT '',
    REG_ID                  VARCHAR(100) NOT NULL,
    REG_DTM                 VARCHAR(14)  NOT NULL,
    MOD_ID                  VARCHAR(100) NOT NULL,
    MOD_DTM                 VARCHAR(14),
    CONSTRAINT pk_oprt_stts_info PRIMARY KEY (BIZ_KEY, TAXCODE)
);

CREATE INDEX IF NOT EXISTS ix_oprt_stts_biz_dtm
    ON OPRT_STTS_INFO (BIZ_KEY, SEARCHDATETIME);

-- ----------------------------------------------------------------------------
-- 2. EXT_TRAN_CALL_HIS — external-call audit log.
--    BIZ_KEY is nullable per legacy; tenant filtering is done service-side.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS EXT_TRAN_CALL_HIS (
    SVC_ID          VARCHAR(50)  NOT NULL,
    REF_DATE        VARCHAR(8)   NOT NULL,
    SEQ_NO          NUMERIC      NOT NULL,
    WEB_SVC_ID      VARCHAR(50)  NOT NULL,
    REG_DTM         VARCHAR(14)  NOT NULL,
    RSLT_CD         VARCHAR(10),
    ERR_CONT        VARCHAR(1000),
    BIZ_KEY         VARCHAR(20),
    USER_ID         VARCHAR(100),
    BANK_CD         VARCHAR(20),
    ACCT_NO         VARCHAR(100),
    START_DATE      VARCHAR(8),
    END_DATE        VARCHAR(8),
    TAXCODE         VARCHAR(100),
    CONSTRAINT pk_ext_tran_call_his PRIMARY KEY (SVC_ID, REF_DATE, SEQ_NO)
);
