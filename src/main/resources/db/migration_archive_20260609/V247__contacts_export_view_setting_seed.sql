-- ============================================================================
-- V247__contacts_export_view_setting_seed.sql
--
-- View Setting (Settings > Contacts) EXPORT screen seeds. Registers the
-- two export screenIds in SYS_SCR_OPTION_WECMS so the shared Excel export
-- pipeline (ExcelExportService -> ScreenOptionService.load) intersects the
-- request columns against a real persisted visibility set. Without the
-- seed every column would be dropped with WBE2013.
--
-- Mirrors the cash-flow-report / firm-banking-history precedent: an
-- export-only screenId with no on-screen List-icon popover (the Contacts
-- table is a fixed 8-column layout; column toggling is not exposed). Every
-- column is USE_YN='Y', FIX_YN='Y' (no popover), SORT_YN='N' (export has
-- no user sort).
--
-- Two screenIds mirror the Account Inquiry VND/FCY split — one provider +
-- one exact column set per type:
--   settings-contacts-local    (9 cols)  -> ContactsLocalExportProvider
--   settings-contacts-overseas (11 cols) -> ContactsOverseasExportProvider
--
-- Columns + order follow legacy comm_0184_001_r006_excel_view.jsp:
--   Local:    Contact Code, Contact Name, Tax ID, Deposit Bank,
--             Deposit Account No., Currency, Current Address, Old Address,
--             Valid Date
--   Overseas: Contact Code, Contact Name, Tax ID, Beneficiary Name,
--             Beneficiary Bank Name, BIC, Beneficiary Account No.,
--             Beneficiary Address, Current Address, Old Address, Valid Date
--
-- FIELD_IDs are semantic refactor names (CONTACT_CODE, DEPOSIT_BANK, ...)
-- chosen to resolve to themselves through FieldIdAlias (none collide with
-- an alias key such as BANK_NM->BANK or TRCO_CMNM->BUYER). The provider
-- allow-list, the frontend column lists in lib/api/contacts.ts, and these
-- seed rows all speak the same key space.
--
-- Idempotent: ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING.
-- ============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    -- ── Local (settings-contacts-local) — 9 columns ────────────────────
    ('settings-contacts-local', 'default', 'CONTACT_CODE',       'Contact Code',          1, 'Y', 'Y', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-local', 'default', 'CONTACT_NAME',       'Contact Name',          2, 'Y', 'Y', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-local', 'default', 'TAX_ID',             'Tax ID',                3, 'Y', 'Y', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-local', 'default', 'DEPOSIT_BANK',       'Deposit Bank',          4, 'Y', 'Y', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-local', 'default', 'DEPOSIT_ACCOUNT_NO', 'Deposit Account No.',   5, 'Y', 'Y', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-local', 'default', 'CURRENCY',           'Currency',              6, 'Y', 'Y', 'Y', 'N', '100px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-local', 'default', 'CURRENT_ADDRESS',    'Current Address',       7, 'Y', 'Y', 'Y', 'N', '280px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-local', 'default', 'OLD_ADDRESS',        'Old Address',           8, 'Y', 'Y', 'Y', 'N', '280px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-local', 'default', 'VALID_DATE',         'Valid Date',            9, 'Y', 'Y', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),

    -- ── Overseas (settings-contacts-overseas) — 11 columns ─────────────
    ('settings-contacts-overseas', 'default', 'CONTACT_CODE',           'Contact Code',              1, 'Y', 'Y', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'CONTACT_NAME',           'Contact Name',              2, 'Y', 'Y', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'TAX_ID',                 'Tax ID',                    3, 'Y', 'Y', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'BENEFICIARY_NAME',       'Beneficiary Name',          4, 'Y', 'Y', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'BENEFICIARY_BANK_NAME',  'Beneficiary Bank Name',     5, 'Y', 'Y', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'BIC_CODE',               'Bank Identifier Code (BIC)',6, 'Y', 'Y', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'BENEFICIARY_ACCOUNT_NO', 'Beneficiary Account No.',   7, 'Y', 'Y', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'BENEFICIARY_ADDRESS',    'Beneficiary Address',       8, 'Y', 'Y', 'Y', 'N', '280px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'CURRENT_ADDRESS',        'Current Address',           9, 'Y', 'Y', 'Y', 'N', '280px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'OLD_ADDRESS',            'Old Address',              10, 'Y', 'Y', 'Y', 'N', '280px', 'system', NOW(), 'system', NOW()),
    ('settings-contacts-overseas', 'default', 'VALID_DATE',             'Valid Date',               11, 'Y', 'Y', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
