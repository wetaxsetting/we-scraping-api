# navigation — Refactor Gap Analysis (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

Legacy JEX menu/permission behaviour vs. the refactor navigation module. Vocabulary:
**MATCH** · **APPROVED DEVIATION** · **N/A** · **PARTIAL**.

| Aspect | Verdict | Detail |
|---|---|---|
| `Visible Menu = Product Tree ∩ grants` | **MATCH** | Implemented in `NavigationService.resolve()` / `build(...)` (`:77-195`); admin = full product tree, normal user `∩ USER_MENU_AUTH_INFM`. |
| Product tree selected by `prdt_type` | **MATCH** | From `TenantContext.currentPrdtType()` (signed JWT), never a client param (`:85`). |
| Admin bypass scope | **MATCH** | `AUTH_GR_ADMIN` skips the per-user `∩` only, never the product scope (`:122-126`). Legacy admin saw the product default tree, not a cross-product union. |
| Parent collapse on empty children | **MATCH** | `build(...)` returns `null` for a parent with no visible child (`:161-195`) — legacy `OSMCMenuHelper.isPermittedMenu`. |
| Out-of-tree grant inert | **MATCH** | A grant for a cd not in the product tree has no node to render (`:48-51`). Verified by `hNonAdmin_grantOutsideProductTree_isInert`. |
| Per-user grant resolver reused unchanged | **MATCH** | `MenuAuthorizationService.grantedMenuCds(...)` — the same `/auth/me` resolver over `USER_MENU_AUTH_INFM`. |
| Server-side per-menu enforcement (F-3) | **MATCH** | `@menuAuthz` gates 30/43 controllers with the identical rule; nav-hidden menu = guard-denied endpoint, cannot drift. |
| Menu catalogue as refactor-owned tables | **APPROVED DEVIATION** | `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` are refactor-owned infra (PO-approved off-inventory), replacing the lost JexAdmin MenuManager trees. Not legacy JEX tables, not in the PROD Active List. |
| `MENU_AUTH_ADMN_SYS` not migrated to an entity | **APPROVED DEVIATION** | Its per-product default-catalogue role is taken over by `PRODUCT_MENU`. |
| `menuGroupId` traceability echo | **APPROVED DEVIATION** | Refactor-added echo (`WE_CMSVN` / `_LUMENS` / `_HAENGSUNG`) for source traceability; does not drive resolution. |
| Translation keyed by product | **APPROVED DEVIATION** | `(PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)` deliberately refines the design doc's `(MENU_ITEM_ID, LANG_CD)` sketch — M's VI/KO diverge from F. |
| Identifier surface = slugs, not WSVC ids | **APPROVED DEVIATION** | `permission_cd` / node id / route are kebab slugs; legacy ids survive only in `LEGACY_MNU_ID` / `LEGACY_WSVC_ID`. |
| Daily Financial split (F/M vs H) | **MATCH (data)** | Two `permission_cd`s; H carries `-hs`. Implemented in the navigation seed (originally `V243`, archived 2026-06-09) + verified by `NavigationServiceTest`. |
| Per-node country gate | **PARTIAL (Phase-1 deferred, R10)** | Only the `SHOW_YN` designer gate is enforced; every exported node is VN-only and there is no per-tenant country model yet. The gate lands later without changing the response contract (`:52-55`). |
| Generic navigation as a self-gated endpoint | **N/A (by design)** | `/navigation` is an `@menuAuthz` allow-list exception — the surface that resolves the menu must be reachable for any authenticated session. Not a defect. |
| Dashboard navigation | **N/A** | Dashboard (`SYS_DSHBRD_INFM` / `USER_DSHBRD_INFM`) is not in refactor scope; navigation resolves menu trees only, no dashboard tiles. |
| Unknown / null product handling | **MATCH (safe-degrade)** | `PrdtType.fromCode` empty → well-formed empty tree, no throw (`:91-96`). Deferred legacy letters round-trip but render nothing. |

## Open items

- **R10 — country gate:** add the per-node country gate in `build(...)` when a per-tenant country model
  lands. No response-contract change. Owner: PO. Not actioned.
- **H-tenant grant remap:** legacy H grants on the standard Daily Financial code must be remapped to `-hs`
  (data migration); the resolver itself is correct. ⚠️ migration number (V244) UNVERIFIED this pass.
