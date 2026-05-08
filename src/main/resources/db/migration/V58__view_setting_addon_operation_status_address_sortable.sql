-- ============================================================================
-- V58__view_setting_addon_operation_status_address_sortable.sql
--
-- Flip `SORT_YN` from 'N' → 'Y' on the seeded `ADDRESS` column for
-- `WEB_SVC_ID = 'addon-operation-status'` and align the backend sort
-- whitelist (`OperationStatusSortField`) with a new `ADDRESS →
-- officeadd` constant in the same MR.
--
-- Why: PO requested ADDRESS be sortable on Operation Status Inquiry
-- (parity with the canonical Account Inquiry sortable-everywhere
-- behaviour). The V56 seed shipped `ADDRESS.SORT_YN='N'` based on the
-- author's judgement that "sorting full address strings is rarely
-- meaningful" — overruled.
--
-- Pattern follows V41 / V42 corrective seeds:
--   1. UPDATE the sys row (no INSERT — the row exists from V56).
--   2. Clear `USER_SCR_OPTION_WECMS` rows for the screen so
--      `ScreenOptionService.load(...)` lazy-init re-copies the
--      corrected sys default on next popover open. (No popover renders
--      on this page today — IR-9 — but the lazy-init still keys the
--      user table when the export endpoint touches it.)
--
-- Idempotent. Safe to re-run.
--
-- Java side, same MR: `OperationStatusSortField` enum gains an
-- `ADDRESS("officeadd")` constant. The frontend's `FALLBACK_COLUMNS`
-- in `<OperationStatusPage>` is updated to match (`ADDRESS.SORT_YN='Y'`)
-- so first-paint behaviour matches post-V58 seed.
--
-- See:
--  - wecms-api-main/.../service/OperationStatusSortField.java
--  - wecms-web-main/.../components/addon/operation-status/OperationStatusPage.tsx
--  - wecms-api-main/CLAUDE.md "Common table sort" rule (seed flag must
--    agree with whitelist enum)
-- ============================================================================

-- 1. Flip the sys default for ADDRESS on this screen.
UPDATE SYS_SCR_OPTION_WECMS
   SET SORT_YN = 'Y'
 WHERE WEB_SVC_ID = 'addon-operation-status'
   AND FIELD_ID   = 'ADDRESS'
   AND SORT_YN   <> 'Y';

-- 2. Clear user overrides for THIS screen so lazy-init re-copies the
--    corrected sys default. Strictly scoped — every other screen's
--    user overrides stay intact.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'addon-operation-status';
