# Cash Flow — Source Code Index (traceability)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../../references/database/flyway-migration-baseline.md) §5.

Complete inventory of every file / object, legacy and refactor. Paths are workspace-relative.

---

## 1. Legacy JEX inventory

### Screen → Action → IDO → SQL → DB

```
cash_0001_001.act
 → cash_0001_001_view.jsp                 (screen markup, ML, CSS, ExcelJS include)
 → cash_0001_001.ts / .js                 (UI logic, AJAX, hierarchy render, ExcelJS export)
     ├─ AJAX cash_0001_001_r001 → cash_0001_001_r001_act.jsp
     │     → IDO CASH_FLOW_R001 → fn get_cashflow_report() → chart_acct_infm + 4 txn sources
     ├─ AJAX cash_0001_001_r002 → cash_0001_001_r002_act.jsp
     │     → IDO BANK_EXCH_RATE_INFM_R002 → bank_exch_rate_infm
     └─ AJAX cash_0001_001_r003 → cash_0001_001_r003_act.jsp
           → AcctMstrDAO (IDO ACCT_MSTR_R001) → acct_mstr
```

| Type | Path |
|---|---|
| View JSP | [WECMS/web/view/jex/wabooks/cash/cash_0001_001_view.jsp](../../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/cash/cash_0001_001_view.jsp) |
| TypeScript | [WECMS_STATIC/ts-src/js/jex/wabooks/cash/cash_0001_001.ts](../../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/cash/cash_0001_001.ts) |
| Compiled JS | `WECMS_STATIC/web/js/jex/wabooks/cash/cash_0001_001.js` (encrypted output — never hand-edit) |
| Action JSP `_r001` | [.../action/.../cash/cash_0001_001_r001_act.jsp](../../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/cash/cash_0001_001_r001_act.jsp) |
| Action JSP `_r002` | `.../cash/cash_0001_001_r002_act.jsp` |
| Action JSP `_r003` | `.../cash/cash_0001_001_r003_act.jsp` |
| WSVC | `WSVC.cash_0001_001.xml` (view), `WSVC.cash_0001_001_r001.xml` / `_r002` / `_r003` |
| IDO | `IDO.CASH_FLOW_R001.xml`, `IDO.BANK_EXCH_RATE_INFM_R002.xml` |
| Menu include | [inc_wabook_fnsh_lnb.jsp:216-221](../../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/include/inc_wabook_fnsh_lnb.jsp#L216-L221) |

### DB objects (JEX DEV, schema `wecmsvndev`)

`get_cashflow_report(varchar,int,varchar,varchar,numeric,numeric,text)` (function), `wb_get_chart_acct_level_text` /
`wb_get_chart_acct_level`, `wb_get_appr_stts` / `wb_get_admin_yn`; tables `chart_acct_infm`,
`scdul_pymn_sumr(_detl)`, `ovrs_pymn_mstr`, `card_trns_hstr`, `acct_trns_hstr`, `acct_trns_rcv_pay_infm`,
`expt_resol_mstr`, `expt_resol_apprvr_infm`, `bank_exch_rate_infm`, `acct_mstr`.

> The `get_cashflow_report` body is **not committed anywhere in the workspace** — it lives only on the
> JEX DEV database. Its anatomy is documented in prose in [business-flow.md §2](business-flow.md). To
> read the verbatim body, use the read-only `postgres_dev_server_jexframe` MCP (`pg_get_functiondef`),
> not a file.

---

## 2. Refactor backend — `wecms-api/`

| File | Role |
|---|---|
| [domain/banking/controller/CashFlowReportController.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/controller/CashFlowReportController.java) | `GET /api/v1/banking/reports/cash-flow-report` + `POST .../export`; parses `bankFilters` JSON; clamps `quarter` |
| [domain/banking/service/CashFlowReportService.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/service/CashFlowReportService.java) | Orchestrator + `validate`; **live path delegates to `CashFlowReportRepository` → `get_cashflow_report` directly (2026-06-13)** |
| `domain/banking/repository/CashFlowReportRepository.java` | **★ live read source (2026-06-13)** — `search(...)` calls `get_cashflow_report` directly (`CashFlowReportRepository.java:67`); DB helpers `wb_get_chart_acct_level` + `wb_get_chart_acct_level_text` added to the refactor DB |
| `domain/banking/service/cashflow/PaymentDataAggregator.java` | **retained but off the active path (2026-06-13)** — 4-source UNION + approval gate + bank filter + FX (`applyFilters` / `applyFxOnly`) — **no quarter param** |
| `domain/banking/service/cashflow/ApprovalStatusResolver.java` | **retained, off active path** — `wb_get_appr_stts` replacement (`appr_type_cd='01'`) |
| `domain/banking/service/cashflow/FxConverter.java` | **retained, off active path** — per-currency single-round HALF_UP conversion |
| `domain/banking/service/cashflow/ChartOfAccountsHierarchy.java` | **retained, off active path** — L1 roots + active L2/L3 sets (replaces `wb_get_chart_acct_level_text`) |
| `domain/banking/service/cashflow/BalanceCalculator.java` | **retained, off active path** — Opening / End / Sales Profit + `INVESTMENT_BALANCE_CONTRIBUTING_CODES` |
| `domain/banking/service/cashflow/CashFlowRowAssembler.java` | **retained, off active path** — ordered row stream; verbatim `row_type` / `group_cd` / display strings |
| `domain/banking/service/cashflow/MonthlyAmountAccumulator.java` · `PaymentDataRow.java` | **retained, off active path** — 12-month accumulator · normalized source-row record |
| [domain/banking/dto/CashFlowReportResponse.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/dto/CashFlowReportResponse.java) | `CashFlowRow` + `MonthlyAmounts` records |
| `domain/banking/dto/BankFilter.java` | `{bankCd, accountNo}` filter record |
| [domain/banking/export/CashFlowExcelWriter.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/export/CashFlowExcelWriter.java) | dedicated POI XSSF writer (live `/export` path) |
| [domain/banking/export/CashFlowReportExportProvider.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/export/CashFlowReportExportProvider.java) | `ExportRowProvider` (`screenId="cash-flow-report"`) for the generic dispatcher / visibility wiring |
| Repositories | `AcctTrnsHstrRepository.findCashFlowSourceRows`, `OvrsPymnMstrRepository.findCashFlowSourceRows`, `CardTrnsHstrRepository.findClassifiedByBizKeyAndYear`, `ScdulPymnSumrDetlRepository.findCashFlowSourceRows` (+ `ScdulPymnCashFlowRow` projection), `ExptResolMstrRepository.findCompletedApprSeqNos`, `ChartAcctInfmRepository.findByBizKeyAndDelYn` |
| Flyway | [V156__sys_scr_option_wecms.sql](../../../../../src/main/resources/db/migration/V156__sys_scr_option_wecms.sql) (View Setting seed — manual DBA script), `V39__card_trns_hstr.sql`, `V78__ovrs_pymn_mstr.sql` — *previously cited as V124/V184/V185, renumbered by the [2026-06-09 rebaseline](../../../../references/database/flyway-migration-baseline.md)* |
| messages | [messages.properties](../../../../../src/main/resources/messages.properties) — `WBE_CASH_FLOW_RATE_REQUIRED` (line ~39) |
| Tests | `src/test/.../banking/service/CashFlowReportQuarterParityTest.java`, `.../cashflow/BalanceCalculatorTest.java`, `.../cashflow/FxConverterTest.java` |

---

## 3. Refactor frontend — `wecms-web/`

| File | Role |
|---|---|
| `app/(dashboard)/banking/reports/cash-flow/page.tsx` | route shell (metadata + `<CashFlowClient />`) |
| `components/banking/reports/cash-flow/CashFlowClient.tsx` | filter state, `fetchCashFlow` via `useApiData`, rate prefetch, bank options, export |
| `components/banking/reports/cash-flow/CashFlowPage.tsx` | presentation: filter row, matrix (`groupReport`, `GroupRows`, bands), sticky End Balance |
| `components/banking/reports/cash-flow/BankFilterPopover.tsx` | bank multi-select |
| `components/banking/reports/cash-flow/types.ts` | camelCase row types, `MONTH_KEYS`, `monthIndicesForQuarter` |
| `components/banking/reports/cash-flow/mockData.ts` | dead seed (unreferenced — no mock fallback) |
| `lib/api/banking.ts` | `fetchCashFlow` (no `quarter` param), `fetchExchangeRates` |
| `lib/api/mappers.ts` | `mapCashFlowReport` / `mapCashFlowRow` / `mapCashFlowMonthly` (~line 561) |
| `lib/types/api.ts` | `ApiCashFlowReport` / `ApiCashFlowRow` (~line 827, UPPER-holdover wire type) |
| `lib/excel.ts` + `hooks/useExcelExport.ts` | export helper (CashFlowClient calls `exportExcel` directly) |
| `lib/viewSettingScreens.ts` | `SCREEN_IDS.CASH_FLOW_REPORT = "cash-flow-report"` |
| `lib/currency.ts` | `formatAmount(value, ccy, includeSymbol)` |
| `locales/{en,ko,vi}.json` | `report.cash_flow.*` keys |

Full frontend detail: [wecms-web/docs/features/banking/reports/cash-flow/component-index.md](../../../../../../wecms-web/docs/features/banking/reports/cash-flow/component-index.md).

---

## 4. Docs & related

| File | Relation |
|---|---|
| [README.md](README.md) … [refactor-gap-analysis.md](refactor-gap-analysis.md) | This package |
| [`../cash-flow.md`](legacy-spec.md) | one-page refactor pointer (retained) |
| [`docs/features/cash-flow/parity-audit/gap-report.md`](../../../../../../docs/features/cash-flow/parity-audit/gap-report.md) + [final-confirmation.md](../../../../../../docs/features/cash-flow/parity-audit/final-confirmation.md) | **primary parity audit** (kept in place, cross-linked) |
| [`docs/features/cash-flow/`](../../../../../../docs/features/cash-flow/) | migrated source package (deprecated pointer) |
| [`docs/refactor-wecms/features/banking/cash-flow-report.md`](../../../../../../docs/refactor-wecms/features/banking/cash-flow-report.md) | deprecated legacy doc tree copy (read-only) |
| [`docs/jex/features/haengsung-hide-closed-bank-accounts.md`](../../../../../../docs/jex/features/haengsung-hide-closed-bank-accounts.md) | `_r003` account-hiding rule (redmine #42633) |
| Frontend package | [wecms-web/docs/features/banking/reports/cash-flow/](../../../../../../wecms-web/docs/features/banking/reports/cash-flow/) |

> `Needs Verification` cross-refs: `rept_0017_001.ts` and `UserLoginManager.java` contain `cash_0001_001`
> string matches (likely menu/permission seeding) — out of this feature's scope, cross-reference only.
