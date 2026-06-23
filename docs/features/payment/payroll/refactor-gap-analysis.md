# Payroll Payment — Refactor Gap Analysis

> Backend feature doc. Hub: [README.md](README.md). Gap vocabulary: **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING**, with a gap *kind*. Source of truth = latest code. Cross-ref: [payment-gap-analysis.md](../../../audits/payment-gap-analysis.md) (rows G-PAYR-1/2/3).

| ID | Gap | Class | Kind | Owner |
|---|---|---|---|---|
| G-PAYR-1 | Backend README previously carried "5-column Payroll layout" + "STUB / backfill pending" though code is fully implemented (~18 endpoints, banking match, export) | **PARTIAL → resolved** | doc-exists-stale | wecms-api-docs owner |
| G-PAYR-2 | `PayrollPaymentResultExportProvider` exports `APPR_FUND_TP` (Payroll Type) blank — shared `PaymentResultExportProvider` base does not project it; needs the `EXPT_RESOL_MSTR` join in the export path | **PARTIAL** | backend-followup | dev |
| G-PAYR-3 | No frontend tests for Payroll screens | **MISSING** | test-missing | dev |

## Notes

- **G-PAYR-1** is closed by this documentation set (2026-06-23): the README stale claims are removed, the Scheduled detail is documented as a 5-column subset and the Result detail as an 8-column subset of the canonical column factories.
- **G-PAYR-2** is the only live behavioral gap: the list pages and Approval Details popup resolve Payroll Type correctly, but the **Excel export** column ships blank. The fix is to join `EXPT_RESOL_MSTR.APPR_FUND_TP` (by `APPR_SEQ_NO`) into the export row provider. No DDL needed.
- **MATCH** (no gap): discriminator `"00"`, status via `wb_get_appr_stts`, file-route-only dispatch, banking match (header 1:1, no Unmatch-All), all key-gen via `wb_get_next_val`, audit `WSVC_ID="payroll-payment"`, request-approval multipart attachments → `EXPT_RESOL_FILE`.
- **APPROVED DEVIATIONS** are listed in [migration-guide.md](migration-guide.md).

## Backend test coverage (strong)
`PayrollPaymentResultParityTest`, `...ResultFooterTotalsTest`, `...ResultStatusCountTest`, `...ResultReportingAccountTest`, `...ResultDownloadAuditTest`, `...ResultDownloadAuthorizationTest`, `...ResultRouteValidationTest`, `...RequestApprovalAttachmentTest`, `PayrollDispatchExecutor{AutoReceive,BankPrtnId}Test`. See [test-scenarios.md](test-scenarios.md).
