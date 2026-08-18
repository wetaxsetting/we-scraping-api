# Payment Excel Download — Refactor Parity Audit

**System:** Refactor (wecms-api + wecms-web)
**Date:** 2026-06-30
**Scope:** Excel **DOWNLOAD** (template-based generated report) for all 5 payment types, across all 4 surfaces.
**Mode:** Documentation-only. No source code was modified.
**Auditor:** Claude Code (read-only verification of source + build/typecheck + template integrity).

---

## 1. Purpose & Scope

Audit the refactored **Payment Excel Download** feature to confirm both the backend API (Spring Boot, `wecms-api/`) and the frontend WEB (Next.js, `wecms-web/`) are fully implemented and wired across every download surface.

Payment types verified:

1. Internal Transfer
2. Vendor Payment
3. Expense Payment
4. Payroll Payment
5. Overseas Payment

Surfaces verified per type:

- **Scheduled List** page — toolbar/row download
- **Scheduled Detail** page — file-card download
- **Result List** page — toolbar/row download
- **Result Detail** page — file-card download

**This audit covers Excel DOWNLOAD only** — the generated, locale-aware, template-based report. Excel **UPLOAD** (import) is out of scope. The legacy uploaded-source file streaming endpoints (`GET /{id}/file`, `GET /scheduled/{id}/file`) are explicitly distinguished from the report download and are **not** part of this feature; they are noted only to avoid confusion.

---

## 1a. Verification Matrix (smoke-test index)

| Payment | API | WEB | Templates | 4 Download Surfaces | Smoke Checklist | Result |
|---------|-----|-----|-----------|---------------------|-----------------|--------|
| Internal Transfer | ✅ | ✅ | ✅ | ✅ | [internal-transfer-excel-download-smoke-test.md](internal-transfer-excel-download-smoke-test.md) | PASS |
| Vendor Payment | ✅ | ✅ | ✅ | ✅ | [vendor-payment-excel-download-smoke-test.md](vendor-payment-excel-download-smoke-test.md) | PASS |
| Expense Payment | ✅ | ✅ | ✅ | ✅ | [expense-payment-excel-download-smoke-test.md](expense-payment-excel-download-smoke-test.md) | PASS |
| Payroll | ✅ | ✅ | ✅ | ✅ | [payroll-payment-excel-download-smoke-test.md](payroll-payment-excel-download-smoke-test.md) | PASS |
| Overseas Payment | ✅ | ✅ | ✅ | ✅ | [overseas-payment-excel-download-smoke-test.md](overseas-payment-excel-download-smoke-test.md) | PASS |

> "Result" = implementation present + structurally consistent (source, endpoints, 15 templates, and all 20 frontend surfaces verified on disk; backend compile + frontend typecheck clean — see §6). It is **not** a live SIT sign-off — the per-module smoke checklists above are the pre-SIT runbooks that confirm end-to-end behaviour against real data.

### Common download surfaces (every type)

1. **Scheduled List** — per-row download glyph → scheduled-scoped endpoint
2. **Scheduled Detail** — file-card download → scheduled-scoped endpoint
3. **Result List** — per-row download glyph → result-scoped endpoint
4. **Result Detail** — file-card download → result-scoped endpoint

---

## 2. Verification Method

- **Backend source** read under `wecms-api/src/main/java/com/kosign/wecms/domain/payment/**` — services, controllers, route mappings, template loading, locale selection, column layout.
- **Frontend source** read under `wecms-web/lib/api/**` and `wecms-web/components/payment/**` — API client functions, endpoint URL strings, page handlers, locale/filename handling.
- **Endpoint matching** cross-checked FE call string ↔ BE `@RequestMapping` + `@GetMapping` directly from source (not inferred). Both the plural `/results/` and singular `/result/` variants were confirmed against the actual controller class-level mapping.
- **Wiring confirmation:** grep confirmed all 20 page components (5 types × 4 surfaces) directly reference their report download function.
- **Templates:** all 15 `.xlsx` files validated as real, non-corrupt OOXML workbooks (valid ZIP containing `xl/workbook.xml`, `xl/worksheets/sheet1.xml`).
- **Builds:** backend `gradlew compileJava compileTestJava` and frontend `npx tsc --noEmit` both run clean.

---

## 3. Backend API

### 3.1 Endpoint inventory (verified from source)

| Payment Type | Class-level `@RequestMapping` | Scheduled report `@GetMapping` | Result report `@GetMapping` | Result controller |
|---|---|---|---|---|
| Internal Transfer | `/api/v1/payment/internal-transfer` (scheduled) · `/api/v1/payment/internal-transfer/results` (result) | `/{id}/report` ([InternalTransferController.java:278](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L278)) | `/{id}/report` → `/results/{id}/report` ([TransferResultController.java:42,109](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/TransferResultController.java#L109)) | `TransferResultController` (separate, mapped at `…/results`) |
| Vendor Payment | `/api/v1/payment/vendor-payment` | `/{id}/report` (`VendorPaymentController:177`) | `/results/{id}/report` (`VendorPaymentController:230`) | same controller |
| Expense Payment | `/api/v1/payment/expense-payment` | `/{id}/report` (`ExpensePaymentController:178`) | `/results/{id}/report` (`ExpensePaymentController:231`) | same controller |
| Payroll Payment | `/api/v1/payment/payroll-payment` | `/{id}/report` (`PayrollPaymentController:161`) | `/results/{id}/report` (`PayrollPaymentController:205`) | same controller |
| Overseas Payment | `/api/v1/payment/overseas-payment` | `/{id}/report` ([OverseasPaymentController.java:480](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/controller/OverseasPaymentController.java#L480)) | `/result/{id}/report` (singular) ([OverseasPaymentController.java:496](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/controller/OverseasPaymentController.java#L496)) | same controller |

> **Path note (verified, not a defect):** the *result* report path differs by type. Internal Transfer, Vendor, Expense, and Payroll use **plural** `/results/{id}/report`; Overseas uses **singular** `/result/{id}/report`. This is intentional — each result endpoint sits under that type's existing result-list mapping (Internal Transfer's is a dedicated `TransferResultController` mapped at `…/results`; Overseas reuses its `…/result` family). **The frontend call strings match each backend variant exactly** (confirmed in §4), so there is no broken route. An earlier exploration pass mis-reported the Internal Transfer result path as singular; direct source inspection of `TransferResultController.java:42` confirms it is **plural** `/results`, matching the FE.

### 3.2 Service classes & template loading

| Payment Type | Service class (file) | Loads via | Template folder | Column count |
|---|---|---|---|---|
| Internal Transfer | `internaltransfer/service/InternalTransferDownloadService.java` | `ClassPathResource` + Apache POI `WorkbookFactory` | `templates/internal-transfer/` | 21 (A–U) |
| Vendor Payment | `vendorpayment/service/VendorPaymentDownloadService.java` | `ClassPathResource` + POI | `templates/vendor-payment/` | 22 (A–V) |
| Expense Payment | `expensepayment/service/ExpensePaymentDownloadService.java` | `ClassPathResource` + POI | `templates/expense-payment/` | 22 (A–V) |
| Payroll Payment | `payrollpayment/service/PayrollPaymentDownloadService.java` | `ClassPathResource` + POI | `templates/payroll-payment/` | 20 (A–T) |
| Overseas Payment | `payment/service/OverseasPaymentDownloadService.java` | `ClassPathResource` + POI | `templates/overseas-payment/` | 22 (A–V) |

All five services load a **locale-specific template** (`…_EN.xlsx` / `…_KO.xlsx` / `…_VI.xlsx`) by appending the resolved language to the template path, then populate cells via POI while preserving the template's existing cell styles. They are **template-based report writers**, not the generic `ExcelExportService` (which is the column-list exporter used for the result-list export toolbar, a different feature).

- Header / data rows: Internal Transfer, Vendor, Expense, Payroll → header at row 3 (0-based index 3 is the first data row). Overseas → header at row 4, first data row index 4, with a single-currency total written to row 3.
- Overseas additionally does currency-aware amount formatting (`#,##0` for VND, `#,##0.00` otherwise) and fully localizes derived strings (Status / Fee Payer / Approval Status) in EN/KO/VI; it unions file-batch (type **E**, `OVRS_PYMN_SUMR`) and manual (type **M**, `OVRS_PYMN_MSTR`) rows.

### 3.3 Locale EN/KO/VI selection

Confirmed for all five: each service derives the filename suffix from the request language and selects the matching workbook. The frontend passes the locale on every call (`?lang=<locale>`), and the FE fallback filename is also locale-suffixed (§4). EN/KO/VI templates exist on the classpath for all five (§5).

### 3.4 Uploaded-source endpoints (out of scope, noted)

Each type also exposes a legacy **uploaded-file** streaming endpoint that is *not* the report download:
`internal-transfer/{id}/file`, `vendor-payment/{id}/file`, `expense-payment/{id}/file`, `payroll-payment/{id}/file`, `overseas-payment/scheduled/{id}/file`. These remain as import-source endpoints and are intentionally **not** wired to the user-facing download action.

---

## 4. Frontend WEB

### 4.1 API client functions (verified from source)

| Payment Type | Scheduled fn | Result fn | API client file |
|---|---|---|---|
| Internal Transfer | `downloadScheduledTransferReport` → `…/internal-transfer/{id}/report` | `downloadTransferResultReport` → `…/internal-transfer/results/{id}/report` | `lib/api/internalTransfer.ts` (L337, L356) |
| Vendor Payment | `downloadVendorScheduledReport` → `…/vendor-payment/{id}/report` | `downloadVendorResultReport` → `…/vendor-payment/results/{id}/report` | `lib/api/vendorPayment.ts` |
| Expense Payment | `downloadExpenseScheduledReport` → `…/expense-payment/{id}/report` | `downloadExpenseResultReport` → `…/expense-payment/results/{id}/report` | `lib/api/expensePayment.ts` |
| Payroll Payment | `downloadPayrollScheduledReport` → `…/payroll-payment/{id}/report` | `downloadPayrollResultReport` → `…/payroll-payment/results/{id}/report` | `lib/api/payrollPayment.ts` |
| Overseas Payment | `downloadOverseasPaymentReport` → `…/overseas-payment/{id}/report` | `downloadOverseasPaymentResultReport` → `…/overseas-payment/result/{id}/report` (singular) | `lib/api/payment.ts` (L438, L452) |

- Internal Transfer / Vendor / Expense / Payroll share the `streamReportDownload(url, lang, fallbackName)` helper in `internalTransfer.ts` (L283). Overseas uses its own `streamOverseasReport(url, lang)` helper in `payment.ts` (L411) — functionally equivalent.
- **Locale:** every function passes `?lang=<locale>` to the API. ✅
- **Filename:** all parse the `Content-Disposition` header (RFC 5987 `filename*=UTF-8''…`, with plain `filename="…"` fallback for Overseas) and fall back to a locale-suffixed name (`WeCMS_<Type>_download_<LANG>.xlsx`). ✅

### 4.2 Surface wiring (all 20 components confirmed by grep)

Each of the following page components **directly references** its report download function (verified — not via inferred shared shells):

| Payment Type | Scheduled List | Scheduled Detail | Result List | Result Detail |
|---|---|---|---|---|
| Internal Transfer | `ScheduledTransferPage.tsx` | `ScheduledTransferDetailPage.tsx` | `TransferResultPage.tsx` | `TransferResultDetailPage.tsx` |
| Vendor Payment | `vendor-payment/ScheduledPaymentPage.tsx` | `vendor-payment/ScheduledPaymentDetailPage.tsx` | `vendor-payment/PaymentResultPage.tsx` | `vendor-payment/PaymentResultDetailPage.tsx` |
| Expense Payment | `expense-payment/ScheduledPaymentPage.tsx` | `expense-payment/ScheduledPaymentDetailPage.tsx` | `expense-payment/PaymentResultPage.tsx` | `expense-payment/PaymentResultDetailPage.tsx` |
| Payroll Payment | `payroll/ScheduledPaymentPage.tsx` | `payroll/ScheduledPaymentDetailPage.tsx` | `payroll/PaymentResultPage.tsx` | `payroll/PaymentResultDetailPage.tsx` |
| Overseas Payment | `overseas-payment/ScheduledPaymentPage.tsx` | `overseas-payment/ScheduledPaymentDetailPage.tsx` | `overseas-payment/PaymentResultPage.tsx` | `overseas-payment/PaymentResultDetailPage.tsx` |

(All under `wecms-web/components/payment/`.) Scheduled surfaces call the scheduled report fn; Result surfaces call the result report fn. ✅

### 4.3 Old / broken routes after refactor

- **No broken routes.** Every FE call string matches a live BE mapping (including the plural-vs-singular result-path variants).
- **Dead-but-intentional:** `downloadOverseasPaymentFile` (`payment.ts:381`) and the `downloadOriginal*Excel` helpers target the uploaded-source `…/file` endpoints. They are **defined but not called from any page handler** — kept deliberately as import-source utilities, consistent with the refactor design. Not a defect.

---

## 5. Template Verification

All under `wecms-api/src/main/resources/templates/`. Every file validated as a real OOXML workbook (valid ZIP, contains `xl/workbook.xml` + `xl/worksheets/sheet1.xml`), non-empty.

| Payment Type | Folder | EN | KO | VI | Sizes EN/KO/VI (bytes) | Valid workbook? |
|---|---|---|---|---|---|---|
| Internal Transfer | `internal-transfer/` | `WeCMS_Internal_Transfer_download_EN.xlsx` | `…_KO.xlsx` | `…_VI.xlsx` | 23769 / 21349 / 21416 | ✅ |
| Vendor Payment | `vendor-payment/` | `WeCMS_Vendor_Payment_download_EN.xlsx` | `…_KO.xlsx` | `…_VI.xlsx` | 23915 / 21472 / 21553 | ✅ |
| Expense Payment | `expense-payment/` | `WeCMS_Expense_Payment_download_EN.xlsx` | `…_KO.xlsx` | `…_VI.xlsx` | 23899 / 21477 / 21546 | ✅ |
| Payroll Payment | `payroll-payment/` | `WeCMS_Payroll_download_EN.xlsx` | `…_KO.xlsx` | `…_VI.xlsx` | 26794 / 26559 / 22984 | ✅ |
| Overseas Payment | `overseas-payment/` | `WeCMS_Overseas_Payment_download_EN.xlsx` | `…_KO.xlsx` | `…_VI.xlsx` | 11939 / 12210 / 12681 | ✅ |

- **Column count matches implementation:** IT 21 / Vendor 22 / Expense 22 / Payroll 20 / Overseas 22 (service constants in §3.2).
- **Header row preserved:** services populate from the configured first-data-row downward and reuse the template's header row and per-column cell styles — header is not overwritten.
- **Classpath loading works:** all 15 files resolve via `ClassPathResource` (confirmed present on the resource path; `processResources`/compile clean).
- **Filename naming note:** Payroll templates drop the word "Payment" (`WeCMS_Payroll_download_*`); the service uses the matching prefix, so this is consistent, not a mismatch.

---

## 6. Build / Checks

| Check | Command | Result |
|---|---|---|
| Backend compile (main + test sources) | `./gradlew compileJava compileTestJava -q` | **PASS** (exit 0, no warnings/errors) |
| Frontend typecheck | `npx tsc --noEmit` | **PASS** (exit 0, no type errors) |
| Template integrity | ZIP + `xl/workbook.xml` check on all 15 `.xlsx` | **PASS** (all valid) |

Notes:
- Backend `compileJava`/`compileTestJava` confirms the controllers, services, and their tests compile against current signatures. It does **not** execute the download-service unit tests (no `test` task run in this audit), but the relevant tests (`*DownloadServiceTest`, controller tests) were added and passing per the per-feature implementation records.
- No files were modified except this audit document.

---

## 7. Payment-Type Matrix

| Payment Type | Scheduled List | Scheduled Detail | Result List | Result Detail | Backend API | WEB Wiring | Template | Result |
|---|---|---|---|---|---|---|---|---|
| Internal Transfer | PASS | PASS | PASS | PASS | PASS | PASS | PASS | **PASS** |
| Vendor Payment | PASS | PASS | PASS | PASS | PASS | PASS | PASS | **PASS** |
| Expense Payment | PASS | PASS | PASS | PASS | PASS | PASS | PASS | **PASS** |
| Payroll Payment | PASS | PASS | PASS | PASS | PASS | PASS | PASS | **PASS** |
| Overseas Payment | PASS | PASS | PASS | PASS | PASS | PASS | PASS | **PASS** |

---

## 8. Endpoint Matrix

| Payment Type | Surface | Frontend Function / File | API Endpoint | Backend Controller | Service | Result |
|---|---|---|---|---|---|---|
| Internal Transfer | Scheduled List | `downloadScheduledTransferReport` · `ScheduledTransferPage.tsx` | `GET /api/v1/payment/internal-transfer/{id}/report` | `InternalTransferController` | `InternalTransferDownloadService` | PASS |
| Internal Transfer | Scheduled Detail | `downloadScheduledTransferReport` · `ScheduledTransferDetailPage.tsx` | `GET /api/v1/payment/internal-transfer/{id}/report` | `InternalTransferController` | `InternalTransferDownloadService` | PASS |
| Internal Transfer | Result List | `downloadTransferResultReport` · `TransferResultPage.tsx` | `GET /api/v1/payment/internal-transfer/results/{id}/report` | `TransferResultController` | `InternalTransferDownloadService` | PASS |
| Internal Transfer | Result Detail | `downloadTransferResultReport` · `TransferResultDetailPage.tsx` | `GET /api/v1/payment/internal-transfer/results/{id}/report` | `TransferResultController` | `InternalTransferDownloadService` | PASS |
| Vendor Payment | Scheduled List | `downloadVendorScheduledReport` · `ScheduledPaymentPage.tsx` | `GET /api/v1/payment/vendor-payment/{id}/report` | `VendorPaymentController` | `VendorPaymentDownloadService` | PASS |
| Vendor Payment | Scheduled Detail | `downloadVendorScheduledReport` · `ScheduledPaymentDetailPage.tsx` | `GET /api/v1/payment/vendor-payment/{id}/report` | `VendorPaymentController` | `VendorPaymentDownloadService` | PASS |
| Vendor Payment | Result List | `downloadVendorResultReport` · `PaymentResultPage.tsx` | `GET /api/v1/payment/vendor-payment/results/{id}/report` | `VendorPaymentController` | `VendorPaymentDownloadService` | PASS |
| Vendor Payment | Result Detail | `downloadVendorResultReport` · `PaymentResultDetailPage.tsx` | `GET /api/v1/payment/vendor-payment/results/{id}/report` | `VendorPaymentController` | `VendorPaymentDownloadService` | PASS |
| Expense Payment | Scheduled List | `downloadExpenseScheduledReport` · `ScheduledPaymentPage.tsx` | `GET /api/v1/payment/expense-payment/{id}/report` | `ExpensePaymentController` | `ExpensePaymentDownloadService` | PASS |
| Expense Payment | Scheduled Detail | `downloadExpenseScheduledReport` · `ScheduledPaymentDetailPage.tsx` | `GET /api/v1/payment/expense-payment/{id}/report` | `ExpensePaymentController` | `ExpensePaymentDownloadService` | PASS |
| Expense Payment | Result List | `downloadExpenseResultReport` · `PaymentResultPage.tsx` | `GET /api/v1/payment/expense-payment/results/{id}/report` | `ExpensePaymentController` | `ExpensePaymentDownloadService` | PASS |
| Expense Payment | Result Detail | `downloadExpenseResultReport` · `PaymentResultDetailPage.tsx` | `GET /api/v1/payment/expense-payment/results/{id}/report` | `ExpensePaymentController` | `ExpensePaymentDownloadService` | PASS |
| Payroll Payment | Scheduled List | `downloadPayrollScheduledReport` · `ScheduledPaymentPage.tsx` | `GET /api/v1/payment/payroll-payment/{id}/report` | `PayrollPaymentController` | `PayrollPaymentDownloadService` | PASS |
| Payroll Payment | Scheduled Detail | `downloadPayrollScheduledReport` · `ScheduledPaymentDetailPage.tsx` | `GET /api/v1/payment/payroll-payment/{id}/report` | `PayrollPaymentController` | `PayrollPaymentDownloadService` | PASS |
| Payroll Payment | Result List | `downloadPayrollResultReport` · `PaymentResultPage.tsx` | `GET /api/v1/payment/payroll-payment/results/{id}/report` | `PayrollPaymentController` | `PayrollPaymentDownloadService` | PASS |
| Payroll Payment | Result Detail | `downloadPayrollResultReport` · `PaymentResultDetailPage.tsx` | `GET /api/v1/payment/payroll-payment/results/{id}/report` | `PayrollPaymentController` | `PayrollPaymentDownloadService` | PASS |
| Overseas Payment | Scheduled List | `downloadOverseasPaymentReport` · `ScheduledPaymentPage.tsx` | `GET /api/v1/payment/overseas-payment/{id}/report` | `OverseasPaymentController` | `OverseasPaymentDownloadService` | PASS |
| Overseas Payment | Scheduled Detail | `downloadOverseasPaymentReport` · `ScheduledPaymentDetailPage.tsx` | `GET /api/v1/payment/overseas-payment/{id}/report` | `OverseasPaymentController` | `OverseasPaymentDownloadService` | PASS |
| Overseas Payment | Result List | `downloadOverseasPaymentResultReport` · `PaymentResultPage.tsx` | `GET /api/v1/payment/overseas-payment/result/{id}/report` | `OverseasPaymentController` | `OverseasPaymentDownloadService` | PASS |
| Overseas Payment | Result Detail | `downloadOverseasPaymentResultReport` · `PaymentResultDetailPage.tsx` | `GET /api/v1/payment/overseas-payment/result/{id}/report` | `OverseasPaymentController` | `OverseasPaymentDownloadService` | PASS |

---

## 9. Final Conclusion

### ✅ PASS

All five payment types — **Internal Transfer, Vendor Payment, Expense Payment, Payroll Payment, Overseas Payment** — have the Excel **DOWNLOAD** feature **fully wired end-to-end**, on **all four surfaces** (Scheduled List, Scheduled Detail, Result List, Result Detail):

- **Backend:** every type has a dedicated template-based download service, scheduled (`/{id}/report`) and result report endpoints, locale EN/KO/VI template selection via `ClassPathResource` + POI, and correct column layout (IT 21 / Vendor 22 / Expense 22 / Payroll 20 / Overseas 22). Backend compiles clean.
- **Frontend:** every type has scheduled + result report API functions, each referenced by all 4 page components (20/20 verified), locale passed on every call, and `Content-Disposition`-based filename handling with locale-suffixed fallback. Typecheck clean.
- **Endpoint contracts match exactly** on both the plural `/results/` (IT, Vendor, Expense, Payroll) and singular `/result/` (Overseas) variants — no broken routes.
- **Templates:** all 15 `.xlsx` (5 types × EN/KO/VI) present, classpath-resolvable, and confirmed valid, non-corrupt workbooks.

**No missing files, functions, endpoints, or templates.** This is Excel DOWNLOAD only; Excel UPLOAD is out of scope. The uploaded-source `…/file` endpoints and their unused `downloadOriginal*Excel` / `downloadOverseasPaymentFile` helpers are intentional import-source retentions, not defects.

### Residual (non-blocking) observations

1. **Live smoke test not performed** — the running app was not exercised against real data (data-blocked / out of scope for a static audit). The generated workbooks were validated structurally, not visually rendered with live rows.
2. **Status / type cell localization** — per prior per-feature records, some derived status cells in the non-Overseas writers may render EN-only (Overseas is fully localized). This is a known divergence tracked in the per-feature audits, not an Excel-download wiring gap.
3. **Backend unit tests not executed in this run** — only `compileJava`/`compileTestJava` were run. The download-service tests compile; running the full `test` task is recommended before release sign-off.

These are follow-ups for QA sign-off and do not change the PASS verdict for download wiring (API + WEB).

---

## 10. Workbook Verification (what each smoke test confirms)

For every module, the linked smoke checklist verifies, on a real downloaded file:

- **Workbook opens** without a "repair / corrupt" prompt (valid OOXML).
- **Localized template applied** — title + header labels in the requested locale (EN/KO/VI), KM→EN.
- **Filename** = `<template base>_{LANG}.xlsx` (e.g. `WeCMS_Vendor_Payment_download_EN.xlsx`; Payroll drops "Payment" → `WeCMS_Payroll_download_EN.xlsx`).
- **`Content-Disposition`** carries the RFC 5987 UTF-8 filename (`filename*=UTF-8''…`).
- **MIME** = `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- **`X-Content-Type-Options: nosniff`** present.
- **Structure** — correct column count + header on the correct Excel row + data from the next row + one row per transaction detail, amounts numeric, template styling preserved:

| Payment | Columns | Header (Excel row) | Data starts (Excel row) | Module-specific |
|---|---|---|---|---|
| Internal Transfer | 21 (A–U) | 3 | 4 | no total row; status EN-only; template amount format |
| Vendor Payment | 22 (A–V) | 3 | 4 | **Contact Name** col J; no total row; status EN-only |
| Expense Payment | 22 (A–V) | 3 | 4 | **Contact Name** col J; no total row; status EN-only |
| Payroll | 20 (A–T) | 3 | 4 | **Payroll Type** col O; **Deposit Account No.** col I; no Contact Name/Description/per-row Doc No; status EN-only |
| Overseas Payment | 22 (A–V) | 4 | 5 | total `A3:T3` single-currency; currency-aware amount; **localized** Status/Fee Payer/Approval Status |

Per-module column lists live in each smoke test §5.

---

## 11. Regression Verification

Confirm these remain **separate, unchanged features**, none of them invoked by the Excel-Download report buttons (each smoke test §8 exercises them per module):

| Feature | Endpoint / location | Status |
|---|---|---|
| Excel **Upload** (blank template → preview → confirm) | per-module upload/preview endpoints | unaffected |
| **Uploaded original-file** download (import source) | `GET …/{id}/file` (Overseas: `…/scheduled/{id}/file`) | still present; not wired to any Excel-Download button; helpers `downloadOriginal*Excel` / `downloadOverseasPaymentFile` defined-but-uncalled by design |
| **Result-list toolbar** export (multi-batch list export) | per-module result-list export endpoint | separate |
| **Generic Excel export** (`common/export` / `ExcelExportService`, View-Setting columns) | `POST …/export` family | separate |

---

## 12. Update log

- **2026-06-30 (this revision):** Added §1a Verification Matrix with the smoke-test index (5 new per-module checklists created under `wecms-api/docs/audits/`), §10 Workbook Verification, and §11 Regression Verification. No source, template, build, or test changes. The original verified content (§2–§9: endpoint inventory, services, templates, builds, matrices, conclusion) is unchanged.

---

## 10. References

- Per-feature implementation records (memory): `project_internal_transfer_excel_download_parity_gap.md`, `project_vendor_payment_excel_download_parity_gap.md`, `project_expense_payment_excel_download_parity_gap.md`, `project_payroll_payment_excel_download_parity_gap.md`, `project_overseas_payment_excel_download_parity_gap.md`.
- Per-feature audits under `wecms-api/docs/audits/`: `internal-transfer-excel-download-audit.md`, `vendor-payment-excel-download-audit.md`, `expense-payment-excel-download-audit.md`, `payroll-payment-excel-download-audit.md`, `overseas-payment-excel-download-audit.md`.
