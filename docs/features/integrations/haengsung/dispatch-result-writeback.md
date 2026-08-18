# HAENGSUNG Dispatch-Result Write-back — Backend Feature Spec (Follow-on / Phase 4)

**Slug:** `haengsung-dispatch-result-writeback` · **Domain:** Payment · **Status:** ✅ Implemented (Step 5, backend + unit tests; flag-gated OFF by default) · **Product type:** `H` only · **Last updated:** 2026-06-28

> **Implemented 2026-06-28.** Backend + unit tests landed (see [§11 Implementation](#11-implementation-2026-06-28)). Reuses the Step-1 Oracle write path and the existing dispatch/result lifecycle — no new dispatch flow, no duplicated dispatch logic. Legacy source of truth: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_004.java`.

## 1. Purpose

After the firm-banking dispatch engine actually executes the bank transfer, this **batch** writes the **final per-transaction result** back to the Haengsung Oracle ERP — success/fail per line and the closed batch status — so the ERP can reconcile what happened. Third of the three HAENGSUNG↔Oracle write-back surfaces.

Direction: **WeCMS → Oracle**. It reads WeCMS payment + dispatch-result tables and writes Oracle status; it imports nothing.

## 2. Trigger / schedule

A JEX batch (`ERP_0100_004`, `AbstractBatchTask`, id `ERP_0100_004`, `@JexDataInfo(type=BAT)`). No cron in source — scheduled via Jex Studio batch scheduler / `TB_BATCH_SCHEDULE_INFO`+`_DTL` ([refresh spec OQ-13](./payment-refresh.md#27-updated-open-items-appendix-b)). Iterates all `BUSI_INFM WHERE prdt_type='H' AND del_yn='N'`, three sections: Vendor/Expense, Payroll, Overseas; per-business + per-record transactions.

## 3. Relationship with the firm-banking dispatch engine

This flow is **downstream of dispatch** and cannot run meaningfully until the dispatch engine is live. It selects only batches whose dispatch has produced a result — reading dispatch tables `TB_FIRM_TRAN` / `TB_PAY_FILE_MST` / `TB_PAY_TARGET` (`PROC_STAT`, `RS_CD`, `RS_MSG`) joined to the WeCMS payment summaries, and it only finalizes a batch when **all** its details have a `PROC_STAT` + `RS_CD` (`isAllDetailsComplete`). It also guards the Oracle master update with `WHERE last_status='51'` (i.e. only batches the approval flow already moved to "processing"). That makes it dependent on **both** Phase 3 (approval set `51`) and a working dispatch path.

## 4. WeCMS tables read

- `SCDUL_PYMN_SUMR` (legacy IDO `SCDUL_PYMN_SUMR_R015` payment / `R016` payroll) + `SCDUL_PYMN_SUMR_DETL` (`R003` / `R004`).
- `OVRS_PYMN_SUMR` (`OVRS_PYMN_SUMR_R008`) + `OVRS_PYMN_SUMR_DETL` (`R004`).
- Dispatch/result join sources: `TB_PAY_FILE_MST`, `TB_PAY_FILE_DATA`, `TB_PAY_TARGET`, `TB_FIRM_TRAN` (for `PROC_STAT` / `RS_CD`).
- WeCMS write: marks the summary `ERP_PROC_CMPL_YN='Y'` (patch) once written back — so the batch doesn't re-process.

## 5. Oracle tables written

- `TB_TRNS_MAST` (`TB_TRNS_MAST_U000`) — keyed by `ERP_FILE_DATE`/`ERP_FILE_NUM`, guarded `WHERE last_status='51'`.
- `TB_TRNS_DETAIL` (`TB_TRNS_DETAIL_U000`) — keyed by `ERP_FILE_DATE`/`ERP_FILE_NUM`/`ERP_FILE_SEQ`.

## 6. Status mapping (dispatch result → ERP)

Per detail line, from WeCMS `PROC_STAT` + `RS_CD`:

| WeCMS dispatch result | ERP DETAIL `END_GB` | ERP DETAIL `PROC_STS` |
|---|---|---|
| `PROC_STAT='0'` AND `RS_CD='0000'` → **success / complete** | `8` (송금완료) | `RS_CD` (`0000`) |
| `PROC_STAT='3'` → **failed (rejected by bank)** | `5` (송금의뢰오류) | `0999` (override) |
| `PROC_STAT='0'` AND `RS_CD≠'0000'` → **failed** | `5` | `RS_CD` |

Master close (when all details complete): `LAST_STATUS='52'` (saf 처리완료 / executed), `END_GB='1'`.

## 7. Columns updated

**`TB_TRNS_MAST`**: `LAST_STATUS='52'`, `IHB_UDT_USR='WEBCASH'`, `IHB_UDT_DT=now(VN)`, `END_GB='1'`, `END_DATETIME=PROC_DTTM`, `NOR_CNT`, `NOR_AMT`, `ERR_CNT`, `ERR_AMT` (from the WeCMS summary's success/error totals).

**`TB_TRNS_DETAIL`**: `END_GB` = `8`/`5` (per §6), `PROC_STS` = `RS_CD` or `0999`, `PROC_STS_MSG` = `RS_MSG`, `IHB_UDT_DT=now(VN)`, `TR_DATE = TRAN_DT`.

## 8. Why this is NOT Phase 1 Refresh import

- It runs **after dispatch**, which Phase 1 does not include at all.
- It writes **result** columns (`LAST_STATUS=52`, `END_GB=8/5`, `PROC_STS`/`PROC_STS_MSG`/`TR_DATE`) — none of which the import writes.
- It depends on Phase 3 (`LAST_STATUS='51'` guard) and on dispatch-result data existing.

**What breaks if omitted (Phase 1):** Oracle never receives the final result — `LAST_STATUS` doesn't reach `52`, detail `END_GB`/`PROC_STS` stay at the import value `2`, and WeCMS `ERP_PROC_CMPL_YN` stays `'N'`. The ERP can't reconcile success/fail. Acceptable for Phase 1 because dispatch is out of Phase 1 scope.

## 9. When to implement

**Phase 4 — only after PO approval and once the firm-banking dispatch engine is active** in the refactor (it already exists, flag-gated inert — see workspace memory `project_batch_refactor_real_impl`). Reuse the Phase 1 Oracle datasource + write path; do not copy the legacy inline IDO logic.

## 11. Implementation (2026-06-28)

Backend implemented as an **event-driven, idempotent write-back** rather than a scan batch — but the data path, status mapping, and Oracle writes are faithful to legacy `ERP_0100_004`.

**Single trigger.** `BulkExecutionResultService.finalizePayFileStatus` (the one point where a firm-banking pay-file's dispatch result is finalized — all siblings of a `FILE_SEQ_NO` reported) publishes one `PaymentDispatchResultFinalizedEvent(bizKey, tranDt, fileSeqNo)`. `HaengsungDispatchResultWriteBackService.onDispatchResultFinalized` subscribes `@TransactionalEventListener(AFTER_COMMIT)` — the exact pattern Step 4 uses for `PaymentApprovalStatusChangedEvent`. There is exactly one trigger; no second dispatch path was added and no dispatch logic duplicated.

**Reused dispatch/result services.** Per-line dispatch result (`PROC_STAT` / `RS_CD` / `RS_MSG` / `PROC_DTTM`) is read through the existing `DispatchResultResolver` (`resolve` for SCDUL, `resolveOverseas` for OVRS) — the refactor's equivalent of the legacy `SCDUL_PYMN_SUMR_DETL_R003` / `OVRS_PYMN_SUMR_DETL_R004` join queries. The Step-1 `HaengsungErpOracleRepository` (secondary Oracle datasource) is reused for the writes.

**Files changed.**
- `domain/payment/dispatch/callback/event/PaymentDispatchResultFinalizedEvent.java` *(new)* — the single trigger signal.
- `domain/payment/dispatch/callback/service/BulkExecutionResultService.java` — inject `ApplicationEventPublisher`; publish the event on actual finalization (inside the pay-file-MST close-out).
- `domain/payment/haengsung/dispatch/HaengsungDispatchResultWriteBackService.java` *(new)* — listener + per-batch write-back for all four types.
- `domain/payment/haengsung/oracle/HaengsungErpOracleRepository.java` — add `updateMasterDispatchResult(...)` (guarded `WHERE LAST_STATUS='51'`) + `updateDetailDispatchResult(...)`.
- `domain/payment/entity/OvrsPymnSumr.java` — map the existing legacy `ERP_PROC_CMPL_YN` CHAR(1) column (no DDL; column verified present, nullable, default `'N'`); `ErpPaymentRefreshMasterImporter` now stamps it `'N'` on OVRS import (parity with the SCDUL leg).
- `…internaltransfer/repository/ScdulPymnSumrRepository.java` + `…overseas/repository/OvrsPymnSumrRepository.java` — add `findByBizKeyAndPayTranDtAndPayFileSeqNo(...)` + `markErpProcComplete(...)`.

**Oracle writes (faithful to legacy).**
- MAST (`updateMasterDispatchResult`): `LAST_STATUS='52'`, `END_GB='1'`, `END_DATETIME` = dispatch `PROC_DTTM`, `NOR_CNT`/`NOR_AMT` (success), `ERR_CNT`/`ERR_AMT` (fail), `IHB_UDT_USR='WEBCASH'`/`IHB_UDT_DT`=now(VN). Guarded `WHERE FILE_DATE/FILE_NUM AND LAST_STATUS='51'` (legacy `and last_status='51'`); 0 rows is a benign no-op (legacy ignores the count).
- DETAIL (`updateDetailDispatchResult`), per line: success (`PROC_STAT='0'` & `RS_CD='0000'`) → `END_GB='8'`, `PROC_STS=RS_CD`; rejected (`PROC_STAT='3'`) → `END_GB='5'`, `PROC_STS='0999'`; other fail → `END_GB='5'`, `PROC_STS=RS_CD`. Always `PROC_STS_MSG=RS_MSG` (null on file path), `TR_DATE`=batch `PAY_TRAN_DT`, `IHB_UDT_DT`=now(VN). Keyed by the imported `ERP_FILE_DATE`/`ERP_FILE_NUM`/`ERP_FILE_SEQ` only. Receive-flag / approval-only columns are never touched.
- Counts/amounts computed in Java from the per-line resolved result + the detail amount (`TRAN_AMT` SCDUL / `TRNF_AMT` OVRS) — matches `SCDUL_PYMN_SUMR_R015` per-detail classification exactly; for OVRS it equals `R008` in the 1:1 dispatch-line case (documented simplification; consistent with the per-line `R004` classification used for the detail writes).

**WeCMS flag + transaction model (no XA).** After the Oracle writes for a batch succeed, the WeCMS summary is flipped `ERP_PROC_CMPL_YN='Y'` (`markErpProcComplete`, own tx). Order: WeCMS dispatch/result commit (already done — AFTER_COMMIT) → Oracle write-back → `ERP_PROC_CMPL_YN='Y'`. If Oracle fails: caught per batch + logged, the committed dispatch result is **not** rolled back, the flag stays `'N'`, and the batch is retried on a later run. Approval status is never changed.

**Retry safety.** `writeBackForFile(bizKey, tranDt, fileSeqNo)` is idempotent and safe to re-invoke: already-pushed batches (`ERP_PROC_CMPL_YN='Y'`) and undispatched / non-ERP batches are skipped; the `LAST_STATUS='51'` guard makes a re-run a no-op on already-closed masters; a batch with any not-yet-complete line (`isAllDetailsComplete` fails) is skipped whole and retried. No retry **scheduler** is wired (out of Step-5 scope).

**Tests.** `HaengsungDispatchResultWriteBackServiceTest` — success END_GB='8'; failed END_GB='5'; `'0999'` fallback; mixed counts/amounts; LAST_STATUS='52'/END_GB='1' (repo SQL); ERP-key-only lookup; `ERP_PROC_CMPL_YN` flipped only after Oracle success; Oracle failure does not propagate from the listener and does not flip the flag; non-HAENGSUNG / non-ERP / not-dispatched / already-pushed / incomplete skipped; all four types (SCDUL Vendor/Expense/Payroll + OVRS). `./gradlew compileJava` + the haengsung/dispatch-callback suites pass.

**Remaining (Step 6 — NOT in scope here):** exchange-rate sync (`ERP_0100_001`). Also still deferred outside Step 5: a retry **scheduler**, and the realtime/API-path (`TB_PAY_TARGET`, no `TB_PAY_FILE_MST`) trigger — the write-back service already handles that data path via `DispatchResultResolver`, so a future scheduled retry can call the same idempotent method without new logic.

## 10. Cross-references
- Parent / Phase 1: [haengsung-payment-refresh.md](./payment-refresh.md) (§9A, §23 field-change matrix, §24 lifecycle).
- Sibling: [haengsung-approval-status-writeback.md](./approval-status-writeback.md) (sets `LAST_STATUS='51'` this batch requires).
- Legacy source: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_004.java`.
