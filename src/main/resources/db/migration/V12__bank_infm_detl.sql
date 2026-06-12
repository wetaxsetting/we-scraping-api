-- bank_infm_detl definition

-- Drop table

-- DROP TABLE bank_infm_detl;

CREATE TABLE bank_infm_detl (
	bank_cd varchar(20) NOT NULL,
	bank_detl_info_cd varchar(3) NOT NULL,
	start_date varchar(8) NOT NULL,
	end_date varchar(8) NOT NULL,
	bank_detl_info_cont varchar(2000) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	seq_no numeric(10) DEFAULT '0'::numeric NOT NULL,
	CONSTRAINT pk_bank_infm_detl PRIMARY KEY (bank_cd, bank_detl_info_cd, start_date, end_date, seq_no)
);
