-- appr_line_infm definition

-- Drop table

-- DROP TABLE appr_line_infm;

CREATE TABLE appr_line_infm (
	biz_key varchar(20) NOT NULL,
	appr_line_no numeric(15) NOT NULL,
	appr_line_nm varchar(100) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_appr_line_infm PRIMARY KEY (biz_key, appr_line_no)
);
