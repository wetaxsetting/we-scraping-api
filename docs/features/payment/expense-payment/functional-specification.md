# Expense Payment — Functional Specification (API)

> **Backend doc.** Frontend counterpart: [wecms-web/docs/features/payment/expense-payment/ui-specification.md](../../../../../wecms-web/docs/features/payment/expense-payment/ui-specification.md).
> Source of truth = latest code. Verified 2026-06-23.

Two sub-modules: **Scheduled Payment** (`pymn_0003_*`) and **Payment Result** (`pymn_0004_*`).

---

## A. Scheduled Payment (list)

### Filters (`GET /api/v1/payment/expense-payment`)
- `from_date` / `to_date` — required `YYYYMMDD`, `to_date ≥ from_date`.
- `srch_wd` — optional title / approval-ref LIKE.
- `appr_type` — optional approval-status filter.
- `sort_field` / `sort_direction` — whitelisted via `ExpensePaymentSortField`; `ASC`/`DESC`.
- `page` (default 0) / `size` (default 10, max 200).

### Columns
Checkbox · Date/Time · File Name · No. of Transactions · Total Transfer Amount · Scheduled Date (`PAY_DATE`) · Approval Status · Requestor (`DRAFT_USER_NM`) · Approver · Approval Ref (`APPR_DOC_NO` / `EXPT_RESOL_MSTR.DOC_NO`) · Approval Title · Action. **Totals footer** (currency totals under Total Transfer Amount).

### Actions
Upload (parse + save draft) · Delete draft · Request Approval (multipart, attachments) · Approve/Reject · Withdraw approval · Change approval line · Reporting-account PATCH · Banking match (×4) · Dispatch (Transfer) · File download.

### Rules
- Status **derived** via `wb_get_appr_stts` — rejected = `R`, not `X`. No `APPR_STTS` column.
- Dispatch gated on full approval (`APPR_TYPE_CD = "01"`) → `WBE2063` otherwise.
- Reporting-account is late-bind (PATCH), not mandated at create.

---

## B. Scheduled Payment Details (`GET /{id}`)
Master summary (Withdrawal Bank / Account No / Total / Approval Ref / Requestor) + original-file card + per-line transaction table (chart-account breadcrumb / Reporting Account picker) + approver chain + combined attachments (`EXPT_RESOL_FILE ∪ EXPT_RESOL_PAY_EVID_FILE`). `id` accepts the numeric key OR the user-visible `DOC_NO` (`VO_YYMMDD_NNN`).

---

## C. Payment Result (list — `GET /results`)

### Filters
Same date/search/sort/page params, plus **`trnf_stts`** — route-aware PROC_STAT: **Completed `C` / In Progress `P`** (NOT `Y`/`N`). null = no status filter.

### Status tabs + counts
List filter buckets: Completed (`C`) / In Progress (`P`). The footer totals are computed across the **full** filtered+dispatched set regardless of the checkbox (F1) — legacy R008 sums all dispatched rows.

### Columns
Date/Time (`TRNF_DTM`) · File Name · No. of Transactions · Total Transfer Amount · Transfer Status · Approval Ref (clickable, F4b — `EXPT_RESOL_MSTR.DOC_NO`) · Approval Title (`EXPT_RESOL_MSTR.TITL`, blank-fallback to `SCDUL_PYMN_SUMR.TITL`) · per-row download glyph (F2 `W` ERP audit). **Totals footer rendered** (Expense renders it, unlike Internal Transfer).

### Actions
Open detail · Excel export (`POST /results/export`) · per-row original-Excel download.

---

## D. Payment Result Details (`GET /results/{id}`)

### Header tab counts
All / Success / Failed / In Progress — derived by `ExpensePaymentResultService.countByStatus` from the resolved per-line statuses:
- blank `proc_stat` AND blank `rs_cd` → In Progress
- `proc_stat="0"` AND `rs_cd="0000"` → Success
- `proc_stat ∈ {9,1,6}` → In Progress
- everything else → Failed

Invariant: `success + failed + inProgress == line count`.

### Columns (canonical 11-column `resultDetailColumns`)
Date/Time · Deposit Bank · Recipient Name · Transfer Amount · Remarks ⓘ · Results · Result Date/Time · Reporting Account · Description · Document No. · Banking Transaction ID.

---

## Validation summary

| Concern | Rule |
|---|---|
| Date params | `^[0-9]{8}$`, `from ≤ to` → else `WBE_VALIDATION` |
| `id` path | numeric (1–30) OR `VO_[0-9]{6}_[0-9]{1,9}` |
| `lineId` path | `^[A-Za-z0-9]{1,20}$` |
| `size` | clamped to 1..200 (default 10) |
| Excel rows | per-cell server-side validation (`ExpensePaymentExcelParser`) |
| Dispatch | approval gate `WBE2063`, cut-off `CutoffValidator`, firm-conf `WBE2089`, VCB USD→other-bank `WBE2088` |

---

Last verified: 2026-06-23. Endpoint-level detail: [interface-specification.md](./interface-specification.md).
