# Menu Authorization — Business Flow (Backend)

End-to-end request flow for navigation resolution and the entitlement editor, plus the JEX parity
narrative. Source of truth: the implemented Spring code in `com.kosign.wecms.domain.navigation`,
`com.kosign.wecms.domain.auth.service.MenuAuthorizationService`, and
`com.kosign.wecms.domain.setting.users.service.MenuEntitlementService`.

```
Authenticated session  (JWT carries bizKey + authGrCd + prdtType + langCd)
      │
      ├──────────────── render sidebar / top-nav ───────────────┐
      ▼                                                          │
GET /api/v1/navigation                                          │
  NavigationController ──► NavigationService.resolve()           │
      │                        │                                 │
      │                        ├─ TenantContext.current()  (product/identity, never a param)
      │                        ├─ PrdtType.fromCode(prdt)  (gate; unknown → empty tree)
      │                        ├─ ProductMenuRepository.findAllByProductType   (membership + DISP_ORD)
      │                        ├─ MenuItemRepository.findAll                   (catalogue structure)
      │                        ├─ MenuItemTranslationRepository.findAllBy…     (labels: lang + EN fallback)
      │                        └─ MenuAuthorizationService.grantedMenuCds      (∩ term; skipped for admin)
      ▼                                                          │
NavigationResponse { product_type, menu_group_id, top_menus[] }  ┘  (lowercase snake_case)


Tenant admin  (Settings > Users → Menu Entitlement tab)
      ▼
GET / PUT /api/v1/settings/users/{user_id}/menu-entitlement
  UserManagementController ──► MenuEntitlementService
      │                            │
      │                            ├─ TenantContext.currentBizKey() / currentUserId() / currentIsAdmin()
      │                            ├─ UserBusiConnInfmRepository  (ensureTenantUser — WBE_USERS_NOT_FOUND)
      │                            └─ UserMenuAuthInfmRepository  (read / delete-then-insert)
      ▼
MenuEntitlementResponse { user_id, menu_auth_cds[] }
```

---

## 1. Navigation resolution

```
GET /api/v1/navigation
  → NavigationService.resolve()  @Transactional(readOnly)
      1. principal = TenantContext.current()
         null userId/bizKey → WBE1401
      2. prdt = PrdtType.fromCode(principal.prdtType())
         unknown/null product → NavigationResponse(rawPrdt, null, []) — degrade safe
      3. membership = productMenuRepo.findAllByProductType(prdt.code())   (DISP_ORD per node)
         empty → NavigationResponse(productType, menuGroupId, [])
      4. itemById = menuItemRepo.findAll() ∩ membership                  (catalogue structure)
      5. labels = EN translations overlaid by caller language (KM/null → EN)
      6. admin?  authGrCd == ADMIN → grants = ∅ + bypass
         user?   grants = grantedMenuCds(bizKey, userId)                  (USE_YN='Y' slugs)
      7. recursive build():
           leaf visible   = SHOW_YN='Y' AND (admin OR grants.contains(permissionCd))
           parent visible = ≥1 visible child AND SHOW_YN='Y'   (collapse empty parents)
           order: DISP_ORD asc, then menuItemId
  → NavigationResponse { productType, menuGroupId, topMenus[] }
```

- Mirrors the legacy rule `Visible Menu = Product Menu Tree (by prdt_type) ∩ User Permission` and the
  `OSMCMenuHelper.isPermittedMenu` parent-collapse rule.
- **Product is read from the signed JWT** (`TenantContext.currentPrdtType()`) — never a request param;
  an H caller can never resolve the F tree by sending a parameter.
- **Universe is the tree.** A grant for a `permission_cd` not in the product's tree has no node to
  render — it is inert. This is what keeps H reduced even if an admin granted an F-only leaf.
- **Admin** bypasses the per-user ∩ only (full product tree, still product-scoped — no cross-product
  union).
- **Daily-Financial split:** H's tree carries `banking-reports-daily-financial-hs`; F/M carry
  `banking-reports-daily-financial`.

## 2. Grant resolution (internal)

```
MenuAuthorizationService.grantedMenuCds(bizKey, userId)   @Transactional(readOnly)
  → menuAuthRepo.findAllByBizKeyAndUserId(bizKey, userId)
      .filter(USE_YN == 'Y').map(menuAuthCd).toList()
MenuAuthorizationService.isGranted(bizKey, userId, cd)     @Transactional(readOnly)
  → existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn(bizKey, userId, cd, 'Y')
```

Consumed by `NavigationService` (∩ term), `MenuAuthorizationGuard` (`@menuAuthz`, single-cd probe),
and `AuthService.me()` (the `/me` `allowed_menu_cds`). The same product-agnostic resolver feeds all
three so the nav and the guard cannot drift.

## 3. Entitlement load

```
GET /api/v1/settings/users/{user_id}/menu-entitlement
  → MenuEntitlementService.load(userId)  @Transactional(readOnly)
      bizKey = TenantContext.currentBizKey()
      ensureTenantUser(bizKey, userId)              → WBE_USERS_NOT_FOUND on no active link
      rows = menuAuthRepo.findAllByBizKeyAndUserId(...)
      grants = rows where USE_YN='Y'  (insertion order)
  → MenuEntitlementResponse { user_id, menu_auth_cds[] }
```

## 4. Entitlement save (delete-then-insert, idempotent, retry-once, admin-only)

```
PUT /api/v1/settings/users/{user_id}/menu-entitlement   body: { menu_auth_cds: [...] }
  → MenuEntitlementService.save(userId, req)   (NOT @Transactional — wrapper)
      1. admin guard: TenantContext.currentIsAdmin() → else AccessDeniedException (403 / WBE1003)
      2. try   self.saveInTransaction(userId, req)
         catch DataIntegrityViolationException → retry once self.saveInTransaction(...)   (fresh tx)

  saveInTransaction(userId, req)  @Transactional
      bizKey/actor = TenantContext.currentBizKey()/currentUserId()
      ensureTenantUser(bizKey, userId)              → WBE_USERS_NOT_FOUND
      dedup = LinkedHashSet(req.menuAuthCds)
      menuAuthRepo.deleteAllByBizKeyAndUserId(...); flush()      ← DELETE before inserts
      for each slug: new UserMenuAuthInfm(bizKey, userId, slug, USE_YN='Y', MOD_ID=actor, MOD_DTM=now)
      saveAll(inserts)                               ← no REG_* (legacy table has none)
  → MenuEntitlementResponse { user_id, menu_auth_cds: dedup }
```

- **Idempotent full-set replace** — the request carries the complete grant set; a missing slug = revoke.
- **Concurrency** — two concurrent PUTs can both delete (seeing no rows) then both insert the same PK;
  the loser hits `user_menu_auth_infm_pkey` → `DataIntegrityViolationException`. The wrapper retries
  **once in a fresh transaction** (not the rolled-back one — hence `save` is not `@Transactional`), and
  the retry sees the winner's committed rows, deletes them, and re-inserts cleanly. Last-writer-wins.
- **Admin-only** — controller `hasRole('ADMIN')` + service `currentIsAdmin()` defense-in-depth.

---

## Tenant + identity invariants (every path)

- **Tenant + product + identity** come only from `TenantContext` (signed JWT) — never a request
  body/param. `bizKey` is the leading PK component of every `USER_MENU_AUTH_INFM` query; the navigation
  product is `TenantContext.currentPrdtType()`.
- **No menu claim in the JWT.** Grants are read fresh per `/navigation` and per `/me` so an entitlement
  change takes effect without re-issuing a token.
- **No audit rows.** Neither navigation resolution nor the entitlement save writes `AUDIT_LOGS_INFM`
  (the legacy MenuManager / `USER_MENU_AUTH_INFM` writes had no such audit either — parity).

---

## Parity classification (vs. legacy JEX)

| Behaviour | Legacy | Refactor | Status |
|---|---|---|---|
| Navigation resolution (`Product Tree ∩ grants`, admin bypass, parent collapse) | MenuManager tree + `OSMCMenuHelper.isPermittedMenu` | `NavigationService.resolve` — `NavigationServiceTest` 8/8 | **MATCH** |
| The product-tree catalogue source | JexAdmin / Jex Studio MenuManager trees + `MENU_AUTH_ADMN_SYS` | refactor-owned `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` infra tables | **APPROVED DEVIATION** — the JexAdmin tree was lost; the refactor owns the menu definition as data |
| `SHOW_YN` designer gate | enforced | enforced | **MATCH** |
| Per-node country gate | enforced per node | not modelled (no per-tenant country model yet — R10) | **MISSING / PARTIAL** |

The catalogue tables are an **APPROVED DEVIATION** rather than a parity break: the legacy menu tree
lived in JexAdmin / Jex Studio MenuManager (`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`) which
the refactor cannot depend on, so the menu definition is reproduced as refactor-owned reference data —
explicitly flagged non-legacy in the inventory. The full classification with evidence is in
[refactor-gap-analysis.md](refactor-gap-analysis.md).
