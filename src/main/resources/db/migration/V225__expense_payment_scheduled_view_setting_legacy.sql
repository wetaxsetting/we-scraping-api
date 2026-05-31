-- ----------------------------------------------------------------------------
-- V225 — Align Payment > Expense Payment > Expense Scheduled Payment
--        View Setting with the legacy pymn_0003_001 OSMC column set,
--        widths, and sort flags (2026-05-30)
--
-- The original V143 seed mirrored V131's `payment-internal-transfer-scheduled`
-- shape (TITL=File Name, REG_ID=Requestor, APPR_SEQ_NO=Approval Ref,
-- TRNF_YN=Transfer Status, DOC_NO=Doc No., …) with Figma-placeholder
-- widths. The legacy JEX page pymn_0003_001 (queried verbatim via JEX
-- DEV `SYS_SCR_OPTION_WECMS` on 2026-05-30) renders 10 user columns +
-- checkbox + ETC in this canonical order:
--
--   0.   CHECKBOX        Checkbox              40px   (fix_yn=Y, special, never sortable)
--   1.   REG_DTM         Date/Time             105px
--   2.   FILE_NM         File Name             290px
--   3.   TOT_TRNS_CNT    No. of Transactions   180px
--   4.   TOTAL_TRAN_AMT  Total Transfer Amount 200px
--   5.   PAY_DATE        Scheduled Date        150px
--   6.   APPR_STTS       Approval Status       155px
--   7.   DRAFT_USER_NM   Requestor             150px
--   8.   APPRVR_NM       Approver              175px
--   9.   APPR_DOC_NO     Approval Ref.         160px
--   10.  TITL            Approval Title        250px
--   99.  ETC             (action col)          150px  (fix_yn=Y, special)
--
-- Same shape as V217 / V221 (Internal Transfer + Vendor Scheduled).
-- Expense, Vendor, and Internal Transfer share `SCDUL_PYMN_SUMR` via the
-- type discriminator (`SCDUL_PYMN_TYPE_CD = '02'` for Expense). Width
-- deltas vs V221 (Vendor) per the PROD pymn_0003_001 dump:
--   • TOT_TRNS_CNT  150 → 180px
--   • APPR_STTS     150 → 155px
--   • DRAFT_USER_NM 100 → 150px
--
-- CHECKBOX special FIELD_ID intentionally retains `sort_yn='N'` (admin-
-- controlled per `ScreenOptionService.SPECIAL_FIELD_IDS` — checkbox
-- columns are never sortable in the refactor, mirroring legacy where the
-- selection column is the row affordance, not a sort target).
--
-- Differences from V143:
--   • REG_DTM size 150 → 105
--   • TITL "File Name" → renamed to FILE_NM; new TITL row added for
--     "Approval Title" at disp_ord 10 / 250px
--   • TOT_TRNS_CNT size 140 → 180
--   • TOTAL_TRAN_AMT size 180 → 200
--   • APPR_STTS sort_yn 'N' → 'Y', size 140 → 155
--   • REG_ID "Requestor" → renamed to DRAFT_USER_NM, size 140 → 150
--   • APPR_SEQ_NO "Approval Ref." → renamed to APPR_DOC_NO, size 140 → 160
--   • TRNF_YN (Transfer Status), DOC_NO (Doc No.) — removed (not in
--     legacy column set; the action column carries dispatch state).
--   • PAY_DATE — added.
--   • APPRVR_NM — added.
--   • CHECKBOX size 38 → 40.
--   • ETC size 60 → 150.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes any obsolete rows from V143 first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults (same
--     precedent as V32/V41/V42/V207/V216/V217/V218/V219/V220/V221/V222
--     /V223/V224).
-- ----------------------------------------------------------------------------

-- 1. Drop the V143 row set we no longer need (CHECKBOX + ETC stay).
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-expense-payment-scheduled'
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
    ('payment-expense-payment-scheduled', 'default', 'REG_DTM',        'Date/Time',             1,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'FILE_NM',        'File Name',             2,  'Y', 'N', 'Y', 'Y', '290px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'TOT_TRNS_CNT',   'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'PAY_DATE',       'Scheduled Date',        5,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'APPR_STTS',      'Approval Status',       6,  'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'DRAFT_USER_NM',  'Requestor',             7,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'APPRVR_NM',      'Approver',              8,  'Y', 'N', 'Y', 'Y', '175px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'APPR_DOC_NO',    'Approval Ref.',         9,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-scheduled', 'default', 'TITL',           'Approval Title',        10, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Update CHECKBOX + ETC widths to match legacy (CHECKBOX 38→40px,
--    ETC 60→150px). Special FIELD_IDs are admin-controlled — only SIZE
--    is updated here, the FIX_YN / DISP_YN / USE_YN / SORT_YN flags stay
--    as the V143 seed set them (CHECKBOX `sort_yn='N'` is preserved per
--    the SPECIAL_FIELD_IDS contract — checkbox columns are never
--    sortable).
UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '40px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-expense-payment-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'CHECKBOX';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '150px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-expense-payment-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'ETC';

-- 4. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-expense-payment-scheduled'
   AND DYM_AREA_ID = 'default';
