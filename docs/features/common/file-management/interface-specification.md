# File Management — Interface Specification (Backend)

**Part of:** Common → file-management
**Last verified:** 2026-06-23

The concrete HTTP surface of the file pattern. **There is no generic `/api/v1/attachments` endpoint** — every endpoint below is owned by a concrete feature controller.

## 1. Contacts attachment endpoints (`ContactsController.java`)

Base: `/api/v1/settings/contacts` — class-gated by `@PreAuthorize("@menuAuthz.canAccess('settings-core-contacts')")` (`:76`); every endpoint is also `authenticated()` via `SecurityConfig`. Tenant scope from `TenantContext.currentBizKey()`; the wire never carries `biz_key`.

| Method | Path | Returns | Body / params | Code ref |
|---|---|---|---|---|
| `GET` | `/{trcoKey}/files` | `ApiResponse<ContactFileListResponse>` | — | `:295-299` |
| `POST` | `/{trcoKey}/files` | `ApiResponse<ContactFileRow>` | multipart, part name **`file`**, `consumes = multipart/form-data` | `:306-311` |
| `GET` | `/{trcoKey}/files/{fileId}` | binary (`InputStreamResource`) | — streams `application/octet-stream` + RFC 5987 `Content-Disposition` (original filename) | `:318-336` |
| `DELETE` | `/{trcoKey}/files/{fileId}` | `ApiResponse<Void>` | — | `:343-349` |

> Note: the download path is `…/files/{fileId}` (not `…/files/{fileId}/download`). The prompt's example `GET .../files/{fileId}/download` does **not** match the code — the verified path has no `/download` suffix.

`ContactFileRow` fields (wire snake_case): `file_id`, `org_file_nm`, `file_size`, `reg_dtm`, `reg_nm`, `trco_infm_titl` (`ContactsFileService.toRow`, `:335-343`).

## 2. ERP transaction-history download endpoint (`ErpTrnsHistoryController.java`)

Base: `/api/v1/settings/erp-transaction-history` — class-gated by `@PreAuthorize("@menuAuthz.canAccess('settings-erp-transaction-history')")` (`:63`).

| Method | Path | Returns | Notes | Code ref |
|---|---|---|---|---|
| `GET` | `/{seqNo}/download` | binary (`InputStreamResource`) | streams under `SAVE_FILE_NM`; `seqNo <= 0` → `WBE_VALIDATION` | `:118-143` |
| `GET` | `/{seqNo}/thumbnail` | `ApiResponse<Void>` | stub → always `WBE9001` | `:153-157` |

(The list + 4 tab counts + Excel export endpoints on this controller belong to the ERP feature, not to this pattern doc.)

## 3. Confirmed error codes

All present in `messages.properties` (verified line numbers).

| Code | Message | Source | Used by |
|---|---|---|---|
| `WBE_CONTACTS_FILE_EMPTY` | `Attachment is empty.` | `:458` | upload (zero bytes) |
| `WBE_CONTACTS_FILE_TOO_LARGE` | `Attachment exceeds the maximum allowed size ({0}).` | `:456` | upload (> 10 MB); arg = `"10 MB"` |
| `WBE_CONTACTS_FILE_TYPE_NOT_ALLOWED` | `Attachment type is not allowed ({0}).` | `:457` | upload (bad ext); arg = allow-list |
| `WBE_CONTACTS_FILE_NAME_INVALID` | `Attachment filename is missing or invalid.` | `:459` | upload (blank/null name) |
| `WBE_CONTACTS_FILE_NOT_FOUND` | `Attachment not found.` | `:460` | download/delete (row absent) |
| `WBE_CONTACTS_FILE_UNAVAILABLE` | `Attachment file is unavailable on the server.` | `:461` | download (file missing on disk) |
| `WBE_CONTACTS_FILE_STORAGE_FAILED` | `Could not store the attachment on the server.` | `:462` | upload (IO failure / null file-id) |
| `WBE_CONTACTS_NOT_FOUND` | (contacts family) | — | parent contact missing / wrong tenant on any file op |
| `WBE2014` | `ERP transaction history row not found.` | `:82` | ERP download (not found OR wrong tenant) |
| `WBE2015` | `ERP transaction history file is unavailable.` | `:83` | ERP download (file_size=0 or file missing) |
| `WBE9001` | `Endpoint is not yet implemented.` | `:91` | ERP thumbnail stub |

`WBE2014`/`WBE2015` are the ERP analogues of `WBE_CONTACTS_FILE_NOT_FOUND`/`_UNAVAILABLE`. Each "not found" code deliberately merges the not-found and wrong-tenant cases so a client cannot probe another tenant's key space.

## 4. Storage configuration keys

CONFIRMED in `application.yml` and `application-sit.yml`.

| Property key | Env var | `application.yml` default (local) | `application-sit.yml` default |
|---|---|---|---|
| `wecms.contacts.storage-root` | `WECMS_CONTACTS_STORAGE_ROOT` | `./tmp/contacts` (`application.yml:207`) | `C:/WECMS PROJECT/File Data` (`application-sit.yml:213`) |
| `wecms.erp-transaction-history.storage-root` | `WECMS_ERP_TRANSACTION_HISTORY_STORAGE_ROOT` | `./tmp/erp-trns-hstr` (`application.yml:215`) | `C:/WECMS PROJECT/File Data` (`application-sit.yml:221`) |

Bound by `@ConfigurationProperties` records: `ContactsFileStorageProperties` (`prefix = wecms.contacts`, field `storageRoot` `@NotBlank`) and `ErpTrnsHistoryStorageProperties` (`prefix = wecms.erp-transaction-history`). The per-module slug folder (`contacts` / `erp-transaction-history`) is a `static final` constant, **never** configurable.

> ⚠️ Note on SIT roots: `application-sit.yml:184-202` carries a TODO flagging the SIT storage-root layout as not yet normalised — the payment modules default to `c:/WeCMS/File Data` while contacts / preferences / user-image / erp-transaction-history default to `C:/WECMS PROJECT/File Data`. Confirm the SIT path with ops before relying on it.

## 5. Multipart caps

`spring.servlet.multipart.max-file-size: 10MB` + `max-request-size: 50MB` (`application.yml:58-65`). Matches `ContactsFileService.MAX_FILE_SIZE_BYTES` (10 MB).
