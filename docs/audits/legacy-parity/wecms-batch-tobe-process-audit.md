# WeCMS Batch — TO-BE Process Audit (Spring Refactor vs Legacy JEX Batch)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

| Field | Value |
|---|---|
| Audit slug | `wecms-batch-tobe-process` |
| Module | `payment` (firm-banking dispatch) |
| Scope | WeCMS Spring/API payment-data creation → hand-off to WeCMS Batch processing (real-time + bulk/file dispatch, EFS legs, callbacks, SFTP, result-check scheduler) |
| Method | Read-only source inspection of `wecms-api` (`domain/payment/dispatch/**` + per-module executors) and the dispatch config in `application*.yml`. Legacy AS-IS reconstructed from PO/Mr. Do screenshots + surviving JEX artifacts. |
| Date | 2026-06-13 |
| Author | Claude Code (AI), for review by Mr. Jung (PO) / Mr. Kang (TO-BE architecture) |
| Read-only | Yes — no code/config/test/SQL was modified. Only this report was created. |
| Related | Memory `project_batch_refactor_real_impl`; [firm-banking-dispatch-schema.md](../../references/database/payment/firm-banking-dispatch-schema.md); root [CLAUDE.md](../../../CLAUDE.md) §"Refactor DB & Legacy-Compatibility Rules" |

> **Evidence convention.** Every claim below cites `file:line` in `wecms-api/src/main/java/com/kosign/wecms/domain/payment/dispatch/` (abbreviated `dispatch/…` from here on) or the config YAML. Flag-gated code is reported as **"implemented but disabled by default"**, never "not implemented", per the audit rules.

---

## 1. Executive Summary

**Is the TO-BE batch architecture implemented?** **Yes — implemented in code, disabled by default.** The full firm-banking dispatch pipeline (real-time transfer, bulk/file transfer, EFS legs 201–302, both EFS callbacks, fixed-length codec, crypto, SFTP, and the result-check scheduler) exists as real production-grade Spring code under `dispatch/**`. It is inert in every profile because six feature flags default OFF and the mock EFS/SFTP clients **throw** rather than fake success.

**What is the current main process?** The TO-BE design (Mr. Kang) replaces the legacy multi-program batch farm with **Spring-service-driven dispatch**:

- **Real-time transfers** are triggered by a Spring `afterCommit` transaction synchronization the moment the payment row commits — there is no polling batch.
- **Bulk/file transfers** are likewise triggered by an `afterCommit` hook that hands off to `BulkReceptionService`, then driven forward by **inbound EFS callbacks** (01301/01302) — event-driven, not batch-scanned.
- The **only surviving scheduled job** is the result-check sweep.

**Is `PROCESSING_RESULT_CHECK` the only remaining scheduler/batch?** **Yes.** The only `@Scheduled` job in the dispatch tree is `dispatch/scheduler/ResultCheckJob.java`, the TO-BE port of legacy `PROCESSING_RESULT_CHECK`. It is gated by a cron property that defaults to `"-"` (never fires). No other `@Scheduled` / `@Component`-batch runner exists in the dispatch package.

**What old JEX batch processes were removed/replaced?**

| Legacy batch | TO-BE disposition |
|---|---|
| `NAPAS_REAL_TIME_BATCH` | **Replaced** — folded into `afterCommit` real-time dispatch (no scanning loop). |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | **Replaced** — folded into `afterCommit` → `BulkReceptionService` (00301). |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | **Replaced / absorbed** — the 00302 execution request now runs **inline inside the 01301 reception callback** (`issueExecutionInline`), so there is no second standalone batch. |
| `PROCESSING_RESULT_CHECK` | **Kept** — ported as `ResultCheckJob` + `ResultCheckService` (the only scheduler). |
| `WB_00000100` (balance inquiry) | **Replaced** — balance now returns inside the EFS 202 response (`BAL_AMT`); recipient/depositor inquiry is the standalone EFS 201 leg. No standalone balance-inquiry batch. |

**Bottom line:** From a code perspective the TO-BE batch process is **built and structurally complete**. It is intentionally dormant pending infrastructure provisioning (EFS endpoint, VAN API key, SFTP host/keys, callback firewall) and SIT round-trip verification. It is **not** production/SIT-active today.

---

## 2. Legacy AS-IS Process Summary

> The legacy batch programs lived in a **separate JEX batch deployment** (designed by Mr. Do) and are **not present in this workspace** — `WECMS DEV/WECMS/src/com/firm/FIRM_PROC.java` is a thin 3.4 KB helper (`inquiryAccount` only), and `WECMS DEV/WECMS_ETC/xml/service/IMO/IMO.WB_00000100.xml` is the surviving balance-inquiry service descriptor. The table below reconstructs the AS-IS shape from the PO/Mr. Do screenshots and the legacy entity set referenced in the dispatch schema doc.

| Legacy process | Purpose | Input table/file | Output table/file | EFS API | Status in TO-BE |
|---|---|---|---|---|---|
| **NAPAS_REAL_TIME_BATCH** | Poll waiting real-time (VND/API-path) targets and push each to the gateway | `TB_PAY_TARGET` (PROC_STAT=9) | `TB_FIRM_TRAN`, `TB_PAY_TARGET` (PROC_STAT) | 202 (+204 FX) | **Replaced** by `afterCommit` real-time dispatch |
| **FILE_DEPOSIT_TRANSFER_BATCH1** | Build fixed-length deposit file from waiting file targets, SFTP-upload, request reception | `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA` (PROC_STAT=9) | fixed-length file + `TB_FIRM_FILE_MST` / `TB_FIRM_FILE_DATA`, `TB_FIRM_BAT_API_TRAN` | 301 | **Replaced** by `afterCommit` → `BulkReceptionService` |
| **FILE_DEPOSIT_TRANSFER_BATCH2** | After reception accepted, request file execution | `TB_FIRM_FILE_MST` (PROC_STEP=1) | `TB_FIRM_FILE_MST` (PROC_STEP), `TB_FIRM_BAT_API_TRAN` | 302 | **Replaced / absorbed** into the 01301 callback (`issueExecutionInline`) |
| **PROCESSING_RESULT_CHECK** | Re-poll no-response / in-flight transfers and reconcile final status | `TB_FIRM_TRAN` (PROC_STAT=6) | `TB_FIRM_TRAN`, `TB_PAY_TARGET` (final PROC_STAT) | 203 | **Kept** as `ResultCheckJob` (only scheduler) |
| **WB_00000100 balance inquiry** | Stand-alone account balance inquiry | account master | inquiry response | 201-class | **Replaced** — balance rides in the 202 response; 201 is recipient/depositor inquiry |
| **(implicit) result-file ingest** | Download/parse the execution result file | result file (SFTP) | `TB_FIRM_FILE_RSLT` + reflect to `TB_FIRM_FILE_DATA`/`TB_PAY_FILE_DATA` | — (01302 callback drives it) | **Replaced** by the 01302 execution-result callback |

---

## 3. TO-BE Spring Process Summary

### A. Real-time transfer

- **Trigger point** — `TransferDispatchExecutor.writeApiPath():229` calls `RealtimeDispatchRegistrar.registerAfterCommit(...)`; the registrar fires `RealtimeDispatchService.dispatch(...)` in an `afterCommit` synchronization (`dispatch/service/RealtimeDispatchRegistrar.java:48-75`).
- **Service/class** — `RealtimeDispatchService` (orchestration + status writes) → `RealtimeTransferService` (EFS 204/202/203 calls) → `RestClientEfsApiClient`.
- **Main DB tables** — `TB_PAY_TARGET` (state), `TB_FIRM_TRAN` (per-transfer gateway log).
- **EFS API** — optional **204** (Woori FX, bank `970457`, non-VND) then **202** (transfer); **203** only as the G999 no-definitive-response recovery (`RealtimeTransferService.java:113,141,158`).
- **State update** — insert `TB_FIRM_TRAN` at `PROC_STAT='6'` (REQ) before the call (`RealtimeDispatchService.java:154`); on outcome `writeStatus(...)` sets both `TB_FIRM_TRAN` and `TB_PAY_TARGET` to `'0'` (SUCC) or `'3'` (FAIL) (`RealtimeDispatchService.java:208,233`).
- **Error handling** — per-target isolation; a failed transfer is committed as FAIL and never rethrown to undo the payment (`RealtimeDispatchRegistrar.java:64-71`). Gateway business error → `BusinessException("WBE_EFS_GATEWAY_ERROR")` → FAIL.
- **Gate** — `wecms.dispatch.realtime-dispatch` (default `false`).

### B. Bulk/file transfer

- **Trigger point** — File-path executors call `DispatchAutoReceiveRegistrar.registerAfterCommit(...)`, which runs `BulkReceptionService.receive(tranDt, bizKey, fileSeqNo)` in an `afterCommit` synchronization (`dispatch/service/DispatchAutoReceiveRegistrar.java:52-73`). *(Confirmed: the after-commit hook IS wired; no inline network I/O inside the payment transaction.)*
- **Service/class** — `BulkReceptionService` (00301 + file build) → `BulkReceptionResultService` (01301 callback + inline 00302) → `BulkExecutionResultService` (01302 callback + result ingest). File codec: `dispatch/file/FixedLengthCodec`. Transport: `dispatch/sftp/JschSftpClient`.
- **Main DB tables** — `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA` (source + final status), `TB_FIRM_FILE_MST` / `TB_FIRM_FILE_DATA` / `TB_FIRM_FILE_RSLT` (dispatch + results), `TB_FIRM_BAT_API_TRAN` (per-leg API audit).
- **EFS API** — **301** (reception request), **302** (execution request, inline from the 01301 callback).
- **State update** — `TB_FIRM_FILE_MST.PROC_STEP` `0→1→2→3` (see §8); `TB_PAY_FILE_MST.PROC_STAT` finalized to `'0'`/`'1'` (partial)/`'3'` by the sibling-aware `finalizePayFileStatus` (`BulkExecutionResultService.java:248-280`).
- **Error handling** — SFTP/EFS failures mark FAIL and break the per-file loop (legacy parity), never rethrow into the payment tx (`BulkReceptionService.java:339-346`); result-file parse failures **throw** so the tx rolls back and the file is replayable at PROC_STEP=2 (`BulkExecutionResultService.java:301-314`).
- **Gates** — `wecms.dispatch.auto-receive` (default `false`) for the trigger; `wecms.dispatch.efs.enabled` + `wecms.dispatch.sftp.enabled` for the I/O.

### C. Result check

- **Trigger point** — `@Scheduled(cron = "${wecms.dispatch.result-check.cron:-}", zone = "Asia/Ho_Chi_Minh")` + `@SchedulerLock` on `ResultCheckJob.run()` (`dispatch/scheduler/ResultCheckJob.java:39-43`). Default cron `"-"` = never fires.
- **Service/class** — `ResultCheckService.runOnce()` → selects no-response 202 rows (`TB_FIRM_TRAN` TRAN_TP=202 / PROC_STAT=6, today, older than ~2 min) and re-inquires via EFS 203 (`ResultCheckService.java:130-218`).
- **Main DB tables** — `TB_FIRM_TRAN`, `TB_PAY_TARGET`.
- **EFS API** — **203** result inquiry (`ResultCheckService.java:112`).
- **State update** — `reflectOutcome(...)` sets `TB_FIRM_TRAN.PROC_STAT` 6→0/3 and propagates to the originating `TB_PAY_TARGET` (`ResultCheckService.java:191-218`).
- **Error handling** — per-row failures logged; row left at PROC_STAT=6 for the next pass (legacy parity).

### D. Callback processing

- **01301 reception result** — `POST /api/v1/payment/dispatch/callbacks/bulk-reception-result` → `BulkReceptionResultController` → `BulkReceptionResultService.handle(...)`. Decrypts EV, verifies VV (`EfsCryptoService.verifyVv`), validates count/amount, advances PROC_STEP 0→1 on `result_cd = "BT00"`, then **issues 00302 inline** per sibling (`BulkReceptionResultService.java:121-265`).
- **01302 execution result** — `POST /api/v1/payment/dispatch/callbacks/bulk-execution-result` → `BulkExecutionResultController` → `BulkExecutionResultService.handle(...)`. Decrypts/verifies, SFTP-downloads the result file, `FixedLengthCodec.decode`, writes `TB_FIRM_FILE_RSLT` per line, reflects onto `TB_FIRM_FILE_DATA` + `TB_PAY_FILE_DATA`, advances PROC_STEP 2→3, then `finalizePayFileStatus` (`BulkExecutionResultService.java:111-241`).
- **Main DB tables** — `TB_FIRM_FILE_MST/DATA/RSLT`, `TB_PAY_FILE_MST/DATA`, `TB_FIRM_BAT_API_TRAN`.
- **Security** — `dispatch/callback/CallbackIpWhitelistFilter` (IP allow-list) + per-VAN API-key HMAC verification.
- **Error handling** — unknown/duplicate file → safe no-op (idempotent); verify failure → `WBE_EFS_VERIFY_FAILED`; malformed result file → throw + rollback (replayable).

### E. SFTP processing

- **Trigger point** — invoked inside `BulkReceptionService` (upload) and `BulkExecutionResultService` (download + move).
- **Service/class** — interface `dispatch/sftp/SftpClient`; real impl `JschSftpClient` (`upload`/`download`/`move` via JSch, `JschSftpClient.java:47-109`); `MockSftpClient` **throws** `UnsupportedOperationException` on all three (`MockSftpClient.java:24-39`).
- **Config** — `dispatch/sftp/SftpProperties` (`wecms.dispatch.sftp.*`): host, port, username, private-key-path/password, local/remote upload+download paths, remote-move path.
- **Gate** — `wecms.dispatch.sftp.enabled` (default `false`). Real client only registered when `=true`; mock active otherwise (`JschSftpClient.java:37`, `MockSftpClient.java:20`).
- **Error handling** — upload/download failure → `SftpTransferException`; `move` is best-effort (housekeeping, swallowed) per legacy parity.

### F. Slack / error handling

- **Structured error codes** — `dispatch/constant/FirmBankingResultCd` (`0000` SUCC, `0001` NO_FOUND, `9999` AMT_ERR, `G999` ETC_ERR, `G003` ENC_VRFY_ERR, `G004` DB_ERR).
- **Trace/logging** — all paths log at WARN/ERROR with the request-scoped trace id; gateway errors funnel through `BusinessException` + the standard `{status,…,trace_id}` envelope.
- **Slack/`MsgSendUtil` alerting** — **NOT ported** (deferred). The legacy Slack alerting leg is documented as out-of-phase in `BulkReceptionService` and has no Spring consumer. Operational alerting is logging-only today.

---

## 4. Process Mapping: Legacy → Spring

| Legacy JEX component | Spring refactor component | Same responsibility? | Notes / difference |
|---|---|---|---|
| NAPAS_REAL_TIME_BATCH (scan PROC_STAT=9) | `RealtimeDispatchRegistrar` + `RealtimeDispatchService` + `RealtimeTransferService` | Yes (functionally) | **Polling loop removed** — replaced by `afterCommit` event hook; one target dispatched per commit, isolated. |
| FILE_DEPOSIT_TRANSFER_BATCH1 (build+upload+301) | `DispatchAutoReceiveRegistrar` + `BulkReceptionService` | Yes | `afterCommit` trigger; same fixed-length file + SFTP + 301. Branch-split present (C5 edge deferred — §10). |
| FILE_DEPOSIT_TRANSFER_BATCH2 (302) | `BulkReceptionResultService.issueExecutionInline()` | Yes (relocated) | **No standalone second batch** — 00302 runs inline inside the 01301 callback, gated by the split-sibling check. |
| PROCESSING_RESULT_CHECK (203 reconcile) | `ResultCheckJob` + `ResultCheckService` | Yes | The only retained scheduler; cron default `"-"` (off). |
| WB_00000100 balance inquiry | EFS 202 response `BAL_AMT` + EFS 201 recipient inquiry (`RecipientLookupService`/`DepositorInquiryService`) | Partially | Balance is no longer a separate batch — returned within 202; 201 is the recipient/depositor lookup leg. |
| Result-file ingest (BCS) | `BulkExecutionResultService.handle()` (01302) | Yes | Driven by inbound callback, not a scan; writes `TB_FIRM_FILE_RSLT` + reflects to pay-file data. |
| EFS request encrypt/sign | `EfsCryptoService` (AES-256/CBC/PKCS5 + HMAC-SHA256) | Yes | Constant-time VV verify (`MessageDigest.isEqual`). |
| Fixed-length file H/D/T | `FixedLengthCodec` | Yes | 1000-byte records, byte-offset slicing; encode/decode symmetric. |
| SFTP transport | `JschSftpClient` | Yes | JSch; `StrictHostKeyChecking=no` (legacy parity). |
| Batch schedule windows (TB_BATCH_SCHEDULE_*) | `TbBatchScheduleInfo`/`TbBatchScheduleDtl` entities present | Partially | Entities + repos mapped; **operating-hours gate not yet enforced** (deferred). |
| Slack alerting (`MsgSendUtil`) | — | No | Not ported (deferred). |

---

## 5. Current Active Batch/Scheduler List

There is exactly **one** scheduled job in the dispatch tree.

| Class/file | Schedule / config flag | Purpose | Enabled by default? | Required DB/config dependency |
|---|---|---|---|---|
| `dispatch/scheduler/ResultCheckJob.java:39` | `@Scheduled(cron = "${wecms.dispatch.result-check.cron:-}", zone="Asia/Ho_Chi_Minh")` + `@SchedulerLock` (ShedLock) | Re-inquire no-response 202 transfers via EFS 203 and reconcile final status (PROCESSING_RESULT_CHECK port) | **No** — cron `"-"` never fires | `wecms.dispatch.efs.enabled=true` + `TB_VAN_API_INFO` seeded (B2); reads/writes `TB_FIRM_TRAN`, `TB_PAY_TARGET`; ShedLock table |

> No other `@Scheduled` method, `@EnableScheduling`-driven runner, or polling loop exists under `dispatch/**`. The real-time and bulk flows are **event-driven** (afterCommit + inbound callbacks), not scheduled.

---

## 6. Removed Legacy Batch Processes

| Removed/replaced legacy process | Why no longer needed in TO-BE |
|---|---|
| **FILE_DEPOSIT_TRANSFER_BATCH1** | The build-file → SFTP-upload → EFS 301 sequence now runs synchronously off the payment commit (`DispatchAutoReceiveRegistrar` → `BulkReceptionService`). There is nothing left for a periodic scanner to pick up, so the scan batch is eliminated. |
| **FILE_DEPOSIT_TRANSFER_BATCH2** | The EFS 302 execution request is issued **inline** the moment the 01301 reception callback confirms acceptance (`BulkReceptionResultService.issueExecutionInline`). Splitting it into a second timed batch would only add latency and a second scan; TO-BE collapses it into the callback. |
| **NAPAS_REAL_TIME_BATCH** | Real-time transfers fire per-target on `afterCommit` (`RealtimeDispatchRegistrar`). No queue of PROC_STAT=9 rows accumulates, so no polling batch is required. |
| **WB_00000100 (balance inquiry)** | Account balance is returned inside the EFS 202 transfer response (`TB_FIRM_TRAN.BAL_AMT`/`BAL_AMT_SIGN`), and recipient lookup is the dedicated EFS 201 leg. A standalone balance-inquiry batch is redundant. |
| **(implicit) result-poll / file-ingest batch** | Result ingestion is callback-driven (01302). The only residual *polling* is the narrow `PROCESSING_RESULT_CHECK` safety net for 202 no-responses, which is retained. |

---

## 7. Detailed TO-BE Flow

### A. Real-time transfer

1. WeCMS Spring writes `TB_PAY_TARGET` at `PROC_STAT='9'` (WAIT) on the API/VND path (`TransferDispatchExecutor.writeApiPath`).
2. On the same path, `RealtimeDispatchRegistrar.registerAfterCommit(bizKey, tranDt, tranSeq)` registers an `afterCommit` callback (gated `realtime-dispatch`).
3. After the payment commits, `RealtimeDispatchService.dispatch(...)` re-reads the target — proceeds only if still `PROC_STAT='9'` and no `TB_FIRM_TRAN` row exists (idempotency).
4. Inserts `TB_FIRM_TRAN` at `PROC_STAT='6'` (REQ).
5. `RealtimeTransferService.transfer(...)`:
   a. **EFS 204** (FX) only when Woori + non-VND (`isWooriFxTransfer`); response feeds the 202 FX block (**not persisted** — §10).
   b. **EFS 202** transfer.
   c. RC `0000` → success; RC `G999` → **EFS 203** recovery inquiry; other → `BusinessException`.
6. `writeStatus(...)` updates `TB_FIRM_TRAN` **and** `TB_PAY_TARGET` to `PROC_STAT='0'` (SUCC) or `'3'` (FAIL); balance fields persisted on direct 202 success.
7. Any no-definitive-response target left at `PROC_STAT='6'` is later reconciled by **PROCESSING_RESULT_CHECK** (EFS 203 → final status).

### B. Bulk/file transfer

1. WeCMS Spring writes `TB_PAY_FILE_MST` (PROC_STAT='9') + `TB_PAY_FILE_DATA` on the file path.
2. `DispatchAutoReceiveRegistrar.registerAfterCommit(...)` registers the `afterCommit` hook (gated `auto-receive`).
3. **After commit** → `BulkReceptionService.receive(...)`:
   - Loads `TB_FIRM_CONF` (VAN_ORG_CD, FILE_TYPE, BAT_TRAN_BRANCH_DIV/LIMIT).
   - `calculateBatchDivision(...)` decides single-file vs branch-split (C5 — §10).
   - Marks pay-file `PROC_STAT='6'`.
   - Per file: insert `TB_FIRM_FILE_MST` (PROC_STEP=0/PROC_STAT=6) + `TB_FIRM_FILE_DATA` (tone-normalized via `VietnameseText.replaceTone`), `FixedLengthCodec.encode` → write local staging file.
   - **SFTP upload** → audit `TB_FIRM_BAT_API_TRAN` (before call) → **EFS 301** reception request.
4. EFS sends **01301** reception callback → `BulkReceptionResultService.handle`:
   - Decrypt EV + verify VV; **validate count/amount** (`isMismatch` → `AMT_ERR`, no 00302).
   - On `result_cd='BT00'` advance PROC_STEP 0→1; record `REG_CNT`/`REG_AMT`.
   - **Inline 00302**: when all split siblings have reported, issue **EFS 302** per sibling → advance PROC_STEP 1→2.
5. EFS sends **01302** execution callback → `BulkExecutionResultService.handle`:
   - Decrypt + verify; **SFTP download** result file; `FixedLengthCodec.decode`.
   - Insert `TB_FIRM_FILE_RSLT` per D-line; reflect `PROC_RSLT_DIV`/`RS_CD` onto `TB_FIRM_FILE_DATA` and `TB_PAY_FILE_DATA`.
   - Advance PROC_STEP 2→3, PROC_STAT='0'; record trailer SUCC/UNPROC counts+amounts.
   - `finalizePayFileStatus(...)` sets `TB_PAY_FILE_MST.PROC_STAT` to `'0'` (all succ) / `'1'` (partial) / `'3'` (all fail) once all siblings reported.

---

## 8. State Machine

### `TB_PAY_TARGET.PROC_STAT` (real-time path)

`ProcStat` (`dispatch/enums/ProcStat.java`): `WAIT='9'`, `REQ='6'`, `SUCC='0'`, `FAIL='3'`.

```
9 (WAIT, written at dispatch)
  └─ afterCommit dispatch → TB_FIRM_TRAN inserted at 6 (REQ)
        ├─ EFS 202 RC=0000 ───────────────► 0 (SUCC)   [success path]
        ├─ EFS 202 G999 → EFS 203 success ► 0 (SUCC)   [recovery success]
        ├─ EFS 202 G999 → EFS 203 fail ───► 3 (FAIL)   [recovery fail]
        ├─ EFS 202 other RC ──────────────► 3 (FAIL)   [failure path]
        └─ no definitive response ────────► stays 6    [pending → PROCESSING_RESULT_CHECK re-inquires via 203]
```

Both `TB_FIRM_TRAN` and `TB_PAY_TARGET` receive the same terminal value (`RealtimeDispatchService.java:208/233`).

### `TB_FIRM_FILE_MST.PROC_STEP` (bulk/file path)

`ProcStep` (`dispatch/enums/ProcStep.java`): `REQ='0'`, `REQ_RECV='1'`, `DEPO_REQ='2'`, `DEPO_RES_RECV='3'` (PROC_STAT runs alongside: 6 in-progress → 0 succ / 3 fail).

```
0 (REQ — file built, uploaded, EFS 301 sent)
  └─ 01301 callback, result_cd=BT00 & count/amount match ─► 1 (REQ_RECV)
        │   mismatch / result_cd≠BT00 ──────────────────► stays 0, PROC_STAT=3 (FAIL), no 00302
        └─ inline 00302 (all siblings reported) ─────────► 2 (DEPO_REQ)
              │   00302 gateway fail ────────────────────► stays 1 (no auto-recovery — open item K9)
              └─ 01302 callback, result file parsed ─────► 3 (DEPO_RES_RECV), PROC_STAT=0
                    │   result file missing/corrupt ─────► stays 2 (tx rollback, replayable)
                    └─ finalizePayFileStatus ────────────► TB_PAY_FILE_MST.PROC_STAT 0 / 1 (partial) / 3
```

**Success path:** `9 → 6` (pay-file) and `0 → 1 → 2 → 3` (firm-file) → pay-file `0`.
**Failure path:** mismatch / non-BT00 / SFTP-EFS error → FAIL committed; per-file loop breaks (legacy R004 skip parity).
**No-response/pending path (real-time):** target stays `PROC_STAT='6'` → reconciled by `ResultCheckService` via EFS 203.

---

## 9. Implementation Completeness Check

| Item | Status | Evidence | Notes |
|---|---|---|---|
| EFS 201 (recipient/depositor inquiry) | **Done (disabled by default)** | `RestClientEfsApiClient.send(EFS_201,…)`; `RecipientLookupService`/`DepositorInquiryService`; key `/WGVN_EFS_201.jct` (`EfsProperties.java:68`) | Gated `recipient-inquiry` + `efs.enabled`. (Account *balance* rides in the 202 response, not 201.) |
| EFS 202 (deposit transfer) | **Done (disabled by default)** | `RealtimeTransferService.java:141` (`send(EFS_202,…)`) | RC handling: 0000 / G999 / other. |
| EFS 203 (result inquiry) | **Done (disabled by default)** | `ResultCheckService.java:112`; also G999 recovery | Used by both the result-check job and inline recovery. |
| EFS 204 (FX / exchange) | **Done — result not persisted (PO decision pending)** | `RealtimeTransferService.java:233` (`send(EFS_204,…)`) | Woori + non-VND only; feeds 202 FX block; no `TB_FIRM_TRAN` row written for 204 (§10). |
| EFS 301 (file reception request) | **Done (disabled by default)** | `BulkReceptionService` `issueReception(...)`; `/WGVN_EFS_301.jct` | |
| EFS 302 (file execution request) | **Done (disabled by default)** | `BulkReceptionResultService.issueExecutionInline(...)` (`send(EFS_302,…)`) | Runs inline from the 01301 callback. |
| 01301 callback (reception result) | **Done** | `BulkReceptionResultController` + `BulkReceptionResultService.handle()` | Count/amount validation + inline 00302. |
| 01302 callback (execution result) | **Done** | `BulkExecutionResultController` + `BulkExecutionResultService.handle()` | Download → parse → `TB_FIRM_FILE_RSLT` → final status. |
| FixedLengthCodec | **Done** | `dispatch/file/FixedLengthCodec.java` (1000-byte H/D/T, byte-offset slices, encode/decode symmetric) | Memory notes byte-verified vs 6 UAT samples; SIT byte-parity re-check pending (B5). |
| SFTP upload/download | **Done (disabled by default)** | `JschSftpClient.java:47-109`; `MockSftpClient` throws | Gated `sftp.enabled`. |
| PROCESSING_RESULT_CHECK | **Done (disabled by default)** | `ResultCheckJob.java:39` + `ResultCheckService.runOnce()` | Cron default `"-"`. |
| Slack notification | **Not ported (deferred)** | No `MsgSendUtil` consumer in `dispatch/**` | Logging-only today; documented deferral. |
| DB writes | **Done** | `TB_FIRM_TRAN`/`TB_PAY_TARGET`/`TB_FIRM_FILE_*`/`TB_PAY_FILE_*`/`TB_FIRM_BAT_API_TRAN` persisted across the services above | All target tables present in the legacy-table inventory. |
| Feature flags / default-disabled state | **Done** | `application-local/sit/uat/prod.yml` dispatch block; `@ConditionalOnProperty` on EFS/SFTP clients | Six flags default OFF; mocks throw (no fake success). |
| Crypto (AES-256 + HMAC) | **Done** | `EfsCryptoService.java:54-56,117-129,178` | AES/CBC/PKCS5 + HmacSHA256 + constant-time verify. |
| VAN API key resolution | **Done — throws until provisioned (B2)** | `VanApiKeyService.java:39-45` (`IllegalStateException` if `TB_VAN_API_INFO` empty) | Hard gate, no fallback key. |
| Callback IP allow-list | **Done** | `dispatch/callback/CallbackIpWhitelistFilter` + `CallbackProperties` | Needs SIT/PROD callback-IP config (B7). |

**Configuration / shared references covered:** `TB_VAN_API_INFO` (`VanApiKeyService`/`TbVanApiInfoRepository`), `TB_FIRM_CONF` (`TbFirmConfRepository`, loaded in `BulkReceptionService`), `TB_BATCH_SCHEDULE_INFO`/`_DTL` (entities + repos present; window-gate not yet enforced), SFTP (`SftpProperties`), EFS (`EfsProperties`).

---

## 10. Remaining Work

Real, code/doc-evidenced remaining items only:

1. **C5 — branch-split edge case (deferred).** `BulkReceptionService.calculateBatchDivision(...)` implements the `Y`/limit split, but the above-limit multi-file path retains a safe degrade (limit ≤ 0 / null → single file) and the full split-loop semantics for above-limit batches are flagged deferred. Confirm the split rule against legacy `TB_FIRM_CONF.BAT_TRAN_BRANCH_*` before enabling for banks that exceed the per-file limit.
2. **204 FX persistence (pending PO — Mr. Jung).** EFS 204 is called and its rate feeds the 202 request, but no standalone `TB_FIRM_TRAN TRAN_TP='204'` row (and the `TB_PAY_TARGET_U002` FX columns) is written. Decision needed: persist the FX inquiry for audit, or keep it request-time-only.
3. **SIT infrastructure provisioning** — B1 (EFS base-url/partner-id), B2 (`TB_VAN_API_INFO` API key), B4 (SFTP host/key/paths), B7 (callback IP allow-list + firewall), I5 (`TB_FIRM_CONF` rows). SIT DB is Flyway-off (`ddl-auto: validate`) so any DDL/seed is applied by a DBA manually.
4. **SIT verification** — B5 byte-parity re-check of `FixedLengthCodec` vs a captured gateway sample (EV/VV URL-encode pass count + the `BT00` reception-code set are the highest pre-SIT risk), then V1–V6 round-trip executions.
5. **Slack/`MsgSendUtil` alerting** — not ported; decide whether SIT/PROD needs it or whether log-based monitoring suffices.
6. **Operating-hours window gate** — `TB_BATCH_SCHEDULE_*` entities exist but the dispatch services do not yet enforce the bank batch window. Confirm whether TO-BE requires it.

---

## 11. Risk / Decision Items (for PO / Tech Lead)

| # | Decision | Owner | Recommendation |
|---|---|---|---|
| R1 | **Batch activation sequence** — order and env for flipping the six flags ON (`efs.enabled`, `sftp.enabled`, `auto-receive`, `realtime-dispatch`, `recipient-inquiry`, `result-check.cron`). | Mr. Kang / Mr. Jung | Enable in SIT only, EFS+SFTP first (with mocks proven to throw), then triggers, then the cron — one leg at a time with round-trip verification. |
| R2 | **204 FX persistence behavior** (§10.2). | Mr. Jung (PO) | Persist a `TRAN_TP='204'` audit row if FX reconciliation/audit is required; otherwise formally accept request-time-only. |
| R3 | **C5 branch-split for above-limit batches** (§10.1). | Mr. Kang | Verify split semantics vs legacy before any bank with `BAT_TRAN_BRANCH_DIV='Y'` goes live. |
| R4 | **SIT/PROD configuration** — EFS endpoint + partner-id (B1), VAN API key seeding (B2), SFTP host/keys (B4), callback IP allow-list + firewall (B7), `TB_FIRM_CONF` rows (I5). | Infra / DBA | Provision before enabling; all DDL/seed run manually (Flyway off). |
| R5 | **Is PROCESSING_RESULT_CHECK enough as the only scheduler?** | Mr. Kang | For the event-driven TO-BE, yes — it is the only residual polling needed (202 no-response safety net). Confirm callback delivery SLAs/retries so the 203 sweep remains a backstop, not the primary path. |
| R6 | **Old-process compatibility / coexistence** — whether the legacy JEX batch farm runs in parallel against the same tables during cutover. | Mr. Jung / Mr. Kang | Define cutover; the TO-BE flags being OFF by default means the Spring app does not contend for `PROC_STAT='9'` rows until explicitly enabled. |
| R7 | **Slack alerting** (§10.5). | Mr. Kang | Decide log-based monitoring vs porting `MsgSendUtil`. |

---

## 12. Final Conclusion

- **Is WeCMS Batch implementation done from a code perspective?** **Yes, structurally.** The full TO-BE dispatch pipeline — real-time (afterCommit + 204/202/203), bulk/file (afterCommit + 301 + inline 302 + 01301/01302 callbacks + result-file ingest), fixed-length codec, AES/HMAC crypto, JSch SFTP, and the `PROCESSING_RESULT_CHECK` scheduler — is implemented as real, production-grade code with real DB writes. The mocks throw rather than fake success, so nothing silently "passes" while disabled. The only non-mainline coding items are the C5 above-limit branch-split edge case and the 204 FX persistence decision (both PO-gated).
- **Is it production/SIT ready?** **Not yet active.** It is **implemented but disabled by default** in every profile (six flags OFF, result-check cron `"-"`). It cannot be exercised end-to-end until the gateway/SFTP/key/firewall infrastructure is provisioned and SIT round-trips pass.
- **What is blocking actual activation?** Infrastructure provisioning (B1 EFS endpoint/partner-id, B2 `TB_VAN_API_INFO` key, B4 SFTP host/keys, B7 callback IPs + firewall, I5 `TB_FIRM_CONF` rows) and SIT verification (B5 byte-parity, V1–V6 round-trips) — plus the two PO decisions (204 FX persistence, C5 split). None of these is a missing code module; they are activation and sign-off steps.

---

*Read-only audit. No source, configuration, test, or SQL was modified. Evidence cites exact files/lines under `wecms-api/src/main/java/com/kosign/wecms/domain/payment/dispatch/` and the dispatch config in `application*.yml`.*
