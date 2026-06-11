-- sys_usag_set_infm definition

-- Drop table

-- DROP TABLE sys_usag_set_infm;

CREATE TABLE sys_usag_set_infm (
	use_usag_cd varchar(20) NOT NULL,
	use_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL,
	disp_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL,
	disp_ord numeric(10) NULL,
	CONSTRAINT sys_usag_set_infm_pk PRIMARY KEY (use_usag_cd),
	CONSTRAINT sys_usag_set_infm_fk01 FOREIGN KEY (use_usag_cd) REFERENCES usag_infm(use_usag_cd)
);
