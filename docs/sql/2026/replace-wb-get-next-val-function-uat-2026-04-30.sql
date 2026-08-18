-- WeCMS Manual Database Change
-- Date: 2026-04-30
-- Source: migrated from Claude Code (AI)/sql/wb_get_next_val_fix_busi_usag_set_infm_20260430.sql
-- Related record: TBD
-- Type: DDL
-- Execution: MANUAL
-- Target environments: UAT — the function is schema-qualified wecmsuat_real in the script body
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — restoring the prior body requires the pre-change function definition
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
-- SQL REVIEW REQUIRED — this script replaces a live legacy DB function in place
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- DROP FUNCTION wecmsuat_real.wb_get_next_val(varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar);

CREATE OR REPLACE FUNCTION wecmsuat_real.wb_get_next_val(tablename character varying, fieldname character varying, param1 character varying DEFAULT NULL::character varying, param2 character varying DEFAULT NULL::character varying, param3 character varying DEFAULT NULL::character varying, param4 character varying DEFAULT NULL::character varying, param5 character varying DEFAULT NULL::character varying, param6 character varying DEFAULT NULL::character varying)
 RETURNS text
 LANGUAGE plpgsql
AS $function$
DECLARE
   newSeqNo text := null;
   bizKey text := null;
   userId text := null;
   grpNm text := null;
   docType text := null;
   apprSeqNo text := null;
   docIdPrefix text := '';
   docIdDateFormat text := 'YYYYMMDD';
   docIdSeqLen integer := 3;
   docIdFactor integer := 0;
   docId text := null;
   docNoPrefix text := '';
   docNoDateFormat text := 'YYMMDD';
   docNoSeqLen integer := 3;
   docNoFactor integer := 0;
   acctNo text := null;
   trscDt text := null;
   acctSeqNo text := null;
   bankCd text := null;
   rcvPaySeqNo integer := null;
   cashCheqSeqNo text := null;
   salRegSeqNo text:= null;
   trcoKey text:= null;
   salDtlSeqNo integer:= 0;
   forwDt text:= null;
   rcvPayDv text:= null;
   ccyCd text:= null;
   detlInfmSeq text:= null;
   regDt text:= null;
   ntnlCd text:= null;
   chnlId text:= null;
   lstCollDate text:= null;
    --rowCnt integer := 0;
BEGIN
    case 
    when lower($1) = 'cash_cheq_mstr' and lower($2) = 'cash_cheq_seq_no' and coalesce(trim($3), '') <> ''  and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (select nextval('sq_cash_cheq'));
    when lower($1) = 'cash_repy_mstr' and lower($2) = 'cash_repy_seq_no' and coalesce(trim($3), '') <> ''  and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (select nextval('sq_cash_repy'));
    when lower($1) = 'trco_rspb_infm' and lower($2) = 'rspb_key' then
        newSeqNo :=  (select nextval('sq_rspb_key'));
    when lower($1) = 'trco_actv_hstr' and lower($2) = 'actv_seq' then
        newSeqNo :=  (select nextval('sq_actv_seq'));
    when lower($1) = 'cstm_ldgr_infm' and lower($2) = 'biz_key' then
        newSeqNo :=  (select nextval('sq_biz_key'));
    when lower($1) = 'trco_infm' and lower($2) = 'trco_key' then
        newSeqNo :=  (select nextval('sq_trco_key'));
    when lower($1) = 'expt_resol_mstr' and lower($2) = 'appr_seq_no' then
        newSeqNo :=  (select nextval('sq_appr_seq_no'));

	-- S: https://www.kosign.com.kh/redmine/issues/40779
	when lower($1) = 'acct_trns_vas_actv_his' and lower($2) = 'acct_trns_vas_actv_seq' then
        newSeqNo :=  (select nextval('sq_acct_trns_vas_actv_seq'));
	-- E: https://www.kosign.com.kh/redmine/issues/40779

	-- S: https://www.kosign.com.kh/redmine/issues/40618
	-- tran_seq
	when lower($1) = 'tb_pay_target' and lower($2) = 'tran_seq' then
        newSeqNo :=  (select nextval('firm_seq_no'));
	-- tran_seq
	when lower($1) = 'tb_firm_tran' and lower($2) = 'tran_seq' then
        newSeqNo :=  (select nextval('firm_seq_no'));
	-- file_seq_no
	when lower($1) = 'tb_pay_file_mst' and lower($2) = 'file_seq_no' then
        newSeqNo :=  (select nextval('firm_file_seq_no'));
	-- not sure file_seq_no
	when lower($1) = 'tb_pay_file_data' and lower($2) = 'file_seq_no' then
        newSeqNo :=  (select nextval('firm_file_seq_no'));
	
	-- seq
	when lower($1) = 'tb_pay_file_data' and lower($2) = 'seq' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
	    newSeqNo := (
	        select coalesce(max(seq::int), 0) + 1 from tb_pay_file_data
	        where 1=1 
	        and biz_key = $3
	        and file_seq_no = $4 
    );

	-- trco_key
	when lower($1) = 'trco_addr_infm' and lower($2) = 'trco_key' then
        newSeqNo :=  (select nextval('sq_trco_key'));
 
       
	-- trco_addr_key
	when lower($1) = 'trco_addr_infm' and lower($2) = 'trco_addr_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(trco_addr_key::numeric), 0) + 1 from trco_addr_infm 
		        where 1=1
				and biz_key  = $3
				and trco_key = $4
		);
	-- trco_bsun_post_adrs
	when lower($1) = 'trco_addr_infm' and lower($2) = 'trco_bsun_post_adrs' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(trco_bsun_post_adrs::numeric), 0) + 1 from trco_addr_infm 
		        where 1=1
				and biz_key  = $3
				and trco_key = $4
		);
	-- seq_no
	when lower($1) = 'erp_trns_hstr' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(seq_no::numeric), 0) + 1 from erp_trns_hstr 
		        where 1=1
				and biz_key  = $3
		);
	when lower($1) = 'scdul_pymn_sumr' and lower($2) = 'scdul_pymn_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(scdul_pymn_no::numeric), 0) + 1 from scdul_pymn_sumr 
		        where 1=1
				and biz_key  = $3
		);
	when lower($1) = 'scdul_pymn_sumr_detl' and lower($2) = 'scdul_pymn_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(scdul_pymn_no::numeric), 0) + 1 from scdul_pymn_sumr_detl 
		        where 1=1
				and biz_key  = $3
		);
	when lower($1) = 'scdul_pymn_sumr_detl' and lower($2) = 'scdul_pymn_detl_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(scdul_pymn_detl_no::numeric), 0) + 1 from scdul_pymn_sumr_detl 
		        where 1=1
				and biz_key  = $3
		);
	-- E: https://www.kosign.com.kh/redmine/issues/40618

       --http://www.kosign.com.kh/redmine/issues/24282#note-8
       --https://www.kosign.com.kh/redmine/issues/39573
    when lower($1) = 'expt_resol_mstr' and lower($2) = 'doc_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
       
       -- docNoPrefix := 'VO';
       select coalesce((
			select besi.env_set_cont from busi_env_set_infm besi
			where besi.biz_key = $3
			and besi.env_set_inf = sesi.env_set_inf
			and exists (
				select 1 from busi_env_set_infm
				where biz_key = besi.biz_key 
				and env_set_inf = '00070'
				and env_set_cont = 'Y'
			)
		), sesi.env_set_cont) into docNoPrefix 
		from sys_env_set_infm sesi
		where sesi.env_set_inf = '00071';
	
	   -- docNoDateFormat := 'YYMMDD';
       select coalesce((
			select besi.env_set_cont from busi_env_set_infm besi
			where besi.biz_key = $3
			and besi.env_set_inf = sesi.env_set_inf
			and exists (
				select 1 from busi_env_set_infm
				where biz_key = besi.biz_key 
				and env_set_inf = '00070'
				and env_set_cont = 'Y'
			)
		), sesi.env_set_cont) into docNoDateFormat 
		from sys_env_set_infm sesi
		where sesi.env_set_inf = '00072';
	
		-- docNoSeqLen := '001';
       select length (coalesce((
			select besi.env_set_cont from busi_env_set_infm besi
			where besi.biz_key = $3
			and besi.env_set_inf = sesi.env_set_inf
			and exists (
				select 1 from busi_env_set_infm
				where biz_key = besi.biz_key 
				and env_set_inf = '00070'
				and env_set_cont = 'Y'
			)
		), sesi.env_set_cont)) into docNoSeqLen 
		from sys_env_set_infm sesi
		where sesi.env_set_inf = '00073';
		
		docNoFactor := length(docNoPrefix) + length(docNoDateFormat);
       
        newSeqNo :=  (
	        select docNoPrefix || '_' ||to_char(timezone('utc'::text, now()), docNoDateFormat::text) || '_' || lpad(cast(coalesce(max(right(SPLIT_PART(doc_no, '_', ARRAY_LENGTH(string_to_array(doc_no, '_'), 1)), docNoSeqLen)::numeric), 0) + 1 as text) ,docNoSeqLen,'0') from expt_resol_mstr
			where 1=1
			and biz_key = bizKey
			and del_yn ='N'
			and left(doc_no, docNoFactor+1) = docNoPrefix||'_'||to_char(timezone('utc'::text, now()), docNoDateFormat::text)
		); 
    when lower($1) = 'busi_txtn_ptrn_set_infm' and lower($2) = 'txtn_ptrn' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
        
        newSeqNo :=  (
	        select coalesce(max(txtn_ptrn::numeric), 10) + 1 from busi_txtn_ptrn_set_infm
			where 1=1
			and biz_key = bizKey
		);   
    when lower($1) = 'empl_infm' and lower($2) = 'empl_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
        
        newSeqNo :=  (
	        select to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || '_' || lpad((count(*)+1)::text ,3,'0') from empl_infm
			where 1=1
			and biz_key = bizKey
			and left(empl_no, 8) = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
		);
	when lower($1) = 'ext_tran_call_his' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		/*
        WITH upsert AS (UPDATE ext_tran_seq_no_infm SET seq_no = seq_no + 1 WHERE svc_id = $3 and ref_date = $4 RETURNING *) 
		INSERT INTO ext_tran_seq_no_infm (svc_id, ref_date, seq_no) SELECT $3, $4, 1 WHERE NOT EXISTS (SELECT * FROM upsert);
		select max(seq_no) into newSeqNo from ext_tran_seq_no_infm where svc_id = $3 and ref_date = $4;
		*/
		newSeqNo :=  (
	        select coalesce(max(seq_no::numeric), 0) + 1 from ext_tran_call_his
			where 1=1
			and svc_id = $3
			and ref_date = $4
		);
	when lower($1) = 'saly_book_mstr' and lower($2) = 'sal_reg_seq_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
    
        newSeqNo :=  (
	        select coalesce(max(sal_reg_seq_no::numeric), 0) + 1 from saly_book_mstr
			where 1=1
			and biz_key = bizKey
		);
	when lower($1) = 'snd_serv_sell_buy_detl' and lower($2) = 'dtl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(dtl_seq_no), 0) + 1 from snd_serv_sell_buy_detl
			where 1=1
			and biz_key = $3
			and reg_seq_no = cast($4 as numeric)
		);
	when lower($1) = 'snd_serv_in_out_detl' and lower($2) = 'dtl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(dtl_seq_no), 0) + 1 from snd_serv_in_out_detl
			where 1=1
			and biz_key = $3
			and reg_seq_no = cast($4 as numeric)
		);
	when lower($1) = 'snd_serv_mstr' and lower($2) = 'reg_seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(reg_seq_no), 0) + 1 from snd_serv_mstr
			where 1=1
			and biz_key = $3
		);
	when lower($1) = 'snd_serv_etc_evid_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(file_id::numeric), 0) + 1 from snd_serv_etc_evid_file
			where 1=1
			and biz_key = $3
			and reg_seq_no = cast($4 as numeric)
		);
	when lower($1) = 'snd_serv_mail_send_hstr' and lower($2) = 'hist_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(hist_seq_no), 0) + 1 from snd_serv_mail_send_hstr
			where 1=1
			and biz_key = $3
			and reg_seq_no = cast($4 as numeric)
		);
	when lower($1) = 'snd_serv_mail_send_hstr_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(file_id::numeric), 0) + 1 from snd_serv_mail_send_hstr_file
			where 1=1
			and biz_key = $3
			and reg_seq_no = cast($4 as numeric)
			and hist_seq_no = cast($5 as numeric)
		);
	when lower($1) = 'saly_book_detl' and lower($2) = 'sal_detl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        bizKey := $3;
    	salRegSeqNo := $4;
    
        newSeqNo :=  (
	        select coalesce(max(sal_dtl_seq_no::numeric), 0) + 1 from saly_book_detl
			where 1=1
			and biz_key = bizKey
			and sal_reg_seq_no = salRegSeqNo
		);
	when lower($1) = 'smrt_asst_dtls' and lower($2) = 'dtl_reg_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(dtl_reg_seq_no), 0) + 1 from smrt_asst_dtls
			where 1=1
			and biz_key = $3
			and reg_dt=$4
			and reg_seq_no = cast($5 as numeric)
		);
	when lower($1) = 'smrt_asst_ptcl' and lower($2) = 'reg_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(reg_seq_no), 0) + 1 from smrt_asst_ptcl
			where 1=1
			and biz_key = $3
			and reg_dt = $4
		);
	when lower($1) = 'smrt_asst_recr_infm' and lower($2) = 'reg_seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(reg_seq_no), 0) + 1 from smrt_asst_recr_infm
			where 1=1
			and biz_key = $3
		);
	when lower($1) = 'saly_book_detl_inf' and lower($2) = 'sal_item_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        bizKey := $3;
    	salRegSeqNo := $4;
    	salDtlSeqNo := $5;
    
        newSeqNo :=  (
	        select coalesce(max(sal_item_seq_no::numeric), 0) + 1 from saly_book_detl_inf
			where 1=1
			and biz_key = bizKey
			and sal_reg_seq_no = salRegSeqNo
			and sal_dtl_seq_no = salDtlSeqNo
		);
    when lower($1) = 'forw_set_rcv_pay_his' and lower($2) = 'forw_rcv_pay_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' and coalesce(trim($6), '') <> '' then
        bizKey := $3;
        rcvPayDv := $4;
    	trcoKey := $5;
    	ccyCd := $6;
    
        newSeqNo :=  (
	        select coalesce(max(forw_rcv_pay_seq_no::numeric), 0) + 1 from forw_set_rcv_pay_his
			where 1=1
			and biz_key = bizKey
			and rcv_pay_dv = rcvPayDv
			and trco_key = trcoKey
			and ccy_cd = ccyCd
		);
    when lower($1) = 'busi_cond_daily_sumr_detl' and lower($2) = 'dtl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' and coalesce(trim($6), '') <> '' and coalesce(trim($7), '') <> '' then
        bizKey := $3;
        lstCollDate := $4;
    	trcoKey := $5;
        trscDt := $6;
    	ccyCd := $7;
    
        newSeqNo :=  (
	        select coalesce(max(dtl_seq_no::numeric), 0) + 1 from busi_cond_daily_sumr_detl
			where 1=1
			and biz_key = bizKey
			and lst_coll_date = lstCollDate
			and trco_key = trcoKey
			and trsc_dt = trscDt
			and ccy_cd = ccyCd
		);
		
	when lower($1) = 'trco_grp_user' and lower($2) = 'grp_cd' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	bizKey := $3;
    	userId := $4;
    	grpNm  := $5;   
    	
    	if(coalesce(trim($5), '') <> '') then 
	        newSeqNo :=  (
			      select coalesce(count(grp_cd::numeric), 0) from trco_grp_user
				where 1=1
				and biz_key = bizKey
				and user_id = userId
				and grp_cd not in ('S', 'B', 'F', 'E')
				and grp_nm ilike '%' || grpNm || '%'
			);
		else 
	    	newSeqNo :=  (
		         select coalesce(max(grp_cd::numeric), 0) + 1 from trco_grp_user
				where 1=1
				and biz_key = bizKey
				and user_id = userId
				and grp_cd not in ('S', 'B', 'F', 'E')
			);
		end if;
    	
	when lower($1) = 'push_notice_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no::numeric), 0) + 1 from push_notice_infm
			where 1=1
			and biz_key = $3
			and user_id = $4
		);
	when lower($1) = 'msg_snd_his' and lower($2) = 'reg_seq_no' /*and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> ''*/ then
        newSeqNo :=  (
	        select coalesce(max(reg_seq_no::numeric), 0) + 1 from msg_snd_his
	/*		where 1=1
			and biz_key = $3
			and user_id = $4
			and msg_type=$5*/
		);
    	/*
   		grpNm  := $5;
        if(coalesce(trim($5), '') <> '') then 
	        newSeqNo :=  (
			      select coalesce(max(grp_cd::numeric), 0) + 1 from trco_grp_user
				where 1=1
				and biz_key = bizKey
				and user_id = userId
				and grp_nm  ILIKE grpNm || '%'
			);
	        
		else 
			newSeqNo :=  (
		         select coalesce(max(grp_cd::numeric), 0) + 1 from trco_grp_user
				where 1=1
				and biz_key = bizKey
				and user_id = userId
			);
		end if;
		*/
		
    when lower($1) = 'item_infm' and lower($2) = 'item_cd' and coalesce(trim($3), '') <> '' then
    	bizKey := $3;

		newSeqNo :=  (
	        -- select  lpad((coalesce(max(item_cd::numeric), 0) + 1)::text,6,'0') from item_infm
	        --select  (coalesce(max(item_cd::numeric), 0) + 1)::text from item_infm
			select case when max(item_cd::numeric) > 9999999999 then (coalesce(max(item_cd::numeric), 0) + 1)::text else lpad((coalesce(max(item_cd::numeric), 0) + 1)::text, 10,'0') end
			from item_infm
			where biz_key = bizKey
			and item_cd ~ '^[0-9]+$'
		);
    when lower($1) = 'noti_category_infm' and lower($2) = 'category_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
	    	select coalesce(max(category_id), 0) + 1 from noti_category_infm
			where 1=1
			and blbr_id = $3
            and lang_code = $4
		);
    when lower($1) = 'noti_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
	         select coalesce(max(file_id::numeric), 0) + 1 from noti_file
			where 1=1
			and blbr_id = $3
            and reg_seq_no = cast($4 as numeric)
		);
    when lower($1) = 'prjt_infm' and lower($2) = 'prjt_no' and coalesce(trim($3), '') <> '' then
    	bizKey := $3;

		newSeqNo :=  (
	       --  select lpad((coalesce(max(prjt_no::numeric), 0) + 1)::text,6,'0') from prjt_infm
	        -- select (coalesce(max(prjt_no::numeric), 0) + 1)::text from prjt_infm
	        select case when max(prjt_no::numeric) > 9999999999 then (coalesce(max(prjt_no::numeric), 0) + 1)::text else lpad((coalesce(max(prjt_no::numeric), 0) + 1)::text, 10,'0') end
	        from prjt_infm
			where biz_key = bizKey
			and prjt_no ~ '^[0-9]+$'
		);
		
    when lower($1) = 'trco_grp_user' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	bizKey := $3;
    	userId := $4;

		newSeqNo :=  (
	         select coalesce(max(disp_ord::numeric), 0) + 1 from trco_grp_user
			where 1=1
			and biz_key = bizKey
			and user_id = userId
		);
    when lower($1) = 'user_scr_option' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then

		newSeqNo :=  (
	         select coalesce(max(disp_ord), 0) + 1 from user_scr_option
			where 1=1
			and biz_key = $3
			and user_id = $4
			and web_svc_id = $5
		);
		
    when lower($1) = 'trco_info_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' then
    	trcoKey := $3;
    
        newSeqNo :=  (
	        select coalesce(max(file_id::numeric), 0) + 1 from trco_info_file
			where 1=1
			and trco_key = trcoKey
		);
    when lower($1) = 'busa_doc_rcv_pay_his' and lower($2) = 'rcv_pay_his_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	
        newSeqNo :=  (
	        select coalesce(max(rcv_pay_his_seq_no), 0) + 1 from busa_doc_rcv_pay_his
			where 1=1
			and biz_key = $3
			and doc_id = $4
		);
    when lower($1) = 'cash_repy_evid_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(file_id::numeric), 0) + 1 from cash_repy_evid_file
			where 1=1
			and biz_key = $3
			and trsc_dt = $4
			and cash_repy_seq_no = cast($5 as numeric)
		);
    when lower($1) = 'cash_cheq_evid_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(file_id::numeric), 0) + 1 from cash_cheq_evid_file
			where 1=1
			and biz_key = $3
			and issu_dt = $4
			and cash_cheq_seq_no = cast($5 as numeric)
		);
    when lower($1) = 'acct_trns_hstr' and lower($2) = 'acct_seq_no' and coalesce(trim($3), '') <> '' then
    	bizKey := $3;
  
    
        newSeqNo :=  (
	        select coalesce(max(acct_seq_no::numeric), 0) + 1 from acct_trns_hstr
			where 1=1
			and biz_key = bizKey
		);
    when lower($1) = 'trco_acct_info' and lower($2) = 'trco_acct_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	bizKey := $3;
    	trcoKey := $4;
    
        newSeqNo :=  (
	        select coalesce(max(trco_acct_key::numeric), 0) + 1 from trco_acct_info
			where 1=1
			and biz_key = bizKey
			and trco_key = trcoKey
		);
    when lower($1) = 'trco_acct_info' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	bizKey := $3;
    	trcoKey := $4;
    
        newSeqNo :=  (
	        select coalesce(max(disp_ord::numeric), 0) + 1 from trco_acct_info
			where 1=1
			and biz_key = bizKey
			and trco_key = trcoKey
		);
    when lower($1) = 'acct_trns_rcv_pay_infm' and lower($2) = 'rcv_pay_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> ''   then
    	bizKey := $3;
        acctSeqNo := $4;
    	
	    --newSeqNo := '1';
		newSeqNo :=  (
	        select coalesce(max(rcv_pay_seq_no), 0) + 1 from acct_trns_rcv_pay_infm
			where 1=1
			and biz_key = bizKey
			and acct_seq_no = acctSeqNo
		);
		       
    when lower($1) = 'acct_trns_rcv_pay_evid_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
    	bizKey := $3;
        acctSeqNo := $4;
        rcvPaySeqNo := $5;
    	
    
        newSeqNo :=  (
	        select coalesce(max(file_id::numeric), 0) + 1 from acct_trns_rcv_pay_evid_file
			where 1=1
			and biz_key = bizKey
			and acct_seq_no = acctSeqNo
			and rcv_pay_seq_no = rcvPaySeqNo
		);
    when lower($1) = 'expt_resol_detl' and lower($2) = 'appr_detl_seq_no' and coalesce(trim($3), '') <> '' then
    	--bizKey := $3;
    	apprSeqNo := $4;
    
        newSeqNo :=  (
	        select coalesce(max(appr_detl_seq_no), 0) + 1 from expt_resol_detl
			where 1=1
			--and biz_key = bizKey
			and appr_seq_no = apprSeqNo
		);
	when lower($1) = 'expt_resol_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' then
    	bizKey := $3;
    	apprSeqNo := $4;
    
        newSeqNo :=  (
	        select coalesce(max(file_id::numeric), 0) + 1 from expt_resol_file
			where 1=1
			and biz_key = bizKey
			and appr_seq_no = apprSeqNo
		);
	when lower($1) = 'noti_infm' and lower($2) = 'reg_seq_no' then
        newSeqNo :=  (
	        select coalesce(max(reg_seq_no), 0) + 1 from noti_infm
		);
	when lower($1) = 'busi_infm' and lower($2) = 'biz_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
     -- ntnlCd := $3;
     -- chnlId := $4;
      newSeqNo :=(
	       /*select $3||'_'|| $4 ||'_'||lpad((count(*)+1)::text ,6,'0') from busi_infm*/
      		select $3||'_'|| $4 ||'_'||lpad(cast(coalesce(max(right(BIZ_KEY, 6)::numeric), 0) + 1 as text), 6, '0') from busi_infm
      		where  left(biz_key,10) = $3||'_'|| $4
	  );
    when lower($1) = 'busa_doc_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(count(*), 0) + 1 from busa_doc_detl
			where biz_key = $3
			and doc_id = $4
		);
    when lower($1) = 'trsc_adm_doc_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	
        newSeqNo :=  (
	        select coalesce(max(DISP_ORD),0) + 1 from trsc_adm_doc_detl
			where biz_key = $3
			and doc_id = $4
		);
    when lower($1) = 'pgro_bsac_lnkn_infm' and lower($2) = 'grp_cd' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	bizKey := $3;
    	userId := $4;
    
        newSeqNo :=  (
	        select userId || '-' || coalesce(count(*), 0) + 1 from pgro_bsac_lnkn_infm
			where biz_key = bizKey
			and user_id = userId
		);
    when lower($1) = 'trsc_adm_doc_mstr' and lower($2) = 'doc_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	docType = $4;
    
        newSeqNo :=  (
        	select case docType when 'P' then 'PO' else docType end || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)|| lpad(cast(coalesce(max(right(doc_id, 3)::numeric), 0) + 1 as text), 3, '0')
        	from trsc_adm_doc_mstr
			where biz_key = $3
			and doc_type = docType
			and left(reg_dtm, 8) = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
		);

	when lower($1) = 'trsc_adm_doc_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	
        newSeqNo :=  (
	        select coalesce(max(file_id::numeric),0) + 1 from trsc_adm_doc_file
			where biz_key = $3
			and doc_id = $4
		);
	
	when lower($1) = 'busi_detl_infm' and lower($2) = 'detl_infm_seq' and coalesce(trim($3), '') <> '' then
    	bizKey := $3;
    
        newSeqNo :=  (
	        select coalesce(max(detl_infm_seq::numeric), 0) + 1 from busi_detl_infm
			where 1=1
			and biz_key = bizKey
		);
	
	when lower($1) = 'user_pw_chg_his' and lower($2) = 'reg_seq_no' and coalesce(trim($3), '') <> '' then
    	newSeqNo :=  (
	        select coalesce(max(reg_seq_no), 0) + 1 from user_pw_chg_his
			where 1=1
			and user_id = $3
		);
	when lower($1) = 'user_usag_use_his' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from user_usag_use_his
			where 1=1
			and biz_key = $3
			and user_id = $4
		);
	
	when lower($1) = 'user_trco_use_his' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from user_trco_use_his
			where 1=1
			and biz_key = $3
			and user_id = $4
		);
	
	when lower($1) = 'bank_cert_info' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
    	newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from bank_cert_info
			where 1=1
			and biz_key = $3
	);
    when lower($1) = 'acct_mstr' and lower($2) = 'prn_seq' and coalesce(trim($3), '') <> '' then
    			bizKey := $3;
    			--bankCd := $4;
    			--acctNo := $5;
    			newSeqNo :=  (
	        		select coalesce(max(prn_seq), 0) + 1 from acct_mstr
					where 1=1
					and biz_Key = $3
					--and bank_cd = $4
					--and acct_no = $5
	           );
	          
	when  lower($1) = 'mrec_pic_store' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from mrec_pic_store
	        where biz_key = $3
	        and reg_dt    = $4
		);
	
	when  lower($1) = 'mrec_pic_store_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
	
		newSeqNo :=  (
	        select coalesce(max(file_id::numeric), 0) + 1 from mrec_pic_store_file
	        where biz_key = $3
	        and reg_dt = $4
	        and seq_no = cast($5 as numeric)
		);
	
	when lower($1) = 'user_point_chrg_ord_his' and lower($2) = 'reg_seq_no' then
    	newSeqNo :=  (
	        select coalesce(max(reg_seq_no), 0) + 1 from user_point_chrg_ord_his
		);
	when lower($1) = 'user_point_use_his' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from user_point_use_his
			where 1=1
			and user_id = $3
			and trsc_dt = $4
		);
	
	when lower($1) = 'noti_category_infm' and lower($2) = 'category_id' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(category_id), 0) + 1 from noti_category_infm
			where 1=1
			and lang_code = $3
	);
--add on (21/10)---
    when lower($1) = 'moc_srch_trco_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from moc_srch_trco_infm
			where 1=1
			and entityid = $3
		);
	
	 when lower($1) = 'moc_srch_trco_empl_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from moc_srch_trco_empl_infm
			where 1=1
			and entityid = $3
	);
	
	
	when lower($1) = 'moc_srch_trco_empl_infm' and lower($2) = 'dtl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(dtl_seq_no), 0) + 1 from moc_srch_trco_empl_infm
			where 1=1
			and entityid = $3
			and seq_no = cast($4 as numeric)
	);




	when lower($1) = 'moc_srch_trco_phaddr_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
	        newSeqNo :=  (
		        select coalesce(max(seq_no), 0) + 1 from moc_srch_trco_phaddr_infm
				where 1=1
				and entityid = $3
		);
		
	
	when lower($1) = 'moc_srch_trco_phaddr_infm' and lower($2) = 'dtl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(dtl_seq_no), 0) + 1 from moc_srch_trco_phaddr_infm
			where 1=1
			and entityid = $3
			and seq_no = cast($4 as numeric)
	);



	when lower($1) = 'moc_srch_trco_poaddr_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
	        newSeqNo :=  (
		        select coalesce(max(seq_no), 0) + 1 from moc_srch_trco_poaddr_infm
				where 1=1
				and entityid = $3
		);
		
	
	when lower($1) = 'moc_srch_trco_poaddr_infm' and lower($2) = 'dtl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(dtl_seq_no), 0) + 1 from moc_srch_trco_poaddr_infm
			where 1=1
			and entityid = $3
			and seq_no = cast($4 as numeric)
	);


   	when lower($1) = 'moc_srch_trco_direc_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
	        newSeqNo :=  (
		        select coalesce(max(seq_no), 0) + 1 from moc_srch_trco_direc_infm
				where 1=1
				and entityid = $3
		);
		
	
	when lower($1) = 'moc_srch_trco_direc_infm' and lower($2) = 'dtl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(dtl_seq_no), 0) + 1 from moc_srch_trco_direc_infm
			where 1=1
			and entityid = $3
			and seq_no = cast($4 as numeric)
	);


   	when lower($1) = 'moc_srch_trco_actv_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
	        newSeqNo :=  (
		        select coalesce(max(seq_no), 0) + 1 from moc_srch_trco_actv_infm
				where 1=1
				and entityid = $3
		);
		
	
	when lower($1) = 'moc_srch_trco_actv_infm' and lower($2) = 'dtl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(dtl_seq_no), 0) + 1 from moc_srch_trco_actv_infm
			where 1=1
			and entityid = $3
			and seq_no = cast($4 as numeric)
	);

	
	when lower($1) = 'efiling_sumr_file' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from efiling_sumr_file
			where 1=1
			and biz_key = $3
			and doc_type = $4
	);


	
---------------------	
-- add on (17/11)

when lower($1) = 'sys_doc_id_gen' and lower($2) = 'doc_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	bizKey := $3;
    	docType := $4;
    
    	if docType = 'E' then

    	newSeqNo :=  (
		        select 'R' || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || lpad(cast(coalesce(max(right(doc_id, 3)::numeric), 0) + 1 as text), 3, '0') from sys_doc_id_gen
				where biz_key = bizKey
				and doc_type = $4
				and req_date = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
			);
    	
    		/*
	        newSeqNo :=  (
				select cast(coalesce(max(right(doc_id, 3)::numeric), 0) + 1 as text) from busa_doc_mstr
				where biz_key = bizKey
				and doc_type = $4
			);
			*/
    
    	/*else
	    	case docType 
	    	when 'P' then 
	    		docIdPrefix := 'PO';
	    	when 'Q' then 
	    		docIdPrefix := 'Q'; 
	    	when 'I' then 
	    		docIdPrefix := 'IV';
	    	
	    	when 'B' then 
	    		docIdPrefix := 'BL';
	    	
	    	
	    	end case;
	    
	        newSeqNo :=  (
		        select docIdPrefix || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || lpad(cast(coalesce(max(right(doc_id, 3)::numeric), 0) + 1 as text), 3, '0') from sys_doc_id_gen
				where biz_key = bizKey
				and doc_type = $4
				and req_date = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
			);
			
			end if;*/
		
		
    	
    	
    	else
		    case docType 
		    	when 'P' then 
		    		docIdPrefix := 'PO-';
		    	when 'Q' then 
		    		docIdPrefix := 'Q-'; 
		    	when 'I' then 
		    		select coalesce((
						select besi.env_set_cont from busi_env_set_infm besi
						where besi.biz_key = bizKey
						and besi.env_set_inf = sesi.env_set_inf
						and exists (
							select 1 from busi_env_set_infm
							where biz_key = besi.biz_key 
							and env_set_inf = '00055'
							and env_set_cont = 'Y'
						)
					), sesi.env_set_cont) into docIdPrefix 
					from sys_env_set_infm sesi
					where sesi.env_set_inf = '00056';
				
					select coalesce((
						select besi.env_set_cont from busi_env_set_infm besi
						where besi.biz_key = bizKey
						and besi.env_set_inf = sesi.env_set_inf
						and exists (
							select 1 from busi_env_set_infm
							where biz_key = besi.biz_key 
							and env_set_inf = '00055'
							and env_set_cont = 'Y'
						)
					), sesi.env_set_cont) into docIdDateFormat 
					from sys_env_set_infm sesi
					where sesi.env_set_inf = '00057';
		    		
	    			select length(coalesce((
						select besi.env_set_cont from busi_env_set_infm besi
						where besi.biz_key = bizKey
						and besi.env_set_inf = sesi.env_set_inf
						and exists (
							select 1 from busi_env_set_infm
							where biz_key = besi.biz_key 
							and env_set_inf = '00055'
							and env_set_cont = 'Y'
						)
					), sesi.env_set_cont)) into docIdSeqLen 
					from sys_env_set_infm sesi
					where sesi.env_set_inf = '00058';
		    	
	    			if coalesce(docIdPrefix, '') = '' then
		    			docIdPrefix := 'IV-';
		    		end if;
		    	
		    		if coalesce(docIdDateFormat, '') = '' then
		    			docIdDateFormat := 'YYYY';
		    		end if;
		    	
		    		if coalesce(docIdSeqLen, 0) = 0 then
		    			docIdSeqLen := 4;
		    		end if;
		    	
		    	when 'B' then 
		    		docIdPrefix := 'BL-';
			end case;
		
			docIdFactor := length(docIdPrefix) + length(docIdDateFormat);
		
		
			newSeqNo :=  (
				        select docIdPrefix || to_char(timezone('utc'::text, now()), docIdDateFormat::text) || lpad(cast(coalesce(max(right(doc_id, LENGTH (doc_id)-docIdFactor)::numeric), 0) + 1 as text),
				        
						  case when   LENGTH  (cast(coalesce(max(right(doc_id, LENGTH (doc_id)-docIdFactor)::numeric), 0) + 1 as text)) <= docIdSeqLen   then
					           docIdSeqLen
						  else 
						        LENGTH  (cast(coalesce(max(right(doc_id, LENGTH (doc_id)-docIdFactor)::numeric), 0) + 1 as text))
						  end  ,'0') from sys_doc_id_gen
								
						        where biz_key = bizKey
								and doc_type = $4
								and req_date = to_char(timezone('utc'::text, now()), docIdDateFormat::text)
								and rpad (doc_id, docIdFactor) =  docIdPrefix || to_char(timezone('utc'::text, now()), docIdDateFormat::text)
								and substring (doc_id , docIdFactor) ~ '^[0-9]+$' --checking is a numeric
			); 
		
		
			/*
			docIdFactor := length(docIdPrefix) + 1 + length(docIdDateFormat);
		
		
			newSeqNo :=  (
				        select docIdPrefix || '-' || to_char(timezone('utc'::text, now()), docIdDateFormat::text) || lpad(cast(coalesce(max(right(doc_id, LENGTH (doc_id)-docIdFactor)::numeric), 0) + 1 as text),
				        
						  case when   LENGTH  (cast(coalesce(max(right(doc_id, LENGTH (doc_id)-docIdFactor)::numeric), 0) + 1 as text)) <= docIdSeqLen   then
					           docIdSeqLen
						  else 
						        LENGTH  (cast(coalesce(max(right(doc_id, LENGTH (doc_id)-docIdFactor-1)::numeric), 0) + 1 as text))
						  end  ,'0') from sys_doc_id_gen
								
						        where biz_key = bizKey
								and doc_type = $4
								and req_date = to_char(timezone('utc'::text, now()), docIdDateFormat::text)
								and rpad (doc_id,docIdFactor) =  docIdPrefix || '-' || to_char(timezone('utc'::text, now()), docIdDateFormat::text)
								and substring (doc_id , docIdFactor+1) ~ '^[0-9]+$' --checking is a numeric
			); 
			*/
			
			/*
		    if  docType in ('P' ,'I', 'B' ) then 
		    
			        newSeqNo :=  (
				        select docIdPrefix || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || lpad(cast(coalesce(max(right(doc_id, LENGTH (doc_id)-11)::numeric), 0) + 1 as text),
				        
						  case when   LENGTH  (cast(coalesce(max(right(doc_id, LENGTH (doc_id)-11)::numeric), 0) + 1 as text)) <= 3   then
					           3
						  else 
						        LENGTH  (cast(coalesce(max(right(doc_id, LENGTH (doc_id)-11-1)::numeric), 0) + 1 as text))
						  end  ,'0') from sys_doc_id_gen
								
						        where biz_key = bizKey
								and doc_type = $4
								and req_date = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
								and rpad (doc_id,11) =  docIdPrefix || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
								and substring (doc_id , 11+1) ~ '^[0-9]+$' --checking is a numeric
							); 
              else 
              
                  newSeqNo :=  (
			        select docIdPrefix || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || lpad(cast(coalesce(max(right(doc_id, LENGTH (doc_id)-10)::numeric), 0) + 1 as text), 
			             
			        case when  LENGTH  (cast(coalesce(max(right(doc_id, LENGTH (doc_id)-10)::numeric), 0) + 1 as text)) <= 3   then
					                 3
						  else 
						        LENGTH  (cast(coalesce(max(right(doc_id, LENGTH (doc_id)-10)::numeric), 0) + 1 as text))
						  end  ,'0') from sys_doc_id_gen
						  
					where biz_key = bizKey
					and doc_type = $4
					and req_date = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
					and rpad (doc_id,10) =  docIdPrefix || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
					and substring (doc_id , 11) ~ '^[0-9]+$' --checking is a numeric
				);
               
			  end if;
			  */

		end if; 
	
	
	
	
		when lower($1) = 'adv_paym_mstr' and lower($2) = 'adv_paym_seq' and coalesce(trim($3), '') <> ''  then
        newSeqNo :=  (
	        select coalesce(max(adv_paym_seq), 0) + 1 from adv_paym_mstr
			where 1=1
			and biz_key = $3
		);
	    
	   when lower($1) = 'adv_paym_trns_hstr' and lower($2) = 'adv_paym_trns_seq' and coalesce(trim($3), '') <> ''  then
        newSeqNo :=  (
	        select coalesce(max(adv_paym_trns_seq), 0) + 1 from adv_paym_trns_hstr
			where 1=1
			and biz_key = $3
		);
	
	   when lower($1) = 'adv_paym_doc_mstr' and lower($2) = 'adv_paym_doc_seq' and coalesce(trim($3), '') <> ''  /*and coalesce(trim($4), '') <> '' */then
        newSeqNo :=  (
	        select coalesce(max(adv_paym_doc_seq), 0) + 1 from adv_paym_doc_mstr
			where 1=1
			and biz_key = $3
			--and adv_paym_seq = cast($4 as numeric)
		);
	   
	   when lower($1) = 'adv_paym_doc_detl' and lower($2) = 'adv_paym_doc_detl_seq' and coalesce(trim($3), '') <> ''  and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(adv_paym_doc_detl_seq), 0) + 1 from adv_paym_doc_detl
			where 1=1
			and biz_key = $3
			and adv_paym_doc_seq = cast($4 as numeric)
	  );
	
-----

-- add on (07/12)
	
	    when lower($1) = 'expt_resol_pay_evid_file' and lower($2) = 'evid_file_id' and coalesce(trim($3), '') <> ''  and coalesce(trim($4), '') <> '' then
       
        newSeqNo :=  (
	        select  cast(coalesce(max(evid_file_id ::numeric ),0) + 1 as text )  from expt_resol_pay_evid_file
			where 1=1
			and biz_key = $3
			and  appr_seq_no = $4
			
		);
	
----	
    when lower($1) = 'busa_doc_mstr' and lower($2) = 'doc_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    	bizKey := $3;
    	docType := $4;
    	if docType = 'E' then
	        newSeqNo :=  (
			/*	select cast(coalesce(max(doc_id::numeric), 0) + 1 as text) from busa_doc_mstr
				where biz_key = bizKey
				and doc_type = 'E'
				and doc_id ~ '^[0-9]+$'*/
			    select 'R' || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || lpad(cast(coalesce(max(right(doc_id, 3)::numeric), 0) + 1 as text), 3, '0') from busa_doc_mstr
				where biz_key = $3
				and doc_type =$4
				and SUBSTRING(reg_dtm,1,8)  = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
				and left(doc_id,10) = 'R' || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
			);
		else
	    	case docType 
	    	when 'I' then 
	    		docIdPrefix := 'IV';
	    	when 'B' then 
	    		docIdPrefix := 'BL'; 
	    	end case;
	    
	        newSeqNo :=  (
		        select docIdPrefix || '-' || to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || lpad(cast(coalesce(max(right(doc_id, 3)::numeric), 0) + 1 as text), 3, '0') from busa_doc_mstr
				where biz_key = bizKey
				and doc_type = docType
				and left(reg_dtm, 8) = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
			);
		end if;
	
	when lower($1) = 'dat_imp_wiz_trco_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from dat_imp_wiz_trco_infm
			where 1=1
			and biz_key = $3
		);
	
	when lower($1) = 'dat_imp_wiz_rcv_forw_set_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from dat_imp_wiz_rcv_forw_set_infm
			where 1=1
			and biz_key = $3
		);
	
	when lower($1) = 'dat_imp_wiz_pay_forw_set_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from dat_imp_wiz_pay_forw_set_infm
			where 1=1
			and biz_key = $3
		);
	
	
	when lower($1) = 'dat_imp_wiz_invoice' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from dat_imp_wiz_invoice
			where 1=1
			and biz_key = $3
		);
	
	when lower($1) = 'dat_imp_wiz_bill' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from dat_imp_wiz_bill
			where 1=1
			and biz_key = $3
		);
	
	when lower($1) = 'dat_imp_wiz_expense' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
	        select coalesce(max(seq_no), 0) + 1 from dat_imp_wiz_expense
			where 1=1
			and biz_key = $3
		);
		
	when lower($1) = 'busa_doc_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
	         select coalesce(max(file_id::numeric), 0) + 1 from busa_doc_file
			where 1=1
			and biz_key = $3
            and doc_id = $4
		);
	
	
	when lower($1) = 'adv_paym_trns_hstr_evid_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
	         select coalesce(max(file_id::numeric), 0) + 1 from adv_paym_trns_hstr_evid_file
			where 1=1
			and biz_key = $3
          and adv_paym_trns_seq = cast($4 as numeric)
		);
	when lower($1) = 'memo_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
	         select coalesce(max(seq_no ::numeric), 0) + 1 from memo_infm
			where 1=1
			and biz_key = $3
            and web_svc_id = $4
		);
	
	when lower($1) = 'einv_fee_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then

        newSeqNo :=  (
             select coalesce(max(disp_ord::numeric), 0) + 1 from einv_fee_detl
	            where 1=1
	            and biz_key = $3
	            and doc_id = $4
         );
        
    when lower($1) = 'einv_tax_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then

        newSeqNo :=  (
             select coalesce(max(disp_ord::numeric), 0) + 1 from einv_tax_detl
	            where 1=1
	            and biz_key = $3
	            and doc_id = $4
         );
        when lower($1) = 'gdt_cert_info' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> ''then

        newSeqNo :=  (
             select coalesce(max(seq_no::numeric), 0) + 1 from gdt_cert_info
	            where 1=1
	            and biz_key = $3
         );
        
        
      --https://www.kosign.com.kh/redmine/issues/36712  
     when lower($1) = 'saly_book_mstr_new' and lower($2) = 'sal_reg_seq_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
    
        newSeqNo :=  (
	        select coalesce(max(sal_reg_seq_no::numeric), 0) + 1 from saly_book_mstr_new  
			where 1=1
			and biz_key = bizKey
		);
	
	--https://www.kosign.com.kh/redmine/issues/36713
	when lower($1) = 'saly_book_detl_new' and lower($2) = 'sal_detl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        bizKey := $3;
    	salRegSeqNo := $4;
    
        newSeqNo :=  (
	        select coalesce(max(sal_dtl_seq_no::numeric), 0) + 1 from saly_book_detl_new sbdn 
			where 1=1
			and biz_key = bizKey
			and sal_reg_seq_no = salRegSeqNo
		);
	

	when lower($1) = 'busi_bankform_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
        newSeqNo :=  (
	        select coalesce(max(seq_no::numeric), 0) + 1 from busi_bankform_inm bbi 
			where 1=1
			and biz_key = bizKey
		);
	
	when lower($1) = 'busi_payroll_tax_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
    bizKey := $3;
    newSeqNo :=  (
        select coalesce(max(seq_no::numeric), 0) + 1 from busi_payroll_tax_infm bpti 
		where 1=1
		and biz_key = bizKey
	);
	
	--https://www.kosign.com.kh/redmine/issues/36714
	when lower($1) = 'saly_book_detl_inf_new' and lower($2) = 'sal_item_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        bizKey := $3;
    	salRegSeqNo := $4;
    	salDtlSeqNo := $5;
    
        newSeqNo :=  (
	        select coalesce(max(sal_item_seq_no::numeric), 0) + 1 from saly_book_detl_inf_new 
			where 1=1
			and biz_key = bizKey
			and sal_reg_seq_no = salRegSeqNo
			and sal_dtl_seq_no = salDtlSeqNo
		);
	
	--https://www.kosign.com.kh/redmine/issues/36704
	when lower($1) = 'busi_sal_acct_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
    
        newSeqNo :=  (
	        select coalesce(max(seq_no::numeric), 0) + 1 from busi_sal_acct_infm  
			where 1=1
			and biz_key = bizKey
		);
	
	
	--https://www.kosign.com.kh/redmine/issues/36707
	when lower($1) = 'empl_faml_infm' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		newSeqNo :=  (
	        select coalesce(max(seq_no::numeric), 0) + 1 from empl_faml_infm 
			where 1=1
			and biz_key  = $3
			and empl_no = $4
		);
	
	--https://www.kosign.com.kh/redmine/issues/36710
	when lower($1) = 'empl_payroll_infm' and lower($2) = 'empl_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		bizKey := $3;
	
		newSeqNo :=  (
	        select coalesce(max(empl_no::numeric), 0) + 1 from empl_payroll_infm 
			where 1=1
			and biz_key  = $3
			and empl_no = $4
		);
	
	
	
	
	--https://www.kosign.com.kh/redmine/issues/36893
	when lower($1) = 'adv_paym_user_list' and lower($2) = 'adv_paym_seq' and coalesce(trim($3), '') <> '' then
	
		newSeqNo :=  (
	        select coalesce(max(adv_paym_seq::numeric), 0) + 1 from adv_paym_user_list 
			where 1=1
			and biz_key  = $3
		);
	
	
	
		
		
		when lower($1) = 'napas_remit_trns_hstr' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(seq_no::numeric), 0) + 1 from napas_remit_trns_hstr 
		        where 1=1
				and biz_key  = $3
		 );	
		
		when lower($1) = 'busi_coll_pay_infm' and lower($2) = 'coll_seq_no' and coalesce(trim($3), '') <> '' then

            newSeqNo :=  (
                select coalesce(max(coll_seq_no::numeric), 0) + 1 from busi_coll_pay_infm 
                where 1=1
                and biz_key  = $3
         ); 
	
        
        		--http://www.kosign.com.kh/redmine/issues/37804#note-1
		
		when lower($1) = 'trns_hstr_loan_borw_hstr' and lower($2) = 'loan_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(loan_seq_no::numeric), 0) + 1 from trns_hstr_loan_borw_hstr 
		        where 1=1
				and biz_key  = $3
				and acct_seq_no = $4
		 );	
		
		
	 when lower($1) = 'trns_hstr_acct_expr_hstr' and lower($2) = 'dp_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(dp_seq_no::numeric), 0) + 1 from trns_hstr_acct_expr_hstr 
		        where 1=1
				and biz_key  = $3
				and acct_seq_no = $4
		 );	
		
	when lower($1) = 'busi_erp_api_infm_mstr' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(seq_no::numeric), 0) + 1 from busi_erp_api_infm_mstr 
		        where 1=1
				and ntnl_cd  = $3
				and tax_id = $4
				and erp_join_date = $5
		 );	
		
	when lower($1) = 'busi_usag_acct_infm_detl' and lower($2) = 'reg_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
			newSeqNo := (
				select coalesce(max(reg_seq_no::numeric),0) + 1 from busi_usag_acct_infm_detl
				where 1=1
				and biz_key = $3
				and use_usag_cd = $4
			);

	--https://www.kosign.com.kh/redmine/issues/38665
		when lower($1) = 'bltb_post_infm' and lower($2) = 'post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(post_seq_no::numeric), 0) + 1 from bltb_post_infm 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
		 );	
	
		when lower($1) = 'bltb_post_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(file_id::numeric), 0) + 1 from bltb_post_file 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
				and post_seq_no = $5::numeric
		 );	
		
		
		when lower($1) = 'bltb_comnt_infm' and lower($2) = 'post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(post_seq_no::numeric), 0) + 1 from bltb_comnt_infm 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
		 );		
		
		when lower($1) = 'bltb_comnt_infm' and lower($2) = 'comnt_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(comnt_seq_no::numeric), 0) + 1 from bltb_comnt_infm 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
		 );	
		
		when lower($1) = 'bltb_post_comnt_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(file_id::numeric), 0) + 1 from bltb_post_comnt_file 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
				and post_seq_no = $5::numeric 
		 );	
		
		
		when lower($1) = 'bltb_draft_post_mstr' and lower($2) = 'draft_post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(draft_post_seq_no::numeric), 0) + 1 from bltb_draft_post_mstr 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
		 );		
		
		when lower($1) = 'bltb_draft_post_detl' and lower($2) = 'draft_post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(draft_post_seq_no::numeric), 0) + 1 from bltb_draft_post_detl 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
		 );	
		
		when lower($1) = 'bltb_draft_post_detl' and lower($2) = 'draft_post_detl_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(draft_post_detl_seq_no::numeric), 0) + 1 from bltb_draft_post_detl 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
		 );	
		
		when lower($1) = 'bltb_draft_post_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(file_id::numeric), 0) + 1 from bltb_draft_post_file 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
				and draft_post_seq_no = $5::numeric
		 );	
		
		when lower($1) = 'bltb_user_new_post_infm' and lower($2) = 'post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(post_seq_no::numeric), 0) + 1 from bltb_user_new_post_infm 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
				and user_id = $5
		 );	
		
		when lower($1) = 'bltb_noti_infm' and lower($2) = 'noti_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(noti_seq_no::numeric), 0) + 1 from bltb_noti_infm 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
		 );	
		
		when lower($1) = 'bltb_noti_cont_infm' and lower($2) = 'noti_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(noti_seq_no::numeric), 0) + 1 from bltb_noti_cont_infm 
		        where 1=1
				and biz_key  = $3
				and lang_cd = $4
				and bltb_cd = $5
		 );	
		
		when lower($1) = 'bltb_infm' and lower($2) = 'bltb_cd' and coalesce(trim($3), '') <> ''  then
		
			newSeqNo :=  (
			select  to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || lpad(cast(coalesce(max(right(bltb_cd , 3)::numeric), 0) + 1 as text), 7, '0') from BLTB_INFM
	        where  1=1
	        and biz_key  = $3
	        and  bltb_cd not in('999999999999999') --for deafult
	        and  left(REG_DTM,8) = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
		 );	
		
		when lower($1) = 'bltb_draft_post_apprvr' and lower($2) = 'draft_post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(draft_post_seq_no::numeric), 0) + 1 from bltb_draft_post_apprvr 
		        where 1=1
				and biz_key  = $3
				and bltb_cd = $4
				and apprvr_id = $5
		 );	
		
		when lower($1) = 'expt_resol_apprvr' and lower($2) = 'appr_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(appr_seq_no::numeric), 0) + 1 from expt_resol_apprvr 
		        where 1=1
				and biz_key  = $3
				and apprvr_id = $4
		 );
		
		when lower($1) = 'busi_usag_set_infm' and lower($2) = 'use_usag_cd' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(cast(cast(max(use_usag_cd) as decimal)+1 as varchar), $4 || lpad('1', 9, '0')) use_usag_cd
			    from busi_usag_set_infm
				where 1 = 1
				and biz_key = $3
				and  usag_grp_cd = $4
		 );
		
		when lower($1) = 'busi_usag_set_infm' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(cast(max(disp_ord) as decimal)+1, '1') disp_ord
				from busi_usag_set_infm
				where 1 = 1
				and biz_key = $3
				and usag_grp_cd = $4
		 );
		
		when lower($1) = 'bltb_draft_post_detl_inf' and lower($2) = 'sal_item_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' and coalesce(trim($6), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(sal_item_seq_no::numeric), 0) + 1 from bltb_draft_post_detl_inf
		        where 1 = 1
				and biz_key  = $3
				and bltb_cd = $4
				and draft_post_seq_no = $5
				and draft_post_detl_seq_no = $6
		 );
		
		--https://www.kosign.com.kh/redmine/issues/39421
		
		when lower($1) = 'expt_resol_apprvr_infm' and lower($2) = 'apprvr_line_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(apprvr_line_no::numeric), 0) + 1 from expt_resol_apprvr_infm 
		        where 1=1
				and biz_key  = $3
				and appr_seq_no = $4
				--and apprvr_id appr_seq_no  = $5
		 );
		
		when lower($1) = 'bltb_draft_post_apprvr_infm' and lower($2) = 'apprvr_line_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(apprvr_line_no::numeric), 0) + 1 from bltb_draft_post_apprvr_infm 
		        where 1=1
				and biz_key  = $3
				and bltb_cd  = $4
				and draft_post_seq_no = $5::numeric
		 );

		----https://www.kosign.com.kh/redmine/issues/39421
		
		
		--S: https://www.kosign.com.kh/redmine/issues/40280
		when lower($1) = 'nbr_trco_infm_his' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(seq_no::numeric), 0) + 1 from nbr_trco_infm_his 
		        where 1=1
				and biz_key  = $3
				and trco_key = $4
		 );	
		
		when lower($1) = 'genl_noti_info' and lower($2) = 'genl_noti_seq_no' and coalesce(trim($3), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(genl_noti_seq_no::numeric), 0) + 1 from genl_noti_info 
		        where 1=1
				and biz_key  = $3
		 );	
		
		when lower($1) = 'genl_noti_snd_rcv_info' and lower($2) = 'genl_noti_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(genl_noti_seq_no::numeric), 0) + 1 from genl_noti_snd_rcv_info 
		        where 1=1
				and biz_key  = $3
				and snd_id  = $4
				and rcv_id  = $5
		 );	
		
		--E: https://www.kosign.com.kh/redmine/issues/40280	
		
		-- S: https://www.kosign.com.kh/redmine/issues/40393
		when lower($1) = 'saly_book_pay_his' and lower($2) = 'saly_book_pay_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(saly_book_pay_seq_no::numeric), 0) + 1 from saly_book_pay_his 
		        where 1=1
				and biz_key  = $3
				and sal_reg_seq_no  = $4
		 );	
		-- E: https://www.kosign.com.kh/redmine/issues/40393
		
		-- S: https://www.kosign.com.kh/redmine/issues/40681
		-- vas_acct_seq_no
	    when lower($1) = 'busi_vas_acct_infm' and lower($2) = 'vas_acct_seq_no' and coalesce(trim($3), '') <> '' then
		
		newSeqNo :=  (
		        select coalesce(max(vas_acct_seq_no::numeric), 0) + 1 from busi_vas_acct_infm 
		        where 1=1
				and biz_key  = $3
		);
	    -- E: https://www.kosign.com.kh/redmine/issues/40681
		
		-- S: https://www.kosign.com.kh/redmine/issues/40669
		when lower($1) = 'memo_infm_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(file_id::numeric), 0) + 1 from memo_infm_file 
		        where 1=1
				and biz_key  = $3
				and web_svc_id  = $4
				and seq_no = cast($5 as numeric)
		 );	
		-- E: https://www.kosign.com.kh/redmine/issues/40669
		
		-- S : https://www.kosign.com.kh/redmine/issues/35921
		
		when lower($1) = 'acct_trco_infm' and lower($2) = 'acct_trco_key' and coalesce(trim($3), '') <> '' then
		
		newSeqNo :=  (
		        select coalesce(max(acct_trco_key::numeric), 0) + 1 from acct_trco_infm 
		        where 1=1
				and biz_key  = $3
		);
		
		-- E : https://www.kosign.com.kh/redmine/issues/35921
	
		-- S : https://www.kosign.com.kh/redmine/issues/35921
		
		when lower($1) = 'vas_acct_infm' and lower($2) = 'vas_acct_seq' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
		
		newSeqNo :=  (
		        select coalesce(max(vas_acct_seq::numeric), 0) + 1 from vas_acct_infm 
		        where 1=1
				and biz_key  = $3
				and wta_biz_key =$4
		);
		
		-- E : https://www.kosign.com.kh/redmine/issues/35921
	
	    -- S : https://www.kosign.com.kh/redmine/issues/35921
	
	    when lower($1) = 'acct_trco_infm' and lower($2) = 'acct_trco_key' then
        newSeqNo :=  (select nextval('sq_acct_trco_key'));
       
       	-- E : https://www.kosign.com.kh/redmine/issues/35921

		-- S: https://www.kosign.com.kh/redmine/issues/40779
		when lower($1) = 'busa_doc_detl_vas_item_infm' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
	        select coalesce(count(*), 0) + 1 from busa_doc_detl_vas_item_infm
			where biz_key = $3
			and doc_id = $4
		);
		-- E: https://www.kosign.com.kh/redmine/issues/40779
	
	    -- S : https://www.kosign.com.kh/redmine/issues/40781
	   
       	when lower($1) = 'busi_doc_vas_item_actv_his' and lower($2) = 'busa_actv_seq' then
        newSeqNo :=  (select nextval('sq_busa_actv_seq'));

		when lower($1) = 'aut_cls_acct_trns_rmrk_infm' and lower($2) = 'aut_acct_trns_rmrk_seq' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(file_id::numeric), 0) + 1 from aut_cls_acct_trns_rmrk_infm 
		        where 1=1
				and biz_key  = $3
				and wta_biz_key  = $4
				and rmrk = $5
		 );	
       
        -- E : https://www.kosign.com.kh/redmine/issues/40781
        
        -- S: https://www.kosign.com.kh/redmine/issues/40684
		when lower($1) = 'audit_logs_infm' and lower($2) = 'audit_logs_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
		
			newSeqNo :=  (
		        select coalesce(max(audit_logs_seq_no::numeric), 0) + 1 from audit_logs_infm 
		        where 1=1
				and biz_key  = $3
				and wsvc_id  = $4
				and lang_cd = $5
		 );	
		-- E: https://www.kosign.com.kh/redmine/issues/40684

        -- S: https://www.kosign.com.kh/redmine/issues/41320

        when lower($1) = 'rmrk_org_infm' and lower($2) = 'rmrk_seq_no' and coalesce(trim($3), '') <> ''  then

			newSeqNo :=  (
		        select coalesce(max(rmrk_seq_no::numeric), 0) + 1 from rmrk_org_infm
		        where 1=1
				and biz_key  = $3
		);

		 -- E: https://www.kosign.com.kh/redmine/issues/41320

        -- S : https://www.kosign.com.kh/redmine/issues/41267

		when lower($1) = 'card_trns_rcv_pay_infm' and lower($2) = 'rcv_pay_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then

		newSeqNo :=  (
		        select coalesce(max(rcv_pay_seq_no::numeric), 0) + 1 from card_trns_rcv_pay_infm
		        where 1=1
				and biz_key  = $3
				and card_seq_no =$4
		);

		-- E : https://www.kosign.com.kh/redmine/issues/41267
 		-- S : https://www.kosign.com.kh/redmine/issues/41267

		when lower($1) = 'card_trns_hstr' and lower($2) = 'card_seq_no' and coalesce(trim($3), '') <> '' then

		newSeqNo :=  (
		        select coalesce(max(card_seq_no::numeric), 0) + 1 from card_trns_hstr
		        where 1=1
				and biz_key  = $3
		);

		-- E : https://www.kosign.com.kh/redmine/issues/41267

		-- S : https://www.kosign.com.kh/redmine/issues/41763
		when lower($1) = 'appr_line_infm' and lower($2) = 'appr_line_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(appr_line_no::numeric), 0) + 1 from appr_line_infm
		        where 1=1
				and biz_key  = $3
		);
		-- E : https://www.kosign.com.kh/redmine/issues/41763

		-- S : https://www.kosign.com.kh/redmine/issues/41773
        when lower($1) = 'bank_exch_rate_infm' and lower($2) = 'exch_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' and coalesce(trim($6), '') <> '' then

        newSeqNo :=  (
            select coalesce(max(exch_seq_no::numeric), 0) + 1 from bank_exch_rate_infm
            where 1 = 1
              and biz_key  = $3
              and bank_cd = $4
              and ref_date = $5
              and ccy_cd = $6
        );
        -- E : https://www.kosign.com.kh/redmine/issues/41773
        -- S : https://www.kosign.com.kh/redmine/issues/41786
        when lower($1) = 'trco_ovrs_infm' and lower($2) = 'trco_ovrs_key' and coalesce(trim($3), '') <> '' then

        newSeqNo :=  (
            select coalesce(max(trco_ovrs_key::numeric), 0) + 1 from trco_ovrs_infm
            where 1 = 1
              and biz_key  = $3
        );
        -- E : https://www.kosign.com.kh/redmine/issues/41786
		-- S : https://www.kosign.com.kh/redmine/issues/41786
		when lower($1) = 'ovrs_pymn_mstr' and lower($2) = 'ovrs_pymn_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(ovrs_pymn_no::numeric), 0) + 1 from ovrs_pymn_mstr 
		        where 1=1
				and biz_key  = $3
		);
		-- E : https://www.kosign.com.kh/redmine/issues/41786
		
		-- S : https://www.kosign.com.kh/redmine/issues/41829
		when lower($1) = 'chart_acct_infm' and lower($2) = 'chart_acct_seq_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(chart_acct_seq_no::numeric), 0) + 1 from chart_acct_infm 
		        where 1=1
				and biz_key  = $3
		);
		-- E : https://www.kosign.com.kh/redmine/issues/41829
	
	    -- S : https://www.kosign.com.kh/redmine/issues/42279
	    
	    when lower($1) = 'ovrs_pymn_sumr' and lower($2) = 'ovrs_pymn_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(no::numeric), 0) + 1 from
		        (
		        	select ovrs_pymn_no as no
		        	from ovrs_pymn_mstr
		        	where 1 = 1
		        	and biz_key = $3
		        	union all
		        	select ovrs_pymn_no as no
		        	from ovrs_pymn_sumr
		        	where 1 = 1
		        	and biz_key = $3
		        )x
		);
	    
	    when lower($1) = 'ovrs_pymn_sumr_detl' and lower($2) = 'ovrs_pymn_detl_no' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
				select coalesce(max(no::numeric), 0) + 1 from
		        (
		        	select ovrs_pymn_no as no
		        	from ovrs_pymn_mstr
		        	where 1 = 1
		        	and biz_key = $3
		        	union all
		        	select ovrs_pymn_detl_no as no
		        	from ovrs_pymn_sumr_detl
		        	where 1 = 1
		        	and biz_key = $3
		        )x
		);
	    
	    -- E : https://www.kosign.com.kh/redmine/issues/42279
	
		-- S : https://www.kosign.com.kh/redmine/issues/42938
		when lower($1) = 'busi_match_log' and lower($2) = 'match_log_seq' and coalesce(trim($3), '') <> '' then
		newSeqNo :=  (
		        select coalesce(max(match_log_seq::numeric), 0) + 1 from busi_match_log
		        where 1=1
				and biz_key  = $3
		);
		-- E : https://www.kosign.com.kh/redmine/issues/42938
		
    else
        newSeqNo := null;
    end case;    

    return newSeqNo;
    COMMIT;
EXCEPTION
 WHEN OTHERS THEN
    ROLLBACK;
    return null;
END;
$function$
;
