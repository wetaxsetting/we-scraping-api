-- busi_usag_set_infm definition

-- Drop table

-- DROP TABLE busi_usag_set_infm;

CREATE TABLE busi_usag_set_infm (
	biz_key varchar(20) NOT NULL,
	use_usag_cd varchar(20) NOT NULL,
	use_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL,
	disp_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL,
	disp_ord numeric(10) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	usag_grp_cd varchar(3) NOT NULL,
	rept_dv varchar(2) NULL,
	rept_grp varchar(2) NULL,
	usag_type bpchar(2) NULL,
	hand_use_usag_cd varchar(20) NULL,
	-- Present on live JEX (verified DEV 2026-06-09) but absent from the pasted UAT DDL; added to match the live schema + the BusiUsagSet entity.
	parent_use_usag_cd varchar(20) NULL,
	use_usag_nm varchar(255) NULL,
	use_usag_cont varchar(500) NULL,
	CONSTRAINT busi_usag_set_infm_pk PRIMARY KEY (biz_key, use_usag_cd)
);
