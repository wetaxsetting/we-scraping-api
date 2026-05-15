-- V117: Fix TOT_TRNS_CNT column type in overseas-payment tables
-- V112 created both columns as NUMERIC(10) but the JPA entities map them
-- as Integer, which Hibernate validates against INTEGER (not NUMERIC).
-- Alter to INTEGER to match the entity mapping and the semantic intent
-- (a count of transactions is never fractional).
--
-- PostgreSQL silently succeeds if the column is already INTEGER (no-op),
-- so this migration is safe on any DB state.

ALTER TABLE OVRS_PYMN_SUMR
    ALTER COLUMN TOT_TRNS_CNT TYPE INTEGER USING TOT_TRNS_CNT::INTEGER;

ALTER TABLE OVRS_PYMN_MSTR
    ALTER COLUMN TOT_TRNS_CNT TYPE INTEGER USING TOT_TRNS_CNT::INTEGER;
