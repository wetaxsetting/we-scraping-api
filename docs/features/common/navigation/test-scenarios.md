# navigation — Test Scenarios (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

`NavigationServiceTest` (Mockito, `@ExtendWith(MockitoExtension.class)`) covers the
`Visible Menu = Product Tree (by prdt_type) ∩ grants` resolver. 8 cases, all green.

## Fixture

A focused slice of the real catalogue (`NavigationServiceTest.java:56-138`):

- **Banking ▸ Reports ▸** {Daily Cash, Daily Financial, Daily Financial HS, Cash Flow}
- **Add-on ▸ E-Invoice ▸** {Sales, Expenses}
- **F/M membership** (`fmMembership`): full slice, **no HS node**, with the Add-on subtree.
- **H membership** (`hMembership`): Banking ▸ Reports ▸ **Daily Financial HS only** — no Add-on, no Daily
  Cash / standard Daily Financial / Cash Flow.
- Tenant `VN_VNM0004_000001`, user `arthurmorgan@rdr2.com`; session set via
  `TenantContext.set(USER, USER, TENANT, LangCd.EN, authGrCd, prdtType)`.

## Cases

| # | Test | Asserts |
|---|---|---|
| 1 | `fAdmin_seesFullProductTree` | `productType=F`, `menuGroupId=WE_CMSVN`; top menus = `[banking, addon-service]` in seq order; Reports carries the 3 F reports in seq order; no `…-daily-financial-hs`; label = the EN translation (`"EN banking"`). |
| 2 | `mAdmin_sameStructureAsF_lumensGroup` | `menuGroupId=WE_CMSVN_LUMENS`; same top menus as F; Reports has 3 children; no `…-daily-financial-hs`. M is structurally identical to F. |
| 3 | `hAdmin_reducedTree_hsReportOnly_noAddon` | `menuGroupId=WE_CMSVN_HAENGSUNG`; top menus = `[banking]` only (no Add-on); Reports = `[banking-reports-daily-financial-hs]` only; none of the F-only reports / Add-on leaked in. |
| 4 | `fNonAdmin_withGrants_intersectsTreeAndGrants` | Granting only `banking-reports-daily-financial` → that single leaf survives; parents un-collapse to reach it; Add-on hidden (no grant); ungranted reports gone. |
| 5 | `hNonAdmin_grantOutsideProductTree_isInert` | H user granted `banking-reports-cash-flow` (NOT in the H tree) → `topMenus` is **empty**. Out-of-tree grant renders nothing. |
| 6 | `hNonAdmin_withHsGrant_seesHsReport` | H user granted `banking-reports-daily-financial-hs` → sees exactly the HS Daily Financial. |
| 7 | `hNonAdmin_withStandardDfGrant_doesNotSeeHsReport` | H user granted the **standard** `banking-reports-daily-financial` → `topMenus` **empty** (the standard code is not the H tree's leaf; inert until remapped — migration V244). |
| 8 | `nonAdmin_noGrants_seesNothing` | F non-admin with zero grants → `productType=F`, `topMenus` empty. |

## What the suite proves

- **Product scope** — F/M get the full slice, H gets the reduced tree (cases 1–3).
- **Admin bypass** — admin sees the full product tree without per-user grants (cases 1–3).
- **Intersection** — non-admin sees only granted leaves; parents un-collapse to reach a surviving leaf
  (case 4).
- **Out-of-tree inert** — a grant outside the product tree shows nothing (case 5).
- **No-grants empty** — non-admin with no grants gets an empty tree (case 8).
- **HS Daily-Financial split** — the `-hs` code is the H tree's leaf; the standard code is inert for H
  (cases 6 + 7), which is exactly why H-tenant legacy grants are remapped by migration.

## Coverage gaps / not yet tested (deferred)

- Controller-level / `@SpringBootTest` HTTP test of `GET /navigation` (only the service is unit-tested).
- `WBE1401` unauthenticated path (missing `userId`/`bizKey`).
- Unknown / null product → empty-tree safe-degrade branch.
- KO/VI label overlay + KM→EN fallback (the fixture uses EN only).
- `@menuAuthz` guard behaviour is covered separately by `EndpointAuthorizationCoverageTest` (build-failing
  coverage lock), not by `NavigationServiceTest`.
