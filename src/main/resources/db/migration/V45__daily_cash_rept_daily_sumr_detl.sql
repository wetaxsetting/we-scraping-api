-- daily_cash_rept_daily_sumr_detl definition

-- Drop table

-- DROP TABLE daily_cash_rept_daily_sumr_detl;

CREATE TABLE daily_cash_rept_daily_sumr_detl (
	biz_key varchar(20) NOT NULL,
	ref_date varchar(8) NOT NULL,
	ccy_cd bpchar(3) NOT NULL,
	acct_type_cd bpchar(2) NOT NULL,
	bank_cd varchar(20) NOT NULL,
	acct_no varchar(100) NOT NULL,
	bal numeric(20, 6) DEFAULT 0 NOT NULL,
	loan_nm varchar(100) NULL,
	expi_dt varchar(8) NULL,
	snd_cnt numeric(20, 6) DEFAULT 0 NOT NULL,
	snd_amt numeric(20, 6) DEFAULT 0 NOT NULL,
	rcv_cnt numeric(20, 6) DEFAULT 0 NOT NULL,
	rcv_amt numeric(20, 6) DEFAULT 0 NOT NULL,
	agmt_amt numeric(20, 6) NULL,
	lst_coll_date varchar(14) NULL,
	CONSTRAINT daily_cash_rept_daily_sumr_detl_chk01 CHECK ((ccy_cd = ANY (ARRAY['VND'::bpchar, 'USD'::bpchar, 'KRW'::bpchar]))),
	CONSTRAINT daily_cash_rept_daily_sumr_detl_pk PRIMARY KEY (biz_key, ref_date, acct_type_cd, ccy_cd, bank_cd, acct_no)
);

-- Table Triggers

create trigger daily_cash_rept_daily_sumr_detl_logtrigger after
insert
    or
delete
    or
update
    on
    daily_cash_rept_daily_sumr_detl for each row execute function daily_cash_rept_daily_sumr_detl_log();
