-- chart_acct_infm definition

-- Drop table

-- DROP TABLE chart_acct_infm;

CREATE TABLE chart_acct_infm (
	biz_key varchar(20) NOT NULL,
	chart_acct_seq_no varchar(10) NOT NULL,
	chart_acct_cd varchar(20) NOT NULL,
	chart_acct_nm_en varchar(255) NOT NULL,
	chart_acct_nm_vn varchar(255) NULL,
	chart_acct_nm_ko varchar(255) NULL,
	chart_acct_type varchar(100) NULL,
	parent_chart_acct_cd varchar(20) NULL,
	stts bpchar(1) NOT NULL,
	del_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	cal_rule bpchar(1) NULL,
	CONSTRAINT pk_chart_acct_infm PRIMARY KEY (biz_key, chart_acct_seq_no),
	CONSTRAINT uk_chart_acct_cd UNIQUE (biz_key, chart_acct_cd, del_yn)
);
