# Transfer History — Refactor Gap Analysis (Parity Review)

> Legacy `trns_0003_001` vs refactor `domain/banking/transferhistory/` + `wecms-web` page.
> Verified 2026-06-06 against the implemented code on disk.

## 1. Scope boundary (read this first)

The original documentation request listed **matching, unmatch, candidate search, detail view, and export** as expected areas. After inspecting the actual implementation, **none of those belong to the Transfer History feature**. Transfer History (`trns_0003_001`) is a **read-only inquiry over `NAPAS_REMIT_TRNS_HSTR`** with no matching, no detail popup, and no export — confirmed in legacy JEX (the screen calls only the two read IDOs `R003` + `R003_CNT`) and in the refactor (the `transferhistory` package exposes exactly one GET endpoint).

The matching/unmatch/candidate capability is a **separate feature** — Banking › Transaction History **bank-matching** (Smart Organizer, legacy `comm_0196_*`), wired into `AccountController`:

- `GET    /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match-candidates`
- `POST   /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match`
- `DELETE /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match`

…over `ACCT_TRNS_HSTR` / `SCDUL_PYMN_SUMR(_DETL)` / `BUSI_MATCH_LOG`. Those are documented under that feature, **not** here. In the parity table below, matching/detail/export rows are marked **N/A (out of feature scope)** — they are not "missing" work for Transfer History.

## 2. Parity table

| Area | Legacy (`trns_0003_001`) | Refactor | Status |
|---|---|---|---|
| Search (date range) | from/to `YYYYMMDD`, `substring(REG_DTM,1,8) BETWEEN` | identical, via Specification | ✅ MATCH |
| Date filters | required from/to; end ≥ start; **no max range** | identical (incl. no max-range cap) | ✅ MATCH |
| Status filter | `""`/`00000000`/`99999999`; `= '00000000'` vs `<> '00000000'` | identical wire; null-safe `<>` | ✅ MATCH |
| History grid | 6 columns (date/time, from, to, amount, remarks, status) | same 6 columns | ✅ MATCH |
| Transaction status | binary: `00000000`=success else failed; `TRNS_MSG` tooltip | identical | ✅ MATCH |
| Sorting | `TRNS_DT`/`OUT_BANK_NM`/`IN_BANK_NM`/`TRSC_AMT`/`RMRK` | same whitelist + `seqNo DESC` tie-break | ✅ MATCH |
| Pagination | `PageInfo`; page size + page no | Spring `Pageable`; whitelist `10/15/20/50/100`, default 15 | ✅ MATCH |
| Totals (VND) | VND amount/count in footer | VND amount/count on wire + footer | ✅ MATCH |
| Totals (USD) | computed by IDO but **not rendered** (legacy gap R2) | computed on wire; **not rendered** in footer today | ⚠️ PARTIAL (parity with legacy gap — USD ready on wire) |
| User / bank names | DB functions `WB_GET_USER_NM`, `wb_get_bank_nm`, `WB_GET_NAPAS_BANK_NM` | Java resolvers + ported `wb_get_napas_bank_nm_or_citad_bank_nm` (V114) | ✅ MATCH (in-bank keyed on NAPAS code) |
| UTC→display zone | `normalizeTrnsDtFrmUTC` | `Asia/Ho_Chi_Minh` normalization | ✅ MATCH |
| Permissions | `NORM_USER_ONLY_YN=Y` (excludes WTA users) | JWT only, no `@PreAuthorize` (OQ-5) | ✅ MATCH |
| Audit logging | none | none (OQ-4) | ✅ MATCH |
| Error handling | period validation | `WBE_TRANSFER_HISTORY_INVALID_PERIOD` + `WBE_VALIDATION` | ✅ MATCH |
| Detail view | none | none | ✅ N/A (feature has no detail) |
| Vendor matching | none | none | ✅ N/A (separate feature) |
| Expense matching | none | none | ✅ N/A (separate feature) |
| Unmatch / Unmatch-all | none | none | ✅ N/A (separate feature) |
| Match candidate search | none | none | ✅ N/A (separate feature) |
| Match status display | none | none | ✅ N/A (separate feature) |
| Export (XLSX) | none | none | ✅ N/A (feature has no export) |

## 3. Verdict

```
Refactor Status (Transfer History feature): 100% Complete — legacy parity verified
```

**Supporting evidence:**
- One read endpoint (`GET /api/v1/banking/internal-transfer/transfer-history`) implementing the full legacy `trns_0003_001_r001` behaviour: tenant-scoped, date-range + status filtered, paged, sorted, with per-currency totals.
- Backend: controller + service + repository + 4 DTOs + sort whitelist + entity + helper-index migration, all present on disk.
- Tests: 7 controller + 15 service tests covering validation, snake_case, status mapping, UTC normalization, NAPAS-code name resolution, sort/page rules, caches, locale collapse.
- Frontend: route page + client + page + table + colgroup + footer wired to the live endpoint (no mock fallback), 6 columns, date-range + status-radio filters, shared pagination + sort, EN/KO/VI locale coverage.
- The areas the prompt called out as risky (matching dialog, candidate search, match/unmatch flows, match status) are **not part of this feature** — they live on `AccountController` under the Transaction-History matching feature and were verified to be wired there.

## 4. Carried-forward legacy gaps (not refactor regressions)

| # | Gap | Status |
|---|---|---|
| R2 | USD totals computed but not rendered in footer | Preserved on wire (`total_usd_amount`/`total_usd_count`); frontend renders VND row only. A second footer row can be added without a backend change. |
| R6 | Dual in-bank columns (`IN_BANK_CODE` vs `IN_NAPAS_BANK_CODE`) | Refactor resolves name from `IN_NAPAS_BANK_CODE` and ships it as wire `in_bank_code`; documented in database-mapping §2. |
| Day-boundary | Filter on raw UTC, display in local time | Intentional legacy parity; a near-midnight transfer can show a display date outside the filtered range. |
| No max date-range cap (R1) | Legacy only enforces end ≥ start | Refactor matches; large ranges are unbounded. Consider a cap if performance becomes an issue. |

## 5. Open questions / follow-ups

| # | Item | Owner |
|---|---|---|
| OQ-A | `WBE_TRANSFER_HISTORY_INVALID_PERIOD` is in `messages.properties` but **not** `messages_en.properties` (resolves via fallback, but violates the "add to both" convention). | Backend cleanup MR |
| OQ-B | `V196__transfer_history_helper_index.sql` has a stale internal header reading `V181`. Harmless, but misleading. | Backend cleanup MR |
| OQ-C | ✅ **RESOLVED 2026-06-07** — deprecated interim endpoint `GET /api/v1/banking/transfers/history` + `TransferController.history()` + `TransferHistoryService` + `TransferHistoryResponse` / `TransferRow` (UPPER_SNAKE) **deleted** after confirming no consumer. | Done |
| OQ-D | ✅ **RESOLVED 2026-06-07** — CLAUDE.md "Current API calls" table updated to `/api/v1/banking/internal-transfer/transfer-history`. | Done |
| OQ-E | USD second-footer-row rendering deferred (business decision — is a USD totals row wanted on screen?). | PO (Mr. Jung) |
| OQ-F | Max date-range cap — do we want one for performance? Legacy has none. | PO (Mr. Jung) |
