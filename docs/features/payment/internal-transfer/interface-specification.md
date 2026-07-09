# Internal Transfer — Interface Specification (Backend REST)

> **Hub:** [README.md](README.md) · **WEB consumption:** [wecms-web/.../api-consumption.md](../../../../../wecms-web/docs/features/payment/internal-transfer/api-consumption.md)

Source-code-verified 2026-06-23 against `InternalTransferController` + `TransferResultController`. Wire format is lowercase snake_case (global Jackson `SNAKE_CASE`). All bodies wrapped in `ApiResponse<T>`.

Base paths:
- Scheduled: `/api/v1/payment/internal-transfer` — `@PreAuthorize("@menuAuthz.canAccess('payment-internal-transfer-scheduled')")`
- Result: `/api/v1/payment/internal-transfer/results` — `@PreAuthorize("@menuAuthz.canAccess('payment-internal-transfer-result')")`

Path-param patterns: `id` (`scdul_pymn_no`) = `^([0-9]{1,30}|VO_[0-9]{6}_[0-9]{1,9})$` (column itself `VARCHAR(10)`; regex permissive for seed/doc-no); `lineId` (`scdul_pymn_detl_no`) = `^[A-Za-z0-9]{1,20}$`.

---

## Scheduled Transfer endpoints

### `GET /` — list
Query: `from_date`✅, `to_date`✅, `srch_wd`, `appr_type`, `sort_field`, `sort_direction`, `page`(=0), `size`(=10).
Response `ScheduledTransferListResponse`: `total_count`, `page`, `size`, `currency_totals[]`, `rows[]` (`ScheduledTransferRow` — `scdul_pymn_no`, `titl`, withdrawal bank/account, `ccy_cd`/`trnf_ccy_cd`, `total_tran_amt`, `tot_trns_cnt`, `doc_no`, `schedule_date`, `appr_stts`, first approver/chain, `appr_seq_no`, `trnf_yn`, `reg_dtm`).

### `GET /withdrawal-accounts` — picker
Query: `ccy_cd` (optional). Response `WithdrawalAccountRow[]`. Legacy `comm_1001_001`.

### `GET /{id}` — detail
Response `ScheduledTransferDetailResponse`: master + `rows[]` (`TransferLineDetail`) + `attachments[]` (`ApprovalAttachmentRow`) + `cont` + `pay_date`. Errors: `WBE2060`.

### `GET /{id}/file` — original Excel download
Octet-stream + RFC 5987 `Content-Disposition`. Errors: `WBE2060` (batch), `WBE2015` (no file).

### `GET /{id}/approval/{apprSeqNo}/attachments/{fileId}` — approval attachment download
`apprSeqNo` digits ≤15; `fileId` `^[A-Za-z0-9_-]+$` ≤100; query `fileType` ∈ `{EXPT_RESOL_FILE, EXPT_RESOL_PAY_EVID_FILE}` (default `EXPT_RESOL_FILE`). Errors: `WBE2069`.

### `GET /next-doc-no` — preview next doc no
Response `NextDocNoResponse` (`VO_YYMMDD_NNN` preview; authoritative number stamped at request-approval).

### `POST /uploads/parse` — Excel parse (multipart)
Parts: `file_data` (file)✅, `bank_cd` (`@RequestParam`)✅. Response `ParseExcelResponse`: `tot_cnt`, `suc_cnt`, `err_cnt`, `lines[]` (`ParsedTransferLine` with `err_fld` / `err_fld_caus`). Errors: `WBE_VALIDATION`, `WBE_UNSUPPORTED_MEDIA_TYPE`, `WBE2018`, `WBE2017`, `WBE2120`, `WBE2121`.

### `POST /` — create draft (multipart)
Parts: `request` (JSON `CreateScheduledTransferRequest`)✅, `file_data` (optional).
`CreateScheduledTransferRequest`: `titl`✅≤100, `bank_cd`✅≤20, `acct_no`✅≤100, `ccy_cd`✅ `^[A-Z]{3}$`, `trnf_ccy_cd`✅ `^[A-Z]{3}$`, `lines`✅≥1 (`@Valid TransferLineInput` — `finc_mda_no` required per line).
Response `CreateScheduledTransferResponse` (`scdul_pymn_no`). Errors: bean-validation; `WBE2070` file write.

### `PATCH /{id}/lines/{lineId}/reporting-account`
Body `UpdateReportingAccountRequest` (`chart_acct_seq_no`, `chart_acct_cd`). Errors: `WBE2060`, `WBE2064`, `WBE2061`.

### `POST /{id}/dispatch`
Body `DispatchRequest` (`trnf_ccy_cd`✅ `^[A-Z]{3}$`). Errors: `WBE2060`, `WBE2064` (already transferred), `WBE2063` (not approved), cut-off-gate rejection.

### `DELETE /{id}` — delete draft
Errors: `WBE2060`, `WBE2062` (`APPR_SEQ_NO` set).

### `POST /{id}/request-approval` — request approval (multipart)
Parts: `request` (JSON `RequestApprovalRequest`)✅, `attachments` (file list, optional).
`RequestApprovalRequest`: `post_title`✅≤200, `post_cont`≤4000, `bank_cd`✅≤14, `acct_no`✅≤20, `ccy_cd`✅≤3, `pay_date`✅ `\d{8}`, `appr_fund_tp`≤1 (Payroll only; null for Internal Transfer), `appr_line_no`✅, `approvers`✅ 1..5 (`apprvr_id`/`apprvr_nm`), `files` (optional).
Response `RequestApprovalResponse` (`appr_seq_no`, `doc_no`). Errors: `WBE2062` (already requested).

### `DELETE /{id}/approval` — withdraw
Errors: `WBE2126` (no `APPR_SEQ_NO`); refuses if closed.

### `PUT /{id}/approval-line` — change approval line
Body `ChangeApprovalLineRequest` (`appr_line_no`). Errors: `WBE2126`; refuses if closed.

### `POST /{id}/approval-action` — approve / reject
Body `ApprovalActionRequest` (`action` ∈ `{APPROVE,REJECT}`✅, `comment`≤1000). Errors: `WBE2126` (no `APPR_SEQ_NO`), `WBE2127` (already final).

### Banking match
- `GET /{id}/lines/{lineId}/match/candidates` (query `match_rmrk_yn`, `srch_wd`) → `PaymentLineMatchCandidateListResponse`
- `POST /{id}/lines/{lineId}/match` (body `PaymentLineMatchRequest`) → `PaymentLineMatchResultResponse`
- `DELETE /{id}/lines/{lineId}/match` → `PaymentLineMatchResultResponse`
- `DELETE /{id}/matches` → `PaymentLineUnmatchAllResponse`

All pass `ScdulPymnTypeCd.INTERNAL.code()` (`'03'`) to `DomesticPaymentLineMatchService`.

---

## Transfer Result endpoints

### `GET /results` — list
Query: `from_date`✅, `to_date`✅, `srch_wd`, `trnf_stts` (`C`/`P`), `sort_field`, `sort_direction`, `page`(=0), `size`(=10).
Response `TransferResultListResponse`: `total_count`, `page`, `size`, `currency_totals[]`, `rows[]` (`TransferResultRow` — master + succ/err counts + `trnf_yn` + `appr_seq_no` + `doc_no` + `appr_fund_tp` (null); `reg_dtm` carries `TRNF_DTM`).

### `GET /results/{id}` — detail
Response `TransferResultDetailResponse`: master + tab counts + `rows[]` (`TransferResultLine` — line fields + resolved bank name + 6 dispatch-result fields + banking-match read fields). Errors: `WBE2060`.

### `POST /results/export` — XLSX export
Body `ExcelExportRequest` (`@Valid`). Streams XLSX directly to `HttpServletResponse` via `ExcelExportService` + `InternalTransferResultExportProvider`.

---

## DEAD LEGACY — Re-run

No `rerun` endpoint exists. The controller carries only a placeholder comment block; no `@PostMapping`, no service method, no client. Re-run was never ported (PO-deprecated cross-module).

## Cross-links

[business-flow.md](business-flow.md) · [functional-specification.md](functional-specification.md) · [database-mapping.md](database-mapping.md) · [source-code-index.md](source-code-index.md)
