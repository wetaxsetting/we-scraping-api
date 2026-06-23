# Vendor Payment — Source Code Index

Backend package root: `wecms-api/src/main/java/com/kosign/wecms/domain/payment/vendorpayment/`. Frontend: `wecms-web/`. One-line role per file. Vendor reuses the shared `internaltransfer.*` / `approvalbox.*` / `match.*` / `dispatch.*` / `export.*` / `common.*` infrastructure — those are listed under "Shared collaborators".

## Backend — Controller

| File | Role |
|---|---|
| [`controller/VendorPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/controller/VendorPaymentController.java) | `@RequestMapping("/api/v1/payment/vendor-payment")`, class `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-scheduled')")`. **~21 mappings** for both sub-modules (Result endpoints override to `'payment-vendor-result'`); request-approval is **multipart**; 4 Banking Match endpoints. Path guards `SCDUL_PYMN_NO_PATTERN` (numeric or `DOC_NO`) + `SCDUL_PYMN_DETL_NO_PATTERN`. (Class Javadoc says "Phase A / authenticated()" — **stale**.) |

## Backend — Services (`service/`)

| File | Role |
|---|---|
| [`VendorPaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java) | Scheduled list/detail + all write actions (create, delete, request-approval, approval-action, withdraw, change-line, reporting-account, dispatch, parse, withdrawal-accounts, file download). `WSVC_ID = "vendor-payment"` at L113; `ScdulPymnTypeCd.VENDOR.code() = "01"` at every repo call. Nested `DownloadResource` record. |
| `VendorPaymentResultService.java` | Result list/detail; route-aware `PROC_STAT` status filter (`C`/`P`); footer totals via `searchDispatchedCurrencyTotals` (status-unfiltered, R008); `countByStatus` → `[success, failed, inProgress]`; Approval Ref/Title resolved from `EXPT_RESOL_MSTR` by `APPR_SEQ_NO`. |
| `VendorPaymentExcelParser.java` | Apache POI workbook walk + per-cell validation. |
| `VendorDispatchExecutor.java` | Firm-banking dispatch flow (flag-gated inert by default; writes `TB_FIRM_*`/`TB_PAY_*` when enabled). |
| `VendorPaymentSortField.java` | Scheduled-list sort whitelist (`SortableField`). |
| `VendorPaymentResultSortField.java` | Result-list sort whitelist. |
| `VendorPaymentFileResolver.java` · `VendorPaymentStorageProperties.java` · `VendorPaymentConfig.java` | Per-module upload-tree storage (`<root>/<biz_key>/vendor-payment/<save_file_nm>`), `resolveForRead` fallback to legacy `internal-transfer/`. |

## Backend — Export (`export/`)

| File | Role |
|---|---|
| `VendorPaymentResultExportProvider.java` | Extends shared `PaymentResultExportProvider`; `screenId() = "payment-vendor-payment-result"`, `erpResultTrnsTypeCd() = "04"` (ERP `VENDOR_PAYMENT_RESULT`). |

## Backend — Repositories / Entities / DTOs (shared, not Vendor-owned)

| Concern | Source |
|---|---|
| Master/detail | `internaltransfer/repository/ScdulPymnSumrRepository` + `ScdulPymnSumrDetlRepository`; entities `ScdulPymnSumr`(+`Detl`, +`Id`) — partitioned by `SCDUL_PYMN_TYPE_CD='01'` |
| Approval | `payment/approvalbox/` — `ExptResolMstr*` / `ExptResolApprvrInfm*` entities + repos; `ApprovalBoxService`, `ApprovalBoxFanoutService`, `ApprovalAttachmentWriter`/`Loader`, `ApprovalDisplayFlagsResolver`, `ApprovalNotificationService` |
| Banking Match | `payment/match/service/DomesticPaymentLineMatchService` + `match/dto/*` |
| DTOs | `internaltransfer/dto/*` — `ScheduledTransferListResponse`, `ScheduledTransferDetailResponse`, `TransferResultListResponse`, `TransferResultDetailResponse`, `CreateScheduledTransferRequest`/`Response`, `RequestApprovalRequest`/`Response`, `DispatchRequest`, `ApprovalActionRequest`, `ChangeApprovalLineRequest`, `UpdateReportingAccountRequest`, `WithdrawalAccountRow`, `ParseExcelResponse` |
| Keys / functions | `DocNoGenerator`, `InternalTransferKeyGenerator`, `LegacyFunctionRepository` (`wb_get_next_val`, `wb_get_appr_stts`, `wb_get_bank_name_any_code`) |
| Common | `WithdrawalAccountListService`, `CutoffValidator`, `ErpTrnsHistoryWriter`, `AuditLogService`, `TransferLineDetailMapper`, `OutBankCdNormalizer`, `ExcelExportService` |

## Frontend — Routes (`wecms-web/app/(dashboard)/payment/vendor-payment/`)

| Route | page.tsx export | Component |
|---|---|---|
| `scheduled-payment/page.tsx` | `PaymentVendorScheduledPage` | `VendorScheduledPaymentPage` |
| `scheduled-payment/[id]/page.tsx` | `PaymentVendorScheduledDetailRoute` | `VendorScheduledPaymentDetailPage` |
| `payment-result/page.tsx` | `PaymentVendorResultPage` | `VendorPaymentResultPage` |
| `payment-result/[id]/page.tsx` | `PaymentVendorResultDetailRoute` | `VendorPaymentResultDetailPage` |

## Frontend — Components (`wecms-web/components/payment/vendor-payment/`)

| File | Role |
|---|---|
| `ScheduledPaymentPage.tsx` | Default export `VendorScheduledPaymentPage` — composes `<PaymentScheduledBatchListPage>` + `<VendorPaymentUploadDialog>` + `<RequestApprovalDialog>` + `<ApprovalDetailsDialog>` + `<ViewSettingPopover>`. |
| `ScheduledPaymentDetailPage.tsx` | `VendorScheduledPaymentDetailPage` — `<PaymentDetailPage>` + reporting-account picker (`scheduledDetailColumns`). |
| `PaymentResultPage.tsx` | `VendorPaymentResultPage` — `<PaymentResultBatchListPage>` (totals footer) + `<ViewSettingPopover>`; export via `useExcelExport`. |
| `PaymentResultDetailPage.tsx` | `VendorPaymentResultDetailPage` — `<ResultDetailView>` + canonical 11-col `resultDetailColumns`. |
| `VendorPaymentUploadDialog.tsx` | Thin wrapper over the shared Excel-upload preview dialog (`parseVendorPaymentExcel` / `createVendorPayment`). |
| `types.ts` | Local row types `VendorScheduledRow` / `VendorResultRow`. |

## Frontend — API client / registries

| File | Role |
|---|---|
| `lib/api/vendorPayment.ts` | **Full surface** — `fetchVendorScheduledList`, `fetchVendorPaymentDetail`, `fetchVendorPaymentResultList`, `fetchVendorPaymentResultDetail`, `fetchVendorWithdrawalAccounts`, `parseVendorPaymentExcel`, `createVendorPayment`, `deleteVendorPayment`, `updateVendorReportingAccount`, `dispatchVendorPayment`, `requestVendorApproval` (multipart), `actOnVendorApproval`, `withdrawVendorApproval`, `changeVendorApprovalLine`, `downloadOriginalVendorExcel`, + 4 Banking Match (`getVendorMatchCandidates`, `matchVendorPaymentLine`, `unmatchVendorPaymentLine`, `unmatchAllVendorPaymentLines`). Reuses `internaltransfer` wire types from `lib/types/api.ts`. (File-level Javadoc says "Phase A" — **stale**.) |
| `lib/viewSettingScreens.ts` | 4 SCREEN_IDS: `PAYMENT_VENDOR_PAYMENT_SCHEDULED` / `…_SCHEDULED_DETAIL` / `…_RESULT` / `…_RESULT_DETAIL`. |

## Tests (`src/test/.../payment/vendorpayment/service/`)

| File | Role |
|---|---|
| `VendorPaymentResultParityTest` | Result list/detail legacy parity |
| `VendorPaymentResultFooterTotalsTest` | Footer totals computed status-unfiltered (R008) |
| `VendorPaymentResultDownloadAuditTest` | Dispatched-download 'W' / ERP `04` audit row |
| `VendorPaymentSortFieldTest` | Sort whitelist resolution |
| `VendorPaymentRequestApprovalAttachmentTest` | Multipart request-approval attachment persistence |
| `VendorDispatchExecutorTest` | Dispatch executor behaviour |

**Missing (G-VEND-1):** no tests for the `appr_type` server filter, `createDraft` key-race retry, the Banking Match endpoints, or the reporting-account PATCH; no FE component tests.
