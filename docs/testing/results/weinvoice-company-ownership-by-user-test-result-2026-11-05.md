# WeInvoice Tax-Code Ownership by User — Test Result

**Date:** 2026-11-05
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Related Record:** [../../issues/weinvoice-company-ownership-by-user-2026-11-05.md](../../issues/weinvoice-company-ownership-by-user-2026-11-05.md)
**Issue:** WeInvoice P1 — ownership correction

## Scope

Ownership of every WeInvoice row moved from the WeCMS tenant (`biz_key`) to the signed-in user
(`owner_id` = JWT user id); admin gate removed from tax-code management; schema-of-record and the local
engine database updated.

## Environment

LOCAL — Windows 11, JDK 21.0.11, Gradle 8.14.4 `--offline`; local `application-local.yml` + `.env.local`
present (git-ignored). Engine PostgreSQL used by the developer's `we-scraping` job.

## Code / Build Under Test

Branch: local `sit` (after `fc4fab6`) + working tree · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*' --tests '*EndpointAuthorizationCoverageTest'` | PASS | 89 WeInvoice tests (87 + 2 new) + 2 coverage, 0 failures |
| `./gradlew test` (full suite) | FAIL — 1 pre-existing only | 2 882 tests, 1 failure, 21 skipped. The failure is `VatDeclarationLifecycleTest$Create.negativeRevenueSyncs`, which also fails on an unchanged `origin/sit` export ([earlier record](weinvoice-api-sit-rebase-test-result-2026-11-05.md)). The 12 `profile=local` failures listed there no longer occur because this machine now has `application-local.yml` |

## Regression Tests

Did a regression test cover the change? **YES** — `CompanyAccessGuardTest`:
- `ownerIsTheSignedInUserNotTheTenant`: the guard resolves the owner from the user id and never queries
  by `BIZ_KEY`.
- `colleaguesOfOneCompanyHaveSeparateLists`: two users with the same `BIZ_KEY` get separate tax-code
  lists, and one cannot use the other's MST (`WBE3002`).
The four existing guard tests were rewritten to the user owner.

## Database Verification

LOCAL engine DB, executed with the developer's approval:
- Pre-check: `weinvoice` held only the 144 seeded providers and the 1 hand-made `biz_key` mapping row
  (all other tables 0 rows); the only objects outside the schema depending on it were its own 7
  `pg_toast` tables; no view elsewhere referenced it.
- One transaction: `DROP SCHEMA weinvoice CASCADE` → `V1__winv_schema.sql` (edited) → provider seed.
- Post-check: ownership column `owner_id` in 8 tables, no `biz_key`; 144 providers;
  `public.invoices` 16 481 rows before and after.
- Mapping row created afterwards for the developer's login `lamine@gmail.com` (MST 1201496252): 10 403 engine invoices in scope.

## Not Tested

- Running application with WeInvoice enabled (blocked: `WECMS_WEINVOICE_DB_PASSWORD` is for the
  developer to fill; mapping row pending) — so no WeInvoice SQL has run against PostgreSQL yet.
- SIT / UAT / PROD: untouched.

## Final Result

PARTIAL — code, tests and local schema done; end-to-end check waits on the password and the user id.

## Recommendation

READY FOR MANUAL COMMIT for the code; end-to-end verification after the developer supplies their
`USER_ID` and fills the DB password.
