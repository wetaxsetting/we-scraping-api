-- trco_acct_info definition

-- Drop table

-- DROP TABLE trco_acct_info;

CREATE TABLE trco_acct_info (
	biz_key varchar(20) NOT NULL,
	trco_key varchar(20) NOT NULL,
	trco_acct_key varchar(20) NOT NULL,
	bank_cd varchar(20) NULL,
	acct_no varchar(100) NOT NULL,
	disp_ord numeric(10) NOT NULL,
	trco_nm varchar(200) NULL,
	trco_enm varchar(200) NULL,
	owac_nm varchar(50) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	bank_nm varchar(200) NULL,
	napas_bank_cd varchar(10) NULL,
	napas_bank_yn varchar(1) DEFAULT 'N'::character varying NULL,
	finc_mda_owner_nm varchar(150) NULL,
	citad_bank_cd varchar(8) NULL,
	owac_addr varchar(500) NULL,
	acct_ccy_cd varchar(3) NULL,
	CONSTRAINT trco_acct_info_pk PRIMARY KEY (biz_key, trco_key, trco_acct_key),
	CONSTRAINT trco_acct_info_fk01 FOREIGN KEY (trco_key) REFERENCES trco_infm(trco_key)
);

-- Table Triggers

create trigger trco_acct_info_before_insert_or_update before
insert
    or
update
    of bank_cd on
    trco_acct_info for each row execute function reject_not_existing_bank_cd();
