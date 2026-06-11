-- napas_bank_info definition

-- Drop table

-- DROP TABLE napas_bank_info;

CREATE TABLE napas_bank_info (
	napas_bank_cd varchar(10) NOT NULL,
	short_name varchar(100) NOT NULL,
	long_name_en varchar(500) NULL,
	long_name_vn varchar(500) NULL,
	bank_cd varchar(20) NULL,
	CONSTRAINT napas_bank_info_pk PRIMARY KEY (napas_bank_cd)
);
