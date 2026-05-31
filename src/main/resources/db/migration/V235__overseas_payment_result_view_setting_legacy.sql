-- ----------------------------------------------------------------------------
-- V235 — Align Payment > Overseas Payment > Overseas Payment Result View
--        Setting with the legacy pymn_0008_001 OSMC column set, widths,
--        and sort flags (2026-05-31)
--
-- The original V177 seed for `payment-overseas-payment-result` mirrored
-- V131-era refactor FIELD_IDs (REG_DTM, ORG_FILE_NM, TOT_TRNS_CNT,
-- TOTAL_TRAN_AMT, PAY_DATE, APPR_STTS, DRAFT_USER_NM, APPRVR,
-- APPR_SEQ_NO, TITL) with Figma-placeholder widths and a refactor-only
-- extras block (Scheduled Date, Approval Status, Requestor, Approver).
-- The legacy JEX page pymn_0008_001 (queried verbatim via JEX DEV
-- `SYS_SCR_OPTION_WECMS where web_svc_id = 'pymn_0008_001' AND
-- dym_area_id = 'table2'` on 2026-05-31) renders 7 user columns + ETC
-- in this canonical order:
--
--   1.   TRAN_DT_2             Date/Time              105px
--   2.   FILE_NM_2             File Name              290px
--   3.   SCDUL_PYMN_DETL_NO_2  No. of Transactions    155px
--   4.   TRAN_AMT_2            Total Transfer Amount  235px
--   5.   PROC_STAT_2           Transfer Status        190px
--   6.   APPR_DOC_NO_2         Approval Ref.          160px
--   7.   TITL_2                Approval Title         250px
--   99.  ETC_2                 (action slot)          50px   (fix_yn=Y, special)
--
-- Same column set as V219 (Internal Transfer Result) + V223 (Vendor
-- Result) + V227 (Expense Result) + V230 (Payroll Result) — Overseas
-- Result uses the identical 7-column + ETC shape.
--
-- Legacy FIELD_ID + DYM_AREA_ID translation:
--   • Legacy `dym_area_id = 'table2'` → refactor `'default'`. The
--     legacy `pymn_0008_001` schema carries two areas (`table1` for the
--     filter / search panel; `table2` for the result table). The
--     refactor's Overseas Result page renders a single result table, so
--     it collapses to the single-area `'default'` convention. CLAUDE.md
--     §"View Setting screenId convention" forbids the legacy numeric
--     area names (`table1`/`table2`); refactor uses meaningful kebab
--     names or `'default'` for single-area pages.
--   • Legacy FIELD_ID suffix `_2` (e.g. `TRAN_DT_2`) → refactor strips
--     the suffix (e.g. `TRAN_DT`). The `_2` suffix disambiguated against
--     table1's `TRAN_DT` (which lives on a different area row). With
--     the refactor collapsing to one area, the suffix is unnecessary;
--     the unsuffixed FIELD_IDs also match the V219 / V223 / V227 / V230
--     established refactor convention.
--
-- Differences from V177:
--   • REG_DTM         → renamed to TRAN_DT     (legacy Date/Time)
--   • ORG_FILE_NM     → renamed to FILE_NM     (legacy File Name)
--   • TOT_TRNS_CNT    → renamed to SCDUL_PYMN_DETL_NO (legacy count name)
--   • TOTAL_TRAN_AMT  → renamed to TRAN_AMT
--   • APPR_STTS       → renamed to PROC_STAT (legacy "process status")
--   • APPR_SEQ_NO     → renamed to APPR_DOC_NO
--   • PAY_DATE        — removed (refactor-only Scheduled Date column)
--   • DRAFT_USER_NM   — removed (refactor-only Requestor column)
--   • APPRVR          — removed (refactor-only Approver column)
--   • ETC             — added (special action slot, FIX_YN='Y')
--   • Widths replaced with legacy PROD values
--   • SORT_YN: 'Y' / 'N' mixed → 'Y' on every user column (legacy default)
--
-- Companion frontend change: page already uses
-- `<PaymentResultBatchListPage>` (CLAUDE.md §12a) — pass `fieldIdMap`
-- with legacy aliases (dateTime → TRAN_DT, fileName → FILE_NM,
-- txnCount → SCDUL_PYMN_DETL_NO, totalAmount → TRAN_AMT, status →
-- PROC_STAT, approvalRef → APPR_DOC_NO, approvalTitle → TITL); the
-- refactor's 4 extraColumns (PAY_DATE / APPR_STTS / DRAFT_USER_NM /
-- APPRVR) auto-hide via `isVisible()` since their FIELD_IDs are not
-- in this seed.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V177 row set first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults.
-- ----------------------------------------------------------------------------

-- 1. Drop the V177 row set we no longer need.
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-overseas-payment-result'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'REG_DTM', 'ORG_FILE_NM', 'TOT_TRNS_CNT', 'TOTAL_TRAN_AMT',
       'PAY_DATE', 'APPR_STTS', 'DRAFT_USER_NM', 'APPRVR',
       'APPR_SEQ_NO', 'TITL',
       'TRAN_DT', 'FILE_NM', 'SCDUL_PYMN_DETL_NO', 'TRAN_AMT',
       'PROC_STAT', 'APPR_DOC_NO', 'ETC'
   );

-- 2. Insert the legacy 7-user-column row set + ETC with PROD widths +
--    SORT_YN='Y' on every user column.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-overseas-payment-result', 'default', 'TRAN_DT',            'Date/Time',             1,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result', 'default', 'FILE_NM',            'File Name',             2,  'Y', 'N', 'Y', 'Y', '290px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result', 'default', 'SCDUL_PYMN_DETL_NO', 'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result', 'default', 'TRAN_AMT',           'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '235px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result', 'default', 'PROC_STAT',          'Transfer Status',       5,  'Y', 'N', 'Y', 'Y', '190px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result', 'default', 'APPR_DOC_NO',        'Approval Ref.',         6,  'Y', 'N', 'Y', 'Y', '160px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result', 'default', 'TITL',               'Approval Title',        7,  'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW()),
    ('payment-overseas-payment-result', 'default', 'ETC',                '',                      99, 'Y', 'Y', 'Y', 'N', '50px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-overseas-payment-result'
   AND DYM_AREA_ID = 'default';
