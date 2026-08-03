# Bulk-file backlog — reconciliation worksheet

**Created:** 2026-08-01 · **System:** Refactor (`wecms-api`) firm-banking bulk dispatch
**Scope:** the standing backlog of stranded bulk files —
**35 files / ~477 payment lines / 2025-08-14 → 2026-05-29** on PROD as at 2026-07-31.

> **This is an operator worksheet, not a monitoring change.** Nothing in this document alters the
> application. `StuckPayFileMonitorService` keeps its own bounded, age-windowed sweeps; this is a
> one-off pass over a 9½-month backlog that the monitor's 7-day retention floor deliberately excludes.
>
> **Companion documents — read the procedure first, this second:**
> - [bulk-file-reconciliation-procedure.md](bulk-file-reconciliation-procedure.md) — the states (§1),
>   the fact-gathering queries (§3), the VAN Center question (§4), and **the dispositions (§5)**. This
>   worksheet collects evidence; **§5 decides what to do with it.**
> - `Claude Code (AI)/sql/tb_firm_file_mst_stranded_backlog_worksheet_20260801.sql` — the query that
>   populates the sheet.

---

## 1. Before you start

1. **Escalate the aggregate to the PO first, not after.** ~477 payment lines of unknown settlement
   state across 9½ months is a reporting-integrity question for the business, not an ops cleanup.
   Run query **1b** in the SQL script and send the PO those totals before working any row.
2. **Nothing in this worksheet licenses an action.** Every disposition comes from
   [§5 of the procedure](bulk-file-reconciliation-procedure.md). For a batch this old the answer is
   **reconcile-forward, essentially never re-drive** — a re-drive of a 9-month-old file is almost
   always wrong regardless of state.
3. **These files predate the refactor.** All 35 rows were written by the legacy JEX batch; the
   refactor has never dispatched a bulk file in PROD. State that in any escalation, because the
   framing decides who owns it.

---

## 2. Producing the sheet

Run **query 1** of
[`tb_firm_file_mst_stranded_backlog_worksheet_20260801.sql`](../../../Claude%20Code%20%28AI%29/sql/tb_firm_file_mst_stranded_backlog_worksheet_20260801.sql)
read-only against the environment being reconciled, and export it to a spreadsheet.

- **Stranded set:** `PROC_STAT='6' AND PROC_STEP <> '3'`.
- **Order:** `TRAN_DT DESC`, then `REQ_AMT DESC` within the day — **newest first, largest first
  within a day.** Newest is most likely to still be reconstructable at the bank; largest is the
  biggest exposure. Work the sheet top to bottom in that order.
- The query is intentionally **unbounded and has no retention floor.** Do not add one — the floor is
  precisely what hides this backlog.

### Two query properties that must not be "fixed"

Both are load-bearing and both were wrong in an earlier revision:

| Property | Why |
|---|---|
| The audit probes carry **no `tran_dt` predicate** | `TB_FIRM_BAT_API_TRAN.TRAN_DT` is the **call** clock; `TB_FIRM_FILE_MST.TRAN_DT` is the **build** clock. Anything that straddled ICT midnight differs by a day, so a date predicate hides the row you are looking for and turns *"the instruction was issued"* into *"never issued"* — **opposite dispositions**, one of which is "re-drive is routine". |
| The name match **strips the leading 2 characters** | A legacy-written `01302` audit row carries the callback's `R_` name; the file carries the sent `S_` name. An exact-name probe returns 0 for **every** legacy-written file whether or not a callback arrived. That is how an earlier revision reported `audit_01302 = 0` for all 35 files — **an artifact of the query, not evidence.** It also means either name can be pasted into `:firm_file_nm`. |

---

## 3. The re-derive instruction — read before quoting any status

**The parent pay-file's `PROC_STAT` is not trustworthy for this set.** Do not quote it to a payment
owner, and do not use it to triage.

Why: the empty-count defect (§7 of the procedure) let a batch finalise as `PROC_STAT='0'` — *all
success* — over firm files that never settled. Some of these 35 sit under a parent that already reads
"completed". Reading the parent would tell you the batch is done when its settlement state is
unknown.

**Re-derive the true state from the firm-file rows.** The worksheet query does this for you in
`parent_state_rederived`, computed over every sibling sharing `(biz_key, tran_dt, file_seq_no)`:

| `parent_state_rederived` | Meaning | Consequence for this row |
|---|---|---|
| `ALL_SETTLED` | every sibling reached `PROC_STEP='3'` | Should not appear alongside a stranded row — if it does, the sheet and the data disagree; stop and raise it. |
| `PARTIALLY_STRANDED` | some siblings settled, some did not | **Worst case.** Part of the batch may have moved. Treat the batch **as a unit** (§5.6) — never file-by-file — and escalate to the PO. |
| `ALL_STRANDED` | no sibling settled | The whole batch is unresolved; still reconcile forward, still as a unit. |

Branch-split batches (`TB_FIRM_CONF.BAT_TRAN_BRANCH_DIV='Y'`) put several `firm_file_nm` values under
one `file_seq_no`, and **siblings can be in different states.** `total_siblings` /
`stranded_siblings` are on the sheet so a split batch is visible at a glance.

**Record both.** `parent_proc_stat_recorded` is kept on the sheet only so a divergence from
`parent_state_rederived` is auditable — a row where the two disagree is itself a finding for the PO
(a batch reported to the business as complete that is not).

---

## 4. Worksheet columns

Columns 1–14 come straight from query 1. Columns 15–20 are filled in by the operator.

### From the query (do not edit)

| # | Column | Source | Notes |
|---|---|---|---|
| 1 | `tran_dt` | `TB_FIRM_FILE_MST` | File **build** date (ICT). Sort key 1, descending. |
| 2 | `firm_file_nm` | `TB_FIRM_FILE_MST` | The row's identity for every downstream record. Carries its own date. |
| 3 | `biz_key` | `TB_FIRM_FILE_MST` | Tenant. |
| 4 | `file_seq_no` / `file_seq` | `TB_FIRM_FILE_MST` | Batch key / split round. Siblings share `file_seq_no`. |
| 5 | `req_cnt` | `TB_FIRM_FILE_MST` | Payment lines in the file. |
| 6 | `req_amt` | `TB_FIRM_FILE_MST` | Total amount. Sort key 2, descending within a day. |
| 7 | `proc_step` | `TB_FIRM_FILE_MST` | `0` reception unconfirmed · `1` execution unconfirmed · `2` execution accepted, result never received. **The cheapest exposure split.** |
| 8 | `proc_stat` | `TB_FIRM_FILE_MST` | `6` for every row in scope. |
| 9 | `reg_dttm` / `proc_dttm` | `TB_FIRM_FILE_MST` | Build time / last state change. `proc_dttm` is empty on many legacy `'0'`/`'1'` rows — that is expected, not corruption. |
| 10 | `reg_rslt_cd` / `reg_rslt_msg` | `TB_FIRM_FILE_MST` | Whatever the last leg recorded. |
| 11 | `audit_00301` | `TB_FIRM_BAT_API_TRAN` | **On a `'0'` row this is the discriminator.** true ⇒ the reception request was issued, the bank has the file, the `01301` was lost → **never re-drive**. false ⇒ never sent. |
| 12 | `audit_00302` | `TB_FIRM_BAT_API_TRAN` | **On a `'1'` row this is the discriminator.** true ⇒ `UNCONFIRMED` → reconcile. false ⇒ `NOT_ISSUED` → but check `parent_state_rederived` **first**. |
| 13 | `audit_01302` | `TB_FIRM_BAT_API_TRAN` | **On a `'2'` row.** false ⇒ the gateway lost the callback (their side). true ⇒ it arrived and **our** handler kept rolling back — our defect, fixable without calling the bank. |
| 14 | `parent_proc_stat_recorded` · `parent_state_rederived` · `total_siblings` · `stranded_siblings` | derived | **§3. Quote the re-derived column, never the recorded one.** |
| — | `van_org_cd` · `bank_prtn_id` · `out_bank_cd` · `file_type` | `TB_FIRM_FILE_MST` | What the VAN Center needs to look the batch up (§4 of the procedure). On the sheet so step 2 needs no second query. |
| — | `line_rows` · `lines_with_result` | `TB_FIRM_FILE_DATA` | `lines_with_result = 0` means no per-line result was ever written back. |

### Filled in by the operator

| # | Column | Values | Notes |
|---|---|---|---|
| 15 | `asked_on` | date | When the VAN Center question was sent. **Blank = not yet asked.** |
| 16 | `question_used` | `4.1` · `4.2` · `4.3` | §4.1 for `PROC_STEP='1'`, §4.2 for `'0'`, §4.3 for `'2'`. **They are different questions — asking the `'1'` question about a `'0'` file invites a "no" that tells you nothing and tempts a re-drive.** |
| 17 | `bank_answer` | `A` · `B` · `C` · `D` | The §5 answer letter for the state in column 7. Record the letter, not a paraphrase. |
| 18 | `answer_received_on` | date | |
| 19 | `disposition` | `RECONCILE_FORWARD` · `RE_DRIVE` · `ESCALATE_PO` · `NO_ACTION` | From §5.1–§5.6 **for the answer in column 17**. `RE_DRIVE` on this backlog needs an explicit written reason — for a 9-month-old batch it is almost never right. |
| 20 | `done_on` + `who` | date + name | Closure. |

---

## 5. Working a row

1. **Read column 7 (`proc_step`)** — it selects which §5 subsection and which §4 question apply.
2. **Read the matching audit column** (11 / 12 / 13) — it selects the branch inside that subsection.
3. **Read `parent_state_rederived`** — `PARTIALLY_STRANDED` overrides the per-file reading: the batch
   is handled as a unit and goes to the PO. A parent recorded `PROC_STAT='3'` (FAIL) also overrides a
   `*_NOT_ISSUED` reading — phase B stopped on an earlier sibling, so re-driving this one alone
   part-executes a batch already reported as failed.
4. **Ask the bank** the §4 question for the state, once per file. Record columns 15–16.
5. **Apply §5** to the answer. Record columns 17–19.
6. **Record the answer against `firm_file_nm`** per §5.5. Given the volume, keep everything in this
   one sheet rather than per-incident notes.

---

## 6. Known specifics carried from §8

- **The largest file — `20251010`, 412 lines, 5,101,563,524 VND.** It is one of exactly two files in
  the table whose `FINC_MDA_OWNER_NM` carries multibyte characters (411 of 412 rows), and the other
  multibyte file also never reached `PROC_STEP='3'`. Handover §8.4 pitfall ④ states that tones must
  be stripped before dispatch or **the bank rejects the file**, and legacy's `hasVietnameseCharacters`
  gate omits four characters its own `replaceTone` table maps (`Ơ ơ Ư ư`).
  **State this as correlation, not causation** — two files is not a sample and the rejection reason
  was never captured. When asking about `20251010`, add: *"was this file refused on character-set
  grounds?"* One extra question; a confirmation retires the last of C-13.
  It does **not** change the triage, and it has no bearing on refactor exposure
  (`VietnameseText.replaceTone` applies the table unconditionally).
- **The two confirmed `PROC_STEP='2'` incidents** — `20260407` (Woori, 2 lines) and `20260529`
  (Shinhan, 14 lines). Both had the `00302` sent and acknowledged; neither `01302` returned. 16 lines
  in an unknown settlement state; the bank has almost certainly executed both. Worked in §9 of the
  procedure — start there for the shape of a completed row.

---

## 7. What "done" looks like

Every row carries a `bank_answer`, a `disposition` and a `done_on`. The sheet is then the audit
record for a 9½-month reporting-integrity gap, and the residual is whatever the PO decided for the
`PARTIALLY_STRANDED` batches.

Re-run query **1b** at the end: `stranded_files` should be lower by exactly the number of rows
reconciled forward. Any row that is still stranded with a `done_on` filled in is a reconciliation
that did not actually write back — treat it as unfinished.
