# Dead-Code Audit & Batch Architecture Review — WeCMS Batch Refactor

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Branch:** `wecms-ui-fixes`
**Scope:** batch-related processing only — `domain/payment/dispatch/**`, `domain/payment/dispatch/monitor/**`,
`common/lock/**`, the five per-module `*DispatchExecutor` classes, and the scheduler surface.
**Legacy baseline:** `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/` (4 programs, 2,821 LOC) + `consts/WCConst.java`.

**Constraints honoured:** no production code modified, no files deleted, no cleanup performed. Verified code
evidence only. Items needing sign-off are marked **[CONFIRM: Mr. Kang]**, **[CONFIRM: PO]**, or **[CONFIRM: batch owner]**.

> **UPDATE 2026-07-29 (second pass) — G-2 re-verified and completed; H-1 and H-2 resolved.**
>
> | Finding | Status | Where |
> |---|---|---|
> | **G-2** operations alert on `00302` failure | **FIXED** — the first pass restored the alert but introduced a re-alert loop; see below | §8 G-2 |
> | **H-1** callback IP allow-list fails open | **FIXED** — and **reclassified**: not a legacy-parity issue | [callback-security-configuration.md](../deployment/callback-security-configuration.md) |
> | **H-2** "unbounded cross-tenant result-check scan" | **FIXED** for the unbounded half; **CLOSED AS FALSE POSITIVE** for the cross-tenant half | §8 H-2 |
>
> Legacy evidence was re-derived from source for all three rather than taken from this report.
> Two of the three original characterisations turned out to be partly wrong — recorded below.
> `./gradlew cleanTest test` → 276 suites / 2249 tests / 0 failures / 0 errors / 20 skipped.

> **UPDATE 2026-07-29 — G-1, G-2 and a newly-found G-3 are implemented and CLOSED.**
> This audit was *analysis only*; a follow-up task then fixed the missing `00302` retry. The findings
> below are preserved as the record of what was wrong, each annotated with its resolution in place.
> Implementation, activation runbook, manual-recovery and rollback procedures:
> **[features/wecms-batch/bulk-execution-retry.md](../features/wecms-batch/bulk-execution-retry.md)**.
> Verdict in §10 updated. **Remaining condition restated 2026-07-29:** not a coexistence sign-off —
> the 4→1 architecture is already approved and coexistence is resolved for DEV/SIT / is an Ops check
> for UAT. The open item is that the retry sweep is the K9 **Option A** design Mr. Kang declined.
> → [legacy-batch2-ownership-verification.md](../audits/legacy-batch2-ownership-verification.md)

> **Working-tree note (material).** This audit reads the **uncommitted working tree**, which has moved
> since the 2026-07-28 pass. `TransferDispatchExecutor.java` and its test are **already deleted**
> (`git status` → `D`), `AntDivChannel`, `common/lock/`, `dispatch/monitor/` and `StuckPayFileMonitorJob`
> are **new and untracked**, and 42 files are modified. Findings below describe the tree as it stands
> today; where a previous document disagrees, this one is current.

**Companion documents (read alongside, not superseded):**
[architecture-review.md](../architecture/architecture-review.md) ·
[scheduler-legacy-parity-audit.md](../audits/scheduler-legacy-parity-audit.md) ·
[scheduler-audit.md](../audits/scheduler-audit.md) ·
[cleanup.md](cleanup.md) (2026-07-28 dead-code pass — partly actioned; see §5.1)

---

## 1. Executive summary

The 4→1 batch reduction is **sound**. Every business responsibility of the three removed legacy batches
has a verified home in the Spring implementation, and the two most safety-critical legacy gates — the
**split-file sibling gate** and the **target-failure `G998` gate** — are reproduced faithfully, including
a legacy quirk (§7.2) that a naive rewrite would have "fixed" into a behaviour change.

`compileJava` + `compileTestJava` pass clean on the current tree with `TransferDispatchExecutor` removed
(exit 0), and every remaining reference to it is comment prose — so that deletion is verified complete
and safe. Its removal also **eliminated a known-wrong constant** (`BANK_PRTN_ID_PLACEHOLDER = "DEFAULT"`)
rather than losing behaviour: the five active executors resolve the real value from `TB_FIRM_CONF`.

One **real parity gap** was found, and it is the same artifact as the most interesting dead-code finding:

> **G-1 — the legacy retry loop was not replaced.** Legacy `FILE_DEPOSIT_TRANSFER_BATCH2` re-polled
> `TB_FIRM_FILE_MST (TRAN_DT, PROC_STEP='1', PROC_STAT='6')` **every 60 seconds**, so a transient VAN
> gateway failure on the `00302` execution instruction was retried automatically on the next tick. The
> refactor issues `00302` **inline from the inbound `01301` callback only**; on gateway failure it
> catches, logs, and leaves the file at `PROC_STEP='1'` with the in-code comment *"no automatic re-issue"*.
> Nothing re-drives that state. The repository finder that would implement the sweep —
> `TbFirmFileMstRepository.findByTranDtAndProcStepAndProcStat`, whose signature is **exactly the legacy
> BATCH2 poll** — exists with **zero callers**.
>
> The stated mitigation ("monitoring") does not currently cover it: `StuckPayFileMonitorService` scans
> only `PROC_STAT='9'`, and `StrandedRecoveryService` scans pay-files only at `PROC_STAT='9'`. **No
> component scans a bulk file at `PROC_STAT='6'`.** Money may already have left via SFTP + `00301`.

This is flagged as **deliberate-but-unmitigated**, not as an unknown defect: the code attributes the
decision to Mr. Kang (K9). It needs confirmation that manual recovery is genuinely intended **and** that
the detection gap is accepted. **[CONFIRM: Mr. Kang]**

Dead code is otherwise minor: 5 unused repository finders, 12 of 14 unused constants in one class
(with 6 local re-declarations), 2 javadoc links to the deleted class that will break `javadoc`, and 4
verified copy-paste helper clusters. No unused DTOs, no unused enum values, no unused services, no
unused repository interfaces.

**Final verdict: `PASS WITH CONDITIONS`** — see §10.

---

## 2. Architecture decision background

| Item | Position |
|---|---|
| Legacy shape | 4 JEX `AbstractSimpleBatchTask` programs, each on its own `.batch` cron, all polling DB state |
| Refactor shape | Event-driven: approval/dispatch commit → after-commit hand-off → **direct VAN Center API** call → inbound callback drives the next step. **One** scheduled job is a legacy-batch refactor |
| Approved by | **Mr. Kang** — confirmed and approved architecture change |
| Audit stance | The reduction is **not** treated as a defect. Parity is audited at the level of business responsibility, DB behaviour, and observable outcome — **not** class-for-class |

**Scheduler surface, precisely stated.** "Only one scheduled batch remains" is correct *for legacy-batch
refactors*, but the tree contains **9 `@Scheduled` classes / 13 annotations**. The dispatch-related ones:

| Job | Cron default | Origin | Status |
|---|---|---|---|
| `ResultCheckJob` | `${wecms.dispatch.result-check.cron:-}` (never fires) | **Refactor of legacy `PROCESSING_RESULT_CHECK_BATCH`** — the one surviving legacy batch | config-disabled |
| `StrandedRecoveryJob` | `${wecms.dispatch.recovery.cron:-}` | **New** refactor-only safety net (no legacy twin) | config-disabled, coexistence-gated |
| `StuckPayFileMonitorJob` | `${wecms.dispatch.stuck-monitor.cron:-}` | **New** refactor-only read-only monitor | config-disabled |

The other six (`ExchangeRateBatchScheduler`, `OperationStatusBatchScheduler`, 3× HAENGSUNG,
`NxisasSessionRegistry`) are outside batch-refactor scope and are covered by
[scheduler-legacy-parity-audit.md](../audits/scheduler-legacy-parity-audit.md).

All three dispatch jobs default to `-` (Spring's never-fire marker) and every flag in
[batch-dispatch-variable-reference.md](../deployment/batch-dispatch-variable-reference.md) is `false`. Per the audit brief, **these are not dead code.**

---

## 3. Legacy four-batch responsibility map

Verified by reading each program end-to-end.

### 3.1 `NAPAS_REAL_TIME_BATCH` — 실시간 입금이체 (722 LOC, cron `* * * * *`)

| # | Responsibility | Evidence |
|---|---|---|
| 1 | Poll `TB_PAY_TARGET` for waiting targets (`_R001`) | `:69-71` |
| 2 | Load `TB_FIRM_CONF` per row; derive API key + `VAN_ORG_CD` | `:88-107` |
| 3 | Operating hours: `TB_BATCH_SCHEDULE_INFO_R001` (`BATCH_ID='NAPAS_REAL_TIME_BATCH'`) → `EXE_DIV` | `:110-136` |
| 4 | Per-channel `TB_BATCH_SCHEDULE_DTL_R001`, skipped when `ANT_DIV_CD='INTER'` | `:136-172` |
| 5 | On hours failure: `TB_PAY_TARGET.PROC_STAT='3'` + ops alert (`거래 마감` / `수행 기준 없음`) | `:186-226` |
| 6 | Woori FX pre-step: `EFS 204` when `OUT_BANK_CD=970457 && ccy≠VND` | `:314-490` |
| 7 | Insert `TB_FIRM_TRAN` (`TRAN_TP=202`, `PROC_STAT='6'`) | `:492-520` |
| 8 | Call `EFS 202`; AES/HMAC encrypt + verify + decrypt | `:525-600` |
| 9 | Success → `TB_FIRM_TRAN`/`TB_PAY_TARGET` `PROC_STAT='0'`; failure → `'3'` + alert | `:606-700` |
| 10 | No-response / `G999` → leave `PROC_STAT='6'` for the result-check batch | `:575-595` |
| 11 | Vietnamese tone-stripping on owner name + purpose | `:257-270` |
| 12 | Per-bank pacing (`Thread.sleep`; Woori 3000 ms) | `:281-286`, `:702` |

### 3.2 `FILE_DEPOSIT_TRANSFER_BATCH1` — 대량 파일 이체처리 접수 (1,342 LOC, cron `*/1 * * * *`)

| # | Responsibility | Evidence |
|---|---|---|
| 1 | Poll `TB_PAY_FILE_MST` at `PROC_STAT='9'` | `:85-93` |
| 2 | Classify channels from `TB_PAY_FILE_DATA_R005` (`E/V/C/N/S` → else `ETC_CNT`) | `:116` |
| 3 | `TB_FIRM_CONF` load + `ORG_STAT` gate | `:219-236` |
| 4 | Operating hours header — **`BATCH_ID='NAPAS_REAL_TIME_BATCH'`** (not BATCH1) | `:271-273` |
| 5 | Per-channel `operTimeChk(bankCd, chan)` — **`BATCH_ID='FILE_DEPOSIT_TRANSFER_BATCH1'`** | `:301-397`, `:797-801` |
| 6 | Batch division into split files | `:932` |
| 7 | Build fixed-length deposit file (header/data/trailer) | `:1038-1136` |
| 8 | **SFTP upload** | `:699-700`, `:1147-1152` |
| 9 | Insert `TB_FIRM_FILE_MST` (`PROC_STEP='0'`, `PROC_STAT='6'`) | `:542-555` |
| 10 | Call **`00301`** bulk reception; audit `TB_FIRM_BAT_API_TRAN` before/after | `:706-745` |
| 11 | Failure → `TB_PAY_FILE_MST.PROC_STAT='3'` | `:691`, `:725`, `:749` |

### 3.3 `FILE_DEPOSIT_TRANSFER_BATCH2` — 대량 파일 이체처리 지시 (337 LOC, cron `*/1 * * * *`)

| # | Responsibility | Evidence |
|---|---|---|
| 1 | **Poll `TB_FIRM_FILE_MST` at `(TRAN_DT, PROC_STEP='1', PROC_STAT='6')`** | `:47-52` |
| 2 | **Target-failure gate** — `TB_PAY_FILE_MST_R004` count > 0 → fail with `G998` | `:68-99` |
| 3 | **Sibling gate** — `TB_FIRM_FILE_MST_R003` pending count > 0 → `continue` | `:104-121` |
| 4 | Call **`00302`** execution instruction | `:127-196` |
| 5 | Audit `TB_FIRM_BAT_API_TRAN` (`API_TRAN_TP='00302'`) before call, update after | `:156-215` |
| 6 | Success → `PROC_STEP='2'`; failure → `PROC_STEP='2'/PROC_STAT='3'` + `TB_PAY_FILE_MST='3'` + alert | `:224-296` |
| 7 | **No-response (`BT01`/`G999`/`C999`) → `continue`, leaving `PROC_STEP='1'` for the next 60 s poll** | `:218-220` |

> Row 7 is the **implicit retry loop**. It is the responsibility behind gap **G-1**.

### 3.4 `PROCESSING_RESULT_CHECK_BATCH` — 펌뱅킹 처리결과조회 (420 LOC, cron `*/1 * * * *`)

| # | Responsibility | Evidence |
|---|---|---|
| 1 | Poll `TB_FIRM_TRAN_R001` for unanswered transfers older than 2 min | `:60-64` |
| 2 | `TB_FIRM_CONF` load + **`ORG_STAT='1'` gate** (else skip + pace) | `:93-108` |
| 3 | Insert `TB_FIRM_TRAN` (`TRAN_TP=203`) with `ORG_TRAN_DT`/`ORG_TRAN_SEQ` | `:118-132` |
| 4 | Call `EFS 203`; on `0001` synthesise "no original transaction" | `:141-260` |
| 5 | Update original transaction + `TB_PAY_TARGET` to final status | `:200-300` |
| 6 | Per-bank pacing | `:83-89` |

---

## 4. Refactored responsibility map (one batch + direct API)

```text
Legacy batch step → business purpose → refactored component → trigger → DB writes → result
```

### 4.1 `NAPAS_REAL_TIME_BATCH` → **event-driven, approval-triggered**

| Legacy responsibility | Refactored component | Trigger | Verified |
|---|---|---|---|
| Poll waiting targets | **removed** — replaced by post-commit hand-off | `RealtimeDispatchRegistrar.registerAfterCommit` (`:48-73`) | ✅ |
| Per-target isolation (`continue`) | per-`tranSeq` try/catch, never rethrown | `RealtimeDispatchRegistrar:66-71` | ✅ |
| Firm-conf + API key | `VanApiKeyService` / `TbFirmConfRepository` | — | ✅ |
| Operating-hours INFO + DTL, `INTER` skip | `OperatingHoursService.evaluateRealtime` (`:107-146`) | — | ✅ |
| Hours failure → `'3'` + alert | `RealtimeDispatchService:154-170` + `OpsAlertService` | — | ✅ |
| Woori FX 204 pre-step | `RealtimeTransferService:245-250` (`BANK_CD_WOORI && ccy≠VND`) | — | ✅ |
| Insert `TB_FIRM_TRAN` 202 | `RealtimeDispatchService` | — | ✅ |
| `EFS 202` + AES/HMAC | `EfsApiClient` / `EfsCryptoService` | **direct VAN API** | ✅ |
| Status transitions `0`/`3`/`6` | `DispatchResultTxService`, `ProcStat` | — | ✅ |
| Vietnamese tone-stripping | `VietnameseText` | — | ✅ |
| Per-bank pacing | `DispatchPacer.perBankDelay` (Woori 3000 ms) | — | ✅ |

### 4.2 `FILE_DEPOSIT_TRANSFER_BATCH1` → **request-time, approval-triggered**

| Legacy responsibility | Refactored component | Verified |
|---|---|---|
| Poll `PROC_STAT='9'` | `DispatchAutoReceiveRegistrar.registerAfterCommit` (`:53-73`) | ✅ |
| Channel classification `E/V/C/N/S` | `AntDivChannel.fromStorageCode` + `BulkReceptionService.presentChannels` (`:537`) | ✅ |
| `ORG_STAT` gate | `TbFirmConfRepository` | ✅ |
| Hours header + per-channel DTL | `OperatingHoursService.evaluateBulk` (`:164-207`) — **both legacy `BATCH_ID`s preserved** (§7.2) | ✅ |
| Fixed-length file build | `FixedLengthCodec`, `DepositHeader/Data/Trailer` | ✅ |
| SFTP upload | `SftpClient` (`JschSftpClient` / `MockSftpClient`, `@ConditionalOnProperty`) | ✅ |
| Insert `TB_FIRM_FILE_MST` `0`/`6` | `BulkReceptionService` | ✅ |
| `00301` + audit | `BulkReceptionService` + `DispatchApiTranAuditWriter` | ✅ |

### 4.3 `FILE_DEPOSIT_TRANSFER_BATCH2` → **absorbed into the `01301` callback**

| Legacy responsibility | Refactored component | Verified |
|---|---|---|
| Poll `PROC_STEP='1'/PROC_STAT='6'` | **replaced** by inbound `01301` callback | ⚠️ see **G-1** |
| Target-failure `G998` gate | `BulkReceptionResultService:171-176` (`TARGET_STAT_ERR`) | ✅ |
| **Sibling gate** | `BulkReceptionResultService:219-227` — defers `00302` while any sibling is at `PROC_STEP='0'` | ✅ |
| `00302` issue | `issueExecutionInline` (`:270-317`) → `Efs302Request` (`:459`) | ✅ |
| Audit before call | `writeApiTran(...)` before `issueExecution` (`:284-287`) — comment: *"Audit BEFORE the call so even a no-response leaves a 00302 marker that blocks re-issue"* | ✅ |
| Idempotency | `existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp` (`:271-274`) — **stronger than legacy** | ✅ (improvement) |
| `PROC_STEP 1→2` on success | `:301-303` | ✅ |
| **No-response → retry next tick** | **absent** — `:305-316` catches, logs, leaves `PROC_STEP='1'`, *"no automatic re-issue"* | ❌ **G-1** |

### 4.4 `PROCESSING_RESULT_CHECK_BATCH` → **the one surviving scheduled batch**

| Legacy responsibility | Refactored component | Verified |
|---|---|---|
| Poll unanswered transfers | `ResultCheckService.runOnce` → `findByTranTpAndProcStat(202, '6')` (`:210`) | ✅ |
| `ORG_STAT='1'` gate | `ResultCheckService:277-289` (audit M-6) | ✅ |
| Insert `TB_FIRM_TRAN` 203 | `ResultCheckService` | ✅ |
| `EFS 203`, `0001` → no-original | `ResultCheckInquiryResult.noOriginal` (`:58-64`) | ✅ |
| Final status update | `DispatchResultResolver` / `DispatchResultTxService` | ✅ |
| Pacing | `DispatchPacer` | ✅ |
| Scheduling | `ResultCheckJob` (`@Scheduled` + `@SchedulerLock`, ICT) | ✅ config-disabled |

### 4.5 Result-file / `01302` — no legacy batch, callback-driven

`BulkExecutionResultService` handles the inbound `01302`: sibling gate (`:290-308`), `PROC_STEP 2→3`,
reflection onto `TB_PAY_FILE_DATA`, and final `TB_PAY_FILE_MST` status. Verified present.

**Conclusion for §4:** every legacy responsibility has an owner except **BATCH2 row 7** (retry-on-no-response).

---

## 5. Dead code findings

Every item below was checked against the full verification list: constructor/field injection, bean lookup,
`@Scheduled`, `@RestController`, `@Configuration`, `@Bean`, `@Qualifier`, `@ConditionalOnProperty`,
reflection/dynamic lookup, YAML/properties, test-only usage, and the direct-VAN-API flow.

### 5.1 `TransferDispatchExecutor` — **already deleted; deletion verified correct**

| Field | Value |
|---|---|
| Class | `TransferDispatchExecutor` (398 LOC) + `TransferDispatchExecutorRealtimeTest` |
| File | `dispatch/service/TransferDispatchExecutor.java` — **`D` (deleted, uncommitted)** |
| Evidence | `git status` → `D`; `./gradlew compileJava compileTestJava` → **exit 0**; no `private final`, no bean name, no `@Qualifier`, no YAML/properties reference, no reflection |
| Reason dead | All five modules inject their own executor: `ExpensePaymentService:168`, `VendorPaymentService:168`, `PayrollPaymentService:161`, `OverseasPaymentService:108`, `InternalTransferDispatchService:77`. Nothing injected the shared one |
| **Legacy-behaviour comparison** | Requested explicitly. Its constants survive in the active executors: `PROC_STAT_PENDING` 5/5, `PROC_RSLT_DIV_UNPROCESSED` 5/5, `FINC_MDA_DIV_ACCOUNT` 5/5, `FUND_TP_OTHER` 4/5, `TRAN_TP_DEPOSIT` 4/5, `BANK_CHNL_DIV_*` 4/5, `ANT_DIV_INTER` 3/5. The <5 counts are **correct divergences**, not losses: Payroll uses `EXPT_RESOL_MSTR.APPR_FUND_TP` instead of a hardcoded fund type (`project_payroll_type_appr_fund_tp_detail_fix`), and Overseas is SWIFT-routed so bank-channel-div does not apply |
| **Behaviour removed on purpose** | `BANK_PRTN_ID_PLACEHOLDER = "DEFAULT"` (`:121`) — the deleted class wrote a placeholder into `TB_FIRM_CONF.BANK_PRTN_ID`. The active executors resolve the real value via `resolveBankPrtnId`; only `OverseasDispatchExecutor:185` keeps `"DEFAULT"` as an explicit missing-row fallback. **Deleting it removed a known-wrong value** |
| Also superseded | Its `resolveAntDivCd` (`:387-393`) defaulted blank codes to `INTER`. Active executors route through `AntDivChannel`, which fails closed on unknown codes — the documented fix for the live failures in `project_batch_schedule_tz_antdiv_fixes` |
| Deletion risk | **None remaining** — already deleted, compiles clean |
| Legacy parity impact | **None.** No legacy behaviour was lost; two wrong behaviours were removed |
| Recommended action | **Keep deleted.** Fix the two dangling javadoc links (§5.2) and commit together |

### 5.2 Two `{@link}` references to the deleted class — **will break `javadoc`**

| File | Line | Evidence |
|---|---|---|
| `dispatch/entity/TbPayFileMst.java` | 17-19 | `{@link` … `TransferDispatchExecutor}` — resolvable target no longer exists |
| `dispatch/entity/TbPayTarget.java` | 17-19 | same |

`javac` ignores these (compile passes), but a `javadoc` build emits an unresolvable-reference error.
Eight other mentions are `{@code}` or plain prose and are harmless.
**Risk:** none. **Parity impact:** none. **Action:** retarget to the five active executors, or downgrade
`{@link}` → `{@code}`.

### 5.3 Unused repository methods (5)

No repository *interface* is unused. These derived finders have **zero call sites** in `src/main` and `src/test`:

| Repository | Method | Line | Note |
|---|---|---|---|
| `TbFirmFileMstRepository` | `findByTranDtAndProcStepAndProcStat` | :34 | **Superseded 2026-07-29.** G-1 is resolved by the bounded `findBulkExecutionRetryCandidates` (grace window + ordering + batch cap) rather than this unbounded legacy-shaped finder. This one is now genuinely unused and safe to delete — kept only as the verbatim legacy predicate for operator diagnosis. |
| `TbFirmFileMstRepository` | `findByTranDtAndBizKey` | :60 | no consumer |
| `TbFirmBatApiTranRepository` | `findByTranDtAndBizKeyAndFirmFileNm` | :27 | no consumer |
| `TbFirmFileRsltRepository` | `findByTranDtAndBizKeyAndFirmFileNmOrderBySeqAsc` | :24 | no consumer |
| `TbBatchScheduleDtlRepository` | `findByBatchIdAndOutBankCd` (`List`) | :25 | **Name-shadowed** by `TbBatchScheduleInfoRepository`'s `Optional` variant; automated counting cannot separate them. Manually verified: only the Info variant is called (`OperatingHoursService:109,166`); the Dtl `List` variant has no caller |

**Risk:** low — read-only derived queries; removing the Java method touches no DDL, column mapping, or
migration surface (workspace DB rule §1 not engaged). **Parity impact:** none *except* row 1.
**Action:** hold all five until G-1 is decided. **[CONFIRM: batch owner]**

### 5.4 Unused constants — `BulkTransferConstants`, 12 of 14

No static imports exist, so counts are exact.

| Constant | Uses | Status |
|---|---|---|
| `BATCH_ID_NAPAS_REAL_TIME` | 3 | live (`OperatingHoursService:109,139,166`) |
| `BATCH_ID_FILE_DEPOSIT_TRANSFER_1` | 1 | live (`OperatingHoursService:193`) |
| `BANK_CD_SHINHAN` | 0 | dead — re-declared `DispatchPacer:28` |
| `BANK_CD_WOORI` | 0 | dead — re-declared `DispatchPacer:30`, `RealtimeDispatchService:88`, `RealtimeTransferService:76` |
| `PER_DELAY_DEFAULT` / `_SHINHAN` / `_WOORI` | 0 | dead — re-declared `DispatchPacer:33-35` (as `long`, not `int`) |
| `CURRENCY_CD_VND` | 0 | dead — re-declared as `CURRENCY_VND` in `RealtimeDispatchService:90`, `RealtimeTransferService:78` |
| `ORG_ST_OK` | 0 | dead — re-declared `ResultCheckService:76` |
| `TAR_FINC_MDA_DIV_ACCT` | 0 | dead — re-declared `RealtimeDispatchService:85` |
| `TAR_FINC_MDA_DIV_CARD`, `ORG_ST_WAIT`, `ORG_ST_STOP`, `ORG_ST_OUT` | 0 | dead, no replacement |

This is a **duplicate-constants** finding. The class is a verbatim port of legacy `consts/WCConst.java`,
and six consumers each re-declared the value they needed as a `private static final` — every copy carrying
a `/** Legacy WCConst.X */` javadoc, so the shared class was clearly meant to be used.

**Recommended:** point the six local copies at `BulkTransferConstants` and delete the local
re-declarations — removes duplication, preserves `WCConst` traceability. Watch the `int`→`long` widening
for `PER_DELAY_*` (`DispatchPacer.perBankDelay` returns `long`).
**Not recommended:** deleting the 12 constants, which loses the legacy mapping the file exists for.

### 5.5 Duplicate helper methods — 4 verified clusters

Bodies hashed after stripping whitespace and comments; identical hash = identical code.

| Helper | Copies | Files |
|---|---|---|
| `sameBankGroup` | **4 identical** (`e364927f`) | Expense, InternalTransfer, Payroll, Vendor |
| `engAbrvatNm` | **4 identical** (`d1c80ab8`) | Expense, InternalTransfer, Payroll, Vendor |
| `resolveAntDivCd` | **2 identical** (`efa59a43`) | Expense, Vendor |
| `DispatchCountRow` → `Map<String,long[]>` block | **4 identical** 7-line bodies | `ExpensePaymentResultService:132-138`, `InternalTransferResultService:182-188`, `PayrollPaymentResultService:144-150`, `VendorPaymentResultService:139-145` |

Plus `eq` (4 copies), `trimToNull` (8 copies across `src/main`), and `DT8`/`TM6`/`DTM14`/`VN_TZ`
re-declared in all five executors (`ZoneId.of("Asia/Ho_Chi_Minh")` appears in **34 files**).

**Do NOT merge** `resolveBankPrtnId` (5 copies, **all 5 hashes differ**, 291–365 chars) or `resolveOwnerNm`
(5 copies, **all differ**, 226–529 chars). Each was tuned per module against its legacy JSP. Merging
requires a parity audit first (workspace north-star: *understand the full legacy flow before writing code*).

**Risk:** low for the four identical clusters; **high** for the two diverged ones.

### 5.6 Nil findings (verified, reported for completeness)

| Category | Result |
|---|---|
| Unused services | **0** (the only one, `TransferDispatchExecutor`, is already deleted) |
| Unused repository interfaces | **0** — all 14 dispatch repositories are constructor-injected |
| Unused DTOs | **0** — every `Efs2xx`/`Efs3xx` request+response, `EfsEnvelope`, `EfsCallbackEnvelope`, `MsgSendRequest`, `BulkReception/ExecutionApiCommand/Result`, `StuckPayFileReport` is wired |
| Unused enum values | **0** — `ProcStat` (4/4), `ProcStep` (4/4), `ApiTranTp` (4/4), `OperatingHoursDecision` (4/4), `StuckPayFileCause` (6/6) |
| Unreachable methods | **0** beyond §5.3 |
| Obsolete legacy-compatible implementations | **1** — `BANK_PRTN_ID_PLACEHOLDER`, removed with §5.1 |

`AntDivChannel`'s public vocabulary (`EBANK`/`VCB`/`CITAD`/`NAPAS`/`SWIFT`, `CODE_*`) has zero *external*
references — only `INTER`, `canonical()`, `fromStorageCode()`, `toStorageCode()` are called from outside.
**Keep:** they are the declared channel vocabulary backing the class's three mapping tables and the
documented contract for `bpchar(1)` writers.

---

## 6. Spring false positives — must remain

Each verified live through a non-code-reference mechanism.

| Class | Code refs | Why live | Mechanism |
|---|---|---|---|
| `EfsClientConfig` | **0** | Produces the `efsRestClient` bean consumed by `@Qualifier("efsRestClient")` at `RestClientEfsApiClient:42` | `@Configuration` + `@Bean` + `@Qualifier` |
| `ResultCheckJob` | 3 (javadoc) | The one surviving legacy batch | `@Scheduled` + `@SchedulerLock` |
| `StrandedRecoveryJob` | 1 (javadoc) | New safety net; **coexistence-gated OFF pending Mr. Kang** | `@Scheduled`, cron `-` |
| `StuckPayFileMonitorJob` | 1 (javadoc) | New read-only monitor | `@Scheduled`, cron `-`, `enabled=false` |
| `BulkExecutionResultController` | **0** | HTTP entry point for `01302` | `@RestController` |
| `BulkReceptionResultController` | **0** | HTTP entry point for `01301` | `@RestController` |
| `MockEfsApiClient` / `RestClientEfsApiClient` | — | Mutually exclusive pair on `wecms.dispatch.efs.enabled` | `@ConditionalOnProperty` |
| `MockSftpClient` / `JschSftpClient` | — | Mutually exclusive pair on `wecms.dispatch.sftp.enabled` | `@ConditionalOnProperty` |
| `CallbackIpWhitelistFilter` | 2 | Registered `SecurityConfig:93` | filter chain |
| `OpsAlertAsyncConfig` | 2 | `@Async(OpsAlertAsyncConfig.EXECUTOR)` at `OpsAlertService:125` | `@Async` qualifier |
| `CallbackProperties`, `SftpProperties`, `EfsProperties` | — | Bound config | `@ConfigurationProperties` / `@Component` |
| `JobLockNames`, `JobLockGuard` | — | Lock identities referenced from `@SchedulerLock` annotation values | annotation constants |

**Per the audit brief, none of the config-disabled jobs is classified as dead code.**

---

## 7. Approved architecture differences (not defects)

### 7.1 Polling → event-driven (approved by Mr. Kang)

| Legacy | Refactor | Assessment |
|---|---|---|
| 4 crons polling DB state every 60 s | after-commit hand-off + inbound callbacks; 1 scheduled residual | **Approved.** Removes up-to-60 s latency and 4×/min full-table scans |
| Batch discovers work | Dispatch registers work | **Safe.** `TransactionSynchronization.afterCommit` guarantees rows are durably committed before re-read, and no network I/O runs inside the payment transaction |
| Per-target `continue` on error | per-target try/catch, never rethrown | **Equivalent** — `RealtimeDispatchRegistrar:66-71`, `DispatchAutoReceiveRegistrar:65-71` |

### 7.2 Legacy quirk deliberately preserved — **verified, do not "fix"**

`OperatingHoursService.evaluateBulk` reads the schedule **header** with `BATCH_ID='NAPAS_REAL_TIME_BATCH'`
(`:166`) but the **detail** rows with `BATCH_ID='FILE_DEPOSIT_TRANSFER_BATCH1'` (`:193`). This looks like a
bug. It is not — legacy does exactly the same: `FILE_DEPOSIT_TRANSFER_BATCH1.java:272` uses
`"NAPAS_REAL_TIME_BATCH"` for `TB_BATCH_SCHEDULE_INFO_R001`, and `:801` uses
`"FILE_DEPOSIT_TRANSFER_BATCH1"` for `TB_BATCH_SCHEDULE_DTL_R001`. **Faithful parity.** This closes the
open question raised in [cleanup.md](cleanup.md) §5.6.

### 7.3 Safe implementation improvements

| Improvement | Evidence | Assessment |
|---|---|---|
| `00302` idempotency guard | `BulkReceptionResultService:271-274` | **Stronger than legacy**, which could re-issue after a restart |
| Audit-before-call | `:284-287` | Matches legacy ordering; comment states the rationale |
| `AntDivChannel` canonicalisation | `AntDivChannel:150-193` | **Fixes a live legacy failure** (1-char `'E'` missing the 5-char `NAPAS` row → `수행 기준 없음`; JEX DEV `tran_seq` 13219/13220) |
| ICT operating-hours zone | `OperatingHoursService:60-88` | Deliberate, documented, and **overridable** via `wecms.dispatch.operating-hours.zone` to restore bug-for-bug KST parity |
| ShedLock across nodes | `SchedulerLockConfig` | New capability; legacy had none |
| `REQUIRES_NEW` per split file | `BulkReceptionService` | Better isolation than legacy's single connection-scoped transaction |

---

## 8. Actual parity gaps

> **⛔ SECTION HEADING CORRECTED 2026-07-29.** G-1 below is **not** a parity gap — see the correction
> banner on it. On the evidence of Mr. Kang's K9 record, **this audit found no genuine legacy-parity
> gap in the batch refactor.** G-2 and G-3 were real defects but are refactor-side implementation bugs
> (a missing alert, a mis-scoped success code), not divergences from legacy business behaviour.

### G-1 — No re-drive for files stranded between `00301` and `00302` · **RECLASSIFIED 2026-07-29**

> ## ⛔ CORRECTION — G-1 WAS NOT A PARITY GAP
>
> This finding is classified below as *"the one real parity gap."* **That classification is wrong**, and
> the error is mine: the deciding record was in the repository the whole time, at
> `WECMS_BATCH DEV/analysis/questions/questions-for-mr-kang.md`, and I searched only the refactor tree.
>
> **K9 put four recovery strategies to Mr. Kang.** He was shown this exact failure mode — *"`00302` times
> out or fails (`BT01`/`G999`/`C999`) → No automatic retry; **funds may never move**"* — and
> **Option A, *"Recovery Scheduler (reconciliation) — periodically re-process aged `STEP=1` rows"*, was
> not selected.** He chose the inline model; the closed decision (2026-06-19) is *"no periodic sweep;
> lost callback = monitoring / manual."*
>
> **Correct classification: APPROVED ARCHITECTURE DIFFERENCE**, not a parity gap. The business
> responsibility of BATCH2 *is* fully reproduced by `issueExecutionInline`; the retry was an emergent
> side-effect of polling, not a distinct legacy responsibility.
>
> **Consequence for the fix built under this finding:** `issueExecutionInline` hardening, the `BT00`
> parity fix and `StuckPayFileMonitorService` are all **correct and aligned** with the approved design
> (monitoring is precisely what K9 prescribed). **`BulkExecutionRetryJob` is not** — it is Option A, and
> it must not be enabled anywhere until Mr. Kang explicitly reopens K9.
>
> Full analysis: [architecture/k9-bulk-retry-reassessment.md](../architecture/k9-bulk-retry-reassessment.md).
> The original finding text is preserved below as the record of the reasoning that led to the error.

> **✅ CLOSED — code implemented, tests green, disabled by default.**
> A bounded recovery sweep now owns the retry responsibility: `BulkExecutionRetryJob` →
> `BulkExecutionRetryService` → `BulkExecutionRetryTxService`, reproducing legacy's target-failure
> `G998` gate, sibling gate, audit-before-call ordering and status transitions, with a refactor-only
> attempt ceiling and grace window. The detection hole is closed separately by a second read-only
> sweep in `StuckPayFileMonitorService` (`BULK_EXECUTION_NOT_ISSUED` /
> `BULK_EXECUTION_UNCONFIRMED`) that works **with the retry sweep disabled** — the point being
> that observability no longer depends on opening a mutating gate.
> A second parity bug was found and fixed on the way: `issueExecution` rejected `BT00`, which legacy
> treats as success (`:242`).
> Full spec + activation/manual-recovery/rollback runbook:
> **[features/wecms-batch/bulk-execution-retry.md](../features/wecms-batch/bulk-execution-retry.md)**.
> `./gradlew cleanTest test` → 269 suites / 2171 tests / 0 failures / 0 errors / 20 skipped
> (skips all pre-existing `VasAccountIntegrationTest`).
>
> **Still open — RESTATED 2026-07-29 after an ownership verification.** The "coexistence precondition"
> written here was wrong on three counts and is superseded by
> [legacy-batch2-ownership-verification.md](../audits/legacy-batch2-ownership-verification.md):
> * The **4→1 architecture, including Batch 2's replacement, was already approved** by Mr. Kang
>   (2026-06-04 / 06-06 / 06-12, *"배치2 → API1으로 갈음"*) — it should never have been re-asked.
> * **Coexistence is scoped and is an Ops item:** DEV/SIT are DB-separated (SIT has no legacy batch
>   container); UAT needs an **Ops** DB-target confirmation; PROD has no refactor deployment.
> * **The real blocker is different:** K9 offered Mr. Kang *"Option A — Recovery Scheduler
>   (reconciliation), periodically re-process aged `STEP=1` rows"* and he **declined** it, choosing
>   *"no periodic sweep; monitoring + manual"*. This sweep **is** Option A, so it must not be enabled
>   anywhere until K9 is reopened. **[CONFIRM: Mr. Kang — reopen K9]**
>
> The `StuckPayFileMonitorService` half is *already* what K9 prescribed and needs no further approval.
>
> The original finding is preserved verbatim below as the record of what was wrong.

**Legacy behaviour.** `FILE_DEPOSIT_TRANSFER_BATCH2` polled `TB_FIRM_FILE_MST` at
`(TRAN_DT, PROC_STEP='1', PROC_STAT='6')` every 60 seconds (`:47-52`). On a no-response `RC`
(`BT01`/`G999`/`C999`) it executed `continue` (`:218-220`), leaving the row in that exact state — which the
**next minute's poll picked up again**. Retry was implicit in the architecture.

**Refactored behaviour.** `00302` is issued only inline from the inbound `01301` callback
(`BulkReceptionResultService.issueExecutionInline`). On gateway failure the catch block records the
result and leaves `PROC_STEP='1'`:

```java
} catch (BusinessException e) {
    // No-response / gateway error: keep PROC_STEP=1, record the result, do NOT rethrow.
    // Recovery is monitoring / manual operations (Mr. Kang, K9) — no automatic re-issue.
```
— `BulkReceptionResultService:305-316`

**Verified evidence that nothing re-drives it:**
- `ProcStep.REQ_RECV` is only ever **written** (`:208`, `:323`) and read once inside the same callback's
  sibling loop (`:230`). No scheduled or event path re-reads it.
- `TbFirmFileMstRepository.findByTranDtAndProcStepAndProcStat` (`:34`) — the finder whose signature *is*
  the legacy poll — has **zero call sites**.
- `StuckPayFileMonitorService:191` scans `TB_PAY_FILE_MST` at `ProcStat.WAIT` (`'9'`) **only**; its javadoc
  explicitly excludes `'6'` as "in-flight".
- `StrandedRecoveryService:132,150` scans pay-target and pay-file at `'9'` **only**; its `'6'` handling
  (`:111-121`) delegates to `ResultCheckService`, which scans `TB_FIRM_TRAN` by
  `findByTranTpAndProcStat(202, '6')` (`:210`) — the **real-time API path**, not the bulk file path.
- **No component scans a bulk pay-file at `PROC_STAT='6'`.**

**Business impact.** A transient VAN gateway failure on `00302` strands the batch after the file has
already been SFTP-uploaded and accepted via `00301` — money may be in flight. Legacy self-healed within
60 s. The refactor requires manual intervention, and **the monitoring the code comment relies on does not
observe this state**, so nobody is alerted.

**This is the same artifact as dead-code finding §5.3 row 1.** The unused finder is the vestigial BATCH2
poll — its lack of a caller *is* the gap.

**Options** (do not implement without approval):
1. Enable a bounded re-issue sweep using the existing finder, gated behind its own flag (closest to legacy).
2. Extend `StuckPayFileMonitorService` to also classify `PROC_STEP='1'/PROC_STAT='6'` beyond a grace
   window — **detection only**, no re-drive, no mutating flag opened. Smallest safe step.
3. Accept manual-only recovery — then the audit asks that it be recorded as an explicit operational
   runbook item, since the in-code mitigation currently does not exist.

**Recommendation:** option 2 now (closes the detection hole without touching the coexistence-gated
mutating path), option 1 evaluated with Mr. Kang for SIT/UAT.

### G-2 — Ops-alert coverage differs on the BATCH2 failure path · **LOW** · **FIXED 2026-07-29**

> **✅ CLOSED — second pass.** The first pass restored the alert; re-verification then found the
> restoration itself had a defect, now fixed.

**Legacy evidence (re-derived from source, 2026-07-29).** `FILE_DEPOSIT_TRANSFER_BATCH2.java` contains
**exactly one** `MsgSendUtil.SendMsg` call — line `:280`, inside the `:264` `else` branch. Every other
path reaches a `continue` *before* the alert code:

| Response | Legacy line | Alert? |
|---|---|---|
| `0000` / `BT00` (success) | `:242-263` | no |
| empty response body | `:202-206` `continue` | no |
| `BT01` / `G999` / `C999` | `:232-234` `continue` | no |
| `G998` target-fail gate | `:83-104` `continue` | no |
| sibling pending | `:121-125` `continue` | no |
| any other rc (permanent) | `:264-280` | **yes, once** |

So **not** all no-response codes are alertable — the task's caution was warranted. Note also the alert
is emitted *before* the DB writes (`:280` vs `:283-291`); for an async fire-and-forget notification
that ordering has no functional consequence, and the refactor alerts *after* the write so a failed
write cannot produce an alert about a state that was never persisted.

**Root cause of the remaining defect.** Legacy could not repeat its alert, because the branch that
alerts also writes `PROC_STEP='2'/PROC_STAT='3'`, removing the row from the next poll's predicate.
Three refactor outcomes deliberately write **no** status (`ATTEMPTS_EXHAUSTED`, `NOT_RETRYABLE`,
`UNRECOVERABLE_STATE`) — plus `ERROR` — so the file stayed selectable and would have alerted on
**every sweep**. That is alert noise the bounded-retry architecture introduced and legacy never had.

**Implementation.** Per-`(file, outcome)` de-duplication in `BulkExecutionRetryService.alertIfNeeded`:
those four outcomes alert once; a *changed* outcome re-alerts; the key is pruned as soon as the file
leaves the candidate set so a genuine recurrence is not muted; an undeliverable alert (no resolvable
`VAN_ORG_CD`) does not consume the slot. `FAILED_PERMANENT` is **not** de-duplicated — like legacy it
writes a terminal status in the same unit, so it is naturally once-only. The legacy headline
`이체실행지시 Batch2 API수신 오류 발생` is preserved verbatim so existing ops greps keep working.
`OpsAlertService` remains a no-op unless `wecms.dispatch.alert.enabled=true`; the gate is never bypassed.

**Tests.** `BulkExecutionAlertParityTest` — 15 tests, plus the 4 in `BulkExecutionRetryServiceTest`.

**Remaining risk.** The de-dup memory is in-process (same limitation as `StuckPayFileMonitorService`):
across a restart or a second node the worst case is a duplicate alert, never a missed one.

Original finding: legacy BATCH2 sent an `이체실행지시 Batch2 API수신 오류 발생` alert on `00302` API failure
(`:266-280`). The refactor's `issueExecutionInline` catch block wrote the audit row and a `log.warn` but
did not call `OpsAlertService`.

### G-3 — `BT00` rejected as a gateway error · **MEDIUM** · **FOUND & RESOLVED 2026-07-29**

Found while implementing the G-1 retry-code mapping, not present in the original audit.

`BulkReceptionResultService.issueExecution` tested `FirmBankingResultCd.isSuccess(rc)`, which accepts
only `"0000"`. Legacy `FILE_DEPOSIT_TRANSFER_BATCH2.java:242` accepts **`"0000"` OR `"BT00"`**, so a
`BT00` response — a legacy success — was thrown as `WBE_EFS_GATEWAY_ERROR` and the file was left at
`PROC_STEP='1'` instead of advancing to `'2'`.

**Fix:** a 302-leg-scoped `FirmBankingResultCd.isBulkExecutionSuccess(rc)`. The generic `isSuccess` is
**unchanged**, so the 201/202/203/`00301` legs keep their `0000`-only contract (those code spaces have
no `BT00`). Pinned by `FirmBankingResultCdBulkExecutionTest.bt00IsScopedToTheBulkExecutionLeg`.

### H-1 — Callback IP allow-list fails open · **HIGH** · **FIXED 2026-07-29** · *reclassified*

**Reclassification.** Carried into this report from
[architecture-review.md](../architecture/architecture-review.md) as a parity-adjacent security gap.
Legacy verification shows it is **not a legacy-parity issue at all**: the legacy callbacks
authenticate solely by HMAC (`FILE_DEPOSIT_TRANSFER_API1_act.jsp:102-109`), no callback JSP reads any
IP, `web.xml` has no security-constraint, and `x-coocon-accessIp` (`ConnectionUtil.java:191`) is an
**outbound** header — the opposite of an inbound control. **Classification: security improvement /
deployment-security requirement.**

**Root cause.** The refactor's *added* allow-list short-circuited when empty (`CallbackIpWhitelistFilter:38`)
and trusted `X-Forwarded-For` unconditionally (`:54-58`, with `trust-forwarded-for: true` hardcoded in
all four profiles). Either alone defeats the control.

**Implementation.** Fail-closed `ENFORCE` default; startup validation of malformed entries (fail fast);
403 for an empty-but-well-formed list; CIDR + IPv4/IPv6 support; `X-Forwarded-For` honoured only from a
configured trusted proxy. Local profile opts out **explicitly** with a startup WARN.

**Tests.** `CallbackIpWhitelistFilterTest` — 19 tests.
**Full detail:** [callback-security-configuration.md](../deployment/callback-security-configuration.md).
**Remaining risk:** the real VAN gateway IPs are still unknown — **[CONFIRM: VAN Center]**. Until
supplied, `sit`/`uat`/`prod` will 403 callbacks, which is the intended fail-closed behaviour.

### H-2 — "Unbounded cross-tenant result-check scan" · **HIGH** · **FIXED + partly FALSE POSITIVE**

**The cross-tenant half is `CLOSED AS FALSE POSITIVE`.** Legacy `IDO.TB_FIRM_TRAN_R001.xml` has **no**
`BIZ_KEY` predicate either:

```sql
WHERE TRAN_DT = TO_CHAR(now(),'YYYYMMDD')
  AND PROC_STAT = '6'
  AND TRAN_TM <= TO_CHAR(now() - INTERVAL '2 minute','hh24miss')
  AND TO_CHAR(now() - INTERVAL '2 minute','hh24miss') < '235800'
  AND TRAN_TP = '202'
```

This is a **system batch servicing every tenant** — global scope is the design. Adding a tenant filter
would be the regression, not the fix. Renamed to **"unbounded global scan"**.

**The unbounded half was real, and worse than reported.** Legacy at least had `TRAN_DT` and `TRAN_TM`
*in SQL*; the refactor called `findByTranTpAndProcStat("202","6")` — no date, no age, no order, no
limit — and filtered in Java, materialising every `202`/`'6'` row ever written on each pass.

**Implementation.** New `findResultCheckCandidates(...)` with DB-side `ORDER BY tranDt, tranTm, tranSeq`
+ `Pageable` limit; `wecms.dispatch.result-check.batch-size` (default 100, non-positive falls back
safely rather than breaking the scheduler). The date/age rules **stay in Java** on purpose: the tiered
`isAged` logic (RQ_DTTM as authority) is the documented **D-1 fix** and collapsing it into one SQL date
compare would reintroduce the UTC/ICT bug that stranded rows seven hours a day. Oldest-first ordering
is what makes the limit safe — a young row can never displace an aged one, so a truncated sweep makes
guaranteed forward progress and nothing is permanently skipped. Per-row `REQUIRES_NEW` isolation,
per-bank pacing and the `DISPATCH_RESULT_CHECK` ShedLock identity are unchanged.

**Tests.** `ResultCheckBoundedScanTest` — 18 tests; `ResultCheckPollTest` (12) re-pointed and passing.
**Remaining risk:** with a very large backlog the queue drains over several passes rather than one;
`batch-size` is the tuning knob. No row is lost.

### Not gaps (checked and cleared)

Payment selection · validation · approval-triggered execution · transaction sequence (`wb_get_next_val`
funnel) · bank/channel routing · operating-hours checks · request generation · direct VAN dispatch ·
`01301`/`01302` callback processing · DB status transitions · `TB_FIRM_BAT_API_TRAN` audit records ·
final observable business result — **all verified present**. Retry/recovery is covered **except G-1**.

---

## 9. Deletion recommendations

**No deletions are performed by this audit.** Ordered by risk.

| # | Item | Risk | Approval | Note |
|---|---|---|---|---|
| 1 | Keep `TransferDispatchExecutor` deleted; commit with §5.2 | none | — | Compile verified clean |
| 2 | Fix 2 dangling `{@link}` javadoc refs (§5.2) | none | — | Prevents `javadoc` build failure |
| 3 | Extract 4 identical helper clusters (§5.5) | none | — | Byte-identical bodies |
| 4 | Point 6 local constant copies at `BulkTransferConstants` (§5.4) | low | — | Mind `int`→`long` |
| 5 | Remove 4 of 5 unused repo finders (§5.3, **excluding** `findByTranDtAndProcStepAndProcStat`) | low | **[CONFIRM: batch owner]** | No DDL impact |
| 6 | `findByTranDtAndProcStepAndProcStat` | — | — | **Released 2026-07-29** — G-1 is resolved by the bounded `findBulkExecutionRetryCandidates`; this legacy-shaped finder is now genuinely unused (kept as an operator diagnostic) |
| 7 | Consolidate zone/formatter constants (§5.5) | medium | **[CONFIRM: batch owner]** | Must not fold in `OperatingHoursService`'s overridable zone |
| 8 | `resolveBankPrtnId` / `resolveOwnerNm` (5 diverged copies) | high | **[CONFIRM: PO]** | Parity audit required first |

Items 1–4 are pure refactors with no contract, DTO, or wire-format change — **no `wecms-web` change is
required for any item in this document**.

---

## 10. Final verdict

# `PASS WITH CONDITIONS`

**Why PASS.** The approved 4→1 reduction preserves the legacy business flow. All three removed batches
have verified owners in the Spring implementation; the safety-critical sibling and target-failure gates
are reproduced; a subtle legacy `BATCH_ID` quirk is preserved rather than "corrected"; the DB status
model, `TB_FIRM_BAT_API_TRAN` audit trail, and direct-VAN-API integration outcome all match. Dead code is
minimal and the one obsolete class was already removed — verified by a clean compile, and its removal
deleted a wrong constant rather than losing behaviour.

**Conditions to clear before any non-local deployment:**

1. ~~**G-1** — decide and record the re-drive/detection strategy…~~ **RESOLVED 2026-07-29.** Bounded
   retry sweep + read-only detection sweep implemented, disabled by default, 65 new tests green. See
   [features/wecms-batch/bulk-execution-retry.md](../features/wecms-batch/bulk-execution-retry.md).
   **Residual condition — RESTATED 2026-07-29.** *Not* the coexistence question, which is already
   resolved for DEV/SIT (DB-separated) and is an **Ops** DB-target check for UAT. The sweep must stay
   OFF **everywhere** because it is the K9 **Option A** periodic sweep Mr. Kang was offered and
   **declined** (*"no periodic sweep; monitoring + manual"*).
   **[CONFIRM: Mr. Kang — reopen K9]** →
   [legacy-batch2-ownership-verification.md](../audits/legacy-batch2-ownership-verification.md)
2. ~~**§5.3 row 1** — do not delete `findByTranDtAndProcStepAndProcStat`…~~ **Released.** The retry uses
   the bounded `findBulkExecutionRetryCandidates`; the legacy-shaped finder is now genuinely unused
   and deletable (kept as an operator diagnostic).
3. ~~**G-2** — restore the `00302`-failure ops alert…~~ **RESOLVED 2026-07-29** (permanent codes only,
   matching legacy).
4. **G-3** — `BT00` parity fix landed; no action outstanding.
5. ~~Carry forward **H-1** and **H-2** from [architecture-review.md](../architecture/architecture-review.md)…~~
   **BOTH RESOLVED 2026-07-29.** **H-1** fixed and reclassified as a deployment-security requirement
   (not legacy parity) — see [callback-security-configuration.md](../deployment/callback-security-configuration.md);
   residual condition is the unknown VAN gateway IPs **[CONFIRM: VAN Center]**. **H-2** fixed for the
   unbounded half and closed as a false positive for the cross-tenant half (legacy is global by
   design). **H-3** was the same issue as **G-1** and is closed.

**Not conditions:** the 4→1 reduction itself, the config-disabled jobs, and the documented
implementation improvements in §7.3.
