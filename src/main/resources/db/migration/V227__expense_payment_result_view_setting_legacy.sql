-- ----------------------------------------------------------------------------
-- V227 — Align Payment > Expense Payment > Payment Result View Setting
--        with the legacy pymn_0004_001 OSMC column set, widths, and sort
--        flags (2026-05-31)
--
-- The original V143 seed mirrored V131's `payment-internal-transfer-result`
-- shape (REG_DTM for Date/Time, TITL re-purposed for File Name, TRNF_YN
-- for Status, APPR_SEQ_NO for Approval Ref, DOC_NO as a trailing column)
-- with Figma-placeholder widths. The legacy JEX page pymn_0004_001
-- (queried verbatim via JEX DEV `SYS_SCR_OPTION_WECMS` on 2026-05-31)
-- renders 7 user columns + ETC in this canonical order:
--
--   1.   TRAN_DT             Date/Time              105px
--   2.   FILE_NM             File Name              290px
--   3.   SCDUL_PYMN_DETL_NO  No. of Transactions    155px
--   4.   TRAN_AMT            Total Transfer Amount  235px
--   5.   PROC_STAT           Transfer Status        190px
--   6.   APPR_DOC_NO         Approval Ref.          150px
--   7.   TITL                Approval Title         250px
--   99.  ETC                 (action slot)          50px   (fix_yn=Y, special)
--
-- Same shape as V219 (Internal Transfer Result) + V223 (Vendor Payment
-- Result). One PROD width delta vs V223 per the pymn_0004_001 dump:
-- APPR_DOC_NO 160 → 150px.
--
-- Differences from V143:
--   • REG_DTM → renamed to TRAN_DT (legacy Date/Time)
--   • TITL "File Name" → renamed to FILE_NM; new TITL row added for
--     "Approval Title" at disp_ord 7 / 250px
--   • TOT_TRNS_CNT → renamed to SCDUL_PYMN_DETL_NO (legacy count name)
--   • TOTAL_TRAN_AMT → renamed to TRAN_AMT
--   • TRNF_YN → renamed to PROC_STAT (legacy "process status")
--   • APPR_SEQ_NO → renamed to APPR_DOC_NO
--   • DOC_NO — removed (not in legacy column set)
--   • Widths replaced with legacy PROD values
--   • ETC size 60 → 50
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes any obsolete rows from V143 first.
--   * INSERTs use ON CONFLICT DO NOTHING.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults (same
--     precedent as V32/V41/V42/V207/V216/V217/V218/V219/V220/V221/V222
--     /V223/V224/V225/V226).
-- ----------------------------------------------------------------------------

-- 1. Drop the V143 row set we no longer need (ETC stays).
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-expense-payment-result'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'REG_DTM', 'TITL', 'TOT_TRNS_CNT', 'TOTAL_TRAN_AMT',
       'TRNF_YN', 'APPR_SEQ_NO', 'DOC_NO',
       'TRAN_DT', 'FILE_NM', 'SCDUL_PYMN_DETL_NO', 'TRAN_AMT',
       'PROC_STAT', 'APPR_DOC_NO'
   );

-- 2. Insert the legacy row set with PROD widths.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-expense-payment-result', 'default', 'TRAN_DT',            'Date/Time',             1, 'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'FILE_NM',            'File Name',             2, 'Y', 'N', 'Y', 'Y', '290px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'SCDUL_PYMN_DETL_NO', 'No. of Transactions',   3, 'Y', 'N', 'Y', 'Y', '155px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'TRAN_AMT',           'Total Transfer Amount', 4, 'Y', 'N', 'Y', 'Y', '235px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'PROC_STAT',          'Transfer Status',       5, 'Y', 'N', 'Y', 'Y', '190px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'APPR_DOC_NO',        'Approval Ref.',         6, 'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('payment-expense-payment-result', 'default', 'TITL',               'Approval Title',        7, 'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Update ETC width to legacy 50px. FIX_YN / USE_YN / DISP_YN stay as
--    V143 set them (admin-controlled special FIELD_ID).
UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE = '50px', MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-expense-payment-result'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID = 'ETC';

-- 4. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-expense-payment-result'
   AND DYM_AREA_ID = 'default';
