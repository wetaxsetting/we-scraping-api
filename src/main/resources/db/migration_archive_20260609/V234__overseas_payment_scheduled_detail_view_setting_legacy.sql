-- ----------------------------------------------------------------------------
-- V234 — Align Payment > Overseas Payment > Overseas Scheduled Payment >
--        Detail View Setting with the legacy pymn_0007_004 OSMC column
--        set, widths, and sort flags (2026-05-31)
--
-- V214 seeded the same 10 FIELD_IDs but with refactor-tailored widths
-- and SORT_YN='N' on every column (the original Sprint 1 scope had
-- no sort wiring). The legacy JEX page pymn_0007_004 (queried verbatim
-- via JEX DEV `SYS_SCR_OPTION_WECMS where web_svc_id = 'pymn_0007_004'`
-- on 2026-05-31) renders 10 user columns in this canonical order with
-- SORT_YN='Y' across the board:
--
--   1.   BNFC_NM         Beneficiary Name          180px
--   2.   BNFC_BANK_NM    Beneficiary Bank Name     220px
--   3.   BNFC_ACCT_NO    Beneficiary Account No.   150px
--   4.   BNFC_ADDR       Beneficiary Address       200px
--   5.   CHART_ACCT_CD   Reporting Account         200px
--   6.   TRNF_CCY_CD     Transfer Currency         120px
--   7.   TRNF_AMT        Transfer Amount           150px
--   8.   FEE_PAYER_CD    Fee Payer                 80px
--   9.   MEMO            Memo                      150px
--   10.  BANK_TRAN_ID    Banking Transaction ID    250px
--
-- Differences from V214:
--   • REORDERED: CHART_ACCT_CD moves from disp_ord 9 → 5; the
--     TRNF_CCY_CD / TRNF_AMT / FEE_PAYER_CD / MEMO block shifts
--     one slot right; BANK_TRAN_ID stays at 10.
--   • WIDTHS: legacy PROD values replace the V214 refactor widths.
--     Key deltas — BNFC_BANK_NM 200 → 220, BNFC_ACCT_NO 180 → 150,
--     BNFC_ADDR 220 → 200, CHART_ACCT_CD 220 → 200, TRNF_CCY_CD
--     140 → 120, TRNF_AMT 138 → 150, FEE_PAYER_CD 150 → 80, MEMO
--     200 → 150, BANK_TRAN_ID 175 → 250.
--   • SORT_YN: 'N' → 'Y' on every column (legacy default behaviour).
--
-- No CHECKBOX special row (the Overseas Scheduled Detail page has no
-- row-selection column) and no ETC action column — same shape as V214.
--
-- Companion frontend change wires the page through View Setting
-- (projects visible items → columns, DB-driven widths, enableSort)
-- and extends the shared `overseasScheduledDetailColumns` factory
-- with `sortable: true` + numeric `sortValue` for Transfer Amount.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V214 row set first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults.
-- ----------------------------------------------------------------------------

-- 1. Drop the V214 row set we no longer need.
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-overseas-payment-scheduled-detail'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'BNFC_NM', 'BNFC_BANK_NM', 'BNFC_ACCT_NO', 'BNFC_ADDR',
       'TRNF_CCY_CD', 'TRNF_AMT', 'FEE_PAYER_CD', 'MEMO',
       'CHART_ACCT_CD', 'BANK_TRAN_ID'
   );

-- 2. Insert the legacy 10-user-column row set with PROD widths +
--    SORT_YN='Y' on every column.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-overseas-payment-scheduled-detail', 'default', 'BNFC_NM',       'Beneficiary Name',         1, 'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'BNFC_BANK_NM',  'Beneficiary Bank Name',    2, 'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'BNFC_ACCT_NO',  'Beneficiary Account No.',  3, 'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'BNFC_ADDR',     'Beneficiary Address',      4, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'CHART_ACCT_CD', 'Reporting Account',        5, 'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'TRNF_CCY_CD',   'Transfer Currency',        6, 'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'TRNF_AMT',      'Transfer Amount',          7, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'FEE_PAYER_CD',  'Fee Payer',                8, 'Y', 'N', 'Y', 'Y', '100px',  'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'MEMO',          'Memo',                     9, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled-detail', 'default', 'BANK_TRAN_ID',  'Banking Transaction ID',  10, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-overseas-payment-scheduled-detail'
   AND DYM_AREA_ID = 'default';
