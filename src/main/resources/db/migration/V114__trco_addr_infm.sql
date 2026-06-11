-- trco_addr_infm definition

-- Drop table

-- DROP TABLE trco_addr_infm;

CREATE TABLE trco_addr_infm (
	trco_key varchar(20) NOT NULL,
	biz_key varchar(20) NOT NULL,
	trco_addr_key varchar(20) NOT NULL,
	trco_bsun_post_adrs varchar(400) NULL,
	disp_ord numeric(10) NOT NULL,
	issu_dt varchar(8) NOT NULL,
	due_dt varchar(8) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_trco_addr_infm PRIMARY KEY (trco_key, biz_key, trco_addr_key)
);
