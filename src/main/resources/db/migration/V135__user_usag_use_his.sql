-- user_usag_use_his definition

-- Drop table

-- DROP TABLE user_usag_use_his;

CREATE TABLE user_usag_use_his (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	seq_no numeric(10) NOT NULL,
	use_usag_cd varchar(20) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	CONSTRAINT user_usag_use_his_pk PRIMARY KEY (biz_key, user_id, seq_no)
);
