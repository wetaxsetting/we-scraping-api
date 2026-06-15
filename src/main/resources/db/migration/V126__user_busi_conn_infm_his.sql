-- user_busi_conn_infm_his definition

-- Drop table

-- DROP TABLE user_busi_conn_infm_his;

CREATE TABLE user_busi_conn_infm_his (
	user_id varchar(100) NOT NULL,
	biz_key varchar(20) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	empl_no varchar(20) NULL,
	user_img varchar(500) NULL,
	mod_id varchar(100) NOT NULL,
	stts varchar(1) NOT NULL,
	lst_lgn_date varchar(14) NULL,
	mob_lst_lgn_date varchar(14) NULL,
	mod_uid varchar(50) DEFAULT uuid_in(md5(random()::text || now()::text)::cstring) NOT NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	CONSTRAINT pk_user_busi_conn_infm_his PRIMARY KEY (user_id, biz_key, mod_dtm, mod_uid)
);
