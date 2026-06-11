-- trco_grp_bsc_cstm definition

-- Drop table

-- DROP TABLE trco_grp_bsc_cstm;

CREATE TABLE trco_grp_bsc_cstm (
	biz_key varchar(20) NOT NULL,
	grp_cd varchar(10) DEFAULT 'E'::character varying NOT NULL,
	tax_id varchar(40) NULL,
	grp_nm varchar(100) DEFAULT '기타'::character varying NOT NULL,
	grp_stts bpchar(1) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	CONSTRAINT trco_grp_bsc_cstm_check CHECK (((grp_cd)::text = ANY (ARRAY[('S'::character varying)::text, ('B'::character varying)::text, ('F'::character varying)::text, ('E'::character varying)::text]))),
	CONSTRAINT trco_grp_bsc_cstm_chk02 CHECK ((grp_stts = ANY (ARRAY['Y'::bpchar, 'C'::bpchar]))),
	CONSTRAINT trco_grp_bsc_cstm_pk PRIMARY KEY (biz_key, grp_cd),
	CONSTRAINT trco_grp_bsc_cstm_fk01 FOREIGN KEY (grp_cd) REFERENCES trco_grp_bsc_sys(grp_cd)
);
