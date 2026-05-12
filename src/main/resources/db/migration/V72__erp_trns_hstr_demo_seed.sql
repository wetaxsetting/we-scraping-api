-- =============================================================================
-- V72 — ERP_TRNS_HSTR: real-DEV seed (≤30 rows for tenant VN_VNM0004_000001)
-- =============================================================================
-- Idempotent demo seed pulled verbatim from JEX DEV (`wecmsvndev.erp_trns_hstr`,
-- 2026-05-11) for tenant `VN_VNM0004_000001`. 30 rows total — top 10 per
-- direction by REG_DTM DESC, SEQ_NO DESC — sized per the project's data-
-- migration rule in wecms-api-main/CLAUDE.md §"Data Migration Rule".
--
-- Mix of file sizes:
--   FILE_SIZE > 0  — 30 of 30 rows (every seeded row references a real
--                    NAS file in DEV; the local download endpoint resolves
--                    these to fixtures committed under
--                    `src/test/resources/erp-transaction-history/fixtures/`
--                    + 'C:/WECMS PROJECT/File Data/<biz_key>/erp-transaction-history/' at
--                    runtime; missing fixtures surface as WBE2015).
--
-- Coverage:
--   W (WeCMS → ERP)  — 10 rows  (TRNS_TYPE_CD: 04, 05, 06, 09 × 3, 13, 14 × 2, 16)
--   E (ERP → WeCMS)  — 10 rows  (TRNS_TYPE_CD: 02, 03, 12, 13, 16 × 6)
--   D (Deleted)      — 10 rows  (TRNS_TYPE_CD: 02, 03, 13 × 2, 16 × 6)
--
-- =============================================================================

INSERT INTO ERP_TRNS_HSTR
    (BIZ_KEY, SEQ_NO, ORG_FILE_NM, SAVE_FILE_NM, FILE_SIZE, ERP_TYPE, TRNS_TYPE_CD, REG_ID, REG_DTM)
VALUES
    -- ------ Direction W (WeCMS → ERP) ------
    ('VN_VNM0004_000001', 759, 'Account Inquiry(VND)_30042026.xls',     'Account Inquiry(VND)_30042026.xls',     11264, 'W', '09', 'lamine@gmail.com', '20260430073409'),
    ('VN_VNM0004_000001', 758, 'WeCMS_Internal_Transfer_Transfer_EN.xlsx','WeCMS_Internal_Transfer_Transfer_EN.xlsx', 21409, 'W', '16', 'lamine@gmail.com', '20260421064755'),
    ('VN_VNM0004_000001', 757, 'Account Inquiry(VND)_07042026.xls',     'Account Inquiry(VND)_07042026.xls',     11264, 'W', '09', 'lamine@gmail.com', '20260407042814'),
    ('VN_VNM0004_000001', 756, 'Account Inquiry(VND)_07042026.xls',     'Account Inquiry(VND)_07042026.xls',     12288, 'W', '09', 'lamine@gmail.com', '20260407042006'),
    ('VN_VNM0004_000001', 748, 'WeCMS_Payroll_download_EN.xlsx',         'WeCMS_Payroll_download_EN.xlsx',         25044, 'W', '06', 'lamine@gmail.com', '20260325045233'),
    ('VN_VNM0004_000001', 747, 'WeCMS_Expense_Payment_download_EN.xlsx', 'WeCMS_Expense_Payment_download_EN.xlsx', 23913, 'W', '05', 'lamine@gmail.com', '20260313095414'),
    ('VN_VNM0004_000001', 746, 'WeCMS_Vendor_Payment_download_EN.xlsx',  'WeCMS_Vendor_Payment_download_EN.xlsx',  24828, 'W', '04', 'lamine@gmail.com', '20260313094426'),
    ('VN_VNM0004_000001', 714, 'WeCMS_Overseas_Payment_download_EN.xlsx','WeCMS_Overseas_Payment_download_EN.xlsx',10847, 'W', '14', 'lamine@gmail.com', '20260210041857'),
    ('VN_VNM0004_000001', 713, 'WeCMS_Overseas_Payment_download_EN.xlsx','WeCMS_Overseas_Payment_download_EN.xlsx',10831, 'W', '14', 'lamine@gmail.com', '20260210041853'),
    ('VN_VNM0004_000001', 712, 'WeCMS_Overseas_Payment_download_EN.xlsx','WeCMS_Overseas_Payment_download_EN.xlsx',10821, 'W', '13', 'lamine@gmail.com', '20260210041845'),

    -- ------ Direction E (ERP → WeCMS) ------
    ('VN_VNM0004_000001', 755, 'WeCMS_Vendor_Payment_upload_EN (22).xlsx', 'File_1774431115330_2.xlsx', 35803, 'E', '03', 'lamine@gmail.com', '20260325093155'),
    ('VN_VNM0004_000001', 753, 'File_1772164535446_6.xlsx',                'File_1774428567866_5.xlsx', 36398, 'E', '13', 'lamine@gmail.com', '20260325084928'),
    ('VN_VNM0004_000001', 751, 'WeCMS_Payroll_upload_EN (17).xlsx',        'File_1774428448520_2.xlsx', 34306, 'E', '12', 'lamine@gmail.com', '20260325084728'),
    ('VN_VNM0004_000001', 750, 'WeCMS_Vendor_Payment_upload_EN (22).xlsx', 'File_1774428098763_4.xlsx', 35800, 'E', '02', 'lamine@gmail.com', '20260325084138'),
    ('VN_VNM0004_000001', 749, 'WeCMS_Internal_Transfer_upload_EN.xlsx',   'File_1774423935253_3.xlsx', 35632, 'E', '16', 'lamine@gmail.com', '20260325073215'),
    ('VN_VNM0004_000001', 745, 'WOORI USD TO VND.xlsx',                    'File_1772438213771_1.xlsx', 35665, 'E', '16', 'lamine@gmail.com', '20260302075653'),
    ('VN_VNM0004_000001', 744, 'WOORI USD TO VND.xlsx',                    'File_1772438099297_4.xlsx', 35679, 'E', '16', 'lamine@gmail.com', '20260302075459'),
    ('VN_VNM0004_000001', 743, 'SHINHAN USD TO VND.xlsx',                  'File_1772437603634_6.xlsx', 35546, 'E', '16', 'lamine@gmail.com', '20260302074643'),
    ('VN_VNM0004_000001', 742, 'SHINHAN USD TO VND.xlsx',                  'File_1772437488737_4.xlsx', 35559, 'E', '16', 'lamine@gmail.com', '20260302074448'),
    ('VN_VNM0004_000001', 741, 'Internal_VND_SHINHAN-2.xlsx',              'File_1772436714180_2.xlsx', 35596, 'E', '16', 'lamine@gmail.com', '20260302073154'),

    -- ------ Direction D (Deleted) ------
    ('VN_VNM0004_000001', 754, 'File_1768373586642_4.xlsx',                  'File_1769595033782_4.xlsx', 35781, 'D', '03', 'lamine@gmail.com', '20260325093106'),
    ('VN_VNM0004_000001', 752, 'WeCMS_Overseas_Payment_upload_KO.xlsx',      'File_1772164535446_6.xlsx', 36398, 'D', '13', 'lamine@gmail.com', '20260325084901'),
    ('VN_VNM0004_000001', 737, 'Internal_VND_SHINHAN-1.xlsx',                'File_1772436309785_2.xlsx', 35578, 'D', '16', 'lamine@gmail.com', '20260302072603'),
    ('VN_VNM0004_000001', 735, 'Internal_VND_SHINHAN-1.xlsx',                'File_1772436127158_5.xlsx', 35578, 'D', '16', 'lamine@gmail.com', '20260302072505'),
    ('VN_VNM0004_000001', 734, 'WeCMS_Internal_Transfer_upload_EN (19).xlsx','File_1772435976816_3.xlsx', 35578, 'D', '16', 'lamine@gmail.com', '20260302072458'),
    ('VN_VNM0004_000001', 723, 'WeCMS_Internal_Transfer_upload_EN (17).xlsx','File_1771906498930_3.xlsx', 35628, 'D', '16', 'lamine@gmail.com', '20260224041523'),
    ('VN_VNM0004_000001', 688, 'File_1769588506833_2.xlsx',                  'File_1770352597105_5.xlsx', 36389, 'D', '13', 'lamine@gmail.com', '20260209020145'),
    ('VN_VNM0004_000001', 682, 'WeCMS_Vendor_Payment_upload_EN (9).xlsx',    'File_1769161911382_5.xlsx', 35748, 'D', '02', 'lamine@gmail.com', '20260129014211'),
    ('VN_VNM0004_000001', 679, 'WeCMS_Internal_Transfer_upload_EN (13).xlsx','File_1768811031765_5.xlsx', 35593, 'D', '16', 'lamine@gmail.com', '20260128090545'),
    ('VN_VNM0004_000001', 678, 'WeCMS_Internal_Transfer_upload_EN (10).xlsx','File_1768470044747_2.xlsx', 35562, 'D', '16', 'lamine@gmail.com', '20260128090407')
ON CONFLICT (BIZ_KEY, SEQ_NO) DO NOTHING;
