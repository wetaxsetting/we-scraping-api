-- bank_infm definition

-- Drop table

-- DROP TABLE bank_infm;

CREATE TABLE bank_infm (
	bank_cd varchar(20) NOT NULL,
	start_date varchar(8) NOT NULL,
	end_date varchar(8) NOT NULL,
	rprs_bank_nm varchar(200) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	CONSTRAINT pk_bank_infm PRIMARY KEY (bank_cd, start_date, end_date)
);
