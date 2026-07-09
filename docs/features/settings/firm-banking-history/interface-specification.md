# Firm Banking History — Interface Specification (REST)

The HTTP contract for the list + export endpoints. Parent: [README.md](README.md).
Legacy `.jct` contract (read-only): [docs/jex/.../interface-specification.md](../../../../../docs/jex/features/firm-banking-history/interface-specification.md).
Frontend consumption: [api-consumption.md](../../../../../wecms-web/docs/features/settings/firm-banking-history/api-consumption.md).

Envelope + naming conventions are the project-global `{ status, data }` / lowercase snake_case rules
(wecms-api/CLAUDE.md). This feature introduces **no new error code**.

---

## 1. List API

```
GET /api/v1/settings/firm-banking-history
Authorization: Bearer <jwt>
Accept-Language: en | ko | vi | km
```

### Query parameters

| Wire param | Java param | Required | Default | Notes |
|---|---|---|---|---|
| `from_date` | `fromDate` | ✅ | — | `YYYYMMDD` |
| `to_date` | `toDate` | ✅ | — | `YYYYMMDD`, `from ≤ to` |
| `svc_type` | `svcType` | — | (none) | `F` / `S` / `G` / `B` |
| `keyword` | `keyword` | — | (none) | partial match on `seq_no` (ILIKE, escaped) |
| `sort_field` | `sortField` | — | `tarn_dtm` | whitelist below |
| `sort_direction` | `sortDirection` | — | `desc` | `asc` / `desc` |
| `page` | `page` | — | `0` | zero-based |
| `size` | `size` | — | `15` | clamped to `100` |

Sort whitelist: `tarn_dtm`, `svc_type`, `svc_nm`, `seq_no`, `dep_bank_cd`, `rslt_cd`. Anything else →
silent fallback to the default sort.

### Success response (200)

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      {
        "tarn_dtm":     "20260507143015",
        "svc_type":     "F",
        "svc_type_key": "settings.firm_banking_history.service_type.firm_banking_transaction",
        "svc_nm":       "201",
        "svc_name_key": "fb_history.service.201",
        "seq_no":       "0000000123",
        "dep_bank_cd":  "Vietcombank - 970436",
        "rslt_cd":      "00000000",
        "rslt_msg":     "00000000",
        "job":          "201",
        "req_data":     null,
        "van_org_cd":   "FB"
      }
    ],
    "total_count":  42,
    "page_number":  0,
    "page_size":    15,
    "total_pages":  3
  }
}
```

| Row field | Meaning |
|---|---|
| `tarn_dtm` | call timestamp `YYYYMMDDHHMMSS` (VN-local) |
| `svc_type` | leg discriminator `F`/`S`/`G`/`B` |
| `svc_type_key` | server-resolved i18n key for the leg; `null` for an unknown letter |
| `svc_nm` | raw service value (F: `TRAN_TP` code; S/G/B: English literal) |
| `svc_name_key` | server-resolved i18n key (`fb_history.service.*`); `null` when the `(svc_type, svc_nm)` pair is unrecognised |
| `seq_no` | per-leg sequence id (keyword-search target) |
| `dep_bank_cd` | resolved "`<bank name> - <code>`" / institution module |
| `rslt_cd` | per-leg result code |
| `rslt_msg` | per-leg result message (F leg re-emits `rslt_cd`; resolved client-side) |
| `job` | per-leg job identifier |
| `req_data` | request payload JSON (G/B legs); `null` otherwise |
| `van_org_cd` | VAN org code (F leg); `null` otherwise |

### Error responses

All errors are HTTP 200 business envelopes (except framework faults) carrying `timestamp`, `path`,
`trace_id`.

| Cause | HTTP | Code | Legacy citation |
|---|---|---|---|
| `from_date` / `to_date` missing | 200 | `WBE_VALIDATION` | `comm_0221_001_r001_act.jsp:30-31` |
| `from_date` / `to_date` not valid `YYYYMMDD` | 200 | `WBE_VALIDATION` | `comm_0221_001_r001_act.jsp:33-34` |
| `from_date > to_date` | 200 | `WBE_VALIDATION` | `comm_0221_001_r001_act.jsp:36` (legacy client also checks → `WBE1000`; refactor keeps the server check authoritative) |
| `svc_type ∉ {F,S,G,B}` | 200 | `WBE_VALIDATION` | new explicit validation (legacy back-end did not enforce) |
| missing required `@RequestParam` | 200 | `WBE_VALIDATION` | `MissingServletRequestParameterException` |
| DB / function failure | 500 | `WBE_DB` | `DataAccessException` |
| anything unexpected | 500 | `WBE9999` | catch-all, logged with `trace_id` |

---

## 2. Authority

Class-level `@PreAuthorize("hasAnyRole('ADMIN','USER') and @menuAuthz.canAccess('settings-firm-banking-history')")`
on the controller (honoured via `@EnableMethodSecurity` in `SecurityConfig`). Both endpoints inherit it.
The role term maps the legacy `NORM_USER_ONLY_YN=Y` / `WTA_USER_ALLOW_YN=N` flags; the
`@menuAuthz.canAccess('settings-firm-banking-history')` term is the F-3 per-menu authorization gate
(stacked, not weakening the role check). See [README § Quick facts](README.md#quick-facts).

---

## 3. Tenant scope

`TenantContext.currentBizKey()` (from the signed JWT) is applied to every leg of the view inside the
service. A request body / query param can never widen or change the tenant.

---

## 4. Export contract

```
POST /api/v1/settings/firm-banking-history/export
Authorization: Bearer <jwt>
Content-Type: application/json
→ 200, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
→ Content-Disposition: attachment; filename*=UTF-8''<percent-encoded>.xlsx
```

> **Refactor-only addition.** The legacy `comm_0221_001` screen has **no** Excel export. The
> refactor adds one via the shared `common/export/` pipeline. There is no legacy parity baseline to
> match here — it is a net-new capability.

Body is the shared [`ExcelExportRequest`](../../../../src/main/java/com/kosign/wecms/common/export/ExcelExportRequest.java):

| Field | Value for this screen |
|---|---|
| `screen_id` | `"firm-banking-history"` |
| `dym_area_id` | `"default"` |
| `title` | on-screen page title (sheet row 0) |
| `file_name` | filename base (no extension; controller appends `.xlsx`) |
| `filters` | `{ from_date, to_date, svc_type?, keyword? }` |
| `sort` | `{ field, direction }` |
| `columns` | column visibility/order hint — intersected server-side with the allow-list + View Setting |
| `sub_header` | informational fallback; backend computes the canonical `"N record(s)"` |

Provider: [`FirmBankingHistoryExportProvider`](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/export/FirmBankingHistoryExportProvider.java).
- `screenId()` → `"firm-banking-history"`.
- `allowedFieldIds()` → `{ REG_DTM, SVC_TYPE, SVC_NM, SEQ_NO, DEP_BANK_CD, RS_CD, RS_MSG }`.
- `fetch(...)` re-runs `FirmBankingHistoryService.listAll(...)` (same filter/sort path, unpaged) and maps each row to a FIELD_ID-keyed map (`REG_DTM←tarn_dtm`, `RS_CD←rslt_cd`, `RS_MSG←rslt_msg`); subheader `"N record(s)"`.
- Capped at `MAX_ROWS = 50,000` by `ExcelExportService`. This screen is **export-only with no View Setting seed**, so there is no persisted visibility to intersect: `ScreenOptionService.load('firm-banking-history', …)` throws `WBE2001`, which `ExcelExportService.resolveColumns` catches — the emitted column set is then **request columns ∩ the provider allow-list above**.

Export-related error codes (from the shared pipeline): `WBE2010` (unknown screenId), `WBE2011`
(> 50k rows), `WBE2013` (no exportable columns after intersection).

---

## 5. No detail endpoint

There is **no** `GET /{id}` detail endpoint. The View Details popover renders entirely from fields
already present in the list row (`rslt_cd`, `rslt_msg`, `job`, `req_data`, `van_org_cd`), so it
requires no second HTTP round-trip — matching legacy behaviour.
