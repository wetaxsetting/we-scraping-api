# Logout — Functional Specification (Backend)

The implemented behavior of the logout endpoint. JEX behaviour reference (read-only): server-side
`HttpSession` teardown on the legacy JSP/WSVC session. The refactor replaces that with **server-side
JWT revocation** — logout adds the presented token's `jti` to the revocation denylist so it stops
authenticating immediately.

> **Formerly a stateless no-op stub (GAP-1, deferred) — implemented 2026 via F-03.** This doc
> previously described logout as performing no server-side work, with "no blacklist / no revocation
> list". That is no longer accurate; the live behavior is documented below.

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Auth | Maps to JEX | Audit |
|---|---|---|---|---|---|---|
| 1 | Logout (server-side revoke) | POST | `/api/v1/auth/logout` | **Public** (permit-all) | server-session invalidation → JWT `jti` revocation | No |

---

## 2. Endpoint behavior

`POST /api/v1/auth/logout` reads the presented Bearer token, revokes it server-side, and returns
`ApiResponse<Void>` with message `"Logout successful"` and `data: null`. The handler delegates to
`AuthService.logout(...)`, which calls `TokenRevocationService.revokeToken(...)` to add the token's
`jti` to the denylist.

- **No request body.** The token is read from the `Authorization: Bearer` header, not the body.
- **Server-side work performed.** The presented token's `jti` is added to the revocation denylist
  (in-memory `TokenRevocationService`); subsequent requests carrying that token are rejected by
  `JwtAuthenticationFilter` with 401.
- **Always 200.** The endpoint returns success even when no/an already-expired token is presented
  (nothing to revoke) — it never fails under normal operation.
- **Public route.** Permit-all in `SecurityConfig`, so the request succeeds with or without a valid
  `Authorization: Bearer` header. A client that has already discarded its token can still call it.

---

## 3. Business rules

### 3.1 Token invalidation is server-side (revocation), coordinated with the client

The access token is a **signed JWT** carrying a `jti` claim. Logout invalidates it on **both** sides:

- **Server-side revocation (live):** `AuthService.logout → TokenRevocationService.revokeToken(...)`
  adds the presented token's `jti` to the in-memory denylist. On every subsequent request
  `JwtAuthenticationFilter` checks the `jti` (and the per-user not-before cutoff) and rejects a revoked
  or superseded token with 401 — so the JWT stops working **before** its TTL elapses.
- **Client discard:** the client also clears `wecms_token` from `localStorage` and the cookie (plus
  `wecms_user`).
- An un-revoked JWT still expires on its own at the TTL (`wecms.jwt.expires-in-seconds`, default
  3600 s); revocation is what makes logout take effect immediately rather than at TTL.

> **Formerly GAP-1, deferred — implemented 2026 via F-03.** This section previously stated there was
> "no blacklist, no revocation list" and that invalidation was client-only. Server-side revocation now
> exists. **Open limitation:** the revocation store is **in-memory** — lost on restart and not shared
> across instances; a durable / distributed store is required before a multi-node deploy. Refresh-token
> revocation remains **MISSING** (deferred). See [refactor-gap-analysis.md](refactor-gap-analysis.md)
> (GAP-1) and [session-management](../session-management/README.md).

### 3.2 Session handling is stateless

`SecurityConfig` sets `SessionCreationPolicy.STATELESS` — the application creates **no `HttpSession`**.
There is therefore **no server session to invalidate** on logout (contrast the legacy JSP/WSVC session,
which logout tore down — now **DEAD LEGACY**, see [migration-guide.md](migration-guide.md)).

### 3.3 Redirect is a frontend concern

The backend never issues a redirect. After the best-effort `POST /api/v1/auth/logout`, the **frontend**
navigates to `/login` (`router.replace("/login")`). The WEB counterpart owns this behavior:
[wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md).

---

## 4. Authorization

- **Public** — `POST /api/v1/auth/logout` is in the `SecurityConfig` permit-all list (alongside
  `/api/v1/auth/login` and `/actuator/health`). No JWT, no menu grant, no role gate.
- A missing/invalid/expired token does **not** produce a 401 on this route (it bypasses the
  `JwtAuthenticationEntryPoint` 401 path because the route is permit-all). This is intentional: a
  client whose token already expired must still be able to "log out" cleanly.

---

## 5. Audit-log expectations

**None.** Logout writes no `AUDIT_LOGS_INFM` row and no `USER_LOGIN_HIS` row. (Login writes a SUCCESS
`USER_LOGIN_HIS` row; logout has no audit counterpart in the current scope.)

---

## 6. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success — always returned (`{ status, data: null }`, message `"Logout successful"`) |

No business error is reachable on this endpoint. Full request/response shapes:
[interface-specification.md](interface-specification.md).
