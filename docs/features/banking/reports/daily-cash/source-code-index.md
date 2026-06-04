# Daily Cash — Source Code Index

Master index of every file related to the legacy **Daily Cash** (`rept_0009_001`) page and the
existing refactor. All legacy paths are under `WECMS DEV/` (read-only reference). Refactor paths
are under `wecms-api/` and `wecms-web/`.

## Legend
- **Type** — View JSP · TS/JS · Action JSP (API) · IDO XML · WSVC XML · BCS · Batch · Helper · Entity · DAO · Excel JSP · DB function · ML · Controller · Service · Repository · React
- API/query/table = the service id, IDO id, or DB object the file is tied to.

---

## Legacy (JEX) — `WECMS DEV/`

| File path | Type | Purpose | Important functions / events | Related API / query / table |
|---|---|---|---|---|
| `WECMS/web/view/jex/wabooks/rept/rept_0009_001_view_osmc.jsp` | View JSP (VN active) | Daily Cash page markup; summary + Current/Savings tables; commented-out Cash/TimeDeposit/Loan tables; Excel POST form + hidden iframe | DOM anchors: `.btn_search_tb`, `.excel-down`, `.page-print`, `.date`, `input[name=checkCurr]`, `.deposit_withdraw`, `.SavingAmount` | view `rept_0009_001`; calls `rept_0009_001_r001`, `rept_0100_001_r001` |
| `WECMS/web/view/jex/wabooks/rept/rept_0009_001_view.jsp` | View JSP (non-VN) | Non-VN variant of the view | same as above | view `rept_0009_001` |
| `WECMS/web/view/jex/wabooks/rept/rept_0009_001_view_old.jsp` | View JSP (dead) | Superseded old view — **not active** | — | — |
| `WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0009_001_osmc.ts` | TS (active) | Page controller: load date, fetch, render all sections, totals, Excel, print | `onload`, `event`, `loadDate`, `fn_listData`, `fn_regularDeposit`, `fn_savingAmt`, `fn_loan`, `fn_cash`, `download_excel`, `fn_formatExcel`, `fn_last_date`, `jConfirm`, `fn_formatcnt` | `rept_0009_001_r001`, `rept_0100_001_r001` |
| `WECMS_STATIC/web/js/jex/wabooks/rept/rept_0009_001_osmc.js` | JS (compiled) | Compiled output of the `.ts` (encrypted/minified — never hand-edit) | — | — |
| `WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0009_001.ts` / `_old.ts` | TS (dead) | Older controllers — **not active** | — | — |
| `WECMS_STATIC/@types/wabooks/rept/rept_0009_001_r001.d.ts` | TS types | Request/response interfaces for the read API | `Drept_0009_001_r001In`, `Drept_0009_001_r001Out`, `_REC1`, `_REC2` | `rept_0009_001_r001` |
| `WECMS_STATIC/@types/wabooks/rept/rept_0100_001_r001.d.ts` | TS types | Request/response for last-collection lookup | `Drept_0100_001_r001In/Out` | `rept_0100_001_r001` |
| `WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0009_001_r001_act.jsp` | Action JSP (API) | Read API: validates date, builds currency dynamic, runs 4 IDO reads, assembles `REC`/`REC1`/`REC2` | `validateInput`, `getDynamic0`, `getRowsForRec1..4` | IDO `DAILY_CASH_REPT_DAILY_SUMR_R004/R005/R006/R007` |
| `WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0100_001_r001_act.jsp` | Action JSP (API) | Last business-aggregation datetime lookup by `BAT_ID` | `validateInput`, `getRow`, `normalizeResult` | IDO `BAT_CALL_HIS_R002`; table `BAT_CALL_HIS` |
| `WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0009_001_r001_common.jsp` | Action JSP (shared) | Shared include for the read action | — | `rept_0009_001_r001` |
| `WECMS_ETC/xml/service/WSVC/WSVC.rept_0009_001_r001.xml` | WSVC XML | Read-API contract: IN/OUT fields + `REC`/`REC1`/`REC2` sub-rules | — | `rept_0009_001_r001` |
| `WECMS_ETC/xml/service/WSVC/WSVC.rept_0009_001.xml` | WSVC XML | View-service contract | — | `rept_0009_001` |
| `WECMS_ETC/xml/service/WSVC/WSVC.rept_0100_001_r001.xml` | WSVC XML | Last-collection API contract (IN `BAT_ID`, OUT `LST_COLL_DATE`) | — | `rept_0100_001_r001` |
| `WECMS_ETC/xml/service/IDO/IDO.DAILY_CASH_REPT_DAILY_SUMR_R004.xml` | IDO XML | REC1 source — account detail | `SELECT ... FROM rept_0900_001_rec1(:BIZ_KEY,:REQ_DATE,:REQ_DATE2)` | fn `rept_0900_001_rec1` |
| `WECMS_ETC/xml/service/IDO/IDO.DAILY_CASH_REPT_DAILY_SUMR_R005.xml` | IDO XML | Per-currency totals (NORM/SAV/ACCT/LOAN) → output `REC` | `... FROM rept_0900_001_rec2(...)` | fn `rept_0900_001_rec2` |
| `WECMS_ETC/xml/service/IDO/IDO.DAILY_CASH_REPT_DAILY_SUMR_R006.xml` | IDO XML | Cash-flow rows → output `REC2`; `ORDER BY CCY_CD VND-first` | `... FROM rept_0900_001_rec3(...)` | fn `rept_0900_001_rec3` |
| `WECMS_ETC/xml/service/IDO/IDO.DAILY_CASH_REPT_DAILY_SUMR_R007.xml` | IDO XML | Total cash per currency → merged into `REC.TOTL_CASH` | `... FROM rept_0900_001_rec4(...)` | fn `rept_0900_001_rec4` |
| `WECMS_ETC/xml/service/IDO/IDO.BAT_CALL_HIS_R002.xml` | IDO XML | `max(JOB_DATE||JOB_END_TM)` for last-collection | — | table `BAT_CALL_HIS` |
| `WECMS/src/batch/jex/wabooks/rept/REPT_0900_001.java` | Batch | Nightly job: iterate NORMAL businesses, write `BAT_CALL_HIS`, call helper. **UAT short-circuits.** | `execute(JexData)` | `BUSI_INFM`, `BAT_CALL_HIS`; `ReptHelper.call_REPT_0900_001` |
| `WECMS/src/bcs/jex/wabooks/rept/REPT_0900_001.java` | BCS | Compute REC1..4 for one business via the 4 IDO functions | `execute`, `getRec1..4`, `getRowsForRec1..4`, `getDynamic0` | IDO `R004–R007` |
| `WECMS/src/wabooks/helper/ReptHelper.java` | Helper | `call_REPT_0900_001`: persist detail/cash-detail/summary tables from BCS output | `call_REPT_0900_001` | `DAILY_CASH_REPT_DAILY_SUMR(_DETL)`, `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` |
| `WECMS/src/wabooks/entity/DailyCashReptDailySumr.java` | Entity | `@Table(DAILY_CASH_REPT_DAILY_SUMR)` per-currency totals | — | `DAILY_CASH_REPT_DAILY_SUMR` |
| `WECMS/src/wabooks/entity/DailyCashReptDailySumrDetl.java` | Entity | `@Table(DAILY_CASH_REPT_DAILY_SUMR_DETL)` account detail | — | `DAILY_CASH_REPT_DAILY_SUMR_DETL` |
| `WECMS/src/wabooks/entity/DailyCashReptDailyCashSumrDetl.java` | Entity | `@Table(DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL)` cash flow | — | `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` |
| `WECMS/src/wabooks/dao/DailyCashReptDailySumrDAO.java` | DAO | CRUD for summary table | `create`, `deleteByPk` | `DAILY_CASH_REPT_DAILY_SUMR` |
| `WECMS/src/wabooks/dao/DailyCashReptDailySumrDetlDAO.java` | DAO | CRUD for detail table | `create`, `deleteByFk` | `DAILY_CASH_REPT_DAILY_SUMR_DETL` |
| `WECMS/src/wabooks/dao/DailyCashReptDailyCashSumrDetlDAO.java` | DAO | CRUD for cash-detail table | `create`, `deleteByFk` | `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` |
| `WECMS/src/wabooks/dao/BatCallHisDAO.java` | DAO | Batch-call-history writes | `create`, `update` | `BAT_CALL_HIS` |
| `WECMS/src/wabooks/enums/AcctTypeCd.java` | Enum | **Authoritative account-type codes** `01–08` | `fromValue`, `getValue`, `getLabel` | `ACCT_TYPE_CD` |
| `WECMS/src/wabooks/enums/ReptBatId.java` | Enum | Report batch ids (`REPT_0200..0900_001`) | `fromValue` | — |
| `WECMS/src/wabooks/beans/DailySumrTotInfo.java` | Bean | Per-currency totals holder used by Excel JSP | getters/setters | — |
| `WECMS/web/view/jexGrid/file/rept_0009_001_excel_osmc_vn.jsp` | Excel JSP (VN) | POI workbook builder from posted JSON (active VN export) | `getHeader`, `generateRows`, `createSavingAccountOrCurrentAccoutHeaderTable`, `downloadExcelFile` | reads posted `excelJsonObject` |
| `WECMS/web/view/jexGrid/file/rept_0009_001_excel_osmc.jsp` | Excel JSP (non-VN) | POI workbook builder (non-VN) | same family | posted JSON |
| `WECMS/web/view/jexGrid/file/rept_0009_001_excel.jsp` | Excel JSP (legacy) | Older export variant | — | posted JSON |
| `WECMS_SYNC/dbSync/AD_DST_MGNT/ML_COM/rept_0009_001_view.json` | ML map | `ML_ID → DIC_ID` map for the view labels | — | DIC table |

### Legacy DB objects (referenced, bodies not in repo)
| Object | Type | Used by |
|---|---|---|
| `rept_0900_001_rec1(biz_key, req_date, req_date2)` | DB function (set-returning) | IDO `R004` → REC1 account detail |
| `rept_0900_001_rec2(...)` | DB function | IDO `R005` → per-currency totals (REC) |
| `rept_0900_001_rec3(...)` | DB function | IDO `R006` → cash flow (REC2) |
| `rept_0900_001_rec4(...)` | DB function | IDO `R007` → total cash (REC.TOTL_CASH) |

> **Open Question — function bodies.** The `rept_0900_001_rec1..4` PL/pgSQL bodies are not in the
> repository and were not queried (the `postgres` MCP was not available this pass). Their source
> tables / joins / aggregation rules are **unverified** — see [`database-mapping.md`](./database-mapping.md) §"Open Questions".

---

## Refactor (Spring Boot) — `wecms-api/`

| File path | Type | Purpose | Important methods | Related |
|---|---|---|---|---|
| `src/main/java/com/kosign/wecms/domain/banking/controller/DailyCashReportController.java` | Controller | REST entry | `search(asOfDate, currency)` (GET), `export(...)` (POST) | `/api/v1/banking/reports/daily-cash-report` |
| `src/main/java/com/kosign/wecms/domain/banking/service/DailyCashReportService.java` | Service | **Live recompute** (2026-06-04): builds summary/accounts/flow from `DailyCashRecomputeRepository` rows; bucketing via `AcctTypeCd.fromCode`; cash=0 | `search`, inner `CurrencyAccumulator` | recompute repo + `BankCdResolver` + `AuditLogService` |
| `src/main/java/com/kosign/wecms/domain/banking/repository/DailyCashRecomputeRepository.java` | Repository | **★ live recompute read source** (2026-06-04) — native `findAccountsAsOf(bizKey, refDate, ccyExact, ccyExclude)` over `ACCT_MSTR`+`ACCT_TRNS_HSTR` = legacy `rept_0900_001_rec1` | `findAccountsAsOf` | `ACCT_MSTR`, `ACCT_TRNS_HSTR` |
| `src/main/java/com/kosign/wecms/domain/banking/repository/DailyCashAccountProjection.java` | Projection | Row shape returned by the recompute query (ccy/type/bank/acct/bal/loan/expi/agmt/snd/rcv) | — | recompute repo |
| `src/main/java/com/kosign/wecms/domain/banking/service/DailyCashCurrencyFilter.java` | Service (enum) | Parse `currency` → `exact()` / `exclude()` | `parse`, `exact`, `exclude` | — |
| `src/main/java/com/kosign/wecms/domain/banking/dto/DailyCashReportResponse.java` | DTO | Wire response (`CurrencySummary`, `AccountRow`, `CurrencyFlow`) — shape unchanged by the source change | — | endpoint |
| `src/main/java/com/kosign/wecms/domain/banking/dto/DailyCashReport.java` | DTO (unused) | Legacy UPPER_SNAKE scaffold — **not referenced** | — | — |
| `src/main/java/com/kosign/wecms/domain/banking/entity/DailyCashReptDailySumrDetl.java` | Entity | `@Table(DAILY_CASH_REPT_DAILY_SUMR_DETL)`, 6-col composite PK — **dormant** (off the read path since 2026-06-04; retained for future batch port) | `Pk` | snapshot table |
| `src/main/java/com/kosign/wecms/domain/banking/repository/DailyCashReptDailySumrDetlRepository.java` | Repository | `findRows(...)` JPQL on the snapshot — **dormant** (no longer called by the service) | `findRows` | snapshot table |
| `src/main/java/com/kosign/wecms/domain/banking/export/DailyCashReportExportProvider.java` | Export provider | `screenId=daily-cash-report`; flat account ledger XLSX via shared POI pipeline | `fetch`, `allowedFieldIds` | `/export` |
| `src/main/java/com/kosign/wecms/common/enums/AcctTypeCd.java` | Enum | Refactor mirror of legacy codes `01–08` (correct) | `fromCode`, `code`, `i18nKey` | — |

## Refactor (Next.js) — `wecms-web/`

| File path | Type | Purpose | Important functions | Related |
|---|---|---|---|---|
| `app/(dashboard)/banking/reports/daily-cash/page.tsx` | Route | Server component, renders `<DailyCashClient/>` | — | `/banking/reports/daily-cash` |
| `components/banking/reports/daily-cash/DailyCashClient.tsx` | React | Fetch + state; maps wire → page props; current/savings split | `isCurrentType` (=`03/04`), `isSavingType` (=`05/06`), `rollupSummary`, `toSummaryRow`, `toAccountRow`, `toWireCurrency` (mapping fixed 2026-06-04) | `fetchDailyCashReport` |
| `components/banking/reports/daily-cash/DailyCashPage.tsx` | React | Presentational: filter row, per-currency summary block (legacy "Daily Cash Status —" style, zero VND/USD/KRW fallback), Current/Saving detail tables, export/print. Cash table + ledger checkbox column removed 2026-06-04. | `currencyTotal`, `compareCurrency`, `zeroSummaryRow`, `SummaryTable`, `AccountLedger` | `ReportLayout` |
| `components/banking/reports/daily-cash/types.ts` | TS types | `CurrencyScope`, row types | — | — |
| `components/banking/reports/daily-cash/mockData.ts` | Mock (unused) | Static seed (not imported after stabilization) | — | — |
| `lib/api/banking.ts` | API client | `fetchDailyCashReport({asOfDate, currency})` | — | endpoint |
| `lib/dailyCashExport.ts` | Util | **Client-side** `xlsx-js-style` workbook + print HTML | `exportDailyCashExcel`, `printDailyCashReport` | ⚠️ violates backend-single-producer rule |

## Existing refactor docs
| File | Notes |
|---|---|
| `wecms-api/docs/features/banking/reports/daily-cash.md` | Backend spec (Planning). OQ-1 share-vs-recompute, OQ-2 `INQ_DV`, OQ-3 non-VND local ccy. Some §3/§4 examples stale vs code. |
| `wecms-web/docs/features/banking/reports/daily-cash.md` | Frontend stub. "Confirm exact grid against API + JEX." |
