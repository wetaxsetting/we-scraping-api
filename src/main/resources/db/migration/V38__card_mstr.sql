-- card_mstr definition

-- Drop table

-- DROP TABLE card_mstr;

CREATE TABLE card_mstr (
	biz_key varchar(20) NOT NULL,
	bank_cd varchar(20) NOT NULL,
	card_no varchar(100) NOT NULL,
	card_type_cd bpchar(2) NOT NULL,
	issu_dt varchar(8) NULL,
	expi_dt varchar(8) NULL,
	card_nm varchar(50) NULL,
	card_nick_nm varchar(50) NULL,
	card_hldr_nm varchar(50) NOT NULL,
	card_his_inq_stts varchar(20) NULL,
	card_his_inq_msg varchar(255) NULL,
	card_his_lst_inq_dt varchar(14) NULL,
	prn_seq numeric(2) NULL,
	actv_yn bpchar(1) DEFAULT 'Y'::bpchar NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	nxisas_acct_type varchar(50) NULL,
	bank_cert_seq numeric(10) NULL,
	trn_lst_suc_dtm varchar(14) NULL,
	lst_lgn_stts varchar(20) NULL,
	lst_lgn_stts_msg varchar(255) NULL,
	lst_lgn_suc_dtm varchar(14) NULL,
	pymn_acct_no varchar(50) NULL,
	CONSTRAINT pk_card_mstr PRIMARY KEY (biz_key, bank_cd, card_no)
);
