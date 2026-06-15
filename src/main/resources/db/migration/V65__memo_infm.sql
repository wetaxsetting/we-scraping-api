-- memo_infm definition

-- Drop table

-- DROP TABLE memo_infm;

CREATE TABLE memo_infm (
	biz_key varchar(20) NOT NULL,
	web_svc_id varchar(50) NOT NULL,
	seq_no numeric(10) NOT NULL,
	memo varchar(200) NULL,
	color varchar(6) NULL,
	ref_no varchar(50) NOT NULL,
	ref_no2 varchar(50) NULL,
	ref_no3 varchar(50) NULL,
	ref_no4 varchar(50) NULL,
	ref_no5 varchar(50) NULL,
	ref_no6 varchar(50) NULL,
	ref_no7 varchar(50) NULL,
	ref_no8 varchar(50) NULL,
	ref_no9 varchar(50) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT memo_infm_pk PRIMARY KEY (biz_key, web_svc_id, seq_no),
	CONSTRAINT memo_infm_uk01 UNIQUE (biz_key, web_svc_id, seq_no, ref_no)
);
