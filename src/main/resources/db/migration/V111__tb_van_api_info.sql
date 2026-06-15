-- tb_van_api_info definition

-- Drop table

-- DROP TABLE tb_van_api_info;

CREATE TABLE tb_van_api_info (
	van_org_cd varchar(20) NOT NULL,
	api_key varchar(100) NOT NULL,
	CONSTRAINT pk_tb_van_api_info PRIMARY KEY (van_org_cd)
);
