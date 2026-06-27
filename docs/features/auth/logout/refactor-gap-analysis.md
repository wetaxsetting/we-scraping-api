# Logout — Refactor Gap Analysis (Parity Review)

Legacy JEX (server-side `HttpSession` teardown on logout) vs. the refactor (`logout` — a public
endpoint that **revokes the presented JWT server-side**). Status vocabulary: **MATCH** ·
**APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Logout endpoint exists** | session-invalidation action | `POST /api/v1/auth/logout` → revokes the `jti`, returns `ApiResponse<Void>` "Logout successful" | **MATCH** (endpoint present + functional) |
| **Stateless session model** | server `HttpSession` | `SessionCreationPolicy.STATELESS` (no server session) | **APPROVED DEVIATION** (architecture) |
| **Server-side session invalidation** | `HttpSession` torn down on logout | none — there is no server session to invalidate; JWT `jti` revocation replaces it | **DEAD LEGACY** (GAP-2) |
| **Server-side token revocation / blacklist** | implicit via session drop | `AuthService.logout → TokenRevocationService` revokes the `jti`; `JwtAuthenticationFilter` rejects it on the next request (store is in-memory) | **IMPLEMENTED** (formerly GAP-1, deferred — implemented 2026 via F-03; open: in-memory store) |
| **Client token clear** | server-dropped session cookie | client clears `wecms_token` + `wecms_user` (WEB) | **MATCH** (effect achieved, client-side) |
| **Redirect after logout** | server-issued | frontend `router.replace("/login")` | **APPROVED DEVIATION** (SPA flow) |
| **Route protection** | session-gated | **public** (permit-all in `SecurityConfig`) | **APPROVED DEVIATION** (must succeed for an expired token) |
| **Audit row on logout** | (none in legacy either) | none | **MATCH** (nothing to port) |

---

## Specially-verified gaps

### GAP-1 — Server-side token revocation / blacklist (IMPLEMENTED — formerly MISSING, deferred)

> **Formerly GAP-1 (MISSING — deferred); implemented 2026 via F-03.**

Logout now revokes the presented token server-side. The JWT carries a `jti` (`JwtTokenProvider`);
`AuthService.logout → TokenRevocationService.revokeToken(...)` adds that `jti` to the denylist, and
[JwtAuthenticationFilter](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java)
rejects revoked / superseded tokens (the per-user not-before cutoff handles admin lock / delete /
demote / password-reset via `UserManagementService.revokeAllForUser`). A revoked token therefore stops
working on the **next** request, well before its TTL (`wecms.jwt.expires-in-seconds`, default 3600 s).

**Open limitation (still deferred):** the revocation store (`TokenRevocationService`) is **in-memory** —
the denylist + per-user cutoff are lost on restart and are **not** shared across instances. A durable /
distributed store (Redis, or a PO-approved table) is required before any multi-node deploy. A refactor-
side token table needs a baseline decision per root CLAUDE.md. **Status: IMPLEMENTED; in-memory store
open.** Owner: backend + PO.

### GAP-2 — Legacy server-session invalidation (DEAD LEGACY)

Legacy JEX logged out by invalidating the server-side `HttpSession`. The refactor is
`SessionCreationPolicy.STATELESS` — it creates **no** `HttpSession`, so there is nothing to invalidate.
The behaviour is intentionally **not ported**. **Status: DEAD LEGACY.**

### Stateless model (APPROVED DEVIATION)

The logout shape — public endpoint, server-side `jti` revocation, client-side token clear, frontend
redirect — is a direct consequence of the **stateless JWT** decision (`jwt-authentication`,
[session-management](../session-management/README.md)). Server-side state is limited to the in-memory
revocation denylist (no `HttpSession`). This is a sanctioned architectural divergence from the legacy
server-session model. **Status: APPROVED DEVIATION.**

---

## Migration status

```
Refactor Status: complete for the in-scope feature surface (endpoint + server-side jti revocation);
                 server-side revocation (GAP-1) IMPLEMENTED — open item is the in-memory store;
                 legacy session teardown (GAP-2) DEAD LEGACY.
```

Evidence:

- **Endpoint implemented + functional** — `AuthController.logout()` → `AuthService.logout` →
  `TokenRevocationService.revokeToken(...)`; returns `ApiResponse.ok("Logout successful", null)`.
- **Revocation enforced** — `JwtAuthenticationFilter` rejects revoked / superseded `jti` on every request.
- **Public route confirmed** — `/api/v1/auth/logout` in the `SecurityConfig` permit-all list.
- **No DB footprint** — revocation state is in-memory (`TokenRevocationService`); no service-owned
  repository/entity (see [database-mapping.md](database-mapping.md)).

Residual (non-blocking, tracked):

1. GAP-1 open item — the revocation store is **in-memory** (lost on restart, per-node); a durable /
   distributed store needs a PO baseline decision before any multi-node deploy.
2. Refresh tokens remain **MISSING** (deferred).
3. No dedicated logout test on disk (see [test-scenarios.md](test-scenarios.md)) — **MISSING**.

None of the residual items affect the correctness of the implemented server-side revocation.
