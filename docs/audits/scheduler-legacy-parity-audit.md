# Scheduler Legacy-Parity Audit — refactor `wecms-api` vs legacy WeCMS/JEX batch

**Date:** 2026-07-28
**Type:** Audit only. No production code, configuration, DDL, scheduler flag, cron, or `shedlock` row was modified.
**Question answered:** do the Spring Boot schedulers correctly match the legacy WeCMS batch processes, or are there duplicated jobs, missing jobs, timing differences, ordering differences, concurrency risks, or shared-table conflicts?

**Companion doc:** [scheduler-audit.md](scheduler-audit.md) (refactor-internal scheduler audit, same date). This document is the **legacy-parity** layer and supersedes it wherever the two differ on inventory — see F-0 below.

---

> ## ⚑ REMEDIATION APPLIED — 2026-07-28
>
> The findings below were **audit-time observations**. Code and configuration corrections have since been applied on this branch; see **[scheduler-final-readiness.md](scheduler-final-readiness.md)** for the change list, test results and the deployment verdict.
>
> | Finding | Status after remediation |
> |---|---|
> | **F-3** HAENGSUNG schedules diverge (24 h / wrong rate) | **FIXED** — defaults now `0 */10 7-19 * * ?`, `0 */5 7-19 * * ?`, `0 0 7-19 * * ?`, pinned by `SchedulerCronAndLockIdentityTest` |
> | **F-4** Javadoc falsely claimed legacy cadence "not in source" | **FIXED** — all three property classes now cite the `.batch` file paths |
> | **F-5** operation-status aimed at the wrong instant | **MITIGATED, not resolved** — cron default is now `-` (never fire) instead of a guessed 00:00 ICT. The real instant still needs OQ-3. |
> | **F-6** missing env var = startup failure | **CLOSED — mechanism changed 2026-07-30.** Originally fixed with a safe inline default on every scheduler flag/cron. The bare-placeholder migration removed all inline defaults (169 across five files); F-6 stays closed because its *safety intent* is preserved and strengthened, not because the original fix survives. See the note below. Asserted per profile by `SchedulerConfigurationContractTest` (was `SchedulerConfigurationDefaultsTest`). |
> | **F-8** transaction-boundary divergence undocumented | **DOCUMENTED** — recorded here and in the final-readiness report |
> | **F-9** result-check body reachable under two lock names | **FIXED** — recovery delegation now acquires `JobLockNames.DISPATCH_RESULT_CHECK`; `StrandedRecoveryResultCheckLockTest` pins it |
> | **F-2** ordering: seed/refresh could run in either order | **FIXED** — one method, one lock, seed→refresh deterministic |
> | scheduler-audit **F-3** manual paths bypass ShedLock | **FIXED** — FX on-demand and HAENGSUNG Refresh now acquire their scheduled twins' lock identities |
> | scheduler-audit **F-9** `ResultCheckJob.lockAtMostFor` too tight | **FIXED** — PT2M → PT30M with the derivation recorded in the method Javadoc |
> | **F-1** `ERP_0100_004` missing · **F-2** `EXCH_0030_001` missing | **NOT ACTIONED BY DESIGN** — no replacement invented; both remain blocking confirmation items (OQ-1 / OQ-2) |

#### F-6 — closed by a different mechanism (2026-07-30). Not reopened.

F-6 was *"missing env var = startup failure"*: scheduler flags and crons were bare
`${VAR}`, so a host that had not exported them could not boot even with every job
switched off. The original remediation gave each one a safe inline default
(`${VAR:false}` / `${VAR:-}`), asserted per profile by
`SchedulerConfigurationDefaultsTest`.

**That mechanism is gone.** The 2026-07-30 bare-placeholder migration removed every
inline default from all five config files — 169 placeholders — as a deliberate
convention: values live in `.env` only, the yml declares shape.
See [../deployment/config-bare-placeholder-migration.md](../deployment/config-bare-placeholder-migration.md).

**F-6 remains CLOSED, for two reasons.**

1. **Its cost argument is void.** F-6 objected that inline defaults were needed
   because otherwise a deploy depended on ~12 variables being present just to start
   with everything off. A SIT deploy now requires **72** variables regardless, so the
   twelve scheduler ones add nothing to that burden. The burden is the whole
   configuration, and it is met by copying the profile's `.env.*.example`.
2. **Its safety intent is preserved and strengthened.** F-6 protected one guarantee:
   a forgotten variable must never silently enable a money-moving job. Inline
   defaults enforced that by being inert — but the same mechanism made a *misspelled*
   variable name indistinguishable from "deliberately not configured": the app booted
   on the hidden value while ops believed the `.env` was in force. Under bare
   placeholders the app refuses to start until somebody states the value. The
   guarantee holds; enforcement moves from the yml to a complete `.env`.

**Enforcement now lives in `SchedulerConfigurationContractTest`**, which asserts the
inverse of the old contract and adds a side the old test could not check at all:
every scheduler flag/cron is bare in the yml **and** has a line in the matching
`.env.*.example`, the committed value there is inert (`false` / `-`) for every
mutating scheduler, and any cron committed as something other than `-` parses. The
build-breaking guard still exists — it now fails if someone reintroduces an inline
default, rather than if someone removes one.

*Caveat worth recording:* bare placeholders fail fast only where the binding is
strict. `@Value` / `@Scheduled` abort naming the variable; `@ConfigurationProperties`
binds the literal `${VAR}` and only a typed field (boolean/int) rejects it — a String
field accepts it silently. Every scheduler flag and cron falls in the strict or typed
group, so F-6's scope is fully covered, but the general claim "a missing variable now
always fails loudly" is **not** true for String properties.
> | **F-7** ShedLock cannot coordinate with legacy | **UNCHANGED — architectural.** Now stated in-code on every affected scheduler as a COEXISTENCE PRECONDITION |
>
> Everything else — including the classification matrix in §4 and every open question in §12 — stands as written.

> ## ⚑ ADDENDUM — 2026-07-29 · `FILE_DEPOSIT_TRANSFER_BATCH2` retry responsibility restored
>
> A later dead-code / architecture review ([reports/dead-code-architecture-review.md](../reports/dead-code-architecture-review.md))
> found one business responsibility of the removed legacy BATCH2 that had **no** owner in the refactor:
> **retrying a failed or unanswered `00302`**.
>
> Legacy retried implicitly — it polled `TB_FIRM_FILE_MST (TRAN_DT, PROC_STEP='1', PROC_STAT='6')`
> every 60 s and did `continue` on an empty body (`:202-206`) or a transient `BT01`/`G999`/`C999`
> (`:232-234`), so the next cycle re-picked the row. The refactor issues `00302` inline from the
> `01301` callback only; on failure the file was left at `PROC_STEP='1'` and **nothing re-drove it**,
> after the file had already been SFTP-uploaded and accepted via `00301`.
>
> **This does NOT change the four-batch → one-batch verdict.** The approved event-driven architecture
> stands; only `PROCESSING_RESULT_CHECK_BATCH` remains a scheduled legacy-batch replacement. What was
> added is an exception handler behind the inline path, not a restored polling batch.
>
> | Item | Status |
> |---|---|
> | Retry sweep `BulkExecutionRetryJob` / `…Service` / `…TxService` | **IMPLEMENTED**, disabled by default (`enabled=false` + cron `-`) |
> | Lock identity `DISPATCH_BULK_EXECUTION_RETRY` | **NEW** — deliberately distinct from `DISPATCH_STRANDED_RECOVERY` (different state, different gateway leg, independently enableable) |
> | Legacy gates (target-fail `G998`, sibling, audit-before-call, status transitions) | **REPRODUCED** with legacy line citations |
> | Attempt ceiling + grace window + batch cap | **REFACTOR-ONLY BOUND** — legacy retried forever and scanned unbounded |
> | `BT00` treated as failure on the 302 leg | **BUG FOUND & FIXED** — legacy `:242` accepts `0000` **or** `BT00` |
> | Ops alert on `00302` failure | **RESTORED** (permanent codes only, matching legacy) |
> | Detection with the sweep OFF | **NEW** — second read-only sweep in `StuckPayFileMonitorService` |
> | **F-7 coexistence** | **CORRECTED 2026-07-29 — scoped, not blanket.** DEV/SIT are **DB-separated** (`wecmsdevdb` ≠ `wecmsvndevdb`) so legacy BATCH2 cannot collide there; SIT has no legacy batch container at all. UAT needs an **Ops** DB-target confirmation. PROD: refactor not deployed. On a genuinely shared DB the duplicate risk is real (neither side locks at selection; legacy POSTs before opening its tx, `:200` vs `:236`; fresh `TNO` both sides). **Owner: Ops/Infra, not Mr. Kang.** |
> | **K9 conflict (NEW)** | ⛔ **The real blocker.** K9 offered Mr. Kang **Option A — *"Recovery Scheduler (reconciliation) — periodically re-process aged `STEP=1` rows — re-introduces one bulk scheduled job"*** — and he **declined** it, choosing the inline model with *"no periodic recovery sweep … monitoring + manual re-trigger"* (`questions-for-mr-kang.md:21-39`, closed 2026-06-19). `BulkExecutionRetryJob` **is** Option A, so it was REMOVED on 2026-07-29 (K9 Option A, offered and not selected). **REMOVED 2026-07-29.** Automatic retry was never a parity requirement — inline execution already reproduces BATCH2 — and the sweep was K9 Option A, which was offered and not selected. `BulkExecutionRetryJob` and all its config have been deleted. See [final-four-to-one-batch-architecture-correction.md](../audits/final-four-to-one-batch-architecture-correction.md). → [legacy-batch2-ownership-verification.md](legacy-batch2-ownership-verification.md) |
>
> **Architecture approval is NOT outstanding.** Mr. Kang approved the 4→1 reduction *including Batch 2's
> replacement* on 2026-06-04, in writing on 2026-06-06 (*"배치2 → API1으로 갈음"*, *"위와 같은 로직으로
> 배치 업무 제거"*), recorded **"CLOSED — Approved"**. Any document still asking for that approval is stale.
>
> Scheduler inventory changes from **3 dispatch jobs to 4** (`ResultCheckJob`, `StrandedRecoveryJob`,
> `StuckPayFileMonitorJob`, `BulkExecutionRetryJob`) — all four cron-`-` by default. The §4
> classification matrix should be read with that fourth row added.
>
> Full spec + runbook: [features/wecms-batch/bulk-execution-retry.md](../features/wecms-batch/bulk-execution-retry.md).

> ## ⚑ ADDENDUM 2 — 2026-07-29 · result-check scan bounded; scan scope re-verified as GLOBAL
>
> A follow-up review examined the surviving scheduled batch, `ResultCheckJob` / `ResultCheckService`
> (the refactor of legacy `PROCESSING_RESULT_CHECK_BATCH`), and produced two findings for this audit.
>
> **1. The scan is GLOBAL by design — not a tenant-isolation defect.**
> Legacy `IDO.TB_FIRM_TRAN_R001.xml` selects with **no** `BIZ_KEY` predicate:
> ```sql
> WHERE TRAN_DT = TO_CHAR(now(),'YYYYMMDD') AND PROC_STAT = '6'
>   AND TRAN_TM <= TO_CHAR(now() - INTERVAL '2 minute','hh24miss')
>   AND TO_CHAR(now() - INTERVAL '2 minute','hh24miss') < '235800'
>   AND TRAN_TP = '202'
> ```
> — no ordering, no limit either. This is a system batch servicing every tenant. An earlier report
> characterised the refactor's equivalent as an "unbounded **cross-tenant**" scan; the cross-tenant
> half is **CLOSED AS FALSE POSITIVE**, and introducing a tenant filter would break legacy parity.
>
> **2. DB-side selectivity had regressed against legacy, and is restored.**
> The refactor called `findByTranTpAndProcStat("202","6")` — dropping legacy's SQL `TRAN_DT` and
> `TRAN_TM` predicates into a Java post-filter, so every `202`/`'6'` row ever written was loaded on
> each pass. Now `findResultCheckCandidates(...)` adds a deterministic `ORDER BY tranDt, tranTm,
> tranSeq` and a `Pageable` limit (`wecms.dispatch.result-check.batch-size`, default 100).
>
> The date/age rules deliberately **remain in Java**: the tiered `isAged` logic (RQ_DTTM as the
> authority, `TRAN_DT`/`TRAN_TM` only for legacy-written rows) is the documented **D-1** improvement
> over legacy's plain `HHmmss` compare, and folding it into one SQL date compare would reintroduce the
> UTC/ICT boundary bug. Oldest-first ordering is what makes the limit safe — a young row cannot
> displace an aged one, so a truncated pass makes guaranteed forward progress.
>
> | Aspect | Legacy | Refactor now |
> |---|---|---|
> | Tenant scope | global | global (**unchanged — parity**) |
> | `TRAN_TP` / `PROC_STAT` | SQL | SQL (**parity**) |
> | Date / age | SQL (`HHmmss` compare) | Java, tiered (**documented D-1 improvement**) |
> | Ordering | none | `tranDt, tranTm, tranSeq` ASC (**new**) |
> | Limit | none — one pass drains everything | `batch-size` 100 (**new operational bound**) |
> | Per-row isolation, pacing, ShedLock identity | per-row, per-bank, n/a | unchanged (`DISPATCH_RESULT_CHECK`) |
>
> Cron remains `-` (never fire); **F-7 coexistence still applies** — the job must stay off while legacy
> `PROCESSING_RESULT_CHECK_BATCH` runs against the same DB. New property
> `WECMS_DISPATCH_RESULT_CHECK_BATCH_SIZE` is bare `${VAR}` in all four profiles and supplied by each `.env.*.example` (inline defaults removed 2026-07-30).
> Tests: `ResultCheckBoundedScanTest` (18), `ResultCheckPollTest` (12, re-pointed).

### Evidence classes used throughout

| Marker | Meaning |
|---|---|
| **[V]** | **Verified** — read directly from source on disk, or returned by a read-only query against a live DB. |
| **[I]** | **Inferred** — a defensible reading of verified evidence, but not directly observed. |
| **[?]** | **Unresolved** — cannot be settled from the evidence available to this audit; listed in §12. |

**Databases queried (read-only):**
- JEX DEV frame DB — `10.254.242.254:54311/wecmsvndevdb`, schema `wecmsvndev` (438 tables). **Legacy-only traffic.**
- Refactor R&D DB — `172.168.5.172:5432/wecmsdevdb`. **Refactor-only traffic.**

These are **different database instances** **[V]** — so DEV/SIT carries *no* legacy↔refactor coexistence exposure. The exposure is UAT/PROD, where the refactor is targeted at the **live JEX DB** (memory `project_uat_prod_live_jex_db_target`).

---

## 1. Executive summary

The refactor implements **13 scheduled methods across 10 classes** **[V]**. The legacy estate contains **two separate batch applications** holding **~32 batch programs** **[V]**:

- `WECMS DEV/WECMS/src/bcs/` + `src/batch/` — 28 BCS programs + 4 ERP simpleBatch programs (the JEX main app)
- **`WECMS_BATCH DEV/`** — 4 firm-banking dispatch batches, in a **fifth workspace tree that the root [CLAUDE.md](../../../CLAUDE.md) does not list**

The refactor covers **8 of ~32 legacy batch programs**. The behavioral parity of what *is* covered is generally high — the FX batch in particular is near-exact. The material risks are **schedule** parity (not behavior), two **missing** legacy jobs, and coexistence on shared tables in UAT/PROD.

| # | Finding | Severity |
|---|---|---|
| **F-0** | **A 13th scheduler, `StuckPayFileMonitorJob`, was added to the source during this audit** — it was absent from the same tree hours earlier. Caught only by the mandated re-read. Any inventory of this subsystem has a short shelf life. | Process |
| **F-1** | **`ERP_0100_004` (WeCMS→ERP vendor/expense/payroll/overseas export, `*/5 7-19`) has no refactor scheduler** **[V]**. It may be intentionally replaced by the event-driven Step-5 dispatch-result write-back, but nothing in the refactor source states that. | HIGH — §10, OQ-1 |
| **F-2** | **`EXCH_0030_001` (multi-bank × multi-SEQ_NO FX backfill) has no refactor counterpart** **[V]**. It is the catch-up/gap-fill job; without it, a missed FX window is never backfilled. | HIGH — §10, OQ-2 |
| **F-3** | **HAENGSUNG schedules diverge sharply from legacy.** All three refactor jobs default to `0 */5 * * * *` = **every 5 min, 24 h**. Legacy: `*/10 7-19` (ERP_0100_001), `*/5 7-19` (ERP_0100_002), **`0 7-19` = hourly** (ERP_0100_003) **[V]**. Card/account sync therefore runs **~12× legacy frequency** and all three run **24 h instead of a 13 h window**. | HIGH — §6 |
| **F-4** | The refactor property Javadoc claims the HAENGSUNG "legacy cadence is Jex-Studio-defined and **not in source**" **[V]** — but it **is** in source, in the `.batch` files. The defaults were chosen against a false premise. | HIGH — §6 |
| **F-5** | **Legacy `OPRT_0001_001`/`OPRT_0010_001` last fired 2026-07-27 15:00:01 UTC = 22:00 ICT = 00:00 Asia/Seoul** **[V]**. The refactor is configured for `0 0 0 * * ?` **ICT** (= 17:00 UTC). Legacy and refactor are aimed at **different instants**. | HIGH — §6, OQ-3 |
| **F-6** | **Legacy is verifiably live in DEV right now.** `bank_exch_rate_infm` shows legacy writes at `20260728013014` (Shinhan) and `20260728100009` (Woori) **[V]**; `ext_tran_call_his` shows OPRT_0001_001/0010_001 at `20260727150001/150005` **[V]**. No legacy batch was found to have any duplicate-execution guard **[V]**. | HIGH — §7 |
| **F-7** | **ShedLock cannot coordinate with legacy.** The `shedlock` table contains only refactor lock names **[V]**; the legacy JexSimpleBatch/BCS runtime has no participation in it **[V]**. Every "distributed lock" claim in the refactor is **refactor-node-only**. | HIGH — §7, §8 |
| **F-8** | **Legacy runs one transaction across all tenants** for EXCH_* and OPRT_* (`beginTransaction()` before the tenant loop, single `commit` after) **[V]** — one bad tenant rolls back **every** tenant. The refactor uses per-tenant transactions **[V]**. A deliberate improvement, but it changes partial-failure semantics and is **not** documented as a divergence. | MEDIUM |
| **F-9** | The 08:30 ICT triple-collision, the 00:00 ICT seed/refresh ordering gap, the on-demand ShedLock bypasses, and the single-thread dependency all carry over from [scheduler-audit.md](scheduler-audit.md) and are **not** legacy-parity issues — legacy had no such coupling because each batch was its own scheduled unit. | MEDIUM — §8 |
| **F-10** | `REPT_0900_001` (legacy daily-cash report batch) shows **17,140 errors across 27,787 runs, still running as of 20260702** **[V]**. Outside scheduler scope, but it is the batch behind Daily Cash and the error rate is not incidental. | Informational — OQ-7 |

**Bottom line:** no refactor scheduler is currently *duplicating* legacy work on a shared DB, because DEV/SIT is a separate database **[V]**. Nothing here blocks DEV/SIT. **Every** flag-enabled refactor batch is unsafe to enable in UAT/PROD until the shared-DB ownership question is settled — see §11.

---

## 2. Refactor scheduler inventory **[V]**

13 methods / 14 `@Scheduled` annotations / 10 classes. Re-verified on disk at finalization.

| # | Class · method | Trigger | Enable mechanism | Lock name | lockAtMost/AtLeast |
|---|---|---|---|---|---|
| R1 | `ExchangeRateBatchScheduler.shinhanFirstTime` | cron `0 30 8 * * ?` ICT (hardcoded) | `@ConditionalOnProperty wecms.exchange-rate.batch.enabled` | `exch-shinhan-first-time` | PT10M / PT30S |
| R2 | `ExchangeRateBatchScheduler.shinhanLastTime` | cron `0 30 8-18 * * ?` ICT (hardcoded) | same bean gate | `exch-shinhan-last-time` | PT10M / PT30S |
| R3 | `ExchangeRateBatchScheduler.woori` | **two** crons `0 30 8 * * ?` + `0 0,30 9-20 * * ?` ICT (hardcoded) | same bean gate | `exch-woori` | PT10M / PT20S |
| R4 | `OperationStatusBatchScheduler.seed` | `${…batch.seed-cron:0 0 0 * * ?}` ICT | `@ConditionalOnProperty wecms.operation-status.batch.enabled` | `oprt-status-seed` | PT30M / PT1M |
| R5 | `OperationStatusBatchScheduler.refresh` | `${…batch.refresh-cron:0 0 0 * * ?}` ICT | same bean gate | `oprt-status-refresh` | PT30M / PT1M |
| R6 | `ResultCheckJob.run` | `${wecms.dispatch.result-check.cron:-}` ICT | cron `-` only | `ResultCheckJob.run` | **PT2M** / PT5S |
| R7 | `StrandedRecoveryJob.run` | `${wecms.dispatch.recovery.cron:-}` ICT | cron `-` **+** `wecms.dispatch.recovery.enabled:false` | `StrandedRecoveryJob.run` | PT10M / PT10S |
| R8 | **`StuckPayFileMonitorJob.run`** *(new — F-0)* | `${wecms.dispatch.stuck-monitor.cron:-}` ICT | cron `-` **+** `wecms.dispatch.stuck-monitor.enabled` | `StuckPayFileMonitorJob.run` | PT5M / PT10S |
| R9 | `HaengsungScheduledPaymentImportJob.run` | `${…cron:0 */5 * * * *}` ICT | `@ConditionalOnProperty .enabled` **+** runtime `haengsung.oracle.enabled` | `HaengsungScheduledPaymentImportJob.run` | PT10M / PT30S |
| R10 | `HaengsungExchangeRateSyncJob.run` | `${…cron:0 */5 * * * *}` ICT | same double gate | `HaengsungExchangeRateSyncJob.run` | PT10M / PT30S |
| R11 | `HaengsungCardAccountSyncJob.run` | `${…cron:0 */5 * * * *}` ICT | same double gate | `HaengsungCardAccountSyncJob.run` | PT10M / PT30S |
| R12 | `NxisasSessionRegistry.evictIdle` | `fixedDelay = 60_000L` (hardcoded) | **none — always on** | **none** | — |
| R13 | `CutoffConfigProvider.scheduledRefresh` | `fixedDelayString`+`initialDelayString` `${…refresh-interval-ms:300000}` | **none — always on** | **none** | — |

Shared infra: `@EnableScheduling` on `NxisasConfig:27`; `@EnableSchedulerLock(defaultLockAtMostFor="PT10M")` on `SchedulerLockConfig:33`; `JdbcTemplateLockProvider` + `usingDbTime()`; **`spring.task.scheduling.pool.size: 1`** ([application.yml:35-37](../../src/main/resources/application.yml#L35-L37)) **[V]**.

`ExchangeRateBatchService` and `OperationStatusBatchService` deliberately carry **no** `@Scheduled` **[V]** — triggers live only on the gated scheduler beans.

---

## 3. Legacy batch inventory **[V]**

### 3.1 JEX main app — `WECMS DEV/WECMS/`

**Architecture:** each batch exists twice — a ~55-line `src/batch/…` **simpleBatch shim** that calls `util.createBCSData(id)` + `DomainUtil.copyDomain` and delegates to the real `src/bcs/…` **BCS implementation** **[V]** (verified on `batch/…/exch/EXCH_0001_001.java:48-51`). Only the ERP programs carry `.batch` schedule files; the rest are scheduled by **JEX Batch Admin / Jex Studio** (DB/console-driven, not in source) **[V]**.

| Legacy program | Source | Refactor counterpart |
|---|---|---|
| `EXCH_0001_001` | `src/bcs/jex/wabooks/exch/` (389 ln) | R1 |
| `EXCH_0010_001` | same (393 ln) | R2 |
| `EXCH_0020_001` | same (309 ln) | R3 |
| **`EXCH_0030_001`** | same (407 ln) | **none — F-2** |
| `OPRT_0001_001` | `src/bcs/jex/wabooks/oprt/` (224 ln) | R4 |
| `OPRT_0010_001` | same (224 ln) | R5 |
| `ERP_0100_001` | `src/batch/jex/wabooks/erp/` (317 ln) + `.batch` | R10 |
| `ERP_0100_002` | same (714 ln) + `.batch` | R9 |
| `ERP_0100_003` | same (347 ln) + `.batch` | R11 |
| **`ERP_0100_004`** | same (976 ln) + `.batch` | **none — F-1** |
| `BATCH_0001_001` (einv) | `src/bcs/jex/wabooks/einv/` | none — out of scope |
| `REPT_0003/0200/0300/0400/0500/0600/0700/0900_001` | `src/bcs/jex/wabooks/rept/` | none — out of scope |
| `MBMR_0100_001`, `MBMR_0200_001` | `src/bcs/jex/wabooks/mbmr/` | none — out of scope |
| `COMM_0500/0800/0900_001`, `COMM_1000_001` | `src/bcs|batch/…/comm/` | none — out of scope |
| `SZON_0004_001`, `SZON_0100_001`, `SZONE_0100_001` | `src/bcs/jex/wabooks/szon/` | none — out of scope |
| `TRCO_0100_001`, `SALE_0100_001`, `FNSH_0100_001`, `BLTB_0100_001`, `ADMN_0020_003` | respective packages | none — out of scope |

### 3.2 Firm-banking batch app — `WECMS_BATCH DEV/` **(fifth tree, undocumented in root CLAUDE.md)**

`source/WECMS_BATCH/WECMS_BATCH/src/batch/` — 4 programs, each with a `.batch` schedule **[V]**:

| Legacy program | `.batch` schedule | Korean name | Refactor counterpart |
|---|---|---|---|
| `PROCESSING_RESULT_CHECK_BATCH` (420 ln) | `*/1 * * * *` | 펌뱅킹 처리결과조회 배치 | R6 |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `*/1 * * * *` | 대량 파일 이체처리 **접수** 배치 주기 1분 | none — event-driven TO-BE |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | `*/1 * * * *` | 대량 파일 이체처리 **지시** 배치 주기 1분 | none — event-driven TO-BE |
| `NAPAS_REAL_TIME_BATCH` | `* * * * *` | 실시간 입금이체 배치 | none — event-driven TO-BE |

`web/WEB-INF/properties/wecms_batch.properties` maps `PROCESSING_RESULT_CHECK=/WGVN_EFS_203.jct` and carries **T_/U_/R_ (Test/UAT/Real) triples for SFTP + API hosts** **[V]** → the legacy firm-banking batch is provisioned for all three environments. A `wecms_batch_HS.properties` variant exists for HAENGSUNG (drops `MSG_API` and `EXC_TRAN_INQ`) **[V]**.

Additional legacy DB-driven windowing: `wecmsvndev.tb_batch_schedule_info` holds 4 rows — `FILE_DEPOSIT_TRANSFER_BATCH1` and `NAPAS_REAL_TIME_BATCH`, for `out_bank_cd` `970424` and `970457`, all `batch_start_tm=000000` / `batch_end_tm=235959` **[V]**. **No row exists for `PROCESSING_RESULT_CHECK_BATCH` or `FILE_DEPOSIT_TRANSFER_BATCH2`** **[V]**.

---

## 4. Refactor-to-legacy mapping matrix

| Refactor scheduler | Legacy batch | Classification | Refactor schedule | Legacy schedule | Same tables | Same behavior | Legacy still active | Safe during coexistence | Evidence |
|---|---|---|---|---|---|---|---|---|---|
| R1 `exch-shinhan-first-time` | `EXCH_0001_001` | **MATCHED** | `0 30 8 * * ?` ICT | 08:30 ICT (Jex Studio) | Yes — `BANK_EXCH_RATE_INFM` | Yes | **Yes (DEV)** | **No** | legacy write `20260728013014`=01:30 UTC=08:30 ICT **[V]** |
| R2 `exch-shinhan-last-time` | `EXCH_0010_001` | **MATCHED** | `0 30 8-18 * * ?` ICT | hourly 08:30–18:30 ICT | Yes | Yes — SEQ 1 skip-if-exists + SEQ 2 upsert | **Yes** | **No** | legacy `mod_dtm 20260728103013`=17:30 ICT **[V]** |
| R3 `exch-woori` | `EXCH_0020_001` | **MATCHED WITH INTENTIONAL DIFFERENCE** | `0 30 8` + `0 0,30 9-20` ICT | every 30 min 08:30–20:30 ICT | Yes | Near — seq probe pinned to `ccy='VND'` | **Yes** | **No** | legacy `20260728100009`=17:00 ICT, `max_seq=15` **[V]** |
| — | **`EXCH_0030_001`** | **MISSING IN REFACTOR** | — | Jex Studio **[?]** | `BANK_EXCH_RATE_INFM` | n/a | **[?]** | n/a | 407-ln multi-bank×SEQ backfill **[V]** |
| R4 `oprt-status-seed` | `OPRT_0001_001` | **PARTIAL MATCH** | `0 0 0 * * ?` ICT | **22:00 ICT / 00:00 KST** | Yes — `OPRT_STTS_INFO`, `EXT_TRAN_CALL_HIS` | Yes — F/M only, create-only | **Yes** | **No** | `ext_tran_call_his 20260727150001` **[V]** |
| R5 `oprt-status-refresh` | `OPRT_0010_001` | **PARTIAL MATCH** | `0 0 0 * * ?` ICT | **22:00 ICT / 00:00 KST** | Yes | Yes — update-only | **Yes** | **No** | `ext_tran_call_his 20260727150005` **[V]** |
| R6 `ResultCheckJob.run` | `PROCESSING_RESULT_CHECK_BATCH` | **MATCHED WITH INTENTIONAL DIFFERENCE** (off by default) | `-` (never fires) | `*/1 * * * *` 24 h | Yes — `TB_FIRM_TRAN`, `TB_PAY_TARGET` | Yes — PROC_STAT + pacing + leave-pending | **[?]** UAT/PROD | **No** | `.batch` **[V]**; absent from `shedlock` **[V]** |
| R7 `StrandedRecoveryJob.run` | *none* | **NEW REFACTOR-ONLY JOB** | `-` (never fires) | — | `TB_PAY_TARGET`, `TB_PAY_FILE_MST` | n/a | n/a | **No** — double-processes JEX rows | Javadoc + no legacy twin **[V]** |
| R8 `StuckPayFileMonitorJob.run` | *none* | **NEW REFACTOR-ONLY JOB** | `-` (never fires) | — | read-only | n/a | n/a | **Yes** (read-only) | source **[V]** |
| R9 `HaengsungScheduledPaymentImportJob` | `ERP_0100_002` | **PARTIAL MATCH** | `0 */5 * * * *` **24 h** | `*/5 **7-19** * * *` | Yes | Trigger-only, reuses Step-2 service | **[?]** | **No** | `.batch` **[V]** |
| R10 `HaengsungExchangeRateSyncJob` | `ERP_0100_001` | **PARTIAL MATCH** | `0 */5 * * * *` **24 h** | **`*/10`** `7-19 * * *` | Yes — `BANK_EXCH_RATE_INFM.erp_snd_yn` → `TB_FCY_EXRATE` | Similar | **[?]** | **No** | `.batch` **[V]** |
| R11 `HaengsungCardAccountSyncJob` | `ERP_0100_003` | **PARTIAL MATCH** | `0 */5 * * * *` **24 h** | **`0`** `7-19 * * *` (hourly) | Yes — `TB_ACCT_HISTORY`, `TB_CARD_APPROVAL` | Similar | **[?]** | **No** | `.batch` **[V]** |
| — | **`ERP_0100_004`** | **MISSING IN REFACTOR** / **NEEDS PO CONFIRMATION** | — | `*/5 7-19 * * *` | vendor/expense/payroll/overseas → ERP | n/a | **[?]** | n/a | 976-ln source **[V]** |
| R12 `evictIdle` | *none* | **NEW REFACTOR-ONLY JOB** | `fixedDelay` 60 s | — | none (in-memory) | n/a | n/a | **Yes** | no legacy match **[V]** |
| R13 `scheduledRefresh` | *none* | **NEW REFACTOR-ONLY JOB** | `fixedDelay` 5 min | — | `TB_CUTOFF_CONFIG`/`_PARAM` (read-only) | n/a | n/a | **Yes** | tables absent from legacy tree **and** legacy inventory **[V]** |
| — | `FILE_DEPOSIT_TRANSFER_BATCH1` | **MATCHED WITH INTENTIONAL DIFFERENCE** (event-driven TO-BE) | event-driven | `*/1 * * * *` | `TB_PAY_FILE_*` | reception now request-time | **[?]** | n/a | `.batch` **[V]**; memory `project_batch_event_driven_tobe_recovery` |
| — | `FILE_DEPOSIT_TRANSFER_BATCH2` | **MATCHED WITH INTENTIONAL DIFFERENCE** | event-driven (01301) | `*/1 * * * *` | `TB_PAY_FILE_*` | execution now 01301-triggered | **[?]** | n/a | ditto |
| — | `NAPAS_REAL_TIME_BATCH` | **MATCHED WITH INTENTIONAL DIFFERENCE** | event-driven (202) | `* * * * *` | `TB_FIRM_TRAN` | real-time 202 inline | **[?]** | n/a | ditto |
| — | ~20 other BCS batches (§3.1) | **MISSING IN REFACTOR** (out of scope) | — | Jex Studio | various | n/a | partly (`REPT_0900_001`) | n/a | `bat_call_his` **[V]** |

---

## 5. Detailed comparison per scheduler

### 5.1 R1/R2/R3 — Exchange rate vs `EXCH_0001/0010/0020_001` → **MATCHED / MATCHED WITH INTENTIONAL DIFFERENCE**

**Legacy behavior [V]** (`src/bcs/jex/wabooks/exch/`):
- Tenant fan-out: `BUSI_INFM_R001` with `busi_stts = BusiStts.NORMAL`, `del_yn = 'N'`, **`prdt_type in ('F','M','H')`** (`EXCH_0001_001:101-107`).
- Bank constants: Shinhan `11704088` / module `GLOBAL/GBL/BANK_Shinhan` / class `COMMON`; Woori `11704020` / `GLOBAL/GBL/BANK_Woori` (`:81`, `:159`, `:313-314`).
- Write mode:
  - `EXCH_0001_001` — SEQ_NO fixed `1`; select by `(biz_key, bank_cd, ref_date, ref_ccy_cd, ccy_cd, seq_no)` then **UPDATE else INSERT** (`:198-241`).
  - `EXCH_0010_001` — processes SEQ_NO **1 and 2**; for SEQ 1 *"DON'T UPDATE (preserve first time data)"* (`:215-216`), for SEQ 2 UPDATE, else INSERT.
  - `EXCH_0020_001` — `SEQ_NO = MAX(seq_no)+1` probed by `and bank_cd = ? and ref_date = ? and ccy_cd = ?` — **no `biz_key`, i.e. cross-tenant max** (`:96-104`) — then **INSERT only**, no update branch (`:216-220`).
- **Transaction: one `beginTransaction()` before the tenant loop, one `commitAndCloseQuietly` after** (`:90`, `:250`) → all tenants in a single transaction.
- **The NXISAS call happens once, outside the tenant loop** (`:168`); the same FX payload is written for every tenant.
- Error handling: none per row. Any exception aborts the whole pass and rolls back **all** tenants.

**Refactor behavior [V]** (`ExchangeRateBatchService`): identical bank codes, modules **and class values** — `SHINHAN_CLASS="COMMON"`, `WOORI_CLASS="CORPORATE"` (`:116-122`); tenant filter `findByDelYnAndBusiSttsAndPrdtTypeIn("N","N",[F,M,H])` (`:502`) matching legacy; write modes documented as `UPSERT` / `SKIP_IF_EXISTS(SEQ 1)+UPSERT(SEQ 2)` / `INSERT_NEW MAX+1` (`:71-79`) matching legacy exactly; Woori probe `findHighestSeqNo(WOORI_BANK_CD, inqDate, WOORI_SEQ_PROBE_CCY)` — also cross-tenant, matching legacy.

**Corroboration from live DEV data [V]:** `bank_exch_rate_infm` grouped by bank shows exactly the two legacy signatures the refactor reproduces — `11704020 / CORPORATE / GLOBAL/GBL/BANK_Woori`, `max_seq=15`; `11704088 / COMMON / GLOBAL/GBL/BANK_Shinhan`, `max_seq=2`. The refactor's constants are therefore verified against **live legacy data**, not just legacy source.

**Divergences:**
1. **Transaction boundary (F-8)** — per-tenant (refactor) vs all-tenants-one-tx (legacy). Improves partial-failure isolation; changes semantics; undocumented.
2. **Per-row error isolation** — refactor logs and continues (`:425-426`); legacy aborts the pass.
3. **Woori seq probe currency** — refactor pins `ccy='VND'`; legacy passes a currency from its loop context. The refactor Javadoc acknowledges "a documented difference between the seq requested and the persisted `SEQ_NO` column" (`:310-312`). Low risk, but it is a real behavioral difference, so R3 is **MATCHED WITH INTENTIONAL DIFFERENCE**, not MATCHED.
4. **Schedule** — see §6; verified equal.

### 5.2 R4/R5 — Operation status vs `OPRT_0001/0010_001` → **PARTIAL MATCH** (behavior matches, schedule does not)

**Legacy [V]** (`src/bcs/jex/wabooks/oprt/`):
- Tenant fan-out `busi_stts=NORMAL`, `del_yn='N'`, **`prdt_type in ('F','M')` — HAENGSUNG `'H'` excluded** (`OPRT_0001_001:89`).
- Per tenant: `BUSA_DOC_MSTR_R035` with `hand_reg_yn = 'N'` → distinct tax IDs; `continue` when the tenant has none (`:142-144`).
- Writes an `EXT_TRAN_CALL_HIS` audit row via `TrcoHelper.insertExtTranModuleTable(util, idoCon, "OPRT_0001_001")` (`:146`), then calls OSMC `TRCO_TRCO001_00003` (`:152`).
- `OPRT_0001_001` — **create-only**: `if (existingData == null) { … create }` (`:184-201`). Never updates.
- `OPRT_0010_001` — **update-only**: `OprtSttsInfoDAO.update(existingData)` (`:188`). Never inserts.
- Single transaction across all tenants (`:72`, `:218`).

**Refactor [V]** (`OperationStatusBatchService`): `FAN_OUT_PRDT_TYPES = List.of("F","M")` (`:86`) with an explicit comment *"HAENGSUNG `'H'` is NOT polled"* (`:85`); `BUSI_STTS_NORMAL="N"` (`:83`); seed skips on `existsById(pk)` (`:203`); refresh `continue`s on rows GDT didn't return (`:260`); `ext_tran_call_his` audit written (`:286`, `:298`). The Javadoc even cites the legacy class names and the create-only/update-only split (`:34`, `:62-63`).

**Assessment: behavior parity is excellent and explicitly documented.** The classification is PARTIAL MATCH solely because of the schedule (F-5, §6) and the per-tenant vs single transaction difference (F-8).

### 5.3 R6 — Result check vs `PROCESSING_RESULT_CHECK_BATCH` → **MATCHED WITH INTENTIONAL DIFFERENCE**

**Legacy [V]** (`WECMS_BATCH DEV/…/src/batch/PROCESSING_RESULT_CHECK_BATCH.java`, 420 ln):
- Reads `TB_FIRM_TRAN_R001`, loops rows (`:62`, `:73`).
- `Thread.sleep(per_request_delay)` between requests, with `continue` on failure (`:108-109`, `:183-184`, `:234-235`, `:393`) → **inter-request pacing + leave-row-for-next-pass**.
- PROC_STAT transitions via `WCConst.PROC_ST_REQ` / `PROC_ST_SUCC` / `PROC_ST_FAIL`, writing `TB_FIRM_TRAN` (`_C001`, `_U001`, `_U003`, `_U004`) and `TB_PAY_TARGET` (`_U001`) (`:130`, `:203`, `:271`, `:300-371`).
- **Per-row transactions**: `beginTransaction` / `commit` / `rollback` inside the loop (`:298-332`, `:337-371`).
- Outer `catch (Exception)` → `LOG.error` banner, pass ends (`:398-404`).
- Legacy comment documents the same PROC_STAT vocabulary the refactor uses: `0 : 처리 완료, 3 : 처리실패, 6 : 처리 중, 9: 이체 대기` (`:321`).

**Refactor [V]** (`ResultCheckService`): per-row try/catch that **deliberately leaves `PROC_STAT='6'` pending** (`:222-227`, `:362`), transient gateway codes rethrown so the row stays claimable (`:156-160`), pacing via `DispatchPacer`. Same table set, same status vocabulary, same retry-by-next-pass model.

**Intentional difference:** refactor cron defaults to `"-"` (never fires) vs legacy `*/1 * * * *`. Confirmed inert — `ResultCheckJob.run` is **absent from `shedlock`** **[V]**.

**Note:** `tb_batch_schedule_info` has **no row** for `PROCESSING_RESULT_CHECK_BATCH` **[V]**, so its window is not DB-gated — unlike `FILE_DEPOSIT_TRANSFER_BATCH1` / `NAPAS_REAL_TIME_BATCH`.

### 5.4 R9/R10/R11 — HAENGSUNG vs `ERP_0100_001/002/003` → **PARTIAL MATCH**

**Legacy [V]** (`src/batch/jex/wabooks/erp/`), all three:
- Tenant fan-out `BusiInfmDAO.getRows(… "and prdt_type ='H' and del_yn = 'N'")` — **no `busi_stts` filter** (`ERP_0100_001:67`, `_003:65`, `_004:75`).
- **Per-business, per-section transactions** with `rollback` + *"Skip the update - continue to next section"* (`_001:110-132`, `_003:112-204`).
- `ERP_0100_001` — reads `BANK_EXCH_RATE_INFM_R003` filtered `biz_key + ref_date + coalesce(erp_snd_yn,'N') in ('Y','N')`; writes ERP-side `TB_FCY_EXRATE_U001`/`_C002` chosen by `erp_snd_yn`; then flips `erp_snd_yn` via `BANK_EXCH_RATE_INFM_U003` (`:93`, `:194-262`).
- `ERP_0100_003` — writes `TB_ACCT_HISTORY` + `TB_CARD_APPROVAL`; reads account history with `hand_reg_yn` + `erp_snd_yn` guards (`:51`, `:89-99`).

**Refactor [V]:** `HaengsungScheduledPaymentImportJob` uses `busiInfmRepository.findByPrdtTypeAndDelYn("H","N")` (`:93`) — **exactly** the legacy predicate, `busi_stts` correctly omitted; per-business try/catch + `TenantContext.clear()` in `finally` (`:114-121`).

**Divergences:** the **schedule** (F-3/F-4, §6) is the dominant gap. R10 and R11 also have **no `try/catch`** in the job body, so they produce no aggregated summary or failure counter, unlike legacy's per-section logging.

### 5.5 R7 / R8 / R12 / R13 — **NEW REFACTOR-ONLY JOBS**

- **R7 `StrandedRecoveryJob`** — no legacy twin **[V]**. The R-1 safety net. Its own Javadoc states refactor-originated rows are indistinguishable from JEX-originated rows on a shared DB, so a blind sweep could double-process JEX rows. Double-gated for exactly that reason. Also carries the F-8 finding from [scheduler-audit.md](scheduler-audit.md): `recoverOnce()` delegates to `resultCheckService.runOnce()` (`StrandedRecoveryService:104`) under a **different** lock name than `ResultCheckJob.run`.
- **R8 `StuckPayFileMonitorJob`** — no legacy twin **[V]**. Read-only observability; deliberately split from R7 so monitoring can be enabled without opening the mutating recovery gate. Notably, neither of its properties appears in any `application-*.yml` **[V]**, so unlike every other flag it **cannot** abort startup (its own Javadoc calls this "readiness finding H-1").
- **R12 `NxisasSessionRegistry.evictIdle`** — no legacy twin **[V]**; the legacy tree has no session registry. Purely in-memory `ConcurrentHashMap` housekeeping.
- **R13 `CutoffConfigProvider.scheduledRefresh`** — no legacy twin **[V]**. `TB_CUTOFF_CONFIG` / `TB_CUTOFF_PARAM` appear **neither** in the legacy source tree **nor** in [legacy-table-inventory.md](../references/database/legacy-table-inventory.md) **[V]**. These are refactor-introduced tables, which sits in tension with the root CLAUDE.md rule that refactor-only constructs with no legacy table are not allowed → **OQ-6**.

---

## 6. Schedule and timezone comparison

Legacy `.batch` schedules are **5-field Unix cron** (`min hour dom mon dow`); Spring is **6-field** (`sec min hour …`). All refactor crons are explicitly `zone = "Asia/Ho_Chi_Minh"` **[V]**. Legacy simpleBatch crons carry **no zone** — they follow the WAS host TZ **[?]**.

| Job | Legacy schedule | Refactor schedule | Verdict |
|---|---|---|---|
| EXCH first | 08:30 ICT | `0 30 8 * * ?` ICT | **MATCH** — legacy write at `01:30 UTC` = 08:30 ICT **[V]** |
| EXCH last | hourly 08:30–18:30 ICT | `0 30 8-18 * * ?` ICT | **MATCH** — legacy `mod_dtm 10:30 UTC` = 17:30 ICT **[V]** |
| EXCH Woori | every 30 min 08:30–20:30 ICT | `0 30 8` + `0 0,30 9-20` ICT | **MATCH** — legacy write `10:00 UTC` = 17:00 ICT, `max_seq=15` **[V]** |
| OPRT seed/refresh | **22:00 ICT = 00:00 KST** **[V]** | `0 0 0 * * ?` **ICT** | **MISMATCH — F-5** |
| Result check | `*/1 * * * *`, 24 h | `-` (off) | Intentional |
| FILE_DEPOSIT 1/2 | `*/1 * * * *`, 24 h; DB window `000000–235959` | event-driven | Intentional |
| NAPAS real-time | `* * * * *`, 24 h; DB window `000000–235959` | event-driven | Intentional |
| ERP_0100_001 → R10 | `*/10 **7-19**` | `0 */5 * * * *` **24 h** | **MISMATCH — 2× rate, 24 h vs 13 h** |
| ERP_0100_002 → R9 | `*/5 **7-19**` | `0 */5 * * * *` **24 h** | **Rate matches; window 24 h vs 13 h** |
| ERP_0100_003 → R11 | **`0 7-19`** (hourly) | `0 */5 * * * *` **24 h** | **MISMATCH — ~12× rate** |
| ERP_0100_004 | `*/5 7-19` | — | **MISSING — F-1** |

**On F-5 (the OPRT timezone finding).** Legacy code stamps `DateTimeUtils.utcNow()`, so `reg_dtm` is UTC **[V]**. The three FX jobs' UTC stamps convert cleanly to their documented **ICT** times, which is why R1–R3 verify as MATCH. The OPRT audit rows land at `15:00:01` UTC — that is **22:00 ICT**, and exactly **00:00 Asia/Seoul**. Given memory `project_batch_schedule_tz_antdiv_fixes` ("JEX DB is Asia/Seoul; legacy windows 2 h off ICT"), the most economical reading is that the OPRT entry in JEX Batch Admin is Seoul-anchored while the FX entries are ICT-anchored **[I]**. The refactor's `0 0 0 * * ?` ICT is 17:00 UTC — **2 h off** the observed legacy instant, which is precisely the documented Seoul/ICT skew. A third value complicates it: the refactor's own `oprt-status-*` locks last fired at naive `10:00:00` **[V]**, matching neither. Resolution needs the Jex Studio entry and the deployed `WECMS_OPERATION_STATUS_BATCH_*_CRON` → **OQ-3**.

**On F-3/F-4 (HAENGSUNG).** `HaengsungExchangeRateSyncProperties:28-29` and `HaengsungCardAccountSyncProperties:28-29` both state the legacy cadence is *"Jex-Studio-defined and not in source"* **[V]**. It **is** in source — `web/WEB-INF/jex/simpleBatch/WECMS/ERP_0100_00*.batch` **[V]**. Every HAENGSUNG default was therefore picked without consulting the available legacy schedule, which explains why all three collapsed onto one identical `0 */5 * * * *` (also causing the every-5-minute triple collision in §8).

---

## 7. Shared-table and coexistence risks

**Environment split [V]:** JEX DEV = `10.254.242.254:54311/wecmsvndevdb`; refactor DEV/SIT = `172.168.5.172:5432/wecmsdevdb`. Different instances → **DEV/SIT has no coexistence exposure**. Per memory `project_uat_prod_live_jex_db_target`, the refactor targets the **live JEX DB** in UAT/PROD → that is where every risk below becomes real.

**Legacy is live [V]:** legacy FX wrote to `bank_exch_rate_infm` on **2026-07-28** (Shinhan `013014`, Woori `100009`); legacy OPRT wrote `ext_tran_call_his` on **2026-07-27** (`150001` / `150005`). Legacy `oprt_stts_info.mod_dtm` reaches `20260728064346` while the last batch audit row is from 07-27 — so `OPRT_STTS_INFO` is written by **both** the batch **and** the interactive JEX screen **[I]**, widening the write surface beyond the batch.

**No legacy duplicate-execution guard [V].** No lock table, advisory lock, or claim-row pattern was found in any legacy batch. Legacy safety rests on "one WAS instance runs the schedule" **[I]**.

**ShedLock cannot coordinate (F-7) [V].** `shedlock` contains only the refactor's own lock names; nothing in the legacy JexSimpleBatch/BCS runtime touches that table. Enabling a refactor batch against the live JEX DB gives **zero** mutual exclusion with its legacy twin.

| Shared table | Legacy writer | Refactor writer | Failure mode if both run |
|---|---|---|---|
| `BANK_EXCH_RATE_INFM` | `EXCH_0001/0010/0020/0030_001` | R1/R2/R3 | Duplicate rows or lost updates on the same `(biz_key, bank_cd, ref_date, ref_ccy_cd, ccy_cd, seq_no)`. **Woori is worst:** both sides compute `SEQ_NO = MAX+1` **cross-tenant** and both **INSERT only** → two concurrent rounds collide on the same seq or silently create a duplicate round. The refactor's `INSERT_NEW` collision guard logs-and-skips, so the loss is silent. |
| `BANK_EXCH_RATE_INFM.erp_snd_yn` | `ERP_0100_001` | R10 | Both flip the same flag after pushing to `TB_FCY_EXRATE` → a rate can be pushed twice or marked sent without being pushed. |
| `TB_FCY_EXRATE` (ERP Oracle) | `ERP_0100_001` | R10 | Duplicate/conflicting ERP rate rows; no XA between PG and Oracle. |
| `OPRT_STTS_INFO`, `EXT_TRAN_CALL_HIS` | `OPRT_0001/0010_001` **+ interactive screen** | R4/R5 | Duplicate GDT/OSMC calls (billable external calls), duplicate audit rows. Create-only/update-only split limits row corruption but not call duplication. |
| `TB_FIRM_TRAN`, `TB_PAY_TARGET` | `PROCESSING_RESULT_CHECK_BATCH` | R6 | **Highest impact.** Both drive `PROC_STAT 6→0/3`. Concurrent EFS 203 inquiries on the same `tranSeq` → double status writes, inconsistent transitions, or a row finalized by one side while the other still holds it. |
| `TB_PAY_TARGET` (`'9'` stranded) | — | R7 | Refactor-only sweep cannot distinguish refactor rows from JEX rows → **double dispatch of real payments**. This is why R7 is double-gated. |
| `TB_ACCT_HISTORY`, `TB_CARD_APPROVAL` (ERP Oracle) | `ERP_0100_003` | R11 | Duplicate ERP history/card rows; `erp_snd_yn` race as above. |

**Ownership recommendation during coexistence:** **legacy owns every batch** listed above until each is formally cut over one at a time. The refactor's own scheduler Javadocs already assert this for FX and operation-status; this audit extends the same conclusion to the HAENGSUNG trio and the dispatch jobs.

---

## 8. Locking and concurrency findings

Carried forward from [scheduler-audit.md](scheduler-audit.md), re-verified, with legacy comparison added.

1. **Single-thread pool is load-bearing [V].** `pool.size: 1` means no two refactor jobs can overlap in a JVM — including a job with itself. Legacy had no equivalent coupling: each batch was its own scheduled unit, so a slow legacy batch never delayed another. Raising the pool size would turn item 2 into a live bug.
2. **Cron collisions (refactor-only regression).** 08:30 ICT fires R1+R2+R3; `0 */5` fires R9+R10+R11; 00:00 ICT fires R4+R5. Order within a collision is undefined. **R4→R5 (seed→refresh) has a semantic ordering requirement** that neither the single thread nor the two independent locks enforce. Legacy avoided this by giving each program its own Jex Studio entry.
3. **ShedLock is refactor-node-only (F-7).** Correct for horizontal scaling; useless for legacy coexistence.
4. **Manual/API paths bypass the lock [V].** `ExchangeRateService:100 → ExchangeRateBatchService.runOnDemand()` (`@Transactional(REQUIRES_NEW)`) and the interactive `ErpPaymentRefreshService.importUnreceivedForCurrentTenant()` both execute scheduled business logic with **no** `@SchedulerLock`. A user action can run concurrently with the scheduled twin.
5. **Two entry points, two lock names over one body [V].** `StrandedRecoveryService.recoverOnce()` → `resultCheckService.runOnce()` under `StrandedRecoveryJob.run`, while the same body is guarded by `ResultCheckJob.run` elsewhere. Enabling both R6 and R7 permits two concurrent result-check passes. Latent — both are off.
6. **`lockAtMostFor` too tight on R6 [V].** `PT2M` for a job intended to run every minute and make N sequential EFS 203 calls; legacy paced with `Thread.sleep(per_request_delay)` and had no upper bound at all. A pass exceeding 2 min releases its lock while still running.
7. **Lock durations otherwise proportionate [V].** R8's `PT5M` is justified in its Javadoc (two reads per file, no network I/O); R4/R5's `PT30M` suits a multi-tenant OSMC fan-out; `usingDbTime()` removes host-clock skew.
8. **R12/R13 correctly hold no lock [V].** Both mutate per-JVM in-memory state; every node must run them. A distributed lock here would be a defect.

---

## 9. Environment enablement matrix

**Gating taxonomy [V]:**

| Mechanism | Jobs | Behavior when off |
|---|---|---|
| `@ConditionalOnProperty(matchIfMissing=false)` | R1–R3 (one flag), R4–R5 (one flag), R9, R10, R11 | **Bean absent** — no trigger registered at all |
| Cron value `-` only | R6 | Bean exists, trigger inert |
| Cron `-` **+** runtime service flag | R7, R8 | Double-gated; service body no-ops even with a cron set |
| Runtime `ObjectProvider` null-check | R9, R10, R11 (via `haengsung.oracle.enabled`) | Job fires, logs `skipped`, returns |
| **No flag at all** | **R12, R13** | Always on in every environment |

**Committed vs live [V]:**

| Variable | `batch-dispatch-variable-reference.md` | `.env.sit` | Live `shedlock` evidence |
|---|---|---|---|
| `WECMS_EXCHANGE_RATE_BATCH_ENABLED` | `false` | **`true`** | **firing** (`exch-*` × 3) |
| `WECMS_OPERATION_STATUS_BATCH_ENABLED` | `false` | `false` | **firing** (`oprt-status-*` × 2) |
| `HAENGSUNG_*_ENABLED` | `false` | `false` | **firing** (all 3) |
| `WECMS_DISPATCH_RESULT_CHECK_CRON` | `-` | `-` | absent → never fired ✓ |
| `WECMS_DISPATCH_RECOVERY_ENABLED` | `false` | — | absent → never fired ✓ |
| `wecms.dispatch.stuck-monitor.*` | not present | not present | absent → never fired ✓ |

All 8 flag-gated jobs have fired on node `GroupwareDev` **[V]**, contradicting both committed templates. `batch-dispatch-variable-reference.md` §7 concedes this in writing: *"live DB evidence (shedlock, 2026-07-28) shows ALL of these firing on a deployed node, while .env.uat.example / .env.prod.example say false. The committed templates are not a reliable description of any running environment."*

**Missing variable ⇒ startup failure, not "off" [V].** In `application.yml` and all of `local/sit/uat/prod`, these are bare `${VAR}` with **no inline default**: `WECMS_EXCHANGE_RATE_BATCH_ENABLED`, `WECMS_OPERATION_STATUS_BATCH_ENABLED`, `WECMS_OPERATION_STATUS_BATCH_{SEED,REFRESH}_CRON`, `WECMS_DISPATCH_RESULT_CHECK_CRON`, `HAENGSUNG_ORACLE_ENABLED`, and all three `HAENGSUNG_*_{ENABLED,CRON}` pairs. An unset variable is an unresolvable placeholder → the context fails to start. Only `wecms.dispatch.recovery.{enabled:false,cron:-}` and R8's two properties degrade safely.

No `@RefreshScope`, no actuator `/refresh` → **every flag change requires a restart**; `@Scheduled(cron="${…}")` resolves once at bean creation **[V]**.

---

## 10. Missing or duplicated batch processes

### Missing in refactor — in-scope

| Legacy | What it does **[V]** | Impact |
|---|---|---|
| **`ERP_0100_004`** (976 ln, `*/5 7-19`) | Per-`prdt_type='H'` business: Vendor/Expense → Payroll → Overseas payment export to ERP, each its own transaction, plus three history writes | HAENGSUNG payment data may never reach ERP on a schedule. **May** be intentionally superseded by the event-driven Step-5 dispatch-result write-back (memory `project_haengsung_dispatch_result_writeback`) — nothing in the refactor source says so → **OQ-1** |
| **`EXCH_0030_001`** (407 ln) | Loops **multiple banks × `SEQ_NO` 1..maxSeqNo** with per-SEQ try/catch that continues on error — an FX **backfill / gap-fill** job | R1–R3 only capture the current round. A missed window (outage, restart, `lockAtMostFor` expiry) is **never backfilled** → **OQ-2** |

### Missing in refactor — out of current scope (no action, listed for completeness)

`BATCH_0001_001` (e-invoice), `REPT_0003/0200/0300/0400/0500/0600/0700/0900_001`, `MBMR_0100/0200_001`, `COMM_0500/0800/0900/1000_001`, `SZON_0004/0100_001`, `SZONE_0100_001`, `TRCO_0100_001`, `SALE_0100_001`, `FNSH_0100_001`, `BLTB_0100_001`, `ADMN_0020_003` — ~20 programs **[V]**. Of these only `REPT_0900_001` shows recent activity (`bat_call_his`: 27,787 runs through **20260702**, **17,140 errors**) **[V]** → **OQ-7**.

### Intentionally not scheduled in refactor (event-driven TO-BE)

`FILE_DEPOSIT_TRANSFER_BATCH1` (reception → request-time), `FILE_DEPOSIT_TRANSFER_BATCH2` (execution → 01301-triggered), `NAPAS_REAL_TIME_BATCH` (single transfer → real-time 202) **[V]**, per `ResultCheckJob`'s Javadoc and memory `project_batch_event_driven_tobe_recovery` ("event-driven trigger = APPROVED TO-BE"). Classified **MATCHED WITH INTENTIONAL DIFFERENCE**. Residual: legacy honored `tb_batch_schedule_info` windows per `out_bank_cd`; the refactor's equivalent is `wecms.dispatch.operating-hours.zone` (in-code default `Asia/Ho_Chi_Minh`) → **OQ-5**.

### Duplicated

**No refactor scheduler duplicates another refactor scheduler [V].** The only duplication risk is legacy↔refactor on a shared DB (§7), which is not currently realized because DEV/SIT is a separate instance.

---

## 11. Deployment readiness decision

| Job | DEV/SIT (separate DB) | UAT/PROD (live JEX DB) | Decision |
|---|---|---|---|
| R1 `exch-shinhan-first-time` | **Safe as currently configured** | **Safe only after legacy `EXCH_0001_001` is disabled** | Requires ownership confirmation |
| R2 `exch-shinhan-last-time` | **Safe as currently configured** | **Safe only after legacy `EXCH_0010_001` is disabled** | Requires ownership confirmation |
| R3 `exch-woori` | **Safe as currently configured** | **Safe only after legacy `EXCH_0020_001` is disabled** | Requires ownership confirmation + **OQ-2** (no backfill twin) |
| R4 `oprt-status-seed` | Safe, but **requires schedule correction** | **Safe only after legacy `OPRT_0001_001` is disabled** | **Requires schedule correction** (F-5) |
| R5 `oprt-status-refresh` | Safe, but **requires schedule + ordering correction** | **Safe only after legacy `OPRT_0010_001` is disabled** | **Requires schedule correction** (F-5) + ordering fix vs R4 |
| R6 `ResultCheckJob` | **Must remain disabled** | **Must remain disabled** | **Requires code correction** — raise `lockAtMostFor` (F-6/§8.6) + unify lock name with R7 (§8.5) before any enablement |
| R7 `StrandedRecoveryJob` | **Must remain disabled** | **Must remain disabled** | Requires ownership confirmation — PO / Mr. Kang shared-DB decision |
| R8 `StuckPayFileMonitorJob` | **Safe to enable** (read-only) | **Safe to enable** (read-only) | Safe — the one dispatch job with no mutation risk |
| R9 `HaengsungScheduledPaymentImportJob` | Safe, but **requires schedule correction** | **Safe only after legacy `ERP_0100_002` is disabled** | **Requires schedule correction** (24 h → `7-19`) |
| R10 `HaengsungExchangeRateSyncJob` | Safe, but **requires schedule correction** | **Safe only after legacy `ERP_0100_001` is disabled** | **Requires schedule correction** (`*/5`→`*/10`, 24 h→`7-19`) |
| R11 `HaengsungCardAccountSyncJob` | Safe, but **requires schedule correction** | **Safe only after legacy `ERP_0100_003` is disabled** | **Requires schedule correction** (`*/5`→hourly, 24 h→`7-19`) |
| R12 `evictIdle` | **Safe as currently configured** | **Safe as currently configured** | Safe |
| R13 `scheduledRefresh` | **Safe as currently configured** | **Safe as currently configured** | Safe — but **OQ-6** on table provenance |
| — `ERP_0100_004` gap | n/a | n/a | **Requires ownership confirmation** — **OQ-1** |

**Aggregate:** nothing blocks DEV/SIT today. **No** mutating refactor batch is cleared for UAT/PROD. Only **R8** is unconditionally safe to enable anywhere. **R6 and R7 must remain disabled** regardless of environment until the code corrections in §8.5–8.6 land.

---

## 12. Open questions requiring PO, Infra, or legacy batch-owner confirmation

| ID | Question | Owner | Blocks |
|---|---|---|---|
| **OQ-1** | Is `ERP_0100_004` (WeCMS→ERP vendor/expense/payroll/overseas export, `*/5 7-19`) intentionally replaced by the event-driven Step-5 dispatch-result write-back, or is it a genuine gap? If replaced, record it in the refactor source. | PO (Mr. Jung) + HAENGSUNG batch owner | HAENGSUNG cutover |
| **OQ-2** | Does `EXCH_0030_001` (multi-bank × multi-SEQ FX backfill) still run, and does the refactor need a backfill equivalent before legacy FX is disabled? Without it a missed window is unrecoverable. | Legacy batch owner + PO | FX cutover |
| **OQ-3** | What is the **actual** Jex Studio schedule for `OPRT_0001_001` / `OPRT_0010_001`, and in which timezone? Observed legacy fire = `15:00:01 UTC` (22:00 ICT / 00:00 KST); refactor default = 00:00 ICT; refactor observed lock = naive `10:00`. Three values, no reconciliation. Also supply the deployed `WECMS_OPERATION_STATUS_BATCH_{SEED,REFRESH}_CRON`. | Legacy batch owner + Infra | R4/R5 schedule correction |
| **OQ-4** | Confirm the deployed flag/cron values on node `GroupwareDev` and each UAT/PROD node. The committed `.env.*` files are contradicted by `shedlock` and cannot be treated as evidence. | Infra / Ops | All enablement decisions |
| **OQ-5** | Legacy honored `tb_batch_schedule_info` windows per `out_bank_cd` (`970424`, `970457`) for `FILE_DEPOSIT_TRANSFER_BATCH1` / `NAPAS_REAL_TIME_BATCH`, and has **no row** for `PROCESSING_RESULT_CHECK_BATCH` / `FILE_DEPOSIT_TRANSFER_BATCH2`. Does the refactor's event-driven path honor the same per-bank windows and the same zone? | PO + Infra | Dispatch cutover |
| **OQ-6** | `TB_CUTOFF_CONFIG` / `TB_CUTOFF_PARAM` exist in neither the legacy tree nor `legacy-table-inventory.md`. Root CLAUDE.md forbids refactor-only tables with no legacy backing. Were these PO-approved, and should the inventory be updated? | PO (Mr. Jung) | Inventory compliance |
| **OQ-7** | `REPT_0900_001` shows 17,140 errors across 27,787 runs, still running as of `20260702`. Is this known/accepted? It is the batch behind Daily Cash. | Legacy batch owner | Reporting accuracy |
| **OQ-8** | `WECMS_BATCH DEV/` is a **fifth code tree** absent from the root [CLAUDE.md](../../../CLAUDE.md) workspace layout, with no stated VCS or ruleset. Should it be added, and which tree rules apply? | Sonny | Workspace governance |
| **OQ-9** | Is the refactor UAT/PROD DB truly the live JEX batch DB? `batch-dispatch-variable-reference.md` §8 flags this as **BLOCKING** and says the real URLs are not committed anywhere. Every §7 risk depends on the answer. | Infra / Ops | All UAT/PROD enablement |
| **OQ-10** *(added 2026-07-28 during remediation)* | **What timezone is the legacy `7-19` hour window in?** The three `ERP_0100_00*.batch` crons carry no zone — they follow the legacy WAS host TZ, which this audit could not establish. The refactor now anchors the corrected windows to `Asia/Ho_Chi_Minh`. If the legacy host runs `Asia/Seoul`, legacy 07–19 is actually **05:00–17:00 ICT**, so the refactor window would be two hours late at both ends. The **cadence** (every 10 min / every 5 min / hourly) is verified exact; only the window boundary is open. Supply the legacy WAS `user.timezone` (or the container TZ). | Infra + HAENGSUNG batch owner | HAENGSUNG UAT/PROD enablement |

---

## 13. Recommended actions in priority order

1. **Answer OQ-9 first.** Whether the refactor UAT/PROD DB is the live JEX batch DB determines whether §7 is theoretical or urgent. Nothing else should be enabled until this is settled in writing.
2. **Correct the three HAENGSUNG schedules to legacy (F-3/F-4)** — `ERP_0100_001` → `*/10` within `7-19`; `ERP_0100_002` → `*/5` within `7-19`; `ERP_0100_003` → **hourly** within `7-19`. Fix the two Javadocs that claim the legacy cadence is "not in source", and cite the `.batch` files. This also breaks the every-5-minute triple collision for free.
3. **Resolve OQ-3, then correct R4/R5's schedule**, and give `refresh` a distinct time from `seed` (or fold both into one method under one lock) so the seed→refresh ordering is guaranteed rather than incidental.
4. **Resolve OQ-1 and OQ-2** — the two genuinely missing legacy jobs. `EXCH_0030_001`'s absence is the more insidious: FX gaps would accumulate silently after any missed window.
5. **Before enabling R6 or R7:** raise `ResultCheckJob.lockAtMostFor` above the worst-case pass duration, and unify the lock name shared by `ResultCheckJob.run` and the `StrandedRecoveryService` delegation.
6. **Close the ShedLock bypasses** — wrap `runOnDemand()` and the interactive `importUnreceivedForCurrentTenant()` in the same lock name as their scheduled twins (ShedLock's `LockingTaskExecutor` covers non-`@Scheduled` entry points).
7. **Write down the coexistence ownership decision per batch**, table by table, using §7. The FX and operation-status schedulers already carry this in their Javadocs; the HAENGSUNG trio and the dispatch jobs do not.
8. **Give every scheduler flag an inline `:false` / `:-` default** so a missing env var disables a job instead of breaking startup (§9). R8 already models the safe pattern.
9. **Document the `pool.size: 1` contract** next to the setting — it is load-bearing for correctness, not a tuning knob (§8.1).
10. **Document the F-8 transaction-boundary divergence** (per-tenant vs legacy all-tenants-one-tx) in both batch services, since it changes partial-failure semantics versus legacy.
11. **Add `WECMS_BATCH DEV/` to the root CLAUDE.md workspace layout** with its ruleset and VCS (OQ-8), and externalise the three hardcoded FX crons for parity with every other job.
12. **Re-run this audit before any cutover.** `StuckPayFileMonitorJob` appeared mid-audit (F-0); the inventory is a moving target.

---

## Appendix — primary evidence index

**Refactor source** (`wecms-api/src/main/java/com/kosign/wecms/`): `config/SchedulerLockConfig.java:33-43` · `domain/banking/nxisas/NxisasConfig.java:27` · `domain/banking/service/ExchangeRateBatchScheduler.java:49-90` · `domain/banking/service/ExchangeRateBatchService.java:71-79,116-139,278-312,425-426,502` · `domain/operation/scheduler/OperationStatusBatchScheduler.java:44-71` · `domain/operation/service/OperationStatusBatchService.java:34,62-63,83-86,137-152,203,260` · `domain/payment/dispatch/scheduler/{ResultCheckJob:39-40,StrandedRecoveryJob:32-33,StuckPayFileMonitorJob:40-41}.java` · `domain/payment/dispatch/service/{ResultCheckService:156-160,204-227,362 , StrandedRecoveryService:66-67,90-140}.java` · `domain/payment/haengsung/{batch/HaengsungScheduledPaymentImportJob:72-121 , exchangerate/HaengsungExchangeRateSyncJob:49-61 , cardaccount/HaengsungCardAccountSyncJob:49-61}.java` + the three `*Properties.java:26-35` · `domain/banking/nxisas/session/NxisasSessionRegistry.java:68-81` · `domain/payment/internaltransfer/service/CutoffConfigProvider.java:89-137`

**Refactor config:** `src/main/resources/application.yml:33-38,93-101,125-141` · `application-{local,sit,uat,prod}.yml` · `batch-dispatch-variable-reference.md` §7–§8 · `.env.sit:50,57,71,123,128` · `build.gradle:53-60`

**Legacy — JEX main app** (`WECMS DEV/WECMS/`): `src/bcs/jex/wabooks/exch/EXCH_000{1,10,20,30}_001.java` · `src/bcs/jex/wabooks/oprt/OPRT_00{01,10}_001.java` · `src/batch/jex/wabooks/exch/EXCH_0001_001.java:48-51` (shim) · `src/batch/jex/wabooks/erp/ERP_0100_00{1,2,3,4}.java` · `web/WEB-INF/jex/simpleBatch/WECMS/ERP_0100_00{1,2,3,4}.batch` · `WECMS_ETC/xml/service/{BCS,BAT}/`

**Legacy — firm-banking app** (`WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/`): `src/batch/PROCESSING_RESULT_CHECK_BATCH.java` · `src/batch/{FILE_DEPOSIT_TRANSFER_BATCH1,FILE_DEPOSIT_TRANSFER_BATCH2,NAPAS_REAL_TIME_BATCH}.java` · `web/WEB-INF/jex/simpleBatch/WECMS_BATCH/*.batch` · `web/WEB-INF/properties/wecms_batch{,_HS}.properties`

**Live queries (read-only):** refactor DB — `shedlock` (8 rows, node `GroupwareDev`), `current_setting('TimeZone')`=`Asia/Phnom_Penh`, `shedlock` time columns = `timestamp without time zone`. JEX DEV DB — `bank_exch_rate_infm` grouped by `(bank_cd, class, module)`; `oprt_stts_info` + `ext_tran_call_his` OPRT aggregates; `bat_call_his` by `job_id`; `tb_batch_schedule_info` (4 rows); `information_schema` column/schema lookups.
