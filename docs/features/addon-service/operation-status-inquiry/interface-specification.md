# Operation Status Inquiry — Interface Specification (REST)

All endpoints under `@RequestMapping("/api/v1/addon")` in [OperationStatusController.java](../../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java). Wire JSON is **lowercase snake_case** (global Jackson `SNAKE_CASE`). Every response uses the standard envelope `{ status: { error, code, message }, data }` (errors add `timestamp`, `path`, `trace_id`). Auth: JWT Bearer on every endpoint; tenant resolved server-side from the token. Full contract detail also in [../../../references/api-spec/addon/operation-status-inquiry-api.md](../../../references/api-spec/addon/operation-status-inquiry-api.md).

---

## 1. `GET /api/v1/addon/operation-status` — list

**Query params** (all optional):

| Param | Type | Notes |
|---|---|---|
| `q` | string | keyword — OR partial match on company name + tax code |
| `companyStatus` | string | raw Vietnamese status substring (partial match) |
| `sortField` | string | View Setting FIELD_ID: `COMPANY` / `ADDRESS` / `OPERATION_STATUS` / `LAST_UPDATE` |
| `sortDirection` | string | `ASC` / `DESC` |
| `page` | int | zero-based; default `0` |
| `size` | int | default `10`, clamped to `100` |

**Response `data` — `OperationStatusListResponse`:**

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "rows": [
      {
        "taxcode": "0301234567",
        "company_nm": "CÔNG TY TNHH ABC",
        "company_status": "NNT đang hoạt động (đã được cấp GCN ĐKT)",
        "status_cd": "active_certificate_issued",
        "office_add": "12 Nguyễn Huệ, Q.1, TP.HCM",
        "last_update": "20260601093000"
      }
    ],
    "total_count": 202,
    "page_number": 0,
    "page_size": 10,
    "total_pages": 21
  }
}
```

Row shape = `OperationStatusRow`: `taxcode`, `company_nm`, `company_status` (raw), `status_cd` (derived 3-state), `office_add`, `last_update` (`yyyyMMddHHmmss`).

---

## 2. `POST /api/v1/addon/operation-status/inquire` — Get Business Info

**Request — `InquireOperationStatusRequest`:**
```json
{ "tax_code": "0301234567" }
```
`tax_code` — required, `^\d{10}(-\d{3})?$`.

**Response `data` — `InquireOperationStatusResponse`:**
```json
{
  "tax_code": "0301234567",
  "company_nm": "CÔNG TY TNHH ABC",
  "company_status": "NNT đang hoạt động (đã được cấp GCN ĐKT)",
  "status_cd": "active_certificate_issued",
  "office_add": "12 Nguyễn Huệ, Q.1, TP.HCM",
  "representative_first_name": "Nguyễn Văn A",
  "search_datetime": "20260604101500"
}
```

**Errors:** `WBE_OSMC_LOOKUP_FAILED` (bridge failed), `WBE_OSMC_NO_RESULT` (no registration detail), `WBE_VALIDATION` (bad tax code).

---

## 3. `POST /api/v1/addon/operation-status` — Save (create)

**Request — `CreateOperationStatusRequest`:**
```json
{
  "tax_code": "0301234567",
  "company_nm": "CÔNG TY TNHH ABC",
  "company_status": "NNT đang hoạt động (đã được cấp GCN ĐKT)",
  "office_add": "12 Nguyễn Huệ, Q.1, TP.HCM",
  "search_datetime": "20260604101500",
  "representative_first_name": "Nguyễn Văn A"
}
```

| Field | Required | Validation |
|---|---|---|
| `tax_code` | ✅ | `^\d{10}(-\d{3})?$` |
| `company_nm` | — | (optional) |
| `company_status` | ✅ | `@NotBlank` |
| `office_add` | ✅ | `@NotBlank` |
| `search_datetime` | ✅ | `^\d{14}$` |
| `representative_first_name` | — | optional; null → `""` server-side |

**Response `data`:** the persisted `OperationStatusRow` (same shape as a list row).

**Errors:** `WBE_OPERATION_STATUS_DUPLICATE` (existing `(BIZ_KEY, TAXCODE)` — also on concurrent-insert race), `WBE_VALIDATION`.

---

## 4. `POST /api/v1/addon/operation-status/update` — bulk re-poll

**Request — `UpdateOperationStatusRequest` (body optional):**
```json
{ "tax_codes": ["0301234567", "0309876543"] }
```
Omit `tax_codes` (or send `{}`) to re-poll the entire tenant cache; non-empty → only those codes (unknown/cross-tenant codes silently dropped).

**Response `data` — `UpdateOperationStatusResponse`:**
```json
{ "total_attempted": 2, "updated": 2, "failed": 0 }
```

**Errors:** `WBE_OSMC_LOOKUP_FAILED` (whole-batch bridge failure; cache untouched). Per-row failures are counted in `failed`, not thrown.

---

## 5. `POST /api/v1/addon/operation-status/export` — XLSX

**Request — shared `ExcelExportRequest`:** carries `screen_id` (`addon-operation-status`), `dym_area_id` (`default`), `file_name`, `title`, `columns[]`, `filters` (`q` / `companyStatus`), `sort` (`field` / `direction`).

**Response:** HTTP 200, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (XLSX binary), `Content-Disposition: attachment; filename*=UTF-8''…`. Allowed columns: `COMPANY`, `ADDRESS`, `OPERATION_STATUS` (raw Vietnamese), `LAST_UPDATE`. Server-computed subheader `"<n> compan(y|ies)  ·  As of <YYYY-MM-DD>"`.

**Errors:** `WBE2010` (unknown screenId), `WBE2011` (>50k rows), `WBE2013` (no exportable columns after intersection).

---

## Common rules

- Business outcomes return **HTTP 200** with `status.error = true`; clients read `status.error` / `status.code`, not the HTTP status.
- `BIZ_KEY` is **never** accepted on the wire (path, query, or body) — always from the JWT.
- Sort `sortField` resolves through `OperationStatusSortField`; unknown/blank → default ORDER BY (`SEARCHDATETIME DESC, TAXCODE ASC`), never a 4xx.
