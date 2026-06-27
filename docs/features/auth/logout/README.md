# Auth > Logout — Backend Feature (Refactor)

**Status:** ✅ Implemented — `POST /api/v1/auth/logout` **revokes the presented token server-side**
(formerly a client-only no-op stub, deferred — implemented 2026 via F-03). The endpoint adds the
token's `jti` to the revocation denylist so the JWT stops authenticating immediately, even though it
has not yet reached its TTL.
**Module / domain:** Auth (system domain — not a WeCMS top-menu group)
**Refactor slug:** `logout` (matches the WEB counterpart)
**Wire path:** `POST /api/v1/auth/logout` — **public** (permit-all in `SecurityConfig`)
**Legacy JEX counterpart:** server-side session teardown on the JSP/WSVC session (`HttpSession`
invalidation). The refactor does **not** port a server session — see
[migration-guide.md](migration-guide.md).

> This folder is the standardized documentation home for the **logout** module. The handler is thin
> (one permit-all entry, no DB write) but it is **not** a no-op: it calls
> `AuthService.logout → tokenRevocation.revokeToken(...)` to revoke the presented JWT server-side. The
> files below keep the same structure as the other auth modules so the set reads as one product.

---

## What this feature is

Logout in the refactor is a **coordinated client + server** operation. The access token is a signed
JWT (`jwt-authentication`) that carries a `jti` claim. On logout the **client discards its own token**
(clears `localStorage` + the `wecms_token` cookie) and navigates to `/login` (the redirect is a
frontend concern) **and** the backend **revokes that token server-side** so it can no longer
authenticate.

The backend exposes `POST /api/v1/auth/logout`: it reads the presented Bearer token, and via
`AuthService.logout → TokenRevocationService.revokeToken(...)` adds the token's `jti` to the in-memory
revocation denylist, then returns `ApiResponse<Void>` with the message `"Logout successful"`. On the
next request `JwtAuthenticationFilter` rejects the revoked `jti` with 401 — the JWT no longer works
even before its TTL elapses.

> **Formerly GAP-1, deferred** — implemented 2026 via F-03. Earlier revisions documented logout as a
> client-only no-op stub with "no token invalidation, no blacklist". Server-side revocation now exists;
> the only remaining limitation is that the revocation store is **in-memory** (see Open items).

A user can:

- **Log out** — the frontend clears its token and calls `POST /api/v1/auth/logout` best-effort, which
  revokes the token server-side, then redirects to `/login`.

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 1 — `POST /api/v1/auth/logout` (see [interface-specification.md](interface-specification.md)) |
| Authority | **Public** — permit-all in `SecurityConfig` (no JWT required) |
| Tenant scope | None — the handler reads no tenant context |
| Tables written | **None** — no DB access; revocation state is held in-memory (`TokenRevocationService`) |
| Token invalidation | **Client + server** — client clears its token; server revokes the `jti` so the JWT is rejected on the next request |
| Session handling | Stateless (`SessionCreationPolicy.STATELESS`) — no `HttpSession` |
| Redirect | Frontend (`router.replace("/login")`) — not a backend concern |
| Return shape | `ApiResponse<Void>` → `{ status, data: null }`, message `"Logout successful"` |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: client clears token → POST revokes `jti` server-side → redirect; legacy classification |
| [functional-specification.md](functional-specification.md) | Endpoint behavior, server-side `jti` revocation, stateless session, redirect-is-frontend |
| [database-mapping.md](database-mapping.md) | No tables written; revocation is in-memory (`TokenRevocationService`), not a DB store |
| [interface-specification.md](interface-specification.md) | REST contract — endpoint table, empty request, response JSON, envelope |
| [source-code-index.md](source-code-index.md) | `AuthController.logout()` → `AuthService.logout` → `TokenRevocationService`; `SecurityConfig` permit-all entry (real paths) |
| [migration-guide.md](migration-guide.md) | Legacy server-session logout → client token clear + server-side `jti` revocation; what changed and why |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) |
| [test-scenarios.md](test-scenarios.md) | Minimal scenario (200) + no-dedicated-test note |

---

## Sibling auth modules

- **JWT authentication** — token issue / parse / filter chain, the stateless model logout depends on:
  [../jwt-authentication/README.md](../jwt-authentication/README.md)
- **Session management** — `GET /api/v1/auth/me`, stateless session model, server-side revocation (live)
  + refresh-token deferral: [../session-management/README.md](../session-management/README.md)
- **Login** — `POST /api/v1/auth/login` (the symmetric counterpart): [../login/README.md](../login/README.md)

## WEB counterpart

Frontend logout feature folder (token clear + best-effort POST + redirect):
[wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md)

---

## Open / residual items

1. **Server-side token revocation / blacklist** — **IMPLEMENTED** (formerly GAP-1, deferred —
   implemented 2026 via F-03). Logout revokes the presented token's `jti`; `JwtAuthenticationFilter`
   rejects revoked tokens on every request. **Open limitation:** the revocation store
   (`TokenRevocationService`) is **in-memory** — the denylist + per-user not-before cutoff are lost on
   restart and are **not** shared across multiple instances. A durable / distributed store (e.g. Redis,
   or a PO-approved table) is still required before a multi-node deploy. See
   [refactor-gap-analysis.md](refactor-gap-analysis.md) (GAP-1).
2. **Legacy server-session invalidation** — **DEAD LEGACY** (intentionally not ported; the refactor has
   no server session to invalidate — revocation is the JWT-native replacement).
3. **Refresh tokens** — still **MISSING** (deferred); a token simply expires at its TTL and re-login is
   required. A future `REFRESH_TOKEN` table is off-inventory → PO decision.
4. **No dedicated logout test** — **MISSING** (see [test-scenarios.md](test-scenarios.md)).

The in-memory revocation store and the missing refresh-token flow are the only open items; the
implemented server-side logout revocation is correct.
