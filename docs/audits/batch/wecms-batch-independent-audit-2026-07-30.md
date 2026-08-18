# WeCMS batch refactor — audit findings (consolidated)

JEX SimpleBatch + JSP → Spring Boot (`wecms-api`, `domain/payment/dispatch`)

**Status.** Refactor is not deployed to production; legacy JEX owns PROD
(REAL_01 + REAL_HS). All three schedulers default to Spring's never-fire marker
`-`, and both dispatch flags default false. SFTP disabled, `MockEfsApiClient` in
place, B4 unprovisioned. No live egress has occurred.

**This safety is conditional and currently accidental.** See C-0: the flags do not
gate row creation, and the only thing preventing coexistence damage today is that
the refactor's DEV/SIT database happens to be a different instance from the JEX
frame DB. The recorded deployment direction removes that separation.

**Method.** Source inspection with file:line evidence, four read-only Claude Code
sessions. Nothing has been reproduced against a live gateway. Findings marked
*inferred* need confirmation by test or by the counterparty.

**Scope note.** None of this argues against the approved architecture. Collapsing
four batches to one and moving VAN Center dispatch to direct API calls is sound,
and the refactor implements it faithfully — the sibling barrier was carried over,
`BulkDispatchClaimTxService` was written specifically for pre-egress claiming, and
recovery components legacy never had were added. What the polling architecture
provided for free was *time*: a minute between steps during which every prior write
had committed, on a thread nobody was waiting on. Removing the scheduler removes
both. Ordering and pacing that were previously implicit now have to be explicit.
That is what these findings are.

---

## ✅ Progress board

**Overall: ~80% to production-ready. Code work is done; what remains is operational.**

| Phase | Progress | Status |
|---|---|---|
| **1. Audit & findings** | `██████████` 100% | ✅ **DONE** — 6 legacy programs swept line by line, 40+ findings |
| **2. Code fixes — dispatch** | `██████████` 100% | ✅ **DONE** — 36 findings closed, 2,687 tests green |
| **3. Documentation** | `██████████` 100% | ✅ **DONE** — all claims verified against source, 4 stale docs corrected |
| **4. Config migration** | `██████████` 100% | ✅ **DONE** — 169 placeholders converted, 4 profiles complete |
| **5. SIT deploy** | `██████████` 100% | ✅ **DONE** — war built and deployed |
| **6. Infrastructure** | `███░░░░░░░` 30% | 🔄 **IN PROGRESS** — values located; key file, username, allow-list outstanding |
| **7. Live verification** | `░░░░░░░░░░` 0% | ⬜ **NOT STARTED** — no bulk file has reached a bank |
| **8. Code — pre-UAT/PROD** | `░░░░░░░░░░` 0% | ⬜ **NOT STARTED** — R-02, revocation store |
| **9. UAT/PROD gates** | `░░░░░░░░░░` 0% | ⬜ **NOT STARTED** — after SIT proves out |

### ✅ What is finished

- ✅ **Wire format** — byte-for-byte identical to legacy, all three record types
- ✅ **Protocol** — same six EFS calls, same order, same `PROC_STEP` values
- ✅ **Crypto** — AES/CBC, zero IV, HMAC, 14-char prefix, all verified
- ✅ **Commit ordering** — restored to legacy's commit-then-upload (two-phase dispatch)
- ✅ **Per-bank pacing** — restored, and correct per row where legacy leaks
- ✅ **Gateway headers** — `x-coocon-accessIp` restored; timeouts back to legacy's 30s/60s
- ✅ **36 defects fixed** — including duplicate 00302, lost per-line results, false all-success
- ✅ **22 legacy defects deliberately not ported** — each evidence-graded
- ✅ **2,687 tests, 0 failures**
- ✅ **All documentation claims verified** — 4 stale blockers corrected, 3 of my own claims withdrawn

### 🔄 In progress — infrastructure

- ✅ SFTP host, port, paths — found in `wecms_batch.properties`
- ✅ API key — inherited from the shared JEX database, no provisioning needed
- ✅ Schedule rows — already in the live JEX DBs, no seeding needed
- ✅ EFS base URL — was in the repo all along
- ⬜ Place the SFTP private key at `~/.ssh/id_rsa`, 0600
- ⬜ Set `WECMS_SFTP_USERNAME=GV00000001`
- ⬜ Confirm which OS account runs `wecms-api`
- ⬜ Set `WECMS_CALLBACK_ALLOWED_IPS` on the SIT host
- ⬜ Run four `SELECT`s to confirm the DB rows per environment
- ⬜ Apply the two index scripts before UAT/PROD

### ⬜ Not started — and the one that matters

- ⬜ **Read the running config off the SIT host (C-1).** Top blocker. Seventeen live
  EFS calls came from a node whose committed config said `false`
- ⬜ **One bulk file end to end.** The first test in this project that exercises
  behaviour rather than reading code
- ⬜ The two `rs_cd` queries afterwards — no test in the suite executes JPQL
- ⬜ 35-file PROD backlog reconciliation — worksheet ready, needs the bank
- ⬜ Two vendor questions — neither blocks SIT
- ⬜ R-02 and the revocation store — pre-UAT/PROD

### The honest headline

Everything above was verified **by reading** — source, production data, the vendor
guide. **Zero percent has been verified by a transaction.** One bulk file through SIT
converts the first row of the match table from *100% by inspection* to *100% by
measurement*, and it is the single highest-value action remaining.

---

## Flow and process — legacy and refactor

The four bulk stages, the realtime rail and the recovery rail are unchanged in what
they send to the bank. Only what triggers each stage changed. This section is the
reference the findings below assume.

### Legacy — four cron batches, two JSP callbacks, one synchronous service

WeCMS writes a row and stops. Everything after that is polling.

| Program | Schedule | Polls | Calls | Writes |
|---|---|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | `* * * * *` | `TB_PAY_TARGET` — `TRAN_DT=today AND PROC_STAT='9'` | EFS 204 (Woori FX), EFS 202 | `TB_FIRM_TRAN`, `TB_PAY_TARGET` → `0`/`3`/`6` |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `*/1 * * * *` | `TB_PAY_FILE_MST` — `PROC_STAT='9'` | EFS 301 (00301) | `TB_FIRM_FILE_MST`/`_DATA` at step `0` |
| `FILE_DEPOSIT_TRANSFER_API1` (JSP) | callback | — | — | `PROC_STEP` 0→1 |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | `*/1 * * * *` | `TB_FIRM_FILE_MST` — `(step 1, stat 6)` | EFS 302 (00302) | `PROC_STEP` 1→2 |
| `FILE_DEPOSIT_TRANSFER_API2` (JSP) | callback | — | SFTP download | `TB_FIRM_FILE_RSLT`, step 2→3, pay file `0`/`1`/`3` |
| `PROCESSING_RESULT_CHECK_BATCH` | `*/1 * * * *` | `TB_FIRM_TRAN` — `TRAN_TP='202' AND PROC_STAT='6'`, aged 2 min | EFS 203 | `TB_FIRM_TRAN`, `TB_PAY_TARGET` → `0`/`3` |
| `WB_00000100` | synchronous | — | EFS 201 | `TB_FIRM_TRAN` (`TRAN_TP='201'`) |

**Realtime rail.** Poll `'9'` rows → `TB_FIRM_CONF` lookup → operating-hours gate
(`TB_BATCH_SCHEDULE_INFO`/`_DTL`, `EXE_DIV` Y/N/E) → tone-strip → per-bank
`Thread.sleep` → Woori + non-VND takes EFS 204 first → insert `TB_FIRM_TRAN` at
`TRAN_TP='202'`/`PROC_STAT='6'` → **EFS 202** → write outcome. No response or `G999`
leaves the row at `'6'` for the result-check batch. No transaction management anywhere
— every write is autocommit.

**Bulk rail.** Poll `'9'` → Gate A validation (network/currency rules, per-bank
network restrictions) → operating hours → branch split by `BAT_TRAN_BRANCH_DIV` into
`ceil(cnt/limit)` files → build the 1000-byte H/D/T record set → insert
`TB_FIRM_FILE_MST` + `_DATA` → **commit** (`:696-697`) → **SFTP upload** (`:700`) →
audit (`:718`) → **EFS 301** (`:721`). Per split file, breaking the loop and failing
the pay file on any error.

Then 01301 lands on a JSP: verify VV, decrypt EV, look up the file by
`(TRAN_DT=today, FIRM_FILE_NM exact, step 0, stat 6)`, reconcile `data_cnt`/`data_amt`
against `REQ_CNT`/`REQ_AMT`, advance 0→1, rebuild EV/VV and answer `RC=0000`.

BATCH2 then polls `(step 1, stat 6)`, applies the R003 sibling barrier — defer while
any sibling of the same `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO)` sits at `(step 0, stat 6)` —
calls **EFS 302**, advances 1→2, and commits per row. `BT01`/`G999`/`C999` are
transient: `continue`, no status write, retried on the next tick.

Finally 01302 lands on the second JSP: SFTP-download the bank's `R_`-prefixed result
file, parse H/D/T, insert `TB_FIRM_FILE_RSLT` per line, update `TB_FIRM_FILE_DATA`
through a prefix-stripping join, advance 2→3, and finalise the pay file to `0`/`1`/`3`
from the sibling aggregate.

**Recovery rail.** `PROCESSING_RESULT_CHECK_BATCH` picks up 202 rows sitting at `'6'`
for two minutes, calls EFS 203, and writes the confirmed outcome — keyed on the date
the *bank echoes back* (D-B).

### Refactor — one scheduled job, two REST callbacks, everything else request-time

| Stage | Trigger | Component |
|---|---|---|
| Row created at `'9'` | `POST /api/v1/payment/{type}/{id}/dispatch` | five `*DispatchExecutor` (`MANDATORY` propagation) — **no flag** |
| Realtime 202 | `afterCommit` | `RealtimeDispatchRegistrar` → `RealtimeDispatchService` (flag `realtime-dispatch`) |
| Bulk 00301 | `afterCommit` | `DispatchAutoReceiveRegistrar` → `BulkReceptionService` (flag `auto-receive`) |
| 01301 | REST callback | `BulkReceptionResultController` → `BulkReceptionResultService` |
| 00302 | `afterCommit` on barrier release | `BulkExecutionInlineTxService` (`REQUIRES_NEW` per sibling) — **BATCH2 absorbed** |
| 01302 | REST callback | `BulkExecutionResultController` → `BulkExecutionResultService` |
| 203 recovery | `@Scheduled` + ShedLock | `ResultCheckJob` → `ResultCheckService` — cron `-`, `enabled` false |
| 201 inquiry | synchronous | `RecipientLookupService` / `DepositorInquiryService` |

**Realtime rail.** `afterCommit` → `RealtimeDispatchService.dispatch` (`REQUIRES_NEW`)
→ `claimForDispatch` `'9'`→`'6'` (atomic, guarded) → `OperatingHoursService` →
Woori/non-VND FX leg → **EFS 202** → `applySuccess` / `applyFail` / `applyPending`.
No response, `G999` or VV mismatch all take the pending path to `'6'`.

**Bulk rail, two phases** (restored to legacy ordering 2026-07-31):

*Phase A* — one transaction over all N split files: insert `TB_FIRM_FILE_MST` +
`_DATA` at step `0`/stat `6`, encode the records, run the blocked-char guard
group-wide, **commit**. No external calls.

*Phase B* — per file, no transaction spanning an external call: write staging → SFTP
upload → audit via `REQUIRES_NEW` → **EFS 301**. Break on failure, pay file `'3'`,
matching legacy `:741-768`.

01301 arrives at a REST controller: verify VV, decrypt, look up by a `TRAN_DT`
**derived from the filename** (not the clock), sibling fail-stop, numeric
reconciliation, advance 0→1, evaluate the sibling barrier, and register the 00302
fan-out on `afterCommit`. Envelope answers `RC=0000` on every durably-recorded path.

00302 runs per sibling in its own `REQUIRES_NEW` transaction, with the idempotency
marker committed *before* the gateway call. Transient failures leave the file at
`(1, 6)` — no auto-retry (K9).

01302 downloads, decodes, writes results through the same prefix-stripping join, and
finalises the pay file behind two guards: defer while any sibling is at `PROC_STAT='6'`,
and never finalise when no sibling is terminal.

**Recovery rail.** `ResultCheckService` scans `TRAN_TP='202'`/`PROC_STAT='6'` over
`{UTC today, yesterday}`, oldest-first, capped at 100 rows with a PT10M pass deadline,
paced per bank, and writes the outcome keyed on the **local** row.

### Side by side

| | Legacy | Refactor |
|---|---|---|
| Row creation | WeCMS writes `'9'`, stops | same — and still ungated (C-0) |
| Realtime trigger | cron, every minute | `afterCommit` on the user request |
| Bulk dispatch trigger | cron, every minute | `afterCommit` on the user request |
| Execution request (00302) | separate cron batch | inline, `afterCommit` on barrier release |
| Callbacks | JSP actions | REST controllers |
| Recovery | cron, every minute | one scheduled job, off by default |
| Commit vs egress | commit, then upload, then call | **same again** since 2026-07-31 |
| Retry on failure | implicit — next tick re-selects | explicit, or none |
| Pacing | `Thread.sleep` in the batch loop | `DispatchPacer`, 203 scan only (C-6 open) |
| Concurrency control | single-threaded batch | ShedLock on the one job; request threads elsewhere |
| Transactions | autocommit / manual `beginTransaction` | Spring `@Transactional`, `REQUIRES_NEW` at seams |
| Tenant scope | all tenants, no `BIZ_KEY` predicate | same, deliberately |

### What the polling provided, and where it went

Three guarantees came from the schedule rather than the code, so nothing was written
down to port. Every open finding traces to one of them.

**It was the retry.** A failed EFS call left the row at `'9'` and the next tick tried
again, indefinitely, for free. Removing the pollers removed that — which is why
`StrandedRecoveryService` and `StuckPayFileMonitorService` had to be written, and why
they are the only detection for states the batch-1 fixes created.

**It was the ordering guarantee.** A minute between stages meant every prior write had
committed. Removing it made commit-versus-egress ordering something that has to be
explicit — root cause A, C-3, and the two-phase dispatch change that closed them.

**It was a background thread.** Sleeping three seconds between Woori calls cost
nothing in a batch loop. On a Tomcat request thread it costs a user's request, which is
why per-bank pacing on the 202 path (C-6) still has nowhere to live.

---

## Status at a glance

Read this section first. Everything below it is evidence.

| Category | Count | Blocked by |
|---|---|---|
| ✅ Verified correct — no action | 20 areas | — |
| ✅ Fixed and tested | 36 findings | — |
| 🔧 Dispatch subsystem code | **complete** | — |
| 🔧 Code deferred to pre-UAT/PROD | 2 items | R-02, revocation store |
| ⚙️ Needs IT / infrastructure | 4 items | mostly reading values legacy already uses |
| 🚧 Security gate | 1 item | R-03 + allow-list (one item) |
| 📞 Needs VAN Center | 2 questions | neither blocks SIT |
| 🧪 Needs a real transaction | 4 checks | B4 provisioning |
| 📋 Legacy defects — deliberately not ported | 22 items | — |
| 🔴 Live production backlog | 35 files | operations |
| 📦 Product / observability | 2 items | not blocking |

Test suite: **2,699 tests, 0 failures**, 323 classes. Deployed to SIT.

**The dispatch subsystem is code-complete.** "No code work left" is not the same
claim — R-02 (action-level authorization) and the in-memory revocation store are real
code items gated to pre-UAT/PROD, outside this subsystem.

---

## Does it match the old legacy?

The question in one table.

| | Verdict |
|---|---|
| **Wire format** — what the bank receives | **Identical.** Byte-for-byte on all three record types |
| **Protocol** — the four bulk stages, the realtime rail, 203 recovery | **Identical.** Same messages, same order, same `PROC_STEP` values |
| **Crypto** — AES/CBC, zero IV, HMAC, 14-char prefix | **Identical** |
| **Business rules** — validation, operating hours, branch split, per-bank pacing | **Identical** |
| **Architecture** — how work is triggered | **Deliberately different.** Approved (K9) |
| **~22 legacy behaviours** | **Deliberately different.** Each fixes a legacy defect |
| **Proven against a real bank** | **Not yet.** No file has been sent |

### Matches exactly — verified

Layout, crypto, tone tables, truncation, state transitions, validation rules, delay
constants, sequence allocation. Twenty areas, checked against legacy source,
production data, or the vendor guide, plus 26 point-by-point agreements against
Mr. Kang's handover.

The commit-then-upload ordering that the refactor had inverted was **restored** on
2026-07-31 — legacy commits at `:696-697`, uploads at `:700`, calls 00301 at `:721`,
and two-phase dispatch now does the same.

### Deliberately different — the approved architecture

Four cron batches become one scheduled job plus request-time events. BATCH2 folds
into the 01301 handler. Callbacks move from JSP actions to REST controllers. All
approved as K9, and the refactor implements it faithfully.

### Deliberately different — where legacy is wrong

Matching legacy in these places would reintroduce a defect that has *demonstrably*
failed in production:

| Legacy behaviour | Evidence it fails |
|---|---|
| Writes the literal characters `null` into the bank file on overflow | Short, misaligned record sent with one log line |
| `tot == 0` satisfies `tot == succ` → "all success" with nothing terminal | **2 live PROD instances** |
| `'E'` never matches `'EBANK'` → payment fails | 4 payments failed or stranded (JEX **DEV**; PROD unmeasured) |
| Blind 00302 resend on transient failure | No way to ask the bank whether the first executed |
| BATCH2's query excludes `PROC_STEP='2'` — no detection, no retry | **Produced the 35-file, 477-line backlog** |
| Tone gate misses `Ơ ơ Ư ư` | Vendor pitfall ④: untone-stripped values are rejected |
| Per-request delay leaks across rows | One Woori row pins 3000ms onto every later bank |
| Result-check keyed on the date the bank echoes back | Zero-row update, silent |
| No MAC verification on the 203 response | Tampered envelope written as a payment outcome |

Full list of 22 in the do-not-port section below.

### Not yet proven

Everything above was established by reading source, comparing against production data,
and checking the vendor guide. **No bulk file has reached a real bank.** SFTP is off,
`MockEfsApiClient` is in place, B4 unprovisioned.

Four checks need one real transaction:

1. One bulk file end to end through SIT
2. The `tb_firm_file_data.rs_cd` query — no test in the suite executes JPQL, so the
   new `substring` predicate has never run
3. A byte-comparison of a legacy-generated file against a refactor-generated one
4. First domestic VND 202, which surfaces the F-4 FX-block question immediately

**Short answer:** it matches legacy everywhere that matters, differs only where
legacy is broken or where the architecture change was approved, and has not yet been
tested against a bank.

---

## ✅ Verified correct — nothing to do

Checked against legacy source, production data, or the VAN Center guide. No action.

| Area | How it was verified |
|---|---|
| 1000-byte H/D/T layout | Field-by-field vs legacy; 998 + CRLF on all three records |
| The 2025-10-29 field change | `feeBrdnClsCD` at 815 **before** `rcptUseAddr` at 818 — the reverse of declaration order, correct |
| Crypto | AES/CBC/PKCS5, all-zero 16-byte IV, HMAC-SHA256, 14-char `RQ_DTIME` prefix |
| Vietnamese tone tables | md5-identical across all four copies in both trees |
| Byte truncation | `byteTruncate` ≡ `getStrByteMaxLen`, including edge cases |
| `PROC_STAT` / `PROC_STEP` transitions | 6/6 verified |
| Gate A validation | All six network/currency rules, line for line |
| Per-bank delay values | Woori 3000ms, Shinhan/default 100ms — identical constants |
| Sequence allocation | Funnelled through `wb_get_next_val`; `TNO` is the allocated `TRAN_SEQ` |
| Realtime rail in production | **0 stranded 202 rows** — the 203 loop has always worked |
| API-layer charset | UTF-8, confirmed at guide §1.2 |
| HTTP headers required | `Content-Type` only — 44 pages, zero mentions of the others |
| D-1 prefix premise | PROD: data rows `S_` (5,472), result rows `R_` (4,434) |
| S-1 `ant_div_cd` provenance | We read the decrypted EV everywhere; legacy's envelope/EV split was **not** inherited |
| S-3 count reconciliation | We compare numerically; legacy's `String.equals` was not ported |
| S-2 callback date | Derived from the filename — API2's side of the asymmetry, deliberate |
| Index scripts | None duplicates an existing index (DEV verified) |

Plus 26 point-by-point agreements against Mr. Kang's handover.

---

## ✅ Fixed and tested

| ID | Finding | Fix |
|---|---|---|
| C-1 | Duplicate 00302 to the bank | Per-sibling `REQUIRES_NEW`, marker committed before the call, `RuntimeException` caught |
| C-2 | 01301 discarded, bank told to stop resending | Three-way branch; `RC=0000` only for a genuine replay |
| C-3 | Split-file barrier released early | Two-phase dispatch — all rows committed before any egress |
| C-4 | External I/O inside transactions | Two-phase dispatch |
| C-5 | Callback errors used the wrong envelope | `EfsEnvelope` on all error paths |
| C-9 | `ResultCheckJob` single-gated | `enabled` flag added; misleading Javadoc corrected |
| C-11 | Lock ceiling shorter than a full batch | PT60M → PT2H30M, test asserts the relationship |
| C-14 | Unguarded field overflow | Entry-time wire-width guard, NFC normalisation, no truncation |
| H-2 | Permanent 00302 failure left state at 6/1 | Now writes `2`/`3` + pay file `3`, matching legacy |
| H-4 | G999 wrote FAIL prematurely | Was already fixed; the audit was stale |
| D-1 | **Per-line results silently discarded** | Prefix strip applied to the detail lookup; test fixture split `S_`/`R_` |
| D-3 | File-round wrap missing | 99 Woori / 999 others, reset-to-1, per split file |
| D-4 | Envelope RC over-reaching | Reverted to `0000` on durably-recorded paths |
| D-8 | Base64 alphabet repair unported | Ported to both `decryptEv` and `verifyVv` |
| F-1 | Read timeout below the vendor SLA | 20s → 60s (guide §3.2 states 30s normative) |
| X-1 | Blocked-char guard dropped rows silently | Now reaches `PROC_STAT='3'` + ops alert on both rails |
| R-F | Callback lookup keyed on the clock | Derived from the filename |
| R-G | No wire-level charset assertion | Non-ASCII scanner warns and alerts |
| — | **False "all success" finalisation** | Two guards; `tot == 0` no longer satisfies `tot == succ` |
| — | Stranded `2`/`6` files undetected | New `BULK_EXECUTION_RESULT_NOT_RECEIVED` cause |
| — | Monitor classifier date-scoped | Switched to the date-proof predicate |
| — | Sweep went blind at midnight | Two-sided `PROC_DTTM` window, 7-day retention |
| — | Alerting disabled everywhere | Enabled in SIT and UAT |
| — | `grp-id` hardcoded | Configurable, legacy value as default |
| — | Result-check pass unbounded | PT10M per-pass deadline |
| — | Config: 169 hidden yml defaults | All converted to bare `${VAR}`; four profiles complete |

Also: `.env.batch.example` renamed to a docs path (nothing read it), F-6 stale-claim
corrections across 8 documents, and the `SchedulerConfigurationContractTest` rewrite.

---

## 🔧 Code remaining — CLOSED

All eight items from the 2026-07-30 list are done except the last, which is
housekeeping. Each is marked against legacy below.

| # | Item | Status | Match against legacy |
|---|---|---|---|
| 1 | `TB_PAY_TARGET` `'9'` sweep in the monitor | ✅ Built | **No legacy equivalent.** Legacy needed no detector — its cron re-selected `'9'` every minute, so a lost dispatch self-healed. Removing the poller removed that, so this is a replacement for a guarantee legacy got from its schedule |
| 2 | Per-bank pacing on the 202 path (C-6) | ✅ Built | **Restores legacy behaviour.** `NAPAS_REAL_TIME_BATCH:698` paced every 202 with an unconditional tail sleep. We now pace per row on a single-threaded drain — *more* correct than legacy, whose `per_request_delay` leaks across rows (D-A). Values verified identical: Woori 3000ms, Shinhan 100ms |
| 3 | Restore `x-coocon-accessIp` | ✅ Restored | **Matches legacy.** `ConnectionUtil:191` sends it; we had dropped it. Port includes `getLocalServerIp()` step for step. `Authorization`/`signature` deliberately still absent — hardcoded placeholders (`auth12345`/`sign12345`) the gateway ignores per handover §8.5.5 |
| 4 | Connect timeout 3s → 30s | ✅ Applied | **Matches legacy** `ConnectionUtil:16`. Read timeout already 60s, matching `:17`. Both had come from the vendor PDF's §6 sample, which the guide marks non-binding |
| 5 | S-11 — read `IDO.TB_FIRM_BAT_API_TRAN_U001.xml` | ✅ Read | My hypothesis was wrong — the `WHERE` does include both columns and both call sites supply them. **The real defect is different:** `apiCall` throws before `updateApiTran`, so the audit row keeps `RS_CD` NULL on exactly the failures an audit exists for, and `:723-726` is dead code. Not ported — we finalise on every branch |
| 6 | S-12 — read `jex.util.date.DateTime` | ✅ Dismissed | No divergence. The format string is lowercased first and `mi` maps to minutes, so lowercase `mm` was always months. Legacy's mixed `"yyyymmdd"` / `"YYYYMMDDHH24MISS"` usage resolves identically |
| 7 | Lock ceiling PT2H30M → PT30M | ✅ Applied | **No legacy equivalent** — ShedLock is refactor-only. The ceiling now tracks the PT10M pass deadline rather than batch size, so it stops growing every time a timeout changes. Post-crash blind spot: 150 min → 30 min |
| 8 | Save + renumber this document | ⬜ **Outstanding** | Housekeeping. `docs/audits/wecms-batch-independent-audit-2026-07-30.md` — 87 citations point at it |

### Two further changes since that list, both toward legacy

| Change | Match against legacy |
|---|---|
| **Two-phase dispatch** | **Restores legacy ordering.** Legacy commits at `:696-697`, uploads at `:700`, calls 00301 at `:721`. The refactor had inverted it — commit last. Now phase A commits all sibling rows before any egress, phase B dispatches per file with no transaction spanning an external call |
| ~~**`PER_DELAY_DEFAULT` 100 → 3000ms**~~ **WITHDRAWN 2026-08-05 — reverted to 100ms** | The premise ("legacy never had a considered value") was **false**. Handover 05 §5.3 documents `기본 딜레이 100ms` twice and 06 §6.3 lists `WCConst.PER_DELAY_*` as `100/3000ms` — two values, not three. **Now matches legacy exactly** on all three constants. See "`PER_DELAY_DEFAULT` — the 3000ms recommendation withdrawn" below |

**Net position on the dispatch subsystem: code-complete.** Of the eight items above,
four restored or matched legacy exactly, two were reading tasks that found no
divergence to fix, one has no legacy counterpart, and one is a document to file.

---

## ⚙️ Needs IT / infrastructure

**Reframed 2026-08-01.** This section said "provisioning — obtain these values." That
was wrong for most rows. Legacy runs in production today, so the values already exist
— in `wecms_batch.properties`, or in the shared JEX database. Verified against legacy
source; corrected below.

| Item | Was | Is | Where the value already lives |
|---|---|---|---|
| **B2** — `TB_VAN_API_INFO.API_KEY` | "Blocks all EFS calls and all ops alerts" | **Not a vendor request.** `CommUtil.getApiKey` and `VanApiKeyService` run the identical query against the same shared JEX table. UAT/PROD point at the live JEX DBs, so the real key is inherited with **zero provisioning** | JEX UAT/PROD `TB_VAN_API_INFO`. DEV verified: 3 rows, all keyed, length 32 |
| **Gate B** — `TB_BATCH_SCHEDULE` on UAT/PROD | "Unseeded → every bulk transfer fails" | **Satisfied by construction.** Legacy reads the same two tables and dispatches daily, so the rows are there. The seed script was scoped to the refactor's *own separate* database — which only SIT has | JEX UAT/PROD `TB_BATCH_SCHEDULE_INFO`/`_DTL`. DEV verified: 4 INFO + 12 DTL, identical windows |
| **EFS base URL** | "Empty in both templates — BLOCKING" | **In the repo.** `U_API_URL` and `R_API_URL` are in `wecms_batch.properties` | Repo. PROD is `van-gw.webcashvietnam.com`; UAT points at `dev-van-gw` — worth asking whether that is still intended |
| **B4** — SFTP | "Obtain credentials from the VAN Center" | **Eight of nine values recoverable from the repo.** The private key file is the only genuinely out-of-band item. Password is not an item — legacy supplies no passphrase | `wecms_batch.properties` `{T,U,R}_SFTP_*`, already copied into `.env.local`/`.env.sit` |
| **Gate H** — index scripts | Implicitly a parity gap | **Not a parity gap.** Legacy never `SELECT`s `TB_FIRM_BAT_API_TRAN` — INSERT ×4, UPDATE ×2, no R-series IDO exists. Its zero indexes are no precedent for our query | New DDL on shared legacy tables. Still apply before UAT/PROD |

### Real remaining infrastructure work

1. Place the SFTP private key at `~/.ssh/id_rsa`, 0600, owned by the process user
2. Set `WECMS_SFTP_USERNAME=GV00000001` — empty today, and `JschSftpClient` throws at
   first use
3. **Confirm which OS account runs `wecms-api`.** `{ENV}_SFTP_CERT` is dead config —
   `SftpUtil:59` ignores it and hardcodes `$HOME/.ssh/id_rsa`, so the effective path
   follows the account
4. Set `WECMS_CALLBACK_ALLOWED_IPS` on the SIT host, or every callback 403s
5. Four `SELECT`s: `TB_VAN_API_INFO` and `TB_BATCH_SCHEDULE` per environment
6. Apply both index scripts before any UAT/PROD leg

### Three things found in the reframing

**PROD's SFTP host is `42.96.55.18`, not `42.96.55.163`.** Different from DEV and UAT.
Anyone copying the DEV value forward points PROD at the wrong gateway.

**`WECMS_EFS_PARTNER_ID` was a stale blocker.** The checklist carried it as BLOCKING
for all environments; nothing reads it in source or build output, no YAML carries it,
and a contract test enforces its absence across all four profiles. Retired 2026-07-29
(D-2). The envelope `ID` is unconditionally `TB_FIRM_CONF.VAN_ORG_CD` — there is no
fallback, that phrasing describes pre-retirement code. The vendor's `prtn_id` is a
separate per-account body field from `BANK_PRTN_ID`.

*Why the retirement was right:* `BANK_PRTN_ID` is per-withdrawal-account — the
`TB_FIRM_CONF` PK is `(biz_key, out_bank_cd, wdrl_act_no)`, and JEX DEV holds two
different values under one tenant and bank. A single static property could only ever
have been correct for one account.

**Junk data in `TB_FIRM_CONF`, and it feeds the wire.** Three rows for tenant
`VN_VNM0004_000001` carry `van_org_cd='34534534'` and `bank_prtn_id='434534'`. Those
two columns now feed the envelope `ID` and the body `prtn_id`, so a row like that
sends a nonsense partner identity on every EFS call for that account. Check before
UAT:

```sql
SELECT biz_key, out_bank_cd, wdrl_act_no, van_org_cd, bank_prtn_id, org_stat
  FROM tb_firm_conf ORDER BY biz_key, out_bank_cd, wdrl_act_no;
```

### And the item that outranks all of them

**C-1 — nobody has read the actual running configuration off a deployed host.**
Knowing a value is recoverable says nothing about what a running node is configured
with. Seventeen live EFS calls were made between 2026-07-22 and 07-24 from a node
whose committed config said `false` for both flags. Step 0 of the pre-first-transfer
sequence exists for this, and it stays the top blocker.

---

## 📞 Needs VAN Center — two open questions, neither blocks SIT

**Retitled 2026-08-01.** This section read "nothing blocking." A verification pass
found two rows were masking genuine vendor questions, and three rows were wrong.

| Row | Status | Corrected |
|---|---|---|
| `BT00`/`BT01`/`C999`/`G999` | ✅ Holds | Legacy defines them by usage. `BATCH2:232` transient = `{BT01, G999, C999}` + empty body; `:242` success = `{0000, BT00}`; all else permanent. We match all three sets |
| Callback RC semantics | ❌ **Premise false** | "Only a throw changed the RC" is **API1-only**. Legacy API2 answers non-`0000` on five *non-throwing* early returns. Parity holds on the three business-outcome paths; five code divergences beyond that |
| Fixed-length charset | ❌ **"Moot by construction" is false** | Two live holes: `rcptUseAddr` (offset 818, no guard either side, legacy-faithful) and the ERP path (no entry layer). `FixedLengthCodec:75-80` already said so — this memo contradicted its own code |
| Retry | ❌ **Backwards** | Legacy **did** poll-retry the 00302 leg — `BATCH2:232`'s `continue` returns the row to the `1`/`6` selection and the next minute re-sends. Our no-retry is an approved TO-BE divergence (K9), not parity. The vendor separately forbids blind resend, which we honour — that is vendor compliance, not a legacy match |
| Operating hours | ⚠️ **Two claims, split** | Matches legacy (`000000–235959`, verified live). Diverges from the vendor — guide §3.3 declares `00:15–23:50` in the gateway's own clock, ~25 min/day outside declared hours, zone unpinned |
| 2-pass decode | ✅ Holds | Single pass, documented, gated on B5. `repairBase64Alphabet` ported and explained by the §6.2 `EncryptAesBase64` URL-decode-on-encrypt bug |
| `FIRM_FILE_NM` naming | ✅ Holds | Token for token — `BATCH1:540` vs `BulkReceptionService:587` |

### The two questions

**q1 — does a non-`0000` callback RC drive a gateway resend?** The guide neither
enumerates the RC values a partner may return nor states what the gateway does with
each; §3.8's diagram draws the callback response with no retry branch.

*What it decides:* our 01302 unknown-file branch answers `0000` where legacy answered
`G999` — **ours is the more permissive**, telling the gateway to stop resending where
legacy told it to continue. If the gateway does resend, legacy's `G999` was a real
recovery mechanism we dropped.

**q2 — which clock do the `00:15–23:50` hours run in?** §3.3 says "웹케시 베트남
시스템 시간" without naming a zone. The window is 25 minutes; the JEX frame DB runs
Asia/Seoul while operations are ICT — a 2-hour offset swallows it whole. A narrowed
window set in the wrong zone is worse than no window. Blocks UAT/PROD sign-off via
Gate B-2.

### The flip is deferred, deliberately

A blanket `G999` on the 01302 unknown-file branch would be wrong even if legacy does
it. Once a file reaches `PROC_STEP='3'`, every replay misses the `'2'`/`'6'` lookup —
so `G999` produces a **deterministic resend loop with nothing to converge on**.
Legacy tolerated it because its poller re-selected work each minute; we removed the
poller.

If q1 says the gateway resends, the correct change is to port the 01301 split — probe
by name without the state predicate, then `0000` for a genuine replay and `G999` for
ambiguous or missing — not to flip the line.

**Applied now, independent of q1:** the discarded-01302 case (no row at all) was the
only unmatched-callback condition in the dispatch tree producing no operator-visible
signal. It now logs at error and raises an ops alert. Acknowledgement unchanged.

*Caught during that change:* the first probe used an exact-name finder, which returns
zero rows for **every** 01302 callback — the table holds `S_` names, the callback
carries `R_`. It would have alerted on the routine path. The D-1 prefix asymmetry
reappearing in new code written by someone who knew about it.

### One internal inconsistency, recorded

Two callback legs give opposite answers to the same question. 01301 chose a retryable
error with its reasoning written out; 01302 chose `0000` with no equivalent note.
That is ours, not a legacy divergence, and it is now documented on both classes as one
decision with two answers.

---

## 🧪 Needs a real transaction

Everything above was verified by reading. These need a file to reach a bank.

| # | Check | Why |
|---|---|---|
| 1 | One bulk file end to end through SIT | The single highest-value test remaining |
| 2 | `SELECT count(*), count(rs_cd) FROM tb_firm_file_data WHERE tran_dt = today` | `rs_cd = 0` with rows present means D-1 is not actually fixed. **No test in the suite executes JPQL** |
| 3 | Byte-compare a legacy-generated file against a refactor-generated one for identical input | The strongest possible parity evidence |
| 4 | First domestic VND 202 | Surfaces F-4 immediately if the FX block is enforced |

---

## 📋 Legacy defects — deliberately NOT ported

Re-examined 2026-08-01 against the premise *"legacy runs in PROD and handles all
payments, so matching it may be safer than fixing it."* That premise is right for some
items and wrong for others, so the list is now graded by evidence rather than
asserted as a whole.

**Result: zero items warrant reverting.** Two came close and are priced below.

An honest caveat that applies to all of them: legacy is proven in PROD; the refactor
dispatch path has never run in PROD at all. "Our fix is unproven" is true of every
row, not just the ones marked so.

### Group A — revert to legacy ✅ **CLOSED — both resolved 2026-08-01**

**Empty, and it stays empty.** No item cleared both bars — never fired in PROD *and*
our direction is the riskier one. The two near-misses were both closed **without**
reverting, each with a better remedy than a straight revert would have given.

| Item | Resolution | Status |
|---|---|---|
| **D-E** — no MAC verify on the 203 response | **Gated, not reverted.** `ResultCheckService` verifies VV, accepts an *absent* VV with a WARN, and still throws `G003` on a present-but-wrong one. Tampering cannot slip through; a missing field cannot strand the rail. Reverting would have meant accepting a tampered envelope's `org_resp_cd` as a payment outcome | ✅ **DONE** |
| **D-A** — per-request delay leaks across rows | **Fixed structurally, by the per-row keying alone.** `DispatchPacer.perBankDelay` derives the delay from the row it is given; no variable spans iterations. The *second* remedy applied here — raising `PER_DELAY_DEFAULT` 100 → 3000ms — was **withdrawn 2026-08-05** once the handover was read: it rested on a false premise and was a throughput regression. **The finding stands; only the constant was wrong.** See below | ✅ **DONE** (constant reverted) |

**Verification.** `DispatchPacerTest` pins all three constants — Woori 3000, Shinhan
100, **default 100** — and the connection was re-proved in both directions on
2026-08-05: setting the default to 3000 turns **8 of 11** tests red
(`everyOtherBankPacesAtTheLegacyDefault` ×7 + `wooriOverrideIsNotTheDefault`),
restoring 100 returns the suite to green.

*The earlier note here read "default 3000 … reverting to 100 turns 7 of 10 red" — the
same mechanism pointing the opposite way, from when 3000 was the committed value. The
count moved 10 → 11 because `wooriOverrideIsNotTheDefault` was added specifically to
catch a future re-generalisation of Woori's floor.*

**One residual on D-E, and it is an observation rather than a task.** The gate exists
because nobody knows whether `WGVN_EFS_203` returns a VV — that leg has never run
against the live gateway. Runbook §5.1a-2 carries an answer row, and **result-check
stays SIT-only until it is filled.** If the 203 response carries no VV, the
accept-with-WARN branch is what keeps the entire result-check rail from failing every
row on its first run. It is the one place a SIT observation changes a UAT decision.

### Group B — keep, legacy demonstrably fails

**Three rows, down from four.** The `ANT_DIV_CD` row was withdrawn 2026-08-01 — see
below.

| Item | Evidence | Env |
|---|---|---|
| `tot == 0` satisfies `tot == succ` → pay file reports ALL SUCCESS over a file that never settled | **2 live instances: `20251128` (9 lines) and `20251201` (2 lines)**, both parent `PROC_STAT='0'` | **PROD** |
| Legacy has no detection and no retry from `PROC_STEP='2'` — BATCH2 selects `'1'` only | **35 files, ~477 lines, 2025-08-14 → 2026-05-29.** DEV corroborates the shape | **PROD** |
| 01301 lookup pinned to today's date while 01302 takes it from the filename | The refactor reproduced legacy's shape and stranded every midnight-crossing callback until FIX A | DEV/SIT |

#### ⚠️ Withdrawn from Group B — `'E'` vs `'EBANK'` (D-7 / B-2)

The row read: *"`'E'` never matches `'EBANK'` — a 5-char literal against a `bpchar(5)`
column carrying both widths. 2 rows FAIL, 2 stranded."*

**Both halves were wrong.**

**The column is not carrying two widths as drift.** There are two enums, not one.
`AntDivCdT` (all 1-char: E/N/C/S/V) governs `TB_PAY_FILE_DATA`, the 1-byte wire slot.
`AntDivCd` governs `TB_PAY_TARGET` and is **deliberately mixed** — `INTER("INTER")`,
`NAPAS("NAPAS")`, `CITAD("CITAD")` sitting in the same enum body as `SWIFT("S")`,
`EBANK("E")`, `VCB("V")`. All 101 DEV rows conform. `'E'` is a **correct value**, not
corrupt data, and `"INTER".equals(...)` at `NAPAS:139` reads a column that genuinely
stores `INTER` in 28 rows.

Three columns, three vocabularies, all correct in place:

| Column | Width | Vocabulary |
|---|---|---|
| `TB_PAY_TARGET` | `bpchar(5)` | `AntDivCd.getValue()` — INTER/NAPAS/CITAD/E/S/V |
| `TB_PAY_FILE_DATA` | `bpchar(1)` | `AntDivCdT.getValue()` — E/N/C/S/V (the wire slot) |
| `TB_BATCH_SCHEDULE_DTL` | `bpchar(5)` | channel names — EBANK/NAPAS/CITAD/SWIFT/VCB |

**Measured on JEX DEV — every row conforms.**

| Table | Column | Distinct values | Vocabulary |
|---|---|---|---|
| `tb_pay_target` | `character(5) NOT NULL` | CITAD 39 · NAPAS 30 · INTER 28 · **E 2** · **S 2** | `AntDivCd.getValue()` |
| `tb_pay_file_data` | `character(1)` | N 106 · E 102 · S 86 · C 29 · V 2 · null 19 | `AntDivCdT.getValue()` |
| `tb_batch_schedule_dtl` | `character(5) NOT NULL` | CITAD 4 · NAPAS 4 · SWIFT 2 · EBANK 1 · VCB 1 | enum **names** |

101 of 101 `tb_pay_target` rows are exactly `AntDivCd.getValue()` for the five
constants that occur. The column is `bpchar(5)` because INTER/NAPAS/CITAD need five
bytes — not because every value is five characters.

**The schedule table is a third vocabulary, and neither enum's.** It stores `EBANK`,
`SWIFT`, `VCB` — the enum *names*. `AntDivCd` would store `E`, `S`, `V`. So the DTL
key is a distinct namespace that both systems normalise *into*, which is why
`operTimeChk` takes hard-coded names rather than passing a stored value through.

It also has **no application writer**. All twelve DEV rows carry
`reg_dttm='20251113095800'` and an empty `last_edtr` — a legacy DBA insert. Our seed
script reproduces the same names.

**Where the value originates.** `SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD` (varchar 5) is
written by `PymnHelper:1486` with an `AntDivCd`, and `TB_PAY_TARGET` inherits it
verbatim at `:2211`/`:2619` — same on our side
(`ExpenseDispatchExecutor:384-390`, `InternalTransferDispatchExecutor:259`). Only
`INTER` is written directly, on the same-bank branch.

**And our fix does not fix the cited rows.** `20250918` `tran_seq` 13219/13220 did
fail at `PROC_STAT='3'` — but because there is no
`NAPAS_REAL_TIME_BATCH / 970424 / EBANK` DTL row. Legacy passes `'E'` verbatim and
misses; we canonicalise to `'EBANK'` and **also** miss. Identical outcome. The
`OperatingHoursService:190-193` comment claiming otherwise has been corrected.

**What is actually true, and what remains open:**

- `fromStorageCode` (bulk path) is **parity**, not a divergence. Legacy performs the
  same `E → EBANK` expansion itself at `FILE_DEPOSIT_TRANSFER_BATCH1:301-381` —
  1-char counts out of `TB_PAY_FILE_DATA`, then `operTimeChk` with hard-coded 5-char
  names. Keep.
- `canonical` on the **realtime** path is a genuine divergence. Legacy
  `NAPAS_REAL_TIME_BATCH:144` passes the value verbatim; we expand first. **Inert
  today** — no EBANK/SWIFT/VCB realtime DTL row exists, so both forms miss.
- **It is armed, though.** Adding one `TB_BATCH_SCHEDULE_DTL` row would make the
  refactor dispatch payments legacy refuses — a money-path behaviour change triggered
  by a DBA insert into a table the live JEX batch reads, with no code review and no
  deploy.

**Recommendation: remove `canonical` from `evaluateRealtime` and match legacy
verbatim**, so enabling an EBANK realtime channel costs a code change rather than one
`INSERT`. The justification for keeping it has fully collapsed — commit `c522720` has
no rationale, the stated goal rested on a false premise, and the audit that proposed
it already admitted the four motivating rows still fail.

Not changed — it alters whether a payment dispatches, so it is Mr. Jung's call.
Interim guard already in place: the seed script carries *do not add an
EBANK/SWIFT/VCB realtime DTL row*.

**Two false comments found and corrected in the same pass:**

- `AntDivChannel:27-30` claimed *"'N' missed the 'NAPAS' schedule row and the payment
  was failed."* `TB_PAY_TARGET` holds **zero** `'N'` rows — it stores `NAPAS`. The
  scenario does not exist. Replaced with the three-vocabulary map.
- `OperatingHoursService:190-193` cited `tran_seq` 13219/13220 as the motivating
  failure and implied canonicalising fixes them. It does not. Now states what the
  change does achieve (padding strip, gate-spelling alignment) and what it does not.

**Six documents carried the old framing** and were corrected: the boundary sweep
(D-7 box, §21/§31 index labels, B-2 reclassified as *not a defect*), the business
parity audit §4.5 — which asserted *"those rows now dispatch"*, false — the operating
hours audit §12b, `payment-dispatch-final-readiness.md` §2.3, the seed script header,
and the timezone memory.

One extra site surfaced in that sweep: **D-L cited D-7 as precedent** — *"legacy holds
two mutually inconsistent encodings of one rule."* Withdrawn. Legacy is not
inconsistent on `ANT_DIV_CD`; D-L stands on its own basis.

### Group C — not portable

`match legacy` is not an available option for these.

| Item | Why |
|---|---|
| **D-G** — 01302 never commits on the success path | Relies on JDBC connection-close flushing. Spring rolls back a connection returned with an open transaction. Porting it loses every write |
| **D-H** — commit after rollback | JEX tolerates it as a no-op; Spring's transaction manager throws on a completed transaction |
| **D-F** — short-read misframing (`sz` read, never used) | The refactor parses through `FixedLengthCodec`, not a raw fixed-buffer loop. Matching would mean *authoring* new defective code, not retaining existing behaviour |
| **D-I** — `ant_div_cd` read from the envelope | The envelope has no such field, so legacy's returned value is always null. Porting = returning null by design |

### Group D — keep, no evidence either way ✅ **SETTLED**

Twelve items. Never observed firing, portable, and our direction carries no identified
added risk. **Two required action; both done. The rest needed a decision, and it is
recorded.**

| Item | Decision | Action |
|---|---|---|
| **D-A** — per-request delay leaks across rows | Keep | ✅ **Done** — per-row keying in `DispatchPacer.perBankDelay`, pinned by `DispatchPacerTest`. The `PER_DELAY_DEFAULT` 100 → 3000ms raise that accompanied it was **withdrawn 2026-08-05**; default is back to legacy's 100ms |
| **D-B** — result-check keyed on the bank's echoed date | Keep | None — we use `row.getTranDt()` |
| **D-C** — one bad row aborts the whole pass | Keep | None — `ResultCheckService` catches per row |
| **D-D** — `RC=0001` decrypts an EV that was never sent | Keep | None — already returns `notFound` before any decrypt |
| **D-J** — BATCH2 abandons the audit row on empty response | Keep | ✅ **Done** — mechanism and attribution corrected in the sweep |
| **D-K** — alert bodies that cannot say what failed | Keep | None in code. ① is a live decision — see below |
| **D-L** — tone gate misses `Ơ ơ Ư ư` | Keep | None — `VietnameseText` is ungated by design |
| **B-1** — unbounded, unordered, today-only scan | Keep | None — ours is bounded and ordered |
| **B-4** — 204 no-response strands the FX firm-tran forever | Keep | None — we record a terminal state |
| **B-5** — 204 error branch writes rate/amount from a failed response | Keep | None — not ported |
| **overflow → literal `null` in the record** | Keep | None — codec throws pre-egress |
| **blind 00302 resend** | Keep | None — K9, no automatic retry |

#### Three worth naming

**D-A** — the leak only mis-paces a bank outside {Shinhan `970424`, Woori `970457`},
and only those two have ever been on the 202 rail. VCB's four attempts all failed
before dispatch and it is now file-routed. So no bank receives 3000ms today that would
drop to 100ms under our change. Legacy's spacing was also order-dependent on an
unordered query — never a guarantee to revert *to*.

*The lever applied for it, and then withdrawn:* `PER_DELAY_DEFAULT` was raised
100 → 3000ms on 2026-08-01, then **reverted to 100ms on 2026-08-05** when handover 05 /
06 / 08 were read directly. The justification — "banks legacy never had a considered
value for" — was false; 100ms is documented twice in 05 §5.3 and again in 06 §6.3, and
05 §5.3 names BIDV and Kookmin as banks that process normally on it. **The structural
fix is the per-row keying, and that is untouched** — it is the half that was always
doing the work. Setting the default back to 3000 turns 8 of 11 tests red.

**D-B** — unobservable by construction. A date mismatch matches zero rows,
`DomainUtil.isError` does not catch that, and the batch logs success. *Never detected*
is not *never occurred.* Highest-severity silent skip in the set.

**D-L** — evidence withdrawn, finding retained. The two multibyte files predate tone
stripping by nineteen days and were a resubmission with an edited payload. So the gate
gap has **zero production instances**. It keeps its code basis — the gate ranges
genuinely exclude `U+01A0`–`U+01B0`, and handover pitfall ④ states the bank refuses
untone-stripped values — but the production correlation was coincidence.

#### One live residual — D-K ①

`API1`/`API2` gate their ops alert on `!"".equals(vanOrgCd) && !"".equals(CRYPT_KEY)`.
The VAN-key lookup runs *first*, so a key failure leaves both empty and the callback
fails **with no alert at all**.

**We inherited the shape.** `OpsAlertService.notify` returns early on a blank
`vanOrgCd`, and it must — the alert ships over `WGVN_MSG_SEND_001`, whose envelope
needs `vanApiKeyService.getApiKey(vanOrgCd)` to build EV/VV. No key, no envelope, no
message. The alerting channel cannot report its own failure.

Not ours to remove inside this channel. Two routes, both decisions rather than fixes:
ERROR-level logging with external log alerting, or a fallback VAN reserved for
infrastructure alerts.

*One stale premise to note:* this was recorded as "live-relevant because UAT has no
`TB_VAN_API_INFO.API_KEY` row (B2)." After the B2 reframing that premise is doubtful —
UAT points at the live JEX database, which legacy reads a key from daily. Confirm with
one `SELECT` before treating the UAT case as the motivating scenario. The circularity
itself stands regardless.

---

## Corrections to this document — 2026-08-01

Three claims here were wrong and are now fixed at source:

| Was | Now |
|---|---|
| `'E'` vs `'EBANK'` — "2 historical PROD failures" | 4 rows, JEX **DEV**. PROD incidence unmeasured and unmeasurable from here |
| D-J produced the 35-file backlog | It does not. Re-attributed to BATCH2's query excluding `PROC_STEP='2'` |
| D-A: non-Woori banks get 30× faster under our change | Empty population — no such bank has ever been on the rail |

And two documents were undercounting production:

`bulk-file-reconciliation-procedure.md` §8/§9 recorded **two** `PROC_STEP='2'`
incidents totalling 16 lines. There are **four**, totalling **27** — and the two it
omitted are precisely the ones whose parent pay file falsely reads `'0'`. An operator
working the sheet would have reconciled the wrong pair and trusted a false status on
the other two. Corrected.

`PayFileFinalizeGuardTest`'s Javadoc was correct and dateless; its **fixture** pointed
at `20260529`, which is *not* a `tot == 0` instance. Moved to `20251201`.

### Test-fixture convention, now documented

Three defects in this project were hidden by tests that were **correct in their
assertion and wrong in what they pointed at**:

- the shared `FILE_NM` constant that made D-1's `S_`/`R_` prefix mismatch unfailable
- the `anyString()` stub that fed one list to both sweep calls and doubled the count
- this fixture, whose wrong date then propagated into a sibling test, an assertion
  comment, and a decision report

Written up in `docs/testing/fixture-data-conventions.md`. The review question is
*"what did you change to watch it fail?"* — and **"nothing, it passed first time" is
the signal, not the reassurance.**

Applied to the new `DispatchPacerTest`: reverting the constant to 100 turned 7 of 10
tests red, then restoring 3000 returned the suite to green. The test is connected to
the constant in both directions.

---

## 🔴 Live production backlog — operations

Not a code item. Inherited from legacy, and legacy had no recovery path either.

**35 files · ~477 payment lines · 2025-08-14 → 2026-05-29**, all at
`PROC_STEP='2'`/`PROC_STAT='6'` — 00302 sent, 01302 never returned. Largest single
file: `S_GV00000006_SHB_002_20251010`, **412 lines, 5,101,563,524 VND**.

Two of them (`20251128`, `20251201`) have a parent pay file reading `'0'` — falsely
settled. And because `finalizePayFileStatus` only runs from the 01302 handler, those
two *did* receive a callback that was then lost in download or parse. Different
question for the bank than the other 33.

**Action:** confirm with the bank whether these executed. Do not trust
`TB_PAY_FILE_MST.PROC_STAT` for this set — re-derive from the firm-file rows.

---

## C-0 — Shared-database coexistence 🔴 **STILL OPEN — decision, not code**

**Blocker. Duplicate salary files to the bank; legacy production batch can be
aborted by refactor traffic.**

**Not live today.** SIT points at the refactor's own database
(`172.168.5.172/wecmsdevdb`), which is *not* the JEX frame DB
(`10.254.242.254/wecmsvndevdb`). So nothing in this finding is currently firing, and
the SIT deploy is unaffected.

**It becomes live at UAT and PROD**, because the recorded 2026-06-09 direction points
the refactor at the live JEX databases in every environment with no data migration.

### What is now known that was not when this was written

**Legacy's selections are predicate-free — verified.** `TB_PAY_TARGET_R001` takes **no
parameters at all** (the `<in>` block is empty); the WHERE is today's date plus
`PROC_STAT='9'`. `TB_PAY_FILE_MST_R001` binds two constants. No tenant, no origin, no
source column. Legacy processes whatever is `'9'` today, from any writer.

**Legacy's status updates are blind writes, not compare-and-swap.**
`IDO.TB_PAY_TARGET_U001` and `TB_PAY_FILE_MST_U001` carry no `PROC_STAT` predicate. So
our atomic `'9'`→`'6'` claim commits, legacy writes `'6'` straight over it, and neither
side notices — which is why the bulk rail has no interlock at all.

**The hazard is two drivers on one queue, not misattribution.** Our claims and
ShedLock coordinate only within the refactor; the legacy JEX batch participates in
neither. The failure mode is a duplicate payment.

**And legacy has C-12's gap too** — both selections are today-only, so a `'9'` row is
retried until midnight and then abandoned silently. Legacy never "solved" the
provenance question; it simply never encountered it, being the sole driver against its
own database.

### The trade cuts both ways, and both sides are now documented

The same shared-database arrangement that creates C-0 is what makes two other items
free:

| | |
|---|---|
| **Cost** | Two drivers on one `'9'` queue. Duplicate dispatch, and legacy's batch can be aborted by our uncommitted index entry |
| **Benefit** | **B2** — the real API key is inherited, no provisioning. **Gate B** — `TB_BATCH_SCHEDULE` rows are already there, no seeding |

That is worth putting in front of whoever decides, because "keep the databases
separate" is not free either — it means provisioning the key and seeding the schedule
per environment.

### A gate has been designed — proposed, not implemented

`wecms.dispatch.legacy-jex-shares-database`, defaulting **true**, fail-closed, with
`recovery.enabled` effective only when it is explicitly false, plus a startup line
naming the resolution and the JDBC host.

**Why a declaration and not an inference.** A JDBC-URL check answers *"where do we
point?"* The real question is *"where does the legacy batch point?"* — configured in
JEX property files on a different host entirely. And deployed config on these hosts is
already known to diverge from the committed `.env.*`, so a gate keyed on an
env-supplied URL would be as fragile as the flags it reinforces.

**Nothing in the code distinguishes the two cases today.** "Coexistence" appears only
in Javadoc and YAML comments — it is prose, not a guard.

### Legacy verification — 2026-08-01. Two findings that reframe this.

#### 🔴 Legacy is NOT single-instance. Its own races are live.

`jex.real.wecms_batch.xml` sets `jex-service-invoke-count="10"` with
`jex-thread-max-pool="10"`, the cron is `*/1 * * * *` with **no overrun guard**, and
there is **no lock of any kind** on the dispatch path — no ShedLock equivalent, no
advisory lock, no `SELECT … FOR UPDATE`.

So a run that exceeds sixty seconds overlaps the next tick, and both select the same
`'9'` rows. **Reachable at ordinary volume:** Woori paced at 3000ms per row means
twenty rows takes sixty seconds.

**This inverts C-0's framing.** It was written as *"the refactor introduces a
coexistence hazard."* What is actually true: **legacy has an unguarded concurrency race
in production today, and coexistence adds a second driver to a queue that already has
more than one.** Our atomic `'9'`→`'6'` claim is the only compare-and-swap anywhere on
this path in either system.

*Worth raising with Mr. Kang on its own merits, separately from the refactor.*

#### ❌ The two-line CAS mitigation is ruled out

`U001` is not a claim — it is the general-purpose status writer, with **11 call sites,
8 of them writing non-`'9'` transitions**: the 202 success path (`'6'`→`'0'`), both
failure paths (`'6'`/`'9'`→`'3'`), and the JSP cancel paths.

Adding `AND PROC_STAT = '9'` would silently break all of them — zero-row updates with
no error, because `DomainUtil.isError` does not catch a zero-row update. The recorded
two-line mitigation **must not be proposed to Mr. Kang in that form.**

**Better shape, same intent:** a *new* claim IDO — `PROC_STAT` in both SET and WHERE,
called only at the selection point, using legacy's own `getUpdateCount` idiom which is
already in use elsewhere. New file, existing paths untouched, no risk to the eight
working transitions.

#### ⚠️ The abort mechanism is confirmed, and worse than recorded

Not an immediate duplicate-key error. Legacy **blocks on our uncommitted index entry**
for up to **93 seconds** — 30s connect + 60s read + 3s pace — because our
`TB_FIRM_TRAN` insert sits in the same transaction as the EFS call. Then it takes the
duplicate key, `:528` throws, the outer catch at `:700` fires, and **the whole pass
aborts with every remaining row skipped.**

Previously recorded as 23s, from the pre-C-7 timeouts.

#### 💡 Row provenance may not need DDL

Both systems allocate through `wb_get_next_val`. A distinct sequence, or a reserved
offset range, would make rows distinguishable **with no schema change** on a table 40+
legacy IDOs read. Materially cheaper than the source column previously assumed.

### The decision, stated once

Three options, in ascending cost:

1. **Keep the databases separate.** Needs no approval — it is declining to do the
   unsafe thing. Costs the B2 and Gate B benefits above, and contradicts the
   2026-06-09 direction, so someone must know that direction had an unexamined
   premise: the flags do **not** gate row creation, so "all flags off" is not inert.
2. **Add the gate**, and set it honestly per environment. Small, and makes the
   assumption auditable.
3. **Row provenance** — a source marker or cutover timestamp on the two pay tables.
   The only option that makes genuine coexistence safe, and the one that also unblocks
   C-12's recovery half.

**~~Cheapest technical mitigation~~ — WITHDRAWN.** The recorded two-line change to
`U001` is ruled out: it is the general status writer, and the predicate would break
eight working transitions silently. See the verification above. The viable form is a
**new** claim IDO called only at the selection point — still small, still legacy-side,
but a new file rather than an edit to a shared one.

The original finding follows.

### The flag model is not a safety model

Write paths to `TB_PAY_TARGET` / `TB_PAY_FILE_MST` fall in three tiers:

| Tier | Path | Gate |
|---|---|---|
| 1 | `POST /api/v1/payment/*/{id}/dispatch` → five `*DispatchExecutor`s, `MANDATORY` propagation, write rows at `PROC_STAT='9'` | **none** |
| 2 | `RealtimeDispatchRegistrar:40-41` (`realtime-dispatch`), `DispatchAutoReceiveRegistrar:45-46` (`auto-receive`) | flag, false by default |
| 3 | `POST .../dispatch/callbacks/bulk-reception-result` and `.../bulk-execution-result` | **none** — `permitAll` at `SecurityConfig:80` |

Only tier 2 is gated. With every flag off and the refactor pointed at a JEX
database, tier 1 still writes `'9'` rows and the legacy batch dispatches them
within 60 seconds. The refactor silently drives legacy dispatch — not a race, a
straight hand-off nobody documented.

Tier 3 is likewise always mapped. `.env.batch.example` states it: the callbacks
are `permitAll` with no on/off flag, and payload VV HMAC is the only
application-level gate.

### Bulk rail — no interlock at all

Legacy `IDO.TB_PAY_FILE_MST_U001` carries no `PROC_STAT` predicate. It is a blind
write, not a compare-and-swap.

```
t0  legacy BATCH1:89   SELECT PROC_STAT='9' → row R held in memory
t1  refactor           claimForReception: UPDATE … '6' … AND procStat='9' → 1 row, COMMITTED
t2  legacy :451        U001 SET PROC_STAT='6' — no predicate → lands on top, no conflict signalled
t3  legacy :478        R004 re-check counts only PROC_STAT='3' → 0 → proceed
t4  both               build a file from the same TB_PAY_FILE_DATA rows
t5  both               SFTP upload → POST 00301
t6  bank               TWO reception requests for one payment file
```

`pk_tb_firm_file_mst` includes `firm_file_nm`, which the two systems generate
independently, so both inserts succeed. `BulkReceptionService:311-314` already
records that the PK cannot help — the insert commits after the upload and after
the 301 call.

Legacy's `R004` re-check at `:478` looks only for `PROC_STAT='3'`. It is a
failure check for its own split-file loop, not an ownership check.

### Real-time rail — an accidental interlock that fails open

Both systems key the 202 firm-tran row off the pay-target's `TRAN_SEQ` (legacy
`:273`/`:498`; refactor `RealtimeDispatchService:231-232`), so
`pk_tb_firm_tran (tran_dt, biz_key, tran_seq)` collides and only one 202 goes out.

This is a side effect of a shared key, not a guard, and it has three holes:

1. **Fails open on date disagreement.** Legacy recomputes `tran_dt` at `:489` from
   the JVM/DB clock; the refactor writes `utcNow()`. When the zones disagree on the
   date the PKs differ, no collision occurs, and two 202s move money twice.
   **This is C-10.** The timezone inconsistency is what disarms the only protection
   on the real-time money path.
2. **Reverse ordering removes it.** Legacy does not write `TB_PAY_TARGET` until
   after its POST, so between legacy's POST and its `U001` the refactor's
   `claimForDispatch` on `'9'` still succeeds.
3. **It protects the firm-tran row, not the pay-target row.** Legacy's `U001` is
   unconditional; final status is whoever writes last.

### The refactor can abort the legacy production batch

When legacy hits the duplicate key at `:493` it throws, and the throw sits inside
the `try` wrapping the entire row loop at `:70`. The whole run aborts and every
remaining `'9'` row that minute is skipped. Legacy also blocks up to 23s
(3s connect + 20s read) on the refactor's uncommitted index entry first.

Sustained refactor traffic against a shared DB is an availability risk to live
production dispatch, independent of the duplication risk.

### No mechanism prevents any of this

- **Database.** Complete constraint set is two primary keys. No `CHECK` on
  `PROC_STAT`, no partial unique index on an in-flight state. The only triggers are
  `AFTER INSERT OR DELETE OR UPDATE` audit-history writers — they record
  transitions into `_his` and cannot reject one.
- **Deployment.** The recorded 2026-06-09 direction is the opposite of a
  restriction: `wecms-api` must run against the existing JEX databases in all
  environments with no business-data migration. Legacy batch runs in DEV, UAT and
  PROD (`JEX_BATCH=true`). Today's safety is an accident — the refactor DEV/SIT DB
  (`wecmsdevdb`) is a different instance from the JEX frame DB (`wecmsvndevdb`).
- **Flags.** Real but partial, as above.
- **Row provenance.** Refactor rows are indistinguishable from JEX rows — shared
  global sequences via `wb_get_next_val`, no source marker. `_his` tables preserve
  both transitions, so this is forensically detectable but only after the fact.

### The documented plan does not cover it

`wecms-batch-activation-runbook.md` §5.1 has an 8-step sequence, and steps 4
(`AUTO_RECEIVE=true`) and 5 (`REALTIME_DISPATCH=true`) do cover request-time
dispatch. What is missing is a legacy-batch-off precondition on those steps. Every
such requirement in the docs is attached elsewhere — step 7 (recovery scheduler,
gate G, BLOCKED), BATCH2 decommissioning, or the operation-status batch.

§7.5 states the requirement and hands it back: rows are indistinguishable, so if
the DB target is shared, Ops and the PO must decide which system owns dispatch —
"a prerequisite, not a rollback step." The folder that would hold that decision,
`docs/refactor-wecms/migration/rollout-plan/`, contains only a README describing
itself as design intent.

Nothing anywhere orders `NAPAS_REAL_TIME_BATCH` or `FILE_DEPOSIT_TRANSFER_BATCH1`
off before step 4 or 5. No doc addresses tier 1 or tier 3 at all; every treatment
of shared-DB risk frames it as a scheduler problem.

### Options

**Hard precondition (required regardless).** `wecms-api` must not point at a JEX
database in any environment until legacy dispatch batches are off in that
environment, or the DB is verified separate. This is a precondition on the DB
target decision itself, not on steps 4–5.

**Cheapest technical fix is in legacy, not the refactor.** Adding
`AND PROC_STAT = '9'` to `IDO.TB_PAY_FILE_MST_U001` and `IDO.TB_PAY_TARGET_U001`
converts legacy's blind write into a genuine compare-and-swap, at which point
`claimForReception` / `claimForDispatch` actually win the row and coexistence
becomes safe on both rails. Two IDO XML lines in a system you own. Carries its own
production risk and needs Mr. Kang's decision, but it is far smaller than any
refactor-side change.

**Gate tier 1.** Add a flag to the dispatch executors so `POST /{id}/dispatch`
cannot create `'9'` rows when the refactor is not the owning system. Closes the
silent hand-off without touching legacy.

**Fix C-10 first if the real-time rail will ever coexist.** The accidental PK
interlock is the only thing preventing duplicate 202s, and the clock disagreement
is what breaks it.

---

## Root cause A — egress precedes commit

Legacy `FILE_DEPOSIT_TRANSFER_BATCH1` commits at `:696-697`, uploads at `:700`,
calls 00301 at `:721`. The row is committed and visible before the bank is told
anything.

`BulkReceptionService.buildAndDispatchFile`, inside one `REQUIRES_NEW` unit
(`:418`):

| # | Step | Line |
|---|---|---|
| 1 | `TB_FIRM_FILE_MST` insert at `PROC_STEP='0'/PROC_STAT='6'` | `:533` |
| 2 | `TB_FIRM_FILE_DATA` inserts | `:584` |
| 3 | Encode + write local file | `:627-629` |
| 4 | **SFTP upload** | `:641` |
| 5 | 00301 audit row | `:650-651` |
| 6 | **EFS 00301 HTTP call** | `:655-657` |
| 7 | Audit result update | `:659-662` |
| 8 | Commit | `:766` |

`BulkDispatchClaimTxService` does commit before egress, but only the
`TB_PAY_FILE_MST` row (`'9'→'6'`, `TbPayFileMstRepository:146-150`). It never
touches `TB_FIRM_FILE_MST` — the table the 01301 callback looks up. The
repository's own comment at `:132-135` records that legacy's real pre-egress guard
was the committed firm-file row and its deterministic `FIRM_FILE_NM` key.

Produces C-1, C-2, C-3 and half of C-4.

## Root cause B — dispatch moved onto the request thread

Legacy paced per-bank inside a background batch loop, where sleeping was free.
The refactor dispatches from `afterCommit` on the Tomcat request thread, where it
is not. Produces C-6, and constrains its fix.

---

## C-1 — Duplicate 00302 execution instruction to the bank ✅ **CLOSED**

**Was: Critical. A salary file can be executed twice.**
**Fixed 2026-07-30. Suite green. Detail of the fix at the end of this section.**

### Sequence

Three split files, all received normally:

1. Third 01301 callback releases the barrier at `BulkReceptionResultService:221-230`.
2. Fan-out loop `:231-236` calls `issueExecutionInline` per sibling. f1's 00302
   reaches the bank. f2's reaches the bank.
3. f3 throws a non-business `RuntimeException` — unprovisioned key
   (`IllegalStateException` from `VanApiKeyService`), crypto failure, serialize
   failure, or `RestClientEfsApiClient` transport error.
4. `issueExecutionInline` catches only `BusinessException` (`:308`). The 00301 leg
   catches both (`BulkReceptionService:680-686`); this leg has no equivalent.
5. `handle()` is plain `@Transactional` (`REQUIRED`, `:123`) spanning the method.
   All of it rolls back: f1/f2 revert to `PROC_STEP='1'`, their 00302 audit rows
   vanish, f3 reverts to `'0'/'6'`.
6. `BulkReceptionResultController:35-38` has no exception handling, so
   `GlobalExceptionHandler` returns HTTP 500 with a `WBE9999` `ApiResponse` body —
   not the `EfsEnvelope` the gateway parses. Gateway retries (*inferred* — see C-5).
7. Replay: f3 found at `'0'/'6'`, advances. Barrier releases. Loop matches f1 and
   f2 on `(step='1', stat='6')`. Guard at `:275-280` probes for audit rows rolled
   back in step 5, finds none, does not skip.
8. **00302 sent a second time for f1 and f2.**

### Why the documented guarantee fails

`BulkReceptionResultService:74-80` states the 00302 audit row is written before the
gateway call so even a no-response blocks re-issue. Written before, yes. *Committed*
before, no. `writeApiTran` (`:392-414`) is private with no propagation annotation
and joins the caller's transaction. `DispatchApiTranAuditWriter.saveInNewTransaction`
(`:37-40`) is `REQUIRES_NEW` and would satisfy the guarantee, but is injected only at
`BulkExecutionResultService:112` — the 01302 path.

### Confirmed regression against legacy

Legacy BATCH2 commits per row inside its loop: `beginTransaction` at `:238`, commit
at `:260-263` (success) or `:317-320` (error), rollbacks at `:255-257`, `:293-297`,
`:309-314`. Each file's transition is durable the moment its 302 result is known.
A mid-tick crash leaves earlier rows committed and later rows untouched — never a
partially applied tick. Blast radius: legacy 1 file, refactor N files.

*(Legacy note: the trailing `finally` at `:325-330` commits rather than rolls back,
so a row throwing mid-transaction has partial writes committed. A legacy defect,
not replicated, worth recording.)*

### ✅ Fix applied — 2026-07-30

All three items done, with one correction to the specification.

| # | Specified | Applied |
|---|---|---|
| 1 | Write the 00302 audit through `DispatchApiTranAuditWriter` so it commits before the gateway call | ✅ Done. The idempotency marker is now durable before the call, so a rollback cannot erase the guard that blocks re-issue |
| 2 | Add `catch (RuntimeException)` to `issueExecutionInline`, mirroring `BulkReceptionService:680-686` | ✅ Done. One sibling's non-business failure can no longer roll back siblings whose 00302 already reached the bank |
| 3 | Move the fan-out out of the callback transaction — per-sibling `REQUIRES_NEW`, **or** `afterCommit` | ✅ Done, and **both**, in the correct order — see below |

**My "or" was wrong, and the correction matters.** `REQUIRES_NEW` called *from*
`handle()` is unsound on its own: the nested transaction cannot see `handle()`'s own
uncommitted `PROC_STEP` 0→1 advance, so it re-reads `'0'`/`'6'` and skips the current
file — or writes through a merge of another transaction's uncommitted state.

What was built instead is `afterCommit` **then** per-sibling `REQUIRES_NEW`:
`BulkReceptionResultService` registers the fan-out on barrier release, and
`BulkExecutionInlineTxService` runs each sibling in its own new transaction. That also
matches legacy more closely than either alone — BATCH2 was a separate process reading
*committed* rows.

**Legacy's per-row boundary is restored.** Blast radius returns from N files to one:
a failure on sibling 3 no longer undoes siblings 1 and 2.

### Known residual — `afterCommit` is not durable

If the JVM dies between the commit and the hook, ready siblings sit at
`PROC_STEP='1'`/`PROC_STAT='6'` with no 00302 sent. C-2's `alreadyProgressed` branch
then returns `RC=0000` on the gateway's 01301 retry, so nothing re-drives them.

Stranding beats duplicate execution, so this is the right trade — but it is permanent,
and detection is the whole mitigation.
`StuckPayFileMonitorService.findStrandedBulkExecutionFiles` covers exactly this state.
**No re-drive was added** — that is K9 Option A, declined 2026-07-29.

### Related, found in the same work

**H-2 shares this catch block.** `BulkReceptionResultService:308` is the
`catch (BusinessException)` clause: H-2 is the defect when it catches, C-1 is the
defect when it does not. Two audits found one each; neither saw the other half. Both
fixed in the same change.

**And the legacy `finally` at `:325-330` commits rather than rolls back**, so a row
throwing mid-transaction has its partial writes committed. Recorded as a legacy defect,
not replicated.

---

## C-2 — 01301 reception result discarded, bank told to stop resending ✅ **CLOSED**

**Was: Critical. A dispatched file's reception result is lost permanently.**
**Fixed 2026-07-30, hardened twice since.**

`BulkReceptionResultService:147-158`. When the lookup returns empty, the method logs
at `info`, persists nothing — no audit row, no state change — and returns
`RC="0000" / RM="정상"` with rebuilt EV/VV (`:248-261`). The strongest possible
"processed, stop resending" signal.

The branch comment reads `// Idempotent replay / already-processed`, correct for a
genuine duplicate. But it also catches the race root cause A creates: the 00301 for
file k is sent at step 6 while file k's own row is uncommitted. A callback arriving
in that window finds no row, is acked `0000`, and is discarded with no durable trace.

The file then sits at `PROC_STEP='0'/PROC_STAT='6'` indefinitely. The bank will not
resend. Only `StuckPayFileMonitorService` would notice, and it is double-gated off.

The TO-BE design lists swallowed exceptions among the legacy defects not to be
replicated. This is a swallowed callback that additionally instructs the counterparty
to stop retrying — worse than what it replaced.

### ✅ Fix applied

`handleUnmatchedCallback` now splits the three cases the single branch was
conflating:

| Case | Response | Note |
|---|---|---|
| Row exists at a later `PROC_STEP` | `0000` | Genuine replay — the gateway should stop |
| No row with this `FIRM_FILE_NM` at all | **`G004`** | Retryable. Matches legacy `API1:168-170` |
| Row exists in an unexpected state | `G999` | Retryable, refactor-only |
| **More than one row** (added by FIX J) | `G999` + alert | No classification at all — see below |

Audit rows persist via `DispatchApiTranAuditWriter` for every case where a tenant can
be resolved, so a discarded callback leaves a durable trace.

**Two hardenings since.**

*FIX J* — `alreadyProgressed` was `anyMatch`, so under a `FIRM_FILE_NM` collision a
callback meant for file Y could be acked `0000` on the strength of file X's progress.
Now: more than one row means no classification, a retryable error, and an alert
listing every row's `FILE_SEQ_NO`, `BIZ_KEY`, `BANK_PRTN_ID` and state, plus the
callback's `prtn_id` — the field that would disambiguate.

*RC matching* — the unknown-file code moved `G999` → `G004` to match legacy.

**And the race this branch was catching is gone.** Two-phase dispatch means a 01301
can no longer arrive before its own file's row is committed, so the case that made
this critical no longer occurs. The branch remains as defence.

**One residual, deliberate:** for a genuinely unknown file no audit row is possible —
`TB_FIRM_BAT_API_TRAN.BIZ_KEY` is `NOT NULL` and part of the PK, and an unmatched
callback resolves no tenant. Guessing one would be worse than an invention. The ops
alert carries the record instead.

---

## C-3 — Split-file barrier releases early ✅ **CLOSED**

**Was: High. The bank executes part of a salary batch while the rest is still
dispatching. Fixed 2026-07-31 by two-phase dispatch.**

The barrier at `BulkReceptionResultService:221-230` is a correct port of legacy R003
in intent. But it sees only committed rows, and per root cause A, sibling k+1's row
does not commit until after sibling k's 00301 has gone to the bank.

Per-file dispatch spans encode, local write, SFTP upload and HTTP round trip —
seconds. Bank reception acks are typically sub-second. Whenever reception latency is
shorter than per-file dispatch time, the first callback sees an incomplete group and
the barrier releases immediately. Not intermittent under those timings; normal.

Legacy has the same structural gap but evaluates on an uncorrelated one-minute cron
that must randomly land in the window. The refactor evaluates on the callback the
just-sent 00301 directly caused.

Legacy intent, R003 comment: 전체 접수가 되지 않는 경우 요청을 하지 않는다 —
if not all are received, do not request.

### ✅ Fix applied — two-phase dispatch

Phase A commits **all N sibling rows** at `PROC_STEP='0'`/`PROC_STAT='6'` — plus the
encode and the group-wide blocked-char guard — before anything leaves. Phase B then
dispatches per file with no transaction spanning an external call.

The barrier now sees the complete sibling group on every evaluation, because every
row exists before the first 00301 goes out. This also restores legacy's ordering:
commit at `:696-697`, upload at `:700`, call 00301 at `:721`.

**Two judgement calls taken over the original specification, both better:**

*Encode moved to phase A.* It is pure local computation with no external effect, and
keeping it there preserves "over-width field ⇒ nothing committed." In phase B the rows
are already committed, so an over-width throw becomes a FAIL-write instead of a
rollback.

*Blocked-char guard made group-wide.* It now runs across the whole group before
anything commits, so a blocked character anywhere rejects the entire request. That
matches legacy's Gate A (`:113-266`), which validates the whole pay file before
splitting. Per-file rejection was accidental.

**One consequence to know:** a phase-B break at file 3 of 5 leaves files 4 and 5 with
committed rows at `'0'`/`'6'` that were never dispatched. Correct — the barrier needs
those rows — and the `PROC_STEP='0'` sweep is what surfaces them.

---

## C-6 — 202/204 path unpaced ✅ **CLOSED**

**Was: High. Woori's 3-second inter-request floor is not enforced on the money path.**
**Fixed 2026-07-31 by `RealtimeDispatchQueue`.**

### Legacy

Four `Thread.sleep(per_request_delay)` sites in `NAPAS_REAL_TIME_BATCH`, all inside
the per-row loop opened at `:82`:

| Site | Line | Branch | Paces |
|---|---|---|---|
| 1 | `:360` | 204 no-response | EFS 204 |
| 2 | `:481` | 204 non-0000 | EFS 204 |
| 3 | `:589` | 202 no-response / G999 | EFS 202 |
| 4 | `:698` | unconditional loop tail | EFS 202 |

Site 4 runs on both 202 success and 202 error, so every iteration issuing a 202
sleeps exactly once. Value re-selected per row at `:275-279` from `WCConst:17-19`:
Woori 3000ms, Shinhan 100ms, default 100ms.

### Refactor

`DispatchPacer` reproduces the values exactly but has one production call site:
`ResultCheckService:325-344`, the 203 scan. `RealtimeTransferService.transfer` (`:179`)
and `inquireFxRate` (`:313`) contain no sleep, no pacer, no limiter.

The Javadoc premise — event-driven legs "fire one request per business event, never
in a back-to-back scan" — does not hold in two places:

- `RealtimeDispatchRegistrar.registerAfterCommit:61-74` is
  `for (String tranSeq : seqs) { realtimeDispatchService.dispatch(...) }`. A
  multi-line approval commits N rows and fires N sequential 202s with nothing between.
- `StrandedRecoveryService.recoverPayTargets:168-176` is a genuine scan loop
  re-driving the 202 path, bounded by page size and serialized by ShedLock, but
  unpaced. Same shape as the 203 scan that *is* paced.

A 20-line Woori payment paced 60 seconds of gateway traffic in legacy. The refactor
issues it as fast as Woori answers.

Traceability drift: the pacer's Javadoc cites only `PROCESSING_RESULT_CHECK_BATCH`
as its legacy origin. `NAPAS_REAL_TIME_BATCH`'s four sleeps are not referenced.

### Why the fix is a design change, not a patch

Legacy could afford to sleep because pacing happened on a background batch thread.
`afterCommit` runs synchronously on the Tomcat request thread. Restoring legacy
pacing there means a user approving 20 Woori lines waits 60 seconds with a pinned
thread; at 50 lines against the 20s read timeout (C-7), worst case is minutes of held
thread and an exhausted pool under modest concurrency.

Pacing was not dropped by oversight — moving dispatch onto the request thread left
nowhere to put it. Restoring it requires an executor or queue between the commit and
the gateway. Raise as a design item, not a missing line.

Current bounds on 202 rate: ShedLock single-runner on recovery, per-sweep batch size,
`EfsProperties` timeouts (bound in-flight duration, not arrival rate), and both
`wecms.dispatch.realtime-dispatch` and the recovery cron defaulting to off.

### ✅ Fix applied — `RealtimeDispatchQueue`

A bounded `ArrayBlockingQueue` with one daemon worker, created lazily so a disabled
rail costs no thread. `afterCommit` enqueues and returns; the drain thread dispatches.

**The request thread is free by construction, not by discipline.**
`RealtimeDispatchRegistrar` no longer holds a reference to `RealtimeDispatchService`
at all — its only collaborator is the queue, so a 202 cannot be issued from that
thread.

`DispatchPacer.pause(outBankCd)` runs in a `finally` inside `drainOne` — exactly one
pace per row on both the success and failure paths, keyed on that row's own
`OUT_BANK_CD` re-read from `TB_PAY_TARGET`. Tree-wide, `DispatchPacer` is injected in
two places and `pause` invoked from two: this rail and the 203 scan.
`recoverPayTargets` enqueues rather than dispatching, so there is **one pacing point**.

**Legacy's D-A leak is fixed structurally.** `NAPAS_REAL_TIME_BATCH` hoists
`per_request_delay` outside its loop and only ever raises it, so one Woori row pins
3000ms onto every later bank. Our key is resolved per ticket — no variable spans
iterations. `PER_DELAY_DEFAULT` stays at legacy's **100ms**: it was raised to 3000ms
alongside this change on 2026-08-01 and **reverted 2026-08-05** (handover 05 §5.3 /
06 §6.3 document 100ms as the 기본 딜레이). The per-row keying is the whole fix.

**Shutdown:** `@PreDestroy` latches, stops accepting, drains for 30s. An in-flight row
is never interrupted before the deadline — a 202 already at the gateway is not
abandoned mid-call. Queued tickets past the deadline are discarded with an exact count
at ERROR; their rows stay `'9'`.

*The worker was first written non-daemon and it wedged the Gradle test JVM — the same
failure a production process missing `@PreDestroy` would show. Now daemon, so the
outcomes are bounded-drain or hard-kill, with no third state where the process hangs
holding unwatched work.*

### Hard dependency — record this before enabling

**`stuck-monitor.enabled` must be true before `realtime-dispatch`.** The pay-target
`'9'` sweep is the *only* detector for a ticket lost from the in-memory queue on
restart. SIT and UAT ship the monitor ON; **local and prod ship it OFF**, so a prod
activation following the step order would enable the money rail with no backstop.
Recorded as Gate I in the runbook.

**Remaining gap, not introduced by this change:** a dispatch that never happens has no
user-facing signal. The HTTP response already said the payment was submitted. Business
failures inside `dispatch()` write `PROC_STAT='3'` and alert; everything else leaves
`'9'` for the monitor. Minimal fix is a read-side projection over `PROC_STAT='9'` —
no schema change — but it is a product decision.

---

## C-4 — External I/O inside transaction boundaries ✅ **CLOSED**

**Was: High. Connection pool exhaustion under load.**

✅ **All three paths fixed.**

| Path | Fix |
|---|---|
| `buildAndDispatchFile` — SFTP upload + 00301 | Two-phase dispatch, 2026-07-31 |
| `BulkReceptionResultService.handle` — N × 00302 | `afterCommit` fan-out + per-sibling `REQUIRES_NEW` |
| `BulkExecutionResultService.handle` — SFTP download + parse | Split into orchestrator + writer, 2026-08-01 |

**The third one was closed after a legacy re-check confirmed we were the divergence.**
Legacy `API2` opens its transaction at `:291` — after the download, the remote move,
the disconnect, the empty-file check and the entire parse loop. We held a connection
across all of it on a callback thread.

`handle` is now a plain orchestrator; a new `BulkExecutionResultTxService` on its own
bean carries the writes. **Dependencies dropped 16 → 11** as the five write-side
repositories and the event publisher moved with the code — a reasonable sign the split
followed a real seam rather than being imposed on one.

**The atomic claim is legacy's own update, hoisted.** `claimExecutionResult` is
legacy's `U003` 2→3 advance (`API2:351-372`) moved to the front of the writer and made
conditional on `PROC_STEP='2' AND PROC_STAT='6'`. Matching legacy's ordering exactly
would have imported its TOCTOU window — two concurrent 01302s both passing an
outside-transaction lookup and both downloading. Zero rows means another caller won:
the writer returns false, writes nothing, and the orchestrator still acks `0000`,
because a non-`0000` would only make the gateway resend against a file the winner is
finalizing.

Reordering is safe because it is one transaction — a later failure rolls the advance
back with everything else and the row returns to `'2'`/`'6'`, replayable.

**Two details that matter:** the MST is **re-read by PK inside the writer**, so the
orchestrator's copy never crosses a boundary and cannot blind-overwrite a concurrent
change; and the `TB_FIRM_FILE_RSLT` insert loop **stays inside**, matching
`API2:293-331` where one failed insert rolls back every prior line.

**The split removed no writes from the rollback set.** Nothing was ever written inside
the transaction ahead of the parse — the only earlier write is the audit row, already
committed in its own `REQUIRES_NEW`. That is why it was cheap, and why the fail-safe
guarantee is identical.

*Comment defect fixed:* `:486-488` claimed the remote move was "best-effort … failure
ignored." There is no catch, so it propagates and aborts the callback — which is
legacy's behaviour, since its `fileMove` sits inside the download `try` at `:226-247`.
Now records the window both systems share: local copy written, remote not archived,
retry re-downloads.

*Test work worth noting:* `BulkExecutionResultServiceTest2` needed real changes, not a
constructor fix — assertions expecting `firmFileMstRepository.save(mst)` were testing a
path that no longer exists. The two negative tests now assert the claim was never
**attempted**, which is stronger than the old save check.

The table below is the original finding.

| Path | External calls in one transaction |
|---|---|
| `buildAndDispatchFile` (`REQUIRES_NEW`, per file) | 2 — SFTP upload + 00301 HTTP |
| `BulkReceptionResultService.handle` | N — one 00302 per sibling |
| `BulkExecutionResultService.handle` | 1 SFTP download + parse |

For a pay file splitting into N: 3N external calls over the lifecycle, N of them
inside the single transaction opened by the barrier-releasing callback.

---

## C-7 — EFS timeouts tightened 3x ✅ **CLOSED**

**Was: High. Unknown outcome on money that may have moved.**

✅ **Fixed.** Read timeout 20s → **60s**, connect 3s → **30s** — both now matching
legacy `ConnectionUtil:16-17`. The old values came from the vendor PDF's §6 sample,
which the guide's own 주의 banner marks non-binding, while §3.2 states a normative
**30-second** round-trip limit that nothing in our code referenced.

**Knock-on handled:** per-row worst case became 93s, so the `ResultCheckJob` lock
ceiling was re-derived — it now tracks the PT10M pass deadline rather than batch size,
and dropped PT2H30M → **PT30M**. Post-crash blind spot: 150 min → 30 min.

| | Connect | Read |
|---|---|---|
| Legacy `ConnectionUtil:16-19` | 30s | 60s |
| Refactor `EfsProperties:76-80` | 3s | 20s |

Any 202 the bank answers between 20 and 60 seconds now times out where legacy
completed. A timed-out 202 is not a failure — it is an unknown outcome. The row lands
at `PROC_STAT='6'` awaiting a result-check job that is off by default.

**Action:** obtain the gateway's p99 response time before treating 20s as safe. If
the bank has ever taken longer than 20s under load, this converts successful
transfers into unresolved ones.

---

## C-8 — Gateway headers absent ✅ **CLOSED**

**Was: High. Deployment blocker.**

✅ **Resolved two ways.** `x-coocon-accessIp` **restored** — legacy sends it at
`ConnectionUtil:191`, and the `getLocalServerIp()` resolution was ported step for step.
`Authorization` and `signature` deliberately **not** restored: they are the hardcoded
placeholder literals `auth12345`/`sign12345`, handover §8.5.5 confirms the gateway
ignores them, and sending fabricated credentials is worse than sending none. A test
now pins their absence.

**The vendor guide independently settles it:** 44 pages, `Content-Type` is the only
header required. Zero occurrences of `Authorization`, `signature`, `x-coocon-accessIp`
or any IP-allowlist concept. Our client was already conformant; restoring the third
header is belt-and-braces against an infra-level allowlist the guide would not
mention.

Legacy `ConnectionUtil:185-193` sets four headers on every EFS call.
`RestClientEfsApiClient:57` sets one; `EfsClientConfig:26-35` sets none.

| Header | Legacy value | Refactor |
|---|---|---|
| `Content-Type` | form-urlencoded | present |
| `x-coocon-accessIp` | `getLocalServerIp()` | **absent** |
| `Authorization` | `auth12345` | **absent** |
| `signature` | `sign12345` | **absent** |

`auth12345` / `sign12345` are hardcoded placeholder literals committed in legacy
source and may not be validated. `x-coocon-accessIp` is vendor-namespaced and is
plausibly used for allowlisting or logging.

**Action:** confirm with the Coocon gateway team which of the three are enforced.
This is an external dependency — start it now, the answer takes days.

---

## C-9 — `ResultCheckJob` single-gated, class doc claims otherwise ✅ **CLOSED**

**Was: Medium.**

✅ **Fixed.** `wecms.dispatch.result-check.enabled` added, default false, checked at
`runOnce()` entry — matching its two siblings. The Javadoc claiming `runOnce()` "stays
a no-op" was corrected; it never was.

**One coupling recorded:** `StrandedRecoveryService` delegates into `runOnce()`, so
the new flag silences that delegation too, even with `recovery.enabled=true`. Judged
correct — the flag answers *"may this instance drive 203 traffic"*, which should not
depend on the caller — and `StrandedRecoveryService` now logs at WARN when it skips,
since silent partial recovery is the trap.

`StrandedRecoveryService:74-76` and `StuckPayFileMonitorService:131-133` each have an
`enabled` boolean checked at method entry. `ResultCheckService` has only
`batch-size`; `runOnce()` opens straight into the query. One environment variable
starts outbound EFS 203 traffic and `PROC_STAT` writes.

Worse, `ResultCheckService:48-50` states `runOnce()` "stays a no-op and the cron
defaults to never fire". The second half is true; the first is not. Anyone reading the
class doc will conclude a second gate exists.

Already logged as P-5 in the readiness audit. The misleading Javadoc is not, and is
the more dangerous half.

The only backstop is bean-level: `wecms.dispatch.efs.enabled=false` selects
`MockEfsApiClient`, which throws. Fail-loud accident, not a gate.

---

## C-10 — Timezone inconsistency across dispatch components ❌ **WITHDRAWN**

**Not a bug.** Both readers are correct against their own writers: `TRAN_DT` is
stamped UTC (`PymnHelper:2197`), `TRAN_TM`/`RQ_DTTM` are stamped ICT
(`RealtimeDispatchService:146-148`). Forcing one zone would break whichever column
lost its writer. **Improvement applied anyway:** both now derive from a single
`Instant`, closing a window where two `now()` reads could straddle midnight.

**R-A refuted.** Every 14-character stamp derives from one `LocalDateTime.now(ICT)`;
the two concatenation sites take both halves from the same `now`. Legacy stamped
`PROC_DTTM` from a single DB-side `to_char(now(),…)` — we are structurally equivalent.

**One residual (R-B), documented not fixed:** `reflectOutcome` stamps the 203 audit
row's own `TRAN_DT` in ICT while every 202 row's is UTC, so between 17:00 and 24:00
UTC the audit row sits one day ahead of the row it audits. `ORG_TRAN_DT` preserves the
linkage, so the writer was left alone — but any reconciliation query filtering on
`TRAN_DT` across both must know.

*Relevant to C-12:* legacy's `now()` resolves in the DB session zone (Asia/Seoul), so
legacy `PROC_DTTM` is two hours ahead of ours. On a shared database a legacy-written
row looks two hours newer to our finders — conservative direction.

The original finding text follows. Beyond touching the join key for every lookup, the
legacy/refactor date disagreement is what disarms the accidental PK interlock that
is currently the only protection against duplicate 202 transfers under shared-DB
coexistence (C-0).

Within `ResultCheckService.runOnce` alone: the date filter uses
`LocalDate.now(ZoneOffset.UTC)`, the age filter uses `LocalDateTime.now(ICT)`.
Seven hours apart, same query path. `StuckPayFileMonitorService` uses ICT-today.
`@Scheduled` zones are ICT. The writer stamps `TRAN_DT` in UTC.

`TRAN_DT` is the join key for the 01301 callback lookup, the sibling barrier, and
every stranded-row finder. H-7 is scoped to result-check; the exposure is wider.

**The parity justification is wrong, and the conclusion is still right.**
`ResultCheckService:257-260` argues UTC "is exactly legacy's invariant". Legacy's
writer is `DateTimeUtils.utcNow()`; legacy's reader is PostgreSQL `now()` in the DB
session zone, recorded as Asia/Seoul. So legacy reader ≠ writer, and legacy's
result-check can only match rows written before 15:00 UTC / 22:00 ICT — late-evening
transfers were never recovered, unnoticed because Vietnamese banking hours sit inside
the safe window.

Legacy has a latent defect here. Parity is the wrong goal. The refactor's divergence
is correct, for different reasons than its comments claim.

**Action:** confirm the JEX DB `TimeZone` setting, then correct the comment before it
is cited as parity in a sign-off.

---

## C-5 — Callback errors use the wrong response envelope ✅ **CLOSED**

**Was: Medium, but it is the trigger for C-1 step 6.**

✅ **Fixed.** A new package-private `CallbackAck`; both controllers now catch
`RuntimeException`, log at error with the stack, and return the **echoed envelope**
with `RC`/`RM` rather than a `WBE9999` `ApiResponse` body the gateway cannot parse.

Refactor `WBE_*` codes are not EFS result codes, so they map to legacy `WCConst`
equivalents. After the RC-contract work these are leg-accurate: `G001` for missing
header fields, `G002`/`G001` for the body split, `G003` for an unprovisioned key or
verify failure, `G004` for a DB fault, `G999` otherwise. Each carries the legacy line
it mirrors.

**One TODO retained (B5):** whether the gateway treats `G999` as retryable or
terminal is unconfirmed — a terminal `G999` would silently consume a transient
WeCMS-side fault. Check against the captured sample.

`BulkReceptionResultController:35-38` calls the service directly with no exception
handling. Escaped exceptions reach `GlobalExceptionHandler`, returning HTTP 500 with
a `WBE9999` `ApiResponse` body. The gateway expects an `EfsEnvelope`
(`ID/RQ_DTIME/TNO/EV/VV/EM/VM/RC/RM`).

**Action:** confirm gateway behaviour on an unparseable body against the interface
spec. It determines whether C-1's replay is triggered by retry or the callback is
simply lost. External dependency — ask now.

---

## C-12 — Rows written outside `wecms-api` are never processed ⚠️ **DETECTION ADDED, RECOVERY BLOCKED**

**Was: High. A `PROC_STAT='9'` row from any non-dispatch writer stalls silently and
permanently.**

✅ **Silently is fixed.** `StuckPayFileMonitorService` gained a fourth sweep over
`TB_PAY_TARGET` at `PROC_STAT='9'`, sharing the finder `StrandedRecoveryService` uses
so monitor and recovery agree on what counts as stranded. Read-only, with its own
grace window — `pay-target-threshold-minutes`, default **5** not 15, because the
after-commit hand-off waits for nothing.

⚠️ **Permanently is not.** Nothing advances a JEX-written `'9'` row.
`StrandedRecoveryService` is the component for it and stays double-gated off, because
refactor rows are indistinguishable from live-JEX rows on a shared database — no
source column, shared sequences, no cutover marker. **Blocked on the same provenance
decision as C-0.**

**One find from building it:** a new cause, `REALTIME_FX_INQUIRY_BLOCKED` — later
renamed `REALTIME_DISPATCHED_NOT_CLAIMED` after the mechanism turned out to be
different from the one first proposed. Classified ahead of the config and window
causes so it cannot be mislabelled as a benign skip.

The original finding text follows.

### Schema note

`PROC_STEP` does not exist on `TB_PAY_TARGET` or `TB_PAY_FILE_MST` — verified
against JEX DEV `information_schema`. Both carry `PROC_STAT` only. `PROC_STEP`
exists on `TB_FIRM_FILE_MST` alone. Scope state-machine work accordingly.

### Writers outside the dispatch package

Fourteen sites write `PROC_STAT` on the two pay tables.

**`wecms-api`, five executors** — write `'9'`, register the afterCommit rails, so
their own rows are normally driven forward in-process. These are C-0 tier 1:
`VendorDispatchExecutor:192/236`, `ExpenseDispatchExecutor:186/229`,
`InternalTransferDispatchExecutor:265/307`, `OverseasDispatchExecutor:198/367`,
`PayrollDispatchExecutor:168`.

**`WECMS DEV` JEX web app, seven sites** — write `'9'`, no Spring transaction:

| Site | Table | Trigger |
|---|---|---|
| `PymnHelper:2220` | `TB_PAY_TARGET` | `pymn_0001_001_c001`, VND API branch |
| `PymnHelper:2283` | `TB_PAY_FILE_MST` | same, File branch |
| `PymnHelper:2628` | `TB_PAY_TARGET` | full FX-matrix branch (VCB/Shinhan/Woori) |
| `PymnHelper:2705` | `TB_PAY_FILE_MST` | same, File branch |
| `pymn_0005_001_c001_act.jsp:148` | `TB_PAY_FILE_MST` | payroll dispatch screen |
| `pymn_0007_001_c001_act.jsp:194/320` | `TB_PAY_FILE_MST` | overseas remittance screen |

**`WECMS_BATCH DEV` callback JSPs, three sites** — write terminal states:
`FILE_DEPOSIT_TRANSFER_API1_act.jsp:298/370` (`'3'`),
`FILE_DEPOSIT_TRANSFER_API2_act.jsp:431` (computed `'0'/'1'/'3'`).

**Nothing found** in `wecms-web` (34 hits, all display/derivation) or
`SOURCE_CODE_ADMIN` (zero occurrences). No admin or correction tooling writes these
columns anywhere in the workspace.

### Why the refactor never picks them up

`RealtimeDispatchRegistrar:61-63` and `DispatchAutoReceiveRegistrar:61-63` register
a `TransactionSynchronization` on the creating transaction. A row written by
`PymnHelper`, a `pymn_*_act.jsp`, or a batch API callback never enters a Spring
transaction, so no synchronization is registered and no hand-off fires.

No other enabled component covers it:

- `ResultCheckService` scans `TB_FIRM_TRAN` at `TRAN_TP='202' AND PROC_STAT='6'`
  (`:292`). A `'9'` row has not been claimed to `'6'` and is invisible.
- `StuckPayFileMonitorService` is read-only — no `save(`, no `setProcStat`, no
  `@Modifying`. It alerts; it does not advance state. Also default-off.
- The atomic `'9'→'6'` claims are reached only via `RealtimeDispatchService.dispatch`
  and `BulkReceptionService.receive`, which nothing calls for a row it did not create.

Net effect: the row sits at `'9'` indefinitely. No FAIL transition, no ops alert, no
timeout. Recovery is the manual model at `.env.batch.example:109`.

Partially known internally — `PayrollDispatchExecutor:264` already carries a comment
flagging `PROC_STAT='9'` WAIT with no FAIL and no ops alert.

### The dependency knot

`StrandedRecoveryService` is built for exactly this case; its Javadoc names it
("a flag flipped ON after rows already existed", "a crash in the commit→afterCommit
window"). Its finders scan by state alone —
`findStrandedPayTargets` / `findStrandedPayFiles`, `procStat = :procStat AND
procDttm < :cutoff` — which would match a JEX-written row.

It is double-gated off in all four profiles (`StrandedRecoveryService:110-113`,
`StrandedRecoveryJob:33`), and the stated reason is a blocker, not an oversight:
refactor rows are indistinguishable from live-JEX rows on a shared DB — same global
`firm_seq_no` / `firm_file_seq_no` sequences, no source column, no cutover marker.
Enabling the sweep would let it double-process JEX-originated rows. Owner: PO /
Mr. Kang.

**Sequencing consequence.** C-0's remedy is to turn the legacy batch off before the
refactor points at a JEX database. The JEX web screens above keep writing `'9'` rows
regardless. With the legacy batch off and `StrandedRecoveryService` off, those rows
strand. C-0 and C-12 must be sequenced together or the cutover trades one failure
mode for the other.

### Row provenance is the single unblock

The same root — refactor and JEX rows being indistinguishable — is cited in three
places: `StrandedRecoveryService`'s Javadoc, activation runbook §7.5, and as the
reason C-0 has no clean fix.

A source marker or cutover timestamp on `TB_PAY_TARGET` and `TB_PAY_FILE_MST` would
let the recovery sweep run safely, make coexistence tractable, and give the cutover
sequence a basis. Worth raising as one decision rather than three separate blockers.

---

## C-13 — Legacy production charset undetermined ✅ **CLOSED — negatively**

**Was: Blocker for any non-ASCII payload.**

✅ **The channel is ASCII, by vendor contract and by observation.**

- Handover pitfall ④: 미적용 시 은행 측 거절 — *untone-stripped values are rejected by
  the bank.* That is the vendor stating the constraint outright.
- **Zero** files carrying non-ASCII have ever reached `PROC_STEP='3'`. Two attempts in
  production history, both failed.
- All six UAT samples are 0 bytes above 0x7F.
- Vendor guide §1.2 closes the API/JSON layer separately: **Charset UTF-8**.

**PROD `file.encoding` no longer blocks anything.** The `.xlsx` request stays useful
only for the `data_amt` 23-vs-22 width question.

⚠️ **Two holes remain, and "moot by construction" was false** — the codebase said so
in a comment this memo contradicted (`FixedLengthCodec:75-80`):

| Hole | Why |
|---|---|
| `rcptUseAddr` (offset 818) | Tone-stripped and byte-truncated, but **no charset guard on either side**. Legacy-faithful by decision |
| ERP import path (D-M) | No entry layer exists, so `PaymentWireWidthGuard` never runs. Decided 2026-08-01 to keep, matching legacy |

On both, the codec's non-ASCII scanner **observes and alerts, never rejects** — one
WARN per field, one ops alert per file, file still sent. That is the final stance, not
a placeholder: rejecting would be unreachable for entered text, stricter than legacy
for `rcptUseAddr`, and would refuse batches over another system's bytes.

The original finding text follows.

### Layout parity: verified clean

Every offset, length, pad character and justification matches across header, data
and trailer. Totals are 998 + CRLF = 1000 on all three. The 2025-10-29 change —
183-byte filler replaced by `rcptUseAddr(180)` + `feeBrdnClsCD(3)` — is a pure
re-partition of trailing filler, so no offset before byte 815 moved.

The refactor gets the hardest part right: `feeBrdnClsCD` is written at 815 *before*
`rcptUseAddr` at 818, the reverse of declaration order, matching legacy
`Deposit_Data.getData():218-221`. `FixedLengthCodec:135-136` and the decode slices
agree. This is the single easiest place to introduce a 180-byte shift.

*Caveat:* only `feeBrdnClsCD`'s addition and the filler's removal carry the
2025-10-29 date comment. `rcptUseAddr` is undated and no earlier revision exists in
the workspace. 180+3 = 183 is consistent with one change, but that is inference.

### The charset itself

| | Width computation | Encode | Decode | Truncate |
|---|---|---|---|---|
| Legacy | `source.getBytes()` — JVM default | `getBytes()` — JVM default | `new String(buf,…)` — JVM default | **hardcoded UTF-8** (`StrUtil:71`) |
| Refactor | `getBytes(CHARSET)` — UTF-8 | UTF-8 | UTF-8 | `FixedLengthCodec.CHARSET` |

Legacy is internally inconsistent: it truncates `RCPT_USE_ADDR` in UTF-8 bytes, then
pads and writes in `file.encoding` bytes.

### Where the value is not

Searched and absent: `jex.prop` (JEX container options only), `.jexSettings/jex.deploy.*.prop`
(host + copy path only), the ops handover env-key list (`handover/06_환경설정_항목목록.md`
— no encoding key at all), and workspace-wide for start scripts, `setenv*`,
`catalina*`, Dockerfiles. The only start scripts present are refactor-side.

**Two decoys.** `jex.real.wecms_batch.xml:2` carries `<jex charset="UTF-8">` and
`web.xml:44-51` sets `clientCharset`/`serverCharset` to UTF-8. Both are JEX
transport/adapter settings. `String.getBytes()` does not consult either.
`.settings/org.eclipse.core.resources.prefs` sets source-file encoding for `javac`,
not runtime.

### Why UTF-8 is not the safe assumption

The legacy stack is pre-JEP 400, so `file.encoding` derives from the host locale. On
a Linux WAS with `LANG` unset, a Java ≤17 JVM resolves it to `ANSI_X3.4-1968`
(US-ASCII), under which every non-ASCII character is written as `?`. That is the
realistic bad case — not UTF-8. If production has been doing this, the bank has
never received a correctly encoded Vietnamese character, and the refactor's UTF-8
output is something it has never parsed.

### Why it isn't moot

All six UAT samples in `WECMS_BATCH DEV/samples/` are pure ASCII — 0 bytes above
0x7F. For that traffic, legacy and refactor produce identical bytes under any of
US-ASCII, UTF-8, EUC-KR or CP1252. That is a property of the sample data, not a
proof.

Non-ASCII can reach the codec: `replaceTone` maps only its 134-character table and
passes everything else through, and the legacy gate misses 27 of those 134 (C-14).

### Resolution

`java -XshowSettings:properties -version` on the PROD WAS (`42.96.47.184`, deploy
root `/WAS_DATA/webRoot/wecmsbatchvn`), or the JEX start script under `/WAS_DATA/`.
Until then, treat the charset as unresolved for any non-ASCII payload.

---

## C-14 — Unguarded field overflow, and NFD input ✅ **CLOSED**

**Was: High. Legacy may already be emitting malformed records; the refactor converts
that into a hard dispatch failure.**

✅ **Investigated to a verdict, then guarded at entry.** The question was settled from
the schema rather than from principle: `RCPT_USE_ADDR` is the **only** one of the five
whose source column is wider than its wire slot (300 chars vs 180 bytes). The other
four are exact-fit or have slack, so **ASCII overflow is arithmetically impossible**
and legacy's single truncator was deliberate, not an oversight.

The reachable path is multibyte surviving tone-stripping — Korean/CJK primarily, NFD
secondarily.

**Fix: guard at entry, not truncation at dispatch.** `WireField` +
`PaymentWireWidthGuard` model the real pipeline (NFC → strip CRLF → `replaceTone` →
count UTF-8 bytes) and report **non-Latin separately from over-width**, because a
Korean memo is both and calling it "too long" would be true and useless. Wired at
preview and save across all five payment types. **No truncation added** — a silently
cut beneficiary name is worse than a clear rejection.

**Plus a missed legacy port found along the way.** Legacy applies
`StringUtils.sanitize`'s `[\p{Zs}]` → space at every entity setter and throughout
`ErpDataRefreshHelper`. We had it on the guard path but **not on the HAENGSUNG ERP
import**, which has no entry layer at all — so a Word-pasted `U+00A0` reached the wire
unchallenged. Ported, with the legacy citations.

That matters because NBSP is **invisible**: it survives tone-stripping, passes the
gate, and reaches the wire as two bytes. The one production instance
(`S_GV00000006_SHB_001_20251128`, four `U+00A0` in `rcpt_use_addr`) is also one of the
two false all-success files.

⚠️ **One field cannot be guarded at entry:** `senderName` is derived server-side from
`ACCT_MSTR.ACCT_NM` (100 chars) — no upload column, no DTO field. A Korean-named
account gives 300 bytes against a 150-byte slot. Covered by `OverseasSenderNameGuard`
at overseas create and dispatch, with a non-blocking warning at registration, since
`ACCT_NM` is bank-adapter-supplied and the user cannot edit what the scrape returned.

The original finding text follows.

### Only one field is truncated

`getStrByteMaxLen` / `byteTruncate` are applied to `rcptUseAddr` (180) only —
legacy `FILE_DEPOSIT_TRANSFER_BATCH1:638/1066`, refactor
`BulkReceptionService:579/604`. Both apply truncate-then-tone, which is the correct
order since tone-stripping only shrinks byte length.

`senderName` (150), `receiverName` (150), `depositAccountMemo` (210) and
`withdrawalAccountMemo` (210) are unguarded on **both** sides.

### Opposite failure modes on overflow

- **Legacy:** `rightPadWith`/`leftPadWith` catch the over-width exception and return
  `null`. The caller does `sb.append(null)` on a `StringBuffer`, which appends the
  four literal characters `null`. A short, 1000-byte-boundary-breaking record goes
  to the bank with only a log line. Production may already have done this.
- **Refactor:** `put()` throws `IllegalArgumentException`, caught pre-egress and the
  dispatch rolls back.

The refactor is correct. It is also a user-visible change: a payment legacy accepted
and silently mangled will now fail at dispatch. Communicate before cutover.

Related, `M-6`: four BATCH1 fields are not wrapped in `null2void` —
`TRSC_CCY_CD`, `TRAN_AMT`, `BANK_CHNL_DIV`, `TAR_BANK_CD`
(`FILE_DEPOSIT_TRANSFER_BATCH1:625-631`) — so a null there also produces the literal
`null` in legacy output.

### NFD makes this reachable

Neither implementation normalizes Unicode — no `java.text.Normalizer` anywhere in
either dispatch path. Both tone tables are NFC-only and byte-identical across all
four copies in the two trees (md5-verified: `b4279cab…` / `70843809…`).

Measured on the shared table:

```
NFC  "Nguyễn Đức Đường" → "Nguyen Duc Duong"   16 UTF-8 bytes
NFD  "Nguyễn Đức Đường" → "Nguyễn Dức Dường"   30 UTF-8 bytes
```

Under NFD the combining marks are not in the table, so base letter and marks both
pass through — and the field nearly doubles in width. `Đ` (U+0110) still strips
because it has no canonical decomposition, hence the mixed output.

Parity is preserved: both implementations behave identically. So is the defect.

**Action:** query the live pay-data tables for NFD text —
`WHERE col IS DISTINCT FROM normalize(col, NFC)` (PG13+) — on
`finc_mda_owner_nm`, `sender_nm`, and both memo columns. Also check
`octet_length(col)` against the wire widths above to see whether overflow has
already occurred in production.

### Related divergence: the tone gate

Legacy gates every `replaceTone` call on `hasVietnameseCharacters`
(`FILE_DEPOSIT_TRANSFER_BATCH1:641-668`). The refactor calls it unconditionally
(`BulkReceptionService:563-566/595-598`); `VietnameseText` has no gate.

Legacy's gate ranges (`\u0041-\u007A`, `\u00C0-\u00E0`, `\u1EA0-\u1EF9`) miss
**27 of the 134** table characters — `áâãèéêìíòóôõùúýĂăĐđĨĩŨũƠơƯư`. A value
containing only those plus digits and punctuation is left toned by legacy and
stripped by the refactor. Any ASCII letter re-arms the gate, so real names almost
always pass, but it is a genuine byte-level divergence and the refactor is the side
that changed.

Hygiene: four implementations across the two trees carry three different gate range
sets. The refactor reproduces the JEX-web gate in `LegacyTextNormalizer` and drops
the batch gate in `VietnameseText`. Not documented as intentional.

### Latent coupling

`byteTruncate` uses `FixedLengthCodec.CHARSET`; legacy's truncator hardcodes
`StandardCharsets.UTF_8`. Repinning `CHARSET` to resolve C-13 would silently change
truncation semantics away from legacy. **The fix for C-13 breaks this unless
`byteTruncate` is pinned independently.**

---

## C-15 — EV/VV URL-encoding compensation not ported ✅ **CLOSED**

**Was: High. Deployment blocker; not closable from source.**

✅ **Closed 2026-08-01 by taking the half of legacy's mechanism that works and
declining the half that compensates for a bug we do not have.**

### The ordering finding — why the pass count is not ported

My working assumption was *"our single pass is legacy's pass 2."* **Wrong, and the
correction is the whole answer.** Legacy repairs the Base64 alphabet **before** its
decode (`:269-271`); we repair **after** (`EfsCryptoService:115`). Trace a plain,
not-URL-encoded Base64 EV containing `+`:

| | Result |
|---|---|
| Legacy pass 1 — no decode, then commons-codec | ✅ correct bytes |
| Legacy pass 2 — `URLDecoder` turns `+` into a space, commons-codec **silently discards** it | ❌ wrong bytes, no error |
| Ours — `urlDecode` makes it a space, repair makes it `+` again, strict decode | ✅ correct bytes |

**Legacy's pass 2 is broken for exactly the input class pass 1 exists to serve.**
Legacy needs two passes because each handles one class; ours handles both. Six input
classes were verified, including two that legacy's pass 2 gets silently wrong and only
its pass 1 rescues.

Porting the ladder would mean reproducing legacy's ordering bug in order to reproduce
its workaround.

*Also relevant:* `java.util.Base64` throws on any character outside the alphabet;
commons-codec discards them. So the specific `%`-bearing silent success legacy's
comment describes **cannot happen on our side** — it fails at the decoder.

*And legacy already ships our stance on one of its own call sites:*
`PROCESSING_RESULT_CHECK_BATCH:240` passes `urldecode=true`, which skips pass 1
entirely via the `else` at `:294`.

### ✅ What was ported — the plaintext validation

**The validation is the trigger, not an accessory.** Without it the pass count never
fires: AES-CBC on wrong-but-plausible bytes throws only if the final block fails PKCS5
unpadding. Legacy's own comment says it — URL-encoded data *"decrypts successfully
as-is."*

But the reverse does not hold, which is what made it portable on its own: the
validation converts a silent wrong-decrypt into a reported failure regardless of how
many passes there are.

`assertPlaintextWellFormed` — `Long.parseLong` on the 14-char prefix, then `readTree`
on the remainder via a **private static mapper**, deliberately not the injected one,
since it parses to a tree and must not inherit the global `SNAKE_CASE` strategy.
Failure → `IllegalStateException`, no plaintext in the message since it reaches logs.

**Only legacy's `isPaddingNum == true` branch was ported.** The `false` branch is
defective: it validates the whole string as JSON, certifies it intact, and then every
caller does `substring(14)` and chops fourteen characters off it.

**One addition beyond the spec, and it closes a real gap.** `readTree("")` returns
`MissingNode` rather than throwing, so an empty or scalar remainder would have passed
a check meant to be equivalent to `JSONObject.fromObject` — which rejects arrays and
scalars outright. `isObject()` is now required. All four call sites bind to records, so
requiring a top-level object is faithful rather than over-tight.

`verifyVv` needs no edit — it inherits through the existing
`catch (RuntimeException)` → `false`, which both callback services already map to
`G003`.

### Deliberate divergence, with a SIT watch item

We now **throw** where legacy returned garbage: a response whose prefix is not 14
digits. Legacy tolerates it at validation and breaks it at the caller.

Right direction, but if the gateway ever omits `RQ_DTIME` on a response we would start
rejecting responses we previously accepted-and-garbled. Signature: `G003` on the
callbacks, gateway-error-and-retry outbound. **Recognise it on the first SIT run
rather than debugging it as a mystery `G003`.** A genuinely prefix-less response is a
per-leg policy decision, not grounds for removing the validation.

### Not reproduced — the nesting

`VerifyMac`'s two passes wrap `VerifyMac2`, which calls `DecryptAesBase64` — itself
2-pass. Up to **four** decrypt attempts per verify, with asymmetric depth between the
outer passes. Recorded as accreted patching rather than a designed ladder, with both
tells: the inner depth flips via the `:294` `else`, and the `getHmacSha256` flag the
outer pass sets is then largely neutralised by `:585-586` decoding both sides anyway.

### B5 re-scoped, not merely re-affirmed

The old `TODO(B5)` framed the question as *"add the second pass if the sample requires
it."* That decision surface is now close to empty — a captured sample could only reveal
whether a counterparty sends EV not URL-encoded, and our pipeline handles that
correctly.

**Pitfall ③'s "implement one side → intermittent failures" is a warning about legacy's
two-input-class design, not about ours.** The framing is gone from the Javadoc so the
next reader is not left believing an unresolved intermittent-failure risk sits behind
it. B5's real dependencies — `BT00`/`BT01`/`C999` and D-4 — are unaffected and stay.

### The test worth noting

A random 32-byte wrong key fails at PKCS5 unpadding ~99.6% of the time, which would
exercise `aes()` rather than the validation. So a key that **unpads cleanly** was
searched for and hardcoded — decrypting the fixed ciphertext to 131 bytes of garbage
with a non-numeric prefix. That is precisely the *alphabet-clean, correctly padded,
decrypts to garbage* class, it returned that garbage before this change, and the
assertion pins the message to `C-15` so it fails loudly if it ever starts taking the
cipher path instead. The derivation and a *"do not simplify this to a readable key"*
warning are in the test Javadoc.

Plus: non-numeric prefix, non-JSON remainder, array/scalar/empty remainder, wrong key
through `verifyVv`, happy-path-unchanged, and the plain-not-URL-encoded case — input
class 2, the one legacy's pass 2 corrupts.

**The original finding follows.**

✅ **The Base64 repair is ported** — `' '→'+'`, `'_'→'/'`, `'-'→'+'`, applied to both
`decryptEv` and `verifyVv` (legacy repairs the VV too). One deliberate ordering
change, documented: legacy repairs *before* its Base64 decode and its first pass never
URL-decodes; our pipeline always URL-decodes, so the equivalent position is *after*
`urlDecode`. Repairing first would be actively wrong — a literal `+` would be repaired
and then turned back into a space.

**And the vendor's own sample explains why the repair exists.** §6.2's
`EncryptAesBase64` calls `HttpUtility.UrlDecode` on the **encrypt** path while its own
header comment says URL *encoding*. That converts every `+` in Base64 output into a
space — precisely the corruption legacy compensates for. The sibling
`EncryptHmacSha256` uses `UrlEncode` correctly, so EV and VV are encoded in opposite
directions in the reference implementation. A decade-old undocumented workaround,
explained by a typo in the vendor's code.

⚠️ **The pass count is deliberately not ported**, gated on B5. We use one
self-consistent encode-on-output / decode-on-input pass. The guide's §4 memo describes
exactly that — single URL-encode, applied last, both directions — and double encoding
occurs only in the `_JSON_` transport variant we do not use.

Handover §8.4 pitfall ③ asserts a concrete failure mode — *intermittent* verify
failures if only one side is implemented — which is the class of bug a green suite
will not catch. The `TODO(B5)` records it as the first hypothesis if one appears in
SIT. **Do not speculatively add passes**; the sample decides.

Crypto parity is otherwise clean: `AES/CBC/PKCS5Padding`, all-zero 16-byte IV, key
bytes used verbatim from UTF-8, 14-character `RQ_DTIME` prefix, HMAC-SHA256 over the
post-prefix payload. The refactor additionally hardens — `MessageDigest.isEqual` for
constant-time comparison, key-length validation naming `TB_VAN_API_INFO.API_KEY`, and
removal of legacy's `public static` mutable IV.

Not ported:

- Legacy input pre-processing: `replaceAll(" ","+")`, `replaceAll("_","/")`,
  `replaceAll("-","+")` (`DecryptAesBase64:269-271`, `VerifyMac:540-545`)
- Legacy two-pass URL-decode retry — decrypt without URL-decode, retry with it on
  any exception (`:273-318`, `:546-567`)

These are compensations for a gateway returning inconsistently encoded `EV`/`VV`.
The refactor uses a single self-consistent encode-on-output / decode-on-input pass
that round-trips against itself, and its javadoc explicitly declines to
speculatively add passes — correct instinct, `TODO(B5)`.

**The risk is total, not partial.** `verifyVv` catches `RuntimeException` and returns
false rather than throwing (`:117-130`). If the real gateway's encoding differs from
the refactor's assumption, every callback fails verification quietly.

**Action:** obtain a captured real gateway `EV`/`VV` sample and byte-verify. Same
class of external dependency as C-8; start it now.

Also unported: `DecryptAes256(byte[],…)` decodes with the platform default charset
(`:128/143/158`) while the Base64 path uses UTF-8 — another instance of C-13 on the
inbound side.

---

## C-11 — `lockAtMostFor` may be shorter than a full batch ✅ **CLOSED**

**Was: Low, arithmetic only.**

✅ **Fixed, and the fix changed shape twice before landing on the right one.**

`PT30M` → `PT60M` → `PT2H30M` → **`PT30M`**. Back where it started, for a completely
different reason.

The first two raises chased the arithmetic: batch-size × per-row, where per-row grew
from 23s to 26s to 66s to 93s as the timeouts moved. Each raise traded crash-recovery
latency for concurrency safety, one for one, and both got worse.

**The real fix was to stop the ceiling tracking the batch size at all.** A **PT10M
per-pass deadline** now bounds the work — checked at the top of each iteration so an
in-flight row always completes, never abandoned mid-203. The worst valid pass becomes
`deadline + one worst row` ≈ 11.6 min regardless of gateway health, so the ceiling
becomes a fixed crash-recovery constant at 2.6× headroom.

`ResultCheckLockCeilingTest` asserts the *relationship*, not the literal — which is
what caught the PT2H30M shortfall the moment the read timeout moved. A new test also
pins that the old batch-size model is now allowed to exceed the ceiling; if that ever
stops holding, the pass-bounding mechanism has been removed.

**Two side effects recorded:** the post-crash blind spot on unfinalized `'6'` rows
drops 150 min → 30 min; and `StrandedRecoveryService`'s hardcoded `ofMinutes(30)`
delegation bound, whose comment claimed it "matches `ResultCheckJob` exactly", finally
does.

`ResultCheckJob` is `lockAtMostFor = PT30M`, `batch-size` defaults to 100. The
derivation at `ResultCheckJob:58-97` gives 23s per row worst case (3s connect + 20s
read) plus 3000ms Woori pacing. 100 rows at that rate is ~43 minutes, exceeding the
ceiling. Under a slow gateway the lock can expire mid-run and a second instance can
start on rows the first is still processing.

Either the ceiling or the batch size needs to move.

---

## Stale documentation

Not defects, but they mislead readers of the code:

- `ResultCheckService:48-50` — `runOnce()` described as a no-op (C-9)
- `BulkReceptionResultService:74-80` — idempotency guarantee that does not hold (C-1)
- `DispatchPacer` Javadoc — "never in a back-to-back scan" (C-6)
- `ResultCheckService:257-260` — UTC parity argument (C-10)
- readiness audit `:150` — result-check lock reported `PT2M`, actually `PT30M`
- readiness audit `:150/154/155` — `RESULT_CHECK_CRON` "no default", stuck-monitor
  YAML "∅"; both now present
- `StuckPayFileMonitorJob:19-20`, `StuckPayFileMonitorService:74-75` — properties
  "appear in no application-*.yml"; they appear in all four profiles
- `application-uat.yml` and `application-prod.yml` are byte-identical in the
  dispatch block; PROD has no distinct posture in config

---

## Open items

**External — start now, answers take days**

1. Which of `x-coocon-accessIp` / `Authorization` / `signature` does the gateway
   enforce? (C-8)
2. Gateway behaviour on an unparseable error body — retry or drop? (C-5)
3. Gateway p99 response time for 202. (C-7)
3b. Captured real gateway `EV`/`VV` sample for byte verification. (C-15)
3c. `file.encoding` from the PROD WAS at `42.96.47.184`. (C-13)
4. Does the bank's 00302 endpoint deduplicate on its own side? (C-1 severity)
5. Does the bank spec require a settling period between 01301 and 00302?

**Internal — still unaudited**

6. ~~Coexistence~~ — answered, see C-0. Remaining decisions: DB target per
   environment, whether to make legacy's `U001` statements compare-and-swap, and
   whether to gate tier 1.
7. ~~Trigger coverage~~ — answered, see C-12.
8. ~~Charset~~ — answered, see C-13, C-14, C-15.
9. Full `PROC_STAT`/`PROC_STEP` transition diff (W4)
10. `operTimeChk` BATCH_ID asymmetry between `:272` and `:801`
11. Whether deployed legacy `.class` files predate the 2025-10-29 `Deposit_Data`
    layout change — if so the audit baseline is not what production ran

---

## Reconciliation with the existing audit corpus

The estate holds ~170 audit documents (110 in `wecms-api/docs/audits/`, 60 in
`WECMS_BATCH DEV/analysis/`). There is no master finding register, and prefixes
collide across documents. This memo must be reconciled before circulation.

### Status of each finding

| Ours | Status | Prior ID / document |
|---|---|---|
| C-0 | **New** | Both halves exist in `payment-dispatch-flags-deployment-readiness-audit.md` §4.2 and §5; never joined |
| C-1 | **New consequence** | Write point documented (`…2026-07-29.md` §6, §4.4) and rated ✅. Duplicate-execution consequence untraced |
| C-2 | Needs check | Not covered by the six questions asked |
| C-3 | Needs check | Not covered |
| C-4 | Partial | Transaction parity in `…2026-07-29.md` §6 |
| C-5 | Needs check | Not covered |
| C-6 | **Known, open** | `M-6` (`…2026-07-29.md:479, :545`), MED, routed to VAN Center. Originally `M-5` (`…2026-07-21.md:165`), fixed for the scan loop only |
| C-7 | Needs check | Not covered |
| C-8 | Needs check | Related to `B1`/`B5` |
| C-9 | **Known** | `P-5` (`payment-dispatch-flags-deployment-readiness-audit.md`) |
| C-10 | Partial | `H-7` → refined into `D-1` (`deployment-readiness.md:252`) |
| C-12 | **Scoping correction** | `payment-dispatch-operating-hours-flow.md` §5.1 claims six writers only — correct within `wecms-api`, incomplete across the estate |
| C-13 | **Known, open** | `K3` — production format version + offsets + charset, 🟡 partially answered. Risk called out at migration planning: `09-van-integration.md:127` |
| C-14 | Likely new (NFD) | Layout half covered by `…2026-07-21.md:14` and `test-coverage-audit.md:174-178` |
| C-15 | **Known, open** | `B5` (`reviews/gap-analysis/09-blockers.md`) |

### Finding we missed

`wecms-batch-business-parity-audit-2026-07-29.md` **H-2** (`:30`, `:514`): a
permanent 00302 failure leaves `TB_FIRM_FILE_MST` at `PROC_STEP='1'/PROC_STAT='6'`
and the pay file at `'6'`. Legacy sets both to `'3'`
(`FILE_DEPOSIT_TRANSFER_BATCH2.java:283-304` ↔
`BulkReceptionResultService.java:308-345`). A bulk payment the bank permanently
rejected displays to the user as still processing, indefinitely. Rated HIGH with
two-sided evidence.

**It shares a code location with C-1.** `BulkReceptionResultService:308` is the
`catch (BusinessException)` block. H-2 is the defect when it catches; C-1 is the
defect when it does not. Neither audit saw the other half. Both need fixing in the
same change.

Also outstanding: `H-4` (`…2026-07-21.md:149`) — on G999 the refactor may write
FAIL `'3'` where legacy leaves `'6'`.

### Contradictions in the corpus

Three statements would mislead a deployment decision and should be corrected at
source, not just noted here:

1. `deployment-readiness.md:195` — audit rows are "REQUIRES_NEW, survives
   rollback". True for the 01302 writer (`DispatchApiTranAuditWriter`), false for
   the 00302/01301 writer. This is C-1's mechanism.
2. `…2026-07-29.md` §6 rates the 00302 audit inside the callback transaction ✅,
   while `M-7` (`…2026-07-21.md:46`) treated the identical shape as a defect on the
   01302 leg and fixed it. The fix was scoped to one leg; the later audit ratified
   the asymmetry.
3. `final-legacy-parity-matrix.md` (2026-06-08) carries a "parity complete" banner
   over a body reading `Fully Implemented: 0`. Its `:12` self-demotes §1 to a
   historical snapshot; §1 was not updated in place.

More broadly, four documents make overlapping claims about the dispatch subsystem
at four dates (2026-06-08 → 06-13 → 07-21 → 07-29), each silently revising the
prior without retraction in place. Any consolidated matrix needs a per-line
"as of which audit" column or it inherits the contradiction.

### Renumbering required

`C-n` collides with two existing series: provisioning items `C-1…C-6`
(`deployment-readiness.md`) and implementation work-items `C1…C5`
(`implementation-status.md`). The house convention is a per-document,
severity-graded series (`H-n` / `M-n` / `L-n`) scoped to a dated file. This memo
should adopt it — e.g. `wecms-batch-independent-audit-2026-07-30.md` declaring its
own `H`/`M`/`L` series — with the reconciliation table above retained as the
mapping to prior IDs.

---

## Fix status — 2026-07-30

Batch 1 applied. 2455 tests, 0 failures.

| ID | Status | Notes |
|---|---|---|
| C-1 | **Closed** | `BulkExecutionInlineTxService` (new, `REQUIRES_NEW` per sibling); `catch (RuntimeException)` added; 00302 marker now written via `DispatchApiTranAuditWriter` and committed before the gateway call |
| H-2 | **Closed** | Permanent 00302 failure now writes firm-file `PROC_STEP='2'`/`PROC_STAT='3'` and pay-file `'3'`, matching legacy |
| C-2 | **Closed** | Three-way unmatched-callback branch; `RC=0000` only for genuine replay; audit row persisted for known-file cases, ops alert for unknown-file |
| C-4 | **Partly closed** | 00302 fan-out moved to `afterCommit`; `buildAndDispatchFile` ordering unchanged (root cause A, separate change) |
| C-5 | **Closed** | Both controllers return `EfsEnvelope` on error; WBE codes mapped to legacy `WCConst` equivalents |
| C-9 | **Closed** | `result-check.enabled` added, default false; `ResultCheckService:48-50` Javadoc corrected |
| H-4 | **Already fixed** | `ofPending()` covers G999, null response and VV-mismatch; `applyPending` writes `'6'`. `…2026-07-21.md:149` was stale, now marked closed |

Documentation corrections applied: `deployment-readiness.md:246` (per-writer table
replacing the blanket rollback-safety claim), `DispatchPacer` Javadoc,
`ResultCheckService:257-260`, `StuckPayFileMonitorJob`/`Service` property claims.

### Design decisions taken during the fix

**`afterCommit`, not per-sibling `REQUIRES_NEW` called from `handle()`.** A nested
transaction cannot see `handle()`'s own uncommitted `PROC_STEP` 0→1 advance, so it
would re-read `'0'/'6'` and skip the current file. `afterCommit` also matches
legacy, where BATCH2 was a separate process reading committed rows.

**Correction to the H-2 wording.** Legacy's permanent-failure branch sets firm-file
`PROC_STEP='2'` **and** `PROC_STAT='3'` — the step advances on the fail path.
Verified as SET (not WHERE) values in `IDO.TB_FIRM_FILE_MST_U001.xml`.

**`RuntimeException` on the 00302 leg is transient, not permanent.**
`isBulkExecutionRetryable` returns true for `G999` and for null/blank. A
`RuntimeException` means no gateway result code was received at all, so egress is
ambiguous and marking the payment permanently failed could contradict a transfer the
bank executed. Contrast `BulkReceptionService`'s H-3 branch, which marks FAIL because
the 00301 leg has no transient option and its pay-file claim would otherwise strand
unwatched.

---

## New residual — manual reconciliation states

**Medium. Two states now require an operator, and the detection path is off by
default.**

The fix leaves two conditions that resolve only by human action:

1. **Transient 00302 failure.** Legacy's transient branch was `continue` with no
   status write, so BATCH2's next tick re-selected the row at `(PROC_STEP='1',
   PROC_STAT='6')` and re-sent 00302 — indefinitely, every minute. The refactor
   cannot: the marker row commits before the send, so the idempotency guard would
   skip any re-drive. The state is terminal until reconciled.

   This is the safer behaviour — legacy's blind re-send could double-execute a file
   the bank already received — and it is approved: K9 Option A (the 00302 retry
   sweep) was declined 2026-07-29.

2. **`afterCommit` non-durability.** If the JVM dies between commit and the hook,
   ready siblings sit at `'1'/'6'` with no 00302 sent. C-2's `alreadyProgressed`
   branch now returns `RC=0000` on the gateway's 01301 retry, so nothing re-drives
   them. Stranding beats duplicate execution, but it is permanent.

Both land in `StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED` /
`BULK_EXECUTION_NOT_ISSUED`, detected by
`findStrandedBulkExecutionFiles` (`procStep='1'`, `procStat='6'`, past age
threshold).

**Consequence:** `StuckPayFileMonitorService` moves from optional to required in
production. It is read-only and safe to enable. And "reconcile with the VAN Center
before acting" needs to become a written procedure — an operator seeing this state
has no documented next step today.

---

## Fix status — batch 2

Applied. 2488 tests, 0 failures.

| ID | Status | Notes |
|---|---|---|
| C-14 | **Closed** | `WireField` + `PaymentWireWidthGuard` at entry (NFC → stripCrLf → replaceTone → UTF-8 byte count), wired into all five types at preview and save. No truncation added. Non-Latin reported distinctly from over-width |
| C-11 | **Closed** | `lockAtMostFor` raised to PT60M. Arithmetic confirmed: 3s + 20s + 3000ms Woori = 26s/row × 100 = 43.3 min. Test asserts the relationship with 20% headroom, not the literal |
| C-10 | **Withdrawn as written** | Not a bug. Both readers are correct against their own writers — `TRAN_DT` stamped UTC (`PymnHelper:2197`), `TRAN_TM`/`RQ_DTTM` stamped ICT (`RealtimeDispatchService:146-148`). Both now derive from one `Instant`, removing a midnight-straddle window. **Residual below** |
| Monitor | **Enabled** | SIT + UAT, cron `0 */5 * * * *`. Read-only, so exempt from the no-committed-scheduler rule. `bulk-file-reconciliation-procedure.md` written — discriminator, five read-only queries, one VAN Center question, action per answer |
| FIX 7 | **Reported, unresolved** | Refactor inherits the `MEMO varchar(500) → TAR_ACCT_RMRK varchar(150)` defect at `OverseasDispatchExecutor:275-276` and `:396-397`. 2 of 37 DEV memos already exceed 150. Four PO options in `overseas-memo-width-truncation-gap.md` |

Also corrected: `FixedLengthCodec:29-70` now states what legacy actually does on
overflow (returns `null`, caller appends the literal `"null"`, 852-byte record,
following fields shifted 146 bytes) and labels the divergence do-not-restore.

`BulkReceptionToneStripInvariantTest` — 9 call-site tests, mutation-verified:
removing one `replaceTone` call fails two named tests. Added because F1
tone-stripping silently regressed to dead code once already (re-implemented
2026-07-10), and every safety argument for the four wire fields depends on it.

### Deliberate divergences taken

**NFC now mutates persisted values, not only the check.** Check-time-only NFC would
leave a hole: NFD input passes entry, then misses tone-stripping at dispatch (the
table is precomposed-only) and fails at the codec. NFC is the identity for every
precomposed value, so this only moves data that would otherwise produce a corrupt
record. Legacy stored input verbatim.

**PT60M over a smaller batch.** Lowering `batch-size` to ~65 would fit PT30M, but the
cap is also the drain rate and this sweep is the only thing finalizing already-sent
payments. Trade accepted: a crashed node holds the lock for up to an hour.

---

## Open residuals from batch 2

**R-A — `PROC_DTTM` may mix zones.** `TRAN_DT` is UTC-derived, `TRAN_TM` is
ICT-derived. If any 14-character timestamp is built by concatenating them
(`tranDt + tranTm`, `dttm14`), the result is not a timestamp in either zone: at 18:30
UTC on July 30, UTC date + ICT time yields `20260730013000` — about one day in the
past. Every stranded finder filters `procDttm < :procDttmCutoff`, so a row written
between 17:00 and 24:00 UTC would qualify as stranded immediately. With the monitor
now enabled in SIT/UAT this would appear as recurring evening false alerts. Legacy
stamped `PROC_DTTM` from a single `to_char(now(),…)` in the IDO XML. **Unverified —
needs checking.**

**R-B — 203 audit row `TRAN_DT` zone.** `reflectOutcome` stamps the 203 audit row's
own `TRAN_DT` in ICT while every 202 row's is UTC, so between 17:00 and 24:00 UTC the
audit row sits one day ahead of the row it audits. `ORG_TRAN_DT` preserves the
linkage, so the writer was left alone, but any reconciliation query filtering on
`TRAN_DT` across both must know.

**R-C — `senderName` cannot be guarded at entry.** It is derived server-side from
`ACCT_MSTR.ACCT_NM` (100 characters) at `OverseasDispatchExecutor:223`; no upload
column, no DTO field. A Korean-named account gives 100 × 3 = 300 bytes against a
150-byte slot, caught only by the codec backstop at dispatch, after approval —
exactly the failure mode the entry guard exists to prevent. Plausible in a
Korean-owned group. Guard belongs at account registration or as a pre-dispatch check.

**R-D — new message keys are EN-only.** Three keys, no `vi`/`ko`/`km`. The entry
guard's value is an actionable message; English text to a Vietnamese operator is less
actionable. Per project policy translations are not invented, so this needs a
translator.

**R-E — untested paths.** No HTTP/controller-level assertion of the guard; overseas
`bnfc_nm` preview (cross-filled from contact master) untested; `senderName`
untested end-to-end; FIX 7 untested by design.

---

## Residual verification — R-A refuted, two new findings

**R-A does not hold.** Every 14-character stamp in `domain/payment/dispatch` derives
from a single `LocalDateTime.now(ICT)`. Only two sites concatenate
(`BulkReceptionResultService:205-207` and its callee), and both take date and time
from the same `now`. The stranded finders compare ICT cutoffs against ICT-written
`procDttm`. Legacy stamped `PROC_DTTM` from a single DB-side
`to_char(now(),'yyyymmddhh24misss')`; the refactor is structurally equivalent.

*Near-miss worth a comment:* `ExpenseDispatchExecutor:150-153` and the same shape in
`Vendor`/`InternalTransfer` compute a UTC date and an ICT `dttm14` four lines apart.
Safe only because `dttm14` is formatted whole and never assembled from `tranDt` —
and `regTm` is also UTC, so even `tranDt + regTm` would be self-consistent.

*Relevant to C-12:* legacy's `now()` resolves in the DB session zone (Asia/Seoul), so
legacy `PROC_DTTM` is Seoul time and the refactor's is ICT — two hours apart. On a
shared DB a legacy-written row looks two hours newer to the refactor's finders than it
is. Delays detection rather than causing false positives; conservative direction.

### R-F — callback lookup keyed on the clock, not the message

`BulkReceptionResultService:205-213` looks up `TB_FIRM_FILE_MST` with `tranDt` derived
from `LocalDateTime.now(ICT)` at callback time, while the row's `TRAN_DT` was stamped
at file-build time. A file built at 23:58 whose 01301 arrives at 00:02 never matches.

`handleUnmatchedCallback` filters on the same clock-derived value, so after the C-2
fix the gateway now retries and every retry fails identically — noisier than the
previous silent `RC=0000`, equally lost.

**`FIRM_FILE_NM` already carries the authoritative date** — `S_{VAN}_{TYPE}_{round}_{date}`
at `BulkReceptionService:513`. Deriving `tranDt` from `req.fileNm()` removes the
rollover. Same defect class as C-2: trusting an ambient value when the message
carries the real one.

*Open:* whether `OperatingHoursService` can permit a bulk dispatch near ICT midnight
at all. If `EXE_DIV` windows are business hours only, this is unreachable in practice.

*Also open:* which `StuckPayFileCause` covers a file stranded at
`PROC_STEP='0'/PROC_STAT='6'`. `findStrandedBulkExecutionFiles` filters
`procStep='1'`, so the 0/6 case may be detected only via the parent pay-file sweep.

### R-G — no wire-level charset assertion

`FixedLengthCodec.put` checks byte width, not charset. A value under the width
containing non-ASCII passes silently — `한국전자` is 12 bytes and clears a 150-byte
slot. Two distinct failure modes, and only the first is guarded:

| Condition | Behaviour |
|---|---|
| `> 150` bytes | `IllegalArgumentException` → pre-egress guard → `PROC_STAT='3'`. Fails safe, but the operator sees "file build failed: IllegalArgumentException" with no mention of the account name |
| `≤ 150` bytes, non-ASCII | Passes to the wire. No error, no alert |

`PaymentWireWidthGuard` is the only non-Latin check and covers only entry-validated
paths. Server-derived, admin-written and JEX-written values bypass it.

Compounds C-13: if PROD `file.encoding` resolved to US-ASCII, legacy wrote `?` for
these characters and the bank has never received a multibyte byte on this channel.
The refactor would send genuine UTF-8 — a silent change in what the bank receives.

Graduated fix: log and alert at the codec now, harden to reject once K3/C-13 confirms
the bank's charset.

### R-C refined

`ACCT_MSTR.ACCT_NM` is `varchar(100)` **characters** — 100 Hangul is 300 bytes against
a 150-byte slot. DEV is clean today (32 rows, max 43 characters, all ASCII), and the
widest values are the Haengsung entity names. No validation exists at any write path:
`AccountRegistrationService:135/170`, `SettingsBankAccountService:365`, with only
`@Size(max = 100)` on both DTOs. Legacy has none either — `StringUtils.sanitize`
only maps Unicode space separators.

Scope is overseas-only: `setSenderNm` has two callers, `OverseasDispatchExecutor:271`
and `:395`; the four domestic executors leave the column null.

Guard belongs at overseas payment creation, **not** at account registration —
`ACCT_NM` is bank-adapter-supplied and the user cannot edit what the scrape returned.
`WithdrawalAccountRow` does not currently carry `acctNm`, so the picker cannot even
display the offending value.

---

## Configuration work — 2026-07-30

Separate from the findings above, but it produced one.

**Bare-placeholder migration.** All 169 `${VAR:default}` placeholders across the five
config files converted to bare `${VAR}`. Values now live only in `.env`; a missing
variable fails startup rather than booting on a default nobody chose. All four
profiles verified complete — 0 missing, 0 literal `${` in any value, 0 unmarked dead
keys. `.env.uat` and `.env.prod` created as scaffolds with `__SET_ON_SERVER__`
sentinels (gitignored, never committed).

**F-6 remains closed, by a different mechanism.** The in-yaml defaults were the
remediation for `scheduler-legacy-parity-audit` F-6, guarded by
`SchedulerConfigurationDefaultsTest`. F-6's cost argument — a deploy needing ~12
variables present merely to boot with everything off — is void now that sit requires
72 regardless. Its safety intent is preserved and strengthened: the app refuses to
start until someone states the value. Test rewritten as
`SchedulerConfigurationContractTest`, asserting bare-in-yml **and** present-in-example
**and** inert-value — a stronger contract than before, since it can now check the
`.env` side.

**`.env.batch.example` renamed** to `docs/deployment/batch-dispatch-variable-reference.md`.
Nothing read it; all 37 keys were already in the real `.env` files. It was
documentation carrying a config filename, and had never been committed.

---

## New finding — config silently overrides code

**Medium. A fix can land in code and have no effect for days.**

FIX I set `stuck-monitor.enabled` and `alert.enabled` to `true` as yml defaults on
2026-07-30. `.env.sit` had carried `false` for both since 07-28. An `.env` value always
beats a yml default, so **FIX I never took effect on SIT** — the monitor did not run,
and the two manual-reconciliation states it alone detects were undetectable, for the
entire period everyone believed the fix was live.

The same gap existed in `.env.uat.example`. Neither was a decision; both were files
written before the fix and never updated after.

Corrected: `.env.sit`, `.env.sit.example` and `.env.uat.example` now set
`STUCK_MONITOR_ENABLED=true`, `CRON=0 */5 * * * *`, `ALERT_ENABLED=true`. Prod stays
off — correct, since the refactor is not deployed there.

**Why it generalises.** Any fix delivered as a yml default is invisible to a profile
whose `.env` already sets that key. The bare-placeholder migration removes the class
of bug entirely — with no yml default, `.env` is the only source and there is nothing
to silently lose to.

*Related residual:* alerts still reach only the log until `WECMS_EFS_ENABLED=true` and
a `TB_VAN_API_INFO.API_KEY` row exist. Detection is live on SIT; delivery is not.

---

## Unversioned work — risk

The deployed war is built from code that is not in git:

- 84 modified files (57 `src/main`, 27 `src/test`) unstaged
- 59 previously-untracked source files, now staged but uncommitted

`JobLockNames.java` is in that set, and its constants must match the deployed ShedLock
rows. If those values are lost and regenerated, two instances hold different lock names
and both run — a single-runner failure on a payment sweep.

`compileJava` is green in a pristine tree; `compileTestJava` needs the full set.
Recommended: commit everything to a working branch, stating in the message that it
includes in-flight edits from multiple authors and is not for merge without owner
review. Splitting is not possible — the tests exercise the whole change surface.

---

## Handover reconciliation — 2026-07-31

Source: Mr. Kang's `WECMS_BATCH_인수인계.html` plus the nine `00~08_*.md` detail
documents in `WECMS_BATCH DEV/handover/`. 26 agreements, 12 disagreements, 2 items
with no legacy basis.

**The handover is accurate where it speaks.** Every defect below came from a boundary
rule it never wrote down.

### New HIGH — D-1, per-line results silently discarded

Legacy strips the `FIRM_FILE_NM` prefix in **two** places: the MST lookup
(`R006:54`) and the detail update (`TB_FIRM_FILE_DATA_U001:34`). The refactor ported
only the first. Sent files are `S_`, result files are `R_`, so on a real 01302
callback the detail map is empty — every per-line `PROC_RSLT_DIV` / `RS_CD` is lost
while the file still advances to `PROC_STEP='3'`.

A bulk payroll completes, file-level status looks correct (trailer aggregates are
independent), and no individual transaction result is recorded.

`BulkExecutionResultServiceTest2:76` uses the `S_` name, so the test passes against a
filename the bank never sends.

### New MED — D-3, file round wrap

`R002.xml:34-49` wraps the round to 1 past 99 for Woori (970457) and past 999 for all
others. `nextFileRound` is a bare `max+1`. Past 999, `%03d` emits 4 characters into a
3-byte header field — every following field shifts and the record is no longer 1000
bytes.

### New MED — X-1, blocked-char guard drops rows with no state

`PaymentBlockedCharGuard.assertCleanForDispatch` throws uncaught on both rails: no
dispatch, no `PROC_STAT='3'`, no `RS_MSG`, no alert. The guard traces to a WeCMS-side
P0 decision (task-44508) so it is intended; its failure handling is not
legacy-equivalent.

### Closed as never documented

- **Charset (C-13).** Absent from all nine documents. The only statement is §4.4's
  heading "CP/UTF-8 byte 기준 패딩" — naming no codepage. Confirms this is a server
  fact, not a document fact.
- **IP allow-list (C-8).** `08 §8.5.5` explicitly defers it: the gateway does not
  validate `Authorization`/`signature`, but whether an allow-list keys on
  `x-coocon-accessIp` is "recommended to be confirmed with Infra". Deferred in
  writing, never answered. We send none of the three.
- **Timeout SLA (C-7).** No SLA anywhere. Legacy 30s/60s documented only as code
  constants. Our 3s/20s came from the vendor PDF sample, not legacy, and no audit
  compared them.

### Falsely marked resolved

`08 §8.5.6` item 6 marks DDL/indexes 해소됨 on the strength of a promise. Two months
on, zero SQL/DDL files exist in the tree. All three index scripts were written against
a reverse-engineered column list, blind to the real PK/UK and existing index set.
**Verifiable today** via `pg_indexes` / `pg_constraint` on JEX DEV — no external
dependency. Cheapest open item in the audit.

### Two divergences needing sign-off

- **D-4.** Legacy returned `0000` on every non-throwing path; only a thrown exception
  changed RC. We now send `G998` (sibling fail-stop) and `9999` (count mismatch), and
  `0000` where legacy sent `G004`. Our C-2 reasoning holds — legacy could afford to
  swallow because the pollers retried — but this is a bank-visible contract change
  and needs VAN Center agreement.
- **D-7.** `ANT_DIV_CD` is `bpchar(5)` holding both 1- and 5-char forms. Legacy keys
  the DTL on the raw value, so `'E'` never matches `'EBANK'` → `NO_CRITERIA` →
  `PROC_STAT='3'`. Measured on JEX DEV. `AntDivChannel.canonical` fixes it, meaning
  **the refactor will dispatch payments legacy rejected.** Mr. Kang should see the
  count.

### Confirmations

D-2 is H-2, already closed in batch 1. D-5 is C-6, open. D-6 is the result-check cron
disabled by default, which silently voids the handover's own principle #18 —
no-response is not failure, hold at `'6'`, let 203 confirm. With the cron off, nothing
confirms.

---

## Fix status — batch 3 (2026-07-31)

Applied. 2577 tests, 0 failures.

| ID | Status | Notes |
|---|---|---|
| D-1 | **Closed** | `findForExecutionResult(tranDt, fileSeq, fileNm)` added with the substring predicate. Sweep of all 7 `FIRM_FILE_NM` lookups found exactly one wrong — legacy's own `R004` is exact-match while `R006` strips, because 01301 echoes the `S_` name we sent and only 01302 carries the bank's `R_` file |
| D-3 | **Closed** | Round wrap ported — 99 for Woori, 999 otherwise, reset-to-1 not modulo. Applied per split file, since legacy calls `R002` inside its loop while we compute `baseRound` once |
| X-1 | **Closed** | Blocked-char guard kept (task-44508); failure now reaches `PROC_STAT='3'` + ops alert on both rails, matching each rail's existing rejection idiom |
| D-10 | **Partly closed** | DEV schema verified. `08 §8.5.6` reopened, `해소됨` removed |
| D-8 | **Closed** | Base64 repair ported to both `decryptEv` and `verifyVv`. Pass count remains `TODO(B5)` |

**Bonus find during D-1.** `rslt.setRcptUseAddrSi(fd != null ? … : null)` — the
`fd == null` path was silently nulling the town name on every `TB_FIRM_FILE_RSLT`
row. Same root cause, fixed by the same change.

**Test camouflage.** The old fixture used one `FILE_NM` constant for three distinct
things, and `auditRow_tranDt_…` passed both before and after the fix. Split into
`SENT_FILE_NM` / `RESULT_FILE_NM`.

### D-7 correction

The earlier framing — "the refactor will dispatch payments legacy rejected" — was
**wrong**. `TB_BATCH_SCHEDULE_DTL` has no realtime row for EBANK or SWIFT under
either bank, so canonicalising `'E'` → `'EBANK'` still yields `NO_CRITERIA` → `'3'`.
Both engines fail identically.

Correct version: 4 rows on JEX DEV carry a 1-char `ANT_DIV_CD` (all HAENGSUNG, all
Shinhan); 2 reached `PROC_STAT='3'` attributable to the mismatch. **0 outcomes change
today.** The canonicalisation removes one of two independent blockers; seeding the
realtime EBANK/SWIFT DTL rows is the other, and that is the open PO routing decision.

### Schema finding

Every dispatch table carries **only its primary key** — no secondary index, no unique
key, no foreign key, no check constraint. Legacy has been sequential-scanning these in
production. Invisible at DEV's 93–1,304 rows; unknown at PROD scale. None of the five
index scripts duplicates an existing index, and all 15 referenced columns exist with
sane types, so the reverse-engineered column list held up.

### D-4 — still needs a VAN Center decision

| Branch | Legacy RC | Ours |
|---|---|---|
| File not at `0`/`6` | `G004` (throws) | `0000` |
| Sibling fail-stop | `0000` | `G998` |
| Count/amount mismatch | `0000` | `9999` |
| `result_cd ≠ BT00` | `0000` | `0000` |
| Success | `0000` | `0000` |

Legacy reached step 6 on every non-throwing path and always answered `0000` with a
rebuilt EV. Only a thrown exception changed the RC. Our C-2 reasoning — legacy could
swallow because the pollers retried — is coherent but is an assumption about gateway
semantics, and it cuts both ways: legacy's `G004` made the gateway retry *and* alert,
our `0000` tells it to stop; conversely `G998`/`9999` may trigger retries of a callback
we have fully processed. Do not ship on inference.

### Verification gap — no test executes JPQL

The project has no embedded database and no Testcontainers, so no test in the suite
ever runs a query. FIX 1 added a new `@Query` with `substring(d.firmFileNm, 3)`,
exercised only through Mockito;
`FirmFileDataExecutionResultQueryContractTest` pins the query *text*, not its
behaviour.

Spring Data validates `@Query` at context startup, so booting proves it **parses**.
Only a real bulk file proves it returns the right rows.

**First SIT check after the first 01302 callback:**

```sql
SELECT count(*) AS total, count(rs_cd) AS with_result,
       count(proc_rslt_div) AS with_div
FROM tb_firm_file_data
WHERE tran_dt = to_char(now(),'yyyymmdd');
```

`with_result = 0` while `total > 0` means the query returned empty and D-1 is not
actually fixed. Repeat on `tb_pay_file_data`.

---

## PROD data findings — 2026-07-31

Run against the live JEX databases. These are inherited legacy conditions, not
refactor regressions.

### D-1 confirmed at production scale

`tb_firm_file_data` is 100% `S_` (5,472 rows PROD, 12,753 UAT); `tb_firm_file_rslt`
is `R_` (4,434 / 5,526). The premise behind the fix holds against real data. Four
anomalous `S_` rows exist in PROD's result table; the substring join handles them
either way, and the one inspected pairs cleanly with its data rows.

Also established: `PROC_RSLT_DIV` is populated at insert, not by the result
write-back — `with_div == total` on every date including DEV, where `with_result` is
zero throughout. **Only `RS_CD` proves the 01302 result landed.** DEV has never
completed a single 01302 cycle in 20 dates, which is precisely why D-1 stayed
invisible: there was no correct behaviour on DEV to diverge from.

### New HIGH — false "all success" finalisation

**Inherited from legacy. Reproduced line-for-line in the refactor.**

`finalizePayFileStatus` computes `tot` as siblings at `'0'` or `'3'` only. With no
sibling terminal, `tot = 0` and `succ = 0`, so `tot == succ` is **true** and the
parent finalises to `'0'` — all success. An empty count satisfies the equality.

Legacy has the same hole: `R003` barriers only on `PROC_STEP='0'`, and `R005` counts
only `'0'`/`'3'` into `TOT`.

Two live PROD instances, both `siblings=1`:

| Date | Lines | Parent | File |
|---|---|---|---|
| 20251128 | 9 | `'0'` | `2/6` |
| 20251201 | 2 | `'0'` | `2/6` |

It also fires transiently on every multi-file batch — the parent reads "all success"
from the first 01302 until the last, self-correcting only because the happy path
recomputes the same value.

**Fixed** by two guards: `awaitingSiblings` defers on any sibling at `PROC_STAT='6'`
regardless of step (divergence from legacy `R003`, needs PO sign-off), and an explicit
`tot == 0` guard, since the equality is the actual defect.

*Diagnostic note:* `finalizePayFileStatus` runs only from the 01302 handler, so for
these two a callback **did** arrive and the result was lost in download or parse. The
other 33 stranded files may never have had one. Different questions for the bank.

### New cause — BULK_EXECUTION_RESULT_NOT_RECEIVED

`findStrandedBulkExecutionFiles` was called with `PROC_STEP='1'` only. The state
production actually produces is `'2'`/`'6'` — 00302 sent, 01302 never returned — which
no sweep detected. It surfaced through the parent as `AWAITING_EXECUTION_RESULT`,
described as "often not a defect".

`PROC_STEP='2'` is positive evidence the gateway accepted the instruction (only
`BulkExecutionInlineTxService` writes it, only after `0000`/`BT00`), so the disposition
is **never re-drive** — the opposite of `NOT_ISSUED`.

### Live PROD backlog — 35 files, ~477 lines

`2025-08-14` → `2026-05-29`, all at `PROC_STEP='2'`/`PROC_STAT='6'`. Largest:
`20251010`, `S_GV00000006_SHB_002_20251010`, **412 lines, 5,101,563,524 VND**.

Legacy could not recover these either — BATCH2 selects `(step 1, stat 6)`, so a file
at step 2 was invisible to its 60-second self-heal. Nine and a half months
unnoticed.

**Operational action:** confirm with the bank whether these executed. `PROC_STAT` on
the parent pay files is not trustworthy for this set — some read `'0'` via the
finalisation defect. Re-derive from the firm-file rows before quoting status to a
payment owner.

### New divergence — 01302 audit row name

Legacy writes the callback's `R_` name into `TB_FIRM_BAT_API_TRAN` for the 01302 leg
(`API2_act.jsp:189`); the refactor writes the sent `S_` name. Every diagnostic query
in the reconciliation procedure joined on the exact name and therefore returned
`audit_01302 = 0` for all 35 legacy files — an artifact that would have led an
operator to diagnose "no callback arrived" when the opposite may be true.

All queries now use the substring join. The 00302 idempotency guard keeps its exact
match — widening a send-guard is the wrong direction. Whether to align the writer to
legacy's `R_` is logged as an open PO decision; it changes audit data shape.

### PROC_DTTM divergence, now documented as intentional

`BulkReceptionResultService:308` sets `PROC_DTTM` on the 0→1 advance; legacy's `U002`
does not. That divergence is the only reason the `'1'`/`'6'` sweep can see its own
candidates — every legacy `'1'` row on DEV has a null `PROC_DTTM` and would be skipped.

Coexistence consequence, now stated rather than incidental: on a shared JEX DB,
legacy-written `'1'`/`'6'` rows are invisible to our sweep. Arguably correct scoping,
but it is a decision.

Blank `PROC_DTTM` was being excluded by the retention floor **by accident**; an
explicit `<> ''` predicate was added, since the repository Javadoc contemplates
removing that floor.

### Schema note

Every dispatch table carries only its primary key — no secondary index, no unique
key, no foreign key, no check constraint. Legacy has been sequential-scanning these
in production. None of the five index scripts duplicates anything that exists.

---

## Final code round — 2026-07-31 / 08-01

Everything below closed after the SIT deploy. Suite: 2,674 tests, 0 failures.

### C-6 closed — dispatch is off the request thread

`RealtimeDispatchQueue`: bounded `ArrayBlockingQueue`, one worker
(`core = max = 1`), lazily created so a disabled rail costs no thread.

`RealtimeDispatchRegistrar` no longer holds a reference to `RealtimeDispatchService`
at all — its only collaborator is the queue. A 202 cannot be issued from the request
thread **by construction**, not by discipline. `afterCommit` enqueues and returns.

`DispatchPacer.pause` runs in a `finally` inside `drainOne`, so exactly one pace per
row on both the success and failure paths, keyed on that row's own `OUT_BANK_CD`
re-read from `TB_PAY_TARGET`. Tree-wide, `DispatchPacer` is now injected in two places
and `pause` invoked from two: the realtime rail and the 203 scan.
`recoverPayTargets` enqueues rather than dispatching, so there is one pacing point.

**Legacy's D-A divergence is now structural.** `NAPAS_REAL_TIME_BATCH` hoists
`per_request_delay` outside its loop and only ever raises it, so one Woori row pins
3000ms onto every later bank. Our key is resolved per ticket, so no variable spans
iterations.

*Two claims that were attached here have both since been withdrawn.* The "effective
rate toward non-Shinhan, non-Woori banks increases up to 30×" line was withdrawn
2026-08-01 — the affected population is empty, so the claim had no subject. The
`PER_DELAY_DEFAULT` 100 → 3000ms raise that replaced it was withdrawn 2026-08-05, on
the handover. The default is legacy's 100ms and the Javadoc records both withdrawals.

**Shutdown:** `@PreDestroy` latches, stops accepting, drains for 30s. An in-flight
row is never interrupted before the deadline — a 202 already at the gateway is not
abandoned mid-call. Tickets still queued are discarded and logged at ERROR; their rows
stay `'9'`.

The worker was first written non-daemon — "a queued transfer is worth blocking exit
for" — and it wedged the Gradle test JVM, which is exactly what a production process
missing `@PreDestroy` would do. Now daemon, so the outcomes are bounded-drain or
hard-kill, with no third state where the process hangs holding unwatched work.

**Activation ordering is a deploy rule, not a comment.** `stuck-monitor.enabled` must
be true *before* `realtime-dispatch` is enabled — that sweep is the sole detection for
tickets lost from the in-memory queue. SIT/UAT ship the monitor ON; local/prod ship it
OFF, so a prod activation following the current step order would enable the money rail
with no backstop.

### Two-phase dispatch applied

Phase A: all N split files' `TB_FIRM_FILE_MST` + `_DATA` rows, plus the encode and the
group-wide blocked-char guard, in one transaction — committed before anything leaves.
Phase B: per file, no transaction spanning an external call.

Restores legacy's `:696-697` commit-then-upload ordering. Two judgement calls taken
over the original spec, both better: encode in phase A (preserves "over-width field ⇒
nothing committed"), and the blocked-char guard group-wide (matches legacy's Gate A at
`:113-266`; per-file rejection was accidental).

Consequence to know: a phase-B break at file 3 of 5 leaves files 4 and 5 with
committed rows at `PROC_STEP='0'`/`PROC_STAT='6'` that were never dispatched. Correct —
the barrier needs those rows — and the new `'0'` sweep is what surfaces them.

### New sweep — PROC_STEP='0' orphans

DEV data changed the design. The existing finder excludes `PROC_DTTM IS NULL`, and
neither side stamps it on insert, so routing `'0'` through it would have selected 2 of
30 rows and **zero** of the orphans two-phase dispatch creates.
`findStrandedReceptionFiles` ages on `COALESCE(PROC_DTTM, REG_DTTM)`.

Two causes, split on the 00301 audit row: `BULK_RECEPTION_RESULT_NOT_RECEIVED`
(present → the bank has the file, reconcile forward, never re-drive) and
`BULK_RECEPTION_NOT_ISSUED` (absent → never sent, safe after checking the parent).
Plus `BULK_BATCH_PARENT_FAILED` when a `'0'` or `'1'` file's parent pay file is `'3'` —
fixing the mislabel where `BULK_EXECUTION_NOT_ISSUED`'s runbook would have told an
operator to re-drive a failed batch with unsent siblings.

### Corrections of record

**`REALTIME_FX_INQUIRY_BLOCKED` was wrong.** The 204 pre-step allocates its **own**
sequence (`runFxInquiry:355`), so it can never match the `existsByTranDtAndBizKeyAndTranSeq`
guard keyed on the pay-target's. Legacy does the same — fresh `ccy_exc_seq` at `:299`,
pay-target `dep_seq` at `:273`. No divergence, no defect. Renamed
`REALTIME_DISPATCHED_NOT_CLAIMED` and documented around the real mechanism, with the
wrong explanation kept so it is not re-derived.

**The 822 multibyte rows predate tone stripping.** Both files are dated `20251010`,
nineteen days before `IDO.TB_FIRM_FILE_DATA_C001` version `20251029205935`. Legacy
writes post-strip to both the table and the wire (`:1069-1095` before the insert at
`:1098`), so those rows are inconsistent with the current writer entirely.

**And they are one payment submitted twice.** Same `BIZ_KEY`, different `FILE_SEQ_NO`
(80, 81), identical `org_seq` range 1–412, identical totals — but **different payload
fingerprints**. Rejected at reception 08:41, payload edited, resubmitted 12:57,
stranded at execution. So the first rejection was about data, not charset. The
candidate-cause note on the 5.1B file is downgraded: it is a lost 01302 like the other
34.

### D-L — the tone gate misses Ơ ơ Ư ư

`hasVietnameseCharacters` covers `U+00C0-00FF`, `U+0100-017F`, `U+1EA0-1EF9`.
`Ơ ơ Ư ư` are `U+01A0/01A1/01AF/01B0` — table members that sit in the gap between the
second and third range. A name whose only diacritics are those (`Sơn`, `Vương`,
`Hương`, `Tư`) goes to the bank untone-stripped, and handover pitfall ④ states
plainly: 미적용 시 은행 측 거절 — *if not applied, the bank rejects it*.

`VietnameseText.replaceTone` applies the table unconditionally. That is a **legacy
defect corrected**, same shape as `ANT_DIV_CD` canonicalisation — not a regression.

Accepted consequence: `Sơn` → `Son` where legacy kept the diacritic. Zero production
instances of the gap firing, since the only multibyte rows predate stripping.

### C-13 closed — negatively

The channel is ASCII, by vendor contract and by observation:

- Handover pitfall ④ — untone-stripped values are rejected by the bank
- Zero files carrying non-ASCII have ever reached `PROC_STEP='3'`
- All six UAT samples are 0 bytes above 0x7F

PROD `file.encoding` is no longer a blocker for anything. The `.xlsx` request stays
useful only for the `data_amt` 23-vs-22 width question.

Codec stance is final: **observe and alert**, not reject. Rejecting would be
unreachable for entered text, stricter than legacy for `rcptUseAddr`, and would refuse
batches over another system's bytes.

### Space-separator normalisation — a missed port, wider than thought

Legacy applies `StringUtils.sanitize`'s `[\p{Zs}]` → space on the payment path in two
layers: every entity setter (`ScdulPymnSumrDetl:220/259/272/285`,
`OvrsPymnSumrDetl:170-267`) and explicitly throughout `ErpDataRefreshHelper`.

`LegacyTextNormalizer.sanitizeSpaces` already covered the guard path. The real hole
was **the HAENGSUNG ERP import** — `ErpPaymentRefreshMasterImporter` took `jeokyo`,
`acetNm`, `acetAddr`, `trAcctBankNm` and `fileNm` raw, on a path that never touches
`PaymentWireWidthGuard`. Fixed, with the legacy citations.

This matters because NBSP is **invisible** — it survives tone-stripping, passes the
gate, and reaches the wire as two bytes. The one production instance
(`S_GV00000006_SHB_001_20251128`, four `U+00A0` in `rcpt_use_addr`) is also one of the
two false all-success files.

### Still open: two tone-stripping implementations

`ErpPaymentRefreshMasterImporter:337-342` strips with `Normalizer.NFD` + `\p{M}`;
everything else uses the 134-entry table. They disagree on `Đ`/`đ` — `U+0110` has no
canonical decomposition, so NFD leaves it while the table maps it to `D`. `Đức` becomes
`Đuc` on the ERP path and `Duc` everywhere else.

Legacy uses the table. Aligning is the fix.

### Also closed this round

| Item | Resolution |
|---|---|
| C-7 read timeout | 20s → 60s. Guide §3.2 states 30s normative; the old value came from a sample the PDF marks non-binding |
| C-7 connect timeout | 3s → 30s, matching `ConnectionUtil:16` |
| C-8 headers | `x-coocon-accessIp` restored (legacy sends it). `Authorization`/`signature` deliberately not — placeholder literals the gateway ignores |
| C-15 | Base64 alphabet repair ported; single URL pass kept. The vendor's own §6.2 sample calls `UrlDecode` on the *encrypt* path, which is the documented mechanism behind legacy's repair |
| Lock ceiling | Now tracks the PT10M pass deadline, not batch size: PT2H30M → PT30M. Post-crash blind spot drops 150 min → 30 min |
| S-11 | Legacy's audit row keeps `RS_CD` NULL on every failure path — `apiCall` throws before `updateApiTran`. Not ported; we finalise on every branch |
| S-12 | Dismissed. `jex.util.date.DateTime` lowercases the format first and maps `mi` → minutes, so lowercase `mm` was always months |

### Remaining — none of it code

| Item | Owner | Note |
|---|---|---|
| **B2** — real per-VAN API key | Infra | Blocks **PROD only**. DEV/SIT hold the vendor's published TEST key; C-3's `RS_CD=1000` suggests the crypto round-trip works. One scratch-key 201 settles it |
| **B4** — SFTP credentials, key file, paths | Infra | Blocks the entire bulk rail. Ten-step checklist written |
| **R-03 + callback allow-list** | Dev + Ops | **One item, not two.** Controllers are `permitAll`; the IP allow-list is the mitigation. `.env.sit` ships it empty with `ENFORCE`, so the first bulk callback 403s and the symptom is silence |
| **R-02** action-level authorization | **Dev code** | Pre-UAT/PROD. Server-side `@PreAuthorize` plus the per-action authority source; frontend ships a permissive stub |
| **In-memory revocation store** | **Dev code** | Pre-UAT/PROD. Admin lock/demote writes to one JVM, so a demoted user keeps a privileged token on every other node for up to 1 hour — the exact scenario F-03 prevents. No environment is *confirmed* single-instance |
| One bulk file end to end | Verification | The only remaining proof |
| 35-file PROD backlog | Operations | Worksheet and query delivered. Outside every retention floor by design |
| ERP charset asymmetry | Recorded (D-M) | Decided: keep as-is. Legacy sends `ü` raw too; we are equal on charset, stricter on blocked characters |
| No user-facing signal for a lost dispatch | Product | Minimal fix is a read-side projection over `PROC_STAT='9'` — no schema change |
| Queue observability is log-only | C-5 | Counters already shaped for Micrometer; registering them is a constructor edit |

**Removed from this list since the last revision:** `PROC_STEP='1'` COALESCE
(decided and applied — all three sweeps now age on
`COALESCE(NULLIF(procDttm,''), regDttm)`), and the `PROC_STEP='0'` sweep (already
built, classified, and green).

---

## Callback RC contract — matched to legacy, 2026-08-01

The D-4 table in earlier revisions was two rounds stale. `FIX 2` had already reverted
the sibling fail-stop and count/amount branches to `0000`; the real divergences were
elsewhere. A full branch-by-branch comparison of both legs found five, four of which
are now matched.

### 01301 — bulk reception result

| Branch | Was | Legacy | Now | Legacy line |
|---|---|---|---|---|
| `ID` missing | `G002` | `G001` | ✅ `G001` | `API1:68-77` |
| `RQ_DTIME`/`TNO`/`EV`/`VV`/`EM`/`VM` missing | **unchecked** | `G001` | ✅ `G001` | `:68-77` |
| `bank_cd` / `prtn_id` missing | `G002` | `G002` | ✅ unchanged | `:125-129` |
| Six detail fields missing | `G002` | `G001` | ✅ `G001` | `:131-147` |
| Unprovisioned VAN key | `G999` | `G003` | ✅ `G003` | `:99-110` |
| Unmatched file | `G999` | `G004` | ✅ `G004` | `:168-170` |
| Ambiguous name (>1 row) | `G999` | — | `G999` kept | refactor-only (FIX J) |
| Business outcomes + success | `0000` | `0000` | ✅ unchanged | `:409-423` |

### 01302 — bulk execution result

| Branch | Was | Legacy | Now | Legacy line |
|---|---|---|---|---|
| `ID`/`EV`/`VV` missing | `G002` / unchecked | `G001` | ✅ `G001` | `API2:94-99` |
| Five body fields missing | `G002` | `G002` | ✅ unchanged | `:130-136` |
| `data_cnt` missing | `G002` | **unchecked** (S-9) | `G002` kept — stricter, harmless | — |
| Unprovisioned VAN key | `G999` | `G003` | ✅ `G003` | `:105-119` |
| Malformed `file_nm` | `G999` | container 500, no envelope | `G999` kept | **D-O** |
| Completed-file replay | `0000` | `G999` | `0000` **declined** | **D-N** |

### Two corrections found in the verification

**The unprovisioned-key code is `G003` on both legs, not `G004` on 01301.** There is
no `getResultCount` check on the key lookup — only `isError`, which does not fire on
zero rows. `CRYPT_KEY` comes back null, `VerifyMac` fails, `G003`. The proof is
structural: if `isError` covered zero rows, API1's own `resultCount == 0` check twenty
lines later would be dead code.

**And legacy's constants read backwards from their use.**
`CD_BASE_NONE_INPUT_DATA` is `G001` and is used for the *detail* fields;
`CD_DETAIL_NONE_INPUT_DATA` is `G002` and is used for the *header* pair. Commented at
every reference site, or someone will "fix" the mapping back.

### D-N — replay `0000` declined, and the old reasoning was wrong

The comment defending our `0000` argued that *legacy never sees a `PROC_STEP='3'`
row*. It does. `R006` binds `'2'`/`'6'` as literals, so a completed file misses the
lookup, falls to `getResultCount < 1`, and gets `G999` — **the same code legacy gives
an unknown file.**

So legacy has the resend loop: gateway resends, lookup misses, `G999` again. It
survived because BATCH2's poller re-selected work every minute, so a wedged callback
was never the only path forward.

We removed the poller (K9). Copying `G999` gives us the loop with **nothing to
converge on** — the same class as the transient-00302 retry already declined. The
divergence stays; the argument is now the poller, not the lookup.

### D-O — malformed `file_nm`, not ported

Legacy `API2:142-148` splits the filename outside any `try`, so a name with fewer
than five parts raises an uncaught `AIOOBE` — container 500, no envelope at all. Ours
returns `G999`. Keep ours.

### Unchanged branches — confirmed matching, not merely untouched

For completeness, the branches that already matched legacy and were verified rather
than altered:

| Branch | Leg | Both |
|---|---|---|
| MAC / verify failure | 01301, 01302 | `G003` |
| DB fault | 01301, 01302 | `G004` |
| Sibling fail-stop, count/amount mismatch | 01301 | `0000` envelope + `G998`/`9999` persisted |
| Success | both | `0000` |
| Ambiguous name (>1 row) | 01301 | `G999` — **refactor-only**, no legacy counterpart (FIX J) |
| Row in unactionable state | 01301 | `G999` — refactor-only |

### Code changes

**Two new exception types**, so the code travels with the condition rather than being
inferred at the boundary:

- `MissingCallbackFieldException` — carries `G001` vs `G002`, with the full legacy
  split recorded in its Javadoc. This is what makes the header/detail distinction
  survive the trip to `CallbackAck`.
- `VanApiKeyNotProvisionedException` — maps to `G003`, and carries the
  *`isError` is not zero-rows* derivation, so the reasoning cannot be lost if someone
  later reads the constant name and assumes `G004`.

**Changed:** `VanApiKeyService` throws the typed exception; `CallbackAck` honours the
carried code and gains `G003`/`G001` in its message switch; both services get
leg-correct header and field guards with per-line legacy citations; 01301's unmatched
branch, log line and ops-alert body all move to `G004`; 01302's replay comment is
rewritten to the poller argument, and the malformed-`file_nm` branch carries the
do-not-port note.

### Tests

Every updated assertion names the legacy line it now mirrors:

- `unmatched_unknownFile_…` → `G004`, citing `API1:168-170`
- `receptionResult_missingRequiredField_maps_to_G002` split into
  `…missingBaseField_maps_to_G001` and `…missingDetailField_maps_to_G002`
- new `unprovisionedApiKey_maps_to_G003_onBothLegs`
- the generic-escape test re-pointed at an unrelated fault, since an unprovisioned key
  no longer produces `G999`
- `missing_api_key_propagates_b2_failure…` asserts the typed exception

**2,689 tests, 0 failures**, on a forced full rerun.

### One deviation from spec, accepted

`CallbackAck` was **not** made leg-aware. Once the missing-field code travels in a
typed exception and the key path collapsed to `G003` on both legs, no exception maps
to different codes per leg — the parameter would be accepted and never branched on,
which is a lie the compiler cannot catch. Recorded in both classes' Javadoc with a
note to add it alongside the first branch that actually needs it.

---

## A pattern worth naming

Two conclusions in this audit survived scrutiny while their stated evidence did not:

- The `20251010` charset correlation — the two files predated tone stripping by
  nineteen days and were a resubmission with an edited payload. The finding held; the
  evidence was coincidence.
- D-N's "legacy never sees a step-3 row" — it does, and answers `G999`. The
  divergence is still correct; the reason was wrong.

Both were caught by checking the premise rather than the conclusion. The same
discipline that `docs/testing/fixture-data-conventions.md` applies to tests —
*what did you change to watch it fail?* — applies to prose. A defensible conclusion
resting on a false premise is harder to spot than a wrong conclusion, because nothing
about it looks wrong.

Three of my own framings were withdrawn this way: the `'E'` vs `'EBANK'` environment
label, D-J's attribution of the 35-file backlog, and D-A's "30× faster" claim with an
empty population behind it.

---

## Legacy re-check of the three residuals — 2026-08-01

C-4, C-10 R-B and C-12 were re-examined against legacy source. All three answers
changed something.

### C-4 — legacy is better; fix ours

Legacy `API2` opens its transaction at **`:291`** — *after* the download (`:234`), the
remote archive move (`:235`), the disconnect (`:236`), the empty-file check
(`:249-254`) and the **entire parse loop** (`:262-290`). It is the very next statement
after the parse's `finally`.

Ours is `@Transactional` around the whole method, so the SFTP round trip and the
decode both run with a Spring-managed connection pinned, on a callback thread. **Ours
is the divergence, and legacy is strictly better.**

**Why the fix is cheap:** moving the download and parse out removes **zero writes**
from the rollback set. The only write preceding the parse is the audit row, and that
already commits in its own `REQUIRES_NEW`. The *"a bad file rolls back and the MST
stays replayable at `PROC_STEP='2'`"* guarantee survives untouched.

**But matching legacy exactly would import its race.** With the `R006` lookup outside
the transaction, two concurrent 01302s can both pass it and both download. Legacy has
that window at `:144-153`. Our sibling legs solved the equivalent with an atomic claim
(H-4, G2), so an atomic `2→3` as the first statement of the new writer is the
consistent answer — a deliberate improvement over legacy, on the same justification.

Two details required: **re-read the MST by PK inside the writer** (carrying it across
a boundary leaves it detached, and `save()` would blind-overwrite a concurrent
change), and the `TB_FIRM_FILE_RSLT` insert loop **stays inside** — legacy has it
inside at `:293-331`, and one failed insert rolling back every prior line is the
semantics we match.

*Comment defect found:* `BulkExecutionResultService:486-488` says
`// Best-effort move … failure ignored`, but `sftpClient.move()` has no try/catch so
an exception propagates — which is exactly legacy's behaviour at `:226-247`. The
behaviour matches; the comment is wrong.

### C-10 R-B — we diverge, and aligning is the wrong first move 📝 **RECORDED**

*Written at the stamping site in `ResultCheckService.reflectOutcome` — where someone
would go to change it — with a cross-reference from the C-10 block in `runOnce`.*

**Legacy uses one clock for both rows.** The 203 audit row's `TRAN_DT` and the 202
row's both come from `DateTime.getInstance()` — same class, same method, same format
string, in two different batches. The pair is internally consistent by construction,
and legacy has no window where the 203 sits a day ahead of the row it audits.

**Our split traces to a refactor decision legacy does not make.** Legacy's 202
firm-tran reuses only the pay-target's `TRAN_SEQ` (`:273`), *not* its `TRAN_DT`, and
stamps its own batch clock at `:489`. We made the two the same value (the D-1
decision), which is why our 202 rows carry UTC at all. Our 203 row is then the only
`TB_FIRM_TRAN` row written on a different clock from its sibling.

**And the blocker on aligning is elsewhere.** `FirmBankingHistoryView` renders
`kr_to_vn_time(TRAN_DT || TRAN_TM)` for **every** `TB_FIRM_TRAN` row, assuming the
stored value is Asia/Seoul wall-clock — legacy's writer. Aligning the 203 to UTC would
make that view **consistently wrong rather than inconsistently wrong.** Better, and
diagnosable in one place — but it means the real question is the view's assumption,
not the writers'.

**Held, not fixed.** Nothing selects `TRAN_TP='203'`, so no aging or eligibility path
is affected, and `ORG_TRAN_DT` already carries the original's UTC date verbatim. This
is one decision — fix the view, or align the writers to it — not R-B in isolation.

*Legacy has two-clock problems of its own, just not this one:* within a single row,
`TRAN_DT`/`TRAN_TM` are batch-JVM zone while `RQ_DTTM` on the same INSERT is DB-side
`NOW()` in Asia/Seoul. And across tables, `TB_PAY_TARGET.TRAN_DT` is `utcNow()` while
the 202 `TB_FIRM_TRAN.TRAN_DT` is the batch clock — so the 203 confirmation's
`WHERE TRAN_DT = …` matches zero rows whenever the two disagree on the date. A latent
legacy defect in the confirmation path, not reproduced.

### C-12 — legacy has the same gap, and never named it 📝 **RECORDED**

*Two new sections in `StrandedRecoveryService`'s class Javadoc, under the coexistence
paragraph. The gate is marked **PROPOSED, NOT IMPLEMENTED — do not implement without
PO sign-off.***

**Both legacy selections are today-only.** `TB_PAY_TARGET_R001` takes **no parameters
at all** — the `<in>` block is empty, and the WHERE is `TRAN_DT = TO_CHAR(now(),…)`
plus `PROC_STAT = '9'`. `TB_PAY_FILE_MST_R001` binds two constants: state and today.
No tenant, no origin, no source column, no `ORDER BY`.

So a `'9'` row is retried on every tick for the rest of the day, then **falls out of
the query permanently, still `'9'`, with nothing written to mark it.** Legacy does not
solve C-12 — it abandons the row at midnight, silently. It was never named because
legacy has no detection.

**The provenance problem is a shared-database problem only.** On a separate DB there
are no JEX-written rows and the question dissolves; legacy never encounters it because
it is the sole driver against its own database. The hazard is not misattribution but
**two independent drivers on one queue** — our atomic `'9'`→`'6'` claims and ShedLock
coordinate only within the refactor, so the failure mode is a duplicate payment, not a
bookkeeping error. Which is why the current double-gating is proportionate.

**Legacy's precondition asymmetry**, which our recovery should match if it ever grows
its own checks:

| Condition | Legacy | Result |
|---|---|---|
| No schedule rule for this bank | `PROC_STAT='3'` + alert | Terminal FAIL |
| Window has **closed** for today (`EXE_DIV='E'`) | `PROC_STAT='3'` + alert | Terminal FAIL |
| **Not yet open** | bare `continue` | Stays `'9'`, retried, abandoned at midnight |

"No rule exists" and "the window has closed" are both fatal; "not yet open" is not.

**Proposed gate, not implemented.** A JDBC-URL check would answer the wrong question —
*where do we point?* rather than *where does the legacy batch point?*, which is
configured in JEX property files on a different host entirely. Nothing in the code
distinguishes the two cases today; "coexistence" appears only in Javadoc and YAML
comments.

Shape that would hold: an explicit fail-closed declaration —
`wecms.dispatch.legacy-jex-shares-database`, defaulting **true** — with
`recovery.enabled` effective only when it is explicitly false, plus a startup line
stating which way it resolved and against which JDBC host. That turns "we believe SIT
is separate" from a tribal fact into an assertion an operator makes per environment
and can read back out of the logs.

⚠️ **Operational caveat for whoever enables it first:** our sweep has **no date bound**
where legacy's is today-only. That widening is the entire point of C-12 — but the
first enabled pass will see *every* historical `'9'` row in the table. `batch-size`
(default 50) and oldest-first ordering bound each pass, not the total.

---

## Handover package — read directly, 2026-08-05

`WECMS_BATCH_인수인계.html` and `00_인수인계_개요.md` were read from the files
themselves rather than through a summary. Most of it corroborates what the source
sweep had already established. One item is new.

### 🆕 `wecms_batch_HS.properties` — a per-customer config file we have never examined

The unresolved section records:

> **[해소됨]** `wecms_batch_HS.properties` — **행성전자 전용** 설정 파일
> (고객사별 프로퍼티 분리 운영). **이관 시 고객사별 설정 분리 구조 유지.**

*"Haengsung Electronics-only configuration file (per-customer property separation in
operation). **Preserve the per-customer configuration separation structure on
migration.**"*

**That is an explicit migration instruction, and we may not have followed it.** The
infrastructure reframing treated `wecms_batch.properties` as the single source
throughout. Our configuration axis is **environment** — local/sit/uat/prod — with **no
per-tenant dimension anywhere in `wecms-api`.**

Haengsung is not a minor tenant: the ERP integration is `HAENGSUNG_*`, the `ACCT_MSTR`
names in DEV are Haengsung entities, and `VN_VNM0004_000005` — the tenant behind the
412-line, 5,101,563,524 VND stranded file — is very likely them.

**Three possible outcomes, and only one is benign:**

| | |
|---|---|
| HS overrides nothing operationally significant | Vestigial. Record and close |
| HS overrides values we would otherwise get wrong | **Live gap on the largest tenant**, and a migration instruction not followed |
| The file is not in the workspace | We cannot know — and it joins the DDL as a second item marked 해소됨 on an undelivered promise |

Unresolved. Prompt issued.

### Confirmations — nothing new, but now first-hand

| Item | Handover says | Our position |
|---|---|---|
| `Authorization` / `signature` | 게이트웨이는 검증하지 않음 — the gateway does not validate them; 이관 시 제거 가능 | Already dropped. Confirmed correct |
| Result-check period | 운영 매 1분 확정. 주석 `*/5`는 폐기 | Parity target is 1 min; SIT starts at 5 as a conservative choice |
| `operTimeChk` BATCH_ID | Consolidated to one common time. Bank-level INFO under the shared key `NAPAS_REAL_TIME_BATCH`; **OUT_BANK_CD별 필수 행 = 실효 게이트**. BATCH1's DTL key is a design remnant — passes when no row exists | Matches. The 필수 행 line is the one that matters for UAT/PROD seeding |
| `TB_VAN_API_INFO.API_KEY` | **기관별** AES/HMAC 공유키 — per-organisation | So DEV holding **one key across two `VAN_ORG_CD`s** is a DEV shortcut, not the intended shape |
| Callback location | `API1`/`API2` in `web/WEB-INF/action/.../api/*_act.jsp`; STEP 0→1 and 2→3 confirmed. 이관 시 JSP 인라인 로직을 서버사이드로 분리·재구현 필요 | Done — REST controllers |
| 무응답 내성 | 무응답 = 실패 아님. '처리중' 유지 후 결과조회로 확정 | Principle #18. Honoured, and the reason the result-check cron matters |

### The secure-channel list — four items, one question

The handover names exactly four things it deliberately excludes, to be delivered
through a separate secure channel:

| Item | Location | Our finding ID |
|---|---|---|
| Real property values | `wecms_batch.properties` · **`wecms_batch_HS.properties`** | B4 (partly in repo) |
| Table layouts (DDL) | 원본 조직 별도 전달 예정 | Gate H — never delivered |
| VAN API key | `TB_VAN_API_INFO.API_KEY` | **B2** |
| SFTP private key | `~/.ssh/id_rsa` | **B4** |
| DB connection | JEX config `target=WABOOKSVNDEV_DB` | — |

**One question to Mr. Kang could close two of them:** *was that secure channel ever
used?* B2 and B4 have been tracked as provisioning gaps for weeks; the handover says
they were always intended to arrive separately.

### The DDL was marked resolved on a promise

> **[해소됨]** DDL/인덱스 — 테이블 레이아웃은 원본 조직이 **별도 전달 예정**.

Marked 해소됨 — *resolved* — on the strength of *will be delivered separately.* Two
months on, zero DDL files exist in the tree. All three index scripts were written
against a reverse-engineered column list.

**Second instance of the same pattern**, alongside `wecms_batch_HS.properties`: an
item closed because delivery was promised, not because it happened. Worth raising as a
pattern rather than as two separate requests.

### Still unread

Five of the nine handover documents have never been opened:
`01_시스템_개요서`, `02_기능_명세서`, `03_데이터모델_정의서`,
`05_배치스케줄_운영가이드`, `07_소스구조_인덱스`.

`02` and `05` are the two most likely to contain something we inferred from source
rather than read — branch conditions and status-code definitions respectively. `05`
in particular may carry the `BT00`/`BT01`/`C999` definitions that appear **zero times**
in the 44-page vendor guide and currently rest entirely on legacy usage.

Also still outstanding: **`글로벌 VAN_펌뱅킹_배치파일 레이아웃.xlsx`**, to which vendor
guide §1.2 delegates the entire fixed-length file definition. It would give an
independent check of our layout against the vendor contract rather than against legacy
source, and settle the `data_amt` 23-vs-22 width question.

---

## Handover `01_시스템_개요서` — read 2026-08-05

Mostly corroboration, and it corroborates in useful places. Two items are new.

### 🆕 `FUND_TP` — a four-value discriminator we have never examined

> 자금종류(FUND_TP): `0`(급여), `1`(상여), `9`(기타/국내외화), `S`(해외송금)

Salary · bonus · other-or-domestic-FX · overseas remittance.

**This has not appeared anywhere in the audit.** Not in the wire-layout verification,
not in the six validation rules, not in the state machine, not in any branch analysis.
`OverseasDispatchExecutor` exists, so the overseas path is handled — but whether it is
keyed on `FUND_TP='S'`, and whether the other three values drive divergent behaviour,
is unverified.

Worth checking rather than assuming: is `FUND_TP` a wire field, a routing
discriminator, both, or neither? The audit has been describing bulk payloads as
"salary files" throughout — that is `FUND_TP='0'`, and there are three other values.

### 🆕 JEX and the EFS gateway are the same vendor

> 프레임워크: **JEX** — **코어콘(Coocon) 계열** 전자금융 프레임워크

The legacy framework and the payment gateway are both Coocon products. That explains
`x-coocon-accessIp` as a vendor-namespaced header, and means the framework/gateway
relationship is closer than we had assumed — a header the API guide never documents
may be a framework convention rather than a gateway requirement.

Does not change the decision to restore it, but it reframes why the guide is silent.

### Corroborations that matter

**The two bank constants are not a whitelist — confirmed in writing.**

> 코드에 상수로 존재하는 은행코드는 신한 `970424`, 우리 `970457` 2개뿐인데, 이는 은행
> 화이트리스트가 아니라 **딜레이 override·특정 망검증 규칙 용도**.

*"The only bank codes present as constants are Shinhan and Woori — these are not a
bank whitelist but exist for delay override and specific network-validation rules."*

That is independent confirmation of the **D-A** analysis: the per-request delay
constants exist only for those two banks, everything else is data-driven, and the
leak's affected population is therefore empty.

**And §1.1 narrows it further.** The business-purpose paragraph names only
신한은행 베트남 and 우리은행 베트남 as the live counterparties, while §1.2 lists five for
DEV (BIDV · VCB · 국민 · 우리 · 신한). If production really is Shinhan and Woori only,
D-A's empty population is firmer still — and worth confirming, since it also bounds who
`PER_DELAY_DEFAULT` can affect. *(That question lost most of its force on 2026-08-05:
the default is back to legacy's 100ms, so a third bank now receives exactly what legacy
gives it. It still matters for the reverse reason — 05 §5.3 names BIDV and Kookmin as
banks that process normally on the 기본 딜레이, which is the evidence that third banks
were expected on this rail at all.)*

**`ANT_DIV_CD` — the six-channel set, stated as names.**

> 타행망 코드(ANT_DIV_CD): `EBANK`, `VCB`, `CITAD`, `NAPAS`, `SWIFT`, `INTER`(당행)

The handover uses the **channel-name vocabulary** — the `TB_BATCH_SCHEDULE_DTL` one —
not the storage encoding. Consistent with the three-vocabulary finding, and useful
because it confirms the conceptual set is six, with `INTER` meaning 당행 (same-bank).
Nothing here contradicts `AntDivCd`'s deliberately mixed `getValue()`.

**Everything else lines up with the source sweep:** the four processing types and their
programs, `PROC_STAT`/`PROC_STEP` as the workflow spine, `TB_BATCH_SCHEDULE_INFO`
bank-level and `_DTL` network-level with Y/N/E, validation-before-dispatch, tone
removal on names/memo/address, no-response tolerance, and per-organisation EV/VV.

---

## Handover `02_기능_명세서` — read 2026-08-05

The richest of the handover documents so far. **Three preconditions appear here that
have never been examined in this audit**, and the finalisation defect is documented
without its author noticing it.

### 🆕 `ORG_STAT` — a result-check precondition we may not have

> §2.4 step 2.1 — 펌 연계기관(`TB_FIRM_CONF_R001`). **기관상태(ORG_STAT) `'1'(거래가능)`
> 아니면 Skip.**

Legacy's result-check skips any row whose organisation is not in a tradeable state.
**`ORG_STAT` has not appeared anywhere in this audit** — not in `ResultCheckService`,
not in the recovery sweep, not in any dispatch precondition we verified.

If we poll 203 for an organisation legacy would skip, we are calling the gateway on
behalf of a suspended tenant. Needs checking on the result-check path *and* on
`StrandedRecoveryService`, which re-drives 202.

### 🆕 `ANT_WHERE_USE_YN` — the gate that decides whether the DTL check runs at all

> §2.1 step 3 — 타행망 사용(**`ANT_WHERE_USE_YN='Y'`** 且 비당행)이면
> `TB_BATCH_SCHEDULE_DTL_R001`(타행망) 추가 검증.

The network-level operating-hours check is **conditional**: it only runs when
`ANT_WHERE_USE_YN='Y'` *and* the channel is not 당행 (`INTER`). Never examined here.

**This bears directly on the open `canonical` question.** The `E`→`EBANK` expansion
only matters when the DTL check runs at all — so the flag narrows the population
further, and may narrow it to nothing. Worth resolving before Mr. Jung sees the
recommendation.

### 🆕 `FUND_TP='S'` is a validation branch — partial answer to yesterday's question

> §2.2 step 2.2 — **해외송금(`FUND_TP='S'`)**: SWIFT 외 망(`swiftOutCnt`)·당타행 외
> (`failChnlCnt`)·통화혼용(`ccyCnt>1`) → 불가.

So `FUND_TP` does discriminate, at least in Gate A. Overseas remittance gets its own
rule set — SWIFT only, no cross-channel, no currency mixing — while everything else
takes the EBANK/VCB/NAPAS rules. Whether the other three values (`0` salary, `1` bonus,
`9` other) diverge anywhere is still open.

### ⚠️ Endpoint naming discrepancy worth one check

> §2.3 — 실행지시 API 호출 — **`BATCH2_API`** 경로 (**`WGVN_EPS_203`** 계열).

`EPS`, not `EFS`; `203`, not `302`. The transaction type is unambiguously `00302`, so
either the handover has a typo or the **URL path genuinely differs from the
transaction code.** We derived our endpoint map from the vendor PDF's §→`.jct`→
`API_TRAN_TP` matrix, so we may already be right — but a mismatch here means calling
the wrong endpoint, and it is cheap to confirm.

### 🔴 The finalisation defect, documented and unnoticed

> §2.7 step 9 — `tot==succ → '0'(전체성공)`, `tot==fail → '3'(전체실패)`,
> 그 외 `'1'(부분성공)`.

The handover states the three-way branch plainly and **does not notice that an empty
set satisfies the first two simultaneously.** With `tot = 0`, both `tot==succ` and
`tot==fail` are true, and whichever is evaluated first wins — which is how a pay file
reports ALL SUCCESS over a file that never settled.

That is our two live PROD instances, `20251128` and `20251201`, described in the
handover as correct behaviour. Independent confirmation that the defect was never
seen, only inherited.

### Corroborations

| Item | §2 says | Our position |
|---|---|---|
| Sibling fail-stop | Ledger-fail pre-check → `RS=G998`, STEP `'1'`, STAT `'3'` | Matches. `G998` persisted, envelope `0000` |
| Reception failure | `result_cd != 'BT00'` → `RS=G999` + pay file `'3'` | Matches |
| Count/amount mismatch | `9999 금액상이` | Matches |
| BATCH2 transient set | 무응답 `BT01/G999/C999` → Skip, next cycle retries | Matches — and confirms the retry we declined (K9) |
| BATCH2 success set | `0000/BT00` → `PROC_STEP='2'` | Matches |
| Result file format | **결과파일을 역으로 읽는다** — same fixed-length format, offsets must match both directions | Verified. Encode and decode slices both checked |
| Amount recombination | `StringToBigDec(data_amt, data_dcm_amt, 2, ROUND_DOWN)` | Matches — and this is S-4, which we correctly did **not** port |
| `RC=0001` on 203 | Grouped with success: 성공/원거래없음 → EV 복호화 | **This is D-D.** The handover documents the behaviour without flagging that decrypting an EV for a transaction the gateway says does not exist is meaningless. We return `notFound` first |
| Realtime tone-strip | Only two fields — `FINC_MDA_OWNER_NM` and `TRAN_USAG` | Bulk strips five. Worth confirming our realtime path strips exactly these two |
| `WB_00000100` response | Returns `FINC_MDA_OWNER_NM` **and `ANT_DIV_CD`** | Relevant to D-I / S-1 — the inquiry is a *source* of `ANT_DIV_CD`, not just a consumer |
| API_TRAN_TP directions | `00301`/`00302` outbound, `01301`/`01302` inbound | Matches |

### Suggested next check

```
Handover 02 names three preconditions this audit has never examined. Report only.

1. ORG_STAT — legacy's result-check skips any row whose TB_FIRM_CONF.ORG_STAT is not
   '1' (§2.4 step 2.1). Quote legacy's check. Does ResultCheckService have an
   equivalent? Does StrandedRecoveryService, which re-drives 202? Report the DEV
   value distribution and whether any non-'1' rows exist.

2. ANT_WHERE_USE_YN — the DTL operating-hours check runs only when this is 'Y' AND
   the channel is not INTER (§2.1 step 3). Quote legacy's gate and ours. Then state
   whether this narrows the population for the open `canonical` question — possibly
   to nothing.

3. WGVN_EPS_203 — §2.3 names the BATCH2 endpoint path as WGVN_EPS_203, not EFS_302.
   Quote the actual URL legacy calls for 00302, and the one we call. Typo or real?

4. FUND_TP — §2.2 confirms 'S' drives its own Gate A rule set. Do values 0, 1 and 9
   diverge anywhere else — wire field, routing, operating hours? Complete yesterday's
   question with this new evidence.
```

---

## Handover `03_데이터모델_정의서` — read 2026-08-05

Four items are new. One of them weakens a recommendation I made for C-0.

### 🆕 Both sequences `CYCLE` — and that undercuts the provenance idea

> `FIRM_SEQ_NO` — 거래일련번호 — `START 1 MAXVALUE 9999999999 **CYCLE**`
> `FIRM_FILE_SEQ_NO` — 파일일련번호 — `START 1 MAXVALUE 9999999 **CYCLE**`

**Correction to C-0.** I proposed sequence-range partitioning as a no-DDL route to row
provenance — a reserved offset range distinguishing refactor rows from legacy ones. The
`CYCLE` clause makes that unsound: a wrapping sequence cannot honour a reserved range,
and both systems draw from the **same** sequence, so ranges cannot be reserved without
changing the sequence definition itself — which is DDL on a shared object.

The idea is not dead, but it is not the cheap option I described. Provenance realistically
means either a column or a cutover timestamp.

*Uniqueness itself is fine:* `TRAN_SEQ` sits in `pk_tb_firm_tran (tran_dt, biz_key, tran_seq)`,
so a wrap would only collide if ten billion values were burned within one `TRAN_DT` for one
tenant. Not reachable. Same reasoning for `FIRM_FILE_SEQ_NO` at ten million.

### 🆕 `ORG_STAT` — the full domain, and it is four values

> `0` 대기 · `1` 거래가능 · `2` 일시정지 · `3` 해지

Waiting · tradeable · **suspended** · terminated. Legacy proceeds only on `'1'`.

This completes the precondition flagged from `02`: it is not a boolean, and two of the
three non-proceeding values are administrative states a tenant can be moved into
deliberately. Dispatching for a **suspended** or **terminated** organisation is a
materially worse outcome than dispatching for one merely awaiting setup.

`TB_FIRM_CONF` also carries **`START_DTTM` / `END_DTTM`** — a validity window that has
never been examined either. Whether `TB_FIRM_CONF_R001` filters on it is unverified.

### 🆕 `EXE_DIV` is computed from **서버시각** — server time

> `START_TM > now → 'N'(시간전)`, `END_TM < now → 'E'(마감)`, 그 외 `'Y'(수행)`
> **서버시각 `HH24MISS` 기준**

Not DB time, not a configured zone — the clock of whichever server evaluates it. Which
means the operating-hours gate inherits the host's timezone, and legacy's JEX host and our
Spring host need not agree.

Directly relevant to **F-7 / Gate B-2**: the vendor's `00:15–23:50` window is 25 minutes
wide, and a two-hour Seoul-vs-ICT discrepancy swallows it whole. Now we know the legacy
side reads a *server* clock rather than a stored zone, which makes the comparison
host-dependent rather than data-dependent.

### 🆕 `FUND_TP` propagates to the firm-file master

It appears in the column list of **both** `TB_PAY_FILE_MST` and `TB_FIRM_FILE_MST` — so
the fund type is carried forward onto the generated file record, not just held on the
request. Adds weight to the open `FUND_TP` question: it is being persisted at two levels,
which is not the shape of a field that only gates validation.

### 🔴 The DDL, a third time — and the handover names it as the authority

> ⚠️ DDL(테이블 생성문)은 본 저장소에 없음. 아래 컬럼 목록은 IDO SQL/소스 사용에서 **역추출**.
> **실제 테이블 레이아웃(타입/제약/인덱스)은 원본 조직이 별도 전달 예정 — 이관 시 해당
> 산출물을 정본으로 사용.**

*"…the real table layout (types, constraints, indexes) will be delivered separately by the
originating organisation — **use that deliverable as the authoritative source on
migration.**"*

So the handover states plainly that its own column list is reverse-engineered and should be
superseded. It never was. **Third instance of the pattern** — after
`wecms_batch_HS.properties` and the `.xlsx` layout spec — of an item marked 해소됨 on a
promise that was not kept.

This is also the direct basis for **Gate H**: all three index scripts were written against
the column list this document explicitly labels as provisional.

### Corroborations

`PROC_STAT` `0`/`3`/`6`/`9` and `PROC_STEP` `0`/`1`/`2`/`3` confirmed. 43 IDO operations
confirmed. `TB_PAY_TARGET` has **no `PROC_STEP`** — matching the schema correction made
earlier. `TB_FIRM_TRAN`'s `ORG_TRAN_DT` / `ORG_TRAN_SEQ` / `ORG_RESP_CD` confirm the 203
linkage fields, and `TB_FIRM_FILE_MST`'s `REG_RSLT_CD` / `REG_RSLT_MSG` confirm where
`G998` and `9999` persist. `TB_PAY_FILE_DATA`'s column list includes `RCPT_USE_ADDR` and
`FEE_BRDN_CLS_CD` — the 2025-10-29 pair — alongside `SENDER_NM`, `TAR_ACCT_RMRK` and
`WDRL_ACCT_RMRK`, the four unguarded wire fields from C-14.

### Added to the next check

```
5. TB_FIRM_CONF.START_DTTM / END_DTTM — a validity window never examined. Does
   TB_FIRM_CONF_R001 filter on it? Do we? If legacy skips an expired conf row and we
   do not, that is the same shape as the ORG_STAT gap.

6. ORG_STAT is four values (0 waiting, 1 tradeable, 2 suspended, 3 terminated), not a
   boolean. Report the DEV distribution and whether any row is 2 or 3 — dispatching for
   a suspended tenant is worse than for one awaiting setup.

7. EXE_DIV is computed from SERVER time (HH24MISS), not DB time or a stored zone.
   Confirm what clock legacy's evaluation uses and what ours uses. This decides whether
   F-7's 25-minute window comparison is even well-defined.
```

---

## Handover `04_인터페이스_연동명세서` — read 2026-08-05

This one independently confirms the wire layout and settles two open questions. It also
reveals a format-history item we did not have.

### ✅ The 1000-byte layout, confirmed from the vendor side

All three records verified against our implementation, field by field:

| Record | Fields | Sum | + CRLF |
|---|---|---|---|
| Header `H` | 8 + filler 919 | 998 | **1000** ✓ |
| Data `D` | 19 fields | 998 | **1000** ✓ |
| Trailer `T` | 7 + filler 907 | 998 | **1000** ✓ |

**And the ordering we flagged as easy-to-break is confirmed correct.** The table lists
수수료부담구분 (`FEE_BRDN_CLS_CD`, 3) **before** 수취인주소 (`RCPT_USE_ADDR`, 180) — so
offsets 815 and 818 respectively, which is the reverse of declaration order and exactly
what `FixedLengthCodec:135-136` does.

`ANT_DIV_CD` is confirmed as **1 byte at offset 62** (byte 63, 1-indexed) — 타행망코드,
one character. That settles the caveat I flagged earlier: the offset claim was accurate,
and it corroborates `AntDivCdT`'s single-character encoding for the wire table.

### 🆕 There were **two** format changes in 2025, not one

| Change | Fields added |
|---|---|
| **2025.07** | 수취은행국가코드 `RCPT_BANK_CNTRY_CD` (2) · 송금통화코드 `REMIT_CCY_CD` (3) |
| **2025.10.29** | 수수료부담구분 `FEE_BRDN_CLS_CD` (3), and the 183-byte filler removed |

> ⚠️ filler(183) 필드는 2025.10.29 제거됨. **포맷 버전 변경 이력 주의.**

We only knew about the second. The handover explicitly warns to watch the format version
history — which matters for **K3**, since "what version is production running" now has two
transitions behind it rather than one.

It also dates the stranded multibyte files usefully: `20251010` sits **after** the July
change and **before** the October one, so those files were written against the
intermediate format.

### ✅ `WGVN_EPS_203` was a typo — settled

`04` §4.2's endpoint table is unambiguous:

| Endpoint | Transaction | Config key |
|---|---|---|
| `WGVN_EFS_302` | 대량 실행 지시, `API_TRAN_TP` `00302` | `{ENV}_API_URL` + `BATCH2_API` |

And it adds an explicit note that resolves the confusion at source: **six** outbound
endpoints (`201`/`202`/`203`/`204`/`301`/`302`), while `00301`/`00302`/`01301`/`01302` are
`API_TRAN_TP` values stored in `TB_FIRM_BAT_API_TRAN` — *"엔드포인트 이름과 별개다"*,
separate from the endpoint names. Doc `02`'s `WGVN_EPS_203` is an error.

### 🆕 SFTP: the username is the VAN org code, per connection

> `CertInit(certPath, ip, port, **id**)` — **id = VAN기관코드**

Legacy passes `VAN_ORG_CD` as the SFTP user for each transfer. We use a **static**
`WECMS_SFTP_USERNAME`. Equivalent while single-VAN, a divergence the moment a second VAN
organisation dispatches — and it was already flagged as empty in `.env.local`/`.env.sit`,
which would throw at first use.

Also newly visible: **`StrictHostKeyChecking=no`** and a 60-second session timeout. Worth
confirming what `JschSftpClient` sets — host-key checking is a security-relevant default
and going *stricter* than legacy would fail the first connection rather than the tenth.

### 🆕 Two small derivations to verify

**카드계좌구분** (card-account flag, 1 byte): `FINC_MDA_DIV='C' → 'N'`, else `'Y'`. An
inversion — `C` for card yields `N` — and exactly the shape that gets implemented
backwards. Never verified here.

**`RM` is URL-encoded** on the response. §4.1 states it plainly. Whether we URL-decode
the response message before logging or persisting it is unverified; if not, operators
read percent-escapes in `RS_MSG`.

### Corroborations

| Item | §4 says | Our position |
|---|---|---|
| Timeouts | **Connect 30s / Read 60s** | ✅ Matches — C-7 closed at exactly these values |
| `Authorization` / `signature` | 게이트웨이가 검증하지 않는다 … 이관 시 **제거해도 무방** | ✅ Dropped, and now confirmed twice |
| `x-coocon-accessIp` | 고정 헤더, 로컬IP | ✅ Restored |
| Crypto | AES/CBC/PKCS5, **16-byte 0x00 IV**, HMAC-SHA256, `RQ_DTIME(14)` prefix | ✅ Byte-identical |
| Base64 repair | 공백→`+`, `_`→`/`, `-`→`+` 보정 로직 포함 | ✅ Ported (C-15) |
| 2-pass decode | URL디코딩 유/무 2-pass 시도 | Declined, with the ordering reason recorded (C-15) |
| Amount split | 정수부/소수부 분리, 재조합 2자리 `ROUND_DOWN` | ✅ Matches. This is S-4, correctly not ported |
| File name | `S_{VAN}_{TYPE}_{회차3}_{YYYYMMDD}` | ✅ Token for token |
| Bank constants | 화이트리스트가 **아니라** 딜레이 override + 망검증 규칙 | ✅ Third written confirmation of the D-A reasoning |
| `VCB` ambiguity | 은행명이자 망코드로 **중복 등장** — VCB is both a bank and a network code | Worth knowing; nothing in our code conflates them, but the naming invites it |
| Slack `TNO` | 고정 `M9999999` | Matches |
| Padding basis | "CP/UTF-8 byte 기준" | Still names no codepage — consistent with C-13's closure by construction |

### Added to the next check

```
8. 카드계좌구분 (D record, offset 64): legacy maps FINC_MDA_DIV='C' → 'N', everything
   else → 'Y'. An inversion. Quote legacy's derivation and ours, and confirm the
   direction is not reversed.

9. RM is URL-encoded on every EFS response (§4.1). Do we decode it before persisting to
   RS_MSG or logging? If not, operators see percent-escapes.

10. SFTP: legacy passes VAN_ORG_CD as the connection id and sets StrictHostKeyChecking=no
    with a 60s session timeout. Report what JschSftpClient does for all three. Going
    stricter on host-key checking than legacy would fail the first real connection.

11. Format history: TWO changes in 2025 — July added RCPT_BANK_CNTRY_CD + REMIT_CCY_CD,
    October added FEE_BRDN_CLS_CD and removed the 183-byte filler. Does anything in the
    codebase or the DB carry a format version marker? K3 asks what version production
    runs; the answer now has two transitions behind it.
```

---

## Handover `05_배치스케줄_운영가이드` — read 2026-08-05

**This document contradicts a change I recommended, and settles the response-code
question that was queued for the vendor.**

### ✅ `PER_DELAY_DEFAULT` — my recommendation was wrong; **reverted 2026-08-05**

Verbatim from `05_배치스케줄_운영가이드.md` §5.3, the 은행/통화/딜레이 table
(lines 96-101) — re-quoted from the source file 2026-08-05, no emphasis added:

> ```
> | 출금은행 | **데이터 기반 다수** (개발계: BIDV·VCB·국민·우리·신한). `OUT_BANK_CD`는 `TB_FIRM_CONF`/`TB_PAY_TARGET`에서 관리 |
> | 신한은행 (코드 상수) | `970424` (딜레이 100ms) |
> | 우리은행 (코드 상수) | `970457` (딜레이 3000ms) |
> | 그 외 은행 | 기본 딜레이 100ms 적용 (코드 상수 없음) |
> | 기본 통화 | `VND` |
> | 기본 딜레이 | 100ms |
> ```

*(An earlier revision of this section quoted the two 100ms rows with bold emphasis that
is not in the source. Corrected — the content was right, the transcription was not.)*

I recommended raising `DispatchPacer.PER_DELAY_DEFAULT` from 100 to **3000ms**, on the
reasoning that it applies "only to banks legacy never had a considered value for."

**The handover states the considered value plainly: 100ms.** It is not an absent
decision — it is the documented default for BIDV, KB Kookmin, VCB and every other
data-driven bank. Shinhan and Woori are overrides *from* it.

**And the cost is not theoretical.** §5.5 warns 우리은행은 건별 3초 딜레이 →
대량 처리 시 처리시간 영향 큼 — *Woori's 3-second per-item delay significantly impacts
bulk processing time.* Applying that same 3000ms to every other bank makes a 100-row
BIDV batch take **five minutes instead of ten seconds**. On the realtime rail, which now
runs through a single-threaded drain, that is a queue-depth problem as well as a latency
one.

**Recommendation: revert `PER_DELAY_DEFAULT` to 100ms.** The D-A finding stands — the
leak is real and our per-row keying is correct — but the constant raise was a mitigation
for a risk the handover shows was already priced.

> ✅ **APPLIED 2026-08-05.** `DispatchPacer.PER_DELAY_DEFAULT = 100L`
> ([DispatchPacer.java:106](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/DispatchPacer.java)).
> Woori 3000ms and Shinhan 100ms unchanged; the per-row keying in `perBankDelay`
> unchanged — **only the constant moved.** All three constants now match legacy
> `WCConst.PER_DELAY_*` exactly.
>
> The Javadoc on the constant cites 05 §5.3, 06 §6.3 and 08 §8.4 by section so the value
> reads as documented rather than inherited, and records that the 3000ms revision was
> withdrawn along with its false premise. `DispatchPacerTest` pins 100 as the default and
> was re-verified in both directions: setting the constant to 3000 turns **8 of 11**
> tests red, restoring 100 returns it to green.
>
> **Why this is not merely "matching legacy".** Handover 08 §8.4 attributes the 3-second
> floor specifically to *Woori's* rate limit. Generalising one counterparty's published
> constraint to every other bank is a throughput penalty with no counterparty behind it:
> a 100-row BIDV batch paces ~5 minutes of gateway traffic where legacy paces ~10
> seconds, and on the single-threaded `RealtimeDispatchQueue` drain that is queue depth
> as well as latency. The revert removes a regression, it does not relax a safeguard.

### ✅ The API response codes — the vendor-queued question, answered here

`BT00`, `BT01` and `C999` appear **zero times** in the 44-page vendor guide. They are
defined here:

| Code | Meaning |
|---|---|
| `0000` | 정상 |
| `0001` | 원거래 없음 — original transaction not found |
| `9999` | 금액 데이터 상이 — amount data mismatch |
| `G001` | **공통부** 필수 입력값 누락 — *common section* required field missing |
| `G002` | **데이터부** 필수 입력값 누락 — *data section* required field missing |
| `G003` | 암복호화 검증오류 |
| `G004` | DB 처리 오류 |
| `G998` | 이체대상 원장 상태 실패 |
| `G999` | 기타 에러 |
| `BT00` | (대량) 정상 |
| `BT01` / `C999` | (대량) **무응답 → 재시도** |

That closes the item — the definitions exist, just not in the vendor's document.

### ⚠️ And legacy's `G001`/`G002` usage contradicts these definitions

The constant names match the documentation: `CD_BASE_NONE_INPUT_DATA` = `G001` = 공통부,
`CD_DETAIL_NONE_INPUT_DATA` = `G002` = 데이터부.

But the **usage** does not:

| Field group | Section | Documented code | Legacy sends |
|---|---|---|---|
| `ID`/`RQ_DTIME`/`TNO`/`EV`/`VV`/`EM`/`VM` | 공통부 | `G001` | `G001` ✅ |
| `bank_cd` / `prtn_id` | 데이터부 | `G002` | `G002` ✅ |
| `file_nm`, `data_cnt`, `data_amt`, `data_dcm_amt`, `result_cd`, `result_msg` | 데이터부 | `G002` | **`G001`** ❌ |

Legacy is **internally inconsistent within the data section** — two of its eight body
fields get the documented code, six get the common-section code.

**We ported the behaviour faithfully**, which is right for parity and means we now send a
semantically wrong code for six fields. If the gateway ever logs or routes on the
documented meaning, those six read as "common section missing" for a data-section field.

Worth recording as an inherited defect rather than fixing: matching legacy is the safer
side while the gateway's actual handling is unknown, and this belongs on the list of
things to raise rather than change.

### ✅ The `BATCH_ID` mapping — 확정, with an explicit migration warning

| Caller | Level | Table | `BATCH_ID` used | If the row is missing |
|---|---|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | bank | INFO | `NAPAS_REAL_TIME_BATCH` | 오류 · Skip |
| `NAPAS_REAL_TIME_BATCH` | network | DTL | `NAPAS_REAL_TIME_BATCH` | 오류 · Skip (non-당행) |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | bank | INFO | **`NAPAS_REAL_TIME_BATCH`** (common) | **오류 · Skip — 필수** |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | network | DTL | `FILE_DEPOSIT_TRANSFER_BATCH1` (잔재) | **`Y` 통과 — 관대** |

> 실효 게이트는 은행레벨 INFO 행 … **`OUT_BANK_CD`별 필수 행.**
> 이관 시: 운영시간은 `NAPAS_REAL_TIME_BATCH` 키 하나로 통합 적재하고, **코드가 넘기는
> `BATCH_ID` 문자열을 그대로 보존할 것.** (배치 이름별로 정규화하면 INFO 행을 못 찾아 막힘)

*"On migration: load operating hours under the single key `NAPAS_REAL_TIME_BATCH`, and
**preserve the `BATCH_ID` strings the code passes verbatim.** If you normalise them by
batch name, the INFO row will not be found and it will block."*

An explicit migration instruction, and the refactor followed it — the asymmetry flagged
in the very first message of this audit turns out to be deliberate, documented, and
load-bearing. **This is the definitive statement behind Gate B: a required INFO row per
`OUT_BANK_CD`, under the common key.**

### 🆕 The DTL exception rule

> 대상 행이 없으면 오류처리 후 Skip **(단, 당행 `INTER`/공백/`null`은 예외 통과)**

A missing DTL row is an error and a skip — **except** for 당행 (`INTER`), blank, or
`null`, which pass through. Combined with `ANT_WHERE_USE_YN` from doc `03`, that is a
two-part gate on whether the network-level check runs at all, and it narrows the
population for the open `canonical` question further.

### Corroborations

Four batch schedules confirmed at 1 minute, with `*/5` explicitly discarded. `PROC_STAT`,
`PROC_STEP`, `ORG_STAT` and `TRAN_TP` domains all match. The `API_TRAN_TP` directions
confirm. The bank-constant reasoning appears for a **fourth** time, with an added
migration instruction we followed: 은행 목록을 코드에서 추출하지 말고 데이터·연계기관에서
확보할 것 — *get the bank list from data, not from code.*

And §5.5 names the monitoring principle directly: 무응답 거래는 자동 재시도되므로,
**6(처리중) 상태 장기 잔존 모니터링이 핵심** — *since no-response transactions retry
automatically, monitoring for long-lived `'6'` status is the key thing.* That is exactly
what `StuckPayFileMonitorService` does, and it confirms legacy leaned on automatic retry
where we lean on detection.

### Next action

```
1. [DONE 2026-08-05] Revert DispatchPacer.PER_DELAY_DEFAULT from 3000 back to 100.

   Handover 05 §5.3 states the default plainly: "그 외 은행 | 기본 딜레이 100ms 적용",
   "기본 딜레이 | 100ms". It is a documented decision, not an absent one, so my earlier
   justification ("banks legacy never had a considered value for") was wrong.

   §5.5 also warns that Woori's 3s delay materially affects bulk processing time —
   applying it to every other bank makes a 100-row BIDV batch five minutes instead of
   ten seconds, and on the realtime rail that is queue depth as well as latency.

   Update DispatchPacerTest to pin 100 as the default, and record in the Javadoc that
   the value is documented in handover 05 rather than merely inherited. Keep the D-A
   per-row keying — that finding stands.

2. Record the G001/G002 semantic mismatch as an inherited defect, do not fix.

   Handover 05 defines G001 = 공통부 (common section), G002 = 데이터부 (data section).
   Legacy sends G001 for six data-section fields (file_nm, data_cnt, data_amt,
   data_dcm_amt, result_cd, result_msg) while correctly sending G002 for bank_cd and
   prtn_id — inconsistent within its own data section.

   We port the behaviour, which is correct for parity. Note in the callback Javadoc that
   six codes are semantically wrong per the documented definitions, and add it to the
   list of things to raise with VAN Center rather than change unilaterally.
```

---

## Handover `06_환경설정_항목목록` — read 2026-08-05

Short document, two items with real consequences for B4.

### 🔴 B4 is two-sided — the new server's key must be **registered**

> §6.4 — [ ] SFTP IP/Port/경로/개인키 확보 및 **신규 서버 키 등록**

*"Obtain the SFTP IP/port/path/private key **and register the new server's key.**"*

We have been treating B4 as *place a file at `~/.ssh/id_rsa` with 0600*. It is not. If
`wecms-api` runs on a host the SFTP counterparty does not already know, that host's
**public key has to be registered on the remote side** — which is a request to the bank
or VAN operator, not a local action.

**That is lead time nobody has started.** It also means "we have the private key" is
insufficient on its own: the same key on a new host still fails if the counterparty
allow-lists by host, and `StrictHostKeyChecking=no` (doc `04`) only relaxes *our* side of
the handshake, not theirs.

Worth resolving before the SIT bulk test is scheduled, because a rejected SFTP
authentication looks identical to a wrong key path.

### 🆕 Legacy has no SFTP username setting at all

The full property key list is 15 entries — and there is **no** `{ENV}_SFTP_USERNAME` or
`_SFTP_ID`. Consistent with doc `04`: the connection id is the `VAN_ORG_CD`, resolved
per transfer.

So `WECMS_SFTP_USERNAME` is not a value we failed to copy across — it is a **refactor
invention with no legacy counterpart**. That reframes the empty value in
`.env.local`/`.env.sit`: the question is not "what was legacy's username" but "should we
have a static one at all, or resolve it per transfer as legacy does."

### ✅ `100` / `3000` confirmed a second time — the revert stands

> §6.3 — 은행 딜레이 | `WCConst.PER_DELAY_*` | **100/3000ms** | 코드 상수

Two values, not three. `PER_DELAY_DEFAULT` = 3000 was my error and should go back to
100, as recorded under doc `05`.

### 🆕 Configuration loading — three system properties and an external path

| Property | Purpose |
|---|---|
| `ENV_MODE` | `T`=DEV, `U`=UAT, `R`=PROD — the `{ENV}` prefix source |
| `WAS_NO` | WAS instance number, used for log and alert tagging |
| `CONT_PROP_PATH` | **External** directory from which `PropertyUtil` loads `wecms_batch.properties` |

The properties file lives **outside the deployable**, at a JVM-property-configured path,
and `WecmsBatchInit` calls `reLoad()` at startup. That is the same shape as our
`setenv.sh` + external `.env` arrangement — worth noting because it means legacy's
deployed values were never in the war either, and the "committed config diverges from
deployed config" problem (C-1) is inherited, not introduced.

### ⚠️ `wecms_batch_HS.properties` — confirmed, mechanism still unstated

> `wecms_batch_HS.properties` = **행성전자 전용** 설정 파일 (고객사별 프로퍼티 분리 운영)

Confirmed as a real, Haengsung-specific file. But §6.1 describes `PropertyUtil` loading
only `wecms_batch.properties` — **it never says how the `_HS` variant is selected.**

So the open question from the index document narrows usefully: not *"does this file
exist"* (it does) but *"what chooses it — `BIZ_KEY`, a JVM argument, a deployment path?"*
That is answerable from `PropertyUtil` and `WecmsBatchInit` and belongs in the prompt
already issued.

### Corroborations

The 201 endpoint is hardcoded `/WGVN_EFS_201.jct` and is **not** a config key —
confirming why it never appeared in the property list. `{ENV}_SFTP_CERT` is listed as a
key while `SftpUtil` hardcodes `~/.ssh/id_rsa` (recorded as 서버 계정 의존 in §6.3), which
is the dead-config finding stated by the handover itself. Timeouts 30s/60s and the
16-byte zero IV appear again. And `Authorization`/`signature` are the **only** item on
the §6.4 checklist already struck through as 조치 불필요 — the rest remain open, two
months on.

### Added to the next check

```
12. SFTP key registration (§6.4): legacy's checklist says "신규 서버 키 등록" — register
    the NEW server's key with the counterparty, not just obtain the private key.

    Report: does wecms-api run on the same host as the JEX batch? If not, whose action
    is the registration, and has it been requested? A rejected authentication is
    indistinguishable from a wrong key path, so this needs answering before the SIT
    bulk test is scheduled.

13. There is no {ENV}_SFTP_USERNAME key in legacy — the connection id is VAN_ORG_CD,
    resolved per transfer (docs 04 and 06 agree). So WECMS_SFTP_USERNAME is ours, not a
    missed copy.

    Report whether a static username is correct here, or whether JschSftpClient should
    resolve it per transfer as legacy does. Single-VAN today makes them equivalent; the
    question is what happens at the second VAN organisation.
```

---

## Handover `07_소스구조_인덱스` — read 2026-08-05

An index rather than a specification, but it names two classes this audit has never
opened — and one of them sits on the callback response path.

### 🆕 `impl/ResultMaker.java` — a global response hook we have not examined

> `impl/ResultMaker.java` — ★ **응답공통 처리 훅** (`IResultMaker`,
> **`web.xml`의 `result_maker`에 등록**)

A response-common processing hook, registered **globally in `web.xml`**, so it runs for
every JEX service — including the `API1` and `API2` callbacks.

**This matters for the callback response contract (D-4).** Every RC/RM comparison we
have made was against what the JSP *sets*. If `ResultMaker` post-processes the envelope
before it goes on the wire — URL-encoding `RM`, populating common fields, normalising
codes — then the JSP is not the last word, and our `CallbackAck` is matching an
intermediate value rather than the final one.

Doc `04` states that `RM` **is** URL-encoded on responses. `ResultMaker` is the obvious
place that happens, and nothing in the JSP analysis accounted for it.

Worth reading before the callback contract is considered settled.

### 🆕 `util/LoadMemory.java` — likely holds the `_HS` selection logic

> `LoadMemory.java` — 설정 메모리 적재 추상기반 (**`PropertyUtil`의 상위 클래스**)

`PropertyUtil` extends `LoadMemory`. Doc `06` describes `PropertyUtil` loading
`wecms_batch.properties` and never explains what selects `wecms_batch_HS.properties` —
so the selection is plausibly in the parent class.

That gives the open `_HS` question a concrete place to look, rather than a search.

### 🔴 JEX itself is a fourth undelivered item

> JEX (`jex.*`) — 프레임워크 코어 — **상용(Coocon) — 소스 부재, 별도 인계 필수**

*"Commercial (Coocon) — source absent, separate handover required."*

Alongside the DDL, the `.xlsx` layout spec and `wecms_batch_HS.properties`, that is a
**fourth** artefact the handover marks as requiring separate delivery. None has arrived.

It also explains a limitation we hit: settling S-12 required **decompiling**
`jex.util.date.DateTime`, because the source is not in the repository. Any future
question about JEX behaviour — `IResultMaker`'s contract included — has the same
constraint.

### 🆕 SVN logs are a separate deliverable, and they would answer real questions

> 형상관리: **SVN**(`.svn`). Git 아님 → **이력 이관 시 svn 로그 별도 확보.**

Several dating questions in this audit went unresolved because the legacy tree has no
Git history:

- when the `//성조 처리 추가` block was added (which decided whether the 822 multibyte
  rows predate tone stripping — inferred from an IDO file version instead)
- when the July 2025 format change landed relative to the October one
- whether `TB_BATCH_SCHEDULE_DTL`'s twelve rows with identical `reg_dttm` were one
  insert or a backfill

All are answerable from SVN logs. Cheap to request, and it would firm up several
inferences currently resting on file timestamps.

### 🆕 `SecurityUtil` includes RSA — unexamined

> `SecurityUtil` | **AES256-CBC/HMAC-SHA256/RSA/SHA256** | apache commons-codec

We have verified the AES and HMAC paths exhaustively. **RSA and bare SHA-256 have never
been looked at.** Probably unused on the dispatch path — no EFS API in the vendor guide
uses asymmetric crypto — but "probably" is what the rest of this audit has spent its time
disproving. One grep settles it.

### Corroborations

The four batch classes, `WB_00000100` as an online SVC, the three `Deposit_*` record
classes, and the JSP-inlined callback logic all confirm. The service counts match
exactly: **43 IDO, 1 SVC, 3 WSVC, 1 CMO.**

Two paths are marked 미열람 — *not viewed* — by the handover author under its security
policy: `wecms_batch.properties` and `WECMS_BATCH_CONFIG/pkgInfo/*.prop`. The first is
consistent with the infrastructure reframing: the file is present in the repository with
values, the author simply declined to quote them. **The second has never been examined by
anyone** and is unknown territory.

### Added to the next check

```
14. impl/ResultMaker.java — a global response hook registered as result_maker in
    web.xml, so it runs for API1 and API2. Quote it in full and quote its web.xml
    registration.

    Does it modify RC, RM, or the envelope before the response goes on the wire? Doc 04
    says RM is URL-encoded on responses — is this where that happens?

    If it post-processes, every RC/RM comparison in the D-4 work was against the JSP's
    intermediate value, not the wire value. Report whether CallbackAck needs to match
    anything ResultMaker does.

15. util/LoadMemory.java — PropertyUtil's parent. Doc 06 never says what selects
    wecms_batch_HS.properties. Check here. Quote whatever picks between the base file
    and the _HS variant, and what key it uses.

16. SecurityUtil's RSA and SHA-256 paths: is either called from the dispatch path, the
    callbacks, or the Slack sender? One grep. If unused, record it so the question is
    not reopened.

17. WECMS_BATCH_CONFIG/pkgInfo/{API,wecms}/*.prop — marked 미열람 in the handover and
    never examined here. Report what they contain and whether anything is
    behaviourally relevant.
```

---

## Handover `08_기능추출_이관가이드` — read 2026-08-05

The migration guide, and it is now a **living document** — your team has amended it.
Item 6 carries a 2026-07-31 reopening written by the 인수 조직, in Korean and English,
with the `pg_indexes` finding and the *"do not restore 해소됨 until the authoritative DDL
actually arrives"* instruction. That is this audit's work written back into the source
document, which is the right place for it.

### ✅ `ResultMaker` — answered here, and it raises a new question

Item 4 resolves what doc `07` only named:

> **`impl.ResultMaker`** — JEX `IResultMaker` 구현체. `web.xml`의 `result_maker`
> init-param으로 등록되어, **프레임워크가 모든 서비스 응답 생성 시 자동 호출**(직접 호출
> 사이트 없음). 정상 `makeResult` / 예외 `makeExceptionResult`로 **응답공통부(WB_COMMON)의
> RC/RM/TR_DV_CD를 표준화** + WAS 로그(**기본오류 `99999`**, **`TR_DV_CD` `0000→0010` 보정**).

So it does two things nothing in our D-4 analysis accounted for:

**A default error code of `99999`.** That value appears **nowhere** in doc `05`'s
response-code table — not `0000`, `0001`, `9999`, `G00x`, `G99x` or `BTxx`. So on an
unhandled exception, legacy's global hook emits a code outside its own documented
domain. Our `CallbackAck` maps escapes to `G999`.

**A `TR_DV_CD` `0000→0010` correction.** A field-level transformation applied to every
response. Doc `02` §2.5 shows `WB_00000100` answering with `TK_DV_CD=0110` — near enough
in name that one of the two is likely a typo, and neither has been checked against our
implementation.

**What this means for the RC contract:** every legacy RC/RM value compared in the D-4
work was read from the **JSP**, and the JSP is not the last writer. Whether `ResultMaker`
overrides a JSP-set `RC` or only fills unset ones decides whether our matching is against
the wire value or an intermediate. Worth settling before the contract is called closed.

### 🔴 The Woori delay is explicitly rate-limit compliance

> §8.4 — **은행별 딜레이**: 우리은행 3초. **미적용 시 은행 측 유량제한(rate limit) 위반 가능.**

*"Bank-specific delay: Woori 3 seconds. If not applied, may violate the bank's rate
limit."*

The strongest statement yet on why C-6 mattered — and **it reinforces reverting
`PER_DELAY_DEFAULT` to 100ms.** The 3000ms value exists because Woori imposes a limit.
Other banks are documented at 100ms precisely because they do not. Applying Woori's
constraint to banks that never had one is not conservative, it is a throughput penalty
with no counterparty behind it.

### The pitfalls list, and how we fared

| §8.4 pitfall | Status |
|---|---|
| 금액 정수/소수 분리 — 단순 정수 처리 금지 | ✅ Matched (S-4 correctly not ported) |
| 응답 복호문 14자리 prefix 제거 | ✅ Matched, and now validated (C-15) |
| **무결성 VV 2-pass** — 한쪽만 구현하면 **간헐적 검증실패** | ⚠️ Declined, with the ordering reason recorded. Legacy's pass 2 is broken for pass 1's input class; ours handles both |
| 베트남어 성조 제거 — **미적용 시 은행 측 거절** | ✅ Applied unconditionally (D-L), which is stricter than legacy's gate |
| 고정 IV 0x00 — **임의 변경 시 통신 불가** | ✅ Byte-identical |
| **무응답=실패 아님** — 즉시 실패처리하면 **이중이체 위험** | ✅ Honoured. Principle #18 |
| 운영시간/타행망 매트릭스 — 마스터데이터 동반 이관 **필수** | ⚠️ Gate B. Inherited on UAT/PROD, seeded on SIT |
| **은행별 딜레이** — 미적용 시 rate limit 위반 | ✅ C-6 closed. Revert the default to 100 |
| 트랜잭션 경계 — 동일 원자성 보장 | ✅ Two-phase dispatch + per-sibling `REQUIRES_NEW` |

Nine pitfalls, seven cleanly matched, two carrying documented divergences with reasons.

### 🆕 The handover's own recommended test plan is exactly what is outstanding

> §8.6 step 5 — **UAT 환경에서 EFS 연동 통합테스트**(201/202/203/204/00301/00302)
> **+ SFTP + Slack**
> §8.6 step 6 — **운영시간/딜레이/무응답 시나리오 회귀테스트**

Six endpoints, plus SFTP, plus Slack, plus regression on operating hours, delays and
no-response. That is the integration test this audit has been unable to substitute for
by reading, and the handover names it as a required migration step rather than an
optional one.

Also §8.1: `.batch` cron values are to be ported **값 그대로 사용** — used as-is. Which
is what the one-minute periods and the `-` disabling represent.

### And the format contract, stated as strongly as it can be

> §8.3 item 3 — 파일 포맷 클래스 — 외부 은행과의 계약. **길이/패딩 한 글자도 변경 불가.**

*"The file format classes are a contract with the external bank. Not one character of
length or padding may be changed."*

Independently verified byte-for-byte in this audit, and now stated as a hard constraint
by the originating team.

### ⚠️ `_HS` — marked 해소됨, but the mechanism is still unstated

Item 7 resolves *what* the file is — 행성전자 전용, per-customer property separation — and
instructs 고객사별 설정 분리 메커니즘을 유지·재현할 것: *preserve and reproduce the
per-customer configuration separation mechanism.*

**It never says what selects it.** Doc `06` describes `PropertyUtil` loading only the base
file; doc `07` points at `LoadMemory` as the parent class. So the question narrows to
those two classes and remains open — with an explicit migration instruction attached that
we have not yet satisfied, since our configuration axis is environment-only.

### Added to the next check

```
18. ResultMaker (resolved in handover 08 item 4) — it standardises RC/RM/TR_DV_CD on
    every response, applies a default error code of 99999, and corrects TR_DV_CD
    0000→0010.

    a. Does it OVERRIDE a JSP-set RC, or only fill unset ones? This decides whether the
       whole D-4 comparison was against wire values or intermediates.
    b. 99999 appears nowhere in doc 05's response-code table. When does legacy actually
       emit it, and does our CallbackAck's G999 stand in for the same condition?
    c. TR_DV_CD 0000→0010 versus doc 02 §2.5's TK_DV_CD=0110 — same field, different
       spellings, or two fields? Do we set either?

19. [DONE 2026-08-05] PER_DELAY_DEFAULT — revert to 100. Handover 08 §8.4 states the
    3000ms Woori value exists to avoid violating that bank's rate limit; docs 05 and 06
    both give 100ms as the documented default for every other bank. Applying Woori's
    constraint universally is a throughput penalty with no counterparty requiring it.

    Applied: DispatchPacer.PER_DELAY_DEFAULT = 100L, Javadoc cites 05 §5.3 / 06 §6.3 /
    08 §8.4, DispatchPacerTest pins 100 and was re-verified in both directions (3000 →
    8 of 11 red; 100 → green). Per-row keying untouched — D-A stands.
```