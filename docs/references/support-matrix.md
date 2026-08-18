# Firm-Banking Sender-Bank Support Matrix

**Date:** 2026-07-29 (supersedes the 2026-07-28 first revision) · **System:** Refactor (`wecms-api`) verified against legacy JEX
**Method:** static trace of `WECMS DEV/` + `WECMS_BATCH DEV/` (legacy source of truth) and
`wecms-api/src/main/java`, plus **read-only** `SELECT`s against JEX DEV
(`postgres_dev_server_jexframe`). No DB writes. No feature flags enabled.

**Related:** [../audits/payment-dispatch-operating-hours-flow.md](../audits/deployment/payment-dispatch-operating-hours-flow.md) ·
[../deployment/wecms-batch-schedule-seed-review.md](../deployment/wecms-batch-schedule-seed-review.md) ·
[../features/wecms-batch/batch-schedule-operating-hours-audit.md](../audits/batch/batch-schedule-operating-hours-audit.md)

> ### ⚠ Correction to the 2026-07-28 revision
>
> The first revision of this document stated that VCB's missing schedule rows mean
> **"no schedule rows — legacy allows"** and that VCB file dispatch is *ungated*. **That was
> wrong.** Legacy distinguishes two different lookups with two different miss-behaviours:
>
> | Lookup miss | Legacy outcome |
> |---|---|
> | `TB_BATCH_SCHEDULE_INFO` row absent | **ERROR → payment FAILS** (both legs) |
> | `TB_BATCH_SCHEDULE_DTL` row absent | BATCH1 file leg: **pass** · NAPAS realtime leg: **FAIL** |
>
> VCB has **no INFO row**, so VCB file dispatch **FAILS** at the dispatch-time gate — in legacy
> *and* in the refactor, identically. The refactor was correct; the document was not. No code
> changed. Details in §4.

---

## 1. Final verdict

| # | Item | Verdict |
|---|---|---|
| 1 | Sender-bank code set = `970424` / `970436` / `970457` | **MATCHES LEGACY** |
| 2 | `TB_FIRM_CONF … ORG_STAT='1'` as *the* authoritative sender-set condition | **MISMATCH — NOT FIXED** (doc-level only; see §3. Legacy applies ORG_STAT **per context**, and the refactor already mirrors each context correctly. The generalisation was mine, not the code's.) |
| 3 | VCB realtime unsupported — forced to file path | **MATCHES LEGACY** |
| 4 | VCB file-forcing covers Internal Transfer / Vendor / Expense | **MATCHES LEGACY** |
| 5 | VCB file-forcing "covers Payroll and Overseas" | **MATCHES LEGACY — but for a different reason** (both are file-only anyway; legacy *blocks* VCB outright rather than routing it — §5.3/§5.4) |
| 6 | `NAPAS_REAL_TIME_BATCH` intentionally has no `970436` rows | **MATCHES LEGACY** |
| 7 | Missing `TB_BATCH_SCHEDULE_DTL` row → file leg passes | **MATCHES LEGACY** |
| 8 | Missing `TB_BATCH_SCHEDULE_INFO` row → **FAIL** (not pass) | **MATCHES LEGACY** (refactor correct; §0 doc was wrong) |
| 9 | VCB file dispatch is therefore **blocked today** in DEV | **MATCHES LEGACY** — **NEEDS PO CONFIRMATION** (operationally, not for parity) |
| 10 | VCB-source Internal Transfer cut-off exemption (24/7) | **MATCHES LEGACY** |
| 11 | VCB Vendor / Expense / Payroll / Overseas remain cut-off-gated | **MATCHES LEGACY** |
| 12 | Weekend / holiday validated at the dispatch-time gate | **MATCHES LEGACY** — neither system validates weekends there; weekends are request-time only (§7) |
| 13 | Payroll VCB VND blocked; VCB USD same-bank only | **MATCHES LEGACY** |
| 14 | Overseas VCB blocked entirely | **MATCHES LEGACY** |
| 15 | Unknown sender bank passes the cut-off gate silently | **MATCHES LEGACY** |

**No production-code mismatch was found. No `src/main/java` file was modified.** Three regression
tests were added to pin behaviours that were correct but unpinned (§9), and this document was
corrected.

---

## 2. Final capability matrix

Sender banks only. `TB_FIRM_BANK_CODE`'s other 3,202 rows (68 NAPAS + 3,137 CITAD branches) are
**destination** banks and carry no capabilities — see §3.1.

| Bank code | Bank | Realtime | File | Inquiry | Dispatch | Dispatch-time operating hours | Request-time cutoff |
|---|---|---|---|---|---|---|---|
| **970424** | ShinhanBank | **Supported** | **Supported** | **Configured** | **Supported** | **Gated** — INFO + 4 DTL channels (CITAD/EBANK/NAPAS/SWIFT) | **Cutoff-gated** — NAPAS · CITAD · CITAD_API · SWIFT; past-cutoff *allowed* except CITAD VND→VND |
| **970436** | Vietcombank | **Not supported — file only** (no bank certificate) | **Supported** (IT / Vendor / Expense) | **Configured** | **Supported — partial** (blocked for Overseas; VND blocked for Payroll) | **No INFO row → `NO_CRITERIA` → FAIL** (legacy-identical; *not* "allow") | Internal Transfer: **Cutoff-exempt** (24/7) · Vendor / Expense / Payroll: **Cutoff-gated** (NAPAS · CITAD · CITAD_LARGE) · Overseas: **Not applicable** (blocked) |
| **970457** | WooriBank | **Supported** | **Supported** | **Configured** | **Supported** | **Gated** — INFO + 4 DTL channels (CITAD/NAPAS/SWIFT/VCB) | **Cutoff-gated** — NAPAS · CITAD · SWIFT; always blocks past cutoff |

### 2.1 VCB per-module detail (the matrix row above is a summary)

| Module | Legacy behaviour | Refactor | Verdict |
|---|---|---|---|
| Internal Transfer | File path, VND + USD; cut-off **exempt** (24/7) | same | MATCHES |
| Vendor | File path, VND + USD; VCB USD→other-bank rejected | same | MATCHES |
| Expense | File path, VND + USD; VCB USD→other-bank rejected | same | MATCHES |
| Payroll | **VCB VND blocked outright**; VCB USD **same-bank only** | same (2 error codes) | MATCHES |
| Overseas | **VCB blocked outright** — cannot source a SWIFT remittance | same (1 error code) | MATCHES |

### 2.2 Vocabulary

- **Supported** — the transport is reachable for this bank in normal operation.
- **Not supported — file only** — deliberately routed away from realtime; not a gap.
- **Configured** — an active `TB_FIRM_CONF` row exists; the runtime feature flag is a separate axis (§10).
- **Gated** — schedule rows exist and are evaluated.
- **`NO_CRITERIA` → FAIL** — the gate runs, finds no criteria, and fails the payment. **Not** "allowed".
- **Cutoff-exempt / Cutoff-gated / Not applicable** — request-time `CutoffValidator` disposition.

---

## 3. Sender-bank set and `ORG_STAT`

### 3.1 The three codes

Verified on JEX DEV 2026-07-29:

```sql
SELECT out_bank_cd, count(*) conf_rows,
       count(*) FILTER (WHERE org_stat='1') active_rows
FROM tb_firm_conf GROUP BY 1 ORDER BY 1;
--  970424 | 9 | 9
--  970436 | 2 | 2
--  970457 | 4 | 4
```

Corroborated in legacy code by `PymnHelper.java:118` (`VCB_BANK_CD = "970436"`) and the
Shinhan/Woori literals at `pymn_0005_001_c001_act.jsp:187,191` and `PymnHelper.java:2359`
(`"970424".equals(outBank) || "970457".equals(outBank) || VCB_BANK_CD.equals(outBank)`).
No fourth code appears anywhere in the payment paths. **MATCHES LEGACY.**

### 3.2 `ORG_STAT` is context-dependent — the "one authoritative condition" framing was wrong

Legacy does **not** have a single "sender banks = `TB_FIRM_CONF WHERE ORG_STAT='1'`" rule. Three
different contexts apply three different conditions:

| Context | Legacy query / check | `ORG_STAT` filter? | Refactor equivalent | Verdict |
|---|---|---|---|---|
| Recipient-name inquiry (web) | `IDO.TB_FIRM_CONF_R002.xml:31`, `R003.xml:31` — `AND ORG_STAT = '1'` (called from `PymnHelper.java:874`, `comm_1003_001_act.jsp:42`, `comm_0184_007_r001_act.jsp:299`) | **Yes** | `FirmBankingInquiryService.java:122` `existsByBizKeyAndOrgStat(bizKey,"1")`; `:223` `findFirstByBizKeyAndOrgStatOrderByRegDttmAsc(bizKey,"1")` | **MATCHES** |
| Dispatch (realtime + file) | `IDO.TB_FIRM_CONF_R001.xml` — keyed on `BIZ_KEY`/`OUT_BANK_CD`/`WDRL_ACT_NO`, **no** `ORG_STAT` predicate (called at `NAPAS_REAL_TIME_BATCH.java:91-95`, `FILE_DEPOSIT_TRANSFER_BATCH1.java:219-224`) | **No** | `RealtimeDispatchService.java:140-141` and `BulkReceptionService.java:189-190` — `findById(new TbFirmConfId(bizKey, outBankCd, wdrlActNo))`, no status filter | **MATCHES** |
| Result-check batch | `PROCESSING_RESULT_CHECK_BATCH.java:107` — Java-side `if (!WCConst.ORG_ST_OK.equals(ORG_STAT))` → skip | **Yes, in Java** | `ResultCheckService.java:286-289` — `if (!ORG_ST_OK.equals(conf.getOrgStat())) { … return true; }` | **MATCHES** |

**Consequence:** an `ORG_STAT != '1'` row still **dispatches** (legacy and refactor alike) but is
skipped by inquiry and by result-check. Verdict for the framing itself: **MISMATCH — NOT FIXED**,
because the defect is in the earlier document's wording, not in any code. No change warranted.

---

## 4. Operating hours — the INFO/DTL asymmetry (the corrected finding)

### 4.1 `EXE_DIV` is identical in both systems

`IDO.TB_BATCH_SCHEDULE_INFO_R001.xml` and `IDO.TB_BATCH_SCHEDULE_DTL_R001.xml` both compute:

```sql
CASE WHEN BATCH_START_TM > TO_CHAR(NOW(),'HH24MISS') THEN 'N'
     WHEN BATCH_END_TM   < TO_CHAR(NOW(),'HH24MISS') THEN 'E'
     ELSE 'Y' END AS EXE_DIV
```

Refactor twin: `OperatingHoursService.exeDiv(...)` at `:224-232` — same lexicographic `HHmmss`
compare. `'N'` → SKIP, `'E'` → CUTOFF, `'Y'` → DISPATCH.

### 4.2 Miss-behaviour differs by table and by leg — verified line by line

| Leg | Lookup | Legacy | Refactor | Verdict |
|---|---|---|---|---|
| File (BATCH1) | INFO absent | `FILE_DEPOSIT_TRANSFER_BATCH1.java:284-291` → `exeErrYN=true; exeYN=false;` `vrfi_msg="No data to perform bulk transfer batch. Please check TB_BATCH_SCHEDULE_INFO."` → **FAIL** | `OperatingHoursService.java:168-170` → `NO_CRITERIA`; `BulkReceptionService.java:246-263` → `PROC_STAT=FAIL` + `대량이체 Batch 수행 기준 없음` alert | **MATCHES** |
| File (BATCH1) | DTL absent | `operTimeChk` at `:797-826`, specifically `:814-817` → `exeYNE="Y"` → **pass** | `OperatingHoursService.java:195-197` → `continue` (pass) | **MATCHES** |
| Realtime (NAPAS) | INFO absent | `NAPAS_REAL_TIME_BATCH.java:125-129` → `exeErrYN=true` → `TB_PAY_TARGET` **FAIL** | `OperatingHoursService.java:111-113` → `NO_CRITERIA` → FAIL | **MATCHES** |
| Realtime (NAPAS) | DTL absent | `NAPAS_REAL_TIME_BATCH.java:155-160` → `exeErrYN=true` → **FAIL** | `OperatingHoursService.java:141-144` → `NO_CRITERIA` | **MATCHES** |
| Realtime (NAPAS) | `ANT_DIV_CD = INTER` | `:138` short-circuits the DTL lookup | `OperatingHoursService.java:134` same | **MATCHES** |

Both legs read INFO under the **shared** key `BATCH_ID='NAPAS_REAL_TIME_BATCH'` — legacy
`FILE_DEPOSIT_TRANSFER_BATCH1.java:272` and `NAPAS_REAL_TIME_BATCH.java:113` both put that literal.
The DTL keys differ (`FILE_DEPOSIT_TRANSFER_BATCH1` vs `NAPAS_REAL_TIME_BATCH`). Refactor mirrors
this at `OperatingHoursService.java:166` / `:193` / `:139`. **MATCHES LEGACY.**

### 4.3 What this means for VCB

JEX DEV 2026-07-29 — `TB_BATCH_SCHEDULE_INFO` holds exactly 4 rows, all
`ANT_WHERE_USE_YN='Y'`, window `000000`–`235959`:

| `BATCH_ID` | `OUT_BANK_CD` |
|---|---|
| `FILE_DEPOSIT_TRANSFER_BATCH1` | 970424, 970457 |
| `NAPAS_REAL_TIME_BATCH` | 970424, 970457 |

`TB_BATCH_SCHEDULE_DTL` holds 12 rows across the same two banks. **Zero rows for `970436` in
either table.**

Therefore, in **both** systems: a VCB pay-file reaching the dispatch-time gate resolves to
`NO_CRITERIA` and is **failed**, with the `수행 기준 없음` / "No data to perform bulk transfer
batch" message. This is exact legacy parity — and it is also why the VCB file path cannot
currently complete a dispatch. That operational question is separate from the parity question and
is escalated in §11, not silently patched.

### 4.4 No VCB schedule rows exist anywhere in legacy

Searched `WECMS DEV/`, `WECMS_BATCH DEV/`, `DEV ADMIN/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/`
for `TB_BATCH_SCHEDULE` in `*.sql`, `*.properties`, `*.xml`, `*.txt`. Only two hits, both query
definitions:

- `WECMS_BATCH DEV/…/IDO/IDO.TB_BATCH_SCHEDULE_INFO_R001.xml`
- `WECMS_BATCH DEV/…/IDO/IDO.TB_BATCH_SCHEDULE_DTL_R001.xml`

**No seed script, no migration, no deployment script, no admin screen, and no hard-coded fallback
window exists in any legacy tree.** The DEV rows carry `reg_dttm = 20251113095800` with an empty
`last_edtr`, consistent with a one-off manual DBA insert. There is no environment-specific legacy
configuration that would give VCB a window in UAT or PROD — but DEV is the only environment
readable from here (§11).

The only VCB INFO row that exists anywhere in the workspace is a **proposal**, not legacy:
`docs/sql/2026/seed-batch-schedule-operating-hours-2026-07-28.sql:322` seeds
`('NAPAS_REAL_TIME_BATCH','970436','N','000000','235959')` and its own header at `:28-29` states
*"970436 IS a configured firm-banking bank in legacy yet has NO legacy schedule row → the VCB gap
is real."* That script is DBA-run and PO-gated; it has **not** been applied. Per the instruction
*"do not silently seed VCB schedule rows"*, nothing was applied here.

---

## 5. VCB realtime — legacy evidence

### 5.1 The rule and its stated reason

`PymnHelper.java:2569-2584` (in `pymn_0009_001_c001`, Internal Transfer):

```java
/*
 * VCB File-transfer switch (Mr. Jung 2026-07-16) — SUPERSEDES the 2026-05-19 scope.
 * VCB (970436) cannot support the real-time Transfer API without a certificate, so
 * ALL VCB transfers are now routed to the File path (sent as an sFTP firm-banking
 * file by WeCMS Batch / Van Center), mirroring the Shinhan USD file-based precedent.
 *   VCB VND -> VCB VND   : File path …  BANK_CHNL_DIV='T'  (was API)
 *   VCB VND -> Other VND : File path …  BANK_CHNL_DIV='O', NAPAS/CITAD from upload (was API)
 *   VCB USD -> VCB USD   : File path …  BANK_CHNL_DIV='T', ANT_DIV_CD=null (unchanged)
 *   Out of scope         : VCB USD->VND, VCB VND->USD (not supported per matrix)
 */
boolean isVcbFileTransfer = VCB_BANK_CD.equals(scdulPymnSumr.getOutBankCd());   // :2585
…
if ((((TRNF_CCY_CD.equals("VND") && !isShinhanUsdToVnd) || isWooriUsdToVnd)) && !isVcbFileTransfer) {  // :2600
```

The near-identical guard is at `PymnHelper.java:2177-2186` + `:2188` in `pymn_0001_001_c001`.

### 5.2 Module coverage of the guard

`pymn_0001_001_c001` is **shared by two modules** — `pymn_0001_001_c001_act.jsp:99` (Vendor) and
`pymn_0003_001_c001_act.jsp:84` (Expense) both call it. `pymn_0009_001_c001` is Internal Transfer
(`pymn_0009_001_c001_act.jsp:98`). So the `isVcbFileTransfer` guard covers exactly **Internal
Transfer, Vendor, Expense** — the three modules that have an API branch at all.

Refactor twins, all structurally identical:
`InternalTransferDispatchExecutor.java:228-232` · `VendorDispatchExecutor.java` ·
`ExpenseDispatchExecutor.java:143-157` · `OverseasDispatchExecutor.java:122-124`.
**MATCHES LEGACY.**

### 5.3 Payroll — no guard needed, and VCB is *blocked*, not routed

`pymn_0005_001_c001_act.jsp` writes only `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA` (`:132-154`) — it
has **no API branch**, so no VCB routing guard is required. Instead it hard-blocks VCB at
`:174-182`:

```java
/*
 * VCB scope for Payroll (PO mapping 2026-06-16):
 *   VCB VND -> not supported at all (block here)
 *   VCB USD -> supported only to VCB (same bank); USD->other-bank is blocked below
 */
if ("970436".equals(outBank) && "VND".equals(rows.getString("TRNF_CCY_CD"))) {
    throw new WbRollbackException(util, WbMessageCode.WBE0000,
            "Payroll does not support VCB VND transfer.");
}
```

Plus `:195-197` (`VCB USD → TRSC_CCY_CD = TRNF_CCY_CD`) and `:221-223`
(`VCB USD → ANT_DIV_CD = null`, same-bank only).

Refactor: `PayrollDispatchExecutor.java:214` throws `WBE_PAYROLL_VCB_VND_NOT_SUPPORTED`
(annotated `// legacy :179-182`), `:246` throws `WBE_PAYROLL_VCB_USD_OTHER_BANK`
(`// legacy :249-252`), `:236` preserves the legacy null; `PayrollPaymentService.java:559-565`
enforces the same pair at request time. Messages registered at
`messages.properties:240-241`. **MATCHES LEGACY.**

### 5.4 Overseas — VCB blocked outright

`pymn_0007_001_c001_act.jsp:159-162` and `:285-288`:

```java
/* VCB does not support overseas remittance (PO mapping 2026-06-16) — defensive guard */
if ("970436".equals(ovrsPymnMstr.getDbtAcctCd())) {
    throw new WbRollbackException(util, WbMessageCode.WBE0000, "VCB does not support overseas remittance.");
}
```

Refactor: `OverseasDispatchExecutor.java:343-345` throws `WBE_OVERSEAS_VCB_NOT_SUPPORTED`
(`messages.properties:245`), cited `// legacy pymn_0007:160-162`. **MATCHES LEGACY.**

### 5.5 Is there *any* valid legacy scenario with VCB realtime? — No, as of 2026-07-16

There **was** one before that date, and the pre-change source is preserved in-workspace:
`Claude Code (AI)/backups/PymnHelper.java.20260716.pre-vcb-file-transfer.bak`.

| | Pre-change (backup) | Post-change (current legacy) |
|---|---|---|
| `pymn_0001_001_c001` | `:2175` `if (input.getString("TRNF_CCY_CD").equals("VND")) {` | `:2188` `… && !isVcbFileTransfer) {` |
| `pymn_0009_001_c001` | `:2576` `if ((TRNF_CCY_CD.equals("VND") && !isShinhanUsdToVnd) \|\| isWooriUsdToVnd) {` | `:2600` `… && !isVcbFileTransfer) {` |
| `isVcbFileTransfer` | **absent** | present at `:2186` / `:2585` |

So VCB VND *did* take the realtime API path until the 2026-07-16 change (SVN r36803) removed it.
Current legacy has **no** product-type, currency, payment-type, or environment exception that
re-enables it — the guard is unconditional on `OUT_BANK_CD` alone. The absence of `970436` rows
under `NAPAS_REAL_TIME_BATCH` is consistent with, and downstream of, this decision.

**Verdict: MATCHES LEGACY.** No refactor change; a regression test now pins it (§9).

---

## 6. VCB Internal-Transfer cut-off exemption

`PymnHelper.validateTransferTiming` at `:3251-3257`:

```java
// pymn_0009 Internal Transfer: a VCB transfer is treated as an internal transfer → works 24/7.
// Rule (PO/senior confirmed): if the SOURCE bank is VCB, allow the transfer regardless of
// channel or destination — not a VCB->VCB same-bank check.
if (scdulPymnSumr != null
        && ScdulPymnTypeCd.INTERNAL.equals(scdulPymnSumr.getScdulPymnTypeCd())
        && VCB_BANK_CD.equals(bankCd)) {
    return;
}
```

Three properties, all reproduced by the refactor:

1. **Source-bank test, not same-bank test** — `CutoffValidator.isInternalTransferCutoffExempt`
   (`:113-115`) returns `resolveBank(outBankCd) == Bank.VCB`.
2. **Internal-Transfer only** — the legacy guard is inside an `ScdulPymnTypeCd.INTERNAL` test; the
   refactor encodes this in the method *name* and documents at `CutoffValidator.java:104-112` that
   Vendor / Expense / Payroll / Overseas must never call it.
3. **Skips everything downstream** — weekend check, start-time, cut-off, and the VCB CITAD ≥500M
   tier are all bypassed by the early `return`.

Pinned by `CutoffValidatorTest.isInternalTransferCutoffExempt_should_be_true_only_for_vcb_source`
and `vcb_still_blocks_past_cutoff_for_non_internal_transfer_callers`. **MATCHES LEGACY.**

---

## 7. Weekends and holidays

- **Dispatch-time gate: no day-of-week logic at all.** Both IDO queries are pure `HHmmss`
  comparisons; both carry `<tmUseYn>N</tmUseYn>` with empty `holStrTm` / `satStrTm` / `sunStrTm`
  elements. `OperatingHoursService` likewise has none. **MATCHES LEGACY.**
- **Request-time gate: weekday rule only, no holiday calendar.** `PymnHelper.java:3271-3275`:
  `boolean isWeekend = (dayOfWeek == 6 || dayOfWeek == 7); if (!"NAPAS".equals(transferType) && isWeekend) throw WBW1162;`
  — NAPAS runs Mon–Sun; CITAD/SWIFT Mon–Fri. Refactor: `CutoffValidator` `WBE2068`
  (`messages.properties:126`), pinned by `should_reject_with_WBE2068_when_weekend_non_napas` and
  `should_allow_weekend_napas`. **MATCHES LEGACY.**
- **No public-holiday table or check exists in either system.** Not a refactor gap.

---

## 8. Request-time cut-off, per bank

`PymnHelper.getCutoffConfig(bankCd, transferType)` (`:3344`) returns `null` for an unknown bank and
`validateTransferTiming:3281-3283` then returns — allow. Refactor: `CutoffValidator` resolves the
brand from `TB_FIRM_BANK_CODE.ENG_ABRVAT_NM` and passes unknown banks silently
(`CutoffValidator.java:27-30`), pinned by `should_bypass_for_unknown_bank`. **MATCHES LEGACY.**

Required rails, `CutoffConfigProvider.java:67-69` (fail-fast at startup if a row is missing):

```
SHINHAN → NAPAS, CITAD, CITAD_API, SWIFT
WOORI   → NAPAS, CITAD, SWIFT
VCB     → NAPAS, CITAD, CITAD_LARGE          (no SWIFT rail — VCB has no overseas)
```

Bank-specific rules, all with legacy twins:

| Rule | Legacy | Refactor |
|---|---|---|
| Shinhan CITAD 16:30 for VND→VND, else 15:30 | `PymnHelper.java:3257-3263` | `CutoffValidator` `CITAD_API` / `vndToVnd` |
| Shinhan past-cutoff allowed, except CITAD VND→VND | legacy req. 2026-04-14 | `should_log_warn_and_allow_when_past_cutoff_for_shinhan_non_vnd_to_vnd` |
| Woori / VCB always block past cutoff | `WBW1165` | `WBE2066` |
| VCB CITAD ≥ 500,000,000 VND → 16:30 | `PymnHelper.java:3291-3300` | `largeTxnThreshold()`, `should_allow_vcb_citad_large_tier_until_1630` |

---

## 9. Changes made

**No `src/main/java` file was modified.** No SQL script was created or run. No feature flag changed.

| File | Change |
|---|---|
| `wecms-api/src/test/java/…/dispatch/service/OperatingHoursServiceTest.java` | **Added** `vcb_hasNoInfoRow_bulkAndRealtime_areNoCriteria` — pins the §4.3 finding for `970436` on both legs and asserts the DTL lookup is never reached. Carries the legacy line citations and an explicit "do not seed to fix this" note. |
| `wecms-api/src/test/java/…/internaltransfer/service/InternalTransferDispatchExecutorBankChnlDivTest.java` | **Added** `vcbVnd_routesToFilePath_notApi` — `verify(tbPayFileMstRepository).save(...)` + `verify(tbPayTargetRepository, never()).save(...)`, citing `PymnHelper:2585/:2600`. |
| `wecms-api/src/test/java/…/expensepayment/service/ExpenseDispatchExecutorTest.java` | **Added** `vcbVnd_routesToFilePath_notApi` — same shape, citing `PymnHelper:2186/:2188` and the shared-helper fact from §5.2. |
| `wecms-api/docs/references/support-matrix.md` | **Rewritten** — corrected the INFO-vs-DTL error, added per-module VCB rows, request-time cut-off column, `ORG_STAT` context table, r36803 before/after, weekend findings. |

Already-correct documents left untouched (verified during this audit):
`docs/deployment/wecms-batch-schedule-seed-review.md` (`:115-116` already states INFO-missing →
FAIL, DTL-missing → pass; `:277` already flags the VCB INFO requirement; `:689` already calls the
VCB gap genuine) and `docs/features/wecms-batch/batch-schedule-operating-hours-audit.md` (`:159`,
`:161`).

### Test coverage against the required list

| Required | Where | Status |
|---|---|---|
| VCB always file path | `VendorDispatchExecutorTest.vcbVnd_routesToFilePath_notApi` (pre-existing) + IT/Expense twins | **added ×2, verified ×1** |
| VCB never realtime | same three, via `tbPayTargetRepository never()` | **covered** |
| Missing VCB file schedule criteria = legacy outcome | `OperatingHoursServiceTest.vcb_hasNoInfoRow_bulkAndRealtime_areNoCriteria` + `batch1_dtlMissing_isDispatch` + `napas_dtlMissing_isNoCriteria` | **added ×1, verified ×2** |
| VCB IT cutoff exemption | `CutoffValidatorTest.isInternalTransferCutoffExempt_should_be_true_only_for_vcb_source` | **verified** |
| VCB Vendor/Expense/Payroll/Overseas cutoff | `CutoffValidatorTest.vcb_still_blocks_past_cutoff_for_non_internal_transfer_callers`, `should_reject_vcb_citad_base_after_1530`, `should_allow_vcb_citad_large_tier_until_1630`; Payroll/Overseas blocks in their executor tests | **verified** |
| Shinhan / Woori unchanged | 10 Shinhan/Woori cases in `CutoffValidatorTest` + `OperatingHoursServiceTest` | **verified** |
| Unknown sender bank | `should_bypass_for_unknown_bank`, `realtime_unknownCode_stillFailsClosed` | **verified** |
| `ORG_STAT` active/inactive | `FirmBankingInquiryServiceTest.no_active_firm_config_returns_empty_without_delegating`; `ResultCheckServiceTest` ORG_STAT gate | **verified** |

### Test results

Targeted suites (after the change):

```
OperatingHoursServiceTest                       tests=18  failures=0  errors=0  skipped=0
InternalTransferDispatchExecutorBankChnlDivTest tests=19  failures=0  errors=0  skipped=0
ExpenseDispatchExecutorTest                     tests=15  failures=0  errors=0  skipped=0
VendorDispatchExecutorTest                      tests=23  failures=0  errors=0  skipped=0
CutoffValidatorTest                             tests=18  failures=0  errors=0  skipped=0
FirmBankingInquiryServiceTest                   tests= 4  failures=0  errors=0  skipped=0
```

Complete suite — `./gradlew test`, BUILD SUCCESSFUL in 1m 15s:

```
classes=260  tests=2055  skipped=20  failures=0  errors=0
```

---

## 10. "Supported" ≠ "enabled"

Every dispatch capability above sits behind a flag that defaults **off** —
`wecms.dispatch.auto-receive`, `wecms.dispatch.realtime-dispatch`,
`wecms.dispatch.recipient-inquiry`. None was changed. The matrix reports *configured capability*.

---

## 11. Remaining risks

| # | Risk | Needs |
|---|---|---|
| R-1 | ✅ **CLOSED as legacy parity 2026-07-29** (§11b). Legacy has no VCB INFO row; the refactor now has none either. **VCB file dispatch will FAIL (`NO_CRITERIA`) until the business explicitly decides to introduce a VCB schedule** — that is the legacy behaviour, preserved on purpose, not a regression. Residual: **PO (Mr. Jung)** may *choose* to introduce a VCB schedule as a forward business decision; if so it needs a new row **and** a new `ANT_WHERE_USE_YN` decision. **DBA:** run [`delete-vcb-batch-schedule-info-row-2026-07-29.sql`](../sql/2026/delete-vcb-batch-schedule-info-row-2026-07-29.sql) on the refactor DEV DB (not executed by this audit). |
| R-2 | ✅ **NOT APPLICABLE.** No legacy VCB INFO row exists, therefore no legacy `ANT_WHERE_USE_YN` value exists — neither `'Y'` nor `'N'`. Nothing to match, nothing to decide unless and until a VCB schedule is introduced. The earlier `'Y'` vs `'N'` analysis described a hypothetical absent from legacy and is **withdrawn**. |
| R-3 | Findings are **JEX DEV only**. UAT and PROD `TB_BATCH_SCHEDULE_INFO/_DTL` and `TB_FIRM_CONF` were not read (no MCP access, and the rules forbid touching them). **UAT/PROD DB verification** required before any deploy — re-run §12. Memory `project_batch_deployed_flags_diverge_from_repo` records that deployed batch flags already diverge from committed `.env.*`, so DEV is not a safe proxy. |
| R-4 | Legacy evaluates `EXE_DIV` in the **DB server zone (Asia/Seoul)**; the refactor uses `Asia/Ho_Chi_Minh` (`OperatingHoursService.java:68-83`). A deliberate, documented divergence — a `090000-153000` window means 09:00–15:30 KST in legacy vs ICT in the refactor. Co-hosting a legacy batch against a shared UAT/PROD DB needs `wecms.dispatch.operating-hours.zone=Asia/Seoul`. **Deployment-time validation.** |
| R-5 | SVN revision **r36803** is cited from in-code comments and corroborated by the dated pre-change backup; the SVN server was not queried. **Infrastructure confirmation** if the exact revision number matters for a release note. |
| R-6 | Payroll `ANT_DIV_CD` defect (**F-1, HIGH, open**) — `PayrollDispatchExecutor` never sets `ANT_DIV_CD`, so Payroll `presentChannels` is empty → `evaluateBulk` → `SKIP` → files stall at `PROC_STAT='9'` silently on Shinhan and Woori. Out of scope for this audit (VCB Payroll is blocked before reaching it), but it gates any Payroll dispatch enablement. See [../audits/payment-dispatch-operating-hours-flow.md](../audits/deployment/payment-dispatch-operating-hours-flow.md) §3.3. |

---

## 11a. R-1 … R-6 follow-up audit (2026-07-29)

Each §11 risk was re-audited against legacy source, SVN history, and both reachable databases.
**One production-code change resulted, and it is comment-only** (R-5 revision correction). Two of my
own §11 statements were **wrong** and are corrected here.

| Risk | Legacy behaviour | Refactor before | Refactor after | Verdict |
|---|---|---|---|---|
| **R-1** | JEX DEV has **no** `970436` INFO row → VCB file dispatch `NO_CRITERIA` → FAIL. SVN proves the VCB switch (**r36822**) shipped **PymnHelper.java only** — no schedule script in r36795–36824. | Code legacy-faithful. Refactor dev DB carried a `970436` INFO row (`ant_where_use_yn='N'`) — a refactor-only addition. | **Row removed** (rollback script + seed tuple commented out). Bank set is now `970424` / `970457` only — identical to legacy. **Java unchanged.** | ✅ **MATCHES LEGACY** — reverted to exact parity 2026-07-29. See §11b. |
| **R-2** | **No legacy row exists → no legacy `ANT_WHERE_USE_YN` value exists.** Neither `'Y'` nor `'N'`. The column is plain data, never hard-coded or derived anywhere in legacy. | `'N'` had been applied on the refactor dev DB. | No VCB row, therefore **no value**. | ✅ **NOT APPLICABLE** — there is nothing to match. See §11b. |
| **R-3** | JEX DEV: INFO 4 rows (2 batch ids × 970424/970457); DTL 12 rows; `tb_firm_conf` 970424/970436/970457 all `ORG_STAT='1'`. | Refactor dev DB: INFO **3** rows (all `NAPAS_REAL_TIME_BATCH`, incl. 970436); DTL 12; `tb_firm_conf` **970424 only** (3 rows, `ORG_STAT='1'`). | unchanged | **DEPLOYMENT VERIFICATION ONLY** — UAT/PROD unreachable. Two DEV deltas recorded below. |
| **R-4** | Stored windows are **Vietnam local time** — proven: `getCutoffConfig:3352-3374` request-time windows (Vietnam clock) are the *same numbers* as the DTL rows. Legacy nonetheless evaluates them on an `Asia/Seoul` DB → closes **2 h early**. | `Asia/Ho_Chi_Minh`, knob `wecms.dispatch.operating-hours.zone`. | unchanged | **MATCHES LEGACY-INTENT — intentional divergence retained** + **DEPLOYMENT VERIFICATION ONLY** for co-hosting. 5 boundary/zone tests added. |
| **R-5** | **r36822** \| soknyran \| 2026-07-16 12:05:39 +0700 \| redmine 43078 \| `M /WECMS/src/wabooks/helper/PymnHelper.java` — **only**. `r36803` is a *different* commit (ouddom, 2026-07-07, redmine 43104). | 6 files cited **r36803**. | corrected to **r36822** | **MISMATCH — FIXED** (comment-only). |
| **R-6** | `pymn_0005_001_c001_act.jsp:214-227` 4-branch rule + `:256-259` Woori override; narrowing via **`AntDivCdT.parse`** (lenient — 1-char *and* long form, case-insensitive, `null` for unknown). Detail source is `AntDivCd.CITAD` = `"CITAD"` (5-char). | **Already fixed** — `PayrollDispatchExecutor:224-256` ports all 4 branches + override via `AntDivChannel.toStorageCode`. F-2 and F-3 also already fixed. | unchanged | **MATCHES LEGACY — NO CHANGE.** My §11 R-6 entry was a **stale citation**, not a live defect. |

### R-1 / R-2 — reverted to exact legacy parity (2026-07-29)

**Legacy has no VCB INFO row. The refactor intentionally preserves this behaviour.**

Re-verified before changing anything:

| Check | Result |
|---|---|
| Legacy JEX DEV `tb_batch_schedule_info` | 4 rows — `970424` + `970457` under both batch ids. **No `970436`.** |
| Flyway migration inserts it? | **No.** `V95__tb_batch_schedule_dtl.sql` / `V96__tb_batch_schedule_info.sql` are DDL only (0 `INSERT`s). Flyway is disabled anyway. |
| Legacy SQL / XML / properties / deployment script inserts it? | **No.** The only legacy `TB_BATCH_SCHEDULE` hits across `WECMS DEV/`, `WECMS_BATCH DEV/`, `DEV ADMIN/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/` are the two IDO query definitions. |
| SVN | **r36822** (the VCB file-transfer switch) committed `PymnHelper.java` **only**; `svn log -r 36795:36825 -v` contains no `.sql` / schedule / DB file. |
| Runtime code depends on it? | **No.** No hard-coded `970436` anywhere in `domain/payment/dispatch/`; `OperatingHoursService` resolves the bank generically. |

**Before / after:**

```
LEGACY (JEX DEV)      NAPAS_REAL_TIME_BATCH × (970424 'Y', 970457 'Y')   → no 970436
refactor DEV BEFORE   NAPAS_REAL_TIME_BATCH × (970424 'Y', 970436 'N', 970457 'Y')   ← divergence
refactor DEV AFTER    NAPAS_REAL_TIME_BATCH × (970424 'Y', 970457 'Y')   → no 970436  ✅ parity
```

**What changed:** the row is removed by
[`docs/sql/2026/delete-vcb-batch-schedule-info-row-2026-07-29.sql`](../sql/2026/delete-vcb-batch-schedule-info-row-2026-07-29.sql)
(DBA-run by hand; **not executed by this audit**), and the seed script's VCB tuple is **commented out**
so a re-run cannot reintroduce it. `970424`, `970457`, all 12 DTL rows, `tb_firm_conf`, and **all
production Java** are untouched.

**Accepted consequence:** a VCB pay-file reaching the dispatch-time gate resolves to `NO_CRITERIA` and
is **FAILED** — `OperatingHoursService:168-170` → `BulkReceptionService:246-263` (`PROC_STAT=FAIL` +
`대량이체 Batch 수행 기준 없음`), byte-for-byte the legacy outcome at
`FILE_DEPOSIT_TRANSFER_BATCH1.java:284-291`.

> **Legacy parity intentionally preserved.**
>
> **VCB dispatch will fail until the business explicitly decides to introduce a VCB schedule.**

This is **not** a regression and the removed row was **not** the correct permanent configuration — it
was a refactor-only addition that diverged from legacy. Reviving it is a PO (Mr. Jung) decision.

**R-2 is NOT APPLICABLE.** With no legacy row there is no legacy `ANT_WHERE_USE_YN` value — neither
`'Y'` nor `'N'`. Any earlier text in this or other documents weighing `'Y'` against `'N'` for VCB
described a hypothetical that legacy does not contain, and is withdrawn. If a VCB schedule is ever
introduced, the value is a fresh business decision, not a copy.

### R-3 — DEV-to-DEV deltas worth flagging

1. **Refactor DB has no `FILE_DEPOSIT_TRANSFER_BATCH1` INFO rows.** Harmless — *both* gates read INFO
   under `NAPAS_REAL_TIME_BATCH` only (legacy `FILE_DEPOSIT_TRANSFER_BATCH1.java:272`,
   `NAPAS_REAL_TIME_BATCH.java:113`), so JEX DEV's 2 extra rows are dead data (seed review §1 item 1).
2. **Refactor `tb_firm_conf` carries only `970424`.** A VCB dispatch on the refactor dev DB therefore
   fails earlier — at the firm-conf lookup (`BulkReceptionService:189-190`) — and never reaches the
   operating-hours gate. R&D seed scope, but it means the R-1 divergence is **not currently
   observable** on this DB.

### R-4 — proof that the stored windows are Vietnam time

`current_setting('TimeZone')` on JEX DEV = **`Asia/Seoul`**; `to_char(now(),'HH24MISS')` returned
`120105` while ICT was `100105` (2026-07-29). On the refactor DB the zone is `Asia/Phnom_Penh` (UTC+7).

The decisive evidence is numeric equality between the two gates:

| Bank · network | `getCutoffConfig` (Vietnam clock) | `TB_BATCH_SCHEDULE_DTL` |
|---|---|---|
| Shinhan NAPAS | 00:05–23:45 | `000500`–`234500` |
| Woori NAPAS | 00:15–23:55 | `001500`–`235500` |
| Shinhan CITAD | 09:00–15:30 | `090000`–`153000` |
| Woori CITAD | 08:30–17:40 | `083000`–`174000` |
| Woori SWIFT | 08:30–19:10 | `083000`–`191000` |

Same numbers. The request-time gate applies them in **Vietnam** time
(`DateTimeUtils.gmtNowVanCenter()`), so the schedule values are Vietnam cut-offs. Evaluating them in
KST — as legacy's `Asia/Seoul` DB does — closes the window 2 h early and contradicts the request-time
gate that just accepted the payment. **ICT is the correct business reading; legacy's KST evaluation is
a legacy bug.** Keep `Asia/Ho_Chi_Minh`; use `wecms.dispatch.operating-hours.zone=Asia/Seoul` only for
bug-for-bug parity beside a co-hosted legacy batch.

> Side finding (not actioned): legacy Shinhan **SWIFT** disagrees with itself —
> `getCutoffConfig:3369-3370` returns 09:00–**22:00** while its own comment and the DTL row say
> **23:45** (`090000`–`234500`). Pre-existing legacy inconsistency, already tracked as seed-review
> **OQ-3**. No refactor action.

### R-5 — SVN, verified

```
$ svn info                      # svn://studio30.jexframe.com:3693/WECMS @ r36805 (working copy)
$ svn log -l 12 -v WECMS/src/wabooks/helper/PymnHelper.java
r36822 | soknyran | 2026-07-16 12:05:39 +0700 | M /WECMS/src/wabooks/helper/PymnHelper.java
        https://www.kosign.com.kh/redmine/issues/43078          ← the VCB file-transfer switch
r36803 | ouddom   | 2026-07-07 17:33:15 +0700 | M /WECMS/src/wabooks/helper/PymnHelper.java
        https://www.kosign.com.kh/redmine/issues/43104          ← a DIFFERENT change
$ svn log -r 36795:36825 -v     # no .sql / schedule / DB file in the entire range
```

Two consequences: (a) the revision was misattributed in 6 refactor files — now corrected; (b) **no DB
or schedule script accompanied the VCB switch**, which is direct evidence that the missing VCB INFO
row (R-1) is an oversight rather than a deliberate "VCB must not dispatch" decision.

Superseded `r36803` mentions remain in three dated historical audits
(`audits/partner-parity-gap-matrix-audit.md:112`,
`audits/wecms-batch-refactor-parity-audit-2026-07-21.md:48`) — left as-written records; **this section
is the authoritative revision reference.**

### R-6 — already fixed; the required mapping, verified

`AntDivCdT.parse` (legacy, `AntDivCdT.java:50-72`) is **lenient**: `fromValue` (1-char `N/C/S/E/V`)
then case-insensitive long form (`napas`/`citad`/`swift`/`ebank`/`vcb`), else `null`.
`AntDivCd` (the 5-char detail enum) stores `CITAD("CITAD")`, so Payroll's detail value —
`pymn_0005_002_c001_act.jsp:118,125` `setAntDivCd(AntDivCd.CITAD)` — is `"CITAD"`, and
`parse("CITAD")` → `CITAD` → **`'C'`**.

| Case | Legacy | Refactor | Test |
|---|---|---|---|
| Shinhan VND (detail `CITAD`) | `'C'` (`:225-226` via `parse`) | `'C'` | `shinhanVnd_citad_narrowsToC` |
| Shinhan VND (detail `NAPAS`) | `'N'` | `'N'` | `shinhanVnd_napas_narrowsToN` |
| Shinhan FCY | `'E'` (`:215-217`) | `'E'` | `shinhanFcy_isEbank` |
| Woori FCY | `'V'` (`:218-220`) | `'V'` | `wooriFcy_isVcbChannel` |
| Woori VND same-bank | `null` (`:256-259`) | `null` | `wooriVnd_sameBank_isNull_preservedLegacyBehaviour` |
| Woori VND other-bank | uploaded code | uploaded code | `wooriVnd_otherBank_keepsUploadedCode` |
| VCB VND | **blocked** (`:179-182`) | `WBE_PAYROLL_VCB_VND_NOT_SUPPORTED` | `PayrollDispatchExecutorBankPrtnIdTest:235-237` |
| VCB USD same-bank | `null` (`:221-223`) | `null` | `vcbFcy_isNull_preservedLegacyBehaviour` |
| VCB USD other-bank | **blocked** (`:249-252`) | `WBE_PAYROLL_VCB_USD_OTHER_BANK` | `PayrollDispatchExecutorBankPrtnIdTest:245-247` |
| Unknown / `INTER` / blank | `null` (never guessed) | `null` | `unknownCode_isNull_notGuessed`, `interCode_isNull_notAFileChannel`, `nullAndBlankDetailCode_areNull` |
| Padded / lowercase input | accepted | accepted | `bpcharPadded*`, `lowercaseInput_isAccepted` |

The **lenient vs strict** split is load-bearing and correctly preserved: Payroll uses
`AntDivChannel.toStorageCode` (≡ `parse`, accepts long form) because legacy Payroll calls `parse`
explicitly; Vendor / Expense / Internal Transfer use `toStorageCodeStrict` (≡ `fromValue`, 1-char
only) because legacy reaches those through `copyFrom` → `TbPayFileData.java:301`. Substituting one
for the other would corrupt one module or the other — documented at `AntDivChannel.java:197-226`.

**Silent-`SKIP` sub-item, deliberately not changed.** When every row of a file has a null channel the
set is empty and `evaluateBulk` returns `SKIP` — file stays `PROC_STAT='9'`, no FAIL, no alert. That
is legacy behaviour (`FILE_DEPOSIT_TRANSFER_BATCH1.java:445` `!exeYN` → `continue`) and is preserved
per the parity rule; `BulkReceptionService:241-243` does log it at INFO. Whether a perpetual silent
WAIT is *operationally* acceptable is open question **Q-4** in
[../audits/payment-dispatch-operating-hours-flow.md](../audits/deployment/payment-dispatch-operating-hours-flow.md)
— a business decision, not a parity defect. Only reachable now via the two legitimate legacy null
branches (VCB FCY same-bank, Woori VND same-bank) or an unrecognised uploaded code.

---

## 12. Reproduction queries

```sql
-- Sender set + status
SELECT out_bank_cd, count(*) conf_rows, count(*) FILTER (WHERE org_stat='1') active_rows
FROM tb_firm_conf GROUP BY 1 ORDER BY 1;

-- Names
SELECT bank_cd, eng_abrvat_nm, bank_nm, bank_stas
FROM tb_firm_bank_code
WHERE bank_cd IN ('970424','970436','970457') AND ant_div_cd = 'NAPAS';

-- Operating-hours coverage — the VCB gap shows as absent rows
SELECT batch_id, out_bank_cd, ant_where_use_yn, batch_start_tm, batch_end_tm
FROM tb_batch_schedule_info ORDER BY 1,2;

SELECT batch_id, out_bank_cd, count(*) dtl_rows,
       string_agg(DISTINCT trim(ant_div_cd), ',' ORDER BY trim(ant_div_cd)) channels
FROM tb_batch_schedule_dtl GROUP BY 1,2 ORDER BY 1,2;

-- Scope check: destination registry is not the capability set
SELECT ant_div_cd, count(*) rows, count(DISTINCT eng_abrvat_nm) brands
FROM tb_firm_bank_code GROUP BY 1;   -- CITAD 3137/101, NAPAS 68/68
```
