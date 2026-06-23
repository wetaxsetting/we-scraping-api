# Auth > Authorization — Backend Feature (Refactor)

**Status:** ✅ Implemented (F-3 complete, 2026-06-06). The `@menuAuthz` guard, role gates (`hasRole('ADMIN')` / `@userMgmtAccess`), product-type gating, and the `EndpointAuthorizationCoverageTest` deny-or-declared lock-in are all on disk and enforced at build time. This is **enforcement only** — there is no dedicated endpoint or table owned by this module.
**Module / domain:** Auth → **Authorization** (system-feature group; not a WeCMS top-menu group)
**Refactor slug:** `authorization` (matches the frontend counterpart)
**Scope:** the **authorization enforcement layer** — how every business `/api/v1/**` request is gated *after* JWT authentication has run. The permission-code catalogue + `USER_MENU_AUTH_INFM` grant data live in the sibling **menu-authorization** module; this module is about the *mechanism* that consumes them.

> This folder documents the **enforcement mechanism**: the `@menuAuthz` 4-step rule, `@PreAuthorize` SpEL usage on business controllers, Spring Security role authorities (`ROLE_ADMIN`/`ROLE_USER`), product-type gating (`PrdtType` F/M/H), `BUSI_ADMN_INFM` authority resolution, the `WBE1003` access-denied envelope, and the `EndpointAuthorizationCoverageTest` build-time lock-in. The **permission-code list** and the **grant data model** are NOT duplicated here — they are cross-linked to menu-authorization.

---

## What this feature is

Authorization is the layer that runs **after** [jwt-authentication](../jwt-authentication/README.md) has parsed the Bearer token and populated `TenantContext` + the Spring Security `Authentication`. It answers a single question for every protected request: *may this authenticated caller invoke this endpoint?*

Three gating styles stack (defence-in-depth — never weakened):

1. **Menu authorization (`@menuAuthz`)** — `@PreAuthorize("@menuAuthz.canAccess('<slug>')")` on a business controller. The guard runs the 4-step rule (validity → product-tree gate → admin bypass → per-user grant) against the navigation catalogue + `USER_MENU_AUTH_INFM`. Live on **30 of 43** controllers; the other **13** are documented allow-list exceptions.
2. **Role gates** — `hasRole('ADMIN')` and the `@userMgmtAccess` bean stacked on top of `@menuAuthz` for admin-only screens (Settings > Users). Roles `ROLE_ADMIN` / `ROLE_USER` are derived from the JWT `authGrCd` claim by `JwtAuthenticationFilter`; `authGrCd` itself is resolved at login from `BUSI_ADMN_INFM` (presence ⇒ ADMIN).
3. **Product-type gates** — the `@menuAuthz` product-tree gate denies a caller a leaf cd that is not in its product's tree (e.g. an `H` tenant is denied F/M-only Cash Flow). Product type is read **only** from `TenantContext.currentPrdtType()` (the signed JWT), resolved through `PrdtType.fromCode(...)` — never from a request param. Per-product *behavioural* gates (HAENGSUNG table-2 layout, account masking, hidden columns, Lumens readonly, e-Invoice gate) are **deferred per-feature**.

A denied request surfaces as HTTP **403** with code **`WBE1003`** (access denied) through `GlobalExceptionHandler`'s `AccessDeniedException` mapping; an unauthenticated request surfaces as **401** `WBE1401`/`WBE1009` from `jwt-authentication`'s entry point (a separate layer).

The whole surface is locked in by [`EndpointAuthorizationCoverageTest`](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java): the build fails if a new business handler ships without a `@menuAuthz` gate and is not on the allow-list.

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoint | **None** — this is enforcement, not a resource. It layers on every business `/api/v1/**` endpoint. |
| Guard bean | `@menuAuthz` → [MenuAuthorizationGuard](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java) (`canAccess`, `canAccessAny`, `canAccessAccountInquiry`, `canAccessEInvoice`) |
| Method security | `@EnableMethodSecurity` on [SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) |
| Coverage | 30 of 43 controllers `@menuAuthz`-gated; 13 allow-listed (12 controller names enumerated in the test set — see [source-code-index.md](source-code-index.md)) |
| Role authorities | `ROLE_ADMIN` / `ROLE_USER` derived from JWT `authGrCd` by `JwtAuthenticationFilter` |
| Authority source | `BUSI_ADMN_INFM` (presence ⇒ ADMIN) — resolved at login, carried in the signed JWT |
| Product type | read only from `TenantContext.currentPrdtType()`; resolved via `PrdtType.fromCode(...)`; F/M/H modelled, 11 letters round-trip verbatim |
| Tenant scope | `TenantContext.currentBizKey()` / `currentUserId()` (signed JWT) — never a request param |
| Access denied | 403 `WBE1003` (`AccessDeniedException` → `GlobalExceptionHandler`) |
| Coverage test | [EndpointAuthorizationCoverageTest](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java) — deny-or-declared, build fails on un-annotated non-allow-listed handler |
| Tables touched (read-only by the guard) | `MENU_ITEM` / `PRODUCT_MENU` (product-tree validity + membership), `USER_MENU_AUTH_INFM` (per-user grant), `BUSI_ADMN_INFM` (authority, at login) |
| Wire format | n/a (no JSON contract of its own); error envelope is the standard lowercase snake_case `{ status, … }` |

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | The `@menuAuthz` 4-step rule in detail; `canAccessAny`/`canAccessAccountInquiry`/`canAccessEInvoice`; role + product-type gating; how authorities are populated from the JWT; the coverage-test contract |
| [business-flow.md](business-flow.md) | Request-time enforcement flow + parity narrative (legacy permission/role model). `@PreAuthorize` gating = MATCH (F-3 complete); product-type behavioural gates = PARTIAL (deferred-per-feature) |
| [database-mapping.md](database-mapping.md) | `BUSI_ADMN_INFM` (authority), `USER_MENU_AUTH_INFM` (grant lookup), `MENU_ITEM` / `PRODUCT_MENU` (product-tree validity/membership) — legacy vs refactor-infra noted |
| [interface-specification.md](interface-specification.md) | The `@PreAuthorize` SpEL contract, the `WBE1003` / 403 envelope, how it layers on every business endpoint; reference to the endpoint-authorization-matrix |
| [source-code-index.md](source-code-index.md) | `MenuAuthorizationGuard`, `TenantContext`, `PrdtType`, `SecurityConfig`, `EndpointAuthorizationCoverageTest` (real paths + methods + the allow-list set) |
| [migration-guide.md](migration-guide.md) | Legacy JSP permission checks → `@menuAuthz` / `@PreAuthorize` Spring Security; what changed |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with evidence |
| [test-scenarios.md](test-scenarios.md) | `EndpointAuthorizationCoverageTest` + `MenuAuthorizationGuardTest` scenarios; gaps |

---

## Related & sibling modules

- **Permission-code catalogue + grant data model (do not duplicate here):** sibling
  [menu-authorization](../menu-authorization/README.md) — `MenuAuthorizationService.grantedMenuCds` / `isGranted`, `USER_MENU_AUTH_INFM`, `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION`, the verified live kebab permission codes.
- **The authentication layer this builds on:** sibling
  [jwt-authentication](../jwt-authentication/README.md) — token issue/parse, the `JwtAuthenticationFilter` that populates `TenantContext` + Spring authorities, the 401 entry point.
- **Endpoint-authorization matrix (which controller carries which cd):**
  [docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md](../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md).
- **Frontend counterpart:** [wecms-web/docs/features/auth/authorization/README.md](../../../../../wecms-web/docs/features/auth/authorization/README.md) — `lib/menuAuth.ts` UX gating (FE never a security boundary; backend enforces via JWT).
- **Auth domain index:** [../README.md](../README.md).

---

## Open / residual items

1. **Per-product behavioural gates deferred-per-feature (PARTIAL).** HAENGSUNG payment-table-2 layout, account masking for bank `11704020`, hidden expense file column, Lumens readonly overrides, and the WeCMS-Default e-Invoice gate are **not** part of this infrastructure phase — each lands with its feature MR. The product-*tree* gate (`@menuAuthz` step 2) is live; the product-*behaviour* gates are not. See [refactor-gap-analysis.md](refactor-gap-analysis.md).
2. **`SecurityConfig.passwordEncoder()` Javadoc is stale.** The comment still says "always writes BCrypt … flags legacy hashes for transparent upgrade", but `WecmsPasswordEncoder` is now verify-only SHA-256 with no write-back (coexistence). Doc/comment cleanup only; does not affect authorization behaviour. Flagged in [refactor-gap-analysis.md](refactor-gap-analysis.md).

None of the residual items affect the implemented enforcement layer's parity or correctness.
