-- =============================================================================
-- V78 — BUSI_VAS_ACCT_INFM: real-DEV seed (≤30 rows for VN_VNM0004_000001)
-- =============================================================================
-- Idempotent demo seed pulled verbatim from JEX DEV (wecmsvndev.busi_vas_acct_infm,
-- 2026-05-11) for tenant VN_VNM0004_000001. 5 rows total — every live row
-- (DEL_YN='N') for the tenant — sized well under the project's data-migration
-- rule cap (max 30 rows / tenant per wecms-api-main/CLAUDE.md §"Data Migration
-- Rule"). Soft-deleted rows are intentionally NOT seeded — refactor only ever
-- reads DEL_YN='N' rows and the local DB stays light.
--
-- Coverage:
--   Status A (Active)   — 2 rows (vas_acct_seq_no = 132, 150)
--   Status I (Inactive) — 3 rows (vas_acct_seq_no = 149, 163, 164)
--   Types: DUAL ('0') x3, DEBIT ('2') x2
--
-- The seeded vas_acct_seq_no values are real DEV PKs (no Java-side generator
-- on the seed path). New refactor inserts go through VasAcctSeqNoGenerator.next()
-- which picks MAX+1 — verified safe vs. the seeded 164 (next allocation = 165).
-- =============================================================================

INSERT INTO BUSI_VAS_ACCT_INFM
    (BIZ_KEY, VAS_ACCT_SEQ_NO, VAS_ACCT_CD, VAS_ACCT_NM, VAS_ACCT_ENM,
     VAS_ACCT_TYPE, STTS, CONT, DEL_YN, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('VN_VNM0004_000001', 164, '1111', 'Tiền mặt',                           NULL, '2', 'I', NULL, 'N',
     'lamine@gmail.com', '20260428040345', 'lamine@gmail.com', '20260428040345'),
    ('VN_VNM0004_000001', 163, '111',  'Tiền mặt',                           NULL, '2', 'I', NULL, 'N',
     'lamine@gmail.com', '20260428040312', 'lamine@gmail.com', '20260428040312'),
    ('VN_VNM0004_000001', 150, '333',  'Test',                               NULL, '0', 'A', NULL, 'N',
     'lamine@gmail.com', '20250408011307', 'lamine@gmail.com', '20250408011307'),
    ('VN_VNM0004_000001', 149, '324',  'Test',                               NULL, '0', 'I', NULL, 'N',
     'lamine@gmail.com', '20250408011253', 'lamine@gmail.com', '20260313035227'),
    ('VN_VNM0004_000001', 132, '323',  'Jinpai Hill ,Taiyang''ao,BaihuaTown,', NULL, '0', 'A', NULL, 'N',
     'lamine@gmail.com', '20250319064406', 'lamine@gmail.com', '20251205092155')
ON CONFLICT (BIZ_KEY, VAS_ACCT_SEQ_NO) DO NOTHING;
