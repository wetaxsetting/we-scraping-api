# Scheduler & Trigger Flow

> Part of the [WeCMS Batch](README.md) feature set. How dispatch work is *triggered*: the one scheduled job, the `afterCommit` event hooks that replaced polling, and the ShedLock configuration.
>
> **Status:** source-verified 2026-06-24.

---

## 1. The event-driven model

The dispatch engine has **one** scheduled job and **two** event hooks. The legacy polling loops (scan `PROC_STAT='9'` / `PROC_STEP=1` on a timer) are gone — real-time and bulk transfers are triggered the moment the originating payment commits, and file completion is driven by inbound gateway callbacks.

```mermaid
flowchart TD
    subgraph Event-driven (no timer)
      TX[Payment tx commit] --> RR[RealtimeDispatchRegistrar.registerAfterCommit]
      TX --> AR[DispatchAutoReceiveRegistrar.registerAfterCommit]
      RR -->|afterCommit| RDS[RealtimeDispatchService.dispatch]
      AR -->|afterCommit| BRS[BulkReceptionService.receive]
      CB1[01301 callback] --> S1[BulkReceptionResultService → inline EFS 302]
      CB2[01302 callback] --> S2[BulkExecutionResultService]
    end
    subgraph Scheduled (the only timer)
      J[ResultCheckJob @Scheduled + @SchedulerLock] --> RCS[ResultCheckService.runOnce]
    end
```

| Mechanism | Class | What it triggers |
|---|---|---|
| `afterCommit` (TransactionSynchronization) | `RealtimeDispatchRegistrar` | `RealtimeDispatchService.dispatch(bizKey, tranDt, tranSeq)` — one real-time target |
| `afterCommit` (TransactionSynchronization) | `DispatchAutoReceiveRegistrar` | `BulkReceptionService.receive(tranDt, bizKey, fileSeqNo)` — one file batch |
| Inbound HTTP callback | `BulkReceptionResultController` | 01301 reception → reconcile + inline EFS 302 |
| Inbound HTTP callback | `BulkExecutionResultController` | 01302 execution → result-file ingest |
| `@Scheduled` cron | `ResultCheckJob` | `ResultCheckService.runOnce()` — 203 reconcile sweep |

---

## 2. afterCommit dispatch flows

Both registrars register a Spring `TransactionSynchronization` so the dispatch runs **after** the payment transaction commits — never inside its uncommitted state. The entry point for the real-time path is `TransferDispatchExecutor.writeApiPath()`; for the file path, the file-path executors call the auto-receive registrar.

```mermaid
sequenceDiagram
    participant Exec as TransferDispatchExecutor
    participant Reg as RealtimeDispatchRegistrar
    participant Sync as TransactionSynchronizationManager
    participant Svc as RealtimeDispatchService
    Exec->>Exec: write TB_PAY_TARGET PROC_STAT='9'
    Exec->>Reg: registerAfterCommit(bizKey, tranDt, tranSeq)
    Reg->>Sync: register(synchronization)
    Note over Sync: payment tx COMMITS
    Sync-->>Svc: afterCommit() → dispatch(...)
    Svc->>Svc: re-read target; proceed only if still '9' & no firm-tran row
```

**Properties of the hook:**
- **Idempotent** — `dispatch` re-reads the target and proceeds only if it is still `PROC_STAT='9'` and no `TB_FIRM_TRAN` row exists for the triple (`existsByTranDtAndBizKeyAndTranSeq`).
- **Isolated** — `dispatch` runs in `@Transactional(propagation = REQUIRES_NEW)`; a dispatch failure commits a FAIL status and never rolls back the committed payment.
- **Gated** — the real-time hook only fires when `wecms.dispatch.realtime-dispatch=true`; the file hook when `wecms.dispatch.auto-receive=true`. With the flags OFF (default), targets simply stay at `PROC_STAT='9'` (legacy idle behaviour).

---

## 3. The scheduled job — ResultCheckJob

The single `@Scheduled` method in the dispatch tree (`ResultCheckJob`, a `@Component`):

```java
@Scheduled(cron = "${wecms.dispatch.result-check.cron:-}", zone = "Asia/Ho_Chi_Minh")
@SchedulerLock(name = "ResultCheckJob.run", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
public void run() {
    log.debug("ResultCheckJob.run fired");
    resultCheckService.runOnce();
}
```

| Aspect | Value |
|---|---|
| **Cron property** | `wecms.dispatch.result-check.cron`, default `"-"` |
| **Default behaviour** | `"-"` is not a valid schedule → the job **never fires** (so the refactor never co-processes live 202 rows on the shared dev DB) |
| **When enabled** | typically every minute; set a real cron only in the environment that owns the real-time traffic |
| **Time zone** | `Asia/Ho_Chi_Minh` (Vietnam local) — so the "today" date filter aligns with the bank business day |
| **Work** | select `TB_FIRM_TRAN` rows `TRAN_TP='202' AND PROC_STAT='6'`, today, older than ~2 min → EFS 203 → `reflectOutcome` writes `TB_FIRM_TRAN` 6→0/3 and propagates to `TB_PAY_TARGET` |

> **Known edge case** (`ResultCheckService`, gap audit §6.6): the "today + `HHmmss` string compare" age filter degenerates across the midnight boundary. Fix to a `RQ_DTTM`/timestamp range before enabling the cron in PROD. There is no per-row retry — a still-pending row stays at `PROC_STAT='6'` and is re-examined next tick.

---

## 4. ShedLock configuration

`@Scheduled` alone would let every application instance run the job on each tick. ShedLock ensures only one node runs it, using a DB lock.

```java
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
```

| Aspect | Value |
|---|---|
| **Provider** | `JdbcTemplateLockProvider` over the app `DataSource`, `.usingDbTime()` (DB clock, not node clock) |
| **Backing table** | `shedlock` (PostgreSQL) — a refactor-owned infrastructure table flagged non-legacy in the table inventory |
| **Per-job lock** | `@SchedulerLock(name="ResultCheckJob.run", lockAtMostFor="PT2M", lockAtLeastFor="PT5S")` |
| **Default ceiling** | `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")` |
| **Dependencies** | `net.javacrumbs.shedlock:shedlock-spring:5.16.0` + `shedlock-provider-jdbc-template:5.16.0` |

`lockAtMostFor` (PT2M) is the safety release if a node dies mid-run; `lockAtLeastFor` (PT5S) prevents two nodes both grabbing the lock on a fast tick. `@EnableScheduling` is enabled application-wide (in `NxisasConfig`); `SchedulerLockConfig` only adds the lock provider.

> **Durability caveat.** The lock is DB-backed (survives restart, shared across instances) — good. This is the dispatch tree's *only* durable-state scheduling concern; the auth-side in-memory revocation/rate-limit stores are unrelated.

---

## 5. Trigger summary

| Trigger | Type | Gate / schedule | Default state |
|---|---|---|---|
| Real-time dispatch | `afterCommit` | `wecms.dispatch.realtime-dispatch` | OFF |
| File / bulk reception | `afterCommit` | `wecms.dispatch.auto-receive` | OFF |
| Depositor inquiry | synchronous call | `wecms.dispatch.recipient-inquiry` | OFF |
| 01301 / 01302 callbacks | inbound HTTP | always mounted; crypto + IP allow-list | mounted (mocks throw on outbound legs) |
| Result-check sweep | `@Scheduled` + ShedLock | `wecms.dispatch.result-check.cron` | `"-"` (never fires) |

See [deployment-guide.md](deployment-guide.md) for the order to enable these, and [firm-banking-dispatch.md](firm-banking-dispatch.md) for what each flow does once triggered.
