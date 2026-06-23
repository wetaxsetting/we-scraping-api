# Approval Box — Backend (high-level summary)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/payment/approval-box/`

Centralized approval inbox consumed by every Payment write flow (internal-transfer / vendor / expense / payroll / overseas). Lists pending items with approval status, requestor, approver chain, and scheduled date; supports approve / reject / withdraw / change-approval-line and bulk-approve, plus a detail popup (transactions, attachments, download). Notifications are written to the legacy **BLTB_*** tables via `ApprovalNotificationService` (the refactor-only `NOTIFICATION` table was removed 2026-06-10).

| Field | Value |
|---|---|
| Slug | `approval-box` |
| REST root | `/api/v1/payment/approval-box` (12 mappings) |
| Frontend route | `/payment/approval-box` |
| JEX counterpart | `resl_0001_001` (centralized approval inbox) |
| Status | ✅ Implemented full-stack — read + mutation + export; legacy parity verified; SIT/UAT done |

## Detailed docs (source of truth)

| Doc | Focus |
|---|---|
| [approval-box/README.md](approval-box/README.md) | Feature overview, endpoints, doc index |
| [approval-box/business-flow.md](approval-box/business-flow.md) | Request → search → detail → approve/reject → status → persistence → refresh |
| [approval-box/functional-specification.md](approval-box/functional-specification.md) | Scope, status model, filters, validation, error codes |
| [approval-box/database-mapping.md](approval-box/database-mapping.md) | Tables, columns, keys, Flyway, domain codes |
| [approval-box/interface-specification.md](approval-box/interface-specification.md) | REST contracts (all 12 endpoints incl. 4 popup endpoints) |
| [approval-box/source-code-index.md](approval-box/source-code-index.md) | Every backend + frontend source file |
| [approval-box/migration-guide.md](approval-box/migration-guide.md) | JEX `resl` → Spring mapping |
| [approval-box/test-scenarios.md](approval-box/test-scenarios.md) | Automated + manual + SIT/UAT scenarios |
| [approval-box/refactor-gap-analysis.md](approval-box/refactor-gap-analysis.md) | MATCH/APPROVED DEVIATION/DEAD LEGACY/PARTIAL/MISSING matrix |

Frontend counterpart: [`wecms-web/docs/features/payment/approval-box/`](../../../../wecms-web/docs/features/payment/approval-box/README.md).
