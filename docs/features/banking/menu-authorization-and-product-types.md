# Banking — Menu Authorization & Product-Type Behavior (Backend)

> **Verified 2026-06-07** against live source + the V243 navigation seed (not docs alone).
> Scope: how the Spring Boot API gates every Banking page/endpoint by **product type
> (F / M / H — the complete WeCMS product-type set)** and by
> **user menu permission** (admin / normal-with-grant / normal-without-grant).
>
> **Authoritative product-type set:** WeCMS has exactly three product types —
> `F` (WeCMS Default), `M` (WeCMS Lumens), `H` (WeCMS HaengSung Electronics). There are no other
> WeCMS product types; treat F/M/H as complete unless source code introduces a new one.
> Frontend counterpart: [wecms-web/docs/features/banking/menu-sidebar-route-guard-behavior.md](../../../../wecms-web/docs/features/banking/menu-sidebar-route-guard-behavior.md).
> Cross-module guard contract: [endpoint-authorization-matrix.md](../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md).

## 1. How authorization is resolved

Identity + product type come **only** from the signed JWT via `TenantContext` — never from
request input ([JwtAuthenticationFilter](../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java),
[TenantContext](../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java)).

- **Product type** — `BUSI_INFM.PRDT_TYPE` stamped at login into the JWT `prdtType` claim.
  `PrdtType.fromCode(raw)` resolves the three WeCMS product types `F` / `M` / `H`; any malformed/blank
  value (including `null`) resolves to `Optional.empty()` and is handled **fail-closed** as a defensive
  guard ([PrdtType.java](../../../src/main/java/com/kosign/wecms/common/enums/PrdtType.java)).
- **Admin vs user** — presence of a `BUSI_ADMN_INFM` row → `authGrCd=ADMIN`; absence → `USER`.
- **Per-user grants** — `USER_MENU_AUTH_INFM` rows with `USE_YN='Y'`, resolved by
  `MenuAuthorizationService.grantedMenuCds(bizKey, userId)`.

### Two enforcement points (cannot drift)

1. **Navigation tree** — `GET /api/v1/navigation` ([NavigationService](../../../src/main/java/com/kosign/wecms/domain/navigation/service/NavigationService.java))
   returns `Product Tree (by prdt_type) ∩ grants` for a normal user, the full Product Tree for an admin.
   Unrecognized/null product type → **empty tree** (well-formed, no error).
2. **Per-endpoint guard** — `@PreAuthorize("@menuAuthz.canAccess(cd)")`
   ([MenuAuthorizationGuard](../../../src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java)).
   Four steps, identical decision to the nav tree: (1) validity (cd must be a product-tree leaf or `settings-*`),
   (2) product-tree gate (cd must be in the **current tenant's** product tree — applies to admin too),
   (3) admin bypasses the per-user grant only, (4) normal user must hold the `USE_YN='Y'` grant.
   Denied → `AccessDeniedException` → HTTP 403 / `WBE1003`. A tenant whose `PRDT_TYPE` is not one of
   F/M/H (data error only — not a valid WeCMS product type) fails this gate and is denied; the nav tree
   returns empty. This is a defensive guard, not a supported product variant.

The frontend is never trusted: a user can bypass the hidden menu and call an endpoint directly,
and the `@menuAuthz` guard still denies it.

## 2. Per-page matrix (verified against V243 seed)

| Banking page | Route | `permission_cd` | F | M | H | Controller / gate |
|---|---|---|:--:|:--:|:--:|---|
| Account Inquiry (VND) | `/banking/account-inquiry` | `banking-vnd-account-inquiry` | ✅ | ✅ | ✅ | `AccountController.listAccounts` — `canAccessAccountInquiry(currency)` (param-aware) |
| Transaction History (VND) | `/banking/transaction-history` | `banking-vnd-inquire-transactions` | ✅ | ✅ | ✅ | `AccountController` tx sub-resource |
| Account Inquiry (FCY) | `/banking/account-inquiry-fcy` | `banking-fcy-update-inquire` | ✅ | ✅ | ✅ | same multiplexed endpoint, FCY param |
| Transaction History (FCY) | `/banking/transaction-history-fcy` | `banking-fcy-inquire-transactions` | ✅ | ✅ | ✅ | tx sub-resource, FCY |
| Own Account Transfer | `/banking/transfer-own-accounts` | `banking-internal-transfer-own` | ✅ | ✅ | ✅ | `OwnAccountTransferController` — `canAccess(...)` |
| Transfer History | `/banking/transfer-history` | `banking-internal-transfer-history` | ✅ | ✅ | ✅ | `TransferHistoryController` — `canAccess(...)` |
| Daily Cash | `/banking/reports/daily-cash` | `banking-reports-daily-cash` | ✅ | ✅ | ❌ | `ReportController.dailyCash` / `DailyCashReportController` |
| Daily Financial (CMC) | `/banking/reports/daily-financial` | `banking-reports-daily-financial` | ✅ | ✅ | ❌ | `ReportController.dailyFinancial` |
| Daily Financial (HS) | `/banking/reports/daily-financial-hs` | `banking-reports-daily-financial-hs` | ❌ | ❌ | ✅ | `ReportController` (HS) — H-only node |
| Cash Flow | `/banking/reports/cash-flow` | `banking-reports-cash-flow` | ✅ | ✅ | ❌ | `CashFlowReportController.search` |
| Smart Organizer (eligible / confirm) | `/banking/smart-organizer` | — (not a menu node) | n/a | n/a | n/a | `SmartOrganizerController` — `canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')` |
| Organizer Settings (rule CRUD) | (modal, no route) | — (allow-listed) | n/a | n/a | n/a | `OrganizerRuleController` — **JWT-auth only**, tenant-scoped (see §4) |

**Key correction:** H is **not** denied Account Inquiry / Transaction History / transfers — those are
in the H tree (`H` = 33 nodes). H lacks **only** Daily Cash, Daily Financial (CMC), and Cash Flow,
and swaps Daily Financial (CMC) for **Daily Financial (HS)** (`rept_0017_001`). F and M are structurally
identical Banking trees (M differs only in VI/KO translations).

## 3. Behavior by case

| Case | Banking top menu | Banking pages | Notes |
|---|---|---|---|
| **Product type H** | Visible | Account Inquiry (VND/FCY), Tx History (VND/FCY), Own Transfer, Transfer History, **Daily Financial (HS)** | Daily Cash / Daily Financial (CMC) / Cash Flow endpoints → 403 (not in H tree, even for H admin) |
| **Product type M** | Visible | Same as F (all 10 list/report pages except HS) | Identical tree to F |
| **Product type F** | Visible | All 10 list/report pages except Daily Financial (HS) | Default WeCMS tree (41 nodes) |
| **Admin user** | Visible (full product tree) | Every page **in the tenant's product tree** | Admin bypasses the per-user grant, **never** the product-tree gate — H admin still 403s on Cash Flow |
| **Normal user + menu permission** | Visible if ≥1 banking grant | Only pages whose `permission_cd` is granted **and** in the product tree | `Product Tree ∩ grants` |
| **Normal user, no menu permission** | Empty (no banking grants) | None — every gated endpoint → 403 | JWT still authenticates (no 401); `@PreAuthorize` returns 403 |
| **Person/user-specific permission** | Per the user's own `USER_MENU_AUTH_INFM` rows | Per-grant | Grants are per `(BIZ_KEY, USER_ID)`; a grant for a `permission_cd` outside the tenant's product tree is **inert** (no node, endpoint still product-gated) |

## 4. Smart Organizer authorization (intentional exception)

Smart Organizer is **not in the product menu universe** (no menu node, no route guard slug) and is
reached contextually from the Transaction History page. Authorization splits in two:

- **Eligible list + confirm** (`SmartOrganizerController`) — gated by `@menuAuthz.canAccessAny(VND tx, FCY tx)`.
  A user without either transaction permission gets 403.
- **Organizer rule CRUD** (`OrganizerRuleController`, the "Organizer Settings" modal) — **JWT-auth only,
  no `@menuAuthz` gate**. It is a **documented allow-list entry** in
  [`EndpointAuthorizationCoverageTest.ALLOWLISTED_CONTROLLERS`](../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java)
  ("new feature X-2"), so the coverage test passes by design. All queries are tenant-scoped by
  `TenantContext.currentBizKey()` → **no cross-tenant exposure**.

**Observation (low severity, not a SIT blocker):** rule CRUD is reachable by any authenticated user of
the tenant even without transaction permission, whereas eligible/confirm require it. The "Go to Organizer
Settings" CTA opens this modal (not a route), so its authorization is inherited from the
caller and not separately gated. Recommend (hardening, post-SIT) gating `OrganizerRuleController` with
the same `canAccessAny(VND tx, FCY tx)` so rule management and rule application share one rule.

## 5. Verdict (backend)

**OK for SIT.** Product-type and permission gating for the complete WeCMS product-type set (F / M / H)
and all four admin/user/grant/person cases is correct and enforced server-side
(nav tree + `@menuAuthz` cannot drift).
The single follow-up is the low-severity Organizer-rule-CRUD authorization-symmetry hardening above —
defense-in-depth is intact (tenant-scoped, eligible/confirm gated), so it is a recommendation, not a fix
required for SIT.
