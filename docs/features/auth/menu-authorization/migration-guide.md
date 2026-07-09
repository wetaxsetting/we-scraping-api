# Menu Authorization — Migration Guide (JEX → Spring)

How each JEX layer of the menu / permission story became its Spring counterpart. JEX functional
reference: [docs/jex/features/menu-product-type-management/](../../../../../docs/jex/features/menu-product-type-management/README.md).
Legacy-vs-refactor classification: [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Per-product menu tree (structure) | JexAdmin / Jex Studio **MenuManager** trees (`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`) | `MENU_ITEM` (product-independent structure) + `MENU_ITEM_TRANSLATION` (labels) — refactor-owned reference tables |
| Per-product membership + order | `MENU_AUTH_ADMN_SYS` (per-product default catalogue) | `PRODUCT_MENU` (PK `PRODUCT_TYPE + MENU_ITEM_ID`, `DISP_ORD` = legacy `MNU_SEQ`) |
| Per-user grant | `USER_MENU_AUTH_INFM` (kept — legacy table) | `UserMenuAuthInfm` + `UserMenuAuthInfmRepository` (legacy `WEB_SVC_ID` column repurposed to hold the kebab slug) |
| Resolution (`Product Tree ∩ grants`, parent collapse) | `OSMCMenuHelper.isPermittedMenu` | `NavigationService.resolve()` (intersection + recursive parent collapse) |
| Grant read | DAO over `USER_MENU_AUTH_INFM` | `MenuAuthorizationService.grantedMenuCds` / `isGranted` |
| Resolved nav for the client | JSP menu rendering driven by the resolved tree | `GET /api/v1/navigation` → `NavigationResponse` |
| Entitlement editor | Settings > Users menu-permission popover | `MenuEntitlementService` (`load` / `save` delete-then-insert) + `UserManagementController` menu-entitlement endpoints |
| Endpoint enforcement | menu visibility only (no server-side `@PreAuthorize` analogue) | `@menuAuthz.canAccess(...)` guard (documented in [../authorization](../authorization/README.md)) — routes through this catalogue |

---

## Identifier translation (slug, not WSVC id)

Every refactor identifier surface carries kebab slugs: the REST path (`/api/v1/navigation`,
`/api/v1/settings/users/{user_id}/menu-entitlement`), the Java package
(`…domain.navigation`, `…domain.auth.service`, `…domain.setting.users`), class names
(`NavigationService`, `MenuAuthorizationService`, `MenuEntitlementService`), and — critically — the
**permission codes themselves** (`banking-vnd-account-inquiry`, `addon-einvoices-sales`,
`settings-core-users`). The legacy `USER_MENU_AUTH_INFM.WEB_SVC_ID` column is **repurposed** to store
the kebab slug rather than a legacy WSVC id (per the identifier-naming rule); the Java field stays
`menuAuthCd` to keep slug semantics. Legacy WSVC / `MNU_ID` values survive only as the
**traceability-only** `MENU_ITEM.LEGACY_WSVC_ID` / `LEGACY_MNU_ID` columns (echoed on the wire as
`legacy_wsvc_id` / `legacy_mnu_id`) — they never drive routing or gating.

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| Menu tree source | JexAdmin / Jex Studio MenuManager (external, now lost) | refactor-owned `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` reference tables |
| Per-product catalogue | `MENU_AUTH_ADMN_SYS` | `PRODUCT_MENU` (the F/M/H difference lives here) |
| Resolution location | JSP / OSMC helper | `NavigationService` (server, returns one resolved tree, not four per-surface lists) |
| Permission-code namespace | mixed legacy WSVC / menu ids | kebab slugs (verified identical between `PERMISSION_CD` and `WEB_SVC_ID`) |
| Daily Financial (H) | one Daily Financial node | split — H carries `banking-reports-daily-financial-hs`; F/M carry `banking-reports-daily-financial` |
| Entitlement save | per-row popover save | delete-then-insert full-set replace, idempotent, retry-once, admin-only |
| Endpoint enforcement | menu visibility only | explicit `@menuAuthz` `@PreAuthorize` gate (F-3) — see [../authorization](../authorization/README.md) |
| Translation key | `(MENU_ID, LANG)` | `(PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)` — product-keyed so M's VI/KO labels can diverge from F |

---

## HS-tenant legacy grant remap

A legacy H grant of `banking-reports-daily-financial` is **inert** for the H product tree, whose Daily
Financial leaf is the split code `banking-reports-daily-financial-hs`. A migration **remaps** H-tenant
legacy grants from the standard code to the `…-hs` code so the report surfaces after the cutover. The
`NavigationServiceTest` HS cases pin this:

- `hNonAdmin_withHsGrant_seesHsReport` — granting `…-hs` shows the HS report.
- `hNonAdmin_withStandardDfGrant_doesNotSeeHsReport` — the standard code is inert for H until remapped.

---

## Dead / out-of-scope legacy flows (not ported)

| Legacy flow | Disposition |
|---|---|
| JexAdmin / Jex Studio MenuManager runtime dependency | **DEAD LEGACY** — the menu definition is now refactor-owned data; no runtime dependency on Jex Studio. |
| Per-node country gate | **MISSING / PARTIAL** — the `SHOW_YN` designer gate is enforced; the per-node country gate is not modelled (every exported node is VN-only; no per-tenant country model yet — R10). Add the gate in `NavigationService.build` without changing the response contract when a country model lands. |
| `USER_MENU_AUTH_INFM.DISP_ORD` (per-user menu order) | **Not mapped** — the entitlement editor stores no per-user order; the column stays for schema parity but the entity does not declare it. |
| Cross-product menu union for "super" users | **Not ported** — admin sees its own product tree only (never a cross-product union). |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification + evidence.

---

## Schema migrations

| Item | Where |
|---|---|
| `USER_MENU_AUTH_INFM` (legacy grant table) | `V131__user_menu_auth_infm.sql` — in the inventory + PROD Active List |
| `MENU_ITEM` (refactor infra) | `V138__menu_item.sql` |
| `PRODUCT_MENU` (refactor infra) | `V137__product_menu.sql` |
| `MENU_ITEM_TRANSLATION` (refactor infra) | `V139__menu_item_translation.sql` |
| F / M / H seed rows for the catalogue | loaded as a **DBA-run dataset** (Flyway disabled, `ddl-auto: validate`, schema treated as external / pre-migrated) — source export under the menu-product-type-management docs |

No Flyway is auto-run; the schema is treated as external / pre-migrated. The three catalogue tables are
**refactor-owned infrastructure** (flagged non-legacy in the inventory) — they are not subject to the
copied-legacy-table column-parity rule.
