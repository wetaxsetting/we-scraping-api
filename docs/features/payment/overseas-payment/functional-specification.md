# Overseas Payment — Functional Specification

> Filters, status, columns, totals, actions, validation, rules. Hub: [README.md](README.md). Verified against `OverseasPaymentService` + `Overseas*` DTOs on 2026-06-23.

## Scheduled Payment

### Filters
- `from_date` / `to_date` — required, `^[0-9]{8}$` (yyyyMMdd).
- `appr_type_cd[]` — repeatable approval-status filter (Not Requested / Pending / In Progress / Approved / Rejected). Omit for all.
- `keyword` — `@Size(max=100)`; partial match on file title (E-mode) or beneficiary name (M-mode), case-insensitive (ILIKE with `!`-escaped metacharacters).
- `page` (0-based) / `size` (default 10, clamped to 100).
- `sort_field` / `sort_direction` — whitelisted via `OverseasScheduledSortField`.

### Columns (list)
Checkbox · Date/Time · File Name · No. of Transactions · Total Transfer Amount · Approval Status (derived) · Requestor · Approver · Approval Ref · Approval Title · Schedule Date · Action. View-Setting-driven (`payment-overseas-payment-scheduled`).

### Detail columns (per line, `OVRS_PYMN_SUMR_DETL`)
Beneficiary name · beneficiary bank · BIC · beneficiary account · beneficiary address · currency · transfer amount · fee payer · memo · reporting account. View-Setting-driven (`payment-overseas-payment-scheduled-detail`).

## Payment Result

### Filters
- `from_date` / `to_date` — required, 8-digit.
- `proc_stat` — CSV of digits (`^[0-9]+(,[0-9]+)*$`). Checkboxes: **Completed = `0,3`**, **In Progress = `6`**. Mapped to computed `TRNF_STTS` letters: `0`→C, `3`→F, `6`/`9`→P. Absent = all.
- `keyword`, `page`/`size`, `sort_field`/`sort_direction` (`OverseasResultSortField`).

### Status semantics
- Derived per-batch `trnf_stts` ∈ {`C` completed, `F` all-error, `P` some-pending} (mirrors `IDO.OVRS_PYMN_SUMR_R005`).
- Per-batch tallies on each row: `success_count` (`PROC_STAT='0' AND RS_CD='0000'`), `failed_count` (`PROC_STAT='3'` or `'0'`+`RS_CD<>'0000'`), `prs_cnt` (in-progress = total − error − success).
- `trnf_yn`: `Y` completed/transferred, `N` in progress.

### Columns (list)
Date/Time · File Name · No. of Transactions · Total Transfer Amount · Transfer Status · **Approval Ref (clickable, F4b)** · Approval Title · Action (download) + Scheduled Date / Approval Status / Requestor / Approver. View-Setting-driven (`payment-overseas-payment-result`).

### Result detail columns
Loaded via the shared detail endpoint; per-line: Date/Time, Deposit/Beneficiary Bank, Recipient, Transfer Amount, Remarks, Results (proc_stat-derived), Result Date/Time, Reporting Account, Description, Document No., Banking Transaction ID/Match. View-Setting-driven (`payment-overseas-payment-result-detail`).

## Multi-currency totals
Both list and result return a per-currency totals footer (`OverseasCurrencyTotal`: `ccy_cd`, `totl_amt`, `tot_cnt_ccy_cd`, `tot_trns_cnt`). The **result footer is never status-filtered** (date + keyword only) — legacy R005_CNT parity.

## Actions & guards

| Action | Pre-conditions | Errors |
|---|---|---|
| Request approval | not transferred; not already approved/pending (rejected may re-submit); ≥1 approver | `WBE2127`, `WBE2046`, `WBE_VALIDATION` |
| Change approval line | derived status `P`; not transferred | `WBE2128`, `WBE2127` |
| Remove approval line | derived status `P`; not transferred | `WBE2098`, `WBE2127` |
| Approve / reject | request exists; not transferred; caller is current `P` approver | `WBE2126`, `WBE2127`, `WBE2051`, `WBE2052` |
| Dispatch | not transferred; approved (status `C`); ≥1 line; passes cutoff | `WBE2064`, `WBE2063`, `WBE2061` |
| Delete draft | `APPR_SEQ_NO` null; not transferred | `WBE2099`, `WBE2127` |
| Save reporting account | line exists | `WBE2045` |
| Download file | file present + readable | `WBE2069` |

## Validation
- Date params 8-digit; `keyword` ≤100; `proc_stat` CSV-of-digits — Bean Validation on the controller.
- Excel: per-cell validation in `OverseasPaymentExcelService.preview` (`MIN_COLUMNS=6`, `MAX_ROWS=500`); upload request `OverseasPaymentExcelUploadRequest` is `@Valid` (sized/positive fields).
- Reporting account: code + seq_no sent together (seq_no is the `CHART_ACCT_INFM` PK identity).

## Rules
- Tenant-scoped by `BIZ_KEY` (`TenantContext.currentBizKey()`).
- List/result **UNION** `OVRS_PYMN_SUMR` (E) ∪ `OVRS_PYMN_MSTR` (M) + LEFT JOIN `EXPT_RESOL_MSTR`; does **not** use `SCDUL_PYMN_TYPE_CD`.
- Approval status **derived** from `APPR_TYPE_CD` (no `APPR_STTS` column).
- Overseas always uses the **File** dispatch path (SWIFT); weekends blocked for the SWIFT network via `CutoffValidator`.

## M-mode behavior
M-mode records (`OVRS_PYMN_MSTR`-only) are listed alongside E-mode batches. Their **detail** is rendered with real remittance line(s) from `findMmodeDetailRows` (header withdrawal = `DBT_*` fields), `legacy_mmode=false` — not header-only. `WBE2045` only when an id resolves in neither table. The remaining full-per-line **parity-coverage** PO/DBA decision is in [refactor-gap-analysis.md](refactor-gap-analysis.md).

## Cross-links
- [interface-specification.md](interface-specification.md) · [business-flow.md](business-flow.md) · [database-mapping.md](database-mapping.md)
