-- V3: normalise CHAR(1) YN flags to VARCHAR(1) to match the JPA @Column
-- length-only mapping (Hibernate generates VARCHAR for String + length).
-- PostgreSQL CHAR(1) is reported as `bpchar` which trips schema-validate.

ALTER TABLE USER_INFM            ALTER COLUMN ACTV_YN TYPE VARCHAR(1);
ALTER TABLE USER_INFM            ALTER COLUMN LOCK_YN TYPE VARCHAR(1);
ALTER TABLE USER_INFM            ALTER COLUMN DEL_YN  TYPE VARCHAR(1);
ALTER TABLE USER_BUSI_CONN_INFM  ALTER COLUMN DEL_YN  TYPE VARCHAR(1);
