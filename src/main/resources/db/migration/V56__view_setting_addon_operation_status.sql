-- ============================================================================
-- V56__view_setting_addon_operation_status.sql
--
-- Seed `SYS_SCR_OPTION_WECMS` for Add-on > Operation Status Inquiry
-- (`WEB_SVC_ID = 'addon-operation-status'`, `DYM_AREA_ID = 'default'`).
--
-- This page does NOT render the View Setting popover — the toolbar is
-- Excel + Print + Update + Inquire only (Figma 12346:109158 has no
-- list-view icon). The seed exists so:
--
--  1. The Excel export endpoint (`POST /api/v1/addon/operation-status/
--     export`) can intersect requested columns against
--     `USE_YN='Y'` per the global Excel-export rule (defence in depth).
--  2. The shared `useExcelExport()` hook on the frontend can drive the
--     spreadsheet's column list off `vs.items`, mirroring Account
--     Inquiry / Sales e-Invoices.
--  3. Future PO requests for user-tunable column visibility / ordering
--     on this page become a frontend-only change (drop the existing
--     `<ViewSettingPopover>` into the toolbar) — the backend surface
--     is already global.
--
-- Columns (matches Figma 12346:109158 + the canonical sort whitelist
-- in `OperationStatusSortField`):
--
--   field_id           field_nm           ord disp use fix size  area    sort
--   CHECKBOX           CHECKBOX             0  N    Y   Y    66px default N
--   COMPANY            COMPANY              1  Y    Y   N   auto default Y
--   ADDRESS            ADDRESS              2  Y    Y   N   auto default N
--   OPERATION_STATUS   OPERATION_STATUS     3  Y    Y   N   339px default Y
--   LAST_UPDATE        LAST_UPDATE          4  Y    Y   N   220px default Y
--
--  * CHECKBOX is the special row-selection column (`DISP_ORD=0`,
--    `FIX_YN='Y'`). Frontend popover hides it, backend
--    `ScreenOptionService.SPECIAL_FIELD_IDS` locks it server-side.
--  * COMPANY / ADDRESS use `auto` for SIZE — these two columns flex to
--    fill the table after the fixed-width siblings (`66 + 339 + 220 =
--    625px`) take their share of `min-w-[960px]`.
--  * OPERATION_STATUS / LAST_UPDATE use Figma WB365 widths (matches
--    `<col className="w-[339px]" />` / `w-[220px]` on
--    `<OperationStatusPage>`).
--  * SORT_YN: COMPANY / OPERATION_STATUS / LAST_UPDATE = 'Y' (matches
--    the `OperationStatusSortField` whitelist enum). ADDRESS = 'N'
--    (sorting full address strings is rarely meaningful; the
--    whitelist enum has no `ADDRESS` constant).
--  * No ETC (DISP_ORD=99) — this page has no per-row action column.
--
-- No source code is touched. Idempotent — `INSERT … ON CONFLICT DO
-- NOTHING` guards re-runs.
-- ============================================================================

-- 1. Wipe any existing rows for THIS screen only (defensive — first
--    seed of this screen, but keeps the migration re-runnable if the
--    seed shape changes in a follow-up).
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'addon-operation-status';
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'addon-operation-status';

-- 2. Seed the system defaults.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, DISP_YN, USE_YN,
     FIX_YN, size, DYM_AREA_ID, SORT_YN, HTML_CONT)
VALUES
    ('addon-operation-status', 'CHECKBOX',         'CHECKBOX',         0, 'N', 'Y', 'Y',  '66px', 'default', 'N', NULL),
    ('addon-operation-status', 'COMPANY',          'COMPANY',          1, 'Y', 'Y', 'N',  'auto', 'default', 'Y', NULL),
    ('addon-operation-status', 'ADDRESS',          'ADDRESS',          2, 'Y', 'Y', 'N',  'auto', 'default', 'N', NULL),
    ('addon-operation-status', 'OPERATION_STATUS', 'OPERATION_STATUS', 3, 'Y', 'Y', 'N', '339px', 'default', 'Y', NULL),
    ('addon-operation-status', 'LAST_UPDATE',      'LAST_UPDATE',      4, 'Y', 'Y', 'N', '220px', 'default', 'Y', NULL)
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- ============================================================================
-- Open risks (NOT addressed by this migration — track separately):
--
--  R1. Frontend `OperationStatusPage` does not yet declare a
--      `COLUMN_REGISTRY` keyed by these `FIELD_ID`s. The page renders
--      a hand-rolled 5-column table today — Phase 4 frontend work
--      replaces it with `vs.items` filtered to `USE_YN='Y'`.
--
--  R2. View Setting popover is intentionally NOT rendered on this
--      page (Figma 12346:109158 has no list-view icon). The seed
--      drives the Excel-export column intersection only. If PO later
--      asks for user-tunable columns, drop `<ViewSettingPopover>`
--      into `<OperationStatusActionBar>` — no schema or service
--      change needed.
--
--  R3. `REPRESENTATIVEFIRSTNAME` is NOT seeded. It exists on the
--      legacy WSVC but is not currently surfaced on the static UI.
--      If product later surfaces it, add a row here with `USE_YN='N'`
--      so opt-in is seed-only (matches the V42 `VAS_ACCOUNT` pattern).
--
--  R4. No FIX_YN='Y' on data columns — every visible data column is
--      reorderable when the popover ships. This is intentional;
--      Operation Status has no required-anchor column unlike
--      Account Inquiry's `BANK`.
-- ============================================================================
