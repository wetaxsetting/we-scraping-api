# WeInvoice Uploaded PDF Replaces Provider PDF — Test Result

**Date:** 2026-11-11
**Type:** TEST RESULT
**Status:** PASS (unit) · manual NOT RUN
**Repository:** wecms-api (`we-scraping-api`)
**Related Record:** [weinvoice-uploaded-pdf-replaces-provider-pdf-2026-11-11](../../issues/weinvoice-uploaded-pdf-replaces-provider-pdf-2026-11-11.md)

## Scope

`InvoiceFileService.list` (one PDF slot, missing engine files, GDT copy stand-in) and `upload` (standard name and
layout); `StoredFiles.saveInvoiceFile`.

## Environment

LOCAL (developer laptop), unit tests only.

## Code / Build Under Test

Branch: `sit` · Commit: NOT COMMITTED

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/eclipse_adoptium-21… ./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` | PASS | 110 tests, 0 failures, 0 errors (15 classes) |
| `InvoiceFileServiceTest` | PASS | 19 tests (3 new: uploaded PDF replaces provider PDF · provider PDF missing → `size null` + GDT copy listed · uploaded XML stays listed next to the PDF; 2 updated: stored name `1_C26TAA_123.pdf`, layout by invoice date) |
| `StoredFilesInvoiceLayoutTest` (new) | PASS | 2 tests: path `<owner>/invoices/<MST>/2026/11/<seller>/1_C26TWE_4.pdf`; second upload → `_2`, first file kept |
| Addendum (XML upload when the engine XML is not on this server): same command | PASS | 111 tests, 0 failures; `InvoiceFileServiceTest` 20 |

## Regression Tests

Did a regression test cover the original defect? **YES** — `uploadedPdfReplacesTheProviderPdfInTheList` (the
provider `pdf` item is no longer listed once a PDF is uploaded) and
`providerPdfMissingOnThisServerKeepsNullSizeAndTheGdtCopyStandsIn`.

## Not Tested

- Manual check on .13 (upload a PDF on 1C26TWE-4, reopen): NOT RUN — needs this build deployed + login.
- Bulk upload (`winv_upld_*`) unchanged, not re-tested.

## Existing Failures

Not run this time: the full suite's 13 pre-existing failures (missing `application-local.yml`) were not re-run.

## Final Result

PASS (unit)

## Recommendation

READY FOR MANUAL COMMIT
