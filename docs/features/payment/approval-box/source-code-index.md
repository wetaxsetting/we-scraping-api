# Approval Box — Source Code Index (Backend)

Package root: `wecms-api-main/src/main/java/com/kosign/wecms/domain/payment/approvalbox/`. One-line role per file.

## Controller

| File | Role |
|---|---|
| `controller/ApprovalBoxController.java` | `@RequestMapping("/api/v1/payment/approval-box")`, class-level `@PreAuthorize("@menuAuthz.canAccess('payment-approval-box')")`. 8 thin endpoints (list, approvers, approve, reject, withdraw, change-approval-line, bulk-approve, export) → delegate to `ApprovalBoxService` + `ExcelExportService`. |

## Service

| File | Role |
|---|---|
| `service/ApprovalBoxService.java` | Core feature service. `list`, `listForExport`, `approvers`, `approve`, `reject`, `bulkApprove`, `withdraw`, `changeApprovalLine`; private `loadForAction` / `applyApprove` / `applyReject` / `writeBackApprStts` / `clearSourceApprStamp` / `promoteNextWaiting` / `mapProjections` / `toRow` / `deriveApprStts` / validation helpers. Audit + notification wiring. |
| `service/ApprovalBoxFanoutService.java` | Shared write-side fan-out called by sibling Payment modules: `fanout` (insert master + chain + notify), `nextDocNo` (`VO_YYMMDD_NNN`), `withdrawApproval`, `changeApprovalLine`. Nested `record Approver`. |
| `service/ApprDv.java` | Enum of the six (+legacy) payment-type codes; `code()`, `slug()`, `i18nKey()`, `resolveSlug`, `fromCode`, `APPROVAL_BOX_CODES`. |
| `service/ApprTypeCd.java` | Constants for master tab discriminator (`PENDING 00`, `APPROVED 01`, `REJECTED 02`, `IN_PROGRESS 03`). |
| `service/ApprvrStts.java` | Constants for per-approver status (`PENDING P`, `APPROVED A`, `REJECTED R`, `WAITING W`, `STOPPED S`). |
| `service/ApprovalBoxSortField.java` | `SortableField` whitelist: wire `FIELD_ID` → entity property; lenient `parse`. |

## Repository

| File | Role |
|---|---|
| `repository/ExptResolMstrRepository.java` | `search` (paged constructor projection + visibility), `countTabs`, `findMaxDocNoSeq…`, `findByBizKeyAndApprSeqNoIn`, `findCompletedApprSeqNos`, doc-no lookups. |
| `repository/ExptResolApprvrInfmRepository.java` | `findChain`, `countByStatus`, `stopDownstream` (`W→S`), `stopAllActive` (`P,W→S`), `deleteByBizKeyAndApprSeqNo`. |

## Entity

| File | Role |
|---|---|
| `entity/ExptResolMstr.java` | `@Table("EXPT_RESOL_MSTR")`, `@IdClass(ExptResolMstrId)`. Master mapping (~37 of 47 cols). |
| `entity/ExptResolMstrId.java` | Composite key `(bizKey, apprSeqNo)`. |
| `entity/ExptResolApprvrInfm.java` | `@Table("EXPT_RESOL_APPRVR_INFM")`, `@IdClass(ExptResolApprvrInfmId)`. Approver-chain mapping (13 cols). |
| `entity/ExptResolApprvrInfmId.java` | Composite key `(bizKey, apprSeqNo, apprvrId)`. |

## DTO

| File | Role |
|---|---|
| `dto/ApprovalBoxRow.java` | One list row on the wire (apprSeqNo, docNo, title, fileName, cont, apprDv, typeLabel, currency, totPayAmt string, payDate, draftUser*, regDtm, apprStts, counts, payEndYn, approvers). |
| `dto/ApprovalBoxProjection.java` | Internal JPQL constructor projection (never on the wire). |
| `dto/ApprovalBoxApprover.java` | Approver row (lineNo, id, name, status, actedAt). |
| `dto/ApprovalBoxListResponse.java` | `{ list, totalCount, tabCounts, pageNumber, pageSize, totalPages }`. |
| `dto/ApprovalBoxTabCounts.java` | `{ pending, inProgress, approved, rejected }` + `empty()`. |
| `dto/ApprovalBoxActionResponse.java` | `{ apprSeqNo, apprStts, apprvrApprTotCnt, apprvrTotCnt }`. |
| `dto/ApprovalBoxBulkApproveRequest.java` | `{ apprSeqNos }` (`@NotNull @NotEmpty @Size(max=100)`). |
| `dto/ApprovalBoxBulkApproveResponse.java` | `{ successes[], failures[] }` + nested `Failure`. |
| `dto/ChangeApprovalLineRequest.java` | `{ apprLineNo }` (`@NotNull @Positive`). |

## Export

| File | Role |
|---|---|
| `export/ApprovalBoxExportProvider.java` | `@Component` `ExportRowProvider`, `screenId() = "approval-box"`, 10-column allow-list, tab→apprTypeCd map, currency totals subheader; reuses `ApprovalBoxService.listForExport`. |

## Tests

| File | Role |
|---|---|
| `src/test/java/.../approvalbox/controller/ApprovalBoxControllerTest.java` | MockMvc controller tests — list validation envelope, bulk bean-validation, endpoint wiring. |

## Cross-domain / shared collaborators (referenced, not owned)

| File | Why |
|---|---|
| `domain/payment/internaltransfer/repository/ScdulPymnSumrRepository.java` | currency/file batch reads + `APPR_STTS` write-back + `clearApprSeqNo` (Internal/Vendor/Expense/Payroll) |
| `domain/payment/overseas/repository/OvrsPymnSumrRepository.java` | same for Overseas (`APPR_DV='34'`) |
| `domain/auth/repository/BusiAdmnInfmRepository.java` | business-admin recipients for decision notifications |
| `common/auditlog/AuditLogService` | `AUDIT_LOGS_INFM` writes (`WSVC_ID="approval-box"`, `REQUIRES_NEW`) |
| `domain/notification/service/NotificationService` (+ `NotificationApprovalWriter`) | request / decision notifications (best-effort) |
| `common/export/ExcelExportService`, `ExcelWorkbookWriter`, `ExportableScreenRegistry` | shared XLSX pipeline |
| `navigation/service/MenuAuthorizationGuard` (`@menuAuthz`) | endpoint authorization |
| `common/tenant/TenantContext` | bizKey / userId / isAdmin from JWT |
| `domain/banking/service/cashflow/ApprovalStatusResolver` | downstream consumer of `findCompletedApprSeqNos` (Cash Flow) |

## Flyway

`src/main/resources/db/migration/`: `V109` (schema + VS seed), `V137` (demo seed), `V207` (VS legacy align), `V216` (VS legacy widths), `V213` (overseas approver-stts backfill), `V194` (USD overseas amount fix), `V211` (`EXPT_RESOL_FILE`).
