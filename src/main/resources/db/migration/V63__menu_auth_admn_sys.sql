-- menu_auth_admn_sys definition

-- Drop table

-- DROP TABLE menu_auth_admn_sys;

CREATE TABLE menu_auth_admn_sys (
	web_svc_id varchar(50) NOT NULL,
	mnu_nm varchar(100) NULL,
	cont varchar(200) NULL,
	disp_ord numeric(10) NULL,
	prdt_type varchar(1) DEFAULT 'F'::character varying NOT NULL,
	CONSTRAINT menu_auth_admn_sys_pk PRIMARY KEY (web_svc_id, prdt_type)
);
