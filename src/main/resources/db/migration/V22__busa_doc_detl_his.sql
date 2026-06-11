-- busa_doc_detl_his definition

-- Drop table

-- DROP TABLE busa_doc_detl_his;

CREATE TABLE busa_doc_detl_his (
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NULL,
	doc_id varchar(50) NOT NULL,
	disp_ord numeric(10) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	item_unit_amt numeric(20, 6) DEFAULT 0 NULL,
	item_nm varchar(255) NULL,
	item_spec varchar(100) NULL,
	item_qunt numeric(20, 6) NULL,
	item_cd varchar(10) NULL,
	sply_amt numeric(20, 6) DEFAULT 0 NULL,
	tax_amt numeric(20, 6) DEFAULT 0 NULL,
	note varchar(255) NULL,
	txtn_ptrn varchar(3) NULL,
	item_detl varchar(4000) NULL,
	mod_id varchar(100) NULL,
	mod_uid varchar(50) DEFAULT uuid_in(md5(random()::text || now()::text)::cstring) NOT NULL,
	stts varchar(1) NOT NULL,
	rcv_pay_amt numeric(20, 6) NULL,
	uom varchar(90) NULL,
	einv_productname varchar(500) NULL,
	einv_taxrate varchar(50) NULL,
	dc_amt numeric(20, 6) NULL,
	bef_dis_amt numeric(20, 6) NULL,
	CONSTRAINT busa_doc_detl_his_chk01 CHECK (((stts)::text = ANY (ARRAY[('C'::character varying)::text, ('U'::character varying)::text, ('D'::character varying)::text]))),
	CONSTRAINT busa_doc_detl_his_pk PRIMARY KEY (biz_key, doc_id, disp_ord, mod_dtm, mod_uid)
);
