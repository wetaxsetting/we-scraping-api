# Menu Authorization — Functional Specification (Backend)

The implemented behaviour of the Menu Authorization backend: the permission-code model, grant
resolution, the navigation resolution algorithm, and the Settings > Users entitlement editor flow.
JEX behaviour reference (read-only): the JexAdmin MenuManager trees + `MENU_AUTH_ADMN_SYS` +
`USER_MENU_AUTH_INFM` + `OSMCMenuHelper.isPermittedMenu`, documented under
[docs/jex/features/menu-product-type-management/](../../../../../docs/jex/features/menu-product-type-management/README.md).

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Maps to JEX | Authz |
|---|---|---|---|---|---|
| 1 | Resolve visible navigation tree | GET | `/api/v1/navigation` | MenuManager tree ∩ `USER_MENU_AUTH_INFM`, `OSMCMenuHelper.isPermittedMenu` | JWT only (allow-listed) |
| 2 | Load a user's menu grants | GET | `/api/v1/settings/users/{user_id}/menu-entitlement` | `USER_MENU_AUTH_INFM` read | `hasRole('ADMIN')` + `@menuAuthz.canAccess('settings-core-users')` |
| 3 | Save a user's menu grants | PUT | `/api/v1/settings/users/{user_id}/menu-entitlement` | `USER_MENU_AUTH_INFM` upsert (Settings > Users) | `hasRole('ADMIN')` + `@menuAuthz.canAccess('settings-core-users')` |

The grant-resolution primitives `MenuAuthorizationService.grantedMenuCds(...)` / `isGranted(...)` are
internal (no endpoint) — consumed by `NavigationService`, `MenuAuthorizationGuard`, and `AuthService.me()`.

**Enforcement (`@menuAuthz.canAccess`) is documented in the sibling
[authorization](../authorization/README.md) module** — it routes through this catalogue but the
4-step rule is not duplicated here.

---

## 2. Permission-code model

A **permission code** (a.k.a. `menuAuthCd` / `permission_cd`) is a kebab-case slug — e.g.
`banking-vnd-account-inquiry`, `addon-einvoices-sales`, `settings-core-users`. It is the single
identity used across three surfaces:

| Surface | Column | Notes |
|---|---|---|
| Catalogue leaf | `MENU_ITEM.PERMISSION_CD` | `null` on a pure parent (grouping node); set on a leaf |
| Per-user grant | `USER_MENU_AUTH_INFM.WEB_SVC_ID` (Java `menuAuthCd`) | legacy `WEB_SVC_ID VARCHAR(50)` column repurposed to hold the slug |
| Endpoint gate | `@menuAuthz.canAccess('<slug>')` annotation literal | resolved against the same catalogue |

Because `PERMISSION_CD` and `WEB_SVC_ID` are the **same namespace** (verified live —
[permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md)),
`Product Tree ∩ grants` reduces to a string-set intersection. Two code families:

- **Product-tree leaves** — codes that appear on some `MENU_ITEM` (Banking / Payment / Add-on / Report
  pages). Subject to the product-tree gate.
- **`settings-*` codes** — Settings panels (GNB/modal, identical for F/M/H). They are **not** seeded
  into the product tree; they are authorised by grant only and skip the product-tree gate.

The authoritative permission-code catalogue is the JEX menu-product-type-management documentation —
linked, not duplicated here. **Daily-Financial split:** F/M carry `banking-reports-daily-financial`,
H carries `banking-reports-daily-financial-hs`.

---

## 3. Grant resolution (`MenuAuthorizationService`)

[MenuAuthorizationService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java)
reads `USER_MENU_AUTH_INFM` for a (tenant, user) pair:

- `grantedMenuCds(bizKey, userId)` → `List<String>` — the kebab slugs of the `USE_YN='Y'` rows only
  (`USE_YN='N'` rows are revoked → excluded). Empty list is a valid state ("no menus" landing). Order
  is the repository's natural order; callers do not rely on it.
- `isGranted(bizKey, userId, menuAuthCd)` → `boolean` — single-cd probe
  (`existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn(..., 'Y')`); avoids loading the whole grant set when
  the guard authorises one code.

**Admin bypass is decided by the caller, not this service.** An admin gets every menu and this
resolver is not consulted for the ∩ term — `AuthService.me()` and `NavigationService` short-circuit on
`authGrCd = ADMIN`.

---

## 4. Navigation resolution algorithm (`NavigationService.resolve`)

[NavigationService](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/NavigationService.java)
produces the resolved tree for `GET /api/v1/navigation`:

1. **Authenticated session.** Read `TenantContext.current()`. Missing `userId` / `bizKey` →
   `WBE1401` (`BusinessException`).
2. **Product gate.** `PrdtType.fromCode(rawPrdt)` — an unrecognised / null product (any of the 11
   deferred legacy letters, or a NULL row) has no navigation tree → return a well-formed **empty**
   response (`productType = rawPrdt`, `menuGroupId = null`, `topMenus = []`) rather than throwing.
   Gate degrades safely.
3. **Product membership.** `productMenuRepo.findAllByProductType(productType)` → the menu universe the
   product can ever show, with per-product `DISP_ORD`. Empty membership → empty tree (with the product's
   `menu_group_id` echoed).
4. **Catalogue join.** Load the whole `MENU_ITEM` catalogue (~42 rows, product-independent) and keep
   only the nodes this product includes (`seqById.containsKey`).
5. **Labels.** Build a `menuItemId → label` map for the caller's language overlaid on EN — see §6.
6. **Grants term.** Admin (`authGrCd = ADMIN`) → empty grant set + admin bypass flag; normal user →
   `Set.copyOf(menuAuthService.grantedMenuCds(bizKey, userId))`.
7. **Tree build (recursive `build(...)`):**
   - **Leaf visible** = `SHOW_YN='Y'` **AND** (`admin` **OR** `grants.contains(permissionCd)`).
   - **Pure parent with no children in this product** → dropped (empty parents never render).
   - **Parent visible** = at least one visible child **AND** `SHOW_YN='Y'` — **parent collapse**:
     when every child is filtered out, the parent is removed (legacy `OSMCMenuHelper.isPermittedMenu`
     parent rule).
8. **Order.** Roots and each child list are sorted by `DISP_ORD` (then `menuItemId` as a stable
   tiebreaker).
9. **Response.** `NavigationResponse(productType, menuGroupId, topMenus)` — depth-0 roots, each
   carrying nested `children`.

**Admin bypass vs. product scope.** Admin bypasses the per-user ∩ **only** — it still sees only its
own product tree, never a cross-product union. **Universe is the tree:** a grant for a `permission_cd`
not in the tenant's product tree is **inert** (no node to render) — this is what keeps the H tree
reduced even if an admin mistakenly granted an F-only leaf.

**Product map (`menu_group_id` echo):** `F → WE_CMSVN`, `M → WE_CMSVN_LUMENS`,
`H → WE_CMSVN_HAENGSUNG`. Traceability to the source tree only; it does not drive resolution.

**Daily-Financial split:** the H product tree carries the `banking-reports-daily-financial-hs` leaf
instead of the standard `banking-reports-daily-financial`; F/M carry the standard one.

### 4.1 Language fallback

`callerLang(principal)` maps the session `LangCd` → `EN` / `KO` / `VI`; anything else (`KM`, null) →
`EN`. `labelMap(productType, lang)` loads the EN translations first, then overlays the caller's own
language (when not EN). A node with no translation row at all falls back to its `menuItemId`.

---

## 5. Entitlement editor flow (`MenuEntitlementService`)

The Settings > Users *Menu Entitlement* tab edits `USER_MENU_AUTH_INFM` via
[MenuEntitlementService](../../../../src/main/java/com/kosign/wecms/domain/setting/users/service/MenuEntitlementService.java):

### 5.1 Load (`load(userId)`, read-only)

- Resolve `bizKey` from `TenantContext.currentBizKey()`.
- `ensureTenantUser(bizKey, userId)` — the target user must hold an active `USER_BUSI_CONN_INFM` link
  (`DEL_YN='N'`) → else `WBE_USERS_NOT_FOUND` (never leaks cross-tenant existence).
- Return `MenuEntitlementResponse(userId, grants)` — the `USE_YN='Y'` slugs in insertion order.

### 5.2 Save (`save(userId, req)` — delete-then-insert, idempotent, retry-once, admin-only)

- **Admin guard (defense-in-depth):** `TenantContext.currentIsAdmin()` → else `AccessDeniedException`
  (HTTP 403 / `WBE1003`). The controller is already `hasRole('ADMIN')`-gated; this blocks a non-admin
  even if the service is reached outside the annotated controller.
- **Delete-then-insert replace.** `saveInTransaction(...)` (`@Transactional`): dedup the request slugs
  (LinkedHashSet), `deleteAllByBizKeyAndUserId` + `flush()`, then re-insert one `USE_YN='Y'` row per
  slug with `MOD_ID` = actor and `MOD_DTM` = now (no `REG_*` — the legacy table has none). The full-set
  replace makes the operation **idempotent** (a missing slug = revoked).
- **Retry-once on a PK race.** `save(...)` is **not** `@Transactional`; it calls `self.saveInTransaction`
  (via an `@Autowired @Lazy` self-reference) and, on a `DataIntegrityViolationException` (two concurrent
  PUTs both delete-then-insert the same PK), **retries once in a fresh transaction**. The retry sees the
  winner's committed rows, deletes them, and re-inserts cleanly — **last-writer-wins**, the correct
  semantic for a full-set replace. (Same idiom as `ChartOfAccountsService`.)
- Returns `MenuEntitlementResponse(userId, dedupedSlugs)`.

---

## 6. Authorization summary

| Path | Gate |
|---|---|
| `GET /api/v1/navigation` | JWT only (no `@menuAuthz`; on `NavigationController`'s allow-listed controller). Tenant + product from the signed JWT |
| `GET .../menu-entitlement` | `hasRole('ADMIN') and @menuAuthz.canAccess('settings-core-users')` |
| `PUT .../menu-entitlement` | `hasRole('ADMIN') and @menuAuthz.canAccess('settings-core-users')` + service-level `currentIsAdmin()` defense-in-depth |

Missing/invalid JWT → 401 `WBE1401` (navigation) / `WBE1009` (Spring entry point). Authenticated but
unauthorized → 403 `WBE1003`.

---

## 7. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE1401` | 200 (business) | Navigation called without a populated `TenantContext` (no/invalid session) |
| `WBE_USERS_NOT_FOUND` | 200 | Entitlement target user has no active link in the tenant (merges "not found" + "wrong tenant") |
| `WBE1003` | 403 | Authenticated but lacks the role / menu grant (non-admin save, or missing `settings-core-users`) |
| `WBE1009` | 401 | JWT missing / invalid (Spring entry point) |
| `WBE_VALIDATION` | 200/400 | Bean-validation failure on the entitlement request (`menuAuthCds` null, or a slug outside 1..100 chars) |
| `WBE9999` | 500 | Unexpected server error (shared) |

Full request/response shapes: [interface-specification.md](interface-specification.md).
