# Payroll Payment — Interface Specification

> Backend feature doc. Hub: [README.md](README.md). All endpoints under base **`/api/v1/payment/payroll-payment`** (note `-payment`, **not** `/payroll`). Controller: [`PayrollPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/controller/PayrollPaymentController.java). Wire format lowercase snake_case. Class-level `@PreAuthorize("@menuAuthz.canAccess('payment-payroll-scheduled')")`.

## Path-segment guards
- `SCDUL_PYMN_NO_PATTERN` = `^([0-9]{1,30}|VO_[0-9]{6}_[0-9]{1,9})$` (Scheduled routes — numeric key or `DOC_NO`).
- `RESULT_SCDUL_PYMN_NO_PATTERN` = `^[0-9]{1,30}$` (Result routes — numeric only; `DOC_NO` → `WBE_VALIDATION`).
- `SCDUL_PYMN_DETL_NO_PATTERN` = `^[A-Za-z0-9]{1,20}$` (line id).

## Endpoints

| # | Method | Path | Authz (method) | Body / params | Returns |
|---|---|---|---|---|---|
| 1 | GET | `/` | class (scheduled) | `from_date`, `to_date` (req); `srch_wd`, `appr_type`, `sort_field`, `sort_direction`, `page`, `size` | `ScheduledTransferListResponse` |
| 2 | GET | `/{id}` | class | path `id` (key or DOC_NO) | `ScheduledTransferDetailResponse` |
| 3 | GET | `/results` | `payment-payroll-result` | `from_date`, `to_date` (req); `srch_wd`, `trnf_stts` (`C`/`P`), sort, page/size | `TransferResultListResponse` |
| 4 | GET | `/results/{id}` | `payment-payroll-result` | path `id` (numeric) | `TransferResultDetailResponse` |
| 5 | POST | `/results/export` | `payment-payroll-result` | `ExcelExportRequest` | streamed XLSX |
| 6 | DELETE | `/{id}` | class | path `id` | `Void` |
| 7 | POST | `/{id}/request-approval` | class | **multipart**: `request` (`RequestApprovalRequest`) + `attachments[]` | `RequestApprovalResponse` |
| 8 | POST | `/{id}/approval-action` | class | `ApprovalActionRequest` | `Void` |
| 9 | DELETE | `/{id}/approval` | class | path `id` | `Void` (withdraw) |
| 10 | PUT | `/{id}/approval-line` | class | `ChangeApprovalLineRequest` (`appr_line_no`) | `Void` |
| 11 | PATCH | `/{id}/lines/{lineId}/reporting-account` | class | `UpdateReportingAccountRequest` | `Void` |
| 12 | POST | `/{id}/dispatch` | class | `DispatchRequest` | `Void` |
| 13 | GET | `/{id}/match/candidates` | class | `srch_wd` | `PayrollMatchCandidateListResponse` |
| 14 | POST | `/{id}/match` | class | `PayrollMatchRequest` | `PayrollMatchResultResponse` |
| 15 | DELETE | `/{id}/match` | class | path `id` | `PayrollMatchResultResponse` (unmatch) |
| 16 | GET | `/withdrawal-accounts` | class | `ccy_cd` | `List<WithdrawalAccountRow>` |
| 17 | POST | `/uploads/parse` | class | **multipart**: `file_data`, `bank_cd` | `ParseExcelResponse` |
| 18 | POST | `/` | class | **multipart**: `request` (`CreateScheduledTransferRequest`), `file_data` | `CreateScheduledTransferResponse` |
| 19 | GET | `/{id}/file` | `payment-payroll-result` **OR** `payment-payroll-scheduled` | path `id` | `InputStreamResource` (XLSX) |

> Endpoint #19 is intentionally widened to accept either menu so a Result-only user can download from the Result detail (closure-audit D1). Write actions keep the class-level `scheduled` gate. Counting the two multipart variants of `/` separately gives ~18–19 mappings.

## Key DTO fields (reused from `internaltransfer.dto`)

- **`ScheduledTransferRow`** (list) — `scdul_pymn_no`, `scdul_pymn_type_cd`, `reg_dtm`, `org_file_nm`, `tot_trns_cnt`, `total_tran_amt`, `pay_tran_dt`, `appr_stts`, **`appr_fund_tp`**, `reg_id`/requestor, approver summary, `doc_no`, `titl`.
- **`TransferResultRow`** (result list) — `scdul_pymn_no`, `ccy_cd`, `titl`, `org_file_nm`, `total_tran_amt`, `tot_trns_cnt`, `succ`, `err`, `trnf_yn`, `reg_dtm` (= `TRNF_DTM`), `appr_seq_no`, `doc_no`, **`appr_fund_tp`**.
- **`TransferResultDetailResponse`** — header + `suc_cnt`/`err_cnt`/`prc_cnt` + `lines[]` + banking-match (`link_match_yn`, `acct_seq_no`, `bank_trns_short_id`, `bank_trns_full_id`).
- **`RequestApprovalRequest`** — `appr_line_no`, `approvers[]`, `post_title`, `post_cont`, withdrawal account (`bank_cd`/`acct_no`/`ccy_cd`), `pay_date`, **`appr_fund_tp`**.

## Common errors
`WBE_VALIDATION` (bad params / DOC_NO on result route) · `WBE2060` (not found) · `WBE2061` (line not found) · `WBE2062` (already requested / delete blocked) · `WBE2063` (not approved on dispatch) · `WBE2064` (already transferred) · `WBE2069` (file unavailable) · `WBE2126` (no approval request) · `WBE2127` (already final) · `WBE_SCHEDULED_PAYMENT_KEY_RACE` (key-gen race exhausted).
