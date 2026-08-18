# SIT / UAT Scheduler Deployment Checklist

**Created:** 2026-07-28
**Applies to:** `wecms-api` scheduler subsystem — 13 `@Scheduled` methods / 10 classes
**Companions:** [scheduler-final-readiness.md](../audits/deployment/scheduler-final-readiness.md) · [scheduler-legacy-parity-audit.md](../audits/legacy-parity/scheduler-legacy-parity-audit.md) · [scheduler-audit.md](../audits/legacy-parity/scheduler-audit.md)
**Sibling checklists:** [sit-security-deployment-checklist.md](sit-security-deployment-checklist.md) · [sit-deployment-checklist-wecms-batch.md](sit-deployment-checklist-wecms-batch.md) · [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md)

> **Two rules that override everything else on this page.**
>
> 1. **ShedLock does not coordinate with legacy.** The `shedlock` table holds only refactor lock names; the legacy JEX / `WECMS_BATCH DEV` runtimes never touch it. A refactor job and its legacy twin running against the same database are **completely unsynchronised**. Coexistence is settled by switching one side off — never by the lock.
> 2. **Committed `.env` files are not evidence.** Live `shedlock` rows on 2026-07-28 showed all 8 flag-gated jobs firing on node `GroupwareDev` while `batch-dispatch-variable-reference.md` declared them all `false`. Always read the host env (SIT: `bin/setenv.sh`).

---

## Phase 0 — Deploy with schedulers disabled (the default path)

Nothing here requires a scheduler environment variable. This is the expected first deployment.

- [ ] Confirm the branch carries the remediated defaults: `git log --oneline -- src/main/resources/application*.yml src/main/java/com/kosign/wecms/common/lock/`
- [ ] `./gradlew test` → expect **2052 tests, 0 failures** (260 classes)
- [ ] Confirm no scheduler is enabled in committed config:
      `grep -rn "ENABLED=true" .env.sit .env.uat.example .env.prod.example` → **no scheduler hits**
- [ ] Deploy. Verify the app boots with **no** scheduler env vars set.
- [ ] **Startup log check** — confirm ABSENT (bean never created ⇒ no trigger):
      `ExchangeRateBatchScheduler`, `OperationStatusBatchScheduler`, `HaengsungScheduledPaymentImportJob`, `HaengsungExchangeRateSyncJob`, `HaengsungCardAccountSyncJob`
- [ ] Confirm PRESENT but never firing (cron `-`): `ResultCheckJob`, `StrandedRecoveryJob`, `StuckPayFileMonitorJob` — no `…fired` DEBUG lines
- [ ] Confirm the two always-on jobs are healthy:
  - `NxisasSessionRegistry.evictIdle` — runs ~immediately after context refresh, then every 60 s
  - `CutoffConfigProvider` — log line `cutoff-config loaded from DB (DB-only, no YAML fallback)`
- [ ] **Pre-req for the above:** `TB_CUTOFF_CONFIG` + `TB_CUTOFF_PARAM` are populated. `@PostConstruct` **fails startup** on missing/invalid rows by design (never fail open on payment cut-offs).
- [ ] Confirm `shedlock` table exists (DBA-run script; Flyway is disabled, `ddl-auto: validate`)
- [ ] Confirm `spring.task.scheduling.pool.size` is still **1** — it is load-bearing for correctness, not a tuning knob

**Exit criteria:** app healthy, no scheduler fired, no scheduler env var required.

---

## Phase 1 — Establish the facts (blocks every enablement)

- [ ] **OQ-9 — DB identity.** Is the target DB the live JEX batch DB? Record the answer in writing.
      SIT is expected to be the refactor instance `172.168.5.172:5432/wecmsdevdb`, **different** from JEX DEV `10.254.242.254:54311/wecmsvndevdb`. Verify for the actual node.
- [ ] **OQ-4 — deployed values.** Dump the real flags/crons from the host (SIT: `bin/setenv.sh`) and diff against `.env.*`. Record the diff; do not "fix" the host as part of this checklist.
- [ ] **Legacy activity snapshot** (read-only, on the DB the refactor will write):
  ```sql
  -- FX: is the legacy EXCH batch still writing?
  SELECT bank_cd, MAX(reg_dtm) AS last_reg, MAX(mod_dtm) AS last_mod, MAX(seq_no::int) AS max_seq
  FROM bank_exch_rate_infm GROUP BY bank_cd;

  -- Operation status: is the legacy OPRT batch still calling out?
  SELECT web_svc_id, COUNT(*) AS calls, MAX(reg_dtm) AS last_reg
  FROM ext_tran_call_his WHERE web_svc_id LIKE 'OPRT%' GROUP BY web_svc_id;

  -- Which refactor jobs have ever fired here?
  SELECT name, locked_at, lock_until, locked_by FROM shedlock ORDER BY locked_at DESC;
  ```
  - [ ] Record `last_reg` / `last_mod` per bank — a timestamp from today means legacy FX is live
  - [ ] Record the OPRT call timestamps — **note the timezone**: legacy stamps `reg_dtm` with `DateTimeUtils.utcNow()` (UTC), while `shedlock` uses DB-local time
  - [ ] **Do not modify or clear any `shedlock` row.** Read only.

**Exit criteria:** written answers to OQ-9 and OQ-4, plus a legacy-activity snapshot for the target DB.

---

## Phase 2 — SIT scheduler testing

Enable **one job at a time**. After each, verify before proceeding.

### 2a. `StuckPayFileMonitorJob` — safe first

Read-only (two reads per stuck file), no legacy twin, no mutation.

- [ ] `WECMS_DISPATCH_STUCK_MONITOR_ENABLED=true` **and** `WECMS_DISPATCH_STUCK_MONITOR_CRON=<cron>` (both gates; either alone is inert)
- [ ] Restart — **no `@RefreshScope`, no actuator `/refresh`; every flag change needs a restart** and `@Scheduled(cron=…)` resolves once at bean creation
- [ ] Verify `shedlock` gains `StuckPayFileMonitorJob.run` with a moving `locked_at`
- [ ] Verify `OpsAlertService` output is sane and no table was written

### 2b. Exchange-rate batch (3 jobs, one flag)

- [ ] **Precondition:** legacy `EXCH_0001_001` / `EXCH_0010_001` / `EXCH_0020_001` are **off** on this DB, *or* Phase 1 proved the DB is refactor-only
- [ ] Note the crons are **hardcoded** (`0 30 8`, `0 30 8-18`, `0 0,30 9-20` ICT) — not tunable per environment
- [ ] Note **all three collide at 08:30 ICT**; `pool.size: 1` serialises them in an undefined order — expected, not a fault
- [ ] `WECMS_EXCHANGE_RATE_BATCH_ENABLED=true`, restart
- [ ] Verify 3 new `shedlock` rows: `exch-shinhan-first-time`, `exch-shinhan-last-time`, `exch-woori`
- [ ] Verify `bank_exch_rate_infm` writes carry the expected signatures — Shinhan `11704088` / `COMMON` / `GLOBAL/GBL/BANK_Shinhan`, Woori `11704020` / `CORPORATE` / `GLOBAL/GBL/BANK_Woori`
- [ ] **Woori seq check:** confirm `SEQ_NO` increments by exactly 1 per round and no duplicate round appeared (legacy and refactor both do cross-tenant `MAX+1` + INSERT-only — the collision-prone leg)
- [ ] Exercise the **on-demand** path: open the Exchange Rate page on an empty day *while* a scheduled FX job runs; confirm the log shows `job-lock '…' already held — skipping` and no duplicate rows
- [ ] **Known gap:** legacy `EXCH_0030_001` (multi-bank × multi-SEQ backfill) has no refactor twin — a missed window will **not** self-heal (OQ-2)

### 2c. Operation-status batch

- [ ] **Precondition:** legacy `OPRT_0001_001` / `OPRT_0010_001` off, or DB proven refactor-only
- [ ] **The cron default is `-` on purpose.** OQ-3 is unresolved: legacy DEV last fired `15:00:01` UTC = 22:00 ICT = 00:00 `Asia/Seoul`, not the 00:00 ICT once assumed. For SIT *testing* pick any convenient cron; **do not** treat that choice as the production schedule.
- [ ] `WECMS_OPERATION_STATUS_BATCH_ENABLED=true` + `WECMS_OPERATION_STATUS_BATCH_CRON=<test cron>`, restart
- [ ] Verify **one** `shedlock` row `oprt-status-batch` (not two — seed and refresh share one lock)
- [ ] Verify log line `operation-status-batch pass done seed=… refresh=…` with seed **before** refresh
- [ ] Verify semantics preserved: seed **inserts only** new `(BIZ_KEY, TAXCODE)`; refresh **updates only** existing rows
- [ ] Remember `OPRT_STTS_INFO` is also written by the interactive JEX screen, so disabling the legacy batch alone does not make the table refactor-exclusive

### 2d. HAENGSUNG syncs (3 jobs, 3 flags + the Oracle master gate)

- [ ] **Precondition:** legacy `ERP_0100_001` / `_002` / `_003` off for the tested job
- [ ] Confirm the corrected crons are in effect (legacy `.batch` parity):

  | Job | Cron | Legacy source |
  |---|---|---|
  | exchange-rate sync | `0 */10 7-19 * * ?` | `ERP_0100_001.batch` — `*/10 7-19 * * *` |
  | payment import | `0 */5 7-19 * * ?` | `ERP_0100_002.batch` — `*/5 7-19 * * *` |
  | card/account sync | `0 0 7-19 * * ?` | `ERP_0100_003.batch` — `0 7-19 * * *` (hourly) |

- [ ] **OQ-10 caveat:** the `7-19` window is anchored to ICT here. If the legacy WAS runs `Asia/Seoul`, the real legacy window is 05:00–17:00 ICT. Cadence is verified exact; the boundary is not.
- [ ] Both gates per job: `HAENGSUNG_<X>_ENABLED=true` **and** `HAENGSUNG_ORACLE_ENABLED=true` (with Oracle off the job fires and logs `skipped — Oracle feature disabled`)
- [ ] Verify `shedlock` rows appear and `ERP_SND_YN` flips exactly once per source row (**no XA** between PostgreSQL and Oracle — a double push cannot be unwound)
- [ ] Exercise the **interactive Refresh** during a scheduled import: expect `WBE_HS_REFRESH_IN_PROGRESS` (HTTP 200, `status.error=true`) and **no** second import
- [ ] **Known gap:** legacy `ERP_0100_004` (WeCMS→ERP vendor/expense/payroll/overseas export, `*/5 7-19`) has no refactor scheduler (OQ-1)

### 2e. Jobs that stay OFF in SIT

- [ ] `ResultCheckJob` — **leave `-`.** Mutates real payment status; enable only inside a dispatch activation exercise, never a scheduler smoke test. *(2026-07-29: the scan is now bounded — `WECMS_DISPATCH_RESULT_CHECK_BATCH_SIZE`, default 100, safe inline default. Scope is GLOBAL across tenants, matching legacy `IDO.TB_FIRM_TRAN_R001` by design. When it is eventually enabled, size the batch against expected daily 202 volume and watch for the `candidate batch cap reached` WARN.)*
- [ ] `StrandedRecoveryJob` — **leave `-` and `enabled=false`.** Re-drives real payments and cannot distinguish refactor-originated from JEX-originated rows (PO / Mr. Kang)
> **No `BulkExecutionRetryJob` exists.** One was added on 2026-07-29 and **removed the same day** — it was
> K9 Option A ("Recovery Scheduler"), offered to Mr. Kang and not selected. There is nothing to leave
> disabled: the job, its ShedLock identity, its YAML keys and its 5 `WECMS_DISPATCH_BULK_EXECUTION_RETRY_*`
> variables are all deleted. If a host still sets them, remove them — they bind to nothing.
> Residual recovery is monitoring + manual (see the note on 2a below).
> [final-four-to-one-batch-architecture-correction.md](../audits/batch/final-four-to-one-batch-architecture-correction.md)

**Exit criteria:** each enabled job has a `shedlock` row, expected target-table writes, and no duplicate/conflicting rows versus legacy.

> **Note on 2a.** `StuckPayFileMonitorJob` gained a **second read-only sweep** on 2026-07-29 covering
> `TB_FIRM_FILE_MST` at `PROC_STEP='1'/PROC_STAT='6'` (bank has the file, `00302` unconfirmed). Enabling
> the monitor in SIT now also surfaces that state — with no mutating gate opened. This is the intended
> the approved residual-recovery signal — there is no automatic retry to fall back on. It classifies
> `BULK_EXECUTION_NOT_ISSUED` (safe to re-drive) vs `BULK_EXECUTION_UNCONFIRMED` (reconcile with the VAN
> Center first). Watch for
> `feature=stuck-pay-file-monitor SUMMARY … firm_file_stuck_total=`.

---

## Phase 2f — Callback security (H-1, added 2026-07-29) — **no flag to enable**

Callback IP filtering is now **fail-closed and ON by default** (`protection: ENFORCE`) in
`sit`/`uat`/`prod`. Nothing needs switching on; what is needed is correct configuration, or callbacks
will be refused.

- [ ] `WECMS_CALLBACK_ALLOWED_IPS` is set to the **real VAN gateway source IP(s)** (literals and/or CIDR, comma-separated). **NEEDS CONFIRMATION from the VAN Center** — still open
- [ ] `WECMS_CALLBACK_TRUSTED_PROXIES` set **only** if TLS terminates at a reverse proxy; otherwise leave empty (empty = no proxy trusted = `X-Forwarded-For` ignored, which is the safe value)
- [ ] `WECMS_CALLBACK_PROTECTION` is **not** overridden to `DISABLED` in this environment
- [ ] Startup log shows `Callback IP allow-list active: N rule(s), M trusted proxy rule(s)` — **not** `… is EMPTY while protection=ENFORCE`
- [ ] Negative check: a request from an IP outside the list returns **403**
- [ ] Positive check: a request from the gateway IP reaches the controller

> **Expect loud failure, not silence.** Previously an unset allow-list silently accepted callbacks
> from any source. Now a misconfiguration produces 403 + a WARN per request. That is intended — do not
> "fix" it with `protection: DISABLED`.
> A malformed entry (bad IP, `/33`, hostname) **aborts startup** by design.
> Full model: [callback-security-configuration.md](callback-security-configuration.md).

## Phase 3 — UAT gate (do not start until every box is ticked)

- [ ] **OQ-9 answered:** the UAT DB is confirmed **not** the live JEX batch DB — or, if it is, each family below has its legacy side confirmed off
- [ ] **Legacy-off evidence, per family** (written, from the legacy batch owner):
  - [ ] FX — `EXCH_0001_001`, `EXCH_0010_001`, `EXCH_0020_001` disabled
  - [ ] Operation status — `OPRT_0001_001`, `OPRT_0010_001` disabled
  - [ ] HAENGSUNG — `ERP_0100_001`, `ERP_0100_002`, `ERP_0100_003` disabled
  - [ ] Result-check — `PROCESSING_RESULT_CHECK_BATCH` (`WECMS_BATCH DEV`, `*/1 * * * *`) disabled
- [ ] **OQ-3 resolved** — JEX Batch Admin entry **and timezone** for OPRT, then set `WECMS_OPERATION_STATUS_BATCH_CRON` to the confirmed value
- [ ] **OQ-10 resolved** — legacy WAS timezone confirmed; adjust the HAENGSUNG `7-19` windows if it is not ICT
- [ ] **OQ-2 resolved** — decision on whether an `EXCH_0030_001` backfill equivalent is required *before* legacy FX is switched off
- [ ] **OQ-1 resolved** — `ERP_0100_004` confirmed replaced by the event-driven Step-5 write-back, or scheduled for implementation
- [ ] **OQ-5 resolved** — event-driven dispatch honours the legacy per-`out_bank_cd` `tb_batch_schedule_info` windows and zone
- [x] **R-1 / R-2 (VCB schedule row) — RESOLVED 2026-07-29: exact legacy parity, no VCB row.** Legacy has no `970436` `tb_batch_schedule_info` row in any environment or script (SVN r36822, the 2026-07-16 VCB file-transfer switch, shipped **no** schedule script). The refactor-only row that had been seeded on the refactor DEV DB is **withdrawn** — seed tuple commented out, removal script at [`delete-vcb-batch-schedule-info-row-2026-07-29.sql`](../sql/2026/delete-vcb-batch-schedule-info-row-2026-07-29.sql). **No production Java changed.** R-2 is **NOT APPLICABLE** — no legacy row means no legacy `ANT_WHERE_USE_YN` value, neither `'Y'` nor `'N'`.
  - [ ] **DBA:** run the rollback script on the refactor DEV DB and confirm the post-check shows `970424` + `970457` only.
  - [ ] **Before enabling `WECMS_DISPATCH_AUTO_RECEIVE`, accept this consequence in writing:** **VCB dispatch will fail (`NO_CRITERIA` → `PROC_STAT=FAIL` + ops alert) until the business explicitly decides to introduce a VCB schedule.** That is legacy behaviour, not a regression. Do **not** re-seed a VCB row to suppress the failures — introducing a VCB schedule is a forward PO (Mr. Jung) decision that also requires a fresh `ANT_WHERE_USE_YN` decision.
- [ ] **R-3 — run the three comparison queries on UAT and PROD** (`tb_batch_schedule_info`, `tb_batch_schedule_dtl`, `tb_firm_conf` grouped by `org_stat`) and diff against JEX DEV. Only DEV was reachable at audit time. Queries: [../references/support-matrix.md](../references/support-matrix.md) §12.
- [ ] **R-4 — choose the operating-hours zone explicitly per environment.** Default `Asia/Ho_Chi_Minh` is the correct business reading (proven: the schedule values equal the Vietnam-clock `getCutoffConfig` windows). Set `wecms.dispatch.operating-hours.zone=Asia/Seoul` **only** if a co-hosted legacy JEX batch shares the DB and bug-for-bug parity is required — legacy's `Asia/Seoul` clock closes every window 2 h early. Confirm the target DB's `current_setting('TimeZone')` before deciding.
- [ ] Full profile context boot verified in UAT (**not** covered by unit tests — those assert property resolution only)
- [ ] `shedlock` present; host secrets set (`WECMS_JWT_SECRET` etc. fail fast); Coocon keys rotated
- [ ] Rollback rehearsed: set the flag back to `false` / cron back to `-` and restart. **Note:** a stale `shedlock` row persists until `lock_until` passes (2–30 min by job) — that is the intended crash-recovery mechanism, so a fast restart may find its own job still "locked". **Do not clear the row.**

**Exit criteria:** every box ticked, with each answer recorded in the audit doc's §12 table.

---

## Rollback

1. Set the job's `*_ENABLED=false` (or its cron back to `-`).
2. Restart the app — flag changes take effect **only** on restart.
3. Confirm the job stops appearing in `shedlock` with a fresh `locked_at`.
4. Leave existing `shedlock` rows alone; they expire on their own.
5. If a legacy batch was switched off for the cutover, switch it back on **before** declaring rollback complete — otherwise neither side is running.

## Escalation

| Symptom | Likely cause | Action |
|---|---|---|
| App won't start, `Could not resolve placeholder` OR a bind error showing `Value: "${VAR}"` | **Expected since 2026-07-30:** placeholders are bare by convention, so the variable is genuinely missing from the host `.env` / `setenv.sh`. | Add the variable — do NOT restore an inline default (`SchedulerConfigurationContractTest` fails the build on one). The profile's `.env.*.example` is the checklist; for SIT see `sit-bare-placeholder-deploy-checklist.md`. |
| Duplicate rows in `bank_exch_rate_infm` | Legacy FX still running alongside the refactor | Disable one side; ShedLock cannot help |
| `PROC_STAT` flapping / inconsistent | Legacy `PROCESSING_RESULT_CHECK_BATCH` + refactor `ResultCheckJob` both active | Disable `ResultCheckJob` immediately (cron → `-`, restart) |
| A `shedlock` row is stuck | Node crashed mid-pass | Wait for `lock_until` (`ResultCheckJob` 30 min, OPRT 60 min, others 5–10 min). **Do not delete the row.** |
| Job fires but does nothing, logs `skipped — Oracle feature disabled` | `haengsung.oracle.enabled=false` | Expected; open the second gate only when Oracle access is intended |
| `WBE_HS_REFRESH_IN_PROGRESS` from the Refresh button | Scheduled import holds the shared lock | Expected and correct; retry shortly |
