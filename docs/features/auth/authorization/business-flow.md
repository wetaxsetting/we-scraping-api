# Authorization — Business Flow (Backend)

Request-time enforcement flow for the authorization layer, plus a parity narrative against the legacy
JEX permission / role model. Source of truth: the implemented Spring code
([MenuAuthorizationGuard](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java),
[SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java),
[TenantContext](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java)).

```
HTTP request  Authorization: Bearer <jwt>
      │
      ▼
SecurityFilterChain (SecurityConfig — STATELESS, CSRF off, CORS on)
      │   CallbackIpWhitelistFilter → JwtAuthenticationFilter
      │     • parse JWT → set Authentication (authorities ROLE_ADMIN | ROLE_USER from authGrCd)
      │     • populate TenantContext (userId, userNm, bizKey, langCd, authGrCd, prdtType)
      ▼
@PreAuthorize SpEL on the business controller (method security; @EnableMethodSecurity)
      │
      ├─ @menuAuthz.canAccess('<slug>')        ──►  MenuAuthorizationGuard
      │                                              ├─ (1) validity: MENU_ITEM.existsByPermissionCd OR settings- prefix
      │                                              ├─ (2) product-tree gate: PRODUCT_MENU.existsInProductTree(prdtType, cd)
      │                                              ├─ (3) admin bypass: TenantContext.currentIsAdmin()
      │                                              └─ (4) per-user grant: MenuAuthorizationService.isGranted(bizKey, userId, cd)
      │                                                       └─ USER_MENU_AUTH_INFM (USE_YN='Y')
      ├─ hasRole('ADMIN')   (stacked, optional)
      └─ @userMgmtAccess.*  (stacked, Settings>Users)
      │
      ├─ allow  → controller handler runs
      └─ deny   → AccessDeniedException → GlobalExceptionHandler → 403 WBE1003
```

All identity + product context comes from the **signed JWT** via `TenantContext` — the route/body/query
carries no `bizKey`, `userId`, `authGrCd`, or `prdtType`.

---

## 1. Public vs. authenticated vs. authorized

`SecurityConfig.filterChain` establishes three tiers:

1. **Public (permit-all)** — `/api/v1/auth/login`, `/api/v1/auth/logout`, `/actuator/health`, and
   `/api/v1/payment/dispatch/callbacks/**` (inbound EFS callbacks; the gateway cannot present a WeCMS
   JWT — authenticated by the per-VAN crypto check + the `CallbackIpWhitelistFilter` source-IP
   allow-list). No JWT, no `@menuAuthz`.
2. **Authenticated** — `anyRequest().authenticated()`. A valid JWT is required; missing/invalid → 401
   from `JwtAuthenticationEntryPoint` (the `jwt-authentication` layer).
3. **Authorized** — `@PreAuthorize` method security on the business controller. This is the
   authorization layer documented here; it runs **after** authentication has populated the context.

---

## 2. Enforcement decision (per request)

For a `@menuAuthz`-gated business endpoint, the guard evaluates the 4-step rule (full detail in
[functional-specification.md §1](functional-specification.md)):

1. **Validity** — is the cd a product-tree leaf (`MENU_ITEM`) or a `settings-*` code? If neither → deny
   (fail-closed; covers a misannotated endpoint).
2. **Product-tree gate** — for a product leaf, is it in *this tenant's* product tree (`PRODUCT_MENU` for
   `currentPrdtType()`)? If not → deny (binds admin **and** user). Settings codes skip this.
3. **Admin bypass** — `currentIsAdmin()` → allow (admin skips the per-user grant but not the product
   gate).
4. **Per-user grant** — normal user must hold a `USE_YN='Y'` row in `USER_MENU_AUTH_INFM`.

The coarse / param-aware overloads (`canAccessAny`, `canAccessAccountInquiry`, `canAccessEInvoice`)
resolve one or more concrete cds and run the same 4-step rule per cd
([functional-specification.md §2](functional-specification.md)).

---

## 3. Authority resolution (at login, carried in the JWT)

Authorization does not re-query authority per request — it reads the signed claim. The authority itself
is resolved once, at login finalize:

```
AuthService.login (jwt-authentication layer)
   └─ busiAdmnRepo.existsByBizKeyAndUserId(bizKey, userId)
        present  → authGrCd = "ADMIN"
        absent   → authGrCd = "USER"
   └─ JWT claim authGrCd stamped (signed, immutable for the token's life)

per request:
   JwtAuthenticationFilter → ROLE_ADMIN | ROLE_USER authority + TenantContext.authGrCd
```

Because the claim is signed, a row added to / removed from `BUSI_ADMN_INFM` takes effect at the **next
login** — the in-flight token is not re-evaluated. Authority is per-(company, user): the same user can
be ADMIN at one `BIZ_KEY` and USER at another.

---

## 4. Parity narrative (legacy JEX → refactor)

### 4.1 `@PreAuthorize` gating — MATCH (F-3 complete)

Legacy JEX gated screens through **menu visibility** + per-screen JSP checks: a user only saw menus
they were entitled to (`USER_MENU_AUTH_INFM`), and the per-product menu trees
(`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`, role of `MENU_AUTH_ADMN_SYS`) scoped which menus
a product could show at all. Admins saw everything in their product.

The refactor reproduces this **server-side** as explicit `@PreAuthorize("@menuAuthz.canAccess('...')")`
gates on every business endpoint (30 of 43 controllers; 13 documented allow-list exceptions), backed by
the same grant table (`USER_MENU_AUTH_INFM`) and the refactor product-tree catalogue
(`MENU_ITEM` / `PRODUCT_MENU`). The product-tree gate (admin + user), admin bypass, and per-user grant
collectively reproduce the legacy visibility decision — and the guard deliberately mirrors the
navigation visibility rule so enforcement and visibility cannot drift. The `EndpointAuthorizationCoverageTest`
makes it impossible to ship an un-gated business endpoint. **Status: MATCH** (F-3 complete, 2026-06-06).
This is *stronger* than legacy (legacy relied on menu-hiding for UX, with thinner server enforcement);
the refactor makes the server decision explicit and build-checked — an **APPROVED DEVIATION** in
strength, MATCH in outcome.

### 4.2 Role gates (`ROLE_ADMIN` / `@userMgmtAccess`) — MATCH

Legacy distinguished company admins (`BUSI_ADMN_INFM`) from normal users; admin-only screens (user
administration) were admin-gated. The refactor derives `ROLE_ADMIN` / `ROLE_USER` from the JWT
`authGrCd` claim (resolved from `BUSI_ADMN_INFM`) and stacks `hasRole('ADMIN')` / `@userMgmtAccess` on
top of `@menuAuthz` for those screens. **Status: MATCH.**

### 4.3 Product-type behavioural gates — PARTIAL (deferred-per-feature)

Legacy branched *behaviour* on `BUSI_INFM.PRDT_TYPE`: HAENGSUNG (`H`) used a different payment-table-2
layout, masked account numbers for bank `11704020`, hid an expense file column, and gated a dedicated
daily-financial-HS report; WeCMS-Default (`F`) gated e-Invoice access; Lumens (`M`) applied readonly
field overrides.

The refactor implements the product-*tree* gate (a leaf cd outside the tenant's product is denied — so
an `H` caller can't reach F/M-only Cash Flow / e-Invoices) as part of this infrastructure phase. The
product-*behavioural* gates above are **deferred** and port with each feature MR. `PrdtType` models only
F/M/H today; the 11 other legacy letters round-trip verbatim. **Status: PARTIAL** (deferred-per-feature)
— see [refactor-gap-analysis.md](refactor-gap-analysis.md).

### 4.4 Permission-code catalogue + grant data

The legacy menu-id → refactor kebab-slug mapping and the `USER_MENU_AUTH_INFM` grant model are **not**
this module's concern — they belong to the sibling [menu-authorization](../menu-authorization/README.md).
This module is the enforcement *mechanism* that consumes them.

---

## 5. Invariants (every enforcement decision)

- **Context is read only from the signed JWT** via `TenantContext` — never a request body/param/header,
  never the frontend's `StoredUser`. The frontend's `lib/menuAuth.ts` gating is UX only, never a
  security boundary.
- **Fail closed.** An unknown/typo'd cd, a null `prdtType`, a null `bizKey`/`userId`, or an unrecognised
  param discriminator (e-Invoice `type`) all → deny. A misannotated endpoint denies rather than opens.
- **Product gate binds admins too.** Admin bypass skips only the per-user grant (step 4), never the
  product-tree gate (step 2).
- **No per-request DB write.** Authorization reads `MENU_ITEM` / `PRODUCT_MENU` / `USER_MENU_AUTH_INFM`
  read-only; `BUSI_ADMN_INFM` is read at login, not per request.
- **Build-time lock-in.** `EndpointAuthorizationCoverageTest` fails the build for any un-annotated,
  non-allow-listed business handler.
