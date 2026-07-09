# Logout — Source Code Index

Legacy ↔ refactor file map for the logout module. JEX paths are **read-only reference**. Frontend
counterpart: [wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md).

---

## Refactor — backend (`wecms-api`)

| Layer | File | Element |
|---|---|---|
| Controller handler | [domain/auth/controller/AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java) | `@PostMapping("/logout") logout(...)` — reads the Bearer token, delegates to `AuthService.logout(...)`, returns `ApiResponse.ok("Logout successful", null)` |
| Service | [domain/auth/service/AuthService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java) | `logout(...)` → `tokenRevocation.revokeToken(...)` — revokes the presented token's `jti` |
| Token revocation | [common/security/TokenRevocationService.java](../../../../src/main/java/com/kosign/wecms/common/security/TokenRevocationService.java) | in-memory `jti` denylist + per-user not-before cutoff; `revokeToken(...)`, `revokeAllForUser(...)` |
| Security permit-all entry | [config/SecurityConfig.java](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) | `.requestMatchers("/api/v1/auth/login", "/api/v1/auth/logout", "/actuator/health").permitAll()` — logout is the second matcher |
| Response envelope | [common/response/ApiResponse.java](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) | `ok(String message, T data)` factory used by the handler |

> **Formerly "no service, no collaborators, no DB access" (stub).** Implemented 2026 via F-03: the
> handler now delegates to `AuthService.logout → TokenRevocationService`. There is still **no
> repository, entity, or DTO** for this module, and no DB access — the revocation denylist is in-memory.

### Stateless model this module depends on (not owned here)

| Concern | File | Notes |
|---|---|---|
| Stateless session policy | [config/SecurityConfig.java](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) | `SessionCreationPolicy.STATELESS` — no `HttpSession` to invalidate |
| JWT issue / parse / filter | [common/security/JwtAuthenticationFilter.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) · [common/security/JwtTokenProvider.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java) | every JWT carries a `jti`; the filter consults `TokenRevocationService` (denylist + per-user not-before) and rejects revoked / superseded tokens — see [../jwt-authentication/README.md](../jwt-authentication/README.md) |
| Admin-triggered bulk revoke | [domain/setting/users/service/UserManagementService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/service/UserManagementService.java) | `revokeAllForUser(...)` — lock / delete / demote / password-reset revokes all of a user's tokens via `TokenRevocationService` |

### Tests

`JwtRevocationTest` exercises the revocation path
([common/security/JwtRevocationTest.java](../../../../src/test/java/com/kosign/wecms/common/security/JwtRevocationTest.java)).
No dedicated `AuthControllerLogoutTest` for the controller endpoint on disk in this pass — see
[test-scenarios.md §2](test-scenarios.md).

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Disposition |
|---|---|
| Server-side `HttpSession` invalidation on logout (JSP/WSVC session teardown) | **DEAD LEGACY** — not ported (refactor is stateless); replaced by JWT `jti` revocation. See [migration-guide.md](migration-guide.md) |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/logout/`
- Sibling modules: [../jwt-authentication/README.md](../jwt-authentication/README.md) ·
  [../session-management/README.md](../session-management/README.md) · [../login/README.md](../login/README.md)
- Frontend feature folder: [wecms-web/docs/features/auth/logout/README.md](../../../../../wecms-web/docs/features/auth/logout/README.md)
