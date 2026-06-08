# Account Inquiry (FCY) — Source Code Index

> Legacy JEX ↔ refactor backend ↔ refactor frontend file index. Legacy refs are read-only under
> `WECMS DEV/`. Full agent-verified legacy inventory:
> [legacy investigation package source-code-index](../../../../../docs/features/account-inquiry-fcy/source-code-index.md).

## 1. Legacy JEX (`fnsh_0005_001`, read-only)

| Concern | File |
|---|---|
| View (active) | `WECMS DEV/WECMS/web/view/jex/wabooks/fnsh/fnsh_0005_001_view_osmc.jsp` |
| Client script (active) | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0005_001_api_osmc.ts` |
| Action — list | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0005_001_r001_act.jsp` → `FnshHelper.fnsh_0001_001_r001(..., CcyCd.USD)` |
| Action — history popup | `fnsh_0005_001_r002_act.jsp` → `FnshHelper.fnsh_0001_001_r002(..., CcyCd.USD)` |
| Action — scrape list | `fnsh_0005_001_r003_act.jsp` (feeds the now-dead `fn_CheckAcc` path) |
| Helper / business logic | `WECMS DEV/WECMS/src/wabooks/helper/FnshHelper.java` (`fnsh_0001_001_r001*`, lines ~363-537) |
| IDO — paged list | `IDO.ACCT_MSTR_R004.xml` (date-aware balance + Vietcombank `11704004` special case) |
| IDO — per-CCY totals | `IDO.ACCT_MSTR_R004_CNT.xml` |
| IDO — grand totals | `IDO.ACCT_MSTR_R001_CNT.xml` |
| IDO — history popup | `IDO.ACCT_TRNS_HSTR_R010.xml` |
| WSVC — list contract | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.fnsh_0005_001_r001.xml` |
| Scrape gateway | `nxisas_fnsh_r001_act.jsp` (→ Coocon smart-gateway); save `fnsh_0001_001_c001` |
| Bank dropdown | `comm_0140_001_r001` (`{CCY_CD:"USD"}`) |
| "View Details" target | `fnsh_0006_001` (FCY Transaction History — separate screen) |
| **Dead / do-not-port** | engine variant `fnsh_0005_001_osmc.ts` + `nxisas_v2.js`; `fn_CheckAcc`, `#btn_Script`, `.select_acct`, `getNxisasMulti` (per-account `.exe`-engine multi-scrape) — marked `// stop use` |

> The FCY **Transaction History** sibling (legacy `fnsh_0002_010`, `ACCT_TRNS_HSTR_R016`,
> `WSVC.fnsh_0002_010_r001`) is a *different* feature — documented under
> [current-savings-fcy/transaction-history.md](../current-savings-fcy/transaction-history.md), not here.

## 2. Refactor backend (`wecms-api`)

| Layer | Class | Path |
|---|---|---|
| Controller | `AccountController` | `src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java` |
| Service | `AccountService` | `…/banking/service/AccountService.java` (`list`, `resolveFilter`, `resolveLastUpdate`, `toRow`, `loadConnectedBanks`) |
| Filter carrier | `AccountFilter` | `…/banking/service/AccountFilter.java` |
| Sort whitelist | `AccountSortField` | `…/banking/service/AccountSortField.java` |
| Amount formatter | `AmountFormatter` | `…/banking/service/AmountFormatter.java` |
| Bank/acct-type resolver | `BankCdResolver` | `…/banking/service/BankCdResolver.java` |
| Repository | `AcctMstrRepository` | `…/banking/repository/AcctMstrRepository.java` (`findByBizKeyAndCcyCdNotAndActvYn`, `findFiltered`, `aggregateByCcy`, `findDistinctBankCds`, `markScrapeFailure`) |
| Entity | `AcctMstr` / `AcctMstrId` | `…/banking/entity/AcctMstr.java` |
| Entity (tx) | `AcctTrnsHstr` | `…/banking/entity/AcctTrnsHstr.java` |
| DTO — list | `AccountListResponse` | `…/banking/dto/AccountListResponse.java` |
| DTO — row | `AccountRow` | `…/banking/dto/AccountRow.java` |
| DTO — per-CCY total | `CurrencyTotal` | `…/banking/dto/CurrencyTotal.java` |
| DTO — bank option | `BankOption` | `…/banking/dto/BankOption.java` |
| DTO — sync req/resp | `TransactionsSyncRequest` / `TransactionsSyncResponse` | `…/banking/dto/` |
| Export provider | `AccountInquiryFcyExportProvider` (extends `AccountInquiryExportProvider`) | `…/banking/export/AccountInquiryFcyExportProvider.java` |
| Sync orchestrator | `BankTransactionSyncService` | `…/banking/nxisas/BankTransactionSyncService.java` |
| Coocon client | `NxisasGatewayClient` | `…/banking/nxisas/NxisasGatewayClient.java` |
| Sync session | `NxisasScrapeSession` / `NxisasSessionRegistry` | `…/banking/nxisas/session/` |
| Authority entity | `AcctAuthInfo` | `…/setting/bankaccount/entity/AcctAuthInfo.java` |
| Menu guard | `MenuAuthorizationGuard` (`canAccessAccountInquiry`) | `…/` (navigation/security) |

## 3. Refactor frontend (`wecms-web`)

| Layer | File |
|---|---|
| Route page | `app/(dashboard)/banking/account-inquiry-fcy/page.tsx` |
| Client wrapper | `app/(dashboard)/banking/account-inquiry-fcy/AccountInquiryFcyClient.tsx` (`currency="FCY"`, `multiCurrencyMode`) |
| Shared client wrapper | `app/(dashboard)/banking/account-inquiry/AccountInquiryClient.tsx` |
| Shared shell | `components/shared/AccountInquiryPage.tsx` (also serves VND) |
| Balance popup | `components/banking/account-inquiry/BalancePopover.tsx` |
| Sync dialog | `components/banking/account-inquiry/TransactionSyncDialog.tsx` |
| Sync history | `components/banking/account-inquiry/SyncHistoryDialog.tsx` |
| Filters | `BankFilterPopover` / `CurrencyFilterPopover` (in `AccountInquiryPage.tsx`) |
| Hooks | `lib/api/useApiData.ts`, `hooks/useTableSort.ts`, `hooks/useStickyLoading.ts`, `components/shared/view-setting/useViewSetting.ts`, `hooks/useExcelExport.ts` |
| API client | `lib/api/banking.ts` (`fetchAccounts`, `fetchTransactions`, `syncTransactions`, `submitSyncQna/Otp/Captcha`) |
| Wire types | `lib/types/api.ts` (`ApiAccountRow`, `ApiAccountListResponse`, `ApiCurrencyTotal`, sync types) |
| Screen-id registry | `lib/viewSettingScreens.ts` (`SCREEN_IDS.ACCOUNT_INQUIRY_FCY = "banking-account-inquiry-fcy"`) |

Frontend detail: [wecms-web/docs/features/banking/account-inquiry-fcy/component-index.md](../../../../../wecms-web/docs/features/banking/account-inquiry-fcy/component-index.md).

## 4. Migrations / seeds

| Migration | Purpose |
|---|---|
| V13 + V23 | `ACCT_MSTR` / `ACCT_TRNS_HSTR` schema + column-parity corrective |
| V43 / V47 | View Setting seed for `banking-account-inquiry-fcy` (column-seed contents assumed = VND fallback; NOT VERIFIED at row level) |
| V243 | Navigation catalogue seed — `banking-fcy-update-inquire` permission |
