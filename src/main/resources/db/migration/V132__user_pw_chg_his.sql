-- user_pw_chg_his definition

-- Drop table

-- DROP TABLE user_pw_chg_his;

CREATE TABLE user_pw_chg_his (
	user_id varchar(100) NOT NULL,
	reg_seq_no numeric(20) NOT NULL,
	user_pw varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	ip_addr varchar(100) NULL,
	reg_dtm varchar(14) NOT NULL,
	CONSTRAINT user_pw_chg_his_pk PRIMARY KEY (user_id, reg_seq_no),
	CONSTRAINT user_pw_chg_his_user_infm_fk01 FOREIGN KEY (user_id) REFERENCES user_infm(user_id)
);
