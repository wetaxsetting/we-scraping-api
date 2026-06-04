# Add-on Service > Operation Status Inquiry — Backend API Design

> **Consolidated feature docs:** see [../../../features/addon-service/operation-status-inquiry/](../../../features/addon-service/operation-status-inquiry/) — especially [interface-specification.md](../../../features/addon-service/operation-status-inquiry/interface-specification.md) for the current (completed) REST contract. This file remains the deep API-design reference.

**Module:** Add-on Service · `domain/operation/`
**Refactor screen ID (proposed):** `addon-operation-status`
**JEX counterpart:** `aosv_0001_001_r001` (read) — `aosv_0001_001_u001` (write, OUT of scope)
**Feature spec:** [../../features/addon-service/operation-status-inquiry.md](../../../features/addon-service/operation-status-inquiry.md)
**Implementation plan:** [../../planning/addon/operation-status-inquiry-plan.md](../../../planning/addon/operation-status-inquiry-plan.md)
**Status:** **Phase 1 read endpoint working end-to-end.** Filters / pagination / sort / Excel export pending. Inquire / Update endpoints deferred (no GDT bridge in refactor R&D scope).

JEX is **read-only reference**. This file is the canonical refactor backend design — backend implementation is approved to start subject to the open questions in the planning doc. All references here to "today" mean Sprint 1 stabilisation state; references to "Phase 2 / 4" are the planned extensions.

---

## Endpoints

### Today (Sprint 1)

| Method | Path | Purpose | Auth | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/addon/operation-status` | List operation statuses for the current tenant | JWT | `ApiResponse<OperationStatusListResponse>` |

### Proposed (Phase 4)

| Method | Path | Purpose | Auth | Status |
|---|---|---|---|---|
| `GET` | `/api/v1/addon/operation-status` | Same as above + `companyStatus` / `page` / `size` / `sortField` / `sortDirection` query params | JWT | Extension |
| `POST` | `/api/v1/addon/operation-status/inquire` | Bulk lookup-by-MST against the GDT (legacy NXISAS path) | JWT | **Stub** in Phase 4 — returns `WBE_NOT_IMPLEMENTED` (or equivalent) until / unless the NXISAS bridge is ported. Real wiring is deferred indefinitely; refactor R&D scope is local-only per project_refactoring_scope.md (memory: project_refactoring_scope). |
| `PUT` | `/api/v1/addon/operation-status/{taxcode}` | Manual operator edit of a single MST's snapshot. **Out of refactor R&D scope** — refactor is read-only. Listed for completeness because the original task brief asks for it. | JWT | Deferred |
| `POST` | `/api/v1/addon/operation-status/export` | XLSX export of the filtered list | JWT | Per [§ Excel export](../../../../CLAUDE.md). Allowed FIELD_IDs match the table column set; subheader = `<count> companies · As of <today>`. |

The path-param shape uses `{taxcode}` deliberately — `OPRT_STTS_INFO`'s PK is composite `(BIZ_KEY, TAXCODE)` and `BIZ_KEY` is session-scoped, so `TAXCODE` is the right URL identifier. Do **not** introduce a synthetic `companyId` PK — the legacy table has no such column.

### Why per-resource, not a generic dispatcher

The Excel endpoint follows the project-wide [§ Excel export](../../../../CLAUDE.md) rule (decision 3): every export gets a per-resource controller method, never a `POST /api/v1/exports/{screenId}` dispatcher. The Inquire endpoint is similarly resource-specific; do not bundle it into a cross-feature "external integrations" router.

---

## Request parameters

### `GET /api/v1/addon/operation-status` query parameters

| Param | Type | Today | Phase 4 | Notes |
|---|---|---|---|---|
| `q` | `String` | ✅ optional | ✅ optional | Up to 100 chars (legacy WSVC `SRCH_WD` length). Partial match. **Naming open** — see Phase 4 task #1 in the planning doc; choice between `q` (current refactor) vs `SRCH_WD` (legacy WSVC parity). |
| `companyStatus` | `String` | ❌ not accepted | ✅ optional | Up to 255 chars (legacy WSVC `COMPANYSTATUS` length). Partial match `ILIKE '%X%'` against the raw stored Vietnamese string. **Wire token is the raw substring**, not a canonical key — backend does NOT translate. |
| `page` | `Integer` (zero-based) | ❌ not accepted (server returns full result set) | ✅ optional, default `0` | Bean-validated `@Min(0)`. |
| `size` | `Integer` | ❌ not accepted | ✅ optional, default `10` | Bean-validated `@Min(1) @Max(100)`. Default = canonical `PAGE_SIZE_OPTIONS[0]`. Backend clamps to `MAX_PAGE_SIZE = 100`. |
| `sortField` | `String` | ❌ not accepted | ✅ optional | View Setting `FIELD_ID` — `COMPANY` / `OPERATION_STATUS` / `LAST_UPDATE`. **No `@Pattern` validation** per [§ Common table sort](../../../../CLAUDE.md) — the per-domain enum's lenient `parse(...)` is the validator; unknown values fall back silently to default ORDER BY. |
| `sortDirection` | `String` | ❌ not accepted | ✅ optional | `ASC` / `DESC`. Lenient parse via [`SortDirection.parse(...)`](../../../../src/main/java/com/kosign/wecms/common/web/SortDirection.java); blank / unknown → null → default ORDER BY. |

**Auth-derived** (not on the wire): `BIZ_KEY` from `TenantContext.currentBizKey()`, populated by [`JwtAuthenticationFilter`](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) from the validated JWT claim. Endpoint does not accept a `BIZ_KEY` query param — wildcard filters are forbidden by [`TenantProperties`](../../../../src/main/java/com/kosign/wecms/config/TenantProperties.java).

### `POST /api/v1/addon/operation-status/inquire` request body (proposed)

```json
{
  "TAXCODES": ["0102345678", "0103456789"]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `TAXCODES` | `String[]` | yes | 1..50 entries (or whatever cap the GDT bridge enforces — TBD). Each entry: numeric Vietnamese MST. **Bean-validate** with `@Size(min = 1, max = 50)` + `@Pattern(regexp = "^\\d+$")` per element. |

Phase 4 stub returns `ApiResponse.error("WBE_NOT_IMPLEMENTED", …)` with HTTP 200 (per the project's "business errors return 200" rule — see [§ Response envelope](../../../../CLAUDE.md)). Real implementation is deferred until / unless the NXISAS bridge is ported.

### `PUT /api/v1/addon/operation-status/{taxcode}` request body (proposed — out of scope)

```json
{
  "COMPANYSTATUS": "...",
  "OFFICEADD":     "...",
  "REPRESENTATIVEFIRSTNAME": "..."
}
```

**Out of refactor R&D scope.** Manual operator edits would normally route through `aosv_0001_001_u001`; refactor is read-only. Documented here only because the task brief asks for it. If a future sprint genuinely needs manual edits, this is where the endpoint lives.

---

## Response structure

### `OperationStatusListResponse` (today)

```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "ROWS": [
      {
        "TAXCODE":        "0102345678",
        "COMPANY_NM":     "CUSTOMER ABC LTD",
        "COMPANY_STATUS": "NNT đang hoạt động (đã được cấp GCN ĐKT)",
        "STATUS_CD":      "active_certificate_issued",
        "OFFICE_ADD":     "33 Phạm Hùng, Mỹ Đình 2, Nam Từ Liêm, Hà Nội",
        "LAST_UPDATE":    "20251125094000"
      }
    ],
    "TOTAL_COUNT": 5
  }
}
```

Backed by the records [`OperationStatusListResponse`](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/OperationStatusListResponse.java) (`ROWS`, `TOTAL_COUNT`) + [`OperationStatusRow`](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/OperationStatusRow.java) (6 fields above).

### `OperationStatusListResponse` (Phase 4 — pagination metadata)

```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "ROWS": [ /* … */ ],
    "TOTAL_COUNT": 25,
    "TOTAL_PAGES": 3,
    "PAGE_NUMBER": 0,
    "PAGE_SIZE":   10
  }
}
```

The four pagination fields land in the same MR as the server-side pagination wiring (`Pageable` repository method + `Page<OprtSttsInfo>` mapping in the service).

### Pagination metadata semantics

- `TOTAL_COUNT` — count of rows after every WHERE predicate is applied (tenant + status filter + keyword filter), unpaged.
- `TOTAL_PAGES` — `ceil(TOTAL_COUNT / PAGE_SIZE)`. `0` when `TOTAL_COUNT == 0`.
- `PAGE_NUMBER` — zero-based; the `page` query param echoed back.
- `PAGE_SIZE` — the `size` query param echoed back, after backend clamping.

### Wire field naming — divergence vs legacy WSVC

| Legacy WSVC `REC` field | Refactor `OperationStatusRow` field | Decision |
|---|---|---|
| `TAXCODE` | `TAXCODE` | ✅ aligned |
| `COMPANYNAME` | `COMPANY_NM` | ⚠️ diverges — Phase 4 task #6 in planning doc decides whether to keep the refactor name or revert to legacy parity |
| `COMPANYSTATUS` | `COMPANY_STATUS` | ⚠️ diverges (underscore inserted) — same Phase 4 decision |
| (n/a — refactor-only) | `STATUS_CD` | Refactor addition — canonical 3-state enum, derived server-side |
| `OFFICEADD` | `OFFICE_ADD` | ⚠️ diverges (underscore inserted) — same Phase 4 decision |
| `SEARCHDATETIME` | `LAST_UPDATE` | ⚠️ diverges (renamed) — same Phase 4 decision |
| `REPRESENTATIVEFIRSTNAME` | (not surfaced today) | Phase 4 task #6 — register column with `USE_YN='N'` so opt-in is seed-only |

**Sales e-Invoices precedent (IR-7):** keep legacy WSVC field names verbatim for traceability. If Phase 4 follows that precedent, the refactor flips to `COMPANYNAME` / `COMPANYSTATUS` / `OFFICEADD` / `SEARCHDATETIME` and the frontend mapper [`mapOperationCompanyRow`](../../../../../wecms-web/lib/api/mappers.ts) absorbs the rename in a single MR.

---

## Operation status mapping

`COMPANYSTATUS` is **GDT-supplied free-form Vietnamese text** — not a controlled enum on the JEX side. Legacy stores whatever string the GDT lookup endpoint returned; the legacy localisation helper `wb_get_operat_stts(value)` is **commented out** in the legacy action JSP (see [`aosv_0001_001_r001_act.jsp:48-49`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp)). The refactor preserves the raw string on the wire (`COMPANY_STATUS`) AND derives a canonical 3-state code (`STATUS_CD`) for frontend display.

### Server-side coercion — [`OperationStatusService.resolveStatusCd(...)`](../../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java)

```java
if (status == null)                              return "not_registered";
if (status.toLowerCase().contains("đang hoạt động")) return "active_certificate_issued";
if (status.toLowerCase().contains("tạm ngừng"))      return "suspended";
return "not_registered";
```

| Canonical key (`STATUS_CD`) | Sample raw `COMPANYSTATUS` (Vietnamese) | Frontend i18n key |
|---|---|---|
| `active_certificate_issued` (≈ "REGISTERED") | `NNT đang hoạt động (đã được cấp GCN ĐKT)` | `operation.status.active_certificate_issued` |
| `suspended` | `NNT tạm ngừng hoạt động kinh doanh` | `operation.status.suspended` |
| `not_registered` | (anything not matching the two patterns above, OR null/empty) | `operation.status.not_registered` |

There is **no `UNKNOWN`** state — anything unrecognised is bucketed into `not_registered`. The legacy TS legend ([`aosv_0001_001.ts`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts)) carries 12 distinct Vietnamese strings → short EN labels; the refactor's 3-state collapse is a deliberate Phase 2 simplification matching what the static UI renders. **Information loss is acceptable** because the raw `COMPANY_STATUS` string is preserved on the wire — Phase 4 should display it as a tooltip / sub-line.

### Filter wire semantics

- Wire `companyStatus` query param: **raw Vietnamese substring**, never a canonical key.
- Backend predicate: `companystatus ILIKE '%' || ? || '%'` (legacy parity).
- The dropdown UI sends the raw token (`""` / `"đang hoạt động"` / `"ngừng"` / `"tạm ngừng"`); display labels go through i18n.

If Phase 4 ever decides to filter by canonical key instead of raw substring, the service will need a server-side reverse map (canonical key → list of substrings, OR-joined). **Today's design is deliberately raw-string** — keeps backend simple, mirrors legacy.

### Why no DB-function port

`wb_get_operat_stts(value, LANG_CD)` is **NOT in local Flyway** — it is the legacy localisation helper. Port options were considered:

1. **Port** to a Flyway migration (legacy parity) — rejected: it duplicates frontend regex logic that already covers vi/en/ko.
2. **Replicate in Java** emitting canonical keys — partially adopted (the `STATUS_CD` derivation is the same idea, simplified to 3 states).
3. **Replicate in frontend** — rejected: status display would diverge between the page and the Excel export.

The current 3-state Java coercion is the right Phase 2 choice. Match the Sales e-Invoices Phase-2 stance on `wb_get_invoice_status` ([sales-e-invoices-plan.md IR-4](../../../planning/addon/sales-e-invoices-plan.md)) — **do not port the DB function**; replicate frontend-side via mapper or server-side via service.

---

## Tenant filtering

**`BIZ_KEY` is the only always-applied baseline filter.** Source of truth: `TenantContext.currentBizKey()`, populated by [`JwtAuthenticationFilter`](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) from the validated JWT claim. Wildcard filters (`%`) are rejected by [`TenantProperties`](../../../../src/main/java/com/kosign/wecms/config/TenantProperties.java) — single concrete `BIZ_KEY` only.

Repository methods are all `BIZ_KEY`-scoped:

```java
List<OprtSttsInfo> findByBizKeyOrderBySearchdatetimeDesc(String bizKey);
List<OprtSttsInfo> findByBizKeyAndCompanynameContainingIgnoreCaseOrderBySearchdatetimeDesc(String bizKey, String q);
List<OprtSttsInfo> findByBizKeyAndTaxcodeContainingOrderBySearchdatetimeDesc(String bizKey, String q);
```

Phase 4 introduces a `Pageable`-aware variant:

```java
Page<OprtSttsInfo> search(String bizKey, String q, String companyStatus, Pageable pageable);
```

Likely a `@Query` method (or Specifications API) so the WHERE chain combines into one query. Both the page query and the (later) export query MUST share one helper that builds the WHERE chain so user filters cannot drift between them — same pattern as Sales e-Invoices' Phase-2 aggregation parity.

### Cross-tenant isolation contract

- Every repository call passes `bizKey` explicitly. The service NEVER queries `OPRT_STTS_INFO` without a `BIZ_KEY` predicate.
- Integration tests assert: a JWT issued for a non-`VN_VNM0004_000001` tenant returns zero rows even when bypassing the keyword filter.
- A future repository method that omits `BIZ_KEY` (e.g. internal admin tooling) goes through a separate service / package — not via this domain.

---

## Bulk processing behaviour

### Bulk Inquire (deferred)

Legacy "Update" button re-polls every visible row against GDT — sequentially via `nxisas_oprtstts_*`, no async. With 100 rows this can block the UI for minutes.

**Refactor R&D Phase 1:** Inquire button = inert + tooltip; Update button = `refreshKey++` → refetch list endpoint. No GDT call.

**Refactor Phase 2 (deferred — only if NXISAS bridge is ported):**

- `POST /api/v1/addon/operation-status/inquire` accepts up to ~50 `TAXCODES` per call.
- Implementation = async job: enqueue work via Spring `@Async` (or a job table polled by a scheduler), return `{ JOB_ID, STATUS: "QUEUED" }` immediately, let the UI poll `GET /api/v1/addon/operation-status/inquire/{jobId}` for completion.
- Per polled MST, write one `EXT_TRAN_CALL_HIS` row (`SVC_ID`, `WEB_SVC_ID = 'addon-operation-status'`, `BIZ_KEY` from session, `TAXCODE`, `RSLT_CD`, `START_DATE` / `END_DATE`, `ERR_CONT` from GDT response). Mirror legacy NXISAS audit-trail.
- On success, write the GDT response back into `OPRT_STTS_INFO` via `MERGE` (or `INSERT … ON CONFLICT (BIZ_KEY, TAXCODE) DO UPDATE`).

### Bulk Excel export

Routes through the shared [`ExcelExportService`](../../../../src/main/java/com/kosign/wecms/common/export/ExcelExportService.java) with `Pageable.unpaged()`, capped at `MAX_ROWS = 50_000` per the global rule. Phase 4 ships an `OperationStatusExportProvider implements ExportRowProvider`:

- `screenId() = "addon-operation-status"`
- `allowedFieldIds() = { COMPANY, ADDRESS, OPERATION_STATUS, LAST_UPDATE, REPRESENTATIVEFIRSTNAME }` (matches the View Setting seed; `CHECKBOX` / `ETC` filtered out by `ScreenOptionService.SPECIAL_FIELD_IDS`)
- Calls back into `OperationStatusService.list(...)` to reuse the same WHERE chain as the page query
- Subheader: `<count> companies · As of <today (YYYY-MM-DD)>`
- Filename (caller-supplied): `Operation_Status_Inquiry_<DDMMYYYY>.xlsx`

For tenant `VN_VNM0004_000001` the row count is well under the 50k cap; the cap exists for cross-tenant consistency.

---

## Error handling

All errors use the standard `ApiResponse<T>` envelope (`ERROR=true`, `CODE`, localised `MESSAGE`). Business errors return HTTP 200 — clients inspect `COMMON_HEAD.ERROR`. Unexpected exceptions return HTTP 500 with `WBE9999`.

| Code | When | HTTP |
|---|---|---|
| `WBI0000` | Success | 200 |
| `WBE_VALIDATION` | Bean-validation failure (e.g. `size > 100`, `taxcode` not numeric) | 200 |
| `WBE2010` | Excel export — unknown `screenId` (request body `screenId` not registered with `ExportableScreenRegistry`) | 200 |
| `WBE2011` | Excel export — exceeds 50,000-row cap | 200 |
| `WBE2012` | Excel export — unexpected runtime error before bytes flushed | 200 |
| `WBE2013` | Excel export — request has no exportable columns after intersection with View Setting / allow-list | 200 |
| `WBE_NOT_IMPLEMENTED` (proposed — add to `messages.properties`) | Inquire endpoint stub return value (until / unless NXISAS bridge is ported) | 200 |
| Unauthenticated | Missing / invalid / expired JWT | 401 (via [`JwtAuthenticationEntryPoint`](../../../../src/main/java/com/kosign/wecms/common/security/) — frontend Axios interceptor handles re-login) |
| `WBE9999` | Unexpected server error (DB outage, NPE, etc.) | 500 |

### Validation errors

`@Valid @RequestParam` / `@Valid @RequestBody` failures route through [`GlobalExceptionHandler`](../../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) — `MethodArgumentNotValidException` / `ConstraintViolationException` → `WBE_VALIDATION` with the offending field listed in `MESSAGE`.

### External API failure (Inquire — deferred)

When the GDT bridge eventually lands, the `inquire` endpoint must:

1. Validate input — bad MST format → `WBE_VALIDATION`.
2. Per-MST poll: catch any `RestClientException` / timeout → write a `RSLT_CD = 'FAIL'` row to `EXT_TRAN_CALL_HIS` with `ERR_CONT` = the GDT response body (truncated to 1000 chars per the column length).
3. Aggregate per-MST results into a job-status response — partial success returns `WBI0000` with a `RESULTS[]` array showing per-MST status; total failure returns the aggregated error code.
4. NEVER bubble GDT-side errors as `WBE9999` — degrade per-row, complete the job, surface partial results.

**Refactor R&D scope: not implemented.** The above is a Phase 2 design note for whoever ports NXISAS.

### `STATUS_CD` defensive fallback

Frontend mapper falls back to `active_certificate_issued` if the wire `STATUS_CD` is missing or unknown. Backend `resolveStatusCd(...)` falls back to `not_registered` for null / unrecognised input. Both fallbacks are **defensive only** — if defaults start firing on real data, the regex / mapping is wrong and must be tightened.

---

## Service / controller / repository implementation

### Today

- Controller: [`OperationStatusController`](../../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java) — thin, single `@GetMapping("/operation-status")` accepting `q` only.
- Service: [`OperationStatusService`](../../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) — `list(search)` reads tenant from `TenantContext`, branches on blank/non-blank search, calls one of three repository finders, maps via `toRow(...)` + `resolveStatusCd(...)`. `@Transactional(readOnly = true)`.
- Repository: [`OprtSttsInfoRepository`](../../../../src/main/java/com/kosign/wecms/domain/operation/repository/OprtSttsInfoRepository.java) — Spring Data JPA derived methods.
- Entity: [`OprtSttsInfo`](../../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfo.java) + `OprtSttsInfoId` (composite PK).

### Phase 4 deliverables

| # | Task | Where |
|---|---|---|
| 1 | Add `companyStatus` query param + service-side WHERE predicate (`companystatus ILIKE '%X%'`). | `OperationStatusController`, `OperationStatusService` |
| 2 | Tighten the OR-clause to `(companyname ILIKE '%X%' OR taxcode ILIKE '%X%')` with `ESCAPE '\'`. Replace the current 2-call merge in `service.list(...)` with a single repository method. | `OperationStatusService`, `OprtSttsInfoRepository` |
| 3 | Add server-side pagination — `page` + `size` query params, switch repository call to `Pageable`-aware variant, response carries `TOTAL_COUNT` / `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE`. | All three layers |
| 4 | Add server-side sort per [§ Common table sort](../../../../CLAUDE.md). Write `OperationStatusSortField` whitelist enum (`COMPANY → companyname`, `OPERATION_STATUS → companystatus`, `LAST_UPDATE → searchdatetime`). Default sort `SEARCHDATETIME DESC` + secondary `TAXCODE ASC`. | New: `domain/operation/service/OperationStatusSortField.java` |
| 5 | Decide wire field-name parity (Phase 4 task #6 in planning doc). | `OperationStatusListResponse`, `OperationStatusRow`, `mapOperationCompanyRow` |
| 6 | Excel export endpoint + provider per [§ Excel export](../../../../CLAUDE.md). | New: `domain/operation/export/OperationStatusExportProvider.java`, `OperationStatusController#export` |
| 7 | (Deferred) Inquire endpoint stub. | `OperationStatusController#inquire` |

### Public service signatures (Phase 4 target)

```java
@Transactional(readOnly = true)
public OperationStatusListResponse list(
        String q,
        String companyStatus,
        int page,
        int size,
        String sortField,
        String sortDirection);
```

The controller forwards raw query params; the service does the parsing (sort enum, sort direction, page clamp). Per [§ Common table sort](../../../../CLAUDE.md), the controller stays thin — enum parsing happens in the service.

---

## Related backend files

### Today (Sprint 1 — already implemented)

| File | Role |
|---|---|
| [`OperationStatusController.java`](../../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java) | `@RequestMapping("/api/v1/addon")` + `@GetMapping("/operation-status")` |
| [`OperationStatusService.java`](../../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) | List query + status coercion |
| [`OperationStatusListResponse.java`](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/OperationStatusListResponse.java) | Top-level response record |
| [`OperationStatusRow.java`](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/OperationStatusRow.java) | Per-row record |
| [`OprtSttsInfo.java`](../../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfo.java) | JPA entity, `@IdClass(OprtSttsInfoId.class)` |
| [`OprtSttsInfoId.java`](../../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfoId.java) | Composite PK class |
| [`OprtSttsInfoRepository.java`](../../../../src/main/java/com/kosign/wecms/domain/operation/repository/OprtSttsInfoRepository.java) | Spring Data JPA |
| [`V16__sprint1_operation_status_schema.sql`](../../../../src/main/resources/db/migration/V16__sprint1_operation_status_schema.sql) | Created `OPRT_STTS_INFO` + `EXT_TRAN_CALL_HIS` |
| [`V17__sprint1_seed_data.sql`](../../../../src/main/resources/db/migration/V17__sprint1_seed_data.sql) | Seeded 5 demo rows for `BIZ_KEY = VN_VNM0004_000001` |

### Legacy JEX assets (read-only reference)

| Asset | Path |
|---|---|
| WSVC (read) | [`WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml) — verified inputs `PAGE_NO`/`PAGE_PER_CNT`/`SRCH_WD`/`COMPANYSTATUS`; outputs `TOT_CNT` + `REC[]` of `TAXCODE`/`COMPANYNAME`/`COMPANYSTATUS`/`OFFICEADD`/`SEARCHDATETIME`/`REPRESENTATIVEFIRSTNAME` |
| WSVC (page) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001.xml` |
| WSVC (update — out of scope) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_u001.xml` |
| WSVC (add-modal — out of scope) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_002*.xml` |
| Action JSP (read) | [`WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp) — verified `wb_get_operat_stts(...)` is **commented out** in legacy; `BIZ_KEY` is the only always-applied filter |
| IDO (paged read) | [`WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml) |
| IDO (count) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001_CNT.xml` |
| IDO (write — out of scope) | `_C001` / `_U001` / `_D001` |
| Legacy entity / DAO | `WECMS DEV/WECMS/src/wabooks/entity/OprtSttsInfo.java` + `dao/OprtSttsInfoDAO.java` |
| Legacy TS | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts` (carries the 12-string Vietnamese status legend; refactor collapses to 3 canonical states) |
| GDT integration (out of scope) | `nxisas_oprtstts_*` services + OSMC bridge — not ported. Note: the inline TODO in `OperationStatusService.java:21` references the OSMC service ID `TRCO_TRCO001_00003` for the future refresh path. |
| Live DEV URL | `https://dev.wecms.vn/aosv_0001_001.act` |

---

## Cross-references

- Feature spec: [../../features/addon-service/operation-status-inquiry.md](../../../features/addon-service/operation-status-inquiry.md)
- Implementation plan: [../../planning/addon/operation-status-inquiry-plan.md](../../../planning/addon/operation-status-inquiry-plan.md)
- Frontend page design: [../../frontend/addon/operation-status-inquiry-page.md](../../../../../wecms-web/docs/features/addon-service/operation-status-inquiry.md)
- Database design: [../../database/addon/operation-status-inquiry-db.md](../../database/addon/operation-status-inquiry-db.md)
- Service mapping: [../../migration/service-mapping.md](../../migration/service-mapping.md) → "Add-on Service > Operation Status Inquiry"
- Sister features that read the same `OPRT_STTS_INFO` data on their Buyer cell: [`addon/sales-e-invoices-api.md`](sales-e-invoices-api.md), [`addon/expenses-e-invoices-api.md`](expenses-e-invoices-api.md)
- Refactor backend rules: [wecms-api/CLAUDE.md](../../../../CLAUDE.md) — Excel export · View Setting · Common table sort · PROD Active Table List
