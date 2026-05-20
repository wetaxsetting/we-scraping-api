-- ============================================================================
-- V170 — Chart of Accounts: extend demo seed for VN_VNM0004_000001 with the
--        full DEV L2/L3 tree under ACC-01 / ACC-02 / ACC-03.
--
-- V97 only imported 9 rows for tenant VN_VNM0004_000001, which left ACC-02
-- (EXPENSES) and ACC-03 (INVESTMENT) with no L2 children locally. The Cash
-- Flow report frontend renders an empty placeholder row for each L1 with no
-- L2 children, which produced a sparse table on first load. This migration
-- pulls in the missing 17 rows from JEX DEV verbatim so the local Cash Flow
-- matrix mirrors what the live system shows.
--
-- Source: JEX DEV `wecmsvndev.chart_acct_infm` rows for biz_key
-- `VN_VNM0004_000001` where del_yn = 'N' and chart_acct_seq_no NOT IN
-- (90, 99, 100, 102, 103, 104, 105, 106, 112) [already seeded by V96/V97].
--
-- Idempotent: ON CONFLICT (BIZ_KEY, CHART_ACCT_SEQ_NO) DO NOTHING.
-- ============================================================================

INSERT INTO CHART_ACCT_INFM (
    BIZ_KEY, CHART_ACCT_SEQ_NO, CHART_ACCT_CD, CHART_ACCT_NM_EN,
    CHART_ACCT_NM_VN, CHART_ACCT_NM_KO, CHART_ACCT_TYPE,
    PARENT_CHART_ACCT_CD, STTS, DEL_YN,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM, CAL_RULE
) VALUES
    -- L2 under ACC-01 (REVENUE)
    ('VN_VNM0004_000001', '71', 'Code001', 'SALES', 'SALES', '영업',
        NULL, 'ACC-01', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),
    ('VN_VNM0004_000001', '72', 'Code002', 'OTHERS', 'OTHERS', '기타',
        NULL, 'ACC-01', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),
    ('VN_VNM0004_000001', '73', 'Code003', 'NON SALES', 'NON SALES', '영업외',
        NULL, 'ACC-01', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),
    ('VN_VNM0004_000001', '97', 'TEST-001', 'REVENUE-01', 'REVENUE-01', '수입',
        NULL, 'ACC-01', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),

    -- L2 under ACC-02 (EXPENSES)
    ('VN_VNM0004_000001', '75', 'Code004', 'PAYMENT', 'PAYMENT', '경비',
        NULL, 'ACC-02', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),
    ('VN_VNM0004_000001', '76', 'Code005', 'IM-EX', 'IM-EX', '수출입',
        NULL, 'ACC-02', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),
    ('VN_VNM0004_000001', '78', 'Code007', 'SALARY', 'SALARY', '인건비',
        NULL, 'ACC-02', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),

    -- L2 under ACC-03 (INVESTMENT)
    ('VN_VNM0004_000001', '81', 'Code009', 'INVESTMENT', 'INVESTMENT', '투자',
        NULL, 'ACC-03', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', 'P'),
    ('VN_VNM0004_000001', '85', 'Code0091', 'INVESTMENT STOCK', 'INVESTMENT STOCK', 'INVESTMENT STOCK',
        NULL, 'ACC-03', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', 'M'),

    -- L3 under Code001 (SALES)
    ('VN_VNM0004_000001', '86', 'Code0011', 'SALES TEST', 'SALES TEST', 'SALES TEST',
        NULL, 'Code001', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),
    ('VN_VNM0004_000001', '88', 'Code0012', 'SALES TEST 2', 'SALES TEST 2', 'SALES TEST 2',
        NULL, 'Code001', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),

    -- L3 under Code002 (OTHERS)
    ('VN_VNM0004_000001', '82', 'Code0033', 'OTHERS SALE', 'OTHERS SALE', 'OTHERS SALE',
        NULL, 'Code002', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),
    ('VN_VNM0004_000001', '83', 'Code0021', 'OTHERS SALE 2', 'OTHERS SALE 2', 'OTHERS SALE 2',
        NULL, 'Code002', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),

    -- L3 under Code003 (NON SALES)
    ('VN_VNM0004_000001', '89', 'Code0031', 'NON SALES TEST', 'NON SALES TEST', 'NON SALES TEST',
        NULL, 'Code003', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),

    -- L3 under Code005 (IM-EX)
    ('VN_VNM0004_000001', '87', 'Code0051', 'IM-EX TEST', 'IM-EX TEST', 'IM-EX TEST',
        NULL, 'Code005', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),

    -- L3 under 1007009 (Collection 4)
    ('VN_VNM0004_000001', '111', 'TEST01', 'TEST', 'TEST', 'TEST',
        NULL, '1007009', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL),

    -- L3 under 2001001 (Payment K1 BLU)
    ('VN_VNM0004_000001', '101', 'F033309', 'Collection 4', '회수4', '회수4',
        NULL, '2001001', 'A', 'N',
        'lamine@gmail.com', '20250901000000', 'lamine@gmail.com', '20250901000000', NULL)
ON CONFLICT (BIZ_KEY, CHART_ACCT_SEQ_NO) DO NOTHING;
