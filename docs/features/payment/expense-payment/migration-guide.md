# Expense Payment — Migration Guide (JEX → Spring)

> **Backend doc.** Source of truth = latest code. Verified 2026-06-23.
> Maps legacy `pymn_0003_*` (Scheduled) + `pymn_0004_*` (Result) onto the refactor controller/service/dispatch surface.

## Legacy → refactor map

| Legacy WSVC | Legacy role | Refactor endpoint | Refactor method |
|---|---|---|---|
| `pymn_0003_001_r001` | Scheduled list | `GET /` | `ExpensePaymentService.list` |
| `pymn_0003_002_r001` | Scheduled detail / Excel parse | `GET /{id}` · `POST /uploads/parse` | `service.detail` · `service.parseExcel` |
| `pymn_0003_002_c001` | Save draft | `POST /` | `service.createDraft` |
| `pymn_0003_001_d001` | Delete draft | `DELETE /{id}` | `service.delete` |
| `pymn_0003_001_c001` | Dispatch (Transfer) | `POST /{id}/dispatch` | `service.dispatch` → `ExpenseDispatchExecutor` |
| `pymn_0004_001_r001` | Result list | `GET /results` | `ExpensePaymentResultService.list` |
| `pymn_0004_002_r001` | Result detail | `GET /results/{id}` | `resultService.detail` |
| `comm_0192_001_c005` | Request approval | `POST /{id}/request-approval` | `service.requestApproval` |
| `bltb_0001_003_u001` | Approve / reject | `POST /{id}/approval-action` | `service.actOnApproval` |
| `comm_1001_001` | Withdrawal accounts | `GET /withdrawal-accounts` | `service.listWithdrawalAccounts` |
| `comm_0197_001_r001/_c001`, `comm_0197_002_u001`, `comm_0197_001_u001` | Banking match | `/{id}/lines/{lineId}/match*`, `/{id}/matches` | `DomesticPaymentLineMatchService` |
| `pymn_0001_003` (download) | Original-file download | `GET /{id}/file` | `service.resolveDownload` |

Approval withdraw (`DELETE /{id}/approval`), change-line (`PUT /{id}/approval-line`), and reporting-account PATCH have **no single legacy WSVC** — they mirror the Internal Transfer refactor contract.

## Approved deviations

1. **Single controller** covers both sub-modules (Internal Transfer uses two). Refactor-internal organization only.
2. **Dedicated `ExpenseDispatchExecutor`** — an isolated copy of the shared `TransferDispatchExecutor` carrying the G2 legacy-parity fixes (`BankChnlDiv='T'`, per-out-bank `TRSC_CCY_CD`/`ANT_DIV_CD`, real `BANK_PRTN_ID` → `WBE2089`, VCB USD→other-bank `WBE2088`). The shared executor is left untouched for Vendor/Payroll.
3. **`APPR_STTS` not stored** on `SCDUL_PYMN_SUMR` (no such legacy column) — derived via `wb_get_appr_stts`; rejected = `R` not `X`. Matches all payment modules.
4. **Reporting-account late-bind via PATCH** — not mandated at create (resolved open question; same as Vendor).
5. **Key reuse** — `InternalTransferKeyGenerator.nextScdulPymnNo` reused verbatim (shared physical table); `APPR_SEQ_NO` via global `wb_get_next_val('sq_appr_seq_no')`.
6. **Dispatch flag-gating** — bank hand-offs `@ConditionalOnProperty` OFF by default in every profile (DB-row creation still runs). See [dispatch-sit-uat-deployment-checklist.md](../../../deployment/expense-payment-dispatch-sit-uat-checklist.md).

## Dead legacy (no refactor equivalent)

- Legacy `ERP_FILE_NUM` (8-digit ERP file sequence) as the Approval-Ref source — superseded by `EXPT_RESOL_MSTR.DOC_NO` (F4a fix). The legacy column still exists but is no longer the Result Approval-Ref home.
- Legacy `TRNF_YN`-shaped `Y`/`N` result status filter — superseded by route-aware PROC_STAT `C`/`P` (C2 fix).

## Stale code-comment artifacts (DO NOT edit in a docs pass)

- `ExpensePaymentResultService` class Javadoc says `SCDUL_PYMN_TYPE_CD = '01'` ([:41-42](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentResultService.java#L41)) and carries "Phase A read-only" framing — code correctly uses `"02"` everywhere.
- `lib/api/expensePayment.ts` header Javadoc + several docstrings carry "Vendor / Phase A / `'01'` vs `'03'`" copy-paste artifacts. Code is correct.

Flag for a future code-comment cleanup MR; no behavior impact.

---

Last verified: 2026-06-23.
