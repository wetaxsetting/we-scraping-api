-- nxsias_module_tran_log_his definition

-- Drop table

-- DROP TABLE nxsias_module_tran_log_his;

CREATE TABLE nxsias_module_tran_log_his (
	"uuid" varchar(32) NULL,
	reg_dtm varchar(14) NULL,
	nxisas_class varchar(100) NULL,
	nxisas_module varchar(100) NULL,
	nxisas_job varchar(100) NULL,
	nxisas_err_code varchar(8) NULL,
	nxisas_err_cont varchar(1000) NULL,
	req_data varchar(1000) NULL,
	biz_key varchar(20) NULL,
	reg_id varchar(100) NULL,
	nxisas_type varchar(100) NULL
);
