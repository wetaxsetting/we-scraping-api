# File Management — Functional Specification (Backend)

**Part of:** Common → file-management
**Last verified:** 2026-06-23

The validation, storage, and naming rules of the shared file pattern. All values are CONFIRMED in code; anything not directly verified is marked `⚠️ UNVERIFIED`.

## 1. Multipart upload validation (contacts upload path)

Source: `ContactsFileService.java`.

| Rule | Value (confirmed) | Failure code | Code ref |
|---|---|---|---|
| Filename present + non-blank | required; path components stripped (`/` and `\`) | `WBE_CONTACTS_FILE_NAME_INVALID` | `:286-304` |
| Non-empty | `size > 0` | `WBE_CONTACTS_FILE_EMPTY` | `:306-317` |
| Size cap | **10 MB** = `10 * 1024 * 1024` bytes (`MAX_FILE_SIZE_BYTES`) | `WBE_CONTACTS_FILE_TOO_LARGE` (message arg `"10 MB"`) | `:72-76`, `:311-315` |
| Extension allow-list | `jpg, jpeg, png, gif, pdf, doc, docx, xls, xlsx, ppt, pptx` (lower-case, no dot) | `WBE_CONTACTS_FILE_TYPE_NOT_ALLOWED` (message arg = allow-list) | `:81-86`, `:327-333` |

**Container-level cap (defence in depth):** `application.yml` sets `spring.servlet.multipart.max-file-size: 10MB` + `max-request-size: 50MB` (`application.yml:58-65`). Both layers exist because the container 413 path produces a non-WBE response otherwise — the service-layer check fires first to produce a friendly envelope.

**Path-traversal guard on filename:** `validateFilename` strips everything before the last `/` or `\`, so a client sending `C:\Users\…\foo.pdf` as the original filename is reduced to `foo.pdf` before any extension check (`:294-298`).

## 2. Server-generated save-name

The on-disk filename is **never** the user's filename. `persistUpload` sets `saveFileNm = UUID.randomUUID() + "." + extension` (`:161`). Rationale (Javadoc `:56-60`): an attacker cannot guess paths, and a future ZIP export cannot collide on duplicate user filenames within one contact. The original filename is preserved in `ORG_FILE_NM` for display + download.

## 3. Tenant-scoped storage layout

Both resolvers compose the same shape (per-module slug folder):

```
<storage-root>/<biz_key>/<slug>/<save_file_nm>
```

- Contacts slug = `contacts` (`ContactsFileStorageProperties.SLUG_FOLDER = "contacts"`, `:33`).
- ERP slug = `erp-transaction-history` (`ErpTrnsHistoryStorageProperties.SLUG_FOLDER`, `:36`).

**Path-traversal guard on the resolved path:** both `ContactsFileResolver.resolve` (`:31-48`) and `ErpTrnsHistoryFileResolver.resolve` (`:50-71`) normalise the result and assert `resolved.startsWith(tenantRoot)`, throwing `IllegalArgumentException` if the path escapes the tenant subtree — defence in depth even though neither `bizKey` (config-pinned) nor `saveFileNm` (server-generated) is user-supplied at runtime.

The slug folder is **not** the legacy JEX WSVC subfolder (`trco_0004_001_u002` for contacts, `comm_0219_001_c001` for ERP). The refactor uses the product-agnostic slug deliberately so future tooling can identify "refactor-written" file paths at a glance (`ContactsFileStorageProperties.java:16-21`, `ErpTrnsHistoryStorageProperties.java:18-21`).

## 4. Title defaults (contacts)

`TRCO_INFO_FILE.TRCO_INFM_TITL` is `NOT NULL`; the refactor UI does not collect a title, so `ContactsFileService` stamps one at insert (`:165`):

- `FILE_ID = "1"` (first attachment) → `"Business Registration"` (legacy first-file convention).
- Any subsequent file → `"New document"`.

(The legacy DEV column default is `'기타문서'` / "Other document"; the refactor overrides per the rule above — `TrcoInfoFile.java:80-83`.)

## 5. Audit / metadata columns

On insert (`:185-189`): `reg_id` / `mod_id` = current user id (`TenantContext.currentUserId()`); `reg_dtm` / `mod_dtm` = UTC `yyyyMMddHHmmss`; `reg_nm` materialised from `USER_INFM.USER_NM` so the list read needs no JOIN. `file_size` stored as a **text token** (legacy quirk — column is `VARCHAR(100)`, not numeric — `TrcoInfoFile.java:75-77`).

## 6. Download response shaping

| Concern | Contacts | ERP |
|---|---|---|
| Filename surfaced | original (`ORG_FILE_NM`) | saved (`SAVE_FILE_NM`) — JEX audit-trail parity |
| Content-Type | `application/octet-stream` | `application/octet-stream` |
| Content-Disposition | `attachment; filename*=UTF-8''<name>` (RFC 5987) | same |
| Extra headers | `X-Content-Type-Options: nosniff`, `Access-Control-Expose-Headers: Content-Disposition` | same |

## 7. Not in scope (filesystem reality)

Physical files are **not** seeded by Flyway. The ERP demo seed (V72) inserts metadata rows with `FILE_SIZE > 0` but no physical file, so a download surfaces `WBE2015` for any row whose file is missing locally — mirroring production behaviour. ⚠️ UNVERIFIED: whether a contacts demo seed (V115) materialises any physical attachment files locally (the seed inserts `TRCO_INFM`/address/account rows; `TRCO_INFO_FILE` seed rows were not confirmed in this pass).
