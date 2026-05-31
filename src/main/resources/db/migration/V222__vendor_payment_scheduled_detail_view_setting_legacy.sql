-- ----------------------------------------------------------------------------
-- V222 — Align Payment > Vendor Payment > Scheduled Payment > Detail
--        View Setting with the legacy pymn_0001_003 OSMC column set,
--        widths, and labels (2026-05-30)
--
-- The original V142 seed carried a refactor-tailored set: a Line-No-style
-- FINC_MDA_NO (Account No.) column + swapped Remark / Description labels
-- (NOTE was labelled "Remark", TRAN_USAG was labelled "Description" —
-- reversed from legacy) + no Banking Transaction ID column. The frontend
-- View Setting popover persists user prefs against the seed but the table
-- rendered a static 8-column factory output.
--
-- A companion frontend change makes the page registry-driven: projects
-- View Setting items → table columns, overriding widths from
-- `sys_scr_option_wecms.size` and fixing the remark / description field
-- mapping to match legacy.
--
-- Legacy PROD column set (queried verbatim from JEX DEV `SYS_SCR_OPTION_WECMS`
-- on 2026-05-30, FIELD_IDs translated to refactor slugs):
--
--   1.   TAR_BANK_CD     Deposit Bank             200px
--   2.   RCPT_NM         Recipient Name           350px
--   3.   TRAN_AMT        Transfer Amount          150px
--   4.   TRAN_USAG       Remarks                  300px
--   5.   CHART_ACCT_CD   Reporting Account        200px
--   6.   NOTE            Description              200px
--   7.   DOC_NO          Document No.             200px
--   8.   BANK_TRAN_ID    Banking Transaction ID   250px
--   99.  ETC             (action slot)            60px   (fix_yn=Y, special)
--
-- Same column set as V218 for Internal Transfer Scheduled Detail with
-- different per-column widths (Vendor: 200/350/150/300/200/200/200/250 vs
-- Internal: 170/300/110/300/200/200/150/250 px).
--
-- Differences from V142:
--   • REMOVED:  FINC_MDA_NO (Account No. — not in legacy column set).
--   • ADDED:    BANK_TRAN_ID (Banking Transaction ID).
--   • RELABELED:
--               NOTE       "Remark" → "Description"  (swap with TRAN_USAG)
--               TRAN_USAG  "Description" → "Remarks" (swap with NOTE)
--               CHART_ACCT_CD "Reporting Acct" → "Reporting Account"
--               DOC_NO     "Doc No." → "Document No."
--   • REORDERED: every disp_ord aligned with the legacy column order
--                (Reporting Account moves from #6 to #5; Description
--                from #7 to #6; Document No. from #8 to #7).
--   • Widths replaced with legacy PROD values.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V142 row set first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults (same
--     precedent as V32/V41/V42/V207/V216/V217/V218/V219/V220/V221).
-- ----------------------------------------------------------------------------

-- 1. Drop the V142 row set we no longer need (ETC stays).
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-vendor-payment-scheduled-detail'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'TAR_BANK_CD', 'RCPT_NM', 'FINC_MDA_NO', 'TRAN_AMT',
       'NOTE', 'CHART_ACCT_CD', 'TRAN_USAG', 'DOC_NO',
       'BANK_TRAN_ID'
   );

-- 2. Insert the legacy row set with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-vendor-payment-scheduled-detail', 'default', 'TAR_BANK_CD',   'Deposit Bank',           1, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'RCPT_NM',       'Recipient Name',         2, 'Y', 'N', 'Y', 'Y', '350px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'TRAN_AMT',      'Transfer Amount',        3, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'TRAN_USAG',     'Remarks',                4, 'Y', 'N', 'Y', 'Y', '300px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'CHART_ACCT_CD', 'Reporting Account',      5, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'NOTE',          'Description',            6, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'DOC_NO',        'Document No.',           7, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-vendor-payment-scheduled-detail', 'default', 'BANK_TRAN_ID',  'Banking Transaction ID', 8, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. ETC stays at V142's 60px (no PROD value listed for this screen);
--    keep V142's FIX_YN / DISP_YN / USE_YN flags. No UPDATE needed.

-- 4. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-vendor-payment-scheduled-detail'
   AND DYM_AREA_ID = 'default';
