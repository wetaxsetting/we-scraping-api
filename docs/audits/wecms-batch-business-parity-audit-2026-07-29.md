# WeCMS Batch Refactoring — Legacy vs Refactor **Business Parity** Audit

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) vs Legacy (`WECMS_BATCH DEV/`)
**Scope:** the 4 legacy payment batch programs + the 2 legacy VAN callback endpoints they depend on
**Frame:** business parity, **not** architectural parity. The four-to-one batch consolidation, the
event-driven trigger model, and direct VAN API calls are **Mr. Kang-approved design changes** and are
NOT reported as defects.

**Method:** every legacy batch was read line-by-line together with its IDO SQL (selection predicates,
UPDATE column sets, join keys) and its callback JSP; each was then traced to the Spring component that
owns the same responsibility and compared decision-by-decision and column-by-column. Every finding
below cites both sides. No claim is made from documentation — several existing refactor docs were found
to be stale and are listed in §10.

---

## 1. Executive Summary

The refactor reproduces legacy business behaviour with **high fidelity**. Selection predicates, the
validation gate, the operating-hours `EXE_DIV` logic, key generation, status transitions, the
fixed-length file layout, amount splitting, Vietnamese tone normalisation, the `BT00` 00302
success rule, the `0001` "no original transaction" rule, and the failure-only alert policy all match.
Several legacy defects were deliberately fixed and are documented as such.

**Two genuine business-parity defects were found**, both on the bulk (file) rail:

| # | Finding | Impact |
|---|---|---|
| **H-1** | The 01302 handler looks up `TB_FIRM_FILE_DATA` by the **`R_`-prefixed result filename**, but those rows are stored under the **`S_`-prefixed sent filename**. Legacy joins with `substring(FIRM_FILE_NM,3)`. | **Per-line transfer results are silently discarded.** `TB_FIRM_FILE_DATA` and `TB_PAY_FILE_DATA` never receive `PROC_RSLT_DIV` / `RS_CD`; the file still advances to `PROC_STEP='3'` so the loss is invisible. |
| **H-2** | A **permanent 00302 failure** leaves `TB_FIRM_FILE_MST` at `PROC_STEP='1'/PROC_STAT='6'` and the pay-file at `'6'`. Legacy sets both to `'3'`. | A bulk payment the bank permanently rejected is shown to the user as **still processing**, forever. |

Plus **6 MEDIUM** items (operating-hours timezone, three bank-visible callback response codes, an added
dispatch-time validation gate that strands rows silently, the missing file-round wrap, unpaced
event-driven VAN requests, and lost `TB_FIRM_TRAN` balance columns on a failed 202) and **10 LOW**
items, most of which are deliberate improvements.

**Verdict:** `PASS WITH TWO REQUIRED FIXES` (H-1, H-2). Neither is an architecture problem; both are
localized code defects on the bulk rail. All dispatch flags being OFF by default means neither is
currently live — but both must be closed before the bulk rail is enabled.

---

## 2. Legacy → Refactor Flow Mapping

### 2.1 `NAPAS_REAL_TIME_BATCH` → real-time 202 (approved: after-commit, not scheduled)

```
TB_PAY_TARGET_R001  (TRAN_DT=today AND PROC_STAT='9'; no tenant/order/limit)
  ↓  RealtimeDispatchRegistrar.registerAfterCommit          [afterCommit, per-target isolation]
  ↓  RealtimeDispatchService.dispatch                       [@Transactional REQUIRES_NEW]
  ├─ TbPayTargetRepository.findByBizKeyAndTranDtAndTranSeq  → re-read, must be '9'      (R001)
  ├─ TbFirmTranRepository.existsByTranDtAndBizKeyAndTranSeq → idempotency  (refactor addition)
  ├─ TbFirmConfRepository.findById(bizKey,outBankCd,wdrlActNo)                          (TB_FIRM_CONF_R001)
  ├─ OperatingHoursService.evaluateRealtime                 (TB_BATCH_SCHEDULE_INFO/DTL_R001)
  │    CUTOFF|NO_CRITERIA → TB_PAY_TARGET '3' + OpsAlert + return          (legacy :191-226)
  │    SKIP               → return, no write, no alert                     (legacy :227-230)
  ├─ VietnameseText.replaceTone(ownerNm, tranUsag)                         (legacy :261-270)
  ├─ [Woori && ccy≠VND] runFxInquiry → EFS_204 → TB_FIRM_TRAN '204' (_U005) + TB_PAY_TARGET FX (_U002)
  ├─ TbPayTargetRepository.claimForDispatch '9'→'6'         (atomic claim; refactor addition)
  ├─ TB_FIRM_TRAN insert TRAN_TP='202' PROC_STAT='6', TRAN_SEQ = pay-target seq         (C001)
  ├─ RealtimeTransferService.transfer → EFS_202
  └─ applyOutcome → TB_FIRM_TRAN (_U004) + TB_PAY_TARGET (_U001)
       0000     → both '0'
       G999/∅   → both '6'  → ResultCheckService confirms via 203
       other    → both '3'  + OpsAlert
```

### 2.2 `FILE_DEPOSIT_TRANSFER_BATCH1` → bulk reception (approved: request-time)

```
TB_PAY_FILE_MST_R001  (PROC_STAT='9' AND TRAN_DT=today)
  ↓  DispatchAutoReceiveRegistrar (afterCommit)
  ↓  BulkReceptionService.receive                           [no outer tx; per-file REQUIRES_NEW]
  ├─ TbFirmConfRepository.findById                                                     (TB_FIRM_CONF_R001)
  ├─ TbPayFileDataRepository.aggregateValidationCounts      → verbatim R005 port
  ├─ BulkTransferValidator.validate  → RS_MSG + '3' + alert + return    (Gate A, legacy :113-266)
  ├─ OperatingHoursService.evaluateBulk (per-channel DTL)               (legacy :269-448)
  ├─ calculateBatchDivision(BAT_TRAN_BRANCH_DIV, reqCnt, BAT_TRAN_BRANCH_LIMIT)        (legacy :932-961)
  ├─ BulkDispatchClaimTxService.claimForReception '9'→'6'   (atomic; legacy U001 :453-459)
  ├─ nextFileRound  (MAX(FILE_SEQ)+1)                                                  (R002)
  └─ per split file → buildAndDispatchFile  [REQUIRES_NEW]
        TB_FIRM_FILE_MST insert STEP='0'/STAT='6'                                      (C001)
        TB_FIRM_FILE_DATA insert per line (line seq restarts at 1)                      (C001)
        FixedLengthCodec.encode(H/D/T) → local file → SftpClient.upload
        TB_FIRM_BAT_API_TRAN insert '00301'  ← BEFORE the call                          (C001)
        issueReception → EFS_301  →  update audit RS_CD/RS_MSG/RS_DTTM                  (U001)
        failure → failBoth: MST STEP='0'/STAT='3' + pay-file '3', break (R004 skip parity)
```

### 2.3 `FILE_DEPOSIT_TRANSFER_BATCH2` → **removed**; responsibility inline in the 01301 callback

```
VAN 01301 → BulkReceptionResultController → BulkReceptionResultService.handle   [@Transactional]
  ├─ VanApiKeyService.getApiKey(envelope.id) → verifyVv → decryptEv       (legacy API1 steps 1-2)
  ├─ validateRequest (8 fields)                                          (legacy API1 step 3)
  ├─ findByTranDtAndFirmFileNmAndProcStepAndProcStat('0','6')            (TB_FIRM_FILE_MST_R004)
  ├─ siblingAlreadyFailed → G998 audit + failFile + ack                  (TB_PAY_FILE_MST_R004)
  ├─ TB_FIRM_BAT_API_TRAN insert '01301', TRAN_SEQ = callback TNO        (C001, legacy :200)
  ├─ result_cd≠BT00        → failFile STEP='1'/STAT='3'                  (legacy :247-310)
  ├─ cnt/amt mismatch      → failFile REG_RSLT_CD='9999'                 (legacy :347-382)
  ├─ BT00 + match          → STEP 0→1, STAT='6', REG_*                   (legacy :386-404)
  └─ sibling gate (no sibling at STEP='0') → issueExecutionInline per ready sibling
        TB_FIRM_BAT_API_TRAN insert '00302' (fresh TRAN_SEQ = TNO) ← BEFORE the call
        issueExecution → EFS_302 (success = 0000 **or BT00**)            (legacy BATCH2 :242)
        success → audit '0000' + MST STEP 1→2                            (legacy :242-263)
        failure → audit RC + OpsAlert (permanent only)                   (legacy :266-280)
```

### 2.4 `PROCESSING_RESULT_CHECK_BATCH` → `ResultCheckJob` (**the one retained scheduled job**)

```
@Scheduled(cron ${wecms.dispatch.result-check.cron:-}) + @SchedulerLock
  ↓  ResultCheckService.runOnce
  ├─ findResultCheckCandidates(TRAN_TP='202', PROC_STAT='6', limit)                     (R001)
  ├─ selectAged  (UTC today|yesterday; RQ_DTTM / TRAN_DT / TRAN_TM tiers, 2-min delay)
  └─ per row → reflectOutcome
       ├─ TB_FIRM_CONF → ORG_STAT must be '1' else skip + pace                (legacy :107-110)
       ├─ TB_FIRM_TRAN insert TRAN_TP='203' STAT='6' + ORG_TRAN_DT/SEQ        (C001)
       ├─ inquire → EFS_203    (0001 → notFound; other non-0000 → throw, row stays '6')
       ├─ 203 row → STAT='0' + confirmed detail                              (U003)
       ├─ DispatchResultTxService.finalizeOriginalAndPayTarget [REQUIRES_NEW] (U004 + U001)
       └─ org_resp_cd≠0000 → "처리결과조회 Batch 원거래오류" alert            (legacy 6-2 :391)
       DispatchPacer.pause(outBankCd)   Woori 3000 / Shinhan 100 / else 3000 (D-A: default != legacy 100)
```

---

## 3. Business Logic Parity

### 3.1 Confirmed identical

| Rule | Legacy | Refactor | ✅ |
|---|---|---|---|
| Realtime selection state | `PROC_STAT='9'`, today | `ProcStat.WAIT` re-read + claim | ✅ |
| Bulk selection state | `PROC_STAT='9'`, today | same | ✅ |
| Result-check selection | `TRAN_TP='202' AND PROC_STAT='6'`, aged 2 min | same (+ tiered aging) | ✅ |
| Gate A overseas rules | `:146-162` swiftOut / ccy-mix | `BulkTransferValidator:79-82` | ✅ |
| Gate A EBANK rules | `:166-183` Shinhan-only, no-mix, single-ccy | `:85-88` | ✅ |
| Gate A VCB rules | `:184-201` Woori-only, no-mix, single-ccy | `:89-92` | ✅ |
| Woori prohibitions | `:204-216` no NAPAS, no FX transfer | `:97-100` | ✅ |
| `FAIL_CHNL_CNT` unreachable branch | `:153` identical expression to `:148` | reproduced as one check + documented | ✅ |
| R005 aggregate | `IDO.TB_PAY_FILE_DATA_R005.xml:24-47` | verbatim native port, `TbPayFileDataRepository:28-47` | ✅ |
| `EXE_DIV` CASE | `INFO_R001.xml:29-32` | `OperatingHoursService.exeDiv:255-263` | ✅ |
| INFO key quirk (BATCH1 uses `NAPAS_REAL_TIME_BATCH`) | `BATCH1:272` vs `:801` | preserved, `:140` / `:197` | ✅ |
| Realtime: missing DTL row → fail closed | `:155-160` | `NO_CRITERIA`, `:172-175` | ✅ |
| Bulk: missing DTL row → pass | `:814-817` | `continue`, `:226-228` | ✅ |
| Bulk channel precedence (any `N` wins over `E`) | `:299-397` | `:229-237` | ✅ |
| `ANT_DIV_CD='INTER'` skips DTL | `:138` | `:165` | ✅ |
| Woori/non-VND 204 pre-step, blank ccy = VND | `:290-293` | `isWooriNonVnd:301-304` | ✅ |
| 204 failure → skip 202, pay-target untouched | `:443-483` `continue` | `:206-212` | ✅ |
| 204 no-response → leave 204 row at `'6'` | `:358-362` | `inquireFxRate` returns null, `:356-361` | ✅ |
| Branch split `ceil(reqCnt/limit)` | `calculateBatchDivision:932-961` | `:586-595` (+ safe limit≤0 degrade) | ✅ |
| Split window by SEQ **value** range | `:470-471,506-507,527-528` | `:337-345` | ✅ |
| Line seq restarts at 1 per file | `datailSeq1` `:598-603` | `seq` `:432-435` | ✅ |
| Tone normalisation set (5 bulk fields + 2 realtime) | `:634-668` / `:261-270` | `:452-455,468` / `:176-177` | ✅ |
| `RCPT_USE_ADDR` 180-byte truncation | `getStrByteMaxLen(...,180)` `:638` | `byteTruncate:681-696` | ✅ |
| Card/account flag `C→N else Y` | `:629-630` | `:482` | ✅ |
| Amount split (`BigDecPaser`) | `StrUtil.BigDecPaser` | `AmountSplitter` | ✅ |
| 00302 success = `0000` **or `BT00`** | `BATCH2:242` | `isBulkExecutionSuccess`, `:511` | ✅ |
| 01301 reception success = `BT00` only | `API1:247` | `RECEPTION_SUCCESS_CD`, `:183` | ✅ |
| 203 `0001` → original FAIL | `:249-253,296` | `notFound()` → `isSuccess('0001')=false` | ✅ |
| `ORG_STAT='1'` gate — result-check **only** | `:107` (absent from BATCH1/NAPAS) | `ResultCheckService:470`; verified absent elsewhere | ✅ |
| Alerts on failure only | 202 `:685`, 204 `:465`, 203 `:222`/`:378`, BATCH1 `:249`/`:424`/`:751`, BATCH2 `:273` | all reproduced, success paths silent | ✅ |
| Pay-file final status `0`/`3`/`1` from **file-level** counts | `R005` over `TB_FIRM_FILE_MST.PROC_STAT` | `finalizePayFileStatus:305-318` | ✅ |
| Sequence source | `FIRM_SEQ_NO_R001` (`firm_seq_no`) | `LegacyFunctionRepository` → `wb_get_next_val` | ✅ |
| File round scope (no `BIZ_KEY`) | `R002` groups by bank+partner only | `nextFileRound:641-654` | ✅ |
| `FILE_TYPE` used raw (no trim) | `:533,540,548` | `:401,412` + comment | ✅ |

### 3.2 Divergences

#### H-2 · Permanent 00302 failure does not fail the file or the pay-file — **HIGH**

* **Legacy:** `FILE_DEPOSIT_TRANSFER_BATCH2.java:264-320` — on any non-`0000`/non-`BT00`,
  non-transient RC the `else` branch sends the alert **and** writes
  `TB_FIRM_FILE_MST_U001` with `PROC_STEP='2'` + `PROC_STAT='3'` (`:283-289`) **and**
  `TB_PAY_FILE_MST_U001` with `PROC_STAT='3'` (`:300-304`), then commits (`:317-320`).
* **Refactor:** `BulkReceptionResultService.issueExecutionInline` catch block
  (`:308-345`) writes the audit row's `RS_CD`/`RS_MSG`/`RS_DTTM` and fires the ops alert —
  and nothing else. `file.setProcStat(...)` is never called on this path, and no pay-file write
  exists. The file stays `PROC_STEP='1'/PROC_STAT='6'`; the pay-file stays `'6'`.
* **Failure scenario:** a bulk payroll file is received (`BT00`, totals match) → `PROC_STEP='1'`.
  The inline 00302 returns a permanent gateway error (e.g. `G001` — required field missing).
  Legacy: file `'3'`, pay-file `'3'` → the Payment Result screen shows **Failed**, and the
  `TB_PAY_FILE_MST_R004` sibling gate blocks the remaining split files.
  Refactor: both stay `'6'` → the screen shows **Processing** indefinitely, the sibling gate never
  trips, and the only signal is a Slack alert plus a `log.warn`.
* **Also:** the log message on `:325-327` says the file is left "for the bulk-execution retry
  sweep" — that sweep was **removed** on 2026-07-29. The message is now misleading.
* **Note on the approved-change boundary:** the *absence of automatic retry* is approved (K9 —
  Mr. Kang chose inline execution over the recovery scheduler). Failing to record the **terminal
  failure state** is not part of that decision: legacy's status write was in the permanent branch,
  not the retry path, and `StuckPayFileMonitorService` classifies this state as
  `BULK_EXECUTION_UNCONFIRMED`, which is the wrong label for a bank that answered with a
  permanent rejection.
* **Fix:** in the catch block, when `!isBulkExecutionRetryable(rc)`, set
  `MST PROC_STEP='2' / PROC_STAT='3'` and `TB_PAY_FILE_MST PROC_STAT='3'`. Transient codes keep
  today's behaviour (legacy `continue`d on `BT01`/`G999`/`C999` without a status write).
* **Documentation correction:** `audits/final-four-to-one-batch-architecture-correction.md` §6
  claims *"Permanent ⇒ `PROC_STAT='3'` both tables + alert | catch block + `failPayFile` ✅"*.
  `failPayFile` **does not exist anywhere in the codebase** (verified by full-tree grep). That row
  is false and must be corrected.

#### M-3 · Dispatch-time blocked-char guard is an added gate that strands rows silently — **MEDIUM**

* **Legacy:** neither batch performs any character validation at dispatch time. The record is
  written to the file / request as-is (after tone normalisation).
* **Refactor:** `RealtimeDispatchService:185-189` and `BulkReceptionService:458-464` call
  `PaymentBlockedCharGuard.assertCleanForDispatch`, which throws
  `BusinessException("WBE_PAYMENT_BLOCKED_CHARS")` (`PaymentBlockedCharGuard:79-85`).
* Neither call site catches it. Realtime: it propagates out of the `REQUIRES_NEW` unit → rollback →
  caught by `RealtimeDispatchRegistrar:67-71` → `log.error`. The pay-target stays `'9'`.
  Bulk: it escapes `buildAndDispatchFile` (the `try` wraps only `issueReception`), rolls back that
  split file, and escapes `receive()`. The claim already committed separately, so the pay-file is
  left at `'6'`.
* **Impact:** a row legacy would have dispatched is **silently not dispatched** — no `'3'` status,
  no `RS_MSG`, no ops alert, no user-visible failure. This is the same stranding class as R-1.
* This gate traces to audit task-44508 (a WeCMS-side P0 decision), so the *gate* may be intended —
  but its **failure handling** is not legacy-equivalent. Recommend: catch it and record a FAIL
  status + alert on both rails, exactly as a gateway business error is recorded today.

#### M-1 · Operating-hours windows evaluated in a different timezone — **MEDIUM**

* **Legacy:** `EXE_DIV` is computed in SQL as `TO_CHAR(NOW(),'HH24MISS')`
  (`IDO.TB_BATCH_SCHEDULE_INFO_R001.xml:29-31`) — the **database server's** zone. The JEX frame DB
  runs `Asia/Seoul` (measured 2026-07-28), so a `090000-153000` window means 09:00–15:30 **KST** =
  07:00–13:30 ICT.
* **Refactor:** `OperatingHoursService.nowHms():266-268` evaluates in
  `wecms.dispatch.operating-hours.zone`, default `Asia/Ho_Chi_Minh` — **2 hours later** than legacy
  for the same row.
* This is a documented, deliberate divergence (`:70-87`) chosen so the dispatch-time gate cannot
  contradict the request-time `CutoffValidator`, and it is overridable to `Asia/Seoul` for
  bug-for-bug parity. It is nonetheless a **timing-behaviour change**, and if a refactor node ever
  shares a DB with the live JEX batch the two engines will gate the same payment differently.
* **Action:** PO/Ops decision — either accept ICT as the corrected business meaning, or set
  `Asia/Seoul` in any coexisting environment. Currently inert only because the seeded windows are
  24-hour.

#### M-4 · File round (`FILE_SEQ`) wrap is not implemented — **MEDIUM**

* **Legacy:** `IDO.TB_FIRM_FILE_MST_R002.xml:34-49` wraps the next round back to `1`:
  **> 99 for Woori (`970457`)**, **> 999 for every other bank**.
* **Refactor:** `BulkReceptionService.nextFileRound:641-654` is a bare `max + 1` with no ceiling;
  `String.format("%03d", …)` at `:346`.
* **Failure scenario:** the 100th Woori file of one day gets `FILE_SEQ="100"` where legacy would send
  `"001"` — outside the bank's accepted 1–99 round range, in both `FIRM_FILE_NM` and the fixed-length
  header `iterationInfo`. Past 999 (any bank) `%03d` yields a **4-character** value, overflowing a
  3-byte header field and changing the record length.
* Low likelihood (needs 100+ split files per bank per day), bank-visible impact.
* **Fix:** port the two ceilings into `nextFileRound`.

#### M-5 · `TB_FIRM_TRAN` balance columns lost on a failed 202 — **MEDIUM**

* **Legacy:** `NAPAS_REAL_TIME_BATCH:597-607` verifies + **decrypts the response before** testing
  `RC` at `:610`, so the failure branch at `:645-659` has the decrypted body and writes
  `BAL_AMT_SIGN`, `BAL_AMT` and `ANT_DIV_CD` via `TB_FIRM_TRAN_U004`.
  (`FINC_MDA_OWNER_NM` / `BAL_DCM_AMT` are also bound but `U004`'s SQL has no such columns, so those
  two are no-ops — the refactor correctly omits them.)
* **Refactor:** `RealtimeTransferService:216-219` throws on non-`0000` **before** decrypting, so
  `applyFail → writeStatus:465-481` leaves `BAL_AMT`, `BAL_AMT_SIGN`, `ANT_DIV_CD` null.
* **Impact:** Firm Banking History shows no post-transaction balance or resolved network for failed
  real-time transfers. Audit-completeness only — no money-movement effect.

#### M-2 · Three 01301 callback response codes differ from legacy (bank-visible) — **MEDIUM**

Legacy `FILE_DEPOSIT_TRANSFER_API1_act.jsp` reaches step 6 (`:409-423`) on **every** non-throwing
path, so it always answers `RC=0000` with a **freshly rebuilt** `EV` over the six echo fields
`{bank_cd, prtn_id, file_nm, data_cnt, data_amt, data_dcm_amt}`. Only a thrown exception changes `RC`.

| Branch | Legacy RC | Legacy EV | Refactor RC | Refactor EV | Evidence |
|---|---|---|---|---|---|
| File not at `STEP='0'/STAT='6'` (unknown / already progressed) | **`G004`** (throws `:168-170`) | echo | **`0000`** | rebuilt | `:153-157` |
| Sibling fail-stop (`G998`) | `0000` (`:195-242` does not throw) | rebuilt | **`G998`** | echo | `:167-179` |
| Count/amount mismatch | `0000` (`:347-382`) | rebuilt | **`9999`** | echo | `:199-207` |
| `result_cd ≠ BT00` | `0000` | rebuilt | `0000` ✅ | echo ✗ | `:192-197` |
| Success | `0000` ✅ | rebuilt ✅ | `0000` ✅ | rebuilt ✅ | `:240` |

* **Impact:** the VAN Center's retry/alerting decision is driven by this `RC`. Legacy's `G004` on an
  unknown file makes the gateway retry and raises a Slack alert; the refactor's `0000` tells it to
  stop. Conversely the refactor returns hard errors (`G998`/`9999`) where legacy returned `0000`,
  which may trigger gateway-side retries of a callback WeCMS has already fully processed.
* The refactor's choices are defensible (idempotent-replay suppression; surfacing the real outcome),
  but they are **bank-visible behaviour changes** and are not covered by the approved architecture
  change. **Action:** confirm the intended contract with the VAN Center, then either restore legacy
  codes or record the new contract as an approved change.

#### M-6 · Event-driven 202 dispatch is unpaced — **MEDIUM (observation)**

* **Legacy:** every batch sleeps `WCConst.PER_DELAY_*` between rows — Woori **3000 ms**, Shinhan /
  default 100 ms (`NAPAS_REAL_TIME_BATCH:275-279,698`). This is a rate limit toward the VAN/bank,
  not just polling hygiene.
* **Refactor:** `DispatchPacer` is applied **only** in `ResultCheckService.runOnce` (`:316-318`).
  `RealtimeDispatchRegistrar.afterCommit:64-72` loops over every `tranSeq` back-to-back with no
  delay; `BulkReceptionService` likewise fires split-file 00301s ungapped.
* **Impact:** approving 500 Woori VND transfers issues 500 sequential 202s with no gap, where legacy
  spread them over ~25 minutes. Request **rate** toward the bank changes even though each request is
  identical. Whether the gateway tolerates this is an external question.
* **Action:** confirm the VAN rate limit; if one exists, apply `DispatchPacer` in the two registrar
  loops. Pacing is present and correct for the one retained scan loop.

---

## 4. Database Parity

### 4.1 `TB_PAY_TARGET`

| Operation | Legacy | Refactor | Verdict |
|---|---|---|---|
| Select | `R001` today + `'9'` | same + claim | ✅ |
| `'9'→'3'` cutoff/no-criteria | `U001` (`:194-205`) | `:161-163` | ✅ |
| `'9'→'6'` no-response/G999 | `U001` (`:576-587`) | `applyPending:454-456` | ✅ |
| `'9'→'6'` claim | *(none — read-then-write)* | `claimForDispatch` | ✅ improvement |
| `→'0'` / `→'3'` final | `U001` (`:631-642` / `:668-679`) | `writeStatus:475-480` | ✅ |
| FX columns (204 success) | `U002` (`:424-439`), `PROC_STAT` untouched | `:380-385` | ✅ |
| `PROC_DTTM` on every write | `U001`/`U002` set it | set on every path | ✅ |
| `FINC_MDA_OWNER_NM` on 202 success | **not written** (`:631-635`) | **written** from the 202 response | ⚠️ **L-1** |
| `FINC_MDA_OWNER_NM` on 202 fail | not written (`:668-672`) | not written (`applyFail` passes null) | ✅ |
| `FINC_MDA_OWNER_NM` on 203 confirm | written, coalesced (`:322`) | `DispatchResultTxService:96-98` | ✅ |

### 4.2 `TB_PAY_FILE_MST`

| Operation | Legacy | Refactor | Verdict |
|---|---|---|---|
| Select | `R001` `'9'` + today | same | ✅ |
| Validation fail `→'3'` + `RS_MSG` | `U001` (`:234-241`) | `:233-235` | ✅ |
| Cutoff `→'3'`, `RS_MSG` empty | `U001` (`:415-422`), `vrfi_msg=""` | `:267-270` | ✅ |
| No-criteria `→'3'` + verbatim message | `:289` | `NO_CRITERIA_MSG:150-151` | ✅ |
| `'9'→'6'` | `U001` (`:453-459`) | atomic claim | ✅ improvement |
| SFTP/00301 fail `→'3'` | `UpdatePayFileMst` (PROC_STAT only) | `failBoth` also sets `RS_MSG` | ⚠️ L-4 |
| 01301 reception fail `→'3'` | `U001` (`:298-310`) | `failFile:363-370` | ✅ |
| Final `0`/`3`/`1` | `U002` from `R005` file counts | `finalizePayFileStatus` | ✅ |
| Permanent 00302 fail `→'3'` | `U001` (`:300-304`) | **missing** | ❌ **H-2** |

### 4.3 `TB_PAY_FILE_DATA`

| Operation | Legacy | Refactor | Verdict |
|---|---|---|---|
| Read for file build | `R001` range, `R002` totals | `findByBizKeyAndTranDtAndFileSeqNo` + window | ✅ |
| Validation aggregate | `R005` | verbatim native port | ✅ |
| Per-line result reflect | `U001` — `A.SEQ = B.ORG_SEQ` join from `TB_FIRM_FILE_DATA` | keyed off `firmDataBySeq`, which is empty in production | ❌ **H-1** |

### 4.4 `TB_FIRM_API_TRAN` (`TB_FIRM_BAT_API_TRAN`)

| Operation | Legacy | Refactor | Verdict |
|---|---|---|---|
| `00301` insert before call | `C001` (`:706-718`) | `writeApiTran` then `issueReception` | ✅ |
| `00301` `TRAN_SEQ` = envelope `TNO` | `seq` (`:703,1204`) | `audit.getTranSeq()` → `cmd.tno()` | ✅ |
| `00301` result update | `U001` (`:729-740`) | `:529-532` | ✅ |
| `00301` failure → audit **not** updated | throw skips `:729` | audit updated with RC | ⚠️ L-3 (improvement) |
| `00302` insert before call | `C001` (`:163-175`) | `:288-289` | ✅ |
| `00302` fresh `TRAN_SEQ` per attempt | `Sequence.getSeq()` (`:160`) | `tranSeq=null` → fresh | ✅ |
| `00302` empty body → `RS_CD` NULL | `:202-206` `continue` | `RS_CD='G999'` | ⚠️ L-3 (improvement) |
| `01301` `TRAN_SEQ` = callback `TNO` | `:200` | `envelope.tno()`, `:189` | ✅ |
| `01301` `REG_RSLT_CD/MSG` + `RS_CD/MSG` + `RS_DTTM` | `:207-211` etc. | `writeApiTran(...)` full set | ✅ |
| `01302` `TRAN_SEQ` = callback `TNO` | `:185` | `:398-399` | ✅ |
| `01302` `TRAN_DT` = processing date, not filename date | `:85,182` | `procDt`, `:183` | ✅ |
| `01302` audit survives a parse rollback | committed before parse tx | `REQUIRES_NEW` writer | ✅ |

### 4.5 `TB_BATCH_SCHEDULE_INFO` / `_DTL`

Read-only on both sides — no INSERT/UPDATE/DELETE in either. Keys, `EXE_DIV`, `ANT_WHERE_USE_YN`,
the per-batch `BATCH_ID` quirk, and both missing-row policies match (§3.1). Two notes:

* **`ANT_DIV_CD` canonicalisation (deliberate divergence — rewritten 2026-08-03).** Legacy uses the
  **raw** column value as the realtime DTL key (`:144`); `OperatingHoursService` canonicalises first
  via `AntDivChannel.canonical`.

  The previous version of this bullet was wrong on two counts. (1) `TB_PAY_TARGET.ANT_DIV_CD` does
  **not** "carry both widths" as drift — it holds `AntDivCd.getValue()`, an enum that is mixed-width
  by design (`INTER`/`NAPAS`/`CITAD` 5-char; `SWIFT`/`EBANK`/`VCB` 1-char). All 101 DEV rows conform;
  `'E'` and `'S'` are correct values. (2) It claimed the four measured rows *"now dispatch"* — **they
  do not.** There is no `NAPAS_REAL_TIME_BATCH/970424/EBANK` DTL row, so `'E'` and `'EBANK'` both miss
  and both return `NO_CRITERIA`, exactly as legacy. Nothing about those rows changed.

  What canonicalisation actually does: strips bpchar padding (neutral — PostgreSQL `bpchar` comparison
  already ignores trailing blanks) and aligns this gate's spelling with `TransferNetwork.parse`. Its
  only live effect is **latent** — if an EBANK/SWIFT/VCB realtime DTL row is ever inserted we would
  dispatch where legacy fails closed. Whether those channels belong on the NAPAS realtime rail is an
  **open PO routing decision** (`batch-schedule-operating-hours-audit.md` §12b). Not an approved
  business-decision change; an inert divergence with an armed edge.

  Note that the analogous expansion on the **bulk** path is plain legacy parity, not divergence:
  `FILE_DEPOSIT_TRANSFER_BATCH1:301-381` itself reads 1-char counts and calls `operTimeChk` with
  hard-coded 5-char names, which is what `AntDivChannel.fromStorageCode` reproduces.
* **Null window bounds:** legacy's `BATCH_START_TM == null` test is a *row-absence* test, not a
  null-value test — both columns are `NOT NULL` in the DDL. `Optional.empty() → NO_CRITERIA` is
  equivalent, and `BatchScheduleNotNullContractTest` pins the precondition. **Not a divergence.**

### 4.6 `TB_FIRM_FILE_MST` / `_DATA` / `_RSLT` and `TB_FIRM_TRAN`

| Operation | Legacy | Refactor | Verdict |
|---|---|---|---|
| `TB_FIRM_FILE_MST` insert `STEP='0'/STAT='6'` | `C001` | `:404-422` | ✅ |
| `→ STEP='1'/STAT='6'` (01301 ok) | `U002` (`:386-404`) | `:210-217` | ✅ |
| `→ STEP='1'/STAT='3'` (01301 fail) | `U002` | `failFile:351-358` | ✅ |
| `→ STEP='2'` (00302 ok) | `U001` (`:243-248`) | `:303-305` | ✅ |
| `→ STEP='3'/STAT='0'` + trailer aggregates | `U003` (`:351-361`) | `:253-260` | ✅ |
| SFTP/API fail `→ STEP='0'/STAT='3'` | `U004` — **only** `PROC_STEP`+`PROC_STAT` | `failBoth` also sets `REG_RSLT_CD/MSG/PROC_DTTM` | ⚠️ L-4 |
| Execution-result lookup by `substring(...,3)` | `R006:54` | `findForExecutionResult:100-103` | ✅ |
| `TB_FIRM_FILE_DATA` per-line result | `U001` — `substring(FIRM_FILE_NM,3)` join | exact-name lookup | ❌ **H-1** |
| `TB_FIRM_FILE_RSLT` insert per D line | `C001` (`:295-320`) | `:206-232` | ✅ |
| `TB_FIRM_TRAN` `202`/`203`/`204` inserts at `'6'` | `C001` | all three reproduced | ✅ |
| `203` row `→'0'` even when the original failed | `U003` (`:270-287`) | `:528-541` | ✅ |
| Original `→'0'`/`'3'` + pay-target, one tx | `beginTransaction` (`:298-333`) | `DispatchResultTxService` `REQUIRES_NEW` | ✅ |
| Original `U004` nulls `BAL_AMT`/`SIGN`/`ANT_DIV_CD` | no `coalesce`, values unbound | refactor **preserves** them | ⚠️ L-2 (improvement) |
| Original targeted by `org_proc_dt` from the response | `:301,319` | local `row.getTranDt()` | ⚠️ L-8 (improvement) |

---

## 5. Integration Parity

```
Legacy:   WECMS_BATCH (JEX)  →  ConnectionUtil.connectUrlByPost  →  VAN gateway  →  Bank
Refactor: wecms-api (Spring) →  RestClientEfsApiClient           →  VAN gateway  →  Bank
```

| Aspect | Legacy | Refactor | ✅ |
|---|---|---|---|
| Envelope fields | `ID,RQ_DTIME,TNO,EV,VV,EM=AES,VM=HmacSHA256` | identical | ✅ |
| Envelope `ID` | `TB_FIRM_CONF.VAN_ORG_CD`, every leg | same (D-2) | ✅ |
| `TNO` = the allocated `TRAN_SEQ` (never a UUID) | 202=`dep_seq`, 204=`ccy_exc_seq`, 203=`tran_seq`, 301/302=`seq` | all four match | ✅ |
| `EV` = `Base64(AES-256-CBC(RQ_DTIME(14) ‖ json))`, zero IV | `SecurityUtil.EncryptAesBase64` | `EfsCryptoService.encryptEv` | ✅ |
| `VV` = `Base64(HMAC-SHA256(json))` — JSON only, no prefix | `getHmacSha256(json,…)` | `buildVv` | ✅ |
| 202 body: 20 fields, exact order | `param_202:528-565` | `Efs202Request:41-62` (order-identical — load-bearing, `VV` signs the serialized JSON) | ✅ |
| FX block always present, `""` on the VND path | `:242-254` init + `:542-552` | `nullToEmpty`, `:157-164` | ✅ |
| 204 body: 8 fields | `param_204:331-339` | `Efs204Request:294-300` | ✅ |
| 203 body: 5 fields | `param:154-159` | `Efs203Request:156-157` | ✅ |
| 301/302 body: 6 fields incl. `data_dcm_amt` | `:1188-1202` / `:142-156` | `Efs301Request`/`Efs302Request` | ✅ |
| 00301 key source = `TB_VAN_API_INFO.API_KEY` | `TB_VAN_API_INFO_R001` (`:1172-1186`) | `VanApiKeyService` | ✅ |
| `RS_MSG` URL-decoded | `URLDecoder.decode(RM,"UTF-8")` | handled in the client layer | ✅ |
| Response `VV` verified — 202/204 | yes (`:598`, `:369`) | yes | ✅ |
| Response `VV` verified — 203 | **no** (`:240` decrypts unverified) | **yes** | ⚠️ L-5 (improvement) |
| Inbound `VV` verified — 01301/01302 | yes (`API1:102`, `API2:109`) | yes | ✅ |
| Request rate toward the VAN | paced per bank | unpaced on the event-driven legs | ⚠️ **M-6** |
| Callback response `RC` | see the table in **M-2** | 3 branches differ | ⚠️ **M-2** |
| Byte-level `EV`/`VV` vs a captured sample | — | **not verified (B5, external)** | ⏳ |

---

## 6. Transaction Parity

| Unit | Legacy | Refactor | Verdict |
|---|---|---|---|
| Realtime per-target | **no transaction** — each IDO auto-commits; `U004` and `U001` are separate commits | one `REQUIRES_NEW` per target — firm-tran + pay-target commit together | ✅ stronger, same end state |
| Realtime batch abort on MAC failure | `JexBIZException` escapes the loop → **whole run aborts**, row left `'9'` | VV-mismatch → pending `'6'`, next row unaffected | ⚠️ L-7 (improvement: legacy's `'9'` re-dispatch risked a double transfer) |
| Bulk per-file | `beginTransaction` around MST+DATA insert + file write; commit **before** SFTP; SFTP/API outside any tx | one `REQUIRES_NEW` per split file via `runInNewTransaction`; SFTP + EFS inside it | ✅ equivalent isolation; earlier files can never be rolled back |
| Bulk validation / cutoff / claim | separate auto-commits | separate committed units | ✅ |
| 01301 callback | audit + MST + pay-file all auto-commit individually | one `@Transactional` | ✅ stronger |
| 00302 inline | `beginTransaction` **after** the POST (`BATCH2:236`) | inside the callback tx | ✅ |
| 01302 callback | audit committed **before** the parse tx; RSLT+DATA+MST+PAY in one tx | audit via `REQUIRES_NEW`; rest in the handler tx | ✅ exact |
| Result-check per row | `beginTransaction` around `U004`+`U001` | `DispatchResultTxService` `REQUIRES_NEW` | ✅ |
| Result-check 203 audit row | committed before the pair | committed before the pair | ✅ |
| Result-check rollback handling | `rollback(); endTransaction();` then **falls through to `commit()`** — legacy defect | proper rollback + propagate | ✅ improvement |
| Per-row failure isolation | `continue` | try/catch per row/target | ✅ |

---

## 7. Error Handling Parity

| Condition | Legacy | Refactor | ✅ |
|---|---|---|---|
| 202 no response | pay-target `'6'`, firm-tran stays `'6'` | both `'6'` (pending) | ✅ |
| 202 `G999` | same as no-response | same | ✅ |
| 202 other non-`0000` | both `'3'` + alert | both `'3'` + alert | ✅ |
| 202 response `VV` fail | throws → **run aborts**, row `'9'` | pending `'6'` | ⚠️ L-7 |
| 204 no response | 204 row stays `'6'`, skip 202 | same | ✅ |
| 204 non-`0000` | 204 row `'3'` + alert, skip 202 | same | ✅ |
| Gate A fail | pay-file `'3'` + `RS_MSG` + alert | same | ✅ |
| Cutoff / no-criteria | `'3'` + alert (distinct messages) | same, verbatim messages | ✅ |
| Before-window (`'N'`) | silent `continue`, no write | `SKIP`, no write, no alert | ✅ |
| SFTP upload fail | firm-file `STEP='0'/STAT='3'`, pay-file `'3'`, alert, next row | `failBoth` + alert + break | ✅ |
| 00301 fail | firm-file + pay-file `'3'`, alert | same | ✅ |
| 01301 bad MAC | `RC=G003` + alert | `G003` ack | ✅ |
| 01301 missing field | `G001`/`G002` + alert | `IllegalArgumentException` → handler | ✅ |
| 01301 file not found | `G004` + alert | **`0000`** | ⚠️ **M-2** |
| 00302 transient (`BT01`/`G999`/`C999`) | `continue`, no status write, **no alert** | audit RC, no status write, **no alert** | ✅ |
| 00302 permanent | file `'3'` + pay-file `'3'` + alert | **alert only** | ❌ **H-2** |
| 01302 bad MAC | `G003` | `G003` | ✅ |
| 01302 download/parse fail | throw → rollback, MST stays `STEP='2'` | same | ✅ |
| 203 no response / non-`0000`/`0001` | 203 row `'3'` + alert; original stays `'6'` | same | ✅ |
| 203 `ORG_STAT ≠ '1'` | skip + pace | skip + pace | ✅ |
| Blocked chars at dispatch | *(no such check)* | throws, uncaught → silent strand | ⚠️ **M-3** |

---

## 8. Retry Parity

| Legacy retry | Mechanism | Refactor | Assessment |
|---|---|---|---|
| Realtime `'9'` re-pick | next 60 s poll | after-commit hand-off; `StrandedRecoveryService` (disabled) | **Approved** (K9 / TO-BE) |
| Bulk `'9'` re-pick | next 60 s poll | request-time hand-off | **Approved** |
| 00302 re-issue after transient | BATCH2 re-poll of `STEP='1'/STAT='6'` | none — inline only | **Approved** (K9 Option A explicitly declined) |
| 203 re-inquiry of aged `'6'` | every-minute poll | `ResultCheckJob` (cron `-` by default) | ✅ retained |
| 203 row left at `'6'` after no response | never re-picked (`R001` filters `TRAN_TP='202'`) | same | ✅ bug-for-bug |
| Per-bank pacing between retries | `Thread.sleep` per row | `DispatchPacer` in the scan loop only | ⚠️ **M-6** for the event legs |

Residual recovery is `StuckPayFileMonitorService` (read-only detection + alert) plus a manual runbook —
the prescribed model. **H-2 degrades it**: a permanently-rejected file is classified
`BULK_EXECUTION_UNCONFIRMED` ("reconcile with the VAN Center first") when it should be a recorded
terminal failure.

---

## 9. Observable Output Parity

| Output | Verdict |
|---|---|
| Fixed-length file H/D/T byte layout | ✅ `FixedLengthCodec` verified against 6 UAT samples; **byte-vs-captured-gateway comparison still external (K3/B5)** |
| Trailer `requestCnt` / `requestAmt` from actual written rows | ✅ (`reqCnt`, summed `reqAmt`) |
| `FIRM_FILE_NM` = `S_{VAN}_{TYPE}_{round}_{date}` | ✅ format; ⚠️ **M-4** round wrap |
| Header `iterationInfo` 3-char zero-pad | ✅; ⚠️ **M-4** overflow past 999 |
| VAN request payloads / envelopes | ✅ (§5) |
| Callback response `RC`/`EV` | ⚠️ **M-2** (3 branches) |
| Ops alert headlines + field blocks | ✅ verbatim Korean strings on all 8 alert sites |
| Ops alerts on success | ✅ none, both sides |
| `TB_PAY_FILE_MST` final status (0/3/1) | ✅ |
| Per-line `PROC_RSLT_DIV` / `RS_CD` on the Result screen | ❌ **H-1** |
| Firm Banking History balance/network on a failed 202 | ⚠️ **M-5** |
| Payment status for a permanently-rejected bulk file | ❌ **H-2** ("Processing" instead of "Failed") |

---

## 10. Remaining Business Gaps

### Required before enabling the bulk rail

| # | Finding | Legacy | Spring | Sev | Type |
|---|---|---|---|---|---|
| **H-1** | Per-line result propagation lost (`R_` vs `S_` filename) | `IDO.TB_FIRM_FILE_DATA_U001.xml:34`, `IDO.TB_PAY_FILE_DATA_U001.xml:32` | `BulkExecutionResultService.java:197`, `TbFirmFileDataRepository.java:21` | **HIGH** | **Real parity defect** |
| **H-2** | Permanent 00302 failure not recorded as failure | `FILE_DEPOSIT_TRANSFER_BATCH2.java:283-304` | `BulkReceptionResultService.java:308-345` | **HIGH** | **Real parity defect** |

**H-1 evidence chain (all four links verified in code):**
1. Sent files are named `S_…` — `BulkReceptionService.java:401`, and `TB_FIRM_FILE_DATA.FIRM_FILE_NM`
   is set to that same value at `:440`.
2. Bank **result** files carry `R_…` — the real UAT samples used by `FixedLengthCodecTest.java:226-228`
   are `receive/R_GV00000001_WRB_001_20251223`, `R_GV00000006_SHB_003_20251021`,
   `R_GV00000007_VCB_008_20260602`.
3. Legacy therefore joins on `substring(FIRM_FILE_NM,3)` in **both** the MST lookup
   (`R006:54`) **and** the detail update (`TB_FIRM_FILE_DATA_U001:34`).
4. The refactor ports the substring rule to the MST lookup only
   (`TbFirmFileMstRepository.findForExecutionResult:100-103`, with an explicit comment about the
   differing prefix) but calls `indexFirmDataBySeq(tranDt, fileSeqNo, req.fileNm())` at `:197`, which
   resolves to the **exact-match** finder. With an `R_` callback the map is empty, every line takes the
   `fd == null` path at `:246-248`, and `TB_FIRM_FILE_DATA` / `TB_PAY_FILE_DATA` are never updated —
   while `TB_FIRM_FILE_RSLT` rows are written and the MST still advances to `PROC_STEP='3'/'0'`, so
   nothing looks wrong.
   *Test blind spot:* `BulkExecutionResultServiceTest2.java:76` sets the callback `file_nm` to the
   **`S_`** name, so the mismatch is never exercised.
   *Fix:* match `TB_FIRM_FILE_DATA` the way the MST is matched — `substring(FIRM_FILE_NM,3)` +
   `FILE_SEQ` (legacy's exact join keys) — and add a test whose callback `file_nm` uses the `R_` prefix.

### Requires a decision (not code-blocked)

| # | Finding | Sev | Owner |
|---|---|---|---|
| **M-1** | Operating-hours windows evaluated in ICT, legacy in the DB zone (Asia/Seoul) — 2 h shift; configurable | MED | PO / Ops |
| **M-2** | Three 01301 callback response codes + failure-branch `EV` differ from legacy | MED | VAN Center + PO |
| **M-3** | Dispatch-time blocked-char guard has no legacy counterpart and strands rows with no status/alert | MED | PO (gate) + dev (failure handling) |
| **M-4** | `FILE_SEQ` round wrap (99 Woori / 999 others) not implemented | MED | dev |
| **M-5** | `TB_FIRM_TRAN.BAL_AMT`/`BAL_AMT_SIGN`/`ANT_DIV_CD` null on a failed 202 | MED | dev |
| **M-6** | Event-driven 202/00301 legs unpaced; legacy rate-limited per bank | MED | VAN Center + dev |

### Low — deliberate improvements (no action needed, recorded for traceability)

| # | Item |
|---|---|
| L-2 | Result-check preserves `BAL_AMT`/`BAL_AMT_SIGN`/`ANT_DIV_CD`; legacy `U004` nulls them |
| L-3 | `TB_FIRM_BAT_API_TRAN.RS_CD` recorded on 00301-throw and empty-00302-body, where legacy left NULL |
| L-5 | 203 response `VV` verified (legacy decrypts unverified) |
| L-6 | Result-check accepts today+yesterday UTC, drops legacy's `< '235800'` blind spot, caps candidates per pass and logs the cap |
| L-7 | 202 `VV`-mismatch → pending `'6'` instead of aborting the run with the row at `'9'` (removes a double-transfer path) |
| L-8 | Result-check targets the original by the locally stored `TRAN_DT`, not the bank-echoed `org_tran_dt` |

### Low — minor divergences worth knowing

| # | Item |
|---|---|
| L-1 | `TB_PAY_TARGET.FINC_MDA_OWNER_NM` overwritten from the 202 success response; legacy writes it only from the 203 |
| L-4 | Extra columns on failure paths: `failBoth` sets `TB_FIRM_FILE_MST.REG_RSLT_CD/MSG/PROC_DTTM` (legacy `U004` sets only the two status columns); `TB_PAY_FILE_MST.RS_MSG` written on SFTP/EFS failure and overwritten in the `G998` branch, where legacy leaves it |
| L-9 | 00302 sibling gate tests `PROC_STEP='0'` only; legacy `R003` requires `PROC_STEP='0' AND PROC_STAT='6'`. Practically unreachable (the pay-file fail-stop trips first), but the predicate should match |

### L-10 — Documentation corrections required

| Document | Correction |
|---|---|
| `audits/final-four-to-one-batch-architecture-correction.md` §6 | Row *"Permanent ⇒ `PROC_STAT='3'` both tables + alert \| catch block + `failPayFile` ✅"* is **false** — `failPayFile` exists nowhere in the tree, and the status writes are absent (**H-2**) |
| `BulkReceptionService` class Javadoc, "Deferred" §, and `BulkTransferValidator:27-28` | Both say the operating-hours gate is "intentionally NOT ported" / "out of scope". It **is** ported and wired (`BulkReceptionService:258`) |
| `BulkReceptionService` §"Branch-split" | *"The whole `receive()` is one transaction"* contradicts §"Transaction model" in the same Javadoc and the code (per-file `REQUIRES_NEW`) |
| `ResultCheckService` class Javadoc | *"No DB status writes in this phase (D3)"* and *"`runOnce()` stays a no-op"* — it writes three tables and is fully implemented |
| `ResultCheckJob.run` Javadoc | *"Rows per pass: unbounded … no cap is introduced"* — a `batch-size` cap (default 100) exists |
| `BulkReceptionResultService:325-327` | Log text refers to "the bulk-execution retry sweep", removed 2026-07-29 |

### External / operational (unchanged, not parity issues)

1. Byte-level `EV`/`VV` + fixed-length file comparison against a captured gateway sample (B5 / K3).
2. VAN gateway source IPs for the callback allow-list.
3. `TB_BATCH_SCHEDULE_INFO`/`_DTL` seed on UAT + PROD (DEV/SIT done). Residual: no realtime DTL row for
   `SWIFT`/`EBANK`, so those targets fail `NO_CRITERIA` — open PO routing decision.
4. EFS `201` returning `RS_CD='1000'` on the dev node (17/17).
5. Confirm the refactor UAT DB is not the JEX UAT batch DB before enabling any scheduler.

---

## Final answers

1. **Does the Spring implementation produce the same business behaviour as legacy?**
   **Substantially yes** — every selection predicate, validation rule, decision branch, status
   transition, key allocation, request payload and alert reproduces legacy, with two exceptions
   (H-1, H-2) and six items needing a decision (M-1…M-6).

2. **Are the architectural changes reported as defects?** No. The four-to-one consolidation,
   event-driven triggers, inline `00302`, and direct VAN API calls are treated as approved throughout.

3. **What must be fixed before the bulk rail is enabled?** H-1 (per-line result propagation) and
   H-2 (permanent 00302 failure status). Both are localized; neither is architectural.

4. **What must be decided?** M-1 operating-hours zone; M-2 callback response contract with the VAN
   Center; M-3 whether the added blocked-char gate is intended, and how it must fail; M-4/M-5 dev
   fixes; M-6 whether the VAN imposes a rate limit.

5. **Is anything currently at risk in production?** No — every dispatch flag defaults OFF and
   `ResultCheckJob`'s cron defaults to `-`. Both HIGH findings are on the bulk rail, which has never
   run in the refactor (`TB_FIRM_FILE_*` empty on the refactor DB).
