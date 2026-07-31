# WeCMS Batch — Legacy (JEX) vs Spring Boot Refactor Parity Audit

**Date:** 2026-07-21
**System:** Refactor (`wecms-api`) vs Legacy JEX (`WECMS_BATCH DEV/`)
**Scope:** Full firm-banking dispatch subsystem — bulk-transfer reception (BATCH1), bulk-transfer execution request (BATCH2), real-time single transfer (NAPAS_REAL_TIME), processing-result check, the two inbound EFS callbacks (API1/API2), the shared support layer (fixed-length codec, crypto, tone, sequence, constants, alerts), the data model / IDO mapping, and the WeCMS→Batch data hand-off.
**Method:** Source-level comparison of every legacy batch program + support class + IDO/WSVC XML + `_act.jsp` against the refactor `domain/payment/dispatch/**`. Six parallel deep audits, each finding independently cross-checked; all HIGH findings re-verified by hand against source.

> **Related docs:** [features/wecms-batch/firm-banking-dispatch.md](../features/wecms-batch/firm-banking-dispatch.md) · [features/wecms-batch/batch-schedule-operating-hours-audit.md](../features/wecms-batch/batch-schedule-operating-hours-audit.md) · [features/haengsung-dispatch-result-writeback.md](../features/haengsung-dispatch-result-writeback.md)

---

## 0. Bottom line

The refactor is a **faithful, high-fidelity port of the batch business logic.** The single highest-risk artifact — the 1000-byte fixed-length H/D/T firm-banking file — is **byte-identical** to legacy (every offset, length, pad rule, and both dated field changes). Crypto (AES-256/CBC/zero-IV/PKCS5 + HMAC-SHA256 EV/VV), key generation (`firm_seq_no`/`firm_file_seq_no` via the `wb_get_next_val` funnel), all 34 `WCConst` constants + enums, the validation ruleset, the operating-hours gate, branch division, Vietnamese tone-stripping, and the WeCMS→Batch `PROC_STAT='9'` hand-off contract all match. **All six dated legacy updates are reflected.**

The refactor also makes one deliberate **architectural change** — it replaced legacy's minute-by-minute *polling batches* with *event-driven after-commit hooks* + inbound callbacks (the approved "TO-BE" design, Mr. Kang; see [wecms-batch-tobe-process-audit.md](wecms-batch-tobe-process-audit.md)). **This change is NOT a parity gap** — the implementation matches the approved TO-BE, and this audit measures against that architecture, not against the removed legacy polling loops. It does, however, carry a **reliability consideration** (records stranded at `PROC_STAT='9'` with no recovery path) — reclassified below as **R-1 (reliability enhancement, not parity)** after review on 2026-07-21. Separately, a cluster of failure-path and integration-contract gaps means the refactor is not yet behaviorally equivalent on the failure/recovery and gateway-acknowledgement paths. There are **7 HIGH** and **12 MEDIUM** parity findings, plus **1 reliability enhancement (R-1)**, below.

**Critical activation caveat:** the entire dispatch subsystem is **inert by default** — every leg is behind a flag that ships OFF (`wecms.dispatch.efs.enabled`, `.sftp.enabled`, `.auto-receive`, `.realtime-dispatch`, `.recipient-inquiry`, `.result-check.cron="-"`, `.alert.enabled`). **None of these findings is a live defect today.** They are **pre-activation parity gaps** that must be closed before dispatch is enabled at SIT/UAT. This audit is exactly the "SIT-readiness" work the batch implementation memo flagged as remaining.

---

## 0.1 Implementation status — parity-fix pass (started 2026-07-21)

Legend: **FIXED** (implemented + tested green) · **PENDING** (planned, not yet implemented) ·
**CONFIRMED NO CHANGE** · **REQUIRES EXTERNAL CONFIRMATION**. Build plan:
[docs/snoopy-stirring-iverson.md](../../../docs/snoopy-stirring-iverson.md). All dispatch flags remain OFF.

| Finding | Item | Status | Evidence |
|---|---|---|---|
| H-6 | A4 — `data_dcm_amt` = legacy `BigDecPaser` (shared `AmountSplitter`) | **FIXED** | `van/AmountSplitter.java` (new) + 3 call sites delegate; `AmountSplitterTest` (12 cases) green; `RealtimeDispatchServiceTest.splitAmount_handlesWholeNumber` updated to legacy `"00"`; dispatch suite 176+ green. *Gateway acceptance of the exact form = REQUIRES EXTERNAL CONFIRMATION (OQ-1).* |
| M-9 | H1 — Vietnamese tone/byte-truncate order | **FIXED** | `BulkReceptionService.java:410,432` now `replaceTone(byteTruncate(addr,180))` (legacy truncate-then-tone); dispatch suite green. |
| X-2 | A1 — TNO = TRAN_SEQ | **FIXED** (203-envelope with B3) | **All request legs** now send the allocated `TRAN_SEQ` as the gateway TNO, never a UUID: 202/204 (`RealtimeTransferService`, threaded `tno` param — 202=pay-target `tranSeq`, 204=`ccyExcSeq`), 301 (`BulkReceptionService`, `BulkReceptionApiCommand.tno` = audit `TRAN_SEQ`), 302 (`BulkReceptionResultService`, `BulkExecutionApiCommand.tno` = audit `TRAN_SEQ`). The 01302 callback audit row's `TRAN_SEQ` is now the **callback's TNO** (`BulkExecutionResultService.writeApiTran`, D6), not a fresh seq. All three `newTno` UUID helpers deleted. Combined with `ResultCheckService.org_tno = row.getTranSeq()`, the 202→203 recovery chain is consistent. Tests assert `sent.tno()==TRAN_SEQ` (`RealtimeTransferServiceTest`, `BulkReceptionServiceTest`) + blank-tno guards; dispatch suite green. **Coupled to B3:** the 203 inquiry's *own* envelope TNO (a fresh allocated seq that also keys the B3 203 audit row) lands with B3 — the recovery correlation (org_tno) is already fixed, so this is the inquiry's own traceability id only. |
| H-2/A1/C1 | A2 — callback ACK envelope | **FIXED** | Both callbacks now return the EFS `EfsEnvelope` ack (`ID/RQ_DTIME/TNO/EV/VV/EM/VM/RC/RM`) instead of `ApiResponse`. **01301** (`BulkReceptionResultService.receptionAck`) rebuilds `EV`/`VV` over the six echo fields with `RC=0000` on reception success (legacy API1:409-423), echoes on failure with the WeCMS-processing RC (`G003` verify-fail / `9999` mismatch / `G998` sibling-stop / `0000` recorded-fail); **01302** (`BulkExecutionResultService.ack`) is RC/RM-only, echoing the request `EV/VV/ID/TNO/EM/VM` verbatim (the load-bearing 01301↔01302 asymmetry). Controllers return the envelope; `BulkCallbackRoutingTest` updated; `successful_01301_...` asserts the rebuilt EV decrypts to the 6 fields + `verifyVv` true. **Note:** 01302 download/parse failures stay as throws (deliberate replayable rollback, G1); the exact per-path RC set is a B5/gateway confirmation item. |
| H-5/D3 | A3 — outbound response VV verify | **FIXED** | All 3 outbound legs verify VV before decrypt: 203 (`ResultCheckService.inquire`), 202 + 204 (`RealtimeTransferService`). 202 VV-fail → pending (leave '6'); 204 VV-fail → G003 error; 203 VV-fail → stays '6'. Tests: `RealtimeTransferServiceTest.success_rc_0000_withInvalidVv_returnsPending`, `woori_204_invalidVv_throws...`, `ResultCheckPollTest.invalidResponseVv_notProcessed_leftAt6` — all green. |
| H-1/B1 | B1 — RC=0001 definitive | **FIXED** | `ResultCheckService.inquire` returns `ResultCheckInquiryResult.notFound` on envelope RC=0001 → finalizes FAIL; `ResultCheckPollTest.envelopeRc0001_notFound_finalizesFailNotStuckAt6` green (regression guard vs infinite '6'). |
| H-4/D2 | B2 — G999/no-response stays '6' | **FIXED** | `RealtimeTransferResult` reshaped `recovered`→`pending`; `RealtimeTransferService` returns `ofPending()` on G999/no-response/VV-fail (removed synchronous inline 203); `RealtimeDispatchService.applyPending` leaves both firm-tran + pay-target at '6' for the scheduled result-check. Tests: `RealtimeTransferServiceTest.g999_returns_pending_and_does_no_synchronous_203`, `noResponse_returnsPending`; `RealtimeDispatchServiceTest.pendingResult_leavesBothRowsAt6_forResultCheck` — green. |
| H-3/D1/A2 | C1 — sibling fail-stop + G998 | **FIXED** | `BulkReceptionResultService.handle` now runs the legacy 2025.09.10 guard (`siblingAlreadyFailed` = pay-file `PROC_STAT='3'`, legacy R004) BEFORE reconciling each split sibling → writes a `G998`/`MSG_TARGET_STAT_ERR` 01301 audit row + marks the firm file `PROC_STEP='1'/PROC_STAT='3'` and stops (no reception advance, no 00302) → prevents partial money movement. Also fixed the 01301 audit `TRAN_SEQ`=callback TNO (A1 parallel). Tests `siblingFailStop_whenPayFileAlreadyFailed_marksG998_...` green. |
| M-1 | C2 — reception-failure → pay-file FAIL | **FIXED** (pulled in with C1) | `failFile` now also marks the matching `TB_PAY_FILE_MST PROC_STAT='3'` + `RS_MSG` (legacy U001) — required for C1 to trip on the next sibling and for the summary to reflect the failure. Test `receptionFailure_propagatesFailToPayFile` green. |
| M-11 | E1 — `RCPT_USE_ADDR_SI` (beneficiary town name) | **FIXED (dispatch chain) + 1 follow-up** | **Extended 2026-07-21** after the team provisioned `RCPT_USE_ADDR_SI` on 3 tables + `BNFC_TOWN_NM` on `OVRS_PYMN_SUMR_DETL` (source `TRCO_OVRS_INFM.BENE_TOWN_NM`). Now mapped on **4 entities** (`TbPayFileData`, `TbFirmFileData`, `TbFirmFileRslt` — all varchar 35, DB-verified; `OvrsPymnSumrDetl.bnfcTownNm` varchar 150) + the source `TrcoOvrsInfm.beneTownNm`. Carried end-to-end through dispatch: overseas write (`OverseasDispatchExecutor` from `line.getBnfcTownNm()`) → `TB_PAY_FILE_DATA` → `TB_FIRM_FILE_DATA` (`BulkReceptionService`, raw carry) → `TB_FIRM_FILE_RSLT` (`BulkExecutionResultService`, carried from the matching firm-data row, since `_SI` is not in the parsed result file). DB-only (never in the 1000-byte file). Tests green. **Follow-up (origination):** copying `TRCO_OVRS_INFM.BENE_TOWN_NM → OVRS_PYMN_SUMR_DETL.BNFC_TOWN_NM` at overseas payment SAVE spans the Excel-preview cross-fill (frontend DTO) + manual-create + ERP-import paths — an overseas-payment-feature change outside the batch scope; recommended as a paired full-stack MR (source entity is now mapped so it's ready to consume). |
| M-10/M2 | E2 — validation-count projection | **CONFIRMED NO CHANGE** | `BulkTransferValidator` uses only the 8 projected counts; the 7 "dropped" counts (`citadCnt`, `citadOuterCnt`, `napasOuterCnt`, `swiftCnt`, `succChnlCnt`, `etcCnt`; `failChnlCnt` ≡ mapped `swiftOuterCnt`) are used by legacy ONLY in the operating-hours block, which the approved TO-BE intentionally does not port. No restore needed. |
| M-8 | D1 — 202 `TB_FIRM_TRAN` FX/currency cols | **FIXED** | Mapped `TbFirmTran.withdCcyCd` (`WITHD_CCY_CD` varchar 3, DB-verified) + set `WITHD_CCY_CD` (always) and `DEP_CCY_CD/APL_EXC_RATE/WITHD_AMT/DEP_AMT` (from the pay-target the 204 stamped, null on VND) on the 202 firm-tran insert (`RealtimeDispatchService`). Compiles; realtime dispatch suite green. |
| M-2 | C3 — result-file `substring(FIRM_FILE_NM,3)` match | **FIXED** | New `TbFirmFileMstRepository.findForExecutionResult` JPQL — `substring(firmFileNm,3)=substring(:fileNm,3)` + `VAN_ORG_CD` + `FILE_SEQ` (legacy R006 v20250910) — used by the 01302 handle so the bank's `R_`-prefixed result file matches the sent `S_` file. Callback + payment suites green. *Substring SQL verified by the JPQL query; a DB-integration test is deferred with the other SIT items (no Testcontainers).* |
| M-4 | B3 — 203 inquiry audit rows | **FIXED** | `ResultCheckService.reflectOutcome` now inserts a `TRAN_TP='203'` `TB_FIRM_TRAN` row per inquiry (fresh `firm_seq_no` via `LegacyFunctionRepository.nextTbFirmTranSeq`, `PROC_STAT='6'` C001 → `'0'` U003 with confirmed `curren/amt/owner/remarks/org_proc_tm` / `'3'` U001 on comms-fail), pointing back at the original via `ORG_TRAN_DT`/`ORG_TRAN_SEQ`. That fresh seq is also the inquiry's gateway TNO (closes the X-2 203-envelope coupling). `TbFirmTran` gained the 5 column mappings (`ORG_TRAN_DT/ORG_TRAN_SEQ/ORG_PROC_TM/WDRL_ACCT_RMRK/TAR_ACCT_RMRK`, all pre-existing in V104). `ResultCheckPollTest` rewritten to assert the 203 row + the original 202 update separately; dispatch suite green. |
| M-6 | B4 — result-check `ORG_STAT!='1'` gate | **FIXED** | `ResultCheckService.reflectOutcome` loads `TB_FIRM_CONF` (`TbFirmConfRepository`, legacy R001) and skips the row when `ORG_STAT != '1'` (legacy `continue`), before the 203 insert / gateway call. Missing conf → skip (defensive, one anomalous row must not abort the scan). Uses `conf.VAN_ORG_CD`/`BANK_PRTN_ID` for the inquiry (legacy). Test `orgStatNotAvailable_skipsRow_noInquiry_noWrite` green. |
| M-5 | B5 — per-bank pacing | **FIXED** | New `DispatchPacer` component (Woori `970457`=3000ms, Shinhan/else=100ms, verbatim `WCConst.PER_DELAY_*`); `ResultCheckService.runOnce` calls `dispatchPacer.pause(row.getOutBankCd())` after each aged row. **Scan-loop only** — the event-driven legs (realtime 202/204, bulk 00301, callbacks) fire one request per business event and are unpaced by design (legacy pacing throttled the removed polling loops). Test `perBankPacing_isInvokedForEachRow` green. |
| M-7/F2 | G1/G2 — tx boundaries + idempotent `9→6` claim | **FIXED (G1 callback + G2) · reception per-file boundary intentionally deferred** | **G1:** the 01302 callback audit row is now written via `DispatchApiTranAuditWriter` in a `REQUIRES_NEW` tx (`BulkExecutionResultService.writeApiTran`), so a download/parse failure that rolls back `handle()` no longer erases the audit trail — legacy API2 "commit audit before parse tx" parity. **G2:** new `TbPayTargetRepository.claimForDispatch` (atomic conditional `UPDATE … SET PROC_STAT='6' WHERE …='9'`) wired into `RealtimeDispatchService.dispatch` after the operating-hours gate + 204 pre-step and before the 202 insert — a duplicate/late after-commit hand-off claims 0 rows and no-ops (never double-runs the 202 / a second Woori 204). No general recovery scheduler added (R-1 stays a PO decision). **Deferred (documented):** the `BulkReceptionService.receive` per-split-file commit — correct only via a per-file `REQUIRES_NEW` reload-by-key restructure that would regress the verified C5 branch-split tests; not done under the no-regression constraint. See "Intentionally left unchanged" below. |
| M-3/A4/C5/D5 · M-12 | H2 — alert alignment | **FIXED** | **Failure-only:** `ResultCheckService` alerts only on comms-fail (`처리결과조회 Batch`) + original-tx-fail (`처리결과조회 Batch 원거래오류` + `[ORG Result]`), never on success; `RealtimeDispatchService` 202 alert moved into the FAIL catch and the 204-success alert removed — matching legacy (both alerts live only in the error `else` branch). **VAN org id (M-12):** `OpsAlertService` envelope `ID` is now always `vanOrgCd` (never `partner-id`) — legacy `MSGSendAPIThread:77`. **Env/server (F3/F4):** the legacy `ServerGB` `[ENV][WAS_NO]` marker is re-attached to the alert content's first line and the JSON `mode` uses the legacy `T/U/R` marker, both from new config `wecms.dispatch.alert.env-mode` + `.was-no` (fallback = active profile for local R&D). `OpsAlertServiceTest` updated; dispatch suite green. |
| F1 (VCB) | VCB file-route switch | **FIXED (routing) + FIELD VALUES CONFIRMED (OQ-2 closed 2026-07-21)** | `isVcbFileTransfer = eq(outBankCd, vcbBankCd)` gate in `VendorDispatchExecutor`, `ExpenseDispatchExecutor`, `InternalTransferDispatchExecutor` → ALL VCB (970436, **VND+USD**) routes to the File path, never the real-time API (Mr. Jung 2026-07-16; legacy r36803, PymnHelper). VCB→other-bank reject (`WBE2088`) is **USD-only** (legacy pymn_0001:2387-2388) so VCB VND→other is allowed. **VCB VND field values now CONFIRMED from legacy `PymnHelper`** (not invented): VCB VND→VCB `BANK_CHNL_DIV='T'` (:2572); VCB VND→Other `BANK_CHNL_DIV='O'` + `ANT_DIV_CD`=uploaded NAPAS/CITAD (`copyFrom`, :2573/:2840); VCB USD→VCB `T`+`ANT_DIV_CD=null` (:2574). Vendor+Expense seed `ANT_DIV_CD` from the uploaded line before the USD override; Internal Transfer got a targeted VCB-VND branch. Tests added in all 3 executor test classes (VCB VND→Other carries uploaded code + `O`; VCB VND→VCB `T`; VCB USD override→null). **Remaining: a one-record SIT acceptance capture only — not a routing/design blocker.** |
| R-1 | stranded-'9' recovery | OUT OF SCOPE (PO decision) | — |

### 0.2 Post-implementation final audit (2026-07-21)

Full re-audit after all 7 HIGH + 12 MEDIUM landed. Two confirmed items were found and fixed in the pass;
one pre-existing residual remains. Full backend suite green (**1847 tests, 0 failures, 20 skipped**).

- **FIXED — B5 pacing branch accuracy.** The initial B5 pass paced after *every* aged row; legacy
  `PROCESSING_RESULT_CHECK_BATCH` sleeps on the ORG_STAT-skip (:108), comms-fail (:234) and
  original-fail (:393) branches but **not** after a clean success (6-1 has no `Thread.sleep`).
  `ResultCheckService.reflectOutcome` now returns a `shouldPace` flag so `runOnce` paces exactly on
  legacy's branches (also reduces the ShedLock `PT2M` pressure on high Woori volume).
- **FIXED — result-check original-row `RS_CD`/`RS_MSG` mapping.** Legacy `TB_FIRM_TRAN_U004` sets
  `RS_CD=org_resp_cd` / `RS_MSG=org_resp_msg` on **both** the success (6-1) and fail (6-2) branches;
  the refactor set them only on failure, leaving the original 202 row's `RS_CD` null on a 203-confirmed
  success (should be `'0000'`). Now set unconditionally.
- **RESIDUAL (pre-existing, not a this-pass regression) — result-check `U004`+`U001` atomicity.**
  Legacy wraps the original `TB_FIRM_TRAN_U004` + `TB_PAY_TARGET_U001` updates in one
  `beginTransaction/commit` (:298-333). The refactor `runOnce`/`reflectOutcome` was non-transactional,
  so those were two independent auto-commit `save()`s. **RESOLVED 2026-07-21 (Item 1)** — see §0.3.

### 0.3 Remaining-items implementation pass (2026-07-21, SIT/UAT-available)

All code-level items that can be safely completed now are implemented + tested (full backend suite
**1862 tests, 0 failures, 20 skipped**). Runtime config + external confirmations are in
[../deployment/wecms-batch-activation-and-external-confirmations.md](../deployment/wecms-batch-activation-and-external-confirmations.md).

| Item | Status | Summary |
|---|---|---|
| **1 — Result-check U004+U001 atomicity** | **FIXED** | New `DispatchResultTxService.finalizeOriginalAndPayTarget` (`REQUIRES_NEW`) reloads the original firm-tran + pay-target by key and updates both in one transaction; either failure rolls back both. `ResultCheckService.reflectOutcome` delegates to it per row; the 203 audit row is still committed separately (legacy parity). Tests: `DispatchResultTxServiceTest` (both-succeed / firm-fail / pay-fail / missing-firm / missing-pay) + `ResultCheckPollTest.oneFailedRow_doesNotBlockLaterRows`. |
| **2 — BATCH1 per-split-file tx boundary** | **FIXED** | `BulkReceptionService.receive` is no longer one `@Transactional`; each split file is built + uploaded + dispatched inside its own `REQUIRES_NEW` `TransactionTemplate` (`runInNewTransaction`). A later split's unexpected throw can no longer roll back already-committed + already-dispatched earlier files. C5 split topology + R004 sibling fail-stop preserved. Tests: `BulkReceptionReceiveTest.laterSplitFailure_stopsBatch_earlierFileStandsCommitted` + `unexpectedThrowInLaterSplit_propagates_earlierFileAlreadySaved` (+ existing split/duplicate-no-op tests). |
| **3 — Overseas town-name origination** | **FIXED** | The full chain `TRCO_OVRS_INFM.BENE_TOWN_NM → OVRS_PYMN_SUMR_DETL.BNFC_TOWN_NM → TB_PAY_FILE_DATA/FIRM_FILE_DATA/FIRM_FILE_RSLT.RCPT_USE_ADDR_SI` was already wired for the Excel/preview/contact-selection paths (`OverseasPaymentExcelService` line 432/750) and correctly empty for the contact-less ERP path. The real gap — a **150-char source → 35-char target** length mismatch with no guard at `OverseasDispatchExecutor` — is fixed with a `capToColumnWidth(..., 35)` cap. Tests added: long-town cap-to-35 + exactly-35 passthrough (existing null/empty/≤35 tests unchanged). |
| **4 — R-1 stranded-record recovery** | **IMPLEMENTED, DISABLED** | New `StrandedRecoveryService` + `StrandedRecoveryJob` (`@Scheduled` cron `"-"` + `@SchedulerLock`). Re-drives aged `'9'` pay-targets (via the G2 atomic-claim dispatch path) + `'9'` pay-files (WAIT-gated receive), delegates firm-tran `'6'` to the result-check; grace window (`grace-minutes`, default 30). **Double-gated OFF** (`recovery.enabled=false` + cron `"-"`) — kept disabled until the legacy-JEX coexistence rule is resolved (rows are not distinguishable across systems). Tests: `StrandedRecoveryServiceTest`. |

---

## 1. Verified-matching (no action — recorded for confidence)

| Area | Result |
|---|---|
| Fixed-length file layout (Header/Data/Trailer, all fields, 998+CRLF=1000) | **Byte-identical.** Incl. dated changes: `receiptBankCountryCode`+`remittanceCurrencyCode` @810/812 (2025.07.07); `feeBrdnClsCd` @815 **before** `rcptUseAddr` @818, 183-byte filler removed (2025.10.29). |
| Crypto envelope | AES/CBC/PKCS5Padding, 16-byte zero IV, UTF-8 key verbatim, `EV=urlEncode(B64(AES(rqDtime14+json)))`, `VV=urlEncode(B64(HMAC-SHA256(json)))`, 14-char prefix strip on decrypt — **identical**. |
| Key generation | `TRAN_SEQ`/`FILE_SEQ_NO` funnel through `wb_get_next_val` → same global `firm_seq_no`/`firm_file_seq_no` sequences legacy `Sequence` draws — **identical value semantics**. |
| Constants & enums | All 34 `WCConst` (PROC_ST/PROC_STP/VN_API/TRAN_TP/CD_*/MSG_*/bank codes/delays/org-status) → `ProcStat`/`ProcStep`/`ApiTranTp`/`TranTp`/`FirmBankingResultCd`/`BulkTransferConstants` — **no value drift**. |
| WeCMS→Batch hand-off | Upstream writers stamp `PROC_STAT='9'` on `TB_PAY_TARGET` + `TB_PAY_FILE_MST`; both legacy batch and refactor read exactly `'9'` — **no hand-off mismatch**. |
| Validation ruleset | Overseas (`FUND_TP='S'`) + domestic-FX (EBANK 970424 / VCB 970457) + Woori NAPAS/exchange prohibition + ccy-mix checks — **match**, incl. the 20251006 fund-type branch. |
| Operating-hours gate | `TB_BATCH_SCHEDULE_INFO`/`_DTL`, `EXE_DIV` Y/E/N → DISPATCH/CUTOFF/SKIP, missing-row semantics — **match**. |
| 2026.02.13 Woori WGVN_EFS_204 FX pre-inquiry | **All 5 sub-steps present** (Woori+non-VND gate, independent TRAN_SEQ, `TRAN_TP='204'` row, single 204 before 202, success `_U005`+`_U002` / fail `'3'`+skip-202). |
| Branch division, filename `S_{VAN}_{TYPE}_{seq3}_{date}`, card flag, tone mapping, 180-byte address cap, happy-path status transitions | **match**. |

### Dated-update checklist (the "many updates and bug fixes over time")

| Date | Change | Status |
|---|---|---|
| 2025.07.07 | Data record: `receiptBankCountryCode` + `remittanceCurrencyCode` | ✅ Present (byte-exact) |
| 20250708 | Shinhan overseas `data_dcm_amt` decimal-amount field (301 + 302) | ✅ Present — **but computed differently, see H-6** |
| 2025.09.10 | Per-file / sibling fail-stop (abort remaining files when a sibling failed) | ⚠️ **Partial** — intra-invocation split-loop break present; cross-callback `TB_PAY_FILE_MST='3'` guard missing (see H-3) |
| 20251006 | Overseas foreign-currency fund types 9/0/1 validation branch | ✅ Present |
| 20251029 | `feeBrdnClsCd` added + 183-byte filler removed | ✅ Present (byte-exact) |
| 20251105 | `TB_FIRM_CONF` lookup moved before validation (for alert msg) | ✅ Present |
| 2026.02.13 | Woori WGVN_EFS_204 FX pre-inquiry | ✅ Present (5/5 sub-steps) |

---

## 2. Cross-cutting / architectural findings

### R-1 (RELIABILITY ENHANCEMENT — **not** a parity gap) — `PROC_STAT='9'` can strand with no recovery
> **Reclassified 2026-07-21** after PO review. The polling→event-driven change is the **approved TO-BE** (BATCH1→afterCommit, BATCH2→inline-in-01301, NAPAS_REAL_TIME→`RealtimeDispatchService`, only PROCESSING_RESULT_CHECK remains scheduled). The removal of the polling loops is **intentional and correct** — it is not measured as a parity defect. What follows is a reliability property of the approved design, matching the TO-BE doc's own open items **R5** ("confirm callback delivery SLAs so the 203 sweep stays a backstop") and **K9** (state-machine note: `stays 1 — no auto-recovery`).

**Question asked:** can a record realistically strand permanently at `PROC_STAT='9'`, and does the existing architecture already recover it?

**Verified — the only scheduled recovery does NOT cover `'9'`.** `ResultCheckService.runOnce()` selects **only** `TB_FIRM_TRAN` rows with `TRAN_TP='202' AND PROC_STAT='6'` (`ResultCheckService.java:159-163`) — i.e. the *post-dispatch confirmation* phase of the real-time path. It never scans `PROC_STAT='9'` on `TB_PAY_TARGET` or `TB_PAY_FILE_MST`, and never touches the bulk-file tables at all. So a row that never advanced past `'9'` is invisible to the one scheduler that exists.

**Code-evidenced stranding scenarios (no recovery today):**
1. **Woori/non-VND 204 FX failure** — `RealtimeDispatchService.dispatch:204-212` returns early on a failed 204, leaving `TB_PAY_TARGET` at `'9'` and writing only a `TRAN_TP='204'` firm-tran row. The code comment says "left at WAIT for a later re-dispatch" — **but no re-dispatch exists**, and result-check scans `'202'` only → permanent strand.
2. **Lost / failed after-commit event** — the hook fires exactly once, synchronously, after commit. A JVM crash/restart/OOM in the commit→afterCommit window, or any setup-phase throw before the `'202'/'6'` firm-tran row persists, leaves `TB_PAY_TARGET` at `'9'` with no `'202'` row → invisible to result-check.
3. **Flag flipped ON after rows already exist** — rows committed at `'9'` while `realtime-dispatch`/`auto-receive` were OFF are never revisited (the registrar was a no-op at their commit time).
4. **Bulk-file path has no scheduled recovery at all** — a `TB_PAY_FILE_MST` at `'9'`, or a `TB_FIRM_FILE_MST` stuck at step `0`/`1` (incl. the documented K9 "00302 fail → stays 1"), is never swept.

**Recoverable (NOT stranded):** real-time rows that *did* reach `TB_FIRM_TRAN '202'/'6'` — result-check resolves these via 203 and they self-heal once EFS/key are available.

- **Files:** `service/RealtimeDispatchService.java:204-212`, `service/ResultCheckService.java:159-163`, `service/DispatchAutoReceiveRegistrar.java`, `service/RealtimeDispatchRegistrar.java`.
- **Disposition:** Genuine **reliability enhancement**, warranted, but **not** a legacy-parity issue and **not** urgent while dispatch is OFF. The *shape* of the recovery (bounded sweep vs refactor-origin marker vs an ops runbook) is a **PO/Mr. Kang decision** — deliberately **not designed or implemented here** pending that decision. Whatever shape is chosen must respect the live-JEX coexistence rule (a blind `'9'` sweep on the shared DB would double-process JEX-originated rows) and add an atomic `'9'→'6'` claim + grace window so it never races the happy-path event.

### X-2 (HIGH) — Gateway `TNO` is a random UUID, not `TRAN_SEQ`
- **Legacy:** the EFS envelope `TNO` = the allocated `TB_FIRM_TRAN.TRAN_SEQ` / `TB_FIRM_BAT_API_TRAN.TRAN_SEQ` (1:1: the number stored == the number the bank sees). The 202→203 result-check recovery reconstructs the bank's `org_tno` **from `TRAN_SEQ`**.
- **Refactor:** `TNO` = `rqDtime + UUID` (`BulkReceptionService.newTno`, `RealtimeTransferService`/`ResultCheckService.newTno:278`), while the stored `TRAN_SEQ` is a separate `firm_seq_no` value. The 203 recovery still builds `orgTno` from `row.getTranSeq()` (`ResultCheckService:196`) — a value **the bank never received**.
- **Impact:** (1) bank-side reconciliation by TNO is broken across every dispatch; (2) **the 202→203 result-check recovery is internally inconsistent and cannot match at the bank** — it will always get "no original transaction." (The alert even prints `TNO`/`ORG TNO` as `tranSeq`, confirming the intended contract.)
- **Files:** `service/BulkReceptionService.java` (newTno/F4), `service/ResultCheckService.java:196,278`, `service/RealtimeTransferService.java`, callback `BulkExecutionResultService` 01302 audit.
- **Fix:** send the allocated `TRAN_SEQ` as the envelope `TNO` everywhere (thread it through `*Command`), matching legacy.

---

## 3. HIGH findings (per area)

> **Reading this table (note added 2026-07-30).** The rows below record each finding **as of the audit
> date (2026-07-21)** and were not rewritten as fixes landed — the status of record is the remediation
> table in §1, where H-1, H-2, H-3, H-4, H-5, H-6 and X-2 are all marked **FIXED**. A row here that
> describes present-tense broken behaviour is therefore not evidence the defect is still open; check §1
> first. H-4 has been annotated inline because its "Refactor" cell described code that no longer exists
> and was being read as an open item.

| ID | Area | Legacy (file:line) | Refactor (file:line) | Impact | Recommended fix |
|---|---|---|---|---|---|
| **H-1** | Result-check | `PROCESSING_RESULT_CHECK_BATCH.java:200,249-253` — `RC=0001` ("no original txn", `CD_NO_FOUND`) accepted as **definitive**; original → FAIL `'3'` | `service/ResultCheckService.java:116-121` — any `rc!='0000'` throws `WBE_EFS_GATEWAY_ERROR`; `FirmBankingResultCd.isSuccess` matches only `0000` | `0001` treated as transient → row left `'6'` and re-inquired **forever**, never finalized | Treat `0001` as a valid definitive "not found" → let `reflectOutcome` mark `PROC_STAT='3'` |
| **H-2** | Result-check / API1 callback | Inbound response = encrypted EFS envelope `ID/RQ_DTIME/TNO/EV/VV/EM/VM/RC/RM` (`FILE_DEPOSIT_TRANSFER_API1_act.jsp:409-423`, API2 `:448-514`) | Callbacks return WeCMS `ApiResponse.ok("received",null)` (`callback/controller/BulkReceptionResultController.java:36-38`, `BulkExecutionResultController.java:31-33`) | The EFS gateway expects the encrypted RC/RM ack envelope; the refactor's `{status,data}` JSON is not a valid acknowledgement → real callback integration fails | Build/return the encrypted EFS response envelope (echo fields + `encryptEv`/`buildVv` + RC/RM) from the callback service. Part of the documented B7/R-03 callback-hardening item. |
| **H-3** | BATCH2 / API1 callback | 2025.09.10 change: before executing each split file, `TB_PAY_FILE_MST_R004` counts `PROC_STAT='3'`; if any sibling failed → mark firm file `G998` + skip (`FILE_DEPOSIT_TRANSFER_BATCH2.java:67-104`, `API1_act.jsp:173-242`) | `BulkReceptionResultService` never queries `TB_PAY_FILE_MST`; `issueExecutionInline:221-265` loops siblings independently, a caught `BusinessException` does **not** break the loop | The cross-sibling fail-stop is lost → **partial money movement legacy explicitly prevents** (one sibling fails, peers still dispatched). The 2025.09.10 fix is only partially reflected. | Inject `TbPayFileMstRepository`; before issuing 00302 count `PROC_STAT='3'` for `(tranDt,bizKey,fileSeqNo)`, short-circuit to the `G998` fail path and abort the sibling loop |
| **H-4** ✅ **CLOSED** | NAPAS realtime | G999 / no-response → `TB_PAY_TARGET` `PROC_STAT='6'` (in-progress), defer to result-check batch (`NAPAS_REAL_TIME_BATCH.java:572-591`) | ~~`RealtimeTransferService:172-182` does a **synchronous 203** then `applyOutcome`/`applyFail` finalizes to `'0'/'3'`~~ — **no longer the code.** The synchronous inline 203 was removed; `RealtimeTransferService` returns `RealtimeTransferResult.ofPending()` on all three no-definitive-response shapes — `G999` (`:208-214`), a null response (`:185-189`) and a `VV`-verify failure (`:196-200`) — and `RealtimeDispatchService:426` routes `pending()` to `applyPending`, which writes `PROC_STAT='6'` on **both** the firm-tran and the pay-target and finalizes neither | ~~On G999 the refactor may write FAIL `'3'` prematurely~~ — cannot happen: no branch reachable from G999 / no-response / VV-fail writes `'3'`. Only a definite non-`0000`, non-`G999` gateway business error throws, which is legacy's own FAIL condition | **Done** — matches legacy `:576` (`PROC_ST_REQ` then `continue`). Re-verified against the legacy source and the current code on **2026-07-30**; the row above was stale, not open. Status of record is the remediation table in §1 (H-4/D2 = FIXED). Tests: `RealtimeTransferServiceTest.g999_returns_pending_and_does_no_synchronous_203`, `noResponse_returnsPending`, `success_rc_0000_withInvalidVv_returnsPending`; `RealtimeDispatchServiceTest.pendingResult_leavesBothRowsAt6_forResultCheck` |
| **H-5** | NAPAS realtime | `SecurityUtil.VerifyMac(EV,VV)` before every 202/203/204 response decrypt; mismatch → `G003` (`NAPAS_REAL_TIME_BATCH.java:369-376,597-605`) | `RealtimeTransferService:165,282` + `ResultCheckService:123` call `decryptEv` only; `EfsCryptoService.verifyVv` exists but is **never invoked on outbound responses** (it **is** used on inbound callbacks) | Outbound response integrity/authenticity is not verified — a tampered/corrupt `EV` is decrypted and trusted | Call `verifyVv(ev,vv,apiKey)` before every outbound response decrypt (202/203/204); throw on mismatch |
| **H-6** | Reception/execution (cross) | `data_dcm_amt` = literal fractional substring of FILE_AMT (`StrUtil.BigDecPaser:98-139`): `".00"→"00"`, no-decimal→`"000"` | `splitAmount` = `(amt−int).movePointRight(2).toBigInteger().toString()` (`BulkReceptionService.java:632`; same in realtime): `.00→"0"`, `.05→"5"`, VND integer→`"0"` | The money decimal field sent to the bank **differs from the legacy-proven output on essentially every request** (VND: `"000"` vs `"0"`). Impact depends on gateway tolerance — but the refactor deviates from the accepted baseline | Reproduce `BigDecPaser` semantics exactly (parse the FILE_AMT string; zero-pad decimal portion; `"000"` whole-number form). Confirm the expected width against the gateway spec |

*(X-2 above is also HIGH — 7 HIGH parity findings total. R-1 is a reliability enhancement, counted separately, not a parity gap.)*

---

## 4. MEDIUM findings

| ID | Area | Legacy | Refactor | Impact | Fix |
|---|---|---|---|---|---|
| M-1 | API1 callback | Reception failure (result≠BT00, or count/amount mismatch) → `TB_PAY_FILE_MST` `PROC_STAT='3'` (`API1_act.jsp:297-310,369-382`) | `BulkReceptionResultService.failFile:267-278` updates only `TB_FIRM_FILE_MST` | Payment summary ledger never reflects the reception failure | Also update matching `TB_PAY_FILE_MST` to `'3'` |
| M-2 | Callbacks | `R006`/`TB_FIRM_FILE_DATA_U001` match on `substring(FIRM_FILE_NM,3)` (+ VAN_ORG_CD + FILE_SEQ) — first 2 chars ignored | Exact-`FIRM_FILE_NM` finder (`BulkExecutionResultService:150-153`) | If the bank returns the result file with a different 2-char prefix, the lookup finds nothing → result silently dropped, file stranded at `'2'` | Match from char 3 onward + add VAN_ORG_CD/FILE_SEQ predicates |
| M-3 | Alerts (both directions) | `MsgSendUtil.SendMsg` fires **only on failure**; callbacks alert in `finally` on any `RC≠0000` | Callbacks send **no** ops alert on verify/download/parse/reception failures (A4/C5); realtime + result-check send alerts on **every** outcome incl. success (D5/B5) | Ops under-alerted on real failures, over-alerted (noise) on successes | Alert only on FAIL/CUTOFF; inject `OpsAlertService` into the callback services |
| M-4 | Result-check | New `TB_FIRM_TRAN` `TRAN_TP='203'` inquiry row per check, carrying confirmed `curren_cd/amt/finc_mda_no/owner_nm/rmrk` (`:117-139,270-287`) | No 203 row created; writes outcome onto the original 202 row; 203 detail fields discarded (`ResultCheckService:193-233`) | Audit/data-migration parity loss (JEX has one 203 ledger row per inquiry; refactor has none) | Insert a `TRAN_TP='203'` row via the funnel and persist the 203 detail |
| M-5 | Result-check | Per-bank pacing `Thread.sleep` (Woori 3000ms, else 100ms) between requests (`:82-89,183,393`) | No throttle — 203/202/204 fired back-to-back (`ResultCheckService:170-178`, `RealtimeDispatchRegistrar:64-72`) | Gateway rate-limit/rejection risk, especially Woori | Reintroduce per-bank delay keyed on `OUT_BANK_CD` |
| M-6 | Result-check | `TB_FIRM_CONF` org-state gate: skip when `ORG_STAT≠'1'` (`:92-110`) | Missing — every aged row inquired regardless of connecting-org state | Suspended/terminated orgs still hit with live 203 calls | Load `TB_FIRM_CONF` per row, skip when `ORG_STAT≠'1'` |
| M-7 | Transactions | BATCH1 commits **per split file** before SFTP/301; callbacks commit the 01302 audit **before** the parse tx | Whole `receive()` (all split files + SFTP + EFS) is one `@Transactional` (`BulkReceptionService:175`); callback handler one tx (`BulkExecutionResultService:115`) | An unexpected throw on a later split file rolls back already-SFTP'd/dispatched siblings; a parse failure rolls back the 01302 audit row (legacy keeps it) | Commit per file (or move SFTP/EFS outside the per-file tx); write callback audit in `REQUIRES_NEW` |
| M-8 | NAPAS realtime | 202 `TB_FIRM_TRAN` insert writes `WITHD_CCY_CD` (always) + `DEP_CCY_CD/APL_EXC_RATE/WITHD_AMT/DEP_AMT` (Woori path) (`:512-516`) | 202 `firmTran` insert sets none of these five; FX cols only on the separate 204 row + `TB_PAY_TARGET` (`RealtimeDispatchService:217-237`) | 202 audit row loses withdrawal/deposit-currency + FX columns legacy always records | Set `withdCcyCd` always + Woori FX cols from the 204 result on the 202 row |
| M-9 | Support (tone) | `getStrByteMaxLen(addr,180)` **then** `replaceTone` (`BATCH1:638,665-667`) | `byteTruncate(replaceTone(addr),180)` — reversed (`BulkReceptionService:410,432`) | For a raw VN address >180 bytes, refactor keeps more characters → non-byte-identical `rcptUseAddr` | Match legacy order: `replaceTone(byteTruncate(addr,180))` |
| M-10 | Data model | `TB_PAY_FILE_DATA_R005` outputs 15 counts | `aggregateValidationCounts`/`BulkValidationCounts` expose 8 (drops `citadCnt/citadOuterCnt/napasOuterCnt/swiftCnt/succChnlCnt/failChnlCnt/etcCnt`) | If `BulkTransferValidator` ever needs the dropped counts, validation diverges | Confirm the validator doesn't need them; else restore the SELECT + projection |
| M-11 | Data model | Upstream writes `TB_PAY_FILE_DATA.RCPT_USE_ADDR_SI` (varchar 35) | Column **not mapped** on `TbPayFileData` entity | Refactor never populates it; structured-address data lost on refactor-written rows (nullable → no insert failure) | Add the `@Column` + populate it in the payment writer |
| M-12 | Support (alert) | Envelope `ID` = `vanOrgCd` verbatim (`MSGSendAPIThread.java:77`) | `id = partnerId ?: vanOrgCd` (`OpsAlertService:117-118`) | When `partner-id` is configured, the alert-channel `ID` is not what the gateway expects | Send `vanOrgCd` as `ID` on the alert path (or confirm gateway accepts partnerId) |

---

## 5. LOW / INFO (recorded, not blocking)

- **SEQ windowing:** refactor partitions split files by list index (`subList`) after sort; legacy by SEQ-value range (`START_SEQ/END_SEQ`). Identical only if SEQ is dense `1..N` — confirm. (BATCH1 F7 / data L1)
- **01302 audit sources:** refactor stamps `TRAN_DT` from filename date + `TRAN_SEQ` from a fresh sequence; legacy uses processing date + the callback's `TNO`. (BATCH2 D6/D7)
- **`BAL_AMT` scale:** refactor stores combined decimal; legacy stored integer part only. (NAPAS D10)
- **204 no-response state:** refactor writes `'3'`; legacy leaves the 204 row `'6'`. (NAPAS D8)
- **`FILE_TYPE` trim:** refactor trims; legacy uses raw. Confirm column is VARCHAR not CHAR. (BATCH1 F8)
- **Alert content:** refactor drops the `[ENV][WAS_NO]` "ServerGB" suffix (support F3) and uses profile name for `mode` instead of `T/U/R` (support F4).
- **`deploy_check.act`:** no 1:1 refactor endpoint; liveness covered by `/actuator/health`. External tools calling `deploy_check.act` by path will 404.
- **Sequence bounds (verify):** confirm the refactor `firm_seq_no` (MAXVALUE 9999999999 CYCLE) / `firm_file_seq_no` (MAXVALUE 9999999 CYCLE) carry the legacy MAXVALUE+CYCLE so values don't exceed `TRAN_SEQ`(10)/`FILE_SEQ_NO`(7) widths. (support F11)
- **Stale Javadocs:** `BulkReceptionService` ("validation/operating-hours/alerting deferred" — actually wired), `BulkReceptionResultController` ("SKELETON, no processing"), `ResultCheckService`/`ResultCheckJob` ("no DB writes" — it does write), `DispatchKeyGenerator` ("zero-padded, per-(BIZ_KEY,TRAN_DT)" — actually global raw). Correct to prevent future misreading.

---

## 6. Recommended fix priority (pre-activation)

1. **Must fix before enabling dispatch:** X-2 (TNO), H-1 (0001 loop), H-2 (callback ack envelope), H-3 (sibling fail-stop), H-4 (G999 premature fail), H-5 (response VV), H-6 (decimal amount) — these are correctness/money/integration breaks that would surface on the first real SIT round-trip.
2. **PO/Kang decision (reliability, not parity):** R-1 — choose the recovery shape for stranded `'9'` (+ file-path `'6'`/`'1'`) rows: bounded sweep, refactor-origin marker, or an ops runbook. Respect live-JEX coexistence; add atomic `'9'→'6'` claim + grace window if a sweep is chosen. Not urgent while dispatch is OFF.
3. **Fix in the same activation sprint:** M-1…M-8 (failure-path + audit parity), M-9 (tone order) if long VN addresses are in scope.
4. **Confirm/verify:** M-10, M-11, M-12, and the LOW verification items.
5. **Cleanup:** stale Javadocs.

---

## 7. Confidence & method notes

- Byte layout, crypto, sequence funnel, and the scheduling/trigger model were verified **directly against source** (not only via the sub-audits).
- H-5 (response VV) and X-2 (TNO) were re-verified by hand: `verifyVv` is called in the three inbound-callback/recipient services but not on outbound 202/203/204; `ResultCheckService.newTno` is UUID-based while the 203 recovery uses `getTranSeq()`.
- H-6 (decimal split) verified by reading both `StrUtil.BigDecPaser` and `BulkReceptionService.splitAmount`.
- Severities reconcile minor per-area disagreements (e.g. the decimal-split bug was HIGH in the reception audit, LOW in others) toward the higher rating where a money or integration contract is affected.

---

## Addendum — 2026-07-22 legacy-output-parity implementation pass

The observable-output parity findings (5 payment types × 11 dispatch artifacts) were derived,
source-proven, and then **aligned to legacy** on 2026-07-22. Full record + per-finding commits +
final verdict: [legacy-vs-refactor-output-parity-audit.md](legacy-vs-refactor-output-parity-audit.md) §7.

- **12 findings aligned or implemented** (B raw owner-nm · C BAL_AMT integer-only · D 204 no-response→'6' · E 01302
  audit TRAN_DT=processing date · F split by SEQ value-range · G raw FILE_TYPE · H Vendor/Expense
  WITHD/DEP_CCY_CD null · I IT remarks=TRAN_USAG · J Payroll same-bank group+guard+VCB blocks ·
  K API-rail TRAN_DT/REG_TM=UTC · L Overseas M-mode dispatch · M UAT Shinhan/Woori override).
- **A0 was a FALSE finding** — legacy fills `TB_PAY_FILE_MST.PROC_DTTM` via its DAO (0 nulls in 136 live
  rows) + a NOT-NULL `_his` trigger; the refactor already matched. Not changed.
- **N** (`data_dcm_amt`) already matched.
- Full backend suite after the pass: **1904 tests, 0 failures, 20 skipped**. Dispatch stays inert (flags OFF).
- **Activation coupling (K):** the 202 firm-tran now carries a UTC `TRAN_DT`, but `ResultCheckService`
  still selects aged rows by ICT-today — make it UTC-aware / RQ_DTTM-based before enabling result-check
  (ties to the day-boundary item). Runtime-only residuals stay in the output-parity audit §5.
