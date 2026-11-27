# WeInvoice: GDT Copy of the Invoice PDF as a Stand-in Until the Provider Original Arrives

**Date:** 2026-11-10
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** READY FOR SIT (pushed on user request 2026-11-10; DB column added; screen 05 not yet checked in a browser)
**Investigation Status:** COMPLETE · **Approval Status:** NOT REQUIRED (developer: "lưu lại và view lên… khi có file hd gốc của NCC thì thay thế, còn không có thì dùng tạm của Thuế") · **Implementation Status:** COMPLETE · **Test Status:** PASS (unit) · DB: COMPLETE
**Repository:** wecms-api (checked out as `we-scraping-api`); engine side in the we-scraping repository; viewer in we-scraping-web

## Problem

Invoice detail (screen 05) shows the original PDF only when the provider (NCC) original was fetched. Most
invoices have no NCC original yet (P1 data, 2026-11-02: 1.911 / 3.516), so the viewer falls back to the copy
rendered from XML, which does not look like the real invoice.

## Findings

- The GDT `export-xml` ZIP carries **no PDF** (4.402 / 4.402 ZIPs on the developer machine, 2026-11-10): always
  `invoice.xml`, `invoice.html`, `details.js`, `viewinvoice-bg.jpg`, `sign-check.jpg`. `invoice.html` is the
  GDT portal's own "view invoice" page; `details.js` draws the QR code and the signature box.
- Printing that page with Chromium (network blocked, only `file:`) gives the GDT print layout (A4, GDT's own
  print CSS; a long invoice is 2 pages exactly as when printed from the portal).

## Expected Behavior

- Engine renders `invoice.html` to PDF when it downloads the XML and stores it as the "GDT copy".
- The invoice record lists the GDT copy **only while there is no NCC original** (engine `invoice_pdf` or an
  uploaded PDF). The NCC original replaces it in the list and in the viewer.
- The GDT copy never counts as "has PDF" (KPIs, filters, "Lấy PDF gốc" button unchanged).

## Scope

- Engine (we-scraping): `gdt_pdf.py`; `web/invoice_job.py` (`save_gdt_pdf` after each XML, command
  `gdt-pdf` to backfill from saved ZIPs); `invoice_store.py`; `db/schema.sql`.
- This repository: `InvoiceDetailRepository.DetailRow.gdtPdfPath`; `InvoiceFileService.list` / `download`
  (file id `pdf-gdt`, kind `pdf_gdt`, origin `gdt`).
- Web: viewer shows the GDT copy when there is no NCC original, labelled as the GDT copy.

## Out of Scope

ZIP export of selected invoices (still exports only the NCC original); `has_pdf` and dashboard counts.

## Database Impact

- This repository: NONE.
- Engine schema (engine-owned, applied by `invoice_job.py init-db`): `ALTER TABLE invoice_xml ADD COLUMN IF NOT
  EXISTS gdt_pdf_path TEXT` — additive, nullable. **Deploy order: engine `init-db` before this backend version**
  (the detail query reads the column).

## Implementation

| Repo | Change |
|---|---|
| engine | `gdt_pdf.py` — extract only the 4 known flat names of the ZIP into a temp dir (size cap 5 MB each), open `invoice.html` in one reused Chromium with every non-`file:` request aborted, print with GDT's print CSS (`prefer_css_page_size`). `web/invoice_job.py` — `save_gdt_pdf` after each XML (`<out>/pdf_gdt/<same layout as xml>.pdf` + `invoice_xml.gdt_pdf_path`; failure only logs, the XML task still completes; `INVOICE_GDT_PDF=0` turns it off); command `gdt-pdf [--company] [--limit] [--all]` backfills from ZIPs saved in `<out>/temp/` (no GDT call; ZIP missing on this machine → counted, skipped). `invoice_store.set_gdt_pdf`, `gdt_pdf_candidates`. `db/schema.sql` — the column. |
| api | `DetailRow.gdtPdfPath` (`x.gdt_pdf_path`); `InvoiceFileService.list` adds `{id: pdf-gdt, kind: pdf_gdt, origin: gdt, deletable: false}` only when there is neither `invoice_pdf` nor an uploaded PDF; `download("pdf-gdt")` serves it from the engine output dir; `delete("pdf-gdt")` → `WBE3007`. `has_pdf`, KPIs, export ZIP unchanged. |
| web | `InvoiceFileWire.kind` accepts `pdf_gdt`; `fromDetail` keeps it out of `files[]` as `Invoice.gdtPdf` (so `hasFile(inv, "pdf")` stays false and "Lấy PDF gốc" still shows); viewer shows it when there is no own PDF, tab "Bản của Thuế (PDF)" + hint (VI/EN/KO). |

## Files Changed

- engine: `gdt_pdf.py` (new), `web/invoice_job.py`, `invoice_store.py`, `db/schema.sql`, `tests/test_gdt_pdf.py` (new)
- api: `InvoiceDetailRepository.java`, `InvoiceFileService.java`, `dto/InvoiceFileItem.java` (doc), `InvoiceFileServiceTest.java`
- web: `components/invoices/InvoiceDetailModal.tsx`, `lib/api/weinvoice/invoices.ts`, `lib/weinvoice/types.ts`, `locales/{vi,en,ko}.json`, `lib/api/weinvoice/invoices.api.test.ts`

## API Impact

`GET /invoices/{id}` and `GET /invoices/{id}/files`: new file item kind `pdf_gdt` (id `pdf-gdt`), only when no provider
original exists. `GET /invoices/{id}/files/pdf-gdt` downloads it. Additive; old clients ignore an unknown kind.

## Database Execution Status

| Environment | Required | Executor | Status | Verification |
|---|---|---|---|---|
| shared engine DB (laptop + .13) | Yes — before this backend version starts | DEV TEAM (user approved 2026-11-10) | COMPLETE 2026-11-10 | `ALTER TABLE invoice_xml ADD COLUMN IF NOT EXISTS gdt_pdf_path TEXT`; `information_schema.columns` → `[('gdt_pdf_path', 'text')]` |

## Verification

### Automated Tests

- api: `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` — PASS, 105 tests, 0 failures
  (`InvoiceFileServiceTest` 16, of which 5 new: listed without original · replaced by engine PDF · replaced by uploaded PDF ·
  download path · cannot delete).
- engine: `pytest tests` — 1162 passed, 13 skipped, 4 failed; the 4 are pre-existing captcha config / training tests
  (`test_config.py` 3 fail on a clean HEAD checkout too; `test_overfit.py`), unrelated. `tests/test_gdt_pdf.py` 8 PASS
  incl. a real Chromium render.
- web: `vitest run components/invoices lib/api/weinvoice` — PASS, 120 tests (1 new); `tsc` clean (outside stale `.next/types`), eslint clean.

### Manual Verification

- Rendering a real GDT ZIP (1C26TWE-4, Webcash): PDF matches the portal view (QR code, signature box), 2 A4 pages as GDT prints it. PASS.
- Backfill `invoice_job.py gdt-pdf --limit 20` (laptop): 17 rendered, 3 ZIP not on this machine, 0 errors, 32 s. 11.274 invoices with XML and no NCC PDF remain without a GDT copy (part of them have their ZIP on .13). PASS.
- 1C26TUN-9557 (buyer 3600790154): 3 pages, no QR — the GDT page itself has `qrcodeContent` empty for this invoice, so the portal shows none either. Not a defect.
- Screen 05 in a browser: NOT RUN — needs a backend restart on the new code + login.

## Test Evidence

[WeInvoice GDT PDF Copy — Test Result](../testing/results/weinvoice-gdt-pdf-fallback-test-result-2026-11-10.md) — PARTIAL (unit PASS, DB BLOCKED)

## Known Limitations / Follow-up

- Invoices whose ZIP was downloaded on another machine have their ZIP there; run `gdt-pdf` on that machine (.13).
- The GDT copy is not part of the ZIP export.
