-- einv_tax_detl_his definition

-- Drop table

-- DROP TABLE einv_tax_detl_his;

CREATE TABLE einv_tax_detl_his (
	biz_key varchar(20) NOT NULL,
	doc_id varchar(50) NOT NULL,
	disp_ord numeric(10) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	einv_taxrate varchar(50) NULL,
	amt numeric(20, 6) NULL,
	tax_amt numeric(20, 6) NULL,
	stts varchar(1) NOT NULL,
	CONSTRAINT pk_einv_tax_detl_his PRIMARY KEY (biz_key, doc_id, disp_ord, mod_dtm)
);
