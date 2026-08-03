# Payment Dispatch — Integration-Test Position & Infrastructure Decision

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`)
**Status:** `BLOCKED — infrastructure decision required (PO)`. This is the one **P1** item that could not be closed.

---

## 1. The position, stated plainly

There are **zero executing integration tests in this repository** — not in dispatch, not anywhere.

| Evidence | Value |
|---|---|
| Real `@SpringBootTest` annotations repo-wide | **1** (`VasAccountIntegrationTest`) |
| …of which class-level `@Disabled` | **1** — *"F1 — pending shared integration-test infrastructure (Testcontainers + JWT mock)"* |
| Real `@DataJpaTest` / `@JdbcTest` / `@Testcontainers` | **0 / 0 / 0** |
| `src/test/resources` | **does not exist** — no test YAML, no test profile |
| Test-DB dependency in `build.gradle` | **none** (no Testcontainers, no H2, no embedded Postgres). `org.postgresql:postgresql` is `runtimeOnly` on the **main** classpath only |
| Dedicated integration source set / task | **none** |
| Docker daemon on this host | **absent** (`dockerDesktopLinuxEngine` pipe not found) |

So the 20 skipped tests in every suite run are that one disabled class. Nothing is skipped *because of*
this host — the skip is committed, and CI skips identically.

## 2. Why this was not silently fixed

The brief's constraint and the project's own rule point the same way:

> `CLAUDE.md` — *"**Not currently in `build.gradle`** (do not assume they are): SpringDoc OpenAPI,
> Testcontainers. Add them in the sprint where the first feature actually needs them."*
> and *"Don't add SpringDoc / Testcontainers / Redis / any other dependency 'just in case'."*

That is permission-with-conditions, not a prohibition — but adding a test-infrastructure dependency
changes the build for every developer and every CI run, and it cannot be validated here because Docker
is unavailable. Adding an unexecutable dependency plus test classes that no one has ever seen pass
would produce **the appearance of coverage with none of the assurance** — precisely what the brief
forbids ("do not call the item complete unless CI evidence is produced").

So the decision is escalated rather than taken.

## 3. What the coverage gap actually is

Three junctions cannot be verified by a Mockito test of either side. Each is currently asserted
*through mocks on both sides*, which verifies the contract **as written** and cannot catch the contract
being wrong.

| Junction | Current best evidence | What is unverifiable without a DB |
|---|---|---|
| **Atomic claim** (`claimForDispatch`, `claimForReception`) | `DispatchClaimQueryContractTest` (8) proves the SQL *is* a single conditional `UPDATE … WHERE procStat='9'` returning `int`; `BulkReceptionConcurrentClaimTest` (7) proves under **real threads** that a 0-row claim yields **zero** egress, using a CAS-backed fake that models the conditional update | PostgreSQL row locking itself: that a second transaction **blocks** on the first and then genuinely matches 0 rows |
| **Tenant isolation** | `DispatchScanTenantScopeContractTest` (8) proves every mutation carries `bizKey` and is fully key-qualified | that tenant A's sweep cannot *read* or *write* tenant B's row in a live schema |
| **`REQUIRES_NEW` / after-commit** | `BulkDispatchClaimTxServiceTest` (7) proves the annotation, the propagation, public/non-final proxyability, and that the call crosses a bean boundary; `*RegistrarTest` proves the hook is registered | that the claim transaction actually **commits independently**, that an outer rollback does not undo it, and that `afterCommit` fires on real commit and not on rollback |

The two halves genuinely complement each other — static SQL shape plus real-thread service behaviour —
but neither is a substitute for executing the engine.

## 4. Options for the PO

| Option | Cost | Fidelity | Notes |
|---|---|---|---|
| **A — Testcontainers PostgreSQL** *(recommended)* | one `testImplementation` dependency set + Docker in CI | **full** — real locking, real SQL, real `bpchar`/`@JdbcTypeCode` mapping | The path the existing `@Disabled` test already names. Requires Docker on every dev machine and CI runner. |
| **B — External PostgreSQL test profile** | CI service container + `application-integrationtest.yml` + a dedicated source set | full | No Docker needed locally; tests skip when the env var is absent. Needs a throwaway schema — must **never** point at the shared dev DB. |
| **C — Embedded PostgreSQL** (`zonkyio/embedded-postgres`) | one dependency, no Docker | full SQL + locking | Heavier startup; another dependency to own. |
| **D — H2** | one dependency | **inadequate** | Rejected. The claim's correctness depends on PostgreSQL row-lock-then-re-evaluate semantics, and the schema uses `bpchar` + `@JdbcTypeCode(SqlTypes.CHAR)`. H2 would pass while proving nothing about the real engine — the brief explicitly warns against this substitution. |
| **E — Stay as-is** | none | none | Honest but leaves the three junctions unverified on a money path. |

**Recommendation: A**, with the source set from B so the suite stays green where Docker is absent.

## 5. Exact commands, once infrastructure exists

Nothing below runs today; it is the specification to implement.

```bash
# Option A — Testcontainers (requires a running Docker daemon)
./gradlew integrationTest

# Option B — external PostgreSQL (no Docker)
export WECMS_IT_DB_URL=jdbc:postgresql://localhost:5432/wecms_it
export WECMS_IT_DB_USER=wecms_it
export WECMS_IT_DB_PASSWORD=***          # never committed; CI secret
./gradlew integrationTest -Dspring.profiles.active=integrationtest
```

Required build additions (Option A + B source set):

```groovy
sourceSets { integrationTest { compileClasspath += sourceSets.main.output + sourceSets.test.output
                               runtimeClasspath += sourceSets.main.output + sourceSets.test.output } }
dependencies {
    integrationTestImplementation 'org.springframework.boot:spring-boot-starter-test'
    integrationTestImplementation 'org.testcontainers:junit-jupiter'
    integrationTestImplementation 'org.testcontainers:postgresql'
    integrationTestRuntimeOnly    'org.postgresql:postgresql'
}
tasks.register('integrationTest', Test) {
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    // Never part of `check` — `./gradlew build` must stay green without Docker.
}
```

The schema must come from the committed migrations (`V95`/`V96`/`V107`/`V109`…), **not** from
`ddl-auto: create`, so the tests exercise the real `bpchar`/`NOT NULL` shape the entities validate against.

## 6. Test specification (to implement under `integrationTest`)

### A · Atomic claim + tenant isolation — `BulkClaimDbConcurrencyIT`

```text
firstClaim_succeeds                                   → affected rows = 1
secondClaim_inSeparateTransaction_returnsZero         → blocks on the row lock, then 0
sameFileSeqNo_differentBizKey_bothClaimIndependently  → 1 and 1
claimCannotMoveARowFromSixOrZeroOrThree               → 0 for each terminal state
tenantA_sweepCannotUpdateTenantBRow                   → claim with A's bizKey leaves B's row untouched
twoConcurrentTransactions_produceExactlyOneWinner     → 2 threads, 2 real tx, exactly one 1
```

Mechanism: two `TransactionTemplate`s on separate threads, a `CountDownLatch` between the first
`UPDATE` and its commit, asserting the second blocks and then returns 0.

### B · After-commit hand-off — `DispatchAfterCommitIT`

```text
businessTransactionCommits_thenBulkReceptionIsInvoked
businessTransactionRollsBack_thenNoReception_noSftp_noEfs
```

Real `PlatformTransactionManager`; `SftpClient`/`EfsApiClient` replaced by recording fakes.

### C · `REQUIRES_NEW` — `DispatchClaimTransactionIT`

```text
claimCommitsIndependentlyOfTheCaller
outerRollback_doesNotUnclaimTheFile      ← the property the H-4 fix depends on
failStrandedPayFile_commitsFromInsideACatchBlock
selfInvocation_wouldBypassTheProxy       (negative control)
```

### D · Callback → inline execution — `BulkCallbackInlineExecutionIT`

```text
successful01301_persistsState_thenIssues00302Inline
duplicate01301_doesNotIssueASecond00302
```

### E · Result check — `ResultCheckIT`

```text
eligiblePendingRow_isUpdatedFromTheInquiryResult
resultCheck_neverSubmitsANewTransfer     ← assert only EFS_203 is ever sent
```

(E's "never submits a transfer" property is already provable statically — `ResultCheckService`
references `EFS_203` only, no `EFS_202`/`301`/`302` — and that is asserted in the batch-mapping section
of the integration-test audit.)

## 7. Status

```text
BLOCKED — PO decision on Option A / B / C, then implement §5–§6.
```

This is the remaining **P1**. Everything achievable without a database has been done and is green; the
residue is genuinely infrastructure, not analysis.
