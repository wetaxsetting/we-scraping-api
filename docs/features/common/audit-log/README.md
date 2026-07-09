# Common → audit-log (Backend)

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

Shared cross-feature **write-path** for the legacy `AUDIT_LOGS_INFM` table. One programmatic
entry point — `AuditLogService.append(AuditLogContext)` — owns the locale fan-out, seq-no
allocation, and `REQUIRES_NEW` transaction semantics so no consumer reimplements them.

> **Module shape: API-write / WEB-read-only.** There is **no controller and no REST endpoint**
> on this module. Audit *rows* are surfaced by per-feature read endpoints in their own domains
> (e.g. Login Credentials `/audit-logs`); there is no generic Common audit READ API. The WEB
> side is a single generic read dialog — see the WEB twin.

## Documents index

| Doc | Purpose |
|---|---|
| [business-flow.md](business-flow.md) | The append flow end-to-end: validation → seq alloc → locale fan-out → save, all inside `REQUIRES_NEW`. |
| [functional-specification.md](functional-specification.md) | The behavioural contract: never-throws, slug `WSVC_ID`, one row per locale, seq sharing. |
| [database-mapping.md](database-mapping.md) | `AUDIT_LOGS_INFM` columns + PK + entity mapping + seq allocation via the funnel. |
| [interface-specification.md](interface-specification.md) | No REST endpoint — the `append(AuditLogContext)` programmatic contract + the per-feature read endpoints that surface rows. |
| [migration-guide.md](migration-guide.md) | Legacy `AUDIT_LOGS_INFM` writes / `comm_0220_003` read query → shared writer; the `WSVC_ID` slug override. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING against legacy JEX. |
| [source-code-index.md](source-code-index.md) | Corrected file map (`AuditLogsInfm`, not `AuditLogEntry`; no `AuditLogSeqNoRepository`). |
| [test-scenarios.md](test-scenarios.md) | Fan-out, `REQUIRES_NEW` annotation pin, swallow-on-failure coverage. |

## Related links

- Common module hub — [../README.md](../README.md)
- WEB twin (read dialog) — [../../../../../wecms-web/docs/features/common/audit-log/README.md](../../../../../wecms-web/docs/features/common/audit-log/README.md)
- Common audit deliverables — [../../../audits/common-source-index.md](../../../audits/common-source-index.md) · [../../../audits/common-gap-analysis.md](../../../audits/common-gap-analysis.md) · [../../../audits/common-docs-audit.md](../../../audits/common-docs-audit.md)

## At a glance (verified)

| Aspect | Value |
|---|---|
| Package | `com.kosign.wecms.common.auditlog` |
| Writer | `AuditLogService.append(AuditLogContext)` — `@Transactional(REQUIRES_NEW)`, never throws |
| Entity | `AuditLogsInfm` (+ `AuditLogsInfmId`) → `@Table AUDIT_LOGS_INFM` |
| Repository | `AuditLogEntryRepository extends JpaRepository<AuditLogsInfm, AuditLogsInfmId>` |
| Seq allocation | `LegacyFunctionRepository.nextAuditLogsSeqNo(...)` → `wb_get_next_val` (V140-centralized) |
| Supported locales | `EN, KO, VI, KM` — one row per locale, all sharing one `AUDIT_LOGS_SEQ_NO` |
| `WSVC_ID` | refactor route **slug** (e.g. `firm-banking-settings`), never a legacy WSVC id |
| Consumers (verified) | FirmBankingSettingsService, ContactsService, ChartOfAccountsService, LoginCredentialsService, + others |
| Non-consumers (parity) | Approval Line, ERP Transaction History — deliberately write **no** audit rows |
