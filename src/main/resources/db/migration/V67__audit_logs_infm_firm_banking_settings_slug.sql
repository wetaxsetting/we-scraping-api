-- ============================================================================
-- V67__audit_logs_infm_firm_banking_settings_slug.sql
--
-- Override of OQ-6 (2026-05-09): firm-banking-settings audit rows now
-- use the refactor route slug `firm-banking-settings` for
-- `audit_logs_infm.wsvc_id` instead of the legacy JEX page id
-- `comm_0220_001`.
--
-- Rationale (decision recorded 2026-05-09):
--   The refactor architecture standardises on slug-based identifiers
--   for routes, REST URIs, Java packages, AND the audit DB column.
--   Legacy WSVC ids stay confined to JEX file:line citations and
--   migration-traceability prose — every other surface, including
--   `audit_logs_infm.wsvc_id`, uses the modern slug.
--
-- Schema widening:
--   V63 created `audit_logs_infm.wsvc_id` as VARCHAR(20) — sized for
--   legacy JEX page ids (e.g. `comm_0220_001`, 13 chars). Refactor
--   slugs are longer (`firm-banking-settings` = 21 chars; future
--   feature slugs can run further still — e.g. `banking-account-
--   inquiry-vnd` = 27 chars). This migration widens the column to
--   VARCHAR(50) BEFORE running the back-fill UPDATE, otherwise the
--   UPDATE fails with `value too long for type character varying(20)`.
--   VARCHAR(50) gives every reasonable refactor slug headroom while
--   still fitting comfortably alongside the rest of the audit-log
--   columns. The matching JPA `@Column(length = 50)` on
--   `AuditLogEntry.wsvcId` lands in the same MR.
--
-- Compatibility impact (local-only, non-production):
--   * The legacy JEX detail-audit query at
--     `comm_0220_003_r001_act.jsp:74-86` filters by
--     `WSVC_ID = 'comm_0220_001'`. After this migration, refactor-
--     written audit rows for firm-banking-settings carry
--     `wsvc_id = 'firm-banking-settings'` and will NOT be returned by
--     the legacy JEX query. JEX is read-only reference (no DEV/UAT/
--     PROD deploy of the refactor); this is a local-only DB and the
--     mismatch is intentional — no JEX runtime ever queries this DB.
--   * The DEV `wecmsvndev.audit_logs_infm` (~1059 rows for this
--     ref_no) is unaffected — DEV is read-only via the postgres MCP;
--     this migration only touches the local R&D DB.
--   * The refactor's audit-read endpoint
--     (`FirmBankingSettingsService.auditLog`) is updated in the same
--     MR to filter by the new value; the audit history rendered in
--     the refactor's Details dialog stays consistent.
--
-- Scope:
--   Only firm-banking-settings audit rows are migrated
--   (ref_no = 'TB_FIRM_CONF'). Other features that still use the
--   legacy bucket (none today) would each ship their own migration
--   when adopting slug-based audit ids. The pattern this migration
--   establishes — one feature, one slug, one targeted UPDATE — is
--   the recommended template.
--
-- Idempotent: ALTER COLUMN to the same target type is a no-op; the
-- back-fill UPDATE WHERE clause only selects rows still carrying the
-- legacy value, so a re-run touches zero rows.
-- ============================================================================

ALTER TABLE audit_logs_infm
    ALTER COLUMN wsvc_id TYPE VARCHAR(50);

UPDATE audit_logs_infm
   SET wsvc_id = 'firm-banking-settings'
 WHERE wsvc_id = 'comm_0220_001'
   AND ref_no  = 'TB_FIRM_CONF';

-- Document the column-level rule on the table itself so a developer
-- inspecting `audit_logs_infm` rows in DBeaver / pgAdmin sees the
-- guidance without hunting through the docs tree.
COMMENT ON COLUMN audit_logs_infm.wsvc_id IS
    'Feature-scope identifier — refactor slug (e.g. firm-banking-settings) NOT the legacy JEX WSVC id. Convention adopted 2026-05-09 (overrides OQ-6 default). Each feature picks its own slug; legacy WSVC ids stay confined to JEX citations + migration-traceability prose. Widened from VARCHAR(20) to VARCHAR(50) in V67 to accommodate slug lengths. See docs/refactor-wecms/features/<slug>.md §11.1 + workspace CLAUDE.md §"Refactor identifier naming".';
