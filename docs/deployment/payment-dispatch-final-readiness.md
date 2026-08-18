# Payment Dispatch — Final Readiness

**Date:** 2026-07-28 · **System:** Refactor (`wecms-api`) · **Scope:** all 5 payment types × 3 banks × VND/USD/FCY × API/FILE
**Method:** code trace at `HEAD` vs legacy (`WECMS DEV/`, `WECMS_BATCH DEV/`) + read-only `SELECT`s against JEX DEV and the refactor DB. No writes.

> **Scope of this verdict.** **Legacy `ANT_DIV_CD` parity: PASS** — every cell of the decision matrix matches
> legacy. **Deployment readiness: GATED** — three categories are WARNING and none of them is an
> `ANT_DIV_CD` issue. This document is therefore a *conditional* release artifact: the parity work is
> complete and signed off below; the deployment checklists in §4 carry the remaining gates.

**Companions:** [../audits/payment-dispatch-operating-hours-flow.md](../audits/deployment/payment-dispatch-operating-hours-flow.md) (findings F-1…F-5, §13 parity closure) ·
[wecms-batch-infrastructure-readiness-audit.md](wecms-batch-infrastructure-readiness-audit.md) ·
[wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) ·
[wecms-batch-schedule-seed-review.md](wecms-batch-schedule-seed-review.md)

---

## 1. Verdict summary

| # | Category | Verdict |
|---|---|---|
| 1 | `ANT_DIV_CD` decision matrix — all types × banks × currencies × routes | **PASS** |
| 2 | OperatingHours gating | **PASS** |
| 3 | Batch schedule seed | **WARNING** |
| 4 | Recovery behaviour | **PASS** |
| 5 | Monitor behaviour | **PASS** |
| 6 | Scheduler flags | **WARNING** |
| 7 | Dispatch flags | **WARNING** |
| 8 | Environment variables | **WARNING** |
| 9 | Transaction boundaries | **PASS** |

**Overall: parity PASS · deployment GATED.** No category is FAIL — nothing is broken. The three WARNINGs are
(a) seed not applied to UAT/PROD, (b) pre-existing infrastructure blockers from the readiness audit, and
(c) the deployed flag state not being auditable from the repository.

---

## 2. Final decision matrix — legacy vs refactor

`ANT_DIV_CD` targets: `TB_PAY_TARGET` = `character(5)` (API route) · `TB_PAY_FILE_DATA` = `character(1)` (FILE route).
Both verified read-only via `information_schema.columns` on JEX DEV **and** the refactor DB.

### 2.1 API route — `TB_PAY_TARGET`

Reached by Vendor, Expense and Internal Transfer only (Payroll and Overseas are FILE-only).

| Payment type | Bank | Currency | Legacy | Refactor | Match |
|---|---|---|---|---|---|
| Vendor / Expense | any | VND, non-VCB | `INTER` if same-bank else detail value (blank→`INTER`) — `PymnHelper:2209-2211` | `resolveAntDivCd` (`Vendor:186` / `Expense:180`) | ✅ |
| Internal Transfer | any | VND non-VCB; Woori USD→VND | `INTER` if same-bank else detail value — `PymnHelper:2617-2619` | `resolveApiAntDivCd` (`:259`) | ✅ |

Width-safe: target is `character(5)`, and the gate normalises via the lenient `AntDivChannel.canonical`.

### 2.2 FILE route — `TB_PAY_FILE_DATA` (`character(1)`)

**Legacy currency-condition asymmetry — verified, reproduced deliberately:**

| Legacy path | Condition | Source |
|---|---|---|
| Payroll | `!TRNF_CCY_CD.equals("VND")` — **any non-VND** | `pymn_0005_001_c001_act.jsp:215` |
| Vendor / Expense | `TRNF_CCY_CD.equals("USD")` — **USD only** | `PymnHelper:2341` |
| Internal Transfer | `TRNF_CCY_CD.equals("USD")` — **USD only** | `PymnHelper:2778` |

All three conditions key on the **per-detail** `TRNF_CCY_CD`, never the batch parameter. The refactor mirrors
each verbatim (`Payroll:230` `!CCY_VND`, `Vendor:293` / `Expense:284` / `IT:359` `CCY_USD`).

| Payment type | Bank | Currency | Legacy `ANT_DIV_CD` | Refactor | Match |
|---|---|---|---|---|---|
| **Vendor / Expense** | Shinhan `970424` | USD | `'E'` (`PymnHelper:2342`) | `ANT_DIV_T_EBANK` (`:295`/`:286`) | ✅ |
| Vendor / Expense | Woori `970457` | USD | `'V'` (`:2344`) | `ANT_DIV_T_VCB` (`:297`/`:288`) | ✅ |
| Vendor / Expense | VCB `970436` | USD | `null` (`:2347`) | `null` (`:299`/`:290`) | ✅ |
| Vendor / Expense | VCB | **VND** | `null` — `copyFrom`→`fromValue("CITAD")` | `toStorageCodeStrict` → `null` (`:289`/`:280`) | ✅ **F-4** |
| Vendor / Expense | any other | non-USD FCY | `null` (no branch; `fromValue`→null) | `toStorageCodeStrict` → `null` | ✅ |
| **Payroll** | Shinhan | non-VND | `'E'` (`:217`) | `CODE_EBANK` (`:232`) | ✅ **F-1** |
| Payroll | Woori | non-VND | `'V'` (`:220`) | `CODE_VCB` (`:234`) | ✅ **F-1** |
| Payroll | VCB | non-VND | `null` (`:223`) | `null` (`:238`) | ✅ **F-1** |
| Payroll | VCB | VND | **blocked** `WBE_PAYROLL_VCB_VND_NOT_SUPPORTED` (`:179-182`) | same guard | ✅ |
| Payroll | any | VND | `parse(detail)` → `'C'` (`:226`) | `toStorageCode` → `'C'` (`:240`) | ✅ **F-1** |
| Payroll | Woori | VND same-bank | `null` (`:257-259`) | `null` (`:255`) | ✅ **F-1** |
| **Internal Transfer** | Shinhan | USD | `'E'` (`:2779`) | `CODE_EBANK` (`:361`) | ✅ **F-2** |
| Internal Transfer | Woori | USD→USD | `'V'` (`:2781`) | `CODE_VCB` (`:363`) | ✅ **F-2** |
| Internal Transfer | VCB | USD | `null` (`:2783`) | `null` (never assigned) | ✅ |
| Internal Transfer | Shinhan | USD→VND **same-bank** | `null` (`:2846-2848`) | `null` (`:385`) | ✅ **F-5** |
| Internal Transfer | Shinhan | USD→VND other-bank | `'E'` (`:2852-2854`) | `CODE_EBANK` (`:385`) | ✅ |
| Internal Transfer | VCB | VND | `null` (`fromValue`) | `toStorageCodeStrict`→`null` (`:398`) | ✅ **F-4** |
| Internal Transfer | any other | USD | `null` | `null` | ✅ |
| **Overseas** | any (VCB blocked) | any | `'S'` SWIFT (`pymn_0007:209`) | `ANT_DIV_CD_SWIFT` (`:237`, `:387`) | ✅ |
| Overseas | VCB | any | **blocked** `WBE_OVERSEAS_VCB_NOT_SUPPORTED` | same guard | ✅ |

**Verdict 1: PASS.** Every cell matches. No 5-char value can now reach a `character(1)` column: every write
is either a `CODE_*` constant, an explicit `null`, or a narrowing call.

> ⚠️ **Documented caveat (inherited from legacy, not a defect).** Because Payroll keys on *non-VND* while
> the other three key on *USD*, a **non-USD foreign currency** (e.g. EUR) yields a channel for Payroll but
> `null` for Vendor / Expense / Internal Transfer. Both behaviours match their own legacy path. Only USD and
> VND are in live use, so this is latent. If a third currency is ever onboarded, revisit as one PO item.

### 2.3 Converter contract

| Converter | Semantics | Consumers | Guard test |
|---|---|---|---|
| `AntDivChannel.toStorageCode` | ≡ legacy `AntDivCdT.parse` — names accepted, `"CITAD"`→`'C'` | **Payroll only** | `strict_and_lenient_divergeExactlyOnTheLongForm` |
| `AntDivChannel.toStorageCodeStrict` | ≡ legacy `AntDivCdT.fromValue` — 1-char only, `"CITAD"`→`null` | Vendor, Expense, Internal Transfer | same |
| `AntDivChannel.fromStorageCode` | `AntDivCdT` 1-char → channel name (bulk gate). **Legacy parity** — `FILE_DEPOSIT_TRANSFER_BATCH1:301-381` does the same expansion itself, calling `operTimeChk` with hard-coded 5-char names | `BulkReceptionService.presentChannels` | `fromStorageCode_isStrict` |
| `AntDivChannel.canonical` | `AntDivCd` value → channel name (realtime gate). **Deliberate divergence** — legacy `NAPAS:144` passes the value verbatim. Inert today; latent flip if an EBANK/SWIFT/VCB realtime DTL row is added. Open PO routing decision, see `batch-schedule-operating-hours-audit.md` §12b | `OperatingHoursService.evaluateRealtime` | `AntDivChannelTest` |

> **`ANT_DIV_CD` is three vocabularies, not one column with drift** (corrected 2026-08-03):
> `TB_PAY_TARGET` (`bpchar(5)`) holds `AntDivCd.getValue()` — `INTER`/`NAPAS`/`CITAD`/`E`/`S`/`V`, mixed-width
> **by design**, all 101 DEV rows conforming; `TB_PAY_FILE_DATA` (`bpchar(1)`) holds `AntDivCdT.getValue()`
> — `E`/`N`/`C`/`S`/`V`, the 1-byte wire slot at offset 62 of the D record; `TB_BATCH_SCHEDULE_DTL`
> (`bpchar(5)`) holds channel **names** — `EBANK`/`NAPAS`/`CITAD`/`SWIFT`/`VCB`. Each is correct in place.

---

## 3. Category evidence

### 3.1 OperatingHours gating — **PASS**

- `SKIP` semantics unchanged: `evaluateRealtime` `:119`, `evaluateBulk` `:176`, empty-channel `:184-185`,
  per-channel any-`N` `:200`, `exeDiv` mapping `:215`.
- Split `batch_id` intact — INFO `NAPAS_REAL_TIME_BATCH` for both gates; DTL `FILE_DEPOSIT_TRANSFER_BATCH1`
  for bulk. Legacy parity (`FILE_DEPOSIT_TRANSFER_BATCH1.java:272`, `NAPAS_REAL_TIME_BATCH.java:113`).
- Asymmetric missing-row semantics preserved: realtime DTL-missing → `NO_CRITERIA`; bulk DTL-missing → pass.
- Zone: `@Value("${wecms.dispatch.operating-hours.zone:Asia/Ho_Chi_Minh})` (`:86`) — in-code default,
  absent from all YAML. Legacy DB zone measured as `Asia/Seoul`; ICT retained to agree with
  `CutoffValidator.VIETNAM_ZONE`.
- Injected into exactly two collaborators, one call site each.

### 3.2 Batch schedule seed — **WARNING**

| Environment | INFO | DTL | Evidence |
|---|---|---|---|
| Refactor DEV | **3** | **12** | verified read-only; all `last_edtr='DBA_MANUAL'`, `reg_dttm=20260728180209` |
| SIT | reported seeded | reported seeded | **not verifiable from here** (no MCP) |
| **UAT** | **not applied** | **not applied** | ❌ blocker for UAT |
| **PROD** | **not applied** | **not applied** | ❌ blocker for PROD |

DEV values are byte-identical to JEX DEV for all 12 DTL rows; the `970436` INFO row is the documented
refactor-only addition (`ant_where_use_yn='N'`). **An empty `TB_BATCH_SCHEDULE_INFO` makes every dispatch
resolve to `NO_CRITERIA` and FAIL** — so UAT/PROD must be seeded before activation.

### 3.3 Recovery behaviour — **PASS**

- Double-gated: `wecms.dispatch.recovery.enabled:false` (`StrandedRecoveryService:66`) **and**
  `cron:-` (`StrandedRecoveryJob`).
- **Never executed:** `StrandedRecoveryJob.run` absent from the 8-row `shedlock` table.
- Untouched by the F-1…F-5 work and by the monitor work.

### 3.4 Monitor behaviour — **PASS**

- Double-gated: `stuck-monitor.enabled:false` + `stuck-monitor.cron:-`.
- **Never executed:** `StuckPayFileMonitorJob.run` absent from `shedlock`.
- Read-only: `@Transactional(readOnly = true)`; `neverMutatesPaymentStatus` asserts no `save`/`saveAll`.
- 21 tests green. Reuses the existing finder, `OpsAlertService`, ShedLock table — no duplicate scheduler
  infrastructure.
- Live standing data it will report once enabled: `TB_PAY_FILE_MST` `PROC_STAT='9'` = **2**;
  `TB_PAY_TARGET` `'9'` = **4** (both classify as `DISPATCH_FLAG_DISABLED` while `auto-receive=false`).

### 3.5 Scheduler flags — **WARNING**

Dispatch-family crons all default to the never-fire marker:

| Job | Cron default |
|---|---|
| `ResultCheckJob` | `${wecms.dispatch.result-check.cron:-}` |
| `StrandedRecoveryJob` | `${wecms.dispatch.recovery.cron:-}` |
| `StuckPayFileMonitorJob` | `${wecms.dispatch.stuck-monitor.cron:-}` |

Why WARNING rather than PASS — both items pre-existing, neither introduced here:

- **8 other scheduler locks are actively firing** on the shared `shedlock` table (Haengsung ×3,
  exchange-rate ×3, operation-status ×2), latest `2026-07-28 05:45 UTC`. They share
  `spring.task.scheduling.pool.size: 1`, so a long job delays others.
- **Two schedulers have no `@SchedulerLock`** — `NxisasSessionRegistry` (`fixedDelay = 60_000`) and
  `CutoffConfigProvider` (`fixedDelayString`). Node-local caches, benign single-instance (audit **H-8**);
  must be revisited before scaling out.

### 3.6 Dispatch flags — **WARNING**

Code defaults are provably safe — `auto-receive:false`, `realtime-dispatch:false`,
`recipient-inquiry:false`, `recovery.enabled:false`, `alert.enabled:false`, `stuck-monitor.enabled:false`.
Mock EFS/SFTP clients **throw** rather than no-op, so a misconfiguration cannot silently send.

But **audit blocker C-1 is unresolved**: `TB_FIRM_TRAN` holds **17** real EFS 201 rows
(`TRAN_TP='201'`, `PROC_STAT='3'`, `RS_CD='1000'`, `2026-07-22`–`2026-07-24`), which can only exist with
`recipient-inquiry=true` **and** `efs.enabled=true` on a deployed node — while every committed `.env.*` says
`false`. Corroborating: 202/203/00301/01302/alert legs are all still **0 rows**, so only the 201 leg ever ran.

No new rows have appeared in the four days since, which is *consistent with* remediation but is **not proof**.
The host environment is not auditable from this repository. **An `env | grep WECMS_` readout per host is
required before activation.**

### 3.7 Environment variables — **WARNING**

New properties added by this work are safe: `stuck-monitor.*` (5) and `operating-hours.zone` are `@Value`
with in-code defaults and **absent from every `application-*.yml`** (verified) — so an unset variable cannot
abort startup.

Pre-existing issues persist, none introduced here:

| ID | Issue |
|---|---|
| **H-1** | Existing dispatch vars have **no in-YAML default** (`${WECMS_EFS_ENABLED}`, not `:false`) — an unset variable aborts startup. Fail-safe but fail-loud |
| **H-4** | `.env.prod.example` is a UAT clone — PROD paths/URLs/ports unverified |
| **H-5** | `recovery.*`, `alert.env-mode`, `alert.was-no` absent from `.env.*.example` |
| **H-6** | Real legacy cert seed (`wabooksvn`) committed in all five templates |

### 3.8 Transaction boundaries — **PASS**

| Component | Boundary |
|---|---|
| All 5 executors | `@Transactional(propagation = MANDATORY)` — must join the caller's tx; master `TRNF_YN='Y'` stamp and dispatch inserts commit or roll back together |
| `RealtimeDispatchRegistrar`, `DispatchAutoReceiveRegistrar` | `afterCommit` via `registerSynchronization` (4 `afterCommit` / 2 `registerSynchronization`) — no network I/O inside a payment tx; a dispatch failure cannot roll back a committed payment |
| `StuckPayFileMonitorService` | `@Transactional(readOnly = true)` |
| `DispatchApiTranAuditWriter` | `REQUIRES_NEW` — audit survives an outer rollback |
| `DispatchResultTxService` | `REQUIRES_NEW` atomic finalize |
| Per-row failure isolation | registrars catch and log per item; never rethrow into the payment path |

---

## 4. Deployment checklists

Ordering is deliberate: SIT gates on parity + seed; UAT adds the coexistence decision; PROD adds the
security items. **Do not skip §4.0 in any environment.**

### 4.0 Common pre-flight (every environment)

1. Confirm the build: `./gradlew compileJava compileTestJava test` → expect **1999 tests, 0 failures**.
2. **Capture the flag state on every host** — closes C-1:
   ```
   env | grep -E 'WECMS_DISPATCH_|WECMS_EFS_|WECMS_SFTP_|WECMS_CALLBACK_'
   ```
   Expected before activation: `auto-receive`, `realtime-dispatch`, `recipient-inquiry`, `recovery.enabled`,
   `alert.enabled`, `efs.enabled`, `sftp.enabled` all `false`. **Paste the redacted output into the
   readiness audit §4.** If `recipient-inquiry`/`efs.enabled` are ON, decide explicitly whether to keep them.
3. Confirm `TB_FIRM_CONF` covers every withdrawing bank you intend to dispatch from, and that every one has a
   `TB_BATCH_SCHEDULE_INFO` row (pre-check 0.4 of the seed script).
4. Confirm the DB timezone. If it is not UTC+7, decide `wecms.dispatch.operating-hours.zone` explicitly
   (JEX DEV measured `Asia/Seoul`; refactor DEV `Asia/Phnom_Penh`).
5. Back up `TB_BATCH_SCHEDULE_INFO` / `_DTL` (`\copy … CSV HEADER`) before any seed run.

### 4.1 SIT

| # | Step | Gate |
|---|---|---|
| 1 | §4.0 complete | — |
| 2 | Run [`seed-batch-schedule-operating-hours-2026-07-28.sql`](../sql/2026/seed-batch-schedule-operating-hours-2026-07-28.sql) §0 pre-checks, then §1–3 | 15 rows `PRESENT` (§5.2) |
| 3 | Run §5.5 behavioural dry-run | all INFO rows `Y (open)` |
| 4 | **Enable the monitor alone** — `stuck-monitor.enabled=true`, `cron=0 */5 * * * ?` | read-only; safe **without** dispatch |
| 5 | Confirm the monitor reports the existing WAIT backlog as `DISPATCH_FLAG_DISABLED` | proves the detector works before it is needed |
| 6 | Fix **H-7** (result-check UTC/ICT day boundary) | **required before** `result-check.cron` |
| 7 | Add `spring-boot-starter-actuator` (**C-5**) | `/actuator/health` currently 404 |
| 8 | Resolve **C-3** with VAN Center — meaning of EFS 201 `RS_CD='1000'` | 17/17 failures |
| 9 | Set SIT `com.kosign.wecms: INFO` **or** extend redaction (**H-3**) | DEBUG logs full bodies |
| 10 | Enable `auto-receive` **inside** a CITAD window (Shinhan 09:00–15:30 / Woori 08:30–17:40 ICT) | outside it, files now FAIL loudly |
| 11 | Verify a Payroll VND file writes `ANT_DIV_CD='C'` and dispatches | F-1 end-to-end |
| 12 | Verify a VCB VND file writes `NULL`, is `SKIP`ped, and the monitor reports `ALL_CHANNELS_NULL` | F-4 Option A end-to-end |

### 4.2 UAT

| # | Step | Gate |
|---|---|---|
| 1 | §4.0 complete + SIT sign-off | — |
| 2 | **Seed `TB_BATCH_SCHEDULE_*`** — §0.1 first; if live-JEX rows exist, `DO NOTHING` preserves them | **blocker** |
| 3 | **Resolve dependency 26** — confirm the refactor UAT DB is *not* the JEX UAT batch DB | **blocker**; recovery must stay OFF until then |
| 4 | Confirm `TB_VAN_API_INFO.API_KEY` provisioned (DEV has 2 rows; UAT `MISSING`) | **blocker** |
| 5 | ~~Confirm `WECMS_EFS_PARTNER_ID` non-empty (empty in every template)~~ → **Verify `TB_FIRM_CONF.VAN_ORG_CD` + `BANK_PRTN_ID` per withdrawal account.** The variable was **retired 2026-07-29 (D-2)**: nothing reads it in source or build output, `SchedulerConfigurationContractTest` enforces its absence across all four profiles, the envelope `ID` is **unconditionally `TB_FIRM_CONF.VAN_ORG_CD`** (no fallback), and the vendor's `prtn_id` is a **separate per-account body field** from `BANK_PRTN_ID`. Query + "what correct means" in [activation runbook §5.0-C](wecms-batch-activation-runbook.md); record in [readiness audit §6.1 / D-2](wecms-batch-infrastructure-readiness-audit.md) | ~~blocker~~ → **check** — per account, not per environment (PK is `(biz_key, out_bank_cd, wdrl_act_no)`); ⚠ 3 JEX DEV rows carry placeholder `34534534`/`434534` |
| 6 | **R-03 / H-2 — callback endpoints are unauthenticated at the application layer; the IP allow-list is the mitigation.** Populate `WECMS_CALLBACK_ALLOWED_IPS` **before the first callback** and confirm the proxy strips client `X-Forwarded-For`. *(Merged 2026-08-01: the `permitAll` controllers and the allow-list were tracked as two entries. They are one item — the first is the vulnerability, the second is its only mitigation, and closing either alone leaves the environment either open or 403ing.)* | **blocker** — set it and every `01301`/`01302` is refused; leave it empty on a `permitAll` surface and `VV` HMAC is the only gate |
| 7 | Verify TLS trust for the VAN gateway CA (**H-9**) | private-CA would fail the handshake |
| 8 | Fix the duplicate `TB_FIRM_CONF` row (**H-10**) | two rows share `(VN_VNM0004_000001, GV00000001)` |
| 9 | Give every dispatch var an in-YAML default (**H-1**) | unset var currently aborts startup |
| 10 | Decide instance count; if > 1, revisit **H-8** and `pool.size: 1` | in-memory monitor dedup is per-JVM |
| 11 | Enable monitor → verify → then dispatch, in that order | never dispatch-first |

### 4.3 PROD

| # | Step | Gate |
|---|---|---|
| 1 | §4.0 complete + UAT sign-off | — |
| 2 | **Author a real `.env.prod.example`** (**H-4**) — PROD-confirmed paths, URLs, ports | **blocker** |
| 3 | **Seed `TB_BATCH_SCHEDULE_*`** with a before-image captured first | **blocker** |
| 4 | Rotate the historically-committed Coocon/OSMC keys | **blocker** |
| 5 | Move `WECMS_CRYPTO_LEGACY_CERT_SEED` out of committed templates (**H-6**) | legacy pgcrypto seed |
| 6 | Close **R-02** (action-level authorization) and **R-03/F-09** (callback hardening — *one item with the allow-list, see UAT step 6*) | pre-deploy security gates, **both Dev code work** |
| 7 | **Durable/multi-instance store for JWT revocation** (+ rate limiting) — **a security control that silently does not work on >1 node**, not a scale-out caveat. See §4.4 | **Dev code work, pre-UAT/PROD.** Fix revocation first |
| 8 | Confirm `TB_VAN_API_INFO`, ~~partner id~~ **`TB_FIRM_CONF.VAN_ORG_CD`/`BANK_PRTN_ID` per account** (D-2 — the partner-id variable is retired), SFTP key `0600`, staging dirs, gateway IP allow-listing | ~~all `MISSING`~~ — **rescoped 2026-08-01**: `TB_VAN_API_INFO` is **inherited** from the live JEX DB, SFTP values are **in the repo**; only the key file, staging dirs and gateway IPs remain genuinely outstanding. See [readiness audit §6.0–§6.1](wecms-batch-infrastructure-readiness-audit.md) |
| 9 | Stand up log rotation, disk + certificate-expiry monitoring, alert hooks (**M-5**) | none exist |
| 10 | Backup/PITR restore drill for the refactor DB | untested |
| 11 | Enable in order: monitor → `alert.enabled` → `auto-receive` → `realtime-dispatch` → `result-check.cron` | recovery stays OFF pending dep. 26 |
| 12 | Keep `recovery.enabled=false` until the coexistence decision is documented | **blocker** |

### 4.4 Pre-UAT/PROD **code** items — R-02 and the in-memory revocation store

> Recorded 2026-08-01. Both are **Dev code work gated to pre-UAT/PROD**, not verification and not Ops.
> The dispatch subsystem itself is code-complete; that does not extend to these two.

**R-02 — action-level authorization.** Per-action authority (Request-approval / Delete / Dispatch) is
unenforced on both sides. Closing it: a PO decision on the authority source → server-side `@PreAuthorize`
on every state-changing handler → replace the frontend's static `{R:true,D:true,T:true}` stub. Today only
menu-level entitlement exists (`@menuAuthz`, all-or-nothing). See
[`../../../docs/audits/action-level-authorization-audit.md`](../../../docs/audits/action-level-authorization-audit.md).

**The in-memory revocation store — a security control that silently does not work on more than one node.**
State it that way, not as a scale-out caveat:

- **The failure.** `TokenRevocationService`'s `userNotBefore` cutoff — written on admin **lock, delete,
  demote and password-reset** — lands in a `ConcurrentHashMap` on **one JVM**. On every other node the
  affected user keeps a **working, privileged** token until its natural `exp`, up to the 1-hour
  access-token TTL. That is precisely the scenario F-03 was built to prevent. Logout degrades the same
  way via the `jti` denylist. Nothing logs, nothing alerts — the other nodes simply never learn.
- **No environment is confirmed single-instance.** All 8 `shedlock` rows show one holder
  (`GroupwareDev`), so the *observed* deployment is single-node — but instance count is an open
  **`[?]` OPS** item on
  [`wecms-batch-infra-team-checklist.md:74-75`](wecms-batch-infra-team-checklist.md), with a companion
  `[?]` at `:136` on shared staging storage. "Probably one node" is not a control.
- **Why it is in memory today** — deliberate, not an oversight. A refactor-only table needs **PO +
  JEX-DDL approval** (the policy that removed the notification and OAT-idempotency tables), so the store
  was kept in-process rather than adding one unilaterally.
- **Two viable routes**, both in ``../security/jwt-revocation.md`` *(document was never written — see [`docs/security/README.md`](../security/README.md) for the security closure set)*: a
  **shared cache**, or a **PO-approved legacy-table-backed denylist**. Do not add a refactor-only table
  without that approval.
- **`RateLimiter` needs no redesign.** Its `tryAcquire(key, limit, windowSeconds)` contract is already
  shaped so only the backing map changes.

**If only one is fixed, fix revocation.** The two fail differently:

| | Degrades gracefully? | Why |
|---|---|---|
| **Rate limiting** | **Yes** | `LoginLockoutService` is durable — it writes `USER_INFM.LOCK_YN='Y'` after 5 consecutive failures, so brute force against a *known* account still locks out on any node. What degrades is the throttle on spraying and on credential reveal: N nodes permit N × the intended attempts per window. |
| **Revocation** | **No** | There is no durable second line. It fails **open** on every node that missed the write, and a demoted or deleted user stays privileged there for up to the token TTL. |

---

### 4.5 Rollback

| Situation | Action |
|---|---|
| Dispatch misbehaving | Set the dispatch flag(s) back to `false`. Registrars become no-ops; in-flight rows stay at their current `PROC_STAT`. |
| Windows wrong | Use the seed script §6 `DO UPDATE` variant to restore the before-image. **Do not delete INFO rows while dispatch is live** — that returns the gate to `NO_CRITERIA` and fails everything. |
| Need to stop dispatch without failing rows | Close the window (`start='235959'`, `end='000000'`) → permanent `SKIP` → rows stay WAIT rather than FAIL. |
| Monitor too noisy | `stuck-monitor.cron=-` or `enabled=false`. Read-only, so disabling loses only visibility. |
| Parity regression suspected | `AntDivChannelTest.strict_and_lenient_divergeExactlyOnTheLongForm` is the canary — if it fails, the two converters have been collapsed. |

---

## 5. Sign-off

| Item | Status |
|---|---|
| F-1 Payroll `ANT_DIV_CD` | ✅ FIXED, legacy-exact |
| F-2 Internal Transfer USD first pass | ✅ FIXED, legacy-exact |
| F-3 `TransferDispatchExecutor` dead code | ✅ DELETED |
| F-4 `character(1)` width violation | ✅ FIXED (Option A), legacy-exact |
| F-5 Shinhan USD→VND same-bank | ✅ FIXED, legacy-exact |
| Q-4 observability (stuck-WAIT monitor) | ✅ DONE, OFF by default |
| Q-4 behavioural (SKIP vs FAIL) | 🔶 OPEN — product decision |
| Q-5 (F-4 Option B) | 🔶 OPEN — ratification only |
| `./gradlew test` | ✅ **1999 tests, 0 failures, 0 errors**, 255 suites |

**Parity: PASS — cleared for SIT** once §4.1 completes. **UAT and PROD remain gated** on the blockers in
§4.2 / §4.3, none of which is `ANT_DIV_CD`-related.
