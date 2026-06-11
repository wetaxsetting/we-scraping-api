-- oprt_stts_info definition

-- Drop table

-- DROP TABLE oprt_stts_info;

CREATE TABLE oprt_stts_info (
	biz_key varchar(20) NOT NULL,
	taxcode varchar(100) NOT NULL,
	companyname varchar(100) NULL,
	companystatus varchar(255) NULL,
	officeadd varchar(255) NULL,
	searchdatetime varchar(14) NOT NULL,
	representativefirstname varchar(255) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NULL,
	CONSTRAINT pk_oprt_stts_info PRIMARY KEY (biz_key, taxcode)
);
