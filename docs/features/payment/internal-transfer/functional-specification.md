# Internal Transfer — Functional Specification (Backend)

> **Hub:** [README.md](README.md) · **Flow narrative:** [business-flow.md](business-flow.md) · **Endpoints:** [interface-specification.md](interface-specification.md)

Source-code-verified 2026-06-23. Filters, status tabs, columns, actions, validation, and business rules per sub-module. Field-level rules cross-link to the per-flow docs.

---

## 1. Scheduled Transfer sub-module

### 1.1 Filters (`GET /api/v1/payment/internal-transfer`)

| Param (wire) | Type | Required | Rule |
|---|---|---|---|
| `from_date` | `YYYYMMDD` | ✅ | `from ≤ to` else `WBE_VALIDATION` |
| `to_date` | `YYYYMMDD` | ✅ | constrains `REG_DTM` |
| `srch_wd` | string | optional | lowercase `%…%` LIKE over `TITL` / `APPR_SEQ_NO` |
| `appr_type` | `00`/`01`/`02`/`03`/`04` | optional | approval-status tab (status derived in-query via `wb_get_appr_stts`) |
| `sort_field` / `sort_direction` | string | optional | whitelisted `InternalTransferSortField`; default `REG_DTM DESC, SCDUL_PYMN_NO DESC` |
| `page` / `size` | int | optional | 0-based; `size` clamped `[1..200]`, default 10 |

### 1.2 Status tabs + counts

Approval-status tabs map (status derived from `EXPT_RESOL_MSTR`, no stored column):

| Tab | `appr_type` | Derivation |
|---|---|---|
| Not Requested | `00` | no `APPR_SEQ_NO` OR orphaned back-stamp (`erm.apprTypeCd IS NULL`) |
| Approved | `01` | derived `'C'` OR `TRNF_YN='Y'` |
| Rejected | `02` | derived `'R'` (legacy code R, not X) |
| In Progress | `03` | derived `'I'` |
| Pending | `04` | derived `'P'` |

Per-currency footer totals (`currencyTotals[]`) aggregate the **entire filtered result set** via `searchCurrencyTotals` (F-1), not the current page.

### 1.3 List columns

Row (`ScheduledTransferRow`): `scdulPymnNo`, `titl`, withdrawal bank name + `wdrlActNo`, `ccyCd`/`trnfCcyCd`, `totalTranAmt`, `totTrnsCnt`, `docNo` (from `EXPT_RESOL_MSTR.DOC_NO`), **`scheduleDate`** (from `EXPT_RESOL_MSTR.PAY_DATE`, NOT `PAY_TRAN_DT`), derived `apprStts`, first-approver name + chain length + chain, `apprSeqNo`, `trnfYn`, `regDtm`.

### 1.4 Detail (`GET /{id}`)

`id` resolves as `SCDUL_PYMN_NO` first, else as a `DOC_NO` (→ `EXPT_RESOL_MSTR` → `APPR_SEQ_NO` → master). Missing → `WBE2060`. Returns master + `rows[]` (per-line via `TransferLineDetailMapper`) + `attachments[]` (`EXPT_RESOL_FILE`) + `cont` (`EXPT_RESOL_MSTR.CONT`) + `payDate`.

### 1.5 Actions + validation

| Action | Endpoint | Validation / guards |
|---|---|---|
| Edit reporting account | `PATCH /{id}/lines/{lineId}/reporting-account` | master exists (`WBE2060`); not transferred (`WBE2064`); line exists (`WBE2061`). `CHART_ACCT_INFM` existence re-check deferred. |
| Delete draft | `DELETE /{id}` | master exists (`WBE2060`); reject if `APPR_SEQ_NO` set (`WBE2062`) |
| Dispatch / Transfer | `POST /{id}/dispatch` | `trnf_ccy_cd` required 3-letter ISO; master exists (`WBE2060`); not already transferred (`WBE2064`); must have `APPR_SEQ_NO` (`WBE2063`); cut-off gate (`CutoffValidator`) |
| Download Excel | `GET /{id}/file` | `WBE2060` missing batch / `WBE2015` no file |
| Banking match | `GET/POST/DELETE /{id}/lines/{lineId}/match` + `DELETE /{id}/matches` | domestic per-line match (legacy `comm_0197_*`, `SCDUL_PYMN_TYPE_CD='03'`) |

### 1.6 Create / parse rules

See [upload-create-flow.md](upload-create-flow.md):
- **Parse** (`POST /uploads/parse`): extension `.xls/.xlsx`, non-empty, `bank_cd` present; header per-locale (EN/KO/VI) → `WBE2018`; per-cell required-field + bank-code + amount + special-char checks; cross-row single-currency (`WBE2120`) + single-network CITAD-vs-NAPAS (`WBE2121`).
- **Create** (`POST /`): `titl` ≤100 required; `bank_cd` ≤20 required; `acct_no` ≤100 required; `ccy_cd`/`trnf_ccy_cd` 3-letter ISO required; `lines` ≥1, each line `finc_mda_no` required.

---

## 2. Transfer Result sub-module

Read-only (`R`-flag only). No mutations.

### 2.1 Filters (`GET /results`)

| Param | Type | Rule |
|---|---|---|
| `from_date` / `to_date` | `YYYYMMDD` | constrains **`TRNF_DTM`** (dispatch datetime), not `REG_DTM` (F-1) |
| `srch_wd` | string | keyword |
| `trnf_stts` | `C`/`P` | route-aware dispatch-result status: `C` Completed / `P` In Progress; unknown/blank → no filter (C2) |
| `sort_field`/`sort_direction` | string | whitelist `InternalTransferResultSortField`; default `TRNF_DTM DESC, SCDUL_PYMN_NO DESC` |
| `page`/`size` | int | as above |

Hard-filtered to `TRNF_YN='Y'` (only dispatched batches).

### 2.2 Columns

Row (`TransferResultRow`): master + per-batch succ/err counts (`DispatchCountRepository`) + `trnfYn` + `apprSeqNo` + `docNo` + `apprFundTp` (null for Internal Transfer). The wire `reg_dtm` field carries `TRNF_DTM`; Approval Title from `EXPT_RESOL_MSTR.TITL` (F-3); File Name from `ORG_FILE_NM`. **No totals footer** on this Result list (Internal Transfer is the only Result list with `paginationOnly`).

### 2.3 Detail (`GET /results/{id}`)

`DispatchResultResolver.resolve(master, lines)` reads per-line `PROC_STAT` / `RS_CD` / `RS_MSG` / `PROC_DTTM` / `RSLT_DTTM` from `TB_PAY_*`/`TB_FIRM_TRAN`. Header tab counts `[success, failed, inProgress]` via `countByStatus` (success = `procStat='0'` + `rsCd='0000'`; in-progress = `procStat∈{9,1,6}` or both blank; else failed). Per-line also surfaces banking-match read fields.

### 2.4 Export (`POST /results/export`)

Pass-through to shared `ExcelExportService` → `InternalTransferResultExportProvider` (`screenId='payment-internal-transfer-result'`), re-runs the same list query path, streams XLSX.

---

## 3. Permissions

| WSVC | Flags | Refactor surface |
|---|---|---|
| `pymn_0009_001` (Scheduled) | R / U / D / T | list/detail (R), reporting-account (U), delete (D), dispatch (T) — `@menuAuthz.canAccess('payment-internal-transfer-scheduled')` |
| `pymn_0010_001` (Result) | R only | GET + export only — `@menuAuthz.canAccess('payment-internal-transfer-result')` |

Tenant scope: `TenantContext.currentBizKey()` on every query. Approval gating + dispatch eligibility enforced server-side.

---

## 4. Business rules summary

- A batch must be **Approved** (have `APPR_SEQ_NO` + Approval Box state) before dispatch is allowed.
- Once `APPR_SEQ_NO` is set, the batch cannot be deleted or re-requested (`WBE2062`).
- Reject is **terminal** — no re-submit (intentional legacy parity, see [refactor-gap-analysis.md](refactor-gap-analysis.md)).
- Dispatch routing: API path (`TB_PAY_TARGET`) for VND / Woori USD→VND; File path otherwise.
- Re-run = DEAD LEGACY (not ported either side).

## 5. Cross-links

[business-flow.md](business-flow.md) · [database-mapping.md](database-mapping.md) · [interface-specification.md](interface-specification.md) · [test-scenarios.md](test-scenarios.md)
