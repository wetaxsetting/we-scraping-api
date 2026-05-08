-- ============================================================================
-- V54__view_setting_addon_expenses_einvoices_prod_alignment.sql
--
-- Expenses e-Invoices View Setting — realign local seed to PROD with
-- the same refactor-side normalization rules used for V49 (Sales
-- e-Invoices) and V46/V47 (Account Inquiry VND/FCY).
--
-- Per Sonny: PROD is the source of truth, DEV is wrong; rewrite the
-- seed. Same transformation shape as V49:
--
--   Rule 1 — WEB_SVC_ID:  legacy `einv_0001_001` → semantic refactor id
--                          `addon-expenses-einvoices` (per
--                          docs/jex/features/view-setting.md §1.1 and
--                          the regex in ScreenOptionController; same
--                          screenId V52's demo seed already used).
--   Rule 2 — FIELD_ID:    drop trailing `_4`     (DOC_ID_4 → DOC_ID,
--                                                  CHECKBOX_4 → CHECKBOX, …)
--   Rule 3 — FIELD_NM:    drop trailing `_4`     (PROD doubles the id as
--                                                  the display name)
--   Rule 4 — DYM_AREA_ID: 'table4' → 'default'   (single-area page per
--                                                  docs §1.5)
--   Rule 5 — DISP_ORD:    CHECKBOX = 0; data columns dense-sequential
--                          1..N in PROD's relative order (PROD's source
--                          DISP_ORDs have a gap — 1, 3, 4, 5, 6, 7, 8, 9
--                          — collapsed to 1..8 here to match the V49
--                          Sales sibling and the popover's dense
--                          renumbering convention); ETC = 99 (no ETC
--                          seeded today — Expenses e-Invoices has no
--                          action column yet).
--   Rule 6 — every other column kept verbatim    (DISP_YN, USE_YN,
--                                                  SIZE, SORT_YN,
--                                                  HTML_CONT).
--
-- Source rows (PROD `sys_scr_option_wecms` for legacy `einv_0001_001`):
--
--   field_id              field_nm              ord disp use size  area    sort html
--   CHECKBOX_4            CHECKBOX_4              0  N    Y    50px table4  N    NULL
--   ISSU_DT_4             ISSU_DT_4               1  Y    Y   120px table4  Y    NULL
--   DOC_ID_4              DOC_ID_4                3  Y    Y   150px table4  Y    NULL
--   TRCO_CMNM_4           TRCO_CMNM_4             4  Y    Y   200px table4  Y    NULL
--   TRCO_TRSC_CCY_CD_4    TRCO_TRSC_CCY_CD_4      5  Y    Y    80px table4  Y    NULL
--   TOTL_AMT_4            TOTL_AMT_4              6  Y    Y   110px table4  Y    NULL
--   einv_invoiceStatus_4  einv_invoiceStatus_4    7  Y    Y   100px table4  Y    NULL
--   TAX_AMT_SUM_4         TAX_AMT_SUM_4           8  Y    Y   110px table4  Y    NULL
--   SPLY_AMT_SUM_4        SPLY_AMT_SUM_4          9  Y    Y   120px table4  Y    NULL
--
-- After transformation (DISP_ORD densely renumbered 1..8 — matches the
-- V49 Sales twin's order):
--
--   field_id              field_nm              ord disp use size  area    sort html
--   CHECKBOX              CHECKBOX                0  N    Y    50px default N    NULL
--   ISSU_DT               ISSU_DT                 1  Y    Y   120px default Y    NULL
--   DOC_ID                DOC_ID                  2  Y    Y   150px default Y    NULL
--   TRCO_CMNM             TRCO_CMNM               3  Y    Y   200px default Y    NULL
--   TRCO_TRSC_CCY_CD      TRCO_TRSC_CCY_CD        4  Y    Y    80px default Y    NULL
--   TOTL_AMT              TOTL_AMT                5  Y    Y   110px default Y    NULL
--   einv_invoiceStatus    einv_invoiceStatus      6  Y    Y   100px default Y    NULL
--   TAX_AMT_SUM           TAX_AMT_SUM             7  Y    Y   110px default Y    NULL
--   SPLY_AMT_SUM          SPLY_AMT_SUM            8  Y    Y   120px default Y    NULL
--
-- FIX_YN is a refactor-only column (DEV/PROD don't have it). Only
-- CHECKBOX (special row, DISP_ORD=0) gets FIX_YN='Y' to mirror the
-- V32/V33 intent and the V49 Sales twin. No specific "primary
-- identifier" column for Expenses — every data column stays at the
-- column default 'N' so users can reorder them freely.
--
-- Note on `einv_invoiceStatus`: post-transform FIELD_ID keeps the
-- lowercase + camelCase shape verbatim from PROD ("strip `_4` only,
-- nothing else"). The same outlier sits in the V49 Sales seed; if a
-- future migration normalizes casing (`EINV_INVOICE_STATUS`), do both
-- screens in lockstep.
--
-- Diff vs V49 (Sales) — only SIZE differs on two columns:
--   * TOTL_AMT       — Sales 120px / Expenses 110px
--   * SPLY_AMT_SUM   — Sales 110px / Expenses 120px
-- Every other row is identical between the two siblings.
--
-- Application source code is intentionally NOT touched. Risks called
-- out at the bottom.
--
-- Three things happen, in order:
--   1. Wipe existing sys + user rows scoped strictly to
--      `web_svc_id = 'addon-expenses-einvoices'` (covers V45's initial
--      seed AND V52's demo seed). The Sales twin under
--      `addon-sales-einvoices` (V49) and every other screen stay
--      intact.
--   2. Reseed sys with the 9 transformed rows.
--   3. (No user-row reseed — `ScreenOptionService.load(...)` lazy-inits
--      from sys on next popover open per docs §1.6.)
--
-- Idempotent. Safe to re-run.
-- ============================================================================

-- 1. Wipe existing rows for THIS screen only. Strictly scoped to
--    `addon-expenses-einvoices` so V49 (Sales) and every other screen
--    stay intact.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'addon-expenses-einvoices';
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'addon-expenses-einvoices';

-- 2. Reseed from PROD with transformations applied in-place.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, DISP_YN, USE_YN,
     FIX_YN, size, DYM_AREA_ID, SORT_YN, HTML_CONT)
VALUES
    ('addon-expenses-einvoices', 'CHECKBOX',           'CHECKBOX',           0, 'N', 'Y', 'Y',  '50px', 'default', 'N', NULL),
    ('addon-expenses-einvoices', 'ISSU_DT',            'ISSU_DT',            1, 'Y', 'Y', 'N', '120px', 'default', 'Y', NULL),
    ('addon-expenses-einvoices', 'DOC_ID',             'DOC_ID',             2, 'Y', 'Y', 'N', '150px', 'default', 'Y', NULL),
    ('addon-expenses-einvoices', 'TRCO_CMNM',          'TRCO_CMNM',          3, 'Y', 'Y', 'N', '200px', 'default', 'Y', NULL),
    ('addon-expenses-einvoices', 'TRCO_TRSC_CCY_CD',   'TRCO_TRSC_CCY_CD',   4, 'Y', 'Y', 'N',  '80px', 'default', 'Y', NULL),
    ('addon-expenses-einvoices', 'TOTL_AMT',           'TOTL_AMT',           5, 'Y', 'Y', 'N', '110px', 'default', 'Y', NULL),
    ('addon-expenses-einvoices', 'einv_invoiceStatus', 'einv_invoiceStatus', 6, 'Y', 'Y', 'N', '100px', 'default', 'Y', NULL),
    ('addon-expenses-einvoices', 'TAX_AMT_SUM',        'TAX_AMT_SUM',        7, 'Y', 'Y', 'N', '110px', 'default', 'Y', NULL),
    ('addon-expenses-einvoices', 'SPLY_AMT_SUM',       'SPLY_AMT_SUM',       8, 'Y', 'Y', 'N', '120px', 'default', 'Y', NULL)
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- ============================================================================
-- Open risks (NOT addressed by this migration — track separately):
--
--  R1. Frontend `EInvoicesPage` `COLUMN_REGISTRY` may not yet match the
--      new seed FIELD_IDs (ISSU_DT / DOC_ID / TRCO_CMNM / TRCO_TRSC_CCY_CD
--      / TOTL_AMT / einv_invoiceStatus / TAX_AMT_SUM / SPLY_AMT_SUM).
--      Same risk as V49 R1 — if the page filters visible columns
--      through a registry keyed by different names, the table will
--      drop every column until either the registry is renamed or the
--      page adds a `FIELD_ID_ALIAS` map (the same pattern shipped for
--      Account Inquiry).
--
--  R2. View Setting popover labels — same path as Account Inquiry / V49.
--      Without a `getLabel` resolver passed to <ViewSettingPopover>,
--      the popup will show raw codes ("ISSU_DT", "DOC_ID", …) in every
--      locale until the e-Invoices page wires a resolver mapping
--      DB FIELD_ID → renderer key → i18n header key.
--
--  R3. Java sort + export wiring — Expenses e-Invoices may not have an
--      `ExportRowProvider` registered for `addon-expenses-einvoices`
--      yet. If not, `POST .../export` returns WBE2010. Same risk as
--      V47 R1 / V49 R3 — adopt provider-by-provider.
--
--  R4. `einv_invoiceStatus` is the lone non-UPPER_SNAKE FIELD_ID in
--      both Sales (V49) and Expenses (V54) seeds. If a future MR
--      normalizes casing to `EINV_INVOICE_STATUS`, ship both sibling
--      migrations + frontend / Java registry renames in lockstep.
--
--  R5. CHECKBOX seed has DISP_YN='N' AND SORT_YN='N' verbatim from
--      PROD (Sales V49 only had DISP_YN='N'). CHECKBOX is a special
--      row excluded from the popover regardless (CLAUDE.md §13 /
--      `VIEW_SETTING_SPECIAL_FIELD_IDS`), so both flags are no-ops
--      for popover visibility. Documented for clarity — do not flip
--      either without checking whether any consumer depends on them
--      staying off.
--
--  R6. PROD's source DISP_ORD has a gap (1, 3, 4, 5, 6, 7, 8, 9 —
--      missing 2) — likely an artifact of a removed legacy column on
--      PROD that wasn't re-numbered. Densely renumbering 1..8 here
--      matches both the V49 Sales twin and the popover's
--      "renumber-densely-on-reorder" convention. Verify with Sonny if
--      a column was deliberately removed and the gap has semantic
--      meaning — if so, V54 needs to be re-issued preserving the gap.
--
--  R7. V52 (`addon_expenses_einvoices_demo_seed`) previously seeded
--      this screen. V54's leading DELETE wipes those rows; the
--      `flyway_schema_history` row for V52 stays as-applied. Future
--      developers reading V52 should treat it as superseded by V54.
--
--  R8. Per docs §1.6, lazy-init populates USER_SCR_OPTION_WECMS on
--      next GET. Existing local user overrides for this screen were
--      deleted in step 1 — by design.
--
-- ============================================================================
