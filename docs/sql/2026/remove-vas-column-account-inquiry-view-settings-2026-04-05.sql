-- WeCMS Manual Database Change
-- Date: 2026-04-05
-- Source: migrated from Claude Code (AI)/sql/fnsh_0001_001_fnsh_0005_001_remove_vas_20260405.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: UAT first, then PROD after verification (per the original script header)
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — deleted View Setting rows must be re-seeded from a pre-change snapshot
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- [IMP] Remove VAS Account Column from Account Inquiry (VND & FCY)
-- Feature: remove-vas-column-and-personal-banking
-- Date: 2026-04-05
-- ============================================================================
-- This script removes VAS column configurations and table3 dymarea rows
-- from the View Settings tables. After execution, users will see the updated
-- column layout (table2 only, no VAS column) on next page load.
--
-- Execution order:
--   1. Delete from sys_scr_option_wecms (system-level column config)
--   2. Delete from user_scr_option_wecms (user cached settings)
--
-- Run on: UAT first, then PROD after verification
-- ============================================================================

BEGIN;

-- ============================================================================
-- 1. sys_scr_option_wecms — Remove VAS column rows and table3 dymarea rows
-- ============================================================================

-- fnsh_0001_001 (VND Account Inquiry)
-- Delete VAS_CD from table1 (VAS column in non-VAS layout)
DELETE FROM sys_scr_option_wecms
WHERE web_svc_id = 'fnsh_0001_001'
  AND field_id = 'VAS_CD'
  AND dym_area_id = 'table1';

-- Delete all table3 dymarea rows (VAS-enabled layout — no longer used)
DELETE FROM sys_scr_option_wecms
WHERE web_svc_id = 'fnsh_0001_001'
  AND dym_area_id = 'table3';

-- fnsh_0005_001 (FCY Account Inquiry)
-- Delete VAS_CD from table1
DELETE FROM sys_scr_option_wecms
WHERE web_svc_id = 'fnsh_0005_001'
  AND field_id = 'VAS_CD'
  AND dym_area_id = 'table1';

-- Delete all table3 dymarea rows
DELETE FROM sys_scr_option_wecms
WHERE web_svc_id = 'fnsh_0005_001'
  AND dym_area_id = 'table3';

-- ============================================================================
-- 2. user_scr_option_wecms — Clear user cached settings
--    System will auto-re-insert from sys_scr_option_wecms on next page load
-- ============================================================================

DELETE FROM user_scr_option_wecms
WHERE web_svc_id = 'fnsh_0001_001';

DELETE FROM user_scr_option_wecms
WHERE web_svc_id = 'fnsh_0005_001';

COMMIT;

-- ============================================================================
-- Verification queries (run after execution to confirm)
-- ============================================================================

-- Should return 0 rows (no VAS or table3 rows remaining)
-- SELECT * FROM sys_scr_option_wecms
-- WHERE web_svc_id IN ('fnsh_0001_001', 'fnsh_0005_001')
--   AND (field_id LIKE '%VAS%' OR dym_area_id = 'table3');

-- Should return 0 rows (user cache cleared)
-- SELECT COUNT(*) FROM user_scr_option_wecms
-- WHERE web_svc_id IN ('fnsh_0001_001', 'fnsh_0005_001');
