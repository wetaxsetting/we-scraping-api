# Logout — Functional Specification (Backend)

The implemented behavior of the logout endpoint. JEX behaviour reference (read-only): server-side
`HttpSession` teardown on the legacy JSP/WSVC session. This is a thin, stateless stub.

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Auth | Maps to JEX | Audit |
|---|---|---|---|---|---|---|
| 1 | Logout (stub) | POST | `/api/v1/auth/logout` | **Public** (permit-all) | server-session invalidation (not ported) | No |

---

## 2. Endpoint behavior

`POST /api/v1/auth/logout` returns `ApiResponse<Void>` with message `"Logout successful"` and
`data: null`:

```java
@PostMapping("/logout")
public ApiResponse<Void> logout() {
    return ApiResponse.ok("Logout successful", null);
}
```

- **No request body.** The handler takes no arguments.
- **No work performed.** No service call, no `TenantContext` read, no DB access, no token revocation.
- **Always 200.** The endpoint cannot fail under normal operation — it is a constant response.
- **Public route.** Permit-all in `SecurityConfig`, so the request succeeds with or without a valid
  `Authorization: Bearer` header. A client that has already discarded its token can still call it.

---

## 3. Business rules

### 3.1 Token invalidation is client-only (no server revocation)

The access token is a **stateless, signed JWT**. The server holds no session and no token store, so
the logout endpoint performs **no server-side invalidation**:

- There is **no blacklist**, **no revocation list**, and **no refresh-token revocation**.
- An issued JWT remains valid until it **expires on its own** (TTL `wecms.jwt.expires-in-seconds`,
  default 3600 s) — calling logout does **not** shorten that lifetime.
- "Logging out" is the **client discarding its own token** (clearing `wecms_token` from `localStorage`
  and the cookie, plus `wecms_user`). The backend is not involved in that discard.

Server-side revocation is **MISSING** (deferred) — a future `REFRESH_TOKEN` / blacklist table would
wire revocation into `JwtAuthenticationFilter`. See
[refactor-gap-analysis.md](refactor-gap-analysis.md) (GAP-1) and
[session-management](../session-management/README.md).

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
