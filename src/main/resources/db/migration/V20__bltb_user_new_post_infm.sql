-- bltb_user_new_post_infm definition

-- Drop table

-- DROP TABLE bltb_user_new_post_infm;

CREATE TABLE bltb_user_new_post_infm (
	biz_key varchar(20) NOT NULL,
	bltb_cd varchar(15) NOT NULL,
	user_id varchar(100) NOT NULL,
	post_seq_no numeric(15) NOT NULL,
	read_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT bltb_user_new_post_infm_chk01 CHECK ((read_yn = ANY (ARRAY['N'::bpchar, 'Y'::bpchar]))),
	CONSTRAINT pk_bltb_user_new_post_infm PRIMARY KEY (biz_key, bltb_cd, user_id, post_seq_no)
);
