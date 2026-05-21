-- =============================================================================
-- V77 — BUSI_VAS_ACCT_INFM: schema parity (Settings > VAS Account)
-- =============================================================================
-- First migration for the BUSI_VAS_ACCT_INFM table — mirrors JEX DEV column-
-- for-column per the legacy-schema-parity rule in wecms-api/CLAUDE.md.
--
-- DEV source-of-truth audit (verified 2026-05-11 via postgres MCP):
--   - 13 columns
--   - PK: (BIZ_KEY, VAS_ACCT_SEQ_NO)
--   - No additional indexes beyond PK; no UNIQUE, no CHECK, no FK, no triggers
--   - ~146 rows total across all tenants; 5 live rows for VN_VNM0004_000001
--
-- DEV column shape:
--   biz_key          VARCHAR(20)    NOT NULL
--   vas_acct_seq_no  NUMERIC(10,0)  NOT NULL
--   vas_acct_cd      VARCHAR(15)    NOT NULL
--   vas_acct_nm      VARCHAR(50)
--   vas_acct_enm     VARCHAR(50)
--   vas_acct_type    CHAR(1)        NOT NULL    -- '0' Dual / '1' Credit / '2' Debit
--   stts             CHAR(1)        NOT NULL    -- 'A' Active / 'I' Inactive
--   cont             VARCHAR(200)
--   del_yn           CHAR(1)        NOT NULL  DEFAULT 'N'
--   reg_id           VARCHAR(100)   NOT NULL
--   reg_dtm          VARCHAR(14)    NOT NULL    -- yyyyMMddHHmmss
--   mod_id           VARCHAR(100)   NOT NULL
--   mod_dtm          VARCHAR(14)    NOT NULL
--
-- JEX citations (read-only reference):
--   WECMS DEV/WECMS/src/wabooks/entity/BusiVasAcctInfm.java
--   WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_R001.xml
--
-- See:
--   docs/refactor-wecms/features/vas-account.md
--   docs/refactor-wecms/database/vas-account.md
--
-- Surrogate-key generation:
--   Refactor uses a Java-side generator (VasAcctSeqNoGenerator) — NOT a new
--   wb_get_next_val branch — per wecms-api/CLAUDE.md "DB Function /
--   Legacy Function Policy" (prefer Java over PL/pgSQL for refactor writes).
--
-- Refactor-only deviation from DEV:
--   vas_acct_seq_no is BIGINT locally (DEV is NUMERIC(10,0)). Mirrors the
--   V75 ERP_TRNS_HSTR fix — Hibernate 6.6.45's schema validator binds a
--   Java Long to BIGINT regardless of precision/scale hints, so the
--   refactor accepts the type drift to keep ddl-auto: validate green.
--   Data range fits losslessly: NUMERIC(10,0) max ≈ 10^10 << BIGINT 9.2×10^18.
-- =============================================================================

CREATE TABLE IF NOT EXISTS BUSI_VAS_ACCT_INFM (
    BIZ_KEY          VARCHAR(20)    NOT NULL,
    VAS_ACCT_SEQ_NO  BIGINT         NOT NULL,
    VAS_ACCT_CD      VARCHAR(15)    NOT NULL,
    VAS_ACCT_NM      VARCHAR(50),
    VAS_ACCT_ENM     VARCHAR(50),
    VAS_ACCT_TYPE    CHAR(1)        NOT NULL,
    STTS             CHAR(1)        NOT NULL,
    CONT             VARCHAR(200),
    DEL_YN           CHAR(1)        NOT NULL DEFAULT 'N',
    REG_ID           VARCHAR(100)   NOT NULL,
    REG_DTM          VARCHAR(14)    NOT NULL,
    MOD_ID           VARCHAR(100)   NOT NULL,
    MOD_DTM          VARCHAR(14)    NOT NULL,
    CONSTRAINT PK_BUSI_VAS_ACCT_INFM PRIMARY KEY (BIZ_KEY, VAS_ACCT_SEQ_NO)
);

-- Speed up the duplicate-code check (every create + dedup-on-rename hits this).
-- DEV has no such index; extra performance indexes are allowed per the
-- wecms-api/CLAUDE.md Legacy Schema Parity Rule.
CREATE INDEX IF NOT EXISTS IX_BUSI_VAS_ACCT_INFM_BIZ_CD_DEL
    ON BUSI_VAS_ACCT_INFM (BIZ_KEY, VAS_ACCT_CD, DEL_YN);

-- Speed up the Active+Inactive list filter (the legacy view's two checkboxes).
CREATE INDEX IF NOT EXISTS IX_BUSI_VAS_ACCT_INFM_BIZ_DEL_STTS
    ON BUSI_VAS_ACCT_INFM (BIZ_KEY, DEL_YN, STTS);
