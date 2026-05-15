-- V148: Dispatch tables for the Transfer ("T-permission") write path.
--
-- All four Payment modules (Internal Transfer / Vendor / Expense / Payroll)
-- write to the same three dispatch tables when the user clicks Transfer:
--
--   • TB_PAY_TARGET     — API path (VND). One row per detail line.
--   • TB_PAY_FILE_MST   — File path (non-VND). One header row per batch.
--   • TB_PAY_FILE_DATA  — File path. One row per detail line under MST.
--
-- Branch lives in TransferDispatchExecutor — VND batches go API,
-- non-VND batches go File. Mirrors legacy PymnHelper.java:2142-2369.
-- The _HIS shadow tables (TB_PAY_TARGET_HIS / *_FILE_MST_HIS / *_FILE_DATA_HIS)
-- are populated by AFTER triggers on each source table; the trigger DDL
-- lives on DEV / UAT / PROD and is intentionally NOT replicated locally
-- (the refactor reads counts via DispatchCountRepository; it does not
-- depend on the HIS tables).
--
-- DEV parity:
--   • Column types + lengths + nullability + defaults copied verbatim
--     from DEV `wecmsvndev.tb_pay_target / *_file_mst / *_file_data`.
--   • PKs match DEV: (TRAN_DT, BIZ_KEY, TRAN_SEQ) for target,
--     (TRAN_DT, BIZ_KEY, FILE_SEQ_NO) for MST, plus SEQ for DATA.
--   • CREATE TABLE IF NOT EXISTS — idempotent on the shared DEV DB
--     (no-op when tables already exist) and creates them on a fresh
--     local DB.

-- ─────────────────────────────────────────────────────────────────────
-- TB_PAY_TARGET  — API path (VND)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS TB_PAY_TARGET (
    TRAN_DT             VARCHAR(8)      NOT NULL,
    BIZ_KEY             VARCHAR(20)     NOT NULL,
    TRAN_SEQ            VARCHAR(10)     NOT NULL,
    OUT_BANK_CD         VARCHAR(15)     NOT NULL,
    WDRL_ACT_NO         VARCHAR(100)    NOT NULL,
    REG_TM              VARCHAR(6)      NOT NULL,
    ANT_DIV_CD          CHAR(5)         NOT NULL,
    TAR_BANK_CD         VARCHAR(15)     NOT NULL,
    FINC_MDA_NO         VARCHAR(100)    NOT NULL,
    FINC_MDA_OWNER_NM   VARCHAR(150),
    TRAN_AMT            NUMERIC(23, 2)  NOT NULL,
    TRAN_USAG           VARCHAR(200),
    PROC_DTTM           VARCHAR(14),
    PROC_STAT           CHAR(1),
    RERUN_DIV           CHAR(1),
    RERUN_REQ_DTTM      VARCHAR(14),
    USER_ID             VARCHAR(100),
    TRSC_CCY_CD         CHAR(3),
    WITHD_CCY_CD        VARCHAR(3),
    DEP_CCY_CD          VARCHAR(3),
    APL_EXC_RATE        NUMERIC(23, 6),
    WITHD_AMT           NUMERIC(23, 2),
    DEP_AMT             NUMERIC(23, 2),
    CONSTRAINT PK_TB_PAY_TARGET
        PRIMARY KEY (TRAN_DT, BIZ_KEY, TRAN_SEQ)
);

-- Lookup index for the read path (DispatchCountRepository).
-- Counts probe via (BIZ_KEY, TRAN_DT, TRAN_SEQ) — same shape as the PK
-- (TRAN_DT, BIZ_KEY, TRAN_SEQ); helper index keeps key order Postgres'
-- planner prefers.
CREATE INDEX IF NOT EXISTS IDX_TB_PAY_TARGET_BIZ_TRAN
    ON TB_PAY_TARGET (BIZ_KEY, TRAN_DT, TRAN_SEQ);

-- ─────────────────────────────────────────────────────────────────────
-- TB_PAY_FILE_MST  — File path header (non-VND)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS TB_PAY_FILE_MST (
    TRAN_DT         VARCHAR(8)      NOT NULL,
    BIZ_KEY         VARCHAR(20)     NOT NULL,
    FILE_SEQ_NO     VARCHAR(7)      NOT NULL,
    OUT_BANK_CD     VARCHAR(15)     NOT NULL,
    WDRL_ACT_NO     VARCHAR(100)    NOT NULL,
    BANK_PRTN_ID    VARCHAR(20)     NOT NULL,
    FUND_TP         CHAR(1)         NOT NULL,
    TRAN_TP         CHAR(1)         NOT NULL,
    PROC_STAT       CHAR(1)         NOT NULL DEFAULT '9',
    REQ_CNT         NUMERIC(7, 0),
    REQ_AMT         NUMERIC(23, 2),
    REG_DTTM        VARCHAR(14),
    PROC_DTTM       VARCHAR(14),
    RS_MSG          VARCHAR(200),
    CONSTRAINT PK_TB_PAY_FILE_MST
        PRIMARY KEY (TRAN_DT, BIZ_KEY, FILE_SEQ_NO)
);

CREATE INDEX IF NOT EXISTS IDX_TB_PAY_FILE_MST_BIZ_TRAN
    ON TB_PAY_FILE_MST (BIZ_KEY, TRAN_DT, FILE_SEQ_NO);

-- ─────────────────────────────────────────────────────────────────────
-- TB_PAY_FILE_DATA  — File path line rows under MST
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS TB_PAY_FILE_DATA (
    TRAN_DT             VARCHAR(8)      NOT NULL,
    BIZ_KEY             VARCHAR(20)     NOT NULL,
    FILE_SEQ_NO         VARCHAR(7)      NOT NULL,
    SEQ                 NUMERIC(4, 0)   NOT NULL,
    TAR_BANK_CD         VARCHAR(15)     NOT NULL,
    FINC_MDA_DIV        VARCHAR(1)      NOT NULL DEFAULT 'A',
    FINC_MDA_NO         VARCHAR(100)    NOT NULL,
    TRSC_CCY_CD         CHAR(3)         NOT NULL DEFAULT 'VND',
    TRAN_AMT            NUMERIC(23, 2)  NOT NULL,
    BANK_CHNL_DIV       CHAR(1)         NOT NULL,
    ANT_DIV_CD          CHAR(1),
    SENDER_NM           VARCHAR(150),
    FINC_MDA_OWNER_NM   VARCHAR(150)    NOT NULL,
    TAR_ACCT_RMRK       VARCHAR(150),
    WDRL_ACCT_RMRK      VARCHAR(150),
    PROC_RSLT_DIV       CHAR(1)         DEFAULT 'N',
    RS_CD               VARCHAR(10),
    RCPT_BANK_CNTRY_CD  VARCHAR(2),
    REMIT_CCY_CD        VARCHAR(3),
    RCPT_USE_ADDR       VARCHAR(300),
    FEE_BRDN_CLS_CD     VARCHAR(3),
    CONSTRAINT PK_TB_PAY_FILE_DATA
        PRIMARY KEY (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, SEQ)
);

CREATE INDEX IF NOT EXISTS IDX_TB_PAY_FILE_DATA_BIZ_TRAN
    ON TB_PAY_FILE_DATA (BIZ_KEY, TRAN_DT, FILE_SEQ_NO, SEQ);
