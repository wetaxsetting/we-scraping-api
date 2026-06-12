-- tb_firm_file_mst definition

-- Drop table

-- DROP TABLE tb_firm_file_mst;

CREATE TABLE tb_firm_file_mst (
	tran_dt varchar(8) NOT NULL,
	biz_key varchar(20) NOT NULL,
	file_seq_no varchar(7) NOT NULL,
	out_bank_cd varchar(15) NULL,
	bank_prtn_id varchar(20) NULL,
	van_org_cd varchar(20) NULL,
	file_type bpchar(3) NULL,
	wdrl_act_no varchar(100) NULL,
	fund_tp bpchar(1) NULL,
	tran_tp bpchar(1) NULL,
	file_seq varchar(3) NULL,
	firm_file_nm varchar(50) NOT NULL,
	proc_step bpchar(1) NULL,
	proc_stat bpchar(1) NOT NULL,
	req_cnt numeric(7) NULL,
	req_amt numeric(23, 2) NULL,
	reg_cnt numeric(7) NULL,
	reg_amt numeric(23, 2) NULL,
	reg_rslt_cd varchar(4) NULL,
	reg_rslt_msg varchar(150) NULL,
	succ_cnt numeric(7) NULL,
	succ_amt numeric(23, 2) NULL,
	unproc_cnt numeric(7) NULL,
	unproc_amt numeric(23, 2) NULL,
	reg_dttm varchar(14) NULL,
	proc_dttm varchar(14) NULL,
	CONSTRAINT pk_tb_firm_file_mst PRIMARY KEY (tran_dt, biz_key, file_seq_no, firm_file_nm)
);
