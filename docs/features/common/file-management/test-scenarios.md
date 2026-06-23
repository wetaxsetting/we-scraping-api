# File Management — Test Scenarios (Backend)

**Part of:** Common → file-management
**Last verified:** 2026-06-23

Expected behaviours for the shared file pattern, expressed as test cases. ⚠️ No dedicated `ContactsFileServiceTest` exists today — these are the cases that should be covered. Each expected code is verified against the live service logic.

## Upload (contacts) — `ContactsFileService.upload`

| # | Given | When | Then |
|---|---|---|---|
| U1 | valid pdf, 1 KB, valid `trcoKey` in tenant | upload | row inserted; `file_id="1"`; `trco_infm_titl="Business Registration"`; `save_file_nm` = UUID+`.pdf`; returns `ContactFileRow` |
| U2 | a second valid file on the same contact | upload | `file_id="2"`; `trco_infm_titl="New document"` |
| U3 | file `> 10 MB` | upload | `WBE_CONTACTS_FILE_TOO_LARGE` (message arg `"10 MB"`); no row, no disk write |
| U4 | extension not in allow-list (e.g. `.exe`, `.zip`) | upload | `WBE_CONTACTS_FILE_TYPE_NOT_ALLOWED` (arg = allow-list) |
| U5 | zero-byte file | upload | `WBE_CONTACTS_FILE_EMPTY` |
| U6 | blank / null original filename | upload | `WBE_CONTACTS_FILE_NAME_INVALID` |
| U7 | filename with path prefix `C:\Users\x\foo.pdf` | upload | accepted as `foo.pdf` (path stripped); extension validated against `pdf` |
| U8 | `trcoKey` absent / in another tenant / `TRCO_STTS='C'` | upload | `WBE_CONTACTS_NOT_FOUND` |
| U9 | `wb_get_next_val` returns null/blank | upload | `WBE_CONTACTS_FILE_STORAGE_FAILED` |
| U10 | `file.transferTo` throws `IOException` | upload | `WBE_CONTACTS_FILE_STORAGE_FAILED`; no orphan row |
| U11 | PK collision on first insert | upload | one-shot retry; on success returns row; on second collision propagates (disk file cleaned up first) |
| U12 | container receives `> 10 MB` raw multipart | request | rejected at `spring.servlet.multipart.max-file-size` before the service runs |

## Download (contacts) — `ContactsFileService.openForDownload`

| # | Given | When | Then |
|---|---|---|---|
| D1 | existing row, file present on disk | download | streams `application/octet-stream`; `Content-Disposition` filename = `org_file_nm` (RFC 5987); `nosniff` header set |
| D2 | unknown `fileId` (or wrong tenant via parent) | download | `WBE_CONTACTS_FILE_NOT_FOUND` |
| D3 | row exists, physical file missing/unreadable | download | `WBE_CONTACTS_FILE_UNAVAILABLE` |
| D4 | `trcoKey` of another tenant | download | `WBE_CONTACTS_NOT_FOUND` (parent check, before file lookup) — never reveals another tenant's file |

## Delete (contacts)

| # | Given | When | Then |
|---|---|---|---|
| X1 | existing row | delete | DB row removed first, then disk file (`deleteQuietly`); returns `ApiResponse.ok(null)` |
| X2 | unknown `fileId` | delete | `WBE_CONTACTS_FILE_NOT_FOUND` |

## Download (ERP) — `ErpTrnsHistoryService.resolveDownload`

| # | Given | When | Then |
|---|---|---|---|
| E1 | row in tenant, `file_size > 0`, file present | download | streams under `save_file_nm` |
| E2 | unknown `seqNo` / wrong tenant | download | `WBE2014` |
| E3 | row with `file_size = 0` (batch row) | download | `WBE2015` |
| E4 | row present, physical file missing | download | `WBE2015` |
| E5 | `seqNo <= 0` | download | `WBE_VALIDATION` (`seq_no: must be a positive number`) |
| E6 | thumbnail endpoint | GET `/{seqNo}/thumbnail` | `WBE9001` (stub) |

## Storage policy / path-traversal

| # | Given | When | Then |
|---|---|---|---|
| P1 | resolver with `bizKey="VN_VNM0004_000001"`, server UUID name | resolve | path = `<root>/VN_VNM0004_000001/contacts/<uuid>.pdf`, stays under tenant root |
| P2 | crafted `saveFileNm` containing `../` (defensive) | resolve | `IllegalArgumentException` — "escapes tenant subtree" |
| P3 | blank `bizKey` or blank `saveFileNm` | resolve | `IllegalArgumentException` |

## Tenant isolation (cross-cutting)

| # | Given | When | Then |
|---|---|---|---|
| T1 | tenant A's contact has file id 1; user of tenant B calls upload/list/download/delete on tenant A's `trcoKey` | any file op | `WBE_CONTACTS_NOT_FOUND` (parent `findByTrcoKeyAndBizKey` fails for tenant B) |
| T2 | ERP row of tenant A; user of tenant B requests its `seqNo` | download | `WBE2014` (`ErpTrnsHstrId(bizKeyB, seqNo)` not found) |
