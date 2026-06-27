# Auth > JWT Authentication — Backend Feature (Refactor)

**Status:** ✅ Implemented — the stateless JWT auth backbone of the refactor: token issuance
(`JwtTokenProvider`, every token carrying a `jti`), per-request parse + tenant binding + **revocation
check** (`JwtAuthenticationFilter`), 401 entry point (`JwtAuthenticationEntryPoint`), config
(`JwtProperties` + `SecurityConfig`), and the request-scoped `TenantContext`. **Server-side
revocation/blacklist = IMPLEMENTED** (formerly MISSING, deferred — implemented 2026 via F-03; in-memory
store). **Refresh token = MISSING** (deferred); **stateless = APPROVED DEVIATION** vs the legacy JEX
`HttpSession`. Issuance is driven by the **login** module; the `GET /me` session lifecycle lives in
**session-management** — both cross-linked below.
**Module / menu:** Auth → **JWT Authentication** (cross-cutting security infrastructure — no standalone screen, no menu node)
**Refactor slug:** `jwt-authentication` (matches the WEB counterpart)
**Wire surface:** the `Authorization: Bearer <token>` request header (every protected endpoint) + the 401 error envelope (`WBE1401`)
**Legacy JEX counterpart:** the JEX `HttpSession` / login-cookie model — server-side session state replaced by a signed, stateless JWT. The 401 code `WBE1401` is **parity** with legacy `SES001` (`WECMS DEV/.../jex.wabooks.custom_v2.js:107`). No DB table backs JWT (stateless).

> This folder is the **primary documentation home** for the JWT token mechanics on the backend.
> "JWT mechanics" = how a token is issued, signed, transported, parsed, validated, expired, and how a
> rejected token becomes a `401 WBE1401`. The *login issuance flow* (credential check, multi-company
> resolution, claim sourcing) lives in [login](../login/) and the *`/me` session lifecycle* lives in
> [session-management](../session-management/); this folder owns only the token contract that ties them
> together.

---

## What this feature is

JWT Authentication is the refactor's stateless security backbone. On a successful login the
**login** service calls `JwtTokenProvider.issue(...)` to mint a signed JWT whose claims pin the
session's identity + tenant + authority + product type. The frontend stores that token and replays it
on every request as `Authorization: Bearer <token>`. On each request `JwtAuthenticationFilter` parses
and verifies the token, sets Spring Security authorities (`ROLE_ADMIN` / `ROLE_USER`), and populates a
request-scoped `TenantContext` that every downstream domain service reads for tenant + product-type
scope. A missing / invalid / expired token never reaches a controller — `JwtAuthenticationEntryPoint`
returns a `401 WBE1401` in the standard envelope.

The model is **stateless** apart from a small server-side **revocation denylist**: there is no
`HttpSession` and no refresh token, but every token carries a `jti` and `JwtAuthenticationFilter`
rejects a token whose `jti` has been revoked (logout) or that predates a per-user not-before cutoff
(admin lock / delete / demote / password-reset). Absent revocation, a token is valid until it expires
(`wecms.jwt.expires-in-seconds`, default `3600`). Claims are signed and immutable, so an authority or
product-type change takes effect only at the **next** login — but the token can now be killed
immediately via revocation rather than only at TTL. The token is the *only* source of tenant + product
type for a request — a request body, query param, or header can never override the JWT-bound `bizKey` /
`prdtType`.

> **Formerly "no blacklist / revocation table" (deferred).** Server-side revocation was implemented 2026
> via F-03 (`TokenRevocationService`). The denylist + per-user cutoff are held **in-memory** — lost on
> restart, not shared across instances; a durable / distributed store is still deferred. Refresh tokens
> remain MISSING (deferred).

---

## Quick facts

| Aspect | Value |
|---|---|
| Library | `jjwt 0.12.6` (HS256 / HS512) |
| Algorithm | **HS256** for a 32–63-byte secret, **HS512** for ≥ 64 bytes (`JwtTokenProvider:43`) |
| Secret | `wecms.jwt.secret` (env `WECMS_JWT_SECRET`), **≥ 32 bytes** or boot fails (`JwtTokenProvider:29-44`) |
| Claims | `sub`=userId, `userNm`, `bizKey`, `langCd`, `authGrCd` (`ADMIN`/`USER`), `prdtType` (verbatim, nullable), `jti` (token id — keys revocation) |
| TTL | `wecms.jwt.expires-in-seconds` (default `3600` s = 1 h) |
| Transport | `Authorization: Bearer <token>` request header |
| Filter | `JwtAuthenticationFilter` (`OncePerRequestFilter`) — before `UsernamePasswordAuthenticationFilter`; sets authorities + `TenantContext`; **clears in `finally`** |
| 401 | `JwtAuthenticationEntryPoint` → HTTP **401** (not 403), code `WBE1401` (parity legacy `SES001`), standard envelope + `X-Trace-Id` |
| Session policy | `SessionCreationPolicy.STATELESS` — no `HttpSession` (server state is only the in-memory revocation denylist) |
| Tenant binding | read **only** from the signed JWT (`TenantContext`), never from a client param |
| Revocation | **IMPLEMENTED** — `TokenRevocationService` (in-memory `jti` denylist + per-user not-before); `JwtAuthenticationFilter` rejects revoked / superseded tokens. *(Open: in-memory store, per-node.)* |
| Refresh token | **MISSING** (deferred) — no refresh / rotation; a token simply expires at its TTL |
| Wire format | error envelope lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend layout

```
com.kosign.wecms.common.security
├── JwtTokenProvider.java            issue(...) HS256/HS512 (stamps jti); parse(token); expiresInSeconds(); fail-fast secret
├── JwtAuthenticationFilter.java     OncePerRequestFilter; Bearer → parse → revocation check → authorities + TenantContext; clear in finally
├── TokenRevocationService.java      in-memory jti denylist + per-user not-before cutoff; revokeToken(...) / revokeAllForUser(...)
└── JwtAuthenticationEntryPoint.java 401 WBE1401 envelope + traceId + X-Trace-Id

com.kosign.wecms.config
├── JwtProperties.java               @ConfigurationProperties("wecms.jwt"): secret / expiresInSeconds / issuer
└── SecurityConfig.java              @EnableMethodSecurity; CSRF off; CORS on; STATELESS; permit-all + filter order

com.kosign.wecms.common.tenant
└── TenantContext.java               request-scoped ThreadLocal Principal(userId,userNm,bizKey,langCd,authGrCd,prdtType)
```

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | Narrative lifecycle: issue → transport → parse → revocation check → authorize → 401; gap classification (revocation = IMPLEMENTED, refresh = MISSING, stateless = APPROVED DEVIATION vs HttpSession = DEAD LEGACY) |
| [functional-specification.md](functional-specification.md) | Token structure (header / claims / signature), full lifecycle, expiration, filter chain order, entry-point 401 behavior, fail-fast secret rules |
| [database-mapping.md](database-mapping.md) | **NONE (no DB table)** — revocation state is in-memory (`TokenRevocationService`); states where a durable revocation / future `REFRESH_TOKEN` table would live |
| [interface-specification.md](interface-specification.md) | `Authorization: Bearer` header contract + 401 error envelope JSON (`WBE1401`) + `X-Trace-Id` header + claim table |
| [source-code-index.md](source-code-index.md) | The 5 security/config files + `TenantContext` with real paths + key methods + line citations |
| [migration-guide.md](migration-guide.md) | Legacy session/cookie auth → stateless JWT; what changed |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with evidence |
| [test-scenarios.md](test-scenarios.md) | Token issue / parse / expiry / invalid-signature / 401 scenarios + coverage (missing JUnit flagged MISSING) |

---

## Sibling modules + WEB counterpart

- **Login (issuance trigger):** [../login/](../login/) — verifies credentials, resolves the tenant, then calls `JwtTokenProvider.issue(...)`.
- **Authorization (consumes the claims):** [../authorization/](../authorization/) — `@menuAuthz` / `hasRole(...)` gates read the authorities + `TenantContext.currentPrdtType()` this filter populates.
- **Session management (`/me` lifecycle + 401 re-auth):** [../session-management/](../session-management/) — stateless `/me`, TTL, and the frontend 401 → re-auth dialog.
- **Frontend counterpart (token storage + transport):** [wecms-web/docs/features/auth/jwt-authentication/README.md](../../../../../wecms-web/docs/features/auth/jwt-authentication/README.md) — `tokenStorage` + the Axios `Authorization: Bearer` request interceptor (FE issues no token, decodes no claims for security).

---

## Open / residual items

1. **Server-side revocation / blacklist = IMPLEMENTED** (formerly MISSING, deferred — implemented 2026 via F-03). Logout revokes the presented `jti`; admin lock/delete/demote/password-reset revokes all of a user's tokens; `JwtAuthenticationFilter` rejects revoked / superseded tokens. **Open limitation:** the revocation store (`TokenRevocationService`) is **in-memory** — lost on restart, not shared across instances; a durable / distributed store is still deferred.
2. **Refresh token = MISSING** (deferred). No refresh-token issuance / rotation; a token simply expires after the TTL. A future `REFRESH_TOKEN` table would land per [database-mapping.md §3](database-mapping.md).
3. **Rate-limiting / brute-force protection = IMPLEMENTED on the auth surface** (formerly MISSING, deferred — implemented 2026). In-memory `RateLimiter` throttles `POST /api/v1/auth/login` (key `login:<ip>`) and the credential-reveal endpoint. **Open limitation:** the limiter is **in-memory** — per-node and reset on restart; durable / distributed rate limiting is still deferred (and remains advisable before a high-traffic non-local deploy).
4. **Stale `passwordEncoder()` Javadoc** in `SecurityConfig:35-38` still says "always writes BCrypt + upgradeEncoding()" — the live encoder is verify-only SHA-256 (coexistence). Out of JWT scope; tracked under the login / change-password docs.
5. **No dedicated JWT JUnit on disk** (`JwtTokenProviderTest` / `JwtAuthenticationFilterTest` / entry-point test) — see [test-scenarios.md §2](test-scenarios.md). The revocation path is covered by `JwtRevocationTest`; the token-mechanics tests above are still MISSING.

The only open limitations are the **in-memory** revocation store and rate limiter, and the missing
refresh-token flow; the implemented token mechanics, revocation, and rate limiting are correct.
