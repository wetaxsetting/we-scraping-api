-- busi_admn_login_his definition

-- Drop table

-- DROP TABLE busi_admn_login_his;

CREATE TABLE busi_admn_login_his (
	biz_key varchar(20) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	login_uuid varchar(50) DEFAULT uuid_in(md5(random()::text || now()::text)::cstring) NOT NULL,
	ip_addr varchar(100) NOT NULL,
	reg_nm varchar(100) NULL,
	lgn_chnl varchar(3) DEFAULT 'PC'::character varying NOT NULL,
	CONSTRAINT pk_busi_admn_login_his PRIMARY KEY (biz_key, reg_id, reg_dtm, login_uuid)
);
