-- sys_doc_id_gen definition

-- Drop table

-- DROP TABLE sys_doc_id_gen;

CREATE TABLE sys_doc_id_gen (
	biz_key varchar(20) NOT NULL,
	doc_type bpchar(1) NOT NULL,
	req_date varchar(8) NOT NULL,
	doc_id varchar(50) NULL,
	CONSTRAINT sys_doc_id_gen_pk PRIMARY KEY (biz_key, doc_type, req_date)
);
