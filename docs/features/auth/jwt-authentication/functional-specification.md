# JWT Authentication — Functional Specification (Backend)

The implemented behaviour of the refactor's JWT token mechanics. Source of truth: the Spring code in
`com.kosign.wecms.common.security` + `com.kosign.wecms.config` + `com.kosign.wecms.common.tenant`.
Issuance is triggered by [../login/](../login/); the `/me` session lifecycle lives in
[../session-management/](../session-management/).

---

## 1. Token structure

The token is a standard compact JWS (`header.payload.signature`) produced by `jjwt 0.12.6`.

### 1.1 Header

| Field | Value | Source |
|---|---|---|
| `alg` | `HS256` (secret 32–63 bytes) **or** `HS512` (secret ≥ 64 bytes) | `JwtTokenProvider:43` (`secretBytes.length >= 64 ? Jwts.SIG.HS512 : Jwts.SIG.HS256`) |
| `typ` | `JWT` | jjwt default |

The algorithm is **chosen at construction from the secret length** — not configurable per token. A
longer secret automatically upgrades the MAC. HS512 is preferred (use a ≥ 64-byte secret).

### 1.2 Claims (payload)

Written by `JwtTokenProvider.issue(userId, userNm, bizKey, langCd, authGrCd, prdtType)` (`:62-92`):

| Claim | Type | Meaning | Notes |
|---|---|---|---|
| `sub` | string | `USER_ID` | JWS subject (`.subject(userId)`) |
| `userNm` | string | display name | used downstream for audit `REG_NM`, `LAST_EDTR` |
| `bizKey` | string | resolved `BIZ_KEY` (tenant) | the company chosen at login — the only tenant source for the request |
| `langCd` | string | language code | `EN` / `KO` / `KM` / `VI`; read back via `LangCd.fromCodeOrDefault(...)` |
| `authGrCd` | string | `ADMIN` / `USER` | **normalized at issue**: `ADMIN` only when equal to `TenantContext.AUTH_GR_ADMIN`, else `USER` (`:73-75`) |
| `prdtType` | string | verbatim `BUSI_INFM.PRDT_TYPE` letter | **omitted entirely** when null/blank (`:80-82`) — never defaulted to `"F"`. Any of the 14 legacy letters round-trips verbatim |
| `iss` | string | `wecms.jwt.issuer` (default `wecms-api`) | `.issuer(props.issuer())` |
| `iat` | epoch | issued-at | `Instant.now()` |
| `exp` | epoch | expiry | `iat + expiresInSeconds()` (`:64-65`) |

`prdtType` is the only optional claim — it is conditionally added, so a JWT for a tenant with a null
`PRDT_TYPE` simply has no `prdtType` key. Every other claim is always present.

### 1.3 Signature

`HMAC-SHA-256` or `HMAC-SHA-512` over `base64url(header).base64url(payload)`, keyed by
`Keys.hmacShaKeyFor(secretBytes)` (`:44`). There is no asymmetric key; the same secret signs and
verifies. The signature makes every claim **tamper-evident and immutable** — a client cannot edit
`bizKey`, `authGrCd`, or `prdtType` without invalidating the token.

---

## 2. Lifecycle — issue → transport → parse → authorize

```
[login module]                                  [every protected request]
  AuthService.login(...)                            JwtAuthenticationFilter.doFilterInternal(...)
    └─ JwtTokenProvider.issue(                         ├─ read Authorization: Bearer <token>
         userId, userNm, bizKey,                       ├─ tokenProvider.parse(token) → Jws<Claims>  (verify signature + exp)
         langCd, authGrCd, prdtType)                   ├─ read claims: sub, userNm, bizKey, authGrCd, prdtType, langCd
         → signed JWT string                           ├─ authorities = [ ROLE_ADMIN | ROLE_USER ]  (from authGrCd)
                                                        ├─ SecurityContextHolder ← UsernamePasswordAuthenticationToken(userId, …)
  LoginResponse.access_token  ──► frontend             ├─ TenantContext.set(userId, userNm, bizKey, langCd, authGrCd, prdtType)
       (tokenStorage)                                   ├─ chain.doFilter(...)        ← controller + domain services run here
                                                        └─ finally: TenantContext.clear()   ← always, even on exception
```

### 2.1 Issue

`JwtTokenProvider.issue(...)` is invoked once per login (login module). It builds the claim map,
normalizes `authGrCd`, conditionally adds `prdtType`, stamps `iat`/`exp`, signs, and returns the
compact token. No DB write — the token is self-contained.

### 2.2 Transport

The frontend stores the token (see the WEB counterpart) and sends it on every request as
`Authorization: Bearer <token>`. The backend reads only this header — never a cookie for the API
contract, never a body field.

### 2.3 Parse + validate

`JwtAuthenticationFilter` (`:34-96`):

1. Reads the `Authorization` header; proceeds only when it is present **and** starts with `Bearer ` (`:40`).
2. `tokenProvider.parse(token)` (`:43`) → `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` (`JwtTokenProvider:94-99`). This verifies the **signature** and the **expiry** — an expired or forged token throws `JwtException`.
3. Extracts claims: `sub`, `userNm`, `bizKey`, `authGrCd`, `prdtType`, `langCd`, and the `jti` (token id).
4. **Revocation check:** the filter consults `TokenRevocationService` — a token whose `jti` is on the denylist (logout), or that was issued before the per-user not-before cutoff (admin lock / delete / demote / password-reset), is treated as invalid; the `SecurityContext` is left unauthenticated (→ 401 on a protected matcher). *(Formerly absent — server-side revocation was deferred; implemented 2026 via F-03.)*
5. **Defensive defaults:** an `authGrCd`-less token defaults to `USER` (never silently elevates to ADMIN, `:56-58`); a blank `userNm` falls back to `userId` (`:80`); `prdtType` is allowed to be null (pre-`prdtType`-cutover tokens, `:84`).
6. Sets a `UsernamePasswordAuthenticationToken(principal=userId, authorities=[ROLE_<auth>])` into the `SecurityContextHolder` (`:62-64`).
7. Populates `TenantContext` (full six-arg setter, `:78-84`).

### 2.4 Authorize

Downstream `@PreAuthorize("@menuAuthz.canAccess('...')")` / `hasRole('ADMIN')` gates read the
authorities the filter set; domain services read tenant + product type from
`TenantContext.currentBizKey()` / `currentPrdtType()`. See [../authorization/](../authorization/).

### 2.5 Teardown

Whatever happens in the chain, `TenantContext.clear()` runs in the `finally` block (`:93-95`) so the
`ThreadLocal` never leaks into the next request served by the same pooled thread. The clear is guarded
by `tenantSet` so a request without a token doesn't touch the holder.

---

## 3. Expiration / TTL

- TTL is `wecms.jwt.expires-in-seconds` (`JwtProperties`, default `3600` s in `application.yml:102`).
- `issue(...)` sets `exp = iat + expiresInSeconds()`.
- `parse(...)` validates `exp` — an expired token throws `JwtException` (subclass `ExpiredJwtException`), the filter clears the context, and the request reaches an authenticated matcher with no `SecurityContext` → `JwtAuthenticationEntryPoint` returns **401 WBE1401**.
- There is **no sliding/refresh** — the token simply expires. Re-authentication = a fresh login. Refresh tokens are **MISSING** (deferred — [refactor-gap-analysis.md](refactor-gap-analysis.md)).
- A claim change (authority promoted, product type changed) takes effect at the **next login** only — the in-flight token's claims are signed and immutable. A token can, however, be **revoked** immediately (logout, or admin lock/delete/demote/password-reset) via `TokenRevocationService`, after which the filter rejects it before its TTL. The revocation store is in-memory (open limitation).

---

## 4. Filter chain order (`SecurityConfig`)

`SecurityConfig.filterChain(...)` (`:42-69`):

- `csrf` **disabled**, `cors` **enabled**, session policy **`STATELESS`** (`:44-46`) — no `HttpSession` is ever created.
- **Public (permit-all):** `/api/v1/auth/login`, `/api/v1/auth/logout`, `/actuator/health`, and `/api/v1/payment/dispatch/callbacks/**` (per-VAN crypto + IP allow-list authenticated, not JWT). Everything else `authenticated()` (`:47-54`).
- **Entry point override:** `exceptionHandling(...).authenticationEntryPoint(jwtAuthenticationEntryPoint)` (`:58`) — so a missing/invalid JWT surfaces as **401** (RFC 7235), not Spring's default 403.
- **Filter order** (`:66-67`): both custom filters anchor before `UsernamePasswordAuthenticationFilter`, inserted in order:

  ```
  CallbackIpWhitelistFilter  →  JwtAuthenticationFilter  →  UsernamePasswordAuthenticationFilter
  ```

  The callback IP filter runs first (rejects disallowed sources for the permitted callback path at the edge); the JWT filter then parses the Bearer header for everything else.
- `@EnableMethodSecurity` (`:24`) — turns on `@PreAuthorize`; the authorities the JWT filter sets are live, not advisory.

---

## 5. Entry-point 401 behaviour (`JwtAuthenticationEntryPoint`)

When an authenticated matcher is hit without a valid `SecurityContext` (the aftermath of the filter
rejecting an expired/forged token, or a request with no `Authorization` header), Spring Security calls
`commence(...)` (`:57-74`):

- HTTP status **401** (not 403) — RFC 7235 "credentials missing or invalid".
- Content type `application/json`, charset UTF-8.
- Body = `ApiResponse.error(WBE1401, <localized message>, requestURI, traceId)` — the standard envelope with `status`, `path`, `trace_id`.
- `WBE1401` is the dedicated **session-expired** code, **parity with legacy `SES001`** (`JwtAuthenticationEntryPoint:46-52`, citing `WECMS DEV/.../jex.wabooks.custom_v2.js:107`). It disambiguates "session no longer valid" from `WBE1009` "bad credentials" (returned by `AuthService` on a wrong password).
- `traceId` comes from `TraceId.current()` (MDC), so the JSON body and the server log line correlate; the same id is echoed in the `X-Trace-Id` response header by the trace filter.
- Logged at `warn` with method + URI (`:64`).

> **Code note:** `WBE1401` is the entry-point's source-of-truth code (`JwtAuthenticationEntryPoint:52`).
> The root `wecms-api/CLAUDE.md` Auth-Rules section narrates the older `WBE1009` value for this path —
> the **live code uses `WBE1401`**, which the `messages.properties:7` entry backs
> (`WBE1401=Your session is no longer valid. Please sign in again.`). This doc follows the code.

---

## 6. Fail-fast secret rules (`JwtTokenProvider` constructor)

Validated at bean construction (`:26-45`) — a bad secret fails application startup, not the first request:

| Condition | Outcome |
|---|---|
| `secret` null or blank | `IllegalStateException` — "wecms.jwt.secret is not set… set `WECMS_JWT_SECRET` (32+ chars)" (`:29-34`) |
| `secret` < 32 bytes (UTF-8) | `IllegalStateException` — "must be at least 32 bytes for HS256 (current length: N)… `openssl rand -hex 64`" (`:36-41`) |
| `secret` 32–63 bytes | boots with **HS256** |
| `secret` ≥ 64 bytes | boots with **HS512** (preferred) |

`JwtProperties` additionally enforces `@NotBlank secret`, `@Positive expiresInSeconds`, `@NotBlank
issuer` via bean validation. The secret is read from env `WECMS_JWT_SECRET` (`application.yml:101`);
never hardcoded.

---

## 7. Tenant / product-type binding rule

The token is the **only** authoritative source of `bizKey` and `prdtType` for a request:

- The filter populates `TenantContext` from the signed claims; domain services read `TenantContext.currentBizKey()` / `currentPrdtType()`.
- A request body, query param, or header **cannot** override the JWT-bound tenant or product type (the secret-signed claim wins). The frontend's `StoredUser.productType` is for UI gating only — never a security boundary.
- `TenantContext` is a request-scoped `ThreadLocal`; it has no async propagation, so a background thread / executor sees `null` (documented in `TenantContext` Javadoc `:36-38`).
