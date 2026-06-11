-- tb_batch_schedule_dtl definition

-- Drop table

-- DROP TABLE tb_batch_schedule_dtl;

CREATE TABLE tb_batch_schedule_dtl (
	batch_id varchar(50) NOT NULL,
	out_bank_cd varchar(15) NOT NULL,
	ant_div_cd bpchar(5) NOT NULL,
	batch_start_tm bpchar(6) NOT NULL,
	batch_end_tm bpchar(6) NOT NULL,
	reg_dttm varchar(14) NULL,
	last_edtr varchar(20) NULL,
	CONSTRAINT pk_tb_batch_schedule_dtl PRIMARY KEY (batch_id, out_bank_cd, ant_div_cd)
);
