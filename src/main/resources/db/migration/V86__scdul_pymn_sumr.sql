-- scdul_pymn_sumr definition

-- Drop table

-- DROP TABLE scdul_pymn_sumr;

CREATE TABLE scdul_pymn_sumr (
	biz_key varchar(20) NOT NULL,
	scdul_pymn_no varchar(10) NOT NULL,
	scdul_pymn_type_cd varchar(2) NOT NULL,
	ccy_cd varchar(3) NOT NULL,
	titl varchar(100) NOT NULL,
	total_tran_amt numeric(20, 6) NOT NULL,
	tot_trns_cnt numeric(10) NOT NULL,
	appr_seq_no varchar(15) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NULL,
	out_bank_cd varchar(15) NULL,
	wdrl_act_no varchar(100) NULL,
	trnf_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	org_file_nm varchar(500) NULL,
	save_file_nm varchar(500) NULL,
	file_size varchar(100) NULL,
	pay_tran_dt varchar(8) NULL,
	pay_file_seq_no varchar(7) NULL,
	trnf_dtm varchar(14) NULL,
	erp_file_date varchar(8) NULL,
	erp_file_num varchar(8) NULL,
	erp_proc_cmpl_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	wdrl_ccy_cd varchar(3) NULL,
	acct_seq_no varchar(20) NULL,
	bank_trns_short_id varchar(20) NULL,
	bank_trns_full_id varchar(100) NULL,
	link_match_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	CONSTRAINT pk_scdul_pymn_sumr PRIMARY KEY (biz_key, scdul_pymn_no)
);

-- Table Triggers

create trigger scdul_pymn_sumr_logtrigger after
insert
    or
delete
    or
update
    on
    scdul_pymn_sumr for each row execute function scdul_pymn_sumr_log();
