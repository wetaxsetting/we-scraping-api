-- blocked_chars_rule definition

-- Drop table

-- DROP TABLE blocked_chars_rule;

CREATE TABLE blocked_chars_rule (
	rule_id varchar(4) NOT NULL,
	bank_cd varchar(15) NOT NULL,
	bank_nm varchar(100) NOT NULL,
	pymn_tpye varchar(4) NOT NULL,
	field_cd varchar(4) NOT NULL,
	blocked_chars varchar(100) NOT NULL,
	eff_fr_dt varchar(14) NULL,
	eff_to_dt varchar(14) NULL,
	"version" varchar(2) NOT NULL,
	status bpchar(1) DEFAULT 'Y'::bpchar NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_blocked_chars_rule PRIMARY KEY (rule_id)
);
