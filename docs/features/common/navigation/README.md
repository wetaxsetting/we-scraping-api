# Common → navigation (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

The **global resolved menu tree + per-menu authorization** for the refactor. One read endpoint —
`GET /api/v1/navigation` — returns the session's product menu tree already resolved to what the caller
may see (`Product Tree ∩ USER_MENU_AUTH_INFM grants` for a normal user, the full `Product Tree` for an
admin). The product type is read from the signed JWT (`TenantContext.currentPrdtType()`) — **never a
client parameter**. The same `permission_cd`s gate every business endpoint via the `@menuAuthz`
`MenuAuthorizationGuard` (F-3).

The catalogue lives in **three refactor-owned infrastructure tables** — `MENU_ITEM`, `PRODUCT_MENU`,
`MENU_ITEM_TRANSLATION` (schema V137–V139; seed originally `V243`, archived 2026-06-09) — that replace the lost JexAdmin / Jex Studio
MenuManager product trees (`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`). They are **not legacy
JEX tables** and are flagged non-legacy in the table inventory.

## Documents index

| Doc | Purpose |
|---|---|
| [business-flow.md](business-flow.md) | login `prdtType` → product tree → `∩` grants / admin full tree → collapse empty parents → `NavigationResponse`; plus `@menuAuthz` enforcement on business endpoints. |
| [functional-specification.md](functional-specification.md) | Product scope, admin-bypass scope, out-of-tree-grant inert, Daily Financial split, translation keying by product, safe-degrade on unknown product. |
| [database-mapping.md](database-mapping.md) | The 3 tables (columns / PKs / V137–V139) + entity mapping; "refactor-owned infra, not legacy". |
| [interface-specification.md](interface-specification.md) | `GET /navigation` contract + `NavigationResponse` / `NavMenuNode` shape + the `@menuAuthz` guard methods (`canAccess` / `canAccessAny` / param-aware) + allow-list note. |
| [migration-guide.md](migration-guide.md) | JexAdmin MenuManager product trees → `MENU_ITEM` / `PRODUCT_MENU`; legacy `MENU_AUTH_ADMN_SYS` role; `permission_cd` normalization. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | MATCH / APPROVED DEVIATION / N/A vocabulary against legacy JEX. |
| [source-code-index.md](source-code-index.md) | Navigation file map + frontend hooks/components + `NavigationServiceTest`. |
| [test-scenarios.md](test-scenarios.md) | The 8 `NavigationServiceTest` cases + admin / non-admin / empty / out-of-tree / HS-split. |

## Related links

- Common module hub — [../README.md](../README.md)
- WEB twin — [../../../../../wecms-web/docs/features/common/navigation/README.md](../../../../../wecms-web/docs/features/common/navigation/README.md)
- Design ref (backend resolution) — [../../../../../docs/jex/features/menu-product-type-management/backend-navigation-implementation-design.md](../../../../../docs/jex/features/menu-product-type-management/backend-navigation-implementation-design.md)
- Design ref (endpoint gate) — [../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md](../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md)
- Design ref (`permission_cd` verify) — [../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md)
- Common audit deliverables — [../../../audits/common-source-index.md](../../../audits/modules/common-source-index.md)

## At a glance (verified)

| Aspect | Value |
|---|---|
| Package | `com.kosign.wecms.domain.navigation` |
| Endpoint | `GET /api/v1/navigation` → `ApiResponse<NavigationResponse>` (JWT-protected) |
| Controller | `NavigationController.navigation()` → `NavigationService.resolve()` (`NavigationController.java:28-31`) |
| Resolver | `NavigationService.resolve()` — `Product Tree (by prdt_type) ∩ grants`; admin = full product tree (`NavigationService.java:77-152`) |
| Product source | `TenantContext.currentPrdtType()` (signed JWT); `PrdtType.fromCode(...)` → unknown/null product = empty tree (`:85-96`) |
| `∩` resolver | `MenuAuthorizationService.grantedMenuCds(bizKey, userId)` over `USER_MENU_AUTH_INFM` (reused unchanged) |
| Admin bypass | `TenantContext.AUTH_GR_ADMIN.equals(authGrCd)` skips the per-user `∩` only, never the product scope (`:123-126`) |
| Parent collapse | a parent with zero visible children returns `null` — empty parents never render (`:161-195`) |
| Endpoint gate | `MenuAuthorizationGuard` bean `@menuAuthz` — `canAccess` / `canAccessAny` + param-aware account-inquiry / e-invoice (F-3) |
| Tables (refactor-owned infra) | `MENU_ITEM` (V138) · `PRODUCT_MENU` (+Id, V137) · `MENU_ITEM_TRANSLATION` (+Id, V139) — seed originally `V243`, archived 2026-06-09 |
| Seed counts | 42 menu items · 115 product-menu rows (F=41, H=33, M=41) · 345 translations (115 × EN/KO/VI) |
| Daily Financial split | F/M → `banking-reports-daily-financial` (rept_0015_001); H → `banking-reports-daily-financial-hs` (rept_0017_001) |
| Test | `NavigationServiceTest` — 8 cases (F/M/H × admin/non-admin, out-of-tree inert, no-grants empty, HS split) |
