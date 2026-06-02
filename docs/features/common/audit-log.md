# Common > Audit Log (Shared Writer) — Refactor Feature Spec (Backend)

**Domain:** `common` (cross-cutting infrastructure — no menu entry, no REST endpoint of its own)
**Refactor slug:** `audit-log`
**Backend package:** `com.kosign.wecms.common.auditlog`
**Status:** **Working** — shared writer used by multiple CRUD-with-audit features.
**Legacy reference:** writes `AUDIT_LOGS_INFM`; legacy detail-audit read query `comm_0220_003_r001_act.jsp:74-86`.

> Created 2026-06-01 (documentation audit round 2). Documents an **already-implemented** shared
> service that previously had no dedicated spec — its behaviour was only described inline in consumer
> feature docs and `wecms-api/CLAUDE.md`. No new scope, no source change.

## Overview

`AuditLogService` is the **single cross-feature writer** for the `AUDIT_LOGS_INFM` table. Every feature
that records an audited mutation (firm-banking-settings, contacts, chart-of-accounts, and future
features) calls `auditLogService.append(ctx)` rather than inserting audit rows itself. The service owns
the locale fan-out, seq_no allocation, and transaction semantics so consumers don't reimplement them.

It is **not** a user-facing feature: there is no controller and no REST endpoint. There is also no
refactor-side audit **read** API — the legacy JEX detail-audit query runs DEV-side only and is out of
scope for the local R&D refactor.

## Implementation

| Piece | File |
|---|---|
| Writer service | `common/auditlog/service/AuditLogService.java` |
| Event context (input) | `common/auditlog/service/AuditLogContext.java` |
| Entity | `common/auditlog/entity/AuditLogEntry.java` + `AuditLogEntryId.java` |
| Repositories | `common/auditlog/repository/AuditLogEntryRepository.java`, `AuditLogSeqNoRepository.java` |
| Schema | Flyway `V63` (`AUDIT_LOGS_INFM`), `V66` (`wb_get_next_val` `audit_logs_infm` branch) |

## Contract & semantics

- **Locale fan-out** — one `append(ctx)` writes **one row per supported locale**
  (`SUPPORTED_LOCALES = EN, KO, VI, KM`) for a single event. All rows share the same
  `audit_logs_seq_no`; the PK `(biz_key, audit_logs_seq_no, wsvc_id, lang_cd)` stays unique because
  `lang_cd` differs. Each row's activity/detail text is pre-resolved via `MessageResolver` so a reader
  sees localized strings without server-side re-translation.
- **Transaction** — `@Transactional(propagation = REQUIRES_NEW)`: the audit insert runs in its **own**
  transaction. If the caller's business tx rolls back, a completed audit row **still commits** (forensic
  record outlives the business outcome).
- **Never throws** — validation failures (missing tenant context / required field) and DB failures
  (seq_no race, outage) are caught internally, logged at WARN, and **swallowed**. A missed audit row is
  preferred over a stranded business write (Phase 1 §11.1 trade-off). Grep the `trace_id` to find gaps.
- **Tenant scoping** — `biz_key` / `reg_id` / `reg_nm` come from `TenantContext` (populated by the JWT
  filter). `reg_dtm` is server time (`yyyyMMddHHmmss`, UTC).
- **`WSVC_ID` is a slug** — consumers pass the refactor route slug (`firm-banking-settings`, `contacts`,
  `chart-of-accounts`, …), **never** a legacy WSVC id, per workspace CLAUDE.md.
- **seq_no allocation** — once per call via `AuditLogSeqNoRepository` → legacy `wb_get_next_val`
  (`MAX(seq_no)+1`, race-prone; accepted for parity, hardening to `nextval('seq')` is a future item).

## Consumers (current)

| Feature | Audit `WSVC_ID` slug | Spec |
|---|---|---|
| Firm Banking Settings | `firm-banking-settings` | [../settings/firm-banking-settings.md](../settings/firm-banking-settings.md) |
| Contacts | `contacts` | [../settings/contacts.md](../settings/contacts.md) |
| Chart of Accounts | `chart-of-accounts` | [../settings/chart-of-accounts.md](../settings/chart-of-accounts.md) |
| View Setting | `view-setting` | [view-setting.md](view-setting.md) |

> Approval Line and ERP Transaction History deliberately write **no** audit rows (legacy JEX parity —
> see those specs). Adding a new audited feature = call `append(ctx)` with the feature's slug; no change
> to this service.

## Related

- Security design (filter chain, audit envelope): [../../references/business-rules/security/README.md](../../references/business-rules/security/README.md)
- Shared security baseline: [../../../../docs/security/](../../../../docs/security/)
- Identifier rule (slug for `WSVC_ID`): workspace CLAUDE.md §"Cross-Tree Rules"
