-- V244 — HaengSung Daily Financial permission-code split: grant remap (F-1, Risk R4).
--
-- The product-tree seed (V243) gives H's Daily Financial the SPLIT code
-- `banking-reports-daily-financial-hs` (legacy rept_0017_001); F/M keep
-- `banking-reports-daily-financial` (rept_0015_001). The frontend route guard
-- (wecms-web/lib/menuAuth.ts) now maps `/banking/reports/daily-financial-hs`
-- to the split code to match.
--
-- Legacy per-user grants in USER_MENU_AUTH_INFM may still carry the STANDARD
-- code for H tenants (granted before the split). For an H non-admin that grant
-- no longer intersects the H product tree (whose Daily Financial leaf is the
-- `-hs` code), so the HS report would be hidden. This migration remaps those
-- legacy H-tenant rows to the split code.
--
-- SCOPE: H tenants only (BUSI_INFM.PRDT_TYPE = 'H'). F / M rows are untouched —
-- they correctly keep `banking-reports-daily-financial`.
--
-- VERIFIED 2026-06-06 on the local R&D DB: 0 H-tenant rows carry either Daily
-- Financial code (only 5 F-tenant rows have the standard code, left untouched),
-- so this is a NO-OP locally. It is shipped for DEV-imported / UAT / PROD data
-- that carries legacy H grants (design Risk R4) and is idempotent: once remapped
-- there is nothing left to match.
--
-- REVERSE (manual, if ever needed): swap the two codes in the UPDATE below,
-- scoped to the same H-tenant predicate.

-- 1) Drop any legacy standard-code row that would collide with an existing
--    split-code row for the same (BIZ_KEY, USER_ID) — keep the `-hs` row
--    (the PK is BIZ_KEY + USER_ID + MENU_AUTH_CD, so a straight rename would
--    violate the PK if both rows already exist).
DELETE FROM USER_MENU_AUTH_INFM u
WHERE u.MENU_AUTH_CD = 'banking-reports-daily-financial'
  AND EXISTS (SELECT 1 FROM BUSI_INFM b
              WHERE b.BIZ_KEY = u.BIZ_KEY AND b.PRDT_TYPE = 'H')
  AND EXISTS (SELECT 1 FROM USER_MENU_AUTH_INFM h
              WHERE h.BIZ_KEY = u.BIZ_KEY
                AND h.USER_ID = u.USER_ID
                AND h.MENU_AUTH_CD = 'banking-reports-daily-financial-hs');

-- 2) Remap the remaining legacy standard-code rows for H tenants to the split code.
UPDATE USER_MENU_AUTH_INFM u
SET MENU_AUTH_CD = 'banking-reports-daily-financial-hs',
    MOD_ID = 'V244',
    MOD_DTM = now()
WHERE u.MENU_AUTH_CD = 'banking-reports-daily-financial'
  AND EXISTS (SELECT 1 FROM BUSI_INFM b
              WHERE b.BIZ_KEY = u.BIZ_KEY AND b.PRDT_TYPE = 'H');
