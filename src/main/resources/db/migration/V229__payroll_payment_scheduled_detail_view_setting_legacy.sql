-- ----------------------------------------------------------------------------
-- V229 — Align Payment > Payroll > Payroll Scheduled Payment > Detail
--        View Setting with the legacy pymn_0005_003 OSMC column set,
--        widths, and labels (2026-05-31)
--
-- The original V144 seed for `payment-payroll-payment-scheduled-detail`
-- carried a refactor-tailored set: a `FINC_MDA_NO` (Account No.) column
-- not in the legacy view + swapped Remark / Description labels (NOTE
-- carried "Remark" in some refactor flows, TRAN_USAG was labelled
-- "Description" — reversed from legacy `pymn_0005_003` mapping where
-- TRAN_USAG IS the Remark column and NOTE / DOC_NO / BANK_TRAN_ID are
-- not rendered at all on Payroll). V144's widths also do not match PROD.
--
-- A companion frontend change makes the page registry-driven, projects
-- View Setting items → table columns, overrides widths from
-- `sys_scr_option_wecms.size`, and fixes the row mapper to swap the
-- remark / description field assignments.
--
-- Legacy PROD column set (queried verbatim from JEX DEV
-- `SYS_SCR_OPTION_WECMS where web_svc_id = 'pymn_0005_003'` on
-- 2026-05-31, FIELD_IDs translated to refactor slugs):
--
--   1.   TAR_BANK_CD     Deposit Bank           180px
--   2.   RCPT_NM         Recipient Name         360px
--   3.   TRAN_AMT        Transfer Amount        110px
--   4.   TRAN_USAG       Remarks                360px
--   5.   CHART_ACCT_CD   Reporting Account      150px
--   99.  ETC             (action slot)          60px   (fix_yn=Y, special)
--
-- Five-user-column layout, narrower than Vendor / Expense Scheduled
-- Detail (Payroll legacy does not render Description / Document No. /
-- Banking Transaction ID — those rows are wired to the master batch
-- only, not the per-payee line). NO NOTE, NO DOC_NO, NO BANK_TRAN_ID
-- columns in this seed.
--
-- Width deltas vs V222 / V226 (Vendor / Expense Scheduled Detail):
--   • TAR_BANK_CD   200 → 180 (Payroll narrower)
--   • RCPT_NM       350 → 360 (Payroll wider)
--   • TRAN_AMT      150 → 110 (Payroll narrower — fixed-format payroll
--                              amounts rarely exceed 8 digits)
--   • TRAN_USAG     300 → 360 (Payroll wider — Remarks is the payee's
--                              salary period descriptor on the JEX
--                              dump, often longer than vendor/expense
--                              wire transfer memos)
--   • CHART_ACCT_CD 200 → 150 (Payroll narrower)
--
-- Differences from V144:
--   • REMOVED:  FINC_MDA_NO (Account No. — not in legacy column set).
--   • RELABELED:
--               TRAN_USAG     "Remark"         → "Remarks"
--               CHART_ACCT_CD "Reporting Acct" → "Reporting Account"
--   • REORDERED: every disp_ord aligned with the legacy column order
--                (CHART_ACCT_CD moves from #6 to #5, TRAN_AMT from #4
--                to #3, FINC_MDA_NO removed entirely).
--   • Widths replaced with legacy PROD values.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V144 row set first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults (same
--     precedent as V32/V41/V42/V207/V216–V228).
-- ----------------------------------------------------------------------------

-- 1. Drop the V144 row set we no longer need (ETC stays).
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-payroll-payment-scheduled-detail'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'TAR_BANK_CD', 'RCPT_NM', 'FINC_MDA_NO', 'TRAN_AMT',
       'TRAN_USAG', 'CHART_ACCT_CD'
   );

-- 2. Insert the legacy 5-user-column row set with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-payroll-payment-scheduled-detail', 'default', 'TAR_BANK_CD',   'Deposit Bank',      1, 'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'RCPT_NM',       'Recipient Name',    2, 'Y', 'N', 'Y', 'Y', '360px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'TRAN_AMT',      'Transfer Amount',   3, 'Y', 'N', 'Y', 'Y', '130px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'TRAN_USAG',     'Remarks',           4, 'Y', 'N', 'Y', 'Y', '360px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled-detail', 'default', 'CHART_ACCT_CD', 'Reporting Account', 5, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. ETC stays at V144's 60px / FIX_YN='Y' / USE_YN='Y' / DISP_YN='N' /
--    SORT_YN='N'. No UPDATE needed — the legacy dump does not list ETC
--    for this screen (action slot is implicit on the row).

-- 4. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-payroll-payment-scheduled-detail'
   AND DYM_AREA_ID = 'default';
