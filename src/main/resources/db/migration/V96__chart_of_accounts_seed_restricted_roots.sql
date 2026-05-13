-- ============================================================================
-- V96__chart_of_accounts_seed_restricted_roots.sql
--
-- Settings > Chart of Accounts: seed the three system-managed root rows
-- (ACC-01 REVENUE, ACC-02 EXPENSES, ACC-03 INVESTMENT) for every active
-- tenant in BUSI_INFM. Resolves the planning OQ on root-row
-- provisioning — Flyway-time seed (not on-demand auto-creation on first
-- read).
--
-- These three codes are the only "restricted" rows in the feature:
-- ChartOfAccountsService blocks UPDATE / DELETE against them via
-- WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE (legacy mirror: comm_0223_001_u001
-- + d001 act.jsp both throw WbMessageCode.WBE1178 for the same set
-- "ACC-01" / "ACC-02" / "ACC-03"). The CHART_ACCT_INFM rows themselves
-- carry no flag — the set is hard-coded by code at every layer
-- (frontend `restricted: true` projection comes off this same set, the
-- backend validator's static field, the legacy `_act.jsp` Set literal).
--
-- Per-tenant seed values:
--   - chart_acct_seq_no  → allocated via wb_get_next_val (V95). Stays
--     tenant-disjoint; subsequent user-created rows get values starting
--     at 4 for fresh tenants, or MAX(seq)+1 for tenants that already
--     have data (V97 demo seed preserves DEV seq numbers 70..112, so
--     the next user-created row for VN_VNM0004_000001 will get 113 —
--     confirmed via DEV introspection 2026-05-13).
--   - reg_id / mod_id    → biz_key (legacy convention — see DEV rows
--     for tenant 000001 seq 70/74/80 with reg_id = 'VN_VNM0004_000001').
--   - reg_dtm / mod_dtm  → '20260513000000' (deterministic seed marker;
--     identifies the row as a Flyway seed rather than a user write).
--
-- DEV name shape (verified 2026-05-13 via postgres_dev MCP, biz_key
-- VN_VNM0004_000001 seq 70/74/80):
--   ACC-01  →  REVENUE     / REVENUE     / 수입
--   ACC-02  →  EXPENSES    / EXPENSES    / 지출
--   ACC-03  →  INVESTMENT  / INVESTMENT  / 투자
--
-- VI names mirror EN (DEV stores English in CHART_ACCT_NM_VN for these
-- three rows — the column allows NULL but the seed rows do not use it).
--
-- Idempotency:
--   ON CONFLICT (BIZ_KEY, CHART_ACCT_CD, DEL_YN) DO NOTHING — re-runs
--   are no-ops. Multiple Flyway re-applies safe.
--
-- Feature spec: docs/refactor-wecms/features/chart-of-accounts.md §4.2
-- Database doc: docs/refactor-wecms/database/chart-of-accounts.md §2.3
-- ============================================================================

INSERT INTO CHART_ACCT_INFM (
    BIZ_KEY,
    CHART_ACCT_SEQ_NO,
    CHART_ACCT_CD,
    CHART_ACCT_NM_EN,
    CHART_ACCT_NM_VN,
    CHART_ACCT_NM_KO,
    CHART_ACCT_TYPE,
    PARENT_CHART_ACCT_CD,
    STTS,
    DEL_YN,
    REG_ID,
    REG_DTM,
    MOD_ID,
    MOD_DTM,
    CAL_RULE
)
SELECT
    b.BIZ_KEY,
    wb_get_next_val('chart_acct_infm', 'chart_acct_seq_no',
                    b.BIZ_KEY, NULL, NULL, NULL, NULL, NULL)   AS chart_acct_seq_no,
    s.cd                                                       AS chart_acct_cd,
    s.name_en                                                  AS chart_acct_nm_en,
    s.name_vn                                                  AS chart_acct_nm_vn,
    s.name_ko                                                  AS chart_acct_nm_ko,
    NULL                                                       AS chart_acct_type,
    NULL                                                       AS parent_chart_acct_cd,
    'A'                                                        AS stts,
    'N'                                                        AS del_yn,
    b.BIZ_KEY                                                  AS reg_id,
    '20260513000000'                                           AS reg_dtm,
    b.BIZ_KEY                                                  AS mod_id,
    '20260513000000'                                           AS mod_dtm,
    NULL                                                       AS cal_rule
FROM BUSI_INFM b
CROSS JOIN (VALUES
    ('ACC-01', 'REVENUE',    'REVENUE',    '수입'),
    ('ACC-02', 'EXPENSES',   'EXPENSES',   '지출'),
    ('ACC-03', 'INVESTMENT', 'INVESTMENT', '투자')
) AS s(cd, name_en, name_vn, name_ko)
WHERE COALESCE(b.DEL_YN, 'N') = 'N'
ON CONFLICT (BIZ_KEY, CHART_ACCT_CD, DEL_YN) DO NOTHING;
