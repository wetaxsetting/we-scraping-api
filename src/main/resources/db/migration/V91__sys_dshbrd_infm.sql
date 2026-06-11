-- sys_dshbrd_infm definition

-- Drop table

-- DROP TABLE sys_dshbrd_infm;

CREATE TABLE sys_dshbrd_infm (
	web_svc_id varchar(50) NOT NULL,
	actv_yn varchar(1) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_sys_dshbrd_infm PRIMARY KEY (web_svc_id)
);
