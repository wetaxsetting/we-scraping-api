-- ============================================================================
-- V85__internal_transfer_schema.sql
--
-- Payment > Internal Transfer — schema parity port from DEV (wecmsvndev).
-- Mirrors legacy:
--   - WECMS DEV/WECMS/src/wabooks/entity/ScdulPymnSumr.java
--     (table SCDUL_PYMN_SUMR — composite PK BIZ_KEY + SCDUL_PYMN_NO)
--   - WECMS DEV/WECMS/src/wabooks/entity/ScdulPymnSumrDetl.java
--     (table SCDUL_PYMN_SUMR_DETL — composite PK + SCDUL_PYMN_DETL_NO)
--
-- Companion docs:
--   - docs/refactor-wecms/database/internal-transfer.md
--   - docs/refactor-wecms/backend/internal-transfer.md
--   - docs/refactor-wecms/migration/internal-transfer-mapping.md
--
-- Scope (this MR — Phase A read path + draft persistence):
--   - SCDUL_PYMN_SUMR — column-for-column port of the Sprint 2 read/write columns
--   - SCDUL_PYMN_SUMR_DETL — column-for-column port
--   - INTERNAL_TRANSFER_KEY_SEQ — refactor-only helper for the Java-side
--     SCDUL_PYMN_NO generator (replaces legacy wb_get_next_val branch which
--     is intentionally NOT ported per api CLAUDE.md "DB Function / Legacy
--     Function Policy").
--   - View Setting seeds for the 4 screen ids
--     ('payment-internal-transfer-scheduled', '-result',
--      '-scheduled-detail', '-result-detail') / DYM_AREA_ID = 'default'.
--
-- Deferred to follow-up MRs (per backend doc §13 + open questions):
--   - TB_PAY_TARGET / TB_PAY_FILE_MST / TB_PAY_FILE_DATA — needed for the
--     actual dispatch path. Phase A ships InternalTransferDispatchService as
--     a local stub that stamps TRNF_YN='Y' on the master without inserting
--     dispatch rows (per the prompt's "local stubs only" rule).
--   - SCDUL_PYMN_PAY_TARGET_CONN / SCDUL_PYMN_PAY_FILE_MST_CONN — join tables
--     paired with the dispatch tables above.
--   - SCDUL_PYMN_SUMR_HIS / SCDUL_PYMN_SUMR_DETL_HIS — legacy audit-history
--     twins; refactor uses AUDIT_LOGS_INFM instead, so these stay schema-
--     parity-only and are NOT written by the refactor service. Deferred
--     until a real consumer materialises.
--
-- Constraints carried from DEV (verified against legacy
-- ScdulPymnSumr.java + ScdulPymnSumrPk.java + entity Javadoc):
--   - SCDUL_PYMN_SUMR_PK      PRIMARY KEY (BIZ_KEY, SCDUL_PYMN_NO)
--   - SCDUL_PYMN_SUMR_DETL_PK PRIMARY KEY (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)
--   - FK SCDUL_PYMN_SUMR (BIZ_KEY) → BUSI_INFM(BIZ_KEY)
--   - FK SCDUL_PYMN_SUMR_DETL (BIZ_KEY, SCDUL_PYMN_NO) → SCDUL_PYMN_SUMR
--
-- Deferred FKs:
--   - SCDUL_PYMN_SUMR (BIZ_KEY, APPR_SEQ_NO) → EXPT_RESOL_MSTR — APPR_SEQ_NO
--     is nullable for draft rows; FK requires both columns NOT NULL on the
--     child. Application code enforces the link.
--   - SCDUL_PYMN_SUMR_DETL (BIZ_KEY, CHART_ACCT_SEQ_NO) → CHART_ACCT_INFM —
--     CHART_ACCT_INFM is not yet migrated locally (legacy table in PROD
--     active list — deferred to a future Settings sprint).
--   - Audit-history triggers (legacy _log_h) — refactor uses AUDIT_LOGS_INFM.
--
-- Idempotency: CREATE TABLE IF NOT EXISTS + INSERT ... ON CONFLICT
-- DO NOTHING. Safe to re-run on a DB that already has the rows.
--
-- Run policy: refactor is local-only R&D. DO NOT execute against DEV / UAT /
-- PROD — Flyway runs only against the developer's local PostgreSQL
-- (localhost:5434/wecms_dev) per workspace CLAUDE.md.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- SCDUL_PYMN_SUMR — Internal Transfer (and other Payment-module) batch master.
-- One row per uploaded file = one "Scheduled Transfer". The refactor's
-- Internal Transfer scope filters every query on SCDUL_PYMN_TYPE_CD = '03'.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SCDUL_PYMN_SUMR (
    BIZ_KEY              VARCHAR(20)   NOT NULL,
    SCDUL_PYMN_NO        VARCHAR(20)   NOT NULL,
    SCDUL_PYMN_TYPE_CD   VARCHAR(2)    NOT NULL,
    OUT_BANK_CD          VARCHAR(20)   NOT NULL,
    WDRL_ACT_NO          VARCHAR(100)  NOT NULL,
    CCY_CD               VARCHAR(3)    NOT NULL,
    TITL                 VARCHAR(100)  NOT NULL,
    TOTAL_TRAN_AMT       NUMERIC(20,6) NOT NULL DEFAULT 0,
    TOT_TRNS_CNT         NUMERIC(15,0) NOT NULL DEFAULT 0,
    APPR_SEQ_NO          VARCHAR(15),
    TRNF_YN              VARCHAR(1)    NOT NULL DEFAULT 'N',
    TRNF_DTM             VARCHAR(14),
    PAY_TRAN_DT          VARCHAR(8),
    PAY_FILE_SEQ_NO      VARCHAR(20),
    ERP_FILE_DATE        VARCHAR(8),
    ERP_FILE_NUM         VARCHAR(50),
    ERP_PROC_CMPL_YN     VARCHAR(1)    NOT NULL DEFAULT 'N',
    WDRL_CCY_CD          VARCHAR(3),
    ACCT_SEQ_NO          VARCHAR(20),
    BANK_TRNS_SHORT_ID   VARCHAR(20),
    BANK_TRNS_FULL_ID    VARCHAR(100),
    LINK_MATCH_YN        VARCHAR(1)    DEFAULT 'N',
    -- FileInfo (flattened from legacy FileInfo bean; legacy stores per-column).
    FILE_SIZE            NUMERIC(20,0),
    ORG_FILE_NM          VARCHAR(500),
    SAVE_FILE_NM         VARCHAR(500),
    FULL_PATH            VARCHAR(1000),
    -- Audit columns (BaseEntity).
    REG_ID               VARCHAR(100)  NOT NULL,
    REG_DTM              VARCHAR(14)   NOT NULL,
    MOD_ID               VARCHAR(100),
    MOD_DTM              VARCHAR(14)   NOT NULL,
    CONSTRAINT SCDUL_PYMN_SUMR_PK    PRIMARY KEY (BIZ_KEY, SCDUL_PYMN_NO),
    CONSTRAINT SCDUL_PYMN_SUMR_FK01  FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM(BIZ_KEY),
    CONSTRAINT SCDUL_PYMN_SUMR_CHK01 CHECK (SCDUL_PYMN_TYPE_CD IN ('01','02','03','04','05','06')),
    CONSTRAINT SCDUL_PYMN_SUMR_CHK02 CHECK (TRNF_YN IN ('Y','N')),
    CONSTRAINT SCDUL_PYMN_SUMR_CHK03 CHECK (ERP_PROC_CMPL_YN IN ('Y','N'))
);

-- Helper indexes for the list query (filter by biz_key + scdul_pymn_type_cd +
-- date range on REG_DTM). DEV has no equivalent index; the refactor adds it
-- because the Internal Transfer list query always scopes to (biz_key, type='03').
-- Extra indexes are allowed per the Legacy Schema Parity Rule.
CREATE INDEX IF NOT EXISTS IX_SCDUL_PYMN_SUMR_BIZ_TYPE_REG
    ON SCDUL_PYMN_SUMR (BIZ_KEY, SCDUL_PYMN_TYPE_CD, REG_DTM DESC);

CREATE INDEX IF NOT EXISTS IX_SCDUL_PYMN_SUMR_BIZ_TRNF_YN
    ON SCDUL_PYMN_SUMR (BIZ_KEY, TRNF_YN);

CREATE INDEX IF NOT EXISTS IX_SCDUL_PYMN_SUMR_BIZ_APPR
    ON SCDUL_PYMN_SUMR (BIZ_KEY, APPR_SEQ_NO);


-- ----------------------------------------------------------------------------
-- SCDUL_PYMN_SUMR_DETL — Internal Transfer batch lines. One row per
-- recipient/amount line within a batch.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SCDUL_PYMN_SUMR_DETL (
    BIZ_KEY              VARCHAR(20)   NOT NULL,
    SCDUL_PYMN_NO        VARCHAR(20)   NOT NULL,
    SCDUL_PYMN_DETL_NO   VARCHAR(20)   NOT NULL,
    TRAN_AMT             NUMERIC(20,6) NOT NULL DEFAULT 0,
    TRNF_CCY_CD          VARCHAR(3)    NOT NULL,
    ANT_DIV_CD           VARCHAR(5),
    RCPT_NM              VARCHAR(100),
    TAR_BANK_CD          VARCHAR(15),
    FINC_MDA_NO          VARCHAR(100)  NOT NULL,
    TRAN_USAG            VARCHAR(200),
    TAR_ACCT_RMRK        VARCHAR(255),
    WDRL_ACT_RMRK        VARCHAR(255),
    PAY_TRAN_DT          VARCHAR(8),
    PAY_TRAN_SEQ         VARCHAR(20),
    PAY_FILE_SEQ_NO      VARCHAR(20),
    PAY_SEQ              NUMERIC(15,0),
    TRCO_KEY             VARCHAR(20),
    HAND_TRCO_KEY        VARCHAR(20),
    DOC_NO               VARCHAR(50),
    NOTE                 VARCHAR(255),
    CITAD_BANK_CD        VARCHAR(200),
    TRCO_CMNM            VARCHAR(200),
    TRCO_CMENM           VARCHAR(200),
    CHART_ACCT_CD        VARCHAR(20),
    CHART_ACCT_SEQ_NO    VARCHAR(10),
    ERP_FILE_DATE        VARCHAR(8),
    ERP_FILE_NUM         VARCHAR(50),
    ERP_FILE_SEQ         NUMERIC(15,0),
    ERP_ATTRIBUTE2       VARCHAR(100),
    DEPO_CCY_CD          VARCHAR(3),
    ACCT_SEQ_NO          VARCHAR(20),
    BANK_TRNS_SHORT_ID   VARCHAR(20),
    BANK_TRNS_FULL_ID    VARCHAR(100),
    LINK_MATCH_YN        VARCHAR(1)    DEFAULT 'N',
    -- Audit columns (BaseEntity).
    REG_ID               VARCHAR(100)  NOT NULL,
    REG_DTM              VARCHAR(14)   NOT NULL,
    MOD_ID               VARCHAR(100),
    MOD_DTM              VARCHAR(14)   NOT NULL,
    CONSTRAINT SCDUL_PYMN_SUMR_DETL_PK   PRIMARY KEY (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO),
    CONSTRAINT SCDUL_PYMN_SUMR_DETL_FK01 FOREIGN KEY (BIZ_KEY, SCDUL_PYMN_NO)
                                          REFERENCES SCDUL_PYMN_SUMR(BIZ_KEY, SCDUL_PYMN_NO)
);

CREATE INDEX IF NOT EXISTS IX_SCDUL_PYMN_SUMR_DETL_BIZ_TRCO
    ON SCDUL_PYMN_SUMR_DETL (BIZ_KEY, TRCO_KEY);

CREATE INDEX IF NOT EXISTS IX_SCDUL_PYMN_SUMR_DETL_BIZ_CHART
    ON SCDUL_PYMN_SUMR_DETL (BIZ_KEY, CHART_ACCT_SEQ_NO);


-- ----------------------------------------------------------------------------
-- INTERNAL_TRANSFER_KEY_SEQ — refactor-only helper for the Java-side
-- SCDUL_PYMN_NO generator. Replaces the legacy
-- wb_get_next_val('scdul_pymn_sumr', 'scdul_pymn_no', BIZ_KEY) branch which
-- is intentionally NOT ported (api CLAUDE.md "DB Function / Legacy Function
-- Policy" rule — no new wb_get_next_val branches in feature code).
--
-- Generated key format: YYYYMMDD + zero-padded(LAST_SEQ, 6). Vietnam-local
-- date (UTC+7) is the source so batches saved on the same Vietnam day share
-- a date prefix regardless of UTC offset.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS INTERNAL_TRANSFER_KEY_SEQ (
    BIZ_KEY   VARCHAR(20) NOT NULL,
    KEY_DT    DATE        NOT NULL,
    LAST_SEQ  INTEGER     NOT NULL DEFAULT 0,
    REG_DTM   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    MOD_DTM   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT INTERNAL_TRANSFER_KEY_SEQ_PK PRIMARY KEY (BIZ_KEY, KEY_DT)
);

COMMENT ON TABLE INTERNAL_TRANSFER_KEY_SEQ IS
    'Refactor-only helper — Java SCDUL_PYMN_NO generator per (tenant, day). '
    'Replaces legacy wb_get_next_val. Not present in DEV / PROD.';


-- ----------------------------------------------------------------------------
-- View Setting seeds — 4 screen ids for the Internal Transfer feature.
-- Mirrors api CLAUDE.md §"Screen Option (View Setting)" rule that every
-- new list page seeds its column set in its own feature migration.
--
-- FIELD_IDs match the InternalTransferSortField / InternalTransferResultSortField
-- whitelist enums exactly so a wire sort_field passes the enum lookup.
-- ----------------------------------------------------------------------------

-- Screen 1: Scheduled Transfer list (Payment > Internal Transfer > Scheduled Transfer)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-internal-transfer-scheduled', 'default', 'CHECKBOX',         'Checkbox',              0,  'Y', 'Y', 'Y', 'N', '38px',  'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'REG_DTM',          'Date / Time',           1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'TITL',             'File Name',             2,  'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'TOT_TRNS_CNT',     'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'TOTAL_TRAN_AMT',   'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'APPR_STTS',        'Approval Status',       5,  'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'REG_ID',           'Requestor',             6,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'APPR_SEQ_NO',      'Approval Ref.',         7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'TRNF_YN',          'Transfer Status',       8,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'DOC_NO',           'Doc No.',               9,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'ETC',              '',                      99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 2: Transfer Result list (read-only)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-internal-transfer-result', 'default', 'REG_DTM',          'Date / Time',           1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result', 'default', 'TITL',             'File Name',             2,  'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result', 'default', 'TOT_TRNS_CNT',     'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result', 'default', 'TOTAL_TRAN_AMT',   'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result', 'default', 'TRNF_YN',          'Transfer Status',       5,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result', 'default', 'APPR_SEQ_NO',      'Approval Ref.',         6,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result', 'default', 'DOC_NO',           'Doc No.',               7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result', 'default', 'ETC',              '',                      99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 3: Scheduled Transfer Detail (per-line table inside the detail page)
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-internal-transfer-scheduled-detail', 'default', 'SCDUL_PYMN_DETL_NO', 'Line No.',         1,  'Y', 'N', 'Y', 'Y', '100px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'TAR_BANK_CD',        'Deposit Bank',     2,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'RCPT_NM',            'Recipient Name',   3,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'FINC_MDA_NO',        'Account No.',      4,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'TRAN_AMT',           'Transfer Amount',  5,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'TRAN_USAG',          'Description',      6,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'CHART_ACCT_CD',      'Reporting Acct',   7,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'DOC_NO',             'Doc No.',          8,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'NOTE',               'Note',             9,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'DEPO_CCY_CD',        'Deposit Currency', 10, 'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled-detail', 'default', 'ETC',                '',                 99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Screen 4: Transfer Result Detail (per-line table with PROC_STAT / RS_CD)
-- Note: PROC_STAT / RS_CD / RS_MSG / PROC_DTTM are surfaced from the
-- (deferred) TB_PAY_TARGET / TB_PAY_FILE_DATA join. Phase A returns NULL
-- for these columns until the dispatch tables land.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-internal-transfer-result-detail', 'default', 'SCDUL_PYMN_DETL_NO', 'Line No.',           1,  'Y', 'N', 'Y', 'Y', '100px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'PROC_DTTM',          'Result Date / Time', 2,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'TAR_BANK_CD',        'Deposit Bank',       3,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'RCPT_NM',            'Recipient Name',     4,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'TRAN_AMT',           'Transfer Amount',    5,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'NOTE',               'Remarks',            6,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'PROC_STAT',          'Results',            7,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'CHART_ACCT_CD',      'Reporting Acct',     8,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'TRAN_USAG',          'Description',        9,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'DOC_NO',             'Document No.',       10, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'BANK_TRNS_SHORT_ID', 'Banking Txn ID',     11, 'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-result-detail', 'default', 'ETC',                '',                   99, 'Y', 'Y', 'N', 'N', '60px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
