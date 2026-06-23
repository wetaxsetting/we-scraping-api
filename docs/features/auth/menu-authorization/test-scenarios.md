# Menu Authorization — Test Scenarios (Backend)

SIT/UAT scenarios + coverage map. Frontend scenarios:
[wecms-web/docs/features/auth/menu-authorization/test-scenarios.md](../../../../../wecms-web/docs/features/auth/menu-authorization/test-scenarios.md).

---

## 1. Implemented unit coverage (on disk)

### `NavigationServiceTest` (8/8 green)

[NavigationServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/navigation/service/NavigationServiceTest.java)
— the `Visible Menu = Product Tree (by prdt_type) ∩ grants` resolver. Fixture: Banking ▸ Reports ▸
{Daily Cash, Daily Financial, Daily Financial HS, Cash Flow} + Add-on ▸ E-Invoice ▸ {Sales, Expenses}.

| # | Test | Asserts |
|---|---|---|
| N1 | `fAdmin_seesFullProductTree` | F admin → `menu_group_id = WE_CMSVN`; Banking + Add-on tops; 3 F reports in seq order; no `…-hs`; EN label resolved |
| N2 | `mAdmin_sameStructureAsF_lumensGroup` | M admin → `WE_CMSVN_LUMENS`; structurally identical to F; no `…-hs` |
| N3 | `hAdmin_reducedTree_hsReportOnly_noAddon` | H admin → `WE_CMSVN_HAENGSUNG`; Banking only (no Add-on); Reports = `…-hs` only; F-only reports/Add-on absent |
| N4 | `fNonAdmin_withGrants_intersectsTreeAndGrants` | F user granted one leaf → only that leaf survives; parents un-collapse to reach it; Add-on hidden |
| N5 | `hNonAdmin_grantOutsideProductTree_isInert` | H user granted Cash Flow (not in H tree) → empty tree (inert grant) |
| N6 | `hNonAdmin_withHsGrant_seesHsReport` | H user granted `…-hs` → HS report visible |
| N7 | `hNonAdmin_withStandardDfGrant_doesNotSeeHsReport` | H user granted standard DF (not H's leaf) → empty (until V-remap) |
| N8 | `nonAdmin_noGrants_seesNothing` | F user, no grants → product type echoed, empty `top_menus` |

**Status: MATCH** — covers admin bypass, product scope, intersection, parent collapse, the HS split,
language fallback (EN), and degraded states.

### `MenuAuthorizationServiceTest`

[MenuAuthorizationServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationServiceTest.java)

| # | Test | Asserts |
|---|---|---|
| M1 | `grantedMenuCds_returnsOnlyUseYnYRows` | mixed `Y`/`N` rows → only the `USE_YN='Y'` slugs returned |
| M2 | `grantedMenuCds_emptyWhenNoGrants` | no rows → empty list |

### `UserManagementAuthorizationTest` (entitlement save)

[UserManagementAuthorizationTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/users/service/UserManagementAuthorizationTest.java)

| # | Test | Asserts |
|---|---|---|
| E1 | `menuEntitlementSave_byNonAdmin_isDeniedBeforeAnyDbAccess` | non-admin save → `AccessDeniedException` (403/WBE1003) **before** any repo interaction |
| E2 | (admin paths in the same class) | admin context passes the guard and reaches business logic |

### `MenuAuthorizationGuardTest` (cross-reference)

[MenuAuthorizationGuardTest.java](../../../../src/test/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuardTest.java)
covers the `@menuAuthz` 4-step rule (validity → product-tree gate → admin bypass → per-user grant). It
is the **enforcement** guard's coverage — documented in the sibling
[authorization](../authorization/README.md) module; listed here for completeness because the guard
reuses this feature's `MenuAuthorizationService` + catalogue.

---

## 2. SIT / UAT scenarios

### Navigation resolution

| # | Scenario | Expected |
|---|---|---|
| NAV1 | F admin `GET /navigation` | full F tree; `menu_group_id = WE_CMSVN`; Add-on present |
| NAV2 | H admin `GET /navigation` | reduced H tree; `…-hs` Daily Financial; no Add-on / Daily Cash / Cash Flow |
| NAV3 | M admin | same structure as F; `menu_group_id = WE_CMSVN_LUMENS`; VI/KO labels differ from F where the seed diverges |
| NAV4 | Normal user with grants | only granted leaves; parents collapse to surface them |
| NAV5 | Normal user, no grants | empty `top_menus` (frontend shows the "no menus" landing) |
| NAV6 | Grant outside the product tree (e.g. H granted Cash Flow) | inert — no node rendered |
| NAV7 | Unrecognised / null product type (deferred letter / NULL) | well-formed empty tree, `menu_group_id = null` — degrade safe, not an error |
| NAV8 | Language KO / VI | labels in that language; KM / null → EN fallback |
| NAV9 | No / invalid JWT (no `TenantContext`) | `WBE1401` |
| NAV10 | Entitlement changed then `GET /navigation` (no token re-issue) | new grant set takes effect immediately (grants read fresh, no JWT menu claim) |

### Entitlement editor

| # | Scenario | Expected |
|---|---|---|
| ENT1 | `GET /{user_id}/menu-entitlement` admin | `USE_YN='Y'` slugs, insertion order |
| ENT2 | `PUT` admin with a slug list | full-set replace; response echoes the deduped set; old grants gone |
| ENT3 | `PUT` admin empty list `[]` | every grant revoked; response `menu_auth_cds: []` |
| ENT4 | `PUT` `menu_auth_cds: null` | `WBE_VALIDATION` (`@NotNull`) |
| ENT5 | `PUT` slug > 100 chars or blank | `WBE_VALIDATION` (`@Size(1,100)`) |
| ENT6 | `GET`/`PUT` non-admin | `WBE1003` (controller `hasRole('ADMIN')` + service `currentIsAdmin()`) |
| ENT7 | Target user with no active link in tenant | `WBE_USERS_NOT_FOUND` (never leaks cross-tenant existence) |
| ENT8 | Two concurrent `PUT`s for the same user (PK race) | one-shot retry in a fresh tx → last-writer-wins; no partial dataset |
| ENT9 | Duplicate slugs in the request | deduped (LinkedHashSet); single row per slug |
| ENT10 | Cross-tenant `user_id` | `WBE_USERS_NOT_FOUND` |

---

## 3. Coverage notes & gaps

- **MATCH (covered):** navigation resolution (`NavigationServiceTest` 8/8), grant resolution
  (`MenuAuthorizationServiceTest`), entitlement save admin guard (`UserManagementAuthorizationTest`),
  guard rule (`MenuAuthorizationGuardTest`, cross-ref).
- **MISSING:** no dedicated **`NavigationControllerTest`** (`@WebMvcTest` asserting the lowercase
  snake_case wire shape of `NavigationResponse` / `NavMenuNode`) and no dedicated
  **`MenuEntitlementServiceTest`** for the happy-path load/save round-trip, the delete-then-insert
  replace, the dedup, and the `ensureTenantUser` → `WBE_USERS_NOT_FOUND` branch. The save admin guard
  is covered by `UserManagementAuthorizationTest`; the non-auth save semantics are not unit-tested on
  disk. **Recommend adding both.**
- **MISSING:** the **concurrency retry** (ENT8) is asserted only by code review, not a test — a
  `DataIntegrityViolationException`-on-first-call / success-on-retry mock test would pin it.
- **MISSING:** the **per-node country gate** (GAP-2) has no test because the gate is not modelled yet —
  add the scenario with the gate.
- **SIT-only:** language label divergence between F and M (NAV3) needs the real F/M/H catalogue seed on
  a shared DB — `NavigationServiceTest` exercises EN only with a synthetic fixture.
