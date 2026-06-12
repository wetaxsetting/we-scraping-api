-- tb_pay_file_data definition

-- Drop table

-- DROP TABLE tb_pay_file_data;

CREATE TABLE tb_pay_file_data (
	tran_dt varchar(8) NOT NULL,
	biz_key varchar(20) NOT NULL,
	file_seq_no varchar(7) NOT NULL,
	seq numeric(4) NOT NULL,
	tar_bank_cd varchar(15) NOT NULL,
	finc_mda_div varchar(1) DEFAULT 'A'::character varying NOT NULL,
	finc_mda_no varchar(100) NOT NULL,
	trsc_ccy_cd bpchar(3) DEFAULT 'VND'::bpchar NOT NULL,
	tran_amt numeric(23, 2) NOT NULL,
	bank_chnl_div bpchar(1) NOT NULL,
	ant_div_cd bpchar(1) NULL,
	sender_nm varchar(150) NULL,
	finc_mda_owner_nm varchar(150) NOT NULL,
	tar_acct_rmrk varchar(150) NULL,
	wdrl_acct_rmrk varchar(150) NULL,
	proc_rslt_div bpchar(1) DEFAULT 'N'::bpchar NULL,
	rs_cd varchar(10) NULL,
	rcpt_bank_cntry_cd varchar(2) NULL,
	remit_ccy_cd varchar(3) NULL,
	rcpt_use_addr varchar(300) NULL,
	fee_brdn_cls_cd varchar(3) NULL,
	CONSTRAINT pk_tb_pay_file_data PRIMARY KEY (tran_dt, biz_key, file_seq_no, seq)
);

-- Table Triggers

create trigger tb_pay_file_data_logtrigger after
insert
    or
delete
    or
update
    on
    tb_pay_file_data for each row execute function tb_pay_file_data_log();
