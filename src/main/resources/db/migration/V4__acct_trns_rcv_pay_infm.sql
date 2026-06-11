-- acct_trns_rcv_pay_infm definition

-- Drop table

-- DROP TABLE acct_trns_rcv_pay_infm;

CREATE TABLE acct_trns_rcv_pay_infm (
	biz_key varchar(20) NOT NULL,
	bank_cd varchar(20) NULL,
	acct_no varchar(100) NULL,
	trsc_dt varchar(8) NULL,
	acct_seq_no varchar(20) NOT NULL,
	rcv_pay_seq_no numeric(5) NOT NULL,
	rcv_pay_trco_key varchar(20) NULL,
	rcv_pay_trco_nm varchar(200) NULL,
	cash_repy_seq_no numeric(10) NULL,
	rcv_pay_usag_cd varchar(20) NULL,
	usag_detl_cd varchar(20) NULL,
	cont varchar(200) NULL,
	fee numeric(20, 6) NULL,
	dc_amt numeric(20, 6) NULL,
	rcv_pay_amt numeric(20, 6) NULL,
	prjt_no varchar(20) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	srch_str text NULL,
	simple_rcv_use_yn bpchar(1) NULL,
	auto_set_yn bpchar(1) NULL,
	split_yn bpchar(1) NULL,
	rcv_pay_trco_enm varchar(200) NULL,
	auto_reg_yn bpchar(1) NULL,
	adv_paym_trns_seq numeric(10) NULL,
	intr_amt numeric(20, 6) NULL,
	rcv_acct_bank_cd varchar(20) NULL,
	rcv_acct_no varchar(100) NULL,
	vas_acct_seq_no numeric(10) NULL,
	vas_acct_nm varchar(50) NULL,
	vas_acct_cd varchar(50) NULL,
	cnfm_yn bpchar(1) NULL,
	cnfm_by_user_id varchar(20) NULL,
	cnfm_by_user_nm varchar(50) NULL,
	cnfm_dtm varchar(14) NULL,
	CONSTRAINT acct_trns_rcv_pay_infm_pk PRIMARY KEY (biz_key, acct_seq_no, rcv_pay_seq_no),
	CONSTRAINT acct_trns_rcv_pay_infm_fk01 FOREIGN KEY (biz_key,acct_seq_no) REFERENCES acct_trns_hstr(biz_key,acct_seq_no)
);

-- Table Triggers

create trigger acct_trns_rcv_pay_infm_logtrigger after
insert
    or
delete
    or
update
    on
    acct_trns_rcv_pay_infm for each row execute function acct_trns_rcv_pay_infm_log();
