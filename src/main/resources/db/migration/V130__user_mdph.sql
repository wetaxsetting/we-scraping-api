-- user_mdph definition

-- Drop table

-- DROP TABLE user_mdph;

CREATE TABLE user_mdph (
	user_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	biz_key varchar(20) NULL,
	user_nm varchar(100) NOT NULL,
	clph_no varchar(100) NOT NULL,
	eml varchar(100) NOT NULL,
	empl_no varchar(20) NULL,
	user_img varchar(500) NULL,
	actv_yn varchar(1) NULL,
	lock_yn varchar(1) NULL,
	mod_id varchar(100) NULL,
	stts varchar(1) NULL,
	mod_uid varchar(50) DEFAULT uuid_in(md5(random()::text || now()::text)::cstring) NOT NULL,
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL,
	dept_nm varchar(100) NULL,
	posi_nm varchar(100) NULL,
	CONSTRAINT user_mdph_chk01 CHECK (((stts)::text = ANY (ARRAY[('C'::character varying)::text, ('U'::character varying)::text, ('D'::character varying)::text]))),
	CONSTRAINT user_mdph_pk PRIMARY KEY (user_id, mod_dtm, mod_uid)
);
