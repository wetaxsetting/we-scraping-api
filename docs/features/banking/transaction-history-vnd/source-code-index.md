# Transaction History (VND) — Source Code Index

Legacy JEX ↔ refactor backend ↔ refactor frontend file map. Legacy paths are under `WECMS DEV/`
(read-only). The exhaustive legacy inventory with `file:line` pointers is the JEX source of truth:
[docs/jex/features/transaction-history-vnd/source-code-index.md](../../../../../docs/jex/features/transaction-history-vnd/source-code-index.md).

---

## 1. Legacy JEX (`fnsh_0002_001`, FCY twin `fnsh_0006_001`)

| Layer | File |
|---|---|
| Menu / code def | `WECMS DEV/WECMS_ETC/xml/code/FNSHK_MENU/CD_fnsh_0002_001.xml` |
| View JSP (VN/OSMC, primary) | `WECMS DEV/WECMS/web/view/jex/wabooks/fnsh/fnsh_0002_001_view_osmc.jsp` |
| View JSP (generic) | `WECMS DEV/WECMS/web/view/jex/wabooks/fnsh/fnsh_0002_001_view.jsp` |
| Client TS (VN, authoritative) | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0002_001_osmc_VN.ts` |
| Read service | `fnsh_0002_001_r001` (WSVC `WSVC.fnsh_0002_001_r001.xml`) |
| Write services | `fnsh_0002_001_c001`, `_u001`…`_u006`, `_d001` |
| Read helper | `FnshHelper.fnsh_0002_001_r001(...)` — `WECMS DEV/WECMS/src/wabooks/helper/FnshHelper.java:324-343` |
| Main read IDO | `IDO.ACCT_TRNS_HSTR_R016.xml` (list) + `ACCT_TRNS_HSTR_R016_CNT` (totals/counts) |
| Entity (legacy) | `WECMS DEV/WECMS/src/wabooks/entity/AcctTrnsHstr.java` (+ `AcctTrnsHstrPk.java`) |
| DB tables | `ACCT_TRNS_HSTR`, `ACCT_TRNS_RCV_PAY_INFM` (+ 14 joined/lookup tables) |

> **Not this feature:** the legacy `trns_0003_001` NAPAS transfer screen + `NAPAS_REMIT_TRNS_HSTR`
> table are the **transfer-history** feature (Own Account Transfer). Account transaction history is
> `fnsh_0002_001`. Bank synchronization is `fnsh_0001_001` (Account Inquiry) + the NXiSAS include
> `inc_wabooks_nxisas_v2.jsp`.

## 2. Refactor backend (`wecms-api/`)

| Layer | Class | Path |
|---|---|---|
| Controller | `AccountController` | `src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java` |
| Service | `TransactionService` | `src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java` |
| Repository (txn) | `AcctTrnsHstrRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctTrnsHstrRepository.java` |
| Repository (rcv/pay) | `AcctTrnsRcvPayInfmRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctTrnsRcvPayInfmRepository.java` |
| Entity | `AcctTrnsHstr` (+ `AcctTrnsHstrId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctTrnsHstr.java` |
| Entity | `AcctTrnsRcvPayInfm` (+ `AcctTrnsRcvPayInfmId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctTrnsRcvPayInfm.java` |
| Amount formatter | `AmountFormatter` | `src/main/java/com/kosign/wecms/domain/banking/service/AmountFormatter.java` |
| Inout enum | `InoutDv` | `src/main/java/com/kosign/wecms/common/enums/InoutDv.java` |
| Export provider | `TransactionHistoryExportProvider` | `src/main/java/com/kosign/wecms/domain/banking/export/TransactionHistoryExportProvider.java` |
| DTOs | `TransactionListResponse`, `TransactionRow`, `TransactionDetailResponse`, `TransactionSummaryResponse`, `TransactionConfirmRequest`, `TransactionReportingAccountRequest`, `TransactionPurposeRequest`, `TransactionVasAccountRequest`, `TransactionContactRequest` | `src/main/java/com/kosign/wecms/domain/banking/dto/` |
| Migrations | V155 / V167 / V169 / V171 / V181 (View Setting); V13 + parity fixes (schema) | `src/main/resources/db/migration/` |

## 3. Refactor frontend (`wecms-web/`)

See the frontend [source-code / component index](../../../../../wecms-web/docs/features/banking/transaction-history-vnd/component-index.md) for the full list; key files:

| Layer | File |
|---|---|
| Route (index → redirect) | `app/(dashboard)/banking/transaction-history/page.tsx` |
| Route (per-account) | `app/(dashboard)/banking/transaction-history/[accountId]/page.tsx` |
| Client wrapper (state/fetch) | `components/banking/transaction-history/TransactionHistoryClient.tsx` |
| Presentation | `components/banking/transaction-history/TransactionHistoryPage.tsx` |
| Index redirect | `components/banking/transaction-history/TransactionHistoryIndexRedirect.tsx` |
| Column registry | `components/banking/transaction-history/columnRegistry.tsx` |
| Types | `components/banking/transaction-history/types.ts` |
| API client | `lib/api/banking.ts` (`fetchTransactions`, `fetchTransactionsSummary`, confirm/undo, classify) |
| Screen registry | `lib/viewSettingScreens.ts` (`SCREEN_IDS.BANKING_TRANSACTION_HISTORY`) |

## 4. ID correspondence

| Surface | Value |
|---|---|
| JEX view id (VND) | `fnsh_0002_001` |
| JEX view id (FCY twin) | `fnsh_0006_001` |
| Refactor feature-folder slug | `transaction-history-vnd` (matches JEX + frontend) |
| Refactor route | `/banking/transaction-history` (`/transaction-history-fcy` for FCY) |
| View Setting screenId | `banking-transaction-history` (one id, VND + FCY) |
| REST base | `/api/v1/banking/accounts/{acctSeqNo}/transactions` |
| Export screenId | `banking-transaction-history` |
