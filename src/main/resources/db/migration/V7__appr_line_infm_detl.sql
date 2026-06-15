-- appr_line_infm_detl definition

-- Drop table

-- DROP TABLE appr_line_infm_detl;

CREATE TABLE appr_line_infm_detl (
	biz_key varchar(20) NOT NULL,
	appr_line_no numeric(15) NOT NULL,
	apprvr_disp_ord numeric(1) NOT NULL,
	apprvr_id varchar(100) NOT NULL,
	apprvr_nm varchar(100) NOT NULL,
	apprvr_posi varchar(100) NULL,
	apprvr_dept varchar(100) NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_appr_line_infm_detl PRIMARY KEY (biz_key, appr_line_no, apprvr_id)
);
