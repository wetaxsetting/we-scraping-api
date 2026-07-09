# Authorization — Test Scenarios (Backend)

Coverage map + SIT/UAT scenarios for the authorization enforcement layer. Two on-disk tests carry this
slice: the build-time coverage lock-in and the guard unit test. Frontend scenarios:
[wecms-web/docs/features/auth/authorization/test-scenarios.md](../../../../../wecms-web/docs/features/auth/authorization/test-scenarios.md).

---

## 1. On-disk tests

| Test | File | Type |
|---|---|---|
| `EndpointAuthorizationCoverageTest` | [config/EndpointAuthorizationCoverageTest.java](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java) | classpath-scan (no Spring context) |
| `MenuAuthorizationGuardTest` | [domain/navigation/service/MenuAuthorizationGuardTest.java](../../../../src/test/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuardTest.java) | Mockito unit |

---

## 2. `EndpointAuthorizationCoverageTest` (deny-or-declared lock-in)

| # | Scenario | Expected |
|---|---|---|
| CT1 | Scan all `@RestController` under `com.kosign.wecms` | every request-mapped handler is `@menuAuthz`-gated (method- or class-level) **or** its controller is in `ALLOWLISTED_CONTROLLERS` |
| CT2 | A new business handler ships without a `@menuAuthz` gate, not allow-listed | **build fails** — `violations` non-empty (`<Controller>#<method>` listed) |
| CT3 | Scan discovers zero controllers (mis-scoped base package) | sanity assertion fails (`controllers` must be non-empty) — vacuous pass guarded |
| CT4 | A class-level `@PreAuthorize("@menuAuthz.canAccess('...')")` | counts as covering every handler in that controller |
| CT5 | A `@PreAuthorize` that does **not** reference `@menuAuthz` (e.g. `hasRole` only) | does **not** satisfy coverage — the handler must still reference `@menuAuthz` or be allow-listed |
| CT6 (`allowlistHasNoStaleEntries`) | Every name in `ALLOWLISTED_CONTROLLERS` still exists as a scanned `@RestController` | passes; a renamed/deleted allow-listed controller **fails** (prevents a stale exemption masking a future controller) |

The 13 allow-listed exceptions and their buckets are documented in
[functional-specification.md §5.1](functional-specification.md) and listed verbatim in
[source-code-index.md](source-code-index.md).

---

## 3. `MenuAuthorizationGuardTest` (the 4-step rule + overloads)

### Product-tree leaves (`canAccess`)

| # | Scenario | Expected |
|---|---|---|
| G1 | F admin, F-tree leaf in tree | allow (`fAdmin_canAccessFmenuCd`) |
| G2 | H admin, F/M-only leaf (Cash Flow) not in H tree | **deny** — product gate binds admin (`hAdmin_cannotAccessFonlyCd`) |
| G3 | H admin, HS report leaf in H tree | allow (`hAdmin_canAccessHsReportCd`) |
| G4 | H user, HS leaf in tree + holds grant | allow (`hUser_withHsGrant_canAccessHsCd`) |
| G5 | H user, HS leaf in tree, **no** -hs grant (holds standard DF code instead) | **deny** — step 4 grant missing (`hUser_withStandardDfGrant_cannotAccessHsCd`) |

### Settings codes (product-invariant)

| # | Scenario | Expected |
|---|---|---|
| G6 | H user, settings cd (not a product leaf), no grant | **deny** — skips product gate but still needs grant (`settingsCd_normalUser_requiresGrant`) |
| G7 | H admin, settings cd, product repo **not** stubbed | allow — proves step 2 was skipped for settings (`settingsCd_admin_allowedWithoutProductGate`) |

### Unknown / invalid

| # | Scenario | Expected |
|---|---|---|
| G8 | F admin, unknown cd (neither product leaf nor `settings-`) | **deny** — unconditional fail-closed even for admin (`unknownCd_isDenied`) |
| G9 | null / blank cd | **deny** — short-circuits before any repo call (`blankOrNullCd_isDenied`) |

### Param-aware overloads (F-3 §4.1)

| # | Scenario | Expected |
|---|---|---|
| G10 | `canAccessAccountInquiry("VND")` | resolves `banking-vnd-account-inquiry` → allow (`accountInquiry_vnd_resolvesVndCd`) |
| G11 | `canAccessAccountInquiry(null)` / `"  "` | defaults to VND cd → allow (`accountInquiry_missing_defaultsToVnd`) |
| G12 | `canAccessAccountInquiry("FCY")` / `"USD"` | resolves `banking-fcy-update-inquire` → allow (`accountInquiry_fcy_resolvesFcyCd`) |
| G13 | `canAccessEInvoice("SALES")` | resolves `addon-einvoices-sales` → allow (`einvoice_sales_resolvesSalesCd`) |
| G14 | `canAccessEInvoice("EXPENSE")` / `"EXPENSES"` | resolves `addon-einvoices-expenses` → allow (`einvoice_expense_resolvesExpensesCd`) |
| G15 | `canAccessEInvoice("BOGUS")` / null / `""` | **fail closed** (`einvoice_unknownOrMissingType_failsClosed`) |
| G16 | H caller, `canAccessEInvoice("SALES")` (e-Invoice is F/M only) | **deny** by product gate (`einvoice_hCaller_deniedByProductGate`) |
| G17 | `canAccessAny(VND, FCY)` where only FCY resolves | allow (`canAccessAny_allowsWhenAnyCodeResolves`) |
| G18 | `canAccessAny` where no code resolves / null array | **deny** (`canAccessAny_deniesWhenNoCodeResolves`) |

---

## 4. SIT / UAT scenarios (end-to-end)

| # | Scenario | Expected |
|---|---|---|
| Z1 | User with the screen grant calls the gated endpoint | 200 — handler runs |
| Z2 | Authenticated user **without** the grant | 403 `WBE1003`, standard envelope |
| Z3 | No / invalid / expired JWT | 401 (`jwt-authentication` entry point) — **not** 403 |
| Z4 | Admin calls a screen in their product | 200 — admin bypass (step 3) |
| Z5 | Admin calls an F/M-only screen as an **H** tenant | 403 `WBE1003` — product gate (step 2) binds admin |
| Z6 | VND-only user calls Account Inquiry `?currency=FCY` | 403 `WBE1003` — `canAccessAccountInquiry` resolves the FCY cd |
| Z7 | F user calls e-Invoice with no/unknown `type` | 403 `WBE1003` — `canAccessEInvoice` fails closed |
| Z8 | Settings > Users write by a non-admin | 403 — `@userMgmtAccess` / `hasRole('ADMIN')` stacked gate denies |
| Z9 | Cross-tenant probe (foreign screen / cd) | 403 `WBE1003` — `bizKey` is from the JWT, never the request; no tenant-existence leak |
| Z10 | Allow-listed endpoint (e.g. `/api/v1/navigation`, `/api/v1/auth/me`) with valid JWT | 200 — no `@menuAuthz` gate by design |

---

## 5. Coverage notes & gaps

- **Guard logic is fully unit-covered** by `MenuAuthorizationGuardTest` (the 4-step rule, admin bypass,
  settings skip, fail-closed unknown/blank, and all three overloads incl. the H product-gate denial).
- **Coverage invariant is build-enforced** by `EndpointAuthorizationCoverageTest` — the strongest
  guarantee in the suite: an un-gated business endpoint cannot ship.
- **No end-to-end `@SpringBootTest` slice** asserting the 401-vs-403 boundary or the stacked role gates
  through the real filter chain is on disk in this pass (MISSING). A `@WebMvcTest` / `@SpringBootTest`
  asserting (a) 403 `WBE1003` envelope on a denied gated endpoint, (b) 401 on a missing JWT, and (c) the
  `hasRole('ADMIN') and @menuAuthz` composition for Settings > Users would close it. The role-derivation
  half (JWT `authGrCd` → `ROLE_*`) is exercised by the `jwt-authentication` filter tests.
- **Per-product behavioural gates are not yet implemented** (PARTIAL, GAP-1), so there are no behavioural-gate
  tests to write here — they arrive with each feature MR.
