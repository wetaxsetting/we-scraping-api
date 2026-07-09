# Expense Payment — Interface Specification (API)

> **Backend doc.** Source of truth = [`ExpensePaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java). Verified 2026-06-23.
> Frontend client (MATCH): [`lib/api/expensePayment.ts`](../../../../../wecms-web/lib/api/expensePayment.ts). Wire format = lowercase snake_case (`project_api_naming_lowercase.md`).

**Base URI:** `/api/v1/payment/expense-payment`
**Class-level authz:** `@PreAuthorize("@menuAuthz.canAccess('payment-expense-scheduled')")` ([:89](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java#L89)).
**Result endpoints add:** `@PreAuthorize("@menuAuthz.canAccess('payment-expense-result')")`.
**Common envelope:** `ApiResponse<T>`.

Path-segment guards: `id` = `^([0-9]{1,30}|VO_[0-9]{6}_[0-9]{1,9})$`; `lineId` = `^[A-Za-z0-9]{1,20}$`.

---

## Endpoint catalogue

| # | Method | Path | Authz | Service call | Legacy WSVC |
|---|---|---|---|---|---|
| 1 | GET | `/` | scheduled | `service.list` | `pymn_0003_001_r001` |
| 2 | GET | `/{id}` | scheduled | `service.detail` | `pymn_0003_002_r001` |
| 3 | GET | `/results` | result | `resultService.list` | `pymn_0004_001_r001` |
| 4 | GET | `/results/{id}` | result | `resultService.detail` | `pymn_0004_002_r001` |
| 5 | POST | `/results/export` | result | `excelExportService.exportTo` | — (refactor XLSX) |
| 6 | DELETE | `/{id}` | scheduled | `service.delete` | `pymn_0003_001_d001` |
| 7 | POST | `/{id}/request-approval` (multipart) | scheduled | `service.requestApproval` | `comm_0192_001_c005` |
| 8 | POST | `/{id}/approval-action` | scheduled | `service.actOnApproval` | `bltb_0001_003_u001` |
| 9 | DELETE | `/{id}/approval` | scheduled | `service.withdrawApproval` | — |
| 10 | PUT | `/{id}/approval-line` | scheduled | `service.changeApprovalLine` | — |
| 11 | PATCH | `/{id}/lines/{lineId}/reporting-account` | scheduled | `service.updateReportingAccount` | — |
| 12 | POST | `/{id}/dispatch` | scheduled | `service.dispatch` | `pymn_0003_001_c001` |
| 13 | GET | `/withdrawal-accounts` | scheduled | `service.listWithdrawalAccounts` | `comm_1001_001` |
| 14 | POST | `/uploads/parse` (multipart) | scheduled | `service.parseExcel` | `pymn_0003_002_r001` |
| 15 | POST | `/` (multipart) | scheduled | `service.createDraft` | `pymn_0003_002_c001` |
| 16 | GET | `/{id}/file` | scheduled | `service.resolveDownload` | `pymn_0001_003` download |
| 17 | GET | `/{id}/lines/{lineId}/match/candidates` | scheduled | `lineMatchService.listCandidates` | `comm_0197_001_r001` |
| 18 | POST | `/{id}/lines/{lineId}/match` | scheduled | `lineMatchService.match` | `comm_0197_001_c001` |
| 19 | DELETE | `/{id}/lines/{lineId}/match` | scheduled | `lineMatchService.unmatch` | `comm_0197_002_u001` |
| 20 | DELETE | `/{id}/matches` | scheduled | `lineMatchService.unmatchAll` | `comm_0197_001_u001` |

---

## Request / response detail

### 1. List Scheduled — `GET /`
Query: `from_date`*, `to_date`*, `srch_wd`, `appr_type`, `sort_field`, `sort_direction`, `page` (0), `size` (10, max 200).
Response: `ScheduledTransferListResponse` (total, page, size, currency totals, rows).

### 2. Scheduled detail — `GET /{id}`
Response: `ScheduledTransferDetailResponse` (master summary, lines, approver chain, attachments). `id` = numeric key or `DOC_NO`.

### 3. List Result — `GET /results`
Query: as #1, plus `trnf_stts` (Completed `C` / In Progress `P`; null = unfiltered).
Response: `TransferResultListResponse` (total, page, size, **footer currency totals across full dispatched set**, rows). Each row carries `appr_seq_no` + `doc_no` (clickable Approval Ref, F4b).

### 4. Result detail — `GET /results/{id}`
Response: `TransferResultDetailResponse` (master, outBankNm, `doc_no`, requestor, **suc/err/prc counts via `countByStatus`**, 11-column lines).

### 5. Export Result — `POST /results/export`
Body: `ExcelExportRequest`. Streams XLSX via `ExpensePaymentResultExportProvider` (`screenId = "payment-expense-payment-result"`, ERP `TRNS_TYPE_CD = "05"`).

### 6. Delete — `DELETE /{id}`
Deletes a draft batch. Response `Void`.

### 7. Request approval — `POST /{id}/request-approval` (multipart)
Parts: `request` (`RequestApprovalRequest` JSON) + optional `attachments[]`. Response `RequestApprovalResponse`. Stamps `APPR_SEQ_NO` via global `wb_get_next_val('sq_appr_seq_no')`; `ApprDv.EXPENSE`. Re-submit guarded.

### 8. Approval action — `POST /{id}/approval-action`
Body: `ApprovalActionRequest` (approve/reject). Approved → `C`, rejected → `R`.

### 9. Withdraw approval — `DELETE /{id}/approval`
Withdraws in-flight request (Approval Details kebab "Delete").

### 10. Change approval line — `PUT /{id}/approval-line`
Body: `ChangeApprovalLineRequest` (`apprLineNo`). Replaces approver chain (kebab "Edit").

### 11. Reporting-account PATCH — `PATCH /{id}/lines/{lineId}/reporting-account`
Body: `UpdateReportingAccountRequest`. Late-bind chart-of-accounts assignment.

### 12. Dispatch — `POST /{id}/dispatch`
Body: `DispatchRequest`. Approval gate (`WBE2063`) + cut-off + `ExpenseDispatchExecutor`. Errors: `WBE2089` (no `TB_FIRM_CONF`), `WBE2088` (VCB USD→other-bank).

### 13. Withdrawal accounts — `GET /withdrawal-accounts`
Query: optional `ccy_cd`. Response `List<WithdrawalAccountRow>`.

### 14. Parse Excel — `POST /uploads/parse` (multipart)
Parts: `file_data` (binary) + `bank_cd`. Response `ParseExcelResponse` (parsed + validated lines).

### 15. Create draft — `POST /` (multipart)
Parts: `request` (`CreateScheduledTransferRequest` JSON) + optional `file_data`. Response `CreateScheduledTransferResponse`. `SCDUL_PYMN_NO` via `nextScdulPymnNo` (one-shot race retry).

### 16. File download — `GET /{id}/file`
Streams the original Excel as `application/octet-stream` (Content-Disposition attachment). Errors: `WBE2060` (batch missing/not in tenant), `WBE2069` (no file behind the batch).

### 17–20. Banking match (domestic, `SCDUL_PYMN_TYPE_CD="02"`)
- 17 `GET .../match/candidates` — query `match_rmrk_yn`, `srch_wd` → `PaymentLineMatchCandidateListResponse`.
- 18 `POST .../match` — body `PaymentLineMatchRequest` → `PaymentLineMatchResultResponse`.
- 19 `DELETE .../match` → `PaymentLineMatchResultResponse`.
- 20 `DELETE /{id}/matches` → `PaymentLineUnmatchAllResponse`.

---

## Error codes

| Code | Where | Meaning |
|---|---|---|
| `WBE_VALIDATION` | list/result | bad date params |
| `WBE2060` | detail / file | batch missing or not in tenant |
| `WBE2063` | dispatch | not fully approved (`APPR_TYPE_CD ≠ "01"`) |
| `WBE2069` | file download | batch row exists but no file |
| `WBE2088` | dispatch (file path) | VCB USD → other-bank reject |
| `WBE2089` | dispatch (file path) | missing `TB_FIRM_CONF` (`BANK_PRTN_ID`) row |

---

Last verified: 2026-06-23.
