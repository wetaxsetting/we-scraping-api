# WeInvoice: Uploaded PDF Replaces the Provider PDF, Missing Engine Files, Standard Upload Names

**Date:** 2026-11-11
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** READY FOR MANUAL COMMIT
**Investigation Status:** COMPLETE · **Approval Status:** NOT REQUIRED (developer, 2026-11-11: "sau khi nạp PDF thì trong các tệp nhìn thấy phải thay thế có phần pdf chưa có. và đổi tên format cho giống cấu trúc quy định hiện tại") · **Implementation Status:** COMPLETE · **Test Status:** PASS (unit)
**Repository:** wecms-api (checked out as `we-scraping-api`); viewer in we-scraping-web
**Evidence:** web audit `we-scraping-web/docs/audits/weinvoice-uploaded-pdf-not-shown-audit-2026-11-11.md`

## Problem

Invoice 1C26TWE-4 (Webcash, sales): the provider (MISA) PDF row exists in the shared engine DB but its file was
downloaded by another machine's engine, so this server cannot read it. The developer uploaded the PDF and
confirmed "replace"; the record then listed three files — the provider PDF ("1 KB", unreadable), the upload,
and no GDT copy — and the viewer still showed the copy rendered from XML.

## Current Behavior (verified from source)

- `InvoiceFileService.list` lists the engine PDF whenever `invoice_pdf` has a row, even when an uploaded PDF
  exists and even when the file is not on this server (`size: null`).
- `upload("pdf")` with `replace` retires only earlier **uploaded** PDFs; the engine PDF stays listed first.
- The GDT copy is listed only when there is no engine PDF row at all.
- Uploads are stored as `<owner>/invoices/<yyyy>/<MM>/<uuid>.<ext>` (upload date) and named as the user's file.

## Expected Behavior

- The record shows **one PDF slot**: the uploaded PDF when there is one (it replaces the provider PDF), otherwise
  the provider PDF.
- A file the DB points at but this server does not have keeps `size: null` (web shows "chưa có").
- When the provider PDF is not on this server and nothing was uploaded, the GDT copy is listed so it can be viewed.
- Uploaded invoice XML / PDF are named and stored like the engine's layout:
  `<owner>/invoices/<company MST>/<yyyy>/<MM>/<seller MST>/<mẫu>_<ký hiệu>_<số>.<ext>` (invoice date), file name
  `<mẫu>_<ký hiệu>_<số>.<ext>`; a second upload of the same invoice gets `_2`, `_3` … on disk.

## Scope

`InvoiceFileService.list` / `upload`; `StoredFiles` (new save with a given relative path); tests.

## Out of Scope

Engine tables (still read only); bulk uploads (`winv_upld_*`); attachments keep their own name; moving files
between machines (operations).

## Database Impact

NONE — no schema or data change. New uploads only get a different relative path in `winv_invc_file.stor_path`.

## API Impact

`GET /invoices/{id}` / `/files`: when an uploaded PDF exists the `pdf` (engine) item is no longer listed; the
`pdf-gdt` item is also listed when the engine PDF file is missing on this server. Uploaded file `name` is now
`<mẫu>_<ký hiệu>_<số>.pdf|xml`. Additive / narrowing only; no field changes.

## Implementation

- `InvoiceFileService.list`: XML (engine) → **one PDF slot** (latest uploaded PDF, else the engine PDF with
  `size = engineFileSize(...)`, `null` when not on this server) → other uploads (XML, attachments) → `pdf-gdt` when
  there is no uploaded PDF and no engine PDF file on this server.
- `InvoiceFileService.upload`: invoice PDF / XML named `standardName(ref, ext)` = `<mẫu>_<ký hiệu>_<số><ext>`,
  stored with `StoredFiles.saveInvoiceFile(owner, company MST, issue date, seller MST, name, bytes)`.
  Attachments unchanged.
- `StoredFiles.saveInvoiceFile`: `<owner>/invoices/<company MST>/<yyyy>/<MM>/<seller MST>/<name>`; existing file →
  `_2`, `_3` … (`CREATE_NEW`, never overwrites); still inside the storage root (`assertWithinRoot`).
- Contract note added to `docs/features/addon-service/weinvoice/interface-specification.md` (`InvoiceFileItem`).

## Files Changed

`common/StoredFiles.java`, `service/InvoiceFileService.java`, `InvoiceFileServiceTest.java`,
`StoredFilesInvoiceLayoutTest.java` (new), `interface-specification.md`, this record, the test-result record.

## Verification

### Automated Tests

- Test: `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` — Result: PASS, 110 tests, 0 failures.

### Regression Review

- Areas checked: `has_pdf` / KPIs / export ZIP do not use `list()` (unchanged); download of `u<n>` reads
  `stor_path` as before, so old uploads (uuid paths) still download; engine `IMPORT` reads the upload by its stored
  relative path (unchanged contract).
- Result: no other caller depends on the engine `pdf` item being listed next to an upload.

### Manual Verification

- Environment: .13 — NOT RUN (needs this build deployed).

## Test Evidence

[WeInvoice Uploaded PDF Replaces Provider PDF — Test Result](../testing/results/weinvoice-uploaded-pdf-replaces-provider-pdf-test-result-2026-11-11.md)
— PASS (unit), regression coverage YES.

## Known Limitations / Follow-up

- Files fetched by another machine's engine stay unreadable on this server ("chưa có") until the engine output is
  copied over or the engine runs on one machine only (operations).
- The GDT copy is only rendered where the export ZIP is; for 1C26TWE-4 the ZIP is on the laptop.
- Uploads made before this change keep their uuid file names on disk.
