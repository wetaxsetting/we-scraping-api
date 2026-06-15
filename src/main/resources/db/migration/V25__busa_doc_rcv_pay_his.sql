-- busa_doc_rcv_pay_his definition

-- Drop table

-- DROP TABLE busa_doc_rcv_pay_his;

CREATE TABLE busa_doc_rcv_pay_his (
	biz_key varchar(20) NULL,
	user_id varchar(100) NULL,
	doc_id varchar(50) NULL,
	rcv_pay_his_seq_no numeric(5) NULL,
	rcv_pay_way_cd bpchar(1) NULL,
	bank_cd varchar(20) NULL,
	acct_no varchar(100) NULL,
	trsc_dt varchar(8) NULL,
	ref_no varchar(100) NULL,
	bal numeric(20, 6) NULL,
	dedu_amt numeric(20, 6) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	card_no varchar(100) NULL
);

-- Table Triggers

create trigger busa_doc_rcv_pay_his_logtrigger after
insert
    or
delete
    or
update
    on
    busa_doc_rcv_pay_his for each row execute function busa_doc_rcv_pay_his_log();
