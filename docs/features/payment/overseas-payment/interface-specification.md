# Overseas Payment — Interface Specification

> Every endpoint on [`OverseasPaymentController`](../../../../src/main/java/com/kosign/wecms/domain/payment/controller/OverseasPaymentController.java). Base `/api/v1/payment/overseas-payment`. Single controller. Routes use **`/result`** (singular). Wire format = lowercase snake_case (global Jackson SNAKE_CASE). Hub: [README.md](README.md). Verified 2026-06-23.

**Authz:** class default `@menuAuthz.canAccess('payment-overseas-request')`. Result endpoints (`/result*`) override to `payment-overseas-result`. Reporting-account override re-asserts `payment-overseas-request`.

**Envelope:** `ApiResponse<T>` (`status` + `data` + `trace_id`). Errors via the standard envelope; `WBE_VALIDATION` on bad params, `WBE9999` catch-all.

---

## Scheduled

### `GET /scheduled` — `listScheduled`
- Authz: request. Params: `from_date`*, `to_date`* (`^[0-9]{8}$`), `appr_type_cd[]`, `keyword` (≤100), `page` (0), `size` (0=default 10, max 100), `sort_field`, `sort_direction`.
- Returns `OverseasScheduledListResponse { total_count, page, size, rows: OverseasScheduledRow[], totals: OverseasCurrencyTotal[] }`.
- `OverseasScheduledRow`: `ovrs_pymn_no, ccy_cd, ovrs_pymn_type_cd, titl, appr_seq_no, total_tran_amt, tot_trns_cnt, reg_dtm, draft_user_id, draft_user_nm, appr_stts, trnf_yn, apprvr_appr_tot_cnt, apprvr_tot_cnt, appr_dv, doc_no, file_url, out_bank_cd, out_bank_nm, wdrl_act_no, bnfc_nm, ovrs_pymn_type ('E'/'M'), pay_date, file_nm, reg_id, approvers: OverseasApprover[]`.
- `OverseasApprover`: `apprvr_id, apprvr_nm, apprvr_stts, apprvr_line_no, mod_dtm, reg_img_url`.

### `GET /scheduled/{id}` — `getScheduledById`
- Returns a single `OverseasScheduledRow`. `id` = `OVRS_PYMN_NO` or `DOC_NO` (fallback resolves `DOC_NO → APPR_SEQ_NO → OVRS_PYMN_SUMR`). `WBE_NOT_FOUND` when unknown.

### `GET /scheduled/{id}/details` — `getScheduledDetail`
- Returns `OverseasPaymentDetailResponse`: `ovrs_pymn_no, titl, appr_stts, total_tran_amt, ccy_cd, tot_trns_cnt, reg_id, reg_dtm, mod_dtm, out_bank_cd, wdrl_act_no, wdrl_ccy_cd, doc_no, appr_seq_no, file_nm, file_size, org_file_nm, rows: OverseasPaymentDetailRowResponse[], attachments: ApprovalAttachmentRow[], legacy_mmode, legacy_mmode_notice`.
- `OverseasPaymentDetailRowResponse`: `ovrs_pymn_detl_no, bnfc_code, ccy_cd, trnf_amt, fee_payer_cd, memo, rprt_acct_cd, bnfc_nm, bnfc_bank_nm, bic_cd, bnfc_acct_no, bnfc_addr, trnf_yn, proc_stat, proc_stat_key, proc_dttm, rslt_dttm, rs_cd, rs_msg`.
- Shared by the Result Detail screen (carries per-line `proc_stat`/result fields). M-mode renders real line(s); `legacy_mmode=false`. `WBE2045` only when found in neither table.

### `GET /scheduled/{id}/file` — `downloadFile`
- Octet-stream of the original uploaded Excel. `WBE2069` when missing/unreadable.

---

## Approval & lifecycle

### `POST /{id}/request-approval` — `requestApproval`
- **multipart/form-data**: `request` (JSON `RequestApprovalRequest`) + `attachments[]` (optional). Returns `RequestApprovalResponse { appr_seq_no, doc_no }`.
- Errors: `WBE2127` (transferred), `WBE2046` (already approved/pending), `WBE_VALIDATION` (no approvers).

### `POST /request-approval-multi` — `requestApprovalMulti`
- JSON `OverseasMultiApprovalRequest { ovrs_pymn_nos[]*, appr_line_no*, post_title (≤200), post_cont (≤4000), pay_date (≤8), approvers[1..5]* }`. One `EXPT_RESOL_MSTR` over many `OVRS_PYMN_MSTR`. Errors `WBE2127`, `WBE2046`, `WBE_NOT_FOUND`.

### `PATCH /{id}/approval-line` — `changeApprovalLine`
- JSON `ChangeApprovalLineRequest`. Allowed while derived status `P`. Errors `WBE2128`, `WBE2127`. Returns `Void`.

### `DELETE /{id}/approval-line` — `removeApprovalLine`
- Retract to draft. Errors `WBE2098`, `WBE2127`. Returns `Void`.

### `DELETE /{id}` — `deleteDraftBatch`
- Only when `APPR_SEQ_NO` null. Errors `WBE2099`, `WBE2127`. Returns `Void`.

### `POST /{id}/approval-action` — `approvalAction`
- JSON `ApprovalActionRequest` (approve/reject). Errors `WBE2126`, `WBE2127`, `WBE2051`, `WBE2052`. Returns `Void`.

### `POST /{id}/dispatch` — `dispatch`
- No body. Writes `TB_PAY_FILE_MST`/`TB_PAY_FILE_DATA`, stamps `TRNF_YN='Y'`/`TRNF_DT`. Errors `WBE2064`, `WBE2063`, `WBE2061`, plus cutoff blocks. Returns `Void`.

### `PATCH /{id}/details/{ovrsPymnDetlNo}/reporting-account` — `saveReportingAccount`
- Authz override: **request**. Body `{rprt_acct_cd, rprt_acct_seq_no}` (both, or both null to clear). `WBE2045` when line missing. Returns `Void`.

### `GET /withdrawal-accounts` — `listWithdrawalAccounts`
- Optional `ccy_cd`. Returns `List<WithdrawalAccountRow>` via shared `WithdrawalAccountListService`.

---

## Excel

### `POST /excel-preview` — `excelPreview`
- multipart `file` + optional `bank_cd`. Returns `OverseasPaymentExcelPreview { rows: OverseasPaymentExcelPreviewRow[], total_count, completed_count, error_count }`. Each row carries per-cell `errors: OverseasPaymentExcelFieldError[] { field, message }`.

### `POST /excel-upload` — `excelUpload`
- multipart `file` + `data` (JSON `OverseasPaymentExcelUploadRequest { file_nm, ccy_cd, total_tran_amt, tot_trns_cnt, wdrl_bank_cd, wdrl_bank_nm, wdrl_acct_no, rows: OverseasPaymentExcelDetailRow[] }`). Returns `OverseasPaymentExcelUploadResult { ovrs_pymn_no }`.

---

## Result

### `GET /result` — `listResult`
- Authz: **result**. Params: `from_date`*, `to_date`*, `keyword` (≤100), `proc_stat` (`^[0-9]+(,[0-9]+)*$`), `page`, `size`, `sort_field`, `sort_direction`.
- Returns `OverseasResultListResponse { total_count, page, size, rows: OverseasResultRow[], totals: OverseasCurrencyTotal[] }`.
- `OverseasResultRow`: `ovrs_pymn_no, ccy_cd, file_nm, total_tran_amt, tot_trns_cnt, reg_dtm, draft_user_nm, reg_id, out_bank_nm, out_bank_cd, wdrl_act_no, trnf_yn, doc_no, titl, success_count, failed_count, pay_date, appr_stts, apprvr_nm (null — join deferred), `**`appr_seq_no`** ` (F4b), trnf_stts, prs_cnt`.

### `GET /result/{id}` — `getResultById`
- Authz: result. Single `OverseasResultRow`. `WBE_NOT_FOUND` when unknown.

### `GET /result/export` — `exportResult` (LIST export)
- Authz: result. Same params as `listResult` (minus paging). Streams XLSX via `OverseasPaymentResultExcelWriter` — 22 columns, one row per detail line across matching batches (M-mode emits one line from `OVRS_PYMN_MSTR`). Writes an `ERP_TRNS_HSTR` 'W' audit row (code `14`) before streaming.

### `POST /result/{id}/export` — `exportResultDetail` (PER-BATCH export)
- Authz: result. Body `ExcelExportRequest`. Injects `ovrsPymnNo` into the filter map → shared `ExcelExportService` + `OverseasPaymentResultDetailExportProvider` (screenId `payment-overseas-payment-result-detail`, allowed FIELD_IDs: `BNFC_BANK_NM, BNFC_ACCT_NO, BNFC_NM, CCY_CD, TRNF_AMT, MEMO, TRNF_YN`). Streams XLSX.

---

## Banking match (Result Detail — frontend-consumed)
The Result Detail screen consumes per-line banking-match endpoints under the overseas base (candidates / match / unmatch / unmatch-all) — see [source-code-index.md](source-code-index.md) §Frontend (`fetchOverseasMatchCandidates`, `matchOverseasPaymentLine`, `unmatchOverseasPaymentLine`, `unmatchAllOverseasPaymentLines`). Backend match handling reuses the shared payment-line match service.

## Cross-links
- [functional-specification.md](functional-specification.md) · [business-flow.md](business-flow.md) · [database-mapping.md](database-mapping.md) · [source-code-index.md](source-code-index.md)
