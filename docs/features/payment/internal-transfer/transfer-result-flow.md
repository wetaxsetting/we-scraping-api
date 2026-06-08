# Flow 3 — Transfer Result

Source-code-verified 2026-06-08. Backend authority: `TransferResultController` + `InternalTransferResultService` + `InternalTransferResultExportProvider`. Frontend detail: [wecms-web/.../internal-transfer/transfer-result-flow.md](../../../../../wecms-web/docs/features/payment/internal-transfer/transfer-result-flow.md).

---

## Purpose

Read-only view of **executed** internal-transfer batches and their per-line bank-side results (success / failed / in-progress, with response code/message). Mirrors legacy `pymn_0010_001` (list) + `pymn_0010_002` (detail). Per the JEX permission matrix this surface carries only the `R` flag — the controller exposes **GET endpoints + an export only**, no mutations.

---

## User Actions

| Action | Endpoint | Service |
|---|---|---|
| **Search** (date range + keyword + `trnf_stts`) | `GET /api/v1/payment/internal-transfer/results` | `InternalTransferResultService.list` |
| **Filter** (`trnf_stts` Y/N, paging, sort) | same | `ScdulPymnSumrRepository.searchDispatched` |
| **Detail view** (per-line results + status tab counts) | `GET .../results/{id}` | `detail` |
| **Download / export** (XLSX) | `POST .../results/export` | shared `ExcelExportService` + `InternalTransferResultExportProvider` |

Gated by `@menuAuthz.canAccess('payment-internal-transfer-result')`.

---

## Backend / API involvement

### Result list (`list`)
`@Transactional(readOnly=true)`. Validates date range. Uses **`searchDispatched`** — same scope as Flow 2's `search` but hard-filtered to `TRNF_YN='Y'` (only batches the user actually dispatched; un-dispatched batches in the approval flow are excluded). Optional `trnf_stts` (`Y`/`N`) further narrows. Default sort `REG_DTM DESC, SCDUL_PYMN_NO DESC` (whitelist `InternalTransferResultSortField`). Per-batch (succ, err) counts via `DispatchCountRepository.countByBatch` (degrades to null counts if the dispatch tables are unreachable). Returns `TransferResultListResponse(totalCount, page, size, currencyTotals[], rows[])` (`TransferResultRow` = master + succ/err counts + `TRNF_YN` + `APPR_SEQ_NO` + `ERP_FILE_NUM`).

### Result detail (`detail`)
`@Transactional(readOnly=true)`. Loads master (`WBE2060`) + lines. **`DispatchResultResolver.resolve(master, lines)`** reads back the per-line dispatch result from `TB_PAY_TARGET`/`TB_PAY_FILE_MST`/`TB_PAY_FILE_DATA`/`TB_FIRM_TRAN` (API-vs-File path hidden behind the resolver) → `DispatchResult(procStat, procStatKey, procDttm, rsltDttm, rsCd, rsMsg)` per `SCDUL_PYMN_DETL_NO`. Each line → `TransferResultLine` (line fields + resolved bank name + the 6 dispatch-result fields + banking-match read fields `LINK_MATCH_YN`/`ACCT_SEQ_NO`/`BANK_TRNS_FULL_ID`). **Header tab counts** `[success, failed, inProgress]` computed by `countByStatus` over the resolved lines (rule: `procStat='0'`+`rsCd='0000'`→success; `procStat∈{9,1,6}` or both blank→in-progress; else→failed — chip-consistent with frontend `deriveResultStatus.ts`). Requester name from `EXPT_RESOL_MSTR.DRAFT_USER_NM`. Returns `TransferResultDetailResponse` (master + counts + `rows[]`).

### Export (`POST .../results/export`)
Thin controller pass-through to the shared `ExcelExportService.exportTo`. Provider `InternalTransferResultExportProvider` (`screenId='payment-internal-transfer-result'`) re-runs the same list query path and streams XLSX (POI), per CLAUDE.md §"Excel export".

---

## Main UI components (frontend touchpoints)

- List page `TransferResultPage.tsx` → shared `PaymentResultBatchListPage` (period + calendar + Completed/In-Progress checkboxes + search + status counter pills + trailing per-row download glyph). **Internal Transfer Result is the only Result list with NO totals footer** (`paginationOnly`, Figma 1:11730). Live-wired via `useApiData(fetchTransferResultList)`.
- Detail page `TransferResultDetailPage.tsx` → shared `ResultDetailView` (adds `DetailStatusTabs` All/Success/Failed/In-Progress + the 11-column Result detail column set incl. Date/Time, Results, Result Date/Time, Banking Transaction ID). Per-line status via `PaymentResultBadge` + `deriveResultStatus.ts`. Live-wired via `useApiData(fetchTransferResultDetail)`.

---

## Database / persistence

Read-only. `SCDUL_PYMN_SUMR`/`_DETL` (master + lines), `TB_PAY_TARGET`/`TB_PAY_FILE_MST`/`TB_PAY_FILE_DATA`/`TB_FIRM_TRAN` (dispatch results via `DispatchResultResolver` + `DispatchCountRepository`), `EXPT_RESOL_MSTR` (requester name), `BANK_INFM` (bank-name resolve). No writes (export streams bytes only).

---

## Legacy mapping

| Legacy | Refactor |
|---|---|
| `pymn_0010_001_r001_act.jsp` + `PymnHelper.pymn_0002_001_r001(INTERNAL)` | `GET .../results` → `list` → `searchDispatched` |
| `pymn_0010_002_r001_act.jsp` + `PymnHelper.pymn_0002_002_r001(INTERNAL)` | `GET .../results/{id}` → `detail` |
| `SCDUL_PYMN_SUMR_R006` (per-line dispatch result IDO) | `DispatchResultResolver.resolve` |
| (client-side XLSX) | `POST .../results/export` → `InternalTransferResultExportProvider` |

---

## Legacy parity status

**MATCH** — read list, per-line result detail (real dispatch-result resolution from `TB_PAY_*`/`TB_FIRM_TRAN`), tab counts, and server-side XLSX export all implemented. Read-only `R`-flag-only surface matches the JEX permission matrix.

## Refactor status

**Implemented.** (Supersedes the baseline note that dispatch-level fields "ship as null in Phase A" — `DispatchResultResolver` now resolves them.)

## Open gaps (source-evidenced)

- `DispatchCountRepository.countByBatch` degrades to empty counts (no error) when the dispatch tables are unreachable on a fresh local DB — the list chips disappear and the row reads "In Progress" via the frontend count-math. Documented degradation, not a bug.
- Local R&D has no bank daemon, so seeded `TRNF_YN='Y'` batches show whatever `PROC_STAT`/`RS_CD` the seed carries.
