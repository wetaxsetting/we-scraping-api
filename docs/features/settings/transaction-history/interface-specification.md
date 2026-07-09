# ERP Transaction History — Interface Specification (Backend REST contract)

All endpoints under base path `/api/v1/settings/erp-transaction-history`. Wire format is lowercase
snake_case (global Jackson `SNAKE_CASE`); the list/thumbnail/export responses use the
`{ status, data, timestamp?, path?, trace_id? }` envelope; the download response is a raw binary stream.
Controller: [ErpTrnsHistoryController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java).

## Authorization (all endpoints)

```java
@PreAuthorize("@menuAuthz.canAccess('settings-erp-transaction-history')")   // class-level
```

Every endpoint requires a valid JWT **and** the `settings-erp-transaction-history` menu grant
(`USER_MENU_AUTH_INFM`; admins bypass the per-user ∩ but not the tenant's product tree). Missing/invalid
JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`. No per-row ACL — any user in the
tenant can list/download any row in that tenant.

---

## 1. List + four tab counts

```
GET /api/v1/settings/erp-transaction-history
    ?from_date=20260501&to_date=20260531        (both REQUIRED)
    &erp_type=W                                 (optional W/E/D)
    &trns_type_cd=09                            (optional "01".."17")
    &keyword=admin                              (optional; matches reg_id)
    &sort_field=REG_DTM&sort_direction=DESC     (optional; whitelisted FIELD_ID)
    &page=0&size=15                             (optional; 0-based; size clamped to 100, default 15)
```

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      {
        "seq_no": 9322,
        "org_file_nm": "Account_Inquiry_VND_31052026.xlsx",
        "save_file_nm": "20260531101500_a1b2c3d4_Account_Inquiry_VND_31052026.xlsx",
        "file_size": 18452,
        "erp_type": "W",
        "erp_type_key": "settings.erp_transaction_history.direction.wecms_to_erp",
        "trns_type_cd": "09",
        "trns_type_key": "settings.erp_transaction_history.type.09",
        "reg_dtm": "20260531101500",
        "reg_id": "admin",
        "user_nm": "Nguyen Van A"
      }
    ],
    "total_count": 759,
    "total_count_wecms": 533,
    "total_count_erp": 205,
    "total_count_deleted": 21,
    "page_number": 0,
    "page_size": 15,
    "total_pages": 51
  }
}
```

- The four counts come from one aggregate query that drops the `erp_type` predicate — all tabs reflect
  the same date / type / keyword scope.
- `keyword` matches `reg_id` only (legacy IDO parity); never `org_file_nm` / `save_file_nm`.
- `user_nm` is `null` for `SYSTEM` rows / removed users (frontend falls back to `reg_id`).
- errors: `WBE_VALIDATION` (bad date, period inverted, bad `erp_type` / `trns_type_cd`).

## 2. Download (binary)

```
GET /api/v1/settings/erp-transaction-history/{seqNo}/download
```

Success → HTTP 200, binary body:

```
Content-Type: application/octet-stream
Content-Disposition: attachment; filename*=UTF-8''<percent-encoded SAVE_FILE_NM>
Content-Length: <file_size>
X-Content-Type-Options: nosniff
Access-Control-Expose-Headers: Content-Disposition
```

The disposition filename is the legacy `SAVE_FILE_NM` (NAS name), **not** `ORG_FILE_NM` — intentional
audit-trail parity. Errors (JSON envelope):

```
WBE_VALIDATION   non-positive seqNo
WBE2014          row not found OR not in tenant (single envelope — never leak cross-tenant existence)
WBE2015          FILE_SIZE = 0 (batch row) OR physical file missing / unreadable on the storage root
```

## 3. Thumbnail (stub)

```
GET /api/v1/settings/erp-transaction-history/{seqNo}/thumbnail
→ 200, status.error = true, status.code = "WBE9001"   ("Endpoint is not yet implemented.")
```

Legacy `?cmd=getthumb` 640px JPEG path; deferred (no current frontend consumer). Fails loudly with
`WBE9001` rather than a misleading 404.

## 4. Excel export (XLSX)

```
POST /api/v1/settings/erp-transaction-history/export
Content-Type: application/json
{
  "screen_id": "erp-transaction-history",
  "dym_area_id": "default",
  "title": "ERP Transaction History",
  "file_name": "ERP_Transaction_History",
  "filters": { "fromDate": "20260501", "toDate": "20260531", "erpType": "W",
               "trnsTypeCd": "09", "keyword": "admin" },
  "sort": { "field": "REG_DTM", "direction": "DESC" },
  "columns": [ { "field_id": "REG_DTM", "header": "Date", "format": "datetime" }, … ]
}
→ 200, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
   Content-Disposition: attachment; filename*=UTF-8''ERP_Transaction_History.xlsx
```

Shared `common/export/` pipeline (see `wecms-api/CLAUDE.md` → "Excel export"). The provider
([ErpTrnsHistoryExportProvider](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/export/ErpTrnsHistoryExportProvider.java))
reuses `ErpTrnsHistoryService.listAll(...)` (same validation/filter/sort, unpaged), computes the
`N record(s)` subheader, and maps each row to a FIELD_ID-keyed map.

- Allowed FIELD_IDs: `REG_DTM`, `ERP_TYPE`, `TRNS_TYPE_CD`, `ORG_FILE_NM`, `FILE_SIZE`, `REG_USER`,
  `REG_ID`.
- `ERP_TYPE` / `TRNS_TYPE_CD` ship the **raw legacy code** in the cell (the wire's `*_key` i18n
  resolution applies to the JSON list DTO, not the spreadsheet); `FILE_SIZE` stays a raw `Long`
  (`format: "number"`) so Excel can re-sum.
- export errors: `WBE2010` (unknown screenId) · `WBE2011` (>50k rows) · `WBE2013` (no exportable
  columns after intersection — **the live seed-gap risk, GAP-4**).

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_VALIDATION` | 200/400 | bad date / period / `erp_type` / `trns_type_cd` / non-positive `seqNo` |
| `WBE2014` | 200 | Download — row not found OR not in tenant |
| `WBE2015` | 200 | Download — file unavailable (`FILE_SIZE = 0` or missing/unreadable) |
| `WBE9001` | 200 | Thumbnail not implemented (stub) |
| `WBE2010` / `WBE2011` / `WBE2013` | 200 | Export — unknown screenId / >50k rows / no exportable columns |
| `WBE1009` | 401 | JWT missing / invalid |
| `WBE1003` | 403 | Authenticated but lacks `settings-erp-transaction-history` |
| `WBE9999` | 500 | Unexpected server error |

`WBE2014` / `WBE2015` / `WBE9001` are present in
[messages.properties](../../../../src/main/resources/messages.properties) (lines 82/83/91).

---

## Per-feature i18n keys emitted on the wire

The list rows carry server-resolved i18n keys so the frontend renders via `t(key)` (the
`<field>_key` domain-enum pattern). Frontend falls back to the raw code when a key is `null`.

| Direction (`erp_type`) | Wire key (`erp_type_key`) |
|---|---|
| `W` | `settings.erp_transaction_history.direction.wecms_to_erp` |
| `E` | `settings.erp_transaction_history.direction.erp_to_wecms` |
| `D` | `settings.erp_transaction_history.direction.deleted` |

| Transaction type (`trns_type_cd`) | Wire key (`trns_type_key`) |
|---|---|
| `"01".."17"` | `settings.erp_transaction_history.type.<NN>` (e.g. `…type.09` → "Account Inquiry (VND)") |

The label catalogue for all 17 codes lives in [../transaction-history.md §3.3](../transaction-history.md)
and the locale files. Frontend consumption: [wecms-web/docs/features/settings/transaction-history/api-consumption.md](../../../../../wecms-web/docs/features/settings/transaction-history/api-consumption.md).
