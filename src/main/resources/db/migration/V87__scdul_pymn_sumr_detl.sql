-- scdul_pymn_sumr_detl definition

-- Drop table

-- DROP TABLE scdul_pymn_sumr_detl;

CREATE TABLE scdul_pymn_sumr_detl (
	biz_key varchar(20) NOT NULL,
	scdul_pymn_no varchar(10) NOT NULL,
	scdul_pymn_detl_no varchar(20) NOT NULL,
	tran_amt numeric(20, 6) NOT NULL,
	ant_div_cd bpchar(5) NULL,
	rcpt_nm varchar(100) NULL,
	tar_bank_cd varchar(15) NULL,
	finc_mda_no varchar(100) NULL,
	tran_usag varchar(200) NULL,
	tar_acct_rmrk varchar(150) NULL,
	wdrl_acct_rmrk varchar(150) NULL,
	pay_tran_dt varchar(8) NULL,
	pay_tran_seq varchar(10) NULL,
	pay_file_seq_no varchar(7) NULL,
	pay_seq numeric(4) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NULL,
	trco_key varchar(20) NULL,
	hand_trco_key varchar(20) NULL,
	doc_no varchar(50) NULL,
	note varchar(200) NULL,
	citad_bank_cd varchar(50) NULL,
	trco_cmnm varchar(200) NULL,
	trco_cmenm varchar(200) NULL,
	chart_acct_cd varchar(20) NULL,
	chart_acct_seq_no varchar(20) NULL,
	erp_file_date varchar(8) NULL,
	erp_file_num varchar(8) NULL,
	erp_file_seq numeric(10) NULL,
	trnf_ccy_cd varchar(3) NULL,
	erp_attribute2 varchar(150) NULL,
	depo_ccy_cd varchar(3) NULL,
	acct_seq_no varchar(20) NULL,
	bank_trns_short_id varchar(20) NULL,
	bank_trns_full_id varchar(100) NULL,
	link_match_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	CONSTRAINT pk_scdul_pymn_sumr_detl PRIMARY KEY (biz_key, scdul_pymn_no, scdul_pymn_detl_no)
);

-- Table Triggers

create trigger scdul_pymn_sumr_detl_logtrigger after
insert
    or
delete
    or
update
    on
    scdul_pymn_sumr_detl for each row execute function scdul_pymn_sumr_detl_log();
