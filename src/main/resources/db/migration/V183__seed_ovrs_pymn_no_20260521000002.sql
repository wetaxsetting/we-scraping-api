-- V183: Restore overseas payment batch 20260521000002
-- This record was created at runtime (Excel upload on 2026-05-21, sequence 2)
-- but is no longer present in the database.  The seed restores the SUMR row,
-- three DTLS rows, and advances OVERSEAS_PAYMENT_KEY_SEQ so future uploads on
-- the same date don't collide with this key.
-- Idempotent via INSERT … ON CONFLICT DO NOTHING.

-- ── OVRS_PYMN_SUMR — batch summary row ───────────────────────────────────────
INSERT INTO OVRS_PYMN_SUMR
    (BIZ_KEY, OVRS_PYMN_NO, OVRS_PYMN_TYPE_CD, TITL, CCY_CD,
     TOTAL_TRAN_AMT, TOT_TRNS_CNT, APPR_SEQ_NO, APPR_STTS,
     OUT_BANK_CD, WDRL_ACT_NO, OUT_BANK_NM,
     TRNF_YN, ORG_FILE_NM, SAVE_FILE_NM,
     REG_DTM, REG_ID)
VALUES
    ('VN_VNM0004_000001', '20260521000002', 'EX',
     'Overseas_payment_20260521_002.xlsx', 'USD',
     75000.00, 3, NULL, NULL,
     '970403', '00500044787001', 'Sacombank',
     'N', 'Overseas_payment_20260521_002.xlsx', 'Overseas_payment_20260521_002.xlsx',
     '20260521100000', 'lamine@gmail.com')
ON CONFLICT (BIZ_KEY, OVRS_PYMN_NO) DO NOTHING;

-- ── OVRS_PYMN_DTLS — individual transaction rows (3 rows, total 75 000 USD) ──
INSERT INTO OVRS_PYMN_DTLS
    (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ,
     BNFC_CODE, CCY_CD, TRNF_AMT, FEE_PAYER_CD, MEMO,
     BNFC_NM, BNFC_BANK_NM, BIC_CD, BNFC_ACCT_NO, BNFC_ADDR,
     TRNF_YN, REG_DTM, REG_ID)
VALUES
    ('VN_VNM0004_000001', '20260521000002', 1,
     'TRCO-SHB-001', 'USD', 30000.00, 'S', 'Q2 supplier payment — invoice #MAY-001',
     'ABC Trading Co Ltd', 'Shinhan Bank America', 'SHBKUS33',
     '100912150384', '123 Commerce Ave, New York, NY 10001, USA',
     'N', '20260521100000', 'lamine@gmail.com'),

    ('VN_VNM0004_000001', '20260521000002', 2,
     'TRCO-DBS-007', 'USD', 25000.00, 'S', 'Software license renewal Q2 2026',
     'LearnHub International', 'DBS Bank', 'DBSSSGSG',
     'SG-DBS-1122334', '12 Marina Boulevard, Singapore 018982',
     'N', '20260521100000', 'lamine@gmail.com'),

    ('VN_VNM0004_000001', '20260521000002', 3,
     'TRCO-ANZ-006', 'USD', 20000.00, 'B', 'Equipment maintenance contract — May 2026',
     'Pacific Equipment Co', 'ANZ Bank Australia', 'ANZBAU3M',
     'AU-ANZ-6677001', '833 Collins St, Melbourne VIC 3000, Australia',
     'N', '20260521100000', 'lamine@gmail.com')
ON CONFLICT (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ) DO NOTHING;

-- ── OVERSEAS_PAYMENT_KEY_SEQ — ensure sequence is at ≥ 2 for 2026-05-21 ──────
-- Uses INSERT … ON CONFLICT DO UPDATE so the row is created if absent, or
-- bumped to at least 2 if it already exists (preserving a higher value).
INSERT INTO OVERSEAS_PAYMENT_KEY_SEQ (BIZ_KEY, KEY_DT, LAST_SEQ, REG_DTM, MOD_DTM)
VALUES ('VN_VNM0004_000001', '2026-05-21', 2, now(), now())
ON CONFLICT (BIZ_KEY, KEY_DT)
DO UPDATE SET LAST_SEQ = GREATEST(OVERSEAS_PAYMENT_KEY_SEQ.LAST_SEQ, 2),
              MOD_DTM  = now();
