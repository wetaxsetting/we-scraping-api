-- ----------------------------------------------------------------------------
-- V232 — Align Payment > Payroll Payment > Scheduled Payment View Setting
--        with the legacy pymn_0005_001 OSMC column set, widths, and sort
--        flags (2026-05-31)
--
-- The original V144 seed used a refactor-tailored set built around the
-- shell's hardcoded FIELD_IDs (TITL=File Name, REG_ID=Requestor,
-- APPR_SEQ_NO=Approval Ref, TRNF_YN=Transfer Status, DOC_NO=Doc No.) with
-- Figma-placeholder widths. The legacy JEX page pymn_0005_001 (column set
-- from SYS_SCR_OPTION_WECMS, verified against pymn_0005_001.ts +
-- WSVC.pymn_0005_001_r001.xml) renders 11 user columns + checkbox + ETC in
-- this canonical order:
--
--   0.   CHECKBOX        Checkbox              40px   (fix_yn=Y, special)
--   1.   REG_DTM         Date/Time             105px
--   2.   FILE_NM         File Name             290px
--   3.   TOT_TRNS_CNT    No. of Transactions   150px
--   4.   TOTAL_TRAN_AMT  Total Transfer Amount 200px
--   5.   PAY_DATE        Scheduled Date        150px
--   6.   APPR_STTS       Approval Status       170px
--   7.   APPR_FUND_TP    Payroll Type          150px
--   8.   DRAFT_USER_NM   Requestor             100px
--   9.   APPRVR_NM       Approver              175px
--   10.  APPR_DOC_NO     Approval Ref.         150px
--   11.  TITL            Approval Title        250px
--   99.  ETC             (action col)          150px  (fix_yn=Y, special)
--
-- This mirrors V217 (Internal Transfer Scheduled) exactly, with the single
-- addition of APPR_FUND_TP (Payroll Type) at disp_ord 7 — a Payroll-only
-- column the other Scheduled siblings don't carry. APPR_FUND_TP projects
-- from EXPT_RESOL_MSTR.APPR_FUND_TP via the APPR_SEQ_NO join (legacy
-- IDO.SCDUL_PYMN_SUMR_R002.xml:98).
--
-- Refactor identifier naming — slugs, not WSVC ids (per workspace CLAUDE.md):
-- WEB_SVC_ID is the route slug 'payment-payroll-payment-scheduled'.
--
-- Differences from V144 (Payroll Scheduled screen only):
--   • REG_DTM size 150 → 105
--   • TITL "File Name" → renamed to FILE_NM (290px); new TITL row added for
--     "Approval Title" at disp_ord 11 / 250px
--   • TOT_TRNS_CNT size 140 → 150
--   • TOTAL_TRAN_AMT size 180 → 200
--   • APPR_STTS sort_yn 'N' → 'Y' (entity has SCDUL_PYMN_SUMR.APPR_STTS;
--     PayrollPaymentSortField gains the APPR_STTS constant in this MR)
--   • REG_ID "Requestor" → renamed to DRAFT_USER_NM (100px)
--   • APPR_SEQ_NO "Approval Ref." → renamed to APPR_DOC_NO (150px)
--   • TRNF_YN (Transfer Status), DOC_NO (Doc No.) — removed (not in the
--     legacy column set; the action column carries the dispatch state)
--   • PAY_DATE — added (rendered via the page's `extraColumn` slot;
--     150px; legacy treats it as a regular View Setting column)
--   • APPRVR_NM — added (175px)
--   • APPR_FUND_TP — added (Payroll Type, 150px) — Payroll-only column
--   • ETC size 60 → 150; CHECKBOX size 38 → 40
--
-- Sort note: APPRVR_NM + APPR_FUND_TP carry SORT_YN='Y' for legacy parity
-- but are NOT in PayrollPaymentSortField — both project from joins the
-- single-table paged query can't sort on, so the frontend omits them from
-- SORTABLE_COLUMNS (no glyph). Same deferral as V217's APPRVR_NM.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the obsolete V144 rows first.
--   * INSERTs use ON CONFLICT DO NOTHING so a partially-applied retry
--     resolves cleanly.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — the lazy-init
--     branch in ScreenOptionService.load(...) re-copies the corrected sys
--     defaults on the user's next GET (same precedent as V217).
--
-- Only the Payroll *Scheduled* screen is touched — the other three Payroll
-- screens seeded in V144 (scheduled-detail, result, result-detail) are
-- left untouched.
-- ----------------------------------------------------------------------------

-- 1. Drop the V144 row set we no longer need so the new INSERTs can
--    repopulate from scratch. CHECKBOX + ETC are kept (admin-controlled
--    special rows); everything else is reseeded.
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-payroll-payment-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'REG_DTM', 'TITL', 'TOT_TRNS_CNT', 'TOTAL_TRAN_AMT',
       'APPR_STTS', 'REG_ID', 'APPR_SEQ_NO', 'TRNF_YN', 'DOC_NO',
       'FILE_NM', 'PAY_DATE', 'APPR_FUND_TP', 'DRAFT_USER_NM',
       'APPRVR_NM', 'APPR_DOC_NO'
   );

-- 2. Insert the legacy row set with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-payroll-payment-scheduled', 'default', 'REG_DTM',        'Date/Time',             1,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'FILE_NM',        'File Name',             2,  'Y', 'N', 'Y', 'Y', '290px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'TOT_TRNS_CNT',   'No. of Transactions',   3,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'PAY_DATE',       'Scheduled Date',        5,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'APPR_STTS',      'Approval Status',       6,  'Y', 'N', 'Y', 'Y', '170px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'APPR_FUND_TP',   'Payroll Type',          7,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'DRAFT_USER_NM',  'Requestor',             8,  'Y', 'N', 'Y', 'Y', '100px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'APPRVR_NM',      'Approver',              9,  'Y', 'N', 'Y', 'Y', '175px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'APPR_DOC_NO',    'Approval Ref.',         10, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-payroll-payment-scheduled', 'default', 'TITL',           'Approval Title',        11, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Update CHECKBOX + ETC widths to match legacy (CHECKBOX 38→40px,
--    ETC 60→150px). Special FIELD_IDs are admin-controlled — only SIZE
--    is updated here, the FIX_YN / DISP_YN / USE_YN flags stay as the
--    V144 seed set them.
UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '40px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-payroll-payment-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'CHECKBOX';

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '150px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-payroll-payment-scheduled'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'ETC';

-- 4. Wipe USER_SCR_OPTION_WECMS overrides for the screen so the corrected
--    sys defaults take effect on every user's next GET. Same precedent as
--    V217 — R&D scope, no preservation of local user-saved overrides.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-payroll-payment-scheduled'
   AND DYM_AREA_ID = 'default';
