# File Management — Database Mapping (Backend)

**Part of:** Common → file-management
**Last verified:** 2026-06-23

The concrete attachment table + its entity, the file-id allocation funnel, and the ERP filesystem-vs-metadata split. Both tables are in the PROD Active Table List / legacy inventory; `TRCO_INFO_FILE` was added to scope by PO call 2026-05-13 (V139/V140).

## 1. `TRCO_INFO_FILE` — the only concrete attachment table

Owned by Settings `contacts`. Schema landed in V139 (`trco_info_file` schema) + V140 (`wb_get_next_val` branch). Entity: `TrcoInfoFile.java`, PK class `TrcoInfoFileId.java`.

| Column | Type (legacy/entity) | Nullable | Entity field | Notes |
|---|---|---|---|---|
| `TRCO_KEY` | `VARCHAR(20)` | no (PK) | `trcoKey` | FK to `TRCO_INFM`; tenancy inherited from parent |
| `FILE_ID` | `VARCHAR(100)` | no (PK) | `fileId` | per-`TRCO_KEY` MAX+1 text token ("1","2",…) |
| `ORG_FILE_NM` | `VARCHAR(500)` | no | `orgFileNm` | original upload filename (display + download) |
| `SAVE_FILE_NM` | `VARCHAR(500)` | no | `saveFileNm` | server UUID + ext, addressable on disk |
| `REG_ID` | `VARCHAR(100)` | no | `regId` | uploader user id |
| `REG_DTM` | `VARCHAR(14)` | no | `regDtm` | UTC `yyyyMMddHHmmss` |
| `REG_NM` | `VARCHAR(100)` | no | `regNm` | uploader display name (materialised from `USER_INFM.USER_NM`) |
| `FILE_SIZE` | `VARCHAR(100)` | yes | `fileSize` | **byte count stored as TEXT** (legacy quirk, not numeric) |
| `TRCO_INFM_TITL` | `VARCHAR(50)` | no | `trcoInfmTitl` | document title; service stamps default on insert |
| `MOD_ID` | `VARCHAR(100)` | no | `modId` | |
| `MOD_DTM` | `VARCHAR(14)` | no | `modDtm` | |

**PK:** composite `(TRCO_KEY, FILE_ID)` via `@IdClass(TrcoInfoFileId.class)` (`TrcoInfoFile.java:41-52`).

**No `BIZ_KEY` column** — tenancy is enforced only through the parent `TRCO_INFM` row check in the service (`ContactsFileService.requireParent`). Repository `TrcoInfoFileRepository` cannot enforce tenancy on its own (Javadoc `:34-38`).

**`BaseEntity` intentionally NOT extended** — `BaseEntity` ships `TIMESTAMPTZ` audit columns, but `TRCO_INFO_FILE` keeps the legacy `VARCHAR(14)` timestamp shape, so the entity declares its own `reg_dtm`/`mod_dtm` (`TrcoInfoFile.java:30-34`).

## 2. File-id allocation — `wb_get_next_val` funnel (single-executor rule)

Allocation routes through `LegacyFunctionRepository.nextTrcoInfoFileFileId(trcoKey)` (`LegacyFunctionRepository.java:224-226`):

```java
nextVal("trco_info_file", "file_id", trcoKey, null, null, null, null, null);
// → SELECT wb_get_next_val('trco_info_file','file_id',trcoKey, …)
```

The V140 (`V140__wb_get_next_val.sql`) branch body (copied verbatim from DEV) is:

```sql
SELECT COALESCE(MAX(file_id::numeric), 0) + 1 FROM trco_info_file WHERE trco_key = $3
```

- **Per-contact** MAX+1, **not** per-tenant (the table has no `BIZ_KEY`).
- Returns a numeric text token (`"1"`, `"2"`, …).
- Concurrency: the upload + INSERT run in one `@Transactional`; a PK collision surfaces as `DataIntegrityViolationException` and `ContactsFileService.upload` retries once (`:137-140`).
- Per the funnel rule (`wecms-api/CLAUDE.md` → "Surviving legacy functions"), `LegacyFunctionRepository` is the **only** executable call site for `wb_get_next_val`. No repository/service may bypass it. Retirement: drop this branch when legacy WeCMS/WABOOKS no longer call `wb_get_next_val` for `trco_info_file`.

## 3. ERP files — filesystem + `ERP_TRNS_HSTR` metadata (no attachment-table)

ERP Transaction History does **not** use `TRCO_INFO_FILE`. Each row of `ERP_TRNS_HSTR` (V71; PK `(BIZ_KEY, SEQ_NO)`) carries the file **metadata** (`ORG_FILE_NM`, `SAVE_FILE_NM`, `FILE_SIZE` numeric, `ERP_TYPE`, `TRNS_TYPE_CD`); the **bytes** live on the filesystem under `<storage-root>/<biz_key>/erp-transaction-history/<save_file_nm>`. Download resolves the path from the metadata row (`ErpTrnsHistoryService.resolveDownload` + `ErpTrnsHistoryFileResolver.resolve`). `FILE_SIZE = 0` means a batch row with no physical file → `WBE2015`.

Note: `ERP_TRNS_HSTR.FILE_SIZE` is **numeric** (`NUMERIC(10,0)`, entity `Long`) — distinct from `TRCO_INFO_FILE.FILE_SIZE` which is `VARCHAR(100)` text.

## 4. Migration-safety note

No DDL is proposed by this doc. `TRCO_INFO_FILE` mirrors legacy column-for-column (V139). The file-id branch matches the legacy `wb_get_next_val` body verbatim. Keep both as-is; any change to the table or the branch is a PO decision per the workspace legacy-compatibility rules.
