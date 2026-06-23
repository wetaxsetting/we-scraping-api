# Exchange Rate Inquiry — Backend (high-level summary)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/payment/exchange-rate-inquiry/`

Foreign-exchange rate lookup under the **Payment → Overseas Payment** menu group. A scheduled Coocon/Shinhan batch (`EXCH_0001_001`) stores daily rates in `BANK_EXCH_RATE_INFM`; the page reads that snapshot on demand. The user picks bank / reference date / notification round (`seq_no`) and views a currency-matrix table, optionally comparing a second bank side-by-side.

| Field | Value |
|---|---|
| Slug | `exchange-rate-inquiry` (menu group: Overseas Payment) |
| REST base | `/api/v1/banking/exchange-rates` (controller `domain.banking.ExchangeRateController` — NOT under `domain/payment`) |
| Frontend route | `/payment/overseas-payment/exchange-rate-inquiry` |
| Menu authz | `@menuAuthz.canAccess('payment-overseas-exchange')` |
| JEX counterpart | read = IDO `BANK_EXCH_RATE_INFM_R002`; write = batch `EXCH_0001_001` |
| Status | ✅ Implemented full-stack — validation, sort, backend-driven bank/currency lists, seq_no preference, compare-bank all wired |

## Detailed docs (source of truth) — `exchange-rate-inquiry/`

| Doc | Focus |
|---|---|
| [exchange-rate-inquiry/README.md](exchange-rate-inquiry/README.md) | Overview, endpoint summary, doc index |
| [exchange-rate-inquiry/business-flow.md](exchange-rate-inquiry/business-flow.md) | Rate fetch/inquiry, bank compare, batch-write context |
| [exchange-rate-inquiry/functional-specification.md](exchange-rate-inquiry/functional-specification.md) | Filters, selectors, compare-bank, sort, validation |
| [exchange-rate-inquiry/database-mapping.md](exchange-rate-inquiry/database-mapping.md) | `BANK_EXCH_RATE_INFM`, keys, seq_no round-probe exception |
| [exchange-rate-inquiry/interface-specification.md](exchange-rate-inquiry/interface-specification.md) | `/exchange-rates`, `/banks`, `/currencies` contracts |
| [exchange-rate-inquiry/source-code-index.md](exchange-rate-inquiry/source-code-index.md) | Controller + service + batch + entity + FE files + tests |
| [exchange-rate-inquiry/migration-guide.md](exchange-rate-inquiry/migration-guide.md) | `pymn_0010_002` + `BANK_EXCH_RATE_INFM_R002` + `EXCH_0001_001` → Spring |
| [exchange-rate-inquiry/refactor-gap-analysis.md](exchange-rate-inquiry/refactor-gap-analysis.md) | Gap matrix (B-4 OpenAPI = APPROVED DEVIATION; ⚠️ re-verify items) |
| [exchange-rate-inquiry/test-scenarios.md](exchange-rate-inquiry/test-scenarios.md) | BE service + batch tests; FE coverage gap |

Related: [overseas-payment/README.md](overseas-payment/README.md) (FX consumer). Frontend counterpart: [`wecms-web/docs/features/payment/exchange-rate-inquiry/`](../../../../wecms-web/docs/features/payment/exchange-rate-inquiry/README.md).
