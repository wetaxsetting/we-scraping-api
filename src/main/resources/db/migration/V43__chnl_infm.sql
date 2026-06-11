-- chnl_infm definition

-- Drop table

-- DROP TABLE chnl_infm;

CREATE TABLE chnl_infm (
	chnl_id varchar(10) NOT NULL,
	chnl_nm varchar(100) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	chnl_type varchar(1) DEFAULT 'B'::character varying NOT NULL,
	CONSTRAINT chnl_infm_pk PRIMARY KEY (chnl_id)
);
