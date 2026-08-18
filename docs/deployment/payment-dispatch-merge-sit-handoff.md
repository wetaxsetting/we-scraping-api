# Payment Dispatch — Merge / SIT Handoff Report

**Scope:** Architecture Review **Revision 2** remediation (N-1, N-2, N-4, H-3/R-2/N-3 + Phases 5–6),
its Revision 4 independent verification, and the Revision 5 stable-tree release-evidence pass.

| | |
|---|---|
| **Prepared** | 2026-07-30 · branch `wecms-ui-fixes` · HEAD `98c924fde6cbece4dd2cfbea228c4539247c251c` |
| **Verdict of record** | `RELEASE EVIDENCE VERIFIED` (Rev 5) |
| **Recommendation** | **READY FOR DEVELOPER REVIEW** — see §10 for why this is not yet `READY FOR SIT` |
| **Source of truth** | [architecture-review.md](../architecture/architecture-review.md) Rev 5 |

> **Nothing is enabled.** Every dispatch, recovery, monitor, callback, EFS and SFTP flag remains
> `false` / `-` in all environments. **No SQL was executed** against any database. This handoff is a
> review package, not a deployment.

---

## 1. Changed production files

### 1a. Changed by this remediation (8 files)

| File | Finding | Change |
|---|---|---|
| [`dispatch/repository/TbFirmTranRepository.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/repository/TbFirmTranRepository.java) | **N-1** | `findResultCheckCandidates` takes `tranDts` and applies the date bound **as a SQL predicate ahead of the `LIMIT`** — a return to the legacy batch-side IDO shape. Prevents permanent candidate starvation by stale `202`/`'6'` rows. |
| [`dispatch/service/ResultCheckService.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/ResultCheckService.java) | **N-1** | `eligibleTranDts(utcToday)` → `[today, yesterday]` UTC (deliberate widening past legacy's `< '235800'` guard); `capReached` is evaluated **before** the `aged.isEmpty()` return so the cap warning can no longer be bypassed; three distinct log outcomes. |
| [`dispatch/repository/TbPayFileMstRepository.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/repository/TbPayFileMstRepository.java) | **H-3 / Phase 5** | `findStrandedPayFiles(procStat, cutoff, Pageable)` — bounded, oldest-first, serves both `'9'` and `'6'`. New `failClaimedPayFile(...)` — conditional terminal write guarded `AND p.procStat = '6'`, returning the affected-row count. |
| [`dispatch/service/BulkDispatchClaimTxService.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkDispatchClaimTxService.java) | **H-3** ⚠️ shared | `failStrandedPayFile(...)` added in `REQUIRES_NEW`, so the failure is recorded from outside the rolled-back per-file transaction. *File also carries H-4 claim work from a parallel session.* |
| [`dispatch/service/BulkReceptionService.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java) | **H-3 / V-1** ⚠️ shared | Three fail-closed `catch (RuntimeException)` guards (outer post-claim, pre-egress build, EFS leg) that mark the pay-file FAIL and alert instead of leaving a committed `'6'`. `AtomicInteger dispatchedFiles` out-parameter so the alert reports the **true** partial-dispatch count. `RS_MSG` capped at 200 chars and carries the exception **type only**, never `getMessage()`. |
| [`dispatch/monitor/StuckPayFileCause.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckPayFileCause.java) | **N-3** | Three new causes: `CLAIMED_NO_FIRM_FILE`, `RECEPTION_UNCONFIRMED`, `AWAITING_EXECUTION_RESULT`. |
| [`dispatch/monitor/StuckPayFileReport.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckPayFileReport.java) | **N-3** | `claimedStuckCount` added. |
| [`dispatch/monitor/StuckPayFileMonitorService.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckPayFileMonitorService.java) | **N-3** | Third **read-only** sweep over stranded `'6'` rows + `classifyClaimedPayFile`. Detection only — it never re-drives a payment. |

**Deleted (dead code, Rev 2):**
`dispatch/service/TransferDispatchExecutor.java` and its test `TransferDispatchExecutorRealtimeTest.java`.

**Not changed:** `build.gradle`, any `application-*.yml`, any entity mapping, any DB schema. N-4 was
diagnosed as **toolchain-only** (Gradle daemon contention) and fixed by `--stop` + a clean daemon — no
dependency pin, no JVM flag.

### 1b. Changed by parallel workstreams — require separate review

The working tree carries **147 changed paths**, the large majority from concurrent sessions running
batch / HAENGSUNG / scheduler / callback-hardening work that is **outside this remediation**. They were
present in the verified tree and are covered by the green suite, but their correctness was **not**
audited here. See §9.

---

## 2. Changed test files

### 2a. Added by this remediation

| File | Tests | Covers |
|---|--:|---|
| [`dispatch/service/ResultCheckStarvationTest.java`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/service/ResultCheckStarvationTest.java) | 9 | Scenarios A1, A2, B, C, C2, D, D2, E, E2 — stale-row starvation, the date predicate, cap-reached warning, and the idle/no-work log split. |
| [`dispatch/service/BulkReceptionStrandedFailureTest.java`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionStrandedFailureTest.java) | 11 | All nine required H-3 scenarios plus 9b (**true** partial-dispatch count) and the `RS_MSG` cap/redaction. |

### 2b. Modified by this remediation

| File | Reason |
|---|---|
| [`dispatch/repository/DispatchScanTenantScopeContractTest.java`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/repository/DispatchScanTenantScopeContractTest.java) | **V-4** — `everyMutationIsStateGuarded` was matching the `SET` clause and stayed green against a deleted guard. Now extracts the `WHERE` clause and asserts `procStat = '[0369]'` against that alone. |
| [`dispatch/service/BulkReceptionServiceTest.java`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionServiceTest.java) | **N-2** — a `null` constructor argument replaced with a real `@Mock BulkDispatchClaimTxService`. (The three 21-arg constructor sites were repaired by the parallel session.) |

**No test was deleted or weakened to make the suite pass.** The two behavioural test changes are
documented in review §4.7.

---

## 3. Documentation and SQL deliverables

### Documentation

| Artifact | State |
|---|---|
| [`docs/architecture/architecture-review.md`](../architecture/architecture-review.md) | **Revision 5** — §V (V-1…V-5 verification findings), §0–§8 (Rev 3 remediation detail), §5-bis (Rev 5 release evidence, mutation table, residue proof), §7 (SIT/UAT/PROD blockers), §8 (final status). |
| This file | Merge / SIT handoff. |

Roughly 35 further untracked documents under `docs/architecture/`, `docs/audits/`, `docs/deployment/`,
`docs/features/wecms-batch/` and `docs/testing/` belong to the parallel batch workstream — listed in §9.

### SQL

| Script | Status |
|---|---|
| [`docs/sql/2026/add-dispatch-sweep-indexes-2026-07-29.sql`](../sql/2026/add-dispatch-sweep-indexes-2026-07-29.sql) | **NOT EXECUTED** — see §6. |

No Flyway migration was added. Flyway stays disabled and `ddl-auto` stays `validate`, per
`project_flyway_disabled_ddl_validate.md`.

---

## 4. Final build and test evidence

Captured 2026-07-30 10:09–10:35 ICT from an exclusively-held, quiet tree with `build/` deleted and
**no Gradle daemon**.

```
Java   : openjdk 21.0.10 2026-01-20 LTS (Temurin-21.0.10+7-LTS)
Gradle : 8.14.4
Branch : wecms-ui-fixes   HEAD: 98c924fde6cbece4dd2cfbea228c4539247c251c
Tree   : 74 modified · 2 deleted · 71 untracked   (unchanged start to finish)

$ ./gradlew --stop                                          → 0 daemons live
$ find build -mindepth 1 -delete; rmdir build               → build/ REMOVED
$ ./gradlew --no-daemon clean compileJava compileTestJava   → exit 0   (116 s, 0 errors)
$ ./gradlew --no-daemon cleanTest test                      → exit 0   (67 s)
```

| Run | Suites | Tests | Passed | Failed | Errors | Skipped |
|---|--:|--:|--:|--:|--:|--:|
| Full suite | **291** | **2,438** | **2,418** | **0** | **0** | **20** |
| Focused (10 named suites) | 10 | 135 | 135 | 0 | 0 | 0 |
| Dispatch package, post-restore | 57 | 595 | 595 | 0 | 0 | 0 |

**Mutation testing — 6 of 6 groups caught**, each applied alone and restored byte-identically:

| # | Mutation | Caught by | Failures |
|---|---|---|--:|
| M1 | drop `tranDt IN :tranDts` | `ResultCheckStarvationTest` C | 1 |
| M2 | force `capReached = false` | `ResultCheckStarvationTest` D | 1 |
| M3 | remove each of the 3 `catch` guards | `BulkReceptionStrandedFailureTest` | 4 / 2 / 4 |
| M4 | force dispatched count to zero | `BulkReceptionStrandedFailureTest` 9b | 2 |
| M5 | drop `AND procStat='6'` from `failClaimedPayFile` | `DispatchScanTenantScopeContractTest` | 2 |
| M6 | drop the H-4 claim guard, **both** rails | `DispatchClaimQueryContractTest` | 2 + 2 |

**Residue.** Tree fingerprint (md5 of the sorted md5sums of 2,550 `.java`/`.sql`/`.md`/`.yml`/
`.properties` files under `src/`, `docs/`, `Claude Code (AI)/sql/`) was `d1a81aec51b439cf234f2cff3c80ad5c`
at 10:09:01, unchanged at 10:11:46, and **still unchanged at 10:33:19** after all nine mutations. No
`MUTATION` marker survives anywhere under `src/`.

---

## 5. Configuration required before SIT

**All of the following are currently OFF and must stay OFF until each owner signs off.** Reference:
[`batch-dispatch-variable-reference.md`](batch-dispatch-variable-reference.md), [`.env.sit.example`](../../.env.sit.example).

### 5a. Must be supplied by Infra / VAN Center before anything is enabled

| Variable | Current | Required for SIT | Owner |
|---|---|---|---|
| `WECMS_CALLBACK_ALLOWED_IPS` | *(empty)* | VAN gateway source IPs. **With `protection=ENFORCE` an empty list 403s every callback** — correct, but it presents as an outage. | VAN Center → Ops |
| `WECMS_CALLBACK_PROTECTION` | `ENFORCE` | Confirm deliberately, per environment. | Ops |
| `WECMS_CALLBACK_TRUSTED_PROXIES` | *(empty)* | Set if any reverse proxy fronts the callback path. | Ops |
| `WECMS_CALLBACK_TRUST_FORWARDED_FOR` | `true` | Confirm deliberately — `true` without trusted proxies is spoofable. | Ops |
| `WECMS_EFS_BASE_URL` | `https://dev-van-gw.webcashvietnam.com` | SIT gateway URL. | Ops |
| `WECMS_SFTP_HOST` / `_USERNAME` / `_KEY_PATH` / `_PASSWORD` | *(empty / placeholder)* | SIT SFTP credentials, key on disk at `/opt/wecms/secrets/`. | Infra |
| `WECMS_SFTP_LOCAL_UPLOAD` / `_LOCAL_DOWNLOAD` | *(empty)* | Absolute paths, created and writable. | Infra |
| `WECMS_DISPATCH_ALERT_ENV_MODE` / `_WAS_NO` | `T` / `01` | Confirm per environment. | Ops |

### 5b. Feature flags — must remain `false` / `-` until PO authorises

```
WECMS_DISPATCH_AUTO_RECEIVE=false
WECMS_DISPATCH_REALTIME_DISPATCH=false
WECMS_DISPATCH_RECIPIENT_INQUIRY=false
WECMS_DISPATCH_RESULT_CHECK_CRON=-
WECMS_DISPATCH_RECOVERY_ENABLED=false
WECMS_DISPATCH_RECOVERY_CRON=-
WECMS_DISPATCH_STUCK_MONITOR_ENABLED=false
WECMS_DISPATCH_STUCK_MONITOR_CRON=-
WECMS_DISPATCH_ALERT_ENABLED=false
WECMS_EFS_ENABLED=false
WECMS_SFTP_ENABLED=false
```

### 5c. Tunables — review before first enablement

| Variable | Default | Note |
|---|--:|---|
| `WECMS_DISPATCH_RESULT_CHECK_BATCH_SIZE` | 100 | The N-1 cap. Cap-reached now emits a WARN. |
| `WECMS_DISPATCH_STUCK_MONITOR_FIRM_FILE_BATCH_SIZE` | 200 | |
| `WECMS_DISPATCH_RECOVERY_GRACE_MINUTES` | 30 | Rows younger than this are never treated as stranded. |

### 5d. CI

CI must run the suite **from a clean daemon** and must never launch two Gradle invocations
concurrently against one tree. Both of the "transient" failure classes seen during this work traced to
exactly that (blocker S-6).

---

## 6. SQL scripts marked `NOT EXECUTED`

| Script | Contents | Status |
|---|---|---|
| `docs/sql/2026/add-dispatch-sweep-indexes-2026-07-29.sql` | Three supporting indexes for the bounded sweeps introduced by H-2 / N-1 / H-3 / Phase 5, plus an `EXPLAIN` validation section. | **NOT EXECUTED** — banner present at two points in the file. Authored read-only; not run against DEV, SIT, UAT or PROD. |

Execution is a **manual DBA action** per root `CLAUDE.md` §"2. Manual DDL execution policy". Run the
`EXPLAIN` validation in §3 of the script first and drop any index the planner does not use.

> **Correction of record:** Revision 3 warned of a bpchar/varchar index-type hazard on these columns.
> Revision 4 finding **V-3 withdrew that warning** — `@JdbcTypeCode(SqlTypes.CHAR)` is already applied
> to every affected column, so the types align. The withdrawal is recorded in §3 of the script itself.

---

## 7. Remaining operational blockers

### SIT

| # | Blocker | Owner | Status |
|---|---|---|:--:|
| S-1…S-3 | Test compilation (N-2), 97 failures (N-4), starvation (N-1) | Dev | ✅ closed |
| S-4 | **Supply the VAN gateway source IPs** (see §5a) | VAN Center → Ops | ⏳ external |
| S-5 | Confirm `callback.protection` + `trusted-proxies` per environment | Ops | ⏳ |
| S-6 | CI: clean daemon, never two concurrent Gradle invocations on one tree | Dev / CI | ⏳ |
| S-7 | **Recognised risk — first domestic VND 202 may be rejected for a missing FX block.** See below. Not a defect; a *diagnosis shortcut* | Dev + VAN Center | ⏳ watch |

#### S-7 — first domestic VND 202: read this before debugging (EFS guide F-4)

**If the very first domestic VND real-time transfer in SIT is rejected by the gateway with a
missing/required-field error, check this first — it is a five-second diagnosis and otherwise looks
like a serialization bug.**

REST API guide v1.2.6 **§5.4** prints all eight FX fields of the 202 request as **`Mondatory`** with
**no conditional qualifier**:

```
withd_curren_cd  String(3)   Mondatory  출금통화코드: USD 고정
dep_curren_cd    String(3)   Mondatory  입금통화코드 VND
apl_exc_rate_itg Number(22)  Mondatory  적용환율 정수
apl_exc_rate_dcm Number(3)   Mondatory  적용환율 소수
withd_amt_itg    Number(22)  Mondatory  출금금액 정수
withd_amt_dcm    Number(3)   Option     출금금액 소수
dep_amt_itg      Number(22)  Mondatory  입금금액 정수
dep_amt_dcm      Number(3)   Option     입금금액 소수
```

We populate this block **only** on the Woori (`970457`) / non-VND branch, fed from the §5.6 **204**
FX-rate inquiry, and omit it entirely for domestic VND — matching legacy `NAPAS_REAL_TIME_BATCH`,
which has run this way in production for years.

**Why we believe the table is mis-marked, not us:** `withd_curren_cd` is documented as *USD 고정*
("fixed USD"), which cannot apply to a domestic VND transfer; the only source of the rate/amount
values is the **204**, which §5.6 states is *우리은행 전용* ("Woori-exclusive"); and the whole block was
appended in the **2026-02-11** revision, which reads like a column that gained fields without gaining
a conditional marker.

**If the rejection happens:** it is **not** a DTO or Jackson problem. Escalate to Mr. Kang as
question 4 in [../audits/efs-guide-1.2.6-open-questions-closure.md](../audits/governance/efs-guide-1.2.6-open-questions-closure.md) §10 —
"confirm the §5.4 FX block is conditional on the Woori FX path and may be omitted for domestic VND".
Do **not** start sending a zero/empty FX block on domestic transfers to make the error go away: a
fabricated `apl_exc_rate` on a money-moving request is far worse than a rejected one.

### UAT

| # | Blocker | Owner |
|---|---|---|
| U-1 | ~~R-2 / H-3 fail-closed + `'6'` visibility~~ | ✅ closed |
| U-2 | **Apply the index script** with its `EXPLAIN` validation | DBA |
| U-3 | ~~Bound the companion finders~~ | ✅ closed |
| U-4 | `nextFileRound` `MAX+1` race (R-4 remainder) — funnel through `wb_get_next_val` | Dev |
| U-5 | **L-2** — `RECEPTION_SUCCESS_CD` set (`TODO(B5)`) and the `EfsCryptoService` URL-encode pass, both against a **captured gateway sample** | Dev + VAN Center |
| U-6 | Confirm the DB target so the coexistence gate on `recovery.enabled` can be reasoned about | Ops |
| U-7 | **Run-book for the three new monitor causes.** The monitor reports `CLAIMED_NO_FIRM_FILE`, `RECEPTION_UNCONFIRMED`, `AWAITING_EXECUTION_RESULT`; **two of the three must not be re-driven blind** and no procedure exists yet | PO + Ops |

### PROD

| # | Blocker | Owner |
|---|---|---|
| P-1 | **R-13** — replace `StrictHostKeyChecking=no` with a real `known_hosts` | Dev + Infra |
| P-2 | **R-12** — durable failure record + change `OpsAlertAsyncConfig`'s `DiscardPolicy`; a dropped alert can be the only trace of a failed hand-off | Dev |
| P-3 | **R-10 / R-11** — batching and off-thread hand-off, validated by a load test | Dev |
| P-4 | Multi-instance decision — the monitor de-dup, JWT revocation and rate-limit stores are all in-memory. **Decision is PO + Ops; the resulting store is Dev work** | PO + Ops, **then Dev** |
| P-5 | Pre-existing gate **R-02** (action-level authz) — **Dev code work**, see the scoping note below — plus secret rotation (Ops) | **Dev** + Ops |

> **Scoping note added 2026-08-01 — "the dispatch subsystem is code-complete" is true; "there is no code
> work left" is not.** The dispatch subsystem (bulk + real-time rails, the three firm-file sweeps at
> `PROC_STEP='0'/'1'/'2'`, the pay-target `'9'` sweep, C-6's paced drain) has no open code item, and the
> SIT and UAT sections above are genuinely verification, provisioning and reconciliation. **Do not
> generalise that to the deploy as a whole.** Two PROD rows in this table are code:
>
> - **P-5 / R-02** is **not** a verification task. Closing it means (a) a PO decision on the per-action
>   authority source for Request-approval / Delete / Dispatch, then (b) server-side `@PreAuthorize` on
>   every state-changing handler, then (c) replacing the frontend's static `{R:true,D:true,T:true}` stub
>   ([`ScheduledTransferPage.tsx:75-77`](../../../wecms-web/components/payment/internal-transfer/ScheduledTransferPage.tsx#L75-L77)) with the real contract. Today only **menu-level**
>   entitlement is enforced — `@menuAuthz` answers "may you open this screen", all-or-nothing, and the
>   five action rights have no backend contract at all. Full analysis:
>   [`../../../docs/audits/action-level-authorization-audit.md`](../../../docs/audits/action-level-authorization-audit.md).
> - **P-4** is **also code**, and is understated as a "multi-instance decision". `TokenRevocationService`'s
>   per-user not-before cutoff — written on admin **lock / delete / demote / password-reset** — lands on
>   one JVM only, so on every other node the affected user keeps a **working privileged token** until
>   natural expiry (≤ 1 h access-token TTL). That is the exact scenario F-03 exists to prevent, it fails
>   **open**, and nothing logs it. Instance count is an unanswered `[?]` OPS item
>   ([`wecms-batch-infra-team-checklist.md:74-75`](wecms-batch-infra-team-checklist.md)), so "probably one
>   node" is not a control. The decision is PO + Ops; the store is Dev work. **If only one of revocation
>   and rate limiting is fixed, fix revocation** — rate limiting degrades gracefully behind the durable
>   `LoginLockoutService`, revocation has no second line. Full statement:
>   [`payment-dispatch-final-readiness.md` §4.4](payment-dispatch-final-readiness.md).
>
> Owners in this table are corrected accordingly. The remaining non-code items are B2 (TEST-key
> verification), B4 provisioning, one bulk file end-to-end through SIT, and the 35-file backlog
> reconciliation.
>
> **R-03 and `WECMS_CALLBACK_ALLOWED_IPS` are ONE item, not two** (merged 2026-08-01; they were tracked
> separately here and in `payment-dispatch-final-readiness.md`): *dispatch callback endpoints are
> unauthenticated at the application layer; the IP allow-list is the mitigation and must be set per
> environment before the first callback, or every `01301`/`01302` returns 403.* The `permitAll` surface
> is the vulnerability and the allow-list is its only mitigation — closing either alone leaves the
> environment either open (`VV` HMAC as sole gate) or silently refusing every callback.

---

## 8. R-6 through R-16 — explicitly out of scope

**No work was started on R-6 … R-16 in this remediation.** This was an explicit constraint, and it
held. Specifically **not** done:

- Vendor / Expense dispatch-executor consolidation
- Creating a `BankDispatchPolicy` abstraction
- Relocating entities or cutoff components
- Extracting the EFS `00301` / `00302` / `203` clients
- Introducing JDBC batching (**R-10**) or asynchronous hand-off (**R-11**)
- Durable failure record / `DiscardPolicy` change (**R-12**)
- SFTP `known_hosts` host-key verification (**R-13**)
- Deleting the dead `CutoffCheck.apiTransport` seam (**R-8**) or the residual R-5 Javadoc

R-6, R-7, R-8, R-9, R-14, R-15 and R-16 remain **open structural debt** — they are why the module grade
is **C+** rather than B. None is a correctness or safety gate. The cheapest next step remains review
wave 3 (R-5 residual Javadoc + R-8), roughly three hours.

---

## 9. Files changed by concurrent sessions — separate review required

A parallel session was editing this tree throughout. Its work is **in the verified tree and covered by
the green 291-suite run**, but it was **not audited by this remediation** and needs its own review.

**Edited today (2026-07-30 09:29–10:01), all before the 10:09 baseline:**

| File | mtime |
|---|---|
| `dispatch/DispatchCountRepository.java` | 09:46:26 |
| `dispatch/service/DispatchResultResolver.java` | 09:48:03 |
| `dispatch/service/DispatchResult.java` | 09:48:24 |
| `dispatch/service/DispatchResultResolverTest.java` | 09:51:25 |
| `dispatch/DispatchCountQueryContractTest.java` | 09:52:53 |
| `dispatch/DispatchCountLegacyParityRegressionTest.java` | 09:54:52 |
| `haengsung/dispatch/HaengsungDispatchResultWriteBackService.java` | 09:58:21 |
| `docs/testing/missing-tests.md` · `docs/audits/dispatch-repository-query-reverification.md` · `docs/testing/integration-test-audit.md` | 09:29 / 10:00 / 10:01 |

**Earlier parallel workstreams present in the same tree** (not enumerated file-by-file here — take them
from `git status`):

- **WeCMS Batch** — `SchedulerLockConfig`, `common/lock/`, `ExchangeRateBatch*`, `OperationStatusBatchScheduler`, `StuckPayFileMonitorJob`, `StrandedRecoveryJob`, `ResultCheckJob`, all five `.env.*.example` files, `batch-dispatch-variable-reference.md`, `application-{sit,uat,prod}.yml`, `build.gradle`
- **HAENGSUNG** — scheduled-payment import, exchange-rate sync, card/account sync, dispatch result write-back, refresh controller
- **Callback hardening** — `CallbackIpWhitelistFilter`, `CallbackProperties`, `IpAllowList`
- **Operating hours / timezone** — `OperatingHoursService` zone fail-fast + its two test classes
- **H-4 atomic claim** — the parts of `BulkReceptionService` and `BulkDispatchClaimTxService` that are not the H-3 guards, plus `TbPayTargetRepository.claimForDispatch`
- **~35 untracked documents** under `docs/architecture/`, `docs/audits/`, `docs/deployment/`, `docs/features/wecms-batch/`, `docs/references/`, `docs/reports/`, `docs/testing/`

**Consequence for review:** `BulkReceptionService.java`, `BulkDispatchClaimTxService.java`,
`TbPayFileMstRepository.java` and `TbPayTargetRepository.java` each carry **two workstreams' changes**.
Per-line attribution needs `git diff` on the file, not this report.

---

## 10. Final recommendation

# READY FOR DEVELOPER REVIEW

**Why not `READY FOR SIT`:** the code is verified, but three things outside this remediation must land
first.

1. **S-4 is external and unresolved.** The VAN gateway source IPs do not exist yet. With
   `protection=ENFORCE`, enabling callbacks against an empty allow-list 403s every request.
2. **147 changed paths span at least five workstreams**, and only 12 of them were audited here. A SIT
   call needs one reviewer to have read the whole diff, which has not happened.
3. **U-7 has no run-book.** The monitor can now report three new stranded causes, and two of them must
   not be re-driven blind. Reporting a condition nobody has a procedure for is not SIT-ready.

**Why not `BLOCKED`:** nothing in this remediation's scope is open. All four Revision 2 blockers are
closed, verified adversarially, and proven test-guarded by mutation. The build reproduces from a clean
tree with no daemon. Nothing is enabled, no SQL ran, and no schema moved.

**Suggested path:** developer review of the full diff → S-4 / S-5 / S-6 closed by Ops and CI → U-7
run-book agreed with the PO → then re-assess for SIT with flags still off, enabling one rail at a time.
