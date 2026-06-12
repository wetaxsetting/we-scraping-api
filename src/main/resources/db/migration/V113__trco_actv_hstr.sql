-- trco_actv_hstr definition

-- Drop table

-- DROP TABLE trco_actv_hstr;

CREATE TABLE trco_actv_hstr (
	actv_seq varchar(20) NOT NULL,
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	trco_key varchar(20) NOT NULL,
	trco_cmnm varchar(200) NULL,
	trco_cmenm varchar(200) NULL,
	trco_flnm varchar(100) NULL,
	api_apv_key varchar(50) NULL,
	dv_cd bpchar(1) NULL,
	task_cd varchar(100) NULL,
	bank_cd varchar(20) NULL,
	acct_no varchar(100) NULL,
	ccy_cd bpchar(3) NULL,
	actv_amt numeric(20, 6) DEFAULT 0 NULL,
	cont varchar(200) NULL,
	note varchar(255) NULL,
	pcsn_stts bpchar(1) DEFAULT 'U'::bpchar NULL,
	reg_dtm varchar(14) NOT NULL,
	rcv_nm varchar(100) NULL,
	rslt_cd varchar(10) NULL,
	rslt_cont varchar(200) NULL,
	mod_dtm varchar(14) NULL,
	mod_id varchar(100) NULL,
	trco_cnfm_yn varchar(1) NULL,
	trsc_dt varchar(8) NULL,
	CONSTRAINT trco_acvt_hstr_chk01 CHECK ((dv_cd = ANY (ARRAY['Q'::bpchar, 'S'::bpchar, 'D'::bpchar, 'G'::bpchar, 'P'::bpchar, 'B'::bpchar]))),
	CONSTRAINT trco_acvt_hstr_chk02 CHECK (((rslt_cd)::text = ANY (ARRAY[('C'::character varying)::text, ('U'::character varying)::text, ('D'::character varying)::text]))),
	CONSTRAINT trco_acvt_hstr_chk03 CHECK (((trco_cnfm_yn)::text = ANY (ARRAY[('0'::character varying)::text, ('1'::character varying)::text]))),
	CONSTRAINT trco_acvt_hstr_chk04 CHECK ((pcsn_stts = ANY (ARRAY['N'::bpchar, 'C'::bpchar, 'U'::bpchar]))),
	CONSTRAINT trco_acvt_hstr_pk PRIMARY KEY (actv_seq),
	CONSTRAINT trco_actv_hstr_fk02 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key)
);
