-- trco_ovrs_infm definition

-- Drop table

-- DROP TABLE trco_ovrs_infm;

CREATE TABLE trco_ovrs_infm (
	trco_key varchar(20) NOT NULL,
	biz_key varchar(20) NOT NULL,
	trco_ovrs_key varchar(20) NOT NULL,
	bene_name varchar(200) NULL,
	bic_code varchar(50) NULL,
	bene_acct_no varchar(100) NULL,
	bene_addr varchar(500) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	bene_bank_code varchar(20) NULL,
	bene_bank_name varchar(200) NULL,
	CONSTRAINT pk_trco_ovrs_infm PRIMARY KEY (trco_key, biz_key, trco_ovrs_key)
);
