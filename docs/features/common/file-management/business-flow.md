# File Management — Business Flow (Backend)

**Part of:** Common → file-management
**Last verified:** 2026-06-23

End-to-end flows for the shared file pattern. All file:line citations are to live refactor code (see [source-code-index.md](source-code-index.md)).

## 1. Upload (contacts attachment — the canonical write path)

Implemented by `ContactsFileService.upload` / `persistUpload` (`ContactsFileService.java:127-199`).

```
POST /api/v1/settings/contacts/{trcoKey}/files   (multipart, field name "file")
  │
  1. requireParent(trcoKey)                               ContactsFileService.java:272-277
  │    └─ TrcoInfmRepository.findByTrcoKeyAndBizKey(trcoKey, currentBizKey)
  │       → tenant ownership check; not-found / wrong-tenant / TRCO_STTS='C'
  │         → WBE_CONTACTS_NOT_FOUND
  │
  2. validateFilename(file)                               :286-304
  │    └─ strip path components ("C:\…\foo.pdf" → "foo.pdf"), trim
  │       → blank/null → WBE_CONTACTS_FILE_NAME_INVALID
  │
  3. validateSize(file)                                   :306-317
  │    └─ size <= 0           → WBE_CONTACTS_FILE_EMPTY
  │       size > 10 MB        → WBE_CONTACTS_FILE_TOO_LARGE  (arg = "10 MB")
  │
  4. validateExtension(extensionOf(name))                 :319-333
  │    └─ ext ∉ allow-list   → WBE_CONTACTS_FILE_TYPE_NOT_ALLOWED (arg = allow-list)
  │
  5. fileId = legacyFn.nextTrcoInfoFileFileId(trcoKey)    :152
  │    └─ wb_get_next_val('trco_info_file','file_id',trcoKey) → per-contact MAX+1 ("1","2",…)
  │       null/blank → WBE_CONTACTS_FILE_STORAGE_FAILED
  │
  6. saveFileNm = UUID().toString() + "." + ext           :161  (server-generated; user name NOT used on disk)
  │
  7. write bytes                                          :167-176
  │    └─ Files.createDirectories(<root>/<biz_key>/contacts)
  │       file.transferTo(<root>/<biz_key>/contacts/<UUID.ext>)
  │       IOException → WBE_CONTACTS_FILE_STORAGE_FAILED
  │
  8. INSERT TRCO_INFO_FILE row                            :178-197
  │    └─ title = "1".equals(fileId) ? "Business Registration" : "New document"
  │       reg_nm materialised from USER_INFM.USER_NM (no JOIN at read time)
  │       reg_dtm/mod_dtm = UTC yyyyMMddHHmmss
  │       PK collision → DataIntegrityViolationException → deleteQuietly(disk) + one-shot retry (:137-140)
  │
  └─ return ContactFileRow (file_id, org_file_nm, file_size, reg_dtm, reg_nm, trco_infm_titl)
```

**Add-mode vs edit-mode (frontend-driven, same backend endpoint):** the WEB Add dialog stages files locally and uploads them **after** the contact is created (so a `trcoKey` exists); the Edit dialog uploads immediately. The backend upload endpoint is identical for both — see the WEB twin's screen-flow.

## 2. List (contacts attachments)

`ContactsFileService.list` (`:205-211`) → `TrcoInfoFileRepository.findByTrcoKeyOrderByFileIdAsc(trcoKey)` after the same parent tenant check. Lexicographic file-id sort matches insertion order for realistic counts.

## 3. Download (contacts attachment)

`ContactsFileService.openForDownload` (`:224-241`):

```
GET /api/v1/settings/contacts/{trcoKey}/files/{fileId}
  1. requireParent(trcoKey)                  → WBE_CONTACTS_NOT_FOUND on wrong tenant
  2. fileRepo.findByTrcoKeyAndFileId(...)     → WBE_CONTACTS_FILE_NOT_FOUND when row absent
  3. path = resolve(parent.bizKey, row.saveFileNm)
  4. !readable || !regularFile               → WBE_CONTACTS_FILE_UNAVAILABLE
  5. return DownloadResource(path, orgFileNm, size)
```

Controller streams via `InputStreamResource` with `Content-Disposition: attachment; filename*=UTF-8''<org_file_nm>`, `application/octet-stream`, `X-Content-Type-Options: nosniff` (`ContactsController.java:318-336`). The download surfaces the **original** filename.

## 4. Download (ERP transaction history — read-only ledger)

`ErpTrnsHistoryService.resolveDownload` (`ErpTrnsHistoryService.java:242-260`):

```
GET /api/v1/settings/erp-transaction-history/{seqNo}/download
  1. row = findById(ErpTrnsHstrId(currentBizKey, seqNo))   → WBE2014 (not found OR wrong tenant)
  2. file_size == 0 (batch row, no physical file)          → WBE2015
  3. path = resolve(row.bizKey, row.saveFileNm)
  4. !readable || !regularFile                             → WBE2015
  5. return DownloadResource(path, orgFileNm, saveFileNm, size)
```

ERP download streams under the legacy **`SAVE_FILE_NM`** (NAS storage name), not the original filename — deliberate audit-trail parity with JEX `FileUtils.downloadFileAsBinary` (`ErpTrnsHistoryController.java:110-143`). The thumbnail variant is a stub → `WBE9001` (`:153-157`).

## 5. Delete (contacts attachment)

`ContactsFileService.delete` (`:257-266`): parent tenant check → `WBE_CONTACTS_FILE_NOT_FOUND` if row absent → delete DB row first, then `deleteQuietly` the disk file (DB-row-first so a partial failure orphans a disk file rather than leaving a visible-but-broken row).

## Tenant scoping (both flows)

`TRCO_INFO_FILE` and `ERP_TRNS_HSTR` rows are always reached through a tenant check: contacts via the parent `TRCO_INFM.BIZ_KEY` (the file table has **no** `BIZ_KEY` column); ERP via `ErpTrnsHstrId(currentBizKey, seqNo)`. The wire never carries `biz_key`; it comes from `TenantContext.currentBizKey()`.
