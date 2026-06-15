-- user_menu_auth_infm definition

-- Drop table

-- DROP TABLE user_menu_auth_infm;

CREATE TABLE user_menu_auth_infm (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	web_svc_id varchar(50) NOT NULL,
	use_yn varchar(1) DEFAULT 'N'::character varying NULL,
	disp_ord numeric(10) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	CONSTRAINT user_menu_auth_infm_pk PRIMARY KEY (biz_key, user_id, web_svc_id),
	CONSTRAINT user_menu_auth_infm_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key),
	CONSTRAINT user_menu_auth_infm_fk02 FOREIGN KEY (user_id) REFERENCES user_infm(user_id)
);
