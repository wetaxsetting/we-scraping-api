-- V176 — Payment > Overseas Payment > Payment Result — View Setting seed
--
-- Registers the 7 data columns for web_svc_id = 'payment-overseas-payment-result'
-- under dym_area_id = 'default'. FIELD_IDs mirror the keys checked by
-- PaymentResultBatchListPage.isVisible() in wecms-web.
--
-- Column count: 7 (no CHECKBOX/ETC specials — result pages have no row
-- selection column and no trailing action-column that maps to a FIELD_ID).
--
-- SORT_YN = 'N' for all columns — the Result list has no sort whitelist
-- enum today (read-only result data, ordered by reg_dtm DESC server-side).
-- FIX_YN  = 'N' for all columns — users may hide any column.
-- DISP_YN = 'Y' for all columns — all columns appear in the popover.
-- USE_YN  = 'Y' for all columns — all columns visible by default.
--
-- Idempotent via ON CONFLICT DO NOTHING.

INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, dym_area_id, field_nm, disp_ord, use_yn, fix_yn,
     disp_yn, sort_yn, size, reg_id, reg_dtm, mod_id, mod_dtm)
VALUES
    ('payment-overseas-payment-result', 'REG_DTM',        'default', 'Date/Time',              1, 'Y', 'N', 'Y', 'N', '120px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'ORG_FILE_NM',    'default', 'File Name',              2, 'Y', 'N', 'Y', 'N', '200px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'TOT_TRNS_CNT',   'default', 'No. of Transactions',    3, 'Y', 'N', 'Y', 'N', '130px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'TOTAL_TRAN_AMT', 'default', 'Total Transfer Amount',  4, 'Y', 'N', 'Y', 'N', '180px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'TRNF_YN',        'default', 'Transfer Status',        5, 'Y', 'N', 'Y', 'N', '130px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'APPR_SEQ_NO',    'default', 'Approval Ref.',          6, 'Y', 'N', 'Y', 'N', '140px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'TITL',           'default', 'Approval Title',         7, 'Y', 'N', 'Y', 'N', '200px', 'system', now(), 'system', now())
ON CONFLICT (web_svc_id, field_id) DO NOTHING;
