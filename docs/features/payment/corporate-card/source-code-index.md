# Corporate Card — Source Code Index (Card Transactions)

File-level index for the Card Transactions sub-module. Paths are repo-relative.

## Backend — `wecms-api/src/main/java/com/kosign/wecms/domain/payment/cardtransactions/`

### Controller (~30 mappings)
[`controller/CardTransactionsController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/controller/CardTransactionsController.java) — class-level `@PreAuthorize("@menuAuthz.canAccess('payment-corporate-card-transactions')")`. Mappings: `list` · `listByApproval` · `withdrawApproval` · `changeApprovalLine` · `cards` · `patch` · `updateReportingAccount` · `clearReportingAccount` · `listManagementCards` · `updateCardActive` · `updateCardInfo` · `deleteCard` · `createCards` · `sync` (@Deprecated) · `startSyncSession` · `submitSyncSessionQna/Otp/Captcha` · `periodLimits` · `requestApproval` (multipart) · `exportCardTransactions` · `nextDocNo` · `listLinkableEInvoices` · `listLinkableReceipts` · `linkExpenses` · `unlinkExpenses` · `createReceipt` · `getReceiptDetail` · `purposeOptions` · `listMemos` · `createMemo` · `deleteMemo`.

### Services (5)
- [`service/CardTransactionsService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/service/CardTransactionsService.java) — core: list/classify/link/receipt/memo/request-approval/withdraw/change-line; `toRow` (`approvers[] = List.of()`), `resolveLinkedDocs`.
- [`service/CardTranListR001Service.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/service/CardTranListR001Service.java) — period-limit lookup.
- [`service/CardTransactionScrapeService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/service/CardTransactionScrapeService.java) — session/challenge scrape (`/sync-session`).
- [`service/CardTransactionSyncService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/service/CardTransactionSyncService.java) — deprecated headless `/sync`.
- [`service/CardTransactionPersister.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/service/CardTransactionPersister.java) — persists scraped rows.
- Sort fields: `service/CardTrnsHstrSortField.java`, `service/CardMstrSortField.java`.

### Entities
- [`entity/CardMstr.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/entity/CardMstr.java) (+ `CardMstrId`)
- `entity/CardTrnsHstr.java` (+ `CardTrnsHstrId`), `entity/CardTrnsRcvPayInfm.java` (+ `…Id`), `entity/BusaDocRcvPayHis.java` (+ `…Id`), `entity/MemoInfm.java` (+ `MemoInfmId`), `entity/UsagInfm.java`.

### DTOs (many)
`CardTransactionRow`, `CardTransactionListResponse`, `CardTransactionTotals`, `CardTransactionListFilters`, `CardTransactionPatchRequest`, `PatchDescriptionRequest`, `PatchPurposeRequest`, `UpdateReportingAccountRequest`, `ActiveCard`, `CardListResponse`, `CardManagementRow`, `CardManagementListResponse`, `UpdateCardActiveRequest`, `UpdateCardInfoRequest`, `CreateCardsRequest`/`Response`, `SyncCardTransactionsRequest`/`Response`, `CardSyncSessionResponse`, `CardTranListPeriodLimitRequest`/`Response`, `RequestApprovalRequest`/`Response`, `ChangeApprovalLineRequest`, `NextDocNoResponse`, `LinkableEInvoiceRow`/`ListResponse`, `LinkableReceiptRow`/`ListResponse`, `LinkExpensesRequest`/`Response`, `CreateReceiptRequest`/`Response`, `ReceiptDetailResponse`, `PurposeOption`, `CreateMemoRequest`, `MemoRow`, `MemoListResponse`, `LinkedDocRef`, `ApproverRef`.

### Export
[`export/CardTransactionsExportProvider.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/export/CardTransactionsExportProvider.java) — `screenId="card-transactions"`.

### Repositories
`CardTrnsHstrRepository`, `CardMstrRepository`, `CardTrnsRcvPayInfmRepository`, `BusaDocRcvPayHisRepository`, `MemoInfmRepository`, `UsagInfmRepository`.

### Tests (5 files / 44 methods)
`CardTransactionsControllerTest`, `CardTransactionsServiceTest`, `CardTransactionScrapeServiceTest`, `CardTransactionSyncServiceTest`, `CardTrnsHstrSortFieldTest`.

## Frontend — `wecms-web/`

- Page: [`app/(dashboard)/payment/corporate-card/card-transactions/page.tsx`](../../../../../wecms-web/app/(dashboard)/payment/corporate-card/card-transactions/page.tsx) — thin server component.
- Components: [`components/payment/corporate-card/`](../../../../../wecms-web/components/payment/corporate-card/) — `CardTransactionsPage`, `CardSummaryCards`, `CardTransactionsFilterBar`, `CardTransactionsTable`, `CardTransactionsTableColumns`, `ApprovalRefDetailDialog`, `LinkCardPaymentsDialog`, `NewReceiptDialog`, `ReceiptDetailDialog`, `ReportingAccountPickerDialog`, `MemoPanel`, `CardManagementDialog`, `AddCardFlowDialog`, `SelectCardsDialog`, `UpdateCardsDialog`, `EditApprovalLinePicker` (16 components), `cardTransactionMappers.ts`, `addCardFlowTypes.ts`, `mockData.ts` (present, not a runtime fallback), `types.ts`.
- API client: [`lib/api/cardTransactions.ts`](../../../../../wecms-web/lib/api/cardTransactions.ts) — 28 functions wrapping the full controller surface.
- Mappers: `components/payment/corporate-card/cardTransactionMappers.ts` (feature-local boundary mapper, not `lib/api/mappers.ts`).

> FE has no tests (G-CARD-5). See [test-scenarios.md](test-scenarios.md).
