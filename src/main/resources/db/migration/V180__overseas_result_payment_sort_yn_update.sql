-- V180: Enable SORT_YN for sortable columns on the Overseas Payment Result screen.
--
-- Backend whitelist OverseasResultSortField now covers all aliases:
--   REG_DTM        → ops.reg_dtm
--   ORG_FILE_NM    → ops.org_file_nm
--   TOT_TRNS_CNT   → ops.tot_trns_cnt
--   TOTAL_TRAN_AMT → ops.total_tran_amt
--   DRAFT_USER_NM  → ops.draft_user_nm  (Requestor — extra column)
--   DOC_NO         → erm.doc_no         (Approval Ref)
--   APPR_TITL      → appr_titl          (Approval Title)
--   PAY_DATE       → pay_date           (Scheduled Date — extra column)
--   APPR_STTS      → appr_stts          (Approval Status — extra column)
--
-- APPRVR is NOT sortable (resolved from a separate batch query, no ORDER BY alias).

UPDATE sys_scr_option_wecms
SET    sort_yn = 'Y'
WHERE  web_svc_id = 'payment-overseas-payment-result'
  AND  dym_area_id = 'default'
  AND  field_id IN (
      'REG_DTM', 'ORG_FILE_NM', 'TOT_TRNS_CNT', 'TOTAL_TRAN_AMT',
      'PAY_DATE', 'APPR_STTS', 'DRAFT_USER_NM',
      'APPR_SEQ_NO', 'TITL'
  );

-- Clear user overrides so the updated sys defaults take effect on next lazy-init.
DELETE FROM user_scr_option_wecms
WHERE  web_svc_id = 'payment-overseas-payment-result'
  AND  dym_area_id = 'default';
