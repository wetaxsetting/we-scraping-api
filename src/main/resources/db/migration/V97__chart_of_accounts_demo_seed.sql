-- ============================================================================
-- V97__chart_of_accounts_demo_seed.sql
--
-- Settings > Chart of Accounts: demo seed for tenant VN_VNM0004_000001.
-- Snapshot taken from real JEX DEV (wecmsvndevdb @ 10.254.242.254:54311,
-- 2026-05-13). Per the "Data Migration Rule" in wecms-api-main/CLAUDE.md:
-- only biz_key 'VN_VNM0004_000001', ≤ 30 rows, parents before children,
-- foreign-key integrity preserved (here: PARENT_CHART_ACCT_CD references
-- another row's CHART_ACCT_CD; no FK declared in DEV, but logical
-- integrity preserved so refactor reads cleanly).
--
-- The three restricted roots (ACC-01 / ACC-02 / ACC-03) are seeded by
-- V96 for every active tenant — V97 only seeds the user-created rows
-- DEV carries for VN_VNM0004_000001 (seq 90..112 range; 9 rows).
--
-- CHART_ACCT_SEQ_NO values are preserved VERBATIM from DEV so that
-- (a) any user-created rows from earlier static-UI work won't collide,
-- and (b) `wb_get_next_val('chart_acct_infm','chart_acct_seq_no',
-- 'VN_VNM0004_000001')` returns 113 immediately after this migration
-- (verified live on DEV 2026-05-13: the call returned 113).
--
-- Two DEV rows in this snapshot have logically interesting shapes:
--   - seq 104: row CHART_ACCT_CD='2001001' has PARENT_CHART_ACCT_CD='2001001'
--     (self-referencing). DEV legacy code allows it; the refactor blocks
--     self-parent only on new Create/Update writes (preserves grandfathered
--     bad data — see feature spec §5 and OQ-2).
--   - seq 112: row CHART_ACCT_CD='CDX-001' has PARENT_CHART_ACCT_CD='Code009'
--     where 'Code009' does NOT exist in the snapshot. Orphan parent —
--     same grandfathering rule applies. Service treats these rows with
--     chart_acct_level = null in the projection.
--
-- Idempotent: INSERT ... ON CONFLICT (BIZ_KEY, CHART_ACCT_SEQ_NO) DO NOTHING.
--
-- Feature spec:  docs/refactor-wecms/features/chart-of-accounts.md
-- Database doc:  docs/refactor-wecms/database/chart-of-accounts.md
-- ============================================================================

INSERT INTO CHART_ACCT_INFM (
    BIZ_KEY, CHART_ACCT_SEQ_NO, CHART_ACCT_CD, CHART_ACCT_NM_EN,
    CHART_ACCT_NM_VN, CHART_ACCT_NM_KO, CHART_ACCT_TYPE,
    PARENT_CHART_ACCT_CD, STTS, DEL_YN,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM, CAL_RULE
) VALUES
    ('VN_VNM0004_000001', '90', '10070090', 'Collection 45', '회수4', '회수4',
        NULL, NULL, 'A', 'N',
        'lamine@gmail.com', '20250812083037', 'lamine@gmail.com', '20260213033723', 'P'),
    ('VN_VNM0004_000001', '99', '1007009', 'Collection 4', '회수4', '회수4',
        NULL, 'ACC-01', 'A', 'N',
        'lamine@gmail.com', '20250915035607', 'lamine@gmail.com', '20250915035607', NULL),
    ('VN_VNM0004_000001', '100', '1007010', 'Collection 5', '회수5', '회수5',
        NULL, 'ACC-01', 'A', 'N',
        'lamine@gmail.com', '20250915035607', 'lamine@gmail.com', '20250915035607', NULL),
    ('VN_VNM0004_000001', '102', '1010324', 'Collection 5', '회수5', '회수5',
        NULL, '2001001', 'A', 'N',
        'lamine@gmail.com', '20251013082649', 'lamine@gmail.com', '20251013082649', NULL),
    ('VN_VNM0004_000001', '103', '6018002', 'Others 2', 'Khác', '금융상품외 OTHERS',
        NULL, '2001001', 'A', 'N',
        'lamine@gmail.com', '20251013082649', 'lamine@gmail.com', '20251013082649', NULL),
    ('VN_VNM0004_000001', '104', '2001001', 'Payment K1 (BLU)', 'Chi trả 1 (BLU)', '지급(K1 BLU)',
        NULL, '2001001', 'A', 'N',
        'lamine@gmail.com', '20251013082649', 'lamine@gmail.com', '20251013082649', NULL),
    ('VN_VNM0004_000001', '105', '2001002', 'Payment K1 (LGP)', 'Chi trả 1 (K1 LGP)', '지급(K1 LGP)',
        NULL, '2001001', 'A', 'N',
        'lamine@gmail.com', '20251013082649', 'lamine@gmail.com', '20251013082649', NULL),
    ('VN_VNM0004_000001', '106', '2001003', 'Payment 1', 'Chi trả 1', NULL,
        NULL, '2001001', 'A', 'N',
        'lamine@gmail.com', '20251013082649', 'lamine@gmail.com', '20251013082649', NULL),
    ('VN_VNM0004_000001', '112', 'CDX-001', 'asdfasd', 'asdf', 'asdf',
        NULL, 'Code009', 'A', 'N',
        'lamine@gmail.com', '20260213065846', 'lamine@gmail.com', '20260213065846', 'P')
ON CONFLICT (BIZ_KEY, CHART_ACCT_SEQ_NO) DO NOTHING;
