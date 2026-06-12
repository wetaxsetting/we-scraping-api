-- tb_batch_schedule_info definition

-- Drop table

-- DROP TABLE tb_batch_schedule_info;

CREATE TABLE tb_batch_schedule_info (
	batch_id varchar(50) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	ant_where_use_yn bpchar(1) DEFAULT 'N'::bpchar NULL,
	batch_start_tm bpchar(6) NOT NULL,
	batch_end_tm bpchar(6) NOT NULL,
	reg_dttm varchar(14) NULL,
	mod_dttm varchar(14) NULL,
	last_edtr varchar(20) NULL,
	CONSTRAINT pk_tb_batch_schedule_info PRIMARY KEY (batch_id, out_bank_cd)
);
