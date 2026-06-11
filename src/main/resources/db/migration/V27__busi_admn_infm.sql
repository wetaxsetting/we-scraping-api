-- busi_admn_infm definition

-- Drop table

-- DROP TABLE busi_admn_infm;

CREATE TABLE busi_admn_infm (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	user_nm varchar(100) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT busi_admn_infm_pk PRIMARY KEY (biz_key, user_id),
	CONSTRAINT busi_admn_infm_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key),
	CONSTRAINT busi_admn_infm_fk02 FOREIGN KEY (user_id) REFERENCES user_infm(user_id)
);

-- Table Triggers

create trigger busi_admn_infm_logtrigger after
insert
    or
delete
    or
update
    on
    busi_admn_infm for each row execute function busi_admn_infm_log();
