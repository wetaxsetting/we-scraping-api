-- =============================================================================
-- V158 — MENU_AUTH_ADMN_SYS : refactor route/slug rows (ADDITIVE seed)
-- =============================================================================
--
-- MANUAL DBA / REFERENCE SCRIPT. Flyway is disabled/removed in the refactor —
-- this file is a manual SQL seed, not an auto-applied migration. The V158
-- number is for ordering/organisation only.
--
-- WHAT THIS DOES
--   * Inserts NEW refactor route/slug rows into MENU_AUTH_ADMN_SYS, mirroring
--     the legacy F / M / H product membership, mnu_nm, cont, and disp_ord.
--   * Only the WEB_SVC_ID is replaced (legacy id -> refactor slug). Every other
--     column value is copied verbatim from the legacy row.
--   * Idempotent: ON CONFLICT DO NOTHING (PK = web_svc_id, prdt_type).
--
-- WHAT THIS DOES NOT DO (by design)
--   * Does NOT update existing legacy WEB_SVC_ID values — legacy rows are
--     PRESERVED untouched (no UPDATE, no DELETE).
--   * Does NOT change the refactor runtime. The refactor still resolves menus
--     and permissions exclusively from MENU_ITEM / PRODUCT_MENU /
--     MENU_ITEM_TRANSLATION (navigation tree) + USER_MENU_AUTH_INFM (per-user
--     grants). MENU_AUTH_ADMN_SYS is NOT read by any refactor entity, repo,
--     service, controller, or security path — these rows are a reference seed.
--   * Does NOT add entities/repositories/services/controllers.
--
-- SCOPE / SAFETY
--   * On the shared/live JEX DB this table is owned by the live JEX system
--     (szon_0014_001 admin screen, filtered by PRDT_TYPE). These additive
--     slug rows carry NEW web_svc_id values, so they never collide with the
--     legacy rows on the PK (web_svc_id, prdt_type). Re-runs are no-ops.
--
-- SKIPPED legacy id
--   * comm_0180_001 ("Settings") — intentionally NOT seeded. It is the Settings
--     modal container (no Next.js route, no slug). Present in F/M/H legacy rows;
--     all three are skipped.
--
-- exch_0001_001 (Exchange Rate Inquiry) — RESOLVED to a route slug
--   The full URL once proposed (78 chars) does NOT fit
--   MENU_AUTH_ADMN_SYS.WEB_SVC_ID (VARCHAR(50), verified on the live JEX DB).
--   The refactor menu identifier for this page is the navigation permission
--   code already defined in wecms-web/lib/menuAuth.ts:61 for route
--   /payment/overseas-payment/exchange-rate-inquiry -> "payment-overseas-exchange"
--   (25 chars, fits). That is the same identifier surface MENU_AUTH_ADMN_SYS
--   represents (menu catalogue / permission code), so the exch rows use it and
--   are inserted normally below.
--   NOTE: overseas menu permission codes are the SHORT form
--   payment-overseas-{request,result,exchange}; the View Setting screenIds are
--   the LONGER payment-overseas-payment-{scheduled,result}. Exchange Rate has
--   no View Setting screenId (it renders no popover) — the permission code is
--   its only canonical identifier.
--
-- COUNTS
--   Executable slug rows inserted .......... 70   (F=25, M=25, H=20)
--   Skipped legacy id (comm_0180_001) ...... 3    (F=1, M=1, H=1)
--   Distinct refactor WEB_SVC_ID ........... 26
-- =============================================================================

-- ---- Product F (WeCMS Default) — 24 rows --------------------------------------
INSERT INTO menu_auth_admn_sys (web_svc_id, mnu_nm, cont, disp_ord, prdt_type) VALUES
    ('banking-account-inquiry-vnd',          'Account Inquiry',             '',  1, 'F'),
    ('banking-transaction-history-vnd',      'Transaction History',         '',  2, 'F'),
    ('banking-account-inquiry-fcy',          'Account Inquiry',             '',  3, 'F'),
    ('banking-transaction-history-fcy',      'Transaction History',         '',  4, 'F'),
    ('own-account-transfer',                 'Transfer Own Accounts',       '',  5, 'F'),
    ('transfer-history',                     'Transfer History',            '',  6, 'F'),
    ('banking-reports-daily-cash',           'Daily Cash',                  '',  7, 'F'),
    ('banking-reports-daily-financial',      'Daily Financial',             '',  8, 'F'),
    ('cash-flow-report',                     'Cash Flow',                   '',  9, 'F'),
    ('approval-box',                         'Approval Box',                '', 10, 'F'),
    ('payment-internal-transfer-scheduled',  'Internal Scheduled Transfer', '', 11, 'F'),
    ('payment-internal-transfer-result',     'Internal Transfer Result',    '', 12, 'F'),
    ('payment-vendor-payment-scheduled',     'Vendor Scheduled Payment',    '', 13, 'F'),
    ('payment-vendor-payment-result',        'Vendor Payment Result',       '', 14, 'F'),
    ('payment-expense-payment-scheduled',    'Expenses Scheduled Payment',  '', 15, 'F'),
    ('payment-expense-payment-result',       'Expenses Payment Result',     '', 16, 'F'),
    ('payment-payroll-payment-scheduled',    'Payroll Scheduled Payment',   '', 17, 'F'),
    ('payment-payroll-payment-result',       'Payroll Payment Result',      '', 18, 'F'),
    ('payment-overseas-payment-scheduled',   'Overseas Payment Request',    '', 19, 'F'),
    ('payment-overseas-payment-result',      'Overseas Request Result',     '', 20, 'F'),
    ('card-transactions',                    'Card Transactions',           '', 22, 'F'),
    ('addon-sales-einvoices',                'Sales e-Invoices',            '', 23, 'F'),
    ('addon-expenses-einvoices',             'Expense e-Invoices',          '', 24, 'F'),
    ('addon-operation-status',               'Operation Status Inquiry',    '', 25, 'F')
ON CONFLICT DO NOTHING;

-- ---- Product M (WeCMS Lumens) — 24 rows ---------------------------------------
INSERT INTO menu_auth_admn_sys (web_svc_id, mnu_nm, cont, disp_ord, prdt_type) VALUES
    ('banking-account-inquiry-vnd',          'Account Inquiry',             '',  1, 'M'),
    ('banking-transaction-history-vnd',      'Transaction History',         '',  2, 'M'),
    ('banking-account-inquiry-fcy',          'Account Inquiry',             '',  3, 'M'),
    ('banking-transaction-history-fcy',      'Transaction History',         '',  4, 'M'),
    ('own-account-transfer',                 'Transfer Own Accounts',       '',  5, 'M'),
    ('transfer-history',                     'Transfer History',            '',  6, 'M'),
    ('banking-reports-daily-cash',           'Daily Cash',                  '',  7, 'M'),
    ('banking-reports-daily-financial',      'Daily Financial',             '',  8, 'M'),
    ('cash-flow-report',                     'Cash Flow',                   '',  9, 'M'),
    ('approval-box',                         'Approval Box',                '', 10, 'M'),
    ('payment-internal-transfer-scheduled',  'Internal Scheduled Transfer', '', 11, 'M'),
    ('payment-internal-transfer-result',     'Internal Transfer Result',    '', 12, 'M'),
    ('payment-vendor-payment-scheduled',     'Vendor Scheduled Payment',    '', 13, 'M'),
    ('payment-vendor-payment-result',        'Vendor Payment Result',       '', 14, 'M'),
    ('payment-expense-payment-scheduled',    'Expenses Scheduled Payment',  '', 15, 'M'),
    ('payment-expense-payment-result',       'Expenses Payment Result',     '', 16, 'M'),
    ('payment-payroll-payment-scheduled',    'Payroll Scheduled Payment',   '', 17, 'M'),
    ('payment-payroll-payment-result',       'Payroll Payment Result',      '', 18, 'M'),
    ('payment-overseas-payment-scheduled',   'Overseas Payment Request',    '', 19, 'M'),
    ('payment-overseas-payment-result',      'Overseas Request Result',     '', 20, 'M'),
    ('card-transactions',                    'Card Transactions',           '', 22, 'M'),
    ('addon-sales-einvoices',                'Sales e-Invoices',            '', 23, 'M'),
    ('addon-expenses-einvoices',             'Expense e-Invoices',          '', 24, 'M'),
    ('addon-operation-status',               'Operation Status Inquiry',    '', 25, 'M')
ON CONFLICT DO NOTHING;

-- ---- Product H (HAENGSUNG Electronics) — 19 rows ------------------------------
-- H omits Daily Cash / Daily Financial(CMC) / Cash Flow / Sales+Expense
-- e-Invoices / Operation Status, and uses the HS Daily Financial variant
-- (rept_0017_001 -> banking-reports-daily-financial-hs).
INSERT INTO menu_auth_admn_sys (web_svc_id, mnu_nm, cont, disp_ord, prdt_type) VALUES
    ('banking-account-inquiry-vnd',          'Account Inquiry',             '',  1, 'H'),
    ('banking-transaction-history-vnd',      'Transaction History',         '',  2, 'H'),
    ('banking-account-inquiry-fcy',          'Account Inquiry',             '',  3, 'H'),
    ('banking-transaction-history-fcy',      'Transaction History',         '',  4, 'H'),
    ('own-account-transfer',                 'Transfer Own Accounts',       '',  5, 'H'),
    ('transfer-history',                     'Transfer History',            '',  6, 'H'),
    ('banking-reports-daily-financial-hs',   'Daily Financial',             '',  7, 'H'),
    ('approval-box',                         'Approval Box',                '',  8, 'H'),
    ('payment-internal-transfer-scheduled',  'Internal Scheduled Transfer', '',  9, 'H'),
    ('payment-internal-transfer-result',     'Internal Transfer Result',    '', 10, 'H'),
    ('payment-vendor-payment-scheduled',     'Vendor Scheduled Payment',    '', 11, 'H'),
    ('payment-vendor-payment-result',        'Vendor Payment Result',       '', 12, 'H'),
    ('payment-expense-payment-scheduled',    'Expenses Scheduled Payment',  '', 13, 'H'),
    ('payment-expense-payment-result',       'Expenses Payment Result',     '', 14, 'H'),
    ('payment-payroll-payment-scheduled',    'Payroll Scheduled Payment',   '', 15, 'H'),
    ('payment-payroll-payment-result',       'Payroll Payment Result',      '', 16, 'H'),
    ('payment-overseas-payment-scheduled',   'Overseas Payment Request',    '', 17, 'H'),
    ('payment-overseas-payment-result',      'Overseas Request Result',     '', 18, 'H'),
    ('card-transactions',                    'Card Transactions',           '', 20, 'H')
ON CONFLICT DO NOTHING;

-- ---- Exchange Rate Inquiry (exch_0001_001) — slug payment-overseas-exchange ---
-- Verified canonical menu permission code (wecms-web/lib/menuAuth.ts:61) for
-- route /payment/overseas-payment/exchange-rate-inquiry. The 78-char full URL
-- once proposed does not fit WEB_SVC_ID VARCHAR(50); this route slug (25 chars)
-- does. F/M disp_ord 21, H disp_ord 19 — mirrors legacy membership.
INSERT INTO menu_auth_admn_sys (web_svc_id, mnu_nm, cont, disp_ord, prdt_type) VALUES
    ('payment-overseas-exchange', 'Exchange Rate Inquiry', '', 21, 'F'),
    ('payment-overseas-exchange', 'Exchange Rate Inquiry', '', 21, 'M'),
    ('payment-overseas-exchange', 'Exchange Rate Inquiry', '', 19, 'H')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- VERIFICATION SQL (read-only — run manually after applying)
-- -----------------------------------------------------------------------------
-- 1) Inserted slug rows per product (expect F=25, M=25, H=20; 70 total):
--    SELECT prdt_type, COUNT(*) AS slug_rows
--    FROM menu_auth_admn_sys
--    WHERE web_svc_id LIKE '%-%'          -- slugs contain hyphens; legacy ids do not
--    GROUP BY prdt_type ORDER BY prdt_type;
--
-- 2) Distinct refactor slug ids (expect 26):
--    SELECT COUNT(DISTINCT web_svc_id) AS distinct_slugs
--    FROM menu_auth_admn_sys
--    WHERE web_svc_id LIKE '%-%';
--
-- 3) Legacy rows preserved & untouched (expect the original legacy count,
--    including comm_0180_001 for F/M/H):
--    SELECT COUNT(*) AS legacy_rows
--    FROM menu_auth_admn_sys
--    WHERE web_svc_id LIKE '%\_%' ESCAPE '\';   -- legacy ids contain underscores
--
-- 4) Confirm comm_0180_001 was NOT given a slug row (expect 0):
--    SELECT COUNT(*) FROM menu_auth_admn_sys WHERE web_svc_id = 'settings';
--
-- 5) Spot-check one slug across products:
--    SELECT web_svc_id, prdt_type, disp_ord, mnu_nm
--    FROM menu_auth_admn_sys
--    WHERE web_svc_id = 'banking-reports-daily-financial-hs';  -- expect H only, disp_ord 7
-- =============================================================================
-- END V158
-- =============================================================================
