-- user_busi_conn_infm definition

-- Drop table

-- DROP TABLE user_busi_conn_infm;

CREATE TABLE user_busi_conn_infm (
	user_id varchar(100) NOT NULL,
	biz_key varchar(20) NOT NULL,
	empl_no varchar(20) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	user_img varchar(500) NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	lst_lgn_date varchar(14) NULL,
	mob_lst_lgn_date varchar(14) NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	CONSTRAINT pk_user_busi_conn_infm PRIMARY KEY (user_id, biz_key)
);

-- Table Triggers

create trigger user_busi_conn_infm_logtrigger after
insert
    or
delete
    or
update
    on
    user_busi_conn_infm for each row execute function user_busi_conn_infm_log();
