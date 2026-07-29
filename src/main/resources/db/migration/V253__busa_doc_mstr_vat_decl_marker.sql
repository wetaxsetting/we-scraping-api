------------------------------------------------------------------
-- Manual DBA Script
-- Flyway disabled/removed.
-- Execute manually in DEV -> UAT -> PROD.
------------------------------------------------------------------
-- File:    V253__busa_doc_mstr_vat_decl_marker.sql
-- Table:   busa_doc_mstr  (invoice header — BOTH directions:
--                          DOC_TYPE 'I' = output/sales, 'B' = input/purchase)
-- Feature: Add-on > Tax Report > VAT Declaration (Mau 01/GTGT TT80)
--
-- WHY: the annex listing is derived from invoices by ISSU_DT alone, so nothing
--      records WHICH declaration an invoice ended up in. Consequences seen in
--      the live data: 219 of 284 invoices carry a MOD_DTM in a different month
--      than ISSU_DT, i.e. late entry / amendment after the period is closed —
--      the frozen VAT_DECL_ITEMS figures and the live annex list then drift.
--
-- PO DECISION (2026-07-24, relayed by the developer): mark the invoice row
--      itself rather than introduce a link table, explicitly to avoid growing
--      the data model. Two columns only.
--
--        VAT_DECL_PROC_YN  'Y' once the invoice has been closed into a locked
--                          declaration, 'N' otherwise (default).
--        VAT_DECL_SID      which declaration closed it (VAT_DECL.SID).
--
--      Written by VatDeclarationService.lock(), cleared by unlock().
--
-- !! DOCUMENTED DEVIATION from the Legacy Schema Parity Rule (CLAUDE.md
--    "Legacy Schema Parity Rule": a migrated legacy table must mirror JEX DEV
--    column-for-column). BUSA_DOC_MSTR is a legacy JEX table and DEV/PROD do
--    NOT have these two columns, so after this script the local table is 102
--    columns vs DEV's 100. Recorded here so a future parity audit reads this as
--    an intentional PO-approved addition, not drift.
--    Coexistence is safe: both columns are additive and nullable / defaulted,
--    so the live JEX system keeps inserting and updating the table unchanged.
--    No FK to VAT_DECL is declared — VAT_DECL is refactor-owned and a hard FK
--    would make the legacy table depend on it.
--
-- Additive + idempotent.
------------------------------------------------------------------

ALTER TABLE busa_doc_mstr
    ADD COLUMN IF NOT EXISTS vat_decl_proc_yn varchar(1) NOT NULL DEFAULT 'N';

ALTER TABLE busa_doc_mstr
    ADD COLUMN IF NOT EXISTS vat_decl_sid bigint NULL;

COMMENT ON COLUMN busa_doc_mstr.vat_decl_proc_yn
    IS 'Y = invoice already closed into a locked VAT declaration; N = not yet (default)';
COMMENT ON COLUMN busa_doc_mstr.vat_decl_sid
    IS 'VAT_DECL.SID that closed this invoice; NULL while unprocessed (FK-by-value, no constraint)';

-- Finding the still-unprocessed invoices of a period is the whole point of the
-- flag, so index the tenant + flag + issue date it is queried by.
CREATE INDEX IF NOT EXISTS ix_busa_doc_mstr_vat_decl_proc
    ON busa_doc_mstr (biz_key, vat_decl_proc_yn, issu_dt);

-- Reverse lookup: every invoice a given declaration closed.
CREATE INDEX IF NOT EXISTS ix_busa_doc_mstr_vat_decl_sid
    ON busa_doc_mstr (vat_decl_sid) WHERE vat_decl_sid IS NOT NULL;

-- Rollback:
--   DROP INDEX IF EXISTS ix_busa_doc_mstr_vat_decl_sid;
--   DROP INDEX IF EXISTS ix_busa_doc_mstr_vat_decl_proc;
--   ALTER TABLE busa_doc_mstr DROP COLUMN IF EXISTS vat_decl_sid;
--   ALTER TABLE busa_doc_mstr DROP COLUMN IF EXISTS vat_decl_proc_yn;
