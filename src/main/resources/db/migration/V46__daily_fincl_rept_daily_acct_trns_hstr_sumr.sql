-- daily_fincl_rept_daily_acct_trns_hstr_sumr definition

-- Drop table

-- DROP TABLE daily_fincl_rept_daily_acct_trns_hstr_sumr;

CREATE TABLE daily_fincl_rept_daily_acct_trns_hstr_sumr (
	biz_key varchar(20) NOT NULL,
	ref_date varchar(8) NOT NULL,
	bank_cd varchar(20) NOT NULL,
	acct_no varchar(100) NOT NULL,
	acct_seq_no varchar(20) NOT NULL,
	trsc_tm varchar(6) NULL,
	ccy_cd bpchar(3) NOT NULL,
	trsc_amt numeric(20, 6) NULL,
	bal numeric(20, 6) NULL,
	inout_dv bpchar(1) NOT NULL,
	rmrk varchar(500) NULL,
	CONSTRAINT daily_fincl_rept_daily_acct_trns_hstr_sumr_chk02 CHECK (((ccy_cd)::text = ANY (ARRAY[('USD'::character varying)::text, ('VND'::character varying)::text]))),
	CONSTRAINT daily_fincl_rept_daily_acct_trns_hstr_sumr_pk PRIMARY KEY (biz_key, ref_date, acct_seq_no)
);
