# Vendor Payment — Functional Specification (Backend)

Authoritative business rules for `vendor-payment` (Scheduled Payment + Payment Result). Code refs: `com.kosign.wecms.domain.payment.vendorpayment.*` + shared `internaltransfer.*` / `approvalbox.*` / `match.*`.

---

## 1. Scope

Vendor batch payments — Excel upload → request approval → transfer, plus a result-inquiry sub-module. Two sub-modules under one controller:

| Sub-module | JEX | Route | Authz |
|---|---|---|---|
| Scheduled Payment | `pymn_0001_001` (list) / `pymn_0001_002` (detail) | `/payment/vendor-payment/scheduled-payment` | `payment-vendor-scheduled` |
| Payment Result | `pymn_0002_001` (list) / `pymn_0002_002` (detail) | `/payment/vendor-payment/payment-result` | `payment-vendor-result` |

Discriminator: `SCDUL_PYMN_TYPE_CD = '01'` at every repository call.

---

## 2. Status model

### Approval status (Scheduled)

`SCDUL_PYMN_SUMR` has **no `APPR_STTS` column**. Status is derived via `wb_get_appr_stts(...)` over the `EXPT_RESOL_MSTR` join. Two-level model (shared with Approval Box):

| Level | Column | Values |
|---|---|---|
| Document | `EXPT_RESOL_MSTR.APPR_TYPE_CD` | `00` Pending · `03` In Progress · `01` Approved · `02` Rejected |
| Per-approver | `EXPT_RESOL_APPRVR_INFM.APPRVR_STTS` | `P` turn · `A` approved · `R` rejected · `W` waiting · `S` stopped |

Wire/derived approval codes: `C` approved, `R` rejected (**not** `X`); a draft (no `APPR_SEQ_NO`) reads as Not Requested.

### Transfer status (Result)

Route-aware, derived from `PROC_STAT`: Completed `C` / In Progress `P` (list filter); per-line detail status ∈ {success, failed, inProgress} from the dispatch-result resolver (`proc_stat` / `rs_cd`).

---

## 3. Scheduled list — filters, tabs, columns

| Filter | Wire param | Rule |
|---|---|---|
| Date range | `from_date`, `to_date` | required `YYYYMMDD`, `from ≤ to`; filters `REG_DTM` |
| Keyword | `srch_wd` | optional; title / approval-ref LIKE |
| Approval status | `appr_type` | optional: `00` Not Requested / `04` Pending / `03` In Progress / `01` Approved / `02` Rejected; applied server-side via `wb_get_appr_stts` |
| Sort | `sort_field`, `sort_direction` | whitelist `VendorPaymentSortField` |
| Paging | `page`, `size` | zero-based; `size` default 10 |

**Columns** (View-Setting-driven, V156): Date/Time · File Name · No. of Transactions · Total Transfer Amount · Scheduled Date · Approval Status · Requestor · Approver · Approval Ref · Approval Title. **Totals footer rendered** (`paginationWithTotals` — Vendor renders it, unlike Internal Transfer).

---

## 4. Result list — filters, status, columns

| Filter | Wire param | Rule |
|---|---|---|
| Date range | `from_date`, `to_date` | required `YYYYMMDD` |
| Keyword | `srch_wd` | optional |
| Transfer status | `trnf_stts` | Completed `C` / In Progress `P` (route-aware `PROC_STAT` EXISTS); both/neither = no filter |
| Sort | `sort_field`, `sort_direction` | whitelist `VendorPaymentResultSortField` (every column maps to a `ScdulPymnSumr` property) |

**Columns**: Date/Time · File Name · No. of Transactions · Total Transfer Amount · Transfer Status · Approval Ref · Approval Title.

**Footer totals (R008 parity):** per-currency totals come from `searchDispatchedCurrencyTotals(...)` — computed **unfiltered by the status checkboxes**, so the footer stays stable while the row list narrows.

**Approval Ref (F4b):** `DOC_NO` resolved from `EXPT_RESOL_MSTR` by `APPR_SEQ_NO` — clickable when the batch carries an `appr_seq_no`. Canonical home `EXPT_RESOL_MSTR.DOC_NO`, **never** `SCDUL_PYMN_SUMR.ERP_FILE_NUM`.

---

## 5. Actions & rules

| Action | Endpoint | Preconditions / rules |
|---|---|---|
| Create draft | `POST /` (multipart) | parsed + per-line validated Excel; keys via funnel |
| Delete draft | `DELETE /{id}` | `WBE2062` if already in approval |
| Request approval | `POST /{id}/request-approval` (multipart) | fan-out + attachments → `EXPT_RESOL_FILE` |
| Approve / reject | `POST /{id}/approval-action` | caller must be the pending approver; no source write-back |
| Withdraw | `DELETE /{id}/approval` | Pending/In-Progress only; nulls `APPR_SEQ_NO` |
| Change approval-line | `PUT /{id}/approval-line` | rebuilds chain; resets to `00` |
| Reporting-account | `PATCH /{id}/lines/{lineId}/reporting-account` | `WBE2064` once dispatched |
| Dispatch (Transfer) | `POST /{id}/dispatch` | cut-off-time guarded; approval-gated; `TRNF_YN='Y'` |
| Banking Match | `…/lines/{lineId}/match[/candidates]` + `…/matches` | domestic path, `ScdulPymnTypeCd.VENDOR.code()` |
| Result export | `POST /results/export` | provider `payment-vendor-payment-result`, ERP `04` |
| File download | `GET /{id}/file` | dispatched download writes 'W' audit (ERP `04`) |

---

## 6. Validation & error codes

| Code | Meaning |
|---|---|
| `WBE_VALIDATION` | date / period / path-pattern validation |
| `WBE2060` | batch not found / wrong tenant |
| `WBE2062` | delete/submit blocked — batch already in approval |
| `WBE2064` | reporting-account change blocked once dispatched |
| `WBE2069` | no file behind the batch |

Path guards: `SCDUL_PYMN_NO_PATTERN` accepts both numeric `scdul_pymn_no` **and** `DOC_NO` (`VO_YYMMDD_NNN`); `SCDUL_PYMN_DETL_NO_PATTERN` for line ids. Excel rows are validated per-row server-side (blocked-chars rules via shared `SpecialCharValidator`). Cut-off-time guard before Transfer.

---

## 7. Authorization

- Class-level `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-scheduled')")` gates the Scheduled flow (list / detail / delete / request-approval / approval-action / withdraw / change-line / reporting-account / dispatch / withdrawal-accounts / parse / create / file / Banking Match inherit it).
- Result endpoints (`/results`, `/results/{id}`, `/results/export`) override with `'payment-vendor-result'`.
- **Two id namespaces (do not conflate):** menu permission codes `payment-vendor-scheduled` / `payment-vendor-result` vs View-Setting / export `screenId`s `payment-vendor-payment-scheduled` / `payment-vendor-payment-result`.

---

## 8. Constants

`SCDUL_PYMN_TYPE_CD = "01"` (`ScdulPymnTypeCd.VENDOR.code()`); `WSVC_ID = "vendor-payment"` ([VendorPaymentService.java:113](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java#L113)); ERP `TRNS_TYPE_CD` = `02` (Scheduled upload/delete) / `04` (Result dispatched-download 'W' audit); export `screenId = "payment-vendor-payment-result"`, `erpResultTrnsTypeCd = "04"`.

---

## 9. Out of scope / dead

- **Re-run** (legacy `pymn_0010_002.ts`) — DEAD LEGACY, PO-deprecated; no endpoint/service/client (confirmed absent).
