# Login — Interface Specification (Backend REST contract)

Single endpoint under **`/api/v1/auth/login`** (public). Wire format is lowercase snake_case (global
Jackson `SNAKE_CASE`); envelope is `{ status, data, timestamp?, path?, trace_id? }`. Controller:
[AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java).

## Endpoint table

| Method | Path | Auth | Returns |
|---|---|---|---|
| POST | `/api/v1/auth/login` | **Public** (permit-all in `SecurityConfig`) | `ApiResponse<LoginResponse>` — authenticated **or** chooser shape |

`GET /api/v1/auth/me`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/change-password` share the
`AuthController` but belong to other modules — see [session-management](../session-management/README.md),
[logout](../logout/README.md), [change-password](../change-password/README.md).

---

## Request

```
POST /api/v1/auth/login
Content-Type: application/json
Accept-Language: en        # → lang_cd EN (ko/km/vi accepted; else EN)
```

```json
// First call — no biz_key
{ "user_id": "alice", "user_pw": "secret" }
```

```json
// Finalize call — with the chosen biz_key (only for multi-link users)
{ "user_id": "alice", "user_pw": "secret", "biz_key": "VN_VNM0004_000001" }
```

`user_id` / `user_pw` required (`@NotBlank`, ≤ 100, trimmed). `biz_key` optional (≤ 20,
`^[A-Z0-9_]+$`, trimmed, blank → null).

---

## Response — authenticated

Single-link user, or multi-link user finalising with `biz_key`:

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "access_token": "eyJhbGciOiJIUzUxMiJ9...",
    "token_type":   "Bearer",
    "expires_in":   3600,
    "user_id":      "alice",
    "user_nm":      "Alice Nguyen",
    "biz_key":      "VN_VNM0004_000001",
    "lang_cd":      "EN",
    "cmpn_nm":      "CÔNG TY TNHH WEBCASH VIỆT NAM",
    "auth_gr_cd":   "ADMIN",
    "prdt_type":    "F",
    "tax_id":       "0312345678"
  }
}
```

- `access_token` is the only field with an explicit `@JsonProperty("access_token")` wire override
  (OAuth-style); every other field auto-snakes from its camelCase Java name.
- `prdt_type` / `tax_id` are verbatim `BUSI_INFM` values — `null` (dropped from the wire via
  `@JsonInclude(NON_NULL)`) when the row carries no value; never silently defaulted.

---

## Response — chooser

Multi-link user, first call without `biz_key` (exceptional fallback path):

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "user_id": "alice",
    "user_nm": "Alice Nguyen",
    "companies_available": [
      { "biz_key": "VN_VNM0004_000014", "cmpn_nm": "HAENGSUNG ELECTRONICS", "auth_gr_cd": "USER",  "prdt_type": "H" },
      { "biz_key": "VN_VNM0004_000001", "cmpn_nm": "WeCMS R&D Tenant",      "auth_gr_cd": "ADMIN", "prdt_type": "F" }
    ]
  }
}
```

- The presence of `companies_available` discriminates the two shapes — no `access_token`, no `biz_key`,
  no `prdt_type` / `tax_id` at the top level on the chooser shape (all null, dropped from the wire).
- Options are sorted by `cmpn_nm` (nulls last), tie-broken on `biz_key`.
- No JWT is issued, no `LST_LGN_DATE` is written, and no `SUCCESS` `USER_LOGIN_HIS` row is recorded on
  this branch — the client re-posts with the chosen `biz_key` to finalize.

---

## Error envelope

```json
{
  "status":    { "error": true, "code": "WBE1009", "message": "ID or password does not match." },
  "timestamp": "2026-06-23T09:14:02Z",
  "path":      "/api/v1/auth/login",
  "trace_id":  "20260623091402PT06592776769567DEVL"
}
```

`BusinessException` → HTTP 200 with `status.error = true`; the frontend reads `status.error`, not the
HTTP code. `timestamp` / `path` / `trace_id` accompany every error (and are echoed in the `X-Trace-Id`
response header).

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success (authenticated or chooser) |
| `WBE1009` | 200 | ID/password mismatch · user not found · zero active links · `biz_key` out of scope |
| `WBE1010` | 200 | Account not activated (`ACTV_YN ≠ Y`) |
| `WBE1011` | 200 | Account locked (`LOCK_YN = Y`) |
| `WBE1128` | 200 | Account deleted (`DEL_YN = Y`) |
| `WBE_VALIDATION` | 200/400 | Bean-validation failure on the request body |
| `WBE9999` | 500 | Unexpected server error |

> `WBE1401` (unauthenticated / session expired) is **not** part of the login response — it is raised by
> `JwtAuthenticationEntryPoint` on the *protected* endpoints when a token is missing/invalid/expired (see
> [jwt-authentication](../jwt-authentication/README.md) / [session-management](../session-management/README.md)).

---

Frontend consumption:
[wecms-web/docs/features/auth/login/api-consumption.md](../../../../../wecms-web/docs/features/auth/login/api-consumption.md).
