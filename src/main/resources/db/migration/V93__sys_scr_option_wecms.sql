-- sys_scr_option_wecms definition

-- Drop table

-- DROP TABLE sys_scr_option_wecms;

CREATE TABLE sys_scr_option_wecms (
	web_svc_id varchar(50) NOT NULL,
	field_id varchar(100) NOT NULL,
	field_nm varchar(100) NOT NULL,
	disp_ord numeric(10) NOT NULL,
	disp_yn varchar(1) NOT NULL,
	use_yn varchar(1) NOT NULL,
	"size" varchar(10) NOT NULL,
	dym_area_id varchar(50) NOT NULL,
	sort_yn bpchar(1) NOT NULL,
	html_cont text NULL,
	CONSTRAINT pk_sys_scr_option_wecms PRIMARY KEY (web_svc_id, field_id)
);
