# e-Invoice Scrape Currency Clamp — Test Result

**Date:** 2026-08-24
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api
**Related Record:** [e-Invoice Scrape — Missing Legacy Currency Resolution](../../bugs/einvoice-scrape-currency-legacy-clamp-2026-08-24.md)
**Issue:** —

## Scope

Verifies the restored legacy K / POS / C currency resolution on
`BUSA_DOC_MSTR.TRCO_TRSC_CCY_CD`:

- `EInvoiceDetailScrapeService.resolveMasterCcy(...)` (new) — phase-2 detail scrape.
- `EInvoiceScrapeService` line 217 — phase-1 list upsert routed through the same resolution.

Closes both reported symptoms: the `WBE_DB` / `busa_doc_mstr_chk07` crash on
`POST /api/v1/addon/einvoices/scrape-detail-results`, and the `Total ()` (NULL currency)
bucket in the e-Invoices footer.

## Environment

- **LOCAL** — Gradle unit tests (JUnit 5 + AssertJ), Temurin JDK 21.
- **Refactor dev DB `172.168.5.172/wecmsdevdb`** — read-only queries only (evidence + backfill
  predicate validation). No DML executed.
- **Live JEX DEV DB `10.254.242.254:54311/wecmsvndevdb`** — read-only, via
  `postgres_dev_server_jexframe` MCP, for legacy-behaviour evidence.
- **SIT** — NOT used. The fix is not deployed there yet.

## Code / Build Under Test

- Branch: working tree (`wecms-api`)
- Commit: **NOT COMMITTED** — work stops at `READY FOR DBA` / `READY FOR MANUAL COMMIT`
- Build: `./gradlew compileJava` → clean; `./gradlew test --tests "…einvoice.*"` → BUILD SUCCESSFUL (38 s)
- API URL/environment: none exercised over HTTP

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew compileJava` | **PASS** | no output, exit 0 |
| `./gradlew test --tests "com.kosign.wecms.domain.einvoice.*"` | **PASS** | BUILD SUCCESSFUL; 112 tests, 0 failures, 0 errors |
| `EInvoiceDetailScrapeCurrencyTest$KInvoice` (new) | **PASS** | 3/3 |
| `EInvoiceDetailScrapeCurrencyTest$PosInvoice` (new) | **PASS** | 2/2 |
| `EInvoiceDetailScrapeCurrencyTest$CInvoice` (new) | **PASS** | 3/3 |

Per-class counts from `build/test-results/test/*.xml`:

```
EInvoiceDetailScrapeCurrencyTest$KInvoice    tests=3  failures=0 errors=0   (new)
EInvoiceDetailScrapeCurrencyTest$PosInvoice  tests=2  failures=0 errors=0   (new)
EInvoiceDetailScrapeCurrencyTest$CInvoice    tests=3  failures=0 errors=0   (new)
EInvoiceServiceTest                          tests=37 failures=0 errors=0
EInvoiceTrcoAutoRegistrarTest                tests=18 failures=0 errors=0
EInvoiceExportProviderTest                   tests=17 failures=0 errors=0
EInvoiceScrapeServiceTest                    tests=12 failures=0 errors=0
EInvoiceDetailWorkbookWriterTest             tests=8  failures=0 errors=0
EInvoiceDueDateTest                          tests=8  failures=0 errors=0
EInvoiceCounterpartyAddressTest              tests=4  failures=0 errors=0
                                             ---- 112 total, 0 failed
```

## Regression Tests

**Did a regression test cover the original defect? YES.**

```
Test file:          src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailScrapeCurrencyTest.java
Test case:          KInvoice.foreignCurrencyFoldsToVnd  — "foreign currency folds to VND — the chk07 regression case"
Original failure
  reproduced/
  protected:        asserts resolve("KRW", "1", "K26TTT", "76676400") == "VND"
                    — the exact invoice from trace 20260824135803PT33011246736568DEVL.
                    Pre-fix the code returned "KRW", which chk07 rejects.
Result:             PASS
```

Second symptom also pinned:

```
Test case:          PosInvoice.blankDefaultsToVnd
Behavior protected: POS e-Invoice (einv_testResults='2') with a blank currency resolves to
                    "VND" (legacy c002:256-261, ticket #38058) instead of leaving NULL.
Result:             PASS
```

## Test Scenarios

| Scenario | Expected | Actual | Result |
|---|---|---|---|
| K invoice, `currency_cd='KRW'` (reported invoice) | `VND` | `VND` | **PASS** |
| K invoice, `currency_cd='KHR'` | `VND` | `VND` | **PASS** |
| K invoice by symbol only (`einv_test_results=''`, symbol `K26TTT`), `KRW` | `VND` | `VND` | **PASS** |
| K invoice, `VND` / `USD` / `VNĐ` | passthrough (`VNĐ`→`VND`) | same | **PASS** |
| K invoice, blank ccy, `totalAmt=76676400` | `VND` | `VND` | **PASS** |
| K invoice, blank ccy, `totalAmt=999` | `VND` (via `999`) | `VND` | **PASS** |
| K invoice, blank ccy, `totalAmt=76,676,400` (separator) | `VND` (via `999`) | `VND` | **PASS** |
| K invoice, blank ccy, non-numeric amount | `VND` (via `999`) | `VND` | **PASS** |
| POS invoice, blank ccy | `VND` | `VND` | **PASS** |
| POS invoice, `USD` / `VND` | passthrough | same | **PASS** |
| C invoice, blank ccy | `null` (leave phase-1 value) | `null` | **PASS** |
| C invoice, `VND` / `USD` | passthrough | same | **PASS** |
| C invoice, residual `KRW` | `998` + WARN (deliberate deviation) | `998` | **PASS** |
| SIT: re-run Update over `20260301`–`20260331`, batch completes without `WBE_DB` | HTTP 200 | — | **NOT RUN** — fix not deployed to SIT |
| SIT: footer shows `Total (VND) 52`, no `Total ()` | 52 / no bucket | — | **BLOCKED** — depends on the DBA backfill |

## Database Verification

Read-only evidence gathering; **no DML or DDL executed**.

| Check | Result |
|---|---|
| `busa_doc_mstr_chk07` on live JEX DEV | `CHECK (trco_trsc_ccy_cd = ANY (ARRAY['VND','USD','999','998']))` — identical to the refactor DB, confirming legacy DDL |
| Live JEX DEV value distribution (all rows) | VND 23,407 · NULL 9,902 · USD 508 · `998` 1 — no foreign code ever stored |
| Live JEX DEV, POS rows (`einv_testresults='2'`) | VND **467** · NULL 78 |
| Refactor dev DB, POS rows, tenant `VN_VNM0004_000001` | VND **0** · NULL **407** — the defect, quantified |
| Legacy K rows with a real FX rate | `1K24TCG-98262…` rate 25464 → `USD`; `1K24TCG-98517…` rate 1.00 → `VND` |
| Backfill predicate (script §1.1), executed read-only | **304 rows** — POS 300 + K 4; **0 with an FX rate**; `issu_dt 20260101..20260820`; single tenant |
| Backfill executed | **NOT RUN** — `PENDING DBA` |

Script: [`docs/sql/2026/backfill-einvoice-currency-2026-08-24.sql`](../../sql/2026/backfill-einvoice-currency-2026-08-24.sql).
Per-environment status in the related record's **Database Execution Status** table.

## Regression Review

- **Areas checked:** `normalizeCcy` was left byte-identical, so its other consumer —
  `EInvoiceTrcoAutoRegistrar:485` (`"USD".equals(normalizeCcy(raw))`) — is untouched
  (`EInvoiceTrcoAutoRegistrarTest` 18/18 PASS). The modified phase-1 line is covered by
  `EInvoiceScrapeServiceTest` 12/12 PASS. Read/display/export paths consume the column
  unchanged (`EInvoiceExportProviderTest` 17/17, `EInvoiceDetailWorkbookWriterTest` 8/8,
  `EInvoiceServiceTest` 37/37).
- **Result:** no regression detected in the e-invoice module.

## API Verification

No endpoint, DTO, wire-field, casing, error-code or authorization change. Request/response
shape of `POST /api/v1/addon/einvoices/scrape-detail-results` is unchanged — **NOT APPLICABLE**.

## Security / Permission Verification

**NOT APPLICABLE** — no auth, permission, tenant-scope, credential or file-access surface
touched. The service continues to read `bizKey` from `TenantContext`.

## Performance / Concurrency Verification

**NOT APPLICABLE** — the change is a pure in-loop value resolution (no query, no I/O, no new
transaction boundary). Worth noting for context: the pre-fix defect *did* fail a whole
53-invoice batch through Hibernate auto-flush, so the fix removes a batch-abort path rather
than adding one.

## Existing Failures

None. The 104 pre-existing e-invoice tests passed before and after the change.

## New Failures

None.

## Not Tested

| Not performed | Why |
|---|---|
| SIT HTTP re-run of the Update flow | Fix not deployed to SIT; deploy is a manual team step (§16) |
| SIT UI footer verification | Depends on both the SIT deploy and the DBA backfill |
| Backfill pre/post-check execution | DML on a shared DB — DBA-executed (§14f) |
| Broader `./gradlew test` (full suite) | Change is confined to one module with no shared-service surface; targeted scope proves it (§15i / §15x) |

## Final Result

**PARTIAL** — unit verification PASS and regression PASS; SIT and data-repair verification
outstanding by design.

## Recommendation

**READY FOR DBA** → then SIT deploy + re-verify → then `READY FOR MANUAL COMMIT`.

No commit, push, merge, tag or deployment was performed.
