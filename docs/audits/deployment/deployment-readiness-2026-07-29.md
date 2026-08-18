# WeCMS Refactor — Deployment Readiness Audit

> ✅ **CANONICAL deployment-readiness audit.** Full re-audit from source, 2026-07-29. Supersedes
> [`deployment-readiness-2026-07-28.md`](../historical/deployment-readiness-2026-07-28.md) (preserved as a
> historical record — the two disagree on some items and the 07-28 findings remain valid for their date).



> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Scope:** firm-banking payment dispatch + the
schedulers, configuration and infrastructure it depends on, across DEV / SIT / UAT / PROD.

**Method:** full re-audit from current source. Every item was re-read in the working tree; **no conclusion
from the 2026-07-28 pass was carried forward unverified** — and that mattered, because a substantial
scheduler/config remediation landed in the working tree since (uncommitted; see §2).

**Evidence limits (stated plainly):**
- The `postgres_dev_server_refactor` and `postgres_dev_server_jexframe` MCP servers were **unavailable** in
  this session. **No database object was live-verified.** Every DB statement below is marked
  **`NOT LIVE-VERIFIED`** and is sourced from the read-only queries of 2026-07-28 recorded in
  [../deployment/wecms-batch-infrastructure-readiness-audit.md](../../deployment/wecms-batch-infrastructure-readiness-audit.md) §4.
- No host `env` output was available, so **no environment's runtime flag state is asserted here.**
- Code is the source of truth throughout; documentation was consulted only after reading the code, and is
  contradicted where it disagrees (see §6, doc drift).
- **No file was modified during the audit pass.** Coverage measurement used a `--init-script` outside the
  repo (see the companion [test-coverage-audit.md](../legacy-parity/test-coverage-audit.md)). A **separate fix pass later
  the same day did change code** — D-1/D-2/D-3, recorded in the UPDATE block below and in
  [legacy-verification.md](../legacy-parity/legacy-verification.md). Nothing was committed or pushed.

> **Supersedes** [../deployment/deployment-readiness.md](../historical/deployment-readiness-2026-07-28.md) (2026-07-28).
> Where the two differ, this document is current — six of that document's findings are now **fixed in code**.

> ## UPDATE 2026-07-29 (later same day) — D-1, D-2 and D-3 are CLOSED
>
> All three code defects in §3 have been fixed after a legacy-first investigation. Full evidence, per-leg
> legacy citations and the decision record: **[legacy-verification.md](../legacy-parity/legacy-verification.md)**.
>
> | Finding | Outcome | Note |
> |---|---|---|
> | **D-1** | **FIXED** — reader aligned to the writer's clock (UTC), previous-day window added, aging moved onto a monotonic timestamp | Legacy confirmed the *write* was already correct (`PymnHelper:2197` stamps `TB_PAY_TARGET.TRAN_DT` from `DateTimeUtils.utcNow()`); the **reader** was the defect. `Option A` (move the write to ICT) was rejected — it would have broken the shared `(BIZ_KEY, TRAN_DT, TRAN_SEQ)` key that `DispatchResultTxService` uses for both tables, mirroring legacy's single `org_proc_dt` (`PROCESSING_RESULT_CHECK_BATCH:301,319`) |
> | **D-2** | **FIXED — and the audit recommendation was reversed** | Legacy has **no partner-id property**: the envelope `ID` is `TB_FIRM_CONF.VAN_ORG_CD` on all seven legs. So the "fallback" was the only legacy-correct branch and the *override* was the defect. The property was removed rather than made fail-fast. **`RS_CD='1000'` is now disproven as related** — the 17 live calls sent exactly what legacy sends |
> | **D-3** | **FIXED** — actuator added; `health` public, `info` authenticated, nothing else exposed, `show-details: never` | C-2 below is closed |
>
> **Verdict change:** deployment readiness stays **FAIL**, and *not* because of these three. What remains is
> provisioning (C-3…C-5), configuration (C-6, H-3, H-6), and four decisions (H-1, H-2, H-4, H-5) — see §5,
> where C-1/C-2 and M-1 are now struck through.
>
> **Verification:** real working tree, `./gradlew clean compileJava cleanTest test jacocoTestReport
> --no-build-cache` → **BUILD SUCCESSFUL**, **269 suites · 2171 tests · 2151 passed · 0 failed · 0 errors ·
> 20 skipped**. Coverage up on every axis (app 56.0 % line / 41.3 % branch; dispatch 79.9 % / 60.6 %;
> `ResultCheckService` 93.8 % / 75.6 %).

---

## 1. Overall verdict

| Dimension | Verdict |
|---|---|
| Deployment readiness | **FAIL** (unchanged after the D-1/D-2/D-3 fixes — the remaining blockers are not code) |
| Legacy parity | **PARTIAL** — dispatch legs parity-complete and byte-verified; the mixed-zone reader regression (D-1) is **now fixed and legacy-verified**; SFTP host-key parity is a deliberate carry-over of a legacy weakness |
| Code implementation | ~~REQUIRES FIXES~~ → **D-1/D-2/D-3 CLOSED.** Two non-defect code items remain: no Micrometer registry (metrics), and `trust-forwarded-for` is hardcoded rather than configurable (H-2) |
| Ready for UAT | **NO** |
| Ready for PROD | **NO** |

**The system is not ready, and specifically not because the flags are off.** Flags-off is the correct
posture and is now *provably* the default (§2, `SchedulerConfigurationContractTest`). As originally
audited it failed because a correctness defect fired the moment `result-check.cron` was set, there was no
health endpoint, no retry at any layer, unprovisioned UAT/PROD seeds and credentials, and a gateway
rejecting 100 % of requests. **The first two are now fixed** (D-1, D-3); what still blocks deployment is
provisioning, four open decisions, and configuration — none of which this repo can close on its own.

---

## 2. What changed since 2026-07-28 — verified fixed in code

These were open findings yesterday. Each was re-read today and is **closed**. All are **uncommitted working-tree
changes** — they are real code, but they are not yet in Git history (`git status` shows 30 modified files, 1
deleted, plus new files), which is itself a deployment-process risk (§5 T-9).

| Prior finding | Status | Evidence (current code) |
|---|---|---|
| H-1 — dispatch vars had no in-YAML default; one missing var aborts startup | **FIXED** | Every key now carries a safe default: [application-local.yml:37-80](../../../src/main/resources/application-local.yml#L37-L80), and the same in `application-sit/uat/prod.yml`. e.g. `auto-receive: ${WECMS_DISPATCH_AUTO_RECEIVE:false}`, `cron: ${…:-}`, `base-url: ${WECMS_EFS_BASE_URL:}` |
| Operating-hours zone hardcoded ICT | **FIXED** | Explicit property with in-code default so an unset value cannot abort startup — [OperatingHoursService.java:85-86](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L85-L86): `@Value("${wecms.dispatch.operating-hours.zone:Asia/Ho_Chi_Minh}")`. Javadoc:70-82 documents setting `Asia/Seoul` for bug-for-bug legacy parity |
| F-9 — `ResultCheckJob` `lockAtMostFor=PT2M` too tight | **FIXED** | Raised to `PT30M` with a full derivation from gateway timeouts + `DispatchPacer` rates — [ResultCheckJob.java:60-101](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L60-L101) |
| F-8 — two lock names over one body (result-check runnable twice) | **FIXED** | Shared lock identity — [JobLockNames.java:59](../../../src/main/java/com/kosign/wecms/common/lock/JobLockNames.java#L59) `DISPATCH_RESULT_CHECK`, acquired by both [ResultCheckJob.java:100](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java#L100) and `StrandedRecoveryService` programmatically |
| Cron collision: operation-status seed + refresh fired at the same instant in undefined order | **FIXED** | Consolidated to **one** cron running seed-then-refresh as one locked ordered unit — [OperationStatusBatchScheduler.java:98-100](../../../src/main/java/com/kosign/wecms/domain/operation/scheduler/OperationStatusBatchScheduler.java#L98-L100). Retired keys asserted absent by test (`SchedulerConfigurationContractTest:161-167`) |
| H-5 — `RECOVERY_*` / alert-label vars undocumented | **FIXED** | Documented in `batch-dispatch-variable-reference.md`; `RECOVERY_*` additionally carry in-YAML defaults |
| Haengsung sync crons ran at **2× legacy rate across 24 h** | **FIXED** | Corrected to legacy `ERP_0100_001/002` schedules: `0 */10 7-19 * * ?` (FX) and `0 */5 7-19 * * ?` (import) — `.env.uat.example` / `.env.prod.example` |
| SFTP client silently proceeded with unset host | **FIXED** | Fails loud — [JschSftpClient.java:112-114](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/sftp/JschSftpClient.java#L112-L114) throws `SftpTransferException("SFTP host/username not configured (B4)")` |

**New since yesterday, verified present:** `common/lock/JobLockNames` (10 named locks); ShedLock
`usingDbTime()` ([SchedulerLockConfig.java:53](../../../src/main/java/com/kosign/wecms/config/SchedulerLockConfig.java#L53)) removing host-clock skew;
a read-only stuck-pay-file monitor (`dispatch/monitor/` + `StuckPayFileMonitorJob`, double-gated off); and
`SchedulerConfigurationContractTest`, which parses the real YAML profiles and **asserts** every enable flag
defaults `false` and every cron defaults to never-fire — a genuine deployment-safety regression guard.

---

## 3. Code defects — the only items that require a code change

### D-1 · Result-check never sees rows dispatched 00:00–07:00 ICT — **Code defect · HIGH · money path**

`TB_FIRM_TRAN.TRAN_DT` is stamped from a **UTC** date while `ResultCheckService` filters it against an
**ICT** date. Full chain, all in current code:

1. The API-rail executors capture `nowUtc` and derive `TRAN_DT` from it (legacy parity, commit `0d24c50`):
   - [ExpenseDispatchExecutor.java:150-152](../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpenseDispatchExecutor.java#L150-L152)
   - [InternalTransferDispatchExecutor.java:176-178](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchExecutor.java#L176-L178)
   - [VendorDispatchExecutor.java:155-157](../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorDispatchExecutor.java#L155-L157)

   The comment at `ExpenseDispatchExecutor:149-150` states the intent explicitly:
   *"Same UTC TRAN_DT flows to the realtime dispatch lookup."*
2. That UTC `tranDt` is passed into
   [RealtimeDispatchService.dispatch(bizKey, tranDt, tranSeq)](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L112)
   and written verbatim onto the 202 ledger row at
   [RealtimeDispatchService.java:234](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L234) — `firmTran.setTranDt(tranDt)`.
   `TRAN_TM` on the same row is stamped **ICT** ([:146-148](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L146-L148), `LocalDateTime.now(ICT)`), so the row carries a **mixed-zone timestamp**.
3. [ResultCheckService.java:204-212](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/ResultCheckService.java#L204-L212) selects the aged set:
   ```java
   LocalDateTime now = LocalDateTime.now(ICT);          // ICT = Asia/Ho_Chi_Minh (:68)
   String today   = now.format(DT8);
   String cutoffTm = now.minusMinutes(RESULT_CHECK_DELAY_MINUTES).format(TM6);
   … .filter(t -> today.equals(t.getTranDt()))          // ICT date vs UTC-stamped column
     .filter(t -> … t.getTranTm().trim().compareTo(cutoffTm) <= 0)
   ```

**Failure scenario.** A VND API-path payment dispatched at 03:00 ICT on 2026-07-29 is stamped
`TRAN_DT = 20260728` (UTC date, since UTC is then 20:00 on the 28th). Every result-check pass that day
computes `today = 20260729`, so the row **never enters the aged set**. The filter is today-only — there is
no yesterday fallback — so it is never retried on any later pass either. The 202 row stays at
`PROC_STAT='6'` permanently and its `TB_PAY_TARGET` row stays `'6'`: a real payment dispatched to the bank
with no result confirmation, invisible to the only reconciliation loop. The window is **00:00–07:00 ICT
daily — 7 of every 24 hours.**

`TRAN_TM`-vs-`cutoffTm` is internally consistent (both ICT), so the time filter is *not* affected; the
defect is the date filter alone.

**Latent, not live:** `result-check.cron` defaults to `-`, so this cannot fire today. It becomes a live
money-path defect on the first environment that sets a cron. This is precisely the residual of prior
finding H-7, now traced end-to-end.

**Fix direction (not applied):** make the aged-set filter zone-agnostic with respect to the writer — either
compare against the UTC date (matching the writer), or widen to `{UTC-today, UTC-yesterday}`, or stop
filtering by date in Java and bound the query on the DB side. Whichever is chosen, add the missing test
(see [test-coverage-audit.md](../legacy-parity/test-coverage-audit.md) G-2) and state the zone contract for `TRAN_DT`
in one place, because `TRAN_DT` (UTC) and `TRAN_TM` (ICT) currently disagree on the same row by design.

### D-2 · Blank EFS partner id is silently substituted — **Code defect · MEDIUM**

All seven envelope-build sites do the same thing:

```java
String id = StringUtils.hasText(efsProperties.getPartnerId())
        ? efsProperties.getPartnerId() : cmd.vanOrgCd();
```

[RealtimeTransferService.java:103-104](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeTransferService.java#L103-L104) ·
[RecipientLookupService.java:91-92](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/recipient/RecipientLookupService.java#L91-L92) ·
[BulkReceptionService.java:704-705](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L704-L705) ·
[BulkReceptionResultService.java:469-470](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/service/BulkReceptionResultService.java#L469-L470) ·
[ResultCheckService.java:134-135](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/ResultCheckService.java#L134-L135) · (+2 more in `RealtimeTransferService`)

`WECMS_EFS_PARTNER_ID` is empty in **all four** templates and defaults to `""`
([EfsProperties.java](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/van/client/EfsProperties.java) `partnerId = ""`), so every live call
sends `ID = <vanOrgCd>` (`GV00000001`) instead of a provisioned partner id — the vendor sample is
`CORP000001`-shaped. Compare `VanApiKeyService.getApiKey`, which **throws** on a missing key: that is the
right pattern on a money path, and this site does the opposite.

**Worth testing first:** an unrecognised envelope `ID` is a plausible cause of the 17/17 `RS_CD='1000'`
rejections (§5 I-1). Provision the partner id and re-probe 201 before escalating to VAN Center.
Stated as a hypothesis — it is not proven.

### D-3 · Actuator is not a dependency, but three places assume it is — **Code defect (missing dependency) · HIGH for operability**

`grep -c actuator build.gradle` → **0**. There is no `spring-boot-starter-actuator` and no Micrometer.
Yet:
- [SecurityConfig.java:75](../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java#L75) permits `/actuator/health`,
- [RequestResponseLoggingFilter.java:69](../../../src/main/java/com/kosign/wecms/common/web/RequestResponseLoggingFilter.java#L69) skips `/actuator`,
- `wecms-api/CLAUDE.md` lists `/actuator/health` in its endpoint table, and the SIT checklist has an
  "`/actuator/health` UP" step.

All four describe an endpoint that returns **404**. There is no health check, no readiness probe, no metric.
This is a code change (add the dependency + expose health) plus a docs correction.

---

## 4. Item-by-item findings, classified

Classification per the requested taxonomy. **CD** = code defect · **LP** = legacy parity difference ·
**CFG** = configuration issue · **INF** = infrastructure/provisioning · **DEP** = deployment-only task ·
**PO** = business decision · **OK** = no issue.

| # | Item | Class | Status | Evidence |
|---|---|---|---|---|
| 1 | DEV / SIT / UAT / PROD configuration | **CFG** | **Deployment configuration incomplete.** SIT/UAT/PROD profile YAMLs exist and are safe-by-default; **`application-dev.yml` does not exist** although DEV is a named target and `application.yml` defaults `spring.profiles.active: local`, with `application-local.yml` untracked → a DEV host has no bindable profile. `.env.prod.example` is still a UAT clone: value-level diff is **only** DB host, CORS origin, Oracle host — PROD storage paths, gateway URL and `SFTP_PORT=3300` are inherited unverified | `ls src/main/resources/application*.yml`; `git ls-files`; `diff .env.uat.example .env.prod.example` |
| 2 | Runtime dispatch + scheduler flags | **CFG** / **INF** | **Code-side complete and provably safe** — every flag defaults false, every cron `-`, asserted by `SchedulerConfigurationContractTest`. **Runtime state UNABLE TO VERIFY** — no host `env` available. The 2026-07-28 DB evidence proved `recipient-inquiry` + `efs.enabled` were live on a deployed node while all templates said `false`; runtime authority is the host `bin/setenv.sh`, invisible to this repo. `.env.local` still carries the **retired** `OPERATION_STATUS_BATCH_SEED_CRON`/`_REFRESH_CRON` keys (untracked file, low impact) | `SchedulerConfigurationContractTest`; `.env.local:66-67` |
| 3 | DB seed data + Flyway scripts | **INF** | **Infrastructure not provisioned (UAT/PROD).** **Flyway is not a dependency at all** — the only `flyway` string in `build.gradle` is a comment; `ddl-auto: validate` in all 5 profiles; the 161 scripts under `db/migration/` are **DBA-run by hand**, per policy. `V95`/`V96`/`V100`/`V111` contain **0 INSERTs** (verified) → seeds are the hand-run scripts in `Claude Code (AI)/sql/`. DEV/SIT seeded 2026-07-28 (3 INFO + 12 DTL, 2 `TB_VAN_API_INFO` keys, 3 `TB_FIRM_CONF`) — **NOT LIVE-VERIFIED today**. UAT/PROD get nothing automatically; unseeded ⇒ `NO_CRITERIA` ⇒ **100 % of dispatches fail before any gateway call** | `grep -ci flyway build.gradle`; `grep -c insert V9*.sql` |
| 4 | EFS config / partner id / keys / endpoints | **CD** + **INF** | Endpoint map for all 7 legs (201/202/203/204/301/302/MSG) defaults correctly **in code** — no config needed. `base-url` set only in DEV/SIT templates; blank for UAT/PROD. Partner id empty everywhere and **silently substituted** (§3 D-2). API keys: `TB_VAN_API_INFO` 2 rows on DEV/SIT, **missing UAT/PROD** (NOT LIVE-VERIFIED). `VanApiKeyService` fails closed — correct. No truststore config; default JVM trust only (no TLS bypass anywhere — verified) | `EfsProperties.defaultEndpoints()`; `VanApiKeyService:46-52`; `EfsClientConfig` |
| 5 | SFTP config + host-key verification | **LP** + **INF** | **Not provisioned in any environment** (`username` blank everywhere; `KEY_PATH` a placeholder; host only in DEV/SIT). **`StrictHostKeyChecking=no`** with no `known_hosts` — [JschSftpClient.java:129](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/sftp/JschSftpClient.java#L129), commented `// legacy SftpUtil parity`. Faithful parity, and a real transport-security hole on the bulk payment-file channel: any host key is accepted, so a redirected endpoint is indistinguishable from the real one. Carrying it to PROD is a **PO decision**, not a default | `JschSftpClient:118-132` |
| 6 | Scheduler pool / ShedLock / crons / timezone | **CFG** (accepted trade-off) | **ShedLock coverage: 10 of 12 `@Scheduled` methods**, all via `JobLockNames`, provider `usingDbTime()`. The 2 unlocked (`NxisasSessionRegistry:68`, `CutoffConfigProvider:133`) mutate per-JVM caches — **correctly** unlocked. `pool.size: 1` ([application.yml:34-38](../../../src/main/resources/application.yml#L34-L38)) is **load-bearing by design**, not an oversight: `scheduler-legacy-parity-audit.md` §8.1 records that raising it would turn the cron-collision finding into a live bug. Residual risk is real and now quantified by the project's own analysis — `ResultCheckJob`'s Javadoc derives 4 min (all-Shinhan) to 13 min (all-Woori) for 200 aged rows, and for that whole time **every other scheduled job is blocked**. Not a defect; a documented coupling that must be monitored, and revisited before the aged set is allowed to grow | `JobLockNames`; `SchedulerLockConfig:45,53`; `ResultCheckJob:68-90` |
| 7 | Retry and recovery | **PO** + **CD-adjacent** | **No retry exists at any layer.** `build.gradle`: no `spring-retry`, no resilience4j, no circuit breaker; no `@Retryable`/`RetryTemplate`/`@EnableRetry` in `src/main/java`. Every "retry" in the tree is a one-shot PK-collision guard on `DataIntegrityViolationException`. The legacy BATCH2 retry loop was removed with the polling batches (approved TO-BE). **Recovery:** `StrandedRecoveryService` + `StuckPayFileMonitorService` are implemented and tested, both **double-gated off**. So a transient HTTP timeout or TCP reset **permanently fails a payment row**. Acceptable while inert; requires a written PO decision before any leg is enabled | `grep -ciE 'retry\|resilience' build.gradle` → 0 |
| 8 | Monitoring / Actuator / health / metrics | **CD** | **FAIL** — §3 D-3. What does exist: `traceId` correlation end-to-end, `TB_FIRM_TRAN` / `TB_FIRM_BAT_API_TRAN` (`REQUIRES_NEW`, survives rollback) / `TB_FIRM_FILE_RSLT` audit rows, and `shedlock.locked_at` as a per-job heartbeat. Everything operational must be observed by SQL | `grep -c actuator build.gradle` → 0 |
| 9 | Logging / sensitive data / rotation | **CFG** | **No `logback-spring.xml` anywhere** → pattern layout only; no file appender, no rotation, no size cap, no JSON. On Tomcat everything lands in `catalina.out` unbounded. **SIT exposes payload data**: [application-sit.yml:156-164](../../../src/main/resources/application-sit.yml#L156-L164) sets `com.kosign.wecms: DEBUG` (activating the 8 000-char request/response body dump in `RequestResponseLoggingFilter`, which is **not** profile-gated) **plus** `org.springframework.jdbc.core: TRACE` and `org.hibernate.orm.jdbc.bind: TRACE` — every SQL bind parameter. The `SENSITIVE_FIELD` redaction matches **JSON field names only**, so it covers passwords/tokens but not account numbers, amounts, EFS envelope fields, or any bind parameter. UAT/PROD are `INFO`/`WARN` — safe | filter has no `@Profile`; YAML as cited |
| 10 | Alerting + transport independence | **CD-adjacent** / **PO** | **Transport is NOT independent.** `OpsAlertService` is well built — wired at 12 call sites across realtime, bulk, callbacks, recipient inquiry and result-check; `@Async` fire-and-forget; try/catch around the whole body; never propagates; failure-only. But its **only** transport is the EFS gateway itself (`WGVN_MSG_SEND_001` via the shared `EfsApiClient`), so it needs `alert.enabled=true` **and** `efs.enabled=true` **and** a seeded VAN key. No SMTP, no webhook, no local sink. **If the gateway is down or rejecting — which it currently is — the alert about it cannot be delivered.** `MSG_SND_HIS` empty (NOT LIVE-VERIFIED) | `OpsAlertService:125-165`; `EfsProperties` `EFS_MSG` |
| 11 | Callback IP restrictions + forwarded headers | **CFG** + **CD** | **Fails open.** [CallbackIpWhitelistFilter.java:38](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/CallbackIpWhitelistFilter.java#L38): when `props.getAllowedIps().isEmpty()` the filter calls `chain.doFilter` and returns — and `WECMS_CALLBACK_ALLOWED_IPS` is **empty in every template**. `trust-forwarded-for: true` is **hardcoded** (not a variable) in all four profile YAMLs, and [:55-58](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/CallbackIpWhitelistFilter.java#L55) takes `X-Forwarded-For[0]` verbatim — spoofable unless a trusted proxy strips it. The callback controllers are `permitAll`. **Payload `VV` HMAC is therefore the only gate** (it is verified before any `EV` decrypt — that part is sound). Prior gate R-03/F-09, unchanged | as cited |
| 12 | Mock vs real client activation | **OK** (code) / see coverage doc | **Correct and the strongest item.** Mutually exclusive inverse `@ConditionalOnProperty`: real clients on `enabled=true` (`RestClientEfsApiClient`, `JschSftpClient`), mocks on `havingValue="false", matchIfMissing=true`. Both mocks **throw** rather than fake success — [MockEfsApiClient.java:26-31](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/van/client/MockEfsApiClient.java#L26-L31), [MockSftpClient.java:24-45](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/sftp/MockSftpClient.java#L24-L45). No configuration mistake can produce a silent fake success on a money path. **Caveat: neither mock is referenced by any test** (0 % coverage) — the fail-safe is correct by inspection, unguarded by regression | as cited |
| 13 | Remaining blockers | — | §5 |

### Legacy parity assessment

- **Parity complete and evidenced:** the six EFS legs + MSG; the 1000-byte fixed-length codec (byte-verified
  against 6 UAT sample files, `FixedLengthCodecTest` asserts exact 1000-byte records, `0x0D 0x0A` at the
  record tail, and H/D/T ordering); AES-256-CBC + HMAC-SHA256 + constant-time `VV`; both callbacks; the
  branch-split rule; the 204 Woori FX pre-step; `DispatchPacer` rates; operating-hours windows; the
  `ANT_DIV_CD` 1-char/5-char canonicalisation; and the recent A–M output-parity pass (12 findings).
- **Deliberate, recorded differences:** event-driven dispatch replacing the polling batches (approved TO-BE);
  ICT rather than the JEX DB's `Asia/Seoul` for operating-hours evaluation (property-switchable);
  `pool.size: 1` serialisation (legacy batches were independent).
- **Parity regression:** §3 D-1 — legacy stamped and read `TRAN_DT` consistently; the refactor stamps UTC and
  reads ICT.
- **Parity weakness carried over:** SFTP `StrictHostKeyChecking=no`.

---

## 5. Remaining blockers

| # | Blocker | Class | Owner | Gates |
|---|---|---|---|---|
| ~~**C-1**~~ | ~~**D-1** UTC/ICT day boundary strands 202 rows~~ **CLOSED 2026-07-29** — reader aligned to UTC + previous-day window + monotonic aging | Code defect | Dev | — |
| ~~**C-2**~~ | ~~Actuator absent~~ **CLOSED 2026-07-29** — `spring-boot-starter-actuator` added; `health` public, `info` authenticated, nothing else exposed. Metrics still absent (no Micrometer registry configured) | Code defect | Dev | metrics only |
| **C-3** | EFS 201 rejects 100 % with `RS_CD='1000'` (17/17, 2026-07-22/24) — **NOT LIVE-VERIFIED today** | Infrastructure | VAN Center (test **D-2** first) | every EFS leg |
| **C-4** | Deployed flag state unknowable from the repo; runtime authority is host `setenv.sh` | Infrastructure | Ops | trusting any environment statement |
| **C-5** | UAT/PROD: no `TB_BATCH_SCHEDULE_INFO`/`_DTL`, no `TB_VAN_API_INFO` — **NOT LIVE-VERIFIED** | Infrastructure/DBA | DBA + PO | realtime + bulk dispatch |
| **C-6** | No `application-dev.yml`; DEV has no bindable profile | Configuration | Dev | DEV deploy |
| **H-1** | Callback fail-open + hardcoded `trust-forwarded-for` + `permitAll` | Configuration + code | Ops + Dev | enabling `auto-receive` |
| **H-2** | No retry at any layer; recovery double-gated off | PO decision | PO / Mr. Kang | enabling any leg |
| **H-3** | SIT logs bodies + SQL bind params; no rotation anywhere | Configuration | Dev | SIT sign-off |
| **H-4** | Alerting transport not independent of the gateway it monitors | PO + code | Dev + PO | meaningful operability |
| **H-5** | SFTP unprovisioned + `StrictHostKeyChecking=no` | Infrastructure + PO | Ops + PO | bulk file channel |
| **H-6** | `.env.prod.example` still a UAT clone (verified today: only 3 values differ) | Configuration | Ops + PO | PROD deploy |
| ~~**M-1**~~ | ~~`partnerId` blank → silent substitution~~ **CLOSED 2026-07-29** — legacy has no partner-id; property removed, `VAN_ORG_CD` unconditional | Code defect | Dev | — |
| **M-2** | Duplicate `TB_FIRM_CONF` row on `(VN_VNM0004_000001, GV00000001)` — **NOT LIVE-VERIFIED** | DBA | DBA | non-deterministic single-row finders |
| **M-3** | `WECMS_CRYPTO_LEGACY_CERT_SEED` real value committed as a default in all templates | Security + PO | Security + PO | (accepted legacy constraint) |
| **T-9** | 30 modified + 1 deleted + new files **uncommitted**; `.gitlab-ci.yml` only forwards `project`/`file`; no Dockerfile, no manifest, no `setenv.sh` in-repo | Deployment-only | Dev + Ops | reproducible deploy |
| **M-4** | `.env.local` carries retired `SEED_CRON`/`REFRESH_CRON` keys | Configuration | Dev | (cosmetic) |

---

## 6. Documentation drift found while auditing

Recorded because each would mislead a deployer; none is a deployment blocker:

- `wecms-api/CLAUDE.md` — "Migrations | **Flyway** (`flyway-core` + `flyway-database-postgresql`)". Flyway is
  **not a dependency**; the schema is external and `ddl-auto: validate`.
- `wecms-api/CLAUDE.md` — lists `/actuator/health` as a live endpoint. It 404s (**D-3**).
- [../deployment/deployment-readiness.md](../historical/deployment-readiness-2026-07-28.md) (2026-07-28) — six findings
  now fixed (§2); its H-1 and lock-duration findings are stale. Superseded by this document.
- [../deployment/wecms-batch-infrastructure-readiness-audit.md](../../deployment/wecms-batch-infrastructure-readiness-audit.md)
  — H-1/H-5 closed; H-7 refined into **D-1**; the rest still stands.

---

## 7. What is genuinely ready

- **Dispatch implementation:** real production code across all six EFS legs + MSG, the byte-verified
  fixed-length codec, crypto, both callbacks, branch-split, 204 FX pre-step, and the DB write paths.
- **Safety architecture, now regression-guarded:** flags default off and a test asserts it; mocks throw;
  crons never-fire by default; `VanApiKeyService` fails closed; SFTP fails loud on missing host; 10/12 jobs
  ShedLock'd with `usingDbTime()`; no TLS bypass anywhere in the tree.
- **Scheduler hardening (this week):** central lock registry, derived lock durations, collision consolidation,
  legacy-rate cron corrections, and a read-only stuck-file monitor.
- **Audit trail:** `traceId` end-to-end plus three dispatch audit tables.
- **Test execution:** 2060 tests, 0 failures — see [test-coverage-audit.md](../legacy-parity/test-coverage-audit.md).

The gap to "deployable" is one correctness defect, one missing dependency, provisioning, and four
decisions — not the bulk of the implementation.
