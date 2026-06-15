-- ext_tran_call_his definition

-- Drop table

-- DROP TABLE ext_tran_call_his;

CREATE TABLE ext_tran_call_his (
	svc_id varchar(50) NOT NULL,
	ref_date varchar(8) NOT NULL,
	seq_no numeric(10) NOT NULL,
	web_svc_id varchar(50) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	rslt_cd varchar(10) NULL,
	err_cont varchar(1000) NULL,
	biz_key varchar(20) NULL,
	user_id varchar(100) NULL,
	bank_cd varchar(20) NULL,
	acct_no varchar(100) NULL,
	start_date varchar(8) NULL,
	end_date varchar(8) NULL,
	taxcode varchar(100) NULL,
	CONSTRAINT ext_tran_call_his_pk PRIMARY KEY (svc_id, ref_date, seq_no)
);
