-- =============================================================================
-- V145: CITAD bank-code seed (Vietnam Citad-network bank routing codes).
--
-- Extends V93 (NAPAS-only) with one representative branch per CITAD bank
-- code (3-digit `citad_bank_cd`). The Payment-module Excel parsers
-- (`*PaymentExcelParser.findFirstByAntDivCdAndCitadBankCd`) look up bank
-- codes of length 3 against this table — without CITAD rows seeded, every
-- legacy template that fills in a CITAD code (e.g. 616 = Shinhan, 201 =
-- VietinBank, 311 = MBBank) fails validation as "Unknown Bank Code".
--
-- Source: `WECMS DEV/WECMS_DOC/database/ddl/WeCMS_BankBranchList.csv` —
-- the canonical bank-branch list shipped with the JEX system. DEV stores
-- ~5,000 branch rows per CITAD-registered bank for the comm_0109_001
-- branch picker; for refactor R&D the first branch per bank is enough
-- because the parser uses `findFirst` semantics (legacy uses count > 0).
--
-- Idempotent via `INSERT … ON CONFLICT (BANK_CD) DO NOTHING`. BANK_CD
-- values preserved verbatim from the CSV; column shape matches V93.
-- =============================================================================

INSERT INTO TB_FIRM_BANK_CODE
    (BANK_CD, ANT_DIV_CD, CITAD_BANK_CD, BANK_NM, BANK_ENG_NM, ENG_ABRVAT_NM, BANK_STAS, BANK_BRANCH_DIV, REG_DTTM)
VALUES
    ('01307001', 'CITAD', '307', N'Ngân hàng TMCP Á Châu', N'Asia Commercial Joint Stock Bank', 'ACB', '1', '1', '20260514120000'),
    ('01664001', 'CITAD', '664', N'Ngân hàng TMCP Á Châu', N'Asia Commercial Joint Stock Bank', 'ABC', '1', '1', '20260514120000'),
    ('01602001', 'CITAD', '602', N'Ngân hàng TNHH MTV ANZ Việt Nam', N'ANZ Vietnam', 'ANZ VIET NAM', '1', '1', '20260514120000'),
    ('01313001', 'CITAD', '313', N'Ngân hàng TMCP Bắc Á', N'Bac A Commercial Joint Stock Bank', 'BacABank', '1', '1', '20260514120000'),
    ('01327001', 'CITAD', '327', N'Ngân hàng TMCP Bản Việt', N'Viet Capital Commercial Joint Stock Bank', 'BVBank', '1', '1', '20260514120000'),
    ('01667001', 'CITAD', '667', N'Ngân hàng Bangkok Đại Chúng TNHH - Hà Nội', N'Bangkok Bank', 'BangkokBank-HN', '1', '1', '20260514120000'),
    ('79612001', 'CITAD', '612', N'Ngân hàng Bangkok Đại Chúng TNHH - Hồ Chí Minh', N'Bangkok Bank', 'BangkokBank-HCM', '1', '1', '20260514120000'),
    ('79620001', 'CITAD', '620', N'Bank of China', N'Bank of China', 'BOC', '1', '1', '20260514120000'),
    ('79615001', 'CITAD', '615', N'Bank of Communications CN TP Hồ Chí Minh', N'Bank of Communications', 'BoCom', '1', '1', '20260514120000'),
    ('01401001', 'CITAD', '401', N'Công ty Cổ phần Thanh toán Quốc gia Việt Nam', N'NAPAS', 'NAPAS', '1', '1', '20260514120000'),
    ('01359001', 'CITAD', '359', N'Ngân hàng TMCP Bảo Việt', N'BaoViet Bank', 'BaoVietBank', '1', '1', '20260514120000'),
    ('01657001', 'CITAD', '657', N'Ngân hàng BNP Paribas CN Hà Nội', N'BNP Paribas', 'BNPParibas-HN', '1', '1', '20260514120000'),
    ('79614001', 'CITAD', '614', N'Ngân hàng BNP Paribas CN Hồ Chí Minh', N'BNP Paribas', 'BNPParibas-HCM', '1', '1', '20260514120000'),
    ('79601001', 'CITAD', '601', N'Ngân hàng BPCEIOM CN TP HCM', N'BPCE IOM Vietnam', 'BPCEIOM', '1', '1', '20260514120000'),
    ('79660001', 'CITAD', '660', N'Ngân hàng Busan CN TP Hồ Chí Minh', N'Busan Bank', 'BusanBank', '1', '1', '20260514120000'),
    ('79634001', 'CITAD', '634', N'Ngân hàng Cathay United Bank', N'Cathay United Bank', 'CathayUnitedBank', '1', '1', '20260514120000'),
    ('79611001', 'CITAD', '611', N'China Construction Bank Corporation CN TP HCM', N'China Construction Bank', 'CCB', '1', '1', '20260514120000'),
    ('79629001', 'CITAD', '629', N'Chinatrust Commercial Bank', N'Chinatrust Commercial Bank', 'CTBC', '1', '1', '20260514120000'),
    ('01207001', 'CITAD', '207', N'Ngân hàng chính sách xã hội', N'Vietnam Bank for Social Policies', 'VBSP', '1', '1', '20260514120000'),
    ('01661001', 'CITAD', '661', N'Ngân hàng TNHH MTV CIMB Việt Nam', N'CIMB Bank', 'CIMB', '1', '1', '20260514120000'),
    ('01605001', 'CITAD', '605', N'Ngân hàng Citibank Việt Nam CN Hà Nội', N'Citi bank', 'CitiBank', '1', '1', '20260514120000'),
    ('79654001', 'CITAD', '654', N'Ngân hàng Citibank Việt Nam CN TPHCM', N'Citi bank', 'CitiBank', '1', '1', '20260514120000'),
    ('01201001', 'CITAD', '201', N'Ngân hàng TMCP Công Thương Việt Nam', N'Vietnam Joint Stock Commercial Bank For Industry And Trade', 'VietinBank', '1', '1', '20260514120000'),
    ('01360001', 'CITAD', '360', N'Ngân hàng TMCP Đại Chúng Việt Nam', N'Pvcom Bank', 'PVcomBank', '1', '1', '20260514120000'),
    ('01638001', 'CITAD', '638', N'Ngân hàng DT&PT Campuchia CN Hà Nội', N'Bank for Investment and Development of Cambodia', 'BIDC-HN', '1', '1', '20260514120000'),
    ('79648001', 'CITAD', '648', N'Ngân hàng DT&PT Campuchia CN Hồ Chí Minh', N'Bank for Investment and Development of Cambodia', 'BIDC-HCM', '1', '1', '20260514120000'),
    ('01202001', 'CITAD', '202', N'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam', N'Joint Stock Commercial Bank for Investment and Development of Vietnam', 'BIDV', '1', '1', '20260514120000'),
    ('79650001', 'CITAD', '650', N'Ngân hàng DBS Việt Nam', N'DBS Bank Limited', 'DBSBank', '1', '1', '20260514120000'),
    ('79619001', 'CITAD', '619', N'Ngân hàng Deutsche Bank', N'Deutsche Bank', 'DeutscheBank', '1', '1', '20260514120000'),
    ('01317001', 'CITAD', '317', N'Ngân hàng TMCP Đông Nam Á', N'Southeast Asia Commercial Joint Stock Bank', 'SeABank', '1', '1', '20260514120000'),
    ('75658001', 'CITAD', '658', N'Ngân hàng TNHH E.SUN CN Đồng Nai', N'"E.SUN Commercial Bank', 'E.SUN', '1', '1', '20260514120000'),
    ('01608001', 'CITAD', '608', N'First Commercial Bank CN Hà Nội', N'First Commercial Bank', 'FCB-HN', '1', '1', '20260514120000'),
    ('79630001', 'CITAD', '630', N'First Commercial Bank CN TP Hồ Chí Minh', N'First Commercial Bank', 'FCB-HCM', '1', '1', '20260514120000'),
    ('01302001', 'CITAD', '302', N'Ngân hàng TMCP Hàng Hải Việt Nam', N'MSB', 'MSB', '1', '1', '20260514120000'),
    ('01603001', 'CITAD', '603', N'Ngân hàng TNHH MTV Hongleong Việt Nam', N'HongLeong Bank', 'HongLeongBank', '1', '1', '20260514120000'),
    ('01901001', 'CITAD', '901', N'Ngân hàng Hợp tác xã Việt Nam', N'Co-operative Bank of Viet Nam', 'Co-opBank', '1', '1', '20260514120000'),
    ('01617001', 'CITAD', '617', N'Ngân hàng TNHH MTV HSBC Việt Nam', N'Hong Kong and Shanghai Banking Corporation', 'HSBC', '1', '1', '20260514120000'),
    ('79640001', 'CITAD', '640', N'Hua Nan Commercial Bank TP HCM', N'Hua Nan Commercial Bank', 'HNCB', '1', '1', '20260514120000'),
    ('01649001', 'CITAD', '649', N'Ngân hàng Industrial and Commercial Bank of China', N'Industrial and Commercial Bank of China', 'ICBC', '1', '1', '20260514120000'),
    ('79668001', 'CITAD', '668', N'Ngân hàng iM', N'iM Bank', 'iMBank', '1', '1', '20260514120000'),
    ('01502001', 'CITAD', '502', N'Ngân hàng TNHH Indovina', N'Indovina Bank', 'IVB', '1', '1', '20260514120000'),
    ('01652001', 'CITAD', '652', N'Ngân hàng Công nghiệp Hàn Quốc CN Hà Nội', N'Industrial Bank of Korea-HN', 'IBK-HN', '1', '1', '20260514120000'),
    ('79641001', 'CITAD', '641', N'Ngân hàng Công nghiệp Hàn Quốc CN Hồ Chí Minh', N'Industrial Bank of Korea-HCM', 'IBK-HCM', '1', '1', '20260514120000'),
    ('79627001', 'CITAD', '627', N'Ngân hàng JPMorgan Chase N.A Chi nhánh TP HCM', N'JPMorgan', 'JPMorgan', '1', '1', '20260514120000'),
    ('79669001', 'CITAD', '669', N'Ngân hàng Đại chúng TNHH Kasikornbank', N'Kasikornbank', 'KBank', '1', '1', '20260514120000'),
    ('01626001', 'CITAD', '626', N'Ngân hàng KEB Hana - CN Hà Nội', N'KEB Hana Bank', 'KEBHanaBank-HN', '1', '1', '20260514120000'),
    ('79656001', 'CITAD', '656', N'Ngân hàng KEB Hana - CN Hồ Chí Minh', N'KEB Hana Bank', 'KEBHanaBank-HCM', '1', '1', '20260514120000'),
    ('01701001', 'CITAD', '701', N'Kho Bạc Nhà nước', N'State Treasury', 'StateTreasury', '1', '1', '20260514120000'),
    ('01353001', 'CITAD', '353', N'Ngân hàng TMCP Kiên Long', N'Kien Long Bank', 'KienlongBank', '1', '1', '20260514120000'),
    ('01666001', 'CITAD', '666', N'Ngân hàng Kookmin CN Hà Nội', N'Kookmin Bank - HN', 'KB-HCM', '1', '1', '20260514120000'),
    ('79631001', 'CITAD', '631', N'Ngân hàng Kookmin CN Hồ Chí Minh', N'Kookmin Bank - HCM', 'KB-HN', '1', '1', '20260514120000'),
    ('01320001', 'CITAD', '320', N'Ngân hàng TM TNHH MTV Kỷ Nguyên Thịnh Vượng', N'Generation of Prosperity Sole Member Limited Commercial Bank', 'GPBank', '1', '1', '20260514120000'),
    ('01310001', 'CITAD', '310', N'Ngân hàng TMCP Kỹ thương Việt Nam', N'Vietnam Technological and Commercial Joint-Stock Bank', 'Techcombank', '1', '1', '20260514120000'),
    ('01357001', 'CITAD', '357', N'Ngân hàng TMCP Lộc Phát Việt Nam', N'Loc Phat Bank', 'LPBank', '1', '1', '20260514120000'),
    ('01609001', 'CITAD', '609', N'Malayan Banking Berhad CN Hà Nội', N'MayBank-HN', 'MayBank-HN', '1', '1', '20260514120000'),
    ('79635001', 'CITAD', '635', N'Malayan Banking Berhad CN Hồ Chí Minh', N'MayBank-HCM', 'MayBank-HCM', '1', '1', '20260514120000'),
    ('79623001', 'CITAD', '623', N'Ngân hàng thương mại Quốc tế Mega TP Hồ Chí Minh', N'"Mega International Commercial Bank Co.', 'MegaBank', '1', '1', '20260514120000'),
    ('01613001', 'CITAD', '613', N'Mizuho Bank CN Hà Nội', N'Mizuho Bank-HN', 'Mizuho Bank-HN', '1', '1', '20260514120000'),
    ('79639001', 'CITAD', '639', N'Mizuho Bank CN Hồ Chí Minh', N'Mizuho Bank-HCM', 'Mizuho Bank-HCM', '1', '1', '20260514120000'),
    ('01306001', 'CITAD', '306', N'Ngân hàng TMCP Nam Á', N'Nam A Commercial Joint Stock Bank', 'NamABank', '1', '1', '20260514120000'),
    ('01339001', 'CITAD', '339', N'Ngân hàng TM TNHH MTV Ngoại thương Công nghệ số', N'Vietcombank Neo Limited', 'VCBNeo', '1', '1', '20260514120000'),
    ('01203001', 'CITAD', '203', N'Ngân hàng TMCP Ngoại Thương Việt Nam', N'Joint Stock Commercial Bank for Foreign Trade of Vietnam', 'Vietcombank', '1', '1', '20260514120000'),
    ('01101011', 'CITAD', '101', N'Ngân hàng nhà nước Việt Nam', N'State Bank of Vietnam', 'SBV', '1', '1', '20260514120000'),
    ('01304001', 'CITAD', '304', N'Ngân hàng TNHH MTV Số Vikki', N'Vikki Bank', 'VikkiBank', '1', '1', '20260514120000'),
    ('01662001', 'CITAD', '662', N'Ngân hàng Nonghyup', N'NongHyup Bank', 'NongHyupBank', '1', '1', '20260514120000'),
    ('01204001', 'CITAD', '204', N'Ngân hàng Nông Nghiệp và Phát triển Nông Thôn Việt Nam', N'Vietnam Bank for Agriculture and Rural Development', 'Agribank', '1', '1', '20260514120000'),
    ('79625001', 'CITAD', '625', N'Oversea - Chinese banking TP HCM', N'Overseas Chinese Bank Corporation', 'OCBC', '1', '1', '20260514120000'),
    ('01341001', 'CITAD', '341', N'Ngân hàng TMCP Xăng Dầu Petrolimex', N'PG Bank', 'PGBank', '1', '1', '20260514120000'),
    ('01321001', 'CITAD', '321', N'Ngân hàng TMCP Phát Triển Thành Phố Hồ Chí Minh', N'Ho Chi Minh City Development Joint Stock Commercial Bank', 'HDBank', '1', '1', '20260514120000'),
    ('01208001', 'CITAD', '208', N'Ngân hàng Phát triển Việt Nam', N'Vietnam Development Bank', 'VDB', '1', '1', '20260514120000'),
    ('01333001', 'CITAD', '333', N'Ngân hàng TMCP Phương Đông', N'Orient Commercial Joint Stock Bank', 'OCB', '1', '1', '20260514120000'),
    ('01501001', 'CITAD', '501', N'Ngân hàng TNHH MTV Public Việt Nam', N'Public Bank Vietnam', 'PublicBank', '1', '1', '20260514120000'),
    ('11311001', 'CITAD', '311', N'Ngân hàng TMCP Quân Đội', N'Military Commercial Joint Stock Bank', 'MBBank', '1', '1', '20260514120000'),
    ('01352001', 'CITAD', '352', N'Ngân hàng TMCP Quốc Dân', N'National Citizen Bank', 'NBC', '1', '1', '20260514120000'),
    ('01314002', 'CITAD', '314', N'Ngân hàng TMCP Quốc Tế', N'Vietnam International Commercial Joint Stock Bank', 'VIB', '1', '1', '20260514120000'),
    ('01308001', 'CITAD', '308', N'Ngân hàng  TMCP Sài Gòn Công Thương', N'Saigon Bank for Industry and Trade', 'SaigonBank', '1', '1', '20260514120000'),
    ('24348001', 'CITAD', '348', N'Ngân hàng TMCP Sài Gòn - Hà Nội', N'Saigon – Hanoi Commercial Joint Stock Bank', 'SHB', '1', '1', '20260514120000'),
    ('01303002', 'CITAD', '303', N'Ngân hàng TMCP Sài Gòn Thương Tín', N'Sai Gon Thuong Tin Commercial Joint Stock Bank', 'Sacombank', '1', '1', '20260514120000'),
    ('01334001', 'CITAD', '334', N'Ngân hàng TMCP Sài Gòn', N'Sai Gon Joint Stock Commercial Bank', 'SCB', '1', '1', '20260514120000'),
    ('01616001', 'CITAD', '616', N'Ngân hàng TNHH MTV Shinhan Việt Nam', N'Shinhan Bank', 'ShinhanBank', '1', '1', '20260514120000'),
    ('79600001', 'CITAD', '600', N'NH The Siam Commercial Bank Public CN TP Hồ Chí Minh', N'The Siam Commercial Bank Public Company Limited', 'SCBThailand', '1', '1', '20260514120000'),
    ('79632001', 'CITAD', '632', N'Ngân hàng SinoPac CN TP HCM', N'SinoPac Bank', 'SinoPac', '1', '1', '20260514120000'),
    ('01604001', 'CITAD', '604', N'Ngân hàng TNHH MTV Standard Chartered (Việt Nam)', N'Standard Chartered Bank', 'StandardCharteredBank', '1', '1', '20260514120000'),
    ('01636001', 'CITAD', '636', N'Ngân hàng Sumitomo Mitsui Banking Corporation TP HCM', N'Sumitomo Mitsui Banking Corporation', 'SMBC', '1', '1', '20260514120000'),
    ('01642001', 'CITAD', '642', N'Ngân hàng Taipei Fubon CN Hà Nội', N'Taipei Fubon - Ha Noi', 'TaipeiFubon-HN', '1', '1', '20260514120000'),
    ('79651001', 'CITAD', '651', N'Ngân hàng Taipei Fubon CN TP Hồ Chí Minh', N'Taipei Fubon - Tp Ho Chi Minh', 'TaipeiFubon-HCM', '1', '1', '20260514120000'),
    ('79655001', 'CITAD', '655', N'Ngân hàng Taipei Fubon CN Bình Dương', N'Taipei Fubon - Binh Duong', 'TaipeiFubon-Binh Duong', '1', '1', '20260514120000'),
    ('75606001', 'CITAD', '606', N'NH The Shanghai Commercial & Savings CN Đồng Nai', N'Shanghai Commercial & Savings Bank', 'SCSB', '1', '1', '20260514120000'),
    ('01358001', 'CITAD', '358', N'Ngân hàng TMCP Tiên Phong', N'TPBank', 'TPBank', '1', '1', '20260514120000'),
    ('79618001', 'CITAD', '665', N'Ngân hàng TNHH MTV United Overseas Bank', N'United Overseas Bank Vietnam', 'UOB', '1', '1', '20260514120000'),
    ('01355001', 'CITAD', '355', N'Ngân hàng TMCP Việt Á', N'VietABank', 'VietABank', '1', '1', '20260514120000'),
    ('01319001', 'CITAD', '319', N'Ngân hàng TNHH MTV Việt Nam Hiện Đại', N'Modern Bank of Vietnam Limited', 'MBV', '1', '1', '20260514120000'),
    ('01309001', 'CITAD', '309', N'Ngân hàng TMCP Việt Nam Thịnh Vương', N'Vietnam Prosperity Joint-Stock Commercial Bank', 'VPBank', '1', '1', '20260514120000'),
    ('01356001', 'CITAD', '356', N'Ngân hàng TMCP Việt Nam Thương Tín', N'Vietnam Thuong Tin Commercial Joint Stock Bank', 'VietBank', '1', '1', '20260514120000'),
    ('01663001', 'CITAD', '663', N'Ngân hàng Wooribank', N'Woori Bank ', 'WooriBank', '1', '1', '20260514120000'),
    ('01305001', 'CITAD', '305', N'Ngân hàng TMCP Xuất nhập khẩu Việt Nam', N'Vietnam Export Import Commercial Joint - Stock Bank', 'EximBank', '1', '1', '20260514120000')
ON CONFLICT (BANK_CD) DO NOTHING;
