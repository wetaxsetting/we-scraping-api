-- bank_cert_info definition

-- Drop table

-- DROP TABLE bank_cert_info;

CREATE TABLE bank_cert_info (
	biz_key varchar(20) NOT NULL,
	seq_no numeric(10) NOT NULL,
	bank_cd varchar(20) NOT NULL,
	cert_info_dv varchar(1) NOT NULL,
	cert_info_con varchar(2000) NOT NULL,
	cert_info_detl_con varchar(2000) NULL,
	cert_info_detl_con2 varchar(2000) NULL,
	acct_list_inq_stts varchar(20) NULL,
	acct_list_inq_msg varchar(255) NULL,
	lst_acct_list_inq_dt varchar(8) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	nxisas_gb_seq_no numeric(10) DEFAULT '0'::numeric NOT NULL,
	cert_info_detl_con3 varchar(2000) NULL,
	CONSTRAINT bank_cert_info_pk PRIMARY KEY (biz_key, seq_no),
	CONSTRAINT bank_cert_info_uk01 UNIQUE (biz_key, bank_cd, cert_info_dv, cert_info_con, nxisas_gb_seq_no),
	CONSTRAINT bank_cert_info_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key)
);
