-- ============================================================================
-- V59__firm_banking_history_source_tables.sql
--
-- Settings > Firm Banking History — adds the three source tables the
-- screen consumes that are not yet present locally:
--
--   1. TB_FIRM_TRAN                 — Korean firm-banking gateway audit log
--                                     (SVC_TYPE='F' leg of the unified history view)
--   2. NXSIAS_MODULE_TRAN_LOG_HIS   — NXISAS bridge audit log
--                                     (SVC_TYPE='G' / 'B' legs)
--   3. TB_FIRM_BANK_CODE            — Korean firm-banking bank-code dictionary;
--                                     wb_get_napas_bank_nm(varchar) (V60)
--                                     looks up eng_abrvat_nm here
--
-- The other two source tables are already present:
--   - NAPAS_REMIT_TRNS_HSTR  (V13 — Sprint 1 banking schema)
--   - EXT_TRAN_CALL_HIS      (V16 — Sprint 1 operation status schema)
--
-- Strategy: mirror DEV (wecmsvndev) column-for-column with the
-- documented intentional divergences from
-- docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md §3a (CHAR→VARCHAR,
-- so Hibernate `validate` accepts the columns without `@Type` hacks). The
-- legacy date/time-as-VARCHAR(14|8) shape is preserved so the unified view
-- (V60) can do `WHERE LEFT(TARN_DTM,8) BETWEEN ? AND ?` byte-for-byte
-- with IDO.TB_FIRM_TRAN_R002.xml.
--
-- Idempotent: every CREATE uses IF NOT EXISTS.
--
-- Reference: docs/refactor-wecms/database/settings/firm-banking-history-schema.md
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. TB_FIRM_TRAN — Firm Banking gateway audit log
--    PK matches DEV: (TRAN_DT, BIZ_KEY, TRAN_SEQ).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_FIRM_TRAN (
    TRAN_DT             VARCHAR(8)    NOT NULL,
    BIZ_KEY             VARCHAR(20)   NOT NULL,
    OUT_BANK_CD         VARCHAR(15)   NOT NULL,
    TRAN_SEQ            VARCHAR(10)   NOT NULL,
    BANK_PRTN_ID        VARCHAR(20)   NOT NULL,
    TRAN_TP             VARCHAR(3)    NOT NULL,
    TRAN_TM             VARCHAR(6)    NOT NULL,
    VAN_ORG_CD          VARCHAR(20)   NOT NULL,
    WDRL_ACT_NO         VARCHAR(100),
    TAR_BANK_CD         VARCHAR(15),
    FINC_MDA_DIV        VARCHAR(1),
    FINC_MDA_NO         VARCHAR(100),
    FINC_MDA_OWNER_NM   VARCHAR(150),
    ANT_DIV_CD          VARCHAR(5),
    TRSC_CCY_CD         VARCHAR(3),
    TRAN_AMT            NUMERIC(23,2),
    TRAN_USAG           VARCHAR(200),
    BAL_AMT             NUMERIC(23,2),
    BAL_AMT_SIGN        VARCHAR(1),
    RQ_DTTM             VARCHAR(14),
    RS_DTTM             VARCHAR(14),
    PROC_STAT           VARCHAR(1),
    RS_CD               VARCHAR(4),
    RS_MSG              VARCHAR(200),
    ORG_TRAN_DT         VARCHAR(8),
    ORG_TRAN_SEQ        VARCHAR(50),
    ORG_PROC_TM         VARCHAR(6),
    ORG_RESP_CD         VARCHAR(4),
    ORG_RESP_MSG        VARCHAR(150),
    WDRL_ACCT_RMRK      VARCHAR(150),
    TAR_ACCT_RMRK       VARCHAR(150),
    WITHD_CCY_CD        VARCHAR(3),
    DEP_CCY_CD          VARCHAR(3),
    APL_EXC_RATE        NUMERIC(23,2),
    WITHD_AMT           NUMERIC(23,2),
    DEP_AMT             NUMERIC(23,2),
    CONSTRAINT pk_tb_firm_tran PRIMARY KEY (TRAN_DT, BIZ_KEY, TRAN_SEQ)
);

-- Composite tenant + date access path used by the unified view's BIZ_KEY
-- + date-range predicate. PK already leads with TRAN_DT, but the view
-- filters on BIZ_KEY first and date second; this index supports that
-- access pattern without redundant scanning.
CREATE INDEX IF NOT EXISTS ix_tb_firm_tran_biz_dt
    ON TB_FIRM_TRAN (BIZ_KEY, TRAN_DT);

-- ----------------------------------------------------------------------------
-- 2. NXSIAS_MODULE_TRAN_LOG_HIS — NXISAS / GDT bridge audit log
--    DEV has no PK on this table (verified via pg_indexes 2026-05-08); we
--    declare a synthetic composite PK (BIZ_KEY, REG_DTM, UUID) so JPA can
--    map the entity. This is a refactor-only addition — see
--    docs/refactor-wecms/features/settings/firm-banking-history.md
--    open question 5.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS NXSIAS_MODULE_TRAN_LOG_HIS (
    BIZ_KEY             VARCHAR(20)   NOT NULL,
    REG_DTM             VARCHAR(14)   NOT NULL,
    UUID                VARCHAR(32)   NOT NULL,
    NXISAS_CLASS        VARCHAR(100),
    NXISAS_MODULE       VARCHAR(100),
    NXISAS_JOB          VARCHAR(100),
    NXISAS_TYPE         VARCHAR(100),
    NXISAS_ERR_CODE     VARCHAR(8),
    NXISAS_ERR_CONT     VARCHAR(1000),
    REQ_DATA            VARCHAR(1000),
    REG_ID              VARCHAR(100),
    CONSTRAINT pk_nxsias_module_tran_log_his PRIMARY KEY (BIZ_KEY, REG_DTM, UUID)
);

-- The unified view's GDT/Business legs filter on (BIZ_KEY, NXISAS_TYPE)
-- and (BIZ_KEY, NXISAS_JOB). The composite PK already covers the
-- tenant + chronological scan; partial indexes on NXISAS_TYPE /
-- NXISAS_JOB are deferred until EXPLAIN warrants them at scale.
CREATE INDEX IF NOT EXISTS ix_nxsias_biz_type
    ON NXSIAS_MODULE_TRAN_LOG_HIS (BIZ_KEY, NXISAS_TYPE);

CREATE INDEX IF NOT EXISTS ix_nxsias_biz_job
    ON NXSIAS_MODULE_TRAN_LOG_HIS (BIZ_KEY, NXISAS_JOB);

-- ----------------------------------------------------------------------------
-- 3. TB_FIRM_BANK_CODE — bank-code dictionary used by wb_get_napas_bank_nm
--    PK matches DEV: (BANK_CD).
--
--    The 1-arg wb_get_napas_bank_nm(_bank_cd) function (V60) hardcodes
--    two bank-code → name mappings and falls through to this table for
--    everything else. The full DEV table has 3205 rows; we only seed the
--    rows actually referenced by the configured tenant's TB_FIRM_TRAN
--    history (V61) — additional rows can be added as new bank codes
--    appear in seeded data.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_FIRM_BANK_CODE (
    BANK_CD              VARCHAR(15)   NOT NULL,
    ANT_DIV_CD           VARCHAR(5)    NOT NULL,
    CITAD_BANK_CD        VARCHAR(3),
    BANK_NM              VARCHAR(300),
    BANK_BRANCH_NM       VARCHAR(300),
    ENG_ABRVAT_NM        VARCHAR(300),
    BANK_ENG_NM          VARCHAR(300),
    BANK_BRANCH_ENG_NM   VARCHAR(300),
    BANK_STAS            VARCHAR(1),
    BANK_BRANCH_DIV      VARCHAR(1),
    REG_DTTM             VARCHAR(14)   NOT NULL,
    MOD_DTTM             VARCHAR(14),
    STANDARD_NAME        VARCHAR(200),
    DIRECT_BANK_CODE     VARCHAR(15),
    CONSTRAINT pk_tb_firm_bank_code PRIMARY KEY (BANK_CD)
);
