# navigation — Migration Guide (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

How the legacy JEX menu architecture maps onto the refactor's navigation module. Legacy WSVC / menu ids
survive only as traceability (`LEGACY_MNU_ID` / `LEGACY_WSVC_ID` columns + this prose); every new
identifier surface uses kebab-case slugs.

## Legacy → refactor structure

| Legacy JEX | Refactor | Notes |
|---|---|---|
| JexAdmin / Jex Studio **MenuManager** product trees `WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG` | `MENU_ITEM` (node structure) + `PRODUCT_MENU` (per-product membership + `DISP_ORD`) | The MenuManager trees were lost; the refactor owns the menu definition as **data** (V137–V139 schema; seed originally `V243`, archived in the 2026-06-09 renumber) rather than depending on Jex Studio. `menuGroupId` echoes the source tree id for traceability. |
| `MENU_AUTH_ADMN_SYS` (per-product default catalogue) | `PRODUCT_MENU` | The "which menus does this product show" role is taken over by `PRODUCT_MENU`. `MENU_AUTH_ADMN_SYS` itself is not migrated to an entity. |
| `USER_MENU_AUTH_INFM` (per-user grants) | **unchanged** — read via `MenuAuthorizationService.grantedMenuCds(...)` / `isGranted(...)` | Legacy table, still the per-user grant source. The refactor reuses the existing resolver unmodified for the `∩` term. |
| `OSMCMenuHelper.isPermittedMenu` (parent-collapse rule) | `NavigationService.build(...)` parent-collapse logic | Same intent: a parent with no visible children is dropped. |
| Legacy per-node label columns / `MNU_NM` | `MENU_ITEM_TRANSLATION` | Per-product per-language; product dimension required because M's VI/KO diverge from F. |
| Legacy WSVC / menu ids (`comm_0220_001`, `rept_0015_001`, …) | kebab slugs (`permission_cd`, route, node id) | Legacy ids preserved only in `LEGACY_MNU_ID` / `LEGACY_WSVC_ID` and migration prose. |

## `permission_cd` normalization

The `permission_cd` values in the navigation seed are the **verified live `menuAuth.ts` kebab codes**,
cross-checked against `USER_MENU_AUTH_INFM.MENU_AUTH_CD` so the refactor `∩` and the legacy per-user grant
join on the same key. The full verification is in
[permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md) §1.

## Daily Financial split (migration impact)

| Product | Code | Legacy WSVC |
|---|---|---|
| F / M | `banking-reports-daily-financial` | rept_0015_001 |
| H | `banking-reports-daily-financial-hs` | rept_0017_001 |

A legacy H-tenant grant on the standard `banking-reports-daily-financial` code is **inert for H** — it is
not the H tree's Daily Financial leaf. H-tenant legacy grants must therefore be **remapped to the `-hs`
code** by a data migration so an existing H user keeps the report after cutover. (The test suite pins both
directions — see `test-scenarios.md`; the seed/remap migration is `V244` per the surrounding design notes,
not read this pass — ⚠️ UNVERIFIED migration number.)

## Migration artifacts

| Migration | Role |
|---|---|
| V137 / V138 / V139 | `CREATE TABLE / INDEX IF NOT EXISTS` for `PRODUCT_MENU` (V137), `MENU_ITEM` (V138), `MENU_ITEM_TRANSLATION` (V139) (refactor-owned infra; no audit columns). *(Pre-2026-06-09-renumber these were `V242`; the archived file was removed with `db/archive/` 2026-06-23.)* |
| seed (archived) | Seed from the JexAdmin export — 42 menu items (41 F + 1 HS), 115 product-menu rows (F=41, H=33, M=41), 345 translations (115 × EN/KO/VI), `ON CONFLICT DO NOTHING`. Shipped as the pre-renumber `V243__navigation_menu_seed.sql` (archived, then **removed with `db/archive/` 2026-06-23**); not in the live `V*` set. Its removal is not missing functionality — UAT/PROD already hold this data in the legacy DB; clean-environment provisioning is out of repo scope (DBA-approved procedures, DC-2). |

## Design references

- Backend resolution design — [backend-navigation-implementation-design.md](../../../../../docs/jex/features/menu-product-type-management/backend-navigation-implementation-design.md)
- Endpoint authorization (F-3) — [endpoint-authorization-matrix.md](../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md)
- `permission_cd` verification — [permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md)
