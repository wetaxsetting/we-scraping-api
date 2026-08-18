# Approval Box — Source Code Index (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

Package root: `wecms-api-main/src/main/java/com/kosign/wecms/domain/payment/approvalbox/`. One-line role per file.

## Controller

| File | Role |
|---|---|
| `controller/ApprovalBoxController.java` | `@RequestMapping("/api/v1/payment/approval-box")`, class-level `@PreAuthorize("@menuAuthz.canAccess('payment-approval-box')")`. 12 thin endpoints → delegate to `ApprovalBoxService` + `ApprovalDetailService` + `ExcelExportService` + `ApprovalAttachmentDownloadService`. The 8 core list/action endpoints (list, approvers, approve, reject, withdraw, change-approval-line, bulk-approve, export) plus 4 shared-popup support endpoints for the notification entry point: `GET /detail`, `GET /{apprSeqNo}/transactions`, `GET /{apprSeqNo}/attachments`, `GET /{apprSeqNo}/attachments/{fileId}` (binary download, `fileType` routes `EXPT_RESOL_FILE` vs `EXPT_RESOL_PAY_EVID_FILE`). NOTE: the controller's own class Javadoc claiming the endpoints are only `authenticated()` is **stale** — the `@menuAuthz` `@PreAuthorize` is the live gate. |

## Service

| File | Role |
|---|---|
| `service/ApprovalBoxService.java` | Core feature service. `list`, `listForExport`, `approvers`, `findOne`, `approve`, `reject`, `bulkApprove`, `withdraw`, `changeApprovalLine`; private `loadForAction` / `applyApprove` / `applyReject` / `promoteNextWaiting` / `mapProjections` / `toRow` / `deriveApprStts` / **`computeFooterTotals`** (footer aggregate, G1) / shared static **`resolveCurrency`** + **`resolveAmount`** (card-aware, G5) + **`toBigDecimal`** / `clearSourceApprStamp` / validation helpers. Constant `CARD_APPR_DV = "33"`. Audit + notification wiring. On approve/reject it stamps `PAY_END_*` once every approver has voted (legacy `comm_0192_010` parity). (No `writeBackApprStts` — the legacy SCDUL/OVRS `APPR_STTS` mirror was removed 2026-06-10; withdraw uses the source repos' `clearApprSeqNo` to null `APPR_SEQ_NO` only.) |
| `service/ApprovalBoxFanoutService.java` | Shared write-side fan-out called by sibling Payment modules: `fanout` (insert master + chain + notify), `writeResolutionDetail`, `writeApprovalLineSnapshot`, `withdrawApproval`, `changeApprovalLine`. Calls `ApprovalNotificationService` (BLTB writer). |
| `service/ApprovalDetailService.java` | Backs the common Approval Details popup from a notification entry point: `master(apprSeqNo|postSeqNo,bltbCd)` (resolves via `BLTB_POST_INFM`), `transactions(apprSeqNo)` (per-line txns for the four scheduled kinds; card/overseas return empty), `attachments(apprSeqNo)` (`EXPT_RESOL_FILE` ∪ `EXPT_RESOL_PAY_EVID_FILE`). |
| `service/ApprovalAttachmentWriter.java` | Write-side helper — persists uploaded approval files into `EXPT_RESOL_FILE` (one row per file; filename sanitisation). |
| `service/ApprovalDisplayFlagsResolver.java` | Resolves the print-form display flags from the env-set (`SYS_ENV_SET_INFM`). |
| `service/ApprDv.java` | Enum of the six (+legacy) payment-type codes; `code()`, `slug()`, `i18nKey()`, `docNm()` (verbatim legacy `getLabel()` for `DOC_NM` write parity), `resolveSlug`, `fromCode`, `APPROVAL_BOX_CODES`. |
| `service/ApprTypeCd.java` | Constants for master tab discriminator (`PENDING 00`, `APPROVED 01`, `REJECTED 02`, `IN_PROGRESS 03`). |
| `service/ApprvrStts.java` | Constants for per-approver status (`PENDING P`, `APPROVED A`, `REJECTED R`, `WAITING W`, `STOPPED S`). |
| `service/ApprovalBoxSortField.java` | `SortableField` whitelist: wire `FIELD_ID` → entity property; lenient `parse`. |

## Repository

| File | Role |
|---|---|
| `repository/ExptResolMstrRepository.java` | `search` (paged constructor projection + 5-branch visibility), `countTabs` (identical visibility, no `apprTypeCd`), **`findAmountBasisForTotals`** (footer basis — tab-unfiltered, same visibility, G1), `findByBizKeyAndApprSeqNoIn`, `findDocNoByApprSeqNos`, `findCompletedApprSeqNos`, doc-no lookups. |
| `repository/ExptResolApprvrInfmRepository.java` | `findChain`, `countByStatus`, `stopDownstream` (`W→S`), `stopAllActive` (`P,W→S`), `deleteByBizKeyAndApprSeqNo`. |

## Entity

| File | Role |
|---|---|
| `entity/ExptResolMstr.java` | `@Table("EXPT_RESOL_MSTR")`, `@IdClass(ExptResolMstrId)`. Master mapping (~37 of 47 cols; includes `CONT`, `PAY_END_*`, the five print-form flags, `APPR_FUND_TP` `@JdbcTypeCode(CHAR)`). |
| `entity/ExptResolMstrId.java` | Composite key `(bizKey, apprSeqNo)`. |
| `entity/ExptResolApprvrInfm.java` | `@Table("EXPT_RESOL_APPRVR_INFM")`, `@IdClass(ExptResolApprvrInfmId)`. Approver-chain mapping (13 cols; `APPRVR_STTS` `@JdbcTypeCode(CHAR)`). |
| `entity/ExptResolApprvrInfmId.java` | Composite key `(bizKey, apprSeqNo, apprvrId)`. |
| `entity/ExptResolDetl.java` + `ExptResolDetlId.java` | `EXPT_RESOL_DETL` per-line transaction detail — read by `ApprovalDetailService.transactions`. |
| `entity/ExptResolApprLine.java` + `ExptResolApprLineId.java` | `EXPT_RESOL_APPR_LINE` line snapshot at submission — written by the fan-out. |

## DTO

| File | Role |
|---|---|
| `dto/ApprovalBoxRow.java` | One list row on the wire (apprSeqNo, docNo, title, fileName, cont, apprDv, typeLabel, currency, totPayAmt string, payDate, draftUser*, regDtm, apprStts, counts, payEndYn, approvers). |
| `dto/ApprovalBoxProjection.java` | Internal JPQL constructor projection (never on the wire). |
| `dto/ApprovalBoxApprover.java` | Approver row (lineNo, id, name, status, actedAt). |
| `dto/ApprovalBoxListResponse.java` | `{ list, totalCount, tabCounts, footerTotals, pageNumber, pageSize, totalPages }`. |
| `dto/ApprovalBoxTabCounts.java` | `{ pending, inProgress, approved, rejected }` + `empty()`. |
| `dto/ApprovalBoxFooterTotals.java` | `{ totalDocumentCount, totalUsdAmount, totalVndAmount, totalKrwAmount }` + `empty()`. Footer bar — tab-unfiltered, full result set (G1/G4). Wire: `footer_totals`. |
| `dto/ApprovalBoxActionResponse.java` | `{ apprSeqNo, apprStts, apprvrApprTotCnt, apprvrTotCnt }`. |
| `dto/ApprovalBoxBulkApproveRequest.java` | `{ apprSeqNos }` (`@NotNull @NotEmpty @Size(max=100)`). |
| `dto/ApprovalBoxBulkApproveResponse.java` | `{ successes[], failures[] }` + nested `Failure`. |
| `dto/ChangeApprovalLineRequest.java` | `{ apprLineNo }` (`@NotNull @Positive`). |
| `dto/ApprovalTransactionRow.java` | Normalized per-line transaction for the Approval Details popup (`GET .../{apprSeqNo}/transactions`): `id, depositBank*, depositAccountNo, amount, recipientName, remarks, contactName, description, documentNo, reportingAccount, reportingBreadcrumb`. |

## Export

| File | Role |
|---|---|
| `export/ApprovalBoxExportProvider.java` | `@Component` `ExportRowProvider`, `screenId() = "approval-box"`, 10-column allow-list, tab→apprTypeCd map, currency totals subheader; reuses `ApprovalBoxService.listForExport`. |

## Tests

| File | Role |
|---|---|
| `src/test/java/.../approvalbox/controller/ApprovalBoxControllerTest.java` | MockMvc controller tests — list happy-path snake_case envelope, missing-`from_date` validation envelope, approve/reject action responses, approve business-code surfacing, bulk-approve partial result, bulk empty-body bean-validation. |
| `src/test/java/.../approvalbox/service/ApprovalBoxFanoutServiceTest.java` | Fan-out unit tests — `writeResolutionDetail` (funnel seq, blank-currency→VND / null-amount→0), `writeApprovalLineSnapshot` (env-gated insert + no-op when disabled/unseeded), `fanout` stamps `APPR_STS` In Progress. |
| `src/test/java/.../approvalbox/service/ApprovalAttachmentWriterTest.java` | `persist` inserts one `EXPT_RESOL_FILE` row per file, no-op when no files, filename path/metacharacter sanitisation. |
| `src/test/java/.../approvalbox/service/ApprovalDisplayFlagsResolverTest.java` | `resolve` reads each print-flag from the env (case-insensitive `Y`), defaults to `N` when unseeded. |

> No dedicated `ApprovalBoxServiceTest` — the core approve/reject status transitions are covered indirectly by the controller test; a Mockito service test (A1/A2/R1 transitions + footer-totals + card-amount) is the recommended follow-up.

## Cross-domain / shared collaborators (referenced, not owned)

| File | Why |
|---|---|
| `domain/payment/internaltransfer/repository/ScdulPymnSumrRepository.java` | currency (`findCcyCdByBizKeyAndApprSeqNos`) + file-name (`findOrgFileNmByBizKeyAndApprSeqNos`) batch reads, uploader-visibility EXISTS (`reg_id`, G3), `clearApprSeqNo` on withdraw (Internal/Vendor/Expense/Payroll). No `APPR_STTS` write-back — the column does not exist in legacy. |
| `domain/payment/overseas/repository/OvrsPymnSumrRepository.java` | same for Overseas (`APPR_DV='34'`): currency + file-name batch reads, uploader-visibility EXISTS, `clearApprSeqNo`. |
| `domain/payment/cardtransactions/repository/CardTrnsHstrRepository.java` | currency (`findCcyCdByBizKeyAndApprSeqNos`) + **card-amount** (`sumTrscAmtByBizKeyAndApprSeqNos` — `SUM(TRSC_AMT)`, G5) batch reads, uploader-visibility EXISTS (`APPR_DV='33'`). |
| `domain/auth/repository/BusiAdmnInfmRepository.java` | business-admin recipients for decision notifications |
| `common/auditlog/AuditLogService` | `AUDIT_LOGS_INFM` writes (`WSVC_ID="approval-box"`, `REQUIRES_NEW`) |
| `domain/payment/notification/service/ApprovalNotificationService` | lifecycle bulletin/notification writer — `notifyApprovalAction(apprSeqNo, apprDv, comntTaskCd)` writes `BLTB_COMNT_INFM` remark + `BLTB_NOTI_INFM`/`BLTB_NOTI_CONT_INFM` to admins + acted approvers; `cleanupApprovalArtifacts(...)` on withdraw. Best-effort. (Backed by legacy BLTB tables — the former refactor-only `domain/notification` + `NOTIFICATION` table was removed 2026-06-10; see memory `project_notification_module_removed.md`.) |
| `domain/payment/internaltransfer/service/ApprovalAttachmentDownloadService` | resolves + streams one Approval Details attachment by `(bizKey, apprSeqNo, fileId, fileType)` — backs the `GET .../{apprSeqNo}/attachments/{fileId}` download. |
| `common/export/ExcelExportService`, `ExcelWorkbookWriter`, `ExportableScreenRegistry` | shared XLSX pipeline |
| `navigation/service/MenuAuthorizationGuard` (`@menuAuthz`) | endpoint authorization |
| `common/tenant/TenantContext` | bizKey / userId / isAdmin from JWT |
| `domain/banking/service/cashflow/ApprovalStatusResolver` | downstream consumer of `findCompletedApprSeqNos` (Cash Flow) |

## Flyway

`src/main/resources/db/migration/` (verified filenames 2026-06-23): schema is one table per file — **V55** `expt_resol_appr_line`, **V56** `expt_resol_apprvr_infm`, **V57** `expt_resol_detl`, **V58** `expt_resol_file`, **V59** `expt_resol_mstr`, **V60** `expt_resol_pay_evid_file`. Key generation: **V140** `wb_get_next_val` (branch #1 `APPR_SEQ_NO`→`sq_appr_seq_no`, branch #39 `DOC_NO` env-set-driven) + **V148** `sq_appr_seq_no`. View Setting columns: **V156** `sys_scr_option_wecms` (manual DBA seed, 11 user columns for `web_svc_id='approval-box'`, no CHECKBOX/ETC).

> The previously-cited `V109/V137/V207/V216/V213/V194/V211` do **not** exist in the tree (no demo seed, no overseas backfill migration). See [database-mapping.md §5](database-mapping.md).
