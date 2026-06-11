-- memo_infm_file definition

-- Drop table

-- DROP TABLE memo_infm_file;

CREATE TABLE memo_infm_file (
	biz_key varchar(20) NOT NULL,
	web_svc_id varchar(50) NOT NULL,
	seq_no numeric(10) NOT NULL,
	file_id varchar(100) NOT NULL,
	org_file_nm varchar(500) NOT NULL,
	save_file_nm varchar(500) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	file_size varchar(100) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	CONSTRAINT pk_memo_infm_file PRIMARY KEY (biz_key, web_svc_id, seq_no, file_id)
);
