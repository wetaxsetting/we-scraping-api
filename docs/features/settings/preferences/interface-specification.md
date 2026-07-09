# Preferences — Interface Specification (Backend REST contract)

All endpoints under base path `/api/v1/settings/preferences`. Wire format is lowercase snake_case
(global Jackson `SNAKE_CASE`); JSON envelope is `{ status, data, timestamp?, path?, trace_id? }`. The
logo **stream** endpoint returns binary, not the JSON envelope.
Controller: [PreferencesController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/controller/PreferencesController.java).

## Authorization (all endpoints)

```java
@PreAuthorize("@menuAuthz.canAccess('settings-core-preference')")   // class-level
```

Every endpoint (read and write) requires a valid JWT **and** the `settings-core-preference` menu grant
(`USER_MENU_AUTH_INFM`; admins bypass the per-user ∩ but not the tenant's product tree). Missing/invalid
JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`.

Tenant scope rides on the JWT (`TenantContext.currentBizKey()`); no endpoint carries a `biz_key` path or
query param.

---

## 1. Read profile

```
GET /api/v1/settings/preferences
```

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "vietnamese_name": "CÔNG TY TNHH WEBCASH VIỆT NAM",
    "display_name": "Webcash Vietnam",
    "tax_id": "0312345678",
    "representative_name": "Nguyen Van A",
    "representative_email": "admin@webcashvietnam.com",
    "tel_no": "+84 28 1234 5678",
    "fax_no": null,
    "address": {
      "post_address": "Lầu 5, Toà nhà ABC",
      "detail_address": "Số 1 Đường XYZ",
      "city": "Hồ Chí Minh",
      "state": "Quận 1"
    },
    "logo_url": "/api/v1/settings/preferences/logo?v=Qm9neF8xMjM.png",
    "modified_by": "admin",
    "modified_at": "2026-06-12T10:15:00+07:00"
  }
}
```

`logo_url` is `null` when the tenant has no logo set.

## 2. Save profile

```
PUT /api/v1/settings/preferences
{ "vietnamese_name": "CÔNG TY TNHH WEBCASH VIỆT NAM",
  "display_name": "Webcash Vietnam",
  "tax_id": "0312345678",
  "representative_name": "Nguyen Van A",
  "representative_email": "admin@webcashvietnam.com",
  "tel_no": "+84 28 1234 5678",
  "fax_no": null,
  "address": { "post_address": "Lầu 5, Toà nhà ABC", "detail_address": "Số 1 Đường XYZ",
               "city": "Hồ Chí Minh", "state": "Quận 1" } }
→ 200, data: PreferencesResponse (refreshed echo)
errors: WBE_VALIDATION (required / length / email / null address) · WBE2080 (no tenant row)
```

The `logo_url` is **not** in the request body — logos have their own endpoints. The two toggles are
**not** here either (env-settings endpoint).

## 3. Upload logo

```
POST /api/v1/settings/preferences/logo        Content-Type: multipart/form-data
  part "file" = the user's image (.jpg/.jpeg/.png/.gif, ≤ 2 MB; .svg rejected — F-02 stored-XSS)
→ 200, data: PreferencesResponse (refreshed; logo_url now non-null with a fresh ?v= cache-buster)
errors: WBE2084 (no filename) · WBE2085 (empty) · WBE2086 (> 2 MB, {0}=cap) ·
        WBE2087 (extension not allowed, {0}=allowed list) · WBE2081 (disk write failed) · WBE2080
```

## 4. Clear logo

```
DELETE /api/v1/settings/preferences/logo
→ 200, data: PreferencesResponse (refreshed; logo_url now null)   — idempotent (no-op if none set)
errors: WBE2080
```

## 5. Stream logo bytes

```
GET /api/v1/settings/preferences/logo
→ 200, Content-Type: image/png|image/jpeg|image/gif   (resolved per stored extension; image/svg+xml never occurs — .svg uploads are rejected, F-02)
       Content-Length: <bytes>
       Content-Disposition: inline
       Cache-Control: private, max-age=300
       X-Content-Type-Options: nosniff
       body = raw image bytes (NOT the JSON envelope)
errors (JSON envelope via GlobalExceptionHandler):
       WBE2082 (no logo set — frontend → placeholder) · WBE2083 (file missing on disk) · WBE2080
```

The endpoint is JWT-protected, so the frontend fetches the blob with the `Authorization` header set and
pins it to an `<img src={objectURL}>` (a plain `<img src>` cannot carry the bearer token). On `WBE2082`
the frontend renders the placeholder card.

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_VALIDATION` | 200/400 | Bean validation (required/length/email) or service `address` null guard |
| `WBE2080` | 200 | `BUSI_INFM` row missing for the current tenant |
| `WBE2081` | 200 | Logo disk write failed |
| `WBE2082` | 200 | No logo set (stream) — frontend treats as "no logo" |
| `WBE2083` | 200 | Logo file unavailable on disk |
| `WBE2084` | 200 | Uploaded file has no usable filename |
| `WBE2085` | 200 | Uploaded file is empty |
| `WBE2086` | 200 | Upload exceeds the 2 MB cap (`{0}` = cap) |
| `WBE2087` | 200 | Upload type not allowed (`{0}` = allowed list) |
| `WBE1009` | 401 | JWT missing / invalid |
| `WBE1003` | 403 | Authenticated but lacks `settings-core-preference` |
| `WBE9999` | 500 | Missing tenant context / unexpected server error |

Messages registered in [messages.properties](../../../../src/main/resources/messages.properties)
(`WBE2080`–`WBE2087`). Frontend consumption of these endpoints:
[wecms-web/docs/features/settings/preferences/api-consumption.md](../../../../../wecms-web/docs/features/settings/preferences/api-consumption.md).
