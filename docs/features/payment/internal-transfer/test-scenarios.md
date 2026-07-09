# Internal Transfer — Test Scenarios (Backend)

> **Hub:** [README.md](README.md) · **WEB tests:** [wecms-web/.../test-scenarios.md](../../../../../wecms-web/docs/features/payment/internal-transfer/test-scenarios.md)

Source-code-verified 2026-06-23. Existing test inventory + scenario coverage + missing coverage. Test root: `src/test/.../domain/payment/internaltransfer/`.

---

## 1. Existing test inventory

### Controllers
- `controller/InternalTransferControllerTest`
- `controller/TransferResultControllerTest`

### Services
- `service/CutoffValidatorTest` — cut-off-time gate (NAPAS/CITAD/SWIFT)
- `service/InternalTransferExcelParserTest` — POI parse + per-cell validation
- `service/InternalTransferKeyGeneratorTest` — `wb_get_next_val` funnel
- `service/InternalTransferSortFieldTest` — sort whitelist
- `service/InternalTransferResultServiceTest` — result list/detail
- `service/InternalTransferListFooterTotalsTest` — currency totals footer (F-1)
- `service/InternalTransferRequestApprovalBackstampTest` — request-approval `APPR_SEQ_NO` stamp
- `service/InternalTransferResultDownloadAuditTest` — export audit
- `service/ApprovalAttachmentDownloadServiceTest` / `service/ApprovalAttachmentLoaderTest` — attachment download (combined EXPT_RESOL_FILE ∪ EXPT_RESOL_PAY_EVID_FILE)
- `service/InternalTransferDispatchExecutorAutoReceiveTest` / `service/InternalTransferDispatchExecutorBankChnlDivTest` — dispatch executor routing

### Repositories
- `repository/ScdulPymnSumrSearchStatusFilterTest` — `appr_type` status filter via `wb_get_appr_stts`
- `repository/ScdulPymnSumrSearchDispatchedStatusFilterTest` — result `procStts` filter
- `repository/ScdulPymnSumrSearchDispatchedDateFilterTest` — result `TRNF_DTM` date window
- `repository/ScdulPymnSumrDetlSortTest` — line sort

---

## 2. Scenario coverage by flow

| Flow | Scenario | Covered by |
|---|---|---|
| 1 Parse | header per-locale, required fields, mixed currency/network | `InternalTransferExcelParserTest` |
| 1 Create | key-gen, master+lines persist | `InternalTransferKeyGeneratorTest`, controller test |
| 2 List | status filter, footer totals, sort | `ScdulPymnSumrSearchStatusFilterTest`, `InternalTransferListFooterTotalsTest`, `InternalTransferSortFieldTest` |
| 2 Detail | master + lines + line sort | `ScdulPymnSumrDetlSortTest`, controller test |
| 2 Dispatch | executor routing (API vs file), auto-receive, bank-channel-div | `InternalTransferDispatchExecutor*Test`, `CutoffValidatorTest` |
| 3 Result list | `procStts` filter, `TRNF_DTM` window | `ScdulPymnSumrSearchDispatched*Test`, `InternalTransferResultServiceTest` |
| 3 Result detail | per-line resolution, tab counts | `InternalTransferResultServiceTest` |
| 3 Export | audit on download | `InternalTransferResultDownloadAuditTest` |
| 4 Request approval | `APPR_SEQ_NO` stamp, fan-out | `InternalTransferRequestApprovalBackstampTest` |
| 4 Attachments | combined UNION download | `ApprovalAttachmentDownloadServiceTest`, `ApprovalAttachmentLoaderTest` |

---

## 3. Missing / thin coverage

| Gap | Note |
|---|---|
| **No dedicated `InternalTransferDispatchServiceTest`** | dispatch flow exercised only via executor tests + controller test — the `WBE2063`/`WBE2064` guards + cut-off-gate orchestration have no direct service test |
| Approve / reject / withdraw / change-line | `actOnApproval`/`withdrawApproval`/`changeApprovalLine` guard codes (`WBE2126`/`WBE2127`) — coverage relies on the cross-feature `ApprovalBoxService` tests; no IT-specific guard test |
| `updateReportingAccount` | `WBE2060`/`WBE2061`/`WBE2064` guard paths — no dedicated test |
| Delete `WBE2062` guard | `APPR_SEQ_NO`-set rejection — no dedicated test |
| Banking-match per-line | covered by shared `DomesticPaymentLineMatchService` tests, not IT-specific |

---

## 4. SIT/UAT notes

- Local R&D has no bank daemon → seeded `TRNF_YN='Y'` batches show whatever `PROC_STAT`/`RS_CD` the seed carries; `DispatchCountRepository` degrades to empty counts when dispatch tables are unreachable (documented degradation).
- SIT smoke: Completed/In-Progress (`trnf_stts` `C`/`P`) result filter with real `TB_PAY_*` data (open item per `project_payment_result_sit_ready.md`).

## 5. Cross-links

[business-flow.md](business-flow.md) · [interface-specification.md](interface-specification.md) · [source-code-index.md](source-code-index.md)
