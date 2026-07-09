# Corporate Card — Business Flow (Card Transactions)

End-to-end flow for every Card Transactions operation, separated **Legacy JEX → Spring → React**. The kebab slug `card-transactions` is canonical across all three; legacy WSVC ids appear only as source-of-truth pointers.

> Scope: a single sub-module, **Card Transactions**. Scraping the raw `CARD_TRNS_HSTR` rows from the bank stays a coexistence carve-out (legacy batch) for the headless path, but the refactor ships a session/challenge-aware scrape (`/sync-session`) for the Update button.

---

## 1 · Card management (list / activate / rename / delete / bulk-create)

**Legacy.** `card_0001_003_r001` lists `CARD_MSTR` per `(BIZ_KEY)`; `_u001` toggles `ACTV_YN`; `_u002` renames `CARD_HLDR_NM`/`CARD_NICK_NM`; `_d001` hard-deletes (in-use guard via `CARD_TRNS_HSTR`). `comm_0218_007_c001` registers scraped cards.

**Spring.** `CardTransactionsService.listManagementCards / updateCardActive / updateCardInfo / deleteCard / createCards`. Delete runs the in-use guard (`CardTrnsHstrRepository.existsBy…`) → `WBE1153`. Bulk-create dedupes on PK `(BIZ_KEY, BANK_CD, CARD_NO)`, returns `inserted_count` + `skipped_count`. Tenant from `TenantContext.currentBizKey()`.

**React.** `CardManagementDialog` (list/activate/rename/delete), `AddCardFlowDialog` + `SelectCardsDialog` (NXISAS scrape → tick → `createScrapedCards`).

---

## 2 · Scrape session + challenge (Update Cards)

**Legacy.** `nxisas_cardTranList` runs the Coocon client `LOGIN → challenge? → CARD_USING` per card; `nxisas_cardTranList_r001` returns per-bank scrape-window limits (`BANK_INFM_DETL` codes 26/27).

**Spring.**
- `POST /sync` (`CardTransactionSyncService`) — **@Deprecated** headless single-shot; aborts QNA/OTP/CAPTCHA banks with `WBE_CARD_SYNC_NEEDS_RE_AUTH`.
- `POST /sync-session` (`CardTransactionScrapeService`) — session/challenge-aware start; reuses the shared NXISAS session framework (same one Account Inquiry sync + Login Credentials use). Returns `*_REQUIRED` + a `session_id` on a challenge.
- `POST /sync-session/{sessionId}/{qna,otp,captcha}` — resume after a challenge.
- `POST /period-limits` (`CardTranListR001Service`) — per-bank window lookup; falls back to legacy default (90 days) when unseeded.

Both scrape paths persist into `CARD_TRNS_HSTR` via `CardTransactionPersister`.

**React.** `UpdateCardsDialog` drives the session/challenge loop (`startCardSyncSession` → `submitCardSyncQna/Otp/Captcha`); `fetchCardTranListPeriodLimits` seeds the date-range validator.

---

## 3 · Sync card transactions → ledger read

**Legacy.** `card_0001_001_r001` reads `CARD_TRNS_HSTR` LEFT JOIN `CARD_MSTR`, paged + filtered (date range required, bank/card allow-lists, keyword, link status), with `wb_get_prch_stts_nm` status labels and `CARD_TRNS_COUNT_R001` totals.

**Spring.** `CardTransactionsService.list(...)` returns `CardTransactionListResponse` = `{ page, page_size, total_count, totals, rows[] }`. `toRow(...)` ships `List.of()` for `approvers[]` on every ledger row; `stts` raw + `stts_key` i18n key (replaces `wb_get_prch_stts_nm`); `bank_nm` via `BankCdResolver` (not `wb_get_bank_nm`); `linked_docs[]` via `resolveLinkedDocs`. Sort whitelist `CardTrnsHstrSortField`; default `TRSC_DT DESC, TRSC_TM DESC, CARD_SEQ_NO DESC`.

**React.** `CardTransactionsPage` (`useApiData(fetchCardTransactions, …)` + `refreshKey`), `CardTransactionsTable`, `CardSummaryCards` (TOTAL / APPROVAL REQUESTED / APPROVAL NOT REQUESTED), `CardTransactionsFilterBar`. Display names resolved client-side from the chart-of-accounts tree + purpose-options keyed by raw codes (APPROVED DEVIATION).

---

## 4 · Request approval (multipart) + withdraw + change-line

**Legacy.** `card_0001_001.ts` routed every Request Approval click through `comm_0194_001.act` with `APPR_DV='33'` (CORPORATE_CARD); approval details rendered via `comm_0195_001`.

**Spring.** `POST /request-approval` is **multipart** (`request` JSON + `attachments`). `CardTransactionsService.requestApproval` stamps `APPR_SEQ_NO` on selected rows and fans out via `ApprovalBoxFanoutService` / `ApprovalNotificationService`:
`EXPT_RESOL_MSTR` + `EXPT_RESOL_DETL` + `EXPT_RESOL_APPRVR_INFM` + `EXPT_RESOL_FILE` + the BLTB bulletin/notification tables, with `APPR_DV='33'` (`ApprDv.CORPORATE_CARD`). `APPR_SEQ_NO` allocated through the `wb_get_next_val` funnel (`LegacyFunctionRepository`).
- `GET /next-doc-no` — preview `VO_YYMMDD_NNN` (Card has no parent batch row).
- `DELETE /by-approval/{apprSeqNo}` — withdraw pending approval (delete master + approvers, clear `CARD_TRNS_HSTR.APPR_SEQ_NO`).
- `PUT /by-approval/{apprSeqNo}/approval-line` — change approval line on a pending approval.
- `GET /by-approval/{apprSeqNo}` — card txns stamped with an `APPR_SEQ_NO` (Approval Details popup grid).

**React.** `CardTransactionsFilterBar` Request Approval CTA + shared `RequestApprovalDialog`; `ApprovalRefDetailDialog` wraps `<ApprovalDetailsDialog kind="card">` (withdraw / change-line); `EditApprovalLinePicker`.

> **Write-path ownership (resolved):** Card OWNS the request-approval write-path. OQ-1's "defer to expense-resolution slug" default was superseded. The screen still does not own its own approval *line* — it picks an existing `APPR_LINE_NO`.

---

## 5 · Link card payments + receipts + purpose options + memo

**Legacy.** `card_0001_002_r001/r002` list linkable GDT bills / receipts; `_c001` links (`SUM(dedu_amt) == TRSC_AMT` guard); `_u001` unlinks. `prch_0011_001` creates a manual receipt + supplies the USAG combo. Memo via `MEMO_INFM_R001/C001/D001` + `wb_get_memo_cont`/`wb_get_memo_color`.

**Spring.**
- `GET /links/e-invoices` — `DOC_TYPE='B'`, `HAND_REG_YN='N'`, `PROV_STTS NOT IN ('EE','VD')`, in window, not already linked.
- `GET /links/receipts` — `DOC_TYPE='E'`, `HAND_REG_YN='Y'`, by `USE_DT`.
- `POST /links` — `linkExpenses` writes 1 `CARD_TRNS_RCV_PAY_INFM` + N `BUSA_DOC_RCV_PAY_HIS` (`RCV_PAY_WAY_CD='R'`); `SUM(dedu_amt) == TRSC_AMT` exact → else `WBE_LINK_AMOUNT_MISMATCH`.
- `DELETE /links/{cardSeqNo}` — `unlinkExpenses` (idempotent).
- `POST /receipts` — `createReceipt` writes `BUSA_DOC_MSTR` (`DOC_TYPE='E'`, `HAND_REG_YN='Y'`), returns new `doc_id`.
- `GET /receipts/{docId}` — read-only receipt/bill detail.
- `GET /purpose-options` — `USAG_INFM` + `USAG_INFM_LANG_DETL`, `grp_cd` default `6`, localized `usag_nm`.
- `GET/POST/DELETE /memos` — `MemoInfm` CRUD; delete restricted to author → `WBE_MEMO_NOT_AUTHOR`. `MEMO_INFM.SEQ_NO` via Java MAX+1 (documented `wb_get_next_val` exception — table not in PROD active list).

**React.** `LinkCardPaymentsDialog` (e-Invoice + Receipt tabs), `NewReceiptDialog`, `ReceiptDetailDialog`, `MemoPanel` (side drawer), `ReportingAccountPickerDialog`.

---

## 6 · Reporting-account clear / classify

**Legacy.** `card_0001_001_u001` sets the description / purpose; `card_0001_001_u002` sets the reporting account (and clears it).

**Spring.**
- `PATCH /{cardSeqNo}` — discriminated `{field:"description"|"purpose", value}`.
- `PUT /{cardSeqNo}/reporting-account` — set `chart_acct_seq_no` + `chart_acct_cd`, validated against `CHART_ACCT_INFM` → else `WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID`.
- `DELETE /{cardSeqNo}/reporting-account` — clear (refactor addition; idempotent).

**React.** Inline `EditableTextCell` (description) + code-cell (purpose) in `CardTransactionsTable`; `ReportingAccountPickerDialog` (chart-of-accounts tree).

---

## Audit write-path

Legacy `card_0001_001*` writes **zero** `AUDIT_LOGS_INFM` rows. The refactor follows the chart-of-accounts / firm-banking-settings precedent: state-changing endpoints write one `AuditLogService.append(...)` row under `WSVC_ID = "card-transactions"` (slug). Additive divergence (OQ-2 adopted).
