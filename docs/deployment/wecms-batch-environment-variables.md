# WeCMS Batch — Environment Variable Reference

**Date:** 2026-07-28 · **System:** Refactor (`wecms-api`) firm-banking dispatch
**Companion:** [wecms-batch-infrastructure-readiness-audit.md](wecms-batch-infrastructure-readiness-audit.md) ·
Template: [`batch-dispatch-variable-reference.md`](batch-dispatch-variable-reference.md)

> **No secret values appear in this document.** Secrets are shown as `******`.

---

## How to read this document

**Secret classification**
- **SECRET** — never commit, never log, never paste into a ticket. Provision out-of-band.
- **SENSITIVE** — not a credential but reveals infrastructure (hostnames, internal paths, IPs).
- **PUBLIC** — safe to commit in a template.

**Required**
- **REQUIRED** — **the application will not start without it.** The profile YAML binds `${VAR}` with no
  in-YAML default, so an unset variable raises `Could not resolve placeholder` at startup. The
  `@Value(...:false)` fallbacks in Java do **not** rescue this (see audit §3.4).
- **OPTIONAL (in-YAML default)** — YAML supplies a default, so an unset variable is fine.
- **OPTIONAL (code default only)** — no YAML entry at all; the Java default applies.

**Default behaviour** describes what happens when the variable is unset *or* set to its safe value.

**Environments:** DEV · SIT · UAT · PROD. **DEV currently has no Spring profile** — see audit blocker C-4.

---

## 1. Activation flags — the four the task asked about

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `WECMS_EFS_ENABLED` | Master switch for the EFS/VAN Center HTTP transport. `true` wires `RestClientEfsApiClient`; `false`/absent wires `MockEfsApiClient`, whose `send()` **throws** — no live request can leave. | `false` | SIT, UAT, PROD (**REQUIRED**) | PUBLIC | `false` → mock client; every EFS call fails loudly | `EfsClientConfig`, `MockEfsApiClient`, `RestClientEfsApiClient` |
| `WECMS_DISPATCH_REALTIME_DISPATCH` | Enables the post-commit NAPAS real-time (EFS 202, +204 FX pre-step) hand-off after a committed VND payment. | `false` | SIT, UAT, PROD (**REQUIRED**) | PUBLIC | `false` → `registerAfterCommit` returns immediately; pay-target stays `PROC_STAT='9'` | `RealtimeDispatchRegistrar:40` → `RealtimeDispatchService` |
| `WECMS_DISPATCH_RESULT_CHECK_CRON` | Spring cron for the EFS 203 processing-result scan, zone `Asia/Ho_Chi_Minh`. **`-` is Spring's never-fire marker.** | `-` | SIT, UAT, PROD (**REQUIRED**) | PUBLIC | `-` → job bean registered but never fires (confirmed: absent from `shedlock`) | `ResultCheckJob:39` → `ResultCheckService` |
| `WECMS_DISPATCH_RECOVERY_CRON` | Spring cron for the stranded-transaction recovery sweep. **Second gate** — `WECMS_DISPATCH_RECOVERY_ENABLED` must *also* be `true`. | `-` | none — **OPTIONAL (in-YAML default `-`)** | PUBLIC | `-` → never fires; and the service no-ops while `enabled=false` | `StrandedRecoveryJob:32` → `StrandedRecoveryService` |

**Validation of these four (per the task's checklist):**

| Requirement | Result |
|---|---|
| Disabled by default | **Yes.** Code defaults are `false` / `false` / `-` / `-`; all committed templates agree. |
| Missing variable cannot accidentally enable processing | **Yes, but harshly.** The first three abort startup if unset (no in-YAML default); they can never resolve to a truthy value. `WECMS_DISPATCH_RECOVERY_CRON` degrades to `-`. |
| Cron cannot start with an unsafe fallback | **Yes.** Both cron fallbacks are `-`. Note the YAML syntax `${WECMS_DISPATCH_RECOVERY_CRON:-}` means "default to the literal `-`", not "default to empty" — an empty string would be an invalid cron and would fail startup. |
| DEV/SIT/UAT/PROD configurable independently | **Partly.** SIT/UAT/PROD each have a profile YAML reading the same variable names, so per-host OS env gives independence. **DEV has no profile YAML** (blocker C-4). |
| Booleans parsed correctly | **Yes.** Spring relaxed binding on `boolean` / `Boolean`; accepts `true`/`false` case-insensitively. Any other token (e.g. `yes`, `1`, `TRUE `) — `true`/`false` only is the safe contract. |
| Disabled cron expressions supported safely | **Yes.** Spring treats `-` as `CRON_DISABLED`; the bean registers, the trigger never schedules. |
| No duplicate or conflicting property names | **Yes** for these four. Each maps to exactly one property, read in exactly one place. |

**Additional activation flags not in the task's list** — all of these also gate batch behaviour:

| Variable | Gates | Default |
|---|---|---|
| `WECMS_DISPATCH_AUTO_RECEIVE` | Bulk file-deposit 00301 reception hand-off (non-VND) | `false` |
| `WECMS_DISPATCH_RECIPIENT_INQUIRY` | EFS 201 recipient/depositor inquiry | `false` — **was live and failing on a deployed node, audit §4.1** |
| `WECMS_DISPATCH_RECOVERY_ENABLED` | First gate of the recovery sweep | `false` (in-YAML) |
| `WECMS_DISPATCH_ALERT_ENABLED` | Ops alerting via EFS `WGVN_MSG_SEND_001` | `false` |
| `WECMS_SFTP_ENABLED` | Real SFTP client vs `MockSftpClient` | `false` |
| `WECMS_EXCHANGE_RATE_BATCH_ENABLED` | 3 FX collection crons (crons hardcoded) | — **currently firing** |
| `WECMS_OPERATION_STATUS_BATCH_ENABLED` | 2 operation-status crons | — **currently firing** |
| `HAENGSUNG_ORACLE_ENABLED` | Secondary Oracle DataSource + 8 gated beans | `false` — **currently on** |
| `HAENGSUNG_SCHEDULED_IMPORT_ENABLED` | ERP scheduled-payment import job | `false` — **currently firing** |
| `HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED` | ERP FX sync job | `false` — **currently firing** |
| `HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED` | ERP card/account sync job | `false` — **currently firing** |

There is **no on/off flag for the two bank callback endpoints** — `/api/v1/payment/dispatch/callbacks/**` is
`permitAll` and always mapped (`SecurityConfig:80`). The only control is `WECMS_CALLBACK_ALLOWED_IPS` plus
network/firewall rules.

---

## 2. Dispatch — core flags

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `WECMS_DISPATCH_AUTO_RECEIVE` | Post-commit 00301 bulk-reception hand-off for non-VND (file-path) payments. | `false` | SIT/UAT/PROD (**REQUIRED**) | PUBLIC | `false` → no-op; `TB_PAY_FILE_MST` stays `PROC_STAT='9'` | `DispatchAutoReceiveRegistrar:45` |
| `WECMS_DISPATCH_RECIPIENT_INQUIRY` | EFS 201 recipient-name verification during Excel upload / payment entry. No legacy counterpart (legacy always inquires). | `false` | SIT/UAT/PROD (**REQUIRED**) | PUBLIC | `false` → `inquiryAccount` is a no-op that writes nothing | `FirmBankingInquiryService:109` |
| `WECMS_DISPATCH_RECOVERY_ENABLED` | First of two gates on the stranded-record recovery sweep. **Coexistence-gated** — keep `false` until the UAT DB target is confirmed. | `false` | OPTIONAL (in-YAML `:false`) | PUBLIC | `false` → `recoverOnce()` returns immediately | `StrandedRecoveryService:66` |
| `WECMS_DISPATCH_RECOVERY_GRACE_MINUTES` | Age in minutes before a `'9'`/`'6'` row is considered stranded. | `30` | OPTIONAL (in-YAML `:30`) | PUBLIC | `30` | `StrandedRecoveryService:70` |
| `WECMS_DISPATCH_ALERT_ENABLED` | Ops alerts on dispatch failures, posted through the EFS `WGVN_MSG_SEND_001` endpoint. Requires `WECMS_EFS_ENABLED=true` to reach anything. | `false` | SIT/UAT/PROD (**REQUIRED**) | PUBLIC | `false` → `notify()` is a no-op; never throws | `OpsAlertService:89` |
| `WECMS_DISPATCH_ALERT_ENV_MODE` | Legacy `ENV_MODE` marker — `T`=DEV, `U`=UAT, `R`=PROD. Drives the alert `mode` field and the `ServerGB` label. **Not present in any YAML or template today** (audit H-5). | `T` | OPTIONAL (code default only) | PUBLIC | empty → falls back to the active Spring profile | `OpsAlertService:97` |
| `WECMS_DISPATCH_ALERT_WAS_NO` | Legacy `WAS_NO` — server/instance number in the `ServerGB` label. **Not present in any YAML or template today.** | `01` | OPTIONAL (code default only) | PUBLIC | empty | `OpsAlertService:101` |

> The two alert variables are read via `@Value("${wecms.dispatch.alert.env-mode:}")` / `.was-no`. Because no
> YAML entry exists, they must be supplied as **JVM/OS properties in the `wecms.dispatch.alert.*` form** or a
> YAML entry must be added first. The `WECMS_DISPATCH_ALERT_ENV_MODE` names above are the *proposed*
> relaxed-binding names — they only work once the YAML lines exist. Marked `NEEDS CONFIRMATION` pending that
> one-line YAML addition.

---

## 2a. Stuck pay-file monitor (read-only observability)

Added 2026-07-28. Detects `TB_PAY_FILE_MST` rows left at `PROC_STAT='9'` (WAIT) past a threshold — the
condition an `OperatingHoursService` `SKIP` produces silently (no FAIL, no alert). **Read-only: it never
changes a payment status, never re-drives dispatch, and never enables anything.**

Rationale + design: [../audits/payment-dispatch-operating-hours-flow.md §10](../audits/deployment/payment-dispatch-operating-hours-flow.md).

| Property | Description | Default | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `wecms.dispatch.stuck-monitor.enabled` | Master gate. Second of two gates (the cron is the other). | `false` | OPTIONAL (code default only) | PUBLIC | `false` → `monitorOnce()` returns immediately, no query issued | `StuckPayFileMonitorService` |
| `wecms.dispatch.stuck-monitor.cron` | Spring cron for the sweep. `-` is the never-fire marker. | `-` | OPTIONAL (code default only) | PUBLIC | `-` → the job never fires in any environment | `StuckPayFileMonitorJob` |
| `wecms.dispatch.stuck-monitor.threshold-minutes` | A WAIT row older than this is reported. Compared against `PROC_DTTM` (= `REG_DTTM` on insert). | `15` | OPTIONAL | PUBLIC | `15` | `StuckPayFileMonitorService` |
| `wecms.dispatch.stuck-monitor.re-alert-minutes` | Heartbeat re-alert interval for an **unchanged** stuck set. `0` alerts every sweep. | `60` | OPTIONAL | PUBLIC | `60` | `StuckPayFileMonitorService` |
| `wecms.dispatch.stuck-monitor.max-alert-keys` | Cap on files itemised in the alert body and in per-row `WARN` logs. The remainder is reported as a count. | `20` | OPTIONAL | PUBLIC | `20` | `StuckPayFileMonitorService` |

**Suggested enablement (SIT first):**

```
wecms.dispatch.stuck-monitor.enabled=true
wecms.dispatch.stuck-monitor.cron=0 */5 * * * ?     # every 5 minutes, ICT
wecms.dispatch.stuck-monitor.threshold-minutes=15
```

> **Binding form.** Like the two alert variables above, these are read via `@Value` and have **no YAML
> entry** — deliberately, so an unset variable can never abort startup (audit finding H-1). Supply them as
> **JVM `-D` / OS properties in the dotted `wecms.dispatch.stuck-monitor.*` form**, which is the form
> verified to work. An `UPPER_SNAKE` environment-variable equivalent is *not* asserted here: relaxed binding
> of hyphenated names from env vars is version-dependent, and no YAML line exists to anchor it. Marked
> `NEEDS CONFIRMATION` on the same footing as `WECMS_DISPATCH_ALERT_ENV_MODE`.

> **Two behavioural notes for Ops.**
> 1. While `wecms.dispatch.auto-receive=false` (today's default everywhere), **every** aged WAIT row
>    classifies as `DISPATCH_FLAG_DISABLED` — that is correct, but it means enabling the monitor before
>    enabling dispatch will report the whole existing backlog. Expect one aggregated alert, then silence
>    until the set changes.
> 2. Alerts route through `OpsAlertService`, which is itself gated by `wecms.dispatch.alert.enabled`
>    (default `false`) **and** needs `wecms.dispatch.efs.enabled=true` to reach the gateway. With alerts off
>    the monitor still produces the full structured `WARN` log — that alone is usable via log search.

**Added 2026-07-29 — second sweep (`TB_FIRM_FILE_MST`).** The monitor now also detects files stranded at
`PROC_STEP='1' / PROC_STAT='6'` — accepted by the bank via `00301`, but with no confirmed `00302`. New
causes `BULK_EXECUTION_NOT_ISSUED` (no `00302` audit row — the inline issue never ran; safe to re-drive)
and `BULK_EXECUTION_UNCONFIRMED` (a `00302` row exists — **reconcile with the VAN Center first**, the bank
may already have executed). Those two need opposite manual actions. Still read-only, still
the same `threshold-minutes` / `re-alert-minutes` / `max-alert-keys`.

| Property | Description | Default | Required in | Secret | Component |
|---|---|---|---|---|---|
| `wecms.dispatch.stuck-monitor.firm-file-batch-size` | Cap on the second sweep's selection, so monitoring cannot become an unbounded scan. | `200` | OPTIONAL | PUBLIC | `StuckPayFileMonitorService` |

> **Binding form — this one IS anchored in YAML.** Unlike its siblings above, `firm-file-batch-size` was
> added to all four profile YAMLs as `${WECMS_DISPATCH_STUCK_MONITOR_FIRM_FILE_BATCH_SIZE:200}`. It
> therefore binds from the `UPPER_SNAKE` environment variable **and** cannot abort startup when unset
> (the inline default still applies). The H-1 concern that motivated omitting the others does not apply
> when the YAML placeholder carries a default — future additions should follow this form.

---

## 2b. Bulk-execution (`00302`) retry sweep — REMOVED 2026-07-29

A scheduled `00302` retry sweep was added and **removed the same day**. It was K9 **Option A**
("Recovery Scheduler (reconciliation)"), which was formally offered to Mr. Kang and **not selected** —
he directed inline `00302` execution from the `01301` callback. Automatic retry was therefore never a
parity requirement.

**All five `WECMS_DISPATCH_BULK_EXECUTION_RETRY_*` variables are gone and bind to nothing.** If you find
them set on a host, remove them.

The approved residual-recovery model is **monitoring + manual recovery** (§2a) — see
[../audits/final-four-to-one-batch-architecture-correction.md](../audits/batch/final-four-to-one-batch-architecture-correction.md).

---

## 2c. Result-check batch size (H-2, added 2026-07-29)

| Property | Env var | Description | Default | Required in | Secret |
|---|---|---|---|---|---|
| `wecms.dispatch.result-check.batch-size` | `WECMS_DISPATCH_RESULT_CHECK_BATCH_SIZE` | Candidate rows fetched per scheduled pass. Bounds memory **and** the number of sequential EFS 203 round-trips one pass makes (and so how long it holds its ShedLock). Non-positive falls back to 100 with a WARN rather than breaking the scheduler. | `100` | OPTIONAL (inline default in all four profiles) | PUBLIC |

Previously the sweep loaded **every** `TRAN_TP='202' / PROC_STAT='6'` row — all dates, all tenants —
into memory each pass. It is now an ordered (`tranDt, tranTm, tranSeq` ASC), DB-limited query.
The scan remains **GLOBAL across tenants**, which matches legacy `IDO.TB_FIRM_TRAN_R001` by design.
Changing this does **not** enable anything: `WECMS_DISPATCH_RESULT_CHECK_CRON` is still `-`.

Watch for `result-check: candidate batch cap reached (N)` — that WARN means more eligible rows remain
and the next pass will continue from the oldest of them.

---

## 3. Callback (inbound from the bank / VAN gateway)

> **⚑ CHANGED 2026-07-29 (H-1) — read before deploying.** The warning block below described the
> *old* fail-open behaviour. Callback IP filtering is now **fail-closed** and needs no flag:
>
> | Property | Env var | Description | Default | Required in |
> |---|---|---|---|---|
> | `wecms.dispatch.callback.protection` | `WECMS_CALLBACK_PROTECTION` | `ENFORCE` (default) rejects with 403 when the allow-list is empty or non-matching. `DISABLED` opts out explicitly and logs a WARN every startup. `local` sets `DISABLED`; `sit`/`uat`/`prod` set `ENFORCE`. | `ENFORCE` | OPTIONAL |
> | `wecms.dispatch.callback.trusted-proxies` | `WECMS_CALLBACK_TRUSTED_PROXIES` | Reverse proxies whose `X-Forwarded-For` may be believed. Literals and/or CIDR. **Empty = no proxy trusted = the header is always ignored** (the safe default). | *(empty)* | OPTIONAL |
> | `wecms.dispatch.callback.trust-forwarded-for` | `WECMS_CALLBACK_TRUST_FORWARDED_FOR` | Whether the header may be consulted **at all**. Necessary but no longer sufficient — the peer must also be in `trusted-proxies`. | `true` | OPTIONAL |
> | `wecms.dispatch.callback.allowed-ips` | `WECMS_CALLBACK_ALLOWED_IPS` | Literal IPv4/IPv6 and/or CIDR, comma-separated. **A malformed entry ABORTS STARTUP.** No in-yaml default in `sit`/`uat`/`prod` — an unset variable must fail fast. | *(none)* | **REQUIRED** |
>
> **`trust-forwarded-for: true` is no longer hardcoded** and is no longer spoofable on its own.
> Full trust model, behaviour matrix and rollback:
> [callback-security-configuration.md](callback-security-configuration.md).

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `WECMS_CALLBACK_ALLOWED_IPS` | Comma-separated source-IP allow-list for `/api/v1/payment/dispatch/callbacks/**`. | `${VAN_GATEWAY_IP_1},${VAN_GATEWAY_IP_2}` | SIT/UAT/PROD (**REQUIRED**, may be empty string) | SENSITIVE | **Empty → the filter is a no-op and every source IP is accepted (fail-open).** Payload `VV` HMAC becomes the only gate. | `CallbackIpWhitelistFilter` |
| *(no variable)* `wecms.dispatch.callback.trust-forwarded-for` | Trust the first `X-Forwarded-For` hop when resolving the client IP. **Hardcoded `true`** in all four profile YAMLs. | — | — | PUBLIC | `true` — spoofable unless a trusted reverse proxy overwrites/strips inbound XFF (audit H-2) | `CallbackIpWhitelistFilter` |
| *(no variable)* `wecms.dispatch.callback.path-prefix` | Path the filter guards. | — | — | PUBLIC | `/api/v1/payment/dispatch/callbacks` | `CallbackProperties` |

---

## 4. EFS / VAN Center gateway (outbound)

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `WECMS_EFS_ENABLED` | See §1. | `false` | SIT/UAT/PROD (**REQUIRED**) | PUBLIC | `false` → mock throws | `EfsClientConfig` |
| `WECMS_EFS_BASE_URL` | Base URL of the EFS gateway. Endpoint paths (`/WGVN_EFS_201.jct` …) are vendor-fixed in code. | `${EFS_GATEWAY_BASE_URL}` | SIT/UAT/PROD (**REQUIRED**; must be non-empty when EFS is on) | SENSITIVE | empty → `RestClient` has no base URL; requests fail | `EfsClientConfig`, `EfsProperties` |
| ~~`WECMS_EFS_PARTNER_ID`~~ **RETIRED 2026-07-29 (D-2)** | **Setting this has no effect. Do not go looking for a value — it cannot bind to anything.** The property `wecms.dispatch.efs.partner-id` was removed: `EfsProperties` has no `partnerId` field (`EfsProperties.java:67`), `getPartnerId()` exists nowhere in `src/`, and the key is in no YAML under `src/main/resources/`, `bin/main/` or `build/resources/main/`. `SchedulerConfigurationContractTest.partnerIdPropertyIsRetired` fails the build if it returns. **The envelope `ID` is unconditionally `TB_FIRM_CONF.VAN_ORG_CD` — no fallback exists** (all six build sites assign `String id = cmd.vanOrgCd();` with no branch). **Not to be confused with the vendor's `prtn_id`**, a separate *per-account* body field inside `EV` sourced from `TB_FIRM_CONF.BANK_PRTN_ID`. **Real check → the row below.** | *(none)* | **nowhere** — not read in any profile | n/a | n/a — unbindable | *(nothing)* |
| *(not a variable)* `TB_FIRM_CONF.VAN_ORG_CD` + `BANK_PRTN_ID` | The two values the retired variable was mistaken for. `VAN_ORG_CD` → envelope `ID`; `BANK_PRTN_ID` → vendor body `prtn_id`. **Both are per *withdrawal account*** — `TB_FIRM_CONF`'s PK is `(biz_key, out_bank_cd, wdrl_act_no)`, so one tenant+bank can legitimately carry different values per account. | *(DB row)* | all, when EFS is on — **verify per account, per environment** | PUBLIC | Wrong/placeholder row → a nonsense partner identity on every EFS call **for that account only**; an unknown `VAN_ORG_CD` also makes `VanApiKeyService` throw (fails closed) | `EfsProperties` consumers via `TbFirmConfRepository` |
| *(not a variable)* `TB_VAN_API_INFO.API_KEY` | Per-VAN AES/HMAC key that builds `EV` and verifies `VV`. **Deliberately never in YAML or any env var** — provisioned out-of-band into the DB. | `******` | all, when EFS is on | **SECRET** | Missing row → `VanApiKeyService.getApiKey` **throws**, so crypto cannot run with a missing key (fails closed) | `VanApiKeyService` → `EfsCryptoService` |
| *(hardcoded)* `wecms.dispatch.efs.connect-timeout-ms` | Connect timeout. | — | — | PUBLIC | `3000` | `EfsClientConfig` |
| *(hardcoded)* `wecms.dispatch.efs.read-timeout-ms` | Read timeout. | — | — | PUBLIC | `20000` | `EfsClientConfig` |
| *(code default)* `wecms.dispatch.efs.endpoints.*` | 7 vendor endpoint paths (`EFS_201/202/203/204/301/302` + `EFS_MSG`). | — | — | PUBLIC | vendor-fixed map in `EfsProperties.defaultEndpoints()` | `EfsProperties` |

> **`WECMS_EFS_PARTNER_ID` correction, 2026-08-01.** This row previously read *"**REQUIRED**; must be
> non-empty when EFS is on … empty → gateway rejects"*, which sent readers looking for a value that
> cannot bind to anything. Verification record:
> [wecms-batch-infrastructure-readiness-audit.md §6.1 + decision D-2](wecms-batch-infrastructure-readiness-audit.md).

---

## 5. SFTP / external file service (outbound)

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `WECMS_SFTP_ENABLED` | Real `JschSftpClient` vs `MockSftpClient`. | `false` | SIT/UAT/PROD (**REQUIRED**) | PUBLIC | `false` → mock; no network I/O | `JschSftpClient` / `MockSftpClient` |
| `WECMS_SFTP_HOST` | SFTP host. | `${SFTP_HOST}` | when SFTP on (**REQUIRED**) | SENSITIVE | empty → connect fails | `JschSftpClient` |
| `WECMS_SFTP_PORT` | SFTP port. Templates carry `3300` (non-standard) — confirm per environment. | `3300` | when SFTP on (**REQUIRED**) | SENSITIVE | code default `22` | `JschSftpClient` |
| `WECMS_SFTP_USERNAME` | SFTP account. | `${SFTP_USERNAME}` | when SFTP on (**REQUIRED**) | SENSITIVE | empty | `JschSftpClient` |
| `WECMS_SFTP_KEY_PATH` | Filesystem path to the private key. The key **material** is never in config. File must be `0600` and owned by the app user. | `/opt/wecms/secrets/sftp_id_rsa` | when SFTP on (**REQUIRED**) | SENSITIVE (**points at a SECRET**) | empty → falls back to password auth | `JschSftpClient` |
| `WECMS_SFTP_PASSWORD` | Key passphrase, or password when no key is used. Legacy used key-only. | `******` | alternative to the key (**REQUIRED** as a variable) | **SECRET** | empty | `JschSftpClient` |
| `WECMS_SFTP_LOCAL_UPLOAD` | Local staging dir the 1000-byte 00301 request file is written to before upload. **Empty in `.env.sit.example`.** | `/DATA/src-wecms/wecms-backend/upload-files/send` | when SFTP on (**REQUIRED**) | SENSITIVE | empty → `System.getProperty("java.io.tmpdir")` (`BulkReceptionService:630`) | `BulkReceptionService` |
| `WECMS_SFTP_REMOTE_UPLOAD` | Remote dir the request file is uploaded into (legacy `sdata`). | `sdata` | when SFTP on (**REQUIRED**) | SENSITIVE | empty | `JschSftpClient` |
| `WECMS_SFTP_LOCAL_DOWNLOAD` | Local dir the 01302 result file is downloaded into. **Empty in `.env.sit.example`.** | `/DATA/src-wecms/wecms-backend/upload-files/receive` | when SFTP on (**REQUIRED**) | SENSITIVE | empty → `java.io.tmpdir` (`BulkExecutionResultService:342`) | `BulkExecutionResultService` |
| `WECMS_SFTP_REMOTE_DOWNLOAD` | Remote dir the result file is downloaded from (legacy `rdata`). | `rdata` | when SFTP on (**REQUIRED**) | SENSITIVE | empty | `JschSftpClient` |
| `WECMS_SFTP_REMOTE_MOVE` | Remote dir a processed result file is moved to (legacy `rdata/success`). | `rdata/success` | when SFTP on (**REQUIRED**) | SENSITIVE | empty | `JschSftpClient` |
| *(hardcoded)* `wecms.dispatch.sftp.connect-timeout-ms` | Connect / channel timeout. | — | — | PUBLIC | `60000` | `JschSftpClient` |

---

## 6. Database

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `WECMS_DB_URL` | JDBC URL of the refactor DB. DEV/SIT templates point at `172.168.5.172:5432/wecmsdevdb`; **UAT/PROD are placeholders — the real URLs are not committed.** | `jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}` | all (**REQUIRED**) | SENSITIVE | none → startup abort | Hikari / all repositories |
| `WECMS_DB_USERNAME` | DB user. | `${DB_USER}` | all (**REQUIRED**) | SENSITIVE | none | Hikari |
| `WECMS_DB_PASSWORD` | DB password. | `******` | all (**REQUIRED**) | **SECRET** | none | Hikari |

Schema is **external and pre-migrated**: `ddl-auto: validate` in every profile, and Flyway is **not a
dependency** — so nothing is applied at startup. The 161 `V*.sql` scripts under
`src/main/resources/db/migration/` are DBA-run references, not runtime migrations.

---

## 7. Security / crypto

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `WECMS_JWT_SECRET` | JWT signing secret. ≥ 32 bytes → HS256; ≥ 64 bytes → HS512. **Fail-fast** on missing/short. | `******` | all (**REQUIRED**) | **SECRET** | none → startup abort (intended) | `JwtTokenProvider` |
| `WECMS_CRYPTO_LEGACY_CERT_SEED` | Seed for the legacy pgcrypto certificate cipher. **The real value is committed in all five `.env.*.example` files and as a code default** (`BankCertCryptoService:69`) — audit H-6. Cannot be freely rotated (legacy data compatibility). | `******` | all (**REQUIRED**) | **SECRET** | code default present | `BankCertCryptoService` |
| `WECMS_CORS_ALLOWED_ORIGINS` | Comma-separated exact origins. Never `*`. | `https://${FRONTEND_HOST}` | all (**REQUIRED**) | SENSITIVE | none → startup abort | `CorsConfig` |

---

## 8. Adjacent batch schedulers (share the ShedLock table)

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `WECMS_EXCHANGE_RATE_BATCH_ENABLED` | Enables 3 FX collection crons. **Crons are hardcoded** (`0 30 8 * * ?`, `0 30 8-18 * * ?`, `0 0,30 9-20 * * ?`) — not tunable per environment (audit M-3). | `false` | all (**REQUIRED**) | PUBLIC | absent → startup abort; `false` → beans not created | `ExchangeRateBatchScheduler` |
| `WECMS_OPERATION_STATUS_BATCH_ENABLED` | Enables the operation-status seed + refresh jobs. | `false` | all (**REQUIRED**) | PUBLIC | `false` → beans not created | `OperationStatusBatchScheduler` |
| `WECMS_OPERATION_STATUS_BATCH_SEED_CRON` | Seed cron. | `0 0 0 * * ?` | all (**REQUIRED**) | PUBLIC | code fallback `0 0 0 * * ?` | idem |
| `WECMS_OPERATION_STATUS_BATCH_REFRESH_CRON` | Refresh cron. | `0 0 0 * * ?` | all (**REQUIRED**) | PUBLIC | code fallback `0 0 0 * * ?` | idem |

---

## 9. Haengsung ERP (secondary Oracle DataSource)

| Variable | Description | Placeholder | Required in | Secret | Default behaviour | Component |
|---|---|---|---|---|---|---|
| `HAENGSUNG_ORACLE_ENABLED` | Master switch for the Oracle DataSource and 8 `@ConditionalOnProperty`-gated beans. | `false` | all (**REQUIRED**) | PUBLIC | `false` → no Oracle DS, all ERP beans absent | `HaengsungOracleDataSourceConfig:40` |
| `HAENGSUNG_ORACLE_URL` | Oracle JDBC URL. | `jdbc:oracle:thin:@//${ORACLE_HOST}:1521/${ORACLE_SERVICE}` | all (**REQUIRED**) | SENSITIVE | none | idem |
| `HAENGSUNG_ORACLE_USERNAME` | Oracle user. | `${ORACLE_USER}` | all (**REQUIRED**) | SENSITIVE | none | idem |
| `HAENGSUNG_ORACLE_PASSWORD` | Oracle password. | `******` | all (**REQUIRED**) | **SECRET** | none | idem |
| `HAENGSUNG_SCHEDULED_IMPORT_ENABLED` / `_CRON` | ERP scheduled-payment import job. | `false` / `0 */5 * * * *` | all (**REQUIRED**) | PUBLIC | disabled | `HaengsungScheduledPaymentImportJob` |
| `HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED` / `_CRON` | ERP FX sync job. | `false` / `0 */5 * * * *` | all (**REQUIRED**) | PUBLIC | disabled | `HaengsungExchangeRateSyncJob` |
| `HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED` / `_CRON` | ERP card + account sync job. | `false` / `0 */5 * * * *` | all (**REQUIRED**) | PUBLIC | disabled | `HaengsungCardAccountSyncJob` |

> Per memory `project_hs_prod_datasource_ora17008`, the Oracle DataSource needs a validation query /
> connection test to avoid `ORA-17008` on stale connections. Hikari settings for this DS bind from
> `spring.datasource.hikari` (`HaengsungOracleDataSourceConfig:65`) — confirm the deployed values.

---

## 10. Gateways (Coocon / OSMC) and file storage

| Variable | Description | Placeholder | Required in | Secret | Component |
|---|---|---|---|---|---|
| `WECMS_COOCON_API_KEY` | Coocon bigdata API key. | `******` | all (**REQUIRED**) | **SECRET** | `CooconProperties` |
| `WECMS_COOCON_API_URL` | Coocon bigdata URL. | `https://${COOCON_HOST}/overseas/finance/scrap/bigdata_info.jsp` | all (**REQUIRED**) | SENSITIVE | idem |
| `WECMS_COOCON_SMART_API_KEY` | Smart-gateway key (also used by NXiSAS scraping). | `******` | all (**REQUIRED**) | **SECRET** | idem |
| `WECMS_COOCON_SMART_API_URL` | Smart-gateway URL. DEV/SIT `dev-sgw…:8443`, UAT/PROD `sgw…`. | `https://${SGW_HOST}/domestic/finance/scrap/smart_gateway.jsp` | all (**REQUIRED**) | SENSITIVE | idem |
| `WECMS_COOCON_UNIQUE_KEY` | Coocon unique key. | `${COOCON_UNIQUE_KEY}` | all (**REQUIRED**) | **SECRET** | idem |
| `WECMS_OSMC_URL` / `_API_KEY` / `_API_ID` / `_UNIQUE_KEY` | OSMC GDT lookup gateway. | `https://${SGW_HOST}/...` / `******` / `BI09` / `${OSMC_UNIQUE_KEY}` | all (**REQUIRED**) | key + unique = **SECRET** | `OsmcProperties` |
| 10 × `WECMS_*_STORAGE_ROOT` | Upload/download roots for overseas-payment, vendor-payment, expense-payment, payroll, contacts, preferences, user-image, erp-transaction-history, card-transactions, internal-transfer. | `/DATA/src-wecms/wecms-backend/upload-files` | all (**REQUIRED**) | SENSITIVE | 10 `*StorageProperties` classes |

> The historically-committed Coocon keys are an **open rotation item** carried from the prior security
> review. Rotate before any PROD activation.

---

## 11. Properties with no environment variable (code defaults only)

These are read by batch-adjacent code but have **no YAML entry and no variable**. They cannot be tuned
per environment without adding a YAML line first.

| Property | Code default | Used by |
|---|---|---|
| `wecms.internal-transfer.routing.shinhan-bank-cd` | `970424` | 5 dispatch executors + `BulkTransferValidator:57` |
| `wecms.internal-transfer.routing.woori-bank-cd` | `970457` | 5 dispatch executors + `BulkTransferValidator:59` |
| `wecms.internal-transfer.routing.vcb-bank-cd` | `970436` | 4 dispatch executors |
| `wecms.dispatch.operating-hours.zone` | `Asia/Ho_Chi_Minh` (`OperatingHoursService:85`) | `OperatingHoursService:236` — the wall-clock zone the `HHmmss` batch-schedule window is evaluated in. **Added 2026-07-28.** Deliberately in-code-only (no YAML entry) so an unset property cannot abort startup (finding H-1). Set to `Asia/Seoul` **only** for bug-for-bug parity with a co-hosted legacy JEX batch — the JEX frame DB runs `Asia/Seoul` (UTC+9), so legacy reads a `090000`–`153000` window as 07:00–13:30 ICT. ICT is the correct interpretation for Vietnamese bank cut-offs and matches `CutoffValidator.VIETNAM_ZONE`. |
| `wecms.internal-transfer.cutoff.refresh-interval-ms` | `300000` | `CutoffConfigProvider:133` |
| `wecms.nxisas.timeout-seconds` / `.session-idle-seconds` | `60` / `300` | `NxisasSessionRegistry` |
| `wecms.oat.seed-decrypt.enabled` | `false` | OAT decrypt endpoints |
| `wecms.jwt.expires-in-seconds` / `.issuer` | `3600` / `wecms-api` | `JwtTokenProvider` |

---

## 12. Startup-required set (minimum to boot)

Because the profile YAMLs bind `${VAR}` with no in-YAML default, **all of the following must be present** or
the application aborts at startup with `Could not resolve placeholder`:

```
WECMS_DB_URL  WECMS_DB_USERNAME  WECMS_DB_PASSWORD
WECMS_JWT_SECRET  WECMS_CORS_ALLOWED_ORIGINS  WECMS_CRYPTO_LEGACY_CERT_SEED
WECMS_DISPATCH_AUTO_RECEIVE  WECMS_DISPATCH_REALTIME_DISPATCH  WECMS_DISPATCH_RECIPIENT_INQUIRY
WECMS_DISPATCH_RESULT_CHECK_CRON  WECMS_DISPATCH_ALERT_ENABLED  WECMS_CALLBACK_ALLOWED_IPS
WECMS_EFS_ENABLED  WECMS_EFS_BASE_URL  WECMS_EFS_PARTNER_ID
WECMS_SFTP_ENABLED  WECMS_SFTP_HOST  WECMS_SFTP_PORT  WECMS_SFTP_USERNAME
WECMS_SFTP_KEY_PATH  WECMS_SFTP_PASSWORD
WECMS_SFTP_LOCAL_UPLOAD  WECMS_SFTP_REMOTE_UPLOAD
WECMS_SFTP_LOCAL_DOWNLOAD  WECMS_SFTP_REMOTE_DOWNLOAD  WECMS_SFTP_REMOTE_MOVE
WECMS_EXCHANGE_RATE_BATCH_ENABLED
WECMS_OPERATION_STATUS_BATCH_ENABLED  WECMS_OPERATION_STATUS_BATCH_SEED_CRON  WECMS_OPERATION_STATUS_BATCH_REFRESH_CRON
WECMS_COOCON_API_KEY  WECMS_COOCON_API_URL  WECMS_COOCON_SMART_API_KEY
WECMS_COOCON_SMART_API_URL  WECMS_COOCON_UNIQUE_KEY
WECMS_OSMC_URL  WECMS_OSMC_API_KEY  WECMS_OSMC_API_ID  WECMS_OSMC_UNIQUE_KEY
HAENGSUNG_ORACLE_ENABLED  HAENGSUNG_ORACLE_URL  HAENGSUNG_ORACLE_USERNAME  HAENGSUNG_ORACLE_PASSWORD
HAENGSUNG_SCHEDULED_IMPORT_ENABLED    HAENGSUNG_SCHEDULED_IMPORT_CRON
HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED  HAENGSUNG_EXCHANGE_RATE_SYNC_CRON
HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED   HAENGSUNG_CARD_ACCOUNT_SYNC_CRON
+ the 10 WECMS_*_STORAGE_ROOT variables
```

Empty-string values satisfy the placeholder for `String` properties (e.g. `WECMS_CALLBACK_ALLOWED_IPS=`).
They do **not** satisfy `boolean` or `int` properties — `WECMS_SFTP_PORT=` fails to bind.

**Recommendation (audit §12, item 9):** give every one of these an explicit in-YAML default so a missing
variable degrades to the safe value instead of aborting startup.
