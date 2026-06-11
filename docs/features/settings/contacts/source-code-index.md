# Contacts — Source Code Index

Legacy ↔ refactor file map for Settings > Contacts. Paths relative to the workspace root.

---

## Refactor backend (`wecms-api/`)

### Controller
- [ContactsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/controller/ContactsController.java) — 17 endpoints; `@RequestMapping("/api/v1/settings/contacts")`; class-level `@menuAuthz` gate.

### Services
- [ContactsService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsService.java) — list / getOne / create / update / softDelete / bulkSoftDelete / listForExport / listBanks / lookupRecipient / preflightExcelImport / resolveExcelImportRecipients / getByBeneficiaryCodes.
- [ContactsImportService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsImportService.java) — `bulkImport()` per-row tx isolation.
- [ContactsValidator.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsValidator.java) — create/update/import dup + address disp_ord rules.
- [ContactsFileService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsFileService.java) · [ContactsFileResolver.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsFileResolver.java) · [ContactsFileStorageProperties.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsFileStorageProperties.java) · [ContactsConfig.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsConfig.java) — attachment storage.
- [ContactSortField.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactSortField.java) — sort whitelist.

### Export (GAP-01)
- [ContactsExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/export/ContactsExportProvider.java) — abstract base, 14 FIELD_IDs, `fetch()` / `toFieldMap()`.
- [ContactsLocalExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/export/ContactsLocalExportProvider.java) — `settings-contacts-local`, type `L`, 9 cols.
- [ContactsOverseasExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/export/ContactsOverseasExportProvider.java) — `settings-contacts-overseas`, type `O`, 11 cols.

### Repositories
- [TrcoInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/repository/TrcoInfmRepository.java) — `search()`, `findByBeneficiaryCodes()`, `findExistingActiveHandTrcoKeys()`, `findExistingActiveTrcoTaxIds()`, `findActiveByBizKeyAndHandTrcoKey()`, derived dup/finder methods.
- [TrcoInfoFileRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/repository/TrcoInfoFileRepository.java) — attachment finders.
- `TrcoAddrInfmRepository` · `TrcoAcctInfoRepository` · `TrcoOvrsInfmRepository` · `PgroBsacLnknInfmRepository` (in the same package) — `findByBizKeyAndTrcoKeyIn` batch finders + `deleteBy…` used by the service.

### Entities
- [TrcoInfm.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/entity/TrcoInfm.java) — `@Id TRCO_KEY` (global), 47-col table / Sprint subset.
- [TrcoAddrInfm.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/entity/TrcoAddrInfm.java) — `@IdClass (TRCO_KEY, BIZ_KEY, TRCO_ADDR_KEY)`.
- [TrcoInfoFile.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/entity/TrcoInfoFile.java) — `@IdClass (TRCO_KEY, FILE_ID)`.
- (`TrcoAcctInfo` / `TrcoOvrsInfm` + their `@IdClass` keys live alongside; consumed by the service.)

### DTOs

22 records under `domain/setting/contacts/dto/`:

| DTO | Role | Key fields |
|---|---|---|
| `ContactRow` | row / detail | `trcoKey, handTrcoKey, trcoCmnm, trcoCmenm, trcoTaxId, trcoType, trcoStts, modDtm, addresses[], accounts[], overseasBeneficiaries[]` |
| `ContactListResponse` | list | `rows[], totalCount, page, size, totalPages` |
| `ContactAddressRow` | child | `trcoAddrKey, trcoBsunPostAdrs, dispOrd, issuDt, dueDt` |
| `ContactAccountRow` | child | `trcoAcctKey, acctNo, dispOrd, bankNm, napasBankCd, citadBankCd, acctCcyCd, fincMdaOwnerNm, owacAddr` |
| `ContactOverseasRow` | child | `trcoOvrsKey, beneName, beneAcctNo, bicCode, beneBankName, beneBankCode, beneAddr` |
| `ContactCreateRequest` | create | core + `addresses[] (AddressOp)`, `accounts[] (AccountOp)`, `overseasBeneficiaries[] (OverseasOp)` |
| `ContactUpdateRequest` | update | core + `trcoStts` + `accountsToDelete[]` + `overseasToDelete[]` |
| `ContactBulkDeleteRequest` | bulk delete | `trcoKeys[] (@NotEmpty @Size 100)` |
| `ContactBulkImportRequest` | import | `contacts[] (@NotEmpty @Size 500, @Valid ContactCreateRequest)` |
| `ContactBulkImportResponse` | import | `total, imported, failed, errors[]` |
| `ContactImportError` | import | `index, handTrcoKey, code, message` |
| `ContactsExcelPreflightRequest` / `…Response` | import | `rows[]` → `results[] (rowIndex, handTrcoKeyExists, trcoTaxIdExists)` |
| `ContactsExcelResolveRecipientsRequest` / `…Response` | import | `rows[]` → `results[] (rowIndex, bankFound, antDivCd, napasBankCd, citadBankCd, fincMdaOwnerNm)` |
| `ContactsByCodesRequest` | by-codes | `codes[] (@NotEmpty @Size 500)` |
| `ContactBankRow` / `ContactBankListResponse` | banks | `bankCd, antDivCd, bankNm, bankEngNm, engAbrvatNm, status` |
| `ContactLookupRecipientRequest` / `…Response` | lookup | `napasBankCd, citadBankCd, acctNo` → `recipientName` |
| `ContactFileRow` / `ContactFileListResponse` | files | `fileId, orgFileNm, fileSize, regDtm, regNm, trcoInfmTitl` |

(Embedded ops: `AddressOp`, `AccountOp`, `OverseasOp`, `AccountDeleteKey`, `OverseasDeleteKey`.)

### Migrations
- `V112__contacts_schema.sql` (5 tables) · `V113__wb_get_next_val_trco_infm.sql` (`sq_trco_key` + branch) · `V114__wb_get_napas_bank_nm_or_citad_bank_nm.sql` · `V115__contacts_demo_seed.sql` · `V117__contacts_disp_ord_to_integer.sql` · `V139__trco_info_file_schema.sql` · `V140__wb_get_next_val_trco_info_file.sql` · `V154__contacts_view_setting_seed.sql` (re-applied `V187`) · `V247__contacts_export_view_setting_seed.sql` (GAP-01). All under [src/main/resources/db/migration/](../../../../src/main/resources/db/migration). ⚠️ internal `-- V8x` header comments are stale — cite by filename.

### Tests
- [ContactsControllerTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/contacts/controller/ContactsControllerTest.java) — MockMvc, snake_case wire, validation envelopes, WBE1190.
- [ContactsServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/contacts/service/ContactsServiceTest.java) — list/sort/dup/PGRO-seed/retry/in-use guard.

---

## Refactor frontend (`wecms-web/`)

- Panel: `components/settings/contacts/ContactsPanel.tsx` · Table: `ContactsTable.tsx` · Dialogs: `AddNewContactDialog.tsx`, `EditContactDialog.tsx`, `ExcelUploadDialog.tsx` · Sub: `AddressPopover.tsx`, `AttachmentsSection.tsx`, `DownloadDropdown.tsx` · Hook: `useContacts.ts` · `schema.ts`, `types.ts`, `mappers.ts`.
- API client: `lib/api/contacts.ts` · wire types `lib/types/api.ts` (`ApiContact*`).
- Templates: `public/templates/contacts/WeCMS_{Local,Overseas}_Contacts_download_{EN,KO,VI}.xlsx`.
- Full inventory: [wecms-web/docs/features/settings/contacts/component-index.md](../../../../../wecms-web/docs/features/settings/contacts/component-index.md).

---

## Legacy JEX (read-only reference, `WECMS DEV/`)

| Role | File |
|---|---|
| List popup view | `WECMS/web/view/jex/wabooks/comm/comm_0184_001_view.jsp` |
| Add-contact popup view | `comm_0184_002_view.jsp` |
| Edit / detail popup view | `comm_0184_005_view.jsp` / `comm_0184_005_u001_view.jsp` |
| Excel-upload popup view | `comm_0184_007_view.jsp` |
| Excel-download (export) view | `comm_0184_001_r006_excel_view.jsp` (Apache POI XSSF) |
| List read action | `WEB-INF/action/jex/wabooks/comm/comm_0184_001_r001_act.jsp` |
| Bulk soft-delete action | `comm_0184_001_u001_act.jsp` |
| Create action | `comm_0184_002_c001_act.jsp` (PGRO seed at :131-147) |
| Edit action | `comm_0184_005_u001_act.jsp` |
| TypeScript | `WECMS_STATIC/.../comm/comm_0184_001.ts` |
| List IDO | `WECMS_ETC/xml/service/IDO.TRCO_INFM_R016.xml` (named `comm_0184_001_r001`; `json_agg` child sub-selects; bank name via `wb_get_napas_bank_nm_or_citad_bank_nm` at :63) |
| In-use count IDO | `IDO.TRCO_INFM_R021_CNT.xml` (counts `ACCT_TRNS_RCV_PAY_INFM` — backs `WBE1190`) |
| Import wizard IDO | `DAT_IMP_WIZ_TRCO_INFM_*` |
| Entity | `WECMS/src/wabooks/entity/TrcoInfm.java` |

---

## Documentation

- This folder (primary backend home) · [../contacts.md](../contacts.md) (preserved single-file spec) · [references/business-rules/contacts-design.md](../../../references/business-rules/contacts-design.md) · [references/database/contacts-schema.md](../../../references/database/contacts-schema.md) · [references/migration/contacts-mapping.md](../../../references/migration/contacts-mapping.md) · workspace-root bundle [docs/features/contacts/](../../../../../docs/features/contacts/README.md) · frontend [wecms-web/docs/features/settings/contacts/](../../../../../wecms-web/docs/features/settings/contacts/README.md).
