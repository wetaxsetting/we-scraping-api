# WeInvoice API (P1) — Test Result

**Date:** 2026-11-02
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Related Record:** [../../features/addon-service/weinvoice/weinvoice-api-implementation-2026-11-02.md](../../features/addon-service/weinvoice/weinvoice-api-implementation-2026-11-02.md)
**Issue:** WeInvoice P1 — UI Spec v1.0

## Scope

The new `domain/weinvoice` module (40 app endpoints, 2 Partner API endpoints), the `SecurityConfig`
change for the Partner API, the configuration / message additions, and the allow-list edit of
`EndpointAuthorizationCoverageTest`. Unit level only — see "Not Tested".

## Environment

LOCAL — Windows 11, Gradle 8.14.4 wrapper, JDK 21.0.11 (Temurin, Gradle-provisioned), `--offline`
after one online dependency download. No database, no running server.

## Code / Build Under Test

Branch: `main` · Base commit: `afc95b0` · Commit: NOT COMMITTED (working tree) ·
Build: `./gradlew compileJava` and `./gradlew test` · API URL: none (not started).

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew compileJava` (with the module) | PASS | exit 0; 136 class files under `build/classes/java/main/com/kosign/wecms/domain/weinvoice` at first compile |
| `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*'` | PASS | 12 classes, 84 tests, 0 failures (second run; first run 5 failures — see below) |
| `./gradlew test` (full suite) | FAIL — 13 pre-existing only | 338 classes, 2 825 tests, 13 failures, 21 skipped; the 13 are listed under "Existing Failures" |
| `EndpointAuthorizationCoverageTest` (in the full suite) | PASS | 2 tests, 0 failures after the allow-list edit; it failed in the first full run (new endpoints not yet declared) |

First WeInvoice run — 5 failures, all addressed before the passing run:
- 4 × `PartnerApiKeyFilterTest`: the test's `ObjectMapper` lacked the JSR-310 module (the envelope
  carries an `Instant`); production uses the Spring mapper, which has it. Test fixture fixed.
- 1 × `InvoiceActionServiceTest.linkWithoutCodeGivesNull`: real defect — `/tra-cuu/` was taken for a
  lookup code. Production code fixed (a code must contain a digit).

## Regression Tests

Tests protecting the behaviours this change introduces (all PASS):

| Test class | Protects |
|---|---|
| `WeInvoiceSecretCipherTest` (7) | password format `v1:` + IV ‖ ciphertext ‖ tag, random IV, tamper and wrong-key rejection |
| `InvoiceCodesTest` (20 incl. parameterized) | status / kind / currency / VAT-rate mapping, the 19 NULL-`tthai` cancelled rows, WeCMS check-result phrases |
| `WeInvoicePropertiesTest` (6) | disabled = no pool + `WBE3000`; injectable schema name, unresolved placeholder and short key rejected without echoing values |
| `PartnerApiKeyFilterTest` (6) | fail closed: missing / wrong / unconfigured key and disabled module → 401 `WBE3019`; other paths untouched |
| `CompanyAccessGuardTest` (5) | malformed MST, foreign / deleted MST, branch MST, no tenant |
| `CompanyServiceTest` (9) | encrypted password stored `PENDING` + login test queued; duplicate, quota, revive; password never printed or returned |
| `InvoiceFileServiceTest` (11) | UI Spec 3.5 confirmation rules for PDF / XML, engine files undeletable, soft delete |
| `XmlKeyReaderTest` (3) | key extraction, XXE refused |
| `InvoiceActionServiceTest` (4) | lookup code from links, pasted link stored and PDF fetch queued |
| `PartnerEInvoiceServiceTest` (4) | WeCMS phrases and codes, size clipping — the detail row passes Bean Validation of the WeCMS record |
| `InvoiceQueryRepositoryTest` (6) | filters bound as parameters, LIKE escaping, sort whitelist, schema substitution |
| `WeInvoiceInvoiceControllerTest` (3) | snake_case binding, envelope, `WBE_VALIDATION`, business error envelope |

## Database Verification

NOT RUN — no WeInvoice schema exists in any database; creating it is a DBA action that was not
authorized in this task (record §"Database Execution Status": all environments NOT STARTED).
Substitute check performed: a temporary test (deleted afterwards) captured all 29 SQL statements the
repositories assemble, which were reviewed by hand for syntax and intent. A defect class the mocks
cannot show was found and fixed by code review: `getObject(col, Long.class)` on `numeric(18)` columns,
which pgjdbc rejects at runtime.

## API Verification

PARTIAL — standalone MockMvc for `WeInvoiceInvoiceController` (binding, envelope, errors) and the
Partner filter in isolation. The other five controllers and a running application were NOT RUN.

## Security / Permission Verification

PASS at unit level — Partner key gate (6 tests), tenant / MST guard (5 tests), password never in
`toString` or response records, XXE refused, path guard via `SecureFileDownload.assertWithinRoot`.
`@PreAuthorize("hasRole('ADMIN')")` on connection writes was not exercised (needs the Spring
security context) — NOT RUN.

## Existing Failures

13 failures, identical on an unchanged export of `HEAD` (`git archive HEAD`, run in the scratchpad with
`--tests '*ActuatorEndpointSecurityTest' --tests '*SchedulerConfigurationContractTest'
--tests '*OperatingHoursZoneConsistencyTest'`: 60 tests, 13 failures):

- `ActuatorEndpointSecurityTest` × 5 (`profile=local`)
- `SchedulerConfigurationContractTest` × 7 (`profile=local`)
- `OperatingHoursZoneConsistencyTest` × 1

All read the profile YAML files; `src/main/resources/application-local.yml` is git-ignored and absent on
this machine. Not caused by this change.

## New Failures

None after the fixes above.

## Not Tested

- Any query against PostgreSQL (schema not applied; approval needed) — row mapping against real
  column types, `ON CONFLICT` paths, the coverage / sync-history unions.
- File download / ZIP / Excel streaming end to end, uploads to disk.
- Application startup with `WECMS_WEINVOICE_ENABLED=true` (Hikari pool, property binding from `.env`).
- The engine side of every request type (worker not built).
- WeCMS ingesting Partner API rows through its real intake (cross-repository).

## Final Result

PARTIAL — code compiles and every unit test of the change passes, no regression in the existing suite;
database, running-server and engine integration verification are outstanding.

## Recommendation

READY FOR DBA (apply the three scripts on a dev WeInvoice database), then DB-level verification, then
READY FOR MANUAL COMMIT.
