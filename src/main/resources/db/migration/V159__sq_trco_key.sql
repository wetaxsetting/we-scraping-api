-- ============================================================================
-- V159__sq_trco_key.sql
--
-- Legacy JEX sequence ported from UAT (source of truth, `wecmsuat_real`).
-- Schema prefix `wecmsuat_real.` stripped; all sequence options preserved
-- VERBATIM (MINVALUE / MAXVALUE / START / CYCLE). Source DDL:
--
--   CREATE SEQUENCE wecmsuat_real.sq_trco_key
--       MINVALUE 0
--       NO MAXVALUE
--       START 0
--       NO CYCLE;
--
-- (INCREMENT BY 1 and CACHE 1 are the PostgreSQL defaults — the source DDL
-- omits them, so they are left implicit here too.)
--
-- WHY THIS MIGRATION EXISTS — regression repair:
--   `wb_get_next_val` (V140) carries the global TRCO_KEY allocator branch
--     when lower($1) = 'trco_infm' and lower($2) = 'trco_key' then
--         newSeqNo := (select nextval('sq_trco_key'));
--   and the same `sq_trco_key` is reused by the trco_addr_infm.trco_key parity
--   branch. The CREATE SEQUENCE that backed it lived in the pre-consolidation
--   migration (archived V113/V85, `START WITH 1000000`). The 2026-06-09
--   migration renumber re-extracted every OTHER global sequence into its own
--   file (V145 sq_acct_trco_key, V146–V154, …) but DROPPED sq_trco_key — while
--   the consumer branch survived. Result: on any freshly-built DB (the shared
--   dev DB 172.168.5.172:5432/wecmsdevdb) `POST /api/v1/settings/contacts`
--   fails with `relation "sq_trco_key" does not exist`. This migration restores
--   the missing object so a fresh build is self-consistent.
--
-- TRCO_KEY is GLOBAL, not per-tenant — TRCO_INFM's PK is `(trco_key)` alone
-- (no biz_key), matching the legacy global-uniqueness contract. Do NOT make it
-- per-tenant.
--
-- Consumed by: LegacyFunctionRepository.nextTrcoKey -> ContactsService.create
-- (one-shot retry on DataIntegrityViolationException as defence-in-depth).
--
-- Retirement plan (per wecms-api/CLAUDE.md "Surviving legacy functions"):
-- drop this sequence and the `trco_infm.trco_key` wb_get_next_val branch, and
-- replace LegacyFunctionRepository.nextTrcoKey with a Java generator, once the
-- legacy WeCMS / WABOOKS systems no longer call wb_get_next_val for trco_infm.
-- No fixed deadline.
--
-- NOTE: Flyway is disabled in all wecms-api profiles (ddl-auto: validate;
-- schema is external/pre-migrated). This file is the canonical record for a
-- fresh build; the already-built shared dev DB is patched by running the same
-- statement by hand (see Claude Code (AI)/sql/sq_trco_key_20260611.sql).
--
-- Idempotent: CREATE SEQUENCE IF NOT EXISTS.
-- ============================================================================

-- sq_trco_key definition

-- DROP SEQUENCE sq_trco_key;

CREATE SEQUENCE IF NOT EXISTS sq_trco_key
	INCREMENT BY 1
	MINVALUE 0
	NO MAXVALUE
	START 0
	CACHE 1
	NO CYCLE;
