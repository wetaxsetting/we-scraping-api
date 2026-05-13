-- ============================================================================
-- V93 — Bank picker seed for Settings > Contacts > Add / Edit
--
-- Extends V61's two-row TB_FIRM_BANK_CODE seed (Shinhan + Woori) with a
-- representative set of NAPAS banks pulled from real JEX DEV so the
-- contacts Add/Edit dialog's bank-picker autocomplete has enough variety
-- for local R&D testing. CITAD branches are NOT seeded — DEV stores ~5,000
-- branch rows per CITAD-registered bank, and the contacts popup deduplicates
-- to one entry per bank by ENG_ABRVAT_NM at render time. For local R&D the
-- NAPAS subset is sufficient; bring CITAD into a follow-up seed when the
-- popup gains a "CITAD branch picker" tab (legacy `comm_0109_001_r002`
-- supports both, but the refactor's MVP picker is NAPAS-only per
-- `comm_0184_002.ts:44` which passes `BANK_TYPE: "NAPAS_CD"`).
--
-- Idempotent via `INSERT … ON CONFLICT (BANK_CD) DO NOTHING`. Real BANK_CD
-- values preserved verbatim from DEV.
-- ============================================================================

INSERT INTO TB_FIRM_BANK_CODE
    (BANK_CD, ANT_DIV_CD, CITAD_BANK_CD, BANK_NM, BANK_ENG_NM, ENG_ABRVAT_NM, BANK_STAS, REG_DTTM)
VALUES
    ('970425', 'NAPAS', '000', N'Ngân hàng TMCP An Bình',                                    N'An Binh Commercial Joint Stock Bank',                          'ABBank',                '1', '20260513120000'),
    ('970416', 'NAPAS', '000', N'Ngân hàng TMCP Á Châu',                                     N'Asia Commercial Joint Stock Bank',                             'ACB',                   '1', '20260513120000'),
    ('970405', 'NAPAS', '000', N'Ngân hàng Nông Nghiệp và Phát triển Nông Thôn Việt Nam',    N'Vietnam Bank for Agriculture and Rural Development',           'Agribank',              '1', '20260513120000'),
    ('970418', 'NAPAS', '000', N'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam',              N'Joint Stock Commercial Bank for Investment and Development of Vietnam', 'BIDV',         '1', '20260513120000'),
    ('970431', 'NAPAS', '000', N'Ngân hàng TMCP Xuất nhập khẩu Việt Nam',                    N'Vietnam Export Import Commercial Joint - Stock Bank',          'EximBank',              '1', '20260513120000'),
    ('970437', 'NAPAS', '000', N'Ngân hàng TMCP Phát Triển Thành Phố Hồ Chí Minh',           N'Ho Chi Minh City Development Joint Stock Commercial Bank',     'HDBank',                '1', '20260513120000'),
    ('458761', 'NAPAS', '000', N'Ngân hàng TNHH MTV HSBC Việt Nam',                          N'Hong Kong and Shanghai Banking Corporation',                   'HSBC',                  '1', '20260513120000'),
    ('970466', 'NAPAS', '000', N'Ngân hàng KEB Hana - CN Hồ Chí Minh',                       N'KEB Hana Bank',                                                'KEBHanaBank-HCM',       '1', '20260513120000'),
    ('970422', 'NAPAS', '000', N'Ngân hàng TMCP Quân Đội',                                   N'Military Commercial Joint Stock Bank',                         'MBBank',                '1', '20260513120000'),
    ('970426', 'NAPAS', '000', N'Ngân hàng TMCP Hàng Hải Việt Nam',                          N'MSB',                                                          'MSB',                   '1', '20260513120000'),
    ('970448', 'NAPAS', '000', N'Ngân hàng TMCP Phương Đông',                                N'Orient Commercial Joint Stock Bank',                           'OCB',                   '1', '20260513120000'),
    ('970403', 'NAPAS', '000', N'Ngân hàng TMCP Sài Gòn Thương Tín',                         N'Sai Gon Thuong Tin Commercial Joint Stock Bank',               'Sacombank',             '1', '20260513120000'),
    ('970410', 'NAPAS', '000', N'Ngân hàng TNHH MTV Standard Chartered (Việt Nam)',          N'Standard Chartered Bank',                                      'StandardCharteredBank', '1', '20260513120000'),
    ('970423', 'NAPAS', '000', N'Ngân hàng TMCP Tiên Phong',                                 N'TPBank',                                                       'TPBank',                '1', '20260513120000'),
    ('970407', 'NAPAS', '000', N'Ngân hàng TMCP Kỹ thương Việt Nam',                         N'Vietnam Technological and Commercial Joint-Stock Bank',        'Techcombank',           '1', '20260513120000'),
    ('970432', 'NAPAS', '000', N'Ngân hàng TMCP Việt Nam Thịnh Vượng',                       N'Vietnam Prosperity Joint-Stock Commercial Bank',               'VPBank',                '1', '20260513120000'),
    ('970436', 'NAPAS', '000', N'Ngân hàng TMCP Ngoại Thương Việt Nam',                      N'Joint Stock Commercial Bank for Foreign Trade of Vietnam',     'Vietcombank',           '1', '20260513120000'),
    ('970415', 'NAPAS', '000', N'Ngân hàng TMCP Công Thương Việt Nam',                       N'Vietnam Joint Stock Commercial Bank For Industry And Trade',   'VietinBank',            '1', '20260513120000')
ON CONFLICT (BANK_CD) DO NOTHING;
