-- usag_infm_lang_detl definition

-- Drop table

-- DROP TABLE usag_infm_lang_detl;

CREATE TABLE usag_infm_lang_detl (
	use_usag_cd varchar(20) NOT NULL,
	lang_code varchar(2) NOT NULL,
	usag_nm varchar(100) NULL,
	usag_cont text NULL,
	CONSTRAINT pk_usag_infm_lang_detl PRIMARY KEY (use_usag_cd, lang_code)
);
