# JWT Authentication — Business Flow (Backend)

End-to-end narrative for the refactor's stateless JWT token mechanics. Source of truth: the Spring code
in `com.kosign.wecms.common.security` + `com.kosign.wecms.config` + `com.kosign.wecms.common.tenant`.
Login issuance flow → [../login/](../login/); `/me` session lifecycle → [../session-management/](../session-management/).

```
[Login — see ../login/]                         [Every protected request]
  AuthService.login(...)                            client
    └─ JwtTokenProvider.issue(                         │  Authorization: Bearer <token>
         sub=userId, userNm, bizKey,                   ▼
         langCd, authGrCd, prdtType)        SecurityConfig filter chain (STATELESS)
         → signed HS256/HS512 JWT              │  CallbackIpWhitelistFilter
              │                                 │        │
              ▼                                 │        ▼
  LoginResponse.access_token  ──────────►       │  JwtAuthenticationFilter
       (frontend tokenStorage)                  │        ├─ parse + verify (signature + exp)
                                                 │        ├─ authorities ROLE_ADMIN / ROLE_USER
                                                 │        ├─ TenantContext.set(...)
                                                 │        └─ finally → TenantContext.clear()
                                                 │             │ (valid)         │ (invalid/expired/missing)
                                                 ▼             ▼                 ▼
                                       UsernamePasswordAuthFilter   controller +   JwtAuthenticationEntryPoint
                                                            domain services        → 401 WBE1401 envelope + X-Trace-Id
```

---

## 1. Issuance (driven by the login module)

A JWT is minted exactly once per successful login, by the **login** service calling
`JwtTokenProvider.issue(userId, userNm, bizKey, langCd, authGrCd, prdtType)`:

- The algorithm is fixed at construction by the secret length — **HS256** for a 32–63-byte secret, **HS512** for ≥ 64 bytes.
- Claims pin the session: `sub`=userId, plus `userNm`, `bizKey` (the resolved tenant), `langCd`, `authGrCd` (normalized to `ADMIN`/`USER`), and `prdtType` (verbatim DB letter, omitted when null).
- The token is signed and self-contained — **no DB row** is created. The login response carries it as `access_token`.

The credential check + multi-company tenant resolution that *precede* this call belong to the login
module ([../login/](../login/)); this folder owns only the `issue(...)` contract.

## 2. Transport

The frontend persists the token and replays it on every request as `Authorization: Bearer <token>`.
The backend's API contract reads only this header — there is no server-side session to look up.

## 3. Parse + authorize (per request)

`JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter` on every request:

1. If there is no `Bearer ` header, the filter does nothing and the request proceeds unauthenticated (and hits the entry point if the matcher is protected).
2. Otherwise it parses + cryptographically verifies the token (signature + expiry). A bad token throws `JwtException`, the filter clears the `SecurityContext`, and the request continues unauthenticated.
3. On a valid token it derives Spring authorities (`ROLE_ADMIN`/`ROLE_USER` from `authGrCd`), sets the `SecurityContext`, and populates the request-scoped `TenantContext` with `(userId, userNm, bizKey, langCd, authGrCd, prdtType)`.
4. `TenantContext` is **always cleared in a `finally`** so a pooled thread never carries one request's tenant into the next.

Downstream authorization (`@menuAuthz` / `hasRole`) and tenant scoping read what this filter set — see
[../authorization/](../authorization/).

## 4. The 401 path

A missing / invalid / expired token never reaches a controller for a protected endpoint.
`JwtAuthenticationEntryPoint.commence(...)` returns HTTP **401** (not 403) with the standard envelope,
code **`WBE1401`** (parity legacy `SES001`), the request `path`, a `trace_id`, and the `X-Trace-Id`
response header. The frontend's Axios interceptor watches for 401 to raise the session-expired re-auth
dialog (see [../session-management/](../session-management/)).

## 5. Statelessness

- `SessionCreationPolicy.STATELESS` — no `HttpSession`, no `JSESSIONID`.
- Token validity is purely a function of signature + `exp`. There is no server-side store to consult, invalidate, or revoke.
- A token is valid until it expires (`wecms.jwt.expires-in-seconds`, default 1 h). Authority / product-type changes apply only at the **next** login because the claims are signed.

---

## Legacy parity classification (use exact gap vocabulary)

| Behaviour | Legacy JEX | Refactor | Status |
|---|---|---|---|
| **Session model** | server-side `HttpSession` (in-memory session state + session cookie) | stateless signed JWT, `SessionCreationPolicy.STATELESS` | **APPROVED DEVIATION** — clean Spring rewrite; no JEX session compatibility surface in the refactor (`wecms-api/CLAUDE.md` API-naming directive). The JEX `HttpSession` itself is **DEAD LEGACY** (intentionally not ported). |
| **Session-expired signal** | `SES001` (`jex.wabooks.custom_v2.js:107`) | `WBE1401` via `JwtAuthenticationEntryPoint` | **MATCH** (behavioural parity — 401 + dedicated session-expired code) |
| **Refresh token** | (legacy renewed the server session implicitly) | none — token expires; re-login required | **MISSING** (deferred; future `REFRESH_TOKEN` table) |
| **Server-side revocation / logout invalidation** | session invalidated server-side on logout | none — logout is client-only (clears stored token); JWT stays valid until `exp` | **MISSING** (no blacklist/revocation store) |
| **Rate-limiting / brute-force guard** | (n/a to token layer) | none | **MISSING** (mandatory before non-local deploy) |
| **HS256/HS512 by secret length** | n/a | chosen at construction (`JwtTokenProvider:43`) | **MATCH (design)** — intentional, fail-fast secret rules |
| **Tenant binding from signed token only** | session held tenant server-side | `TenantContext` from JWT claims; never a client param | **MATCH** (intent preserved) |

> The stateless choice is the architectural pivot: it removes the JEX `HttpSession` (DEAD LEGACY) in
> favour of a signed, self-contained token (APPROVED DEVIATION), at the cost of refresh + revocation
> (both MISSING and deferred). Full evidence table: [refactor-gap-analysis.md](refactor-gap-analysis.md).
