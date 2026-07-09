# Authorization — Refactor Gap Analysis (Parity Review)

Legacy JEX permission / role enforcement vs. the refactor authorization layer (`@menuAuthz` +
`@PreAuthorize` + role gates + product-tree gate + coverage test). Status vocabulary: **MATCH** ·
**APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

The permission-code catalogue + grant data model parity is the sibling
[menu-authorization/refactor-gap-analysis.md](../menu-authorization/refactor-gap-analysis.md)'s concern.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **`@PreAuthorize` endpoint gating** | menu-visibility + per-JSP checks | `@menuAuthz.canAccess(...)` on 30/43 controllers; 13 allow-listed | **MATCH** (F-3 complete, 2026-06-06) |
| **4-step rule (validity → product → admin → grant)** | menu tree ∩ grant, admin sees all in product | `MenuAuthorizationGuard.canAccess` mirrors the navigation visibility rule | **MATCH** |
| **Per-user grant (`USER_MENU_AUTH_INFM`)** | `USE_YN='Y'` rows | `MenuAuthorizationService.isGranted` (step 4) | **MATCH** |
| **Admin bypass** | admin sees all menus in product | `currentIsAdmin()` skips step 4 (never step 2) | **MATCH** |
| **Role gates (`ROLE_ADMIN`, `@userMgmtAccess`)** | admin-only screens | `hasRole('ADMIN')` + `@userMgmtAccess` stacked on `@menuAuthz`, not weakened | **MATCH** |
| **Authority resolution (`BUSI_ADMN_INFM` presence ⇒ ADMIN)** | admin-master table | resolved at login → `authGrCd` JWT claim → `ROLE_*` + `currentIsAdmin()` | **MATCH** |
| **Product-TREE gate** | per-product menu tree (`WE_CMSVN`/`_LUMENS`/`_HAENGSUNG`) | step 2 `PRODUCT_MENU.existsInProductTree(currentPrdtType(), cd)`, binds admin + user | **MATCH** |
| **`settings-*` product-invariant** | settings identical across F/M/H | step 1 prefix admits, step 2 skips product gate | **MATCH** |
| **Param-aware multiplex gates** | one menu per screen | `canAccessAny` / `canAccessAccountInquiry` / `canAccessEInvoice` (each runs full 4-step) | **MATCH** |
| **Build-time coverage assurance** | convention only | `EndpointAuthorizationCoverageTest` deny-or-declared | **APPROVED DEVIATION** (stronger; refactor addition) |
| **Server-side explicit enforcement** | menu-hiding (UX) + thin checks | explicit `@PreAuthorize` funnel | **APPROVED DEVIATION** (stronger) |
| **Identity / product source** | server session (`WbUser`) | stateless signed JWT (`TenantContext`) | **APPROVED DEVIATION** (architecture; see jwt-authentication) |
| **Authority refresh timing** | session-bound | next-login (JWT signed/immutable) | **APPROVED DEVIATION** (consequence of stateless) |
| **Allow-listed un-gated controllers** | n/a | 13 documented exceptions (auth/session, nav resolver, View Setting, notifications, reference data, refactor-new, EFS callbacks) | **APPROVED DEVIATION** (documented exceptions) |
| **Per-product BEHAVIOURAL gates** (table-2, masking, hidden cols, Lumens readonly, e-Invoice gate) | branched on `PRDT_TYPE` | product-*tree* gate live; behavioural gates not in this phase | **PARTIAL** (deferred-per-feature) (GAP-1) |
| **Scattered per-JSP checks** | per-screen JSP logic | consolidated into one `@menuAuthz` funnel | **DEAD LEGACY** (intentional) |

---

## Specially-verified gaps

### GAP-1 — Per-product behavioural gates deferred-per-feature (PARTIAL)

Legacy branched *behaviour* on `BUSI_INFM.PRDT_TYPE`:

| Product | Deferred behaviour | Lands with |
|---|---|---|
| `H` (HAENGSUNG) | payment-table-2 layout · account masking for bank `11704020` · hidden expense file column · daily-financial-HS report gating | Payment / Banking Transfer / Add-on Expense / Reports MRs |
| `F` (WeCMS Default) | e-Invoice access gate | Add-on e-Invoice MR |
| `M` (Lumens) | readonly-field overrides | the first form whose readonly rules differ for Lumens |

This authorization-infrastructure phase ships the product-**tree** gate (step 2) — so an `H` caller is
correctly denied an F/M-only leaf cd (Cash Flow, Daily Cash, e-Invoices) at the endpoint, and vice
versa. The product-**behavioural** gates above are **not** part of the enforcement layer; each ports
with its feature MR. `PrdtType` models only `F`/`M`/`H`; the 11 other legacy letters round-trip verbatim
and `fromCode(...)` returns empty for them so any future gate degrades safely. **Status: PARTIAL** —
infrastructure only this phase; behaviour lands per feature. Owner: per-feature MR + PO.

### Allow-listed controllers (APPROVED DEVIATION — documented exceptions)

13 controllers are intentionally not `@menuAuthz`-gated; the `EndpointAuthorizationCoverageTest`
allow-list enumerates them with reasons (auth/session, the nav resolver itself, the View Setting
preference store, the notifications bell, cross-screen reference data, refactor-new features with no
menu cd yet, and inbound EFS machine-to-machine callbacks gated by IP-whitelist). Each is a deliberate,
reviewable entry — not a "gate later" dumping ground. The companion test
`allowlistHasNoStaleEntries` prevents a renamed/deleted controller from leaving a stale exemption.
**Status: APPROVED DEVIATION** — see [functional-specification.md §5.1](functional-specification.md).

### Build-time coverage test (APPROVED DEVIATION — stronger than legacy)

`EndpointAuthorizationCoverageTest` has no legacy equivalent. It enforces deny-or-declared at build
time: a new business endpoint without a `@menuAuthz` gate fails the build. **Status: APPROVED
DEVIATION** — a refactor strengthening, fully in the spirit of the legacy model.

### Stale `SecurityConfig.passwordEncoder()` Javadoc (non-blocking cleanup)

The bean comment still says "always writes BCrypt … flags legacy hashes for transparent upgrade via
`upgradeEncoding()`". This is **stale** — `WecmsPasswordEncoder` is now verify-only SHA-256 with no
write-back (coexistence rule, 2026-06-10; see the change-password / login docs). It does **not** affect
authorization behaviour (password verification is the authentication layer). Recommend a one-line
comment fix in the next stabilisation pass. **Status: DEAD LEGACY** (the BCrypt-upgrade path the comment
describes is gone).

---

## Migration status

```
Refactor Status: complete for the enforcement layer (F-3, 2026-06-06);
                 per-product BEHAVIOURAL gates (GAP-1) deferred-per-feature.
```

Evidence for closed items:

- **`@menuAuthz` on 30/43 controllers; 13 allow-listed** — locked in by
  [EndpointAuthorizationCoverageTest](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java)
  (deny-or-declared, fails the build on a gap).
- **4-step rule + all three overloads** — covered by
  [MenuAuthorizationGuardTest](../../../../src/test/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuardTest.java).
- **Role gates + authority resolution** — `ROLE_ADMIN`/`ROLE_USER` from `authGrCd`; `BUSI_ADMN_INFM`
  presence ⇒ ADMIN.
- **Product-tree gate** — `PRODUCT_MENU.existsInProductTree`, binds admin + user (H denied F/M-only).

Residual (non-blocking, tracked):
1. GAP-1 — per-product behavioural gates port with each feature MR (PARTIAL).
2. Stale `SecurityConfig.passwordEncoder()` Javadoc cleanup (does not affect authorization).

Neither residual item affects the implemented enforcement layer's parity or correctness.
