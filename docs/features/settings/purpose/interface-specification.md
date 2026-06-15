# Purpose — Interface Specification (Backend REST contract)

All endpoints under base path `/api/v1/settings/purpose-settings`. Wire format is lowercase
snake_case (global Jackson `SNAKE_CASE`); envelope is `{ status, data, timestamp?, path?, trace_id? }`.
Controller: [PurposeSettingsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsController.java).

## Authorization (all endpoints)

```java
@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")   // class-level
```

Every endpoint (read and write) requires a valid JWT **and** the `settings-core-purpose` menu grant
(`USER_MENU_AUTH_INFM`; admins bypass the per-user ∩ but not the tenant's product tree). Missing/invalid
JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`. (The class Javadoc claiming "no
`@PreAuthorize`" is stale — see [refactor-gap-analysis.md](refactor-gap-analysis.md).)

`@Validated` enables path/query guards: `use_usag_cd` → `^[A-Za-z0-9]{1,20}$`; `keyword` → printable Unicode, ≤100.

---

## 1. List + search

```
GET /api/v1/settings/purpose-settings?keyword=Operating
```
`keyword` optional (case-insensitive substring on `use_usag_nm`).

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      {
        "use_usag_cd": "0000000001",
        "hand_use_usag_cd": "BD01",
        "use_usag_nm": "Operating Expenses",
        "use_usag_cont": "Day-to-day operating spend",
        "parent_use_usag_cd": null,
        "disp_yn": "Y",
        "disp_ord": 1,
        "used_in_transactions": true,
        "mod_id": "admin",
        "mod_dtm": "20260512101500",
        "children": [
          { "use_usag_cd": "0000000002", "hand_use_usag_cd": "BD01-01",
            "use_usag_nm": "Utilities", "parent_use_usag_cd": "0000000001",
            "disp_yn": "Y", "disp_ord": 1, "used_in_transactions": false,
            "mod_id": "admin", "mod_dtm": "20260512101500", "children": null }
        ]
      }
    ]
  }
}
```

## 2. Detail

```
GET /api/v1/settings/purpose-settings/{use_usag_cd}
→ data: PurposeSettingDetail (same fields as a row, flat, no children) | 404 if not in tenant
```

## 3. Create

```
POST /api/v1/settings/purpose-settings
{ "hand_use_usag_cd": "BD02", "use_usag_nm": "Capital Expenditure",
  "use_usag_cont": "CapEx", "parent_use_usag_cd": null, "disp_yn": "Y" }
→ 200, data: PurposeSettingDetail (post-write echo, incl. generated use_usag_cd + disp_ord)
errors: WBW1152 (dup code) · WBW1138 (dup name) · WBE_VALIDATION (bean / parent rules)
```

## 4. Update

```
PUT /api/v1/settings/purpose-settings/{use_usag_cd}
{ "hand_use_usag_cd": "BD02", "use_usag_nm": "Capital Expenditure (Renamed)",
  "use_usag_cont": "...", "parent_use_usag_cd": "0000000001", "disp_yn": "Y" }
→ 200, data: PurposeSettingDetail (refreshed)
errors: WBW1138 (dup name excl. self) · WBE_VALIDATION (parent invalid / self / demote-with-children) · 404
```

## 5. Soft-delete

```
DELETE /api/v1/settings/purpose-settings/{use_usag_cd}
→ 200, data: null    (sets USE_YN='N')
errors: WBE1199 (active children OR row/child referenced by an active transaction) · 404
```

## 6. Reorder

```
PUT /api/v1/settings/purpose-settings/order
{ "items": [ { "use_usag_cd": "0000000001", "disp_ord": 1 },
             { "use_usag_cd": "0000000005", "disp_ord": 2 } ] }
→ 200, data: PurposeSettingListResponse (refreshed tree)
errors: WBE_VALIDATION (duplicate code / duplicate order / unknown code)
```

## 7a. Excel preview

```
POST /api/v1/settings/purpose-settings/excel/preview        Content-Type: multipart/form-data
  part "file" = the user's .xlsx
→ 200, data: PurposeExcelPreview
{
  "rows": [
    { "row_index": 4, "hand_use_usag_cd": "BD02", "use_usag_nm": "...",
      "use_usag_cont": "...", "parent_use_usag_cd": null, "valid": true, "errors": [] },
    { "row_index": 5, "hand_use_usag_cd": "", "use_usag_nm": "X", "use_usag_cont": "",
      "parent_use_usag_cd": "NOPE", "valid": false,
      "errors": [
        { "field": "hand_use_usag_cd", "code": "required", "message": "Purpose Code is required." },
        { "field": "parent_use_usag_cd", "code": "parent_not_found", "message": "Parent not found." }
      ] }
  ],
  "total_count": 2, "completed_count": 1, "error_count": 1
}
errors: WBE2041 (>500 rows) · WBE_VALIDATION (empty file) + corrupt/header-mismatch file-level codes
per-cell error codes: required · length · dup_file · dup_db · parent_not_found · parent_not_root
```

## 7b. Excel import

```
POST /api/v1/settings/purpose-settings/excel/import         Content-Type: application/json
{ "rows": [ { "hand_use_usag_cd": "BD02", "use_usag_nm": "...",
              "use_usag_cont": "...", "parent_use_usag_cd": null } ] }
→ 200, data: { "inserted_count": 12 }
- re-validates against live DB (preview 'valid' not trusted); roots-first append; atomic
errors: WBE_VALIDATION (any row fails → whole batch rolls back)
```

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBW1152` | 200 | Duplicate `hand_use_usag_cd` (Create) |
| `WBW1138` | 200 | Duplicate `use_usag_nm` (Create + Update excl. self) |
| `WBE1199` | 200 | Delete blocked — active children OR row/child in use (recursive) |
| `WBE2041` | 200 | Excel preview exceeds 500-row cap |
| `WBE_VALIDATION` | 200/400 | Bean validation, parent rules, reorder dup, Excel row failure |
| `WBE1009` | 401 | JWT missing / invalid |
| `WBE1003` | 403 | Authenticated but lacks `settings-core-purpose` |
| `WBE9999` | 500 | Unexpected server error |

Per OQ-8 no new `WBE_PURPOSE_*` codes were introduced. Frontend consumption of these endpoints:
[wecms-web/docs/features/settings/purpose/api-consumption.md](../../../../../wecms-web/docs/features/settings/purpose/api-consumption.md).
