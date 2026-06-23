# JWT Authentication — Migration Guide (JEX → Spring)

How the legacy JEX server-side session / cookie authentication became the refactor's stateless JWT.
JEX is **read-only reference**; the refactor is a clean Spring Boot rewrite, not a port of the JEX
session machinery. Login issuance → [../login/](../login/); `/me` lifecycle → [../session-management/](../session-management/).

---

## The core change: server-side session → stateless signed token

| Concern | Legacy JEX | Spring refactor |
|---|---|---|
| Identity carrier | server-side `HttpSession` + session cookie | self-contained signed JWT in `Authorization: Bearer` |
| Where session state lives | in JEX server memory (session attributes) | nowhere — the token *is* the state, signed and immutable |
| Session lookup per request | servlet container resolves the session from the cookie | `JwtAuthenticationFilter` parses + verifies the token; no lookup |
| Tenant / authority / product type | held as session attributes, mutable server-side | signed JWT claims (`bizKey`, `authGrCd`, `prdtType`), read into `TenantContext` per request |
| Session expiry | container session timeout | `exp` claim (`wecms.jwt.expires-in-seconds`, default 3600) |
| Session-expired signal | `SES001` (`jex.wabooks.custom_v2.js:107`) | `WBE1401` via `JwtAuthenticationEntryPoint` (parity) |
| Logout | server invalidates the session | client clears the stored token (no server invalidation) |
| Algorithm | n/a | HS256 (secret 32–63 B) / HS512 (≥ 64 B), chosen at startup |

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Session creation on login | JEX login establishes an `HttpSession` | `JwtTokenProvider.issue(...)` mints a signed JWT (called by the login service) |
| Per-request auth | servlet session resolution from the session cookie | `JwtAuthenticationFilter` (Bearer parse → authorities → `TenantContext`) |
| Tenant + authority state | session attributes | signed JWT claims → `TenantContext.Principal(userId, userNm, bizKey, langCd, authGrCd, prdtType)` |
| Session-expired handling | redirect / `SES001` | `JwtAuthenticationEntryPoint` → 401 `WBE1401` envelope + `X-Trace-Id` |
| Session config | container session settings | `SecurityConfig` (`STATELESS`, permit-all list, filter order, entry point) + `JwtProperties` |

---

## What changed (intentional)

| Area | Legacy | Refactor | Why |
|---|---|---|---|
| Session model | stateful `HttpSession` | stateless JWT | horizontally scalable, no server session store; CTO directive — no JEX compatibility surface in the refactor |
| Token transport | session cookie | `Authorization: Bearer` header | REST-standard; the frontend owns storage + transport |
| Claim immutability | session attributes mutable mid-session | claims signed at issue, immutable | authority/product-type change applies at next login, not in-flight |
| Tenant trust | session attribute could be reset server-side | tenant read **only** from the signed token, never a client param | tamper-evident tenant scope (`TenantContext` rule) |
| 401 vs 403 | mixed | 401 for missing/invalid token, 403 for insufficient grant (RFC 7235) | the FE 401 interceptor depends on the distinction |
| Secret handling | n/a | env `WECMS_JWT_SECRET`, fail-fast ≥ 32 bytes at startup | no hardcoded secret; misconfiguration fails boot, not the first request |

---

## Dead / out-of-scope legacy flows (not ported)

| Legacy flow | Disposition |
|---|---|
| JEX `HttpSession` server-side session state | **DEAD LEGACY** — replaced wholesale by the stateless JWT. No session attributes, no `JSESSIONID` reliance, no session store. |
| Server-side logout / session invalidation | **DEAD LEGACY** (the server step) → logout is client-only in the refactor. The corresponding refactor capability (token revocation) is **MISSING** (deferred), not ported. |
| Session-cookie auth transport | **DEAD LEGACY** — the API contract uses the `Authorization: Bearer` header. (A non-HttpOnly cookie exists on the frontend only as the edge-middleware presence check, not the API auth credential.) |

---

## Not yet built (MISSING — deferred)

| Capability | Status | Where it would land |
|---|---|---|
| Refresh token (issuance + rotation) | **MISSING** | new PO-approved `REFRESH_TOKEN` table ([database-mapping.md §3](database-mapping.md)) + rotation in `JwtAuthenticationFilter` |
| Server-side revocation / blacklist | **MISSING** | revocation store consulted by `JwtAuthenticationFilter`; would make logout invalidate an in-flight token |
| Rate-limiting / brute-force guard | **MISSING** | mandatory before any non-local deploy (`wecms-api/CLAUDE.md` "Notes for future implementation") |

---

## Schema / migrations

**None.** JWT authentication is stateless — no table, no Flyway migration. See
[database-mapping.md](database-mapping.md). All tuning is config (`wecms.jwt.*` in `application.yml`).

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full MATCH / APPROVED DEVIATION /
DEAD LEGACY / PARTIAL / MISSING classification with evidence.
