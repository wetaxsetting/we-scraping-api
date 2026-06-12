# Settings > VAS Account — Interface Specification (Backend)

Every REST endpoint, request/response DTO field list, wire shape, and error code. The *what* is in
[functional-specification.md](functional-specification.md); flows in
[business-flow.md](business-flow.md). JEX source of truth:
[docs/jex/features/vas-account/interface-specification.md](../../../../../docs/jex/features/vas-account/interface-specification.md).

> **Wire casing.** All DTOs are migrated to **lowercase snake_case** via the global Jackson
> `SNAKE_CASE` strategy (Java fields stay camelCase). Envelope is `{ status, data, timestamp?, path?,
> trace_id? }`. `vas_acct_seq_no` ships as a **string** on the full row (legacy NUMERIC PK → string
> avoids JS precision loss) but as a **number** on the lookup row — both are intentional and reflected
> in the DTO types below.

Base URI: `/api/v1/settings/vas-account`. Class-level gate
`@PreAuthorize("@menuAuthz.canAccess('settings-core-vas-account')")`. All paths tenant-scoped.

---

## 1. List — `GET /api/v1/settings/vas-account`

Query params: `search_wd`, `stts` (repeatable), `sort_field`, `sort_direction`, `page` (default 0),
`size` (default 0 → 20), `all` (default false). When `all=true` → unpaged full set.

```
GET /api/v1/settings/vas-account?stts=A&stts=I&all=true
→ ApiResponse<VasAccountListResponse>
```

**`VasAccountListResponse`**

| Wire | Type | Meaning |
|---|---|---|
| `list` | `VasAccountRow[]` | rows on the requested page (or full set when `all=true`) |
| `total_count` | long | unpaged filtered total |
| `page_number` | int | zero-based page index (echoed after clamp) |
| `page_size` | int | rows/page (echoed after clamp; = count when `all=true`) |
| `total_pages` | int | filtered page count |

**`VasAccountRow`** (list + detail share this DTO)

| Wire | Type | Meaning |
|---|---|---|
| `vas_acct_seq_no` | string | surrogate key (string to dodge JS precision loss) |
| `vas_acct_cd` | string | code |
| `vas_acct_nm` | string | name |
| `vas_acct_enm` | string \| null | English name |
| `vas_acct_type` | string | `"0"`/`"1"`/`"2"` |
| `vas_acct_type_key` | string \| null | i18n key (`settings.vas_account.type.{dual,credit,debit}`) |
| `stts` | string | `"A"`/`"I"` |
| `stts_key` | string \| null | i18n key (`settings.vas_account.status.{active,inactive}`) |
| `cont` | string \| null | notes |
| `mod_id` | string | last modifier |
| `mod_dtm` | string | `yyyyMMddHHmmss` |

## 2. Lookup — `GET /api/v1/settings/vas-account/lookup`

Query param: `search_wd` (optional). Returns **active + live** rows, capped at 500, ordered by code.

```
GET /api/v1/settings/vas-account/lookup?search_wd=cash
→ ApiResponse<VasAccountLookupResponse>   // { items: VasAccountLookupRow[] }
```

**`VasAccountLookupRow`** — `vas_acct_seq_no` (**number**), `vas_acct_cd`, `vas_acct_nm`.

## 3. Detail — `GET /api/v1/settings/vas-account/{vasAcctSeqNo}`

Path: `vasAcctSeqNo` (Long). Live row only. → `ApiResponse<VasAccountRow>`. Missing → `WBE2040`;
non-numeric path / `seqNo ≤ 0` → `WBE_VALIDATION`.

## 4. Create — `POST /api/v1/settings/vas-account`

Body `@Valid VasAccountCreateRequest`:

| Wire | Type | Validation |
|---|---|---|
| `vas_acct_cd` | string | `@NotBlank @Size(min=3,max=15) @Pattern("^[A-Za-z0-9_]+$")` |
| `vas_acct_nm` | string | `@NotBlank @Size(max=50)` |
| `vas_acct_enm` | string | `@Size(max=50)` (optional) |
| `vas_acct_type` | string | `@Pattern("^[012]?$")` (optional; blank → `0` Dual) |
| `stts` | string | `@NotBlank @Pattern("^[AI]$")` |
| `cont` | string | `@Size(max=200)` (optional) |

→ `ApiResponse<VasAccountRow>`. Duplicate live code → `WBE0003`.

## 5. Update — `PUT /api/v1/settings/vas-account/{vasAcctSeqNo}`

Path `vasAcctSeqNo` + body `@Valid VasAccountUpdateRequest` (same field set + validation as Create).
→ `ApiResponse<VasAccountRow>`. Not-found → `WBE2040`; duplicate on code change → `WBE0003`.
`vas_acct_enm` / `vas_acct_type` / `cont` preserved when blank. Status change (A↔I) happens here.

## 6. Delete (soft) — `DELETE /api/v1/settings/vas-account/{vasAcctSeqNo}`

Sets `DEL_YN='Y'`. → `ApiResponse<Void>` (`ApiResponse.ok(null)`). Not-found → `WBE2040`.

## 7. Excel preview — `POST /api/v1/settings/vas-account/excel/preview`

`multipart/form-data`, part `file` (`MultipartFile`). → `ApiResponse<VasAccountExcelPreview>`.

**`VasAccountExcelPreview`** — `rows: VasAccountExcelPreviewRow[]`, `total_count`, `completed_count`,
`error_count` (all int).

**`VasAccountExcelPreviewRow`** — `row_index` (1-based), `vas_acct_cd`, `vas_acct_nm`, `valid` (bool),
`errors: VasAccountExcelFieldError[]`.

**`VasAccountExcelFieldError`** — `field` (`"vas_acct_cd"`/`"vas_acct_nm"`), `code`
(`required`/`length`/`format`/`dup_file`/`dup_db`), `message` (pre-localized).

Errors: empty/missing file → `WBE_VALIDATION`; header mismatch → `WBE2042`; > 500 rows → `WBE2041`;
unreadable workbook → `WBE2043`.

## 8. Excel import — `POST /api/v1/settings/vas-account/excel/import`

`application/json`, body `@Valid VasAccountExcelImportRequest`:

| Wire | Type | Validation |
|---|---|---|
| `upld_optn` | string | `@NotNull @Pattern("^[AR]$")` — Append / Replace |
| `rows` | `Row[]` | `@NotNull`; each `Row` = `{ vas_acct_cd, vas_acct_nm }` |

→ `ApiResponse<VasAccountExcelImportResult>` — `inserted_count`, `replaced_count` (int).
Empty rows → `WBE_VALIDATION`; > 500 → `WBE2041`; first invalid row → `WBE_VALIDATION` (`"row N: <msg>"`,
atomic abort).

## 9. Export — `POST /api/v1/settings/vas-account/export`

Body `@Valid ExcelExportRequest` (shared shape) + streams XLSX (`void` handler →
`HttpServletResponse`). Delegates to the shared `ExcelExportService` → `VasAccountExportProvider`
(`screenId = "vas-account"`, allowed FIELD_IDs `VAS_ACCT_CD` / `VAS_ACCT_NM` / `VAS_ACCT_TYPE` /
`STTS`). Type + Status emitted as localized labels.

## 10. Error codes

| Code | When |
|---|---|
| `WBI0000` | success envelope |
| `WBE0003` | duplicate `vas_acct_cd` (create, update-on-code-change, Excel append) |
| `WBE2040` | VAS Account not found in tenant (detail / update / delete) — merges "not found" + "wrong tenant" |
| `WBE2041` | Excel exceeds the 500-row cap |
| `WBE2042` | Excel header row invalid |
| `WBE2043` | Uploaded file is not a readable Excel workbook |
| `WBE_VALIDATION` | bean-validation failure · invalid `stts` filter · non-positive seqNo · non-numeric path · empty import / per-row import failure |
| `WBE_DB` | PK collision on concurrent insert (sequence race — no retry on this path) |
| `WBE9999` | unmapped server fault |

> **As-built vs. the older single-file spec.** The retired plan referenced a
> `POST …/excel/cell/validate` endpoint (`comm_0217_002_r002`), a `tot_cnt`/`suc_cnt`/`rec`
> Excel-preview shape, and `WBE0404`/`WBE2014` codes. None of those ship: there is **no cell-validate
> endpoint** (inline cell editing was dropped with the Type/English columns), the preview shape is
> `rows`/`total_count`/`completed_count`/`error_count`, not-found is `WBE2040`, and duplicate is
> `WBE0003`. This document is the authoritative as-built contract.
