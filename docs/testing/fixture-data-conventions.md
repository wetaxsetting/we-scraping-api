# Fixture-data conventions — the passing test that cannot fail

**Status:** convention, applies to every test in `wecms-api`. Written 2026-08-01 after the third instance.

## The pattern

> **A test whose assertion is correct, but whose *data* cannot exercise the condition the test claims to
> cover.** It passes. It goes on passing after the defect is introduced. It is worse than no test,
> because the coverage report, the reviewer and the next engineer all read it as evidence.

This is not "a test with a bug" — a test with a bug fails, and someone fixes it. This one is green from
the day it is written until the day someone reads the fixture and notices the values could never have
distinguished the two branches.

**Three instances in this project so far.** All three were found by hand, none by the suite:

| # | Where | The fixture | Why it could not fail |
|---|---|---|---|
| 1 | [`BulkExecutionResultServiceTest2:82-85`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkExecutionResultServiceTest2.java#L82-L85) | One shared `FILE_NM` constant used for **both** the name we send (`S_…`) and the name the bank returns in the `01302` (`R_…`) | The whole defect (**D-1 (boundary-sweep)**, the `S_`/`R_` prefix asymmetry) *is* the two names differing. With one constant on both sides they always matched, so the exact-name lookup passed. The prefix mismatch "could never fail a test" — the test's own words, once corrected |
| 2 | [`StuckFirmFileMonitorTest:115-117`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckFirmFileMonitorTest.java#L115-L117) · [`StuckFirmFileClassifierDateRolloverTest:167`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckFirmFileClassifierDateRolloverTest.java#L167) | `when(...findStranded(anyString(), ...))` — stubbed **step-agnostically** | Since 2026-07-31 the sweep makes **two** calls, one per stranded step (`'1'` and `'2'`). A step-agnostic stub hands the *same* list to both, so every file is counted twice — and the test asserting the count passes against the doubled number. Fixed by stubbing `eq(ProcStep.REQ_RECV.code())` and `eq(ProcStep.DEPO_REQ.code())` separately |
| 3 | [`PayFileFinalizeGuardTest`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/callback/service/PayFileFinalizeGuardTest.java) fixture | `tranDt = "20260529"` — a real PROD file, but **not an instance of the defect under test** (its parent correctly reads `PROC_STAT='6'`; the `tot == 0` instances are `20251128` / `20251201`, whose parents were finalised to `'0'`) | The assertions hold for any date, so nothing failed. But the fixture read as *"this is the production case"* and it wasn't — it pointed at the other PROD pair surfaced on the same day. It then propagated: the same wrong pair was cited in the test body, in a sibling monitor test, and in a decision report. Corrected 2026-08-01 |

Instance 3 shows the second-order cost: **wrong fixture data is quoted as evidence.** A date in a test
looks authoritative precisely because someone bothered to use a real one.

## The rule

**Before writing the assertion, ask what fixture value would make this test fail. If you cannot name one,
the test does not cover what its name says.**

Concretely:

1. **Two things that must differ must differ in the fixture.** Never reuse one constant for both sides of
   a comparison the code is supposed to distinguish (instance 1). If the production values are `S_…` and
   `R_…`, the fixture uses `S_…` and `R_…`.
2. **Stub on the argument that selects the branch.** `anyString()` is correct only for arguments the
   behaviour genuinely ignores. If the production code calls the same method twice with different
   arguments, `any…()` collapses them and the test silently exercises one path twice (instance 2).
3. **A fixture that names real production data must name the *right* production data** — and say which
   incident it is, so a later reconciliation cannot "correct" it back (instance 3). If the value is
   arbitrary, make it obviously arbitrary rather than plausibly real.
4. **Write the negative case.** The cheapest guard against all three is one test asserting the condition
   is *not* met when the input differs. Instance 1 needed exactly one test with mismatched prefixes.
5. **A green suite is not evidence a predicate ran.** This project has **no embedded DB and no
   Testcontainers**, so **no test executes JPQL**. Spring Data validates queries at context startup —
   booting proves a query *parses*, never that it returns the right rows. Repository predicates
   (`substring(..., 3)`, the prefix-stripped finders) are first exercised for real by the first SIT
   callback. See the runbook's §5.1a-1 note.

## In review

Ask of any new test: **"what did you change to watch it fail?"** If the answer is "nothing, it passed
first time" — that is the signal, not the reassurance. A test that has never been seen red has never been
shown to be connected to the code.

## Related

- [`missing-tests.md`](missing-tests.md) — gaps where no test exists at all (the honest version of the
  same problem)
- [`integration-test-audit.md`](integration-test-audit.md) — what the absence of an embedded DB costs
- [`../deployment/wecms-batch-activation-runbook.md`](../deployment/wecms-batch-activation-runbook.md)
  §5.1a-1 — the unexercised-JPQL warning, and the SIT queries that stand in for it
- [`../audits/legacy-batch-boundary-rule-sweep-2026-07-31.md`](../audits/batch/legacy-batch-boundary-rule-sweep-2026-07-31.md)
  — D-1 (boundary-sweep), the defect instance 1 hid
