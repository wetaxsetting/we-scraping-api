# Overseas Payment — Source Code Index

> Complete Legacy → Backend → Frontend file map. Source-code-verified 2026-06-23. Hub: [README.md](README.md).

## Backend (`wecms-api`)

### Controller — `domain/payment/controller/`
- `OverseasPaymentController.java` — `@RequestMapping("/api/v1/payment/overseas-payment")`, class authz `payment-overseas-request`; `/result*` override `payment-overseas-result`. 19 mappings (scheduled list/by-id/details/file · excel-preview/upload · request-approval (multipart)/request-approval-multi · change/remove approval-line · delete-draft · approval-action · dispatch · reporting-account · withdrawal-accounts · result list/by-id · result list-export/per-batch-export).

### Services — `domain/payment/service/`
- `OverseasPaymentService.java` — core: `list`, `getById`, `getDetail`, `resolveLegacyMmodeOrThrow`, `listResult`, `getResultById`, `exportResult`, `requestApproval`, `requestApprovalMulti`, `stampApproval`/`stampApprovalMulti`, `actOnApproval`, `dispatch`, `changeApprovalLine`, `removeApprovalLine`, `deleteDraftBatch`, `saveReportingAccount`, `deriveApprStts`/`mapApprTypeCd`, `mapProcStatToStatusLetters`, mappers.
- `OverseasPaymentExcelService.java` — `preview`, `submit`, `resolveDownload` (+ `DownloadResource`). `MIN_COLUMNS=6`, `MAX_ROWS=500`, ERP code `14`.
- `OverseasPaymentResultExcelWriter.java` — 22-column list-export workbook (XSSF; merged title/summary rows); `ExportRow` record + static `write(...)`.
- `OverseasScheduledSortField.java` / `OverseasResultSortField.java` — ORDER BY whitelists (V232 / V235 + pre-version backwards-compat constants; `parse(...)` lenient).

### Overseas helper package — `domain/payment/overseas/`
- `service/OverseasDispatchExecutor.java` — File-path (SWIFT) dispatch: writes `TB_PAY_FILE_MST` + `TB_PAY_FILE_DATA` per line, fee-burden/country/BIC mapping, `resolveBankPrtnId` (`WBE2089`), `@Transactional(MANDATORY)`.
- `service/OverseasPaymentKeyGenerator.java` — `nextOvrsPymnNo` → `wb_get_next_val` funnel (per-tenant MAX+1 over `OVRS_PYMN_MSTR ∪ OVRS_PYMN_SUMR`).
- `service/OverseasPaymentFileResolver.java` + `OverseasPaymentStorageProperties.java` — per-tenant storage root (`<root>/<biz_key>/overseas-payment/`), path-traversal guard, legacy-flat-root read fallback.
- `service/OverseasPaymentConfig.java` — `@EnableConfigurationProperties` wiring.
- `export/OverseasPaymentResultDetailExportProvider.java` — per-batch export provider, screenId `payment-overseas-payment-result-detail`, allowed FIELD_IDs `BNFC_BANK_NM, BNFC_ACCT_NO, BNFC_NM, CCY_CD, TRNF_AMT, MEMO, TRNF_YN`.
- `repository/OvrsPymnSumrRepository.java` — `findByBizKeyAndOvrsPymnNo`, `findByBizKeyAndApprSeqNo`, `findCcyCdByBizKeyAndApprSeqNos`, `findOrgFileNmByBizKeyAndApprSeqNos`, `clearApprSeqNo`.
- `repository/OvrsPymnMstrRepository.java` — `findByBizKeyAndApprSeqNo`, `findByBizKeyAndOvrsPymnNo`, `findCashFlowSourceRows`.
- `repository/OvrsPymnDtlsRepository.java` — `findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc`, `findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo`, `deleteByBizKeyAndOvrsPymnNo` (maps `OvrsPymnSumrDetl` → `OVRS_PYMN_SUMR_DETL`).

### Repositories — `domain/payment/repository/`
- `OverseasPaymentRepository.java` — native SQL builder: `findScheduled`/`countScheduled`/`sumByCurrency`/`findScheduledById`, `findResult`/`countResult`/`sumResultByCurrency`/`findResultById`/`findResultForExport`, `findMmodeDetailRows`; private `buildUnionSql`/`buildResultSql`.
- `OverseasApproverRepository.java` — `findByApprSeqNos(bizKey, apprSeqNos)` (native, grouped by `appr_seq_no`).

### Entities — `domain/payment/entity/`
- `OvrsPymnSumr` (+`OvrsPymnSumrId`) → `OVRS_PYMN_SUMR`; `OvrsPymnSumrDetl` (+`OvrsPymnSumrDetlId`) → `OVRS_PYMN_SUMR_DETL`; `OvrsPymnMstr` (+`OvrsPymnMstrId`) → `OVRS_PYMN_MSTR`. Approval-box domain: `ExptResolMstr`, `ExptResolApprvrInfm`, `ExptResolDetl`, `ExptResolFile`.

### DTOs — `domain/payment/dto/`
`OverseasScheduledListResponse`, `OverseasScheduledRow`, `OverseasResultListResponse`, `OverseasResultRow`, `OverseasApprover`, `OverseasCurrencyTotal`, `OverseasMultiApprovalRequest`, `OverseasPaymentDetailResponse`, `OverseasPaymentDetailRowResponse`, `OverseasPaymentExcelPreview`, `OverseasPaymentExcelPreviewRow`, `OverseasPaymentExcelUploadRequest`, `OverseasPaymentExcelUploadResult`, `OverseasPaymentExcelDetailRow`, `OverseasPaymentExcelFieldError`, `OverseasMatchCandidateRow`. Reused: `ChangeApprovalLineRequest`, `internaltransfer.dto.{ApprovalActionRequest, RequestApprovalRequest, RequestApprovalResponse, ApprovalAttachmentRow, WithdrawalAccountRow}`.

### Cross-domain collaborators
`approvalbox/service/{ApprovalBoxFanoutService, ApprovalDisplayFlagsResolver, ApprovalAttachmentWriter, ApprDv}`; `approvalbox/repository/{ExptResolMstrRepository, ExptResolApprvrInfmRepository}`; `internaltransfer/service/{ApprovalAttachmentLoader, CutoffValidator, TransferNetwork}`; `payment/common/{WithdrawalAccountListService, OutBankCdNormalizer}`; `payment/service/DocNoGenerator`; `payment/dispatch/service/DispatchResultResolver`; `setting/erptransactionhistory/service/ErpTrnsHistoryWriter`; `common/legacy/LegacyFunctionRepository`; `payment/notification/service/ApprovalNotificationService` (BLTB); `auth/repository/BusiInfmRepository`; `common/auditlog/service/AuditLogService`; `common/tenant/TenantContext`; `common/export/ExcelExportService`.

### Tests — `src/test/.../payment/`
- `service/OverseasPaymentDetailTest` — S-mode attachments + M-mode real-row render + DOC_NO fallback + `WBE2045` not-found.
- `service/OverseasPaymentResultApprSeqNoTest` — F4b appr_seq_no surfaced / null-for-draft.
- `service/OverseasPaymentResultStatusTest` — counts + computed status, Completed→{C,F}, In-Progress→{P}, null filter, footer never filtered, row mapping.
- `service/OverseasPaymentResultExportAuditTest` — 'W' row code 14; M-mode export line.
- `service/OverseasPaymentResultDownloadAuditTest` — dispatched writes 'W', non-dispatched skips.
- `service/OverseasPaymentRequestApprovalBackstampTest` — H-1 withdrawal back-stamp.
- `overseas/service/OverseasDispatchExecutorAutoReceiveTest` — file-path auto-receive registration.
- `overseas/service/OverseasDispatchExecutorBankPrtnIdTest` — BANK_PRTN_ID read, FUND_TP='S', `WBE2089` blocks.

---

## Frontend (`wecms-web`)

### Routes — `app/(dashboard)/payment/overseas-payment/`
`scheduled-payment/page.tsx` → `<OverseasScheduledPaymentPage>`; `scheduled-payment/[id]/page.tsx` → `<OverseasScheduledPaymentDetailPage>`; `payment-result/page.tsx` → `<OverseasPaymentResultPage>`; `payment-result/[id]/page.tsx` → `<OverseasPaymentResultDetailPage>`; `exchange-rate-inquiry/page.tsx` → `<ExchangeRateInquiryPage>` (documented separately).

### Components — `components/payment/overseas-payment/`
`ScheduledPaymentPage.tsx` (→ shared `PaymentScheduledBatchListPage`), `ScheduledPaymentDetailPage.tsx` (→ `PaymentDetailPage`), `PaymentResultPage.tsx` (→ `PaymentResultBatchListPage`), `PaymentResultDetailPage.tsx` (→ `ResultDetailView`), `ExchangeRateInquiryPage.tsx` (standalone), `OverseasExcelUploadModal.tsx`, `ChangeApproverDialog.tsx`, `types.ts`. Shared: `RequestApprovalDialog`, `ApprovalDetailsDialog`, `VendorExpenseMatchDialog` (banking match), `ViewSettingPopover`.

### API client — `lib/api/payment.ts`
`fetchOverseasScheduledList`, `fetchOverseasScheduledById`, `fetchOverseasPaymentDetail`, `fetchOverseasWithdrawalAccounts`, `fetchOverseasResultList`, `fetchOverseasResultById`, `fetchOverseasMatchCandidates`, `submitOverseasPaymentExcel`, `downloadOverseasPaymentFile`, `exportOverseasResultList`, `matchOverseasPaymentLine`, `unmatchOverseasPaymentLine`, `unmatchAllOverseasPaymentLines`, `dispatchOverseasPayment`, `requestOverseasApproval`, `changeOverseasApprovalLine`, `removeOverseasApprovalLine`, `deleteOverseasDraftBatch`, `actOnOverseasApproval`, `saveOverseasReportingAccount`. FX functions live in `lib/api/banking.ts` (`fetchExchangeRates`, `fetchExchangeRateBanks`, `fetchExchangeRateCurrencies`).

### Mappers / types — `lib/api/mappers.ts`, `lib/types/api.ts`
`ApiOverseasScheduledRow`, `ApiOverseasScheduledListResponse`, `ApiOverseasResultRow`, `ApiOverseasResultListResponse`, `ApiOverseasPaymentDetailResponse`, `ApiOverseasPaymentExcelDetailRow`, `ApiOverseasApprover`, `ApiOverseasCurrencyTotal`. In-memory types in `components/payment/overseas-payment/types.ts`.

### View Setting screen IDs — `lib/viewSettingScreens.ts`
`PAYMENT_OVERSEAS_PAYMENT_SCHEDULED` (`payment-overseas-payment-scheduled`), `_SCHEDULED_DETAIL`, `_RESULT`, `_RESULT_DETAIL` + matching `SCREEN_ID_TO_ROUTE`.

### Hooks
`useApiData`, `useTableSort`, `useViewSetting`, `useBlockedChars`, `useExcelExport`, `useStickyLoading`, `useDialog`, `useTranslation`.

### Tests
None — no `*.test.*` for overseas screens (G-OVRS-4).

---

## Legacy (JEX, read-only `WECMS DEV/`)
See [migration-guide.md](migration-guide.md) for the full WSVC → refactor table.
