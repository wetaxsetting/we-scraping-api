# Excel Import — Source Code Index (Backend)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

All paths under `c:\WECMS PROJECT\wecms-api\` unless noted. Code is source of truth; citations are file:line at verification time.

## No shared backend service (confirmed)

- Glob `src/main/java/.../common/**/*Import*.java` → **no files**.
- `common/export/` contains **export-only** classes (`ExcelExportService`, `ExcelWorkbookWriter`, `ExcelExportRequest`, …) — `ExcelExportService.java` imports no `MultipartFile` / `Workbook` / import logic.
- ⇒ The only shared Excel pipeline is **export**. Import is per-feature. (This is open decision C3 — see [refactor-gap-analysis.md](refactor-gap-analysis.md).)

## Per-feature backend (controllers + services)

### Purpose Settings (canonical)

| Component | Path | Notes |
|---|---|---|
| Controller | `src/main/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsController.java` | preview `:167-171`, import `:179-183`, base `:66`, authz `:69` |
| Service | `.../purposesettings/service/PurposeExcelService.java` | preview `:202-338`, import `:359-503`, header `:583-600`, key gen `:616`, audit `:171-174,492-499` |
| DTO — preview | `.../purposesettings/dto/PurposeExcelPreview.java` (`:20-29`), `PurposeExcelPreviewRow.java` (`:18-33`), `PurposeExcelFieldError.java` (`:32-41`) | |
| DTO — import | `.../purposesettings/dto/PurposeExcelImportRequest.java` (`:20-32`), `PurposeExcelImportResult.java` (`:8-10`) | |
| Entity | `.../purposesettings/entity/BusiUsagSetInfm.java` (`@Table` `:50`) | table `BUSI_USAG_SET_INFM` |

### VAS Account (canonical)

| Component | Path | Notes |
|---|---|---|
| Controller | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/controller/VasAccountController.java` | preview `:181-185`, import `:193-197`, export `:212-217`, base `:70`, authz `:72` |
| Service | `.../vasaccount/service/VasAccountExcelService.java` | preview `:205-277`, import `:299-375`, header `:447-459`, seq gen `:346`, soft-delete-all `:342`, audit `:156-160,363-370` |
| Seq generator | `.../vasaccount/...VasAcctSeqNoGenerator` | Java MAX+1 (NOT `wb_get_next_val`) |
| DTO — preview | `.../vasaccount/dto/VasAccountExcelPreview.java` (`:17-26`), `VasAccountExcelPreviewRow.java` (`:17-29`), `VasAccountExcelFieldError.java` | |
| DTO — import | `.../vasaccount/dto/VasAccountExcelImportRequest.java` (`:27-36`), `VasAccountExcelImportResult.java` (`:11-14`) | |
| Entity | `.../vasaccount/entity/BusiVasAcctInfm.java` (`@Table` `:42`; deviation Javadoc `:51-57`) | table `BUSI_VAS_ACCT_INFM` |

### Chart of Accounts (variant — client parse, non-atomic)

| Component | Path | Notes |
|---|---|---|
| Controller | `.../domain/setting/chartofaccounts/...ChartOfAccountsController.java` | `bulk-upload` `:166-170`, `bulk-delete` `:180-184`, `bulk-deactivate` `:194-198`; **no `/excel/preview`** |
| DTOs | `ChartOfAccountBulkUploadRequest` / `ChartOfAccountBulkUploadResponse` | |

### Contacts (variant — client parse, non-atomic)

| Component | Path | Notes |
|---|---|---|
| Controller | `.../domain/setting/contacts/...ContactsController.java` | `excel/import` `:201-205`, `excel/preflight` `:221-225`, `excel/resolve-recipients` `:240-244`, `export` `:184-189`, `by-codes` `:258-261` |
| Service | `.../contacts/service/ContactsImportService.java` | per-row, non-atomic, cap 500, always HTTP 200 with partial `errors[]` |
| DTOs | `ContactBulkImportRequest/Response`, `ContactsExcelPreflightRequest/Response`, `ContactsExcelResolveRecipientsRequest/Response` | |

### Related (import-shaped, other modules)

- Overseas Payment: `.../domain/payment/...OverseasPaymentExcelService.java` (uses `WBE2041/2042/2043` too; `WBE2045/2046` are its own codes)
- Internal Transfer: `.../domain/payment/internaltransfer/...InternalTransferExcelParser.java`

## Error codes

`src/main/resources/messages.properties` — `WBE2041` `:41-47` region; `WBE2040` `:34` (NOT Excel); `WBE2044` retired `:48-51`. Not present in `messages_en.properties` (only `WBE2040` is).

## Tests (class paths confirmed; individual `@Test` method names UNVERIFIED)

- `src/test/java/com/kosign/wecms/domain/setting/purposesettings/service/PurposeExcelServiceTest.java`
- `src/test/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsControllerTest.java`
- `src/test/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAccountExcelServiceTest.java`
- `src/test/java/com/kosign/wecms/domain/setting/vasaccount/VasAccountIntegrationTest.java`
- `src/test/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferExcelParserTest.java`
- (export-side) `src/test/java/com/kosign/wecms/common/export/ExcelExportServiceErpAuditTest.java`

> ⚠️ There is **no** `PurposeSettingsImportServiceTest` / `VasAccountImportServiceTest` by those exact names — the actual classes are `PurposeExcelServiceTest` / `VasAccountExcelServiceTest`. Use these names.

## Shared frontend shell (cross-tree reference)

Lives in the WEB repo, documented in the WEB twin — listed here only for traceability:

- `wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx`
- `wecms-web/components/shared/excel-upload/templateValidator.ts`
- `wecms-web/components/shared/ExcelUploadButton.tsx`
- `wecms-web/lib/xlsxReader.ts`

## Related

- [interface-specification.md](interface-specification.md) · [refactor-gap-analysis.md](refactor-gap-analysis.md) · [README.md](README.md)
- WEB twin: [`../../../../../wecms-web/docs/features/common/excel-import/component-index.md`](../../../../../wecms-web/docs/features/common/excel-import/component-index.md)
