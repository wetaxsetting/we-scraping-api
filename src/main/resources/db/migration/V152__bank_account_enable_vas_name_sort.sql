-- =====================================================================
-- V152 — Settings > Bank Account: enable sort on the VAS_NAME column.
--
-- V127 shipped VAS_NAME with SORT_YN='N' because the legacy comment
-- assumed the column was projected from BUSI_VAS_ACCT_INFM and had no
-- backing field on ACCT_MSTR. That was incorrect — `vas_acct_nm` is
-- denormalised onto ACCT_MSTR (the bank-account row carries its own
-- copy of the VAS name), so sorting works without joining the sibling
-- table.
--
-- Effect:
--   1. SYS_SCR_OPTION_WECMS.SORT_YN flips N → Y for
--      (WEB_SVC_ID='bank-account', FIELD_ID='VAS_NAME').
--   2. USER_SCR_OPTION_WECMS rows for the same scope are deleted so
--      lazy-init (ScreenOptionService.load) re-copies the updated
--      sys default on next GET. Mirrors the V42 / V151 cutover.
--
-- The Java whitelist enum `BankAccountSortField` ships the matching
-- constant (`VAS_NAME → vasAcctNm`) in the same MR so the "seed +
-- whitelist must agree" rule from wecms-api-main/CLAUDE.md §"Common
-- table sort" is preserved.
--
-- Idempotent.
-- =====================================================================

UPDATE SYS_SCR_OPTION_WECMS
   SET SORT_YN = 'Y',
       MOD_ID  = 'SYSTEM',
       MOD_DTM = TO_CHAR(NOW() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS')
 WHERE WEB_SVC_ID  = 'bank-account'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID    = 'VAS_NAME'
   AND SORT_YN     = 'N';

DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID  = 'bank-account'
   AND DYM_AREA_ID = 'default';
