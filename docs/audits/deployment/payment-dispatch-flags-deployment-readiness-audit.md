# Payment Dispatch Flags — Legacy Parity & Deployment Readiness Audit

**Revision:** 1 (initial) · **Date:** 2026-07-28 · **System:** Refactor (`wecms-api`) vs Legacy WeCMS + Legacy WeCMS Batch
**Scope:** every payment-dispatch-related feature flag, scheduler flag, cron property, environment variable and activation condition.
**Change status:** **AUDIT ONLY.** No code, YAML, cron, DB row or flag was modified. No dispatch, recovery, EFS or bank call was executed.

## Relationship to existing docs — read this first

This audit deliberately does **not** restate work already published. Two deployment docs already cover the
*infrastructure* dimension and remain authoritative for it:

| Doc | Owns |
|---|---|
| [../deployment/wecms-batch-environment-variables.md](../../deployment/wecms-batch-environment-variables.md) | The per-variable env reference (12 sections, incl. the startup-required set) |
| [../deployment/wecms-batch-infrastructure-readiness-audit.md](../../deployment/wecms-batch-infrastructure-readiness-audit.md) | Config layering, deployed-runtime evidence, EFS-201 live-failure record, provisioning state, monitoring requirements, security findings |

**What this document adds:** the **legacy-parity dimension** — which the two docs above do not address. For every
refactor flag it identifies the actual legacy WeCMS / WeCMS Batch control (or proves none exists), classifies the
parity result, and answers the ten parity questions. It also records **four code-level findings not present in
either existing doc** (P-3, P-4, P-5, P-7 below).

Where a finding is shared with the readiness audit it is marked **(also in readiness audit)** and cited, not re-derived.

**Related:** [payment-dispatch-operating-hours-flow.md](payment-dispatch-operating-hours-flow.md) ·
[../features/wecms-batch/batch-schedule-operating-hours-audit.md](../batch/batch-schedule-operating-hours-audit.md) ·
[../deployment/wecms-batch-schedule-seed-review.md](../../deployment/wecms-batch-schedule-seed-review.md)

---

## 1. Executive summary

**Verdict: FUNCTIONAL MATCH WITH INTENTIONAL SAFETY DIFFERENCES** — with two parity gaps and one governance
gap that must be closed before activation. Full conclusion in §10.

The refactor reproduces legacy's *dispatch behaviour* but replaces legacy's *activation model* wholesale.
That replacement is deliberate and is an improvement, but it means **there is no legacy flag to compare most
refactor flags against** — a fact that must not be mistaken for parity.

| ID | Sev | Class | Finding |
|---|---|---|---|
| **P-1** | **HIGH** | Configuration defect | Dispatch env placeholders in `application-{sit,uat,prod}.yml` carry **no in-YAML default** (`${WECMS_EFS_ENABLED}`, not `${…:false}`). A missing variable aborts startup; the Java `:false` fallbacks cannot rescue it. Fail-safe but fail-loud. **(also in readiness audit §3.4)** |
| **P-2** | **HIGH** | Missing infrastructure / governance | **Deployed runtime contradicts every committed template.** ShedLock rows prove 3 HAENGSUNG jobs + FX + operation-status ran on 2026-07-27/28 while all templates declare them `false`. Committed OFF defaults therefore give **zero assurance** about deployed dispatch flags. **(also in readiness audit §4.3)** |
| **P-3** | **HIGH** | Code defect (coexistence) | **The recovery sweep has no tenant or origin filter.** `findByProcStatAndProcDttmLessThan(procStat, cutoff)` selects across **all** `BIZ_KEY`s and cannot distinguish refactor-created from JEX-created rows. On the shared live JEX DB, enabling it would re-drive legacy payments → duplicate dispatch. **NEW — not in either existing doc.** |
| **P-4** | **HIGH** | Code defect (hidden dependency) | **Enabling recovery silently also enables result-checking.** `StrandedRecoveryService.recoverOnce()` calls `resultCheckService.runOnce()` unconditionally, so `recovery.enabled=true` starts EFS 203 polling even with `result-check.cron="-"`. **NEW.** |
| **P-5** | MEDIUM | Code defect (asymmetry) | **`ResultCheckJob` is single-gated** (cron only — no `enabled` boolean), unlike recovery and stuck-monitor which are double-gated. Setting one cron string starts outbound EFS 203 traffic with no second gate. **NEW.** |
| **P-6** | MEDIUM | Legacy parity gap | **Operating-hours timezone diverges by 2 hours.** Refactor evaluates in `Asia/Ho_Chi_Minh`; legacy has *no* timezone config and uses the DB-server clock, which on the JEX frame DB is `Asia/Seoul`. Documented + overridable, but it changes which payments pass the window. |
| **P-7** | MEDIUM | Legacy parity gap | **Ops-alert environment labels cannot bind from a `.env` file.** `OpsAlertService` reads `wecms.dispatch.alert.env-mode` / `.was-no`, but neither key exists in any YAML. Legacy *always* stamps `[DEV\|UAT\|PROD][WAS_NO]` via `CommUtil.getServerGB()`. Alerts will lose the node label ops uses to identify the source. **NEW.** |
| **P-8** | MEDIUM | Intentional safeguard, needs a guard | Mock clients are the **fallback** (`matchIfMissing = true`). Today an absent variable aborts startup (P-1) rather than silently mocking — but if anyone "fixes" P-1 by adding `:false` YAML defaults, **UAT/PROD would silently run `MockEfsApiClient`** and payments would appear to succeed without reaching a bank. |
| **P-9** | MEDIUM | Intentional safeguard (pre-deploy gate) | Callbacks are `permitAll()` and `allowedIps` is empty in every template → no IP gate; `VV` HMAC is the sole authentication. This is the standing R-03 gate. **(also in readiness audit §8)** |
| **P-10** | INFO | Legacy parity difference | Legacy has **no per-feature flag, no ShedLock, no recovery job, no stuck monitor, no alert gate and no mock client**. Every refactor flag in those areas is a *new* safety gate with no legacy counterpart — DIFFERENT-BY-DESIGN, not a gap. |

**Bottom line:** no code change is required to keep the system safe today; the safe state is the current state.
**Two code fixes (P-3, P-4) are required before `recovery.enabled` may ever be turned on**, and P-7 should be
fixed before alerts are relied upon. Everything else is configuration and deployment-governance work.

---

## 2. How legacy actually controls execution (the parity baseline)

Establishing this first is essential, because most refactor flags have no legacy counterpart and the comparison
is otherwise meaningless.

**Verified against legacy** — tree: `WECMS_BATCH DEV/source/WECMS_BATCH/`. This is the **only** payment-dispatch
batch tree; `WECMS DEV/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/` and `DEV ADMIN/` contain none.

### 2.1 Legacy has exactly four batch programs and three gates

| Legacy batch | Source | Cron | Operating-hours gate |
|---|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | `…/src/batch/NAPAS_REAL_TIME_BATCH.java` | `* * * * *` (`NAPAS_REAL_TIME_BATCH.batch:6`) | INFO + DTL |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `…/src/batch/FILE_DEPOSIT_TRANSFER_BATCH1.java` | `*/1 * * * *` | INFO + DTL |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | `…/src/batch/FILE_DEPOSIT_TRANSFER_BATCH2.java` | `*/1 * * * *` | **none** |
| `PROCESSING_RESULT_CHECK_BATCH` | `…/src/batch/PROCESSING_RESULT_CHECK_BATCH.java` | `*/1 * * * *` | **none** |

Execution is gated by exactly three things, in order:

1. **`JEX_BATCH`** — a boolean in `web/WEB-INF/jex.prop`, **per WAS container, all-or-nothing for all four
   batches**. Consumed by the framework (`lib/JexSimpleBatch.0.3.0.jar` → `JexSimpleBatchManager`, effectively
   `StringUtil.toBoolean(JexSystem.getProperty("JEX_BATCH"), false)`), not by application code.
2. **The `schedule=` line in the per-batch `.batch` file.** The `.batch` format has **no `enabled` key** —
   recognised keys are `name`, `batchClass`, `schedule`, `monitoringLog`, `INPUT`, `httpMethod`, `httpTimeout`,
   `HTTP_HEADER`. Disabling one batch = comment out `schedule=` or delete the file.
3. **Per-record operating hours** from `TB_BATCH_SCHEDULE_INFO` / `_DTL` (`EXE_DIV` = `N`/`E`/`Y`) — and only
   for the two dispatch batches.

Trigger mechanism is an **in-WAS servlet + bundled cron4j** (`web.xml:32-37`, `jex.simplebatch.JexSimpleBatchServlet`,
`load-on-startup 2`). **No crontab, shell script, systemd unit, Quartz config or `main()` exists anywhere in the
legacy trees** — verified by `find` for `*.sh *.bat *.cmd *crontab* *cron* *.service *.timer quartz* standalone*.xml *.conf`
across all five trees (0 results) and `grep -rln "public static void main"` (0 results).

### 2.2 Legacy per-environment selection

`jex.prop` container section → `ENV_MODE` letter (`T`/`U`/`R`) → used as a **property-key prefix**
(`NAPAS_REAL_TIME_BATCH.java:36-39`: `PropertyUtil.getProperty(mode + "_API_URL")`). The DataSource is switched
by *file* (`JEX.config.file` → `jex.{dev,uat,real}.wecms_batch.xml`), not by `ENV_MODE`.

Container matrix (`jex.prop`) — note this is also legacy's only duplicate-run defence:

| Section | `JEX_BATCH` | `ENV_MODE` | `WAS_NO` |
|---|---|---|---|
| `[WECMS_BATCH_DEV]` | `true` | `T` | 01 |
| `[WECMS_BATCH_UAT]` | `true` | `U` | 02 |
| `[WECMS_BATCH_REAL_01]` | `true` | `R` | 03 |
| `[WECMS_BATCH_REAL_02]` | **`false`** | `R` | 04 |
| `[WECMS_BATCH_REAL_HS]` | `true` | `R` | 05 |

### 2.3 What legacy does **not** have (verified negatives)

Each negative below was established by an explicit search; they matter because a refactor flag with no legacy
counterpart cannot be "parity".

| Legacy feature | Result | Search |
|---|---|---|
| Per-batch `enabled` / feature flag | **NOT FOUND** | `grep -rniE "\benabled\b\|feature.?flag\|USE_YN\|ENABLE_YN\|BATCH_YN\|ON_OFF"` → only `ANT_WHERE_USE_YN` (a network-window flag, semantically unrelated) |
| `USE_YN`/`ENABLED` column on `TB_BATCH_SCHEDULE_INFO` | **NOT FOUND** | full read of `IDO.TB_BATCH_SCHEDULE_INFO_R001.xml` / `_DTL_R001.xml` |
| ShedLock / DB lock / PID file / advisory lock / `FOR UPDATE` | **NOT FOUND** | `grep -rniE "shedlock\|LOCK_TABLE\|pidfile\|\.pid\|already.?running\|FOR UPDATE\|advisory_lock"` → 0 |
| Recovery / stranded / stuck / reprocess batch | **NOT FOUND** | `grep -rliE "RECOVER\|STRANDED\|STUCK\|REPROCESS\|ORPHAN"` → 0 in the batch tree; `find -name "*.batch"` → 4 files only |
| Aged-record threshold / grace period | **NOT FOUND, structurally impossible** | every sweep is pinned to `TRAN_DT = today` (`IDO.TB_PAY_TARGET_R001.xml:41`, `IDO.TB_FIRM_TRAN_R001.xml:57`) |
| Timezone / `ZoneId` configuration | **NOT FOUND** | `grep -rniE "timezone\|ZoneId\|Asia/\|user\.timezone"` over the batch tree → 0 |
| EFS mock-vs-real switch | **NOT FOUND** | full read of `util/ConnectionUtil.java` — every path funnels to `sendRequest`; no bypass branch |
| SFTP enable switch | **NOT FOUND** | no `*_SFTP_ENABLED` key; no conditional around `sftpFileUpload` (`FILE_DEPOSIT_TRANSFER_BATCH1.java:1148-1157`) |
| Ops-alert on/off gate | **NOT FOUND** | full read of `util/MsgSendUtil.java` (42 lines) + `thread/MSGSendAPIThread.java` (131 lines) — always sends |

**Answer to parity question 2 ("if legacy does not use a feature flag, what controls execution?"):**
the batch is registered by the WAS servlet and **runs unconditionally every minute** whenever
`JEX_BATCH=true` for that container. Per-payment safety comes only from the operating-hours window and the
`PROC_STAT` state machine — never from configuration.

---

## 3. Complete refactor flag inventory

All line numbers re-derived from current `HEAD`. **Verified by code** unless noted.

Column meanings: **Java default** = `@Value(...:x)` / `*Properties` field initialiser. **YAML** = how
`application-{sit,uat,prod}.yml` defines it. `∅` = key absent from all YAML (Java default is therefore effective).

### 3.1 Dispatch activation flags

| # | Property | Java default | YAML (sit/uat/prod) | Consumer (class:line) | Controls | Activation point | Depends on | Legacy equivalent | Parity | Deployment impact |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `wecms.dispatch.realtime-dispatch` | `false` | `${WECMS_DISPATCH_REALTIME_DISPATCH}` **no default** | `RealtimeDispatchRegistrar:40-41`, guard `:49` | Post-commit hand-off to `RealtimeDispatchService.dispatch` (EFS 202 + Woori 204) | Registrar, after payment commit | `efs.enabled`, VAN key, `TB_FIRM_CONF` | `JEX_BATCH` + `NAPAS_REAL_TIME_BATCH.batch` `schedule=* * * * *` | **DIFFERENT-BY-DESIGN** — legacy has no equivalent boolean; the batch always ran | ON → real 202 traffic. OFF → rows stay `PROC_STAT='9'`, never dispatched. Missing var → **startup abort** (P-1) |
| 2 | `wecms.dispatch.auto-receive` | `false` | `${WECMS_DISPATCH_AUTO_RECEIVE}` **no default** | `DispatchAutoReceiveRegistrar:45-46`, guard `:53`; also read by `StuckPayFileMonitorService:140` | Post-commit hand-off to `BulkReceptionService.receive` (file build + SFTP + EFS 00301) | Registrar, after payment commit | `efs.enabled`, `sftp.enabled`, `TB_FIRM_CONF` | `JEX_BATCH` + `FILE_DEPOSIT_TRANSFER_BATCH1.batch` `schedule=*/1 * * * *` | **DIFFERENT-BY-DESIGN** | ON → real file upload + 00301. OFF → pay-files stay `'9'`. Missing var → **startup abort** |
| 3 | `wecms.dispatch.recipient-inquiry` | `false` | `${WECMS_DISPATCH_RECIPIENT_INQUIRY}` **no default** | `FirmBankingInquiryService:109`, gate `:165-170` | EFS 201 depositor-name inquiry during Excel upload / payment entry | Service, request-time | `efs.enabled`, VAN key | Legacy 201 is **not** a batch and **not** flagged — synchronous `WB_00000100.java:114,131,142`, reached via a raw TCP socket (`web.xml:39-97`, `firm_gw` on `127.0.0.1:11000`) | **DIFFERENT-BY-DESIGN** (refactor adds a gate legacy lacks) | ON → live 201. **Known to fail 17/17 with `RS_CD='1000'`** — see readiness audit §4.1. Missing var → **startup abort** |

### 3.2 Scheduled jobs

| # | Property | Java default | YAML | Consumer | Controls | Depends on | Legacy equivalent | Parity | Deployment impact |
|---|---|---|---|---|---|---|---|---|---|
| 4 | `wecms.dispatch.result-check.cron` | `"-"` (never fire) | `${WECMS_DISPATCH_RESULT_CHECK_CRON}` **no default** | `ResultCheckJob:39` (`@Scheduled` + `@SchedulerLock` `PT2M`/`PT5S`) | EFS 203 processing-result scan | `efs.enabled`; **and see P-4** | `PROCESSING_RESULT_CHECK_BATCH.batch:7` `schedule=*/1 * * * *`; the 2-minute wait is **hardcoded in SQL** (`IDO.TB_FIRM_TRAN_R001.xml:59`), not a property | **DIFFERENT-BY-DESIGN** (cadence configurable; legacy's was fixed) — but **single-gated, see P-5** | Setting a cron alone starts outbound 203 traffic. Missing var → **startup abort** |
| 5 | `wecms.dispatch.recovery.enabled` | `false` | `${…:false}` ✅ **has default** | `StrandedRecoveryService:66-67`, gate `:91-93` | Re-drives aged `PROC_STAT='9'` pay-targets + pay-files; **also runs result-check (P-4)** | `recovery.cron` **and** this flag | **NONE — legacy has no recovery batch at all** | **DIFFERENT-BY-DESIGN / new behaviour** | **MUST stay OFF on a shared DB (P-3).** ON → possible duplicate dispatch of legacy rows |
| 6 | `wecms.dispatch.recovery.cron` | `"-"` | `${…:-}` ✅ | `StrandedRecoveryJob:32` (`@SchedulerLock` `PT10M`/`PT10S`) | Drives the sweep | `recovery.enabled` | NONE | DIFFERENT-BY-DESIGN | Double-gated — safest flag in the set |
| 7 | `wecms.dispatch.recovery.grace-minutes` | `30` | `${…:30}` ✅ | `StrandedRecoveryService:70-71`, used `:95` | Age cutoff before a row is swept | `recovery.enabled` | **NONE** — legacy's only wait is the hardcoded 2-minute 203 delay | **NOT APPLICABLE** (no legacy value to match) | Too low → races live dispatch; too high → slow recovery |
| 8 | `wecms.dispatch.stuck-monitor.enabled` | `false` | **∅** | `StuckPayFileMonitorService:121-122`, gate `:172-173` | Read-only stuck-pay-file detection + alert | `stuck-monitor.cron`, `alert.enabled` for output | **NONE** — legacy monitoring is manual (`handover/05_배치스케줄_운영가이드.md:133`) | DIFFERENT-BY-DESIGN | Absent from YAML → **cannot abort startup**; safe to enable (read-only) |
| 9 | `wecms.dispatch.stuck-monitor.cron` | `"-"` | **∅** | `StuckPayFileMonitorJob:40` (`@SchedulerLock` `PT5M`/`PT10S`) | Drives the monitor | `stuck-monitor.enabled` | NONE | DIFFERENT-BY-DESIGN | Double-gated, read-only |
| 10 | `wecms.dispatch.stuck-monitor.threshold-minutes` | `15` | **∅** | `StuckPayFileMonitorService:125` | Age at which a file counts as stuck | #8 | NONE | NOT APPLICABLE | Tuning only |
| 11 | `wecms.dispatch.stuck-monitor.re-alert-minutes` | `60` | **∅** | `:129` | Re-alert suppression window | #8 | NONE | NOT APPLICABLE | Tuning only |
| 12 | `wecms.dispatch.stuck-monitor.max-alert-keys` | `20` | **∅** | `:133` | Alert-volume cap | #8 | NONE | NOT APPLICABLE | Tuning only |

### 3.3 EFS / SFTP / bank communication — mock vs real

| # | Property | Java default | YAML | Consumer | Controls | Legacy equivalent | Parity | Deployment impact |
|---|---|---|---|---|---|---|---|---|
| 13 | `wecms.dispatch.efs.enabled` | `false` (`EfsProperties.enabled`) | `${WECMS_EFS_ENABLED}` **no default** | **Bean selection:** `RestClientEfsApiClient:33` (`havingValue="true"`) vs `MockEfsApiClient:23` (`havingValue="false", matchIfMissing=true`) | Real HTTP gateway vs in-memory mock | **NONE** — legacy always calls the real gateway | **DIFFERENT-BY-DESIGN**, but see **P-8** | `true` → real bank traffic. `false`/absent → **mock**; payments appear to succeed with no bank contact |
| 14 | `wecms.dispatch.efs.base-url` | `""` | `${WECMS_EFS_BASE_URL}` **no default** | `EfsProperties` | Gateway URL | `{T,U,R}_API_URL` in `wecms_batch.properties:21-24` — **legacy UAT points at the DEV gateway** | **DIFFERENT-BY-DESIGN** (refactor separates UAT) | Wrong value → traffic to the wrong environment |
| 15 | `wecms.dispatch.efs.partner-id` | `""` | `${WECMS_EFS_PARTNER_ID}` **no default** | `EfsProperties`; falls back to `vanOrgCd` in `BulkReceptionService:704-705` | Envelope `ID` | `TB_FIRM_CONF.BANK_PRTN_ID` (a DB row, not a property) | **DIFFERENT** — refactor adds a global override legacy lacks | Empty in every committed template — blocking for EFS |
| 16 | `wecms.dispatch.sftp.enabled` | (`SftpProperties`) | `${WECMS_SFTP_ENABLED}` **no default** | `JschSftpClient:37` (`"true"`) vs `MockSftpClient:20` (`"false", matchIfMissing=true`) | Real SFTP vs mock | **NONE** — legacy always uploads | DIFFERENT-BY-DESIGN, see **P-8** | `false`/absent → mock upload; the bank never receives the file |
| 17 | `wecms.dispatch.sftp.*` (host/port/user/key/paths) | — | no defaults | `SftpProperties` | Connection + staging paths | `{T,U,R}_SFTP_IP/_PORT/_CERT/_PATH`, `_SEND_PATH`, `_LOCAL_DOWN_PATH`… | **MATCH** (same values in `.env.sit`: `42.96.55.163:3300`, `sdata`/`rdata`) | Legacy `{ENV}_SFTP_CERT` is **dead config** (`SftpUtil.java:57-59` overrides it with `$HOME/.ssh/id_rsa`); refactor's `private-key-path` is honoured — an improvement |
| 18 | VAN API key | — | **not a property** | `VanApiKeyService:39-43` → `TB_VAN_API_INFO.API_KEY`; throws `IllegalStateException` when absent | Per-VAN AES/HMAC key | `CommUtil.getApiKey()` → same table, same column | **MATCH** | Unprovisioned → dispatch fails loudly at first use. **2 rows present** in the refactor DB (verified by DB) |

### 3.4 Alerts and callbacks

| # | Property | Java default | YAML | Consumer | Legacy equivalent | Parity | Deployment impact |
|---|---|---|---|---|---|---|---|
| 19 | `wecms.dispatch.alert.enabled` | `false` | `${WECMS_DISPATCH_ALERT_ENABLED}` **no default** | `OpsAlertService:89-90`, gate `:126-128` | `MsgSendUtil.SendMsg` — **always on, no flag** | **DIFFERENT-BY-DESIGN** (refactor can be silent; legacy never is) | OFF → dispatch failures produce **no Slack alert**. Must be ON before dispatch |
| 20 | `wecms.dispatch.alert.env-mode` | `""` | **∅ — key absent** | `OpsAlertService:97`, used `:173` | `ENV_MODE` → `CommUtil.getServerGB()` `[DEV\|UAT\|PROD][WAS_NO]`, always stamped | **MISSING** → **P-7** | Alerts lose the node label ops uses to identify the source |
| 21 | `wecms.dispatch.alert.was-no` | `""` | **∅ — key absent** | `OpsAlertService:101` | `WAS_NO` in `jex.prop` | **MISSING** → **P-7** | Same |
| 22 | `wecms.dispatch.callback.allowed-ips` | `[]` (empty) | `${WECMS_CALLBACK_ALLOWED_IPS}` **no default**; empty in every template | `CallbackProperties:44` + `CallbackIpWhitelistFilter` | Legacy relied on network reachability + `VV` | **DIFFERENT-BY-DESIGN** | Empty → **no IP gate**; `VV` HMAC is the sole authentication (**P-9**) |
| 23 | `wecms.dispatch.callback.trust-forwarded-for` | `true` | literal `true` | `CallbackProperties:47` | NONE | DIFFERENT-BY-DESIGN | `true` + empty allow-list → `X-Forwarded-For` is trusted but unused |
| 24 | `wecms.dispatch.callback.path-prefix` | `/api/v1/payment/dispatch/callbacks` | ∅ | `CallbackProperties:41` | `*.jct` servlet mapping | DIFFERENT-BY-DESIGN | Endpoints are `permitAll()` — `SecurityConfig:80` |

### 3.5 Operating hours and routing

| # | Property | Java default | YAML | Consumer | Legacy equivalent | Parity |
|---|---|---|---|---|---|---|
| 25 | `wecms.dispatch.operating-hours.zone` | `Asia/Ho_Chi_Minh` | **∅** | `OperatingHoursService:85-86`, used `:236` | **NONE.** Legacy compares `TO_CHAR(NOW(),'HH24MISS')` — the **DB server's** clock (`Asia/Seoul` on the JEX frame DB) | **DIFFERENT-BY-DESIGN → P-6.** Set `Asia/Seoul` to reproduce legacy bug-for-bug |
| 26 | `wecms.internal-transfer.routing.{shinhan,vcb,woori}-bank-cd` | `970424` / `970436` / `970457` | ∅ | routing + executors | `WCConst.java:13-14` hardcoded constants | **MATCH** (same codes; refactor makes them configurable) |
| 27 | `wecms.internal-transfer.cutoff.refresh-interval-ms` | `300000` | ∅ | `CutoffConfigProvider:133` (`@Scheduled fixedDelay`) | NONE | DIFFERENT-BY-DESIGN — cache refresh only, no dispatch effect |

### 3.6 Adjacent schedulers sharing the ShedLock table (in scope: they can move payment-adjacent data)

| # | Property | YAML | Consumer | Note |
|---|---|---|---|---|
| 28 | `wecms.exchange-rate.batch.enabled` | `${WECMS_EXCHANGE_RATE_BATCH_ENABLED}` | `ExchangeRateBatchScheduler:49` `@ConditionalOnProperty` | `.env.sit` = **`true`**; all other templates `false` |
| 29 | `wecms.operation-status.batch.{enabled,seed-cron,refresh-cron}` | env placeholders | `OperationStatusBatchScheduler:44,56,65` | Templates `false`; **ShedLock proves it ran** (P-2) |
| 30 | `haengsung.oracle.enabled` | `${HAENGSUNG_ORACLE_ENABLED}` | 11 `@ConditionalOnProperty` sites incl. `ErpPaymentRefreshService:46` | Templates `false`; gates the whole HS Oracle leg |
| 31 | `haengsung.scheduled-payment-import.{enabled,cron}` | env placeholders | `HaengsungScheduledPaymentImportJob:55,72` | **Creates `SCDUL_PYMN_SUMR` payment rows.** Templates `false`; **ShedLock proves it ran 2026-07-28** (P-2) |
| 32 | `haengsung.exchange-rate-sync.{enabled,cron}` | env placeholders | `HaengsungExchangeRateSyncJob:37,49` | Templates `false`; **ran 2026-07-28** |
| 33 | `haengsung.card-account-sync.{enabled,cron}` | env placeholders | `HaengsungCardAccountSyncJob:37,49` | Templates `false`; **ran 2026-07-28** |

---

## 4. Runtime-behaviour traces

### 4.1 Does disabling a scheduler truly prevent execution?

**Mostly yes — with two exceptions.**

```
cron "-"  →  Spring registers the @Scheduled method but never fires it  →  service never invoked
```

`"-"` is Spring's documented never-fire marker (`Scheduled.CRON_DISABLED`). All three dispatch jobs default to
it. **Verified by database:** the `shedlock` table contains **8 rows and none belongs to a dispatch job** —
no `ResultCheckJob.run`, `StrandedRecoveryJob.run` or `StuckPayFileMonitorJob.run` lock has ever been created,
while 8 other job locks exist with `locked_at` timestamps. Dispatch schedulers have never fired in this database.

**Exception 1 (P-4) — the service is reachable by another path.** `ResultCheckService.runOnce()` is called from
**two** places: `ResultCheckJob:42` *and* `StrandedRecoveryService.recoverOnce()`:

```java
// StrandedRecoveryService, inside recoverOnce()
recoverPayTargets(cutoff);
recoverPayFiles(cutoff);
try {
    resultCheckService.runOnce();          // ← runs regardless of result-check.cron
} catch (Exception e) { … }
```

So `result-check.cron="-"` does **not** guarantee result-checking is off; `recovery.enabled=true` + a recovery
cron activates it too. This dependency is not stated in the env-var reference.

**Exception 2 (P-5) — asymmetric gating.** `ResultCheckJob` has *no* `enabled` boolean; the cron is its only
gate. Recovery and stuck-monitor each have cron **plus** an `enabled` check inside the service. A single
mistyped env var therefore has different consequences depending on which job it belongs to.

### 4.2 Does a disabled registrar still allow rows to be inserted?

**Yes — and this is by design, but it must be understood before activation.**

```
POST …/{id}/dispatch → service.dispatch() → executor.execute()
   → TB_PAY_TARGET / TB_PAY_FILE_MST + TB_PAY_FILE_DATA  INSERT at PROC_STAT='9'   ← always happens
   → registrar.registerAfterCommit(...)                                            ← no-ops when flag OFF
```

`RealtimeDispatchRegistrar:49` and `DispatchAutoReceiveRegistrar:53` return immediately when disabled — *after*
the executor has already written and committed the dispatch rows. With both flags OFF the system therefore
**accumulates `PROC_STAT='9'` rows indefinitely, with no dispatch, no failure and no alert.** That is the
intended inert state, but it produces exactly the silent-WAIT backlog the operating-hours audit flags as
operationally hard to notice. Monitoring `PROC_STAT='9'` age is a prerequisite for any activation
(readiness audit §9.2).

Cross-reference: this is also why **F-4 in [payment-dispatch-operating-hours-flow.md](payment-dispatch-operating-hours-flow.md)
bites with the flags off** — the write precedes the registrar.

### 4.3 Can recovery or monitoring affect legacy-created rows on a shared DB?

**Recovery: YES — this is P-3, the most serious code finding.** The selection is:

```java
List<TbPayTarget> aged = payTargetRepository
        .findByProcStatAndProcDttmLessThan(ProcStat.WAIT.code(), cutoff);
```

Repository signature (`TbPayTargetRepository:75`, `TbPayFileMstRepository:29`):

```java
List<TbPayTarget> findByProcStatAndProcDttmLessThan(String procStat, String procDttmCutoff);
```

**Two parameters only — no `BIZ_KEY`, no origin/source column, no `TRAN_DT` bound.** Consequences on the live
JEX DB (which is the refactor's target in all environments per `project_uat_prod_live_jex_db_target`):

1. **Cross-tenant.** Every `BIZ_KEY`'s rows are swept, not just the refactor's tenants.
2. **Cross-system.** A `PROC_STAT='9'` row written by legacy `pymn_*` is indistinguishable from a
   refactor-written one, so the sweep would re-drive **legacy payments** through
   `RealtimeDispatchService.dispatch` / `BulkReceptionService.receive`.
3. **Wider than legacy.** Legacy pins every sweep to `TRAN_DT = today`
   (`IDO.TB_PAY_TARGET_R001.xml:41`); this sweep has no date bound, so it can reach back indefinitely and pick
   up rows legacy has permanently abandoned.

Mitigations that exist: the per-row atomic `'9'→'6'` claim (`RealtimeDispatchService:221`) and the WAIT gate
prevent *concurrent* double-dispatch, and ShedLock prevents two refactor nodes sweeping at once. **Neither
prevents the refactor from dispatching a payment legacy owns.** The service Javadoc acknowledges the risk and
is why the flag is double-gated OFF; the audit records it as an open code defect because the guard is a flag,
not a query predicate.

**Stuck-monitor: NO.** `StuckPayFileMonitorService.monitorOnce()` performs reads plus an alert; it issues no
`PROC_STAT` write and re-drives nothing. It is safe to enable for observability on a shared DB — see §7.

### 4.4 Can mock clients remain active in UAT/PROD?

**Not today, but the protection is accidental — see P-8.**

```
@ConditionalOnProperty(prefix="wecms.dispatch.efs", name="enabled",
                       havingValue="false", matchIfMissing=true)   → MockEfsApiClient
@ConditionalOnProperty(prefix="wecms.dispatch.efs", name="enabled",
                       havingValue="true")                          → RestClientEfsApiClient
```

`matchIfMissing = true` makes **mock the fallback**. What prevents a silent mock in UAT/PROD is *not* a
prod-specific guard — it is P-1: the YAML defines the property as `${WECMS_EFS_ENABLED}` with no default, so an
absent variable makes the placeholder unresolvable and the application fails to start rather than booting with
a mock. Same structure for SFTP.

The two findings are therefore **coupled**: fixing P-1 the obvious way (adding `:false` defaults) would convert
a startup abort into a **silent mock in production**, where payments are recorded as dispatched but no bank is
contacted. Any P-1 fix must add an explicit prod assertion (e.g. fail-fast when profile is `prod`/`uat` and
`efs.enabled=false`), not just a default.

*Verification label:* the startup-abort behaviour is **inferred from Spring's strict nested-placeholder
resolution, not runtime tested** in this audit. It is independently asserted in the readiness audit §3.4. A
one-command boot test with a variable removed would upgrade this to verified — recommended before any
UAT/PROD cutover.

### 4.5 What happens if one flag is enabled without its dependencies

| Enabled alone | Result | Severity |
|---|---|---|
| `realtime-dispatch=true`, `efs.enabled=false` | `MockEfsApiClient` returns success → `TB_PAY_TARGET` marked SUCCESS, **no bank contacted**. Silent false-success. | **HIGH** |
| `auto-receive=true`, `sftp.enabled=false` | `MockSftpClient` "uploads" → 00301 issued (or mocked) for a file the bank never received | **HIGH** |
| `realtime-dispatch=true`, `alert.enabled=false` | Cut-off / no-criteria / gateway failures set `PROC_STAT='3'` FAIL with **no alert** | MEDIUM |
| `result-check.cron` set, `realtime-dispatch=false` | Polls 203 for transactions that were never dispatched — wasted calls, no harm | LOW |
| `realtime-dispatch=true`, `result-check.cron="-"` | 202 `G999`/no-response rows stay at `PROC_STAT='6'` **forever** — no 203 confirmation, no timeout | **HIGH** |
| `recovery.enabled=true` + cron, shared DB | Re-drives legacy rows → **duplicate dispatch** (P-3); also silently starts result-checking (P-4) | **HIGH** |
| `alert.enabled=true`, `efs.enabled=false` | Alerts are built then sent through the mock — **ops believe alerting works when it does not** | MEDIUM |
| `stuck-monitor.enabled=true` + cron, `alert.enabled=false` | Monitor computes a report that goes nowhere | LOW |
| Any dispatch flag ON while legacy batch also runs | See §5 | **HIGH** |

---

## 5. Coexistence — can refactor and legacy batch run simultaneously?

**No, not for the same tables, and not safely.** Analysis:

| Duplicate-dispatch vector | Legacy defence | Refactor defence | Combined |
|---|---|---|---|
| Two refactor nodes | — | ShedLock (`shedlock` table, `usingDbTime()`) + atomic `'9'→'6'` claim | **Safe** |
| Two legacy containers | static `JEX_BATCH=false` on `REAL_02` only | — | **Already unsafe in legacy** — `REAL_01` *and* `REAL_HS` both have `JEX_BATCH=true`, `ENV_MODE=R`, the same real DataSource and the same gateway, with **no lock of any kind**. Legacy's baseline is not single-runner-safe. |
| Legacy batch + refactor | none | none — ShedLock is refactor-internal; legacy neither writes nor reads the `shedlock` table | **UNSAFE.** Both poll `PROC_STAT='9'` on the same tables with no shared lock. |

The only thing preventing duplicate dispatch today is that **every refactor dispatch flag is OFF**. There is no
technical interlock. Note also that the refactor's atomic `'9'→'6'` claim only de-duplicates *within* the
refactor — legacy's `IDO.TB_PAY_TARGET_R001` does a plain `SELECT` with no locking clause, so it will happily
pick up a row the refactor is mid-dispatch on.

**Answer to parity question 7 — flags that must remain disabled while the DB is shared with live legacy:**
`realtime-dispatch`, `auto-receive`, `result-check.cron`, `recovery.enabled`, `recovery.cron`. Plus
`recipient-inquiry` for a different reason (it is broken, not unsafe — 17/17 `RS_CD='1000'`).

---

## 6. The ten parity questions, answered

1. **Does each refactor flag have a real legacy equivalent?** **No.** Of 27 dispatch properties, only 3 map to a
   legacy control: SFTP connection settings (**MATCH**), the bank-code constants (**MATCH**), and the VAN API key
   (**MATCH**, same table/column). The activation flags map to legacy's *`JEX_BATCH` + `.batch` cron* only
   loosely, and 12 properties (recovery ×3, stuck-monitor ×5, alert labels ×2, operating-hours zone, cutoff
   refresh) have **no legacy counterpart whatsoever**.
2. **If legacy has no feature flag, what controls execution?** The WAS servlet registers the batch and it runs
   every minute unconditionally when `JEX_BATCH=true`. Per-payment control is the operating-hours window plus
   the `PROC_STAT` state machine — never configuration. §2.1.
3. **Does the refactor preserve the same schedule and execution conditions?** **Partly.** The
   *operating-hours* conditions are preserved faithfully (including the deliberately cross-wired
   `BATCH_ID` keys — see [payment-dispatch-operating-hours-flow.md §2](payment-dispatch-operating-hours-flow.md)).
   The *schedule* is not: legacy polls every minute; the refactor is **request-time / post-commit** (Mr. Kang
   2026-06-04/06 TOBE) with only result-check remaining scheduled. That is an approved architectural change.
4. **Does the refactor introduce additional safety gates?** **Yes, substantially** — per-feature booleans,
   never-fire cron defaults, double-gating on the mutating sweeps, ShedLock, mock clients, an alert gate, a
   callback IP filter, and an explicit operating-hours timezone. Legacy has **none** of these.
5. **Are the differences intentional coexistence controls or parity gaps?** Mostly intentional
   (**DIFFERENT-BY-DESIGN**): items 1–13, 16, 19, 22–27. Genuine gaps: **P-7** (alert labels — legacy always
   stamps them, refactor cannot bind them), **P-6** (timezone — a real 2-hour behavioural difference), and
   **P-3/P-4** (refactor-only defects in refactor-only features).
6. **Can both run simultaneously without duplicate dispatch?** **No.** No shared lock exists. §5.
7. **Which flags must stay disabled on a shared DB?** `realtime-dispatch`, `auto-receive`,
   `result-check.cron`, `recovery.enabled`, `recovery.cron`. §5.
8. **Which flags are safe to enable for monitoring only?** `stuck-monitor.enabled` + `stuck-monitor.cron`
   (read-only, no `PROC_STAT` write — §4.3), paired with `alert.enabled` so the report is delivered. Note
   `alert.enabled` also needs `efs.enabled=true` to reach anything, which is a real-gateway decision.
9. **Which flags are safe in SIT but unsafe in UAT/PROD?** `efs.enabled=false` and `sftp.enabled=false`
   (mock) are correct in SIT but must be `true` in UAT/PROD — and **P-8** means the mock is the silent
   fallback, so this needs an explicit assertion rather than a default. `recipient-inquiry=true` is
   SIT-diagnostic only while `RS_CD='1000'` persists. `WECMS_EXCHANGE_RATE_BATCH_ENABLED=true` is set in
   `.env.sit` and `false` in the UAT/PROD templates.
10. **Does enabling the refactor reproduce legacy behaviour for all banks and payment types?** **Not verified,
    and at least two known gaps exist.** Per-bank/per-type parity is the subject of the operating-hours audit,
    which currently records **F-4 (HIGH, open)** — Vendor/Expense/Internal-Transfer VCB+VND file dispatch fails
    with a `character(1)` overflow — and **F-2 (MEDIUM, open)**. Until those close, enabling dispatch does
    **not** reproduce legacy behaviour for VCB+VND on three of the five payment types.

---

## 7. Environment matrix

**Values are the committed templates only.** Per **P-2** the deployed runtime is authoritative and is
**not auditable from this repository** (SIT loads OS env vars from `bin/setenv.sh`, per
`reference_sit_server_config_setenv`), and it currently **contradicts** these templates for the adjacent
schedulers. "DEV" is not a refactor environment — the refactor profiles are `local`, `sit`, `uat`, `prod`.

| Property | local (`.env.local`) | SIT (`.env.sit`) | UAT (`.env.uat.example`) | PROD (`.env.prod.example`) | Legacy equivalent | Safe current value | Required before activation |
|---|---|---|---|---|---|---|---|
| `wecms.dispatch.realtime-dispatch` | `false` | `false` | `false` | `false` | `JEX_BATCH` + `NAPAS_REAL_TIME_BATCH.batch` | **`false`** | EFS+VAN key+`TB_FIRM_CONF`; legacy batch stopped for the same banks; F-4 closed |
| `wecms.dispatch.auto-receive` | `false` | `false` | `false` | `false` | `JEX_BATCH` + `…BATCH1.batch` | **`false`** | SFTP+EFS provisioned; F-4 closed |
| `wecms.dispatch.recipient-inquiry` | `false` | `false` | `false` | `false` | none (sync socket service) | **`false`** | `RS_CD='1000'` root-caused (readiness §4.1) |
| `wecms.dispatch.result-check.cron` | `-` | `-` | `-` | `-` | `*/1 * * * *` (fixed) + 2-min SQL delay | **`-`** | Enable **only after** outbound dispatch is on |
| `wecms.dispatch.recovery.enabled` | ∅ → `false` | ∅ → `false` | ∅ → `false` | ∅ → `false` | **none** | **`false`** | **P-3 + P-4 fixed**; PO decision on shared DB |
| `wecms.dispatch.recovery.cron` | ∅ → `-` | ∅ → `-` | ∅ → `-` | ∅ → `-` | none | **`-`** | As above |
| `wecms.dispatch.recovery.grace-minutes` | ∅ → `30` | ∅ → `30` | ∅ → `30` | ∅ → `30` | none | `30` | Tuning only |
| `wecms.dispatch.stuck-monitor.enabled` | ∅ → `false` | ∅ → `false` | ∅ → `false` | ∅ → `false` | none (manual) | `false` | **Safe to enable now** (read-only) |
| `wecms.dispatch.stuck-monitor.cron` | ∅ → `-` | ∅ → `-` | ∅ → `-` | ∅ → `-` | none | `-` | Safe to enable with `alert.enabled` |
| `wecms.dispatch.alert.enabled` | `false` | `false` | `false` | `false` | **always on** | `false` | **Must be `true` before any dispatch flag** |
| `wecms.dispatch.alert.env-mode` | ∅ | ∅ | ∅ | ∅ | `ENV_MODE` (always set) | — | **P-7: add the YAML key** |
| `wecms.dispatch.alert.was-no` | ∅ | ∅ | ∅ | ∅ | `WAS_NO` (always set) | — | **P-7: add the YAML key** |
| `wecms.dispatch.efs.enabled` | `false` (mock) | `false` (mock) | `false` (**mock**) | `false` (**mock**) | none — always real | `false` in local/SIT | **`true` in UAT/PROD** + explicit prod assertion (P-8) |
| `wecms.dispatch.efs.base-url` | dev gateway | `https://dev-van-gw.webcashvietnam.com` | *empty* | *empty* | `{T,U,R}_API_URL` (UAT→DEV gw) | — | PROD `https://van-gw.webcashvietnam.com` |
| `wecms.dispatch.efs.partner-id` | *empty* | *empty* | *empty* | *empty* | `TB_FIRM_CONF.BANK_PRTN_ID` | — | **Blocking — empty everywhere** |
| `wecms.dispatch.sftp.enabled` | `false` (mock) | `false` (mock) | `false` (**mock**) | `false` (**mock**) | none — always real | `false` in local/SIT | **`true` in UAT/PROD** (P-8) |
| `wecms.dispatch.sftp.host` / `.port` | `42.96.55.163` / `3300` | `42.96.55.163` / `3300` | *empty* / `3300` | *empty* / `3300` | `{T,U}_SFTP_IP=42.96.55.163`, `R_SFTP_IP=42.96.55.18` | — | PROD must use `42.96.55.18` |
| `wecms.dispatch.callback.allowed-ips` | *empty* | *empty* | *empty* | *empty* | none (network only) | *empty* in local/SIT | **Gateway IPs before UAT/PROD (R-03)** |
| `wecms.dispatch.operating-hours.zone` | ∅ → `Asia/Ho_Chi_Minh` | ∅ | ∅ | ∅ | none (DB clock = `Asia/Seoul`) | `Asia/Ho_Chi_Minh` | **Conscious PO decision — P-6** |
| `wecms.exchange-rate.batch.enabled` | `false` | **`true`** | `false` | `false` | n/a | as set | — |
| `wecms.operation-status.batch.enabled` | `false` | `false` | `false` | `false` | n/a | `false` | **Contradicted by runtime (P-2)** |
| `haengsung.oracle.enabled` | `false` | `false` | `false` | `false` | n/a | `false` | HS Oracle DSN + creds |
| `haengsung.scheduled-payment-import.enabled` | `false` | `false` | `false` | `false` | n/a | `false` | **Contradicted by runtime (P-2)** — creates payment rows |
| `haengsung.exchange-rate-sync.enabled` | `false` | `false` | `false` | `false` | n/a | `false` | **Contradicted by runtime (P-2)** |
| `haengsung.card-account-sync.enabled` | `false` | `false` | `false` | `false` | n/a | `false` | **Contradicted by runtime (P-2)** |

---

## 8. Deployment readiness checklist

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | All mutating dispatch flags default to disabled | ✅ **PASS** | Java defaults `false`; every committed template `false` — verified by configuration |
| 2 | All dispatch schedulers default to never-run | ✅ **PASS** | cron default `"-"` on all three jobs — verified by code |
| 3 | Missing env vars cannot accidentally enable dispatch | ✅ **PASS** (by abort, not by default) | No-default placeholders → unresolvable → abort. Inferred, not runtime tested |
| 4 | Startup does not fail because an optional property is absent | ❌ **FAIL — P-1** | 8+ dispatch keys have no in-YAML default; every one is startup-required |
| 5 | SIT uses the intended mock integrations | ✅ **PASS** | `.env.sit`: `WECMS_EFS_ENABLED=false`, `WECMS_SFTP_ENABLED=false` → mock beans |
| 6 | UAT/PROD cannot accidentally use mock clients | ⚠️ **CONDITIONAL — P-8** | Protected only by the P-1 abort; `matchIfMissing=true` makes mock the fallback. No prod-specific assertion exists |
| 7 | Recovery stays disabled while tables are shared | ✅ **PASS** today / ❌ **unsafe if enabled — P-3** | Double-gated OFF; but the sweep has no tenant/origin filter |
| 8 | ShedLock available before any scheduler is enabled | ✅ **PASS** | `shedlock` table exists, 8 rows — **verified by database** |
| 9 | Required schedule + operating-hours rows exist | ✅ **PASS** | `tb_batch_schedule_info` = 3, `tb_batch_schedule_dtl` = 12 — **verified by database**; content reviewed in [../deployment/wecms-batch-schedule-seed-review.md](../../deployment/wecms-batch-schedule-seed-review.md) |
| 10 | Bank partner IDs / firm-banking config exist | ⚠️ **PARTIAL** | `tb_firm_conf` = 3, `tb_van_api_info` = 2 — **verified by database**; but `WECMS_EFS_PARTNER_ID` empty in every template |
| 11 | EFS connectivity / certs / credentials / URLs / IP whitelisting ready | ❌ **NOT READY** | `partner-id` empty; `allowed-ips` empty; EFS 201 returns `RS_CD='1000'` 17/17 (readiness §4.1) |
| 12 | Alerts configured before enabling dispatch | ❌ **NOT READY** | `alert.enabled=false` everywhere; `env-mode`/`was-no` unbindable (**P-7**) |
| 13 | Result-checking enabled only after outbound dispatch | ⚠️ **NOT ENFORCED** | Ordering is documentation-only; **P-4** lets recovery start it out of order |
| 14 | Rollback procedure for every enabled flag | ⚠️ **PARTIAL** | Flags are reversible by restart, but no documented rollback for in-flight `PROC_STAT='6'` rows left after a mid-flight disable. §9 |
| 15 | No activation can duplicate-process with legacy batch | ❌ **NO INTERLOCK** | ShedLock is refactor-internal; legacy uses no lock. §5 |

---

## 9. Recommendations — **none applied; approval required**

Per root `CLAUDE.md` §5 (analyse and get approval before modifying a legacy-compatible process). Classified by
whether code is required.

### Code changes required (2 — both block `recovery.enabled` forever until done)

| ID | Change | Rationale |
|---|---|---|
| **R-1 (P-3)** | Add a tenant/origin predicate to the recovery finders — at minimum `BIZ_KEY IN (:refactorTenants)` plus a `TRAN_DT` bound mirroring legacy's `TRAN_DT = today`. Preferred: an origin discriminator agreed with the PO. | Without it the sweep can re-dispatch legacy-owned payments. A flag is not a substitute for a query predicate. |
| **R-2 (P-4)** | Either remove the `resultCheckService.runOnce()` call from `recoverOnce()`, or gate it on the result-check cron being set — and document the coupling either way. | `recovery.enabled=true` must not silently start EFS 203 traffic. |

### Code changes recommended (2)

| ID | Change |
|---|---|
| **R-3 (P-5)** | Add a `wecms.dispatch.result-check.enabled` boolean so all three jobs are double-gated symmetrically. |
| **R-4 (P-8)** | Add a startup assertion: when the active profile is `uat`/`prod`, fail fast if `wecms.dispatch.efs.enabled=false` or `wecms.dispatch.sftp.enabled=false` while any dispatch flag is `true`. This must land **with** any P-1 fix, not after. |

### Configuration / YAML changes (3)

| ID | Change |
|---|---|
| **R-5 (P-1)** | Add explicit in-YAML defaults (`${WECMS_EFS_ENABLED:false}` etc.) — **only together with R-4**, otherwise this converts a startup abort into a silent production mock. |
| **R-6 (P-7)** | Add `env-mode:` and `was-no:` keys under `wecms.dispatch.alert` in the profile YAMLs so the documented env vars bind, restoring legacy's `[ENV][WAS_NO]` alert label. |
| **R-7** | Add the 5 `stuck-monitor.*` keys to the profile YAMLs and `.env.*.example` so the monitor is enableable through the normal channel (it is currently reachable only as a raw property). |

### Documentation / governance (3)

| ID | Change |
|---|---|
| **R-8 (P-2)** | Establish a single authority for deployed flags. Either commit the deployment env source, or add a startup log line + `/actuator` surface that reports the effective dispatch-flag set so the running state is auditable. Today the templates are misleading. |
| **R-9 (P-6)** | Obtain an explicit PO decision on `operating-hours.zone`: keep ICT (business-correct, diverges from legacy by 2 h) or set `Asia/Seoul` for bug-for-bug parity while legacy runs in parallel. Record it. |
| **R-10** | Document the activation order and a rollback procedure per flag, including how to drain in-flight `PROC_STAT='6'` rows if dispatch is disabled mid-flight. |

### Suggested activation order (documentation only — do not execute without approval)

```
1. alert.enabled=true + efs.enabled=true      (alerting must work before anything can fail silently)
2. stuck-monitor.enabled=true + cron          (read-only observability; safe on a shared DB)
3. [close F-4 / F-2 from the operating-hours audit]
4. sftp.enabled=true                          (real file channel)
5. auto-receive=true   OR  realtime-dispatch=true   — one payment type, one bank, legacy stopped for it
6. result-check.cron   (only after step 5)
7. recovery.enabled    (only after R-1 + R-2 + PO shared-DB decision)
```

---

## 10. Final conclusion

> **Does the refactor dispatch-flag and scheduler configuration match legacy WeCMS and WeCMS Batch?**

# FUNCTIONAL MATCH WITH INTENTIONAL SAFETY DIFFERENCES

**1. What matches legacy.** The dispatch *behaviour* and its data contracts: the operating-hours gate including
the deliberately cross-wired `BATCH_ID` keys, the `PROC_STAT` state machine, the EFS 201/202/203/204/00301/00302
call set, the SFTP endpoints and path layout, the bank-code constants (`970424`/`970436`/`970457`), the VAN API
key source (`TB_VAN_API_INFO.API_KEY`), and the alert message strings and trigger conditions. Where the refactor
makes something configurable that legacy hardcoded (bank codes, SFTP key path, EFS base-url per environment),
the committed values match legacy's.

**2. What intentionally differs.** The entire activation model. Legacy: one container-wide `JEX_BATCH` boolean +
per-batch cron files + every-minute unconditional polling, no locking, no mock, no alert gate, no recovery, no
timezone config. Refactor: per-feature flags defaulting OFF, never-fire cron defaults, double-gating on mutating
sweeps, ShedLock, mock clients, a callback IP filter, and request-time/post-commit dispatch instead of polling
(the approved Mr. Kang TOBE). These are improvements, and 12 refactor properties have **no legacy counterpart at
all** — recovery (×3), stuck-monitor (×5), alert labels (×2), operating-hours zone, cutoff refresh.

**3. What is still missing.** `WECMS_EFS_PARTNER_ID` empty in every template; `allowed-ips` empty; EFS 201
returning `RS_CD='1000'` 17/17; `alert.enabled=false` everywhere with the two alert-label keys unbindable (P-7);
no legacy↔refactor interlock (§5); no auditable view of deployed flags (P-2); and the operating-hours audit's
**F-4 (HIGH, open)** still breaks VCB+VND file dispatch on three payment types.

**4. What must remain disabled.** `realtime-dispatch`, `auto-receive`, `result-check.cron`, `recovery.enabled`,
`recovery.cron` — for as long as the live JEX batch operates on the same tables. `recipient-inquiry` stays off
because it is broken, not because it is unsafe.

**5. What can safely be enabled.** `stuck-monitor.enabled` + `stuck-monitor.cron` (read-only, no `PROC_STAT`
write), paired with `alert.enabled` + `efs.enabled` so the report is delivered. Nothing else.

**6. Are code changes required?** **Yes — two, and only for one flag.** R-1 (tenant/origin predicate on the
recovery sweep) and R-2 (remove the hidden result-check coupling) must land before `recovery.enabled` may ever
be `true`. R-3 and R-4 are strongly recommended. **No code change is needed to keep the system safe in its
current all-OFF state.**

**7. Is deployment/configuration work sufficient otherwise?** For everything except recovery, **yes** —
provisioning (partner id, callback IPs, SFTP credentials, VAN keys), the EFS-201 root cause, alert enablement,
and the P-2 governance fix are configuration and infrastructure work, not code.

**8. Readiness by environment.**

| Env | Verdict |
|---|---|
| **SIT** | **READY for continued inert/mock operation.** Templates are correct and mock is intended. Not ready for real dispatch: `partner-id` empty, EFS 201 failing, alerts off. Safe next step is enabling the stuck monitor. |
| **UAT** | **NOT READY.** Blocked by: empty `partner-id`; mock-fallback risk with no prod assertion (P-8); alerts off and unlabelled (P-7); no legacy interlock; F-4 open. |
| **PROD** | **NOT READY.** All UAT blockers, plus: R-03 callback hardening, the P-2 governance gap (deployed flags not auditable), the P-6 timezone decision, and R-1/R-2 if recovery is ever in scope. |

### Verification labels for this audit

- **Verified by code:** every property/consumer mapping in §3; the gating logic in §4.1–4.3, 4.5; the mock/real bean conditions in §4.4.
- **Verified by configuration:** all YAML and `.env*` values in §3 and §7.
- **Verified by database (read-only):** `shedlock` existence + 8 rows + the absence of any dispatch-job lock; `tb_batch_schedule_info` = 3, `_dtl` = 12, `tb_firm_conf` = 3, `tb_van_api_info` = 2.
- **Verified against legacy:** §2 in full — `jex.prop`, the four `.batch` files, `web.xml`, `wecms_batch.properties`, `wecms_batch_HS.properties`, `MsgSendUtil`/`MSGSendAPIThread`, `ConnectionUtil`, `SftpUtil`, the IDO XMLs, and the framework behaviour extracted from `JexSimpleBatch.0.3.0.jar`. Negative findings are listed with the searches behind them (§2.3).
- **Inferred but not runtime tested:** the startup-abort consequence of an absent no-default env var (P-1/§4.4) — Spring strict nested-placeholder semantics; the duplicate-dispatch scenarios in §4.5 and §5 (no dispatch was executed); the P-3 legacy-row impact (derived from the query signature + shared-DB target, not observed).
- **Unable to verify:** the **actual deployed flag values** in SIT/UAT/PROD — the deployment mechanism is outside this repository (P-2). Every environment column in §7 is a *committed template* value, not a runtime value.
