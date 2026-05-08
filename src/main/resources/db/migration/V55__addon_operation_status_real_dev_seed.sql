-- ============================================================================
-- V55__addon_operation_status_real_dev_seed.sql
--
-- Replace V17's fabricated demo seed for `OPRT_STTS_INFO` with the 30
-- most recent real DEV rows for tenant `VN_VNM0004_000001`. Per the
-- Data Migration Rule (wecms-api-main/CLAUDE.md): max 30 rows, only
-- `VN_VNM0004_000001`, real DEV rows only — never fabricated.
--
-- The real DEV table (`wecmsvndev.oprt_stts_info`) has 202 rows for
-- this tenant across 6 distinct `companystatus` values. The 30 rows
-- below are the latest by `searchdatetime DESC` (audit query run
-- 2026-05-04). They include 1 "ngừng hoạt động" row + 29
-- "đang hoạt động" variants — enough to exercise the canonical
-- 3-state coercion in `OperationStatusService.resolveStatusCd(...)`
-- and the status-filter dropdown.
--
-- Also (Sprint 1 audit conclusion):
--
-- Schema parity verified — DEV `oprt_stts_info` matches V16 column-for-
-- column. No corrective `ALTER TABLE` migration needed.
--
-- Ordering: parents first (no FK chain on this table — single-table
-- seed). Idempotent via `INSERT … ON CONFLICT (BIZ_KEY, TAXCODE) DO
-- NOTHING`. Re-running is safe — V17's stub rows under synthetic
-- taxcodes (0102345678..0106789012) are deleted in step 1 below; the
-- DEV taxcodes don't collide with the V17 stubs.
--
-- Three things happen, in order:
--   1. DELETE the V17 stub rows (synthetic taxcodes only — preserves
--      any future migration that adds real rows under the same tenant).
--   2. INSERT 30 real DEV rows.
--   3. (No View Setting changes — that's V56's job.)
-- ============================================================================

-- 1. Drop V17 stub rows (synthetic taxcodes).
DELETE FROM OPRT_STTS_INFO
 WHERE BIZ_KEY = 'VN_VNM0004_000001'
   AND TAXCODE IN ('0102345678', '0103456789', '0104567890',
                   '0105678901', '0106789012');

-- 2. Real DEV seed — top 30 rows by searchdatetime DESC.
INSERT INTO OPRT_STTS_INFO
    (BIZ_KEY, TAXCODE, COMPANYNAME, COMPANYSTATUS, OFFICEADD,
     SEARCHDATETIME, REPRESENTATIVEFIRSTNAME, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('VN_VNM0004_000001', '1201496252-001', 'VĂN PHÒNG ĐẠI DIỆN CÔNG TY CỔ PHẦN WEBCASH VIỆT NAM',
     'NNT ngừng hoạt động và đã hoàn thành thủ tục chấm dứt hiệu lực MST',
     'Căn số B-00.21, Tầng trệt, Chung cư lô C1 - Số 6, Đường D9, , TP Hồ Chí Minh',
     '20250929170149', '', 'lamine@gmail.com', '20250408032736', 'lamine@gmail.com', '20260503150012'),

    ('VN_VNM0004_000001', '0310603505', 'CÔNG TY TNHH ĐƯỜNG NÉT',
     'NNT đang hoạt động',
     'Officetel SAV.8-09.10, The Sun Avenue, số 28 Mai Chí Thọ, Phường An Phú(Hết hiệu lực), TP Hồ Chí Minh',
     '20250827152707', '', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150013'),

    ('VN_VNM0004_000001', '3600495818', 'CÔNG TY CỔ PHẦN HÀNG TIÊU DÙNG BIÊN HÒA',
     'NNT đang hoạt động',
     'Khu công nghiệp Biên Hòa 1, Phường Trấn Biên, Đồng Nai',
     '20250827144419', '', 'info@webcashvietnam.com', '20251007093410', 'info@webcashvietnam.com', '20260503150013'),

    ('VN_VNM0004_000001', '3700337149', 'CÔNG TY TNHH HỮU TÍN',
     'NNT đang hoạt động',
     'Lô CN19 Ô số 4A, Giáp đường số 07, Khu công nghiệp Sóng Thần 3, Phường Bình Dương, TP Hồ Chí Minh',
     '20250822163944', '', 'lamine@gmail.com', '20241223082907', 'lamine@gmail.com', '20260503150014'),

    ('VN_VNM0004_000001', '1201496252', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'NNT đang hoạt động',
     'LK08-13, Khu phố 3, Phường Thới Sơn, Đồng Tháp',
     '20250822163121', '', 'lamine@gmail.com', '20250820063247', 'lamine@gmail.com', '20260503150012'),

    ('VN_VNM0004_000001', '0318793907', 'CÔNG TY TNHH LY THÁI BAKERY',
     'NNT đang hoạt động',
     'Tầng 6 tháp Golden House, Tòa nhà Sunwah Pearl, số 90 Nguyễn, Phường Thạnh Mỹ Tây, TP Hồ Chí Minh',
     '20250809075713', 'Thái Phi Đán', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150013'),

    ('VN_VNM0004_000001', '0318743945', 'CÔNG TY TNHH NEGA FOOD',
     'NNT đang hoạt động',
     'Tầng Lửng, R4-23 Hưng Phước 4, Phường Tân Hưng, TP Hồ Chí Minh',
     '20250806163146', 'Lê Thiên Diệu', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150016'),

    ('VN_VNM0004_000001', '3603277021', 'CÔNG TY TRÁCH NHIỆM HỮU HẠN HYOSUNG ĐỒNG NAI',
     'NNT đang hoạt động',
     'Đường N3, KCN Nhơn Trạch 5, Xã Nhơn Trạch, Đồng Nai',
     '20250728044213', 'Bae In Han', 'info@webcashvietnam.com', '20251007093410', 'info@webcashvietnam.com', '20260503150017'),

    ('VN_VNM0004_000001', '3603512483', 'CÔNG TY TNHH OT MOTOR VINA',
     'NNT đang hoạt động',
     'Lô đất số B-3.1, Phân KCN Nhơn Trạch 6B, KCN Nhơn Trạch 6, Xã Phước An, Đồng Nai',
     '20250727142450', 'Jeong Namki', 'info@webcashvietnam.com', '20251007093410', 'info@webcashvietnam.com', '20260503150017'),

    ('VN_VNM0004_000001', '3600272184', 'CÔNG TY TNHH  XĂNG DẦU SỐ 3',
     'NNT đang hoạt động',
     '511, tổ 32, đường Lê Duẩn, khu Cầu Xéo, Xã Long Thành, Đồng Nai',
     '20250726222008', 'Lê Thành Nhân', 'info@webcashvietnam.com', '20251007093410', 'info@webcashvietnam.com', '20260503150013'),

    ('VN_VNM0004_000001', '0302359405', 'CÔNG TY CỔ PHẦN CHẾ BIẾN THỦY HẢI SẢN LIÊN THÀNH',
     'NNT đang hoạt động',
     '243 Bến Vân Đồn, Phường Vĩnh Hội, TP Hồ Chí Minh',
     '20250723134705', 'Hoàng Thị Kim Châu', 'info@webcashvietnam.com', '20251007093408', 'info@webcashvietnam.com', '20260503150013'),

    ('VN_VNM0004_000001', '0101600331', 'Văn phòng đại diện Posco Eco & Challenge Co., Ltd tại Thành phố Hà Nội',
     'NNT đang hoạt động',
     'P.401,412 Tầng 4 TTTM Daeha, 360 đường Kim Mã, Phường Giảng Võ, TP Hà Nội',
     '20250722143821', 'Kim Young Gi', 'info@webcashvietnam.com', '20251007093408', 'info@webcashvietnam.com', '20260503150015'),

    ('VN_VNM0004_000001', '3600790154', 'CÔNG TY TNHH SAMIL VINA',
     'NNT đang hoạt động',
     'KCN Long Thành, Xã An Phước, Đồng Nai',
     '20250707113344', 'Lee Sang Hoon', 'info@webcashvietnam.com', '20251007093410', 'info@webcashvietnam.com', '20260503150015'),

    ('VN_VNM0004_000001', '0309103635', 'NGÂN HÀNG TNHH MỘT THÀNH VIÊN SHINHAN VIỆT NAM',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Tòa nhà The Mett, Số 15 Đường Trần Bạch Đằng, Phường Thủ Thiêm, Thành phố Thủ Đức, TP Hồ Chí Minh',
     '20250404132626', 'Kang Gewwon', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150016'),

    ('VN_VNM0004_000001', '0309103635-018', 'NGÂN HÀNG TNHH MỘT THÀNH VIÊN SHINHAN VIỆT NAM - CHI NHÁNH ĐÔNG SÀI GÒN',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'LD 01-03 và LD 01-04, Tòa nhà Lexington, số 67, đường Mai Ch, Phường An Phú, Quận 2 (Hết hiệu lực), TP Hồ Chí Minh',
     '20250404132626', 'Dương Thị Loan', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150016'),

    ('VN_VNM0004_000001', '0309103635-022', 'NGÂN HÀNG TNHH MỘT THÀNH VIÊN SHINHAN VIỆT NAM - CHI NHÁNH PHÚ MỸ HƯNG',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Tầng 1 (trệt) và 2, Khu phố Riverpark Residence, 334-336-338 Đặng Đức Thuật, Phường Tân Phong, Quận 7, TP Hồ Chí Minh',
     '20250404132626', 'Park Sung Jin', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150014'),

    ('VN_VNM0004_000001', '0318659404', 'CÔNG TY TNHH E-HO F&B',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     '79-81 Võ Nguyên Giáp, Phường Thảo Điền, Thành phố Thủ Đức, TP Hồ Chí Minh',
     '20250301014701', 'Kwon Soonbeom', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150015'),

    ('VN_VNM0004_000001', '0110269067-001', 'CHI NHÁNH THÀNH PHỐ HỒ CHÍ MINH - CÔNG TY CỔ PHẦN DI CHUYỂN XANH VÀ THÔNG MINH GSM',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Lầu 20A, Vincom Đồng Khởi, 72 Lê Thánh Tôn, Phường Bến Nghé, Quận 1, TP Hồ Chí Minh',
     '20241018165520', 'Nguyễn Văn Thanh', 'info@webcashvietnam.com', '20251007093408', 'info@webcashvietnam.com', '20260503150014'),

    ('VN_VNM0004_000001', '0318057706-001', 'CHI NHÁNH CÔNG TY TNHH DAEYOUNG ONE',
     'NNT đang hoạt động',
     'Phân khu 6A, Khu công nghiệp Nhơn Trạch VI, Xã Long Thọ, Huyện Nhơn Trạch, Đồng Nai',
     '20240729144934', 'Kim Kwangyoon', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150013'),

    ('VN_VNM0004_000001', '0317882865', 'CÔNG TY TNHH HYOSUNG GLOBAL LOGISTICS VINA',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Lô L9-08B-09, Tầng 09, Tòa nhà Vincom Center Đồng Khởi, 72 L, Phường Bến Nghé, Quận 1, TP Hồ Chí Minh',
     '20240718163737', 'Lee Joon Hyong', 'WeCms226@gmail.com', '20250326015549', 'lamine@gmail.com', '20260503150014'),

    ('VN_VNM0004_000001', '3603486995', 'NGÔ THỊ LÂM THAO',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Tổ 36, Kp Long Đức 1, Phường Tam Phước, Thành phố Biên Hoà, Đồng Nai',
     '20240718130533', 'Ngô Thị Lâm Thao', 'lamine@gmail.com', '20241223080547', 'lamine@gmail.com', '20260503150012'),

    ('VN_VNM0004_000001', '0317256025', 'VĂN PHÒNG ĐẠI DIỆN HANSOLL TEXTILE, LTD. TẠI THÀNH PHỐ HỒ CHÍ MINH',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Phòng 5 01 Tầng 5, Tháp 6, số 628C, đường Võ Nguyên Giáp, Phường An Phú, Thành phố Thủ Đức, TP Hồ Chí Minh',
     '20240718121524', 'YUN HYUNSUB', 'WeCms226@gmail.com', '20250326075745', 'lamine@gmail.com', '20260503150012'),

    ('VN_VNM0004_000001', '0317277699', 'BAN QUẢN TRỊ CHUNG CƯ SARICA',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Số 6 Đường 9, Phường An Lợi Đông, Thành phố Thủ Đức, TP Hồ Chí Minh',
     '20240718121354', 'Nguyễn Văn Dòng', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150015'),

    ('VN_VNM0004_000001', '0900880639', 'CÔNG TY TNHH DORCO LIVING VINA',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Đường D3, Khu G, Khu công nghiệp Hòa Mạc, Phường Hòa Mạc, Thị Xã Duy Tiên, Hà Nam',
     '20240718110428', 'Baek Hakgi', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150017'),

    ('VN_VNM0004_000001', '0317254980', 'VĂN PHÒNG CÔNG CHỨNG BÙI NGỌC LONG',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     '163 Lương Định Của, Phường An Khánh, Thành phố Thủ Đức, TP Hồ Chí Minh',
     '20240718104343', 'Bùi Ngọc Long', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150013'),

    ('VN_VNM0004_000001', '3901180642', 'CÔNG TY TNHH ILSHIN VIỆT NAM',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Lô số 30-4, Đường N14, Khu Công nghiệp Phước Đông, Xã Bàu Đồn, Huyện Gò Dầu, Tây Ninh',
     '20240604180313', 'An Hansoon', 'info@webcashvietnam.com', '20251007093410', 'info@webcashvietnam.com', '20260503150012'),

    ('VN_VNM0004_000001', '3700240066', 'CÔNG TY TNHH NEW TOYO PULPPY (VIỆT NAM)',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Số 8, Đường số 6, khu công nghiệp Việt Nam-Singapore, Phường Bình Hòa, Thành phố Thuận An, Bình Dương',
     '20240604120115', 'Yen Wen Hwa', 'info@webcashvietnam.com', '20251007093410', 'info@webcashvietnam.com', '20260503150017'),

    ('VN_VNM0004_000001', '3603435207', 'CÔNG TY TNHH NASAN HCM VIỆT NAM',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Đường số 6, KCN An Phước, Xã An Phước, Huyện Long Thành, Đồng Nai',
     '20240604115346', 'Park, Chun Bong', 'info@webcashvietnam.com', '20251007093410', 'info@webcashvietnam.com', '20260503150017'),

    ('VN_VNM0004_000001', '0317970938', 'CÔNG TY TNHH GOURMET82',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Số 02 Thi Sách, Phường Bến Nghé, Quận 1, TP Hồ Chí Minh',
     '20240604092412', 'Đặng Thị Hiếu Hòa', 'info@webcashvietnam.com', '20251007093409', 'info@webcashvietnam.com', '20260503150017'),

    ('VN_VNM0004_000001', '3800335628-002', 'VĂN PHÒNG ĐẠI DIỆN CÔNG TY TNHH SAM WOON IND TẠI THÀNH PHỐ HỒ CHÍ MINH',
     'NNT đang hoạt động (đã được cấp GCN ĐKT)',
     'Số 10A đường Nguyễn Thị Minh Khai, Phường Đa Kao, Quận 1, TP Hồ Chí Minh',
     '20240604085126', 'Yun Sung Hwan', 'lamine@gmail.com', '20241224025858', 'lamine@gmail.com', '20260503150016')
ON CONFLICT (BIZ_KEY, TAXCODE) DO NOTHING;
