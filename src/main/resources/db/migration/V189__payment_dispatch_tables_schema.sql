-- =============================================================================
-- V189 — Payment Dispatch infrastructure tables
-- =============================================================================
-- Six PROD-active-list dispatch tables referenced by the new entities in
-- src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/. Schemas
-- copied column-for-column from wecmsvndev DEV DB (verified via the read-only
-- postgres_dev MCP, 2026-05-19):
--
--   TB_BATCH_SCHEDULE_DTL    — 7 cols, PK (BATCH_ID, OUT_BANK_CD, ANT_DIV_CD)
--   TB_BATCH_SCHEDULE_INFO   — 8 cols, PK (BATCH_ID, OUT_BANK_CD)
--   TB_FIRM_BAT_API_TRAN     — 15 cols, PK (TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID)
--   TB_FIRM_FILE_DATA        — 24 cols, PK (TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)
--   TB_FIRM_FILE_MST         — 26 cols, PK (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM)
--   TB_FIRM_FILE_RSLT        — 22 cols, PK (TRAN_DT, BIZ_KEY, FIRM_FILE_NM, SEQ)
--
-- All six are on the PROD Active Table List in `wecms-api-main/CLAUDE.md`.
-- Idempotent — CREATE TABLE IF NOT EXISTS so re-running on a DB that already
-- has these tables (the wecmsvndev source) is a no-op.
-- =============================================================================

CREATE TABLE IF NOT EXISTS TB_BATCH_SCHEDULE_DTL (
    BATCH_ID         VARCHAR(50)  NOT NULL,
    OUT_BANK_CD      VARCHAR(15)  NOT NULL,
    ANT_DIV_CD       CHAR(5)      NOT NULL,
    BATCH_START_TM   CHAR(6)      NOT NULL,
    BATCH_END_TM     CHAR(6)      NOT NULL,
    REG_DTTM         VARCHAR(14),
    LAST_EDTR        VARCHAR(20),
    CONSTRAINT pk_tb_batch_schedule_dtl PRIMARY KEY (BATCH_ID, OUT_BANK_CD, ANT_DIV_CD)
);

CREATE TABLE IF NOT EXISTS TB_BATCH_SCHEDULE_INFO (
    BATCH_ID         VARCHAR(50)  NOT NULL,
    OUT_BANK_CD      VARCHAR(15)  NOT NULL,
    ANT_WHERE_USE_YN CHAR(1),
    BATCH_START_TM   CHAR(6)      NOT NULL,
    BATCH_END_TM     CHAR(6)      NOT NULL,
    REG_DTTM         VARCHAR(14),
    MOD_DTTM         VARCHAR(14),
    LAST_EDTR        VARCHAR(20),
    CONSTRAINT pk_tb_batch_schedule_info PRIMARY KEY (BATCH_ID, OUT_BANK_CD)
);

CREATE TABLE IF NOT EXISTS TB_FIRM_BAT_API_TRAN (
    TRAN_DT          VARCHAR(8)   NOT NULL,
    BIZ_KEY          VARCHAR(20)  NOT NULL,
    OUT_BANK_CD      VARCHAR(15)  NOT NULL,
    TRAN_SEQ         VARCHAR(10)  NOT NULL,
    BANK_PRTN_ID     VARCHAR(20)  NOT NULL,
    API_TRAN_TP      CHAR(5)      NOT NULL,
    TRAN_TM          CHAR(6)      NOT NULL,
    FIRM_FILE_NM     VARCHAR(50)  NOT NULL,
    BAT_CNT          NUMERIC(7,0) NOT NULL,
    BAT_AMT          NUMERIC(23,2) NOT NULL,
    REG_RSLT_CD      VARCHAR(4),
    REG_RSLT_MSG     VARCHAR(150),
    RS_CD            VARCHAR(4),
    RS_MSG           VARCHAR(200),
    RS_DTTM          VARCHAR(14),
    CONSTRAINT pk_tb_firm_bat_api_tran
        PRIMARY KEY (TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID)
);

CREATE TABLE IF NOT EXISTS TB_FIRM_FILE_DATA (
    TRAN_DT             VARCHAR(8)   NOT NULL,
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    FILE_SEQ_NO         VARCHAR(7)   NOT NULL,
    FILE_SEQ            VARCHAR(3)   NOT NULL,
    FIRM_FILE_NM        VARCHAR(50)  NOT NULL,
    ORG_SEQ             NUMERIC(7,0) NOT NULL,
    SEQ                 NUMERIC(7,0) NOT NULL,
    TAR_BANK_CD         VARCHAR(15)  NOT NULL,
    FINC_MDA_DIV        VARCHAR(1)   NOT NULL,
    FINC_MDA_NO         VARCHAR(100) NOT NULL,
    TRSC_CCY_CD         CHAR(3)      NOT NULL,
    TRAN_AMT            NUMERIC(23,2) NOT NULL,
    BANK_CHNL_DIV       CHAR(1),
    ANT_DIV_CD          CHAR(1),
    SENDER_NM           VARCHAR(150),
    FINC_MDA_OWNER_NM   VARCHAR(150),
    TAR_ACCT_RMRK       VARCHAR(150),
    WDRL_ACCT_RMRK      VARCHAR(150),
    PROC_RSLT_DIV       CHAR(1),
    RS_CD               VARCHAR(10),
    RCPT_BANK_CNTRY_CD  VARCHAR(2),
    REMIT_CCY_CD        VARCHAR(3),
    RCPT_USE_ADDR       VARCHAR(300),
    FEE_BRDN_CLS_CD     VARCHAR(3),
    CONSTRAINT pk_tb_firm_file_data
        PRIMARY KEY (TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)
);

CREATE TABLE IF NOT EXISTS TB_FIRM_FILE_MST (
    TRAN_DT          VARCHAR(8)   NOT NULL,
    BIZ_KEY          VARCHAR(20)  NOT NULL,
    FILE_SEQ_NO      VARCHAR(7)   NOT NULL,
    OUT_BANK_CD      VARCHAR(15),
    BANK_PRTN_ID     VARCHAR(20),
    VAN_ORG_CD       VARCHAR(20),
    FILE_TYPE        CHAR(3),
    WDRL_ACT_NO      VARCHAR(100),
    FUND_TP          CHAR(1),
    TRAN_TP          CHAR(1),
    FILE_SEQ         VARCHAR(3),
    FIRM_FILE_NM     VARCHAR(50)  NOT NULL,
    PROC_STEP        CHAR(1),
    PROC_STAT        CHAR(1)      NOT NULL,
    REQ_CNT          NUMERIC(7,0),
    REQ_AMT          NUMERIC(23,2),
    REG_CNT          NUMERIC(7,0),
    REG_AMT          NUMERIC(23,2),
    REG_RSLT_CD      VARCHAR(4),
    REG_RSLT_MSG     VARCHAR(150),
    SUCC_CNT         NUMERIC(7,0),
    SUCC_AMT         NUMERIC(23,2),
    UNPROC_CNT       NUMERIC(7,0),
    UNPROC_AMT       NUMERIC(23,2),
    REG_DTTM         VARCHAR(14),
    PROC_DTTM        VARCHAR(14),
    CONSTRAINT pk_tb_firm_file_mst
        PRIMARY KEY (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM)
);

CREATE TABLE IF NOT EXISTS TB_FIRM_FILE_RSLT (
    TRAN_DT             VARCHAR(8)   NOT NULL,
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    FILE_SEQ            VARCHAR(3)   NOT NULL,
    FIRM_FILE_NM        VARCHAR(50)  NOT NULL,
    SEQ                 NUMERIC(7,0) NOT NULL,
    TAR_BANK_CD         VARCHAR(15)  NOT NULL,
    FINC_MDA_DIV        VARCHAR(1)   NOT NULL,
    FINC_MDA_NO         VARCHAR(100) NOT NULL,
    TRSC_CCY_CD         CHAR(3)      NOT NULL,
    TRAN_AMT            NUMERIC(23,2) NOT NULL,
    BANK_CHNL_DIV       CHAR(1),
    ANT_DIV_CD          CHAR(1),
    SENDER_NM           VARCHAR(150),
    FINC_MDA_OWNER_NM   VARCHAR(150),
    TAR_ACCT_RMRK       VARCHAR(150),
    WDRL_ACCT_RMRK      VARCHAR(150),
    PROC_RSLT_DIV       CHAR(1)      NOT NULL,
    RS_CD               VARCHAR(10)  NOT NULL,
    RCPT_BANK_CNTRY_CD  VARCHAR(2),
    REMIT_CCY_CD        VARCHAR(3),
    RCPT_USE_ADDR       VARCHAR(300),
    FEE_BRDN_CLS_CD     VARCHAR(3),
    CONSTRAINT pk_tb_firm_file_rslt
        PRIMARY KEY (TRAN_DT, BIZ_KEY, FIRM_FILE_NM, SEQ)
);
