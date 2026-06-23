# Card Transactions — Backend (high-level summary)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/payment/corporate-card/`

The Payment-module ledger of every card transaction scraped from the bank for the tenant's registered corporate cards. The page reads the populated `CARD_TRNS_HSTR` rows and exposes read + classify (Chart of Accounts) + inline-edit (reporting account / purpose / description) + **Card Management** (list/activate/rename/delete/bulk-create) + **Update Cards** (Coocon scrape) + **Request Approval** (multipart, `EXPT_RESOL_*` fan-out, `APPR_DV='33'`) + approval withdraw/change-line + **Link Card Payments** (e-invoice + receipt) + Memo CRUD + Excel export + Print. Scraping itself stays on the JEX side (coexistence carve-out).

| Field | Value |
|---|---|
| Slug | `card-transactions` (module group: Corporate Card) |
| REST root | `/api/v1/payment/card-transactions` (~30 mappings) |
| Frontend route | `/payment/corporate-card/card-transactions` |
| Menu authz | `@menuAuthz.canAccess('payment-corporate-card-transactions')` (class-level, live) |
| Audit `WSVC_ID` | `card-transactions` (legacy JEX `card_0001_001` writes none) |
| Status | ✅ Implemented full-stack — ~30 endpoints + 5 services + 44 tests; FE page + 16 components + 28-fn client |

## Detailed docs (source of truth) — `corporate-card/`

| Doc | Focus |
|---|---|
| [corporate-card/README.md](corporate-card/README.md) | Overview, endpoint summary, doc index |
| [corporate-card/business-flow.md](corporate-card/business-flow.md) | Ledger read → classify → link → approve; scrape/sync; memo |
| [corporate-card/functional-specification.md](corporate-card/functional-specification.md) | Filters, summary cards, list/detail, update/sync, validation |
| [corporate-card/database-mapping.md](corporate-card/database-mapping.md) | `CARD_MSTR` + transaction/memo/linked-doc tables, keys, key-gen |
| [corporate-card/interface-specification.md](corporate-card/interface-specification.md) | ~30 REST contracts (sync, period-limit, patch, create-cards, …) |
| [corporate-card/source-code-index.md](corporate-card/source-code-index.md) | Controller + 5 services + entities + DTOs + FE files + tests |
| [corporate-card/migration-guide.md](corporate-card/migration-guide.md) | JEX `card_0001_*` → Spring mapping |
| [corporate-card/refactor-gap-analysis.md](corporate-card/refactor-gap-analysis.md) | Gap matrix (incl. MEMO_INFM baseline + PRCH_STTS PO decisions) |
| [corporate-card/test-scenarios.md](corporate-card/test-scenarios.md) | BE 5 test files / 44 methods + FE coverage gap |

Frontend counterpart: [`wecms-web/docs/features/payment/corporate-card/`](../../../../wecms-web/docs/features/payment/corporate-card/README.md).
