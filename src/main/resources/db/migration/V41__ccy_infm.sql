-- ccy_infm definition

-- Drop table

-- DROP TABLE ccy_infm;

CREATE TABLE ccy_infm (
	ccy_cd bpchar(3) NOT NULL,
	ccy_sym bpchar(1) NULL,
	use_yn varchar(1) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	CONSTRAINT pk_ccy_infm PRIMARY KEY (ccy_cd)
);
