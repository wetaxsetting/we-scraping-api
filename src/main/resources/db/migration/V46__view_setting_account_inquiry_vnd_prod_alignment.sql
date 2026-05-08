-- ============================================================================
-- V46__view_setting_account_inquiry_vnd_prod_alignment.sql
--
-- Account Inquiry (VND) View Setting — realign local seed to PROD with
-- refactor-side normalization rules.
--
-- The local seed for Banking > Account Inquiry (VND) has drifted from PROD.
-- Per Sonny: PROD is the source of truth, DEV is wrong; rewrite the seed.
--
-- This migration uses PROD's column values verbatim EXCEPT for the
-- agreed transformation rules below (applied in-place — no separate
-- mapping table). The rules normalize the JEX legacy `_2` suffix and
-- the area-name convention to the single-area refactor shape:
--
--   Rule 1 — FIELD_ID:    drop trailing `_2`     (CHECKBOX_2 → CHECKBOX,
--                                                  BANK_NM_2 → BANK_NM, …)
--   Rule 2 — FIELD_NM:    drop trailing `_2`     (same as above; PROD
--                                                  doubles the id as the
--                                                  display name — no
--                                                  human-readable label
--                                                  yet, frontend i18n
--                                                  layer fills that in)
--   Rule 3 — DYM_AREA_ID: 'table2' → 'default'   (single-area page per
--                                                  docs §1.5)
--   Rule 4 — every other column kept verbatim    (DISP_ORD, DISP_YN,
--                                                  USE_YN, SIZE, SORT_YN,
--                                                  HTML_CONT)
--
-- Source rows (PROD `sys_scr_option_wecms` for legacy `fnsh_0001_001`):
--
--   field_id              field_nm              ord disp use size  area    sort html
--   CHECKBOX_2            CHECKBOX_2              0  Y    Y   35px  table2  Y    NULL
--   BANK_NM_2             BANK_NM_2               8  Y    Y   250px table2  Y    NULL
--   ACCT_NICK_NM_2        ACCT_NICK_NM_2          9  Y    Y   107px table2  Y    NULL
--   BAL_2                 BAL_2                  10  Y    Y   160px table2  Y    NULL
--   REAL_AMT_2            REAL_AMT_2             11  Y    Y   160px table2  Y    NULL
--   ACCT_HIS_LST_INQ_DT_2 ACCT_HIS_LST_INQ_DT_2  12  Y    Y   150px table2  Y    NULL
--
-- After transformation:
--
--   field_id              field_nm              ord disp use size  area    sort html
--   CHECKBOX              CHECKBOX                0  Y    Y   35px  default Y    NULL
--   BANK_NM               BANK_NM                 8  Y    Y   250px default Y    NULL
--   ACCT_NICK_NM          ACCT_NICK_NM            9  Y    Y   107px default Y    NULL
--   BAL                   BAL                    10  Y    Y   160px default Y    NULL
--   REAL_AMT              REAL_AMT               11  Y    Y   160px default Y    NULL
--   ACCT_HIS_LST_INQ_DT   ACCT_HIS_LST_INQ_DT    12  Y    Y   150px default Y    NULL
--
-- WEB_SVC_ID is rewritten from the legacy JEX id (`fnsh_0001_001`) to
-- the semantic refactor id (`banking-account-inquiry-vnd`, per
-- docs/jex/features/view-setting.md §1.1 and the regex enforced in
-- ScreenOptionController). Legacy underscored ids fail the controller's
-- @Pattern guard, so keeping `fnsh_0001_001` here would orphan the rows.
--
-- FIX_YN is a refactor-only column (DEV/PROD don't have it). CHECKBOX
-- (special row, DISP_ORD=0) and BANK_NM (primary identifier column)
-- keep FIX_YN='Y' to mirror the V32/V33 intent; everything else stays
-- at the column default 'N'.
--
-- Application source code is intentionally NOT touched in this
-- migration (per Sonny's instruction). Risks called out at the bottom.
--
-- Three things happen, in order:
--   1. Wipe existing sys + user rows scoped strictly to
--      `web_svc_id = 'banking-account-inquiry-vnd'`. Other screens are
--      untouched.
--   2. Reseed sys with the 6 transformed rows.
--   3. (No user-row reseed — `ScreenOptionService.load(...)` lazy-inits
--      from sys on next popover open per docs §1.6.)
--
-- Idempotent. Safe to re-run.
-- ============================================================================

-- 1. Wipe existing rows for this screen so the seed below is the single
--    authoritative version. Scope is strictly the refactor WEB_SVC_ID;
--    no other screen's rows are touched.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd';
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd';

-- 2. Reseed from PROD with transformations applied in-place.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, DISP_YN, USE_YN,
     FIX_YN, size, DYM_AREA_ID, SORT_YN, HTML_CONT)
VALUES
    ('banking-account-inquiry-vnd', 'CHECKBOX',            'CHECKBOX',             0, 'Y', 'Y', 'Y',  '35px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'BANK_NM',             'BANK_NM',              8, 'Y', 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'ACCT_NICK_NM',        'ACCT_NICK_NM',         9, 'Y', 'Y', 'N', '107px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'BAL',                 'BAL',                 10, 'Y', 'Y', 'N', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'REAL_AMT',            'REAL_AMT',            11, 'Y', 'Y', 'N', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'ACCT_HIS_LST_INQ_DT', 'ACCT_HIS_LST_INQ_DT', 12, 'Y', 'Y', 'N', '150px', 'default', 'Y', NULL)
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- ============================================================================
-- Open risks (NOT addressed by this migration — track separately):
--
--  R1. FIELD_IDs no longer match the existing Java registries from V32–V42:
--        - AccountSortField enum constants are BANK / ACCOUNT_NICKNAME /
--          BALANCE / AVAILABLE_BALANCE / LAST_UPDATE — none of these
--          match the new BANK_NM / ACCT_NICK_NM / BAL / REAL_AMT /
--          ACCT_HIS_LST_INQ_DT seed ids → every column-header click
--          falls through to default ORDER BY (`prnSeq, bankCd, acctNo`).
--        - AccountInquiryExportProvider allow-list uses the same legacy
--          names → after intersection the export request resolves to
--          zero columns and returns WBE2013.
--      ScreenOptionService.SPECIAL_FIELD_IDS already contains 'CHECKBOX',
--      so the special-row lock is preserved (CHECKBOX_2 risk from the
--      pre-transformation draft is GONE).
--      Source-code follow-up MR must rename the enum + allow-list
--      entries to the PROD ids OR introduce a wire-FIELD_ID → entity-
--      property translation layer.
--
--  R2. Frontend column rendering and the registry at
--      wecms-web-main/lib/viewSettingScreens.ts must be updated to use
--      the same transformed FIELD_IDs (BANK_NM, ACCT_NICK_NM, BAL,
--      REAL_AMT, ACCT_HIS_LST_INQ_DT, plus CHECKBOX as the special
--      anchor). Any reference to the old BANK / ACCOUNT_NICKNAME /
--      BALANCE / AVAILABLE_BALANCE / LAST_UPDATE keys leaves a column
--      hidden until the registry is realigned.
--
--  R3. VAS_ACCOUNT is no longer seeded (PROD doesn't have it). The
--      V41/V42 deferral comments referencing BUSI_VAS_ACCT_INFM remain
--      accurate but the column is now invisible to the popover entirely,
--      not just hidden by DISP_YN.
--
--  R4. SORT_YN='Y' on every column post-transformation. Per the
--      "Common table sort" rule (wecms-api-main/CLAUDE.md), every
--      SORT_YN='Y' column must have a constant in the matching
--      whitelist enum (AccountSortField). Until R1 is closed the
--      backend silently falls through to default ORDER BY for clicks
--      on these columns — UI shows sort glyphs that do nothing. Either
--      ship the enum rename in the same MR or temporarily flip
--      SORT_YN='N' for the unmapped columns until the rename lands.
--
--  R5. Per docs §1.6, lazy-init populates USER_SCR_OPTION_WECMS on the
--      next GET. Existing local user overrides for this screen were
--      deleted in step 1 — by design. Developers will see the View
--      Setting popup reset on next login.
--
-- ============================================================================
