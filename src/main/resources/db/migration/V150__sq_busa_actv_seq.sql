-- ============================================================================
-- V150__sq_busa_actv_seq.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / NO CYCLE). No COMMENT in
-- the source DDL.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's curated 10 do not include the
-- busi_doc_vas_item_actv_his 'busa_actv_seq' branch), NOT referenced by any
-- Spring Java, NOT referenced by any native SQL. Reference-only today.
-- Its eventual consumer also depends on the BUSI_DOC_VAS_ITEM_ACTV_HIS table,
-- which is NOT in the V1–V139 schema set.
-- ============================================================================

-- sq_busa_actv_seq definition

-- DROP SEQUENCE sq_busa_actv_seq;

CREATE SEQUENCE sq_busa_actv_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;
