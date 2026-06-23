# Payroll Payment — Migration Guide (JEX → Spring)

> Backend feature doc. Hub: [README.md](README.md). Maps legacy JEX `pymn_0005_*` / `pymn_0006_*` to the refactor controller/service. Identifiers use refactor slugs; legacy WSVC ids appear only as source-of-truth pointers.

## Legacy → Spring map

| Legacy WSVC | Behavior | Spring target |
|---|---|---|
| `pymn_0005_001` | Scheduled list | `PayrollPaymentController.list` → `PayrollPaymentService.list` (`GET /`) |
| `pymn_0005_002` | Excel upload (parse + create) | `parseExcel` / `createDraft` (`POST /uploads/parse`, `POST /`) |
| `pymn_0005_003` | Scheduled detail | `PayrollPaymentService.detail` (`GET /{id}`) |
| `pymn_0006_001` | Result list | `PayrollPaymentResultService.list` (`GET /results`) |
| `pymn_0006_002` | Result detail | `PayrollPaymentResultService.detail` (`GET /results/{id}`) |
| `comm_0192_001_c005` | Request approval / stamp | `requestApproval` → `stampApproval` |
| `comm_0190_*` | Approve / reject | `actOnApproval` → `ApprovalBoxService` |
| `pymn_0005_001_c001` | Dispatch (file-route) | `dispatch` → `PayrollDispatchExecutor` |
| `comm_0197_*` | Banking match (batch path) | `PayrollBankingMatchService` (`/{id}/match*`) |
| `comm_1001_001` | Withdrawal-account picker | `WithdrawalAccountListService` (`GET /withdrawal-accounts`) |

## Approved deviations

| Deviation | Rationale |
|---|---|
| **No `APPR_STTS` column write-back** | Legacy `SCDUL_PYMN_SUMR` has no `APPR_STTS`; status is derived via `wb_get_appr_stts(...)` (single-executor funnel). Matches legacy DDL. |
| **`DOC_NO` not back-stamped onto `SCDUL_PYMN_SUMR`** | Canonical home is `EXPT_RESOL_MSTR.DOC_NO`; `ERP_FILE_NUM` (varchar(8)) would overflow. List + detail resolve `DOC_NO` from `EXPT_RESOL_MSTR` by `APPR_SEQ_NO`. |
| **Single controller** for Scheduled + Result | Internal Transfer splits into two; Payroll keeps one `PayrollPaymentController`. Functional parity unchanged. |
| **Slug asymmetry** | Frontend `payroll`, backend/audit `payroll-payment`. Confirmed acceptable. |
| **Payroll-only `PayrollDispatchExecutor`** | Legacy Payroll dispatches file-route only (never `TB_PAY_TARGET`); the shared currency-branching executor would mis-route VND → API. |
| **Rejected = `R`** | Legacy code is `R`, not `X`. |

## Dead legacy (not ported)
- No Payroll "Re-run" feature (the Internal Transfer `pymn_0010_002.ts` Re-run was retired PO-side; Payroll never had a refactor equivalent).
- `SCDUL_PYMN_SUMR.ERP_FILE_NUM` overloading for `DOC_NO` — abandoned (schema drift / overflow).

## Known partial
- **G-PAYR-2** — `PayrollPaymentResultExportProvider` exports `APPR_FUND_TP` (Payroll Type) **blank** until the export base path ships the `EXPT_RESOL_MSTR` join. Backend follow-up.
