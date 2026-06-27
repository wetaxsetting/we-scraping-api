# Auth Source Index — Backend + Frontend

> Consolidated index of every Auth-related source file across `wecms-api` (Spring Boot) and `wecms-web`
> (Next.js), grouped by module. Verified against the code on 2026-06-23. Per-module deep indexes live in each
> module's `source-code-index.md` (API) / `component-index.md` (WEB).
> Companion files: [auth-docs-audit.md](auth-docs-audit.md) · [auth-gap-analysis.md](auth-gap-analysis.md).

Backend base package: `com.kosign.wecms` — paths under `wecms-api/src/main/java/com/kosign/wecms/`.
Frontend paths under `wecms-web/`.

---

## login
**Backend** — `domain/auth/`
- `controller/AuthController.java` — `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/change-password`; Accept-Language→LangCd.
- `service/AuthService.java` — `login(LoginRequest, ipAddr, langCd)` (port of `SzonHelper.logn_2001_001_r001`), `me()`.
- `service/LoginHistoryService.java` — `record(...)` `@Transactional(REQUIRES_NEW)` → `USER_LOGIN_HIS`.
- `dto/LoginRequest.java`, `dto/LoginResponse.java` (`authenticated(...)` / `chooser(...)`), `dto/CompanyOption.java`.
- `entity/`: `UserInfm` (USER_INFM), `BusiInfm` (BUSI_INFM), `UserBusiConnInfm` (+`UserBusiConnInfmId`), `BusiAdmnInfm` (BUSI_ADMN_INFM), `UserLoginHis` (+`UserLoginHisId`).
- `repository/`: `UserInfmRepository` (`existsByUserId`; `findById` inherited), `BusiInfmRepository` (declares `findByDelYnAndBusiSttsAndPrdtTypeIn`; `findById`/`findAllById` inherited), `UserBusiConnInfmRepository` (`findAllByUserIdAndDelYn`, `findByUserIdAndBizKeyAndDelYn`), `BusiAdmnInfmRepository` (`existsByBizKeyAndUserId`, `findAllByUserId`, `countByBizKey`, `findAllByBizKey`), `UserLoginHisRepository`.
- Test: `domain/auth/service/AuthServiceTest.java`.

**Frontend** — `app/(auth)/login/`, `components/auth/`
- `app/(auth)/login/page.tsx`, `loginSchema.ts`.
- `components/auth/{LoginForm,CompanyChooser,FormField,IconInput,PasswordInput,MarketingPanel}.tsx`.
- `lib/auth/client.ts` — `login(...)`, `LoginResult` union, `completeLogin`/`finalizeChooser`/`cancelChooser`, `toStoredUser`, `LoginError`.
- `lib/types/api.ts` — `LoginRequestRec`, `LoginResponseRec` (dual shape), `CompanyOptionRec`.

## logout
**Backend** — `AuthController.logout()` → `AuthService.logout()` → `common/security/TokenRevocationService.revokeToken(...)` (revokes the presented JWT `jti`; **REMEDIATED via F-03** — formerly a public no-op stub) + `config/SecurityConfig.java` permit-all entry. Revocation store is in-memory (per-node). Test: `common/security/JwtRevocationTest.java`.
**Frontend** — `components/layout/ProfileMenu.tsx` (confirm dialog + `useTransition`), `lib/auth/client.logout()`, `lib/auth/storage.ts` (`tokenStorage.clear`/`userStorage.clear`; preserves locale + saved id).

## change-password
**Backend** — `domain/auth/`
- `service/ChangePasswordService.java` (port of `comm_0400_001_u001`), `dto/ChangePasswordRequest.java`, `entity/UserPwChgHis.java` (+`UserPwChgHisId`, USER_PW_CHG_HIS), `repository/UserPwChgHisRepository.java` (`findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc`).
- `common/security/WecmsPasswordEncoder.java`, `common/security/LegacyHashPasswordEncoder.java` (`Base64(SHA-256(utf8(raw)))`, `public.hash()` parity).
- `REG_SEQ_NO` via `common/legacy/LegacyFunctionRepository` → `wb_get_next_val`.
- Codes: `WBE_CHANGE_PASSWORD_MISMATCH` (WBE1052), `WBE2091` (WBE1053), `WBE_CHANGE_PASSWORD_REUSED` (WBE1017), `WBE1401`.

**Frontend** — `components/layout/ChangePasswordDialog.tsx` (inline Zod), opened from `ProfileMenu.tsx`; `lib/auth/client.changePassword(...)`; `lib/types/api.ts` `ChangePasswordRequestRec`.

## profile
**Backend** — `domain/auth/controller/UserController.java` (`GET /api/v1/users`, port of `comm_0192_001_r001`) + its `UserDirectoryService` + `TenantUserListResponse`/`TenantUserRow` DTOs. Allow-listed (no `@menuAuthz`). `USER_IMG` on `USER_BUSI_CONN_INFM`. Avatar CRUD lives in `domain/setting/users/` (`POST/GET/DELETE /api/v1/settings/users/{user_id}/image`) — out of auth scope.
**Frontend** — `components/layout/ProfileMenu.tsx` (user block, avatar [edit pencil UI-only], language submenu), `lib/i18n/LocaleProvider.tsx` (`setLocale`), `lib/auth/storage.ts` (`userStorage`/`StoredUser`). No `/profile` route.

## jwt-authentication
**Backend** — `common/security/`
- `JwtTokenProvider.java` (HS256/HS512, `issue(...)` stamps a `jti`, `parse(...)`, `expiresInSeconds()`), `JwtAuthenticationFilter.java` (parses + **checks revocation** before binding authorities), `TokenRevocationService.java` (in-memory `jti` denylist + per-user not-before cutoff; `revokeToken`/`revokeAllForUser` — **REMEDIATED via F-03**), `JwtAuthenticationEntryPoint.java` (→`WBE1401`), `config/JwtProperties.java` (`wecms.jwt.*`, env `WECMS_JWT_SECRET`), `common/tenant/TenantContext.java`. Test: `common/security/JwtRevocationTest.java`.
**Frontend** — `lib/auth/storage.ts` (`tokenStorage` — `wecms_token` localStorage + non-HttpOnly cookie `Max-Age=3600`), `lib/axios.ts` (request Bearer interceptor, `baseURL` `NEXT_PUBLIC_API_BASE_URL`).

## authorization
**Backend**
- `domain/navigation/service/MenuAuthorizationGuard.java` — `@menuAuthz` (`canAccess`, `canAccessAny`, `canAccessAccountInquiry`, `canAccessEInvoice`).
- `common/tenant/TenantContext.java` (`currentIsAdmin`, `currentPrdtType`, `AUTH_GR_ADMIN/USER`), `common/enums/PrdtType.java` (F/M/H + `fromCode`), `config/SecurityConfig.java` (`@EnableMethodSecurity`, STATELESS, permit-all set, filter order).
- Test: `config/EndpointAuthorizationCoverageTest.java` (12-name `ALLOWLISTED_CONTROLLERS`), `domain/navigation/service/MenuAuthorizationGuardTest.java`.
**Frontend** — `lib/menuAuth.ts` (`ROUTE_MENU_MAP`, `isMenuAllowed`, `isSectionAllowed`, `isRouteAllowed`, `routeMenuCds`, `firstAllowedRoute`, `isProductType`, `KNOWN_PRODUCT_TYPES`), `lib/auth/storage.ts` (`StoredUser.{allMenus,allowedMenuCds,productType}`), `app/(dashboard)/layout.tsx` (route-gate consumption).

## menu-authorization
**Backend**
- `domain/auth/service/MenuAuthorizationService.java` (`grantedMenuCds`, `isGranted`).
- `domain/navigation/controller/NavigationController.java` (`GET /api/v1/navigation`), `service/NavigationService.java` (`resolve()`), `entity/{MenuItem,ProductMenu,MenuItemTranslation}.java`, `dto/NavigationResponse.java` (+`NavMenuNode`), repos `MenuItemRepository`/`ProductMenuRepository`/`MenuItemTranslationRepository`.
- Settings>Users editor: `domain/setting/users/service/MenuEntitlementService.java`, `entity/UserMenuAuthInfm.java` (USER_MENU_AUTH_INFM), `repository/UserMenuAuthInfmRepository.java`.
- Tables `MENU_ITEM`/`PRODUCT_MENU`/`MENU_ITEM_TRANSLATION` = refactor-owned infra (V242 schema + V243 seed); `USER_MENU_AUTH_INFM` = legacy. Tests: `MenuAuthorizationServiceTest`, `NavigationServiceTest`, `UserManagementAuthorizationTest`.
- Permission-code catalogue: see `docs/jex/features/menu-product-type-management/`.
**Frontend** — `hooks/useNavigation.ts`, `hooks/useTopMenuItems.ts`, `components/layout/{Sidebar,sidebarNav,Header,topMenu,SidebarMenuItem,HeaderIcons}.tsx/ts`, `lib/api/navigation.ts`, `components/settings/users/entitlementTree.ts`.

## session-management
**Backend** — `AuthController.me()`, `AuthService.me()`, `dto/MeResponse.java` (+`all_menus`, `allowed_menu_cds`, `tax_id`), `common/tenant/TenantContext.java`, `config/SecurityConfig.java` (STATELESS), `common/security/JwtAuthenticationEntryPoint.java` (WBE1401).
**Frontend** — `lib/auth/client.ts` `fetchMe()`, `app/(dashboard)/layout.tsx` (mount guard + `isRouteAllowed`), `proxy.ts` (edge middleware: cookie presence, `/login?next=`), `lib/axios.ts` (401 response interceptor → `requestReAuth` bus → retry `_isRetry`), `SessionExpiredDialog` (+`sessionExpired.ts` bus), `lib/types/api.ts` `MeResponseRec`.

---

## Migrations (Flyway — manual-run; cite the table, confirm the file before asserting a V-number)
- Core auth schema: `USER_INFM`, `USER_BUSI_CONN_INFM`, `USER_LOGIN_HIS`, `BUSI_INFM`, `BUSI_ADMN_INFM` (+ `EMPL_INFM`, `wb_get_next_val` bootstrap). `USER_PW_CHG_HIS`. `USER_MENU_AUTH_INFM`.
- Navigation catalogue: `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` — **V242** schema, **V243** seed (42 MENU_ITEM, 115 PRODUCT_MENU [F=41/H=33/M=41], 345 translations).
- Key generation centralized in **V155** `wb_get_next_val`. Flyway is **disabled**; `ddl-auto=validate` (schema external/pre-migrated). All under `wecms-api/src/main/resources/db/migration/`.

## Error codes (auth surface)
`WBI0000` (success) · `WBE1009` (id/pw mismatch / not provisioned) · `WBE1010` (not activated) · `WBE1011` (locked) · `WBE1128` (deleted) · `WBE1401` (unauthenticated / session expired) · `WBE1003` (access denied) · `WBE2091` (current-password confirm failed) · `WBE_CHANGE_PASSWORD_MISMATCH` · `WBE_CHANGE_PASSWORD_REUSED` · `WBE_VALIDATION`.
