# VAS Account — Refactor Feature Spec

> **⚠️ Superseded — preserved for traceability.** The primary, as-built documentation home for this
> feature is now the folder **[vas-account/](vas-account/README.md)** (9 docs). This single file
> predates the folder and describes the *planned* surface — it references a retired
> `POST …/excel/cell/validate` endpoint, the legacy `tot_cnt`/`suc_cnt`/`rec` Excel-preview wire shape,
> and `WBE0404`/`WBE2014` codes. The as-built reality (no cell-validate endpoint; preview ships
> `rows`/`total_count`/`completed_count`/`error_count`; not-found `WBE2040`; duplicate `WBE0003`) is in
> [vas-account/interface-specification.md](vas-account/interface-specification.md) and
> [vas-account/refactor-gap-analysis.md](vas-account/refactor-gap-analysis.md). Read the folder first;
> this file is kept only for migration history. **Note:** the legacy WSVC family is `comm_0217_*`
> (not `comm_0223`, which is Chart of Accounts), and there is **no parent-child hierarchy** in the DB
> or backend (the table is flat; the tree view is a client-side display-only prefix grouping).

| Field | Value |
|---|---|
| Slug | `vas-account` |
| Module | Settings |
| Status | Implemented (shipped 2026-05-11; integration tests deferred — see audit F1) |
| JEX twin | `comm_0217_001` family (`_001` list/delete · `_002` Excel upload · `_003` create · `_004` detail/update) |
| Refactor host | **Settings modal panel** (`<VasAccountPanel />` mounted inside `<SettingsModal>` — `components/settings/SettingsModal.tsx:23,162`). NOT a standalone Next.js route — the original `/settings/vas-account` plan was retired during implementation (see audit F4). View Setting `SCREEN_ID_TO_ROUTE` accordingly points at the modal's host route (`/banking/account-inquiry`) so deep links opening the popover still anchor on a valid page. |
| Refactor base URI | `/api/v1/settings/vas-account` (Spring Boot) |
| Audit `WSVC_ID` | `vas-account` (slug — never `comm_0217_001`) |
| View Setting `WEB_SVC_ID` | `vas-account` (single area `default`) |
| i18n key prefix | `settings.vas_account.*` (DB-style snake key segment, dotted) |

---

## 1. Summary

VAS Account ("Value-Added Service" / accounting chart of accounts entry) is the per-tenant accounting code list used by WeCMS bank-account tagging and transaction categorization. Each tenant maintains its own short codes (e.g. `111` "Tiền mặt") that downstream tables (`ACCT_MSTR.VAS_ACCT_SEQ_NO`, `ACCT_TRNS_HSTR.VAS_ACCT_SEQ_NO`, `ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_SEQ_NO`) reference by surrogate key. The screen is reached from the Settings popup tab "VAS Account".

The feature presents:

- A searchable, filterable list (Active / Inactive) of VAS Accounts.
- Hierarchical display: child rows whose `vas_acct_cd` starts with a parent's code render indented under that parent (frontend-computed prefix tree).
- CRUD entry points: Add, Edit, Delete (soft delete via `DEL_YN='Y'`).
- Excel Upload entry point with two write modes: **A**ppend or **R**eplace.

### In scope

- List + search + status filter (multi-select via Active / Inactive checkboxes).
- Detail (fetch single record for edit popup).
- Create (manual entry, popup form).
- Update (popup form, with duplicate-code re-check when `vas_acct_cd` changes).
- Soft delete (set `del_yn = 'Y'`).
- Excel upload — read sheet → row-by-row validation → bulk create. Two modes:
  - `A` append: insert rows that don't duplicate an existing live `vas_acct_cd`; reject duplicates with `WBE_DUPLICATE`.
  - `R` replace-all: soft-delete every live row for the tenant, then insert the entire payload.
- View Setting popover for the list grid (column visibility / order).
- Excel export of the list view (count + filter snapshot, shared export pipeline).
- Audit-log emission on create / update / delete / Excel upload via `AUDIT_LOGS_INFM` with `wsvc_id = 'vas-account'`.

### Out of scope (deferred)

- Bank Account linkage UI (the popup that picks a `vas_acct_seq_no` to attach to an `ACCT_MSTR` row) — that belongs to the bank-account feature and ports with the bank-account-management refactor.
- Auto-derivation of parent-child relationship as a server-side tree shape — the refactor keeps the legacy "flat list, frontend computes hierarchy by prefix" model.
- Bulk delete (legacy has no bulk delete; per-row only).
- Hard delete (PROD only soft-deletes; refactor matches).
- Korean / Khmer translations beyond what's already verified (refactor `i18n` follows `EN + KO + VI`; KM omitted, per wecms-api enum policy).

---

## 2. JEX source-of-truth citations

> All citations below are read-only. The refactor consumes them as functional spec, not as code to copy.

### View JSPs

- `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0217_001_view.jsp:230-294` — list tab markup, search input, Active/Inactive checkboxes, Excel-Upload + Add-VAS-Account buttons, 4-column table (VAS Code · VAS Account Name · Status · action icon).
- `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0217_002_view.jsp` — Excel upload popup view.
- `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0217_003_view.jsp` — Add VAS Account popup view.
- `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0217_004_view.jsp` — Edit VAS Account popup view.

### Action JSPs (business logic)

- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_001_r001_act.jsp:28-67` — list query: `BIZ_KEY` + `DEL_YN='N'` always; optional `SRCH_WD` matches `vas_acct_cd` or `vas_acct_nm` (ILIKE); `STTS` filter accepts `A`, `I`, or `N` (where `N` means "neither A nor I" — used as the "0 checkboxes ticked" sentinel).
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_001_d001_act.jsp:32-105` — soft delete: validate `VAS_ACCT_SEQ_NO`, find by `(BIZ_KEY, VAS_ACCT_SEQ_NO)`, throw `WBE0404` if not found, set `del_yn='Y'`, call `updateVasAcctDelYn(...)`.
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_002_r001_act.jsp:55-258` — Excel parse + validation: column 1 = `VAS_ACCT_CD` (required 3-15 chars; duplicate-check against live rows), col 2 = `VAS_ACCT_NM` (required ≤ 100), col 3 = `VAS_ACCT_TYPE` (free text → enum map), col 4 = `VAS_ACCT_ENM` (optional ≤ 50), col 5 = `CONT` (optional ≤ 500), col 6 = `STTS` (free text → A/I). Caps at 500 rows. Returns rows + per-row errors + counts (`TOT_CNT`, `SUC_CNT`, `ERR_CNT`).
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_002_r002_act.jsp:34-108` — single-field re-validation (per-cell) called when user edits a row in the upload preview grid.
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_002_c001_act.jsp:40-190` — Excel upload save: `UPLD_OPTN` must be `A` (append) or `R` (replace). Append blocks duplicates with `WBE0003`; replace soft-deletes every live row first, then inserts the new payload. `VAS_ACCT_TYPE` decoded from the labels `Dual` / `Credit balance` / `Debit balance` (defaults to DUAL on miss).
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_003_c001_act.jsp:33-116` — manual create: requires `VAS_ACCT_CD`, `VAS_ACCT_NM`, `VAS_ACCT_TYPE`, `STTS`. Throws `WBE0003` on duplicate `(BIZ_KEY, VAS_ACCT_CD)` where `DEL_YN='N'`. New `VAS_ACCT_SEQ_NO` from `SeqHelper.getNextVal(... "vas_acct_seq_no" ...)`.
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_004_r001_act.jsp:27-74` — detail fetch by `VAS_ACCT_SEQ_NO` (single PK lookup).
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_004_u001_act.jsp:34-156` — update: required fields `VAS_ACCT_SEQ_NO`, `VAS_ACCT_CD`, `VAS_ACCT_NM`, `VAS_ACCT_TYPE`, `STTS`. If `VAS_ACCT_CD` changes vs. the loaded row, run a separate duplicate-check; otherwise straight update.

### WSVC service contracts

- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0217_001_r001.xml:31-52` — list IN: `SRCH_WD` (100), `STTS` (1). OUT `REC` records: `VAS_ACCT_SEQ_NO`, `VAS_ACCT_CD`, `VAS_ACCT_NM`, `VAS_ACCT_ENM`, `VAS_ACCT_TYPE`, `STTS`, `CONT`, `MOD_ID`, `MOD_DTM`.
- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0217_001_d001.xml:31-39` — delete IN: `VAS_ACCT_SEQ_NO`. OUT: empty.
- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0217_003_c001.xml:31-45` — create IN: `VAS_ACCT_CD` (15), `VAS_ACCT_NM` (50), `VAS_ACCT_ENM` (50), `CONT` (500), `VAS_ACCT_TYPE` (20), `STTS` (1).
- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0217_004_r001.xml:31-46` — detail IN: `VAS_ACCT_SEQ_NO`. OUT: full record minus `BIZ_KEY` / audit columns.
- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0217_004_u001.xml:31-47` — update IN: as create + `VAS_ACCT_SEQ_NO` + `BIZ_KEY` (the BIZ_KEY input is shadowed server-side by the session, never trusted from wire).

### IDO XML

- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_R001.xml:24-57` — default read of all 12 columns.
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_R002.xml:24-44` — combo-lookup query (`VAS_ACCT_CD || ' ' || VAS_ACCT_NM AS DISP_VAS_ACCT_NM`) used by other screens to populate VAS pickers; the refactor exposes it as a separate combo endpoint (or reuses the list endpoint with a `combo` flag — see Decisions needed).
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_C001.xml:24-65` — INSERT.
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_U001.xml:24-50` — full UPDATE (COALESCE pattern).
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_U002.xml:24-39` — single-field UPDATE on `DEL_YN` (used by the soft-delete path).
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_D001.xml` — physical DELETE (defined but **unused** in JEX code paths — the live system always soft-deletes; refactor does the same).

### Entity / DAO / Enums

- `WECMS DEV/WECMS/src/wabooks/entity/BusiVasAcctInfm.java:30-158` — entity (`@Table("BUSI_VAS_ACCT_INFM")`); fields and copy-from/copy-to.
- `WECMS DEV/WECMS/src/wabooks/entity/BusiVasAcctInfmPk.java:22-61` — composite PK (`BIZ_KEY`, `VAS_ACCT_SEQ_NO`).
- `WECMS DEV/WECMS/src/wabooks/dao/BusiVasAcctInfmDAO.java:20-138` — `create()` auto-allocates `VAS_ACCT_SEQ_NO` via `SeqHelper.getNextVal`; `update()`; `deleteByPk()` (unused — soft delete preferred); `updateVasAcctDelYn()` (soft delete).
- `WECMS DEV/WECMS/src/wabooks/enums/VasAcctType.java:8-62` — three values: `DUAL("0")`, `CREDIT("1")`, `DEBIT("2")`. `parse(String)` also accepts the labels "Debit Balance", "Credit Balance", "Dual Balance" (used by Excel upload).
- `WECMS DEV/WECMS/src/wabooks/enums/AStts.java:8-57` — two values: `ACTIVE("A")`, `INACTIVE("I")`. `parse(String)` also accepts the labels "Active", "Inactive".

### Frontend (read-only ref)

- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0217_001.ts:14-310` — list page, hierarchy builder, row rendering, edit/delete popup wiring.
- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0217_003.ts` — Add VAS Account popup.
- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0217_004.ts` — Edit VAS Account popup.
- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0217_002.ts` — Excel Upload popup.

### Cross-module dependents (read-only)

- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_MSTR_R001.xml:69,115,174` — bank-account inquiry SELECTs / INSERTs the `VAS_ACCT_SEQ_NO` foreign key.
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_MSTR_C001.xml:62,102,144` — bank-account create writes `VAS_ACCT_SEQ_NO`.
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_TRNS_HSTR_R016.xml` — transaction history joins on `vas_acct_seq_no`.
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_TRNS_RCV_PAY_INFM_*.xml` — receive/pay records carry `vas_acct_seq_no`.

> **Impact note** — `BUSI_VAS_ACCT_INFM` is a parent table for `ACCT_MSTR` / `ACCT_TRNS_HSTR` / `ACCT_TRNS_RCV_PAY_INFM` via `vas_acct_seq_no`. Soft delete must NOT cascade. Refactor preserves the soft-delete-only contract — dependent rows continue to reference soft-deleted parents.

---

## 3. REST contract

Base URI: `/api/v1/settings/vas-account`. All endpoints are JWT-protected (`SecurityConfig` blanket auth-required rule).

### Endpoints

| # | Method | Path | Returns | Maps from |
|---|---|---|---|---|
| 1 | GET | `/api/v1/settings/vas-account` | `ApiResponse<VasAccountListResponse>` | `comm_0217_001_r001` |
| 2 | GET | `/api/v1/settings/vas-account/{vasAcctSeqNo}` | `ApiResponse<VasAccountRow>` | `comm_0217_004_r001` |
| 3 | POST | `/api/v1/settings/vas-account` | `ApiResponse<VasAccountRow>` | `comm_0217_003_c001` |
| 4 | PUT | `/api/v1/settings/vas-account/{vasAcctSeqNo}` | `ApiResponse<VasAccountRow>` | `comm_0217_004_u001` |
| 5 | DELETE | `/api/v1/settings/vas-account/{vasAcctSeqNo}` | `ApiResponse<Void>` | `comm_0217_001_d001` |
| 6 | POST | `/api/v1/settings/vas-account/excel/preview` | `ApiResponse<VasAccountExcelPreview>` | `comm_0217_002_r001` (multipart) |
| 7 | ~~POST~~ | ~~`/api/v1/settings/vas-account/excel/cell/validate`~~ | **Removed.** | ~~`comm_0217_002_r002`~~ — inline cell re-validation UX was retired by the PO call 2026-05-11 that dropped Type / English Name from the upload preview. The frontend dialog never edits cells in-place, so no server endpoint is needed. See audit F2. |
| 8 | POST | `/api/v1/settings/vas-account/excel/import` | `ApiResponse<VasAccountExcelImportResult>` | `comm_0217_002_c001` |
| 9 | POST | `/api/v1/settings/vas-account/export` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (XLSX) | New — via shared `common/export/`. Implemented by `VasAccountExportProvider` (registered against `screenId = "vas-account"`). Endpoint is `void`-returning; controller streams XLSX bytes into the `HttpServletResponse`. See audit F3. |

All endpoints scope to `BIZ_KEY = TenantContext.currentBizKey()`. Wire is lowercase snake_case (global Jackson `SNAKE_CASE` strategy). Envelope is `{ status, data, timestamp?, path?, trace_id? }`.

### Wire DTOs (snake_case on the wire; Java field names camelCase)

#### `VasAccountRow` (list row)

```json
{
  "vas_acct_seq_no":     "123",
  "vas_acct_cd":         "111",
  "vas_acct_nm":         "Tiền mặt",
  "vas_acct_enm":        null,
  "vas_acct_type":       "2",
  "vas_acct_type_key":   "settings.vas_account.type.debit",
  "stts":                "A",
  "stts_key":            "settings.vas_account.status.active",
  "cont":                null,
  "mod_id":              "user@example.com",
  "mod_dtm":             "20260511093028"
}
```

- `vas_acct_seq_no` ships as **string** on the wire (legacy `NUMERIC(10,0)` PK — strings dodge JS-number precision loss; matches the legacy frontend's `null2void(...)` treatment).
- `vas_acct_type` is the verbatim DB code (`"0"`, `"1"`, `"2"`); `vas_acct_type_key` is the i18n key for the localized label (`settings.vas_account.type.{dual,credit,debit}`). Same pattern as `acct_type_cd` + `acct_type_key` already in `BankCdResolver`.
- `stts` is the verbatim DB code (`"A"`, `"I"`); `stts_key` is the i18n key (`settings.vas_account.status.{active,inactive}`).
- `mod_dtm` is the legacy `varchar(14)` `yyyyMMddHHmmss` value — string, not ISO. Frontend formats for display.

#### `VasAccountDetail` (single record, includes `CONT`)

Same as `VasAccountRow`. Detail and list ship the same DTO; per CLAUDE.md "no two DTOs for the same payload".

#### `VasAccountListResponse`

```json
{
  "list": [ /* VasAccountRow[] */ ],
  "total_count": 42,
  "page_number": 0,
  "page_size":   20,
  "total_pages": 3
}
```

Top-level shape mirrors `ErpTrnsHistoryListResponse` — paginated from day one (DN-2 closed: align with ERP Transaction History). `list` is the rows on the requested page; `total_count` is the unpaged filtered total (after `search_wd` + `stts`); `page_number` is zero-based (Spring Data convention); `page_size` is echoed back **after** the server's clamp; `total_pages` is the unpaged filtered page count.

Wire query params on `GET /api/v1/settings/vas-account`:

- `page` (int, default `0`, zero-based).
- `size` (int, default `DEFAULT_PAGE_SIZE = 20`, clamped to `MAX_PAGE_SIZE = 100`).
- `search_wd`, `stts` (repeatable), `sort_field`, `sort_direction` — unchanged.

#### Create request — `VasAccountCreateRequest`

```json
{
  "vas_acct_cd":   "111",
  "vas_acct_nm":   "Tiền mặt",
  "vas_acct_enm":  "Cash",
  "vas_acct_type": "2",
  "stts":          "A",
  "cont":          "Notes"
}
```

#### Update request — `VasAccountUpdateRequest`

Same shape; path `{vasAcctSeqNo}` identifies the row. `biz_key` never appears on the wire — tenant comes from JWT.

#### Excel preview response — `VasAccountExcelPreview`

```json
{
  "tot_cnt": 12,
  "suc_cnt": 10,
  "err_cnt": 2,
  "rec": [
    {
      "row_index": 4,
      "vas_acct_cd":   "111",
      "vas_acct_nm":   "Tiền mặt",
      "vas_acct_type": "2",
      "vas_acct_enm":  null,
      "cont":          null,
      "stts":          "A",
      "errors": {
        "vas_acct_cd": { "code": "WBE0003", "message": "Duplicate VAS_ACCT_CD" }
      }
    }
  ]
}
```

#### Excel import request — `VasAccountImportRequest`

```json
{
  "upld_optn": "A",
  "rec": [ /* validated rows from preview, errors removed */ ]
}
```

#### Excel import response — `VasAccountImportResult`

```json
{
  "inserted_count": 10,
  "replaced_count": 0
}
```

### Status codes (HTTP)

- `200 OK` — success and **business errors** (legacy contract — frontend reads `status.error`, never the HTTP code).
- `400` — `IllegalArgumentException`, malformed JSON, malformed multipart.
- `401` — missing / invalid JWT (`JwtAuthenticationEntryPoint`).
- `403` — authenticated but lacks Settings authority (placeholder for ADMIN gate when `@PreAuthorize` lands — see Decisions needed).
- `404` — handled by `NoResourceFoundException` → `WBE_NOT_FOUND` for unknown URIs only; a missing VAS Account row returns `200` with `status.error=true` and `WBE0404`.
- `500` — `WBE9999` catch-all.

### Error codes used (add to `messages.properties`)

| Code | Meaning | Bound to |
|---|---|---|
| `WBI0000` | Success | every success response |
| `WBE0001` | Invalid `{0}` | validation failures (legacy parity) |
| `WBE0003` | Duplicate `{0}` | `vas_acct_cd` duplicate on create / update / Excel append |
| `WBE0404` | `{0}` not found | detail / update / delete by unknown `vas_acct_seq_no` |
| `WBE_VALIDATION` | Bean-validation failure | `MethodArgumentNotValidException` etc. |
| `WBE2014` (reuse) | Row not in tenant | `(biz_key, vas_acct_seq_no)` mismatch — single envelope merging "not found" + "wrong tenant" so we don't disclose existence across tenants. Same precedent as ERP Transaction History. |
| `WBE_VAS_EXCEL_TOO_MANY_ROWS` (new — TBD WBE2020 or next free) | Excel upload exceeds the 500-row cap | Excel preview |
| `WBE_VAS_EXCEL_HEADER_INVALID` (new) | Excel header row mismatch | Excel preview |
| `WBE9999` | Server error | catch-all |

Final codes assigned in the MR; placeholder names above are working aliases.

### Error envelope (every error)

```json
{
  "status":    { "error": true, "code": "WBE0003", "message": "Duplicate VAS Account Code" },
  "timestamp": "2026-05-11T09:30:28Z",
  "path":      "/api/v1/settings/vas-account",
  "trace_id":  "20260511093028PT12345678901234DEVL"
}
```

Inherits from the global `GlobalExceptionHandler` + `TraceIdFilter` — no custom envelope.

---

## 4. Validation rules

### Server-side (Spring `@Valid` + service-layer business checks)

| Field | Constraint | Source |
|---|---|---|
| `vas_acct_cd` | `@NotBlank`, `@Size(min=3, max=15)`, `@Pattern("^[A-Za-z0-9_]+$")` (refactor adds the alphanumeric guard — legacy validated length only, but legacy data is all alphanumeric and the prefix-tree frontend assumes it) | legacy `ValidationHelper.isValidStringInRange(value, 3, 15)` + `comm_0217_002_r001_act.jsp:200-211` |
| `vas_acct_nm` | `@NotBlank`, `@Size(max=50)` (DB column is 50; legacy WSVC/Excel both check ≤ 50/100 inconsistently — refactor follows the DB) | DB schema + `comm_0217_003_c001_act.jsp:36` |
| `vas_acct_enm` | optional, `@Size(max=50)` | `comm_0217_002_c001_act.jsp:55` |
| `vas_acct_type` | `@NotBlank`, value ∈ {`0`, `1`, `2`}; resolved via `VasAcctType.fromCode(...)` enum (server fails the request with `WBE0001` if unknown) | `VasAcctType` enum |
| `stts` | `@NotBlank`, value ∈ {`A`, `I`}; resolved via `AStts.fromCode(...)` | `AStts` enum |
| `cont` | optional, `@Size(max=500)` on the wire (DB column is 200; the legacy WSVC reserves 500 — refactor accepts ≤ 500 on the wire then truncates? No: refactor caps at the DB length and returns `WBE0001` if exceeded. See Decisions needed DN-3) | mixed legacy spec |
| Business: duplicate `vas_acct_cd` | `(biz_key, vas_acct_cd, del_yn='N')` unique within tenant — server-side `existsBy…` check on create, on update when code changes, on Excel append per-row | `comm_0217_003_c001_act.jsp:97-101`, `comm_0217_004_u001_act.jsp:117-126`, `comm_0217_002_r001_act.jsp:151-170` |

### Client-side (React Hook Form + Zod)

Mirror of the server constraints (frontend rejects bad input fast). Message keys live in the Zod schema as translation keys, resolved via `useTranslation()` at render time.

### Excel-upload-specific

- **Header check** — row index 2 (0-based) must contain the localized headers in one of EN / KO / VI. Mismatch → `WBE_VAS_EXCEL_HEADER_INVALID` with the offending cell name (legacy `validDefualtHeader(...)` pattern).
- **Row cap** — 500 rows max (legacy cap, preserved). Exceeded → `WBE_VAS_EXCEL_TOO_MANY_ROWS`.
- **Per-cell validation** errors are non-fatal at the preview stage: every row is returned with its row index and a per-field `errors` map. The frontend renders the preview grid and lets the user fix in-place. The single-cell `excel/cell/validate` endpoint re-runs validation for one cell when the user types a fix.
- **Upload mode `R` (Replace)** soft-deletes every live row for the tenant before inserting — non-recoverable from the UI. Frontend MUST show a confirmation dialog before posting (`useDialog({ type: "confirmation" })`).

---

## 5. Permission / role requirements

- **Authentication:** every endpoint requires a valid JWT (`SecurityConfig` blanket rule).
- **Authority gate:** Settings is conventionally an ADMIN-only menu in legacy WeCMS. The refactor JWT carries `auth_gr_cd` (`ADMIN` / `USER`) sourced from `BUSI_ADMN_INFM` presence at login. When `@PreAuthorize` lands (currently scaffolded but not wired — see `wecms-api/CLAUDE.md` "Authority model"), every endpoint on this controller gets `@PreAuthorize("hasRole('ADMIN')")`. Until then, the controller is **technically reachable by USER roles** — call it out in the MR and add a TODO comment referencing the broader `@PreAuthorize` MR.
- **Tenant scope:** every query / write joins on `BIZ_KEY = TenantContext.currentBizKey()`. The wire never accepts a tenant override.

---

## 6. Audit logging

Per the workspace cross-tree rule (2026-05-09 override) and `wecms-api/CLAUDE.md` §"Refactor identifier naming", every refactor-side write into `AUDIT_LOGS_INFM` carries `wsvc_id = 'vas-account'` (the slug — **never** `comm_0217_001` / `comm_0217_002` / `comm_0217_003` / `comm_0217_004`).

Audit rows emitted (one row per user-facing action):

| Action | `wsvc_id` | `audit_logs_actv` | `ref_no` | `ref_no1` |
|---|---|---|---|---|
| Create | `vas-account` | `CREATE` | new `vas_acct_seq_no` | `vas_acct_cd` |
| Update | `vas-account` | `UPDATE` | `vas_acct_seq_no` | new `vas_acct_cd` |
| Delete (soft) | `vas-account` | `DELETE` | `vas_acct_seq_no` | `vas_acct_cd` |
| Excel import — append | `vas-account` | `EXCEL_IMPORT_APPEND` | `inserted_count` | — |
| Excel import — replace | `vas-account` | `EXCEL_IMPORT_REPLACE` | `inserted_count` | — |

`audit_logs_actv_dtl` (text) carries a JSON snapshot of the diff (old vs. new for update; row payload for create / delete). `lang_cd` from `TenantContext.currentLangCd()`. `reg_id` / `reg_nm` from the JWT principal; `reg_dtm` = current UTC `yyyyMMddHHmmss`.

The audit write is invoked through the existing audit-service hook (already wired for firm-banking-settings — same pattern applies here). No new audit infrastructure required.

---

## 7. i18n key prefix

All user-facing strings for this feature live under `settings.vas_account.*`:

- `settings.vas_account.page_title` — "VAS Account"
- `settings.vas_account.subtitle` — "Set up VAS account for the transaction"
- `settings.vas_account.search_placeholder` — "Search"
- `settings.vas_account.filter.active` — "Active"
- `settings.vas_account.filter.inactive` — "Inactive"
- `settings.vas_account.column.code` — "VAS Code"
- `settings.vas_account.column.name` — "VAS Account Name"
- `settings.vas_account.column.status` — "Status"
- `settings.vas_account.type.dual` — "Dual Balance"
- `settings.vas_account.type.credit` — "Credit Balance"
- `settings.vas_account.type.debit` — "Debit Balance"
- `settings.vas_account.status.active` — "Active"
- `settings.vas_account.status.inactive` — "Inactive"
- `settings.vas_account.action.add` — "Add VAS Account"
- `settings.vas_account.action.upload` — "Excel Upload"
- `settings.vas_account.action.edit` — "Edit"
- `settings.vas_account.action.delete` — "Delete"
- `settings.vas_account.confirm.delete` — "Are you sure you want to delete this VAS Account?"
- `settings.vas_account.confirm.replace` — "Replace will soft-delete every existing VAS Account and insert the uploaded rows. Continue?"
- `settings.vas_account.excel.title` — "Excel Upload"
- `settings.vas_account.excel.upld_optn.append` — "Append"
- `settings.vas_account.excel.upld_optn.replace` — "Replace All"
- `settings.vas_account.empty` — "No Data Found"

Locales `en`, `ko`, `vi` get full coverage; `km` omitted (refactor enum policy — wecms-api CLAUDE.md "Adding a new code"). The legacy JEX dictionary IDs (`comm_0217_001_m001` … `_m010`, `_j001` … `_j005`) are NOT re-used — they're JEX ML-system artifacts.

---

## 8. View Setting

- `WEB_SVC_ID` = `vas-account` (single area `default`).
- Seed columns (matching legacy `comm_0217_001_view.jsp` 4-column grid + a hidden English-name + action column):

| FIELD_ID | DISP_ORD | USE_YN | FIX_YN | DISP_YN | SORT_YN | SIZE | Notes |
|---|---|---|---|---|---|---|---|
| `CHECKBOX` | 0 | Y | Y | N | N | `38px` | hidden from popover (special), only seeded if bulk-delete arrives — currently NOT seeded since legacy has no row select |
| `VAS_ACCT_CD` | 1 | Y | Y | Y | Y | `185px` | matches legacy `width:185px` |
| `VAS_ACCT_NM` | 2 | Y | N | Y | Y | `600px` | matches legacy `width:600px` |
| `VAS_ACCT_TYPE` | 3 | N | N | Y | Y | `140px` | hidden by default — legacy view doesn't show it but popover should let the user opt in |
| `STTS` | 4 | Y | N | Y | Y | `120px` | matches legacy `width:120px` |
| `ETC` | 99 | Y | Y | N | N | `45px` | row action menu (Edit / Delete) — special FIELD_ID, hidden from popover |

Seed migration: `V{N}__vas_account_view_setting_seed.sql`. `vas-account` adopts the popover the moment the seed ships — backend code does not change (the global `ScreenOptionController` accepts any `WEB_SVC_ID`).

The frontend's `SCREEN_IDS` registry at `wecms-web/lib/viewSettingScreens.ts` gets a new entry `VAS_ACCOUNT: "vas-account"` plus a `SCREEN_ID_TO_ROUTE["vas-account"] = "/settings/vas-account"`.

The View Setting `SORT_YN='Y'` columns (`VAS_ACCT_CD`, `VAS_ACCT_NM`, `VAS_ACCT_TYPE`, `STTS`) MUST have matching constants in the per-domain whitelist enum `VasAccountSortField` — common-table-sort rule (`wecms-api/CLAUDE.md` §"Common table sort").

---

## 9. Excel export

- Endpoint `POST /api/v1/settings/vas-account/export` via the shared `common/export/` pipeline.
- New `VasAccountExportProvider` class (one component, registers itself with `screenId = "vas-account"`).
- Allowed FIELD_IDs: `VAS_ACCT_CD`, `VAS_ACCT_NM`, `VAS_ACCT_TYPE`, `VAS_ACCT_ENM`, `STTS`, `CONT`.
- Reuses `VasAccountService.list(...)` — the export and the list share one query path, so filters / sort / View Setting visibility intersect identically.
- Subheader (backend-computed): `"<count> VAS Account(s)"` (no monetary totals for this feature).

---

## 10. Cross-module impact (read-only — for awareness)

- `ACCT_MSTR.VAS_ACCT_SEQ_NO` (bank account master) references `BUSI_VAS_ACCT_INFM`. Soft delete here does NOT cascade; downstream rows continue pointing to soft-deleted parents (legacy behaviour).
- `ACCT_TRNS_HSTR.VAS_ACCT_SEQ_NO`, `ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_SEQ_NO` — same relationship; no cascade.
- The "VAS picker" used by other forms (`comm_0218_nxisas`, `fnsh_0002_001_osmc_VN`, etc.) calls `BUSI_VAS_ACCT_INFM_R002` (the `DISP_VAS_ACCT_NM` combo query). The refactor exposes this either as `GET /api/v1/settings/vas-account?combo=true` (returns the DISP-concat shape) or as a separate `/picker` endpoint. **Out of scope for this feature MR**; revisit when the first refactor screen needs a VAS picker.

---

## 11. Decisions needed (DN)

| ID | Question | Why it matters | Proposed default |
|---|---|---|---|
| DN-1 | Authority gate — does VAS Account require `ROLE_ADMIN`, or is it open to all authenticated users in the same tenant? | Legacy JEX gates the Settings popup by menu permission (USER vs ADMIN) but the menu-permission system itself is not yet ported. | Hold the endpoints unannotated this MR; add `@PreAuthorize("hasRole('ADMIN')")` when the auth gate MR lands. Document explicitly in the controller Javadoc + TODO. |
| DN-2 | ~~Pagination — VAS Account is currently small (≤ 200 rows/tenant on DEV), and the legacy frontend renders the whole list in one go.~~ | — | **CLOSED 2026-05-11.** Paginate from day one matching the ERP Transaction History page (`page` + `size` query params, zero-based, `DEFAULT_PAGE_SIZE = 20`, `MAX_PAGE_SIZE = 100`; response carries `list`, `total_count`, `page_number`, `page_size`, `total_pages`). |
| DN-3 | ~~`cont` length on the wire — DB column is 200 chars, legacy WSVC reserves 500.~~ | — | **CLOSED 2026-05-11.** Cap at **200** (DB authority); payloads > 200 chars rejected with `WBE0001`. Backend DTO carries `@Size(max=200)`; frontend Zod schema mirrors. The legacy WSVC `length=500` reservation is treated as an over-allocation and ignored. |
| DN-4 | ~~Excel column order — legacy header sequence is `No. / Account Code / Account Name / Account Type / English Name / Description / Status`.~~ | — | **CLOSED 2026-05-11.** Keep the legacy column order verbatim. Refactor Excel template stays drop-in compatible with existing tenant files. Header row 2 (0-based) must match one of the EN / KO / VI label sets in that exact column sequence — see backend doc §"Excel-upload-specific". |
| DN-5 | ~~JPA entity column subset — all 13 columns or the wire-visible subset?~~ | — | **CLOSED 2026-05-11.** `BusiVasAcctInfm` entity maps **all 13 columns** (`biz_key`, `vas_acct_seq_no`, `vas_acct_cd`, `vas_acct_nm`, `vas_acct_enm`, `vas_acct_type`, `stts`, `cont`, `del_yn`, `reg_id`, `reg_dtm`, `mod_id`, `mod_dtm`). Small table; no reason to subset. DTO exposes the 10 wire-visible fields; `biz_key`, `del_yn`, and the `reg_*` audit pair stay server-only. See backend doc §"Entity". |
| DN-6 | ~~Preserve legacy "child rows render indented under parent by `vas_acct_cd` prefix" behaviour?~~ | — | **CLOSED 2026-05-11.** Yes — refactor frontend keeps the prefix-tree builder. `VasAccountTable` runs `buildHierarchy(rows)` (mirrors `comm_0217_001.ts:200-251`) on the page slice and renders parents with indented children. The DB has no `parent_id` column; tree shape is purely client-derived from `vas_acct_cd` string prefixes. **Pagination note:** since pagination is server-side (DN-2), the tree is computed on the **per-page slice** — a parent on page 1 and its prefix-children on page 2 will render flat on each page; this matches the legacy single-page paint and is acceptable for the small per-tenant row counts. |
| DN-7 | ~~Status badge tokens — reuse `wb-status-approved/rejected-*` or new `wb-status-vasacc-*`?~~ | — | **CLOSED 2026-05-11** (provisional). Reuse the existing `StatusBadge` with `wb-status-approved-*` (green) for `ACTIVE` and `wb-status-rejected-*` (red) for `INACTIVE`. **Reconfirm against Figma during implementation** — if the Figma frame disagrees, add `wb-status-vasacc-*` tokens in `app/globals.css` under `@theme inline` in the same MR and update `VasAccountStatusBadge` to consume them. Call the deviation out in the PR description. |
| DN-8 | ~~Bulk-delete affordance (`CHECKBOX` column + bulk action)?~~ | — | **CLOSED 2026-05-11.** No. Legacy `comm_0217_001` has no bulk delete; refactor matches. View Setting seed (database doc §"`V{N+2}__vas_account_view_setting_seed.sql`") **omits** the `CHECKBOX` special FIELD_ID. Only the per-row action menu (Edit / Delete via the `ETC` column) provides delete. Bulk-select can be added in a future MR if PO requests — at that point the same migration adds the `CHECKBOX` seed row, the page wires a multi-select state, and a new `DELETE /api/v1/settings/vas-account/bulk` endpoint lands. |

All eight Decisions Needed (DN-1 through DN-8) are now closed as of 2026-05-11. The plan is ready to drive an implementation MR.
