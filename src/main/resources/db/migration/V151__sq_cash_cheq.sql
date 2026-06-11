-- ============================================================================
-- V151__sq_cash_cheq.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / NO CYCLE). No COMMENT in
-- the source DDL.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's curated 10 do not include the
-- cash_cheq_mstr 'cash_cheq_seq_no' branch), NOT referenced by any Spring
-- Java, NOT referenced by any native SQL. Reference-only today.
-- Its eventual consumer also depends on the CASH_CHEQ_MSTR table, which is
-- NOT in the V1–V139 schema set.
-- (Note: V141's header comment still lists sq_cash_cheq among "intentionally
--  NOT created" — that note is now stale; V141 itself is unchanged.)
-- ============================================================================

-- sq_cash_cheq definition

-- DROP SEQUENCE sq_cash_cheq;

CREATE SEQUENCE sq_cash_cheq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;
