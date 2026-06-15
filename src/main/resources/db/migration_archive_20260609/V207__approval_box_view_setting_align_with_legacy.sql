-- ----------------------------------------------------------------------------
-- V207 — Align Approval Box View Setting with the legacy resl_0001_001 OSMC
--        column set + DISP_ORD ordering (2026-05-27)
--
-- The original V109 seed used a refactor-tailored set (10 user columns) with
-- CURRENCY/APPRER_NM hidden at the tail. The legacy JEX page resl_0001_001
-- (queried verbatim via the JEX DEV `SYS_SCR_OPTION_WECMS` table on
-- 2026-05-27) renders 11 user columns in this canonical order:
--
--   1.  REG_DTM         Date
--   2.  DOC_NM          Type
--   3.  DOC_NO          Document No.
--   4.  TITL            Title
--   5.  FILE_NM         File Name              -- absent from V109
--   6.  CURRENCY        Transfer Currency      -- was V109 disp_ord=10, USE_YN='N'
--   7.  TOT_PAY_AMT     Transfer Amount        -- was V109 disp_ord=5
--   8.  PAY_END_YN      Approval Status        -- was V109 disp_ord=6
--   9.  PAY_DATE        Scheduled Date         -- was V109 disp_ord=7
--   10. DRAFT_USER_NM   Requestor              -- was V109 disp_ord=8
--   11. APPRER_NM       Approver               -- was V109 disp_ord=9, USE_YN='N'
--
-- Refactor identifier naming — slugs, not WSVC ids (per workspace CLAUDE.md):
-- the FIELD_IDs drop the legacy "_2" suffix (REG_DTM, not REG_DTM_2) — that
-- suffix is a JEX `dymAreaId='table3'` implementation detail. The DISP_ORD
-- VALUES are what align with legacy, not the FIELD_ID strings.
--
-- Idempotent. Re-running is a no-op:
--   * `INSERT … ON CONFLICT DO NOTHING` for the new FILE_NM row.
--   * UPDATE statements set the same DISP_ORD / USE_YN as the legacy seed.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — the lazy-init
--     branch in `ScreenOptionService.load(...)` re-copies the corrected
--     sys defaults on the user's next GET.
-- ----------------------------------------------------------------------------

-- 1. Insert the missing FILE_NM column (disp_ord = 5).
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('approval-box', 'default', 'FILE_NM', 'File Name', 5, 'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 2. Reorder + un-hide the existing rows to match legacy.
--    CHECKBOX stays at 0 and ETC stays at 99 — special FIELD_IDs are admin-
--    controlled and untouched by this migration.
UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 6, USE_YN = 'Y', SIZE = '150px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'CURRENCY';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 7, MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'TOT_PAY_AMT';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 8, MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'PAY_END_YN';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 9, MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'PAY_DATE';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 10, MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'DRAFT_USER_NM';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 11, USE_YN = 'Y', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'APPRER_NM';

-- 3. Wipe USER_SCR_OPTION_WECMS overrides for the screen so the corrected
--    sys defaults take effect on every user's next GET (the
--    `ScreenOptionService.load(...)` lazy-init branch re-copies sys into
--    user when the user has zero rows for the scope). Same precedent as
--    V32/V41/V42 — R&D scope, no preservation of local user-saved overrides.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default';
