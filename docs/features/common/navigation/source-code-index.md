# Navigation — Source Code Index (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

File map. Backend paths under `wecms-api/src/main/java/com/kosign/wecms/`; frontend paths under
`wecms-web/`.

## Backend module (`domain/navigation/`)

| File | Role |
|---|---|
| `domain/navigation/controller/NavigationController.java` | `@RestController @RequestMapping("/api/v1/navigation")`; `navigation()` → `navigationService.resolve()` (`:28-31`). |
| `domain/navigation/service/NavigationService.java` | `resolve()` — `Product Tree ∩ grants` / admin full tree; `build(...)` per-node visibility + parent collapse; `labelMap(...)` / `callerLang(...)` label resolution. |
| `domain/navigation/service/MenuAuthorizationGuard.java` | Spring bean `@menuAuthz` (F-3). `canAccess` / `canAccessAny` / `canAccessAccountInquiry` / `canAccessEInvoice`. |
| `domain/navigation/dto/NavigationResponse.java` | Response record — `productType`, `menuGroupId`, `topMenus`. |
| `domain/navigation/dto/NavMenuNode.java` | One resolved node — `id`, `permissionCd`, `parentId`, `depth`, `seq`, `label`, `route`, `icon`, `legacyMnuId`, `legacyWsvcId`, `children`. |
| `domain/navigation/entity/MenuItem.java` | `@Entity @Table(MENU_ITEM)`, PK `MENU_ITEM_ID`; `isParent()` helper. |
| `domain/navigation/entity/ProductMenu.java` (+ `ProductMenuId.java`) | `@Entity @Table(PRODUCT_MENU)`, `@IdClass`, PK `(PRODUCT_TYPE, MENU_ITEM_ID)`. |
| `domain/navigation/entity/MenuItemTranslation.java` (+ `MenuItemTranslationId.java`) | `@Entity @Table(MENU_ITEM_TRANSLATION)`, `@IdClass`, PK `(PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)`. |
| `domain/navigation/repository/MenuItemRepository.java` | `findAll()` + `existsByPermissionCd(cd)`. |
| `domain/navigation/repository/ProductMenuRepository.java` | `findAllByProductType(productType)` + `existsInProductTree(productType, cd)` (JPQL join). |
| `domain/navigation/repository/MenuItemTranslationRepository.java` | `findAllByProductTypeAndLangCd(productType, langCd)`. |

## Shared dependencies (outside the module)

| File | Used for |
|---|---|
| `domain/auth/service/MenuAuthorizationService.java` | `grantedMenuCds(bizKey, userId)` (`:44`) for the `∩`; `isGranted(bizKey, userId, cd)` (`:58`) for the guard. Over `USER_MENU_AUTH_INFM`, reused unchanged. |
| `common/tenant/TenantContext.java` | `current()` / `currentPrdtType()` / `currentBizKey()` / `currentUserId()` / `currentIsAdmin()` / `AUTH_GR_ADMIN`. |
| `common/enums/PrdtType.java` | `fromCode(raw)` (F/M/H recognised; empty for the 11 deferred letters) + `code()`. |
| `common/enums/LangCd.java` | caller language → EN/KO/VI/KM. |

## Migrations

| Migration | Role |
|---|---|
| V137 / V138 / V139 | Schema — `PRODUCT_MENU` (V137) / `MENU_ITEM` (V138) / `MENU_ITEM_TRANSLATION` (V139) (refactor-owned infra, no audit columns). *(Pre-2026-06-09-renumber: `V242`; the archived file was removed with `db/archive/` on 2026-06-23.)* |
| seed (archived) | Seed — 42 menu items, 115 product-menu rows (F=41, H=33, M=41), 345 translations. Shipped as the pre-renumber `V243` (archived, then **removed with `db/archive/` 2026-06-23** — no longer in the repo); no `INSERT` in the live V137–V139 files. Removal is not missing functionality — UAT/PROD already hold this data in the legacy DB; clean-environment provisioning is out of repo scope (DBA-approved procedures, DC-2). |

## Tests

| File | Covers |
|---|---|
| `src/test/java/com/kosign/wecms/domain/navigation/service/NavigationServiceTest.java` | 8 cases (Mockito): F/M/H × admin/non-admin, out-of-tree-grant inert, no-grants empty, HS Daily-Financial split. See `test-scenarios.md`. |

## Frontend consumers (wecms-web)

| File | Role |
|---|---|
| `lib/api/navigation.ts` | `fetchNavigation()` client + snake→camel mapper (`toMenuNode` / `toNavigationTree`); `firstRouteInSubtree(node)`. |
| `hooks/useNavigation.ts` | Fetches the tree once per session, caches by `bizKey`; degrades to an empty rail on failure. |
| `hooks/useTopMenuItems.ts` | Derives the top-nav modules from the tree (depth-0 nodes) + appends Settings. |
| `lib/menuAuth.ts` | Route-guard + section-grant helpers (`isRouteAllowed`, `isSectionAllowed`, `firstAllowedRoute`) — unchanged by the tree wiring. |
| `components/layout/sidebarNav.ts` | `buildSidebarGroupsFromTree(tree, pathname, accounts)` — tree → sidebar render model (presentation registry for labelKey/icon/badge). |
| `components/layout/{Sidebar,Header,SidebarMenuItem,ProfileMenu,AppShell}.tsx` | Shell chrome consuming the tree. |

## Notes

- The catalogue is small (~42 rows) and product-independent — `NavigationService` loads `MenuItem.findAll()`
  whole once per request and joins it to `PRODUCT_MENU` membership in memory.
- `MenuItem` and `ProductMenu` have **no JPA relationship**; the product-tree join is expressed in the
  `ProductMenuRepository.existsInProductTree` JPQL predicate.
- **Migration numbering verified 2026-06-23:** the live catalogue tables are **V137** (`product_menu`),
  **V138** (`menu_item`), **V139** (`menu_item_translation`); `wb_get_next_val` is **V140**. The
  `V242`/`V243` numbers cited in earlier docs are the **pre-2026-06-09-renumber** scheme; their files were in the archived history (`db/archive/`),
  **removed 2026-06-23 (approved decision)** — no longer in the repo. The seed (`V243`) was **not** carried into the live
  `V*` set as an `INSERT`. Its removal is not missing functionality — UAT/PROD already hold the data in the legacy DB; clean-environment provisioning is out of repo scope (DBA-approved procedures, DC-2).
