# testing/

Refactor-side test strategy and per-feature test plans.

## Purpose

Test plans for the Spring Boot + Next.js refactor: unit, integration, E2E, AI-assisted manual scenarios.

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
