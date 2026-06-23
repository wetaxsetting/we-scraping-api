# Vendor Payment — Interface Specification (Backend REST contract)

Base path: `/api/v1/payment/vendor-payment`. Controller: [`controller/VendorPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/controller/VendorPaymentController.java) (**~21 mappings**, one controller for both sub-modules).

Wire JSON is lowercase snake_case (global Jackson `SNAKE_CASE`). Class-level `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-scheduled')")`; Result endpoints override to `'payment-vendor-result'`. All JSON responses use the standard envelope `{ status, data, timestamp?, path?, trace_id? }`; business errors return HTTP 200 with `status.error = true`. Binary/XLSX endpoints stream without an envelope.

> The controller's class Javadoc still says "Phase A scope" / `authenticated()` — that is **stale**; the live gate is the `@menuAuthz` `@PreAuthorize` annotations on the class + the Result methods. DTOs are the shared `internaltransfer.dto.*` records (Vendor and Internal Transfer share the physical `SCDUL_PYMN_SUMR` table).

## Endpoint summary

| # | Method | URI | Authz | Request | Response data |
|---|---|---|---|---|---|
| 1 | GET | `/` | scheduled | query params | `ScheduledTransferListResponse` |
| 2 | GET | `/{id}` | scheduled | path | `ScheduledTransferDetailResponse` |
| 3 | GET | `/results` | result | query params | `TransferResultListResponse` |
| 4 | GET | `/results/{id}` | result | path | `TransferResultDetailResponse` |
| 5 | POST | `/results/export` | result | body `ExcelExportRequest` | XLSX stream |
| 6 | DELETE | `/{id}` | scheduled | path | `null` |
| 7 | POST | `/{id}/request-approval` | scheduled | **multipart** (`request` + `attachments`) | `RequestApprovalResponse` |
| 8 | POST | `/{id}/approval-action` | scheduled | body `ApprovalActionRequest` | `null` |
| 9 | DELETE | `/{id}/approval` | scheduled | path (withdraw) | `null` |
| 10 | PUT | `/{id}/approval-line` | scheduled | body `ChangeApprovalLineRequest` | `null` |
| 11 | PATCH | `/{id}/lines/{lineId}/reporting-account` | scheduled | body `UpdateReportingAccountRequest` | `null` |
| 12 | POST | `/{id}/dispatch` | scheduled | body `DispatchRequest` | `null` |
| 13 | GET | `/withdrawal-accounts` | scheduled | query `ccy_cd?` | `WithdrawalAccountRow[]` |
| 14 | POST | `/uploads/parse` | scheduled | **multipart** (`file_data` + `bank_cd`) | `ParseExcelResponse` |
| 15 | POST | `/` | scheduled | **multipart** (`request` + `file_data?`) | `CreateScheduledTransferResponse` |
| 16 | GET | `/{id}/file` | scheduled | path | binary (octet-stream) |
| 17 | GET | `/{id}/lines/{lineId}/match/candidates` | scheduled | query `match_rmrk_yn?`, `srch_wd?` | `PaymentLineMatchCandidateListResponse` |
| 18 | POST | `/{id}/lines/{lineId}/match` | scheduled | body `PaymentLineMatchRequest` | `PaymentLineMatchResultResponse` |
| 19 | DELETE | `/{id}/lines/{lineId}/match` | scheduled | path | `PaymentLineMatchResultResponse` |
| 20 | DELETE | `/{id}/matches` | scheduled | path (unmatch-all) | `PaymentLineUnmatchAllResponse` |

Path-segment guards: `SCDUL_PYMN_NO_PATTERN = ^([0-9]{1,30}|VO_[0-9]{6}_[0-9]{1,9})$` (accepts numeric `scdul_pymn_no` **or** `DOC_NO`); `SCDUL_PYMN_DETL_NO_PATTERN = ^[A-Za-z0-9]{1,20}$`.

---

## 1. Scheduled list — `GET /`

**Query params:** `from_date` (req `YYYYMMDD`), `to_date` (req, `≥ from_date`), `srch_wd?`, `appr_type?` (`00`/`01`/`02`/`03`/`04`), `sort_field?`, `sort_direction?` (`ASC`/`DESC`), `page` (default 0), `size` (default 10).

**Response (`ScheduledTransferListResponse`):** paged `list` (each row: scdul-pymn-no, doc no, file name, txn count, total amount, scheduled date, derived approval status, requestor, approver, **approval ref `DOC_NO`**, approval title) + `total_count` + per-currency **footer totals** + paging meta. Approval status derived via `wb_get_appr_stts`.

## 2. Scheduled detail — `GET /{id}`

`{id}` = numeric `scdul_pymn_no` OR `DOC_NO`. **Response (`ScheduledTransferDetailResponse`):** master (withdrawal bank/account, total, requestor, approval ref) + per-line list (deposit bank, recipient, amount, remarks, reporting-account, description, document no.) + attachments. `WBE2060` not found / wrong tenant.

## 3. Result list — `GET /results`

**Query params:** `from_date`, `to_date`, `srch_wd?`, `trnf_stts?` (`C` Completed / `P` In Progress — route-aware `PROC_STAT`), `sort_field?`, `sort_direction?`, `page`, `size`.

**Response (`TransferResultListResponse`):** paged dispatched batches + per-currency totals **(computed unfiltered by `trnf_stts` — R008 parity)**; each row carries the clickable **Approval Ref (`DOC_NO`)** + Approval Title (`TITL`) resolved by `APPR_SEQ_NO`.

## 4. Result detail — `GET /results/{id}`

**Response (`TransferResultDetailResponse`):** master header + canonical 11-column per-line result table; header tab counts `[success, failed, inProgress]` derived server-side (`countByStatus`); per-line status from `proc_stat` / `rs_cd`. Carries `appr_seq_no` (F4b). `WBE2060` not found.

## 5. Result export — `POST /results/export`

**Body:** shared `ExcelExportRequest` — `screenId = "payment-vendor-payment-result"`; `filters` (date range, status, keyword); `columns` (intersected server-side with View Setting ∩ provider allow-list). **Response:** HTTP 200, XLSX, RFC 5987 `Content-Disposition`. Provider `VendorPaymentResultExportProvider` (ERP `04`).

## 6–12. Write actions

- **Delete** `DELETE /{id}` → `null`. `WBE2062` when in approval.
- **Request approval** `POST /{id}/request-approval` — **multipart**: `request` part (`RequestApprovalRequest` JSON) + optional `attachments[]`; no-file submit omits the parts. → `RequestApprovalResponse`. Attachments → `EXPT_RESOL_FILE`.
- **Approval action** `POST /{id}/approval-action` — body `ApprovalActionRequest` (approve/reject) → `null`.
- **Withdraw** `DELETE /{id}/approval` → `null`. Pending/In-Progress only.
- **Change approval-line** `PUT /{id}/approval-line` — body `ChangeApprovalLineRequest` `{ appr_line_no }` (`@NotNull @Positive`) → `null`.
- **Reporting-account** `PATCH /{id}/lines/{lineId}/reporting-account` — body `UpdateReportingAccountRequest` → `null`. `WBE2064` once dispatched.
- **Dispatch** `POST /{id}/dispatch` — body `DispatchRequest` → `null`. Cut-off + approval gated; `TRNF_YN='Y'`.

## 13–16. Upload / accounts / file

- **Withdrawal accounts** `GET /withdrawal-accounts` (`ccy_cd?`) → `WithdrawalAccountRow[]`.
- **Parse** `POST /uploads/parse` — multipart `file_data` + `bank_cd` → `ParseExcelResponse` (per-line validation).
- **Create draft** `POST /` — multipart `request` (`CreateScheduledTransferRequest` JSON) + optional `file_data` → `CreateScheduledTransferResponse`.
- **Download file** `GET /{id}/file` — octet-stream, RFC 5987 filename, `X-Content-Type-Options: nosniff`. A dispatched-batch download writes an ERP 'W' / `VENDOR_PAYMENT_RESULT` (code `04`) audit row. `WBE2069` no file behind the batch.

## 17–20. Banking Match (per line, domestic path)

All call `DomesticPaymentLineMatchService` with `ScdulPymnTypeCd.VENDOR.code()`:

- **Candidates** `GET /{id}/lines/{lineId}/match/candidates` (`match_rmrk_yn?`, `srch_wd?`) → `PaymentLineMatchCandidateListResponse`.
- **Match** `POST /{id}/lines/{lineId}/match` — body `PaymentLineMatchRequest` → `PaymentLineMatchResultResponse`.
- **Unmatch one** `DELETE /{id}/lines/{lineId}/match` → `PaymentLineMatchResultResponse`.
- **Unmatch all** `DELETE /{id}/matches` → `PaymentLineUnmatchAllResponse`.

---

## Error envelope example

```json
{
  "status":    { "error": true, "code": "WBE2060", "message": "Batch not found." },
  "timestamp": "2026-06-23T09:00:00Z",
  "path":      "/api/v1/payment/vendor-payment/VO_260521_001",
  "trace_id":  "20260623090000PT06592776769567DEVL"
}
```
