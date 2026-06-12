-- ovrs_pymn_mstr_his definition

-- Drop table

-- DROP TABLE ovrs_pymn_mstr_his;

CREATE TABLE ovrs_pymn_mstr_his (
	biz_key varchar(20) NOT NULL,
	ovrs_pymn_no varchar(14) NOT NULL,
	appr_seq_no varchar(15) NULL,
	dbt_acct_no varchar(100) NOT NULL,
	dbt_acct_cd varchar(15) NOT NULL,
	dbt_acct_hldr varchar(100) NULL,
	dbt_ccy_cd varchar(3) NOT NULL,
	avl_bal numeric(20, 6) NULL,
	trnf_dt varchar(14) NOT NULL,
	trnf_ccy_cd varchar(3) NOT NULL,
	trnf_amt numeric(20, 6) NOT NULL,
	total_tran_amt numeric(20, 6) NOT NULL,
	tot_trns_cnt numeric(10) NOT NULL,
	fee_payer_cd bpchar(1) NOT NULL,
	trco_key varchar(20) NOT NULL,
	hand_trco_key varchar(20) NOT NULL,
	bnfc_nm varchar(100) NOT NULL,
	bnfc_bank_cd varchar(15) NOT NULL,
	bnfc_bank_bic varchar(15) NOT NULL,
	bnfc_acct_no varchar(100) NOT NULL,
	bnfc_addr varchar(500) NOT NULL,
	memo varchar(500) NOT NULL,
	trnf_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	pay_seq varchar(4) NULL,
	pay_tran_dt varchar(8) NULL,
	pay_tran_seq varchar(10) NULL,
	pay_file_seq_no varchar(7) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	hist_mod_dtm varchar(14) NOT NULL,
	hist_mod_uid varchar(100) NOT NULL,
	stts bpchar(1) NOT NULL,
	chart_acct_seq_no varchar(10) NULL,
	chart_acct_cd varchar(20) NULL,
	bnfc_bank_code varchar(20) NULL,
	bnfc_bank_name varchar(200) NULL,
	CONSTRAINT pk_ovrs_pymn_mstr_his PRIMARY KEY (biz_key, ovrs_pymn_no, hist_mod_dtm, hist_mod_uid)
);
