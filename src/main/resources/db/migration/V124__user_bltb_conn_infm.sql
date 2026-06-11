-- user_bltb_conn_infm definition

-- Drop table

-- DROP TABLE user_bltb_conn_infm;

CREATE TABLE user_bltb_conn_infm (
	biz_key varchar(20) NOT NULL,
	bltb_cd varchar(15) NOT NULL,
	user_id varchar(100) NOT NULL,
	bltb_user_type bpchar(1) DEFAULT 'M'::bpchar NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_user_bltb_conn_infm PRIMARY KEY (biz_key, bltb_cd, user_id),
	CONSTRAINT user_bltb_conn_infm_chk01 CHECK ((bltb_user_type = ANY (ARRAY['M'::bpchar, 'A'::bpchar])))
);
