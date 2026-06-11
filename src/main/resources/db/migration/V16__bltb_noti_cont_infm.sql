-- bltb_noti_cont_infm definition

-- Drop table

-- DROP TABLE bltb_noti_cont_infm;

CREATE TABLE bltb_noti_cont_infm (
	biz_key varchar(20) NOT NULL,
	noti_seq_no numeric(20) NOT NULL,
	lang_cd varchar(2) NOT NULL,
	titl varchar(100) NULL,
	cont varchar(200) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	bltb_cd varchar(15) NOT NULL,
	CONSTRAINT pk_bltb_noti_cont_infm PRIMARY KEY (biz_key, bltb_cd, noti_seq_no, lang_cd)
);
