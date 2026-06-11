# Contacts — Interface Specification (Backend REST contract)

All endpoints under base path `/api/v1/settings/contacts`. Wire format is lowercase snake_case
(global Jackson `SNAKE_CASE`); envelope is `{ status, data, timestamp?, path?, trace_id? }`.
Controller: [ContactsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/controller/ContactsController.java).

## Authorization (all endpoints)

```java
@PreAuthorize("@menuAuthz.canAccess('settings-core-contacts')")   // class-level
```

Every endpoint (read and write) requires a valid JWT **and** the `settings-core-contacts` menu grant.
Missing/invalid JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`. `@Validated`
enables the `{trcoKey}` `@NotBlank` path guard. Tenant scope comes from `TenantContext.currentBizKey()`
— `biz_key` is never on the wire.

---

## 1. List + search

```
GET /api/v1/settings/contacts?search=ACME&trco_type=L&page=0&size=15&sort_field=HAND_TRCO_KEY&sort_direction=ASC
→ data: ContactListResponse
```
Query params (all optional): `search`, `trco_type` (`L`/`O`), `page` (def 0), `size` (def 15, max 100),
`sort_field` (`HAND_TRCO_KEY` / `TRCO_CMNM` / `TRCO_TYPE` / `TRCO_TAX_ID`), `sort_direction`
(`ASC`/`DESC`).

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "rows": [
      {
        "trco_key": "9212", "hand_trco_key": "ACME01",
        "trco_cmnm": "ACME Co", "trco_cmenm": "ACME Co", "trco_tax_id": "0101234567",
        "trco_type": "L", "trco_stts": null, "mod_dtm": "20260513101500",
        "addresses": [ { "trco_addr_key": "1", "trco_bsun_post_adrs": "…", "disp_ord": 1, "issu_dt": "20260101", "due_dt": "99991231" } ],
        "accounts": [ { "trco_acct_key": "1", "acct_no": "00500044787814", "disp_ord": 1, "bank_nm": "Vietcombank", "napas_bank_cd": "01310001", "citad_bank_cd": null, "acct_ccy_cd": "VND", "finc_mda_owner_nm": "ACME CO", "owac_addr": null } ],
        "overseas_beneficiaries": []
      }
    ],
    "total_count": 30, "page": 0, "size": 15, "total_pages": 2
  }
}
```

## 2. Detail

```
GET /api/v1/settings/contacts/{trcoKey}
→ data: ContactRow (master + addresses[] + accounts[] + overseas_beneficiaries[])
errors: WBE_CONTACTS_NOT_FOUND (missing / trashed / wrong tenant)
```

## 3. Create

```
POST /api/v1/settings/contacts        (ContactCreateRequest)
{ "hand_trco_key": "ACME01", "trco_cmnm": "ACME Co", "trco_cmenm": "ACME Co",
  "trco_tax_id": "0101234567", "trco_type": "L",
  "addresses": [ { "op": "upsert", "trco_addr_key": "C…", "trco_bsun_post_adrs": "…", "disp_ord": 1 } ],
  "accounts":  [ { "op": "upsert", "trco_acct_key": "C…", "bank_nm": "Vietcombank", "acct_no": "00500…", "napas_bank_cd": "01310001", "acct_ccy_cd": "VND", "finc_mda_owner_nm": "ACME CO", "disp_ord": 1 } ],
  "overseas_beneficiaries": [] }
→ 200, data: ContactRow (post-write echo, generated trco_key)
errors: WBE_CONTACTS_HAND_KEY_DUP · WBE_CONTACTS_TAX_ID_DUP · WBE_CONTACTS_ACCT_DUP · WBE_CONTACTS_BENE_DUP · WBE_VALIDATION (bean / address disp_ord)
```

Child ops use a discriminated `op` field: `upsert` | `delete` (delete ignored on create). Address
`disp_ord ∈ {1,2}`. Account `op` carries `bank_nm`, `acct_no`, `napas_bank_cd`/`citad_bank_cd`,
`acct_ccy_cd`, `finc_mda_owner_nm`, `owac_addr`. Overseas `op` carries `bene_name`, `bene_acct_no`,
`bic_code`, `bene_bank_name`, `bene_bank_code`, `bene_addr`.

## 4. Update

```
PUT /api/v1/settings/contacts/{trcoKey}     (ContactUpdateRequest)
{ … same core … "trco_stts": null,
  "accounts": [ … upsert/delete ops … ], "accounts_to_delete": [ { "trco_acct_key": "1" } ],
  "overseas_beneficiaries": [ … ], "overseas_to_delete": [ { "trco_ovrs_key": "1" } ] }
→ 200, data: ContactRow (refreshed)
errors: WBE_CONTACTS_NOT_FOUND · WBE1190 (trco_stts→'C' while in use) · dup codes · WBE_VALIDATION
```

`trco_stts: "C"` trashes the contact (subject to the `WBE1190` guard). `accounts_to_delete` /
`overseas_to_delete` are folded into the per-collection delete pass alongside any `op:"delete"` rows.

## 5. Soft-delete (single)

```
DELETE /api/v1/settings/contacts/{trcoKey}
→ 200, data: null   (sets TRCO_STTS='C')
errors: WBE_CONTACTS_NOT_FOUND · WBE1190 (referenced by an active transaction)
```

## 6. Bulk soft-delete

```
POST /api/v1/settings/contacts/bulk-delete       { "trco_keys": ["9212","9213"] }   (@NotEmpty @Size 100)
→ 200, data: null
errors: WBE_CONTACTS_NOT_FOUND (any key) · WBE1190 (first in-use key rolls back the batch) · WBE_VALIDATION (empty list)
```

## 7. Export (GAP-01)

```
POST /api/v1/settings/contacts/export     (ExcelExportRequest)
{ "screen_id": "settings-contacts-local", "dym_area_id": "default",
  "file_name": "Local Contacts", "filters": { "keyword": "ACME" }, "columns": [ … ] }
→ 200, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (XLSX stream)
   Content-Disposition: attachment; filename*=UTF-8''Local%20Contacts.xlsx
errors: WBE2010 (unknown screenId) · WBE2011 (>50k rows) · WBE2013 (no exportable columns)
```
`screen_id` is `settings-contacts-local` (9 cols) or `settings-contacts-overseas` (11 cols). Columns +
order in [refactor-gap-analysis.md § Export](refactor-gap-analysis.md) and the V247 seed.

## 8. Excel import (three calls)

```
POST /excel/preflight              { "rows": [ { "hand_trco_key": "ACME01", "trco_tax_id": "0101…" } ] }   (≤500)
→ data: { "results": [ { "row_index": 0, "hand_trco_key_exists": true, "trco_tax_id_exists": false } ] }

POST /excel/resolve-recipients     { "rows": [ { "row_index": 0, "bank_name": "Vietcombank", "acct_no": "005…", "currency": "VND" } ] }   (≤500)
→ data: { "results": [ { "row_index": 0, "bank_found": true, "ant_div_cd": "NAPAS", "napas_bank_cd": "01310001", "citad_bank_cd": null, "finc_mda_owner_nm": "ACME CO" } ] }

POST /excel/import                 { "contacts": [ ContactCreateRequest, … ] }   (@NotEmpty @Size 500)
→ 200, data: { "total": 10, "imported": 9, "failed": 1, "errors": [ { "index": 4, "hand_trco_key": "X", "code": "WBE_CONTACTS_HAND_KEY_DUP", "message": "…" } ] }
```
Import is always HTTP 200 — per-row partial success; a failing row records a `ContactImportError` and
rolls back only itself.

## 9. Supporting endpoints

```
POST /by-codes        { "codes": ["ACME01","BETA02"] }  (@NotEmpty @Size 500)  → data: ContactRow[] (unmatched silently omitted)
GET  /banks                                              → data: { "rows": ContactBankRow[] }  (NAPAS + CITAD, global)
POST /lookup-recipient { "napas_bank_cd":"…", "citad_bank_cd":"…", "acct_no":"…" } → data: { "recipient_name": "…" }  (stub)
```

## 10. Attachments

```
GET    /{trcoKey}/files                       → data: { "rows": ContactFileRow[] }
POST   /{trcoKey}/files   (multipart "file")  → data: ContactFileRow   errors: WBE_CONTACTS_FILE_TOO_LARGE / _TYPE_NOT_ALLOWED / _EMPTY / _NAME_INVALID / _STORAGE_FAILED
GET    /{trcoKey}/files/{fileId}              → application/octet-stream (Content-Disposition attachment, UTF-8 filename, X-Content-Type-Options: nosniff)   errors: WBE_CONTACTS_FILE_NOT_FOUND / _FILE_UNAVAILABLE
DELETE /{trcoKey}/files/{fileId}              → 200, data: null   errors: WBE_CONTACTS_FILE_NOT_FOUND
```
`ContactFileRow`: `file_id`, `org_file_nm`, `file_size`, `reg_dtm`, `reg_nm`, `trco_infm_titl`
(`save_file_nm` deliberately not exposed).

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_CONTACTS_NOT_FOUND` | 200 | Contact not found in tenant (merges not-found + wrong-tenant + trashed) |
| `WBE_CONTACTS_HAND_KEY_DUP` | 200 | Duplicate `hand_trco_key` in the tenant |
| `WBE_CONTACTS_TAX_ID_DUP` | 200 | Duplicate non-blank `trco_tax_id` in the tenant |
| `WBE_CONTACTS_ACCT_DUP` | 200 | Duplicate `bank_nm|acct_no` within the account set |
| `WBE_CONTACTS_BENE_DUP` | 200 | Duplicate `bene_acct_no|bic_code` within the overseas set |
| `WBE1190` | 200 | Delete / trash blocked — contact referenced by an active transaction |
| `WBE_CONTACTS_FILE_NAME_INVALID` | 200 | Attachment filename invalid |
| `WBE_CONTACTS_FILE_EMPTY` | 200 | Empty attachment |
| `WBE_CONTACTS_FILE_TOO_LARGE` | 200 | Attachment exceeds 10 MB |
| `WBE_CONTACTS_FILE_TYPE_NOT_ALLOWED` | 200 | Extension not in the allow-list |
| `WBE_CONTACTS_FILE_STORAGE_FAILED` | 200 | Disk / DB write failure |
| `WBE_CONTACTS_FILE_NOT_FOUND` | 200 | Attachment row not found in tenant |
| `WBE_CONTACTS_FILE_UNAVAILABLE` | 200 | Attachment file missing/unreadable on storage root |
| `WBE2010` | 200 | Export screenId unknown |
| `WBE2011` | 200 | Export exceeds 50,000-row cap |
| `WBE2013` | 200 | Export has no exportable columns after View Setting intersection |
| `WBE_VALIDATION` | 200/400 | Bean validation, address disp_ord, empty bulk/import list |
| `WBE9999` | 500/200 | Unexpected server error (also the per-row import catch-all code) |
| `WBE1009` | 401 | JWT missing / invalid |
| `WBE1003` | 403 | Authenticated but lacks `settings-core-contacts` |

Frontend consumption: [wecms-web/docs/features/settings/contacts/api-consumption.md](../../../../../wecms-web/docs/features/settings/contacts/api-consumption.md).
DTO field-by-field inventory: [source-code-index.md § DTOs](source-code-index.md#dtos).
