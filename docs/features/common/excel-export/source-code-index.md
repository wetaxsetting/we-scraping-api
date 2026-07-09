# Excel Export — Source Code Index

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

All paths relative to repo root. Backend lives in
`wecms-api/`, frontend in `wecms-web/`.

## `common/export/` primitives (11 files)

`wecms-api/src/main/java/com/kosign/wecms/common/export/`

| File | Role |
|---|---|
| `ExcelExportService.java` | Orchestrator — `exportTo(response, request)`; resolve provider → intersect columns → fetch → 50k cap → stream; `resolveColumns`, `enrichWidth`, `pxToCharWidth`, `sanitizeFileName`, ERP-audit tee |
| `ExportableScreenRegistry.java` | `@Component`; auto-collects every provider, rejects dup/blank screenIds at startup; `require(id)` → WBE2010 |
| `ExportRowProvider.java` | Per-screen strategy interface — `screenId()`, `allowedFieldIds()`, `fetch(request)`, default `auditsErpExport()`/`recordErpExport(...)` |
| `ExportFetch.java` | Result record — `rows[]`, `subHeader`, `subHeaderRight`; `of(...)` factories |
| `ExcelWorkbookWriter.java` | `@Component`; POI SXSSF writer, pooled styles, format coercion, column-width pass |
| `ExcelExportRequest.java` | Wire body record — `screenId`, `dymAreaId`, `fileName`, `title`, `filters`, `sort`, `columns`, `subHeader` |
| `ExcelColumnSpec.java` | One column — `fieldId`, `header`, `format`, `ccyCd`, `align`, `widthChars`; `resolvedFormat()`/`resolvedAlign()` |
| `ExcelColumnFormat.java` | Enum `TEXT|AMOUNT|NUMBER|DATE|DATETIME`; lenient `parse` |
| `ExcelAlignment.java` | Enum `LEFT|RIGHT|CENTER`; lenient `parse` |
| `ExcelSortSpec.java` | Wire sort `{ field, direction }` |
| `FieldIdAlias.java` | DB FIELD_ID → canonical resolver (Account Inquiry + e-Invoice aliases incl. `_2`/`_4` forms) |

## Confirmed providers (20 registered concrete classes)

Abstract base classes — **not registered**: `AccountInquiryExportProvider`,
`EInvoiceExportProvider`, `PaymentResultExportProvider`, `ContactsExportProvider`.

| Provider | screenId | Path (`wecms-api/src/main/java/com/kosign/wecms/domain/…`) |
|---|---|---|
| `AccountInquiryVndExportProvider` | `banking-account-inquiry-vnd` | `banking/export/` |
| `AccountInquiryFcyExportProvider` | `banking-account-inquiry-fcy` | `banking/export/` |
| `TransactionHistoryExportProvider` | `banking-transaction-history` | `banking/export/` |
| `DailyCashReportExportProvider` | `daily-cash-report` | `banking/export/` |
| `CashFlowReportExportProvider` | `cash-flow-report` | `banking/export/` |
| `SalesEInvoiceExportProvider` | `addon-sales-einvoices` | `einvoice/export/` |
| `BillEInvoiceExportProvider` | `addon-expenses-einvoices` | `einvoice/export/` |
| `OperationStatusExportProvider` | `addon-operation-status` | `operation/export/` |
| `ApprovalBoxExportProvider` | `approval-box` | `payment/approvalbox/export/` |
| `CardTransactionsExportProvider` | `card-transactions` | `payment/cardtransactions/export/` |
| `VasAccountExportProvider` | `vas-account` | `setting/vasaccount/export/` |
| `ErpTrnsHistoryExportProvider` | `erp-transaction-history` | `setting/erptransactionhistory/export/` |
| `FirmBankingHistoryExportProvider` | `firm-banking-history` | `setting/firmbankinghistory/export/` |
| `ContactsLocalExportProvider` | `settings-contacts-local` | `setting/contacts/export/` |
| `ContactsOverseasExportProvider` | `settings-contacts-overseas` | `setting/contacts/export/` |
| `InternalTransferResultExportProvider` | `payment-internal-transfer-result` | `payment/internaltransfer/export/` |
| `VendorPaymentResultExportProvider` | `payment-vendor-payment-result` | `payment/vendorpayment/export/` |
| `ExpensePaymentResultExportProvider` | `payment-expense-payment-result` | `payment/expensepayment/export/` |
| `PayrollPaymentResultExportProvider` | `payment-payroll-payment-result` | `payment/payrollpayment/export/` |
| `OverseasPaymentResultDetailExportProvider` | `payment-overseas-payment-result-detail` | `payment/overseas/export/` |

> Note: the prompt's candidate list named a "FirmBankingHistoryExportProvider"
> under `banking/export/` — the **actual** path is
> `setting/firmbankinghistory/export/`. The prompt's "AccountInquiryExportProvider"
> at `banking-account-inquiry-vnd` is the **abstract base**; the registered
> provider for that screenId is `AccountInquiryVndExportProvider`.

## Reference controller

`AccountController.exportAccounts` —
`wecms-api/src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java:124-130`
(`@PostMapping("/accounts/export")`, `@PreAuthorize(@menuAuthz…)`, returns void).
A second endpoint `exportTransactions` (`/accounts/transactions/export`,
`AccountController.java:513-518`) routes Banking > Transaction History through
the same service.

## Frontend (consumers)

`wecms-web/`

| File | Role |
|---|---|
| `lib/excel.ts` | `exportExcel(opts)` — POSTs JSON, parses XLSX blob OR JSON error, saves blob; `buildRequestBody`, `parseContentDispositionFilename`, `todayDmy`, `triggerDownload` |
| `hooks/useExcelExport.ts` | `useExcelExport()` — walks `vs.items`, drops specials + `use_yn!=='Y'` + `disp_yn==='N'` + unregistered FIELD_IDs, sorts by `disp_ord`, resolves headers via `t(...)`, calls `exportExcel` |
| `components/shared/PageActionButtons.tsx` | Toolbar Excel/list/print icons; `showExcel`/`onExport`/`exporting` props |

## Tests

`wecms-api/src/test/java/`

| Test | Covers |
|---|---|
| `com/kosign/wecms/common/export/ExcelExportServiceErpAuditTest.java` | ERP-audit tee path (`auditsErpExport` providers) |
| `com/kosign/wecms/domain/einvoice/export/EInvoiceExportProviderTest.java` | e-Invoice provider fetch/columns |
| `com/kosign/wecms/domain/payment/export/PaymentResultExportProviderCodeTest.java` | Payment-result ERP type codes |
| `com/kosign/wecms/domain/payment/service/OverseasPaymentResultExportAuditTest.java` | Overseas result export audit |
| `com/kosign/wecms/domain/setting/erptransactionhistory/export/ErpTrnsHistoryExportProviderTest.java` | ERP-trans-history provider |
| `com/kosign/wecms/domain/setting/firmbankinghistory/export/FirmBankingHistoryExportProviderTest.java` | Firm-banking-history provider |
| `com/kosign/wecms/domain/setting/vasaccount/export/VasAccountExportProviderTest.java` | VAS account provider |

> The prompt's candidate test name `ExcelExportServiceErpAuditTest` is confirmed
> present. No dedicated `ExcelExportServiceTest` (orchestrator unit test) was
> found in this pass — verify before asserting orchestrator unit coverage.
