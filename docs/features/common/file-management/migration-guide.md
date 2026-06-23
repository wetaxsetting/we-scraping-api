# File Management — Migration Guide (Backend)

**Part of:** Common → file-management
**Last verified:** 2026-06-23

How the legacy JEX file upload/download WSVCs map onto the refactor's file pattern. Legacy WSVC ids appear here **only** as source-of-truth pointers / migration-traceability prose (per the identifier-naming rule); refactor surfaces use slugs.

## 1. Legacy → refactor mapping

| Legacy JEX WSVC / DAO | Behaviour | Refactor counterpart |
|---|---|---|
| `comm_0184_002_c001` (+ `TrcoInfoFileDAO.create`) | contact file insert (Add Contact attachments) | `ContactsFileService.upload` → `POST /api/v1/settings/contacts/{trcoKey}/files` |
| `comm_0184_005_*` | contact file edit-time attach/manage | same upload/list/delete endpoints, edit-mode (immediate) |
| (legacy contact file list/download) | list + stream contact attachment | `ContactsFileService.list` / `openForDownload` → `GET .../files` / `GET .../files/{fileId}` |
| `comm_0219_001_r001` | ERP file ledger list + counts | `ErpTrnsHistoryService.list` (owned by ERP feature) |
| `comm_0219_001_r002_view.jsp` (`FileUtils.downloadFileAsBinary`) | ERP file binary download | `ErpTrnsHistoryService.resolveDownload` → `GET .../erp-transaction-history/{seqNo}/download` |
| `comm_0219_001_r002` `?cmd=getthumb` (`FileUtils.downloadThumbFileAsBinary`) | 640px JPEG thumbnail | stub → `WBE9001` (deferred, no consumer) |

## 2. Key-generation migration

Legacy `SeqHelper.getNextVal(util, idoCon, TrcoInfoFile.class, "file_id", trcoKey)` (`TrcoInfoFileDAO.create:34`) → refactor `LegacyFunctionRepository.nextTrcoInfoFileFileId(trcoKey)` → `wb_get_next_val('trco_info_file','file_id',trcoKey)` (V140 branch, body copied verbatim from DEV). Per-contact MAX+1, no sequence. See [database-mapping.md](database-mapping.md) §2.

## 3. Storage path scheme — deliberate divergence

| Aspect | Legacy JEX | Refactor |
|---|---|---|
| Root | `WbStatic.PATH_TO_NAS_ROOT` (NAS) | `wecms.{contacts,erp-transaction-history}.storage-root` config key |
| Sub-folder | legacy WSVC id subfolder (`trco_0004_001_u002`, `comm_0219_001_c001`) | **slug** (`contacts`, `erp-transaction-history`) |
| Full path | `<NAS>/<wsvc-subfolder>/…` | `<storage-root>/<biz_key>/<slug>/<save_file_nm>` |
| Save filename | (legacy save-name convention) | server-generated `UUID + .<ext>` |

The slug folder is intentional (`ContactsFileStorageProperties.java:16-21`) so refactor-written file paths are identifiable at a glance and never collide with legacy NAS layout. **Migration implication:** legacy file *data migration* (copying NAS files into the refactor storage root) would need to re-key the sub-folder from the WSVC subfolder to the slug, and re-key save filenames if the legacy save-name scheme differs from UUID. ⚠️ UNVERIFIED: the exact legacy NAS sub-folder names and the legacy `SAVE_FILE_NM` format were not inspected in this pass — confirm against the live JEX `FileUtils` / DAO before any file ETL.

## 4. Behaviour parity notes

- **Title default** — legacy popup always supplied a title; the refactor UI does not, so the service stamps `"Business Registration"` (first file) / `"New document"` (subsequent) to keep the `NOT NULL` column populated. This is a refactor-side substitution, not a legacy behaviour.
- **Download filename** — contacts surfaces the **original** filename; ERP surfaces the **saved** filename (`SAVE_FILE_NM`) to match legacy `FileUtils.downloadFileAsBinary(out, util, file, save_file_nm)` audit-trail behaviour.
- **No audit-log rows** — neither the contacts file ops nor the ERP read path write `AUDIT_LOGS_INFM` (read-only / file-op parity with legacy).

## 5. Refactor-only constructs check

No new file/attachment table is introduced. `TRCO_INFO_FILE` exists in the legacy inventory (scope-widened by PO 2026-05-13); the ERP path reuses existing `ERP_TRNS_HSTR`. There is no refactor-only "attachment", "upload", or "blob" table — consistent with the workspace rule that refactor-only constructs with no legacy table are not allowed.
