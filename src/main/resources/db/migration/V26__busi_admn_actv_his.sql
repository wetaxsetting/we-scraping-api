-- busi_admn_actv_his definition

-- Drop table

-- DROP TABLE busi_admn_actv_his;

CREATE TABLE busi_admn_actv_his (
	biz_key varchar(20) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	actv_uuid varchar(50) DEFAULT uuid_in(md5(random()::text || now()::text)::cstring) NOT NULL,
	stts varchar(1) NOT NULL,
	menu_cd bpchar(1) NOT NULL,
	sect_cd bpchar(4) NULL,
	sub_sect_cd bpchar(5) NULL,
	task_cd bpchar(5) NOT NULL,
	CONSTRAINT pk_busi_admn_actv_his PRIMARY KEY (biz_key, reg_id, reg_dtm, actv_uuid)
);
