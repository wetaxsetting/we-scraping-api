# navigation — Functional Specification (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

The behavioural contract of `GET /api/v1/navigation` and the `@menuAuthz` guard. All facts verified
against `NavigationService.java`, `MenuAuthorizationGuard.java`, and the entities/repositories.

## Resolution rule

```
Visible Menu = Product Menu Tree (by prdt_type)  ∩  User Permission
```

- **Normal user** → product tree intersected with their `USE_YN='Y'` grants from `USER_MENU_AUTH_INFM`
  (resolved via `MenuAuthorizationService.grantedMenuCds(bizKey, userId)`, the same product-agnostic
  resolver `/auth/me` uses, kept unchanged).
- **Admin** (`authGrCd = ADMIN`) → the **full product tree**. Admin bypasses the per-user `∩` **only** —
  never the product scope. An admin's tree is still product-scoped, never a cross-product union.

## Behavioural rules (verified)

| Rule | Behaviour | Source |
|---|---|---|
| Product type source | `TenantContext.currentPrdtType()` — the signed JWT. **Never** a request body, query param, or header. | `NavigationService.java:85` |
| Product scope is absolute | Product type gates the entire tree. Admin bypass applies to the per-user `∩` only, not the product scope. | `:122-126` |
| Unknown / null product degrades safely | `PrdtType.fromCode(rawPrdt)` empty → return `NavigationResponse(rawPrdt, null, [])`. No throw; deferred legacy letters and NULL rows get an empty tree. | `:91-96` |
| No membership seeded | A recognised product with zero `PRODUCT_MENU` rows → empty `topMenus`, `menuGroupId` still echoed. | `:101-104` |
| Parents collapse | A parent whose visible-child list is empty returns `null` — empty parents never render (legacy `OSMCMenuHelper.isPermittedMenu`). | `:161-195` |
| Out-of-tree grant is inert | A grant for a `permission_cd` not in the tenant's product tree has no node to render and shows nothing. Keeps H reduced even with a stray F-only grant. | `:48-51` (class Javadoc) |
| `SHOW_YN` designer gate | A node with `SHOW_YN != 'Y'` is dropped regardless of grant. Phase-1 scope: the per-node country gate is **not** modelled yet (R10). | `:52-55`, `:171`, `:191` |
| Sibling order | `DISP_ORD` (per-product `MNU_SEQ`), tiebroken by `menuItemId`. | `:138-142` |
| Pure parent has no grant | A node with `permissionCd == null` is a grouping node (`MenuItem.isParent()`); no route, no grant key. | `MenuItem.java:63-66` |

## Daily Financial split

A single feature carries **two distinct permission codes** keyed by product:

| Product | Daily Financial node | `permission_cd` | Legacy WSVC |
|---|---|---|---|
| F / M | standard | `banking-reports-daily-financial` | rept_0015_001 |
| H | HAENGSUNG | `banking-reports-daily-financial-hs` | rept_0017_001 |

H carries only the `-hs` leaf. A legacy H-tenant grant on the *standard* code is therefore inert for H
(it is not the H tree's leaf cd) — which is why H-tenant grants are remapped by migration (see migration
guide / test scenarios). The HS node is the 42nd menu item (41 F-nodes + 1 HS).

## Translation keying by product

`MENU_ITEM_TRANSLATION` is keyed by `(PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)` — the **product dimension is
required** because M's localized VI/KO labels diverge from F for the same node (F often carries an English
string in its VI field; M carries the real Vietnamese). A `(MENU_ITEM_ID, LANG_CD)`-only key could not
represent that. The service builds the EN layer first, then overlays the caller's language
(`labelMap(productType, lang)`); KM has no rows and falls back to EN.

## `menuGroupId` (traceability echo)

`F → WE_CMSVN`, `M → WE_CMSVN_LUMENS`, `H → WE_CMSVN_HAENGSUNG` — echoed on the response purely for source
traceability to the lost JexAdmin MenuManager trees; it does not drive resolution. `null` when the product
is not a recognised navigation product. (`NavigationService.java:66-70`, `NavigationResponse` Javadoc.)

## `@menuAuthz` guard — functional contract

Mirrors the nav rule: **validity → product-tree gate (admin + user) → admin bypass of the per-user grant
only → per-user grant.** A `null`/blank cd → deny; an unknown / typo'd cd → deny (a misannotated endpoint
fails closed). `settings-*` codes are product-invariant and skip the product-tree gate. Param-aware helpers
resolve a multiplexed endpoint's exact cd from a bound argument (Account Inquiry VND/FCY by currency;
e-Invoice by SALES/EXPENSE type — unknown e-invoice type **fails closed**). See `interface-specification.md`.
