-- ============================================================================
-- V90__approval_line_schema.sql
--
-- Settings > Approval Line (slug: approval-line) feature schema. Two
-- PROD-active-list tables ported column-for-column from DEV
-- (wecmsvndevdb @ 10.254.242.254:54311, verified 2026-05-13):
--   APPR_LINE_INFM       — approval-line master (7 cols, PK 2 cols)
--   APPR_LINE_INFM_DETL  — approver chain rows (12 cols, PK 3 cols)
--
-- DEV column-shape snapshot (information_schema.columns + pg_constraint
-- via the read-only `postgres_dev` MCP, 2026-05-13):
--   - APPR_LINE_INFM PK = (biz_key, appr_line_no). All seven columns
--     NOT NULL on DEV. No additional indexes.
--   - APPR_LINE_INFM_DETL PK = (biz_key, appr_line_no, apprvr_id).
--     apprvr_disp_ord / apprvr_id / apprvr_nm NOT NULL; everything else
--     nullable (including the audit columns reg_id / reg_dtm / mod_id /
--     mod_dtm — preserved verbatim so older DEV rows with NULL audit
--     fields copy cleanly). No additional indexes.
--
-- Schema follows the Legacy Schema Parity Rule from
-- `wecms-api/CLAUDE.md`: column names UPPER_SNAKE_CASE, timestamp
-- columns VARCHAR(14) (yyyyMMddHHmmss) to mirror DEV. Same precedent as
-- V84 (contacts) — entities do NOT extend BaseEntity for these tables;
-- the service stamps audit columns explicitly.
--
-- Index addition beyond DEV: ix_appr_line_infm_biz_reg_dtm supports the
-- default list sort (reg_dtm DESC tie-broken on appr_line_no DESC). This
-- is permitted by the Legacy Schema Parity Rule for performance.
--
-- Idempotent — CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS.
-- Re-running this migration on an already-migrated DB is a no-op.
--
-- Feature spec:    docs/refactor-wecms/features/approval-line.md
-- Backend design:  docs/refactor-wecms/backend/approval-line.md
-- Migration map:   docs/refactor-wecms/migration/approval-line.md
-- ============================================================================

-- ---------------------------------------------------------------------------
-- APPR_LINE_INFM — approval-line master
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS APPR_LINE_INFM (
    BIZ_KEY        VARCHAR(20)   NOT NULL,
    APPR_LINE_NO   NUMERIC(15,0) NOT NULL,
    APPR_LINE_NM   VARCHAR(100)  NOT NULL,
    REG_ID         VARCHAR(100)  NOT NULL,
    REG_DTM        VARCHAR(14)   NOT NULL,
    MOD_ID         VARCHAR(100)  NOT NULL,
    MOD_DTM        VARCHAR(14)   NOT NULL,
    CONSTRAINT pk_appr_line_infm PRIMARY KEY (BIZ_KEY, APPR_LINE_NO)
);

-- Default-sort support: list ORDER BY reg_dtm DESC, appr_line_no DESC
-- per-tenant. The PK alone is BIZ_KEY-leading so a bare predicate on
-- BIZ_KEY can still use it; this index materialises the full sort to
-- avoid a sort step in the planner. Allowed beyond-DEV per the Legacy
-- Schema Parity Rule.
CREATE INDEX IF NOT EXISTS ix_appr_line_infm_biz_reg_dtm
    ON APPR_LINE_INFM (BIZ_KEY, REG_DTM DESC, APPR_LINE_NO DESC);

-- ---------------------------------------------------------------------------
-- APPR_LINE_INFM_DETL — approver chain rows (max 5 per line; cap enforced
-- in the service layer by WBE_APPROVAL_LINE_TOO_MANY_APPROVERS, mirroring
-- legacy WbMessageCode.WBW1164).
--
-- DISP_ORD type: NUMERIC(1,0) on DEV — pin the cap shape at the DB level.
-- POSI / DEPT / audit columns nullable to match DEV (older rows have NULLs).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS APPR_LINE_INFM_DETL (
    BIZ_KEY          VARCHAR(20)   NOT NULL,
    APPR_LINE_NO     NUMERIC(15,0) NOT NULL,
    APPRVR_DISP_ORD  NUMERIC(1,0)  NOT NULL,
    APPRVR_ID        VARCHAR(100)  NOT NULL,
    APPRVR_NM        VARCHAR(100)  NOT NULL,
    APPRVR_POSI      VARCHAR(100),
    APPRVR_DEPT      VARCHAR(100),
    REG_ID           VARCHAR(100),
    REG_DTM          VARCHAR(14),
    MOD_ID           VARCHAR(100),
    MOD_DTM          VARCHAR(14),
    CONSTRAINT pk_appr_line_infm_detl
        PRIMARY KEY (BIZ_KEY, APPR_LINE_NO, APPRVR_ID)
);

-- DEFERRED (DEV-only, out of refactor scope for this MR):
--   No FK constraint between detail and master on DEV — preserve that
--   parity. Cascading delete is performed by the service via a single
--   @Modifying delete on the detail repository, then deleteById on the
--   master (mirrors legacy ApprLineInfmDetlDAO.deleteByFk pattern).
