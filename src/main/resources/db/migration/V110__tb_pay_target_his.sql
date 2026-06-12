-- tb_pay_target_his definition

-- Drop table

-- DROP TABLE tb_pay_target_his;

CREATE TABLE tb_pay_target_his (
	tran_dt varchar(8) NOT NULL,
	biz_key varchar(20) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	tran_seq varchar(10) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	"uuid" varchar(50) NOT NULL,
	wdrl_act_no varchar(100) NOT NULL,
	reg_tm varchar(6) NOT NULL,
	ant_div_cd bpchar(5) NOT NULL,
	tar_bank_cd varchar(15) NOT NULL,
	finc_mda_no varchar(100) NOT NULL,
	finc_mda_owner_nm varchar(150) NULL,
	tran_amt numeric(22) NOT NULL,
	tran_usag varchar(200) NULL,
	proc_dttm varchar(14) NULL,
	proc_stat bpchar(1) NULL,
	rerun_div bpchar(1) NULL,
	rerun_req_dttm varchar(14) NULL,
	user_id varchar(100) NULL,
	stts varchar(1) NOT NULL,
	CONSTRAINT pk_tb_pay_target_his PRIMARY KEY (uuid)
);
