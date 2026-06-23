# Approval Box — Interface Specification (Backend REST contract)

Base path: `/api/v1/payment/approval-box`. Controller: `controller/ApprovalBoxController.java`.
Wire JSON is lowercase snake_case (global Jackson `SNAKE_CASE`). Every endpoint is gated by `@PreAuthorize("@menuAuthz.canAccess('payment-approval-box')")`. All responses use the standard envelope `{ status, data, timestamp?, path?, trace_id? }`; business errors return HTTP 200 with `status.error = true`.

| # | Method | URI | Request | Response data |
|---|---|---|---|---|
| 1 | GET | `/api/v1/payment/approval-box` | query params | `ApprovalBoxListResponse` |
| 2 | GET | `/api/v1/payment/approval-box/{apprSeqNo}/approvers` | path | `ApprovalBoxApprover[]` |
| 3 | POST | `/api/v1/payment/approval-box/{apprSeqNo}/approve` | path | `ApprovalBoxActionResponse` |
| 4 | POST | `/api/v1/payment/approval-box/{apprSeqNo}/reject` | path | `ApprovalBoxActionResponse` |
| 5 | DELETE | `/api/v1/payment/approval-box/{apprSeqNo}/withdraw` | path | `null` |
| 6 | PUT | `/api/v1/payment/approval-box/{apprSeqNo}/approval-line` | path + body `ChangeApprovalLineRequest` | `null` |
| 7 | POST | `/api/v1/payment/approval-box/bulk-approve` | body `ApprovalBoxBulkApproveRequest` | `ApprovalBoxBulkApproveResponse` |
| 8 | POST | `/api/v1/payment/approval-box/export` | body `ExcelExportRequest` | XLSX stream (no envelope) |

**Shared Approval-Details-popup support endpoints** (same controller; consumed by the common popup opened from a header notification, not by the main list page):

| # | Method | URI | Request | Response data |
|---|---|---|---|---|
| 9 | GET | `/api/v1/payment/approval-box/detail` | query `appr_seq_no` OR `post_seq_no` (+ optional `bltb_cd`) | `ApprovalBoxRow` (`WBE_APPROVAL_DETAIL_NOT_FOUND` when missing/withdrawn/cross-tenant) |
| 10 | GET | `/api/v1/payment/approval-box/{apprSeqNo}/transactions` | path | `ApprovalTransactionRow[]` (per-line txns for the four scheduled kinds; card/overseas → `[]`) |
| 11 | GET | `/api/v1/payment/approval-box/{apprSeqNo}/attachments` | path | `ApprovalAttachmentRow[]` (`EXPT_RESOL_FILE` ∪ `EXPT_RESOL_PAY_EVID_FILE`) |
| 12 | GET | `/api/v1/payment/approval-box/{apprSeqNo}/attachments/{fileId}` | path + optional `fileType` (`EXPT_RESOL_FILE` default / `EXPT_RESOL_PAY_EVID_FILE`) | binary stream (`WBE2069` when missing) |

---

## 1. List — `GET /api/v1/payment/approval-box`

**Query params:**

| Param | Type | Required | Default | Notes |
|---|---|---|---|---|
| `from_date` | string `YYYYMMDD` | **yes** | — | filters `DRAFT_DATE` (inclusive) |
| `to_date` | string `YYYYMMDD` | **yes** | — | `≥ from_date` |
| `srch_wd` | string | no | — | keyword over doc-name / title / doc-no |
| `appr_type_cd` | string | no | (all) | `00`/`01`/`02`/`03` |
| `sort_field` | string | no | — | View Setting `FIELD_ID` (whitelist) |
| `sort_direction` | string | no | — | `ASC`/`DESC` |
| `page` | int | no | `0` | zero-based |
| `size` | int | no | `0`→15 | clamped `[1..100]` |

**Response `data` (`ApprovalBoxListResponse`):**

```json
{
  "list": [
    {
      "appr_seq_no": "1532",
      "doc_no": "VO_260605_007",
      "title": "Internal Transfer 2026-06",
      "file_name": "Internal_Transfer_260605.xlsx",
      "cont": "monthly settlement",
      "appr_dv": "35",
      "type_label": "internal_transfer",
      "currency": "VND",
      "tot_pay_amt": "150000000",
      "pay_date": "20260610",
      "draft_user_id": "lamine@gmail.com",
      "draft_user_nm": "Lamine",
      "reg_dtm": "20260605101500",
      "appr_stts": "I",
      "apprvr_tot_cnt": 3,
      "apprvr_appr_tot_cnt": 1,
      "pay_end_yn": "N",
      "approvers": [
        { "apprvr_line_no": 1, "apprvr_id": "u1", "apprvr_nm": "Approver 1", "apprvr_stts": "A", "appr_dtm": "20260605110000" },
        { "apprvr_line_no": 2, "apprvr_id": "u2", "apprvr_nm": "Approver 2", "apprvr_stts": "P", "appr_dtm": null }
      ]
    }
  ],
  "total_count": 42,
  "tab_counts": { "pending": 12, "in_progress": 8, "approved": 18, "rejected": 4 },
  "footer_totals": {
    "total_document_count": 42,
    "total_usd_amount": 20.00,
    "total_vnd_amount": 3600000,
    "total_krw_amount": 0
  },
  "page_number": 0,
  "page_size": 15,
  "total_pages": 3
}
```

- `tot_pay_amt` is a **string** (BigDecimal `toPlainString()`) to avoid JS precision loss.
- `appr_stts` ∈ `P`/`I`/`C`/`R` (derived from `APPR_TYPE_CD`).
- `apprvr_stts` per approver ∈ `P`/`A`/`R`/`W`/`S`.
- `tab_counts` ignore the selected `appr_type_cd` (all four chips reflect the same date/keyword/visibility scope).
- `footer_totals` (`ApprovalBoxFooterTotals`) — the on-screen footer bar. **TAB-UNFILTERED, full result set** (legacy `resl_0001_001` parity), distinct from `total_count` (tab-filtered, drives pagination):
  - `total_document_count` — tab-unfiltered row count (= sum of the four `tab_counts`).
  - `total_usd_amount` / `total_vnd_amount` / `total_krw_amount` — per-currency amount sums (JSON numbers; Jackson `BigDecimal`). Each row's amount is bucketed by its resolved `CCY_CD`; card rows (`appr_dv='33'`) use `SUM(CARD_TRNS_HSTR.TRSC_AMT)`. Only USD / VND / KRW are surfaced (the three the footer renders); other currencies are not in the buckets, matching legacy.

---

## 2. Approver chain — `GET .../{apprSeqNo}/approvers`

Response `data`: `ApprovalBoxApprover[]`, ordered by `apprvr_line_no ASC`:

```json
[ { "apprvr_line_no": 1, "apprvr_id": "u1", "apprvr_nm": "Approver 1", "apprvr_stts": "A", "appr_dtm": "20260605110000" } ]
```

Empty chain → `[]`. Blank `apprSeqNo` → `WBE_VALIDATION`.

---

## 3 & 4. Approve / Reject — `POST .../{apprSeqNo}/approve|reject`

No request body. Response `data` (`ApprovalBoxActionResponse`):

```json
{ "appr_seq_no": "1532", "appr_stts": "I", "apprvr_appr_tot_cnt": 2, "apprvr_tot_cnt": 3 }
```

`appr_stts` reflects the new document status after the action (`C` fully approved, `I` in progress, `R` rejected).

**Errors:** `WBE2050` not found · `WBE2051` not an approver · `WBE2052` not your turn · `WBE2053` already finalised · `WBE9999` null context.

---

## 5. Withdraw — `DELETE .../{apprSeqNo}/withdraw`

No body. Success → `data: null`. Only Pending/In-Progress documents may be withdrawn; otherwise `WBE_VALIDATION`; missing → `WBE_NOT_FOUND`. Deletes the master + chain and returns the source batch to Draft.

---

## 6. Change approval line — `PUT .../{apprSeqNo}/approval-line`

**Body (`ChangeApprovalLineRequest`):**
```json
{ "appr_line_no": 7 }
```
`@NotNull` + `@Positive` → `WBE_VALIDATION` otherwise. Success → `data: null`. Rebuilds the chain from the picked approval-line template and resets the document to Pending. Guards Pending/In-Progress (`WBE_VALIDATION`), not-found (`WBE_NOT_FOUND`), and empty template (`WBE_VALIDATION`).

---

## 7. Bulk approve — `POST .../bulk-approve`

**Body (`ApprovalBoxBulkApproveRequest`):**
```json
{ "appr_seq_nos": ["1532", "1533", "1534"] }
```
`@NotNull @NotEmpty @Size(max=100)`; >100/empty → `WBE2054`.

**Response (`ApprovalBoxBulkApproveResponse`):**
```json
{
  "successes": [ { "appr_seq_no": "1532", "appr_stts": "C", "apprvr_appr_tot_cnt": 3, "apprvr_tot_cnt": 3 } ],
  "failures":  [ { "appr_seq_no": "1533", "code": "WBE2052", "message": "Approval already actioned by this approver." } ]
}
```
Each id approves independently; one bad id does not fail the batch.

---

## 8. Export — `POST .../export`

**Body:** shared `ExcelExportRequest` — `screenId` must be `approval-box`; `filters` map keys `fromDate`, `toDate`, `apprType` (tab key: `all`/`pending`/`approved`/`rejected`/`in_progress`), `keyword`; `sort` (`field`/`direction`); `columns` (ordered, intersected server-side with the View Setting + the provider allow-list).

**Allow-listed FIELD_IDs (`ApprovalBoxExportProvider`):** `REG_DTM, DOC_NM, DOC_NO, TITL, FILE_NM, CURRENCY, TOT_PAY_AMT, PAY_END_YN, PAY_DATE, DRAFT_USER_NM` (10; the Approver column is excluded — multi-line cell doesn't flatten).

**Response:** HTTP 200, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, RFC 5987 `Content-Disposition` filename. Returns all matching rows (≤ 50,000). Subheader: left = `Date: DD-Mon-YYYY ~ DD-Mon-YYYY`; right = `<n> document(s) : Total USD … / Total VND … / Total KRW …`. The `PAY_END_YN` column carries the approval-status label (Pending/Approved/Rejected/In Progress).

**Errors:** `WBE2010` unknown screenId · `WBE2011` >50k rows · `WBE2013` no exportable columns after intersection.

---

## Error envelope example

```json
{
  "status":    { "error": true, "code": "WBE2052", "message": "Approval already actioned by this approver." },
  "timestamp": "2026-06-08T09:00:00Z",
  "path":      "/api/v1/payment/approval-box/1533/approve",
  "trace_id":  "20260608090000PT06592776769567DEVL"
}
```
