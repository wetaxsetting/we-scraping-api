-- WeCMS Manual Database Change
-- Date: 2026-06-11
-- Source: migrated from Claude Code (AI)/sql/acct_mstr_trigger_deps_20260611.sql
-- Related record: TBD
-- Type: DDL
-- Execution: MANUAL
-- Target environments: Refactor DEV DB (172.168.5.172/wecmsdevdb)
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: pre/post-check queries are present in the original script below
-- MIGRATION REVIEW REQUIRED — this script creates a new table and/or DB function that has no counterpart in src/main/resources/db/migration/. No migration was created and no V-number was assigned during the archive migration; the placement decision is open.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- acct_mstr_trigger_deps_20260611.sql
-- =============================================================================
-- Purpose : Restore the missing dependency closure of the legacy daily-cash-report
--           maintenance triggers on the REFACTOR DEV DB (172.168.5.172/wecmsdevdb).
--
-- Symptom : POST /api/v1/banking/accounts/register (AccountRegistrationService)
--           fails with:
--             ERROR: function rept_0900_001_rec1(character varying, text, text) does not exist
--             Where: PL/pgSQL function acct_mstr_log() line 189
--
-- Root cause:
--   The legacy AFTER-write triggers were copied onto the refactor DB, but their
--   function + table dependency closure was NOT. A single INSERT into acct_mstr
--   fires this cascade (all objects below are present on the live JEX DB):
--
--     INSERT acct_mstr
--       -> trg acct_mstr_logtrigger -> acct_mstr_log()
--            INSERT daily_cash_rept_daily_sumr_detl  FROM rept_0900_001_rec1(...)   [MISSING fn]
--       -> that INSERT fires daily_cash_rept_daily_sumr_detl_logtrigger
--            -> rept_0900_001_rec2(...) -> rept_0900_001_rec1(...)                  [MISSING fn]
--            -> rept_0900_001_rec4(...) -> rept_0900_001_rec3(...)                  [MISSING fn]
--                 -> reads table cash_repy_mstr                                    [MISSING table]
--            INSERT daily_cash_rept_daily_sumr                                     [MISSING table]
--
--   rept_0900_001_rec1/rec3 also call wb_get_amt(...)                              [MISSING fn]
--
-- Objects this script creates (verbatim from live JEX DDL = source of truth):
--   FUNCTIONS : wb_get_amt, rept_0900_001_rec1, rept_0900_001_rec2,
--               rept_0900_001_rec3, rept_0900_001_rec4
--   TABLES    : cash_repy_mstr, daily_cash_rept_daily_sumr
--
-- Already present on refactor DB (NOT created here):
--   fn wb_get_bank_nm; tables acct_mstr, acct_trns_hstr,
--   daily_cash_rept_daily_sumr_detl, busi_infm
--
-- BASELINE NOTE (REQUIRES PO / Mr. Jung APPROVAL BEFORE RUNNING):
--   cash_repy_mstr and daily_cash_rept_daily_sumr are NOT in the approved
--   141-table legacy-table-inventory.md. Running this script ADDS two tables to
--   the refactor baseline. Get explicit PO sign-off first, then have the inventory
--   updated to match.
--
-- FK NOTE:
--   On JEX, cash_repy_mstr carries 3 FKs: fk01->busi_infm, fk02->adv_paym_mstr,
--   fk03->adv_paym_trns_hstr. On the refactor DB busi_infm exists (fk01 kept) but
--   adv_paym_mstr / adv_paym_trns_hstr do NOT (also off-inventory). fk02/fk03 are
--   therefore OMITTED. They are irrelevant to this fix: the report trigger chain
--   only READS cash_repy_mstr (never inserts), so no FK is exercised.
--
-- Execution:
--   * Run MANUALLY by a DBA against 172.168.5.172/wecmsdevdb. NOT a Flyway migration
--     (Flyway disabled + ddl-auto=validate in all wecms-api profiles).
--   * Idempotent: CREATE OR REPLACE for functions, CREATE TABLE IF NOT EXISTS for tables.
--   * Leaves all existing legacy rows untouched (creates empty tables only).
--   * Run order is top-to-bottom; objects are ordered so dependencies exist first.
--   * Adjust the schema in the SET search_path line below if the deployment schema
--     differs (the legacy triggers live in schema "wecmsdevdb").
--
-- Author  : Claude Code (AI)  |  Date: 2026-06-11
-- =============================================================================

\set ON_ERROR_STOP on
BEGIN;

-- Match the schema that owns acct_mstr_log() and the legacy triggers.
SET search_path TO wecmsdevdb;

-- -----------------------------------------------------------------------------
-- 1) Helper function: wb_get_amt  (currency-aware rounding; used by rec1, rec3)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION wb_get_amt(_amt numeric, _ccy_cd character varying)
 RETURNS numeric
 LANGUAGE plpgsql
AS $function$
BEGIN
    if _ccy_cd = 'VND' then
        return round(_amt, 0);
    else
        return round(_amt, 2);
    end if;
END;
$function$;

-- -----------------------------------------------------------------------------
-- 2) Table: cash_repy_mstr   (read by rept_0900_001_rec3)
--    PK + CHECK kept; fk01 (busi_infm) kept; fk02/fk03 omitted (targets absent).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cash_repy_mstr (
    biz_key            character varying(20)  NOT NULL,
    trsc_dt            character varying(8)   NOT NULL,
    cash_repy_seq_no   numeric(10,0)          NOT NULL,
    inout_dv           character(1)           NOT NULL,
    trsc_amt           numeric(20,6)          NOT NULL DEFAULT 0,
    use_usag_cd        character varying(20),
    ccy_cd             character(3)           NOT NULL,
    trco_key           character varying(20),
    trco_nm            character varying(200),
    prjt_no            character varying(20),
    cont               character varying(200),
    reg_id             character varying(100) NOT NULL,
    reg_dtm            character varying(14)  NOT NULL,
    mod_id             character varying(100) NOT NULL,
    mod_dtm            character varying(14)  NOT NULL,
    trco_enm           character varying(200),
    adv_paym_seq       numeric(10,0),
    adv_paym_trns_seq  numeric(10,0),
    hand_reg_yn        character varying(1)   DEFAULT 'N'::character varying,
    attach_doc_id      character varying(50),
    CONSTRAINT cash_repy_mstr_pk PRIMARY KEY (biz_key, trsc_dt, cash_repy_seq_no),
    CONSTRAINT cash_repy_mstr_chk01 CHECK (inout_dv = ANY (ARRAY['1'::bpchar, '2'::bpchar])),
    CONSTRAINT cash_repy_mstr_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key)
    -- fk02 FOREIGN KEY (biz_key, adv_paym_seq)      REFERENCES adv_paym_mstr(biz_key, adv_paym_seq)        -- OMITTED: target table absent on refactor DB
    -- fk03 FOREIGN KEY (biz_key, adv_paym_trns_seq) REFERENCES adv_paym_trns_hstr(biz_key, adv_paym_trns_seq) -- OMITTED: target table absent on refactor DB
);

-- -----------------------------------------------------------------------------
-- 3) Table: daily_cash_rept_daily_sumr   (written by daily_cash_rept_daily_sumr_detl_log)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_cash_rept_daily_sumr (
    biz_key         character varying(20)  NOT NULL,
    ref_date        character varying(8)   NOT NULL,
    ccy_cd          character(3)           NOT NULL,
    totl_norm_amt   numeric(20,6)          NOT NULL DEFAULT 0,
    totl_cash       numeric(20,6)          NOT NULL DEFAULT 0,
    totl_acct_amt   numeric(20,6)          NOT NULL DEFAULT 0,
    totl_loan_amt   numeric(20,6)          NOT NULL DEFAULT 0,
    totl_sav_amt    numeric(20,6)          NOT NULL DEFAULT 0,
    lst_coll_date   character varying(14),
    CONSTRAINT daily_cash_rept_daily_sumr_pk PRIMARY KEY (biz_key, ref_date, ccy_cd)
);

-- -----------------------------------------------------------------------------
-- 4) rept_0900_001_rec1  (account-detail rows; called by acct_mstr_log, acct_trns_hstr_log, rec2)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION rept_0900_001_rec1(p_biz_key character varying, p_req_date character varying, p_req_date2 character varying)
 RETURNS TABLE(biz_key character varying, ref_date character varying, ccy_cd character, acct_type_cd character, bank_cd character varying, bank_nm character varying, acct_no character varying, bal numeric, loan_nm character varying, expi_dt character varying, agmt_amt numeric, snd_cnt numeric, snd_amt numeric, rcv_cnt numeric, rcv_amt numeric)
 LANGUAGE plpgsql
AS $function$
#variable_conflict use_column             -- how to resolve conflicts
BEGIN
    RETURN QUERY (

    select
        t.BIZ_KEY
    ,    t.REF_DATE
    ,    t.CCY_CD
    ,    t.ACCT_TYPE_CD
    ,    t.BANK_CD
    ,   wb_get_bank_nm(t.BANK_CD, 's') BANK_NM
    ,    t.ACCT_NO
    ,    coalesce(t.hstr_bal, t.mstr_bal) as bal
    ,    t.LOAN_NM
    ,    t.EXPI_DT
    ,    coalesce(t.AGMT_AMT, 0) as agmt_amt
    ,    coalesce(t.SND_CNT::numeric, 0) as snd_cnt
    ,    coalesce(t.SND_AMT, 0) as snd_amt
    ,    coalesce(t.RCV_CNT::numeric, 0) as rcv_cnt
    ,    coalesce(t.RCV_AMT, 0) as rcv_amt
    from (
        -- 일일시재보고서(계좌상세)
        select mstr.biz_key
             , p_req_date ref_date
             , mstr.ccy_cd
             , mstr.acct_type_cd
             , mstr.bank_cd
             , mstr.acct_no
             , wb_get_amt(mstr.bal, mstr.ccy_cd) as mstr_bal
             , (select wb_get_amt(bal, ccy_cd) from acct_trns_hstr where trsc_dt <= p_req_date and biz_key = mstr.biz_key and bank_cd = mstr.bank_cd and acct_no = mstr.acct_no and ccy_cd = mstr.ccy_cd order by trsc_dt desc, cast(acct_seq_no as numeric) desc limit 1) as hstr_bal
             , mstr.loan_nm
             , mstr.expi_dt
             , wb_get_amt(mstr.agmt_amt, mstr.ccy_cd) as agmt_amt
             , hstr.snd_cnt snd_cnt
             , wb_get_amt(hstr.snd_amt, mstr.ccy_cd) as snd_amt
             , hstr.rcv_cnt rcv_cnt
             , wb_get_amt(hstr.rcv_amt, mstr.ccy_cd) as rcv_amt
          from acct_mstr mstr
          left outer join (
               select biz_key
                , ccy_cd
                , bank_cd
                , acct_no
                , sum((case when inout_dv = '2' then 1 else 0 end)) snd_cnt
                , sum((case when inout_dv = '2' then wb_get_amt(trsc_amt, ccy_cd) else 0 end)) snd_amt
                , sum((case when inout_dv = '1' then 1 else 0 end)) rcv_cnt
                , sum((case when inout_dv = '1' then wb_get_amt(trsc_amt, ccy_cd) else 0 end)) rcv_amt
             from acct_trns_hstr
            where trsc_dt = p_req_date
              and biz_key = p_biz_key
            group by biz_key, ccy_cd, bank_cd, acct_no
               ) hstr
            on mstr.biz_key = hstr.biz_key
           and mstr.ccy_cd = hstr.ccy_cd
           and mstr.bank_cd = hstr.bank_cd
           and mstr.acct_no = hstr.acct_no
           --and mstr.acct_type_cd in  ('03','04')
           and mstr.acct_type_cd in ('03', '04', '05', '06')
         where mstr.biz_key = p_biz_key
         -- https://www.kosign.com.kh/redmine/issues/40201
         and mstr.actv_yn = 'Y'
         --https://www.kosign.com.kh/redmine/issues/38437
         and  (case when mstr.acct_type_cd = '01' then mstr.expi_dt  >=  p_req_date else 1=1 end )

         order by mstr.acct_type_cd,mstr.bank_cd,mstr.acct_no
    ) t

    );
END;
$function$;

-- -----------------------------------------------------------------------------
-- 5) rept_0900_001_rec3  (cash-detail rows; reads cash_repy_mstr; called by rec4)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION rept_0900_001_rec3(p_biz_key character varying, p_req_date character varying, p_req_date2 character varying)
 RETURNS TABLE(biz_key character varying, ref_date character varying, ccy_cd character, bef_date_bal numeric, bal numeric, snd_cnt numeric, snd_amt numeric, rcv_cnt numeric, rcv_amt numeric)
 LANGUAGE plpgsql
AS $function$
#variable_conflict use_column             -- how to resolve conflicts
BEGIN
    RETURN QUERY (

	select
		t.biz_key
	,	t.ref_date
	,	t.ccy_cd
	,	t.bef_date_bal
	,	t.bal
	,	t.snd_cnt::numeric
	,	t.snd_amt
	,	t.rcv_cnt::numeric
	,	t.rcv_amt
	from (
		select grp.biz_key
		     , grp.trsc_dt ref_date
		     , grp.ccy_cd
		     , coalesce((select sum(case when inout_dv = '2' then -1 * trsc_amt else trsc_amt end)
			  from cash_repy_mstr
				 where biz_key = grp.biz_key
				   and trsc_dt < grp.trsc_dt
				   and ccy_cd = grp.ccy_cd
		       ), 0) bef_date_bal
		     , coalesce((select sum(case when inout_dv = '2' then -1 * trsc_amt else trsc_amt end)
			  from cash_repy_mstr
				 where biz_key = grp.biz_key
				   and trsc_dt <= grp.trsc_dt
				   and ccy_cd = grp.ccy_cd
		       ), 0) bal
		     , coalesce(summ.snd_cnt, 0) snd_cnt
		     , coalesce(summ.snd_amt, 0) snd_amt
		     , coalesce(summ.rcv_cnt, 0) rcv_cnt
		     , coalesce(summ.rcv_amt, 0) rcv_amt
		  from (
				select p_biz_key biz_key
				     , p_req_date trsc_dt
				     , ccy_cd
				  from cash_repy_mstr
				 group by ccy_cd
			   ) grp
		  left outer join (
				select mstr.biz_key
				     , mstr.trsc_dt
				     , mstr.ccy_cd
				     , sum(case when mstr.inout_dv = '2' then 1 else 0 end) snd_cnt
				     , sum(case when mstr.inout_dv = '2' then trsc_amt else 0 end) snd_amt
				     , sum(case when mstr.inout_dv = '1' then 1 else 0 end) rcv_cnt
				     , sum(case when mstr.inout_dv = '1' then trsc_amt else 0 end) rcv_amt
				  from cash_repy_mstr mstr
				 where mstr.biz_key = p_biz_key
				   and mstr.trsc_dt = p_req_date
				 group by biz_key, trsc_dt, mstr.ccy_cd
				) summ
		       on grp.biz_key = summ.biz_key
			  and grp.trsc_dt = summ.trsc_dt
			  and grp.ccy_cd = summ.ccy_cd
		 order by grp.ccy_cd
	) t

    );
END;
$function$;

-- -----------------------------------------------------------------------------
-- 6) rept_0900_001_rec2  (currency totals; wraps rec1; called by daily_cash_rept_daily_sumr_detl_log)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION rept_0900_001_rec2(p_biz_key character varying, p_req_date character varying, p_req_date2 character varying)
 RETURNS TABLE(biz_key character varying, ref_date character varying, ccy_cd character, totl_norm_amt numeric, totl_sav_amt numeric, totl_acct_amt numeric, totl_loan_amt numeric)
 LANGUAGE plpgsql
AS $function$
#variable_conflict use_column             -- how to resolve conflicts
BEGIN
    RETURN QUERY (

	select
		t.BIZ_KEY
	,	t.REF_DATE
	,	t.CCY_CD
	,	t.totl_norm_amt
	,   t.totl_sav_amt
	,	t.totl_acct_amt
	,	t.totl_loan_amt
	from (
		select org.biz_key
		     , org.ref_date
		     , org.ccy_cd
		     , sum(case when acct_type_cd in ('03','04') then bal else 0 end) totl_norm_amt
		     , sum(case when acct_type_cd in ('05', '06') then bal else 0 end) totl_sav_amt
		     , sum(case when acct_type_cd = '02' then bal else 0 end) totl_acct_amt
		     , sum(case when acct_type_cd = '01' then bal else 0 end) totl_loan_amt
		  from (
		-- 일일시재보고서(계좌상세)
		select * from rept_0900_001_rec1(p_biz_key, p_req_date, p_req_date2)
		-- 일일시재보고서(계좌상세) 끝
		 ) org
		 group by org.biz_key, org.ref_date, org.ccy_cd

		 order by org.ref_date,org.ccy_cd
	) t

    );
END;
$function$;

-- -----------------------------------------------------------------------------
-- 7) rept_0900_001_rec4  (cash totals; wraps rec3; called by daily_cash_rept_daily_sumr_detl_log)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION rept_0900_001_rec4(p_biz_key character varying, p_req_date character varying, p_req_date2 character varying)
 RETURNS TABLE(biz_key character varying, ref_date character varying, ccy_cd character, totl_cash numeric)
 LANGUAGE plpgsql
AS $function$
#variable_conflict use_column             -- how to resolve conflicts
BEGIN
    RETURN QUERY (

		select
			t.biz_key
		,	t.ref_date
		,	t.ccy_cd
		,	t.totl_cash
		from (
			select org.biz_key
			     , org.ref_date
			     , org.ccy_cd
			     , sum(org.bal) totl_cash
			  from (
			-- 일일시제보고서(현금상세)
			select * from rept_0900_001_rec3(p_biz_key, p_req_date, p_req_date2)
			-- 일일시제보고서(현금상세) 끝
			 ) org
			group by org.biz_key, org.ref_date, org.ccy_cd
			order by org.ref_date,org.ccy_cd
		) t

    );
END;
$function$;

COMMIT;

-- =============================================================================
-- POST-RUN VERIFICATION (run as separate read-only checks):
--   SELECT proname, pg_get_function_identity_arguments(oid)
--     FROM pg_proc WHERE proname IN
--     ('wb_get_amt','rept_0900_001_rec1','rept_0900_001_rec2','rept_0900_001_rec3','rept_0900_001_rec4');
--   -- expect 5 rows
--   SELECT to_regclass('wecmsdevdb.cash_repy_mstr'), to_regclass('wecmsdevdb.daily_cash_rept_daily_sumr');
--   -- expect both non-null
--   -- Then re-run POST /api/v1/banking/accounts/register; the 500 WBE_DB should clear.
-- =============================================================================
