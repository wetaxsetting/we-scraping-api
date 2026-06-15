-- expt_resol_file definition

-- Drop table

-- DROP TABLE expt_resol_file;

CREATE TABLE expt_resol_file (
	biz_key varchar(20) NOT NULL,
	appr_seq_no varchar(15) NOT NULL,
	file_id varchar(100) NOT NULL,
	org_file_nm varchar(500) NOT NULL,
	save_file_nm varchar(500) NOT NULL,
	note varchar(255) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	reg_nm varchar(100) NOT NULL,
	file_size varchar(100) NULL,
	CONSTRAINT expt_resol_file_pk PRIMARY KEY (biz_key, appr_seq_no, file_id),
	CONSTRAINT expt_resol_file_fk01 FOREIGN KEY (biz_key,appr_seq_no) REFERENCES expt_resol_mstr(biz_key,appr_seq_no)
);
