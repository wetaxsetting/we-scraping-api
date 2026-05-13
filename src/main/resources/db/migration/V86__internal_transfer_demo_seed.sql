-- ============================================================================
-- V86 — Internal Transfer demo seed (Phase A wiring verification).
--
-- Seeds 10 SCDUL_PYMN_SUMR + 10 matching SCDUL_PYMN_SUMR_DETL rows for
-- the configured R&D tenant (VN_VNM0004_000001), pulled verbatim from
-- JEX DEV (`scdul_pymn_no` 363–375, `scdul_pymn_type_cd = '03'`,
-- ordered REG_DTM DESC).
--
-- Why this seed exists: the Phase A wiring (V85 + page rewire) ports
-- the Internal Transfer Scheduled-list endpoint to the Spring backend.
-- Without seeded rows the page renders empty (or falls back to the
-- static-UI mock during transition). This seed gives QA + the static
-- wiring real shape to verify against.
--
-- Date-window note: REG_DTM values are preserved from DEV verbatim per
-- the seed-precedent in V22 / V29 (no restamping). Eight of the ten
-- rows are from March 2026, two from May 11 2026 — so against today's
-- date (CLAUDE.md: 2026-05-13) the page's default "Last 30 Days"
-- filter shows the two May rows. Widening the filter to "Last 6
-- Months" reveals all ten. This deliberately matches DEV's historical
-- shape rather than fabricating recent dates.
--
-- MOD_DTM mirrors REG_DTM (DEV's MOD_DTM was not queried — this seed
-- intentionally treats the rows as never-modified, which is correct
-- for batches that have not been edited after their initial save).
--
-- Idempotent via ON CONFLICT DO NOTHING — safe to re-run after a DB
-- reset.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- SCDUL_PYMN_SUMR — 10 master rows
-- ---------------------------------------------------------------------------
INSERT INTO SCDUL_PYMN_SUMR
    (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_TYPE_CD,
     OUT_BANK_CD, WDRL_ACT_NO,
     CCY_CD, WDRL_CCY_CD,
     TITL, TOTAL_TRAN_AMT, TOT_TRNS_CNT,
     APPR_SEQ_NO, TRNF_YN, ERP_PROC_CMPL_YN,
     ORG_FILE_NM,
     REG_ID, REG_DTM, MOD_DTM)
VALUES
    ('VN_VNM0004_000001', '375', '03', '970457', '100912150384',
     'VND', 'USD',
     'WeCMS_Internal_Transfer_upload_EN (7)', 100000.000000, 1,
     '1540', 'N', 'N',
     'WeCMS_Internal_Transfer_upload_EN (7).xlsx',
     'lamine@gmail.com', '20260511075524', '20260511075524'),

    ('VN_VNM0004_000001', '374', '03', '970457', '100912150384',
     'VND', 'USD',
     'WeCMS_Internal_Transfer_upload_EN (7)', 100000.000000, 1,
     '1539', 'N', 'N',
     'WeCMS_Internal_Transfer_upload_EN (7).xlsx',
     'lamine@gmail.com', '20260511071313', '20260511071313'),

    ('VN_VNM0004_000001', '370', '03', '970424', '700005285299',
     'VND', 'USD',
     'WeCMS_Internal_Transfer_upload_EN', 100000.000000, 1,
     '1533', 'Y', 'N',
     'WeCMS_Internal_Transfer_upload_EN.xlsx',
     'lamine@gmail.com', '20260325073215', '20260325073215'),

    ('VN_VNM0004_000001', '369', '03', '970457', '100912150384',
     'USD', 'USD',
     'WOORI USD TO VND', 10.000000, 1,
     '1532', 'Y', 'N',
     'WOORI USD TO VND.xlsx',
     'lamine@gmail.com', '20260302075653', '20260302075653'),

    ('VN_VNM0004_000001', '368', '03', '970457', '100912150384',
     'VND', 'USD',
     'WOORI USD TO VND', 10000.000000, 1,
     '1531', 'Y', 'N',
     'WOORI USD TO VND.xlsx',
     'lamine@gmail.com', '20260302075459', '20260302075459'),

    ('VN_VNM0004_000001', '367', '03', '970424', '700005285299',
     'USD', 'USD',
     'SHINHAN USD TO VND', 10.000000, 1,
     '1530', 'Y', 'N',
     'SHINHAN USD TO VND.xlsx',
     'lamine@gmail.com', '20260302074643', '20260302074643'),

    ('VN_VNM0004_000001', '366', '03', '970424', '700005285299',
     'VND', 'USD',
     'SHINHAN USD TO VND', 10000.000000, 1,
     '1529', 'Y', 'N',
     'SHINHAN USD TO VND.xlsx',
     'lamine@gmail.com', '20260302074448', '20260302074448'),

    ('VN_VNM0004_000001', '365', '03', '970424', '700022964690',
     'USD', 'VND',
     'Internal_VND_SHINHAN-2', 10.000000, 1,
     NULL, 'N', 'N',
     'Internal_VND_SHINHAN-2.xlsx',
     'lamine@gmail.com', '20260302073154', '20260302073154'),

    ('VN_VNM0004_000001', '364', '03', '970424', '700022964690',
     'USD', 'VND',
     'Internal_VND_SHINHAN-2', 10.000000, 1,
     '1528', 'Y', 'N',
     'Internal_VND_SHINHAN-2.xlsx',
     'lamine@gmail.com', '20260302073137', '20260302073137'),

    ('VN_VNM0004_000001', '363', '03', '970424', '700022964690',
     'VND', 'VND',
     'Internal_VND_SHINHAN-1', 10000.000000, 1,
     '1538', 'N', 'N',
     'Internal_VND_SHINHAN-1.xlsx',
     'lamine@gmail.com', '20260302072916', '20260302072916')
ON CONFLICT (BIZ_KEY, SCDUL_PYMN_NO) DO NOTHING;


-- ---------------------------------------------------------------------------
-- SCDUL_PYMN_SUMR_DETL — one line per master row
-- ---------------------------------------------------------------------------
INSERT INTO SCDUL_PYMN_SUMR_DETL
    (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO,
     TRAN_AMT, TRNF_CCY_CD,
     ANT_DIV_CD, RCPT_NM, TAR_BANK_CD, FINC_MDA_NO,
     TRAN_USAG, DOC_NO, NOTE, CHART_ACCT_CD,
     REG_ID, REG_DTM, MOD_DTM)
VALUES
    ('VN_VNM0004_000001', '375', '493',
     100000.000000, 'VND',
     'NAPAS', 'CONG TY TNHH WEBCASH VIET NAM', '970424', '700005285299',
     'Internal payment 1', '1C24TPA-25861', 'VEHICLE FEE', '2001001',
     'lamine@gmail.com', '20260511075524', '20260511075524'),

    ('VN_VNM0004_000001', '374', '492',
     100000.000000, 'VND',
     'NAPAS', 'CONG TY TNHH WEBCASH VIET NAM', '970424', '700005285299',
     'Internal payment 1', '1C24TPA-25861', 'VEHICLE FEE', '2001001',
     'lamine@gmail.com', '20260511071313', '20260511071313'),

    ('VN_VNM0004_000001', '370', '488',
     100000.000000, 'VND',
     'NAPAS', 'CONG TY TNHH WEBCASH VIET NAM', '970424', '700005285280',
     'Internal payment 1', '1C24TPA-25861', 'VEHICLE FEE', '2001001',
     'lamine@gmail.com', '20260325073215', '20260325073215'),

    ('VN_VNM0004_000001', '369', '487',
     10.000000, 'USD',
     'NAPAS', 'WEBCASH VIETNAM COMPANY LIMITED', '970457', '100912150373',
     'Internal payment 1', '', 'VEHICLE FEE', '',
     'lamine@gmail.com', '20260302075653', '20260302075653'),

    ('VN_VNM0004_000001', '368', '486',
     10000.000000, 'VND',
     'NAPAS', 'WEBCASH VIETNAM COMPANY LIMITED', '970457', '100912150373',
     'Internal payment 1', '', 'VEHICLE FEE', '',
     'lamine@gmail.com', '20260302075459', '20260302075459'),

    ('VN_VNM0004_000001', '367', '485',
     10.000000, 'USD',
     'NAPAS', 'E-Time Deposit (VND)', '970424', '703011613756',
     'Internal payment 1', '', 'VEHICLE FEE', '',
     'lamine@gmail.com', '20260302074643', '20260302074643'),

    ('VN_VNM0004_000001', '366', '484',
     10000.000000, 'VND',
     'NAPAS', 'E-Time Deposit (VND)', '970424', '703011613756',
     'Internal payment 1', '', 'VEHICLE FEE', '',
     'lamine@gmail.com', '20260302074448', '20260302074448'),

    ('VN_VNM0004_000001', '365', '483',
     10.000000, 'USD',
     'NAPAS', 'CONG TY TNHH WEBCASH VIET NAM', '970424', '700005285299',
     'Internal payment 1', '1C24TPA-25861', 'VEHICLE FEE', '2001001',
     'lamine@gmail.com', '20260302073154', '20260302073154'),

    ('VN_VNM0004_000001', '364', '482',
     10.000000, 'USD',
     'NAPAS', 'CONG TY TNHH WEBCASH VIET NAM', '970424', '700005285299',
     'Internal payment 1', '1C24TPA-25861', 'VEHICLE FEE', '2001001',
     'lamine@gmail.com', '20260302073137', '20260302073137'),

    ('VN_VNM0004_000001', '363', '481',
     10000.000000, 'VND',
     'NAPAS', 'CONG TY TNHH WEBCASH VIET NAM', '970424', '700005285299',
     'SHINHAN VND TO USD AND INPUT VND', '',
     'SHINHAN VND -> USD (INPUT VND)', '',
     'lamine@gmail.com', '20260302072916', '20260302072916')
ON CONFLICT (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO) DO NOTHING;
