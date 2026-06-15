-- api_tran_log_his definition

-- Drop table

-- DROP TABLE api_tran_log_his;

CREATE TABLE api_tran_log_his (
	trsc_dt varchar(8) NOT NULL,
	trsc_tm varchar(6) NOT NULL,
	user_id varchar(100) NOT NULL,
	svc_id varchar(50) NOT NULL,
	ip_addr varchar(100) NULL,
	seq_no numeric(10) NULL,
	api_com_data varchar(200) NULL,
	req_data varchar(1000) NULL,
	rslt_cd varchar(10) NULL,
	err_cont varchar(1000) NULL,
	CONSTRAINT api_tran_log_his_pk PRIMARY KEY (trsc_dt, trsc_tm, user_id, svc_id)
);
