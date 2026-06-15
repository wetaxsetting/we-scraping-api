-- expt_resol_apprvr_infm definition

-- Drop table

-- DROP TABLE expt_resol_apprvr_infm;

CREATE TABLE expt_resol_apprvr_infm (
	biz_key varchar(20) NOT NULL,
	appr_seq_no varchar(15) NOT NULL,
	apprvr_id varchar(100) NOT NULL,
	apprvr_stts bpchar(1) NOT NULL,
	apprvr_nm varchar(100) NOT NULL,
	appr_dtm varchar(14) NULL,
	reg_id varchar(100) NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	read_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	apprvr_line_no numeric(15) NULL,
	appr_line_no numeric(15) NULL,
	CONSTRAINT pk_expt_resol_apprvr PRIMARY KEY (biz_key, appr_seq_no, apprvr_id)
);
