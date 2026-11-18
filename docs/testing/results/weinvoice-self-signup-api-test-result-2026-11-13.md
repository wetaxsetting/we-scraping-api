# WeInvoice Self Sign-up — API Test Result

**Date:** 2026-11-13
**Type:** TEST RESULT
**Status:** PASS (LOCAL) · move script pending
**Repository:** wecms-api (`we-scraping-api`)
**Related Record:** [weinvoice-self-signup-2026-11-13](../../features/addon-service/weinvoice/weinvoice-self-signup-2026-11-13.md)

## Scope

`WeInvoiceAccountService` (sign-up, login, `/me`), the routing in `AuthService` (login / `/me` / logout),
`POST /api/v1/auth/signup`.

## Environment

LOCAL (Windows, Gradle offline, JDK 21 toolchain). No database: Mockito unit tests.

## Code / Build Under Test

Branch: `sit` (local working tree) · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `WeInvoiceAccountServiceTest` (new, incl. table-missing fallback) | PASS | 13/13 (re-run after the fallback) |
| `AuthServiceTest` (3 new: WeInvoice first, fall-through, `/me` routing) | PASS | 26/26 |
| auth + security + `EndpointAuthorizationCoverageTest` + `ComponentBeanNameUniquenessTest` | PASS | 10 classes, 67 tests, 0 failed |
| `./gradlew test --offline` (full) | 1 FAIL (pre-existing) | 2997 tests, 1 failed, 21 skipped |

## Regression Tests

`notAWeInvoiceAccount_fallsThroughToWeCmsLogin` pins the WeCMS login path when the id is not a WeInvoice account;
`signup_rejectsAnEmailThatIsAWeCmsLogin` pins that sign-up cannot shadow a WeCMS login;
`login_movedWeCmsAccount_keepsItsStoredIdAsSubject` pins the owner id of a moved account.

## Database Verification

V4 applied on the LOCAL shared engine DB by the developer (table + 2 indexes). End-to-end run against it (backend
:8080 rebuilt): sign-up, login (upper-cased email), `/me`, wrong password (`fail_cnt` 1), duplicate email `WBE3040`,
WeCMS id `lamine@gmail.com` refused `WBE3040`, weak password `WBE3039`, logout then `/me` 401 — all PASS; row stored
with BCrypt and `reg_src = SIGNUP`. Move script: NOT APPLIED (0 rows).

## Existing Failures

`VatDeclarationLifecycleTest › create — required fields › sync accepts a period whose adjustments exceed its revenue` —
fails on clean `origin/sit` too (recorded in the project CLAUDE.md before this change).

## New Failures

None.

## Not Tested

The move script (not applied); SIT.

## Final Result

PASS on LOCAL (unit + end to end). The lamine move is still to be applied.

## Recommendation

READY FOR DBA (V4, then the move script), then a `bootRun` check of sign-up and login.
