-- Demo seed data for overseas-payment tables.
-- Tenant: VN_VNM0004_000001 (primary R&D tenant), dates in 2026.
-- Covers both SUMR (E = Excel batch) and MSTR (M = manual) types so the
-- UNION ALL list query is exercised.
--
-- EXPT_RESOL_MSTR / EXPT_RESOL_APPRVR_INFM rows that back the Overseas Payment
-- approval chains are seeded by V134 (approval_box_demo_seed) which uses correct
-- schema. APPR_SEQ_NO '1536' in that seed is an Overseas Payment (APPR_DV='34')
-- row linked to lamine@gmail.com.
--
-- Idempotent via INSERT … ON CONFLICT DO NOTHING.

-- ── OVRS_PYMN_SUMR — batch / Excel upload rows ───────────────────────────────
INSERT INTO OVRS_PYMN_SUMR
    (BIZ_KEY, OVRS_PYMN_NO, OVRS_PYMN_TYPE_CD, TITL, CCY_CD,
     TOTAL_TRAN_AMT, TOT_TRNS_CNT, APPR_SEQ_NO,
     OUT_BANK_CD, WDRL_ACT_NO, OUT_BANK_NM,
     TRNF_YN, ORG_FILE_NM, FILE_URL,
     REG_DTM, REG_ID)
VALUES
    ('VN_VNM0004_000001', 'OV-E-2026-0001', 'EX', 'Overseas_remittance_20260508.xlsx', 'USD',
     50000.00, 3, '1536',
     '970403', '00500044787001', 'Sacombank',
     'Y', 'Overseas_remittance_20260508.xlsx', '/files/VN_VNM0004_000001/OV-E-2026-0001.xlsx',
     '20260508120000', 'lamine@gmail.com'),
    ('VN_VNM0004_000001', 'OV-E-2026-0002', 'EX', 'Supplier_wire_20260512.xlsx', 'USD',
     120000.00, 5, NULL,
     '970403', '00500044787001', 'Sacombank',
     'N', 'Supplier_wire_20260512.xlsx', '/files/VN_VNM0004_000001/OV-E-2026-0002.xlsx',
     '20260512090000', 'lamine@gmail.com')
ON CONFLICT (BIZ_KEY, OVRS_PYMN_NO) DO NOTHING;

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
    ('VN_VNM0004_000001', 'OV-M-2026-0001',
     '00500044787001', 'SAC-001', 'USD',
     'USD', 8000.00, 8000.00, 1,
     'S',
     'Hanoi Branch Treasury', '970436', 'VCB-HN-001234', 'Vietcombank Hanoi',
     'BFTVVNVX', 'Jan office expense wire', NULL, 'N',
     '20260510140000', 'lamine@gmail.com'),
    ('VN_VNM0004_000001', 'OV-M-2026-0002',
     '00500044787001', 'SAC-001', 'USD',
     'USD', 15000.00, 15000.00, 1,
     'O',
     'Global Supplier Co Ltd', '970415', 'TCB-SUPP-9988', 'Techcombank',
     'VTCBVNVX', 'Jan payroll transfer', NULL, 'N',
     '20260513080000', 'lamine@gmail.com')
ON CONFLICT (BIZ_KEY, OVRS_PYMN_NO) DO NOTHING;
