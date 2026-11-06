# WeInvoice API (P1) on `sit` — Test Result

**Date:** 2026-11-05
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Related Record:** [../../features/addon-service/weinvoice/weinvoice-api-implementation-2026-11-02.md](../../features/addon-service/weinvoice/weinvoice-api-implementation-2026-11-02.md)
**Issue:** WeInvoice P1 — UI Spec v1.0

## Scope

Re-verification of the WeInvoice P1 change after moving it from a stale local `main` (`afc95b0`) onto
`origin/sit` (`6aa0c1f`), plus the two fixes this required (record §"Moved onto `sit`"): keys-only
`WECMS_WEINVOICE_*` lines in the five `.env.*.example` templates, and `Boolean` flags in
`WeInvoiceProperties` so blank values bind. The first run is recorded in
[weinvoice-api-test-result-2026-11-02.md](weinvoice-api-test-result-2026-11-02.md); this file does not
replace it.

## Environment

LOCAL — Windows 11, Gradle 8.14.4 wrapper, JDK 21 (Temurin, Gradle-provisioned), `--offline`.
No database, no running server.

## Code / Build Under Test

Branch: `f1_v1_weinvoice_api` (local, created from `origin/sit` `6aa0c1f`) · Commit: NOT COMMITTED
(working tree) · Build: `./gradlew test` · API URL: none (not started).

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*' --tests '*SchedulerConfigurationContractTest' --tests '*EndpointAuthorizationCoverageTest'` (before the `Boolean` fix) | FAIL — 6 pre-existing only | 113 tests, 6 failures, all `[application-local.yml must exist]`; the keys-only and `.env.example` ↔ `application.yml` parity tests PASS with the blanked block |
| `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*'` with the new test, before the `Boolean` fix | FAIL — 1 new, caused by the change | `blankEnvValuesBindToDisabledModule`: `Failed to bind properties under 'wecms.weinvoice.enabled' to boolean` — fixed |
| `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*' --tests '*EndpointAuthorizationCoverageTest'` | PASS | 85 WeInvoice tests (84 + 1 new) + 2 coverage tests, 0 failures |
| `./gradlew test` (full suite) | FAIL — 13 pre-existing only | 346 classes, 2 878 tests, 13 failures, 21 skipped; listed under "Existing Failures" |

## Regression Tests

| Test | Protects | Result |
|---|---|---|
| `WeInvoicePropertiesTest.blankEnvValuesBindToDisabledModule` (new) | a host `.env` copied from the keys-only template (every `WECMS_WEINVOICE_*` blank) binds through Spring's `Binder` to a disabled module: `enabled=false`, `testDataEnabled=false`, quota 5, no partner key, no pool, `WBE3000` | PASS (failed before the fix — reproduces the startup failure) |
| `SchedulerConfigurationContractTest` — keys-only templates, example ↔ yaml key parity for sit / uat / prod | the blanked block satisfies `sit`'s template rules | PASS (the `profile=local` cases cannot run, see below) |

Scenario added: T6 in [test-scenarios.md](../../features/addon-service/weinvoice/test-scenarios.md).

## Existing Failures

13, none caused by this change:

- `ActuatorEndpointSecurityTest` × 5, `SchedulerConfigurationContractTest` × 6,
  `OperatingHoursZoneConsistencyTest` × 1 — read `application-local.yml`, which is git-ignored and absent
  on this machine (same cause as the 2026-11-02 run; `SchedulerConfigurationContractTest` is 6 here
  instead of 7 because `sit` reshaped that test).
- `VatDeclarationLifecycleTest$Create.negativeRevenueSyncs` × 1 (`Expecting actual: 0 to be greater
  than: 0`, line 644) — VAT declaration module, untouched by this change. Reproduced on an unchanged
  export of `origin/sit` (`git archive origin/sit`, `--tests '*VatDeclarationLifecycleTest*'`: 33 tests,
  the same 1 failure).

## New Failures

None after the `Boolean` fix.

## Not Tested

Unchanged from the 2026-11-02 run: no query against PostgreSQL (schema not applied), no running
application, no engine worker, no WeCMS-side Partner API intake. Application startup with the real
`application-sit.yml` and a SIT host `.env` was not run.

## Final Result

PARTIAL — the change compiles and passes on top of `sit` with no new failure; database, running-server
and engine verification are still outstanding.

## Recommendation

READY FOR MANUAL COMMIT on `f1_v1_weinvoice_api` — the code can be committed and pushed. Before **deploying**
to SIT, add the 14 `WECMS_WEINVOICE_*` keys to the SIT host `.env` (blank is fine, so the module stays off). Turning
WeInvoice on stays READY FOR DBA (three scripts) and needs the engine worker.
