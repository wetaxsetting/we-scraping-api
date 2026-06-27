# Logout — Test Scenarios (Backend)

SIT/UAT scenarios + coverage map. Frontend scenarios (token clear + redirect):
[wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md).

---

## 1. SIT / UAT scenarios

| # | Scenario | Expected |
|---|---|---|
| LO1 | `POST /api/v1/auth/logout` with a valid JWT | 200; `{ status: { error:false, code:"WBI0000", message:"Logout successful" }, data:null }`; the token's `jti` is revoked server-side |
| LO2 | `POST /api/v1/auth/logout` with **no** token (public route) | 200; same envelope — permit-all, no 401; nothing to revoke |
| LO3 | `POST /api/v1/auth/logout` with an **expired / invalid** token | 200; same envelope — route bypasses the JWT 401 path; no valid `jti` to revoke |
| LO4 | Token is rejected after logout (server-side revocation) | a JWT obtained pre-logout **no longer authenticates** other endpoints (e.g. `GET /api/v1/auth/me` → 401 `WBE1401`) after logout — `JwtAuthenticationFilter` rejects the revoked `jti`, before the TTL elapses |
| LO5 | No DB side effect | no `AUDIT_LOGS_INFM` row, no `USER_LOGIN_HIS` row written; revocation is in-memory |
| LO6 | Idempotency | calling logout twice returns the same 200 envelope; re-revoking an already-revoked `jti` is harmless |

---

## 2. Coverage notes

- **Revocation path is covered by `JwtRevocationTest`**
  ([common/security/JwtRevocationTest.java](../../../../src/test/java/com/kosign/wecms/common/security/JwtRevocationTest.java)).
  **No dedicated `AuthControllerLogoutTest` for the controller endpoint on disk** in this pass —
  **MISSING**. A `@WebMvcTest(AuthController.class)` case asserting LO1 (200 + `WBI0000` + message
  `"Logout successful"` + `data:null`) and LO2 (public route — succeeds without a token) would close
  the gap and lock the lowercase snake_case envelope shape.
- **LO4 (server-side revocation)** is the concrete demonstration that logout takes effect immediately:
  a token obtained pre-logout must be **rejected** on a protected endpoint (e.g. `GET /api/v1/auth/me`
  → 401) after logout, before natural expiry. This confirms the **implemented** server-side revocation
  ([refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-1, formerly MISSING — implemented 2026 via
  F-03). The only open item is that the revocation store is in-memory (lost on restart, per-node).
- **LO3 (public route)** confirms the `SecurityConfig` permit-all entry — best exercised end-to-end
  (the route must not return 401 for a stale token).
