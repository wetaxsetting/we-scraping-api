-- busi_vas_acct_infm definition

-- Drop table

-- DROP TABLE busi_vas_acct_infm;

CREATE TABLE busi_vas_acct_infm (
	biz_key varchar(20) NOT NULL,
	vas_acct_seq_no numeric(10) NOT NULL,
	vas_acct_cd varchar(15) NOT NULL,
	vas_acct_nm varchar(50) NULL,
	vas_acct_enm varchar(50) NULL,
	vas_acct_type bpchar(1) NOT NULL,
	stts bpchar(1) NOT NULL,
	cont varchar(200) NULL,
	del_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_busi_vas_acct_infm PRIMARY KEY (biz_key, vas_acct_seq_no)
);
