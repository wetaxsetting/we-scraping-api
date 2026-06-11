-- V179: Enable SORT_YN for Approval Status (APPR_STTS), Approval Ref (APPR_SEQ_NO),
--        and Approval Title (TITL) on the Overseas Scheduled Payment screen.
--        PAY_DATE (Schedule Date extra column) is not in SYS_SCR_OPTION_WECMS so no
--        seed row exists for it — its sort is wired entirely via the extra-column
--        sortFieldId prop on the frontend.
--
-- Backend whitelist OverseasScheduledSortField now covers all four aliases:
--   APPR_STS  → appr_sts   (Approval Status)
--   APPR_SEQ_NO → appr_seq_no (Approval Ref)
--   TITL      → titl       (Approval Title)
--   PAY_DATE  → pay_date   (Schedule Date — extra column, not seeded here)

UPDATE sys_scr_option_wecms
SET    sort_yn = 'Y'
WHERE  web_svc_id = 'payment-overseas-payment-scheduled'
  AND  dym_area_id = 'default'
  AND  field_id IN ('APPR_STTS', 'APPR_SEQ_NO', 'TITL');

-- Clear user overrides for this screen so the updated sys defaults take
-- effect on next lazy-init (mirrors the precedent from V41 / V42).
DELETE FROM user_scr_option_wecms
WHERE  web_svc_id = 'payment-overseas-payment-scheduled'
  AND  dym_area_id = 'default';
