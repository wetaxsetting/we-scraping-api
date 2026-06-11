-- trco_infm definition

-- Drop table

-- DROP TABLE trco_infm;

CREATE TABLE trco_infm (
	trco_key varchar(20) NOT NULL,
	biz_key varchar(20) NOT NULL,
	tax_id varchar(40) NOT NULL,
	trco_cmnm varchar(200) NOT NULL,
	trco_cmenm varchar(200) NOT NULL,
	trco_tax_id varchar(40) NULL,
	trco_rppr_nm varchar(100) NULL,
	trco_rprs_tlph varchar(30) NULL,
	trco_fxno varchar(30) NULL,
	trco_bsun_post_adrs varchar(400) NULL,
	trco_bsun_dtl_adrs varchar(400) NULL,
	trco_bsun_st varchar(200) NULL,
	trco_bsun_city varchar(200) NULL,
	trco_stts bpchar(1) NULL,
	trco_latd varchar(11) NULL,
	trco_lotd varchar(11) NULL,
	trsc_ccy_cd bpchar(3) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	trco_nick_nm varchar(200) NULL,
	trco_rppr_enm varchar(100) NULL,
	trco_wabooks_biz_key varchar(20) NULL,
	trco_type varchar(1) NULL,
	gst_no varchar(100) NULL,
	eml varchar(100) NULL,
	tax_accountant_login_id varchar(100) NULL,
	trco_inq_yn bpchar(1) DEFAULT 'Y'::bpchar NOT NULL,
	memo varchar(200) NULL,
	dflt_trco_key varchar(20) NULL,
	trco_paym_term varchar(3) NULL,
	trco_oprt_stts varchar(255) NULL,
	trco_scrp_srch_dtm varchar(14) NULL,
	sal_day varchar(2) NULL,
	agnc_wdrw_dt varchar(8) NULL,
	sel_fee varchar(10) NULL,
	cnrc_str_dt varchar(8) NULL,
	cnrc_end_dt varchar(8) NULL,
	token_expy_dt varchar(8) NULL,
	jrdt_txof varchar(100) NULL,
	decl_stts varchar(1) NULL,
	jnng_path varchar(1) NULL,
	jnng_path_dtl varchar(100) NULL,
	cnrc_stts varchar(1) NULL,
	pcsb varchar(100) NULL,
	hand_trco_key varchar(20) NULL,
	CONSTRAINT trco_infm_chk01 CHECK ((trsc_ccy_cd = ANY (ARRAY['VND'::bpchar, 'USD'::bpchar]))),
	CONSTRAINT trco_infm_pk PRIMARY KEY (trco_key),
	CONSTRAINT trco_infm_uk01 UNIQUE (biz_key, trco_nick_nm)
);
CREATE INDEX idx_trco_infm ON trco_infm USING btree (trco_key);

-- Table Triggers

create trigger trco_infm_logtrigger before
insert
    or
delete
    or
update
    on
    trco_infm for each row execute function trco_infm_log();
