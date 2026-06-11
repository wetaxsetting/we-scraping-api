# Internal Transfer — Source Code Index

Complete Legacy → Backend → Frontend file map, source-code-verified 2026-06-08.

---

## Backend (`wecms-api`)

### Controllers — `domain/payment/internaltransfer/controller/`
| File | Role | Flow |
|---|---|---|
| `InternalTransferController.java` | `@RequestMapping("/api/v1/payment/internal-transfer")`, `@PreAuthorize("@menuAuthz.canAccess('payment-internal-transfer-scheduled')")`. Endpoints: list · withdrawal-accounts · detail · file download · approval-attachment download · next-doc-no · uploads/parse · create · PATCH reporting-account · rerun · dispatch · DELETE · request-approval (multipart) · DELETE approval (withdraw) · PUT approval-line · approval-action · banking-match (candidates/match/unmatch/unmatch-all). | 1,2,4 |
| `TransferResultController.java` | `@RequestMapping(".../results")`, `@PreAuthorize("…('payment-internal-transfer-result')")`. list · detail · export. | 3 |

### Services — `.../service/`
| File | Role | Flow |
|---|---|---|
| `InternalTransferService.java` | Core: `list`, `detail`, `createDraft`, `updateReportingAccount`, `rerun`, `delete`, `requestApproval`, `actOnApproval`, `withdrawApproval`, `changeApprovalLine`, `previewNextDocNo`, `listWithdrawalAccounts`, `parseExcel`, `resolveDownload`, `resolveApprovalAttachmentDownload`. | 1,2,4 |
| `InternalTransferResultService.java` | `list` (`searchDispatched`), `detail` (+ `DispatchResultResolver`, `countByStatus`). | 3 |
| `InternalTransferDispatchService.java` | `dispatch` — cut-off gate + `InternalTransferDispatchExecutor` + `TRNF_YN` stamp. | 2 |
| `InternalTransferDispatchExecutor.java` | Writes `TB_PAY_TARGET` (API path) / `TB_PAY_FILE_MST`+`TB_PAY_FILE_DATA` (file path); legacy routing rule. | 2 |
| `InternalTransferExcelParser.java` | Real POI workbook parser + per-cell validation + cross-row consistency. | 1 |
| `InternalTransferKeyGenerator.java` | `SCDUL_PYMN_NO` generator (`<yyyyMMdd><6-seq>`). | 1 |
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
| `ScdulPymnSumrRepository.java` | `findBy…ScdulPymnNoAndScdulPymnTypeCd`, `findBy…ApprSeqNoAndScdulPymnTypeCd`, `search`, `searchDispatched`, `findMaxApprSeqNoByBizKey`, `findCcyCdByBizKeyAndApprSeqNos`, `findOrgFileNmByBizKeyAndApprSeqNos`, `updateApprStts`, `clearApprSeqNo`. |
| `ScdulPymnSumrDetlRepository.java` | line read / delete-by-batch. |
| `ExptResolFileRepository.java` | approval attachments (`findByBizKeyAndApprSeqNoOrderByFileIdAsc`, `findByBizKeyAndApprSeqNoAndFileId`). |
| `InternalTransferKeySeqRepository.java` | key-sequence support. |
| `ScdulPymnCashFlowRow.java` | projection. |

### DTOs — `.../dto/`
Create/Upload: `CreateScheduledTransferRequest`, `TransferLineInput`, `CreateScheduledTransferResponse`, `ParseExcelResponse`, `ParsedTransferLine`, `WithdrawalAccountRow`, `NextDocNoResponse`.
Scheduled: `ScheduledTransferListResponse`, `ScheduledTransferRow`, `ScheduledTransferDetailResponse`, `TransferLineDetail`, `ScheduledTransferApprover`, `CurrencyTotal`, `UpdateReportingAccountRequest`, `DispatchRequest`.
Result: `TransferResultListResponse`, `TransferResultRow`, `TransferResultDetailResponse`, `TransferResultLine`.
Approval: `RequestApprovalRequest` (+ `ApproverInput`/`FileInput`), `RequestApprovalResponse`, `ApprovalActionRequest`, `ChangeApprovalLineRequest`, `ApprovalAttachmentRow`.

### Entities — `.../entity/`
`ScdulPymnSumr` (+`ScdulPymnSumrId`), `ScdulPymnSumrDetl` (+`ScdulPymnSumrDetlId`), `ExptResolFile` (+`ExptResolFileId`), `InternalTransferKeySeq` (+`InternalTransferKeySeqId`).

### Export — `.../export/`
`InternalTransferResultExportProvider.java` (`screenId='payment-internal-transfer-result'`).

### Cross-domain collaborators (referenced)
`approvalbox/service/ApprovalBoxService` (approve/reject chain), `ApprovalBoxFanoutService` (withdraw/change-line), `ApprDv`; `payment/service/DocNoGenerator`; `payment/common/{TransferLineDetailMapper, OutBankCdNormalizer, UploadConsistencyValidator}`; `payment/dispatch/service/DispatchResultResolver` + `dispatch/repository/TbPayFileDataRepository` + `DispatchCountRepository`; `payment/match/service/DomesticPaymentLineMatchService`; `setting/erptransactionhistory/service/ErpTrnsHistoryWriter`; `setting/chartofaccounts/repository/ChartOfAccountRepository`; `setting/firmbankingsettings/repository/TbFirmBankCodeRepository`; `banking/repository/{AcctMstrRepository, BankInfmRepository}`; `payment/validation/SpecialCharValidator`; `notification/service/NotificationService`; `auth/repository/BusiInfmRepository`; `common/auditlog/service/AuditLogService`; `common/tenant/TenantContext`; `navigation/service/MenuAuthorizationGuard`.

### Tests — `src/test/.../internaltransfer/`
`controller/InternalTransferControllerTest`, `controller/TransferResultControllerTest`, `service/CutoffValidatorTest`, `service/InternalTransferExcelParserTest`, `service/InternalTransferKeyGeneratorTest`, `service/InternalTransferSortFieldTest`.

---

## Frontend (`wecms-web`)

### Routes — `app/(dashboard)/payment/internal-transfer/`
`scheduled-transfer/page.tsx` + `scheduled-transfer/[id]/page.tsx`; `transfer-result/page.tsx` + `transfer-result/[id]/page.tsx`.

### Components — `components/payment/internal-transfer/`
`ScheduledTransferPage.tsx` (Flow 2 list + Flow 4 Request-Approval/Approval-Details), `ScheduledTransferDetailPage.tsx` (Flow 2 detail), `TransferResultPage.tsx` (Flow 3 list), `TransferResultDetailPage.tsx` (Flow 3 detail), `InternalTransferUploadDialog.tsx` (Flow 1), `types.ts`.

Shared (`components/payment/shared/`): `PaymentScheduledBatchListPage`, `PaymentResultBatchListPage`, `PaymentDetailPage`, `ResultDetailView`, `PaymentExcelUploadDialog`, `RequestApprovalDialog`, `ApprovalDetailsDialog` (+ `chainToDialogApprovers`/`isCurrentUserActiveApprover`), `DetailSummary`, `DetailHeader`, `DetailStatusTabs`, `FileCard`, `detailColumns.tsx`, `deriveResultStatus.ts`, `PaymentResultBadge`, `WithdrawalAccountDropdown`. Plus `components/payment/corporate-card/EditApprovalLinePicker`, `components/payment/approval-box/StatusBadge`/`types`.

### API client — `lib/api/internalTransfer.ts`
`fetchScheduledTransferList`, `fetchScheduledTransferDetail`, `fetchNextInternalTransferDocNo`, `withdrawInternalTransferApproval`, `changeInternalTransferApprovalLine`, `downloadOriginalExcel`, `downloadApprovalAttachment`, `parseAttachmentFilename`, `fetchWithdrawalAccounts`, `parseInternalTransferExcel`, `createScheduledTransfer`, `updateReportingAccount`, `rerunTransferLine`, `deleteScheduledTransfer`, `dispatchScheduledTransfer`, `submitInternalTransferApproval`, `actOnInternalTransferApproval`, `fetchTransferResultList`, `fetchTransferResultDetail`, banking-match fns. Plus `lib/api/approval-box.ts` `fetchApprovalBoxApprovers`; `lib/api/mappers.ts` `mapScheduledTransferList`/`mapScheduledTransferDetail`.

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
| `pymn_0010_002.ts:441-472` | Client TS | `rerun` |
| `comm_1001_001` | WSVC | `listWithdrawalAccounts` |
| `comm_0194_001` → `comm_0192_001_c005` | Approval submit | `requestApproval` |
| `comm_0195_001` (footer Approve/Reject → `bltb_0001_003_u001`; kebab Delete; edit-mode) | Approval Details popup | `actOnApproval` / `withdrawApproval` / `changeApprovalLine` |
| WSVC XML: `WSVC.pymn_0009_001_r001/_c001/_d001`, `pymn_0009_002_r001/_c001/_u002`, `pymn_0010_001_r001/_002_r001` (under `WECMS_ETC/xml/service/WSVC/`) | Service contracts | (validation lengths, baseline §6) |
| Entities `ScdulPymnSumr`, `ScdulPymnSumrDetl`, `TbPayTarget`, `TbPayFileMst`, `TbPayFileData` | JEX entities | refactor `entity/*` + `payment/dispatch/entity/*` |

> Permission matrix: `comm_0190_003_view.jsp:530-547` — `pymn_0009_001` = R/U/D/T, `pymn_0010_001` = R only.
