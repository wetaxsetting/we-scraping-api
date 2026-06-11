-- sys_env_set_infm definition

-- Drop table

-- DROP TABLE sys_env_set_infm;

CREATE TABLE sys_env_set_infm (
	env_set_inf varchar(5) NOT NULL,
	env_set_cont varchar(1000) NULL,
	CONSTRAINT sys_env_set_infm_pk PRIMARY KEY (env_set_inf)
);
