# navigation — Business Flow (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

Two flows live in this module: (1) resolving the visible menu tree for `GET /api/v1/navigation`, and
(2) the server-side `@menuAuthz` gate that enforces the same rule on every business endpoint.

## 1. Menu-tree resolution (`NavigationService.resolve()`)

Source: `NavigationService.java:77-152` (build helper `:161-195`).

| Step | Action | Code |
|---|---|---|
| 1 | Read the session principal from `TenantContext.current()`. Missing `userId`/`bizKey` → `BusinessException("WBE1401")`. | `:79-82` |
| 2 | Read the **product type from the JWT** (`principal.prdtType()`) and resolve via `PrdtType.fromCode(...)`. An unknown / null product (any of the 11 deferred legacy letters, or NULL) → log a warn and return a **well-formed empty** `NavigationResponse(rawPrdt, null, [])` — the gate degrades safely, never throws. | `:85-96` |
| 3 | Load the product's membership rows: `productMenuRepo.findAllByProductType(productType)`. Empty → `NavigationResponse(productType, menuGroupId, [])`. Build `seqById` (`MENU_ITEM_ID → DISP_ORD`). | `:99-108` |
| 4 | Load the whole `MENU_ITEM` catalogue (`findAll()`), keep only nodes this product includes (intersect with `seqById`). The catalogue is small (~42 rows) and product-independent. | `:110-117` |
| 5 | Build the label map for the caller's language (`callerLang(principal)` → EN/KO/VI, else EN) overlaid on the EN fallback layer (`labelMap(productType, lang)`). | `:118-120`, `:217-240` |
| 6 | Decide admin vs. normal user: `admin = AUTH_GR_ADMIN.equals(authGrCd)`. Normal user → `grants = Set.copyOf(menuAuthService.grantedMenuCds(bizKey, userId))`; admin → empty grant set (bypassed later). | `:122-126` |
| 7 | Index children by parent, sort each sibling list by `seq` then `menuItemId`; sort roots the same way. | `:128-142` |
| 8 | For each root, `build(...)` the visible subtree; collect non-null roots into `topMenus`. | `:144-150` |
| 9 | Return `NavigationResponse(productType, MENU_GROUP.get(prdt), topMenus)`. | `:151` |

### `build(...)` — per-node visibility (`:161-195`)

- **Leaf** (no children in this product): visible iff `SHOW_YN='Y'` AND (`admin` OR `grants.contains(permissionCd)`). A grouping node that happens to have no members in this product is treated as a leaf and dropped (`isParent()` → `null`).
- **Parent**: build each child; the parent is visible iff at least one child survived AND `SHOW_YN='Y'`. **All-children-filtered → parent collapses (`null`).** Mirrors the legacy `OSMCMenuHelper.isPermittedMenu` parent rule.
- A grant for a `permission_cd` **not in the product tree** is inert — there is no node to render it on. This keeps H reduced even if an admin granted an F-only leaf.

```
JWT prdtType ──▶ PrdtType.fromCode ──▶ unknown/null ──▶ empty NavigationResponse
       │ recognised (F/M/H)
       ▼
 PRODUCT_MENU(productType) ──▶ MENU_ITEM ∩ membership ──▶ labels (caller lang / EN)
       │
       ├─ admin?  ── yes ──▶ full product tree (SHOW_YN gate only)
       └─ no ──▶ ∩ USER_MENU_AUTH_INFM grants (grantedMenuCds)
                       │
                       ▼
            collapse empty parents ──▶ topMenus (ordered by seq) ──▶ NavigationResponse
```

## 2. `@menuAuthz` endpoint enforcement (F-3)

Source: `MenuAuthorizationGuard.java:66-106`. Exposed as the Spring bean `@menuAuthz`; controllers gate a
handler with `@PreAuthorize("@menuAuthz.canAccess('<cd>')")`. The decision **mirrors the navigation
visibility rule exactly** so a menu the nav hides is an endpoint the guard denies — they cannot drift.

| Step | Rule | Code |
|---|---|---|
| 1 | **Validity** — `cd` must be a product-tree leaf (`menuItemRepo.existsByPermissionCd`) OR a `settings-*` code. Anything else → deny (fail-closed for both admin and user). | `:75-83` |
| 2 | **Product-tree gate** — a product leaf must belong to **this tenant's** product (`productMenuRepo.existsInProductTree(prdtType, cd)`). `settings-*` codes are product-invariant and skip this gate. Applies to admin and user alike, so an H caller is denied an F/M-only cd. | `:85-92` |
| 3 | **Admin bypass** — `TenantContext.currentIsAdmin()` skips the per-user grant, never the product-tree gate. | `:94-97` |
| 4 | **Per-user grant** — a normal user must hold the grant: `menuAuthService.isGranted(bizKey, userId, cd)`. | `:99-105` |

`false` from the guard → Spring raises `AccessDeniedException` → HTTP 403 / `WBE1003`. Coverage: 30 of 43
controllers are gated; the other 13 are documented allow-list exceptions (navigation, screen-options,
notifications, auth, country, actuator, etc.) — `EndpointAuthorizationCoverageTest` fails the build on any
un-annotated, non-allow-listed handler.
