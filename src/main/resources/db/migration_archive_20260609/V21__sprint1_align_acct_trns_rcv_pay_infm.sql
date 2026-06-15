-- ============================================================================
-- V21__sprint1_align_acct_trns_rcv_pay_infm.sql
--
-- V13 declared ACCT_TRNS_RCV_PAY_INFM with names that didn't match the legacy
-- wecmsvndev schema (TRCO_KEY/USAG_CD/AMT/CONF_YN/CONF_USER_ID/CONF_DTM).
-- The audit confirmed the real legacy column names are RCV_PAY_TRCO_KEY,
-- RCV_PAY_TRCO_NM, RCV_PAY_USAG_CD, RCV_PAY_AMT, CNFM_YN, CNFM_BY_USER_ID,
-- CNFM_BY_USER_NM, CNFM_DTM. This migration renames the V13 columns and adds
-- the legacy locator columns (BANK_CD, ACCT_NO, TRSC_DT) so the table mirrors
-- the legacy shape — the JPA entity is updated to match.
--
-- Idempotent: every step uses IF EXISTS / IF NOT EXISTS so re-runs and the
-- "fresh DB" path (V13 → V21) both end in the same shape.
--
-- Note: V13's PK is (BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO). Legacy carries
-- locator columns on top of that PK; they are denormalised but match how
-- legacy pages key into the table when joining to ACCT_TRNS_HSTR.
-- ============================================================================

-- 1. Rename V13 columns to legacy names.
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM RENAME COLUMN TRCO_KEY     TO RCV_PAY_TRCO_KEY;
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM RENAME COLUMN TRCO_CMNM    TO RCV_PAY_TRCO_NM;
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM RENAME COLUMN USAG_CD      TO RCV_PAY_USAG_CD;
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM RENAME COLUMN AMT          TO RCV_PAY_AMT;
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM RENAME COLUMN CONF_YN      TO CNFM_YN;
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM RENAME COLUMN CONF_USER_ID TO CNFM_BY_USER_ID;
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM RENAME COLUMN CONF_DTM     TO CNFM_DTM;

-- 2. Add legacy locator + confirmer-name columns (idempotent).
ALTER TABLE ACCT_TRNS_RCV_PAY_INFM
    ADD COLUMN IF NOT EXISTS BANK_CD          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ACCT_NO          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS TRSC_DT          VARCHAR(8),
    ADD COLUMN IF NOT EXISTS CNFM_BY_USER_NM  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS RCV_PAY_TRCO_ENM VARCHAR(200);
