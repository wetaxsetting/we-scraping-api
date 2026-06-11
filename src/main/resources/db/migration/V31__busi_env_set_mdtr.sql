-- busi_env_set_mdtr definition

-- Drop table

-- DROP TABLE busi_env_set_mdtr;

CREATE TABLE busi_env_set_mdtr (
	biz_key varchar(20) NOT NULL,
	env_set_inf varchar(5) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	env_set_cont varchar(1000) NULL,
	mod_id varchar(100) NULL
);
