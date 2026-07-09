# Payroll Payment — Test Scenarios

> Backend feature doc. Hub: [README.md](README.md). Backend coverage is strong; the frontend has no tests (G-PAYR-3).

## Backend (implemented)

| Test | Covers |
|---|---|
| `PayrollPaymentResultParityTest` | Result list/detail shape matches legacy `pymn_0006_*` |
| `PayrollPaymentResultFooterTotalsTest` | Footer totals over the full filtered set (not the page slice); status filter not applied to totals |
| `PayrollPaymentResultStatusCountTest` | Header tab counts All/Success/Failed/In Progress; `success+failed+inProgress==lines` |
| `PayrollPaymentResultReportingAccountTest` | Per-line Reporting Account display (`wb_get_chart_acct_nm` / `_lv_nm`), render gate on `CHART_ACCT_CD` |
| `PayrollPaymentResultDownloadAuditTest` | ERP `'W'` audit row (TRNS `06`) on dispatched-batch download |
| `PayrollPaymentResultDownloadAuthorizationTest` | `/{id}/file` accepts either `payment-payroll-result` or `payment-payroll-scheduled` menu (closure-audit D1) |
| `PayrollPaymentResultRouteValidationTest` | Result route rejects `DOC_NO`; numeric-only guard |
| `PayrollPaymentRequestApprovalAttachmentTest` | Multipart request-approval persists attachments to `EXPT_RESOL_FILE` |
| `PayrollDispatchExecutorAutoReceiveTest` | File-route dispatch; auto-receive registration |
| `PayrollDispatchExecutorBankPrtnIdTest` | Bank partner id resolution in the file-route executor |

## Coverage gaps

| Gap | Class |
|---|---|
| **Frontend tests** — no `.test.*` for any Payroll screen (list/detail/upload/match dialogs) | MISSING (G-PAYR-3) |
| Backend Scheduled-side create / parse / approval-action / reporting-account / banking-match unit tests | MISSING (covered indirectly via executor + result tests) |
| Export `APPR_FUND_TP` blank-column assertion (would lock in the G-PAYR-2 partial until fixed) | suggested |

## Suggested scenarios to add (frontend)
- Scheduled list: filter by approval status, Payroll Type column renders Salary/Bonus/blank, totals footer, pagination, non-sortable `APPR_FUND_TP`/`APPRVR_NM`.
- Scheduled detail: 5-column subset render, inline Reporting Account picker save + rollback, original-Excel download.
- Result list: Completed/In Progress checkbox → `trnf_stts` `C`/`P`, clickable Approval Ref (F4b), Excel export.
- Result detail: 8-column subset, status tabs, Banking Match (Match / Unmatch, no Unmatch-All).
