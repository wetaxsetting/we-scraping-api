-- trco_grp_bsc_sys definition

-- Drop table

-- DROP TABLE trco_grp_bsc_sys;

CREATE TABLE trco_grp_bsc_sys (
	grp_cd varchar(10) NOT NULL,
	grp_nm varchar(100) NOT NULL,
	grp_stts bpchar(1) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	CONSTRAINT trco_grp_bsc_sys_chk01 CHECK (((grp_cd)::text = ANY (ARRAY[('S'::character varying)::text, ('B'::character varying)::text, ('F'::character varying)::text, ('E'::character varying)::text]))),
	CONSTRAINT trco_grp_bsc_sys_chk02 CHECK ((grp_stts = ANY (ARRAY['Y'::bpchar, 'C'::bpchar]))),
	CONSTRAINT trco_grp_bsc_sys_pk PRIMARY KEY (grp_cd)
);
