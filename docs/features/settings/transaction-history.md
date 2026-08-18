# Settings > ERP Transaction History — Feature Summary (Refactor)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/transaction-history/`

## Feature Overview
- **Purpose:** Per-tenant read-only audit ledger of every file exchanged between WeCMS and the customer's ERP. A row in `ERP_TRNS_HSTR` is created whenever a file moves between the two systems — an export WeCMS sends to the ERP (`ERP_TYPE='W'`), an ERP-produced file ingested into WeCMS (`'E'`), or a soft-deleted upload (`'D'`). This is the ERP file-exchange ledger, NOT the Banking transaction-history page.
- **Business role:** A tenant user lists/filters rows by date range (required), direction (the four tabs), transaction type (17 codes), and keyword (partial match on `reg_id`), sees four tab counts in one round-trip, downloads the physical file attached to a row, and exports the filtered ledger to XLSX. The screen never mutates a row.

## Menu / Route Mapping
- **Menu / panel:** Settings → Transaction History (Settings modal panel — no standalone route)
- **Menu permission ID (slug):** `settings-erp-transaction-history` (class-level `@PreAuthorize("@menuAuthz.canAccess('settings-erp-transaction-history')")` on `ErpTrnsHistoryController`)
- **Wire base path:** `/api/v1/settings/erp-transaction-history` (refactor slug `erp-transaction-history` on every identifier surface; doc-folder name is `transaction-history`)
- **Frontend mount / route:** Settings modal panel key `erp-transaction-history`
- **Legacy JEX counterpart:** WSVC family `comm_0219_001` — `_r001` (list + counts), `_r002` (download), `_c001` (upload — NOT ported); table `ERP_TRNS_HSTR` — legacy ids cited for traceability only.

## Current Status
- ✅ **Implemented:** Backend read paths verified against `comm_0219_001` source. `GET /` (list + four tab counts All/WeCMS→ERP/ERP→WeCMS/Deleted in one round-trip; counts drop the `erp_type` predicate so all tabs share date/type/keyword scope). `GET /{seqNo}/download` (binary; `Content-Disposition` = legacy `SAVE_FILE_NM`; `WBE2014` not-found/wrong-tenant single envelope, `WBE2015` file missing / `FILE_SIZE=0`). `USER_NM` via LEFT JOIN `UserInfm` (replaces legacy `WB_GET_USER_NM`). Sort whitelist `ErpTrnsHistorySortField` (`REG_DTM`/`ERP_TYPE`/`TRNS_TYPE_CD`/`REG_USER`; `FILE` non-sortable), default `REG_DTM DESC, SEQ_NO DESC`. Server-resolved `erp_type_key`/`trns_type_key` i18n keys on the wire.
- 🟡 **Partially implemented:** **Excel export** (`POST /export`, `ErpTrnsHistoryExportProvider`) is wired but **seed-gated** — it requires `erp-transaction-history` rows in `SYS_SCR_OPTION_WECMS`, but the live `V156__sys_scr_option_wecms.sql` seed **explicitly excludes** the slug, so `ExcelExportService` drops every column with **`WBE2013`**. Export is unverified end-to-end until a DBA seeds the 7 columns (`REG_DTM`, `ERP_TYPE`, `TRNS_TYPE_CD`, `ORG_FILE_NM`, `FILE_SIZE`, `REG_USER`, `REG_ID`); the CLAUDE.md "V154 seed" narrative and the live seed disagree and must be reconciled with PO/DBA (GAP-4). Thumbnail (`GET /{seqNo}/thumbnail`) is a documented stub → `WBE9001` (no consumer yet, GAP-2). Default sort still to be confirmed against `SortHelper` (OQ-7).
- ⚪ **Deprecated / Legacy / Not ported:** Upload (`comm_0219_001_c001`) — DEAD LEGACY (legacy JSP renders no file input and would throw at runtime); each future Payment/Transfer feature writes its own row via the inert `ErpTrnsHistoryWriter` (no live caller yet, OQ-1/GAP-1). Soft-delete write (`_U001`/`_D001`) not ported — deleted rows are displayed read-only, written elsewhere (OQ-8). HAENGSUNG product gating (per-tab/type filters, hidden File column) not preserved — product-agnostic (OQ-2). Batch writers (`ERP_0100_001..004`, `ErpDataRefreshHelper`) out of scope. View Setting popover not adopted (legacy panel has no list-icon, OQ-FE-2) — export still uses the seed.

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [transaction-history/README.md](transaction-history/README.md) |
| Business flow | [transaction-history/business-flow.md](transaction-history/business-flow.md) |
| Database mapping | [transaction-history/database-mapping.md](transaction-history/database-mapping.md) |
| Functional specification | [transaction-history/functional-specification.md](transaction-history/functional-specification.md) |
| Interface specification | [transaction-history/interface-specification.md](transaction-history/interface-specification.md) |
| Migration guide | [transaction-history/migration-guide.md](transaction-history/migration-guide.md) |
| Refactor gap analysis | [transaction-history/refactor-gap-analysis.md](transaction-history/refactor-gap-analysis.md) |
| Source code index | [transaction-history/source-code-index.md](transaction-history/source-code-index.md) |
| Test scenarios | [transaction-history/test-scenarios.md](transaction-history/test-scenarios.md) |

## Historical Design Notes (preserved)
- **[Legacy JEX]** `ERP_TYPE` direction codes: `W` = WeCMS → ERP, `E` = ERP → WeCMS, `D` = soft-deleted `E` row (audit retained). `TRNS_TYPE_CD` is a 17-code transaction-type set (`01`..`17`); the full label catalogue lives in the folder docs. The legacy Deleted-tab type subset (`""`, `02`, `03`, `12`, `13`, `16`) is the only universal legacy filter preserved.
- **[Not ported]** Legacy migration-number narrative drift: the CLAUDE.md table narrates this table as `V71/V72` + a `V154` View Setting seed; the live migration is `V54__erp_trns_hstr.sql` (post-2026-06-09 renumber). Doc-only — does not affect behaviour.
