# Session Management — Source Code Index

Every backend file the session lifecycle touches, with full path, one-line role, and key methods + line
citations (verified against the tree). JEX paths are read-only reference. Frontend mirror:
[wecms-web/docs/features/auth/session-management/component-index.md](../../../../../wecms-web/docs/features/auth/session-management/component-index.md).

All refactor paths are under `wecms-api/src/main/java/com/kosign/wecms/`.

---

## Refactor — backend (`wecms-api`)

### Owned by `domain/auth` (session slice)

| Layer | File | Role | Key methods (line) |
|---|---|---|---|
| Controller | [domain/auth/controller/AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java) | `@RequestMapping("/api/v1/auth")`; thin `/me` handler that delegates to the service | `me()` → `ApiResponse.ok(authService.me())` (**lines 49–52**); Javadoc notes the global `anyRequest().authenticated()` rule keeps unauthenticated callers out (lines 42–48) |
| Service | [domain/auth/service/AuthService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java) | Session rehydrate — re-validate, company lookup, menu payload | `me()` `@Transactional(readOnly = true)` (**lines 246–313**): principal/`WBE1401` gate 248–251; user re-validate 255–259; link re-check 261–267; `BUSI_INFM` `cmpn_nm`/`tax_id` 273–275; `langCd` default 277; `authGrCd` echo 283–285; `prdtType` echo 290; menu payload 296–299; `MeResponse` build 301–312. `CODE_UNAUTHENTICATED = "WBE1401"` (line 101) |
| DTO | [domain/auth/dto/MeResponse.java](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/MeResponse.java) | Current-session snapshot record (no token) | record components `userId, userNm, bizKey, langCd, cmpnNm, authGrCd, prdtType, taxId, allMenus, allowedMenuCds` (**lines 23–52**); `tax_id` re-query rationale (Javadoc 31–38); `all_menus`/`allowed_menu_cds` semantics (Javadoc 40–49) |

### Reused cross-package collaborators (not re-implemented)

| Concern | File | Key methods / facts (line) |
|---|---|---|
| Request-scoped principal | [common/tenant/TenantContext.java](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | plain `ThreadLocal<Principal>` (**line 47**); `current()` 93–95, `currentUserId()` 97–100, `currentBizKey()` 113–116, `currentAuthGrCd()` 127–130, `currentPrdtType()` 148–151; `AUTH_GR_ADMIN`/`AUTH_GR_USER` 42–45; class Javadoc warns against leaking beyond the request lifecycle / no async propagation (lines 35–38); `Principal` record (line 163) |
| Stateless security chain | [config/SecurityConfig.java](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) | `SessionCreationPolicy.STATELESS` (**line 46**); `/api/v1/auth/login` + `/logout` + `/actuator/health` + callbacks permit-all, `anyRequest().authenticated()` 47–54; `JwtAuthenticationEntryPoint` wired via `exceptionHandling` (line 58); `@EnableMethodSecurity` (line 24); JWT filter added before `UsernamePasswordAuthenticationFilter` (line 67) |
| 401 session-expired entry point | [common/security/JwtAuthenticationEntryPoint.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationEntryPoint.java) | `CODE_SESSION_EXPIRED = "WBE1401"` (**line 52**, legacy `SES001` parity per Javadoc 42–51); `commence(...)` sets HTTP 401, writes the `ApiResponse.error(WBE1401, …, path, traceId)` envelope (**lines 57–74**) |
| Menu grants (used by `me()`) | [domain/auth/service/MenuAuthorizationService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java) | `grantedMenuCds(bizKey, userId)` → `List<String>` kebab slugs (see [menu-authorization](../menu-authorization/README.md)) |
| JWT issue / parse / filter | [common/security/JwtTokenProvider.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java) + [JwtAuthenticationFilter.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) | token mechanics that populate `TenantContext` (owned by [jwt-authentication](../jwt-authentication/README.md)) |
| Error envelope | [common/exception/GlobalExceptionHandler.java](../../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) + [BusinessException.java](../../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) | maps the `me()`-thrown `BusinessException("WBE1401")` into the standard envelope |

### Config

- TTL: `wecms.jwt.expires-in-seconds` — bound by
  [config/JwtProperties.java](../../../../src/main/java/com/kosign/wecms/config/JwtProperties.java)
  (`@ConfigurationProperties("wecms.jwt")`, component `expiresInSeconds` is `@Positive` — **no default in
  the record**; the value, default **3600**, is supplied via `application.yml`). `JwtTokenProvider.expiresInSeconds()`
  echoes it.
- No session-store table, no key-generation funnel on the session path. Tables `USER_INFM` /
  `USER_BUSI_CONN_INFM` / `BUSI_INFM` are legacy inventory tables; the auth schema landed across the V1
  series + later corrective migrations — cite the table + see `src/main/resources/db/migration/` rather
  than a fixed version number (Flyway is disabled, `ddl-auto: validate`).

### Tests

| Test | File | Covers (line) |
|---|---|---|
| `AuthServiceTest` | [src/test/java/com/kosign/wecms/domain/auth/service/AuthServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/auth/service/AuthServiceTest.java) | `me_readsPrdtTypeFromJwtClaim_notFromBusiInfm` (**lines 326–346**), `me_admin_bypassesMenuFiltering_allMenusTrue` (**lines 352–372**), `me_normalUser_returnsOnlyGrantedMenus` (**lines 374–395**). **No** `me()` rejection test (deleted/inactive/locked/link-removed → `WBE1401`) exists — gap, see [test-scenarios.md](test-scenarios.md). |

---

## Legacy JEX (`WECMS DEV/`) — read-only reference

| Artifact | Path |
|---|---|
| Session bootstrap action | `…/web/WEB-INF/action/jex/wabooks/logn/logn_0001_001_r002_act.jsp` (post-login session/context load) |
| Session-expired code source | `WECMS DEV/.../jex.wabooks.custom_v2.js:107` (legacy `SES001`, parity target for `WBE1401`) |
| Entities | `WECMS DEV/WECMS/src/wabooks/entity/{UserInfm, BusiInfm, UserBusiConnInfm}.java` |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/session-management/`
- Sibling auth modules: [login](../login/README.md) · [logout](../logout/README.md) · [jwt-authentication](../jwt-authentication/README.md) · [menu-authorization](../menu-authorization/README.md) · [change-password](../change-password/README.md) · [authorization](../authorization/README.md) · [profile](../profile/README.md)
- Frontend feature folder: [wecms-web/docs/features/auth/session-management/README.md](../../../../../wecms-web/docs/features/auth/session-management/README.md)
- JEX functional reference: [docs/jex/features/login-wecms.md](../../../../../docs/jex/features/login-wecms.md)
