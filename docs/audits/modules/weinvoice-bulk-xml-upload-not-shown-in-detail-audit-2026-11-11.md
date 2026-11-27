# WeInvoice: XML from "Tải lên hóa đơn" Recognised but Detail Still Shows "chưa có" — Audit

**Date:** 2026-11-11
**Type:** AUDIT / INVESTIGATION
**Current Work Classification:** AUDIT
**Finding Classification:** BUG (engine import) + ISSUE (two workers on one queue)
**Status:** COMPLETE (investigation)
**Investigation Status:** COMPLETE
**Approval Status:** APPROVED (developer, 2026-11-11: "Duyệt, sửa luôn")
**Implementation Status:** COMPLETE in the engine repository (no api / web change)
**Repository:** wecms-api (`we-scraping-api`); engine worker `web/weinvoice_worker.py` (we-scraping) involved

## Observed Behavior

The developer dropped an XML into the bulk upload screen ("Tải lên hóa đơn"). The batch recognised the invoice,
but the invoice detail still shows the XML as "chưa có" (not on this server).

## Expected Behavior

An XML uploaded for an invoice whose XML file is not on this server fills the invoice's XML slot, as the detail
screen upload now does (api `8f0de22`).

## Scope

Bulk upload → engine `IMPORT` → which tables/files are written → what the detail lists.

## Initial Evidence

Developer report 2026-11-11 (screen 07 upload, then screen 05 detail).

## Investigation Plan

1. `winv_upld_infm` / `winv_upld_file` rows of the latest batch and their result.
2. Engine `weinvoice_worker.do_import` for an XML whose invoice already exists (MERGED / DUPLICATE).
3. `InvoiceFileService.list` — what it reads for the XML slot.

## Database Trace (shared engine DB, read-only, 2026-11-11)

| Batch (`winv_upld_infm`) | Request | File | Result (`winv_upld_file`) |
|---|---|---|---|
| 3, 11/11 11:15 (VN) | #14 IMPORT — DONE | `1C26TWE_00000003_3603379922.xml` | **DUPLICATE** "Hóa đơn đã có XML", invoice 10434 |
| 2, 11/11 10:19 (VN) | #13 IMPORT — FAILED | `1C26TWE_00000004.pdf` | **ERROR** `FileNotFoundError … C:\Project\scan-captcha-project\output\weinvoice-uploads\…` |

- Invoice 10434 (1C26TWE-3): `invoice_xml.xml_path = xml/1201496252/2026/11/1201496252/1_C26TWE_3.xml`, fetched
  2026-11-02 by the **laptop** engine (source `xml_email`) — the file is not on .13.
- Request #13 appears in the **laptop's** `output/logs/invoice_job_2026-11.log` (10:19:38 "WeInvoice #13 IMPORT … bắt
  đầu"); #14 does not → #14 ran on .13, #13 was claimed by the laptop's `weinvoice_worker.py`.

## Engine Trace

`web/weinvoice_worker.py` `Importer.import_xml` (≈ line 480): an existing invoice with an `invoice_xml` row →
`DUPLICATE`; the uploaded bytes are **not written anywhere** (the rule assumes the GDT XML is the reference copy and is
present). `import_pdf` does the same for `has_pdf` ("Hóa đơn đã có PDF"). The check is on the **DB row**, not on the
file on this machine's disk.

## Root Cause

1. **BUG (engine):** bulk import treats "the DB has an XML/PDF row" as "the file exists". When the file was fetched
   by another machine, the upload is discarded as DUPLICATE and the detail keeps showing the missing engine file
   ("chưa có"). The detail-screen upload was fixed in api `8f0de22`; the bulk path (engine) was not.
2. **ISSUE (operations):** `weinvoice_worker.py` runs on both the laptop and .13 against one queue
   (`winv_engn_rqst`). Uploads live in the API server's storage root, so a request the laptop claims cannot read its
   files (request #13 → ERROR). Only the machine that hosts the API storage may run this worker.

## Recommended Implementation

- engine `Importer.import_xml`: invoice found with `has_xml` → if the engine XML file is **missing on this machine**,
  write the uploaded bytes to the stored `xml_path` (no re-parse; the key already matched) and report `MERGED`
  "Bổ sung file XML (máy chủ chưa có file)"; keep `DUPLICATE` when the file is there.
- engine `Importer.import_pdf`: same for `has_pdf` with the stored `invoice_pdf.pdf_path`.
- operations: stop `weinvoice_worker.py` on the laptop (or point both at one shared storage); re-run batch 2 on .13.

## Required Regression Tests

- engine `tests/test_weinvoice_worker.py`: existing XML row + file missing → file written, `MERGED`; file present →
  `DUPLICATE`, nothing written. Same pair for PDF.

## Affected Repositories

we-scraping (engine worker); operations on the laptop / .13. No api / web change.

## DB Impact

NONE.

## Implementation (2026-11-11, engine repository `we-scraping`)

The fix is entirely in the engine, which has no docs-first tree; it is recorded here and in the engine `CLAUDE.md`.

- `web/weinvoice_worker.py` `Importer._missing_here(rel)`: the DB has a path but this machine has no file.
- `import_xml`: existing invoice with `has_xml` and the XML file missing here → write the uploaded bytes to the stored
  `xml_path`, `MERGED` "Bổ sung file XML (máy chủ chưa có file)"; no `_save_xml`, no task change (record not re-parsed).
  File present → `DUPLICATE` as before.
- `import_pdf`: `_invoice` now also returns `pdf_path`; `has_pdf` and the file missing here → write to the stored
  `pdf_path`, `MERGED` "Bổ sung file PDF (máy chủ chưa có file)"; present → `DUPLICATE`.
- Operations: the laptop's `weinvoice_worker.py` (started in a developer session) was stopped 2026-11-11; only .13 runs it.

## Verification

- `pytest tests/test_weinvoice_import_missing_file.py tests/test_weinvoice_worker.py` — PASS, 34 tests (4 new: XML
  restored and not re-parsed · XML present stays DUPLICATE · PDF restored · PDF present stays DUPLICATE).
- `pytest tests -k "invoice or weinvoice or mail or login_guard or gdt_pdf or internal_api or fetch_missing"` — PASS,
  538 passed, 13 skipped.
- Manual on .13 (re-upload batch 3 XML and batch 2 PDF): NOT RUN — needs the engine update pulled on .13.
