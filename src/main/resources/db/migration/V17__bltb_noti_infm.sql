-- bltb_noti_infm definition

-- Drop table

-- DROP TABLE bltb_noti_infm;

CREATE TABLE bltb_noti_infm (
	biz_key varchar(20) NOT NULL,
	bltb_cd varchar(15) NOT NULL,
	noti_seq_no numeric(20) NOT NULL,
	bltb_name varchar(50) NOT NULL,
	snd_id varchar(100) NOT NULL,
	snd_name varchar(200) NOT NULL,
	rcv_id varchar(100) NOT NULL,
	rcv_nm varchar(200) NOT NULL,
	noti_dt varchar(8) NOT NULL,
	noti_tm varchar(6) NOT NULL,
	noti_url varchar(100) NULL,
	actv_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	noti_wsvc varchar(100) NULL,
	noti_json varchar(2000) NULL,
	post_seq_no numeric(15) NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NULL,
	snd_biz_key varchar(20) NULL,
	CONSTRAINT bltb_noti_infm_chk01 CHECK (((actv_yn)::bpchar = ANY (ARRAY['N'::bpchar, 'Y'::bpchar]))),
	CONSTRAINT pk_bltb_noti_infm PRIMARY KEY (biz_key, bltb_cd, noti_seq_no)
);
