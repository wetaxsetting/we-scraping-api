# Settings > Transaction History (ERP) — Backend Feature (Refactor)

**Status:** ✅ Backend implemented — controller (4 endpoints) + 1 read service + 1 writer service + repository + entity + 5 DTOs + sort whitelist + export provider + file resolver/storage config. Read paths (list + 4 tab counts + download) verified against the JEX `comm_0219_001` source-of-truth. Read-only audit ledger; upload write-path intentionally NOT ported (OQ-1).
**Module / menu:** Settings → **Transaction History** (Settings modal panel — **no standalone route**). This is the **ERP file-exchange audit ledger**, NOT the Banking transaction-history page.
**Refactor slug:** `erp-transaction-history` (REST URI, Java package, log tags, audit `WSVC_ID`, View Setting `screenId`, export `screenId`). Doc-folder name `transaction-history` per the prompt; the slug on every identifier surface is `erp-transaction-history`.
**Wire base path:** `/api/v1/settings/erp-transaction-history` (GET list+counts · GET `/{seqNo}/download` · GET `/{seqNo}/thumbnail` · POST `/export`)
**Menu permission slug:** `settings-erp-transaction-history`
**Legacy JEX counterpart:** WSVC family `comm_0219_001` — `_r001` (list + counts), `_r002` (download), `_c001` (upload — NOT ported). Table `ERP_TRNS_HSTR`.

> This folder is the **primary documentation home** for ERP Transaction History on the backend.
> It consolidates and indexes material that previously lived in a single flat file; that file is
> **preserved and cross-linked** (see [§ Related & preserved docs](#related--preserved-docs)), not
> deleted. The frontend counterpart is at
> [wecms-web/docs/features/settings/transaction-history/](../../../../../wecms-web/docs/features/settings/transaction-history/README.md).

---

## What this feature is

ERP Transaction History is the per-tenant **read-only audit ledger** of every file exchanged between
WeCMS and the customer's ERP. A row in `ERP_TRNS_HSTR` is created whenever a file moves between the
two systems — an export WeCMS sends to the ERP (`ERP_TYPE='W'`), an ERP-produced file ingested into
WeCMS (`'E'`), or a soft-deleted upload (`'D'`). A tenant user can:

- **List / filter** rows for the current `BIZ_KEY` by date range (`from_date`/`to_date`, required),
  direction (`erp_type` — the four tabs), transaction type (`trns_type_cd` — 17 codes), and keyword
  (partial match on `reg_id`), with paging + a whitelisted sort.
- See **four tab counts** (All / WeCMS → ERP / ERP → WeCMS / Deleted) returned alongside the page
  slice in one round-trip.
- **Download** the physical file attached to a row (binary stream; `Content-Disposition` carries the
  legacy `SAVE_FILE_NM`).
- **Export** the filtered+sorted ledger to XLSX through the shared `common/export/` pipeline.

There is **no create / update / delete on this feature** — the screen never mutates a row. The legacy
upload path (`comm_0219_001_c001`) is intentionally **not ported** (OQ-1): each future Payment /
Transfer feature that produces a file writes its own `ERP_TRNS_HSTR` row through `ErpTrnsHistoryWriter`
(a shared writer service, inert until a caller wires it). The thumbnail endpoint is a documented stub
(`WBE9001`).

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 4: GET (list + 4 counts) · GET `/{seqNo}/download` (binary) · GET `/{seqNo}/thumbnail` (stub → `WBE9001`) · POST `/export` (XLSX) |
| Authority | `@PreAuthorize("@menuAuthz.canAccess('settings-erp-transaction-history')")` — class-level on the controller (F-3 menu-auth rollout). See [interface-specification.md §Authorization](interface-specification.md). |
| Tenant scope | `TenantContext.currentBizKey()` — `bizKey` is the leading PK component; never from a request param |
| Table | `ERP_TRNS_HSTR` (9 cols, PK `(BIZ_KEY, SEQ_NO)`; live migration `V54__erp_trns_hstr.sql`) — in the [legacy-table-inventory](../../../references/database/legacy-table-inventory.md) |
| Mutations | **None** — read-only audit ledger (list / download / export). No create / update / delete endpoint. |
| Upload | **NOT ported** (OQ-1) — `comm_0219_001_c001` is legacy-broken/unused; future Payment features write their own audit row via `ErpTrnsHistoryWriter` |
| `USER_NM` resolution | LEFT JOIN `UserInfm` on `REG_ID = USER_ID` — replaces the legacy `WB_GET_USER_NM(REG_ID)` DB function |
| Sort whitelist | `ErpTrnsHistorySortField` — `REG_DTM` / `ERP_TYPE` / `TRNS_TYPE_CD` / `REG_USER`; `FILE` is non-sortable (OQ-5). Default `REG_DTM DESC, SEQ_NO DESC` (OQ-7) |
| Download storage | `wecms.erp-transaction-history.storage-root` (default `C:/WECMS PROJECT/File Data`) → `<root>/<biz_key>/erp-transaction-history/<save_file_nm>` (OQ-3) |
| Download errors | `WBE2014` (row not found / wrong tenant — single envelope) · `WBE2015` (file missing / `FILE_SIZE = 0`) |
| Export | `ErpTrnsHistoryExportProvider`, `screenId = erp-transaction-history`, reuses `ErpTrnsHistoryService.listAll`; subheader `N record(s)` |
| View Setting popover | **NOT adopted** in the panel (OQ-FE-2 — legacy JEX panel has no toolbar list-icon). Export IS wired. **Seed gap flagged** — see [refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-4. |
| Audit | Read-only feature writes **no** `AUDIT_LOGS_INFM` row (mirrors `firm-banking-history` precedent — the legacy `mntLogYn=Y` per-read filter is not ported) |
| i18n keys on wire | `erp_type_key` (`settings.erp_transaction_history.direction.*`) + `trns_type_key` (`settings.erp_transaction_history.type.NN`) — server-resolved, frontend renders via `t(key)` |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.erptransactionhistory
├── controller/ErpTrnsHistoryController.java     4 endpoints; class-level @menuAuthz gate
├── service/
│   ├── ErpTrnsHistoryService.java               list (+4 counts) / listAll / resolveDownload; validation; i18n-key resolution
│   ├── ErpTrnsHistorySortField.java             sort whitelist (SortableField): REG_DTM / ERP_TYPE / TRNS_TYPE_CD / REG_USER
│   ├── ErpTrnsHistoryFileResolver.java          <root>/<biz_key>/erp-transaction-history/<save_file_nm> path composer (traversal-guarded)
│   ├── ErpTrnsHistoryStorageProperties.java     @ConfigurationProperties("wecms.erp-transaction-history")
│   ├── ErpTrnsHistoryConfig.java                @EnableConfigurationProperties wiring
│   └── ErpTrnsHistoryWriter.java                shared audit-row writer (recordUploaded/Deleted/ExportedFile) — inert, no caller yet
├── repository/ErpTrnsHstrRepository.java        search() (LEFT JOIN UserInfm) + countAllDirections()
├── entity/
│   ├── ErpTrnsHstr.java                          @Table(ERP_TRNS_HSTR), 9 cols, @IdClass
│   └── ErpTrnsHstrId.java                        composite PK { bizKey, seqNo }
├── dto/
│   ├── ErpTrnsHistoryListResponse.java           page slice + 4 counts + paging meta
│   ├── ErpTrnsHistoryRow.java                     wire row (+ erp_type_key / trns_type_key)
│   ├── ErpTrnsHistoryProjection.java              JPQL constructor projection (entity ⨝ UserInfm) — internal
│   └── ErpTrnsHistoryCounts.java                  4-count aggregate projection — internal
└── export/ErpTrnsHistoryExportProvider.java     screenId = erp-transaction-history; reuses listAll
```

Flyway (live numbering): [`V54__erp_trns_hstr.sql`](../../../../src/main/resources/db/migration/V54__erp_trns_hstr.sql) creates the table. (The `wecms-api/CLAUDE.md` migration table still narrates this as V71/V72 + a V154 View Setting seed — those numbers are from the pre-2026-06-09 renumber and are stale; the live file is `V54`.)

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: filter → controller → service → repository (search + countAllDirections) → list+counts response; download path; export path; the inert writer |
| [functional-specification.md](functional-specification.md) | Operations matrix, field-by-field validation, the four tabs + counts fan-out, `ERP_TYPE` / `TRNS_TYPE_CD` code sets, download guards, authorization, audit stance |
| [database-mapping.md](database-mapping.md) | `ERP_TRNS_HSTR` columns + the full legacy-column → entity-field → DTO-field → UI-field mapping; `REG_USER` via LEFT JOIN `UserInfm` |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes per endpoint, error-code registry, authorization, the per-feature i18n keys emitted on the wire |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (JSP / `_act.jsp` / IDO / WSVC / entity / DAO) became its Spring counterpart, incl. dropped/dead legacy flows |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) — incl. the upload-write-path exclusion (OQ-1) and the View Setting seed gap |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios — list/per-tab/filter, download success + WBE2015 + WBE2014, export, sort, search; no create/update/delete; no upload |

---

## Related & preserved docs

These existing docs are **preserved** and remain authoritative for their slice. This README is the
entry point:

- **Detailed flat feature spec** (full JEX endpoint contract, code tables, OQ-1…OQ-9 decision log):
  [../transaction-history.md](../transaction-history.md) — the original ~450-line spec, kept as the
  deep design narrative.
- **Frontend feature folder:** [wecms-web/docs/features/settings/transaction-history/](../../../../../wecms-web/docs/features/settings/transaction-history/README.md)
- **Frontend flat spec (preserved):** [wecms-web/docs/features/settings/transaction-history.md](../../../../../wecms-web/docs/features/settings/transaction-history.md)
- **Legacy table baseline:** [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md) (`erp_trns_hstr`)
- **Settings feature index:** [../README.md](../README.md) (if present)

---

## Open items (tracked, non-blocking)

1. **OQ-1 (upload) — DEAD LEGACY by design.** `comm_0219_001_c001` is not ported; each future Payment
   feature writes its own audit row through `ErpTrnsHistoryWriter`. The writer is present but **has no
   live caller yet**. See [refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-1.
2. **GAP-4 — View Setting export seed gap.** `ErpTrnsHistoryExportProvider` requires `erp-transaction-history`
   rows in `SYS_SCR_OPTION_WECMS` (otherwise `ExcelExportService` drops every column → `WBE2013`). The
   live `V156__sys_scr_option_wecms.sql` seed file **explicitly excludes** `erp-transaction-history`
   (comment at line 94), contradicting the `wecms-api/CLAUDE.md` "V154 seed" narrative. Export is
   **unverified** until a seed lands. See [refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-4.
3. **OQ-7 (default sort) — confirm against `SortHelper`.** Default is `REG_DTM DESC, SEQ_NO DESC`; a
   future read of `SortHelper.getSortDynamic(util, "comm_0219_001_r001")` may adjust it (backend-only
   change, no contract impact).
