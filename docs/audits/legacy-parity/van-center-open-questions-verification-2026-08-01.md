# VAN Center open questions — legacy verification pass (2026-08-01)

> **Read-only with respect to legacy.** Every claim below was re-derived from the legacy source in
> `WECMS_BATCH DEV/source/`, the live JEX DEV frame DB, and the refactor tree. One code change followed
> from it and is recorded in §5.
>
> **Why this document exists.** The table in §1 previously lived only in a working memo, under the
> header *"Needs VAN Center — nothing blocking."* Four of its seven rows did not survive verification
> against legacy source, and two of them were masking genuine vendor questions — so the header was
> wrong as well as the rows. The corrected section is recorded here, in the tree, where the next reader
> will find it.

**Scope:** the seven rows of the former "Needs VAN Center" table, covering the firm-banking bulk
dispatch legs (`00301` / `01301` / `00302` / `01302`) and the fixed-length file.

**Primary sources:**
- `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH2.java`
- `…/web/WEB-INF/action/jex/wecms_batch/api/FILE_DEPOSIT_TRANSFER_API1_act.jsp` (01301)
- `…/web/WEB-INF/action/jex/wecms_batch/api/FILE_DEPOSIT_TRANSFER_API2_act.jsp` (01302)
- `…/src/consts/WCConst.java`
- JEX DEV `tb_batch_schedule_info` / `tb_batch_schedule_dtl` (read-only MCP)
- EFS REST API guide v1.2.6 — as extracted in
  [`efs-guide-1.2.6-open-questions-closure.md`](../governance/efs-guide-1.2.6-open-questions-closure.md)

---

## 1. Needs VAN Center — two open questions, neither blocking SIT

> **Retitled 2026-08-01.** This section was headed *"Needs VAN Center — nothing blocking."* That was
> wrong: the verification pass found that two rows were masking genuine vendor questions. Both are
> recorded in §2. Neither blocks SIT; **§10 q6 blocks UAT/PROD sign-off** via Gate B-2.

| # | Row | Status | Text |
|---|---|---|---|
| 1 | **BT00 / BT01 / C999 / G999** | ✅ **Holds** — evidence sharpened | Legacy defines them by usage. `BATCH2:232` transient = `{BT01, G999, C999}` + empty body (`:202`, continues **before** the audit write, leaving `RS_CD` NULL); `:242` success = `{0000, BT00}`; all else permanent (`:264`, ops alert + `PROC_STAT='3'`). Ours matches all three sets — `isBulkExecutionRetryable:131`, `isBulkExecutionSuccess:106`, `RECEPTION_SUCCESS_CD:228` — mapping legacy's empty body to `null`/blank. **Answered from legacy.** Three nuances in §1.1. |
| 2 | **Callback RC semantics** | ❌ **Premise false — rewritten** | Both systems answer `0000` on every *durably-recorded business outcome* (`G998` sibling fail-stop, non-`BT00` reception, `9999` count/amount mismatch persist to `REG_RSLT_CD`/`RS_CD` while the envelope says `0000`) — verified `API1:195-405` → `:409-423`. **Neither system is uniform beyond that.** Legacy API1 answers `G001/G002/G003/G004/G999` on eight throw paths (`:69,:91,:102,:125,:131,:158,:168,:182`); **legacy API2 answers `G001/G002/G003/G004/G999` on five *non-throwing* early returns** (`:94,:109,:131,:155,:163`) — so *"only a throw changed the RC"* is **API1-only** and false as a general claim. Ours answers `G002/G003/G004/G999` on the equivalent paths via `CallbackAck` + inline branches. **Five code divergences (§3); two notable: 01301 unknown-file (legacy `G004` vs our `G999`) and 01302 unknown-file (legacy `G999` vs our `0000` — ours is more permissive).** Answered from legacy; **§10 q1 still open** on whether any of these codes drive a gateway resend. **The 01302 flip is deferred, with a decision sequence — see §4.** |
| 3 | **Fixed-length file charset** | ❌ **"Moot by construction" is false — rewritten** | `PaymentWireWidthGuard` rejects any char `> 0x7F` after NFC + tone-stripping on the four `WireField` slots (`senderName` 79, `receiverName` 229, both memos 379/589), at Excel-preview **and** save, across all five payment modules; `OverseasSenderNameGuard` covers server-derived `senderName`. **Two live holes:** (1) **`rcptUseAddr` (offset 818)** — tone-stripped and byte-truncated but with **no charset guard on either side**, legacy-faithful by decision; (2) **the ERP import path** — no entry layer exists, so a non-Latin name persists and dispatches ([D-M](../batch/legacy-batch-boundary-rule-sweep-2026-07-31.md), DECIDED 2026-08-01 to keep, matching legacy). On both, `FixedLengthCodec`'s non-ASCII scanner **observes and alerts, never rejects** (`:273-301`, `:193-211`) — one WARN per field, one ops alert per file, file still sent. **`FixedLengthCodec:75-80` already stated hole (1) verbatim** — the memo contradicted its own code. Answered from legacy: legacy sends the same bytes on both paths. The residual K3 question (which codepage the bank accepts) still matters for exactly these two paths. |
| 4 | **Retry** | ❌ **Backwards as written — rewritten** | **Legacy DID poll-retry the 00302 leg.** `BATCH2:232`'s `continue` returns the row to the `PROC_STEP='1'/PROC_STAT='6'` selection and the next 60 s pass re-sends it. **Our no-automatic-retry is an approved TO-BE divergence ([K9](../../architecture/bulk-retry-reassessment.md)), not parity** — `FirmBankingResultCd:113-118` says so outright: *"'Retryable' describes what legacy did with these codes, not what the refactor does."* A transient file rests at `PROC_STEP=1/PROC_STAT=6` for `StuckPayFileMonitorService` (`BULK_EXECUTION_UNCONFIRMED`) and manual recovery instead of a poll. **Separately**, the vendor forbids blind resend — §5.4's 주의 requires a 203 confirmation after a no-response or `G999` and warns of double transfers; zero `재시도`/`재전송`/`retry` in 44 pages — and we honour that (`RealtimeTransportFailureNoResendTest`). **That is compliance with the vendor, not a match with legacy.** The two must not be conflated. |
| 5 | **2-pass URL decode** | ✅ **Holds** — **and CLOSED 2026-08-03, without B5** | Legacy decrypts twice (`SecurityUtil.DecryptAesBase64:263-319`, `VerifyMac:534-568`); we do one self-consistent pass (`EfsCryptoService.decryptEv:110`). **The row's original verdict — "gated on B5 … closure needs a captured sample" — is superseded.** A dedicated legacy read settled it from the source: legacy repairs the Base64 alphabet **before** its decode, we repair **after**, and that ordering is why one pass here covers both input classes legacy needs two for (its pass 2 in fact *corrupts* plain not-URL-encoded Base64 — `URLDecoder` turns `'+'` into a space that commons-codec silently discards). Six input classes traced, all six correct. **Legacy's plaintext validation (`:278-293`) WAS ported** → `EfsCryptoService.assertPlaintextWellFormed`; its defective `isPaddingNum == false` branch and `VerifyMac`'s 4-attempt nesting deliberately were not. `repairBase64Alphabet` unchanged (still explained by the guide's §6.2 URL-*decode*-on-encrypt bug). Full detail: [efs-guide-1.2.6-open-questions-closure.md §5.4 addendum](../governance/efs-guide-1.2.6-open-questions-closure.md). |
| 6 | **Operating hours** | ⚠️ **Two claims, split — rewritten** | **Matches legacy.** All four live `tb_batch_schedule_info` rows are `batch_start_tm='000000'` / `batch_end_tm='235959'` (verified live on JEX DEV, 2026-08-01); `OperatingHoursService` reads INFO under `BATCH_ID='NAPAS_REAL_TIME_BATCH'` for both legs exactly as legacy does (`BATCH1:272`, NAPAS `:113`), and our seed reproduces the two rows the code actually reads. **The INFO window is not the whole gate** — per-channel DTL narrows it (Shinhan CITAD/EBANK `090000-153000`, Woori NAPAS `001500-235500`, Woori CITAD/VCB `083000-174000`, …) and those match legacy too. **Diverges from the vendor.** Guide §3.3 declares online hours `00:15-23:50` in **Webcash Vietnam system time** — the gateway's own clock — so a 24 h INFO window lets dispatch reach the gateway ~25 min/day outside declared service hours, and the clock's zone is unpinned (our gate defaults to ICT, `OperatingHoursService:88`, overridable to `Asia/Seoul`). **"Matches legacy" and "matches the vendor" are different claims here and only the first is true.** Open as **F-7 / Gate B-2** (PO — blocks UAT/PROD) + **§10 q6**. |
| 7 | **File naming** | ✅ **Holds** | `"S_" + vanOrgCd + "_" + fileType + "_" + fileSeq + "_" + date` — legacy `FILE_DEPOSIT_TRANSFER_BATCH1:540` vs `BulkReceptionService:587`, token for token. The 01302 parse (`BulkExecutionResultService:147`, `parts[1]`=vanOrgCd / `parts[3]`=fileSeq / `parts[4]`=tranDt) mirrors legacy `API2:142-151`'s `file_sel_cond`. **Answered from legacy.** |

### 1.1 Row 1 — three evidence nuances

The claim holds; these prevent three specific misreadings.

**a. Two different wire fields, one literal.** `API1:247` tests `result_cd` — the **decrypted
business-payload** field — while `BATCH2:242` tests the **envelope `RC`**. Same `BT00` string, different
fields on different legs. That is why `BT00` appears twice, and why `RECEPTION_SUCCESS_CD` (`:228`,
business field) and `isBulkExecutionSuccess` (`:106`, envelope) are correctly two constructs rather than
a duplication to collapse.

**b. Legacy has no `null`/blank RC branch.** `rtn.getString("RC")` throws on a missing key, and the throw
is caught by the batch-wide `catch(Exception e){ e.printStackTrace(); }` at `BATCH2:323` — **aborting the
whole pass, not just the row.** Our `isBulkExecutionRetryable(null|blank) → true` maps legacy's
*empty-body* branch (`:202`), not a legacy null-RC branch; the mapping is behaviourally faithful but is a
construction, not a quotation.

**c. Persistence divergence on the no-body 00302.** Legacy `continue`s at `:202` **before** the
`TB_FIRM_BAT_API_TRAN_U001` write, leaving `RS_CD` **NULL**. We write `G999`
(`BulkExecutionInlineTxService:196`). Same transient classification, different persisted value —
**anyone reconciling `RS_CD IS NULL` against legacy history will not find our rows.**

---

## 2. The two open vendor questions

### §10 q1 — does a non-`0000` callback `RC` drive a gateway resend?  *(surfaced by row 2)*

The guide neither enumerates the `RC` values a partner may return on a Return-URL callback nor states
what the gateway does with each; §3.8's diagram draws the callback response as a plain return arrow with
no retry branch.

**What it would change.** Whether our **01302 unknown-file `0000`** silently consumes a result legacy
would have had resent. If the gateway resends on non-`0000`, legacy's `G999` was a real recovery
mechanism we have dropped, and §4's sequence applies. If it does not resend, `0000` and `G999` are
behaviourally identical there and the divergence is cosmetic. It also decides whether `CallbackAck`'s
`G999` default retries or terminates for transient WeCMS-side faults.

**Not SIT-blocking** — SIT exercises the success paths.

### §10 q6 — which clock do the `00:15-23:50` hours run in?  *(surfaced by row 6)*

§3.3 says *"웹케시 베트남 시스템 시간 기준"* — the gateway's system time — without naming a zone. The
closed window is 25 minutes; the JEX frame DB runs `Asia/Seoul` while operations are ICT, a 2 h offset
that swallows the window whole.

**What it would change.** Whether the PO's **Gate B-2** decision (leave the 24 h window, or narrow it to
`001500`–`235000`) can be made at all. A narrowed window set in the wrong zone is worse than no window.

**Not SIT-blocking; blocks UAT/PROD sign-off** —
[`wecms-batch-activation-runbook.md`](../../deployment/wecms-batch-activation-runbook.md) §B-2.

---

## 3. Row 2 in detail — the five callback-RC divergences

| # | Condition | Legacy | Ours | Note |
|---|---|---|---|---|
| 1 | **01301 unknown file** | `G004` (`API1:168-170`, throw) | `G999` (`BulkReceptionResultService:581`) | Both non-`0000` and both retryable in intent; the code differs |
| 2 | **01302 unknown file** | `G999` + `"<file_nm> not found."` (`API2:163-169`, early return) | **`0000`** (`BulkExecutionResultService:172`) | **Ours is more permissive** — see §4 |
| 3 | Missing-field code split | API1: `G001` for six of eight detail fields, `G002` for two (`:125-147`); also checks `RQ_DTIME/TNO/EM/VM` presence → `G001` (`:69-79`) | `G002` for all; only `ID` presence is checked | Legacy's own `G001`/`G002` split is internally inconsistent |
| 4 | Unprovisioned VAN key | API1 `G004` (DB error, `:91-96`); API2 `G003` (key lookup sits inside the verify `try`, `:107-120`) | `G999` (`CallbackAck` default) | |
| 5 | Malformed `file_nm` on 01302 | Uncaught `ArrayIndexOutOfBoundsException` at `API2:145` — **outside any `try`** → container 500, no envelope | `G999` (`BulkExecutionResultService:155`) | Ours is strictly better; still a divergence |

**Where parity holds.** On the three business-outcome paths — sibling fail-stop, non-`BT00` reception,
count/amount mismatch — both systems persist the business code (`G998` / `G999` / `9999`) to
`REG_RSLT_CD`/`RS_CD` and answer `RC=0000` on the envelope. That is the D-4 remediation and it is genuine
parity.

---

## 4. The 01302 unknown-file acknowledgement — DEFERRED, with a sequence

**Decision 2026-08-01: do not flip to `G999`. Ask §10 q1 first. Nothing changed on the wire.**

### The code

Legacy, `FILE_DEPOSIT_TRANSFER_API2_act.jsp:163-169`:

```jsp
if (DomainUtil.getResultCount(idoOutFFMR004) < 1) {
    result.put("RC", WCConst.CD_ETC_ERR);          // G999
    result.put("RM", file_nm + " not found.");
    util.setResult(result, "default");
    return;
}
```

Ours, `BulkExecutionResultService` — `ack(envelope, FirmBankingResultCd.SUCC, …)` on the same condition.

### Why a blanket `G999` is wrong, not merely unproven

The `PROC_STEP='2'/PROC_STAT='6'` lookup collapses four conditions into one miss:

| | Condition | Deserves |
|---|---|---|
| (a) | File already at `PROC_STEP='3'` — genuine replay | `0000` |
| (b) | File still at `'0'`/`'1'` — the 00302 was never confirmed | retryable |
| (c) | **No row at all** — name or date mismatch | retryable |
| (d) | File terminal at `PROC_STAT='3'` | retryable |

**(a) is the common case, and a blanket `G999` breaks it.** Once a file reaches `PROC_STEP='3'`, *every*
subsequent replay misses this lookup. Answering `G999` to a correctly-processed replay produces a
**deterministic resend loop with nothing to converge on**. Legacy tolerated exactly that only because its
60-second poller re-selected work each pass; our leg is event-driven, so a loop has nothing to converge
on. Changing a money-path acknowledgement on an unproven premise is also the mistake this project
already declined to make on charset (C-13) and on the 2-pass decode (B5).

**(b) and (d) are already monitored** — `BULK_EXECUTION_UNCONFIRMED` and
`BULK_EXECUTION_RESULT_NOT_RECEIVED` respectively. **(c) was not**, which is what §5 fixed.

### The sequence

1. **Ask §10 q1.** It is the only input that makes this decidable, and it is already drafted.
2. **If the gateway resends on non-`0000`:** do not flip wholesale — **port the 01301 split to 01302**.
   Probe by file without the step/stat predicate, then classify: already-advanced → `0000` (replay, stop
   resending); no row / ambiguous → `G999` + `error` + ops alert; `'0'`/`'1'`/`'3'` → `G999` and let the
   existing monitor causes carry it. That gives legacy's "keep resending the unknown" without legacy's
   loop on the replay.
3. **If the gateway does not resend:** leave `0000` and record the divergence as cosmetic in row 2.

---

## 5. What changed in code (2026-08-01)

**Signal only. The acknowledgement is unchanged at `0000` on every branch.**

Case (c) above — no row at all — left **no state anywhere**: nothing for `StuckPayFileMonitorService` to
sweep, and no audit row is possible (`TB_FIRM_BAT_API_TRAN.BIZ_KEY` is a `NOT NULL` PK column and an
unmatched callback resolves no tenant). Its only trace was a `log.info`. It was the **only
unmatched-callback condition in the dispatch tree with no operator-visible signal** — while its 01301
twin logged at `error` *and* raised an ops alert for the same condition.

| File | Change |
|---|---|
| `BulkExecutionResultService` | On a `'2'`/`'6'` miss, probe for any row matching the file in any state. **No row →** `log.error` + `OpsAlertService.notify` naming `TRAN_DT`, `FIRM_FILE_NM`, envelope `ID`, `TNO` — **never the decrypted payload** (payee names, amounts). **Row exists →** unchanged `log.info`. Ack stays `0000` on both, with the deferral and the loop argument written into the comment so the alert is not later read as a half-applied flip. |
| `TbFirmFileMstRepository` | New `findAnyStateForExecutionResult(tranDt, fileNm, vanOrgCd, fileSeq)` — `findForExecutionResult` minus the state predicate. **Keeps the prefix-insensitive `substring(…, 3)` match**: `FIRM_FILE_NM` stores the sent `S_…` name while the callback carries the bank's `R_…` name, so an exact-name probe returns 0 rows for *every* 01302 callback, replays included, and would fire the alert on the routine path. |
| `BulkExecutionUnmatchedCallbackAlertTest` (new) | Alert fires when no row matches (and carries the four fields, not the payload); does **not** fire on a genuine replay of a `PROC_STEP='3'` file. Both cases assert `rc == "0000"`, so a later half-applied flip fails the build. |

---

## 6. Internal inconsistency — two callback legs, opposite answers

**This is ours, not a legacy divergence.** Recorded here so the next reader sees one decision with two
answers rather than two independent choices.

Both legs face the identical question: *the state-scoped lookup found nothing — is this a replay we
already handled, or a callback we cannot match?*

| | 01301 (`BulkReceptionResultService`) | 01302 (`BulkExecutionResultService`) |
|---|---|---|
| Replay (row advanced) | `0000` | `0000` |
| No row / ambiguous | **`G999`** + `error` + ops alert | **`0000`** + `error` + ops alert *(since 2026-08-01)* |
| Unexpected state | **`G999`** | `0000` |
| Reasoning recorded? | **Yes**, `:596-603` | **No** — `0000` was chosen with no equivalent note |

The 01301 reasoning, verbatim (`BulkReceptionResultService:596-603`):

> The gateway will keep resending and the condition is deterministic … so this is an unbounded retry
> until someone acts. That is deliberate and is the lesser evil: **a retrying callback is recoverable the
> moment the ambiguity is resolved, whereas a wrongly-acked one is gone.**

Applied consistently, that argument points at `G999` on the 01302 leg too. It is **not** applied there,
for the reason in §4: 01302's miss is dominated by the routine replay case, so the same reasoning that
justifies `G999` on 01301 would cost a resend loop on 01302. The two answers are defensible **together**
only as the pair described here — the split in §4 step 2 is what would make them one rule with one
outcome per case. Until §10 q1 lands, the asymmetry stands and is documented rather than silent.

---

## 7. Related documents

| Document | Relationship |
|---|---|
| [`efs-guide-1.2.6-open-questions-closure.md`](../governance/efs-guide-1.2.6-open-questions-closure.md) | Source of §10 q1 / q6, F-7, D-4, B5, K3. This pass corrects nothing in it — it corrects the memo that summarised it. |
| [`legacy-batch-boundary-rule-sweep-2026-07-31.md`](../batch/legacy-batch-boundary-rule-sweep-2026-07-31.md) | **D-M** (ERP charset asymmetry) and **D-L** — the evidence behind row 3 |
| [`../../architecture/bulk-retry-reassessment.md`](../../architecture/bulk-retry-reassessment.md) | K9 — the approved no-retry TO-BE behind row 4 |
| [`../deployment/wecms-batch-activation-runbook.md`](../../deployment/wecms-batch-activation-runbook.md) | Gate **B-2** — the PO decision §10 q6 unblocks |
| [`../deployment/bulk-file-reconciliation-procedure.md`](../../deployment/bulk-file-reconciliation-procedure.md) | The manual procedure the §5 alert feeds into |
| [`wecms-batch-business-parity-audit-2026-07-29.md`](wecms-batch-business-parity-audit-2026-07-29.md) | **M-2** — the earlier, narrower reading of the callback-RC divergence |
