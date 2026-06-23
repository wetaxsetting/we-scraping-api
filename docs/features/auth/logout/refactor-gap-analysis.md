# Logout — Refactor Gap Analysis (Parity Review)

Legacy JEX (server-side `HttpSession` teardown on logout) vs. the refactor (`logout` — a stateless
public stub). Status vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** ·
**MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Logout endpoint exists** | session-invalidation action | `POST /api/v1/auth/logout` → stub `ApiResponse<Void>` "Logout successful" | **MATCH** (endpoint present) |
| **Stateless session model** | server `HttpSession` | `SessionCreationPolicy.STATELESS` (no server session) | **APPROVED DEVIATION** (architecture) |
| **Server-side session invalidation** | `HttpSession` torn down on logout | none — there is no server session to invalidate | **DEAD LEGACY** (GAP-2) |
| **Server-side token revocation / blacklist** | implicit via session drop | none — JWT valid until expiry; logout does not revoke | **MISSING** (GAP-1, deferred) |
| **Client token clear** | server-dropped session cookie | client clears `wecms_token` + `wecms_user` (WEB) | **MATCH** (effect achieved, client-side) |
| **Redirect after logout** | server-issued | frontend `router.replace("/login")` | **APPROVED DEVIATION** (SPA flow) |
| **Route protection** | session-gated | **public** (permit-all in `SecurityConfig`) | **APPROVED DEVIATION** (must succeed for an expired token) |
| **Audit row on logout** | (none in legacy either) | none | **MATCH** (nothing to port) |

---

## Specially-verified gaps

### GAP-1 — Server-side token revocation / blacklist (MISSING — deferred)

The refactor uses a stateless, signed JWT with no server-side token store. Logout does **not** revoke
the token: an issued JWT remains valid until it **expires on its own** (TTL
`wecms.jwt.expires-in-seconds`, default 3600 s). Calling logout does not shorten that window. A future
**`REFRESH_TOKEN` / blacklist table** would wire revocation into
[JwtAuthenticationFilter](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java)
so a revoked token is rejected on the next request. **Status: MISSING** (deferred; aligns with
`jwt-authentication` and `session-management`, which record refresh-token / revocation as deferred).
Owner: backend + PO (a refactor-side token table needs a baseline decision per root CLAUDE.md).

### GAP-2 — Legacy server-session invalidation (DEAD LEGACY)

Legacy JEX logged out by invalidating the server-side `HttpSession`. The refactor is
`SessionCreationPolicy.STATELESS` — it creates **no** `HttpSession`, so there is nothing to invalidate.
The behaviour is intentionally **not ported**. **Status: DEAD LEGACY.**

### Stateless model (APPROVED DEVIATION)

The whole logout shape — public stub, no server work, client-side token clear, frontend redirect — is a
direct consequence of the **stateless JWT** decision (`jwt-authentication`,
[session-management](../session-management/README.md)). This is a sanctioned architectural divergence
from the legacy server-session model. **Status: APPROVED DEVIATION.**

---

## Migration status

```
Refactor Status: complete for the in-scope feature surface (the stub endpoint);
                 server-side revocation (GAP-1) MISSING/deferred; legacy session teardown (GAP-2) DEAD LEGACY.
```

Evidence:

- **Endpoint implemented** — `AuthController.logout()` returns `ApiResponse.ok("Logout successful", null)`.
- **Public route confirmed** — `/api/v1/auth/logout` in the `SecurityConfig` permit-all list.
- **No DB footprint** — no service/repository/entity (see [database-mapping.md](database-mapping.md)).

Residual (non-blocking, tracked):

1. GAP-1 — server-side revocation when a `REFRESH_TOKEN` / blacklist table lands (PO baseline decision).
2. No dedicated logout test on disk (see [test-scenarios.md](test-scenarios.md)) — **MISSING**.

Neither residual item affects the implemented stub's correctness.
