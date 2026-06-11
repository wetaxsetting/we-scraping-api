-- ============================================================================
-- V61__firm_banking_history_demo_seed.sql
--
-- Settings > Firm Banking History — demo seed for the configured tenant
-- (BIZ_KEY = VN_VNM0004_000001) so the local R&D stack returns
-- non-empty rows from V_FIRM_BANKING_HISTORY without a full DEV import.
--
-- Idempotent: every INSERT uses ON CONFLICT DO NOTHING.
--
-- Tables seeded here:
--   1. TB_FIRM_BANK_CODE   — 2 bank codes referenced by the TB_FIRM_TRAN
--                            sample (970424 = Shinhan VN, 970457 = Woori VN).
--                            wb_get_napas_bank_nm hardcodes their display
--                            names but still benefits from a row existing
--                            (forward-compat with the function's fallback
--                            SELECT branch).
--   2. TB_FIRM_TRAN        — 8 representative rows from DEV (latest first).
--   3. NXSIAS_MODULE_TRAN_LOG_HIS — 8 representative rows from DEV.
--
-- Tables already seeded by earlier migrations (left untouched):
--   - NAPAS_REMIT_TRNS_HSTR — V22 (25 rows for tenant)
--   - EXT_TRAN_CALL_HIS    — V29 (50 rows; not all tenant-scoped, by design)
--
-- Source: rows pulled live from wecmsvndev on 2026-05-08 via the read-only
-- postgres_dev MCP. See feature spec §11 open question 4.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. TB_FIRM_BANK_CODE — bank codes referenced by TB_FIRM_TRAN sample
-- ---------------------------------------------------------------------------
INSERT INTO TB_FIRM_BANK_CODE
    (BANK_CD, ANT_DIV_CD, CITAD_BANK_CD, BANK_NM, ENG_ABRVAT_NM, REG_DTTM)
VALUES
    ('970424', 'NAPAS', '000', N'Ngân hàng TNHH MTV Shinhan Việt Nam', 'ShinhanBank', '20251226162922'),
    ('970457', 'NAPAS', '000', N'Ngân hàng Wooribank',                 'WooriBank',   '20251226162922')
ON CONFLICT (BANK_CD) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. TB_FIRM_TRAN — 8 representative tenant rows (latest first)
-- ---------------------------------------------------------------------------
INSERT INTO TB_FIRM_TRAN
    (TRAN_DT, BIZ_KEY, TRAN_SEQ, OUT_BANK_CD, BANK_PRTN_ID, TRAN_TP, TRAN_TM, VAN_ORG_CD,
     WDRL_ACT_NO, TAR_BANK_CD, FINC_MDA_DIV, FINC_MDA_NO, FINC_MDA_OWNER_NM,
     ANT_DIV_CD, TRSC_CCY_CD, TRAN_AMT, TRAN_USAG, BAL_AMT, RQ_DTTM, RS_DTTM,
     PROC_STAT, RS_CD, RS_MSG, APL_EXC_RATE, WITHD_AMT, DEP_AMT)
VALUES
    ('20260421', 'VN_VNM0004_000001', '13768', '970424', 'WEBC1',  '202', '154910', 'GV00000001',
     '700005285299', '970424', 'A', '703010473002', 'E-Time Deposit (VND)',
     'INTER', 'VND', 1000.00, 'Internal payment 1', 0.00, '20260421154910', '20260421154911',
     '3', '0999', 'Authorization error', 0.00, 0.00, 0.00),
    ('20260325', 'VN_VNM0004_000001', '13764', '970424', '434534', '201', '173823', '34534534',
     '700005285280', '970424', 'A', '700071152854', '',
     NULL, NULL, 0.00, NULL, NULL, '20260325173823', NULL,
     '6', NULL, NULL, 0.00, 0.00, 0.00),
    ('20260325', 'VN_VNM0004_000001', '13763', '970424', '434534', '201', '173808', '34534534',
     '700005285280', '970424', 'A', '700071152854', '',
     NULL, NULL, 0.00, NULL, NULL, '20260325173808', NULL,
     '6', NULL, NULL, 0.00, 0.00, 0.00),
    ('20260325', 'VN_VNM0004_000001', '13762', '970424', '434534', '201', '173758', '34534534',
     '700005285280', '970424', 'A', '700071152854', '',
     NULL, NULL, 0.00, NULL, NULL, '20260325173758', NULL,
     '6', NULL, NULL, 0.00, 0.00, 0.00),
    ('20260325', 'VN_VNM0004_000001', '13761', '970424', '434534', '201', '173751', '34534534',
     '700005285280', '970424', 'A', '700071152854', '',
     NULL, NULL, 0.00, NULL, NULL, '20260325173751', NULL,
     '6', NULL, NULL, 0.00, 0.00, 0.00),
    ('20260325', 'VN_VNM0004_000001', '13760', '970424', '434534', '201', '173726', '34534534',
     '700005285280', '970424', 'A', '700071152854', '',
     NULL, NULL, 0.00, NULL, NULL, '20260325173726', NULL,
     '6', NULL, NULL, 0.00, 0.00, 0.00),
    ('20260325', 'VN_VNM0004_000001', '13759', '970424', '434534', '201', '173711', '34534534',
     '700005285280', '970424', 'A', '700071152854', '',
     NULL, NULL, 0.00, NULL, NULL, '20260325173711', NULL,
     '6', NULL, NULL, 0.00, 0.00, 0.00),
    ('20260325', 'VN_VNM0004_000001', '13758', '970424', '434534', '201', '173710', '34534534',
     '700005285280', '970424', 'A', '700071152854', '',
     NULL, NULL, 0.00, NULL, NULL, '20260325173710', NULL,
     '6', NULL, NULL, 0.00, 0.00, 0.00)
ON CONFLICT (TRAN_DT, BIZ_KEY, TRAN_SEQ) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. NXSIAS_MODULE_TRAN_LOG_HIS — 8 representative tenant rows.
--    All 8 sampled rows are E-INVOICE_SALES_INQUIRY (SVC_TYPE='G' leg).
--    A future demo-seed pass can add BUSSINESS_REGISTRATION_* rows
--    (SVC_TYPE='B' leg) when DEV exposes them for this tenant.
-- ---------------------------------------------------------------------------
INSERT INTO NXSIAS_MODULE_TRAN_LOG_HIS
    (BIZ_KEY, REG_DTM, UUID, NXISAS_CLASS, NXISAS_MODULE, NXISAS_JOB, NXISAS_TYPE,
     NXISAS_ERR_CODE, NXISAS_ERR_CONT, REQ_DATA, REG_ID)
VALUES
    ('VN_VNM0004_000001', '20251017044610', 'cd3117bcd41c46c0b502135095536318',
     'COMMON', 'GLOBAL/VNM/PUBLIC_GDT', 'INVOICE_DETAIL', 'E-INVOICE_SALES_INQUIRY',
     '00000000', '',
     '{"sellerTaxCode":"1201496252","endDate":"20251017","numericSymbol":"1","invoiceType":"1","invoiceSymbol":"C25TWV","invoiceNo":"1278","startDate":"20251013"}',
     'lamine@gmail.com'),
    ('VN_VNM0004_000001', '20251017044610', '8637e4f1963943a1be611310c52cf2ef',
     'COMMON', 'GLOBAL/VNM/PUBLIC_GDT', 'INVOICE_DETAIL', 'E-INVOICE_SALES_INQUIRY',
     '00000000', '',
     '{"sellerTaxCode":"1201496252","endDate":"20251017","numericSymbol":"1","invoiceType":"1","invoiceSymbol":"C25TWV","invoiceNo":"1275","startDate":"20251013"}',
     'lamine@gmail.com'),
    ('VN_VNM0004_000001', '20251017044610', '2d029f4277044d62ab9a55961d996adb',
     'COMMON', 'GLOBAL/VNM/PUBLIC_GDT', 'INVOICE_DETAIL', 'E-INVOICE_SALES_INQUIRY',
     '00000000', '',
     '{"sellerTaxCode":"1201496252","endDate":"20251017","numericSymbol":"1","invoiceType":"1","invoiceSymbol":"C25TWV","invoiceNo":"1280","startDate":"20251013"}',
     'lamine@gmail.com'),
    ('VN_VNM0004_000001', '20251017044610', 'cb9464d05a284df4aecb5eda8a353eb2',
     'COMMON', 'GLOBAL/VNM/PUBLIC_GDT', 'INVOICE_DETAIL', 'E-INVOICE_SALES_INQUIRY',
     '00000000', '',
     '{"sellerTaxCode":"1201496252","endDate":"20251017","numericSymbol":"1","invoiceType":"1","invoiceSymbol":"C25TWV","invoiceNo":"1279","startDate":"20251013"}',
     'lamine@gmail.com'),
    ('VN_VNM0004_000001', '20251017044610', '06d53799992f4ae9aa3cab1a68226f43',
     'COMMON', 'GLOBAL/VNM/PUBLIC_GDT', 'INVOICE_DETAIL', 'E-INVOICE_SALES_INQUIRY',
     '00000000', '',
     '{"sellerTaxCode":"1201496252","endDate":"20251017","numericSymbol":"1","invoiceType":"1","invoiceSymbol":"C25TWV","invoiceNo":"1277","startDate":"20251013"}',
     'lamine@gmail.com'),
    ('VN_VNM0004_000001', '20251017044610', '6b4ef1071e3c40db8c27a5e9501a982a',
     'COMMON', 'GLOBAL/VNM/PUBLIC_GDT', 'INVOICE_DETAIL', 'E-INVOICE_SALES_INQUIRY',
     '00000000', '',
     '{"sellerTaxCode":"1201496252","endDate":"20251017","numericSymbol":"1","invoiceType":"1","invoiceSymbol":"C25TWV","invoiceNo":"1276","startDate":"20251013"}',
     'lamine@gmail.com'),
    ('VN_VNM0004_000001', '20251017044610', 'dfe8db95e6184d59b750886f9482be0e',
     'COMMON', 'GLOBAL/VNM/PUBLIC_GDT', 'INVOICE_DETAIL', 'E-INVOICE_SALES_INQUIRY',
     '00000000', '',
     '{"sellerTaxCode":"1201496252","endDate":"20251017","numericSymbol":"1","invoiceType":"1","invoiceSymbol":"C25TWV","invoiceNo":"1281","startDate":"20251013"}',
     'lamine@gmail.com'),
    ('VN_VNM0004_000001', '20251017044609', '73e5ad748b244652ae30e41a99cbb3aa',
     'COMMON', 'GLOBAL/VNM/PUBLIC_GDT', 'INVOICE_DETAIL', 'E-INVOICE_SALES_INQUIRY',
     '00000000', '',
     '{"sellerTaxCode":"1201496252","endDate":"20251017","numericSymbol":"1","invoiceType":"1","invoiceSymbol":"C25TWV","invoiceNo":"1346","startDate":"20251013"}',
     'lamine@gmail.com')
ON CONFLICT (BIZ_KEY, REG_DTM, UUID) DO NOTHING;
