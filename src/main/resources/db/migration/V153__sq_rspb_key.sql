-- ============================================================================
-- V153__sq_rspb_key.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / NO CYCLE). No COMMENT in
-- the source DDL.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's curated 10 do not include the
-- trco_rspb_infm 'rspb_key' branch), NOT referenced by any Spring Java
-- (TRCO_RSPB_INFM is explicitly "neither mapped nor in the PROD active list"
-- per EInvoiceTrcoAutoRegistrar / EInvoiceDetailScrapeService comments),
-- NOT referenced by any native SQL. Reference-only today.
-- Its eventual consumer also depends on the TRCO_RSPB_INFM table, which is
-- NOT in the V1–V139 schema set.
-- (Note: V141's header comment still lists sq_rspb_key among "intentionally
--  NOT created" — that note is now stale; V141 itself is unchanged.)
-- ============================================================================

-- sq_rspb_key definition

-- DROP SEQUENCE sq_rspb_key;

CREATE SEQUENCE sq_rspb_key
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;
