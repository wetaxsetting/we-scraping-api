# Authorization — Functional Specification (Backend)

The implemented behaviour of the authorization enforcement layer. JEX behaviour reference (read-only):
the legacy menu-visibility model + per-screen JSP permission checks. Source of truth: the implemented
Spring code — [MenuAuthorizationGuard](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java),
[TenantContext](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java),
[PrdtType](../../../../src/main/java/com/kosign/wecms/common/enums/PrdtType.java),
[SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java), and the
build-time lock-in [EndpointAuthorizationCoverageTest](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java).

This module is the **enforcement mechanism**. The permission-code catalogue + the `USER_MENU_AUTH_INFM`
grant model live in the sibling [menu-authorization](../menu-authorization/README.md) — cross-linked,
not duplicated.

---

## 1. The `@menuAuthz` 4-step rule

`@menuAuthz` is the Spring bean `MenuAuthorizationGuard` (annotated `@Component("menuAuthz")`).
Controllers gate an endpoint with method security:

```java
@PreAuthorize("@menuAuthz.canAccess('settings-core-login-credentials')")
```

`canAccess(String permissionCd)` runs four steps **in order**, all reading identity + product context
from the signed JWT via `TenantContext` (never from request input):

| Step | Name | Logic | Denies (→ 403 `WBE1003`) when |
|---|---|---|---|
| 0 | Null/blank guard | `permissionCd` null/empty → deny | the annotation literal is missing |
| 1 | **Validity** | the cd must be a product-tree leaf (`MENU_ITEM.existsByPermissionCd(cd)`) **OR** start with the `settings-` prefix | the cd is neither — an unknown / typo'd cd **fails closed** for admin and user alike (a misannotated endpoint denies, never opens) |
| 2 | **Product-tree gate** | a product leaf must belong to the **current tenant's** product (`PRODUCT_MENU.existsInProductTree(prdtType, cd)`). `settings-*` codes are product-invariant → **skip** this step | `prdtType` is null, OR the leaf is not in this product's tree (e.g. an `H` caller asks for an F/M-only cd like Cash Flow / Daily Cash / e-Invoices). **Applies to admin and normal user alike.** |
| 3 | **Admin bypass** | `TenantContext.currentIsAdmin()` (i.e. `authGrCd == "ADMIN"`) → **allow** | (an admin skips step 4, but **never** step 2 — the product gate still binds) |
| 4 | **Per-user grant** | a normal user must hold a `USE_YN='Y'` row in `USER_MENU_AUTH_INFM` for the cd, via `MenuAuthorizationService.isGranted(bizKey, userId, cd)` | no granted row exists, OR `bizKey` / `userId` is null |

Returning `true` → Spring allows the invocation. Returning `false` → Spring raises
`AccessDeniedException` → HTTP 403 / `WBE1003`.

> **Drift-free by design.** The guard mirrors the navigation *visibility* rule exactly: a menu the
> nav would hide is an endpoint this guard denies. It reuses the unchanged `MenuAuthorizationService`
> resolver and the same `MENU_ITEM` / `PRODUCT_MENU` catalogue the nav builds from, so enforcement and
> visibility cannot diverge.

### 1.1 The `settings-` exception (step 1/2)

Settings panels (`settings-core-login-credentials`, `settings-core-bank-account`, …) are GNB/modal
surfaces identical across F/M/H, so they are **not** seeded in the product tree. Step 1 admits them via
the `settings-` prefix; step 2 skips the product gate for them; they are then authorised by grant only
(step 4) — or admin bypass (step 3). A `settings-*` cd is therefore **product-invariant**.

---

## 2. The coarse / param-aware overloads

For endpoints that legitimately serve more than one screen and cannot resolve the exact screen
pre-authorization, the guard exposes three additional methods. **Each still runs the full per-code
4-step rule**, so the product-tree gate, admin bypass, and per-user grant all still bind.

### 2.1 `canAccessAny(String... permissionCds)`

Allows when the caller may access **any** of the supplied codes (F-3 §4.1 fallback). Used for export
endpoints whose discriminator lives in the request body (not a bound argument) and account-keyed
transaction sub-resources whose VND/FCY split derives from the account, not a query param. A null array
→ deny.

```java
@PreAuthorize("@menuAuthz.canAccessAny('login-credentials','bank-account','corporate-card-transactions')")
```

### 2.2 `canAccessAccountInquiry(String currency)`

Param-aware gate for the multiplexed Account Inquiry list (`GET /api/v1/banking/accounts?currency=…`):

- `currency` absent / blank / `"VND"` (case-insensitive) → resolves `banking-vnd-account-inquiry` (documented default).
- any other (FCY or a specific foreign code) → resolves `banking-fcy-update-inquire`.

So a VND-only user cannot read the FCY list and vice-versa, matching the navigation split.

### 2.3 `canAccessEInvoice(String type)`

Param-aware gate for the multiplexed e-Invoice list (`GET /api/v1/addon/einvoices?type=SALES|EXPENSE`):

- `SALES` → `addon-einvoices-sales`.
- `EXPENSE` / `EXPENSES` → `addon-einvoices-expenses`.
- anything else (unknown / missing) → **fails closed** (returns false) — there is no documented default
  for e-Invoice. (e-Invoice is F/M only, so an `H` caller is additionally denied by the product gate.)

---

## 3. Role gating (stacked, not weakened)

`@EnableMethodSecurity` on `SecurityConfig` activates method security. Two role constructs stack **on
top of** `@menuAuthz` (never replacing it):

- **`hasRole('ADMIN')`** — Spring evaluates the caller's authorities. `JwtAuthenticationFilter` grants
  `ROLE_ADMIN` when the JWT `authGrCd` claim is `ADMIN`, else `ROLE_USER`. (Spring's `hasRole('ADMIN')`
  matches the `ROLE_ADMIN` authority.)
- **`@userMgmtAccess`** — the Settings > Users admin-only guard bean. Defence-in-depth: even with the
  menu grant, a non-admin is denied user-management writes. The service layer additionally checks
  `TenantContext.currentIsAdmin()` (via `MenuEntitlementService`).

These compose: a Settings > Users endpoint can be `@PreAuthorize("@menuAuthz.canAccess('...') and
@userMgmtAccess.canManageUsers()")` — both must pass.

---

## 4. How authorities are populated from the JWT

Authorization reads everything from the **signed JWT**, surfaced through `TenantContext` (a
request-scoped `ThreadLocal<Principal>` set by `JwtAuthenticationFilter` and cleared in `finally`):

| Context accessor | JWT claim | Used by |
|---|---|---|
| `currentUserId()` | `sub` | step 4 grant lookup, tenant scoping |
| `currentBizKey()` | `bizKey` | step 4 grant lookup, tenant scoping |
| `currentAuthGrCd()` / `currentIsAdmin()` | `authGrCd` (`ADMIN`/`USER`) | step 3 admin bypass; `hasRole('ADMIN')` (via `ROLE_*` authority) |
| `currentPrdtType()` | `prdtType` (verbatim letter, nullable) | step 2 product-tree gate |
| `currentLangCd()` | `langCd` | (message localization, not authorization) |

`authGrCd` is resolved **at login** from `BUSI_ADMN_INFM` — presence of a `(BIZ_KEY, USER_ID)` row ⇒
`ADMIN`, absence ⇒ `USER` — and stamped into the JWT. Authority is per-link: the same user may be ADMIN
at one company and USER at another. Because the claim is signed and immutable, an authority change takes
effect at the **next login**, not mid-token. `JwtAuthenticationFilter` defensively defaults a missing
`authGrCd` to `USER` (fail-closed).

`prdtType` is the verbatim `BUSI_INFM.PRDT_TYPE` letter, nullable. The refactor models only `F` / `M` /
`H` in `PrdtType`; the 11 other legacy letters round-trip verbatim. Code that needs to *branch* on
product type resolves via `PrdtType.fromCode(...)` (returns `Optional.empty()` for unknown/deferred
letters, so gates degrade safely) — **never** by comparing literal strings, and **never** from a request
body / query / header / the frontend's `StoredUser.productType`.

---

## 5. The coverage-test contract

[`EndpointAuthorizationCoverageTest`](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java)
is the **deny-or-declared lock-in**. It runs without a Spring context (classpath scan only, so it runs
anywhere) and asserts two invariants:

1. **`everyProtectedEndpointIsMenuGatedOrAllowlisted`** — scans every `@RestController` under
   `com.kosign.wecms`; for every request-mapped handler, it must be:
   - gated by a `@PreAuthorize` whose value references `@menuAuthz` (method-level **or** inherited from
     a class-level `@PreAuthorize`), **or**
   - declared in the `ALLOWLISTED_CONTROLLERS` set.

   A new business endpoint without a `@menuAuthz` gate **fails the build** — the omission cannot ship
   silently. The test also asserts the scan found controllers at all (guards against a mis-scoped base
   package passing vacuously).
2. **`allowlistHasNoStaleEntries`** — every name in `ALLOWLISTED_CONTROLLERS` must still correspond to a
   real scanned `@RestController`, so a renamed/deleted controller cannot leave a stale entry that
   masks a future business controller of the same name.

A controller counts as "menu-gated" if its `@PreAuthorize` references any of `@menuAuthz.canAccess`,
`canAccessAny`, `canAccessAccountInquiry`, or `canAccessEInvoice` (the test matches the `@menuAuthz`
bean-name substring).

### 5.1 Allow-list (documented exceptions)

13 controllers are intentionally **not** menu-gated; the test set enumerates them. They fall into
documented buckets:

| Bucket | Controllers |
|---|---|
| auth / session (public or authenticated-only; no business screen) | `AuthController`, `UserController` |
| navigation resolver (it *computes* the menu; cannot gate on a menu cd) | `NavigationController` |
| View Setting preference store (authenticated-only, no business data — PO note Q-B) | `ScreenOptionController` |
| global header surface for every authenticated user (no per-screen cd) | `NotificationController` |
| cross-screen reference data / utilities (gating to one cd would break reuse) | `CountryController`, `BlockedCharsRuleController` |
| refactor-new features with no menu `permission_cd` yet (route-migration-audit X-2) | `SmartOrganizerController`, `OrganizerRuleController`, `SyncHistoryController` |
| inbound EFS machine-to-machine callbacks (no menu cd; gated by IP-whitelist) | `BulkReceptionResultController`, `BulkExecutionResultController` |

> **Audit fix (2026-06-19):** `BankCredentialController`, `BankScrapingController`, and
> `EnvironmentSettingsController` were **removed** from the allow-list once given real `@menuAuthz`
> gates — they are now enforced like any other business controller.

This is "30 of 43 controllers `@menuAuthz`-gated; the other 13 allow-listed". (The `ALLOWLISTED_CONTROLLERS`
set lists 12 controller names — the 13th allow-list slot is consumed by the auth/session bucket carrying
two controllers; see [source-code-index.md](source-code-index.md) for the verbatim set.)

---

## 6. Error surface

| Code | HTTP | Meaning | Source |
|---|---|---|---|
| (allow) | — | `canAccess(...)` returns `true` → invocation proceeds | `MenuAuthorizationGuard` |
| `WBE1003` | **403** | Authenticated but unauthorized (guard returned false → `AccessDeniedException`) | `GlobalExceptionHandler` `AccessDeniedException` handler |
| `WBE1401` / `WBE1009` | 401 | Missing / invalid / expired JWT — **not this layer**; handled by `jwt-authentication`'s `JwtAuthenticationEntryPoint` before method security runs | sibling [jwt-authentication](../jwt-authentication/README.md) |

A denied request never leaks *which* step denied it — the response is a flat 403 `WBE1003`. Cross-tenant
probing returns the same 403 (the guard reads `bizKey` from the JWT; a foreign `bizKey` cannot be
supplied). Full envelope: [interface-specification.md](interface-specification.md).
