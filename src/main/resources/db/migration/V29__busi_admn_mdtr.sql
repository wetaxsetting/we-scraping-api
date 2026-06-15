-- busi_admn_mdtr definition

-- Drop table

-- DROP TABLE busi_admn_mdtr;

CREATE TABLE busi_admn_mdtr (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	user_nm varchar(100) NOT NULL,
	mod_id varchar(100) NOT NULL,
	stts varchar(1) NOT NULL,
	mod_uid varchar(50) DEFAULT uuid_in(md5(random()::text || now()::text)::cstring) NOT NULL,
	CONSTRAINT busi_admn_mdtr_pk PRIMARY KEY (biz_key, user_id, mod_dtm, mod_uid)
);
