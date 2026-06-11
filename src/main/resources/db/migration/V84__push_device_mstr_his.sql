-- push_device_mstr_his definition

-- Drop table

-- DROP TABLE push_device_mstr_his;

CREATE TABLE push_device_mstr_his (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	device_id varchar(200) NOT NULL,
	push_id varchar(200) NULL,
	pushserver_kind varchar(10) NOT NULL,
	model_name varchar(100) NULL,
	os varchar(20) NULL,
	note varchar(255) NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	mod_id varchar(100) NOT NULL,
	stts varchar(1) NOT NULL,
	app_id varchar(200) NOT NULL,
	lang_code varchar(2) NULL,
	CONSTRAINT push_device_mstr_his_chk01 CHECK (((stts)::text = ANY (ARRAY[('C'::character varying)::text, ('U'::character varying)::text, ('D'::character varying)::text]))),
	CONSTRAINT push_device_mstr_his_pk PRIMARY KEY (biz_key, user_id, device_id, mod_dtm)
);
