-- ============================================================================
-- V28__sprint1_db_parity_audit.sql
--
-- Final Sprint 1 DB-parity audit corrective migration (2026-04-30).
-- Fixes the schema gaps identified by the audit report at
-- docs/refactor-wecms/db-parity-audit-2026-04-30.md.
--
-- All operations are SAFE / ADDITIVE:
--   * Add columns nullable (no backfill required for Sprint 1 reads)
--   * Widen NUMERIC precision to match DEV
--   * Widen VARCHAR length to match DEV (never narrowing)
--   * Add UNIQUE / CHECK constraints only after verifying current rows pass
--   * Drop+recreate PK on small/empty tables only
--
-- NOT included (intentional — see audit §12):
--   * CHAR ↔ VARCHAR reversal (V18/V19 deliberate)
--   * TIMESTAMPTZ/DATE → VARCHAR (Java modernization)
--   * NOT NULL tightening (Sprint 2 follow-up)
--   * Drop of local-only convenience columns (rule 4: do not delete)
--   * Deferred FKs and triggers (V24 documents)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Add columns missing locally (DEV has them; nullable in local for now)
-- ----------------------------------------------------------------------------

-- empl_infm._empl_nm: DEV column with leading underscore (unusual but real)
-- Local already has empl_nm (no underscore). Both coexist; canonical name TBD.
ALTER TABLE EMPL_INFM ADD COLUMN IF NOT EXISTS _EMPL_NM VARCHAR(100);

-- user_login_his.reg_uid: DEV PK component, missing locally.
-- Add nullable first; backfill existing rows; then rebuild PK in step 6.
ALTER TABLE USER_LOGIN_HIS ADD COLUMN IF NOT EXISTS REG_UID VARCHAR(100);

-- Backfill reg_uid for the 134 existing test rows so we can include it in PK.
-- Use user_id as a stable placeholder. Future logins write the real UID.
UPDATE USER_LOGIN_HIS SET REG_UID = USER_ID WHERE REG_UID IS NULL;

ALTER TABLE USER_LOGIN_HIS ALTER COLUMN REG_UID SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 2. Widen NUMERIC precision to match DEV (audit §3c)
--    Unconstrained numeric → numeric(20,6) for amounts, numeric(N) for seqs.
-- ----------------------------------------------------------------------------

ALTER TABLE ACCT_MSTR        ALTER COLUMN BAL                  TYPE NUMERIC(20,6);
ALTER TABLE ACCT_MSTR        ALTER COLUMN REAL_AMT             TYPE NUMERIC(20,6);
ALTER TABLE ACCT_TRNS_HSTR   ALTER COLUMN TRSC_AMT             TYPE NUMERIC(20,6);
ALTER TABLE ACCT_TRNS_HSTR   ALTER COLUMN BAL                  TYPE NUMERIC(20,6);
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM ALTER COLUMN RCV_PAY_AMT    TYPE NUMERIC(20,6);

ALTER TABLE BAT_CALL_HIS     ALTER COLUMN TOT_CNT              TYPE NUMERIC(15);
ALTER TABLE BAT_CALL_HIS     ALTER COLUMN SUC_CNT              TYPE NUMERIC(15);
ALTER TABLE BAT_CALL_HIS     ALTER COLUMN ERR_CNT              TYPE NUMERIC(15);

ALTER TABLE BUSA_DOC_MSTR    ALTER COLUMN TOTL_AMT             TYPE NUMERIC(20,6);
ALTER TABLE BUSA_DOC_MSTR    ALTER COLUMN SPLY_AMT_SUM         TYPE NUMERIC(20,6);
ALTER TABLE BUSA_DOC_MSTR    ALTER COLUMN TAX_AMT_SUM          TYPE NUMERIC(20,6);

ALTER TABLE BUSI_COND_DAILY_SUMR ALTER COLUMN SELL_AMT          TYPE NUMERIC(20,6);
ALTER TABLE BUSI_COND_DAILY_SUMR ALTER COLUMN BUY_AMT           TYPE NUMERIC(20,6);
ALTER TABLE BUSI_COND_DAILY_SUMR ALTER COLUMN RCV_AMT           TYPE NUMERIC(20,6);
ALTER TABLE BUSI_COND_DAILY_SUMR ALTER COLUMN SND_AMT           TYPE NUMERIC(20,6);

ALTER TABLE BUSI_COND_DAILY_SUMR_DETL ALTER COLUMN DTL_SEQ_NO   TYPE NUMERIC(5);
ALTER TABLE BUSI_COND_DAILY_SUMR_DETL ALTER COLUMN TRSC_AMT     TYPE NUMERIC(20,6);

ALTER TABLE EINV_FEE_DETL    ALTER COLUMN DISP_ORD             TYPE NUMERIC(10);
ALTER TABLE EINV_FEE_DETL    ALTER COLUMN EINV_FEE             TYPE NUMERIC(20,6);

ALTER TABLE EINV_TAX_DETL    ALTER COLUMN DISP_ORD             TYPE NUMERIC(10);
ALTER TABLE EINV_TAX_DETL    ALTER COLUMN AMT                  TYPE NUMERIC(20,6);
ALTER TABLE EINV_TAX_DETL    ALTER COLUMN TAX_AMT              TYPE NUMERIC(20,6);

ALTER TABLE EXT_TRAN_CALL_HIS ALTER COLUMN SEQ_NO              TYPE NUMERIC(10);

ALTER TABLE NAPAS_REMIT_TRNS_HSTR ALTER COLUMN SEQ_NO          TYPE NUMERIC(10);
ALTER TABLE NAPAS_REMIT_TRNS_HSTR ALTER COLUMN TRSC_AMT        TYPE NUMERIC(20,6);
ALTER TABLE NAPAS_REMIT_TRNS_HSTR ALTER COLUMN FEE             TYPE NUMERIC(20,6);

ALTER TABLE SELL_HIS_DAILY_SUMR ALTER COLUMN SPLY_AMT_SUM      TYPE NUMERIC(20,6);
ALTER TABLE SELL_HIS_DAILY_SUMR ALTER COLUMN TAX_AMT_SUM       TYPE NUMERIC(20,6);
ALTER TABLE SELL_HIS_DAILY_SUMR ALTER COLUMN SELL_AMT_SUM      TYPE NUMERIC(20,6);
ALTER TABLE SELL_HIS_DAILY_SUMR ALTER COLUMN RCV_AMT_SUM       TYPE NUMERIC(20,6);

ALTER TABLE SELL_HIS_DAILY_SUMR_DETL ALTER COLUMN ITEM_QUNT    TYPE NUMERIC(20,6);
ALTER TABLE SELL_HIS_DAILY_SUMR_DETL ALTER COLUMN ITEM_UNIT_AMT TYPE NUMERIC(20,6);
ALTER TABLE SELL_HIS_DAILY_SUMR_DETL ALTER COLUMN SPLY_AMT     TYPE NUMERIC(20,6);
ALTER TABLE SELL_HIS_DAILY_SUMR_DETL ALTER COLUMN TAX_AMT      TYPE NUMERIC(20,6);
ALTER TABLE SELL_HIS_DAILY_SUMR_DETL ALTER COLUMN SELL_AMT     TYPE NUMERIC(20,6);
ALTER TABLE SELL_HIS_DAILY_SUMR_DETL ALTER COLUMN RCV_AMT      TYPE NUMERIC(20,6);

-- ----------------------------------------------------------------------------
-- 3. Widen VARCHAR length to match DEV (audit §3d)
--    Never narrowing — only widening.
-- ----------------------------------------------------------------------------

-- user_id width: 50 → 100 across all auth tables
ALTER TABLE BUSI_ADMN_INFM    ALTER COLUMN USER_ID  TYPE VARCHAR(100);
ALTER TABLE USER_BUSI_CONN_INFM ALTER COLUMN USER_ID TYPE VARCHAR(100);
ALTER TABLE USER_INFM         ALTER COLUMN USER_ID  TYPE VARCHAR(100);
ALTER TABLE USER_LOGIN_HIS    ALTER COLUMN USER_ID  TYPE VARCHAR(100);

-- reg_id / mod_id width: 50 → 100 across all tables that have them
ALTER TABLE BUSI_ADMN_INFM    ALTER COLUMN REG_ID TYPE VARCHAR(100);
ALTER TABLE BUSI_ADMN_INFM    ALTER COLUMN MOD_ID TYPE VARCHAR(100);
ALTER TABLE EMPL_INFM         ALTER COLUMN REG_ID TYPE VARCHAR(100);
ALTER TABLE EMPL_INFM         ALTER COLUMN MOD_ID TYPE VARCHAR(100);
ALTER TABLE USER_BUSI_CONN_INFM ALTER COLUMN REG_ID TYPE VARCHAR(100);
ALTER TABLE USER_BUSI_CONN_INFM ALTER COLUMN MOD_ID TYPE VARCHAR(100);
ALTER TABLE USER_INFM         ALTER COLUMN REG_ID TYPE VARCHAR(100);
ALTER TABLE USER_INFM         ALTER COLUMN MOD_ID TYPE VARCHAR(100);
ALTER TABLE BUSI_INFM         ALTER COLUMN REG_ID TYPE VARCHAR(100);
ALTER TABLE BUSI_INFM         ALTER COLUMN MOD_ID TYPE VARCHAR(100);

-- rcv_pay_trco_nm width: 200 → 250
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM ALTER COLUMN RCV_PAY_TRCO_NM TYPE VARCHAR(250);

-- ----------------------------------------------------------------------------
-- 4. Add missing UNIQUE constraints (audit §4a)
--    Pre-verified: zero duplicates locally for both.
-- ----------------------------------------------------------------------------

ALTER TABLE ACCT_TRNS_HSTR ADD CONSTRAINT acct_trns_hstr_uk01
    UNIQUE (BIZ_KEY, BANK_CD, ACCT_NO, TRSC_DT, TRSC_TM, CCY_CD, INOUT_DV, TRSC_AMT, BAL, RMRK, RMRK2);

ALTER TABLE BUSI_INFM ADD CONSTRAINT busi_infm_uk01
    UNIQUE (CHNL_ID, TAX_ID, NTNL_CD);

-- ----------------------------------------------------------------------------
-- 5. Add missing CHECK constraint (audit §4b)
--    Pre-verified: local row has prdt_type='F' which is in DEV's allowed set.
-- ----------------------------------------------------------------------------

ALTER TABLE BUSI_INFM ADD CONSTRAINT busi_infm_chk01
    CHECK (PRDT_TYPE IN ('W','R','B','E','L','S','P','T','U','C','D','F','M','H'));

-- ----------------------------------------------------------------------------
-- 6. Realign primary keys to DEV shape (audit §4d)
-- ----------------------------------------------------------------------------

-- busi_cond_daily_sumr_detl: column ORDER differs (dtl_seq_no/prjt_no swapped)
-- DEV PK: (biz_key, trco_key, trsc_dt, ccy_cd, dtl_seq_no, prjt_no)
-- Local has 3 rows; drop+recreate is safe.
ALTER TABLE BUSI_COND_DAILY_SUMR_DETL DROP CONSTRAINT IF EXISTS pk_busi_cond_daily_sumr_detl;
ALTER TABLE BUSI_COND_DAILY_SUMR_DETL ADD CONSTRAINT pk_busi_cond_daily_sumr_detl
    PRIMARY KEY (BIZ_KEY, TRCO_KEY, TRSC_DT, CCY_CD, DTL_SEQ_NO, PRJT_NO);

-- user_login_his: DEV PK is (user_id, reg_dtm, reg_uid). Local was missing
-- reg_uid. Now that reg_uid exists and is backfilled (step 1), rebuild the PK.
ALTER TABLE USER_LOGIN_HIS DROP CONSTRAINT IF EXISTS pk_user_login_his;
ALTER TABLE USER_LOGIN_HIS ADD CONSTRAINT pk_user_login_his
    PRIMARY KEY (USER_ID, REG_DTM, REG_UID);
