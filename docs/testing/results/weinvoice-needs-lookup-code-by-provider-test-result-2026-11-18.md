# WeInvoice "Needs a Lookup Code" by Provider (API) — Test Result

**Date:** 2026-11-18
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api (`we-scraping-api`)
**Related Record:** [weinvoice-needs-lookup-code-by-provider-2026-11-18](../../issues/weinvoice-needs-lookup-code-by-provider-2026-11-18.md)

## Scope

`ProviderLookup.needsCode` for list rows (`needs_lookup_code`), the detail (`lookup.needs_code`), the `provider=needs_code`
filter and the facet count: no flag once the original PDF is stored or for providers looked up without a code.

## Environment

LOCAL (Windows laptop), JDK 21, `./gradlew test --offline`; read-only count on the shared engine DB.

## Code / Build Under Test

Branch: `sit` (working tree, on top of `420bc98`) · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `ProviderLookupTest` (new): rule cases (FPT needs, PDF stored / code present / unknown provider → no, 6 providers without code → no); SQL fragment carries `NOT c.has_pdf` + the provider list, provider filter uses it | PASS | 2/2 |
| `InvoiceRowLookupMappingTest`: needs-code example moved from BKAV to FPT (BKAV is now exempt by design); new case PDF stored / BKAV → no flag | PASS | |
| `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` | PASS | 28 classes, 211 tests, 0 failures |

## Regression Tests

The old expectation "BKAV without a code needs one" was the defect itself; the test now pins the opposite.

## Database Verification

Read-only count with the new rule (`invoice_pdf` / uploaded PDF, typed codes from `winv_lkup_cd`): invoices flagged
8,550 → about 4,200 — WeTax 1,604, FPT 1,437, VNPT 966, TS24 56, Thế Giới Di Động 34, SmartSign 31, EFY 29, ….

## Not Tested

`bootRun` + real HTTP: NOT RUN.

## Final Result

PASS

## Recommendation

READY FOR MANUAL COMMIT
