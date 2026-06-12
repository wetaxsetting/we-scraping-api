-- ============================================================================
-- manual_seed_napas_bank_info.sql
--
-- ⚠️  MANUAL DBA EXECUTION ONLY — DO NOT AUTO-RUN.
--
--   * This file is intentionally placed under db/manual/, NOT under
--     db/migration/.  It is NOT a Flyway migration and must never be moved
--     into the migration folder.  Flyway is disabled in this project
--     (ddl-auto: validate; Flyway removed 2026-06-10) — nothing here runs at
--     application startup.
--   * A DBA must execute this script by hand against the target database,
--     after confirming the schema (the napas_bank_info table) already exists.
--   * Idempotent: re-running is safe — ON CONFLICT DO NOTHING skips rows that
--     already exist.  No UPDATE / overwrite of existing rows.
--
-- Purpose
--   Seeds NAPAS_BANK_INFO — the NAPAS bank-code lookup (napas_bank_cd ->
--   short/long names + the local bank_cd it maps to) — empty on a fresh
--   refactor database.  Used for NAPAS routing / bank-name resolution.
--
-- Load order (no hard FK)
--   bank_cd here references the bank_infm registry conceptually, but there is
--   NO foreign-key constraint on napas_bank_info.bank_cd, so this table can be
--   loaded independently.  Note: several bank_cd values (e.g. 11704202..11704243)
--   are NOT present in manual_seed_bank_infm.sql; because there is no FK, these
--   rows still insert cleanly.  Encoding: file is UTF-8 — Vietnamese diacritics
--   are stored verbatim.
--
-- Table / conflict key
--   Table   : napas_bank_info
--   PK      : napas_bank_info_pk (napas_bank_cd)   ← single-column primary key.
--   NOT NULL columns : napas_bank_cd, short_name (both present in every row).
--   Data    : 65 rows, supplied by the requester (real JEX DEV registry).
--
-- Execution (example — adjust connection + schema to your environment)
--   psql "host=... dbname=... user=..." -v ON_ERROR_STOP=1 \
--        -f manual_seed_napas_bank_info.sql
--   If your search_path does not include the schema that owns the table,
--   prefix the table name accordingly (e.g. wecmsdevdb.napas_bank_info).
-- ============================================================================

INSERT INTO napas_bank_info (napas_bank_cd,short_name,long_name_en,long_name_vn,bank_cd) VALUES
	 ('970416','ACB','Asia Commercial Bank','Ngân hàng TMCP Á Châu','11704011'),
	 ('970405','Agribank','Vietnam Bank for Agriculture and Rural Development','Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam','11704100'),
	 ('970409','BacABank','Bac A Commercial Joint Stock Bank','Ngân hàng TMCP Bắc Á','11704202'),
	 ('970438','BaoVietBank','Baoviet Joint Stock Commercial Bank','Ngân hàng TMCP Bảo Việt','11704203'),
	 ('970418','BIDV','Joint Stock Commercial Bank for Investment and Development of Vietnam','Ngân hàng TMCP Đầu tư và Phát triển Việt Nam','11704008'),
	 ('546034','CAKE by VPBank','Cake digital bank','Ngân hàng số Cake by VPBank','11704204'),
	 ('422589','CIMB VN','CIMB Bank (Vietnam) Ltd.','Ngân hàng TNHH MTV CIMB Việt Nam','11704205'),
	 ('970446','CoopBank','Co-operative bank of VietNam','Ngân hàng Hợp tác xã Việt Nam','11704206'),
	 ('796500','DBS Bank','The Development Bank of Singapore Limited','Ngân hàng TNHH MTV Phát triển Singapore','11704207'),
	 ('970406','Dong A Bank','DongA Joint Stock Commercial Bank','Ngân hàng TMCP Đông Á','11704208')
ON CONFLICT (napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_info (napas_bank_cd,short_name,long_name_en,long_name_vn,bank_cd) VALUES
	 ('970431','Eximbank','Vietnam Export Import Commercial Joint Stock Bank','Ngân hàng Thương Mại Cổ Phần Xuất Nhập Khẩu Việt Nam','11704209'),
	 ('970464','FCCOM','Finance Company Limited for Community','Công ty tài chính trách nhiệm hữu hạn một thành viên Cộng Đồng','11704210'),
	 ('970408','GPBank','Global Petro Sole Member Limited Commercial Bank','Ngân Hàng Thương Mại Trách Nhiệm Hữu Hạn Một Thành Viên Dầu Khí Toàn Cầu','11704211'),
	 ('970437','HD Bank','Ho Chi Minh City Development Joint Stock Commercial Bank','Ngân hàng TMCP Phát triển Thành phố Hồ Chí Minh','11704101'),
	 ('970442','Hong Leong Bank','Hong Leong Bank Vietnam Limited','Ngân hàng TNHH MTV Hong Leong Việt Nam','11704212'),
	 ('458761','HSBC VN','HSBC Bank (Vietnam) Limited','Ngân hàng TNHH Một thành viên HSBC (Việt Nam)','11704213'),
	 ('970455','IBK HA NOI','Industrial Bank of Korea – Hanoi Branch','Ngân hàng Công nghiệp Hàn Quốc – Chi nhánh Hà Nội','11704003'),
	 ('970456','IBK HCM','Industrial Bank of Korea – Ho Chi Minh City Branch','Ngân hàng Industrial Bank of Korea – Chi nhánh TP. Hồ Chí Minh','11704003'),
	 ('970434','IVB','Indovina Bank Limited','Ngân hàng Trách nhiệm hữu hạn Indovina','11704214'),
	 ('668888','Kasikornbank HCM','KASIKORNBANK Public Company Limited – Ho Chi Minh Branch','Ngân hàng Đại chúng TNHH KASIKORNBANK – Chi nhánh TP. Hồ Chí Minh','11704215')
ON CONFLICT (napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_info (napas_bank_cd,short_name,long_name_en,long_name_vn,bank_cd) VALUES
	 ('970466','KEBHANAHCM','KEB Hana Bank – Ho Chi Minh City Branch','Ngân hàng KEB HANA chi nhánh TP. Hồ Chí Minh','11704080'),
	 ('970467','KEBHANAHN','KEB Hana Bank – Hanoi Branch','Ngân hàng KEB HANA chi nhánh Hà Nội','11704080'),
	 ('963399','UMEE by Kienlongbank','UMEE by Kienlongbank','UMEE by Kienlongbank','11704216'),
	 ('970452','Kienlongbank','Kien Long Commercial Joint - Stock Bank','Ngân hàng Thương mại Cổ phần Kiên Long','11704217'),
	 ('970462','KB HANOI BRANCH','KOOKMIN BANK - HANOI BRANCH','NGÂN HÀNG KOOKMIN - CHI NHÁNH HÀ NỘI','11704013'),
	 ('970463','KB HO CHI MINH BRANCH','KOOKMIN BANK - Ho Chi Minh City Branch','NGÂN HÀNG KOOKMIN - CHI NHÁNH TP. Hồ Chí Minh','11704013'),
	 ('963369','Liobank','Liobank by OCB','Liobank - Ngân hàng số Liobank by OCB','11704219'),
	 ('970422','MB','Military Commercial Joint Stock Bank','Ngân hàng Thương mại Cổ phần Quân đội','11704007'),
	 ('970468','MIRAE ASSET FINANCE COMPANY VIT NAM LIMITED','Mirae Asset Finance Company Vietnam','Công ty Tài Chính Mirae Asset Việt Nam','11704220'),
	 ('970426','MSB','Vietnam Maritime Commercial Joint Stock Bank','Ngân hàng TMCP Hàng Hải Việt Nam','11704014')
ON CONFLICT (napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_info (napas_bank_cd,short_name,long_name_en,long_name_vn,bank_cd) VALUES
	 ('970428','Nam A Bank','NAM A BANK COMMERCIAL JOINT STOCK BANK','Ngân hàng TMCP Nam Á','11704221'),
	 ('970419','NCB','National Citizen Commercial Joint Stock Bank','Ngân hàng TMCP Quốc Dân','11704222'),
	 ('801011','NongHyup CN Ha Noi','NONGHYUP BANK HANOI BRANCH','Ngân Hàng Nonghyup - Chi Nhánh Hà Nội','11704223'),
	 ('970448','OCB','ORIENT COMMERCIAL JOINT STOCK BANK','Ngân hàng TMCP Phương Đông','11704224'),
	 ('970414','Oceanbank','Oceanbank Commercial One Member Limited Liability Bank','Ngân hàng Thương mại TNHH một thành viên Đại Dương','11704225'),
	 ('970430','PG Bank','Petrolimex Group Commercial Joint Stock Bank','Ngân Hàng TMCP Xăng Dầu Petrolimex','11704226'),
	 ('970439','Public bank Vietnam Limited','Public Bank Vietnam Limited','Ngân hàng TNHH MTV Public Việt Nam','11704227'),
	 ('970412','PVcomBank','Vietnam Public Joint Stock Commercial Bank','Ngân hàng TMCP Đại Chúng Việt Nam','11704228'),
	 ('970403','Sacombank','Saigon Thuong Tin Commercial Joint Stock Bank','Ngân hàng thương mại cổ phần Sài Gòn Thương Tín','11704229'),
	 ('970400','Saigonbank','SAIGON BANK FOR INDUSTRY AND TRADE','Ngân Hàng Thương Mại Cổ Phần Sài Gòn Công Thương','11704230')
ON CONFLICT (napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_info (napas_bank_cd,short_name,long_name_en,long_name_vn,bank_cd) VALUES
	 ('970429','SCB','Sai Gon Joint Stock Commercial Bank','Ngân hàng Thương mại cổ phần Sài Gòn','11704231'),
	 ('970440','SeABank','Southeast Asia Commercial Joint Stock Bank','Ngân hàng TMCP Đông Nam Á','11704232'),
	 ('970443','SHB','Saigon – Hanoi Commercial Joint Stock Bank','Ngân hàng TMCP Sài Gòn – Hà Nội','11704233'),
	 ('970424','SHINHAN BANK','Shinhan Bank Vietnam Limited','Ngân hàng TNHH Một Thành Viên Shinhan Việt Nam','11704088'),
	 ('970410','Standard Chartered Bank','Ngân hàng TNHH Một thành viên Standard Chartered (Việt Nam)','Ngân hàng TNHH Một thành viên Standard Chartered (Việt Nam)','11704104'),
	 ('970407','Techcombank','Vietnam Technological and Commercial Joint Stock Bank','Ngân hàng Thương mại cổ phần Kỹ Thương Việt Nam','11704002'),
	 ('970423','Tien Phong Bank','Tien Phong Commercial Joint Stock Bank','Ngân hàng Thương mại Cổ phần Tiên Phong','11704103'),
	 ('963388','TIMO by Viet Capital Bank','Timo Digital Bank','Ngân hàng số Timo','11704234'),
	 ('970458','UOB Viet Nam','United Overseas Bank (Vietnam) Limited','Ngân hàng TNHH một thành viên United Overseas Bank','11704235'),
	 ('970441','VIB','Vietnam International Commercial Joint Stock Bank','Ngân hàng Thương mại Cổ phần Quốc tế Việt Nam','11704012')
ON CONFLICT (napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_info (napas_bank_cd,short_name,long_name_en,long_name_vn,bank_cd) VALUES
	 ('970427','Viet A Bank','VIETNAM ASIA JOINT STOCK COMMERCIAL BANK','Ngân hàng Thương mại Cổ phần Việt Á','11704236'),
	 ('970454','Viet Capital Bank','Viet Capital Commercial Joint Stock Bank','Ngân hàng TMCP Bản Việt','11704237'),
	 ('546035','Ubank by VPBank','Übank Digital Bank','Ngân hàng số Übank','11704102'),
	 ('970425','ABBank','An Binh Commercial Joint Stock Bank','Ngân hàng TMCP An Bình','11704106'),
	 ('970433','VietBank','Viet Nam Thuong Tin Commercial Joint Stock Bank','Ngân hàng thương mại cổ phần Việt Nam Thương Tín','11704238'),
	 ('970436','Vietcombank','Joint Stock Commercial Bank for Foreign Trade of Vietnam','Ngân hàng thương mại cổ phần Ngoại thương Việt Nam','11704004'),
	 ('970415','Vietinbank','VIETNAM JOINT STOCK COMMERCIAL BANK FOR INDUSTRY AND TRADE','Ngân hàng TMCP Công Thương Việt Nam','11704001'),
	 ('1207001','VBSP','Vietnam Bank for Social Policies','Ngân hàng Chính sách xã hội','11704239'),
	 ('970444','CBBank','Construction Joint Stock Commercial Bank','Ngân hàng Thương mại TNHH MTV Xây dựng Việt Nam','11704240'),
	 ('971005','Viettel Money by Viettel Digital Services','Viettel Digital Services Corporation (Viettel Money)','Tổng Công ty Dịch vụ Số Viettel (Viettel Money)','11704241')
ON CONFLICT (napas_bank_cd) DO NOTHING;

INSERT INTO napas_bank_info (napas_bank_cd,short_name,long_name_en,long_name_vn,bank_cd) VALUES
	 ('971011','VNPT Money','VNPT DIGITAL FINANCIAL SERVICE CENTER (VNPT Money)','Trung Tâm dịch vụ tài chính số VNPT (VNPT Money)','11704242'),
	 ('970432','VPBank','Vietnam Prosperity Joint-Stock Commercial Bank','Ngân hàng Thương mại cổ phần Việt Nam Thịnh Vượng','11704102'),
	 ('970421','VRB','Vietnam - Russia Joint Venture Bank','Ngân hàng Liên doanh Việt - Nga','11704243'),
	 ('970457','Wooribank VN','Woori Bank Vietnam Limited','Ngân hàng TNHH MTV Woori Việt Nam','11704020'),
	 ('970449','LPBank','Fortune Vietnam Joint Stock Commercial Bank','Ngân hàng Thương mại Cổ phần Bưu điện Liên Việt','11704218')
ON CONFLICT (napas_bank_cd) DO NOTHING;
