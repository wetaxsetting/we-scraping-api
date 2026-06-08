# Transaction History (FCY) — Source Code Index

Legacy JEX ↔ refactor backend ↔ refactor frontend file index for the FCY transaction inquiry. FCY
shares almost all code with the VND twin — the rows below mark where FCY-specific identifiers exist.

---

## 1. Legacy JEX (`fnsh_0006_001`, VND twin `fnsh_0002_001`)

All under `WECMS DEV/`. Read-only reference.

| Layer | File |
|---|---|
| Grid view JSP | `WECMS/web/view/jex/wabooks/fnsh/fnsh_0006_001_view.jsp` (structurally identical to `fnsh_0002_001_view.jsp`) |
| Grid read action | `WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0006_001_r001_act.jsp` (passes `CcyCd.fromValue(input.getString("CCY_CD"))`, tag `"fnsh_0006_001"`) |
| Detail popup | `…/fnsh_0006_003.act` (detail/edit + classification) |
| Manual-entry popup (NOT the grid) | `…/fnsh_0006_002_*` (reuses `fnsh_0002_002_*` helpers with `CcyCd.USD`) |
| TS client | `WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0002_001.ts` (shared client shape for `fnsh_0006_001.ts`) |
| Shared helper | `WECMS/src/wabooks/helper/FnshHelper.java` — `fnsh_0002_001_r001(...)` (read), `fnsh_0002_001_r001_dynamic0/1` (WHERE incl. `ATH.ccy_cd = ?`), `fnsh_0002_002_c001` (manual create) |
| Grid IDO | `IDO.ACCT_TRNS_HSTR_R016.xml` (rows) + `IDO.ACCT_TRNS_HSTR_R016_CNT.xml` (totals) |
| WSVC | `WSVC.fnsh_0002_001_r001.xml` (`fnsh_0006_001_r001` delegates to the same helper) |
| Menu code | `WECMS_ETC/xml/code/FNSHU_MENU/CD_fnsh_0006_001.xml` (FCY/USD menu group `FNSHU`; VND twin under `FNSHK`) |

> **The two FCY discriminators in legacy:** (1) `ATH.ccy_cd = :ccy` where `:ccy` = `CCY_CD` from the
> request input (vs VND's hard-wired local currency); (2) the dynamic WHERE restricts the account-type
> set to USD/foreign types (`04`/`06`) vs VND's `03`/`05`. The refactor keeps discriminator (1) via the
> `ACCT_MSTR.CCY_CD`-derived in-memory filter; discriminator (2) is not separately re-implemented on
> the transaction path (the account is already resolved by `acctSeqNo`).

---

## 2. Refactor backend (`wecms-api/`)

All under `src/main/java/com/kosign/wecms/`.

| Layer | Class | Path |
|---|---|---|
| Controller | `AccountController` | `domain/banking/controller/AccountController.java` |
| Service | `TransactionService` | `domain/banking/service/TransactionService.java` |
| Match service (Smart Organizer hand-off) | `MatchService` | `domain/banking/service/MatchService.java` |
| Per-account authz guard (#29) | `AccountAccessGuard` | `domain/banking/service/AccountAccessGuard.java` |
| Amount formatter | `AmountFormatter` | `domain/banking/service/AmountFormatter.java` |
| Inout enum | `InoutDv` | `common/enums/InoutDv.java` |
| Repo (transactions) | `AcctTrnsHstrRepository` | `domain/banking/repository/AcctTrnsHstrRepository.java` |
| Repo (rcv/pay confirm+classify) | `AcctTrnsRcvPayInfmRepository` | `domain/banking/repository/AcctTrnsRcvPayInfmRepository.java` |
| Repo (currency recovery — FCY) | `AcctMstrRepository` (`findByBizKeyAndBankCdAndAcctNo`) | `domain/banking/repository/AcctMstrRepository.java` |
| Repo (match metadata — BUG-2) | `BusiMatchLogRepository` | `domain/banking/repository/BusiMatchLogRepository.java` |
| Repo (per-user authority — #29) | `AcctAuthInfoRepository` | `domain/setting/bankaccount/repository/AcctAuthInfoRepository.java` |
| Entity (transaction) | `AcctTrnsHstr` (+`AcctTrnsHstrId`) | `domain/banking/entity/AcctTrnsHstr.java` |
| Entity (rcv/pay) | `AcctTrnsRcvPayInfm` (+id) | `domain/banking/entity/AcctTrnsRcvPayInfm.java` |
| Entity (account master — `CCY_CD`) | `AcctMstr` (+id) | `domain/banking/entity/AcctMstr.java` |
| Entity (match log) | `BusiMatchLog` (+id) | `domain/banking/entity/BusiMatchLog.java` |
| Entity (sync audit — sibling) | `ExtTranCallHis` (+id) | `domain/banking/synchistory/entity/ExtTranCallHis.java` |
| DTO (list) | `TransactionListResponse`, `TransactionRow` | `domain/banking/dto/` |
| DTO (detail/summary) | `TransactionDetailResponse`, `TransactionSummaryResponse` | `domain/banking/dto/` |
| DTO (requests) | `TransactionConfirmRequest`, `TransactionReportingAccountRequest`, `TransactionPurposeRequest`, `TransactionVasAccountRequest`, `TransactionContactRequest` | `domain/banking/dto/` |
| Export provider (shared VND+FCY) | `TransactionHistoryExportProvider` | `domain/banking/export/TransactionHistoryExportProvider.java` |
| Sync orchestrator (sibling) | `BankTransactionSyncService` | `domain/banking/nxisas/BankTransactionSyncService.java` |
| Sync-history writer/read (sibling) | `SyncHistoryWriter`, `SyncHistoryService`, `SyncHistoryController` | `domain/banking/synchistory/` |
| View Setting seed | V155 → V169 / V171 / V167 / V181 | `src/main/resources/db/migration/` |

---

## 3. Refactor frontend (`wecms-web/`)

| Layer | File |
|---|---|
| Route (index → first account) | `app/(dashboard)/banking/transaction-history-fcy/page.tsx` (`<TransactionHistoryIndexRedirect scope="FCY" />`) |
| Route (per-account) | `app/(dashboard)/banking/transaction-history-fcy/[accountId]/page.tsx` (`<TransactionHistoryClient acctSeqNo={accountId} scope="FCY" />`) |
| Client wrapper (shared) | `components/banking/transaction-history/TransactionHistoryClient.tsx` |
| Page (presentation, shared) | `components/banking/transaction-history/TransactionHistoryPage.tsx` |
| Grid + registry | `TransactionTable.tsx`, `TransactionTableColumns.tsx`, `TransactionTableFooter.tsx`, `columnRegistry.tsx` |
| Filters / toolbar | `TransactionTypeFilter.tsx`, `TransactionStatusTabs.tsx`, `TransactionHistoryActions.tsx`, `SmartOrganizerButton.tsx` |
| Accounts source | `components/banking/transaction-history/accounts.ts` |
| API client | `lib/api/banking.ts` (`fetchTransactions`, `fetchTransactionsSummary`, `fetchTransactionDetail`, confirm/classify, export via `useExcelExport`) |
| Wire types | `lib/types/api.ts` (`ApiTransactionRow`, `ApiTransactionListResponse`, `ApiTransactionDetailResponse`, …) |
| Mappers | `lib/api/mappers.ts` (`mapTransactionRow`, `mapTransactionDetail`, `mapTransactionSummary`) |
| i18n | `locales/{en,ko,vi}.json` prefix `txn_history.*` (incl. `title_fcy`, `empty_fcy`) |

Frontend detail: [wecms-web/docs/features/banking/transaction-history-fcy/component-index.md](../../../../../wecms-web/docs/features/banking/transaction-history-fcy/component-index.md).

---

## 4. ID correspondence

| Surface | Value |
|---|---|
| Legacy JEX grid screen | `fnsh_0006_001` (VND twin `fnsh_0002_001`) |
| Legacy menu group | `FNSHU_MENU` (FCY/USD) |
| Refactor feature slug | `transaction-history-fcy` |
| Next.js route | `/banking/transaction-history-fcy/[accountId]` |
| REST base | `/api/v1/banking/accounts/{acctSeqNo}/transactions` |
| Menu permission | `banking-fcy-inquire-transactions` |
| View Setting `WEB_SVC_ID` | `banking-transaction-history` (shared VND + FCY) |
| Export screenId | `banking-transaction-history` |
