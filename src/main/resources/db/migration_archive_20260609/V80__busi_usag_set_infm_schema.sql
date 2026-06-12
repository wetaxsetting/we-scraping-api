-- ============================================================================
-- V80__busi_usag_set_infm_schema.sql
--
-- Settings > Purpose master table. Mirrors JEX DEV `busi_usag_set_infm`
-- column-for-column (verified via `postgres` MCP on 2026-05-12). Refactor
-- only uses rows where `usag_grp_cd IS NULL` (the level-2 hierarchy
-- bucket per `comm_0187_001_r001_act.jsp:50`). Legacy aux columns
-- (`usag_grp_cd`, `usag_type`, `rept_dv`, `rept_grp`) stay on the table
-- for schema parity but are never written by the refactor service.
--
-- Audit columns: ONLY `mod_id` / `mod_dtm` exist on this legacy table —
-- there are no `reg_id` / `reg_dtm` columns. The JPA entity therefore
-- does NOT extend `BaseEntity` (which assumes both pairs); it declares
-- the two MOD_* columns directly. See feature spec OQ-Backend-1.
--
-- PK: (biz_key, use_usag_cd). No FKs, no CHECK, no UNIQUE beyond PK on
-- DEV — refactor preserves the same shape so legacy data imports
-- cleanly. Service-layer enforces the `hand_use_usag_cd` /
-- `use_usag_nm` uniqueness scope (per OQ-7, partial unique indexes are
-- off by default; can be added in a future hardening migration).
-- ============================================================================

CREATE TABLE IF NOT EXISTS busi_usag_set_infm (
    biz_key             VARCHAR(20)  NOT NULL,
    use_usag_cd         VARCHAR(20)  NOT NULL,
    use_yn              VARCHAR(1)   NOT NULL DEFAULT 'Y',
    disp_yn             VARCHAR(1)   NOT NULL DEFAULT 'Y',
    disp_ord            NUMERIC,
    mod_id              VARCHAR(100),
    mod_dtm             VARCHAR(14),
    usag_grp_cd         VARCHAR(3),
    rept_dv             VARCHAR(2),
    rept_grp            VARCHAR(2),
    usag_type           CHAR(2),
    hand_use_usag_cd    VARCHAR(20),
    parent_use_usag_cd  VARCHAR(20),
    use_usag_nm         VARCHAR(255),
    use_usag_cont       VARCHAR(500),
    CONSTRAINT busi_usag_set_infm_pk PRIMARY KEY (biz_key, use_usag_cd)
);

-- Supporting index for the recursive child / "in use" queries the
-- refactor's service hits frequently:
--   1. hasActiveChildren(parent_use_usag_cd) — delete pre-check.
--   2. v_purpose_in_use branch (a) — parent referenced by active child.
-- DEV has no equivalent (the table runs cold there because the legacy
-- screen reads via a full table scan per tenant), but the refactor's
-- list endpoint LEFT JOINs against v_purpose_in_use on every request,
-- and pruning to active children pays off even at small tenant sizes.
CREATE INDEX IF NOT EXISTS busi_usag_set_infm_parent_ix
    ON busi_usag_set_infm (biz_key, parent_use_usag_cd)
    WHERE parent_use_usag_cd IS NOT NULL AND use_yn = 'Y';
