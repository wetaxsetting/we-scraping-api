-- tb_firm_conf definition

-- Drop table

-- DROP TABLE tb_firm_conf;

CREATE TABLE tb_firm_conf (
	biz_key varchar(20) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	bank_prtn_id varchar(20) NOT NULL,
	van_org_cd varchar(20) NOT NULL,
	org_stat varchar(1) DEFAULT '0'::character varying NOT NULL,
	wdrl_act_no varchar(100) NOT NULL,
	file_type bpchar(3) NOT NULL,
	bat_tran_branch_div bpchar(1) DEFAULT 'Y'::bpchar NOT NULL,
	bat_tran_branch_limit numeric(7) DEFAULT '2000'::numeric NOT NULL,
	reg_dttm varchar(14) NOT NULL,
	start_dttm varchar(14) NULL,
	end_dttm varchar(14) NULL,
	mod_dttm varchar(14) NULL,
	last_edtr varchar(20) NULL,
	CONSTRAINT pk_tb_firm_conf PRIMARY KEY (biz_key, out_bank_cd, wdrl_act_no)
);
