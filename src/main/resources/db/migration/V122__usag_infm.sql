-- usag_infm definition

-- Drop table

-- DROP TABLE usag_infm;

CREATE TABLE usag_infm (
	use_usag_cd varchar(20) NOT NULL,
	usag_grp_cd varchar(3) NOT NULL,
	rept_dv varchar(2) NULL,
	rept_grp varchar(2) NULL,
	acct_cd varchar(5) NULL,
	acct_nm varchar(50) NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	usag_type bpchar(2) NULL,
	hand_use_usag_cd varchar(20) NULL,
	CONSTRAINT usag_infm_pk PRIMARY KEY (use_usag_cd)
);
