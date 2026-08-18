# testing/

**Status:** active · **Last reviewed:** 2026-08-17

Refactor-side test strategy and per-feature test plans.

## Purpose

Test plans for the Spring Boot + Next.js refactor: unit, integration, E2E, AI-assisted manual scenarios.

## Testing is part of the Definition of Done

**Added 2026-08-18.** Tests are not an optional follow-up MR. Every meaningful BUG / ISSUE / FEATURE /
parity fix passes the **implementation quality gate** before it may be reported complete —
authoritative rule: [`../../CLAUDE.md`](../../CLAUDE.md) §15, summarized in
[`../README.md`](../README.md) §"Implementation quality gate".

- **A bug fix normally carries a regression test** that fails before the fix and passes after it. If
  that is impractical, the dated record explains **why** and supplies manual / test-scenario evidence
  instead — *"no test needed"* with no justification is not acceptable (§15d).
- **A feature verifies the dimensions it actually has** — happy path, validation, empty state, error
  state, permission, boundary values, API failure, duplicate/retry, DB, localization, concurrency —
  not a boilerplate checklist copied between features (§15e).
- **Run the smallest relevant scope first** — `./gradlew test --tests "com.kosign.wecms.domain.<module>.*"` —
  then consider a broader `./gradlew test` regression run before completion, and **report exactly what
  was run** (§15i).
- **Failures are classified, never hidden**: caused by this change (task is NOT COMPLETE) ·
  pre-existing (record the evidence it predates the work) · environment · test data · unresolved
  (§15j).
- **Honest result states only** — `PASS` · `FAIL` · `NOT RUN` · `BLOCKED` · `NOT APPLICABLE`. A test
  plan is never reported as a test result (§15h).
- **Permanent scenarios live with the feature.** When a change alters permanent behaviour, update
  [`../features/`](../features/)`<domain>/<feature>/test-scenarios.md`; the dated bug/issue record
  keeps only the evidence for that change (§15f).
- **Executed evidence gets its own durable record** *(added 2026-08-18)*. Once verification actually
  runs, write a **test-result record** at [results/](results/)`descriptive-name-test-result-YYYY-MM-DD.md`
  and link it from the implementation record's `## Test Evidence` — both directions must resolve. The
  implementation record's `## Verification` section becomes the **summary**; the commands, counts,
  environment, build under test, and pre-existing-vs-new failures live in the result record.
  `Implementation Status: COMPLETE` requires test evidence that supports it (§18).
- **Three documents, three jobs.** `test-scenarios.md` = the **specification** (how it should be
  tested) · a **test-result record** = the **evidence** (what actually ran) · the bug/issue/feature
  record = **why the change happened**. Never let one stand in for another.

> **Work with the test infrastructure that exists.** This repository has **no H2, no embedded
> database, and no Testcontainers** — see [`../../CLAUDE.md`](../../CLAUDE.md) §"Do / Don't" and
> §"Known Issues / Inconsistencies". Mockito service tests are the dominant idiom; where a guarantee is
> genuinely a database property, assert it as a **query-contract test** (the established pattern, e.g.
> `DispatchClaimQueryContractTest`) or verify it manually and record that evidence. Adding an embedded
> DB is a dependency change and a separate, approved task.

Read [fixture-data-conventions.md](fixture-data-conventions.md) before writing the test — a correct
assertion over fixture data that cannot exercise the condition is a *passing test that cannot fail*.

## Subfolders

| Folder | Holds |
|---|---|
| [results/](results/) | **Added 2026-08-18.** Durable **test-result records** — one per actually executed verification of an implemented change, `descriptive-name-test-result-YYYY-MM-DD.md`. Rules: [results/README.md](results/README.md) |
| [sit/](sit/) | System integration test plans (SIT environment) — also carries the **field-name-casing assertion rule** every SIT script must follow |
| [uat/](uat/) | User-acceptance test scripts + cycle reports + sign-off log (AI-assisted manual, run by the PO / test owner) |

> **`results/` does not compete with `sit/` or `uat/`.** Those are organized **per environment cycle**
> (plans, cycle reports, sign-off); `results/` is organized **per implemented change**, whichever
> environment it was verified in. A test-result record may cite a SIT/UAT cycle report as its evidence,
> and vice versa.

Per-feature test plans (e.g. [vas-account.md](vas-account.md)) sit at this root and cross-link to the feature spec under [../features/](../features/).

### Test layers with no subfolder (collapsed 2026-08-17)

`integration/`, `regression/`, and `performance/` were README-only scaffolds with no documents and no
near-term work scheduled; they were collapsed into this index rather than kept as empty folders. Their
scope rules are preserved below — **write these documents at this root**, and only re-create a folder
when the layer actually has several documents to group.

| Layer | Where it goes today | Scope |
|---|---|---|
| **Integration** | This root — see the existing [integration-test-audit.md](integration-test-audit.md) | Spring Boot integration / repository-layer tests + Next.js E2E: which classes/specs exist, what they cover, how they reach the local DB, and the test-data setup / teardown + transactional-rollback policy. Pick and record one E2E tool before writing E2E plans. |
| **Regression** | This root, as `<feature>-regression.md` | The standing suite that must pass before every merge to `main` — drawn from prior bug fixes, parity audits, and UAT scripts that proved load-bearing. Record which bug fix added which regression test, and who owns suite execution. |
| **Performance** | This root, as `<feature>-performance.md` | Load / soak / spike plans: target endpoints, SLOs (latency, throughput, error rate), environment provisioning, and reports. **R&D-local today** (`project_refactoring_scope`) — no DEV/UAT/PROD load is generated. |

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
