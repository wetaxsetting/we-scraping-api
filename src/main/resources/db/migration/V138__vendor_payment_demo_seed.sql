-- ============================================================================
-- V122 — Vendor Payment demo seed (Phase A wiring verification).
--
-- Seeds 15 SCDUL_PYMN_SUMR + 15 matching SCDUL_PYMN_SUMR_DETL rows for
-- tenant VN_VNM0004_000001 with SCDUL_PYMN_TYPE_CD = '01' (Vendor).
-- Mirrors the V115 Internal Transfer seed pattern; the two modules share
-- the same physical table — only the type discriminator differs.
--
-- Why this seed: Phase A of the Vendor Payment port wires
--   GET /api/v1/payment/vendor-payment            (Scheduled list)
--   GET /api/v1/payment/vendor-payment/results    (Result list)
-- to the live Spring backend. Without rows the page renders empty on a
-- fresh local DB (no upstream module writes vendor rows yet — vendor
-- create / upload is Phase B+).
--
-- Date-window note: REG_DTM values are spread across the last 30 days
-- from 2026-05-14 (today) so all 15 rows show up under the page's
-- default "Last 30 Days" filter without the user widening the window.
--
-- Status mix:
--   - 9 rows with APPR_SEQ_NO + APPR_STTS='C' (approved, dispatched)
--   - 2 rows with APPR_SEQ_NO + APPR_STTS=NULL (in approval, pending)
--   - 1 row  with APPR_SEQ_NO + APPR_STTS='X' (rejected)
--   - 3 rows with no APPR_SEQ_NO (drafts — amber row tint on the page)
--
-- SCDUL_PYMN_NO values 401-415 — disjoint from V115's 363-375 range so
-- the PK never collides regardless of payment-type stamping.
--
-- Idempotent via ON CONFLICT DO NOTHING.
-- ============================================================================

INSERT INTO SCDUL_PYMN_SUMR
    (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_TYPE_CD,
     OUT_BANK_CD, WDRL_ACT_NO,
     CCY_CD, WDRL_CCY_CD,
     TITL, TOTAL_TRAN_AMT, TOT_TRNS_CNT,
     APPR_SEQ_NO, APPR_STTS, TRNF_YN, ERP_PROC_CMPL_YN,
     ORG_FILE_NM,
     REG_ID, REG_DTM, MOD_DTM)
VALUES
    -- Latest — approved + dispatched
    ('VN_VNM0004_000001', '415', '01', '970424', '700005285299',
     'VND', 'VND',
     'Vendor_Payment_260513', 50000000.000000, 8,
     '2015', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_015.xlsx',
     'lamine@gmail.com', '20260513090000', '20260513090530'),

    ('VN_VNM0004_000001', '414', '01', '970424', '700005285299',
     'USD', 'USD',
     'Vendor_Payment_USD_260512', 12000.000000, 3,
     '2014', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_014.xlsx',
     'lamine@gmail.com', '20260512143000', '20260512143400'),

    -- In approval (no final outcome yet — APPR_STTS NULL)
    ('VN_VNM0004_000001', '413', '01', '970457', '100912150384',
     'VND', 'VND',
     'Vendor_Payment_260511', 35000000.000000, 12,
     '2013', NULL, 'N', 'N',
     'WeCMS_Vendor_Payment_upload_EN_013.xlsx',
     'lamine@gmail.com', '20260511110000', '20260511110000'),

    -- Rejected
    ('VN_VNM0004_000001', '412', '01', '970424', '700005285299',
     'VND', 'VND',
     'Vendor_Payment_260510', 8500000.000000, 4,
     '2012', 'X', 'N', 'N',
     'WeCMS_Vendor_Payment_upload_EN_012.xlsx',
     'lamine@gmail.com', '20260510084500', '20260510153000'),

    -- Draft — no approval ref (amber row tint on the page)
    ('VN_VNM0004_000001', '411', '01', '970424', '700005285299',
     'VND', 'VND',
     'Vendor_Payment_260509_draft', 4200000.000000, 2,
     NULL, NULL, 'N', 'N',
     'WeCMS_Vendor_Payment_upload_EN_011.xlsx',
     'lamine@gmail.com', '20260509145500', '20260509145500'),

    ('VN_VNM0004_000001', '410', '01', '970457', '100912150384',
     'USD', 'USD',
     'Vendor_Payment_USD_260508', 5500.000000, 6,
     '2010', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_010.xlsx',
     'lamine@gmail.com', '20260508101200', '20260508101900'),

    ('VN_VNM0004_000001', '409', '01', '970424', '700005285299',
     'VND', 'VND',
     'Vendor_Payment_260506', 18500000.000000, 7,
     '2009', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_009.xlsx',
     'lamine@gmail.com', '20260506093000', '20260506093500'),

    -- In approval (no outcome yet)
    ('VN_VNM0004_000001', '408', '01', '970424', '703011613756',
     'VND', 'VND',
     'Vendor_Payment_260505', 9800000.000000, 5,
     '2008', NULL, 'N', 'N',
     'WeCMS_Vendor_Payment_upload_EN_008.xlsx',
     'lamine@gmail.com', '20260505152000', '20260505152000'),

    ('VN_VNM0004_000001', '407', '01', '970457', '100912150384',
     'VND', 'VND',
     'Vendor_Payment_260503', 22000000.000000, 9,
     '2007', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_007.xlsx',
     'lamine@gmail.com', '20260503111500', '20260503112100'),

    ('VN_VNM0004_000001', '406', '01', '970424', '700005285299',
     'USD', 'USD',
     'Vendor_Payment_USD_260502', 8200.000000, 4,
     '2006', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_006.xlsx',
     'lamine@gmail.com', '20260502143000', '20260502143400'),

    -- Draft
    ('VN_VNM0004_000001', '405', '01', '970424', '700005285299',
     'VND', 'VND',
     'Vendor_Payment_260430_draft', 6700000.000000, 3,
     NULL, NULL, 'N', 'N',
     'WeCMS_Vendor_Payment_upload_EN_005.xlsx',
     'lamine@gmail.com', '20260430100000', '20260430100000'),

    ('VN_VNM0004_000001', '404', '01', '970457', '100912150384',
     'VND', 'VND',
     'Vendor_Payment_260428', 15000000.000000, 6,
     '2004', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_004.xlsx',
     'lamine@gmail.com', '20260428084500', '20260428085100'),

    ('VN_VNM0004_000001', '403', '01', '970424', '703011613756',
     'VND', 'VND',
     'Vendor_Payment_260425', 11500000.000000, 4,
     '2003', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_003.xlsx',
     'lamine@gmail.com', '20260425160000', '20260425160500'),

    -- Draft
    ('VN_VNM0004_000001', '402', '01', '970424', '700005285299',
     'VND', 'VND',
     'Vendor_Payment_260422_draft', 3300000.000000, 2,
     NULL, NULL, 'N', 'N',
     'WeCMS_Vendor_Payment_upload_EN_002.xlsx',
     'lamine@gmail.com', '20260422094500', '20260422094500'),

    ('VN_VNM0004_000001', '401', '01', '970457', '100912150384',
     'USD', 'USD',
     'Vendor_Payment_USD_260420', 4400.000000, 2,
     '2001', 'C', 'Y', 'N',
     'WeCMS_Vendor_Payment_upload_EN_001.xlsx',
     'lamine@gmail.com', '20260420110000', '20260420110500')
ON CONFLICT (BIZ_KEY, SCDUL_PYMN_NO) DO NOTHING;


-- ---------------------------------------------------------------------------
-- SCDUL_PYMN_SUMR_DETL — one demo line per master row.
-- SCDUL_PYMN_DETL_NO values 601-615 — disjoint from V115's 481-493 range.
-- ---------------------------------------------------------------------------
INSERT INTO SCDUL_PYMN_SUMR_DETL
    (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO,
     TRAN_AMT, TRNF_CCY_CD,
     ANT_DIV_CD, RCPT_NM, TAR_BANK_CD, FINC_MDA_NO,
     TRAN_USAG, DOC_NO, NOTE, CHART_ACCT_CD,
     REG_ID, REG_DTM, MOD_DTM)
VALUES
    ('VN_VNM0004_000001', '415', '615',
     50000000.000000, 'VND',
     'NAPAS', 'ABC Vendor JSC', '970422', '0123456789012',
     'Office supplies — May invoice', 'INV-2026-0513', 'May supplies', '5001001',
     'lamine@gmail.com', '20260513090000', '20260513090000'),

    ('VN_VNM0004_000001', '414', '614',
     12000.000000, 'USD',
     'NAPAS', 'International Materials Ltd', '970418', '00012345678',
     'Material import — May lot 2', 'INV-2026-0512-USD', 'Raw materials', '5001002',
     'lamine@gmail.com', '20260512143000', '20260512143000'),

    ('VN_VNM0004_000001', '413', '613',
     35000000.000000, 'VND',
     'NAPAS', 'Logistics Partners Co', '970436', '99001112223',
     'Logistics — May rebill', 'INV-2026-0511', 'Logistics', '5001003',
     'lamine@gmail.com', '20260511110000', '20260511110000'),

    ('VN_VNM0004_000001', '412', '612',
     8500000.000000, 'VND',
     'NAPAS', 'Cleaning Services VN', '970422', '0987654321098',
     'Monthly cleaning', 'INV-2026-0510', 'Service', '5001004',
     'lamine@gmail.com', '20260510084500', '20260510084500'),

    ('VN_VNM0004_000001', '411', '611',
     4200000.000000, 'VND',
     'NAPAS', 'Stationery Wholesale', '970418', '11122334455',
     'Stationery — Q2 draft', 'INV-2026-0509', 'Stationery', '5001001',
     'lamine@gmail.com', '20260509145500', '20260509145500'),

    ('VN_VNM0004_000001', '410', '610',
     5500.000000, 'USD',
     'NAPAS', 'Software License Co', '970418', '5544332211',
     'Q2 software renewal', 'INV-2026-0508-USD', 'Software', '5001005',
     'lamine@gmail.com', '20260508101200', '20260508101200'),

    ('VN_VNM0004_000001', '409', '609',
     18500000.000000, 'VND',
     'NAPAS', 'Equipment Vendors VN', '970422', '0234567890123',
     'Server rack purchase', 'INV-2026-0506', 'IT equipment', '5001006',
     'lamine@gmail.com', '20260506093000', '20260506093000'),

    ('VN_VNM0004_000001', '408', '608',
     9800000.000000, 'VND',
     'NAPAS', 'Marketing Agency Ltd', '970436', '88001112223',
     'May marketing fee', 'INV-2026-0505', 'Marketing', '5001007',
     'lamine@gmail.com', '20260505152000', '20260505152000'),

    ('VN_VNM0004_000001', '407', '607',
     22000000.000000, 'VND',
     'NAPAS', 'Construction Co Ltd', '970418', '6677889900',
     'Office renovation phase 2', 'INV-2026-0503', 'Construction', '5001008',
     'lamine@gmail.com', '20260503111500', '20260503111500'),

    ('VN_VNM0004_000001', '406', '606',
     8200.000000, 'USD',
     'NAPAS', 'International Consultants', '970418', '7788990011',
     'April consulting fees', 'INV-2026-0502-USD', 'Consulting', '5001009',
     'lamine@gmail.com', '20260502143000', '20260502143000'),

    ('VN_VNM0004_000001', '405', '605',
     6700000.000000, 'VND',
     'NAPAS', 'Furniture Supplier VN', '970422', '0345678901234',
     'Meeting room furniture', 'INV-2026-0430', 'Furniture', '5001010',
     'lamine@gmail.com', '20260430100000', '20260430100000'),

    ('VN_VNM0004_000001', '404', '604',
     15000000.000000, 'VND',
     'NAPAS', 'Utilities Provider', '970436', '12345098765',
     'April utility bills', 'INV-2026-0428', 'Utility', '5001011',
     'lamine@gmail.com', '20260428084500', '20260428084500'),

    ('VN_VNM0004_000001', '403', '603',
     11500000.000000, 'VND',
     'NAPAS', 'Logistics Partners Co', '970436', '99001112223',
     'April logistics', 'INV-2026-0425', 'Logistics', '5001003',
     'lamine@gmail.com', '20260425160000', '20260425160000'),

    ('VN_VNM0004_000001', '402', '602',
     3300000.000000, 'VND',
     'NAPAS', 'Office Supplies Co', '970418', '4433221100',
     'Misc supplies draft', 'INV-2026-0422', 'Supplies', '5001001',
     'lamine@gmail.com', '20260422094500', '20260422094500'),

    ('VN_VNM0004_000001', '401', '601',
     4400.000000, 'USD',
     'NAPAS', 'International Materials Ltd', '970418', '00012345678',
     'April material lot', 'INV-2026-0420-USD', 'Raw materials', '5001002',
     'lamine@gmail.com', '20260420110000', '20260420110000')
ON CONFLICT (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO) DO NOTHING;
