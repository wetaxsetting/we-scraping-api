# WeCMS Refactor — Test Coverage Audit

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Scope:** whole suite, with the firm-banking
dispatch subsystem audited in depth.

**Companion:** [deployment-readiness.md](deployment-readiness.md) — the defects referenced as **D-1/D-2/D-3**
are defined there.

> ## UPDATE 2026-07-29 (later same day) — D-1/D-2/D-3 fixed; two coverage gaps closed
>
> Legacy-first fix pass; decision record in
> **[d1-d2-d3-legacy-verification.md](d1-d2-d3-legacy-verification.md)**.
>
> **Execution after the fixes** — real working tree, `clean` + `cleanTest`, no cached results:
> **269 suites · 2171 tests · 2151 passed · 0 failed · 0 errors · 20 skipped** (`BUILD SUCCESSFUL in 4m 1s`).
> Of the +111 tests vs the 2060 baseline, **43 are from this fix pass** (below); the remainder belong to
> another author's concurrent `dispatch/monitor/` work.
>
> | Suite | Tests | Covers |
> |---|---|---|
> | `ResultCheckSelectionBoundaryTest` (new) | 24 | **closes G-2** — every ICT/UTC date boundary, month/year/leap rollovers, the 1440-minute no-stranding sweep, tiered aging, defensive input |
> | `ActuatorEndpointSecurityTest` (new) | 18 | D-3 — exposure allow-list, `show-details: never`, `info` contributors off, health-public/info-authenticated split, base-path not relocated |
> | `SchedulerConfigurationContractTest` (+1) | 24 | `partnerIdPropertyIsRetired` across all four profiles |
>
> **A test found a bug in the fix.** The first cut of `isAged` checked the date shortcut before
> `RQ_DTTM`; `rowInsideTheDelayWindowIsNeverSelected` failed at 07:00 ICT because a row dispatched one
> minute earlier already carries the previous UTC `TRAN_DT`. Tier order corrected so `RQ_DTTM` wins. This
> is the boundary suite paying for itself immediately, and it is the concrete counter-example to the
> baseline's "green suite ≠ coverage" point.
>
> **G-8 resolved differently than recommended.** The finding was "tests assert the partner-id fallback as
> correct". Legacy proved the fallback *is* correct — envelope `ID` is always `TB_FIRM_CONF.VAN_ORG_CD` on
> all seven legs, and no partner-id concept exists in legacy. So the assertions were **kept** (they are
> legacy-parity tests) and the *property* was removed instead, with a new profile-level test preventing its
> return. No test was left asserting a defect.
>
> **Coverage moved up on every axis** (measured on the real tree after the concurrent work compiled):
>
> | Scope | Line | Branch | Baseline |
> |---|---|---|---|
> | Whole application | **56.0 %** | **41.3 %** | 55.7 % / 41.0 % |
> | `**/payment/dispatch/**` | **79.9 %** | **60.6 %** | 78.2 % / 58.5 % |
> | `ResultCheckService` (D-1) | **93.8 %** | **75.6 %** | 93.3 % / 71.6 % |
>
> The verdict is unchanged at **PARTIAL** — these are improvements at the margin, not a step change, and the
> five HIGH gaps that were not in scope for this pass are still open.
>
> Still open from the list below: **G-1, G-3, G-4, G-5, G-6, G-7, G-9, G-10, G-11.**

---

## 0. How this was measured

**JaCoCo is NOT configured in the repository.** `build.gradle` has no `jacoco` plugin and no coverage task;
`./gradlew jacocoTestReport` fails with "task not found" on a clean checkout. Reported as required.

To produce **real** numbers rather than declaring coverage unmeasurable, JaCoCo was injected through a Gradle
**init script held outside the repository**, so no project file was modified:

```bash
# init script: <scratchpad>/jacoco-init.gradle  (outside the repo; applies the jacoco plugin only)
./gradlew --init-script <scratchpad>/jacoco-init.gradle \
          cleanTest test jacocoTestReport --no-build-cache --console=plain
```

`cleanTest` + `--no-build-cache` guarantee no cached or `UP-TO-DATE` test result was accepted — the log shows
`> Task :test` executing (only `compileJava`/`compileTestJava` were up-to-date, which does not affect test
execution). **`BUILD SUCCESSFUL in 2m 10s`.**

> **If coverage is to be tracked going forward, the `jacoco` plugin must be added to `build.gradle`** — this
> measurement is reproducible only with the external init script and is therefore not a durable CI gate.

Sources: `build/test-results/test/*.xml` (261 files) and `build/reports/jacoco/test/jacocoTestReport.{csv,xml}`.

---

## 1. Execution results — PASS

Aggregated from the JUnit XML (not from console text):

| Metric | Value |
|---|---|
| Test suites (classes) | **260** |
| Tests | **2060** |
| Passed | **2040** |
| Failed | **0** |
| Errors | **0** |
| Skipped | **20** |
| Wall clock | 2 m 10 s |

**Test execution: PASS.** No failure, no error, no flake observed on a clean run.

**All 20 skipped tests are one class:** `VasAccountIntegrationTest`, `@Disabled` at class level
([VasAccountIntegrationTest.java:87-88](../../src/test/java/com/kosign/wecms/domain/setting/vasaccount/VasAccountIntegrationTest.java#L87-L88)) —
*"class-level disable so JUnit skips without booting the Spring context. Re-enable once the Testcontainers +
JWT-mock shared infra lands."* Those 20 tests are the project's only full-stack CRUD + tenant-isolation +
Excel-import/rollback + audit-row suite. It is written and inert (see **G-7**).

---

## 2. Measured coverage — PARTIAL overall, better on dispatch

**Whole application** (JaCoCo report-level counters):

| Counter | Covered / Total | % |
|---|---|---|
| Instruction | 92 200 / 168 284 | **54.8 %** |
| **Line** | 17 950 / 32 252 | **55.7 %** |
| **Branch** | 6 260 / 15 275 | **41.0 %** |
| **Method** | 2 668 / 5 046 | **52.9 %** |
| **Class** | 692 / 1 094 | **63.3 %** |
| Complexity | 4 580 / 12 773 | 35.9 % |

**402 production classes have zero line coverage.**

**Dispatch subtree** (`**/payment/dispatch/**`, 102 classes) — materially better than the app average:

| Counter | Covered / Total | % |
|---|---|---|
| Line | 2 131 / 2 724 | **78.2 %** |
| Branch | 668 / 1 142 | **58.5 %** |
| Method | 294 / 417 | **70.5 %** |

Verdict: **Test coverage: PARTIAL.** The money path is genuinely well covered; branch coverage (41 % app-wide,
58.5 % on dispatch) is the weakest dimension, and specific high-risk classes are at zero.

---

## 3. Coverage of the required areas

Measured per class from `jacocoTestReport.csv`. `—` = no branches in class.

| Required area | Class | Line | Branch | Method | Assessment |
|---|---|---|---|---|---|
| Realtime dispatch | `RealtimeDispatchService` | **99.5 %** | 77.4 % | 100 % | Strong |
| | `RealtimeTransferService` | 81.3 % | 75.0 % | 90.9 % | Good |
| Bulk / file dispatch | `BulkReceptionService` | 91.1 % | 67.3 % | 87.5 % | Good |
| | `BulkTransferValidator` | 92.9 % | 70.6 % | 100 % | Good |
| EFS transport (201–302, MSG) | `RestClientEfsApiClient` | 92.3 % | 57.1 % | 100 % | Good; branch gaps on error envelopes |
| SFTP upload/download/move | `JschSftpClient` | **10.7 %** | **8.8 %** | 44.4 % | **G-3 — effectively untested** |
| Fixed-length generate/parse | `FixedLengthCodec` | 93.9 % | 79.2 % | 100 % | Strong, byte-level (§5) |
| | `VietnameseText` | 100 % | 100 % | 100 % | Complete |
| Encryption / HMAC / signature | `EfsCryptoService` | 76.0 % | 50.0 % | 100 % | **G-9 — half the branches unexercised** |
| Recipient inquiry | `RecipientLookupService` | 84.3 % | 62.5 % | 100 % | Good, but see **G-8** |
| | `DepositorInquiryService` | 100 % | 60.0 % | 100 % | Good |
| Result checking | `ResultCheckService` | 93.3 % | 71.6 % | 100 % | Good — but **G-2** gap |
| | `DispatchResultTxService` | 100 % | 100 % | 100 % | Complete |
| | `DispatchResultResolver` | **0.0 %** | **0.0 %** | **0.0 %** | **G-1 — 110 lines, mocked everywhere** |
| Scheduler jobs | `ResultCheckJob` / `StrandedRecoveryJob` / `StuckPayFileMonitorJob` | **0.0 %** each | — | 0 % | Bodies are 4-line delegators; **contracts are tested** (§4) — acceptable |
| Operating-hours rules | `OperatingHoursService` | **98.3 %** | **93.2 %** | 87.5 % | Strong |
| ANT_DIV_CD routing | `AntDivChannel` | **100 %** | **100 %** | **100 %** | Complete |
| Callbacks (01301 / 01302) | `BulkReceptionResultService` | 93.0 % | 65.9 % | 100 % | Good |
| | `BulkExecutionResultService` | 88.9 % | 54.1 % | 100 % | Adequate; branch-thin |
| | `CallbackIpWhitelistFilter` | **0.0 %** | **0.0 %** | **0.0 %** | **G-4 — security filter untested** |
| Retry / recovery | `StrandedRecoveryService` | 90.9 % | 100 % | 100 % | Strong |
| | `StuckPayFileMonitorService` | 94.9 % | 86.2 % | 100 % | Strong |
| | *(transport retry)* | — | — | — | **No retry exists to test** (deployment-readiness §4 item 7) |
| Pacing | `DispatchPacer` | **6.2 %** | **0.0 %** | 25.0 % | **G-6 — mocked, rule never executed** |
| Alerting | `OpsAlertService` | 89.8 % | 66.7 % | 100 % | Good |
| Config activation / fail-safe | `EfsProperties` / `SftpProperties` | 100 % | — | 100 % | Bindings covered |
| | `MockEfsApiClient` / `MockSftpClient` | **0.0 %** | — | **0.0 %** | **G-5 — fail-safe unguarded** |
| | `VanApiKeyService` | 25.0 % | — | 33.3 % | **G-10 — fail-closed throw untested** |

**EFS leg reference counts in tests** (all seven legs are exercised):
`EFS_201` 9 · `EFS_202` 16 · `EFS_203` 11 · `EFS_204` 12 · `EFS_301` 5 · `EFS_302` 14 · `EFS_MSG` 3.
`EFS_301` at 5 is the thinnest — that is the bulk-file reception leg.

---

## 4. What is tested well — recorded so the gaps below aren't misread

Genuine strengths, verified by reading the tests, not by counting them:

- **Byte-level legacy parity.** `FixedLengthCodecTest` (15 tests) asserts exact **1000-byte** records,
  `0x0D 0x0A` at each record tail, H/D/T record ordering and offset-level field placement, with layout
  values *"confirmed byte-for-byte against the six UAT sample files"* and an
  `encode(decode(bytes)) == bytes` round-trip. This is real output-parity testing, not shape assertion.
- **Cron parity by evaluation, not by string compare.** `SchedulerCronAndLockIdentityTest` *evaluates* the
  cron expressions and asserts the fire pattern against the legacy Jex Studio schedules — e.g. 78 fires
  (13 h × 6/h, 10-minute gaps, 07:00→19:00) for `ERP_0100_001`, and an explicit
  `haengsungCronDefaults_areNotThe24HourGuess()` regression guard against the corrected 2×-rate bug.
- **Deployment-safety contract as a test.** `SchedulerConfigurationContractTest` parses the **real YAML
  profiles** and asserts every enable flag defaults `false`, every cron defaults to never-fire, the
  stuck-monitor is declared and disabled, all unconditionally-bound dispatch properties resolve (startup
  safety), and the **retired** `seed-cron`/`refresh-cron` keys are absent. This is the right shape for a
  config regression gate.
- **Lock identity and idempotency.** `JobLockGuardTest`, `StrandedRecoveryResultCheckLockTest`,
  `ExchangeRateOnDemandLockTest` cover the shared-lock-identity fix and the manual-path bypass.
- **Dispatch service bodies.** 30 dispatch test classes; realtime, bulk, callbacks, operating hours,
  result-check outcome branches (success/failure/transport-failure/`rc=0001`/bad-`VV`), and both recovery
  services are all covered at 80–100 % line.

---

## 5. Coverage gaps

Severity reflects blast radius on a money path, not line count.

### G-1 · `DispatchResultResolver` — 110 lines, 0 % executed, mocked in 12 test classes — **HIGH**

The class that maps `TB_FIRM_TRAN` result codes to per-line dispatch success/failure — consumed by **15+
production call sites**: all five payment Result screens, all five download/export services,
`MatchService`/`AutoMatchService` bank matching, and `HaengsungDispatchResultWriteBackService`.

It is `@Mock`ed or `mock(...)`ed in **every** test that touches it:
`AutoMatchServiceTest:70` · `MatchServiceListCandidatesTest:68` · `MatchServiceOverseasTest:62` ·
`ExpensePaymentResultFooterTotalsTest:50` · `ExpensePaymentResultParityTest:57` ·
`HaengsungDispatchResultWriteBackServiceTest:52` · `InternalTransferDownloadServiceTest:64` ·
`InternalTransferResultServiceTest:65` · `PayrollPaymentResultFooterTotalsTest:50` ·
`PayrollPaymentResultParityTest:57` · `PayrollPaymentResultReportingAccountTest:67` ·
`PayrollPaymentResultStatusCountTest:69`.

**This is the archetype of the "mocked without validating real mapping" failure mode.** Twelve suites assert
what the *callers* do with a stubbed verdict; **zero** assert that the resolver produces the right verdict.
Its two internal `coalesceProcStat` mappers (`:162`, `:224`) and `resolveOverseas` have never run in a test.
A wrong mapping here silently mislabels real payments as succeeded/failed on every result screen and in every
export.

**Recommended:** a direct unit test over `DispatchResultResolver` — table-driven across the `PROC_STAT`
(`0`/`3`/`6`/`9`) × `RS_CD` matrix, the `coalesceProcStat` precedence rules, and `resolveOverseas`'s
always-SWIFT/file-route branch, asserted against the legacy result-code semantics.

### G-2 · No test for the UTC/ICT day boundary (defect D-1) — ~~HIGH~~ **CLOSED 2026-07-29**

`ResultCheckPollTest` has 12 well-chosen scenarios (aged success/failure, fresh-row skip, `ORG_STAT` skip,
transport failure, `rc=0001`, bad `VV`, pacing on/off) — but **every one constructs its fixture rows with a
`TRAN_DT` that already matches the ICT "today"** the service computes. No test builds a row the way the
production writers do (UTC date from `ExpenseDispatchExecutor:151`), so the filter mismatch at
`ResultCheckService:207` is invisible to the suite. This is why a HIGH money-path defect passed 2060 green
tests.

**Recommended:** inject a fixed `Clock` (the executors already take one) and assert that a 202 row stamped
with the **UTC** date at 03:00 ICT still enters the aged set. That test must fail before D-1 is fixed.

### G-3 · `JschSftpClient` — 10.7 % line / 8.8 % branch; one test, the guard only — **HIGH**

`JschSftpClientTest` contains exactly **one** test —
`upload_withUnconfiguredHost_throwsTransferException()` — which only proves the new fail-loud config guard.
The bodies of `upload`, `download` and `move`, the key-vs-password auth selection, channel lifecycle, and the
`disconnect` paths are never executed. The real bulk payment-file transport is untested.

**Recommended:** an embedded SSHD/SFTP fake (Apache MINA sshd) covering upload → download → move round-trip,
key-auth vs password-auth selection, and failure/cleanup paths. Also asserts the host-key policy explicitly,
so a future change away from `StrictHostKeyChecking=no` is a deliberate test change.

### G-4 · `CallbackIpWhitelistFilter` — 0 % — **HIGH (security)**

The filter that is the *only* network-level gate on the unauthenticated callback endpoints has no test at
all. Untested behaviours include the **fail-open** branch (`allowedIps.isEmpty()` → pass everything,
`:38`), the `X-Forwarded-For[0]` trust path (`:55-58`), and the 403 rejection. The fail-open default is a
deployment-readiness blocker (H-1 there); nothing prevents a refactor from silently changing it.

**Recommended:** `MockMvc`/filter unit tests for: empty allow-list → pass (documenting fail-open as
*intentional*, or failing if it is not); IP in list → pass; IP not in list → 403; spoofed `X-Forwarded-For`
with `trust-forwarded-for` true vs false.

### G-5 · Neither mock client is referenced by any test — 0 % — **MEDIUM**

`grep -rn "MockEfsApiClient\|MockSftpClient" src/test/java` → **no match**. The single most important
fail-safe in the subsystem — mocks that **throw** instead of faking success — has no regression guard. If
someone "helpfully" made `MockEfsApiClient.send` return an empty success envelope, every test would still
pass and a disabled environment would start silently reporting fake dispatch successes.

**Recommended:** two tiny tests asserting `UnsupportedOperationException` from each mock, plus an
`ApplicationContextRunner` test asserting bean selection flips with `wecms.dispatch.{efs,sftp}.enabled` —
which would also cover activation (required area 12) at the context level rather than by inspection.

### G-6 · `DispatchPacer` — 6.2 % line / 0 % branch, mocked — **MEDIUM**

`@Mock DispatchPacer` in `ResultCheckPollTest:72`. The tests assert *that* pacing is invoked
(`perBankPacing_pacesOnOriginalFailure`, `…notInvokedOnCleanSuccess` — good) but never that the **rates** are
right. The legacy-parity rule (Woori `970457` = 3000 ms, else 100 ms) is never executed, so a wrong constant
would not be caught — and this rate is the load-bearing input to `ResultCheckJob`'s `PT30M` lock derivation.

**Recommended:** a direct unit test on `DispatchPacer.pause(outBankCd)` asserting the per-bank delay values.

### G-7 · No integration or end-to-end test for dispatch; the only integration suite is disabled — **HIGH**

- **0** Testcontainers usages in the tree.
- 14 classes use `@SpringBootTest`/`@DataJpaTest`/`@WebMvcTest` — **none in the dispatch package**. All 30
  dispatch test classes are Mockito unit tests.
- The two callback controllers have 1 covered line each (`BulkCallbackRoutingTest` covers routing only); no
  test posts a real callback envelope through the HTTP layer.
- The one substantial integration suite, `VasAccountIntegrationTest`, is `@Disabled` — its 20 tests are the
  full CRUD + tenant-isolation + Excel-rollback + audit-row coverage, and they never run.

So nothing verifies: enqueue → after-commit hand-off → operating-hours gate → 202 → callback → result-check →
`PROC_STAT` transition across a real transaction boundary and a real schema. Transaction propagation
(`REQUIRES_NEW` audit rows surviving rollback, the atomic `'9'→'6'` claim) is asserted only against mocks.

**Recommended:** land the shared Testcontainers + JWT-mock infra the disabled class is waiting on, re-enable
it, then add one dispatch end-to-end slice against a real PostgreSQL with a stub EFS server.

### G-8 · The partner-id tests lock in the defect (D-2) — **MEDIUM**

`RecipientLookupServiceTest:40` constructs `new EfsProperties()` with the comment
`// partnerId blank → ID = vanOrgCd`, and `:80` asserts
`assertThat(sent.id()).isEqualTo("ORG000001")  // partnerId blank → vanOrgCd`.

`grep -rn "setPartnerId" src/test/java` → **no match**: **no test ever sets a non-blank partner id**, so the
provisioned branch of the ternary at all seven envelope sites is never exercised, and six test classes
actively assert the substitution *as correct*. The suite encodes current behaviour rather than the
requirement, which is exactly why D-2 was never surfaced by testing.

**Recommended:** add the provisioned-partner-id branch (`ID == partnerId`), and — once the PO confirms
whether a blank partner id should be rejected — a test asserting the fail-loud behaviour.

### G-9 · `EfsCryptoService` branch 50 % — **MEDIUM**

6 tests cover the essentials well (EV round-trip, `VV` match, `VV` tamper-detection, EV determinism, bad
`rqDtime` length, malformed-EV never throws). Half the branches remain unexercised — padding boundaries,
key-length edge cases, and non-ASCII/Vietnamese payloads through the cipher. On a money path where `VV` is
currently the **only** callback gate, 50 % branch coverage is thin.

**Recommended:** boundary cases at PKCS5 block edges, Vietnamese-diacritic payloads, wrong-key `VV`
rejection, and constant-time-compare behaviour on equal-length mismatches.

### G-10 · Notable zero-coverage production classes outside dispatch — **MEDIUM**

Top of the 402, by missed lines:

| Missed lines | Class | Why it matters |
|---|---|---|
| 217 | `banking.export.CashFlowExcelWriter` | Report output correctness; entirely untested |
| 184 | `einvoice.service.EInvoiceDetailExportService` | Export output |
| 157 | `setting.preferences.service.PreferencesService` | Settings write path |
| **142** | **`common.legacy.LegacyFunctionRepository`** | **The mandated single executor for *all* key generation (28 `wb_get_next_val` branches). Every PK in the system is allocated here and none of it is covered.** |
| 123 | `payment.repository.OverseasPaymentRepository` | Payment queries |
| 116 | `setting.users.service.UserImageService` | Upload path |

`LegacyFunctionRepository` is the standout: the funnel rule makes it the only place keys are minted, so a
regression there corrupts key formats across every module.

### G-11 · Scheduler-starvation risk has no test — **LOW (by nature)**

`pool.size: 1` is load-bearing by design, and `ResultCheckJob`'s Javadoc derives 4–13 min pass durations for
200 aged rows — during which every other scheduled job is blocked. No test exercises concurrent scheduled
load, and a unit test is arguably the wrong instrument. Recorded as a **monitoring** requirement (job-lag
alerting on `shedlock.locked_at` freshness) rather than a test gap, so it is not silently dropped.

---

## 6. Coverage-gap summary

| ID | Gap | Severity |
|---|---|---|
| G-1 | `DispatchResultResolver` 0 %, mocked in 12 suites | **HIGH** |
| ~~G-2~~ | ~~No test for the UTC/ICT day boundary (D-1)~~ **CLOSED** — `ResultCheckSelectionBoundaryTest` (24 tests) | ~~HIGH~~ |
| G-3 | `JschSftpClient` upload/download/move untested | **HIGH** |
| G-4 | `CallbackIpWhitelistFilter` 0 % (security) | **HIGH** |
| G-7 | No dispatch integration/E2E; only integration suite `@Disabled`; 0 Testcontainers | **HIGH** |
| G-5 | Mock clients' fail-safe throw unguarded | MEDIUM |
| G-6 | `DispatchPacer` rates never executed | MEDIUM |
| ~~G-8~~ | ~~Partner-id tests assert the defect as correct~~ **RESOLVED** — legacy proved the fallback correct; property removed, assertions kept as parity tests | ~~MEDIUM~~ |
| G-9 | `EfsCryptoService` 50 % branch | MEDIUM |
| G-10 | `LegacyFunctionRepository` (all key generation) + 5 large classes at 0 % | MEDIUM |
| G-11 | No scheduler-load test (monitoring item, not a unit-test gap) | LOW |

---

## 7. Verdicts

- **Test execution: PASS** — 2060 tests, 2040 passed, 0 failed, 0 errors, 20 skipped, clean run.
- **Test coverage: PARTIAL** — 55.7 % line / 41.0 % branch app-wide; 78.2 % line / 58.5 % branch on dispatch;
  402 classes at zero; five HIGH gaps, four of them on the money path.

**A green suite is not evidence of coverage here, and this audit has the specific proof:** defect **D-1** (a
HIGH money-path correctness bug) and **D-2** (a silent envelope-ID substitution) both survive 2060 passing
tests — D-1 because no fixture reproduces how production stamps `TRAN_DT`, D-2 because six suites assert the
faulty fallback as intended behaviour.
