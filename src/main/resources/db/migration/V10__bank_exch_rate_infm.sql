-- bank_exch_rate_infm definition

-- Drop table

-- DROP TABLE bank_exch_rate_infm;

CREATE TABLE bank_exch_rate_infm (
	biz_key varchar(20) NOT NULL,
	bank_cd varchar(14) NOT NULL,
	ref_date varchar(8) NOT NULL,
	ref_ccy_cd varchar(3) NOT NULL,
	ccy_cd bpchar(3) NOT NULL,
	exch_seq_no varchar(20) NOT NULL,
	tran_sell_amt numeric(20, 6) NOT NULL,
	tran_buy_amt numeric(20, 6) NOT NULL,
	buy_amt numeric(20, 6) NOT NULL,
	sell_amt numeric(20, 6) NOT NULL,
	srch_dt varchar(8) NULL,
	basic_rate numeric(20, 6) NULL,
	basic_tran numeric(20, 6) NULL,
	"class" varchar(10) NULL,
	"module" varchar(100) NULL,
	exch_rate_reg_dt varchar(14) NULL,
	seq_no varchar(2) DEFAULT '1'::bpchar NULL,
	stand_point bpchar(1) NULL,
	last_update_date varchar(8) NULL,
	last_update_time varchar(6) NULL,
	reg_dtm varchar(14) NOT NULL,
	reg_id varchar(100) NOT NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	erp_snd_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
	CONSTRAINT pk_bank_exch_rate_infm PRIMARY KEY (biz_key, bank_cd, ref_date, ccy_cd, exch_seq_no)
);
