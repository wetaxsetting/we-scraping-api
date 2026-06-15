-- busi_match_log definition

-- Drop table

-- DROP TABLE busi_match_log;

CREATE TABLE busi_match_log (
	biz_key varchar(20) NOT NULL,
	match_log_seq varchar(20) NOT NULL,
	bank_trns_short_id varchar(20) NOT NULL,
	bank_trns_full_id varchar(100) NOT NULL,
	pay_trns_id varchar(50) NOT NULL,
	match_type_cd bpchar(1) NOT NULL,
	memo varchar(500) NULL,
	matched_at varchar(14) NOT NULL,
	matched_by varchar(100) NOT NULL,
	last_matched_at varchar(14) NULL,
	last_matched_by varchar(100) NULL,
	locked_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_busi_match_log PRIMARY KEY (biz_key, match_log_seq)
);
