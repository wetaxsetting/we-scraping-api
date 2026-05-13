-- ============================================================================
-- V87__contacts_demo_seed.sql
--
-- Demo seed for Settings > Contacts — 30 master rows + 30 addresses +
-- 28 bank accounts + 9 overseas beneficiaries + 30 group links, pulled
-- from JEX DEV (`wecmsvndev` @ 10.254.242.254:54311) on 2026-05-13 via
-- the read-only `postgres_dev` MCP.
--
-- Scope (per Data Migration Rule in `wecms-api-main/CLAUDE.md`):
--   - Tenant pin: BIZ_KEY = 'VN_VNM0004_000001' only.
--   - Row cap: <= 30 per table (DEV master row 30-newest by mod_dtm DESC).
--   - Filter: HAND_TRCO_KEY IS NOT NULL AND coalesce(trco_stts,'') <> 'C'
--     — matches the legacy `comm_0184_001_r001` listing filter so the
--     refactor's list endpoint surfaces the same rows DEV does.
--
-- TRCO_KEY values are real DEV PK values (9212..9322) preserved verbatim
-- so the FK chain (TRCO_INFM <- TRCO_ADDR_INFM / TRCO_ACCT_INFO /
-- TRCO_OVRS_INFM / PGRO_BSAC_LNKN_INFM) stays consistent. Newly-created
-- contacts get values from `sq_trco_key` (V85) starting at 1_000_000,
-- well above the seed range — no collision risk.
--
-- Idempotent: every INSERT carries ON CONFLICT DO NOTHING.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- TRCO_INFM — 30 master rows. Vietnamese diacritics preserved verbatim
-- (file encoding is UTF-8). All 30 rows share TAX_ID='1201496252' (the
-- tenant's tax id), REG_ID='lamine@gmail.com', TRSC_CCY_CD per DEV,
-- TRCO_INQ_YN='Y' (the DEV default).
-- ---------------------------------------------------------------------------
INSERT INTO TRCO_INFM
    (TRCO_KEY, BIZ_KEY, TAX_ID, TRCO_CMNM, TRCO_CMENM, TRCO_TAX_ID,
     TRCO_TYPE, TRCO_STTS, TRSC_CCY_CD, TRCO_INQ_YN, HAND_TRCO_KEY,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('9322', 'VN_VNM0004_000001', '1201496252', 'CÔNG TY TNHH WEBCASH', 'CÔNG TY TNHH WEBCASH', NULL,
     'O', NULL, 'USD', 'Y', 'TEST_CODE_01',
     'lamine@gmail.com', '20260227100240', 'lamine@gmail.com', '20260227100240'),
    ('9321', 'VN_VNM0004_000001', '1201496252', 'Chan Youvita', 'Chan Youvita', NULL,
     'L', NULL, 'USD', 'Y', 'TEST_1',
     'lamine@gmail.com', '20251225031133', 'chanyouvita.kosign@gmail.com', '20251225031133'),
    ('9316', 'VN_VNM0004_000001', '1201496252', 'Hello World', 'Hello World', '000000000001',
     'O', NULL, 'USD', 'Y', 'CC_00002',
     'lamine@gmail.com', '20251205090854', 'lamine@gmail.com', '20251205090854'),
    ('9315', 'VN_VNM0004_000001', '1201496252', 'Jinpai Hill', 'Jinpai Hill', NULL,
     'O', NULL, 'USD', 'Y', 'C00490',
     'lamine@gmail.com', '20251205083609', 'lamine@gmail.com', '20251205083609'),
    ('9314', 'VN_VNM0004_000001', '1201496252', 'Jinpai Hill', 'Jinpai Hill', NULL,
     'O', NULL, 'USD', 'Y', 'C00489',
     'lamine@gmail.com', '20251205070502', 'lamine@gmail.com', '20251205070502'),
    ('9313', 'VN_VNM0004_000001', '1201496252', 'Jinpai Hill', 'Jinpai Hill', NULL,
     'O', NULL, 'USD', 'Y', 'C00488',
     'lamine@gmail.com', '20251205070224', 'lamine@gmail.com', '20251205070224'),
    ('9312', 'VN_VNM0004_000001', '1201496252', 'CÔNG TY TNHH WEBCASH', 'CÔNG TY TNHH WEBCASH', '0201496352-001',
     'O', NULL, 'USD', 'Y', 'CC_000001',
     'lamine@gmail.com', '20251205024749', 'lamine@gmail.com', '20251205024749'),
    ('9311', 'VN_VNM0004_000001', '1201496252', 'Test Contact 001', 'Test Contact 001', NULL,
     'O', NULL, 'VND', 'Y', 'YT63523',
     'lamine@gmail.com', '20251204063541', 'lamine@gmail.com', '20251204063541'),
    ('9310', 'VN_VNM0004_000001', '1201496252', 'Chan', 'Chan', NULL,
     'L', NULL, 'VND', 'Y', 'C000001',
     'lamine@gmail.com', '20251201074033', 'lamine@gmail.com', '20251201074033'),
    ('9308', 'VN_VNM0004_000001', '1201496252', 'User Test', 'User Test', NULL,
     'L', NULL, 'VND', 'Y', 'C8263',
     'lamine@gmail.com', '20251030061820', 'lamine@gmail.com', '20251030061820'),
    ('9307', 'VN_VNM0004_000001', '1201496252', 'vathna testttt', 'vathna testttt', NULL,
     'L', NULL, 'VND', 'Y', 'C092',
     'lamine@gmail.com', '20251022025054', 'lamine@gmail.com', '20251022025054'),
    ('9306', 'VN_VNM0004_000001', '1201496252', 'TEST_0001', 'TEST_0001', NULL,
     'L', NULL, 'VND', 'Y', 'TEST_0001',
     'lamine@gmail.com', '20251022023833', 'lamine@gmail.com', '20251022023833'),
    ('9305', 'VN_VNM0004_000001', '1201496252', 'vathna testing', 'vathna testing', NULL,
     'L', NULL, 'VND', 'Y', 'C091',
     'lamine@gmail.com', '20251022013805', 'lamine@gmail.com', '20251022013805'),
    ('9304', 'VN_VNM0004_000001', '1201496252', 'Vathna', 'Vathna', NULL,
     'L', NULL, 'VND', 'Y', 'CP002',
     'lamine@gmail.com', '20251021093840', 'lamine@gmail.com', '20251021093840'),
    ('9301', 'VN_VNM0004_000001', '1201496252', 'CÔNG TY TNHH WEBCASH', 'CÔNG TY TNHH WEBCASH', '0201496352-801',
     'O', NULL, 'USD', 'Y', 'C005002',
     'lamine@gmail.com', '20251013081234', 'lamine@gmail.com', '20251013081234'),
    ('9272', 'VN_VNM0004_000001', '1201496252', 'Test34234', 'Test34234', NULL,
     'L', NULL, 'VND', 'Y', 'T435345',
     'lamine@gmail.com', '20251003072439', 'lamine@gmail.com', '20251003072439'),
    ('9271', 'VN_VNM0004_000001', '1201496252', 'Test33423', 'Test33423', NULL,
     'L', NULL, 'VND', 'Y', '0004',
     'lamine@gmail.com', '20251003065926', 'lamine@gmail.com', '20251003065926'),
    ('9269', 'VN_VNM0004_000001', '1201496252', 'Shin Ramyun', 'Shin Ramyun', NULL,
     'L', NULL, 'VND', 'Y', 'A01928',
     'lamine@gmail.com', '20251002074859', 'lamine@gmail.com', '20251002074859'),
    ('9268', 'VN_VNM0004_000001', '1201496252', 'Leno Ultra', 'Leno Ultra', NULL,
     'L', NULL, 'VND', 'Y', 'A0001',
     'lamine@gmail.com', '20251002064357', 'lamine@gmail.com', '20251002064357'),
    ('9267', 'VN_VNM0004_000001', '1201496252', 'CVA', 'CVA', NULL,
     'L', NULL, 'VND', 'Y', 'V06352',
     'lamine@gmail.com', '20251002063224', 'lamine@gmail.com', '20251002063224'),
    ('9249', 'VN_VNM0004_000001', '1201496252', 'space test', 'space test', NULL,
     'L', NULL, 'VND', 'Y', 'jfsd2342',
     'lamine@gmail.com', '20250926090935', 'lamine@gmail.com', '20250926090935'),
    ('9246', 'VN_VNM0004_000001', '1201496252', 'QA', 'QA', NULL,
     'O', NULL, 'VND', 'Y', 'TEST_CODE',
     'lamine@gmail.com', '20250918025419', 'lamine@gmail.com', '20250918025419'),
    ('9245', 'VN_VNM0004_000001', '1201496252', 'TONG6565', 'TONG6565', '3243242343-243',
     'L', NULL, 'VND', 'Y', 'TONG6565',
     'lamine@gmail.com', '20250912095118', 'lamine@gmail.com', '20250912095118'),
    ('9227', 'VN_VNM0004_000001', '1201496252', 'CÔNG VIỆT NAM', 'CÔNG VIỆT NAM', '1201482652',
     'L', NULL, 'VND', 'Y', 'C08801',
     'lamine@gmail.com', '20250908114546', 'lamine@gmail.com', '20250908114546'),
    ('9221', 'VN_VNM0004_000001', '1201496252', 'SDFjkkjd', 'SDFjkkjd', NULL,
     'L', NULL, 'VND', 'Y', 'UIUY23432',
     'lamine@gmail.com', '20250908102222', 'lamine@gmail.com', '20250908102222'),
    ('9218', 'VN_VNM0004_000001', '1201496252', 'asdlkflk23', 'asdlkflk23', NULL,
     'L', NULL, 'VND', 'Y', 'kdhjh213',
     'lamine@gmail.com', '20250908100401', 'lamine@gmail.com', '20250908100401'),
    ('9215', 'VN_VNM0004_000001', '1201496252',
     'CHI NHÁNH CẤP NƯỚC KHU LIÊN HỢP - CÔNG TY CP-TỔNG CÔNG TY NƯỚC- MÔI TRƯỜNG BÌNH DƯƠNG',
     'CHI NHÁNH CẤP NƯỚC KHU LIÊN HỢP - CÔNG TY CP-TỔNG CÔNG TY NƯỚC- MÔI TRƯỜNG BÌNH DƯƠNG',
     NULL,
     'L', NULL, 'VND', 'Y', 'VV0035',
     'lamine@gmail.com', '20250908091313', 'lamine@gmail.com', '20250908091313'),
    ('9214', 'VN_VNM0004_000001', '1201496252', 'CÔNG TY NAM 4', 'CÔNG TY NAM 4', '1201474255',
     'L', NULL, 'VND', 'Y', 'C04005',
     'lamine@gmail.com', '20250908071021', 'lamine@gmail.com', '20250908071021'),
    ('9213', 'VN_VNM0004_000001', '1201496252', 'CÔNG TY NAM 3', 'CÔNG TY NAM 3', '1201474254',
     'L', NULL, 'VND', 'Y', 'C04004',
     'lamine@gmail.com', '20250908071021', 'lamine@gmail.com', '20250908071021'),
    ('9212', 'VN_VNM0004_000001', '1201496252', 'CÔNG TY NAM 2', 'CÔNG TY NAM 2', '1201474253',
     'L', NULL, 'VND', 'Y', 'C04003',
     'lamine@gmail.com', '20250908071021', 'lamine@gmail.com', '20250908071021')
ON CONFLICT (TRCO_KEY) DO NOTHING;

-- ---------------------------------------------------------------------------
-- TRCO_ADDR_INFM — 30 addresses (15 contacts × current + previous,
-- mostly). Discriminator is DISP_ORD (1=current, 2=previous), NOT
-- TRCO_ADDR_KEY (OQ-8 resolved 2026-05-12).
-- ---------------------------------------------------------------------------
INSERT INTO TRCO_ADDR_INFM
    (TRCO_KEY, BIZ_KEY, TRCO_ADDR_KEY, TRCO_BSUN_POST_ADRS, DISP_ORD,
     ISSU_DT, DUE_DT, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('9322', 'VN_VNM0004_000001', '1', 'LK08-13, Khu phố 3, Phường 6, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 1, '20260227', '20260228', 'lamine@gmail.com', '20260227100240', 'lamine@gmail.com', '20260227100240'),
    ('9322', 'VN_VNM0004_000001', '2', 'Khu phố 3, Phường 3, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 2, '20260227', '20220831', 'lamine@gmail.com', '20260227100240', 'lamine@gmail.com', '20260227100240'),
    ('9321', 'VN_VNM0004_000001', '1', 'LK08-13, Khu phố 3, Phường 6, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 1, '20260225', '20260225', 'lamine@gmail.com', '20251225031133', 'chanyouvita.kosign@gmail.com', '20260225085926'),
    ('9321', 'VN_VNM0004_000001', '2', 'Khu phố 3, Phường 3, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 2, '20260225', '20220831', 'lamine@gmail.com', '20251225031133', 'chanyouvita.kosign@gmail.com', '20260225085926'),
    ('9316', 'VN_VNM0004_000001', '1', 'LK08-13, Khu phố 3, Phường 6, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 1, '20251205', '20251206', 'lamine@gmail.com', '20251205090854', 'lamine@gmail.com', '20251205090854'),
    ('9316', 'VN_VNM0004_000001', '2', 'Khu phố 3, Phường 3, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 2, '20251205', '20220831', 'lamine@gmail.com', '20251205090854', 'lamine@gmail.com', '20251205090854'),
    ('9315', 'VN_VNM0004_000001', '1', E'Jinpai Hill ,Taiyang\'ao,BaihuaTown,Huidong County, Guangdong Province,China', 1, '20251205', '20251206', 'lamine@gmail.com', '20251205083609', 'lamine@gmail.com', '20251205083609'),
    ('9315', 'VN_VNM0004_000001', '2', E'Jinpai Hill ,Taiyang\'ao,BaihuaTown,Huidong County, Guangdong Province,China', 2, '20251205', '20220831', 'lamine@gmail.com', '20251205083609', 'lamine@gmail.com', '20251205083609'),
    ('9314', 'VN_VNM0004_000001', '1', E'Jinpai Hill ,Taiyang\'ao,BaihuaTown,Huidong County, Guangdong Province,China', 1, '20251205', '20251206', 'lamine@gmail.com', '20251205070502', 'lamine@gmail.com', '20251205070502'),
    ('9314', 'VN_VNM0004_000001', '2', E'Jinpai Hill ,Taiyang\'ao,BaihuaTown,Huidong County, Guangdong Province,China', 2, '20251205', '20220831', 'lamine@gmail.com', '20251205070502', 'lamine@gmail.com', '20251205070502'),
    ('9313', 'VN_VNM0004_000001', '1', E'Jinpai Hill ,Taiyang\'ao,BaihuaTown,Huidong County, Guangdong Province,China', 1, '20251205', '20251206', 'lamine@gmail.com', '20251205070224', 'lamine@gmail.com', '20251205070224'),
    ('9313', 'VN_VNM0004_000001', '2', E'Jinpai Hill ,Taiyang\'ao,BaihuaTown,Huidong County, Guangdong Province,China', 2, '20251205', '20220831', 'lamine@gmail.com', '20251205070224', 'lamine@gmail.com', '20251205070224'),
    ('9312', 'VN_VNM0004_000001', '1', 'LK08-13, Khu phố 3, Phường 6, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 1, '20251205', '20251206', 'lamine@gmail.com', '20251205024749', 'lamine@gmail.com', '20251205024749'),
    ('9312', 'VN_VNM0004_000001', '2', 'Khu phố 3, Phường 3, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 2, '20251205', '20220831', 'lamine@gmail.com', '20251205024749', 'lamine@gmail.com', '20251205024749'),
    ('9311', 'VN_VNM0004_000001', '1', '', 1, '20251204', '20251205', 'lamine@gmail.com', '20251204063541', 'lamine@gmail.com', '20251204063541'),
    ('9308', 'VN_VNM0004_000001', '1', '', 1, '20251030', '20251031', 'lamine@gmail.com', '20251030061820', 'lamine@gmail.com', '20251030061820'),
    ('9307', 'VN_VNM0004_000001', '1', 'as', 1, '20251022', '20251023', 'lamine@gmail.com', '20251022025054', 'lamine@gmail.com', '20251022025054'),
    ('9305', 'VN_VNM0004_000001', '1', 'PP', 1, '20251022', '20251022', 'lamine@gmail.com', '20251022013805', 'lamine@gmail.com', '20251022025003'),
    ('9304', 'VN_VNM0004_000001', '1', 'Phnom Penh testtt', 1, '20251021', '20251022', 'lamine@gmail.com', '20251021093840', 'lamine@gmail.com', '20251021093840'),
    ('9301', 'VN_VNM0004_000001', '1', 'LK08-13, Khu phố 3, Phường 6, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 1, '20251013', '20251014', 'lamine@gmail.com', '20251013081234', 'lamine@gmail.com', '20251013081234'),
    ('9301', 'VN_VNM0004_000001', '2', 'Khu phố 3, Phường 3, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 2, '20251013', '20220831', 'lamine@gmail.com', '20251013081234', 'lamine@gmail.com', '20251013081234'),
    ('9272', 'VN_VNM0004_000001', '1', 'PP', 1, '20251003', '20251003', 'lamine@gmail.com', '20251003072440', 'lamine@gmail.com', '20251003072607'),
    ('9272', 'VN_VNM0004_000001', '2', 'TT', 2, '20251003', '20251031', 'lamine@gmail.com', '20251003072440', 'lamine@gmail.com', '20251003072607'),
    ('9271', 'VN_VNM0004_000001', '1', '', 1, '20251003', '20251004', 'lamine@gmail.com', '20251003065926', 'lamine@gmail.com', '20251003065926'),
    ('9267', 'VN_VNM0004_000001', '1', '', 1, '20251002', '20251003', 'lamine@gmail.com', '20251002063224', 'lamine@gmail.com', '20251002063224'),
    ('9249', 'VN_VNM0004_000001', '1', '', 1, '20250926', '20250927', 'lamine@gmail.com', '20250926090935', 'lamine@gmail.com', '20250926090935'),
    ('9245', 'VN_VNM0004_000001', '1', '', 1, '20250912', '20250913', 'lamine@gmail.com', '20250912095118', 'lamine@gmail.com', '20250912095118'),
    ('9227', 'VN_VNM0004_000001', '1', 'LK08-13, Khu phố 3, Phường 6, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 1, '20250908', '20250909', 'lamine@gmail.com', '20250908114546', 'lamine@gmail.com', '20250908114546'),
    ('9227', 'VN_VNM0004_000001', '2', 'Khu phố 3, Phường 3, Thành phố Mỹ Tho, Tỉnh Tiền Giang, Việt Nam', 2, '20250908', '20220831', 'lamine@gmail.com', '20250908114546', 'lamine@gmail.com', '20250908114546'),
    ('9221', 'VN_VNM0004_000001', '1', '', 1, '20250908', '20250909', 'lamine@gmail.com', '20250908102222', 'lamine@gmail.com', '20250908102222')
ON CONFLICT (TRCO_KEY, BIZ_KEY, TRCO_ADDR_KEY) DO NOTHING;

-- ---------------------------------------------------------------------------
-- TRCO_ACCT_INFO — 28 bank accounts. BANK_NM stored verbatim from DEV
-- (the wb_get_napas_bank_nm_or_citad_bank_nm function in V86 resolves
-- the name dynamically; the stored BANK_NM is the legacy display copy).
-- ---------------------------------------------------------------------------
INSERT INTO TRCO_ACCT_INFO
    (BIZ_KEY, TRCO_KEY, TRCO_ACCT_KEY, ACCT_NO, DISP_ORD, TRCO_NM, TRCO_ENM,
     BANK_NM, NAPAS_BANK_CD, CITAD_BANK_CD, FINC_MDA_OWNER_NM, ACCT_CCY_CD,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('VN_VNM0004_000001', '9321', '1', '700005285299', 1, 'Chan Youvita', 'Chan Youvita', 'ShinhanBank - Shinhan Bank', '970424', NULL, 'SHINHAN', 'USD', 'lamine@gmail.com', '20251225031133', 'chanyouvita.kosign@gmail.com', '20260225085926'),
    ('VN_VNM0004_000001', '9321', '2', '100912150384', 1, NULL, NULL, 'WooriBank - Woori Bank', '970457', NULL, 'WOORI BANK', 'USD', 'lamine@gmail.com', '20251225041430', 'chanyouvita.kosign@gmail.com', '20260225085926'),
    ('VN_VNM0004_000001', '9321', '3', '1051996380', 1, NULL, NULL, 'VCBNeo - Vietcombank Neo Limited', '970444', NULL, 'VIETCOM BANK', 'USD', 'lamine@gmail.com', '20251225042714', 'chanyouvita.kosign@gmail.com', '20260225085926'),
    ('VN_VNM0004_000001', '9321', '4', '111002954854', 1, NULL, NULL, 'VietBank - Vietnam Thuong Tin Commercial Joint Stock Bank', '970433', NULL, 'VIET BANK THOUONG', 'USD', 'lamine@gmail.com', '20251225042714', 'chanyouvita.kosign@gmail.com', '20260225085926'),
    ('VN_VNM0004_000001', '9310', '1', '1000223344', 1, 'Chan', 'Chan', 'ABBank - ABBANK', '970425', NULL, E'~R!e@c^i*p=i{e}n[t]n\'a"m<e>', 'USD', 'lamine@gmail.com', '20251201074033', 'lamine@gmail.com', '20251204023946'),
    ('VN_VNM0004_000001', '9308', '1', '884739434', 1, 'User Test', 'User Test', 'WooriBank - Woori', '970457', NULL, '&DU', 'USD', 'lamine@gmail.com', '20251030061820', 'lamine@gmail.com', '20251030061820'),
    ('VN_VNM0004_000001', '9307', '1', '1111111111111111', 1, 'vathna testttt', 'vathna testttt', 'ABBank - ABBANK', '970425', NULL, 'va', 'USD', 'lamine@gmail.com', '20251022025054', 'lamine@gmail.com', '20251022025054'),
    ('VN_VNM0004_000001', '9307', '2', '2222222222', 2, 'vathna testttt', 'vathna testttt', 'ACB - ACB', '970416', NULL, 'as', 'USD', 'lamine@gmail.com', '20251022025054', 'lamine@gmail.com', '20251022025054'),
    ('VN_VNM0004_000001', '9306', '1', '123456789', 1, 'TEST_0001', 'TEST_0001', 'ABBank - ABBANK', '970425', NULL, 'TEST', 'USD', 'lamine@gmail.com', '20251022023833', 'lamine@gmail.com', '20251022024239'),
    ('VN_VNM0004_000001', '9305', '1', '1111111111111111', 1, 'vathna testing', 'vathna testing', 'ABBank - ABBANK', '970425', NULL, 'vathna', 'USD', 'lamine@gmail.com', '20251022013805', 'lamine@gmail.com', '20251022025003'),
    ('VN_VNM0004_000001', '9305', '2', '2222222222222', 1, NULL, NULL, 'ACB - ACB', '970416', NULL, 'vathnaaaa', 'USD', 'lamine@gmail.com', '20251022025003', 'lamine@gmail.com', '20251022025003'),
    ('VN_VNM0004_000001', '9304', '1', '654543345345', 1, 'Vathna', 'Vathna', 'ABBank - ABBANK', '970425', NULL, 'testttt', 'USD', 'lamine@gmail.com', '20251021093840', 'lamine@gmail.com', '20251021093840'),
    ('VN_VNM0004_000001', '9272', '1', '23423423432', 1, NULL, NULL, 'ABBank - ABBANK', '970425', NULL, '', 'VND', 'lamine@gmail.com', '20251003072607', 'lamine@gmail.com', '20251003072607'),
    ('VN_VNM0004_000001', '9269', '1', '20009113', 1, 'Shin Ramyun', 'Shin Ramyun', 'ShinhanBank - Shinhan Bank', '970424', NULL, '', 'VND', 'lamine@gmail.com', '20251002074859', 'lamine@gmail.com', '20251009043831'),
    ('VN_VNM0004_000001', '9269', '2', '8888777787', 1, NULL, NULL, 'MegaBank - Mega ICBC', NULL, '79623001', 'Shin Ramyun', 'VND', 'lamine@gmail.com', '20251009043831', 'lamine@gmail.com', '20251009043831'),
    ('VN_VNM0004_000001', '9268', '1', '192837465', 1, 'Leno Ultra', 'Leno Ultra', 'MegaBank - Mega International Commercial Bank Co., Ltd', NULL, '79623001', 'Huyền Thảo', 'USD', 'lamine@gmail.com', '20251002064357', 'lamine@gmail.com', '20260325084050'),
    ('VN_VNM0004_000001', '9268', '2', '987654321', 1, 'Leno Ultra', 'Leno Ultra', 'ABBank - An Binh Commercial Joint Stock Bank', '970425', NULL, '', 'VND', 'lamine@gmail.com', '20251002064357', 'lamine@gmail.com', '20260325084050'),
    ('VN_VNM0004_000001', '9268', '3', '987654321', 1, NULL, NULL, 'MegaBank - Mega International Commercial Bank Co., Ltd', NULL, '79623001', 'Testing Recipient', 'VND', 'lamine@gmail.com', '20251009040816', 'lamine@gmail.com', '20260325084050'),
    ('VN_VNM0004_000001', '9268', '4', '987654321', 1, NULL, NULL, 'ABC - Agricultural Bank Of China', NULL, '01664001', 'Testing Recipient 2', 'VND', 'lamine@gmail.com', '20251009042348', 'lamine@gmail.com', '20260325084050'),
    ('VN_VNM0004_000001', '9267', '1', '111111111111', 1, 'CVA', 'CVA', 'ABBank - ABBANK', '970425', NULL, '', 'VND', 'lamine@gmail.com', '20251002063224', 'lamine@gmail.com', '20251002063224'),
    ('VN_VNM0004_000001', '9245', '1', '323423343', 1, 'TONG6565', 'TONG6565', 'ABBank - ABBANK', '970425', NULL, '', 'VND', 'lamine@gmail.com', '20250912095118', 'lamine@gmail.com', '20250912095118'),
    ('VN_VNM0004_000001', '9227', '1', '123456789012', 1, 'CÔNG VIỆT NAM', 'CÔNG VIỆT NAM', 'CitiBank-HN', NULL, '01605001', NULL, 'VND', 'lamine@gmail.com', '20250908114546', 'lamine@gmail.com', '20250908114546'),
    ('VN_VNM0004_000001', '9221', '1', '34243243231', 1, 'SDFjkkjd', 'SDFjkkjd', 'CitiBank-HCM - Citibank - HCM', NULL, '79654001', '', 'VND', 'lamine@gmail.com', '20250908102222', 'lamine@gmail.com', '20250908102222'),
    ('VN_VNM0004_000001', '9218', '1', '7326346833', 1, 'asdlkflk23', 'asdlkflk23', 'CitiBank - Citi Bank', '533948', NULL, '', 'VND', 'lamine@gmail.com', '20250908100401', 'lamine@gmail.com', '20250908100401'),
    ('VN_VNM0004_000001', '9215', '1', '0281000579779', 1,
     'CHI NHÁNH CẤP NƯỚC KHU LIÊN HỢP - CÔNG TY CP-TỔNG CÔNG TY NƯỚC- MÔI TRƯỜNG BÌNH DƯƠNG',
     'CHI NHÁNH CẤP NƯỚC KHU LIÊN HỢP - CÔNG TY CP-TỔNG CÔNG TY NƯỚC- MÔI TRƯỜNG BÌNH DƯƠNG',
     'VCB', '970436', NULL, NULL, 'VND', 'lamine@gmail.com', '20250908091313', 'lamine@gmail.com', '20250908091313'),
    ('VN_VNM0004_000001', '9214', '1', '123456789015', 1, 'CÔNG TY NAM 4', 'CÔNG TY NAM 4', 'SHB', '970443', NULL, NULL, NULL, 'lamine@gmail.com', '20250908071021', 'lamine@gmail.com', '20250908071021'),
    ('VN_VNM0004_000001', '9213', '1', '123456789014', 1, 'CÔNG TY NAM 3', 'CÔNG TY NAM 3', 'SHB', '970443', NULL, NULL, NULL, 'lamine@gmail.com', '20250908071021', 'lamine@gmail.com', '20250908071021'),
    ('VN_VNM0004_000001', '9212', '1', '123456789013', 1, 'CÔNG TY NAM 2', 'CÔNG TY NAM 2', 'MSB', '970426', NULL, NULL, NULL, 'lamine@gmail.com', '20250908071021', 'lamine@gmail.com', '20250908071021')
ON CONFLICT (BIZ_KEY, TRCO_KEY, TRCO_ACCT_KEY) DO NOTHING;

-- ---------------------------------------------------------------------------
-- TRCO_OVRS_INFM — 9 overseas beneficiary rows.
-- ---------------------------------------------------------------------------
INSERT INTO TRCO_OVRS_INFM
    (TRCO_KEY, BIZ_KEY, TRCO_OVRS_KEY, BENE_NAME, BIC_CODE, BENE_ACCT_NO, BENE_ADDR,
     BENE_BANK_NAME, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('9322', 'VN_VNM0004_000001', '39', 'NGUYEN VAN NAPAS', 'SHBKKRSEXXX', '180012992264', 'Korea', 'SHINHAN', 'lamine@gmail.com', '20260227100240', 'lamine@gmail.com', '20260227100240'),
    ('9316', 'VN_VNM0004_000001', '38', 'Hello World', 'Hello World', 'Hello World', 'Hello World', 'Hello World', 'lamine@gmail.com', '20251205090854', 'lamine@gmail.com', '20251205090854'),
    ('9315', 'VN_VNM0004_000001', '37', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'lamine@gmail.com', '20251205083609', 'lamine@gmail.com', '20251205083609'),
    ('9314', 'VN_VNM0004_000001', '36', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'lamine@gmail.com', '20251205070502', 'lamine@gmail.com', '20251205070502'),
    ('9313', 'VN_VNM0004_000001', '35', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'Jinpai Hill', 'lamine@gmail.com', '20251205070224', 'lamine@gmail.com', '20251205070224'),
    ('9312', 'VN_VNM0004_000001', '34',
     E'Công ty Cổ phần Dịch vụ Du lịch và Lữ hành A`~!@#$C%^&*(m)-_=D+{n}[]\\|/?\'";:.,><B',
     'SHBKKRSEXXX', '180012992264', 'Korea', 'SHINHAN', 'lamine@gmail.com', '20251205024749', 'lamine@gmail.com', '20251205024749'),
    ('9311', 'VN_VNM0004_000001', '33', 'V a t h', 'SHBKVNVXXXX', '83838382777', 'Phnom Penh', 'Shinhan', 'lamine@gmail.com', '20251204063541', 'lamine@gmail.com', '20251204063541'),
    ('9301', 'VN_VNM0004_000001', '32', 'NGUYEN VAN NAPAS', 'SHBKKRSEXXX', '180012992264', 'Korea', 'SHINHAN', 'lamine@gmail.com', '20251013081234', 'lamine@gmail.com', '20251013081234'),
    ('9246', 'VN_VNM0004_000001', '24', 'SHINHAN BANK', 'SHBKKRSEXXX', '32423423423',
     'PAYMEMT FOR MAINTENANCE SERVICE FEE IN DEC.2025 AS INV.1591/CONG TY TNHH GLOBAL HANSOLL',
     'SHINHAN', 'lamine@gmail.com', '20250918025419', 'lamine@gmail.com', '20260225064925')
ON CONFLICT (TRCO_KEY, BIZ_KEY, TRCO_OVRS_KEY) DO NOTHING;

-- ---------------------------------------------------------------------------
-- PGRO_BSAC_LNKN_INFM — 30 group-link rows (15 contacts × {B, S}).
-- Mirrors legacy `comm_0184_002_c001_act.jsp:131-147` (USER_ID='ALL'
-- with GRP_CD in {B, S} at create-time).
-- ---------------------------------------------------------------------------
INSERT INTO PGRO_BSAC_LNKN_INFM
    (BIZ_KEY, USER_ID, GRP_CD, TRCO_KEY, TAX_ID, REG_DTM)
VALUES
    ('VN_VNM0004_000001', 'ALL', 'B', '9322', '1201496252', '20260227100240'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9322', '1201496252', '20260227100240'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9321', '1201496252', '20251225031133'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9321', '1201496252', '20251225031133'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9316', '1201496252', '20251205090854'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9316', '1201496252', '20251205090854'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9315', '1201496252', '20251205083609'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9315', '1201496252', '20251205083609'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9314', '1201496252', '20251205070502'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9314', '1201496252', '20251205070502'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9313', '1201496252', '20251205070224'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9313', '1201496252', '20251205070224'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9312', '1201496252', '20251205024749'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9312', '1201496252', '20251205024749'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9311', '1201496252', '20251204063541'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9311', '1201496252', '20251204063541'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9310', '1201496252', '20251201074033'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9310', '1201496252', '20251201074033'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9308', '1201496252', '20251030061820'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9308', '1201496252', '20251030061820'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9307', '1201496252', '20251022025054'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9307', '1201496252', '20251022025054'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9306', '1201496252', '20251022023833'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9306', '1201496252', '20251022023833'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9305', '1201496252', '20251022013805'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9305', '1201496252', '20251022013805'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9304', '1201496252', '20251021093840'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9304', '1201496252', '20251021093840'),
    ('VN_VNM0004_000001', 'ALL', 'B', '9301', '1201496252', '20251013081234'),
    ('VN_VNM0004_000001', 'ALL', 'S', '9301', '1201496252', '20251013081234')
ON CONFLICT (BIZ_KEY, USER_ID, GRP_CD, TRCO_KEY) DO NOTHING;
