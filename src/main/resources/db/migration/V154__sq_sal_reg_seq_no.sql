-- ============================================================================
-- V154__sq_sal_reg_seq_no.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / NO CYCLE). No COMMENT in
-- the source DDL.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch, NOT referenced by any Spring Java, NOT referenced by
-- any native SQL. The sequence name `sq_sal_reg_seq_no` appears NOWHERE in the
-- codebase (not even the archive).
--
-- Note: even the legacy UAT wb_get_next_val does NOT draw from this sequence —
-- its saly_book_mstr / saly_book_mstr_new 'sal_reg_seq_no' branches use
-- per-tenant MAX+1, not nextval('sq_sal_reg_seq_no'). This sequence is
-- vestigial / serves some other legacy path outside the function. The related
-- SALY_BOOK_MSTR table is also NOT in the V1–V139 schema set. Reference-only.
-- ============================================================================

-- sq_sal_reg_seq_no definition

-- DROP SEQUENCE sq_sal_reg_seq_no;

CREATE SEQUENCE sq_sal_reg_seq_no
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;
