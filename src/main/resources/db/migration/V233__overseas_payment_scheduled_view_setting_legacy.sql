-- ----------------------------------------------------------------------------
-- V233 — Align Payment > Overseas Payment > Overseas Scheduled Payment
--        View Setting with the legacy pymn_0007_001 OSMC column set,
--        widths, and sort flags (2026-05-31)
--
-- The original V178 seed for `payment-overseas-payment-scheduled`
-- mirrored the V131-era batch-list FIELD_IDs (REG_DTM, TITL,
-- TOT_TRNS_CNT, TOTAL_TRAN_AMT, APPR_STTS, REG_ID, APPR_SEQ_NO).
-- The legacy JEX page pymn_0007_001 (queried verbatim via JEX DEV
-- `SYS_SCR_OPTION_WECMS where web_svc_id = 'pymn_0007_001'` on
-- 2026-05-31) renders a per-transaction view with 16 user columns
-- + CHECKBOX + ETC in this canonical order:
--
--   0.   CHECKBOX        (selection column)    40px   (fix_yn=Y, special)
--   1.   TRNF_DT         Transfer Date         120px
--   2.   DBT_ACCT_NO     Debit Account No.     155px
--   3.   DBT_CCY_CD      Debit Currency        155px
--   4.   BNFC_NM         Beneficiary           170px
--   5.   BNFC_BANK_NM    Beneficiary Bank Name 170px
--   6.   BNFC_ACCT_NO    Beneficiary Acct No.  210px
--   7.   BNFC_ADDR       Beneficiary Address   230px
--   8.   CHART_ACCT_CD   Reporting Account     240px
--   9.   TRNF_CCY_CD     Transfer Currency     155px
--   10.  TRNF_AMT        Transfer Amount       155px
--   11.  FEE_PAYER_CD    Fee Payer             155px
--   12.  MEMO            Memo/Description      155px
--   13.  APPR_STTS       Approval Status       155px
--   14.  USER_NM         Requestor             155px
--   15.  APPRVR_ID       Approver              190px
--   99.  ETC             (action slot)         130px  (fix_yn=Y, special)
--
-- SORT_YN policy (per the user's task spec — most columns sortable,
-- a few intentionally inert):
--   • BNFC_NM      SORT_YN='N' — "Beneficiary" is a free-text join
--                                column; legacy disables sort
--                                because the underlying field varies
--                                across the join.
--   • APPRVR_ID    SORT_YN='N' — multi-approver chain, sort would
--                                require an aggregate.
--   • CHECKBOX     SORT_YN='N' — special FIELD_ID; never sortable
--                                per CLAUDE.md §13a special-field rule.
--   • ETC          SORT_YN='N' — special FIELD_ID; action slot.
--   • Every other  SORT_YN='Y' — per user spec.
--
-- Architectural note: this Overseas Scheduled list page in the
-- refactor uses the shared `<PaymentScheduledBatchListPage>` shell
-- which renders a BATCH-LIST view (one row = one uploaded file
-- containing N transactions). The legacy pymn_0007_001 renders a
-- PER-TRANSACTION view (one row = one international wire). The seed
-- below makes the View Setting registry legacy-aligned so the
-- popover lists the canonical 16 FIELD_IDs; the current shell only
-- maps 6 of them to existing column slots (TRNF_DT → dateTime,
-- BNFC_NM → fileName, TRNF_AMT → totalAmount, APPR_STTS → status,
-- USER_NM → requestor, APPRVR_ID → approver). The remaining
-- per-transaction columns (DBT_ACCT_NO, DBT_CCY_CD, BNFC_BANK_NM,
-- BNFC_ACCT_NO, BNFC_ADDR, CHART_ACCT_CD, TRNF_CCY_CD, FEE_PAYER_CD,
-- MEMO) ride through the seed for popover visibility but do not
-- render in the current batch-list shell — a future feature MR
-- needs to either expand each batch row to N transaction rows or
-- switch the page to a per-transaction endpoint.
--
-- Differences from V178:
--   • REG_DTM         → renamed to TRNF_DT     (legacy Transfer Date)
--   • TITL "File Name" → removed (legacy uses BNFC_NM for the file-name slot)
--   • TOT_TRNS_CNT    → removed (legacy is per-transaction, no count column)
--   • TOTAL_TRAN_AMT  → renamed to TRNF_AMT
--   • REG_ID          → renamed to USER_NM
--   • APPR_SEQ_NO     → removed (legacy has no Approval Ref column)
--   • ADDED:           CHECKBOX, DBT_ACCT_NO, DBT_CCY_CD, BNFC_NM,
--                      BNFC_BANK_NM, BNFC_ACCT_NO, BNFC_ADDR,
--                      CHART_ACCT_CD, TRNF_CCY_CD, FEE_PAYER_CD,
--                      MEMO, APPRVR_ID, ETC.
--   • Widths replaced with legacy PROD values.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V178 / V179 row set first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults.
-- ----------------------------------------------------------------------------

-- 1. Drop the V178 / V179 row set we no longer need.
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-overseas-payment-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'REG_DTM', 'TITL', 'TOT_TRNS_CNT', 'TOTAL_TRAN_AMT',
       'APPR_STTS', 'REG_ID', 'APPR_SEQ_NO',
       'CHECKBOX', 'TRNF_DT', 'DBT_ACCT_NO', 'DBT_CCY_CD',
       'BNFC_NM', 'BNFC_BANK_NM', 'BNFC_ACCT_NO', 'BNFC_ADDR',
       'CHART_ACCT_CD', 'TRNF_CCY_CD', 'TRNF_AMT', 'FEE_PAYER_CD',
       'MEMO', 'USER_NM', 'APPRVR_ID', 'ETC'
   );

-- 2. Insert the legacy 16-user-column row set + CHECKBOX + ETC
--    with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-overseas-payment-scheduled', 'default', 'CHECKBOX',      '',                       0, 'Y', 'Y', 'Y', 'N', '40px',  'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'TRNF_DT',       'Transfer Date',          1, 'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'DBT_ACCT_NO',   'Debit Account No.',      2, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'DBT_CCY_CD',    'Debit Currency',         3, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'BNFC_NM',       'Beneficiary',            4, 'Y', 'N', 'Y', 'N', '170px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'BNFC_BANK_NM',  'Beneficiary Bank Name',  5, 'Y', 'N', 'Y', 'Y', '170px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'BNFC_ACCT_NO',  'Beneficiary Account Number', 6, 'Y', 'N', 'Y', 'Y', '210px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'BNFC_ADDR',     'Beneficiary Address',    7, 'Y', 'N', 'Y', 'Y', '230px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'CHART_ACCT_CD', 'Reporting Account',      8, 'Y', 'N', 'Y', 'Y', '240px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'TRNF_CCY_CD',   'Transfer Currency',      9, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'TRNF_AMT',      'Transfer Amount',       10, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'FEE_PAYER_CD',  'Fee Payer',             11, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'MEMO',          'Memo/Description',      12, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'APPR_STTS',     'Approval Status',       13, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'USER_NM',       'Requestor',             14, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'APPRVR_ID',     'Approver',              15, 'Y', 'N', 'Y', 'N', '190px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-scheduled', 'default', 'ETC',           '',                      99, 'Y', 'Y', 'Y', 'N', '130px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-overseas-payment-scheduled'
   AND DYM_AREA_ID = 'default';
