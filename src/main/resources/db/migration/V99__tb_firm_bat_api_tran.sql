-- tb_firm_bat_api_tran definition

-- Drop table

-- DROP TABLE tb_firm_bat_api_tran;

CREATE TABLE tb_firm_bat_api_tran (
	tran_dt varchar(8) NOT NULL,
	biz_key varchar(20) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	tran_seq varchar(10) NOT NULL,
	bank_prtn_id varchar(20) NOT NULL,
	api_tran_tp bpchar(5) NOT NULL,
	tran_tm bpchar(6) NOT NULL,
	firm_file_nm varchar(50) NOT NULL,
	bat_cnt numeric(7) NOT NULL,
	bat_amt numeric(23, 2) NOT NULL,
	reg_rslt_cd varchar(4) NULL,
	reg_rslt_msg varchar(150) NULL,
	rs_cd varchar(4) NULL,
	rs_msg varchar(200) NULL,
	rs_dttm varchar(14) NULL,
	CONSTRAINT pk_tb_firm_bat_api_tran PRIMARY KEY (tran_dt, biz_key, tran_seq, api_tran_tp, out_bank_cd, bank_prtn_id)
);
