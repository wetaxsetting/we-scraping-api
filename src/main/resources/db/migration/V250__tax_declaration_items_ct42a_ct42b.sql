------------------------------------------------------------------
-- Manual DBA Script
-- Flyway disabled/removed.
-- Execute manually in DEV -> UAT -> PROD.
------------------------------------------------------------------
-- File:    V250__tax_declaration_items_ct42a_ct42b.sql
-- Table:   tax_declaration_items  (VAT declaration indicators)
-- Feature: Add-on > Tax Report > VAT Declaration (Mau 01/GTGT TT80)
--
-- WHY: the PO prototype (docs/vat-declaration/VAT_Declaration_Report.html)
--      renders TWO editable sub-cells under indicator [42] "Thue GTGT de nghi
--      hoan" — `_vin(d,'42a')` and `_vin(d,'42b')`, labelled f_rVI41a /
--      f_rVI41b = "(TK 1331)" / "(TK 1332)". Neither existed in the refactor
--      schema, so Save Draft silently dropped them.
--
-- SEMANTICS: pure breakdown of [42] by input-VAT account —
--   [42a] = VAT refund requested, account 1331 (goods & services)
--   [42b] = VAT refund requested, account 1332 (fixed assets)
-- They take NO part in any formula (the prototype's compute() keeps
-- [43] = [41] - [42]); they are persisted for the HTKK XML export only.
-- Deliberately NOT constrained to [42a]+[42b] = [42] — the prototype does not
-- enforce it either.
--
-- Additive + idempotent; nullable like every other indicator column.
------------------------------------------------------------------

ALTER TABLE tax_declaration_items
    ADD COLUMN IF NOT EXISTS vat_refund_req_1331 numeric(20,6) NULL;

ALTER TABLE tax_declaration_items
    ADD COLUMN IF NOT EXISTS vat_refund_req_1332 numeric(20,6) NULL;

COMMENT ON COLUMN tax_declaration_items.vat_refund_req_1331
    IS 'ct42a - VAT refund requested, input-VAT account 1331 (goods & services)';
COMMENT ON COLUMN tax_declaration_items.vat_refund_req_1332
    IS 'ct42b - VAT refund requested, input-VAT account 1332 (fixed assets)';

-- Rollback:
--   ALTER TABLE tax_declaration_items DROP COLUMN IF EXISTS vat_refund_req_1331;
--   ALTER TABLE tax_declaration_items DROP COLUMN IF EXISTS vat_refund_req_1332;
