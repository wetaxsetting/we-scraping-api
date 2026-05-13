-- ============================================================================
-- V91__wb_get_next_val_appr_line_infm.sql
--
-- Settings > Approval Line: extend `wb_get_next_val` with the
-- `appr_line_infm.appr_line_no` branch (OQ-7 decided 2026-05-13). The
-- refactor's approval-line Create path delegates PK generation to this
-- branch via the existing `common.legacy.LegacyFunctionRepository`
-- funnel.
--
-- Branch body copied VERBATIM from JEX DEV (`wecmsvndev.wb_get_next_val`,
-- captured via the read-only `postgres_dev` MCP on 2026-05-13):
--
--   when lower($1) = 'appr_line_infm' and lower($2) = 'appr_line_no'
--        and coalesce(trim($3), '') <> '' then
--       newSeqNo := (
--           select coalesce(max(appr_line_no::numeric), 0) + 1
--           from appr_line_infm
--           where 1=1
--             and biz_key = $3
--       );
--
-- Args:
--   $1 = 'appr_line_infm'        (table)
--   $2 = 'appr_line_no'          (column)
--   $3 = caller's biz_key        (REQUIRED — per-tenant scope predicate)
--
-- Returns: numeric text — the next per-tenant `appr_line_no` value.
-- Concurrency: bare MAX+1, no advisory lock. Service wraps the call +
-- INSERT in one @Transactional; PK conflict surfaces as
-- DataIntegrityViolationException and rolls the tx back (the user's
-- retry produces a fresh allocation).
--
-- Retirement plan (per `wecms-api-main/CLAUDE.md` "Surviving legacy
-- functions"): drop this branch and migrate the call site to a
-- Java-side per-tenant generator once the legacy WeCMS / WABOOKS
-- systems no longer call `wb_get_next_val` for `appr_line_infm`.
-- No fixed deadline.
--
-- Idempotent: CREATE OR REPLACE FUNCTION. Preserves every V85 branch
-- + adds the new branch next to the other Settings features.
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
    -- Cross-feature: AUDIT_LOGS_INFM.AUDIT_LOGS_SEQ_NO — V66 (P-1).
    when lower($1) = 'audit_logs_infm' and lower($2) = 'audit_logs_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
            newSeqNo := (
                select cast(coalesce(max(audit_logs_seq_no::numeric), 0) + 1 as text)
                from audit_logs_infm
            );

    -- Settings > Approval Line: APPR_LINE_INFM.APPR_LINE_NO — V91
    -- (OQ-7 resolved 2026-05-13). Body copied VERBATIM from DEV. Per-
    -- tenant MAX+1 on biz_key. Mirrors legacy
    -- SeqHelper.getNextVal(util, idoCon, ApprLineInfm.class, "appr_line_no").
    when lower($1) = 'appr_line_infm' and lower($2) = 'appr_line_no' and coalesce(trim($3), '') <> '' then
            newSeqNo := (
                select cast(coalesce(max(appr_line_no::numeric), 0) + 1 as text)
                from appr_line_infm
                where 1=1
                  and biz_key = $3
            );

    -- Settings > Contacts: TRCO_INFM.TRCO_KEY — V85 (OQ-4 decided 2026-05-12).
    when lower($1) = 'trco_infm' and lower($2) = 'trco_key' then
            newSeqNo := (select nextval('sq_trco_key'));

    -- Settings > Purpose: BUSI_USAG_SET_INFM.USE_USAG_CD — V82 (OQ-3).
    when lower($1) = 'busi_usag_set_infm' and lower($2) = 'use_usag_cd' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') = '' then
            newSeqNo := (
                select coalesce(lpad((max(use_usag_cd)::numeric + 1)::varchar, 10, '0'),
                                '0000000001')
                from busi_usag_set_infm
                where biz_key = $3
                  and use_usag_cd ~ '^0[0-9]{9}$'
            );

    -- Sprint 0: BUSI_INFM.BIZ_KEY — Phase 1 bootstrap (CompanyBootstrapService)
    when lower($1) = 'busi_infm' and lower($2) = 'biz_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
          newSeqNo :=(
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
        			newSeqNo :=  (
    	        		select coalesce(max(prn_seq), 0) + 1 from acct_mstr
    					where 1=1
    					and biz_Key = $3
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
    		newSeqNo :=  (
    	        select coalesce(max(seq_no::numeric), 0) + 1 from ext_tran_call_his
    			where 1=1
    			and svc_id = $3
    			and ref_date = $4
    		);

    -- All other table/field combinations: return NULL.
    else
        newSeqNo := null;
    end case;

    return newSeqNo;
END;
$function$;
