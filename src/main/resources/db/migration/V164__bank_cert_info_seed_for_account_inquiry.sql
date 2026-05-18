-- ============================================================================
-- V164__bank_cert_info_seed_for_account_inquiry.sql
--
-- Account Inquiry disconnected-bank flag (2026-05-18).
--
-- Seeds BANK_CERT_INFO with the 9 credential rows the V22 ACCT_MSTR import
-- references via BANK_CERT_SEQ. Without this seed every account would resolve
-- as "disconnected" on the local dashboard because BANK_CERT_INFO is empty,
-- and every checkbox on Banking > Account Inquiry would render disabled.
--
-- The disconnected-bank check on the frontend (legacy
-- `fnsh_0005_001.ts:711` parity, refactor restoration) treats a row as
-- connected when ACCT_MSTR.BANK_CERT_SEQ resolves to a BANK_CERT_INFO row
-- whose CERT_INFO_CON AND CERT_INFO_DV are both non-blank. After this seed:
--
--   * Connected banks    → Vietin, IBK, BIDV, ACB, Kookmin, KEBHana, Woori,
--                          Shinhan, Agribank (the 9 seq numbers below).
--   * Disconnected banks → Vietcom (seq 17, missing here), MSB (seq 3,
--                          missing), HD Bank (seq 9, missing — matches the
--                          UI screenshot's "Test data" row), plus any
--                          ACCT_MSTR row with NULL BANK_CERT_SEQ.
--
-- Cert values copied verbatim from JEX DEV (`wecmsvndev.bank_cert_info`)
-- so the local dashboard mirrors the intended live system shape. Per the
-- Data Migration Rule: tenant scoped to VN_VNM0004_000001, no other
-- tenants touched, idempotent.
-- ============================================================================

INSERT INTO BANK_CERT_INFO (
    BIZ_KEY, SEQ_NO, BANK_CD,
    CERT_INFO_DV, CERT_INFO_CON,
    NXISAS_GB_SEQ_NO,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM
) VALUES
    ('VN_VNM0004_000001',  5, '11704008', '1', '22057592LY1C',     0, 'lamine@gmail.com', '20250313040709', 'lamine@gmail.com', '20250321080025'),
    ('VN_VNM0004_000001', 13, '11704003', '1', 'wcgw0801',         0, 'lamine@gmail.com', '20250321071139', 'lamine@gmail.com', '20250321071139'),
    ('VN_VNM0004_000001', 15, '11704080', '1', 'WEBCASH01',        0, 'lamine@gmail.com', '20250407091322', 'lamine@gmail.com', '20250407091322'),
    ('VN_VNM0004_000001', 18, '11704011', '1', '3668808',          0, 'lamine@gmail.com', '20250411045321', 'lamine@gmail.com', '20250411045321'),
    ('VN_VNM0004_000001', 19, '11704013', '1', 'WEBCASH9',         0, 'lamine@gmail.com', '20250425084224', 'lamine@gmail.com', '20250425084224'),
    ('VN_VNM0004_000001', 21, '11704100', '1', '6280CTWEBCASHVN2', 0, 'lamine@gmail.com', '20250508070846', 'lamine@gmail.com', '20250508070846'),
    ('VN_VNM0004_000001', 23, '11704001', '1', 'genuwin.tao',      0, 'lamine@gmail.com', '20251215122506', 'lamine@gmail.com', '20251215122506'),
    ('VN_VNM0004_000001', 24, '11704020', '1', 'GENO728',          0, 'lamine@gmail.com', '20251216025343', 'lamine@gmail.com', '20260408031824'),
    ('VN_VNM0004_000001', 25, '11704088', '1', 'WEBCASH01',        0, 'lamine@gmail.com', '20251216032812', 'lamine@gmail.com', '20260325034145')
ON CONFLICT (BIZ_KEY, SEQ_NO) DO NOTHING;
