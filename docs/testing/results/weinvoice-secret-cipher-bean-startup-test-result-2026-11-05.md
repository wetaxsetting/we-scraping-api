# WeInvoice Secret Cipher Bean Startup Fix — Test Result

**Date:** 2026-11-05
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Related Record:** [../../bugs/weinvoice-secret-cipher-bean-startup-fix-2026-11-05.md](../../bugs/weinvoice-secret-cipher-bean-startup-fix-2026-11-05.md)
**Issue:** WeInvoice P1 follow-up

## Scope

`@Autowired` on `WeInvoiceSecretCipher(WeInvoiceProperties)`, and the new
`WeInvoiceBeanConstructionTest`.

## Environment

LOCAL — Windows 11, JDK 21.0.11 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`), Gradle 8.14.4
`--offline`. `bootRun` against the shared dev PostgreSQL configured in the developer's `.env.local`
(read at startup only: `ddl-auto=validate`, every scheduler and dispatch leg disabled).

## Code / Build Under Test

Branch: local `sit` at `8823f7f` + working tree · Commit: NOT COMMITTED · Build: `./gradlew test`, `./gradlew bootRun`.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --tests '*WeInvoiceBeanConstructionTest'` **without** the fix (fix stashed) | FAIL — as expected | 2 tests, 2 failed (`springInstantiatesSecretCipher`, `everyWeInvoiceBeanHasAConstructorSpringCanChoose`) |
| `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*'` with the fix | PASS | 87 tests (85 + 2 new), 0 failures |

## Regression Tests

Did a regression test cover the original defect? **YES**

- Test file: `src/test/java/com/kosign/wecms/domain/weinvoice/WeInvoiceBeanConstructionTest.java`
- Test cases: `springInstantiatesSecretCipher` (Spring builds the bean from a minimal context, as at
  startup) and `everyWeInvoiceBeanHasAConstructorSpringCanChoose` (every `@Component` in
  `domain.weinvoice` has one constructor, one `@Autowired` constructor, or a no-arg one).
- Original failure reproduced: yes — both fail without the fix. Result with the fix: PASS.

## Manual Verification

| Scenario | Expected | Actual | Result |
|---|---|---|---|
| `./gradlew bootRun`, profile `local`, WeInvoice disabled | context starts | before: `No default constructor found` for `WeInvoiceSecretCipher`; after: `Tomcat started on port 8080`, `Started WecmsApiMainApplication in 32.982 seconds` | PASS |

Startup log after the fix: no ERROR; 3 WARN, all unrelated and expected locally (Hibernate dialect
deprecation, empty callback allow-list under `ENFORCE` with dispatch off, Spring Security generated
password notice).

## Existing Failures

Not re-run in full here; the 13 pre-existing failures are documented in
[weinvoice-api-sit-rebase-test-result-2026-11-05.md](weinvoice-api-sit-rebase-test-result-2026-11-05.md).
Note: this machine now has a local `application-local.yml`, so the 12 `profile=local` failures listed
there may no longer reproduce here.

## New Failures

None.

## Not Tested

Startup on the SIT host (manual deploy). WeInvoice enabled (no WeInvoice schema yet).

## Final Result

PASS

## Recommendation

READY FOR MANUAL COMMIT — and deploy promptly: `8823f7f` on `origin/sit` cannot start without this fix.
