-- acct_auth_info definition

-- Drop table

-- DROP TABLE acct_auth_info;

CREATE TABLE acct_auth_info (
    biz_key varchar(20) NOT NULL,
    bank_cd varchar(20) NOT NULL,
    acct_no varchar(100) NOT NULL,
    user_id varchar(100) NOT NULL,
    use_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL,
    reg_id varchar(100) NOT NULL,
    reg_dtm varchar(14) NOT NULL,
    mod_id varchar(100) NULL,
    mod_dtm varchar(14) NULL,
    auth_type bpchar(1) DEFAULT 'V'::bpchar NOT NULL,
    CONSTRAINT acct_auth_info_pk PRIMARY KEY (biz_key, bank_cd, acct_no, user_id, auth_type),
    CONSTRAINT acct_auth_info_fk01 FOREIGN KEY (biz_key,bank_cd,acct_no) REFERENCES acct_mstr(biz_key,bank_cd,acct_no)
);
