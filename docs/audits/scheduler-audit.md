# `@Scheduled` Job Audit — wecms-api

**Date:** 2026-07-28
**Scope:** every `@Scheduled` method in `wecms-api/src/main/java` (12 methods / 13 annotations across 9 classes).
**Live evidence:** `shedlock` table on the refactor dev DB (`172.168.5.172:5432/wecmsdevdb`), read 2026-07-28.
**Not in scope:** the legacy JEX BCS batch (separate tree, separate process), `TB_BATCH_SCHEDULE_*` DB-driven windows.

---

## 0. Executive summary

| # | Finding | Severity |
|---|---|---|
| F-1 | **The whole scheduler is single-threaded** (`spring.task.scheduling.pool.size: 1`). No two jobs can ever overlap in one JVM — including a job with itself. A slow job delays every other job. | Structural (by design, but under-documented) |
| F-2 | **Three jobs fire at the same instant, three times over.** 08:30 ICT → 3 FX jobs; `0 */5` → 3 HAENGSUNG jobs; 00:00 ICT → both operation-status jobs. Execution order within a collision is **undefined**. `oprt-status-seed` → `oprt-status-refresh` has a semantic ordering expectation that is not guaranteed. | HIGH |
| F-3 | **ShedLock guards only the scheduled path.** Two user-triggered paths call the same work with no lock: `ExchangeRateService:100 → runOnDemand()` and `ErpPaymentRefreshService.importUnreceivedForCurrentTenant()` (manual Refresh button). Both can run concurrently with their scheduled twin. | HIGH |
| F-4 | **ShedLock does not coordinate with the legacy JEX batch.** The refactor runs against the live JEX DB; ShedLock only serialises refactor nodes. The FX and operation-status schedulers' own Javadoc says enabling them double-writes/races the legacy batch — and `.env.sit` sets `WECMS_EXCHANGE_RATE_BATCH_ENABLED=true`. | HIGH |
| F-5 | **Live state contradicts the committed env templates.** All 8 gated jobs have fired on node `GroupwareDev`; `batch-dispatch-variable-reference.md` declares all of them `false`. | HIGH |
| F-6 | **Every enable flag / cron is `${VAR}` with no inline default** (except `wecms.dispatch.recovery.*`). A missing host env var is a hard startup failure, not a disabled job. | MEDIUM |
| F-7 | **No retry anywhere.** Failure recovery is uniformly "log and wait for the next cron fire". No `@Retryable`, no backoff, no dead-letter, no alert on repeated failure. | MEDIUM |
| F-8 | `StrandedRecoveryService.recoverOnce()` calls `resultCheckService.runOnce()` under lock name `StrandedRecoveryJob.run`, while the same logic is guarded elsewhere by `ResultCheckJob.run`. Two different lock names over one body → concurrent execution if both jobs are enabled. **Latent** (both currently off). | MEDIUM |
| F-9 | `ResultCheckJob` has `lockAtMostFor = PT2M` on a job intended to run **every minute** and performing N sequential EFS 203 gateway calls. A pass exceeding 2 min releases its lock while still running. | MEDIUM |
| F-10 | The three FX crons are **hardcoded** in `ExchangeRateBatchScheduler` — not tunable per environment, unlike every other job. | LOW |
| F-11 | Live `shedlock.locked_at` values for `exch-shinhan-first-time` and `oprt-status-*` do not line up with their configured crons under either UTC or ICT reading. Needs log verification — see §5. | Open question |

---

## 1. Shared infrastructure

| Aspect | Value | Source |
|---|---|---|
| Scheduling enabled by | `@EnableScheduling` on `NxisasConfig` (not on the main application class) | [NxisasConfig.java:27](../../src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasConfig.java#L27) |
| **Thread pool** | **`size: 1`**, prefix `wecms-scheduler-` | [application.yml:33-38](../../src/main/resources/application.yml#L33-L38) |
| Distributed lock | ShedLock 5.16.0, `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")` | [SchedulerLockConfig.java:33](../../src/main/java/com/kosign/wecms/config/SchedulerLockConfig.java#L33) |
| Lock store | PostgreSQL `shedlock` table via `JdbcTemplateLockProvider`, **`usingDbTime()`** (immune to host clock skew) | [SchedulerLockConfig.java:36-43](../../src/main/java/com/kosign/wecms/config/SchedulerLockConfig.java#L36-L43) |
| Lock mode | Scheduler-proxy mode → `@SchedulerLock` **must** sit on the `@Scheduled` method, not the delegate. Honoured everywhere. | — |
| Lock table DDL | Manual DBA script (formerly Flyway V210). Flyway disabled, `ddl-auto: validate`. | [build.gradle:53-60](../../build.gradle#L53-L60) |
| Timezone | All cron jobs anchored `Asia/Ho_Chi_Minh`. DB session TZ is `Asia/Phnom_Penh` (also UTC+7); `shedlock` time columns are `timestamp without time zone`. | — |

**Consequence of pool size 1 (F-1):** ShedLock protects against *cross-node* duplication. *Intra-node* serialisation comes entirely from the single scheduler thread. The two protections are independent, and the single thread is doing more work than its one-line config suggests — it is the only thing preventing self-overlap on the `fixedDelay` jobs and the only reason the 08:30 triple-collision is safe.

---

## 2. Per-scheduler matrix

### 2.1 Exchange-rate collection — `ExchangeRateBatchScheduler`

Gate: `@ConditionalOnProperty("wecms.exchange-rate.batch.enabled", havingValue="true", matchIfMissing=false)` — **bean does not exist** when off, so no cron is registered at all. Work + `@Transactional` units live on `ExchangeRateBatchService`.

| Job | Cron (ICT) | Lock name | lockAtMostFor / AtLeastFor |
|---|---|---|---|
| `shinhanFirstTime` (legacy `EXCH_0001_001`) | `0 30 8 * * ?` — once/day 08:30 | `exch-shinhan-first-time` | PT10M / PT30S |
| `shinhanLastTime` (legacy `EXCH_0010_001`) | `0 30 8-18 * * ?` — hourly 08:30–18:30 (11×) | `exch-shinhan-last-time` | PT10M / PT30S |
| `woori` (legacy `EXCH_0020_001`) | **two** annotations: `0 30 8 * * ?` + `0 0,30 9-20 * * ?` — 08:30 then :00/:30 of 09–20 (25×) | `exch-woori` | PT10M / PT20S |

- **Can run simultaneously?** No — pool size 1. Note **all three collide at 08:30 ICT** (F-2): `8-18` includes hour 8, and woori's first annotation is also `0 30 8`. Three jobs queue on one thread, order undefined.
- **Distributed lock?** Yes, all three.
- **Duplicate execution risk:** *Cross-node* — covered. *Same node* — covered by the single thread. **Uncovered:** (a) the on-demand path `ExchangeRateService:100 → ExchangeRateBatchService.runOnDemand()` (`REQUIRES_NEW`) takes **no lock** and can run while a scheduled FX job holds one (F-3); partially mitigated by the `INSERT_NEW` collision guard at [ExchangeRateBatchService.java:393](../../src/main/java/com/kosign/wecms/domain/banking/service/ExchangeRateBatchService.java#L393) which logs and skips. (b) the **legacy JEX FX batch** writing the same `BANK_EXCH_RATE_INFM` (F-4).
- **Startup behaviour:** cron-only — never fires at startup; first fire at the next matching instant. Bean absent entirely when the flag is off.
- **Failure recovery:** per-row `try/catch` → `log.error` → continue with the next row ([:425-426](../../src/main/java/com/kosign/wecms/domain/banking/service/ExchangeRateBatchService.java#L425-L426)); gateway transport failure → `log.warn`, pass ends ([:478-479](../../src/main/java/com/kosign/wecms/domain/banking/service/ExchangeRateBatchService.java#L478-L479)). No retry — next cron fire is the recovery. Trigger methods are `@Transactional`, so an exception that escapes the per-row catch rolls back the whole pass.
- **Note (F-10):** these three crons are the only ones hardcoded in Java — no `${...}` override.

### 2.2 Operation-status inquiry — `OperationStatusBatchScheduler`

Gate: `@ConditionalOnProperty("wecms.operation-status.batch.enabled", ..., matchIfMissing=false)` — bean absent when off.

| Job | Cron (ICT) | Lock name | lockAtMostFor / AtLeastFor |
|---|---|---|---|
| `seed` (legacy `OPRT_0001_001`) | `${wecms.operation-status.batch.seed-cron:0 0 0 * * ?}` | `oprt-status-seed` | PT30M / PT1M |
| `refresh` (legacy `OPRT_0010_001`) | `${wecms.operation-status.batch.refresh-cron:0 0 0 * * ?}` | `oprt-status-refresh` | PT30M / PT1M |

- **Can run simultaneously?** No — pool size 1. **Both default to the same instant (00:00 ICT)** and, per F-2, run in **undefined order**. Seed-then-refresh is the semantically intended sequence but nothing enforces it; the two locks are independent so ShedLock does not order them either. Fix by separating the crons (e.g. seed 00:00, refresh 00:30) or by making one method call both in order under a single lock.
- **Distributed lock?** Yes, both.
- **Duplicate execution risk:** cross-node covered. **Legacy JEX already maintains `OPRT_STTS_INFO`** — the scheduler Javadoc states that enabling this double-writes/races the legacy batch (F-4).
- **Startup behaviour:** cron-only, no startup fire.
- **Failure recovery:** per-tenant `try/catch (RuntimeException)` → `log.warn` → continue ([OperationStatusBatchService.java:145-152](../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusBatchService.java#L145-L152)); each tenant is its own `@Transactional` unit, so one tenant's rollback does not lose the others. Returns a `BatchSummary(tenants, okTenants, failedTenants, affectedRows)`. No retry.

### 2.3 Firm-banking dispatch — `ResultCheckJob`, `StrandedRecoveryJob`

Gate: **cron-value gating**, not `@ConditionalOnProperty`. The beans always exist; the cron defaults to `"-"` (Spring's never-fire marker), so the trigger is registered and inspectable but never fires.

| Job | Cron | Enabled flag | Lock name | lockAtMostFor / AtLeastFor |
|---|---|---|---|---|
| `ResultCheckJob.run` (legacy `PROCESSING_RESULT_CHECK_BATCH`) | `${wecms.dispatch.result-check.cron:-}` | cron only | `ResultCheckJob.run` | **PT2M** / PT5S |
| `StrandedRecoveryJob.run` (R-1 safety net) | `${wecms.dispatch.recovery.cron:-}` | **double gate**: cron `-` **and** `wecms.dispatch.recovery.enabled:false` (service-level no-op) | `StrandedRecoveryJob.run` | PT10M / PT10S |

- **Can run simultaneously?** No — pool size 1.
- **Distributed lock?** Yes, both.
- **Duplicate execution risk:**
  - **F-9:** `ResultCheckJob` is documented as "every minute when enabled" but `lockAtMostFor = PT2M`. Each pass makes N sequential EFS 203 gateway calls; a pass exceeding 2 minutes releases its lock while still executing, so another node's next fire can start a second concurrent pass. Raise to comfortably exceed the worst-case pass duration.
  - **F-8:** `StrandedRecoveryService.recoverOnce()` delegates to `resultCheckService.runOnce()` at [StrandedRecoveryService.java:104](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/StrandedRecoveryService.java#L104) while holding `StrandedRecoveryJob.run`. The result-check body is therefore reachable under two distinct lock names — enabling both jobs allows two concurrent result-check passes. Mitigate with a shared lock name or by having recovery skip the delegation.
  - Coexistence: refactor-originated rows are indistinguishable from JEX-originated rows on the shared DB, so a blind recovery sweep could double-process JEX rows. This is exactly why both gates are shut (PO/Mr. Kang decision pending).
- **Startup behaviour:** beans created, trigger registered against `"-"` → never fires. **Confirmed by live evidence:** neither lock name is present in `shedlock`.
- **Failure recovery:** best-designed of the set. `ResultCheckService.runOnce` catches per-row, logs, and **deliberately leaves `PROC_STAT='6'` pending** so the next pass retries ([:222-227](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/ResultCheckService.java#L222-L227), [:362](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/ResultCheckService.java#L362)) — one failed row never blocks the others; transient gateway codes rethrow so the row stays claimable. `StrandedRecoveryService` catches per pay-target and per pay-file → `log.warn` → continue.

### 2.4 HAENGSUNG Oracle syncs — 3 jobs

Gate: **two layers** — `@ConditionalOnProperty(<job>.enabled, matchIfMissing=false)` (bean absent when off) **plus** a runtime `ObjectProvider.getIfAvailable() == null` check that no-ops when `haengsung.oracle.enabled=false` (the shared service bean is then absent).

| Job | Cron default | Enabled flags | Lock name | lockAtMostFor / AtLeastFor |
|---|---|---|---|---|
| `HaengsungScheduledPaymentImportJob.run` (legacy `ERP_0100_002`) | `${haengsung.scheduled-payment-import.cron:0 */5 * * * *}` | `.enabled` + `haengsung.oracle.enabled` | `HaengsungScheduledPaymentImportJob.run` | PT10M / PT30S |
| `HaengsungExchangeRateSyncJob.run` (legacy `ERP_0100_001`) | `${haengsung.exchange-rate-sync.cron:0 */5 * * * *}` | `.enabled` + `haengsung.oracle.enabled` | `HaengsungExchangeRateSyncJob.run` | PT10M / PT30S |
| `HaengsungCardAccountSyncJob.run` (legacy `ERP_0100_003`) | `${haengsung.card-account-sync.cron:0 */5 * * * *}` | `.enabled` + `haengsung.oracle.enabled` | `HaengsungCardAccountSyncJob.run` | PT10M / PT30S |

- **Can run simultaneously?** No — pool size 1. But **all three share the default cron `0 */5 * * * *`**, so every 5 minutes three jobs queue on one thread (F-2). The payment-import job iterates every active `PRDT_TYPE='H'` company; a slow Oracle round-trip there delays the other two and every other scheduler.
- **Distributed lock?** Yes, all three, distinct names.
- **Duplicate execution risk:** cross-node covered. **Uncovered (F-3):** `ErpPaymentRefreshService.importUnreceivedForCurrentTenant()` is shared with the interactive manual Refresh path, which holds **no** lock — a user pressing Refresh while the job runs executes the same import concurrently.
- **Startup behaviour:** cron-only, no startup fire. `haengsung.oracle.enabled=false` → job fires but logs `skipped — Oracle feature disabled` and returns (cheap no-op, still consumes the scheduler thread every 5 min).
- **Failure recovery:**
  - Payment import: per-business `try/catch (RuntimeException)` → `log.warn` → continue; `TenantContext.clear()` in `finally` (correct — no context leak onto the shared scheduler thread). Returns an aggregated `Summary`.
  - Exchange-rate / card-account sync: **no `try/catch` in the job**. An exception propagates to Spring's scheduler, which logs `Unexpected error occurred in scheduled task` and keeps the cron schedule alive. Acceptable, but these two produce no aggregated summary and no failure counter.
  - No retry in any of the three; next 5-minute fire is the recovery.

### 2.5 Always-on in-process jobs (no flag, no lock)

| Job | Trigger | Enabled flag | Distributed lock |
|---|---|---|---|
| `NxisasSessionRegistry.evictIdle` | `@Scheduled(fixedDelay = 60_000L)` — hardcoded | **none — always on** | **none** |
| `CutoffConfigProvider.scheduledRefresh` | `fixedDelayString` + `initialDelayString` = `${wecms.internal-transfer.cutoff.refresh-interval-ms:300000}` | **none — always on** | **none** |

- **Can run simultaneously?** No — pool size 1 serialises them against each other and against every gated job. `fixedDelay` also guarantees no self-overlap (next delay is measured from completion).
- **Distributed lock?** None — and **correctly so**. Both operate on per-JVM in-memory state (a `ConcurrentHashMap` of scrape sessions; a `volatile` config snapshot). Every node *must* run them. These are the two jobs where a distributed lock would be a bug, not a fix.
- **Duplicate execution risk:** none meaningful (per-node by design).
- **Startup behaviour — differs, deliberately:**
  - `evictIdle`: `fixedDelay` with **no** `initialDelay` → **runs almost immediately after context refresh**, then every 60 s after completion. Harmless (empty map).
  - `scheduledRefresh`: `initialDelay` equals the interval → **no startup fire**; first run at +5 min. Startup loading is instead done by `@PostConstruct init()`, which **fails the application context** if `TB_CUTOFF_CONFIG` / `TB_CUTOFF_PARAM` is empty, incomplete, or malformed — fail-fast, never fail-open.
- **Failure recovery:**
  - `evictIdle`: no `try/catch`. An exception escapes to the scheduler (logged, schedule continues). Guards against evicting an in-flight scrape (`isWorkerRunning()`), and retains terminal sessions for the TTL so the frontend's final poll still succeeds.
  - `scheduledRefresh`: **the reference implementation.** `refresh()` catches everything, **keeps the last valid snapshot**, and logs at `ERROR` ([:121-125](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/CutoffConfigProvider.java#L121-L125)) — a bad live DBA edit never degrades a running instance and never fails open on payment cut-off validation.

---

## 3. Enabled-flag reality check (F-5, F-6)

**F-6 — no inline defaults.** In `application.yml` and every profile (`local`, `sit`, `uat`, `prod`), the scheduler flags and crons are written as bare `${VAR}`:

```yaml
wecms.exchange-rate.batch.enabled:      ${WECMS_EXCHANGE_RATE_BATCH_ENABLED}      # no :default
wecms.operation-status.batch.enabled:   ${WECMS_OPERATION_STATUS_BATCH_ENABLED}   # no :default
wecms.dispatch.result-check.cron:       ${WECMS_DISPATCH_RESULT_CHECK_CRON}       # no :default
haengsung.{oracle,scheduled-payment-import,exchange-rate-sync,card-account-sync}.enabled: ${...}  # no :default
```

An unset host env var is an **unresolvable placeholder → startup failure**, not a disabled job. The only flags with an inline default are `wecms.dispatch.recovery.enabled:false` and `wecms.dispatch.recovery.cron:-` — i.e. the one job whose safety the team explicitly hardened. Recommend giving every scheduler flag a safe `:false` / `:-` inline default so "unconfigured" degrades to "off" rather than "won't boot".

Flags are read via `@Value` / `@ConfigurationProperties` with **no** `@RefreshScope`, and there is no actuator `/refresh`. **Every flag change requires a restart.** `@Scheduled(cron="${...}")` is resolved once at bean creation.

**F-5 — live state vs. committed templates.** `shedlock` on the refactor dev DB, single node `GroupwareDev`:

| Lock name | Last `locked_at` | Interpretation |
|---|---|---|
| `HaengsungCardAccountSyncJob.run` | 2026-07-28 04:25:04 | firing |
| `HaengsungScheduledPaymentImportJob.run` | 2026-07-28 04:25:04 | firing |
| `HaengsungExchangeRateSyncJob.run` | 2026-07-28 04:25:00 | firing |
| `exch-woori` | 2026-07-28 04:00:06 | firing |
| `exch-shinhan-last-time` | 2026-07-28 03:30:00 | firing |
| `exch-shinhan-first-time` | 2026-07-27 18:30:08 | firing (timing anomalous — §5) |
| `oprt-status-seed` | 2026-07-27 10:00:10 | firing (timing anomalous — §5) |
| `oprt-status-refresh` | 2026-07-27 10:00:00 | firing (timing anomalous — §5) |
| `ResultCheckJob.run` | **absent** | never fired — cron `-` confirmed |
| `StrandedRecoveryJob.run` | **absent** | never fired — cron `-` confirmed |

All 8 gated jobs are live on that node. `batch-dispatch-variable-reference.md` declares `WECMS_EXCHANGE_RATE_BATCH_ENABLED=false`, `WECMS_OPERATION_STATUS_BATCH_ENABLED=false`, and all HAENGSUNG flags `false`; `.env.sit` sets the FX flag `true` and the operation-status flag `false`. The committed templates are not a reliable description of any running environment — reconcile against the host env before changing anything. (Consistent with `batch-dispatch-variable-reference.md` §7 and memory `project_batch_deployed_flags_diverge_from_repo`.)

Because the refactor runs against the **live JEX DB** in all environments (memory `project_uat_prod_live_jex_db_target`), F-4 is not theoretical: the FX and operation-status batches are live on a DB whose `BANK_EXCH_RATE_INFM` / `OPRT_STTS_INFO` the legacy JEX batch also owns.

---

## 4. Cross-cutting answers

**Can run simultaneously?** — **No, never, for any pair, within one JVM.** `pool.size: 1`. Across JVMs, 10 of 12 are serialised by ShedLock; the remaining 2 are per-node caches that must run everywhere.

**Distributed lock?** — 10 of 12 have `@SchedulerLock` with a unique name. The 2 without (`evictIdle`, `scheduledRefresh`) correctly have none. `usingDbTime()` means locks are immune to host clock skew. `defaultLockAtMostFor = PT10M` is always overridden explicitly per job.

**Startup behaviour** —
- No job runs *because of* startup, with one exception: `evictIdle` (`fixedDelay`, no `initialDelay`) fires immediately after context refresh.
- Cron jobs, including the `"-"` ones, never fire at startup.
- Flag-gated beans (`@ConditionalOnProperty`) don't exist at all when off — no trigger registered, no thread cost.
- Cron-gated beans (dispatch) do exist; only the trigger is inert.
- `CutoffConfigProvider.@PostConstruct init()` is the only scheduler-adjacent component that can **block startup** — deliberately, so payment cut-off validation can't fail open.
- **Stale locks survive restart.** ShedLock rows are not cleared at startup; a node that dies mid-job leaves its lock until `lock_until` passes (2–30 min depending on the job). That is the intended crash-recovery mechanism, but it means a fast restart will find its own job still "locked".

**Failure recovery** — one uniform pattern, no exceptions: **catch at the smallest unit (row / tenant / business), log, continue; the next cron fire is the retry.** There is **no** retry, backoff, dead-letter, circuit breaker, failure-streak counter, or alert-on-repeated-failure anywhere in the 12 jobs. Quality ranking:
1. `CutoffConfigProvider.refresh` — keeps last-good snapshot, never fails open. Best.
2. `ResultCheckService.runOnce` — deliberate `PROC_STAT='6'` pending state so the next pass retries the exact failed rows.
3. `OperationStatusBatchService`, `HaengsungScheduledPaymentImportJob` — per-unit isolation + aggregated summary + `TenantContext` cleanup in `finally`.
4. `ExchangeRateBatchService` — per-row isolation, but the enclosing trigger is `@Transactional`, so anything escaping the inner catch rolls the whole pass back.
5. `HaengsungExchangeRateSyncJob`, `HaengsungCardAccountSyncJob`, `evictIdle` — no `try/catch`; rely on Spring's scheduler logging. Schedule survives, but no summary and no failure signal.

---

## 5. Open question (F-11) — lock timestamps vs. configured crons

`shedlock.locked_at` / `lock_until` are `timestamp without time zone`; the DB session TZ is `Asia/Phnom_Penh` (UTC+7 = ICT); `usingDbTime()` sources them from the DB clock. Reading the stored values **as UTC** makes three jobs line up cleanly with their ICT crons — `exch-woori` 04:00 → 11:00 ICT (valid `0 0,30 9-20`), `exch-shinhan-last-time` 03:30 → 10:30 ICT (valid `0 30 8-18`), and the HAENGSUNG trio 04:25 → 11:25 ICT (valid `0 */5`). Under that same reading:

- `exch-shinhan-first-time` last fired 01:30 ICT, but its cron is `0 30 8 * * ?` (08:30 ICT, hardcoded, not overridable).
- `oprt-status-seed` / `oprt-status-refresh` last fired 17:00 ICT, but the documented cron is `0 0 0 * * ?` (00:00 ICT). These two **are** env-overridable, so a deployed `WECMS_OPERATION_STATUS_BATCH_*_CRON` different from the template would explain it.

Neither reading (UTC or ICT-naive) resolves `exch-shinhan-first-time`. Two candidate explanations — a deployed JVM default zone interacting with the trigger, or a stale row from a node restart — cannot be distinguished from the lock table alone. **Action:** verify against the node's scheduler logs and its actual `WECMS_OPERATION_STATUS_BATCH_*_CRON` / JVM `-Duser.timezone` before treating either as a defect. Related precedent: memory `project_batch_schedule_tz_antdiv_fixes` (legacy JEX DB on `Asia/Seoul`, windows read 2 h off ICT).

---

## 6. Recommendations, in priority order

1. **Separate the colliding crons (F-2).** Give `oprt-status-refresh` a distinct time from `oprt-status-seed` (or fold both into one method under one lock, in order). Stagger the three HAENGSUNG jobs (`0 0/5`, `0 1/5`, `0 2/5`). Decide explicitly whether all three FX jobs are meant to fire together at 08:30.
2. **Close the ShedLock bypasses (F-3).** Wrap `ExchangeRateBatchService.runOnDemand()` and the manual `importUnreceivedForCurrentTenant()` path in the same lock name as their scheduled twin (ShedLock supports programmatic `LockingTaskExecutor` for non-`@Scheduled` entry points).
3. **Resolve the legacy-JEX coexistence question (F-4)** before the FX / operation-status flags stay on in any shared-DB environment. ShedLock cannot help here — it only sees refactor nodes.
4. **Reconcile deployed flags with the committed templates (F-5)** and make the templates authoritative or delete them.
5. **Give every scheduler flag an inline `:false` / `:-` default (F-6)** so a missing env var disables a job instead of breaking startup.
6. **Raise `ResultCheckJob.lockAtMostFor` (F-9)** above the worst-case pass duration, and unify the lock name shared with the recovery delegation (F-8), before either dispatch job is enabled.
7. **Document the pool-size-1 contract (F-1)** next to `spring.task.scheduling.pool.size` — it is load-bearing for correctness, not a tuning knob. If it is ever raised, F-2 becomes a live concurrency bug and every `fixedDelay` job loses its self-overlap guarantee.
8. **Add a failure signal (F-7).** At minimum, a consecutive-failure counter per job plus a log marker Ops can alert on; the existing `BatchSummary` / `Summary` records are the natural carriers.
9. **Externalise the three FX crons (F-10)** for parity with every other job.

---

## Appendix — inventory

| Class | Methods | File |
|---|---|---|
| `ExchangeRateBatchScheduler` | 3 (4 annotations) | `domain/banking/service/ExchangeRateBatchScheduler.java` |
| `OperationStatusBatchScheduler` | 2 | `domain/operation/scheduler/OperationStatusBatchScheduler.java` |
| `ResultCheckJob` | 1 | `domain/payment/dispatch/scheduler/ResultCheckJob.java` |
| `StrandedRecoveryJob` | 1 | `domain/payment/dispatch/scheduler/StrandedRecoveryJob.java` |
| `HaengsungScheduledPaymentImportJob` | 1 | `domain/payment/haengsung/batch/HaengsungScheduledPaymentImportJob.java` |
| `HaengsungExchangeRateSyncJob` | 1 | `domain/payment/haengsung/exchangerate/HaengsungExchangeRateSyncJob.java` |
| `HaengsungCardAccountSyncJob` | 1 | `domain/payment/haengsung/cardaccount/HaengsungCardAccountSyncJob.java` |
| `NxisasSessionRegistry` | 1 | `domain/banking/nxisas/session/NxisasSessionRegistry.java` |
| `CutoffConfigProvider` | 1 | `domain/payment/internaltransfer/service/CutoffConfigProvider.java` |

`ExchangeRateBatchService` and `OperationStatusBatchService` deliberately carry **no** `@Scheduled` — triggers live only on their gated scheduler beans so the work stays callable on-demand while the cron stays off.
