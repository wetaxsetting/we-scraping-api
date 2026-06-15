-- user_login_his definition

-- Drop table

-- DROP TABLE user_login_his;

CREATE TABLE user_login_his (
	user_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	ip_addr varchar(100) NOT NULL,
	lgn_chnl varchar(3) DEFAULT 'PC'::character varying NOT NULL,
	rslt_cd varchar(10) NOT NULL,
	rslt_cont varchar(200) NULL,
	reg_uid varchar(50) DEFAULT uuid_in(md5(random()::text || now()::text)::cstring) NOT NULL,
	CONSTRAINT user_login_his_pk PRIMARY KEY (user_id, reg_dtm, reg_uid)
);
