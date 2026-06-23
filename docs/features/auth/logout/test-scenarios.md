# Logout — Test Scenarios (Backend)

SIT/UAT scenarios + coverage map. Frontend scenarios (token clear + redirect):
[wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md).

---

## 1. SIT / UAT scenarios

| # | Scenario | Expected |
|---|---|---|
| LO1 | `POST /api/v1/auth/logout` with a valid JWT | 200; `{ status: { error:false, code:"WBI0000", message:"Logout successful" }, data:null }` |
| LO2 | `POST /api/v1/auth/logout` with **no** token (public route) | 200; same envelope — permit-all, no 401 |
| LO3 | `POST /api/v1/auth/logout` with an **expired / invalid** token | 200; same envelope — route bypasses the JWT 401 path |
| LO4 | Token still works after logout (no server revocation) | a prior valid JWT continues to authenticate other endpoints until it expires (TTL default 3600 s) — confirms stateless / no blacklist |
| LO5 | No DB side effect | no `AUDIT_LOGS_INFM` row, no `USER_LOGIN_HIS` row written |
| LO6 | Idempotency | calling logout twice returns the same 200 envelope; no error |

---

## 2. Coverage notes

- **No dedicated `AuthControllerLogoutTest` on disk** in this pass — **MISSING**. The handler is a
  one-line constant-response stub with no service/repository/DB collaborators, so the risk is low, but
  a `@WebMvcTest(AuthController.class)` case asserting LO1 (200 + `WBI0000` + message
  `"Logout successful"` + `data:null`) and LO2 (public route — succeeds without a token) would close
  the gap and lock the lowercase snake_case envelope shape.
- **LO4 (no server revocation)** is an SIT-observable property, not a unit assertion — verify a token
  obtained pre-logout still authenticates a protected endpoint (e.g. `GET /api/v1/auth/me`) after the
  logout call, until natural expiry. This documents the **MISSING** server-side revocation
  ([refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-1), not a defect.
- **LO3 (public route)** confirms the `SecurityConfig` permit-all entry — best exercised end-to-end
  (the route must not return 401 for a stale token).
