-- ============================================================================
-- V27__wb_get_next_val_sprint1_scope.sql
--
-- Corrective migration: replaces V26's 2-branch stub with the correct
-- scope — Sprint 0 bootstrap branches + all Sprint 1 migrated-table
-- branches that have a corresponding DEV wb_get_next_val entry.
--
-- Rule (CLAUDE.md): for wb_get_next_val, include branches for tables that
-- are migrated locally AND have a DEV branch. Do not port branches for
-- tables outside the current refactor scope.
--
-- Source of truth: wecmsvndev.wb_get_next_val as of 2026-04-30.
-- Branch bodies copied verbatim from DEV.
--
-- Sprint 0 branches (2):
--   busi_infm.biz_key     — Phase 1 bootstrap (CompanyBootstrapService)
--   empl_infm.empl_no     — Phase 1 bootstrap (EmployeeNumberGeneratorService)
--
-- Sprint 1 branches added (11) — all Sprint 1 tables with a DEV branch:
--   acct_mstr.prn_seq
--   acct_trns_hstr.acct_seq_no
--   acct_trns_rcv_pay_infm.rcv_pay_seq_no
--   acct_trns_rcv_pay_evid_file.file_id
--   busa_doc_rcv_pay_his.rcv_pay_his_seq_no
--   busa_doc_mstr.doc_id
--   busi_cond_daily_sumr_detl.dtl_seq_no
--   einv_fee_detl.disp_ord
--   einv_tax_detl.disp_ord
--   napas_remit_trns_hstr.seq_no
--   ext_tran_call_his.seq_no
--
-- Sprint 1 tables with NO DEV wb_get_next_val branch (6):
--   bank_infm                — no branch in DEV; PK uses bank-registry data
--   busi_cond_daily_sumr     — no branch in DEV; batch-inserted by BCS process
--   sell_his_daily_sumr      — no branch in DEV; batch-inserted by BCS process
--   sell_his_daily_sumr_detl — no branch in DEV; batch-inserted by BCS process
--   bat_call_his             — no branch in DEV; scheduler-managed keys
--   oprt_stts_info           — no branch in DEV; synced from external source
-- ============================================================================

CREATE OR REPLACE FUNCTION wb_get_next_val(
    tablename character varying,
    fieldname character varying,
    param1 character varying DEFAULT NULL::character varying,
    param2 character varying DEFAULT NULL::character varying,
    param3 character varying DEFAULT NULL::character varying,
    param4 character varying DEFAULT NULL::character varying,
    param5 character varying DEFAULT NULL::character varying,
    param6 character varying DEFAULT NULL::character varying
)
RETURNS text
LANGUAGE plpgsql
AS $function$
DECLARE
    newSeqNo        text    := null;
    bizKey          text    := null;
    acctSeqNo       text    := null;
    rcvPaySeqNo     integer := null;
    lstCollDate     text    := null;
    trcoKey         text    := null;
    trscDt          text    := null;
    ccyCd           text    := null;
    docType         text    := null;
    docIdPrefix     text    := '';
BEGIN
    case
    -- Sprint 0: BUSI_INFM.BIZ_KEY — Phase 1 bootstrap (CompanyBootstrapService)
    when lower($1) = 'busi_infm' and lower($2) = 'biz_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
         -- ntnlCd := $3;
         -- chnlId := $4;
          newSeqNo :=(
    	       /*select $3||'_'|| $4 ||'_'||lpad((count(*)+1)::text ,6,'0') from busi_infm*/
          		select $3||'_'|| $4 ||'_'||lpad(cast(coalesce(max(right(BIZ_KEY, 6)::numeric), 0) + 1 as text), 6, '0') from busi_infm
          		where  left(biz_key,10) = $3||'_'|| $4
    	  );

    -- Sprint 0: EMPL_INFM.EMPL_NO — Phase 1 bootstrap (EmployeeNumberGeneratorService)
    when lower($1) = 'empl_infm' and lower($2) = 'empl_no' and coalesce(trim($3), '') <> '' then
            bizKey := $3;
            
            newSeqNo :=  (
    	        select to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || '_' || lpad((count(*)+1)::text ,3,'0') from empl_infm
    			where 1=1
    			and biz_key = bizKey
    			and left(empl_no, 8) = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
    		);

    -- Sprint 1: ACCT_MSTR.PRN_SEQ
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

    -- Sprint 1: ACCT_TRNS_HSTR.ACCT_SEQ_NO
    when lower($1) = 'acct_trns_hstr' and lower($2) = 'acct_seq_no' and coalesce(trim($3), '') <> '' then
        	bizKey := $3;
      
        
            newSeqNo :=  (
    	        select coalesce(max(acct_seq_no::numeric), 0) + 1 from acct_trns_hstr
    			where 1=1
    			and biz_key = bizKey
    		);

    -- Sprint 1: ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_SEQ_NO
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

    -- Sprint 1: ACCT_TRNS_RCV_PAY_EVID_FILE.FILE_ID
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

    -- Sprint 1: BUSA_DOC_RCV_PAY_HIS.RCV_PAY_HIS_SEQ_NO
    when lower($1) = 'busa_doc_rcv_pay_his' and lower($2) = 'rcv_pay_his_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        	
            newSeqNo :=  (
    	        select coalesce(max(rcv_pay_his_seq_no), 0) + 1 from busa_doc_rcv_pay_his
    			where 1=1
    			and biz_key = $3
    			and doc_id = $4
    		);

    -- Sprint 1: BUSA_DOC_MSTR.DOC_ID
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

    -- Sprint 1: BUSI_COND_DAILY_SUMR_DETL.DTL_SEQ_NO
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

    -- Sprint 1: EINV_FEE_DETL.DISP_ORD
    when lower($1) = 'einv_fee_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    
            newSeqNo :=  (
                 select coalesce(max(disp_ord::numeric), 0) + 1 from einv_fee_detl
    	            where 1=1
    	            and biz_key = $3
    	            and doc_id = $4
             );

    -- Sprint 1: EINV_TAX_DETL.DISP_ORD
    when lower($1) = 'einv_tax_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
    
            newSeqNo :=  (
                 select coalesce(max(disp_ord::numeric), 0) + 1 from einv_tax_detl
    	            where 1=1
    	            and biz_key = $3
    	            and doc_id = $4
             );

    -- Sprint 1: NAPAS_REMIT_TRNS_HSTR.SEQ_NO
    when lower($1) = 'napas_remit_trns_hstr' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
    		
    			newSeqNo :=  (
    		        select coalesce(max(seq_no::numeric), 0) + 1 from napas_remit_trns_hstr 
    		        where 1=1
    				and biz_key  = $3
    		 );

    -- Sprint 1: EXT_TRAN_CALL_HIS.SEQ_NO
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

    -- All other table/field combinations: return NULL.
    -- To add a new branch: table must be migrated locally, Spring service
    -- must create new rows in it, and the lead must confirm the branch.
    else
        newSeqNo := null;
    end case;

    return newSeqNo;
END;
$function$;
