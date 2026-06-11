-- ----------------------------------------------------------------------------
-- V218 — Align Payment > Internal Transfer > Scheduled Transfer Detail
--        View Setting with the legacy pymn_0009_003 OSMC column set,
--        widths, and labels (2026-05-30)
--
-- The original V131 seed (lines 253-263) carried an early-Sprint refactor
-- set: SCDUL_PYMN_DETL_NO + FINC_MDA_NO + DEPO_CCY_CD columns that the
-- live JEX page never surfaces, paired with mismatched labels ("Note" vs
-- "Description", "Description" vs "Remarks") that diverged from the
-- legacy WSVC contract.
--
-- The frontend table at
-- `components/payment/internal-transfer/ScheduledTransferDetailPage.tsx`
-- has been registry-driven for 8 user columns since 2026-04 and silently
-- filtered out the three V131 extras via the `COLUMN_REGISTRY[field_id]`
-- guard. This migration cleans the seed so the View Setting popover
-- stops listing toggle rows for columns that never render in the table,
-- and so labels in the popover match the table headers byte-for-byte.
--
-- Legacy PROD column set (queried from JEX DEV `SYS_SCR_OPTION_WECMS` on
-- 2026-05-30, FIELD_IDs translated to refactor slugs):
--
--   1.  TAR_BANK_CD     Deposit Bank             170px
--   2.  RCPT_NM         Recipient Name           300px
--   3.  TRAN_AMT        Transfer Amount          110px
--   4.  TRAN_USAG       Remarks                  300px
--   5.  CHART_ACCT_CD   Reporting Account        200px
--   6.  NOTE            Description              200px
--   7.  DOC_NO          Document No.             150px
--   8.  BANK_TRAN_ID    Banking Transaction ID   250px
--   99. ETC             (action slot)            60px   (fix_yn=Y, special)
--
-- Differences from V131:
--   • REMOVED: SCDUL_PYMN_DETL_NO (Line No.), FINC_MDA_NO (Account No.),
--             DEPO_CCY_CD (Deposit Currency) — three columns that the
--             legacy `pymn_0009_003` page never surfaces.
--   • ADDED:   BANK_TRAN_ID (Banking Transaction ID) — column #8 on the
--             legacy page; renders the dispatch ID once the batch has
--             been transferred.
--   • Sizes updated to match PROD widths.
--   • Labels updated: TRAN_USAG "Description" → "Remarks", NOTE "Note" →
--             "Description", CHART_ACCT_CD "Reporting Acct" →
--             "Reporting Account", DOC_NO "Doc No." → "Document No.".
--   • DISP_ORD compacted to 1..8 + 99.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the obsolete V131 rows first.
--   * INSERT BANK_TRAN_ID guarded by ON CONFLICT DO NOTHING.
--   * UPDATEs set the same values the legacy seed has.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — the lazy-init
--     branch in `ScreenOptionService.load(...)` re-copies the corrected
--     sys defaults on the user's next GET (same precedent as V32/V41/V42
--     /V207/V216/V217).
-- ----------------------------------------------------------------------------

-- 1. Remove the V131-only FIELD_IDs not in the legacy column set.
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN ('SCDUL_PYMN_DETL_NO', 'FINC_MDA_NO', 'DEPO_CCY_CD');

-- 2. Add the legacy column missing from V131.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('payment-internal-transfer-scheduled-detail', 'default', 'BANK_TRAN_ID', 'Banking Transaction ID',
     8, 'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Bring the 7 retained rows up to legacy PROD widths + labels + disp_ord.
UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 1, FIELD_NM = 'Deposit Bank', SIZE = '170px', SORT_YN = 'Y',
       MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default' AND FIELD_ID = 'TAR_BANK_CD';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 2, FIELD_NM = 'Recipient Name', SIZE = '300px', SORT_YN = 'Y',
       MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default' AND FIELD_ID = 'RCPT_NM';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 3, FIELD_NM = 'Transfer Amount', SIZE = '130px', SORT_YN = 'Y',
       MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default' AND FIELD_ID = 'TRAN_AMT';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 4, FIELD_NM = 'Remarks', SIZE = '300px', SORT_YN = 'Y',
       MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default' AND FIELD_ID = 'TRAN_USAG';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 5, FIELD_NM = 'Reporting Account', SIZE = '200px', SORT_YN = 'Y',
       MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default' AND FIELD_ID = 'CHART_ACCT_CD';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 6, FIELD_NM = 'Description', SIZE = '200px', SORT_YN = 'Y',
       MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default' AND FIELD_ID = 'NOTE';

UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = 7, FIELD_NM = 'Document No.', SIZE = '150px', SORT_YN = 'Y',
       MOD_ID = 'system', MOD_DTM = NOW()
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default' AND FIELD_ID = 'DOC_NO';

-- 4. Wipe USER_SCR_OPTION_WECMS overrides for the screen so the corrected
--    sys defaults take effect on every user's next GET. Same precedent as
--    V32/V41/V42/V207/V216/V217 — R&D scope, no preservation of local
--    user-saved overrides.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'payment-internal-transfer-scheduled-detail'
   AND DYM_AREA_ID = 'default';
