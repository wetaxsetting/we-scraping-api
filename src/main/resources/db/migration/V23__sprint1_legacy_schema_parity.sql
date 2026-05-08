-- ============================================================================
-- V23__sprint1_legacy_schema_parity.sql
--
-- Legacy schema parity corrective migration. Earlier Sprint 1 migrations
-- (V13 / V15) created subset tables that mirrored only the columns the
-- Sprint 1 static UI reads. This breaks the legacy parity rule: a future
-- legacy data import or Sprint 2 feature would fail because the columns
-- don't exist locally.
--
-- This migration is PURELY ADDITIVE. For every column that exists in
-- wecmsvndev but is missing locally, we add it via
-- `ALTER TABLE ADD COLUMN IF NOT EXISTS` so the local schema mirrors the
-- legacy schema column-for-column. Nullability and defaults match DEV
-- exactly. Numeric precision/scale match DEV exactly.
--
-- We do NOT drop any local columns that exist in V13 but not in DEV
-- (e.g. local DOC_ID, ACCT_SEQ_NO on busa_doc_rcv_pay_his) — dropping
-- could break data already inserted by V17/V22. The extra columns are
-- harmless to Hibernate validate and to legacy ETL, since legacy reads
-- specific column names and ignores extras.
--
-- Idempotent: every step uses IF NOT EXISTS so re-runs are no-ops and
-- a fresh DB built from V1 → V23 ends in the same shape.
--
-- Source of truth: information_schema.columns for wecmsvndev tables on
-- 2026-04-29.
-- ============================================================================

-- ============================================================================
-- 1. ACCT_MSTR — DEV has 48 columns, V13 created 18. Add the missing 30.
-- ============================================================================
ALTER TABLE ACCT_MSTR
    ADD COLUMN IF NOT EXISTS LOAN_NM                VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ACCT_DV_CD             CHAR(1),
    ADD COLUMN IF NOT EXISTS NEW_DT                 VARCHAR(8),
    ADD COLUMN IF NOT EXISTS EXPI_DT                VARCHAR(8),
    ADD COLUMN IF NOT EXISTS AGMT_AMT               NUMERIC(20,6) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS CORC_BF_BAL            NUMERIC(20,6) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS PYAT_DT                VARCHAR(8),
    ADD COLUMN IF NOT EXISTS PYAT_AMT               NUMERIC(20,6) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS CONT_RT                NUMERIC(15,5),
    ADD COLUMN IF NOT EXISTS HAND_REG_YN            VARCHAR(1),
    ADD COLUMN IF NOT EXISTS ACCT_HIS_INQ_STTS      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ACCT_HIS_INQ_MSG       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ACCT_HIS_LST_INQ_DT    VARCHAR(14),
    ADD COLUMN IF NOT EXISTS CERT_INFO_DV           VARCHAR(1),
    ADD COLUMN IF NOT EXISTS CERT_INFO_CON          VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS PRN_SEQ                NUMERIC(2,0),
    ADD COLUMN IF NOT EXISTS NXISAS_ACCT_TYPE       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS BANK_CERT_SEQ          NUMERIC(10,0),
    ADD COLUMN IF NOT EXISTS TRN_LST_SUC_DTM        VARCHAR(14),
    ADD COLUMN IF NOT EXISTS LST_LGN_STTS           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS LST_LGN_STTS_MSG       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS LST_LGN_SUC_DTM        VARCHAR(14),
    ADD COLUMN IF NOT EXISTS ACCT_CUST_TYPE         VARCHAR(1),
    ADD COLUMN IF NOT EXISTS REMIT_OUT_ACCT_USE_YN  CHAR(1),
    ADD COLUMN IF NOT EXISTS ACCT_PWD               VARCHAR(100),
    ADD COLUMN IF NOT EXISTS TRNS_PWD               VARCHAR(100),
    ADD COLUMN IF NOT EXISTS VAS_ACCT_SEQ_NO        NUMERIC(10,0),
    ADD COLUMN IF NOT EXISTS VAS_ACCT_NM            VARCHAR(50),
    ADD COLUMN IF NOT EXISTS VAS_ACCT_CD            VARCHAR(50),
    ADD COLUMN IF NOT EXISTS CONT                   VARCHAR(200);

-- Backfill the new NOT-NULL legacy column ACCT_CUST_TYPE on existing seed
-- rows before tightening to NOT NULL. Default 'C' (corporate) matches the
-- legacy DDL default.
UPDATE ACCT_MSTR SET ACCT_CUST_TYPE = 'C' WHERE ACCT_CUST_TYPE IS NULL;
ALTER TABLE ACCT_MSTR ALTER COLUMN ACCT_CUST_TYPE SET DEFAULT 'C';
ALTER TABLE ACCT_MSTR ALTER COLUMN ACCT_CUST_TYPE SET NOT NULL;

-- The legacy DEFAULT for REMIT_OUT_ACCT_USE_YN is 'N'.
ALTER TABLE ACCT_MSTR ALTER COLUMN REMIT_OUT_ACCT_USE_YN SET DEFAULT 'N';

-- ============================================================================
-- 2. ACCT_TRNS_HSTR — DEV has 28 columns, V13 created 18. Add the missing 10.
-- ============================================================================
ALTER TABLE ACCT_TRNS_HSTR
    ADD COLUMN IF NOT EXISTS HAND_REG_YN          VARCHAR(1),
    ADD COLUMN IF NOT EXISTS CHART_ACCT_SEQ_NO    VARCHAR(10),
    ADD COLUMN IF NOT EXISTS CHART_ACCT_CD        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ERP_SND_YN           CHAR(1) DEFAULT 'N',
    ADD COLUMN IF NOT EXISTS BANK_TRNS_SHORT_ID   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS BANK_TRNS_FULL_ID    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS SCDUL_PYMN_NO        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS SCDUL_PYMN_DETL_NO   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS OVRS_PYMN_NO         VARCHAR(14),
    ADD COLUMN IF NOT EXISTS OVRS_PYMN_DETL_NO    VARCHAR(20);

-- Backfill ERP_SND_YN on existing seed rows then tighten to NOT NULL.
UPDATE ACCT_TRNS_HSTR SET ERP_SND_YN = 'N' WHERE ERP_SND_YN IS NULL;
ALTER TABLE ACCT_TRNS_HSTR ALTER COLUMN ERP_SND_YN SET NOT NULL;

-- ============================================================================
-- 3. ACCT_TRNS_RCV_PAY_INFM — DEV has 37, local has 21. Add the missing 16.
--    Local also has DOC_ID (V13 surplus) — kept; no drop. Legacy doesn't
--    use it, but it doesn't break Hibernate validate.
-- ============================================================================
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM
    ADD COLUMN IF NOT EXISTS CASH_REPY_SEQ_NO     NUMERIC(10,0),
    ADD COLUMN IF NOT EXISTS USAG_DETL_CD         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS CONT                 VARCHAR(200),
    ADD COLUMN IF NOT EXISTS FEE                  NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS DC_AMT               NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS PRJT_NO              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS SRCH_STR             TEXT,
    ADD COLUMN IF NOT EXISTS SIMPLE_RCV_USE_YN    CHAR(1),
    ADD COLUMN IF NOT EXISTS AUTO_SET_YN          CHAR(1),
    ADD COLUMN IF NOT EXISTS SPLIT_YN             CHAR(1),
    ADD COLUMN IF NOT EXISTS AUTO_REG_YN          CHAR(1),
    ADD COLUMN IF NOT EXISTS ADV_PAYM_TRNS_SEQ    NUMERIC(10,0),
    ADD COLUMN IF NOT EXISTS INTR_AMT             NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS RCV_ACCT_BANK_CD     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS RCV_ACCT_NO          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS VAS_ACCT_SEQ_NO      NUMERIC(10,0),
    ADD COLUMN IF NOT EXISTS VAS_ACCT_NM          VARCHAR(50);

-- ============================================================================
-- 4. BUSA_DOC_RCV_PAY_HIS — DEV has 14, local has 8. Add 10 missing legacy
--    columns. Local-extra columns from V13 (ACCT_SEQ_NO, RCV_PAY_DT, AMT)
--    are kept; not used by legacy or Sprint 1.
-- ============================================================================
ALTER TABLE BUSA_DOC_RCV_PAY_HIS
    ADD COLUMN IF NOT EXISTS USER_ID              VARCHAR(100),
    ADD COLUMN IF NOT EXISTS RCV_PAY_HIS_SEQ_NO   NUMERIC(5,0),
    ADD COLUMN IF NOT EXISTS RCV_PAY_WAY_CD       CHAR(1),
    ADD COLUMN IF NOT EXISTS BANK_CD              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ACCT_NO              VARCHAR(100),
    ADD COLUMN IF NOT EXISTS TRSC_DT              VARCHAR(8),
    ADD COLUMN IF NOT EXISTS REF_NO               VARCHAR(100),
    ADD COLUMN IF NOT EXISTS BAL                  NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS DEDU_AMT             NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS CARD_NO              VARCHAR(100);

-- ============================================================================
-- 5. ACCT_TRNS_RCV_PAY_EVID_FILE — DEV has 13, local has 11. Add 7 missing
--    legacy columns. Local-extra columns from V13 (FILE_SEQ_NO, FILE_NM,
--    FILE_PATH, MOD_ID, MOD_DTM) and the FILE_SIZE numeric vs varchar
--    type mismatch are kept; table is unused by Sprint 1.
-- ============================================================================
ALTER TABLE ACCT_TRNS_RCV_PAY_EVID_FILE
    ADD COLUMN IF NOT EXISTS BANK_CD              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ACCT_NO              VARCHAR(100),
    ADD COLUMN IF NOT EXISTS TRSC_DT              VARCHAR(8),
    ADD COLUMN IF NOT EXISTS FILE_ID              VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ORG_FILE_NM          VARCHAR(500),
    ADD COLUMN IF NOT EXISTS SAVE_FILE_NM         VARCHAR(500),
    ADD COLUMN IF NOT EXISTS REG_NM               VARCHAR(100);

-- ============================================================================
-- 6. BUSA_DOC_MSTR — DEV has 100, local has 20. Add the missing 80 columns.
--    This is the biggest parity gap; the static UI only reads ~16 fields,
--    but legacy ETL and Sprint 2 features (full e-invoice detail, payment
--    matching, project tracking) need the full column set.
-- ============================================================================
ALTER TABLE BUSA_DOC_MSTR
    -- Header / issuer columns
    ADD COLUMN IF NOT EXISTS USER_ID                  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ISSU_CMNM                VARCHAR(200),
    ADD COLUMN IF NOT EXISTS ISSU_CMENM               VARCHAR(200),
    ADD COLUMN IF NOT EXISTS ISSU_RPPR_NM             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ISSU_RPRS_TLPH           VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ISSU_FXNO                VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ISSU_BSUN_POST_ADRS      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS ISSU_BSUN_DTL_ADRS       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS ISSU_BSUN_ST             VARCHAR(200),
    ADD COLUMN IF NOT EXISTS ISSU_BSUN_CITY           VARCHAR(200),
    -- Trading counterpart contact / address columns
    ADD COLUMN IF NOT EXISTS TRCO_RPPR_NM             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS TRCO_RPRS_TLPH           VARCHAR(30),
    ADD COLUMN IF NOT EXISTS TRCO_FXNO                VARCHAR(30),
    ADD COLUMN IF NOT EXISTS TRCO_BSUN_POST_ADRS      VARCHAR(400),
    ADD COLUMN IF NOT EXISTS TRCO_BSUN_DTL_ADRS       VARCHAR(400),
    ADD COLUMN IF NOT EXISTS TRCO_BSUN_ST             VARCHAR(200),
    ADD COLUMN IF NOT EXISTS TRCO_BSUN_CITY           VARCHAR(200),
    ADD COLUMN IF NOT EXISTS TRCO_RPPR_ENM            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS TRCO_TYPE                VARCHAR(1),
    -- Dates / payment terms
    ADD COLUMN IF NOT EXISTS DUE_DT                   VARCHAR(8),
    ADD COLUMN IF NOT EXISTS PAY_DUE_CD               VARCHAR(2),
    ADD COLUMN IF NOT EXISTS BUSA_USAG_CD             VARCHAR(20),
    ADD COLUMN IF NOT EXISTS PRJT_NO                  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS REG_DT                   VARCHAR(8),
    -- Status / messaging
    ADD COLUMN IF NOT EXISTS TRSC_MSG                 VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS PROV_STTS               CHAR(2),
    ADD COLUMN IF NOT EXISTS EML_TRF_DTM              VARCHAR(14),
    -- File attachment metadata
    ADD COLUMN IF NOT EXISTS ORG_FILE_NM              VARCHAR(500),
    ADD COLUMN IF NOT EXISTS SAVE_FILE_NM             VARCHAR(500),
    ADD COLUMN IF NOT EXISTS FILE_SIZE                VARCHAR(100),
    ADD COLUMN IF NOT EXISTS CONT                     TEXT,
    -- User / approval / channel
    ADD COLUMN IF NOT EXISTS USE_DT                   VARCHAR(14),
    ADD COLUMN IF NOT EXISTS USER_NM                  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS APPR_SEQ_NO              VARCHAR(15),
    ADD COLUMN IF NOT EXISTS APLY_EXCH_RATE           NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS REG_CHNL                 VARCHAR(3),
    ADD COLUMN IF NOT EXISTS PC_MOD_YN                VARCHAR(1),
    ADD COLUMN IF NOT EXISTS SEQ_NO                   NUMERIC(10,0),
    ADD COLUMN IF NOT EXISTS ITEM_DETL_USE_YN         VARCHAR(1),
    ADD COLUMN IF NOT EXISTS PRJT_USE_YN              VARCHAR(1),
    ADD COLUMN IF NOT EXISTS DOC_TITLE                VARCHAR(500),
    ADD COLUMN IF NOT EXISTS ADV_PAYM_TRNS_SEQ        NUMERIC(10,0),
    ADD COLUMN IF NOT EXISTS HAND_DOC_ID              VARCHAR(50),
    ADD COLUMN IF NOT EXISTS GST_NO                   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS EML                      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS WHT_CD                   VARCHAR(3),
    ADD COLUMN IF NOT EXISTS HAND_REG_YN              VARCHAR(1),
    -- e-Invoice header columns (Vietnam tax authority interop)
    ADD COLUMN IF NOT EXISTS EINV_INVOICEINFO         VARCHAR(500),
    ADD COLUMN IF NOT EXISTS TOTL_DIS_AMT             NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS EINV_CHECKRESULTS        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS EINV_NUMERICSYMBOL       VARCHAR(5),
    ADD COLUMN IF NOT EXISTS EINV_TITLE               VARCHAR(300),
    ADD COLUMN IF NOT EXISTS EINV_MCCQT               VARCHAR(50),
    ADD COLUMN IF NOT EXISTS EINV_BUYERBANKACC        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS EINV_SELLERBANKACC       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS EINV_PAYMENTTYPE         VARCHAR(50),
    ADD COLUMN IF NOT EXISTS EINV_LISTNUMBER          VARCHAR(50),
    ADD COLUMN IF NOT EXISTS EINV_LISTDATE            VARCHAR(50),
    ADD COLUMN IF NOT EXISTS EINV_TOTALAMTINLETTER    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS EINV_BUYERSIGNBY         VARCHAR(200),
    ADD COLUMN IF NOT EXISTS EINV_BUYERSIGNDATE       VARCHAR(8),
    ADD COLUMN IF NOT EXISTS EINV_BUYERSIGNTIME       VARCHAR(6),
    ADD COLUMN IF NOT EXISTS EINV_SELLERSIGNBY        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS EINV_SELLERSIGNDATE      VARCHAR(8),
    ADD COLUMN IF NOT EXISTS EINV_SELLERSIGNTIME      VARCHAR(6),
    ADD COLUMN IF NOT EXISTS EINV_QRCODE              VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS EINV_BUYERFULLNAME       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS TOTL_FEE_AMT             NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS EINV_BUYERBANKNAME       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS EINV_SELLERBANKNAME      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS EINV_ORIGINALINVOICE     VARCHAR(200),
    ADD COLUMN IF NOT EXISTS DIS_TYPE                 CHAR(1),
    ADD COLUMN IF NOT EXISTS EINV_CON_DOC_ID          VARCHAR(50),
    ADD COLUMN IF NOT EXISTS TOTL_BEF_DIS_AMT         NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS EINV_INVOICESTATUS_BEF   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS EINV_TESTRESULTS         CHAR(1),
    ADD COLUMN IF NOT EXISTS EINV_RATE                NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS EINV_BUYERCITIZENID      VARCHAR(12),
    ADD COLUMN IF NOT EXISTS DISP_DOC_ID              VARCHAR(50),
    ADD COLUMN IF NOT EXISTS OVER_DUE_DAYS            VARCHAR(3);

-- Backfill the new NOT-NULL legacy columns then apply the legacy DEFAULTs.
-- Existing V17/V22 rows had no values; defaults match wecmsvndev DDL.
UPDATE BUSA_DOC_MSTR SET PAY_DUE_CD       = '30' WHERE PAY_DUE_CD       IS NULL;
UPDATE BUSA_DOC_MSTR SET PROV_STTS        = 'OP' WHERE PROV_STTS        IS NULL;
UPDATE BUSA_DOC_MSTR SET REG_CHNL         = 'PC' WHERE REG_CHNL         IS NULL;
UPDATE BUSA_DOC_MSTR SET ITEM_DETL_USE_YN = 'N'  WHERE ITEM_DETL_USE_YN IS NULL;
UPDATE BUSA_DOC_MSTR SET PRJT_USE_YN      = 'N'  WHERE PRJT_USE_YN      IS NULL;
UPDATE BUSA_DOC_MSTR SET HAND_REG_YN      = 'Y'  WHERE HAND_REG_YN      IS NULL;

ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN PAY_DUE_CD       SET DEFAULT '30';
ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN PROV_STTS        SET DEFAULT 'OP';
ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN REG_CHNL         SET DEFAULT 'PC';
ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN ITEM_DETL_USE_YN SET DEFAULT 'N';
ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN PRJT_USE_YN      SET DEFAULT 'N';
ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN HAND_REG_YN      SET DEFAULT 'Y';

ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN REG_CHNL         SET NOT NULL;
ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN ITEM_DETL_USE_YN SET NOT NULL;
ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN PRJT_USE_YN      SET NOT NULL;
ALTER TABLE BUSA_DOC_MSTR ALTER COLUMN HAND_REG_YN      SET NOT NULL;
