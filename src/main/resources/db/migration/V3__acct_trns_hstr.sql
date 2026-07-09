-- acct_trns_hstr definition

-- Drop table

-- DROP TABLE acct_trns_hstr;

CREATE TABLE acct_trns_hstr (
	biz_key varchar(20) NOT NULL,
	bank_cd varchar(20) NULL,
	acct_no varchar(100) NULL,
	trsc_dt varchar(8) NULL,
	acct_seq_no varchar(20) NOT NULL,
	trsc_tm varchar(6) NULL,
	ccy_cd bpchar(3) NULL,
	inout_dv bpchar(1) NULL,
	trsc_amt numeric(20, 6) NULL,
	bal numeric(20, 6) NULL,
	rmrk varchar(500) NULL,
	rmrk2 varchar(500) NULL,
	hand_reg_yn varchar(1) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	bank_ref_no varchar(100) NULL,
	chart_acct_seq_no varchar(10) NULL,
	chart_acct_cd varchar(20) NULL,
	erp_snd_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
	bank_trns_short_id varchar(20) NULL,
	bank_trns_full_id varchar(100) NULL,
	link_match_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	scdul_pymn_no varchar(20) NULL,
	scdul_pymn_detl_no varchar(20) NULL,
	ovrs_pymn_no varchar(14) NULL,
	ovrs_pymn_detl_no varchar(20) NULL,
	CONSTRAINT acct_trns_hstr_chk01 CHECK ((inout_dv = ANY (ARRAY['0'::bpchar, '1'::bpchar, '2'::bpchar]))),
	CONSTRAINT acct_trns_hstr_chk02 CHECK ((ccy_cd = ANY (ARRAY['VND'::bpchar, 'USD'::bpchar]))),
	CONSTRAINT acct_trns_hstr_pk PRIMARY KEY (biz_key, acct_seq_no),
	CONSTRAINT acct_trns_hstr_uk01 UNIQUE (biz_key, bank_cd, acct_no, trsc_dt, trsc_tm, ccy_cd, inout_dv, trsc_amt, bal, rmrk, rmrk2)
);

-- Table Triggers

create trigger acct_trns_hstr_logtrigger after
insert
    or
delete
    or
update
    on
    acct_trns_hstr for each row execute procedure acct_trns_hstr_log();
create trigger sync_to_rept_trigger after
insert
    or
delete
    or
update
    on
    acct_trns_hstr for each row execute procedure sync_acct_trns_hstr_to_rept();
