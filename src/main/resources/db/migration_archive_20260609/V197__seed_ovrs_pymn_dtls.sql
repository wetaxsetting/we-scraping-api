-- V182: Seed OVRS_PYMN_DTLS — individual transaction rows for seeded overseas Excel batches.
-- Covers OV-E-2026-0001 (3 rows, USD 50 000) and OV-E-2026-0002 (5 rows, USD 120 000).
-- Row counts / amounts match TOT_TRNS_CNT / TOTAL_TRAN_AMT seeded in V157.
-- Beneficiary data is representative R&D data; TRNF_YN='Y' for the approved batch (0001),
-- 'N' for the pending batch (0002).
-- Idempotent via INSERT … ON CONFLICT DO NOTHING.

-- ── OV-E-2026-0001 — approved batch (APPR_SEQ_NO='1536', TRNF_YN='Y') ──────────
INSERT INTO OVRS_PYMN_DTLS
    (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ,
     BNFC_CODE, CCY_CD, TRNF_AMT, FEE_PAYER_CD, MEMO,
     BNFC_NM, BNFC_BANK_NM, BIC_CD, BNFC_ACCT_NO, BNFC_ADDR,
     TRNF_YN, REG_DTM, REG_ID)
VALUES
    ('VN_VNM0004_000001', 'OV-E-2026-0001', 1,
     'TRCO-SHB-001', 'USD', 20000.00, 'S', 'Q1 supplier invoice — raw materials',
     'ABC Trading Co Ltd', 'Shinhan Bank America', 'SHBKUS33',
     '100912150384', '123 Commerce Ave, New York, NY 10001, USA',
     'Y', '20260508120000', 'lamine@gmail.com'),

    ('VN_VNM0004_000001', 'OV-E-2026-0001', 2,
     'TRCO-SGB-002', 'USD', 15000.00, 'S', 'Consulting fee Feb 2026',
     'Global Consulting Partners', 'Standard Chartered Bank', 'SCBLSGSG',
     'SGP-CC-7712340', '80 Raffles Place, Singapore 048624',
     'Y', '20260508120000', 'lamine@gmail.com'),

    ('VN_VNM0004_000001', 'OV-E-2026-0001', 3,
     'TRCO-HSBC-003', 'USD', 15000.00, 'O', 'Annual license renewal FY2026',
     'Tech Solutions Korea Inc', 'HSBC Korea', 'HKBKKRSE',
     'KR-HSBC-4482916', '100 Eulji-ro, Jung-gu, Seoul 04548, Korea',
     'Y', '20260508120000', 'lamine@gmail.com')
ON CONFLICT (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ) DO NOTHING;

-- ── OV-E-2026-0002 — pending batch (no APPR_SEQ_NO, TRNF_YN='N') ────────────────
INSERT INTO OVRS_PYMN_DTLS
    (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ,
     BNFC_CODE, CCY_CD, TRNF_AMT, FEE_PAYER_CD, MEMO,
     BNFC_NM, BNFC_BANK_NM, BIC_CD, BNFC_ACCT_NO, BNFC_ADDR,
     TRNF_YN, REG_DTM, REG_ID)
VALUES
    ('VN_VNM0004_000001', 'OV-E-2026-0002', 1,
     'TRCO-SHB-001', 'USD', 30000.00, 'S', 'Q2 raw material purchase',
     'ABC Trading Co Ltd', 'Shinhan Bank America', 'SHBKUS33',
     '100912150384', '123 Commerce Ave, New York, NY 10001, USA',
     'N', '20260512090000', 'lamine@gmail.com'),

    ('VN_VNM0004_000001', 'OV-E-2026-0002', 2,
     'TRCO-UOB-004', 'USD', 25000.00, 'O', 'IT infrastructure upgrade',
     'DataCenter Asia Pte Ltd', 'United Overseas Bank', 'UOVBSGSG',
     'SG-UOB-9900123', '1 Harbourfront Ave, #14-01, Singapore 098632',
     'N', '20260512090000', 'lamine@gmail.com'),

    ('VN_VNM0004_000001', 'OV-E-2026-0002', 3,
     'TRCO-BNY-005', 'USD', 20000.00, 'S', 'Marketing agency retainer May 2026',
     'Creative Works LLC', 'BNY Mellon', 'IRVTUS3N',
     'US-BNY-3301298', '240 Greenwich St, New York, NY 10286, USA',
     'N', '20260512090000', 'lamine@gmail.com'),

    ('VN_VNM0004_000001', 'OV-E-2026-0002', 4,
     'TRCO-ANZ-006', 'USD', 25000.00, 'B', 'Machinery spare parts — invoice #AUS-2026-88',
     'Pacific Equipment Co', 'ANZ Bank Australia', 'ANZBAU3M',
     'AU-ANZ-6677001', '833 Collins St, Melbourne VIC 3000, Australia',
     'N', '20260512090000', 'lamine@gmail.com'),

    ('VN_VNM0004_000001', 'OV-E-2026-0002', 5,
     'TRCO-DBS-007', 'USD', 20000.00, 'S', 'Annual training programme — batch 2',
     'LearnHub International', 'DBS Bank', 'DBSSSGSG',
     'SG-DBS-1122334', '12 Marina Boulevard, Singapore 018982',
     'N', '20260512090000', 'lamine@gmail.com')
ON CONFLICT (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ) DO NOTHING;
