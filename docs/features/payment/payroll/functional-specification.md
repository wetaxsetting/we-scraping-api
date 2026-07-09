# Payroll Payment — Functional Specification

> Backend feature doc. Hub: [README.md](README.md). FE mirror: [wecms-web/.../payroll/ui-specification.md](../../../../../wecms-web/docs/features/payment/payroll/ui-specification.md).

## Scheduled Payment (`pymn_0005_001` / detail `pymn_0005_003`)

### Filters (`GET /`)
- `from_date` / `to_date` — `yyyyMMdd`, required; from ≤ to (else `WBE_VALIDATION`).
- `srch_wd` — keyword (LIKE, lower-cased).
- `appr_type` — approval-status filter (`All / Not requested / Pending / In Progress / Approved / Rejected`).
- `sort_field` / `sort_direction`, `page` / `size`.

### Columns (list — V232 legacy parity)
CHECKBOX · Date/Time (`REG_DTM`) · File Name (`FILE_NM`/`ORG_FILE_NM`) · No. of Transactions (`TOT_TRNS_CNT`) · Total Transfer Amount (`TOTAL_TRAN_AMT`) · Scheduled Date (`PAY_DATE`) · Approval Status (`APPR_STTS`, derived) · **Payroll Type (`APPR_FUND_TP`)** · Requestor (`DRAFT_USER_NM`) · Approver (`APPRVR_NM`) · Approval Ref (`APPR_DOC_NO` = `EXPT_RESOL_MSTR.DOC_NO`) · Approval Title (`TITL`) · ETC (action).

- **Sortable whitelist**: `PayrollPaymentSortField` (V232-aligned). `APPRVR_NM` and `APPR_FUND_TP` carry `SORT_YN='Y'` for legacy parity but are **not** sortable (project from joins the single-table paged query can't sort).
- Footer totals aggregate the **entire** filtered set (legacy R003), not the page slice.

### Detail (`GET /{id}`)
- Accepts numeric `SCDUL_PYMN_NO` **or** `DOC_NO` (`VO_YYMMDD_NNN`) — DOC_NO resolves via `EXPT_RESOL_MSTR.APPR_SEQ_NO`.
- Header: Withdrawal Bank, Account No (`accountNumber (currency)`), Total Transfer Amount, Approval Ref (`DOC_NO`), Requestor (`DRAFT_USER_NM`, blank for drafts).
- Lines = **5-column subset** of canonical `scheduledDetailColumns`: Deposit Bank, Recipient Name, Transfer Amount, Remarks, Reporting Account (inline chart-of-accounts picker).
- Attachments = UNION of `EXPT_RESOL_FILE` ∪ `EXPT_RESOL_PAY_EVID_FILE` by `APPR_SEQ_NO`.

### Actions
Delete draft (`WBE2062` if approval requested) · Request Approval (multipart, attachments) · Approve / Reject · Withdraw approval · Change approval line · Update reporting account (`WBE2064` if transferred) · Dispatch (Transfer) · Download original Excel.

## Payment Result (`pymn_0006_001` / detail `pymn_0006_002`)

### Filters (`GET /results`)
- `from_date` / `to_date`, `srch_wd`, sort, page/size.
- `trnf_stts` — **two-status** filter: `C` (Completed) / `P` (In Progress). Maps route-aware to `PROC_STAT`; neither/both → no filter. The footer totals are intentionally **not** status-filtered (legacy R008).
- Only dispatched batches (`TRNF_YN='Y'`) appear.

### Columns (list)
Date/Time (`TRAN_DT`/`TRNF_DTM`) · File Name (`FILE_NM`) · No. of Transactions · Total Transfer Amount · **Payroll Type (`APPR_FUND_TP`)** · Transfer Status (`PROC_STAT`) · Approval Ref (`APPR_DOC_NO`, clickable F4b when `appr_seq_no` present) · Approval Title (`EXPT_RESOL_MSTR.TITL`, fallback `SCDUL_PYMN_SUMR.TITL`) · download glyph. Totals footer rendered.

### Detail (`GET /results/{id}`)
- Numeric `SCDUL_PYMN_NO` only — `DOC_NO` rejected at the boundary (`RESULT_SCDUL_PYMN_NO_PATTERN`).
- Header: Withdrawal Bank (single-arg `wb_get_napas_bank_nm`), Account No, Total, Approval Ref (`DOC_NO`), Requestor.
- Status tabs + counts: **All / Success / Failed / In Progress** via `countByStatus` (success `proc_stat="0"` & `rs_cd="0000"`; in-progress blank/`9`/`1`/`6`; else failed). `success + failed + inProgress == lines`.
- Lines = **8-column subset** of canonical `resultDetailColumns`: Date/Time, Deposit Bank, Recipient Name, Transfer Amount, Remarks, Results (`RS_CD`), Result Date/Time, Reporting Account (read-only name + level breadcrumb).
- Banking Match (header-level 1:1, no Unmatch-All).

### Export (`POST /results/export`)
Excel export via shared `ExcelExportService` + `PayrollPaymentResultExportProvider` (screenId `payment-payroll-payment-result`, ERP `06`). **PARTIAL**: `APPR_FUND_TP` (Payroll Type) exports **blank** — the shared `PaymentResultExportProvider` base does not yet project it (G-PAYR-2; needs the `EXPT_RESOL_MSTR` join in the export path).

## Business rules
- Payroll Type (`APPR_FUND_TP`) is mandatory at Request Approval for `APPR_DV=21`; `"0"`=Salary, `"1"`=Bonus, `"9"`=Other; rendered Salary/Bonus/blank on lists.
- Status never persisted on the master — always `wb_get_appr_stts`; rejected = `R`.
- Dispatch is file-route only; cut-off-time validated pre-flight.
