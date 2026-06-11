-- tb_firm_bank_code_cms definition

-- Drop table

-- DROP TABLE tb_firm_bank_code_cms;

CREATE TABLE tb_firm_bank_code_cms (
	ant_div_cd bpchar(5) NOT NULL,
	bank_cd varchar(15) NOT NULL,
	citad_bank_cd bpchar(3) NULL,
	bank_nm varchar(300) NOT NULL,
	bank_branch_nm varchar(300) NOT NULL,
	eng_abrvat_nm varchar(300) NULL,
	bank_eng_nm varchar(300) NULL,
	bank_branch_eng_nm varchar(300) NULL,
	bank_stas bpchar(1) NULL,
	bank_branch_div varchar(15) NULL,
	direct_bank_code varchar(15) NULL,
	standard_name varchar(200) NULL,
	reg_dttm varchar(14) NULL,
	mod_dttm varchar(14) NULL,
	CONSTRAINT pk_tb_firm_bank_code_cms PRIMARY KEY (ant_div_cd, bank_cd)
);
