-- ============================================================================
-- V84__contacts_schema.sql
--
-- Settings > Contacts feature schema. Five PROD-active-list tables ported
-- column-for-column from DEV (wecmsvndevdb @ 10.254.242.254:54311):
--   TRCO_INFM             — master contact row (47 columns; PK on trco_key
--                            ONLY; trco_key is a global sequence per DEV's
--                            wb_get_next_val('trco_infm','trco_key',…)
--                            branch, NOT per-tenant)
--   TRCO_ADDR_INFM        — addresses (11 cols)
--   TRCO_ACCT_INFO        — bank accounts for Local contacts (20 cols)
--   TRCO_OVRS_INFM        — overseas beneficiaries (13 cols)
--   PGRO_BSAC_LNKN_INFM   — group-membership rows seeded with `B` + `S`
--                            per legacy `comm_0184_002_c001_act.jsp:131-147`
--
-- DEV column-shape snapshot (Task B1 audit via the read-only `postgres_dev`
-- MCP, 2026-05-13):
--   - TRCO_INFM PK = (trco_key) — single column. UK on (biz_key,
--     trco_nick_nm). CHECK trsc_ccy_cd IN ('VND','USD'). idx_trco_infm
--     redundant with PK; not re-created here.
--   - TRCO_ADDR_INFM PK = (trco_key, biz_key, trco_addr_key) — that
--     column order is verbatim from DEV.
--   - TRCO_ACCT_INFO PK = (biz_key, trco_key, trco_acct_key) — different
--     column order from sibling tables; preserved verbatim per the
--     Legacy Schema Parity Rule.
--   - TRCO_OVRS_INFM PK = (trco_key, biz_key, trco_ovrs_key) — verbatim.
--   - PGRO_BSAC_LNKN_INFM PK = (biz_key, user_id, grp_cd, trco_key). UK
--     on (biz_key, grp_cd, trco_key).
--   - FKs: pgro_bsac_lnkn_infm → busi_infm(biz_key) + → trco_infm(trco_key)
--          trco_acct_info → trco_infm(trco_key)
--     (trco_addr_infm + trco_ovrs_infm have no DEV FKs to carry over.)
--
-- Idempotent — CREATE TABLE IF NOT EXISTS / ADD CONSTRAINT guards. Re-running
-- this migration on an already-migrated DB is a no-op.
--
-- Feature spec: docs/refactor-wecms/features/contacts.md
-- Schema doc:   docs/refactor-wecms/database/contacts-schema.md
-- ============================================================================

-- ---------------------------------------------------------------------------
-- TRCO_INFM — master contact row
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TRCO_INFM (
    TRCO_KEY                 VARCHAR(20)  NOT NULL,
    BIZ_KEY                  VARCHAR(20)  NOT NULL,
    TAX_ID                   VARCHAR(40)  NOT NULL,
    TRCO_CMNM                VARCHAR(200) NOT NULL,
    TRCO_CMENM               VARCHAR(200) NOT NULL,
    TRCO_TAX_ID              VARCHAR(40),
    TRCO_RPPR_NM             VARCHAR(100),
    TRCO_RPRS_TLPH           VARCHAR(30),
    TRCO_FXNO                VARCHAR(30),
    TRCO_BSUN_POST_ADRS      VARCHAR(400),
    TRCO_BSUN_DTL_ADRS       VARCHAR(400),
    TRCO_BSUN_ST             VARCHAR(200),
    TRCO_BSUN_CITY           VARCHAR(200),
    TRCO_STTS                CHAR(1),
    TRCO_LATD                VARCHAR(11),
    TRCO_LOTD                VARCHAR(11),
    TRSC_CCY_CD              CHAR(3)      NOT NULL,
    REG_ID                   VARCHAR(100) NOT NULL,
    REG_DTM                  VARCHAR(14)  NOT NULL,
    MOD_ID                   VARCHAR(100),
    MOD_DTM                  VARCHAR(14),
    TRCO_NICK_NM             VARCHAR(200),
    TRCO_RPPR_ENM            VARCHAR(100),
    TRCO_WABOOKS_BIZ_KEY     VARCHAR(20),
    TRCO_TYPE                VARCHAR(1),
    GST_NO                   VARCHAR(100),
    EML                      VARCHAR(100),
    TAX_ACCOUNTANT_LOGIN_ID  VARCHAR(100),
    TRCO_INQ_YN              CHAR(1)      NOT NULL DEFAULT 'Y',
    MEMO                     VARCHAR(200),
    DFLT_TRCO_KEY            VARCHAR(20),
    TRCO_PAYM_TERM           VARCHAR(3),
    TRCO_OPRT_STTS           VARCHAR(255),
    TRCO_SCRP_SRCH_DTM       VARCHAR(14),
    SAL_DAY                  VARCHAR(2),
    AGNC_WDRW_DT             VARCHAR(8),
    SEL_FEE                  VARCHAR(10),
    CNRC_STR_DT              VARCHAR(8),
    CNRC_END_DT              VARCHAR(8),
    TOKEN_EXPY_DT            VARCHAR(8),
    JRDT_TXOF                VARCHAR(100),
    DECL_STTS                VARCHAR(1),
    JNNG_PATH                VARCHAR(1),
    JNNG_PATH_DTL            VARCHAR(100),
    CNRC_STTS                VARCHAR(1),
    PCSB                     VARCHAR(100),
    HAND_TRCO_KEY            VARCHAR(20),
    CONSTRAINT trco_infm_pk    PRIMARY KEY (TRCO_KEY),
    CONSTRAINT trco_infm_uk01  UNIQUE (BIZ_KEY, TRCO_NICK_NM),
    CONSTRAINT trco_infm_chk01 CHECK (TRSC_CCY_CD IN ('VND', 'USD'))
);

-- Tenant + soft-delete + managed-only filter (legacy `comm_0184_001_r001`
-- WHERE clause). Extra index beyond DEV — allowed by the Legacy Schema
-- Parity Rule for performance.
CREATE INDEX IF NOT EXISTS ix_trco_infm_biz_handkey
    ON TRCO_INFM (BIZ_KEY, HAND_TRCO_KEY)
    WHERE HAND_TRCO_KEY IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_trco_infm_biz_taxid
    ON TRCO_INFM (BIZ_KEY, TRCO_TAX_ID)
    WHERE TRCO_TAX_ID IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_trco_infm_biz_moddtm
    ON TRCO_INFM (BIZ_KEY, MOD_DTM DESC NULLS LAST, TRCO_KEY DESC);

-- ---------------------------------------------------------------------------
-- TRCO_ADDR_INFM — addresses (current + previous via DISP_ORD 1 / 2)
-- OQ-8 resolved 2026-05-12: DISP_ORD is the discriminator, NOT TRCO_ADDR_KEY.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TRCO_ADDR_INFM (
    TRCO_KEY              VARCHAR(20)   NOT NULL,
    BIZ_KEY               VARCHAR(20)   NOT NULL,
    TRCO_ADDR_KEY         VARCHAR(20)   NOT NULL,
    TRCO_BSUN_POST_ADRS   VARCHAR(400),
    DISP_ORD              NUMERIC(10,0) NOT NULL,
    ISSU_DT               VARCHAR(8)    NOT NULL,
    DUE_DT                VARCHAR(8)    NOT NULL,
    REG_ID                VARCHAR(100)  NOT NULL,
    REG_DTM               VARCHAR(14)   NOT NULL,
    MOD_ID                VARCHAR(100)  NOT NULL,
    MOD_DTM               VARCHAR(14)   NOT NULL,
    CONSTRAINT pk_trco_addr_infm PRIMARY KEY (TRCO_KEY, BIZ_KEY, TRCO_ADDR_KEY)
);

CREATE INDEX IF NOT EXISTS ix_trco_addr_infm_biz_trco
    ON TRCO_ADDR_INFM (BIZ_KEY, TRCO_KEY);

-- ---------------------------------------------------------------------------
-- TRCO_ACCT_INFO — bank accounts for Local contacts
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TRCO_ACCT_INFO (
    BIZ_KEY            VARCHAR(20)   NOT NULL,
    TRCO_KEY           VARCHAR(20)   NOT NULL,
    TRCO_ACCT_KEY      VARCHAR(20)   NOT NULL,
    BANK_CD            VARCHAR(20),
    ACCT_NO            VARCHAR(100)  NOT NULL,
    DISP_ORD           NUMERIC(10,0) NOT NULL,
    TRCO_NM            VARCHAR(200),
    TRCO_ENM           VARCHAR(200),
    OWAC_NM            VARCHAR(50),
    REG_ID             VARCHAR(100)  NOT NULL,
    REG_DTM            VARCHAR(14)   NOT NULL,
    MOD_ID             VARCHAR(100)  NOT NULL,
    MOD_DTM            VARCHAR(14)   NOT NULL,
    BANK_NM            VARCHAR(200),
    NAPAS_BANK_CD      VARCHAR(10),
    NAPAS_BANK_YN      VARCHAR(1)    DEFAULT 'N',
    FINC_MDA_OWNER_NM  VARCHAR(150),
    CITAD_BANK_CD      VARCHAR(8),
    OWAC_ADDR          VARCHAR(500),
    ACCT_CCY_CD        VARCHAR(3),
    CONSTRAINT trco_acct_info_pk PRIMARY KEY (BIZ_KEY, TRCO_KEY, TRCO_ACCT_KEY)
);

CREATE INDEX IF NOT EXISTS ix_trco_acct_info_biz_trco
    ON TRCO_ACCT_INFO (BIZ_KEY, TRCO_KEY);

-- ---------------------------------------------------------------------------
-- TRCO_OVRS_INFM — overseas beneficiaries for Overseas contacts
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TRCO_OVRS_INFM (
    TRCO_KEY        VARCHAR(20)  NOT NULL,
    BIZ_KEY         VARCHAR(20)  NOT NULL,
    TRCO_OVRS_KEY   VARCHAR(20)  NOT NULL,
    BENE_NAME       VARCHAR(200),
    BIC_CODE        VARCHAR(50),
    BENE_ACCT_NO    VARCHAR(100),
    BENE_ADDR       VARCHAR(500),
    REG_ID          VARCHAR(100) NOT NULL,
    REG_DTM         VARCHAR(14)  NOT NULL,
    MOD_ID          VARCHAR(100) NOT NULL,
    MOD_DTM         VARCHAR(14)  NOT NULL,
    BENE_BANK_CODE  VARCHAR(20),
    BENE_BANK_NAME  VARCHAR(200),
    CONSTRAINT pk_trco_oversea_infm PRIMARY KEY (TRCO_KEY, BIZ_KEY, TRCO_OVRS_KEY)
);

CREATE INDEX IF NOT EXISTS ix_trco_ovrs_infm_biz_trco
    ON TRCO_OVRS_INFM (BIZ_KEY, TRCO_KEY);

-- ---------------------------------------------------------------------------
-- PGRO_BSAC_LNKN_INFM — per-tenant group / user → trco link rows
-- Legacy `comm_0184_002_c001_act.jsp` seeds 'B' (buy) + 'S' (sale) on
-- contact create with user_id='ALL'.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS PGRO_BSAC_LNKN_INFM (
    BIZ_KEY    VARCHAR(20)  NOT NULL,
    USER_ID    VARCHAR(100) NOT NULL,
    GRP_CD     VARCHAR(10)  NOT NULL,
    TRCO_KEY   VARCHAR(20)  NOT NULL,
    TAX_ID     VARCHAR(40),
    REG_DTM    VARCHAR(14),
    CONSTRAINT pgro_bsac_lnkn_infm_pk   PRIMARY KEY (BIZ_KEY, USER_ID, GRP_CD, TRCO_KEY),
    CONSTRAINT pgro_bsac_lnkn_infm_uk01 UNIQUE (BIZ_KEY, GRP_CD, TRCO_KEY)
);

-- ---------------------------------------------------------------------------
-- Foreign keys — carried over from DEV verbatim. Deferrable so seed
-- INSERTs can run in any order within a single transaction.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'trco_acct_info_fk01') THEN
        ALTER TABLE TRCO_ACCT_INFO
            ADD CONSTRAINT trco_acct_info_fk01
            FOREIGN KEY (TRCO_KEY) REFERENCES TRCO_INFM(TRCO_KEY)
            DEFERRABLE INITIALLY DEFERRED;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pgro_bsac_lnkn_infm_fk01') THEN
        ALTER TABLE PGRO_BSAC_LNKN_INFM
            ADD CONSTRAINT pgro_bsac_lnkn_infm_fk01
            FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM(BIZ_KEY)
            DEFERRABLE INITIALLY DEFERRED;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pgro_bsac_lnkn_infm_fk02') THEN
        ALTER TABLE PGRO_BSAC_LNKN_INFM
            ADD CONSTRAINT pgro_bsac_lnkn_infm_fk02
            FOREIGN KEY (TRCO_KEY) REFERENCES TRCO_INFM(TRCO_KEY)
            DEFERRABLE INITIALLY DEFERRED;
    END IF;
END $$;

-- DEFERRED (DEV-only, out of refactor scope for this MR):
--   TRCO_MDPH (history twin) FKs — TRCO_MDPH table is in the PROD active
--   list but not yet ported. Audit fan-out lives in AUDIT_LOGS_INFM via
--   the global audit aspect (OQ-6 resolved 2026-05-12).
--   Audit-log triggers / TRG_TRCO_* — deferred to the sprint that ports
--   the per-table history tables.
