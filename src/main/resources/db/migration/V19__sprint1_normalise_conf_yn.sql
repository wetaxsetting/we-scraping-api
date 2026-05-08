-- ============================================================================
-- V19__sprint1_normalise_conf_yn.sql
--
-- Follow-up to V18: V18 normalised CHAR → VARCHAR for the Sprint 1 banking
-- tables but missed ACCT_TRNS_RCV_PAY_INFM.CONF_YN. Hibernate's schema
-- validator caught the mismatch on first boot post-V18.
--
-- Idempotent: re-typing an already-VARCHAR column is a no-op in PostgreSQL.
-- ============================================================================

ALTER TABLE ACCT_TRNS_RCV_PAY_INFM
    ALTER COLUMN CONF_YN TYPE VARCHAR(1);
