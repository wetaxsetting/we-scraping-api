# WeInvoice: Invoice Downloads per UI Spec v1.1 (Excel 3 sheets, bulk PDF pack, GDT original list)

**Date:** 2026-11-25
**Type:** FEATURE
**Current Work Classification:** FEATURE
**Status:** READY FOR MANUAL COMMIT
**Approval Status:** APPROVED (user, 2026-11-25: "tiến hành dev giúp t"; bulk PDF: "xử lý server rồi tải về 1 pack")
**Implementation Status:** COMPLETE (API) · **Test Status:** PASS (unit / MockMvc; not run against a live DB or engine)
**Repository:** wecms-api (`we-scraping-api`) — frontend counterpart: `wecms-web` record
`docs/features/weinvoice/weinvoice-invoice-downloads-v1-1-2026-11-25.md` (web repository)

## Purpose

PO released WeInvoice UI Spec **v1.1** (20/11/2026, `docs/WeInvoice_UI_Spec_v1.1/` of the scraping project). It changes
only the purchase / sales invoice list: a download bar above the table, a bulk "Tải PDF (n) ▾" with 6 packaging modes,
a 3-sheet Excel, a "missing XML" dialog with two choices, and a "Bảng kê gốc (CQT)" ZIP. Business rules: file 02 §3.6,
§3.11, §3.12 of v1.1.

## Requirement

1. **Scope of every download button (§3.6 v1.1):** no row ticked → the whole list under the current filter; rows ticked →
   only those rows.
2. **Excel (§3.11):** sheets *Tổng hợp* · *Chi tiết bán ra* · *Chi tiết mua vào* (detail sheet only when that direction
   exists). Row 1 = merged disclaimer note, row 2 = header, dates as real dates `dd/mm/yyyy`, money `#,##0`, auto-filter.
   Detail = one row per XML line; discount lines negative; invoice without lines → one row
   "(không có dòng hàng trong XML)" with the totals.
3. **Bulk PDF (§3.12)** — processed on the server, delivered as **one ZIP** (user decision 2026-11-25):
   6 modes `merge` `_NOI-PDF` · `flat` · `rendered` `_BAN-THE-HIEN` · `pdf_xml` `_PDF-XML` · `by_company`
   `_THEO-CONG-TY` · `by_month` `_THEO-THANG`; ZIP name `<MST>_<MUAVAO|BANRA>_<dd-mm-yyyy>-<dd-mm-yyyy><suffix>.zip`;
   PDF name `yyyy-mm-dd-<number>-<PARTNER ASCII NO SPACES>.pdf`. Invoices without an original PDF are still packed:
   rendered copy under `_ban_the_hien_khong_co_pdf_goc\` + `doc_truoc.txt`, XML under `_thay_bang_xml\yyyy-mm\`
   (`yyyy-mm-dd-<number>-<symbol>-<seller MST>.xml`); `merge` does not merge rendered copies. Temporary failures are
   left out of the ZIP and reported for **Retry**; "failed invoices" Excel with reason + lookup link. Progress in 2 steps
   (missing XML from GDT → PDF), cancellable.
4. **Bảng kê gốc (CQT) (§3.12):** `bang-ke-goc-CQT_<from>_<to>.zip`, one Excel per month with the columns of the GDT portal
   export; month not fully synced → suffix `__THIEU`.

## Current System Behavior

- `POST /invoices/export/excel` takes `InvoiceFilter` and writes one sheet through the shared `ExcelExportService`.
- `POST /invoices/export/zip` takes `ids` (max 2000), modes `flat/by_month/by_partner/by_month_partner/per_invoice`.
- No bulk-PDF job, no GDT original list export.

## Target Behavior / Proposed Implementation (initial — refined in Implementation)

- `InvoiceFilter` scope + optional `ids` on Excel, ZIP and PDF job (ids win when present).
- Excel: a WeInvoice-specific POI writer (3 sheets) — the shared `ExcelExportService` is not changed.
- Bulk PDF job, server-side, in memory per API instance (jobs are short-lived, a file is produced once):
  `POST /invoices/export/pdf-jobs` → `{job_id}`; `GET /invoices/export/pdf-jobs/{id}` progress + items + result;
  `POST /invoices/export/pdf-jobs/{id}/cancel`; `POST /invoices/export/pdf-jobs/{id}/retry`;
  `GET /invoices/export/pdf-jobs/{id}/file` (the ZIP); `GET /invoices/export/pdf-jobs/{id}/failed-excel`.
  Step 1 asks the engine for missing XML (existing engine-request path) and waits a bounded time; step 2 resolves each
  invoice from what the engine already holds (original PDF / uploaded PDF), and classifies the rest by the engine `pdf`
  task state into fallback reasons (`no_code_invoice`, `no_lookup_code`, `not_connected`, `no_provider`) or a temporary
  failure (`gateway`). Merge + rendered copies with OpenPDF (already a dependency).
- Bảng kê gốc: rebuilt from `invoices.list_raw` (the engine keeps every GDT Excel column of the row), one workbook per
  month with one sheet per GDT list type (with code / without code / cash register × purchase / sales) because the
  cash-register list has a different column layout; `__THIEU` from `winv_sync_cvrg`.

## Scope

API only in this record: endpoints, ZIP/Excel/PDF building, engine reads. The frontend is the web record.

## Out of Scope

- Changing the engine (Python) or how it fetches PDFs from providers.
- QR code on the server-rendered fallback copy (no QR library in the build; GDT copies built by the engine carry the QR).
- Persisting jobs across API restarts.

## API Impact (planned)

New endpoints above; `ExportZipRequest` gains `filter` and `ids` becomes optional; Excel body gains optional `ids`.
Contract goes into `interface-specification.md`.

## UI Impact

Download bar, bulk PDF dialog, missing-XML dialog, GDT list button — web record.

## Database Impact

**None.** Read-only use of engine tables (`invoices`, `invoice_xml`, `invoice_pdf`, `invoice_details`, `tasks`) and
`winv_sync_cvrg`; engine requests use the existing `winv_engn_rqst` insert path. No DDL, no DBA script.

## Integration Impact

Engine: only existing FETCH_XML requests. Files are read from the storage paths the API already resolves
(`StoredFiles`); a PDF the DB knows but this server does not hold counts as missing.

## Validation / Business Rules

§3.6, §3.11, §3.12 of UI Spec v1.1 file 02 (quoted above).

## Testing Plan

Unit tests: file / ZIP naming, mode layout, fallback classification, Excel sheets (open the produced workbook with POI),
GDT list grouping + `__THIEU`, scope resolution (filter vs ids). Existing WeInvoice tests must stay green.

## Implementation

| Part | What |
|---|---|
| Scope (§3.6) | `InvoiceExportService.scopeIds` — ticked `ids` win, else every id of the filter (`InvoiceQueryService.ids`, ≤ 20 000). `ExportZipRequest`: `ids` optional (≤ 20 000, was ≤ 2 000) + `filter`. |
| Excel (§3.11) | `InvoiceWorkbook` (POI streaming): Tổng hợp (the 20 columns of the prototype, "(không mã)", Có/Thiếu), Chi tiết bán ra / mua vào (one row per `invoice_details` line, discount `tinh_chat = 3` negative, rates `10%` / `KCT` / `KKKNT` kept, no lines → "(không có dòng hàng trong XML)" + totals); row 1 merged note, row 2 header, auto-filter, frozen header, `dd/mm/yyyy`, `#,##0`. File `WeInvoice_<MuaVao|BanRa|TatCa>_<MST>_<from>_<to>.xlsx`. The shared `ExcelExportService` is untouched (still used by the ZIP summary). |
| Bảng kê gốc (§3.12) | `POST /invoices/export/cqt-list` → `CqtListWorkbook`: one workbook per month, one sheet per GDT list type in the exact GDT export layout (rows 3–4 title / "Từ ngày … đến ngày …", header row 6; 19 columns; cash-register sales 17 columns with CCCD, cash-register purchase 17 columns with seller address), values from `invoices.list_raw` (`_tthai_text` / `_ttxly_text`, else the numeric codes mapped to the portal texts). Uploads / test data left out. `__THIEU` per month from `winv_sync_cvrg` (`InvoiceQueryRepository.incompletePeriods`), on the file and on the ZIP; `doc_truoc.txt` explains the sheets. **Deviation (spec "mỗi tháng 1 file"):** still 1 file per month, but with several sheets — GDT exports each list type separately and the cash-register list has other columns, so one sheet cannot be "đúng cột" for all (checked on the GDT files the engine keeps in `output/list/`). |
| Bulk PDF (§3.12) | `PdfPackJobService` (2-thread pool, in-memory jobs, 2 h retention) + `PdfPackBuilder` (classification + ZIP layout, pure) + `PdfPackNames` (names) + `RenderedInvoicePdf` (OpenPDF fallback template, bronze double frame, embedded Unicode TTF) + `PackRepository` (engine rows, uploads, manual lookup codes, provider name, task states). Endpoints `/invoices/export/pdf-jobs[/{id}[/cancel or /retry or /file or /failed-excel]]`. Layout follows the v1.1 prototype bundle; where the prototype and the spec text differ the spec wins (`_thay_bang_xml/yyyy-mm/` also for `flat`). |
| Errors | `WBE3042` job not found / expired / other owner · `WBE3043` retry while running · `WBE3044` ZIP not ready (messages EN / VI / KO). |

## Files Changed

- `controller/WeInvoiceInvoiceController.java` — 7 endpoints.
- `dto/ExportZipRequest.java` (ids optional, filter); new `dto/PdfJobRequest.java`, `dto/PdfJobStatus.java`.
- `repository/PackRepository.java` (new).
- `service/InvoiceExportService.java` (Excel 3 sheets, `cqtList`, `scopeIds`, ZIP by filter); new `InvoiceWorkbook`, `CqtListWorkbook`, `PdfPackNames`, `PdfPackBuilder`, `PdfPackJobService`, `RenderedInvoicePdf`.
- `messages.properties`, `messages_vi.properties`, `messages_ko.properties` — WBE3042–3044.
- Tests: new `InvoiceWorkbookTest`, `PdfPackBuilderTest`, `PdfPackJobServiceTest`; `WeInvoiceInvoiceControllerTest` (constructor).
- Docs: `interface-specification.md` (Invoices table, `PdfJobStatus`, error codes).

## API Impact

New: `POST /invoices/export/cqt-list`, `POST /invoices/export/pdf-jobs`, `GET …/{job_id}`, `POST …/{job_id}/cancel`, `POST …/{job_id}/retry`, `GET …/{job_id}/file`, `GET …/{job_id}/failed-excel`. Changed: Excel content (3 sheets, new file name); ZIP accepts `filter` and up to 20 000 ids. Existing callers keep working (ids still accepted).

## Verification

### Automated Tests

`./gradlew test --offline` (JDK 21): **3 026 tests, 1 failure** — `VatDeclarationLifecycleTest` "sync accepts a period whose adjustments exceed its revenue", pre-existing (fails on clean `origin/sit`). All 229 WeInvoice tests pass, incl. 18 new: `PdfPackBuilderTest` (12: ZIP / PDF / XML names, every classification reason, live → gateway, layouts flat / pdf_xml / by_company / by_month / merge, rendered PDF opens), `InvoiceWorkbookTest` (4: 3 sheets, note + merge + date + `#,##0` + auto-filter, discount negative, rate labels, GDT list month workbook 19 / 17 columns + texts + "Từ ngày" clipping, `__THIEU` readme), `PdfPackJobServiceTest` (2: job packs original + no-code fallback without asking the engine, ZIP entries, other owner → `WBE3042`; unknown mode rejected).

### Test Scenarios

`test-scenarios.md` not updated yet — add when QA runs the flows on SIT.

### Regression Review

Excel / ZIP callers: the web keeps sending `ids` (still accepted). `InvoiceExportService` constructor gained `PackRepository` (Spring wiring; `WeInvoiceBeanConstructionTest` passes). No engine table written; engine requests use the existing `winv_engn_rqst` insert.

### Manual Verification

**Not run** — no backend / engine running on this machine during the change. On SIT: purchase list → Tải PDF ▾ → each mode; Excel with / without ticked rows; Bảng kê gốc for a month with cash-register invoices.

## Result

API side implemented and unit-tested. READY FOR MANUAL COMMIT (no commit / push made).

## Test Evidence

[weinvoice-invoice-downloads-v1-1-test-result-2026-11-25.md](../../../testing/results/weinvoice-invoice-downloads-v1-1-test-result-2026-11-25.md)

## Known Limitations / Follow-up

- Jobs live in memory of one API instance: a restart loses running jobs; several API instances behind a load balancer would need sticky sessions or a shared store.
- Live provider fetch: at most 20 invoices per run (engine `FETCH_PDF` handles one invoice per request, 3–10 s each); the rest are reported `pending` for Retry. Large periods rely on the engine having fetched PDFs in the background already.
- The server-rendered fallback copy has no QR (no QR library in the build); the engine GDT print (`gdt_pdf_path`) has it and is preferred.
- Invoices whose PDF the DB knows but this server does not hold (engine on another machine) are reported `not_on_server`.
- Bảng kê gốc rows the engine took from the GDT JSON list (not the Excel export): texts are mapped from the codes; columns GDT does not send in JSON stay empty.
