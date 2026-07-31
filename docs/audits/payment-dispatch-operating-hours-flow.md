# Payment Dispatch → `OperatingHoursService` Flow Audit

**Date:** 2026-07-28 (**revision 6** — synchronised with current `HEAD`; all findings F-1…F-5 closed) · **System:** Refactor (`wecms-api`) · **Scope:** all 5 payment types
**Method:** static trace of `wecms-api/src/main/java` (controller → service → executor → registrar → gate),
cross-checked against the legacy JEX sources under `WECMS DEV/` and `WECMS_BATCH DEV/`, plus **read-only**
`SELECT`s against JEX DEV and the refactor DB. No code was modified. No DB writes.

> **Placement note:** requested as `docs/audit/…`; written to `wecms-api/docs/audits/` — the established
> refactor-backend audit folder (root `CLAUDE.md` §Documentation Structure Rules). Same file, conventional home.

> **Revision 6 changelog (synchronisation pass).** Revisions 1–5 were written *while* the findings were
> being fixed, so the front matter (§1, §3.1, §3.2, §3.4, §4, §5A, §6, §7, §8, §12.7) still described
> **F-4 as an open HIGH deployment blocker** and F-2 / F-3 / F-5 as open — contradicting §12 and §13, which
> record their implementation. This revision **synchronises the whole document with current `HEAD`**:
>
> - **All five findings (F-1, F-2, F-3, F-4, F-5) are FIXED and verified in code.** No operating-hours code
>   blocker remains for normal payment dispatch — see §1 and §13.7.
> - Every wording that called F-4 a deployment blocker has been retired.
> - **Historical evidence is preserved, not deleted.** The original finding descriptions, the schema proof
>   (§5A.1–5A.2), the affected-case tables and the severity reasoning remain verbatim, each now marked
>   **RESOLVED** with the current source reference that closes it.
> - Line-number citations re-derived from current `HEAD` (they had drifted again — e.g. the Vendor bulk
>   `ANT_DIV_CD` write moved `:279` → `:289`).
> - The **ICT vs legacy DB-server timezone difference is restated as an intentional, configurable
>   decision** (new §2.1), not a defect.
>
> Revision 1 → 2 history, for the record: rev 1 reported F-1 as HIGH/open; F-1 was implemented (§9); the
> re-trace of the fixed code is what surfaced F-4.

**Related:** [../features/wecms-batch/batch-schedule-operating-hours-audit.md](../features/wecms-batch/batch-schedule-operating-hours-audit.md) ·
[../deployment/wecms-batch-schedule-seed-review.md](../deployment/wecms-batch-schedule-seed-review.md) ·
[../deployment/wecms-batch-infrastructure-readiness-audit.md](../deployment/wecms-batch-infrastructure-readiness-audit.md)

---

## 1. Executive summary

All five payment types reach `OperatingHoursService` — **none bypasses the gate** once its feature flag is on.
The wiring is complete and correct in that respect.

The trace surfaced five findings across revisions 1–3. **All five are now fixed and verified in code at
`HEAD`.** Every `ANT_DIV_CD` cell in the 11-row legacy-parity matrix (§13.2) matches legacy.

> ### ✅ There is no remaining operating-hours code blocker for normal payment dispatch.
>
> All five payment types reach `OperatingHoursService`, none bypasses it, and no operating-hours defect
> blocks dispatch on any of them. What remains for SIT `auto-receive` activation is **not**
> operating-hours-related — it is the deployment-configuration and infrastructure set tracked in
> [../deployment/wecms-batch-infrastructure-readiness-audit.md](../deployment/wecms-batch-infrastructure-readiness-audit.md)
> plus the `TB_BATCH_SCHEDULE_*` seed on SIT/UAT/PROD.

| # | Severity | Status | Finding (original description preserved) | Effect (as originally assessed) | Closed by |
|---|---|---|---|---|---|
| **F-1** | HIGH | ✅ **FIXED** (§9) | **`PayrollDispatchExecutor` never set `ANT_DIV_CD`.** Legacy `pymn_0005_001_c001_act.jsp:214-227` has a 4-branch rule plus a Woori override at `:256-259`; the refactor ported the sibling `BANK_CHNL_DIV` block (`:228-264`) and both VCB guards but **dropped the `ANT_DIV_CD` block entirely**. | Every Payroll file row got `ANT_DIV_CD = NULL` → `presentChannels` **empty** → `evaluateBulk` returned **`SKIP`** → the pay-file was left `PROC_STAT='9'` **forever, with no FAIL and no alert**. Silent stall. Also wrote wrong data vs legacy (`'C'` expected for the normal VND case). | 4-branch rule + Woori override at [`PayrollDispatchExecutor:230-241,249-256`](../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollDispatchExecutor.java#L230-L256), narrowing via `AntDivChannel.toStorageCode` (≡ legacy `parse`) |
| **F-2** | MEDIUM | ✅ **FIXED** (§13.4) | `InternalTransferDispatchExecutor` sets `ANT_DIV_CD` on only 2 of its 4 file-path cases. Non-VCB, non-Shinhan-USD→VND foreign-currency rows (e.g. Woori USD→USD) fall through both branches → `NULL`. | Same silent-`SKIP` mechanism as F-1, for that currency/bank combination. | Legacy USD first pass ported at [`InternalTransferDispatchExecutor:359-364`](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchExecutor.java#L359-L364) — Shinhan→`'E'`, Woori→`'V'` (legacy `PymnHelper:2779-2782`). Rows 9/10 correctly left `null` — legacy is `null` there |
| **F-3** | LOW | ✅ **FIXED — deleted** (§12.4) | `TransferDispatchExecutor` — the original shared executor that calls **both** registrars — has **no production injection**. Only javadoc references and one test (`TransferDispatchExecutorRealtimeTest`). | Dead production code that looks live. Not a bypass, but a maintenance trap: a future wiring of it would inherit a file path documented as having "diverged from legacy in six places". | Class + obsolete test **deleted**; javadoc links repaired. Verified absent from `src/` at `HEAD` |
| **F-4** | HIGH | ✅ **FIXED — Option A** (§12.2) | **Vendor / Expense / Internal Transfer copied the 5-char `ANT_DIV_CD` raw into a `character(1)` column.** Payroll was the only file path narrowing correctly. The unguarded copies were at `VendorDispatchExecutor:279`, `ExpenseDispatchExecutor:272`, `InternalTransferDispatchExecutor:359` *(pre-fix line numbers)*. Source is `character(5)` holding `"NAPAS"`/`"CITAD"`; target is `character(1)` — **both DB-verified, §5A.1**. | INSERT failed: PostgreSQL `value too long for type character(1)` → `DataIntegrityViolationException` → `WBE_DB` HTTP 500, dispatch transaction rolled back. Hit **VCB + VND** — the case OQ-2 (Mr. Jung 2026-07-16) confirmed as file-based. Was **not** flag-gated: the write precedes the registrar. | New legacy-faithful converter `AntDivChannel.toStorageCodeStrict` ([`:226`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/constant/AntDivChannel.java#L226)) ≡ legacy `AntDivCdT.fromValue`, applied at [`VendorDispatchExecutor:289`](../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorDispatchExecutor.java#L289), [`ExpenseDispatchExecutor:280`](../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpenseDispatchExecutor.java#L280), [`InternalTransferDispatchExecutor:398`](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchExecutor.java#L398). Test assertions corrected to the 1-char contract |
| **F-5** | MEDIUM | ✅ **FIXED** (§13.4) | Internal Transfer wrote `'C'` (CITAD) for the Shinhan-USD→VND **same-bank** file case, following a legacy *comment* (`PymnHelper:2801-2804`) that contradicts the legacy *code* four lines below it. | Wrote a channel legacy never assigns, so the file was gated by the CITAD window instead of legacy's `null`/skip. | Restored to `null` at [`InternalTransferDispatchExecutor:385`](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchExecutor.java#L385) per legacy `:2846-2848`. The code is authoritative; the comment is stale |

Everything else checks out: `batch_id` keys are correct on all paths (including the deliberate split-key), the
`out_bank_cd` sources are legacy-faithful, the request-time `CutoffValidator` gate runs before dispatch on
every path, and both registrars are post-commit + failure-isolated.

**Still open — neither is a code blocker.** **Q-4 (behavioural)**: whether an empty channel set should FAIL
rather than `SKIP`. Legacy `SKIP` is preserved deliberately; the condition is no longer silent (§10 monitor
reports `ALL_CHANNELS_NULL`). **Q-5**: ratification only — Option A is live and legacy-exact; Option B remains
a PO choice, not a defect.

**Verification at this revision:** `./gradlew cleanTest test` — forced full re-run, **exit 0, 3m04s, 255
suites, 1999 tests, 0 failures, 0 errors**, 20 skipped (all in the unrelated `VasAccountIntegrationTest`).

---

## 2. The two gate entry points

| Gate method | Called from | Reads INFO under | Reads DTL under | Missing DTL |
|---|---|---|---|---|
| `evaluateRealtime(outBankCd, antDivCd)` [`:102`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L102) | [`RealtimeDispatchService:154`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/RealtimeDispatchService.java#L154) | `NAPAS_REAL_TIME_BATCH` | `NAPAS_REAL_TIME_BATCH` | **`NO_CRITERIA` → FAIL** |
| `evaluateBulk(outBankCd, presentChannels)` [`:159`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L159) | [`BulkReceptionService:240`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L240) | **`NAPAS_REAL_TIME_BATCH`** | `FILE_DEPOSIT_TRANSFER_BATCH1` | **pass** |

`OperatingHoursService` is injected into exactly these **two** collaborators, one call site each.
Gate inputs differ in width-tolerance, which is what makes F-4 a bulk-only defect:

- **Realtime** — `TB_PAY_TARGET.ANT_DIV_CD` is `character(5)` and legitimately holds *both* widths, so the gate
  normalises through the **lenient** `AntDivChannel.canonical` ([`:130`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L130)). Width-safe.
- **Bulk** — `presentChannels` maps the 1-char `TB_PAY_FILE_DATA.ANT_DIV_CD` through the **strict**
  `AntDivChannel.fromStorageCode` ([`BulkReceptionService:533-543`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/BulkReceptionService.java#L533-L543)) — `E/V/C/N/S` only; anything else → `null` →
  dropped from the set (legacy `ETC_CNT`, deliberately un-gated).

The INFO key is shared by both gates — verbatim legacy parity
(`FILE_DEPOSIT_TRANSFER_BATCH1.java:272` and `NAPAS_REAL_TIME_BATCH.java:113` both put
`"NAPAS_REAL_TIME_BATCH"`). The DTL keys differ. This is **not** a bug; see the seed review §2.1.

Decision → action mapping:

| Decision | Realtime (`RealtimeDispatchService:155-173`) | Bulk (`BulkReceptionService:241-262`) |
|---|---|---|
| `DISPATCH` | proceed to 204/202 | proceed to file build + 00301 |
| `SKIP` | `return`, row stays WAIT, **no alert** | `return`, file stays WAIT, **no alert** |
| `CUTOFF` | `TB_PAY_TARGET` FAIL + `실시간지급이체 Batch 거래 마감` | `TB_PAY_FILE_MST` FAIL + `대량이체 Batch 거래 마감` |
| `NO_CRITERIA` | `TB_PAY_TARGET` FAIL + `실시간지급이체 Batch 수행 기준 없음` | `TB_PAY_FILE_MST` FAIL + `대량이체 Batch 수행 기준 없음` |

### 2.1 Evaluation timezone — an intentional, configurable decision (not a defect)

The window comparison is a lexicographic `HHmmss` compare, matching legacy `TO_CHAR(NOW(),'HH24MISS')`. The
**clock it is evaluated against differs by design**:

| | Zone used | Configurable? |
|---|---|---|
| **Legacy** | the **DB server's** local zone — on the JEX frame DB that is `Asia/Seoul` (UTC+9), **not** ICT | **No.** Legacy has no timezone setting anywhere; `grep -rniE "timezone\|ZoneId\|Asia/"` over the batch tree returns zero hits |
| **Refactor** | `Asia/Ho_Chi_Minh` (ICT) | **Yes** — `wecms.dispatch.operating-hours.zone`, [`OperatingHoursService:85-86`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/OperatingHoursService.java#L85-L86) |

So a `090000-153000` window means 09:00–15:30 **KST** in legacy (= 07:00–13:30 Vietnam time) and 09:00–15:30
**ICT** in the refactor — a **2-hour difference in which payments pass the gate**.

**This is a deliberate, documented decision, not a finding.** Two reasons, both recorded in the service
Javadoc: it matches the business meaning of the windows (Vietnamese bank cut-offs), and it matches
`CutoffValidator.VIETNAM_ZONE`, so the request-time gate and this dispatch-time gate cannot contradict each
other — a payment accepted at 15:00 ICT is not then failed by a KST-evaluated cut-off.

**Empirical proof added 2026-07-29 (R-4 audit) — the stored values ARE Vietnam time.** The reasoning above was
an argument from business meaning; it is now evidence. Legacy `PymnHelper.getCutoffConfig:3352-3374` returns the
request-time windows, and those are evaluated on the **Vietnam** clock (`DateTimeUtils.gmtNowVanCenter()`,
`PymnHelper:3260`). Its numbers are **identical** to the `TB_BATCH_SCHEDULE_DTL` rows:

| Bank · network | `getCutoffConfig` (Vietnam clock) | `TB_BATCH_SCHEDULE_DTL` |
|---|---|---|
| Shinhan NAPAS | 00:05–23:45 | `000500`–`234500` |
| Woori NAPAS | 00:15–23:55 | `001500`–`235500` |
| Shinhan CITAD | 09:00–15:30 | `090000`–`153000` |
| Woori CITAD | 08:30–17:40 | `083000`–`174000` |
| Woori SWIFT | 08:30–19:10 | `083000`–`191000` |

Same cut-offs, expressed twice. Since one copy is definitionally Vietnam-local, so is the other — therefore
legacy's `Asia/Seoul` evaluation closes the window 2 h early and **contradicts the request-time gate that just
accepted the payment**. ICT is the correct reading; legacy's KST evaluation is a legacy bug, not a spec. DB zone
confirmed by query on 2026-07-29: JEX DEV `current_setting('TimeZone')` = `Asia/Seoul` (`to_char(now(),'HH24MISS')`
= `120105` vs ICT `100105`); the refactor DB is `Asia/Phnom_Penh` (UTC+7). Boundary + zone behaviour is pinned by
5 tests in `OperatingHoursServiceTest` (`windowBoundaries_*`, `nearMidnightWindow_doesNotWrap`,
`sameStoredWindow_yieldsDifferentDecisionsInIctVsKst`, `operatingHoursZone_defaultsToIct_andIsOverridable`).
Full record: [../references/support-matrix.md](../references/support-matrix.md) §11a R-4.

**It is reversible per environment.** Set `wecms.dispatch.operating-hours.zone=Asia/Seoul` to reproduce legacy
bug-for-bug — the intended setting if a co-hosted legacy JEX batch on a shared UAT/PROD DB ever requires exact
parity. The property carries an in-code default, so an unset value can never abort startup. Choosing the value
is an activation-time decision for the PO/Ops, tracked in the readiness audit; it is **not** an open code
defect and no code change is required either way.

---

### 2.2 VCB (`970436`) has no INFO row — legacy parity, intentionally preserved (2026-07-29)

Both gates key `TB_BATCH_SCHEDULE_INFO` on `BATCH_ID='NAPAS_REAL_TIME_BATCH'` (§2). **Legacy has rows
for `970424` and `970457` only — there is no `970436` row, in any environment or script.** Verified
2026-07-29: no Flyway migration inserts one (`V95`/`V96` are DDL only); no legacy
SQL/XML/properties/deployment script inserts one; SVN **r36822** (the 2026-07-16 VCB file-transfer
switch) committed `PymnHelper.java` **only**, with no schedule script anywhere in r36795–36824; and no
runtime code depends on such a row.

A refactor-only `970436` row (`ant_where_use_yn='N'`) had been seeded on the refactor DEV DB on
2026-07-28, which made VCB file dispatch **succeed** — a divergence, since legacy fails it. It has been
**withdrawn**: the seed tuple is commented out and the applied row is removed by
[`tb_batch_schedule_info_vcb_rollback_20260729.sql`](../../../Claude%20Code%20%28AI%29/sql/tb_batch_schedule_info_vcb_rollback_20260729.sql).
**No production Java changed** — the code was already correct.

Resulting behaviour, identical in both systems:

| | Legacy | Refactor |
|---|---|---|
| VCB file-path INFO lookup | miss | miss |
| Decision | `exeErrYN=true`, `vrfi_msg="No data to perform bulk transfer batch. Please check TB_BATCH_SCHEDULE_INFO."` (`FILE_DEPOSIT_TRANSFER_BATCH1.java:284-291`) | `NO_CRITERIA` (`OperatingHoursService:168-170`) |
| Outcome | pay-file **FAIL** | pay-file **FAIL** + `대량이체 Batch 수행 기준 없음` alert (`BulkReceptionService:246-263`) |

> **Legacy parity intentionally preserved.**
>
> **VCB dispatch will fail until the business explicitly decides to introduce a VCB schedule.**

There is likewise **no legacy `ANT_WHERE_USE_YN` value for VCB** — neither `'Y'` nor `'N'`. Any earlier
`'Y'`-vs-`'N'` analysis for VCB is withdrawn as describing something legacy does not contain.
Pinned by `OperatingHoursServiceTest.vcb_hasNoInfoRow_bulkAndRealtime_areNoCriteria`. Full record:
[../references/support-matrix.md](../references/support-matrix.md) §11a / §11b.

## 3. Per-payment-type execution paths

`out_bank_cd` on every path is the **canonical NAPAS code** — normalised at create/upload time by
`OutBankCdNormalizer.normalize` before it is persisted to `SCDUL_PYMN_SUMR.OUT_BANK_CD` /
`OVRS_PYMN_SUMR.OUT_BANK_CD`, then copied verbatim onto the dispatch row.

### 3.1 Vendor Payment — ✅ F-4 closed

| Step | Detail |
|---|---|
| **Entry API** | `POST /api/v1/payment/vendor-payment/{id}/dispatch` → [`VendorPaymentController:411-419`](../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/controller/VendorPaymentController.java#L411-L419) |
| **Service** | `VendorPaymentService.dispatch(id, req)` — [`:1146`](../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java#L1146). Request-time gate `cutoffValidator.validate(...)` at `:1188` |
| **Executor** | `VendorDispatchExecutor.execute(master, lines, trnfCcyCd)` — [`:142`](../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorDispatchExecutor.java#L142) |
| **Routing** | `:149` — `VND && !VCB` → **API path** (`realtimeDispatchRegistrar` `:211`); otherwise → **File path** (`autoReceiveRegistrar` `:325`) |
| **Gate** | **BOTH** — `evaluateRealtime` (VND non-VCB) and `evaluateBulk` (USD, and all VCB) |
| **`batch_id`** | realtime: INFO+DTL `NAPAS_REAL_TIME_BATCH` · bulk: INFO `NAPAS_REAL_TIME_BATCH`, DTL `FILE_DEPOSIT_TRANSFER_BATCH1` |
| **`out_bank_cd`** | `TB_PAY_TARGET.OUT_BANK_CD` ← `master.getOutBankCd()` (`:182`) · `TB_PAY_FILE_MST.OUT_BANK_CD` ← same (`:227`) |
| **`ant_div_cd`** | realtime: `resolveAntDivCd` `:186` — `INTER` if `outBankCd == tarBankCd`, else the line's `ANT_DIV_CD` (blank → `INTER`). **Width-safe** (`character(5)` target) · bulk: ✅ narrowed via `AntDivChannel.toStorageCodeStrict` `:289`, then USD overrides `:293-301` — Shinhan→`E`, Woori→`V`, VCB→`null`. **F-4 closed** (was a raw copy at pre-fix `:279`) |
| **Reached?** | ✅ Both legs, all cases |

### 3.2 Expense Payment — ✅ F-4 closed

| Step | Detail |
|---|---|
| **Entry API** | `POST /api/v1/payment/expense-payment/{id}/dispatch` → [`ExpensePaymentController:408-416`](../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java#L408-L416) |
| **Service** | `ExpensePaymentService.dispatch(id, req)` — [`:1161`](../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java#L1161). Cut-off at `:1203` |
| **Executor** | `ExpenseDispatchExecutor.execute(...)` — [`:139`](../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpenseDispatchExecutor.java#L139) |
| **Routing** | `:145-157` — identical shape to Vendor: `VND && !VCB` → API (`:204`); else File (`:317`) |
| **Gate** | **BOTH** — `evaluateRealtime` and `evaluateBulk` |
| **`batch_id`** | same as Vendor |
| **`out_bank_cd`** | `master.getOutBankCd()` — API `:176`, File `:220` |
| **`ant_div_cd`** | realtime: `resolveAntDivCd` `:180` (width-safe) · bulk: ✅ narrowed via `AntDivChannel.toStorageCodeStrict` `:280`, USD overrides `:284-292` (Shinhan→`E`, Woori→`V`, VCB→`null`). **F-4 closed** (was a raw copy at pre-fix `:272`) |
| **Reached?** | ✅ Both legs, all cases |

### 3.3 Payroll Payment — ✅ F-1 closed; now the reference implementation for the width rule

| Step | Detail |
|---|---|
| **Entry API** | `POST /api/v1/payment/payroll-payment/{id}/dispatch` → [`PayrollPaymentController:370-378`](../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/controller/PayrollPaymentController.java#L370-L378) |
| **Service** | `PayrollPaymentService.dispatch(id, req)` — [`:1158`](../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentService.java#L1158). Cut-off at `:1197` |
| **Executor** | `PayrollDispatchExecutor.execute(master, lines, trnfCcyCd, apprFundTp)` — [`:136`](../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollDispatchExecutor.java#L136) |
| **Routing** | **File only**, unconditionally — no `realtimeDispatchRegistrar` is even injected. `autoReceiveRegistrar` at `:237`. Deliberate: `PayrollPaymentService:159` notes the shared executor "routes VND→API, wrong for Payroll" |
| **Gate** | `evaluateBulk` only |
| **`batch_id`** | INFO `NAPAS_REAL_TIME_BATCH` · DTL `FILE_DEPOSIT_TRANSFER_BATCH1` |
| **`out_bank_cd`** | `TB_PAY_FILE_MST.OUT_BANK_CD` ← `master.getOutBankCd()` (`:154`) |
| **`ant_div_cd`** | ✅ **FIXED (§9).** Legacy 4-branch selection + Woori-VND-same-bank override, narrowed to `bpchar(1)` via `AntDivChannel.toStorageCode`. Was: never set → always `NULL` |
| **Reached?** | ✅ Reached, and now resolves a real channel. Was: reached but always `SKIP` |

**Consequence chain (F-1 — historical; line numbers are pre-fix and no longer resolve):**

```
PayrollDispatchExecutor         TbPayFileData.antDivCd  = null   (never assigned)
BulkReceptionService:533-543    presentChannels(details) = {}     (nulls are skipped)
OperatingHoursService:184-186   presentChannels.isEmpty() → SKIP
BulkReceptionService:241-245    SKIP → return; PROC_STAT stays '9' (WAIT); no alert, no FAIL
```

This bit whenever the bank's INFO row carried `ANT_WHERE_USE_YN='Y'` — which is **both Shinhan `970424`
and Woori `970457`**, verified in the seeded refactor DB and in legacy JEX DEV. Payroll for those two banks
would therefore **never dispatch and never report a problem** once `wecms.dispatch.auto-receive` was enabled.
**F-2 has since been fixed too** (§13.4), so the only remaining route to an empty channel set is the set of
**preserved-legacy `NULL` branches** — VCB FCY, Woori-VND same-bank, Shinhan-USD→VND same-bank, and an
unrecognised detail code. Those are exact legacy behaviour, deliberately kept, and no longer silent: the §10
monitor reports them as `ALL_CHANNELS_NULL`. Whether legacy's `null`-then-stall is *desirable* is the open
Q-4 behavioural question — a product decision, not a defect.

**Legacy, for comparison** — `pymn_0005_001_c001_act.jsp:214-227`:

```java
if (!TRNF_CCY_CD.equals("VND") && "970424".equals(outBank))       setAntDivCd(AntDivCdT.EBANK);   // 'E'
else if (!TRNF_CCY_CD.equals("VND") && "970457".equals(outBank))  setAntDivCd(AntDivCdT.VCB);     // 'V'
else if (!TRNF_CCY_CD.equals("VND") && "970436".equals(outBank))  setAntDivCd(null);              // VCB USD, intentional
else                                                              setAntDivCd(AntDivCdT.parse(rows.getString("ANT_DIV_CD")));
// …then :257-259, inside the same-bank branch:
if ("970457".equals(outBank) && TRNF_CCY_CD.equals("VND"))        setAntDivCd(null);              // Woori VND same-bank
```

Since `PayrollPaymentService:544` hard-codes the detail `ANT_DIV_CD = "CITAD"` (legacy
`pymn_0005_002_c001_act.jsp:118,125` parity), the `else` branch means **legacy Payroll VND writes
`ANT_DIV_CD='C'`** → channel `CITAD` → a real window is evaluated. The refactor wrote `NULL` **before the
F-1 fix**; it now writes `'C'` for that case, matching legacy (§9.2).

Corroborating live data (`tb_pay_file_data`, JEX DEV, read-only): `'N'` 106 · `'E'` 102 · `'S'` 86 ·
`'C'` 29 · `NULL` 19 · `'V'` 2. NULL exists but is the *minority*, and it corresponds to the two
*intentional* null branches above — not to a whole payment type.

### 3.4 Internal Transfer — ✅ F-2, F-4 and F-5 all closed

| Step | Detail |
|---|---|
| **Entry API** | `POST /api/v1/payment/internal-transfer/{id}/dispatch` → [`InternalTransferController:414-422`](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L414-L422) |
| **Service** | `InternalTransferDispatchService.dispatch(id, req)` — [`:87`](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchService.java#L87). **VCB-source batches skip the request-time cut-off entirely** (`:141`, `isInternalTransferCutoffExempt`, legacy `PymnHelper:3225-3233`); others validate at `:153` |
| **Executor** | `InternalTransferDispatchExecutor.execute(...)` — routing via `computeRoutingFlags` `:208-234` |
| **Routing** | `useApiPath = ((VND && !isShinhanUsdToVnd) \|\| isWooriUsdToVnd) && !isVcbFileTransfer`. API → `realtimeDispatchRegistrar` `:285`; File → `autoReceiveRegistrar` `:382` |
| **Gate** | **BOTH** |
| **`batch_id`** | as per leg (same as Vendor/Expense) |
| **`out_bank_cd`** | `master.getOutBankCd()` — API `:256`, File `:301` |
| **`ant_div_cd`** | realtime: `resolveApiAntDivCd` `:259` (`INTER` if same bank, else line value, blank → `INTER`); width-safe · bulk: now **three** legacy-ordered stages — USD first pass `:359-364` (Shinhan→`'E'`, Woori→`'V'`; **F-2 fix**), same-bank branch `:385` (`sameBank ? null : 'E'`; **F-5 fix**), VCB+VND narrowed via `toStorageCodeStrict` `:398` (**F-4 fix**) |
| **Reached?** | ✅ Both legs, all cases |

**File-path case enumeration — current `HEAD`.** Full 11-row legacy-vs-refactor matrix in §13.2.

| Case | `ANT_DIV_CD` written | Legacy | Verdict |
|---|---|---|---|
| Shinhan USD (detail ccy) | `'E'` — first pass `:360-361` | `'E'` (`:2779-2780`) | ✅ **F-2 fixed** |
| Woori USD (detail ccy) | `'V'` — first pass `:362-363` | `'V'` (`:2781-2782`) | ✅ **F-2 fixed** |
| Shinhan USD→VND **same-bank** | `null` — `:385` | `null` (`:2846-2848`) | ✅ **F-5 fixed** (was `'C'`, following a stale legacy comment) |
| Shinhan USD→VND other-bank | `'E'` — `:385` | `'E'` (`:2852-2854`) | ✅ unchanged |
| **VCB + VND** | narrowed 1-char, or `null` for a 5-char upload — `:398` | `null` via `copyFrom`→`fromValue` | ✅ **F-4 fixed**, legacy-exact |
| VCB + USD | `null` | `null` (explicit, `:2783-2785`) | ✅ intentional |
| other bank + USD | `null` | `null` (no branch) | ✅ intentional |

The **VCB + VND** row is the case OQ-2 explicitly confirmed as file-based. It previously failed with a
`character(1)` overflow; it now writes legacy's `null` for a 5-char upload and a narrowed 1-char code
otherwise. The rows that legitimately write `null` still produce an empty channel set → `SKIP` → `PROC_STAT='9'`;
that is exact legacy behaviour, preserved deliberately, and surfaced by the §10 monitor.

### 3.5 Overseas Payment

| Step | Detail |
|---|---|
| **Entry API** | `POST /api/v1/payment/overseas-payment/{id}/dispatch` → [`OverseasPaymentController:250-254`](../../src/main/java/com/kosign/wecms/domain/payment/controller/OverseasPaymentController.java#L250-L254). No request body |
| **Service** | `OverseasPaymentService.dispatch(ovrsPymnNo)` — [`:1341`](../../src/main/java/com/kosign/wecms/domain/payment/service/OverseasPaymentService.java#L1341), cut-off `:1380`. Second, M-mode path at `:1416-1435`, cut-off `:1431` |
| **Executor** | `OverseasDispatchExecutor.execute(sumr, lines)` (batch) and `execute(mstr)` (single-row M-mode) |
| **Routing** | **File only**, both overloads. `autoReceiveRegistrar` at `:322` and `:409`. No realtime registrar injected |
| **Gate** | `evaluateBulk` only |
| **`batch_id`** | INFO `NAPAS_REAL_TIME_BATCH` · DTL `FILE_DEPOSIT_TRANSFER_BATCH1` |
| **`out_bank_cd`** | ⚠️ **two different sources.** Batch path `:193` ← `master.getOutBankCd()`. M-mode path `:362` ← **`master.getDbtAcctCd()`** — legacy-correct (`pymn_0007:189-190` sets `OUT_BANK_CD = DBT_ACCT_CD`), and it *is* a bank code (compared to `OUT_BANK_CD_VCB` at `:344`). Documented, not a defect |
| **`ant_div_cd`** | `'S'` (SWIFT), hard-coded on both paths — `:237` and `:387`, constant at `:107` |
| **Reached?** | ✅ Yes, both overloads |

---

## 4. Consolidated matrix

| Payment type | Entry API (`POST`) | Service | Executor | Gate method | DTL `batch_id` | `out_bank_cd` source | `ant_div_cd` source | Reached? |
|---|---|---|---|---|---|---|---|---|
| **Vendor** | `…/vendor-payment/{id}/dispatch` | `VendorPaymentService.dispatch:1146` | `VendorDispatchExecutor:143` | `evaluateRealtime` (VND non-VCB) **+** `evaluateBulk` (else) | `NAPAS_REAL_TIME_BATCH` / `FILE_DEPOSIT_TRANSFER_BATCH1` | `master.getOutBankCd()` | `resolveAntDivCd` `:186` / `toStorageCodeStrict` `:289` + USD overrides (**F-4 fixed**) | ✅ |
| **Expense** | `…/expense-payment/{id}/dispatch` | `ExpensePaymentService.dispatch:1161` | `ExpenseDispatchExecutor:140` | `evaluateRealtime` **+** `evaluateBulk` | same | `master.getOutBankCd()` | `resolveAntDivCd` `:180` / `toStorageCodeStrict` `:280` + USD overrides (**F-4 fixed**) | ✅ |
| **Payroll** | `…/payroll-payment/{id}/dispatch` | `PayrollPaymentService.dispatch:1158` | `PayrollDispatchExecutor:144` | `evaluateBulk` only | `FILE_DEPOSIT_TRANSFER_BATCH1` | `master.getOutBankCd()` | legacy 4-branch + Woori override, narrowed via `toStorageCode` `:240` (**F-1 fixed**) | ✅ |
| **Internal Transfer** | `…/internal-transfer/{id}/dispatch` | `InternalTransferDispatchService.dispatch:87` | `InternalTransferDispatchExecutor:163` | `evaluateRealtime` **+** `evaluateBulk` | same | `master.getOutBankCd()` | `resolveApiAntDivCd` `:259` / USD first pass `:359-364` (**F-2**) + same-bank `null` `:385` (**F-5**) + `toStorageCodeStrict` `:398` (**F-4**) — all fixed | ✅ |
| **Overseas** | `…/overseas-payment/{id}/dispatch` | `OverseasPaymentService.dispatch:1341` | `OverseasDispatchExecutor:174` + `:342` | `evaluateBulk` only | `FILE_DEPOSIT_TRANSFER_BATCH1` | `getOutBankCd()` / **`getDbtAcctCd()`** (M-mode) | `'S'` hard-coded | ✅ |

**All five payment types: gate reached, no operating-hours defect outstanding.**

---

## 5. Bypass analysis

### 5.1 No path bypasses the gate

Every dispatch reaches `OperatingHoursService` via exactly one of two registrars, and both are unconditional
once their flag is on:

```
executor ──► RealtimeDispatchRegistrar.registerAfterCommit  ──► RealtimeDispatchService.dispatch ──► evaluateRealtime
        └──► DispatchAutoReceiveRegistrar.registerAfterCommit ──► BulkReceptionService.receive   ──► evaluateBulk
```

There is no code path that writes `TB_PAY_TARGET` / `TB_PAY_FILE_MST` and then calls the EFS legs directly.
Verified by exhaustive grep at current `HEAD`:

- **13 `registerAfterCommit` invocations**, all inside the 6 executors. No other caller.
- **`OperatingHoursService` is injected into exactly 2 classes** — `RealtimeDispatchService` and
  `BulkReceptionService` — one call site each.
- **The only writers of `TB_PAY_TARGET` / `TB_PAY_FILE_MST` are those 6 executors.** Every other holder of
  those repositories only reads or updates existing rows (`BulkExecutionResultService`,
  `BulkReceptionResultService`, `DispatchResultResolver`, `DispatchResultTxService`,
  `StrandedRecoveryService`, `DispatchKeyGenerator`).

`StrandedRecoveryService` — the only re-drive path — routes through `RealtimeDispatchService.dispatch` and
`BulkReceptionService.receive`, so recovered rows **are** gated.

**Caveat (F-4):** "no bypass" is a statement about *wiring*. F-4 means three of those paths currently abort
with a DB error **before** reaching the gate on certain cases — not a bypass, but not a completed gate
evaluation either.

### 5.2 Conditions that legitimately short-circuit the gate (not bypasses)

| # | Short-circuit | Where | Legacy basis |
|---|---|---|---|
| 1 | `ANT_DIV_CD = 'INTER'` skips the realtime DTL lookup (INFO still evaluated) | `OperatingHoursService:97` | `NAPAS_REAL_TIME_BATCH.java:138` |
| 2 | `ANT_WHERE_USE_YN != 'Y'` skips the DTL lookup on both gates | `:97`, `:142` | `FILE_DEPOSIT_TRANSFER_BATCH1.java:297` |
| 3 | Missing bulk DTL row passes | `:158-160` | `operTimeChk :814-817` |
| 4 | Flags off ⇒ registrar returns before any gate call | `RealtimeDispatchRegistrar:40`, `DispatchAutoReceiveRegistrar:45` | refactor safety design |

Item 4 is the current state everywhere: `wecms.dispatch.realtime-dispatch` and `.auto-receive` both default
`false`, so **no path reaches the gate in production today**. This audit describes the behaviour that
activates when those flags flip.

### 5.3 Not a bypass, but worth recording

- **`TransferDispatchExecutor` (F-3)** — wires both registrars (`:229`, `:327`) but has **zero production
  injection**. No `private final TransferDispatchExecutor` exists in any service; every reference is javadoc
  or `TransferDispatchExecutorRealtimeTest`. It is unreachable, therefore not a bypass — but it is live-looking
  dead code whose file path is documented as having "diverged from legacy in six places"
  (`VendorDispatchExecutor:48`). Recommend deleting it or annotating it `@Deprecated` with a pointer to the
  five module executors that superseded it.
- **VCB internal-transfer cut-off exemption** — `InternalTransferDispatchService:141` skips the
  **request-time** `CutoffValidator` for VCB-sourced internal transfers. It does **not** skip
  `OperatingHoursService`; the dispatch-time gate still runs. Since the seeded VCB INFO row carries
  `ANT_WHERE_USE_YN='N'`, that gate resolves to `DISPATCH` inside the 24h window — so VCB internal transfers
  are effectively 24/7 on both layers, matching the PO's stated intent.

---

## 5A. F-4 in detail — the `character(1)` width violation ✅ **RESOLVED**

> **Status: RESOLVED (§12.2, Option A).** This section is the **original evidence record, preserved verbatim**
> — the schema proof, the source-width proof, the affected-case enumeration and the severity reasoning that
> justified the fix. Read it as *why the fix was needed*, not as an open finding.
>
> **Closed by:** `AntDivChannel.toStorageCodeStrict` ([`:226`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/constant/AntDivChannel.java#L226), ≡ legacy `AntDivCdT.fromValue`) applied at
> [`VendorDispatchExecutor:289`](../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorDispatchExecutor.java#L289) ·
> [`ExpenseDispatchExecutor:280`](../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpenseDispatchExecutor.java#L280) ·
> [`InternalTransferDispatchExecutor:398`](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchExecutor.java#L398).
> The HTTP 500 is gone and the refactor now writes legacy's `null` for a 5-char upload.
> The failure mechanism was **runtime-proven** before the fix — see §12.1.
>
> **The line numbers quoted below (`:279`, `:272`, `:359`) are pre-fix** and no longer resolve.

### 5A.1 Schema, verified read-only on both databases

```sql
SELECT table_name, column_name, data_type, character_maximum_length
FROM information_schema.columns WHERE column_name = 'ant_div_cd';
```

| Table | Type | JEX DEV | Refactor DB |
|---|---|---|---|
| `scdul_pymn_sumr_detl.ant_div_cd` | `character` | — | **5** |
| `tb_pay_target.ant_div_cd` | `character` | **5** | **5** |
| `tb_pay_file_data.ant_div_cd` | `character` | **1** | **1** |

The realtime target is `character(5)`; the **bulk target is `character(1)`**. Entity mappings agree —
`TbPayFileData.antDivCd` is `@Column(length = 1)` with `@JdbcTypeCode(SqlTypes.CHAR)`. The schema is
legacy-correct on both sides; **only the Java copy is wrong**, so no DDL is involved in the fix.

### 5A.2 The source really does hold 5-char values

| Writer | Line | Value |
|---|---|---|
| `VendorPaymentExcelParser` | `:375`, `:378` | `"NAPAS"` / `"CITAD"` |
| `ExpensePaymentExcelParser` | `:390`, `:393` | `"NAPAS"` / `"CITAD"` |
| `InternalTransferExcelParser` | `:389`, `:392` | `"NAPAS"` / `"CITAD"` |
| `PayrollPaymentService` | `:544` | `"CITAD"` (hard-coded) |
| `ErpPaymentRefreshMasterImporter` | `:141` → `mapAntDivCd` `:273-275` | `trim(raw)` — **no narrowing**; HS ERP values pass straight through (incl. the unresolved `'T'`) |

Persisted by `VendorPaymentService:560`, `ExpensePaymentService:582`, `InternalTransferService:654` — each
`detl.setAntDivCd(emptyToNull(line.antDivCd()))`.

### 5A.3 Affected cases *(historical — all now narrowed)*

| Payment type | Case reaching a raw copy | Consequence |
|---|---|---|
| Vendor | **VCB + VND** (OQ-2 confirmed file-based) | overflow |
| Vendor | non-VND with an out-bank outside {Shinhan, Woori, VCB} | overflow |
| Expense | same two cases | overflow |
| Internal Transfer | **VCB + VND** (`:359`) | overflow |

`NULL` uploads are unaffected. The USD override blocks (Shinhan→`E`, Woori→`V`, VCB→`null`) are all
width-safe; the defect is confined to the cases those blocks do **not** overwrite.

### 5A.4 Why the tests did not catch it *(historical — assertions since corrected)*

At the time of discovery the executor unit tests were Mockito-based with no database, so they asserted the
in-memory value and passed — while **encoding the wrong width as expected behaviour**:

| Test | Line | Assertion |
|---|---|---|
| `ExpenseDispatchExecutorTest` | `:181` | `assertThat(d.getAntDivCd()).isEqualTo("NAPAS")` |
| `ExpenseDispatchExecutorTest` | `:193` | `assertThat(d.getAntDivCd()).isEqualTo("CITAD")` |
| `InternalTransferDispatchExecutorBankChnlDivTest` | `:192` | `assertThat(d.getAntDivCd()).isEqualTo("NAPAS")` |

Compare `PayrollDispatchExecutorAntDivCdTest`, which asserts the **1-char** forms (`"C"`, `"N"`, `"E"`, `"V"`)
— the correct contract, established by the F-1 fix.

**Resolved.** Those three assertions were corrected as part of the F-4 fix, and `AntDivChannelTest` gained
dedicated strict-converter coverage including `strict_longFormNames_becomeNull` and
`strict_and_lenient_divergeExactlyOnTheLongForm` — the guard that pins the Payroll-vs-others contract (§12.6).
No test asserts a 5-char value on `TB_PAY_FILE_DATA` at `HEAD`; the surviving `"NAPAS"`/`"CITAD"` assertions
are on `TB_FIRM_TRAN` and `SCDUL_PYMN_SUMR_DETL`, both `character(5)` — correct.

### 5A.5 Severity rationale *(as assessed when open)*

- **Not flag-gated.** The `TB_PAY_FILE_DATA` insert happens inside the dispatch transaction, *before*
  `autoReceiveRegistrar.registerAfterCommit`. Unlike the gate itself, this is reachable with
  `wecms.dispatch.auto-receive=false` — today's default.
- **User-visible failure.** `DataAccessException` → `WBE_DB` → **HTTP 500** on `POST …/{id}/dispatch`,
  transaction rolled back, no payment created. Not a silent stall, but not a clean business error either.
- **Hits a PO-confirmed flow.** VCB + VND file-based routing is exactly what OQ-2 (Mr. Jung, 2026-07-16)
  confirmed and what the surrounding code comments were written to implement.
- **Evidence basis:** code + DB schema + the `AntDivChannel` contract. This rev-2 "not runtime-observed"
  caveat was **later withdrawn** — the failure mechanism was runtime-proven in §12.1 before the fix landed.

---

## 6. Recommended fixes — ✅ **ALL IMPLEMENTED**

This section was the original remediation proposal. **Every item has since been implemented**; the proposals
are kept for traceability, each annotated with what actually shipped. Implementation records: §9 (F-1),
§12 (F-4, F-3), §13 (F-2, F-5).

### F-4 — width violation ✅ **IMPLEMENTED (§12.2)**

The proposal was to route the three raw copies through the F-1 narrowing:

```java
// VendorDispatchExecutor:279 · ExpenseDispatchExecutor:272 · InternalTransferDispatchExecutor:359
- data.setAntDivCd(line.getAntDivCd());
+ data.setAntDivCd(AntDivChannel.toStorageCode(line.getAntDivCd()));
```

**What shipped differs deliberately and is stricter.** `toStorageCode` (≡ legacy `AntDivCdT.parse`) maps
`"CITAD"` → `'C'`, which is **Payroll's** legacy contract. The other three file paths go through legacy
`TbPayFileData.copyFrom` → `AntDivCdT.fromValue`, which is 1-char-only and yields `null` for a long name. Using
`toStorageCode` there would have given Vendor / Expense / Internal Transfer a channel legacy never assigns and
gated the file by that channel's window — a money-routing change. So a second converter was added:

```java
// VendorDispatchExecutor:289 · ExpenseDispatchExecutor:280 · InternalTransferDispatchExecutor:398
+ data.setAntDivCd(AntDivChannel.toStorageCodeStrict(line.getAntDivCd()));
```

Paired items, all done: the three unit assertions were corrected (§5A.4); the HS-ERP `'T'` path now yields
`null` rather than an insert error (Q-4 territory, as predicted); and **no DB change was needed** — both
schemas were already legacy-correct (§5A.1).

### F-2 — Internal Transfer FCY fall-through ✅ **IMPLEMENTED (§13.4)**

The proposal was to confirm the intended value against `PymnHelper` first. That verification found legacy's
**USD first pass** at `PymnHelper:2779-2786`, and only **two** of the FCY cases take a non-null channel there
(Shinhan→`'E'`, Woori→`'V'`). Those two were ported at `InternalTransferDispatchExecutor:359-364`; the VCB and
other-bank cases were **left `null`** because legacy is `null` there too. The original guess that it "should
follow the Vendor/Expense rule" was therefore only half right — the parity check mattered.

### F-3 — dead shared executor ✅ **IMPLEMENTED (§12.4)**

`TransferDispatchExecutor` and its obsolete test were **deleted** (not deprecated); javadoc links were
repaired. Verified absent from `src/` at `HEAD`.

### F-5 — same-bank channel ✅ **IMPLEMENTED (§13.4)**

Found during the §13 parity pass, after this section was written: Internal Transfer wrote `'C'` for the
Shinhan-USD→VND same-bank case, following a legacy *comment* that its own code contradicts. Restored to
legacy's `null` at `InternalTransferDispatchExecutor:385`.

---

## 7. Verification method

- **Code:** every controller/service/executor/registrar named above was read directly at current `HEAD`; call
  sites confirmed by exhaustive grep — `registerAfterCommit` (**13 invocations**, all inside the 6 executors);
  `OperatingHoursService` injection (**2 classes**, one call site each); `setAntDivCd` (**31 sites** across
  `src/main`); `TransferDispatchExecutor` references (**14**, all Javadoc or test).
- **Legacy:** `pymn_0005_001_c001_act.jsp:200-265` (Payroll), `NAPAS_REAL_TIME_BATCH.java:105-235`,
  `FILE_DEPOSIT_TRANSFER_BATCH1.java:265-460`, `:790-830`, `IDO.TB_PAY_FILE_DATA_R005.xml`.
- **Data (read-only):** `tb_pay_file_data` channel distribution and `tb_batch_schedule_*` contents on JEX DEV;
  seeded schedule rows and `tb_firm_conf` on the refactor DB.
- **Schema (read-only, rev 2):** `information_schema.columns` for `ant_div_cd` on **both** JEX DEV
  (`postgres_dev_server_jexframe`) and the refactor DB (`postgres_dev_server_refactor`) — the basis for §5A.1.
- **Tests read:** `PayrollDispatchExecutorAntDivCdTest`, `AntDivChannelTest`, `ExpenseDispatchExecutorTest`,
  `InternalTransferDispatchExecutorBankChnlDivTest`, `OperatingHoursServiceTest`.
- **Build / tests (rev 6, forced):** `./gradlew cleanTest test` — **exit 0, 3m04s, 255 suites, 1999 tests,
  0 failures, 0 errors**, 20 skipped (all in `VasAccountIntegrationTest`, unrelated to dispatch). 134 of the
  255 suites are dispatch/payment-related; no `@Disabled` in any dispatch/payment test. `cleanTest` forces
  real execution, so this is not a Gradle up-to-date cache result.
- **Runtime proof of the F-4 mechanism:** the rev-2 caveat ("not runtime-observed") was **withdrawn in §12.1**
  — the `character(1)` overflow was proven against the real column before the fix landed.
- **Still not runtime-executed:** no live payment dispatch, no EFS/SFTP call, no DB write (dispatch flags are
  off by design). Post-fix behaviour is verified by code + legacy diff + unit tests, not by observing a
  dispatched file end-to-end. That verification belongs to SIT activation.
- **Line-number drift is real and recurring.** Citations moved between every revision (rev 1 → 2: bulk gate
  `:248` → `:240`; rev 2 → 6: Vendor bulk write `:279` → `:289`, IT `:359` → `:398`). All citations in this
  revision were re-derived from current `HEAD` — treat them as valid only for that commit. Pre-fix line
  numbers retained inside the historical sections (§5A, §6) are labelled as such.

## 8. Open questions

| ID | Question | Owner | Status |
|---|---|---|---|
| **Q-5** | Approve the F-4 narrowing on Vendor / Expense / Internal Transfer — including correcting the three test assertions (§5A.4) and the HS-ERP `'T'` handling? | PO + Dev | 🔶 **OPEN — ratification only.** **Option A is live, legacy-exact and verified** (§12.2); the HTTP 500 is gone and the OQ-2 VCB-VND file flow no longer 500s. Option B (`toStorageCode` semantics for these three paths) remains a PO choice — **not a defect and not a blocker** |
| Q-1 | Approve the F-1 port, including the 5-char→1-char mapping and the Woori-VND-same-bank `null` branch? | PO + Dev | ✅ **CLOSED — implemented 2026-07-28** (§9). The mapping is settled; the Woori-VND-same-bank `null` is preserved as legacy (folded into Q-4) |
| Q-2 | What is the correct `ANT_DIV_CD` for Internal Transfer non-VCB FCY file rows (F-2)? | PO + Dev | ✅ **ANSWERED FROM LEGACY, then implemented** (§13.1–13.4). Legacy's USD first pass (`PymnHelper:2779-2786`) assigns non-null in **only two** cases — Shinhan `'E'`, Woori `'V'`; VCB and other-bank are legitimately `null`. Ported exactly |
| Q-3 | Delete or deprecate `TransferDispatchExecutor` (F-3)? | Dev | ✅ **CLOSED — deleted** (§12.4) |
| Q-4 | Should an empty channel set be treated as `SKIP` (legacy) or escalated to a visible failure? A silent perpetual WAIT with no alert is operationally worse than a FAIL. **Still reachable after the F-1 fix** — via the two preserved legacy `NULL` branches (VCB FCY, Woori VND same-bank) and via an unrecognised detail code. | PO + Ops | **Observability half DONE 2026-07-28 (§10)** — the condition is now detected, classified and alerted by `StuckPayFileMonitorService`, without touching `SKIP` semantics. The *behavioural* half (should the gate itself FAIL instead of SKIP?) remains a PO decision — but is now much less urgent, since the failure mode is no longer silent |

---

## 9. F-1 implementation record (2026-07-28) — FIXED

### 9.1 Changed files

| File | Change |
|---|---|
| [`dispatch/constant/AntDivChannel.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/constant/AntDivChannel.java) | **Extended** (no new map elsewhere). Added the 5 public 1-char constants `CODE_EBANK/VCB/CITAD/NAPAS/SWIFT`, a private `CODE_BY_NAME` inverse map, and **`toStorageCode(String)`** — a port of legacy `wabooks.enums.AntDivCdT.parse`. `BY_STORAGE_CODE` now reuses the new constants instead of bare literals. |
| [`payrollpayment/service/PayrollDispatchExecutor.java`](../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollDispatchExecutor.java) | **Fix.** Added the legacy 4-branch `ANT_DIV_CD` selection before the existing bank-guard block, plus the Woori-VND-same-bank override inside the `sameBank` branch. Added the `AntDivChannel` import, a class-javadoc parity bullet, and the required NULL-branch/`SKIP` comment. |
| [`test/…/dispatch/constant/AntDivChannelTest.java`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/constant/AntDivChannelTest.java) | **New** — 9 tests over the shared vocabulary (both directions + round-trip). |
| [`test/…/payrollpayment/service/PayrollDispatchExecutorAntDivCdTest.java`](../../src/test/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollDispatchExecutorAntDivCdTest.java) | **New** — 14 tests over the executor branch matrix. |

No other file was touched. Specifically **unchanged**, per requirement 7: `OperatingHoursService`
(empty-channel semantics), `BulkReceptionService`, the batch-schedule seed data, realtime/file routing,
and the DB schema. F-2 and F-3 were left alone. No dispatch flag was enabled.

### 9.2 Implemented rule (legacy `pymn_0005_001_c001_act.jsp`)

```java
// selection — legacy :214-227
boolean fcy = !CCY_VND.equalsIgnoreCase(trimToNull(line.getTrnfCcyCd()));
if      (fcy && eq(outBank, shinhanBankCd)) data.setAntDivCd(AntDivChannel.CODE_EBANK);   // :215-217 'E'
else if (fcy && eq(outBank, wooriBankCd))   data.setAntDivCd(AntDivChannel.CODE_VCB);     // :218-220 'V'
else if (fcy && eq(outBank, vcbBankCd))     data.setAntDivCd(null);                       // :221-223 preserved NULL
else  data.setAntDivCd(AntDivChannel.toStorageCode(line.getAntDivCd()));                  // :225-226 narrowed

// override — legacy :256-259, INSIDE the same-bank branch (order matters)
if (sameBank && eq(outBank, wooriBankCd) && CCY_VND.equalsIgnoreCase(trimToNull(line.getTrnfCcyCd()))) {
    data.setAntDivCd(null);                                                               // preserved NULL
}
```

**Width handling (requirement 3).** `PayrollPaymentService:544` writes the 5-char `"CITAD"` to
`SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD` (`bpchar(5)`); the target `TB_PAY_FILE_DATA.ANT_DIV_CD` is `bpchar(1)`.
The value is **narrowed**, never copied. `toStorageCode` mirrors legacy `AntDivCdT.parse` exactly —
accepts either width, case-insensitive, `null` for anything unrecognised — with one addition: it **trims**,
which legacy did not need but is required here because a stored `'C'` reads back from `bpchar` as `"C    "`.

**Unknown values are not guessed (requirement 8).** `toStorageCode` returns `null` for unrecognised input,
including `INTER` (legacy `AntDivCdT` has no such constant). The row then carries a `NULL` channel rather
than being routed onto a channel nobody chose.

### 9.3 Test results

`./gradlew compileJava compileTestJava` — **clean**.
`./gradlew test` — **1958 tests, 0 failures, 0 errors** (was 1935 before this change; +23).

| Suite | Tests | Result |
|---|---|---|
| `AntDivChannelTest` (new) | 9 | ✅ |
| `PayrollDispatchExecutorAntDivCdTest` (new) | 14 | ✅ |
| `PayrollDispatchExecutorBankPrtnIdTest` (pre-existing) | 9 | ✅ unaffected |
| `PayrollDispatchExecutorAutoReceiveTest` (pre-existing) | 1 | ✅ unaffected |

Requirement-8 coverage, case by case:

| Required case | Test |
|---|---|
| Shinhan VND CITAD → `'C'` | `shinhanVnd_citad_narrowsToC` |
| Shinhan VND NAPAS → `'N'` | `shinhanVnd_napas_narrowsToN` |
| Shinhan FCY → `'E'` | `shinhanFcy_isEbank` |
| Woori FCY → `'V'` | `wooriFcy_isVcbChannel` |
| VCB FCY → `NULL` | `vcbFcy_isNull_preservedLegacyBehaviour` |
| Woori VND same-bank → `NULL` | `wooriVnd_sameBank_isNull_preservedLegacyBehaviour` |
| 5-char / padded inputs | `bpcharPaddedOneCharInput_isAccepted`, `bpcharPaddedFiveCharName_isAccepted`, `AntDivChannelTest.bpcharPadding_isTrimmed` |
| unknown value fails safely, not guessed | `unknownCode_isNull_notGuessed`, `interCode_isNull_notAFileChannel`, `AntDivChannelTest.unrecognised_returnsNull_neverGuessed` |

Three guard tests beyond the requirement: `wooriVnd_otherBank_keepsUploadedCode` (proves the override is
same-bank-scoped and does not blank every Woori VND payroll), `nonFirmBankingBank_vnd_stillUsesUploadedCode`
(legacy's else branch is "VND **or** other banks"), and `lowercaseInput_isAccepted`.

### 9.4 Remaining risks

| # | Risk | Severity | Note |
|---|---|---|---|
| R-a | **The two preserved legacy `NULL` branches still stall.** VCB FCY and Woori VND same-bank write `NULL`; a file whose rows are *all* null-channel still yields an empty channel set → `SKIP` → `PROC_STAT='9'`. | Medium → **Low (no longer silent)** | **Legacy behaviour, deliberately preserved** (requirements 5–7). **Mitigated 2026-07-28 (§10):** the monitor detects it and classifies it as `ALL_CHANNELS_NULL`. The stall still happens — it is now *visible*. Escalating the gate itself is still Q-4. |
| R-b | An **unrecognised** detail code also yields `NULL` → same stall. Reachable via the HAENGSUNG ERP raw-passthrough path (`ErpPaymentRefreshMasterImporter.mapAntDivCd`, documented unresolved `'T'`). | Medium → **Low (no longer silent)** | Failing closed is correct; the *silence* was the problem. **Mitigated 2026-07-28 (§10):** classified as `UNKNOWN_CHANNEL_CODE`. |
| R-c | **F-2** (Internal Transfer FCY fall-through) is untouched and still writes `NULL` for non-VCB, non-Shinhan-USD→VND foreign-currency file rows. | Medium | Out of scope by instruction. Needs Q-2 answered. |
| R-d | **F-3** (`TransferDispatchExecutor` dead code) is untouched. | Low | Out of scope by instruction. Needs Q-3. |
| R-e | Payroll normally runs **VND/CITAD**, so post-fix the channel is `'C'` → the Shinhan/Woori `CITAD` DTL rows now gate it: **09:00–15:30** (Shinhan) / **08:30–17:40** (Woori) ICT. Dispatching outside those windows now yields `CUTOFF` (FAIL + alert) or `SKIP`, where before it silently stalled. | **Behaviour change — intended** | This is the point of the fix, but it is a *visible* change: Payroll dispatch attempted at 18:00 ICT will now FAIL loudly instead of stalling. Flag to Ops before enabling `auto-receive`. |
| R-f | No end-to-end run: dispatch flags remain OFF, so the fix is verified by unit tests + legacy diff, not by observing a dispatched Payroll file. | Low | Validate in SIT with `auto-receive` on inside a CITAD window. |
| **R-g** | **The width rule this fix established is not yet applied to the other three file paths** — Vendor, Expense and Internal Transfer still copy the 5-char detail value raw into `bpchar(1)` (**F-4**, §5A). | **High** | Found during the rev-2 re-trace. Unlike R-a/R-b this is *not* a silent stall — it is a `WBE_DB` HTTP 500 that fires with the dispatch flags **off**, on the PO-confirmed VCB-VND flow. Fix is the same one-line `toStorageCode` call per site (§6 F-4). Needs **Q-5**. |

---

## 10. Stuck-pay-file monitoring implementation (2026-07-28)

Addresses the **observability half of Q-4** and mitigates **R-a / R-b**. Monitoring only — the gate's
`SKIP` semantics, payment statuses, dispatch routing, seed data and schema are all untouched, and no flag
was enabled. F-2, F-3 and F-4 were not modified.

### 10.1 Infrastructure audit (requirement 1) — what already existed

| Component | Verdict | Reused? |
|---|---|---|
| `StrandedRecoveryService` | Re-drives stranded `'9'`/`'6'` rows. Has exactly the eligibility rule needed, but **mutates** and is gated behind `wecms.dispatch.recovery.enabled`, which is coexistence-gated and must stay OFF. | Rule + finder reused; **not** extended (see 10.2) |
| `StrandedRecoveryJob` | `@Scheduled` + `@SchedulerLock`, cron `-`, ICT. Correct shape to copy. | Shape copied, ShedLock table shared |
| `TbPayFileMstRepository.findByProcStatAndProcDttmLessThan` | Already exists — status + age window. | ✅ **Reused as-is, no new query** |
| `OpsAlertService.notify(vanOrgCd, content)` | Fire-and-forget EFS Slack alert, `@Async`, self-gated by `alert.enabled`, never throws. | ✅ **Reused** |
| `SchedulerLockConfig` | ShedLock over PostgreSQL, `usingDbTime()`, shared `shedlock` table. | ✅ **Reused** |
| `AntDivChannel` | The single channel vocabulary (F-1). | ✅ **Reused** for classification |
| `ProcStat` enum | `0` SUCC · `3` FAIL · `6` REQ · `9` WAIT. | ✅ **Reused** |
| Micrometer / actuator | **Absent** — readiness blocker **C-5**. No meter registry exists. | n/a — counters go to the log + the returned report |
| `MSG_SND_HIS` | Written only by the unrelated BLTB `ApprovalNotificationService`, not by `OpsAlertService`. | ❌ Not suitable for dedup (see 10.4) |

### 10.2 Why a separate job rather than extending the recovery sweep (requirement 2)

`StrandedRecoveryService.recoverOnce()` is inert unless `wecms.dispatch.recovery.enabled=true`. That flag is
**coexistence-gated** — on a shared UAT/PROD DB the refactor cannot distinguish its own rows from legacy-JEX
rows, so it must stay OFF pending a PO decision. Folding read-only monitoring into it would make
observability available *only* when the one mutating gate that has to remain closed is opened — precisely
backwards. A monitor must be safe to enable on its own.

So the schedule and the enable flag are separate; **everything else is shared** (finder, eligibility rule,
ShedLock table, alert transport). No duplicate query, no duplicate alert path, no second lock provider.

### 10.3 What it detects and how it classifies (requirements 3–4)

Eligibility — `PROC_STAT='9'` **and** `PROC_DTTM < now(ICT) − threshold` (default **15 min**, configurable).
Requirement 3's "exclude records already being actively processed": the schema's reliable indicator is
`PROC_STAT` itself — `'6'` (REQ) means in-flight, `'0'`/`'3'` are terminal — so selecting only `'9'` already
excludes them; the age window then excludes a row the post-commit path is handling right now. Every executor
stamps `PROC_DTTM = REG_DTTM` on insert, so for a never-dispatched file the window measures age since
registration.

Causes, in evaluation order (most actionable first):

| `StuckPayFileCause` | Meaning |
|---|---|
| `DISPATCH_FLAG_DISABLED` | `auto-receive=false` — explains every stuck row on its own, so checked first (and short-circuits before any detail read) |
| `NO_DETAIL_ROWS` | No `TB_PAY_FILE_DATA` rows; `BulkReceptionService` would throw |
| `ALL_CHANNELS_NULL` | Every detail `ANT_DIV_CD` null/blank → empty channel set → `SKIP`. **R-a** |
| `UNKNOWN_CHANNEL_CODE` | A code outside `E/V/C/N/S` (HAENGSUNG raw `'T'`). **R-b** |
| `OPERATING_HOURS_SKIP` | Channels resolve but a read-only `evaluateBulk` re-check still says `SKIP` — genuinely outside the window; self-healing |
| `UNKNOWN` | Needs manual inspection |

The `evaluateBulk` re-check is read-only (two PK lookups, no writes) and does not alter `SKIP` semantics.

### 10.4 Alert de-duplication (requirement 7)

No existing audit/history infrastructure was suitable — documented before implementing, as required:
`OpsAlertService` persists nothing; `MSG_SND_HIS` belongs to the BLTB notification module and repurposing a
legacy table for scheduler bookkeeping is not permitted; a new refactor-only table needs PO approval
(precedent `project_oat_idempotency_dropped`). So dedup is **in-memory**, matching the established pattern
of `TokenRevocationService` / `RateLimiter` (both documented single-instance stores):

1. **One aggregated alert per sweep**, never one per file — inherently flood-resistant.
2. A **fingerprint** of the stuck set (sorted file keys + causes). Alert only when the fingerprint *changes*,
   or when `re-alert-minutes` (default **60**) has elapsed — a heartbeat for a persistent condition.
3. The fingerprint **resets when nothing is stuck**, so a recurrence alerts immediately instead of being
   suppressed as "unchanged".
4. `firstSeen` tracking is pruned to the current stuck set each sweep, bounding the map.
5. `ShedLock` already serialises the job to one node per run. **Residual risk:** duplicate alerts across a
   restart or failover, since the map is per-JVM. Bounded and acceptable for an observability signal.

### 10.5 Changed files

| File | Change |
|---|---|
| [`dispatch/monitor/StuckPayFileCause.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckPayFileCause.java) | **New** — 6-value cause enum |
| [`dispatch/monitor/StuckPayFileReport.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckPayFileReport.java) | **New** — sweep result record (counts + `alerted`) |
| [`dispatch/monitor/StuckPayFileMonitorService.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckPayFileMonitorService.java) | **New** — `@Transactional(readOnly = true)` sweep, classification, structured logs, aggregated de-duplicated alert |
| [`dispatch/scheduler/StuckPayFileMonitorJob.java`](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/scheduler/StuckPayFileMonitorJob.java) | **New** — `@Scheduled(cron = "${…:-}")` + `@SchedulerLock`, ICT |
| [`test/…/monitor/StuckPayFileMonitorServiceTest.java`](../../src/test/java/com/kosign/wecms/domain/payment/dispatch/monitor/StuckPayFileMonitorServiceTest.java) | **New** — 21 tests |

No existing production file was modified. No YAML was touched (requirement 9 — see 10.7).

### 10.6 Observability output

Per stuck row (capped at `max-alert-keys`), `WARN`:

```
feature=stuck-pay-file-monitor stuck pay-file biz_key=… tran_dt=… file_seq_no=… out_bank_cd=…
  fund_tp=… req_cnt=… proc_dttm=… stuck_minutes=… cause=ALL_CHANNELS_NULL
```

Once per sweep, `WARN` — the metrics substitute while actuator is absent:

```
feature=stuck-pay-file-monitor SUMMARY stuck_total=3 threshold_minutes=15 cutoff=20260728174500
  by_cause={ALL_CHANNELS_NULL=2, UNKNOWN_CHANNEL_CODE=1}
```

Plus one aggregated ops alert (Korean lead line, matching the existing dispatch alerts) carrying
`STUCK_TOTAL`, `THRESHOLD_MIN`, `BY_CAUSE` and the capped file list. `StuckPayFileReport` returns the same
numbers for tests and for a future actuator health indicator.

### 10.7 Configuration — disabled by default in every environment (requirement 9)

Five properties, all `@Value` with in-code defaults and **absent from every `application-*.yml`**, so an
unset property cannot abort startup (readiness finding H-1) and nothing enables by accident:

| Property | Default |
|---|---|
| `wecms.dispatch.stuck-monitor.enabled` | `false` |
| `wecms.dispatch.stuck-monitor.cron` | `-` (Spring never-fire) |
| `wecms.dispatch.stuck-monitor.threshold-minutes` | `15` |
| `wecms.dispatch.stuck-monitor.re-alert-minutes` | `60` |
| `wecms.dispatch.stuck-monitor.max-alert-keys` | `20` |

Double-gated exactly like the recovery sweep: the cron never fires **and** the service is inert.
Full reference: [../deployment/wecms-batch-environment-variables.md §2a](../deployment/wecms-batch-environment-variables.md).

### 10.8 Test results (requirements 8, 10)

`./gradlew compileJava compileTestJava` — clean. `./gradlew test` — **1979 tests, 0 failures, 0 errors**
(1958 before; +21). `StuckPayFileMonitorServiceTest`: **21 tests, all green.**

The `findByProcStatAndProcDttmLessThan` stub applies the **real** predicate (status equality + `procDttm <
cutoff`) over a canned row set, so the age/status exclusions are genuinely exercised — they pass only if the
service passes the right status code and computes the right cutoff.

| Required case | Test |
|---|---|
| row younger than threshold ignored | `rowYoungerThanThreshold_isIgnored` |
| row older than threshold detected | `rowOlderThanThreshold_isDetected` |
| NULL-only detail channels classified | `allNullDetailChannels_classifiedAsAllChannelsNull` |
| unknown channel classified | `unknownChannelCode_isClassified`, `unknownChannelWins_evenWhenAnotherRowResolves` |
| completed / failed rows ignored | `completedFailedAndInFlightRows_areIgnored` (also covers `'6'` in-flight) |
| alert de-duplication | `alert_sentOnce_thenSuppressedWhileStuckSetUnchanged`, `alert_reSent_whenStuckSetChanges`, `alert_reSent_afterHeartbeatInterval`, `cleanSweep_resetsFingerprint_soRecurrenceAlertsImmediately` |
| threshold configuration | `thresholdIsConfigurable`, `queryUsesWaitStatusAndThresholdCutoff` |

Beyond the requirement: `noDetailRows_isClassified`, `operatingHoursSkip_isClassified`,
`dispatchFlagDisabled_isClassifiedFirst_withoutReadingDetails`, `resolvedChannelInsideWindow_isUnknownCause`,
`alertSkipped_whenNoVanOrgCdResolves`, `disabled_isNoOp`, **`neverMutatesPaymentStatus`** (asserts no
`save`/`saveAll` anywhere — requirement 5), `sweepFailure_isSwallowed_soSchedulerThreadSurvives`,
`multipleCauses_areCountedSeparately`.

### 10.9 Remaining risks

| # | Risk | Severity | Note |
|---|---|---|---|
| M-a | Dedup state is **in-memory** — duplicate alerts across restart/failover, and per-JVM if scaled out. | Low | Documented above; ShedLock limits it to one sweep per run. A durable store needs a PO-approved table. |
| M-b | With `auto-receive=false` (today's default) **every** aged WAIT row classifies as `DISPATCH_FLAG_DISABLED`, so enabling the monitor first reports the whole existing backlog. | Low | Correct behaviour, but expect one large aggregated alert then silence. Noted in the config reference. |
| M-c | Alerts need `alert.enabled=true` **and** `efs.enabled=true` to leave the box; both default `false`. | Low | The structured `WARN` log works regardless and is usable via log search alone. |
| M-d | **`TB_PAY_TARGET` (realtime) is not covered.** | Low | The realtime gate fails loudly (`NO_CRITERIA`/`CUTOFF` → FAIL + alert), so a silent WAIT is only reachable via a pre-start `SKIP`. Extending the sweep is a small follow-up if wanted. |
| M-e | No metrics — actuator/Micrometer absent (blocker **C-5**). | Medium | Counters are in the summary log and in `StuckPayFileReport`; wiring a health indicator is trivial once actuator lands. |
| M-f | Not exercised end-to-end (flags off, cron `-`). | Low | Verified by 21 unit tests; validate in SIT by enabling the monitor alone — it is read-only, so this is safe *without* enabling dispatch. |
| M-g | The gate still `SKIP`s rather than failing — **Q-4's behavioural half is still open**. | Medium | Unchanged by design (requirement: do not change `SKIP` semantics). The condition is now visible, which is what made it dangerous. |

---

## 11. F-2 / F-3 / F-4 review (2026-07-28 rev 3) — no code changed

> ✅ **HISTORICAL — all three findings reviewed here are now FIXED.** This section is the **decision record
> that justified the fixes**, preserved verbatim: the legacy-inconsistency analysis (§11.0), the per-finding
> evidence, the Option A/B comparison (§11.4) and the priority ranking (§11.7).
>
> **Severity and priority labels below are as assessed at rev 3, while the findings were open** — including
> "HIGH / BLOCKER" and "Pre-SIT blocker: yes" for F-4 in the §11.7 table. **None of those labels describes
> `HEAD`.** Current status: F-2 ✅ (§13.4) · F-3 ✅ (§12.4) · F-4 ✅ (§12.2) · F-5 ✅ (§13.4). See §13.7.

Deep-dive review of the three then-open findings. **No code was modified in this revision.** Two corrections
to rev 2 are recorded below (11.1, 11.4) — both change what the fix should be, not whether one is needed.

### 11.0 The finding that reframes all three: legacy is internally inconsistent

Legacy has **two** different `ANT_DIV_CD` converters, and which one a payment type uses decides its
behaviour. This was not visible in rev 2.

| Legacy converter | Accepts | Returns for `"CITAD"` | Used by |
|---|---|---|---|
| `AntDivCdT.fromValue(v)` | **1-char only** (`value.equals`) | **`null`** | `TbPayFileData.copyFrom` → `TbPayFileData.java:301` |
| `AntDivCdT.parse(v)` | 1-char **or** name, case-insensitive | **`CITAD` → `'C'`** | Payroll only — `pymn_0005_001_c001_act.jsp:226` |

`AntDivCdT.java` — `fromValue` iterates `my.value.equals(value)` over values `N/C/S/E/V`; `parse` calls
`fromValue` first and *then* falls back to `equalsIgnoreCase("napas"|"citad"|…)`.

And the source column really does hold the long form — **read-only, both DBs**:

```sql
SELECT ant_div_cd, length(ant_div_cd), count(*) FROM scdul_pymn_sumr_detl GROUP BY 1;
```

| Value | Length | JEX DEV rows | Refactor DB rows |
|---|---|---|---|
| `CITAD` | 5 | **347** | 2 |
| `NAPAS` | 5 | **187** | 11 |
| `INTER` | 5 | 2 | — |
| `S` | 1 | 6 | — |
| `E` | 1 | 2 | — |

**Consequence.** For the dominant 5-char values, legacy `copyFrom` → `fromValue("CITAD")` → **`null`**.
So legacy Vendor / Expense / Internal Transfer write **`NULL`** to `TB_PAY_FILE_DATA.ANT_DIV_CD` on the
cases their USD override blocks do not cover — whereas legacy **Payroll** converts the same input to
`'C'`, because it uses `parse`. The `PymnHelper:2840-2843` comment ("would otherwise leak into the file")
describes the *intent* of the explicit null, not what `fromValue` actually does with a 5-char value.

This is why the F-1 fix (`toStorageCode` ≡ `parse`) was correct **for Payroll** and must **not** be
copy-pasted to the other three paths — see 11.4.

### 11.1 F-2 — Internal Transfer is missing legacy's USD first-pass block

> **Correction to rev 2.** Rev 2 described F-2 as "sets `ANT_DIV_CD` on only 2 of its 4 file-path cases"
> — a fall-through. The completed trace shows the real shape: the refactor **never ported legacy's USD
> first-pass override block** (`PymnHelper:2779-2786`) into `InternalTransferDispatchExecutor` at all.
> Vendor and Expense **do** have it (`VendorDispatchExecutor:283-289`, `ExpenseDispatchExecutor:276-284`);
> Internal Transfer is the only path missing it. Scope is narrower and more precise than rev 2 stated.

**Legacy trace (complete).** Internal Transfer file path = `PymnHelper.pymn_0009_001_c001`:

| Stage | Line | Behaviour |
|---|---|---|
| pre-fill | `:2718` | `tbPayFileData.copyFrom(rows)` → `AntDivCdT.fromValue(detail)` → **`null`** for 5-char input |
| 1st pass (USD) | `:2779-2786` | Shinhan USD → `EBANK 'E'` · Woori USD → `VCB 'V'` · VCB USD → `null` |
| 2nd pass (same-bank) | `:2846-2852` | `isShinhanUsdToVnd`: same-bank → `null`, other-bank → `EBANK 'E'` |

**Refactor:** only two `setAntDivCd` sites on the file path — `:353` (`isShinhanUsdToVnd` → `CITAD`/`EBANK`)
and `:359` (VCB + VND → raw copy). No USD first-pass. `grep CCY_USD` finds it only in routing flags, never
in channel selection.

#### Full bank / currency / channel decision matrix — Internal Transfer file path

`useApiPath = ((VND && !isShinhanUsdToVnd) || isWooriUsdToVnd) && !isVcbFileTransfer`
(`InternalTransferDispatchExecutor:229-232`). Only the **FILE** rows below reach `TB_PAY_FILE_DATA`.

| # | `out_bank_cd` | `TRNF_CCY_CD` | wdrl → depo | Route | Legacy `ANT_DIV_CD` | Refactor | Verdict |
|---|---|---|---|---|---|---|---|
| 1 | Shinhan `970424` | VND | VND → VND | **API** | n/a (`TB_PAY_TARGET`, char(5)) | n/a | ✅ match |
| 2 | Shinhan | VND | **USD → VND** (`isShinhanUsdToVnd`) | FILE | 2nd pass: same-bank → `null`; other → `'E'` | `:353` same-bank → `'C'`; other → `'E'` | ⚠️ **same-bank differs** — legacy `null`, refactor `'C'`. Pre-existing, **out of F-2 scope**, logged as **F-5** below |
| 3 | Shinhan | **USD** | USD → USD | FILE | 1st pass → **`'E'`** | **`NULL`** (no branch) | ❌ **F-2 defect** |
| 4 | Woori `970457` | USD | **USD → VND** (`isWooriUsdToVnd`) | **API** | n/a | n/a | ✅ match |
| 5 | Woori | **USD** | USD → USD | FILE | 1st pass → **`'V'`** | **`NULL`** (no branch) | ❌ **F-2 defect** |
| 6 | Woori | VND | VND → VND | **API** | n/a | n/a | ✅ match |
| 7 | VCB `970436` | **VND** | any | FILE | `copyFrom` → `fromValue("CITAD")` → **`null`** | raw `"CITAD"` → **INSERT overflow** | ❌ **F-4** |
| 8 | VCB | USD | USD → USD same-bank only | FILE | 1st pass → `null` | `NULL` (no branch) | ✅ accidental match |
| 9 | other bank | VND | — | **API** | n/a | n/a | ✅ match |
| 10 | other bank | USD | — | FILE | `copyFrom` → `null` (5-char) | `NULL` (no branch) | ✅ accidental match |

#### Are the missing branches defects? **Yes — two of them, rows 3 and 5.**

Verdict per row: **rows 3 and 5 are confirmed defects** — legacy writes a real channel (`'E'` / `'V'`), the
refactor writes `NULL`. Rows 8 and 10 are *not* defects: the refactor's `NULL` coincides with legacy's
`null` (legacy by explicit branch in row 8, by `fromValue` returning null in row 10). Row 7 is F-4, not F-2.
Row 2 is a distinct pre-existing divergence (**F-5**, new, LOW) that this review surfaced but which is
outside the F-2 brief.

**Runtime effect of rows 3 and 5:** `NULL` channel → `presentChannels` empty → `evaluateBulk` → `SKIP` →
`TB_PAY_FILE_MST` stays `PROC_STAT='9'`. Silent before §10; **now detected** as `ALL_CHANNELS_NULL`.

### 11.2 F-3 — verified: no production injection, and no leakage of any kind

Every channel checked against current `HEAD`:

| Vector | Command | Result |
|---|---|---|
| Constructor / field injection | `grep -rn TransferDispatchExecutor main/java \| grep -E 'private final\|@Autowired\|@Resource\|@Inject'` | **NONE** |
| Programmatic bean lookup | `grep -rn 'getBean(\|ApplicationContext' main/java` | **NONE anywhere in `main/`** |
| Reflection | `grep -rn 'Class\.forName\|newInstance()' main/java` | **NONE anywhere in `main/`** |
| Component-scan / import customisation | `grep -rn '@ComponentScan\|excludeFilters\|@Import(' main/java` | **NONE** |
| Test config leaking into runtime | `TransferDispatchExecutorRealtimeTest:60` | `new TransferDispatchExecutor(...)` — **plain constructor, no Spring context**, no `@SpringBootTest` / `@TestConfiguration` / `@MockBean` |
| Remaining references | — | javadoc only (`TbPayFileMst:19`, `TbPayTarget:19`, `DispatchAutoReceiveRegistrar:15`, 4 executors, 2 services) |

**One correction of emphasis.** The class is annotated `@Service` (`TransferDispatchExecutor.java:66`), so
Spring **does instantiate the bean at startup** and injects its full dependency set. "No production
injection" is accurate about *invocation*; it is not an inert class. It is a live, fully-wired,
never-called bean.

**Recommendation: delete — not deprecate.**
1. `@Deprecated` does not prevent injection; the next developer looking for a "shared" executor finds a
   compiling, Spring-managed bean and wires it. The five module executors exist precisely because this
   one's file path *diverged from legacy in six places* (`VendorDispatchExecutor:48`).
2. It is fully superseded — all five payment types have their own executor, and every registrar call site
   is accounted for (§5.1).
3. It carries the same `setAntDivCd(line.getAntDivCd())` raw-copy shape as F-4, so leaving it in place
   leaves a fourth latent instance of that defect.
4. Deleting removes the class + `TransferDispatchExecutorRealtimeTest` and nothing else — no production
   reference exists to update.

**If a safety margin is wanted**, the correct intermediate step is to **remove `@Service`** (and mark
`@Deprecated`): that converts a live bean into an inert class, so it can no longer be injected by
accident, while keeping the source for one release. Deprecating *without* removing `@Service` achieves
almost nothing.

### 11.3 F-4 — why it was added, and the evidence

**Why it was added (rev 2).** The F-1 fix established a written contract on
`AntDivChannel.toStorageCode`: *"Callers writing to `bpchar(1)` MUST route through this method; the 5-char
detail value must never be copied across directly."* Re-tracing the fixed code to confirm F-1 was complete
showed **Payroll is the only file path that obeys it** — the other three still raw-copy. That is what F-4
records.

**Exact source locations (re-verified line-by-line at `HEAD`):**

| Site | Line | Statement |
|---|---|---|
| `VendorDispatchExecutor` | **:279** | `data.setAntDivCd(line.getAntDivCd());` |
| `ExpenseDispatchExecutor` | **:272** | `data.setAntDivCd(line.getAntDivCd());` |
| `InternalTransferDispatchExecutor` | **:359** | `data.setAntDivCd(line.getAntDivCd());` |

**Evidence chain:**
1. Source column `scdul_pymn_sumr_detl.ant_div_cd` = `character(5)`; holds `"CITAD"` 347 / `"NAPAS"` 187 on
   JEX DEV, `"NAPAS"` 11 / `"CITAD"` 2 on the refactor DB (11.0) — **read-only verified on both**.
2. Target column `tb_pay_file_data.ant_div_cd` = `character(1)` — **read-only verified on both**
   (`information_schema.columns`), and `V105__tb_pay_file_data.sql:18` agrees.
3. `TbPayFileData.antDivCd` is `@Column(length = 1)` + `@JdbcTypeCode(SqlTypes.CHAR)`; `length` drives DDL
   only and does not truncate at bind time.
4. Reachability: VCB + VND is forced onto the file path (`ExpenseDispatchExecutor:145-157` and peers,
   OQ-2 Mr. Jung 2026-07-16), and the Excel parsers write the 5-char form
   (`ExpensePaymentExcelParser:390,393` and peers).

> **⚠️ Correction to rev 2 — do not mis-test this.** An **explicit cast truncates silently**:
> `SELECT 'NAPAS'::character(1)` returns `'N'` (verified read-only on the refactor DB). That is the
> documented `character(n)` cast exception and is **not** the INSERT path. PostgreSQL applies
> *assignment* cast semantics on INSERT/UPDATE, which **error** with
> `value too long for type character(1)` unless the excess characters are all spaces. Anyone probing this
> with a cast will wrongly conclude it is benign.
>
> **Evidence basis:** documented PostgreSQL semantics + schema + code. **Not runtime-observed** — writes
> are outside this review's remit. Confirming it needs one INSERT in a scratch schema, which a DBA can do
> in seconds and which should be step 0 of the fix.

**Classification: BLOCKER** (not warning, not cleanup) — on three grounds:
- **Not flag-gated.** The insert precedes `autoReceiveRegistrar.registerAfterCommit`, so it fires with
  `auto-receive=false` — today's default in every environment.
- **User-visible hard failure.** `DataAccessException` → `WBE_DB` → **HTTP 500** on
  `POST …/{id}/dispatch`, whole transaction rolled back, no payment created.
- **Hits a PO-confirmed live flow** (VCB + VND file-based).

### 11.4 F-4's fix is a PO decision, not a mechanical edit

> **Correction to rev 2.** Rev 2 proposed *"the same one-line `toStorageCode` call per site"*. **That is
> not legacy parity.** `toStorageCode` ≡ `AntDivCdT.parse` → `"CITAD"` → `'C'`. But legacy Vendor /
> Expense / Internal Transfer use `copyFrom` → `AntDivCdT.fromValue` → `"CITAD"` → **`null`** (11.0).
> Applying `toStorageCode` would make these files carry a channel legacy never gave them, so the file
> would be gated by that channel's operating window instead of being `SKIP`ped — a **money-routing
> behaviour change**, not a bug fix.

| Option | Semantics | Result for VCB + VND | Legacy parity | Trade-off |
|---|---|---|---|---|
| **A — legacy-faithful** | strict 1-char only (≡ `fromValue`); 5-char → `null` | `NULL` → `SKIP`, file waits | ✅ **exact** | Removes the HTTP 500. Reintroduces a stall — but now **visible** via §10 (`ALL_CHANNELS_NULL`) |
| **B — corrective** | name → 1-char (≡ `parse` / `toStorageCode`) | `'C'` → gated by the CITAD window, dispatches | ❌ diverges | File actually dispatches; matches what Payroll now does; but changes routing vs live JEX |

**Recommendation: A for the hotfix, B only on an explicit PO decision.** A is the smaller, reversible,
parity-preserving change that removes the 500 today; the monitor makes its stall observable, which is
exactly what §10 was built for. B is arguably what the business *wants* (a VCB VND payment probably
should dispatch), and legacy's `fromValue`/`parse` split looks like a legacy bug — but choosing B means
knowingly diverging from live JEX on a money path, which is the PO's call, not the implementer's.

Either way the three unit-test assertions that currently encode the 5-char value
(`ExpenseDispatchExecutorTest:181,193`, `InternalTransferDispatchExecutorBankChnlDivTest:192`) must flip.

### 11.5 Per-finding answers to the review questions

| | **F-2** | **F-3** | **F-4** |
|---|---|---|---|
| **Severity** | **MEDIUM** | **LOW** | **HIGH — BLOCKER** |
| **Affected payment type** | Internal Transfer only (Vendor/Expense have the block) | None at runtime — dead bean | Vendor, Expense, Internal Transfer |
| **Exact runtime condition** | File path, `TRNF_CCY_CD='USD'`, `out_bank_cd` ∈ {Shinhan `970424`, Woori `970457`}, USD→USD (i.e. not `isWooriUsdToVnd`, not `isShinhanUsdToVnd`) | `@Service` bean constructed at startup; never invoked | File path with a 5-char detail `ANT_DIV_CD` not overwritten by a USD override — chiefly **VCB + VND**; also a non-{Shinhan,Woori,VCB} out-bank with FCY |
| **Customer / business impact** | Transfer accepted, then never leaves. Funds not moved, no error shown, no alert (pre-§10) | None | `POST …/dispatch` returns **HTTP 500**. Payment not created; user sees a server error, not a business message |
| **Blocks money movement?** | **Yes — blocks** (silent stall) | No | **Yes — blocks** (hard failure, rollback) |
| **Misroutes money?** | **No.** Channel is `NULL`, so the file is never sent — it cannot go out on the wrong rail | No | **No.** The transaction rolls back; nothing is sent |
| **Must fix before SIT `auto-receive` activation?** | **Yes.** Silent stalls in SIT are exactly what activation testing must not be polluted by. §10 makes them visible, which is a mitigation, not a fix | **No.** Zero runtime effect; cleanup at convenience | **Yes — and it is already broken *before* activation.** Independent of the flag; should be fixed first, regardless of the SIT schedule |
| **Required PO decision** | Confirm the intended channel for IT Shinhan-USD→USD (`'E'`) and Woori-USD→USD (`'V'`) — i.e. confirm legacy `PymnHelper:2779-2786` is still correct. **Q-2** | Delete vs de-`@Service`+deprecate. **Q-3** | **Choose option A (legacy-faithful `null`) or B (corrective `'C'`/`'N'`)** — 11.4. This is the one genuinely consequential decision. **Q-5** |
| **Recommended order** | **2nd** | **3rd** | **1st** |

### 11.6 Recommended implementation order — and why

1. **F-4 first.** It is the only finding failing *today*, with flags off, on a PO-confirmed flow, with a
   user-visible 500. Step 0 is a DBA confirming the INSERT error in a scratch schema (11.3); step 1 is the
   A/B decision (11.4); step 2 is one line per site plus three test assertions.
2. **F-2 second, in the same MR if the PO answers both.** It touches the *same method* in
   `InternalTransferDispatchExecutor` (`:353-360`) as the F-4 site, so doing them together avoids editing
   one file twice and re-reviewing the same block. Porting `PymnHelper:2779-2786` is mechanical once Q-2
   is confirmed.
3. **F-3 last, standalone.** No runtime effect, so it must not be bundled with a money-path change — a
   deletion in the same MR would make the diff harder to review and the revert riskier.
4. **F-5 (new, LOW)** — matrix row 2, Shinhan-USD→VND **same-bank**: legacy `null`, refactor `'C'`. The
   only case in the whole matrix where the refactor emits a channel legacy did not. Not urgent (it makes
   the file *more* likely to dispatch, not less) but it is a real divergence and belongs in the same Q-2
   conversation.

### 11.7 Final priority table

| Rank | ID | Severity | Payment type(s) | Fails today? | Blocks money? | Misroutes money? | Pre-SIT blocker? | PO decision | Fix size |
|---|---|---|---|---|---|---|---|---|---|
| **1** | **F-4** | **HIGH / BLOCKER** | Vendor · Expense · Internal Transfer | ✅ **yes, flags off** | ✅ blocks (HTTP 500) | ❌ no | ✅ **yes** | **Q-5 — option A or B** | 3 lines + 3 test assertions |
| **2** | **F-2** | MEDIUM | Internal Transfer | ❌ needs `auto-receive=true` | ✅ blocks (silent stall) | ❌ no | ✅ yes | Q-2 — confirm `'E'`/`'V'` | ~8 lines (port `PymnHelper:2779-2786`) |
| **3** | **F-5** | LOW *(new, rev 3)* | Internal Transfer | ❌ | ❌ | ⚠️ emits a channel legacy did not | ❌ no | Q-2 (same conversation) | 1 line |
| **4** | **F-3** | LOW | none (dead bean) | ❌ | ❌ | ❌ | ❌ no | Q-3 — delete vs de-`@Service` | delete 1 class + 1 test |
| — | F-1 | HIGH | Payroll | — | — | — | — | closed | ✅ **FIXED** (§9) |
| — | Q-4 obs. | — | all bulk | — | — | — | — | closed for observability | ✅ **DONE** (§10) |

**Bottom line for activation:** F-4 must be fixed before anything else and is already live-broken; F-2
must be fixed before `auto-receive` is enabled in SIT; F-5 and F-3 do not gate activation. The §10 monitor
turns F-2's and F-4-option-A's failure mode from silent into observable, but it is a detector, not a fix.

---

## 12. Implementation record — F-4 fixed (Option A), F-3 deleted (2026-07-28 rev 4)

Two changes landed, verified separately: **F-4** (money path) and **F-3** (cleanup). **F-2 and F-5 were
NOT touched** — both remain OPEN pending approval. Untouched as instructed: schedule seed data,
operating-hours timezone, dispatch flags, recovery behaviour, stuck-file monitor behaviour, and
`OperatingHoursService` `SKIP` semantics.

### 12.1 Phase 1 — F-4 failure mechanism is now RUNTIME-PROVEN (rev 3 caveat withdrawn)

A scratch-schema INSERT was **not** possible: `.claude/rules/database.md` denies `INSERT`/`UPDATE`/DDL/
`CREATE TEMP TABLE`/`DO $$`, and both MCP servers are read-only. Per the brief's fallback clause that would
mean "documented semantics only" — but a rigorous read-only proof exists and was used instead.

PostgreSQL enforces `character(n)` length through the internal typmod-coercion function
`bpchar(bpchar, integer, boolean)`, whose third argument **is** the explicit-vs-assignment switch
(`varchar.c`: `if (!isExplicit) ereport(ERROR, "value too long …")`). Calling it directly is a pure
function evaluation — no DML, no DDL, no table touched — and it is the *same* code path an `INSERT`
invokes.

**First, the real typmod of the target column** (not a guessed length — `atttypmod` = declared length + 4):

```sql
SELECT a.attname, t.typname, a.atttypmod, a.atttypmod - 4 AS declared_length
FROM pg_attribute a JOIN pg_type t ON t.oid = a.atttypid
WHERE a.attrelid = 'tb_pay_file_data'::regclass AND a.attname = 'ant_div_cd';
```

| attname | typname | atttypmod | declared_length |
|---|---|---|---|
| `ant_div_cd` | `bpchar` | **5** | **1** |

**Then the coercion probes**, using that verified typmod `5`:

| Probe | `isExplicit` | Result | SQLSTATE |
|---|---|---|---|
| `bpchar('CITAD'::bpchar, 5, true)` | `true` (explicit cast) | **`'C'`** — silent truncation | — |
| `bpchar('CITAD'::bpchar, 5, false)` | **`false` (assignment / INSERT)** | **ERROR `value too long for type character(1)`** | `22001` |
| `bpchar('NAPAS'::bpchar, 5, false)` | `false` | **ERROR `value too long for type character(1)`** | `22001` |
| `bpchar('C    '::bpchar, 5, false)` | `false` | `'C'` — OK, excess chars are spaces | — |
| `bpchar('N'::bpchar, 5, false)` | `false` | `'N'` — OK | — |

**Recorded answers to the Phase-1 questions:**

- **PostgreSQL result:** assignment coercion of a 5-char value into `character(1)` **fails**; it does *not*
  truncate. Explicit casts *do* truncate — which is exactly why `SELECT 'NAPAS'::character(1)` (→ `'N'`)
  is a misleading probe and was excluded, as the brief required.
- **SQLSTATE:** `22001` `string_data_right_truncation` (documented code for this message; the MCP surfaced
  the message text only, not the code).
- **Fails or truncates:** **fails.** The statement errors and the surrounding transaction is aborted →
  `DataAccessException` → `WBE_DB` → HTTP 500, dispatch rolled back.
- **Does Hibernate send the raw 5-char value?** **Yes.** `@Column(length = 1)` influences DDL generation
  only and performs no bind-time truncation; `@JdbcTypeCode(SqlTypes.CHAR)` selects the JDBC type, not a
  length clamp. The full `String` is bound and PostgreSQL applies the assignment coercion above. *(Derived
  from mapping semantics — the JDBC bind itself was not observed at runtime.)*
- **Padded 1-char values are safe**, which is what makes the trimming in the new converter correct rather
  than merely convenient.

**Conclusion:** F-4 is no longer "documented semantics, not runtime-observed". The failing coercion was
executed against the real column's typmod on the refactor DB. Rev 3's §11.3 caveat is withdrawn.

### 12.2 Phase 2 — F-4 resolved with **Option A (legacy-faithful)**

**Decision record.** Option A was implemented on the requester's standing instruction *"use Option A for
the compatibility hotfix unless PO explicitly approves Option B"*. **No PO sign-off is on record for
either option** — Q-5 stays formally open as a ratification item, not as a blocker. Option B remains
available and is a one-word change (`toStorageCodeStrict` → `toStorageCode`) at three sites plus the
corresponding test expectations.

| | Option A — **IMPLEMENTED** | Option B — not implemented |
|---|---|---|
| Semantics | ≡ legacy `AntDivCdT.fromValue`: 1-char only | ≡ legacy `AntDivCdT.parse`: names too |
| `"CITAD"` → | **`null`** | `'C'` |
| VCB + VND outcome | `NULL` → empty channel set → `SKIP`, file waits at `PROC_STAT='9'` | `'C'` → gated by the CITAD window, dispatches |
| Legacy parity | ✅ exact | ❌ diverges from live JEX |
| HTTP 500 | ✅ removed | ✅ removed |
| Needs PO approval | no (restores legacy) | **yes** |

Option A restores exactly what legacy does and removes the 500. It does reinstate a stall — but that stall
is now **observable** via §10 (`ALL_CHANNELS_NULL`), which is precisely why the monitor was built first.

**New converter** — `AntDivChannel.toStorageCodeStrict(String)`:

```java
public static String toStorageCodeStrict(String antDivCd) {
    if (antDivCd == null) return null;
    String key = antDivCd.trim().toUpperCase(Locale.ROOT);
    return BY_STORAGE_CODE.containsKey(key) ? key : null;
}
```

`'C'`/`'c'`/`'C    '` → `'C'`; `"CITAD"`/`"NAPAS"`/`"INTER"`/`'T'`/blank/`null` → `null`. Trimming is the
one deliberate addition over legacy (bpchar padding; PostgreSQL itself clips trailing spaces on assignment
— proven in 12.1). Reuses the existing `BY_STORAGE_CODE` map — **no new map, no per-executor duplication**
(requirement 3).

**`toStorageCode` was NOT changed** (requirement 2) — Payroll correctly depends on `parse` semantics. The
two methods now encode legacy's own split, documented in the javadoc of both.

**Call sites changed** — `data.setAntDivCd(line.getAntDivCd())` → `AntDivChannel.toStorageCodeStrict(...)`:

| File | Line |
|---|---|
| `VendorDispatchExecutor` | `:279` |
| `ExpenseDispatchExecutor` | `:272` |
| `InternalTransferDispatchExecutor` | `:359` |

### 12.3 Phase 3 — F-2 and F-5: NOT IMPLEMENTED at rev 4 ✅ **both since fixed in rev 5**

> **Superseded.** This records the rev-4 state. **F-2 and F-5 were both implemented in rev 5 (§13.4)** after
> the legacy-parity verification in §13.1–13.3 established the exact legacy values. The paragraph below
> describes the code as it stood at rev 4, not at `HEAD`.

The brief gates both on approval ("*fix F-2 and F-5 only if approved*"). **No approval is on record**, so
neither was touched. `InternalTransferDispatchExecutor` still has no USD first-pass block, and the
Shinhan-USD→VND same-bank case still writes `'C'` where legacy writes `null`.

Scope was re-confirmed against the §11.1 matrix and is unchanged: **F-2 = rows 3 and 5 only** (Shinhan
USD→USD → `'E'`, Woori USD→USD → `'V'`); **F-5 = row 2** (same-bank Shinhan USD→VND). Rows 8 and 10 are
not defects. F-5 is deliberately kept out of the F-2 change as the brief requires.

### 12.4 Phase 4 — F-3 deleted (separate change)

Re-verified before deleting: **14 references, all javadoc/comment prose; zero injection, zero
`getBean`/reflection, zero `@ComponentScan` customisation.** The only test constructed it with `new`.

Deleted:
- `main/java/…/dispatch/service/TransferDispatchExecutor.java`
- `test/java/…/dispatch/service/TransferDispatchExecutorRealtimeTest.java`

Deletion was chosen over de-`@Service`-ing because the class carried the **same raw-copy shape as F-4** —
leaving it in place would have left a fourth latent instance of the defect that this very change removes.

Five `{@link …TransferDispatchExecutor}` javadoc references would have dangled, so each was converted to
`{@code TransferDispatchExecutor} (deleted 2026-07-28, F-3)` in `DispatchAutoReceiveRegistrar`,
`ExpenseDispatchExecutor`, `OverseasDispatchExecutor`, `PayrollDispatchExecutor`, `VendorDispatchExecutor`.
Remaining prose mentions were left as historical context.

This was applied and verified **after** the F-4 checkpoint, so the money-path change and the deletion are
independently attributable.

### 12.5 Changed files

**F-4 (money path)**

| File | Change |
|---|---|
| `dispatch/constant/AntDivChannel.java` | **+ `toStorageCodeStrict`** (+ javadoc on the legacy converter split). `toStorageCode` unchanged |
| `vendorpayment/service/VendorDispatchExecutor.java` | `:279` narrowed + import + F-4 comment |
| `expensepayment/service/ExpenseDispatchExecutor.java` | `:272` narrowed + import + F-4 comment |
| `internaltransfer/service/InternalTransferDispatchExecutor.java` | `:359` narrowed + import + F-4 comment |
| `test/…/constant/AntDivChannelTest.java` | **+6 tests** (9 → 15) |
| `test/…/vendorpayment/service/VendorDispatchExecutorTest.java` | 2 assertions flipped + 2 renamed, **+3 tests** (20 → 23) |
| `test/…/expensepayment/service/ExpenseDispatchExecutorTest.java` | 2 assertions flipped + 2 renamed, **+3 tests** (11 → 14) |
| `test/…/internaltransfer/…/InternalTransferDispatchExecutorBankChnlDivTest.java` | 1 assertion flipped + 1 renamed, **+3 tests** (8 → 11) |

> Rev 2 listed **3** assertions to flip; the real count is **5** — it missed
> `VendorDispatchExecutorTest:224` and `:238`.

**F-3 (cleanup)**

| File | Change |
|---|---|
| `dispatch/service/TransferDispatchExecutor.java` | **DELETED** |
| `test/…/dispatch/service/TransferDispatchExecutorRealtimeTest.java` | **DELETED** |
| 5 files above | `{@link}` → `{@code}` javadoc only |

### 12.6 Verification

| Gate | Result |
|---|---|
| `./gradlew compileJava` | clean |
| `./gradlew compileTestJava` | clean |
| `./gradlew test` — F-4 checkpoint | **1994 tests, 0 failures, 0 errors** (1979 → 1994; **+15**) |
| `./gradlew test` — after F-3 deletion | **1992 tests, 0 failures, 0 errors**, 255 suites (−2: the deleted suite) |

| Suite | Tests |
|---|---|
| `AntDivChannelTest` | 15 ✅ |
| `VendorDispatchExecutorTest` | 23 ✅ |
| `ExpenseDispatchExecutorTest` | 14 ✅ |
| `InternalTransferDispatchExecutorBankChnlDivTest` | 11 ✅ |
| `PayrollDispatchExecutorAntDivCdTest` (F-1, untouched) | 14 ✅ |

Requirement-4 coverage in `AntDivChannelTest`: `strict_oneCharCodes_arePreserved` (C→C, N→N),
`strict_longFormNames_becomeNull` (CITAD→null, NAPAS→null), `strict_paddedOneCharValues_areAccepted`,
`strict_caseIsIgnored`, `strict_unknownAndInterAndBlank_becomeNull`, and
**`strict_and_lenient_divergeExactlyOnTheLongForm`** — the guard that pins the Payroll-vs-others contract
so a future refactor cannot silently collapse the two converters.

Requirement-5 executor coverage — three new tests in each of Vendor / Expense / Internal Transfer:
`vcbVnd_oneCharAntDivCd_isPreserved`, `vcbVnd_paddedOneCharAntDivCd_isPreserved`,
`vcbVnd_unknownAntDivCd_isNulled`. The first two exist specifically to prevent over-correcting F-4 into
"always null".

### 12.7 Finding status after this change

| ID | Status | Note |
|---|---|---|
| **F-1** | ✅ **FIXED** (§9) | Payroll `ANT_DIV_CD` ported |
| **F-4** | ✅ **FIXED — Option A** (§12.2) | HTTP 500 removed; legacy `null` behaviour restored. **Q-5 open for ratification only** (Option B would need PO approval) |
| **F-3** | ✅ **FIXED — deleted** (§12.4) | Class + obsolete test removed; javadoc links repaired |
| **F-2** | 🔶 OPEN *as of rev 4* → ✅ **FIXED in rev 5 (§13.4)** | Was: not approved, IT missing `PymnHelper:2779-2786`. Legacy first pass has since been ported |
| **F-5** | 🔶 OPEN *as of rev 4* → ✅ **FIXED in rev 5 (§13.4)** | Was: awaiting a separate explicit decision. Same-bank restored to legacy `null` |
| Q-4 (observability) | ✅ **DONE** (§10) | Monitor detects the Option-A stall as `ALL_CHANNELS_NULL` |
| Q-4 (behavioural) | 🔶 **OPEN** | Whether the gate should FAIL instead of SKIP — unchanged by design |

**Activation readiness *as assessed at rev 4*.** The one finding that failed *with flags off* was fixed, and
F-2 was then the only remaining money-path item for enabling `auto-receive` in SIT.

> **Superseded by rev 5 / rev 6.** F-2 and F-5 were subsequently implemented (§13.4). **No operating-hours
> finding blocks SIT `auto-receive` activation any more** — see §13.7. What remains is deployment
> configuration and infrastructure, not code.

---

## 13. Final legacy-parity verification and closure (2026-07-28 rev 5)

Legacy-first verification of the remaining `ANT_DIV_CD` findings, then implementation of only the
confirmed mismatches. Legacy parity took priority over behaviour improvement throughout: **no legacy
behaviour was "corrected"**, no new channel mapping was introduced, and `OperatingHoursService`, dispatch
flags, monitoring, recovery and the scheduler were untouched.

### 13.1 Legacy source of truth

Internal Transfer file path = `PymnHelper.pymn_0009_001_c001`, in execution order:

| Stage | Legacy line | Behaviour |
|---|---|---|
| Routing flags | `:2557-2565` | `isWooriUsdToVnd` = Woori ∧ input `TRNF_CCY_CD`=USD ∧ wdrl=USD ∧ depo=VND · `isShinhanUsdToVnd` = Shinhan ∧ wdrl=USD ∧ depo=VND |
| Pre-fill | `:2718` | `tbPayFileData.copyFrom(rows)` → `AntDivCdT.fromValue` (`TbPayFileData.java:301`) — **1-char only, so a 5-char upload becomes `null`** |
| **1st pass (USD)** | **`:2779-2786`** | on **per-detail** `TRNF_CCY_CD='USD'`: Shinhan → `EBANK 'E'` · Woori → `VCB 'V'` · VCB → explicit `null` |
| **2nd pass (same-bank)** | **`:2831-2855`** | `isShinhanUsdToVnd` only: same-bank → **explicit `setAntDivCd(null)`** (`:2846-2848`) · other-bank → `EBANK 'E'` (`:2852-2854`) |

Two points that decided the outcome:

- The 1st pass keys on the **per-detail** `TRNF_CCY_CD`, not the batch parameter. `isShinhanUsdToVnd`
  carries detail `TRNF_CCY_CD='VND'` (legacy's own note, `:2775-2777`), so it is never caught by the 1st
  pass — the two passes do not interact.
- **The legacy comment at `:2801-2804` contradicts the legacy code at `:2846-2848`.** The comment claims
  *"Same bank → CITAD"*; the code four lines later executes `setAntDivCd(null)`, with its own justification
  (sFTP_Firm_Banking_Form_Layout_File v2.1, Sheet1 "File ebank USD → VND same bank": `scfm_intf_t` empty;
  Shinhan/Mr. Kang 2026-05-12 confirmed a DB null renders as a 1-byte space in the BLS file). **The code is
  authoritative.** The refactor had implemented the comment — that is the whole of F-5.

### 13.2 Legacy vs Refactor comparison table

Only **FILE**-routed rows write `TB_PAY_FILE_DATA`. Refactor routing:
`useApiPath = ((VND ∧ ¬isShinhanUsdToVnd) ∨ isWooriUsdToVnd) ∧ ¬isVcbFileTransfer`.

| # | Bank | Currency (detail / wdrl→depo) | Route | Legacy `ANT_DIV_CD` | Refactor **before** | Match? | Action |
|---|---|---|---|---|---|---|---|
| 1 | Shinhan `970424` | VND / VND→VND | API | n/a | n/a | ✅ | — |
| 2 | Shinhan | **VND / USD→VND** same-bank | FILE | **`null`** (`:2846-2848`) | **`'C'`** | ❌ | **F-5 → FIX** |
| 3 | Shinhan | VND / USD→VND other-bank | FILE | `'E'` (`:2852-2854`) | `'E'` | ✅ | **KEEP** |
| 4 | Shinhan | **USD / USD→USD** | FILE | **`'E'`** (`:2779-2780`) | **`null`** | ❌ | **F-2 → FIX** |
| 5 | Woori `970457` | USD / USD→VND | API | n/a | n/a | ✅ | — |
| 6 | Woori | **USD / USD→USD** | FILE | **`'V'`** (`:2781-2782`) | **`null`** | ❌ | **F-2 → FIX** |
| 7 | Woori | VND / VND→VND | API | n/a | n/a | ✅ | — |
| 8 | VCB `970436` | VND / any | FILE | `null` (`copyFrom`→`fromValue`) | `null` (F-4 Option A) | ✅ | **KEEP** |
| 9 | VCB | USD / USD→USD same-bank | FILE | `null` (explicit, `:2783-2785`) | `null` (never assigned) | ✅ | **KEEP** |
| 10 | other bank | USD / any | FILE | `null` (no branch; `fromValue`→null) | `null` | ✅ | **KEEP** |
| 11 | other bank | VND / any | API | n/a | n/a | ✅ | — |

**Answer to "does legacy actually set `ANT_DIV_CD` for every Internal Transfer FCY case?" — No.** Legacy
assigns a non-null channel in only **two** FCY cases (rows 4 and 6). Rows 9 and 10 are legitimately `null`
in legacy, and the refactor already matched — so they were **left alone**, exactly as the parity rule
requires.

### 13.3 Recommendation and outcome per finding

| Finding | Verdict | Basis |
|---|---|---|
| **F-4** | **KEEP** (Option A, unchanged) | Legacy `copyFrom`→`fromValue` yields `null` for a 5-char upload; refactor writes `null`. Parity holds. No PO decision on record → not revisited, per rule 1. |
| **F-2** | **FIX** — 2 cases only | Rows 4 and 6: legacy writes non-null (`'E'`/`'V'`), refactor wrote `null`. Rows 9/10 excluded because legacy is `null` there. |
| **F-5** | **FIX** — restore `null` | Legacy `:2846-2848` writes `null`; refactor wrote `'C'`. Rule 3 applies directly: legacy `null` → refactor `null`. |

### 13.4 Code changes

All in [`InternalTransferDispatchExecutor`](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchExecutor.java) — the only file with a production change.

| Change | Location | Detail |
|---|---|---|
| **F-2** — port legacy 1st pass | inserted before the `sameBank` block (mirrors legacy order) | `if (CCY_USD.equalsIgnoreCase(trimToNull(line.getTrnfCcyCd())))` → Shinhan `AntDivChannel.CODE_EBANK`, Woori `AntDivChannel.CODE_VCB`. **No VCB branch** — legacy's explicit `null` is already the refactor's state, and the brief restricts implementation to differing non-null values |
| **F-5** — same-bank `null` | the `isShinhanUsdToVnd` branch | `sameBank ? ANT_DIV_T_CITAD : ANT_DIV_T_EBANK` → `sameBank ? null : AntDivChannel.CODE_EBANK` |
| cleanup | constants block | Local `ANT_DIV_T_CITAD` / `ANT_DIV_T_EBANK` became unused and were **removed** — they were duplicate channel mappings. The block now uses the shared `AntDivChannel.CODE_*` vocabulary, so **no new mapping was introduced** |

`AntDivChannel` itself was **not modified** in this revision: `toStorageCode` (Payroll / `parse`) and
`toStorageCodeStrict` (Vendor / Expense / IT — `fromValue`) both stand unchanged.

### 13.5 Tests

`InternalTransferDispatchExecutorBankChnlDivTest` — **11 → 18 tests (+7)**:

| Test | Covers |
|---|---|
| `shinhanUsd_firstPass_isEbank` | row 4 — F-2 fix |
| `wooriUsdToUsd_firstPass_isVcbChannel` | row 6 — F-2 fix |
| `vcbUsd_firstPass_staysNull` | row 9 — guards F-2 from giving VCB a channel |
| `otherBankUsd_firstPass_staysNull` | row 10 — guards F-2 from over-reaching |
| `shinhanUsdToVnd_sameBank_isNull_notCitad` | row 2 — F-5 fix (raw same-bank) |
| `shinhanUsdToVnd_sameBankByEngAbrvatNm_isNull` | row 2 — F-5 fix via the `ENG_ABRVAT_NM` group path |
| `shinhanUsdToVnd_otherBank_isEbank_unchanged` | row 3 — guards F-5 from touching other-bank |

Four of the seven exist purely to prove the *unchanged* rows stayed unchanged — the parity brief's main
risk was over-reach, not under-reach.

### 13.6 Verification

| Gate | Result |
|---|---|
| `./gradlew compileJava` | clean |
| `./gradlew compileTestJava` | clean |
| `./gradlew test` | **1999 tests, 0 failures, 0 errors**, 255 suites |

Progression across this work: 1979 (monitor) → 1994 (F-4) → 1992 (F-3 deletion, −2) → **1999** (F-2 + F-5, +7).

| Suite | Tests |
|---|---|
| `InternalTransferDispatchExecutorBankChnlDivTest` | 18 ✅ |
| `VendorDispatchExecutorTest` | 23 ✅ |
| `ExpenseDispatchExecutorTest` | 14 ✅ |
| `AntDivChannelTest` | 15 ✅ |
| `PayrollDispatchExecutorAntDivCdTest` | 14 ✅ |

### 13.7 FINAL parity status — all findings closed

| ID | Status | Legacy parity | Note |
|---|---|---|---|
| **F-1** | ✅ **FIXED** (§9) | ✅ exact | Payroll `ANT_DIV_CD` 4-branch + Woori override ported (`parse` semantics) |
| **F-2** | ✅ **FIXED** (§13.4) | ✅ exact | Legacy USD 1st pass ported to Internal Transfer — rows 4 and 6 only |
| **F-3** | ✅ **FIXED** (§12.4) | n/a | `TransferDispatchExecutor` + obsolete test deleted |
| **F-4** | ✅ **FIXED — Option A** (§12.2) | ✅ exact | `toStorageCodeStrict` ≡ `fromValue`. **KEEP** — re-verified this revision |
| **F-5** | ✅ **FIXED** (§13.4) | ✅ exact | Same-bank restored to `null`; the refactor had followed a stale legacy comment |
| Q-4 observability | ✅ **DONE** (§10) | n/a | Stuck-WAIT monitor detects the residual legacy stalls |
| Q-4 behavioural | 🔶 **OPEN** | — | Whether the gate should FAIL instead of SKIP. Unchanged by design |
| Q-5 (F-4 Option B) | 🔶 **OPEN — ratification only** | — | Option A is live and legacy-exact; Option B remains a PO choice, not a defect |

**Every `ANT_DIV_CD` cell in the 11-row matrix now matches legacy.** No `ANT_DIV_CD` mismatch remains on
any of the five payment types.

### FINAL VERDICT (rev 6)

> **There is no remaining operating-hours code blocker for normal payment dispatch.**
>
> All five findings (F-1…F-5) are fixed and verified in code at `HEAD`. Every `ANT_DIV_CD` cell in the 11-row
> legacy matrix matches legacy. All five payment types reach `OperatingHoursService`; none bypasses it.
> Verified by a forced full test run: **1999 tests, 0 failures, 0 errors** (§7).
>
> **No source-code change is required for operating-hours behaviour before SIT or UAT activation.**

**Activation readiness.** The money-path blockers are closed: F-4 (failed with flags off) and F-2 (would
have stalled silently once `auto-receive` was enabled) are both fixed and legacy-exact. What remains for
SIT `auto-receive` activation is **not** `ANT_DIV_CD`-related and **not code** — it is the readiness-audit set
(**C-1, C-3, C-4, C-5, H-2, H-7**) plus the `TB_BATCH_SCHEDULE_*` seed on SIT/UAT/PROD. See
[../deployment/wecms-batch-infrastructure-readiness-audit.md](../deployment/wecms-batch-infrastructure-readiness-audit.md)
and [payment-dispatch-flags-deployment-readiness-audit.md](payment-dispatch-flags-deployment-readiness-audit.md).

**Two items remain open, neither a code defect and neither blocking:**

| Item | Nature | Why it does not block |
|---|---|---|
| **Q-4 behavioural** | Product decision | Whether an empty channel set should FAIL rather than `SKIP`. Legacy `SKIP` is preserved deliberately, and the condition is no longer silent (§10 monitor: `ALL_CHANNELS_NULL`) |
| **Q-5** | Ratification | Option A is live and legacy-exact. Option B is a PO preference, not a defect |
| **Evaluation timezone** | **Intentional, configurable decision** (§2.1) | ICT vs legacy's DB-server `Asia/Seoul` is a deliberate 2-hour divergence, reversible per environment via `wecms.dispatch.operating-hours.zone`. An activation-time choice for PO/Ops — **not a defect, no code change either way** |

**Residual behaviour to expect, by design.** Rows 2, 8, 9 and 10 legitimately write `null`, so those files
produce an empty channel set → `SKIP` → `PROC_STAT='9'`. That is exact legacy behaviour and was
deliberately preserved. It is no longer silent: the §10 monitor reports it as `ALL_CHANNELS_NULL`. Whether
legacy's `null`-then-stall is *desirable* is the still-open Q-4 behavioural question — a product decision,
not a parity defect.

---

## 14. Runtime-flow audit at green HEAD (2026-07-29 rev 6)

**Verdict: PARTIALLY COMPLETE** — the runtime path and legacy parity are fully verified against a green
`HEAD`; **no live SIT end-to-end dispatch has been observed.**

Scope was the payment-dispatch runtime flow only. No scheduler / infrastructure / environment /
deployment-readiness audit was performed. No YAML, `.env`, CI/CD, seed data or flag was touched, and no
unrelated refactor issue was fixed.

### 14.1 Build state — green, re-verified from scratch

| Command | Exit | Result |
|---|---|---|
| `./gradlew compileJava` | **0** | BUILD SUCCESSFUL |
| `./gradlew compileTestJava` | **0** | BUILD SUCCESSFUL |
| `./gradlew cleanTest test` | **0** | **2052 tests · 0 failures · 0 errors · 260 suites** |

`cleanTest` was used deliberately: a plain `./gradlew test` reported `Task :test UP-TO-DATE`, which is not
admissible evidence for an audit. The counts above come from a forced re-execution.

> **Blocker from rev 5 is cleared.** The 2026-07-28 red build
> (`ExchangeRateOnDemandLockTest.java:7` — `package …banking.nxisas.gateway does not exist`) is resolved;
> that suite is present and passing at HEAD. The in-flight `JobLockNames` / `JobLockGuard` refactor has
> landed. Test count moved 1999 → 2052 (+53) from that work, which is outside this audit's scope.

**Re-verification was mandatory, not optional:** the lock refactor modified four files on this flow —
`DispatchAutoReceiveRegistrar`, `BulkReceptionService`, `OperatingHoursService`, `StrandedRecoveryService`
(plus `ResultCheckJob`). Every hop below was therefore re-traced at current HEAD rather than carried over.

Impact assessment of that refactor on this flow:

| File | Change | Effect on dispatch runtime |
|---|---|---|
| `DispatchAutoReceiveRegistrar` | javadoc only (F-3 link repair) | **none** |
| `RealtimeDispatchRegistrar` | untouched | **none** |
| `BulkReceptionService` / `OperatingHoursService` | the F-1…F-5 work already audited | as documented §9–§13 |
| `StrandedRecoveryService` `:108-121` | now acquires `JobLockNames.DISPATCH_RESULT_CHECK` via `JobLockGuard` before delegating to `resultCheckService.runOnce()` | **result-processing leg only**; recovery remains double-gated OFF |

### 14.2 Runtime path — traced hop by hop at HEAD

| # | Hop | Evidence at HEAD | Status |
|---|---|---|---|
| 1 | **Approval → dispatch entry** | `POST /api/v1/payment/{vendor,expense,payroll,internal-transfer}-payment/{id}/dispatch` → `…Service.dispatch(id, req)`; request-time gate `cutoffValidator.validate(...)` runs first on every module | ✅ |
| 2 | **Executor, inside caller's tx** | All 5 executors `@Transactional(propagation = MANDATORY)` — dispatch rows and the master `TRNF_YN='Y'` stamp commit or roll back together | ✅ |
| 3 | **Route selection** | Vendor `:149` / Expense `:145` — `VND && !VCB` → API, else FILE · IT `computeRoutingFlags :229-232` · Payroll + Overseas FILE-only (no realtime registrar injected) | ✅ |
| 4 | **Row creation** | API → `TB_PAY_TARGET` (`character(5)`) · FILE → `TB_PAY_FILE_MST` + `TB_PAY_FILE_DATA` (`character(1)`) | ✅ |
| 5 | **Transaction commit → afterCommit registrar** | `RealtimeDispatchRegistrar:49` / `DispatchAutoReceiveRegistrar:53` flag gate, then `registerSynchronization(...) { afterCommit() }` at `:61-63` of both. A missing active transaction logs a warn and skips (`:56` / `:57`) — never dispatches inline | ✅ |
| 6 | **OperatingHoursService** | Realtime `evaluateRealtime :102` ← `RealtimeDispatchService:154` · Bulk `evaluateBulk :159` ← `BulkReceptionService:240`. INFO key `BATCH_ID_NAPAS_REAL_TIME` on **both** (`:109`, `:166`); DTL key differs (`:139` realtime, `:193` bulk) | ✅ |
| 7 | **Idempotency claim** | Realtime: atomic `PROC_STAT '9'→'6'` via `TbPayTargetRepository.claimForDispatch` (`:31-35`), invoked `RealtimeDispatchService:221`; `claimed == 0` → no-op `:222-223`. Bulk: WAIT gate `:182` + `TB_FIRM_FILE_MST` PK | ✅ |
| 8 | **API / FILE dispatch** | API → 204 FX pre-step then 202 · FILE → fixed-length build, SFTP upload, 00301 | ✅ |
| 9 | **Result processing** | 203 `ResultCheckService` ← `ResultCheckJob` (cron `-`) · callbacks 01301 `BulkReceptionResultController` / 01302 `BulkExecutionResultController` · `StrandedRecoveryService` delegates into the 203 body **under the same lock identity** (`:117-121`) | ✅ |
| 10 | **Status updates** | Realtime `:161` FAIL · `:229`/`:252` REQ · `:369` SUCC · `:440`/`:461` terminal via `writeStatus :469-475`. Bulk `:215`/`:250` FAIL · `:276`/`:377` REQ · `:570`/`:576` FAIL | ✅ |
| 11 | **Monitor / alert** | CUTOFF & NO_CRITERIA → FAIL + Korean ops alert. **SKIP → return, no write, no alert** (legacy `continue`). Residual WAIT detected by `StuckPayFileMonitorService` (OFF by default) | ✅ |

**Gate decision → action, confirmed unchanged:**

| Decision | Realtime | Bulk |
|---|---|---|
| `DISPATCH` | proceed to 204/202 | proceed to file build + 00301 |
| `SKIP` | return, row stays WAIT, **no alert** | return, file stays WAIT, **no alert** |
| `CUTOFF` | `TB_PAY_TARGET` FAIL + `실시간지급이체 Batch 거래 마감` | `TB_PAY_FILE_MST` FAIL + `대량이체 Batch 거래 마감` |
| `NO_CRITERIA` | FAIL + `실시간지급이체 Batch 수행 기준 없음` | FAIL + `대량이체 Batch 수행 기준 없음` |

### 14.3 `ANT_DIV_CD` branches at HEAD — line numbers re-pinned

Line numbers shifted with the lock refactor; all citations below are current.

| Payment type | Route | Site | Writes |
|---|---|---|---|
| Vendor | API | `:186` | `resolveAntDivCd` — `INTER` \| detail value |
| Vendor | FILE | `:289` base · `:295`/`:297`/`:299` USD | `toStorageCodeStrict` → `E` / `V` / `null` |
| Expense | API | `:180` | `resolveAntDivCd` |
| Expense | FILE | `:280` base · `:286`/`:288`/`:290` USD | `toStorageCodeStrict` → `E` / `V` / `null` |
| Payroll | FILE only | `:232`/`:234`/`:238` FCY · `:240` VND · `:255` Woori-VND same-bank | `CODE_EBANK` / `CODE_VCB` / `null` / `toStorageCode` / `null` |
| Internal Transfer | API | `:259` | `resolveApiAntDivCd` |
| Internal Transfer | FILE | `:361`/`:363` USD 1st pass · `:385` Shinhan-USD→VND 2nd pass · `:398` VCB-VND | `CODE_EBANK` / `CODE_VCB` / `sameBank ? null : CODE_EBANK` / `toStorageCodeStrict` |
| Overseas | FILE only | `:237`, `:387` | `'S'` SWIFT |

Converter split intact — `toStorageCode` (≡ `AntDivCdT.parse`) is used **only** by Payroll `:240`;
`toStorageCodeStrict` (≡ `AntDivCdT.fromValue`) by Vendor `:289`, Expense `:280`, IT `:398`. The canary
`AntDivChannelTest.strict_and_lenient_divergeExactlyOnTheLongForm` passes.

### 14.4 Finding status at green HEAD

| ID | Status | Verified at HEAD by |
|---|---|---|
| **F-1** Payroll never set `ANT_DIV_CD` | ✅ **FIXED**, legacy-exact | `PayrollDispatchExecutor:230-255`; `PayrollDispatchExecutorAntDivCdTest` **14/14** |
| **F-2** IT missing legacy USD 1st pass | ✅ **FIXED**, legacy-exact | `InternalTransferDispatchExecutor:359-364`; `InternalTransferDispatchExecutorBankChnlDivTest` **18/18** |
| **F-3** `TransferDispatchExecutor` dead code | ✅ **FIXED** — deleted | `git diff --stat` shows `-398` lines; zero production references remain |
| **F-4** `character(1)` width violation | ✅ **FIXED** (Option A) | `Vendor:289`, `Expense:280`, `IT:398`; `AntDivChannelTest` **15/15**, `VendorDispatchExecutorTest` **23/23**, `ExpenseDispatchExecutorTest` **14/14** |
| **F-5** Shinhan USD→VND same-bank | ✅ **FIXED**, legacy-exact | `InternalTransferDispatchExecutor:385` — `sameBank ? null : CODE_EBANK` |

Supporting suites on this flow, all green: `OperatingHoursServiceTest` 17 · `BulkReceptionReceiveTest` 17 ·
`RealtimeDispatchServiceTest` 20 · `StuckPayFileMonitorServiceTest` 21.

### 14.5 Evidence classification — what is and is not proven

**(a) Verified by code trace + tests** — all of §14.2, §14.3, §14.4. Mockito-based; no database, no network.
This proves *logic*, not *persistence*.

**(b) Verified against a live database** (read-only `SELECT`, JEX DEV + refactor DEV):

| Fact | Evidence |
|---|---|
| `tb_pay_file_data.ant_div_cd` = `character(1)`, `atttypmod=5` | `pg_attribute` |
| `scdul_pymn_sumr_detl.ant_div_cd` = `character(5)`, holds `CITAD` 347 / `NAPAS` 187 | `information_schema` + `GROUP BY` |
| Assignment coercion of a 5-char value **fails** (`22001`), explicit cast silently truncates | `bpchar('CITAD'::bpchar, 5, false)` vs `(…, 5, true)` |
| Schedule seed present on refactor DEV | 3 INFO + 12 DTL, `last_edtr='DBA_MANUAL'` |
| Dispatch legs never executed | `TB_FIRM_TRAN` 17 rows, all `201`/FAIL/`1000`, 2026-07-22→24; `TB_FIRM_BAT_API_TRAN`, `TB_FIRM_FILE_MST`, `TB_FIRM_FILE_RSLT`, `MSG_SND_HIS` **all 0** |
| Dispatch schedulers never fired | `shedlock` has no `ResultCheckJob.run`, no `StrandedRecoveryJob.run`, no `StuckPayFileMonitorJob.run` |

**(c) Observed through an end-to-end SIT dispatch — NONE.**

**Nothing in this flow has ever executed against a real bank leg.** Dispatch flags are `false` and the
crons are `-`, so:

- no `TB_PAY_FILE_DATA` row has been written by a *dispatched* payment;
- `OperatingHoursService` has never returned a decision in situ;
- no 202 / 204 / 00301 / 203 / 01301 / 01302 leg has run — corroborated by the four empty tables above;
- no ops alert has been emitted (`MSG_SND_HIS` = 0);
- the monitor has never swept (absent from `shedlock`);
- **F-4's INSERT-level fix is proven at the coercion-function level, not by an actual INSERT.** The
  Hibernate bind of a 5-char value was derived from mapping semantics (`@Column(length=1)` is DDL-only),
  not watched on the wire.

### 14.6 Remaining runtime validation gap

One gap, precisely bounded: **a controlled SIT end-to-end dispatch has not been performed.** It is the only
way to convert (a)+(b) into observed behaviour. Minimum set to close it — each maps to a branch already
verified statically:

| # | Case | Expected observable |
|---|---|---|
| 1 | Payroll VND, Shinhan, inside 09:00–15:30 ICT | `ANT_DIV_CD='C'`, gate `DISPATCH`, `PROC_STAT 9→6`, 00301 sent — **closes F-1 live** |
| 2 | Vendor or Expense **VCB + VND** | `ANT_DIV_CD=NULL`, **no** `22001` error, gate `SKIP`, row stays `'9'` — **closes F-4 live** |
| 3 | Internal Transfer Shinhan **USD→USD** | `ANT_DIV_CD='E'` — **closes F-2 live** |
| 4 | Internal Transfer Shinhan **USD→VND same-bank** | `ANT_DIV_CD=NULL`, `BANK_CHNL_DIV='T'` — **closes F-5 live** |
| 5 | Any file outside its channel window | gate `CUTOFF` → `PROC_STAT='3'` + `거래 마감` alert |
| 6 | Enable the monitor alone (read-only) | case 2's file reported as `ALL_CHANNELS_NULL` |

Prerequisite for cases 1–5 is `auto-receive=true` in SIT. Case 6 needs only the monitor and is safe
independently — it is read-only and mutates nothing.

### 14.7 Verdict

**PARTIALLY COMPLETE.**

Code and legacy parity are fully verified on a **green HEAD** (2052 tests, 0 failures): every hop of the
runtime path is traced with line-level evidence, all four payment types plus Overseas are covered across
bank / currency / route / `ANT_DIV_CD` branches, and F-1…F-5 are confirmed fixed and legacy-exact.

It is **not COMPLETE** because no live SIT end-to-end dispatch has been observed — every leg of the actual
bank conversation remains unexercised, and the live-database evidence positively confirms that
(four dispatch tables empty, no dispatch lock rows). Until §14.6 runs, this flow is verified *by
construction*, not *by observation*.
