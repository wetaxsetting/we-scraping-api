-- ----------------------------------------------------------------------------
-- V236 — Align Payment > Overseas Payment > Overseas Payment Result >
--        Detail View Setting with the legacy pymn_0008_002 OSMC column
--        set, widths, and sort flags (2026-05-31)
--
-- V163 seeded 11 user columns with refactor-tailored FIELD_IDs (TRN_SEQ,
-- BNFC_NM, BNFC_BANK_NM, BNFC_ACCT_NO, BNFC_ADDR, CCY_CD, TRNF_AMT,
-- FEE_PAYER_CD, MEMO, RPRT_ACCT_CD, TRNF_YN) and Figma-derived widths,
-- all SORT_YN='N'. The legacy JEX page pymn_0008_002 (queried verbatim
-- via JEX DEV `SYS_SCR_OPTION_WECMS where web_svc_id = 'pymn_0008_002'`
-- on 2026-05-31) renders 13 user columns + ETC in this canonical order:
--
--   1.   PAY_TRAN_SEQ    Transaction Seq         150px
--   2.   BNFC_NM         Beneficiary Name        220px
--   3.   BNFC_BANK_NM    Beneficiary Bank Name   225px
--   4.   BNFC_ACCT_NO    Beneficiary Account No. 200px
--   5.   BNFC_ADDR       Beneficiary Address     300px
--   6.   TRNF_CCY_CD     Transfer Currency       150px
--   7.   TRNF_AMT        Transfer Amount         160px
--   8.   FEE_PAYER       Fee Payer               120px
--   9.   MEMO            Memo                    300px
--   10.  CHART_ACCT_CD   Reporting Account       300px
--   11.  PROC_STAT       Status                  100px
--   12.  RSLT_DTTM       Result Date/Time        130px
--   13.  BANK_TRAN_ID    Banking Transaction ID  250px
--   99.  ETC             (action slot)           100px  (fix_yn=Y, special)
--
-- SORT_YN policy (per the user's task spec):
--   • FEE_PAYER  SORT_YN='N' — legacy disables sort on the fee-payer
--                              code column.
--   • ETC        SORT_YN='N' — special FIELD_ID; action slot.
--   • Every other SORT_YN='Y' — 12 user columns sortable.
--
-- FIELD_ID renames vs V163:
--   • TRN_SEQ      → renamed to PAY_TRAN_SEQ  (legacy column name)
--   • CCY_CD       → renamed to TRNF_CCY_CD   (legacy "transfer currency")
--   • FEE_PAYER_CD → renamed to FEE_PAYER     (legacy column name)
--   • RPRT_ACCT_CD → renamed to CHART_ACCT_CD (consistent with Overseas
--                                              Scheduled Detail V234)
--   • TRNF_YN      → renamed to PROC_STAT     (legacy "process status")
--
-- Added FIELD_IDs:
--   • RSLT_DTTM     — Result Date/Time (130px)
--   • BANK_TRAN_ID  — Banking Transaction ID (250px)
--   • ETC           — action slot (100px, FIX_YN='Y')
--
-- All 13 columns + ETC live in the seed. Widths replaced with legacy
-- PROD values; SORT_YN flipped from 'N' across the board to 'Y' on 12
-- user columns ('N' only on FEE_PAYER + ETC).
--
-- Companion frontend change extends the shared
-- `overseasResultDetailColumns` factory with `sortable: true` +
-- typed `sortValue` on the 12 sortable columns (numeric for
-- TRNF_AMT / PAY_TRAN_SEQ, string for the rest), and rewires the
-- Overseas Result Detail page to project visible View Setting items →
-- columns with DB-driven widths + dynamicMinWidth + enableSort.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V163 + any pre-existing V236 row set first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults.
-- ----------------------------------------------------------------------------

-- 1. Drop the V163 row set we no longer need (and any pre-existing
--    V236 rows from a re-run).
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-overseas-payment-result-detail'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'TRN_SEQ', 'BNFC_NM', 'BNFC_BANK_NM', 'BNFC_ACCT_NO',
       'BNFC_ADDR', 'CCY_CD', 'TRNF_AMT', 'FEE_PAYER_CD', 'MEMO',
       'RPRT_ACCT_CD', 'TRNF_YN',
       'PAY_TRAN_SEQ', 'TRNF_CCY_CD', 'FEE_PAYER', 'CHART_ACCT_CD',
       'PROC_STAT', 'RSLT_DTTM', 'BANK_TRAN_ID', 'ETC'
   );

-- 2. Insert the legacy 13-user-column row set + ETC with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-overseas-payment-result-detail', 'default', 'PAY_TRAN_SEQ',  'Transaction Seq',        1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_NM',       'Beneficiary Name',       2,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_BANK_NM',  'Beneficiary Bank Name',  3,  'Y', 'N', 'Y', 'Y', '225px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_ACCT_NO',  'Beneficiary Account No.',4,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BNFC_ADDR',     'Beneficiary Address',    5,  'Y', 'N', 'Y', 'Y', '300px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'TRNF_CCY_CD',   'Transfer Currency',      6,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'TRNF_AMT',      'Transfer Amount',        7,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'FEE_PAYER',     'Fee Payer',              8,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'MEMO',          'Memo',                   9,  'Y', 'N', 'Y', 'Y', '300px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'CHART_ACCT_CD', 'Reporting Account',      10, 'Y', 'N', 'Y', 'Y', '300px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'PROC_STAT',     'Status',                 11, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'RSLT_DTTM',     'Result Date/Time',       12, 'Y', 'N', 'Y', 'Y', '130px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'BANK_TRAN_ID',  'Banking Transaction ID', 13, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result-detail', 'default', 'ETC',           '',                       99, 'Y', 'Y', 'Y', 'N', '100px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-overseas-payment-result-detail'
   AND DYM_AREA_ID = 'default';
