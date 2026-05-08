-- ============================================================================
-- V49__view_setting_addon_sales_einvoices_prod_alignment.sql
--
-- Sales e-Invoices View Setting — realign local seed to PROD with the
-- same refactor-side normalization rules used for V46/V47 (Account
-- Inquiry VND/FCY).
--
-- Per Sonny: PROD is the source of truth, DEV is wrong; rewrite the
-- seed. Same transformation shape as V46/V47:
--
--   Rule 1 — FIELD_ID:    drop trailing `_4`     (ISSU_DT_4 → ISSU_DT,
--                                                  DOC_ID_4 → DOC_ID,
--                                                  CHECKBOX_4 → CHECKBOX, …)
--   Rule 2 — FIELD_NM:    drop trailing `_4`     (PROD doubles the id as
--                                                  the display name)
--   Rule 3 — DYM_AREA_ID: 'table4' → 'default'   (single-area page per
--                                                  docs §1.5)
--   Rule 4 — DISP_ORD:    CHECKBOX = 0; data columns sequential 1..N;
--                          ETC = 99 (no ETC seeded today — Sales
--                          e-Invoices has no action column yet)
--   Rule 5 — every other column kept verbatim    (DISP_YN, USE_YN,
--                                                  SIZE, SORT_YN, HTML_CONT)
--
-- Source rows (PROD `sys_scr_option_wecms` for legacy `einv_0001_000`):
--
--   field_id              field_nm              ord disp use size  area    sort html
--   CHECKBOX_4            CHECKBOX_4              0  N    Y    50px table4  Y    NULL
--   ISSU_DT_4             ISSU_DT_4               1  Y    Y   120px table4  Y    NULL
--   DOC_ID_4              DOC_ID_4                2  Y    Y   150px table4  Y    NULL
--   TRCO_CMNM_4           TRCO_CMNM_4             3  Y    Y   200px table4  Y    NULL
--   TRCO_TRSC_CCY_CD_4    TRCO_TRSC_CCY_CD_4      4  Y    Y    80px table4  Y    NULL
--   TOTL_AMT_4            TOTL_AMT_4              5  Y    Y   120px table4  Y    NULL
--   einv_invoiceStatus_4  einv_invoiceStatus_4    6  Y    Y   100px table4  Y    NULL
--   TAX_AMT_SUM_4         TAX_AMT_SUM_4           7  Y    Y   110px table4  Y    NULL
--   SPLY_AMT_SUM_4        SPLY_AMT_SUM_4          8  Y    Y   110px table4  Y    NULL
--
-- After transformation:
--
--   field_id              field_nm              ord disp use size  area    sort html
--   CHECKBOX              CHECKBOX                0  N    Y    50px default Y    NULL
--   ISSU_DT               ISSU_DT                 1  Y    Y   120px default Y    NULL
--   DOC_ID                DOC_ID                  2  Y    Y   150px default Y    NULL
--   TRCO_CMNM             TRCO_CMNM               3  Y    Y   200px default Y    NULL
--   TRCO_TRSC_CCY_CD      TRCO_TRSC_CCY_CD        4  Y    Y    80px default Y    NULL
--   TOTL_AMT              TOTL_AMT                5  Y    Y   120px default Y    NULL
--   einv_invoiceStatus    einv_invoiceStatus      6  Y    Y   100px default Y    NULL
--   TAX_AMT_SUM           TAX_AMT_SUM             7  Y    Y   110px default Y    NULL
--   SPLY_AMT_SUM          SPLY_AMT_SUM            8  Y    Y   110px default Y    NULL
--
-- WEB_SVC_ID is rewritten from the legacy JEX id (`einv_0001_000`) to
-- the semantic refactor id (`addon-sales-einvoices`, per
-- docs/jex/features/view-setting.md §1.1, the regex enforced in
-- ScreenOptionController, and the V45 seed already in place under
-- this id). Same call as V46/V47 — keeping `einv_0001_000` would
-- orphan the rows because the controller's @Pattern regex rejects
-- underscored ids.
--
-- FIX_YN is a refactor-only column (DEV/PROD don't have it). Only
-- CHECKBOX (special row, DISP_ORD=0) gets FIX_YN='Y' to mirror the
-- V32/V33 intent. No specific "primary identifier" column for
-- Sales e-Invoices — every data column stays at the column default
-- 'N' so users can reorder them freely.
--
-- Note on `einv_invoiceStatus`: the post-transform FIELD_ID keeps the
-- lowercase + camelCase shape verbatim from PROD ("strip `_4` only,
-- nothing else"). It is the one outlier in an otherwise UPPER_SNAKE
-- set. If frontend / Java registries expect a different casing, that
-- belongs in a follow-up MR — this migration mirrors PROD exactly per
-- Rule 1.
--
-- Application source code is intentionally NOT touched. Risks called
-- out at the bottom.
--
-- Three things happen, in order:
--   1. Wipe existing sys + user rows scoped strictly to
--      `web_svc_id = 'addon-sales-einvoices'`. Other screens
--      (including the Expenses twin under `addon-expenses-einvoices`,
--      Account Inquiry under V46/V47) are untouched.
--   2. Reseed sys with the 9 transformed rows.
--   3. (No user-row reseed — `ScreenOptionService.load(...)` lazy-inits
--      from sys on next popover open per docs §1.6.)
--
-- Idempotent. Safe to re-run.
-- ============================================================================

-- 1. Wipe existing rows for THIS screen only. Strictly scoped to
--    `addon-sales-einvoices` so the V45 Expenses twin and every other
--    screen stays intact.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'addon-sales-einvoices';
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'addon-sales-einvoices';

-- 2. Reseed from PROD with transformations applied in-place.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, DISP_YN, USE_YN,
     FIX_YN, size, DYM_AREA_ID, SORT_YN, HTML_CONT)
VALUES
    ('addon-sales-einvoices', 'CHECKBOX',           'CHECKBOX',           0, 'N', 'Y', 'Y',  '50px', 'default', 'Y', NULL),
    ('addon-sales-einvoices', 'ISSU_DT',            'ISSU_DT',            1, 'Y', 'Y', 'N', '120px', 'default', 'Y', NULL),
    ('addon-sales-einvoices', 'DOC_ID',             'DOC_ID',             2, 'Y', 'Y', 'N', '150px', 'default', 'Y', NULL),
    ('addon-sales-einvoices', 'TRCO_CMNM',          'TRCO_CMNM',          3, 'Y', 'Y', 'N', '200px', 'default', 'Y', NULL),
    ('addon-sales-einvoices', 'TRCO_TRSC_CCY_CD',   'TRCO_TRSC_CCY_CD',   4, 'Y', 'Y', 'N',  '80px', 'default', 'Y', NULL),
    ('addon-sales-einvoices', 'TOTL_AMT',           'TOTL_AMT',           5, 'Y', 'Y', 'N', '120px', 'default', 'Y', NULL),
    ('addon-sales-einvoices', 'einv_invoiceStatus', 'einv_invoiceStatus', 6, 'Y', 'Y', 'N', '100px', 'default', 'Y', NULL),
    ('addon-sales-einvoices', 'TAX_AMT_SUM',        'TAX_AMT_SUM',        7, 'Y', 'Y', 'N', '110px', 'default', 'Y', NULL),
    ('addon-sales-einvoices', 'SPLY_AMT_SUM',       'SPLY_AMT_SUM',       8, 'Y', 'Y', 'N', '110px', 'default', 'Y', NULL)
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- ============================================================================
-- Open risks (NOT addressed by this migration — track separately):
--
--  R1. Frontend `EInvoicesPage` `COLUMN_REGISTRY` keys may not yet
--      match the new seed FIELD_IDs (ISSU_DT / DOC_ID / TRCO_CMNM /
--      TRCO_TRSC_CCY_CD / TOTL_AMT / einv_invoiceStatus / TAX_AMT_SUM
--      / SPLY_AMT_SUM). If the page filters visible columns through
--      its own COLUMN_REGISTRY (same shape as
--      `components/shared/AccountInquiryPage.tsx`), the table will
--      drop every column until either:
--        - the registry is renamed to match these FIELD_IDs, OR
--        - the page adds a `FIELD_ID_ALIAS` map (same pattern that
--          shipped for Account Inquiry in the previous round) so DB
--          and renderer keys can diverge cleanly.
--      Inspect `components/addon/sales-einvoices/EInvoicesPage.tsx`
--      before relying on this seed in QA.
--
--  R2. View Setting popover labels — same path as Account Inquiry
--      after the prior MR. The popover renders `item.FIELD_NM`
--      verbatim unless the page passes a `getLabel` resolver. With
--      this seed, the popup will show raw codes ("ISSU_DT", "DOC_ID",
--      …) in every locale until the e-Invoices page wires a resolver
--      that maps DB FIELD_ID → renderer key → i18n header key.
--
--  R3. Java registries — Sales e-Invoices may not be wired into Excel
--      export / sort whitelists at the time this migration lands. If
--      not, sort clicks fall through to default ORDER BY and export
--      attempts return WBE2010 ("export not configured for screen").
--      Same risk as V47 R1 — adopt provider-by-provider.
--
--  R4. `einv_invoiceStatus` is the lone non-UPPER_SNAKE FIELD_ID in
--      the seed. The transform-rule mandates "strip `_4` and nothing
--      else", so this preserves PROD verbatim. If a future migration
--      normalizes casing (`EINV_INVOICE_STATUS`), the same migration
--      MUST flip the matching frontend / Java registry entries in
--      lockstep — otherwise the table flicker problem from R1 returns.
--
--  R5. CHECKBOX seed has DISP_YN='N' verbatim from PROD. CHECKBOX is
--      already a special row excluded from the popover (CLAUDE.md
--      §13 / `VIEW_SETTING_SPECIAL_FIELD_IDS`), so DISP_YN='N' here
--      is a no-op for popover visibility. Documented for clarity —
--      do not flip it without checking whether any consumer depends
--      on the flag staying off.
--
--  R6. Per docs §1.6, lazy-init populates USER_SCR_OPTION_WECMS on
--      next GET. Existing local user overrides for this screen were
--      deleted in step 1 — by design.
--
-- ============================================================================
