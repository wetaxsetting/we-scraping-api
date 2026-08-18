-- WeCMS Manual Database Change
-- Date: 2026-06-26
-- Source: migrated from Claude Code (AI)/sql/chart_acct_infm_l1_seed_20260626.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: Refactor DEV DB (172.168.5.172:5432/wecmsdevdb)
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- Seed: Chart of Accounts — built-in Level 1 (root) system records
-- Table   : chart_acct_infm
-- Target  : Refactor DEV DB (postgres_dev_server_refactor, 172.168.5.172:5432/wecmsdevdb)
-- Source  : Legacy JEX DEV DB (postgres_dev_server_jexframe) — read 2026-06-26
-- Tenant  : biz_key = 'VN_VNM0004_000001'
-- Author  : Claude Code (AI)
-- Run by  : DBA, by hand. NOT a Flyway migration (Flyway disabled, ddl-auto=validate).
-- =============================================================================
--
-- WHAT THIS DOES
--   Inserts the three default Level-1 (parent IS NULL) chart-of-accounts roots that
--   the legacy system seeds at company creation:
--       ACC-01 REVENUE / 수입,  ACC-02 EXPENSES / 지출,  ACC-03 INVESTMENT / 투자
--   All three are system-seeded in legacy (reg_id = mod_id = biz_key), stts='A',
--   del_yn='N', chart_acct_type=NULL, cal_rule=NULL.
--
-- KEY GENERATION (per task requirement)
--   chart_acct_cd is a FIXED business key (ACC-01/02/03) -> preserved verbatim.
--   chart_acct_seq_no is a per-tenant SURROGATE (legacy via SeqHelper, stored as
--   varchar). It is NOT hardcoded here: the script reads the current MAX seq for the
--   tenant and allocates the next contiguous values automatically. With an empty
--   refactor table this yields 1, 2, 3; if rows already exist it continues after the max.
--
-- IDEMPOTENCY / CONSTRAINT SAFETY
--   A NOT EXISTS guard on uk_chart_acct_cd (biz_key, chart_acct_cd, del_yn='N') means
--   re-running inserts nothing for codes already present — no PK or unique violation.
--   Wrapped in a single transaction.
--
-- SCHEMA NOTE
--   Legacy chart_acct_seq_no is varchar(14); refactor is varchar(10). Legacy values
--   (70/74/80) and the generated values fit in 10 chars, so the difference is harmless.
--   All other 14 columns are structurally identical between the two databases.
--
-- AUDIT FIELDS
--   reg_id/mod_id/reg_dtm/mod_dtm are preserved EXACTLY as in legacy (these are the
--   same system records being migrated, so their original provenance is kept).
--
-- MIGRATION CAVEAT (read before running in any shared/UAT/PROD-facing context)
--   Downstream tables (acct_trns_hstr, card_trns_hstr, *_pymn_sumr_detl, rept_trns_hstr,
--   rmrk_org_infm) reference chart accounts by the (chart_acct_cd, chart_acct_seq_no)
--   PAIR. This script intentionally regenerates chart_acct_seq_no (per task), so the new
--   seq (e.g. 1/2/3) will differ from legacy (70/74/80). That is fine for a fresh,
--   empty refactor tenant, but if legacy transaction data is later migrated for this
--   tenant, either (a) migrate that data with remapped seq values, or (b) re-run this
--   seed preserving the legacy seq (70/74/80) instead. Raise with PO before a real
--   data migration.
-- =============================================================================

BEGIN;

INSERT INTO chart_acct_infm (
    biz_key,
    chart_acct_seq_no,
    chart_acct_cd,
    chart_acct_nm_en,
    chart_acct_nm_vn,
    chart_acct_nm_ko,
    chart_acct_type,
    parent_chart_acct_cd,
    stts,
    del_yn,
    reg_id,
    reg_dtm,
    mod_id,
    mod_dtm,
    cal_rule
)
SELECT
    s.biz_key,
    (base.next_seq + s.rn - 1)::text AS chart_acct_seq_no,  -- auto-generated, contiguous
    s.chart_acct_cd,
    s.chart_acct_nm_en,
    s.chart_acct_nm_vn,
    s.chart_acct_nm_ko,
    s.chart_acct_type,
    s.parent_chart_acct_cd,
    s.stts,
    s.del_yn,
    s.reg_id,
    s.reg_dtm,
    s.mod_id,
    s.mod_dtm,
    s.cal_rule
FROM (
    VALUES
        -- biz_key,             cd,       en,           vn,           ko,    type, parent, stts, del_yn, reg_id,              reg_dtm,          mod_id,              mod_dtm,          cal_rule, rn
        ('VN_VNM0004_000001', 'ACC-01', 'REVENUE',    'REVENUE',    '수입', NULL::varchar, NULL::varchar, 'A', 'N', 'VN_VNM0004_000001', '20250806065018', 'VN_VNM0004_000001', '20250806065018', NULL::char(1), 1),
        ('VN_VNM0004_000001', 'ACC-02', 'EXPENSES',   'EXPENSES',   '지출', NULL,          NULL,          'A', 'N', 'VN_VNM0004_000001', '20250806065018', 'VN_VNM0004_000001', '20250806065018', NULL,          2),
        ('VN_VNM0004_000001', 'ACC-03', 'INVESTMENT', 'INVESTMENT', '투자', NULL,          NULL,          'A', 'N', 'VN_VNM0004_000001', '20250806065018', 'VN_VNM0004_000001', '20250806065018', NULL,          3)
) AS s (
    biz_key, chart_acct_cd, chart_acct_nm_en, chart_acct_nm_vn, chart_acct_nm_ko,
    chart_acct_type, parent_chart_acct_cd, stts, del_yn,
    reg_id, reg_dtm, mod_id, mod_dtm, cal_rule, rn
)
CROSS JOIN (
    SELECT COALESCE(MAX(chart_acct_seq_no::int), 0) + 1 AS next_seq
    FROM chart_acct_infm
    WHERE biz_key = 'VN_VNM0004_000001'
) AS base
WHERE NOT EXISTS (
    SELECT 1
    FROM chart_acct_infm e
    WHERE e.biz_key        = s.biz_key
      AND e.chart_acct_cd  = s.chart_acct_cd
      AND e.del_yn         = 'N'
);

COMMIT;

-- =============================================================================
-- VERIFICATION (run after the INSERT)
-- Expect 3 rows: ACC-01/02/03, parent NULL, stts A, del_yn N, generated seq values.
-- =============================================================================
-- SELECT biz_key, chart_acct_seq_no, chart_acct_cd,
--        chart_acct_nm_en, chart_acct_nm_vn, chart_acct_nm_ko,
--        chart_acct_type, parent_chart_acct_cd, stts, del_yn, cal_rule,
--        reg_id, reg_dtm, mod_id, mod_dtm
-- FROM chart_acct_infm
-- WHERE biz_key = 'VN_VNM0004_000001'
--   AND chart_acct_cd IN ('ACC-01','ACC-02','ACC-03')
-- ORDER BY chart_acct_cd;

-- =============================================================================
-- ROLLBACK (removes ONLY the three seeded roots for this tenant; leaves any
-- other rows untouched. Safe because ACC-01/02/03 are fixed business keys.)
-- =============================================================================
-- BEGIN;
-- DELETE FROM chart_acct_infm
-- WHERE biz_key = 'VN_VNM0004_000001'
--   AND chart_acct_cd IN ('ACC-01','ACC-02','ACC-03')
--   AND parent_chart_acct_cd IS NULL
--   AND reg_id = 'VN_VNM0004_000001';   -- system-seeded guard
-- COMMIT;
