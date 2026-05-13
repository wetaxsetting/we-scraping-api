-- V86: Demo seed data for overseas-payment tables
-- Tenant: HAENGSUNG (biz_key = 'HS'), date range 2025-01-01 ~ 2025-01-31
-- Covers both SUMR (E) and MSTR (M) types so the UNION ALL list is exercised.

-- ── Approval chains ──────────────────────────────────────────────────────────
INSERT INTO EXPT_RESOL_MSTR
    (BIZ_KEY, APPR_SEQ_NO, TITL, APPR_TYPE_CD, APPR_DV, DOC_NO, PAY_DATE,
     DRAFT_USER_ID, DRAFT_USER_NM, APPR_STTS, REG_DTM, REG_ID)
VALUES
    ('HS', 'APPR-2025-0001', 'Jan remittance batch #1', 'OV', '1', 'DOC-0001', '20250110',
     'user01', 'Kim Minsu', 'APPROVED', '20250105120000', 'user01'),
    ('HS', 'APPR-2025-0002', 'Supplier USD wire', 'OV', '1', 'DOC-0002', '20250115',
     'user01', 'Kim Minsu', 'PENDING', '20250112090000', 'user01'),
    ('HS', 'APPR-2025-0003', 'Manual transfer to Hanoi branch', 'OV', '1', 'DOC-0003', '20250120',
     'user02', 'Lee Jisoo', 'IN_PROGRESS', '20250118140000', 'user02'),
    ('HS', 'APPR-2025-0004', 'Payroll remittance Jan', 'OV', '1', 'DOC-0004', '20250125',
     'user02', 'Lee Jisoo', 'DRAFT', '20250122080000', 'user02');

-- ── Approver steps ───────────────────────────────────────────────────────────
INSERT INTO EXPT_RESOL_APPRVR_INFM
    (BIZ_KEY, APPR_SEQ_NO, APPRVR_LINE_NO, APPRVR_ID, APPRVR_NM, APPRVR_STTS, MOD_DTM)
VALUES
    ('HS', 'APPR-2025-0001', '1', 'mgr01', 'Park Sungho',   'A', '20250106100000'),
    ('HS', 'APPR-2025-0001', '2', 'dir01', 'Choi Yongbae',  'A', '20250107153000'),
    ('HS', 'APPR-2025-0002', '1', 'mgr01', 'Park Sungho',   'P', NULL),
    ('HS', 'APPR-2025-0003', '1', 'mgr01', 'Park Sungho',   'A', '20250119090000'),
    ('HS', 'APPR-2025-0003', '2', 'dir01', 'Choi Yongbae',  'P', NULL),
    ('HS', 'APPR-2025-0004', '1', 'mgr01', 'Park Sungho',   'P', NULL);

-- ── OVRS_PYMN_SUMR — batch / Excel upload rows ───────────────────────────────
INSERT INTO OVRS_PYMN_SUMR
    (BIZ_KEY, OVRS_PYMN_NO, OVRS_PYMN_TYPE_CD, TITL, CCY_CD,
     TOTAL_TRAN_AMT, TOT_TRNS_CNT, APPR_SEQ_NO,
     OUT_BANK_CD, WDRL_ACT_NO, OUT_BANK_NM,
     TRNF_YN, ORG_FILE_NM, FILE_URL,
     REG_DTM, REG_ID)
VALUES
    ('HS', 'OV-E-2025-0001', 'EX', 'Overseas_remittance_20250105.xlsx', 'USD',
     50000.00, 3, 'APPR-2025-0001',
     '970403', '00500044787001', 'Sacombank',
     'Y', 'Overseas_remittance_20250105.xlsx', '/files/HS/OV-E-2025-0001.xlsx',
     '20250105120000', 'user01'),
    ('HS', 'OV-E-2025-0002', 'EX', 'Supplier_wire_batch_20250112.xlsx', 'USD',
     120000.00, 5, 'APPR-2025-0002',
     '970403', '00500044787001', 'Sacombank',
     'N', 'Supplier_wire_batch_20250112.xlsx', '/files/HS/OV-E-2025-0002.xlsx',
     '20250112090000', 'user01');

-- ── OVRS_PYMN_MSTR — individual / manual rows ────────────────────────────────
INSERT INTO OVRS_PYMN_MSTR
    (BIZ_KEY, OVRS_PYMN_NO,
     DBT_ACCT_NO, DBT_ACCT_CD, DBT_CCY_CD,
     TRNF_CCY_CD, TRNF_AMT, TOTAL_TRAN_AMT, TOT_TRNS_CNT,
     FEE_PAYER_CD,
     BNFC_NM, BNFC_BANK_CD, BNFC_ACCT_NO, BNFC_BANK_NAME,
     BNFC_BANK_BIC, MEMO, APPR_SEQ_NO, TRNF_YN,
     REG_DTM, REG_ID)
VALUES
    ('HS', 'OV-M-2025-0001',
     '00500044787001', 'SAC-001', 'USD',
     'USD', 8000.00, 8000.00, 1,
     'S',
     'Hanoi Branch Treasury', '970436', 'VCB-HN-001234', 'Vietcombank Hanoi',
     'BFTVVNVX', 'Jan office expense wire', 'APPR-2025-0003', 'N',
     '20250118140000', 'user02'),
    ('HS', 'OV-M-2025-0002',
     '00500044787001', 'SAC-001', 'USD',
     'USD', 15000.00, 15000.00, 1,
     'O',
     'Global Supplier Co Ltd', '970415', 'TCB-SUPP-9988', 'Techcombank',
     'VTCBVNVX', 'Jan payroll transfer', 'APPR-2025-0004', 'N',
     '20250122080000', 'user02');
