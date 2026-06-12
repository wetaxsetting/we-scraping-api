-- rmrk_org_infm definition

-- Drop table

-- DROP TABLE rmrk_org_infm;

CREATE TABLE rmrk_org_infm (
	biz_key varchar(20) NOT NULL,
	rcv_pay_dv varchar(3) NULL,
	rmrk varchar(500) NOT NULL,
	match_type bpchar(1) NOT NULL,
	disp_ord numeric(10) NOT NULL,
	trco_key varchar(20) NULL,
	use_usag_cd varchar(20) NULL,
	vas_acct_seq_no numeric(10) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	rmrk_seq_no numeric(10) NOT NULL,
	chart_acct_seq_no varchar(10) NULL,
	chart_acct_cd varchar(20) NULL,
	CONSTRAINT pk_rmrk_org_infm PRIMARY KEY (biz_key, rmrk_seq_no)
);
