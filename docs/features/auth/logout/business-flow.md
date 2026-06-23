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
      │   (2) best-effort POST (errors swallowed) ── carries no body, JWT optional
      ▼
POST /api/v1/auth/logout          ── PUBLIC (permit-all in SecurityConfig — no JWT required)
      │
      ▼
AuthController.logout()
      │   no service call · no TenantContext read · no DB access · no token revocation
      ▼
ApiResponse.ok("Logout successful", null)   →   { status, data: null }
      │
      ▼
Frontend ignores the result, then router.replace("/login")   ── redirect is client-side
```

---

## 1. The single path

```
POST /api/v1/auth/logout
  → AuthController.logout()
      return ApiResponse.ok("Logout successful", null);   // stub — no work performed
```

There is no service, no repository, no entity, and no DB touch. The handler is a one-liner that
returns the standard success envelope with `data: null` and message `"Logout successful"`.

Because the route is **permit-all** in `SecurityConfig` (alongside `/api/v1/auth/login` and
`/actuator/health`), the request is accepted whether or not it carries a valid `Authorization: Bearer`
header — a client whose token has already been cleared can still call it without a 401.

---

## 2. Why the server does nothing

The access token is a **stateless, signed JWT** (`jwt-authentication`): the server holds no session
and no token store, so there is nothing on the server to invalidate. "Logging out" is the client
discarding its own copy of the token. The endpoint exists for two reasons:

1. **Symmetry** — the frontend has a single `logout()` call to make, paired with `login()`.
2. **A future hook** — when server-side revocation lands (a `REFRESH_TOKEN` / blacklist table), this
   handler is where the revocation call attaches, without a new route.

---

## 3. Legacy classification

| Legacy behavior | Refactor disposition |
|---|---|
| **Server-side `HttpSession` invalidation** on logout (JEX JSP/WSVC session teardown) | **DEAD LEGACY** — intentionally not ported. The refactor is `SessionCreationPolicy.STATELESS`; there is no server session to invalidate. |
| **Token revocation / blacklist** so an issued token stops working immediately | **MISSING** (deferred) — a stateless JWT stays valid until it expires (TTL `wecms.jwt.expires-in-seconds`, default 3600 s) regardless of logout. A future `REFRESH_TOKEN` / blacklist table wires revocation into `JwtAuthenticationFilter`. |

See [session-management](../session-management/README.md) for the stateless model these classifications
flow from, and [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full parity matrix.

---

## Invariants

- **No tenant context.** The handler does not call `TenantContext.current*()`; logout is identity-agnostic.
- **No DB access.** No table is read or written (see [database-mapping.md](database-mapping.md)).
- **No audit row.** Unlike login (which writes `USER_LOGIN_HIS`), logout writes nothing.
- **Idempotent + side-effect-free.** Calling it twice, or with no token, returns the same 200 envelope.
- **Redirect is the frontend's job.** The backend never issues a redirect; the client navigates to
  `/login` after the best-effort call.
