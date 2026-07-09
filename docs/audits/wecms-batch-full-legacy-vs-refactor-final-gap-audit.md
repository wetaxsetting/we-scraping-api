# WeCMS Batch Full Legacy vs Spring Refactor Final Gap Audit

| Field | Value |
|---|---|
| Audit slug | `wecms-batch-full-legacy-vs-refactor-final-gap` |
| Module | `payment` — firm-banking dispatch (`domain/payment/dispatch/**` + `common/firmbanking/**`) |
| Method | Read-only, source-level comparison of the legacy JEX batch design (Mr. Do) against the Spring refactor (Mr. Kang TO-BE). Every claim cites `file:line`. |
| Date | 2026-06-13 |
| Reviewers | Mr. Jung (PO), Mr. Kang (TO-BE architecture) |
| Read-only | Yes — no code/config/test/SQL/doc was modified. Only this report was created. |
| Supersedes (on two points) | The "pending" status of **C5 branch-split** and **204 FX persistence** in memory `project_batch_refactor_real_impl` and the earlier executive audit — both are now **implemented in source** (evidence below). This report reflects the actual current code per the audit rule "compare actual source code, not assumptions." |

> Path note: filed under `wecms-api/docs/audits/` (refactor-backend audits), consistent with the earlier `wecms-batch-tobe-process-audit.md`.

---

## 1. Executive Verdict

- **Code-complete?** **Yes (effectively).** Every legacy batch process, EFS leg (201/202/203/204/301/302), both callbacks (01301/01302), SFTP transport, fixed-length codec, crypto, and DB write path is implemented as real production-grade code. The two items previously tracked as pending — **C5 branch-split** and **204 FX persistence** — are now fully implemented in source. Remaining gaps are a small set of **deferred pre-flight features** (operating-hours gate, channel-mix validation, UAT bank override, Slack alerting) and one **minor edge case** (result-check day-boundary), none of which is a missing core flow.
- **SIT-ready?** **No — not yet.** Code is ready; activation is not. SIT needs infrastructure provisioning (EFS base-url/partner-id, `TB_VAN_API_INFO` key, SFTP host/key, callback IP firewall, `TB_FIRM_CONF` rows) and byte-parity verification before the flags can be turned on.
- **PROD-ready?** **No.** Beyond all SIT blockers, PROD additionally requires the **UAT Shinhan/Woori bank-code override** to be ported and a PO decision on the deferred pre-flight gates.
- **Main remaining blockers:**
  1. Infrastructure/SIT provisioning (non-code) — B1/B2/B4/B7/I5.
  2. SIT byte-parity verification (B5) — fixed-length file + EV/VV encode + the `BT00` reception-code set.
  3. Four deferred functional features (operating-hours gate, channel-mix validation, UAT bank override, Slack alerting) — PO must rule which are required for SIT vs PROD.
  4. One minor code fix (result-check day-boundary) and one doc-only fix (stale `ResultCheckService` class Javadoc).

---

## 2. Legacy Process Coverage Matrix

| Legacy process | Legacy purpose | Spring replacement | Status | Evidence | Notes |
|---|---|---|---|---|---|
| **WB_00000100** | Account-holder (depositor) name inquiry, synchronous | `FirmBankingInquiryService` → `DepositorInquiryService` → `RecipientLookupService` (EFS 201) | **Done (disabled by default)** | `RecipientLookupService.java:75-123`; `DepositorInquiryService.java:68-118`; `FirmTranInquiryAuditService.java:51-113`; gate `FirmBankingInquiryService.java:107-108,152-156` | Synchronous service call, not batch. Full `TB_FIRM_TRAN` `TRAN_TP='201'` C001/U002 lifecycle. Transport socket→REST (intentional). |
| **NAPAS_REAL_TIME_BATCH** | Poll waiting VND targets, push 202 (+204 FX) | `RealtimeDispatchRegistrar` (afterCommit) → `RealtimeDispatchService` → `RealtimeTransferService` | **Done (disabled by default)** | `RealtimeDispatchRegistrar.java:52-73`; `RealtimeDispatchService.java:105-208`; `RealtimeTransferService.java:100-187` | Polling loop replaced by per-commit afterCommit hook. 204 FX persistence now implemented (`runFxInquiry`). |
| **PROCESSING_RESULT_CHECK_BATCH** | Re-check no-response 202s (203), reconcile status | `ResultCheckJob` → `ResultCheckService.runOnce()` | **Done (disabled by default)** | `ResultCheckJob.java:39-44`; `ResultCheckService.java:152-243` | Only surviving scheduler. Cron `"-"`. Minor day-boundary edge case (§6.6). |
| **FILE_DEPOSIT_TRANSFER_BATCH1** | Build fixed-length file, SFTP upload, request reception (301) | `DispatchAutoReceiveRegistrar` (afterCommit) → `BulkReceptionService.receive()` | **Done (disabled by default)** | `DispatchAutoReceiveRegistrar.java:52-73`; `BulkReceptionService.java:149-379,527-564` | C5 branch-split now ported (`:380-405`). Pre-flight gates deferred (§6.1–6.2). |
| **FILE_DEPOSIT_TRANSFER_BATCH2** | After reception accepted, request execution (302) | Absorbed inline into the 01301 callback: `BulkReceptionResultService.issueExecutionInline()` | **Done (replaced/absorbed)** | `BulkReceptionResultService.java:191-265,367-404` | No standalone second batch (Mr. Kang K9, 2026-06-12). 00302 fires inline on reception success. |
| **01301 callback** | Receive reception result, validate count/amount, trigger execution | `BulkReceptionResultController` → `BulkReceptionResultService.handle()` | **Done** | `BulkReceptionResultService.java:120-208` | VV verify + count/amount reconcile + split-sibling gate + inline 00302. |
| **01302 callback** | Receive execution result, ingest result file, finalize status | `BulkExecutionResultController` → `BulkExecutionResultService.handle()` | **Done** | `BulkExecutionResultService.java:110-241` | Download → parse → `TB_FIRM_FILE_RSLT` → reflect to data tables → finalize pay-file. |

---

## 3. EFS API Coverage

All six legs route through `EfsApiClient.send(EFS_xxx, envelope)` → `/WGVN_EFS_xxx.jct` (`EfsApiClient.java:42-47`, `EfsProperties.java:66-75`). Real client `RestClientEfsApiClient` is active only when `wecms.dispatch.efs.enabled=true`; `MockEfsApiClient` throws `UnsupportedOperationException` otherwise.

| EFS API | Legacy usage | Spring class/service | DB table writes | Status | Gap if any |
|---|---|---|---|---|---|
| **201** | Depositor-name inquiry (WB_00000100) | `RecipientLookupService.java:98` | `TB_FIRM_TRAN` (`TRAN_TP='201'`, C001/U002 via `FirmTranInquiryAuditService`) | **Done (disabled by default)** | None functional |
| **202** | Real-time transfer (NAPAS_REAL_TIME_BATCH) | `RealtimeTransferService.java:161` | `TB_FIRM_TRAN` (`TRAN_TP='202'`) + `TB_PAY_TARGET` (via `RealtimeDispatchService`) | **Done (disabled by default)** | None functional |
| **203** | Result inquiry (PROCESSING_RESULT_CHECK + G999 recovery) | `ResultCheckService.java:112` | `TB_FIRM_TRAN` 6→0/3 + `TB_PAY_TARGET` propagation | **Done (disabled by default)** | Day-boundary edge (§6.6) |
| **204** | Woori FX pre-rate inquiry | `RealtimeTransferService.java:254-286` (call) + `RealtimeDispatchService.java:238-307` (persist) | `TB_FIRM_TRAN` (`TRAN_TP='204'`, U005) + `TB_PAY_TARGET` FX cols (U002) | **Done (disabled by default)** | **Persistence IS implemented** — corrects prior "pending PO" note |
| **301** | Bulk reception request (BATCH1) | `BulkReceptionService.java:527-564` | `TB_FIRM_BAT_API_TRAN` (00301 audit) + `TB_FIRM_FILE_MST/DATA` | **Done (disabled by default)** | None functional |
| **302** | Bulk execution request (BATCH2) | `BulkReceptionResultService.java:367-404` | `TB_FIRM_BAT_API_TRAN` (00302 audit) + `TB_FIRM_FILE_MST` step 1→2 | **Done (disabled by default)** | None functional |

**Crypto (all legs):** `EfsCryptoService` — real AES-256/CBC/PKCS5 EV + HMAC-SHA256 VV + constant-time verify (`MessageDigest.isEqual`). Inbound callback envelopes are VV-verified before decrypt (`BulkReceptionResultService.java:129`, `BulkExecutionResultService.java:119`, `RecipientLookupService.java:111`).

**VAN key:** resolved per-`vanOrgCd` from `TB_VAN_API_INFO` via `VanApiKeyService`, always **before** any envelope is built; throws `IllegalStateException` until seeded (B2).

---

## 4. File / SFTP Flow Coverage

| Step | Legacy | Spring | Status | Evidence |
|---|---|---|---|---|
| Fixed-length H/D/T file creation | `Deposit_Header/Data/Trailer` builder | `FixedLengthCodec.encode(header, lines, trailer)` (1000-byte records, byte-offset fields) | **Done** | `BulkReceptionService.java:327-333`; codec `FixedLengthCodec` (H/D/T, 1000B) |
| SFTP upload | BATCH1 SFTP put | `JschSftpClient.upload()` (real JSch); `MockSftpClient.upload()` throws | **Done (disabled by default)** | `BulkReceptionService.java:339-346`; `JschSftpClient.java:47-63` |
| EFS 301 | BATCH1 reception request | `BulkReceptionService.issueReception()` | **Done** | `BulkReceptionService.java:354-378,527-564` |
| 01301 callback | Reception result inbound | `BulkReceptionResultService.handle()` | **Done** | `BulkReceptionResultService.java:120-208` |
| EFS 302 | BATCH2 execution request | `issueExecutionInline()` → `issueExecution()` | **Done** | `BulkReceptionResultService.java:221-265,367-404` |
| 01302 callback | Execution result inbound | `BulkExecutionResultService.handle()` | **Done** | `BulkExecutionResultService.java:110-241` |
| Result file download | BATCH result fetch | `JschSftpClient.download()` + best-effort `move()` | **Done (disabled by default)** | `BulkExecutionResultService.java:284-299` |
| Result file parsing | Fixed-length parse | `FixedLengthCodec.decode()` (malformed → throw → rollback, replayable) | **Done** | `BulkExecutionResultService.java:301-314` |
| `TB_FIRM_FILE_RSLT` write | Per-line result rows | one `TbFirmFileRslt` per D line | **Done** | `BulkExecutionResultService.java:171-217` |
| `TB_PAY_FILE_DATA/MST` final status | Reflect + finalize | reflect per line + `finalizePayFileStatus` (sibling-aware) | **Done** | `BulkExecutionResultService.java:202-213,248-280` |

---

## 5. DB State Transition Comparison

| Table.column | Legacy values | Spring values | Match? | Evidence |
|---|---|---|---|---|
| `TB_PAY_TARGET.PROC_STAT` | 9 wait → 0/3 (transfer outcome) | `WAIT=9` → `REQ=6`(firm-tran) → `SUCC=0`/`FAIL=3` written back | ✅ | `RealtimeDispatchService.java:348-364`; `ResultCheckService.java:226-242` |
| `TB_FIRM_TRAN.PROC_STAT` | 6 request → 0/3 result (per TRAN_TP 201/202/204) | `REQ=6` insert → `SUCC=0`/`FAIL=3` (U002/U004/U005) | ✅ | 201: `FirmTranInquiryAuditService.java:72,105`; 202: `RealtimeDispatchService.java:189,352`; 204: `RealtimeDispatchService.java:264,276,300` |
| `TB_PAY_FILE_MST.PROC_STAT` | 9 → 6 → 0/1(partial)/3 | `WAIT=9` → `REQ=6` → `0`/`1`/`3` (sibling-aware finalize) | ✅ | `BulkReceptionService.java:192`; `BulkExecutionResultService.java:259-279` |
| `TB_FIRM_FILE_MST.PROC_STEP` | 0→1→2→3 | `REQ=0` → `REQ_RECV=1` → `DEPO_REQ=2` → `DEPO_RES_RECV=3` | ✅ | insert `:265`; 0→1 `BulkReceptionResultService.java:182`; 1→2 `:248`; 2→3 `BulkExecutionResultService.java:221` |
| `TB_FIRM_FILE_DATA` result status | `PROC_RSLT_DIV` + `RS_CD` per line | reflected from parsed result onto firm + pay data | ✅ | `BulkExecutionResultService.java:202-213` |
| `TB_FIRM_BAT_API_TRAN` audit | One row per API leg (00301/01301/00302/01302), audit-before-call | Same — written before the gateway call, updated with RC | ✅ | `BulkReceptionService.java:350-373`; `BulkReceptionResultService.java:158,233`; `BulkExecutionResultService.java:158,234` |

Mismatch handling (01301): received count/amount vs `REQ_CNT`/`REQ_AMT`; on mismatch → `FAIL` with `REG_RSLT_CD='9999'` (`AMT_ERR`), no 00302 (`BulkReceptionResultService.java:172-179,322-326`).

---

## 6. Remaining Functional Gaps

### 6.1 Operating-hours gate (`TB_BATCH_SCHEDULE_INFO/DTL`)
- **Legacy:** BATCH1 pre-flight checks the bank batch window before dispatch.
- **Refactor:** Entities + repositories exist (`TbBatchScheduleInfo/Dtl`, `TbBatchScheduleInfoRepository`, `TbBatchScheduleDtlRepository`) but are **not consumed** by any dispatch logic. Explicitly deferred (`BulkReceptionService.java:102-106`).
- **Risk:** Low while disabled; on activation a dispatch could fire outside the bank window. Medium for PROD.
- **Decision/fix:** PO decides if required for SIT or PROD; if yes, wire the window check into `BulkReceptionService.receive()` (and optionally `RealtimeDispatchService`).
- **Files likely affected:** `BulkReceptionService.java`, the two `TbBatchSchedule*Repository` interfaces.

### 6.2 Channel-mix validation (EBANK / VCB / CITAD / NAPAS rules)
- **Legacy:** BATCH1 validates the channel mix of the detail rows before building the file.
- **Refactor:** Explicitly deferred (`BulkReceptionService.java:104`).
- **Risk:** Medium — a malformed channel mix could be sent to the gateway.
- **Decision/fix:** Confirm the legacy ruleset with PO; port into the detail-build loop.
- **Files likely affected:** `BulkReceptionService.java` (detail loop `:273-325`).

### 6.3 UAT Shinhan/Woori product-type override
- **Legacy:** Under UAT, `OUT_BANK_CD`/`BANK_PRTN_ID` are swapped to WEBC/HASU partner ids for Shinhan (`970424`)/Woori (`970457`) keyed off product type (`PymnHelper.java:916-946`).
- **Refactor:** Firm-config values used verbatim; override deferred (`FirmBankingInquiryService.java:87-90,174-177`).
- **Risk:** High for UAT/PROD on those two banks; none for local R&D (no UAT env).
- **Decision/fix:** Port before any UAT/PROD activation involving Shinhan/Woori.
- **Files likely affected:** `FirmBankingInquiryService.java`, `RealtimeDispatchService.java` (cmd build), possibly a shared override helper.

### 6.4 204 FX persistence — **NOT a gap (implemented)**
- **Legacy:** Persist a `TRAN_TP='204'` `TB_FIRM_TRAN` row + FX columns on `TB_PAY_TARGET` before the 202.
- **Refactor:** **Implemented.** `RealtimeDispatchService.runFxInquiry()` allocates an independent `TRAN_SEQ`, inserts the `TRAN_TP='204'` row at `PROC_STAT='6'`, runs the 204, on success updates it to `'0'` with `DEP_CCY_CD/APL_EXC_RATE/WITHD_AMT/DEP_AMT` (U005) **and** writes the same four FX columns onto `TB_PAY_TARGET` (U002); on failure sets `'3'` and skips the 202 (`RealtimeDispatchService.java:238-307`).
- **Risk:** None — corrects the stale "pending PO" note in memory `project_batch_refactor_real_impl`. If any narrower sub-decision was intended (e.g. FX audit retention policy), confirm with PO, but the code path is complete.
- **Decision/fix:** Update the memory/earlier audit; no code change required.

### 6.5 Slack / `MsgSendUtil` alerting
- **Legacy:** BATCH1 and the 204 fatal path log + Slack-alert on failure.
- **Refactor:** Not ported; logging-only (`BulkReceptionService.java:105`; legacy alert referenced at `RealtimeTransferService.java:251`).
- **Risk:** Operational visibility only — no functional impact on the transfer itself.
- **Decision/fix:** PO/Tech Lead decide log-based monitoring vs porting `MsgSendUtil`.
- **Files likely affected:** failure branches in `BulkReceptionService`, `RealtimeDispatchService`, `ResultCheckService`.

### 6.6 PROCESSING_RESULT_CHECK midnight / day-boundary edge case
- **Legacy:** Selects 202 transactions "from 2 minutes ago" (timestamp arithmetic).
- **Refactor:** Filters `tranDt == today (ICT)` **and** string-compares `tranTm <= (now − 2 min as HHmmss)` (`ResultCheckService.java:159-160`). Consequences when enabled with traffic near midnight: (a) a prior-day pending 202 is never re-checked (its `tranDt` ≠ today); (b) in the first 2 minutes after midnight the `HHmmss` compare degenerates (`000030 <= 235900`), selecting very-fresh rows before the 2-minute guard truly elapses.
- **Risk:** Low (narrow window; job disabled by default), but real.
- **Decision/fix:** Replace the same-day + string-time filter with a `RQ_DTTM`/timestamp comparison across the day boundary.
- **Files likely affected:** `ResultCheckService.java:152-161`, possibly `TbFirmTranRepository` (add a datetime-range finder).

### 6.7 (Doc-only) Stale `ResultCheckService` class Javadoc
- The class header (`ResultCheckService.java:38-42,51-54`) still says `runOnce()` "stays a no-op" with "No DB status writes (D3)", contradicting the implemented body (`:152-243`, dated C4 2026-06-13). Documentation drift only — no functional impact. Recommend a comment fix.

### 6.8 (Note, not a gap) `BT00` reception-code set
- `BulkReceptionResultService.java:101` carries `TODO(B5): confirm full reception result-code set`. The single success token `BT00` is implemented; SIT must confirm whether additional success/partial codes exist. Tracked under SIT byte-parity (B5).

### 6.9 (Note, not a gap) Transaction-model deviation
- Legacy ran SFTP/EFS I/O outside the DB transaction; the refactor runs them inside the `@Transactional` `receive()`/`dispatch()` unit (documented, accepted for low-volume R&D — `BulkReceptionService.java:79-85`). Re-evaluate only if PROD volume warrants.

---

## 7. Activation / Infra / SIT Blockers (non-code)

| ID | Blocker | What's needed | Evidence the code is ready |
|---|---|---|---|
| B1 | EFS endpoint / partner id | Set `wecms.dispatch.efs.base-url` + `partner-id`, flip `efs.enabled=true` | `EfsProperties.java:43-52`; mock throws until enabled |
| B2 | `TB_VAN_API_INFO` seed | DBA seeds per-VAN API key (manual SQL; Flyway off) | `VanApiKeyService` throws until seeded |
| B4 | SFTP host / key / paths | Set `wecms.dispatch.sftp.*` + flip `sftp.enabled=true` | `JschSftpClient.java:47-109`; `SftpProperties` |
| B7 | Callback IP allow-list / firewall | Configure `CallbackProperties` IP allow-list + network firewall for inbound 01301/01302 | `CallbackIpWhitelistFilter`, `CallbackProperties` |
| I5 | `TB_FIRM_CONF` rows | Seed per-tenant firm config (`VAN_ORG_CD`/`OUT_BANK_CD`/`BANK_PRTN_ID`/`WDRL_ACT_NO`/branch-split) | gates `BulkReceptionService.java:164-168`, `RealtimeDispatchService.java:130-134` |
| B5 | SIT byte-parity | Verify fixed-length file bytes + EV/VV encode passes + the `BT00` reception-code set vs captured gateway samples | `FixedLengthCodec`; `BulkReceptionResultService.java:101` |
| V1–V6 | SIT round-trip tests | Execute real 201/202(+204)/203 + 301→01301→302→01302 round-trips end-to-end | all flows above |

**Feature flags (all default OFF, verified in all four profiles):** `wecms.dispatch.efs.enabled`, `.sftp.enabled`, `.auto-receive`, `.realtime-dispatch`, `.recipient-inquiry`, `.result-check.cron` (`-`). Evidence: `application-local.yml:80,83,88,94,103,113`; `application-sit.yml:87,90,92,98,107,118`; `application-uat.yml:26,29,31,35,41,47`; `application-prod.yml:34,35,36,38,43,49`.

---

## 8. Final Conclusion

**Is the WeCMS Batch refactor done from a code perspective?** **Yes — effectively code-complete.** All seven legacy processes are replaced or absorbed, all six EFS legs and both callbacks are implemented, SFTP and the fixed-length codec are real, crypto and VAN-key resolution are correct, and the full DB state machine (`TB_PAY_TARGET`, `TB_FIRM_TRAN`, `TB_PAY_FILE_MST/DATA`, `TB_FIRM_FILE_MST/DATA/RSLT`, `TB_FIRM_BAT_API_TRAN`) matches legacy. The two items previously tracked as pending — **C5 branch-split** (`BulkReceptionService.java:380-405`) and **204 FX persistence** (`RealtimeDispatchService.java:238-307`) — are implemented in source; the "pending" labels in memory and the earlier executive audit are stale and should be corrected.

**What exactly remains?**
- **Code changes (small):** result-check day-boundary fix (§6.6); doc-only Javadoc correction (§6.7).
- **Deferred features (PO scope):** operating-hours gate (§6.1), channel-mix validation (§6.2), UAT Shinhan/Woori override (§6.3), Slack alerting (§6.5).
- **Infra/SIT (non-code):** B1/B2/B4/B7/I5 provisioning, B5 byte-parity, V1–V6 round-trips (§7).

**Which items are PO / Tech Lead decisions?** Operating-hours gate, channel-mix validation, and Slack alerting — required for SIT or only PROD? Plus confirmation that 204 FX persistence (now implemented) satisfies the original PO intent.

**Which items are infrastructure / SIT only?** B1, B2, B4, B7, I5, B5 byte-parity, and the V1–V6 round-trips. None require code changes — only configuration, seeding, firewall, and verification.

**Which items require a code change before PROD?**
1. **UAT Shinhan/Woori bank-code override (§6.3)** — mandatory before any PROD activation involving those two banks.
2. **Result-check day-boundary fix (§6.6)** — before the result-check cron is enabled in PROD.
3. **Operating-hours gate / channel-mix validation (§6.1–6.2)** — only if PO confirms they are required for PROD.

**Net:** the batch refactor is built and structurally complete; what stands between it and SIT/PROD is **activation, provisioning, verification, and a short list of deferred pre-flight features + one minor edge-case fix** — not missing core functionality.

---

*Read-only audit. No source, configuration, test, SQL, or existing doc was modified — only this report was created. Evidence cites exact files/lines under `wecms-api/src/main/java/com/kosign/wecms/domain/payment/dispatch/`, `common/firmbanking/`, and the four `application-*.yml` profiles.*
