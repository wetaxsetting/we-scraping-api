# WeInvoice Tax Codes Belong to the User Account, Not the WeCMS Tenant

**Date:** 2026-11-05
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** READY FOR MANUAL COMMIT (local end-to-end check pending)
**Investigation Status:** COMPLETE · **Approval Status:** APPROVED (developer, 2026-11-05: "Đồng ý, chuyển") · **Implementation Status:** COMPLETE · **Test Status:** PASS (unit; end-to-end pending)
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Issue:** WeInvoice P1 — design correction to [weinvoice-api-implementation-2026-11-02.md](../features/addon-service/weinvoice/weinvoice-api-implementation-2026-11-02.md)

## Problem

UI Spec v1.0 (`02_Yeu_cau_nghiep_vu.md` line 43): *"Một tài khoản quản lý nhiều MST"* — one **account**
manages several tax codes, and a user may hold tax codes of several unrelated companies. The P1
implementation scoped every WeInvoice row to the WeCMS tenant (JWT `BIZ_KEY`): all users of one WeCMS
company shared one tax-code list, and one user could not manage tax codes of other companies. Linking a
tax code to a WeCMS `BIZ_KEY` is WeCMS's own concern (its "Update" button through the Partner API, which
works by `tax_id` and is unaffected).

## Current Behavior

- 8 tables keyed by `biz_key`: `winv_cmpn_infm`, `winv_engn_rqst`, `winv_tag_infm`, `winv_invc_tag`,
  `winv_invc_file`, `winv_lkup_cd`, `winv_upld_infm`, `winv_user_pref` (the last one by `biz_key, user_id`).
- `CompanyAccessGuard.bizKey()` reads `TenantContext.currentBizKey()`; 25 Java files carry it.
- Adding / changing / removing a tax code needs WeCMS `ROLE_ADMIN` (`WeInvoiceCompanyController`).
- The 5-tax-code quota counts per tenant.

## Expected Behavior

- Every WeInvoice row is owned by the signed-in **user** (`TenantContext.currentUserId()`, WeCMS
  `USER_INFM.USER_ID`, varchar 100). A user sees and manages only their own tax codes; two users may hold
  the same tax code independently.
- Any signed-in user manages their own tax codes (no WeCMS admin role). Sub-accounts and the
  8-permission model stay P2 as the spec says.
- Quota (5) counts per user. Partner API unchanged.

## Scope

Schema-of-record `V1__winv_schema.sql` (`biz_key varchar(20)` → `owner_id varchar(100)`;
`winv_user_pref` keyed by `owner_id` alone), the WeInvoice Java module, its tests, feature docs.

## Out of Scope

WeCMS-side mapping between `BIZ_KEY` and tax codes; sub-accounts / permissions (P2); engine worker.

## Database Impact

FLYWAY MIGRATION (schema-of-record edit). `V1__winv_schema.sql` was never applied to SIT / UAT / PROD;
its only application is the developer's local engine database (2026-11-05, same day, 144 provider rows +
1 hand-made mapping row). Editing V1 instead of adding V2 is therefore safe and keeps one clean file.
The local `weinvoice` schema is dropped and recreated with the developer's approval — only that schema;
the engine tables in `public` are not touched.

| Environment | Required | Executor | Status | Executed Date | Verification |
|---|---|---|---|---|---|
| LOCAL (engine DB) | Yes | DEV TEAM (AI assistant, approved 2026-11-05) | COMPLETE | 2026-11-05 | schema recreated; 1 row `winv_cmpn_infm` (`owner_id=lamine@gmail.com` — the developer's login, exists in `wecmsdevdb.user_infm` — MST 1201496252, `NEED_PASSWORD`, default) → 10 403 engine invoices in scope |
| SIT / UAT / PROD | Yes, when WeInvoice is enabled there | DBA TEAM | NOT STARTED | - | - |

## Implementation

- `V1__winv_schema.sql`: `biz_key varchar(20)` → `owner_id varchar(100)` in all 8 tables (keys and
  indexes follow); `winv_user_pref` keyed by `owner_id` alone (its separate `user_id` column dropped);
  table comments say "user" instead of "tenant".
- `CompanyAccessGuard.ownerId()` (was `bizKey()`) returns `TenantContext.currentUserId()`; no user →
  `AccessDeniedException`. `tenantMsts()` → `ownedMsts()`.
- Mechanical rename in the 25 WeInvoice main files and their tests: `bizKey` → `ownerId`,
  `biz_key` → `owner_id`. `PreferenceRepository` rewritten to one row per user; `MeService` adapted.
- `WeInvoiceCompanyController`: the four `@PreAuthorize("hasRole('ADMIN')")` removed.
- Javadoc wording "tenant" → "user"; Partner API untouched (no owner there).
- Docs: feature `README.md`, `interface-specification.md`, `test-scenarios.md` (T2, T3, T4),
  `EndpointAuthorizationCoverageTest` allow-list comment, design note `weinvoice-api-and-data-2026-11-02.md`.

## Files Changed

`src/main/resources/db/weinvoice/V1__winv_schema.sql`; 25 files under
`src/main/java/com/kosign/wecms/domain/weinvoice/`; `CompanyAccessGuardTest` and other WeInvoice tests
(rename); `EndpointAuthorizationCoverageTest`; the docs above.

## API Impact

Same endpoints and payloads. Behaviour: the tax-code list, tags, uploads, lookup codes, files,
preferences and engine requests are per signed-in user; tax-code writes no longer return 403 for
non-admin users.

## Verification

- Automated: WeInvoice 89/89 PASS (2 new regression tests); full suite 2 882 tests, 1 pre-existing
  failure only. Details: [test result](../testing/results/weinvoice-company-ownership-by-user-test-result-2026-11-05.md).
- Local DB: schema recreated, 144 providers, engine tables unchanged (16 481 invoices).
- Mapping row created for `lamine@gmail.com` (developer's login).
- End-to-end with WeInvoice enabled: NOT RUN — needs the developer's DB password.

## Test Evidence

[weinvoice-company-ownership-by-user-test-result-2026-11-05.md](../testing/results/weinvoice-company-ownership-by-user-test-result-2026-11-05.md)

## Result

```
Code: COMPLETE · Unit tests: PASS · Local schema: RECREATED
Local user→MST mapping: DONE (lamine@gmail.com → 1201496252)
End-to-end check: PENDING (WECMS_WEINVOICE_DB_PASSWORD to be filled by the developer)
Commit / push: PENDING (developer)
```
