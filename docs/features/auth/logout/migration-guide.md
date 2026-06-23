# Logout — Migration Guide (JEX → Spring)

How the legacy logout behaviour became its Spring counterpart. The short version: a server-side session
teardown became a **stateless client-side token clear** plus a no-op server stub.

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Session | `HttpSession` (per JSP/WSVC session) torn down on logout | **none** — `SecurityConfig` is `SessionCreationPolicy.STATELESS`; no server session exists |
| Logout action | server-side session invalidation | `POST /api/v1/auth/logout` → `AuthController.logout()` — a no-op stub returning `ApiResponse.ok("Logout successful", null)` |
| Token / credential clear | session cookie dropped server-side | **client clears its own JWT** (`wecms_token` localStorage + cookie, `wecms_user`) — WEB concern |
| Redirect | server redirect after invalidation | **frontend** `router.replace("/login")` |
| Route protection | session-gated | **public** — permit-all in `SecurityConfig` |

---

## Identifier translation (slug, not WSVC id)

The refactor module is the kebab slug **`logout`** — matching the WEB counterpart
(`wecms-web/docs/features/auth/logout/`). The REST path is `/api/v1/auth/logout`. No legacy WSVC id is
carried on any identifier surface; legacy session behaviour appears only as migration-traceability prose
here.

---

## What changed and why

| Area | Legacy | Refactor | Why |
|---|---|---|---|
| Session model | server `HttpSession` | stateless JWT (no server session) | Stateless auth is the refactor baseline (`jwt-authentication`). With no server session there is nothing to invalidate. **APPROVED DEVIATION** (architecture). |
| Logout work | invalidate the server session | **no server-side work** (stub) | The token lives only on the client; logout = client discards it. The endpoint stays for symmetry + a future revocation hook. |
| Token invalidation | implicit via session drop | **client-only**; server does **not** revoke the JWT | A stateless JWT stays valid until it expires (TTL `wecms.jwt.expires-in-seconds`, default 3600 s). Immediate server-side revocation is **MISSING** (deferred) — would need a `REFRESH_TOKEN` / blacklist table. |
| Redirect | server-issued | frontend (`/login`) | Standard SPA flow; the API is a pure JSON contract. |
| Route auth | session-gated | **public** (permit-all) | A client whose token already expired must still log out cleanly without a 401. |

### Coexistence / stateless rationale

The refactor runs against the live JEX DB but does **not** share the JEX server session — JEX uses
server-side `HttpSession`, the refactor uses stateless JWTs. There is therefore no cross-system session
to tear down, and no coexistence requirement that logout invalidate anything server-side. The two
systems' "logged-in" state is independent: a logout in one does not (and need not) affect the other.

---

## Dead / deferred legacy flows

| Legacy flow | Disposition |
|---|---|
| Server-side `HttpSession` invalidation on logout | **DEAD LEGACY** — intentionally not ported; refactor is stateless, no session to invalidate. |
| Immediate token revocation (an issued token stops working on logout) | **MISSING** (deferred) — future `REFRESH_TOKEN` / blacklist table wires revocation into `JwtAuthenticationFilter`. |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification + evidence, and
[session-management](../session-management/README.md) for the stateless model.

---

## Schema / key-gen migrations

**None.** Logout has no DB footprint (see [database-mapping.md](database-mapping.md)). A future
revocation table would be the only schema change, gated on a PO baseline decision (root CLAUDE.md
§"Refactor DB & Legacy-Compatibility Rules").
