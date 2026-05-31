-- ----------------------------------------------------------------------------
-- V216 — Align approval-box View Setting `SIZE` values with the legacy
--        resl_0001_001 DB widths surfaced by sys_scr_option_wecms on PROD
--        (2026-05-30)
--
-- Background
-- ----------
-- V109 seeded the original `approval-box` rows with Figma-driven placeholder
-- widths (REG_DTM=120px, DOC_NM=160px, …). V207 reordered the columns to
-- match legacy DISP_ORD + added the FILE_NM row at 250px / CURRENCY at
-- 150px, but did not touch the other widths.
--
-- The frontend now consumes the DB `SIZE` value verbatim on the table's
-- <col style="width: …">. Aligning these values with the legacy PROD
-- widths is the only place where the change is meaningful — neither the
-- backend service nor the frontend ApprovalTable should know about
-- per-column pixel constants.
--
-- Legacy PROD widths (from the live `sys_scr_option_wecms` DB row dump
-- the PO shared 2026-05-30, FIELD_IDs translated to refactor slugs by
-- dropping the legacy `_2` dymAreaId suffix per CLAUDE.md identifier
-- naming):
--
--   REG_DTM         Date                  90px
--   DOC_NM          Type                 170px
--   DOC_NO          Document No.         135px
--   TITL            Title                220px
--   FILE_NM         File Name            260px
--   CURRENCY        Transfer Currency    150px   (already set by V207)
--   TOT_PAY_AMT     Transfer Amount      150px
--   PAY_END_YN      Approval Status      160px
--   PAY_DATE        Scheduled Date       145px
--   DRAFT_USER_NM   Requestor            180px
--   APPRER_NM       Approver             180px
--
-- CHECKBOX (38px) and ETC (60px) are admin-controlled special FIELD_IDs
-- and stay untouched (CLAUDE.md "Special FIELD_IDs" rule).
--
-- Idempotent. Re-running is a no-op:
--   * UPDATEs set the same SIZE / SORT_YN values that the legacy seed has.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — the lazy-init
--     branch in `ScreenOptionService.load(...)` re-copies the corrected
--     sys defaults on the user's next GET (same precedent as V32/V41/V42
--     /V207).
-- ----------------------------------------------------------------------------

-- 1. SIZE values — align with legacy PROD widths.
UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '120px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'REG_DTM';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '170px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'DOC_NM';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '135px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'DOC_NO';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '220px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'TITL';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '260px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'FILE_NM';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '150px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'CURRENCY';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '150px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'TOT_PAY_AMT';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '160px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'PAY_END_YN';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '145px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'PAY_DATE';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '180px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'DRAFT_USER_NM';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '180px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default' AND FIELD_ID = 'APPRER_NM';

-- 2. SORT_YN — re-assert 'Y' on every user column the legacy PROD seed
--    treats as sortable. V109 already set these to 'Y'; this is a
--    defensive idempotent reassertion in case a local DB carries an
--    earlier ad-hoc flip.
UPDATE SYS_SCR_OPTION_WECMS
   SET SORT_YN = 'Y', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'approval-box'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'REG_DTM', 'DOC_NM', 'DOC_NO', 'TITL', 'FILE_NM',
       'CURRENCY', 'TOT_PAY_AMT', 'PAY_END_YN', 'PAY_DATE',
       'DRAFT_USER_NM', 'APPRER_NM'
   );

-- 3. Wipe USER_SCR_OPTION_WECMS overrides for the screen so the corrected
--    sys defaults take effect on every user's next GET. Same precedent
--    as V32/V41/V42/V207 — R&D scope, no preservation of local
--    user-saved overrides.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'approval-box' AND DYM_AREA_ID = 'default';
