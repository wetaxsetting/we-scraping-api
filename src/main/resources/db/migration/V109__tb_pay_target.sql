-- tb_pay_target definition

-- Drop table

-- DROP TABLE tb_pay_target;

CREATE TABLE tb_pay_target (
	tran_dt varchar(8) NOT NULL,
	biz_key varchar(20) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	tran_seq varchar(10) NOT NULL,
	wdrl_act_no varchar(100) NOT NULL,
	reg_tm varchar(6) NOT NULL,
	ant_div_cd bpchar(5) NOT NULL,
	tar_bank_cd varchar(15) NOT NULL,
	finc_mda_no varchar(100) NOT NULL,
	finc_mda_owner_nm varchar(150) NULL,
	tran_amt numeric(23, 2) NOT NULL,
	tran_usag varchar(200) NULL,
	proc_dttm varchar(14) NULL,
	proc_stat bpchar(1) DEFAULT '9'::bpchar NULL,
	rerun_div bpchar(1) DEFAULT 'N'::bpchar NULL,
	rerun_req_dttm varchar(14) NULL,
	user_id varchar(100) NULL,
	trsc_ccy_cd bpchar(3) DEFAULT 'VND'::bpchar NULL,
	withd_ccy_cd varchar(3) NULL,
	dep_ccy_cd varchar(3) NULL,
	apl_exc_rate numeric(23, 2) NULL,
	withd_amt numeric(23, 2) NULL,
	dep_amt numeric(23, 2) NULL,
	CONSTRAINT pk_tb_pay_target PRIMARY KEY (tran_dt, biz_key, tran_seq)
);

-- Table Triggers

create trigger tb_pay_target_logtrigger after
insert
    or
delete
    or
update
    on
    tb_pay_target for each row execute function tb_pay_target_log();
