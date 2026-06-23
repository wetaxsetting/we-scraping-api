# Menu Authorization — Source Code Index

Legacy ↔ refactor file map for the Menu Authorization feature (catalogue + grant model + resolved
navigation). JEX paths are **read-only reference**. Frontend mirror:
[wecms-web/docs/features/auth/menu-authorization/component-index.md](../../../../../wecms-web/docs/features/auth/menu-authorization/component-index.md).

---

## Refactor — backend (`wecms-api`)

### Navigation (`domain/navigation`)

| Layer | File | Key members |
|---|---|---|
| Controller | [controller/NavigationController.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/controller/NavigationController.java) | `@GetMapping navigation()` → `ApiResponse<NavigationResponse>` (`@RequestMapping("/api/v1/navigation")`) |
| Service | [service/NavigationService.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/NavigationService.java) | `resolve()` `@Transactional(readOnly)`; private `build(...)` (recursive, parent collapse), `toNode(...)`, `labelMap(productType, lang)`, `callerLang(principal)`; `MENU_GROUP` map |
| Enforcement guard | [service/MenuAuthorizationGuard.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java) | `@Component("menuAuthz")`; `canAccess(cd)`, `canAccessAny(cds…)`, `canAccessAccountInquiry(ccy)`, `canAccessEInvoice(type)` — **documented in [../authorization](../authorization/README.md)** |
| Entity | [entity/MenuItem.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/entity/MenuItem.java) | `@Table("MENU_ITEM")`; `isParent()` = `permissionCd == null` |
| Entity | [entity/ProductMenu.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/entity/ProductMenu.java) + `ProductMenuId` | `@Table("PRODUCT_MENU")`, `@IdClass` PK `(PRODUCT_TYPE, MENU_ITEM_ID)` |
| Entity | [entity/MenuItemTranslation.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/entity/MenuItemTranslation.java) + `MenuItemTranslationId` | `@Table("MENU_ITEM_TRANSLATION")`, `@IdClass` PK `(PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)` |
| Repository | [repository/MenuItemRepository.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/repository/MenuItemRepository.java) | `findAll()`, `existsByPermissionCd(cd)` |
| Repository | [repository/ProductMenuRepository.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/repository/ProductMenuRepository.java) | `findAllByProductType(pt)`, `existsInProductTree(pt, cd)` (`@Query` join) |
| Repository | [repository/MenuItemTranslationRepository.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/repository/MenuItemTranslationRepository.java) | `findAllByProductTypeAndLangCd(pt, langCd)` |
| DTO | [dto/NavigationResponse.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/dto/NavigationResponse.java) | record `(String productType, String menuGroupId, List<NavMenuNode> topMenus)` |
| DTO | [dto/NavMenuNode.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/dto/NavMenuNode.java) | record `(id, permissionCd, parentId, depth, seq, label, route, icon, legacyMnuId, legacyWsvcId, children)` |

### Grant resolution (`domain/auth`)

| Layer | File | Key members |
|---|---|---|
| Service | [service/MenuAuthorizationService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java) | `grantedMenuCds(bizKey, userId)` → `List<String>` (`USE_YN='Y'` slugs); `isGranted(bizKey, userId, menuAuthCd)` → `boolean`. Both `@Transactional(readOnly)` |

### Entitlement editor (`domain/setting/users`)

| Layer | File | Key members |
|---|---|---|
| Service | [service/MenuEntitlementService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/service/MenuEntitlementService.java) | `load(userId)` `@Transactional(readOnly)`; `save(userId, req)` (wrapper — admin guard + retry-once, NOT `@Transactional`); `saveInTransaction(userId, req)` `@Transactional`; `ensureTenantUser(...)`; `@Autowired @Lazy self` |
| Controller (endpoints) | [controller/UserManagementController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/controller/UserManagementController.java) | `getMenuEntitlement(user_id)` (GET `/{user_id}/menu-entitlement`), `updateMenuEntitlement(user_id, req)` (PUT) — both `@PreAuthorize("hasRole('ADMIN') and @menuAuthz.canAccess('settings-core-users')")` |
| Entity | [entity/UserMenuAuthInfm.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/entity/UserMenuAuthInfm.java) + `UserMenuAuthInfmId` | `@Table("USER_MENU_AUTH_INFM")`, `@IdClass` PK `(BIZ_KEY, USER_ID, WEB_SVC_ID)`; `WEB_SVC_ID`→`menuAuthCd`; no `BaseEntity` (mod-only) |
| Repository | [repository/UserMenuAuthInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/repository/UserMenuAuthInfmRepository.java) | `findAllByBizKeyAndUserId(...)`, `existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn(...)`, `@Modifying deleteAllByBizKeyAndUserId(...)` |
| DTO | [dto/MenuEntitlementResponse.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/dto/MenuEntitlementResponse.java) | record `(String userId, List<String> menuAuthCds)` |
| DTO | [dto/MenuEntitlementUpdateRequest.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/dto/MenuEntitlementUpdateRequest.java) | record `(@NotNull List<@Size(1,100) String> menuAuthCds)` |

### Reused cross-package collaborators

| Concern | File | Package |
|---|---|---|
| Session identity / product / tenant | [TenantContext.java](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | `common/tenant` |
| Product-type enum (`F`/`M`/`H`) | [PrdtType.java](../../../../src/main/java/com/kosign/wecms/common/enums/PrdtType.java) | `common/enums` |
| Target-user link guard | [UserBusiConnInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserBusiConnInfmRepository.java) | `domain/auth` |
| Legacy DTM converter | `LegacyDtmConverter` | `common/entity` |

### Schema / config

- `USER_MENU_AUTH_INFM` — **legacy** table (`V131__user_menu_auth_infm.sql`); in the
  [legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md) + PROD Active List.
- `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` — **refactor infra** (not legacy):
  `V138__menu_item.sql`, `V137__product_menu.sql`, `V139__menu_item_translation.sql`. Seed rows for the
  F / M / H trees are loaded as a DBA-run dataset (Flyway disabled, `ddl-auto: validate`).
- No `wb_get_next_val` branch — the catalogue is seeded externally and `USER_MENU_AUTH_INFM` uses a
  composite natural PK (no surrogate key generation).

### Tests

| File | Covers |
|---|---|
| [NavigationServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/navigation/service/NavigationServiceTest.java) | resolution algorithm — 8/8 (F/M/H admin + non-admin, out-of-tree-grant inert, no-grants empty, HS split) |
| [MenuAuthorizationServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationServiceTest.java) | `grantedMenuCds` — only `USE_YN='Y'` rows; empty when no grants |
| [MenuAuthorizationGuardTest.java](../../../../src/test/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuardTest.java) | `@menuAuthz` 4-step rule (cross-ref [../authorization](../authorization/README.md)) |
| [UserManagementAuthorizationTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/users/service/UserManagementAuthorizationTest.java) | entitlement save admin guard (`menuEntitlementSave_byNonAdmin_isDeniedBeforeAnyDbAccess`) |

---

## Refactor — frontend (`wecms-web`)

| Layer | File |
|---|---|
| Navigation hook | `hooks/useNavigation.ts` — `GET /api/v1/navigation`, cached per bizKey |
| Top-menu hook | `hooks/useTopMenuItems.ts` |
| Sidebar | `components/layout/Sidebar.tsx` + `sidebarNav.ts` (`buildSidebarGroupsFromTree`) |
| Top nav | `components/layout/Header.tsx` + `topMenu.ts` |
| Menu-auth framework | `lib/menuAuth.ts` — `allMenus` / `allowedMenuCds` helpers, `ROUTE_MENU_MAP` |
| Entitlement editor universe | `entitlementTree.ts` (generated from the nav tree; `mockMenuEntitlements.ts` removed) |

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Source |
|---|---|
| Per-product menu trees (lost source) | JexAdmin / Jex Studio **MenuManager** — `WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG` |
| Per-product default catalogue | `MENU_AUTH_ADMN_SYS` (replaced by `PRODUCT_MENU`) |
| Per-user grant | `USER_MENU_AUTH_INFM` (kept — legacy) |
| Resolution / parent collapse | `OSMCMenuHelper.isPermittedMenu` |
| Permission-code catalogue + verification | [docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md) |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/menu-authorization/`
- Enforcement guard (sibling): [../authorization/README.md](../authorization/README.md)
- Frontend feature folder: [wecms-web/docs/features/auth/menu-authorization/README.md](../../../../../wecms-web/docs/features/auth/menu-authorization/README.md)
- JEX menu / product-type spec: [docs/jex/features/menu-product-type-management/](../../../../../docs/jex/features/menu-product-type-management/README.md)
