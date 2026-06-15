-- ============================================================================
-- V45__view_setting_seed_addon_einvoices.sql
--
-- Seed View Setting columns for Add-on Service > Sales e-Invoices and
-- the Bill / Expenses sibling. Pairs with Backend MR C — the Excel
-- export endpoint (`POST /api/v1/addon/einvoices/export`) intersects
-- the requested columns against this seed before writing the workbook,
-- so without these rows ExcelExportService would emit `WBE2013`
-- ("no exportable columns") on every request.
--
-- Per IR-9 in `docs/refactor-wecms/planning/addon/sales-e-invoices-plan.md`:
--   - View Setting *data* (this seed + the column-metadata fed into
--     the table colgroup + the Excel allow-list) is in scope.
--   - View Setting *management UI* (popover, list-view toolbar icon,
--     Save / Reset controls) is OUT of scope for this page. The
--     Sales / Bill toolbars stay Print + Excel + Update only per
--     Figma 9194:6859. Users cannot toggle column visibility from
--     this page; the seed defines the column set and is treated as
--     fixed for end users. Backend Save / Reset endpoints stay
--     globally available (Account Inquiry uses them) — they simply
--     receive no traffic from this page.
--
-- Column set mirrors the static UI's 8-column on-screen table
-- (Figma 9194:6696) plus the special CHECKBOX row at DISP_ORD=0.
-- The `EInvoiceExportProvider.allowedFieldIds()` allow-list is the
-- 8 visible columns (CHECKBOX is dropped by the Excel writer via
-- `ScreenOptionService.SPECIAL_FIELD_IDS`):
--
--   CHECKBOX (special), DATE_TIME, INVOICE_NO, BUYER, CURRENCY,
--   TOTAL, STATUS, TAX, PRETAX
--
-- No ETC row — Sales / Bill have no per-row action column.
--
-- SIZE values are PLACEHOLDERS sized roughly from the on-screen
-- `--w-wb-si-*` tokens. They will be replaced with the canonical
-- Figma WB365 widths in a follow-up migration when the designer
-- publishes them (deferred per the planning doc's "Deferred / PO
-- decision" table; mirrors the V34 pattern from Account Inquiry).
--
-- BUYER column relabel for the Bill variant: SUPPLIER (the Expenses
-- sibling page swaps the column header). Frontend i18n handles the
-- on-screen label; the SYS_SCR_OPTION_WECMS row stores the canonical
-- internal name.
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING` —
-- matches the table PK introduced in V30.
-- ============================================================================

-- Sales e-Invoices ----------------------------------------------------
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD,
     USE_YN, FIX_YN, DISP_YN, size, SORT_YN)
VALUES
    ('addon-sales-einvoices', 'default', 'CHECKBOX',   'Checkbox',     0, 'Y', 'Y', 'N', '38px',  'N'),
    ('addon-sales-einvoices', 'default', 'DATE_TIME',  'Date / Time',  1, 'Y', 'N', 'Y', '120px', 'Y'),
    ('addon-sales-einvoices', 'default', 'INVOICE_NO', 'Invoice No.',  2, 'Y', 'N', 'Y', '160px', 'Y'),
    ('addon-sales-einvoices', 'default', 'BUYER',      'Buyer',        3, 'Y', 'N', 'Y', '320px', 'Y'),
    ('addon-sales-einvoices', 'default', 'CURRENCY',   'Currency',     4, 'Y', 'N', 'Y', '80px',  'Y'),
    ('addon-sales-einvoices', 'default', 'TOTAL',      'Total',        5, 'Y', 'N', 'Y', '140px', 'Y'),
    ('addon-sales-einvoices', 'default', 'STATUS',     'Status',       6, 'Y', 'N', 'Y', '120px', 'Y'),
    ('addon-sales-einvoices', 'default', 'TAX',        'Tax',          7, 'Y', 'N', 'Y', '140px', 'Y'),
    ('addon-sales-einvoices', 'default', 'PRETAX',     'Pre-tax',      8, 'Y', 'N', 'Y', '140px', 'Y')
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- Bill / Expenses e-Invoices -----------------------------------------
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD,
     USE_YN, FIX_YN, DISP_YN, size, SORT_YN)
VALUES
    ('addon-expenses-einvoices', 'default', 'CHECKBOX',   'Checkbox',     0, 'Y', 'Y', 'N', '38px',  'N'),
    ('addon-expenses-einvoices', 'default', 'DATE_TIME',  'Date / Time',  1, 'Y', 'N', 'Y', '120px', 'Y'),
    ('addon-expenses-einvoices', 'default', 'INVOICE_NO', 'Invoice No.',  2, 'Y', 'N', 'Y', '160px', 'Y'),
    ('addon-expenses-einvoices', 'default', 'BUYER',      'Supplier',     3, 'Y', 'N', 'Y', '320px', 'Y'),
    ('addon-expenses-einvoices', 'default', 'CURRENCY',   'Currency',     4, 'Y', 'N', 'Y', '80px',  'Y'),
    ('addon-expenses-einvoices', 'default', 'TOTAL',      'Total',        5, 'Y', 'N', 'Y', '140px', 'Y'),
    ('addon-expenses-einvoices', 'default', 'STATUS',     'Status',       6, 'Y', 'N', 'Y', '120px', 'Y'),
    ('addon-expenses-einvoices', 'default', 'TAX',        'Tax',          7, 'Y', 'N', 'Y', '140px', 'Y'),
    ('addon-expenses-einvoices', 'default', 'PRETAX',     'Pre-tax',      8, 'Y', 'N', 'Y', '140px', 'Y')
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
