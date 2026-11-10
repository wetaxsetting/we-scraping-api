# WeInvoice Engine Request Worker — Test Result

**Date:** 2026-11-09
**Type:** TEST RESULT
**Status:** PASS (engine live run 2026-11-10); screens end to end NOT RUN
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

Before the live run (read-only, 2026-11-09): `invoice_details_nguon_check` was still `('xml','xml_email','pdf')`;
`weinvoice.winv_engn_rqst` holds 3 `LOGIN_TEST` rows `QUEUED`; no upload batches.

### Live Run (LOCAL engine DB, 2026-11-10)

`python -X utf8 web/invoice_job.py init-db` → `invoice_details_nguon_check` now accepts `xml_upload`.
Requests written exactly as the backend writes them (upload batch #1 of `lamine@gmail.com`, MST 1201496252,
3 files copied under `WEINVOICE_STORAGE_ROOT`), then `python -X utf8 web/weinvoice_worker.py --once`:

| # | Request | Result |
|---|---|---|
| 4 | `IMPORT {upload_id: 1}` | DONE `{new 1, merged 1, duplicate 1}` in 1 s: XML of invoice 167736 → `DUPLICATE`; mail XML K26TCA-211612 (not in DB) → `NEW` invoice 275201 (`mua-khma`, `list_raw.nguon = upload`, details `xml_upload`, tasks xml/pdf done); its PDF → `MERGED` (`source_id = upload`); `rslt_*` incl. partner name and total written |
| 5 | `FETCH_PDF` invoice 167736 (has PDF) | DONE `{pdf: exists, source_id: email}` |
| 6 | `FETCH_PDF` invoice 7689 (Viettel) | FAILED, `err_msg` = portal answer "Hóa đơn không tồn tại trên hệ thống" (same as the job's earlier attempts) |
| 7 | `SYNC {purchase, 2026-10}` | DONE in 14 s (cached GDT token, MTT list captcha solved): listed 80, new 2 (both K26TTT, `summary_only`), `winv_sync_cvrg` mua 30/30, mua-khma 37/38, mua-mtt 13/13, all `cmpl_yn = Y` |

The 3 obsolete `LOGIN_TEST` rows stayed `QUEUED` (not claimed). No placeholder company was needed (MST already in `companies`).

## Not Tested

- `FETCH_XML`, `TEST_DATA` (TestData folder is empty), cancel of a running request, a successful provider download
  through `FETCH_PDF`, an upload-only MST (placeholder company).
- Screens end to end (backend + web + worker running together).

## Final Result

PASS — offline / unit tests pass; engine live run 2026-11-10: IMPORT, FETCH_PDF (exists / failure), SYNC as expected. Screens end to end not run.

## Recommendation

READY FOR SIT — code pushed. Remaining: run `web/weinvoice_worker.py` as a service where the engine
serves SIT (after `init-db` there) and check screens 06 / 09 / 10 end to end.
