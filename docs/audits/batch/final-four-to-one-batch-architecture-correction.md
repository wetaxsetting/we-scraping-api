# Final Four-to-One Batch Architecture Correction

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Type:** architecture correction + removal
**Authority:** Mr. Kang's K9 reply — already approved; **no new decision was requested or is required**
**Constraints honoured:** no scheduler enabled, no SQL executed, no live DB touched, no UAT files accessed,
no frontend change, nothing committed or pushed.

---

## 1. Executive summary

`BulkExecutionRetryJob` and its supporting classes have been **removed**. The Spring refactor now matches
Mr. Kang's approved four-to-one architecture exactly: **`ResultCheckJob` is the only scheduled
replacement of the four legacy payment batches.**

The retry sweep was K9 **Option A** — *"Recovery Scheduler (reconciliation) — periodically re-process
aged `STEP=1` rows — re-introduces one bulk scheduled job"* — which was formally offered to Mr. Kang and
**not selected**. He directed inline execution from the `01301` callback. Keeping the sweep, even
disabled, meant carrying an unapproved second scheduled payment batch indefinitely; the earlier
recommendation to preserve it "pending a decision" was itself a mistake, because the decision already
existed.

Also corrected: my own **G-1 finding**, which classified the missing retry as *"a real parity gap."*
It is not. It is an **approved architecture difference** — legacy's retry was an emergent side-effect of
minute-based polling, not a distinct business responsibility, and BATCH2's actual responsibility is fully
reproduced by `issueExecutionInline`.

**One genuine parity defect was found and fixed** along the way, and it survives the removal: the
`00302` leg rejected `BT00`, which legacy treats as success.

`./gradlew cleanTest test` → **275 suites · 2230 tests · 0 failures · 0 errors · 20 skipped** (all skips
pre-existing).

---

## 2. Mr. Kang's confirmed decisions

| Date | Decision | Evidence |
|---|---|---|
| 2026-06-04 | Bulk path re-scoped: BATCH1 → request-time; **BATCH2 → removed**; `01301` issues `00302` inline; only `ResultCheckJob` scheduled | `post-kang-confirmation-review.md:44-51` |
| 2026-06-06 | Written clarification ending **"위와 같은 로직으로 배치 업무 제거"**; BATCH2 = *"API1으로 갈음"*; recorded **"CLOSED — Approved"** | `post-kang-confirmation-review.md:30-38, 48` |
| **2026-06-12** | **K9:** *"Batch 2 제거로 API 응답 정상인 경우 실행 지시로 받아들이도록 프로세스 개선 필요 합니다."* — *since Batch 2 is removed, improve the process so a normal API response becomes the execution instruction* | `questions-for-mr-kang.md:23-25` |
| 2026-06-12 | **K3:** UAT sample files at `/WAS_DATA/file/wecmsbatch/send` | `questions-for-mr-kang.md:195` |
| 2026-06-12 | **WGVN_EFS_204:** confirmed real (guide v1.2.6 §5.6) | `questions-for-mr-kang.md:196` |
| 2026-06-12 | **Bank rules:** only those in `NAPAS_REAL_TIME_BATCH` | `questions-for-mr-kang.md:197` |
| 2026-06-12 | **Currency:** only BATCH1's transfer-blocking checks | ibid. |
| 2026-06-12 | **Cut-off:** legacy logic exists, DB currently 24-hour; removal is a separate decision | ibid. (PO item) |
| 2026-06-19 | K9 closed: *"no periodic sweep; lost callback = monitoring / manual"* | `post-kang-confirmation-review.md:1-6` |

**He was fully informed when he answered K9.** The question document laid out the gap explicitly —
including *"`00302` times out or fails (`BT01`/`G999`/`C999`) → No automatic retry; **funds may never
move**"* — and offered four options. Option A was the recovery scheduler. He chose inline execution.

---

## 3. Legacy four-batch inventory

| Legacy batch | Cron | Responsibility |
|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | `* * * * *` | Poll `TB_PAY_TARGET` `'9'`; operating-hours gate; Woori FX `204`; EFS `202`; status write-back; per-bank pacing |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `*/1 * * * *` | Poll `TB_PAY_FILE_MST` `'9'`; channel classification; currency/network checks; file build; SFTP; EFS `00301` |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | `*/1 * * * *` | Poll `TB_FIRM_FILE_MST` `PROC_STEP='1'/PROC_STAT='6'`; `G998` target gate; sibling gate; EFS `00302`; **implicit retry via re-poll** |
| `PROCESSING_RESULT_CHECK_BATCH` | `*/1 * * * *` | Poll `TB_FIRM_TRAN` `202`/`'6'` older than 2 min; `ORG_STAT` gate; EFS `203`; finalize |

No per-batch enable flag exists — each `.batch` file carries only `name`/`batchClass`/`schedule`/`[INPUT]`.
Container-level `JEX_BATCH` in `jex.prop` decides whether a node runs all four.

---

## 4. Approved Spring one-batch architecture

| Legacy batch | Approved Spring replacement | Scheduled? |
|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | `RealtimeDispatchRegistrar` (after-commit) → `RealtimeDispatchService` → EFS `202` | **No** |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | `DispatchAutoReceiveRegistrar` (after-commit) → `BulkReceptionService` → file → SFTP → `00301` | **No** |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | `BulkReceptionResultService.issueExecutionInline` from the `01301` callback | **No — removed** |
| `PROCESSING_RESULT_CHECK_BATCH` | **`ResultCheckJob` → `ResultCheckService`** | **Yes — the only one** |

Residual recovery = `StuckPayFileMonitorService` (read-only detection + ops alert) + a manual runbook.

---

## 5. Legacy Batch 2 flow (re-confirmed from source)

`FILE_DEPOSIT_TRANSFER_BATCH2.java`:

| Step | Line | Behaviour |
|---|---|---|
| Selection | `:48-54` | `TB_FIRM_FILE_MST_R001` — `TRAN_DT=today AND PROC_STEP='1' AND PROC_STAT='6'`. No age filter, no `ORDER BY`, no `LIMIT`, no tenant predicate |
| Target-failure gate | `:70-104` | `TB_PAY_FILE_MST_R004` counts parents at `PROC_STAT='3'`; `CNT>0` ⇒ `PROC_STEP='2'/PROC_STAT='3'` + `REG_RSLT_CD='G998'` + `continue` |
| Sibling gate | `:108-125` | `TB_FIRM_FILE_MST_R003` counts siblings at `PROC_STEP='0' AND PROC_STAT='6'`; `CNT>0` ⇒ `continue`, no state change |
| Sequence | `:160` | `Sequence.getSeq()` — a **fresh `TRAN_SEQ` per attempt** |
| Audit insert | `:~165` | `TB_FIRM_BAT_API_TRAN_C001`, `API_TRAN_TP='00302'`, **before** the POST |
| POST | `:200` | `ConnectionUtil.connectUrlByPost` |
| Empty body | `:202-206` | `continue` — audit **not** updated, `RS_CD` stays `NULL` |
| Audit update | `:210-229` | `RS_CD`/`RS_MSG`/`RS_DTTM` |
| Transient | `:232-234` | `BT01`/`G999`/`C999` ⇒ `continue` |
| Success | `:242-263` | `0000` **or `BT00`** ⇒ `PROC_STEP='2'` |
| Permanent | `:264-305` | Ops alert (`:280` — the file's **only** `SendMsg`) + `PROC_STEP='2'/PROC_STAT='3'` + `TB_PAY_FILE_MST.PROC_STAT='3'` |
| Transaction | `:236` | `beginTransaction()` opens **after** the POST |

**Retry behaviour:** purely emergent. A `continue` left the row in the selection state; 60 seconds later
the same query re-picked it. The K9 document frames it the same way — BATCH2 *"acted as a self-healing
recovery sweep **in addition to** issuing the execution."*

---

## 6. Spring inline `01301 → 00302` flow (verified against §5)

`BulkReceptionResultService.handle` → `issueExecutionInline`:

| Legacy step | Spring | Parity |
|---|---|---|
| Target-failure `G998` gate | `:171-176` — `TARGET_STAT_ERR` ack | ✅ |
| Sibling gate | `:219-227` — defers `00302` while any sibling is at `PROC_STEP='0'` | ✅ |
| Fresh `TRAN_SEQ` per request | `writeApiTran(..., tranSeq=null)` → `dispatchKeyGenerator.nextTranSeq` | ✅ |
| Audit **before** the call | `:284-287` | ✅ |
| `00302` request fields | `Efs302Request(bankCd, prtnId, fileNm, dataCnt, dataAmt, dataDcmAmt)` | ✅ |
| Envelope `TNO` = allocated `TRAN_SEQ` | `issueExecution` A1 | ✅ |
| Envelope `ID` = `VAN_ORG_CD` | D-2, legacy-verified | ✅ |
| Success `0000` **or `BT00`** | `isBulkExecutionSuccess` | ✅ **fixed — see §7** |
| Success ⇒ `PROC_STEP='2'` | `:301-303` | ✅ |
| Permanent ⇒ `PROC_STAT='3'` both tables + alert | catch block + `failPayFile` | ✅ |
| Transient/empty ⇒ leave `PROC_STEP='1'`, no alert | catch block, `isBulkExecutionRetryable` | ✅ |
| Idempotency | `existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp` — **stronger than legacy**, which had none | ✅ (improvement) |
| Amount split | `AmountSplitter` = legacy `BigDecPaser` | ✅ |

Constants verified identical: `PROC_STEP` `0/1/2/3`, `PROC_STAT` `0/3/6/9`, `G998`, `G999`, `C999`,
`BT00`, `BT01`, `0000`, `FIRM_FILE_NM`, `API_TRAN_TP='00302'`.

---

## 7. Real parity mismatches found and fixed

| # | Mismatch | Legacy evidence | Fix | Status |
|---|---|---|---|---|
| 1 | **`BT00` rejected as a gateway error.** `issueExecution` tested `isSuccess(rc)`, which accepts only `"0000"`, so a legacy-success `BT00` threw `WBE_EFS_GATEWAY_ERROR` and the file stayed at `PROC_STEP='1'` | `:242` — `if("0000".equals(RC) \|\| "BT00".equals(RC))` | `FirmBankingResultCd.isBulkExecutionSuccess` — **302-leg-scoped**; the generic `isSuccess` is unchanged so the 201/202/203/`00301` legs keep their `0000`-only contract | **FIXED**, retained |
| 2 | **No ops alert on `00302` permanent failure.** The catch block wrote the audit row and a `log.warn` only | `:266-280` — the file's only `MsgSendUtil.SendMsg`, in the `:264` permanent `else` | `opsAlertService.notify(...)` with the legacy `이체실행지시 Batch2 API수신 오류 발생` headline, **permanent codes only** (transient stays silent, as legacy did) | **FIXED**, retained |

Both fixes live in the inline path and are unaffected by the removal.

---

## 8. Why automatic retry is not a parity requirement

Two independent grounds:

1. **The approved TO-BE supersedes raw legacy behaviour, and did so knowingly.** Mr. Kang was shown
   *"No automatic retry; funds may never move"* and offered the recovery scheduler as Option A. He chose
   inline execution. A behaviour removed deliberately, with its risk stated in writing, is an approved
   difference — not a gap.
2. **Retry was not a distinct legacy business responsibility.** It was a side-effect of the polling loop
   (`continue` → re-picked next minute). BATCH2's *business* responsibility — issue `00302` under the
   target-failure and sibling gates, with the correct status transitions and audit trail — **is** fully
   reproduced by `issueExecutionInline` (§6).

*Stated plainly:* legacy did retry. That is exactly the behaviour-for-behaviour comparison the approved
architecture overrides.

---

## 9. Code removed

| File | Reason |
|---|---|
| `dispatch/scheduler/BulkExecutionRetryJob.java` | the `@Scheduled` sweep — K9 Option A |
| `dispatch/service/BulkExecutionRetryService.java` | bounded sweep + alert de-duplication |
| `dispatch/service/BulkExecutionRetryTxService.java` | per-file `REQUIRES_NEW` re-issue |
| `dispatch/service/BulkExecutionRetryOutcome.java` | 10 outcomes, retry-only |
| `dispatch/service/BulkExecutionRetryReport.java` | sweep report, retry-only |
| `test/.../BulkExecutionRetryServiceTest.java` | 11 tests |
| `test/.../BulkExecutionRetryTxServiceTest.java` | 15 tests |

Also removed:

* `JobLockNames.DISPATCH_BULK_EXECUTION_RETRY` — replaced by a comment recording *why* no such lock may
  be re-added.
* `wecms.dispatch.bulk-execution-retry.*` (5 keys) from all four profile YAMLs.
* `WECMS_DISPATCH_BULK_EXECUTION_RETRY_*` (5 vars) + §2b from `batch-dispatch-variable-reference.md`.
* `TbFirmBatApiTranRepository.findByTranDtAndBizKeyAndFirmFileNmAndApiTranTpOrderByTranSeqAsc` —
  the attempt-**counting** finder existed only to enforce a retry ceiling.
* `StuckPayFileMonitorService.bulkExecutionMaxAttempts` and the monitor's dependence on
  `bulk-execution-retry.max-attempts`.
* `StuckPayFileCause.BULK_EXECUTION_RETRY_EXHAUSTED` — meaningless without automatic retry.
* Alert de-duplication machinery (`DEDUPED_OUTCOMES`, `alertedOutcomes`) — it existed because three
  retry outcomes recurred every sweep; with no sweep there is nothing to de-duplicate.

`BulkExecutionAlertParityTest` was **rewritten** rather than deleted: its retry-dedup half went, its
code-classification half (which codes are permanent ⇒ alert) is still load-bearing for the inline path.

---

## 10. Code retained

| Component | Why |
|---|---|
| `BulkReceptionResultService.issueExecutionInline` | **This is the approved BATCH2 replacement** |
| `FirmBankingResultCd.isBulkExecutionSuccess` | the `BT00` parity fix (§7) |
| `FirmBankingResultCd.isBulkExecutionRetryable` | still the transient-vs-permanent discriminator for **alert classification**; re-documented so the name cannot be misread as implying retry |
| `TbFirmFileMstRepository.findStrandedBulkExecutionFiles` | **renamed** from `findBulkExecutionRetryCandidates`; now the monitor's read-only detection query |
| `TbFirmBatApiTranRepository.existsBy…ApiTranTp` | inline idempotency guard **and** the monitor's `NOT_ISSUED`/`UNCONFIRMED` discriminator |
| `StuckPayFileMonitorService` + `StuckPayFileMonitorJob` | the prescribed monitoring |
| `ResultCheckJob` / `ResultCheckService` (incl. the H-2 bounded scan) | the one retained scheduled replacement |
| `StrandedRecoveryService` / `StrandedRecoveryJob` | pre-existing, unrelated: it re-drives `PROC_STAT='9'` (dispatch never began), not the `STEP='1'/STAT='6'` execution state. Not a BATCH2 replacement |

---

## 11. Monitoring and manual recovery verification

`StuckPayFileMonitorService` is verified **read-only**:

* no write, no `PROC_STEP`/`PROC_STAT` mutation, no `EFS_302`, no `issueExecution` — pinned by
  `FourToOneBatchArchitectureTest.monitorDoesNotReferenceExecutionEndpoint`;
* `@Transactional(readOnly = true)`; never throws (shares the single scheduler thread);
* bounded selection (`firm-file-batch-size`, default 200) and an age threshold;
* double-gated OFF (`stuck-monitor.enabled=false` + cron `-`).

**Causes re-scoped to remove retry semantics** — the two states need *opposite* manual actions:

| Cause | Meaning | Manual action |
|---|---|---|
| `BULK_EXECUTION_NOT_ISSUED` | no `00302` audit row — the inline issue never ran (lost `01301`, crash) | safe to re-drive |
| `BULK_EXECUTION_UNCONFIRMED` | a `00302` row exists, file never advanced | **reconcile with the VAN Center first** — the bank may already have executed |

Manual runbook: `features/wecms-batch/bulk-execution-retry.md` §11 (retained; the document's retry
sections are struck).

---

## 12. K3 — fixed-length file verification status

Source-level parity is implemented (`FixedLengthCodec`, `DepositHeader`/`Data`/`Trailer`,
`VietnameseText`, `AmountSplitter` = legacy `BigDecPaser`), with `FixedLengthCodecTest` and
`AmountSplitterTest` green.

`/WAS_DATA/file/wecmsbatch/send` is **not accessible from this workspace**, and no sample content was
invented.

> **Source-level parity verified. Byte-level comparison with the UAT reference files remains an
> operational verification.**

---

## 13. WGVN_EFS_204 status

Confirmed by Mr. Kang (guide v1.2.6 §5.6) and **implemented** — `Efs204Request`/`Efs204Response`,
`EfsApiClient.EFS_204`, consumed by `RealtimeTransferService` for the Woori FX pre-step with FX
persistence. No mismatch found. **Not reopened.**

---

## 14. Bank-specific rule verification

All bank-specific behaviour traces to `NAPAS_REAL_TIME_BATCH` (or BATCH1), per Mr. Kang's statement:

| Rule | Location | Legacy source |
|---|---|---|
| Per-bank pacing (Shinhan 100 ms, Woori 3000 ms, **default 3000 ms** — legacy's 100 deliberately not carried over, D-A 2026-08-01) | `DispatchPacer` | `WCConst.PER_DELAY_*`, `NAPAS_REAL_TIME_BATCH:281-286` |
| Woori-only FX `204` pre-step when `ccy ≠ VND` | `RealtimeTransferService:245-250`, `RealtimeDispatchService:303` | `NAPAS_REAL_TIME_BATCH:314-490` |
| VCB file-based transfer (VND + USD) | `isVcbFileTransfer` in the executors | `PymnHelper` latest + Mr. Jung 2026-07-16 (SVN r36822) — documented business decision, not invented |
| Bank/channel operating-hours keys | `OperatingHoursService` + `BulkTransferConstants.BATCH_ID_*` | `NAPAS_REAL_TIME_BATCH:110`, `BATCH1:272/801` |

**No invented bank rules found.**

---

## 15. Currency-rule verification

Per Mr. Kang, only BATCH1's transfer-blocking checks apply. Implemented as **Gate A**:
`BulkTransferValidator` + the `TB_PAY_FILE_DATA_R005` aggregate (`BulkValidationCounts`), consumed by
`BulkReceptionService`. `AntDivChannel` reproduces the `E/V/C/N/S` classification verbatim.

The only currency behaviour beyond that is the **VCB VND/USD file routing** in §14, which has documented
legacy + business evidence. **No invented currency restrictions found.**

---

## 16. Cut-off handling

Framework **kept and configurable**, exactly as directed:

* `CutoffConfig` / `CutoffParam` entities + repositories + `CutoffConfigProvider` — **DB-driven**;
  `@PostConstruct` fails startup on missing/invalid rows (never fail open on payment cut-offs).
* `OperatingHoursService` evaluates `TB_BATCH_SCHEDULE_INFO`/`_DTL` windows and preserves the legacy
  `EXE_DIV` derivation, including the legacy `BATCH_ID` quirk (INFO keyed `NAPAS_REAL_TIME_BATCH`, DTL
  keyed `FILE_DEPOSIT_TRANSFER_BATCH1` — `BATCH1:272` vs `:801`).
* Zone is overridable (`wecms.dispatch.operating-hours.zone`, default `Asia/Ho_Chi_Minh`) so a co-hosted
  legacy batch can be matched bug-for-bug if required.

Nothing was hardcoded to 24-hour operation and no cut-off check was deleted.

> **Code capability:** full legacy-compatible cut-off evaluation.
> **Current DB settings:** 24-hour windows, so the gate is effectively inert today.
> Whether to *remove* the check remains a separate **PO** decision (unchanged).

---

## 17. Customer-specific configuration handling

Preserved as deployment-level separation, per Mr. Kang:

* per-profile YAMLs (`local`/`sit`/`uat`/`prod`) plus `.env.*` templates;
* `haengsung.*` properties (`oracle`, `scheduled-payment-import`, `exchange-rate-sync`,
  `card-account-sync`) independently gated, each `enabled=false` by default, with its own Oracle
  master gate;
* legacy container separation (`jex.prop`, `wecms_batch.properties` vs `wecms_batch_HS.properties`,
  `REAL_01`/`REAL_HS`) documented and untouched.

Product-type behaviour goes through `TenantContext.currentPrdtType()` + `PrdtType`, not hardcoded
customer conditions in shared business logic.

---

## 18. Scheduler inventory — before and after

### Before (dispatch package)

| Job | Class | Kind |
|---|---|---|
| `ResultCheckJob` | legacy-batch replacement | ✅ approved |
| `StrandedRecoveryJob` | refactor-only recovery (`PROC_STAT='9'`) | pre-existing, no legacy twin |
| `StuckPayFileMonitorJob` | monitoring only | ✅ approved |
| **`BulkExecutionRetryJob`** | **second legacy-batch replacement** | ❌ **unapproved** |

### After

| Job | Class | Kind | Default |
|---|---|---|---|
| **`ResultCheckJob`** | **legacy-batch replacement — the only one** | `PROCESSING_RESULT_CHECK_BATCH` | cron `-` |
| `StrandedRecoveryJob` | recovery, refactor-only | not a BATCH replacement | `enabled=false` + cron `-` |
| `StuckPayFileMonitorJob` | monitoring only, read-only | not a BATCH replacement | `enabled=false` + cron `-` |

Outside the dispatch package (unchanged, out of scope): `ExchangeRateBatchScheduler`,
`OperationStatusBatchScheduler`, 3 × HAENGSUNG sync jobs, `NxisasSessionRegistry` (cache maintenance).

**Verified by test:** no scheduled class references `EFS_301`/`EFS_302`/`EFS_202`; those legs are issued
only from request-time, after-commit and callback paths.

---

## 19. Tests executed and results

```
./gradlew compileJava compileTestJava   → exit 0
./gradlew cleanTest test                → exit 0
275 suites · 2230 tests · 0 failures · 0 errors · 20 skipped
```

The 20 skips are all pre-existing `VasAccountIntegrationTest` (untouched). No flaky or
environment-dependent tests observed.

| Suite | Tests | Purpose |
|---|---|---|
| **`FourToOneBatchArchitectureTest`** (new) | **7** | no `BulkExecutionRetry*` class; no retry property in any YAML; no retry lock name; `ResultCheckJob` is the only scheduled payment-batch replacement; no scheduler issues `00301`/`00302`/`202`; monitor references neither `EFS_302` nor `issueExecution`; `ResultCheckJob` exists with a never-fire default |
| `BulkReceptionResultServiceTest` | 12 | inline `01301`→`00302`, gates, ack, audit |
| `BulkExecutionResultServiceTest2` | 4 | `01302` result flow |
| `BulkExecutionAlertParityTest` (rewritten) | 14 | permanent ⇒ alert; transient/empty ⇒ silent; `BT00` success; classifications never overlap |
| `FirmBankingResultCdBulkExecutionTest` | 30 | full `00302` code mapping |
| `StuckFirmFileMonitorTest` (updated) | 10 | detection; `NOT_ISSUED` vs `UNCONFIRMED`; presence-not-count; bounded; disabled |
| `StuckPayFileMonitorServiceTest` | 21 | WAIT sweep, causes, de-duplication |
| `ResultCheckPollTest` · `ResultCheckBoundedScanTest` · `ResultCheckServiceTest` | 12 · 18 · 4 | the one retained scheduler |
| `SchedulerConfigurationContractTest` · `SchedulerCronAndLockIdentityTest` | 24 · 9 | every flag/cron has a safe default; lock identities pinned |

Net: **−26** retry tests removed, **+7** architecture guards added, monitor/alert suites re-scoped.

---

## 20. Documents corrected

| Document | Correction |
|---|---|
| `reports/dead-code-architecture-review.md` | G-1 reclassified from *parity gap* → **approved architecture difference**; §8 heading annotated (no genuine parity gap found); retry conditions removed |
| `architecture/architecture-review.md` | H-3 resolution reworded; retry removal recorded |
| `architecture/bulk-retry-reassessment.md` | outcome updated from "keep disabled" → **removed** |
| `audits/scheduler-legacy-parity-audit.md` | addendum rewritten: no second scheduled job; inventory = one replacement |
| `audits/legacy-batch2-ownership-verification.md` | coexistence condition closed — with the sweep gone there is no second `00302` issuer |
| `features/wecms-batch/bulk-execution-retry.md` | retained as the **legacy-evidence + manual-recovery** record; retry sections struck |
| `features/wecms-batch/README.md` | entry reworded |
| `deployment/sit-uat-scheduler-deployment-checklist.md` · `wecms-batch-activation-runbook.md` · `wecms-batch-environment-variables.md` | retry rows/sections removed |
| `testing/integration-test-audit.md` | G-1 "parity gap" phrasing corrected |
| `batch-dispatch-variable-reference.md` | §2b removed; monitor section documents the two causes + the approved residual model |

Standard wording now used everywhere:

> Legacy Batch 2 automatically retried some transient outcomes as a side effect of minute-based polling.
> The approved Spring architecture intentionally removes Batch 2 and replaces its business
> responsibility with inline `00302` execution from the successful `01301` callback. **Automatic
> scheduled retry is therefore not a required parity item.** `BulkExecutionRetryJob` was **removed**
> because it contradicted the approved four-to-one batch architecture.

---

## 21. Remaining external operational checks

| # | Item | Owner |
|---|---|---|
| 1 | Byte-level comparison of generated fixed-length files against the UAT reference set at `/WAS_DATA/file/wecmsbatch/send` (K3) | Infra / SIT |
| 2 | VAN gateway source IPs for the callback allow-list — until supplied, `sit`/`uat`/`prod` fail closed with 403 (H-1, by design) | VAN Center |
| 3 | `TB_BATCH_SCHEDULE_INFO`/`_DTL` seed on the UAT and PROD DBs (DEV/SIT done) | DBA |
| 4 | EFS `201` returning `RS_CD='1000'` on the shared dev DB (17/17) | VAN Center |
| 5 | Confirm whether the refactor UAT DB is the JEX UAT batch DB — still relevant to `StrandedRecoveryJob`, no longer to `00302` | Ops / Infra |
| 6 | Whether to *remove* the inert cut-off gate (code capability retained either way) | PO |

None of these is an architecture mismatch.

---

## 22. Final verdict

# `PASS WITH OPERATIONAL CONDITIONS`

The architecture now matches Mr. Kang's approved four-to-one design with no mismatch remaining. The
conditions above are **all external**: unavailable UAT sample files, unsupplied VAN gateway IPs and
credentials, and DBA seeding. No condition preserves an architecture deviation.

---

## Final answers

**1. Does Spring now match the approved four-to-one architecture?**
**YES.** `ResultCheckJob` is the only scheduled replacement of the four legacy payment batches; the other
three responsibilities run request-time / after-commit / callback-driven. Pinned by
`FourToOneBatchArchitectureTest`.

**2. Is `FILE_DEPOSIT_TRANSFER_BATCH2` fully removed from the Spring design?**
**YES.** No scheduled equivalent exists. Its business responsibility lives in
`BulkReceptionResultService.issueExecutionInline`.

**3. Does successful `01301` processing issue `00302` inline?**
**YES** — after the target-failure `G998` gate and the split-file sibling gate, with audit-before-call
ordering, a fresh `TRAN_SEQ` as the envelope `TNO`, and `PROC_STEP 1→2` on a confirmed response
(`0000` or `BT00`).

**4. Was `BulkExecutionRetryJob` removed?**
**YES** — job, service, tx-service, outcome enum, report record, both test classes, the ShedLock
identity, 5 YAML keys × 4 profiles, 5 env vars, and the attempt-counting repository method.

**5. Is automatic scheduled `00302` retry still present anywhere?**
**NO.** Verified three ways: no `BulkExecutionRetry*` source file, no `bulk-execution-retry` property in
any YAML, no retry lock name in `JobLockNames` — plus a test asserting no scheduled class references
`EFS_302`.

**6. Is `ResultCheckJob` the only scheduled payment-batch replacement?**
**YES.** `StuckPayFileMonitorJob` is monitoring-only (read-only, verified) and `StrandedRecoveryJob`
targets `PROC_STAT='9'` with no legacy twin — neither replaces one of the four batches.

**7. Is monitoring still available for stranded bulk-transfer records?**
**YES.** `StuckPayFileMonitorService`'s second sweep detects `PROC_STEP='1'/PROC_STAT='6'` past a
threshold and classifies `BULK_EXECUTION_NOT_ISSUED` vs `BULK_EXECUTION_UNCONFIRMED` — two states that
need opposite manual actions — with ops alerting and a manual runbook. Read-only, bounded, off by default.

**8. Were all verified legacy bank and currency rules preserved?**
**YES.** Bank rules trace to `NAPAS_REAL_TIME_BATCH` (per-bank pacing, Woori FX `204`) plus the
documented VCB file-routing decision; currency rules are BATCH1's transfer-blocking checks via
`BulkTransferValidator` + `AntDivChannel`. No invented rules found.

**9. Were cut-off rules kept configurable?**
**YES.** `CutoffConfig`/`CutoffParam` remain DB-driven with fail-fast validation, and
`OperatingHoursService` preserves the legacy `EXE_DIV` evaluation with an overridable zone. Nothing
hardcoded to 24 hours; no check deleted.

**10. What operational verification, if any, remains?**
Six external items (§21): UAT fixed-length sample comparison (K3), VAN gateway IPs, UAT/PROD
`TB_BATCH_SCHEDULE` seed, EFS `201` `RS_CD='1000'`, the UAT DB-target question for
`StrandedRecoveryJob`, and the PO's separate cut-off-removal decision. **No architecture work remains.**
