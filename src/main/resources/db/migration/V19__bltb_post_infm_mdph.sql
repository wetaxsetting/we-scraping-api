-- bltb_post_infm_mdph definition

-- Drop table

-- DROP TABLE bltb_post_infm_mdph;

CREATE TABLE bltb_post_infm_mdph (
	biz_key varchar(20) NOT NULL,
	bltb_cd varchar(15) NOT NULL,
	post_seq_no numeric(15) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	post_title varchar(200) DEFAULT 'M'::character varying NOT NULL,
	post_type_cd varchar(2) DEFAULT '01'::character varying NOT NULL,
	post_appr_dv varchar(2) DEFAULT '00'::character varying NULL,
	post_stts varchar(2) DEFAULT 'PL'::character varying NOT NULL,
	appr_seq_no varchar(15) NULL,
	pin_post_type varchar(2) NULL,
	pin_post_due_dt varchar(8) NULL,
	reg_pin_post_date varchar(14) NULL,
	post_cont text NULL,
	stts varchar(1) NULL,
	mod_id varchar(100) NOT NULL,
	mod_uid varchar(50) DEFAULT uuid_in(md5(random()::text || now()::text)::cstring) NOT NULL,
	ref_bltb_cd varchar(15) NULL,
	ref_post_seq_no numeric(15) NULL,
	reg_chnl varchar(3) DEFAULT 'PC'::character varying NULL,
	pc_mod_yn varchar(1) DEFAULT 'Y'::character varying NULL,
	CONSTRAINT pk_bltb_post_infm_mdph PRIMARY KEY (biz_key, bltb_cd, post_seq_no, mod_uid)
);
