-- busi_infm definition

-- Drop table

-- DROP TABLE busi_infm;

CREATE TABLE busi_infm (
	biz_key varchar(20) NOT NULL,
	cmpn_nm varchar(200) NOT NULL,
	cmpn_enm varchar(200) NOT NULL,
	tax_id varchar(40) NOT NULL,
	rppr_nm varchar(100) NOT NULL,
	tel_no varchar(30) NULL,
	fxno varchar(30) NULL,
	eml varchar(100) NOT NULL,
	rprs_bsun_post_adrs varchar(200) NOT NULL,
	rprs_bsun_dtl_adrs varchar(200) NULL,
	rprs_bsun_city varchar(200) NOT NULL,
	rprs_bsun_st varchar(200) NOT NULL,
	cmpn_logo varchar(500) NULL,
	join_dt varchar(14) NOT NULL,
	fst_join_nm varchar(100) NOT NULL,
	fst_join_tel varchar(100) NOT NULL,
	fst_use_date varchar(14) NULL,
	lst_pay_date varchar(8) NULL,
	use_date_from varchar(8) NULL,
	use_date_to varchar(8) NULL,
	busi_stts varchar(1) NULL,
	ntnl_cd varchar(2) DEFAULT 'KH'::character varying NOT NULL,
	free_use_date_from varchar(8) NULL,
	free_use_date_to varchar(8) NULL,
	bank_cd varchar(20) NULL,
	acct_no varchar(100) NULL,
	chnl_id varchar(10) DEFAULT 'CAM0001'::character varying NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	overdue_yn varchar(1) DEFAULT 'N'::character varying NULL,
	canc_date varchar(8) NULL,
	wiz_run_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	cmpn_stamp varchar NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NULL,
	busi_nick_nm varchar(200) NULL,
	alli_cmpn_cd varchar(20) NULL,
	alli_cmpn_nm varchar(200) NULL,
	prdt_type varchar(1) DEFAULT 'W'::character varying NOT NULL,
	owac_nm varchar(200) NULL,
	memo varchar(200) NULL,
	paid_start_date varchar(8) NULL,
	cmpn_qr_cd_img varchar(500) NULL,
	erp_cd bpchar(1) NULL,
	CONSTRAINT busi_infm_chk01 CHECK (((prdt_type)::bpchar = ANY (ARRAY['W'::bpchar, 'R'::bpchar, 'B'::bpchar, 'E'::bpchar, 'L'::bpchar, 'S'::bpchar, 'P'::bpchar, 'T'::bpchar, 'U'::bpchar, 'C'::bpchar, 'D'::bpchar, 'F'::bpchar, 'M'::bpchar, 'H'::bpchar]))),
	CONSTRAINT busi_infm_pk PRIMARY KEY (biz_key),
	CONSTRAINT busi_infm_uk01 UNIQUE (chnl_id, tax_id, ntnl_cd),
	CONSTRAINT busi_infm_fk01 FOREIGN KEY (ntnl_cd) REFERENCES natn_infm(ntnl_cd),
	CONSTRAINT busi_infm_fk02 FOREIGN KEY (chnl_id) REFERENCES chnl_infm(chnl_id)
);

-- Table Triggers

create trigger busi_infm_logtrigger after
insert
    or
delete
    or
update
    on
    busi_infm for each row execute function busi_infm_log();
