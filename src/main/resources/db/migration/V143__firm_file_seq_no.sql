-- ============================================================================
-- V143__firm_file_seq_no.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / CYCLE) and the original
-- COMMENT retained.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's curated 10 do not include the
-- tb_pay_file_mst / tb_pay_file_data 'file_seq_no' branches), NOT referenced
-- by any Spring Java, NOT referenced by any native SQL. Reference-only today.
-- ============================================================================

-- firm_file_seq_no definition

-- DROP SEQUENCE firm_file_seq_no;

CREATE SEQUENCE firm_file_seq_no
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9999999
	START 1
	CACHE 1
	CYCLE;
COMMENT ON SEQUENCE firm_file_seq_no IS '펌뱅킹 일괄이체 파일 기초 데이터 생성시 시퀀스 채번';
