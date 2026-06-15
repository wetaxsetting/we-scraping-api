-- V178 — Payment > Overseas Payment > Scheduled Payment — View Setting seed
--
-- Registers the 7 gated data columns for web_svc_id = 'payment-overseas-payment-scheduled'
-- under dym_area_id = 'default'. FIELD_IDs mirror the isVisible() keys checked by
-- PaymentScheduledBatchListPage in wecms-web.
--
-- Gated columns (controlled by isVisible() in the shared shell):
--   1 REG_DTM        Date/Time
--   2 TITL           File Name
--   3 TOT_TRNS_CNT   No. of Transactions
--   4 TOTAL_TRAN_AMT Total Transfer Amount
--   5 APPR_STTS      Approval Status
--   6 REG_ID         Requestor
--   7 APPR_SEQ_NO    Approval Ref.
--
-- Always-on columns (no FIELD_ID gate — never seeded):
--   CHECKBOX, APPROVER, APPROVAL_TITLE, ACTION
--
-- SORT_YN = 'Y' for columns covered by OverseasScheduledPaymentSortField:
--   REG_DTM, TOT_TRNS_CNT, TOTAL_TRAN_AMT, REG_ID.
-- SORT_YN = 'N' for columns with no sort whitelist entry (TITL, APPR_STTS, APPR_SEQ_NO).
-- FIX_YN  = 'N' for all columns — users may hide any column.
-- DISP_YN = 'Y' for all columns — all columns appear in the popover.
-- USE_YN  = 'Y' for all columns — all columns visible by default.
--
-- Idempotent via ON CONFLICT DO NOTHING.

INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, dym_area_id, field_nm, disp_ord, use_yn, fix_yn,
     disp_yn, sort_yn, size, reg_id, reg_dtm, mod_id, mod_dtm)
VALUES
    ('payment-overseas-payment-scheduled', 'REG_DTM',        'default', 'Date/Time',             1, 'Y', 'N', 'Y', 'Y', '120px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-scheduled', 'TITL',           'default', 'File Name',             2, 'Y', 'N', 'Y', 'N', '200px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-scheduled', 'TOT_TRNS_CNT',   'default', 'No. of Transactions',   3, 'Y', 'N', 'Y', 'Y', '130px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-scheduled', 'TOTAL_TRAN_AMT', 'default', 'Total Transfer Amount', 4, 'Y', 'N', 'Y', 'Y', '180px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-scheduled', 'APPR_STTS',      'default', 'Approval Status',       5, 'Y', 'N', 'Y', 'N', '140px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-scheduled', 'REG_ID',         'default', 'Requestor',             6, 'Y', 'N', 'Y', 'Y', '140px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-scheduled', 'APPR_SEQ_NO',    'default', 'Approval Ref.',         7, 'Y', 'N', 'Y', 'N', '140px', 'system', now(), 'system', now())
ON CONFLICT (web_svc_id, field_id) DO NOTHING;
