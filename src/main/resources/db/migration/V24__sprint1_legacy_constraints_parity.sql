-- ============================================================================
-- V24__sprint1_legacy_constraints_parity.sql
--
-- Round 2 of the Sprint 1 schema-parity audit (after V23 added missing
-- columns). This migration adds the remaining legacy constraint set:
-- realigned PRIMARY KEYs, UNIQUE constraints, CHECK constraints, and
-- the FOREIGN KEY constraints whose target tables exist locally.
--
-- Triggers + audit-log FKs that depend on tables NOT yet migrated
-- (cash_repy_mstr, mrec_pic_store, *_log_h, sync_acct_trns_hstr_to_rept)
-- are intentionally DEFERRED and documented in the trailing block.
--
-- Source of truth: pg_constraint / pg_indexes / pg_trigger of
-- wecmsvndev as of 2026-04-29.
-- ============================================================================

-- ============================================================================
-- 1. Align BUSI_INFM.BIZ_KEY VARCHAR(32) → VARCHAR(20) to match legacy.
--    Prerequisite for adding FKs from Sprint 1 tables → BUSI_INFM
--    (Postgres requires identical column types on both sides of an FK).
--    Local has 1 row with max biz_key length = 17 — safe shrink.
-- ============================================================================
ALTER TABLE BUSI_INFM     ALTER COLUMN BIZ_KEY TYPE VARCHAR(20);
ALTER TABLE EMPL_INFM     ALTER COLUMN BIZ_KEY TYPE VARCHAR(20);
ALTER TABLE BUSI_ADMN_INFM ALTER COLUMN BIZ_KEY TYPE VARCHAR(20);
ALTER TABLE USER_BUSI_CONN_INFM ALTER COLUMN BIZ_KEY TYPE VARCHAR(20);

-- ============================================================================
-- 2. Seed cleanup — remove V17 stub rows that violate the legacy CHECK
--    constraints we are about to add. The KRW row was a fake demo entry
--    (DEV's BUSI_COND_DAILY_SUMR is empty for tenant VN_VNM0004_000001;
--    legacy chk01 only allows VND/USD).
-- ============================================================================
DELETE FROM BUSI_COND_DAILY_SUMR WHERE BIZ_KEY='VN_VNM0004_000001' AND CCY_CD='KRW';

-- ============================================================================
-- 3. Re-align PRIMARY KEYs to legacy shape on the 5 tables where the
--    V13 / V14 / V15 / V20 PK didn't match DEV.
--    All 5 tables are empty or hold only Sprint 1 stub data — safe to
--    drop+recreate.
-- ============================================================================

-- bank_infm: legacy PK = (bank_cd, start_date, end_date) — supports
-- historical bank registration tracking. start_date / end_date are
-- already NOT NULL.
ALTER TABLE BANK_INFM DROP CONSTRAINT IF EXISTS pk_bank_infm;
ALTER TABLE BANK_INFM ADD CONSTRAINT pk_bank_infm
    PRIMARY KEY (BANK_CD, START_DATE, END_DATE);

-- bat_call_his: legacy PK adds REQ_DATE + MOD_UID to the prior 4-col PK.
ALTER TABLE BAT_CALL_HIS DROP CONSTRAINT IF EXISTS pk_bat_call_his;
ALTER TABLE BAT_CALL_HIS ADD CONSTRAINT pk_bat_call_his
    PRIMARY KEY (JOB_ID, BIZ_KEY, JOB_DATE, JOB_STR_TM, REQ_DATE, MOD_UID);

-- acct_trns_rcv_pay_evid_file: legacy PK uses FILE_ID, not FILE_SEQ_NO.
-- FILE_ID was added by V23 as nullable; need NOT NULL on PK columns.
UPDATE ACCT_TRNS_RCV_PAY_EVID_FILE SET FILE_ID = COALESCE(FILE_ID, FILE_SEQ_NO::text) WHERE FILE_ID IS NULL;
ALTER TABLE ACCT_TRNS_RCV_PAY_EVID_FILE ALTER COLUMN FILE_ID SET NOT NULL;
ALTER TABLE ACCT_TRNS_RCV_PAY_EVID_FILE DROP CONSTRAINT IF EXISTS pk_acct_trns_rcv_pay_evid_file;
ALTER TABLE ACCT_TRNS_RCV_PAY_EVID_FILE ADD CONSTRAINT pk_acct_trns_rcv_pay_evid_file
    PRIMARY KEY (BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO, FILE_ID);

-- sell_his_daily_sumr: legacy PK adds TRCO_CMNM + TRCO_CMENM.
ALTER TABLE SELL_HIS_DAILY_SUMR DROP CONSTRAINT IF EXISTS pk_sell_his_daily_sumr;
ALTER TABLE SELL_HIS_DAILY_SUMR ADD CONSTRAINT pk_sell_his_daily_sumr
    PRIMARY KEY (BIZ_KEY, TRSC_DT, TRSC_TYPE, TRCO_KEY, TRCO_CMNM, TRCO_CMENM, PRJT_NO, CCY_CD);

-- sell_his_daily_sumr_detl: legacy PK adds TRCO_CMNM + TRCO_CMENM + ITEM_NM.
ALTER TABLE SELL_HIS_DAILY_SUMR_DETL DROP CONSTRAINT IF EXISTS pk_sell_his_daily_sumr_detl;
ALTER TABLE SELL_HIS_DAILY_SUMR_DETL ADD CONSTRAINT pk_sell_his_daily_sumr_detl
    PRIMARY KEY (BIZ_KEY, TRSC_DT, TRSC_TYPE, TRCO_KEY, TRCO_CMNM, TRCO_CMENM, ITEM_CD, ITEM_NM, CCY_CD, PRJT_NO);

-- ============================================================================
-- 4. UNIQUE constraints from DEV.
--    busa_doc_mstr_uk01: rows where reg_dt or seq_no is NULL are treated
--    as distinct in PostgreSQL UNIQUE — so the existing seed (which has
--    reg_dt / seq_no NULL on all rows) does not violate this constraint.
-- ============================================================================
ALTER TABLE BUSA_DOC_MSTR ADD CONSTRAINT busa_doc_mstr_uk01
    UNIQUE (BIZ_KEY, REG_DT, SEQ_NO);

-- ============================================================================
-- 5. CHECK constraints from DEV. Each is verified against the live local
--    seed before being added — none should fail at this point.
-- ============================================================================

-- ACCT_MSTR
ALTER TABLE ACCT_MSTR ADD CONSTRAINT acct_mstr_chk01
    CHECK (ACCT_TYPE_CD = ANY (ARRAY['01','02','03','04','05','06','07','08']));
ALTER TABLE ACCT_MSTR ADD CONSTRAINT acct_mstr_chk02
    CHECK (CASE
            WHEN ACCT_TYPE_CD = '01' THEN ACCT_DV_CD = ANY (ARRAY['3','4'])
            WHEN ACCT_TYPE_CD = '02' THEN ACCT_DV_CD = ANY (ARRAY['1','2'])
            ELSE NULL
           END);
ALTER TABLE ACCT_MSTR ADD CONSTRAINT acct_mstr_chk03
    CHECK (HAND_REG_YN = ANY (ARRAY['Y','N']));
ALTER TABLE ACCT_MSTR ADD CONSTRAINT acct_mstr_chk05
    CHECK (ACTV_YN = ANY (ARRAY['Y','N']));
ALTER TABLE ACCT_MSTR ADD CONSTRAINT acct_mstr_chk06
    CHECK (CCY_CD = ANY (ARRAY['VND','USD','KRW']));

-- ACCT_TRNS_HSTR
ALTER TABLE ACCT_TRNS_HSTR ADD CONSTRAINT acct_trns_hstr_chk01
    CHECK (INOUT_DV = ANY (ARRAY['0','1','2']));
-- DEV's chk02 limits CCY_CD to (VND, USD) — local seed (real DEV data)
-- only has VND + USD, so this is safe to add.
ALTER TABLE ACCT_TRNS_HSTR ADD CONSTRAINT acct_trns_hstr_chk02
    CHECK (CCY_CD = ANY (ARRAY['VND','USD']));

-- BUSA_DOC_MSTR
ALTER TABLE BUSA_DOC_MSTR ADD CONSTRAINT busa_doc_mstr_chk01
    CHECK (DOC_TYPE = ANY (ARRAY['I','B','E']));
ALTER TABLE BUSA_DOC_MSTR ADD CONSTRAINT busa_doc_mstr_chk02
    CHECK (PAY_DUE_CD = ANY (ARRAY['15','30','60','00']));
ALTER TABLE BUSA_DOC_MSTR ADD CONSTRAINT busa_doc_mstr_chk07
    CHECK (TRCO_TRSC_CCY_CD = ANY (ARRAY['VND','USD','999','998']));

-- BUSI_COND_DAILY_SUMR (KRW row already deleted in step 2)
ALTER TABLE BUSI_COND_DAILY_SUMR ADD CONSTRAINT busi_cond_daily_sumr_chk01
    CHECK (CCY_CD = ANY (ARRAY['VND','USD']));
ALTER TABLE BUSI_COND_DAILY_SUMR_DETL ADD CONSTRAINT busi_cond_daily_sumr_detl_chk01
    CHECK (CCY_CD = ANY (ARRAY['VND','USD']));

-- SELL_HIS_DAILY_SUMR (table is empty locally — chk01 trivially passes)
ALTER TABLE SELL_HIS_DAILY_SUMR ADD CONSTRAINT sell_his_daily_sumr_chk01
    CHECK (CCY_CD = ANY (ARRAY['VND','USD']));

-- ============================================================================
-- 6. FOREIGN KEYs from DEV — added only where the target table exists
--    locally and all rows pass the FK check.
-- ============================================================================

-- acct_mstr → busi_infm. Now that BUSI_INFM.BIZ_KEY was shrunk to
-- VARCHAR(20) in step 1, the column types match.
ALTER TABLE ACCT_MSTR ADD CONSTRAINT acct_mstr_fk01
    FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM (BIZ_KEY);

-- busi_cond_daily_sumr → busi_infm
ALTER TABLE BUSI_COND_DAILY_SUMR ADD CONSTRAINT busi_cond_daily_sumr_fk01
    FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM (BIZ_KEY);

-- sell_his_daily_sumr → busi_infm
ALTER TABLE SELL_HIS_DAILY_SUMR ADD CONSTRAINT sell_his_daily_sumr_fk01
    FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM (BIZ_KEY);

-- acct_trns_rcv_pay_infm → acct_trns_hstr (DEV's fk01).
-- All 15 rcv_pay rows in V22 seed reference acct_seq_no values present
-- in V22's acct_trns_hstr seed; FK passes.
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM ADD CONSTRAINT acct_trns_rcv_pay_infm_fk01
    FOREIGN KEY (BIZ_KEY, ACCT_SEQ_NO) REFERENCES ACCT_TRNS_HSTR (BIZ_KEY, ACCT_SEQ_NO);

-- acct_trns_rcv_pay_evid_file → acct_trns_rcv_pay_infm. Table is empty
-- locally so FK trivially passes; constraint protects future writes.
ALTER TABLE ACCT_TRNS_RCV_PAY_EVID_FILE ADD CONSTRAINT acct_trns_rcv_pay_evid_file_fk01
    FOREIGN KEY (BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO)
    REFERENCES ACCT_TRNS_RCV_PAY_INFM (BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO);

-- ============================================================================
-- DEFERRED — documented for Sprint 2+
-- ============================================================================
-- The following legacy constraints / triggers are NOT added by Sprint 1
-- because their dependencies are out of scope. They will be added in the
-- sprint that ports the dependency.
--
-- 1. acct_trns_rcv_pay_infm_fk02 — references CASH_REPY_MSTR(biz_key,
--    trsc_dt, cash_repy_seq_no). CASH_REPY_MSTR is a cash-receipt master
--    table not in Sprint 1 scope. Defer to Sprint 2 cash module.
--
-- 2. busa_doc_mstr_fk04 — references MREC_PIC_STORE(biz_key, reg_dt,
--    seq_no). MREC_PIC_STORE is the picture/upload registry; not in
--    Sprint 1 scope. Defer.
--
-- 3. Triggers (28 total across the 17 tables):
--    - {table}_logtrigger — AFTER INSERT/UPDATE/DELETE → calls audit-log
--      function {table}_log() which writes to {table}_log_h history.
--      The _log() functions and _log_h tables are NOT in Sprint 1 scope.
--    - acct_mstr_before_insert_or_update → reject_not_existing_bank_cd().
--      Function not migrated. The legacy bank-cd FK behaviour is
--      partially covered by BANK_INFM existing locally; explicit FK
--      from ACCT_MSTR.BANK_CD → BANK_INFM.BANK_CD is also DEFERRED
--      because legacy DEV's PK on BANK_INFM is composite (BANK_CD,
--      START_DATE, END_DATE) — a single-column FK to BANK_CD requires
--      a UNIQUE constraint on BANK_CD alone, which DEV doesn't have.
--    - sync_to_rept_trigger on acct_trns_hstr → sync function not
--      migrated; sync target REPT_TRNS_HSTR exists in DEV but is not
--      in Sprint 1 scope.
--
--    Sprint 1 endpoints are READ-ONLY, so these triggers would only fire
--    on Flyway seed inserts — and those use ON CONFLICT DO NOTHING so a
--    second run is a no-op. Deferring the triggers is safe for Sprint 1;
--    Sprint 2+ writes will need them.
-- ============================================================================
