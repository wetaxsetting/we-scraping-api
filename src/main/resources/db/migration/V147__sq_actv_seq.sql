-- ============================================================================
-- V147__sq_actv_seq.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / NO CYCLE). No COMMENT in
-- the source DDL.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's curated 10 do not include the
-- trco_actv_hstr 'actv_seq' branch), NOT referenced by any native SQL.
--
-- IMPORTANT: the target table TRCO_ACTV_HSTR *does* exist (V113) and *is*
-- written by live Spring code (EInvoiceTrcoAutoRegistrar), BUT that writer
-- deliberately generates ACTV_SEQ via a Java MAX+1 @Query in
-- TrcoActvHstrRepository.nextActvSeq() and explicitly does NOT use this
-- sequence (see the repository Javadoc). So this sequence is bypassed by
-- design and nothing draws from it. Reference-only today.
-- ============================================================================

-- sq_actv_seq definition

-- DROP SEQUENCE sq_actv_seq;

CREATE SEQUENCE sq_actv_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;
