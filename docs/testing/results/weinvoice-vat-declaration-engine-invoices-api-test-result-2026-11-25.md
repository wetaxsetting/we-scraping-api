# WeInvoice VAT Declaration on Engine Invoices (API) — Test Result

**Date:** 2026-11-25
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** we-scraping-api
**Related Record:** [WeInvoice VAT Declaration on Engine Invoices](../../features/addon-service/weinvoice/weinvoice-vat-declaration-engine-invoices-2026-11-25.md)

## Scope

`VatScope`, `VatInvoiceSource`, the scoped overloads of `VatDeclarationService` (WeCMS path unchanged),
`EngineVatInvoiceSource` (engine SQL), `WeInvoiceVatDeclarationController`, authz allow-list entry.

## Environment

LOCAL (Windows, JDK 21 from `~/.gradle/jdks`, `--offline`). Engine SQL run read-only against the shared engine DB.

## Code / Build Under Test

Branch: `sit` · Base commit: `6b74af4` · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --tests "*VatDeclarationWeInvoiceScopeTest"` | PASS | 4 tests |
| `./gradlew test --tests "*EngineVatInvoiceSourceTest"` | PASS | 3 tests (params, SQL contract, no-op markers) |
| `./gradlew test --tests "com.kosign.wecms.domain.weinvoice.*" --tests "com.kosign.wecms.domain.vatdeclaration.*" --tests "*EndpointAuthorizationCoverageTest"` | PASS except 1 pre-existing | 287 run, 1 failed (see Existing Failures) |
| `./gradlew test` (full suite) | PASS except 1 pre-existing | 3,033 run, 1 failed, 21 skipped |

## Regression Tests

WeCMS path: `VatDeclarationLifecycleTest` (unchanged) keeps verifying the `VatAnnexInvoiceRepository` calls
(`markProcessed` / `clearProcessed` / `syncBuckets` …) through the original method signatures — all its tests that
passed before still pass.

## Database Verification (engine SQL, read-only)

The four engine queries (dumped from the class constants) run against the engine DB:

| MST / period | Result |
|---|---|
| 1201496252, 10/2026 | Sales 8% 2,200,000 / VAT 176,000 + KCT 368,680,645 = 370,880,645 / 176,000 = header totals of the 16 sales invoices. Purchases differ from the header sum by 262,886 / 2,583, explained by 2 invoices: one USD invoice converted at its rate (header stored 1.00 USD), one invoice with XML lines but no XML total. 0.07 s |
| 0318793907, 10/2026 (45,640 sales invoices) | Sales 8,715,513,426 / 697,250,538 = header totals; 0.34 s |
| 3600790154, 11/2026 | 0% sales include USD exports converted at `ty_gia` (C26TKS-445, 7,303.84 USD × 25,705) |
| annex rows / groups | one row per invoice × rate, counterpart MST + name, product names of the rate's lines; search "cước" narrows to 17 rows |

## Database Verification (declaration tables)

NOT RUN — `vat_decl` / `vat_decl_items` on the API's main DB were not exercised (no running API against a DB here).

## Existing Failures

`VatDeclarationLifecycleTest > create — required fields > sync accepts a period whose adjustments exceed its revenue`
— fails identically on the unmodified source (service restored from `HEAD`, new classes moved out, same test run:
49 run, 1 failed). Recorded in CLAUDE.md as pre-existing.

## New Failures

None.

## Not Tested

End-to-end create → lock → export through HTTP against a real DB (needs the API running with `vat_decl` tables and the
engine DB); SIT.

## Final Result

PARTIAL — unit + engine-SQL checks PASS; end-to-end NOT RUN.

## Recommendation

READY FOR MANUAL COMMIT — verify on SIT after confirming `vat_decl` / `vat_decl_items` exist on the main DB.
