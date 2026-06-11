-- busa_doc_detl definition

-- Drop table

-- DROP TABLE busa_doc_detl;

CREATE TABLE busa_doc_detl (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NULL,
	doc_id varchar(50) NOT NULL,
	disp_ord numeric(10) NOT NULL,
	item_unit_amt numeric(20, 6) DEFAULT 0 NULL,
	item_nm varchar(255) NULL,
	item_spec varchar(100) NULL,
	item_qunt numeric(20, 6) NULL,
	item_cd varchar(10) NOT NULL,
	sply_amt numeric(20, 6) DEFAULT 0 NULL,
	tax_amt numeric(20, 6) DEFAULT 0 NULL,
	note varchar(255) NULL,
	txtn_ptrn varchar(3) NULL,
	item_detl varchar(4000) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	rcv_pay_amt numeric(20, 6) NULL,
	uom varchar(90) NULL,
	einv_productname varchar(500) NULL,
	einv_taxrate varchar(50) NULL,
	dc_amt numeric(20, 6) NULL,
	bef_dis_amt numeric(20, 6) NULL,
	CONSTRAINT busa_doc_detl_pk PRIMARY KEY (biz_key, doc_id, disp_ord),
	CONSTRAINT busa_doc_detl_fk01 FOREIGN KEY (biz_key,doc_id) REFERENCES busa_doc_mstr(biz_key,doc_id)
);

-- Table Triggers

create trigger busa_doc_detl_logtrigger after
insert
    or
delete
    or
update
    on
    busa_doc_detl for each row execute function busa_doc_detl_log();
