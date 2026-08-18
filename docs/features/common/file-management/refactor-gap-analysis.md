# File Management — Refactor Gap Analysis (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

**Part of:** Common → file-management
**Last verified:** 2026-06-23

Gap vocabulary for the file pattern. Each item is one of: **APPROVED** (in scope + implemented), **MISSING-by-design** (intentionally absent, needs a PO decision to add), or **DEFERRED** (stubbed, no consumer yet).

## Gap vocabulary

| # | Item | Status | Notes |
|---|---|---|---|
| C1 | Contacts attachment upload / list / download / delete | **APPROVED — implemented** | `ContactsFileService` + `ContactsController` `/{trcoKey}/files…`; `TRCO_INFO_FILE` in scope (PO 2026-05-13, V139/V140). |
| C2 | ERP transaction-history file download | **APPROVED — implemented** | `ErpTrnsHistoryService.resolveDownload` + `GET .../{seqNo}/download`; reuses `ERP_TRNS_HSTR` + filesystem. |
| C3 | Tenant-scoped storage policy + path-traversal guards + UUID save-names | **APPROVED — implemented** | `*FileResolver` + `*StorageProperties` + `validateFilename`. |
| C4 | **Generic `/api/v1/attachments` (cross-resource) endpoint** | **MISSING-by-design — open decision** | There is intentionally no generic attachments resource. File upload/download is a Common pattern; the concrete table (`TRCO_INFO_FILE`) is owned by `contacts`, the ERP download by `erp-transaction-history`. A generic endpoint would imply a refactor-only attachment table with no single legacy owner — forbidden without a fresh PO decision. **Do not implement speculatively.** |
| C5 | ERP file **thumbnail** (`?cmd=getthumb` legacy variant) | **DEFERRED — stub** | `GET .../{seqNo}/thumbnail` → `WBE9001`; no current frontend consumer. |
| C6 | ERP file **upload / write-path** | **DEFERRED — not ported** | ERP feature is read-only audit ledger; upload write-path intentionally not ported (each future Payment feature writes its own audit row — ERP feature OQ-1). |
| C7 | Contacts file **title** input on the wire | **MISSING-by-design** | UI does not collect a title; server stamps a default (`"Business Registration"` / `"New document"`) to satisfy the `NOT NULL` column. Not a gap to close — a deliberate refactor substitution. |
| C8 | Physical-file seeding in Flyway | **MISSING-by-design** | Seeds insert metadata only; downloads of un-seeded files surface `WBE2015` / `WBE_CONTACTS_FILE_UNAVAILABLE` — mirrors production. |

## C4 detail — why "generic /attachments" is MISSING-by-design

The refactor's legacy-compatibility rule forbids refactor-only tables with no legacy backing. A generic `/api/v1/attachments` resource would either (a) need its own refactor-only attachment table (forbidden), or (b) federate across `TRCO_INFO_FILE` + `ERP_TRNS_HSTR` + future per-feature file tables, which have different PK shapes (`(TRCO_KEY, FILE_ID)` vs `(BIZ_KEY, SEQ_NO)`), different filename semantics (original vs saved), different storage roots, and different ownership. The pattern documented here is the sanctioned alternative: **per-feature file endpoints that share a storage policy + the `*FileResolver` / `wb_get_next_val` primitives.** Reviving a generic endpoint requires a fresh PO decision and a genuine cross-system audit-history need (mirrors the retired "audit DB constant" bucket precedent).

## Open questions / uncertain mappings

- ⚠️ The legacy NAS sub-folder names and the legacy `SAVE_FILE_NM` format were not inspected in this pass — file ETL planning needs them confirmed against live JEX `FileUtils` / DAOs.
- ⚠️ Whether the contacts demo seed (V115) materialises any physical attachment files locally is unconfirmed.
- The SIT storage-root layout is flagged TODO/unresolved in `application-sit.yml:184-202` — confirm with ops before relying on SIT file paths.
