# Bulk-Execution (`00302`) — Legacy Evidence & Manual Recovery Runbook

> # ⛔ THE RETRY SWEEP DESCRIBED HERE WAS REMOVED ON 2026-07-29
>
> `BulkExecutionRetryJob` / `BulkExecutionRetryService` / `BulkExecutionRetryTxService` **no longer
> exist**, together with their ShedLock identity, 5 YAML keys × 4 profiles and 5 environment variables.
> They were K9 **Option A** ("Recovery Scheduler (reconciliation)"), which was formally offered to
> Mr. Kang and **not selected** — he directed inline `00302` execution from the `01301` callback.
> **There is no automatic retry, by design, and none is required for legacy parity.**
>
> **This document is retained for the parts that are still true and useful:**
>
> | Section | Status |
> |---|---|
> | §1 legacy BATCH2 behaviour, line-by-line | ✅ **valid** — the source-of-truth record |
> | §4 retry-code mapping (incl. the `BT00` parity fix) | ✅ **valid** — still classifies alerts on the inline path |
> | §5-§7 idempotency / transactions / status-transition parity | ✅ **valid** for the inline path |
> | §8 monitoring | ✅ **valid** — this *is* the approved residual model |
> | §11 **manual recovery procedure** | ✅ **valid — the operational answer** |
> | §3 files changed · §9 configuration · §10 activation · §12 rollback · §13a alert de-duplication | ❌ **obsolete** — describe deleted code |
>
> Causes were renamed: `BULK_EXECUTION_INSTRUCTION_PENDING` → **`BULK_EXECUTION_NOT_ISSUED`** (no `00302`
> audit row — safe to re-drive) and `BULK_EXECUTION_RETRY_EXHAUSTED` → **`BULK_EXECUTION_UNCONFIRMED`**
> (a row exists — **reconcile with the VAN Center first**). The two need opposite manual actions.
>
> Full record: [final-four-to-one-batch-architecture-correction.md](../../audits/batch/final-four-to-one-batch-architecture-correction.md)

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`)
**Status:** the retry sweep is **deleted**; this file survives as the legacy-evidence + manual-recovery record
**Legacy source of truth:** `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH2.java`
**Why it was deleted:** it was K9 **Option A**, offered to Mr. Kang and not selected —
[bulk-retry-reassessment.md](../../architecture/bulk-retry-reassessment.md)

---

## 1. Legacy behaviour (verified from source)

`FILE_DEPOSIT_TRANSFER_BATCH2` ran on cron `*/1 * * * *`
(`web/WEB-INF/jex/simpleBatch/WECMS_BATCH/FILE_DEPOSIT_TRANSFER_BATCH2.batch`).

| Step | Legacy line | Behaviour |
|---|---|---|
| Selection | `:48-54` | `TB_FIRM_FILE_MST_R001` → `WHERE TRAN_DT=:TRAN_DT AND PROC_STEP='1' AND PROC_STAT='6'`. No age filter, no `ORDER BY`, no `LIMIT`, no tenant predicate. |
| Target-failure gate | `:70-104` | `TB_PAY_FILE_MST_R004` counts parent rows at `PROC_STAT='3'`. `CNT>0` ⇒ set `PROC_STEP='2'/PROC_STAT='3'`, `REG_RSLT_CD='G998'`, `continue`. |
| Sibling gate | `:108-125` | `TB_FIRM_FILE_MST_R003` counts siblings at `PROC_STEP='0' AND PROC_STAT='6'`. `CNT>0` ⇒ `continue`, **no state change**. |
| Sequence | `:160` | `String seq = Sequence.getSeq()` — **a fresh `TRAN_SEQ` per attempt**. |
| Audit (insert) | `:~165` | `TB_FIRM_BAT_API_TRAN_C001`, `API_TRAN_TP='00302'`, written **before** the POST. |
| POST | `:200` | `ConnectionUtil.connectUrlByPost(...)`. |
| **No body** | **`:202-206`** | **`continue` — audit row never updated, so `RS_CD` stays `NULL`. Row keeps `PROC_STEP='1'` ⇒ re-picked next minute.** |
| Audit (update) | `:210-229` | `TB_FIRM_BAT_API_TRAN_U001` with `RS_CD`/`RS_MSG`/`RS_DTTM`. |
| **Transient** | **`:232-234`** | **`if("BT01".equals(RC) \|\| "G999".equals(RC) \|\| "C999".equals(RC)) { continue; }` ⇒ re-picked next minute.** |
| Success | `:242-263` | `if("0000".equals(RC) \|\| "BT00".equals(RC))` ⇒ `PROC_STEP='2'`. |
| Permanent | `:264-305` | Ops alert `이체실행지시 Batch2 API수신 오류 발생` + `PROC_STEP='2'/PROC_STAT='3'` + `TB_PAY_FILE_MST.PROC_STAT='3'`. |

**The retry was never an explicit feature.** It emerged from the polling loop: a `continue` left the
row in the selection state, and 60 seconds later the same query found it again. Legacy retried
**forever**, with no attempt ceiling.

---

## 2. What was BELIEVED to be a gap — and why that was wrong

Before this change, `00302` was issued **only** from the inline path
(`BulkReceptionResultService.issueExecutionInline`). On gateway failure it caught, logged, and left
the file at `PROC_STEP='1'` with the comment *"no automatic re-issue"* — and **nothing re-drove it**:

| Component | Scanned | Covered `PROC_STEP='1'/PROC_STAT='6'`? |
|---|---|---|
| `StuckPayFileMonitorService` | `TB_PAY_FILE_MST` at `PROC_STAT='9'` | ✗ — javadoc explicitly excluded `'6'` as "in-flight" |
| `StrandedRecoveryService` | pay-target + pay-file at `'9'`; firm-**tran** `'6'` → result-check | ✗ — the `'6'` path is `TB_FIRM_TRAN` (real-time leg), not the bulk file leg |
| `ResultCheckService` | `TB_FIRM_TRAN` by `findByTranTpAndProcStat(202,'6')` | ✗ — real-time API path |
| `TbFirmFileMstRepository.findByTranDtAndProcStepAndProcStat` | *the exact legacy predicate* | **declared, zero callers** |

> **⛔ This section's conclusion is WRONG and is kept only as the record of the error.**
> The table above is factually accurate — nothing did re-drive that state. But calling it a *parity gap*
> was the mistake: Mr. Kang was shown this exact failure mode in K9, including *"funds may never move"*,
> was offered a recovery scheduler as Option A, and chose inline execution with monitoring + manual
> recovery instead. It is an **approved architecture difference**, not a defect.
>
> The one thing this section got right and that was worth fixing: nothing *observed* the state. That is
> now covered by the read-only monitor (§8), which is what K9 actually prescribed.

---

## 3. What was implemented — SINCE DELETED

```text
BulkExecutionRetryJob            @Scheduled + @SchedulerLock, cron "-" by default
  └─ BulkExecutionRetryService   sweep: gate → bounded select → per-file loop → alert (never throws)
       └─ BulkExecutionRetryTxService   @Transactional(REQUIRES_NEW) per file
            ├─ 1. state re-check      PROC_STEP='1'/PROC_STAT='6' reloaded by PK
            ├─ 2. target-fail gate    TB_PAY_FILE_MST PROC_STAT='3' ⇒ G998        (legacy :83-104)
            ├─ 3. sibling gate        any sibling PROC_STEP='0'    ⇒ defer        (legacy :108-125)
            ├─ 4. retry eligibility   last 00302 RS_CD retryable? attempts < max?
            └─ 5. issue               audit row → EFS 302 → status update         (legacy :160-305)
```

### Files changed

| File | Change |
|---|---|
| `dispatch/service/BulkExecutionRetryService.java` | **new** — bounded sweep |
| `dispatch/service/BulkExecutionRetryTxService.java` | **new** — per-file `REQUIRES_NEW` body |
| `dispatch/service/BulkExecutionRetryOutcome.java` | **new** — 10 per-file outcomes |
| `dispatch/service/BulkExecutionRetryReport.java` | **new** — sweep result record |
| `dispatch/scheduler/BulkExecutionRetryJob.java` | **new** — `@Scheduled` driver |
| `common/lock/JobLockNames.java` | + `DISPATCH_BULK_EXECUTION_RETRY` |
| `dispatch/constant/FirmBankingResultCd.java` | + `BT00`/`BT01`/`C999` + `isBulkExecutionSuccess` + `isBulkExecutionRetryable` |
| `dispatch/repository/TbFirmFileMstRepository.java` | + `findBulkExecutionRetryCandidates` (bounded) |
| `dispatch/repository/TbFirmBatApiTranRepository.java` | + `findBy…ApiTranTpOrderByTranSeqAsc` (attempt history) |
| `dispatch/callback/service/BulkReceptionResultService.java` | `BT00` success parity + restored ops alert |
| `dispatch/monitor/StuckPayFileCause.java` | + 2 causes |
| `dispatch/monitor/StuckPayFileMonitorService.java` | + second (firm-file) sweep |
| `dispatch/monitor/StuckPayFileReport.java` | + `firmFileStuckCount` |
| `application-{local,sit,uat,prod}.yml` | + `bulk-execution-retry.*`, + `stuck-monitor.firm-file-batch-size` |
| `batch-dispatch-variable-reference.md` | + section 2b |

**No DDL. No new table, no new column, no frontend change, no bank payload change, no fixed-length
format change, no `01301`/`01302` contract change, no operating-hours change.**

---

## 4. Retry-code mapping

Derived verbatim from legacy; pinned by `FirmBankingResultCdBulkExecutionTest`.

| `RS_CD` | Class | Action | Legacy line |
|---|---|---|---|
| `0000` | success | `PROC_STEP='2'` | `:242` |
| `BT00` | success | `PROC_STEP='2'` | `:242` |
| `null` / blank | **retryable** | leave `PROC_STEP='1'` | `:202-206` (`continue` before the audit update ⇒ `RS_CD` stays `NULL`) |
| `BT01` | **retryable** | leave `PROC_STEP='1'` | `:232-234` |
| `G999` | **retryable** | leave `PROC_STEP='1'` | `:232-234` |
| `C999` | **retryable** | leave `PROC_STEP='1'` | `:232-234` |
| `G001` `G002` `G003` `G004` `G998` `9999` `0001` … | permanent | `PROC_STAT='3'` both tables + alert | `:264-305` (`else`) |

> **`BT00` was a live parity bug.** `issueExecution` tested `FirmBankingResultCd.isSuccess(rc)`, which
> accepts only `"0000"`, so a legacy-success `BT00` was thrown as a gateway error. Fixed with a
> **302-leg-scoped** `isBulkExecutionSuccess`; the generic `isSuccess` is unchanged so the
> 201/202/203/`00301` legs keep their `0000`-only contract.

---

## 5. Idempotency

The inline path guards with `existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp` — *"a `00302` row
exists ⇒ don't issue"*. Correct there (a duplicate `01301` callback must not double-issue), and
**deliberately not reused** by the retry: the first failed attempt already wrote a row, so that probe
would block every retry permanently. Legacy had no such guard — it allocated a fresh `TRAN_SEQ`
per attempt (`:160`) and relied purely on `PROC_STEP`.

Three distinct states are distinguished, which the boolean probe cannot do:

| State | Evidence | Retry? |
|---|---|---|
| completed `00302` | file is at `PROC_STEP='2'` | **no** — state guard rejects it |
| failed / no-response attempt | latest audit `RS_CD` ∈ retryable set (or `NULL`) | **yes** |
| permanent-failure attempt | latest audit `RS_CD` permanent | **no** — `NOT_RETRYABLE` |

Duplicate-execution safety therefore rests on four independent layers:

1. **Distributed lock** — `@SchedulerLock(DISPATCH_BULK_EXECUTION_RETRY)`; no two nodes sweep at once.
2. **State re-check inside the transaction** — the file is reloaded **by PK** and must still be
   `PROC_STEP='1'/PROC_STAT='6'`. The selection is a snapshot; a callback or another sweep may have
   advanced it. Covered by tests 9 & 11.
3. **Row lock** — the subsequent `save` in the same `REQUIRES_NEW` transaction.
4. **Terminal state** — a confirmed `00302` sets `PROC_STEP='2'`, permanently leaving the predicate.

**Attempt count is derived, not stored** (workspace rule §1 — no refactor-only column):
`COUNT(TB_FIRM_BAT_API_TRAN WHERE API_TRAN_TP='00302')` for the file. Legacy wrote exactly one row
per attempt, so the row count *is* the attempt count.

---

## 6. Transactions & locking

| Concern | Design |
|---|---|
| Sweep | **Non-transactional.** No DB transaction is held across N sequential EFS round-trips. |
| Per file | `@Transactional(propagation = REQUIRES_NEW)` in `BulkExecutionRetryTxService`, mirroring `DispatchResultTxService` and `BulkReceptionService`'s per-split-file unit. |
| Isolation | A throw from one file is caught by the sweep, counted as `ERROR`, alerted, and the loop continues. One bad file never rolls back or blocks another. |
| Lock identity | `BulkExecutionRetryJob.run` — **distinct** from `StrandedRecoveryJob.run`. Different states, different gateway leg, independently enableable; sharing a name would make one silently exclude the other. |
| `lockAtMostFor` | `PT30M` — worst case ≈ `read-timeout 20 s × batch-size 50` ≈ 17 min, plus headroom. |
| Scheduler thread | `spring.task.scheduling.pool.size: 1` is shared; `retryOnce()` therefore **never throws**. |

---

## 7. Status transitions — legacy vs refactor

| Scenario | Legacy | Refactor | Match |
|---|---|---|---|
| `00302` confirmed (`0000`/`BT00`) | `PROC_STEP='2'`, `PROC_STAT` untouched | same | ✅ |
| No response body | `PROC_STEP='1'` unchanged, `RS_CD` `NULL`, retried next cycle | `PROC_STEP='1'` unchanged, audit `RS_CD` recorded, retried next sweep | ✅ behaviour (audit is *more* complete) |
| `BT01`/`G999`/`C999` | `PROC_STEP='1'` unchanged, retried next cycle | same | ✅ |
| Permanent rc | `PROC_STEP='2'/PROC_STAT='3'` + `TB_PAY_FILE_MST='3'` + alert | same | ✅ |
| Target already failed | `PROC_STEP='2'/PROC_STAT='3'`, `REG_RSLT_CD='G998'` | same | ✅ |
| Sibling pending | `continue`, no state change | same | ✅ |
| **Attempts exhausted** | *n/a — retried forever* | **alert, leave at `PROC_STEP='1'`** | ⚠️ **approved divergence** |

> **Why exhaustion does not mark FAIL.** `PROC_STAT='3'` means *the bank rejected this batch*. An
> unreachable gateway is not a rejection. Marking it failed would put a false verdict on the payment
> ledger and could mask a transfer the bank actually executed. The file is left in its true state and
> an operator is alerted.

---

## 8. Monitoring (works with retry disabled)

`StuckPayFileMonitorService` gained a **second read-only sweep** over the same predicate, with two
new causes:

| Cause | Meaning |
|---|---|
| `BULK_EXECUTION_INSTRUCTION_PENDING` | stranded, attempts < ceiling — automatic retry may still fix it |
| `BULK_EXECUTION_RETRY_EXHAUSTED` | stranded, attempts ≥ ceiling — automatic recovery gave up |

This is the safe first step: **monitoring can be enabled without opening any mutating gate.** It
mutates nothing, reuses the same bounded finder, and falls back to the file's own `VAN_ORG_CD` when
no `TB_FIRM_CONF` row resolves one. `StuckPayFileReport.firmFileStuckCount()` is reported separately
from `stuckCount()` because a WAIT pay-file has moved no money whereas these files are at the bank.

---

## 9. Configuration — OBSOLETE (all five properties deleted 2026-07-29)

> The block below no longer exists in any profile. Kept only as the record of what was removed.
>
> `WECMS_DISPATCH_STUCK_MONITOR_FIRM_FILE_BATCH_SIZE` (the monitor cap) **is** still live — see
> [wecms-batch-environment-variables.md §2a](../../deployment/wecms-batch-environment-variables.md).

```yaml
wecms:
  dispatch:
    bulk-execution-retry:
      enabled: ${WECMS_DISPATCH_BULK_EXECUTION_RETRY_ENABLED:false}
      cron: "${WECMS_DISPATCH_BULK_EXECUTION_RETRY_CRON:-}"
      grace-seconds: ${WECMS_DISPATCH_BULK_EXECUTION_RETRY_GRACE_SECONDS:120}
      max-attempts: ${WECMS_DISPATCH_BULK_EXECUTION_RETRY_MAX_ATTEMPTS:3}
      batch-size: ${WECMS_DISPATCH_BULK_EXECUTION_RETRY_BATCH_SIZE:50}
    stuck-monitor:
      firm-file-batch-size: ${WECMS_DISPATCH_STUCK_MONITOR_FIRM_FILE_BATCH_SIZE:200}
```

Present in **all four** profiles (`local`/`sit`/`uat`/`prod`). Every key has an inline default, so an
unset host variable cannot abort startup (readiness finding H-1). Verified tab-free.

| Property | Default | Notes |
|---|---|---|
| `enabled` | `false` | master gate; the sweep is a no-op without it |
| `cron` | `-` | Spring's never-fire marker; **both** gates must be opened |
| `grace-seconds` | `120` | must exceed the EFS read timeout (20 s) so a mid-flight callback is never raced |
| `max-attempts` | `3` | counted from `TB_FIRM_BAT_API_TRAN`; legacy was unbounded |
| `batch-size` | `50` | bounds the query *and* the gateway call rate; a capped sweep logs a WARN |

---

## 10. Activation procedure — OBSOLETE (nothing to activate)

> There is no retry job to enable. **Step 1 below is the only part that still applies** — enabling the
> read-only monitor, which is the approved residual-recovery signal in its own right, not a precursor to
> a retry sweep. Steps 2-5 describe deleted configuration.

> **CORRECTED 2026-07-29 — the earlier blanket "requires Mr. Kang for coexistence" wording was wrong
> on three counts.** See [legacy-batch2-ownership-verification.md](../../audits/batch/legacy-batch2-ownership-verification.md).
>
> **Architecture: already approved — do not re-ask.** Mr. Kang approved the 4→1 reduction *including
> Batch 2's replacement*: 2026-06-04 re-scope, 2026-06-06 written clarification
> (*"배치2 → API1으로 갈음"*, ending *"위와 같은 로직으로 배치 업무 제거"*), recorded **"CLOSED — Approved"**.
>
> **Runtime coexistence: resolved per environment, and it is an Ops item, not an approval.**
>
> | Env | Legacy BATCH2 | Shares the refactor DB? | Verdict |
> |---|---|---|---|
> | DEV | running | **No** (`wecmsdevdb` ≠ `wecmsvndevdb`, different host) | **safe** |
> | SIT | not deployed (no `WECMS_BATCH_SIT` container) | n/a | **safe** |
> | UAT | enabled in config | **unknown** — UAT DB URL not committed | **Ops must confirm the DB target** |
> | PROD | enabled in config | refactor not deployed | **N/A** |
>
> **⛔ THIS JOB IS NOT REQUIRED FOR LEGACY PARITY. [CONFIRM: Mr. Kang — reopen K9]**
>
> Mr. Kang has **already approved** replacing Batch 2 with inline execution via the `01301` callback.
> `issueExecutionInline` reproduces BATCH2's business responsibility in full; the retry was an emergent
> side-effect of polling, not a distinct legacy responsibility. **Automatic retry is therefore an
> architecture enhancement beyond the approved TO-BE**, and requires an explicit K9 decision before it
> may be enabled in any environment.
> Full ruling: [bulk-retry-reassessment.md](../../architecture/bulk-retry-reassessment.md).
>
> K9 formally offered Mr. Kang four recovery strategies. **Option A was
> *"Recovery Scheduler (reconciliation) — periodically re-process aged `STEP=1` rows — re-introduces
> one bulk scheduled job."*** He did not choose it; the recorded decision is the inline model with
> ***"no periodic recovery sweep … monitoring + manual re-trigger"*** (`questions-for-mr-kang.md:21-39`,
> closed 2026-06-19).
>
> **This job is Option A.** It must not be enabled in *any* environment — including DEV and SIT, where
> coexistence is provably safe — until Mr. Kang reopens K9. The `StuckPayFileMonitorService` half is
> *already* compliant with K9 (monitoring + alerting) and needs no further approval.
>
> On a shared DB the duplicate risk is real and verified: neither side locks the row at selection,
> legacy POSTs `00302` *before* opening its transaction (`:200` vs `:236`), and both allocate a fresh
> `TNO`, so the VAN has no key to collapse the two.

**Step 1 — observe first (safe, no mutation).** Enable only the monitor:

```bash
WECMS_DISPATCH_STUCK_MONITOR_ENABLED=true
WECMS_DISPATCH_STUCK_MONITOR_CRON=0 */5 * * * ?
```

Watch for `feature=stuck-pay-file-monitor SUMMARY … firm_file_stuck_total=…`. If it stays `0` for a
full business day, there is nothing to recover and step 2 is not urgent.

**Step 2 — confirm the preconditions.**

- [ ] Mr. Kang has confirmed legacy `FILE_DEPOSIT_TRANSFER_BATCH2` is **decommissioned** in this environment, **or** the refactor DB is verified separate from the legacy batch DB.
- [ ] `WECMS_EFS_ENABLED=true` and the gateway is reachable (otherwise every retry fails on the mock client, which throws).
- [ ] `TB_VAN_API_INFO.API_KEY` provisioned for the tenant's `VAN_ORG_CD`.
- [ ] `shedlock` table present.
- [ ] Baseline captured: `SELECT COUNT(*) FROM tb_firm_file_mst WHERE tran_dt = to_char(now(),'YYYYMMDD') AND proc_step='1' AND proc_stat='6';`

**Step 3 — enable, conservatively.**

```bash
WECMS_DISPATCH_BULK_EXECUTION_RETRY_ENABLED=true
WECMS_DISPATCH_BULK_EXECUTION_RETRY_CRON=0 */2 * * * ?
WECMS_DISPATCH_BULK_EXECUTION_RETRY_GRACE_SECONDS=300   # wider than default for the first run
WECMS_DISPATCH_BULK_EXECUTION_RETRY_BATCH_SIZE=5        # small first blast radius
```

**Step 4 — verify.**

```bash
grep "feature=bulk-execution-retry SUMMARY" application.log
# candidates=N truncated=false … by_outcome={REISSUED=x, RETRY_PENDING=y, …}
```

```sql
-- one audit row per attempt, newest last
SELECT tran_seq, rs_cd, rs_msg, rs_dttm FROM tb_firm_bat_api_tran
 WHERE tran_dt = :d AND biz_key = :b AND firm_file_nm = :f AND api_tran_tp = '00302'
 ORDER BY tran_seq;

-- the file should have advanced
SELECT proc_step, proc_stat, proc_dttm FROM tb_firm_file_mst
 WHERE tran_dt = :d AND biz_key = :b AND firm_file_nm = :f;
```

Expected: `REISSUED` ⇒ `proc_step='2'`. `RETRY_PENDING` ⇒ still `'1'`, one more audit row, retried
next sweep. `ATTEMPTS_EXHAUSTED` ⇒ still `'1'`, alert raised, **operator required**.

**Step 5 — widen** `batch-size` to 50 and `grace-seconds` to 120 once a full day is clean.

---

## 11. Manual recovery (retry disabled, or attempts exhausted)

1. **Identify** — the monitor's `BULK_EXECUTION_RETRY_EXHAUSTED` alert, or:
   ```sql
   SELECT tran_dt, biz_key, file_seq_no, firm_file_nm, out_bank_cd, req_cnt, req_amt, proc_dttm
     FROM tb_firm_file_mst
    WHERE proc_step = '1' AND proc_stat = '6'
    ORDER BY proc_dttm;
   ```
2. **Read the attempt history** (the query in step 4 above). The last `rs_cd` tells you which case
   you are in — retryable, permanent, or never-attempted (`NULL`/no rows).
3. **Confirm with the bank / VAN Center whether the batch was executed.** This is the decisive step
   and cannot be inferred from WeCMS data alone: the file is already at the bank.
4. **Then, and only then:**
   - **Bank did NOT execute** → clear the attempt ceiling by temporarily raising
     `WECMS_DISPATCH_BULK_EXECUTION_RETRY_MAX_ATTEMPTS`, let one sweep run, restore the value.
     Preferred over hand-written SQL — it goes through every gate and writes a full audit trail.
   - **Bank DID execute** → the `01302` result callback should still arrive and finalize normally.
     Do not re-issue. If the callback is genuinely lost, escalate to the batch owner; a DBA status
     correction needs PO sign-off and a manual SQL script under `Claude Code (AI)/sql/`.
5. **Never** hand-edit `PROC_STEP`/`PROC_STAT` to "unstick" a file without step 3.

---

## 12. Rollback — OBSOLETE

Fully reversible by configuration — no schema change to undo.

| Level | Action | Effect |
|---|---|---|
| Immediate | `WECMS_DISPATCH_BULK_EXECUTION_RETRY_ENABLED=false` | sweep inert on the next fire; in-flight sweep finishes its current file |
| Belt-and-braces | also `…_CRON=-` | job never fires |
| Monitoring only | keep `WECMS_DISPATCH_STUCK_MONITOR_*` on | still observe the state, mutate nothing |
| Code | revert the commit | no DDL, no data migration; files already advanced to `PROC_STEP='2'` stay correct — they were legitimately executed |

A rollback **cannot** un-send a `00302` already confirmed by the bank. That is why activation starts
with `batch-size=5`.

---

## 13. Tests

`./gradlew cleanTest test` → **269 suites, 2171 tests, 0 failures, 0 errors, 20 skipped**
(all 20 skipped are the pre-existing `VasAccountIntegrationTest`, untouched by this change).

| # | Requirement | Test |
|---|---|---|
| 1 | eligible file retried | `BulkExecutionRetryTxServiceTest.eligibleFileIsRetriedAndAdvances` |
| 2 | younger than grace skipped | `BulkExecutionRetryServiceTest.graceWindowIsAppliedAsCutoff` / `graceIsConfigurable` |
| 3 | success → correct next step | `eligibleFileIsRetriedAndAdvances`, `bt00CountsAsSuccessCode` |
| 4 | transient stays retryable | `transientFailureRemainsRetryable`, `noResponseIsRetryable` |
| 5 | permanent not retried | `permanentFailureIsNotRetried`, `permanentPriorAttemptIsRefusedWithoutCallingGateway` |
| 6 | attempt limit enforced | `attemptCeilingIsEnforced`, `belowCeilingStillRetries` |
| 7 | sibling gate blocks | `siblingPendingBlocksExecution` |
| 8 | target-failure `G998` | `targetFailureProducesG998` |
| 9 | successful `00302` prevents duplicate | `alreadyExecutedFileIsSkipped` |
| 10 | failed audit does not block retry | `failedAuditAttemptDoesNotBlockRetry`, `eachAttemptAllocatesFreshSeqAndAuditsFirst` |
| 11 | concurrency cannot duplicate | `alreadyExecutedFileIsSkipped`, `vanishedFileIsSkipped` |
| 12 | disabled config prevents execution | `BulkExecutionRetryServiceTest.disabledByDefaultDoesNothing` |
| 13 | monitor detects stranded files | `StuckFirmFileMonitorTest` (9 tests) |
| 14 | ops alert on final failure | `permanentFailureAlerts`, `attemptsExhaustedAlerts`, `transientOutcomeDoesNotAlert`, `unexpectedExceptionAlerts` |
| 15 | `01301` flow still works | `BulkReceptionResultServiceTest` — 12/12 pass |
| 16 | `01302` flow still works | `BulkExecutionResultServiceTest2` — 4/4 pass |
| — | retry-code mapping | `FirmBankingResultCdBulkExecutionTest` (30 tests) |

New: 65 tests across 4 classes.

---

## 13a. Alert behaviour — PARTLY OBSOLETE (the de-duplication described here was retry-only)

The first pass of this feature restored the legacy alert but introduced a noise defect. Re-verifying
G-2 against legacy source found and fixed it.

**Legacy has exactly one alert call** — `FILE_DEPOSIT_TRANSFER_BATCH2.java:280`, inside the `:264`
`else` (permanent-failure) branch. Everything else `continue`s before reaching it:

| Response | Legacy line | Alert? | Refactor |
|---|---|---|---|
| `0000` / `BT00` | `:242-263` | no | no (`REISSUED`) |
| empty body | `:202-206` `continue` | no | no (`RETRY_PENDING`) |
| `BT01` / `G999` / `C999` | `:232-234` `continue` | no | no (`RETRY_PENDING`) |
| `G998` target-fail gate | `:83-104` `continue` | no | no (`TARGET_FAILED`) |
| sibling pending | `:121-125` `continue` | no | no (`SIBLING_PENDING`) |
| permanent rc | `:264-280` | **yes, once** | **yes, once** (`FAILED_PERMANENT`) |

**The defect.** Legacy could not repeat its alert because the alerting branch *also* writes
`PROC_STEP='2'/PROC_STAT='3'`, removing the row from the next poll. Three refactor outcomes
deliberately write **no** status — `ATTEMPTS_EXHAUSTED`, `NOT_RETRYABLE`, `UNRECOVERABLE_STATE` — plus
`ERROR`, so the file stayed selectable and alerted on **every sweep**.

**The fix.** Per-`(file, outcome)` de-duplication:

* those four outcomes alert **once**;
* a **changed** outcome for the same file alerts again (e.g. `NOT_RETRYABLE` → `ATTEMPTS_EXHAUSTED`);
* the key is **pruned** when the file leaves the candidate set, so a genuine recurrence is not muted;
* an **undeliverable** alert (no resolvable `VAN_ORG_CD`) does not consume the slot;
* `FAILED_PERMANENT` is **not** de-duplicated — it writes a terminal status in the same unit, exactly
  as legacy did, so it is naturally once-only and suppressing it could hide a real second event.

**Ordering note.** Legacy alerts *before* its DB writes (`:280` vs `:283-291`); the refactor alerts
*after*, so a failed write cannot produce an alert about a state that was never persisted. For an
async fire-and-forget notification the ordering carries no functional consequence.

**Limitation.** The de-dup memory is in-process, like `StuckPayFileMonitorService`. Across a restart
or a second node the worst case is a duplicate alert, never a missed one.

Tests: `BulkExecutionAlertParityTest` (15).

## 14. Open items

| # | Item | Owner |
|---|---|---|
| 1 | **Reopen K9.** This job is the Option-A periodic sweep that was offered and declined in favour of *"no periodic sweep; monitoring + manual"*. Blocks enabling in **every** environment, including the DB-separated ones. → [ownership verification](../../audits/batch/legacy-batch2-ownership-verification.md) | **Mr. Kang** |
| 1b | Confirm whether the refactor UAT DB is the JEX UAT batch DB (UAT only; DEV/SIT already proven separate) | **Ops / Infra** |
| 2 | Confirm `max-attempts=3` / `grace-seconds=120` suit real VAN latency | batch owner |
| 3 | Cross-midnight files fall out of the `TRAN_DT=today` scope — same as legacy, surfaced by the monitor, recovered manually. Widening the scope is a deliberate divergence and needs approval. | PO |
| 4 | `01302`-lost case (bank executed, callback never arrived) has no automated path — step 11.4 | batch owner |
