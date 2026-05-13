-- V85: Create overseas-payment tables
-- Source: wabooks.entity.OvrsPymnSumr, OvrsPymnMstr, ExptResolMstr, ExptResolApprvrInfm (JEX DEV)
-- Read by: GET /api/v1/payment/overseas-payment/scheduled (pymn_0007_001_r001)

-- ─────────────────────────────────────────────────────────────────────────────
-- EXPT_RESOL_MSTR — Approval resolution master
-- Simplified for R&D: APPR_STTS stored directly instead of computed via
-- wb_get_appr_stts (the function evaluates approver rows dynamically in JEX).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE EXPT_RESOL_MSTR (
    BIZ_KEY             VARCHAR(20)     NOT NULL,
    APPR_SEQ_NO         VARCHAR(20)     NOT NULL,

    TITL                VARCHAR(200),
    APPR_TYPE_CD        VARCHAR(10),
    APPR_DV             VARCHAR(1),
    DOC_NO              VARCHAR(50),
    PAY_DATE            VARCHAR(8),

    -- Requestor (DRAFT = submitter in JEX terminology)
    DRAFT_USER_ID       VARCHAR(100),
    DRAFT_USER_NM       VARCHAR(100),

    -- Computed approval status stored directly for R&D parity
    -- JEX computes this via wb_get_appr_stts(biz_key, reg_id, sub_id, appr_seq_no, appr_type_cd)
    APPR_STTS           VARCHAR(20),

    REG_DTM             VARCHAR(14),
    REG_ID              VARCHAR(100),
    MOD_DTM             VARCHAR(14),
    MOD_ID              VARCHAR(100),

    CONSTRAINT PK_EXPT_RESOL_MSTR
        PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- EXPT_RESOL_APPRVR_INFM — per-step approver rows for an approval chain
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE EXPT_RESOL_APPRVR_INFM (
    BIZ_KEY             VARCHAR(20)     NOT NULL,
    APPR_SEQ_NO         VARCHAR(20)     NOT NULL,
    APPRVR_LINE_NO      VARCHAR(5)      NOT NULL,

    APPRVR_ID           VARCHAR(100),
    APPRVR_NM           VARCHAR(100),
    APPRVR_STTS         VARCHAR(20),
    REG_IMG_URL         VARCHAR(500),
    MOD_DTM             VARCHAR(14),

    CONSTRAINT PK_EXPT_RESOL_APPRVR_INFM
        PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO, APPRVR_LINE_NO)
);

CREATE INDEX IDX_EXPT_RESOL_APPRVR_INFM_KEY_SEQ
    ON EXPT_RESOL_APPRVR_INFM (BIZ_KEY, APPR_SEQ_NO);

-- ─────────────────────────────────────────────────────────────────────────────
-- OVRS_PYMN_SUMR — overseas payment summary (batch / Excel-upload type = 'E')
-- Source: wabooks.entity.OvrsPymnSumr
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE OVRS_PYMN_SUMR (
    BIZ_KEY             VARCHAR(20)     NOT NULL,
    OVRS_PYMN_NO        VARCHAR(20)     NOT NULL,

    -- Payment classification code (e.g. EX for export remittance)
    OVRS_PYMN_TYPE_CD   VARCHAR(10),

    -- Title / file name of the Excel batch upload
    TITL                VARCHAR(200),

    CCY_CD              VARCHAR(10),
    TOTAL_TRAN_AMT      NUMERIC(20, 2),
    TOT_TRNS_CNT        NUMERIC(10),

    -- Linked approval chain
    APPR_SEQ_NO         VARCHAR(20),

    -- Withdrawal account (source)
    OUT_BANK_CD         VARCHAR(20),
    WDRL_ACT_NO         VARCHAR(100),

    -- Transfer completion flag (Y after TRNF_YN flip by batch)
    TRNF_YN             VARCHAR(1)      NOT NULL DEFAULT 'N',

    -- Stored bank name (R&D: avoids wb_get_bank_name_any_code call)
    OUT_BANK_NM         VARCHAR(100),

    -- File storage columns (org name shown in UI, save name for retrieval)
    ORG_FILE_NM         VARCHAR(300),
    SAVE_FILE_NM        VARCHAR(300),
    FILE_URL            VARCHAR(500),

    -- ERP sync columns (not surfaced in list but present in JEX entity)
    ERP_FILE_DATE       VARCHAR(8),
    ERP_FILE_NUM        VARCHAR(20),
    ERP_PROC_CMPL_YN    VARCHAR(1),

    REG_DTM             VARCHAR(14)     NOT NULL,
    REG_ID              VARCHAR(100),
    MOD_DTM             VARCHAR(14),
    MOD_ID              VARCHAR(100),

    CONSTRAINT PK_OVRS_PYMN_SUMR
        PRIMARY KEY (BIZ_KEY, OVRS_PYMN_NO)
);

CREATE INDEX IDX_OVRS_PYMN_SUMR_BIZ_DTM
    ON OVRS_PYMN_SUMR (BIZ_KEY, REG_DTM);

CREATE INDEX IDX_OVRS_PYMN_SUMR_APPR_SEQ
    ON OVRS_PYMN_SUMR (BIZ_KEY, APPR_SEQ_NO);

-- ─────────────────────────────────────────────────────────────────────────────
-- OVRS_PYMN_MSTR — overseas payment master (individual / manual type = 'M')
-- Source: wabooks.entity.OvrsPymnMstr
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE OVRS_PYMN_MSTR (
    BIZ_KEY             VARCHAR(20)     NOT NULL,
    OVRS_PYMN_NO        VARCHAR(20)     NOT NULL,

    -- Debit (withdrawal) side
    DBT_ACCT_NO         VARCHAR(100),
    DBT_ACCT_CD         VARCHAR(20),
    DBT_CCY_CD          VARCHAR(10),

    -- Transfer amount (actual debited currency / amount)
    TRNF_CCY_CD         VARCHAR(10),
    TRNF_AMT            NUMERIC(20, 2),
    TOTAL_TRAN_AMT      NUMERIC(20, 2),
    TOT_TRNS_CNT        NUMERIC(10),

    -- Fee payer: O = Ours, S = SHA, B = BEN
    FEE_PAYER_CD        VARCHAR(1),

    -- Beneficiary
    BNFC_NM             VARCHAR(200),
    BNFC_BANK_CD        VARCHAR(20),
    BNFC_ACCT_NO        VARCHAR(100),
    BNFC_BANK_BIC       VARCHAR(20),
    BNFC_ADDR           VARCHAR(500),
    BNFC_BANK_NAME      VARCHAR(200),

    -- Transfer remittance info
    MEMO                VARCHAR(500),

    -- Linked approval chain
    APPR_SEQ_NO         VARCHAR(20),

    -- Transfer completion flag
    TRNF_YN             VARCHAR(1)      NOT NULL DEFAULT 'N',

    REG_DTM             VARCHAR(14)     NOT NULL,
    REG_ID              VARCHAR(100),
    MOD_DTM             VARCHAR(14),
    MOD_ID              VARCHAR(100),

    CONSTRAINT PK_OVRS_PYMN_MSTR
        PRIMARY KEY (BIZ_KEY, OVRS_PYMN_NO)
);

CREATE INDEX IDX_OVRS_PYMN_MSTR_BIZ_DTM
    ON OVRS_PYMN_MSTR (BIZ_KEY, REG_DTM);

CREATE INDEX IDX_OVRS_PYMN_MSTR_APPR_SEQ
    ON OVRS_PYMN_MSTR (BIZ_KEY, APPR_SEQ_NO);
