-- non_pay_cond_daily_sumr definition

-- Drop table

-- DROP TABLE non_pay_cond_daily_sumr;

CREATE TABLE non_pay_cond_daily_sumr (
	biz_key varchar(20) NOT NULL,
	ref_date varchar(8) NOT NULL,
	trco_key varchar(20) NOT NULL,
	trco_cmnm varchar(200) NOT NULL,
	trco_cmenm varchar(200) NOT NULL,
	trco_tax_id varchar(40) NULL,
	ccy_cd bpchar(3) NOT NULL,
	non_snd_amt numeric(20, 6) DEFAULT 0 NOT NULL,
	CONSTRAINT non_pay_cond_daily_sumr_chk01 CHECK ((ccy_cd = ANY (ARRAY['VND'::bpchar, 'USD'::bpchar]))),
	CONSTRAINT non_pay_cond_daily_sumr_pk PRIMARY KEY (biz_key, ref_date, trco_key, ccy_cd),
	CONSTRAINT non_pay_cond_daily_sumr_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key)
);
