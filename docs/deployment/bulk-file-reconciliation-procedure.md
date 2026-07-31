# Bulk-File Reconciliation Procedure — `BULK_EXECUTION_UNCONFIRMED` / `BULK_EXECUTION_NOT_ISSUED` / `RECEPTION_UNCONFIRMED` / **execution-result-not-received** (`PROC_STEP='2'`)

> **2026-07-31 — a fourth state, now detected: `BULK_EXECUTION_RESULT_NOT_RECEIVED`.**
> A firm file at **`PROC_STEP='2'` / `PROC_STAT='6'`** — `00302` accepted, `01302` result never
> returned. It was previously invisible: the firm-file sweep filtered `PROC_STEP='1'` only, and the
> parent pay-file reported it as the benign `AWAITING_EXECUTION_RESULT`. The sweep now queries both
> steps and raises this as its own cause. See **§1.2** for the state, **§5.4** for the action, **§8**
> for the live PROD backlog, and **§9** for two worked examples.
>
> **Two things found alongside it, both now fixed, both worth knowing before you read a stranded row:**
> 1. **False "all success"** — a pay-file could be finalised to `PROC_STAT='0'` while a file of its own
>    never reported. Two live PROD instances. Any pay-file status you are looking at that was written
>    **before 2026-07-31 may be wrong**; re-derive it from the firm-file rows (query 3.0). See §7.
> 2. **`01302` audit rows are stored under the `R_` name by legacy** and the `S_` name by the refactor,
>    so the old exact-name probe reported "no callback arrived" for every legacy file regardless. All
>    queries here now use a prefix-stripped match — see the box in §3.

**Audience:** on-call operator + the WeCMS dev on rota.
**Status:** operational procedure. Written 2026-07-30 (C-14, 2026-07-30 independent audit / batch-1 follow-up).
Extended 2026-07-30 (FIX F) with `RECEPTION_UNCONFIRMED` — see the note below.
**Scope:** the **three** firm-banking bulk-file states that have **no automatic retry** and resolve
**only by human action**.

> **FIX F — why a third state was added.** The first revision of this document covered only the two
> `BULK_EXECUTION_*` causes, which are raised by the **firm-file** sweep over
> `PROC_STEP='1'/PROC_STAT='6'`. That left a real state uncovered: an **unmatched `01301`** never
> advances `PROC_STEP` `0→1` at all, so the firm-file sweep — whose predicate is `PROC_STEP='1'` —
> cannot see it. It surfaces instead through the **parent pay-file** sweep, as
> `RECEPTION_UNCONFIRMED`, and an operator following the old §1 table would have found no row that
> matched what the alert said. It belongs here for the same reason as the other two: the file may
> already be at the bank, there is no retry, and only a human can resolve it.

---

## 0. Why this document exists

After the batch-1 fail-closed fix, every other dispatch failure mode ends in a terminal
`PROC_STAT='3'` (FAIL) that an operator can act on from the UI. These three do not. They sit at
`PROC_STAT='6'` indefinitely — the two `BULK_EXECUTION_*` states at
`TB_FIRM_FILE_MST.PROC_STEP='1'`, `RECEPTION_UNCONFIRMED` at `PROC_STEP='0'` — and in **all three the
payment file may already be at the bank**: the two execution states because `00301` succeeded, and
`RECEPTION_UNCONFIRMED` because the SFTP upload precedes the `00301` call. None of them is a "nothing
happened yet" state, and `PROC_STEP='0'` in particular must not be read as one.

The legacy system self-healed the two execution states within 60 seconds, because
`FILE_DEPOSIT_TRANSFER_BATCH2` re-picked
the row on its next poll. The approved TO-BE removed that batch in favour of inline execution from the
`01301` callback, and deliberately did **not** add a retry sweep (K9: Option A "Recovery Scheduler" was
offered and not selected — a duplicate `00302` risks double-executing a bulk payment, which is worse
than a stranded one). The residual is therefore **monitoring plus this procedure**, and nothing else.

`StuckPayFileMonitorService` is the only detection. It is enabled by default in SIT and UAT
(`wecms.dispatch.stuck-monitor.enabled=true`, cron `0 */5 * * * *`) and is read-only — it never mutates
`PROC_STAT`, never re-drives, never dispatches.

Two 2026-07-30 changes to that detection are load-bearing for this procedure:

- **FIX G — the `NOT_ISSUED` / `UNCONFIRMED` discriminator is no longer date-scoped.** The classifier
  probed for the `00302` audit row using the *file's* `TRAN_DT`, but that row is stamped with the
  *callback* clock. A file built at 23:58 whose `01301` landed at 00:02 was therefore reported
  `BULK_EXECUTION_NOT_ISSUED` **even though `00302` had been sent** — and §5.1 row A of this document
  says `NOT_ISSUED` plus "not executed" makes a re-drive routine. **If you are holding an alert raised
  before 2026-07-30, do not trust its cause label**: re-run query 3.2 yourself and classify from the
  audit rows. After the fix the classifier fails safe towards `UNCONFIRMED`.
- **FIX H — the firm-file sweep is no longer scoped to today.** It previously selected
  `TRAN_DT = <today ICT>`, so a file stranded at 23:59 **disappeared from the sweep at 00:00 and was
  never reported again**. It is now bounded on `PROC_DTTM`: older than the 15-minute threshold, newer
  than `stuck-monitor.retention-days` (default 7). Consequences for you: counts may be higher than you
  are used to (files from previous days are now included, which is the blind spot closing), and a file
  stranded for **more than the retention window still falls out of view** — the cliff is moved, not
  abolished. If an alert stops recurring without anyone acting, check `PROC_DTTM` against the window
  before concluding it resolved.

> **The one rule that overrides everything below:** never re-issue a `00302` for a file whose execution
> status — or a `00301` for a file whose **reception** status — you have not confirmed with the VAN
> Center. "Reconcile before acting" is the whole procedure, not a caveat on it.

---

## 1. How the three states differ

**Read the `PROC_STEP` first — it decides which half of the table you are in.**

The two `BULK_EXECUTION_*` states come from the **firm-file** sweep and present identically in the
database — `PROC_STEP='1'`, `PROC_STAT='6'`, older than the 15-minute threshold. The **only**
discriminator between them is whether a `00302` audit row exists in `TB_FIRM_BAT_API_TRAN`.

`RECEPTION_UNCONFIRMED` is a **different row shape entirely** — the firm file is still at
`PROC_STEP='0'` — and it is raised by the **pay-file** sweep, not the firm-file sweep. If you look for a
`PROC_STEP='1'` row because the procedure said so, you will not find one.

| | `RECEPTION_UNCONFIRMED` | `BULK_EXECUTION_NOT_ISSUED` | `BULK_EXECUTION_UNCONFIRMED` | **execution-result-not-received** (§1.2) |
|---|---|---|---|---|
| Raised by | **pay-file** sweep (`TB_PAY_FILE_MST.PROC_STAT='6'`) | firm-file sweep | firm-file sweep | **firm-file sweep**, as `BULK_EXECUTION_RESULT_NOT_RECEIVED` (since 2026-07-31; previously undetected) |
| Firm-file row | `PROC_STEP='0'` / `PROC_STAT='6'` | `PROC_STEP='1'` / `PROC_STAT='6'` | `PROC_STEP='1'` / `PROC_STAT='6'` | **`PROC_STEP='2'` / `PROC_STAT='6'`** |
| **Discriminator** | `PROC_STEP='0'` **and** a firm-file row exists; then read whether a **`00301`** audit row is present | `PROC_STEP='1'` **and** the **`00302`** audit row is **absent** | `PROC_STEP='1'` **and** the **`00302`** audit row is **present** | `PROC_STEP='2'` — **the file itself advanced**, so a `00302` succeeded. A `00302` audit row is present *and* carries a success code |
| Which leg is unconfirmed | **reception** (`00301`) | execution (`00302`) — never issued | execution (`00302`) — issued, unconfirmed | **the result** (`01302`) — execution was *accepted*, the outcome never came back |
| Did the bank receive the FILE? | **Unknown — assume possibly yes** (SFTP upload precedes the `00301` call) | Yes — reception succeeded | Yes — reception succeeded | Yes |
| Did the bank receive an EXECUTION instruction? | **No** — the flow never got that far | **No** | **Unknown — assume possibly yes** | **Yes — confirmed.** The gateway answered `0000`/`BT00`; that is what advanced the step |
| Cause | The `00301` leg did not complete: the file was built and the firm-file row committed, but the reception request failed, or its `01301` result never matched (see §1.1) | (a) lost `01301` callback; (b) **JVM death between the reception commit and the `afterCommit` `00302` fan-out** — an in-JVM hook, not a persisted outbox; (c) file re-read at fan-out time and no longer reception-success | Gateway did not confirm (empty body, or `BT01` / `G999` / `C999`), or the confirmation was lost in transit | The `01302` callback never arrived, or arrived and could not be matched / failed mid-handler and rolled back (the handler leaves the file at `'2'` by design on a parse or download failure) |
| Default disposition after confirming with the VAN Center | Re-upload is usually harmless; **re-issuing `00301` is not** | Re-drive is usually correct | Re-drive is usually **wrong** | **Never re-drive.** The instruction is confirmed-delivered; only the result is missing |

A gateway **retry** of the lost `01301` does not rescue `BULK_EXECUTION_NOT_ISSUED` case (b): the file is
past `PROC_STEP='0'`, so the retry is classified as an already-processed replay and acked `RC=0000`.


### 1.1 `RECEPTION_UNCONFIRMED` — what "our row says otherwise" means

> **A file sitting at `PROC_STEP='0'` / `PROC_STAT='6'` may already have been received by the bank.**
> Our row is not evidence that it was not. Read this before you act on one.

There are two ways to reach this state, and **our data cannot always tell them apart**:

1. **The `00301` request itself did not complete.** The SFTP upload happens **before** the `00301`
   call, so the file may be sitting on the bank's server with no accepted instruction against it.
2. **The `00301` succeeded and the `01301` reception result was lost or never matched.** The bank
   received the file *and* accepted it; only our confirmation is missing. Until 2026-07-30 the most
   common cause of the "never matched" variant was a **date-rollover defect** (FIX A): the `01301`
   handler derived its `TRAN_DT` lookup key from the callback clock while the row carried the
   file-build date, so a file built at 23:58 whose callback arrived at 00:02 could never be matched —
   and every gateway retry missed identically. That defect is fixed (the lookup key now comes from the
   file name, which carries its own date), but **rows stranded by it before the fix are still in the
   table**, and any future lost callback lands in the same state.

So `PROC_STEP='0'` means "**we** have not confirmed reception", never "the bank has not received it".
The `00301` audit row in `TB_FIRM_BAT_API_TRAN` narrows it — its presence means a request was actually
issued — but even a present row with no success code leaves egress ambiguous, which is why both
variants map to one cause constant and both require the VAN Center before any action.

**Reading it in the log.** The pay-file sweep emits the audit-row fact alongside the cause, so the two
variants are distinguishable without a second constant:

```
feature=stuck-pay-file-monitor claimed-stranded pay-file at PROC_STEP=0 biz_key=… file_seq_no=…
  reception_audit_row_present=true — reconcile with the VAN Center before any manual action
  (the file may already be on the bank's SFTP server)
```

`reception_audit_row_present=true` → variant 2 is likely (a request was issued).
`reception_audit_row_present=false` → variant 1 is likely (the failure was earlier).
**Neither value licenses an action on its own.**

---

### 1.2 `PROC_STEP='2'` / `PROC_STAT='6'` — execution accepted, result never received

> **This is the most dangerous of the four states to mishandle, and currently the least visible.**
> The bank has almost certainly moved the money. Nothing in the monitor says so.

**Why the discriminator is different.** For the two `BULK_EXECUTION_*` states you have to *infer* what
the bank did from an audit row. Here you do not: **the file advanced to `PROC_STEP='2'`**, and the only
code path that writes that step is
`BulkExecutionInlineTxService` after the `00302` returned a success code (`0000` or `BT00`). So a
`PROC_STEP='2'` row is *positive evidence* that the gateway accepted the execution instruction — not an
ambiguity to be resolved. What is missing is the `01302` result callback that reports per-line outcomes.

**Two ways to get here:**

1. **The `01302` never arrived** — gateway-side loss, or it was never sent.
2. **The `01302` arrived and the handler rolled back.** `BulkExecutionResultService` deliberately leaves
   the file at `PROC_STEP='2'` when the SFTP result-file download or the fixed-length parse fails, so the
   callback can be retried. If the underlying cause is persistent (result file absent from the remote
   path, wrong path, malformed file), every retry rolls back identically and the file stays at `'2'`
   forever. **Check for a `01302` audit row** — its presence distinguishes these two, and it is the
   first thing to look at.

**Why it is not `BULK_EXECUTION_NOT_ISSUED`.** That cause means *no instruction reached the bank*, and
its default disposition is "re-drive is usually correct". Applying that reasoning to a `PROC_STEP='2'`
file would re-issue a `00302` for a batch the bank has already executed — a double-payment. The two
states need **opposite** actions, which is exactly why this needs its own row rather than being folded
into an existing cause.

**Finding these rows — do this manually until the sweep is extended:**

```sql
-- Firm files whose execution was accepted but whose result never came back.
SELECT m.tran_dt, m.biz_key, m.file_seq_no, m.firm_file_nm, m.req_cnt, m.req_amt, m.proc_dttm,
       (SELECT count(*) FROM tb_firm_bat_api_tran a
         WHERE a.biz_key = m.biz_key AND a.firm_file_nm = m.firm_file_nm
           AND a.api_tran_tp = '00302') AS audit_00302,
       (SELECT count(*) FROM tb_firm_bat_api_tran a
         WHERE a.biz_key = m.biz_key AND a.firm_file_nm = m.firm_file_nm
           AND a.api_tran_tp = '01302') AS audit_01302,
       p.proc_stat AS parent_pay_stat
  FROM tb_firm_file_mst m
  LEFT JOIN tb_pay_file_mst p
         ON p.tran_dt = m.tran_dt AND p.biz_key = m.biz_key AND p.file_seq_no = m.file_seq_no
 WHERE m.proc_step = '2' AND m.proc_stat = '6'
 ORDER BY m.tran_dt DESC;
```

`audit_01302 = 0` → the callback never arrived (variant 1).
`audit_01302 > 0` → it arrived and the handler kept failing (variant 2) — go and read the application
log for that file before anything else; the fault is likely on our side (SFTP path, malformed file).

**Do not rely on the parent pay-file to surface these.** It usually sits at `PROC_STAT='6'` and is
reported as `AWAITING_EXECUTION_RESULT`, whose documented meaning is *"often not a defect … simply has
not been finalised yet"* and which explicitly defers the actionable detail to the firm-file sweep — a
report that is never produced for a `PROC_STEP='2'` file. Worse, if a **sibling** split file failed and
set the pay-file to `'3'`, the pay-file drops out of the claimed sweep entirely and the stranded
`PROC_STEP='2'` sibling becomes invisible to every sweep.
## 2. Detection — what the alert and the log look like

Per-file WARN (one line per file, capped at `max-alert-keys`, default 20). The **firm-file** sweep, which
raises the two `BULK_EXECUTION_*` causes:

```
feature=stuck-pay-file-monitor stranded firm-file biz_key=… tran_dt=… file_seq_no=…
  firm_file_nm=S_GV00000001_WRB_001_20260730 out_bank_cd=970457 req_cnt=… proc_step=1
  proc_stat=6 proc_dttm=… stuck_minutes=… cause=BULK_EXECUTION_UNCONFIRMED
```

The **pay-file** sweep, which raises `RECEPTION_UNCONFIRMED` — note it carries **no `firm_file_nm`**
(the pay-file may have several split siblings), so §3 is entered by `file_seq_no`, not by file name:

```
feature=stuck-pay-file-monitor claimed-stranded pay-file at PROC_STEP=0 biz_key=…
  file_seq_no=… reception_audit_row_present=true — reconcile with the VAN Center before any
  manual action (the file may already be on the bank's SFTP server)
```

Sweep summary:

```
feature=stuck-pay-file-monitor SUMMARY stuck_total=… claimed_stuck_total=…
  firm_file_stuck_total=… threshold_minutes=15 cutoff=… by_cause={…}
```

Grep `feature=stuck-pay-file-monitor` to pull the whole history. One **aggregated** ops alert is sent
per sweep (never one per file), suppressed while the stuck set is unchanged and re-sent on the
`re-alert-minutes` heartbeat (default 60).

**Alert delivery — three things must all be true (FIX I, 2026-07-30).** `wecms.dispatch.alert.enabled`
is now `true` by default in SIT and UAT (it was `false`, which made this monitor's alert — the only
detection for the two states above — a log line and nothing more). But the flag is only the first link:

| # | Requirement | State |
|---|---|---|
| 1 | `wecms.dispatch.alert.enabled=true` | **SIT/UAT: yes**, as of FIX I |
| 2 | `wecms.dispatch.efs.enabled=true` + `base-url` | **no** — B1. Without it `OpsAlertService` calls `MockEfsApiClient`, which throws; the failure is swallowed and logged as `ops-alert send failed vanOrgCd=… — swallowed` |
| 3 | `TB_VAN_API_INFO.API_KEY` row for the alert's `VAN_ORG_CD` | DEV/SIT: present (2 rows). **UAT: recorded MISSING** — B2. Without it `VanApiKeyService` throws and the alert is swallowed the same way |

So **today the alert still does not reach Slack** — but the failure is now visible as a WARN per sweep
instead of silence, and delivery starts with no further config change once (2) and (3) land. Until then:
**grep is the alert channel.** If you are on rota for these two states, poll
`feature=stuck-pay-file-monitor` rather than waiting to be paged.

The channel itself is the shared legacy `WECMSBATCH` Slack group (`grpID` is a fixed legacy constant),
so once it works, refactor alerts land alongside the live JEX batch's. The `[ENV][WAS_NO]` label from
`wecms.dispatch.alert.env-mode` is what distinguishes them.

> **The counts can be a lower bound.** If you see `firm-file sweep cap reached (limit 200)` or
> `pay-file sweep cap reached`, the sweep truncated — treat the numbers as "at least".

---

## 3. Step 1 (all three states) — establish the facts from our own data

Run these before contacting anyone. Substitute the `biz_key` / `tran_dt` / `firm_file_nm` from the
alert. **Read-only queries only** — do not `UPDATE` anything in this step.

> **Coming from a `RECEPTION_UNCONFIRMED` alert?** It gives you `biz_key` + `file_seq_no` but **no
> `firm_file_nm`**. Run **3.0 first** to get the name(s), then run 3.1–3.5 for each one. A branch-split
> pay-file has several, and they can be in different states.

```sql
-- 3.0  RECEPTION_UNCONFIRMED entry point: name every firm file under this pay-file.
--      Expect PROC_STEP='0' on at least one row — that is what put you here.
SELECT tran_dt, firm_file_nm, file_seq, proc_step, proc_stat, req_cnt, req_amt,
       reg_dttm, proc_dttm, reg_rslt_cd, reg_rslt_msg
FROM   tb_firm_file_mst
WHERE  biz_key = :biz_key AND tran_dt = :tran_dt AND file_seq_no = :file_seq_no
ORDER  BY firm_file_nm;
-- Zero rows here is a DIFFERENT cause (CLAIMED_NO_FIRM_FILE) — nothing was built and nothing was
-- uploaded. That state is not in scope for this document.
```

> ### ⚠️ Every query below matches `FIRM_FILE_NM` with the leading 2 characters stripped. Do not "fix" it.
>
> The same batch is stored under **two different names** depending on which side wrote the row:
>
> | Row | Name it carries |
> |---|---|
> | `TB_FIRM_FILE_MST`, `TB_FIRM_FILE_DATA` | the **sent** name, `S_…` |
> | `TB_FIRM_FILE_RSLT` | the **callback's** name — `R_…` |
> | `TB_FIRM_BAT_API_TRAN` `01302`, **legacy-written** | the callback's `R_…` name (`API2_act.jsp:189` binds `file_nm`) |
> | `TB_FIRM_BAT_API_TRAN` `01302`, **refactor-written** | the sent `S_…` name (`BulkExecutionResultService.writeApiTran`) |
>
> An exact `firm_file_nm = :firm_file_nm` probe for a `01302` row therefore returns **0 for every
> legacy-written file, whether or not a callback ever arrived.** That is not a cosmetic miss: it is the
> difference between *"the gateway lost the result"* (their problem) and *"the result arrived and our
> download/parse kept rolling back"* (**our** problem, fixable without calling the bank). An earlier
> revision of these queries used the exact match and reported `audit_01302 = 0` for all 35 PROD stranded
> files — **an artifact of the query, not evidence.**
>
> It also means you can paste **either** name into `:firm_file_nm` and every query still resolves.

```sql
-- 3.1  The file itself: confirm it is genuinely stranded and how long for.
SELECT tran_dt, biz_key, file_seq_no, firm_file_nm, out_bank_cd, bank_prtn_id, van_org_cd,
       file_type, file_seq, proc_step, proc_stat, req_cnt, req_amt, reg_dttm, proc_dttm,
       reg_rslt_cd, reg_rslt_msg
FROM   tb_firm_file_mst
WHERE  biz_key = :biz_key AND tran_dt = :tran_dt
  AND  substring(firm_file_nm,3) = substring(:firm_file_nm,3);

-- 3.2  THE DISCRIMINATOR — every API leg logged for this file, in one query.
--
--      >>> NOTE THE MISSING tran_dt PREDICATE. This is deliberate, and it is the same reason the
--      >>> application's own idempotency guard dropped it (FIX D, 2026-07-30): an audit row's TRAN_DT
--      >>> is the CALL clock, while the file row's TRAN_DT is the BUILD clock. For anything that
--      >>> straddled ICT midnight those differ by a day, so adding `AND tran_dt = :tran_dt` here can
--      >>> hide the very row you are looking for and make an issued 00302 look absent — i.e. make
--      >>> UNCONFIRMED read as NOT_ISSUED, whose disposition is the opposite. FIRM_FILE_NM carries its
--      >>> own date, so this predicate is already date-specific. Do not "tighten" it.
--
--      PROC_STEP='1' rows -> read 00302:  absent = NOT_ISSUED,  present = UNCONFIRMED
--                                          (then read RS_CD / RS_MSG / RS_DTTM).
--      PROC_STEP='0' rows -> read 00301:  this is RECEPTION_UNCONFIRMED; its presence means a
--                                          reception request was actually issued (§1.1 variant 2),
--                                          its absence means the failure was earlier (variant 1).
--                                          NEITHER licenses an action — see §5.3.
--      PROC_STEP='2' rows -> read 01302:  absent = the callback never arrived (gateway lost it);
--                                          present = it arrived and OUR handler kept rolling back
--                                          (SFTP download / parse) — see §1.2 and §5.4 answer C.
--
--      >>> The prefix-stripped match on FIRM_FILE_NM is REQUIRED here, not cosmetic — a legacy 01302
--      >>> audit row carries the R_ name while the file carries S_. See the box above.
SELECT api_tran_tp, firm_file_nm AS audit_file_nm, tran_dt AS audit_tran_dt, tran_seq, tran_tm,
       bat_cnt, bat_amt, reg_rslt_cd, reg_rslt_msg, rs_cd, rs_msg, rs_dttm
FROM   tb_firm_bat_api_tran
WHERE  biz_key = :biz_key
  AND  substring(firm_file_nm,3) = substring(:firm_file_nm,3)
ORDER  BY api_tran_tp, tran_dt, tran_seq;
-- `audit_file_nm` is selected so you can see WHICH name each leg was written under — an R_ on the
-- 01302 row means legacy wrote it, an S_ means the refactor did.
-- `audit_tran_dt` is selected so you can SEE a rollover when there is one: an audit row dated a day
-- later than the file's TRAN_DT is the signature, and it is normal, not corruption.

-- 3.3  The originating pay-file: is it also stranded, or already terminal?
SELECT tran_dt, biz_key, file_seq_no, out_bank_cd, wdrl_act_no, fund_tp, tran_tp,
       proc_stat, req_cnt, req_amt, rs_msg, reg_dttm, proc_dttm
FROM   tb_pay_file_mst
WHERE  biz_key = :biz_key AND tran_dt = :tran_dt AND file_seq_no = :file_seq_no;

-- 3.4  What is actually in the file — the amount and count you will quote to the VAN Center.
SELECT count(*) AS lines, sum(tran_amt) AS total_amt, min(seq) AS first_seq, max(seq) AS last_seq
FROM   tb_firm_file_data
WHERE  biz_key = :biz_key AND tran_dt = :tran_dt
  AND  substring(firm_file_nm,3) = substring(:firm_file_nm,3);

-- 3.5  Per-line processing state. All 'N' means no result has been written back for any line.
SELECT proc_rslt_div, count(*)
FROM   tb_firm_file_data
WHERE  biz_key = :biz_key AND tran_dt = :tran_dt
  AND  substring(firm_file_nm,3) = substring(:firm_file_nm,3)
GROUP  BY proc_rslt_div;
```

Record `van_org_cd`, `bank_prtn_id`, `firm_file_nm`, line count and total amount — these are what the
VAN Center needs to look the batch up.

**Branch-split files:** if `TB_FIRM_CONF.BAT_TRAN_BRANCH_DIV='Y'`, one pay-file produces several
`firm_file_nm` values sharing one `file_seq_no`. Query 3.1 by `file_seq_no` (dropping the
`firm_file_nm` predicate) to see all siblings, and **treat each sibling separately** — some may have
executed and some not.

---

## 4. Step 2 — the VAN Center question

### 4.1 For the two `BULK_EXECUTION_*` states (`PROC_STEP='1'`)

There is exactly one question, and it is the same for both:

> **For file `<firm_file_nm>` (`<line count>` lines, total `<total_amt>`, partner `<bank_prtn_id>`,
> org `<van_org_cd>`, date `<tran_dt>`): did the bank RECEIVE an execution instruction, and did it
> EXECUTE the batch? If it executed, please provide the per-line results.**

### 4.2 For `RECEPTION_UNCONFIRMED` (`PROC_STEP='0'`)

**A different question — do not ask the §4.1 one.** No execution instruction was ever issued from our
side, so asking "did it execute" invites a "no" that tells you nothing about the file's whereabouts, and
an operator who hears "not executed" is liable to re-drive. What is unknown here is **whether the bank
holds the file and whether it accepted it**:

> **For file `<firm_file_nm>` (`<line count>` lines, total `<total_amt>`, partner `<bank_prtn_id>`,
> org `<van_org_cd>`, date `<tran_dt>`): (1) is this file PRESENT on your SFTP server? (2) was its
> RECEPTION accepted or rejected — and if accepted, what reception result was returned to us and when?
> (3) is there any execution activity against it? Please confirm explicitly that NO execution
> instruction has been received, so it is safe for us to re-drive the reception leg.**

Point (3) is not redundant. Reception acceptance without a matching `01301` on our side means our view
of the file is already one step behind the bank's; before re-issuing anything we need it stated that the
bank is not further ahead still.

Ask for the answer in writing, in both cases. Everything downstream depends on which answer you get.

---

### 4.3 For **execution-result-not-received** (`PROC_STEP='2'`)

The question is **not** "did you execute?" — the `PROC_STEP='2'` row already tells you the gateway
accepted the instruction. Ask for the **outcome**, and for the result file:

> For file `<FIRM_FILE_NM>`, dated `<TRAN_DT>`, `<REQ_CNT>` lines, total `<REQ_AMT>`:
> we sent the execution instruction (`00302`) at `<PROC_DTTM>` and received your success acknowledgement,
> but we have never received the execution **result** (`01302`) or its result file.
>
> 1. **Were these `<REQ_CNT>` lines executed?** Per line: settled / failed / not processed.
> 2. Was a result file produced for this batch? If so, under what **exact filename** and on which
>    remote path — and is it still there?
> 3. Was a `01302` notification sent to us, and when? If it was sent and we did not acknowledge it,
>    we need the retry history.

Item 2 matters as much as item 1: if the result file exists but our download failed, this is our defect
(wrong path, permissions, name mismatch) and the fix is on our side, not theirs.

---

## 5. Step 3 — action per answer

The action depends on the **VAN Center's answer**, not on the monitor's cause. The cause only tells
you which answer to expect.

### 5.1 `BULK_EXECUTION_NOT_ISSUED` (no `00302` audit row)

| VAN Center answer | What it means | Action |
|---|---|---|
| **A. Not executed; no execution instruction received** | The expected answer. The file is on the bank's SFTP server, reception succeeded, execution was never requested. Nothing has moved. | **Re-drive.** Have the dev re-issue the `00302` for this `firm_file_nm` (see §6). Then verify `PROC_STEP` advances to `'2'` and the `01302` callback lands. This is the only branch where a re-drive is routine. |
| **B. Executed** | Our audit row is missing but the bank acted — i.e. a `00302` was sent and the audit write was lost, or the bank executed off the reception alone. **Do not re-drive.** | Treat as §5.2 row **B**: obtain the per-line results and have the dev reconcile the file forward from them. Escalate to the PO — a missing audit row for an executed batch is an audit-integrity defect, not just a stranded file. |
| **C. Cannot determine** | | Do **not** re-drive. Escalate to the PO + VAN Center jointly. Leave the file stranded — a stranded file is recoverable, a double-executed one is not. |
| **D. File not received / rejected at reception** | Contradicts our `00301` success. | Do not re-drive. Escalate; the reception audit row and the bank's record disagree and that must be understood first. |

### 5.2 `BULK_EXECUTION_UNCONFIRMED` (`00302` audit row present)

Read `RS_CD` / `RS_MSG` from query 3.2 first — but **`RS_CD` is not authoritative about execution.** A
`BT01` / `G999` / `C999` or an empty body means *we did not get a confirmation*, not *the bank did not
execute*.

| VAN Center answer | What it means | Action |
|---|---|---|
| **A. Not executed; instruction received but not acted on** | The instruction reached the gateway and stopped there. | **Re-drive** (see §6), then verify `PROC_STEP='2'` + `01302`. Confirm with the VAN Center that the original instruction is definitively cancelled/void before re-issuing, otherwise the re-drive is the duplicate. |
| **B. Executed** | The default assumption for this state. Money has moved. | **Never re-drive.** Obtain the per-line results and have the dev reconcile the file forward (`PROC_STEP='2'`, per-line `PROC_RSLT_DIV`, pay-target finalization) from the bank's authoritative results. Notify the payment owner that the batch settled. |
| **C. Executed with partial failures** | Some lines settled, some did not. | **Never re-drive the whole file.** Reconcile per line from the bank's results. Any re-send is per failed line and needs explicit PO approval. |
| **D. Cannot determine** | | Do **not** re-drive. Escalate to PO + VAN Center. Leave stranded and keep the monitor watching it. |

### 5.3 `RECEPTION_UNCONFIRMED` (firm file at `PROC_STEP='0'`)

Read the `00301` audit row from query 3.2 first — but **its absence is not evidence the file is not at
the bank.** The SFTP upload precedes the `00301` call, so "no request was accepted" and "no file was
delivered" are different statements and only the VAN Center can separate them.

| VAN Center answer | What it means | Action |
|---|---|---|
| **A. File not present on the SFTP server** | The upload did not land, or landed somewhere else. Nothing is at the bank and nothing was instructed. | **Safe to re-drive the reception leg.** Have the dev re-run `00301` for this `firm_file_nm` (see §6). The `FIRM_FILE_NM` is deterministic, so a re-upload overwrites rather than duplicating. Then verify `PROC_STEP` advances `0→1` and the `01301` lands. |
| **B. File present; reception NOT accepted (or rejected)** | The file is on the server but no instruction stands against it. | **Re-drive the reception leg** (see §6). Confirm with the VAN Center that the earlier attempt is definitively void first — if it is merely *unprocessed* rather than *rejected*, re-issuing `00301` is the duplicate. |
| **C. File present; reception ACCEPTED** | The bank accepted it and our `01301` was lost or unmatched — §1.1 variant 2. **Do NOT re-issue `00301`:** a second accepted reception for the same file can produce a second execution downstream. | Obtain the reception result the bank returned (result code, count, amount, timestamp) and have the dev **reconcile the reception forward** — advance the firm file `PROC_STEP='0'→'1'` from the bank's authoritative values, which lets the normal `00302` path take over. Cross-check the bank's count/amount against query 3.4 **before** advancing; a mismatch is a FAIL, not an advance. |
| **D. File present; EXECUTION activity exists** | The bank is two steps ahead of us. Money may have moved with no `01301` and no `00302` on our side. | **Never re-drive anything.** Escalate to the PO immediately and treat it as §5.2 row **B**: obtain the per-line results and reconcile the file forward to `PROC_STEP='2'` from them. Also raise it as an integration defect — this combination should not be reachable. |
| **E. Cannot determine** | | Do **not** re-drive. Escalate to the PO + VAN Center. Leave the file stranded and keep the monitor watching it. A stranded file is recoverable; a double-received one is not. |

**If the file was stranded by the pre-2026-07-30 date-rollover defect** (FIX A — check query 3.2 for a
`01301` audit row, or its conspicuous absence, and compare the file's `TRAN_DT` with `REG_DTTM` near
midnight), answer **C** is the expected one: the bank accepted it and our handler could not match the
callback. Reconcile forward; do not re-issue. The defect itself is fixed, so this applies to
already-stranded rows, not to new ones.

### 5.4 **execution-result-not-received** (firm file at `PROC_STEP='2'`)

**Never re-drive. There is no correct re-drive from this state.** The execution instruction is
confirmed-delivered and confirmed-accepted; re-issuing `00302` would be a straight duplicate
instruction. Everything here is *reconcile forward* or *escalate*.

| VAN Center answer | What it means | Action |
|---|---|---|
| **A. Executed; result file exists** | The expected answer. Money has moved; only our copy of the outcome is missing. | Obtain the result file (or the per-line outcomes) and **reconcile forward** (§6): per-line `PROC_RSLT_DIV` / `RS_CD` onto `TB_FIRM_FILE_DATA` and `TB_PAY_FILE_DATA`, `TB_FIRM_FILE_MST` → `PROC_STEP='3'` / `PROC_STAT='0'` with the trailer aggregates, then finalize the pay-file. Notify the payment owner that the batch settled. |
| **B. Executed with partial failures** | Some lines settled, some did not. | Same reconcile-forward, per line. Any re-send is **per failed line** with explicit PO approval — never the whole file. |
| **C. Result file exists on the remote path (we failed to fetch it)** | **Our defect, not theirs.** The `01302` handler downloads before it parses and rolls back on either failure, leaving the file at `'2'`. | Fix the fetch (path / permissions / filename), then let the gateway's `01302` retry land — or, if it will not retry, reconcile forward from the file. Raise the root cause: a persistent download failure strands *every* batch, not just this one. |
| **D. Not executed, despite the accepted instruction** | Contradicts our `00302` success acknowledgement. | Do **not** act on our data. Escalate to PO + VAN Center jointly — the gateway acknowledged an instruction it did not carry out, which is an integration defect well beyond this file. |
| **E. Cannot determine** | | Do **not** re-drive. Escalate. Leave the file at `'2'` — it is a faithful record of what we know, and a stranded file is recoverable where a double-executed one is not. |

**Before calling:** run the §1.2 query and check `audit_01302`. If it is **> 0**, the callback *did*
arrive and our handler kept failing — read the application log for that `firm_file_nm` first; you may be
able to answer **C** without involving the bank at all.

### 5.5 In every branch

- Record the VAN Center's answer, the timestamp, and who you spoke to, against `firm_file_nm`.
- Do not resolve the alert by silencing the monitor. The fingerprint clears itself once the file
  advances; if it stops being reported without you acting, find out why.

---

## 6. Re-drive and reconcile — mechanics

Both are **dev-executed**, never operator-executed, and both need the §4 answer on file first.

- **Re-drive (execution)** = re-issue `00302` for one `firm_file_nm` through the normal bulk-execution
  path. There is no UI button and no scheduled retry for this **by design** (K9). It is a deliberate,
  logged, one-file action.
  - The application's own guard will **refuse** a second `00302` for a file that already has one, and
    since FIX D that guard is no longer date-scoped — so it holds across a midnight boundary too. Do not
    treat the guard as the safety mechanism, though: it protects against an accidental repeat, not
    against a deliberate re-drive of a file the bank already executed. §4 is the safety mechanism.
- **Re-drive (reception)** = re-upload the file over SFTP and re-issue `00301` for one `firm_file_nm`.
  Only for §5.3 answers **A** and **B**. The upload half is idempotent (`FIRM_FILE_NM` is deterministic,
  so it overwrites); the `00301` half is **not** — that is why an accepted reception (answer **C**) is
  reconciled forward instead.
- **Reconcile forward (execution result)** = write the bank's authoritative per-line results onto
  `TB_FIRM_FILE_DATA.PROC_RSLT_DIV`, advance `TB_FIRM_FILE_MST` to `PROC_STEP='2'`, and finalize the
  originating `TB_PAY_TARGET` / `TB_PAY_FILE_MST` rows — the same end state the `01302` callback would
  have produced.
- **Reconcile forward (reception result)** = advance `TB_FIRM_FILE_MST` `PROC_STEP='0'→'1'` with the
  bank's reception result (`REG_CNT` / `REG_AMT` / `REG_RSLT_CD` / `REG_RSLT_MSG`), leaving
  `PROC_STAT='6'` — the end state the `01301` callback would have produced. The normal `00302` path then
  applies. Verify the bank's count and amount against query 3.4 first: on a mismatch the correct end
  state is FAIL (`PROC_STEP='1'`/`PROC_STAT='3'`), which is what the callback itself would have written.

Any manual DML follows the workspace rule: a reviewed script under `Claude Code (AI)/sql/`, run by a
DBA, scoped to the single `(biz_key, tran_dt, firm_file_nm)` — never an ad-hoc `UPDATE` against a
shared DB. On UAT/PROD the DB is shared with the live JEX system, so a broad `WHERE` clause can touch
legacy-written rows.

---

## 7. Prevention and open items

- `BULK_EXECUTION_NOT_ISSUED` case (b) — JVM death between the reception commit and the `afterCommit`
  `00302` fan-out — is a **known residual of the inline-execution design**, not a bug to be fixed in
  place. Eliminating it needs a persisted outbox (or the two-phase dispatch change, which is
  separately tracked). Until then this procedure *is* the mitigation.
- `BULK_EXECUTION_UNCONFIRMED` will keep occurring for as long as the gateway can fail to confirm. Its
  frequency is the signal worth trending — a rise means a gateway problem, not an application one.
- `RECEPTION_UNCONFIRMED` had a **code cause** that is now fixed: the `01301` date-rollover defect
  (FIX A, 2026-07-30) made every callback across ICT midnight unmatchable and permanently stranded the
  file. Rows created before that fix remain. **New** occurrences should now only be genuine lost
  callbacks or failed reception requests, so a continuing stream of them after the fix is a signal —
  check for another unmatched-callback path before assuming gateway loss.
- `RECEPTION_UNCONFIRMED` cannot be eliminated for the same structural reason as
  `BULK_EXECUTION_NOT_ISSUED` case (b): the SFTP upload precedes the `00301` call, so there is always a
  window in which the bank holds a file we have no accepted instruction for. Closing it needs a
  persisted outbox / two-phase dispatch, which is tracked separately.
- If either state recurs often enough that this procedure becomes routine, that is the evidence needed
  to revisit K9 with the PO. Bring counts, not impressions: `by_cause` in the sweep summary is the
  source.
- **CLOSED (2026-07-31) — `PROC_STEP='2'` / `PROC_STAT='6'` now has detection.** The firm-file sweep
  makes two calls (`'1'` and `'2'`) and raises `BULK_EXECUTION_RESULT_NOT_RECEIVED` as its own cause;
  `classifyClaimedPayFile` no longer reports such a file as the benign `AWAITING_EXECUTION_RESULT`.
  `by_cause` is now a valid trend source for this state. **No retry was added — K9 stands.**
- **FIXED (2026-07-31) — false "all success" on pay-file finalisation.** `finalizePayFileStatus`
  computed `tot`/`succ` over terminal siblings only, so a sibling at `PROC_STAT='6'` was in neither
  count. For a **single** file stranded at `'2'/'6'` that gave `tot=0, succ=0` — and `0 == 0` is true —
  finalising the parent to `'0'` (ALL SUCCESS) while its only file never reported. **Two live PROD
  instances**, both `siblings=1`; no split batch is required to reach it. The barrier did not help: it
  tested `PROC_STEP='0' AND PROC_STAT='6'`, a faithful port of legacy `R003`, so a file mid-execution
  sailed past. Two guards now apply — defer while any sibling is at `PROC_STAT='6'` (a deliberate
  divergence from legacy R003), and never finalise on `tot == 0`.
  - **Legacy has the identical defect** (`R003` + `R005`'s `TOT` definition + `if (tot == succ)`), so
    this is inherited, not introduced — but it means **any pay-file status written before 2026-07-31 by
    either system may be wrong**. Re-derive from the firm-file rows (query 3.0) rather than trusting
    `TB_PAY_FILE_MST.PROC_STAT` when reconciling a historical batch.
- **KNOWN DIVERGENCE — the `01302` audit row's `FIRM_FILE_NM` differs between the two systems.** Legacy
  stores the callback's `R_` name (`API2_act.jsp:189`); the refactor stores the sent `S_` name
  (`BulkExecutionResultService.writeApiTran`). Not harmful in itself — each system reads its own rows
  consistently — but it silently broke every exact-name diagnostic across the boundary, and it will
  produce a mixed-convention audit table in any shared UAT/PROD DB. Queries here are prefix-stripped;
  **whether to align the writer to legacy is an open decision** (changing it alters audit data shape,
  so it needs a PO call, and the `00302` idempotency guard must keep its exact match either way).

---

## 8. LIVE PROD BACKLOG — 35 stranded files, ~477 payment lines (as at 2026-07-31)

> **This is a backlog to work, not an illustration.** These files exist in PROD right now.

| | |
|---|---|
| **Stranded files** | **35** (`PROC_STAT='6'` and `PROC_STEP <> '3'`) |
| **Payment lines** | **~477** |
| **Date range** | **2025-08-14 → 2026-05-29** (9½ months) |
| **Largest single file** | **20251010 — 412 lines, 5,101,563,524 VND** |
| Confirmed `PROC_STEP='2'` (execution accepted, result never received) | 20260407 (WRB, 2 lines), 20260529 (SHB, 14 lines) |

### These predate the refactor and were produced by legacy

State this plainly in any escalation, because the framing decides who owns it:

- **The refactor has never dispatched a bulk file in PROD.** Every dispatch flag defaults off and
  `TB_FIRM_FILE_*` is empty on the refactor DB. All 35 rows were written by the legacy JEX batch.
- **Legacy had no re-drive path from `PROC_STEP='2'` either.** `FILE_DEPOSIT_TRANSFER_BATCH2` selects
  `(PROC_STEP='1', PROC_STAT='6')` — a file already advanced to `'2'` fell outside its query, so the
  60-second self-heal that covered `'1'` never applied here. The file simply stopped, silently, with no
  alert and no retry. That is why a backlog accumulated over 9½ months rather than being noticed.
- **So this is an inherited condition being surfaced for the first time, not something the refactor
  caused.** What changed on 2026-07-31 is only that it is now *visible*: the new sweep raises it, and
  the finalisation guards stop the parent pay-file from reporting a false "all success" over it.

The corollary matters for triage: **a pay-file's `PROC_STAT` is not trustworthy for these 35.** Some may
already read `'0'` (all success) courtesy of the empty-count defect (§7). Re-derive the true state from
the firm-file rows with query 3.0 before quoting any status to a payment owner.

### Operational action

1. **Confirm with the bank whether these executed.** That is the whole question, and it is unanswered
   for all 35. Work newest-first (most likely to still be reconstructable at the bank) and
   largest-first within a day — the 20251010 file alone is 412 lines / 5.1bn VND.
2. Use the §4.3 script for `PROC_STEP='2'` files and §4.1 / §4.2 for the others, one call per file.
3. Apply §5.4 (step 2) or §5.1–§5.3 per the answer. **Expect reconcile-forward, not re-drive** — for a
   9-month-old batch a re-drive is almost never right regardless of state.
4. Record each answer against `firm_file_nm` per §5.5. Given the volume, keep it in one tracked sheet
   rather than per-incident notes.
5. **Escalate the aggregate to the PO before starting**, not after: ~477 lines of unknown settlement
   state across 9½ months is a reporting-integrity question for the business, not just an ops cleanup.

### Bounding the exposure first (cheapest useful step)

Before contacting the bank, split the 35 by whether the money almost certainly moved:

- `PROC_STEP='2'` → execution **was** accepted; assume executed until the bank says otherwise.
- `PROC_STEP='1'` → execution instruction never confirmed; the file is at the bank but may not have run.
- `PROC_STEP='0'` → reception itself unconfirmed; the file may not even have been accepted.

Query 1 in the operator pack returns `proc_step` and the audit columns for exactly this triage.

---

## 9. Worked examples — the two PROD `PROC_STEP='2'` incidents (2026-07-31)

Two genuinely stranded PROD files, both in the §1.2 state. **Neither was reported by any sweep** — they
were found by hand. They are the reason §1.2 exists.

| | Incident 1 | Incident 2 |
|---|---|---|
| `TRAN_DT` | **20260407** | **20260529** |
| Bank / file type | Woori (`WRB`) | Shinhan (`SHB`) |
| Payment lines | **2** | **14** |
| Firm-file state | `PROC_STEP='2'` / `PROC_STAT='6'` | `PROC_STEP='2'` / `PROC_STAT='6'` |
| `00302` | sent, acknowledged (this is what advanced the step) | sent, acknowledged |
| `01302` | never returned | never returned |
| Detected by the monitor? | **No** — firm-file sweep filters `PROC_STEP='1'` | **No** |
| Reported (via parent pay-file) as | `AWAITING_EXECUTION_RESULT` — *"often not a defect"* | same |

**16 payment lines in total are in an unknown settlement state.** Our database says "processing"; the
bank has almost certainly executed both batches.

### What to do with these two — the operational action

1. **Ask the bank whether those 16 lines executed.** Use the §4.3 script, once per file. This is the
   only question that matters and it has not yet been asked.
2. Then follow §5.4 on the answer. Expect answer **A** or **B**; reconcile forward, do not re-drive.
3. Record the answer against each `firm_file_nm` per §5.5.

### Why they read as benign

`AWAITING_EXECUTION_RESULT` is documented as *"the file is inside the `01301` → `00302` → `01302`
pipeline and the pay-file simply has not been finalised yet … often **not** a defect"*, and it defers
the actionable per-file detail to the firm-file sweep's `BULK_EXECUTION_*` causes. For a `PROC_STEP='2'`
file that deferral points at a report that is never generated. An operator reading the alert as
documented would reasonably conclude nothing needs doing.

### This shape is not rare

JEX **DEV** carries **16** firm files at `PROC_STEP='2'` / `PROC_STAT='6'` (query run 2026-07-31). Twelve
are `REQ_CNT=0` test artifacts with no `00302` audit row, but **four are the same shape as the PROD
incidents** — a `00302` audit row present, no `01302`, real line counts:

| `TRAN_DT` | `FIRM_FILE_NM` | Lines | `00302` | `01302` |
|---|---|---|---|---|
| 20250714 | `S_GV00000001_SHB_003_20250714` | 4 | ✅ | ✗ |
| 20250711 | `S_GV00000001_SHB_105_20250711` | 4 | ✅ | ✗ |
| 20250711 | `S_GV00000001_SHB_106_20250711` | 4 | ✅ | ✗ |
| 20250711 | `S_GV00000001_SHB_107_20250711` | 5 | ✅ | ✗ |

All 16 have a parent pay-file at `PROC_STAT='6'`, so all 16 would today be reported as
`AWAITING_EXECUTION_RESULT`. **Two PROD incidents are not an outlier — it is a recurring shape with no
detection.**

---

## Related

- `StuckPayFileMonitorService` / `StuckPayFileCause` — detection and cause classification.
- `docs/deployment/wecms-batch-activation-runbook.md` — activation order; §5.1 step 8 gates alerting.
- `docs/audits/payment-dispatch-operating-hours-flow.md` — Q-4, the open question this monitor answers
  for the WAIT population.
