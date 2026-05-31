-- ----------------------------------------------------------------------------
-- V217 — Align Payment > Internal Transfer > Scheduled Transfer View Setting
--        with the legacy pymn_0009_001 OSMC column set, widths, and sort
--        flags (2026-05-30)
--
-- The original V131 seed used a refactor-tailored set built around the
-- shell's hardcoded FIELD_IDs (TITL=File Name, REG_ID=Requestor,
-- APPR_SEQ_NO=Approval Ref, TRNF_YN=Transfer Status, DOC_NO=Doc No., …)
-- with Figma-placeholder widths. The legacy JEX page pymn_0009_001 (queried
-- verbatim via JEX DEV `SYS_SCR_OPTION_WECMS` on 2026-05-30) renders 10
-- user columns + checkbox + ETC in this canonical order:
--
--   0.   CHECKBOX        Checkbox              40px   (fix_yn=Y, special)
--   1.   REG_DTM         Date/Time             105px
--   2.   FILE_NM         File Name             290px
--   3.   TOT_TRNS_CNT    No. of Transactions   150px
--   4.   TOTAL_TRAN_AMT  Total Transfer Amount 200px
--   5.   PAY_DATE        Scheduled Date        150px
--   6.   APPR_STTS       Approval Status       170px
--   7.   DRAFT_USER_NM   Requestor             100px
--   8.   APPRVR_NM       Approver              175px
--   9.   APPR_DOC_NO     Approval Ref.         150px
--   10.  TITL            Approval Title        250px
--   99.  ETC             (action col)          150px  (fix_yn=Y, special)
--
-- Refactor identifier naming — slugs, not WSVC ids (per workspace CLAUDE.md):
-- the FIELD_IDs drop the legacy `_2` suffix. The DISP_ORD values + widths +
-- sort_yn flags are what align with legacy.
--
-- Differences from V131:
--   • REG_DTM size 150 → 105
--   • TITL "File Name" → renamed to FILE_NM; new TITL row added for
--     "Approval Title" at disp_ord 10 / 250px
--   • TOT_TRNS_CNT size 140 → 150
--   • TOTAL_TRAN_AMT size 180 → 200
--   • APPR_STTS sort_yn 'N' → 'Y' (entity has SCDUL_PYMN_SUMR.APPR_STTS
--     since V120; sort enables once InternalTransferSortField gains the
--     constant — see follow-up Java enum update in this MR)
--   • REG_ID "Requestor" → renamed to DRAFT_USER_NM
--   • APPR_SEQ_NO "Approval Ref." → renamed to APPR_DOC_NO
--   • TRNF_YN (Transfer Status), DOC_NO (Doc No.) — removed (not in
--     legacy column set; the action column carries the dispatch state
--     instead)
--   • PAY_DATE — added (was rendered via the page's `extraColumn` slot
--     in the refactor; legacy treats it as a regular View Setting column)
--   • APPRVR_NM — added (was always-rendered without a FIELD_ID gate in
--     the refactor; legacy treats it as a regular View Setting column)
--   • ETC size 60 → 150
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes any obsolete rows from V131 first.
--   * INSERTs use ON CONFLICT DO NOTHING so a partially-applied retry
--     resolves cleanly.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — the lazy-init
--     branch in ScreenOptionService.load(...) re-copies the corrected
--     sys defaults on the user's next GET (same precedent as V32/V41/V42
--     /V207/V216).
-- ----------------------------------------------------------------------------

-- 1. Drop the V131 row set we no longer need so the new INSERTs can
--    repopulate from scratch. CHECKBOX + ETC are kept (admin-controlled
--    special rows); everything else is reseeded.
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'REG_DTM', 'TITL', 'TOT_TRNS_CNT', 'TOTAL_TRAN_AMT',
       'APPR_STTS', 'REG_ID', 'APPR_SEQ_NO', 'TRNF_YN', 'DOC_NO',
       'FILE_NM', 'PAY_DATE', 'DRAFT_USER_NM', 'APPRVR_NM', 'APPR_DOC_NO'
   );

-- 2. Insert the legacy row set with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-internal-transfer-scheduled', 'default', 'REG_DTM',        'Date/Time',             1,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'FILE_NM',        'File Name',             2,  'Y', 'N', 'Y', 'Y', '290px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'TOT_TRNS_CNT',   'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'PAY_DATE',       'Scheduled Date',        5,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'APPR_STTS',      'Approval Status',       6,  'Y', 'N', 'Y', 'Y', '170px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'DRAFT_USER_NM',  'Requestor',             7,  'Y', 'N', 'Y', 'Y', '100px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'APPRVR_NM',      'Approver',              8,  'Y', 'N', 'Y', 'Y', '175px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'APPR_DOC_NO',    'Approval Ref.',         9,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-internal-transfer-scheduled', 'default', 'TITL',           'Approval Title',        10, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Update CHECKBOX + ETC widths to match legacy (CHECKBOX 38→40px,
--    ETC 60→150px). Special FIELD_IDs are admin-controlled — only SIZE
--    is updated here, the FIX_YN / DISP_YN / USE_YN flags stay as the
--    V131 seed set them.
UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '40px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'CHECKBOX';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '150px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'ETC';

-- 4. Wipe USER_SCR_OPTION_WECMS overrides for the screen so the corrected
--    sys defaults take effect on every user's next GET. Same precedent as
--    V32/V41/V42/V207/V216 — R&D scope, no preservation of local
--    user-saved overrides.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled'
   AND DYM_AREA_ID = 'default';
