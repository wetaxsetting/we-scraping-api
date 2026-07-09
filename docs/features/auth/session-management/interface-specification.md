# Session Management — Interface Specification (Backend REST contract)

Single endpoint under **`/api/v1/auth/me`** (protected). Wire format is lowercase snake_case (global
Jackson `SNAKE_CASE`); envelope is `{ status, data, timestamp?, path?, trace_id? }`. Controller:
[AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java).

## Endpoint table

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/auth/me` | **Protected** (`anyRequest().authenticated()` in `SecurityConfig`) | `ApiResponse<MeResponse>` |

`POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/change-password` share the
`AuthController` but belong to other modules — see [login](../login/README.md), [logout](../logout/README.md),
[change-password](../change-password/README.md).

---

## Request

```
GET /api/v1/auth/me
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

No request body, no query params. The caller's identity is read from the signed JWT (via the filter →
`TenantContext`); nothing in the request can override the token-bound `user_id` / `biz_key`.

---

## Response — success

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "user_id":          "alice",
    "user_nm":          "Alice Nguyen",
    "biz_key":          "VN_VNM0004_000001",
    "lang_cd":          "EN",
    "cmpn_nm":          "CÔNG TY TNHH WEBCASH VIỆT NAM",
    "auth_gr_cd":       "USER",
    "prdt_type":        "F",
    "tax_id":           "0312345678",
    "all_menus":        false,
    "allowed_menu_cds": ["addon-einvoices-sales", "addon-einvoices-expenses", "addon-operation-status-inquiry"]
  }
}
```

Admin session (menu filtering bypassed):

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "user_id":          "admin",
    "user_nm":          "Admin User",
    "biz_key":          "VN_VNM0004_000001",
    "lang_cd":          "EN",
    "cmpn_nm":          "CÔNG TY TNHH WEBCASH VIỆT NAM",
    "auth_gr_cd":       "ADMIN",
    "prdt_type":        "F",
    "tax_id":           "0312345678",
    "all_menus":        true,
    "allowed_menu_cds": []
  }
}
```

- Every field auto-snakes from its camelCase Java name — there is no `@JsonProperty` override on
  `MeResponse` (unlike `LoginResponse.access_token`). `MeResponse` carries no token (the client already
  holds it).
- `prdt_type` / `tax_id` are verbatim values — **`null`** (and dropped from the wire via the envelope's
  `@JsonInclude(NON_NULL)`) when the underlying value is unset; never silently defaulted.
- `all_menus = true` ⇒ admin; `allowed_menu_cds` is then `[]` and the frontend ignores it (shows the full
  product tree). `all_menus = false` ⇒ normal user; `allowed_menu_cds` carries the granted leaf slugs (an
  empty array means "no menus granted"). Resolved fresh per call — see
  [menu-authorization](../menu-authorization/README.md).

---

## Error envelope — session expired / unauthenticated

```json
{
  "status":    { "error": true, "code": "WBE1401", "message": "Your session has expired. Please log in again." },
  "timestamp": "2026-06-23T09:14:02Z",
  "path":      "/api/v1/auth/me",
  "trace_id":  "20260623091402PT06592776769567DEVL"
}
```

`WBE1401` has **two origins**, both surfacing this same envelope:

1. **Token missing / malformed / expired / forged** — the request never reaches the handler. The JWT
   filter cannot bind a `SecurityContext`, and
   [JwtAuthenticationEntryPoint](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationEntryPoint.java)
   returns **HTTP 401** with code `WBE1401` (legacy `SES001` parity). The entry point fires before the
   controller chain, so `@RestControllerAdvice` does not see it; it builds the envelope directly and
   echoes the `X-Trace-Id` response header.
2. **Token valid but live state changed** — `AuthService.me()` re-validates against the DB and throws
   `BusinessException("WBE1401")` when the user is deleted/inactive/locked or the token-bound link was
   removed. `GlobalExceptionHandler` maps it (HTTP 200, business-outcome convention) into the same
   envelope.

Every error response carries `timestamp` / `path` / `trace_id`, and the same trace id is echoed in the
`X-Trace-Id` response header (grepable in server logs).

## Error-code registry

| Code | HTTP | Origin | Meaning |
|---|---|---|---|
| `WBI0000` | 200 | `AuthService.me` success | Session rehydrate OK — `MeResponse` returned |
| `WBE1401` | **401** | `JwtAuthenticationEntryPoint` | Token missing / invalid / expired (session no longer valid) |
| `WBE1401` | 200 | `AuthService.me` (`BusinessException`) | Token valid but user deleted/inactive/locked or link removed |
| `WBE9999` | 500 | catch-all | Unexpected server error |

> `WBE1401` is this module's (and [jwt-authentication](../jwt-authentication/README.md)'s) code; it is
> **distinct from** `WBE1009` (bad credentials, raised only by the [login](../login/README.md) endpoint).
> A client can branch on "session no longer valid" (`WBE1401`) vs "credentials wrong" (`WBE1009`) without
> parsing messages.

---

Frontend consumption:
[wecms-web/docs/features/auth/session-management/api-consumption.md](../../../../../wecms-web/docs/features/auth/session-management/api-consumption.md).
