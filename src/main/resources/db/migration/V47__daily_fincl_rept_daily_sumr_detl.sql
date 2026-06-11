-- daily_fincl_rept_daily_sumr_detl definition

-- Drop table

-- DROP TABLE daily_fincl_rept_daily_sumr_detl;

CREATE TABLE daily_fincl_rept_daily_sumr_detl (
	biz_key varchar(20) NOT NULL,
	ref_date varchar(8) NOT NULL,
	ccy_cd bpchar(3) NOT NULL,
	acct_type_cd bpchar(2) NOT NULL,
	bank_cd varchar(20) NOT NULL,
	acct_no varchar(100) NOT NULL,
	bal numeric(20, 6) NULL,
	acct_nick_nm varchar(50) NULL,
	loan_nm varchar(100) NULL,
	expi_dt varchar(8) NULL,
	agmt_amt numeric(20, 6) NULL,
	snd_cnt numeric(20, 6) NULL,
	snd_amt numeric(20, 6) NULL,
	rcv_cnt numeric(20, 6) NULL,
	rcv_amt numeric(20, 6) NULL,
	intl_rcv_amt numeric(20, 6) NULL,
	intl_rcv_cnt numeric(10) NULL,
	intl_snd_amt numeric(20, 6) NULL,
	intl_snd_cnt numeric(10) NULL,
	bank_nm varchar(100) NULL,
	CONSTRAINT daily_fincl_rept_daily_sumr_detl_chk01 CHECK ((ccy_cd = ANY (ARRAY['VND'::bpchar, 'USD'::bpchar, 'KRW'::bpchar]))),
	CONSTRAINT daily_fincl_rept_daily_sumr_detl_chk02 CHECK ((acct_type_cd = ANY (ARRAY['01'::bpchar, '02'::bpchar, '03'::bpchar, '04'::bpchar, '05'::bpchar, '06'::bpchar, '07'::bpchar, '08'::bpchar]))),
	CONSTRAINT daily_fincl_rept_daily_sumr_detl_pk PRIMARY KEY (biz_key, ref_date, ccy_cd, acct_type_cd, bank_cd, acct_no)
);
