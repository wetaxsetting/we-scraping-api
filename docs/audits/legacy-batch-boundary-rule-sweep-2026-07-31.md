# Legacy batch boundary-rule sweep — 2026-07-31

**Scope.** Every conditional, substring, wrap, clamp and silent skip in the six legacy programs of
the firm-banking dispatch path, each classified as **ported**, **deliberate divergence**, or
**legacy defect — do not port**.

**Programs swept (all six complete):**

| Program | Path |
|---|---|
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/` |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | same |
| `NAPAS_REAL_TIME_BATCH` | same |
| `PROCESSING_RESULT_CHECK_BATCH` | same |
| `WB_00000100` | `.../src/svc/jex/wecms_batch/wecms/` |
| `FILE_DEPOSIT_TRANSFER_API1_act.jsp` / `API2_act.jsp` | `.../web/WEB-INF/action/jex/wecms_batch/api/` |

**Method.** Read against the refactor counterpart, looking specifically for boundary rules the
handover never documented — the class that produced every defect found so far (D-1 (boundary-sweep)
`S_`/`R_` prefix asymmetry, D-3 (boundary-sweep) 99/999 round wrap, D-4 always-0000 callback contract,
D-7 `ANT_DIV_CD` vocabularies).

> **⚠ ID collision — qualify `D-1` and `D-3` whenever they leave this document.** Two independent `D-n`
> series exist and **two IDs collide**:
>
> | ID | **This document** (batch/dispatch defect series) | [`d1-d2-d3-legacy-verification.md`](d1-d2-d3-legacy-verification.md) |
> |---|---|---|
> | `D-1` | `S_`/`R_` `FIRM_FILE_NM` prefix asymmetry | `ResultCheckService` `TRAN_DT` UTC/ICT reader fix |
> | `D-3` | 99 / 999 file-round wrap (reset, not modulo) | Spring Boot Actuator |
> | `D-2` | *(not used here)* | EFS envelope partner ID |
> | `D-4`, `D-7` | always-0000 callback contract · `ANT_DIV_CD` vocabularies | *(not used there)* |
>
> Per-document scoping is the house convention (`H-1` already means three different things in three
> documents) and it holds as long as an ID stays home. It breaks where two series meet in **one**
> document — today that is
> [`../deployment/wecms-batch-activation-runbook.md`](../deployment/wecms-batch-activation-runbook.md),
> which cites `D-1 (d1-d2-d3 record)` at §5.1 step 6 and `D-1 (boundary-sweep)` at §5.1a-1.
> **Convention: write `D-1 (boundary-sweep)` / `D-1 (d1-d2-d3 record)` at every citation outside the
> owning document.** Recorded 2026-08-01.

---

## 1. LEGACY DEFECTS — do not port

Each of these is wrong in legacy. Porting any would import the defect. Recorded here so a future
parity pass does not "restore" them.

### D-A · `NAPAS_REAL_TIME_BATCH` — the per-request delay leaks across rows

`NAPAS_REAL_TIME_BATCH:68` declares `per_request_delay` **outside** the row loop and `:275-278`
assigns it only for Shinhan (`970424`) and Woori (`970457`). There is no `else` resetting it to
`PER_DELAY_DEFAULT`. Once one Woori row sets 3000 ms, **every subsequent row of every other bank
keeps 3000 ms** until another Shinhan/Woori row changes it. The pacing is not per-row-bank after the
first Woori row.

`PROCESSING_RESULT_CHECK_BATCH:82` declares the same variable **inside** the loop, so it resets each
iteration. The two batches disagree; PRCB is the correct one. This confirms the leak is a defect and
not a design.

**Refactor:** `DispatchPacer.pause(outBankCd)` resolves the delay per call from the row's own bank
code. Correct, and a deliberate divergence from `NAPAS_REAL_TIME_BATCH`.

#### DECIDED 2026-08-01 — keep the divergence. The "30× faster" concern is withdrawn; a different lever was applied instead.

**The leak's population is empty.** It can only mis-pace a bank outside `{970424 Shinhan, 970457 Woori}`
— both of those set `per_request_delay` explicitly on their own rows (`:275-278`), and `bank_cd` there is
`OUT_BANK_CD` (`:234`), the withdrawal bank. No such bank has ever been on the paced rail. Measured
read-only on **JEX DEV**, 2026-08-01:

| Query | Result |
|---|---|
| `TB_FIRM_TRAN` by `TRAN_TP`, `OUT_BANK_CD` | `202`: `970424` × 46 (20241220–20260421), `970457` × 6 (20251223–20260629). **No third bank, ever.** |
| `TB_PAY_TARGET` by `OUT_BANK_CD` | `970424` × 83, `970457` × 14, `970436` × 4 |
| the four VCB `970436` rows | all `20260617`, all `PROC_STAT='3'` FAIL, **zero `202` firm-trans** — they never dispatched |

VCB has since been routed off the API rail to file-based entirely for both VND and USD (PO-approved
2026-07-16, memory `project_vcb_file_transfer_switch`). So there is no bank receiving 3000 ms today that
would drop to 100 ms under the per-row fix. Shinhan gets 100 ms in both systems. **An earlier reading of
this — recorded in `StrandedRecoveryService`'s Javadoc as "up to 30× faster than the rate they actually
receive today" — is withdrawn; it had no subject.**

**Reverting is feasible but buys nothing, and is worse than legacy in two specific ways.** Cost, stated
so it is not re-litigated:

1. **It poisons a rail legacy paces correctly.** The refactor has **one** `DispatchPacer` shared by two
   rails — `ResultCheckService:473` (the 203 scan) and `RealtimeDispatchQueue:168` (the 202 FIFO). Legacy's
   own 203 sibling, `PROCESSING_RESULT_CHECK_BATCH:82-89`, declares the delay **inside** its loop. Making
   the shared pacer leak would import the NAPAS defect onto the 203 rail, where legacy does not have it.
   A faithful revert needs two pacers with opposite semantics.
2. **It is unbounded on the FIFO.** Legacy's `per_request_delay` is a method-local re-initialised on every
   batch invocation (`:68`), so the leak dies at the end of each poll. The refactor's 202 rail is a
   **long-lived single FIFO worker** (C-6) draining continuously across unrelated payments. There is no
   pass boundary to reset against, so a leaked 3000 ms would persist indefinitely.

**And legacy's spacing was never a guarantee to revert *to*.** It is order-dependent on
`TB_PAY_TARGET_R001`, which has **no `ORDER BY`** (B-1), so identical workloads can space at 100 ms or
3000 ms on consecutive days depending on how rows happen to sort.

**The lever actually applied.** The real residual is not the leak, it is the *unknown-bank default*:
legacy's 100 ms is the initialiser of a variable its loop overwrites for the only two banks it knew about
— it is not a considered value, and it has never been sent to a third bank. So
**`DispatchPacer.PER_DELAY_DEFAULT` was raised 100 → 3000 ms** (2026-08-01). That is deterministic,
applies only to banks legacy never had a considered value for, leaves Shinhan and Woori at their verified
legacy values, and is `>=` legacy's effective spacing for **every** row rather than only when a Woori row
sorted ahead. Reasoning is documented on the constant itself. Lower it only with a measured throughput
answer from the VAN Center for the specific bank.

### D-B · `PROCESSING_RESULT_CHECK_BATCH` — the finalize key comes from the gateway response

`PROCESSING_RESULT_CHECK_BATCH:244` reads `org_proc_dt = jsonEV.getString("org_tran_dt")` — the
date **echoed by the bank** — and then keys both terminal updates on it (`TB_FIRM_TRAN_U004` at
`:302`, `TB_PAY_TARGET_U001` at `:319`, both `TRAN_DT = org_proc_dt`).

If the bank echoes any other date than the one WeCMS stored, both UPDATEs match **zero rows** and the
transfer stays at `PROC_STAT='6'` permanently. `DomainUtil.isError` catches DB errors, not zero-row
updates, so this fails **silently** — the batch logs success. This is the highest-severity silent
skip in the swept set: the outcome of a payment already sent to the bank is discarded on a field the
bank controls.

**Refactor:** `ResultCheckService.reflectOutcome` passes `row.getTranDt()` / `row.getTranSeq()` —
the **local** row's key — into `DispatchResultTxService.finalizeOriginalAndPayTarget`. The response
date is never used as a key. Deliberate divergence, previously unrecorded; recorded here.

### D-C · `PROCESSING_RESULT_CHECK_BATCH` — one bad row aborts the whole batch

The outer `catch` at `:395-406` **rethrows** (`throw new Exception(e.getMessage())`). Every other
swept program either swallows (`NAPAS_REAL_TIME_BATCH`, `WB_00000100`) or `continue`s to the next
row (`BATCH1`, `BATCH2`). So in PRCB a single row that throws — e.g. the `RC=0001` decrypt below —
stops every remaining row in the pass.

**Refactor:** `ResultCheckService.runOnce` catches per row, leaves the row at `'6'`, paces, and
continues. Ported from the intent, not the code.

### D-D · `PROCESSING_RESULT_CHECK_BATCH` — `RC=0001` still decrypts an EV that may not exist

`:238` admits both `0000` and `0001` (`CD_NO_FOUND`) into the else-branch, which unconditionally
decrypts `rEV` at `:240`. On a `0001` "original transaction not found" the gateway has no business
payload to return; the decrypt throws, and via D-C it takes the whole batch down. The `0001` handling
at `:249-253` — which sets `org_resp_cd` without reading EV — is written as if EV were absent, so the
code contradicts itself.

**Refactor:** `ResultCheckService:216-220` returns `ResultCheckInquiryResult.notFound(...)` on `0001`
**before** any decrypt. Already correct.

### D-E · `PROCESSING_RESULT_CHECK_BATCH` — no MAC verification on the 203 response

`API1:102`, `API2:109`, `NAPAS:369` (the 204 leg) and `WB_00000100:152` all call
`SecurityUtil.VerifyMac` before decrypting. PRCB does not — `:240` decrypts straight from `rEV`.
A tampered or corrupt envelope is parsed and its `org_resp_cd` is written to `TB_FIRM_TRAN` and
`TB_PAY_TARGET` as a payment outcome.

**Refactor:** `ResultCheckService` verifies `VV` and surfaces `G003` as a gateway error so the row
stays `'6'` and is retried. Deliberate divergence (A3 / H-5), already implemented.

#### GATED 2026-08-01 — an absent `VV` is accepted with a WARN; only a *present but wrong* `VV` throws

This is the highest-blast-radius unproven behaviour in the swept set, and the gate exists because of an
asymmetry the entry above states but does not weigh: **PRCB is the one leg legacy does not verify.** Two
readings fit that fact — the PRCB author forgot, or `WGVN_EFS_203` **emits no `VV` to verify**. The
sweep assumed the first. Nobody has checked, and **the refactor's 203 leg has never run against the live
gateway** (the SIT live run was EFS + `201`, 17/17 `RS_CD=1000`).

If the second reading is right, a strict check fails **100% of rows**: every result-check inquiry throws
`WBE_EFS_GATEWAY_ERROR`, every row stays at `PROC_STAT='6'`, and the entire result-check rail strands on
first run — a worse outcome than legacy's, which at least completes. That is the one place in these 19
where our direction is plausibly riskier than legacy's.

Reverting would mean accepting a tampered envelope's `org_resp_cd` as a payment outcome into
`TB_FIRM_TRAN` and `TB_PAY_TARGET`, with no detection. So the gate is narrower than a revert:

- `VV` **absent** (null/blank) → accept, log a WARN naming the condition. A missing field cannot strand
  the rail.
- `VV` **present but not matching** → unchanged: throw `G003`, row stays `'6'`. Tampering still cannot
  slip through.

**Pre-condition, not a permanent state:** do not enable result-check in UAT until one SIT `203`
round-trip records whether `VV` is present. Runbook §5.1a-2 owns the answer; remove the absent-`VV`
branch once it is `yes`.

### D-F · `FILE_DEPOSIT_TRANSFER_API2` — short-read record misframing

`API2:263-279`: `byte[] buf = new byte[1000]; while ((sz = fis.read(buf)) > 0) { String tmp = new
String(buf); ... }`. `sz` is read and **never used**, and `new String(buf)` re-decodes the whole
buffer including stale bytes from the previous iteration. A short read — entirely legal on a stream —
mis-frames every subsequent record.

The charset half of this line (`new String(buf)` with no charset) stays **blocked** pending the
`.xlsx` from Mr. Kang. The short-read half is independent of the charset question and must not be
ported either way.

### D-G · `FILE_DEPOSIT_TRANSFER_API2` — never commits on the success path

`idoCon.beginTransaction()` at `:291`; every error branch does `rollback()` + `endTransaction()`; the
success path falls through `:447` to `result.put("RC","0000")` with **no `commit()` and no
`endTransaction()`**. It relies on implicit connection-close behaviour.

**Refactor:** Spring `@Transactional`. Not portable and must not be ported.

### D-H · `WB_00000100` and `PROCESSING_RESULT_CHECK_BATCH` — commit after rollback

`WB_00000100` (three blocks) and `PROCESSING_RESULT_CHECK_BATCH:298-336` both do
`if (isError) { rollback(); endTransaction(); }` and then **unconditionally** `commit();
endTransaction();`. Do not port.

### D-I · `WB_00000100` — `ant_div_cd` read from the envelope, persisted from the EV

`WB_00000100` sets `ant_div_cd = rtn_201.getString("ant_div_cd")` — the **outer envelope**, which
carries `RC/RM/EV/VV` only — while the `TB_FIRM_TRAN_U002` write uses
`rs_EV_201.getString("ant_div_cd")`, the **decrypted EV**. The value returned to the caller is
therefore not the value persisted. Read before the `RC` check, so it also runs on failures.

Given D-7 (the four `ANT_DIV_CD` rows that failed or stranded — see memory
`project_batch_schedule_tz_antdiv_fixes`), this is the provenance seam most likely to have produced
them.

> **D-7 / B-2 characterisation corrected 2026-08-03. Supersedes the 2026-08-01 note.**
>
> **1 — `TB_PAY_TARGET.ANT_DIV_CD` is not "mixed-width drift".** It holds
> `wabooks.enums.AntDivCd.getValue()`, applied consistently. That enum is mixed-width **by design** —
> `INTER("INTER")`, `NAPAS("NAPAS")`, `CITAD("CITAD")` sit in the same enum body as `SWIFT("S")`,
> `EBANK("E")`, `VCB("V")`. **All 101 JEX DEV rows conform to the enum.** The column is `bpchar(5)`
> because `INTER`/`NAPAS`/`CITAD` need 5 bytes, not because every value is 5 chars. `'E'` and `'S'`
> are the **correct** values for EBANK and SWIFT — not anomalies, not drift. The correct model is
> **three columns, three vocabularies, all correct in place**:
>
> | Column | Width | Vocabulary | Values |
> |---|---|---|---|
> | `TB_PAY_TARGET.ANT_DIV_CD` | `bpchar(5)` | `AntDivCd.getValue()` | `INTER` · `NAPAS` · `CITAD` · `E` · `S` · `V` |
> | `TB_PAY_FILE_DATA.ANT_DIV_CD` | `bpchar(1)` | `AntDivCdT.getValue()` | `E` · `N` · `C` · `S` · `V` — the 1-byte wire slot at offset 62 of the D record |
> | `TB_BATCH_SCHEDULE_DTL.ANT_DIV_CD` | `bpchar(5)` | channel **names** | `EBANK` · `NAPAS` · `CITAD` · `SWIFT` · `VCB` |
>
> `TB_BATCH_SCHEDULE_DTL` holds enum *names*, matching neither enum's `getValue()`. Its DEV rows carry
> `reg_dttm='20251113095800'` — a legacy DBA INSERT, not our seed.
>
> **2 — legacy performs the `E → EBANK` expansion itself.** `FILE_DEPOSIT_TRANSFER_BATCH1:301-381`
> reads 1-char counts out of `TB_PAY_FILE_DATA` (via `R005`) and then calls
> `operTimeChk(bankCd, "EBANK"/"VCB"/"CITAD"/"NAPAS"/"SWIFT")` with the **hard-coded 5-char name**.
> So `AntDivChannel.fromStorageCode` normalises in the same direction legacy does, at the same point
> in the same flow. `canonical` normalises the same direction but on the **realtime** path, where
> legacy (`NAPAS:144`) passes the value verbatim — that one is a real divergence, see B-2.
>
> **3 — evidence is JEX DEV, not PROD** (unchanged from the 2026-08-01 note). Measured read-only via
> `postgres_dev_server_jexframe`:
>
> ```
> ant_div_cd='E    ', proc_stat='3'  → 2 rows, 20250918 (tran_seq 13219/13220)  FAIL
> ant_div_cd='S    ', proc_stat='9'  → 2 rows, 20250919 (tran_seq 13221/13222)  stranded in WAIT
> ```
>
> **PROD incidence is unmeasured** (`.claude/rules/database.md` forbids PROD access). Do not cite
> these four rows as PROD failures — **and do not cite them as fixed.** Canonicalisation does not
> rescue them: there is no `NAPAS_REAL_TIME_BATCH/970424/EBANK` (or `/SWIFT`) DTL row, so `'E'` and
> `'EBANK'` both miss and both return `NO_CRITERIA`. EBANK and SWIFT are **file-path** channels, which
> is why this D-I provenance seam remains the leading explanation for how they reached `TB_PAY_TARGET`
> at all.

**Refactor: clean, verified 2026-07-31.** `RecipientLookupService:123` returns
`parsed.antDivCd()` where `parsed` is the deserialized **decrypted** EV (`decryptEv` at `:118`), and
`verifyVv` runs first at `:112`. `RealtimeDispatchService:465` writes `result.antDivCd()` from the
same decrypted-response object. `DepositorInquiryService:118` treats it as log-only. There is no
envelope read anywhere. Nothing to fix.

### D-J · `BATCH2` — the audit row is abandoned on an empty response

> **Rewritten 2026-08-01 — the previous version got the `PROC_STEP` wrong and attached the wrong
> evidence.** It read: *"the audit row … keeps `RS_CD = NULL` forever and the firm file stays
> `PROC_STEP='2'`. This is precisely the population `BULK_EXECUTION_RESULT_NOT_RECEIVED` was built to
> detect — 35 PROD files, 477 lines, nine months."* Both sentences are withdrawn. Corrections below.

`FILE_DEPOSIT_TRANSFER_BATCH2:202-203` does `if (respData == null || respData.length() == 0) { continue; }`
**before** the `TB_FIRM_BAT_API_TRAN_U001` write at `:211`, so the audit row inserted at `:163-176` keeps
`RS_CD = NULL`. That much is correct and unchanged.

**The file stays at `PROC_STEP='1'`, not `'2'`.** BATCH2 selects
`PROC_STEP = PROC_STP_REQ_RECV = '1'` (`:51`, `WCConst:35`) and writes `'2'` **only after parsing a
response** (`:248`, `:288`). The `continue` skips both the audit update *and* the step advance, so the
row is left exactly where it was picked up. That makes this
**`BULK_EXECUTION_UNCONFIRMED`** — a `00302` audit row present, no success code — and **not**
`BULK_EXECUTION_RESULT_NOT_RECEIVED`. The two causes have **opposite dispositions** in the operator
procedure (§5.2 "re-drive is usually *wrong*" vs §5.4 "**never** re-drive"), so the distinction is not
pedantry.

**Legacy retried it, same-day.** BATCH2 re-picks `PROC_STEP='1'/PROC_STAT='6'` on its next 60-second
tick — the self-heal described in [`../deployment/bulk-file-reconciliation-procedure.md`](../deployment/bulk-file-reconciliation-procedure.md) §0. The retry is
bounded by `TRAN_DT = dt` (today) in the `R001` selection at `:48-51`, so the strand becomes permanent
only across ICT midnight. Legacy's blast radius here is one poll interval, not nine months.

**Never observed. Moved to "no evidence either way".** JEX DEV, read-only 2026-08-01:

```
api_tran_tp='00302':  33 rows,  rs_cd IS NULL → 0
```

The six DEV files at `PROC_STEP='1'/'6'` (all `20250210`) carry **no `00302` audit row at all** — they
never reached the `C001` insert, so they are `NOT_ISSUED`, a different shape again. The four genuine
`PROC_STEP='2'/'6'` files (`20250711` ×3, `20250714`) each have a `00302` present **with a non-null
`RS_CD`** and no `01302` — lost result callback, not this. Falsifiable on PROD with one query nobody has
run: `SELECT count(*) FROM tb_firm_bat_api_tran WHERE api_tran_tp='00302' AND rs_cd IS NULL;`

**The 35-file backlog is not D-J's evidence — see D-J-bis.** It is re-attributed below.

**Verdict: keep the divergence.** Writing the audit row unconditionally costs nothing and loses nothing.
But it is a latent-defect closure, not a fix for an observed failure, and this entry must not be cited as
the latter.

### D-J-bis · `BATCH2` has no retry from `PROC_STEP='2'`, and legacy has no detection — *this* is what the backlog evidences

Re-attributed here 2026-08-01, out of D-J.

`FILE_DEPOSIT_TRANSFER_BATCH2:48-51` selects `PROC_STEP='1'` **and** `TRAN_DT = today`. A file that has
advanced to `PROC_STEP='2'` — execution accepted by the gateway, `01302` result never returned — falls
outside that query entirely. So the 60-second self-heal that covers `'1'` **never applied to `'2'`**, and
nothing else in legacy looked at it: no sweep, no alert, no retry. The file simply stopped.

**Production evidence — 35 stranded files, ~477 payment lines, 2025-08-14 → 2026-05-29 (9½ months).**
Detail and the operator worksheet: [`../deployment/bulk-file-reconciliation-procedure.md`](../deployment/bulk-file-reconciliation-procedure.md) §8. All 35 were
written by the legacy JEX batch — the refactor has never dispatched a bulk file in PROD (every dispatch
flag defaults off, `TB_FIRM_FILE_*` empty on the refactor DB). DEV corroborates the shape: 16 files at
`'2'/'6'` and 30 at `'0'/'6'` (verified read-only 2026-08-01).

**What the refactor does instead:** `StuckPayFileMonitorService` raises
`BULK_EXECUTION_RESULT_NOT_RECEIVED` as its own cause (the firm-file sweep queries `'1'` **and** `'2'`,
and is bounded on `PROC_DTTM` rather than today), and the B-9 finalisation guards stop the parent pay-file
reporting a false ALL-SUCCESS over such a file. **No retry was added — K9 stands**, deliberately: a
duplicate `00302` risks double-executing a bulk payment, which is worse than a stranded one. Detection
plus the reconciliation procedure is the whole mitigation.

### D-K · Alert bodies that cannot say what failed

Three separate instances, all silent-skip shaped:

1. **`API1` / `API2` `finally` blocks** gate the alert on `!"".equals(vanOrgCd) && !"".equals(CRYPT_KEY)`.
   The VAN-key lookup runs *first*, so a failure there — unknown VAN, missing `API_KEY`, the B2
   provisioning gap — leaves both empty and the callback fails **with no alert at all**. See §3 S-8.
2. **`WB_00000100`** reads `result.getString("RC")`/`("RM")` for the alert body before those are
   populated (they are set at the end, from `WB_COMMON`), and reads `input.getString("ID")`/`("TNO")`
   when ID/TNO live on `WB_COMMON` — all four fields print blank.
3. **`NAPAS_REAL_TIME_BATCH`** labels a field `TRAN_SEQ` and prints `idoOutPTR001.get("TRAN_DT")`.

### D-L · The `hasVietnameseCharacters` gate misses four characters its own tone table maps

`StrUtil.hasVietnameseCharacters` ([`StringUtils.java:52-54`](../../../WECMS%20DEV/WECMS/src/wabooks/commons/StringUtils.java#L52-L54)) admits three
ranges — `U+00C0-U+00FF`, `U+0100-U+017F`, `U+1EA0-U+1EF9`. The `replaceTone` table
([`:73`](../../../WECMS%20DEV/WECMS/src/wabooks/commons/StringUtils.java#L73)) contains `ƠơƯư` = **U+01A0, U+01A1, U+01AF, U+01B0**, all four of which fall
between `U+017F` and `U+1EA0` — **outside every gate range**. The gate is evaluated per *string*, so a
value whose only non-ASCII characters are those four returns `false`, `replaceTone` is never called,
and the raw bytes go through unstripped.

Both strip sites in `FILE_DEPOSIT_TRANSFER_BATCH1` are affected identically — the DB row
([`:1075-1077`](../../../WECMS_BATCH%20DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH1.java#L1075-L1077)) and the wire bytes ([`:647-650`](../../../WECMS_BATCH%20DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH1.java#L647-L650)) — so the two stay consistent
with each other while both bypass the strip. Common Vietnamese given names sit entirely inside the
gap: **Sơn, Tư, Vương, Hương**.

**This is a defect, not a tolerance.** The vendor states the requirement and the consequence —
handover §8.4 pitfall ④ ([`08_기능추출_이관가이드.md:64`](../../../WECMS_BATCH%20DEV/handover/08_기능추출_이관가이드.md#L64)):

> **베트남어 성조 제거**: 송금/수취인명·적요·주소는 펌뱅킹/이체 전 성조 제거. **미적용 시 은행 측 거절.**
>
> *"Vietnamese tone removal: sender/receiver name, remarks and address must have tones removed before
> firm-banking/transfer. **If not applied, the bank rejects it.**"*

Legacy's gate therefore produces files the vendor says will be refused.

> **Production instances: zero. Corrected 2026-08-01 — do not cite the two multibyte files as evidence.**
>
> This section previously read: *"The production record agrees: exactly two files ever carried multibyte
> receiver names, and neither reached `PROC_STEP='3'`."* That sentence is **withdrawn**. Both files
> (`S_GV00000006_SHB_001_20251010` and `..._002_20251010`, 411 multibyte rows of 412 each) are dated
> **20251010**, nineteen days before the tone-strip block entered `FILE_DEPOSIT_TRANSFER_BATCH1` —
> `IDO.TB_FIRM_FILE_DATA_C001` version **`20251029205935`**. They predate tone stripping *entirely*, so
> they cannot be instances of this gate leaking: at the time they were written there was no gate and no
> strip. They are also a resubmission pair, not a split — same `BIZ_KEY`, same `ORG_SEQ` range 1–412,
> same totals, four hours apart, but different `FILE_SEQ_NO` (80 / 81) and **different payload
> fingerprints**, i.e. the payload was edited between attempts, which makes the first rejection a data
> rejection rather than a charset one. Detail:
> [`../deployment/bulk-file-reconciliation-procedure.md`](../deployment/bulk-file-reconciliation-procedure.md) §"The largest file is NOT a charset case".
>
> **D-L keeps its code basis and loses its production evidence.** The gate really does exclude
> `U+01A0-U+01B0` while its own table maps those four characters, and pitfall ④ really does state the
> consequence — that is enough to justify the refactor's correction below on its own. What no longer
> holds is any claim that the defect has been *observed*: after the 20251029 IMP, **no dispatched file
> has ever carried an unstripped name**, and the gate hole has zero known production instances. Treat
> D-L as a latent defect closed pre-emptively, and do not reach for these two files the next time
> evidence is wanted.

**Refactor: corrected deliberately.** [`VietnameseText.replaceTone`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/file/VietnameseText.java) applies the table
**unconditionally — no gate** — which is what pitfall ④ requires and what legacy's own table already
encoded. Called at [`BulkReceptionService:646-649/662`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L646-L649) (DB row) and
[`:678-687`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L678-L687) (wire).

> **Analogy to D-7 / B-2 withdrawn 2026-08-03.** This previously read *"Same shape as the D-7 / B-2
> `ANT_DIV_CD` canonicalisation: legacy holds two mutually inconsistent encodings of one rule."* That
> is not what `ANT_DIV_CD` is. Legacy is **not** inconsistent there — three columns each apply their
> own coherent vocabulary (see the D-7 box above), and legacy performs the `E → EBANK` expansion itself
> on the bulk path. D-L is a genuine legacy self-contradiction; `ANT_DIV_CD` is not. Do not use one to
> justify the other. The D-L divergence stands on its own basis below.

**Consequence to accept, not to reverse:** for a name like `Sơn`, legacy stores and sends `Sơn`; the
refactor stores and sends `Son`. The accented form is lost from `TB_FIRM_FILE_DATA.FINC_MDA_OWNER_NM`
relative to legacy. That is the correct trade — the column is a record of what was *dispatched*, and
what legacy dispatched was refused. Do not restore the gate to preserve the diacritic.

> **Superseded concern.** An earlier reading of this recorded `PaymentWireWidthGuard`'s non-Latin
> rejection as "stricter than legacy, therefore a refactor regression". That is **withdrawn**. The guard
> is stricter than legacy *in the direction pitfall ④ mandates* — which stands on the vendor statement
> alone, not on the two multibyte files (see the correction above; they predate tone stripping and are
> not evidence of anything). The genuinely harmful strictness in that guard was NBSP — an invisible
> character legacy silently folds to a space via `StringUtils.sanitize` — and that is fixed
> ([`LegacyTextNormalizer.sanitizeSpaces`](../../src/main/java/com/kosign/wecms/domain/payment/common/LegacyTextNormalizer.java)), not outstanding.

---

### D-M · Charset validation is an **entry-layer** guard, so any path without an entry layer bypasses it

**DECIDED 2026-08-01 — keep as-is, matching legacy. Read the framing before proposing a fix.**

> **If you arrived here from "the ERP import path is unguarded", that framing is wrong and will lead you
> to the wrong change.** [`PaymentWireWidthGuard`](../../src/main/java/com/kosign/wecms/domain/payment/common/PaymentWireWidthGuard.java) is an **entry-layer** guard — an Excel-upload
> preview check plus a save-path assertion. It is **not called on the dispatch path for any module**:
> not for vendor, expense, payroll, internal transfer or overseas. There is no per-module gap. The ERP
> importer differs in exactly one structural respect — it has **no entry layer at all** (no parser, no
> save service, no user session) — so it is the one writer that reaches `TB_PAY_FILE_DATA` without ever
> crossing the layer the guard lives in. **ERP is not special; it is the only path that skips the layer.**

#### What each stage actually checks

| Stage | Check | Excel / API entry | ERP import |
|---|---|---|---|
| Entry | `PaymentWireWidthGuard` — non-Latin **and** wire width | ✅ preview + save | ❌ *(no entry layer exists)* |
| Entry | `PaymentBlockedCharGuard.assertClean` — `BLOCKED_CHARS_RULE` | ✅ | ❌ |
| Import | `\p{Zs}` fold + `VietnameseText.replaceTone` on names | n/a | ✅ |
| Dispatch (phase A) | `VietnameseText.replaceTone`, unconditional, all five slots | ✅ | ✅ |
| Dispatch (phase A) | `PaymentBlockedCharGuard.assertCleanForDispatch` | ✅ | ✅ |
| Encode | `FixedLengthCodec` over-width → throws (refuses, never truncates) | ✅ | ✅ |
| Encode | C-13 non-ASCII scan → collect + one ops alert per file | ✅ | ✅ |

So an ERP row is **not** unguarded: it gets tone-stripping and the blocked-character rule at dispatch, and
both codec backstops. What it never gets is the **non-Latin rejection** and the entry-time width check.

#### The observable consequence

`Müller` (U+00FC) is not in the 134-entry tone table, so it survives `replaceTone` on every path.

- **Excel / API entry:** rejected with `WBE_PAYMENT_WIRE_FIELD_NON_LATIN`, at preview and again at save.
  The operator fixes it before anything persists.
- **ERP import:** accepted silently, persisted, dispatched. The C-13 scanner raises **one ops alert per
  file** *after* the instruction is on the wire ([`BulkReceptionService:764-781`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L764-L781)) — whose body already
  names "an ERP passthrough row" as an expected source.

Same value, two outcomes, decided by entry path.

#### Legacy does the same thing, and less

`ErpDataRefreshHelper` runs `sanitize` → `hasVietnameseCharacters` gate → `replaceTone`, and stops. No
charset check, no width check. `ü` sits inside the gate's `U+00C0-U+00FF` range, so the gate *fires*,
`replaceTone` runs, `ü` is not in the table, and it passes through — **legacy sends `Müller` raw on this
path too.** Legacy's blocked-character equivalent, `StringUtils.removeSpecialCharacters` (`:90`), is
**commented out at all eight ERP call sites** — `ErpDataRefreshHelper:520, 554, 643, 684` and
`ERP_0100_002:389, 429, 489, 526`, each citing `redmine/issues/42761` — and has no live caller on that
path. The refactor is therefore **equal to legacy on charset** and **stricter on blocked characters**,
because our dispatch guard runs where legacy's is disabled.

#### Options considered

| | Option | Why not / why |
|---|---|---|
| 1 | Extend `PaymentWireWidthGuard` to the ERP importer | **Rejected.** There is no user to correct a rejected ERP row — the import is a scheduled pull from the customer's Oracle, so a rejection strands the payment with nobody in session to fix it. It would also block a payment on an **unproven premise**: the bank's accepted charset on this channel is still unconfirmed (K3 / C-13), which is the same reason the codec *collects* non-ASCII rather than refusing it. And it diverges from legacy, which sends the value. |
| 2 | **Keep the behaviour, record the divergence** | **TAKEN.** Matches legacy on the one axis where legacy has a defined behaviour, keeps the refactor's genuine improvement (the blocked-char guard) and keeps the C-13 alert as the detection. Costs an explicit asymmetry, which this entry is. |
| 3 | Move the charset check from entry to dispatch, for **all** modules | **Deferred, not rejected.** This is the only option that removes the asymmetry rather than documenting it, and it is the structurally correct end state. It is a **wire-charset decision, not an ERP one**, and it needs the K3 answer first (`글로벌 VAN_펌뱅킹_배치파일 레이아웃.xlsx`, still not in the handover folder). Revisit when that lands or when the bank confirms the accepted charset. |

**Do not implement option 1.** If the asymmetry becomes intolerable before K3 is answered, the next step
is option 3 with a PO decision on what a dispatch-time rejection should do to an unattended row — not a
guard bolted onto one importer.

### D-N · `API2` — `G999` to a completed-file **replay**, with no poller to converge on

*(Recorded 2026-08-03. This is a legacy behaviour we deliberately decline, not a legacy bug.)*

**Legacy behaviour, verified not inferred.**
[`IDO.TB_FIRM_FILE_MST_R006.xml`](../../../WECMS_BATCH%20DEV/source/WECMS_BATCH/WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_FIRM_FILE_MST_R006.xml)
constrains state, not just identity:

```sql
WHERE TRAN_DT = :TRAN_DT
AND PROC_STEP = :PROC_STEP
AND PROC_STAT = :PROC_STAT
AND VAN_ORG_CD = :VAN_ORG_CD
AND FILE_SEQ = :FILE_SEQ
AND substring(FIRM_FILE_NM,3) = substring(:FIRM_FILE_NM,3)
```

and `API2_act.jsp:144-146` binds `PROC_STEP="2"` / `PROC_STAT="6"` as **literals**. So a file already
advanced to `PROC_STEP='3'` is not hidden from legacy by any quirk — legacy runs the identical filtered
lookup, gets `getResultCount < 1`, and answers **`G999` + `"<file_nm> not found."`** (`:163-169`).
**Legacy answers `G999` to a completed-file replay exactly as it does to a genuinely unknown file.**

**Why we decline it — the poller, not the lookup.** Legacy could absorb that resend loop because
`FILE_DEPOSIT_TRANSFER_BATCH2` re-selected work every 60 s: a callback wedged in `G999` was never the
only route by which a file moved forward. **We removed that poller (K9.)** Copying `G999` would give us
the loop with nothing to converge on — the gateway resends indefinitely against a file that is already
correctly processed, and no other actor advances it. Same class as the transient-00302 retry already
declined.

**What we do instead:** `RC=0000` on both replay branches, with the escalation signal carried by log +
ops alert rather than the wire. `BulkExecutionResultService` (the `PROC_STEP='2'`/`PROC_STAT='6'` miss
branch).

> **Correction.** The in-code comment previously argued this from the *lookup* — that legacy never sees
> a step-3 row. That was wrong: it does see it, via `getResultCount < 1`, and answers `G999`. The
> poller is the argument. Comment rewritten 2026-08-03.

**Related, and deliberately *not* changed with it:** the 01301 leg answers `G004` for its unmatched-file
miss (`API1_act.jsp:168-170`, `CD_DB_ERR`) — legacy's two legs genuinely use different codes for the
equivalent miss, and both are now matched. See §2 B-2.

### D-O · `API2` — uncaught `AIOOBE` on a malformed `file_nm`: container 500, no envelope

*(Recorded 2026-08-03. Ours is strictly better; keep it.)*

`API2_act.jsp:142-148` does `file_nm.split("_")` and then indexes `[4]` with **no length guard**, and it
sits **outside any `try`** — the verify `try` closes at `:119` and the next opens at `:173`. A short or
malformed name therefore raises an uncaught `ArrayIndexOutOfBoundsException`, and the gateway receives a
**container HTTP 500 with no envelope at all** — not a parseable `RC`, so the callback outcome is
undefined from the bank's side and the gateway's retry behaviour is driven by an unparseable body.

**Refactor keeps `G999` + ops alert** (`BulkExecutionResultService`, the `parts.length < 5` branch). Do
not "restore parity" here: there is no legacy `RC` to match, only an absence of one.

**Related tightening, also kept:** legacy `API2` omits `data_cnt` from its required-field guard
(`:130-136`) yet writes the unchecked value straight into `TB_FIRM_BAT_API_TRAN.BAT_CNT` — the **S-9**
asymmetry in §3. We require the field. A blank `BAT_CNT` is never useful and no conforming gateway omits
it, so this is a harmless strictening; do not drop the check to match legacy.

---

## 2. Boundary rules — ported or deliberate divergence

| # | Legacy rule | Site | Status |
|---|---|---|---|
| B-1 | `TB_PAY_TARGET_R001`: `TRAN_DT = TO_CHAR(now(),'yyyymmdd') AND PROC_STAT='9'` — today only, no `BIZ_KEY`, no `ORDER BY`, no `LIMIT` | `IDO.TB_PAY_TARGET_R001.xml` | Cross-tenant scope is the design (system batch). Boundedness is the H-2 divergence. |
| B-2 | `"INTER".equals(ANT_DIV_CD)` — a **5-char** literal | `NAPAS:139` | **Not a defect — corrected 2026-08-03.** `TB_PAY_TARGET` genuinely stores `"INTER"` (28 DEV rows); `AntDivCd.INTER.getValue()` *is* `"INTER"`. The literal is right. The real divergence is one line later at `NAPAS:144`, where legacy passes `ANT_DIV_CD` **verbatim** to the DTL lookup and we canonicalise first — inert today (no EBANK/SWIFT/VCB realtime DTL row), live the moment one is added. **Open PO routing decision**, see §12b of `batch-schedule-operating-hours-audit.md`. Evidence is JEX DEV, not PROD — see the D-7 box. |
| B-14 | Callback `RC` per failure class — the two legs deliberately differ | `API1_act.jsp:68-77`, `:125-129`, `:131-147`, `:159-170`; `API2_act.jsp:94-99`, `:130-136`, `:163-169` | **Matched 2026-08-03** (was four divergences). 01301 header + six body fields → `G001`, `bank_cd`/`prtn_id` → `G002`, unmatched file → `G004`; 01302 header → `G001`, five body fields → `G002`. Unprovisioned VAN key → `G003` on **both** legs (legacy never row-counts the key lookup — a null `CRYPT_KEY` fails `VerifyMac`; the earlier "`G004` on 01301" reading mistook the constant name for the flow). Carried by `MissingCallbackFieldException` + `VanApiKeyNotProvisionedException` through `CallbackAck`. Two branches keep a refactor-only `G999`: the 01301 **ambiguous-name** stop (FIX J — legacy has no ambiguity guard at all) and the 01302 **malformed `file_nm`** guard (D-O). |
| B-3 | Bank has no `TB_BATCH_SCHEDULE_INFO` row → `PROC_STAT='3'` FAIL. The comment claims "skip except INTER/blank/null", but the `INTER` exclusion exists **only** in the inner `ANT_WHERE_USE_YN` branch — the bank-level check fails unconditionally | `NAPAS:126-131` | Comment describes an intent the code does not implement. Refactor follows the **code**; flagged as an open question for the PO, not silently reinterpreted. |
| B-4 | 204 no-response → `sleep; continue`, leaving the `TRAN_TP='204'` firm-tran at `'6'` forever | `NAPAS:357-360` | Same strand class as D-J, on the FX leg. Refactor: `RealtimeDispatchService` FX leg records a terminal state. |
| B-5 | 204 **error** branch still reads `dep_curren_cd` / `apl_exc_rate` / `withd_amt` / `dep_amt` out of the EV and writes them to the DB | `NAPAS:441-448` | Writing rate/amount fields from a failed response. Not ported. |
| B-6 | `apl_exc_rate` folded at **scale 2, ROUND_DOWN** | `NAPAS:389` | An exchange rate truncated to 2 dp. Harmless at VND/USD magnitudes (~25 000.xx); catastrophic for a sub-unit rate. See §3 S-4. |
| B-7 | 01301 firm-file lookup pinned to `TRAN_DT = procDt` (today); 01302 takes the date from the **filename** | `API1:152` vs `API2:145` | Undocumented asymmetry. Refactor took **API2's side** — see §3 S-2. |
| B-8 | `file_nm.split("_")` → `[1]`=VAN, `[3]`=FILE_SEQ, `[4]`=TRAN_DT | `API2:142-151` | `FIRM_FILE_NM` naming stays **blocked** (PO decision). The prefix (`S_`/`R_`) is index 0, so D-1 (boundary-sweep) does not shift the indices. |
| B-9 | `tot == succ → '0'` / `tot == fail → '3'` / else `'1'` | `API2:427-429` | `tot == 0` satisfies `tot == succ`. Retained divergence (finalisation guards). Two live PROD instances. |
| B-10 | 99 (Woori) / 999 (others) file-round wrap, **reset to 1**, not modulo | `IDO.TB_FIRM_FILE_MST_R002` | D-3 (boundary-sweep). Ported per-split-file (M-4). |
| B-11 | Per-file loop breaks on a phase-B failure; pay-file FAIL; remaining split files never sent | `BATCH1:741-768` | Ported. Pinned by `BulkTwoPhaseDispatchOrderingTest` test 4. |
| B-12 | Commit at `:696-697`, upload at `:700`, audit at `:718`, 00301 at `:721` | `BATCH1` | **Restored 2026-07-31** (two-phase dispatch). Was inverted. |
| B-13 | Success branch has no `Thread.sleep`; skip / comms-fail / original-fail branches do | `PRCB:108,234,393` | Ported — `reflectOutcome` returns the pace decision. |

---

## 3. Report-only items

- **S-9** — `API1:134` requires a non-empty `data_cnt`; `API2:131` checks `bank_cd, prtn_id, file_nm,
  data_amt, data_dcm_amt` and **omits `data_cnt`**, which is then written straight to
  `TB_FIRM_BAT_API_TRAN.BAT_CNT`. Undocumented asymmetry, no known impact.
- **S-11** — `API2`'s success-path `TB_FIRM_BAT_API_TRAN_U001` sets `OUT_BANK_CD` + `BANK_PRTN_ID`
  (`:451-460`); the `catch`-path call omits both (`:479-486`). If `U001`'s `WHERE` includes them the
  failure update matches zero rows. **Needs the IDO checked** to confirm.
- **S-12** — lowercase `"yyyymmddhh24miss"` (`API1:419`, `API2` `dt`/`tm`, `BATCH2` `RQ_DTIME`,
  `NAPAS:61-62`, `WB_00000100`) vs uppercase `"YYYYMMDDHH24MISS"` (every `RS_DTTM`).
  **Still unresolved** — `jex.util.date.DateTime` has not been read. Not a finding until it is.
