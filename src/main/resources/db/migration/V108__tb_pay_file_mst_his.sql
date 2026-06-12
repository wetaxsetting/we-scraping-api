-- tb_pay_file_mst_his definition

-- Drop table

-- DROP TABLE tb_pay_file_mst_his;

CREATE TABLE tb_pay_file_mst_his (
	tran_dt varchar(8) NOT NULL,
	biz_key varchar(20) NOT NULL,
	file_seq_no varchar(7) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	wdrl_act_no varchar(100) NOT NULL,
	"uuid" varchar(50) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	bank_prtn_id varchar(20) NOT NULL,
	fund_tp bpchar(1) NOT NULL,
	tran_tp bpchar(1) NOT NULL,
	proc_stat bpchar(1) NOT NULL,
	req_cnt numeric(7) NOT NULL,
	req_amt numeric(23, 2) NOT NULL,
	reg_dttm varchar(14) NULL,
	proc_dttm varchar(14) NOT NULL,
	stts varchar(1) NOT NULL,
	rs_msg varchar(200) NULL,
	CONSTRAINT pk_tb_pay_file_mst_his PRIMARY KEY (uuid)
);
