# Logout — Business Flow (Backend)

End-to-end flow for the logout operation. Source of truth: the implemented Spring code in
[AuthController.logout()](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java)
and the permit-all entry in
[SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java).

```
User (any authenticated screen)
      │
      ▼
ProfileMenu → Logout → confirm dialog
      │   (1) client clears its own token  (wecms_token localStorage + cookie, wecms_user)
      │   (2) best-effort POST ── carries no body, presents the Bearer token
      ▼
POST /api/v1/auth/logout          ── PUBLIC (permit-all in SecurityConfig — no JWT required)
      │
      ▼
AuthController.logout()
      │   → AuthService.logout() → TokenRevocationService.revokeToken(jti)   (in-memory denylist)
      ▼
ApiResponse.ok("Logout successful", null)   →   { status, data: null }
      │
      ▼
Frontend redirects: router.replace("/login")   ── redirect is client-side
      │
      ▼
(next request with the old token) → JwtAuthenticationFilter rejects revoked jti → 401
```

---

## 1. The single path

```
POST /api/v1/auth/logout
  → AuthController.logout()
      → AuthService.logout(bearerToken)
          → TokenRevocationService.revokeToken(jti)   // adds jti to the in-memory denylist
      return ApiResponse.ok("Logout successful", null);
```

The handler reads the presented Bearer token and revokes its `jti` server-side, then returns the
standard success envelope with `data: null` and message `"Logout successful"`. No repository / entity /
DB row is involved — the revocation denylist lives in memory.

Because the route is **permit-all** in `SecurityConfig` (alongside `/api/v1/auth/login` and
`/actuator/health`), the request is accepted whether or not it carries a valid `Authorization: Bearer`
header — a client whose token has already been cleared can still call it without a 401 (there is simply
nothing to revoke in that case).

---

## 2. What the server does on logout

The access token is a **signed JWT** (`jwt-authentication`) carrying a `jti`. Logout revokes that `jti`
server-side so the token stops authenticating immediately, even though it has not reached its TTL:

1. **Revoke** — `AuthService.logout` calls `TokenRevocationService.revokeToken(...)`, adding the `jti`
   to the in-memory denylist. `JwtAuthenticationFilter` consults the denylist (and the per-user
   not-before cutoff) on every request and rejects a revoked / superseded token with 401.
2. **Client discard** — the frontend also clears its own copy of the token (symmetry with `login()`).

> **Formerly "why the server does nothing" (GAP-1, deferred) — implemented 2026 via F-03.** This
> section previously said the endpoint performed no work and existed only for symmetry + a future hook.
> Server-side revocation is now live. The store is **in-memory** (lost on restart, per-node); a durable
> / distributed store is still deferred.

---

## 3. Legacy classification

| Legacy behavior | Refactor disposition |
|---|---|
| **Server-side `HttpSession` invalidation** on logout (JEX JSP/WSVC session teardown) | **DEAD LEGACY** — intentionally not ported. The refactor is `SessionCreationPolicy.STATELESS`; there is no server session to invalidate. JWT `jti` revocation is the replacement. |
| **Token revocation / blacklist** so an issued token stops working immediately | **IMPLEMENTED** (formerly GAP-1, deferred — implemented 2026 via F-03). Logout revokes the `jti` via `TokenRevocationService`; `JwtAuthenticationFilter` rejects it on the next request. **Open:** the store is in-memory (lost on restart, per-node); durable / distributed revocation is still deferred. |
| **Refresh-token revocation** | **MISSING** (deferred) — refresh tokens are not implemented; a token simply expires at its TTL (`wecms.jwt.expires-in-seconds`, default 3600 s). A future `REFRESH_TOKEN` table is off-inventory → PO decision. |

See [session-management](../session-management/README.md) for the stateless model these classifications
flow from, and [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full parity matrix.

---

## Invariants

- **No tenant context.** The handler does not call `TenantContext.current*()`; logout reads only the
  presented token's `jti`.
- **No DB access.** No table is read or written; revocation state is in-memory (see
  [database-mapping.md](database-mapping.md)).
- **No audit row.** Unlike login (which writes `USER_LOGIN_HIS`), logout writes no row.
- **Idempotent.** Calling it twice, or with no token, returns the same 200 envelope (re-revoking an
  already-revoked `jti`, or revoking nothing, is harmless).
- **Redirect is the frontend's job.** The backend never issues a redirect; the client navigates to
  `/login` after the best-effort call.
