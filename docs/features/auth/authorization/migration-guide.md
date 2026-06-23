# Authorization — Migration Guide (JEX → Spring)

How the legacy JEX permission / role enforcement became the refactor's `@menuAuthz` / `@PreAuthorize`
Spring Security layer. The permission-code translation (legacy menu id → refactor kebab slug) is the
sibling [menu-authorization/migration-guide.md](../menu-authorization/migration-guide.md)'s concern;
this doc is about the **enforcement mechanism**.

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Menu-visibility decision | per-product menu trees (`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`; role of `MENU_AUTH_ADMN_SYS`) + `USER_MENU_AUTH_INFM` grants | `MenuAuthorizationGuard.canAccess` 4-step rule over `MENU_ITEM` / `PRODUCT_MENU` + `USER_MENU_AUTH_INFM` |
| Per-screen permission check | ad-hoc `*_act.jsp` page-level checks (no single funnel) | `@PreAuthorize("@menuAuthz.canAccess('<slug>')")` on the controller (one consistent funnel) |
| Company-admin distinction | `BUSI_ADMN_INFM` row presence | `authGrCd` JWT claim (resolved at login from `BUSI_ADMN_INFM`) → `ROLE_ADMIN`/`ROLE_USER` + `TenantContext.currentIsAdmin()` |
| Admin-only screen gate | admin-only menu visibility | `hasRole('ADMIN')` / `@userMgmtAccess` stacked on `@menuAuthz` |
| Product scoping | product-specific menu tree shown per `BUSI_INFM.PRDT_TYPE` | step 2 product-tree gate (`PRODUCT_MENU.existsInProductTree(currentPrdtType(), cd)`), binds admin + user |
| Identity / product source | server session (`WbUser`) | signed JWT → `TenantContext` (`userId`, `bizKey`, `authGrCd`, `prdtType`) |
| "Is this endpoint gated?" assurance | manual / convention | build-time `EndpointAuthorizationCoverageTest` (deny-or-declared) |

---

## Identifier translation (slug, not WSVC id)

The permission codes the guard checks are kebab-case slugs (`settings-core-login-credentials`,
`banking-vnd-account-inquiry`, `addon-einvoices-sales`, …) — never legacy WSVC ids. Legacy ids appear
only in (1) JEX file:line citations and (2) migration-traceability prose. The legacy-id → slug mapping
is verified in the menu-authorization module
([permission-code-normalization-verification.md](../../../../../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md));
the authorization layer consumes the slugs verbatim.

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| Enforcement style | menu-hiding (UX) + thin per-JSP checks | explicit server-side `@PreAuthorize` on every business endpoint (one funnel) |
| Where the decision lives | scattered across JSPs / framework | one bean (`MenuAuthorizationGuard`) mirroring the navigation visibility rule (cannot drift) |
| Product scoping | per-product menu tree | step 2 product-tree gate, binds admin + user (an H caller is denied F/M-only cds) |
| "Un-gated endpoint" risk | possible (convention only) | impossible to ship silently (coverage test fails the build) |
| Identity source | server session | stateless signed JWT (`TenantContext`) |
| Authority refresh | session-bound | next-login (JWT claim is signed/immutable) |
| Denial response | JSP/redirect | HTTP 403 `WBE1003`, standard envelope |

---

## What changed structurally

1. **One guard, not many checks.** Legacy enforcement was per-JSP; the refactor centralizes the
   decision in `@menuAuthz` and applies it uniformly via `@PreAuthorize`. A controller declares its
   screen cd once (class-level where possible).
2. **The product tree became refactor-infra tables.** The JexAdmin/Jex Studio MenuManager trees (lost
   with the move off JEX tooling) are reconstituted as `MENU_ITEM` / `PRODUCT_MENU` (refactor-owned,
   non-legacy). The grant table (`USER_MENU_AUTH_INFM`) and authority table (`BUSI_ADMN_INFM`) stay
   legacy.
3. **Stronger, build-checked coverage.** `EndpointAuthorizationCoverageTest` is a refactor addition with
   no legacy equivalent — it locks the deny-or-declared invariant at build time.

---

## Dead / out-of-scope legacy flows (not ported in this layer)

| Legacy flow | Disposition |
|---|---|
| Per-product *behavioural* gates (HAENGSUNG table-2 layout, account masking for bank `11704020`, hidden expense file column, Lumens readonly overrides, WeCMS-Default e-Invoice gate) | **PARTIAL / deferred-per-feature** — this infrastructure phase ships the product-*tree* gate only; each behavioural gate ports with its feature MR (see [refactor-gap-analysis.md](refactor-gap-analysis.md)) |
| Server-session-bound authority refresh | **DEAD LEGACY** — replaced by the stateless next-login model (JWT claims are signed/immutable); a deliberate consequence of the stateless design (see jwt-authentication) |
| Scattered per-JSP permission checks | **DEAD LEGACY** — consolidated into the single `@menuAuthz` funnel |

---

## Schema / migrations

No migration is owned by the authorization layer. The tables it reads are migrated by their owning
modules:

| Item | Where |
|---|---|
| `MENU_ITEM` / `PRODUCT_MENU` (product-tree validity + membership) | refactor-infra navigation tables (V242 schema, V243 seed) — owned by menu-authorization; cited by table name, not relied on here |
| `USER_MENU_AUTH_INFM` (per-user grant) | legacy table — owned by menu-authorization / Settings > Users |
| `BUSI_ADMN_INFM` (authority) | legacy table — owned by the auth-entity / login layer |

Flyway is **disabled** + `ddl-auto: validate` (schema external / pre-migrated). The authorization layer
adds no DDL.
