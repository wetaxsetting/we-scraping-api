# Vendor Payment — Refactor Feature Spec (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

> **Frontend counterpart:** [wecms-web/docs/features/payment/vendor-payment/README.md](../../../../../wecms-web/docs/features/payment/vendor-payment/README.md) · **Sub-modules:** Scheduled Payment + Payment Result.

## Documents in this folder

| Doc | Focus |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end lifecycle (Legacy/Spring): upload → parse → create → request-approval (multipart) → approve/reject/withdraw/change-line → Banking Match → dispatch → result inquiry → export/download. Status via `wb_get_appr_stts` (`C`/`R`). |
| [database-mapping.md](database-mapping.md) | `SCDUL_PYMN_SUMR`(+detl, `'01'`), `EXPT_RESOL_*`, `TB_PAY_*`; entity↔table↔column; key-gen funnel; V156 seed. |
| [functional-specification.md](functional-specification.md) | Filters, status model, columns, actions, validation, authz, constants. |
| [interface-specification.md](interface-specification.md) | All ~21 endpoints — URI, method, `@PreAuthorize`, request/response DTOs, query params, errors. |
| [migration-guide.md](migration-guide.md) | Legacy `pymn_0001/0002` → controller/service mapping; approved deviations; Re-run = DEAD LEGACY. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING verdicts + stale-claim corrections. |
| [source-code-index.md](source-code-index.md) | Controller / services / repos / entities / DTOs + FE routes/components/client + tests. |
| [test-scenarios.md](test-scenarios.md) | Backend test inventory + manual/SIT matrix + missing coverage. |

## Screens (this feature)

The four screens are sections of this hub (no separate per-screen files). File-level detail is in §1.2
(frontend) + §1.3 (routes) below; one-line summaries:

| Screen | JEX WSVC | Scope | Status |
|---|---|---|---|
| Scheduled Payment | `pymn_0001_001` | List + create scheduled vendor payment batches (Excel upload, request approval, transfer, per-line banking match). | **Implemented full-stack** (backend + frontend) |
| Scheduled Payment Details | `pymn_0001_002` | Drill-down detail of one scheduled vendor payment batch — line items, amounts, approver chain, reporting-account picker, attachments. | **Implemented full-stack** |
| Payment Result | `pymn_0002_001` | Executed vendor-payment results with route-aware status filter; renders the totals footer (unlike Internal Transfer). | **Implemented full-stack** |
| Payment Result Details | `pymn_0002_002` | Per-transaction result detail (canonical 11-column `resultDetailColumns`). | **Implemented full-stack** |


| Field | Value |
|---|---|
| System | Refactor (Spring Boot + Next.js) |
| Slug | `vendor-payment` |
| Module | Payment → Vendor Payment |
| Discriminator | `SCDUL_PYMN_TYPE_CD = "01"` — refactor enum `ScdulPymnTypeCd.VENDOR.code()` (applied at every repository call). |
| Audit `WSVC_ID` | `vendor-payment` — declared as a `static final String WSVC_ID` at [VendorPaymentService.java:113](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java#L113). |
| REST URI base | `/api/v1/payment/vendor-payment` |
| Frontend route base | `/payment/vendor-payment` |
| Authorization | Class-level `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-scheduled')")`; Result endpoints override with `'payment-vendor-result'`. See §5. |
| Status | **Implemented full-stack** as of 2026-06-23 (this audit). Backend controller (~21 mapped methods) + 2 read services + Excel parser + dispatch executor + result export provider; frontend list + detail pages for both Scheduled and Result, plus upload dialog. |
| Baseline | [internal-transfer/README.md](../internal-transfer/README.md) — Vendor mirrors this shape and reuses its DTOs; see [payment hub README](../README.md). |

> **History.** This doc was a stub (documented retroactively 2026-05-22). The implementation has since
> advanced well past the stub's "Phase A read-only" description — Phase B (detail), Phase C (write
> actions: delete / request-approval / approval-action / dispatch / withdraw / change-approval-line),
> Phase D (Excel upload / withdrawal-accounts / reporting-account PATCH / file download), and a Banking
> Match phase (per-line match / unmatch) are all landed. This audit (2026-06-23) corrects the stub to
> match the latest code.

---

## 1. Files in use

### 1.1 Backend ([`com.kosign.wecms.domain.payment.vendorpayment`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment))

- **Controller:** [`VendorPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/controller/VendorPaymentController.java) — **one** controller covers both Scheduled and Result flows (unlike Internal Transfer, which splits across `InternalTransferController` + `TransferResultController`). Class-level `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-scheduled')")`; Result endpoints override to `'payment-vendor-result'`. Mapped methods (line numbers as of this audit):
  - `GET ` — Scheduled list at L119 (params `from_date`, `to_date`, `srch_wd`, `appr_type`, `sort_field`, `sort_direction`, `page`, `size`)
  - `GET /{id}` — Scheduled detail at L137
  - `GET /results` — Result list at L151 (params include `trnf_stts`)
  - `GET /results/{id}` — Result detail at L171
  - `POST /results/export` — Result XLSX export at L183
  - `DELETE /{id}` — delete draft at L200
  - `POST /{id}/request-approval` — **multipart** (JSON `request` part + optional `attachments`) at L213
  - `POST /{id}/approval-action` at L226
  - `DELETE /{id}/approval` — withdraw in-flight approval at L241
  - `PUT /{id}/approval-line` — change approver chain at L256
  - `PATCH /{id}/lines/{lineId}/reporting-account` at L275
  - `POST /{id}/dispatch` at L292
  - `GET /withdrawal-accounts` at L312
  - `POST /uploads/parse` — multipart Excel parse at L321
  - `POST /` — create draft (multipart: `request` + `file_data`) at L332
  - `GET /{id}/file` — original-Excel download at L352
  - Banking Match: `GET /{id}/lines/{lineId}/match/candidates` (L383), `POST /{id}/lines/{lineId}/match` (L398), `DELETE /{id}/lines/{lineId}/match` (L412), `DELETE /{id}/matches` (unmatch-all, L425)
  - Path-segment guards: `SCDUL_PYMN_NO_PATTERN` accepts both the numeric `scdul_pymn_no` **and** the user-visible `DOC_NO` (`VO_YYMMDD_NNN`); `SCDUL_PYMN_DETL_NO_PATTERN` for line ids.
- **Services:**
  - [`VendorPaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java) — Scheduled list/detail + all write actions; `WSVC_ID = "vendor-payment"` at L113; `ScdulPymnTypeCd.VENDOR.code() = "01"` discriminator at every repository call. Reuses the shared `internaltransfer.dto.*` records and the `internaltransfer.entity.*` tables.
  - [`VendorPaymentResultService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentResultService.java) — Result list/detail; route-aware `PROC_STAT` status filter (`C`/`P`); header tab-count derivation (`countByStatus`); footer currency totals via `searchDispatchedCurrencyTotals` (unfiltered by status — legacy R008 parity).
  - [`VendorPaymentExcelParser.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentExcelParser.java) — Apache POI walk + per-cell validation.
  - [`VendorDispatchExecutor.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorDispatchExecutor.java) — dispatch flow.
  - [`VendorPaymentSortField.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentSortField.java) — Scheduled-list sort whitelist (`SortableField`).
  - [`VendorPaymentResultSortField.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentResultSortField.java) — Result-list sort whitelist.
  - [`VendorPaymentFileResolver.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentFileResolver.java) + [`VendorPaymentStorageProperties.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentStorageProperties.java) + [`VendorPaymentConfig.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentConfig.java) — per-module upload-tree storage (`<root>/<biz_key>/vendor-payment/<save_file_nm>`), with a `resolveForRead` fallback to the legacy `internal-transfer/` folder.
- **Export:** [`VendorPaymentResultExportProvider.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/export/VendorPaymentResultExportProvider.java) — extends the shared `PaymentResultExportProvider`; `screenId() = "payment-vendor-payment-result"`, `erpResultTrnsTypeCd() = "04"` (ERP `VENDOR_PAYMENT_RESULT`).
- **Shared infrastructure consumed** (do not fork — see [payment hub README §2](../README.md)):
  - Entities `ScdulPymnSumr` + `ScdulPymnSumrDetl` + `ScdulPymnSumrId` from `internaltransfer/entity/`; approval-box entities `ExptResolMstr` / `ExptResolApprvrInfm` from `payment/approvalbox/entity/`.
  - Shared DTOs from `internaltransfer/dto/` (`ScheduledTransferListResponse`, `ScheduledTransferDetailResponse`, `TransferResultListResponse`, `TransferResultDetailResponse`, `CreateScheduledTransferRequest`, `RequestApprovalRequest`, `DispatchRequest`, `ApprovalActionRequest`, `ChangeApprovalLineRequest`, `UpdateReportingAccountRequest`, `WithdrawalAccountRow`, `ParseExcelResponse`, …).
  - `ApprovalBoxService` + `ApprovalBoxFanoutService` (approve/reject/withdraw/change-line + EXPT_RESOL_* fan-out), `ApprovalAttachmentWriter` + `ApprovalAttachmentLoader` (EXPT_RESOL_FILE ∪ EXPT_RESOL_PAY_EVID_FILE), `ApprovalDisplayFlagsResolver`, `ApprovalNotificationService`.
  - `DomesticPaymentLineMatchService` from `payment/match/` for the Banking Match endpoints.
  - `DocNoGenerator` (`EXPT_RESOL_MSTR.DOC_NO`), `InternalTransferKeyGenerator` (`SCDUL_PYMN_NO`), `LegacyFunctionRepository` (`wb_get_next_val` funnel for `APPR_SEQ_NO` + detail numbering; `wb_get_appr_stts`; `wb_get_bank_name_any_code`), `ErpTrnsHistoryWriter`, `AuditLogService`, `WithdrawalAccountListService`, `TransferLineDetailMapper`, `OutBankCdNormalizer`, `CutoffValidator`.

### 1.2 Frontend ([`components/payment/vendor-payment/`](../../../../../wecms-web/components/payment/vendor-payment))

| File | Role |
|---|---|
| [`ScheduledPaymentPage.tsx`](../../../../../wecms-web/components/payment/vendor-payment/ScheduledPaymentPage.tsx) | Scheduled list page. **Default export** `VendorScheduledPaymentPage` — composes `<PaymentScheduledBatchListPage>` + `<VendorPaymentUploadDialog>` + `<RequestApprovalDialog>` + `<ApprovalDetailsDialog>` (read-only / edit modes) + `<ViewSettingPopover>`. |
| [`ScheduledPaymentDetailPage.tsx`](../../../../../wecms-web/components/payment/vendor-payment/ScheduledPaymentDetailPage.tsx) | Scheduled detail. Default export `VendorScheduledPaymentDetailPage` — composes `<PaymentDetailPage>` with the reporting-account picker (`scheduledDetailColumns`). |
| [`PaymentResultPage.tsx`](../../../../../wecms-web/components/payment/vendor-payment/PaymentResultPage.tsx) | Result list. Default export `VendorPaymentResultPage` — composes `<PaymentResultBatchListPage>` (totals footer renders) + `<ViewSettingPopover>`; Excel export via shared `useExcelExport`. |
| [`PaymentResultDetailPage.tsx`](../../../../../wecms-web/components/payment/vendor-payment/PaymentResultDetailPage.tsx) | Result detail. Default export `VendorPaymentResultDetailPage` — composes `<ResultDetailView>` with the canonical 11-column `resultDetailColumns`. |
| [`VendorPaymentUploadDialog.tsx`](../../../../../wecms-web/components/payment/vendor-payment/VendorPaymentUploadDialog.tsx) | Thin wrapper over the shared Excel-upload preview dialog; supplies the Vendor parse/upload handlers (`parseVendorPaymentExcel` / `createVendorPayment`). |
| [`types.ts`](../../../../../wecms-web/components/payment/vendor-payment/types.ts) | Local row types `VendorScheduledRow` / `VendorResultRow` (+ action / highlight unions). |

### 1.3 Routes ([`app/(dashboard)/payment/vendor-payment/`](<../../../../../wecms-web/app/(dashboard)/payment/vendor-payment>))

| Route | page.tsx export | Imported component (default import) |
|---|---|---|
| `scheduled-payment/page.tsx` | `PaymentVendorScheduledPage` | `VendorScheduledPaymentPage` |
| `scheduled-payment/[id]/page.tsx` | `PaymentVendorScheduledDetailRoute` | `VendorScheduledPaymentDetailPage` |
| `payment-result/page.tsx` | `PaymentVendorResultPage` | `VendorPaymentResultPage` |
| `payment-result/[id]/page.tsx` | `PaymentVendorResultDetailRoute` | `VendorPaymentResultDetailPage` |

### 1.4 API client + SCREEN_IDS + View Setting seed

- API client: [`lib/api/vendorPayment.ts`](../../../../../wecms-web/lib/api/vendorPayment.ts) — **full surface** (not just `requestVendorApproval`): `fetchVendorScheduledList`, `fetchVendorPaymentDetail`, `fetchVendorPaymentResultList`, `fetchVendorPaymentResultDetail`, `fetchVendorWithdrawalAccounts`, `parseVendorPaymentExcel`, `createVendorPayment`, `deleteVendorPayment`, `updateVendorReportingAccount`, `dispatchVendorPayment`, `requestVendorApproval` (multipart), `actOnVendorApproval`, `withdrawVendorApproval`, `changeVendorApprovalLine`, `downloadOriginalVendorExcel`, plus the four Banking Match calls (`getVendorMatchCandidates`, `matchVendorPaymentLine`, `unmatchVendorPaymentLine`, `unmatchAllVendorPaymentLines`). Reuses the shared `internaltransfer` wire types from `lib/types/api.ts`.
- SCREEN_IDS — 4 constants registered in [`lib/viewSettingScreens.ts`](../../../../../wecms-web/lib/viewSettingScreens.ts):
  - `PAYMENT_VENDOR_PAYMENT_SCHEDULED` = `"payment-vendor-payment-scheduled"`
  - `PAYMENT_VENDOR_PAYMENT_SCHEDULED_DETAIL` = `"payment-vendor-payment-scheduled-detail"`
  - `PAYMENT_VENDOR_PAYMENT_RESULT` = `"payment-vendor-payment-result"`
  - `PAYMENT_VENDOR_PAYMENT_RESULT_DETAIL` = `"payment-vendor-payment-result-detail"`
- View Setting seeds: **consolidated into the single manual-DBA script** [V156__sys_scr_option_wecms.sql](../../../../src/main/resources/db/migration/V156__sys_scr_option_wecms.sql) (49 vendor-payment rows across the 4 screen slugs). The older per-feature `V141/V142/V150/V160` migrations referenced by the stub no longer exist — they were folded into V156 during the 2026-06-09 migration consolidation. Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"PROD DB Table Scope" + the SYS_SCR_OPTION_WECMS seed strategy, V156 is a DBA-run file, not Flyway-applied.

---

## 2. Divergence from the Internal Transfer baseline

| Concern | Internal Transfer | Vendor Payment |
|---|---|---|
| Controller split | Two controllers (Scheduled + Result) | **Single** `VendorPaymentController` covers both |
| Subroute names | `internal-transfer/scheduled-transfer` + `internal-transfer/transfer-result` | `vendor-payment/scheduled-payment` + `vendor-payment/payment-result` |
| Result list totals footer | **Omitted** (`paginationOnly` per Figma 1:11730) | **Rendered** (`paginationWithTotals`) |
| Discriminator value | `"03"` | `"01"` |
| Audit `WSVC_ID` | `internal-transfer` | `vendor-payment` |
| Storage subfolder | `internal-transfer/` | `vendor-payment/` (read falls back to `internal-transfer/`) |
| ERP `TRNS_TYPE_CD` | Internal codes | `02` (Vendor Scheduled Payment) on upload/delete; `04` (Vendor Payment Result) on dispatched-file download (F2 'W' audit row) |

Vendor consumes the shared infrastructure verbatim (`payment/approvalbox/`, `payment/match/`, `payment/common/`, `payment/dispatch/`, `payment/export/`, `internaltransfer/dto` + `internaltransfer/entity`).

---

## 3. Data model

- Master/detail tables are the **shared** `SCDUL_PYMN_SUMR` / `SCDUL_PYMN_SUMR_DETL` (from `internaltransfer/entity/`), partitioned by `SCDUL_PYMN_TYPE_CD = '01'`.
- Approval-side rows live in `EXPT_RESOL_MSTR` (carries `DOC_NO`, `DRAFT_USER_NM`, `APPR_TYPE_CD`, `PAY_DATE`, `TITL`), `EXPT_RESOL_APPRVR_INFM` (per-approver chain), `EXPT_RESOL_DETL`, `EXPT_RESOL_APPR_LINE`, plus attachments in `EXPT_RESOL_FILE` ∪ `EXPT_RESOL_PAY_EVID_FILE`.
- **No `SCDUL_PYMN_SUMR.APPR_STTS` column** — legacy JEX has none. Approval status is derived at read time via the legacy DB function `wb_get_appr_stts(...)` through `LegacyFunctionRepository` (codes: `C` approved, `R` rejected — **not** `X`).
- **Key generation** (all through the `wb_get_next_val` funnel / documented exceptions per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"DB Function / Legacy Function Policy"): `APPR_SEQ_NO` → `LegacyFunctionRepository.nextApprSeqNo` (global `sq_appr_seq_no`); `SCDUL_PYMN_NO` → `InternalTransferKeyGenerator` (date-prefixed, OD-2 exception); `SCDUL_PYMN_DETL_NO` → seeded once via `nextScdulPymnDetlNo` then incremented in Java; `EXPT_RESOL_MSTR.DOC_NO` → `DocNoGenerator` (Java-side exception, env-set-driven).

---

## 4. Business-logic notes (verified against latest code)

- **Status derivation (Scheduled).** `appr_type` filter IS applied server-side by `ScdulPymnSumrRepository.search` (00 Not Requested / 04 Pending / 03 In Progress / 01 Approved / 02 Rejected) via `wb_get_appr_stts`.
- **Result status filter (route-aware, C2).** Completed → `C`, In Progress → `P` (route-aware `PROC_STAT` EXISTS inside `searchDispatched`), **not** the old `TRNF_YN`-shaped `Y`/`N`. The frontend maps the Completed / In-Progress checkboxes to `C`/`P`. The footer totals are intentionally computed **unfiltered** by status (legacy R008 parity).
- **Approval Ref (F4a/C1).** `DOC_NO`'s canonical home is `EXPT_RESOL_MSTR.DOC_NO` (resolved by `APPR_SEQ_NO`), **never** `SCDUL_PYMN_SUMR.ERP_FILE_NUM`. Surfaced as the clickable Approval Ref on both list + detail; clicking opens the shared Approval Details popup.
- **F2 download 'W' audit (Result).** A dispatched-batch original-file download (`trnf_yn='Y'`) writes an ERP `VENDOR_PAYMENT_RESULT` ('W', code `04`) audit row; the writer is `REQUIRES_NEW` + swallows failures.
- **Per-line tab counts (Result detail, G1).** `countByStatus` buckets resolved lines into `[success, failed, inProgress]`, matching the shared frontend `deriveResultStatus.ts` rule.

---

## 5. Authorization

- Class-level `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-scheduled')")` gates the Scheduled flow (list / detail / delete / request-approval / approval-action / withdraw / change-line / reporting-account / dispatch / withdrawal-accounts / parse / create / file / Banking Match — those without their own override inherit the class gate).
- Result endpoints (`/results`, `/results/{id}`, `/results/export`) override with `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-result')")`.
- **Note the two id namespaces:** the menu permission codes are `payment-vendor-scheduled` / `payment-vendor-result`, whereas the View Setting / export `screenId`s are `payment-vendor-payment-scheduled` / `payment-vendor-payment-result`. They are deliberately distinct surfaces — do not conflate.

---

## 6. Tests

Backend tests under [`src/test/.../payment/vendorpayment/service/`](../../../../src/test/java/com/kosign/wecms/domain/payment/vendorpayment/service):
`VendorPaymentRequestApprovalAttachmentTest`, `VendorPaymentResultDownloadAuditTest` (F2 'W' audit), `VendorPaymentResultFooterTotalsTest`, `VendorPaymentResultParityTest`, `VendorPaymentSortFieldTest`.

**Coverage gaps (MISSING):** no test for the Scheduled-list `appr_type` filter, the `createDraft` key-race retry path, the Banking Match endpoints, or the reporting-account PATCH. No frontend (component) tests for the Vendor pages.

---

## 7. Verification ground truth

Source from (in priority order):

1. [`VendorPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/controller/VendorPaymentController.java) for the REST surface.
2. [`VendorPaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java) + [`VendorPaymentResultService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentResultService.java) for business logic + audit channel.
3. The frontend pages + [`lib/api/vendorPayment.ts`](../../../../../wecms-web/lib/api/vendorPayment.ts) for the UX + wire surface.

---

## Legacy & Migration Traceability

- **Sub-module WSVC mapping:** Scheduled Payment = `pymn_0001_*` ; Payment Result = `pymn_0002_*`.
- **JEX counterparts:** `pymn_0001_*` (Vendor Payment Scheduled) + `pymn_0002_*` (Vendor Payment Result).

---

Last verified: 2026-06-23 (against latest implementation code)
