-- ============================================================================
-- V26__wb_get_next_val_scope_trim.sql
--
-- Corrective migration: replaces the over-broad V25 full DEV port of
-- wb_get_next_val (164 branches) with a scope-minimal version that only
-- includes the branches actually required by the current JEX-to-Spring
-- refactor scope.
--
-- Rule (added to CLAUDE.md): never port full legacy common DB functions
-- blindly. Add only the branches needed by the migrated-table scope.
--
-- Decision (2026-04-30):
--   - V25 ported all 164 DEV branches. That was wrong — it pulled in
--     business logic (key-generation patterns) for tables that don't
--     exist locally and aren't in scope, making the function a hidden
--     dependency catalogue rather than a targeted utility.
--   - Sprint 1 endpoints are entirely read-only. No new rows. No
--     key-generation needed for Sprint 1 tables.
--   - The only branches needed today are the two Bootstrap branches that
--     create BUSI_INFM (company key) and EMPL_INFM (employee number) rows
--     during Phase 1 bootstrap seeding. These were in V11 and stay in scope.
--   - All other branches are removed. When a Sprint 2+ feature needs a
--     generator for a specific table, that branch is added then — after
--     confirming the table is migrated and the service creates new rows.
--
-- What this migration does:
--   1. Drops the 11 sequences V25 created that are not needed in scope.
--      (sq_biz_key was in V11 and is kept; the other 11 are new from V25.)
--   2. Replaces public.wb_get_next_val with the 2-branch version.
--
-- Source of truth for branch logic: wecmsvndev.wb_get_next_val (2026-04-30).
-- Branch bodies are copied verbatim from DEV for the two in-scope branches.
-- ============================================================================

-- --------------------------------------------------------------------------
-- 1. Drop the 11 sequences V25 created that are not needed in current scope.
--    sq_biz_key was created by V11 and is kept.
-- --------------------------------------------------------------------------
DROP SEQUENCE IF EXISTS sq_cash_cheq;
DROP SEQUENCE IF EXISTS sq_cash_repy;
DROP SEQUENCE IF EXISTS sq_rspb_key;
DROP SEQUENCE IF EXISTS sq_actv_seq;
DROP SEQUENCE IF EXISTS sq_trco_key;
DROP SEQUENCE IF EXISTS sq_appr_seq_no;
DROP SEQUENCE IF EXISTS sq_acct_trns_vas_actv_seq;
DROP SEQUENCE IF EXISTS firm_seq_no;
DROP SEQUENCE IF EXISTS firm_file_seq_no;
DROP SEQUENCE IF EXISTS sq_acct_trco_key;
DROP SEQUENCE IF EXISTS sq_busa_actv_seq;

-- --------------------------------------------------------------------------
-- 2. Replace the function with the 2-branch scope-minimal version.
--    CREATE OR REPLACE is idempotent — safe to re-run.
-- --------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.wb_get_next_val(
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
    newSeqNo text := null;
    bizKey   text := null;
BEGIN
    case
    -- -----------------------------------------------------------------------
    -- Branch 1: BUSI_INFM.BIZ_KEY
    -- Used by: Phase 1 bootstrap (CompanyBootstrapService via
    --   LegacyFunctionRepository). Generates NTNL_CD_CHNL_ID_NNNNNN format.
    -- -----------------------------------------------------------------------
    when lower($1) = 'busi_infm' and lower($2) = 'biz_key'
         and coalesce(trim($3), '') <> ''
         and coalesce(trim($4), '') <> ''
    then
        newSeqNo := (
            select $3 || '_' || $4 || '_' ||
                   lpad(cast(coalesce(max(right(biz_key, 6)::numeric), 0) + 1 as text), 6, '0')
            from busi_infm
            where left(biz_key, 10) = $3 || '_' || $4
        );

    -- -----------------------------------------------------------------------
    -- Branch 2: EMPL_INFM.EMPL_NO
    -- Used by: Phase 1 bootstrap (EmployeeNumberGeneratorService via
    --   LegacyFunctionRepository). Generates YYYYMMDD_NNN format.
    -- -----------------------------------------------------------------------
    when lower($1) = 'empl_infm' and lower($2) = 'empl_no'
         and coalesce(trim($3), '') <> ''
    then
        bizKey := $3;
        newSeqNo := (
            select to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text) || '_' ||
                   lpad((count(*) + 1)::text, 3, '0')
            from empl_infm
            where biz_key = bizKey
              and left(empl_no, 8) = to_char(timezone('utc'::text, now()), 'YYYYMMDD'::text)
        );

    -- -----------------------------------------------------------------------
    -- All other table/field combinations: return NULL.
    -- A new branch is added here ONLY when:
    --   (a) the table is migrated to local Flyway,
    --   (b) the Spring service creates new rows in that table, AND
    --   (c) the PO/lead explicitly confirms the branch is needed.
    -- Do NOT add branches preemptively.
    -- -----------------------------------------------------------------------
    else
        newSeqNo := null;
    end case;

    return newSeqNo;
END;
$function$;
