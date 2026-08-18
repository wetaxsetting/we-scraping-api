# WeCMS Batch — `TB_BATCH_SCHEDULE_INFO` / `_DTL` Seed Review

**Date:** 2026-07-28 · **System:** Refactor (`wecms-api`) firm-banking dispatch · **Scope:** DEV / SIT / UAT / PROD
**Method:** static code trace of `wecms-api/src/main/java` + the legacy batch source under
`WECMS_BATCH DEV/source/WECMS_BATCH/`, then a **read-only** verification pass against the JEX DEV and
refactor databases (`SELECT` only — no writes, no DDL, no PROD).

> **⚠ Read §17 first.** A second pass (same day) verified this review against the live legacy data.
> It **closed OQ-1, OQ-3 and OQ-7**, **confirmed OQ-5 as a defect that has already failed and stranded
> real payments**, uncovered a **2-hour timezone divergence** (the JEX DB runs `Asia/Seoul`, not ICT),
> and **applied two code fixes** to `wecms-api`. Sections 1–16 are the original static review and remain
> accurate except for the "both tables are empty" baseline, which §17 supersedes.

**Deliverable under review:** [`docs/sql/2026/seed-batch-schedule-operating-hours-2026-07-28.sql`](../sql/2026/seed-batch-schedule-operating-hours-2026-07-28.sql)

**Related documents:**
- [wecms-batch-infrastructure-readiness-audit.md](wecms-batch-infrastructure-readiness-audit.md) — this closes its blocker **C-2**
- [../features/wecms-batch/batch-schedule-operating-hours-audit.md](../audits/batch/batch-schedule-operating-hours-audit.md) — the gate's implementation record
- [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md) · [wecms-batch-infra-team-checklist.md](wecms-batch-infra-team-checklist.md)

---

## 1. Executive verdict

### Can the legacy rows supplied for review be inserted as-is? **NO.**

The legacy data is *mostly* right, and the refactor is a faithful port of the legacy gate — but a
verbatim copy is wrong in three ways and incomplete in one:

| # | Problem with a verbatim copy | Consequence |
|---|---|---|
| 1 | **2 of the 4 legacy INFO rows are dead data.** Both gates look the INFO table up under `BATCH_ID='NAPAS_REAL_TIME_BATCH'` — nothing anywhere reads `('FILE_DEPOSIT_TRANSFER_BATCH1', …)` from `TB_BATCH_SCHEDULE_INFO`. | Harmless but misleading; a future reader assumes they gate the file path. Excluded. |
| 2 | **VCB `970436` has no INFO row at all**, yet refactor code routes *all* VCB payments (VND **and** USD) onto the file path, which calls the gate. | **Every VCB dispatch fails** with `수행 기준 없음`. This is the one genuinely missing row. |
| 3 | `reg_dttm` / `mod_dttm` / `last_edtr` are `''` (empty string) in the legacy rows. | Valid but untraceable. Replaced with a DB-computed `to_char(now(),'YYYYMMDDHH24MISS')` and `last_edtr='DBA_MANUAL'`. |
| 4 | **Three of the windows in the reviewed spec cannot and must not go in these tables** (Shinhan CITAD-API 16:30, VCB CITAD small/large 15:30/16:30). | They are already implemented in a **different** subsystem. Encoding them here would double-apply them. |

Plus one conflict and one inherited hazard that need a decision, not a code change: the legacy
Shinhan SWIFT window **disagrees** with the reviewed spec (23:45 vs 22:00 — **OQ-3**), and the
realtime path can be handed a 1-char `ANT_DIV_CD` that no seeded row matches, which **fails the
payment** (**OQ-5**).

### What is genuinely confirmed

The refactor's `OperatingHoursService` is a faithful, line-for-line port of the legacy gate — every
asymmetry, including the counter-intuitive ones, is preserved deliberately and correctly. All 12 DTL
rows in the legacy data are correct and belong in the seed, including the `('…BATCH1','970457','VCB  ')`
row that looks like a data-entry error and is not (§7).

### The single most important structural finding

**There are two independent, unrelated operating-window systems in this codebase.** Conflating them
is the main risk in this task:

| | `TB_BATCH_SCHEDULE_INFO` / `_DTL` | `TB_CUTOFF_CONFIG` / `TB_CUTOFF_PARAM` |
|---|---|---|
| Consumer | `OperatingHoursService` | `CutoffValidator` ← `CutoffConfigProvider` |
| When | **Dispatch time** (post-commit, batch-style) | **Request time** (user clicks Transfer) |
| Keyed by | `out_bank_cd` (`970424`/`970457`/`970436`) | bank *brand* (`SHINHAN`/`WOORI`/`VCB`) via `TB_FIRM_BANK_CODE.ENG_ABRVAT_NM` |
| Rails | `EBANK` `VCB` `CITAD` `NAPAS` `SWIFT` | `NAPAS` `CITAD` `CITAD_API` `SWIFT` `CITAD_LARGE` |
| Amount-aware | **No** — no amount column exists | **Yes** — `large-txn-threshold` param |
| Missing config | INFO missing ⇒ **FAIL the payment** | ⇒ **allow** (documented "no such rail") |
| On failure | mark FAIL + Korean ops alert | `WBE2066` / `WBE2067` / `WBE2068` to the user |
| Seeded by | this script | [`create-cutoff-config-tables-and-seed-2026-07-09.sql`](../sql/2026/create-cutoff-config-tables-and-seed-2026-07-09.sql) |

The "known operating windows" in the review request — Shinhan CITAD API 16:30, VCB CITAD
small/large — are **cut-off-config facts**, not batch-schedule facts. `CutoffConfigProvider.java:67-69`
declares exactly those rails. Do not try to express them in `TB_BATCH_SCHEDULE_DTL`.

---

## 2. Schedule lookup call-site matrix

Every read of either table in the entire refactor. There are exactly **two** call sites, both inside
`OperatingHoursService`, and exactly **two** callers of that service.

### 2.1 The gate itself

| # | Class · method | File:line | Table | `batch_id` passed | `out_bank_cd` | `ant_div_cd` |
|---|---|---|---|---|---|---|
| 1 | `OperatingHoursService.evaluateRealtime` | [`OperatingHoursService.java:82-84`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L82-L84) | INFO | `NAPAS_REAL_TIME_BATCH` | caller arg | — |
| 2 | `OperatingHoursService.evaluateRealtime` | [`:100-103`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L100-L103) | DTL | `NAPAS_REAL_TIME_BATCH` | caller arg | **raw `TB_PAY_TARGET.ANT_DIV_CD`** |
| 3 | `OperatingHoursService.evaluateBulk` | [`:128-130`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L128-L130) | INFO | **`NAPAS_REAL_TIME_BATCH`** ← *not* BATCH1 | caller arg | — |
| 4 | `OperatingHoursService.evaluateBulk` | [`:154-157`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L154-L157) | DTL | **`FILE_DEPOSIT_TRANSFER_BATCH1`** | caller arg | channel **name** (`EBANK`/`VCB`/`CITAD`/`NAPAS`/`SWIFT`) |

**The split key in rows 3+4 is the headline structural fact and it is correct.** Legacy proof, read
directly from the batch source:

```java
// FILE_DEPOSIT_TRANSFER_BATCH1.java:271-273  — the FILE batch reads INFO under the NAPAS key
JexData idoInBSIR001 = util.createIDOData("TB_BATCH_SCHEDULE_INFO_R001");
idoInBSIR001.put("BATCH_ID", "NAPAS_REAL_TIME_BATCH");
idoInBSIR001.put("OUT_BANK_CD", data.getString("OUT_BANK_CD"));

// FILE_DEPOSIT_TRANSFER_BATCH1.java:797-802  — but reads DTL under its own key
private String operTimeChk(String bankCd, String antDivCd) {
    JexData idoInBSDR001 = util.createIDOData("TB_BATCH_SCHEDULE_DTL_R001");
    idoInBSDR001.put("BATCH_ID", "FILE_DEPOSIT_TRANSFER_BATCH1");
```

`NAPAS_REAL_TIME_BATCH.java:112-114` and `:141-144` use `NAPAS_REAL_TIME_BATCH` for both.
**Answering the review question directly: the bulk path reading `NAPAS_REAL_TIME_BATCH` for INFO is
intentional legacy parity, not a bug.**

### 2.2 Callers of the gate

| Caller | File:line | Dispatch process | Gate | Decision handling |
|---|---|---|---|---|
| `RealtimeDispatchService.dispatch` | [`:154`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L154) | NAPAS realtime transfer **EFS 202** (+ **204** FX pre-step) | `evaluateRealtime(pay.getOutBankCd(), pay.getAntDivCd())` | `:155-159` SKIP → return, leave `PROC_STAT` WAIT, no alert · `:160-173` CUTOFF/NO_CRITERIA → `TB_PAY_TARGET` **FAIL** + `실시간지급이체 …` alert |
| `BulkReceptionService.receive` | [`:248`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L248) | File deposit transfer / bulk reception **00301** | `evaluateBulk(pay.getOutBankCd(), presentChannels(details))` | `:249-253` SKIP → return, leave WAIT, no alert · `:254-270` CUTOFF/NO_CRITERIA → `TB_PAY_FILE_MST` **FAIL** + `대량이체 …` alert |

### 2.3 Missing-row behaviour — the asymmetry that decides the whole seed

| Situation | Realtime (`NAPAS_REAL_TIME_BATCH`) | Bulk (`FILE_DEPOSIT_TRANSFER_BATCH1`) |
|---|---|---|
| **INFO row missing** | `NO_CRITERIA` → **FAIL** (`:85-87`) | `NO_CRITERIA` → **FAIL** (`:131-133`) |
| **DTL row missing** | `NO_CRITERIA` → **FAIL** (`:104-107`) | **`continue` → PASS** (`:158-160`) |
| INFO `EXE_DIV='E'` | `CUTOFF` → FAIL (`:89-91`) | `CUTOFF` → FAIL (`:135-137`) |
| INFO `EXE_DIV='N'` | `SKIP`, silent (`:92-94`) | `SKIP`, silent (`:138-140`) |
| `ANT_WHERE_USE_YN != 'Y'` | `DISPATCH`, DTL skipped (`:97-99`) | `DISPATCH`, DTL skipped (`:142-144`) |
| `ant_div_cd = 'INTER'` | `DISPATCH`, DTL skipped (`:97`) | n/a (channel names only) |
| No recognised channel in file | n/a | **`SKIP`** (`:147-149`) — file stuck WAIT, no alert |

Legacy proof of the DTL asymmetry — `operTimeChk` returns `"Y"` when the row is absent, whereas the
realtime path sets `exeErrYN=true; exeYN=false`:

```java
// FILE_DEPOSIT_TRANSFER_BATCH1.java:814-817   — missing DTL row = PASS
if (idoOutBSDR001.get("BATCH_START_TM") == null) { exeYNE = "Y"; }

// NAPAS_REAL_TIME_BATCH.java:155-160          — missing DTL row = FAIL
if (idoOutBSDR001.get("BATCH_START_TM") == null) { exeErrYN = true; exeYN = false; }
```

**Practical consequence for the seed:** gaps in the `FILE_DEPOSIT_TRANSFER_BATCH1` DTL rows are
benign. Gaps in the `NAPAS_REAL_TIME_BATCH` DTL rows **fail payments**. That is why §11 OQ-5 matters.

---

## 3. How many refactor processes exist, and which read these tables

**One Spring Boot application, multiple independent dispatch processes, one shared operating-hours
service, but two separate schedule configurations** (distinct `batch_id` for the DTL lookup, shared
INFO).

| Process | Entry point | Reads batch schedule? | Which config |
|---|---|---|---|
| Recipient / depositor inquiry **EFS 201** | `FirmBankingInquiryService` → `DepositorInquiryService` | **No** | — (gated only by `wecms.dispatch.recipient-inquiry`) |
| NAPAS realtime transfer **EFS 202** | `RealtimeDispatchService` | **Yes** | INFO `NAPAS_REAL_TIME_BATCH` + DTL `NAPAS_REAL_TIME_BATCH` |
| FX pre-inquiry **EFS 204** | `RealtimeDispatchService` (inside the same `dispatch()`) | **Indirectly** — the gate at `:154` runs *before* the 204 | same as above |
| Bulk / file reception **00301** | `BulkReceptionService` | **Yes** | INFO `NAPAS_REAL_TIME_BATCH` + DTL `FILE_DEPOSIT_TRANSFER_BATCH1` |
| Processing result check **EFS 203** | `ResultCheckService` ← `ResultCheckJob` | **No** | — (cron-gated only) |
| Callback **01301** | `BulkReceptionResultController` → `…Service` | **No** | — |
| Callback **01302** | `BulkExecutionResultController` → `…Service` | **No** | — |
| Stranded recovery | `StrandedRecoveryService` ← `StrandedRecoveryJob` | **No** | — |

So **exactly 2 of the 8 processes** read these tables. Verified by exhaustive grep: `OperatingHoursService`
is injected in only `RealtimeDispatchService` and `BulkReceptionService`; no other class references
either repository.

### 3.1 Which payment modules feed each path

Six executors register dispatch work post-commit. This determines which `(out_bank_cd, ant_div_cd)`
combinations can actually occur:

| Executor | Realtime (`registerAfterCommit` → `TB_PAY_TARGET`) | Bulk (→ `TB_PAY_FILE_MST`) |
|---|---|---|
| `TransferDispatchExecutor` (shared) | `:229` | `:327` |
| `InternalTransferDispatchExecutor` | `:285` | `:382` |
| `VendorDispatchExecutor` | `:211` | `:325` |
| `ExpenseDispatchExecutor` | `:204` | `:317` |
| `PayrollDispatchExecutor` | — | `:237` **file only** |
| `OverseasDispatchExecutor` | — | `:322`, `:409` **file only** |

**Consequences used in the seed design:**
- **SWIFT never reaches the realtime path.** Only `OverseasDispatchExecutor` produces SWIFT, and it
  is file-only. ⇒ no `('NAPAS_REAL_TIME_BATCH', …, 'SWIFT')` row is needed.
- **Payroll never reaches the realtime path**, and forces `ANT_DIV_CD='CITAD'`
  ([`PayrollPaymentService.java:544`](../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentService.java#L544)).
- **All VCB reaches the bulk path only** — see §6.

---

## 4. Required INFO rows

`TB_BATCH_SCHEDULE_INFO` — PK `(batch_id, out_bank_cd)`. Only `batch_id='NAPAS_REAL_TIME_BATCH'` is
ever read (§2.1).

| `batch_id` | `out_bank_cd` | `ant_where_use_yn` | `batch_start_tm` | `batch_end_tm` | Basis | Confidence |
|---|---|---|---|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | `970424` Shinhan | `Y` | `000000` | `235959` | Legacy row, verbatim. `BulkTransferConstants.java:21` | **Confirmed** |
| `NAPAS_REAL_TIME_BATCH` | `970457` Woori | `Y` | `000000` | `235959` | Legacy row, verbatim. `BulkTransferConstants.java:23` | **Confirmed** |
| `NAPAS_REAL_TIME_BATCH` | `970436` VCB | `N` | `000000` | `235959` | **Not in legacy data.** Required by refactor routing — §6 | **Row required (confirmed); flag value = OQ-2** |

The 24-hour `000000`–`235959` window is the legacy value and is deliberate: INFO is the bank-level
on/off switch plus the `ANT_WHERE_USE_YN` toggle that decides whether the per-channel DTL table is
consulted at all. Real time restriction lives in DTL. Corroborated by
[batch-schedule-operating-hours-audit.md:169](../audits/batch/batch-schedule-operating-hours-audit.md)
("legacy currently runs 24h windows / inert").

**`ant_where_use_yn` is the highest-leverage value in this seed.** `'Y'` engages the DTL gate; any
other value dispatches as soon as the INFO window is open (`:97`, `:142`). Shinhan and Woori keep the
legacy `'Y'`. VCB's `'N'` is argued in §6.3.

---

## 5. Required DTL rows

`TB_BATCH_SCHEDULE_DTL` — PK `(batch_id, out_bank_cd, ant_div_cd)`.

### 5.1 `batch_id = 'FILE_DEPOSIT_TRANSFER_BATCH1'` — bulk / file path (00301)

Missing row ⇒ **PASS**, so these rows *restrict*; they never cause a failure by their absence.

| `out_bank_cd` | `ant_div_cd` | Window | Reachable because | vs. reviewed spec |
|---|---|---|---|---|
| `970424` | `NAPAS` | `000500`–`234500` | detail `ANT_DIV_CD='N'` | ✅ 00:05–23:45 |
| `970424` | `CITAD` | `090000`–`153000` | detail `'C'` | ✅ "CITAD file" 09:00–15:30 |
| `970424` | `EBANK` | `090000`–`153000` | **Shinhan USD** → `'E'` (`ExpenseDispatchExecutor:277-278`, `VendorDispatchExecutor:284-285`) | not in spec; legacy row kept |
| `970424` | `SWIFT` | `090000`–`234500` | Overseas → `'S'` (`OverseasDispatchExecutor:107`) | ⚠️ **spec says 22:00** — **OQ-3** |
| `970457` | `NAPAS` | `001500`–`235500` | detail `'N'` | ✅ 00:15–23:55 |
| `970457` | `CITAD` | `083000`–`174000` | detail `'C'` | ✅ 08:30–17:40 |
| `970457` | `VCB` | `083000`–`174000` | **Woori USD** → `'V'` — see §7 | not in spec; legacy row kept |
| `970457` | `SWIFT` | `083000`–`191000` | Overseas → `'S'` | ✅ 08:30–19:10 |

### 5.2 `batch_id = 'NAPAS_REAL_TIME_BATCH'` — realtime path (202/204)

Missing row ⇒ **NO_CRITERIA ⇒ payment FAILS.** These rows are load-bearing.

| `out_bank_cd` | `ant_div_cd` | Window | Note |
|---|---|---|---|
| `970424` | `NAPAS` | `000500`–`234500` | Legacy row, verbatim |
| `970424` | `CITAD` | `090000`–`153000` | Legacy row. Spec's "CITAD API 16:30" is **not** applied here — **OQ-4** |
| `970457` | `NAPAS` | `001500`–`235500` | Legacy row, verbatim |
| `970457` | `CITAD` | `083000`–`174000` | Legacy row, verbatim |

Not seeded, correctly: `SWIFT` (unreachable on this path, §3.1), `INTER` (short-circuits before the
lookup, `:97`), VCB `970436` (never uses this path, §6).

**Total executable rows: 3 INFO + 12 DTL = 15.**

---

## 6. VCB handling

### 6.1 Routing trace — every module, every currency

| Module | VND | USD | Path |
|---|---|---|---|
| Vendor payment | VCB → **File** | VCB → **File** | `VendorDispatchExecutor`, same `isVcbFileTransfer` branch |
| Expense payment | VCB → **File** | VCB → **File** | [`ExpenseDispatchExecutor.java:143-157`](../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpenseDispatchExecutor.java#L143-L157) |
| Payroll | **File** | **File** | file-only for every bank (`PayrollDispatchExecutor:237`) |
| Internal transfer | **File** for VCB | **File** | plus a **total cut-off exemption**, §6.4 |
| Overseas | **File** | **File** | file-only (`OverseasDispatchExecutor:322`, `:409`) |

The decisive code:

```java
// ExpenseDispatchExecutor.java:143-157
// F1 (audit VCB, legacy PymnHelper.pymn_0001_001_c001:2188, SVN r36822): ALL VCB (970436),
// VND and USD, dispatches via the File path — pull VCB VND off the API path.
boolean isVcbFileTransfer = eq(master.getOutBankCd(), vcbBankCd);
if (CCY_VND.equalsIgnoreCase(trnfCcyCd) && !isVcbFileTransfer) {
    writeApiPath(...);          // → TB_PAY_TARGET  → evaluateRealtime
} else {
    writeFilePath(...);         // → TB_PAY_FILE_MST → evaluateBulk
}
```

Bank code `970436` is config-overridable at `ExpenseDispatchExecutor.java:116-117`
(`wecms.internal-transfer.routing.vcb-bank-cd`, default `970436`). Corroborated by memory
`project_vcb_file_transfer_switch` (JEX moved VCB off the Transfer API to File-based for VND + USD).

### 6.2 Verdict

| Question | Answer |
|---|---|
| Does the refactor require schedule rows for `970436`? | **Yes — one INFO row.** Without it, `evaluateBulk` hits `:131-133` → `NO_CRITERIA` → `BulkReceptionService:254-269` marks the pay-file FAIL. |
| INFO row? | **Required.** |
| NAPAS detail row? | **No.** |
| CITAD detail row? | **No.** |
| SWIFT detail row? | **No.** |
| Why no detail rows? | With `ant_where_use_yn='N'` the DTL table is never consulted for `970436` (`:142-144`). Even with `'Y'`, a missing BATCH1 DTL row passes (`:158-160`), so detail rows would add nothing. And the VCB windows in the reviewed spec are **cut-off-config** values, not batch-schedule values (§1, §8). |

### 6.3 Why `ant_where_use_yn='N'` for VCB — the one judgement call in the script

For every *recognised* channel, `'Y'` and `'N'` are behaviourally identical for VCB (no DTL rows
exist ⇒ BATCH1 missing-row ⇒ pass). They differ in exactly one case, and it is real:

```java
// ExpenseDispatchExecutor.java:274-284 — ANT_DIV_CD USD overrides
//   Shinhan USD -> EBANK ("E"); Woori USD -> VCB ("V"); VCB USD -> null.
if (CCY_USD.equalsIgnoreCase(lineCcy)) {
    if (eq(outBank, shinhanBankCd))      data.setAntDivCd(ANT_DIV_T_EBANK);
    else if (eq(outBank, wooriBankCd))   data.setAntDivCd(ANT_DIV_T_VCB);
    else if (eq(outBank, vcbBankCd))     data.setAntDivCd(null);   // ← VCB USD
}
```

A **VCB USD-only file** therefore has every detail row at `ANT_DIV_CD = null`.
`BulkReceptionService.presentChannels` skips nulls (`:544-546`), so the channel set is **empty**, and
with `'Y'` the gate returns `SKIP` at `:147-149`: the pay-file is left `PROC_STAT` WAIT **forever,
with no alert and no failure** — a silent stall, the worst of the four outcomes. `'N'` short-circuits
at `:142` and dispatches.

`'N'` is also the column DEFAULT in `V96__tb_batch_schedule_info.sql:10`. Recorded as **OQ-2** because
no legacy row exists to copy.

### 6.4 Not to be confused with the VCB internal-transfer 24/7 exemption

`CutoffValidator.isInternalTransferCutoffExempt` (`:116-118`) bypasses the **request-time** cut-off
entirely for VCB-sourced *internal transfers* (legacy `PymnHelper.java:3225-3233`). This is a
`CutoffValidator` behaviour and has **no effect** on `OperatingHoursService` — a VCB internal transfer
still passes through the dispatch-time gate and still needs the INFO row. The review request's
"internal transfers may bypass the time restriction" refers to this exemption; it does not remove the
seed requirement.

---

## 7. The suspicious legacy row — `('FILE_DEPOSIT_TRANSFER_BATCH1','970457','VCB  ')`

**It is correct. Insert it.**

### Why Woori `970457` is paired with `VCB`

`ANT_DIV_CD` in this table is a **destination channel / network name**, not a withdrawing bank. Woori
routes its **USD** remittances over the VCB channel:

```java
// ExpenseDispatchExecutor.java:279-280   (VendorDispatchExecutor.java:286-287 identical)
} else if (eq(outBank, wooriBankCd)) {
    data.setAntDivCd(ANT_DIV_T_VCB);      // ANT_DIV_T_VCB = "V"  (:101)
}
```

`'V'` is then mapped to the DTL key `VCB` by `BulkReceptionService.CHANNEL_BY_ANT_DIV` (`:145-146`).
Legacy twin: `operTimeChk(data.getString("OUT_BANK_CD"), "VCB")` at
`FILE_DEPOSIT_TRANSFER_BATCH1.java:321`. Symmetrically, Shinhan USD → `'E'` → `EBANK`, which is why
Shinhan has an `EBANK` row and Woori does not.

The 1-char → name mapping is confirmed against the legacy IDO, not inferred:

```sql
-- IDO.TB_PAY_FILE_DATA_R005.xml (verbatim)
SUM(CASE WHEN COALESCE(ANT_DIV_CD,'') = 'E' THEN CNT ELSE 0 END) AS EBANK_CNT
SUM(CASE WHEN COALESCE(ANT_DIV_CD,'') = 'V' THEN CNT ELSE 0 END) AS VCB_CNT
SUM(CASE WHEN COALESCE(ANT_DIV_CD,'') = 'C' THEN CNT ELSE 0 END) AS CITAD_CNT
SUM(CASE WHEN COALESCE(ANT_DIV_CD,'') = 'N' THEN CNT ELSE 0 END) AS NAPAS_CNT
SUM(CASE WHEN COALESCE(ANT_DIV_CD,'') = 'S' THEN CNT ELSE 0 END) AS SWIFT_CNT
SUM(CASE WHEN COALESCE(ANT_DIV_CD,'') IN ('E','V','C','N','S','') THEN 0 ELSE CNT END) AS ETC_CNT
```

`TB_PAY_FILE_DATA.ANT_DIV_CD` is `bpchar(1)` (`V105__tb_pay_file_data.sql:18`), which pins the bulk
domain to exactly these 5 codes plus null/ETC.

### Are the trailing spaces required? **No.**

`ant_div_cd` is `bpchar(5)`. PostgreSQL blank-pads on INSERT, so `'VCB'` is *stored* as `'VCB  '` —
identical to inserting the padded literal. And the lookup matches either way: `bpchar = bpchar`
equality ignores trailing blanks, and a `bpchar → text` cast strips them. The trailing spaces in the
legacy `INSERT` are cosmetic. The seed script writes `'VCB'`.

### Is it legacy-only? Can the refactor query it? **The refactor can and does.**

`evaluateBulk` will look up `('FILE_DEPOSIT_TRANSFER_BATCH1','970457','VCB')` for any Woori USD
file. The row is live, not historical.

---

## 8. Amount-dependent CITAD windows

### Can `TB_BATCH_SCHEDULE_DTL` represent the rule? **No — structurally impossible.**

The PK is `(batch_id, out_bank_cd, ant_div_cd)`. There is **exactly one** CITAD row per bank per
batch, and the table has **no amount column** (7 columns total, `V95`). One row cannot carry two
cut-offs.

### Where the amount condition *is* implemented

Not in `OperatingHoursService`, not before it, not after it, not in YAML, not as a code constant —
it lives in the **other** window system, evaluated at request time, before dispatch ever happens:

```java
// CutoffValidator.java:207-211
// VCB >=500M tier — bumps the File-SFTP CITAD cut-off to 16:30.
if (bank == Bank.VCB && ctx.hasTxnAtOrAbove500M() && windows.citadLarge() != null) {
    return windows.citadLarge();
}
return windows.citad();
```

- Threshold: `TB_CUTOFF_PARAM` key `large-txn-threshold`, loaded and validated at
  `CutoffConfigProvider.java:59`, `:187-206`; exposed via `CutoffValidator.largeTxnThreshold()` (`:90-92`).
  It was formerly a hard-coded constant and is now DB-driven (2026-07-09).
- Rails required for VCB: `CutoffConfigProvider.java:69` → `NAPAS`, `CITAD`, `CITAD_LARGE`.
- The Shinhan analogue is the `CITAD_API` rail (`:67`), selected for VND→VND at
  `CutoffValidator.java:204` — **currency-based, not transport-based**, so File-route modules such as
  Payroll also get 16:30 (legacy `PymnHelper.java:3257-3263`).
- `CutoffConfigProvider` **fails application startup** if any required rail or the threshold is
  missing (`:174-178`) — it can never silently fail open. That is a materially stronger guarantee
  than the batch-schedule gate, which fails *closed per payment* instead.

### Expected design — stated explicitly

The two systems are complementary and must stay separate:

- **Request time** (`TB_CUTOFF_CONFIG`): fine-grained, brand-keyed, currency- and amount-aware.
  Rejects the user's Transfer click with `WBE2066/7/8`. **This is where every window in the review
  request's "Known operating windows" list belongs**, including Shinhan CITAD API 16:30 and both VCB
  CITAD tiers.
- **Dispatch time** (`TB_BATCH_SCHEDULE_*`): coarse, bank-keyed, channel-level, amount-blind. Legacy
  batch parity. **Seed the legacy windows only.**

⚠️ **Do not** encode the amount tier here by inventing a channel code such as `CITADL`. `evaluateBulk`
only ever asks for the five names produced by `CHANNEL_BY_ANT_DIV`, so such a row would be
permanently unreachable dead data — while looking authoritative to the next reader.

---

## 9. Column types and value validity

Read from the DDL, not assumed.

### `TB_BATCH_SCHEDULE_INFO` — [`V96__tb_batch_schedule_info.sql`](../../src/main/resources/db/migration/V96__tb_batch_schedule_info.sql)

| Column | Type | Null | Default | PK | Entity mapping |
|---|---|---|---|---|---|
| `batch_id` | `varchar(50)` | NOT NULL | — | **PK 1** | `TbBatchScheduleInfo:31-32` |
| `out_bank_cd` | `varchar(15)` | NOT NULL | — | **PK 2** | `:35-36` |
| `ant_where_use_yn` | `bpchar(1)` | NULL | `'N'` | — | `:38-40` `@JdbcTypeCode(CHAR)` |
| `batch_start_tm` | `bpchar(6)` | **NOT NULL** | — | — | `:42-44` |
| `batch_end_tm` | `bpchar(6)` | **NOT NULL** | — | — | `:46-48` |
| `reg_dttm` | `varchar(14)` | NULL | — | — | `:50-51` |
| `mod_dttm` | `varchar(14)` | NULL | — | — | `:53-54` |
| `last_edtr` | `varchar(20)` | NULL | — | — | `:56-57` |

`CONSTRAINT pk_tb_batch_schedule_info PRIMARY KEY (batch_id, out_bank_cd)`. No UNIQUE, no CHECK, no FK.

### `TB_BATCH_SCHEDULE_DTL` — [`V95__tb_batch_schedule_dtl.sql`](../../src/main/resources/db/migration/V95__tb_batch_schedule_dtl.sql)

| Column | Type | Null | PK | Entity mapping |
|---|---|---|---|---|
| `batch_id` | `varchar(50)` | NOT NULL | **PK 1** | `TbBatchScheduleDtl:31-33` |
| `out_bank_cd` | `varchar(15)` | NOT NULL | **PK 2** | `:35-37` |
| `ant_div_cd` | `bpchar(5)` | NOT NULL | **PK 3** | `:39-42` `@JdbcTypeCode(CHAR)` |
| `batch_start_tm` | `bpchar(6)` | **NOT NULL** | — | `:44-46` |
| `batch_end_tm` | `bpchar(6)` | **NOT NULL** | — | `:48-50` |
| `reg_dttm` | `varchar(14)` | NULL | — | `:52-53` |
| `last_edtr` | `varchar(20)` | NULL | — | `:55-56` |

`CONSTRAINT pk_tb_batch_schedule_dtl PRIMARY KEY (batch_id, out_bank_cd, ant_div_cd)`.
**DTL has no `mod_dttm` column** — INFO does. A `DO UPDATE` on DTL must not set it.

### Key findings on values

1. **Empty strings in `reg_dttm` / `mod_dttm` are valid but undesirable.** Both are nullable
   `varchar(14)` with no CHECK, so the legacy `''` inserts cleanly. No application code reads them —
   `OperatingHoursService` reads only `getBatchStartTm()`, `getBatchEndTm()`,
   `getAntWhereUseYn()`. The seed writes `to_char(now(),'YYYYMMDDHH24MISS')` instead: a valid
   14-char value, DB-computed rather than hand-invented, and traceable.
2. **`last_edtr='DBA_MANUAL'`** = 10 chars ≤ 20. Fits. It also serves as the rollback predicate.
3. **`CURRENT_TIMESTAMP` must not be used bare.** The columns are `varchar(14)`, not `timestamp` —
   a bare `CURRENT_TIMESTAMP` would insert an ISO string that overflows 14 chars. `to_char(...)` is
   the correct form.
4. **`batch_start_tm` / `batch_end_tm` are `bpchar(6)` NOT NULL** — exactly 6 digits, `HHmmss`.
   `OperatingHoursService.exeDiv` (`:187-195`) does a **lexicographic string** compare against
   `LocalTime.now(ICT)` formatted `HHmmss`, so zero-padding is mandatory (`'90000'` would silently
   mis-sort). No value may be NULL.
5. **No key generation.** Both PKs are natural composites — `wb_get_next_val` is not involved, so
   `project_wb_get_next_val_funnel` does not apply.
6. **Neither table has `BIZ_KEY`.** These windows are **global** — shared by every tenant, and shared
   with the live JEX batch when the refactor runs against the live JEX DB
   (`project_uat_prod_live_jex_db_target`). See Risk R2.

---

## 10. Pre-check queries for the DBA

All read-only, all in **§0 of the SQL script**, and **none were executed during this review** (per the
review constraint). Run them, read the output, and only then proceed.

| # | Check | Pass condition |
|---|---|---|
| 0.1 | Dump existing INFO + DTL rows | 0 rows on a fresh refactor DB; on UAT/PROD, review any live JEX rows |
| 0.2 | Row counts | documents the C-2 baseline (`0`/`0`) |
| 0.3 | PK collisions against the 15 rows this script inserts | any hit is **preserved** by `DO NOTHING` — review it |
| **0.4** | **`DISTINCT out_bank_cd` in `TB_FIRM_CONF` vs INFO coverage** | **`has_info_row = 1` for every bank.** The highest-value check — see OQ-1 |
| 0.5 | Time format `^[0-9]{6}$` on both tables | 0 rows |
| 0.6 | Inverted windows (`start >= end`) | 0 rows — such a row can never resolve to `'Y'` |
| 0.7 | Trailing-space / unknown channel values (trimmed vs the queryable set) | 0 rows |
| 0.8 | DTL rows with no parent INFO row | 0 rows — otherwise the DTL row is unreachable |
| 0.9 | Overlapping windows within one `(batch_id, out_bank_cd)` | informational; overlap is legal (per-channel independent evaluation) |
| 0.10 | Dispatch flags OFF | **not checkable in SQL** — flags are host OS env. Run `env \| grep WECMS_DISPATCH_` on the app host |

Post-insert verification is §5 of the script: row counts (5.1), a `PRESENT`/`MISSING` verdict for all
15 intended rows (5.2), hygiene re-run (5.3), bank coverage re-run (5.4), and — most useful — **5.5, a
behavioural dry-run** that replays the `EXE_DIV` `CASE` in SQL against the current ICT wall-clock, so
the DBA can see exactly what the gate would decide for every seeded key without dispatching anything.

---

## 11. Missing or conflicting logic, and open questions

### Confirmed conflicts and hazards

| ID | Severity | Finding | Evidence | Owner |
|---|---|---|---|---|
| **OQ-1** | **High** | **Any withdrawing bank in `TB_FIRM_CONF` beyond `970424`/`970457`/`970436` needs its own INFO row**, or every dispatch from it fails. `OutBankCdNormalizer` accepts any NAPAS code, so this is not hypothetical. The window is unknown and must not be guessed. | pre-check 0.4; `OutBankCdNormalizer.java:72-93` | DBA + PO |
| **OQ-2** | Medium | **`ant_where_use_yn` for the new VCB `970436` INFO row.** Script uses `'N'`; §6.3 gives the reasoning (avoids a silent WAIT-forever stall on VCB USD-only files). No legacy row exists to copy. If the live JEX DB already has a `970436` row, **copy it verbatim instead** — `DO NOTHING` already preserves it. | §6.3; `V96` DDL default | PO + DBA |
| **OQ-3** | **High** | **Shinhan SWIFT window conflict.** Legacy DTL says `090000`–`234500` (23:45); the reviewed spec says 09:00–**22:00**. Script keeps the legacy value (legacy-first rule). One of the two is wrong. | §5.1; legacy `INSERT` vs review request | PO / bank |
| **OQ-4** | Medium | **Shinhan CITAD 15:30 vs 16:30 on the realtime path.** The seeded realtime CITAD row is `153000`. The 16:30 rule *is* implemented — at request time, via the `SHINHAN/CITAD_API` rail (`CutoffValidator.java:204`). Setting `163000` here would double-apply it; leaving `153000` means a payment accepted at 16:00 by `CutoffValidator` is then failed by the dispatch gate. **Needs an explicit decision on which layer owns the 16:30 rule.** | `CutoffValidator.java:196-206`; §5.2 | PO + Dev |
| **OQ-5** | **High** | **1-char `ANT_DIV_CD` aliases fail the realtime gate.** `TransferLineInput.java:32-33` validates `^(N\|NAPAS\|INTER\|C\|CITAD\|S\|SWIFT)?$`, and `resolveAntDivCd` carries the value through **verbatim** into `TB_PAY_TARGET.ANT_DIV_CD` (`bpchar(5)`, `V109:14`). A pay-target with `'N'` looks up DTL `ant_div_cd='N'`, misses, and **fails the payment** (`:104-107`). Same for HAENGSUNG's ERP import, whose `mapAntDivCd` is a bare `trim()` with a self-documented unresolved `'T'` value. **Inherited from legacy** — legacy has the identical 2-row seed and identical fail path. | `TransferDispatchExecutor.java:387-393`; `InternalTransferDispatchExecutor.java:439-445`; `ExpenseDispatchExecutor.java:374-381`; `ErpPaymentRefreshMasterImporter.java:266-275` | PO + Dev |

**OQ-5 options** (§4.2 of the script carries the ready-to-enable SQL, commented out):

- **(a) Data stopgap** — add 4 alias rows (`'N'`, `'C'` per bank) mirroring their 5-char twin's window
  exactly (copied, not invented). Reversible; no code change. But it *allows* payments legacy would
  have failed — a deliberate behavioural divergence.
- **(b) Code fix (recommended)** — normalise the 1-char form to the 5-char form before the lookup.
  Correct, keeps the seed legacy-identical. Out of scope for a DBA script; needs an application MR
  and paired review.

### Structural observations (not blockers)

1. **The split INFO/DTL `batch_id` is correct**, verified against legacy source (§2.1). Do not "fix" it.
2. **The missing-DTL asymmetry is correct** (BATCH1 passes, NAPAS fails) — genuine legacy behaviour
   (§2.3). Do not normalise it.
3. **Timezone deviation, already documented.** The gate compares against `Asia/Ho_Chi_Minh`
   (`OperatingHoursService.java:55`, `:197-199`); legacy used the DB server's `NOW()`. If the DB server
   is not on ICT, window edges shift between the two systems. Recorded in
   [batch-schedule-operating-hours-audit.md §12a](../audits/batch/batch-schedule-operating-hours-audit.md).
4. **No weekday / holiday dimension in these tables at all** — the legacy IDO `CASE` is time-of-day
   only, and `<tmUseYn>N</tmUseYn>` makes the jex-framework service-time block inert. Weekend blocking
   for non-NAPAS rails exists only in `CutoffValidator.java:133-140`. A seeded 24h INFO window really
   does mean "open on Sunday" as far as the dispatch gate is concerned.
5. **`TB_PAY_FILE_DATA.ANT_DIV_CD` is `bpchar(1)`** while `SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD` is
   `bpchar(5)`. `ExpenseDispatchExecutor.java:272` copies detail → file data directly, so a detail row
   carrying `'NAPAS'` would raise a value-too-long DB error on the file path. Out of scope here, but
   worth a separate look; it is the same 1-char/5-char inconsistency as OQ-5 seen from the other side.

---

## 12. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Seed omitted, dispatch enabled → `NO_CRITERIA` fails **100 %** of transfers + ops-alert storm | High | High | This script; verify with post-check 5.2 before flipping any flag |
| R2 | **Shared-table blast radius.** No `BIZ_KEY` ⇒ global rows; on the live JEX DB this changes legacy JEX batch behaviour for every tenant | Medium | **Critical** | `ON CONFLICT DO NOTHING` default; run pre-check 0.1 on UAT/PROD first; before-image via §7.1 |
| R3 | **OQ-5** — a `'N'`/`'C'`/`'T'` pay-target fails the realtime gate | Medium | High | Decide (a) or (b) before enabling `realtime-dispatch` |
| R4 | **OQ-3** — wrong Shinhan SWIFT cut-off (23:45 vs 22:00) | Medium | Medium | PO/bank confirmation; one value to change |
| R5 | **OQ-4** — layer disagreement on Shinhan CITAD 16:30: accepted at request time, failed at dispatch time | Medium | Medium | Decide the owning layer before enabling realtime dispatch |
| R6 | **OQ-1** — an unlisted withdrawing bank in `TB_FIRM_CONF` | Medium | High | Pre-check 0.4 is mandatory, per environment |
| R7 | VCB USD-only file silently stalls in WAIT (no alert, no failure) if `ant_where_use_yn='Y'` | Medium | Medium | Script uses `'N'` (§6.3); monitor `TB_PAY_FILE_MST` `PROC_STAT='9'` age |
| R8 | Amount tier mis-encoded into `TB_BATCH_SCHEDULE_DTL` as a fake channel | Low | Medium | §8 states the prohibition; such a row is unreachable dead data |
| R9 | `DO UPDATE` used carelessly on UAT/PROD, overwriting live JEX windows | Low | **Critical** | §6 of the script is opt-in and clearly fenced; before-image required first |
| R10 | DB server not on ICT ⇒ refactor and legacy JEX disagree on window edges | Low | Medium | Confirm host TZ; deviation is documented |
| R11 | Seed applied while `WECMS_DISPATCH_*` is unexpectedly ON (audit finding C-1: deployed flags diverge from committed `.env.*`) | Medium | High | Pre-check 0.10 — `env \| grep WECMS_` on every host first |

---

## 13. Final DBA execution steps

Run in this order, per environment. Do not compress steps.

1. **Review the SQL end to end** — [`docs/sql/2026/seed-batch-schedule-operating-hours-2026-07-28.sql`](../sql/2026/seed-batch-schedule-operating-hours-2026-07-28.sql).
   Confirm it contains no DDL, no `DELETE`, no `UPDATE`, and that §6 (`DO UPDATE`) stays commented.
2. **Confirm dispatch flags are OFF** on the application host:
   `env | grep -E 'WECMS_DISPATCH_|WECMS_EFS_ENABLED|WECMS_SFTP_ENABLED'`.
   All must be `false`. Do **not** rely on the committed `.env.*` files — audit finding C-1 proved they
   do not describe any running environment.
3. **Take a before-image** (§7.1) — `\copy` both tables to CSV. Mandatory on UAT/PROD.
4. **Run pre-checks §0.1 – §0.9** and read every result. **Stop** if 0.4 returns a bank code outside
   `970424`/`970457`/`970436`, or if 0.5/0.6/0.7/0.8 return any row.
5. **Obtain sign-off on the open questions** before proceeding past this point:
   **OQ-1** (unlisted banks — DBA/PO), **OQ-2** (VCB flag — PO), **OQ-3** (Shinhan SWIFT — PO/bank),
   **OQ-4** (CITAD 16:30 ownership — PO/Dev), **OQ-5** (1-char aliases — PO/Dev).
   OQ-3/OQ-4/OQ-5 do not block *inserting the seed*; they block *enabling dispatch*.
6. **Execute §1 – §3 manually** in the approved environment. One transaction; PostgreSQL rolls the
   whole thing back on any error. Section 4 stays commented.
7. **Run post-checks §5.1 – §5.5.** Every row in 5.2 must read `PRESENT`; every bank in 5.4 must read
   `1`; §5.3 hygiene must return 0 rows. Read §5.5 to see the gate's live decision per key.
8. **Keep dispatch flags disabled.** This step changes master data only. Enabling dispatch is a
   separate, gated action — follow [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md).
9. **Validate operating-hours behaviour before activation.** Re-run §5.5 at two different times of
   day (inside and outside a channel window) and confirm the `exe_div` column flips
   `N (SKIP)` → `Y (open)` → `E (CUTOFF)` as expected. Every INFO row must read `Y (open)` at any hour.
10. **Then, and only then**, proceed to activation — and note that blockers **C-1, C-3, C-4, C-5, H-2,
    H-7** from [the readiness audit](wecms-batch-infrastructure-readiness-audit.md) remain open
    independently of this seed.

---

## 14. Rollback guidance

The script performs **no `DELETE` and no `UPDATE`** in its default form, so rollback means removing
exactly the rows it added.

| Situation | Action |
|---|---|
| **Dispatch still OFF** (normal case) | Run the targeted `DELETE` in §7.2. It is scoped by `last_edtr = 'DBA_MANUAL'` **and** the explicit 15-key list, so any live JEX row that `DO NOTHING` preserved is untouched. Inspect with the paired `SELECT` first. |
| **Dispatch already LIVE** | **Do not delete.** Removing the INFO rows returns the gate to `NO_CRITERIA`, i.e. every dispatch fails again. Instead **close the window**: set `batch_start_tm='235959'`, `batch_end_tm='000000'` → permanently `'N'` → `SKIP`, which leaves rows in WAIT rather than FAIL and raises no alert. |
| **Wrong windows seeded** | Use the §6 `DO UPDATE` variant to restore the before-image values. Do not delete-then-reinsert on a shared DB. |
| **A live JEX row was overwritten** (only possible if §6 `DO UPDATE` was used) | Restore from the §7.1 CSV before-image. This is why step 3 is mandatory. |

`DO NOTHING` is the reason rollback is simple: the script's blast radius is bounded to rows that did
not previously exist.

---

## 15. Open questions requiring PO / VAN / DBA confirmation

| ID | Question | Blocks | Owner |
|---|---|---|---|
| **OQ-1** | Does any environment's `TB_FIRM_CONF` reference a withdrawing bank beyond `970424`/`970457`/`970436`? If so, what is its operating window? | Dispatch from that bank | DBA + PO |
| **OQ-2** | Confirm `ant_where_use_yn='N'` for VCB `970436` — or supply the live JEX row if one exists. | VCB dispatch correctness | PO + DBA |
| **OQ-3** | Shinhan SWIFT: `234500` (legacy) or `220000` (reviewed spec)? | Overseas Shinhan dispatch | PO / bank |
| **OQ-4** | Which layer owns the Shinhan CITAD 16:30 rule — request-time `CutoffValidator` (`CITAD_API`, current) or the dispatch gate? Should the realtime CITAD row stay `153000`? | Enabling `realtime-dispatch` | PO + Dev |
| **OQ-5** | 1-char `ANT_DIV_CD` aliases: add data rows (a) or normalise in code (b, recommended)? | Enabling `realtime-dispatch` | PO + Dev |
| OQ-6 | Are the legacy `('FILE_DEPOSIT_TRANSFER_BATCH1', …)` INFO rows genuinely unused, or is a future change expected to make `evaluateBulk` use its own INFO key? | Nothing today | Dev |
| OQ-7 | Is the DB server timezone ICT? If not, refactor (ICT) and legacy JEX (server `NOW()`) disagree on window edges on a shared DB. | Coexistence accuracy | Ops + DBA |
| OQ-8 | `TB_PAY_FILE_DATA.ANT_DIV_CD` is `bpchar(1)` but is populated by direct copy from the `bpchar(5)` detail column — is a value-too-long error reachable on the file path? | Separate defect review | Dev |

---

## 16. Evidence index

Primary sources read for this review. Every conclusion above cites one of these.

**Refactor (`wecms-api/src/main/java/com/kosign/wecms/domain/payment/`)**
`dispatch/service/OperatingHoursService.java` · `dispatch/service/OperatingHoursDecision.java` ·
`dispatch/service/RealtimeDispatchService.java` · `dispatch/service/BulkReceptionService.java` ·
`dispatch/constant/BulkTransferConstants.java` · `dispatch/entity/TbBatchScheduleInfo.java` ·
`dispatch/entity/TbBatchScheduleDtl.java` · `dispatch/repository/TbBatchScheduleInfoRepository.java` ·
`dispatch/repository/TbBatchScheduleDtlRepository.java` · `dispatch/entity/TbPayFileData.java` ·
`dispatch/entity/TbPayTarget.java` · `dispatch/service/TransferDispatchExecutor.java` ·
`expensepayment/service/ExpenseDispatchExecutor.java` · `vendorpayment/service/VendorDispatchExecutor.java` ·
`internaltransfer/service/InternalTransferDispatchExecutor.java` ·
`payrollpayment/service/PayrollDispatchExecutor.java` · `payrollpayment/service/PayrollPaymentService.java` ·
`overseas/service/OverseasDispatchExecutor.java` · `internaltransfer/service/CutoffValidator.java` ·
`internaltransfer/service/CutoffConfigProvider.java` · `internaltransfer/dto/TransferLineInput.java` ·
`internaltransfer/entity/ScdulPymnSumrDetl.java` · `common/OutBankCdNormalizer.java` ·
`haengsung/service/ErpPaymentRefreshMasterImporter.java`

**Schema** `src/main/resources/db/migration/`: `V95__tb_batch_schedule_dtl.sql` ·
`V96__tb_batch_schedule_info.sql` · `V105__tb_pay_file_data.sql` · `V109__tb_pay_target.sql`

**Tests** `src/test/java/.../dispatch/service/OperatingHoursServiceTest.java` (12 cases, incl. the
`EBANK`+`VCB` multi-channel precedence case and the `INTER` short-circuit)

**Legacy (read-only reference)** `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/`:
`NAPAS_REAL_TIME_BATCH.java:105-190` · `FILE_DEPOSIT_TRANSFER_BATCH1.java:265-400`, `:790-830` ·
`IDO.TB_PAY_FILE_DATA_R005.xml` · (`IDO.TB_BATCH_SCHEDULE_INFO_R001.xml` /
`IDO.TB_BATCH_SCHEDULE_DTL_R001.xml` quoted via the operating-hours audit)

**Docs / memory** `wecms-batch-infrastructure-readiness-audit.md` (C-2, C-1 — empty-table and
flag-divergence evidence) · `../features/wecms-batch/batch-schedule-operating-hours-audit.md` ·
`docs/references/database/legacy-table-inventory.md:133-134` ·
`docs/sql/2026/create-cutoff-config-tables-and-seed-2026-07-09.sql` (manual-script precedent) ·
memories `project_vcb_file_transfer_switch`, `project_payment_cutoff_validation_16h30`,
`project_flyway_disabled_ddl_validate`, `project_uat_prod_live_jex_db_target`,
`project_sys_scr_option_seed_strategy`

**Read-only DB verification (added 2026-07-28, second pass).** The original review deliberately
opened no DB connection. A follow-up pass ran **read-only `SELECT`s only** against
`postgres_dev_server_jexframe` (JEX DEV) and `postgres_dev_server_refactor` — no writes, no DDL, no
PROD. Those queries resolved four open questions and uncovered two confirmed defects; see §17.

---

## 17. Second pass — legacy-data verification and code fixes (2026-07-28)

### 17.1 What the legacy database actually says

| Query | Result | Resolves |
|---|---|---|
| `tb_batch_schedule_info` on JEX DEV | **4 rows**, exactly as supplied for review. **No `970436` row.** All `ant_where_use_yn='Y'`, all `000000`–`235959`, all `reg_dttm`/`mod_dttm`/`last_edtr` = `''` | confirms §1 items 1–3 |
| `tb_batch_schedule_dtl` on JEX DEV | **12 rows**, byte-identical to the reviewed data — including Shinhan `SWIFT` = `090000`–`234500` | **OQ-3 → RESOLVED** |
| `information_schema.columns` | `ant_div_cd` is `character(5)` — the refactor DDL matches. `length('VCB')` returning 3 is `length()` ignoring bpchar padding, not an unpadded value | confirms the trailing-space analysis in §7 |
| `SELECT out_bank_cd, count(*) FROM tb_firm_conf GROUP BY 1` | exactly **3 banks: `970424` (9 rows), `970436` (2), `970457` (4)** — all `org_stat='1'` | **OQ-1 → RESOLVED** |
| `SELECT current_setting('TimeZone')` on JEX DEV | **`Asia/Seoul`** (UTC+9) | **OQ-7 → RESOLVED, and it is a defect** |
| `ant_div_cd` distribution in `tb_pay_target` | `CITAD` 39 · `NAPAS` 30 · `INTER` 28 · **`'E'` 2 · `'S'` 2** | **OQ-5 → CONFIRMED as a live failure** |
| the 4 one-char rows in detail (**JEX DEV**) | `('970424','E')` × 2 → **`PROC_STAT='3'` (FAIL)**, 2025-09-18. `('970424','S')` × 2 → `PROC_STAT='9'` (WAIT), stranded since 2025-09-19 | — |
| refactor DB state | **3 INFO + 12 DTL, `last_edtr='DBA_MANUAL'`, `reg_dttm=20260728180209`** — the seed script has already been executed | supersedes the "both tables empty" baseline |
| `SELECT current_setting('TimeZone')` on the refactor DB | `Asia/Phnom_Penh` (UTC+7) — same offset as ICT, so DB-clock and ICT agree *there* | scopes the TZ defect to the JEX DB |

Two consequences worth stating plainly:

- **OQ-1 is better than feared and worse than it looks.** There is no unknown fourth bank — but `970436`
  **is** a fully configured firm-banking bank in legacy (2 `TB_FIRM_CONF` rows, `org_stat='1'`) that has
  **no schedule row at all**. The missing VCB INFO row is therefore a genuine legacy data gap, not a
  refactor-only concern, and the seed's VCB row is confirmed necessary.
- **OQ-5 is not theoretical.** Four real legacy pay-targets carried 1-char `ANT_DIV_CD`; two **failed**
  and two are **still stranded in WAIT**. This is the exact mechanism the first pass predicted.
  **Environment: JEX DEV, not PROD** (corrected 2026-08-01). Every row in the table above was measured
  on `postgres_dev_server_jexframe`; PROD incidence of the 1-char form is **unmeasured** and cannot be
  measured from here (`.claude/rules/database.md` forbids PROD access). The mechanism is proven; the
  production frequency is not. Do not quote these four rows as PROD failures — the correction exists
  because they were cited that way once.

### 17.2 Confirmed defect A — timezone divergence (was R10 / OQ-7)

Legacy computes `EXE_DIV` in SQL as `TO_CHAR(NOW(),'HH24MISS')` — the **database server's** zone. That
zone is **`Asia/Seoul`**, so legacy reads a `090000`–`153000` window as 09:00–15:30 **KST** =
**07:00–13:30 Vietnam time**. The refactor evaluated in ICT. Against a shared UAT/PROD JEX DB the two
systems would disagree by **two hours** on every window edge.

ICT is the correct interpretation — the windows are Vietnamese bank cut-offs, and
`CutoffValidator.VIETNAM_ZONE` is already `Asia/Ho_Chi_Minh`, so a KST-evaluated dispatch gate would
have failed payments that the request-time gate had just accepted.

**Fix applied** — [`OperatingHoursService.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java):
the hardcoded `ZoneId ICT` constant is replaced by

```java
@Value("${wecms.dispatch.operating-hours.zone:Asia/Ho_Chi_Minh}")
private String operatingHoursZone = "Asia/Ho_Chi_Minh";
```

Default behaviour is unchanged (ICT); the zone is now explicit, documented against the measured
`Asia/Seoul` finding, and overridable to `Asia/Seoul` if a co-hosted legacy batch ever requires
bug-for-bug parity. The field keeps an in-code default **and** is absent from every YAML, so an unset
property cannot abort startup (respects audit finding H-1).

### 17.3 Confirmed defect B — `ANT_DIV_CD` width inconsistency (was R3 / OQ-5)

`TB_PAY_TARGET.ANT_DIV_CD` is `bpchar(5)` and legitimately carries **both** widths. The rest of the
refactor already treats them as equivalent — `TransferNetwork.parse` maps `"N"` and `"NAPAS"` to the
same network for the request-time gate. `OperatingHoursService` was the **only** consumer using the raw
column value as a schedule key, so `'N'` never matched the `'NAPAS'` row → `NO_CRITERIA` → payment
failed. It also never stripped bpchar padding.

**Fix applied** — new [`AntDivChannel`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/constant/AntDivChannel.java),
the single canonical `ANT_DIV_CD` vocabulary:

| Method | Used by | Behaviour |
|---|---|---|
| `fromStorageCode(String)` | `BulkReceptionService.presentChannels` | **strict** — `E/V/C/N/S` → channel name; `null` for anything else (legacy `ETC`, deliberately ungated) |
| `canonical(String)` | `OperatingHoursService.evaluateRealtime` | **lenient** — trims bpchar padding, upper-cases, expands `E/V/C/N/S`, passes known names through, and returns an **unknown code verbatim** so it still misses the lookup and still fails closed |

`BulkReceptionService`'s private `CHANNEL_BY_ANT_DIV` map was removed in favour of the shared class —
one mapping, two gates, no drift. Behaviour of the bulk path is unchanged (`TB_PAY_FILE_DATA.ANT_DIV_CD`
is `bpchar(1)`, so only 1-char codes were ever possible there).

**Deliberately NOT changed:** an unrecognised code (e.g. HAENGSUNG's documented unresolved `'T'`) is
**not** coerced onto a channel. It keeps missing the lookup and keeps failing loudly — the legacy
outcome. Guessing a network for a money transfer is not a defect fix.

**Residual, still a PO decision:** with canonicalisation, `'N'`→`NAPAS` and `'C'`→`CITAD` now resolve
against the seeded rows. `'S'`→`SWIFT` and `'E'`→`EBANK` resolve to a **key that has no realtime
schedule row**, so those still fail. Whether SWIFT/EBANK *should* be dispatchable on the NAPAS realtime
rail is a routing decision, not a window decision — the candidate rows stay commented in §4.2 of the
seed script. Note the refactor's own API boundary (`TransferLineInput` `@Pattern`) already rejects
`'E'`/`'V'`, so this reduces to SWIFT-on-realtime plus the HAENGSUNG raw-passthrough path.

### 17.4 Verification

- `./gradlew compileJava compileTestJava` — clean.
- `./gradlew test` — **1935 tests, 0 failures, 0 errors.**
- `OperatingHoursServiceTest` — **12 → 17 tests**, all green. Five added: 1-char NAPAS alias, 1-char
  CITAD alias, bpchar-padded value, padded `INTER` still short-circuits the DTL lookup, and unknown
  code still fails closed.

### 17.5 Risks after this pass

| Was | Now |
|---|---|
| R3 / OQ-5 (High) | **Reduced.** Code fix lands the `N`/`C` majority case; SWIFT/EBANK-on-realtime remains a PO routing decision |
| R4 / OQ-3 (High) | **CLOSED.** Legacy DEV data confirms `234500`; the seed already matches. The reviewed spec's 22:00 is not what legacy runs |
| R6 / OQ-1 (High) | **CLOSED.** Exactly 3 banks, all covered by the seed |
| R10 / OQ-7 (Medium) | **CLOSED in code.** Zone explicit, documented, overridable |
| R1 (High) | **CLOSED on the refactor DB** — seed applied, 3 + 12 rows verified. **⚠ Superseded 2026-07-29 for the VCB row only — see §18.** |
| R2, R5/OQ-4, R7/OQ-2, R8, R9, R11 | **Unchanged.** All are data / ops / PO decisions, not code defects |

**Still open and still blocking activation:** OQ-2 (VCB flag confirmation), OQ-4 (which layer owns
Shinhan CITAD 16:30), the SWIFT/EBANK realtime routing decision, and readiness-audit blockers
**C-1, C-3, C-4, C-5, H-2, H-7**.

---

**Original first-pass note (retained):** the first pass opened no database connection; its row-state
claims (both tables empty; `TB_FIRM_CONF` = 3 rows) were quoted from the 2026-07-28 readiness audit
§4.4. §17 supersedes the "both tables empty" statement.

---

## 18. Third pass — VCB INFO row WITHDRAWN, reverted to exact legacy parity (2026-07-29)

**This section supersedes every earlier statement in this document that treats a VCB (`970436`)
`TB_BATCH_SCHEDULE_INFO` row as required, correct, or permanent** — specifically §1 item 2, §2's VCB
rationale, the `ant_where_use_yn='N'` judgement call, §4.3, and the R1 row in §17.5.

### Decision

**Legacy has no VCB INFO row. The refactor intentionally preserves this behaviour.**

Legacy JEX DEV `tb_batch_schedule_info` holds `970424` and `970457` only. Re-verified 2026-07-29
before any change: no Flyway migration inserts a `970436` row (`V95`/`V96` are DDL only, 0 `INSERT`s);
no legacy SQL / XML / properties / deployment script inserts one anywhere under `WECMS DEV/`,
`WECMS_BATCH DEV/`, `DEV ADMIN/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/`; SVN **r36822** — the
2026-07-16 VCB file-transfer switch — committed `PymnHelper.java` **only**, and `svn log -r
36795:36825 -v` shows no `.sql` / schedule / DB file in the entire range; and no runtime code depends
on the row (no hard-coded `970436` in `domain/payment/dispatch/`).

### What changed

| Item | Change |
|---|---|
| §1 SECTION 1 VCB tuple | **Commented out** in `seed-batch-schedule-operating-hours-2026-07-28.sql`. The seed now inserts 2 INFO rows (Shinhan, Woori) — legacy's exact bank set. Re-running it cannot reintroduce VCB. |
| Applied refactor DEV row | Removed by [`delete-vcb-batch-schedule-info-row-2026-07-29.sql`](../sql/2026/delete-vcb-batch-schedule-info-row-2026-07-29.sql) — DBA-run by hand, with pre-check, a raise-on-mismatch guard, and post-check. **Not executed by the audit.** |
| `970424` / `970457` / all 12 DTL rows / `tb_firm_conf` | **Untouched.** |
| Production Java | **No changes.** The code was already legacy-faithful. |

### `ANT_WHERE_USE_YN` — the question is withdrawn, not answered

With no legacy row there is **no legacy value** for VCB. Neither `'Y'` nor `'N'` exists. The
`'N'` rationale previously recorded in §2 and at the seed script's VCB comment block described a
hypothetical that legacy does not contain, and no longer applies. If a VCB schedule is ever
introduced it needs a **fresh business decision** on this column — it is not a copy from legacy.

### Accepted consequence

A VCB pay-file reaching the dispatch-time gate resolves to `NO_CRITERIA` and is **FAILED**:
`OperatingHoursService:168-170` → `BulkReceptionService:246-263` (`PROC_STAT=FAIL` +
`대량이체 Batch 수행 기준 없음` alert). Byte-for-byte the legacy outcome at
`FILE_DEPOSIT_TRANSFER_BATCH1.java:284-291` (`vrfi_msg = "No data to perform bulk transfer batch.
Please check TB_BATCH_SCHEDULE_INFO."`).

> **Legacy parity intentionally preserved.**
>
> **VCB dispatch will fail until the business explicitly decides to introduce a VCB schedule.**

This is **not** a defect and the removed row was **not** the correct permanent configuration.
Pinned by `OperatingHoursServiceTest.vcb_hasNoInfoRow_bulkAndRealtime_areNoCriteria`. Full record:
[../references/support-matrix.md](../references/support-matrix.md) §11a / §11b.
