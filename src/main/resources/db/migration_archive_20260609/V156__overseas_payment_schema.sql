-- V112: Create overseas-payment tables (OVRS_PYMN_SUMR + OVRS_PYMN_MSTR)
-- Source: wabooks.entity.OvrsPymnSumr, OvrsPymnMstr (JEX DEV)
-- Read by: GET /api/v1/payment/overseas-payment/scheduled (pymn_0007_001_r001)
--
-- Note: EXPT_RESOL_MSTR and EXPT_RESOL_APPRVR_INFM are created by V111
-- (approval_box_schema) with full DEV column-for-column parity. Do NOT
-- re-create them here. The APPR_STS column is the correct DEV name (not
-- APPR_STTS which was a typo in an earlier draft of this migration).

-- ─────────────────────────────────────────────────────────────────────────────
-- OVRS_PYMN_SUMR — overseas payment summary (batch / Excel-upload type = 'E')
-- Source: wabooks.entity.OvrsPymnSumr
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS OVRS_PYMN_SUMR (
    BIZ_KEY             VARCHAR(20)     NOT NULL,
    OVRS_PYMN_NO        VARCHAR(20)     NOT NULL,

    -- Payment classification code (e.g. EX for export remittance)
    OVRS_PYMN_TYPE_CD   VARCHAR(10),

    -- Title / file name of the Excel batch upload
    TITL                VARCHAR(200),

    CCY_CD              VARCHAR(10),
    TOTAL_TRAN_AMT      NUMERIC(20, 2),
    TOT_TRNS_CNT        INTEGER,

    -- Linked approval chain
    APPR_SEQ_NO         VARCHAR(20),

    -- Withdrawal account (source)
    OUT_BANK_CD         VARCHAR(20),
    WDRL_ACT_NO         VARCHAR(100),

    -- Approval status (local-only Phase A bridge; see V151/V152 for context)
    APPR_STTS           VARCHAR(1),

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

CREATE INDEX IF NOT EXISTS IDX_OVRS_PYMN_SUMR_BIZ_DTM
    ON OVRS_PYMN_SUMR (BIZ_KEY, REG_DTM);

CREATE INDEX IF NOT EXISTS IDX_OVRS_PYMN_SUMR_APPR_SEQ
    ON OVRS_PYMN_SUMR (BIZ_KEY, APPR_SEQ_NO);

-- ─────────────────────────────────────────────────────────────────────────────
-- OVRS_PYMN_MSTR — overseas payment master (individual / manual type = 'M')
-- Source: wabooks.entity.OvrsPymnMstr
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS OVRS_PYMN_MSTR (
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
    TOT_TRNS_CNT        INTEGER,

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

CREATE INDEX IF NOT EXISTS IDX_OVRS_PYMN_MSTR_BIZ_DTM
    ON OVRS_PYMN_MSTR (BIZ_KEY, REG_DTM);

CREATE INDEX IF NOT EXISTS IDX_OVRS_PYMN_MSTR_APPR_SEQ
    ON OVRS_PYMN_MSTR (BIZ_KEY, APPR_SEQ_NO);
