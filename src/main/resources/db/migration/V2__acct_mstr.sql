-- acct_mstr definition

-- Drop table

-- DROP TABLE acct_mstr;

CREATE TABLE acct_mstr (
	biz_key varchar(20) NOT NULL,
	bank_cd varchar(20) NOT NULL,
	acct_no varchar(100) NOT NULL,
	acct_type_cd bpchar(2) NOT NULL,
	loan_nm varchar(100) NULL,
	acct_dv_cd bpchar(1) NULL,
	new_dt varchar(8) NULL,
	expi_dt varchar(8) NULL,
	ccy_cd bpchar(3) NOT NULL,
	agmt_amt numeric(20, 6) DEFAULT 0 NULL,
	corc_bf_bal numeric(20, 6) DEFAULT 0 NULL,
	bal numeric(20, 6) DEFAULT 0 NULL,
	real_amt numeric(20, 6) DEFAULT 0 NULL,
	pyat_dt varchar(8) NULL,
	pyat_amt numeric(20, 6) DEFAULT 0 NULL,
	cont_rt numeric(15, 5) NULL,
	acct_nick_nm varchar(50) NULL,
	hand_reg_yn varchar(1) NULL,
	acct_his_inq_stts varchar(20) NULL,
	acct_his_inq_msg varchar(255) NULL,
	acct_his_lst_inq_dt varchar(14) NULL,
	acct_bal_inq_stts varchar(20) NULL,
	acct_bal_inq_msg varchar(255) NULL,
	acct_bal_upd_dtm varchar(14) NULL,
	acct_bal_lst_inq_dtm varchar(14) NULL,
	cert_info_dv varchar(1) NULL,
	cert_info_con varchar(2000) NULL,
	prn_seq numeric(2) NULL,
	actv_yn bpchar(1) DEFAULT 'Y'::bpchar NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	nxisas_acct_type varchar(50) NULL,
	bank_cert_seq numeric(10) NULL,
	trn_lst_suc_dtm varchar(14) NULL,
	lst_lgn_stts varchar(20) NULL,
	lst_lgn_stts_msg varchar(255) NULL,
	lst_lgn_suc_dtm varchar(14) NULL,
	acct_cust_type varchar(1) DEFAULT 'C'::character varying NOT NULL,
	remit_out_acct_use_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	acct_pwd varchar(100) NULL,
	trns_pwd varchar(100) NULL,
	vas_acct_seq_no numeric(10) NULL,
	vas_acct_nm varchar(50) NULL,
	vas_acct_cd varchar(50) NULL,
	cont varchar(200) NULL,
	acct_nm varchar(100) NULL,
	CONSTRAINT acct_mstr_chk01 CHECK ((acct_type_cd = ANY (ARRAY['01'::bpchar, '02'::bpchar, '03'::bpchar, '04'::bpchar, '05'::bpchar, '06'::bpchar, '07'::bpchar, '08'::bpchar]))),
	CONSTRAINT acct_mstr_chk02 CHECK (
CASE
    WHEN (acct_type_cd = '01'::bpchar) THEN (acct_dv_cd = ANY (ARRAY['3'::bpchar, '4'::bpchar]))
    WHEN (acct_type_cd = '02'::bpchar) THEN (acct_dv_cd = ANY (ARRAY['1'::bpchar, '2'::bpchar]))
    ELSE NULL::boolean
END),
	CONSTRAINT acct_mstr_chk03 CHECK (((hand_reg_yn)::text = ANY (ARRAY[('Y'::character varying)::text, ('N'::character varying)::text]))),
	CONSTRAINT acct_mstr_chk05 CHECK ((actv_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT acct_mstr_chk06 CHECK ((ccy_cd = ANY (ARRAY['VND'::bpchar, 'USD'::bpchar, 'KRW'::bpchar]))),
	CONSTRAINT acct_mstr_pk PRIMARY KEY (biz_key, bank_cd, acct_no),
	CONSTRAINT acct_mstr_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key)
);

-- Table Triggers

create trigger acct_mstr_before_insert_or_update before
insert
    or
update
    of bank_cd on
    acct_mstr for each row execute function reject_not_existing_bank_cd();
create trigger acct_mstr_logtrigger after
insert
    or
delete
    or
update
    on
    acct_mstr for each row execute function acct_mstr_log();
