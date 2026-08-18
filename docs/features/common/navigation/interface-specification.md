# Navigation — Interface Specification (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

## `GET /api/v1/navigation`

| | |
|---|---|
| Method / path | `GET /api/v1/navigation` |
| Auth | Protected (JWT) — global `anyRequest().authenticated()`; on the `@menuAuthz` allow-list (navigation is **not** self-gated). |
| Request body | none |
| Request params | **none** — product type comes from the JWT (`TenantContext.currentPrdtType()`), never the client. |
| Response | `ApiResponse<NavigationResponse>` |
| Controller | `NavigationController.navigation()` → `navigationService.resolve()` (`NavigationController.java:28-31`) |
| Errors | `WBE1401` (`BusinessException`) when the session principal is missing `userId`/`bizKey`. Unknown / null product → **HTTP 200 with an empty well-formed tree** (not an error). |

Wire shape is lowercase snake_case via the global Jackson `SNAKE_CASE` strategy.

### `NavigationResponse` (`dto/NavigationResponse.java`)

| Java field | Wire | Type | Notes |
|---|---|---|---|
| `productType` | `product_type` | string | Verbatim `BUSI_INFM.PRDT_TYPE` letter for the session. |
| `menuGroupId` | `menu_group_id` | string \| null | Traceability echo (`F→WE_CMSVN`, `M→WE_CMSVN_LUMENS`, `H→WE_CMSVN_HAENGSUNG`); `null` when the product is not a recognised navigation product. |
| `topMenus` | `top_menus` | `NavMenuNode[]` | Resolved depth-0 roots, each with nested children. One resolved hierarchy — not four per-surface lists. |

### `NavMenuNode` (`dto/NavMenuNode.java`)

Nodes are already filtered to what the caller may see and ordered by `seq` within each parent.

| Java field | Wire | Type | Notes |
|---|---|---|---|
| `id` | `id` | string | Refactor slug — stable node id. |
| `permissionCd` | `permission_cd` | string \| null | Kebab menuAuth grant key; `null` for pure parents. |
| `parentId` | `parent_id` | string \| null | Parent slug; `null` for top menus. |
| `depth` | `depth` | int | 0 top · 1 group · 2 leaf (legacy `MNU_DEP`). |
| `seq` | `seq` | int | Sibling order within the parent (legacy `MNU_SEQ` / `DISP_ORD`). |
| `label` | `label` | string | Resolved to the caller's language (EN fallback). |
| `route` | `route` | string \| null | Target Next.js route; `null` for pure parents. |
| `icon` | `icon` | string \| null | CSS / asset icon hint; may be `null`. |
| `legacyMnuId` | `legacy_mnu_id` | string \| null | Traceability — JexAdmin `MNU_ID`. |
| `legacyWsvcId` | `legacy_wsvc_id` | string \| null | Traceability — legacy WSVC id (URL − `.act`). |
| `children` | `children` | `NavMenuNode[]` | Nested child nodes (already filtered + ordered). |

### Example (shape)

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "product_type": "F",
    "menu_group_id": "WE_CMSVN",
    "top_menus": [
      {
        "id": "banking", "permission_cd": null, "parent_id": null,
        "depth": 0, "seq": 0, "label": "Banking", "route": null, "icon": null,
        "legacy_mnu_id": "…", "legacy_wsvc_id": null,
        "children": [ /* groups → leaves */ ]
      }
    ]
  }
}
```

## `@menuAuthz` guard — programmatic interface (no REST endpoint)

`MenuAuthorizationGuard` is the Spring bean `@menuAuthz` consumed by `@PreAuthorize` on business
controllers. It has no endpoint of its own.

| Method | Signature | Use |
|---|---|---|
| `canAccess` | `boolean canAccess(String permissionCd)` | Single-screen gate. Full rule: validity → product-tree gate → admin bypass → per-user grant. `null`/blank/unknown → deny. (`:66-106`) |
| `canAccessAny` | `boolean canAccessAny(String... permissionCds)` | Coarse / feature-level gate — allow when the caller may access **any** code. For endpoints serving more than one screen whose discriminator is in the request body (exports) or derived from the resource (account-keyed transaction sub-resources). Each cd still runs the full per-code rule. (`:119-129`) |
| `canAccessAccountInquiry` | `boolean canAccessAccountInquiry(String currency)` | Param-aware: VND/blank → `banking-vnd-account-inquiry`; any FCY → `banking-fcy-update-inquire`. (`:141-145`) |
| `canAccessEInvoice` | `boolean canAccessEInvoice(String type)` | Param-aware: `SALES` → `addon-einvoices-sales`; `EXPENSE`/`EXPENSES` → `addon-einvoices-expenses`; unknown/missing → **deny (fail-closed)**. (`:156-165`) |

`false` → `AccessDeniedException` → HTTP 403 / `WBE1003`.

### Allow-list note

Coverage is **30 of 43 controllers** gated by `@menuAuthz`. The other **13 are documented allow-list
exceptions** — including **navigation**, **screen-options** (View Setting), and **notifications** — plus
auth, country, actuator, etc. `EndpointAuthorizationCoverageTest` fails the build on any un-annotated,
non-allow-listed `/api/v1/**` handler. Navigation is intentionally not self-gated: it is the surface that
resolves the menu and must be reachable for any authenticated session. Full matrix:
[endpoint-authorization-matrix.md](../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md).
