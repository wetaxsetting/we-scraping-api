-- busi_usag_set_infm_lang_detl definition

-- Drop table

-- DROP TABLE busi_usag_set_infm_lang_detl;

CREATE TABLE busi_usag_set_infm_lang_detl (
	biz_key varchar(20) NOT NULL,
	use_usag_cd varchar(20) NOT NULL,
	lang_code varchar(2) NOT NULL,
	usag_nm varchar(100) NULL,
	usag_cont text NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	rept_dv_nm varchar(100) NULL,
	rept_grp_nm varchar(100) NULL,
	CONSTRAINT pk_busi_usag_set_infm_lang_detl PRIMARY KEY (biz_key, use_usag_cd, lang_code)
);
