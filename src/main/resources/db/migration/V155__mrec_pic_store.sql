-- mrec_pic_store definition
--
-- NOTE — approved scope divergence (2026-06-10, lead: Sonny): mrec_pic_store is
-- NOT in the PROD Active Table List, but busa_doc_mstr (V23) carries
--   busa_doc_mstr_fk04 FOREIGN KEY (biz_key, reg_dt, seq_no)
--     REFERENCES mrec_pic_store(biz_key, reg_dt, seq_no)
-- Added so a manual topological (DBeaver) build can satisfy that FK. DDL sourced
-- verbatim from the live wecmsdevdb schema; the `wecmsdevdb.` schema prefix is
-- stripped so the unqualified V23 FK resolves via search_path like every other
-- migration. No JPA entity / repository is mapped for this table (out of scope).
--
-- WARNING — version ordering: this file is V155, which runs AFTER V23 under
-- Flyway's version order, so it does NOT make a clean `flyway migrate` succeed
-- (V23 still fails first with `relation "mrec_pic_store" does not exist`). It is
-- effective ONLY when migrations are executed manually in dependency order
-- (Phase 1, before busa_doc_mstr).

-- Drop table

-- DROP TABLE mrec_pic_store;

CREATE TABLE mrec_pic_store (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	reg_dt varchar(8) NOT NULL,
	seq_no numeric(10) NOT NULL,
	write_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT mrec_pic_store_pk PRIMARY KEY (biz_key, reg_dt, seq_no)
);
