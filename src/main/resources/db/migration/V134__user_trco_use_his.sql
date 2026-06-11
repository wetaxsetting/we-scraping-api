-- user_trco_use_his definition

-- Drop table

-- DROP TABLE user_trco_use_his;

CREATE TABLE user_trco_use_his (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	seq_no numeric(10) NOT NULL,
	trco_key varchar(20) NULL,
	reg_dtm varchar(14) NULL,
	CONSTRAINT user_trco_use_his_pk PRIMARY KEY (biz_key, user_id, seq_no),
	CONSTRAINT user_trco_use_his_fk01 FOREIGN KEY (user_id) REFERENCES user_infm(user_id)
);
