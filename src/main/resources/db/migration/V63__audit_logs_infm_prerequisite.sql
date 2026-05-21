-- ============================================================================
-- V63__audit_logs_infm_prerequisite.sql
--
-- Cross-cutting prerequisite — installs AUDIT_LOGS_INFM (legacy JEX
-- shared audit-log table) so the firm-banking-settings audit-read
-- endpoint has something to query against. Mirrors legacy JEX
-- wecmsvndev.audit_logs_infm column-for-column (15 cols, PK
-- (BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD)).
--
-- OWNERSHIP — this table is shared infrastructure. The full audit-log
-- feature (write path: shared AuditLogService, three-language activity
-- + detail key resolution, REQUIRES_NEW transactional semantics, etc.)
-- is a separate cross-feature MR (Phase 1 §11.1, prerequisite P-1).
-- This migration is the bare-minimum schema bootstrap so that the
-- firm-banking-settings audit-read endpoint can ship in the read-only
-- Phase 2 slice (Path A, OQ-2 + OQ-6 resolved 2026-05-09).
--
-- When the audit-log feature lands:
--   1. Add the writer service under domain/common/auditlog/ (no
--      schema change — this table already supports the writer).
--   2. Author Update / Delete (none) for firm-banking-settings.
--   3. Other features (purpose, approval-line, ...) consume the same
--      writer and the same table.
--
-- The wsvc_id column carries the legacy JEX page id verbatim (e.g.
-- 'comm_0220_001' for firm-banking-settings) per OQ-6. See
-- wecms-api/CLAUDE.md §"Refactor identifier naming".
--
-- Idempotent: CREATE TABLE IF NOT EXISTS.
-- ============================================================================

CREATE TABLE IF NOT EXISTS AUDIT_LOGS_INFM (
    BIZ_KEY              VARCHAR(20)   NOT NULL,
    AUDIT_LOGS_SEQ_NO    NUMERIC       NOT NULL,
    WSVC_ID              VARCHAR(20)   NOT NULL,
    LANG_CD              VARCHAR(3)    NOT NULL,
    REF_NO               VARCHAR(50)   NOT NULL,
    REF_NO1              VARCHAR(50)   NOT NULL,
    REF_NO2              VARCHAR(50),
    REF_NO3              VARCHAR(50),
    REF_NO4              VARCHAR(50),
    REF_NO5              VARCHAR(50),
    AUDIT_LOGS_ACTV      VARCHAR(50),
    AUDIT_LOGS_ACTV_DTL  TEXT          NOT NULL,
    REG_ID               VARCHAR(100)  NOT NULL,
    REG_NM               VARCHAR(100)  NOT NULL,
    REG_DTM              VARCHAR(14)   NOT NULL,
    CONSTRAINT pk_audit_logs_infm PRIMARY KEY (BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD)
);

-- Lookup index for the firm-banking-settings audit-read query
-- (BIZ_KEY + REF_NO + REF_NO1 + REF_NO2 + WSVC_ID + LANG_CD). The PK
-- already covers (BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD); the
-- audit-read filters by ref_no/ref_no1/ref_no2 instead of seq_no, so a
-- helper index keeps the query plan from full-scanning under load.
-- DEV has no such index today; this is a refactor-only addition that
-- can be promoted to DEV later if the audit-log feature owner agrees.
CREATE INDEX IF NOT EXISTS ix_audit_logs_infm_ref_lookup
    ON AUDIT_LOGS_INFM (BIZ_KEY, REF_NO, REF_NO1, REF_NO2, WSVC_ID, LANG_CD);

COMMENT ON TABLE AUDIT_LOGS_INFM IS
    'Shared audit log mirroring legacy JEX wecmsvndev.audit_logs_infm. Bootstrapped by the firm-banking-settings refactor (V63) so the audit-read endpoint has a target. Full ownership transfers to the cross-feature audit-log MR when authored (Phase 1 P-1). Writers append three rows per Create/Update (one per locale: EN/KO/local), gated by REQUIRES_NEW. wsvc_id stores the legacy JEX page id verbatim per OQ-6.';
