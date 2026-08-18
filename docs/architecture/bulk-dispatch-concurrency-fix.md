# Bulk Dispatch — H-4 Double-Egress Fix (Atomic Pre-Egress Claim)

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Branch:** `wecms-ui-fixes` (uncommitted)
**Severity:** `P0` — duplicate live-money submission
**Legacy baseline:** `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH1.java`
**Related:** [../testing/integration-test-audit.md](../testing/integration-test-audit.md) §6.1 ·
[payment-dispatch-call-graph.md](payment-dispatch-call-graph.md) ·
[architecture-review.md](architecture-review.md) H-4

---

## 1. Executive summary

The bulk-payment rail could submit the **same file to the bank twice**. Its duplicate guard was a
read-then-check on `TB_PAY_FILE_MST.PROC_STAT` followed, several steps later, by an *unconditional*
write of `'6'`. Two drivers — the auto-receive after-commit hook and a `StrandedRecoveryService` sweep,
or two nodes — could both read `'9'`, both pass the check, both write `'6'` (the same value, so nothing
conflicted), and both go on to SFTP-upload the file and issue EFS `00301`.

The mitigation everyone believed in did not work. The `TB_FIRM_FILE_MST` primary key *is*
deterministic, but inside `buildAndDispatchFile`'s transaction the insert is a plain `save()` with no
intervening query, so Hibernate defers it to commit — and that commit happens **after** the upload and
**after** the 301 call. Two class Javadocs asserted the opposite safety property.

**The decisive legacy finding is an ordering one.** Legacy is *not* structured this way. It
`beginTransaction()` at `:541`, inserts `TB_FIRM_FILE_MST` at `:542-559`, **commits at `:696-697`**, and
only then uploads at `:700` and calls 00301 at `:724`. Because the insert is executed and committed
*before* egress, legacy's deterministic `FIRM_FILE_NM` PK is a genuine pre-egress guard. The refactor
moved the commit boundary to *after* egress and thereby destroyed the only thing making the PK
protective. **This is a legacy-parity defect, not a legacy defect inherited.**

**Fix:** the `'9' → '6'` transition — which legacy already performs via `TB_PAY_FILE_MST_U001` at
`:453-454` — is now a **single conditional UPDATE**, executed in its own short transaction that commits
before anything is built, uploaded or dispatched. The database picks exactly one winner; the loser sees
0 rows and returns before the egress boundary. No new table, no new column, no schema change, no new
scheduler.

Verified by a real two-thread race: **with the claim disabled, 4 of 7 race tests fail; with it, all
pass.**

## 2. Confirmed defect

`BulkReceptionService.receive` (pre-fix), all line numbers from the working tree:

| Step | Line | What |
|---|---|---|
| read the pay-file | `:177` | `findByTranDtAndBizKeyAndFileSeqNo(...)` |
| **check** WAIT | `:182-186` | `if (!ProcStat.WAIT.code().equals(pay.getProcStat())) return;` |
| validate / operating-hours | `:212-262` | may FAIL or SKIP and return |
| **write** `'6'` | `:277-279` | `pay.setProcStat(REQ); pay.setProcDttm(...); payFileMstRepository.save(pay);` — **unconditional** |
| per split file, `REQUIRES_NEW` | `:310` | `runInNewTransaction(... buildAndDispatchFile ...)` |
| insert firm-file MST | `:381` | `firmFileMstRepository.save(mst)` — plain `save`, INSERT deferred |
| insert firm-file DATA × N | `:432` | also deferred |
| **SFTP upload** | `:470` | **egress** |
| 00301 audit row | `:485` | `REQUIRES_NEW` writer |
| **EFS 301** | `:490` | **egress** |
| commit | — | PK violation surfaces *here* |

Two independent failures:

1. **TOCTOU on `PROC_STAT`.** The check (`:182`) and the write (`:279`) are separate statements with
   ~90 lines between them. Both drivers pass, and because both write the *same* value `'6'` there is no
   lost-update conflict to detect.
2. **The PK backstop fires after egress.** Hibernate's `FlushModeType.AUTO` flushes before *query*
   execution, not before `persist`/`merge`. Between `:381` and `:470` there is no query — only more
   `save()` calls — so nothing forces a flush. Verified by inspection of that window.

`receive()` carries **no** `@Transactional` (confirmed: zero occurrences in the file), and both callers
enter with no ambient transaction, so `save(pay)` at `:279` did commit promptly. That made the *state*
durable early but is irrelevant to the race: the decision had already been taken non-atomically.

## 3. Current refactor race timeline (pre-fix)

```
Node A                                  Node B
------                                  ------
read PROC_STAT -> '9'                   read PROC_STAT -> '9'
check WAIT: pass                        check WAIT: pass
validate + operating hours: DISPATCH    validate + operating hours: DISPATCH
save(pay, PROC_STAT='6')  [commits]     save(pay, PROC_STAT='6')  [commits, same value]
BEGIN (REQUIRES_NEW)                    BEGIN (REQUIRES_NEW)
  save(mst)   <- INSERT deferred          save(mst)   <- INSERT deferred
  build fixed-length file                 build fixed-length file
  SFTP upload            *** EGRESS ***   SFTP upload            *** EGRESS ***
  EFS 00301              *** EGRESS ***   EFS 00301              *** EGRESS ***
COMMIT -> succeeds                      COMMIT -> PK violation on TB_FIRM_FILE_MST
                                        (too late: the bank already has two files
                                         and two reception instructions)
```

### Corrected timeline (post-fix)

```
Node A                                  Node B
------                                  ------
read PROC_STAT -> '9'                   read PROC_STAT -> '9'
fast-path check: pass (advisory only)   fast-path check: pass (advisory only)
validate + operating hours: DISPATCH    validate + operating hours: DISPATCH
BEGIN (REQUIRES_NEW, short)             BEGIN (REQUIRES_NEW, short)
  UPDATE ... SET proc_stat='6'            UPDATE ... SET proc_stat='6'
  WHERE ... AND proc_stat='9'             WHERE ... AND proc_stat='9'
  -> 1 row                                -> blocks on A's row lock
COMMIT  (lock released)                   -> re-evaluates: 0 rows
                                        COMMIT
claim WON                               claim LOST
build file                              return  *** NO EGRESS ***
SFTP upload            *** EGRESS ***
EFS 00301              *** EGRESS ***
COMMIT
```

## 4. Legacy JEX evidence

All references `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH1.java`
(1,342 LOC) unless stated. Read end-to-end; no summary was relied on.

| # | Question | Legacy answer | Evidence |
|---|---|---|---|
| 1 | Logical file identity | `"S_" + vanOrgCd + "_" + fileType + "_" + fileSeq + "_" + procDt` | `:540` |
| 2 | Deterministic? | **Yes** — every component derives from the row + config; no timestamp, no random | `:540` |
| 3 | Row inserted before egress? | **Yes** — `TB_FIRM_FILE_MST_C001` with `FIRM_FILE_NM`, `PROC_STEP='0'`, `PROC_STAT='6'` | `:542-559` |
| 4 | **Committed before egress?** | **YES** | **`:696` `idoCon.commit();` `:697` `endTransaction();`** |
| 5 | Atomic update / claim? | No conditional UPDATE. The pay-file `'9'→'6'` write is unconditional | `:453-454` (`TB_PAY_FILE_MST_U001`, `PROC_STAT = PROC_ST_REQ`) |
| 6 | DB locking? | No explicit `SELECT … FOR UPDATE`, no advisory lock | none found |
| 7 | Relies on a unique/PK constraint? | **Yes, effectively** — PK `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM)`; the insert is executed inside the tx and committed before egress | `:559` + `:696` |
| 8 | Explicit duplicate query? | No | — |
| 9 | **SFTP before or after commit?** | **AFTER** | **`:700` `sftpFileUpload(...)`**, commit at `:696` |
| 10 | **EFS 301 before or after commit?** | **AFTER** | `:724` `apiCall(...)` → `ConnectionUtil.connectUrlByPost` `:1221` |
| 11 | SFTP succeeds but commit fails? | Cannot happen in that order — commit precedes upload. A post-upload failure runs `UpdateFirmFileMst(...)` and rethrows | `:742-745` |
| 12 | 301 succeeds but local tx fails? | The 301 result is written by separate statements after the commit; a non-`0000` RC sets the pay-file FAIL | `:726-728`, `:731-741` |
| 13 | Does BATCH2 retry the same file? | Yes — re-polls `(TRAN_DT, PROC_STEP='1', PROC_STAT='6')` every 60 s; retries only on empty body or `RC ∈ {BT01, G999, C999}` | `FILE_DEPOSIT_TRANSFER_BATCH2.java:48-54`, `:201-203`, `:231-233` |
| 14 | Retry reuses the same filename/seq? | Yes — it re-reads the same `TB_FIRM_FILE_MST` row; 00302 references the existing `FIRM_FILE_NM` | `BATCH2:48-54` |
| 15 | Duplicate submission prevented by bank/VAN? | **Not provable from this repository.** No idempotency key in the 301/302 contract | — |
| 16 | Could multiple instances/nodes run concurrently? | Governed by the container-level `JEX_BATCH` flag in `jex.prop`, not per batch; each `.batch` declares only `name`/`batchClass`/`schedule` | `WECMS_BATCH/web/WEB-INF/jex/simpleBatch/WECMS_BATCH/*.batch`; see [../audits/legacy-batch2-ownership-verification.md](../audits/batch/legacy-batch2-ownership-verification.md) §3 |
| 17 | Process- or DB-level lock? | **None found** in the batch source | — |
| 18 | Does `STEP`/`PROC_STAT` act as a claim? | It acts as a *state marker*, written unconditionally. It is not a claim | `:453-454` |

### 5. Legacy concurrency model — classification

Using the required vocabulary:

> **Safe because of unique identity** (primary) **+ safe only because one scheduler instance runs**
> (secondary). **Not** safe because of a DB claim, and **not** inherently safe in the
> read-then-check sense.

The load-bearing detail is ordering, not the constraint alone: legacy executes the identity-bearing
INSERT and **commits it before any irreversible call** (`:559` → `:696` → `:700`). A hypothetical second
concurrent BATCH1 instance would block on the PK index, then fail the insert, and would **never reach
`:700`**. Legacy therefore satisfies the property this fix restores — it simply gets there by a
different mechanism.

### 6. Refactor concurrency model

| Property | Legacy | Refactor |
|---|---|---|
| Trigger | one scheduled poll, `*/1 * * * *`, one instance | **request-time** after-commit hook **+** a recovery sweep |
| Concurrent drivers | effectively 1 | **≥ 2 by design** (`DispatchAutoReceiveRegistrar:65`, `StrandedRecoveryService:158`) |
| Threads | batch thread | request threads + scheduler threads |
| Nodes | single container per env | multi-node capable (ShedLock guards *jobs*, not this request-time path) |

Consequently **bug-for-bug legacy replication would still be unsafe here.** Legacy's implicit
serialization does not exist in the refactor, and the recovery sweep is a second driver legacy never
had. Safety takes priority over copying the operational assumption — which is exactly why the fix adds
an explicit claim rather than only restoring the commit order.

## 7. Legacy vs refactor comparison

| Concern | Legacy JEX | Refactor (pre-fix) | Required | Refactor (post-fix) |
|---|---|---|---|---|
| Logical file identity | `FIRM_FILE_NM` deterministic `:540` | same (unchanged) | deterministic | same, **unchanged** |
| Duplicate lookup | none (relies on PK) | read-then-check `:182` | atomic | conditional UPDATE |
| Atomic claim | ✗ (PK stands in) | ✗ | ✓ | ✓ `claimForReception` |
| DB insert timing | executed in tx `:559` | deferred to commit | — | unchanged |
| **Commit timing** | **before egress `:696`** | **after egress** | before egress | **claim commits before egress** |
| SFTP timing | after commit `:700` | inside tx | after the claim commits | after the claim commits |
| EFS 301 timing | after commit `:724` | inside tx | after the claim commits | after the claim commits |
| Recovery driver | none | sweep, unguarded | shares the claim | shares the claim |
| Multi-node safety | n/a (1 instance) | **unsafe** | safe | safe (DB decides) |
| Failure after upload | `UpdateFirmFileMst` `:742` | `failBoth` in the same tx | no blind resend | unchanged; row stays `'6'`, never re-claimable |
| Retry behaviour | BATCH2 re-poll | inline from 01301 | unchanged | unchanged |
| Alert behaviour | `MsgSendUtil` `:751` | `OpsAlertService` | unchanged | unchanged |

**Defect classification: `legacy parity defect`.** The refactor changed a transaction boundary legacy
relies on, and lost the protection that boundary provided.

## 8. Design alternatives considered

| Option | Assessment | Verdict |
|---|---|---|
| **A — atomic conditional claim on an existing business row** (`TB_PAY_FILE_MST.PROC_STAT '9'→'6'`) | No DDL. Reuses the state legacy already writes (`:453-454`), made conditional. Same key every driver already passes. Mirrors the proven API-rail `claimForDispatch`. Loser path already exists (the `!WAIT` no-op). | **CHOSEN** |
| B — insert a claim/outbox row + force flush before egress | Would work, and is closest to legacy's literal mechanism. But it means relying on a constraint violation as normal control flow — explicitly ruled out by the brief — or adding a new table, which the workspace DB rules forbid (no refactor-only tables). Also needs `DataIntegrityViolationException` constraint-name inspection to distinguish a duplicate from an unrelated violation. | Rejected — larger and less explicit than A |
| C — advisory / pessimistic lock | `SELECT … FOR UPDATE` would have to be held across the SFTP transfer (else it protects nothing), which the brief forbids and which would serialise unrelated tenants behind one slow upload. A PostgreSQL advisory lock adds a concept the codebase does not use anywhere. | Rejected |
| D — transactional outbox / state machine | Correct and more general, but it is a re-architecture of the bulk rail (new intent table, worker, two-phase outcome). Far beyond this defect, and it would re-open the 4→1 batch-architecture decision. | Rejected — disproportionate |

Option A also satisfies every required property, so by the brief's "smallest design" rule it wins.

### Required-property check (Option A)

| Property | How |
|---|---|
| one node wins per logical file | conditional UPDATE; PostgreSQL row lock serialises, loser matches 0 rows |
| claim before SFTP/EFS | claim runs and **commits** in `BulkDispatchClaimTxService` before the split loop |
| loser performs no external call | `receive()` returns immediately on `false` |
| normal flow and recovery share the claim | both call `receive(...)`; the claim is inside it |
| crash after claim recoverable | row sits at `'6'`; `StuckPayFileMonitorService` reports it (K9-approved monitoring + manual re-trigger) |
| crash after upload, before status update | row stays `'6'`, is never re-claimable → **no blind resend** |
| callbacks stay idempotent | untouched (01301/01302 guards unchanged) |
| no cross-tenant claim | `BIZ_KEY` in the predicate — required, since `FILE_SEQ_NO` comes from a global sequence |
| no commit-time exception as duplicate control | explicit affected-row count; PK is now only a backstop |
| no long external call under a row lock | claim commits and releases before the file is even built |
| stranded claims alerted | `StuckPayFileMonitorService` (unchanged) |
| BATCH2 / recovery decisions preserved | no scheduler added, no polling introduced, K9 inline model intact |

## 9. Transaction boundaries

```
receive()                                    [no ambient transaction — verified]
 ├─ read pay-file                            own tx (repository)
 ├─ fast-path WAIT check                     advisory only, explicitly NOT the guard
 ├─ validate / operating hours               own tx per write; may FAIL('3') or SKIP and return
 │                                           SKIP must not claim, so the row stays '9'
 ├─ TX-1  BulkDispatchClaimTxService.claimForReception   REQUIRES_NEW, short
 │          UPDATE ... SET proc_stat='6', proc_dttm=?
 │          WHERE biz_key=? AND tran_dt=? AND file_seq_no=? AND proc_stat='9'
 │        COMMIT  ← the duplicate decision is now durable, and no lock is held onward
 │        0 rows  → return, NO EGRESS
 └─ per split file: TX-2 (REQUIRES_NEW, existing)
          insert TB_FIRM_FILE_MST + TB_FIRM_FILE_DATA
          build fixed-length file
          SFTP upload            ← egress (no pay-file row lock held)
          00301 audit (REQUIRES_NEW)
          EFS 301                ← egress
       COMMIT / on failure failBoth('3') and stop the batch
```

`REQUIRES_NEW` on TX-1 is deliberate: today no caller holds a transaction, so `REQUIRED` would behave
identically — but if one ever does, the claim must still commit on its own *before* the upload rather
than at the end of an outer transaction that also spans the egress.

## 10. State transitions

```
                 ┌─────────────────────────── validation / cut-off FAIL ──────┐
                 │                                                            ▼
  '9' WAIT ──────┼── operating-hours SKIP ──► '9' WAIT (unchanged, retried)   '3' FAIL
                 │
                 └── claimForReception (atomic, 1 row) ──► '6' REQ ──► build/upload/301
                                        │                                │
                       (0 rows: another driver owns it)                  ├─ ok  ──► '6' (01301/01302 advance it)
                                        │                                └─ fail ──► '3' FAIL (failBoth)
                                        ▼
                                   return, NO EGRESS
```

`'6'` is never re-claimable — that is what makes "crash after upload" safe rather than a resend.

## 11. Failure matrix

| Failure point | Row state | Egress already done? | Behaviour | Resend risk |
|---|---|---|---|---|
| before claim (validation / cut-off) | `'3'` | no | FAIL + alert | none |
| operating-hours SKIP | `'9'` | no | untouched, retried later | none |
| claim lost | unchanged (`'6'` by the winner) | no | idempotent no-op | none |
| crash immediately after claim | `'6'` | no | stranded; monitor reports it, manual re-trigger (K9) | none — `'6'` is not claimable |
| SFTP upload fails | `'3'` | no | `failBoth` + batch stops + alert | none |
| crash after upload, before 301 | `'6'` | **file uploaded** | stranded at `'6'`; monitor reports | **none** (would need a manual decision) |
| EFS 301 returns non-`0000` | `'3'` | file uploaded | `failBoth`, no advance | none |
| crash after 301, before commit | `'6'` | **both** | TX-2 rolls back the MST insert; row stays `'6'` | none — never re-claimed |
| later split file fails | `'3'` | earlier files sent | batch stops, earlier files stand committed (existing legacy-parity behaviour) | none |

The pre-existing K9 trade-off is unchanged: an uncertain `'6'` is **detected and alerted**, never
auto-resent. This fix does not widen or narrow that.

## 12. Normal-flow ↔ recovery interaction

`StrandedRecoveryService.recoverPayFiles` (`:158`) re-drives `receive(...)` for pay-files still at
`'9'` older than the grace window (default 30 min). Pre-fix, a file whose SFTP upload was merely slow
was exactly such a row, so the sweep raced the in-flight hand-off — the concrete path that made H-4
reachable. Post-fix both contend on the same claim and the DB picks one. Covered by
`normalFlowRacingRecovery_producesExactlyOneEgress`.

The sweep only scans `'9'`, so once claimed a file is outside its reach entirely — a second layer of
protection, and the reason a crash after claim needs the monitor rather than the sweep.

## 13. Multi-node guarantee

The winner is chosen by a single conditional `UPDATE` inside a committed transaction. PostgreSQL takes a
row lock on the matched row; a concurrent updater blocks until the first transaction commits, then
re-evaluates its `WHERE` against the new row version and matches 0 rows. That is independent of JVM,
thread, ShedLock, and instance count — the property an in-memory lock could not provide.

`BIZ_KEY` is in the predicate, so tenants never contend even though `FILE_SEQ_NO` is drawn from the
global `firm_file_seq_no` sequence and can repeat across tenants.

**Executed-evidence limitation, stated plainly.** PostgreSQL's row-locking behaviour is **not executed**
by the test suite: this project deliberately ships no Testcontainers and no embedded database
(`CLAUDE.md`), and this host has no Docker daemon. The guarantee is therefore covered in two halves —
`DispatchClaimQueryContractTest` proves statically that the SQL is a single conditional `UPDATE`
returning an affected-row count, and `BulkReceptionConcurrentClaimTest` proves with real threads that a
0-row claim yields zero external calls. To close the remaining gap in CI, once a test database exists:

```bash
./gradlew test --tests "*BulkClaimDbConcurrencyTest"   # @DataJpaTest, two tx, assert 1 then 0 rows
```

## 14. Tests added

| Class | Tests | Covers |
|---|---|---|
| `service/BulkReceptionConcurrentClaimTest` | 7 | Real 2-/4-thread races via `CountDownLatch`; **exactly one** SFTP upload and **exactly one** EFS 00301; normal-flow-vs-recovery race; loser does no egress and no firm-file insert; `'6'`/`'0'`/`'3'` not re-claimable; cross-tenant independence (sequential and concurrent). Uses a CAS-backed fake repository (models the conditional UPDATE) and a recording SFTP client — **no network**. |
| `service/BulkDispatchClaimTxServiceTest` | 7 | `firstClaim_succeeds`, `secondClaim_forSameTenantAndLogicalFile_returnsLost`, key passed verbatim, lost claim does no further work; `@Transactional(REQUIRES_NEW)` present and not read-only; method public / class non-final so the **proxy actually applies**; `BulkReceptionService` calls it **across a bean boundary** and carries no `@Transactional` itself (self-invocation would be inert). |
| `repository/DispatchClaimQueryContractTest` | 8 (+4) | Bulk rail added alongside the API rail: `@Modifying`, `int` return, single `UPDATE … WHERE p.procStat='9'` → `'6'`, fully key-qualified, tenant-scoped, no `LIKE`/`%`; plus a shared-shape test so the two rails cannot drift. |
| `service/BulkReceptionReceiveTest`, `BulkReceptionServiceValidationTest` | 3 updated | Now assert the **claim** (once, with the right key) and `never()).save(any())` on the happy path — a regression guard that the old unconditional write does not return. |

### Sensitivity check (the test proves something)

The claim was temporarily disabled in `receive()` and the race suite re-run:

```
BulkReceptionConcurrentClaimTest > two concurrent drivers: exactly one SFTP upload and exactly one EFS 00301 FAILED
BulkReceptionConcurrentClaimTest > four concurrent drivers (two nodes x two paths): still exactly one egress FAILED
BulkReceptionConcurrentClaimTest > normal after-commit hand-off racing a recovery sweep still yields one egress FAILED
BulkReceptionConcurrentClaimTest > the same logical file number under a different tenant is an independent claim FAILED
7 tests completed, 4 failed
```

The experiment was fully reverted (`grep 'TEMP-H4-EXPERIMENT' → no match`).

## 15. Build evidence

See [../testing/integration-test-audit.md](../testing/integration-test-audit.md) §12 for the
consolidated table; summary:

All from `c:/WECMS PROJECT/wecms-api`, JDK 21 (Temurin 21.0.10), Gradle 8.14.4.

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `./gradlew compileJava` | **0** | `BUILD SUCCESSFUL` |
| 2 | `./gradlew compileTestJava` | **0** | `BUILD SUCCESSFUL` |
| 3 | `./gradlew cleanTest test --tests "*BulkReception*" --tests "*Claim*" --tests "*StrandedRecovery*"` | **0** | **74 tests, 0 failures, 0 errors, 0 skipped** (9 classes) |
| 4 | `./gradlew cleanTest test` (full suite) | **0** | **277 classes, 2,248 tests, 20 skipped, 0 failures, 0 errors** |
| 5 | `./gradlew build` | **0** | `BUILD SUCCESSFUL in 4m 15s` (`bootWar`, `war`, `assemble`, `test`, `check`) |
| 6 | race suite re-run ×3 (flake check) | **0** | `BUILD SUCCESSFUL` all three |

Per-class, the three H-4 classes:

| Class | tests | failures | errors | skipped |
|---|---|---|---|---|
| `BulkReceptionConcurrentClaimTest` | 7 | 0 | 0 | 0 |
| `BulkDispatchClaimTxServiceTest` | 7 | 0 | 0 | 0 |
| `DispatchClaimQueryContractTest` | 8 | 0 | 0 | 0 |

Full-suite totals aggregated from all 277 JUnit XML reports in `build/test-results/test/`; **zero** files
contain a `<failure>` or `<error>` element. All 20 skips are the single pre-existing `@Disabled`
`VasAccountIntegrationTest` — unrelated to this change and identical in CI.

`UP-TO-DATE` was not accepted as evidence: every reported test run was preceded by `cleanTest`, and
where a Windows file lock on `build/test-results/test/binary/output.bin` interfered, by `./gradlew
--stop` first. The final full run wrote to the normal `build/test-results/test/` directory (277
reports), not a `test_stale_*` fallback.

**One flake was found and fixed in the new test, not in production.** The first full-suite run failed
`fourConcurrentDrivers_produceExactlyOneEgress` on the `claimAttempts == 4` assertion: with four
threads, some legitimately short-circuited on the *advisory* fast-path check before reaching the claim,
so fewer than four claims were attempted. The egress assertions were unaffected. The fixture now pins
the row read to `WAIT` so every driver is forced through to the claim — making the race deterministic
*and* ensuring the test exercises the guard rather than the fast-path. Re-run three times, stable.

## 15a. Follow-up pass (2026-07-29, same day)

The residual items this document raised were worked in a follow-up pass:

| Item | Outcome |
|---|---|
| §16.1 — transaction across egress | **DECISION REQUIRED (PO).** Legacy order proven conclusively (`:541` begin → `:696` commit → `:700` SFTP → `:724` 301), but splitting it creates an "uploaded but not instructed" state that `PROC_STAT` cannot express (only `'0'/'3'/'6'/'9'` exist, and adding a value needs PO + DBA approval). Deliberately not bundled. Full analysis: [bulk-file-transaction-boundary.md](bulk-file-transaction-boundary.md) |
| §13 — PostgreSQL row-locking not executed | **Still blocked.** No test DB exists in this project and Docker is unavailable on this host. Four costed options escalated: [../testing/payment-dispatch-integration-tests.md](../testing/payment-dispatch-integration-tests.md) |
| H-2 (sibling finding) | **Resolved.** Cross-tenant scanning is legacy-correct and *not* a defect; the unbounded half was real and is fixed on every rail. [payment-dispatch-tenant-isolation.md](payment-dispatch-tenant-isolation.md) |

The H-4 claim itself is unchanged and remains verified.

## 16. Remaining risks

1. **`P2` · TX-2 still spans the egress.** `buildAndDispatchFile` holds one transaction across the MST
   insert, the SFTP upload and the 301 call. Legacy commits first (`:696`) and uploads after (`:700`).
   The claim makes this no longer a *duplicate* risk, and the locks held are on freshly-inserted rows
   nobody contends for — but it is a real remaining legacy-parity divergence and it keeps a DB
   transaction open for the duration of a network transfer. Splitting it changes `failBoth`'s semantics
   (FAIL would need its own transaction, as legacy does at `:727`/`:743`), so it is deliberately **not**
   bundled into this P0 fix. Tracked separately.
2. **`P2` · No VAN-side idempotency key.** Item 15 above: nothing in the 301/302 contract prevents the
   bank from accepting a genuine duplicate if one ever were sent. Our protection is entirely local.
3. **`P3` · Crash between claim and upload** leaves a `'6'` row that only the monitor reports. This is
   the K9-approved model (monitoring + manual re-trigger), not a regression, but it remains an
   operational obligation.
4. **PostgreSQL row-locking is not executed in tests** (§13), pending a test database.

## 17. Deployment considerations

- **No DDL, no migration, no seed.** `PROC_STAT` and the pay-file PK already exist; only a JPQL
  `@Modifying` query was added.
- **No configuration change.** No new property, no flag, no cron. Every dispatch flag remains `false`
  and every cron `-` in all four profiles.
- **Rollout is order-independent.** The claim is backward-compatible with rows in any state: a `'9'` row
  is claimable, anything else is not. A mixed-version cluster is safe — an old node would still race,
  but a new node cannot be the second sender, and the guard strictly reduces exposure.
- **Legacy coexistence unchanged.** A live legacy BATCH1 polling the same DB would take the same
  `'9'→'6'` transition; it writes it unconditionally, so a legacy instance could still race a refactor
  node. That is the pre-existing coexistence question, not something this fix alters — and it is one
  more reason the dispatch flags stay off pending sign-off.

## 18. Safety statement

- **No commit, no push.** All changes are unstaged working-tree edits.
- **No SQL executed** against DEV/SIT/UAT/PROD or any shared database. The legacy evidence came from
  reading `WECMS_BATCH DEV` source and IDO XML; the schema facts came from committed migrations.
- **No live external call.** The concurrency tests use a recording in-memory SFTP fake and an overridden
  `issueReception`; no EFS, VAN, bank, SFTP or callback endpoint was contacted.
- **No scheduler, recovery, realtime-dispatch, EFS or SFTP flag enabled.** None touched.
- **No deployment flag changed**; no `.env*`, no `application*.yml`, no `build.gradle`.
- **No secret exposed** — no API key, `EV`, `VV`, credential, account number or customer datum appears
  in any added file or in this document.
- **No Batch-2-style scheduler reintroduced**; no polling job added. The 4→1 architecture is intact.

## 19. Post-fix batch-mapping verification (2026-07-29)

The concurrency fix was re-audited against the approved legacy→refactor batch mapping to confirm it did
not shift any mapping. **It did not.** The claim is a conditional UPDATE on an existing business column
inside the existing `receive()` entry point — it adds no scheduler, no cron, no property, no polling
loop, and no new entry point, so every trigger model is byte-for-byte the one that was approved.

| Legacy batch | Legacy responsibility | Refactor replacement | Trigger model | Scheduled? | External submission? | Verification |
|---|---|---|---|---:|---:|---|
| `NAPAS_REAL_TIME_BATCH` | Realtime payment dispatch | `RealtimeTransferService` | Event-driven | No | Yes | [RealtimeDispatchRegistrar.java:63-66](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchRegistrar.java#L63-L66) → [RealtimeDispatchService.java:112](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L112); claim [:221](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L221); EFS 202 [:269-270](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L269-L270) — **untouched by this fix** |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | Build and submit bulk files | `BulkReceptionService` | Request-time / after-commit | No | Yes | [DispatchAutoReceiveRegistrar.java:63-65](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/DispatchAutoReceiveRegistrar.java#L63-L65) → [BulkReceptionService.java:198](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L198); **claim [:319](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L319) commits before** SFTP [:641](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L641) and EFS 00301 [:655](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L655) — **the only mapping this fix changes internally; its trigger model is unchanged** |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | Continue processing after reception | Inline 00302 after successful 01301 | Callback-driven | No | Yes | [BulkReceptionResultService.java:234](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L234) → [:274](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L274) → EFS 00302 [:292](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L292); guards [:149-158](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L149-L158) + [:275-280](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L275-L280) — **untouched by this fix** |
| `PROCESSING_RESULT_CHECK_BATCH` | Poll pending transaction results | `ResultCheckJob` | Scheduled | Yes | No new transfer | [ResultCheckJob.java:99](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L99) (cron `-`, `Asia/Ho_Chi_Minh`, ShedLock [:100-101](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L100-L101)); inquiry-only — [ResultCheckService.java:172](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/ResultCheckService.java#L172) is its sole gateway call — **untouched by this fix** |

**Only `ResultCheckJob` remains as the scheduled legacy payment-batch replacement.**

The fix adds **no** scheduled money-submission job. Regression checks all pass: no scheduled replacement
for `NAPAS_REAL_TIME_BATCH`; no polling replacement for `FILE_DEPOSIT_TRANSFER_BATCH1`; no active
scheduled replacement for `FILE_DEPOSIT_TRANSFER_BATCH2`; exactly one retained scheduled
result-processing batch; recovery shares the normal path's claim rather than bypassing it
([StrandedRecoveryService.java:197](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/StrandedRecoveryService.java#L197));
no second worker can trigger 00302 for a file alongside 01301; every scheduler is disabled by default.

`StrandedRecoveryJob` is a **refactor safety mechanism, not a retained legacy batch**, and stays
independently double-gated (cron `-` **and** `enabled=false`).

Full call graph, the complete 14-entry `@Scheduled` inventory with per-job capability classification,
and the test table: [payment-dispatch-call-graph.md](payment-dispatch-call-graph.md) §6. Executable
proof: [`LegacyBatchMappingVerificationTest`](../../src/test/java/com/kosign/wecms/config/LegacyBatchMappingVerificationTest.java)
(8 tests, all green).
