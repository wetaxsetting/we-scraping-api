-- user_dshbrd_infm definition

-- Drop table

-- DROP TABLE user_dshbrd_infm;

CREATE TABLE user_dshbrd_infm (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	web_svc_id varchar(50) NOT NULL,
	actv_yn varchar(1) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_user_dshbrd_infm PRIMARY KEY (biz_key, user_id, web_svc_id)
);
