-- ============================================================================
-- V32__view_setting_semantic_screen_ids.sql
--
-- Cutover from legacy WEB_SVC_ID code values (e.g. fnsh_0001_001) to the
-- semantic kebab-case screenId convention locked in by
-- docs/features/view-setting.md (§1.1, §1.5).
--
--   <module>-<page>[-<type>]    e.g. banking-account-inquiry-vnd
--
-- This is the cutover step. It is intentionally a "wipe + reseed" rather
-- than a UPDATE...SET WEB_SVC_ID = ... mapping because the refactor is
-- local-only R&D and there is no preservation requirement for user
-- overrides during the cutover (per Sonny — "no migration required for
-- now"). Anyone running the local stack should expect their View Setting
-- popup to reset to defaults after this migration applies; that's by
-- design.
--
-- Three things happen in one migration:
--   1. Wipe the V30 fnsh_0001_001 rows from both sys + user tables.
--   2. Reseed Account Inquiry (VND) under banking-account-inquiry-vnd
--      with explicit DYM_AREA_ID = 'default' on every row.
--   3. Backfill any stray nulls in DYM_AREA_ID and ALTER both columns to
--      NOT NULL so future seeds can never compile with a null area
--      (V30 schema had it nullable — see design §2.1).
--
-- No PK / FK / index changes. Idempotent; safe to re-run on a fresh DB.
-- ============================================================================

-- 1. Wipe legacy seed (and any user overrides keyed off it).
DELETE FROM USER_SCR_OPTION_WECMS WHERE WEB_SVC_ID = 'fnsh_0001_001';
DELETE FROM SYS_SCR_OPTION_WECMS  WHERE WEB_SVC_ID = 'fnsh_0001_001';

-- 2. Reseed Banking > Account Inquiry (VND) under the semantic screenId.
--    DYM_AREA_ID = 'default' on every row per §1.5 single-area convention.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, FIX_YN, DISP_YN, DYM_AREA_ID)
VALUES
    ('banking-account-inquiry-vnd', 'BANK',              'Bank',              1, 'Y', 'Y', 'Y', 'default'),
    ('banking-account-inquiry-vnd', 'ACCOUNT_NICKNAME',  'Account Nickname',  2, 'Y', 'N', 'Y', 'default'),
    ('banking-account-inquiry-vnd', 'BALANCE',           'Balance',           3, 'Y', 'N', 'Y', 'default'),
    ('banking-account-inquiry-vnd', 'AVAILABLE_BALANCE', 'Available Balance', 4, 'Y', 'N', 'Y', 'default'),
    ('banking-account-inquiry-vnd', 'VAS_ACCOUNT',       'VAS Account',       5, 'N', 'N', 'Y', 'default'),
    ('banking-account-inquiry-vnd', 'LAST_UPDATE',       'Last Update',       6, 'Y', 'N', 'Y', 'default')
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Backfill any stray nulls (defensive — should be none after step 1)
--    and lock both columns to NOT NULL so seeds without DYM_AREA_ID fail
--    fast at insert time.
UPDATE SYS_SCR_OPTION_WECMS  SET DYM_AREA_ID = 'default' WHERE DYM_AREA_ID IS NULL;
UPDATE USER_SCR_OPTION_WECMS SET DYM_AREA_ID = 'default' WHERE DYM_AREA_ID IS NULL;

ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN DYM_AREA_ID SET NOT NULL;
ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN DYM_AREA_ID SET NOT NULL;
