-- ============================================================================
-- V89__contacts_disp_ord_to_integer.sql
--
-- Hotfix for the Contacts schema (V84): Hibernate 6.6.x's schema-validate
-- rejects NUMERIC(10,0) against the JPA `Integer dispOrd` mapping with
--
--   Schema-validation: wrong column type encountered in column [disp_ord]
--   in table [trco_acct_info]; found [numeric (Types#NUMERIC)], but
--   expecting [integer (Types#INTEGER)]
--
-- Same root cause + same fix as the V35 → V36 hotfix for
-- `SysScrOptionWecms.dispOrd`. The convention established by V36 is:
-- keep the entity as `Integer dispOrd` and ALTER the LOCAL column to
-- INTEGER, accepting a permanent refactor-only deviation from DEV's
-- NUMERIC(10,0). Documented in entity Javadoc + this migration header.
--
-- Data safety:
--   * `disp_ord` on both tables only carries small integers (1 or 2
--     in the legacy data; the V87 seed uses 1 and 2 verbatim). NUMERIC
--     (10,0) → INTEGER is a safe lossless cast for the existing
--     domain.
--   * USING clause makes the conversion explicit so PostgreSQL does
--     not error on the cast.
--
-- Idempotent: re-applying the conversion on an already-INTEGER column
-- is a no-op for PostgreSQL when the source type already matches the
-- target.
--
-- Refactor-only deviation from DEV (DEV stays NUMERIC(10,0)) —
-- documented inline below + on the entities. Future sprint that ports
-- the per-table history (TRCO_MDPH) may need to align with this
-- choice when it mirrors the columns.
-- ============================================================================

ALTER TABLE TRCO_ADDR_INFM
    ALTER COLUMN DISP_ORD TYPE INTEGER USING DISP_ORD::integer;

ALTER TABLE TRCO_ACCT_INFO
    ALTER COLUMN DISP_ORD TYPE INTEGER USING DISP_ORD::integer;
