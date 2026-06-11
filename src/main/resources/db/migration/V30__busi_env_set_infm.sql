-- busi_env_set_infm definition

-- Drop table

-- DROP TABLE busi_env_set_infm;

CREATE TABLE busi_env_set_infm (
	biz_key varchar(20) NOT NULL,
	env_set_inf varchar(5) NOT NULL,
	env_set_cont varchar(1000) NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	CONSTRAINT busi_env_set_infm_pk PRIMARY KEY (biz_key, env_set_inf),
	CONSTRAINT busi_env_set_infm_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key),
	CONSTRAINT busi_env_set_infm_fk02 FOREIGN KEY (env_set_inf) REFERENCES sys_env_set_infm(env_set_inf)
);

-- Table Triggers

create trigger busi_env_set_logtrigger after
insert
    or
delete
    or
update
    on
    busi_env_set_infm for each row execute function busi_env_set_log();
