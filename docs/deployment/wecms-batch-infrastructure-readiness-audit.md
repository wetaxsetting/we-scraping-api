# WeCMS Batch — Infrastructure & Configuration Readiness Audit

**Date:** 2026-07-28 · **System:** Refactor (`wecms-api`) firm-banking dispatch · **Scope:** DEV / SIT / UAT / PROD
**Method:** static repository audit + read-only query of the refactor DB (`postgres_dev_server_refactor`,
`172.168.5.172:5432/wecmsdevdb`). No code was modified; no flag was changed.

> **UPDATE 2026-07-28 (later same day) — blocker C-2 is CLOSED.** `TB_BATCH_SCHEDULE_INFO` and
> `TB_BATCH_SCHEDULE_DTL` have been seeded by the DBA (3 INFO + 12 DTL rows, `last_edtr='DBA_MANUAL'`,
> `reg_dttm` `20260728180209`–`20260728180237`) and re-verified row-by-row against the seed spec — an exact
> match. Two related code defects were also fixed in the same pass (timezone made an explicit property,
> `ANT_DIV_CD` canonicalised via the new `AntDivChannel`). See §4.5 for the verification and
> [wecms-batch-schedule-seed-review.md](wecms-batch-schedule-seed-review.md) §17 for the full record.
> **Every other blocker (C-1, C-3, C-4, C-5, H-2, H-7) is unchanged.** The overall verdict stays
> **NOT READY**.

**Companion documents (read together, do not duplicate):**
- [wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) — per-variable reference
- [wecms-batch-infra-team-checklist.md](wecms-batch-infra-team-checklist.md) — actionable checklist
- [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md) — activation + rollback procedure
- [wecms-batch-activation-and-external-confirmations.md](wecms-batch-activation-and-external-confirmations.md) — prior (2026-07-21) activation audit
- [sit-deployment-checklist-wecms-batch.md](sit-deployment-checklist-wecms-batch.md) — prior SIT deploy checklist

> **No secrets in this document.** Credential values are recorded as presence/absence only and masked `******`.

---

## 1. Executive Summary

**Verdict: NOT READY.**

The application code is substantively complete and the safety architecture is sound — every dispatch leg is
flag-gated, mock clients throw rather than silently no-op, both dispatch schedulers default to Spring's
never-fire cron `-`, and ShedLock guards multi-instance duplication. That part of the design holds up.

Three findings block activation, and the first one also invalidates the central assumption of the previous
(2026-07-21) audit:

1. **The deployed runtime does not match the repository's declared posture.** The refactor DB carries 17
   `TB_FIRM_TRAN` rows with `TRAN_TP='201'` dated **2026-07-22** and **2026-07-24**. Those rows can only be
   written when `wecms.dispatch.recipient-inquiry=true`, and their result code can only be produced by the
   **real** EFS client — so `wecms.dispatch.efs.enabled=true` was also live. Every committed `.env.*` file
   says `false` for both. The prior audit read `TB_FIRM_TRAN` as empty on 2026-07-21 and concluded "no
   dispatch leg has ever executed"; that conclusion is **now out of date**.
2. ~~**`TB_BATCH_SCHEDULE_INFO` and `TB_BATCH_SCHEDULE_DTL` are empty, and no seed exists anywhere in the
   repository.**~~ **CLOSED 2026-07-28** — seeded (3 INFO + 12 DTL) and verified (§4.5). At the time of the
   original audit, `OperatingHoursService` mapped a missing INFO row to `NO_CRITERIA` and both dispatch entry
   points treat `NO_CRITERIA` as **fail the row**, so enabling either leg would have failed 100% of transfers
   before any gateway call. That is no longer the case.
3. **Every one of the 17 real EFS 201 calls failed** with gateway `RS_CD='1000'`. The integration is
   reachable but rejecting — an external VAN Center item, not a code defect.

Two further gaps prevent the operational parts of the plan from being executed as written:
`spring-boot-boot-starter-actuator` is **not a dependency** (so the `/actuator/health` check that the SIT
checklist and every monitoring plan rely on does not exist), and there is **no `application-dev.yml`** even
though DEV is one of the four named target environments.

**What is genuinely ready:** the code, the flag architecture, the ShedLock wiring, the DB schema (161 DDL
scripts, `ddl-auto: validate` passing), the `shedlock` table, `TB_VAN_API_INFO` keys (2 rows, both
populated), and `TB_FIRM_CONF` (3 rows, all `ORG_STAT='1'`).

---

## 2. Architecture Summary

### 2.1 Components in scope

| Component | Entry point | Trigger | Gate |
|---|---|---|---|
| Recipient / depositor inquiry (EFS 201) | `DepositorInquiryService` ← `FirmBankingInquiryService` | Synchronous, on Excel upload / payment entry | `wecms.dispatch.recipient-inquiry` |
| NAPAS real-time transfer (EFS 202, +204 FX pre-step) | `RealtimeDispatchService` ← `RealtimeDispatchRegistrar` | Post-commit hook after VND payment dispatch | `wecms.dispatch.realtime-dispatch` |
| File deposit transfer / bulk reception (00301) | `BulkReceptionService` ← `DispatchAutoReceiveRegistrar` | Post-commit hook after non-VND payment dispatch | `wecms.dispatch.auto-receive` |
| Processing result check (EFS 203) | `ResultCheckService` ← `ResultCheckJob` | `@Scheduled` cron | `wecms.dispatch.result-check.cron` |
| Stranded transaction recovery | `StrandedRecoveryService` ← `StrandedRecoveryJob` | `@Scheduled` cron | `wecms.dispatch.recovery.enabled` **and** `.cron` (double-gated) |
| Bank callback — reception result (01301) | `BulkReceptionResultController` → `BulkReceptionResultService` | Inbound HTTP from VAN gateway | **none** — always mapped, unauthenticated |
| Bank callback — execution result (01302) | `BulkExecutionResultController` → `BulkExecutionResultService` | Inbound HTTP from VAN gateway | **none** — always mapped, unauthenticated |
| External file service (SFTP) | `JschSftpClient` / `MockSftpClient` | Called by bulk reception | `wecms.dispatch.sftp.enabled` |
| VAN Center / EFS transport | `RestClientEfsApiClient` / `MockEfsApiClient` | Called by all EFS legs | `wecms.dispatch.efs.enabled` |
| Ops alerting (EFS MSG) | `OpsAlertService` | Called on failure paths | `wecms.dispatch.alert.enabled` |

### 2.2 Safety architecture (verified, and it is good)

- **Mock clients throw, they do not no-op.** `MockEfsApiClient.send` throws
  `UnsupportedOperationException`; `MockSftpClient` likewise stands in for real I/O. A live request cannot
  leave by accident, and a misconfiguration surfaces loudly rather than silently succeeding.
- **Mutually exclusive bean wiring.** `@ConditionalOnProperty(..., havingValue="true")` on the real client
  vs `havingValue="false", matchIfMissing=true` on the mock — no duplicate-bean risk, and *missing*
  configuration selects the mock.
- **Never-fire cron default.** Both dispatch schedulers bind `${...cron:-}`; `-` is Spring's never-fire
  marker, so an unset variable cannot start a job.
- **Post-commit dispatch hooks.** Both registrars run inside `afterCommit`, so no network I/O happens
  inside a payment transaction and a dispatch failure cannot roll back a committed payment.
- **Failure isolation.** Both registrars catch and log per-item; a failure never rethrows into the payment
  path and never blocks sibling items.
- **Distributed locking.** `SchedulerLockConfig` wires ShedLock over the existing PostgreSQL
  (`JdbcTemplateLockProvider`, `usingDbTime()`, `defaultLockAtMostFor = PT10M`).

---

## 3. Configuration Inventory

### 3.1 Where configuration lives

| Layer | Location | Committed? |
|---|---|---|
| (a) Code default | `@Value(...:default)` / `*Properties` field initialisers | Yes — in Java |
| (b) Profile YAML | `src/main/resources/application-{sit,uat,prod}.yml` | Yes |
| (b′) Profile YAML — local | `src/main/resources/application-local.yml` | **No — gitignored** (`.gitignore:41`) |
| (c) Env file | `.env.local`, `.env.sit` (present on disk), `.env.{,local.,sit.,uat.,prod.}example` | Only `*.example` (verified via `git ls-files`) |
| (d) **Deployed runtime authority** | Host OS environment (SIT: `bin/setenv.sh`, per memory `reference_sit_server_config_setenv`) | **No — not in this repository** |

Layer (d) is authoritative and **is not auditable from the repository**. §4 shows that layer (d) currently
diverges from layer (c) — this is the audit's central finding.

### 3.2 Dispatch, EFS, SFTP, callback, alert properties

`enabled` / cron columns show the code default. "Env var" is the placeholder in the profile YAMLs. All rows
below appear in `application-{sit,uat,prod}.yml` and (uncommitted) `application-local.yml`; **none appear in
the base `application.yml`**.

| Property | Environment variable | Code default | Used by | Required in | Secret | Status |
|---|---|---|---|---|---|---|
| `wecms.dispatch.auto-receive` | `WECMS_DISPATCH_AUTO_RECEIVE` | `false` | `DispatchAutoReceiveRegistrar:45` | all | No | Must stay OFF (§5 C-2) |
| `wecms.dispatch.realtime-dispatch` | `WECMS_DISPATCH_REALTIME_DISPATCH` | `false` | `RealtimeDispatchRegistrar:40` | all | No | Must stay OFF (§5 C-2) |
| `wecms.dispatch.recipient-inquiry` | `WECMS_DISPATCH_RECIPIENT_INQUIRY` | `false` | `FirmBankingInquiryService:109` | all | No | **Live on a deployed node, failing** (§4) |
| `wecms.dispatch.result-check.cron` | `WECMS_DISPATCH_RESULT_CHECK_CRON` | `-` (never) | `ResultCheckJob:39` | all | No | OFF; UTC bug open (§5 H-7) |
| `wecms.dispatch.recovery.enabled` | `WECMS_DISPATCH_RECOVERY_ENABLED` | `false` (in-YAML `:false`) | `StrandedRecoveryService:66` | all | No | OFF, coexistence-gated |
| `wecms.dispatch.recovery.cron` | `WECMS_DISPATCH_RECOVERY_CRON` | `-` (in-YAML `:-`) | `StrandedRecoveryJob:32` | all | No | OFF |
| `wecms.dispatch.recovery.grace-minutes` | `WECMS_DISPATCH_RECOVERY_GRACE_MINUTES` | `30` (in-YAML `:30`) | `StrandedRecoveryService:70` | all | No | Default fine |
| `wecms.dispatch.alert.enabled` | `WECMS_DISPATCH_ALERT_ENABLED` | `false` | `OpsAlertService:89` | all | No | OFF |
| `wecms.dispatch.alert.env-mode` | *(none defined)* | `""` → profile fallback | `OpsAlertService:97` | optional | No | **Not in any YAML or `.env.*.example`** |
| `wecms.dispatch.alert.was-no` | *(none defined)* | `""` | `OpsAlertService:101` | optional | No | **Not in any YAML or `.env.*.example`** |
| `wecms.dispatch.callback.allowed-ips` | `WECMS_CALLBACK_ALLOWED_IPS` | empty → **fail-open** | `CallbackIpWhitelistFilter` | all | No | **Empty everywhere** (§5 H-2) |
| `wecms.dispatch.callback.trust-forwarded-for` | *(hardcoded `true`)* | `true` | `CallbackIpWhitelistFilter` | — | No | **Hardcoded in all 4 profile YAMLs** |
| `wecms.dispatch.callback.path-prefix` | *(none)* | `/api/v1/payment/dispatch/callbacks` | `CallbackProperties` | — | No | Code default only |
| `wecms.dispatch.efs.enabled` | `WECMS_EFS_ENABLED` | `false` → mock | `EfsClientConfig`, `MockEfsApiClient` | all | No | **Live on a deployed node** (§4) |
| `wecms.dispatch.efs.base-url` | `WECMS_EFS_BASE_URL` | `""` | `EfsClientConfig` | when EFS on | No | SIT template has a value; UAT/PROD empty |
| `wecms.dispatch.efs.partner-id` | `WECMS_EFS_PARTNER_ID` | `""` | `EfsProperties` | when EFS on | No | **Empty in every template** |
| `wecms.dispatch.efs.endpoints.*` | *(none)* | 7 vendor paths in code | `EfsProperties.defaultEndpoints()` | — | No | Vendor-fixed; fine |
| `wecms.dispatch.efs.connect-timeout-ms` | *(hardcoded `3000`)* | `3000` | `EfsClientConfig` | — | No | Hardcoded in all 4 YAMLs |
| `wecms.dispatch.efs.read-timeout-ms` | *(hardcoded `20000`)* | `20000` | `EfsClientConfig` | — | No | Hardcoded in all 4 YAMLs |
| `wecms.dispatch.sftp.enabled` | `WECMS_SFTP_ENABLED` | `false` → mock | `JschSftpClient`, `MockSftpClient` | all | No | OFF everywhere |
| `wecms.dispatch.sftp.host` / `.port` / `.username` | `WECMS_SFTP_HOST` / `_PORT` / `_USERNAME` | `""` / `22` / `""` | `JschSftpClient` | when SFTP on | No | Empty; port template `3300` |
| `wecms.dispatch.sftp.private-key-path` | `WECMS_SFTP_KEY_PATH` | `""` | `JschSftpClient` | when SFTP on | **path to secret** | Empty |
| `wecms.dispatch.sftp.password` | `WECMS_SFTP_PASSWORD` | `""` | `JschSftpClient` | alt. to key | **YES** `******` | Empty |
| `wecms.dispatch.sftp.connect-timeout-ms` | *(hardcoded `60000`)* | `60000` | `JschSftpClient` | — | No | Hardcoded |
| `wecms.dispatch.sftp.local-upload-path` | `WECMS_SFTP_LOCAL_UPLOAD` | `""` | `BulkReceptionService` | when SFTP on | No | **Empty in SIT template** → falls back to `java.io.tmpdir` |
| `wecms.dispatch.sftp.remote-upload-path` | `WECMS_SFTP_REMOTE_UPLOAD` | `""` | `JschSftpClient` | when SFTP on | No | `sdata` |
| `wecms.dispatch.sftp.local-download-path` | `WECMS_SFTP_LOCAL_DOWNLOAD` | `""` | `BulkExecutionResultService:342` | when SFTP on | No | **Empty in SIT template** → `java.io.tmpdir` |
| `wecms.dispatch.sftp.remote-download-path` | `WECMS_SFTP_REMOTE_DOWNLOAD` | `""` | `JschSftpClient` | when SFTP on | No | `rdata` |
| `wecms.dispatch.sftp.remote-move-path` | `WECMS_SFTP_REMOTE_MOVE` | `""` | `JschSftpClient` | when SFTP on | No | `rdata/success` |

### 3.3 Adjacent properties that affect batch behaviour

| Property | Environment variable | Code default | Used by | Secret | Status |
|---|---|---|---|---|---|
| `spring.datasource.url` / `.username` / `.password` | `WECMS_DB_URL` / `_USERNAME` / `_PASSWORD` | none | all | password **YES** `******` | UAT/PROD URL **not committed** |
| `wecms.jwt.secret` | `WECMS_JWT_SECRET` | none (fail-fast) | `JwtTokenProvider` | **YES** `******` | Blank in all templates — correct |
| `wecms.crypto.legacy-cert-seed` | `WECMS_CRYPTO_LEGACY_CERT_SEED` | `wabooksvn` (`BankCertCryptoService:69`) | cert cipher | **YES** `******` | **Real value committed** in every template (§5 H-6) |
| `wecms.internal-transfer.routing.shinhan-bank-cd` | *(none)* | `970424` | 5 dispatch executors | No | Code default only — no YAML |
| `wecms.internal-transfer.routing.woori-bank-cd` | *(none)* | `970457` | 5 dispatch executors | No | Code default only |
| `wecms.internal-transfer.routing.vcb-bank-cd` | *(none)* | `970436` | 4 dispatch executors | No | Code default only |
| `wecms.internal-transfer.cutoff.refresh-interval-ms` | *(none)* | `300000` | `CutoffConfigProvider:133` | No | Code default only |
| `wecms.coocon.*` (5 keys) | `WECMS_COOCON_*` | none | scraping + FX | 3 keys **YES** `******` | Historically-committed keys — rotation open |
| `wecms.osmc.*` (4 keys) | `WECMS_OSMC_*` | none | GDT lookup | 1 key **YES** `******` | — |
| `wecms.nxisas.timeout-seconds` / `.session-idle-seconds` | *(none)* | `60` / `300` | `NxisasSessionRegistry` | No | Code defaults; safe |
| `wecms.oat.seed-decrypt.enabled` | *(none)* | `false` | OAT decrypt endpoints | No | Code default only; safe |
| `haengsung.oracle.enabled` / `.url` / `.username` / `.password` | `HAENGSUNG_ORACLE_*` | `false` / — | Oracle DS + 8 gated beans | password **YES** `******` | **Live on the deployed node** (§4.3) |
| `haengsung.{scheduled-payment-import,exchange-rate-sync,card-account-sync}.{enabled,cron}` | `HAENGSUNG_*` | `false` / in-code cron | 3 scheduled jobs | No | **All three firing** (§4.3) |
| `wecms.exchange-rate.batch.enabled` | `WECMS_EXCHANGE_RATE_BATCH_ENABLED` | none | `ExchangeRateBatchScheduler` | No | **Firing** (§4.3); crons hardcoded |
| `wecms.operation-status.batch.enabled` / `.seed-cron` / `.refresh-cron` | `WECMS_OPERATION_STATUS_BATCH_*` | `0 0 0 * * ?` | `OperationStatusBatchScheduler` | No | **Firing** (§4.3) |
| 10 × `wecms.<module>.storage-root` | 10 × `WECMS_*_STORAGE_ROOT` | none | upload/download modules | No | PROD template = UAT clone (§5 H-4) |

### 3.4 Configuration-mechanism findings

- **The base `application.yml` contains no `wecms.dispatch.*` block at all.** Every dispatch property is
  defined only in the four profile YAMLs. A new profile that forgets the block inherits the *code* defaults
  — which are safe (all OFF / mock), so this fails safe, but it is silent rather than explicit.
- **Dispatch env vars have no in-YAML default** (the three `recovery.*` keys are the sole exception). The
  YAML says `${WECMS_EFS_ENABLED}`, not `${WECMS_EFS_ENABLED:false}`. An unset variable therefore raises
  `Could not resolve placeholder` and the **application fails to start**. The `@Value(...:false)` fallbacks
  in Java do **not** rescue this — the property *is* defined, it just resolves to an unresolvable nested
  placeholder. This is fail-safe (never accidentally enabled) but fail-loud (startup abort), and it means
  every variable in [wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) marked
  "required" must be present for the app to boot at all.
- **Flyway is not a dependency** (`build.gradle` contains no `flyway-core`; the only match is a comment) and
  there is **no `spring.flyway.*` key in any YAML**. Combined with `ddl-auto: validate`, the schema is fully
  external and pre-migrated. This matches `project_flyway_disabled_ddl_validate`, but the mechanism is
  *dependency removal*, not a configuration flag — worth knowing before anyone "re-enables" Flyway.
- **No Docker, no Kubernetes, no `setenv.sh` in the repository.** `.gitlab-ci.yml` only triggers an external
  pipeline (`project: '$URL'`, `file: '$FILE'`) for the `sit` and `uat` branches, so **the actual deployment
  mechanism is not auditable from this repository**.

---

## 4. Deployed-Runtime Evidence (read-only DB, 2026-07-28)

This section is the audit's most important content: it is the only available signal about layer (d).

### 4.1 EFS 201 recipient inquiry ran live and failed — 17/17

```
SELECT tran_dt, tran_tp, proc_stat, rs_cd, rs_msg, count(*) ... FROM tb_firm_tran
```

| `TRAN_DT` | `TRAN_TP` | Rows | `PROC_STAT` | `RS_CD` | `RS_MSG` | Window (`RQ_DTTM`) |
|---|---|---|---|---|---|---|
| 20260724 | 201 | 11 | `3` (FAIL) | `1000` | `WBE_EFS_GATEWAY_ERROR` | 10:52:34 → 14:09:22 |
| 20260722 | 201 | 6 | `3` (FAIL) | `1000` | `WBE_EFS_GATEWAY_ERROR` | 17:32:51 → 17:53:31 |

All 17 rows are tenant `VN_VNM0004_000001`. `TB_FIRM_BAT_API_TRAN`, `TB_FIRM_FILE_MST`,
`TB_FIRM_FILE_RSLT` and `MSG_SND_HIS` are all still **empty** — so only the 201 leg ran; 202/203/204/301/302
and ops alerting never executed.

**Why this proves both flags were ON — the reasoning chain:**

1. `FirmBankingInquiryService:109` gates the whole 201 path on `wecms.dispatch.recipient-inquiry`
   (default `false`); when disabled the method is a documented no-op that **writes nothing**. A
   `TB_FIRM_TRAN` 201 row existing at all therefore requires `recipient-inquiry=true`.
2. `DepositorInquiryService` writes the request row first (`recordRequest`), then sets the result via
   `recordResult` on one of three branches: success → `RS_CD='0000'`; `BusinessException` → `RS_CD` = the
   **gateway response RC**; other `RuntimeException` → `RS_CD='G999'`.
3. `MockEfsApiClient.send` **throws `UnsupportedOperationException`** — a `RuntimeException`. Under the mock
   the rows would read `RS_CD='G999'` / `MSG_ETC_ERR`.
4. The rows read `RS_CD='1000'` / `RS_MSG='WBE_EFS_GATEWAY_ERROR'` — branch 2, the gateway-RC branch.
   `1000` is **not** any `FirmBankingResultCd` constant (`SUCC='0000'`, `ETC_ERR='G999'`, …), so it was
   parsed from a real response envelope.
5. Therefore `RestClientEfsApiClient` was the active bean, which requires
   `wecms.dispatch.efs.enabled=true`, and a real EFS endpoint answered.

**Conclusion:** on a deployed node, between 2026-07-22 and 2026-07-24, `WECMS_DISPATCH_RECIPIENT_INQUIRY`
and `WECMS_EFS_ENABLED` were both `true` and pointed at a reachable EFS gateway that **rejected every
request with RC `1000`**. Every committed `.env.*` file says `false` for both.

### 4.2 Dispatch schedulers have never fired — confirmed

The `shedlock` table holds one row per job that has ever acquired the lock. It contains **8 rows, and
neither `ResultCheckJob.run` nor `StrandedRecoveryJob.run` is among them.** Result-check and stranded
recovery have never executed anywhere against this DB. This corroborates the `-` cron.

### 4.3 Other schedulers are live — and contradict the templates too

All 8 `shedlock` rows carry `locked_by = 'GroupwareDev'` (a single node — see §7.4), with the three
Haengsung locks refreshed at **2026-07-28 01:35 UTC**, i.e. currently running:

| ShedLock name | Last `locked_at` (UTC) | Implies |
|---|---|---|
| `HaengsungScheduledPaymentImportJob.run` | 2026-07-28 01:35:03 | `HAENGSUNG_ORACLE_ENABLED=true` + `HAENGSUNG_SCHEDULED_IMPORT_ENABLED=true` |
| `HaengsungExchangeRateSyncJob.run` | 2026-07-28 01:35:00 | `HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED=true` |
| `HaengsungCardAccountSyncJob.run` | 2026-07-28 01:35:00 | `HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED=true` |
| `exch-shinhan-last-time` | 2026-07-28 01:30:06 | `WECMS_EXCHANGE_RATE_BATCH_ENABLED=true` |
| `exch-woori` | 2026-07-28 01:30:03 | idem |
| `exch-shinhan-first-time` | 2026-07-27 18:30:08 | idem |
| `oprt-status-seed` | 2026-07-27 10:00:10 | `WECMS_OPERATION_STATUS_BATCH_ENABLED=true` |
| `oprt-status-refresh` | 2026-07-27 10:00:00 | idem |

Every one of these is `false` in `.env.uat.example` / `.env.prod.example` (and
`WECMS_EXCHANGE_RATE_BATCH_ENABLED=true` only in `.env.sit.example`). The committed templates are **not a
reliable description of any running environment.**

### 4.4 Provisioning state

| Object | State | Reading |
|---|---|---|
| `TB_VAN_API_INFO` | 2 rows, both `API_KEY` populated (values not read) | B2 provisioned on this DB |
| `TB_FIRM_CONF` | 3 rows, all `ORG_STAT='1'`; `VAN_ORG_CD='GV00000001'` on all | I5 provisioned — but **two rows share `(VN_VNM0004_000001, GV00000001)`** (§5 H-10) |
| `shedlock` | 8 rows, single holder `GroupwareDev` | Lock table healthy; single-instance today |
| `TB_BATCH_SCHEDULE_INFO` | ~~0 rows~~ → **3 rows** (re-verified later the same day) | **C-2 CLOSED** — §4.5 |
| `TB_BATCH_SCHEDULE_DTL` | ~~0 rows~~ → **12 rows** | **C-2 CLOSED** — §4.5 |
| `TB_PAY_TARGET` | 4 rows, all `PROC_STAT='9'` | Real-time dispatch never advanced them |
| `TB_PAY_FILE_MST` | 2 rows, all `PROC_STAT='9'` | Bulk reception never advanced them |
| `TB_FIRM_BAT_API_TRAN` / `TB_FIRM_FILE_MST` / `TB_FIRM_FILE_RSLT` / `MSG_SND_HIS` | empty | 202/203/301/302/alert never ran |

### 4.5 Re-verification of the batch schedule seed (C-2 closure, 2026-07-28 later same day)

The DBA executed the seed after the first pass. Re-queried read-only; **all 15 rows match the seed spec in
[wecms-batch-schedule-seed-review.md](wecms-batch-schedule-seed-review.md) §4–§5 exactly** — no drift, no
extra rows, no missing rows.

**`TB_BATCH_SCHEDULE_INFO` — 3 rows** (`reg_dttm 20260728180209`, `last_edtr='DBA_MANUAL'`, `mod_dttm` null):

| `batch_id` | `out_bank_cd` | `ant_where_use_yn` | window | vs spec |
|---|---|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | `970424` Shinhan | `Y` | `000000`–`235959` | ✅ |
| `NAPAS_REAL_TIME_BATCH` | `970436` VCB | `N` | `000000`–`235959` | ✅ (the refactor-required row; flag = OQ-2) |
| `NAPAS_REAL_TIME_BATCH` | `970457` Woori | `Y` | `000000`–`235959` | ✅ |

**`TB_BATCH_SCHEDULE_DTL` — 12 rows** (`reg_dttm 20260728180220` bulk / `…180237` realtime):

| `batch_id` | `out_bank_cd` | `ant_div_cd` | window | vs spec |
|---|---|---|---|---|
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `970424` | `NAPAS` | `000500`–`234500` | ✅ |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `970424` | `CITAD` | `090000`–`153000` | ✅ |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `970424` | `EBANK` | `090000`–`153000` | ✅ |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `970424` | `SWIFT` | `090000`–`234500` | ✅ (legacy value; OQ-3 closed) |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `970457` | `NAPAS` | `001500`–`235500` | ✅ |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `970457` | `CITAD` | `083000`–`174000` | ✅ |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `970457` | `VCB` | `083000`–`174000` | ✅ stored `'VCB  '`, `length()` = 3 — correct `bpchar(5)` padding, not an unpadded value |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `970457` | `SWIFT` | `083000`–`191000` | ✅ |
| `NAPAS_REAL_TIME_BATCH` | `970424` | `NAPAS` | `000500`–`234500` | ✅ |
| `NAPAS_REAL_TIME_BATCH` | `970424` | `CITAD` | `090000`–`153000` | ✅ (OQ-4 still open — see below) |
| `NAPAS_REAL_TIME_BATCH` | `970457` | `NAPAS` | `001500`–`235500` | ✅ |
| `NAPAS_REAL_TIME_BATCH` | `970457` | `CITAD` | `083000`–`174000` | ✅ |

**Functional check against the actual pending rows on this DB.** All 4 `TB_PAY_TARGET` rows are
`out_bank_cd='970424'`, `ANT_DIV_CD='INTER'`, `PROC_STAT='9'`. Tracing
`OperatingHoursService.evaluateRealtime`: the INFO lookup now finds a row (previously `null` →
`NO_CRITERIA`), the 24-hour window yields `EXE_DIV='Y'`, and `INTER` **short-circuits to `DISPATCH` before
the DTL lookup**. So these four rows would now be dispatched rather than failed — the seed genuinely
unblocks the exact data present.

**Two code fixes landed alongside the seed** (both verified present on disk):

| Fix | Change | Verified |
|---|---|---|
| Timezone made explicit | The hardcoded `ZoneId ICT` constant is replaced by `@Value("${wecms.dispatch.operating-hours.zone:Asia/Ho_Chi_Minh}")` (`OperatingHoursService:85`, used at `:236`). In-code default, **absent from every YAML**, so an unset property cannot abort startup — consistent with finding H-1. | ✅ |
| `ANT_DIV_CD` canonicalised | New `dispatch/constant/AntDivChannel.java` — `fromStorageCode` (strict, bulk path) and `canonical` (lenient, realtime path: strips `bpchar` padding, expands `E/V/C/N/S`, passes known names through, returns unknown codes **verbatim so they still fail closed**). `OperatingHoursService:132` now keys the DTL lookup on the canonical channel, not the raw column. | ✅ |

The timezone fix matters because the JEX frame DB runs `Asia/Seoul` (UTC+9) while these windows are
Vietnamese bank cut-offs — legacy therefore reads `090000`–`153000` as 07:00–13:30 ICT. ICT is the correct
interpretation (it matches `CutoffValidator.VIETNAM_ZONE`), and the property now allows `Asia/Seoul` for
bug-for-bug parity if a co-hosted legacy batch ever demands it.

**What the seed does NOT close:**

- **SWIFT / EBANK on the realtime rail have no DTL row** (deliberately). With canonicalisation, `'S'`→`SWIFT`
  and `'E'`→`EBANK` now resolve to a key that has no `NAPAS_REAL_TIME_BATCH` row, so those targets still fail
  with `NO_CRITERIA`. Whether they *should* be dispatchable on that rail is a **PO routing decision**, not a
  window decision. (`TransferLineInput`'s `@Pattern` already rejects `'E'`/`'V'` at the API boundary, so this
  reduces to SWIFT-on-realtime plus the HAENGSUNG raw-passthrough path.)
- **OQ-4 — Shinhan CITAD 16:30.** The realtime CITAD row is seeded `153000` (15:30), but memory
  `project_payment_cutoff_validation_16h30` records the CITAD cut-off moving to **16:30**, currently enforced
  at request time by `CutoffValidator` (`CITAD_API`). Which layer owns that rule is unresolved; if the answer
  is "the dispatch gate", this row needs updating to `163000`.
- **OQ-2 — VCB `ant_where_use_yn='N'`** is a refactor judgement call, not a legacy row (legacy JEX DEV has
  **no** `970436` INFO row at all). Needs PO confirmation.

---

## 5. Findings

Severity: **C** = blocks activation · **H** = must fix before the affected leg is enabled · **M** = should fix.
"Owner" is the accountable team.

### Critical

| # | Finding | Evidence | Owner |
|---|---|---|---|
| **C-1** | **Deployed config diverges from the repository.** `recipient-inquiry` + `efs.enabled` were live on a deployed node (2026-07-22/24) while every committed template says `false`. No one can state the current flag state of any environment from this repository. | §4.1 | Ops / Arthur |
| ~~**C-2**~~ **CLOSED 2026-07-28** | ~~`TB_BATCH_SCHEDULE_INFO` / `_DTL` empty, no seed exists.~~ Seeded by the DBA: 3 INFO + 12 DTL, verified row-by-row against the seed spec (exact match), plus two code fixes (explicit zone property, `AntDivChannel` canonicalisation). **Residual, not blocking the seed:** SWIFT/EBANK have no realtime DTL row (PO routing decision) and OQ-4 (CITAD 15:30 vs 16:30) is open. | §4.5 | ~~Dev + DBA + PO~~ → PO (OQ-2, OQ-4, SWIFT routing) |
| **C-3** | **EFS 201 rejects every request with RC `1000`.** 17/17 failures against a reachable gateway. Meaning of RC `1000` is undocumented in the repo. | §4.1 | VAN Center |
| **C-4** | **No `application-dev.yml`.** DEV is a named target environment with no Spring profile. `application.yml:11` defaults `spring.profiles.active: local`, and `application-local.yml` is gitignored — so a fresh clone or a DEV host has no bindable profile. | `ls src/main/resources/`, `.gitignore:41` | Dev |
| **C-5** | **Actuator is not a dependency.** `SecurityConfig:75` permits `/actuator/health` and `RequestResponseLoggingFilter:69` skips `/actuator`, but `spring-boot-starter-actuator` is absent from `build.gradle` → **no health endpoint and no metrics exist**. The SIT checklist step 8 ("`/actuator/health` UP") cannot pass, and §9 monitoring cannot be implemented. | `grep -n actuator build.gradle` → no dependency | Dev |

### High

| # | Finding | Evidence | Owner |
|---|---|---|---|
| **H-1** | Dispatch env vars carry **no in-YAML default** → a missing variable aborts startup. Fail-safe but fail-loud; every "required" variable must be set on every host. | §3.4 | Dev + Ops |
| **H-2** | **Callback endpoints are unauthenticated and the IP filter fails open.** `SecurityConfig:80` `permitAll`; `CallbackIpWhitelistFilter` passes everything through when `allowedIps` is empty (empty in all four templates); `trust-forwarded-for: true` is **hardcoded** in all four profile YAMLs, so `X-Forwarded-For` is spoofable unless a trusted proxy strips it. Payload `VV` HMAC is then the **only** gate. (Prior gate R-03.) | `CallbackIpWhitelistFilter`, 4 × YAML | Ops + Dev |
| **H-3** | **SIT logs full request and response bodies.** `application-sit.yml:158` sets `com.kosign.wecms: DEBUG`; `RequestResponseLoggingFilter` logs bodies at DEBUG (8 000 chars). Redaction (`SENSITIVE_FIELD`) matches only password/token/secret **field names** — it does **not** cover account numbers, `EV`/`VV` ciphertext, or EFS envelope fields. UAT/PROD are `INFO` and therefore safe. | filter + YAML | Dev |
| **H-4** | **`.env.prod.example` is a UAT clone.** Only DB host, CORS origin and Oracle host differ; PROD inherits UAT's `/DATA/src-wecms/wecms-backend/upload-files` paths, `sgw.webcashvietnam.com` URLs and `WECMS_SFTP_PORT=3300` unverified. | `diff .env.prod.example .env.uat.example` | Ops + PO |
| **H-5** | **Used-but-undocumented variables.** `WECMS_DISPATCH_RECOVERY_ENABLED` / `_CRON` / `_GRACE_MINUTES` and `wecms.dispatch.alert.env-mode` / `.was-no` are read by code but absent from every `.env.*.example`. | grep across templates | Dev |
| **H-6** | **Real legacy cert seed committed.** `WECMS_CRYPTO_LEGACY_CERT_SEED=wabooksvn` appears in all five `.env.*.example` files and as a code default (`BankCertCryptoService:69`). It is the legacy pgcrypto seed, so it cannot simply be rotated — but it should not be a committed default. | templates + Java | Security + PO |
| **H-7** | **Result-check day-boundary bug (prior finding K) is still open.** API-rail `TRAN_DT`/`REG_TM` are stamped UTC while `ResultCheckService` selects on ICT-today. Must be fixed **before** `result-check.cron` is ever set. | prior activation doc §2026-07-22 | Dev |
| **H-8** | **Two `@Scheduled` methods have no `@SchedulerLock`:** `NxisasSessionRegistry:68` (`fixedDelay = 60_000`) and `CutoffConfigProvider:133` (`fixedDelayString`). Both maintain node-local caches, so duplication is benign today — but this must be re-confirmed if the deployment scales out. | grep | Dev |
| **H-9** | **No truststore/keystore configuration.** `EfsClientConfig` uses `SimpleClientHttpRequestFactory` with default JVM trust. Good (no TLS bypass anywhere — verified) but if the VAN gateway presents a private-CA certificate, the CA must be imported into the JVM truststore; nothing in the repo does this. | `EfsClientConfig` | Ops |
| **H-10** | **Duplicate `TB_FIRM_CONF` row.** Two rows share `(VN_VNM0004_000001, GV00000001)` with `ORG_STAT='1'`. `hasActiveFirmConfig` uses `exists`, so it tolerates this, but any `findBy…` returning a single row could be non-deterministic. | §4.4 | DBA + Dev |

### Medium

| # | Finding | Evidence |
|---|---|---|
| M-1 | Deployment pipeline not auditable — `.gitlab-ci.yml` only triggers `project: '$URL'` / `file: '$FILE'`; no Dockerfile, no K8s manifest, no `setenv.sh` in the repository. |
| M-2 | `WECMS_SFTP_LOCAL_UPLOAD` / `_LOCAL_DOWNLOAD` are **empty** in `.env.sit.example`; code silently falls back to `System.getProperty("java.io.tmpdir")` (`BulkExecutionResultService:342`, `BulkReceptionService:630`) — files would land in the OS temp dir. |
| M-3 | `ExchangeRateBatchScheduler` crons are **hardcoded** (`0 30 8 * * ?`, `0 30 8-18 * * ?`, `0 0,30 9-20 * * ?`) — not tunable per environment, unlike every other job. |
| M-4 | EFS/SFTP timeouts and `trust-forwarded-for` are hardcoded literals in the four profile YAMLs rather than variables — they cannot be tuned per environment without a rebuild. |
| M-5 | No log-rotation, disk-monitoring, alert-hook or certificate-expiry configuration anywhere in the repository. |
| M-6 | `.env.sit` and `.env.local` exist on disk but are correctly **untracked** (`git ls-files` shows only `*.example`). Confirm they are excluded from build artifacts too. |

---

## 6. Infrastructure Dependencies

Status: **READY** = evidenced · **MISSING** = evidenced absent · **UNCLEAR** = `NEEDS CONFIRMATION`.
Hostnames/ports marked `NEEDS CONFIRMATION` are **not invented** — the repository does not contain them.

| # | Dependency | Why required | Component | Endpoint | Port / proto | Direction | Credential | Owner | Verify | DEV | SIT | UAT | PROD |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | PostgreSQL (refactor) | all state | every component | `172.168.5.172` (DEV/SIT); UAT/PROD `NEEDS CONFIRMATION` | 5432 / TCP | outbound | `WECMS_DB_PASSWORD` | Ops/DBA | `psql -c 'select 1'` | READY | READY | UNCLEAR | UNCLEAR |
| 2 | Schema present, `ddl-auto: validate` | startup | all | — | — | — | — | DBA | app boots without a validate error | READY | READY | UNCLEAR | UNCLEAR |
| 3 | `shedlock` table | scheduler locking | both dispatch jobs + 6 others | — | — | — | — | DBA | 8 rows present | READY | READY | UNCLEAR | UNCLEAR |
| 4 | `TB_VAN_API_INFO.API_KEY` | AES/HMAC `EV`/`VV` | all EFS legs | — | — | — | `******` per VAN | Ops (out-of-band) | 2 rows keyed | READY | READY | MISSING | MISSING |
| 5 | `TB_FIRM_CONF` `ORG_STAT='1'` | connecting-org gate | 201/202/203 + bulk | — | — | — | — | DBA | 3 rows | READY (dup, H-10) | READY | UNCLEAR | UNCLEAR |
| 6 | **`TB_BATCH_SCHEDULE_INFO` / `_DTL` rows** | operating-hours gate; empty ⇒ every dispatch FAILS | realtime + bulk | — | — | — | — | DBA + PO | `select count(*)` > 0 | **READY** (3+12, §4.5) | **READY** (same DB) | UNCLEAR — must be seeded on the UAT DB | UNCLEAR — must be seeded on the PROD DB |
| 7 | EFS / VAN Center gateway | all EFS legs | `EfsApiClient` | SIT template `dev-van-gw.webcashvietnam.com`; UAT/PROD `NEEDS CONFIRMATION` | 443 / HTTPS | outbound | partner `ID` + VAN key | Ops + VAN Center | `openssl s_client` + a 201 probe | reachable, **RC 1000** | UNCLEAR | MISSING | MISSING |
| 8 | EFS partner id | envelope `ID` | all EFS legs | — | — | — | not a secret | VAN Center | `WECMS_EFS_PARTNER_ID` non-empty | MISSING | MISSING | MISSING | MISSING |
| 9 | Outbound IP allow-listing at the gateway | gateway accepts our source IP | all EFS legs | — | 443 | outbound | — | Ops + VAN Center | gateway-side confirmation | UNCLEAR (RC 1000 candidate cause) | UNCLEAR | UNCLEAR | UNCLEAR |
| 10 | TLS trust for the gateway CA | HTTPS handshake | `EfsClientConfig` | — | 443 | outbound | CA cert | Ops | `openssl s_client -verify_return_error` | UNCLEAR | UNCLEAR | UNCLEAR | UNCLEAR |
| 11 | SFTP / external file service | bulk 00301 upload + 01302 download | `JschSftpClient` | `NEEDS CONFIRMATION` | template `3300` / SSH | outbound | key at `WECMS_SFTP_KEY_PATH` or `******` | Ops | `nc -vz` + `sftp -i` | MISSING | MISSING | MISSING | MISSING |
| 12 | SFTP private key file + `0600` perms | key auth | `JschSftpClient` | local FS | — | — | **`******`** | Ops | `ls -l` = `-rw-------` | MISSING | MISSING | MISSING | MISSING |
| 13 | Local staging dirs (send/receive) | file build + result parse | bulk reception / execution | `/DATA/src-wecms/wecms-backend/upload-files/{send,receive}` (UAT template) | — | — | — | Ops | `touch`/`ls` as the app user | MISSING (empty ⇒ tmpdir) | MISSING | UNCLEAR | UNCLEAR |
| 14 | 10 × module storage roots | uploads/downloads | payment + settings | UAT/PROD `/DATA/src-wecms/wecms-backend/upload-files` | — | — | — | Ops | `touch`/`ls` | READY (Windows paths) | READY | UNCLEAR | UNCLEAR (H-4) |
| 15 | Inbound callback reachability | 01301 / 01302 | callback controllers | `https://<api-host>/api/v1/payment/dispatch/callbacks/**` | 443 / HTTPS | **inbound** | none (unauthenticated) | Ops + VAN Center | `curl` from the gateway side | UNCLEAR | UNCLEAR | UNCLEAR | UNCLEAR |
| 16 | Reverse proxy sets/strips `X-Forwarded-For` | IP allow-list correctness | `CallbackIpWhitelistFilter` | — | — | inbound | — | Ops | header inspection behind the proxy | **UNCLEAR — H-2** | UNCLEAR | UNCLEAR | UNCLEAR |
| 17 | Gateway source IPs for the allow-list | fail-closed callbacks | `CallbackIpWhitelistFilter` | `NEEDS CONFIRMATION` | — | inbound | — | VAN Center | value supplied | MISSING | MISSING | MISSING | MISSING |
| 18 | Server TLS certificate for the API host | inbound HTTPS | callbacks | — | 443 | inbound | cert + key | Ops | `openssl s_client` expiry | UNCLEAR | UNCLEAR | UNCLEAR | UNCLEAR |
| 19 | Load balancer / multi-instance decision | ShedLock sufficiency | all schedulers | — | — | — | — | Ops | instance count | single (`GroupwareDev`) | UNCLEAR | UNCLEAR | UNCLEAR |
| 20 | Haengsung Oracle (secondary DS) | ERP import / FX / card sync | 8 gated beans | `NEEDS CONFIRMATION` | 1521 / TCP | outbound | `HAENGSUNG_ORACLE_PASSWORD` | Ops | `nc -vz` | **live (§4.3)** | UNCLEAR | UNCLEAR | UNCLEAR |
| 21 | Coocon / OSMC gateways | scraping + FX + GDT | banking / add-on | `sgw.webcashvietnam.com` (UAT/PROD), `dev-sgw…:8443` (DEV/SIT) | 443 / 8443 | outbound | `******` | Ops | HTTPS probe | READY | READY | UNCLEAR | UNCLEAR |
| 22 | **Actuator health / metrics** | monitoring, LB probe | ops | `/actuator/health` | 443 | inbound | — | Dev | `curl` → currently **404** | **MISSING (C-5)** | MISSING | MISSING | MISSING |
| 23 | Log rotation + disk headroom | file build, DEBUG bodies | all | — | — | — | — | Ops | `df -h`, logrotate config | UNCLEAR | UNCLEAR | UNCLEAR | UNCLEAR |
| 24 | Backup / PITR for the refactor DB | recovery from a bad sweep | all | — | — | — | — | DBA | restore drill | UNCLEAR | UNCLEAR | UNCLEAR | UNCLEAR |
| 25 | Monitoring + alerting hooks | on-call | ops | — | — | — | — | Ops | alert fires on a synthetic failure | MISSING | MISSING | MISSING | MISSING |
| 26 | Legacy-JEX coexistence decision (UAT/PROD DB target) | recovery cannot distinguish refactor rows | `StrandedRecoveryService` | — | — | — | — | PO + Ops | compare refactor vs JEX batch DB URL | safe (DB-separated) | safe (no JEX batch) | **UNCLEAR** | N/A |

---

## 7. Scheduler Safety Findings

### 7.1 Batch schedulers

| Field | `ResultCheckJob` | `StrandedRecoveryJob` |
|---|---|---|
| Class | `…dispatch/scheduler/ResultCheckJob.java` | `…dispatch/scheduler/StrandedRecoveryJob.java` |
| Method | `run()` | `run()` |
| Purpose | EFS 203 scan of aged 202 / `PROC_STAT='6'` rows | Recover stranded `'9'` / `'6'` rows |
| Cron property | `${wecms.dispatch.result-check.cron:-}` | `${wecms.dispatch.recovery.cron:-}` |
| Zone | `Asia/Ho_Chi_Minh` | `Asia/Ho_Chi_Minh` |
| Locking | `@SchedulerLock(lockAtMostFor=PT2M, lockAtLeastFor=PT5S)` | `@SchedulerLock(lockAtMostFor=PT10M, lockAtLeastFor=PT10S)` |
| Transaction | none on the job; `DispatchResultTxService` does the atomic finalize in `REQUIRES_NEW` | per-row claim `'9'→'6'` via `TbPayTargetRepository.claimForDispatch` |
| Failure handling | per-row isolation; `DispatchPacer` paces on skip/fail | per-row isolation |
| Retry | next cron tick | next cron tick |
| Multi-instance | ShedLock-serialised | ShedLock-serialised |
| Duplicate execution possible? | **No** (ShedLock + DB clock) | **No** (ShedLock + atomic row claim) |
| Disabled safely? | **Yes** — `-` never fires | **Yes** — double-gated (`enabled=false` **and** cron `-`) |
| Ready to enable? | **No** — C-2 (schedule rows) and H-7 (UTC boundary) | **No** — C-2 plus the UAT coexistence decision (dep. 26) |
| Has it ever run? | **No** — absent from `shedlock` (§4.2) | **No** — absent from `shedlock` |

### 7.2 Other schedulers sharing the same lock table

`ExchangeRateBatchScheduler` (3 crons, hardcoded), `OperationStatusBatchScheduler` (2 crons),
`HaengsungScheduledPaymentImportJob`, `HaengsungExchangeRateSyncJob`, `HaengsungCardAccountSyncJob` — all
`@SchedulerLock`-protected, all `@ConditionalOnProperty`-gated, **all currently firing** (§4.3).

### 7.3 Schedulers without ShedLock

`NxisasSessionRegistry:68` (`fixedDelay = 60_000`) and `CutoffConfigProvider:133` (`fixedDelayString`,
default 300 000 ms). Both refresh node-local caches, so concurrent execution on two nodes is benign — but
this is an assumption to re-validate before scaling out (H-8).

### 7.4 Multi-instance posture

`@EnableScheduling` lives on `NxisasConfig:27`; `SchedulerLockConfig` supplies the lock provider over
PostgreSQL using the **database clock** (`usingDbTime()`), which removes host-clock skew as a failure mode.
All 8 `shedlock` rows are held by a single `locked_by = 'GroupwareDev'`, so the observed deployment is
**single-instance today**. `spring.task.scheduling.pool.size: 1` also means all jobs share one thread — a
long job delays others.

**Duplicate-execution risk for the batch: LOW.** ShedLock covers both dispatch jobs, and the per-row atomic
`'9'→'6'` claim is a second line of defence *within* the refactor. The residual risk is **cross-system**:
on a shared UAT/PROD DB the refactor cannot distinguish its own rows from legacy-JEX rows (both draw the
same global `firm_seq_no` / `firm_file_seq_no`), so ShedLock does not help there — dependency 26.

---

## 8. Security Findings

| Check | Result |
|---|---|
| Secrets committed in YAML | **None.** All four profile YAMLs use `${VAR}` placeholders only. |
| Secrets committed in `.env` | **None tracked.** `git ls-files` returns only the five `*.example` files; `.gitignore:41-49` ignores `.env.*` and re-includes `!.env.*.example`. |
| Weak / real default secret | **H-6** — `WECMS_CRYPTO_LEGACY_CERT_SEED=wabooksvn` in all five templates + `BankCertCryptoService:69`. Masked here as `******`. |
| Historically-committed Coocon keys | Rotation remains an open Ops item (carried from the prior security review). |
| Sensitive values logged | **H-3** — SIT `DEBUG` + `RequestResponseLoggingFilter` logs full bodies; redaction is field-name-based only. |
| Full request/response payload logging | Yes at DEBUG — active in SIT (`application-sit.yml:158`), not in UAT/PROD (`INFO`). |
| Bank account information logged | Masked on the dispatch paths — `RecipientLookupService:112` uses `mask(...)`; `DepositorInquiryService` logs only `antDivCd`. **Not** masked by the generic body logger in SIT. |
| `EV` / `VV` / API keys logged | Not logged as values anywhere — verified by grep across the dispatch package. |
| Encryption keys stored insecurely | No. Per-VAN keys live in `TB_VAN_API_INFO` and are read via `VanApiKeyService`; never in YAML, never in the repo. |
| HMAC key handling | `VanApiKeyService.getApiKey` **throws** when no row exists — fails closed on a money-moving path. Good. |
| TLS verification disabled / trust-all | **None found.** No `X509TrustManager`, no hostname-verifier override, no `setSSLSocketFactory` anywhere in `src/main/java`. |
| Truststore / keystore config | **H-9** — none; default JVM trust only. |
| Unprotected callback endpoints | **H-2** — `permitAll`, IP filter fails open, `trust-forwarded-for` hardcoded `true`. |
| Missing callback authentication | Payload `VV` HMAC only (`EfsCryptoService.verifyVv`); no mTLS, no shared header secret. |
| Request-signature validation | Present — `VV` verified before any `EV` decrypt on 201/202/203/204 and both callbacks. |
| IP allow-listing | Implemented but **unconfigured** (empty in all templates ⇒ inactive). |
| Excessive file permissions | Not determinable from the repo — SFTP key must be `0600` (dependency 12). |

---

## 9. Monitoring Requirements

### 9.1 What exists today

| Capability | Status | Evidence |
|---|---|---|
| Correlation ids | **Implemented** — `TraceIdFilter` → MDC `traceId`, echoed as `X-Trace-Id`; log pattern `%5p [traceId=%X{traceId:--}]` (`application.yml:148`) | ready |
| Transaction ids | **Implemented** — `TRAN_SEQ` / `TNO` on every `TB_FIRM_TRAN` row | ready |
| Batch execution audit | **Implemented** — `TB_FIRM_TRAN`, `TB_FIRM_BAT_API_TRAN` (`DispatchApiTranAuditWriter`, `REQUIRES_NEW` so it survives a rollback), `TB_FIRM_FILE_RSLT` | ready |
| Alert hook | **Implemented but OFF** — `OpsAlertService` posts via EFS `WGVN_MSG_SEND_001`; requires `alert.enabled=true` **and** EFS enabled | code ready |
| Scheduler execution visibility | **Partial** — `shedlock.locked_at` is a usable heartbeat; no metric | usable |
| **Stuck pay-file (WAIT) detection** | **Implemented 2026-07-28, OFF by default** — `StuckPayFileMonitorService` + `StuckPayFileMonitorJob` sweep `TB_PAY_FILE_MST` for `PROC_STAT='9'` rows older than a threshold (default 15 min), classify the likely cause, emit a structured `WARN` summary and one de-duplicated aggregated ops alert. **Read-only — never changes `PROC_STAT`.** Closes the observability half of the silent-`SKIP` gap (`OperatingHoursService` returns `SKIP` with no FAIL and no alert). Config: [wecms-batch-environment-variables.md §2a](wecms-batch-environment-variables.md) · design: [../audits/payment-dispatch-operating-hours-flow.md §10](../audits/payment-dispatch-operating-hours-flow.md) | code ready, **enable in SIT first** |
| **Health endpoint** | **MISSING** — C-5, actuator not a dependency | blocker |
| **Metrics** | **MISSING** — no Micrometer, no actuator | blocker |
| **Structured (JSON) logs** | **MISSING** — plain pattern layout only | gap |
| Log rotation | **MISSING** — no configuration in the repo | gap |
| Certificate-expiry monitoring | **MISSING** | gap |
| Disk-usage monitoring | **MISSING** | gap |

### 9.2 What operations must monitor once anything is enabled

| Signal | Source available today | Suggested threshold |
|---|---|---|
| Dispatch success / failure counts | `TB_FIRM_TRAN` group by `PROC_STAT` (`0`=succ, `3`=fail, `6`=in-flight) | any failure |
| Pending transaction count | `TB_PAY_TARGET` / `TB_PAY_FILE_MST` where `PROC_STAT='9'` | > 0 for > 15 min |
| **Stuck bulk pay-file (silent WAIT)** | **Now automated** for `TB_PAY_FILE_MST` — grep `feature=stuck-pay-file-monitor SUMMARY` for `stuck_total` + `by_cause`, or alert on the aggregated ops message. Manual equivalent: `SELECT * FROM tb_pay_file_mst WHERE proc_stat='9' AND proc_dttm < to_char(now() AT TIME ZONE 'Asia/Ho_Chi_Minh' - interval '15 min','YYYYMMDDHH24MISS')`. **`TB_PAY_TARGET` (realtime) is NOT covered** — the realtime gate fails loudly (`NO_CRITERIA`/`CUTOFF` → FAIL + alert), so a silent WAIT there is only reachable via the pre-start `SKIP`; still a manual check for now. | any row `stuck_total > 0` |
| Stranded transaction count | `PROC_STAT='6'` and `PROC_DTTM` older than the grace window | > 0 |
| Result-check failures | `TB_FIRM_TRAN` `TRAN_TP='203'` non-`0000` | any |
| Callback failures | log `01301/01302 callback VV verification failed` | any |
| VAN connectivity failures | `RS_CD='G999'` / gateway RC ≠ `0000` — **RC `1000` is the current live signal** | any |
| Bank API failures | `TB_FIRM_TRAN.RS_CD` distribution | any non-`0000` |
| File creation failures | log `auto-receive (00301) failed` | any |
| Retry exhaustion | absence of a `'6'`→terminal transition within `lockAtMostFor` | any |
| Scheduler execution | `shedlock.locked_at` freshness per job name | stale > 2 × cron interval |
| DB connection failures | `WBE_DB` / `DataAccessException` in logs | any |
| Disk usage on the staging dirs | OS | > 80 % |
| TLS certificate expiry | OS / external | < 30 days |

---

## 10. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Someone activates a leg believing flags are OFF, when a deployed host already has them ON | **Realised** (§4.1) | High | Mandatory `env \| grep WECMS_` readout on every host before any change (runbook step 0) |
| R2 | Dispatch enabled with `TB_BATCH_SCHEDULE_INFO` empty → 100 % of transfers fail + an alert storm | ~~High~~ → **Low on DEV/SIT** (seeded, §4.5); **High on UAT/PROD** (not yet seeded) | High | Seed and verify per environment before enabling. Residual: SWIFT/EBANK targets still fail on the realtime rail (no DTL row — PO routing decision) |
| R3 | Callback endpoint abused — unauthenticated, IP filter fails open, XFF spoofable | Medium | **Critical** (money movement) | Populate `WECMS_CALLBACK_ALLOWED_IPS`, confirm the proxy strips inbound XFF, or add mTLS |
| R4 | Recovery sweep double-processes legacy-JEX rows on a shared UAT/PROD DB | Medium | **Critical** | Keep double-gated OFF until Ops confirms distinct DB targets (dep. 26) |
| R5 | Result-check misses transactions across the UTC/ICT day boundary | High once enabled | Medium | Fix H-7 before setting the cron |
| R6 | No health endpoint or metrics → failures discovered only by user complaint | High | Medium | Add actuator (C-5) |
| R7 | SFTP paths unset → files silently written to `java.io.tmpdir` and lost on restart | Medium | Medium | Set all four path variables before enabling SFTP (M-2) |
| R8 | PROD runs on unverified UAT-cloned paths/URLs | Medium | High | Author a real PROD template (H-4) |
| R9 | Account numbers leak into SIT DEBUG logs | Medium | Medium | Set SIT `com.kosign.wecms: INFO`, or extend redaction (H-3) |
| R10 | Scale-out breaks the two unlocked schedulers or the single-thread pool | Low today | Medium | Decide instance count; revisit H-8 and `pool.size: 1` |
| R11 | Private-CA gateway certificate fails the handshake at cutover | Medium | Medium | Verify with `openssl s_client`; import the CA (H-9) |

---

## 11. Blockers

**Must be closed before any dispatch leg is enabled in any environment:**

1. **C-1** — capture `env | grep WECMS_` (secrets redacted) on every DEV/SIT/UAT host and reconcile against
   this document. Decide explicitly whether `recipient-inquiry` + `efs.enabled` stay on.
2. ~~**C-2** — seed `TB_BATCH_SCHEDULE_INFO` / `_DTL`.~~ **CLOSED on the DEV/SIT DB** (§4.5). Still to do:
   run the same seed on the **UAT and PROD** DBs once those targets are named, and close OQ-2 (VCB flag),
   OQ-4 (CITAD 15:30 vs 16:30) and the SWIFT/EBANK realtime routing decision with the PO.
3. **C-3** — obtain from VAN Center the meaning of EFS 201 `RS_CD='1000'` and what must change.
4. **C-4** — create `application-dev.yml` (or formally state that DEV uses the `local` profile).
5. **C-5** — add `spring-boot-starter-actuator` so health and metrics exist.
6. **H-2** — restrict the callback path by IP / mTLS / network before any non-local enablement.
7. **H-7** — fix the result-check UTC/ICT day-boundary before setting `result-check.cron`.
8. **Dependency 26** — Ops confirmation that the refactor UAT DB is not the JEX UAT batch DB.

---

## 12. Recommendations

**Immediate (this week, no activation involved)**
1. Run the `env | grep WECMS_` readout on every host; paste the redacted result into §4 of the prior
   activation document. Until then no environment's flag state is known.
2. If the 17 failing 201 calls are unintended, set `WECMS_DISPATCH_RECIPIENT_INQUIRY=false` on that host —
   it is currently generating failed audit rows against a shared DB.
3. Add `spring-boot-starter-actuator`, expose `health` (+ `info`, `metrics`) and keep the rest closed.
4. Add `application-dev.yml`.
5. Add the five undocumented variables to every `.env.*.example` (H-5) — done in
   [`batch-dispatch-variable-reference.md`](batch-dispatch-variable-reference.md).

**Before enabling any leg**
6. Author and run the `TB_BATCH_SCHEDULE_INFO` / `_DTL` seed (C-2), PO-approved windows.
7. Populate `WECMS_CALLBACK_ALLOWED_IPS` and confirm the reverse proxy strips client-supplied
   `X-Forwarded-For`; consider making `trust-forwarded-for` a variable rather than a hardcoded `true`.
8. Fix H-7 (result-check UTC).
9. Give every dispatch variable an explicit in-YAML default (`${VAR:false}`, `${VAR:-}`) so a missing
   variable degrades to OFF instead of aborting startup — safer *and* quieter than today's behaviour.
10. Set SIT `com.kosign.wecms: INFO`, or extend `SENSITIVE_FIELD` to cover account numbers and EFS envelope
    fields.

**Before UAT / PROD**
11. Author a genuine `.env.prod.example` with PROD-confirmed paths, URLs and ports (H-4).
12. Resolve the coexistence question (dep. 26) with a documented PO decision.
13. Decide the instance count; if > 1, revisit H-8 and `spring.task.scheduling.pool.size`.
14. Move `WECMS_CRYPTO_LEGACY_CERT_SEED` out of the committed templates (H-6).
15. Stand up log rotation, disk monitoring, certificate-expiry monitoring and alert hooks (M-5).
