# Operation Status Inquiry — Backend Functional Specification

Business rules implemented by `com.kosign.wecms.domain.operation`. Source of truth is the code; JEX (`aosv_0001_001` / `aosv_0001_002`) is the functional reference.

---

## 1. Purpose & scope

- **In scope (implemented):** read-only listing of `OPRT_STTS_INFO` for the current tenant; single Tax ID GDT lookup ("Inquire"); persist a looked-up row ("Save"); bulk re-poll + upsert ("Update"); XLSX export.
- **Tenant isolation:** every operation is scoped to `TenantContext.currentBizKey()`. The wire never accepts `BIZ_KEY`; an unauthenticated request is rejected with 401 before the controller runs.
- **Out of scope:** UI redesign; per-row delete (legacy `aosv_0001_002_d001` IDO exists but is not exposed); per-row audit-history surfacing; localisation of the raw GDT status string (the legacy `wb_get_operat_stts` helper is commented out and not ported).

---

## 2. Operating-status definition

`COMPANYSTATUS` is **GDT-supplied free-form Vietnamese text** — not a controlled enum. It is whatever string the last successful GDT poll returned. The refactor derives a compact 3-state code on read (response only — never persisted) via `OperationStatusService.resolveStatusCd(...)`:

| Raw `COMPANYSTATUS` contains (lower-cased) | Derived `status_cd` |
|---|---|
| `đang hoạt động` | `active_certificate_issued` |
| `ngừng hoạt động` | `suspended` |
| `tạm ngừng` | `suspended` |
| `tạm nghỉ` | `suspended` |
| null / anything else | `not_registered` |

`not_registered` is a **defensive fallback**; if it fires on real data the match list needs extending. The wire carries **both** the raw `company_status` and the derived `status_cd` so the frontend can tighten display states without a wire migration. Export deliberately emits the **raw** string (preserves the full GDT message).

Observed DEV strings (audit 2026-05-04, tenant `VN_VNM0004_000001`, 6 distinct values across 202 rows) are listed in the `resolveStatusCd` Javadoc.

---

## 3. List — filters, sort, pagination

### Filters
| Wire param | Behaviour | Legacy parity |
|---|---|---|
| `q` | OR partial-match (case-insensitive) on `COMPANYNAME` and `TAXCODE`. Fixed 2-column whitelist. | legacy `SRCH_WD` (`aosv_0001_001_r001_act.jsp:54-61`) |
| `companyStatus` | Partial match (`ILIKE '%X%'`) on the raw Vietnamese `COMPANYSTATUS`. Caller sends a raw substring (e.g. `đang hoạt động` / `ngừng` / `tạm`); display labels are localised on the frontend. | legacy `COMPANYSTATUS` (`:47-52`) |

Both patterns are built by `buildLikePattern(...)`: trim → return null if blank (skips the predicate) → escape `%`/`_`/`!` with `ESCAPE '!'` → wrap `%…%` → lower-case. JPQL uses `(:param IS NULL OR LOWER(COALESCE(col,'')) LIKE :param ESCAPE '!')`.

### Pagination
- `page` is **zero-based** (Spring Data). `size` defaults to `DEFAULT_PAGE_SIZE = 10` when `<= 0` and is clamped to `MAX_PAGE_SIZE = 100`.
- Response carries `total_count`, `page_number`, `page_size` (post-clamp), `total_pages`.
- Export uses `Pageable.unpaged()` — the full filtered set in one round trip.

### Sort (common-sort contract)
- Query params `sortField` (View Setting `FIELD_ID`) + `sortDirection` (`ASC`/`DESC`).
- Whitelist `OperationStatusSortField`: `COMPANY→companyname`, `ADDRESS→officeadd`, `OPERATION_STATUS→companystatus`, `LAST_UPDATE→searchdatetime`. `parse()` returns `null` for unknown/blank → silent fallback to default ORDER BY (never echoes user input into SQL, never 4xx).
- **Default ORDER BY:** `SEARCHDATETIME DESC, TAXCODE ASC`. A user sort is always followed by the default as a deterministic tiebreaker.
- `CHECKBOX` is a special FIELD_ID and intentionally **not** in the whitelist — never sortable.

---

## 4. Inquire (single Tax ID lookup)

- Input `tax_code` validated `^\d{10}(-\d{3})?$` (10 digits, optional `-NNN` branch).
- Calls the live OSMC → NXISAS → GDT bridge (`OsmcGdtClient.lookup`).
- Writes one `EXT_TRAN_CALL_HIS` + one `NXSIAS_MODULE_TRAN_LOG_HIS` audit row.
- Bridge failure → `WBE_OSMC_LOOKUP_FAILED`; empty registration detail → `WBE_OSMC_NO_RESULT` (legacy `WBE1152` parity).
- Returns the matched registration (tax code, company name, raw status + derived `status_cd`, office address, representative first name, GDT "as-of" `search_datetime`). Does not persist.

---

## 5. Save (create)

- Required fields (bean validation on `CreateOperationStatusRequest`): `tax_code` (regex), `company_status`, `office_add`, `search_datetime` (`^\d{14}$`). `company_nm` and `representative_first_name` are optional (legacy allows blank representative; GDT returns it blank for some ceased branches).
- **Duplicate guard:** `existsById((BIZ_KEY, TAXCODE))` → `WBE_OPERATION_STATUS_DUPLICATE` (legacy `WBE0003`). Concurrent-insert race (`DataIntegrityViolationException`) maps to the same error — never a 500.
- Create-only; the bulk Update path owns the upsert/refresh flow.
- `representative_first_name` null coalesces to `""` (column NOT NULL). Audit stamped from the JWT-bound user.

---

## 6. Update (bulk re-poll)

- Optional `tax_codes`: omitted/empty → re-poll the whole tenant cache; non-empty → only those codes that exist in the tenant cache (unknown / cross-tenant codes silently dropped — a stale frontend selection cannot cross the tenant boundary).
- Empty cache or empty working set → `{0,0,0}` no-op.
- Whole-batch bridge failure → `WBE_OSMC_LOOKUP_FAILED` (cache untouched). Per-row failures are counted, not fatal.
- Returns `{ total_attempted, updated, failed }`.

---

## 7. Error codes

| Code | Meaning |
|---|---|
| `WBE_OPERATION_STATUS_DUPLICATE` | Save hit an existing `(BIZ_KEY, TAXCODE)` (legacy `WBE0003`) |
| `WBE_OSMC_LOOKUP_FAILED` | GDT bridge call failed (Inquire single / Update batch) |
| `WBE_OSMC_NO_RESULT` | GDT returned no registration detail for the tax code (legacy `WBE1152`) |
| `WBE_VALIDATION` | Bean-validation failure (bad tax-code/date format, missing required field) |
| `WBE9999` | Unexpected server fault (catch-all) |

All business outcomes return HTTP 200 with `status.error = true` per the global envelope; the frontend reads `status.error` / `status.code`. Add new codes to `messages.properties` before throwing.

---

## 8. Product-type & permissions

- No `@PreAuthorize` gate on these endpoints today — any authenticated tenant user (matches live JEX, which has no role gate on `aosv_0001_*`).
- No product-type (`PRDT_TYPE`) branching on this feature.
