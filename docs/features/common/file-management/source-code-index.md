# File Management — Source Code Index (Backend)

**Part of:** Common → file-management
**Last verified:** 2026-06-23

Every file backing the shared file pattern. Paths are absolute-from-repo-root; line numbers verified 2026-06-23.

## Backend — upload / download service + controller

| Concern | Path | Key lines |
|---|---|---|
| Contacts file service (upload/list/download/delete, validation, storage, file-id) | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsFileService.java` | upload `:127-141`; persistUpload `:143-199`; list `:205-211`; openForDownload `:224-241`; delete `:257-266`; validation helpers `:272-333`; size cap `:72-76`; allow-list `:81-86`; title defaults `:88-93`, `:165` |
| Contacts file resolver (path composition + traversal guard) | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsFileResolver.java` | resolve `:31-48`; tenantRoot `:52-60` |
| Contacts storage-root config | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsFileStorageProperties.java` | `prefix = wecms.contacts` `:22`; SLUG_FOLDER `:33` |
| Contacts controller (file endpoints) | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/controller/ContactsController.java` | listFiles `:295-299`; uploadFile `:306-311`; downloadFile `:318-336`; deleteFile `:343-349`; class `@PreAuthorize` `:76` |
| ERP file download service | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java` | resolveDownload `:242-260`; DownloadResource record `:266` |
| ERP file resolver | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryFileResolver.java` | resolve `:50-71`; tenantDirectory `:80-86` |
| ERP storage-root config | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryStorageProperties.java` | `prefix = wecms.erp-transaction-history` `:23`; SLUG_FOLDER `:36` |
| ERP controller (download + thumbnail stub) | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java` | download `:118-143`; thumbnail stub `:153-157`; class `@PreAuthorize` `:63` |

## Backend — entity + repository + key generation

| Concern | Path | Key lines |
|---|---|---|
| `TRCO_INFO_FILE` entity | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/entity/TrcoInfoFile.java` | `@Table` `:40`; `@IdClass` `:41`; columns `:44-90`; file_size TEXT note `:75-77`; no-BaseEntity note `:30-34` |
| Composite PK class | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/entity/TrcoInfoFileId.java` | `(trcoKey, fileId)` `:14-16` |
| File repository | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/repository/TrcoInfoFileRepository.java` | findByTrcoKeyOrderByFileIdAsc `:30`; findByTrcoKeyAndFileId `:32`; deleteByTrcoKeyAndFileId `:40-41` |
| File-id allocation funnel | `wecms-api/src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java` | nextTrcoInfoFileFileId `:224-226` |
| DTO | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/dto/ContactFileRow.java` | wire row |
| DTO (list response) | `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/dto/ContactFileListResponse.java` | |

## Backend — config + messages + migrations

| Concern | Path | Key lines |
|---|---|---|
| Multipart caps + storage-root keys (base/local) | `wecms-api/src/main/resources/application.yml` | multipart `:58-65`; contacts root `:207`; erp root `:215` |
| Storage-root keys (SIT) | `wecms-api/src/main/resources/application-sit.yml` | contacts `:213`; erp `:221`; SIT TODO `:184-202` |
| Error-code messages | `wecms-api/src/main/resources/messages.properties` | `WBE_CONTACTS_FILE_*` `:456-462`; `WBE2014`/`WBE2015` `:82-83`; `WBE9001` `:91` |
| Migrations | `wecms-api/src/main/resources/db/migration/` | V139 (`TRCO_INFO_FILE` schema, header mislabeled V98), V140 (`trco_info_file.file_id` branch, header mislabeled V99), V71 (`ERP_TRNS_HSTR`) |

## Frontend (cross-link — full detail in the WEB twin)

| Concern | Path |
|---|---|
| Shared authenticated blob download | `wecms-web/lib/file.ts` (`downloadFile`) |
| Download glyph (Payment rows) | `wecms-web/components/shared/icons/DownloadIcon.tsx` |
| Contacts attachments section (staged add-mode vs immediate edit-mode) | `wecms-web/components/settings/contacts/AttachmentsSection.tsx` |

## Tests

| Test | Path | Coverage |
|---|---|---|
| Contacts controller | `wecms-api/src/test/java/com/kosign/wecms/domain/setting/contacts/controller/ContactsControllerTest.java` | mocks `fileService` (`:59`, `:66`, `:81`); file-endpoint coverage is shallow ⚠️ |
| ERP history service | `wecms-api/src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryServiceTest.java` | list + (verify download) ⚠️ confirm download-case coverage |
| ERP history controller | `wecms-api/src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryControllerTest.java` | |
| ERP export provider | `wecms-api/src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/export/ErpTrnsHistoryExportProviderTest.java` | (export, not file download) |

⚠️ **No dedicated `ContactsFileServiceTest`** exists — the upload/download/validation logic in `ContactsFileService` is not directly unit-tested in this pass. See [test-scenarios.md](test-scenarios.md) for the cases that should exist.
