# Authorization — Source Code Index

File map for the authorization enforcement layer. JEX paths are **read-only reference**. The
permission-code catalogue + grant resolver source live in the sibling
[menu-authorization/source-code-index.md](../menu-authorization/source-code-index.md). Frontend mirror:
[wecms-web/docs/features/auth/authorization/](../../../../../wecms-web/docs/features/auth/authorization/README.md).

---

## Refactor — backend (`wecms-api`)

### Core enforcement classes

| Concern | File | Key members |
|---|---|---|
| **The `@menuAuthz` guard** | [domain/navigation/service/MenuAuthorizationGuard.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java) — `@Component("menuAuthz")` | `canAccess(String permissionCd)` (4-step rule), `canAccessAny(String...)`, `canAccessAccountInquiry(String currency)`, `canAccessEInvoice(String type)`; constant `SETTINGS_PREFIX = "settings-"` |
| **Request-scoped identity/product context** | [common/tenant/TenantContext.java](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | `currentUserId()`, `currentBizKey()`, `currentAuthGrCd()`, `currentIsAdmin()`, `currentPrdtType()`, `currentLangCd()`; constants `AUTH_GR_ADMIN="ADMIN"` / `AUTH_GR_USER="USER"`; `record Principal(userId, userNm, bizKey, langCd, authGrCd, prdtType)`; `set(...)` overloads + `clear()` |
| **Product-type enum (gate input)** | [common/enums/PrdtType.java](../../../../src/main/java/com/kosign/wecms/common/enums/PrdtType.java) | constants `F` / `M` / `H` (`code()`, `i18nKey()`); `fromCode(String) → Optional<PrdtType>` (empty for unknown/deferred letters) |
| **Security filter chain + method security** | [config/SecurityConfig.java](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) | `@EnableMethodSecurity`; `filterChain(HttpSecurity)` — CSRF off, CORS on, `STATELESS`; permit-all `/auth/login`, `/auth/logout`, `/actuator/health`, `/payment/dispatch/callbacks/**`; `anyRequest().authenticated()`; filter order `CallbackIpWhitelistFilter → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter`; `passwordEncoder()` bean (Javadoc stale — see gap analysis) |

### Collaborators (read by the guard; owned elsewhere)

| Concern | File | Package | Used by |
|---|---|---|---|
| Per-user grant resolver | [domain/auth/service/MenuAuthorizationService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java) | `domain/auth` | step 4 — `isGranted(bizKey, userId, menuAuthCd)`; also `grantedMenuCds(bizKey, userId)` |
| Product-tree leaf validity | [domain/navigation/repository/MenuItemRepository.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/repository/MenuItemRepository.java) | `domain/navigation` | step 1 — `existsByPermissionCd(cd)` |
| Product-tree membership | [domain/navigation/repository/ProductMenuRepository.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/repository/ProductMenuRepository.java) | `domain/navigation` | step 2 — `existsInProductTree(productType, permissionCd)` (predicate join) |
| Authority source (at login) | [domain/auth/repository/BusiAdmnInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiAdmnInfmRepository.java) | `domain/auth` | `existsByBizKeyAndUserId(...)` → `authGrCd` (consumed by jwt-authentication login finalize) |
| JWT → context/authorities | [common/security/JwtAuthenticationFilter.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) | `common/security` | populates `TenantContext` + `ROLE_ADMIN`/`ROLE_USER` (jwt-authentication layer) |
| 403 mapping | [common/exception/GlobalExceptionHandler.java](../../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) | `common/exception` | `AccessDeniedException → 403 WBE1003` |

> The grant entity/repository (`USER_MENU_AUTH_INFM` → `UserMenuAuthInfm` /
> `UserMenuAuthInfmRepository`) and the navigation catalogue entities (`MenuItem`, `ProductMenu`) are
> documented in [menu-authorization/source-code-index.md](../menu-authorization/source-code-index.md) —
> this module only reads them.

### Tests

| Test | File | Covers |
|---|---|---|
| **Coverage lock-in** | [config/EndpointAuthorizationCoverageTest.java](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java) | `everyProtectedEndpointIsMenuGatedOrAllowlisted` (deny-or-declared) + `allowlistHasNoStaleEntries`; holds the `ALLOWLISTED_CONTROLLERS` set |
| **Guard unit test** | [domain/navigation/service/MenuAuthorizationGuardTest.java](../../../../src/test/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuardTest.java) | the 4-step rule (F/H admin + user, product gate, admin bypass, settings skip, unknown/blank deny) + all three overloads |

#### `ALLOWLISTED_CONTROLLERS` set (verbatim from `EndpointAuthorizationCoverageTest`)

The deny-or-declared test exempts these controllers (the "13 allow-list exceptions" — the `Set.of(...)`
literal enumerates 12 controller names across the documented buckets):

```
AuthController                  // login / logout / me / change-password  (auth/session)
UserController                  // user lookup helper (auth-adjacent)
NavigationController            // the navigation resolver itself
ScreenOptionController          // View Setting preference store (PO note Q-B)
NotificationController          // global header bell (no per-screen cd)
CountryController               // cross-screen reference data
BlockedCharsRuleController      // payment-compose validation helper
SmartOrganizerController        // /banking/smart-organizer (refactor-new, X-2)
OrganizerRuleController         // /banking/smart-organizer/rules (refactor-new, X-2)
SyncHistoryController           // /banking/sync-history (refactor-new, no cd)
BulkReceptionResultController   // /payment/dispatch/callbacks/bulk-reception-result (01301 EFS callback)
BulkExecutionResultController   // /payment/dispatch/callbacks/bulk-execution-result (01302 EFS callback)
```

> **Removed from the allow-list (audit fix 2026-06-19):** `BankCredentialController`,
> `BankScrapingController`, `EnvironmentSettingsController` — now carry real `@menuAuthz` gates and are
> enforced like any business controller (`BankCredential → settings-core-login-credentials`;
> `BankScraping → canAccessAny(login-credentials, bank-account, corporate-card-transactions)`;
> `EnvironmentSettings → canAccessAny(preference, firm-banking-settings)`).

---

## Configuration

- `@EnableMethodSecurity` (in `SecurityConfig`) — activates `@PreAuthorize` evaluation.
- No dedicated config slot for authorization; product type / authority / tenant ride in the JWT (see
  `jwt-authentication` for `wecms.jwt.*`).

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Concern | Legacy artifact |
|---|---|
| Menu-visibility / per-product menu trees | JexAdmin / Jex Studio MenuManager (`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`); role of `MENU_AUTH_ADMN_SYS` |
| Per-user grant table | `USER_MENU_AUTH_INFM` |
| Company-admin authority | `BUSI_ADMN_INFM` |
| Per-screen JSP permission checks | `*_act.jsp` page-level checks (no single funnel — replaced by `@menuAuthz`) |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/authorization/`
- Sibling permission catalogue + grants: [menu-authorization](../menu-authorization/README.md)
- Sibling authentication layer: [jwt-authentication](../jwt-authentication/README.md)
- Endpoint → cd matrix: [docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md](../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md)
- Frontend feature folder: [wecms-web/docs/features/auth/authorization/](../../../../../wecms-web/docs/features/auth/authorization/README.md)
