# Expense Payment — Source Code Index

> **Backend doc.** Source of truth = latest code. Verified 2026-06-23.
> Backend root: `wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/`. Frontend: `wecms-web/`.

## Backend — `domain/payment/expensepayment/**`

| File | Role |
|---|---|
| `controller/ExpensePaymentController.java` | Single controller — full surface (20 mappings). Class authz `payment-expense-scheduled`; Result authz `payment-expense-result`. Base `/api/v1/payment/expense-payment`. |
| `service/ExpensePaymentService.java` | Scheduled list/detail + all write actions (create, delete, request-approval, approval-action, withdraw, change-line, reporting-account, dispatch, file download). `WSVC_ID = "expense-payment"` ([:113](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java#L113)). |
| `service/ExpensePaymentResultService.java` | Result list (route-aware PROC_STAT + footer totals) + detail (per-line resolve + `countByStatus`). |
| `service/ExpensePaymentExcelParser.java` | Apache POI walk + per-cell validation. |
| `service/ExpenseDispatchExecutor.java` | **Dedicated** dispatch executor (G2-parity copy): VND→`TB_PAY_TARGET`, non-VND→`TB_PAY_FILE_MST`/`_DATA`, `PROC_STAT='9'`, `BankChnlDiv='T'`, real `BANK_PRTN_ID`→`WBE2089`, VCB USD→other-bank `WBE2088`. |
| `service/ExpensePaymentSortField.java` · `ExpensePaymentResultSortField.java` | Sort whitelists. |
| `service/ExpensePaymentConfig.java` · `ExpensePaymentFileResolver.java` · `ExpensePaymentStorageProperties.java` | Config / file resolution / storage props. |
| `export/ExpensePaymentResultExportProvider.java` | Extends `PaymentResultExportProvider`; `screenId() = "payment-expense-payment-result"`, ERP `TRNS_TYPE_CD = "05"` (`ErpTypeCd.EXPENSE_PAYMENT_RESULT`). |

### Shared infrastructure consumed (do not fork)
Entities `ScdulPymnSumr` / `ScdulPymnSumrDetl` (`internaltransfer.entity`), `ExptResolMstr` / `ExptResolApprvrInfm` (`approvalbox.entity`), dispatch `TbPayTarget` / `TbPayFileMst` / `TbPayFileData` / `TbFirmConf`. Services `ApprovalBoxFanoutService`, `ApprovalAttachmentWriter/Loader`, `ApprovalDisplayFlagsResolver`, `DomesticPaymentLineMatchService`, `DispatchResultResolver`, `TransferLineDetailMapper`, `InternalTransferKeyGenerator`, `LegacyFunctionRepository` (`wb_get_next_val`), `CutoffValidator`, `ErpTrnsHistoryWriter`, `AuditLogService`. Repos `ScdulPymnSumrRepository`, `ScdulPymnSumrDetlRepository`, `ExptResolMstrRepository`, `DispatchCountRepository`, `TbFirmConfRepository`, `TbPay*Repository`.

### DTOs (reused from `internaltransfer.dto` + `match.dto`)
`ScheduledTransferListResponse` / `…DetailResponse`, `TransferResultListResponse` / `…DetailResponse` / `TransferResultRow` / `TransferResultLine`, `CreateScheduledTransferRequest` / `…Response`, `ParseExcelResponse`, `RequestApprovalRequest` / `…Response`, `ApprovalActionRequest`, `ChangeApprovalLineRequest`, `UpdateReportingAccountRequest`, `DispatchRequest`, `WithdrawalAccountRow`, `CurrencyTotal`; `PaymentLineMatch{Candidate,Request,Result}…`, `PaymentLineUnmatchAllResponse`. `ScdulPymnTypeCd.EXPENSE` enum.

## Frontend — `wecms-web/`

| Path | Role |
|---|---|
| `app/(dashboard)/payment/expense-payment/scheduled-payment/page.tsx` · `[id]/page.tsx` | Scheduled list + detail routes |
| `app/(dashboard)/payment/expense-payment/payment-result/page.tsx` · `[id]/page.tsx` | Result list + detail routes |
| `components/payment/expense-payment/ScheduledPaymentPage.tsx` | Scheduled list (composes `<PaymentScheduledBatchListPage>`); exports `ExpenseScheduledPaymentPage` |
| `components/payment/expense-payment/ScheduledPaymentDetailPage.tsx` | Scheduled detail (`<PaymentDetailPage>`) |
| `components/payment/expense-payment/PaymentResultPage.tsx` | Result list (`<PaymentResultBatchListPage>`, totals footer rendered) |
| `components/payment/expense-payment/PaymentResultDetailPage.tsx` | Result detail (`<ResultDetailView>`, 11-column `resultDetailColumns`) |
| `components/payment/expense-payment/ExpensePaymentUploadDialog.tsx` | Excel-upload dialog (Expense column set) |
| `components/payment/expense-payment/types.ts` | Local component types |
| `lib/api/expensePayment.ts` | API client — ~18 functions (see below) |
| `lib/viewSettingScreens.ts` | 4 SCREEN_IDS (`PAYMENT_EXPENSE_PAYMENT_{SCHEDULED,SCHEDULED_DETAIL,RESULT,RESULT_DETAIL}`) |

### `lib/api/expensePayment.ts` functions
`fetchExpenseScheduledList`, `fetchExpenseWithdrawalAccounts`, `parseExpensePaymentExcel`, `createExpensePayment`, `fetchExpensePaymentDetail`, `fetchExpensePaymentResultDetail`, `fetchExpensePaymentResultList`, `deleteExpensePayment`, `updateExpenseReportingAccount`, `getExpenseMatchCandidates`, `matchExpensePaymentLine`, `unmatchExpensePaymentLine`, `unmatchAllExpensePaymentLines`, `dispatchExpensePayment`, `requestExpenseApproval`, `actOnExpenseApproval`, `withdrawExpenseApproval`, `changeExpenseApprovalLine`, `downloadOriginalExpenseExcel`.
> **Stale comment (code-comment cleanup MR, do not edit in docs pass):** header Javadoc + several docstrings carry "Vendor / Phase A / `'01'` vs `'03'`" copy-paste artifacts — code is correct.

## Tests — `src/test/.../expensepayment/service/`
`ExpenseDispatchExecutorTest`, `ExpensePaymentDispatchApprovalGateTest`, `ExpensePaymentResultDownloadAuditTest`, `ExpensePaymentResultFooterTotalsTest`, `ExpensePaymentResultParityTest`.
**Missing coverage:** Scheduled create/parse, request-approval, approval-action, withdraw/change-line, reporting-account PATCH, banking-match endpoints; no frontend tests.

---

Last verified: 2026-06-23.
