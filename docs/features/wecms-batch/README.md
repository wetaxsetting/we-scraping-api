# WeCMS Batch — Firm-Banking Dispatch (Backend Feature)

> **Status:** Implemented, production-grade — **inert by default** (six feature flags OFF, result-check cron `"-"`, mock gateway/SFTP clients throw). Not SIT/PROD-active. Source-verified 2026-06-24.
>
> **Backend package:** [`com.kosign.wecms.domain.payment.dispatch`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/) (`TB_FIRM_CONF` owned by the settings module).
>
> **Domain note:** firm-banking dispatch is a **payment-domain** capability; this folder sits at the `features/` root by request rather than under `features/payment/`. Shared payment infrastructure it builds on is indexed in [../payment/README.md §2.1](../payment/README.md).

## Module overview

WeCMS Batch is the **firm-banking dispatch engine**: the backend module that takes a committed payment (real-time or file/bulk) and drives it through the bank gateway (Coocon/EFS) to completion — building fixed-length deposit files, encrypting/signing envelopes, transferring over SFTP, reconciling inbound result callbacks, and writing the per-transfer and per-file audit trail.

It replaces a legacy farm of standalone JEX "batch" programs with **event-driven Spring services** inside the single `wecms-api` deployable. There is no separate batch jar — the dispatch path ships with the API.

## Purpose

- Push **real-time** (VND / API-path) transfers to the bank the moment the payment commits (EFS 202, plus EFS 204 Woori FX pre-rate for non-VND Woori transfers).
- Build, upload, and execute **file/bulk** deposit transfers (EFS 301 reception → EFS 302 execution), then ingest the result file.
- Verify beneficiary account-holder names synchronously (EFS 201 depositor inquiry).
- **Reconcile** no-response transfers with a single scheduled sweep (EFS 203).
- Maintain the full DB state machine and per-leg API audit so every dispatch is traceable.

## Architecture summary

- **Event-driven, not polling.** Real-time and bulk transfers fire on a Spring `afterCommit` synchronization registered off the payment transaction (`RealtimeDispatchRegistrar`, `DispatchAutoReceiveRegistrar`). The legacy status-scan loops are gone.
- **One scheduled job.** Only the result-check sweep (`ResultCheckJob`) remains a `@Scheduled` job, guarded by ShedLock.
- **Callback-driven completion.** The bank posts back to two unauthenticated-but-crypto-verified callback endpoints (01301 reception, 01302 execution); the 302 execution request is issued **inline** from the 01301 callback (no second batch).
- **Inert by default.** Six `@ConfigurationProperties` flags default OFF; `@ConditionalOnProperty` selects mock EFS/SFTP clients that **throw** rather than fake success, so nothing dispatches until infrastructure is provisioned.

See [firm-banking-dispatch.md](firm-banking-dispatch.md) for the full architecture and [scheduler-flow.md](scheduler-flow.md) for the trigger/scheduling model.

## Main business flows

| Flow | Trigger | EFS legs | Detail |
|---|---|---|---|
| **Depositor inquiry** | Synchronous service call | 201 | [firm-banking-dispatch.md §Depositor inquiry](firm-banking-dispatch.md#depositor-inquiry-flow) |
| **Real-time dispatch** | `afterCommit` (per target) | 204 → 202 (→ 203 recovery) | [firm-banking-dispatch.md §Realtime](firm-banking-dispatch.md#realtime-dispatch-flow) |
| **File / bulk dispatch** | `afterCommit` → reception | 301 → (01301) → 302 inline | [firm-banking-dispatch.md §File](firm-banking-dispatch.md#file-dispatch-flow) |
| **Callback ingest** | Inbound POST from gateway | 01301 / 01302 | [firm-banking-dispatch.md §Callback](firm-banking-dispatch.md#callback-flow) |
| **Result check** | `@Scheduled` sweep | 203 | [firm-banking-dispatch.md §Result-check](firm-banking-dispatch.md#result-check-flow) |

## Related tables

`TB_PAY_TARGET` · `TB_FIRM_TRAN` · `TB_PAY_FILE_MST` · `TB_PAY_FILE_DATA` · `TB_FIRM_FILE_MST` · `TB_FIRM_FILE_DATA` · `TB_FIRM_FILE_RSLT` · `TB_FIRM_BAT_API_TRAN` · `TB_VAN_API_INFO` · `TB_FIRM_CONF` (+ `TB_BATCH_SCHEDULE_INFO` / `_DTL`, entities present but not yet consumed).

Full per-table reference: [database-schema.md](database-schema.md). Column-by-column DDL parity: [../../references/database/payment/firm-banking-dispatch-schema.md](../../references/database/payment/firm-banking-dispatch-schema.md).

## Related services

| Service / class | Role |
|---|---|
| `RealtimeDispatchService` · `RealtimeTransferService` | Real-time transfer orchestration + EFS 204/202/203 |
| `BulkReceptionService` | File build + SFTP upload + EFS 301 |
| `BulkReceptionResultService` | 01301 callback + inline EFS 302 |
| `BulkExecutionResultService` | 01302 callback + result-file ingest |
| `ResultCheckService` / `ResultCheckJob` | Scheduled 203 reconcile |
| `FirmBankingInquiryService` → `DepositorInquiryService` / `RecipientLookupService` | EFS 201 depositor inquiry |
| `EfsApiClient` (`RestClientEfsApiClient` / `MockEfsApiClient`) · `EfsCryptoService` | Gateway transport + AES-256/HMAC crypto |
| `SftpClient` (`JschSftpClient` / `MockSftpClient`) · `FixedLengthCodec` | SFTP transport + 1000-byte H/D/T file codec |
| `DispatchKeyGenerator` → `LegacyFunctionRepository` | JEX-compatible key generation (`wb_get_next_val`) |

## Related documents

- [firm-banking-dispatch.md](firm-banking-dispatch.md) — the feature guide (purpose, architecture, all flow diagrams, design decisions).
- [bulk-execution-retry.md](bulk-execution-retry.md) — **`00302` retry sweep** (added 2026-07-29). **⛔ NOT approved for enablement, and NOT a legacy-parity fix.** An earlier revision of this line called it a restoration of a dropped legacy responsibility — that was wrong. `issueExecutionInline` already reproduces BATCH2's business responsibility; legacy's retry was an emergent side-effect of polling. K9 offered Mr. Kang the periodic recovery scheduler (**Option A**) and he **did not select it**, choosing inline execution + monitoring + manual recovery. The document's legacy evidence, retry-code mapping (incl. the `BT00` fix), idempotency/transaction analysis, **monitoring** section and **manual-recovery runbook** all remain valid and approved — only the scheduled sweep is not. Ruling: [k9-bulk-retry-reassessment.md](../../architecture/k9-bulk-retry-reassessment.md).
- [scheduler-flow.md](scheduler-flow.md) — schedulers, cron, `afterCommit`, ShedLock, event-driven model.
- [database-schema.md](database-schema.md) — the ten dispatch tables (purpose / columns / relationships / R-I-U flows).
- [deployment-guide.md](deployment-guide.md) — build, env vars, EFS/SFTP config, flags, SIT/UAT/PROD activation checklists.
- [troubleshooting.md](troubleshooting.md) — common operational issues with symptoms / root cause / verification / resolution.
- [legacy-jex-mapping.md](legacy-jex-mapping.md) — legacy JEX → Spring component mapping (the only migration-traceability doc in this set).
- [wecms-batch-flow-process.html](wecms-batch-flow-process.html) — **complete handover & operational playbook** (standalone HTML, enterprise style, 18 sections): executive overview, end-to-end business process, legacy vs Spring architecture, real-time/FX/file flows, callback logic, scheduler, status-transition matrix, database CRUD ownership, EFS integration cards, sequence diagrams, configuration, SIT/UAT/PROD activation checklists, troubleshooting decision trees, full legacy→Spring mapping, and an appendix. Open in a browser; print-friendly for onboarding. A developer can understand the whole system from this file without opening source.

**External companions:**
- TO-BE process audit: [../../audits/wecms-batch-tobe-process-audit.md](../../audits/wecms-batch-tobe-process-audit.md)
- Final gap audit (authoritative remaining-work list): [../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md](../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md)
- Schema DDL parity: [../../references/database/payment/firm-banking-dispatch-schema.md](../../references/database/payment/firm-banking-dispatch-schema.md)
- Dispatch-path payment peer: [../../references/migration/internal-transfer-mapping.md](../../references/migration/internal-transfer-mapping.md)
- Shared payment infrastructure index: [../payment/README.md §2.1](../payment/README.md)

## Navigation

```
features/wecms-batch/
├── README.md                  ← you are here
├── firm-banking-dispatch.md   feature guide + all flow diagrams
├── scheduler-flow.md          schedulers · afterCommit · ShedLock
├── database-schema.md         the 10 dispatch tables
├── deployment-guide.md        build · config · activation checklists
├── troubleshooting.md         operational issues + fixes
├── legacy-jex-mapping.md      legacy JEX → Spring mapping
└── wecms-batch-flow-process.html   visual flow-process handover (standalone HTML)
```
