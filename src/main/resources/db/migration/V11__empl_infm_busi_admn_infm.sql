-- ============================================================================
-- V11__empl_infm_busi_admn_infm.sql
--
-- Phase 1 bootstrap support — installs the EMPL_INFM / BUSI_ADMN_INFM tables
-- when missing, plus the legacy sq_biz_key sequence and wb_get_next_val
-- function that LegacyFunctionRepository (seahorse §2 / crystalline §3.1)
-- delegates to.
--
-- Idempotent. Two real-world starting states must both work:
--
--   a) Fresh DB built only from V1–V10
--      → EMPL_INFM and BUSI_ADMN_INFM do not yet exist. V11 creates them in
--        the minimal Phase 1 shape (FK to BUSI_INFM, audit columns).
--
--   b) DB seeded from a legacy JEX import (current local laptop state)
--      → EMPL_INFM and BUSI_ADMN_INFM already exist with the full legacy
--        column set and rows. V11 leaves them and their data untouched.
--
-- Either way the sequence + function are guaranteed to be present after V11.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. EMPL_INFM — minimal Phase 1 shape; only created on a fresh DB.
--    BIZ_KEY uses VARCHAR(32) to match BUSI_INFM.BIZ_KEY (V1) so the FK is
--    valid in both starting states.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS EMPL_INFM (
    BIZ_KEY                 VARCHAR(32)  NOT NULL,
    EMPL_NO                 VARCHAR(20)  NOT NULL,
    EMPL_NM                 VARCHAR(100),
    JBCL_NM                 VARCHAR(100),
    DEPT_NM                 VARCHAR(100),
    CLPH_NO                 VARCHAR(100),
    EML                     VARCHAR(100),
    CMPN_JOIN_DATE          VARCHAR(8),
    CMPN_FIRE_DATE          VARCHAR(8),
    EMPL_RESI_ST            VARCHAR(200),
    EMPL_RESI_CITY          VARCHAR(200),
    EMPL_RESI_DTL_ADRS      VARCHAR(200),
    EMPL_RESI_POST_ADRS     VARCHAR(200),
    USER_IMG                VARCHAR(500),
    NATN_ID                 VARCHAR(20),
    BANK_CD                 VARCHAR(20),
    ACCT_NO                 VARCHAR(100),
    MAPP_SEUP_YN            VARCHAR(1)   NOT NULL DEFAULT 'N',
    MAPP_NOTI_YN            VARCHAR(1)   NOT NULL DEFAULT 'N',
    PAYROLL_PAGE_REG_YN     VARCHAR(1)   NOT NULL DEFAULT 'N',
    WABOOKS_EMP_MNG_REG_YN  VARCHAR(1)   NOT NULL DEFAULT 'Y',
    TEL_NO                  VARCHAR(100),
    EMPL_NO_AUTO_GEN_YN     VARCHAR(1)   NOT NULL DEFAULT 'N',
    R_EMPL_NO               VARCHAR(50),
    REG_ID                  VARCHAR(100),
    REG_DTM                 TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MOD_ID                  VARCHAR(100),
    MOD_DTM                 TIMESTAMPTZ,
    CONSTRAINT pk_empl_infm PRIMARY KEY (BIZ_KEY, EMPL_NO),
    CONSTRAINT fk_empl_infm_busi FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM (BIZ_KEY)
);

CREATE INDEX IF NOT EXISTS ix_empl_infm_biz_date
    ON EMPL_INFM (BIZ_KEY, LEFT(EMPL_NO, 8));

-- ----------------------------------------------------------------------------
-- 2. BUSI_ADMN_INFM — minimal Phase 1 shape; only created on a fresh DB.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS BUSI_ADMN_INFM (
    BIZ_KEY     VARCHAR(32)  NOT NULL,
    USER_ID     VARCHAR(100) NOT NULL,
    USER_NM     VARCHAR(100) NOT NULL,
    REG_ID      VARCHAR(100),
    REG_DTM     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MOD_ID      VARCHAR(100),
    MOD_DTM     TIMESTAMPTZ,
    CONSTRAINT pk_busi_admn_infm PRIMARY KEY (BIZ_KEY, USER_ID),
    CONSTRAINT fk_busi_admn_infm_busi FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM (BIZ_KEY),
    CONSTRAINT fk_busi_admn_infm_user FOREIGN KEY (USER_ID) REFERENCES USER_INFM (USER_ID)
);

-- ----------------------------------------------------------------------------
-- 3. sq_biz_key — sequence used by wb_get_next_val('busi_infm','biz_key',...).
--    Seeded past the existing V2 / V5 keys so generated values can't collide.
-- ----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS sq_biz_key START WITH 1000 INCREMENT BY 1;

-- ----------------------------------------------------------------------------
-- 4. wb_get_next_val — BIZ_KEY + EMPL_NO branches only.
--    Mirrors the legacy logic verbatim for these two cases (functions.sql
--    lines 5217 + 5600 in the JEX DEV dump). All other table/field combos
--    return NULL on purpose; do NOT call them from the Spring layer.
--    CREATE OR REPLACE makes this re-runnable.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION wb_get_next_val(
    tablename character varying,
    fieldname character varying,
    param1    character varying DEFAULT NULL,
    param2    character varying DEFAULT NULL,
    param3    character varying DEFAULT NULL,
    param4    character varying DEFAULT NULL,
    param5    character varying DEFAULT NULL,
    param6    character varying DEFAULT NULL
) RETURNS text
  LANGUAGE plpgsql
AS $function$
DECLARE
    newSeqNo text := NULL;
BEGIN
    -- BUSI_INFM.BIZ_KEY: {NTNL_CD}_{CHNL_ID}_{NNNNNN}
    IF lower(tablename) = 'busi_infm'
       AND lower(fieldname) = 'biz_key'
       AND coalesce(trim(param1), '') <> ''
       AND coalesce(trim(param2), '') <> '' THEN
        SELECT param1 || '_' || param2 || '_'
            || lpad(cast(coalesce(max(right(BIZ_KEY, 6)::numeric), 0) + 1 AS text), 6, '0')
          INTO newSeqNo
          FROM BUSI_INFM
         WHERE left(BIZ_KEY, length(param1) + length(param2) + 1) = param1 || '_' || param2;

        IF newSeqNo IS NULL THEN
            newSeqNo := param1 || '_' || param2 || '_000001';
        END IF;
        RETURN newSeqNo;
    END IF;

    -- EMPL_INFM.EMPL_NO: YYYYMMDD_NNN per (BIZ_KEY, today)
    IF lower(tablename) = 'empl_infm'
       AND lower(fieldname) = 'empl_no'
       AND coalesce(trim(param1), '') <> '' THEN
        SELECT to_char(timezone('utc', now()), 'YYYYMMDD') || '_'
            || lpad(((count(*) + 1))::text, 3, '0')
          INTO newSeqNo
          FROM EMPL_INFM
         WHERE BIZ_KEY = param1
           AND left(EMPL_NO, 8) = to_char(timezone('utc', now()), 'YYYYMMDD');
        RETURN newSeqNo;
    END IF;

    -- All other branches deliberately unsupported in Phase 1. See
    -- crystalline-honking-sky.md §3.1: per-domain generators land in Phase 3.
    RETURN NULL;
END;
$function$;
