# K9 Re-assessment — `BulkExecutionRetryJob` vs the Approved TO-BE

> # ⚑ ACTED ON 2026-07-29 — the job was REMOVED
>
> This document analysed `BulkExecutionRetryJob` and concluded it was K9 **Option A**, offered to
> Mr. Kang and not selected. Its §9 then recommended keeping the code inert "pending Mr. Kang's answer".
> **That disposition was overridden the same day** — there was no answer to wait for, because K9 was
> already decided and closed. The classes, the ShedLock identity, 5 YAML keys × 4 profiles and 5
> environment variables are **deleted**. **No new K9 decision was requested.**
>
> Everything below remains accurate **as analysis** — evidence, the five answers, the comparison table,
> the Option-A classification. Read the present tense in §5, §9 and §11 as describing the state *at the
> time of the assessment*, not now.
>
> Outcome: **`REMOVE FROM IMPLEMENTATION` — done.**
> Record: [../audits/final-four-to-one-batch-architecture-correction.md](../audits/batch/final-four-to-one-batch-architecture-correction.md)

**Date:** 2026-07-29 · **Type:** architecture verification only — no code implemented, deleted, enabled,
disabled, or reconfigured; no SQL; no commit.
**Primary authority:** Mr. Kang's written K9 reply (2026-06-12), supplied as new primary evidence and
independently located in the repository at `WECMS_BATCH DEV/analysis/questions/questions-for-mr-kang.md:23-25`.

---

## 1. Executive summary

`BulkExecutionRetryJob` **does not match the approved TO-BE**. It re-introduces a scheduled
reconciliation sweep over `TB_FIRM_FILE_MST (PROC_STEP='1', PROC_STAT='6')` — structurally the same
mechanism as legacy `FILE_DEPOSIT_TRANSFER_BATCH2`, which the approved architecture **removed**.

Decisively: K9 put **four** recovery strategies to Mr. Kang. **Option A was verbatim *"Recovery
Scheduler (reconciliation) — periodically re-process aged `STEP=1` rows — re-introduces one bulk
scheduled job."*** He was shown the exact failure modes it would cover — including *"`00302` times out
or fails (`BT01`/`G999`/`C999`) → No automatic retry; **funds may never move**"* — and he **did not
select it**. He answered with the inline-execution model.

**I must correct my own earlier work.** In the dead-code / architecture review I classified the missing
retry as finding **G-1, "a real parity gap."** That classification was **wrong**. The K9 record was in
the repository the whole time; I searched the refactor tree and not
`WECMS_BATCH DEV/analysis/`, so I never saw it. It is not a gap — it is an **approved architecture
difference**, consciously chosen with the consequence spelled out in writing. Everything I built on
that premise inherits the error.

One nuance, stated precisely because it affects how firmly this reads: Mr. Kang's *literal* words cover
only the happy path. The phrases *"no periodic recovery sweep"* and *"monitoring + manual re-trigger"*
are the analysis team's recorded interpretation, attributed *"Per Mr. Kang's direction"* and formally
closed on 2026-06-19. So the sweep contradicts a **closed decision of record** rather than a verbatim
prohibition — but since Option A was offered and passed over, that interpretation is well-founded.

---

## 2. Legacy architecture

`FILE_DEPOSIT_TRANSFER_BATCH2` (`WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH2.java`,
`.batch` cron `*/1 * * * *`):

```text
every 60s → SELECT TB_FIRM_FILE_MST WHERE TRAN_DT=today
                                      AND PROC_STEP='1' AND PROC_STAT='6'   (:48-54)
          → target-failure G998 gate  (:70-104)
          → sibling gate             (:108-125)
          → POST 00302               (:200)
          → empty body      → continue  (:202-206)   ┐ row keeps STEP='1'
          → BT01/G999/C999  → continue  (:232-234)   ┘ ⇒ re-picked next minute
          → 0000/BT00       → PROC_STEP='2'          (:242-263)
          → other           → alert + PROC_STAT='3'  (:264-305)
```

**Retry was never a designed feature.** It was an emergent property of polling: a `continue` left the
row in the selection state and 60 seconds later the same query found it again. The K9 document itself
frames it that way — BATCH2 *"acted as a **self-healing recovery sweep in addition to** issuing the
execution."*

---

## 3. Approved TO-BE architecture

Recorded across three dated records (`post-kang-confirmation-review.md:30-38, 44-51`):

| Legacy component | Approved TO-BE |
|---|---|
| BATCH1 (reception) | Removed as a scheduled job → request-time `BulkReceptionService` |
| **BATCH2 (execution sweep)** | **Removed** — *"배치2 → API1으로 갈음"* (Batch 2 → replaced by API1) |
| `01301` callback | On a normal response, **issues `00302` inline** — *"실행지시로 갈음"* |
| `01302` | Unchanged from AS-IS |
| **Scheduler** | **Only `ResultCheckJob` retained** |

Closing statement of the 2026-06-06 clarification: **"위와 같은 로직으로 배치 업무 제거"** — *remove the
batch jobs with the above logic.* Recorded as **"CLOSED — Approved."**

---

## 4. Mr. Kang's K9 response

**What he was asked** (`questions-for-mr-kang.md:41-66`) — the gap was put to him explicitly, not
glossed:

> **The gap.** *"Because BATCH2's minute-by-minute sweep is gone, no component currently recovers a file
> stranded at `PROC_STEP='1' / PROC_STAT='6'`."*
>
> | Scenario | Effect today |
> |---|---|
> | `01301` callback never received | File stays at `STEP=1 / STAT=6`; never executed |
> | Application restart / crash mid-flow | In-flight `00302` intent is lost; no resweep |
> | `00302` times out or fails (`BT01`/`G999`/`C999`) | No automatic retry; **funds may never move** |

**The four options offered:**

| Option | Strategy | Selected? |
|---|---|---|
| **A** | **Recovery Scheduler (reconciliation)** — *"Periodically re-process aged `STEP=1` rows — **re-introduces one bulk scheduled job**"* | **NO** |
| B | Outbox / Retry — persist the intent, re-send a bounded number of times | NO |
| C | Manual Recovery — monitoring + alert only | adopted for the residual edge |
| D | Other | the inline model (≈ D) |

**His reply (2026-06-12), verbatim:**

> **"Batch 2 제거로 API 응답 정상인 경우 실행 지시로 받아들이도록 프로세스 개선 필요 합니다."**
>
> *Since Batch 2 is removed, improve the process so that a normal API response becomes the execution
> instruction.*

**Recorded decision** (`:27-39`) — *the interpretation, not his words*:

> *"confirms the **inline-execution model** (≈ Option D) — there is **no periodic recovery sweep** …
> Per Mr. Kang's direction we do **not** re-introduce a scheduled sweep; this becomes a **monitoring +
> manual re-trigger** concern (operations), to be covered by alerting rather than an automatic bulk
> scheduler."*

**Closed 2026-06-19** (`post-kang-confirmation-review.md:1-6`): *"K9 — inline-00302 on a normal 01301
response is IMPLEMENTED … **no periodic sweep**; lost callback = monitoring / manual."*

---

## 5. Current Spring implementation

| Component | What it does | Aligned with TO-BE? |
|---|---|---|
| `BulkReceptionResultService.issueExecutionInline` | `01301` → idempotent inline `00302`, sibling gate, `G998` gate, audit-before-call, `PROC_STEP 1→2` | ✅ **This *is* the approved design** |
| `StuckPayFileMonitorService` | read-only detection of `STEP='1'/STAT='6'`; `OpsAlertService`; causes `BULK_EXECUTION_INSTRUCTION_PENDING` / `_RETRY_EXHAUSTED` | ✅ **This *is* the prescribed "monitoring"** |
| `ResultCheckJob` / `ResultCheckService` | refactor of `PROCESSING_RESULT_CHECK_BATCH`; cron `-` | ✅ the one retained scheduler |
| **`BulkExecutionRetryJob`** | **`@Scheduled` + `@SchedulerLock`; periodically selects aged `STEP='1'/STAT='6'` rows and re-issues `00302`** | ❌ **This is Option A** |
| `BulkExecutionRetryService` / `…TxService` | bounded sweep + per-file `REQUIRES_NEW` re-issue | ❌ same |

Current state: double-gated OFF (`enabled=false` + cron `"-"`) in all four profiles, so **nothing is
running against the decision today**.

---

## 6-8. Comparison and answers

| | Legacy | Approved TO-BE | Current Spring |
|---|---|---|---|
| Batch2 polling | every 60s sweep | **removed** → `01301` inline execution | inline execution ✅ **+ a sweep re-added** ❌ |
| Automatic retry | yes (emergent from polling) | **no** — Option A offered and not selected | **yes** — `BulkExecutionRetryJob` ❌ |
| Monitoring | ops alert on permanent failure only | **yes** — the prescribed mechanism | `StuckPayFileMonitorService` ✅ |
| Manual recovery | n/a (self-healing) | **yes** — the prescribed mechanism | documented runbook ✅ |
| Scheduled reconciliation | BATCH2 | **no** — only `ResultCheckJob` retained | `BulkExecutionRetryJob` ❌ |

### Question 1 — Does legacy parity require an automatic retry scheduler?

# **NO**

Two independent grounds:

1. **The approved TO-BE supersedes raw legacy behaviour, and did so knowingly.** Mr. Kang was shown the
   precise consequence — *"No automatic retry; funds may never move"* — and still chose the inline
   model over Option A. A behaviour deliberately removed with its risk stated in writing is an
   **approved difference**, not a parity gap.
2. **Retry was not a distinct legacy business responsibility.** It was a side-effect of the polling
   loop (`continue` → re-picked next minute). The *business* responsibility of BATCH2 — issue `00302`
   under the target-failure and sibling gates — **is** fully reproduced by `issueExecutionInline`.

*Stated honestly:* legacy did retry, so a literal behaviour-for-behaviour comparison would list it.
That is exactly the comparison the approved TO-BE overrides.

### Question 2 — Does K9 describe `01301` → inline `00302` as the Batch 2 replacement?

# **YES**

> *"Batch 2 제거로 API 응답 정상인 경우 실행 지시로 받아들이도록 프로세스 개선 필요 합니다."*
> — *Since Batch 2 is removed, improve the process so that a normal API response becomes the execution
> instruction.*

Corroborated by the 2026-06-06 mapping: *"배치2 → API1으로 갈음"* and *"API1 … 실행지시로 갈음."*

### Question 3 — Does the approved architecture contain a scheduled reconciliation job equivalent to Batch 2?

# **NO**

*"Scheduler | Only `ResultCheckJob` retained."* Option A — the only option that would have created one
— was offered and not selected.

### Question 4 — Does `BulkExecutionRetryJob` introduce behaviour absent from the approved TO-BE?

# **YES**

It adds a **periodic scheduled sweep that re-issues `00302`**. Concretely:

* it selects on the **same predicate** as legacy BATCH2 (`TRAN_DT` + `PROC_STEP='1'` + `PROC_STAT='6'`);
* it runs on a **cron**, i.e. it is a scheduled reconciliation job — making the surviving scheduler
  count **two**, where the approved design says *"only `ResultCheckJob`"*;
* it performs **automatic** re-issue, where the closed decision says the residual edge is *"monitoring
  + manual re-trigger."*

Refinements it adds over legacy (grace window, attempt ceiling, batch cap, distinct lock identity) make
it *safer* than BATCH2 — they do not make it a different **kind** of component. It is Option A.

### Question 5 — Classification

# **D — a reversal of a previously approved architecture decision**

Not **A** (parity does not require it, per Q1). Not **B** (the "residual reliability gap" was
identified, quantified and consciously accepted). Not **C** (a plain enhancement would not contradict a
closed decision).

---

## 9. Is a new K9 decision required?

**Yes** — and only for the sweep. Split the work precisely:

| Component | Status |
|---|---|
| `issueExecutionInline` (+ the `BT00` parity fix, the `G998`/sibling gates, audit ordering) | **Keep — this is the approved design**, no decision needed |
| `StuckPayFileMonitorService` + the two new causes + ops alerting | **Keep — this is the prescribed monitoring**, no decision needed |
| Manual-recovery runbook | **Keep** — the prescribed manual path |
| **`BulkExecutionRetryJob` / `BulkExecutionRetryService` / `BulkExecutionRetryTxService`** | **Requires an explicit K9 re-opening by Mr. Kang before it may be enabled anywhere** |

**Recommendation: keep the code, disabled.** Not removal (Outcome D), because:

* it is inert (double-gated OFF, verified in all four profiles) and cannot act by accident;
* deleting it would discard a working, tested implementation of the very option Mr. Kang may choose if
  the residual risk is re-raised — and the failure mode *"funds may never move"* is significant enough
  that re-raising it is reasonable;
* the decision belongs to Mr. Kang, not to this audit. Removing the option pre-empts him just as
  enabling it would.

If Mr. Kang declines to reopen K9, the correct follow-up is deletion — recorded here as the standing
alternative, not actioned.

**What to put to Mr. Kang** (one question, not a re-approval of anything already settled):

> The inline `01301`→`00302` model is implemented as you directed. The residual edge you were shown in
> K9 — a lost callback, a crash, or a `BT01`/`G999`/`C999` on `00302` — currently has monitoring +
> manual recovery only, per the closed decision. A bounded automatic retry (K9 Option A, but with a
> grace window, an attempt ceiling and a batch cap) is implemented and switched off. **Do you want it
> enabled, or should monitoring + manual remain the answer?**

---

## 10. Documents updated

| Document | Correction |
|---|---|
| `reports/dead-code-architecture-review.md` | **G-1 reclassified** from "real parity gap" to **approved architecture difference**; the retry is not a parity requirement |
| `architecture/architecture-review.md` | H-3 resolution reworded — the sweep is Option A, not a parity fix |
| `audits/scheduler-legacy-parity-audit.md` | addendum corrected: the sweep is a *second* scheduled job against *"only `ResultCheckJob` retained"* |
| `audits/legacy-batch2-ownership-verification.md` | pointer to this reassessment as the design ruling |
| `features/wecms-batch/bulk-execution-retry.md` | opening reframed: not a parity restoration; an unapproved Option A |
| `deployment/sit-uat-scheduler-deployment-checklist.md` · `wecms-batch-activation-runbook.md` · `wecms-batch-environment-variables.md` · `batch-dispatch-variable-reference.md` | uniform wording (below) |
| `testing/integration-test-audit.md` | G-1 "parity gap" phrasing corrected |

Standard wording now used everywhere:

> Mr. Kang has already approved replacing Batch 2 with inline execution via the `01301` callback.
> **`BulkExecutionRetryJob` is not required for legacy parity.** It is K9 **Option A**, which was
> offered and not selected; automatic retry is therefore an architecture enhancement **beyond** the
> approved TO-BE and requires an explicit K9 decision before it may be enabled in any environment.

---

## 11. Final recommendation

1. **Do not enable** `BulkExecutionRetryJob` in any environment — including DEV/SIT, where coexistence
   is provably safe. The blocker is the design decision, not the database topology.
2. **Do not delete it yet.** Keep it inert pending Mr. Kang's answer to the single question in §9.
3. **Keep and rely on** `issueExecutionInline` + `StuckPayFileMonitorService` + the manual runbook —
   together these are the complete approved design, and they are implemented.
4. **Correct the record:** G-1 was not a parity gap. My earlier reports asserted it was, and built a
   condition on that error.

---

# `REQUIRES NEW K9 DECISION`

**Outcome C.** `BulkExecutionRetryJob` conflicts with the approved K9 design. Legacy parity is already
complete without it — `issueExecutionInline` reproduces BATCH2's business responsibility, and the
prescribed monitoring + manual recovery are implemented.

---

> ## ⚑ SUPERSEDED 2026-07-29 — the job was REMOVED, not kept disabled
>
> This document's §9 recommended keeping the code inert "pending Mr. Kang's answer". **That
> recommendation was itself wrong**, and was overridden the same day: there was no answer to wait for.
> K9 was already decided and closed — Option A was offered and not selected — so the only correct action
> was to delete the sweep, not to hold it open as a standing proposal.
>
> **`BulkExecutionRetryJob`, `BulkExecutionRetryService`, `BulkExecutionRetryTxService`,
> `BulkExecutionRetryOutcome`, `BulkExecutionRetryReport` and both test classes are deleted**, along with
> the ShedLock identity, 5 YAML keys × 4 profiles, and 5 environment variables. **No new K9 decision was
> requested.**
>
> Everything else in this document stands: the evidence, the five answers, the comparison table, and the
> classification of `BulkExecutionRetryJob` as **Option A / a reversal of an approved decision**. Only the
> §9 "keep it disabled" disposition is retired.
>
> Full record: [../audits/final-four-to-one-batch-architecture-correction.md](../audits/batch/final-four-to-one-batch-architecture-correction.md).
>
> **Effective verdict: `REMOVE FROM IMPLEMENTATION` — done.**
