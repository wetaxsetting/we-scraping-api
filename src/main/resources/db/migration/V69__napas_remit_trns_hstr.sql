-- napas_remit_trns_hstr definition

-- Drop table

-- DROP TABLE napas_remit_trns_hstr;

-- NOTE — reverted to legacy parity (2026-06-10): this base DDL is the
-- JEX-current 21 columns and is the source of truth. An earlier plan added
-- OAT_IDEMPOTENCY_KEY varchar(64) + a partial unique index for Own Account
-- Transfer replay protection, but the live JEX NAPAS_REMIT_TRNS_HSTR table
-- has no such column. The entity mapping + the service replay/idempotency
-- guard were removed so Hibernate `validate` passes against the legacy
-- table. Do NOT add OAT_IDEMPOTENCY_KEY below — it does not exist in JEX.

CREATE TABLE napas_remit_trns_hstr (
	biz_key varchar(20) NOT NULL,
	seq_no numeric(10) NOT NULL,
	out_bank_code varchar(20) NOT NULL,
	out_acct_no varchar(100) NOT NULL,
	trans_type bpchar(1) NULL,
	in_napas_bank_code varchar(10) NULL,
	in_acct_no varchar(100) NOT NULL,
	ccy_cd bpchar(3) NOT NULL,
	trsc_amt numeric(20, 6) NOT NULL,
	fee_dfr_sbj bpchar(1) NULL,
	fee numeric(20, 6) NULL,
	rmrk varchar(500) NULL,
	rmrk2 varchar(500) NULL,
	owac_nm varchar(200) NULL,
	reg_dtm varchar(14) NOT NULL,
	reg_id varchar(100) NOT NULL,
	trns_stts varchar(20) NULL,
	trns_msg varchar(255) NULL,
	in_bank_code varchar(20) NULL,
	trns_stlm_id varchar(100) NULL,
	trns_stlm_yn varchar(1) NULL,
	CONSTRAINT napas_remit_trns_hstr_pk PRIMARY KEY (biz_key, seq_no)
);
