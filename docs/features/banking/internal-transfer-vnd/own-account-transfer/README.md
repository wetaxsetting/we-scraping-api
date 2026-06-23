# Banking → Own Account Transfer (Backend)

WeCMS menu: **Banking → Internal Transfer (Own Account Transfer)**. Move funds between the tenant's
own registered VND accounts, plus the read-only history of executed own-account transfers. (Distinct
from Payment → Internal Transfer, which dispatches to third-party beneficiaries.)

**Refactor slug:** `own-account-transfer` · **Route:** `/banking/own-account-transfer` ·
**JEX counterpart:** `trns_0001_001` (+ `trns_0001_007` composer, `trns_0002_001` history) — cited only as functional reference.
**Status:** ✅ Implemented full-stack (backend + tests + Next.js page). Legacy parity verified; B1/B9/B10 closed (see [refactor-gap-analysis.md](refactor-gap-analysis.md)).

> JEX trees (`WECMS DEV/`, `SOURCE_CODE_ADMIN/`, …) are **read-only reference**. These docs describe the
> **refactor** (Spring Boot) behaviour; the identically-slugged docs under [`docs/jex/features/own-account-transfer/`](../../../../../../docs/jex/features/own-account-transfer/README.md)
> describe the legacy live system. Same slug, two systems, never mixed.

---

## Documentation map (this folder)

This folder is the **primary documentation home** for the feature. The nine consolidation docs below
were authored 2026-06-05 from the shipped implementation; the two pre-existing canonical specs
(`own-account-transfer.md`, `transfer-history.md`) are preserved as the deep field-level reference and
cross-linked.

| Doc | Purpose |
|---|---|
| [README.md](README.md) | This index. |
| [business-flow.md](business-flow.md) | End-to-end user/business flow — picker → composer → engine scrape → result intake → balance refresh. |
| [functional-specification.md](functional-specification.md) | Screen functions, search/filters, grid, actions, statuses, validation, error handling. |
| [database-mapping.md](database-mapping.md) | Legacy column → entity field → DTO field → UI field mapping; tables read/written. |
| [interface-specification.md](interface-specification.md) | The 10 REST endpoints — method, URI, request/response DTOs, service method, category. |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend) with file:line citations. |
| [migration-guide.md](migration-guide.md) | JEX WSVC → Spring mapping, must-preserve parity, idempotency, SEED-CBC, deferred items. |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios — creation, approval, execution, balance/status inquiry, permissions. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity matrix (MATCH / PARTIAL / MISSING) + completion statement. |
| [own-account-transfer.md](legacy-spec.md) | **Canonical feature spec** (field-by-field, business rules, 12 resolved OQs). Preserved. |
| [transfer-history.md](../transfer-history/legacy-spec.md) | **Canonical companion spec** — read-only transfer-history list. Preserved. |

## Related reference docs (preserved in place, cross-linked — not moved)

| Layer | Location |
|---|---|
| API contract | [own-account-transfer-api.md](../../../../references/api-spec/banking/own-account-transfer-api.md) |
| DB schema | [own-account-transfer-db.md](../../../../references/database/banking/own-account-transfer-db.md) |
| Banking ERD | [banking-erd.md](../../../../references/database/erd/banking-erd.md) · ownership: [table-ownership.md](../../../../references/database/table-ownership.md) |
| JEX → Spring mapping | [own-account-transfer.md](../../../../references/migration/banking/own-account-transfer.md) · [transfer-history.md](../../../../references/migration/transfer-history.md) |
| Parity audits | [own-account-transfer-parity-2026-06-01.md](../../../../references/audits/own-account-transfer-parity-2026-06-01.md) · [current-implementation-audit](../../../../references/audits/own-account-transfer-current-implementation-audit.md) · [legacy-bank-business-rules](../../../../references/audits/own-account-transfer-legacy-bank-business-rules.md) · [transfer-history-audit-2026-05-20.md](../../../../references/audits/transfer-history-audit-2026-05-20.md) |
| Legacy (JEX) functional spec | [docs/jex/features/own-account-transfer/](../../../../../../docs/jex/features/own-account-transfer/README.md) |
| Frontend (Next.js) | [wecms-web/docs/features/banking/internal-transfer-vnd/own-account-transfer/](../../../../../../wecms-web/docs/features/banking/internal-transfer-vnd/own-account-transfer/README.md) |

## Open questions

- **OQ-A** (resolved, PO 2026-05-18) — scope is strictly own→own within the same `biz_key`, VND-only. See [own-account-transfer.md §3](legacy-spec.md).
- **OQ-FE-1** — composer credential-collection (saved bank-cert lookup, in-app password capture) deferred to the Phase-B composer MR. See [refactor-gap-analysis.md](refactor-gap-analysis.md).
- Feature-level B-register items (B5/B11/B14/B-LIVE) are tracked in [refactor-gap-analysis.md](refactor-gap-analysis.md) — none block release.
