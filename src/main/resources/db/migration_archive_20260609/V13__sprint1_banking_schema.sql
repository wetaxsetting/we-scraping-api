-- ============================================================================
-- V13__sprint1_banking_schema.sql
--
-- Sprint 1 Banking schema. Mirrors the legacy wecmsvndev tables
-- column-for-column for the subset Sprint 1 actually reads — additional
-- legacy columns (loan/interest/cert/VAS/etc.) are intentionally omitted
-- and may be added in later sprints when a feature needs them.
--
-- Idempotent: every CREATE uses IF NOT EXISTS, so V13 is safe to apply
-- against a DB that was bootstrapped only with V1–V12 (current local
-- laptop state) AND against a DB that already received a partial legacy
-- import. None of the existing auth/empl tables are touched.
--
-- Reference: §11–§13 of task-sprint-1-backend-golden-chipmunk.md.
-- BIZ_KEY uses VARCHAR(20) to match legacy exactly. No FK back to
-- BUSI_INFM (V1 declares BUSI_INFM.BIZ_KEY as VARCHAR(32) so the column
-- types differ); service-layer enforcement via TenantContext is the
-- tenant boundary instead.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. ACCT_MSTR — bank accounts (Account Inquiry VND/FCY).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ACCT_MSTR (
    BIZ_KEY                 VARCHAR(20)  NOT NULL,
    BANK_CD                 VARCHAR(20)  NOT NULL,
    ACCT_NO                 VARCHAR(100) NOT NULL,
    ACCT_TYPE_CD            CHAR(2)      NOT NULL,
    CCY_CD                  CHAR(3)      NOT NULL,
    ACCT_NICK_NM            VARCHAR(50),
    ACCT_NM                 VARCHAR(100),
    BAL                     NUMERIC,
    REAL_AMT                NUMERIC,
    ACCT_BAL_INQ_STTS       VARCHAR(20),
    ACCT_BAL_INQ_MSG        VARCHAR(255),
    ACCT_BAL_UPD_DTM        VARCHAR(14),
    ACCT_BAL_LST_INQ_DTM    VARCHAR(14),
    ACTV_YN                 CHAR(1)      NOT NULL DEFAULT 'Y',
    REG_ID                  VARCHAR(100) NOT NULL,
    REG_DTM                 VARCHAR(14)  NOT NULL,
    MOD_ID                  VARCHAR(100),
    MOD_DTM                 VARCHAR(14),
    CONSTRAINT pk_acct_mstr PRIMARY KEY (BIZ_KEY, BANK_CD, ACCT_NO)
);

CREATE INDEX IF NOT EXISTS ix_acct_mstr_biz_ccy
    ON ACCT_MSTR (BIZ_KEY, CCY_CD);

-- ----------------------------------------------------------------------------
-- 2. ACCT_TRNS_HSTR — bank-statement transactions (Transaction History VND/FCY).
--    PK matches legacy: (BIZ_KEY, ACCT_SEQ_NO).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ACCT_TRNS_HSTR (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    ACCT_SEQ_NO         VARCHAR(20)  NOT NULL,
    BANK_CD             VARCHAR(20),
    ACCT_NO             VARCHAR(100),
    TRSC_DT             VARCHAR(8),
    TRSC_TM             VARCHAR(6),
    CCY_CD              CHAR(3),
    INOUT_DV            CHAR(1),
    TRSC_AMT            NUMERIC,
    BAL                 NUMERIC,
    RMRK                VARCHAR(500),
    RMRK2               VARCHAR(500),
    LINK_MATCH_YN       CHAR(1)      NOT NULL DEFAULT 'N',
    BANK_REF_NO         VARCHAR(100),
    REG_ID              VARCHAR(100),
    REG_DTM             VARCHAR(14),
    MOD_ID              VARCHAR(100),
    MOD_DTM             VARCHAR(14),
    CONSTRAINT pk_acct_trns_hstr PRIMARY KEY (BIZ_KEY, ACCT_SEQ_NO)
);

CREATE INDEX IF NOT EXISTS ix_acct_trns_hstr_acct
    ON ACCT_TRNS_HSTR (BIZ_KEY, BANK_CD, ACCT_NO, TRSC_DT);

-- ----------------------------------------------------------------------------
-- 3. ACCT_TRNS_RCV_PAY_INFM — links a transaction to a receipt/payment
--    record (used to derive the "confirmed" flag in Transaction History).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ACCT_TRNS_RCV_PAY_INFM (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    ACCT_SEQ_NO         VARCHAR(20)  NOT NULL,
    RCV_PAY_SEQ_NO      VARCHAR(20)  NOT NULL,
    DOC_ID              VARCHAR(50),
    USAG_CD             VARCHAR(20),
    VAS_ACCT_CD         VARCHAR(50),
    TRCO_KEY            VARCHAR(20),
    TRCO_CMNM           VARCHAR(200),
    AMT                 NUMERIC,
    CONF_YN             CHAR(1)      NOT NULL DEFAULT 'N',
    CONF_USER_ID        VARCHAR(100),
    CONF_DTM            VARCHAR(14),
    REG_ID              VARCHAR(100),
    REG_DTM             VARCHAR(14),
    MOD_ID              VARCHAR(100),
    MOD_DTM             VARCHAR(14),
    CONSTRAINT pk_acct_trns_rcv_pay_infm PRIMARY KEY (BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO)
);

CREATE INDEX IF NOT EXISTS ix_atrpi_acct_seq
    ON ACCT_TRNS_RCV_PAY_INFM (BIZ_KEY, ACCT_SEQ_NO);

-- ----------------------------------------------------------------------------
-- 4. BUSA_DOC_RCV_PAY_HIS — historical link from a business doc to a
--    receipt/payment row. Sprint 1 reads it only for the "confirmed" flag.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS BUSA_DOC_RCV_PAY_HIS (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    DOC_ID              VARCHAR(50)  NOT NULL,
    SEQ_NO              NUMERIC      NOT NULL,
    ACCT_SEQ_NO         VARCHAR(20),
    RCV_PAY_DT          VARCHAR(8),
    AMT                 NUMERIC,
    REG_ID              VARCHAR(100),
    REG_DTM             VARCHAR(14),
    CONSTRAINT pk_busa_doc_rcv_pay_his PRIMARY KEY (BIZ_KEY, DOC_ID, SEQ_NO)
);

-- ----------------------------------------------------------------------------
-- 5. ACCT_TRNS_RCV_PAY_EVID_FILE — evidence-file metadata for a
--    receipt/payment row. Sprint 1 does not read or write this — declared
--    so V13 establishes the full Sprint 1 banking shape in one place.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ACCT_TRNS_RCV_PAY_EVID_FILE (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    ACCT_SEQ_NO         VARCHAR(20)  NOT NULL,
    RCV_PAY_SEQ_NO      VARCHAR(20)  NOT NULL,
    FILE_SEQ_NO         NUMERIC      NOT NULL,
    FILE_NM             VARCHAR(255),
    FILE_PATH           VARCHAR(500),
    FILE_SIZE           NUMERIC,
    REG_ID              VARCHAR(100),
    REG_DTM             VARCHAR(14),
    MOD_ID              VARCHAR(100),
    MOD_DTM             VARCHAR(14),
    CONSTRAINT pk_acct_trns_rcv_pay_evid_file PRIMARY KEY (BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO, FILE_SEQ_NO)
);

-- ----------------------------------------------------------------------------
-- 6. NAPAS_REMIT_TRNS_HSTR — Vietnamese inter-bank remittance history
--    (Transfer History page).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS NAPAS_REMIT_TRNS_HSTR (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    SEQ_NO              NUMERIC      NOT NULL,
    OUT_BANK_CODE       VARCHAR(20)  NOT NULL,
    OUT_ACCT_NO         VARCHAR(100) NOT NULL,
    TRANS_TYPE          CHAR(1),
    IN_BANK_CODE        VARCHAR(20),
    IN_NAPAS_BANK_CODE  VARCHAR(10),
    IN_ACCT_NO          VARCHAR(100) NOT NULL,
    OWAC_NM             VARCHAR(200),
    CCY_CD              CHAR(3)      NOT NULL,
    TRSC_AMT            NUMERIC      NOT NULL,
    FEE                 NUMERIC,
    FEE_DFR_SBJ         CHAR(1),
    RMRK                VARCHAR(500),
    RMRK2               VARCHAR(500),
    TRNS_STTS           VARCHAR(20),
    TRNS_MSG            VARCHAR(255),
    TRNS_STLM_ID        VARCHAR(100),
    TRNS_STLM_YN        VARCHAR(1),
    REG_ID              VARCHAR(100) NOT NULL,
    REG_DTM             VARCHAR(14)  NOT NULL,
    CONSTRAINT pk_napas_remit_trns_hstr PRIMARY KEY (BIZ_KEY, SEQ_NO)
);

CREATE INDEX IF NOT EXISTS ix_napas_remit_biz_dtm
    ON NAPAS_REMIT_TRNS_HSTR (BIZ_KEY, REG_DTM);
