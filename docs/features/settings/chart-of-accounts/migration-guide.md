# Chart of Accounts — JEX → Refactor Migration Guide


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

This is the **feature-folder summary**. The full field-by-field map (IDO XMLs, DAO, TS sources) lives in the preserved reference: [../../../references/migration/chart-of-accounts.md](../../../references/migration/chart-of-accounts.md). Where that design-era reference and the shipped code differ, **this file records the as-built reality** and flags the delta.

Legacy WSVC ids are traceability-only — no refactor identifier carries them.

---

## 1. Service / WSVC mapping (as built)

| JEX WSVC | Refactor endpoint | Handler | Note vs design doc |
|---|---|---|---|
| `comm_0223_001_r001` | `GET /` | `#list` | — |
| `comm_0223_001_r002` | `GET /tree` | `#tree` + `ChartOfAccountsTreeBuilder` | level computed in Java |
| `comm_0223_001_r003` / `_d002` | — | dropped | unused variants |
| `comm_0223_001_c001` | `POST /` | `#create` | — |
| `comm_0223_001_u001` | `PUT /{seq}` | `#update` | parent-cleanup `:238-256` **dropped** (OQ-3) |
| `comm_0223_001_u002` (parent patch) | folded into `#delete` | `reparentActiveChildren` | — |
| `comm_0223_001_d001` | `DELETE /{seq}` | `#delete` | soft-delete only (hard-delete IDO `D001` not ported) |
| `comm_0223_002_c001` (Excel) | **`POST /bulk-upload`** (JSON) | `#bulkUpload` | ⚠️ **Correction:** the design migration doc said `POST .../bulk-import` (multipart). The **shipped** endpoint is `POST /bulk-upload` taking a JSON array of pre-parsed rows — Excel parsing is on the front-end (SheetJS), not multipart upload. |
| `comm_0223_002_r001` / `_r002` (upload history) | deferred | — | ERP-audit-ledger feature |
| — (new) | `POST /bulk-delete`, `POST /bulk-deactivate` | `#bulkDelete`, `#bulkDeactivate` | refactor additions powering panel multi-select |

## 2. IDO mapping

| IDO | Refactor counterpart |
|---|---|
| `CHART_ACCT_INFM_R001` | `ChartOfAccountRepository.findByBizKeyAndDelYn` / `searchByBizKey` |
| `CHART_ACCT_INFM_R002` | `ChartOfAccountsTreeBuilder` (Java recursion; level + breadcrumb) |
| `CHART_ACCT_INFM_C001` | `repo.save(entity)` (Hibernate INSERT) |
| `CHART_ACCT_INFM_U000/U001` | JPA dirty-checking under `@Transactional` |
| `CHART_ACCT_INFM_U002` (set parent NULL where parent=?) | **NOT ported** (OQ-3) |
| `CHART_ACCT_INFM_D001` (hard delete) | **NOT ported** — soft-delete only |
| `R003/R004/U003` | dropped (unused) |

## 3. Error code mapping

| Legacy | Refactor |
|---|---|
| `WBE0001` (not found / parent) | `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND` |
| `WBE0003` (already exists) | `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` |
| `WBE0404` (record not found) | `WBE_CHART_OF_ACCOUNTS_NOT_FOUND` |
| `WBE1178` (restricted) | `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` |
| — (new) | `WBE_CHART_OF_ACCOUNTS_CYCLE`, `…_DEPTH_EXCEEDED`, `…_SELF_PARENT`, `…_PARENT_REQUIRED`, `…_CAL_RULE_LEVEL3_ONLY` |

## 4. Behavioural deltas (as built)

| # | Behaviour | Direction | As-built note |
|---|---|---|---|
| Δ1 | Cycle detection on parent change | **Added** | `assertNoCycle` |
| Δ2 | Depth cap at write time | **Added + tightened** | cap is **3** (not legacy's read-only 4 / upload's 5) — PO 2026-05-25 |
| Δ3 | Self-parent rejection | **Added** | `assertNotSelfParent` |
| Δ4 | Parent **required** on create AND update | **Added / tightened** | level 1 reserved for ACC-0x (legacy update allowed empty parent → root). CA-G6 |
| Δ5 | Cal-rule **level-3-only, optional** | **Changed** | supersedes legacy "INVESTMENT-branch, required". CA-G4 |
| Δ6 | Account code **immutable on update** (UI) | **Changed** | legacy allowed code edit; backend still supports rename. CA-G7 |
| Δ7 | `u001:238-256` bulk parent-cleanup | **Dropped** | OQ-3 — per-row update only |
| Δ8 | Hard-delete IDO `D001` | **Dropped** | soft-delete only |
| Δ9 | `u002` parent-null patch | **Folded** | inlined into `delete` re-parent |
| Δ10 | Hierarchy computed server-side | **Moved server-side** | `ChartOfAccountsTreeBuilder`; wire ships `chart_acct_level`/`has_children`/`breadcrumb` |
| Δ11 | `chart_acct_seq_no` typed `String` end-to-end | **As built** | ⚠️ **Correction:** the design doc proposed `Long` with a JPA cast. The **shipped** entity types it as `String` (`VARCHAR(14)`) end-to-end — no converter. Wire ships a string. |
| Δ12 | `reg_dtm`/`mod_dtm` as `VARCHAR(14)` strings | **As built** | ⚠️ **Correction:** the design doc proposed `OffsetDateTime`. Shipped as `yyyyMMddHHmmss` strings (legacy parity). |
| Δ13 | Audit-log write per CRUD | **Added** | `wsvc_id="chart-of-accounts"` (slug). Legacy `comm_0223_*` wrote none. |
| Δ14 | `wb_get_next_val` seq branch | **Ported** (V95) | per-tenant MAX+1 |
| Δ15 | Bulk delete / bulk deactivate endpoints | **Added** | not in legacy `comm_0223_001` surface |
| Δ16 | Bulk **activate** | **Not added** | CA-G1 — single-row only |
| Δ17 | Excel **export** | **Not added** | only static template download |
| Δ18 | Authorization | **Added** | menu gate `settings-core-chart-of-accounts` (legacy had none) |

## 5. Out of scope (defer)

- Smart Organizer `fnsh_0200_*` reporting-account read view (ports with Banking transaction module).
- `ACCT_TRNS_HSTR` / `CARD_TRNS_HSTR` write-side FK enforcement (ports with those modules; banking + card already validate on write).
- Upload-history audit ledger (`comm_0223_002_r001/_r002`).

See the legacy dossier [docs/jex/features/chart-of-accounts/migration-guide.md](../../../../../docs/jex/features/chart-of-accounts/migration-guide.md) for the JEX-side narrative.
