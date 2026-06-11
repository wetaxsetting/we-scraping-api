-- trco_busi_infm_sumr definition

-- Drop table

-- DROP TABLE trco_busi_infm_sumr;

CREATE TABLE trco_busi_infm_sumr (
	biz_key varchar(20) NULL,
	issu_dt varchar(8) NULL,
	inout_dv bpchar(1) NULL,
	trsc_type varchar(1) NULL,
	trco_key varchar(20) NULL,
	seq_no numeric(10) NULL,
	trco_nm varchar(200) NULL,
	trco_enm varchar(200) NULL,
	trco_tax_id varchar(40) NULL,
	ccy_cd bpchar(3) NULL,
	sply_amt numeric(20, 6) NULL,
	tax_amt numeric(20, 6) NULL,
	totl_amt numeric(20, 6) NULL,
	use_usag_cd varchar(20) NULL,
	prjt_no varchar(20) NULL,
	ref_no1 varchar(100) NULL,
	ref_no2 varchar(100) NULL,
	ref_no3 varchar(100) NULL,
	ref_no4 varchar(100) NULL,
	ref_no5 varchar(100) NULL,
	ref_no6 varchar(100) NULL,
	cont varchar(200) NULL
);
CREATE INDEX idx_trco_busi_infm_sumr ON trco_busi_infm_sumr USING btree (biz_key, trco_key, ccy_cd, issu_dt, inout_dv, trsc_type);
