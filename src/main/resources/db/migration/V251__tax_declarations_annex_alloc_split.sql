------------------------------------------------------------------
-- Manual DBA Script
-- Flyway disabled/removed.
-- Execute manually in DEV -> UAT -> PROD.
------------------------------------------------------------------
-- File:    V251__tax_declarations_annex_alloc_split.sql
-- Table:   tax_declarations  (VAT declaration header)
-- Feature: Add-on > Tax Report > VAT Declaration (Mau 01/GTGT TT80)
--
-- WHY: the PO prototype's create dialog renders FIVE annex checkboxes
--      (docs/vat-declaration/VAT_Declaration_Report.html, `.annexChk`):
--        BKBR_01-1/GTGT
--        BKMV_01-2/GTGT
--        PL 01-6/GTGT (CSSX)                <- allocation, production facility
--        PL 01-6/GTGT (VT cuoc tra sau)     <- allocation, post-paid telecom
--        PL 142/2024-174/2024-204/2025/QH15 (8% reduction)
--      The refactor collapsed the TWO 01-6/GTGT variants into a single
--      ANNEX_ALLOC_YN, so the two annexes could not be told apart even though
--      they are different forms.
--
-- DECISION: keep the existing ANNEX_ALLOC_YN as the CSSX variant (no rename —
--      avoids breaking anything already reading it) and add the telecom one.
--      Matching annex-row discriminator: AnnexType ALLOC -> ALLOC_CSSX +
--      ALLOC_TELE (tax_declaration_annex_items.ANNEX_TYPE is varchar(10), both
--      codes fit; the table holds 0 rows today so no back-fill is needed).
--
-- Additive + idempotent.
------------------------------------------------------------------

ALTER TABLE tax_declarations
    ADD COLUMN IF NOT EXISTS annex_alloc_tele_yn varchar(1) NOT NULL DEFAULT 'N';

COMMENT ON COLUMN tax_declarations.annex_alloc_yn
    IS 'PL 01-6/GTGT (CSSX) - allocation annex, production facility';
COMMENT ON COLUMN tax_declarations.annex_alloc_tele_yn
    IS 'PL 01-6/GTGT (VT cuoc tra sau) - allocation annex, post-paid telecom';

-- Rollback:
--   ALTER TABLE tax_declarations DROP COLUMN IF EXISTS annex_alloc_tele_yn;
