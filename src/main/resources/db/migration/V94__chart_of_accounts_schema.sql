-- ============================================================================
-- V94__chart_of_accounts_schema.sql
--
-- Settings > Chart of Accounts (slug: chart-of-accounts) feature schema.
-- One PROD-active-list table ported column-for-column from DEV
-- (wecmsvndevdb @ 10.254.242.254:54311, verified 2026-05-13):
--   CHART_ACCT_INFM — chart-of-accounts master (15 cols, PK 2 cols, UK 3 cols)
--
-- DEV column-shape snapshot (information_schema.columns + pg_constraint
-- via the read-only postgres_dev MCP, 2026-05-13):
--   - PK (biz_key, chart_acct_seq_no)
--   - UK uk_chart_acct_cd (biz_key, chart_acct_cd, del_yn) — drives the
--     revive-on-create + rename-on-delete behaviour (one active + one
--     soft-deleted row per code is the legal state).
--   - No FK constraints (refactor mirrors).
--   - No CHECK constraints (refactor mirrors; STTS / DEL_YN / CAL_RULE
--     value validation lives in the JPA enums + bean validation).
--   - No additional indexes beyond PK + UK.
--
-- Schema follows the Legacy Schema Parity Rule from
-- `wecms-api-main/CLAUDE.md`: column names UPPER_SNAKE_CASE, timestamp
-- columns VARCHAR(14) (yyyyMMddHHmmss) to mirror DEV. Same precedent as
-- V84 (contacts) and V90 (approval-line) — entity does NOT extend
-- BaseEntity; the service stamps audit columns explicitly.
--
-- Indexes added beyond DEV (allowed per the Legacy Schema Parity Rule's
-- "Indexes beyond PK/UK" clause):
--   - ix_chart_acct_infm_biz_parent — partial WHERE del_yn='N'.
--     Powers the child re-parent query in Delete + the has_children
--     group-count in List.
--   - ix_chart_acct_infm_biz_moddtm — supports the optional MOD_DTM
--     secondary sort. Same precedent as V90 ix_appr_line_infm_biz_reg_dtm.
--
-- DEV stores `chart_acct_seq_no` as VARCHAR(14). The refactor entity
-- maps it as `String` end-to-end (entity + DTO + path variable) so no
-- AttributeConverter is needed and no schema divergence from DEV is
-- introduced. The wire delivers the value as a numeric string token
-- (e.g. "70"). Frontend treats it as an opaque identifier; if a numeric
-- comparison is needed it parses on the consumer side. Same convention
-- as ERP_TRNS_HSTR (VARCHAR(14) reg_dtm — handled as String).
--
-- Idempotent — CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS.
-- Re-running this migration on an already-migrated DB is a no-op.
--
-- Feature spec:    docs/refactor-wecms/features/chart-of-accounts.md
-- Backend design:  docs/refactor-wecms/backend/chart-of-accounts.md
-- Migration map:   docs/refactor-wecms/migration/chart-of-accounts.md
-- ============================================================================

-- ---------------------------------------------------------------------------
-- CHART_ACCT_INFM — chart-of-accounts master
-- Column count parity with DEV: 15
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS CHART_ACCT_INFM (
    BIZ_KEY              VARCHAR(20)  NOT NULL,
    CHART_ACCT_SEQ_NO    VARCHAR(14)  NOT NULL,
    CHART_ACCT_CD        VARCHAR(20)  NOT NULL,
    CHART_ACCT_NM_EN     VARCHAR(255) NOT NULL,
    CHART_ACCT_NM_VN     VARCHAR(255),
    CHART_ACCT_NM_KO     VARCHAR(255),
    CHART_ACCT_TYPE      VARCHAR(100),
    PARENT_CHART_ACCT_CD VARCHAR(20),
    STTS                 CHAR(1)      NOT NULL,
    DEL_YN               CHAR(1)      NOT NULL DEFAULT 'N',
    REG_ID               VARCHAR(100) NOT NULL,
    REG_DTM              VARCHAR(14)  NOT NULL,
    MOD_ID               VARCHAR(100) NOT NULL,
    MOD_DTM              VARCHAR(14)  NOT NULL,
    CAL_RULE             CHAR(1),
    CONSTRAINT pk_chart_acct_infm PRIMARY KEY (BIZ_KEY, CHART_ACCT_SEQ_NO),
    CONSTRAINT uk_chart_acct_cd   UNIQUE      (BIZ_KEY, CHART_ACCT_CD, DEL_YN)
);

-- Powers the child re-parent query in ChartOfAccountsService.delete +
-- the has_children group-count in list. Partial index keeps it small;
-- soft-deleted rows are never read on these paths.
CREATE INDEX IF NOT EXISTS ix_chart_acct_infm_biz_parent
    ON CHART_ACCT_INFM (BIZ_KEY, PARENT_CHART_ACCT_CD)
    WHERE DEL_YN = 'N';

-- Supports an optional MOD_DTM secondary sort path on List. Same
-- precedent as V90 ix_appr_line_infm_biz_reg_dtm.
CREATE INDEX IF NOT EXISTS ix_chart_acct_infm_biz_moddtm
    ON CHART_ACCT_INFM (BIZ_KEY, MOD_DTM DESC);

-- DEFERRED (DEV-only, out of refactor scope for this MR):
--   No FK constraint between this table and BUSI_INFM(BIZ_KEY) on DEV —
--   preserve that parity. Tenant isolation enforced in the service via
--   TenantContext.currentBizKey().
--
--   No self-referential FK between PARENT_CHART_ACCT_CD and CHART_ACCT_CD
--   within the same tenant — preserve that parity. Parent existence
--   enforced in ChartOfAccountsValidator at write time.
