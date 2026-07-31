# Architecture Review — Payment Dispatch Module

**Revision 5** · final stable-tree release evidence — *quiet tree, no production changes*

| | |
|---|---|
| **Scope** | `wecms-api/src/main/java/com/kosign/wecms/domain/payment/dispatch/**` plus the five per-module dispatch executors and their call sites |
| **Rev 5 verified** | 2026-07-30 10:09–10:35 ICT · branch `wecms-ui-fixes` · HEAD `98c924fde6cbece4dd2cfbea228c4539247c251c` · 74 modified + 2 deleted + 71 untracked |
| **Rev 4 verified** | 2026-07-29 18:55 · **Rev 3** 17:29 · **Rev 2** 15:38 · **Rev 1** 2026-07-28 |
| **Build** | 🟢 `--no-daemon clean compileJava compileTestJava` **exit 0, 0 errors** (116 s) · `--no-daemon cleanTest test` **exit 0** (67 s) — `build/` deleted first, both compile tasks *executed* (not `UP-TO-DATE`) |
| **Tests** | 🟢 **291 suites · 2,438 tests · 2,418 passed · 0 failures · 0 errors · 20 skipped** |
| **Mutation checks** | 🟢 **6 of 6 caught** (M1–M6), every mutation restored byte-identically |
| **Tree stability** | 🟢 baseline `d1a81aec51b439cf234f2cff3c80ad5c` (2 550 files) — **unchanged** at 10:11:46 and again at 10:33:19; **zero mutation residue** |
| **Verdict** | ✅ **RELEASE EVIDENCE VERIFIED** — Rev 4's fixes reproduce from a clean tree with no daemon, and every one is proven test-guarded. The Rev 4 caveat below stands as history, not as an open item. |
| **Overall module grade** | **C+** (unchanged) |

> ### Rev 5 — what changed since Rev 4
> **No production code changed.** The only edit in this revision is this documentation block.
> The test totals moved **286 → 291 suites / 2 393 → 2 438 tests** because the concurrent session's
> work (H-4 test scaffolding, `OperatingHours` zone fail-fast) landed in the tree between the two
> runs; the Rev 4 figures were correct when taken and are superseded, not corrected.
> Dispatch-module subtotal on the restored tree: **57 suites · 595 tests · 0 failures · 0 errors**.
> The repository was **exclusively held** for this pass — no Spring Boot application, no Gradle
> daemon (`--stop` before and `--no-daemon` throughout), no other Claude Code session. One VS Code
> Gradle build server (PID 19988) was resident but idle and never touched the tree; the fingerprint
> proves it.

> ### ⚠️ Rev 3's completion claim did not survive verification unmodified
> Mutation testing of every newly-added test, plus a purpose-built probe, found that the H-3
> partial-dispatch count was **silently reported as zero** on the exact failure path the fix exists
> for — and that the test meant to catch it could not. A second contract test was passing against a
> deleted state guard. A third claim (the SQL bpchar hazard) was factually wrong. Details and fixes
> in **§V**. Everything else in Rev 3 verified as written.

> ### ⚠ Concurrent editing throughout
> Another Claude Code session was editing the same tree for the whole of this pass. Observed
> timestamps: H-4 test scaffolding 15:41–16:10, `StrandedRecoveryService` + `TbPayTargetRepository`
> bounding ~15:40, `OperatingHoursService` zone fail-fast 17:12–17:25. This produced **five transient
> build failures** unrelated to any code defect (three `cleanTest` file-lock failures, one phantom
> "package does not exist" compile failure that passed on immediate retry, and two genuine but
> **in-flight, out-of-scope** `OperatingHours` test failures at 17:20 that the other session then
> fixed at 17:25). Every result below is from a run that reproduced cleanly. §6 lists which changes
> are this pass's and which are the other session's.

---

## V. Independent verification (Rev 4)

Rev 3 was treated as a **claim to be falsified**, not confirmed. Method: re-read every "COMPLETE" item
against current source; **mutation-test every new test** (revert the production logic, confirm the test
fails); build a probe for the one behaviour that looked unproven; re-derive the SQL and scope claims
from first principles.

### V.1 Defects found — all fixed and re-verified

#### V-1 · **HIGH** — the partial-dispatch count was lost on the escaping-throw path *(FIXED)*

Rev 3 §4.4 claimed requirement 9 was met. It did not hold on the path that matters.

`buildAndDispatchAllFiles` **returned** the count. A return value is assigned only on normal
completion — so when the method **threw** (precisely what `receive()`'s outer guard exists for), the
caller's `dispatchedFiles` stayed at its initialiser, `0`.

Proven with a purpose-built probe (3 split files; file 1 fully uploaded and 00301-accepted; file 2's
`firmFileDataRepository.save` throws, escaping both inner guards):

```
PROBE rsMsg = 00301 dispatch failed unexpectedly: DataIntegrityViolationException
              (files dispatched before failure: 0)     ← ONE file was already at the bank
PROBE alert = … | FILES_DISPATCHED : 0 / 3 | …
```

That is the exact misleading signal requirement 9 was written to prevent, and it is **worse than
silence**: it tells the operator nothing left the building and invites a blind re-drive that
double-sends a file the bank already holds.

**Fix** — `BulkReceptionService.java:341-368, 379-390`: the counter is now an `AtomicInteger`
accumulator passed *in*, incremented inside the loop, so it is correct at every throw point.
`buildAndDispatchAllFiles` returns `void`; its Javadoc states why it is an out-parameter. Re-verified
with the same probe: `files dispatched before failure: 1` / `FILES_DISPATCHED : 1 / 3`.

#### V-2 · **Ineffective test that masked V-1** *(FIXED)*

`partialDispatch_recordsCountInTheFailureMessage` asserted only
`.contains("files dispatched before failure:")` — which **passes with the value `0`**.

Its documented scenario was also wrong: with the gateway mock returning `null`, file 1 failed at
`issueReception` and the escape came from `failBoth`'s second firm-file save, so zero really was correct
there and the test proved nothing about a partial dispatch.

**Fix** — rewritten as `partialDispatch_countSurvivesAnEscapingThrow`: constructs a genuine partial
dispatch and asserts the exact count in **both** `RS_MSG` and the ops alert. Confirmed to fail against
pre-fix code.

#### V-3 · **Incorrect conclusion — the SQL bpchar hazard does not exist** *(CORRECTED)*

Rev 3 §4.5 and the index script warned that JDBC binds the bpchar predicate columns as varchar, that
this could make the index unusable, and that the fix would be `@JdbcTypeCode(SqlTypes.CHAR)`.

**Every affected column already carries that annotation** — never checked before the warning was written:

| Column | DB type | Mapping | Binds as |
|---|---|---|:--:|
| `TbFirmTran.tranTp` / `tranTm` / `procStat` (`:72,:77,:82`) | `bpchar` | `@JdbcTypeCode(SqlTypes.CHAR)` | CHAR ✓ |
| `TbPayFileMst.procStat` (`:65`) · `TbPayTarget.procStat` (`:81`) | `bpchar` | `@JdbcTypeCode(SqlTypes.CHAR)` | CHAR ✓ |
| `tranDt` / `tranSeq` / `procDttm` | `varchar` | *(none)* | VARCHAR ✓ |

Bind types match column types throughout, so `bpchar_col = ?` resolves through the native bpchar
operator and **the indexes are likely usable as written**. Warning withdrawn in
`dispatch_sweep_indexes_20260729.sql` §3 and replaced with the table above plus a correction-of-record.
Ordinary `EXPLAIN` validation still applies; the special hazard does not.

#### V-4 · **A contract test was green against a deleted state guard** *(FIXED)*

`DispatchScanTenantScopeContractTest.everyMutationIsStateGuarded` matched
`procStat\s*=\s*'[0369]'` against the **whole** JPQL. Every mutation satisfies that from its **SET**
clause alone (`SET p.procStat = '3'` for `failClaimedPayFile`; `SET … procStat = '6'` for both claims).

Proven by mutation: deleting `AND p.procStat = '6'` from `failClaimedPayFile` left the test **passing**.
That guard stops a late terminal write clobbering a status a callback already advanced — part of the
H-3 fix, so this was a hole in verifying my own work.

**Fix** — the assertion now splits on `WHERE` and matches the **WHERE clause only**. Re-verified by
mutation: guard removed → test fails; guard present → 10/10 pass.

**Not affected:** `DispatchClaimQueryContractTest` pins both *claims* with explicit `p.procStat = '9'`
assertions, so **H-4's duplicate-dispatch protection was never unguarded.**

#### V-5 · Documentation count errors *(CORRECTED)*

Rev 3 said `ResultCheckStarvationTest` has **12** tests (actual **9**) and **3** monitor tests were
added (actual **4**).

### V.2 Rev 3 claims that verified as written

Each mutation-tested — production logic reverted, suite re-run:

| Claim | Mutation applied | Result |
|---|---|:--:|
| N-1 SQL date bound | removed `AND t.tranDt IN :tranDts` | test **C** fails ✓ |
| N-1 cap-warning reorder | forced `capReached = false` | test **D** fails ✓ |
| H-3 EFS-leg `RuntimeException` catch | narrowed to `NoSuchElementException` | **3** tests fail ✓ |
| H-3 post-claim outer guard | narrowed to `NoSuchElementException` | **3** tests fail ✓ |
| H-3 pre-egress build guard | narrowed to `NoSuchElementException` | test **3** fails ✓ |
| H-4 claim state guards | (read) explicit `procStat = '9'` assertions | pinned ✓ |
| N-2 test compilation | clean build | exit 0 ✓ |
| N-4 toolchain-only | clean-daemon full suite | green, reproducible ✓ |

**One nuance (not a defect).** Test A (`staleDatesAreExcludedByTheQueryItself`) asserts the *date set
the service passes*, not that the query filters on it — so it survives the N-1 mutation. Test **C** is
what actually pins the fix, as designed. A reader should not treat A as the regression guard.

**Scope creep: none.** Only `dispatch/**`, this doc and the SQL script were touched. `build.gradle`
(actuator) and `application.yml` differ from HEAD but were **already in the Phase 0 baseline** —
pre-existing, untouched by Rev 3 or Rev 4.

### V.3 Environmental hazard — Gradle instability on this tree

Verification hit **7+ transient build failures with no code cause**: phantom
`package com.kosign.wecms.common.* does not exist` (200 errors, passed on retry),
`NoClassDefFoundError` on an anonymous inner class, three `cleanTest` lock failures, a
`processResources` failure, a test-JVM crash, and — most seriously — a **corrupted build output** where
`build/classes/java/main` held 650 classes with `common/` entirely missing while Gradle reported
`compileJava UP-TO-DATE`.

Cause: a second Claude Code session editing and building the same tree concurrently (edits observed
15:41–18:30 across `OperatingHoursService`, `DispatchResultResolverTest`,
`ApprovalBoxDownloadServiceTest`, `EndpointAuthorizationCoverageTest`), interacting with Gradle's
incremental/VFS state.

**Not a code defect** — every green result is reproducible from a clean daemon — but a real
verification hazard: an `UP-TO-DATE` compile against a partially-deleted output silently tests stale
classes. **Operational rule: never run two Gradle invocations against this tree concurrently; CI must
build from a clean daemon.**

### V.4 Rev 4 verification evidence

```
Java   : openjdk 21.0.10 2026-01-20 LTS (Temurin-21.0.10+7-LTS)
Gradle : 8.14.4
Branch : wecms-ui-fixes   HEAD: 98c924f   Tree: 70 M · 2 D · 69 ??

$ ./gradlew clean compileJava compileTestJava   → BUILD SUCCESSFUL  exit 0  (126 s, 0 errors)
$ ./gradlew cleanTest test                      → BUILD SUCCESSFUL  exit 0  (135 s)
```

| | Suites | Tests | Passed | Failed | Errors | Skipped |
|---|--:|--:|--:|--:|--:|--:|
| Full suite | **286** | **2,393** | **2,373** | **0** | **0** | **20** |
| Focused scope (24 suites) | 24 | 233 | 233 | 0 | 0 | 0 |

**Files changed by Rev 4** — production: `BulkReceptionService.java` (V-1). Tests:
`BulkReceptionStrandedFailureTest` (V-2), `DispatchScanTenantScopeContractTest` (V-4). SQL:
`dispatch_sweep_indexes_20260729.sql` §3 (V-3). Docs: this file. **No other production code touched.**

---

### 5-bis. Rev 5 — stable-tree release evidence (2026-07-30)

Re-run from a quiet, exclusively-held tree with `build/` deleted and no Gradle daemon. Supersedes
the Rev 4 figures above (the tree grew by the concurrent session's tests between the two runs).

```
Java   : openjdk 21.0.10 2026-01-20 LTS (Temurin-21.0.10+7-LTS)
Gradle : 8.14.4
Branch : wecms-ui-fixes   HEAD: 98c924fde6cbece4dd2cfbea228c4539247c251c   Tree: 74 M · 2 D · 71 ??

$ ./gradlew --stop                                          → 0 daemons live
$ find build -mindepth 1 -delete; rmdir build               → build/ REMOVED
$ ./gradlew --no-daemon clean compileJava compileTestJava   → BUILD SUCCESSFUL  exit 0  (116 s, 0 errors)
$ ./gradlew --no-daemon cleanTest test                      → BUILD SUCCESSFUL  exit 0  (67 s)
```

| | Suites | Tests | Passed | Failed | Errors | Skipped |
|---|--:|--:|--:|--:|--:|--:|
| Full suite | **291** | **2,438** | **2,418** | **0** | **0** | **20** |
| Focused scope (10 named suites) | 10 | 135 | 135 | 0 | 0 | 0 |
| Dispatch package (post-restore) | 57 | 595 | 595 | 0 | 0 | 0 |

**Mutation checks — 6 of 6 caught.** Each applied alone, then restored and confirmed byte-identical
with `diff -q` before the next.

| # | Mutation | Detected by | Failures |
|---|---|---|--:|
| M1 | Drop `t.tranDt IN :tranDts` from `findResultCheckCandidates` | `ResultCheckStarvationTest` · test C | 1 |
| M2 | Force `capReached` to `false` in `ResultCheckService.runOnce` | `ResultCheckStarvationTest` · test D | 1 |
| M3a | Remove the outer post-claim `catch` (`BulkReceptionService:353`) | `BulkReceptionStrandedFailureTest` | 4 |
| M3b | Remove the pre-egress build `catch` (`:630`) | `BulkReceptionStrandedFailureTest` | 2 |
| M3c | Remove the EFS-leg `catch` (`:678`) | `BulkReceptionStrandedFailureTest` | 4 |
| M4 | Force the dispatched-file count to zero (`:357`) | `BulkReceptionStrandedFailureTest` · 9b | 2 |
| M5 | Drop `AND p.procStat = '6'` from `failClaimedPayFile` | `DispatchScanTenantScopeContractTest` | 2 |
| M6a | Drop the H-4 claim guard from `claimForReception` | `DispatchClaimQueryContractTest` | 2 |
| M6b | Drop the H-4 claim guard from `claimForDispatch` | `DispatchClaimQueryContractTest` | 2 |

M3 is counted once in the "6 of 6" total but was exercised at all three guard sites. An earlier M3
attempt mutated line 346 and passed spuriously — the guards had shifted **+7 lines** since Rev 4.
The file was restored and each guard re-located by reading its context before re-mutating; treat
hard-coded line numbers in this document as advisory.

**Residue.** Tree fingerprint (md5 of the sorted md5sums of 2 550 `.java`/`.sql`/`.md`/`.yml`/
`.properties` files under `src/`, `docs/`, and `Claude Code (AI)/sql/`) was
`d1a81aec51b439cf234f2cff3c80ad5c` at 10:09:01, unchanged at 10:11:46 (stability window) and
**still unchanged at 10:33:19** after all nine mutations. `git status --porcelain` returned the same
74 M / 2 D / 71 ?? counts throughout; no `MUTATION` marker survives anywhere under `src/`.

**Files changed by Rev 5** — none in production, none in tests, none in SQL. Documentation only
(this file).

---

## 0. What was asked, and what happened

| # | Objective | Status | Where |
|---|---|:--:|---|
| 1 | **N-2** broken test compilation | ✅ **FIXED** | §1 |
| 2 | **N-1** result-check candidate starvation | ✅ **FIXED** | §2 |
| 3 | **N-4** Mockito / Byte Buddy test execution failures | ✅ **RESOLVED — toolchain only, no code or dependency change** | §3 |
| 4 | **H-3 / R-2 / N-3** unrecovered `PROC_STAT='6'` strand | ✅ **FIXED** | §4 |
| 5 | Phase 5 — bound companion queries | ✅ **DONE** (all 3 consumers) | §4.4 |
| 6 | Phase 6 — index script, authored not executed | ✅ **DELIVERED, `NOT EXECUTED`** | §4.5 |

**Nothing outside that scope was started.** No Vendor/Expense consolidation, no `BankDispatchPolicy`,
no entity relocation, no EFS client extraction, no JDBC batching, no async hand-off, no SQL executed,
and no flag enabled anywhere.

---

## 1. N-2 · Test compilation — FIXED

**Rev 2 finding:** H-4 added a 22nd constructor parameter (`BulkDispatchClaimTxService`) and three test
classes still passed 21 arguments; `compileTestJava` failed with 3 errors and no test could run.

**Already repaired by the concurrent session before this pass began** (test files updated 15:41–15:55).
Verified from clean:

```
./gradlew clean compileJava compileTestJava   →  BUILD SUCCESSFUL (exit 0)
```

Two of the three sites were repaired *well* — `BulkReceptionReceiveTest:112` and
`BulkReceptionServiceValidationTest:99` inject a **real** `BulkDispatchClaimTxService` over the mocked
repository and stub `claimForReception → 1`, so the claim is genuinely exercised rather than stubbed
away. Claim-lost (`→ 0`) is covered by `BulkDispatchClaimTxServiceTest` and
`BulkReceptionConcurrentClaimTest.loser_performsNoEgressAndNoInsert`, satisfying the requirement that
both directions be asserted.

**One gap closed by this pass.** `BulkReceptionServiceTest` passed `null` for the new argument among 17
other nulls. The task required no `null` unless the constructor explicitly permits it, and Lombok's
generated constructor does not:

`src/test/java/…/dispatch/service/BulkReceptionServiceTest.java:44-64` — a `@Mock
BulkDispatchClaimTxService` is now supplied in position 19. Rationale recorded in the field Javadoc:
a null claim collaborator would NPE from *inside* the claim call the moment a `receive()` test is added
to that class, hiding the real cause at the construction site; and a mock's default `false` return is
the safe direction (an unconfigured claim means "stand down", never "proceed").

---

## 2. N-1 · Result-check starvation — FIXED

**Rev 2 finding (HIGH).** Candidates were selected oldest-first with a `LIMIT`, then date-filtered **in
Java after the limit**. Rows parked at `202`/`'6'` from older dates sorted to the front of every page,
consumed the whole window, and were discarded — so once such rows reached the batch size the sweep
processed nothing, permanently, logging only `DEBUG "no aged … transfers"`.

### 2.1 Legacy verification first

Rev 2 asserted the date filter belonged in SQL. That was checked against JEX before changing anything,
and the picture is more specific than Rev 2 recorded:

- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.TB_FIRM_TRAN_R001.xml` is a **dynamic shell** —
  `FROM TB_FIRM_TRAN WHERE 1=1 ??` with a `DYNAMIC_0` placeholder.
- `PROCESSING_RESULT_CHECK_BATCH.java:62-64` calls `util.createIDOData("TB_FIRM_TRAN_R001")` and
  **passes no dynamic clause**, so that copy would select the entire table.
- The copy that actually runs is the **batch-side** one,
  `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_FIRM_TRAN_R001.xml`:

```sql
WHERE  TRAN_DT = TO_CHAR(now() ,'YYYYMMDD')
   AND PROC_STAT = '6'
   AND TRAN_TM <= TO_CHAR(now() - INTERVAL '2 minute','hh24miss')
   AND TO_CHAR(now() - INTERVAL '2 minute','hh24miss') < '235800'
   AND TRAN_TP = '202' -- 입금이체
```

So `TRAN_DT` **was** a SQL predicate in legacy. Moving it into Java was the regression; moving it back
is a return to legacy shape, not a divergence. Confirmed also: no `BIZ_KEY`, no `ORDER BY`, no `LIMIT` —
Rev 2's reclassification of the "cross-tenant" half as a false positive stands.

### 2.2 The fix

**`TbFirmTranRepository.findResultCheckCandidates` (line 102)** — the eligible date set is now a WHERE
predicate, applied before the limit:

```java
SELECT t FROM TbFirmTran t
 WHERE t.tranTp   = :tranTp
   AND t.procStat = :procStat
   AND t.tranDt  IN :tranDts          // ← N-1: was absent; filtering happened after the LIMIT
 ORDER BY t.tranDt ASC, t.tranTm ASC, t.tranSeq ASC
```

**`ResultCheckService.eligibleTranDts(LocalDate)`** — a pure static function returning
`[UTC today, UTC yesterday]`, called at `runOnce():289`. Evaluated in **UTC** because `TRAN_DT` is
*written* in UTC (`PymnHelper.pymn_0001_001_c001:2197` via `DateTimeUtils.utcNow()`); deriving it from
an ICT date is the D-1 defect and must not return.

**The tiered aging rule is untouched.** `selectAged` / `isAged` keep the `RQ_DTTM`-first three-tier
logic verbatim — that is the documented D-1 improvement over legacy's wrapping `HHmmss` compare, and
collapsing it into SQL would reintroduce the UTC/ICT boundary bug. Only the *date bound* moved, which
is a plain equality on a single column and carries none of that subtlety. `selectAged` retains its date
filter as a deliberate redundant guard so a future widening of the query cannot silently outrun the
reasoning behind `isAged`.

**Cap warning re-ordered (`runOnce():302-320`).** `capReached` is now evaluated **before** the
`aged.isEmpty()` return, so a sweep that did no work can no longer log only at `DEBUG`. Three distinct
outcomes:

| Situation | Log level | Message |
|---|---|---|
| Empty page | `DEBUG` | `no aged 202/PROC_STAT=6 transfers` — healthy idle |
| Full page, some aged | `WARN` | `candidate batch cap reached` |
| **Full page, none aged** | `WARN` | `full candidate page (n) but no row passed the 2-minute age window — sweep did no work this pass` |

### 2.3 Tests — A through E

New `ResultCheckStarvationTest` (9 tests — Rev 3 said 12; corrected in Rev 4 V-5). Also migrated `ResultCheckBoundedScanTest` (16 tests) and
`ResultCheckPollTest` to the 4-arg signature.

| Req | Test | Asserts |
|---|---|---|
| **A** | `staleDatesAreExcludedByTheQueryItself` | the captured date set is exactly `{UTC today, UTC yesterday}` and excludes a 3-days-ago date |
| **A** | `stalePageDoesNotDisplaceTodaysAgedRow` | 10 stale rows (batch size 10) **plus** one aged row from today → the today row is still processed |
| **B** | `yesterdayUtcCandidateIsEligible` | a UTC-yesterday aged row is selected and processed |
| **C** | `queryRestrictsTranDtBeforeThePageLimit` | `@Query` text contains `t.tranDt IN :tranDts`, positioned **before** `ORDER BY`; oldest-first ordering retained; no `bizKey` |
| **C** | `pageLimitAppliedOnceWithNoCompetingSort` | limit applied exactly once, page 0, `Sort.unsorted()` |
| **D** | `fullPageWithNothingAgedWarnsRatherThanLookingIdle` | via a Logback `ListAppender`: both WARN lines present |
| **D** | `genuinelyIdleSweepDoesNotWarn` | an empty page raises **no** warning — otherwise the D signal is worthless |
| **E** | `eligibleSetIsComputedInUtcNotIct` | `eligibleTranDts` is UTC-derived, and `runOnce` passes exactly that |
| **E** | `ictUtcDivergenceWindowIsPinned` | 02:00 ICT is the previous UTC day; 08:00 ICT is the same — pins *why* E matters |

`ResultCheckSelectionBoundaryTest` (the pre-existing D-1 00:00–07:00 ICT suite) is untouched and passes.

> **Note on test C.** Asserted as a `@Query`-text contract rather than a `@DataJpaTest`, following the
> established idiom in `DispatchClaimQueryContractTest`: the project ships no embedded database and no
> Testcontainers, and the property that matters here is static — a `LIMIT` applied before a Java-side
> date filter *is* the defect.

---

## 3. N-4 · Test-execution failures — RESOLVED (toolchain only)

**Rev 2 observation:** 2,194 tests / **97 failed**, every sampled failure a
`MockitoException → TypeCache → UnsupportedOperationException at InstrumentationImpl` —
i.e. byte-buddy failing to attach its instrumentation agent.

### Classification, in the required order

1. **Clean daemon / process state.** `./gradlew --status` showed **10 stale daemons** plus a BUSY one,
   and 7 `java.exe` processes; a concurrent session had wiped `build/test-results/` mid-read. After
   `./gradlew --stop` and a fresh daemon, **the full suite passed** — 278 suites / 2,257 tests / 0
   failures on the first attempt.
2. **Reproduce one failing test in isolation.** Four of the previously-failing classes —
   `ExpenseDispatchExecutorTest`, `VendorDispatchExecutorTest`, `DispatchAutoReceiveRegistrarTest`,
   `ExpensePaymentReportDownloadAuditTest` — **all pass in isolation**. Not reproducible.
3. **`-XX:+EnableDynamicAgentLoading`** — not needed and **not added**. The self-attach demonstrably
   works: every run since emits
   `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because
   bootstrap classpath has been appended`, which *is* byte-buddy's agent jar being appended.
4. **Byte-buddy vs JDK support** — recorded but not acted on. `mockito-core 5.17.0`; byte-buddy
   force-resolved to `1.17.8` by the Spring Boot BOM (`assertj-core 3.27.7` requests `1.18.3`); JDK
   **21.0.10** Temurin. No incompatibility was demonstrated, so no pin was applied.
5. **No pin, no override, no JVM flag.** `build.gradle` is **unchanged** by this pass.

### Verdict

**Environment / toolchain only — specifically Gradle daemon and concurrent-process contention. Not a
code regression, and not a dependency-management defect.** Two independent supports: (a) the affected
classes include ones this work never touched; (b) the failures vanished on a clean daemon with no code
or dependency change.

The smallest verified correction was operational, so that is what is recorded rather than a build edit.
**Operational note for CI and for developers:** run the suite from a clean daemon, and do not run two
Gradle invocations against this tree concurrently. Five further transient failures during this pass
(§ header) all trace to the same cause. If N-4 ever recurs on a *quiet* tree, escalate to step 4 —
pin `byte-buddy` to `1.18.3` via `ext['byte-buddy.version']` — but do **not** pre-emptively pin it now,
because there is no evidence the version is at fault.

---

## 4. H-3 / R-2 / N-3 · The `PROC_STAT='6'` strand — FIXED

**Rev 2 finding.** `buildAndDispatchFile` caught only `BusinessException` around the EFS `00301` leg.
`VanApiKeyService.getApiKey` throws `IllegalStateException` when `TB_VAN_API_INFO` has no key (the B2
provisioning gap), and the crypto / serialisation steps throw `IllegalStateException` /
`IllegalArgumentException`. Each escaped, rolled back the per-file `REQUIRES_NEW` transaction —
discarding the `TB_FIRM_FILE_MST` row **and** any `failBoth` write inside it — then escaped `receive()`
to be swallowed by `DispatchAutoReceiveRegistrar`. The pay-file was left at `'6'`: neither `'9'` nor
`'3'`, and every recovery and monitoring query selected only `'9'`.

**N-3:** H-4's claim commits `'9'→'6'` in its own transaction *before* any file work, so the `'6'` can
no longer roll back with the per-file transaction — every failure in between strands the row.

### 4.1 Fail closed around the EFS reception leg

`BulkReceptionService.java:666` — a `catch (RuntimeException e)` alongside the existing
`catch (BusinessException e)`, at the smallest boundary around `issueReception`:

- existing `BusinessException` handling and result-code mapping **unchanged**;
- closes out the `00301` audit row with `RS_CD = G999` (`FirmBankingResultCd.ETC_ERR`) so the ledger
  never shows a request that silently never resolved;
- `failBoth(...)` marks **both** the firm-file and pay-file terminally failed, inside the per-file
  transaction, then returns `false` so that transaction **commits**;
- `RS_MSG` carries the exception **type** and context only — never `e.getMessage()`, because messages on
  this path can embed a file name, a gateway body, an account number or a serialised payload, and
  `RS_MSG` is read back into the UI. The full exception goes to the log;
- **`RuntimeException`, never `Throwable`/`Error`** — an `OutOfMemoryError` must not be converted into a
  business FAIL by a handler that may itself be unable to run.

### 4.2 Failures before the split-file row exists

Every failure point after the claim was reviewed. Two further guards were needed:

**Pre-egress build guard** (`BulkReceptionService.java:618`) — wraps `fixedLengthCodec.encode` +
`writeLocalFile`. The realistic throws are an over-width field (the codec correctly *refuses* rather
than truncating — on a fixed-length bank file, truncation is a wrong-amount bug) and an unwritable
staging path. Failing terminally here is unambiguous precisely because **nothing has left the
building**.

**Post-claim outer guard** (`BulkReceptionService.java:346`) — covers everything the per-file
transaction cannot: `nextFileRound` (a repository read in `receive()`, outside
`buildAndDispatchFile`), split-window construction, the transaction template itself, and the
`TB_FIRM_FILE_MST` insert. Because that transaction has already rolled back, the terminal write must
come from outside it:

- `TbPayFileMstRepository.failClaimedPayFile` (line 108) — a conditional
  `UPDATE … SET PROC_STAT='3', RS_MSG=:rsMsg … AND p.procStat = '6'`;
- driven by `BulkDispatchClaimTxService.failStrandedPayFile` in its own `REQUIRES_NEW` transaction
  (needed because the caller reaches it from a `catch` block where the ambient transaction may already
  be rollback-only);
- **guarded on `'6'`**, so it can only ever move a row *out of* the stranded state — it cannot overwrite
  a FAIL a split file already recorded, a status a callback has advanced, or a row still at `'9'`. The
  affected-row count distinguishes "I recorded it" from "something else owns this row";
- `RS_MSG` truncated to **200 chars** to match the mapped column width — a failure message that
  overflowed its own column would fail its own UPDATE and re-create the strand.

`receive()` was refactored so the post-claim region has one entry point (`buildAndDispatchAllFiles`,
line 372) that also returns **how many split files reached the bank**, which feeds the partial-dispatch
reporting below.

### 4.3 Recovery / detection of committed `'6'`

Per the constraint — *must not resend a file that may already have reached SFTP or EFS; prefer
monitoring over blind resend for ambiguous post-egress states* — this is implemented as **detection
only**, in `StuckPayFileMonitorService` (already read-only and double-gated OFF).

A third sweep (`findStrandedClaimedPayFiles`, line 358) selects `PROC_STAT='6'` pay-files past the
grace window, bounded and ordered, and classifies them by how far the dispatch got
(`classifyClaimedPayFile`, line 382) using only existing rows — no new column, no new table:

| New `StuckPayFileCause` | Condition | Operator meaning |
|---|---|---|
| `CLAIMED_NO_FIRM_FILE` | no `TB_FIRM_FILE_MST` row | nothing left the building — the only state safe to re-drive |
| `RECEPTION_UNCONFIRMED` | firm-file row(s) all at `PROC_STEP='0'` | **ambiguous** — SFTP precedes the `00301` call, so the file may already be at the bank. The `00301` audit row's presence is logged so "never sent" can be told from "sent, unconfirmed" |
| `AWAITING_EXECUTION_RESULT` | any firm-file row past `PROC_STEP='0'` | inside the callback pipeline; often legitimate (a partially-reported split batch). **Never re-drive** |

Requirements met: grace-window constrained (shared threshold), bounded and paginated
(`pay-file-batch-size`, default 200), disabled by default under the existing double gate, no writes at
all, shared-DB safe (read-only), and the four sub-states distinguished as far as the data permits —
states 3 and 4 map to `AWAITING_EXECUTION_RESULT` with the firm-file sweep's existing
`BULK_EXECUTION_NOT_ISSUED` / `BULK_EXECUTION_UNCONFIRMED` carrying the per-file detail.

`StuckPayFileReport` gained `claimedStuckCount`, counted separately because this population is neither
"hasn't started" nor "definitely at the bank".

### 4.4 Phase 5 — companion queries bounded

All three consumers now use bounded, oldest-first finders. Two were done by the concurrent session
(§6); the pay-file finder and the monitor's use of it were completed here.

| Consumer | Now uses | Cap |
|---|---|---|
| `StrandedRecoveryService.recoverPayTargets` | `TbPayTargetRepository.findStrandedPayTargets` | `recovery.batch-size` |
| `StrandedRecoveryService.recoverPayFiles` | `TbPayFileMstRepository.findStrandedPayFiles` | `recovery.batch-size` |
| `StuckPayFileMonitorService` WAIT + claimed sweeps | `TbPayFileMstRepository.findStrandedPayFiles` | `stuck-monitor.pay-file-batch-size` |

`findStrandedPayFiles` serves both `'9'` and `'6'` from one query (`procStat` is a bound parameter).
Global scope preserved — **no `BIZ_KEY` added**, matching legacy's system-batch design; the ordering is
total without it because `FILE_SEQ_NO` comes from a global sequence. The unbounded
`findByProcStatAndProcDttmLessThan` finders are retained and documented as diagnostic-only with "do not
add new callers". Cap-reached is logged on every sweep, so a truncated run never reads as complete —
and truncation here can only under-report, because the age cutoff is in the *same* SQL as the limit
(the N-1 lesson).

### 4.5 Phase 6 — index script, NOT EXECUTED

📄 **`Claude Code (AI)/sql/dispatch_sweep_indexes_20260729.sql`** — authored, reviewed, **not run
against any database**.

Existing indexes were checked read-only first: **all three tables carry only their primary key**, and
no PK matches any sweep's access pattern, so there is no duplicate-index risk.

| Index | Columns | Serves |
|---|---|---|
| `ix_tb_firm_tran_result_check` | `(TRAN_TP, PROC_STAT, TRAN_DT, TRAN_TM, TRAN_SEQ)` | equalities lead; `TRAN_DT` third because it is both the IN-list filter and the first sort key; then the rest of the `ORDER BY` |
| `ix_tb_pay_file_mst_stranded` | `(PROC_STAT, PROC_DTTM)` | equality then range/sort |
| `ix_tb_pay_target_stranded` | `(PROC_STAT, PROC_DTTM)` | equality then range/sort |

Contains: existence checks (`IF NOT EXISTS`), forward SQL, `CONCURRENTLY` variants for UAT/PROD,
rollback SQL, reason, target query per index, estimated operational impact (sizes measured read-only:
`tb_firm_tran` ~10,151 rows / 2,480 kB; `tb_pay_file_mst` ~121; `tb_pay_target` ~94), `EXPLAIN`-only
verification steps, and an explicit `NOT EXECUTED` banner.

> ⚠ **One flagged risk the DBA must validate.** `tran_tp`, `tran_tm` and `proc_stat` are **`bpchar`**,
> while JDBC binds parameters as varchar. PostgreSQL has no native `bpchar = varchar` operator and
> resolves it by casting, which — depending on the declared parameter type — can render a plain bpchar
> btree index unusable. This could not be settled from here: confirming it needs the index to exist plus
> an `EXPLAIN` against it, and this pass is read-only (index creation is DDL; `EXPLAIN ANALYZE` is
> deny-listed). The script carries the before/after `EXPLAIN` procedure and, if the plan still shows
> Seq Scan + Sort, points at the likelier real fix: `@JdbcTypeCode(SqlTypes.CHAR)` on the mapping, which
> is already this codebase's established pattern for bpchar columns (V38 precedent). **Do not keep an
> unused index** — roll back and raise it.

### 4.6 Tests — all nine required scenarios

New `BulkReceptionStrandedFailureTest` (11 tests), plus additions to the monitor suite.

| Req | Test | Asserts |
|---|---|---|
| 1 | `missingApiKey_doesNotStrandThePayFile` | `IllegalStateException` from `getApiKey` → pay-file reaches FAIL, never left at `'6'`; `receive()` does not propagate |
| 1 | `missingApiKey_recordsTheAuditOutcome` | the `00301` audit row is closed with `G999` |
| 2 | `cryptoFailure_doesNotStrandThePayFile` | an invalid-length AES key → `IllegalArgumentException` inside the leg → FAIL |
| 3 | `preEgressBuildFailure_stillReachesATerminalState` | codec refuses an over-width field → FAIL, **and no SFTP upload and no EFS call** |
| 3 | `escapingThrow_isRecordedThroughTheConditionalUpdate` | `nextFileRound` throws (outside the per-file tx) → recorded via `failClaimedPayFile`; `RS_MSG` contains the type, **not** the raw message, ≤ 200 chars |
| 4 | `businessExceptionBehaviourIsUnchanged` | committed FAIL as before, and **not** routed through the new outer guard |
| 5 | `inFlightRowOlderThanThreshold_isReportedAsClaimedStranded` | an aged `'6'` row is reported as `CLAIMED_NO_FIRM_FILE` |
| 6 | `recentInFlightRow_isNotReported` | a `'6'` row inside the window is **not** reported — no false positive on every normal dispatch |
| 7 | `claimedStrandedSweep_neverResendsAndNeverWrites` | reported, but **no** `save`, `claimForReception`, `failClaimedPayFile` or firm-file write |
| 8 | `lostClaim_hasNoSideEffects` | claim `→ 0`: nothing built, sent or written — and the loser must not mark the winner's file failed |
| 9 | `partialDispatch_isReportedAsPartial` | 3 split files, file 3 fails → ops alert carries `FILES_DISPATCHED` |
| 9 | `partialDispatch_recordsCountInTheFailureMessage` | `RS_MSG` carries `files dispatched before failure:` |
| — | `errorIsNotCaught` | a `StackOverflowError` **propagates** — guard-rail against widening to `Throwable` |
| — | `terminalWriteIsConditional` | a 0-row conditional update is a normal outcome, not an error |

### 4.7 Two pre-existing tests changed — both updated, neither weakened

Per the constraint not to silently delete tests, both are recorded explicitly:

**`BulkReceptionReceiveTest.unexpectedThrowInLaterSplit_propagates_earlierFileAlreadySaved`** →
renamed `…_recordsTerminalFail_earlierFileAlreadySaved`. It asserted the throw **propagates**. That was
the defect: the only caller catches and logs, so "propagates" meant "disappears into a log line" while
the committed claim stranded. The two properties the test always existed to protect — the earlier file
stays committed, the loop stops — are **unchanged and still asserted**; new assertions confirm the
pay-file reaches FAIL and the ops alert carries the partial-dispatch count. The Javadoc records what
changed and why.

**`StuckPayFileMonitorServiceTest.completedFailedAndInFlightRows_areIgnored`** → split into
`completedAndFailedRows_areIgnored` + `recentInFlightRow_isNotReported` +
`inFlightRowOlderThanThreshold_isReportedAsClaimedStranded`. Its premise — that `'6'` means "actively
processing" and is always ignorable — *was* the H-3 blind spot. `'0'`/`'3'` are still asserted
terminal-and-ignored; `'6'` inside the window is still ignored; `'6'` past the window is now correctly
reported.

One assertion of my own was wrong and was corrected rather than forced onto the code: I had expected the
later-split failure to route through the *outer* guard, but the **inner** EFS guard catches it and
records `failBoth` inside the committed per-file transaction — which is the better path (it marks the
firm-file row too). The test now asserts actual behaviour.

---

## 5. Verification evidence

All from a fresh daemon, from clean. **No `UP-TO-DATE` test result is reported as a pass.**

```
Java    : openjdk 21.0.10 2026-01-20 LTS (Temurin-21.0.10+7-LTS)
Gradle  : 8.14.4  (daemon JVM 21.0.10)
Branch  : wecms-ui-fixes    HEAD: 98c924f
Tree    : 70 modified · 2 deleted · 64 untracked (uncommitted)

$ ./gradlew clean compileJava compileTestJava      →  BUILD SUCCESSFUL   exit 0
$ ./gradlew cleanTest test                         →  BUILD SUCCESSFUL   exit 0
```

| Run | Suites | Tests | Passed | Failed | Errors | Skipped |
|---|--:|--:|--:|--:|--:|--:|
| **Full suite (final)** | **282** | **2,317** | **2,297** | **0** | **0** | **20** |
| Focused suites (remediation scope) | 24 | 233 | 233 | 0 | 0 | 0 |

Focused suites executed (all green): `BulkReceptionServiceTest`, `BulkReceptionReceiveTest`,
`BulkReceptionServiceValidationTest`, `BulkReceptionStrandedFailureTest`,
`BulkReceptionConcurrentClaimTest`, `BulkDispatchClaimTxServiceTest`,
`DispatchClaimQueryContractTest`, `DispatchScanTenantScopeContractTest`, `ResultCheckServiceTest`,
`ResultCheckPollTest`, `ResultCheckBoundedScanTest`, `ResultCheckSelectionBoundaryTest`,
`ResultCheckStarvationTest`, `StrandedRecoveryServiceTest`, `StrandedRecoveryResultCheckLockTest`,
`StuckPayFileMonitorServiceTest`, `StuckFirmFileMonitorTest`, `CallbackIpWhitelistFilterTest`
(+`$Validation`), `BulkCallbackRoutingTest`, `BulkReceptionResultServiceTest`,
`BulkExecutionResultServiceTest2`, `DispatchAutoReceiveRegistrarTest`, `RealtimeDispatchRegistrarTest`.

Test count grew **2,257 → 2,317** (+60) across this pass; 23 of those are the new
`ResultCheckStarvationTest` + `BulkReceptionStrandedFailureTest` + the four monitor additions, the rest
from the concurrent session.

> **One intermediate run is worth recording.** At 17:20 the full suite reported 2,316 tests / **2
> failed** — `OperatingHoursServiceTest.invalidZone_failsFastAtBeanCreation_notOncePerPayment` and
> `OperatingHoursZoneConsistencyTest`. Both are the other session's **in-flight** zone fail-fast work
> (`OperatingHoursService.java` modified 17:12, its test 17:13), entirely outside this remediation and
> not caused by any change here. That session completed it at 17:25 and the suite went green. Recorded
> because a reader comparing timestamps would otherwise see a contradiction.

### Revised grades

| Dimension | Rev 2 | **Rev 3** | Basis |
|---|:--:|:--:|---|
| **Build & verification health** | **F** | **A−** | Compiles clean; 2,317 tests / 0 failures reproducibly. −1 for the daemon-contention fragility (§3). |
| Failure handling | C+ | **B** | All four H findings closed. Three fail-closed guards on the bulk rail, a conditional terminal write, and a `'6'` detection sweep. Still open: `DiscardPolicy`, registrar log-only hand-off (M-6). |
| Performance | C− | **C** | Three sweeps bounded; index script authored (unapplied, and bpchar-usability unproven). No batching, no async hand-off. |
| Legacy compatibility | A | **A** | Strengthened again — the N-1 fix was derived from the batch-side IDO after finding the `WECMS_ETC` copy is an unused dynamic shell. |
| Maintainability | C− | **C−** | Unchanged — no structural work in scope. |
| Complexity | D+ | **D+** | `BulkReceptionService` grew again (~803 → ~880) for the guards. Justified, but it reinforces R-9. |
| Coupling · Duplication · Extensibility | C · F · D+ | **unchanged** | R-6…R-16 untouched by design. |
| **Overall** | **C** | **C+** | Correctness and verifiability materially better; structure identical. |

---

## 6. Change attribution

Because two sessions edited this tree concurrently, ownership is recorded explicitly.

**This remediation pass — production (7 files)**
- `dispatch/repository/TbFirmTranRepository.java` — SQL date bound (N-1)
- `dispatch/service/ResultCheckService.java` — `eligibleTranDts`, cap-warning order (N-1)
- `dispatch/repository/TbPayFileMstRepository.java` — `findStrandedPayFiles`, `failClaimedPayFile`
- `dispatch/service/BulkDispatchClaimTxService.java` — `failStrandedPayFile`
- `dispatch/service/BulkReceptionService.java` — 3 fail-closed guards, `buildAndDispatchAllFiles`, partial-dispatch reporting, `RS_MSG` bounding
- `dispatch/monitor/StuckPayFileCause.java` — 3 new causes
- `dispatch/monitor/StuckPayFileReport.java` + `StuckPayFileMonitorService.java` — third sweep, cap logging

**This pass — tests (9 files)**: new `ResultCheckStarvationTest`, new `BulkReceptionStrandedFailureTest`;
updated `BulkReceptionServiceTest`, `BulkReceptionReceiveTest`, `ResultCheckBoundedScanTest`,
`ResultCheckPollTest`, `StuckPayFileMonitorServiceTest`, `StuckFirmFileMonitorTest`,
`StrandedRecoveryServiceTest`, `StrandedRecoveryResultCheckLockTest`.

**This pass — SQL (1 file, NOT EXECUTED)**: `Claude Code (AI)/sql/dispatch_sweep_indexes_20260729.sql`

**Concurrent session (not this pass)**: the H-4 claim itself (`claimForReception`,
`BulkDispatchClaimTxService`, `BulkReceptionConcurrentClaimTest`, `DispatchClaimQueryContractTest`) and
the N-2 constructor repairs; `StrandedRecoveryService` bounding + `TbPayTargetRepository
.findStrandedPayTargets`; `OperatingHoursService` zone fail-fast + `OperatingHoursZoneConsistencyTest`;
`DispatchScanTenantScopeContractTest`. **`build.gradle` unchanged by both.**

---

## 7. Remaining blockers

### SIT

| # | Blocker | Owner | Status |
|---|---|---|:--:|
| S-1 | ~~Fix test compilation (N-2)~~ | Dev | ✅ done |
| S-2 | ~~Resolve the 97 test failures (N-4)~~ | Dev | ✅ toolchain only; §3 |
| S-3 | ~~Fix N-1 starvation~~ | Dev | ✅ done |
| S-4 | **Supply the VAN gateway source IPs.** With `protection=ENFORCE` the default, an unset allow-list means every callback gets 403 — correct, but it will present as an outage | VAN Center → Ops | ⏳ external |
| S-5 | Confirm each environment's `callback.protection` and `trusted-proxies` are set deliberately | Ops | ⏳ |
| S-6 | **NEW** — CI must run the suite from a clean daemon and never two Gradle invocations concurrently on one tree (§3) | Dev/CI | ⏳ |

### UAT

| # | Blocker | Owner |
|---|---|---|
| U-1 | ~~R-2 / H-3 fail-closed + `'6'` visibility~~ | ✅ done (§4) |
| U-2 | **Apply the index script** — `dispatch_sweep_indexes_20260729.sql`, running the `EXPLAIN` validation in §3 of that script. The bpchar/varchar hazard Rev 3 flagged does NOT apply (Rev 4 V-3) — the mappings already bind CHAR. Do not keep an unused index | DBA |
| U-3 | ~~Bound the companion finders~~ | ✅ done (§4.4) |
| U-4 | **`nextFileRound` `MAX+1` race** (R-4 remainder) — still a Java-side `MAX+1` over a repository read. Latent while drivers are gated off; funnel through `wb_get_next_val` | Dev |
| U-5 | **L-2** — `RECEPTION_SUCCESS_CD` code set (`TODO(B5)`) and the `EfsCryptoService` URL-encode pass, both against a **captured gateway sample** | Dev + VAN Center |
| U-6 | Confirm the DB target so the coexistence gate on `recovery.enabled` can be reasoned about | Ops |
| U-7 | **NEW** — decide the operational response to `CLAIMED_NO_FIRM_FILE` / `RECEPTION_UNCONFIRMED` / `AWAITING_EXECUTION_RESULT`. The monitor reports them; the run-book for each does not exist, and two of the three must not be re-driven blind | PO + Ops |

### PROD

| # | Blocker | Owner |
|---|---|---|
| P-1 | **R-13** — replace `StrictHostKeyChecking=no` with a real `known_hosts` | Dev + Infra |
| P-2 | **R-12** — durable failure record + change `OpsAlertAsyncConfig`'s `DiscardPolicy`. Dropping an alert can still be the only trace of a failed hand-off | Dev |
| P-3 | **R-10 / R-11** — batching and off-thread hand-off, validated by a load test | Dev |
| P-4 | Multi-instance decision — the monitor's de-dup, revocation and rate-limit stores are all in-memory | PO + Ops |
| P-5 | Pre-existing gates **R-02** (action-level authz) and secret rotation | PO + Ops |

**Not blockers.** R-6, R-7, R-8, R-9, R-14, R-15, R-16 remain open structural debt and are why the grade
is C+ rather than B. None is a correctness or safety gate. Rev 2's sequencing for them stands, with
wave 3 (R-5 residual Javadoc + **R-8**, deleting the dead `CutoffCheck.apiTransport` seam) still the
cheapest next step at roughly three hours.

---

## 8. Final status

### ✅ COMPLETE — for the stated remediation scope

All four objectives are closed and verified against the current tree:

- **N-2** — test compilation restored; `compileTestJava` green from clean.
- **N-1** — the date bound is a SQL predicate ahead of the limit (a return to legacy shape, verified
  against the batch-side IDO); the cap warning can no longer be bypassed; tests A–E present and green.
- **N-4** — classified as **toolchain/environment only**. No code change, no dependency change, no JVM
  flag. Resolved by clean daemon state; full suite reproducibly green.
- **H-3 / R-2 / N-3** — three fail-closed guards, a `'6'`-guarded conditional terminal write in its own
  transaction, and a read-only detection sweep that classifies the strand without ever resending.
  All nine required test scenarios covered.
- **Phase 5** — all three companion consumers bounded, ordered and cap-logged.
- **Phase 6** — index script delivered, `NOT EXECUTED`, with one flagged bpchar risk for DBA validation.

**Constraints honoured:** no R-6…R-16 work started · no schema change · no SQL executed · no flag
enabled in any environment · no legacy behaviour weakened to make a test pass · no test silently deleted
(the two behavioural changes are documented in §4.7) · `build.gradle` untouched.

**Not production-ready** — and not claimed to be. S-4/S-5 (external), U-2/U-4/U-5/U-7 and all of
P-1…P-5 remain open. What this pass delivers is that the module now **compiles, tests green, and no
longer has a silent path that loses a claimed payment batch**.

---

*Rev 3 verified against the working tree at 2026-07-29 17:29 ICT (HEAD `98c924f` + 70 modified, 2
deleted and 64 untracked paths). Java 21.0.10 · Gradle 8.14.4. Full suite: 282 suites / 2,317 tests /
0 failures / 0 errors / 20 skipped, exit 0, from clean with a fresh daemon. The tree was modified by a
concurrent session throughout; re-verify line numbers before acting on an individual citation.*
