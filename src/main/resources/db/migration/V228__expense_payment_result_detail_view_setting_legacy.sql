-- ----------------------------------------------------------------------------
-- V228 — Align Payment > Expense Payment > Payment Result > Detail
--        View Setting with the legacy pymn_0004_002 OSMC column set,
--        widths, and labels (2026-05-31)
--
-- The original V143 seed for `payment-expense-payment-result-detail`
-- carried an early-Sprint refactor set: SCDUL_PYMN_DETL_NO (Line No.) +
-- PROC_STAT (Results) + BANK_TRNS_SHORT_ID (Banking Txn ID) — three
-- columns that don't match the legacy shape — paired with swapped
-- labels (NOTE was labelled "Remarks" and TRAN_USAG was labelled
-- "Description", reversed from legacy).
--
-- A companion frontend change wires the View Setting popover that was
-- previously not driving the table column set, projects View Setting
-- items → table columns, and fixes the remark / description row
-- mapping to match legacy.
--
-- Legacy PROD column set (mirrors V224 Vendor Result Detail; same
-- legacy WSVC labels and widths as `pymn_0002_002` since the two
-- legacy pages render the identical inner table):
--
--   1.   PROC_DTTM       Date/Time                105px
--   2.   TAR_BANK_CD     Deposit Bank             300px
--   3.   RCPT_NM         Recipient Name           350px
--   4.   TRAN_AMT        Transfer Amount          150px
--   5.   TRAN_USAG       Remarks                  250px
--   6.   RS_CD           Results                  100px
--   7.   RSLT_DTTM       Result Date/Time         130px
--   8.   CHART_ACCT_CD   Reporting Account        150px
--   9.   NOTE            Description              200px
--   10.  DOC_NO          Document No.             200px
--   11.  BANK_TRAN_ID    Banking Transaction ID   250px
--   99.  ETC             (action slot)            100px  (fix_yn=Y, special)
--
-- Identical to V224 — every Payment Result Detail page across modules
-- (Internal Transfer V220, Vendor V224, Expense V228) shares the same
-- 11-FIELD_ID set; per-module width deltas live in their own seeds.
-- Internal Transfer differs in three widths (Deposit Bank 260 vs 300,
-- Remarks 350 vs 250, Document No. 150 vs 200); Vendor and Expense are
-- identical.
--
-- Differences from V143:
--   • REMOVED:  SCDUL_PYMN_DETL_NO (Line No. — not in legacy),
--               PROC_STAT (replaced by RS_CD for the Results column),
--               BANK_TRNS_SHORT_ID (replaced by BANK_TRAN_ID).
--   • ADDED:    RS_CD (Results), RSLT_DTTM (Result Date/Time),
--               BANK_TRAN_ID (Banking Transaction ID).
--   • RELABELED:
--               PROC_DTTM "Result Date / Time" → "Date/Time" (carries
--                         the row's request-processing time)
--               CHART_ACCT_CD "Reporting Acct" → "Reporting Account"
--               TRAN_USAG "Description" → "Remarks" (swap with NOTE)
--               NOTE "Remarks" → "Description" (swap with TRAN_USAG)
--   • REORDERED: every disp_ord aligned with the legacy column order.
--   • Widths replaced with legacy PROD values.
--   • ETC size 60 → 100.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V143 row set first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * UPDATE on ETC sets the same value the legacy seed has.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults (same
--     precedent as V32/V41/V42/V207/V216/V217/V218/V219/V220/V221/V222
--     /V223/V224/V225/V226/V227).
-- ----------------------------------------------------------------------------

-- 1. Drop the V143 row set we no longer need (ETC stays).
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-expense-payment-result-detail'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'SCDUL_PYMN_DETL_NO', 'PROC_DTTM', 'TAR_BANK_CD', 'RCPT_NM',
       'TRAN_AMT', 'TRAN_USAG', 'PROC_STAT', 'CHART_ACCT_CD', 'NOTE',
       'DOC_NO', 'BANK_TRNS_SHORT_ID',
       'RS_CD', 'RSLT_DTTM', 'BANK_TRAN_ID'
   );

-- 2. Insert the legacy row set with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-expense-payment-result-detail', 'default', 'PROC_DTTM',     'Date/Time',              1, 'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'TAR_BANK_CD',   'Deposit Bank',           2, 'Y', 'N', 'Y', 'Y', '300px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'RCPT_NM',       'Recipient Name',         3, 'Y', 'N', 'Y', 'Y', '350px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'TRAN_AMT',      'Transfer Amount',        4, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'TRAN_USAG',     'Remarks',                5, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'RS_CD',         'Results',                6, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'RSLT_DTTM',     'Result Date/Time',       7, 'Y', 'N', 'Y', 'Y', '130px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'CHART_ACCT_CD', 'Reporting Account',      8, 'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'NOTE',          'Description',            9, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'DOC_NO',        'Document No.',          10, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result-detail', 'default', 'BANK_TRAN_ID',  'Banking Transaction ID',11, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Update ETC width to legacy 100px. Special FIELD_ID — only SIZE
--    is updated here, FIX_YN / USE_YN / DISP_YN stay as V143 set them.
UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '100px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-expense-payment-result-detail'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'ETC';

-- 4. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-expense-payment-result-detail'
   AND DYM_AREA_ID = 'default';
