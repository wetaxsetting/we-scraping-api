# Account Inquiry (VND) — Source Code Index

**Screen ID:** `banking-account-inquiry-vnd` · **JEX:** `fnsh_0001_001` · **Hub:** [README.md](README.md)

Legacy ↔ refactor-backend ↔ refactor-frontend file map. Legacy paths under `WECMS DEV/` are
**read-only** functional reference.

---

## 1. Legacy (JEX — read-only)

| Asset | Path |
|---|---|
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/fnsh/fnsh_0001_001_view.jsp` |
| Action — read list | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0001_001_r001_act.jsp` |
| Action — recent tx | `…/fnsh_0001_001_r002_act.jsp` |
| Action — all accounts (scrape) | `…/fnsh_0001_001_r003_act.jsp` |
| Action — sync ingest (NXiSAS) | `…/fnsh_0001_001_c001_act.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0001_001.ts` |
| WSVC — read | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.fnsh_0001_001_r001.xml` |
| WSVC — sync | `…/WSVC.fnsh_0001_001_c001.xml` |
| IDO — list | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_MSTR_R001.xml` |
| IDO — totals | `…/IDO.ACCT_MSTR_R002.xml` |
| IDO — currency totals + cert join | `…/IDO.ACCT_MSTR_R001_CNT.xml` |
| IDO — balance/sync update | `…/IDO.ACCT_MSTR_U002.xml` |
| Helper (read/dynamic) | `WECMS DEV/WECMS/src/wabooks/helper/FnshHelper.java` |
| Helper (sort) | `WECMS DEV/WECMS/src/wabooks/helper/SortHelper.java` (`#86` default order) |
| Helper (NXiSAS) | `WECMS DEV/WECMS/src/wabooks/helper/IsasHelper.java` |
| Entity | `WECMS DEV/WECMS/src/wabooks/entity/AcctMstr.java` |
| Live DEV URL | `https://dev.wecms.vn/fnsh_0001_001.act` |

---

## 2. Refactor backend (`wecms-api`)

| Layer | File (under `src/main/java/com/kosign/wecms/`) |
|---|---|
| Controller | `domain/banking/controller/AccountController.java` |
| Service (list/detail/lookup/sync delegate) | `domain/banking/service/AccountService.java` |
| Repository | `domain/banking/repository/AcctMstrRepository.java` |
| Entity / id | `domain/banking/entity/AcctMstr.java`, `AcctMstrId.java` |
| Sort whitelist | `domain/banking/service/AccountSortField.java` |
| Amount formatter | `domain/banking/service/AmountFormatter.java` |
| Bank-name / acct-type resolver | `domain/banking/service/BankCdResolver.java` (+ `BankInfmRepository`) |
| Account-type enum | `common/enums/AcctTypeCd.java` |
| Excel provider (VND) | `domain/banking/export/AccountInquiryVndExportProvider.java` (base `AccountInquiryExportProvider.java`) |
| Excel pipeline (shared) | `common/export/*` (`ExcelExportService`, `ExportableScreenRegistry`, `ExcelWorkbookWriter`) |
| Sync orchestrator | `domain/banking/nxisas/BankTransactionSyncService.java` (+ `BankScrapingController`, `NxisasScrapeSession`) |
| Status updater | `domain/banking/nxisas/AcctMstrStatusUpdater.java` (`markScrapeFailure`) |
| Sync-history read | `domain/banking/synchistory/controller/SyncHistoryController.java` (+ service) |
| Per-user authority entity | `domain/setting/bankaccount/entity/AcctAuthInfo.java` |
| Sort/web primitives | `common/web/SortDirection.java`, `SortableField.java` |
| DTOs | `domain/banking/dto/` — `AccountListResponse`, `AccountRow`, `CurrencyTotal`, `BankOption`, `AccountLookupListResponse`, `AccountLookupRow`, `OwnAccountListResponse`, `TransactionsSyncRequest`, `TransactionsSyncResponse`, `SubmitQnaRequest`, `SubmitChallengeRequest` |
| Migrations | `src/main/resources/db/migration/` — V20, V30–V35, V41, V42, V44 (see [database-mapping.md](database-mapping.md)) |

---

## 3. Refactor frontend (`wecms-web`)

| Layer | File |
|---|---|
| Route page | `app/(dashboard)/banking/account-inquiry/page.tsx` |
| Client wrapper | `app/(dashboard)/banking/account-inquiry/AccountInquiryClient.tsx` |
| Shared page shell | `components/shared/AccountInquiryPage.tsx` |
| Balance popup | `components/banking/account-inquiry/BalancePopover.tsx` |
| Sync dialog | `components/banking/account-inquiry/TransactionSyncDialog.tsx` |
| Sync-history dialog | `components/banking/account-inquiry/SyncHistoryDialog.tsx` |
| Latest-tx modal | `components/banking/account-inquiry/LatestTransactionHistoryModal.tsx` |
| API client | `lib/api/banking.ts` (`fetchAccounts`, `syncTransactions`, `submitSyncOtp/Qna/Captcha`, `fetchSyncHistory`, `fetchTransactions`) |
| Mappers | `lib/api/mappers.ts` (`mapAccountRow`, `mapCurrencyTotal`) |
| Wire types | `lib/types/api.ts` (`ApiAccountRow`, `ApiCurrencyTotal`, `ApiBankOption`, `ApiAccountListResponse`) |
| Sort hook | `hooks/useTableSort.ts` |
| View Setting | `components/shared/view-setting/*`, `lib/viewSettingScreens.ts` (`SCREEN_IDS.ACCOUNT_INQUIRY_VND`) |
| Excel export | `lib/excel.ts`, `hooks/useExcelExport.ts` |
| Currency / datetime | `lib/currency.ts`, `lib/datetime.ts` |

Full frontend detail: [wecms-web/docs/features/banking/current-savings-vnd/account-inquiry/component-index.md](../../../../../../wecms-web/docs/features/banking/current-savings-vnd/account-inquiry/component-index.md).
