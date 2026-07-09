# JWT Authentication — Refactor Gap Analysis (Parity Review)

Legacy JEX (server-side `HttpSession` / cookie auth, `SES001` session-expired signal) vs. the refactor
stateless JWT layer (`JwtTokenProvider` + `JwtAuthenticationFilter` + `JwtAuthenticationEntryPoint` +
`SecurityConfig` + `JwtProperties` + `TenantContext`). Status vocabulary: **MATCH** ·
**APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Session model** | server-side `HttpSession` | stateless signed JWT, `SessionCreationPolicy.STATELESS` | **APPROVED DEVIATION** (architecture — clean Spring rewrite; the JEX session itself is DEAD LEGACY) |
| **Token transport** | session cookie | `Authorization: Bearer <token>` header | **APPROVED DEVIATION** (REST-standard) |
| **Session-expired signal** | `SES001` (`jex.wabooks.custom_v2.js:107`) | `WBE1401` via `JwtAuthenticationEntryPoint` | **MATCH** (behavioural parity — 401 + dedicated code) |
| **401 vs 403 semantics** | mixed | 401 missing/invalid token, 403 insufficient grant (RFC 7235) | **MATCH (design)** — required by the FE 401 interceptor |
| **Tenant binding** | session attribute (server-side) | signed JWT claim → `TenantContext`; never a client param | **MATCH** (intent preserved, stronger guarantee) |
| **Authority binding** | session attribute | `authGrCd` claim → `ROLE_ADMIN`/`ROLE_USER` | **MATCH** |
| **Product-type binding** | (legacy product gating) | `prdtType` claim (verbatim, nullable) → `currentPrdtType()` | **MATCH** |
| **HS256 / HS512 by secret length** | n/a | chosen at construction — HS256 (32–63 B), HS512 (≥ 64 B) (`JwtTokenProvider:43`) | **MATCH (design)** — intentional, fail-fast |
| **Fail-fast secret (≥ 32 bytes or boot fails)** | n/a | `JwtTokenProvider:29-44` + `JwtProperties` bean validation | **MATCH (design)** |
| **JEX `HttpSession` server-side state** | present | removed entirely | **DEAD LEGACY** (GAP-1) |
| **Server-side logout / session invalidation** | invalidates session | logout revokes the `jti`; filter rejects revoked tokens | **DEAD LEGACY** (the `HttpSession` step) + **IMPLEMENTED** (revocation, GAP-3) |
| **Session-cookie auth transport** | session cookie is the credential | Bearer header is the credential | **DEAD LEGACY** (GAP-2) |
| **Refresh token** | implicit session renewal | none — token expires; re-login required | **MISSING** (GAP-3) |
| **Server-side revocation / blacklist** | session invalidation | `TokenRevocationService` (in-memory jti denylist + per-user cutoff); filter rejects revoked / superseded tokens | **IMPLEMENTED** (formerly MISSING — GAP-3, implemented 2026 via F-03; open: in-memory store) |
| **Rate-limiting / brute-force guard** | (n/a to token layer) | in-memory `RateLimiter` on `POST /api/v1/auth/login` + credential reveal | **IMPLEMENTED** (formerly MISSING — GAP-4, implemented 2026; open: in-memory limiter) |

---

## Specially-verified gaps

### GAP-1 — JEX `HttpSession` removed (DEAD LEGACY)

The legacy server-side `HttpSession` (session attributes for identity/tenant, container-managed
timeout, `JSESSIONID`) is **intentionally not ported**. `SecurityConfig:46` sets
`SessionCreationPolicy.STATELESS` — no session is ever created. Identity + tenant + authority +
product type are carried entirely in the signed JWT claims and re-derived per request into
`TenantContext`. **Status: DEAD LEGACY** — replaced by the stateless model (APPROVED DEVIATION).

### GAP-2 — Session-cookie auth transport (DEAD LEGACY)

The API authenticates on the `Authorization: Bearer` header only (`JwtAuthenticationFilter:38-41`); the
legacy session-cookie transport is gone. (A non-HttpOnly cookie does exist on the frontend, but only as
the Next.js edge-middleware *presence* check — it is not the API credential.) **Status: DEAD LEGACY.**

### GAP-3 — Refresh token (MISSING) + server-side revocation (IMPLEMENTED)

> **Server-side revocation was formerly part of this gap (MISSING) — implemented 2026 via F-03.**

**Server-side revocation — IMPLEMENTED.** Every JWT carries a `jti` (`JwtTokenProvider`).
`TokenRevocationService` keeps an in-memory `jti` denylist + a per-user not-before cutoff; logout
revokes the presented `jti`, and admin lock / delete / demote / password-reset revoke all of a user's
tokens (`UserManagementService.revokeAllForUser`). `JwtAuthenticationFilter` rejects revoked /
superseded tokens on every request, so a token can be killed **before** its `exp`. **Open limitation:**
the store is **in-memory** — lost on restart, not shared across instances; a durable / distributed store
(Redis, or a PO-approved table) is still deferred.

**Refresh token — MISSING (deferred).** There is **no refresh token / rotation**; a token is valid until
its `exp` and re-authentication is a fresh login. A future `REFRESH_TOKEN` table would require PO
baseline approval (it is absent from the legacy inventory) — see
[database-mapping.md §3](database-mapping.md) and `wecms-api/CLAUDE.md` §"Notes for future
implementation". **Status: refresh token MISSING; revocation IMPLEMENTED (in-memory store open).**

### GAP-4 — Rate-limiting / brute-force protection (IMPLEMENTED)

> **Formerly MISSING (deferred) — implemented 2026.**

In-memory `RateLimiter` (`common/web/RateLimiter`) throttles `POST /api/v1/auth/login` (key
`login:<ip>`) and the credential-reveal endpoint; 5 consecutive login failures also auto-lock the
account (`LoginLockoutService` sets `USER_INFM.LOCK_YN='Y'` — see [../login/](../login/)). **Open
limitation:** the limiter is **in-memory** — per-node and reset on restart; durable / distributed rate
limiting is still deferred (and remains advisable before a high-traffic non-local deploy).
**Status: IMPLEMENTED (in-memory limiter open).**

### HS256/HS512 selection by secret length (MATCH — design)

`JwtTokenProvider:43` picks `HS512` when the secret is ≥ 64 bytes, else `HS256`, with a hard fail-fast
floor at 32 bytes (`:36-41`). This is **intentional design**, not a parity gap — a longer secret
auto-upgrades the MAC, and a too-short or missing secret fails application boot. **Status: MATCH
(design).**

---

## Non-blocking cleanup (not a parity gap)

- **Stale `passwordEncoder()` Javadoc** in `SecurityConfig:35-38` still claims the encoder "always writes BCrypt + flags legacy hashes for upgrade via `upgradeEncoding()`". The live `WecmsPasswordEncoder` is **verify-only SHA-256, no write-back** (coexistence). This is a password-layer comment, **out of JWT scope** — flagged here only because it sits in `SecurityConfig`. Tracked under the login / change-password docs.
- **Auth-Rules `WBE1009`-on-401 narration** in `wecms-api/CLAUDE.md` is stale — the live entry point emits `WBE1401` (`JwtAuthenticationEntryPoint:52`, backed by `messages.properties:7`). Docs in this folder follow the code.

---

## Migration status

```
Refactor Status: complete for the in-scope token mechanics
                 (issue / parse / revoke / authorize / 401 / fail-fast secret / stateless tenant binding).
                 Server-side revocation + login rate-limiting are IMPLEMENTED (in-memory stores — open).
                 Refresh token is MISSING (deferred).
```

Evidence for the implemented surface:

- **Issue + parse** — `JwtTokenProvider` (HS256/HS512 pick `:43`, claim map `:67-82`, `parse` `:94-99`).
- **Per-request bind** — `JwtAuthenticationFilter` sets authorities + `TenantContext`, clears in `finally` (`:34-96`).
- **401 envelope** — `JwtAuthenticationEntryPoint` → `WBE1401` standard envelope + `traceId` (`:57-74`).
- **Chain config** — `SecurityConfig` STATELESS + permit-all + entry point + filter order (`:42-69`).
- **Fail-fast secret** — `JwtTokenProvider:29-44` + `JwtProperties` bean validation.

Residual (non-blocking, tracked):

1. GAP-3 — **refresh token** still MISSING (deferred; PO baseline decision required for a `REFRESH_TOKEN` table).
2. GAP-3 / GAP-4 open limitation — the revocation store **and** the rate limiter are **in-memory** (per-node, reset on restart); durable / distributed versions are deferred before a multi-node deploy.
3. Stale `SecurityConfig` `passwordEncoder()` Javadoc cleanup.
4. No dedicated JWT token-mechanics JUnit on disk (the revocation path is covered by `JwtRevocationTest`) — see [test-scenarios.md §2](test-scenarios.md). MISSING.

None of the residual items affect the implemented token mechanics', revocation's, or rate limiting's correctness.
