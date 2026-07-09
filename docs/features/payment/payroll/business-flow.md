# Payroll Payment — Business Flow (Legacy / Spring / React)

> Backend feature doc. Hub: [README.md](README.md). FE counterpart: [wecms-web/.../payroll/screen-flow.md](../../../../../wecms-web/docs/features/payment/payroll/screen-flow.md).

End-to-end flow for a Payroll batch: **upload → request approval → approve/reject → dispatch → result inquiry → banking match**. All three layers are kept separate below; the Spring column is the source of truth.

## 0. Discriminator + shared table

Payroll lives in `SCDUL_PYMN_SUMR` (+ `_DETL`) alongside Internal Transfer / Vendor / Expense. Every read and write is scoped by `SCDUL_PYMN_TYPE_CD = "00"` (`ScdulPymnTypeCd.PAYROLL.code()`). Approval-side state lives in `EXPT_RESOL_MSTR` / `EXPT_RESOL_APPRVR_INFM` (`APPR_DV = 21`).

## 1. Excel upload → parse → create draft

| Layer | Behavior |
|---|---|
| **Legacy** | `pymn_0005_002` — upload payroll Excel; `PymnHelper.uploadPaymentExcel` parses lines, normalises the bank code (`bank_cd OR napas_bank_cd`), inserts `SCDUL_PYMN_SUMR` + `_DETL`, records ERP upload history. |
| **Spring** | `POST /uploads/parse` → `PayrollPaymentService.parseExcel` → `PayrollPaymentExcelParser.parse(file, bankCd)` (Apache POI; per-row validation; `PymnType.PAYROLL_PAYMENT("PAYR")` blocked-chars). `POST /` → `createDraft` → `doCreateDraft`: `wb_get_next_val` allocates `SCDUL_PYMN_NO` (one-shot retry on MAX+1 race; master flushed before any side effect), then per-line `SCDUL_PYMN_DETL_NO` (seed-once + increment), disk write, ERP history `TRNS_TYPE_CD="12"` (Payroll Scheduled), audit row. `OUT_BANK_CD` normalised via `OutBankCdNormalizer`. |
| **React** | `<PayrollPaymentUploadDialog>`: Download template → Browse → preview (error cells highlighted) → Save draft. `parsePayrollPaymentExcel` → `createPayrollPayment` in `lib/api/payrollPayment.ts`. |

## 2. Request approval

| Layer | Behavior |
|---|---|
| **Legacy** | `comm_0192_001_c005` — stamps `SCDUL_PYMN_SUMR.APPR_SEQ_NO`, writes `EXPT_RESOL_MSTR` (`APPR_STS="2"` In Progress, `DOC_NO`, display flags from `BUSI_ENV_SET_INFM`, **`APPR_FUND_TP` Payroll Type**), approver rows (first `P`, rest `W`), `EXPT_RESOL_DETL`, attachments, bulletin/notification fan-out. |
| **Spring** | `POST /{id}/request-approval` (**multipart** — JSON `request` + `attachments`) → `PayrollPaymentService.requestApproval` → `stampApproval`: `APPR_SEQ_NO` via `wb_get_next_val` (global `sq_appr_seq_no`), `DOC_NO` via `DocNoGenerator`, `EXPT_RESOL_MSTR` (`APPR_STS="2"`, `APPR_DV=21`, `APPR_FUND_TP` from `req.apprFundTp()`), approver fan-out, `EXPT_RESOL_DETL` + approval-line snapshot, `EXPT_RESOL_FILE` attachments (`ApprovalAttachmentWriter`), BLTB notification. Guard: `WBE2062` if already requested. **`DOC_NO` is not back-stamped onto `SCDUL_PYMN_SUMR`** — canonical home is `EXPT_RESOL_MSTR.DOC_NO`. |
| **React** | Shared `<RequestApprovalDialog kind="payroll">` (read-only withdrawal account) → `requestPayrollApproval`. |

## 3. Approve / reject

| Layer | Behavior |
|---|---|
| **Legacy** | Approval Box (`comm_0190_*`) approve/reject transitions `EXPT_RESOL_MSTR.APPR_TYPE_CD`. |
| **Spring** | `POST /{id}/approval-action` → `actOnApproval`: derives current status via `wb_get_appr_stts`; rejects re-action when already `C` (approved) or `R` (rejected) with `WBE2127`. Delegates to `ApprovalBoxService.approve/reject` — the approval box owns the `APPR_TYPE_CD` transition. **No write-back to `SCDUL_PYMN_SUMR`** (no `APPR_STTS` column). Withdraw = `DELETE /{id}/approval`; change line = `PUT /{id}/approval-line`. |
| **React** | Shared `<ApprovalDetailsDialog kind="payroll">` — Approve / Reject / kebab Edit (approval line) / Withdraw; combined attachment list; shared `APPR_SEQ_NO` download endpoint. |

## 4. Dispatch (Transfer)

| Layer | Behavior |
|---|---|
| **Legacy** | `pymn_0005_001_c001` — Payroll dispatches **file-route only**: writes `TB_PAY_FILE_MST` + `TB_PAY_FILE_DATA` regardless of currency, never `TB_PAY_TARGET`. `FUND_TP` from `APPR_FUND_TP`. |
| **Spring** | `POST /{id}/dispatch` → `dispatch`: guards `WBE2064` (already transferred) / `WBE2063` (not approved); `CutoffValidator.validate`; resolves `APPR_FUND_TP` → `PayrollDispatchExecutor.execute(master, lines, trnfCcyCd, apprFundTp)` → `TB_PAY_FILE_MST.FUND_TP` (Salary `0` / Bonus `1` / Other `9`, default Other). **Payroll-only executor** — never `TB_PAY_TARGET`; the shared currency-branching executor is intentionally not used. Flag-gated inert by default. |
| **React** | "Transfer" toolbar/row action → `dispatchPayrollPayment` (pre-flight `validateBankingTime`). |

## 5. Payment Result inquiry

| Layer | Behavior |
|---|---|
| **Legacy** | `pymn_0006_001` (list) / `pymn_0006_002` (detail) — dispatched batches only; status Completed/In Progress; totals footer; per-line Success/Failed. |
| **Spring** | `GET /results` → `PayrollPaymentResultService.list`: `searchDispatched` (gates `TRNF_YN='Y'`); Completed/In Progress filter maps to `PROC_STAT` codes `C`/`P` (route-aware EXISTS); footer totals over the **full** filtered set (unfiltered by status, legacy R008). `GET /results/{id}` → `detail`: per-line `DispatchResultResolver`, header tab counts (All/Success/Failed/In Progress) via `countByStatus`, Reporting Account via batched `wb_get_chart_acct_nm` / `_lv_nm`, withdrawal-bank header via single-arg `wb_get_napas_bank_nm`, Approval Ref/Title/Requestor from `EXPT_RESOL_MSTR`. |
| **React** | `<PayrollPaymentResultPage>` / `<PayrollPaymentResultDetailPage>`; Approval Ref clickable (F4b) when `appr_seq_no` present; Excel export (`POST /results/export`); per-row original-Excel download. |

## 6. Banking match (Result detail)

| Layer | Behavior |
|---|---|
| **Legacy** | `pymn_0006_002` + `comm_0197_*` — header-level / batch-level 1:1 match against a bank transaction; **no Unmatch-All** for Payroll. |
| **Spring** | `GET /{id}/match/candidates` (candidates pinned to exact pay date + total amount; date range non-filtering), `POST /{id}/match`, `DELETE /{id}/match` → `PayrollBankingMatchService`. Header state read from `SCDUL_PYMN_SUMR` (`LINK_MATCH_YN`, `ACCT_SEQ_NO`, `BANK_TRNS_*`). |
| **React** | `<PayrollMatchDialog>`: matched → matched-ref + Unmatch; complete (`prc_cnt===0`) → Match; processing → no action. |

## Audit / key-gen invariants

- All mutating endpoints write `AUDIT_LOGS_INFM` with `WSVC_ID="payroll-payment"`, `REF_NO="SCDUL_PYMN_SUMR"`.
- Every key (`SCDUL_PYMN_NO`, `SCDUL_PYMN_DETL_NO`, `APPR_SEQ_NO`, `DOC_NO`) funnels through `wb_get_next_val` via `LegacyFunctionRepository` (single-executor rule).
