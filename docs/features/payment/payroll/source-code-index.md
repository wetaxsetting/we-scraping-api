# Payroll Payment — Source Code Index

> Backend feature doc. Hub: [README.md](README.md). Paths verified against the latest code (2026-06-23).

## Backend (`com.kosign.wecms.domain.payment.payrollpayment`)

| Layer | File |
|---|---|
| Controller | [`controller/PayrollPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/controller/PayrollPaymentController.java) — base `/api/v1/payment/payroll-payment`, ~18 mappings, `SCDUL_PYMN_NO`/result/line guards |
| Scheduled service | [`service/PayrollPaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentService.java) — list/detail/create/delete/request-approval/approval-action/withdraw/change-line/reporting-account/dispatch/download; `WSVC_ID` L96 |
| Result service | [`service/PayrollPaymentResultService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentResultService.java) — result list/detail, status counts, footer totals, Reporting Account display |
| Dispatch | [`service/PayrollDispatchExecutor.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollDispatchExecutor.java) — file-route only, `FUND_TP` from `APPR_FUND_TP`, never `TB_PAY_TARGET` |
| Banking match | [`service/PayrollBankingMatchService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollBankingMatchService.java) |
| Excel parse | [`service/PayrollPaymentExcelParser.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentExcelParser.java) — POI; `PymnType.PAYROLL_PAYMENT("PAYR")` |
| File resolve | `service/PayrollPaymentFileResolver.java`, `service/PayrollPaymentStorageProperties.java`, `service/PayrollPaymentConfig.java` |
| Sort whitelists | `service/PayrollPaymentSortField.java`, `service/PayrollPaymentResultSortField.java` |
| Export | [`export/PayrollPaymentResultExportProvider.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/export/PayrollPaymentResultExportProvider.java) — extends `PaymentResultExportProvider`, screenId `payment-payroll-payment-result`, ERP `06` |
| Match DTOs | `dto/PayrollMatchCandidateListResponse.java`, `dto/PayrollMatchCandidateRow.java`, `dto/PayrollMatchRequest.java`, `dto/PayrollMatchResultResponse.java` |

**Reused entities/DTOs/repos** (from `internaltransfer` / `approvalbox` / `payment.common`): `ScdulPymnSumr`(+`Detl`,`Id`), `ScdulPymnSumrRepository`, `ScdulPymnSumrDetlRepository`, `ExptResolMstr`(+ repo), `ExptResolApprvrInfm`(+ repo), the `internaltransfer.dto.*` wire DTOs, `InternalTransferKeyGenerator`, `CutoffValidator`, `WithdrawalAccountListService`, `TransferLineDetailMapper`, `OutBankCdNormalizer`, `ApprovalBoxService`/`...FanoutService`, `ApprovalAttachmentWriter`/`...Loader`, `LegacyFunctionRepository`, `DocNoGenerator`.

## Frontend (`wecms-web`)

| Layer | File |
|---|---|
| Routes | `app/(dashboard)/payment/payroll/scheduled-payment/{page,[id]/page}.tsx`, `.../payment-result/{page,[id]/page}.tsx` |
| Components | `components/payment/payroll/` — `ScheduledPaymentPage.tsx`, `ScheduledPaymentDetailPage.tsx`, `PaymentResultPage.tsx`, `PaymentResultDetailPage.tsx`, `PayrollPaymentUploadDialog.tsx`, `PayrollMatchDialog.tsx`, `types.ts` |
| Shared components | `components/payment/shared/` — `PaymentScheduledBatchListPage`, `PaymentDetailPage`, `PaymentResultBatchListPage`, `ResultDetailView`, `RequestApprovalDialog`, `ApprovalDetailsDialog`, `PaymentExcelUploadDialog` |
| API client | [`lib/api/payrollPayment.ts`](../../../../../wecms-web/lib/api/payrollPayment.ts) |
| Hooks/lib | `useViewSetting` + `<ViewSettingPopover>`, `useTableSort`, `useExcelExport`, `useDialog`; SCREEN_IDS in `lib/viewSettingScreens.ts` (`PAYMENT_PAYROLL_PAYMENT_{SCHEDULED,SCHEDULED_DETAIL,RESULT,RESULT_DETAIL}`); wire types in `lib/types/api.ts` |

## Migrations
`V156__sys_scr_option_wecms.sql`, `V146__scdul_pymn_sumr_chk01_allow_payroll.sql`, `V156__sys_scr_option_wecms.sql`. *(historical — FOLDED INTO BASE DDL; the CHECK now ships inside `V86__scdul_pymn_sumr.sql`)*

## Backend tests (`src/test/.../payrollpayment`)
`PayrollPaymentResultParityTest`, `PayrollPaymentResultFooterTotalsTest`, `PayrollPaymentResultStatusCountTest`, `PayrollPaymentResultReportingAccountTest`, `PayrollPaymentResultDownloadAuditTest`, `PayrollPaymentResultDownloadAuthorizationTest`, `PayrollPaymentResultRouteValidationTest`, `PayrollPaymentRequestApprovalAttachmentTest`, `PayrollDispatchExecutorAutoReceiveTest`, `PayrollDispatchExecutorBankPrtnIdTest`. **Frontend tests: none** (G-PAYR-3).
