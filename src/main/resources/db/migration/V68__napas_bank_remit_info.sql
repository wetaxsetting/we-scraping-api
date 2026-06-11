-- napas_bank_remit_info definition

-- Drop table

-- DROP TABLE napas_bank_remit_info;

CREATE TABLE napas_bank_remit_info (
	bank_cd varchar(20) NOT NULL,
	napas_bank_cd varchar(10) NOT NULL,
	remit_yn bpchar(1) DEFAULT 'Y'::bpchar NOT NULL,
	CONSTRAINT napas_bank_remit_info_pk PRIMARY KEY (bank_cd, napas_bank_cd)
);
