# WeCMS Batch — Deployment Readiness Audit

**Date:** 2026-07-28 · **System:** Refactor (`wecms-api`) firm-banking payment dispatch
**Scope:** the 13 requested verification items, across DEV / SIT / UAT / PROD
**Method:** static source audit of `wecms-api` (code, `application*.yml`, `.env.*`, `build.gradle`, Flyway
scripts). **The `postgres_dev_server_refactor` and `postgres_dev_server_jexframe` MCP servers were not
available in this session**, so no DB object was re-queried. Every DB fact below is carried from the
read-only queries executed earlier the same day and recorded in
[wecms-batch-infrastructure-readiness-audit.md](wecms-batch-infrastructure-readiness-audit.md) §4 — it is
**dated evidence, not live evidence**. Re-confirm before acting on any UAT/PROD decision.

**No secrets in this document.** Credentials are recorded as presence/absence only.

> **Overall verdict: FAIL — not ready for deployment.**
> 11 of 13 items FAIL. The two that pass are the safety mechanisms that keep the subsystem inert; the
> failures are concentrated in provisioning, observability, and resilience. No blocker is a mainline coding
> defect — the dispatch implementation itself is real and production-grade
> (see memory `project_batch_refactor_real_impl`).

---

## 1. Verdict summary

| # | Item | Verdict | One-line reason |
|---|---|---|---|
| 1 | DB seed | **FAIL** | Seeded and verified on DEV/SIT only; UAT/PROD unseeded; no seed in Flyway |
| 2 | Dispatch flags | **FAIL** | Deployed state contradicts every committed template — repo is not a source of truth |
| 3 | Scheduler status | **FAIL** | 13 `@Scheduled` methods on a **1-thread** pool; 2 jobs unlocked; H-7 day-boundary bug open |
| 4 | Batch configuration | **FAIL** | No `application-dev.yml`; no in-YAML defaults (missing var ⇒ startup abort); PROD template is a UAT clone |
| 5 | Mock clients | **PASS** | Both mocks **throw**; inverse `@ConditionalOnProperty` — no fake-success path exists |
| 6 | Real clients | **PARTIAL** | Real implementations present and correctly gated, but SFTP runs `StrictHostKeyChecking=no` |
| 7 | API credentials | **FAIL** | `TB_VAN_API_INFO` DEV/SIT only; `WECMS_EFS_PARTNER_ID` empty in all 4 templates, silently substituted |
| 8 | SFTP | **FAIL** | Not provisioned in any environment; host key checking disabled; staging paths fall back to temp dir |
| 9 | EFS | **FAIL** | Gateway reachable but rejecting **17/17** with `RS_CD='1000'`; no base-url for UAT/PROD |
| 10 | Retry | **FAIL** | No retry exists anywhere on any transport; the only recovery net is double-gated OFF |
| 11 | Monitoring | **FAIL** | Actuator is not a dependency — `/actuator/health` 404s; no metrics |
| 12 | Logging | **FAIL** | No rotation, no file appender, no JSON; SIT dumps full bodies + JDBC bind params |
| 13 | Alerting | **FAIL** | Implemented and wired, but OFF everywhere **and** its only transport is the EFS gateway |

---

## 2. Item-by-item findings

### 1. DB seed — FAIL

| Object | DEV / SIT | UAT | PROD |
|---|---|---|---|
| `TB_BATCH_SCHEDULE_INFO` | 3 rows (`DBA_MANUAL`, verified row-by-row) | **not seeded** | **not seeded** |
| `TB_BATCH_SCHEDULE_DTL` | 12 rows (verified row-by-row) | **not seeded** | **not seeded** |
| `TB_VAN_API_INFO` | 2 rows, both keyed | **missing** | **missing** |
| `TB_FIRM_CONF` | 3 rows, all `ORG_STAT='1'` | unconfirmed | unconfirmed |
| `shedlock` | 8 rows, single holder | unconfirmed | unconfirmed |

- The schedule seed is **not in Flyway**. Verified independently: `V95__tb_batch_schedule_dtl.sql`,
  `V96__tb_batch_schedule_info.sql`, `V100__tb_firm_conf.sql`, `V111__tb_van_api_info.sql` contain
  **zero `INSERT` statements** — they are DDL-only. The seeds live as hand-run scripts at
  `Claude Code (AI)/sql/tb_batch_schedule_seed_refactor.sql` and `tb_van_api_info_seed_20260612.sql`.
  This is correct per the manual-DDL policy, but it means **UAT and PROD get nothing automatically**.
- Consequence if a leg is enabled on an unseeded DB: `OperatingHoursService` returns `NO_CRITERIA` and both
  `RealtimeDispatchService` and `BulkReceptionService` **fail the row** — 100 % of transfers fail before any
  gateway call.
- Residual on the seeded DB: no realtime `TB_BATCH_SCHEDULE_DTL` row for `SWIFT`/`EBANK`, so an `'S'`/`'E'`
  target still fails `NO_CRITERIA` (open PO routing decision). Two `TB_FIRM_CONF` rows share
  `(VN_VNM0004_000001, GV00000001)` — tolerated by `exists`, non-deterministic for any single-row finder.

**Verdict:** PASS on DEV/SIT, FAIL on UAT/PROD. Deployment readiness is judged on the target environments → **FAIL**.

### 2. Dispatch flags — FAIL

All six gates are `false` / `"-"` in every committed template — which is the correct posture:

| Variable | `.env.local` | `.env.sit` | `.env.uat.example` | `.env.prod.example` |
|---|---|---|---|---|
| `WECMS_DISPATCH_AUTO_RECEIVE` | false | false | false | false |
| `WECMS_DISPATCH_REALTIME_DISPATCH` | false | false | false | false |
| `WECMS_DISPATCH_RECIPIENT_INQUIRY` | false | false | false | false |
| `WECMS_DISPATCH_RESULT_CHECK_CRON` | `-` | `-` | `-` | `-` |
| `WECMS_EFS_ENABLED` | false | false | false | false |
| `WECMS_SFTP_ENABLED` | false | false | false | false |
| `WECMS_DISPATCH_ALERT_ENABLED` | false | false | false | false |

The failure is not the values — it is that **they describe nothing**. `TB_FIRM_TRAN` holds 17 rows
`TRAN_TP='201'` dated 2026-07-22 / 2026-07-24. Those rows can only be written with
`recipient-inquiry=true`, and `RS_CD='1000'` can only be parsed from a real gateway envelope
(`MockEfsApiClient` throws, which would give `G999`) — so `efs.enabled=true` was live too. Runtime authority
on SIT is the host's `bin/setenv.sh`, which is invisible to this repository (memory
`reference_sit_server_config_setenv`).

**Rule going forward:** never state an environment's flag state from the repo. Require
`env | grep -E '^(WECMS|HAENGSUNG)_'` (redacted) from the host, plus a `shedlock` + `TB_FIRM_TRAN` query.

### 3. Scheduler status — FAIL

Both dispatch jobs are correctly inert — `ResultCheckJob.run` and `StrandedRecoveryJob.run` are **absent
from `shedlock`**, confirming they have never fired. Cron defaults to Spring's never-fire `"-"`
([ResultCheckJob.java:39](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L39),
[StrandedRecoveryJob.java:32](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/StrandedRecoveryJob.java#L32)),
and recovery is additionally gated by `recovery.enabled=false`.

Three problems:

- **Thread starvation (new finding).** `application.yml:34-38` sets
  `spring.task.scheduling.pool.size: 1`, and there are **13 `@Scheduled` methods** in the tree:
  4 exchange-rate, 3 Haengsung sync, 2 operation-status, 1 NXiSAS session sweep, 1 cutoff refresh,
  plus the 2 dispatch jobs. They all serialise on one thread. `ResultCheckJob` at a 1-minute cron would
  queue behind an FX scrape or a 60 s SFTP connect timeout, and `lockAtMostFor = "PT2M"` is sized for a job
  that runs promptly. Raise the pool (or isolate the dispatch jobs on their own scheduler) **before**
  setting `result-check.cron`.
- **Two unlocked jobs.** `NxisasSessionRegistry:68` and `CutoffConfigProvider:133` have no
  `@SchedulerLock`. Benign today (node-local caches, single instance) — must be re-confirmed on scale-out.
- **H-7 still open.** API-rail `TRAN_DT`/`REG_TM` are stamped UTC while `ResultCheckService` selects on
  ICT-today. This must be fixed before `result-check.cron` is ever set.

### 4. Batch configuration — FAIL

- **No `application-dev.yml`** — DEV is a named target environment with no Spring profile.
  `application.yml` defaults to `local`, and `application-local.yml` is gitignored, so a fresh clone or a
  DEV host has no bindable profile.
- **No in-YAML defaults for the dispatch variables** (`${WECMS_DISPATCH_AUTO_RECEIVE}` etc. with no `:default`)
  — a single missing host variable aborts startup. Fail-safe, but every variable must be set on every host.
  The `recovery.*` keys are the exception (they carry `:false` / `:-` / `:30`).
- **`.env.prod.example` is a UAT clone** — PROD inherits UAT storage paths, gateway URL and
  `WECMS_SFTP_PORT=3300` unverified.
- `batch-dispatch-variable-reference.md` (created 2026-07-28) documents the previously-undocumented `RECOVERY_*` and alert
  label variables, but it is **untracked** — not committed, so it is not yet part of the deliverable.
- `wecms.dispatch.alert.env-mode` / `.was-no` are read via `@Value` but declared in **no** profile YAML;
  with them unset the alert label degrades to the active Spring profile name instead of `DEV`/`UAT`/`PROD`.
- Hardcoded literals that cannot be tuned per environment without a rebuild: EFS/SFTP timeouts,
  `callback.trust-forwarded-for: true`, and the three `ExchangeRateBatchScheduler` crons.

### 5. Mock clients — PASS

The one item with no reservations.

- `MockEfsApiClient.send` **throws** `UnsupportedOperationException`
  ([MockEfsApiClient.java:26-31](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/van/client/MockEfsApiClient.java#L26-L31)).
- `MockSftpClient.upload/download/move` all **throw**
  ([MockSftpClient.java:24-45](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/sftp/MockSftpClient.java#L24-L45)).
- Both are bound by inverse `@ConditionalOnProperty(havingValue = "false", matchIfMissing = true)`, so they
  are mutually exclusive with the real clients — no duplicate-bean conflict, and no configuration mistake can
  produce a silently-faked success on a money-moving path. A mock in the path fails the row loudly.

### 6. Real clients — PARTIAL

Real, non-stub implementations exist and are correctly gated on `enabled=true`:
`RestClientEfsApiClient` (+ `EfsClientConfig` building `efsRestClient` with explicit 3 s connect / 20 s read
timeouts) and `JschSftpClient` (real JSch upload/download/move).

One production-hardening defect:

- **`StrictHostKeyChecking=no`** at
  [JschSftpClient.java:129](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/sftp/JschSftpClient.java#L129),
  with no `known_hosts` configuration anywhere. Documented as legacy `SftpUtil` parity, and it is faithful
  parity — but it means the bulk payment-file channel accepts **any** host key, so a redirected or spoofed
  SFTP endpoint is indistinguishable from the real one. Legacy parity is the correct default for behaviour;
  it is not a good reason to carry a transport-security hole into PROD. Raise with the PO as a deliberate
  divergence (pin the host key) rather than inheriting it silently.

### 7. API credentials — FAIL

- `TB_VAN_API_INFO`: 2 rows, both `API_KEY` populated on the DEV/SIT DB. **Missing on UAT and PROD.**
- `VanApiKeyService.getApiKey` **throws** when no row exists
  ([VanApiKeyService.java:46-52](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/van/client/VanApiKeyService.java#L46-L52))
  — fails closed on a money-moving path. Correct design.
- Keys are never in YAML, env, or the repo; `EV`/`VV`/`API_KEY` values are never logged.
- **`WECMS_EFS_PARTNER_ID` is empty in all four templates**, and the code does not fail on that — all seven
  envelope-build sites do
  `StringUtils.hasText(partnerId) ? partnerId : cmd.vanOrgCd()`
  (e.g. [RealtimeTransferService.java:103-104](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeTransferService.java#L103-L104)).
  So every live call silently sent `ID = GV00000001` instead of a provisioned partner id (the vendor sample
  is `CORP000001`-shaped).

  **Hypothesis, not a conclusion:** an unrecognised envelope `ID` is a plausible cause of the 17/17
  `RS_CD='1000'` rejections in item 9. It is cheap to test — provision the partner id and re-probe 201
  before escalating further to VAN Center. Either way, the silent substitution is wrong for a money path:
  a blank partner id should fail loud like the missing API key does.

### 8. SFTP — FAIL

Not provisioned in **any** environment:

| Setting | DEV/SIT template | UAT/PROD template |
|---|---|---|
| `WECMS_SFTP_ENABLED` | false | false |
| `WECMS_SFTP_HOST` | `42.96.55.163` | *(blank)* |
| `WECMS_SFTP_USERNAME` | *(blank)* | *(blank)* |
| `WECMS_SFTP_KEY_PATH` | `/home/was/.ssh/id_rsa` (placeholder) | *(blank)* |

Plus: host key checking disabled (item 6); the private key file and its `0600` permissions are unverifiable
from the repo; and `WECMS_SFTP_LOCAL_UPLOAD` / `_LOCAL_DOWNLOAD` are empty in `.env.sit.example`, where the
code silently falls back to `System.getProperty("java.io.tmpdir")` — payment files would land in the OS temp
directory.

### 9. EFS — FAIL

- **The integration is reachable and rejecting.** All 17 live `TRAN_TP='201'` calls returned
  `PROC_STAT='3'`, `RS_CD='1000'`, `RS_MSG='WBE_EFS_GATEWAY_ERROR'`. `1000` is not a `FirmBankingResultCd`
  constant and is undocumented in the repo — it came from the gateway. Owner: VAN Center (see the partner-id
  hypothesis in item 7 for a cheap first test).
- `WECMS_EFS_BASE_URL` is set only in the DEV/SIT templates; blank for UAT and PROD.
- Endpoint paths for all seven legs (201/202/203/204/301/302/MSG) default correctly in code — no
  configuration needed there.
- **No truststore/keystore configuration.** `EfsClientConfig` uses `SimpleClientHttpRequestFactory` with
  default JVM trust. There is no TLS bypass anywhere in the tree (verified — no `X509TrustManager`, no
  hostname-verifier override), which is good; but if the gateway presents a private-CA certificate, the CA
  must be imported into the JVM truststore and nothing in the repo does that.

### 10. Retry — FAIL

**There is no retry.** Verified three ways:

- `build.gradle` has **no** `spring-retry`, no `resilience4j`, no circuit breaker. No `@EnableRetry`,
  `@Retryable`, or `RetryTemplate` anywhere in `src/main/java`.
- Every "retry" hit in the dispatch and payment packages is a **one-shot key-allocation race retry** on
  `DataIntegrityViolationException` (`ExpensePaymentService`, `SyncHistoryWriter`,
  `OwnAccountTransferService`, …) — a PK-collision guard, not a transport retry.
- The legacy `FILE_DEPOSIT_TRANSFER_BATCH2` retry loop was removed along with the polling batches when the
  event-driven model was adopted (the approved TO-BE, memory `project_batch_event_driven_tobe_recovery`).
  `FirmBankingResultCd` explicitly notes the gateway retry-code set is unverified "before wiring retry logic".

So a transient HTTP timeout, TCP reset, or SFTP hiccup **fails the payment row permanently** — there is no
second attempt at any layer. The only recovery net, `StrandedRecoveryService` / `StrandedRecoveryJob`, is
double-gated OFF (`recovery.enabled=false` **and** cron `"-"`) pending the PO/Mr. Kang coexistence decision.
Note also that the monitoring plan lists "retry exhaustion" as a signal to watch — there is currently no
retry to exhaust.

This is acceptable while everything is inert. It is not acceptable at the moment a leg is enabled: decide
consciously whether to add bounded transport retry, enable the recovery sweep, or accept manual ops
reprocessing — and write that decision down.

### 11. Monitoring — FAIL

**`spring-boot-starter-actuator` is not a dependency.** Verified: no `actuator` string in `build.gradle`,
and no Micrometer / Prometheus registry. Yet:

- `SecurityConfig:75` permits `/actuator/health`,
- `RequestResponseLoggingFilter:69` skips `/actuator`,
- `wecms-api/CLAUDE.md` documents `/actuator/health` as a live endpoint,
- the SIT deployment checklist has a "`/actuator/health` UP" step.

All four describe an endpoint that returns **404**. There is no health check, no readiness probe, and no
metric of any kind.

What does exist and is genuinely useful: `traceId` correlation (`TraceIdFilter` → MDC → `X-Trace-Id`
response header, `%X{traceId}` in the log pattern); per-transaction audit rows in `TB_FIRM_TRAN`,
`TB_FIRM_BAT_API_TRAN` and `TB_FIRM_FILE_RSLT`; and `shedlock.locked_at` as a per-job heartbeat.
Everything operational must currently be observed by SQL.

**`TB_FIRM_BAT_API_TRAN` rollback survival is per-writer, not global.** An earlier revision of this
section asserted these rows are "written `REQUIRES_NEW`, so they survive a rollback" without
qualification. That is true of exactly one writer and false of the others, which matters because this
section is read to decide whether an operator can reconstruct what happened from the ledger alone:

| Leg | Writer | `REQUIRES_NEW`? | Survives a rollback of its handler? |
|---|---|---|---|
| `01302` execution-result callback | `BulkExecutionResultService.writeApiTran` → `DispatchApiTranAuditWriter.saveInNewTransaction` | **yes** | **yes** — this is the G1 / audit M-7 fix, restoring legacy's commit-audit-before-parse ordering |
| `00302` execution request (inline from `01301`) | `BulkReceptionResultService.writeApiTran` (private, no propagation) | no | no — joins `handle()`'s transaction and rolls back with it |
| `01301` reception-result callback | same private `writeApiTran` | no | no |
| `202`/`203`/`204`/`00301` legs | direct `firmBatApiTranRepository.save(...)` in the caller's transaction | no | no |

So: an operator reconciling a **01302** failure can rely on the audit row being present; one
reconciling a rolled-back **01301**/**00302** callback cannot, and must fall back to the application
log (`traceId`). Do not generalise the 01302 guarantee to the rest of the ledger.

### 12. Logging — FAIL

Good: `traceId` on every line, `EV`/`VV`/`API_KEY` never logged as values, account numbers masked on the
dispatch paths (`RecipientLookupService` uses `mask(...)`).

Failing:

- **No `logback-spring.xml` anywhere** — console/pattern layout only. No file appender, no rotation, no size
  cap, no JSON. On the Tomcat hosts everything goes to `catalina.out` and grows without bound.
- **SIT leaks payload data.** `application-sit.yml:156-164` sets `com.kosign.wecms: DEBUG` — which activates
  the full request/response body dump in `RequestResponseLoggingFilter` (8 000 chars) — **plus**
  `org.springframework.jdbc.core: TRACE` and `org.hibernate.orm.jdbc.bind: TRACE`, which log every SQL bind
  parameter. The `SENSITIVE_FIELD` redaction (F-06) matches **JSON field names only**, so it covers
  passwords and tokens but not account numbers, amounts, EFS envelope fields, or any bind parameter. UAT and
  PROD are at `INFO` / `WARN` and are safe.

### 13. Alerting — FAIL

The implementation is the strongest part of this item: `OpsAlertService` is wired at **12 call sites** across
`RealtimeDispatchService`, `BulkReceptionService`, `BulkExecutionResultService`, `DepositorInquiryService` and
`ResultCheckService`; it is `@Async` fire-and-forget on a dedicated executor, wraps its whole body in
try/catch, logs failures at `warn` and never propagates — so a slow or failing alert can never affect a
dispatch. Failure-only, with `[ENV][WAS_NO]` server labelling.

Why it still fails:

- `WECMS_DISPATCH_ALERT_ENABLED=false` in every environment — `notify` returns immediately.
- **Its only transport is the EFS gateway itself** (`WGVN_MSG_SEND_001` via the shared `EfsApiClient`), so it
  additionally requires `efs.enabled=true` **and** a seeded `TB_VAN_API_INFO` key. There is no independent
  channel — no SMTP, no webhook, no local sink. That is a single point of failure in exactly the scenario
  alerting exists for: **if the gateway is down or rejecting (which it currently is — item 9), the alert
  about it cannot be delivered.** `MSG_SND_HIS` is empty, confirming no alert has ever been sent.
- `alert.env-mode` / `.was-no` are bound in no YAML (item 4), so labels would read the profile name.

---

## 3. Blockers, in the order they should be cleared

| # | Blocker | Owner | Gates |
|---|---|---|---|
| B1 | Establish the authoritative runtime config channel and capture the real flag state of every host (`env | grep`, redacted). Reconcile with the templates. | Ops + Arthur | everything — nothing else can be trusted until this is done |
| B2 | Add `spring-boot-starter-actuator` (+ Micrometer) and re-point the SIT checklist at a health endpoint that exists. | Dev | all activation checklists, all monitoring |
| B3 | Provision `WECMS_EFS_PARTNER_ID`, then re-probe EFS 201. If `RS_CD='1000'` persists, escalate to VAN Center with the envelope. | Ops → VAN Center | every EFS leg |
| B4 | Seed `TB_BATCH_SCHEDULE_INFO` / `_DTL`, `TB_VAN_API_INFO`, `TB_FIRM_CONF` on the UAT and PROD DBs; de-duplicate the `TB_FIRM_CONF` pair. | DBA + PO | realtime + bulk dispatch |
| B5 | Decide the retry / recovery posture (bounded transport retry vs. enabling the sweep vs. accepted manual reprocessing) and record it. | PO / Mr. Kang | enabling any leg |
| B6 | Fix H-7 (UTC vs ICT day boundary) and raise the scheduler pool above 1. | Dev | setting `result-check.cron` |
| B7 | Close the callback surface: populate `WECMS_CALLBACK_ALLOWED_IPS` (the filter fails **open** when empty) and make `trust-forwarded-for` configurable with a proxy that strips `X-Forwarded-For`. | Ops + Dev | enabling `auto-receive` |
| B8 | Provision SFTP (host / user / key at `0600`) and pin the host key instead of `StrictHostKeyChecking=no`. | Ops + PO | bulk file channel |
| B9 | Add a logback config with rotation; drop SIT off `DEBUG`/`TRACE` or accept the payload exposure in writing. | Dev | SIT sign-off |
| B10 | Give alerting a transport that does not depend on the gateway it monitors. | Dev + PO | meaningful operability |
| B11 | Add `application-dev.yml`; de-clone `.env.prod.example`; commit `batch-dispatch-variable-reference.md`. | Dev + Ops | DEV/PROD deploy |

---

## 4. What is genuinely ready

Stated plainly, because the failure list above is long and the code is not the problem:

- The dispatch implementation is real, production-grade code — EFS legs 201/202/203/204/301/302, the
  1000-byte `FixedLengthCodec` (byte-verified against 6 UAT samples), `EfsCryptoService` (AES-256/CBC +
  HMAC-SHA256 + constant-time `VV` verify), both callbacks, and the real DB write paths.
- The safety architecture holds: every leg flag-gated, mocks that throw instead of faking success, both
  schedulers defaulting to never-fire, `VanApiKeyService` failing closed, ShedLock on all eight
  business-critical jobs, and no TLS bypass anywhere.
- Schema (`ddl-auto: validate` passing), the `shedlock` table, and the DEV/SIT seed set.
- Audit and correlation: `traceId` end-to-end, `TB_FIRM_TRAN` / `TB_FIRM_BAT_API_TRAN` / `TB_FIRM_FILE_RSLT`.

The gap between this and "deployable" is provisioning, observability, and resilience — not implementation.

---

## 5. Cross-references

- [wecms-batch-infrastructure-readiness-audit.md](wecms-batch-infrastructure-readiness-audit.md) — the
  source of every DB fact here (§4), full dependency matrix (§6), finding IDs C-1…C-5 / H-1…H-10 / M-1…M-6
- [wecms-batch-schedule-seed-review.md](wecms-batch-schedule-seed-review.md) — seed spec + §17 verification
- [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md) — activation + rollback order
- [wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) — per-variable reference
- [wecms-batch-activation-and-external-confirmations.md](wecms-batch-activation-and-external-confirmations.md)
  — prior (2026-07-21) activation audit; its "all dispatch OFF" conclusion is superseded by C-1
- Memory: `project_batch_deployed_flags_diverge_from_repo`, `project_batch_refactor_real_impl`,
  `project_batch_event_driven_tobe_recovery`, `project_batch_schedule_tz_antdiv_fixes`,
  `reference_sit_server_config_setenv`
