# Exchange Rate Inquiry — Backend (wecms-api)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

> **Audience: backend.** For the Next.js screen design see the WEB counterpart:
> [`wecms-web/docs/features/payment/exchange-rate-inquiry/README.md`](../../../../../wecms-web/docs/features/payment/exchange-rate-inquiry/README.md).
> These folder docs are the **detailed source of truth**; the root summary
> [`../exchange-rate-inquiry.md`](../exchange-rate-inquiry.md) is a high-level pointer.

**Feature slug:** `exchange-rate-inquiry`
**Menu / authz slug:** `payment-overseas-exchange`
**Module:** Payment → **Overseas Payment** (top-menu group)
**System:** Refactor (Spring Boot + Next.js)
**API base:** `/api/v1/banking/exchange-rates`
**Controller:** `com.kosign.wecms.domain.banking.controller.ExchangeRateController` — lives in the **`domain.banking`** package, *not* `domain.payment`, despite the Payment menu placement.

---

## 1. Overview

Exchange Rate Inquiry shows the foreign-currency rates published by the tenant's connected
banks (Shinhan + Woori via the Coocon / NXISAS smart-gateway). The page renders a
currency-matrix table — one row per counter-currency, four rate columns (Cash Buy, Cash
Sell, T/T Buy, T/T Sell) — with an optional side-by-side comparison against a second bank
and an "Inquired on" timestamp footer.

Rates are written by a daily batch (legacy `EXCH_0001_001` / `EXCH_0010_001` /
`EXCH_0020_001`, refactored into `ExchangeRateBatchService`) and read on demand by the
`GET /api/v1/banking/exchange-rates` endpoint. The displayed rates are as current as the
last successful batch round.

**This feature belongs to the Overseas Payment menu group.** It sits alongside the
Overseas Payment request / result screens (see
[`../overseas-payment/README.md`](../overseas-payment/README.md), the FX consumer) under
the same top menu, but its backend is implemented in the banking domain because the rate
data lives in `BANK_EXCH_RATE_INFM` and is collected by the banking-batch / NXISAS
gateway path.

## 2. Legacy → Refactor mapping (at a glance)

| Concern | Legacy JEX | Refactor |
|---|---|---|
| Menu / WSVC id | `pymn_0010_002` | slug `payment-overseas-exchange` (authz) / `exchange-rate-inquiry` (feature) |
| Read path | IDO `BANK_EXCH_RATE_INFM_R002` | `ExchangeRateService.list` → `BankExchRateInfmRepository` (DISTINCT ON native queries) |
| Write path | batch `EXCH_0001_001` / `EXCH_0010_001` / `EXCH_0020_001` (Coocon / Shinhan + Woori) | `ExchangeRateBatchService` + `ExchangeRateBatchScheduler` (gated off by default) |
| Table | `BANK_EXCH_RATE_INFM` | `BANK_EXCH_RATE_INFM` (same — V127 schema, V130 seed) |

## 3. Endpoint summary

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/banking/exchange-rates` | List rate rows for the tenant (filters: `ref_date`, `bank_cd`, `seq_no`, `sort_field`, `sort_direction`) + available rounds |
| GET | `/api/v1/banking/exchange-rates/banks` | Bank codes that have rate data (drives the bank dropdown) |
| GET | `/api/v1/banking/exchange-rates/currencies` | All distinct display-currency codes for the tenant |

All three are gated by `@PreAuthorize("@menuAuthz.canAccess('payment-overseas-exchange')")`
at the controller level and require a Bearer JWT. Full contract in
[`interface-specification.md`](interface-specification.md).

## 4. Status

| Layer | Status | Notes |
|---|---|---|
| Controller | ✅ Done | `ExchangeRateController` — `@Validated` + `@PreAuthorize`, 3 endpoints |
| Service (read) | ✅ Done | `ExchangeRateService.list/listBanks/listCurrencies` |
| Service (batch write) | ✅ Done | `ExchangeRateBatchService` (3 legacy jobs + on-demand catch-up); scheduler `ExchangeRateBatchScheduler` is gated off by default |
| Repository | ✅ Done | `BankExchRateInfmRepository` (DISTINCT ON + round + seq-no queries) |
| Entity | ✅ Done | `BankExchRateInfm` + `BankExchRateInfmId` |
| DTOs | ✅ Done | `ExchangeRateListResponse` · `ExchangeRateRow` · `ExchangeRateBankItem` · `ExchangeRateRoundItem` |
| Sort whitelist | ✅ Done | `ExchangeRateSortField` (CURRENCY / CASH_BUY / CASH_SELL / TT_BUY / TT_SELL) |
| Input validation | ✅ RESOLVED | `@Pattern`/`@Size` on `ref_date` / `bank_cd` / `seq_no` |
| Timezone (`resolveDate`) | ✅ RESOLVED | Uses `Asia/Ho_Chi_Minh` (UTC+7) — earlier "JVM default TZ bug" no longer applies |
| Seq-no preference | ✅ RESOLVED | `seq_no` param ("1"/"2"; omit → latest via DISTINCT ON) |
| Backend-driven bank list | ✅ RESOLVED | `/banks` endpoint |
| Backend-driven currency list | ✅ RESOLVED | `/currencies` endpoint |
| OpenAPI annotations | ⚠️ APPROVED DEVIATION | No `@Tag`/`@Operation` — Springdoc not wired project-wide (intentional) |
| Backend unit tests | ✅ Done | `ExchangeRateServiceTest`, `ExchangeRateBatchServiceTest`, `ExchangeRateBatchPersistenceTest`, `ExchangeRateBatchSchedulerTest` |

## 5. Related docs

- [business-flow.md](business-flow.md) — Legacy / Spring / React end-to-end flow
- [database-mapping.md](database-mapping.md) — `BANK_EXCH_RATE_INFM` entity ↔ table ↔ column
- [functional-specification.md](functional-specification.md) — filters, selectors, compare, sort, rules
- [interface-specification.md](interface-specification.md) — endpoints, DTOs, validation, errors
- [migration-guide.md](migration-guide.md) — JEX → Spring mapping + resolved gaps + approved deviations
- [refactor-gap-analysis.md](refactor-gap-analysis.md) — gap vocabulary, B-4 approved deviation, ⚠️ re-verify items
- [source-code-index.md](source-code-index.md) — every backend + frontend file with line citations
- [test-scenarios.md](test-scenarios.md) — BE service + batch tests, FE gap

## 6. Cross-links — the eight other backend payment features

| Feature | Doc |
|---|---|
| Overseas Payment (FX consumer) | [`../overseas-payment/README.md`](../overseas-payment/README.md) |
| Approval Box | [`../approval-box/README.md`](../approval-box/README.md) |
| Internal Transfer | [`../internal-transfer/`](../internal-transfer/) |
| Vendor Payment | [`../vendor-payment/`](../vendor-payment/) |
| Expense Payment | [`../expense-payment/`](../expense-payment/) |
| Payroll Payment | [`../payroll/`](../payroll/) |
| Card Transactions | [`../card-transactions.md`](../card-transactions.md) |
| Payment source index | [`../payment-source-index.md`](../../../audits/modules/payment-source-index.md) |
| Payment gap analysis | [`../payment-gap-analysis.md`](../../../audits/modules/payment-gap-analysis.md) |
