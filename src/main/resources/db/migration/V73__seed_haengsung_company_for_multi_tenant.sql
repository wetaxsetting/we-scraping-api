-- ============================================================================
-- V73__seed_haengsung_company_for_multi_tenant.sql
--
-- Multi-tenant scaffolding: seed the second R&D tenant
--   BIZ_KEY = 'VN_VNM0004_000014'   (HAENGSUNG ELECTRONICS)
--
-- Sources real wecmsvndev data for BUSI_INFM, USER_INFM, USER_BUSI_CONN_INFM,
-- and BUSI_ADMN_INFM rows tied to BIZ_KEY 000014 (snapshot 2026-05-11). The
-- 6 DEV-imported users are seeded with the BCrypt sentinel password
-- (__BCRYPT_PENDING__) and patched on startup by DataSeeder to BCrypt of
-- "Password!1" so every R&D login uses the same well-known credential.
--
-- Designed for mixed local DB states. Developer DBs fall into one of two
-- shapes:
--
--   A. Bootstrap-only DBs — V5__seed_from_jex_dev_20260422.sql produced
--      few/no real users, so BootstrapRunner created 'rndadmin' + V2 seeded
--      'testuser' / 'lockeduser'. Both 'rndadmin' and 'testuser' exist in
--      USER_INFM.
--
--   B. DEV-imported DBs — V5's snapshot pulled real wecmsvndev users
--      (~99 rows including 'lamine@gmail.com' as the active admin),
--      then V6's 'scope to single tenant' deleted V2's 'testuser'/
--      'lockeduser' and BootstrapRunner short-circuited via
--      isBootstrapped(VN_VNM0004_000001) without creating 'rndadmin'.
--      Neither 'rndadmin' nor 'testuser' exists in USER_INFM.
--
-- The chooser-exerciser links below use INSERT ... SELECT ... WHERE EXISTS
-- so they no-op when the parent user is absent — preventing FK violations
-- against USER_INFM on shape (B) DBs. They light up on shape (A) DBs.
--
-- Additionally, this migration links 'lamine@gmail.com' (the most active
-- real user on shape-(B) DBs) to BIZ_KEY 000014 as a normal USER so the
-- chooser branch is actually exercisable on DEV-imported DBs too. That
-- link is also guarded by WHERE EXISTS — no-op on DBs where lamine
-- doesn't exist.
--
-- BUSI_ADMN_INFM authority mapping after this migration:
--   VN_VNM0004_000001  → (untouched by V73) existing admins remain ADMIN.
--   VN_VNM0004_000014  → ONLY 'wecmshaengsung@gmail.com' is ADMIN. The
--                        other 5 DEV-imported users + the optional
--                        'rndadmin' + 'testuser' + 'lamine@gmail.com'
--                        links are USER (no BUSI_ADMN_INFM row inserted).
--
-- All inserts idempotent (ON CONFLICT DO NOTHING on every row + WHERE
-- EXISTS guards). No DELETE / no UPDATE on existing rows — V73 strictly
-- adds new data. VN_VNM0004_000001's BUSI_INFM / USER_INFM /
-- USER_BUSI_CONN_INFM / BUSI_ADMN_INFM rows are never touched.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BUSI_INFM — HAENGSUNG ELECTRONICS (real wecmsvndev data, 2025-08-19 join).
--    Local schema (V1 + V7): JOIN_DT is TIMESTAMPTZ. DEV stores the legacy
--    YYYYMMDDHH24MISS string, so cast on the way in.
-- ----------------------------------------------------------------------------
INSERT INTO BUSI_INFM (
    BIZ_KEY, CMPN_NM, CMPN_ENM, TAX_ID, RPPR_NM, EML,
    RPRS_BSUN_POST_ADRS, RPRS_BSUN_CITY, RPRS_BSUN_ST,
    JOIN_DT, FST_JOIN_NM, FST_JOIN_TEL,
    NTNL_CD, BUSI_STTS, DEL_YN, CHNL_ID, PRDT_TYPE,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM
) VALUES (
    'VN_VNM0004_000014',
    'HAENGSUNG ELECTRONICS',
    'HAENGSUNG ELECTRONICS',
    '0201635899',
    'HAENGSUNG ELECTRONICS',
    'wecmshaengsung@gmail.com',
    'LK08-13, Quarter 3, Thoi Son Ward, Dong Thap Province, Vietnam',
    'Dong Thap',
    'Vietnam',
    to_timestamp('20250819050035', 'YYYYMMDDHH24MISS'),
    'HAENGSUNG ELECTRONICS',
    '+00842873077799',
    'VN', 'N', 'N', 'VNM0004', 'H',
    'SYSTEM',
    to_timestamp('20250819050035', 'YYYYMMDDHH24MISS'),
    'wecmshaengsung@gmail.com',
    to_timestamp('20251208022743', 'YYYYMMDDHH24MISS')
)
ON CONFLICT (BIZ_KEY) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. USER_INFM — 6 users linked to BIZ_KEY 000014 in wecmsvndev.
--    USER_PW is the sentinel; DataSeeder patches it to BCrypt(Password!1)
--    on startup. EML kept from DEV (the address actually receives mail in
--    the live system but is irrelevant locally).
-- ----------------------------------------------------------------------------
INSERT INTO USER_INFM (
    USER_ID, USER_NM, USER_PW, EML,
    ACTV_YN, LOCK_YN, DEL_YN,
    DEPT_NM, POSI_NM,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM
) VALUES
    ('wecmshaengsung@gmail.com', 'Admin',         '__BCRYPT_PENDING__', 'wecmshaengsung@gmail.com',
     'Y','N','N', 'QA',             'Testing',
     'SYSTEM',                   to_timestamp('20250819050052','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20250918081553','YYYYMMDDHH24MISS')),
    ('dev_hs@gmail.com',        'Sokny Ran',     '__BCRYPT_PENDING__', 'dev_hs@gmail.com',
     'Y','N','N', '',               'Jinpai Hill, Taiyang''ao, Baihua Town, Huidong County, Guangdong, China',
     'wecmshaengsung@gmail.com', to_timestamp('20250821113551','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251203092444','YYYYMMDDHH24MISS')),
    ('vathnaf@gmail.com',       'vathna',        '__BCRYPT_PENDING__', 'vathnaf@gmail.com',
     'Y','N','N', 'web',            'web',
     'wecmshaengsung@gmail.com', to_timestamp('20250822073455','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251205062222','YYYYMMDDHH24MISS')),
    ('kosigntc@gmail.com',      'Chan Youvita',  '__BCRYPT_PENDING__', 'chanyouvita.kosign@gmail.com',
     'Y','N','N', 'Global Product', 'QA',
     'wecmshaengsung@gmail.com', to_timestamp('20251023075530','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251223035048','YYYYMMDDHH24MISS')),
    ('ouddomDev@gmail.com',     'Ouddom',        '__BCRYPT_PENDING__', 'ouddomDev@gmail.com',
     'Y','N','N', 'Global PROD',    'Dev',
     'wecmshaengsung@gmail.com', to_timestamp('20251114033237','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251205043125','YYYYMMDDHH24MISS')),
    ('dom@gmail.com',           'dom',           '__BCRYPT_PENDING__', 'dom@gmail.com',
     'Y','N','N', 'dev',            'dev',
     'wecmshaengsung@gmail.com', to_timestamp('20251114064132','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251205062814','YYYYMMDDHH24MISS'))
ON CONFLICT (USER_ID) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3a. USER_BUSI_CONN_INFM — 6 DEV-imported HAENGSUNG users → BIZ_KEY 000014.
--     Safe as an unconditional VALUES block: section 2 above inserts these
--     user_ids into USER_INFM in the same migration / same transaction,
--     so the FK to USER_INFM is satisfied either way (existing rows are
--     untouched by ON CONFLICT DO NOTHING; new rows are present when this
--     section runs).
-- ----------------------------------------------------------------------------
INSERT INTO USER_BUSI_CONN_INFM (
    USER_ID, BIZ_KEY, EMPL_NO, DEL_YN,
    LST_LGN_DATE,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM
) VALUES
    ('wecmshaengsung@gmail.com', 'VN_VNM0004_000014', NULL, 'N',
     to_timestamp('20260428081955','YYYYMMDDHH24MISS'),
     'SYSTEM',                   to_timestamp('20250819050059','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20250918081553','YYYYMMDDHH24MISS')),
    ('dev_hs@gmail.com', 'VN_VNM0004_000014', NULL, 'N',
     to_timestamp('20260408040522','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20250821113551','YYYYMMDDHH24MISS'),
     'dev_hs@gmail.com',         to_timestamp('20251203092444','YYYYMMDDHH24MISS')),
    ('vathnaf@gmail.com', 'VN_VNM0004_000014', NULL, 'N',
     to_timestamp('20251023072449','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20250822073455','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251205062222','YYYYMMDDHH24MISS')),
    ('kosigntc@gmail.com', 'VN_VNM0004_000014', NULL, 'N',
     to_timestamp('20260217014953','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251023075530','YYYYMMDDHH24MISS'),
     'kosigntc@gmail.com',       to_timestamp('20251223035048','YYYYMMDDHH24MISS')),
    ('ouddomDev@gmail.com', 'VN_VNM0004_000014', NULL, 'N',
     to_timestamp('20260210094535','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251114033237','YYYYMMDDHH24MISS'),
     'ouddomDev@gmail.com',      to_timestamp('20251205043125','YYYYMMDDHH24MISS')),
    ('dom@gmail.com', 'VN_VNM0004_000014', NULL, 'N',
     to_timestamp('20260217064742','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251114064132','YYYYMMDDHH24MISS'),
     'dom@gmail.com',            to_timestamp('20251205062814','YYYYMMDDHH24MISS'))
ON CONFLICT (USER_ID, BIZ_KEY) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3b. Multi-company chooser exercisers — guarded so missing parent users
--     never cause FK violations.
--
--     Each row is INSERT...SELECT ... WHERE EXISTS (SELECT 1 FROM USER_INFM
--     WHERE USER_ID = ...): no-op when the user is absent, normal insert
--     when present. ON CONFLICT DO NOTHING handles re-run idempotency
--     (e.g. if a previous V73 attempt partially inserted these rows).
--
--     'rndadmin' / 'testuser' light up on bootstrap-only DBs (shape A in
--     the header). 'lamine@gmail.com' lights up on DEV-imported DBs
--     (shape B). At least one of the two paths is active on every
--     developer's local DB, giving every developer a multi-company user
--     to exercise the company-chooser flow with.
-- ----------------------------------------------------------------------------

-- rndadmin → 000014 as USER (bootstrap-only DBs).
INSERT INTO USER_BUSI_CONN_INFM (
    USER_ID, BIZ_KEY, EMPL_NO, DEL_YN, LST_LGN_DATE,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM
)
SELECT 'rndadmin', 'VN_VNM0004_000014', NULL, 'N', NULL,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
 WHERE EXISTS (SELECT 1 FROM USER_INFM WHERE USER_ID = 'rndadmin')
ON CONFLICT (USER_ID, BIZ_KEY) DO NOTHING;

-- testuser → 000014 as USER (bootstrap-only DBs).
INSERT INTO USER_BUSI_CONN_INFM (
    USER_ID, BIZ_KEY, EMPL_NO, DEL_YN, LST_LGN_DATE,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM
)
SELECT 'testuser', 'VN_VNM0004_000014', NULL, 'N', NULL,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
 WHERE EXISTS (SELECT 1 FROM USER_INFM WHERE USER_ID = 'testuser')
ON CONFLICT (USER_ID, BIZ_KEY) DO NOTHING;

-- lamine@gmail.com → 000014 as USER (DEV-imported DBs). NOT seeded into
-- BUSI_ADMN_INFM for 000014, so authority resolves to USER at the new
-- company even though lamine is an ADMIN at 000001 — confirms the
-- per-link authority model.
INSERT INTO USER_BUSI_CONN_INFM (
    USER_ID, BIZ_KEY, EMPL_NO, DEL_YN, LST_LGN_DATE,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM
)
SELECT 'lamine@gmail.com', 'VN_VNM0004_000014', NULL, 'N', NULL,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
 WHERE EXISTS (SELECT 1 FROM USER_INFM WHERE USER_ID = 'lamine@gmail.com')
ON CONFLICT (USER_ID, BIZ_KEY) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4. BUSI_ADMN_INFM — authority per (BIZ_KEY, USER_ID).
--    Mirrors DEV exactly: wecmshaengsung@gmail.com is the only admin of
--    BIZ_KEY 000014. The other 5 DEV users + rndadmin + testuser remain
--    normal USERs at 000014 (presence in this table = ADMIN per legacy
--    authority model — no AUTH_GR_CD column exists in legacy
--    USER_BUSI_CONN_INFM, confirmed via DEV information_schema 2026-05-11).
-- ----------------------------------------------------------------------------
INSERT INTO BUSI_ADMN_INFM (
    BIZ_KEY, USER_ID, USER_NM,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM
) VALUES
    ('VN_VNM0004_000014', 'wecmshaengsung@gmail.com', 'Admin',
     'SYSTEM',                   to_timestamp('20250819050059','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20250918081553','YYYYMMDDHH24MISS'))
ON CONFLICT (BIZ_KEY, USER_ID) DO NOTHING;
