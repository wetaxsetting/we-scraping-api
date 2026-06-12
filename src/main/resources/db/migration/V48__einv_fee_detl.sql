-- einv_fee_detl definition

-- Drop table

-- DROP TABLE einv_fee_detl;

CREATE TABLE einv_fee_detl (
	biz_key varchar(20) NOT NULL,
	doc_id varchar(50) NOT NULL,
	disp_ord numeric(10) NOT NULL,
	einv_feename varchar(100) NULL,
	einv_fee numeric(20, 6) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_einv_fee_detl PRIMARY KEY (biz_key, doc_id, disp_ord)
);

-- Table Triggers

create trigger einv_fee_detl_logtrigger after
insert
    or
delete
    or
update
    on
    einv_fee_detl for each row execute function einv_fee_detl_log();
