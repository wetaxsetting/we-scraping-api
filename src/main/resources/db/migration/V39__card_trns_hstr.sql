-- card_trns_hstr definition

-- Drop table

-- DROP TABLE card_trns_hstr;

CREATE TABLE card_trns_hstr (
	biz_key varchar(20) NOT NULL,
	bank_cd varchar(20) NOT NULL,
	card_no varchar(100) NOT NULL,
	trsc_dt varchar(8) NOT NULL,
	trsc_tm varchar(6) NULL,
	card_seq_no varchar(20) NOT NULL,
	card_nm varchar(50) NULL,
	merch_nm varchar(50) NULL,
	ccy_cd bpchar(3) NOT NULL,
	inout_dv bpchar(1) DEFAULT '2'::bpchar NOT NULL,
	trsc_amt numeric(20, 6) NULL,
	using_type varchar(50) NULL,
	prch_stts varchar(50) NULL,
	note varchar(255) NULL,
	cont varchar(200) NULL,
	appr_seq_no varchar(15) NULL,
	card_trns_usage_code varchar(20) NULL,
	auth_code varchar(15) NULL,
	city varchar(100) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	chart_acct_seq_no varchar(10) NULL,
	chart_acct_cd varchar(20) NULL,
	erp_snd_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
	CONSTRAINT pk_card_trns_hstr PRIMARY KEY (biz_key, card_seq_no)
);
