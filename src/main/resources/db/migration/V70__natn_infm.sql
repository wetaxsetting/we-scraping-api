-- natn_infm definition

-- Drop table

-- DROP TABLE natn_infm;

CREATE TABLE natn_infm (
	ntnl_cd varchar(2) NOT NULL,
	natl_nm varchar(100) NULL,
	time_zone varchar(50) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	natn_num varchar(5) NULL,
	note varchar(255) NULL,
	CONSTRAINT natn_infm_pk PRIMARY KEY (ntnl_cd)
);
