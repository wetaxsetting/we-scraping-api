-- ============================================================================
-- V17__sprint1_seed_data.sql
--
-- Sprint 1 seed data — pinned to BIZ_KEY = 'VN_VNM0004_000001' (the only
-- tenant Sprint 0 supports per wecms.tenant.biz-key). All inserts are
-- idempotent via ON CONFLICT DO NOTHING — V17 may be re-run safely and
-- never overwrites edits a developer made in their local DB.
--
-- Seed shape mirrors the static-UI mock rows in wecms-web so the
-- Next.js pages render identical content against either source. Numeric
-- amounts are stored as raw values; the frontend formats them for
-- display.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Width fix-up — legacy EINV_INVOICESYMBOL is VARCHAR(10) but the static UI
-- mocks combine the symbol + serial ("1C24TLM-0405", 12 chars). Widen here
-- so seed inserts succeed in both starting states (DB just bootstrapped from
-- V15, or DB built freshly from V1–V17). ALTER is idempotent —
-- repeated runs of the same TYPE clause are no-ops in PostgreSQL.
-- ----------------------------------------------------------------------------
ALTER TABLE BUSA_DOC_MSTR
    ALTER COLUMN EINV_INVOICESYMBOL TYPE VARCHAR(50);

-- ----------------------------------------------------------------------------
-- 1. ACCT_MSTR — VND + FCY accounts (Account Inquiry mocks).
-- ----------------------------------------------------------------------------
INSERT INTO ACCT_MSTR (BIZ_KEY, BANK_CD, ACCT_NO, ACCT_TYPE_CD, CCY_CD, ACCT_NICK_NM, ACCT_NM, BAL, REAL_AMT, ACCT_BAL_INQ_STTS, ACCT_BAL_UPD_DTM, ACTV_YN, REG_ID, REG_DTM)
VALUES
    ('VN_VNM0004_000001', 'BIDV',     '0001-00000569-14', '11', 'VND', 'VIETCOM 8210 - payment account',  'BIDV Current',         20000000, 20000000, 'OK',  '20251125094000', 'Y', 'system', '20251125094000'),
    ('VN_VNM0004_000001', 'WOORI',    '0001-00000523-06', '13', 'VND', 'VIETCOM 8210 - payment account',  'WOORI Fixed',          20000000, 20000000, 'ERR', '20251125094000', 'Y', 'system', '20251125094000'),
    ('VN_VNM0004_000001', 'HDBANK',   '0001-00000569-14', '12', 'VND', 'VIETCOM 8210 - payment account',  'HD BANK Savings',      20000000, 20000000, 'OK',  '20251125094000', 'Y', 'system', '20251125094000'),
    ('VN_VNM0004_000001', 'HDBANK',   '0001-00000569-15', '11', 'VND', 'VIETCOM 8210 - payment account',  'HD BANK Current',      20000000, 20000000, 'ERR', '20251125094000', 'Y', 'system', '20251125094000'),
    ('VN_VNM0004_000001', 'WOORI',    '00500044787814',   '11', 'USD', 'WOORI 7814 - USD operating',      'WOORI USD Current',     7500000,  7500000, 'OK',  '20251125094000', 'Y', 'system', '20251125094000'),
    ('VN_VNM0004_000001', 'BIDV',     '00500088442913',   '11', 'USD', 'BIDV 2913 - USD payable',         'BIDV USD Current',     12500000, 12500000, 'OK',  '20251125094000', 'Y', 'system', '20251125094000'),
    ('VN_VNM0004_000001', 'SHINHAN',  '00500011226946',   '11', 'USD', 'SHINHAN 6946 - USD payroll',      'SHINHAN USD Current',   3500000,  3500000, 'OK',  '20251125094000', 'Y', 'system', '20251125094000')
ON CONFLICT (BIZ_KEY, BANK_CD, ACCT_NO) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. ACCT_TRNS_HSTR — sample transactions for BIDV / 0001-00000569-14.
-- ----------------------------------------------------------------------------
INSERT INTO ACCT_TRNS_HSTR (BIZ_KEY, ACCT_SEQ_NO, BANK_CD, ACCT_NO, TRSC_DT, TRSC_TM, CCY_CD, INOUT_DV, TRSC_AMT, BAL, RMRK, LINK_MATCH_YN, REG_ID, REG_DTM)
VALUES
    ('VN_VNM0004_000001', 'TX0000000001', 'BIDV', '0001-00000569-14', '20251205', '101530', 'VND', 'I',   5000000, 25000000, 'PAYMENT FROM CUSTOMER ABC',  'Y', 'system', '20251205101530'),
    ('VN_VNM0004_000001', 'TX0000000002', 'BIDV', '0001-00000569-14', '20251204', '142115', 'VND', 'O',   1500000, 23500000, 'OFFICE SUPPLIES',            'N', 'system', '20251204142115'),
    ('VN_VNM0004_000001', 'TX0000000003', 'BIDV', '0001-00000569-14', '20251204', '093045', 'VND', 'I',  10000000, 33500000, 'INVOICE 1C24TLM-0405',       'Y', 'system', '20251204093045'),
    ('VN_VNM0004_000001', 'TX0000000004', 'BIDV', '0001-00000569-14', '20251203', '160220', 'VND', 'O',   3200000, 30300000, 'UTILITY BILL',               'N', 'system', '20251203160220'),
    ('VN_VNM0004_000001', 'TX0000000005', 'BIDV', '0001-00000569-14', '20251203', '111450', 'VND', 'I',   8500000, 38800000, 'PAYMENT FROM CUSTOMER XYZ',  'Y', 'system', '20251203111450')
ON CONFLICT (BIZ_KEY, ACCT_SEQ_NO) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3. ACCT_TRNS_RCV_PAY_INFM — link rows that flip Transaction History's
--    "confirmed" badge on for the seeded TX0000000001 / 0003 / 0005.
-- ----------------------------------------------------------------------------
INSERT INTO ACCT_TRNS_RCV_PAY_INFM (BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO, DOC_ID, USAG_CD, VAS_ACCT_CD, TRCO_KEY, TRCO_CMNM, AMT, CONF_YN, CONF_USER_ID, CONF_DTM, REG_ID, REG_DTM)
VALUES
    ('VN_VNM0004_000001', 'TX0000000001', 'RP000001', 'EINV-S-0001', 'SALES',   '1311', 'TRC0000001', 'CUSTOMER ABC LTD', 5000000,  'Y', 'testuser', '20251205103000', 'system', '20251205103000'),
    ('VN_VNM0004_000001', 'TX0000000003', 'RP000002', 'EINV-S-0003', 'SALES',   '1311', 'TRC0000002', 'INVOICE 0405',     10000000, 'Y', 'testuser', '20251204094500', 'system', '20251204094500'),
    ('VN_VNM0004_000001', 'TX0000000005', 'RP000003', 'EINV-S-0005', 'SALES',   '1311', 'TRC0000003', 'CUSTOMER XYZ',     8500000,  'Y', 'testuser', '20251203112000', 'system', '20251203112000')
ON CONFLICT (BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4. NAPAS_REMIT_TRNS_HSTR — sample inter-bank transfer history.
-- ----------------------------------------------------------------------------
INSERT INTO NAPAS_REMIT_TRNS_HSTR (BIZ_KEY, SEQ_NO, OUT_BANK_CODE, OUT_ACCT_NO, TRANS_TYPE, IN_BANK_CODE, IN_ACCT_NO, OWAC_NM, CCY_CD, TRSC_AMT, FEE, RMRK, TRNS_STTS, TRNS_MSG, REG_ID, REG_DTM)
VALUES
    ('VN_VNM0004_000001', 1, 'BIDV',    '0001-00000569-14', 'O', 'WOORI',   '0001-00000523-06', 'WOORI VIETNAM',   'VND', 5000000,  10000, 'INTERNAL TRANSFER',   '00000000', 'SUCCESS',           'testuser', '20251205103000'),
    ('VN_VNM0004_000001', 2, 'WOORI',   '0001-00000523-06', 'O', 'BIDV',    '0001-00000569-14', 'BIDV VIETNAM',    'VND', 2500000,  10000, 'PAYBACK',             '99999999', 'FAILED - NSF',      'testuser', '20251204140000'),
    ('VN_VNM0004_000001', 3, 'BIDV',    '0001-00000569-14', 'O', 'HDBANK',  '0001-00000569-14', 'HD BANK',         'VND', 7500000,  15000, 'OFFICE RENT',         '00000000', 'SUCCESS',           'testuser', '20251203093000'),
    ('VN_VNM0004_000001', 4, 'WOORI',   '00500044787814',   'O', 'BIDV',    '00500088442913',   'BIDV USD',        'USD',   12500,     50, 'USD INTERNAL',        '00000000', 'SUCCESS',           'testuser', '20251202160000'),
    ('VN_VNM0004_000001', 5, 'SHINHAN', '00500011226946',   'O', 'WOORI',   '00500044787814',   'WOORI USD',       'USD',    8500,     50, 'PAYROLL CONSOLIDATE', '00000000', 'SUCCESS',           'testuser', '20251201101500')
ON CONFLICT (BIZ_KEY, SEQ_NO) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5. BUSI_COND_DAILY_SUMR + DETL — Daily Cash / Daily Financial seed.
-- ----------------------------------------------------------------------------
INSERT INTO BUSI_COND_DAILY_SUMR (BIZ_KEY, TRCO_KEY, TRCO_CMNM, TRCO_CMENM, TRCO_TAX_ID, TRSC_DT, CCY_CD, SELL_AMT, BUY_AMT, RCV_AMT, SND_AMT, PRJT_NO)
VALUES
    ('VN_VNM0004_000001', 'TRC0000001', 'CUSTOMER ABC LTD',  'CUSTOMER ABC LTD',  '0102345678', '20251125', 'VND', 50000000, 30000000, 25000000, 18000000, ''),
    ('VN_VNM0004_000001', 'TRC0000002', 'CUSTOMER XYZ',      'CUSTOMER XYZ',      '0103456789', '20251125', 'VND', 12500000,        0,  8500000,        0, ''),
    ('VN_VNM0004_000001', 'TRC0000003', 'SUPPLIER DELTA',    'SUPPLIER DELTA',    '0104567890', '20251125', 'USD',        0,    12500,        0,    12500, ''),
    ('VN_VNM0004_000001', 'TRC0000004', 'EXCHANGE PARTNER',  'EXCHANGE PARTNER',  '0105678901', '20251125', 'KRW',        0,        0,    50000,        0, '')
ON CONFLICT (BIZ_KEY, TRCO_KEY, TRSC_DT, CCY_CD, PRJT_NO) DO NOTHING;

INSERT INTO BUSI_COND_DAILY_SUMR_DETL (BIZ_KEY, TRCO_KEY, TRSC_DT, CCY_CD, PRJT_NO, DTL_SEQ_NO, TRSC_TYPE, TRCO_CMNM, TRCO_CMENM, TRCO_TAX_ID, TRSC_AMT, DOC_TYPE, REF_NO)
VALUES
    ('VN_VNM0004_000001', 'TRC0000001', '20251125', 'VND', '', 1, 'S', 'CUSTOMER ABC LTD', 'CUSTOMER ABC LTD', '0102345678', 30000000, 'I', 'EINV-S-0001'),
    ('VN_VNM0004_000001', 'TRC0000001', '20251125', 'VND', '', 2, 'S', 'CUSTOMER ABC LTD', 'CUSTOMER ABC LTD', '0102345678', 20000000, 'I', 'EINV-S-0002'),
    ('VN_VNM0004_000001', 'TRC0000003', '20251125', 'USD', '', 1, 'P', 'SUPPLIER DELTA',   'SUPPLIER DELTA',   '0104567890',    12500, 'O', 'EINV-E-0010')
ON CONFLICT (BIZ_KEY, TRCO_KEY, TRSC_DT, CCY_CD, PRJT_NO, DTL_SEQ_NO) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 6. BUSA_DOC_MSTR — sales + expense e-invoices (Add-on > e-Invoices pages).
-- ----------------------------------------------------------------------------
INSERT INTO BUSA_DOC_MSTR (BIZ_KEY, DOC_ID, DOC_TYPE, INV_TYPE, ISSU_DT, ISSU_TAX_ID, TRCO_KEY, TRCO_TAX_ID, TRCO_CMNM, TRCO_CMENM, TRCO_TRSC_CCY_CD, TOTL_AMT, SPLY_AMT_SUM, TAX_AMT_SUM, EINV_INVOICESTATUS, EINV_INVOICESYMBOL, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('VN_VNM0004_000001', 'EINV-S-0001', 'S', '1', '20251205', '0100100000', 'TRC0000001', '0102345678', 'CUSTOMER ABC LTD',  'CUSTOMER ABC LTD',  'VND', 5000000,  4500000, 500000, 'Original',    '1C24TLM-0405', 'system', '20251205113950', 'system', '20251205113950'),
    ('VN_VNM0004_000001', 'EINV-S-0002', 'S', '1', '20251204', '0100100000', 'TRC0000001', '0102345678', 'CUSTOMER ABC LTD',  'CUSTOMER ABC LTD',  'VND', 8500000,  7727272, 772728, 'Replacement', '1C24TLM-0406', 'system', '20251204093045', 'system', '20251204093045'),
    ('VN_VNM0004_000001', 'EINV-S-0003', 'S', '1', '20251203', '0100100000', 'TRC0000002', '0103456789', 'CUSTOMER XYZ',      'CUSTOMER XYZ',      'VND',12000000, 10909090,1090910, 'Original',    '1C24TLM-0407', 'system', '20251203111450', 'system', '20251203111450'),
    ('VN_VNM0004_000001', 'EINV-S-0004', 'S', '1', '20251202', '0100100000', 'TRC0000003', '0104567890', 'SUPPLIER DELTA',    'SUPPLIER DELTA',    'USD',     500,      455,     45, 'Original',    '1C24TLM-0408', 'system', '20251202160000', 'system', '20251202160000'),
    ('VN_VNM0004_000001', 'EINV-S-0005', 'S', '1', '20251201', '0100100000', 'TRC0000004', '0105678901', 'EXCHANGE PARTNER',  'EXCHANGE PARTNER',  'KRW',  150000,   136360,  13640, 'Cancelled',   '1C24TLM-0409', 'system', '20251201101500', 'system', '20251201101500'),
    ('VN_VNM0004_000001', 'EINV-E-0010', 'E', '1', '20251205', '0100100000', 'TRC0000003', '0104567890', 'SUPPLIER DELTA',    'SUPPLIER DELTA',    'USD',    1500,     1364,    136, 'Original',    'EXP-2024-0001','system', '20251205141500', 'system', '20251205141500'),
    ('VN_VNM0004_000001', 'EINV-E-0011', 'E', '1', '20251204', '0100100000', 'TRC0000003', '0104567890', 'SUPPLIER DELTA',    'SUPPLIER DELTA',    'VND', 3200000,  2909090, 290910, 'Original',    'EXP-2024-0002','system', '20251204160220', 'system', '20251204160220'),
    ('VN_VNM0004_000001', 'EINV-E-0012', 'E', '1', '20251202', '0100100000', 'TRC0000005', '0106789012', 'OFFICE LANDLORD',   'OFFICE LANDLORD',   'VND',12000000, 10909090,1090910, 'Original',    'EXP-2024-0003','system', '20251202090000', 'system', '20251202090000')
ON CONFLICT (BIZ_KEY, DOC_ID) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 7. OPRT_STTS_INFO — Add-on > Operation Status seed.
-- ----------------------------------------------------------------------------
INSERT INTO OPRT_STTS_INFO (BIZ_KEY, TAXCODE, COMPANYNAME, COMPANYSTATUS, OFFICEADD, SEARCHDATETIME, REPRESENTATIVEFIRSTNAME, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('VN_VNM0004_000001', '0102345678', 'CUSTOMER ABC LTD',  'NNT đang hoạt động (đã được cấp GCN ĐKT)',  '12 Le Loi, Ben Thanh, District 1, HCMC',  '20251125094000', 'NGUYEN VAN A',   'system', '20251125094000', 'system', '20251125094000'),
    ('VN_VNM0004_000001', '0103456789', 'CUSTOMER XYZ',      'NNT tạm ngừng hoạt động kinh doanh',         '88 Hai Ba Trung, Ben Nghe, District 1, HCMC', '20251124104500', 'TRAN THI B',     'system', '20251124104500', 'system', '20251124104500'),
    ('VN_VNM0004_000001', '0104567890', 'SUPPLIER DELTA',    'NNT đang hoạt động (đã được cấp GCN ĐKT)',  '5 Nguyen Hue, Ben Nghe, District 1, HCMC',    '20251123091200', 'LE VAN C',       'system', '20251123091200', 'system', '20251123091200'),
    ('VN_VNM0004_000001', '0105678901', 'EXCHANGE PARTNER',  'NNT đang hoạt động (đã được cấp GCN ĐKT)',  '23 Tran Hung Dao, Pham Ngu Lao, District 1',  '20251122152300', 'PHAM THI D',     'system', '20251122152300', 'system', '20251122152300'),
    ('VN_VNM0004_000001', '0106789012', 'OFFICE LANDLORD',   'NNT đang hoạt động (đã được cấp GCN ĐKT)',  '99 Pasteur, Ben Thanh, District 1, HCMC',     '20251121133900', 'HOANG VAN E',    'system', '20251121133900', 'system', '20251121133900')
ON CONFLICT (BIZ_KEY, TAXCODE) DO NOTHING;
