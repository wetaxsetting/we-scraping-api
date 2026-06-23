# Menu Authorization — Refactor Gap Analysis (Parity Review)

Legacy JEX (JexAdmin / Jex Studio MenuManager trees + `MENU_AUTH_ADMN_SYS` + `USER_MENU_AUTH_INFM` +
`OSMCMenuHelper.isPermittedMenu`) vs. the refactor (`menu-authorization`). Status vocabulary:
**MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Navigation resolution** (`Product Tree ∩ grants`, admin bypass, parent collapse, product scope) | MenuManager tree + `OSMCMenuHelper.isPermittedMenu` | `NavigationService.resolve` — `NavigationServiceTest` 8/8 green | **MATCH** |
| **Grant resolution** (`USE_YN='Y'` slugs) | DAO over `USER_MENU_AUTH_INFM` | `MenuAuthorizationService.grantedMenuCds` / `isGranted` | **MATCH** |
| **Per-user grant store** | `USER_MENU_AUTH_INFM` | `UserMenuAuthInfm` (legacy table, `WEB_SVC_ID` → kebab slug) | **MATCH** |
| **`SHOW_YN` designer gate enforced** | enforced | enforced in `NavigationService.build` | **MATCH** |
| **Language fallback** (EN/KO/VI; KM/null → EN) | per-language labels | `labelMap` EN overlay + `callerLang` switch | **MATCH** |
| **Product map** (F→WE_CMSVN, M→WE_CMSVN_LUMENS, H→WE_CMSVN_HAENGSUNG) | MenuManager tree ids | `MENU_GROUP` map echoed as `menu_group_id` | **MATCH** |
| **Daily-Financial split** (H = `…-hs`, F/M = standard) | HS-specific report node | encoded in `PRODUCT_MENU`; pinned by `NavigationServiceTest` HS cases | **MATCH** |
| **Entitlement editor** (load/save) | Settings > Users popover | `MenuEntitlementService.load` / `save` (delete-then-insert, idempotent, retry-once, admin-only) | **MATCH** |
| **Product-tree catalogue source** | JexAdmin / Jex Studio MenuManager (lost) + `MENU_AUTH_ADMN_SYS` | refactor-owned `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` infra tables | **APPROVED DEVIATION** (GAP-1) |
| **Endpoint enforcement** | menu visibility only | `@menuAuthz.canAccess(...)` `@PreAuthorize` gate (F-3) — see [../authorization](../authorization/README.md) | **APPROVED DEVIATION** (stronger; documented in sibling module) |
| **Save flow** | per-row popover save | delete-then-insert full-set replace + retry-once | **APPROVED DEVIATION** (idempotent semantics) |
| **Permission-code namespace** | mixed legacy WSVC / menu ids | kebab slugs (verified identical `PERMISSION_CD` ↔ `WEB_SVC_ID`) | **APPROVED DEVIATION** (slug rule) |
| **Per-node country gate** | enforced per node | not modelled (R10) | **MISSING / PARTIAL** (GAP-2) |
| **JexAdmin / Jex Studio runtime dependency** | menu served from Jex Studio | replaced by refactor-owned data | **DEAD LEGACY** (GAP-3) |
| **`USER_MENU_AUTH_INFM.DISP_ORD`** (per-user menu order) | column populated | column unmapped (no per-user order stored) | **DEAD LEGACY** (GAP-4) |
| **Cross-product menu union for super-users** | — (not a legacy feature either) | admin sees own product tree only | **MATCH** (nothing to port) |

---

## Specially-verified gaps

### GAP-1 — Refactor-owned catalogue tables (APPROVED DEVIATION)

The legacy menu tree lived in JexAdmin / Jex Studio **MenuManager** (`WE_CMSVN` /
`WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`), which the refactor cannot depend on. The menu **structure**
(`MENU_ITEM`), **per-product membership** (`PRODUCT_MENU`, replacing `MENU_AUTH_ADMN_SYS`), and
**labels** (`MENU_ITEM_TRANSLATION`) are reproduced as **refactor-owned reference tables**, explicitly
flagged **non-legacy** in the [legacy table inventory](../../../references/database/legacy-table-inventory.md)
(alongside `SHEDLOCK` / `menu_item` family). They are seeded by a DBA-run dataset and never mutated at
runtime (no audit columns). The per-user grant store (`USER_MENU_AUTH_INFM`) stays legacy.
**Status: APPROVED DEVIATION** — sanctioned because the JexAdmin source was lost; the refactor owns the
menu definition as data. Not a parity break.

### GAP-2 — Per-node country gate not modelled (MISSING / PARTIAL)

The legacy menu carried a per-node country gate. The refactor enforces the **`SHOW_YN` designer gate**
(MATCH) but **not** the country gate — every exported node is VN-only and there is no per-tenant
country model yet (R10). When a country model lands, add the gate in `NavigationService.build` without
changing the response contract. **Status: MISSING / PARTIAL** — non-blocking; the current VN-only
deployment is unaffected.

### GAP-3 — JexAdmin / Jex Studio runtime dependency (DEAD LEGACY)

The menu was served at runtime from Jex Studio's MenuManager. The refactor has **no runtime dependency
on Jex Studio** — the tree is refactor-owned data resolved by `NavigationService`. **Status: DEAD
LEGACY** — intentionally excluded.

### GAP-4 — `USER_MENU_AUTH_INFM.DISP_ORD` unmapped (DEAD LEGACY)

The legacy grant table carries a nullable `DISP_ORD` (per-user menu order). The refactor entity does
**not** map it — the entitlement editor stores no per-user ordering; navigation order comes from
`PRODUCT_MENU.DISP_ORD` instead. The column is preserved physically for schema parity but the entity
ignores it (Hibernate `validate` tolerates unmapped columns). **Status: DEAD LEGACY** — intentional.

### HS-tenant legacy grant remap (MATCH, intentional)

A legacy H grant of the standard `banking-reports-daily-financial` is inert for the H tree (whose leaf
is the split `…-hs` code). A migration remaps H-tenant legacy grants to `…-hs` so the report surfaces.
Pinned by `NavigationServiceTest` (`hNonAdmin_withHsGrant_seesHsReport` +
`hNonAdmin_withStandardDfGrant_doesNotSeeHsReport`). **Status: MATCH** — correct post-cutover behaviour.

---

## Migration status

```
Refactor Status: complete for the in-scope feature surface (catalogue + grant model + resolved nav +
                 entitlement editor); per-node country gate (GAP-2) deferred pending a country model.
```

Evidence for closed gaps:

- **Resolution algorithm verified** — `NavigationServiceTest` 8/8 (F/M/H admin + non-admin,
  out-of-tree-grant inert, no-grants empty, HS Daily-Financial split).
- **Grant resolver verified** — `MenuAuthorizationServiceTest` (only `USE_YN='Y'` rows; empty when none).
- **Entitlement save admin guard verified** — `UserManagementAuthorizationTest`
  (`menuEntitlementSave_byNonAdmin_isDeniedBeforeAnyDbAccess`).
- **Enforcement guard** (`@menuAuthz`) verified by `MenuAuthorizationGuardTest` and the Phase-F
  coverage test — documented in the sibling [authorization](../authorization/README.md) module.
- **Slug namespace** verified identical between `PERMISSION_CD` and `WEB_SVC_ID`
  ([permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md)).

Residual (non-blocking, tracked):
1. GAP-2 — per-node country gate, when a per-tenant country model lands.
2. Catalogue F/M/H seed is a DBA-run dataset (Flyway disabled) — operational, not code.
3. No dedicated `NavigationControllerTest` / `MenuEntitlementServiceTest` on disk (see
   [test-scenarios.md](test-scenarios.md)).

None of the residual items affect the implemented feature's parity or correctness.
