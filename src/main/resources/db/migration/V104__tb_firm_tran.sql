-- tb_firm_tran definition

-- Drop table

-- DROP TABLE tb_firm_tran;

CREATE TABLE tb_firm_tran (
	tran_dt varchar(8) NOT NULL,
	biz_key varchar(20) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	tran_seq varchar(10) NOT NULL,
	bank_prtn_id varchar(20) NOT NULL,
	tran_tp bpchar(3) NOT NULL,
	tran_tm bpchar(6) NOT NULL,
	van_org_cd varchar(20) NOT NULL,
	wdrl_act_no varchar(100) NULL,
	tar_bank_cd varchar(15) NULL,
	finc_mda_div varchar(1) DEFAULT 'A'::character varying NULL,
	finc_mda_no varchar(100) NULL,
	finc_mda_owner_nm varchar(150) NULL,
	ant_div_cd bpchar(5) NULL,
	trsc_ccy_cd bpchar(3) NULL,
	tran_amt numeric(23, 2) DEFAULT '0'::numeric NULL,
	tran_usag varchar(200) NULL,
	bal_amt numeric(23, 2) NULL,
	bal_amt_sign bpchar(1) NULL,
	rq_dttm varchar(14) NULL,
	rs_dttm varchar(14) NULL,
	proc_stat bpchar(1) NULL,
	rs_cd varchar(4) NULL,
	rs_msg varchar(200) NULL,
	org_tran_dt varchar(8) NULL,
	org_tran_seq varchar(50) NULL,
	org_proc_tm varchar(6) NULL,
	org_resp_cd varchar(4) NULL,
	org_resp_msg varchar(150) NULL,
	wdrl_acct_rmrk varchar(150) NULL,
	tar_acct_rmrk varchar(150) NULL,
	withd_ccy_cd varchar(3) NULL,
	dep_ccy_cd varchar(3) NULL,
	apl_exc_rate numeric(23, 2) NULL,
	withd_amt numeric(23, 2) NULL,
	dep_amt numeric(23, 2) NULL,
	CONSTRAINT pk_tb_firm_tran PRIMARY KEY (tran_dt, biz_key, tran_seq)
);
