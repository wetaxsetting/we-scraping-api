# Vendor Payment — Refactor Feature Spec (STUB)

## Screens (this feature)

The four screens are sections of this hub (no separate per-screen files). File-level detail is in §1.2
(frontend) + §1.3 (routes) below; one-line summaries:

| Screen | JEX WSVC | Scope | Status |
|---|---|---|---|
| Scheduled Payment | `pymn_0001_001` | List + create scheduled vendor payment batches (Excel upload, request approval, transfer). | Implemented in code; doc stub (backfill pending) |
| Scheduled Payment Details | `pymn_0001_001` | Drill-down detail of one scheduled vendor payment batch — line items, amounts, approver chain. | Implemented in code; doc stub (backfill pending) |
| Payment Result | `pymn_0002_001` | Executed vendor-payment results with status filter; renders the totals footer (unlike Internal Transfer). | Implemented in code; doc stub (backfill pending) |
| Payment Result Details | `pymn_0002_001` | Per-transaction result detail (canonical 11-column `resultDetailColumns`). | Implemented in code; doc stub (backfill pending) |


| Field | Value |
|---|---|
| System | Refactor (Spring Boot + Next.js) |
| Slug | `vendor-payment` |
| Module | Payment → Vendor Payment |
| JEX counterparts | `pymn_0001_*` (Vendor Payment Scheduled) + `pymn_0002_*` (Vendor Payment Result) — labels confirmed at [comm_0190_003_view.jsp:550](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L550) (Scheduled) and [comm_0190_003_view.jsp:559-560](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp) (Result). |
| Discriminator | `SCDUL_PYMN_TYPE_CD = "01"` — verified at [pymn_0001_001_r001_act.jsp:69](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0001_001_r001_act.jsp#L69) + refactor enum `ScdulPymnTypeCd.VENDOR.code()`. |
| Audit `WSVC_ID` | `vendor-payment` — declared as a `static final String` at [VendorPaymentService.java:109](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java#L109). |
| REST URI base | `/api/v1/payment/vendor-payment` |
| Frontend route base | `/payment/vendor-payment` |
| Status | **Implementation status: documented retroactively against existing code as of 2026-05-22.** Backend + frontend pages exist; this stub preserves the implementation in the docs surface so it isn't lost. A full-spec follow-up MR is needed to bring this doc to parity with [internal-transfer.md](../internal-transfer/README.md). |
| Baseline | [internal-transfer/README.md](../internal-transfer/README.md) — Vendor mirrors this shape; see [payment hub README](../README.md). |

> **What this stub is for.** Internal Transfer is the locked baseline for the Payment module. Vendor Payment, Expense Payment, and Payroll were implemented in code (backend controller / service / export + frontend pages + locale entries) but never received the same per-feature spec treatment. This stub captures the file inventory + the divergences from the Internal Transfer baseline so reviewers can trace the implementation. A future MR fills in the equivalent of Internal Transfer's §3 (screen inventory) through §11 (cross-tree traceability).

---

## 1. Files in use

### 1.1 Backend ([`com.kosign.wecms.domain.payment.vendorpayment`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment))

- **Controller:** [`VendorPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/controller/VendorPaymentController.java) — 13 mapped methods covering both Scheduled and Result flows in **one** controller (unlike Internal Transfer, which splits across `InternalTransferController` + `TransferResultController`):
  - `GET /api/v1/payment/vendor-payment` — list (Scheduled) at L103
  - `GET /{id}` — detail (Scheduled) at L121
  - `GET /results` — list (Result) at L135
  - `GET /results/{id}` — detail (Result) at L154
  - `POST /results/export` — Result export at L165
  - `DELETE /{id}` — delete draft at L181
  - `POST /{id}/request-approval` at L192
  - `POST /{id}/approval-action` at L203
  - `POST /{id}/dispatch` at L215
  - `GET /withdrawal-accounts` at L235
  - `POST /uploads/parse` at L244
  - `POST /` — create draft at L255
  - `GET /{id}/file` — file download at L275
- **Services:**
  - [`VendorPaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java) — `WSVC_ID = "vendor-payment"` at L109; the `ScdulPymnTypeCd.VENDOR.code() = "01"` discriminator is applied at every repository call.
  - `VendorPaymentResultService.java` — Result-side read service.
  - `VendorPaymentExcelParser.java` — Apache POI walk + per-cell validation (uses `PymnType.VENDOR_PAYMENT("VEND")` for `BLOCKED_CHARS_RULE` lookup).
  - `VendorPaymentSortField.java` — list-page sort whitelist (implements `SortableField`).
  - `VendorPaymentResultSortField.java` — result-list sort whitelist.
- **Export:** [`VendorPaymentResultExportProvider.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/export/VendorPaymentResultExportProvider.java) — implements `ExportRowProvider`.
- **Shared infrastructure consumed** (do not fork — see [payment hub README §2](../README.md)):
  - Entities `ScdulPymnSumr` + `ScdulPymnSumrDetl` (from `internaltransfer/entity/` — historically) plus dispatch entities `TbPayTarget` / `TbPayFileMst` / `TbPayFileData` from `payment/dispatch/entity/`.
  - `SpecialCharValidator` + `BlockedCharsRuleRepository` from `payment/validation/`.
  - `ApprovalBoxService` from `payment/approvalbox/` for `request-approval` + `approval-action` delegation.
  - `AuditLogService` from `common/`.

### 1.2 Frontend ([`components/payment/vendor-payment/`](../../../../../wecms-web/components/payment/vendor-payment))

| File | Role |
|---|---|
| [`ScheduledPaymentPage.tsx`](../../../../../wecms-web/components/payment/vendor-payment/ScheduledPaymentPage.tsx) | Scheduled list page (composes `<PaymentScheduledBatchListPage>`). Exports as `VendorScheduledPaymentPage`. |
| [`ScheduledPaymentDetailPage.tsx`](../../../../../wecms-web/components/payment/vendor-payment/ScheduledPaymentDetailPage.tsx) | Scheduled detail (composes `<PaymentDetailPage>`). |
| [`PaymentResultPage.tsx`](../../../../../wecms-web/components/payment/vendor-payment/PaymentResultPage.tsx) | Result list (composes `<PaymentResultBatchListPage>` — totals footer renders, unlike Internal Transfer). |
| [`PaymentResultDetailPage.tsx`](../../../../../wecms-web/components/payment/vendor-payment/PaymentResultDetailPage.tsx) | Result detail (composes `<ResultDetailView>` with the canonical 11-column `resultDetailColumns`). |
| [`VendorPaymentUploadDialog.tsx`](../../../../../wecms-web/components/payment/vendor-payment/VendorPaymentUploadDialog.tsx) | Thin wrapper over `<PaymentExcelUploadDialog>` (which wraps app-wide `<ExcelUploadDialog>`). Supplies the Vendor-specific column set + parse/upload handlers. |
| `types.ts` | Local component types (rows, summary fields). |

### 1.3 Routes ([`app/(dashboard)/payment/vendor-payment/`](../../../../../wecms-web/app/(dashboard)/payment/vendor-payment))

| Route | page.tsx export | Imported component |
|---|---|---|
| `scheduled-payment/page.tsx` | `PaymentVendorScheduledPage` | `VendorScheduledPaymentPage` |
| `scheduled-payment/[id]/page.tsx` | `PaymentVendorScheduledDetailRoute` | `VendorScheduledPaymentDetailPage` |
| `payment-result/page.tsx` | `PaymentVendorResultPage` | `VendorPaymentResultPage` |
| `payment-result/[id]/page.tsx` | `PaymentVendorResultDetailRoute` | `VendorPaymentResultDetailPage` |

### 1.4 API client + SCREEN_IDS + locale entries

- API client: [`lib/api/vendorPayment.ts`](../../../../../wecms-web/lib/api/vendorPayment.ts) (currently exposes `requestVendorApproval`; the broader list/detail/parse/create surface lands in a follow-up MR).
- SCREEN_IDS — 4 constants registered in [`lib/viewSettingScreens.ts`](../../../../../wecms-web/lib/viewSettingScreens.ts):
  - `PAYMENT_VENDOR_PAYMENT_SCHEDULED` = `"payment-vendor-payment-scheduled"`
  - `PAYMENT_VENDOR_PAYMENT_SCHEDULED_DETAIL` = `"payment-vendor-payment-scheduled-detail"`
  - `PAYMENT_VENDOR_PAYMENT_RESULT` = `"payment-vendor-payment-result"`
  - `PAYMENT_VENDOR_PAYMENT_RESULT_DETAIL` = `"payment-vendor-payment-result-detail"`
- Locale entries: View Setting seeds shipped via [V141__vendor_payment_view_setting_seed.sql](../../../../src/main/resources/db/migration/V141__vendor_payment_view_setting_seed.sql) + [V142__vendor_payment_detail_view_setting_seed.sql](../../../../src/main/resources/db/migration/V142__vendor_payment_detail_view_setting_seed.sql) + [V150__vendor_payment_result_view_setting_seed.sql](../../../../src/main/resources/db/migration/V150__vendor_payment_result_view_setting_seed.sql). Demo seed in [V160__vendor_payment_demo_seed.sql](../../../../src/main/resources/db/migration/V160__vendor_payment_demo_seed.sql).

---

## 2. Divergence from the Internal Transfer baseline

| Concern | Internal Transfer | Vendor Payment |
|---|---|---|
| Controller split | Two controllers (Scheduled + Result) | **Single** `VendorPaymentController` covers both |
| Subroute names | `internal-transfer/scheduled-transfer` + `internal-transfer/transfer-result` | `vendor-payment/scheduled-payment` + `vendor-payment/payment-result` |
| Result list totals footer | **Omitted** (`paginationOnly` per Figma 1:11730) | **Rendered** |
| Upload-dialog column set | 8 cols — recipient acct, name, bank, network, amount, description, doc no, note | Vendor-specific — recipient bank account + vendor code among the columns (exact set in `<VendorPaymentUploadDialog>`'s `COLUMNS` array — follow-up MR documents the field list) |
| Approval-rule specifics | Standard | Vendor-specific approval rules may apply; needs PO confirmation in follow-up MR |
| Discriminator value | `"03"` | `"01"` |
| Audit `WSVC_ID` | `internal-transfer` | `vendor-payment` |
| Key-sequence helper table | `INTERNAL_TRANSFER_KEY_SEQ` (PK `(BIZ_KEY, KEY_DT)`) | Vendor maintains its own per-(tenant, day) sequence — table name TBD-by-code-inspection |

Per [payment hub README §2 + §3](../README.md), Vendor consumes the shared infrastructure verbatim (`payment/dispatch/`, `payment/validation/`, `payment/approvalbox/`, `payment/export/`).

---

## 3. Open documentation gaps (backfill in a follow-up MR)

The Internal Transfer baseline has 12 sections (Overview, Roles, Screen inventory, Data model, User flows, Validation, State machine, Non-functional, Error codes, Out of scope, Cross-tree traceability, Risk register). Vendor's equivalent is **not yet written**. Specifically:

- [ ] Section 1 — Overview & business goal (Vendor-specific use cases vs Internal Transfer).
- [ ] Section 2 — User roles & permissions table (R / U / D / T flags per `comm_0190_003_view.jsp` matrix).
- [ ] Section 3 — Screen inventory with JEX → refactor file:line citations for every `_r/_c/_u/_d` action under `pymn_0001_*` + `pymn_0002_*`.
- [ ] Section 4 — Data model. Verify the `BIZ_KEY` + `SCDUL_PYMN_NO` shape is identical to Internal Transfer (it should be — shared tables).
- [ ] Section 5 — End-to-end user flows (Vendor-specific upload → approval → dispatch → result).
- [ ] Section 6 — Field-by-field validation rules. **The upload-dialog column set is the most important undocumented piece** — Figma + the running `<VendorPaymentUploadDialog>` are the sources to cite.
- [ ] Section 7 — State machine. Vendor-specific `APPR_STTS` / `TRNF_YN` / `PROC_STAT` codes (likely identical to Internal Transfer but needs verification).
- [ ] Section 8 — Non-functional (i18n, currency, file size, audit).
- [ ] Section 9 — Error codes (which `WBE2xxx` codes Vendor allocates beyond the shared set).
- [ ] Section 10 — Out of scope (any HAENGSUNG-specific overrides? Payment Result list totals diverging from Internal Transfer is documented divergence, not out-of-scope).
- [ ] Section 11 — Cross-tree traceability table.
- [ ] Section 12 — Risk register / open questions.

Also missing supporting docs:

- [ ] `docs/refactor-wecms/migration/vendor-payment-mapping.md` — full JEX → refactor field mapping.
- [ ] `docs/refactor-wecms/backend/vendor-payment.md` — backend design doc.
- [ ] `docs/refactor-wecms/frontend/vendor-payment.md` — frontend design doc.
- [ ] `docs/refactor-wecms/database/payment/vendor-payment-schema.md` — schema doc (likely a thin overlay over `internal-transfer-schema.md` since the master/detail tables are shared).
- [ ] `docs/refactor-wecms/audits/vendor-payment-parity-checklist.md` — parity audit.

---

## 4. Verification ground truth

When backfilling the missing sections, source from (in priority order):

1. [`VendorPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/controller/VendorPaymentController.java) for the REST surface.
2. [`VendorPaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java) for the business logic + audit-key channel.
3. The `<VendorScheduledPaymentPage>` + `<VendorPaymentResultPage>` + `<VendorPaymentUploadDialog>` for the UX surface.
4. JEX actions under [`WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn) — specifically `pymn_0001_*` + `pymn_0002_*`.
5. WSVC XML under [`WECMS DEV/WECMS_ETC/xml/service/WSVC/`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC) — files matching `WSVC.pymn_0001_*` + `WSVC.pymn_0002_*`.

---

Last verified: 2026-05-22
