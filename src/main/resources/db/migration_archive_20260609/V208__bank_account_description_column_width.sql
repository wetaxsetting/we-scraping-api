-- =====================================================================
-- V208 — Settings > Bank Account: narrow the DESCRIPTION column width.
--
-- The DESCRIPTION column was seeded at 200px (V153 / V186) but in
-- practice almost every row is blank — the wide column left a large
-- empty band and pushed the columns to its right needlessly far across
-- the horizontal scroll. 140px comfortably fits the "Description"
-- header label (rendered at 14px/600 + sort glyph + cell padding) and
-- any short description text while reclaiming 60px for the rest of the
-- table.
--
-- Effect:
--   SYS_SCR_OPTION_WECMS.SIZE flips 200px → 140px for
--   (WEB_SVC_ID='bank-account', DYM_AREA_ID='default', FIELD_ID='DESCRIPTION').
--
-- No USER_SCR_OPTION_WECMS touch needed: ScreenOptionService.buildMergedResponse
-- always sources SIZE from the system row (never the per-user override),
-- so the new width takes effect for every user on next GET without a
-- lazy-init reset. Column width is not user-customisable in this product.
--
-- Frontend counterpart (same MR): the FALLBACK_COLUMNS first-paint seed
-- in BankAccountPanel.tsx and the --w-wb-bank-acct-table-min token in
-- app/globals.css are reduced by the same 60px.
--
-- Idempotent.
-- =====================================================================

UPDATE SYS_SCR_OPTION_WECMS
   SET SIZE    = '140px',
       MOD_ID  = 'SYSTEM',
       MOD_DTM = NOW()
 WHERE WEB_SVC_ID  = 'bank-account'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID    = 'DESCRIPTION'
   AND SIZE        = '200px';
