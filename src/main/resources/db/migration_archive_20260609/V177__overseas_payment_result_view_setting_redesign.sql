-- V177 — Payment > Overseas Payment > Payment Result — View Setting redesign
--
-- Replaces the V176 7-column seed with the 10-column layout matching the
-- product spec screenshot:
--   1 REG_DTM       Date/Time
--   2 ORG_FILE_NM   File Name
--   3 TOT_TRNS_CNT  No. of Transactions
--   4 TOTAL_TRAN_AMT Total Transfer Amount
--   5 PAY_DATE       Scheduled Date      (new)
--   6 APPR_STTS      Approval Status     (new)
--   7 DRAFT_USER_NM  Requestor           (new)
--   8 APPRVR         Approver            (new, USE_YN='N' — hidden by default)
--   9 APPR_SEQ_NO    Approval Ref.
--  10 TITL           Approval Title
--
-- TRNF_YN (Transfer Status) is removed — replaced by APPR_STTS.
-- User overrides are cleared so the lazy-init re-copies from the updated sys rows.

-- 1. Remove the old Transfer Status column
DELETE FROM sys_scr_option_wecms
WHERE web_svc_id = 'payment-overseas-payment-result'
  AND field_id   = 'TRNF_YN';

-- 2. Clear user overrides so lazy-init re-populates from sys on next load
DELETE FROM user_scr_option_wecms
WHERE web_svc_id = 'payment-overseas-payment-result';

-- 3. Update disp_ord for the two columns that shift right
UPDATE sys_scr_option_wecms
   SET disp_ord = 9,
       mod_dtm  = now()
 WHERE web_svc_id = 'payment-overseas-payment-result'
   AND field_id   = 'APPR_SEQ_NO';

UPDATE sys_scr_option_wecms
   SET disp_ord = 10,
       mod_dtm  = now()
 WHERE web_svc_id = 'payment-overseas-payment-result'
   AND field_id   = 'TITL';

-- 4. Insert the four new columns (idempotent via ON CONFLICT DO NOTHING)
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, dym_area_id, field_nm, disp_ord, use_yn, fix_yn,
     disp_yn, sort_yn, size, reg_id, reg_dtm, mod_id, mod_dtm)
VALUES
    ('payment-overseas-payment-result', 'PAY_DATE',      'default', 'Scheduled Date',   5, 'Y', 'N', 'Y', 'N', '140px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'APPR_STTS',     'default', 'Approval Status',  6, 'Y', 'N', 'Y', 'N', '140px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'DRAFT_USER_NM', 'default', 'Requestor',        7, 'Y', 'N', 'Y', 'N', '140px', 'system', now(), 'system', now()),
    ('payment-overseas-payment-result', 'APPRVR',        'default', 'Approver',         8, 'N', 'N', 'Y', 'N', '140px', 'system', now(), 'system', now())
ON CONFLICT (web_svc_id, field_id) DO NOTHING;
