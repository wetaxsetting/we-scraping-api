-- trco_info_file definition

-- Drop table

-- DROP TABLE trco_info_file;

CREATE TABLE trco_info_file (
	trco_key varchar(20) NOT NULL,
	file_id varchar(100) NOT NULL,
	org_file_nm varchar(500) NOT NULL,
	save_file_nm varchar(500) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	reg_nm varchar(100) NOT NULL,
	file_size varchar(100) NULL,
	trco_infm_titl varchar(50) DEFAULT '기타문서'::character varying NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT trco_info_file_pkey PRIMARY KEY (trco_key, file_id),
	CONSTRAINT trco_info_file_fk01 FOREIGN KEY (trco_key) REFERENCES trco_infm(trco_key)
);
