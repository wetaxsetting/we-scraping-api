-- ============================================================================
-- V160__smart_organizer_rmrk_org_infm_schema.sql
--
-- Banking > Smart Organizer: RMRK_ORG_INFM schema (rule table).
--
-- Refactor slug: `smart-organizer`. Legacy WSVC: fnsh_0200_001 (page) +
-- fnsh_0200_002 (Organizer Settings popup) — preserved only in citations.
--
-- Column-for-column parity with DEV (wecmsvndev.rmrk_org_infm, captured
-- via the read-only `postgres_dev` MCP on 2026-05-16): 13 columns, PK
-- on (biz_key, rmrk_seq_no). No other constraints (no UK / CHECK / FK
-- on DEV). Helper indexes added beyond DEV per the Legacy Schema
-- Parity Rule ("extra indexes don't hurt parity"):
--
--   - ix_rmrk_org_infm_biz_disp     — drives the default ORDER BY for
--                                     the Organizer Settings list
--                                     (BIZ_KEY, DISP_ORD ASC). Mirrors
--                                     legacy IDO RMRK_ORG_INFM_R002
--                                     `ORDER BY DISP_ORD`.
--   - ix_rmrk_org_infm_biz_rcv_pay  — supports the rule-match join in
--                                     the Smart Organizer eligibility
--                                     query (RCV_PAY_DV = 'RCV'|'PAY'
--                                     branches off INOUT_DV).
--
-- `disp_ord` deliberately stored as INTEGER (refactor convention),
-- NOT DEV's NUMERIC(10,0) — same precedent as V36 (SysScrOptionWecms),
-- V89 (Contacts), V92 (Approval Line). Hibernate 6 `ddl-auto: validate`
-- rejects NUMERIC against Java `Integer`/`Long` mappings. DISP_ORD on
-- this table carries small per-rule ordering values (1..N per tenant),
-- well inside INTEGER range — lossless. `rmrk_seq_no` likewise stored
-- as BIGINT (per-tenant MAX+1, fits in BIGINT).
--
-- `match_type` is `CHAR(1)` on DEV (bpchar). Refactor entity will
-- annotate with `@JdbcTypeCode(SqlTypes.CHAR)` so the mapping accepts
-- the bpchar column (same precedent as V38 sort_yn).
--
-- Idempotent: CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS.
-- ============================================================================

CREATE TABLE IF NOT EXISTS RMRK_ORG_INFM (
    BIZ_KEY            VARCHAR(20)  NOT NULL,
    RMRK_SEQ_NO        BIGINT       NOT NULL,
    RCV_PAY_DV         VARCHAR(3),
    RMRK               VARCHAR(500) NOT NULL,
    MATCH_TYPE         CHAR(1)      NOT NULL,
    DISP_ORD           INTEGER      NOT NULL,
    TRCO_KEY           VARCHAR(20),
    USE_USAG_CD        VARCHAR(20),
    VAS_ACCT_SEQ_NO    BIGINT,
    REG_ID             VARCHAR(100),
    REG_DTM            VARCHAR(14),
    CHART_ACCT_SEQ_NO  VARCHAR(10),
    CHART_ACCT_CD      VARCHAR(20),
    CONSTRAINT PK_RMRK_ORG_INFM PRIMARY KEY (BIZ_KEY, RMRK_SEQ_NO)
);

-- Default list ORDER BY (legacy IDO.RMRK_ORG_INFM_R002.xml `ORDER BY DISP_ORD`).
CREATE INDEX IF NOT EXISTS IX_RMRK_ORG_INFM_BIZ_DISP
    ON RMRK_ORG_INFM (BIZ_KEY, DISP_ORD);

-- Smart Organizer eligibility join — RCV_PAY_DV bucket per tenant.
CREATE INDEX IF NOT EXISTS IX_RMRK_ORG_INFM_BIZ_RCV_PAY
    ON RMRK_ORG_INFM (BIZ_KEY, RCV_PAY_DV);

-- ============================================================================
-- Deferred items (per the Legacy Schema Parity Rule trailing-comment
-- convention):
--   - No UK / CHECK / FK on DEV → none locally.
--   - No audit-log trigger ported (legacy DEV doesn't have one for this
--     table either). Refactor audits writes via AUDIT_LOGS_INFM with
--     `WSVC_ID = 'smart-organizer'` (slug, not legacy fnsh_0200_002).
--   - No demo seed for tenant VN_VNM0004_000001 — DEV's rmrk_org_infm
--     is empty for this tenant as of 2026-05-16. Refactor users create
--     rules via the Organizer Settings popup.
-- ============================================================================
