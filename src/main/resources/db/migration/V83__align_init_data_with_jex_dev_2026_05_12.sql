-- ============================================================================
-- V83__align_init_data_with_jex_dev_2026_05_12.sql
--
-- Align BUSI_INFM + USER_INFM init data on the shared refactor DB
-- (172.168.5.172:5432/wecmsdevdb) with JEX DEV (10.254.242.254:54311/
-- wecmsvndevdb) for both R&D tenants:
--   - VN_VNM0004_000001 (CÔNG TY TNHH WEBCASH VIỆT NAM, PRDT_TYPE='F')
--   - VN_VNM0004_000014 (HAENGSUNG ELECTRONICS,        PRDT_TYPE='H')
--
-- Snapshot taken: 2026-05-12 via the read-only `postgres_dev` MCP, with
-- the user set filtered by USER_BUSI_CONN_INFM.DEL_YN='N' for both
-- BIZ_KEYs (48 users for 000001 + 6 for 000014 = 54 total).
--
-- Sync scope (per Sonny 2026-05-12 — "all collom and information this
-- 2 table must be the same"):
--   - BUSI_INFM: every column for both BIZ_KEYs reset to DEV values.
--   - USER_INFM: every column for all 54 linked users reset to DEV
--     values, INCLUDING USER_PW.
--
-- USER_PW handling — overrides the sentinel pattern:
--   * Refactor's normal seed inserts USER_PW='__BCRYPT_PENDING__', and
--     DataSeeder (Profile=local) patches the sentinel to BCrypt of the
--     well-known R&D password "Password!1" on startup.
--   * V83 deliberately writes DEV's legacy SHA-256 base64 hashes
--     verbatim. After V83:
--       - DataSeeder no longer patches these rows (USER_PW != sentinel).
--       - "Password!1" stops working for the 54 synced users.
--       - Each user's REAL JEX password (e.g. lamine@gmail.com /
--         test@2001) is the working credential.
--       - First successful login transparently upgrades the SHA-256
--         hash to BCrypt via WecmsPasswordEncoder.upgradeEncoding
--         (see SecurityConfig + WecmsPasswordEncoder).
--
-- SECURITY: this migration contains SHA-256 base64 hashes of real user
-- passwords from the JEX DEV environment. SHA-256 of short personal
-- passwords is trivially brute-forceable. Treat this file as sensitive:
--   - GitLab repo must remain private to the WeCMS team.
--   - Do NOT export this file outside the team.
--   - If real-user password parity is no longer required, replace this
--     migration's USER_PW values with '__BCRYPT_PENDING__' (see
--     "Reversal recipe" at the bottom of this file).
--
-- Type conversions (refactor schema deviates from DEV per V7 directive):
--   DEV column         | DEV type     | Refactor type   | Cast used
--   join_dt            | VARCHAR(14)  | TIMESTAMPTZ     | to_timestamp(..,'YYYYMMDDHH24MISS')
--   fst_use_date       | VARCHAR(14)  | TIMESTAMPTZ     | to_timestamp(..)
--   reg_dtm / mod_dtm  | VARCHAR(14)  | TIMESTAMPTZ     | to_timestamp(..)
--   lst_pay_date       | VARCHAR(8)   | DATE            | to_date(..,'YYYYMMDD')
--   use_date_from/to   | VARCHAR(8)   | DATE            | to_date(..)
--   free_use_date_*    | VARCHAR(8)   | DATE            | to_date(..)
--   canc_date          | VARCHAR(8)   | DATE            | to_date(..)
--   paid_start_date    | VARCHAR(8)   | DATE            | to_date(..)
--   lst_lgn_date / mob_| VARCHAR(14)  | TIMESTAMPTZ     | (all NULL in DEV — no cast needed)
-- Empty strings in DEV's VARCHAR date columns map to NULL locally.
--
-- Idempotent: BUSI_INFM uses INSERT … ON CONFLICT DO UPDATE; USER_INFM
-- uses INSERT … ON CONFLICT (USER_ID) DO UPDATE. Safe to re-run.
--
-- Out of scope for this migration:
--   - USER_BUSI_CONN_INFM rows: V73 already seeds the multi-tenant
--     links; V83 does NOT touch them.
--   - BUSI_ADMN_INFM rows: V73 already seeds wecmshaengsung@gmail.com
--     for 000014; admins for 000001 are seeded elsewhere.
--   - Users present in local but no longer in DEV (deleted users): NOT
--     deleted by V83. If a cleanup pass is needed, ship a follow-up
--     migration after Sonny confirms which user_ids should be removed.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BUSI_INFM — VN_VNM0004_000001 (CÔNG TY TNHH WEBCASH VIỆT NAM)
-- ----------------------------------------------------------------------------
INSERT INTO BUSI_INFM (
    BIZ_KEY, CMPN_NM, CMPN_ENM, TAX_ID, RPPR_NM,
    TEL_NO, FXNO, EML,
    RPRS_BSUN_POST_ADRS, RPRS_BSUN_DTL_ADRS, RPRS_BSUN_CITY, RPRS_BSUN_ST,
    CMPN_LOGO,
    JOIN_DT, FST_JOIN_NM, FST_JOIN_TEL,
    FST_USE_DATE, LST_PAY_DATE, USE_DATE_FROM, USE_DATE_TO,
    BUSI_STTS, NTNL_CD,
    FREE_USE_DATE_FROM, FREE_USE_DATE_TO,
    BANK_CD, ACCT_NO, CHNL_ID,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM,
    OVERDUE_YN, CANC_DATE, WIZ_RUN_YN, CMPN_STAMP,
    DEL_YN, BUSI_NICK_NM, ALLI_CMPN_CD, ALLI_CMPN_NM,
    PRDT_TYPE, OWAC_NM, MEMO, PAID_START_DATE, CMPN_QR_CD_IMG, ERP_CD
) VALUES (
    'VN_VNM0004_000001',
    'CÔNG TY TNHH WEBCASH VIỆT NAM',
    'CÔNG TY TNHH WEBCASH VIỆT NAM',
    '1201496252',
    'CÔNG TY TNHH',
    NULL, NULL,
    'info@webcashvietnam.com',
    'LK08-13, Khu phố 3, Phường 6, Thành phố Mỹ Tho, Thành phố Mỹ Tho, Tien Giang, Vietnam222',
    NULL,
    'Tien Giang',
    'Vietnam',
    'File_1749540397981_2.png',
    to_timestamp('20241030102902','YYYYMMDDHH24MISS'),
    'lamine',
    '+008432323233',
    to_timestamp('20241030080650','YYYYMMDDHH24MISS'),
    NULL, NULL, NULL,
    'N', 'VN',
    to_date('20250731','YYYYMMDD'),
    to_date('20260731','YYYYMMDD'),
    NULL, NULL, 'VNM0004',
    'SYSTEM',
    to_timestamp('20241030032902','YYYYMMDDHH24MISS'),
    'lamine@gmail.com',
    to_timestamp('20251205084936','YYYYMMDDHH24MISS'),
    'N', NULL, 'N', NULL,
    'N', NULL, '', '',
    'F', NULL, NULL, NULL, NULL, NULL
)
ON CONFLICT (BIZ_KEY) DO UPDATE SET
    CMPN_NM             = EXCLUDED.CMPN_NM,
    CMPN_ENM            = EXCLUDED.CMPN_ENM,
    TAX_ID              = EXCLUDED.TAX_ID,
    RPPR_NM             = EXCLUDED.RPPR_NM,
    TEL_NO              = EXCLUDED.TEL_NO,
    FXNO                = EXCLUDED.FXNO,
    EML                 = EXCLUDED.EML,
    RPRS_BSUN_POST_ADRS = EXCLUDED.RPRS_BSUN_POST_ADRS,
    RPRS_BSUN_DTL_ADRS  = EXCLUDED.RPRS_BSUN_DTL_ADRS,
    RPRS_BSUN_CITY      = EXCLUDED.RPRS_BSUN_CITY,
    RPRS_BSUN_ST        = EXCLUDED.RPRS_BSUN_ST,
    CMPN_LOGO           = EXCLUDED.CMPN_LOGO,
    JOIN_DT             = EXCLUDED.JOIN_DT,
    FST_JOIN_NM         = EXCLUDED.FST_JOIN_NM,
    FST_JOIN_TEL        = EXCLUDED.FST_JOIN_TEL,
    FST_USE_DATE        = EXCLUDED.FST_USE_DATE,
    LST_PAY_DATE        = EXCLUDED.LST_PAY_DATE,
    USE_DATE_FROM       = EXCLUDED.USE_DATE_FROM,
    USE_DATE_TO         = EXCLUDED.USE_DATE_TO,
    BUSI_STTS           = EXCLUDED.BUSI_STTS,
    NTNL_CD             = EXCLUDED.NTNL_CD,
    FREE_USE_DATE_FROM  = EXCLUDED.FREE_USE_DATE_FROM,
    FREE_USE_DATE_TO    = EXCLUDED.FREE_USE_DATE_TO,
    BANK_CD             = EXCLUDED.BANK_CD,
    ACCT_NO             = EXCLUDED.ACCT_NO,
    CHNL_ID             = EXCLUDED.CHNL_ID,
    REG_ID              = EXCLUDED.REG_ID,
    REG_DTM             = EXCLUDED.REG_DTM,
    MOD_ID              = EXCLUDED.MOD_ID,
    MOD_DTM             = EXCLUDED.MOD_DTM,
    OVERDUE_YN          = EXCLUDED.OVERDUE_YN,
    CANC_DATE           = EXCLUDED.CANC_DATE,
    WIZ_RUN_YN          = EXCLUDED.WIZ_RUN_YN,
    CMPN_STAMP          = EXCLUDED.CMPN_STAMP,
    DEL_YN              = EXCLUDED.DEL_YN,
    BUSI_NICK_NM        = EXCLUDED.BUSI_NICK_NM,
    ALLI_CMPN_CD        = EXCLUDED.ALLI_CMPN_CD,
    ALLI_CMPN_NM        = EXCLUDED.ALLI_CMPN_NM,
    PRDT_TYPE           = EXCLUDED.PRDT_TYPE,
    OWAC_NM             = EXCLUDED.OWAC_NM,
    MEMO                = EXCLUDED.MEMO,
    PAID_START_DATE     = EXCLUDED.PAID_START_DATE,
    CMPN_QR_CD_IMG      = EXCLUDED.CMPN_QR_CD_IMG,
    ERP_CD              = EXCLUDED.ERP_CD;

-- ----------------------------------------------------------------------------
-- 2. BUSI_INFM — VN_VNM0004_000014 (HAENGSUNG ELECTRONICS)
-- ----------------------------------------------------------------------------
INSERT INTO BUSI_INFM (
    BIZ_KEY, CMPN_NM, CMPN_ENM, TAX_ID, RPPR_NM,
    TEL_NO, FXNO, EML,
    RPRS_BSUN_POST_ADRS, RPRS_BSUN_DTL_ADRS, RPRS_BSUN_CITY, RPRS_BSUN_ST,
    CMPN_LOGO,
    JOIN_DT, FST_JOIN_NM, FST_JOIN_TEL,
    FST_USE_DATE, LST_PAY_DATE, USE_DATE_FROM, USE_DATE_TO,
    BUSI_STTS, NTNL_CD,
    FREE_USE_DATE_FROM, FREE_USE_DATE_TO,
    BANK_CD, ACCT_NO, CHNL_ID,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM,
    OVERDUE_YN, CANC_DATE, WIZ_RUN_YN, CMPN_STAMP,
    DEL_YN, BUSI_NICK_NM, ALLI_CMPN_CD, ALLI_CMPN_NM,
    PRDT_TYPE, OWAC_NM, MEMO, PAID_START_DATE, CMPN_QR_CD_IMG, ERP_CD
) VALUES (
    'VN_VNM0004_000014',
    'HAENGSUNG ELECTRONICS',
    'HAENGSUNG ELECTRONICS',
    '0201635899',
    'HAENGSUNG ELECTRONICS',
    NULL, NULL,
    'wecmshaengsung@gmail.com',
    'LK08-13, Quarter 3, Thoi Son Ward, Dong Thap Province, Vietnam',
    NULL,
    'Dong Thap',
    'Vietnam',
    '',
    to_timestamp('20250819050035','YYYYMMDDHH24MISS'),
    'HAENGSUNG ELECTRONICS',
    '+00842873077799',
    to_timestamp('20250819051051','YYYYMMDDHH24MISS'),
    NULL, NULL, NULL,
    'N', 'VN',
    to_date('20250731','YYYYMMDD'),
    to_date('20260731','YYYYMMDD'),
    '', '', 'VNM0004',
    'SYSTEM',
    to_timestamp('20250819050035','YYYYMMDDHH24MISS'),
    'wecmshaengsung@gmail.com',
    to_timestamp('20251208022743','YYYYMMDDHH24MISS'),
    '', NULL, 'N', NULL,
    'N', NULL, NULL, NULL,
    'H', '', NULL, NULL, '', NULL
)
ON CONFLICT (BIZ_KEY) DO UPDATE SET
    CMPN_NM             = EXCLUDED.CMPN_NM,
    CMPN_ENM            = EXCLUDED.CMPN_ENM,
    TAX_ID              = EXCLUDED.TAX_ID,
    RPPR_NM             = EXCLUDED.RPPR_NM,
    TEL_NO              = EXCLUDED.TEL_NO,
    FXNO                = EXCLUDED.FXNO,
    EML                 = EXCLUDED.EML,
    RPRS_BSUN_POST_ADRS = EXCLUDED.RPRS_BSUN_POST_ADRS,
    RPRS_BSUN_DTL_ADRS  = EXCLUDED.RPRS_BSUN_DTL_ADRS,
    RPRS_BSUN_CITY      = EXCLUDED.RPRS_BSUN_CITY,
    RPRS_BSUN_ST        = EXCLUDED.RPRS_BSUN_ST,
    CMPN_LOGO           = EXCLUDED.CMPN_LOGO,
    JOIN_DT             = EXCLUDED.JOIN_DT,
    FST_JOIN_NM         = EXCLUDED.FST_JOIN_NM,
    FST_JOIN_TEL        = EXCLUDED.FST_JOIN_TEL,
    FST_USE_DATE        = EXCLUDED.FST_USE_DATE,
    LST_PAY_DATE        = EXCLUDED.LST_PAY_DATE,
    USE_DATE_FROM       = EXCLUDED.USE_DATE_FROM,
    USE_DATE_TO         = EXCLUDED.USE_DATE_TO,
    BUSI_STTS           = EXCLUDED.BUSI_STTS,
    NTNL_CD             = EXCLUDED.NTNL_CD,
    FREE_USE_DATE_FROM  = EXCLUDED.FREE_USE_DATE_FROM,
    FREE_USE_DATE_TO    = EXCLUDED.FREE_USE_DATE_TO,
    BANK_CD             = EXCLUDED.BANK_CD,
    ACCT_NO             = EXCLUDED.ACCT_NO,
    CHNL_ID             = EXCLUDED.CHNL_ID,
    REG_ID              = EXCLUDED.REG_ID,
    REG_DTM             = EXCLUDED.REG_DTM,
    MOD_ID              = EXCLUDED.MOD_ID,
    MOD_DTM             = EXCLUDED.MOD_DTM,
    OVERDUE_YN          = EXCLUDED.OVERDUE_YN,
    CANC_DATE           = EXCLUDED.CANC_DATE,
    WIZ_RUN_YN          = EXCLUDED.WIZ_RUN_YN,
    CMPN_STAMP          = EXCLUDED.CMPN_STAMP,
    DEL_YN              = EXCLUDED.DEL_YN,
    BUSI_NICK_NM        = EXCLUDED.BUSI_NICK_NM,
    ALLI_CMPN_CD        = EXCLUDED.ALLI_CMPN_CD,
    ALLI_CMPN_NM        = EXCLUDED.ALLI_CMPN_NM,
    PRDT_TYPE           = EXCLUDED.PRDT_TYPE,
    OWAC_NM             = EXCLUDED.OWAC_NM,
    MEMO                = EXCLUDED.MEMO,
    PAID_START_DATE     = EXCLUDED.PAID_START_DATE,
    CMPN_QR_CD_IMG      = EXCLUDED.CMPN_QR_CD_IMG,
    ERP_CD              = EXCLUDED.ERP_CD;

-- ----------------------------------------------------------------------------
-- 3. USER_INFM — 54 users linked to VN_VNM0004_000001 (48) and 000014 (6).
--    DEV snapshot 2026-05-12. Column order matches the refactor schema
--    (V1 + V7 + V28). LST_LGN_DATE / MOB_LST_LGN_DATE / PW_CHG_REQ_SEQ_NO /
--    BIZ_KEY / EMPL_NO are NULL across the board on DEV, so omitted from
--    the VALUES list and the SET clause.
-- ----------------------------------------------------------------------------
INSERT INTO USER_INFM (
    USER_ID, USER_NM, CLPH_NO, EML, USER_PW, USER_IMG,
    ACTV_YN, LOCK_YN, REG_ID, REG_DTM, MOD_ID, MOD_DTM,
    DEL_YN, DEPT_NM, POSI_NM
) VALUES
    -- ---- BIZ_KEY = VN_VNM0004_000001 (48 users) ----
    ('DevTesting@gmail.com', 'DevTesting', 'OM966wYAjArwrl2vp95wCA==', 'DevTesting@gmail.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250808011947','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250808011947','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('Dv@gmail.com', 'Dev test', 'u6NsOBbRv0e+qnjRWxBTOg==', 'Dev@gmail.com',
     '59NzSQ7bXLa3t9WO0bFRNPZlIyTfF4wR6XcHbWUucMs=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404063611','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404063611','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('Ni@gmail.com', 'Nika', 'MAWxmlGWE4+HoirJEl3eOw==', 'Nika@gmail.com',
     '6HfSPxJ1UNMVkHpb97OL+ybzcD4Q1jxGJ6TalKcYe3s=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404025746','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404025746','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('Testdev@gmail.com', 'Test Dev', 'iNLFUxFbrxTjB9whMqnfTw==', 'Testdev@gmail.com',
     'zycQ9HoDeDgc8pkcZea4oov1trpNvMtQPDXgOMSDWE4=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250406045354','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250406045354','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('Testing012@gmail.com', 'Testing', 'FUQTdNyoPxwQ+iiUg7bF9g==', 'Testing012@gmail.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250619063257','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250619063338','YYYYMMDDHH24MISS'),
     'N', 'B2B R&D', 'Software Engineer'),
    ('Testing2025@gmail.com', 'Testing', 'C60ROJHZ1q+DvxuHlrJS0Q==', 'Testing2025@gmail.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250619063132','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250619063222','YYYYMMDDHH24MISS'),
     'N', 'Global Product', 'Web Developer'),
    ('WeCms226@gmail.com', 'WeCms226', 'HpMOsMYEXdUyU+FqtDKJTA==', 'WeCms226@gmail.com',
     'A1Y0zP/7eg9r8R3nLZ0/xyK0YElkYiAWmjFqSr8tQzY=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250108080736','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20251205042448','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('WeCms228@gmail.com', 'WeCms228', '56XpKENjJu7b8hlUB42Hrw==', 'WeCms228@gmail.com',
     'XREr5ikRENAN412lzPRCIuAwMzqNjxGpOOYpA6lDZ5E=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250224075132','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404062529','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('WeCmsWWW@gmail.com', 'WeCmsWWW', 'kgDKv/IQmnJtjCHnv8aWaA==', 'WeCmsWWW@gmail.com',
     'ilc7Ihe0PlOLK5oE518pIwVv7SVbQpKwdlQMkDaNcqs=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250327112816','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20251205042402','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('arthurmorgan@rdr2.com', 'Arthur', 'w9enpIHtcp8a2BOSoI023g==', 'arthurmorgan@rdr2.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250925091252','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20260223074858','YYYYMMDDHH24MISS'),
     'N', 'Global Product Center', 'Web Developer'),
    ('boo@gmail.com', 'book', 'wp2vJpPL2jgs5j66kSELyA==', 'book@gmail.com',
     'kR6dXsAAMrz5nOwherRBEgCVQRNV2bs8qWyYp0ZFCbY=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404073026','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404073026','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('chanyouvita.kosign@gmail.com', 'Chan Youvita', '5s3hGJCIQMt0op+pZGU42Q==', 'chanyouvita.kosign@gmail.com',
     'QMC7BUvwfVxhTIqjyCfOXaIOr0wEozjzRLm/kVBcbM4=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250828031605','YYYYMMDDHH24MISS'),
     'chanyouvita.kosign@gmail.com', to_timestamp('20260217014154','YYYYMMDDHH24MISS'),
     'N', 'Global Product Center', 'QA'),
    ('chorvy@gmail.com', 'chorvy', 'S+QeWvVYr84sKyDUZYOgNQ==', 'chorvy@gmail.com',
     'Ex0D/FKTlcmNJDnT+dgB0EbTqS/c6gTC4mCplRXvHpM=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250406052238','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250407013348','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('dev1@gmail.com', 'dev-1', 'UFsMoeJ4YipDhmi+y9jWOQ==', 'dev@gmail.com',
     'NEcT0sVTNcI8LvFPsYTCjmaD1gEfFxHlIdekvU3xwA4=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250727105656','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250727105708','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('dev2@gmail.com', 'dev-2', 'UFsMoeJ4YipDhmi+y9jWOQ==', 'dev@gmail.com',
     'NEcT0sVTNcI8LvFPsYTCjmaD1gEfFxHlIdekvU3xwA4=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250727105750','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250727105828','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('devteam@gmail.com', 'Dev', 'Ose8nj97YRi633eBLOA2Zg==', 'devteam@gmail.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250408093533','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250408093533','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('hel@gmail.com', 'hello', '4ITyPKSvDgRo+VT2UIRciQ==', 'hello@gmail.com',
     'qZeqF/a2kwczfaxooL/DgGeyrZd+XSPK2IH0Bw+5sYA=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404023602','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404023602','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('hem@gmail.com', 'hoell', 'qlz2GTAkF3dttUo/Cz5rHg==', 'hoe@gmail.com',
     '5/1WfLgkDiKC/JImdI8YRmeKEE7j4VyPpiqpjwZgioQ=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404072137','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20251205035315','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('hok@gmail.com', 'hooh', 'bxbOV0qBkHW5gDzZAqThcA==', 'hoho@gmail.com',
     'Tjzhz3la7qOydxaHNbCI8C5B2Y8SnJ+Prb7Euh+lcIo=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404063012','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250806021958','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('im@gmail.com', 'vouyim', '3s7zWygBUFp7ZpFjuGy94A==', 'vouyim@gmail.com',
     'yT2kmBx86YSMSPnF7kxhW40XSXYbW5YKLYMQPiZW4yo=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404072857','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404072857','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('ing@gmail.com', 'setting', '6PgKl1XmxgGXKN4CdRVz5Q==', 'setting@gmail.com',
     'hS9rH0Hyf4vnGlMmEFwa7/sQfIccNZ6cDkiGXDo+VSY=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404073236','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404073236','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('lamine@gmail.com', 'lamine', 'sVSK2Y4hRA0079ICIifldQ==', 'lamine@gmail.com',
     '6HfSPxJ1UNMVkHpb97OL+ybzcD4Q1jxGJ6TalKcYe3s=', NULL, 'Y','N',
     'SYSTEM', to_timestamp('20241030032902','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20260224103901','YYYYMMDDHH24MISS'),
     'N', 'WeCMS', 'Software Engineer'),
    ('lamine_user@gmail.com', 'Dev User', 'UFsMoeJ4YipDhmi+y9jWOQ==', 'lamine_user@gmail.com',
     '6HfSPxJ1UNMVkHpb97OL+ybzcD4Q1jxGJ6TalKcYe3s=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20260109011501','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20260109014353','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('na@gmail.com', 'rerere', 'HYx8CKB/46hVl6aNsgmG0Q==', 'rena@gmail.com',
     'ZLf4QaxOo+D+lGCqvZTXmIHq0LrzBljcXPkvAofgEH0=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404064347','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404064347','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('nguygen@gmail.com', 'nguygen', 'iKkPcF1LWl3hr88jvptJww==', 'nguygen@gmail.com',
     '6HfSPxJ1UNMVkHpb97OL+ybzcD4Q1jxGJ6TalKcYe3s=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20260507075221','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20260507075221','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('ouddomtith@gmail.com', 'ouddom', '8mCkNuc/GuYcPkpksXs+xw==', 'ouddomtith@gmail.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250407044926','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250407044926','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('phearun123@gmail.com', 'Rin Phearun', 'ztCFsNE3eosQ7RzKnBLQdg==', 'phearun@gmail.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250405101602','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250405101602','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('ria@gmail.com', 'rina', '3s7zWygBUFp7ZpFjuGy94A==', 'rina@gmail.com',
     'vb5zRjckb2ADRm7yFRJSMaOSw8sF92gdJSSb9uZuAS0=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404071811','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404071811','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('soknyran.uat@gmail.com', 'Sokny Ran', 'UFsMoeJ4YipDhmi+y9jWOQ==', 'soknyran.uat@gmail.com',
     'gW0hqMIR5C4cqlTAPeU7k3HoXX7fJxGe9AMUJ1AFDWM=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250727105557','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250727105557','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('testweb@gmail.com', 'test web', '3s7zWygBUFp7ZpFjuGy94A==', 'testweb@gmail.com',
     'UlK0xzyKVsGkEq5sZx+bZOF8/XGhulRyBQlbQCCblj4=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250407031758','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250407031758','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('tithouddom@gmail.com', 'Tith Ouddom', 'syPRvy4OH+nb/ldehBtxNA==', 'tithouddom@gmail.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250626060901','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250626060901','YYYYMMDDHH24MISS'),
     'N', 'Global Product', 'Web Developer'),
    ('to@gmail.com', 'Tota', 'LhNdgxzqGtkmgh1HLUZcdA==', 'tota@gmail.com',
     '0Ds1KEcU0vYWINLOmid6bPJURUAzTGsM9ElFGIvW/QY=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404030921','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250404030921','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),
    ('vathna1@gmail.com', 'vathnaa', 'Ova3gFY9LKjMMEQ+kumEvg==', 'vathna1@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250805021605','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250805022041','YYYYMMDDHH24MISS'),
     'N', 'web', 'web'),
    ('vathna2@gmail.com', 'vvathna', 'eW5o1QXlJhVyvy/chWxlbw==', 'vathna2@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'vathna1@gmail.com', to_timestamp('20250808030136','YYYYMMDDHH24MISS'),
     'vathna1@gmail.com', to_timestamp('20250808030136','YYYYMMDDHH24MISS'),
     'N', 'web', 'web'),
    ('vathna@gmail.com', 'vathna', 'MfKubMvpvwEtZ/qbBuDH9w==', 'vathna@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250630014836','YYYYMMDDHH24MISS'),
     'vathna1@gmail.com', to_timestamp('20250808025519','YYYYMMDDHH24MISS'),
     'N', 'R&D', 'Developer'),
    ('vathnadmin@gmail.com', 'vathna', 'H24STx30avoUhiDPesrA/g==', 'vathnadmin@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250814074722','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250814074722','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('vathnae@gmail.com', 'vathna', 'H24STx30avoUhiDPesrA/g==', 'vathnae@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250814041232','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250814041232','YYYYMMDDHH24MISS'),
     'N', 'web', 'web'),
    ('vathnao@gmail.com', 'vathna', 'eW5o1QXlJhVyvy/chWxlbw==', 'vathnao@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250814084957','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250814084957','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('vathnap@gmail.com', 'vathna', 'H24STx30avoUhiDPesrA/g==', 'vathnap@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250814101229','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250814101229','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('vathnaq@gmail.com', 'vathna', 'H24STx30avoUhiDPesrA/g==', 'vathnaq@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250814023538','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250814023538','YYYYMMDDHH24MISS'),
     'N', 'web', 'web'),
    ('vathnas@gmail.com', 'vathna', 'H24STx30avoUhiDPesrA/g==', 'vathnas@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250815023839','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250815023839','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('vathnat@gmail.com', 'vathna', 'H24STx30avoUhiDPesrA/g==', 'vathnat@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250814023019','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250814023019','YYYYMMDDHH24MISS'),
     'N', 'web', 'web'),
    ('vathnatesting1@gmail.com', 'vathna testing1', 'eW5o1QXlJhVyvy/chWxlbw==', 'vathnatesting1@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250806025625','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250806032028','YYYYMMDDHH24MISS'),
     'N', 'web', 'web'),
    ('vathnau@gmail.com', 'vathna', 'H24STx30avoUhiDPesrA/g==', 'vathnau@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250814083903','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250814083903','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('vathnay@gmail.com', 'vathna', 'H24STx30avoUhiDPesrA/g==', 'vathnay@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250814063539','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250814063539','YYYYMMDDHH24MISS'),
     'N', 'web', 'web'),
    ('weCMS@gmail.com', 'Testing', 'Jye+RmlANlfttG06VYmN3w==', 'test@gmail.com',
     'sG9UKea0WT86f+zD3gvnfat3AVBCK1pxNnSwRmiTp+E=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250328030115','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20260413075755','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('youvita@gmail.com', 'Youvita', 'eW5o1QXlJhVyvy/chWxlbw==', 'youvita@gmail.com',
     'lfq+Xre9+H1oqEZ3fghV6nDrXaHMS5IT2d5TMLgaUhw=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20260105031406','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20260109024131','YYYYMMDDHH24MISS'),
     'N', '', ''),
    ('yy@gmail.com', 'chhorvy', '1I3E6Kw4G4TyseML3q3NRw==', 'Chhorvy@gmail.com',
     'C9c3kHvXTfqZo192FDapf5WfUB7GurVx//4RPtzqocg=', NULL, 'Y','N',
     'lamine@gmail.com', to_timestamp('20250404060612','YYYYMMDDHH24MISS'),
     'lamine@gmail.com', to_timestamp('20250610082424','YYYYMMDDHH24MISS'),
     'N', NULL, NULL),

    -- ---- BIZ_KEY = VN_VNM0004_000014 (6 users) ----
    ('dev_hs@gmail.com', 'Sokny Ran', 'eW5o1QXlJhVyvy/chWxlbw==', 'dev_hs@gmail.com',
     'Mq1YvXye/vSyUp7e7ACK8R2zsEYtKRyAgIwK0xAanXc=', NULL, 'Y','N',
     'wecmshaengsung@gmail.com', to_timestamp('20250821113551','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251203092444','YYYYMMDDHH24MISS'),
     'N', '', 'Jinpai Hill ,Taiyang''ao,BaihuaTown,Huidong County, Guangdong Province,China'),
    ('dom@gmail.com', 'dom', 'emQMn34r5W5HTsC3Rfum6w==', 'dom@gmail.com',
     '6HfSPxJ1UNMVkHpb97OL+ybzcD4Q1jxGJ6TalKcYe3s=', NULL, 'Y','N',
     'wecmshaengsung@gmail.com', to_timestamp('20251114064132','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251205062814','YYYYMMDDHH24MISS'),
     'N', 'dev', 'dev'),
    ('kosigntc@gmail.com', 'Chan Youvita', 'eW5o1QXlJhVyvy/chWxlbw==', 'chanyouvita.kosign@gmail.com',
     'QMC7BUvwfVxhTIqjyCfOXaIOr0wEozjzRLm/kVBcbM4=', NULL, 'Y','N',
     'wecmshaengsung@gmail.com', to_timestamp('20251023075530','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251223035048','YYYYMMDDHH24MISS'),
     'N', 'Global Product', 'QA'),
    ('ouddomDev@gmail.com', 'Ouddom', '49NZn0AooG3QvAmvGTb1zg==', 'ouddomDev@gmail.com',
     'NsyZiTG1uM4L9i6YcSA/QxyqPEp3iXIpiBBSWxu3nsU=', NULL, 'Y','N',
     'wecmshaengsung@gmail.com', to_timestamp('20251114033237','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251205043125','YYYYMMDDHH24MISS'),
     'N', 'Global PROD', 'Dev'),
    ('vathnaf@gmail.com', 'vathna', 'eW5o1QXlJhVyvy/chWxlbw==', 'vathnaf@gmail.com',
     'diqyW9PJFFycNgjvnx8Oy7KqajWqotGN/FL4d7sQWpw=', NULL, 'Y','N',
     'wecmshaengsung@gmail.com', to_timestamp('20250822073455','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20251205062222','YYYYMMDDHH24MISS'),
     'N', 'web', 'web'),
    ('wecmshaengsung@gmail.com', 'Admin', '3P8qocGnnbsedGwbi4Eahw==', 'wecmshaengsung@gmail.com',
     'Mq1YvXye/vSyUp7e7ACK8R2zsEYtKRyAgIwK0xAanXc=', NULL, 'Y','N',
     'SYSTEM', to_timestamp('20250819050052','YYYYMMDDHH24MISS'),
     'wecmshaengsung@gmail.com', to_timestamp('20250918081553','YYYYMMDDHH24MISS'),
     'N', 'QA', 'Testing')
ON CONFLICT (USER_ID) DO UPDATE SET
    USER_NM   = EXCLUDED.USER_NM,
    CLPH_NO   = EXCLUDED.CLPH_NO,
    EML       = EXCLUDED.EML,
    USER_PW   = EXCLUDED.USER_PW,     -- intentional: copy DEV hash, overwrite any prior BCrypt/sentinel
    USER_IMG  = EXCLUDED.USER_IMG,
    ACTV_YN   = EXCLUDED.ACTV_YN,
    LOCK_YN   = EXCLUDED.LOCK_YN,
    REG_ID    = EXCLUDED.REG_ID,
    REG_DTM   = EXCLUDED.REG_DTM,
    MOD_ID    = EXCLUDED.MOD_ID,
    MOD_DTM   = EXCLUDED.MOD_DTM,
    DEL_YN    = EXCLUDED.DEL_YN,
    DEPT_NM   = EXCLUDED.DEPT_NM,
    POSI_NM   = EXCLUDED.POSI_NM;

-- ============================================================================
-- Reversal recipe — revert USER_PW to the sentinel pattern
--
-- If you decide to stop committing DEV password hashes to git, ship a
-- follow-up migration (e.g. V84__revert_user_pw_to_sentinel.sql) with:
--
--   UPDATE USER_INFM
--      SET USER_PW = '__BCRYPT_PENDING__'
--    WHERE USER_ID IN (
--        'DevTesting@gmail.com', 'Dv@gmail.com', /* ...all 54 user_ids... */
--    );
--
-- DataSeeder will then re-patch them to BCrypt("Password!1") on next boot.
-- Do NOT edit V83 itself once it has been applied — Flyway will reject the
-- checksum mismatch on every developer's DB.
-- ============================================================================
