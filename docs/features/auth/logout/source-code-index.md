# Logout — Source Code Index

Legacy ↔ refactor file map for the logout module. JEX paths are **read-only reference**. Frontend
counterpart: [wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md).

---

## Refactor — backend (`wecms-api`)

| Layer | File | Element |
|---|---|---|
| Controller handler | [domain/auth/controller/AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java) | `@PostMapping("/logout") public ApiResponse<Void> logout()` — returns `ApiResponse.ok("Logout successful", null)` |
| Security permit-all entry | [config/SecurityConfig.java](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) | `.requestMatchers("/api/v1/auth/login", "/api/v1/auth/logout", "/actuator/health").permitAll()` — logout is the second matcher |
| Response envelope | [common/response/ApiResponse.java](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) | `ok(String message, T data)` factory used by the handler |

There is **no service, repository, entity, or DTO** for this module — the handler is a one-line stub
with no collaborators and no DB access.

### Stateless model this module depends on (not owned here)

| Concern | File | Notes |
|---|---|---|
| Stateless session policy | [config/SecurityConfig.java](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) | `SessionCreationPolicy.STATELESS` — no `HttpSession` to invalidate |
| JWT issue / parse / filter | [common/security/JwtAuthenticationFilter.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) · [common/security/JwtTokenProvider.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java) | stateless token; no revocation hook (the future home of server-side logout revocation) — see [../jwt-authentication/README.md](../jwt-authentication/README.md) |

### Tests

No dedicated `AuthControllerLogoutTest` on disk in this pass — see
[test-scenarios.md §2](test-scenarios.md).

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Disposition |
|---|---|
| Server-side `HttpSession` invalidation on logout (JSP/WSVC session teardown) | **DEAD LEGACY** — not ported (refactor is stateless); see [migration-guide.md](migration-guide.md) |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/logout/`
- Sibling modules: [../jwt-authentication/README.md](../jwt-authentication/README.md) ·
  [../session-management/README.md](../session-management/README.md) · [../login/README.md](../login/README.md)
- Frontend feature folder: [wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md)
