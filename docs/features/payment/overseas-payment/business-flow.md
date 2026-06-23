# Overseas Payment — Business Flow

> End-to-end flow per action. Each action is traced **Legacy (JEX)** → **Spring (wecms-api)** → **React (wecms-web)**. Hub: [README.md](README.md). Source-verified against [`OverseasPaymentService`](../../../../src/main/java/com/kosign/wecms/domain/payment/service/OverseasPaymentService.java) + [`OverseasPaymentController`](../../../../src/main/java/com/kosign/wecms/domain/payment/controller/OverseasPaymentController.java) on 2026-06-23.

## Data model at a glance

The Scheduled list and the Result list both read a **UNION** of two tables and LEFT JOIN the approval master:

- `OVRS_PYMN_SUMR` — **E-mode** (Excel batch). One summary row + N `OVRS_PYMN_SUMR_DETL` lines.
- `OVRS_PYMN_MSTR` — **M-mode** (manual / individual). One remittance per row, no detail child.
- `EXPT_RESOL_MSTR` — approval master; `APPR_TYPE_CD` is the authoritative approval state (no `APPR_STTS` column exists on either payment table).

Approval status is **derived** (`mapApprTypeCd`): `01`→`C` (approved), `02`→`X` (rejected), `03`→`I` (in-progress), `00`/null/other→`P` (pending).

---

## 1. Excel preview (parse + validate)

- **Legacy** — `pymn_0007_001` batch-upload parse step (`PymnHelper.importDataFromExcel*`). Reads a 3-row preamble (title / spacer / headers) then data rows; per-cell validation; beneficiary detail fields cross-filled from `TRCO_OVRS_INFM`.
- **Spring** — `POST /excel-preview` (multipart `file` + optional `bank_cd`) → `OverseasPaymentExcelService.preview(...)`. Returns `OverseasPaymentExcelPreview` (all rows valid+invalid, `totalCount` / `completedCount` / `errorCount`, per-cell `OverseasPaymentExcelFieldError`). Columns: Beneficiary Code, Transfer Currency, Transfer Amount, Fee Payer, Memo, Reporting Account (optional); `MIN_COLUMNS=6`, `MAX_ROWS=500`.
- **React** — `OverseasExcelUploadModal` → `submitOverseasPaymentExcel` preview leg; the shared `<ExcelUploadDialog>` paints error cells red and disables Upload while `errorCount > 0`.

## 2. Excel upload (persist batch)

- **Legacy** — `pymn_0007_001` save: insert `OVRS_PYMN_SUMR` + N `OVRS_PYMN_SUMR_DETL`; allocate `OVRS_PYMN_NO`.
- **Spring** — `POST /excel-upload` (multipart `file` + JSON `data` = `OverseasPaymentExcelUploadRequest`) → `OverseasPaymentExcelService.submit(...)`. Allocates `OVRS_PYMN_NO` via `OverseasPaymentKeyGenerator.nextOvrsPymnNo` (→ `wb_get_next_val` funnel, per-tenant MAX+1 over `OVRS_PYMN_MSTR ∪ OVRS_PYMN_SUMR`), saves the physical Excel under the per-tenant storage root, retries once on `DataIntegrityViolationException`. Returns `OverseasPaymentExcelUploadResult { ovrs_pymn_no }`.
- **React** — modal Step 3 picks the withdrawal account (`fetchOverseasWithdrawalAccounts`), then `submitOverseasPaymentExcel(body, file)`.

## 3. Request approval (single batch — E-mode)

- **Legacy** — `comm_0194_001 → comm_0192_001_c005` (`OVRS_PYMN_TYPE='S'` branch): write `EXPT_RESOL_MSTR` (`APPR_DV='34'`, `APPR_STS='2'`, `APPR_TYPE_CD='00'`), approver chain `EXPT_RESOL_APPRVR_INFM` (first `P`, rest `W`), `EXPT_RESOL_DETL`, print-header snapshot, bulletin/notification; back-stamp `OUT_BANK_CD`/`WDRL_ACT_NO`/`WDRL_CCY_CD` onto the summary (`comm_0192_001_c005_act.jsp:404-410`).
- **Spring** — `POST /{id}/request-approval` (**multipart**: `request` JSON + `attachments[]`) → `requestApproval(...)`. Guards: `WBE2127` if already transferred, `WBE2046` if already approved/pending (rejected `X` may re-submit). Allocates `APPR_SEQ_NO` via `LegacyFunctionRepository.nextApprSeqNo` (one-shot race retry), `stampApproval(...)` writes the master + chain + `EXPT_RESOL_DETL` + line snapshot + attachments (`ApprovalAttachmentWriter` → `EXPT_RESOL_FILE`) + bulletin fan-out; `DOC_NO` via `DocNoGenerator` (`VO_YYMMDD_NNN`). Display flags resolved from `BUSI_ENV_SET_INFM`. Returns `RequestApprovalResponse { apprSeqNo, docNo }`.
- **React** — shared `<RequestApprovalDialog>` → `requestOverseasApproval(id, body, attachments)`.

## 4. Request approval (multi — M-type)

- **Legacy** — `comm_0192_001_c005` `OVRS_PYMN_TYPE='M'` branch: one `EXPT_RESOL_MSTR` over a list of `OVRS_PYMN_MSTR` rows, one `EXPT_RESOL_DETL` per payment, stamp `APPR_SEQ_NO` on every master.
- **Spring** — `POST /request-approval-multi` (JSON `OverseasMultiApprovalRequest`) → `requestApprovalMulti(...)`. Validates every master first (`WBE2127`/`WBE2046`), then `stampApprovalMulti(...)` (sum totals, one `EXPT_RESOL_DETL` per payment, stamp `APPR_SEQ_NO` on each `OVRS_PYMN_MSTR`).
- **React** — not yet surfaced on a dedicated screen; endpoint available for the M-type approval path.

## 5. Change approval line

- **Legacy** — `comm_0195_001` edit-mode.
- **Spring** — `PATCH /{id}/approval-line` → `changeApprovalLine(...)`. Allowed only while derived status `P` (else `WBE2128`); `WBE2127` if transferred. Deletes + re-inserts the `EXPT_RESOL_APPRVR_INFM` chain.
- **React** — `ChangeApproverDialog` → `changeOverseasApprovalLine(id, body)`.

## 6. Remove approval line (retract)

- **Legacy** — `comm_0192_004_d001` cleanup.
- **Spring** — `DELETE /{id}/approval-line` → `removeApprovalLine(...)`. Allowed only while `P` (else `WBE2098`); `WBE2127` if transferred. Cleans bulletin/attachments, deletes chain + `EXPT_RESOL_MSTR`, clears `APPR_SEQ_NO` (batch returns to draft).
- **React** — `removeOverseasApprovalLine(id)`.

## 7. Approve / reject

- **Legacy** — `comm_0195_001` footer Approve/Reject → `bltb_0001_003_u001`.
- **Spring** — `POST /{id}/approval-action` (`ApprovalActionRequest`) → `actOnApproval(...)`. Guards `WBE2126` (no request yet), `WBE2127` (already transferred), `WBE2051` (caller not on chain), `WBE2052` (out-of-order — only the current `P` approver may act). Flips the caller's `EXPT_RESOL_APPRVR_INFM` row (`A`/`R`), promotes the next `W`→`P`, cascades downstream `W`→`S` on reject, and sets `EXPT_RESOL_MSTR.APPR_TYPE_CD` (`01` final-approve / `02` reject / `03` in-progress) so the derived status stays consistent.
- **React** — shared `<ApprovalDetailsDialog>` → `actOnOverseasApproval(id, {action})`.

## 8. Dispatch (Transfer)

- **Legacy** — `pymn_0007_001_c001_act.jsp:100-456` (type `S` path). Overseas always uses the **File** path (SWIFT/international wire, never domestic NAPAS API).
- **Spring** — `POST /{id}/dispatch` (no body) → `dispatch(...)`. Guards: `WBE2064` already dispatched, `WBE2063` not approved (`APPR_SEQ_NO` null or derived status ≠ `C`), `WBE2061` no lines. Runs `CutoffValidator` (SWIFT network → weekends blocked + per-bank window), then `OverseasDispatchExecutor.execute(...)` writes `TB_PAY_FILE_MST` + one `TB_PAY_FILE_DATA` per line, stamps shadow keys, then sets `TRNF_YN='Y'`/`TRNF_DT`. (`PROC_STAT` flip to `0`/`3` is the live JEX bank daemon's job — local R&D leaves it pending `9`.)
- **React** — Transfer button → `dispatchOverseasPayment(id)`.

## 9. Delete draft batch

- **Spring** — `DELETE /{id}` → `deleteDraftBatch(...)`. Only when `APPR_SEQ_NO` is null (`WBE2099` otherwise) and not transferred (`WBE2127`). Deletes detail lines then the summary.
- **React** — `deleteOverseasDraftBatch(id)`.

## 10. Reporting account assignment

- **Legacy** — `pymn_0007_001_u001` — patches `CHART_ACCT_SEQ_NO` + `CHART_ACCT_CD` together.
- **Spring** — `PATCH /{id}/details/{ovrsPymnDetlNo}/reporting-account` (body `{rprt_acct_cd, rprt_acct_seq_no}`) → `saveReportingAccount(...)`. Both code + seq_no persisted as a pair (seq_no is the `CHART_ACCT_INFM` PK identity). Gated by `payment-overseas-request` (same authority as the scheduled detail page, not the Result menu).
- **React** — inline picker on the Scheduled detail page → `saveOverseasReportingAccount(...)`.

## 11. Withdrawal accounts

- **Legacy** — `comm_1001_001` (module-agnostic).
- **Spring** — `GET /withdrawal-accounts?ccy_cd=` → shared `WithdrawalAccountListService.list(ccyCd)`.
- **React** — Upload modal Step 3 → `fetchOverseasWithdrawalAccounts(ccyCd?)`.

## 12. File download

- **Spring** — `GET /scheduled/{id}/file` → `OverseasPaymentExcelService.resolveDownload(...)`; streams the original Excel (octet-stream). `WBE2069` when the batch has no file / is unreadable.
- **React** — file card → `downloadOverseasPaymentFile(id)`.

## 13. Result inquiry (list + detail)

- **Legacy** — `pymn_0008_001_r001` (`IDO.OVRS_PYMN_SUMR_R005` for counts + computed `TRNF_STTS`; `getDynamicStatusFilter`).
- **Spring** — `GET /result` → `listResult(...)`. `proc_stat` CSV maps to computed `TRNF_STTS` letters (`0`→C, `3`→F, `6`/`9`→P) via `mapProcStatToStatusLetters`; footer totals are **never** status-filtered (R005_CNT parity). Each `OverseasResultRow` carries `success_count` / `failed_count` / `prs_cnt` (from `TB_PAY_FILE_MST.PROC_STAT` + `TB_PAY_FILE_DATA.RS_CD`), derived `trnf_stts`, and `appr_seq_no` (F4b). `GET /result/{id}` → single row. Result Detail loads via the **shared** `GET /scheduled/{id}/details` (carries per-line `proc_stat`/result fields).
- **React** — `PaymentResultPage` → `fetchOverseasResultList`; Result Detail → `fetchOverseasPaymentDetail`. Approval Ref cell is clickable (F4b) → `<ApprovalDetailsDialog>`.

## 14. Result export — two paths

- **List export** — `GET /result/export` → `OverseasPaymentService.exportResult(...)` → `OverseasPaymentResultExcelWriter` (22-column workbook, one row per detail line across all matching batches; M-mode batches emit one line from `OVRS_PYMN_MSTR`). Tees through a temp file to write an `ERP_TRNS_HSTR` 'W' audit row (code `14`) before streaming (best-effort, `REQUIRES_NEW`).
- **Per-batch export** — `POST /result/{id}/export` (body `ExcelExportRequest`) → injects `ovrsPymnNo` into the filter map → shared `ExcelExportService` + `OverseasPaymentResultDetailExportProvider` (screenId `payment-overseas-payment-result-detail`).
- **React** — `useExcelExport()` (list) + per-row export glyph (per-batch).

## 15. M-mode detail rendering

Result/Scheduled detail (`getDetail`) first tries `OVRS_PYMN_SUMR` (E-mode). On a miss it resolves a legacy **M-mode** record (lives only in `OVRS_PYMN_MSTR`) via `resolveLegacyMmodeOrThrow(...)`: it now renders the real remittance line(s) from `findMmodeDetailRows` (header withdrawal = debit-account fields per R006 M branch) with `legacyMmode=false` — **not** an empty/header-only response. `WBE2045` is thrown only when the id resolves in neither table. See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the residual PO/DBA decision on full per-line parity coverage.
