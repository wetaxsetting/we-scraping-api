-- msg_snd_his definition

-- Drop table

-- DROP TABLE msg_snd_his;

CREATE TABLE msg_snd_his (
	reg_seq_no numeric(20) NOT NULL,
	biz_key varchar(20) NOT NULL,
	user_id varchar(100) NOT NULL,
	msg_type varchar(1) NOT NULL,
	snd_date varchar(8) NULL,
	snd_time varchar(6) NULL,
	rcv_nm varchar(100) NOT NULL,
	rcv_no varchar(100) NOT NULL,
	snd_cont varchar(2000) NOT NULL,
	snd_rslt varchar(10) NOT NULL,
	err_cont varchar(1000) NULL,
	stts varchar(1) DEFAULT '0'::character varying NULL,
	snd_str text NULL,
	rcv_str text NULL,
	push_badge_count numeric(5) NULL,
	snd_titl text NULL,
	push_action_url varchar(250) NULL,
	CONSTRAINT pk_msg_snd_his PRIMARY KEY (reg_seq_no)
);
