-- trco_rspb_infm definition

-- Drop table

-- DROP TABLE trco_rspb_infm;

CREATE TABLE trco_rspb_infm (
	rspb_key varchar(20) NOT NULL,
	biz_key varchar(20) NOT NULL,
	trco_key varchar(20) NOT NULL,
	"name" varchar(50) NULL,
	clph_no varchar(100) NULL,
	eml varchar(100) NULL,
	tax_chrg bpchar(1) NULL,
	memo varchar(200) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	CONSTRAINT trco_rspb_infm_chk01 CHECK ((tax_chrg = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT trco_rspb_infm_pk PRIMARY KEY (rspb_key),
	CONSTRAINT trco_rspb_infm_fk01 FOREIGN KEY (trco_key) REFERENCES trco_infm(trco_key),
	CONSTRAINT trco_rspb_infm_fk02 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key)
);