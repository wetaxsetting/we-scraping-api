-- erp_trns_hstr definition

-- Drop table

-- DROP TABLE erp_trns_hstr;

CREATE TABLE erp_trns_hstr (
	biz_key varchar(20) NOT NULL,
	seq_no numeric(10) NOT NULL,
	org_file_nm varchar(500) NOT NULL,
	save_file_nm varchar(500) NOT NULL,
	file_size numeric(10) NOT NULL,
	erp_type bpchar(1) NOT NULL,
	trns_type_cd bpchar(2) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_erp_trns_hstr PRIMARY KEY (biz_key, seq_no)
);
