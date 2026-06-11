# User Management — Interface Specification (REST)

Back to [README](README.md) · [functional-specification](functional-specification.md) · frontend [api-consumption](../../../../../wecms-web/docs/features/settings/user-management/api-consumption.md)

**Base path:** `/api/v1/settings/users` · **Auth:** JWT (everything outside `/api/v1/auth/*`).
**Wire format:** lowercase snake_case (global Jackson `SNAKE_CASE`); envelope `{ status, data, … }`.
Controller: `UserManagementController` (`domain/setting/users/controller/`).

---

## 1. Endpoints

| # | Method | Path | Auth gate | Request | Response data |
|---|---|---|---|---|---|
| 1 | GET | `/api/v1/settings/users` | `@userMgmtAccess.canViewUsers()` | — | `UserListResponse` |
| 2 | GET | `/api/v1/settings/users/check-id?login_id=` | `hasRole('ADMIN')` | query `login_id` | `CheckLoginIdResponse` |
| 3 | GET | `/api/v1/settings/users/{user_id}` | `@userMgmtAccess.canViewUsers()` | path | `UserRowResponse` |
| 4 | POST | `/api/v1/settings/users` | `hasRole('ADMIN')` | `UserCreateRequest` | `UserRowResponse` |
| 5 | PUT | `/api/v1/settings/users/{user_id}` | `hasRole('ADMIN')` | `UserUpdateRequest` | `UserRowResponse` |
| 6 | DELETE | `/api/v1/settings/users/{user_id}` | `hasRole('ADMIN')` | path | `Void` |
| 7 | GET | `/api/v1/settings/users/{user_id}/menu-entitlement` | `hasRole('ADMIN')` | path | `MenuEntitlementResponse` |
| 8 | PUT | `/api/v1/settings/users/{user_id}/menu-entitlement` | `hasRole('ADMIN')` | `MenuEntitlementUpdateRequest` | `MenuEntitlementResponse` |
| 9 | POST | `/api/v1/settings/users/{user_id}/image` | `hasRole('ADMIN')` | multipart `file` | `UserRowResponse` |
| 10 | DELETE | `/api/v1/settings/users/{user_id}/image` | `hasRole('ADMIN')` | path | `UserRowResponse` |
| 11 | GET | `/api/v1/settings/users/{user_id}/image` | `@userMgmtAccess.canViewUsers()` | path | image bytes (`InputStreamResource`) |

> The earlier plan's `/api/v1/users` base path was never shipped — the live base is
> `/api/v1/settings/users`.

## 2. Request DTOs (Java records → snake_case wire)

### `UserCreateRequest`
```
user_id      string  @NotBlank @Size(max=100)
user_pw      string  @NotBlank @Size(min=8,max=100) @Pattern(".*[^A-Za-z0-9].*")
user_nm      string  @NotBlank @Size(max=100)
eml          string  @NotBlank @Size(max=100) @Email
clph_no      string  @NotBlank @Size(max=100)
posi_nm      string  @NotBlank @Size(max=100)
dept_nm      string  @NotBlank @Size(max=100)
set_as_admin boolean
```

### `UserUpdateRequest`
```
user_nm      string  @NotBlank @Size(max=100)
eml          string  @NotBlank @Size(max=100) @Email
clph_no      string  @NotBlank @Size(max=100)
posi_nm      string  @NotBlank @Size(max=100)
dept_nm      string  @NotBlank @Size(max=100)
set_as_admin boolean
user_pw      string? @Size(min=8,max=100) @Pattern(".*[^A-Za-z0-9].*")  // optional; blank = keep
```
`user_id` travels in the path, not the body (immutable).

### `MenuEntitlementUpdateRequest`
```
menu_auth_cds  string[]  @NotNull, each @Size(min=1,max=100)   // full desired set; [] = revoke all
```

## 3. Response DTOs

### `UserRowResponse`
```
user_id          string
user_nm          string
eml              string
clph_no          string
posi_nm          string|null
dept_nm          string|null
is_admin         boolean
is_current_user  boolean        // true → frontend renders "(You)"
user_img_url     string|null    // /api/v1/settings/users/{userId}/image?v={saveFileNm} | null
```

### `UserListResponse`
```
users        UserRowResponse[]
total_count  int                // display only — NO plan cap
```

### `CheckLoginIdResponse`
```
available  boolean   // true = login id free (global check)
```

### `MenuEntitlementResponse`
```
user_id        string
menu_auth_cds  string[]   // granted slugs (Y-only)
```

## 4. Example envelopes

Success — `GET /api/v1/settings/users`:
```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "users": [
      { "user_id": "alice", "user_nm": "Alice", "eml": "a@x.com", "clph_no": "0900000001",
        "posi_nm": "Manager", "dept_nm": "Finance", "is_admin": true,
        "is_current_user": true, "user_img_url": "/api/v1/settings/users/alice/image?v=Ab3..." }
    ],
    "total_count": 1
  }
}
```

Business error — `POST` duplicate id:
```json
{
  "status": { "error": true, "code": "WBE_USERS_DUPLICATE_LOGIN_ID", "message": "This ID is already taken." },
  "timestamp": "2026-06-05T03:21:00Z",
  "path": "/api/v1/settings/users",
  "trace_id": "20260605032100PT…DEVL"
}
```

Authorization error — viewer attempts a write → **HTTP 403**, `status.code = WBE1003`.

## 5. Error codes

| Code | HTTP | Meaning / trigger |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_USERS_NOT_FOUND` | 200 | User/link not in tenant (detail, update, delete, entitlement, image) |
| `WBE_USERS_DUPLICATE_LOGIN_ID` | 200 | Create with an existing `USER_ID` |
| `WBE_USERS_LOGIN_ID_REQUIRED` | 200 | check-id called with blank `login_id` |
| `WBE_USERS_CANNOT_DELETE_SELF` | 200 | Deleting your own account |
| `WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN` | 200 | Demoting/deleting the tenant's only admin |
| `WBE_VALIDATION` | 200 | Bean-validation failure on a request body/param |
| `WBE1003` | 403 | Defence-in-depth admin guard (`AccessDeniedException`) |
| `WBE2111` | 200 | Avatar disk write failed |
| `WBE2113` | 200 | No avatar set (stream) — frontend renders placeholder |
| `WBE2114` | 200 | Avatar file missing/unreadable on disk |
| `WBE2115` | 200 | Uploaded file has no usable filename |
| `WBE2116` | 200 | Uploaded file empty |
| `WBE2117` | 200 | Uploaded file exceeds 2 MB (arg = cap) |
| `WBE2118` | 200 | Extension not in allow-list jpg/jpeg/png/gif/svg (arg = list) |
| `WBE9999` | 500 | Unexpected server fault |

All codes resolve via `messages.properties` / `messages_en.properties`. Envelope + trace-id rules:
workspace [`wecms-api/CLAUDE.md` §API/DB Rules](../../../../CLAUDE.md).

## 6. Frontend consumption

The Next.js client calls these via `lib/api/userManagement.ts`. Endpoint-by-endpoint client mapping:
[wecms-web api-consumption](../../../../../wecms-web/docs/features/settings/user-management/api-consumption.md).
