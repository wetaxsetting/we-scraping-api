# WeInvoice Engine Request Worker — Test Result

**Date:** 2026-11-09
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api (checked out as `we-scraping-api`); engine side in the we-scraping repository
**Related Record:** [../../issues/weinvoice-engine-request-worker-2026-11-09.md](../../issues/weinvoice-engine-request-worker-2026-11-09.md)

## Scope

Engine `web/weinvoice_worker.py` (claim / finish / cancel / stale, SYNC, FETCH_XML, FETCH_PDF, IMPORT,
TEST_DATA), `weinvoice_bridge.py` upgrade of upload-only companies, engine `nguon` CHECK; backend origin
mapping (`InvoiceCodes.recordOrigin` / `xmlOrigin` / `pdfOrigin` and its three callers).

## Environment

LOCAL — Windows 11, JDK 21.0.11, Gradle `--offline`; Python 3.13 (engine).

## Code / Build Under Test

Branch: local `sit` (api), `main` (engine) + working trees · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --offline --tests 'com.kosign.wecms.domain.weinvoice.*' --tests '*EndpointAuthorizationCoverageTest'` | PASS | 102 tests, 0 failures, 0 errors (JUnit XML) |
| Engine `python -X utf8 -m pytest tests/test_weinvoice_worker.py tests/test_weinvoice_bridge.py -q` | PASS | 41 passed |
| Engine `python -X utf8 -m pytest tests -q -k "invoice or xml_parser or fetch_pdf or pdf_ or mail or main_auto or weinvoice"` | PASS | 1111 passed, 13 skipped (DB tests, no `TEST_DATABASE_URL`), 16 deselected |

Worker tests cover: month range (cut at today, future skipped, cross-year, leap February, bad input,
24-month cap); sub-type from symbol; `tthai` from the related-invoice block; manual lookup code replaces
only lookup-like `TTKhac` fields, company code, link resolved through the adapter; owner MST choice
(buyer / seller / foreign / preferred / adopt-only); ZIP order XML→PDF, summary, single member, broken /
empty ZIP; unknown extension; non-PDF; storage path traversal blocked; request life cycle DONE /
CANCELLED (checkpoint) / FAILED (business error, GDT login error, unexpected exception) / empty queue.

## Regression Tests

`InvoiceCodesTest.originOfUploadedRecordsAndFiles` pins the origin wording; engine job suite unchanged (1111).

## Database Verification

Read-only on the engine DB: `invoice_details_nguon_check` is still `('xml','xml_email','pdf')`;
`weinvoice.winv_engn_rqst` holds 3 `LOGIN_TEST` rows `QUEUED`; no upload batches.

## Not Tested

- Live worker run against the database: SYNC / FETCH_XML (GDT login with the real account), FETCH_PDF
  (provider portal), IMPORT / TEST_DATA (write invoices) — waits for the developer's go-ahead.
- Engine `init-db` (CHECK widening) — not applied.
- Screens end to end (backend + web + worker running together).

## Final Result

PARTIAL — offline / unit tests pass on engine, backend and web; live run pending.

## Recommendation

READY FOR MANUAL COMMIT after the developer's live check (apply `init-db`, run `web/weinvoice_worker.py`,
upload one XML + one PDF on screen 09, run one sync).
