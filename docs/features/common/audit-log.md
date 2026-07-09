# Common > Audit Log (Shared Writer) — Summary (Backend)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/common/audit-log/`

**Domain:** `common` (cross-cutting infrastructure — no menu entry, no REST endpoint of its own) · **Slug:** `audit-log`
**Backend package:** `com.kosign.wecms.common.auditlog`
**Status:** ✅ Implemented — shared writer used by multiple CRUD-with-audit features.

## Feature overview

`AuditLogService.append(AuditLogContext)` is the **single cross-feature writer** for `AUDIT_LOGS_INFM`.
Every audited mutation (firm-banking-settings, contacts, chart-of-accounts, login-credentials, …) calls it
rather than inserting audit rows itself. The service owns the locale fan-out, seq allocation, and transaction
semantics. **No controller, no REST endpoint** (write-path only).

## Contract (at a glance)

- **Locale fan-out** — one `append(ctx)` writes one row per supported locale (EN/KO/VI/KM), all sharing one
  `AUDIT_LOGS_SEQ_NO`; PK `(biz_key, audit_logs_seq_no, wsvc_id, lang_cd)` stays unique via `lang_cd`.
- **`@Transactional(REQUIRES_NEW)`** — audit commits even if the caller's business tx rolls back.
- **Never throws** — validation/DB failures caught, logged WARN, swallowed.
- **`WSVC_ID` is the refactor route slug** (`firm-banking-settings`, …), never a legacy WSVC id (override 2026-05-09).
- **seq allocation** via `LegacyFunctionRepository.nextAuditLogsSeqNo(...)` → `wb_get_next_val` (centralized in V140).

> **Verified names (correcting earlier drafts):** entity is `AuditLogsInfm` (+ `AuditLogsInfmId`), repository is
> `AuditLogEntryRepository`. There is **no** `AuditLogEntry` class and **no** `AuditLogSeqNoRepository` (the
> native-query seq repo was removed — seq now flows through the `LegacyFunctionRepository` funnel).

## Detailed documentation

| Doc | Scope |
|---|---|
| [audit-log/README.md](audit-log/README.md) | Folder hub + full document index |
| [audit-log/business-flow.md](audit-log/business-flow.md) | append flow + REQUIRES_NEW + fan-out |
| [audit-log/functional-specification.md](audit-log/functional-specification.md) | never-throws, slug rule, seq allocation |
| [audit-log/database-mapping.md](audit-log/database-mapping.md) | `AUDIT_LOGS_INFM` columns/PK + entity mapping |
| [audit-log/interface-specification.md](audit-log/interface-specification.md) | `append(AuditLogContext)` programmatic contract |
| [audit-log/migration-guide.md](audit-log/migration-guide.md) | legacy `AUDIT_LOGS_INFM` writes → shared writer |
| [audit-log/refactor-gap-analysis.md](audit-log/refactor-gap-analysis.md) | parity matrix |
| [audit-log/source-code-index.md](audit-log/source-code-index.md) | verified file map (corrected names) |
| [audit-log/test-scenarios.md](audit-log/test-scenarios.md) | fan-out, rollback-survival, swallow-on-failure |

## Related

- Frontend read-view twin: [`../../../../wecms-web/docs/features/common/audit-log/README.md`](../../../../wecms-web/docs/features/common/audit-log/README.md)
- Security design: [`../../references/business-rules/security/README.md`](../../references/business-rules/security/README.md) · shared baseline [`../../../../docs/security/`](../../../../docs/security/)
- Audit deliverables: [`../../audits/common-source-index.md`](../../audits/common-source-index.md) · [`common-gap-analysis.md`](../../audits/common-gap-analysis.md) · [`common-docs-audit.md`](../../audits/common-docs-audit.md)
