-- pgro_bsac_lnkn_his definition

-- Drop table

-- DROP TABLE pgro_bsac_lnkn_his;

CREATE TABLE pgro_bsac_lnkn_his (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	grp_cd varchar(10) NOT NULL,
	trco_key varchar(20) NOT NULL,
	tax_id varchar(40) NULL,
	reg_dtm varchar(14) NOT NULL,
	stts varchar(1) NOT NULL,
	CONSTRAINT pgro_bsac_lnkn_his_chk01 CHECK (((stts)::text = ANY (ARRAY[('C'::character varying)::text, ('U'::character varying)::text, ('D'::character varying)::text])))
);
