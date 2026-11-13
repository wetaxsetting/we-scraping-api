# WeInvoice: Engine Worker for Queued Requests (SYNC, FETCH_XML, FETCH_PDF, IMPORT, TEST_DATA)

**Date:** 2026-11-09
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** READY FOR SIT (code pushed; engine live run PASS 2026-11-10; screens end to end not run)
**Investigation Status:** COMPLETE ([web audit of the gaps](../../../we-scraping-web/docs/issues/weinvoice-p1-api-wiring-gaps-2026-11-09.md), backend sibling repo) · **Approval Status:** NOT REQUIRED (developer: "Viết worker") · **Implementation Status:** COMPLETE · **Test Status:** PASS (unit + engine live run)
**Repository:** wecms-api (checked out as `we-scraping-api`); engine side in the we-scraping repository

## Problem

Five endpoints only queue a row in `weinvoice.winv_engn_rqst` (`SYNC`, `FETCH_XML`, `FETCH_PDF`,
`IMPORT`, `TEST_DATA`). No engine process consumed them, so the screens waited until their timeout
(sync, "Lấy XML còn thiếu", "Lấy PDF gốc", upload, test data).

## Expected Behavior

An engine worker claims each queued row, reports step / progress, honours cancel, and ends it
`DONE` / `PARTIAL` / `FAILED` / `CANCELLED` per the module README §"Engine contract". Files a user
uploads show origin `upload` in the invoice record.

## Scope

- Engine (we-scraping): `web/weinvoice_worker.py` + tests; `db/schema.sql` (`invoice_details.nguon`
  accepts `xml_upload`); `weinvoice_bridge.py` (upgrade upload-only companies when they connect).
- This repository: origin mapping for engine files coming from uploads / test data
  (`InvoiceFileService.list`, invoice list / detail `origin`); README engine contract wording.

## Out of Scope

`LOGIN_TEST` (synchronous since 2026-11-06); the scheduled job (`tick`, queue workers) — unchanged.

## Database Impact

- This repository: NONE (no DDL / DML in `weinvoice` schema; the engine writes rows the schema already
  defines: `winv_engn_rqst` status columns, `winv_sync_cvrg`, `winv_upld_file.rslt_*`, `winv_lkup_cd` for
  test data codes).
- Engine schema (we-scraping `db/schema.sql`, engine-owned, applied by `invoice_job.py init-db`): CHECK
  `invoice_details_nguon_check` widened to `('xml','xml_email','xml_upload','pdf')` — additive, no data change.

## Implementation

### Engine (we-scraping)

`web/weinvoice_worker.py` — `python -X utf8 web/weinvoice_worker.py [--once] [--poll 5]`:

| Request | Behaviour |
|---|---|
| claim | oldest `QUEUED` row with `cncl_yn='N'` (`FOR UPDATE SKIP LOCKED`) → `RUNNING`, `strt_dtm`; startup turns `RUNNING` rows idle > 180 min into `FAILED` ("Engine dừng giữa chừng") |
| `SYNC` | per month of `from_month..to_month` (future months skipped, current month cut at today, ≤ 24): steps `login` → `list` → `xml` → `done`, progress `{month, listed, xml_done, xml_total}`; list per sub-type with the job's `sync_lists` (`move_cursor=False`), then XML of that month's pending tasks in batches of 5 (cancel checked between batches); `winv_sync_cvrg` per (MST, sub-type, month): `gdt_cnt` = rows GDT listed (kept from the previous run when the list failed), `got_cnt` = invoices stored, `cmpl_yn = Y` when listed without error and `got_cnt ≥ gdt_cnt`; result `{new, updated: 0, xml, listed, xml_failed, sync_errors, months}`; `PARTIAL` on list errors / XML failures / circuit breaker |
| `FETCH_XML` | invoices of the MST without XML: task reset to pending now (also `dead` / `no_source`, not `summary_only`), downloaded at once; result `{requested, xml, failed, already, summary_only, not_found}` |
| `FETCH_PDF` | first invoice id; PDF task claimed (or created) now; lookup code / company code / link from the request, else from `winv_lkup_cd`, override the XML guesses (a link is resolved through the adapter's `invoice_from_link`); steps `open · submit · download · done`; result `{pdf: saved|exists, source_id}`; failure message = the adapter's error |
| `IMPORT {upload_id[, file_nos, company_mst]}` | files of the batch (unprocessed, or the listed `file_nos` for "adopt"), XML → ZIP → PDF; per file `winv_upld_file.rslt_*` (`NEW` create invoice from XML · `MERGED` XML / PDF added · `DUPLICATE` · `FOREIGN` neither MST belongs to the owner · `PDF_PENDING` · `ERROR`); result counts |
| `IMPORT {invoice_id, file_id}` | the uploaded XML must carry the invoice's key; saved as the invoice's XML when it has none |
| `TEST_DATA` | `WEINVOICE_TEST_DATA_DIR` (default `docs/WeInvoice_UI_Spec_v1.0/TestData`): `XML/` (xml, zip), `ma_tra_cuu.csv` → `winv_lkup_cd`, `PDF/`; result = the prototype's report (`folder, xml_total, created, merged, duplicate, errors[], new_tax_ids[], codes_applied, codes_unmatched[], pdf_attached, pdf_unmatched[]`) |

- Dedupe key (UI Spec 3.2): MST bán + mẫu + ký hiệu + số within the owner's MST. An upload never
  overwrites an XML that GDT provided.
- An invoice created from an upload: `list_raw = {"nguon": "upload"|"test_data", "file": …}`, XML stored with
  `invoice_details.nguon = 'xml_upload'`, PDF with `invoice_pdf.source_id = 'upload'|'test_data'`.
- A managed MST without an engine company row gets a placeholder `companies` row
  (`gdt_username = '#upload:<MST>'`, `active = false` — the job never logs in with it); when the MST
  becomes `CONNECTED`, `weinvoice_bridge.register_connected_companies` switches it to the real account
  and activates it.
- Uploaded files are read from `WEINVOICE_STORAGE_ROOT` (= backend `WECMS_WEINVOICE_STORAGE_ROOT`);
  paths are confined to that root. ZIP limits: 2 000 members, 50 MB each, 500 MB total.

### This repository

- `InvoiceCodes.recordOrigin` / `xmlOrigin` / `pdfOrigin` — one place for the origin wording:
  record `upload` when `list_raw.nguon` is `upload` / `test_data`, else `gdt`; XML file `email` (`xml_email`),
  `upload` (`xml_upload`), else `gdt`; PDF file `email…` → `email`, `upload` / `test_data` → `upload`, else `vendor`.
- `InvoiceQueryRepository` (list rows read `list_raw->>'nguon'`), `InvoiceQueryService.detail`,
  `InvoiceFileService.list` use them (previously hard-coded `gdt` / `email` / `vendor`).
- Module README §"Engine contract": rows describe what the worker actually does.

## Files Changed

- This repository: `common/InvoiceCodes.java`, `repository/InvoiceQueryRepository.java`,
  `service/InvoiceQueryService.java`, `service/InvoiceFileService.java`, `InvoiceCodesTest.java`,
  `docs/features/addon-service/weinvoice/README.md`, this record, the test-result record.
- Engine (we-scraping, separate repository): `web/weinvoice_worker.py` (new), `tests/test_weinvoice_worker.py` (new),
  `db/schema.sql`, `weinvoice_bridge.py`, `.env.example` (`WEINVOICE_STORAGE_ROOT`, `WEINVOICE_TEST_DATA_DIR`), `CLAUDE.md`.
- Web: `we-scraping-web/docs/issues/weinvoice-p1-api-wiring-gaps-2026-11-09.md` (test data + language).

## API Impact

No endpoint or DTO change. `origin` now reports `upload` for records / files that came from uploads
(already a documented value of `InvoiceRow.origin` / `InvoiceFileItem.origin`). Engine requests now reach
`done` / `partial` / `failed` / `cancelled` instead of staying `queued`.

## UI Impact

None in this repository. Screens that waited on engine requests (sync, fetch XML, original PDF, upload,
test data) get a result once the worker runs.

## Verification

### Automated Tests

- `./gradlew test --offline --tests 'com.kosign.wecms.domain.weinvoice.*' --tests '*EndpointAuthorizationCoverageTest'` — PASS, 102 tests, 0 failures (new `InvoiceCodesTest.originOfUploadedRecordsAndFiles`).
- Engine `python -m pytest tests/test_weinvoice_worker.py tests/test_weinvoice_bridge.py` — PASS, 41.
- Engine regression `python -m pytest tests -k "invoice or xml_parser or fetch_pdf or pdf_ or mail or main_auto or weinvoice"` — PASS, 1111 (13 skipped: DB tests without `TEST_DATABASE_URL`).

### Manual Verification

- Environment: LOCAL — engine DB read-only check: CHECK `invoice_details_nguon_check` still the old one
  (`xml`, `xml_email`, `pdf`); queue holds only 3 old `LOGIN_TEST` rows (ignored by the worker).
- Live worker run 2026-11-10 (developer approved): `init-db` applied; IMPORT (DUPLICATE / NEW / MERGED),
  FETCH_PDF (exists, portal failure message), SYNC 2026-10 purchase (80 listed, 2 new, coverage 3 rows `Y`) — PASS,
  details in the test-result record. FETCH_XML / TEST_DATA / cancel not exercised live.

## Test Evidence

[weinvoice-engine-request-worker-test-result-2026-11-09.md](../testing/results/weinvoice-engine-request-worker-test-result-2026-11-09.md)

## Result

```
Code: COMPLETE (engine worker + backend origin + web) · Tests: PASS (offline / unit)
Engine schema change (nguon CHECK): applied on the LOCAL engine DB 2026-11-10 (init-db)
Live run: PASS (LOCAL) · Pushed: engine main ca8b862, api sit 4a522a2, web scraping-data e04d912 (developer request)
```

## Known Limitations / Follow-up

- Any other engine DB (SIT host): run `python -X utf8 web/invoice_job.py init-db` before the first IMPORT,
  otherwise saving an uploaded XML fails (`invoice_details_nguon_check`).
- The worker must run as a service next to the queue workers (`python -X utf8 web/weinvoice_worker.py`);
  a scheduled-task entry is not added yet.
- `FETCH_XML` / `SYNC` log in to GDT with the stored account; repeated wrong passwords lock the GDT account.
- Codes typed by users (`winv_lkup_cd`) are used by `FETCH_PDF` only; the background PDF worker still reads
  codes from the XML.
- `updated` in the SYNC result stays 0: the engine never changes a stored invoice's status
  (`upsert_invoices` is insert-only, see CLAUDE.md §3.3).
- The 3 old `LOGIN_TEST` rows stay `QUEUED` (obsolete since 2026-11-06).
