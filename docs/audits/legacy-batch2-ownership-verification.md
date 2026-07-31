# Legacy Batch 2 — Ownership & Coexistence Verification

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) + legacy `WECMS_BATCH DEV`
**Question:** does enabling the Spring `00302` retry actually require *new* approval from Mr. Kang?
**Method:** recursive read of `WECMS_BATCH DEV/**` (analysis, decisions, questions, reviews, stakeholder,
handover, source, config) plus the refactor's audit/deployment tree. Evidence-first; the prior report's
condition was **not** carried forward unexamined.

---

## 1. Executive summary

The condition I previously wrote —

> *"Mr. Kang must confirm that legacy `FILE_DEPOSIT_TRANSFER_BATCH2` is stopped before enabling the
> Spring `00302` retry."*

— is **wrong in three separate ways**, and it also **concealed a more serious item**.

| Claim in the old condition | Verdict |
|---|---|
| Mr. Kang approval is needed for the 4→1 architecture | **FALSE — already given**, three times, dated and quoted (§4) |
| The blocker is Mr. Kang confirming legacy BATCH2 is stopped | **MIS-ASSIGNED** — for DEV/SIT it is already proven safe; for UAT it is an **Ops DB-target** question, not an architecture approval |
| The condition applies to all environments | **OVER-BROAD** — DEV and SIT are verified DB-separated; PROD has no refactor deployment |

**The item the old condition hid.** K9 — *"which bulk recovery strategy?"* — was formally put to Mr. Kang
with four options. **Option A was literally "Recovery Scheduler (reconciliation) — periodically
re-process aged `STEP=1` rows — re-introduces one bulk scheduled job."** Mr. Kang did **not** choose it.
He chose the inline model, and the decision was recorded and closed as *"no periodic recovery sweep …
monitoring + manual re-trigger"*.

**`BulkExecutionRetryJob` is Option A.** It is the periodic sweep that was offered and declined. So new
Mr. Kang input **is** required — not to re-approve the architecture, and not to confirm coexistence, but
to **reverse a closed K9 decision**. My previous two reports never surfaced this.

The `StuckPayFileMonitorService` half of that work is, by contrast, **exactly** what K9 prescribed
(monitoring + alerting) and needs no further approval.

---

## 2. Scope and repositories inspected

| Tree | What was read |
|---|---|
| `WECMS_BATCH DEV/analysis/**` | `decisions/`, `questions/` (incl. `questions-for-mr-kang.md`), `reviews/` (incl. `post-kang-confirmation-review.md`), `stakeholder/`, `final-document-consistency-audit.md`, `final-legacy-parity-matrix.md`, `migration/**` |
| `WECMS_BATCH DEV/handover/**` | `05_배치스케줄_운영가이드.md` (schedule/ops guide), technical analysis, EFS guide v1.2.6 |
| `WECMS_BATCH DEV/source/**` | 4 batch `.java`, 4 `.batch` scheduler files, `web.xml`, `wecms_batch{,_HS}.properties`, IDO XML |
| `wecms-api/docs/**` | `audits/`, `deployment/`, `features/wecms-batch/`, `reports/` |
| `wecms-api/src/**` + `application-*.yml` + `batch-dispatch-variable-reference.md` | retry job/service/tx, `ResultCheckJob`, scheduler properties |

Search terms covered the requested English set plus Korean forms (`배치 업무 제거`, `갈음`, `승인`,
`확인`, `제거`, `배치2`).

---

## 3. Four-batch inventory

Source: `WECMS_BATCH/src/batch/*.java`, `web/WEB-INF/jex/simpleBatch/WECMS_BATCH/*.batch`,
`handover/05_배치스케줄_운영가이드.md`.

| Legacy batch | Legacy responsibility | Spring replacement | Legacy scheduler expected to run? | Evidence |
|---|---|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | Poll `TB_PAY_TARGET` `PROC_STAT='9'`; operating-hours gate; Woori FX 204; EFS 202; status write-back | `RealtimeDispatchRegistrar` (after-commit) → `RealtimeDispatchService` → EFS 202 | **No** in the refactor. Legacy node still runs it where deployed. | `.batch` `schedule=* * * * *`; TOBE table `post-kang-confirmation-review.md:34-38` |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | Poll `TB_PAY_FILE_MST` `'9'`; channel classification; file build; SFTP; EFS `00301` | `DispatchAutoReceiveRegistrar` (after-commit) → `BulkReceptionService` (**request-time, not scheduled**) | **No** in the refactor. | `.batch` `*/1 * * * *`; Mr. Kang TOBE row "DB load → file generation → API call" |
| **`FILE_DEPOSIT_TRANSFER_BATCH2`** | Poll `TB_FIRM_FILE_MST (TRAN_DT, PROC_STEP='1', PROC_STAT='6')`; target-fail `G998` gate; sibling gate; EFS `00302`; **implicit retry via re-poll** | `BulkReceptionResultService.issueExecutionInline` from the `01301` callback (**absorbs BATCH2**) | **No** — explicitly **removed** by approved design | `.batch` `*/1 * * * *`; `FILE_DEPOSIT_TRANSFER_BATCH2.java:48-54`; Mr. Kang: *"배치2 → API1으로 갈음"* |
| `PROCESSING_RESULT_CHECK_BATCH` | Poll `TB_FIRM_TRAN` 202/`'6'` older than 2 min; EFS 203; finalize | `ResultCheckJob` → `ResultCheckService` — **the only surviving scheduled legacy-batch replacement** | **No** in the refactor (cron `-`). | `.batch` `*/1 * * * *`; `IDO.TB_FIRM_TRAN_R001.xml` |

**No per-batch enable flag exists.** Each `.batch` file carries only `name`, `batchClass`, `schedule`,
`[INPUT]` — verified byte-for-byte on `FILE_DEPOSIT_TRANSFER_BATCH2.batch`. Whether a node runs the four
batches is decided by the **container-level `JEX_BATCH` flag in `jex.prop`**, not per batch. Consequence:
**legacy BATCH2 cannot be disabled individually** — a legacy batch container runs all four or none.

---

## 4. Mr. Kang approval evidence

Three dated records, escalating in specificity.

### 4.1 — 2026-06-04 · TOBE re-scope

> `analysis/reviews/post-kang-confirmation-review.md:44-51` (context table), corroborated at
> `final-document-consistency-audit.md:286`

Bulk path re-scoped: BATCH1 → request-time; **BATCH2 → Removed**; `01301` issues `00302` inline;
`ResultCheckJob` the only scheduled job. Applies to the refactor design (all environments).

### 4.2 — 2026-06-06 · Written clarification, item-by-item

> `analysis/reviews/post-kang-confirmation-review.md:30-38`

Mr. Kang's note ends **"위와 같은 로직으로 배치 업무 제거"** — *"remove the batch jobs with the above
logic."* The per-component mapping records:

| Component | Mr. Kang's TOBE |
|---|---|
| 배치2 (BATCH2) | **"Replaced by API1 (API1으로 갈음)"** |
| API1 (01301) | on a normal response, **substituted by the execution instruction ("실행지시로 갈음")** |
| Result inquiry | batches removed; only result-check remains scheduled |

Recorded at `:48` as **"CLOSED — Approved … Mr. Kang's clarification is the explicit approval the
question requested; all five points (BATCH1 removed, BATCH2 removed, 01301→00302, API2 unchanged,
ResultCheckJob-only) confirmed verbatim."**

**This explicitly includes Batch 2.** It concerns the *refactor design*, not the legacy server's runtime.

### 4.3 — 2026-06-12 · K9 reply — **the decisive record for the retry job**

> `analysis/questions/questions-for-mr-kang.md:21-39`

K9 asked *"which recovery strategy is intended?"* and offered four options (`:60-66`):

| Option | Strategy | Chosen? |
|---|---|---|
| **A** | **Recovery Scheduler (reconciliation)** — *"Periodically re-process aged `STEP=1` rows — **re-introduces one bulk scheduled job**"* | **NO** |
| B | Outbox / Retry — persist the intent, re-send a bounded number of times | NO |
| C | Manual Recovery — monitoring + alert only | *(adopted for the residual edge)* |
| D | Other | *(the inline model, ≈ D)* |

Mr. Kang's reply: **"Batch 2 제거로 API 응답 정상인 경우 실행 지시로 받아들이도록 프로세스 개선
필요합니다."** — *with BATCH2 removed, improve the process so a normal API response is accepted as the
execution instruction.*

The recorded decision (`:27-39`):

> *"confirms the **inline-execution model** (≈ Option D) — there is **no periodic recovery sweep** …
> Per Mr. Kang's direction we do **not** re-introduce a scheduled sweep; this becomes a **monitoring +
> manual re-trigger** concern (operations), to be covered by alerting rather than an automatic bulk
> scheduler."*

Closed 2026-06-19 (`post-kang-confirmation-review.md:1-6`): *"K9 — inline-00302 on a normal 01301
response is IMPLEMENTED … **no periodic sweep**; lost callback = monitoring / manual."*

---

## 5. Architecture approval conclusion

### Question A — did Mr. Kang approve the 4→1 architecture?

# **YES**

Explicit, dated (2026-06-04 / 06-06), quoted verbatim, mapped component-by-component, and formally
closed as *"CLOSED — Approved"*. It **explicitly names Batch 2** as replaced by API1.

**No further architecture approval is required, and none should be requested.** Any document still
asking Mr. Kang to approve the 4→1 reduction is stale.

---

## 6. Batch 2 runtime ownership conclusion

### Question B — is it confirmed that legacy BATCH2 will not run against the refactor DB?

# **PARTIAL**

Not one answer — it resolves per environment, and **none of it is a Mr. Kang architecture question**.

* **DEV, SIT — YES, proven from repository + live evidence.** Not by anyone stopping BATCH2, but by
  **database separation**: the legacy batch never reaches the refactor DB.
* **UAT — NOT PROVABLE from this repository.** The refactor UAT DB URL is not committed. This is an
  **Ops / Infra** confirmation, not an approval.
* **PROD — N/A.** The refactor is not deployed there.

§4.2 proves Mr. Kang approved *that the Spring system will not contain BATCH2*. It does **not** prove
anything about an independently-deployed legacy JEX container's live process — and it was never asked
to. Treating the two as identical was the error in my previous condition.

---

## 7. Environment-by-environment legacy Batch 2 status

Container-level `JEX_BATCH` governs all four batches (§3).

| Environment | Legacy Batch 2 status | Shares a DB with the refactor? | Evidence |
|---|---|---|---|
| **DEV** | **enabled** (`WECMS_BATCH_DEV` `JEX_BATCH=true`) | **NO** — refactor `wecmsdevdb`@`172.168.5.172` vs JEX `wecmsvndevdb`@`10.254.242.254` (different host *and* database; both MCP identities + `.claude/rules/database.md`) | `wecms-batch-activation-and-external-confirmations.md:98` |
| **SIT** | **not deployed** — no `WECMS_BATCH_SIT` container exists in `jex.prop` (only DEV/UAT/REAL_01/REAL_02/REAL_HS) | N/A — nothing to collide with | ibid. `:100` |
| **UAT** | **enabled (config)** — `WECMS_BATCH_UAT` `JEX_BATCH=true` | **UNKNOWN** — refactor UAT DB URL not committed (`.env.uat.example` is a placeholder) | ibid. `:101` |
| **PROD** | **enabled (config)** — `REAL_01` + `REAL_HS` `JEX_BATCH=true`, `REAL_02=false` | N/A — refactor not deployed to PROD | ibid. `:102` |

> Per the task's own restriction, config evidence is **not** treated as proof of a live process. DEV/SIT
> safety rests on **DB separation** (independently verified), not on any claim that BATCH2 is stopped.

---

## 8. Spring retry activation status

| Property | Verified state |
|---|---|
| Disabled by default | **Yes** — `wecms.dispatch.bulk-execution-retry.enabled` defaults `false` |
| Double-gated | **Yes** — plus `cron` defaults `"-"` (never-fire); both must be opened |
| Safe when env vars missing | **Changed 2026-07-30** — inline defaults removed (bare-placeholder migration). An unset variable now DOES abort startup for typed/`@Value` properties; it can still never silently enable a job, and the inert committed value moved to `.env.*.example` where `SchedulerConfigurationContractTest` enforces it. See scheduler-legacy-parity-audit.md §F-6. |
| Independently activatable | **Yes** — own lock identity `DISPATCH_BULK_EXECUTION_RETRY`, distinct from `DISPATCH_STRANDED_RECOVERY` and `DISPATCH_RESULT_CHECK` |
| Described as recovery, not a BATCH2 restoration | **Yes in the code Javadoc** — *"not a restoration of BATCH2 … the inline issue remains the primary path, this is the exception handler behind it"* |
| **Consistent with the closed K9 decision** | **NO** — see §9.2. It is a periodic scheduled sweep; K9 recorded *"no periodic sweep"*. |

`StuckPayFileMonitorService` (read-only detection + `OpsAlertService`) **is** consistent with K9 —
it is the *"monitoring + manual re-trigger … covered by alerting"* the decision named.

---

## 9. Technical coexistence and duplicate-request analysis

### 9.1 — Can legacy BATCH2 and the Spring retry issue `00302` for the same file?

# **YES — verified duplicate risk** (only where a DB is shared, i.e. UAT / any future PROD)

| Guard | Legacy BATCH2 | Spring retry | Shared? |
|---|---|---|---|
| Selection predicate | `TRAN_DT` + `PROC_STEP='1'` + `PROC_STAT='6'` (`:48-54`) | identical + grace window | **same rows** |
| Row lock at selection | **none** — plain `executeList`, no `SELECT … FOR UPDATE` | **none** — `findById` is a plain read | ✗ |
| Transaction boundary | `beginTransaction()` at `:236` — **after** the POST at `:200` | `REQUIRES_NEW`, but the POST is inside it | ✗ |
| Idempotency probe before sending | **none** — legacy has no `exists` check at all | last-attempt-RC + attempt-cap (refactor-only) | ✗ |
| `TRAN_SEQ` per attempt | **fresh** each cycle (`Sequence.getSeq()`, `:160`) | **fresh** each attempt | ✗ — so the VAN sees two distinct `TNO`s |
| Distributed lock | none | ShedLock — **refactor nodes only** | ✗ |
| `PROC_STEP` re-check | none between select and send | reloaded by PK inside the tx | ✗ (one-sided) |

**The race, concretely.** Both runtimes select the same `STEP='1'/STAT='6'` row. Neither holds a lock at
selection. Legacy POSTs `00302` *before* opening its transaction (`:200` → `:236`), so its
`PROC_STEP='2'` write lands after the network call. The Spring retry's PK re-check therefore still sees
`'1'` and proceeds. Both send `00302` for the same `FIRM_FILE_NM`, each with a **different** `TNO` — so
there is no VAN-side idempotency key to collapse them, and the guide defines none for `00302`. Result:
**two execution instructions for one bulk file.**

Note this is **not a refactor-introduced weakness**: legacy's own baseline is not single-runner-safe —
`REAL_01` and `REAL_HS` both run with `JEX_BATCH=true`, the same DataSource and gateway, *"with no lock
of any kind"* (`payment-dispatch-flags-deployment-readiness-audit.md:341`).

**Materiality.** DEV and SIT: **not reachable** (no shared DB). UAT: **reachable if** the DB target is
shared. PROD: refactor not deployed.

### 9.2 — The finding that matters more

Independently of any duplicate risk: **the retry job implements the K9 option Mr. Kang was offered and
did not choose.**

```text
K9 Option A  = "Recovery Scheduler (reconciliation) — periodically re-process aged STEP=1 rows
                — re-introduces one bulk scheduled job"          ← NOT chosen
K9 decision  = inline execution; "no periodic recovery sweep";
               residual edge → "monitoring + manual re-trigger"   ← chosen, closed 2026-06-19
BulkExecutionRetryJob = @Scheduled sweep that periodically re-processes aged STEP='1' rows
                        and re-issues 00302                       ← Option A
```

This is a **design reversal of a closed decision**, and it is what actually requires Mr. Kang. It is
independent of the DB-sharing question: it would apply even in DEV/SIT where coexistence is provably
safe.

**No code was changed by this audit.** The job is double-gated OFF, so nothing is running against the
decision today — but it should not be enabled anywhere, and arguably should not be retained, without
Mr. Kang re-opening K9.

---

## 10. Documentation statements corrected

| Document | Corrected from | Corrected to |
|---|---|---|
| `reports/dead-code-architecture-review.md` | "Mr. Kang must confirm legacy BATCH2 is decommissioned" | Architecture approved 2026-06-04/06/12; DEV/SIT safe by DB separation; UAT = Ops DB check; **new** K9-reversal item raised |
| `architecture/architecture-review.md` | coexistence framed as a blanket Mr. Kang gate | split into *approved architecture* vs *Ops verification* vs *K9 reversal* |
| `audits/scheduler-legacy-parity-audit.md` | F-7 coexistence applied blanket to the retry job | scoped per environment; K9 conflict recorded |
| `features/wecms-batch/bulk-execution-retry.md` | "COEXISTENCE PRECONDITION — requires Mr. Kang" | restated: architecture already approved; **the** open item is the K9 Option-A reversal |
| `deployment/sit-uat-scheduler-deployment-checklist.md` | "[CONFIRM: Mr. Kang]" on the retry row | DEV/SIT safe; UAT Ops DB check; K9 decision flagged |
| `deployment/wecms-batch-activation-runbook.md` | same blanket wording | same split |
| `deployment/wecms-batch-environment-variables.md` §2b | same blanket wording | same split |
| `batch-dispatch-variable-reference.md` §2b | "COEXISTENCE PRECONDITION — REQUIRES MR. KANG'S CONFIRMATION" | per-env status + the K9 conflict |

Standard wording adopted everywhere:

> **Architecture:** approved by Mr. Kang (2026-06-04 / 06-06 / 06-12) — no further design approval needed.
> **Runtime:** DEV/SIT safe by DB separation; UAT requires an **Ops** DB-target confirmation; PROD N/A.
> **Open decision:** the periodic retry sweep reverses the closed **K9** "no periodic sweep" decision —
> **REMOVED 2026-07-29.** Automatic retry was never a parity requirement — inline execution already reproduces BATCH2 — and the sweep was K9 Option A, which was offered and not selected. `BulkExecutionRetryJob` and all its config have been deleted. See [final-four-to-one-batch-architecture-correction.md](../audits/final-four-to-one-batch-architecture-correction.md)..

---

## 11. Is additional Mr. Kang approval required?

**Yes — but for a different item than previously stated, and none of the three previously-cited reasons.**

| Item | Needs Mr. Kang? |
|---|---|
| 4→1 batch architecture | **No** — approved 2026-06-04/06-06 |
| BATCH2 replaced by the `01301` inline path | **No** — approved verbatim ("API1으로 갈음") |
| Legacy BATCH2 stopped in DEV/SIT | **No** — moot; DB-separated |
| Legacy BATCH2 vs refactor in UAT | **No** — an **Ops/Infra** DB-target confirmation |
| **`BulkExecutionRetryJob` re-introducing a periodic bulk sweep** | **YES — reopens closed K9** |

## 12. Is only operational verification required?

**No.** Two distinct remaining items, with different owners:

1. **Ops / Infra** — confirm whether the refactor UAT DB is the JEX UAT batch DB *(operational, UAT-only)*.
2. **Mr. Kang** — decide whether K9 is reopened to permit Option A *(design decision, all environments)*.

---

## 13. Files changed

Documentation only. **No production code, no configuration values, no flags, no SQL, no commit.**

`reports/dead-code-architecture-review.md` · `architecture/architecture-review.md` ·
`audits/scheduler-legacy-parity-audit.md` · `features/wecms-batch/bulk-execution-retry.md` ·
`deployment/sit-uat-scheduler-deployment-checklist.md` · `deployment/wecms-batch-activation-runbook.md` ·
`deployment/wecms-batch-environment-variables.md` · `batch-dispatch-variable-reference.md` ·
**this file (new)**

---

## 14. Part 8 — is a `legacy-batch2-disabled-confirmed` flag warranted?

**Assessment: excessive. Do not implement.**

1. **It would not enforce anything.** It records a human claim with no operational check — precisely the
   anti-pattern the task warns against. Anyone able to set `enabled=true` can set the attestation too.
2. **It solves the wrong problem.** The blocking item is the K9 design reversal, which no flag settles.
3. **The real risk is already covered.** DEV/SIT are DB-separated; UAT is gated by the existing
   double gate plus the documented Ops check.
4. **A cheaper, enforceable alternative exists if UAT ever needs it:** compare the configured
   `WECMS_DB_URL` against a known legacy-DB deny-list at startup — that checks a *fact*, not a claim.
   Not implemented now; recorded as an option.

**Recommendation:** keep the existing double gate, fix the documentation (done), and route the K9
question to Mr. Kang.

---

## 15. Remaining unknowns

| # | Unknown | Owner | Why unresolvable here |
|---|---|---|---|
| 1 | Refactor UAT `WECMS_DB_URL` — same instance as the JEX UAT batch DB? | Ops / Infra | Not committed; UAT DB not queryable from this workspace |
| 2 | Live process state of `WECMS_BATCH_UAT` / `REAL_01` / `REAL_HS` | Legacy batch owner | Config ≠ running process; no runtime access (and inferring it from config is explicitly disallowed) |
| 3 | Whether Mr. Kang would accept Option A now that the residual-edge risk is better characterised | Mr. Kang | Not previously asked in these terms |
| 4 | Whether the VAN Center de-duplicates `00302` by `file_nm` server-side | VAN Center | Not in guide v1.2.6; the `TNO` differs per attempt, so no client-side key exists |

---

## 16. Final verdict

# `ARCHITECTURE APPROVED — OPERATIONAL VERIFICATION REQUIRED`

...**plus one newly-identified design decision that the previous reports missed.**

The 4→1 reduction — including Batch 2's replacement — is **approved, dated and quoted**. No further
architecture approval should be requested. What remains is (a) an **Ops** UAT DB-target check, and (b) a
**Mr. Kang** decision on whether to reopen **K9** to permit the periodic retry sweep, which the closed
decision excluded.

---

## Final answers

**1. Did Mr. Kang already approve the 4-to-1 architecture?**
**YES.** 2026-06-04 re-scope; 2026-06-06 written clarification ending *"위와 같은 로직으로 배치 업무 제거"*;
recorded **"CLOSED — Approved"** (`post-kang-confirmation-review.md:48`).

**2. Does that approval explicitly include replacement of legacy Batch 2?**
**YES, explicitly.** *"배치2 → API1으로 갈음"* (Batch 2 → replaced by API1), and again in the 2026-06-12
K9 reply: *"Batch 2 제거로…"* (with Batch 2 removed…).

**3. Is legacy Batch 2 disabled or stopped in DEV, SIT, UAT, and PROD?**
**No — and for DEV/SIT that does not matter.**
DEV: **running**, but **DB-separated** from the refactor → safe.
SIT: **not deployed** (no `WECMS_BATCH_SIT` container) → safe.
UAT: **enabled in config**; DB overlap **unknown** → unverified.
PROD: **enabled in config**; refactor not deployed → N/A.

**4. Can legacy Batch 2 and the Spring retry run against the same database safely?**
**NO — verified duplicate risk on a shared DB.** Neither side locks the row at selection; legacy POSTs
`00302` *before* opening its transaction (`:200` vs `:236`); both allocate a fresh `TNO`, so the VAN has
no key to collapse the two. Only reachable in UAT (if shared) or a future PROD deploy — **not** in
DEV or SIT.

**5. Is additional Mr. Kang approval required?**
**YES — but not for the architecture and not for coexistence.** It is required to **reopen K9**: the
periodic retry sweep is Option A, which was offered and declined in favour of *"no periodic sweep;
monitoring + manual"*.

**6. Is only deployment/runtime verification required?**
**NO.** Two items remain: an **Ops** UAT DB-target confirmation, and the **Mr. Kang** K9 decision above.
