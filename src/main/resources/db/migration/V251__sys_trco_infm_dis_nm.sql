-- sys_trco_infm_dis_nm definition

-- Drop table

-- DROP TABLE sys_trco_infm_dis_nm;

CREATE TABLE sys_trco_infm_dis_nm (
	seq_no numeric(10) NOT NULL,
	abbr_nm varchar(50) NULL,
	abbr_ful_nm varchar(100) NULL,
	abbr_ggl_ful_nm varchar(100) NULL,
	disp_yn bpchar(1) NOT NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	CONSTRAINT sys_trco_infm_dis_nm_pk PRIMARY KEY (seq_no)
);