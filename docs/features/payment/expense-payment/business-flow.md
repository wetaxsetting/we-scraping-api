# Expense Payment — Business Flow (API)

> **Backend doc.** Frontend counterpart: [wecms-web/docs/features/payment/expense-payment/screen-flow.md](../../../../../wecms-web/docs/features/payment/expense-payment/screen-flow.md).
> Source of truth = latest code under `domain/payment/expensepayment/**` + `domain/payment/dispatch/**`. Verified 2026-06-23.

End-to-end flow for the two Expense Payment sub-modules — **Scheduled Payment** (`pymn_0003_*`) and **Payment Result** (`pymn_0004_*`) — across Legacy JEX, the Spring Boot refactor, and the React (Next.js) consumer. Each stage states the three layers separately.

Discriminator: `SCDUL_PYMN_TYPE_CD = "02"` (`ScdulPymnTypeCd.EXPENSE.code()`), supplied by the service at every repository call. Expense shares the physical `SCDUL_PYMN_SUMR` table with Internal Transfer (`"03"`) and Vendor.

---

## 1. Upload (draft creation)

**Legacy JEX** — `pymn_0003_002_r001` parses the uploaded Excel and `pymn_0003_002_c001` saves the draft batch into `SCDUL_PYMN_SUMR` (+ `SCDUL_PYMN_SUMR_DETL` lines). `comm_1001_001` supplies the firm-banking-capable withdrawal accounts.

**Spring** —
1. `GET /withdrawal-accounts` → `ExpensePaymentService.listWithdrawalAccounts` (module-agnostic — same lookup as Internal Transfer).
2. `POST /uploads/parse` (multipart) → `ExpensePaymentService.parseExcel` → `ExpensePaymentExcelParser` (Apache POI walk + per-cell validation). Reuses the Internal Transfer template format.
3. `POST /` (multipart: `request` JSON + `file_data` binary) → `ExpensePaymentService.createDraft`:
   - `SCDUL_PYMN_NO` via `InternalTransferKeyGenerator.nextScdulPymnNo` (shared physical table → key reuse is deliberate). One-shot retry on the `MAX+1` race (`wb_get_next_val` has no row lock).
   - Stamps `SCDUL_PYMN_TYPE_CD = "02"`, persists canonical NAPAS network so cut-off / dispatch resolve correctly.
   - Detail numbering via `wb_get_next_val('scdul_pymn_sumr_detl','scdul_pymn_detl_no')` (V140 #38, per-tenant `MAX+1`).
   - Stores the original Excel for later download (`GET /{id}/file`).

**React** — `<ExpensePaymentUploadDialog>` (wraps `<PaymentExcelUploadDialog>`) supplies the Expense-specific column set, calls `parseExpensePaymentExcel` then `createExpensePayment`.

---

## 2. Request approval

**Legacy JEX** — `comm_0192_001_c005` creates the approval request: an `EXPT_RESOL_MSTR` master + `EXPT_RESOL_APPRVR_INFM` approver chain, and back-stamps `SCDUL_PYMN_SUMR.APPR_SEQ_NO`.

**Spring** — `POST /{id}/request-approval` (**multipart** — JSON `request` + optional `attachments[]`) → `ExpensePaymentService.requestApproval`:
- `APPR_SEQ_NO` via the **global** `wb_get_next_val('sq_appr_seq_no')` funnel (V140 #1 — the GLOBAL sequence shared across every Payment module, so the id is above any existing rows in `EXPT_RESOL_*`).
- `ExptResolMstr.APPR_DV = ApprDv.EXPENSE.code()`.
- Approval attachments written via `ApprovalAttachmentWriter`; approver fan-out via `ApprovalBoxFanoutService`.
- Rejects when `APPR_SEQ_NO` is already set (re-submit guard).

**React** — `requestExpenseApproval` (multipart FormData).

---

## 3. Approve / reject

**Legacy JEX** — `bltb_0001_003_u001` records the approver decision; `wb_get_appr_stts(...)` over `EXPT_RESOL_MSTR.APPR_TYPE_CD` derives the batch approval status.

**Spring** — `POST /{id}/approval-action` → `ExpensePaymentService.actOnApproval`. Status is **derived**, never stored on `SCDUL_PYMN_SUMR` (no `APPR_STTS` column in legacy JEX) — derived via `wb_get_appr_stts(...)`. **Rejected = `R`, not `X`** (`APPR_STTS_REJECTED = "R"`; approved = `"C"`).

Two further approval-management actions (kebab menu on the Approval Details popup):
- `DELETE /{id}/approval` → `withdrawApproval` (withdraw an in-flight request).
- `PUT /{id}/approval-line` → `changeApprovalLine` (replace the approver chain).

**React** — `actOnExpenseApproval`, `withdrawExpenseApproval`, `changeExpenseApprovalLine`.

---

## 4. Banking match (per-line, domestic)

**Legacy JEX** — `comm_0197_*` domestic path matches a payment line to a real bank transaction (used for reconciliation / reporting-account assignment).

**Spring** — four endpoints delegate to the shared `DomesticPaymentLineMatchService`, all passing `ScdulPymnTypeCd.EXPENSE.code()`:
- `GET /{id}/lines/{lineId}/match/candidates` → `listCandidates` (`comm_0197_001_r001`).
- `POST /{id}/lines/{lineId}/match` → `match` (`comm_0197_001_c001`).
- `DELETE /{id}/lines/{lineId}/match` → `unmatch` (`comm_0197_002_u001`).
- `DELETE /{id}/matches` → `unmatchAll` (`comm_0197_001_u001`).

Reporting-account (chart-of-accounts) is **late-bind via PATCH** `/{id}/lines/{lineId}/reporting-account` → `updateReportingAccount`. Not mandated at create time (resolved open question, same as Vendor).

**React** — `getExpenseMatchCandidates`, `matchExpensePaymentLine`, `unmatchExpensePaymentLine`, `unmatchAllExpensePaymentLines`, `updateExpenseReportingAccount`.

---

## 5. Dispatch (Transfer) — DISPATCH behavior

**Legacy JEX** — `pymn_0003_001_c001` runs the firm-banking transmission for the approved batch (`PymnHelper.pymn_0001_001_c001`).

**Spring** — `POST /{id}/dispatch` → `ExpensePaymentService.dispatch` → **`ExpenseDispatchExecutor`** (a deliberate isolated copy of the shared `TransferDispatchExecutor` carrying the G2 legacy-parity fixes — the shared executor is left untouched for Vendor/Payroll):

1. **Approval gate (G1):** requires `EXPT_RESOL_MSTR.APPR_TYPE_CD = "01"` (fully approved) before any bank-dispatch table is written — `WBE2063` otherwise (legacy parity, `PymnHelper.pymn_0001_001_c001:2146`).
2. **Cut-off-time validation** (`CutoffValidator`).
3. `ExpenseDispatchExecutor.execute()` writes firm-banking rows **unconditionally** with `PROC_STAT = "9"` (pending):
   - **VND** → `TB_PAY_TARGET`, one row per detail line, `ANT_DIV_CD = INTER`, `BANK_CHNL_DIV = "T"` (legacy `BankChnlDiv.CURRENT_BANK_TRANSFER` — `"T"`, NOT `"C"`).
   - **non-VND** → `TB_PAY_FILE_MST` + `TB_PAY_FILE_DATA`. Real `BANK_PRTN_ID` from `TB_FIRM_CONF` (PK `(BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)`) — **`WBE2089`** when that row is absent (legacy NPE-blocks; refactor blocks cleanly). Per-out-bank `TRSC_CCY_CD` / `ANT_DIV_CD` (Shinhan USD→`E`, Woori USD→VCB `V`, VCB USD→null), and the **VCB USD→other-bank reject (`WBE2088`)** guard.
4. Master is stamped `TRNF_YN = "Y"` + `TRNF_DTM`; transaction commits.
5. **Post-commit bank hand-offs are flag-gated OFF by default** in every profile (`@ConditionalOnProperty`): EFS/VAN send (`wecms.dispatch.efs.enabled`), SFTP file channel (`wecms.dispatch.sftp.enabled`), 00301 File reception (`wecms.dispatch.auto-receive`), VND 202 realtime (`wecms.dispatch.realtime-dispatch`). With EFS off, `MockEfsApiClient` is wired and **throws** if a send is ever attempted; SFTP off → `MockSftpClient` (no I/O). See [dispatch-sit-uat-deployment-checklist.md](../../../deployment/expense-payment-dispatch-sit-uat-checklist.md) — verified accurate against the dispatch code this pass.

**Net:** out of the box (including SIT/UAT) Transfer creates pending DB rows but performs no real bank exchange. Intentional, not a defect.

**React** — `dispatchExpensePayment` (cut-off pre-flight surfaced to the user).

---

## 6. Result inquiry

**Legacy JEX** — `pymn_0004_001_r001` lists dispatched batches with per-currency totals; `pymn_0004_002_r001` is the per-transaction result detail.

**Spring** — `ExpensePaymentResultService`:
- `GET /results` → `list`: gates on `TRNF_YN = "Y"` (dispatched marker). Status filter is **route-aware PROC_STAT** — `trnf_stts` = **Completed `C` / In Progress `P`** (NOT the old `TRNF_YN`-shaped `Y`/`N`). The **footer currency totals (F1)** are aggregated across the FULL filtered + dispatched set (status filter intentionally NOT applied to totals — legacy R008 sums all dispatched rows). **Approval Ref (F4a)** = `EXPT_RESOL_MSTR.DOC_NO` (NOT `SCDUL_PYMN_SUMR.ERP_FILE_NUM`, null in DEV). **Approval Title (F3)** = `EXPT_RESOL_MSTR.TITL` with blank-aware fallback to `SCDUL_PYMN_SUMR.TITL`.
- `GET /results/{id}` → `detail`: resolves per-line dispatch result via the shared `DispatchResultResolver`; **header tab counts** (All / Success / Failed / In Progress) via `countByStatus` over the resolved per-line statuses (success = `proc_stat="0"` AND `rs_cd="0000"`; in-progress = blank/blank or `proc_stat ∈ {9,1,6}`; everything else = failed). Result detail rows use the canonical 11-column set.
- `POST /results/export` → `ExcelExportService.exportTo` via `ExpensePaymentResultExportProvider` (`screenId = "payment-expense-payment-result"`, ERP `TRNS_TYPE_CD = "05"`).

**React** — `fetchExpensePaymentResultList`, `fetchExpensePaymentResultDetail`, Excel export, `downloadOriginalExpenseExcel` (F2 — `W` ERP audit on the dispatched-batch download).

---

## ERP vs Payment Result differences

- **Payment Result** = the executed dispatch outcome read back from the firm-banking / `TB_PAY_*` tables (per-line `proc_stat` / `rs_cd`), surfaced as the Result list + detail.
- **ERP audit** = `ERP_TRNS_HISTORY` rows written via `ErpTrnsHistoryWriter`. Expense uses two ERP type codes: scheduled-payment `"03"` (`ERP_TRNS_TYPE_EXPENSE_SCHEDULED_PAYMENT`) and payment-result `"05"` (`ERP_TRNS_TYPE_EXPENSE_PAYMENT_RESULT` / `ErpTypeCd.EXPENSE_PAYMENT_RESULT`). The F2 dispatched-batch download stamps a `W` ERP audit (type `05`).

---

## Status model (cross-cutting)

- Approval status is **derived** (`wb_get_appr_stts` over `EXPT_RESOL_MSTR.APPR_TYPE_CD`) — `SCDUL_PYMN_SUMR` has **no `APPR_STTS` column**. Rejected = `R`, not `X`.
- Result status is **route-aware PROC_STAT** — Completed `C` / In Progress `P` for the list filter; per-line buckets (Success / Failed / In Progress) for the detail header counts.

---

Last verified: 2026-06-23 (reconciled against latest code).
