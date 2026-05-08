-- ============================================================================
-- V33__view_setting_special_columns_seed.sql
--
-- Seed the CHECKBOX special column for banking-account-inquiry-vnd.
--
-- Background — special FIELD_IDs convention (system-wide rule):
--   CHECKBOX  DISP_ORD = 0   row-selection column, always first
--   ETC       DISP_ORD = 99  optional trailing action column, always last
--
-- These rows ride through the View Setting data so the popover Save+Reset
-- cycle preserves them, but they are NEVER shown in the popup row list and
-- the user cannot edit them. Enforcement lives in:
--   - frontend  components/shared/view-setting/useViewSetting.ts
--                 (`VIEW_SETTING_SPECIAL_FIELD_IDS` + `isSpecialViewSettingField`)
--   - backend   ScreenOptionService.SPECIAL_FIELD_IDS
--                 (locks USE_YN='Y' and pins DISP_ORD to sys seed on save)
--
-- This migration seeds CHECKBOX only — Account Inquiry (VND) uses the
-- selection checkbox column but does not have an action column. ETC will be
-- seeded by the feature migration of any future page that needs it; do
-- NOT bulk-seed ETC across pages that don't render an action column.
--
-- Idempotent via ON CONFLICT DO NOTHING. Safe to re-run.
-- FIX_YN='Y' is defensive — if the popover filter ever regresses, the
-- checkbox + reorder buttons stay disabled for this row anyway.
-- ============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, FIX_YN, DISP_YN, DYM_AREA_ID)
VALUES
    ('banking-account-inquiry-vnd', 'CHECKBOX', 'Checkbox', 0, 'Y', 'Y', 'Y', 'default')
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
