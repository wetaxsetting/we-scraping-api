-- ovrs_pymn_sumr_detl_his definition

-- Drop table

-- DROP TABLE ovrs_pymn_sumr_detl_his;

CREATE TABLE ovrs_pymn_sumr_detl_his (
	biz_key varchar(20) NOT NULL,
	ovrs_pymn_no varchar(14) NOT NULL,
	ovrs_pymn_detl_no varchar(15) NOT NULL,
	trnf_amt numeric(20, 6) NOT NULL,
	trnf_ccy_cd varchar(3) NOT NULL,
	bnfc_nm varchar(100) NULL,
	bnfc_bank_name varchar(200) NULL,
	bnfc_bank_cd varchar(15) NOT NULL,
	bnfc_acct_no varchar(100) NOT NULL,
	bnfc_bank_bic varchar(15) NULL,
	bnfc_addr varchar(500) NULL,
	fee_payer_cd bpchar(1) NOT NULL,
	trco_key varchar(20) NULL,
	hand_trco_key varchar(20) NULL,
	memo varchar(500) NULL,
	pay_tran_dt varchar(8) NULL,
	pay_tran_seq varchar(10) NULL,
	pay_file_seq_no varchar(7) NULL,
	pay_seq varchar(4) NULL,
	chart_acct_seq_no varchar(10) NULL,
	chart_acct_cd varchar(20) NULL,
	erp_file_date varchar(8) NULL,
	erp_file_num varchar(8) NULL,
	erp_file_seq numeric(10) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	erp_attribute2 varchar(150) NULL,
	hist_mod_dtm varchar(14) NOT NULL,
	hist_mod_uid varchar(100) NOT NULL,
	stts bpchar(1) NOT NULL,
	acct_seq_no varchar(20) NULL,
	bank_trns_short_id varchar(20) NULL,
	bank_trns_full_id varchar(100) NULL,
	link_match_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	CONSTRAINT pk_ovrs_pymn_sumr_detl_his PRIMARY KEY (biz_key, ovrs_pymn_no, ovrs_pymn_detl_no, hist_mod_dtm, hist_mod_uid)
);
