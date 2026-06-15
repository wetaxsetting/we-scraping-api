-- gdt_cert_info definition

-- Drop table

-- DROP TABLE gdt_cert_info;

CREATE TABLE gdt_cert_info (
	biz_key varchar(20) NOT NULL,
	seq_no numeric(10) NOT NULL,
	cert_info_dv varchar(1) NOT NULL,
	cert_info_con varchar(2000) NOT NULL,
	cert_info_detl_con varchar(2000) NULL,
	cert_info_detl_con2 varchar(2000) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_gdt_cert_info PRIMARY KEY (biz_key, seq_no)
);
