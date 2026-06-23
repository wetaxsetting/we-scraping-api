# Excel Export — Interface Specification

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

## Endpoint contract (per-resource — never a generic dispatcher)

```
POST /api/v1/{module}/{resource}/export
Content-Type: application/json
Authorization: Bearer <JWT>      (same auth path as the list endpoint)
Body: ExcelExportRequest

→ 200 OK
  Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  Content-Disposition: attachment; filename*=UTF-8''<percent-encoded>.xlsx
  X-Content-Type-Options: nosniff
  Access-Control-Expose-Headers: Content-Disposition
  Body: XLSX bytes
```

The controller is a thin shim: `@Valid @RequestBody ExcelExportRequest` +
`HttpServletResponse`, forwards to `excelExportService.exportTo(response, request)`,
returns `void`. Reference: `AccountController.exportAccounts`
(`AccountController.java:124-130`). Response headers set in
`ExcelExportService.configureResponseHeaders` (`ExcelExportService.java:345-362`).

> `POST` (not `GET`) so the column spec + filter state travel as a JSON body
> without query-string length limits (`AccountController.java:120-122`).

## Request body — `ExcelExportRequest` (ExcelExportRequest.java:71-94)

| Field | Type | Required | Notes |
|---|---|---|---|
| `screenId` | string | yes | View Setting `WEB_SVC_ID`; `@NotBlank` + kebab regex `^[a-z][a-z0-9]*(-[a-z0-9]+)+$` (≥2 segments) |
| `dymAreaId` | string | yes | View Setting `DYM_AREA_ID`; `@NotBlank` + kebab regex; single-area pages send `"default"` |
| `fileName` | string | no | filename base (no extension); controller appends `.xlsx` |
| `title` | string | no | in-sheet row-0 title; distinct from `fileName`; falls back to `fileName`→`screenId` |
| `filters` | `Map<String,Object>` | no | opaque per-screen filter state; the provider decides which keys it reads |
| `sort` | `ExcelSortSpec` | no | `{ field, direction }` — same wire contract as the list endpoint |
| `columns` | `ExcelColumnSpec[]` | no | visible + ordered columns; backend re-intersects with allow-list + visibility |
| `subHeader` | string | no | informational fallback; provider's computed subheader wins |

### `ExcelColumnSpec` (ExcelColumnSpec.java:40-46)

`{ fieldId, header, format, ccyCd, align, widthChars }` — `header` is
already-translated (frontend owns locale); `format` ∈ `text|amount|number|date|datetime`
(unknown→`text`); `align` ∈ `left|right|center` (unknown→`left`, amount/number→`right`);
`widthChars` optional explicit POI width.

### `ExcelSortSpec` (ExcelSortSpec.java:12-15)

`{ field, direction }` — `field` = View Setting `FIELD_ID`, `direction` =
`ASC|DESC`; either null/blank → provider's legacy default ORDER BY.

## Error codes

| Code | Meaning | Source |
|---|---|---|
| `WBE2010` | Unknown `screenId` (no registered provider) | `ExportableScreenRegistry.java:25, :52-58` |
| `WBE2011` | Result exceeds `MAX_ROWS = 50_000` | `ExcelExportService.java:63, :88-90` |
| `WBE2012` | Export failed (unexpected runtime, response still uncommitted) | `ExcelExportService.java:64, :139-141` |
| `WBE2013` | No exportable columns after intersection | `ExcelExportService.java:65, :82-84` |

Bad request shape (validation) → standard `WBE_VALIDATION` envelope via the
global handler. All four codes ship in the standard `{ status, … }` error
envelope; the frontend's blob path sniffs `Content-Type: application/json` on a
200 to surface them (see frontend twin api-consumption).

## Confirmed provider / screenId table (20 registered)

Two abstract base classes (`AccountInquiryExportProvider`,
`EInvoiceExportProvider`, `PaymentResultExportProvider`,
`ContactsExportProvider`) are **not** registered — only their concrete
subclasses are. Verified by grepping `@Component` + `extends/implements
ExportRowProvider` and reading each `screenId()` / `allowedFieldIds()`.

| screenId | Provider | Allowed FIELD_IDs | ERP audit |
|---|---|---|---|
| `banking-account-inquiry-vnd` | `AccountInquiryVndExportProvider` | BANK, ACCOUNT_NICKNAME, BALANCE, AVAILABLE_BALANCE, VAS_ACCOUNT, LAST_UPDATE | no |
| `banking-account-inquiry-fcy` | `AccountInquiryFcyExportProvider` | (same as VND — inherited) | no |
| `banking-transaction-history` | `TransactionHistoryExportProvider` | TRSC_ID, TRSC_DTM, DEPOSIT_AMT, WITHDRAW_AMT, BAL, RMRK, FILE_NM, MTCH_DT, MTCH_BY, PURPOSE, CONTACT_NM, CONFIRMED_BY | no |
| `daily-cash-report` | `DailyCashReportExportProvider` | BANK, ACCT_NO, BALANCE, LOAN_NAME, MATURITY_DATE, SEND_COUNT, SEND_AMOUNT, RECEIVE_COUNT, RECEIVE_AMOUNT | no |
| `cash-flow-report` | `CashFlowReportExportProvider` | DISP_CD, DISP_NM, ITEM_CD, ITEM_NM, ROW_TYPE, JAN_AMT…DEC_AMT, TOTAL_AMT, DISP_CCY_CD | no |
| `addon-sales-einvoices` | `SalesEInvoiceExportProvider` | DATE_TIME, INVOICE_NO, BUYER, CURRENCY, TOTAL, STATUS, TAX, PRETAX | no |
| `addon-expenses-einvoices` | `BillEInvoiceExportProvider` | (same — inherited from `EInvoiceExportProvider`) | no |
| `addon-operation-status` | `OperationStatusExportProvider` | COMPANY, ADDRESS, OPERATION_STATUS, LAST_UPDATE | no |
| `approval-box` | `ApprovalBoxExportProvider` | REG_DTM, DOC_NM, DOC_NO, TITL, FILE_NM, CURRENCY, TOT_PAY_AMT, PAY_END_YN, PAY_DATE, DRAFT_USER_NM | no |
| `card-transactions` | `CardTransactionsExportProvider` | TRSC_DT, CARD_NO, MERCH_NM, CARD_NICK_NM, CARD_HLDR_NM, CCY_CD, TRSC_AMT, STTS, CARD_TRNS_USAGE_NM, CONT, … | no |
| `vas-account` | `VasAccountExportProvider` | VAS_ACCT_CD, VAS_ACCT_NM, VAS_ACCT_TYPE, STTS | no |
| `erp-transaction-history` | `ErpTrnsHistoryExportProvider` | REG_DTM, ERP_TYPE, TRNS_TYPE_CD, ORG_FILE_NM, FILE_SIZE, REG_USER, REG_ID | no |
| `firm-banking-history` | `FirmBankingHistoryExportProvider` | REG_DTM, SVC_TYPE, SVC_NM, SEQ_NO, DEP_BANK_CD, RS_CD, RS_MSG | no |
| `settings-contacts-local` | `ContactsLocalExportProvider` | CONTACT_CODE, CONTACT_NAME, TAX_ID, DEPOSIT_BANK, DEPOSIT_ACCOUNT_NO, CURRENCY, CURRENT_ADDRESS, OLD_ADDRESS, VALID_DATE | no |
| `settings-contacts-overseas` | `ContactsOverseasExportProvider` | CONTACT_CODE, CONTACT_NAME, TAX_ID, BENEFICIARY_NAME, BENEFICIARY_BANK_NAME, BIC_CODE, BENEFICIARY_ACCOUNT_NO, BENEFICIARY_ADDRESS, CURRENT_ADDRESS, OLD_ADDRESS | no |
| `payment-internal-transfer-result` | `InternalTransferResultExportProvider` | REG_DTM, TITL, TOT_TRNS_CNT, TOTAL_TRAN_AMT, TRNF_YN, APPR_SEQ_NO, DOC_NO (base set) | **yes** (ERP `17`) |
| `payment-vendor-payment-result` | `VendorPaymentResultExportProvider` | (base set) | **yes** (ERP `04`) |
| `payment-expense-payment-result` | `ExpensePaymentResultExportProvider` | (base set) | **yes** (ERP `05`) |
| `payment-payroll-payment-result` | `PayrollPaymentResultExportProvider` | (base set) | **yes** (ERP `06`) |
| `payment-overseas-payment-result-detail` | `OverseasPaymentResultDetailExportProvider` | BNFC_BANK_NM, BNFC_ACCT_NO, BNFC_NM, CCY_CD, TRNF_AMT, MEMO, TRNF_YN | no |

> Payment *result* providers extend `PaymentResultExportProvider` (base
> allow-list REG_DTM, TITL, TOT_TRNS_CNT, TOTAL_TRAN_AMT, TRNF_YN, APPR_SEQ_NO,
> DOC_NO — `PaymentResultExportProvider.java:54-62`) and opt into the ERP audit
> tee. `OverseasPaymentResultDetailExportProvider` implements the interface
> directly (no ERP-audit opt-in observed) and requires a `filters.ovrsPymnNo`
> (→ `WBE_VALIDATION` if absent —
> `OverseasPaymentResultDetailExportProvider.java:60-62`).
