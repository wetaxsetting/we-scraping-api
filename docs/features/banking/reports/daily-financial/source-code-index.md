# Daily Financial — Source Code Index (Traceability)

Complete file/method/line traceability. Legacy paths are under `WECMS DEV/` (read-only). DB objects
confirmed in `wecmsvndev`. Refactor paths under `wecms-api/` and `wecms-web/`.

---

## 1. Legacy frontend

| Artifact | Path | Key methods / lines |
|---|---|---|
| View JSP | `WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp` | title `:101`; filter bar `:109-148`; financial-status blocks `:170-247`; current acc `:286-336`; savings acc `:338-389`; cash/timedep/loan **commented out** `:249-284,391-514`; excel form `:525-527` |
| TypeScript | `WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts` | `onload :132`; `event :144`; drilldowns `:146-167`; checkbox→Y/N `:169-187`; `btn_search :189-210`; print `:212-227`; excel `:229-265`; `load :269`; `fn_listData :276-399`; `fn_finacialStatus :401-449`; `fn_currentAccounts :451-678`; `fn_timeDeposit :680`; `fn_cash :697-719`; `fn_loan :721-741`; `fn_accBank :743-924`; `fn_append :926-934` |
| Compiled JS | `WECMS_STATIC/web/js/jex/wabooks/rept/rept_0015_001.js` | generated from TS (do not hand-edit) |
| Type defs | `WECMS_STATIC/@types/wabooks/rept/rept_0015_001_r001.d.ts` | response typing |
| Excel JSP | `WECMS/web/view/jexGrid/file/rept_0015_001_excel.jsp` | 4,508 lines; IDO re-reads `:308-399`; download `:403-433`; header builders `:723-976`; row builders `:980-1229+` |
| View includes | `inc_wabooks_hd_v2_osmc.jsp`, `inc_wabooks_hd_v2_add.jsp`, `inc_wabooks_nxisas_v2.jsp`, `inc_wabooks_gnb_v2_osmc.jsp`, `inc_wabook_fnsh_lnb.jsp`, `comm_0192_007_view.jsp`, `inc_wabooks_footer_v2.jsp` | view `:45-47,81-95,529` |

## 2. Legacy backend (action + helpers)

| Artifact | Path | Methods / lines |
|---|---|---|
| Action JSP | `WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0015_001_r001_act.jsp` | `validatInput :41-49`; recompute helpers `:53-205`; dynamic builders `:207-381`; `getREC :387-397`; `getRec1 :429-436`; `getRec2 :439-447`; `getRec3 :450-457`; main body `:487-545` |
| Enums | `WECMS/src/wabooks/enums/AcctTypeCd.java`, `CcyCd.java`, `YesOrNo.java` | account-type codes 01-06; USD label; Y/N |
| Helpers | `wabooks.commons.IdoUtils` (`mergeDynamic`, `getDynamic`, `executeWithTxn`, `executeListWithTxn`), `wabooks.helper.LocaleHelper` (`getLangCd`), `wabooks.helper.ValidationHelper`, `wabooks.commons.NumberUtils`, `ObjectUtils` | per action imports |

## 3. Legacy IDO XML (`WECMS_ETC/xml/service/IDO/`)

`IDO.DAILY_FINCL_REPT_DAILY_SUMR_DETL_{R001,R002,R003,R004,D002,C002}.xml` ·
`IDO.DAILY_FINCL_REPT_DAILY_CASH_SUMR_{R001,R002,R003,D002,C002}.xml` ·
`IDO.DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR_{R002,D002,C002}.xml` ·
`IDO.DAILY_FINCL_REPT_DAILY_CASH_SUMR_DETL_{R002,D002,C002}.xml` ·
`IDO.DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR_{R002,D002,C002}.xml`
(CRUD/role table in [database-mapping.md §4](database-mapping.md#4-ido-inventory-crud-per-table); SQL
in [interface-specification.md §5](interface-specification.md#5-key-sql-verbatim)).

## 4. Legacy WSVC XML

`WECMS_ETC/xml/service/WSVC/WSVC.rept_0015_001.xml` (view) ·
`WECMS_ETC/xml/service/WSVC/WSVC.rept_0015_001_r001.xml` (API: IN 4 fields, OUT REC/REC1/REC2/REC3 +
subRules REC2_1).

## 5. Legacy popups

| Popup | View / Action | Reads | Out of refactor scope? |
|---|---|---|---|
| `rept_0015_002` (txn history) | `…/rept/rept_0015_002_view.jsp` + `…_r001_act.jsp` | `DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR` (→ refactor reads `ACCT_TRNS_HSTR`) | in scope (popup implemented) |
| `rept_0015_003` (receipt/payment detail) | `…/rept/rept_0015_003_view.jsp` + `…_r001_act.jsp` | `DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR`, `ACCT_TRNS_RCV_PAY_INFM` | summary table **out of scope** |
| `rept_0015_004` (cash-book detail) | `…/rept/rept_0015_004_view.jsp` + `…_r001_act.jsp` | `DAILY_FINCL_REPT_DAILY_CASH_SUMR_DETL`, `CASH_REPY_MSTR` | **out of scope** (cash) |

## 6. Database objects (live `wecmsvndev`)

**Tables:** `daily_fincl_rept_daily_sumr_detl`, `daily_fincl_rept_daily_acct_trns_hstr_sumr`,
`daily_fincl_rept_daily_trns_rcv_pay_hstr_sumr`, `daily_fincl_rept_daily_cash_sumr`,
`daily_fincl_rept_daily_cash_sumr_detl`, `acct_mstr`, `acct_trns_hstr`, `acct_trns_rcv_pay_infm`,
`cash_repy_mstr`.
**Functions:** `wb_insert_daily_fincl_rept_daily_sumr_detl_data`,
`wb_insert_daily_fincl_rept_daily_cash_sumr_data`, `rept_0015_001_r001_rec1`,
`rept_0015_001_r001_rec3`, `wb_get_bank_nm`, `wb_get_usag_nm`.
**Views / sequences:** none specific to this feature.
Column-level detail: [database-mapping.md](database-mapping.md).

## 7. Legacy entities / DAOs (read-only reference)

| Table | JEX entity (`WECMS/src/wabooks/entity/`) |
|---|---|
| ACCT_MSTR | `AcctMstr.java` |
| ACCT_TRNS_HSTR | `AcctTrnsHstr.java` |
| ACCT_TRNS_RCV_PAY_INFM | `AcctTrnsRcvPayInfm.java` |
| CASH_REPY_MSTR | `CashRepyMstr.java` |

The five `DAILY_FINCL_REPT_*` summary tables are populated by SQL functions / `INSERT…SELECT`; no
dedicated JEX entity drives the read (the action uses raw IDOs).

## 8. Refactor backend (`wecms-api/`)

| Artifact | Path | Notes |
|---|---|---|
| Controller | `src/main/java/com/kosign/wecms/domain/banking/controller/ReportController.java` | `dailyFinancial :29-36`; `dailyFinancialTransactions :43-55`; `dailyFinancialHs :57-63` |
| Service | `src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java` | `dailyFinancial` (`:147-225`, 2026-06-14 — reads the legacy function **`rept_0015_001_r001_rec1`** via `DailyFinancialCmcFunctionRepository.rec1(...)` and projects its rows: as-of balance + same-day SND/RCV + internal split + per-currency Financial Status incl. KRW; DF-3 `activeAccountsOnly`/DF-4 `includeInternalTransfers` applied; the earlier `computeDailyAggregates`/`asOfBalance` Java live-compute was removed; inline REC2_1 still empty — popup serves drilldown); `dailyFinancialTransactions` (`ACCT_TRNS_HSTR`, paginated — **implemented**); `dailyFinancialHs` (calls `fn_get_haengsung_daily_financial_report` via `DailyFinancialHsReportRepository`) |
| Function repo | `src/main/java/com/kosign/wecms/domain/banking/repository/DailyFinancialCmcFunctionRepository.java` | `rec1(...)` — single native-query funnel to `rept_0015_001_r001_rec1`; documented set-returning exception (CLAUDE.md §"Legacy report DB functions") |
| DTOs | `…/domain/banking/dto/DailyFinancialCmcReport.java`, `DailyFinancialTransactionsReport.java`, `DailyFinancialHsReport.java` | REC3/cash dropped from CMC DTO |
| Entities | `…/domain/banking/entity/AcctMstr.java`, `AcctTrnsHstr.java`, `AcctTrnsRcvPayInfm.java` | reused (Sprint 1) |
| Repos | `AcctMstrRepository`, `AcctTrnsHstrRepository` (`findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc`) | |
| Helpers | `AmountFormatter` (`wire`, `forDisplay`), `TenantContext` | |
| Recompute fns | `wb_insert_daily_fincl_*` | **not referenced** — see CLAUDE.md §"Legacy report DB functions — Sprint 1 stance" |
| Feature doc (stub) | `wecms-api/docs/features/banking/reports/daily-financial.md` | superseded by this package |

## 9. Refactor frontend (`wecms-web/`) — per coverage scan

| Artifact | Path |
|---|---|
| Route page | `app/.../banking/reports/daily-financial/page.tsx` |
| Page component | `components/banking/reports/daily-financial/DailyFinancialPage.tsx` |
| Txn popup | `components/banking/reports/daily-financial/TransactionHistoryDialog.tsx` — **2026-06 restyle: UI aligned to e-Invoice Latest Inquiry History popup** (bordered table, sticky header, centered empty-state, footer summary, default page size 10 / options 10·20·50·100, upward dropdown); imports `PAGE_SIZE_OPTIONS` from `components/shared/Pagination`, `ChevronLeft`/`ChevronRight` from `lucide-react`. **READY FOR SIT.** |
| Types | `components/banking/reports/daily-financial/types.ts` |
| Mock data | `components/banking/reports/daily-financial/mockData.ts` |
| HS variant | `components/banking/reports/daily-financial-hs/DailyFinancialHsPage.tsx` |
| Feature doc | `wecms-web/docs/features/banking/reports/daily-financial.md` |
| Page mapping | `wecms-web/docs/references/page-mapping/page-mapping.md` (rept_0015_001 → /banking/reports/daily-financial) |

> Route group confirmed `app/(dashboard)/banking/reports/daily-financial/page.tsx`. Page-level wiring
> is **live (real API)** as of 2026-06 — `DailyFinancialClient` calls `GET …/daily-financial` (HTTP 200,
> VND/USD/KRW verified); `mockData.ts` is fallback-only. The Transaction History Details popup is
> restyled to the e-Invoice pattern and **READY FOR SIT** (see [README.md §5](README.md#5-sit-readiness-2026-06)).
