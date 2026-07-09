# Flow 2 — Scheduled Transfer

Source-code-verified 2026-06-23. Backend authority: `InternalTransferController` + `InternalTransferService` + `InternalTransferDispatchService`. Frontend detail: [wecms-web/.../internal-transfer/scheduled-transfer-flow.md](../../../../../wecms-web/docs/features/payment/internal-transfer/scheduled-transfer-flow.md).

---

## Purpose

Manage scheduled internal-transfer batches: search the list, open the per-batch detail (master + lines), edit a line's Reporting Account, delete a draft, and **dispatch** (execute "Transfer") an approved batch to the bank network. Mirrors legacy `pymn_0009_001` (list/dispatch/delete) + `pymn_0009_003` (detail) + `pymn_0009_002_u002` (reporting account).

---

## User Actions

| Action | Endpoint | Service |
|---|---|---|
| **Search** (date range + keyword + approval-status tab) | `GET /api/v1/payment/internal-transfer` | `InternalTransferService.list` |
| **Filter** (`appr_type` `00/01/02/03/04`, `srch_wd`, paging, sort) | same | `ScdulPymnSumrRepository.search` |
| **View details** | `GET .../{id}` | `InternalTransferService.detail` |
| **Edit** (Reporting Account on a line) | `PATCH .../{id}/lines/{lineId}/reporting-account` | `updateReportingAccount` |
| **Cancel / Delete** (draft only) | `DELETE .../{id}` | `delete` |
| **Dispatch / Transfer** (the "T" capability) | `POST .../{id}/dispatch` | `InternalTransferDispatchService.dispatch` |
| **Download original Excel** | `GET .../{id}/file` | `resolveDownload` |
| Download approval attachment | `GET .../{id}/approval/{apprSeqNo}/attachments/{fileId}` | `resolveApprovalAttachmentDownload` |
| **Approval status tracking** | status derived on read via `wb_get_appr_stts(...)` over `EXPT_RESOL_MSTR` + approver chain — see [approval-flow.md](approval-flow.md) |

> **DEAD LEGACY — Re-run is NOT ported.** Earlier revisions of this doc listed a `POST .../{id}/lines/{lineId}/rerun` endpoint (`rerun` service, `WBE2122/2123/2124`). No such endpoint, service method, or error codes exist in the current code — `InternalTransferController` carries only a placeholder comment block. There is no frontend `rerunTransferLine` client either. Re-run was deprecated cross-module (see memory `project_payment_result_sit_ready.md` — "Re-run DEPRECATED by PO").

All gated by `@menuAuthz.canAccess('payment-internal-transfer-scheduled')`.

---

## Backend / API involvement

### Search (`list`)
`@Transactional(readOnly=true)`. Validates `from_date`/`to_date` (`YYYYMMDD`, from≤to → else `WBE_VALIDATION`). Builds a lowercase `%…%` LIKE pattern; clamps size `[1..200]` default 10; default sort `REG_DTM DESC, SCDUL_PYMN_NO DESC` (whitelist `InternalTransferSortField`). `ScdulPymnSumrRepository.search` filters `bizKey` + `scdulPymnTypeCd='03'` + `regDtm BETWEEN` + keyword (`titl`/`apprSeqNo`) + the **`apprType` mapping** (status derived in-query via `FUNCTION('wb_get_appr_stts', …)` over the LEFT-JOINed `EXPT_RESOL_MSTR`, NOT a stored `SCDUL_PYMN_SUMR.APPR_STTS` column): `00` Not-Requested = no `APPR_SEQ_NO` OR orphaned back-stamp (`erm.apprTypeCd IS NULL`); `01` approved = derived `'C'` OR `TRNF_YN='Y'`; `02` rejected = derived `'R'` (legacy code R, not X); `03` in-progress = `'I'`; `04` pending = `'P'`; unknown codes pass through as no-op. Returns `ScheduledTransferListResponse(totalCount, page, size, currencyTotals[], rows[])`. Per-page enrichment: `buildDocNoMap` (`EXPT_RESOL_MSTR.DOC_NO` by `APPR_SEQ_NO`), `buildPayDateMap` (`EXPT_RESOL_MSTR.PAY_DATE` = **Scheduled Date**), `buildFirstApproverMap` (first approver name + chain length + chain via `ExptResolApprvrInfmRepository.findChain`); per-batch `apprStts` derived via `LegacyFunctionRepository.apprSttsByApprSeqNos` (one batch `wb_get_appr_stts` query per page). Per-currency footer totals aggregate the **entire filtered result set** (`ScdulPymnSumrRepository.searchCurrencyTotals`, F-1), not just the current page slice.

### Detail (`detail`)
`@Transactional(readOnly=true)`. Resolves `id` as `SCDUL_PYMN_NO` first, else as a `DOC_NO` (→ `EXPT_RESOL_MSTR` → `APPR_SEQ_NO` → master). Missing → `WBE2060`. Lines via `ScdulPymnSumrDetlRepository.findByBizKeyAndScdulPymnNo` (sorted `SCDUL_PYMN_DETL_NO ASC`), each mapped by `TransferLineDetailMapper.toLineDetail` (resolves bank name + chart-account breadcrumb). Withdrawal bank name via `resolveBankNm(outBankCd)`. Approval fields read from `EXPT_RESOL_MSTR` by `APPR_SEQ_NO`: `requestor`=`DRAFT_USER_NM`, `cont`=`CONT`, `payDate`=`PAY_DATE`. Attachments from `EXPT_RESOL_FILE` (`ApprovalAttachmentRow[]`). Returns `ScheduledTransferDetailResponse` (master + `rows[]` + `attachments[]` + `cont` + `payDate`).

### Edit reporting account (`updateReportingAccount`)
`@Transactional`. Loads master (`WBE2060`); rejects if `TRNF_YN='Y'` (`WBE2064`); loads line (`WBE2061`); sets `chartAcctSeqNo`/`chartAcctCd` via dirty-checking; audit `update.detail`. (Note: `CHART_ACCT_INFM` existence re-check deferred per service comment.)

### Delete (`delete`)
`@Transactional`. Loads master; **rejects if `APPR_SEQ_NO` set** (`WBE2062` — batch in/after approval). Deletes lines then master; best-effort ERP-history "deleted" row (`ErpTrnsHistoryWriter.recordDeletedFile`); audit `delete.detail`.

### Dispatch (`InternalTransferDispatchService.dispatch`)
`@Transactional`. Loads master (`WBE2060`); rejects if already `TRNF_YN='Y'` (`WBE2064`); rejects if no `APPR_SEQ_NO` (`WBE2063`). Reads network from first line (`TransferNetwork.fromAntDivCd`), runs `CutoffValidator.validate(outBankCd, network)` (bank cut-off gate). Delegates to **`InternalTransferDispatchExecutor.execute(master, lines, trnfCcyCd)`** which applies the legacy routing rule and **writes the real dispatch rows**: VND/API path → one `TB_PAY_TARGET` per line + stamp `PAY_TRAN_DT`/`PAY_TRAN_SEQ`; non-VND/File path → `TB_PAY_FILE_MST` + N `TB_PAY_FILE_DATA` + stamp `PAY_FILE_SEQ_NO`. Then stamps `TRNF_YN='Y'`, `TRNF_DTM=now`; audit `dispatch.detail`. (Bank-network callback flipping `PROC_STAT` is the live JEX daemon — not run locally.)

### Re-run — NOT IMPLEMENTED (DEAD LEGACY)
There is no `rerun` service method, no `POST .../lines/{lineId}/rerun` endpoint, and no `WBE2122/2123/2124` error codes in the current code. Re-run was deprecated cross-module by the PO and never ported. See the DEAD LEGACY callout under User Actions above.

---

## Main UI components (frontend touchpoints)

- List page `ScheduledTransferPage.tsx` → shared `PaymentScheduledBatchListPage` (period dropdown + calendar + Approval-Status dropdown + search + Request-Approval CTA + per-row Transfer/Delete/Download chips + currency totals footer). Live-wired via `useApiData(fetchScheduledTransferList)`, server paging/sort, stable-data latch, View Setting (`SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_SCHEDULED`).
- Detail page `ScheduledTransferDetailPage.tsx` → shared `PaymentDetailPage` (DetailSummary + FileCard + line table). Live-wired via `useApiData(fetchScheduledTransferDetail)`; View Setting `…_SCHEDULED_DETAIL`. Reporting-account cell shows "Select Account" when unassigned (cream `#FFFEDB` cell highlight, legacy parity).
- **Scheduled Date** rendered from the list row `scheduleDate` / detail `pay_date` (source `EXPT_RESOL_MSTR.PAY_DATE`), NOT `PAY_TRAN_DT`.

---

## Database / persistence

| Table | Role |
|---|---|
| `SCDUL_PYMN_SUMR` / `_DETL` | master + lines (read; line `CHART_ACCT_*` update; `TRNF_YN`/`TRNF_DTM` stamp on dispatch; deleted on delete) |
| `EXPT_RESOL_MSTR` | read for `DOC_NO`, `PAY_DATE` (Scheduled Date), `DRAFT_USER_NM`, `CONT` |
| `EXPT_RESOL_APPRVR_INFM` | read for approver chain (`findChain`) |
| `EXPT_RESOL_FILE` | read for attachments |
| `TB_PAY_TARGET` / `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA` | written on dispatch (executor); read on Flow 3 (result) |
| `AUDIT_LOGS_INFM` | create/update/delete/dispatch/request-approval/approve/reject/withdraw/change-line audit rows |

---

## Legacy mapping

| Legacy | Refactor |
|---|---|
| `pymn_0009_001_r001_act.jsp` + `PymnHelper.pymn_0001_001_r001("03")` | `GET .../` → `list` → `ScdulPymnSumrRepository.search` |
| `pymn_0009_003_r001_act.jsp` (IDOs `SCDUL_PYMN_SUMR_R012`/`_R013`) | `GET .../{id}` → `detail` |
| `pymn_0009_002_u002_act.jsp` + `PymnHelper.pymn_0001_002_u002` | `PATCH .../lines/{lineId}/reporting-account` |
| `pymn_0009_001_d001_act.jsp` | `DELETE .../{id}` |
| `pymn_0009_001_c001_act.jsp` + `PymnHelper.pymn_0009_001_c001` (+ `validateTransferTiming`) | `POST .../{id}/dispatch` → `InternalTransferDispatchService` (+ `CutoffValidator`, `InternalTransferDispatchExecutor`) |
| `pymn_0010_002.ts:441-472` (ETC Re-run) | **DEAD LEGACY — not ported** (no refactor endpoint/service) |

---

## Legacy parity status

**MATCH** — list/filter/detail/edit/delete/dispatch all implemented with the legacy routing + cut-off gate. Re-run is **DEAD LEGACY** (deprecated by PO; not ported on either side). **PARTIAL** on dispatch only in that the local R&D tree has no bank daemon to flip `PROC_STAT` after the `TB_PAY_*` insert (documented; not a code gap).

## Refactor status

**Implemented.** (Supersedes the baseline "dispatch persists rows and stops" / "stub" note — the executor writes the full `TB_PAY_*` row set.)

## Open gaps (source-evidenced)

- `updateReportingAccount` defers the `CHART_ACCT_INFM` existence re-check (service comment) — bean-validation only.
- Dispatch's `EXPT_RESOL_MSTR.APPR_TYPE_CD == '01'` defensive read is deferred to a shared `isApproved(apprSeqNo)` service (service comment); the authoritative gate is `APPR_SEQ_NO` presence + the Approval Box state machine.
