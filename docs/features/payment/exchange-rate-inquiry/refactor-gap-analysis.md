# Exchange Rate Inquiry — Refactor Gap Analysis

Gap register against legacy JEX parity + project conventions.

## Gap vocabulary

| Tag | Meaning |
|---|---|
| **RESOLVED** | implemented and verified against the current code |
| **APPROVED DEVIATION** | intentional difference from legacy / convention, signed off |
| **uncertain-needs-confirmation (⚠️ re-verify)** | code suggests done, but should be confirmed before closing |
| **MISSING** | not implemented; work item |
| **MATCH** | refactor behaviour already matches legacy |

---

## Backend

| ID | Item | Status | Evidence |
|---|---|---|---|
| B-1 | Input validation (`ref_date`/`bank_cd`/`seq_no`) | **RESOLVED** | `@Validated` + `@Pattern`/`@Size` on `ExchangeRateController.list` |
| B-2 | Timezone — `resolveDate` anchor | **⚠️ re-verify** (code = `Asia/Ho_Chi_Minh`) | `ExchangeRateService` `VIETNAM_TZ` (line 43) + `resolveDate` (line 205) use UTC+7. Register still flags as "verify"; code suggests resolved. Confirm no alternate date path uses JVM default TZ. |
| B-3 | Seq-no preference / latest round | **RESOLVED** | `seq_no` param + `findLatestPerCurrencyBy...` DISTINCT ON (`CAST(seq_no AS integer) DESC`) |
| B-4 | OpenAPI / Springdoc annotations absent | **APPROVED DEVIATION** | No `@Tag`/`@Operation`; Springdoc not wired project-wide (controller Javadoc states this). Backend follow-up only if Springdoc is enabled. |
| B-5 | Audit logging on read endpoints | **MATCH** (exempt) | Read-only inquiry; project audit is write-path only. Controller Javadoc documents the exemption. No action. |
| B-6 | Backend-driven bank list | **RESOLVED** | `GET /exchange-rates/banks` → `listBanks` |
| B-7 | Backend-driven currency list | **RESOLVED** | `GET /exchange-rates/currencies` → `listCurrencies` |
| B-8 | Sort | **RESOLVED** | `sort_field`/`sort_direction` + `ExchangeRateSortField` whitelist, in-memory `applySort`, nulls-last |
| B-9 | Authorization | **RESOLVED** | `@PreAuthorize("@menuAuthz.canAccess('payment-overseas-exchange')")` |
| B-10 | Batch write path | **RESOLVED** | `ExchangeRateBatchService` (3 jobs + on-demand) + `ExchangeRateBatchScheduler` (gated off) |

## Frontend (summary — full detail in WEB folder)

| ID | Item | Status | Evidence |
|---|---|---|---|
| F-1/F-2 | Mapper at the boundary (no in-component snake_case) | **⚠️ re-verify** (code = resolved) | `lib/api/mappers.ts → toExchangeRateListResult`; component consumes camelCase. Confirm no stray snake_case reads. |
| F-3 | `formatRate` location | **⚠️ re-verify** (code = resolved) | `lib/currency.ts → formatExchangeRate` (imported, not page-local) |
| F-4 | Date wiring | **RESOLVED** | shared `<DateField>`; `refDate` sent to the API |
| F-5 | Error dialog on failure | **⚠️ re-verify** (code = resolved) | `useDialog().open({ type: "error" })` on `apiError`; no mock fallback |
| F-6 | Backend-driven bank list | **RESOLVED** | `fetchExchangeRateBanks()`; static fallback only |
| F-7 | Bank dropdown UX | **RESOLVED** | real popover dropdown (not cycle toggle) |
| F-8 | Compare-bank | **RESOLVED** | second `fetchExchangeRates` → 13-column matrix, same round |
| F-9 | Frontend tests | **MISSING** | no `*.test.*` for the exchange-rate / overseas screens (gap-analysis G-OVRS-4) |

## Open items & owners

| Item | Status | Owner |
|---|---|---|
| B-4 OpenAPI annotations | APPROVED DEVIATION — no action unless Springdoc is wired product-wide | PO / dev |
| B-2 timezone, FE mapper/formatRate/error-dialog | ⚠️ re-verify against live build (code reads as resolved) | dev |
| Frontend tests for exchange-rate / overseas screens | MISSING | dev (test) |
| Woori `EXCH_0030_001` fixed-1..15 catch-up vs MAX+1 (`runWoori` G2) | open decision — MAX+1 ported; fixed-range catch-up needs PO/ops confirmation of which job PROD runs | PO / ops |

## What is NOT a gap

- No JEX view service for the read path — the refactor uses a REST endpoint by design.
- Most-recent-date fallback + on-demand catch-up — refactor-only conveniences, documented.
- Batch scheduling off by default — coexistence rule (legacy batch owns collection).
- V130 seed values KRW-denominated for a fixed historical date — demo data only; not a
  production contract.
