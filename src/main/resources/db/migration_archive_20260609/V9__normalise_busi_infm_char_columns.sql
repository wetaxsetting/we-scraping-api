-- =======================================================================
-- V9__normalise_busi_infm_char_columns.sql
--
-- V7 declared WIZ_RUN_YN and ERP_CD as CHAR(1) (matching legacy exactly).
-- Hibernate schema-validate maps a String @Column(length=1) to VARCHAR(1)
-- and treats bpchar/CHAR as a type mismatch. Widening to VARCHAR(1) is
-- functionally equivalent for single-character storage and lets the entity
-- mapping pass validation — same fix V3 applied for the YN columns.
-- =======================================================================

ALTER TABLE BUSI_INFM ALTER COLUMN WIZ_RUN_YN TYPE VARCHAR(1);
ALTER TABLE BUSI_INFM ALTER COLUMN ERP_CD     TYPE VARCHAR(1);
