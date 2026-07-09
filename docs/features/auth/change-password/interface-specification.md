# Change Password — Interface Specification (Backend REST contract)

Single endpoint under base path **`/api/v1/auth`**. Wire format is lowercase snake_case (global Jackson
`SNAKE_CASE`); envelope is `{ status, data, timestamp?, path?, trace_id? }`. Controller:
[AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java).

## Authorization

JWT only — the global `anyRequest().authenticated()` rule in `SecurityConfig`. No `@menuAuthz`, no
role gate. Missing/invalid JWT → 401 `WBE1401` via `JwtAuthenticationEntryPoint` (before the
controller). The caller identity comes from `TenantContext.currentUserId()` (signed JWT); the endpoint
accepts no user id and always changes the caller's own password.

---

## 1. Change password

```
POST /api/v1/auth/change-password
Authorization: Bearer <jwt>
Content-Type: application/json
```

### Request

```json
{
  "current_password": "OldPass!1",
  "new_password":     "NewPass!2",
  "confirm_password": "NewPass!2"
}
```

| Field | Required | Length | Rule |
|---|---|---|---|
| `current_password` | yes | ≤ 100 | `@NotBlank @Size(max=100)`; trimmed |
| `new_password` | yes | ≤ 100 | `@NotBlank @Size(max=100)`; trimmed |
| `confirm_password` | yes | ≤ 100 | `@NotBlank @Size(max=100)`; trimmed; must equal `new_password` |

### Success response

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Password changed" },
  "data": null
}
```

`200 OK`. On success the server has rewritten `USER_INFM.USER_PW` and appended a `USER_PW_CHG_HIS`
row. No payload is returned.

### Error responses (envelope)

Business outcomes ship `200` with `status.error = true` (frontend reads `status.error`, not the HTTP
code); the JWT entry-point and bean-validation paths follow the shared mapping. Examples:

```json
// new != confirm
{ "status": { "error": true, "code": "WBE_CHANGE_PASSWORD_MISMATCH",
              "message": "New password and confirmation do not match." },
  "timestamp": "2026-06-23T09:30:00Z", "path": "/api/v1/auth/change-password",
  "trace_id": "20260623093000PT01234567890123DEVL" }

// current password wrong
{ "status": { "error": true, "code": "WBE2091", "message": "Password confirmation failed." }, ... }

// reuse of last 3
{ "status": { "error": true, "code": "WBE_CHANGE_PASSWORD_REUSED",
              "message": "You cannot reuse any of your last 3 passwords." }, ... }

// no / invalid JWT (returned by JwtAuthenticationEntryPoint, HTTP 401)
{ "status": { "error": true, "code": "WBE1401",
              "message": "Your session is no longer valid. Please sign in again." }, ... }
```

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success ("Password changed") |
| `WBE_CHANGE_PASSWORD_MISMATCH` | 200 | `new_password` ≠ `confirm_password` (legacy `WBE1052`) |
| `WBE2091` | 200 | `current_password` ≠ stored hash — "Password confirmation failed." (legacy `WBE1053`) |
| `WBE_CHANGE_PASSWORD_REUSED` | 200 | New password reuses one of the last 3 history rows (legacy `WBE1017`) |
| `WBE1401` | 200 / 401 | Unauthenticated / session no longer valid / caller no longer eligible |
| `WBE_VALIDATION` | 200 / 400 | Bean-validation failure (any field blank or > 100 chars) |
| `WBE9999` | 500 | Unexpected server error (shared catch-all) |

Message keys live in [messages.properties](../../../../src/main/resources/messages.properties)
(`WBE_CHANGE_PASSWORD_MISMATCH`, `WBE_CHANGE_PASSWORD_REUSED`, `WBE2091`, `WBE1401`) and
`messages_en.properties`.

---

## Validation order (authoritative)

The backend surfaces the **first** failing rule, in this order: `WBE_VALIDATION` (bean validation) →
`WBE_CHANGE_PASSWORD_MISMATCH` → `WBE2091` → `WBE_CHANGE_PASSWORD_REUSED`. See
[functional-specification.md](functional-specification.md) §3.

Frontend consumption:
[wecms-web/docs/features/auth/change-password/api-consumption.md](../../../../../wecms-web/docs/features/auth/change-password/api-consumption.md).
