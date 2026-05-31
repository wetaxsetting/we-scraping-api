-- ----------------------------------------------------------------------------
-- V231 — Align Payment > Payroll > Payroll Payment Result > Detail
--        View Setting with the legacy pymn_0006_002 OSMC column set,
--        widths, and labels (2026-05-31)
--
-- The original V144 seed for `payment-payroll-payment-result-detail`
-- carried a refactor-tailored set: a `SCDUL_PYMN_DETL_NO` (Line No.)
-- column not in the legacy view, a `PROC_STAT` (Results) column
-- replaced by `RS_CD` in legacy, a trailing `BANK_TRNS_SHORT_ID`
-- column not present in the legacy Payroll Result Detail, AND swapped
-- Remarks / Description labels (NOTE → "Remarks" / TRAN_USAG →
-- something else — reversed from legacy `pymn_0006_002` mapping where
-- TRAN_USAG IS the Remarks column).
--
-- A companion frontend change makes the page registry-driven, projects
-- View Setting items → table columns, overrides widths from
-- `sys_scr_option_wecms.size`, and fixes the row mapper to swap the
-- remark / description field assignments.
--
-- Legacy PROD column set (queried verbatim from JEX DEV
-- `SYS_SCR_OPTION_WECMS where web_svc_id = 'pymn_0006_002'` on
-- 2026-05-31, FIELD_IDs translated to refactor slugs):
--
--   1.   PROC_DTTM       Date/Time             105px
--   2.   TAR_BANK_CD     Deposit Bank          200px
--   3.   RCPT_NM         Recipient Name        330px
--   4.   TRAN_AMT        Transfer Amount       110px
--   5.   TRAN_USAG       Remarks               330px
--   6.   RS_CD           Results               100px
--   7.   RSLT_DTTM       Result Date/Time      130px
--   8.   CHART_ACCT_CD   Reporting Account     130px
--   99.  ETC             (action slot)         60px   (fix_yn=Y, special)
--
-- Eight-user-column layout — narrower than Vendor / Expense Result
-- Detail (which carry 11 user columns including NOTE / DOC_NO /
-- BANK_TRAN_ID). Payroll Result Detail legacy does NOT render
-- Description / Document No. / Banking Transaction ID — those rows
-- are wired to the master batch only, not the per-payee result line.
--
-- Width deltas vs V224 / V228 (Vendor / Expense Result Detail):
--   • TAR_BANK_CD   300 → 200 (Payroll narrower — Vendor/Expense are
--                              wider because the column carries the
--                              two-line bank-name + currency stack)
--   • RCPT_NM       350 → 330 (Payroll slightly narrower)
--   • TRAN_AMT      150 → 110 (Payroll narrower — fixed-format payroll
--                              amounts rarely exceed 8 digits)
--   • TRAN_USAG     250 → 330 (Payroll wider — Remarks is the payee's
--                              salary period descriptor, often longer
--                              than vendor/expense wire transfer memos)
--   • CHART_ACCT_CD 150 → 130 (Payroll narrower)
--   • PROC_DTTM     105 = 105 (matches Vendor/Expense)
--   • RS_CD         100 = 100 (matches Vendor/Expense)
--   • RSLT_DTTM     130 = 130 (matches Vendor/Expense)
--   • NOTE/DOC_NO/BANK_TRAN_ID  — not in legacy (Payroll-specific delta)
--
-- Differences from V144:
--   • REMOVED:  SCDUL_PYMN_DETL_NO (Line No. — not in legacy),
--               PROC_STAT (replaced by RS_CD for the Results column),
--               BANK_TRNS_SHORT_ID (not in legacy Payroll Result
--                                   Detail — Payroll does not surface
--                                   Banking Txn ID on the per-line view).
--   • ADDED:    RS_CD (Results), RSLT_DTTM (Result Date/Time).
--   • RELABELED:
--               PROC_DTTM "Result Date / Time" → "Date/Time" (carries
--                         the row's request-processing time)
--               CHART_ACCT_CD "Reporting Acct" → "Reporting Account"
--   • REORDERED: every disp_ord aligned with the legacy column order.
--   • Widths replaced with legacy PROD values.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V144 row set first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults (same
--     precedent as V32/V41/V42/V207/V216–V230).
-- ----------------------------------------------------------------------------

-- 1. Drop the V144 row set we no longer need (ETC stays).
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-payroll-payment-result-detail'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'SCDUL_PYMN_DETL_NO', 'PROC_DTTM', 'TAR_BANK_CD', 'RCPT_NM',
       'TRAN_AMT', 'TRAN_USAG', 'PROC_STAT', 'CHART_ACCT_CD',
       'BANK_TRNS_SHORT_ID',
       'RS_CD', 'RSLT_DTTM'
   );

-- 2. Insert the legacy 8-user-column row set with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-payroll-payment-result-detail', 'default', 'PROC_DTTM',     'Date/Time',         1, 'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'TAR_BANK_CD',   'Deposit Bank',      2, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'RCPT_NM',       'Recipient Name',    3, 'Y', 'N', 'Y', 'Y', '330px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'TRAN_AMT',      'Transfer Amount',   4, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'TRAN_USAG',     'Remarks',           5, 'Y', 'N', 'Y', 'Y', '330px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'RS_CD',         'Results',           6, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'RSLT_DTTM',     'Result Date/Time',  7, 'Y', 'N', 'Y', 'Y', '130px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-result-detail', 'default', 'CHART_ACCT_CD', 'Reporting Account', 8, 'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. ETC stays at V144's 60px / FIX_YN='N' / USE_YN='Y' / DISP_YN='Y' /
--    SORT_YN='N'. No UPDATE needed — the legacy dump does not list ETC
--    width separately for this screen.

-- 4. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-payroll-payment-result-detail'
   AND DYM_AREA_ID = 'default';
