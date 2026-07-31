------------------------------------------------------------------
-- Manual DBA Script
-- Flyway disabled/removed.
-- Execute manually in DEV -> UAT -> PROD.
------------------------------------------------------------------
-- File:    V252__vat_decl_rename_and_drop_annex.sql
-- Feature: Add-on > Tax Report > VAT Declaration (Mau 01/GTGT TT80)
--
-- 1) RENAME to the logical names the design sheet always used (the entity
--    Javadoc already said "logical VAT_DECL / VAT_DECL_ITEMS"):
--       tax_declarations       -> vat_decl
--       tax_declaration_items  -> vat_decl_items
--    The FK column on the child is already VAT_DECL_ID, so it now reads
--    consistently with its parent.
--
-- 2) DROP tax_declaration_annex_items.
--    The annex listing is NOT persisted any more — it is DERIVED per request
--    from the period's real invoices (BUSA_DOC_MSTR header + BUSA_DOC_DETL
--    product lines), filtered by the declaration's FROM_DT..TO_DT and by
--    direction (DOC_TYPE 'I' = output/sales, 'B' = input/purchase).
--    Consequence: manual annex rows can no longer be stored, so the
--    add-row / delete-row endpoints are removed with this change.
--    The table holds 0 rows, so nothing is lost.
--
-- Renames are NOT idempotent in plain SQL, so each is guarded by a DO block
-- that checks the current catalogue state first — safe to re-run.
------------------------------------------------------------------

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'tax_declarations')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'vat_decl') THEN
        ALTER TABLE tax_declarations RENAME TO vat_decl;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'tax_declaration_items')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'vat_decl_items') THEN
        ALTER TABLE tax_declaration_items RENAME TO vat_decl_items;
    END IF;
END $$;

DROP TABLE IF EXISTS tax_declaration_annex_items;

-- Rollback:
--   ALTER TABLE vat_decl       RENAME TO tax_declarations;
--   ALTER TABLE vat_decl_items RENAME TO tax_declaration_items;
--   (the annex table is not restored — it carried no rows)
