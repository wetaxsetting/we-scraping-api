-- expt_resol_detl definition

-- Drop table

-- DROP TABLE expt_resol_detl;

CREATE TABLE expt_resol_detl (
	biz_key varchar(20) NOT NULL,
	appr_seq_no varchar(15) NOT NULL,
	appr_detl_seq_no numeric(5) NOT NULL,
	trco_key varchar(20) NULL,
	trco_cmnm varchar(200) NULL,
	trco_cmenm varchar(200) NULL,
	ccy_cd bpchar(3) NOT NULL,
	amt numeric(20, 6) DEFAULT 0 NOT NULL,
	busa_usag_cd varchar(20) NULL,
	memo varchar(1000) NULL,
	doc_id varchar(50) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	doc_type bpchar(1) NULL,
	trsc_dt varchar(8) NULL,
	CONSTRAINT expt_resol_detl_chk01 CHECK ((ccy_cd = ANY (ARRAY['VND'::bpchar, 'USD'::bpchar, 'KRW'::bpchar]))),
	CONSTRAINT expt_resol_detl_chk02 CHECK ((doc_type = ANY (ARRAY['B'::bpchar, 'E'::bpchar, ''::bpchar]))),
	CONSTRAINT expt_resol_detl_pk PRIMARY KEY (biz_key, appr_seq_no, appr_detl_seq_no),
	CONSTRAINT expt_resol_detl_fk01 FOREIGN KEY (biz_key,appr_seq_no) REFERENCES expt_resol_mstr(biz_key,appr_seq_no)
);
