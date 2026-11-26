# WeInvoice Provider for Cash-Register Invoices — Test Result

**Date:** 2026-11-26
**Type:** TEST RESULT
**Status:** PASS
**Repository:** we-scraping-api
**Related Record:** [WeInvoice — "Phát hành qua" Empty for Cash-Register Invoices — Fix](../../bugs/weinvoice-provider-empty-for-cash-register-invoices-fix-2026-11-26.md)

## Scope

`ProviderSql` (effective provider), list / detail / PDF-pack SQL, `needs_lookup_code` kept on the provider written on the invoice.

## Environment

LOCAL (Windows, JDK 21 from `~/.gradle/jdks`, `--offline`). SQL run read-only against the shared engine DB.

## Code / Build Under Test

Branch: `sit` · Base commit: `c175843` · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `ProviderSqlTest` | PASS | 3 tests (order of sources, adapter map incl. `('ipos', '0104789847')`, CTE bound to `:mst`) |
| `InvoiceRowLookupMappingTest` | PASS | 5 tests, incl. `inferredProviderIsShownButNeverAsksForACode` |
| `InvoiceListServerPagingTest`, `ProviderLookupTest`, `InvoiceFileServiceTest` | PASS | 4 / 2 / 20 tests |
| `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` | PASS | 236 tests |
| `./gradlew test --offline` | PASS except 1 pre-existing | 3,037 run, 1 failed, 21 skipped |

## Regression Tests

Did a regression test cover the original defect? **YES (SQL contract)** — `ProviderSqlTest` pins that the effective provider
falls back past the XML header; `InvoiceRowLookupMappingTest.inferredProviderIsShownButNeverAsksForACode` pins that an
inferred provider fills `provider_tax_id` without setting `needs_lookup_code`. The values themselves are a DB property
(no embedded DB in this repo) → verified read-only below.

## Database Verification (read-only)

| Check | Expected | Actual | Result |
|---|---|---|---|
| List, 1201496252, from 2023-01-01 | more rows with a provider, no slowdown | 7,619 → 9,582 of 10,450; 0.16 s | PASS |
| List, 0318793907 | same | 3,884 → 64,174 of 64,189; 0.22 s | PASS |
| Detail 171 (1C26MYY-5509, PDF `email:ipos`) | 0104789847 | 0104789847 | PASS |
| Detail 162 / 644 / 645 / 1199 | MISA / WeTax / WeTax / EasyInvoice | 0101243150 / 1201496252 / 1201496252 / 0105987432 | PASS |
| PDF sources still unmapped | only sources without a provider | `email` 234, `express247` 8 | PASS |

## Existing Failures

`VatDeclarationLifecycleTest > create — required fields > sync accepts a period whose adjustments exceed its revenue` — fails
on the unmodified baseline (recorded 2026-11-25).

## New Failures

None.

## Not Tested

UI visual check and SIT: NOT RUN — not deployed.

## Final Result

PASS

## Recommendation

READY FOR MANUAL COMMIT
