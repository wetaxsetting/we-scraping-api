-- pgro_bsac_lnkn_infm definition

-- Drop table

-- DROP TABLE pgro_bsac_lnkn_infm;

CREATE TABLE pgro_bsac_lnkn_infm (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	grp_cd varchar(10) NOT NULL,
	trco_key varchar(20) NOT NULL,
	tax_id varchar(40) NULL,
	reg_dtm varchar(14) NULL,
	CONSTRAINT pgro_bsac_lnkn_infm_pk PRIMARY KEY (biz_key, user_id, grp_cd, trco_key),
	CONSTRAINT pgro_bsac_lnkn_infm_uk01 UNIQUE (biz_key, grp_cd, trco_key),
	CONSTRAINT pgro_bsac_lnkn_infm_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key),
	CONSTRAINT pgro_bsac_lnkn_infm_fk02 FOREIGN KEY (trco_key) REFERENCES trco_infm(trco_key)
);

-- Table Triggers

create trigger pgro_bsac_lnkn_infm_logtrigger after
insert
    or
delete
    or
update
    on
    pgro_bsac_lnkn_infm for each row execute function pgro_bsac_lnkn_infm_log();
