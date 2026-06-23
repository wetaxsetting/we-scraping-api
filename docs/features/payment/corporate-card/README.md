# Corporate Card — Payment Module (Refactor backend)

> **Companion frontend docs:** [`wecms-web/docs/features/payment/corporate-card/`](../../../../../wecms-web/docs/features/payment/corporate-card/README.md). Keep both trees in lockstep — feature slugs and sub-screen names must match for traceability.

The **Corporate Card** group of the Payment module exposes a single sub-module today: **Card Transactions** — the ledger of every card transaction scraped from the bank for the tenant's registered corporate cards, plus the full classify / link / approval / scrape surface around it.

| Field | Value |
|---|---|
| Module | `payment` (Payments domain), **Corporate Card** group |
| Sub-modules | **Card Transactions** (`card-transactions`) — the only one |
| REST root | `/api/v1/payment/card-transactions` |
| Class-level authz | `@PreAuthorize("@menuAuthz.canAccess('payment-corporate-card-transactions')")` (applies to every endpoint) |
| View Setting `WEB_SVC_ID` | `card-transactions` (two area variants: `table1` F/M, `table2` H) |
| Audit `AUDIT_LOGS_INFM.WSVC_ID` | `card-transactions` (slug — legacy JEX `card_0001_001` writes NONE) |
| Status | **Implemented full-stack** — backend (~30 endpoints + 5 services + 5 test files / 44 methods) and frontend (page + 16 components + 28-fn API client) both on disk |
| Legacy WSVC source | `card_0001_001` (ledger/classify), `card_0001_002` (link), `card_0001_003` (card mgmt), `comm_0218_007` (scrape register), `comm_0194/0195_001` (approval), `prch_0011_001` (receipt), `nxisas_cardTranList[_r001]` (scrape) |

> **Status banner.** This sub-module is **Implemented full-stack**, NOT "design phase", "docs only", "Sprint-2 list-only", or "not started". The earlier list-only framing is retired. See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the gap vocabulary and per-gap status.

## Sub-module map

| Sub-module | Slug | Route (WEB) | REST base | Status |
|---|---|---|---|---|
| **Card Transactions** | `card-transactions` | `/payment/corporate-card/card-transactions` | `/api/v1/payment/card-transactions` | Implemented full-stack |

## Endpoint summary (~30 endpoints)

Full contract in [interface-specification.md](interface-specification.md). Grouped:

| Group | Count | Endpoints |
|---|---|---|
| Ledger + classify | 6 | `GET /` · `GET /cards` · `PATCH /{cardSeqNo}` · `PUT /{cardSeqNo}/reporting-account` · `DELETE /{cardSeqNo}/reporting-account` · `POST /export` |
| Card Management | 5 | `GET /cards/management` · `PATCH /cards/{bankCd}/{cardNo}/active` · `PATCH /cards/{bankCd}/{cardNo}` · `DELETE /cards/{bankCd}/{cardNo}` · `POST /cards` |
| Update Cards — Coocon scrape | 6 | `POST /sync` (@Deprecated) · `POST /sync-session` · `POST /sync-session/{sessionId}/{qna,otp,captcha}` · `POST /period-limits` |
| Request Approval + management | 5 | `POST /request-approval` (multipart) · `GET /next-doc-no` · `GET /by-approval/{apprSeqNo}` · `DELETE /by-approval/{apprSeqNo}` · `PUT /by-approval/{apprSeqNo}/approval-line` |
| Link + receipts + memos | 8 | `GET /links/e-invoices` · `GET /links/receipts` · `POST /links` · `DELETE /links/{cardSeqNo}` · `POST /receipts` · `GET /receipts/{docId}` · `GET /purpose-options` · `GET /memos` + `POST /memos` + `DELETE /memos/{seqNo}` |

> ~30 distinct handlers on `CardTransactionsController` (the memo trio collapses three verbs onto `/memos`). Counts mirror the live controller.

## Document set

| Doc | Purpose |
|---|---|
| [README.md](README.md) | This file — banner, overview, sub-module map, endpoint summary |
| [business-flow.md](business-flow.md) | Legacy / Spring / React end-to-end flow for every operation |
| [database-mapping.md](database-mapping.md) | Entity ↔ table ↔ column, PK/FK, key-gen, migrations |
| [functional-specification.md](functional-specification.md) | Filters, totals, list/detail, columns, validation, rules |
| [interface-specification.md](interface-specification.md) | ~30 endpoints — URL, method, authz, DTO fields, errors |
| [migration-guide.md](migration-guide.md) | Legacy WSVC → controller/service; approved deviations; dead legacy |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Gap vocabulary; deviations; PO/dev decisions |
| [source-code-index.md](source-code-index.md) | Controller mappings + services + entity + DTOs + FE + tests |
| [test-scenarios.md](test-scenarios.md) | BE test files; FE missing |

## Status callout

- **Authz live** — class-level `@PreAuthorize("@menuAuthz.canAccess('payment-corporate-card-transactions')")` on `CardTransactionsController`. Not a role gate; F-3 menu-authz layer (any tenant user whose product tree grants the menu).
- **Wire row JSON** — the ledger row does NOT carry invented fields (`card_trns_usage_nm`, `chart_acct_nm`/`_en`/`_level_nm`, `appr_dv`, `post_seq_no`, `post_reg_id`). `approvers[]` is always empty on ledger rows. Real extra fields are `using_type` / `auth_code` / `city` / `note`. Display names resolved client-side (APPROVED DEVIATION). See [interface-specification.md](interface-specification.md) §"Ledger row shape".
