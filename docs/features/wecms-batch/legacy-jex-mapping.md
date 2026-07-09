# Legacy JEX → Spring Mapping

> Part of the [WeCMS Batch](README.md) feature set. **This is the only migration-traceability document in the set** — the feature, scheduler, schema, deployment, and troubleshooting docs describe current behaviour; this one records where each legacy JEX component went and why.
>
> **Status:** source-verified 2026-06-24. The legacy batch programs lived in a **separate JEX batch deployment** and are not in this workspace; references below are the surviving artifacts + reconstructed AS-IS from PO/Mr. Do screenshots and the legacy entity set.

---

## 1. Component mapping

| Legacy component | Legacy path / artifact | Spring component | Spring class path (`com.kosign.wecms.domain.payment.dispatch.…`) | Status | Notes |
|---|---|---|---|---|---|
| `NAPAS_REAL_TIME_BATCH` | separate JEX batch deployment | `RealtimeDispatchService` (+ `RealtimeDispatchRegistrar`, `RealtimeTransferService`) | `service/RealtimeDispatchService` | ✅ Migrated | Polling loop → `afterCommit` hook. 204 FX persistence implemented (`runFxInquiry`). |
| `FILE_DEPOSIT_TRANSFER_BATCH1` | separate JEX batch deployment | `BulkReceptionService` (+ `DispatchAutoReceiveRegistrar`) | `service/BulkReceptionService` | ✅ Migrated | File build + SFTP + EFS 301. C5 branch-split ported. Operating-hours + channel-mix gates deferred. |
| `FILE_DEPOSIT_TRANSFER_BATCH2` | separate JEX batch deployment | `BulkReceptionResultService.issueExecutionInline()` | `callback/service/BulkReceptionResultService` | ✅ Absorbed | No standalone second batch — EFS 302 issued inline from the 01301 callback (architecture decision K9). |
| `PROCESSING_RESULT_CHECK_BATCH` | separate JEX batch deployment | `ResultCheckJob` → `ResultCheckService` | `scheduler/ResultCheckJob`, `service/ResultCheckService` | ✅ Migrated | The only surviving scheduler. Cron default `"-"`. Day-boundary edge case open. |
| `WB_00000100` | `WECMS DEV/WECMS_ETC/xml/service/IMO/IMO.WB_00000100.xml`; `WECMS DEV/WECMS/src/com/firm/FIRM_PROC.java` | `FirmBankingInquiryService` → `DepositorInquiryService` / `RecipientLookupService` | `service/FirmBankingInquiryService`, `recipient/{DepositorInquiryService,RecipientLookupService,FirmTranInquiryAuditService}` | ✅ Migrated | Synchronous service, not a batch. EFS 201. Transport socket → REST. |
| `FILE_DEPOSIT_TRANSFER_API1_act.jsp` | JEX action JSP (01301 reception callback) | `BulkReceptionResultController` | `callback/controller/BulkReceptionResultController` | ✅ Migrated | `POST /api/v1/payment/dispatch/callbacks/bulk-reception-result`. |
| `FILE_DEPOSIT_TRANSFER_API2_act.jsp` | JEX action JSP (01302 execution callback) | `BulkExecutionResultController` | `callback/controller/BulkExecutionResultController` | ✅ Migrated | `POST /api/v1/payment/dispatch/callbacks/bulk-execution-result`. |

## 2. Infrastructure / helper mapping

| Legacy component | Legacy artifact | Spring component | Spring class path | Status | Notes |
|---|---|---|---|---|---|
| Gateway call (socket) | JEX socket client | `EfsApiClient` → `RestClientEfsApiClient` / `MockEfsApiClient` | `van/client/EfsApiClient` (+ impls), `van/client/EfsProperties` | ✅ Migrated | Transport socket → `RestClient`. Legs `EFS_201..EFS_302` → `/WGVN_EFS_*.jct`. Mock throws when `efs.enabled=false`. |
| Request encrypt / sign | JEX crypto | `EfsCryptoService` | `van/client/EfsCryptoService` | ✅ Migrated | AES-256/CBC/PKCS5 EV + HMAC-SHA256 VV + constant-time verify. |
| `util.SftpUtil` | JEX SFTP helper (60 s) | `SftpClient` → `JschSftpClient` / `MockSftpClient` | `sftp/SftpClient` (+ impls), `sftp/SftpProperties` | ✅ Migrated | JSch (`com.github.mwiede:jsch`). `connect-timeout-ms=60000`. Mock throws when `sftp.enabled=false`. |
| `Deposit_Header/Data/Trailer` builders | JEX file builders | `FixedLengthCodec` | `file/FixedLengthCodec` | ✅ Migrated | 1000-byte H/D/T (`CONTENT_LEN=998` + CRLF), UTF-8, byte-offset fields. |
| `consts.WCConst` result codes | JEX constants | `FirmBankingResultCd` | `constant/FirmBankingResultCd` | ✅ Migrated | `0000`/`0001`/`9999`/`G001..G004`/`G998`/`G999`. |
| Status codes (PROC_STAT/STEP/TRAN_TP) | JEX enums | `ProcStat`, `ProcStep`, `TranTp` | `enums/*` | ✅ Migrated | Same legacy code values. |
| VAN key lookup | JEX config read | `VanApiKeyService` (+ `TbVanApiInfo`) | `van/…`, `entity/TbVanApiInfo` | ✅ Migrated | Per `VAN_ORG_CD` from `TB_VAN_API_INFO`; throws until seeded (B2). |
| Key generation (`SeqHelper`) | JEX `wb_get_next_val` | `DispatchKeyGenerator` → `LegacyFunctionRepository` | `service/DispatchKeyGenerator`, `common/legacy/LegacyFunctionRepository` | ✅ Migrated | Single funnel to `wb_get_next_val`. |
| Inbound IP guard | (none in legacy) | `CallbackIpWhitelistFilter` + `CallbackProperties` | `callback/CallbackIpWhitelistFilter`, `callback/CallbackProperties` | ➕ Added | Refactor addition; protects the unauthenticated callbacks (pre-deploy gate R-03). |
| `PymnHelper` UAT bank-code override | `PymnHelper.java:916-946` | — | — | ⏳ Deferred | UAT Shinhan(`970424`)/Woori(`970457`) override not ported — **mandatory before UAT/PROD on those banks**. |
| Slack `MsgSendUtil` alerting | JEX alert util | — | — | ⏳ Not ported | Logging-only; porting is an open PO decision. |
| Operating-hours gate | `TB_BATCH_SCHEDULE_*` driven | `TbBatchScheduleInfo/Dtl` (+ repos) — entities only | `entity/TbBatchSchedule*`, `repository/TbBatchSchedule*Repository` | ⏳ Deferred | Entities + repositories mapped; the window check is **not wired** into dispatch. |
| Channel-mix validation | BATCH1 pre-flight | — | — | ⏳ Deferred | EBANK/VCB/CITAD/NAPAS rules not ported. |
| Legacy `*_HIS` audit triggers | DEV BEFORE/AFTER triggers | `AUDIT_LOGS_INFM` + `TB_FIRM_BAT_API_TRAN` | `common/auditlog/…`, `entity/TbFirmBatApiTran` | 🔁 Replaced | Trigger DDL not ported; refactor audit model used instead. |

## 3. IDO query mapping

| Legacy IDO | Behaviour | Spring repository method |
|---|---|---|
| `TB_FIRM_TRAN_R001` | select no-response 202s (`TRAN_TP='202' AND PROC_STAT='6'`, ~2 min old) | `TbFirmTranRepository.findByTranTpAndProcStat('202','6')` + Java-side date/age filter in `ResultCheckService` |
| (real-time idempotency) | "already dispatched?" check | `TbFirmTranRepository.existsByTranDtAndBizKeyAndTranSeq(...)` |
| (result-detail fetch) | batch fetch of detail rows | `TbFirmTranRepository.findByBizKeyAndTranDtAndTranSeqIn(...)` |

> The legacy IDO embedded the 2-minute window in SQL timestamp arithmetic; the refactor pushes the date/age filter into `ResultCheckService` (the source of the day-boundary edge case — see [scheduler-flow.md §3](scheduler-flow.md#3-the-scheduled-job--resultcheckjob)).

## 4. EFS leg mapping

| EFS leg | Legacy usage | Spring caller | `.jct` endpoint |
|---|---|---|---|
| 201 | depositor inquiry (`WB_00000100`) | `RecipientLookupService` | `/WGVN_EFS_201.jct` |
| 202 | real-time transfer (`NAPAS_REAL_TIME_BATCH`) | `RealtimeTransferService` | `/WGVN_EFS_202.jct` |
| 203 | result inquiry (`PROCESSING_RESULT_CHECK_BATCH` + G999 recovery) | `ResultCheckService`, `RealtimeTransferService` | `/WGVN_EFS_203.jct` |
| 204 | Woori FX pre-rate (non-VND) | `RealtimeDispatchService.runFxInquiry` → `RealtimeTransferService` | `/WGVN_EFS_204.jct` |
| 301 | bulk reception request (`BATCH1`) | `BulkReceptionService` | `/WGVN_EFS_301.jct` |
| 302 | bulk execution request (`BATCH2`) | `BulkReceptionResultService.issueExecutionInline` | `/WGVN_EFS_302.jct` |

## 5. Migration narrative & decisions

The migration moved a **polling-based, JEX-runtime-coupled batch farm** to **event-driven Spring services** inside `wecms-api`. The intentional model changes:

1. **Polling → event-driven.** Real-time and bulk transfers fire on a Spring `afterCommit` synchronization off the payment transaction; no status-scan loop accumulates `PROC_STAT='9'` rows. Only the result-check sweep stayed scheduled.
2. **BATCH2 absorbed into the 01301 callback.** The EFS 302 execution request issues inline once reception is accepted — no standalone second batch (removes a scan + a timer's latency; decision K9, 2026-06-12).
3. **Socket → REST.** Gateway transport is `RestClient` over `.jct` URLs; mock clients throw when the leg is disabled so nothing fakes success.
4. **Audit model.** Legacy `*_HIS` BEFORE/AFTER triggers are replaced by `AUDIT_LOGS_INFM` + the per-leg `TB_FIRM_BAT_API_TRAN` rows.
5. **204 FX persistence** is implemented (a `TRAN_TP='204'` `TB_FIRM_TRAN` row + `TB_PAY_TARGET` FX columns) — the earlier "pending PO" label in memory `project_batch_refactor_real_impl` is **stale**.
6. **C5 branch-split** (above-limit multi-file split when `BAT_TRAN_BRANCH_DIV='Y'`) is **ported** — also previously mislabelled "pending."

**Migration status legend:** ✅ Migrated · ✅ Absorbed (folded into another component) · 🔁 Replaced (different mechanism) · ➕ Added (refactor-only) · ⏳ Deferred / not ported (PO scope).

**Deferred / open before non-local activation** (full authoritative list — [../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md](../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md)):
- UAT Shinhan/Woori bank-code override (mandatory before UAT/PROD on those banks).
- Result-check day-boundary fix (before enabling the PROD cron).
- Operating-hours gate + channel-mix validation (PO ruling: SIT-only vs PROD-required).
- Slack alerting (PO decision: log-based monitoring vs port `MsgSendUtil`).
- Infra/SIT provisioning (B1/B2/B4/B7/I5) + B5 byte-parity + V1–V6 round-trips — non-code.

## 6. Source-of-truth pointers

- Legacy entities (read-only structural reference): `WECMS DEV/WECMS/src/wabooks/entity/TbFirmFile{Mst,Data,Rslt}.java`, `TbFirmBatApiTran.java`.
- Legacy helper: `WECMS DEV/WECMS/src/com/firm/FIRM_PROC.java`; service descriptor `WECMS DEV/WECMS_ETC/xml/service/IMO/IMO.WB_00000100.xml`.
- Schema DDL parity: [../../references/database/payment/firm-banking-dispatch-schema.md](../../references/database/payment/firm-banking-dispatch-schema.md).
- Dispatch-path payment peer: [../../references/migration/internal-transfer-mapping.md](../../references/migration/internal-transfer-mapping.md).
- Audits: [TO-BE process](../../audits/wecms-batch-tobe-process-audit.md) · [final gap](../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md).
