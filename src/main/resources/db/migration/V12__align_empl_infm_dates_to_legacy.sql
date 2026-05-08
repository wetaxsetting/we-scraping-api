-- ============================================================================
-- V12__align_empl_infm_dates_to_legacy.sql
--
-- The local laptop DB was seeded from a legacy import script that converted
-- EMPL_INFM.CMPN_JOIN_DATE and CMPN_FIRE_DATE to PostgreSQL DATE columns.
-- The authoritative JEX legacy schema (verified against wecmsvndevdb on
-- 2026-04-29) declares them as VARCHAR(8) — and so do V11 and the
-- EmplInfm JPA entity. Realign the local columns so Hibernate schema
-- validation succeeds against the legacy-imported tenant.
--
-- Safe on existing rows: to_char(NULL, …) returns NULL, and any populated
-- date is rendered as YYYYMMDD which is exactly the legacy storage format.
-- No data is dropped.
-- ============================================================================

ALTER TABLE EMPL_INFM
    ALTER COLUMN CMPN_JOIN_DATE TYPE VARCHAR(8) USING to_char(CMPN_JOIN_DATE, 'YYYYMMDD'),
    ALTER COLUMN CMPN_FIRE_DATE TYPE VARCHAR(8) USING to_char(CMPN_FIRE_DATE, 'YYYYMMDD');
