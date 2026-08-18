-- WeCMS Manual Database Change
-- Date: 2026-06-11
-- Source: migrated from Claude Code (AI)/sql/daily_cash_rept_daily_sumr_20260611.sql
-- Related record: TBD
-- Type: DDL
-- Execution: MANUAL
-- Target environments: Refactor DEV DB (172.168.5.172/wecmsdevdb)
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
-- MIGRATION REVIEW REQUIRED — this script creates a new table and/or DB function that has no counterpart in src/main/resources/db/migration/. No migration was created and no V-number was assigned during the archive migration; the placement decision is open.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- daily_cash_rept_daily_sumr_20260611.sql
-- =============================================================================
-- Purpose : Create the ONE remaining missing table from the acct_mstr trigger
--           dependency closure on the REFACTOR DEV DB (172.168.5.172/wecmsdevdb).
--
-- Context : Follow-up to acct_mstr_trigger_deps_20260611.sql. After that script,
--           the 5 functions (wb_get_amt, rept_0900_001_rec1..rec4) and table
--           cash_repy_mstr were created successfully (all in schema wecmsdevdb and
--           resolving), but section 3 (this table) did NOT get applied — the next
--           POST /api/v1/banking/accounts/register failed with:
--             ERROR: relation "daily_cash_rept_daily_sumr" does not exist
--             Where: PL/pgSQL function daily_cash_rept_daily_sumr_detl_log() line 28
--
-- This is the LAST object in the cascade: confirmed (2026-06-11) that
-- daily_cash_rept_daily_sumr has NO triggers on JEX or refactor, so once it exists
-- the acct_mstr INSERT chain completes.
--
-- BASELINE NOTE: daily_cash_rept_daily_sumr is NOT in the approved 141-table
--   legacy-table-inventory.md. Adding it requires PO / Mr. Jung sign-off (same gate
--   already flagged for cash_repy_mstr). DDL below is a verbatim copy of live JEX.
--
-- Execution: DBA-run by hand against 172.168.5.172/wecmsdevdb. NOT Flyway.
--   Idempotent (CREATE TABLE IF NOT EXISTS). Creates an empty table; touches no rows.
--   IMPORTANT: ensure this statement actually executes — in the prior run this exact
--   block was skipped. Run it on its own and confirm the verification query returns 1.
--
-- Author  : Claude Code (AI)  |  Date: 2026-06-11
-- =============================================================================

\set ON_ERROR_STOP on

-- Match the schema that owns the legacy triggers + the other restored objects.
SET search_path TO wecmsdevdb;

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

-- VERIFY (expect non-null):
--   SELECT to_regclass('wecmsdevdb.daily_cash_rept_daily_sumr');
-- Then re-run POST /api/v1/banking/accounts/register; the 500 WBE_DB should clear.
-- =============================================================================
