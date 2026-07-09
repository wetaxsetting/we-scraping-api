# Excel Import — Interface Specification (Backend)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

The **contract pattern** for import endpoints. There is **no shared `/api/v1/common/excel/import`** — each owning feature exposes its own endpoints under its own base path. This doc documents the pattern + the error codes; exact request/response field lists for each feature also appear in that feature's `interface-specification.md`.

## Endpoint pattern (canonical: Purpose Settings · VAS Account)

| Step | Method + path | Body | Returns |
|---|---|---|---|
| Preview | `POST {feature-base}/excel/preview` | `multipart/form-data` with `file` part | `*ExcelPreview` |
| Import | `POST {feature-base}/excel/import` | JSON `*ExcelImportRequest` | `*ExcelImportResult` |

### Confirmed endpoints

**Purpose Settings** — base `@RequestMapping("/api/v1/settings/purpose-settings")` (`PurposeSettingsController.java:66`), gated `@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")` (line 69):

- `POST /api/v1/settings/purpose-settings/excel/preview` — multipart `file` (`PurposeSettingsController.java:167-171`)
- `POST /api/v1/settings/purpose-settings/excel/import` — JSON (`PurposeSettingsController.java:179-183`)

**VAS Account** — base `@RequestMapping("/api/v1/settings/vas-account")` (`VasAccountController.java:70`), gated `@PreAuthorize("@menuAuthz.canAccess('settings-core-vas-account')")` (line 72):

- `POST /api/v1/settings/vas-account/excel/preview` — multipart `file` (`VasAccountController.java:181-185`)
- `POST /api/v1/settings/vas-account/excel/import` — JSON (`VasAccountController.java:193-197`)
- (also `POST /api/v1/settings/vas-account/export`, line 212-217)

### Non-canonical endpoints

**Chart of Accounts** — base `@RequestMapping("/api/v1/settings/chart-of-accounts")`:

- `POST .../bulk-upload` — JSON `ChartOfAccountBulkUploadRequest` → `ChartOfAccountBulkUploadResponse`; per-row, **non-atomic** (`ChartOfAccountsController.java:166-170`)
- (`bulk-delete` 180-184, `bulk-deactivate` 194-198). **No `/excel/preview`.**

**Contacts** — base `@RequestMapping("/api/v1/settings/contacts")`:

- `POST .../excel/import` — JSON `ContactBulkImportRequest` → `ContactBulkImportResponse`; per-row, always HTTP 200, partial `errors[]`, cap 500 (`ContactsController.java:201-205`)
- `POST .../excel/preflight` — duplicate pre-check, `ContactsExcelPreflightRequest/Response` (lines 221-225)
- `POST .../excel/resolve-recipients` — bank/recipient resolution for preview, `ContactsExcelResolveRecipientsRequest/Response` (lines 240-244)
- **No `/excel/preview` multipart endpoint.**

## DTO shapes (confirmed — canonical features)

### Purpose Settings

```
PurposeExcelPreview               (PurposeExcelPreview.java:20-29)
  rows: List<PurposeExcelPreviewRow>
  totalCount, completedCount, errorCount: int

PurposeExcelPreviewRow            (PurposeExcelPreviewRow.java:18-33)
  rowIndex: int
  handUseUsagCd, useUsagNm, useUsagCont, parentUseUsagCd: String
  valid: boolean
  errors: List<PurposeExcelFieldError>

PurposeExcelFieldError            (PurposeExcelFieldError.java:32-41)
  field, code, message: String

PurposeExcelImportRequest         (PurposeExcelImportRequest.java:20-32)
  rows: List<Row>  (@NotNull @NotEmpty)
  Row(handUseUsagCd, useUsagNm, useUsagCont, parentUseUsagCd)
  // no upldOptn — append only

PurposeExcelImportResult          (PurposeExcelImportResult.java:8-10)
  insertedCount: int
```

### VAS Account

```
VasAccountExcelPreview            (VasAccountExcelPreview.java:17-26)
  rows: List<VasAccountExcelPreviewRow>
  totalCount, completedCount, errorCount: int

VasAccountExcelPreviewRow         (VasAccountExcelPreviewRow.java:17-29)
  rowIndex: int
  vasAcctCd, vasAcctNm: String
  valid: boolean
  errors: List<VasAccountExcelFieldError>

VasAccountExcelImportRequest      (VasAccountExcelImportRequest.java:27-36)
  upldOptn: String  (@Pattern "^[AR]$" — A=Append, R=Reset/Replace)
  rows: List<Row>
  Row(vasAcctCd, vasAcctNm)

VasAccountExcelImportResult       (VasAccountExcelImportResult.java:11-14)
  insertedCount, replacedCount: int
```

> Wire format is lowercase snake_case on the wire (global Jackson `SNAKE_CASE`); the Java field names above are the camelCase source — see memory `project_api_naming_lowercase.md`.

## Error codes — WBE204x family (confirmed)

Defined in `messages.properties` (EN base; lines 41-47):

| Code | Message | Meaning | Thrown by |
|---|---|---|---|
| `WBE2041` | `Excel file exceeds the {0}-row limit.` | row count over `MAX_ROWS` (500) | `PurposeExcelService.java:221`; `VasAccountExcelService.java:224` (also `OverseasPaymentExcelService.java:143`) |
| `WBE2042` | `Excel header row is invalid ({0}).` | header row mismatch vs template | `PurposeExcelService.java:586,596`; `VasAccountExcelService.java:450,455` |
| `WBE2043` | `Uploaded file is not a readable Excel workbook.` | corrupt / unreadable / wrong-extension / encrypted | `PurposeExcelService.java:336`; `VasAccountExcelService.java:275` |

⚠️ **Caveats (verified):**
- `WBE2040` (`VAS Account not found.`, `messages.properties:34`) is **NOT** an Excel code — it is VAS "not found / wrong tenant".
- `WBE2044` is **retired/reserved** (comment `messages.properties:48-51`) — do not reuse.
- `WBE2045/2046` are **Overseas Payment** codes (batch not found / already submitted), unrelated to the import family.
- `WBE2041/2042/2043` exist **only** in `messages.properties`, not in `messages_en.properties` (which carries only `WBE2040`). ⚠️ This split is a potential localization gap — flag if EN resolution matters.
- Import-time per-row failures (canonical) surface as `WBE_VALIDATION` with a 1-based row index.

## Related

- [functional-specification.md](functional-specification.md) · [business-flow.md](business-flow.md) · [source-code-index.md](source-code-index.md) · [README.md](README.md)
- Owning-feature interface specs: [`../../settings/purpose/interface-specification.md`](../../settings/purpose/interface-specification.md) · [`../../settings/vas-account/interface-specification.md`](../../settings/vas-account/interface-specification.md)
