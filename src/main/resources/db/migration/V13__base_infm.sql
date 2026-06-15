-- base_infm definition

-- Drop table

-- DROP TABLE base_infm;

CREATE TABLE base_infm (
	ntnl_cd varchar(2) NOT NULL,
	num_format varchar(50) NULL,
	dat_format varchar(50) NULL,
	taxid_format varchar(50) NULL,
	natn_num varchar(5) NULL,
	tel_len numeric(5) NULL,
	use_eng_trco_nm_yn bpchar(1) DEFAULT 'Y'::bpchar NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	CONSTRAINT pk_base_infm PRIMARY KEY (ntnl_cd)
);
