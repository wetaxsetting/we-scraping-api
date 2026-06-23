# Auth > Logout — Backend Feature (Refactor)

**Status:** ✅ Implemented — a single public stub endpoint. Stateless JWT, so logout is a client
concern; the server endpoint exists for symmetry + a future audit/revocation hook.
**Module / domain:** Auth (system domain — not a WeCMS top-menu group)
**Refactor slug:** `logout` (matches the WEB counterpart)
**Wire path:** `POST /api/v1/auth/logout` — **public** (permit-all in `SecurityConfig`)
**Legacy JEX counterpart:** server-side session teardown on the JSP/WSVC session (`HttpSession`
invalidation). The refactor does **not** port a server session — see
[migration-guide.md](migration-guide.md).

> This folder is the standardized documentation home for the **logout** module. It is a deliberately
> thin module: one handler, one permit-all entry, no DB write, no token revocation. The files below
> keep the same structure as the other auth modules so the set reads as one product.

---

## What this feature is

Logout in the refactor is a **client-driven** operation. The access token is a stateless,
signed JWT (`jwt-authentication`): there is no server-side session and no token store, so "logging
out" means the **client discards its own token** (clears `localStorage` + the `wecms_token` cookie)
and navigates to `/login`. The redirect is a frontend concern.

The backend exposes `POST /api/v1/auth/logout` as a **stub** that always returns
`ApiResponse<Void>` with the message `"Logout successful"`. It performs **no** server-side work — no
token invalidation, no blacklist, no refresh-token revocation, no audit row. It exists so the client
has a symmetric endpoint to call (best-effort) and so a future revocation hook has a place to land.

A user can:

- **Log out** — the frontend clears its token and calls `POST /api/v1/auth/logout` best-effort
  (errors swallowed), then redirects to `/login`.

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 1 — `POST /api/v1/auth/logout` (see [interface-specification.md](interface-specification.md)) |
| Authority | **Public** — permit-all in `SecurityConfig` (no JWT required) |
| Tenant scope | None — the handler reads no tenant context |
| Tables written | **None** — stateless; no DB access |
| Token invalidation | **Client-only** — client clears its token; server does not revoke |
| Session handling | Stateless (`SessionCreationPolicy.STATELESS`) — no `HttpSession` |
| Redirect | Frontend (`router.replace("/login")`) — not a backend concern |
| Return shape | `ApiResponse<Void>` → `{ status, data: null }`, message `"Logout successful"` |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: client clears token → best-effort POST → redirect; legacy classification |
| [functional-specification.md](functional-specification.md) | Endpoint behavior, client-only invalidation, stateless session, redirect-is-frontend |
| [database-mapping.md](database-mapping.md) | No tables written (stateless); where a future revocation table would live |
| [interface-specification.md](interface-specification.md) | REST contract — endpoint table, empty request, response JSON, envelope |
| [source-code-index.md](source-code-index.md) | `AuthController.logout()` + the `SecurityConfig` permit-all entry (real paths) |
| [migration-guide.md](migration-guide.md) | Legacy server-session logout → stateless stub; what changed and why |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) |
| [test-scenarios.md](test-scenarios.md) | Minimal scenario (200) + no-dedicated-test note |

---

## Sibling auth modules

- **JWT authentication** — token issue / parse / filter chain, the stateless model logout depends on:
  [../jwt-authentication/README.md](../jwt-authentication/README.md)
- **Session management** — `GET /api/v1/auth/me`, stateless session model, refresh-token / server-side
  revocation deferral: [../session-management/README.md](../session-management/README.md)
- **Login** — `POST /api/v1/auth/login` (the symmetric counterpart): [../login/README.md](../login/README.md)

## WEB counterpart

Frontend logout feature folder (token clear + best-effort POST + redirect):
[wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md)

---

## Open / residual items

1. **Server-side token revocation / blacklist** — **MISSING** (deferred). With stateless JWTs an
   in-flight token stays valid until it expires (TTL `wecms.jwt.expires-in-seconds`, default 3600 s)
   regardless of logout. A future `REFRESH_TOKEN` / blacklist table would wire revocation into
   `JwtAuthenticationFilter`. See [refactor-gap-analysis.md](refactor-gap-analysis.md) (GAP-1).
2. **Legacy server-session invalidation** — **DEAD LEGACY** (intentionally not ported; the refactor has
   no server session to invalidate).
3. **No dedicated logout test** — **MISSING** (see [test-scenarios.md](test-scenarios.md)).

None of the residual items affect the implemented stub's correctness.
