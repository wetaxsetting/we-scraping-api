-- push_device_mstr definition

-- Drop table

-- DROP TABLE push_device_mstr;

CREATE TABLE push_device_mstr (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	device_id varchar(200) NOT NULL,
	push_id varchar(200) NULL,
	pushserver_kind varchar(10) NOT NULL,
	model_name varchar(100) NULL,
	os varchar(20) NULL,
	note varchar(255) NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	app_id varchar(200) NOT NULL,
	lang_code varchar(2) DEFAULT 'EN'::character varying NOT NULL,
	CONSTRAINT push_device_mstr_pk PRIMARY KEY (biz_key, user_id, device_id)
);

-- Table Triggers

create trigger push_device_mstr_logtrigger after
insert
    or
delete
    or
update
    on
    push_device_mstr for each row execute function push_device_mstr_log();
