-- WeCMS Manual Database Change
-- Date: 2026-06-13
-- Source: migrated from Claude Code (AI)/sql/cash_flow_report_helpers_20260613.sql
-- Related record: TBD
-- Type: DDL
-- Execution: MANUAL
-- Target environments: Refactor DB (172.168.5.172:5432/wecmsdevdb)
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: pre/post-check queries are present in the original script below
-- MIGRATION REVIEW REQUIRED — this script creates a new table and/or DB function that has no counterpart in src/main/resources/db/migration/. No migration was created and no V-number was assigned during the archive migration; the placement decision is open.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- cash_flow_report_helpers_20260613.sql
-- =============================================================================
-- Purpose : Install the two chart-of-accounts level helper functions that
--           get_cashflow_report() depends on, into the refactor DB.
--
-- WHY     : The Cash Flow refactor (2026-06-13 architecture decision) now calls
--           the legacy DB function get_cashflow_report() directly as the source
--           of truth (CashFlowReportRepository -> CashFlowReportService).
--           get_cashflow_report() already exists in the refactor DB
--           (172.168.5.172:5432/wecmsdevdb), and so do wb_get_appr_stts +
--           wb_get_admin_yn. But its chart-level helpers are MISSING, so the
--           function currently errors at runtime:
--               function wb_get_chart_acct_level_text(varchar,varchar,varchar)
--               does not exist
--
-- BODIES  : Copied VERBATIM from JEX DEV (wecmsvndev.wb_get_chart_acct_level /
--           wb_get_chart_acct_level_text) with the schema qualifier removed so
--           they install into the refactor DB's default schema and resolve
--           chart_acct_infm / each other via search_path (same pattern as the
--           already-installed get_cashflow_report).
--
-- EXECUTION: Run MANUALLY by a DBA against the refactor DB. This is NOT a
--           Flyway migration and is NOT applied by the application on startup
--           (Flyway is disabled; ddl-auto=validate does not see functions).
--
-- RUN ORDER (dependency):
--   1) wb_get_chart_acct_level        (leaf — reads chart_acct_infm only)
--   2) wb_get_chart_acct_level_text   (wraps #1)
--   Both are CREATE OR REPLACE => idempotent / safe to re-run.
--
-- DEPENDS ON: table chart_acct_infm (already present in the refactor DB).
-- VERIFY AFTER RUN:
--   SELECT * FROM get_cashflow_report('VN_VNM0004_000001',2025,NULL,'VND',25000,18,NULL) LIMIT 1;
--   -- should return rows, not "function ... does not exist".
-- =============================================================================

-- 1) Numeric level resolver (leaf helper) -------------------------------------
CREATE OR REPLACE FUNCTION wb_get_chart_acct_level(
    p_biz_key character varying,
    p_chart_acct_seq_no character varying,
    p_chart_acct_cd character varying)
 RETURNS integer
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_current_cd VARCHAR(20);
    v_parent_cd VARCHAR(20);
    v_account_level INTEGER;
    v_temp_cd VARCHAR(20);
    v_temp_parent VARCHAR(20);
    v_counter INTEGER;
BEGIN
    -- Get the current account details
    SELECT chart_acct_cd,
           CASE WHEN TRIM(parent_chart_acct_cd) = '' THEN NULL ELSE parent_chart_acct_cd END
    INTO v_current_cd, v_parent_cd
    FROM chart_acct_infm
    WHERE biz_key = p_biz_key
      AND chart_acct_seq_no = p_chart_acct_seq_no
      AND chart_acct_cd = p_chart_acct_cd
      AND del_yn = 'N';

    -- If account not found
    IF v_current_cd IS NULL THEN
        RETURN 0;  -- Return 0 for not found
    END IF;

    -- Check for circular reference
    IF v_parent_cd = v_current_cd THEN
        RETURN -1;  -- Return -1 for circular reference
    END IF;

    -- If this is a root account (Level 1)
    IF v_parent_cd IS NULL OR TRIM(v_parent_cd) = '' THEN
        RETURN 1;  -- Level 1
    END IF;

    -- Determine account level by traversing up the hierarchy
    v_temp_cd := v_current_cd;
    v_account_level := 1;
    v_counter := 0;

    LOOP
        -- Get parent of current account
        SELECT CASE WHEN TRIM(parent_chart_acct_cd) = '' THEN NULL ELSE parent_chart_acct_cd END
        INTO v_temp_parent
        FROM chart_acct_infm
        WHERE biz_key = p_biz_key
          AND chart_acct_cd = v_temp_cd
          AND del_yn = 'N';

        -- If no parent found or reached root, exit
        IF v_temp_parent IS NULL THEN
            EXIT;
        END IF;

        -- Check for circular reference (parent points to itself or back to current)
        IF v_temp_parent = v_temp_cd OR v_temp_parent = v_current_cd THEN
            RETURN -1;  -- Return -1 for circular reference
        END IF;

        -- Move up one level
        v_temp_cd := v_temp_parent;
        v_account_level := v_account_level + 1;
        v_counter := v_counter + 1;

        -- Prevent infinite loop
        IF v_counter > 10 THEN
            RETURN -2;  -- Return -2 for max depth exceeded
        END IF;
    END LOOP;

    -- Return the level (should be 1-5 based on your business rule)
    IF v_account_level <= 5 THEN
        RETURN v_account_level;
    ELSE
        RETURN -3;  -- Return -3 if level exceeds maximum allowed (5)
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RETURN -99;  -- Return -99 for any other error
END;
$function$;

-- 2) Text level wrapper (used by get_cashflow_report) -------------------------
-- NOTE: legacy body called wecmsvndev.wb_get_chart_acct_level(...) — the schema
-- qualifier is stripped here so it resolves the function installed in #1.
CREATE OR REPLACE FUNCTION wb_get_chart_acct_level_text(
    p_biz_key character varying,
    p_chart_acct_seq_no character varying,
    p_chart_acct_cd character varying)
 RETURNS character varying
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_level INTEGER;
BEGIN
    -- Get the numeric level first
    v_level := wb_get_chart_acct_level(p_biz_key, p_chart_acct_seq_no, p_chart_acct_cd);

    -- Return appropriate text based on level
    CASE v_level
        WHEN 0 THEN
            RETURN 'Account not found';
        WHEN -1 THEN
            RETURN 'Circular reference detected';
        WHEN -2 THEN
            RETURN 'Max depth exceeded';
        WHEN -3 THEN
            RETURN 'Level exceeds maximum (5)';
        WHEN -99 THEN
            RETURN 'Error occurred';
        WHEN 1 THEN
            RETURN 'Level 1';
        WHEN 2 THEN
            RETURN 'Level 2';
        WHEN 3 THEN
            RETURN 'Level 3';
        WHEN 4 THEN
            RETURN 'Level 4';
        WHEN 5 THEN
            RETURN 'Level 5';
        ELSE
            RETURN 'Invalid level: ' || v_level::text;
    END CASE;

EXCEPTION
    WHEN OTHERS THEN
        RETURN 'Error: ' || SQLERRM;
END;
$function$;
