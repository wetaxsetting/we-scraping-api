# Auth > Menu Authorization — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — the permission/menu catalogue (`MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION`), the per-user grant store (`USER_MENU_AUTH_INFM`) with the Settings > Users entitlement editor, the resolved navigation endpoint (`GET /api/v1/navigation`), and the grant-resolution service all evidenced on disk. `NavigationServiceTest` passes 8/8 (F/M/H admin + non-admin, out-of-tree-grant inert, no-grants empty, HS Daily-Financial split).
**Module / menu:** Auth → **Menu Authorization** (cross-cutting infrastructure — no standalone screen of its own; it powers the sidebar / top-nav for every product and the Settings > Users *Menu Entitlement* tab)
**Refactor slug:** `menu-authorization` (matches the WEB counterpart and the JEX-traceability slug)
**Wire base path:** **`GET /api/v1/navigation`** (resolved tree) + the entitlement editor under **`/api/v1/settings/users/{user_id}/menu-entitlement`** (GET / PUT)
**Legacy JEX counterpart:** the JexAdmin / Jex Studio **MenuManager** product trees (`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`), `MENU_AUTH_ADMN_SYS` (per-product default catalogue), `USER_MENU_AUTH_INFM` (per-user grant) + `OSMCMenuHelper.isPermittedMenu` (resolution / parent collapse). See the JEX functional spec under [docs/jex/features/menu-product-type-management/](../../../../../docs/jex/features/menu-product-type-management/README.md).

> This folder is the **primary backend documentation home** for Menu Authorization. It is the
> *catalogue + grant model + resolved navigation* half of the auth menu story. The
> **`@menuAuthz` enforcement guard** (the server-side `@PreAuthorize` gate on business endpoints) is
> documented in the sibling [authorization](../authorization/README.md) module — this folder
> cross-links it but does not duplicate the 4-step rule. The **resolved-tree consumption** (sidebar,
> top-nav, mobile switcher, entitlement editor UI) is the WEB counterpart.

---

## What this feature is

Menu Authorization is the **data model + resolution logic** that decides which menu nodes a session
may see. Three pieces:

1. **The permission/menu catalogue** — `MENU_ITEM` (product-independent node structure: slug,
   parent, depth, route, icon, `SHOW_YN`, `PERMISSION_CD`), `PRODUCT_MENU` (per-product membership +
   display order — where the F / M / H difference is encoded; replaces `MENU_AUTH_ADMN_SYS`), and
   `MENU_ITEM_TRANSLATION` (per-product per-language label). These are **refactor-owned
   infrastructure tables** flagged non-legacy in the inventory — they replace the lost JexAdmin
   MenuManager trees.
2. **The per-user grant store** — `USER_MENU_AUTH_INFM` (legacy table): one `USE_YN='Y'` row per
   granted kebab `menuAuthCd` slug per (tenant, user). Edited through the Settings > Users *Menu
   Entitlement* tab ([MenuEntitlementService](../../../../src/main/java/com/kosign/wecms/domain/setting/users/service/MenuEntitlementService.java),
   delete-then-insert, idempotent, retry-once, admin-only).
3. **The resolved navigation** — `GET /api/v1/navigation`
   ([NavigationService.resolve](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/NavigationService.java))
   returns `Product Tree ∩ grants` for a normal user and the full product tree for an admin, with
   parent collapse and language fallback. The grant term is resolved by
   [MenuAuthorizationService.grantedMenuCds](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java).

A tenant administrator (via Settings > Users) can:

- **Load** a user's granted menu slugs — `GET /api/v1/settings/users/{user_id}/menu-entitlement`.
- **Save** the full grant set — `PUT /api/v1/settings/users/{user_id}/menu-entitlement` (delete-then-insert
  replace; admin-only; retry-once on a PK race).

Every authenticated session can:

- **Resolve its visible navigation tree** — `GET /api/v1/navigation` (product selected from the
  signed JWT, never a client param).

---

## Catalogue + grant model (read this first)

| Concern | Source | Owner | Notes |
|---|---|---|---|
| Node structure (hierarchy / route / icon / show flag) | `MENU_ITEM` | **Refactor infra** (non-legacy) | `PERMISSION_CD` null = pure parent. `LEGACY_MNU_ID` / `LEGACY_WSVC_ID` are traceability only |
| Per-product membership + order | `PRODUCT_MENU` (PK `PRODUCT_TYPE + MENU_ITEM_ID`) | **Refactor infra** (non-legacy) | `DISP_ORD` = legacy `MNU_SEQ`; replaces `MENU_AUTH_ADMN_SYS`. F / M / H difference lives here |
| Per-product per-language label | `MENU_ITEM_TRANSLATION` (PK `PRODUCT_TYPE + MENU_ITEM_ID + LANG_CD`) | **Refactor infra** (non-legacy) | EN / KO / VI; KM falls back to EN |
| Per-user grant | `USER_MENU_AUTH_INFM` (PK `BIZ_KEY + USER_ID + WEB_SVC_ID`) | **Legacy** | `WEB_SVC_ID` repurposed to hold the kebab `menuAuthCd` slug; `USE_YN='Y'` rows only; no `REG_*` |

`PERMISSION_CD` (on `MENU_ITEM`) and `WEB_SVC_ID` (on `USER_MENU_AUTH_INFM`, the `menuAuthCd`) are the
**same kebab-slug namespace** — that identity is what makes `Product Tree ∩ grants` a simple set
intersection. Verified in the JEX
[permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md).

**Permission-code catalogue** — the authoritative list of verified live kebab `menuAuth` codes is the
JEX menu-product-type-management documentation; this folder links it rather than re-listing it.

---

## Quick facts

| Aspect | Value |
|---|---|
| Resolution rule | `Visible Menu = Product Menu Tree (by prdt_type) ∩ User grants`; admin bypasses the ∩ (full product tree, still product-scoped) |
| Product map | `F → WE_CMSVN`, `M → WE_CMSVN_LUMENS`, `H → WE_CMSVN_HAENGSUNG` (echoed as `menu_group_id`) |
| Daily-Financial split | F/M carry `banking-reports-daily-financial`; H carries `banking-reports-daily-financial-hs` |
| Product source | `TenantContext.currentPrdtType()` (signed JWT) — never a client param |
| Grant resolution | [MenuAuthorizationService.grantedMenuCds](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java) (`USE_YN='Y'` rows); `isGranted(...)` for the single-cd probe |
| Entitlement editor | delete-then-insert replace, idempotent, retry-once on PK race; admin-only → `WBE1003` |
| Language fallback | EN / KO / VI from the session; KM / null → EN |
| Parent collapse | a parent renders only when ≥ 1 child survives the filter (legacy `OSMCMenuHelper.isPermittedMenu`) |
| Enforcement guard | `@menuAuthz.canAccess('…')` — **documented in [authorization](../authorization/README.md)**; routes through this same catalogue |
| Tables | `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` (refactor infra, NOT legacy) + `USER_MENU_AUTH_INFM` (legacy) |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout

```
com.kosign.wecms.domain.navigation
├── controller/NavigationController.java        GET /api/v1/navigation (resolved tree)
├── service/
│   ├── NavigationService.java                  resolve() = Product Tree ∩ grants; parent collapse; lang fallback
│   └── MenuAuthorizationGuard.java             @menuAuthz enforcement bean (documented in ../authorization)
├── entity/
│   ├── MenuItem.java                           MENU_ITEM (refactor infra)
│   ├── ProductMenu.java  + ProductMenuId       PRODUCT_MENU (refactor infra)
│   └── MenuItemTranslation.java + …Id          MENU_ITEM_TRANSLATION (refactor infra)
├── repository/
│   ├── MenuItemRepository.java                 findAll, existsByPermissionCd
│   ├── ProductMenuRepository.java              findAllByProductType, existsInProductTree
│   └── MenuItemTranslationRepository.java      findAllByProductTypeAndLangCd
└── dto/
    ├── NavigationResponse.java                 productType, menuGroupId, topMenus
    └── NavMenuNode.java                         recursive tree node

com.kosign.wecms.domain.auth.service
└── MenuAuthorizationService.java               grantedMenuCds(bizKey,userId), isGranted(…)

com.kosign.wecms.domain.setting.users
├── service/MenuEntitlementService.java         load / save (delete-then-insert, retry-once, admin-only)
├── entity/UserMenuAuthInfm.java + …Id          USER_MENU_AUTH_INFM (legacy)
├── repository/UserMenuAuthInfmRepository.java  findAllByBizKeyAndUserId, existsBy…, deleteAllBy…
├── dto/MenuEntitlementResponse.java            userId + menuAuthCds[]
└── dto/MenuEntitlementUpdateRequest.java       menuAuthCds[] (full-set replace)
```

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: navigation resolution (intersection / admin bypass / parent collapse / product scope / lang fallback) + the entitlement load/save flow; parity narrative |
| [functional-specification.md](functional-specification.md) | Permission-code model, grant resolution, the navigation resolution algorithm, the entitlement editor flow, error surface |
| [database-mapping.md](database-mapping.md) | `USER_MENU_AUTH_INFM` (legacy) + `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` (refactor infra) columns → entity → DTO, from the real entity code |
| [interface-specification.md](interface-specification.md) | REST contract — `GET /api/v1/navigation` response JSON + the entitlement GET/PUT (reference), error-code registry, authorization |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), service/method signatures |
| [migration-guide.md](migration-guide.md) | JexAdmin MenuManager / `MENU_AUTH_ADMN_SYS` / `USER_MENU_AUTH_INFM` → refactor catalogue + navigation; what changed |
| [test-scenarios.md](test-scenarios.md) | `NavigationServiceTest`, `MenuAuthorizationServiceTest`, `MenuAuthorizationGuardTest`, `UserManagementAuthorizationTest` scenarios + gaps |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with evidence |

---

## Related & cross-linked docs

- **Enforcement guard (sibling auth module):** [../authorization/README.md](../authorization/README.md) — the
  `@menuAuthz.canAccess(...)` `@PreAuthorize` gate and its 4-step rule. It routes through *this*
  catalogue; the rule is not duplicated here.
- **Session resolver that also reads grants:** [../session-management/README.md](../session-management/README.md)
  if present — `GET /api/v1/auth/me` returns `all_menus` + `allowed_menu_cds` via the same
  `MenuAuthorizationService`.
- **WEB counterpart (resolved-tree consumption + entitlement UI):**
  [wecms-web/docs/features/auth/menu-authorization/README.md](../../../../../wecms-web/docs/features/auth/menu-authorization/README.md)
- **JEX menu / product-type catalogue (functional spec, permission-code catalogue):**
  [docs/jex/features/menu-product-type-management/](../../../../../docs/jex/features/menu-product-type-management/README.md)
  — incl. [permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md)
  and [endpoint-authorization-matrix.md](../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md).
- **Legacy table inventory (baseline):** [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)

---

## Open / residual items

1. **Per-node country gate not modelled (PARTIAL).** The legacy menu carried a per-node country gate;
   the refactor enforces the `SHOW_YN` designer gate but not the country gate (every exported node is
   VN-only and there is no per-tenant country model yet — R10). See
   [refactor-gap-analysis.md](refactor-gap-analysis.md).
2. **Catalogue seed is applied externally** (Flyway disabled, `ddl-auto: validate`, schema treated as
   external / pre-migrated). The schema for the three catalogue tables ships as the manually-run files
   `V138__menu_item.sql` / `V137__product_menu.sql` / `V139__menu_item_translation.sql`; the F / M / H
   seed rows are loaded as a DBA-run dataset, not an in-tree Flyway INSERT (see
   [database-mapping.md](database-mapping.md)).
3. **HS-tenant legacy grant remap.** A legacy H grant of `banking-reports-daily-financial` is inert
   for the H tree (whose leaf is the split `…-hs` code) — remapped by migration so the report surfaces.
   See [migration-guide.md](migration-guide.md) + the `NavigationServiceTest` HS cases.

None of the residual items affect the implemented feature's parity or correctness.
