-- WeCMS Manual Database Change
-- Date: 2026-06-11
-- Source: migrated from Claude Code (AI)/sql/sq_trco_key_20260611.sql
-- Related record: src/main/resources/db/migration/V159__sq_trco_key.sql (the Flyway-set equivalent of the same object)
-- Type: DDL
-- Execution: MANUAL
-- Target environments: Any environment whose wb_get_next_val references sq_trco_key but where the sequence is absent (authored for 172.168.5.172:5432/wecmsdevdb)
-- Executor: DBA TEAM
-- Rollback: Reversible with DROP SEQUENCE, but wb_get_next_val's trco_infm branch then fails again
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
-- COLLISION NOTE — this is the by-hand equivalent of src/main/resources/db/migration/V159__sq_trco_key.sql (ALREADY GOVERNED). Flyway is disabled, so V159 does not auto-apply; this script applies the same object manually. No duplicate migration was created.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- sq_trco_key_20260611.sql  —  MANUAL DBA SCRIPT (run by hand, NOT by Flyway)
--
-- Purpose : Repair the runtime error
--             ERROR: relation "sq_trco_key" does not exist
--             (PL/pgSQL function wb_get_next_val ... line 76 at assignment)
--           hit by POST /api/v1/settings/contacts on the shared refactor dev DB
--           172.168.5.172:5432/wecmsdevdb (database name wecmsdevdb).
--
-- Cause   : wb_get_next_val carries the global TRCO_KEY allocator branch
--             when lower($1)='trco_infm' and lower($2)='trco_key' then
--                 newSeqNo := (select nextval('sq_trco_key'));
--           but the sq_trco_key sequence was lost in the 2026-06-09 migration
--           consolidation. Every other global sequence (sq_acct_trco_key, etc.)
--           was re-extracted; sq_trco_key was not. Canonical repair shipped as
--           Flyway migration V159__sq_trco_key.sql, but Flyway is DISABLED in
--           wecms-api (ddl-auto: validate, schema pre-migrated) — so it does NOT
--           auto-apply. This script applies the same object by hand.
--
-- Definition: VERBATIM from the UAT source of truth (wecmsuat_real.sq_trco_key)
--             provided 2026-06-11 — START 0 / MINVALUE 0 / NO MAXVALUE / NO CYCLE.
--             INCREMENT 1 and CACHE 1 are PostgreSQL defaults.
--
-- TRCO_KEY is GLOBAL (TRCO_INFM PK is (trco_key) alone) — do NOT scope per tenant.
--
-- Run order : standalone — no dependencies. Run once per environment whose
--             wb_get_next_val references sq_trco_key but where the sequence is
--             absent. Safe to re-run (IF NOT EXISTS — no-op if already present).
--
-- Run as    : the wecmsdevdb owner / DBA. Adjust the schema qualifier to match
--             where wb_get_next_val resolves `sq_trco_key` on the target server
--             (unqualified here relies on the function's search_path, same as
--             the existing sq_acct_trco_key et al.).
--
-- DO NOT touch any existing legacy rows or other sequences.
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS sq_trco_key
	INCREMENT BY 1
	MINVALUE 0
	NO MAXVALUE
	START 0
	CACHE 1
	NO CYCLE;

-- Verify:
--   SELECT last_value, is_called FROM sq_trco_key;
--   SELECT wb_get_next_val('trco_infm','trco_key',NULL,NULL,NULL);  -- expect '0' then '1', '2', ...
