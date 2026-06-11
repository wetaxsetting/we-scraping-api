-- citad_bank_cd_lookup definition

-- Drop table

-- DROP TABLE citad_bank_cd_lookup;

CREATE TABLE citad_bank_cd_lookup (
	bank_cd varchar(20) NOT NULL,
	citad_bank_cd varchar(20) NULL,
	use_yn bpchar(1) DEFAULT 'Y'::bpchar NOT NULL,
	CONSTRAINT pk_citad_bank_cd_lookup PRIMARY KEY (bank_cd)
);
