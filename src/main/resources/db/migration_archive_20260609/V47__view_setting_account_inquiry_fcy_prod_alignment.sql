-- ============================================================================
-- V47__view_setting_account_inquiry_fcy_prod_alignment.sql
--
-- Account Inquiry (FCY) View Setting — seed from PROD with refactor-side
-- normalization rules. FCY twin of V46 (VND).
--
-- Per Sonny: PROD is the source of truth, DEV is wrong; rewrite the seed
-- the same way V46 did for VND. The transformations are identical:
--
--   Rule 1 — FIELD_ID:    drop trailing `_2`     (CHECKBOX_2 → CHECKBOX,
--                                                  BANK_NM_2 → BANK_NM, …)
--   Rule 2 — FIELD_NM:    drop trailing `_2`     (PROD doubles the id as
--                                                  the display name)
--   Rule 3 — DYM_AREA_ID: 'table2' → 'default'   (single-area page per
--                                                  docs §1.5)
--   Rule 4 — every other column kept verbatim    (DISP_ORD, DISP_YN,
--                                                  USE_YN, SIZE, SORT_YN,
--                                                  HTML_CONT)
--
-- Source rows (PROD `sys_scr_option_wecms` for legacy `fnsh_0005_001`):
--
--   field_id              field_nm              ord disp use size  area    sort html
--   CHECKBOX_2            CHECKBOX_2              0  Y    Y   35px  table2  Y    NULL
--   BANK_NM_2             BANK_NM_2               8  Y    Y   250px table2  Y    NULL
--   ACCT_NICK_NM_2        ACCT_NICK_NM_2          9  Y    Y   160px table2  Y    NULL
--   BAL_2                 BAL_2                  10  Y    Y   160px table2  Y    NULL
--   REAL_AMT_2            REAL_AMT_2             11  Y    Y   160px table2  Y    NULL
--   ACCT_HIS_LST_INQ_DT_2 ACCT_HIS_LST_INQ_DT_2  12  Y    Y   150px table2  Y    NULL
--
-- After transformation:
--
--   field_id              field_nm              ord disp use size  area    sort html
--   CHECKBOX              CHECKBOX                0  Y    Y   35px  default Y    NULL
--   BANK_NM               BANK_NM                 8  Y    Y   250px default Y    NULL
--   ACCT_NICK_NM          ACCT_NICK_NM            9  Y    Y   160px default Y    NULL
--   BAL                   BAL                    10  Y    Y   160px default Y    NULL
--   REAL_AMT              REAL_AMT               11  Y    Y   160px default Y    NULL
--   ACCT_HIS_LST_INQ_DT   ACCT_HIS_LST_INQ_DT    12  Y    Y   150px default Y    NULL
--
-- Diff from VND (V46): only ACCT_NICK_NM SIZE differs — 107px (VND) vs
-- 160px (FCY). Every other row is identical between the two screens; the
-- two stay distinct only by WEB_SVC_ID.
--
-- WEB_SVC_ID is rewritten from the legacy JEX id (`fnsh_0005_001`) to
-- the semantic refactor id (`banking-account-inquiry-fcy`, per
-- docs/jex/features/view-setting.md §1.1 and the regex enforced in
-- ScreenOptionController). This deviates from the literal instruction
-- to "use WEB_SVC_ID = fnsh_0005_001" because the controller's @Pattern
-- regex (`^[a-z][a-z0-9]*(-[a-z0-9]+)+$`) rejects underscored legacy
-- ids — leaving the row under `fnsh_0005_001` would orphan it. Same
-- assumption + same refactor-namespace as V46 for VND.
--
-- FIX_YN is a refactor-only column (DEV/PROD don't have it). CHECKBOX
-- (special row, DISP_ORD=0) and BANK_NM (primary identifier column)
-- keep FIX_YN='Y' to mirror the V32/V33 intent; everything else stays
-- at the column default 'N'.
--
-- Application source code is intentionally NOT touched (per Sonny's
-- instruction). Risks called out at the bottom.
--
-- Three things happen, in order:
--   1. Wipe existing sys + user rows scoped strictly to
--      `web_svc_id = 'banking-account-inquiry-fcy'`. Other screens
--      (including VND, seeded by V46) are untouched.
--   2. Reseed sys with the 6 transformed rows.
--   3. (No user-row reseed — `ScreenOptionService.load(...)` lazy-inits
--      from sys on next popover open per docs §1.6.)
--
-- Idempotent. Safe to re-run.
-- ============================================================================

-- 1. Wipe existing rows for THIS screen only. Strictly scoped to
--    `banking-account-inquiry-fcy` so the VND seed from V46 and every
--    other screen stays intact.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-account-inquiry-fcy';
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-account-inquiry-fcy';

-- 2. Reseed from PROD with transformations applied in-place.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, DISP_YN, USE_YN,
     FIX_YN, size, DYM_AREA_ID, SORT_YN, HTML_CONT)
VALUES
    ('banking-account-inquiry-fcy', 'CHECKBOX',            'CHECKBOX',             0, 'Y', 'Y', 'Y',  '35px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'BANK_NM',             'BANK_NM',              8, 'Y', 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'ACCT_NICK_NM',        'ACCT_NICK_NM',         9, 'Y', 'Y', 'N', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'BAL',                 'BAL',                 10, 'Y', 'Y', 'N', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'REAL_AMT',            'REAL_AMT',            11, 'Y', 'Y', 'N', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'ACCT_HIS_LST_INQ_DT', 'ACCT_HIS_LST_INQ_DT', 12, 'Y', 'Y', 'N', '150px', 'default', 'Y', NULL)
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- ============================================================================
-- Open risks (NOT addressed by this migration — track separately):
--
--  R1. Java registries don't yet recognise the FCY screenId at all.
--      AccountInquiryExportProvider currently registers only
--      `banking-account-inquiry-vnd` (see CLAUDE.md "Endpoints with
--      Excel export wired"). Until an FCY export provider is added,
--      POST /api/v1/banking/accounts/export with screenId
--      `banking-account-inquiry-fcy` returns WBE2010
--      ("export not configured for screen"). The list endpoint
--      `GET /api/v1/banking/accounts?currency=USD|EUR|...` already
--      works — only export is missing.
--
--  R2. Sort whitelist (AccountSortField) uses legacy refactor ids
--      (BANK / ACCOUNT_NICKNAME / BALANCE / AVAILABLE_BALANCE /
--      LAST_UPDATE) and won't match the new FCY (or VND, post-V46)
--      seed FIELD_IDs (BANK_NM / ACCT_NICK_NM / BAL / REAL_AMT /
--      ACCT_HIS_LST_INQ_DT). Sort clicks on FCY headers fall through
--      to default ORDER BY (`prnSeq, bankCd, acctNo`) until the enum
--      is renamed. Same risk as V46 R1 — one rename closes both.
--
--  R3. Frontend registry at wecms-web/lib/viewSettingScreens.ts
--      must register `banking-account-inquiry-fcy` with the same
--      transformed FIELD_IDs (CHECKBOX, BANK_NM, ACCT_NICK_NM, BAL,
--      REAL_AMT, ACCT_HIS_LST_INQ_DT). Without it, the FCY popover
--      either won't open or will paint a stale fallback column set.
--
--  R4. SORT_YN='Y' on every column. Per CLAUDE.md "Common table sort"
--      every SORT_YN='Y' column must have a matching whitelist enum
--      constant — until R2 is closed, sort glyphs render but do
--      nothing. Either fix the enum in the same MR or temporarily
--      flip SORT_YN='N' on the unmapped columns.
--
--  R5. VAS_ACCOUNT is not seeded for FCY (PROD doesn't have it for
--      this screen either, same as VND).
--
--  R6. Per docs §1.6, lazy-init populates USER_SCR_OPTION_WECMS on
--      the next GET. Existing local user overrides for this screen
--      were deleted in step 1 — by design.
--
-- ============================================================================
