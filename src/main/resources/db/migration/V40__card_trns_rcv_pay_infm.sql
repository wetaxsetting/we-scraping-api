-- card_trns_rcv_pay_infm definition

-- Drop table

-- DROP TABLE card_trns_rcv_pay_infm;

CREATE TABLE card_trns_rcv_pay_infm (
	biz_key varchar(20) NOT NULL,
	bank_cd varchar(20) NOT NULL,
	card_no varchar(100) NOT NULL,
	trsc_dt varchar(8) NOT NULL,
	card_seq_no varchar(20) NOT NULL,
	rcv_pay_seq_no numeric(5) NOT NULL,
	rcv_pay_amt numeric(20, 6) NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	mod_id varchar(100) NOT NULL,
	mod_dtm varchar(14) NOT NULL,
	CONSTRAINT pk_card_trns_rcv_pay_infm PRIMARY KEY (biz_key, card_seq_no, rcv_pay_seq_no)
);
