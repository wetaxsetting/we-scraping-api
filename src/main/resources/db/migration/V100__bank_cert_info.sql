-- ============================================================================
-- V100__bank_cert_info.sql
--
-- Bank credentials master for the NXISAS scraping spike (Banking module).
-- Mirrors JEX legacy `BANK_CERT_INFO` column-for-column (entity reference:
-- `WECMS DEV/WECMS/src/wabooks/entity/BankCertInfo.java` + `BankCertInfoPk.java`).
--
-- PK: (BIZ_KEY, SEQ_NO) — composite, identical to legacy.
--
-- Encryption columns (`CERT_INFO_DETL_CON`, `_CON2`, `_CON3`) hold the
-- bank-side credential payload. Legacy JEX encrypts via `ISASSeedCBC`
-- (Coocon SEED-CBC) wrapped by a PostgreSQL `encrypt(?)` DB function;
-- the refactor switches to JCE AES-GCM in Java (`BankCertCryptoService`,
-- Step 3 of the NXISAS plan), so the column holds an AES-GCM base64
-- ciphertext blob. The column shape stays the legacy VARCHAR(2000) so
-- a future data-migration path stays open.
--
-- Audit columns follow the JEX shape used by ERP_TRNS_HSTR (V71) and
-- EMPL_INFM (V11): VARCHAR(14) for REG_DTM/MOD_DTM, VARCHAR(100) for
-- REG_ID/MOD_ID. Not BaseEntity (legacy timestamps are 14-char strings,
-- not TIMESTAMPTZ).
--
-- BIZ_KEY = VARCHAR(20) per the workspace-wide convention (V28 audit).
--
-- LST_ACCT_LIST_INQ_DT is VARCHAR(8) (yyyyMMdd) — matches the legacy
-- `LocalDate8Serializer` used by the JEX entity.
--
-- NXISAS_GB_SEQ_NO is NUMERIC(10,0) — legacy stores it as a numeric
-- (entity setter casts with `cast(? as numeric)`).
-- ============================================================================

CREATE TABLE IF NOT EXISTS BANK_CERT_INFO (
    BIZ_KEY                 VARCHAR(20)  NOT NULL,
    SEQ_NO                  NUMERIC(10,0) NOT NULL,
    BANK_CD                 VARCHAR(20),
    CERT_INFO_DV            VARCHAR(20),
    CERT_INFO_CON           VARCHAR(2000),
    CERT_INFO_DETL_CON      VARCHAR(2000),
    CERT_INFO_DETL_CON2     VARCHAR(2000),
    CERT_INFO_DETL_CON3     VARCHAR(2000),
    ACCT_LIST_INQ_STTS      VARCHAR(20),
    ACCT_LIST_INQ_MSG       VARCHAR(255),
    LST_ACCT_LIST_INQ_DT    VARCHAR(8),
    NXISAS_GB_SEQ_NO        NUMERIC(10,0),
    REG_ID                  VARCHAR(100),
    REG_DTM                 VARCHAR(14),
    MOD_ID                  VARCHAR(100),
    MOD_DTM                 VARCHAR(14),
    CONSTRAINT BANK_CERT_INFO_PK PRIMARY KEY (BIZ_KEY, SEQ_NO)
);

-- Tenant-scoped lookups by bank (the "credentials for bank X under tenant Y"
-- query the credential-list page runs every load). PostgreSQL collapses to
-- the PK index when BANK_CD isn't filtered, so this is an additive helper.
CREATE INDEX IF NOT EXISTS BANK_CERT_INFO_BIZ_BANK_IX
    ON BANK_CERT_INFO (BIZ_KEY, BANK_CD);
