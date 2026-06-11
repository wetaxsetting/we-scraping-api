-- empl_infm definition

-- Drop table

-- DROP TABLE empl_infm;

CREATE TABLE empl_infm (
	biz_key varchar(20) NOT NULL,
	empl_no varchar(20) NOT NULL,
	"_empl_nm" varchar(100) NULL,
	jbcl_nm varchar(100) NULL,
	dept_nm varchar(100) NULL,
	clph_no varchar(100) NULL,
	eml varchar(100) NULL,
	cmpn_join_date varchar(8) NULL,
	cmpn_fire_date varchar(8) NULL,
	empl_resi_st varchar(200) NULL,
	empl_resi_city varchar(200) NULL,
	empl_resi_dtl_adrs varchar(200) NULL,
	empl_resi_post_adrs varchar(200) NULL,
	user_img varchar(500) NULL,
	natn_id varchar(20) NULL,
	bank_cd varchar(20) NULL,
	acct_no varchar(100) NULL,
	mapp_seup_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	mapp_noti_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	payroll_page_reg_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	wabooks_emp_mng_reg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NULL,
	mod_dtm varchar(14) NULL,
	tel_no varchar(100) NULL,
	empl_no_auto_gen_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
	r_empl_no varchar(50) NOT NULL,
	gender_cd bpchar(1) NULL,
	birth_date varchar(8) NULL,
	natn_num_cd varchar(5) NULL,
	citizen_natn_num_cd varchar(5) NULL,
	empl_iden_type bpchar(1) NULL,
	emer_clph varchar(100) NULL,
	resi_yn bpchar(1) NULL,
	mari_yn bpchar(1) NULL,
	child_dep numeric NULL,
	efil_dept_nm varchar(100) NULL,
	eng_fst_nm varchar(100) NULL,
	eng_lst_nm varchar(100) NULL,
	nat_fst_nm varchar(100) NULL,
	nat_lst_nm varchar(100) NULL,
	actv_yn bpchar(1) DEFAULT 'Y'::bpchar NOT NULL,
	CONSTRAINT empl_infm_pk PRIMARY KEY (biz_key, empl_no),
	CONSTRAINT empl_infm_uk01 UNIQUE (biz_key, r_empl_no),
	CONSTRAINT empl_infm_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key)
);

-- Table Triggers

create trigger empl_infm_logtrigger after
insert
    or
delete
    or
update
    on
    empl_infm for each row execute function empl_infm_log();
