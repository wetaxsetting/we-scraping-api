-- ============================================================================
-- V144__firm_seq_no.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / CYCLE) and the original
-- COMMENT retained.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's curated 10 do not include the
-- tb_pay_target / tb_firm_tran 'tran_seq' branches), NOT referenced by any
-- Spring Java, NOT referenced by any native SQL. Reference-only today.
-- (Note: V141's header comment still lists firm_seq_no among "intentionally
--  NOT created" — that note is now stale; V141 itself is unchanged.)
-- ============================================================================

-- firm_seq_no definition

-- DROP SEQUENCE firm_seq_no;

CREATE SEQUENCE firm_seq_no
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9999999999
	START 1
	CACHE 1
	CYCLE;
COMMENT ON SEQUENCE firm_seq_no IS '펌뱅킹 거래에 사용할 거래일련번호(시퀀스) 채번';
