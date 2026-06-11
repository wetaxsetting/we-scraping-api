-- bltb_post_infm definition

-- Drop table

-- DROP TABLE bltb_post_infm;

CREATE TABLE bltb_post_infm (
	biz_key varchar(20) NOT NULL,
	bltb_cd varchar(15) NOT NULL,
	post_seq_no numeric(15) NOT NULL,
	post_title varchar(200) DEFAULT 'M'::character varying NOT NULL,
	post_type_cd varchar(2) DEFAULT '01'::character varying NOT NULL,
	post_appr_dv varchar(2) NULL,
	post_stts varchar(2) DEFAULT 'PL'::character varying NOT NULL,
	appr_seq_no varchar(15) NULL,
	pin_post_type varchar(2) NULL,
	pin_post_due_dt varchar(8) NULL,
	post_cont text NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	reg_pin_post_date varchar(14) NULL,
	ref_bltb_cd varchar(15) NULL,
	ref_post_seq_no numeric(15) NULL,
	reg_chnl varchar(3) DEFAULT 'PC'::character varying NULL,
	pc_mod_yn varchar(1) DEFAULT 'Y'::character varying NULL,
	req_type bpchar(1) NULL,
	CONSTRAINT bltb_post_infm_chk01 CHECK (((post_type_cd)::bpchar = ANY (ARRAY['01'::bpchar, '02'::bpchar, '03'::bpchar]))),
	CONSTRAINT bltb_post_infm_chk02 CHECK (((post_stts)::bpchar = ANY (ARRAY['PL'::bpchar, 'PV'::bpchar]))),
	CONSTRAINT pk_bltb_post_infm PRIMARY KEY (biz_key, bltb_cd, post_seq_no)
);

-- Table Triggers

create trigger bltb_post_infm_logtrigger after
insert
    or
delete
    or
update
    on
    bltb_post_infm for each row execute function bltb_post_infm_log();
