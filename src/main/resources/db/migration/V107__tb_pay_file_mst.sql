-- tb_pay_file_mst definition

-- Drop table

-- DROP TABLE tb_pay_file_mst;

CREATE TABLE tb_pay_file_mst (
	tran_dt varchar(8) NOT NULL,
	biz_key varchar(20) NOT NULL,
	file_seq_no varchar(7) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	wdrl_act_no varchar(100) NOT NULL,
	bank_prtn_id varchar(20) NOT NULL,
	fund_tp bpchar(1) NOT NULL,
	tran_tp bpchar(1) NOT NULL,
	proc_stat bpchar(1) DEFAULT '9'::bpchar NOT NULL,
	req_cnt numeric(7) NULL,
	req_amt numeric(23, 2) NULL,
	reg_dttm varchar(14) NULL,
	proc_dttm varchar(14) NULL,
	rs_msg varchar(200) NULL,
	CONSTRAINT pk_tb_pay_file_mst PRIMARY KEY (tran_dt, biz_key, file_seq_no)
);

-- Table Triggers

create trigger tb_pay_file_mst_logtrigger after
insert
    or
delete
    or
update
    on
    tb_pay_file_mst for each row execute function tb_pay_file_mst_log();
