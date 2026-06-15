-- ============================================================================
-- V152__sq_cash_repy.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / NO CYCLE). No COMMENT in
-- the source DDL.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's curated 10 do not include the
-- cash_repy_mstr 'cash_repy_seq_no' branch), NOT referenced by any Spring
-- Java, NOT referenced by any native SQL. Reference-only today.
--
-- Context: backs cash_repy_mstr.cash_repy_seq_no. The CASH_REPY_MSTR table is
-- explicitly OUT of the refactor PROD active-table scope (see
-- DailyCashReportService / DailyCashRecomputeRepository comments) and has no
-- CREATE TABLE in V1–V139 — though V4's acct_trns_rcv_pay_infm_fk02 FK
-- references it (resolves only on the live DB where the legacy table exists).
-- No live Spring writer allocates cash_repy_seq_no via this sequence.
-- ============================================================================

-- sq_cash_repy definition

-- DROP SEQUENCE sq_cash_repy;

CREATE SEQUENCE sq_cash_repy
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;
