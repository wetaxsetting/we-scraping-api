-- expt_resol_appr_line definition

-- Drop table

-- DROP TABLE expt_resol_appr_line;

CREATE TABLE expt_resol_appr_line (
	biz_key varchar(20) NOT NULL,
	appr_seq_no varchar(15) NOT NULL,
	appr_line_no varchar(1) NOT NULL,
	jbcl_nm varchar(100) NULL,
	empl_nm varchar(100) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_expt_resol_appr_line PRIMARY KEY (biz_key, appr_seq_no, appr_line_no)
);
