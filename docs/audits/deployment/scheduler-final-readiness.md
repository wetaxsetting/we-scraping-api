# Scheduler Final Readiness — SIT / UAT


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> **⚠️ SUPERSEDED IN PART — 2026-07-30.** Every statement below about *safe inline
> defaults* describes the state before the bare-placeholder migration, which removed
> all 169 inline defaults from the five config files. Finding 1 and finding 8 below
> were remediated that way and are **still closed**, by a different mechanism: the
> flags/crons are now bare `${VAR}` and the inert value lives in the profile's
> `.env.*.example`, enforced by `SchedulerConfigurationContractTest` (renamed from
> `SchedulerConfigurationContractTest`, and asserting the inverse contract plus the
> `.env` side the old test could not see). Read the rows below as history.
> Rationale and the F-6 record: [scheduler-legacy-parity-audit.md](../legacy-parity/scheduler-legacy-parity-audit.md) §F-6;
> mechanics: [../deployment/config-bare-placeholder-migration.md](../../deployment/config-bare-placeholder-migration.md).

**Date:** 2026-07-28
**Branch scope:** `wecms-api` scheduler subsystem (13 `@Scheduled` methods / 10 classes)
**Inputs:** [scheduler-audit.md](../legacy-parity/scheduler-audit.md) (refactor-internal) · [scheduler-legacy-parity-audit.md](../legacy-parity/scheduler-legacy-parity-audit.md) (legacy parity)
**Nothing was enabled.** No mutating scheduler was turned on, no live database was touched, no `shedlock` row was read-modified, no legacy batch was disabled, no deployed environment variable was changed.

---

## 1. Files changed

### New — shared programmatic-lock infrastructure

| File | Purpose |
|---|---|
| `src/main/java/com/kosign/wecms/common/lock/JobLockNames.java` | Canonical ShedLock lock-name constants. A lock name is a *lock identity*, not a label — two paths executing the same body must present the same string or ShedLock lets them run concurrently. Values reproduce the names already in deployed `shedlock` rows, so no existing mutual exclusion is broken. |
| `src/main/java/com/kosign/wecms/common/lock/JobLockGuard.java` | Programmatic locking for bodies reached from a non-`@Scheduled` entry point. `runIfFree` (one lock) and `runIfAllFree` (nested, simultaneous). Returns `false` rather than throwing when a lock is held; propagates `RuntimeException`/`Error` verbatim so `BusinessException` still reaches `GlobalExceptionHandler`. |

### Modified — main

| File | Change |
|---|---|
| `config/SchedulerLockConfig.java` | Added `LockingTaskExecutor` bean (backs `JobLockGuard`). Corrected the stale "created by Flyway V210" comment (Flyway is disabled; the table is DBA-run). Added the scope limit: **ShedLock coordinates refactor nodes only.** `lockProvider` de-publicised per the `@Bean` convention. |
| `domain/banking/service/ExchangeRateBatchScheduler.java` | Lock names → `JobLockNames` constants. Added COEXISTENCE PRECONDITION block (legacy `EXCH_*` must be off first; Woori `MAX+1`/INSERT-only hazard; `EXCH_0030_001` has no refactor twin). |
| `domain/banking/service/ExchangeRateBatchService.java` | Injected `JobLockGuard`. **New `runOnDemandGuarded()`** — non-transactional wrapper acquiring all three scheduled FX identities, then delegating to the unchanged `@Transactional(REQUIRES_NEW) runOnDemand()`. |
| `domain/banking/service/ExchangeRateService.java` | On-demand catch-up call site → `runOnDemandGuarded()`. |
| `domain/operation/scheduler/OperationStatusBatchScheduler.java` | **Rewritten.** Two `@Scheduled` methods / two locks / two crons → **one `runSeedThenRefresh()`, one lock, one cron**. Seed→refresh order now deterministic; a seed failure no longer suppresses refresh. Cron default `-`. |
| `domain/payment/dispatch/scheduler/ResultCheckJob.java` | Lock name → shared `JobLockNames.DISPATCH_RESULT_CHECK`. **`lockAtMostFor` PT2M → PT30M** with the full derivation in the Javadoc. Added COEXISTENCE PRECONDITION. |
| `domain/payment/dispatch/service/StrandedRecoveryService.java` | Injected `JobLockGuard`; the `resultCheckService.runOnce()` delegation now runs under `DISPATCH_RESULT_CHECK` with bounds matching `ResultCheckJob`, and logs+skips when held. |
| `domain/payment/dispatch/scheduler/StrandedRecoveryJob.java`, `StuckPayFileMonitorJob.java` | Lock names → `JobLockNames` constants. |
| `domain/payment/haengsung/exchangerate/HaengsungExchangeRateSyncProperties.java` | `DEFAULT_CRON` `0 */5 * * * *` → **`0 */10 7-19 * * ?`**. Javadoc corrected: cites `ERP_0100_001.batch`; records the OQ-10 window-timezone caveat. |
| `domain/payment/haengsung/batch/HaengsungScheduledPaymentImportProperties.java` | `DEFAULT_CRON` → **`0 */5 7-19 * * ?`** (rate was right, window was not). Cites `ERP_0100_002.batch`. |
| `domain/payment/haengsung/cardaccount/HaengsungCardAccountSyncProperties.java` | `DEFAULT_CRON` → **`0 0 7-19 * * ?`** (hourly). Cites `ERP_0100_003.batch`. |
| 3 × `Haengsung*Job.java` | Lock names → constants; COEXISTENCE PRECONDITION per job (which legacy `ERP_*` must be off, and the `ERP_SND_YN` / no-XA hazard). |
| `domain/payment/haengsung/controller/HaengsungPaymentRefreshController.java` | Injected `JobLockGuard`; the interactive Refresh now acquires `HAENGSUNG_PAYMENT_IMPORT` (the scheduled job's identity) and returns `WBE_HS_REFRESH_IN_PROGRESS` when held. |
| `messages.properties`, `messages_en.properties` | New `WBE_HS_REFRESH_IN_PROGRESS`. |

### Modified — configuration (5 profiles)

`application.yml`, `application-local.yml`, `application-sit.yml`, `application-uat.yml`, `application-prod.yml`:

- Inline safe defaults on every scheduler flag/cron: `${…:false}` / `${…:-}`.
- `seed-cron` + `refresh-cron` → single `cron: "${WECMS_OPERATION_STATUS_BATCH_CRON:-}"`.
- HAENGSUNG cron placeholders now carry the **legacy** default inline.
- New `wecms.dispatch.stuck-monitor.{enabled,cron}` (previously in no YAML at all — undiscoverable).
- Inline defaults for the **unconditionally bound** `wecms.dispatch.efs.*` / `sftp.*` (plain `@Component` + `@ConfigurationProperties`, so they bind even when the feature is off — a bare `${VAR}` there aborted the boot of an app that was not going to use them).
- Coexistence warnings as comments on `result-check` and the FX/OPRT blocks.

### Modified — env templates

`.env.sit`, `.env.uat.example`, `.env.prod.example`, `batch-dispatch-variable-reference.md`: HAENGSUNG crons → legacy values with the `.batch` source cited; `SEED_CRON`/`REFRESH_CRON` → `WECMS_OPERATION_STATUS_BATCH_CRON=-`; stuck-monitor vars added; coexistence notes.

**`.env.sit`: `WECMS_EXCHANGE_RATE_BATCH_ENABLED` `true` → `false`.** This was the only committed configuration that enabled a scheduler. **This edits the committed file only** — deployed SIT values come from `bin/setenv.sh` (memory `reference_sit_server_config_setenv`), so the running SIT node is unaffected and Ops must reconcile the host separately.

### New / modified tests

| File | Tests |
|---|---|
| `config/SchedulerConfigurationContractTest.java` *(new)* | 20 |
| `config/SchedulerCronAndLockIdentityTest.java` *(new)* | 9 |
| `common/lock/JobLockGuardTest.java` *(new)* | 10 |
| `common/lock/JobLockGuardStubs.java` *(new, helper)* | — |
| `domain/payment/dispatch/service/StrandedRecoveryResultCheckLockTest.java` *(new)* | 4 |
| `domain/banking/service/ExchangeRateOnDemandLockTest.java` *(new)* | 4 |
| `domain/operation/scheduler/OperationStatusBatchSchedulerTest.java` *(rewritten)* | 10 |
| `domain/payment/haengsung/controller/HaengsungPaymentRefreshControllerTest.java` *(extended)* | 3 |
| `domain/payment/dispatch/service/StrandedRecoveryServiceTest.java` *(constructor updated)* | 6 |

### Documentation

- `docs/audits/scheduler-legacy-parity-audit.md` — remediation banner mapping each finding to its post-fix status; new **OQ-10**.
- `docs/audits/scheduler-final-readiness.md` — this report.
- `docs/deployment/sit-uat-scheduler-deployment-checklist.md` — new operational checklist.

---

## 2. Problems corrected

| # | Problem | Correction | Evidence |
|---|---|---|---|
| 1 | **Missing env var aborted startup** instead of disabling the job. ~12 variables were bare `${VAR}` read by `@ConditionalOnProperty`/`@Scheduled` on every boot. | Safe inline defaults (`:false` / `:-`) in all 5 profiles, plus the unconditionally-bound EFS/SFTP properties. | `SchedulerConfigurationContractTest` resolves each key against the real YAML with **no** env vars contributed — resolution throws on a bare placeholder, so passing *is* the startup-safety proof. |
| 2 | **HAENGSUNG card/account sync ran ~22× legacy rate** (288 fires/day vs 13); exchange-rate sync 2×; all three 24 h instead of a 13 h window. | `DEFAULT_CRON` corrected to the legacy `.batch` schedules. | `SchedulerCronAndLockIdentityTest` enumerates a full day of fire times and asserts count, first/last hour and gap. |
| 3 | **Javadoc asserted the legacy cadence was "not in source"** — the premise the wrong defaults were chosen on. | All three property classes now quote the `.batch` file and path. | Source. |
| 4 | **seed and refresh could run in either order** at the same instant (independent locks impose no ordering; the single-thread pool picks arbitrarily). Refresh-before-seed silently drops a day of new tax codes — seed is create-only, refresh update-only. | One `runSeedThenRefresh()` method, one lock, one cron. | `OperationStatusBatchSchedulerTest` asserts exactly one `@Scheduled` method, `InOrder` seed→refresh, and that a seed failure still runs refresh. |
| 5 | **Result-check body reachable under two lock identities** — recovery delegated into `runOnce()` while holding only its own lock, so both jobs enabled ⇒ concurrent double-processing of live `PROC_STAT='6'` payment rows. | Delegation acquires `DISPATCH_RESULT_CHECK` with `ResultCheckJob`'s bounds; skips when held. | `StrandedRecoveryResultCheckLockTest` captures the `LockKey` and asserts identity + bounds; the held case asserts `verifyNoInteractions(resultCheckService)`. |
| 6 | **`ResultCheckJob.lockAtMostFor = PT2M` could expire mid-pass**, freeing the lock while still running. | PT30M, derived and documented. | Derivation in §3 below + `SchedulerCronAndLockIdentityTest`. |
| 7 | **Two manual entry points ran scheduled bodies unlocked** — FX on-demand catch-up and the interactive HAENGSUNG Refresh. | Both acquire their scheduled twins' identities via `JobLockGuard`. | `ExchangeRateOnDemandLockTest`, `HaengsungPaymentRefreshControllerTest`. |
| 8 | **Stuck-monitor properties existed in no YAML** — undiscoverable, and only accidentally startup-safe. | Declared in all 4 dispatch profiles with `false` / `-`. | `SchedulerConfigurationContractTest`. |
| 9 | **Committed config enabled a scheduler** (`.env.sit` FX batch `true`). | → `false`, with a note that the deployed host is driven by `setenv.sh`. | `.env.sit`. |
| 10 | **Coexistence preconditions existed only in audit docs**, not where an engineer flipping a flag would look. | COEXISTENCE PRECONDITION blocks on every affected scheduler + YAML/env comments. | Source. |

### `lockAtMostFor` derivation for `ResultCheckJob` (PT2M → PT30M)

Verified inputs:

- **Rows per pass: unbounded** — `TbFirmTranRepository.findByTranTpAndProcStat` returns a plain `List`, no `Limit`/`Pageable`; the today+aged filter is in-memory. Legacy had no cap either, so **no cap was introduced** (parity preserved) — but it means the bound is chosen against expected load, not proven from code.
- **Sequential** — one loop, one EFS 203 inquiry per row.
- **Worst case per row ≈ 23 s** — `EfsProperties.connectTimeoutMs=3000` + `readTimeoutMs=20000`.
- **Pacing** — `DispatchPacer`: Woori `970457` 3000 ms, Shinhan `970424` 100 ms, **every other bank 3000 ms** (default raised from legacy's 100 on 2026-08-01 — D-A; no third bank has ever been on the paced rail, so legacy's 100 was an unexercised initialiser, not a measured value). Applied on the skip / comms-fail / original-fail branches.
- **Errors don't shorten a pass** — failed rows are logged, left at `'6'`, paced, loop continues.

Typical: 200 aged rows ≈ 4 min (all-Shinhan) to ≈ 13 min (all-Woori). Degraded (gateway timing out) ≈ 26 s/row, so **PT2M covered only ~4 Woori rows**. **PT30M** absorbs ~400 Woori rows at typical latency or ~70 fully-timed-out rows. A generous ceiling costs only a longer stale-lock window after a real crash; too small causes concurrent processing of live payment rows. `lockAtLeastFor` stays PT5S.

---

## 3. Tests executed and results

| Command | Result |
|---|---|
| `./gradlew compileJava` | **PASS** |
| `./gradlew compileTestJava` | **PASS** |
| `./gradlew test --tests '*Scheduler*' '*JobLock*' '*ExchangeRate*' '*OperationStatus*' '*Haengsung*' '*StrandedRecovery*' '*ResultCheck*' '*Cutoff*' '*Nxisas*'` | **PASS** — 279 tests |
| `./gradlew test` (full suite) | **PASS — 2052 tests, 0 failures, 0 errors, 20 skipped** (260 test classes) |

Per new/changed class: `SchedulerConfigurationContractTest` 20/20 · `SchedulerCronAndLockIdentityTest` 9/9 · `JobLockGuardTest` 10/10 · `OperationStatusBatchSchedulerTest` 10/10 · `StrandedRecoveryResultCheckLockTest` 4/4 · `ExchangeRateOnDemandLockTest` 4/4 · `StrandedRecoveryServiceTest` 6/6 · `HaengsungPaymentRefreshControllerTest` 3/3 — all 0 failures.

### Required-proof coverage

| Required proof | Covered by | How |
|---|---|---|
| 1. Missing scheduler env vars don't fail startup | `SchedulerConfigurationContractTest` (× 4 profiles) | Real YAML loaded into a `StandardEnvironment` with the system-environment and system-properties sources **removed**; each key resolved. Unresolvable placeholder ⇒ throw ⇒ fail. |
| 2. Disabled jobs don't register or fire | `OperationStatusBatchSchedulerTest`, `ExchangeRateBatchSchedulerTest` | `ApplicationContextRunner` asserts `doesNotHaveBean` when the flag is missing/false. Cron-gated jobs asserted to default to `-`. |
| 3. HAENGSUNG cron defaults match legacy | `SchedulerCronAndLockIdentityTest` | Full-day fire enumeration: 78 / 156 / 13 fires, first hour 07, last hour 19, gaps 10 / 5 / 60 min; plus a guard that none equals the old `0 */5 * * * *`. |
| 4. Seed and refresh cannot run simultaneously | `OperationStatusBatchSchedulerTest` | Exactly one `@Scheduled` method; `seed`/`refresh` names gone; `InOrder` seed→refresh. |
| 5. ResultCheck and StrandedRecovery cannot process concurrently | `StrandedRecoveryResultCheckLockTest` + `SchedulerCronAndLockIdentityTest` | Delegation captured and asserted to use `DISPATCH_RESULT_CHECK` with matching bounds; held case invokes nothing. |
| 6. Manual and scheduled entry points lock consistently | `ExchangeRateOnDemandLockTest`, `HaengsungPaymentRefreshControllerTest`, `JobLockGuardTest` | FX asserts all three identities via `runIfAllFree` and no repository/gateway interaction when held; HAENGSUNG asserts `WBE_HS_REFRESH_IN_PROGRESS` + `verifyNoInteractions(service)`. |
| 7. Always-on read-only / in-memory jobs still work | `CutoffConfigProviderTest`, `CutoffValidatorTest`, `CutoffConfigServiceTest` (pre-existing, re-run green); `NxisasSessionRegistry` untouched | Neither always-on job was modified; both remain lock-free by design (per-JVM state — a distributed lock there would be a defect). |

### Not run, and why

**Full `@SpringBootTest` context startup per profile was not executed.** `local`/`sit` require the VPN-only shared PostgreSQL (`ddl-auto: validate` against a pre-migrated external schema), and `uat`/`prod` require host secrets that are deliberately not committed. `SchedulerConfigurationContractTest` covers the property-resolution failure mode that a context test would have caught here — but it does **not** prove full context startup. A real profile boot remains a SIT deployment step (§5 of the checklist).

---

## 4. Final scheduler configuration matrix

All 13 jobs, post-change. "Gate" = what must be true for the body to run.

| # | Job | Trigger / default cron (ICT) | Gate | Lock identity | atMost / atLeast |
|---|---|---|---|---|---|
| 1 | `ExchangeRateBatchScheduler.shinhanFirstTime` | `0 30 8 * * ?` (hardcoded) | `wecms.exchange-rate.batch.enabled` = **false** | `exch-shinhan-first-time` | PT10M / PT30S |
| 2 | `…shinhanLastTime` | `0 30 8-18 * * ?` (hardcoded) | same flag | `exch-shinhan-last-time` | PT10M / PT30S |
| 3 | `…woori` | `0 30 8 * * ?` + `0 0,30 9-20 * * ?` (hardcoded) | same flag | `exch-woori` | PT10M / PT20S |
| 4 | `OperationStatusBatchScheduler.runSeedThenRefresh` | **`-` (never fire)** | `wecms.operation-status.batch.enabled` = **false** | `oprt-status-batch` | PT60M / PT1M |
| 5 | `ResultCheckJob.run` | **`-`** | cron only | **`ResultCheckJob.run` (shared)** | **PT30M** / PT5S |
| 6 | `StrandedRecoveryJob.run` | **`-`** | cron **+** `wecms.dispatch.recovery.enabled` = **false** | `StrandedRecoveryJob.run` | PT10M / PT10S |
| 7 | `StuckPayFileMonitorJob.run` | **`-`** | cron **+** `wecms.dispatch.stuck-monitor.enabled` = **false** | `StuckPayFileMonitorJob.run` | PT5M / PT10S |
| 8 | `HaengsungScheduledPaymentImportJob.run` | **`0 */5 7-19 * * ?`** | `.enabled` = **false** **+** `haengsung.oracle.enabled` = **false** | `HaengsungScheduledPaymentImportJob.run` (shared with Refresh) | PT10M / PT30S |
| 9 | `HaengsungExchangeRateSyncJob.run` | **`0 */10 7-19 * * ?`** | same double gate | `HaengsungExchangeRateSyncJob.run` | PT10M / PT30S |
| 10 | `HaengsungCardAccountSyncJob.run` | **`0 0 7-19 * * ?`** | same double gate | `HaengsungCardAccountSyncJob.run` | PT10M / PT30S |
| 11 | `NxisasSessionRegistry.evictIdle` | `fixedDelay` 60 s | none — always on | none *(correct: per-JVM state)* | — |
| 12 | `CutoffConfigProvider.scheduledRefresh` | `fixedDelay` 5 min, `initialDelay` 5 min | none — always on | none *(correct: per-JVM snapshot)* | — |
| — | Non-scheduled lock users | `ExchangeRateBatchService.runOnDemandGuarded()` holds identities 1+2+3; `HaengsungPaymentRefreshController.refresh()` holds #8's; `StrandedRecoveryService` borrows #5's | — | — | — |

Unchanged and load-bearing: **`spring.task.scheduling.pool.size: 1`** — no two jobs overlap in a JVM, including a job with itself. Raising it would make the 08:30 FX triple-collision a live concurrency bug and remove the self-overlap guarantee from both `fixedDelay` jobs.

---

## 5. Jobs safe to deploy **disabled**

**All 13.** With the committed defaults, the app boots with:

- FX (3), operation-status (1), HAENGSUNG (3): **bean not created** (`@ConditionalOnProperty matchIfMissing=false`) — no trigger registered, no thread cost.
- Result-check, recovery, stuck-monitor: bean present, cron `-` → **never fires**.
- `evictIdle`, `scheduledRefresh`: always on, no DB mutation, no legacy interaction.

Startup requires **zero** scheduler environment variables. `CutoffConfigProvider.@PostConstruct` remains the one scheduler-adjacent component that can intentionally block startup — it fails fast on missing/invalid `TB_CUTOFF_CONFIG`/`TB_CUTOFF_PARAM` rather than fail open on payment cut-off validation. That is unchanged and correct; ensure those rows exist before deploying.

## 6. Jobs safe to **enable in SIT**

| Job | Safe in SIT? | Condition |
|---|---|---|
| `StuckPayFileMonitorJob` | **YES** | Read-only (two reads per stuck file). No legacy twin, no mutation. Set both `WECMS_DISPATCH_STUCK_MONITOR_ENABLED=true` and a cron. |
| FX (3) · operation-status (1) · HAENGSUNG (3) | **Conditionally** | SIT points at the refactor DB `172.168.5.172:5432/wecmsdevdb`, which is a **different instance** from JEX DEV `10.254.242.254:54311/wecmsvndevdb` — so there is no legacy↔refactor collision in SIT. Confirm that is still true for the SIT node (OQ-4/OQ-9), then enable **one job at a time** and verify the `shedlock` row and the target-table writes before the next. |
| `ResultCheckJob` | **NO** | Mutates real payment status. Needs the legacy-batch-inactive confirmation plus dispatch-leg readiness; enable only as part of a dispatch activation exercise, never as part of a scheduler smoke test. |
| `StrandedRecoveryJob` | **NO** | Re-drives real payments and cannot distinguish refactor-originated from JEX-originated rows. PO / Mr. Kang decision outstanding. |

## 7. Jobs that must remain **disabled**

- **`StrandedRecoveryJob`** — until shared-DB ownership and row-origin safety are confirmed. Double-gated deliberately.
- **`ResultCheckJob`** — while legacy `PROCESSING_RESULT_CHECK_BATCH` (`*/1 * * * *`, in `WECMS_BATCH DEV/`) is active against the same DB.
- **Everything mutating, in UAT/PROD** — because the refactor targets the **live JEX DB** there (memory `project_uat_prod_live_jex_db_target`) and legacy was verified still running.

## 8. Requirements before UAT enablement

Per job, all of which must be satisfied **in writing**:

1. **OQ-9 — DB identity.** Confirm whether the refactor UAT/PROD DB is the live JEX batch DB. Everything else is conditional on this.
2. **OQ-4 — deployed values.** Get the actual flags/crons on each node. The committed `.env` files are contradicted by live `shedlock` evidence and are not proof.
3. **Legacy-off confirmation, per family** — FX needs `EXCH_0001/0010/0020_001` off; operation-status needs `OPRT_0001/0010_001` off; HAENGSUNG needs `ERP_0100_001/002/003` off; result-check needs `PROCESSING_RESULT_CHECK_BATCH` off. ShedLock cannot substitute for any of these.
4. **OQ-3 — operation-status instant.** The cron is `-` precisely because the value is unknown. Obtain the JEX Batch Admin entry **and its timezone** before setting `WECMS_OPERATION_STATUS_BATCH_CRON`.
5. **OQ-10 — HAENGSUNG window timezone.** Cadence is verified exact; the `7-19` boundary assumes the legacy WAS runs ICT. If it runs `Asia/Seoul` the real window is 05:00–17:00 ICT.
6. **OQ-2 — FX backfill.** Decide whether a refactor equivalent of `EXCH_0030_001` is required before legacy FX is switched off; without it a missed window never self-heals.
7. **OQ-1 — `ERP_0100_004`.** Confirm whether the event-driven Step-5 write-back fully replaces it.
8. **Operational**: `shedlock` table present; a real profile context boot (not covered by unit tests); and agreement that `pool.size: 1` stays 1.

## 9. Remaining PO / Infra questions

Unchanged from [scheduler-legacy-parity-audit.md](../legacy-parity/scheduler-legacy-parity-audit.md) §12, plus one added during remediation. **None was closed by this work** — code corrections cannot answer ownership questions.

| ID | Question | Owner |
|---|---|---|
| OQ-1 | Is `ERP_0100_004` replaced by the event-driven Step-5 write-back, or a real gap? | PO + HAENGSUNG batch owner |
| OQ-2 | Does the refactor need an `EXCH_0030_001` FX backfill/gap-recovery equivalent? | Legacy batch owner + PO |
| OQ-3 | Actual JEX Batch Admin schedule **and timezone** for `OPRT_0001/0010_001`? | Legacy batch owner + Infra |
| OQ-4 | Deployed flag/cron values on `GroupwareDev` and each UAT/PROD node? | Infra / Ops |
| OQ-5 | Does the event-driven dispatch path honour the legacy per-`out_bank_cd` `tb_batch_schedule_info` windows and zone? | PO + Infra |
| OQ-6 | Were `TB_CUTOFF_CONFIG` / `TB_CUTOFF_PARAM` PO-approved? They appear in neither the legacy tree nor `legacy-table-inventory.md`. | PO |
| OQ-7 | `REPT_0900_001`: 17,140 errors / 27,787 runs, still running — known/accepted? | Legacy batch owner |
| OQ-8 | Should `WECMS_BATCH DEV/` be added to the root CLAUDE.md workspace layout, and under which ruleset? | Sonny |
| OQ-9 | Is the refactor UAT/PROD DB the live JEX batch DB? | Infra / Ops |
| **OQ-10** | Timezone of the legacy `7-19` hour window in `ERP_0100_00*.batch`? | Infra + HAENGSUNG batch owner |

### Intentional differences from legacy (recorded, not defects)

- **Transaction boundary** — legacy `EXCH_*` / `OPRT_*` wrap *all* tenants in one transaction (one bad tenant rolls back every tenant); the refactor uses per-tenant transactions. An improvement in partial-failure isolation, and a genuine behavioural difference.
- **Woori seq probe currency** — refactor pins `ccy='VND'`; documented in `ExchangeRateBatchService`.
- **Manual HAENGSUNG Refresh under contention** — now returns `WBE_HS_REFRESH_IN_PROGRESS` instead of importing. New user-visible message, required by the locking fix; chosen over a silent zero-row result, which would misreport "nothing to import".
- **`ERP_0100_004` / `EXCH_0030_001`** — deliberately **not** replaced. No legacy-equivalent flow was removed.

---

## 10. Final verdict

Everything in the task's "Required work" that is a code or configuration matter is done and test-pinned: deployment-safe defaults across five profiles, HAENGSUNG crons corrected to the legacy `.batch` schedules, seed→refresh made a single ordered locked unit, the result-check double-lock-identity defect closed, `lockAtMostFor` re-derived, both unlocked manual entry points brought under their scheduled twins' identities, and coexistence preconditions written where an engineer flipping a flag will actually see them. Full suite: **2052 tests, 0 failures**.

What is **not** resolved is ownership, not code: ten open questions remain, and the SIT-vs-UAT distinction turns entirely on OQ-9. The branch is safe to ship with every mutating scheduler off — that is now the guaranteed default rather than something that depended on a dozen environment variables being present. Enabling anything mutating in UAT still requires the legacy-off confirmations in §8.

Per the required verdict vocabulary, and reading it as the state of the *branch*:

### `READY FOR SIT SCHEDULER TESTING`

Justification: the app deploys safely with all schedulers disabled by default (no scheduler env var required to boot), and SIT is a **separate database instance** from JEX DEV, so scheduler behaviour can be exercised there without legacy coexistence risk — subject to confirming the SIT node's DB target (OQ-4/OQ-9) and enabling **one job at a time**. `StuckPayFileMonitorJob` is safe to enable immediately; `ResultCheckJob` and `StrandedRecoveryJob` must stay off regardless of environment.

This verdict covers SIT only. **UAT is NOT cleared** — it requires the §8 confirmations, above all OQ-9 and the per-family legacy-off evidence.
