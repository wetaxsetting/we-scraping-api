# Logout — Interface Specification (Backend REST contract)

The endpoint is under base path **`/api/v1/auth`**. Wire format is lowercase snake_case (global Jackson
`SNAKE_CASE`); envelope is `{ status, data, timestamp?, path?, trace_id? }`. Controller:
[AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java).

## Endpoint table

| # | Method | Path | Auth | Returns |
|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/logout` | **Public** (permit-all) | `ApiResponse<Void>` — `{ status, data: null }` |

## Authorization

**Public** — `POST /api/v1/auth/logout` is in the `SecurityConfig` permit-all list
([SecurityConfig.java](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java), the same
`.requestMatchers(...).permitAll()` entry as `/api/v1/auth/login` and `/actuator/health`). No JWT, no
menu grant, no role gate. A request with a missing / invalid / expired token still succeeds (it bypasses
the `JwtAuthenticationEntryPoint` 401 path) — in that case there is simply no valid `jti` to revoke.
When a valid token is presented, the handler revokes its `jti` server-side.

---

## 1. Logout

### Request

```
POST /api/v1/auth/logout
```

- **No request body** (empty).
- `Authorization: Bearer <token>` header is **optional** (route is permit-all). When present and valid,
  its `jti` is revoked server-side (`TokenRevocationService`), so the token is rejected on the next
  request.

### Response

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Logout successful" },
  "data": null
}
```

`data` is `null` (`ApiResponse<Void>`); `@JsonInclude(NON_NULL)` drops it, so the wire body may render
as `{ "status": { "error": false, "code": "WBI0000", "message": "Logout successful" } }`. HTTP 200.

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success — the only outcome (constant response, message `"Logout successful"`) |

No business error is reachable on this endpoint.

---

Frontend consumption (token clear + best-effort POST + redirect):
[wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md).
