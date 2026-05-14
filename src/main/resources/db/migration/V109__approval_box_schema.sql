-- ============================================================================
-- V84__approval_box_schema.sql
--
-- Payment > Approval Box — schema parity port from DEV (wecmsvndev).
-- Mirrors legacy:
--   - WECMS DEV/WECMS/src/wabooks/entity/ExptResolMstr.java
--     (table EXPT_RESOL_MSTR — composite PK BIZ_KEY + APPR_SEQ_NO)
--   - WECMS DEV/WECMS/src/wabooks/entity/ExptResolApprvrInfm.java
--     (table EXPT_RESOL_APPRVR_INFM — composite PK + APPRVR_ID)
--
-- DEV inspection: information_schema.columns + pg_constraint + pg_indexes
-- on the read-only `postgres_dev` MCP. Column count + types verified
-- 2026-05-13 (see docs/refactor-wecms/features/payment/approval-box/
-- database-impact.md §3 for the inventory + sample rows).
--
-- Scope:
--   - EXPT_RESOL_MSTR — full column-for-column port (47 columns).
--   - EXPT_RESOL_APPRVR_INFM — full column-for-column port (13 columns).
--   - View Setting seed for WEB_SVC_ID = 'approval-box' / DYM_AREA_ID = 'default'.
--
-- Deferred to follow-up MRs (per docs/refactor-wecms/features/payment/
-- approval-box/migration-plan.md Phase 2-4):
--   - EXPT_RESOL_FILE / EXPT_RESOL_PAY_EVID_FILE — needed by detail-popup port.
--   - EXPT_RESOL_APPR_LINE — owned by Settings > Approval Line feature.
--   - BLTB_POST_INFM / OVRS_PYMN_MSTR / SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR /
--     CARD_TRNS_HSTR projections — current list query does not need them
--     (see Behaviour delta D1 in design-backend.md §13: refactor exposes
--     a single currency + amount per row rather than the legacy currency-
--     by-column model, sourced from EXPT_RESOL_MSTR.TOT_PAY_KHR_AMT which
--     is the live amount column for VN tenants).
--
-- Constraints carried from DEV:
--   - EXPT_RESOL_MSTR_PK   PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO)
--   - EXPT_RESOL_MSTR_FK01 FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM
--   - EXPT_RESOL_MSTR_CHK01 CHECK (APPR_DV  IN ('00','10','20','21','30',
--                                  '31','32','33','34','35','40','50','60','70'))
--   - EXPT_RESOL_MSTR_CHK02 CHECK (APPR_STS IN ('0','1','2','4','9'))
--   - EXPT_RESOL_MSTR_CHK03 CHECK (DEL_YN      IN ('Y','N'))
--   - EXPT_RESOL_MSTR_CHK04 CHECK (HAND_REG_YN IN ('Y','N'))
--   - PK_EXPT_RESOL_APPRVR PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO, APPRVR_ID)
--
-- Deferred constraint: legacy EXPT_RESOL_APPRVR_INFM has NO foreign-key to
-- EXPT_RESOL_MSTR (verified on DEV via pg_constraint). Preserved here —
-- application code enforces cascade ordering on delete. Adding the FK
-- locally would catch the bug class but diverge from DEV; defer to a
-- future schema-parity-tightening audit.
--
-- Idempotency: CREATE TABLE IF NOT EXISTS + INSERT ... ON CONFLICT
-- DO NOTHING. Safe to re-run on a DB that already has the rows.
-- ============================================================================

CREATE TABLE IF NOT EXISTS EXPT_RESOL_MSTR (
    BIZ_KEY               VARCHAR(20)   NOT NULL,
    APPR_SEQ_NO           VARCHAR(15)   NOT NULL,
    APPR_DV               VARCHAR(2)    NOT NULL,
    APPR_STS              VARCHAR(2)    NOT NULL DEFAULT '0',
    DRAFT_DATE            VARCHAR(8)    NOT NULL,
    DRAFT_TIME            VARCHAR(6)    NOT NULL,
    DRAFT_USER_ID         VARCHAR(100)  NOT NULL,
    DRAFT_USER_NM         VARCHAR(100)  NOT NULL,
    DRAFT_USER_POS_NM     VARCHAR(100),
    DRAFT_USER_DEPT_CD    VARCHAR(20),
    DRAFT_USER_DEPT_NM    VARCHAR(100),
    DRAFT_USER_SITE_CD    VARCHAR(20)   NOT NULL,
    DRAFT_USER_SITE_NM    VARCHAR(200)  NOT NULL,
    TITL                  VARCHAR(200)  NOT NULL,
    CONT                  TEXT,
    DOC_NO                VARCHAR(50)   NOT NULL,
    DOC_NM                VARCHAR(100)  NOT NULL,
    TOT_PAY_VND_AMT       NUMERIC                DEFAULT 0,
    TOT_PAY_USD_AMT       NUMERIC                DEFAULT 0,
    PAY_DATE              VARCHAR(8),
    DEL_YN                VARCHAR(1)    NOT NULL DEFAULT 'N',
    HAND_REG_YN           VARCHAR(1)    NOT NULL DEFAULT 'Y',
    REG_ID                VARCHAR(100)  NOT NULL,
    REG_DTM               VARCHAR(14)   NOT NULL,
    MOD_ID                VARCHAR(100),
    MOD_DTM               VARCHAR(14)   NOT NULL,
    APPR_BOX_USE_YN       VARCHAR(1)    NOT NULL DEFAULT 'N',
    APPR_BOX_DISP_CNT     VARCHAR(1),
    APPR_DAT_DISP_YN      VARCHAR(1)    NOT NULL DEFAULT 'N',
    CMPN_LOGO_DISP_YN     VARCHAR(1)    NOT NULL DEFAULT 'N',
    REG_DISP_YN           VARCHAR(1)    NOT NULL DEFAULT 'N',
    CMPN_LOGO             VARCHAR(50),
    USER_NM               VARCHAR(100),
    PAY_END_YN            VARCHAR(1)    NOT NULL DEFAULT 'N',
    PAY_END_DTM           VARCHAR(14),
    PAY_END_NM            VARCHAR(100),
    TOT_PAY_KHR_AMT       NUMERIC,
    PAY_REQ_DATE          VARCHAR(8),
    REF_DATA_CNT          NUMERIC,
    PAY_YN                CHAR(1)                DEFAULT 'N',
    ADV_PAYM_SEQ          NUMERIC,
    ADV_PAYM_NM           VARCHAR(50),
    BANK_CD               VARCHAR(20),
    BANK_NM               VARCHAR(100),
    ACCT_NO               VARCHAR(100),
    APLY_EXCH_RATE        NUMERIC,
    APPR_TYPE_CD          VARCHAR(2)    NOT NULL DEFAULT '00',
    REG_CHNL              VARCHAR(3)             DEFAULT 'PC',
    PC_MOD_YN             VARCHAR(1)             DEFAULT 'Y',
    APPR_FUND_TP          CHAR(1),
    CONSTRAINT EXPT_RESOL_MSTR_PK    PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO),
    CONSTRAINT EXPT_RESOL_MSTR_FK01  FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM(BIZ_KEY),
    CONSTRAINT EXPT_RESOL_MSTR_CHK01 CHECK (APPR_DV  IN ('00','10','20','21','30','31','32','33','34','35','40','50','60','70')),
    CONSTRAINT EXPT_RESOL_MSTR_CHK02 CHECK (APPR_STS IN ('0','1','2','4','9')),
    CONSTRAINT EXPT_RESOL_MSTR_CHK03 CHECK (DEL_YN      IN ('Y','N')),
    CONSTRAINT EXPT_RESOL_MSTR_CHK04 CHECK (HAND_REG_YN IN ('Y','N'))
);

-- Helper index for the Approval Box list query (filter by biz_key + date
-- range on DRAFT_DATE). DEV has no equivalent index; the refactor adds
-- one because every query scopes to (biz_key, draft_date range).
-- Extra indexes are allowed per the Legacy Schema Parity Rule.
CREATE INDEX IF NOT EXISTS IX_EXPT_RESOL_MSTR_BIZ_DRAFT_DATE
    ON EXPT_RESOL_MSTR (BIZ_KEY, DRAFT_DATE);

-- Helper index for the visibility filter's reg_id branch (non-admin
-- users see rows where erm.reg_id = current user).
CREATE INDEX IF NOT EXISTS IX_EXPT_RESOL_MSTR_BIZ_REG_ID
    ON EXPT_RESOL_MSTR (BIZ_KEY, REG_ID);

CREATE TABLE IF NOT EXISTS EXPT_RESOL_APPRVR_INFM (
    BIZ_KEY        VARCHAR(20)   NOT NULL,
    APPR_SEQ_NO    VARCHAR(15)   NOT NULL,
    APPRVR_ID      VARCHAR(100)  NOT NULL,
    APPRVR_STTS    CHAR(1)       NOT NULL,
    APPRVR_NM      VARCHAR(100)  NOT NULL,
    APPR_DTM       VARCHAR(14),
    REG_ID         VARCHAR(100),
    REG_DTM        VARCHAR(14)   NOT NULL,
    MOD_ID         VARCHAR(100)  NOT NULL,
    MOD_DTM        VARCHAR(14)   NOT NULL,
    READ_YN        VARCHAR(1)    NOT NULL DEFAULT 'N',
    APPRVR_LINE_NO NUMERIC,
    APPR_LINE_NO   NUMERIC,
    CONSTRAINT PK_EXPT_RESOL_APPRVR PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO, APPRVR_ID)
    -- Deferred: legacy schema has no FK from this table to EXPT_RESOL_MSTR.
    -- Preserve the gap so DEV-parity holds; application code enforces
    -- cascade ordering when an approval row is deleted (Phase 4).
);

-- Helper index for the approver-chain fetch + visibility filter
-- (non-admin users see rows where they are an assigned approver).
CREATE INDEX IF NOT EXISTS IX_EXPT_RESOL_APPRVR_INFM_BIZ_APPRVR_ID
    ON EXPT_RESOL_APPRVR_INFM (BIZ_KEY, APPRVR_ID);

-- ----------------------------------------------------------------------------
-- View Setting seed — WEB_SVC_ID = 'approval-box' / DYM_AREA_ID = 'default'.
-- Mirrors discovery.md §7 and design-frontend.md §7. FIELD_IDs match the
-- ApprovalBoxSortField whitelist exactly so a wire sort_field passes the
-- enum lookup. SIZE values are placeholders (Figma WB-Approval-Box widths
-- land with the frontend MR).
-- ----------------------------------------------------------------------------
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('approval-box', 'default', 'CHECKBOX',      'Checkbox',         0,  'Y', 'Y', 'Y', 'N', '38px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'REG_DTM',       'Date',             1,  'Y', 'N', 'Y', 'Y', '120px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'DOC_NM',        'Type',             2,  'Y', 'N', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'DOC_NO',        'Document No.',     3,  'Y', 'N', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'TITL',          'Subject',          4,  'Y', 'N', 'Y', 'Y', '240px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'TOT_PAY_AMT',   'Transfer Amount',  5,  'Y', 'N', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'PAY_END_YN',    'Approval Status',  6,  'Y', 'N', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'PAY_DATE',      'Scheduled Date',   7,  'Y', 'N', 'Y', 'Y', '140px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'DRAFT_USER_NM', 'Requestor',        8,  'Y', 'N', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'APPRER_NM',     'Approver',         9,  'N', 'N', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'CURRENCY',      'Currency',         10, 'N', 'N', 'Y', 'Y', '100px',
     'system', NOW(), 'system', NOW()),
    ('approval-box', 'default', 'ETC',           '',                 99, 'Y', 'Y', 'N', 'N', '60px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
