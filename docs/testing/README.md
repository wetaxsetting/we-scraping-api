# testing/

Refactor-side test strategy and per-feature test plans.

## Purpose

Test plans for the Spring Boot + Next.js refactor: unit, integration, E2E, AI-assisted manual scenarios.

## Subfolders

| Folder | Holds |
|---|---|
| [integration/](integration/) | Spring Boot integration / repository-layer test plans |
| [sit/](sit/) | System integration test plans (SIT environment) |
| [regression/](regression/) | Regression suites guarding shipped features |
| [performance/](performance/) | Load / performance test plans |
| [uat/](uat/) | User-acceptance test scripts (AI-assisted manual) |

Per-feature test plans (e.g. [vas-account.md](vas-account.md)) sit at this root and cross-link to the feature spec under [../features/](../features/).

## Conventions — read before writing tests

| Doc | Covers |
|---|---|
| [fixture-data-conventions.md](fixture-data-conventions.md) | **The passing test that cannot fail** — a correct assertion over fixture data that cannot exercise the condition. Three defects in this project have been hidden this way. Includes the review question to ask of every new test |

## What goes here

- Per-feature test plans (manual + automated scenarios)
- Test strategy notes (JUnit 5 / Spring Boot Test / Playwright if introduced)
- AI-assisted test scenarios (Mr. Youvita's manual test scripts)
- Test data setup notes (single tenant `BIZ_KEY = VN_VNM0004_000001`)

## What does NOT go here

- Cross-cutting testing strategy that applies to **both** systems → [../../../docs/testing/](../../../docs/testing/)
- Per-feature design specs → [../features/](../features/)
- Bug reports → [../../bug-reports/](../../../docs/bug-reports/) (tag `System: Refactor`)

## Example file names

- `account-inquiry-vnd-tests.md`
- `login-auth-tests.md`
- `view-setting-tests.md`
- `e2e-strategy.md`
- `manual-test-scenarios-sprint-1.md`

## Reference

JEX is **read-only reference**. Read [../../jex/screen-flows/](../../../docs/jex/screen-flows/) and the live JEX system to understand expected behavior, then write test scenarios for the refactor equivalent.
