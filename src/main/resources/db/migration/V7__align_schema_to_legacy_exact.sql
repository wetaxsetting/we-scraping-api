-- =======================================================================
-- V7__align_schema_to_legacy_exact.sql
--
-- Directive (2026-04-22): local schema must mirror WeCMS DEV exactly —
--   * table names match legacy
--   * column names match legacy (no renames, no removals)
--   * types may be adjusted (VARCHAR(14) timestamps → TIMESTAMPTZ, etc.)
--   * BIZ_KEY is a first-class column on every relevant table
--   * direct data copy from wecmsvndevdb must be possible without mapping tricks
--
-- This migration:
--   1. renames my invented audit columns (REG_USER, MOD_USER) → legacy names
--      (REG_ID, MOD_ID)
--   2. drops LANG_CD from USER_INFM (legacy never had it — LANG_CD is derived
--      from the Accept-Language header at request time)
--   3. renames BUSI_INFM.BUSI_NM → CMPN_NM to match legacy
--   4. adds every legacy column we were missing, with the legacy name
--   5. keeps all new columns NULLABLE so the existing R&D rows (V2, V5) stay
--      valid and future COPY-from-legacy works even when a value is absent
--
-- =======================================================================

-- -----------------------------------------------------------------------
-- 1. Rename audit columns to match legacy (REG_ID / MOD_ID)
-- -----------------------------------------------------------------------
ALTER TABLE BUSI_INFM            RENAME COLUMN REG_USER TO REG_ID;
ALTER TABLE BUSI_INFM            RENAME COLUMN MOD_USER TO MOD_ID;
ALTER TABLE USER_INFM            RENAME COLUMN REG_USER TO REG_ID;
ALTER TABLE USER_INFM            RENAME COLUMN MOD_USER TO MOD_ID;
ALTER TABLE USER_BUSI_CONN_INFM  RENAME COLUMN REG_USER TO REG_ID;
ALTER TABLE USER_BUSI_CONN_INFM  RENAME COLUMN MOD_USER TO MOD_ID;

-- -----------------------------------------------------------------------
-- 2. Drop LANG_CD from USER_INFM (not a legacy column)
-- -----------------------------------------------------------------------
ALTER TABLE USER_INFM DROP COLUMN LANG_CD;

-- -----------------------------------------------------------------------
-- 3. Rename BUSI_NM → CMPN_NM on BUSI_INFM
-- -----------------------------------------------------------------------
ALTER TABLE BUSI_INFM RENAME COLUMN BUSI_NM TO CMPN_NM;

-- -----------------------------------------------------------------------
-- 4. Add missing USER_INFM columns (legacy has 20 cols; we had 12)
--    BIZ_KEY on USER_INFM preserved from legacy for multi-tenant context.
-- -----------------------------------------------------------------------
ALTER TABLE USER_INFM ADD COLUMN CLPH_NO           VARCHAR(100);
ALTER TABLE USER_INFM ADD COLUMN USER_IMG          VARCHAR(500);
ALTER TABLE USER_INFM ADD COLUMN LST_LGN_DATE      TIMESTAMPTZ;
ALTER TABLE USER_INFM ADD COLUMN MOB_LST_LGN_DATE  TIMESTAMPTZ;
ALTER TABLE USER_INFM ADD COLUMN PW_CHG_REQ_SEQ_NO NUMERIC;
ALTER TABLE USER_INFM ADD COLUMN BIZ_KEY           VARCHAR(20);
ALTER TABLE USER_INFM ADD COLUMN EMPL_NO           VARCHAR(20);
ALTER TABLE USER_INFM ADD COLUMN DEPT_NM           VARCHAR(100);
ALTER TABLE USER_INFM ADD COLUMN POSI_NM           VARCHAR(100);

-- -----------------------------------------------------------------------
-- 5. Add missing USER_BUSI_CONN_INFM columns
-- -----------------------------------------------------------------------
ALTER TABLE USER_BUSI_CONN_INFM ADD COLUMN USER_IMG          VARCHAR(500);
ALTER TABLE USER_BUSI_CONN_INFM ADD COLUMN MOB_LST_LGN_DATE  TIMESTAMPTZ;

-- -----------------------------------------------------------------------
-- 6. Add missing BUSI_INFM columns (legacy has 45 cols; we had 8)
--    Types:
--      VARCHAR(14) timestamps  → TIMESTAMPTZ
--      VARCHAR(8)  dates       → DATE
--      large/unbounded text    → TEXT
-- -----------------------------------------------------------------------
ALTER TABLE BUSI_INFM ADD COLUMN CMPN_ENM             VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN TAX_ID               VARCHAR(40);
ALTER TABLE BUSI_INFM ADD COLUMN RPPR_NM              VARCHAR(100);
ALTER TABLE BUSI_INFM ADD COLUMN TEL_NO               VARCHAR(30);
ALTER TABLE BUSI_INFM ADD COLUMN FXNO                 VARCHAR(30);
ALTER TABLE BUSI_INFM ADD COLUMN EML                  VARCHAR(100);
ALTER TABLE BUSI_INFM ADD COLUMN RPRS_BSUN_POST_ADRS  VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN RPRS_BSUN_DTL_ADRS   VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN RPRS_BSUN_CITY       VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN RPRS_BSUN_ST         VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN CMPN_LOGO            VARCHAR(500);
ALTER TABLE BUSI_INFM ADD COLUMN JOIN_DT              TIMESTAMPTZ;
ALTER TABLE BUSI_INFM ADD COLUMN FST_JOIN_NM          VARCHAR(100);
ALTER TABLE BUSI_INFM ADD COLUMN FST_JOIN_TEL         VARCHAR(100);
ALTER TABLE BUSI_INFM ADD COLUMN FST_USE_DATE         TIMESTAMPTZ;
ALTER TABLE BUSI_INFM ADD COLUMN LST_PAY_DATE         DATE;
ALTER TABLE BUSI_INFM ADD COLUMN USE_DATE_FROM        DATE;
ALTER TABLE BUSI_INFM ADD COLUMN USE_DATE_TO          DATE;
ALTER TABLE BUSI_INFM ADD COLUMN FREE_USE_DATE_FROM   DATE;
ALTER TABLE BUSI_INFM ADD COLUMN FREE_USE_DATE_TO     DATE;
ALTER TABLE BUSI_INFM ADD COLUMN BANK_CD              VARCHAR(20);
ALTER TABLE BUSI_INFM ADD COLUMN ACCT_NO              VARCHAR(100);
ALTER TABLE BUSI_INFM ADD COLUMN CHNL_ID              VARCHAR(10);
ALTER TABLE BUSI_INFM ADD COLUMN OVERDUE_YN           VARCHAR(1);
ALTER TABLE BUSI_INFM ADD COLUMN CANC_DATE            DATE;
ALTER TABLE BUSI_INFM ADD COLUMN WIZ_RUN_YN           CHAR(1);
ALTER TABLE BUSI_INFM ADD COLUMN CMPN_STAMP           TEXT;
ALTER TABLE BUSI_INFM ADD COLUMN BUSI_NICK_NM         VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN ALLI_CMPN_CD         VARCHAR(20);
ALTER TABLE BUSI_INFM ADD COLUMN ALLI_CMPN_NM         VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN PRDT_TYPE            VARCHAR(1);
ALTER TABLE BUSI_INFM ADD COLUMN OWAC_NM              VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN MEMO                 VARCHAR(200);
ALTER TABLE BUSI_INFM ADD COLUMN PAID_START_DATE      DATE;
ALTER TABLE BUSI_INFM ADD COLUMN CMPN_QR_CD_IMG       VARCHAR(500);
ALTER TABLE BUSI_INFM ADD COLUMN ERP_CD               CHAR(1);
