-- bltb_comnt_infm definition

-- Drop table

-- DROP TABLE bltb_comnt_infm;

CREATE TABLE bltb_comnt_infm (
	biz_key varchar(20) NOT NULL,
	bltb_cd varchar(15) NOT NULL,
	post_seq_no numeric(15) NOT NULL,
	comnt_seq_no numeric(15) NOT NULL,
	comnt_dv_cd bpchar(1) DEFAULT 'C'::bpchar NOT NULL,
	comnt_task_cd varchar(4) NOT NULL,
	comnt_cont varchar(1000) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	req_type bpchar(1) NULL,
	recp_key bpchar(20) NULL,
	CONSTRAINT bltb_comnt_infm_chk01 CHECK ((comnt_dv_cd = ANY (ARRAY['C'::bpchar, 'R'::bpchar]))),
	CONSTRAINT pk_bltb_comnt_infm PRIMARY KEY (biz_key, bltb_cd, post_seq_no, comnt_seq_no)
);
