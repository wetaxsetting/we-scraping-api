# JWT Authentication — Interface Specification (Backend contract)

JWT authentication has **no REST endpoint of its own** — it is a cross-cutting security layer. Its
"interface" is two wire contracts every protected endpoint shares:

1. the **`Authorization: Bearer <token>`** request header (how a caller proves identity), and
2. the **401 `WBE1401`** error envelope (what a missing/invalid/expired token gets back).

Token *issuance* is part of the login response (`access_token`) — see [../login/](../login/). The
error envelope is lowercase snake_case (global Jackson `SNAKE_CASE`); shape
`{ status, data, timestamp?, path?, trace_id? }`.

---

## 1. Request contract — `Authorization` header

Every endpoint outside the permit-all list (`/api/v1/auth/login`, `/api/v1/auth/logout`,
`/actuator/health`, `/api/v1/payment/dispatch/callbacks/**`) requires:

```
Authorization: Bearer <jwt>
```

- Scheme is literally `Bearer ` (trailing space) — `JwtAuthenticationFilter:40` proceeds only when the header is present **and** starts with `Bearer `.
- `<jwt>` is the compact `header.payload.signature` string the login response returned as `access_token`.
- No other transport is honored by the API contract — not a cookie, not a query param, not a body field. The token is the **only** source of the request's tenant + product type (a body/param cannot override the JWT-bound `bizKey` / `prdtType`).
- A request without the header (or with a malformed/expired/forged token) is treated as unauthenticated; protected matchers then return the 401 below.

### Token claims carried in the header (read-only on the server)

`JwtTokenProvider.issue(...)` writes these; `JwtAuthenticationFilter` reads them. Clients never decode
claims for a security decision (the FE uses them for display only).

| Claim | JSON key | Type | Meaning | Nullable |
|---|---|---|---|---|
| Subject | `sub` | string | `USER_ID` | no |
| Display name | `userNm` | string | user display name (audit `REG_NM`, `LAST_EDTR`) | no (falls back to `userId` if blank on read) |
| Tenant | `bizKey` | string | resolved `BIZ_KEY` | no |
| Language | `langCd` | string | `EN` / `KO` / `KM` / `VI` | no |
| Authority | `authGrCd` | string | `ADMIN` or `USER` (normalized at issue) | no (defaults `USER` on read if absent) |
| Product type | `prdtType` | string | verbatim `BUSI_INFM.PRDT_TYPE` letter | **yes** — claim omitted entirely when null/blank |
| Token id | `jti` | string | unique per-token id — keys server-side revocation (`TokenRevocationService`) | no |
| Issuer | `iss` | string | `wecms.jwt.issuer` (`wecms-api`) | no |
| Issued at | `iat` | epoch seconds | issue time — compared against the per-user not-before cutoff on revocation | no |
| Expiry | `exp` | epoch seconds | `iat + expires_in_seconds` | no |

Header `alg` is `HS256` (secret 32–63 bytes) or `HS512` (≥ 64 bytes), fixed at startup.

The `jti` lets logout (and admin lock/delete/demote/password-reset) revoke a specific token or all of a
user's tokens server-side; `JwtAuthenticationFilter` rejects a revoked / superseded token with **401
`WBE1401`**, before its `exp`. *(Server-side revocation was formerly absent — implemented 2026 via
F-03; the revocation store is in-memory.)*

---

## 2. Authorization outcomes

| Situation | HTTP | Code | Source |
|---|---|---|---|
| Valid token, sufficient grant | 200 / endpoint result | `WBI0000` (success) | controller |
| **Missing / invalid / expired / forged token** | **401** | **`WBE1401`** | `JwtAuthenticationEntryPoint` |
| Valid token but lacks the menu/role grant | 403 | `WBE1003` | `GlobalExceptionHandler` (`AccessDeniedException`) — see [../authorization/](../authorization/) |

401 vs 403 is deliberate (RFC 7235): 401 = "credentials missing or invalid" (this layer), 403 =
"authenticated but forbidden" (the authorization layer).

---

## 3. 401 error envelope (`WBE1401`)

Returned by `JwtAuthenticationEntryPoint.commence(...)` — HTTP **401**, `Content-Type: application/json; charset=UTF-8`:

```json
{
  "status": {
    "error": true,
    "code": "WBE1401",
    "message": "Your session is no longer valid. Please sign in again."
  },
  "timestamp": "2026-06-23T09:12:44Z",
  "path": "/api/v1/banking/accounts",
  "trace_id": "20260623091244PT06592776769567DEVL"
}
```

- `status.error` = `true`, `status.code` = `WBE1401` (the dedicated session-expired code; parity with legacy `SES001`, `JwtAuthenticationEntryPoint:46-52`).
- `status.message` is localized via `MessageResolver.resolve("WBE1401")` from `Accept-Language` (`messages.properties:7`).
- `path` = the request URI that failed.
- `trace_id` = the request-scoped MDC `traceId` (`TraceId.current()`), so the body and server logs correlate.

> `WBE1401` is the code the **live entry-point** emits (`JwtAuthenticationEntryPoint:52`). The older
> `wecms-api/CLAUDE.md` Auth-Rules narration of `WBE1009` on this path is stale; this contract follows
> the code + `messages.properties`.

### Response header

```
X-Trace-Id: 20260623091244PT06592776769567DEVL
```

The same JEX-style trace id is echoed in the `X-Trace-Id` response header on every error (including
this 401), so a user can quote it from the re-auth dialog and ops can grep it in the logs.

---

## 4. Error-code registry (this slice)

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success (valid token, endpoint succeeded) |
| `WBE1401` | 401 | Session no longer valid — missing / invalid / expired / forged JWT (parity legacy `SES001`) |
| `WBE1003` | 403 | Authenticated but lacks the required grant (authorization layer, not this filter) |

---

## 5. Frontend consumption

The frontend stores the `access_token`, attaches it as `Authorization: Bearer` on every request, and
watches for HTTP 401 to raise the session-expired re-auth dialog. It never decodes the JWT for a
security decision. See
[wecms-web/docs/features/auth/jwt-authentication/api-consumption.md](../../../../../wecms-web/docs/features/auth/jwt-authentication/README.md)
and the session-management 401 interceptor in [../session-management/](../session-management/).
