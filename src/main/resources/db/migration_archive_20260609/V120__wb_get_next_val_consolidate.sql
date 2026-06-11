-- ============================================================================
-- V120__wb_get_next_val_consolidate.sql
--
-- Consolidate every `wb_get_next_val` branch added since V27 into one
-- definitive `CREATE OR REPLACE FUNCTION` body. Fixes a project-wide
-- regression where each "add one feature branch" migration silently
-- dropped pre-existing branches because every redefinition was authored
-- against an outdated snapshot.
--
-- ROOT CAUSE
-- ----------
-- `wb_get_next_val` is a single PL/pgSQL function shared by every
-- refactor feature that needs sequence-style allocation. Each branch
-- addition was shipped as a `CREATE OR REPLACE FUNCTION` that replaces
-- the entire body. Authors copy-pasted from earlier-but-not-latest
-- migrations, so branches accumulated lossy diffs:
--
--   V66  added audit_logs_infm
--   V82  added busi_usag_set_infm
--   V91  added appr_line_infm
--   V95  added chart_acct_infm
--   V99  added trco_info_file              ✓ retained all earlier
--   V108 added bank_exch_rate_infm         ✗ dropped audit/appr_line/
--                                              chart_acct/trco_info_file/
--                                              busi_usag_set_infm
--   V113 added trco_infm + restored audit  ✗ still missing appr_line/
--                                              chart_acct/trco_info_file/
--                                              bank_exch_rate_infm
--
-- Whichever snapshot survives in a developer's local DB depends on
-- migration apply order. The Login Credentials audit-fan-out hit the
-- gap and surfaced the issue (audit_logs_infm branch missing →
-- `wb_get_next_val` returned NULL → AuditLogService swallowed the
-- failure per design → no audit rows written).
--
-- WHAT V120 DOES
-- --------------
-- One `CREATE OR REPLACE FUNCTION` containing the UNION of every branch
-- from V66 / V82 / V91 / V95 / V99 / V108 / V113 — 20 branches total.
-- Each branch body is preserved VERBATIM from its source migration so
-- behaviour is identical to "all migrations applied to V113" — just
-- with the missing branches restored.
--
-- Branch inventory (table_name → source migration):
--   audit_logs_infm          → V66  (cross-feature, AuditLogService)
--   busi_usag_set_infm       → V82  (Purpose)
--   appr_line_infm           → V91  (Approval Line)
--   chart_acct_infm          → V95  (Chart of Accounts)
--   trco_info_file           → V99  (Contacts file upload)
--   bank_exch_rate_infm      → V108 (Exchange Rates)
--   trco_infm                → V85  (Contacts, via sq_trco_key sequence)
--   busi_infm                → V11  (Sprint 0 / CompanyBootstrapService)
--   empl_infm                → V11  (Sprint 0 / EmployeeNumberGeneratorService)
--   acct_mstr                → V27  (Sprint 1)
--   acct_trns_hstr           → V27
--   acct_trns_rcv_pay_infm   → V27
--   acct_trns_rcv_pay_evid_file → V27
--   busa_doc_rcv_pay_his     → V27
--   busa_doc_mstr            → V27
--   busi_cond_daily_sumr_detl → V27
--   einv_fee_detl            → V27
--   einv_tax_detl            → V27
--   napas_remit_trns_hstr    → V27
--   ext_tran_call_his        → V27
--
-- Idempotent: pure `CREATE OR REPLACE FUNCTION`. Safe to re-run.
--
-- FUTURE BRANCH ADDITIONS
-- -----------------------
-- The right way to add a new branch from now on is to copy THIS file's
-- body, splice the new `when ... then ... ` clause into the appropriate
-- alphabetical / sprint slot, and ship the migration as V121+. Do NOT
-- copy from an older `wb_get_next_val_*.sql` (V66 / V82 / V91 / V95 /
-- V99 / V108 / V113) — those snapshots are now retired and reference
-- branches that have moved.
--
-- A future hardening step would be to retire the legacy function
-- entirely and use Java-side per-feature MAX+1 generators (or proper
-- PostgreSQL SEQUENCE objects). Each call site is independently
-- replaceable; the function would shrink one branch at a time until
-- it can be DROP'd.
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
    -- Powers the shared AuditLogService.append() seq_no allocation for
    -- every CRUD-with-audit feature (firm-banking-settings, contacts,
    -- chart-of-accounts, login-credentials, ...). Untenanted MAX+1
    -- per DEV parity; PK on (biz_key, audit_logs_seq_no, wsvc_id,
    -- lang_cd) keeps inserts unique.
    when lower($1) = 'audit_logs_infm' and lower($2) = 'audit_logs_seq_no' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' and coalesce(trim($5), '') <> '' then
            newSeqNo := (
                select cast(coalesce(max(audit_logs_seq_no::numeric), 0) + 1 as text)
                from audit_logs_infm
            );

    -- Settings > Contacts: TRCO_INFO_FILE.FILE_ID — V99 (scope expanded 2026-05-13).
    -- Per-contact MAX+1 on trco_key. Mirrors legacy
    -- SeqHelper.getNextVal(util, idoCon, TrcoInfoFile.class, "file_id", trcoKey).
    when lower($1) = 'trco_info_file' and lower($2) = 'file_id' and coalesce(trim($3), '') <> '' then
            trcoKey := $3;
            newSeqNo := (
                select cast(coalesce(max(file_id::numeric), 0) + 1 as text)
                from trco_info_file
                where 1=1
                  and trco_key = trcoKey
            );

    -- Settings > Chart of Accounts: CHART_ACCT_INFM.CHART_ACCT_SEQ_NO — V95.
    when lower($1) = 'chart_acct_infm' and lower($2) = 'chart_acct_seq_no' and coalesce(trim($3), '') <> '' then
            newSeqNo := (
                select cast(coalesce(max(chart_acct_seq_no::numeric), 0) + 1 as text)
                from chart_acct_infm
                where 1=1
                  and biz_key = $3
            );

    -- Settings > Approval Line: APPR_LINE_INFM.APPR_LINE_NO — V91 (OQ-7 resolved 2026-05-13).
    when lower($1) = 'appr_line_infm' and lower($2) = 'appr_line_no' and coalesce(trim($3), '') <> '' then
            newSeqNo := (
                select cast(coalesce(max(appr_line_no::numeric), 0) + 1 as text)
                from appr_line_infm
                where 1=1
                  and biz_key = $3
            );

    -- Settings > Contacts: TRCO_INFM.TRCO_KEY — V85 (OQ-4 decided 2026-05-12).
    -- Delegates to the local sq_trco_key sequence. Param 3+ ignored
    -- on DEV — refactor passes NULLs.
    when lower($1) = 'trco_infm' and lower($2) = 'trco_key' then
            newSeqNo := (select nextval('sq_trco_key'));

    -- Settings > Purpose: BUSI_USAG_SET_INFM.USE_USAG_CD — V82 (OQ-3).
    -- Tenant-scoped 10-char zero-padded numeric. Restricts to the
    -- legacy generated-id space (^0[0-9]{9}$) so it never collides
    -- with the legacy admin-seeded codes that use other prefixes.
    when lower($1) = 'busi_usag_set_infm' and lower($2) = 'use_usag_cd' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') = '' then
            newSeqNo := (
                select coalesce(lpad((max(use_usag_cd)::numeric + 1)::varchar, 10, '0'),
                                '0000000001')
                from busi_usag_set_infm
                where biz_key = $3
                  and use_usag_cd ~ '^0[0-9]{9}$'
            );

    -- Banking > Exchange Rates: BANK_EXCH_RATE_INFM.EXCH_SEQ_NO — V108.
    -- Tenant-scoped MAX+1. Caller:
    -- ExchangeRateBatchService.fetchAndSaveExchangeRates().
    when lower($1) = 'bank_exch_rate_infm' and lower($2) = 'exch_seq_no' and coalesce(trim($3), '') <> '' then
            newSeqNo := (
                select cast(coalesce(max(exch_seq_no::numeric), 0) + 1 as text)
                from bank_exch_rate_infm
                where biz_key = $3
            );

    -- Sprint 0: BUSI_INFM.BIZ_KEY — Phase 1 bootstrap (CompanyBootstrapService).
    when lower($1) = 'busi_infm' and lower($2) = 'biz_key' and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
          newSeqNo :=(
                select $3||'_'|| $4 ||'_'||lpad(cast(coalesce(max(right(BIZ_KEY, 6)::numeric), 0) + 1 as text), 6, '0') from busi_infm
                where  left(biz_key,10) = $3||'_'|| $4
        );

    -- Sprint 0: EMPL_INFM.EMPL_NO — Phase 1 bootstrap (EmployeeNumberGeneratorService).
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
    -- To add a new branch: extend THIS file's body (not an older
    -- snapshot), splice the new `when ... then ...` clause into the
    -- appropriate slot, and ship as V121+ via copy of THIS migration.
    else
        newSeqNo := null;
    end case;

    return newSeqNo;
END;
$function$;
