# Payment Dispatch — Active Call Graph

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Branch:** `wecms-ui-fixes` (uncommitted working tree)
**Purpose:** the single reachable dispatch path for each payment module, established by reading constructor
injection sites rather than by name similarity. Written as the evidence base for the
`TransferDispatchExecutor` dead-code conclusion (see §4) and as the map the integration-test audit
([../testing/integration-test-audit.md](../testing/integration-test-audit.md)) scores coverage against.

Every arrow below is a verified `private final` injection or a direct method call, with the file:line
that proves it.

---

## 1. Executor ownership — five modules, five executors, no shared one

| Module | Entry service | Executor | Injection site |
|---|---|---|---|
| Internal Transfer | `InternalTransferDispatchService` | `InternalTransferDispatchExecutor` | [InternalTransferDispatchService.java:77](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchService.java#L77) |
| Vendor Payment | `VendorPaymentService` | `VendorDispatchExecutor` | [VendorPaymentService.java:168](../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java#L168) |
| Expense Payment | `ExpensePaymentService` | `ExpenseDispatchExecutor` | [ExpensePaymentService.java:168](../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java#L168) |
| Payroll Payment | `PayrollPaymentService` | `PayrollDispatchExecutor` | [PayrollPaymentService.java:161](../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentService.java#L161) |
| Overseas Payment | `OverseasPaymentService` | `OverseasDispatchExecutor` | [OverseasPaymentService.java:108](../../src/main/java/com/kosign/wecms/domain/payment/service/OverseasPaymentService.java#L108) |

**No module injects a shared executor.** This is the primary evidence for §4.

## 2. Which modules have which rail

Determined by which registrar each executor injects — a module that never injects
`RealtimeDispatchRegistrar` has **no API/real-time rail at all**.

| Executor | `RealtimeDispatchRegistrar` (API rail) | `DispatchAutoReceiveRegistrar` (file rail) | Rails |
|---|---|---|---|
| `InternalTransferDispatchExecutor` | ✅ `:155` | ✅ `:152` | both |
| `VendorDispatchExecutor` | ✅ `:131` | ✅ `:130` | both |
| `ExpenseDispatchExecutor` | ✅ `:128` | ✅ `:127` | both |
| `PayrollDispatchExecutor` | ❌ — | ✅ `:132` | **file only** |
| `OverseasDispatchExecutor` | ❌ — | ✅ `:164` | **file only** (SWIFT) |

Two consequences worth stating explicitly, because they change what needs testing:

- **Payroll cannot take the API rail.** There is no code path from `PayrollDispatchExecutor` to
  `TB_PAY_TARGET`. "VCB never dispatches via the API path" is therefore *structurally* guaranteed for
  Payroll, not merely asserted by a test. (Payroll additionally rejects VCB VND outright —
  `WBE_PAYROLL_VCB_VND_NOT_SUPPORTED`, legacy `:179-182`.)
- **Overseas is SWIFT/file-only**, which is why `BANK_CHNL_DIV` and the API-rail `ANT_DIV_CD` rules
  do not apply to it.

## 3. The two rails end-to-end

```
{module} controller
  └─ {Module}Service / InternalTransferDispatchService      approval gate (*DispatchApprovalGateTest)
       └─ {Module}DispatchExecutor                          routing decision: API vs File
            │                                                ANT_DIV_CD, BANK_CHNL_DIV, TRAN_DT/REG_TM (UTC)
            │
            ├── API / real-time rail  [Internal Transfer · Vendor · Expense]
            │   RealtimeDispatchRegistrar.registerAfterCommit(bizKey, tranDt, tranSeqs)
            │     └─ TransactionSynchronization.afterCommit          per-seq try/catch, never rethrows
            │         └─ RealtimeDispatchService.dispatch(bizKey, tranDt, tranSeq)
            │              ├─ OperatingHoursService.evaluateRealtime(outBankCd, antDivCd)
            │              │    CUTOFF / NO_CRITERIA → PROC_STAT='3' + OpsAlertService
            │              │    SKIP                 → leave at '9', no alert
            │              ├─ TbPayTargetRepository.claimForDispatch(...)   ATOMIC '9'→'6'  ← the G2 claim
            │              │    0 rows ⇒ another driver won ⇒ no-op, no transfer
            │              ├─ RealtimeTransferService
            │              │    Woori + non-VND → EFS 204 (FX rate) first, then EFS 202
            │              │    RC=G999 / no response → PENDING, rows stay '6'
            │              └─ DispatchResultTxService   atomic firm-tran + pay-target finalize
            │
            └── File / bulk rail  [all five]
                DispatchAutoReceiveRegistrar.registerAfterCommit(tranDt, bizKey, fileSeqNo)
                  └─ afterCommit  (gated on wecms.dispatch.auto-receive)
                      └─ BulkReceptionService.receive(tranDt, bizKey, fileSeqNo)
                           ├─ read TB_PAY_FILE_MST; if PROC_STAT != '9' → no-op
                           │    (fast-path early-out only; advisory, NOT the duplicate guard)
                           ├─ BulkTransferValidator.validate(...)     network/currency, whole-file
                           ├─ OperatingHoursService.evaluateBulk(outBankCd, presentChannels)
                           │    presentChannels ← AntDivChannel.fromStorageCode per detail row
                           │    SKIP returns here, leaving the row '9' — must NOT claim
                           ├─ BulkDispatchClaimTxService.claimForReception(...)   ATOMIC '9'→'6'
                           │    REQUIRES_NEW, COMMITS before any egress; 0 rows ⇒ return, NO EGRESS
                           │    ← H-4 fix (2026-07-29); mirrors legacy commit-before-upload
                           │      (FILE_DEPOSIT_TRANSFER_BATCH1 :696 commit → :700 SFTP)
                           ├─ per split file, in its OWN REQUIRES_NEW transaction:
                           │    TB_FIRM_FILE_MST insert (step 0 / stat 6)
                           │    FixedLengthCodec.encode → local file
                           │    SftpClient.upload            ← egress
                           │    EFS 301 (reception request)  ← egress
                           └─ inbound callbacks:
                                01301 → BulkReceptionResultService → EFS 302 inline → step 2
                                01302 → BulkExecutionResultService → parse result file → step 3
```

### Scheduled / recovery drivers (all cron-disabled by default)

Re-verified 2026-07-29. **Exactly three** dispatch schedulers exist; `BulkExecutionRetryJob` was
**withdrawn** (it was the periodic sweep K9 explicitly declined — see
[../audits/legacy-batch2-ownership-verification.md](../audits/legacy-batch2-ownership-verification.md)).

| Job | Cron property (default) | Capability | Drives |
|---|---|---|---|
| `ResultCheckJob` | `wecms.dispatch.result-check.cron` (`-`) | **result inquiry only** — `ResultCheckService` references `EFS_203` and no `EFS_202`/`301`/`302`, so it cannot submit or resubmit a transfer | aged 202 rows → EFS 203 |
| `StrandedRecoveryJob` | `wecms.dispatch.recovery.cron` (`-`) **+** `recovery.enabled` (`false`) | **recovery** — can drive a *first* send for a row still at `'9'`; cannot resend a sent one, because every re-drive passes the atomic claim and `'6'`/`'0'`/`'3'` are unclaimable | `RealtimeDispatchService.dispatch`, `BulkReceptionService.receive`, `ResultCheckService.runOnce` |
| `StuckPayFileMonitorJob` | `wecms.dispatch.stuck-monitor.cron` (`-`) **+** `.enabled` (`false`) | **monitoring / alerting — read-only**, performs no writes | `StuckPayFileMonitorService` |

The three HAENGSUNG jobs and `CutoffConfigProvider`'s `fixedDelay` refresh are **not** legacy
payment-batch replacements: the former are ERP import/sync (own `enabled=false` flags), the latter is
config maintenance. Neither submits money.

`StrandedRecoveryService` is a **second concurrent driver** of both rails
([StrandedRecoveryService.java:61-62](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/StrandedRecoveryService.java#L61-L62)).
Both rails are now safe against it: each has an atomic conditional claim that de-dupes before egress
(API: `claimForDispatch`; bulk: `claimForReception`). Until 2026-07-29 the bulk rail had none, and this
sweep was the concrete way H-4 became reachable — see
[bulk-dispatch-concurrency-fix.md](bulk-dispatch-concurrency-fix.md) §12.

### Egress points (exactly four)

| Egress | Class | Offline stand-in | Flag |
|---|---|---|---|
| EFS/VAN gateway (201/202/203/204/301/302/MSG) | `RestClientEfsApiClient` | `MockEfsApiClient` (throws) | `wecms.dispatch.efs.enabled` |
| SFTP upload/download/move | `JschSftpClient` | `MockSftpClient` (throws) | `wecms.dispatch.sftp.enabled` |
| Ops alert (Slack via `WGVN_MSG_SEND_001`) | `OpsAlertService` → `EfsApiClient` | same pair | `wecms.dispatch.alert.enabled` |
| Inbound callbacks (01301/01302) | `Bulk*ResultController` | — (ingress) | `wecms.dispatch.callback.protection` |

Mutual exclusivity of the two client pairs is now pinned by
`DispatchExternalClientWiringTest` (10 tests).

## 4. `TransferDispatchExecutor` — deleted, deletion verified

**Verdict: correctly dead; deletion is complete.** Checked against the full reachability list rather
than a name grep:

| Mechanism | Result |
|---|---|
| Constructor / field / setter injection | **none** — `grep -rn "TransferDispatchExecutor" src/main` returns only comment prose (9 hits, all `{@code}`/`//`) |
| Lowercase bean-name reference (`transferDispatchExecutor`) | **none** in `src/main` or `src/test` |
| `@Qualifier` / `@Primary` / `@Bean` factory method | **none** |
| `ApplicationContext.getBean` / bean-name lookup / reflection | **none** — no `getBean(` reference anywhere |
| Strategy map / `List<Interface>` collection injection | **n/a** — it implemented no interface |
| `@Scheduled` / `@EventListener` / `@Async` / callback registry | **none** |
| YAML / properties / SpEL / XML config | **none** — no `wecms.*` key names it |
| Reachable REST path | **none** — every dispatch endpoint routes to a per-module executor (§1) |
| Test-only usage | was the sole constructor caller; deleted with the class |
| Compilation after removal | `compileJava` + `compileTestJava` **exit 0** |

Its constants survive in the five active executors, and its removal deleted two *wrong* behaviours
rather than losing any: `BANK_PRTN_ID_PLACEHOLDER = "DEFAULT"` (the active executors resolve the real
`TB_FIRM_CONF.BANK_PRTN_ID`) and a `resolveAntDivCd` that defaulted blank codes to `INTER` (the active
path routes through `AntDivChannel`, which fails closed). Full comparison:
[../reports/dead-code-architecture-review.md](../reports/dead-code-architecture-review.md) §5.1.

**Fixed in this pass:** the two dangling `{@link …TransferDispatchExecutor}` javadoc references in
`TbPayTarget` and `TbPayFileMst` — `javac` ignored them but a `javadoc` build would fail on an
unresolvable reference. Both now name the five active executors as `{@code}`.

## 5. What the graph says about test coverage

The graph has three junctions where a defect cannot be caught by a Mockito test of either side:

1. **`registerAfterCommit` → `afterCommit` → service** — the transaction-synchronization hand-off.
   Tested by asserting the hook is registered (`*RegistrarTest`), never by committing a real
   transaction.
2. **`claimForDispatch` / `claimForReception`** — atomicity is a property of the SQL, and the callers
   stub the row count. Pinned statically by `DispatchClaimQueryContractTest` (both rails), and
   behaviourally by `BulkReceptionConcurrentClaimTest`'s real two-thread race. PostgreSQL's row locking
   itself is still not executed (no test DB).
3. ~~**`receive()`'s read-then-check**~~ — **CLOSED 2026-07-29** by the atomic claim (H-4).

See [../testing/integration-test-audit.md](../testing/integration-test-audit.md) §5 for the
flow-by-flow matrix.

## 6. Legacy batch → refactor mapping (re-verified 2026-07-29, post-H-4)

Mr. Kang's approved **four-legacy-batches → one scheduled replacement** mapping, re-traced from each
legacy `.batch` schedule to its final refactor service after the H-4 atomic-claim fix. Every legacy
batch polled `*/1 * * * *`
(`WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/web/WEB-INF/jex/simpleBatch/WECMS_BATCH/*.batch`);
**three of the four are no longer scheduled at all.**

| Legacy batch | Legacy responsibility | Refactor replacement | Trigger model | Scheduled? | External submission? | Verification |
|---|---|---|---|---:|---:|---|
| `NAPAS_REAL_TIME_BATCH` | Realtime payment dispatch | `RealtimeTransferService` (driven by `RealtimeDispatchService`) | Event-driven | No | Yes | Entry [RealtimeDispatchRegistrar.java:63-66](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchRegistrar.java#L63-L66) (`afterCommit`) → [RealtimeDispatchService.java:112](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L112); atomic claim [:221](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L221) → [TbPayTargetRepository.java:32-39](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/repository/TbPayTargetRepository.java#L32-L39); EFS 202 [:269-270](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L269-L270). No `@Scheduled` in either class. |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | Build and submit bulk files | `BulkReceptionService` | Request-time / after-commit | No | Yes | Entry [DispatchAutoReceiveRegistrar.java:63-65](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/DispatchAutoReceiveRegistrar.java#L63-L65) → [BulkReceptionService.java:198](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L198); claim commits pre-egress [:319](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L319) → [TbPayFileMstRepository.java:146-153](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/repository/TbPayFileMstRepository.java#L146-L153); SFTP [:641](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L641); EFS 00301 [:655](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L655). All five modules: `InternalTransferDispatchExecutor:421`, `PayrollDispatchExecutor:292`, `VendorDispatchExecutor:335`, `ExpenseDispatchExecutor:325`, `OverseasDispatchExecutor:322`+`:409`. |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | Continue processing after reception | Inline 00302 after successful 01301 | Callback-driven | No | Yes | Reception advance [BulkReceptionResultService.java:210-217](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L210-L217) → inline issue [:234](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L234) → [:274](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L274); EFS 00302 [:292](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L292). Duplicate control: step-0 lookup [:149-158](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L149-L158) + ledger probe [:275-280](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L275-L280). No `@Scheduled` in the file; no BATCH2 bean, cron or lock identity exists. |
| `PROCESSING_RESULT_CHECK_BATCH` | Poll pending transaction results | `ResultCheckJob` | Scheduled | Yes | No new transfer | [ResultCheckJob.java:99](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L99) (`cron` default `-`, zone `Asia/Ho_Chi_Minh`), ShedLock [:100-101](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L100-L101), delegates [:104](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L104); the service's only gateway call is EFS 203 — [ResultCheckService.java:172](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/ResultCheckService.java#L172). |

### Payment-related `@Scheduled` inventory — capability classification

All 14 `@Scheduled` annotations in `src/main/java` were enumerated; the payment-related ones classify as:

| Job | Class | Capability | New bank submission? | Re-submit existing? | Inquire only? | Alert only? |
|---|---|---|---:|---:|---:|---:|
| `ResultCheckJob` | result inquiry | EFS 203 for aged `202`/`'6'` rows | **No** | **No** | **Yes** | no |
| `StrandedRecoveryJob` | recovery (refactor-only safety net, **not** a retained legacy batch) | re-drives `'9'` rows through the same claim-bearing entry points | Yes — a *first* send only | **No** (`'6'`/`'0'`/`'3'` are unclaimable) | no | no |
| `StuckPayFileMonitorJob` | monitoring / alerting | read-only sweep + `OpsAlertService` | **No** | **No** | no | **Yes** |
| `HaengsungScheduledPaymentImportJob` | ERP import | pulls ERP rows into WeCMS tables | **No** | **No** | no | no |
| `HaengsungCardAccountSyncJob` · `HaengsungExchangeRateSyncJob` | data sync | card/account and FX-rate sync | **No** | **No** | no | no |
| `CutoffConfigProvider` (`fixedDelay`) | cache maintenance | refreshes cut-off config | **No** | **No** | no | no |
| `NxisasSessionRegistry` (`fixedDelay`) | cache maintenance | scraping-session eviction | **No** | **No** | no | no |
| `ExchangeRateBatchScheduler` (×4) · `OperationStatusBatchScheduler` | banking / add-on scraping | not payment dispatch | **No** | **No** | no | no |

`StrandedRecoveryJob` is a **refactor safety mechanism, not a retained legacy batch.** It is
independently gated and disabled by default: cron `wecms.dispatch.recovery.cron` defaults to `-` AND
`wecms.dispatch.recovery.enabled` defaults to `false`
([StrandedRecoveryJob.java:33](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/StrandedRecoveryJob.java#L33),
[StrandedRecoveryService.java:75-76](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/StrandedRecoveryService.java#L75-L76)).
It never bypasses the normal path's duplicate control — it re-enters through
`RealtimeDispatchService.dispatch` ([:171](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/StrandedRecoveryService.java#L171))
and `BulkReceptionService.receive` ([:197](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/StrandedRecoveryService.java#L197)),
so it contends on the same atomic claims.

### Tests proving the mapping

[`LegacyBatchMappingVerificationTest`](../../src/test/java/com/kosign/wecms/config/LegacyBatchMappingVerificationTest.java)
(8 tests) — executable proof, complementing
[`FourToOneBatchArchitectureTest`](../../src/test/java/com/kosign/wecms/config/FourToOneBatchArchitectureTest.java)
(7 tests, source-scan proof that a withdrawn sweep has not returned):

| Test | Proves |
|---|---|
| `realtimeTransfer_hasNoScheduledPollingEntryPoint` | no `@Scheduled` on the realtime classes; the only scheduler reaching them is the gated recovery net, via `dispatch(...)` |
| `bulkReception_isTriggeredByApprovedRequestOrAfterCommitPath` | `receive()` does **not** run at registration time and runs exactly once on `afterCommit`; gated off by default; all five modules wired |
| `successful01301_triggers00302Inline` | a real encrypted 01301 issues one 00302 inline and advances to `PROC_STEP='2'` |
| `duplicate01301_executes00302OnlyOnce` | across three callback deliveries exactly one 00302 leaves — both the step-0 lookup and the ledger probe exercised |
| `noBatch2ScheduledBeanIsActive` | a real Spring `@EnableScheduling` context registers **zero** tasks with default properties |
| `resultCheckJob_isTheOnlyRetainedScheduledPaymentBatch` | with only the result-check cron set, the container schedules exactly `ResultCheckJob.run` |
| `resultCheckJob_doesNotSubmitNewTransfers` | every gateway service code the job emits is `EFS_203` — never 202/301/302 |
| `recovery_usesTheSameAtomicClaimAsNormalBulkDispatch` | the sweep calls `claimForReception` with the pay-file's own key, and on a lost claim performs no SFTP, no EFS, no firm-file insert |
