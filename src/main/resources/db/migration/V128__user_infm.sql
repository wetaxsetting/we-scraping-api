-- user_infm definition

-- Drop table

-- DROP TABLE user_infm;

CREATE TABLE user_infm (
	user_id varchar(100) NOT NULL,
	user_nm varchar(100) NOT NULL,
	clph_no varchar(100) NOT NULL,
	eml varchar(100) NULL,
	user_pw varchar(100) NOT NULL,
	user_img varchar(500) NULL,
	lst_lgn_date varchar(14) NULL,
	mob_lst_lgn_date varchar(14) NULL,
	actv_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	lock_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	pw_chg_req_seq_no numeric NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	biz_key varchar(20) NULL,
	empl_no varchar(20) NULL,
	dept_nm varchar(100) NULL,
	posi_nm varchar(100) NULL,
	CONSTRAINT user_infm_pk PRIMARY KEY (user_id)
);

-- Table Triggers

create trigger user_infm_logtrigger after
insert
    or
delete
    or
update
    on
    user_infm for each row execute function user_infm_log();
