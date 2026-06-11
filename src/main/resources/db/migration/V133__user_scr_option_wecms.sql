-- user_scr_option_wecms definition

-- Drop table

-- DROP TABLE user_scr_option_wecms;

CREATE TABLE user_scr_option_wecms (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	web_svc_id varchar(50) NOT NULL,
	field_id varchar(100) NOT NULL,
	field_nm varchar(100) NOT NULL,
	disp_ord numeric(10) NOT NULL,
	disp_yn varchar(1) NOT NULL,
	use_yn varchar(1) NOT NULL,
	"size" varchar(10) NOT NULL,
	dym_area_id varchar(50) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_user_scr_option_wecms PRIMARY KEY (biz_key, user_id, web_svc_id, field_id)
);
