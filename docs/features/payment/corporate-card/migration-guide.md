# Corporate Card — Migration Guide (Card Transactions)

Legacy JEX → Spring service mapping for the Card Transactions sub-module, plus approved deviations and dead-legacy notes. Slug `card-transactions`; legacy WSVC ids retained only as source-of-truth pointers.

---

## 1 · Legacy WSVC → controller / service

| Legacy WSVC | Refactor endpoint | Service method |
|---|---|---|
| `card_0001_001_r001` | `GET /` | `CardTransactionsService.list` |
| `card_0001_001_r002` | `GET /cards` | `CardTransactionsService.cards` |
| `card_0001_001_u001` | `PATCH /{cardSeqNo}` | `CardTransactionsService.patch` |
| `card_0001_001_u002` | `PUT /{cardSeqNo}/reporting-account` | `CardTransactionsService.updateReportingAccount` |
| (refactor addition) | `DELETE /{cardSeqNo}/reporting-account` | `clearReportingAccount` |
| (none — JEX client-side) | `POST /export` | `ExcelExportService` + `CardTransactionsExportProvider` |
| `card_0001_003_r001` | `GET /cards/management` | `listManagementCards` |
| `card_0001_003_u001` | `PATCH /cards/{bankCd}/{cardNo}/active` | `updateCardActive` |
| `card_0001_003_u002` | `PATCH /cards/{bankCd}/{cardNo}` | `updateCardInfo` |
| `card_0001_003_d001` | `DELETE /cards/{bankCd}/{cardNo}` | `deleteCard` |
| `comm_0218_007_c001` | `POST /cards` | `createCards` |
| `nxisas_cardTranList` | `POST /sync` (deprecated), `POST /sync-session` (+ resume) | `CardTransactionSyncService`, `CardTransactionScrapeService` |
| `nxisas_cardTranList_r001` | `POST /period-limits` | `CardTranListR001Service` |
| `card_0001_001`→`comm_0194_001` | `POST /request-approval` | `requestApproval` (+ `ApprovalBoxFanoutService`/`ApprovalNotificationService`) |
| `comm_0195_001` | `GET /by-approval/{apprSeqNo}` + `PUT .../approval-line` | `listByApprSeqNo`, `changeApprovalLine` |
| `comm_0192_004_d001` | `DELETE /by-approval/{apprSeqNo}` | `withdrawApproval` |
| (Card has no parent batch) | `GET /next-doc-no` | `previewNextDocNo` |
| `card_0001_002_r001` | `GET /links/e-invoices` | `listLinkableEInvoices` |
| `card_0001_002_r002` | `GET /links/receipts` | `listLinkableReceipts` |
| `card_0001_002_c001` | `POST /links` | `linkExpenses` |
| `card_0001_002_u001` | `DELETE /links/{cardSeqNo}` | `unlinkExpenses` |
| `prch_0011_001` | `POST /receipts`, `GET /purpose-options` | `createReceipt`, `listPurposeOptions` |
| `card_0001_001` `.detail-receipt` | `GET /receipts/{docId}` | `getReceiptDetail` |
| `MEMO_INFM_R001/C001/D001` | `GET/POST/DELETE /memos` | `listMemos`, `createMemo`, `deleteMemo` |

---

## 2 · Approved deviations

| ID | Deviation | Rationale |
|---|---|---|
| Display names resolved client-side | Ledger row ships raw codes only (`card_trns_usage_code`, `chart_acct_cd`); no `*_nm` columns | Frontend resolves from chart-of-accounts tree + purpose-options. `card_trns_usage_nm` needs the legacy `wb_get_usag_nm` resolver / USAG_INFM join — deferred as a PO decision. (`CardTransactionRow.java:61-67`) |
| `approvers[]` empty on ledger rows | `toRow` ships `List.of()` always | The approver chain surfaces only in the Approval Details popup (`GET /by-approval/{apprSeqNo}` + `<ApprovalDetailsDialog kind="card">`); ledger rows don't carry the chain. Intentional. |
| Audit ON | `AUDIT_LOGS_INFM.WSVC_ID = 'card-transactions'` | Legacy writes zero audit rows; refactor adds the hook per chart-of-accounts / firm-banking-settings precedent (OQ-2 adopted). Slug, not `card_0001_001`. |
| Card owns request-approval write-path | `POST /request-approval` lives here, not in a future `expense-resolution` slug | OQ-1 "defer" default superseded. Screen still picks an existing `APPR_LINE_NO`, not its own line. |
| `MEMO_INFM` Java MAX+1 | Memo key-gen bypasses `wb_get_next_val` | `MEMO_INFM` not in PROD active list; documented funnel exception. Baseline scope confirm = G-CARD-3 (PO). |

---

## 3 · Dead legacy

- `wb_get_prch_stts_nm` server-side status label → replaced by raw `stts` + `stts_key` i18n key.
- `wb_get_bank_nm` DB function → replaced by `BankCdResolver`.
- `wb_get_memo_cont` / `wb_get_memo_color` → memo CRUD reads `MEMO_INFM` directly.
- JEX client-side Excel export → server-side shared `ExcelExportService` pipeline.
- `setUpDataForPrint` per-page HTML template → shared `printReport` helper (frontend).

> Tables referenced by legacy but out of the active inventory get no entity (refactoring enforcement rule). `MEMO_INFM` is the lone exception currently shipped under a documented PO-pending deviation.
