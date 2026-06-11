-- ============================================================================
-- V145__sq_acct_trco_key.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / NO CYCLE). No COMMENT in
-- the source DDL.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's curated 10 do not include the
-- acct_trco_infm 'acct_trco_key' branch), NOT referenced by any Spring Java,
-- NOT referenced by any native SQL. Reference-only today. Its eventual
-- consumer (the acct_trco_infm branch) also depends on the ACCT_TRCO_INFM
-- table, which is NOT in the V1–V139 schema set.
-- ============================================================================

-- sq_acct_trco_key definition

-- DROP SEQUENCE sq_acct_trco_key;

CREATE SEQUENCE sq_acct_trco_key
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;
