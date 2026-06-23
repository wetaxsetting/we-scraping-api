# Internal Transfer — Source Code Index

Complete Legacy → Backend → Frontend file map, source-code-verified 2026-06-23.

---

## Backend (`wecms-api`)

### Controllers — `domain/payment/internaltransfer/controller/`
| File | Role | Flow |
|---|---|---|
| `InternalTransferController.java` | `@RequestMapping("/api/v1/payment/internal-transfer")`, `@PreAuthorize("@menuAuthz.canAccess('payment-internal-transfer-scheduled')")`. Endpoints: list · withdrawal-accounts · detail · file download · approval-attachment download · next-doc-no · uploads/parse · create · PATCH reporting-account · dispatch · DELETE · request-approval (multipart) · DELETE approval (withdraw) · PUT approval-line · approval-action · banking-match (candidates/match/unmatch/unmatch-all). | 1,2,4 |
| | **DEAD LEGACY:** no `rerun` endpoint exists. The controller carries only a placeholder comment block (`// Re-run one detail line (legacy pymn_0010_002.ts:441-472)`) with no `@PostMapping`; `InternalTransferService` has no `rerun` method. Re-run was never ported. | |
| `TransferResultController.java` | `@RequestMapping(".../results")`, `@PreAuthorize("…('payment-internal-transfer-result')")`. list · detail · export. | 3 |

### Services — `.../service/`
| File | Role | Flow |
|---|---|---|
| `InternalTransferService.java` | Core: `list`, `transactionLinesByBatch`, `detail`, `createDraft`, `updateReportingAccount`, `delete`, `requestApproval`, `actOnApproval`, `withdrawApproval`, `changeApprovalLine`, `previewNextDocNo`, `listWithdrawalAccounts`, `parseExcel`, `resolveDownload`, `resolveApprovalAttachmentDownload` (+ fileType overload). No `rerun` method (DEAD LEGACY — see controller row). | 1,2,4 |
| `InternalTransferResultService.java` | `list` (`searchDispatched`), `detail` (+ `DispatchResultResolver`, `countByStatus`). | 3 |
| `InternalTransferDispatchService.java` | `dispatch` — cut-off gate + `InternalTransferDispatchExecutor` + `TRNF_YN` stamp. | 2 |
| `InternalTransferDispatchExecutor.java` | Writes `TB_PAY_TARGET` (API path) / `TB_PAY_FILE_MST`+`TB_PAY_FILE_DATA` (file path); legacy routing rule. | 2 |
| `InternalTransferExcelParser.java` | Real POI workbook parser + per-cell validation + cross-row consistency. | 1 |
| `InternalTransferKeyGenerator.java` | `SCDUL_PYMN_NO` generator. **Routes through `LegacyFunctionRepository.nextScdulPymnNo` → `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no',biz_key)` (per-tenant plain-int `MAX+1`).** The earlier refactor-only date-prefixed `<yyyyMMdd><6-seq>` counter-table approach was removed 2026-06-09 (OD-2 reversal) so the refactor mints the same value the live JEX DB would. `@Transactional(MANDATORY)`. | 1 |
| `InternalTransferFileResolver.java` + `InternalTransferStorageProperties.java` | Storage-root path resolution (batch Excel + approval attachments); path-traversal guard. | 1,4 |
| `CutoffValidator.java` + `CutoffProperties.java` | Per-bank/network cut-off-time gate. | 2 |
| `InternalTransferConfig.java` | Bean config. | — |
| `ScdulPymnTypeCd.java` | `INTERNAL = "03"`. | all |
| `TransferNetwork.java` | `fromAntDivCd` (NAPAS/CITAD/SWIFT). | 2 |
| `TrnfYn.java` | `NOT_TRANSFERRED`/`TRANSFERRED`. | 2 |
| `InternalTransferSortField.java` / `InternalTransferResultSortField.java` | Sort whitelists. | 2,3 |

### Repositories — `.../repository/`
| File | Role |
|---|---|
| `ScdulPymnSumrRepository.java` | `findBy…ScdulPymnNoAndScdulPymnTypeCd`, `findBy…ApprSeqNoAndScdulPymnTypeCd`, `search` (+ `apprType` status filter via `wb_get_appr_stts`), `searchCurrencyTotals`, `searchDispatched` (+ `procStts` filter), `searchDispatchedCurrencyTotals`, `findCcyCdByBizKeyAndApprSeqNos`, `findOrgFileNmByBizKeyAndApprSeqNos`, `clearApprSeqNo`. **Removed 2026-06-10:** `findMaxApprSeqNoByBizKey` (APPR_SEQ_NO now via the `wb_get_next_val` funnel) and `updateApprStts` (SCDUL_PYMN_SUMR has no APPR_STTS column — status derived on read via `wb_get_appr_stts`). |
| `ScdulPymnSumrDetlRepository.java` | line read / delete-by-batch; `DETL_NO_NUMERIC_ASC` sort constant. |
| `ExptResolFileRepository.java` | approval attachments (EXPT_RESOL_FILE). |
| `ExptResolPayEvidFileRepository.java` | payment-evidence attachments (EXPT_RESOL_PAY_EVID_FILE) — the second leg of the combined attachment UNION (GAP-ATT-1). |
| `ScdulPymnCashFlowRow.java` | projection. |

### DTOs — `.../dto/`
Create/Upload: `CreateScheduledTransferRequest`, `TransferLineInput`, `CreateScheduledTransferResponse`, `ParseExcelResponse`, `ParsedTransferLine`, `WithdrawalAccountRow`, `NextDocNoResponse`.
Scheduled: `ScheduledTransferListResponse`, `ScheduledTransferRow`, `ScheduledTransferDetailResponse`, `TransferLineDetail`, `ScheduledTransferApprover`, `CurrencyTotal`, `UpdateReportingAccountRequest`, `DispatchRequest`.
Result: `TransferResultListResponse`, `TransferResultRow`, `TransferResultDetailResponse`, `TransferResultLine`.
Approval: `RequestApprovalRequest` (+ `ApproverInput`/`FileInput`), `RequestApprovalResponse`, `ApprovalActionRequest`, `ChangeApprovalLineRequest`, `ApprovalAttachmentRow`.

### Entities — `.../entity/`
`ScdulPymnSumr` (+`ScdulPymnSumrId`), `ScdulPymnSumrDetl` (+`ScdulPymnSumrDetlId`), `ExptResolFile` (+`ExptResolFileId`), `ExptResolPayEvidFile` (+`ExptResolPayEvidFileId`). No `InternalTransferKeySeq` entity — the refactor-only key-counter table was removed (key generation funnels through `wb_get_next_val`).

### Export — `.../export/`
`InternalTransferResultExportProvider.java` (`screenId='payment-internal-transfer-result'`).

### Cross-domain collaborators (referenced)
`approvalbox/service/ApprovalBoxService` (approve/reject chain), `ApprovalBoxFanoutService` (withdraw/change-line), `ApprDv`; `payment/service/DocNoGenerator`; `payment/common/{TransferLineDetailMapper, OutBankCdNormalizer, UploadConsistencyValidator}`; `payment/dispatch/service/DispatchResultResolver` + `dispatch/repository/TbPayFileDataRepository` + `DispatchCountRepository`; `payment/match/service/DomesticPaymentLineMatchService`; `setting/erptransactionhistory/service/ErpTrnsHistoryWriter`; `setting/chartofaccounts/repository/ChartOfAccountRepository`; `setting/firmbankingsettings/repository/TbFirmBankCodeRepository`; `banking/repository/{AcctMstrRepository, BankInfmRepository}`; `payment/notification/service/ApprovalNotificationService` (BLTB_* bulletin/notification fan-out); `payment/approvalbox/service/ApprovalBoxFanoutService` (EXPT_RESOL_DETL / EXPT_RESOL_APPR_LINE snapshot) + `ApprovalDisplayFlagsResolver`; `payment/internaltransfer/service/ApprovalAttachmentLoader` + `ApprovalAttachmentDownloadService` (combined EXPT_RESOL_FILE ∪ EXPT_RESOL_PAY_EVID_FILE); `auth/repository/BusiInfmRepository`; `common/auditlog/service/AuditLogService`; `common/tenant/TenantContext`; `navigation/service/MenuAuthorizationGuard`.

### Tests — `src/test/.../internaltransfer/`
Controllers: `controller/InternalTransferControllerTest`, `controller/TransferResultControllerTest`.
Services: `service/CutoffValidatorTest`, `service/InternalTransferExcelParserTest`, `service/InternalTransferKeyGeneratorTest`, `service/InternalTransferSortFieldTest`, `service/InternalTransferResultServiceTest`, `service/InternalTransferListFooterTotalsTest`, `service/InternalTransferRequestApprovalBackstampTest`, `service/InternalTransferResultDownloadAuditTest`, `service/ApprovalAttachmentDownloadServiceTest`, `service/ApprovalAttachmentLoaderTest`, `service/InternalTransferDispatchExecutorAutoReceiveTest`, `service/InternalTransferDispatchExecutorBankChnlDivTest`.
Repositories: `repository/ScdulPymnSumrSearchStatusFilterTest`, `repository/ScdulPymnSumrSearchDispatchedStatusFilterTest`, `repository/ScdulPymnSumrSearchDispatchedDateFilterTest`, `repository/ScdulPymnSumrDetlSortTest`.
Coverage gap: no dedicated `InternalTransferDispatchServiceTest` (dispatch flow is exercised only via the executor tests + controller test).

---

## Frontend (`wecms-web`)

### Routes — `app/(dashboard)/payment/internal-transfer/`
`scheduled-transfer/page.tsx` + `scheduled-transfer/[id]/page.tsx`; `transfer-result/page.tsx` + `transfer-result/[id]/page.tsx`.

### Components — `components/payment/internal-transfer/`
`ScheduledTransferPage.tsx` (Flow 2 list + Flow 4 Request-Approval/Approval-Details), `ScheduledTransferDetailPage.tsx` (Flow 2 detail), `TransferResultPage.tsx` (Flow 3 list), `TransferResultDetailPage.tsx` (Flow 3 detail), `InternalTransferUploadDialog.tsx` (Flow 1), `types.ts`.

Shared (`components/payment/shared/`): `PaymentScheduledBatchListPage`, `PaymentResultBatchListPage`, `PaymentDetailPage`, `ResultDetailView`, `PaymentExcelUploadDialog`, `RequestApprovalDialog`, `ApprovalDetailsDialog` (+ `chainToDialogApprovers`/`isCurrentUserActiveApprover`), `DetailSummary`, `DetailHeader`, `DetailStatusTabs`, `FileCard`, `detailColumns.tsx`, `deriveResultStatus.ts`, `PaymentResultBadge`, `WithdrawalAccountDropdown`. Plus `components/payment/corporate-card/EditApprovalLinePicker`, `components/payment/approval-box/StatusBadge`/`types`.

### API client — `lib/api/internalTransfer.ts`
`fetchScheduledTransferList`, `fetchScheduledTransferDetail`, `fetchNextInternalTransferDocNo`, `withdrawInternalTransferApproval`, `changeInternalTransferApprovalLine`, `downloadOriginalExcel`, `downloadApprovalAttachment`, `parseAttachmentFilename`, `fetchWithdrawalAccounts`, `parseInternalTransferExcel`, `createScheduledTransfer`, `updateReportingAccount`, `deleteScheduledTransfer`, `dispatchScheduledTransfer`, `submitInternalTransferApproval`, `actOnInternalTransferApproval`, `fetchTransferResultList`, `fetchTransferResultDetail`, banking-match fns (`fetchInternalTransferMatchCandidates`, `matchInternalTransferLine`, `unmatchInternalTransferLine`, `unmatchAllInternalTransferLines`). No `rerunTransferLine` (re-run was never ported — DEAD LEGACY). Plus `lib/api/approval-box.ts` `fetchApprovalBoxApprovers`; `lib/api/mappers.ts` `mapScheduledTransferList`/`mapScheduledTransferDetail`.

### Hooks
`useApiData`, `usePagination`, `useTableSort`, `useStickyLoading`, `useViewSetting`, `useBlockedChars`, `useExcelExport`.

### Types — `lib/types/api.ts`
`ApiScheduledTransferListResponse`/`…Row`, `ApiScheduledTransferDetailResponse`, `ApiTransferLineDetail`, `ApiTransferResultListResponse`/`…DetailResponse`, `ApiCreateScheduledTransferRequest`, `ApiParseExcelResponse`, `ApiRequestApprovalRequest`/`…Response`, `ApiApprovalActionRequest`, `ApiDispatchRequest`, `ApiUpdateReportingAccountRequest`, `ApiWithdrawalAccountRow`, banking-match types.

---

## Legacy (JEX, read-only `WECMS DEV/`)

| Legacy file | Type | → Refactor |
|---|---|---|
| `pymn_0009_001_r001_act.jsp` (+ `PymnHelper.pymn_0001_001_r001`) | Action JSP + helper | `list` |
| `pymn_0009_001_c001_act.jsp` (+ `PymnHelper.pymn_0009_001_c001`, `validateTransferTiming`, `getCutoffConfig`) | Action JSP + helper | `dispatch` (+ executor + `CutoffValidator`) |
| `pymn_0009_001_d001_act.jsp` | Action JSP | `delete` |
| `pymn_0009_002_r001_act.jsp` (+ `PymnHelper.importDataFromExcelForInternalTransfer`) | Action JSP + helper | `parseExcel` → `InternalTransferExcelParser` |
| `pymn_0009_002_c001_act.jsp` (+ `PymnHelper.uploadPaymentExcel`) | Action JSP + helper | `createDraft` |
| `pymn_0009_002_u002_act.jsp` (+ `PymnHelper.pymn_0001_002_u002`) | Action JSP + helper | `updateReportingAccount` |
| `pymn_0009_003_r001_act.jsp` (IDO `SCDUL_PYMN_SUMR_R012`/`_R013`) | Action JSP + IDO | `detail` |
| `pymn_0010_001_r001_act.jsp` (+ `PymnHelper.pymn_0002_001_r001`) | Action JSP + helper | result `list` |
| `pymn_0010_002_r001_act.jsp` (+ `PymnHelper.pymn_0002_002_r001`; IDO `SCDUL_PYMN_SUMR_R006`) | Action JSP + helper + IDO | result `detail` + `DispatchResultResolver` |
| `pymn_0010_002.ts:441-472` | Client TS | **DEAD LEGACY — re-run NOT ported** (no backend endpoint/service, no frontend client fn). |
| `comm_1001_001` | WSVC | `listWithdrawalAccounts` |
| `comm_0194_001` → `comm_0192_001_c005` | Approval submit | `requestApproval` |
| `comm_0195_001` (footer Approve/Reject → `bltb_0001_003_u001`; kebab Delete; edit-mode) | Approval Details popup | `actOnApproval` / `withdrawApproval` / `changeApprovalLine` |
| WSVC XML: `WSVC.pymn_0009_001_r001/_c001/_d001`, `pymn_0009_002_r001/_c001/_u002`, `pymn_0010_001_r001/_002_r001` (under `WECMS_ETC/xml/service/WSVC/`) | Service contracts | (validation lengths, baseline §6) |
| Entities `ScdulPymnSumr`, `ScdulPymnSumrDetl`, `TbPayTarget`, `TbPayFileMst`, `TbPayFileData` | JEX entities | refactor `entity/*` + `payment/dispatch/entity/*` |

> Permission matrix: `comm_0190_003_view.jsp:530-547` — `pymn_0009_001` = R/U/D/T, `pymn_0010_001` = R only.
