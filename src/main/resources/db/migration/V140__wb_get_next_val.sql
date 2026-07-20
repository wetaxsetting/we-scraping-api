-- DROP FUNCTION wecmsdevdb.wb_get_next_val(varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar);

CREATE OR REPLACE FUNCTION wecmsdevdb.wb_get_next_val(tablename character varying, fieldname character varying, param1 character varying DEFAULT NULL::character varying, param2 character varying DEFAULT NULL::character varying, param3 character varying DEFAULT NULL::character varying, param4 character varying DEFAULT NULL::character varying, param5 character varying DEFAULT NULL::character varying, param6 character varying DEFAULT NULL::character varying)
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
BEGIN
    case
    -- ------------------------------------------------------------------
    -- ADDED branches — global sequence (highest collision risk vs Java MAX+1)
    -- ------------------------------------------------------------------

    -- 1. Payment > Approval seq: EXPT_RESOL_MSTR.APPR_SEQ_NO — shared global sequence.
    when lower($1) = 'expt_resol_mstr' and lower($2) = 'appr_seq_no' then
        newSeqNo :=  (select nextval('sq_appr_seq_no'));

    -- 2. Payment > Dispatch: TB_PAY_TARGET.TRAN_SEQ — shared firm transaction sequence.
    when lower($1) = 'tb_pay_target' and lower($2) = 'tran_seq' then
        newSeqNo :=  (select nextval('firm_seq_no'));

    -- 3. Payment > Dispatch: TB_FIRM_TRAN.TRAN_SEQ — legacy UAT twin of #25; draws the
    --      SAME global firm_seq_no sequence (tb_firm_tran shares the
    --      (tran_dt, biz_key, tran_seq) key space with tb_pay_target). No live Spring
    --      caller today — Spring reuses the #25 allocation for both rows — added for
    --      verbatim JEX parity so a future direct caller cannot diverge.
    when lower($1) = 'tb_firm_tran' and lower($2) = 'tran_seq' then
        newSeqNo :=  (select nextval('firm_seq_no'));

    -- 4. Payment > Dispatch: TB_PAY_FILE_MST.FILE_SEQ_NO — shared firm file sequence.
    when lower($1) = 'tb_pay_file_mst' and lower($2) = 'file_seq_no' then
        newSeqNo :=  (select nextval('firm_file_seq_no'));

    -- 5. Payment > Dispatch: TB_PAY_FILE_DATA.FILE_SEQ_NO — legacy UAT twin of #26; draws
    --      the SAME global firm_file_seq_no sequence. (Legacy author's own comment was
    --      "-- not sure file_seq_no": tb_pay_file_data inherits the master's file_seq_no, so
    --      it is never allocated independently.) No live Spring caller — every dispatch
    --      executor reuses the #26 (tb_pay_file_mst) allocation for its TbPayFileData rows.
    --      Added for verbatim JEX parity.
    when lower($1) = 'tb_pay_file_data' and lower($2) = 'file_seq_no' then
        newSeqNo :=  (select nextval('firm_file_seq_no'));

    -- 6. Add-on > e-Invoice: TRCO_ACTV_HSTR.ACTV_SEQ — global activity sequence.
    when lower($1) = 'trco_actv_hstr' and lower($2) = 'actv_seq' then
        newSeqNo :=  (select nextval('sq_actv_seq'));

    -- 6. Add-on > e-Invoice: TRCO_RSPB_INFM.RSPB_KEY — global responsible-person
    --     sequence (sq_rspb_key, V153). Added 2026-07-14 for the D5.3
    --     representative-persistence parity; branch body verbatim from DEV
    --     wb_get_next_val. CREATE OR REPLACE is idempotent — DBA re-applies V140.
    when lower($1) = 'trco_rspb_infm' and lower($2) = 'rspb_key' then
        newSeqNo :=  (select nextval('sq_rspb_key'));

    -- 7. Settings > Contacts: TRCO_INFM.TRCO_KEY — global sequence (NOT per-tenant).
    when lower($1) = 'trco_infm' and lower($2) = 'trco_key' then
        newSeqNo :=  (select nextval('sq_trco_key'));

    -- 8. Settings > Approval Line: APPR_LINE_INFM.APPR_LINE_NO — per-tenant MAX+1.
    when lower($1) = 'appr_line_infm' and lower($2) = 'appr_line_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(appr_line_no::numeric), 0) + 1 from appr_line_infm
            where 1=1
            and biz_key  = $3
        );

    -- 9. Settings > Chart of Accounts: CHART_ACCT_INFM.CHART_ACCT_SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'chart_acct_infm' and lower($2) = 'chart_acct_seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(chart_acct_seq_no::numeric), 0) + 1 from chart_acct_infm
            where 1=1
            and biz_key  = $3
        );

    -- 10. Banking > Smart Organizer: RMRK_ORG_INFM.RMRK_SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'rmrk_org_infm' and lower($2) = 'rmrk_seq_no' and coalesce(trim($3), '') <> ''  then
        newSeqNo :=  (
            select coalesce(max(rmrk_seq_no::numeric), 0) + 1 from rmrk_org_infm
            where 1=1
            and biz_key  = $3
        );

    -- 11. Payment > Card Transactions Sync: CARD_TRNS_HSTR.CARD_SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'card_trns_hstr' and lower($2) = 'card_seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(card_seq_no::numeric), 0) + 1 from card_trns_hstr
            where 1=1
            and biz_key  = $3
        );

    -- 12. Settings > Contacts > Attachments: TRCO_INFO_FILE.FILE_ID — per-contact MAX+1.
    when lower($1) = 'trco_info_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' then
        trcoKey := $3;
        newSeqNo :=  (
            select coalesce(max(file_id::numeric), 0) + 1 from trco_info_file
            where 1=1
            and trco_key = trcoKey
        );

    -- 13. Banking > Transaction Sync: ACCT_TRNS_HSTR.ACCT_SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'acct_trns_hstr' and lower($2) = 'acct_seq_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
        newSeqNo :=  (
            select coalesce(max(acct_seq_no::numeric), 0) + 1 from acct_trns_hstr
            where 1=1
            and biz_key = bizKey
        );

    -- 14. Banking > Smart Organizer: ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_SEQ_NO — per (tenant, acct_seq_no) MAX+1.
    when lower($1) = 'acct_trns_rcv_pay_infm' and lower($2) = 'rcv_pay_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> ''   then
        bizKey := $3;
        acctSeqNo := $4;
        newSeqNo :=  (
            select coalesce(max(rcv_pay_seq_no), 0) + 1 from acct_trns_rcv_pay_infm
            where 1=1
            and biz_key = bizKey
            and acct_seq_no = acctSeqNo
        );

    -- 15. Bootstrap: BUSI_INFM.BIZ_KEY — {ntnl_cd}_{chnl_id}_{NNNNNN} composite. ACTIVE — called by
    --      Spring tenant provisioning via the generic funnel (BusinessKeyGeneratorService.nextVal).
    when lower($1) = 'busi_infm' and lower($2) = 'biz_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=(
            select $3||'_'|| $4 ||'_'||lpad(cast(coalesce(max(right(BIZ_KEY, 6)::numeric), 0) + 1 as text), 6, '0') from busi_infm
            where  left(biz_key,10) = $3||'_'|| $4
        );

    -- 16. Bootstrap: EMPL_INFM.EMPL_NO — {YYYYMMDD}_{NNN} per-tenant daily counter. ACTIVE — called
    --      by Spring tenant provisioning via the generic funnel (EmployeeNumberGeneratorService.nextVal).
    when lower($1) = 'empl_infm' and lower($2) = 'empl_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
        newSeqNo :=  (
            select to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || '_' || lpad((count(*)+1)::text ,3,'0') from empl_infm
            where 1=1
            and biz_key = bizKey
            and left(empl_no, 8) = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
        );

    -- ------------------------------------------------------------------
    -- RESTORED branches (live callers; dropped by the V140 consolidation)
    -- ------------------------------------------------------------------

    -- 17. Common > Audit Log: AUDIT_LOGS_INFM.AUDIT_LOGS_SEQ_NO — whole-table MAX+1 (untenanted).
    --     This restores the archived V66 body BYTE-FOR-BYTE — the body the live
    --     AuditLogService contract depends on (one seq_no per audit event, reused
    --     across the per-locale row fan-out; $3/$4/$5 presence-checked, unused in
    --     the SELECT). NOTE: the *current* live DEV function has since been changed
    --     to scope this branch by (biz_key, wsvc_id, lang_cd). That divergence is
    --     reported as a separate follow-up — adopting it here would break the
    --     existing caller, which passes (bizKey, refNo, refNo1), not real
    --     wsvc_id / lang_cd values, so a scoped MAX would never match a stored row.
    when lower($1) = 'audit_logs_infm' and lower($2) = 'audit_logs_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        newSeqNo :=  (
            select cast(coalesce(max(audit_logs_seq_no::numeric), 0) + 1 as text)
            from audit_logs_infm
        );

    -- 18. Settings > Purpose Settings: BUSI_USAG_SET_INFM.USE_USAG_CD (grouped) — per (biz_key, usag_grp_cd).
    when lower($1) = 'busi_usag_set_infm' and lower($2) = 'use_usag_cd' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(cast(cast(max(use_usag_cd) as decimal)+1 as varchar), $4 || lpad('1', 9, '0')) use_usag_cd
            from busi_usag_set_infm
            where 1 = 1
            and biz_key = $3
            and  usag_grp_cd = $4
        );

    -- 19. Purpose Settings: USE_USAG_CD (no group) — plain 10-digit 0000000001.. sequence per biz_key.
    --      Regex requires a leading '0' to exclude legacy group-prefixed codes.
    when lower($1) = 'busi_usag_set_infm' and lower($2) = 'use_usag_cd' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') = '' then
        newSeqNo :=  (
            select coalesce(lpad((max(use_usag_cd)::numeric + 1)::varchar, 10, '0'), '0000000001')
            from busi_usag_set_infm
            where 1 = 1
            and biz_key = $3
            and use_usag_cd ~ '^0[0-9]{9}$'
        );

    -- ------------------------------------------------------------------
    -- ADDED branches — per-scope MAX+1
    -- ------------------------------------------------------------------

    -- 20. Payment > Dispatch: TB_PAY_FILE_DATA.SEQ — per (biz_key, file_seq_no) MAX+1 line
    --      counter (this is the row's SEQ within a file, NOT a global sequence). ACTIVE
    --      (MR-2B 2026-06-10): nextTbPayFileDataSeq — the 4 dispatch executors (Transfer /
    --      InternalTransfer / Overseas / Payroll) seed ONCE per dispatch then increment SEQ in
    --      Java across the file batch (seed-once pattern; no per-row DB round-trip). Because
    --      file_seq_no is freshly allocated per dispatch, the seed is always 1 -> 1, 2, 3, ...
    --      Body copied verbatim from the live JEX function, including the legacy `seq::int` cast
    --      (every other MAX+1 branch uses ::numeric — left as-is for parity, not normalised).
    when lower($1) = 'tb_pay_file_data' and lower($2) = 'seq' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(seq::int), 0) + 1 from tb_pay_file_data
            where 1=1
            and biz_key = $3
            and file_seq_no = $4
        );

    -- 21. Banking > Sync History: EXT_TRAN_CALL_HIS.SEQ_NO — per (svc_id, ref_date) MAX+1.
    when lower($1) = 'ext_tran_call_his' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(seq_no::numeric), 0) + 1 from ext_tran_call_his
            where 1=1
            and svc_id = $3
            and ref_date = $4
        );

    -- 22. Banking > Own-Account Transfer: NAPAS_REMIT_TRNS_HSTR.SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'napas_remit_trns_hstr' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(seq_no::numeric), 0) + 1 from napas_remit_trns_hstr
            where 1=1
            and biz_key  = $3
        );

    -- 23. Banking > Bank Credentials: BANK_CERT_INFO.SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'bank_cert_info' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(seq_no), 0) + 1 from bank_cert_info
            where 1=1
            and biz_key = $3
        );

    -- 24. Payment > Banking Match: BUSI_MATCH_LOG.MATCH_LOG_SEQ — per-tenant MAX+1.
    when lower($1) = 'busi_match_log' and lower($2) = 'match_log_seq' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(match_log_seq::numeric), 0) + 1 from busi_match_log
            where 1=1
            and biz_key  = $3
        );

    -- 25. Settings > VAS Account: BUSI_VAS_ACCT_INFM.VAS_ACCT_SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'busi_vas_acct_infm' and lower($2) = 'vas_acct_seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(vas_acct_seq_no::numeric), 0) + 1 from busi_vas_acct_infm
            where 1=1
            and biz_key  = $3
        );

    -- 26. Settings > Login Credentials (GDT): GDT_CERT_INFO.SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'gdt_cert_info' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(seq_no::numeric), 0) + 1 from gdt_cert_info
            where 1=1
            and biz_key = $3
        );

    -- 27. Auth > Change Password: USER_PW_CHG_HIS.REG_SEQ_NO — per-user MAX+1.
    when lower($1) = 'user_pw_chg_his' and lower($2) = 'reg_seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(reg_seq_no), 0) + 1 from user_pw_chg_his
            where 1=1
            and user_id = $3
        );

    -- 28. Settings > ERP Transaction History: ERP_TRNS_HSTR.SEQ_NO — per-tenant MAX+1.
    when lower($1) = 'erp_trns_hstr' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(seq_no::numeric), 0) + 1 from erp_trns_hstr
            where 1=1
            and biz_key  = $3
        );

    -- 29. Payment > Card Transactions: BUSA_DOC_RCV_PAY_HIS.RCV_PAY_HIS_SEQ_NO — per (biz_key, doc_id) MAX+1.
    when lower($1) = 'busa_doc_rcv_pay_his' and lower($2) = 'rcv_pay_his_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(rcv_pay_his_seq_no), 0) + 1 from busa_doc_rcv_pay_his
            where 1=1
            and biz_key = $3
            and doc_id = $4
        );

    -- 30. Payment > Overseas: OVRS_PYMN_SUMR_DETL.OVRS_PYMN_DETL_NO — per-tenant MAX+1 over (mstr UNION detl).
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

    -- 31. Settings > Contacts / e-Invoice auto-register: TRCO_ACCT_INFO.TRCO_ACCT_KEY — per (biz_key, trco_key) MAX+1.
    when lower($1) = 'trco_acct_info' and lower($2) = 'trco_acct_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        bizKey := $3;
        trcoKey := $4;
        newSeqNo :=  (
            select coalesce(max(trco_acct_key::numeric), 0) + 1 from trco_acct_info
            where 1=1
            and biz_key = bizKey
            and trco_key = trcoKey
        );

    -- 32. Banking > Exchange Rate batch: BANK_EXCH_RATE_INFM.EXCH_SEQ_NO — per (biz_key, bank_cd, ref_date, ccy_cd) MAX+1.
    when lower($1) = 'bank_exch_rate_infm' and lower($2) = 'exch_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' and coalesce(trim($6), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(exch_seq_no::numeric), 0) + 1 from bank_exch_rate_infm
            where 1 = 1
            and biz_key  = $3
            and bank_cd = $4
            and ref_date = $5
            and ccy_cd = $6
        );

    -- 33. Settings > Contacts (address): TRCO_ADDR_INFM.TRCO_KEY — global sq_trco_key sequence
    --      (same sequence as #7 trco_infm.trco_key). No live Spring caller — address rows
    --      inherit the parent contact's TRCO_KEY (allocated once via #7 nextTrcoKey). Parity branch.
    when lower($1) = 'trco_addr_infm' and lower($2) = 'trco_key' then
        newSeqNo :=  (select nextval('sq_trco_key'));

    -- 34. Settings > Contacts (address): TRCO_ADDR_INFM.TRCO_ADDR_KEY — per (biz_key, trco_key)
    --      MAX+1. No live Spring caller — ContactsService sets TRCO_ADDR_KEY from the client
    --      request (AddressOp.trcoAddrKey), not DB-generated. Parity branch (cf. OD-1 trco_acct_key).
    when lower($1) = 'trco_addr_infm' and lower($2) = 'trco_addr_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(trco_addr_key::numeric), 0) + 1 from trco_addr_infm
            where 1=1
            and biz_key  = $3
            and trco_key = $4
        );

    -- 35. Settings > Contacts (address): TRCO_ADDR_INFM.TRCO_BSUN_POST_ADRS — per (biz_key, trco_key)
    --      MAX+1. No live Spring caller — set from the client request, not DB-generated. Parity branch.
    when lower($1) = 'trco_addr_infm' and lower($2) = 'trco_bsun_post_adrs' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(trco_bsun_post_adrs::numeric), 0) + 1 from trco_addr_infm
            where 1=1
            and biz_key  = $3
            and trco_key = $4
        );

    -- ------------------------------------------------------------------
    -- OD-2 REVERSAL (authorized 2026-06-09, wired MR-1) — scheduled-payment keys moved from
    -- the date-prefixed InternalTransferKeyGenerator to this plain-int funnel.
    -- Branch bodies copied VERBATIM from the live JEX function. Spring wiring is LIVE:
    -- InternalTransferKeyGenerator.nextScdulPymnNo -> funnel #36, shared by all 4
    -- scheduled-payment modules (Vendor / Expense / Payroll / Internal Transfer).
    -- ------------------------------------------------------------------

    -- 36. Payment > Scheduled Payment: SCDUL_PYMN_SUMR.SCDUL_PYMN_NO — per-tenant plain-int MAX+1.
    --      ACTIVE (MR-1): nextScdulPymnNo (InternalTransferKeyGenerator), shared by all 4
    --      scheduled-payment modules; replaced the date-prefixed Java generator.
    when lower($1) = 'scdul_pymn_sumr' and lower($2) = 'scdul_pymn_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(scdul_pymn_no::numeric), 0) + 1 from scdul_pymn_sumr
            where 1=1
            and biz_key  = $3
        );

    -- 37. Payment > Scheduled Payment (detail): SCDUL_PYMN_SUMR_DETL.SCDUL_PYMN_NO — per-tenant plain-int MAX+1.
    --      DORMANT — no live Spring caller: detail rows inherit the master's SCDUL_PYMN_NO as an
    --      FK copy (set from the #36 master value), never DB-generated. Parity branch; do not wire.
    when lower($1) = 'scdul_pymn_sumr_detl' and lower($2) = 'scdul_pymn_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(scdul_pymn_no::numeric), 0) + 1 from scdul_pymn_sumr_detl
            where 1=1
            and biz_key  = $3
        );

    -- 38. Payment > Scheduled Payment (detail): SCDUL_PYMN_SUMR_DETL.SCDUL_PYMN_DETL_NO — per-tenant plain-int MAX+1.
    --      ACTIVE: nextScdulPymnDetlNo — the 4 scheduled-payment create flows seed ONCE per
    --      document then increment in Java across the detail batch (seed-once pattern).
    when lower($1) = 'scdul_pymn_sumr_detl' and lower($2) = 'scdul_pymn_detl_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(scdul_pymn_detl_no::numeric), 0) + 1 from scdul_pymn_sumr_detl
            where 1=1
            and biz_key  = $3
        );

    -- 39. Payment > Approval / Resolution: EXPT_RESOL_MSTR.DOC_NO — env-set-driven
    --     {prefix}_{date}_{seq} (busi_env_set_infm / sys_env_set_infm 00070..00073),
    --     per-tenant daily MAX+1 of the trailing numeric part. Reverses the earlier
    --     Java-side exception (2026-06-10): doc_no now allocates through this funnel
    --     branch so the refactor mints the same DOC_NO the live JEX/UAT/PROD DB would.
    --     doc_no carries no unique constraint (PK is (biz_key, appr_seq_no)), so the
    --     no-row-lock MAX+1 race is accepted legacy parity — no retry required.
    --     Body copied VERBATIM from the live JEX function (uses docNoPrefix /
    --     docNoDateFormat / docNoSeqLen / docNoFactor from the DECLARE block).
    when lower($1) = 'expt_resol_mstr' and lower($2) = 'doc_no' and coalesce(trim($3), '') <> '' then
        bizKey := $3;

        select coalesce((
            select besi.env_set_cont
            from busi_env_set_infm besi
            where besi.biz_key = $3
            and besi.env_set_inf = sesi.env_set_inf
            and exists (
                select 1
                from busi_env_set_infm
                where biz_key = besi.biz_key
                and env_set_inf = '00070'
                and env_set_cont = 'Y'
            )
        ), sesi.env_set_cont)
        into docNoPrefix
        from sys_env_set_infm sesi
        where sesi.env_set_inf = '00071';

        select coalesce((
            select besi.env_set_cont
            from busi_env_set_infm besi
            where besi.biz_key = $3
            and besi.env_set_inf = sesi.env_set_inf
            and exists (
                select 1
                from busi_env_set_infm
                where biz_key = besi.biz_key
                and env_set_inf = '00070'
                and env_set_cont = 'Y'
            )
        ), sesi.env_set_cont)
        into docNoDateFormat
        from sys_env_set_infm sesi
        where sesi.env_set_inf = '00072';

        select length(coalesce((
            select besi.env_set_cont
            from busi_env_set_infm besi
            where besi.biz_key = $3
            and besi.env_set_inf = sesi.env_set_inf
            and exists (
                select 1
                from busi_env_set_infm
                where biz_key = besi.biz_key
                and env_set_inf = '00070'
                and env_set_cont = 'Y'
            )
        ), sesi.env_set_cont))
        into docNoSeqLen
        from sys_env_set_infm sesi
        where sesi.env_set_inf = '00073';

        docNoFactor := length(docNoPrefix) + length(docNoDateFormat);

        newSeqNo := (
            select docNoPrefix || '_' ||
                   to_char(timezone('utc'::text, now()), docNoDateFormat::text) || '_' ||
                   lpad(
                       cast(
                           coalesce(
                               max(
                                   right(
                                       split_part(
                                           doc_no,
                                           '_',
                                           array_length(string_to_array(doc_no, '_'), 1)
                                       ),
                                       docNoSeqLen
                                   )::numeric
                               ),
                               0
                           ) + 1 as text
                       ),
                       docNoSeqLen,
                       '0'
                   )
            from expt_resol_mstr
            where biz_key = bizKey
            and del_yn = 'N'
            and left(doc_no, docNoFactor + 1) =
                docNoPrefix || '_' || to_char(timezone('utc'::text, now()), docNoDateFormat::text)
        );

    -- 40. Settings > Contacts (bank account): TRCO_ACCT_INFO.DISP_ORD — per (biz_key, trco_key)
    --     MAX+1 display order. DORMANT legacy-parity branch (Option B, 2026-06-10): added so
    --     wb_get_next_val matches UAT/PROD, but NO Spring caller routes through it — ContactsService
    --     keeps DISP_ORD client-supplied (default 1). Same display-order exception family as
    --     rmrk_org_infm.disp_ord / busi_usag_set_infm.disp_ord. Do NOT wire to the funnel without a
    --     PO/frontend decision (Option C — would reverse the client-supplied contract).
    when lower($1) = 'trco_acct_info' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        bizKey := $3;
        trcoKey := $4;
        newSeqNo :=  (
            select coalesce(max(disp_ord::numeric), 0) + 1 from trco_acct_info
            where 1=1
            and biz_key = bizKey
            and trco_key = trcoKey
        );

    -- 41. Payment > Approval/Resolution (detail): EXPT_RESOL_DETL.APPR_DETL_SEQ_NO — per
    --     appr_seq_no MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10): NO Spring caller
    --     — there is no ExptResolDetl entity, so Spring never writes EXPT_RESOL_DETL. Body copied
    --     VERBATIM from JEX, including the legacy quirk: the predicate checks $3 but the body keys
    --     on $4 (appr_seq_no) and ignores biz_key (commented out upstream). Do not wire (no write path).
    when lower($1) = 'expt_resol_detl' and lower($2) = 'appr_detl_seq_no' and coalesce(trim($3), '') <> '' then
        apprSeqNo := $4;
        newSeqNo :=  (
            select coalesce(max(appr_detl_seq_no), 0) + 1 from expt_resol_detl
            where 1=1
            and appr_seq_no = apprSeqNo
        );

    -- 42. Payment > Approval/Resolution (attachment): EXPT_RESOL_FILE.FILE_ID — per
    --     (biz_key, appr_seq_no) MAX+1. ACTIVE (MR-2A 2026-06-10): nextExptResolFileId —
    --     InternalTransferService seeds ONCE on the request-approval attachment path then increments
    --     FILE_ID in Java across the batch (seed-once pattern). FILE_ID is part of the PK
    --     (BIZ_KEY, APPR_SEQ_NO, FILE_ID); appr_seq_no is freshly allocated per submission, so the
    --     seed is always 1 (MAX over a brand-new appr_seq_no = 0) -> 1, 2, 3, ...
    when lower($1) = 'expt_resol_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
        apprSeqNo := $4;
        newSeqNo :=  (
            select coalesce(max(file_id::numeric), 0) + 1 from expt_resol_file
            where 1=1
            and biz_key = bizKey
            and appr_seq_no = apprSeqNo
        );

    -- 43. Add-on > e-Invoice (detail): BUSA_DOC_DETL.DISP_ORD — per (biz_key, doc_id) COUNT(*)+1
    --     display order. DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired. Spring
    --     keeps DISP_ORD source-supplied from the scraped e-invoice line (EInvoiceDetailScrapeService:
    --     d.setDispOrd(p.dispOrd() > 0 ? p.dispOrd() : i + 1)), with a wholesale delete+re-insert
    --     write model — different from the legacy append-one COUNT(*)+1, so do NOT wire (Option C).
    --     Same display-order exception family as trco_acct_info.disp_ord / busi_usag_set_infm.disp_ord.
    when lower($1) = 'busa_doc_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(count(*), 0) + 1 from busa_doc_detl
            where biz_key = $3
            and doc_id = $4
        );

    -- 44. Settings > Contacts (custom group): PGRO_BSAC_LNKN_INFM.GRP_CD — legacy custom-group code
    --     {user_id}-{count+1} scoped by (biz_key, user_id). DORMANT legacy-parity branch (Option B,
    --     2026-06-10): NOT wired — the custom-contact-group feature is NOT ported. ContactsService
    --     writes only the fixed system codes GRP_CD ∈ {'B','S'} with USER_ID='ALL', never this
    --     generator. Body copied VERBATIM from JEX: relies on PostgreSQL precedence (binary '+'
    --     binds tighter than '||'), so it evaluates as userId || '-' || (count(*)+1). Do not wire
    --     (Option C) until the custom-group feature exists.
    when lower($1) = 'pgro_bsac_lnkn_infm' and lower($2) = 'grp_cd' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        bizKey := $3;
        userId := $4;
        newSeqNo :=  (
            select userId || '-' || coalesce(count(*), 0) + 1 from pgro_bsac_lnkn_infm
            where biz_key = bizKey
            and user_id = userId
        );

    -- 45. Settings > User usage history: USER_USAG_USE_HIS.SEQ_NO — per (biz_key, user_id) MAX+1.
    --     DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — the user-usage-history
    --     tracking feature is not ported (no entity / repository / service / caller). Body verbatim
    --     from JEX. Do not wire (Option C) until the feature exists.
    when lower($1) = 'user_usag_use_his' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(seq_no), 0) + 1 from user_usag_use_his
            where 1=1
            and biz_key = $3
            and user_id = $4
        );

    -- 46. Settings > User contact-use history: USER_TRCO_USE_HIS.SEQ_NO — per (biz_key, user_id) MAX+1.
    --     DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — the contact-use-history
    --     tracking feature is not ported (no entity / repository / service / caller). Body verbatim
    --     from JEX. Do not wire (Option C) until the feature exists.
    when lower($1) = 'user_trco_use_his' and lower($2) = 'seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(seq_no), 0) + 1 from user_trco_use_his
            where 1=1
            and biz_key = $3
            and user_id = $4
        );

    -- 47. Banking > Accounts: ACCT_MSTR.PRN_SEQ — per-tenant MAX+1 print/display order. DORMANT
    --     legacy-parity branch (Option B, 2026-06-10): NOT wired. prn_seq is READ-ONLY in the
    --     refactor — AccountService uses it only as the Account Inquiry default sort key
    --     (DEFAULT_SORT asc("prnSeq")); account rows come from bank sync/seed and the legacy
    --     print-order-reassignment write path is not ported, so prn_seq is never generated in
    --     Spring. Body copied VERBATIM from JEX, including the legacy quirks: only $3 is checked
    --     ($4/$5 bank_cd/acct_no commented out) and the `biz_Key` capital-K spelling (harmless —
    --     PostgreSQL folds unquoted identifiers to lowercase). Do not wire (Option C).
    when lower($1) = 'acct_mstr' and lower($2) = 'prn_seq' and coalesce(trim($3), '') <> '' then
        bizKey := $3;
        newSeqNo :=  (
            select coalesce(max(prn_seq), 0) + 1 from acct_mstr
            where 1=1
            and biz_Key = $3
        );

    -- 48. Add-on / Documents: SYS_DOC_ID_GEN.DOC_ID — env-set-driven business-document numbering
    --     (docType 'E' -> R-YYYYMMDD-nnn; else PO-/Q-/IV-/BL- with env-set 00055..00058 driving the
    --     'I' invoice prefix/date-format/seq-length). DORMANT legacy-parity branch (Option B,
    --     2026-06-10): NOT wired — there is no SysDocIdGen entity/repository/service/caller; the
    --     business-document numbering feature is not ported. Copied VERBATIM from JEX, INCLUDING the
    --     commented-out legacy alternative blocks. Do not wire (Option C) until the feature exists.
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
            
        end if;

    -- 49. Add-on / Documents: BUSA_DOC_MSTR.DOC_ID — legacy manual business-document numbering
    --     (docType 'E' -> R-YYYYMMDD-nnn daily MAX+1; 'I' -> IV-…, 'B' -> BL-…). DORMANT
    --     legacy-parity branch (Option B, 2026-06-10): NOT wired. Spring writes BUSA_DOC_MSTR only
    --     via the e-invoice scrape (EInvoiceScrapeService: e.setDocId(docId)), where doc_id is the
    --     SCRAPED invoice identity (GDT serial) — NOT this MAX+1 generator. The manual-doc-creation
    --     path that would use it is not ported. Active logic copied verbatim from JEX (the legacy
    --     commented-out numeric-doc_id alternative is omitted). Do not wire (Option C).
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

    -- 50. Add-on > e-Invoice (fee detail): EINV_FEE_DETL.DISP_ORD — per (biz_key, doc_id) MAX+1
    --     display order. DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — Spring
    --     sources disp_ord from the e-invoice scrape (EInvoiceDetailScrapeService:
    --     e.setDispOrd(f.dispOrd() > 0 ? f.dispOrd() : i + 1)), wholesale delete+re-insert. Same
    --     display-order exception family as busa_doc_detl.disp_ord. Do not wire (Option C).
    when lower($1) = 'einv_fee_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(disp_ord::numeric), 0) + 1 from einv_fee_detl
            where 1=1
            and biz_key = $3
            and doc_id = $4
        );

    -- 51. Add-on > e-Invoice (tax detail): EINV_TAX_DETL.DISP_ORD — per (biz_key, doc_id) MAX+1
    --     display order. DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — Spring
    --     sources disp_ord from the e-invoice scrape (EInvoiceDetailScrapeService:
    --     e.setDispOrd(t.dispOrd() > 0 ? t.dispOrd() : i + 1)), wholesale delete+re-insert. Do not
    --     wire (Option C).
    when lower($1) = 'einv_tax_detl' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(disp_ord::numeric), 0) + 1 from einv_tax_detl
            where 1=1
            and biz_key = $3
            and doc_id = $4
        );

    -- 52. Bulletin Board (post): BLTB_POST_INFM.POST_SEQ_NO — per (biz_key, bltb_cd) MAX+1.
    --     DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — the bulletin-board
    --     feature is not ported (no BltbPostInfm entity/caller). Do not wire (Option C).
    when lower($1) = 'bltb_post_infm' and lower($2) = 'post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(post_seq_no::numeric), 0) + 1 from bltb_post_infm
            where 1=1
            and biz_key  = $3
            and bltb_cd = $4
        );

    -- 53. Bulletin Board (comment, parent-post seq): BLTB_COMNT_INFM.POST_SEQ_NO — per
    --     (biz_key, bltb_cd) MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired —
    --     bulletin-board feature not ported. Do not wire (Option C).
    when lower($1) = 'bltb_comnt_infm' and lower($2) = 'post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(post_seq_no::numeric), 0) + 1 from bltb_comnt_infm
            where 1=1
            and biz_key  = $3
            and bltb_cd = $4
        );

    -- 54. Bulletin Board (comment, own seq): BLTB_COMNT_INFM.COMNT_SEQ_NO — per (biz_key, bltb_cd)
    --     MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — bulletin-board
    --     feature not ported. Do not wire (Option C).
    when lower($1) = 'bltb_comnt_infm' and lower($2) = 'comnt_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(comnt_seq_no::numeric), 0) + 1 from bltb_comnt_infm
            where 1=1
            and biz_key  = $3
            and bltb_cd = $4
        );

    -- 55. Bulletin Board (user new-post tracker): BLTB_USER_NEW_POST_INFM.POST_SEQ_NO — per
    --     (biz_key, bltb_cd, user_id) MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10):
    --     NOT wired — bulletin-board feature not ported. Do not wire (Option C).
    when lower($1) = 'bltb_user_new_post_infm' and lower($2) = 'post_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(post_seq_no::numeric), 0) + 1 from bltb_user_new_post_infm
            where 1=1
            and biz_key  = $3
            and bltb_cd = $4
            and user_id = $5
        );

    -- 56. Bulletin Board (notice): BLTB_NOTI_INFM.NOTI_SEQ_NO — per (biz_key, bltb_cd) MAX+1.
    --     DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — notice feature not
    --     ported. Do not wire (Option C).
    when lower($1) = 'bltb_noti_infm' and lower($2) = 'noti_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(noti_seq_no::numeric), 0) + 1 from bltb_noti_infm
            where 1=1
            and biz_key  = $3
            and bltb_cd = $4
        );

    -- 57. Bulletin Board (notice content, per-language): BLTB_NOTI_CONT_INFM.NOTI_SEQ_NO — per
    --     (biz_key, lang_cd, bltb_cd) MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10):
    --     NOT wired — notice feature not ported. Do not wire (Option C).
    when lower($1) = 'bltb_noti_cont_infm' and lower($2) = 'noti_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(noti_seq_no::numeric), 0) + 1 from bltb_noti_cont_infm
            where 1=1
            and biz_key  = $3
            and lang_cd = $4
            and bltb_cd = $5
        );

    -- 58. Settings > Purpose Settings: BUSI_USAG_SET_INFM.DISP_ORD — per (biz_key, usag_grp_cd)
    --     MAX(disp_ord)+1 display order. DORMANT legacy-parity branch (Option B, 2026-06-10):
    --     NOT wired — Spring keeps its Java MAX+1 (PurposeSettingsService.findMaxDispOrd, scoped
    --     per biz_key only). JEX here scopes per (biz_key, usag_grp_cd) — a deliberate scope
    --     divergence kept Java-side for this cosmetic column. Do not wire (Option C).
    when lower($1) = 'busi_usag_set_infm' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(cast(max(disp_ord) as decimal)+1, '1') disp_ord
            from busi_usag_set_infm
            where 1 = 1
            and biz_key = $3
            and usag_grp_cd = $4
        );

    -- 59. Payment > Approval Box (approver): EXPT_RESOL_APPRVR_INFM.APPRVR_LINE_NO — per
    --     (biz_key, appr_seq_no) MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10):
    --     NOT wired — ApprovalBoxFanoutService assigns apprvr_line_no as a per-approval Java loop
    --     ordinal (setApprvrLineNo(BigDecimal.valueOf(line))). Body verbatim from JEX incl. the
    --     legacy commented-out predicate. Do not wire (Option C).
    when lower($1) = 'expt_resol_apprvr_infm' and lower($2) = 'apprvr_line_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(apprvr_line_no::numeric), 0) + 1 from expt_resol_apprvr_infm
            where 1=1
            and biz_key  = $3
            and appr_seq_no = $4
            --and apprvr_id appr_seq_no  = $5
        );

    -- 60. Payment > Card Transactions: CARD_TRNS_RCV_PAY_INFM.RCV_PAY_SEQ_NO — per
    --     (biz_key, card_seq_no) MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10): NOT
    --     wired — CardTransactionsService hardcodes RCV_PAY_SEQ_NO = 1 (one rcv-pay row per card
    --     txn: setRcvPaySeqNo(BigDecimal.ONE)). Do not wire (Option C).
    when lower($1) = 'card_trns_rcv_pay_infm' and lower($2) = 'rcv_pay_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(rcv_pay_seq_no::numeric), 0) + 1 from card_trns_rcv_pay_infm
            where 1=1
            and biz_key  = $3
            and card_seq_no = $4
        );

    -- 61. Settings > Contacts (overseas beneficiary): TRCO_OVRS_INFM.TRCO_OVRS_KEY — per biz_key
    --     MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — ContactsService
    --     takes TRCO_OVRS_KEY from the client request (OD-1 family, like trco_acct_key /
    --     trco_addr_key). Do not wire (Option C) without the OD-1 client-key decision.
    when lower($1) = 'trco_ovrs_infm' and lower($2) = 'trco_ovrs_key' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(trco_ovrs_key::numeric), 0) + 1 from trco_ovrs_infm
            where 1 = 1
            and biz_key  = $3
        );

    -- 62. Payment > Overseas (master): OVRS_PYMN_MSTR.OVRS_PYMN_NO — per-tenant plain-int MAX+1.
    --     DORMANT legacy-parity branch (Option B, 2026-06-10): NOT wired — OVRS_PYMN_MSTR has no
    --     Spring write path (the refactor's overseas batches are written to OVRS_PYMN_SUMR, whose
    --     key IS funnelled via #63 / nextOvrsPymnNo). This master-table branch is kept for verbatim
    --     JEX parity only; do not wire (Option C) unless an OVRS_PYMN_MSTR write path is ported.
    when lower($1) = 'ovrs_pymn_mstr' and lower($2) = 'ovrs_pymn_no' and coalesce(trim($3), '') <> '' then
        newSeqNo :=  (
            select coalesce(max(ovrs_pymn_no::numeric), 0) + 1 from ovrs_pymn_mstr
            where 1=1
            and biz_key  = $3
        );

    -- 63. Payment > Overseas (summary): OVRS_PYMN_SUMR.OVRS_PYMN_NO — per-tenant plain-int MAX+1
    --     over (ovrs_pymn_mstr UNION ovrs_pymn_sumr). ACTIVE (MR-2C 2026-06-10): nextOvrsPymnNo
    --     (OverseasPaymentKeyGenerator) — the overseas equivalent of the MR-1 OD-2 reversal; replaced
    --     the date-prefixed Java generator + OVERSEAS_PAYMENT_KEY_SEQ table (deleted). OVRS_PYMN_NO is
    --     the PK of OVRS_PYMN_SUMR and the FK copied onto every OVRS_PYMN_SUMR_DETL row; because the
    --     funnel is a lock-free MAX+1, OverseasPaymentExcelService.submit wraps the create in a
    --     boundary-level one-shot retry on DataIntegrityViolationException.
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

    -- 64. Settings > Purpose Settings: BUSI_USAG_SET_INFM.DISP_ORD (no group) — NULL-safe per-biz_key
    --     MAX+1. DORMANT legacy-parity branch (Option B, 2026-06-10): the no-group ($4 empty)
    --     companion to the grouped #58 (mirrors the use_usag_cd #18/#19 grouped/plain pair). NOT
    --     wired — Spring keeps its Java per-biz_key MAX+1 (PurposeSettingsService.findMaxDispOrd).
    --     Do not wire (Option C). Legacy note: "NU APARTMENT / CMC".
    when lower($1) = 'busi_usag_set_infm' and lower($2) = 'disp_ord' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') = '' then
        newSeqNo :=  (
            select coalesce((coalesce(max(disp_ord), 0) + 1)::varchar, '1')
            from busi_usag_set_infm
            where 1 = 1
            and biz_key = $3
        );

    -- All other table/field combinations: return NULL (no dead legacy-table branches).
    else
        newSeqNo := null;
    end case;

    return newSeqNo;
END;
$function$
;
